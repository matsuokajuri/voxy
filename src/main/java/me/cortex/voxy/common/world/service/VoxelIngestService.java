package me.cortex.voxy.common.world.service;

import me.cortex.voxy.common.voxelization.ILightingSupplier;
import me.cortex.voxy.common.voxelization.VoxelizedSection;
import me.cortex.voxy.common.voxelization.WorldConversionFactory;
import me.cortex.voxy.common.voxelization.WorldVoxilizedSectionMipper;
import me.cortex.voxy.common.thread.Service;
import me.cortex.voxy.common.thread.ServiceManager;
import me.cortex.voxy.common.world.WorldEngine;
import me.cortex.voxy.common.world.WorldUpdater;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.lighting.LayerLightSectionStorage;
import net.minecraft.world.level.lighting.LevelLightEngine;

import java.util.concurrent.ConcurrentLinkedDeque;

public class VoxelIngestService {
    private static final ILightingSupplier NO_LIGHTING = (x, y, z) -> (byte) 0;
    private static final ThreadLocal<VoxelizedSection> SECTION_CACHE = ThreadLocal.withInitial(VoxelizedSection::createEmpty);
    private static volatile AutoIngestTarget autoIngestTarget = chunk -> null;

    public interface AutoIngestTarget {
        WorldEngine getEngine(LevelChunk chunk);
    }

    public record IngestStats(
            int convertedSections,
            int nonAirSections,
            int nonAirVoxels,
            int worldUpdates,
            int storageWrites,
            int missingBlockLightSections,
            int missingSkyLightSections,
            int deferredLightSections) {
        public static final IngestStats EMPTY = new IngestStats(0, 0, 0, 0, 0, 0, 0, 0);

        public boolean updated() {
            return this.worldUpdates > 0;
        }

        public boolean deferred() {
            return this.deferredLightSections > 0;
        }

        public IngestStats withStorageWrites(int storageWrites) {
            return new IngestStats(
                    this.convertedSections,
                    this.nonAirSections,
                    this.nonAirVoxels,
                    this.worldUpdates,
                    storageWrites,
                    this.missingBlockLightSections,
                    this.missingSkyLightSections,
                    this.deferredLightSections
            );
        }

        private IngestStats withMissingLightSections(int missingBlockLightSections, int missingSkyLightSections) {
            if (missingBlockLightSections == 0 && missingSkyLightSections == 0) {
                return this;
            }
            return new IngestStats(
                    this.convertedSections,
                    this.nonAirSections,
                    this.nonAirVoxels,
                    this.worldUpdates,
                    this.storageWrites,
                    this.missingBlockLightSections + missingBlockLightSections,
                    this.missingSkyLightSections + missingSkyLightSections,
                    this.deferredLightSections
            );
        }

        private IngestStats withDeferredLightSection() {
            return this.withDeferredLightSections(1);
        }

        private IngestStats withDeferredLightSections(int deferredLightSections) {
            return new IngestStats(
                    this.convertedSections,
                    this.nonAirSections,
                    this.nonAirVoxels,
                    this.worldUpdates,
                    this.storageWrites,
                    this.missingBlockLightSections,
                    this.missingSkyLightSections,
                    this.deferredLightSections + deferredLightSections
            );
        }

        private IngestStats add(IngestStats other) {
            return new IngestStats(
                    this.convertedSections + other.convertedSections,
                    this.nonAirSections + other.nonAirSections,
                    this.nonAirVoxels + other.nonAirVoxels,
                    this.worldUpdates + other.worldUpdates,
                    this.storageWrites + other.storageWrites,
                    this.missingBlockLightSections + other.missingBlockLightSections,
                    this.missingSkyLightSections + other.missingSkyLightSections,
                    this.deferredLightSections + other.deferredLightSections
            );
        }
    }

    //Original Voxy runs voxel conversion + mip + world insertion on a dedicated "Ingest service"
    // worker (ServiceManager weight 5000); the game thread only captures light data and enqueues.
    // The record mirrors original's IngestSection with the resolved lighting supplier captured
    // up front (it embeds the copied DataLayers and the Forge uniform-sky-light default, both of
    // which must be sampled on the game thread).
    private record PendingSection(WorldEngine engine, LevelChunkSection section, int x, int y, int z, ILightingSupplier lighting) {}

    private static volatile VoxelIngestService activeService;

    private final ConcurrentLinkedDeque<PendingSection> ingestQueue = new ConcurrentLinkedDeque<>();
    private final Service service;

    public VoxelIngestService(ServiceManager serviceManager) {
        this.service = serviceManager.createServiceNoCleanup(() -> this::processJob, 5000, "Ingest service");
    }

    private void processJob() {
        //poll() instead of pop(): Service.steal()/drain() can retire permits without consuming
        // queue entries, so a permit/queue mismatch must not crash the worker.
        var task = this.ingestQueue.poll();
        if (task == null) {
            return;
        }
        //The owning world may have been closed between enqueue and execution (dimension change,
        // logout); queued sections for it are simply dropped.
        if (!task.engine.isLive()) {
            return;
        }
        ingestNow(task.engine, task.section, task.x, task.y, task.z, task.lighting);
    }

    public static void setActiveService(VoxelIngestService service) {
        activeService = service;
    }

    public static void setAutoIngestTarget(AutoIngestTarget target) {
        autoIngestTarget = target == null ? chunk -> null : target;
    }

    public boolean enqueueIngest(WorldEngine engine, LevelChunk chunk) {
        return ingestChunk(engine, chunk);
    }

    public static boolean ingestChunk(WorldEngine engine, LevelChunk chunk) {
        return ingestChunkWithStats(engine, chunk).updated();
    }

    public static IngestStats ingestChunkWithStats(WorldEngine engine, LevelChunk chunk) {
        if (engine == null || chunk == null) {
            return IngestStats.EMPTY;
        }
        if (!engine.isLive()) {
            throw new IllegalStateException("Tried inserting chunk into WorldEngine that was not alive");
        }

        IngestStats stats = IngestStats.EMPTY;
        int sectionY = chunk.getMinSection();
        var lightEngine = chunk.getLevel().getLightEngine();
        boolean dimensionHasSkyLight = chunk.getLevel().dimensionType().hasSkyLight();
        IngestStats deferredStats = IngestStats.EMPTY;
        for (var section : chunk.getSections()) {
            if (section != null) {
                var sectionPos = SectionPos.of(chunk.getPos().x, sectionY, chunk.getPos().z);
                var blockLight = lightEngine.getLayerListener(LightLayer.BLOCK).getDataLayerData(sectionPos);
                boolean missingRequiredLight = !hasUsableLight(
                        dimensionHasSkyLight,
                        lightEngine,
                        sectionPos,
                        section);
                if (missingRequiredLight && !section.hasOnlyAir()) {
                    deferredStats = deferredStats.add(deferredLightingStats(
                            dimensionHasSkyLight,
                            blockLight == null));
                }
            }
            sectionY++;
        }
        if (deferredStats.deferred()) {
            return deferredStats;
        }
        sectionY = chunk.getMinSection();
        for (var section : chunk.getSections()) {
            if (section != null) {
                var sectionPos = SectionPos.of(chunk.getPos().x, sectionY, chunk.getPos().z);
                var blockLight = lightEngine.getLayerListener(LightLayer.BLOCK).getDataLayerData(sectionPos);
                var skyLight = lightEngine.getLayerListener(LightLayer.SKY).getDataLayerData(sectionPos);
                boolean missingBlockLight = blockLight == null;
                boolean missingRequiredLight = !hasUsableLight(
                        dimensionHasSkyLight,
                        lightEngine,
                        sectionPos,
                        section);
                if (missingRequiredLight && !section.hasOnlyAir()) {
                    stats = stats.add(deferredLightingStats(dimensionHasSkyLight, missingBlockLight));
                    continue;
                }
                int skyDefault = resolveUniformSkyLight(chunk, lightEngine, sectionPos, skyLight);
                if (shouldIngestLoadedChunkSection(section, blockLight, skyLight, skyDefault)) {
                    IngestStats sectionStats = rawIngestWithStats(
                            engine,
                            section,
                            chunk.getPos().x,
                            sectionY,
                            chunk.getPos().z,
                            getLightingSupplier(
                                    blockLight == null ? null : blockLight.copy(),
                                    skyLight == null ? null : skyLight.copy(),
                                    skyDefault))
                            .withMissingLightSections(missingBlockLight ? 1 : 0, 0);
                    stats = stats.add(sectionStats);
                }
            }
            sectionY++;
        }
        return stats;
    }

    public static boolean ingestChunkSection(WorldEngine engine, LevelChunk chunk, int sectionY) {
        return ingestChunkSectionWithStats(engine, chunk, sectionY).updated();
    }

    public static IngestStats ingestChunkSectionWithStats(WorldEngine engine, LevelChunk chunk, int sectionY) {
        if (engine == null || chunk == null) {
            return IngestStats.EMPTY;
        }
        if (!engine.isLive()) {
            throw new IllegalStateException("Tried inserting chunk section into WorldEngine that was not alive");
        }
        int sectionIndex = sectionY - chunk.getMinSection();
        LevelChunkSection[] sections = chunk.getSections();
        if (sectionIndex < 0 || sectionIndex >= sections.length) {
            return IngestStats.EMPTY;
        }
        LevelChunkSection section = sections[sectionIndex];
        if (section == null) {
            return IngestStats.EMPTY;
        }

        var sectionPos = SectionPos.of(chunk.getPos().x, sectionY, chunk.getPos().z);
        var lightEngine = chunk.getLevel().getLightEngine();
        var blockLight = lightEngine.getLayerListener(LightLayer.BLOCK).getDataLayerData(sectionPos);
        var skyLight = lightEngine.getLayerListener(LightLayer.SKY).getDataLayerData(sectionPos);
        boolean missingBlockLight = blockLight == null;
        boolean dimensionHasSkyLight = chunk.getLevel().dimensionType().hasSkyLight();
        boolean missingRequiredLight = !hasUsableLight(
                dimensionHasSkyLight,
                lightEngine,
                sectionPos,
                section);
        if (missingRequiredLight && !section.hasOnlyAir()) {
            return deferredLightingStats(dimensionHasSkyLight, missingBlockLight);
        }
        int skyDefault = resolveUniformSkyLight(chunk, lightEngine, sectionPos, skyLight);
        if (!shouldIngestLoadedChunkSection(section, blockLight, skyLight, skyDefault)) {
            return IngestStats.EMPTY;
        }
        return rawIngestWithStats(
                engine,
                section,
                chunk.getPos().x,
                sectionY,
                chunk.getPos().z,
                getLightingSupplier(
                        blockLight == null ? null : blockLight.copy(),
                        skyLight == null ? null : skyLight.copy(),
                        skyDefault))
                .withMissingLightSections(missingBlockLight ? 1 : 0, 0);
    }

    private static boolean shouldIngestSection(LevelChunkSection section, int cx, int cy, int cz) {
        return section != null;
    }

    private static boolean shouldIngestLoadedChunkSection(LevelChunkSection section, DataLayer blockLight, DataLayer skyLight, int skyDefault) {
        //Original enqueueIngest queues every section once the chunk's lighting is usable. Its
        //worker explicitly inserts vs.zero() for an all-air section without light data; that write
        //is required to erase older/persisted non-air voxels. Skipping the section leaves stale
        //terrain in WorldEngine forever, even after the real client chunk has become all air.
        return shouldIngestSection(section, 0, 0, 0);
    }

    private static boolean hasUsableLight(
            boolean dimensionHasSkyLight,
            LevelLightEngine lightEngine,
            SectionPos sectionPos,
            LevelChunkSection section) {
        //Original enqueueIngest waits for either SKY or BLOCK LIGHT_AND_DATA before accepting a
        //non-air chunk. The Forge 1.20.1 adapter must be stricter per section because a non-null
        //placeholder layer can be visible while a client light packet is still being applied:
        //SKY is the proven readiness signal in sky dimensions, while BLOCK is the only signal that
        //can ever become ready in dimensions without skylight.
        LightLayer readinessLayer = requiredReadinessLayer(dimensionHasSkyLight);
        return isLightingReadyForIngest(
                section.hasOnlyAir(),
                lightEngine.getDebugSectionType(readinessLayer, sectionPos));
    }

    static LightLayer requiredReadinessLayer(boolean dimensionHasSkyLight) {
        return dimensionHasSkyLight ? LightLayer.SKY : LightLayer.BLOCK;
    }

    static boolean isLightingReadyForIngest(
            boolean sectionHasOnlyAir,
            LayerLightSectionStorage.SectionType requiredLayerType) {
        return sectionHasOnlyAir
                || requiredLayerType == LayerLightSectionStorage.SectionType.LIGHT_AND_DATA;
    }

    static IngestStats deferredLightingStats(
            boolean dimensionHasSkyLight,
            boolean blockLayerDataMissing) {
        int missingBlockLightSections = dimensionHasSkyLight
                ? (blockLayerDataMissing ? 1 : 0)
                : 1;
        int missingSkyLightSections = dimensionHasSkyLight ? 1 : 0;
        return IngestStats.EMPTY
                .withMissingLightSections(missingBlockLightSections, missingSkyLightSections)
                .withDeferredLightSection();
    }

    // When a section has no stored sky DataLayer, Minecraft leaves its sky light implicit: a fully
    // sky-exposed section above the surface is uniformly 15 (the engine never stores a layer for it),
    // while an enclosed section is 0. Sampling the light engine's computed value lets air voxels carry
    // the correct sky light instead of defaulting to 0. The previous 0 default left opaque LOD faces,
    // which take their light from the neighbouring air voxel, unlit/black against open sky.
    private static int resolveUniformSkyLight(LevelChunk chunk, LevelLightEngine lightEngine, SectionPos sectionPos, DataLayer skyLight) {
        if (skyLight != null && !skyLight.isEmpty()) {
            return 0;//Per-voxel sky data is present, so no uniform default is needed.
        }
        if (!chunk.getLevel().dimensionType().hasSkyLight()) {
            return 0;
        }
        //A null sky layer is uniform across the section, so one sample at the section origin is enough.
        return lightEngine.getLayerListener(LightLayer.SKY).getLightValue(sectionPos.origin());
    }

    public static VoxelizedSection convertSection(WorldEngine engine, LevelChunkSection section, int x, int y, int z, DataLayer blockLight, DataLayer skyLight) {
        return convertSection(engine, section, x, y, z, getLightingSupplier(blockLight, skyLight));
    }

    public static VoxelizedSection convertSection(WorldEngine engine, LevelChunkSection section, int x, int y, int z, ILightingSupplier lightingSupplier) {
        if (engine == null || section == null) {
            return null;
        }
        var voxelized = acquireCachedSection(x, y, z);
        if (section.hasOnlyAir() && lightingSupplier == NO_LIGHTING) {
            return voxelized.zero();
        }

        WorldConversionFactory.convert(
                voxelized,
                engine.getMapper(),
                section.getStates(),
                section.getBiomes(),
                lightingSupplier
        );
        WorldVoxilizedSectionMipper.mipSection(voxelized, engine.getMapper());
        return voxelized;
    }

    static VoxelizedSection acquireCachedSection(int x, int y, int z) {
        // WorldUpdater.insertUpdate is synchronous and explicitly releases ownership before it
        // returns, so original Voxy safely reuses one 4,681-long backing array per ingest thread.
        return SECTION_CACHE.get().setPosition(x, y, z);
    }

    public static boolean rawIngest(WorldEngine engine, LevelChunkSection section, int x, int y, int z, DataLayer blockLight, DataLayer skyLight) {
        return rawIngestWithStats(engine, section, x, y, z, blockLight, skyLight).updated();
    }

    public static IngestStats rawIngestWithStats(WorldEngine engine, LevelChunkSection section, int x, int y, int z, DataLayer blockLight, DataLayer skyLight) {
        return rawIngestWithStats(engine, section, x, y, z, getLightingSupplier(blockLight, skyLight));
    }

    public static boolean rawIngest(WorldEngine engine, LevelChunkSection section, int x, int y, int z, ILightingSupplier lightingSupplier) {
        return rawIngestWithStats(engine, section, x, y, z, lightingSupplier).updated();
    }

    public static IngestStats rawIngestWithStats(WorldEngine engine, LevelChunkSection section, int x, int y, int z, ILightingSupplier lightingSupplier) {
        if (!shouldIngestSection(section, x, y, z)) {
            return IngestStats.EMPTY;
        }
        VoxelIngestService service = activeService;
        if (service != null && service.service.isLive()) {
            engine.markActive();
            service.ingestQueue.add(new PendingSection(engine, section, x, y, z, lightingSupplier));
            service.service.execute();
            //Conversion happens on the ingest worker, so voxel-level stats are unknown here;
            // nonAirSections falls back to the section's own emptiness flag.
            return new IngestStats(1, section.hasOnlyAir() ? 0 : 1, 0, 1, 0, 0, 0, 0);
        }
        return ingestNow(engine, section, x, y, z, lightingSupplier);
    }

    private static IngestStats ingestNow(WorldEngine engine, LevelChunkSection section, int x, int y, int z, ILightingSupplier lightingSupplier) {
        var voxelized = convertSection(engine, section, x, y, z, lightingSupplier);
        if (voxelized == null) {
            return IngestStats.EMPTY;
        }
        engine.markActive();
        WorldUpdater.insertUpdate(engine, voxelized);
        return new IngestStats(
                1,
                voxelized.lvl0NonAirCount > 0 ? 1 : 0,
                voxelized.lvl0NonAirCount,
                1,
                0,
                0,
                0,
                0
        );
    }

    public static boolean tryAutoIngestChunk(LevelChunk chunk) {
        return tryAutoIngestChunkWithStats(chunk).updated();
    }

    public static IngestStats tryAutoIngestChunkWithStats(LevelChunk chunk) {
        WorldEngine engine = autoIngestTarget.getEngine(chunk);
        if (engine == null) {
            return IngestStats.EMPTY;
        }
        return ingestChunkWithStats(engine, chunk);
    }

    public int getTaskCount() {
        return this.service.numJobs();
    }

    public void shutdown() {
        if (activeService == this) {
            activeService = null;
        }
        if (this.service.isLive()) {
            this.service.shutdown();
        }
        this.ingestQueue.clear();
    }

    private static ILightingSupplier getLightingSupplier(DataLayer blockLight, DataLayer skyLight) {
        return getLightingSupplier(blockLight, skyLight, 0);
    }

    private static ILightingSupplier getLightingSupplier(DataLayer blockLight, DataLayer skyLight, int skyDefault) {
        boolean hasSkyLight = skyLight != null && !skyLight.isEmpty();
        boolean hasBlockLight = blockLight != null && !blockLight.isEmpty();
        int skyConstant = Math.max(0, Math.min(15, skyDefault));
        if (!hasSkyLight && !hasBlockLight && skyConstant == 0) {
            return NO_LIGHTING;
        }
        return (x, y, z) -> {
            int block = hasBlockLight ? Math.min(15, blockLight.get(x, y, z)) : 0;
            int sky = hasSkyLight ? Math.min(15, skyLight.get(x, y, z)) : skyConstant;
            return (byte) (sky | (block << 4));
        };
    }

}
