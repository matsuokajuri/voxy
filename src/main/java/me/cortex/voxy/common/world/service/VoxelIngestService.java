package me.cortex.voxy.common.world.service;

import me.cortex.voxy.common.voxelization.ILightingSupplier;
import me.cortex.voxy.common.voxelization.VoxelizedSection;
import me.cortex.voxy.common.voxelization.WorldConversionFactory;
import me.cortex.voxy.common.voxelization.WorldVoxilizedSectionMipper;
import me.cortex.voxy.common.thread.ServiceManager;
import me.cortex.voxy.common.world.WorldEngine;
import me.cortex.voxy.common.world.WorldUpdater;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import org.slf4j.LoggerFactory;

public class VoxelIngestService {
    private static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger("Voxy");
    private static final ILightingSupplier NO_LIGHTING = (x, y, z) -> (byte) 0;
    private static final boolean AUDIT_LIGHTING = Boolean.getBoolean("voxy.forge.auditLighting");
    private static final int MAX_LIGHTING_AUDITS = 12;
    private static int lightingAuditRuns;
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

    public VoxelIngestService() {
    }

    public VoxelIngestService(ServiceManager serviceManager) {
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
        int missingNonAirBlockLightSections = 0;
        int missingNonAirSkyLightSections = 0;
        for (var section : chunk.getSections()) {
            if (section != null && shouldIngestLoadedChunkSection(section, chunk.getPos().x, sectionY, chunk.getPos().z)) {
                var sectionPos = SectionPos.of(chunk.getPos().x, sectionY, chunk.getPos().z);
                var blockLight = lightEngine.getLayerListener(LightLayer.BLOCK).getDataLayerData(sectionPos);
                var skyLight = lightEngine.getLayerListener(LightLayer.SKY).getDataLayerData(sectionPos);
                boolean missingSkyLight = skyLight == null && chunk.getLevel().dimensionType().hasSkyLight();
                if (missingSkyLight) {
                    missingNonAirBlockLightSections += blockLight == null ? 1 : 0;
                    missingNonAirSkyLightSections++;
                }
            }
            sectionY++;
        }
        if (missingNonAirSkyLightSections != 0) {
            return IngestStats.EMPTY
                    .withMissingLightSections(missingNonAirBlockLightSections, missingNonAirSkyLightSections)
                    .withDeferredLightSections(missingNonAirSkyLightSections);
        }
        sectionY = chunk.getMinSection();
        for (var section : chunk.getSections()) {
            if (section != null && shouldIngestLoadedChunkSection(section, chunk.getPos().x, sectionY, chunk.getPos().z)) {
                var sectionPos = SectionPos.of(chunk.getPos().x, sectionY, chunk.getPos().z);
                var blockLight = lightEngine.getLayerListener(LightLayer.BLOCK).getDataLayerData(sectionPos);
                var skyLight = lightEngine.getLayerListener(LightLayer.SKY).getDataLayerData(sectionPos);
                boolean missingBlockLight = blockLight == null;
                boolean missingSkyLight = skyLight == null && chunk.getLevel().dimensionType().hasSkyLight();
                if (missingSkyLight) {
                    stats = stats.add(IngestStats.EMPTY
                            .withMissingLightSections(missingBlockLight ? 1 : 0, 1)
                            .withDeferredLightSection());
                    continue;
                }
                IngestStats sectionStats = rawIngestWithStats(
                        engine,
                        section,
                        chunk.getPos().x,
                        sectionY,
                        chunk.getPos().z,
                        getLightingSupplier(
                                blockLight == null ? null : blockLight.copy(),
                                skyLight == null ? null : skyLight.copy()))
                        .withMissingLightSections(missingBlockLight ? 1 : 0, missingSkyLight ? 1 : 0);
                stats = stats.add(sectionStats);
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
        if (section == null || !shouldIngestLoadedChunkSection(section, chunk.getPos().x, sectionY, chunk.getPos().z)) {
            return IngestStats.EMPTY;
        }

        var sectionPos = SectionPos.of(chunk.getPos().x, sectionY, chunk.getPos().z);
        var lightEngine = chunk.getLevel().getLightEngine();
        var blockLight = lightEngine.getLayerListener(LightLayer.BLOCK).getDataLayerData(sectionPos);
        var skyLight = lightEngine.getLayerListener(LightLayer.SKY).getDataLayerData(sectionPos);
        boolean missingBlockLight = blockLight == null;
        boolean missingSkyLight = skyLight == null && chunk.getLevel().dimensionType().hasSkyLight();
        if (missingSkyLight) {
            return IngestStats.EMPTY
                    .withMissingLightSections(missingBlockLight ? 1 : 0, 1)
                    .withDeferredLightSection();
        }
        return rawIngestWithStats(
                engine,
                section,
                chunk.getPos().x,
                sectionY,
                chunk.getPos().z,
                getLightingSupplier(
                        blockLight == null ? null : blockLight.copy(),
                        skyLight == null ? null : skyLight.copy()))
                .withMissingLightSections(missingBlockLight ? 1 : 0, missingSkyLight ? 1 : 0);
    }

    private static boolean shouldIngestSection(LevelChunkSection section, int cx, int cy, int cz) {
        return section != null;
    }

    private static boolean shouldIngestLoadedChunkSection(LevelChunkSection section, int cx, int cy, int cz) {
        return shouldIngestSection(section, cx, cy, cz) && !section.hasOnlyAir();
    }

    public static VoxelizedSection convertSection(WorldEngine engine, LevelChunkSection section, int x, int y, int z, DataLayer blockLight, DataLayer skyLight) {
        return convertSection(engine, section, x, y, z, getLightingSupplier(blockLight, skyLight));
    }

    public static VoxelizedSection convertSection(WorldEngine engine, LevelChunkSection section, int x, int y, int z, ILightingSupplier lightingSupplier) {
        if (engine == null || section == null) {
            return null;
        }
        var voxelized = VoxelizedSection.createEmpty().setPosition(x, y, z);
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
        var voxelized = convertSection(engine, section, x, y, z, lightingSupplier);
        if (voxelized == null) {
            return IngestStats.EMPTY;
        }
        auditVoxelLighting(x, y, z, voxelized);
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
        WorldEngine engine = autoIngestTarget.getEngine(chunk);
        if (engine == null) {
            return false;
        }
        return ingestChunk(engine, chunk);
    }

    public int getTaskCount() {
        return 0;
    }

    public void shutdown() {
    }

    private static ILightingSupplier getLightingSupplier(DataLayer blockLight, DataLayer skyLight) {
        boolean hasSkyLight = skyLight != null && !skyLight.isEmpty();
        boolean hasBlockLight = blockLight != null && !blockLight.isEmpty();
        if (!hasSkyLight && !hasBlockLight) {
            return NO_LIGHTING;
        }
        return (x, y, z) -> {
            int block = hasBlockLight ? Math.min(15, blockLight.get(x, y, z)) : 0;
            int sky = hasSkyLight ? Math.min(15, skyLight.get(x, y, z)) : 0;
            return (byte) (sky | (block << 4));
        };
    }

    private static void auditVoxelLighting(int sectionX, int sectionY, int sectionZ, VoxelizedSection voxelized) {
        if (!AUDIT_LIGHTING || lightingAuditRuns >= MAX_LIGHTING_AUDITS || voxelized.lvl0NonAirCount == 0) {
            return;
        }
        int minSky = 15;
        int maxSky = 0;
        int minBlock = 15;
        int maxBlock = 0;
        long sumSky = 0L;
        long sumBlock = 0L;
        int sampled = 0;
        for (int i = 0; i < 16 * 16 * 16; i++) {
            long id = voxelized.section[i];
            if (me.cortex.voxy.common.world.other.Mapper.isAir(id)) {
                continue;
            }
            int light = me.cortex.voxy.common.world.other.Mapper.getLightId(id);
            int sky = light & 0x0F;
            int block = (light >>> 4) & 0x0F;
            minSky = Math.min(minSky, sky);
            maxSky = Math.max(maxSky, sky);
            minBlock = Math.min(minBlock, block);
            maxBlock = Math.max(maxBlock, block);
            sumSky += sky;
            sumBlock += block;
            sampled++;
        }
        if (sampled == 0) {
            return;
        }
        lightingAuditRuns++;
        LOGGER.info(
                "Voxy lighting ingest audit: run={} section={},{},{} nonAir={} sampled={} skyMin={} skyMax={} skyAvg={} blockMin={} blockMax={} blockAvg={}",
                lightingAuditRuns,
                sectionX,
                sectionY,
                sectionZ,
                voxelized.lvl0NonAirCount,
                sampled,
                minSky,
                maxSky,
                String.format("%.2f", (double) sumSky / sampled),
                minBlock,
                maxBlock,
                String.format("%.2f", (double) sumBlock / sampled));
    }

}
