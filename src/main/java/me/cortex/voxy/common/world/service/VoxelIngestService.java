package me.cortex.voxy.common.world.service;

import me.cortex.voxy.common.voxelization.ILightingSupplier;
import me.cortex.voxy.common.voxelization.VoxelizedSection;
import me.cortex.voxy.common.voxelization.WorldConversionFactory;
import me.cortex.voxy.common.voxelization.WorldVoxilizedSectionMipper;
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

    public VoxelIngestService() {
    }

    public boolean enqueueIngest(WorldEngine engine, LevelChunk chunk) {
        return ingestChunk(engine, chunk);
    }

    public static boolean ingestChunk(WorldEngine engine, LevelChunk chunk) {
        if (engine == null || chunk == null) {
            return false;
        }
        if (!engine.isLive()) {
            throw new IllegalStateException("Tried inserting chunk into WorldEngine that was not alive");
        }

        boolean ingestedAny = false;
        int sectionY = chunk.getMinSection();
        for (var section : chunk.getSections()) {
            if (section != null && shouldIngestSection(section, chunk.getPos().x, sectionY, chunk.getPos().z)) {
                ingestedAny |= rawIngest(engine, section, chunk.getPos().x, sectionY, chunk.getPos().z, createLevelLightingSupplier(chunk, sectionY));
            }
            sectionY++;
        }
        return ingestedAny;
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
        return rawIngest(engine, section, x, y, z, getLightingSupplier(blockLight, skyLight));
    }

    public static boolean rawIngest(WorldEngine engine, LevelChunkSection section, int x, int y, int z, ILightingSupplier lightingSupplier) {
        if (!shouldIngestSection(section, x, y, z)) {
            return false;
        }
        var voxelized = convertSection(engine, section, x, y, z, lightingSupplier);
        if (voxelized == null) {
            return false;
        }
        engine.markActive();
        WorldUpdater.insertUpdate(engine, voxelized);
        return true;
    }

    public static boolean tryAutoIngestChunk(LevelChunk chunk) {
        LOGGER.debug("Automatic chunk ingest is disabled in the Forge skeleton.");
        return false;
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
