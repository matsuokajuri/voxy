package me.cortex.voxy.forge;

import org.junit.jupiter.api.Test;
import org.lwjgl.system.MemoryUtil;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class Round9XScanBookkeepingTest {
    private static final int MODEL_ID = 37;
    private static final int SECTION_VOLUME = 32 * 32 * 32;
    private static final int BUFFER_QUAD_CAPACITY = 1 << 16;
    private static final long MODEL_METADATA = repeatedFaceMetadata(0b1_0111, 0b100_0000);
    private static final Unsafe UNSAFE = unsafe();

    @Test
    void thirtyThirtyOneAndThirtyTwoCellRunsRetainExactCoverage() throws Exception {
        for (int length : new int[]{30, 31, 32}) {
            boolean[] solid = new boolean[SECTION_VOLUME];
            for (int z = 0; z < length; z++) {
                solid[index(10, 7, z)] = true;
            }
            assertExactFaceCoverage(solid, "z-run-" + length);
        }
    }

    @Test
    void sparseRowsAroundTheFiveBitBoundaryRetainExactCoverage() throws Exception {
        boolean[] solid = new boolean[SECTION_VOLUME];
        int[] xs = {0, 1, 10, 21, 30, 31};
        int[] zs = {0, 1, 15, 29, 30, 31};
        for (int y = 0; y < 32; y += 3) {
            for (int i = 0; i < xs.length; i++) {
                if (((y + i) & 1) == 0) {
                    solid[index(xs[i], y, zs[i])] = true;
                }
            }
        }
        assertExactFaceCoverage(solid, "sparse-five-bit-boundaries");
    }

    @Test
    void deterministicRandomVolumesMatchTheCellFaceOracle() throws Exception {
        for (long seed : new long[]{0x4F525859L, 0x52444639L, 0x5EEDC0DEL}) {
            Random random = new Random(seed);
            boolean[] solid = new boolean[SECTION_VOLUME];
            for (int i = 0; i < solid.length; i++) {
                solid[i] = random.nextInt(8) == 0;
            }
            assertExactFaceCoverage(solid, "seed-" + Long.toUnsignedString(seed));
        }
    }

    private static void assertExactFaceCoverage(boolean[] solid, String fixture) throws Exception {
        RenderDataFactory factory = new RenderDataFactory(null, fakeModelFactory(), false);
        try {
            populate(factory, solid);
            setBoundsToEmpty(factory);
            invoke(factory, "generateYZFaces");
            invoke(factory, "generateXFaces");

            int[][] expected = expectedCoverage(solid);
            int[][] actual = decodedCoverage(allQuads(factory));
            for (int face = 0; face < ForgeModelAtlasLayout.FACE_COUNT; face++) {
                assertArrayEquals(expected[face], actual[face], fixture + " face=" + face);
            }
        } finally {
            factory.free();
        }
    }

    private static void populate(RenderDataFactory factory, boolean[] solid) throws Exception {
        long[] sectionData = field(factory, "sectionData", long[].class);
        int[] opaqueMasks = field(factory, "opaqueMasks", int[].class);
        long packed = ((long) MODEL_ID << 26) | RenderDataFactory.getQuadTyping(MODEL_METADATA);
        for (int y = 0; y < 32; y++) {
            for (int z = 0; z < 32; z++) {
                int mask = 0;
                for (int x = 0; x < 32; x++) {
                    int index = index(x, y, z);
                    if (!solid[index]) {
                        continue;
                    }
                    mask |= 1 << x;
                    sectionData[index * 2] = packed;
                    sectionData[index * 2 + 1] = MODEL_METADATA;
                }
                opaqueMasks[y * 32 + z] = mask;
            }
        }
    }

    private static int[][] expectedCoverage(boolean[] solid) {
        int[][] expected = new int[ForgeModelAtlasLayout.FACE_COUNT][SECTION_VOLUME];
        int[][] direction = {
                {0, -1, 0}, {0, 1, 0},
                {0, 0, -1}, {0, 0, 1},
                {-1, 0, 0}, {1, 0, 0}
        };
        for (int y = 0; y < 32; y++) {
            for (int z = 0; z < 32; z++) {
                for (int x = 0; x < 32; x++) {
                    int index = index(x, y, z);
                    if (!solid[index]) {
                        continue;
                    }
                    for (int face = 0; face < direction.length; face++) {
                        int nx = x + direction[face][0];
                        int ny = y + direction[face][1];
                        int nz = z + direction[face][2];
                        if (nx < 0 || nx >= 32 || ny < 0 || ny >= 32 || nz < 0 || nz >= 32
                                || !solid[index(nx, ny, nz)]) {
                            expected[face][index] = 1;
                        }
                    }
                }
            }
        }
        return expected;
    }

    private static int[][] decodedCoverage(List<Long> quads) {
        int[][] actual = new int[ForgeModelAtlasLayout.FACE_COUNT][SECTION_VOLUME];
        for (long quad : quads) {
            assertEquals(MODEL_ID, RenderFaceDecision.modelId(quad));
            int face = (int) (quad & 0b111L);
            int axis = face >> 1;
            int length = (int) ((quad >>> 3) & 0xFL) + 1;
            int width = (int) ((quad >>> 7) & 0xFL) + 1;
            int z = (int) ((quad >>> 11) & 0x1FL);
            int y = (int) ((quad >>> 16) & 0x1FL);
            int x = (int) ((quad >>> 21) & 0x1FL);
            switch (axis) {
                case 0 -> {
                    for (int dz = 0; dz < width; dz++) {
                        for (int dx = 0; dx < length; dx++) {
                            actual[face][checkedIndex(x + dx, y, z + dz)]++;
                        }
                    }
                }
                case 1 -> {
                    for (int dy = 0; dy < width; dy++) {
                        for (int dx = 0; dx < length; dx++) {
                            actual[face][checkedIndex(x + dx, y + dy, z)]++;
                        }
                    }
                }
                case 2 -> {
                    for (int dz = 0; dz < width; dz++) {
                        for (int dy = 0; dy < length; dy++) {
                            actual[face][checkedIndex(x, y + dy, z + dz)]++;
                        }
                    }
                }
                default -> throw new AssertionError("invalid axis " + axis);
            }
        }
        return actual;
    }

    private static int checkedIndex(int x, int y, int z) {
        assertTrue(x >= 0 && x < 32, "x=" + x);
        assertTrue(y >= 0 && y < 32, "y=" + y);
        assertTrue(z >= 0 && z < 32, "z=" + z);
        return index(x, y, z);
    }

    private static int index(int x, int y, int z) {
        return x | (z << 5) | (y << 10);
    }

    private static List<Long> allQuads(RenderDataFactory factory) throws Exception {
        int[] counters = field(factory, "quadCounters", int[].class);
        long pointer = field(factory, "quadBufferPtr", Long.class);
        List<Long> quads = new ArrayList<>();
        for (int bucket = 0; bucket < counters.length; bucket++) {
            long bucketOffset = (long) bucket * BUFFER_QUAD_CAPACITY * Long.BYTES;
            for (int index = 0; index < counters[bucket]; index++) {
                quads.add(MemoryUtil.memGetLong(
                        pointer + bucketOffset + (long) index * Long.BYTES));
            }
        }
        return quads;
    }

    private static ModelFactory fakeModelFactory() throws Exception {
        ModelFactory factory = (ModelFactory) UNSAFE.allocateInstance(ModelFactory.class);
        int[] mappings = new int[1 << 20];
        Arrays.fill(mappings, -1);
        long[] metadata = new long[1 << 16];
        metadata[MODEL_ID] = MODEL_METADATA;
        long[][] masks = new long[1 << 16][];
        masks[MODEL_ID] = new long[FaceOcclusionMask.WORDS_PER_MODEL];
        Arrays.fill(masks[MODEL_ID], -1L);
        int[] fluidLut = new int[1 << 16];
        Arrays.fill(fluidLut, -1);
        putObject(factory, "idMappings", mappings);
        putObject(factory, "metadataCache", metadata);
        putObject(factory, "faceOcclusionMasks", masks);
        putObject(factory, "fluidStateLUT", fluidLut);
        return factory;
    }

    private static long repeatedFaceMetadata(int faceByte, int global) {
        long metadata = ((long) global & 0xFFFFL) << 48;
        for (int face = 0; face < ForgeModelAtlasLayout.FACE_COUNT; face++) {
            metadata |= ((long) faceByte & 0xFFL) << (face * Byte.SIZE);
        }
        return metadata;
    }

    private static void setBoundsToEmpty(RenderDataFactory factory) throws Exception {
        for (String name : new String[]{"minX", "minY", "minZ"}) {
            Field field = RenderDataFactory.class.getDeclaredField(name);
            field.setAccessible(true);
            field.setInt(factory, Integer.MAX_VALUE);
        }
        for (String name : new String[]{"maxX", "maxY", "maxZ"}) {
            Field field = RenderDataFactory.class.getDeclaredField(name);
            field.setAccessible(true);
            field.setInt(factory, Integer.MIN_VALUE);
        }
    }

    private static void putObject(Object target, String fieldName, Object value) throws Exception {
        Field field = ModelFactory.class.getDeclaredField(fieldName);
        UNSAFE.putObject(target, UNSAFE.objectFieldOffset(field), value);
    }

    private static void invoke(RenderDataFactory factory, String methodName) throws Exception {
        Method method = RenderDataFactory.class.getDeclaredMethod(methodName);
        method.setAccessible(true);
        method.invoke(factory);
    }

    @SuppressWarnings("unchecked")
    private static <T> T field(RenderDataFactory factory, String name, Class<T> type) throws Exception {
        Field field = RenderDataFactory.class.getDeclaredField(name);
        field.setAccessible(true);
        Object value = field.get(factory);
        if (type == Long.class) {
            return (T) Long.valueOf((long) value);
        }
        return type.cast(value);
    }

    private static Unsafe unsafe() {
        try {
            Field field = Unsafe.class.getDeclaredField("theUnsafe");
            field.setAccessible(true);
            return (Unsafe) field.get(null);
        } catch (ReflectiveOperationException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }
}
