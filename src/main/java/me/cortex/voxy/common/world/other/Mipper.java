package me.cortex.voxy.common.world.other;

/** Selects a deterministic representative voxel for one 2x2x2 source cell. */
public final class Mipper {
    private static final int VISUAL_COVERAGE_MASK = 0xFF;
    private static final int BLOCK_ID_MASK = (1 << 20) - 1;

    private Mipper() {
    }

    /**
     * @param targetLevel the level written by this mip operation; level one represents a 2-block cell
     */
    public static long mip(long I000, long I100, long I001, long I101,
                           long I010, long I110, long I011, long I111,
                           Mapper mapper, int targetLevel) {
        if (targetLevel < 1) {
            throw new IllegalArgumentException("Mipping target level must be positive");
        }

        int B000 = Mapper.getBlockId(I000);
        int B100 = Mapper.getBlockId(I100);
        int B001 = Mapper.getBlockId(I001);
        int B101 = Mapper.getBlockId(I101);
        int B010 = Mapper.getBlockId(I010);
        int B110 = Mapper.getBlockId(I110);
        int B011 = Mapper.getBlockId(I011);
        int B111 = Mapper.getBlockId(I111);

        int nonAirCount = nonAir(B000) + nonAir(B100) + nonAir(B001) + nonAir(B101)
                + nonAir(B010) + nonAir(B110) + nonAir(B011) + nonAir(B111);

        long pair = sortPair(B000, B100);
        B000 = (int) (pair >>> 32);
        B100 = (int) pair;
        pair = sortPair(B001, B101);
        B001 = (int) (pair >>> 32);
        B101 = (int) pair;
        pair = sortPair(B010, B110);
        B010 = (int) (pair >>> 32);
        B110 = (int) pair;
        pair = sortPair(B011, B111);
        B011 = (int) (pair >>> 32);
        B111 = (int) pair;
        pair = sortPair(B000, B001);
        B000 = (int) (pair >>> 32);
        B001 = (int) pair;
        pair = sortPair(B100, B101);
        B100 = (int) (pair >>> 32);
        B101 = (int) pair;
        pair = sortPair(B010, B011);
        B010 = (int) (pair >>> 32);
        B011 = (int) pair;
        pair = sortPair(B110, B111);
        B110 = (int) (pair >>> 32);
        B111 = (int) pair;
        pair = sortPair(B100, B001);
        B100 = (int) (pair >>> 32);
        B001 = (int) pair;
        pair = sortPair(B110, B011);
        B110 = (int) (pair >>> 32);
        B011 = (int) pair;
        pair = sortPair(B000, B010);
        B000 = (int) (pair >>> 32);
        B010 = (int) pair;
        pair = sortPair(B101, B111);
        B101 = (int) (pair >>> 32);
        B111 = (int) pair;
        pair = sortPair(B100, B110);
        B100 = (int) (pair >>> 32);
        B110 = (int) pair;
        pair = sortPair(B001, B011);
        B001 = (int) (pair >>> 32);
        B011 = (int) pair;
        pair = sortPair(B100, B010);
        B100 = (int) (pair >>> 32);
        B010 = (int) pair;
        pair = sortPair(B101, B011);
        B101 = (int) (pair >>> 32);
        B011 = (int) pair;
        pair = sortPair(B001, B010);
        B001 = (int) (pair >>> 32);
        B010 = (int) pair;
        pair = sortPair(B101, B110);
        B101 = (int) (pair >>> 32);
        B110 = (int) pair;
        pair = sortPair(B101, B010);
        B101 = (int) (pair >>> 32);
        B010 = (int) pair;

        int selectedBlock = 0;
        long bestRank = -1L;
        int currentBlock = B000;
        int currentCount = 1;
        for (int index = 1; index < 8; index++) {
            int nextBlock = switch (index) {
                case 1 -> B100;
                case 2 -> B001;
                case 3 -> B101;
                case 4 -> B010;
                case 5 -> B110;
                case 6 -> B011;
                case 7 -> B111;
                default -> throw new IllegalStateException();
            };
            if (nextBlock == currentBlock) {
                currentCount++;
                continue;
            }
            long rank = rankBlock(currentBlock, currentCount, mapper, targetLevel);
            if (rank > bestRank) {
                bestRank = rank;
                selectedBlock = currentBlock;
            }
            currentBlock = nextBlock;
            currentCount = 1;
        }
        long rank = rankBlock(currentBlock, currentCount, mapper, targetLevel);
        if (rank > bestRank) {
            selectedBlock = currentBlock;
        }

        int selectedProperties = selectedBlock == 0 ? 0 : mapper.getBlockStateMipProperties(selectedBlock);
        if (selectedBlock != 0 && nonAirCount < requiredSupport(selectedProperties, targetLevel)) {
            selectedBlock = 0;
        }

        int selectedBiome = selectedBlock == 0 ? 0 : selectBiome(selectedBlock,
                I000, I100, I001, I101, I010, I110, I011, I111);
        int light = aggregateLight(selectedBlock, selectedProperties,
                I000, I100, I001, I101, I010, I110, I011, I111);
        return Mapper.composeMappingId((byte) light, selectedBlock, selectedBiome);
    }

    private static long rankBlock(
            int block,
            int count,
            Mapper mapper,
            int targetLevel) {
        if (block == 0) {
            return -1L;
        }
        int properties = mapper.getBlockStateMipProperties(block);
        int coverage = properties & VISUAL_COVERAGE_MASK;
        int opacity = (properties >>> 8) & 15;
        int emission = (properties >>> 12) & 15;
        int fluid = (properties >>> 16) & 1;

        // Occurrence count owns the result. Material properties resolve equal support without
        // making one isolated opaque voxel erase a repeated translucent or fluid surface.
        int materialPriority = (emission << 13) | (opacity << 8) | coverage;
        if (fluid != 0) {
            materialPriority += Math.max(0, 4 - targetLevel) << 5;
        }
        return ((long) count << 40)
                | ((long) materialPriority << 20)
                | (BLOCK_ID_MASK - block);
    }

    private static long sortPair(int left, int right) {
        return ((long) Math.min(left, right) << 32) | Integer.toUnsignedLong(Math.max(left, right));
    }

    private static int requiredSupport(int properties, int targetLevel) {
        int emission = (properties >>> 12) & 15;
        if (emission != 0) {
            if (targetLevel <= 4) {
                return 1;
            }
            return Math.min(8, Math.max(2, baseSupport(targetLevel) - 1));
        }

        int support = baseSupport(targetLevel);
        int coverage = properties & VISUAL_COVERAGE_MASK;
        if (coverage <= 32 && targetLevel >= 4) {
            support++;
        }
        return Math.min(8, support);
    }

    private static int baseSupport(int targetLevel) {
        if (targetLevel <= 2) {
            return 1;
        }
        if (targetLevel == 3) {
            return 2;
        }
        if (targetLevel == 4) {
            return 3;
        }
        return 4;
    }

    private static int selectBiome(
            int selectedBlock,
            long I000, long I100, long I001, long I101,
            long I010, long I110, long I011, long I111) {
        int selectedBiome = 1 << 9;
        selectedBiome = minimumBiome(selectedBlock, I000, selectedBiome);
        selectedBiome = minimumBiome(selectedBlock, I100, selectedBiome);
        selectedBiome = minimumBiome(selectedBlock, I001, selectedBiome);
        selectedBiome = minimumBiome(selectedBlock, I101, selectedBiome);
        selectedBiome = minimumBiome(selectedBlock, I010, selectedBiome);
        selectedBiome = minimumBiome(selectedBlock, I110, selectedBiome);
        selectedBiome = minimumBiome(selectedBlock, I011, selectedBiome);
        selectedBiome = minimumBiome(selectedBlock, I111, selectedBiome);
        return selectedBiome == 1 << 9 ? 0 : selectedBiome;
    }

    private static int aggregateLight(
            int selectedBlock,
            int selectedProperties,
            long I000, long I100, long I001, long I101,
            long I010, long I110, long I011, long I111) {
        int L000 = Mapper.getLightId(I000);
        int L100 = Mapper.getLightId(I100);
        int L001 = Mapper.getLightId(I001);
        int L101 = Mapper.getLightId(I101);
        int L010 = Mapper.getLightId(I010);
        int L110 = Mapper.getLightId(I110);
        int L011 = Mapper.getLightId(I011);
        int L111 = Mapper.getLightId(I111);
        if (selectedBlock == 0) {
            int blockLight = max8(
                    L000 >>> 4, L100 >>> 4, L001 >>> 4, L101 >>> 4,
                    L010 >>> 4, L110 >>> 4, L011 >>> 4, L111 >>> 4);
            int skyLight = ((L000 & 15) + (L100 & 15) + (L001 & 15) + (L101 & 15)
                    + (L010 & 15) + (L110 & 15) + (L011 & 15) + (L111 & 15) + 7) / 8;
            return (blockLight << 4) | skyLight;
        }

        int count = selected(selectedBlock, I000) + selected(selectedBlock, I100)
                + selected(selectedBlock, I001) + selected(selectedBlock, I101)
                + selected(selectedBlock, I010) + selected(selectedBlock, I110)
                + selected(selectedBlock, I011) + selected(selectedBlock, I111);
        if (count == 0) {
            throw new IllegalStateException("Selected mip material was not present in its source cell");
        }
        int blockLightSum = selectedBlockLight(selectedBlock, I000, L000)
                + selectedBlockLight(selectedBlock, I100, L100)
                + selectedBlockLight(selectedBlock, I001, L001)
                + selectedBlockLight(selectedBlock, I101, L101)
                + selectedBlockLight(selectedBlock, I010, L010)
                + selectedBlockLight(selectedBlock, I110, L110)
                + selectedBlockLight(selectedBlock, I011, L011)
                + selectedBlockLight(selectedBlock, I111, L111);
        int skyLightSum = selectedSkyLight(selectedBlock, I000, L000)
                + selectedSkyLight(selectedBlock, I100, L100)
                + selectedSkyLight(selectedBlock, I001, L001)
                + selectedSkyLight(selectedBlock, I101, L101)
                + selectedSkyLight(selectedBlock, I010, L010)
                + selectedSkyLight(selectedBlock, I110, L110)
                + selectedSkyLight(selectedBlock, I011, L011)
                + selectedSkyLight(selectedBlock, I111, L111);
        int emission = (selectedProperties >>> 12) & 15;
        int blockLight = Math.max(emission, (blockLightSum + count / 2) / count);
        int skyLight = (skyLightSum + count / 2) / count;
        return (blockLight << 4) | skyLight;
    }

    private static int max8(int a, int b, int c, int d, int e, int f, int g, int h) {
        return Math.max(Math.max(Math.max(a, b), Math.max(c, d)),
                Math.max(Math.max(e, f), Math.max(g, h)));
    }

    private static int selected(int selectedBlock, long mapping) {
        return Mapper.getBlockId(mapping) == selectedBlock ? 1 : 0;
    }

    private static int selectedBlockLight(int selectedBlock, long mapping, int light) {
        return Mapper.getBlockId(mapping) == selectedBlock ? light >>> 4 : 0;
    }

    private static int selectedSkyLight(int selectedBlock, long mapping, int light) {
        return Mapper.getBlockId(mapping) == selectedBlock ? light & 15 : 0;
    }

    private static int minimumBiome(int selectedBlock, long mapping, int current) {
        return Mapper.getBlockId(mapping) == selectedBlock
                ? Math.min(current, Mapper.getBiomeId(mapping))
                : current;
    }

    private static int nonAir(int block) {
        return block == 0 ? 0 : 1;
    }
}
