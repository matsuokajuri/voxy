package me.cortex.voxy.common.world.other;

import me.cortex.voxy.common.config.storage.inmemory.MemoryStorageBackend;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("round7-performance")
class MipperPerformanceTest {
    private static final int CELL_COUNT = 1 << 12;
    private static final int MEASURE_PASSES = 255;
    private static volatile long blackhole;

    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void compareLevelAwareSelectorWithInheritedOpacitySelector() {
        MemoryStorageBackend backend = new MemoryStorageBackend();
        try {
            Mapper mapper = new Mapper(backend);
            int[] blocks = {
                    0,
                    mapper.getIdForBlockState(Blocks.STONE.defaultBlockState()),
                    mapper.getIdForBlockState(Blocks.GLASS.defaultBlockState()),
                    mapper.getIdForBlockState(Blocks.OAK_LEAVES.defaultBlockState()),
                    mapper.getIdForBlockState(Blocks.WATER.defaultBlockState()),
                    mapper.getIdForBlockState(Blocks.GLOWSTONE.defaultBlockState()),
                    mapper.getIdForBlockState(Blocks.IRON_BARS.defaultBlockState())
            };
            long[] cells = new long[CELL_COUNT * 8];
            Random random = new Random(0x71C0FFEE5L);
            for (int index = 0; index < cells.length; index++) {
                int block = blocks[random.nextInt(blocks.length)];
                int light = random.nextInt(256);
                cells[index] = block == 0
                        ? Mapper.airWithLight(light)
                        : Mapper.composeMappingId((byte) light, block, random.nextInt(16));
            }

            measureLevelAware(mapper, cells, 32);
            measureInherited(mapper, cells, 32);
            long levelAwareNanos = Long.MAX_VALUE;
            long inheritedNanos = Long.MAX_VALUE;
            for (int sample = 0; sample < 5; sample++) {
                if ((sample & 1) == 0) {
                    inheritedNanos = Math.min(inheritedNanos, measureInherited(mapper, cells, MEASURE_PASSES));
                    levelAwareNanos = Math.min(levelAwareNanos, measureLevelAware(mapper, cells, MEASURE_PASSES));
                } else {
                    levelAwareNanos = Math.min(levelAwareNanos, measureLevelAware(mapper, cells, MEASURE_PASSES));
                    inheritedNanos = Math.min(inheritedNanos, measureInherited(mapper, cells, MEASURE_PASSES));
                }
            }
            long operations = (long) CELL_COUNT * MEASURE_PASSES;
            double levelAwarePerOperation = (double) levelAwareNanos / operations;
            double inheritedPerOperation = (double) inheritedNanos / operations;
            double ratio = levelAwarePerOperation / inheritedPerOperation;
            System.out.printf(
                    "Forxy round 7 mipping benchmark: levelAware=%.1f ns/op inherited=%.1f ns/op ratio=%.2fx mipSection=%.1f us operations=%d checksum=%016x%n",
                    levelAwarePerOperation,
                    inheritedPerOperation,
                    ratio,
                    levelAwarePerOperation * 585.0 / 1_000.0,
                    operations,
                    blackhole);
            assertTrue(
                    levelAwarePerOperation <= 120.0 && ratio <= 12.0,
                    "Level-aware mipping exceeded the accepted 120 ns/op and 12x CPU budget: "
                            + levelAwarePerOperation + " ns/op, " + ratio + "x");
        } finally {
            backend.close();
        }
    }

    private static long measureLevelAware(Mapper mapper, long[] cells, int passes) {
        long checksum = 0;
        long start = System.nanoTime();
        for (int pass = 0; pass < passes; pass++) {
            for (int offset = 0; offset < cells.length; offset += 8) {
                checksum += Long.rotateLeft(Mipper.mip(
                        cells[offset], cells[offset + 1], cells[offset + 2], cells[offset + 3],
                        cells[offset + 4], cells[offset + 5], cells[offset + 6], cells[offset + 7],
                        mapper, 1 + ((offset >>> 3) & 3)), pass & 63) + offset;
            }
        }
        blackhole = checksum;
        return System.nanoTime() - start;
    }

    private static long measureInherited(Mapper mapper, long[] cells, int passes) {
        long checksum = 0;
        long start = System.nanoTime();
        for (int pass = 0; pass < passes; pass++) {
            for (int offset = 0; offset < cells.length; offset += 8) {
                checksum += Long.rotateLeft(inheritedOpacityMip(
                        cells[offset], cells[offset + 1], cells[offset + 2], cells[offset + 3],
                        cells[offset + 4], cells[offset + 5], cells[offset + 6], cells[offset + 7],
                        mapper), pass & 63) + offset;
            }
        }
        blackhole = checksum;
        return System.nanoTime() - start;
    }

    private static long inheritedOpacityMip(
            long I000, long I100, long I001, long I101,
            long I010, long I110, long I011, long I111,
            Mapper mapper) {
        long selected = I111;
        int best = Mapper.isAir(I111) ? -1 : mapper.getBlockStateOpacity(I111) << 4 | 7;
        if (!Mapper.isAir(I110) && (mapper.getBlockStateOpacity(I110) << 4 | 6) > best) {
            best = mapper.getBlockStateOpacity(I110) << 4 | 6;
            selected = I110;
        }
        if (!Mapper.isAir(I011) && (mapper.getBlockStateOpacity(I011) << 4 | 3) > best) {
            best = mapper.getBlockStateOpacity(I011) << 4 | 3;
            selected = I011;
        }
        if (!Mapper.isAir(I010) && (mapper.getBlockStateOpacity(I010) << 4 | 2) > best) {
            best = mapper.getBlockStateOpacity(I010) << 4 | 2;
            selected = I010;
        }
        if (!Mapper.isAir(I101) && (mapper.getBlockStateOpacity(I101) << 4 | 5) > best) {
            best = mapper.getBlockStateOpacity(I101) << 4 | 5;
            selected = I101;
        }
        if (!Mapper.isAir(I100) && (mapper.getBlockStateOpacity(I100) << 4 | 4) > best) {
            best = mapper.getBlockStateOpacity(I100) << 4 | 4;
            selected = I100;
        }
        if (!Mapper.isAir(I001) && (mapper.getBlockStateOpacity(I001) << 4 | 1) > best) {
            best = mapper.getBlockStateOpacity(I001) << 4 | 1;
            selected = I001;
        }
        if (!Mapper.isAir(I000) && mapper.getBlockStateOpacity(I000) << 4 > best) {
            selected = I000;
        }
        return selected;
    }
}
