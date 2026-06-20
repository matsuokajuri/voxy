package me.cortex.voxy.common.world.service;

import me.cortex.voxy.common.voxelization.ILightingSupplier;
import me.cortex.voxy.common.voxelization.VoxelizedSection;
import me.cortex.voxy.common.voxelization.WorldConversionFactory;
import me.cortex.voxy.common.voxelization.WorldVoxilizedSectionMipper;
import me.cortex.voxy.common.thread.ServiceManager;
import me.cortex.voxy.common.world.WorldEngine;
import me.cortex.voxy.common.world.WorldUpdater;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import org.slf4j.LoggerFactory;

public class VoxelIngestService {
    private static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger("Voxy");
    private static final ILightingSupplier NO_LIGHTING = (x, y, z) -> (byte) 0;
    private static volatile AutoIngestTarget autoIngestTarget = chunk -> null;

    public interface AutoIngestTarget {
        WorldEngine getEngine(LevelChunk chunk);
    }

    public record IngestStats(int convertedSections, int nonAirSections, int nonAirVoxels, int worldUpdates, int storageWrites) {
        public static final IngestStats EMPTY = new IngestStats(0, 0, 0, 0, 0);

        public boolean updated() {
            return this.worldUpdates > 0;
        }

        public IngestStats withStorageWrites(int storageWrites) {
            return new IngestStats(
                    this.convertedSections,
                    this.nonAirSections,
                    this.nonAirVoxels,
                    this.worldUpdates,
                    storageWrites
            );
        }

        private IngestStats add(IngestStats other) {
            return new IngestStats(
                    this.convertedSections + other.convertedSections,
                    this.nonAirSections + other.nonAirSections,
                    this.nonAirVoxels + other.nonAirVoxels,
                    this.worldUpdates + other.worldUpdates,
                    this.storageWrites + other.storageWrites
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
        for (var section : chunk.getSections()) {
            if (section != null && shouldIngestSection(section, chunk.getPos().x, sectionY, chunk.getPos().z)) {
                stats = stats.add(rawIngestWithStats(engine, section, chunk.getPos().x, sectionY, chunk.getPos().z, createLevelLightingSupplier(chunk, sectionY)));
            }
            sectionY++;
        }
        return stats;
    }

    private static boolean shouldIngestSection(LevelChunkSection section, int cx, int cy, int cz) {
        return section != null;
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
        engine.markActive();
        WorldUpdater.insertUpdate(engine, voxelized);
        return new IngestStats(
                1,
                voxelized.lvl0NonAirCount > 0 ? 1 : 0,
                voxelized.lvl0NonAirCount,
                1,
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

    private static ILightingSupplier createLevelLightingSupplier(LevelChunk chunk, int sectionY) {
        var level = chunk.getLevel();
        int baseX = chunk.getPos().x << 4;
        int baseY = sectionY << 4;
        int baseZ = chunk.getPos().z << 4;
        var pos = new BlockPos.MutableBlockPos();
        return (x, y, z) -> {
            pos.set(baseX + x, baseY + y, baseZ + z);
            int block = Math.min(15, level.getBrightness(LightLayer.BLOCK, pos));
            int sky = Math.min(15, level.getBrightness(LightLayer.SKY, pos));
            return (byte) (sky | (block << 4));
        };
    }
}
