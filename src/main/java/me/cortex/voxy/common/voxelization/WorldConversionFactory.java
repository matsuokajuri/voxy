package me.cortex.voxy.common.voxelization;

import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;
import me.cortex.voxy.common.world.other.Mapper;
import net.minecraft.core.Holder;
import net.minecraft.util.SimpleBitStorage;
import net.minecraft.util.ZeroBitStorage;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.GlobalPalette;
import net.minecraft.world.level.chunk.HashMapPalette;
import net.minecraft.world.level.chunk.LinearPalette;
import net.minecraft.world.level.chunk.Palette;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.chunk.PalettedContainerRO;
import net.minecraft.world.level.chunk.SingleValuePalette;

import java.util.WeakHashMap;

public class WorldConversionFactory {
    private static final class Cache {
        private final int[] biomeCache = new int[4*4*4];
        private final WeakHashMap<Mapper, Reference2IntOpenHashMap<BlockState>> localMappings = new WeakHashMap<>();
        private int[] paletteCache = new int[1024];
        private final long[] zoomCellCache = new long[5*5*5];

        private Reference2IntOpenHashMap<BlockState> getLocalMapping(Mapper mapper) {
            return this.localMappings.computeIfAbsent(mapper, ignored -> new Reference2IntOpenHashMap<>());
        }

        private int[] getPaletteCache(int size) {
            if (this.paletteCache.length < size) {
                this.paletteCache = new int[size];
            }
            return this.paletteCache;
        }
    }

    private static final ThreadLocal<Cache> THREAD_LOCAL = ThreadLocal.withInitial(Cache::new);

    private static int setupLocalPalette(
            Palette<BlockState> palette,
            Reference2IntOpenHashMap<BlockState> blockCache,
            Mapper mapper,
            int[] paletteCache) {
        int paletteSize = palette.getSize();
        if (palette instanceof LinearPalette<?>) {
            for (int i = 0; i < paletteSize; i++) {
                paletteCache[i] = mapBlockState(palette.valueFor(i), blockCache, mapper);
            }
        } else if (palette instanceof HashMapPalette<?> || isForgeLithiumHashPalette(palette)) {
            // Original setupLithiumLocalPallet uses this same size/valueFor -> local Mapper
            // cache loop, including the transient-hole sentinel. Harium/Radium retain the
            // older me.jellysquid package, not upstream's net.caffeinemc package.
            for (int i = 0; i < paletteSize; i++) {
                BlockState state = null;
                try {
                    state = palette.valueFor(i);
                } catch (RuntimeException ignored) {
                    // Preserve original Voxy's invalid-entry sentinel for a transient palette hole.
                }
                paletteCache[i] = mapBlockState(state, blockCache, mapper);
            }
        } else if (palette instanceof SingleValuePalette<?>) {
            paletteCache[0] = mapBlockState(palette.valueFor(0), blockCache, mapper);
        } else {
            // Keep the original explicit palette contract. Supporting Lithium does not make
            // every unknown palette/storage implementation interchangeable or permit a
            // per-block get(x,y,z) substitute for the packed-storage conversion below.
            throw new IllegalStateException("Unknown block palette type: " + palette.getClass().getName());
        }
        return paletteSize;
    }

    private static boolean isForgeLithiumHashPalette(Palette<?> palette) {
        // Gate on the actual optional implementation, not the mod id: Harium 1.0.0 reports
        // "harium" while keeping this Lithium ABI. No optional class needs to be loaded when
        // absent. Walking parents preserves upstream instanceof semantics for subclasses.
        for (Class<?> type = palette.getClass(); type != null; type = type.getSuperclass()) {
            if (type.getName().equals("me.jellysquid.mods.lithium.common.world.chunk.LithiumHashPalette")) {
                return true;
            }
        }
        return false;
    }

    private static int mapBlockState(
            BlockState state,
            Reference2IntOpenHashMap<BlockState> blockCache,
            Mapper mapper) {
        int blockId = -1;
        if (state != null) {
            blockId = blockCache.getOrDefault(state, -1);
            if (blockId == -1) {
                blockId = mapper.getIdForBlockState(state);
                blockCache.put(state, blockId);
            }
        }
        return blockId;
    }

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
        // Original Voxy creates a per-thread local block-state palette, then decodes Minecraft's
        // packed storage directly. PalettedContainer.data and Data.palette/storage are exposed by
        // the Forge access transformer; no reflection or 4096-call get() fallback is used here.
        var cache = THREAD_LOCAL.get();
        var blockCache = cache.getLocalMapping(stateMapper);
        var biomes = cache.biomeCache;
        var data = section.section;
        var zoomCells = cache.zoomCellCache;

        Palette<BlockState> palette = blockContainer.data.palette;
        int[] paletteCache = cache.getPaletteCache(palette.getSize());

        GlobalPalette<BlockState> globalPalette = null;
        int maximumLocalPaletteIndex;
        if (palette instanceof GlobalPalette<?>) {
            @SuppressWarnings("unchecked")
            GlobalPalette<BlockState> castPalette = (GlobalPalette<BlockState>) palette;
            globalPalette = castPalette;
            maximumLocalPaletteIndex = globalPalette.getSize();
        } else {
            int paletteSize = setupLocalPalette(palette, blockCache, stateMapper, paletteCache);
            maximumLocalPaletteIndex = Math.max(0, paletteSize - 1);
        }

        int biomeIndex = 0;
        int initialBiome = -1;
        for (int y = 0; y < 4; y++) {
            for (int z = 0; z < 4; z++) {
                for (int x = 0; x < 4; x++) {
                    int biomeId = stateMapper.getIdForBiome(biomeContainer.get(x, y, z));
                    biomes[biomeIndex++] = biomeId;
                    if (initialBiome == -1) {
                        initialBiome = biomeId;
                    }
                    shouldZoom &= initialBiome == biomeId;
                }
            }
        }

        if (shouldZoom) {
            computeZoomCells(biomes, zoomSeed, zoomCells);
        }

        int nonZeroCnt = 0;
        if (blockContainer.data.storage instanceof SimpleBitStorage blockStorage) {
            long[] packedData = blockStorage.getRaw();
            int valuesPerLongMinusOne = (64 / blockStorage.getBits()) - 1;
            int paletteMask = (1 << blockStorage.getBits()) - 1;
            int entryBits = blockStorage.getBits();

            long packed = 0;
            int packedIndex = 0;
            int remaining = 0;
            for (int i = 0; i <= 0xFFF; i++) {
                if (remaining-- == 0) {
                    packed = packedData[packedIndex++];
                    remaining = valuesPerLongMinusOne;
                }

                int packedPaletteIndex = (int) (packed & paletteMask);
                int blockId;
                if (globalPalette == null) {
                    blockId = paletteCache[Math.min(packedPaletteIndex, maximumLocalPaletteIndex)];
                } else {
                    blockId = stateMapper.getIdForBlockState(globalPalette.valueFor(packedPaletteIndex));
                }
                packed >>>= entryBits;

                byte light = lightSupplier.supply(i & 0xF, (i >>> 8) & 0xF, (i >>> 4) & 0xF);
                nonZeroCnt += blockId != 0 ? 1 : 0;
                data[i] = Mapper.composeMappingId(light, blockId, biomes[biomeIndexForLinearIndex(i)]);
            }
        } else if (blockContainer.data.storage instanceof ZeroBitStorage) {
            int blockId = paletteCache[0];
            if (blockId == 0) {
                for (int i = 0; i <= 0xFFF; i++) {
                    data[i] = Mapper.airWithLight(lightSupplier.supply(i & 0xF, (i >>> 8) & 0xF, (i >>> 4) & 0xF));
                }
            } else {
                nonZeroCnt = 4096;
                for (int i = 0; i <= 0xFFF; i++) {
                    byte light = lightSupplier.supply(i & 0xF, (i >>> 8) & 0xF, (i >>> 4) & 0xF);
                    data[i] = Mapper.composeMappingId(light, blockId, biomes[biomeIndexForLinearIndex(i)]);
                }
            }
        } else {
            throw new IllegalStateException(
                    "Unknown block palette storage type: " + blockContainer.data.storage.getClass().getName());
        }
        section.lvl0NonAirCount = nonZeroCnt;
        return section;
    }

    static int biomeIndexForLinearIndex(int index) {
        // Java 17 equivalent of original Voxy's Java 21
        // Integer.compress(index, 0b1100_1100_1100).
        return ((index >>> 2) & 0x3) | ((index >>> 4) & 0xC) | ((index >>> 6) & 0x30);
    }

    private static void computeZoomCells(int[] biomes, long zoomSeed, long[] zoomInfo) {
        // Kept as the original Voxy hook. The upstream implementation currently does not populate
        // zoomInfo, so shouldZoom has no effect on emitted voxels yet.
        for (int cy = 0; cy < 4; cy++) {
            for (int cz = 0; cz < 4; cz++) {
                for (int cx = 0; cx < 4; cx++) {
                    // Original placeholder.
                }
            }
        }
    }

    //Support for other mods etc that use this entry point
    @Deprecated(forRemoval = true)
    public static void mipSection(VoxelizedSection section, Mapper mapper) {
        WorldVoxilizedSectionMipper.mipSection(section, mapper);
    }
}
