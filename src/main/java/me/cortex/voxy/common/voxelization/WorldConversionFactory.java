package me.cortex.voxy.common.voxelization;

import me.cortex.voxy.common.world.other.Mapper;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.chunk.PalettedContainerRO;

public class WorldConversionFactory {
    private static final class Cache {
        private final int[] biomeCache = new int[4*4*4];
    }

    private static final ThreadLocal<Cache> THREAD_LOCAL = ThreadLocal.withInitial(Cache::new);

    public static VoxelizedSection convert(VoxelizedSection section,
                                           Mapper stateMapper,
                                           PalettedContainer<BlockState> blockContainer,
                                           PalettedContainerRO<Holder<Biome>> biomeContainer,
                                           ILightingSupplier lightSupplier) {
        return convert(section, stateMapper, blockContainer, biomeContainer, lightSupplier, false, 0);
    }

    public static VoxelizedSection convert(VoxelizedSection section,
                                           Mapper stateMapper,
                                           PalettedContainer<BlockState> blockContainer,
                                           PalettedContainerRO<Holder<Biome>> biomeContainer,
                                           ILightingSupplier lightSupplier,
                                           boolean shouldZoom,
                                           long zoomSeed) {
        var cache = THREAD_LOCAL.get();
        var biomes = cache.biomeCache;
        var data = section.section;

        int biomeIndex = 0;
        for (int y = 0; y < 4; y++) {
            for (int z = 0; z < 4; z++) {
                for (int x = 0; x < 4; x++) {
                    biomes[biomeIndex++] = stateMapper.getIdForBiome(biomeContainer.get(x, y, z));
                }
            }
        }

        int nonZeroCnt = 0;
        for (int y = 0; y < 16; y++) {
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    int idx = (y << 8) | (z << 4) | x;
                    int blockId = stateMapper.getIdForBlockState(blockContainer.get(x, y, z));
                    byte light = lightSupplier.supply(x, y, z);
                    nonZeroCnt += blockId == 0 ? 0 : 1;
                    if (blockId == 0) {
                        data[idx] = Mapper.airWithLight(light);
                    } else {
                        data[idx] = Mapper.composeMappingId(light, blockId, biomes[biomeIndexForBlock(x, y, z)]);
                    }
                }
            }
        }
        section.lvl0NonAirCount = nonZeroCnt;
        return section;
    }


    private static int biomeIndexForBlock(int x, int y, int z) {
        return ((y >> 2) << 4) | ((z >> 2) << 2) | (x >> 2);
    }

    //Support for other mods etc that use this entry point
    @Deprecated(forRemoval = true)
    public static void mipSection(VoxelizedSection section, Mapper mapper) {
        WorldVoxilizedSectionMipper.mipSection(section, mapper);
    }
}
