package me.cortex.voxy.forge;

import me.cortex.voxy.common.world.other.Mapper;
import org.junit.jupiter.api.Test;
import org.lwjgl.system.MemoryUtil;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class Round9SectionBorderOwnershipTest {
    private static final int MODEL_ID = 23;
    private static final int BLOCK_ID = 1;
    private static final int COVERING_MODEL_ID = 24;
    private static final int COVERING_BLOCK_ID = 2;
    private static final int PARTIAL_MODEL_ID = 25;
    private static final int PARTIAL_BLOCK_ID = 3;
    private static final int PLANE_SIZE = 32 * 32;
    private static final int BUFFER_QUAD_CAPACITY = 1 << 16;
    private static final int[] NEIGHBOR_SLOT_BY_FACE = {2, 3, 4, 5, 0, 1};
    private static final long FULL_OPAQUE_METADATA = repeatedFaceMetadata(0b1_0111, 0b100_0000);
    private static final long PARTIAL_METADATA = repeatedFaceMetadata(0b1_0100, 0);
    private static final Unsafe UNSAFE = unsafe();

    @Test
    void allSixBordersGiveAnExposedFaceToTheSolidSectionOnly() throws Exception {
        for (int face = 0; face < ForgeModelAtlasLayout.FACE_COUNT; face++) {
            BorderSnapshot solid = meshBorder(face, true, false);
            BorderSnapshot adjacentAir = meshBorder(face ^ 1, false, true);

            assertEquals(1, solid.faceQuads(), "solid owner face " + face);
            assertEquals(face, solid.firstFace(), "solid direction " + face);
            assertEquals(0, adjacentAir.faceQuads(), "air neighbour cannot own face " + face);
            assertEquals(1, solid.faceQuads() + adjacentAir.faceQuads(),
                    "one owner independent of adjacent rebuild order " + face);
        }
    }

    @Test
    void allSixFullyCoveredBordersHaveNoOwnerOnEitherSide() throws Exception {
        for (int face = 0; face < ForgeModelAtlasLayout.FACE_COUNT; face++) {
            BorderSnapshot first = meshBorder(face, true, true);
            BorderSnapshot second = meshBorder(face ^ 1, true, true);

            assertEquals(0, first.faceQuads(), "first covered side " + face);
            assertEquals(0, second.faceQuads(), "second covered side " + face);
            assertEquals(0, first.faceQuads() + second.faceQuads(),
                    "covered border must not duplicate geometry " + face);
        }
    }

    @Test
    void allSixNonOpaqueBordersUseExactCoverageWithoutLosingExposedFaces() throws Exception {
        for (int face = 0; face < ForgeModelAtlasLayout.FACE_COUNT; face++) {
            BorderSnapshot exposed = meshNonOpaqueBorder(face, true, NeighborKind.AIR);
            BorderSnapshot adjacentAir = meshNonOpaqueBorder(face ^ 1, false, NeighborKind.COVERING);
            BorderSnapshot covered = meshNonOpaqueBorder(face, true, NeighborKind.COVERING);
            BorderSnapshot partiallyCovered = meshNonOpaqueBorder(face, true, NeighborKind.PARTIAL);

            assertEquals(1, exposed.faceQuads(), "non-opaque exposed face " + face);
            assertEquals(face, exposed.firstFace(), "non-opaque direction " + face);
            assertEquals(0, adjacentAir.faceQuads(), "non-opaque air side cannot own face " + face);
            assertEquals(0, covered.faceQuads(), "exactly covered non-opaque face " + face);
            assertEquals(1, partiallyCovered.faceQuads(), "insufficient exact coverage stays visible " + face);
        }
    }

    private static BorderSnapshot meshBorder(int face, boolean currentSolid, boolean neighborSolid)
            throws Exception {
        ModelFactory models = fakeModelFactory();
        RenderDataFactory factory = new RenderDataFactory(null, models, false);
        try {
            int x = face == 4 ? 0 : face == 5 ? 31 : 7;
            int y = face == 0 ? 0 : face == 1 ? 31 : 11;
            int z = face == 2 ? 0 : face == 3 ? 31 : 13;
            int index = x | (z << 5) | (y << 10);

            if (currentSolid) {
                long[] sectionData = field(factory, "sectionData", long[].class);
                int[] opaqueMasks = field(factory, "opaqueMasks", int[].class);
                sectionData[index * 2] = ((long) MODEL_ID << 26)
                        | RenderDataFactory.getQuadTyping(FULL_OPAQUE_METADATA)
                        | (0xA5L << 55);
                sectionData[index * 2 + 1] = FULL_OPAQUE_METADATA;
                opaqueMasks[y * 32 + z] |= 1 << x;
            }

            if (neighborSolid) {
                long[] neighboringFaces = field(factory, "neighboringFaces", long[].class);
                int planeIndex = switch (face >> 1) {
                    case 0 -> z * 32 + x;
                    case 1 -> y * 32 + x;
                    case 2 -> y * 32 + z;
                    default -> throw new IllegalArgumentException("Invalid face " + face);
                };
                int slot = NEIGHBOR_SLOT_BY_FACE[face] * PLANE_SIZE + planeIndex;
                neighboringFaces[slot] = Mapper.composeMappingId((byte) 0x3C, BLOCK_ID, 0);
            }

            invoke(factory, face < 4 ? "generateYZFaces" : "generateXFaces");
            int[] counters = field(factory, "quadCounters", int[].class);
            int faceQuads = counters[face + 2];
            int firstFace = -1;
            if (faceQuads != 0) {
                long buffer = field(factory, "quadBufferPtr", Long.class);
                long offset = (long) (face + 2) * BUFFER_QUAD_CAPACITY * Long.BYTES;
                firstFace = (int) (MemoryUtil.memGetLong(buffer + offset) & 0b111L);
            }
            return new BorderSnapshot(faceQuads, firstFace);
        } finally {
            factory.free();
        }
    }

    private static BorderSnapshot meshNonOpaqueBorder(
            int face,
            boolean currentPresent,
            NeighborKind neighborKind
    ) throws Exception {
        ModelFactory models = fakePartialModelFactory();
        RenderDataFactory factory = new RenderDataFactory(null, models, false);
        try {
            int x = face == 4 ? 0 : face == 5 ? 31 : 7;
            int y = face == 0 ? 0 : face == 1 ? 31 : 11;
            int z = face == 2 ? 0 : face == 3 ? 31 : 13;
            int index = x | (z << 5) | (y << 10);

            if (currentPresent) {
                long[] sectionData = field(factory, "sectionData", long[].class);
                int[] nonOpaqueMasks = field(factory, "nonOpaqueMasks", int[].class);
                sectionData[index * 2] = ((long) MODEL_ID << 26)
                        | RenderDataFactory.getQuadTyping(PARTIAL_METADATA)
                        | (0xA5L << 55);
                sectionData[index * 2 + 1] = PARTIAL_METADATA;
                nonOpaqueMasks[y * 32 + z] |= 1 << x;
            }

            if (neighborKind != NeighborKind.AIR) {
                long[] neighboringFaces = field(factory, "neighboringFaces", long[].class);
                int planeIndex = switch (face >> 1) {
                    case 0 -> z * 32 + x;
                    case 1 -> y * 32 + x;
                    case 2 -> y * 32 + z;
                    default -> throw new IllegalArgumentException("Invalid face " + face);
                };
                int slot = NEIGHBOR_SLOT_BY_FACE[face] * PLANE_SIZE + planeIndex;
                int blockId = neighborKind == NeighborKind.COVERING
                        ? COVERING_BLOCK_ID
                        : PARTIAL_BLOCK_ID;
                neighboringFaces[slot] = Mapper.composeMappingId((byte) 0x3C, blockId, 0);
            }

            invoke(factory, face < 4 ? "generateYZFaces" : "generateXFaces");
            int[] counters = field(factory, "quadCounters", int[].class);
            int faceQuads = counters[face + 2];
            int firstFace = -1;
            if (faceQuads != 0) {
                long buffer = field(factory, "quadBufferPtr", Long.class);
                long offset = (long) (face + 2) * BUFFER_QUAD_CAPACITY * Long.BYTES;
                firstFace = (int) (MemoryUtil.memGetLong(buffer + offset) & 0b111L);
            }
            return new BorderSnapshot(faceQuads, firstFace);
        } finally {
            factory.free();
        }
    }

    private static ModelFactory fakeModelFactory() throws Exception {
        ModelFactory factory = (ModelFactory) UNSAFE.allocateInstance(ModelFactory.class);
        int[] mappings = new int[1 << 20];
        Arrays.fill(mappings, -1);
        mappings[BLOCK_ID] = MODEL_ID;
        long[] metadata = new long[1 << 16];
        metadata[MODEL_ID] = FULL_OPAQUE_METADATA;
        long[][] masks = new long[1 << 16][];
        masks[MODEL_ID] = fullMasks();
        putObject(factory, "idMappings", mappings);
        putObject(factory, "metadataCache", metadata);
        putObject(factory, "faceOcclusionMasks", masks);
        return factory;
    }

    private static ModelFactory fakePartialModelFactory() throws Exception {
        ModelFactory factory = (ModelFactory) UNSAFE.allocateInstance(ModelFactory.class);
        int[] mappings = new int[1 << 20];
        Arrays.fill(mappings, -1);
        mappings[BLOCK_ID] = MODEL_ID;
        mappings[COVERING_BLOCK_ID] = COVERING_MODEL_ID;
        mappings[PARTIAL_BLOCK_ID] = PARTIAL_MODEL_ID;
        long[] metadata = new long[1 << 16];
        metadata[MODEL_ID] = PARTIAL_METADATA;
        metadata[COVERING_MODEL_ID] = PARTIAL_METADATA;
        metadata[PARTIAL_MODEL_ID] = PARTIAL_METADATA;
        long[][] masks = new long[1 << 16][];
        masks[MODEL_ID] = patternedMasks(MaskKind.TARGET);
        masks[COVERING_MODEL_ID] = patternedMasks(MaskKind.COVERING);
        masks[PARTIAL_MODEL_ID] = patternedMasks(MaskKind.PARTIAL);
        putObject(factory, "idMappings", mappings);
        putObject(factory, "metadataCache", metadata);
        putObject(factory, "faceOcclusionMasks", masks);
        return factory;
    }

    private static long[] fullMasks() {
        long[] masks = new long[FaceOcclusionMask.WORDS_PER_MODEL];
        Arrays.fill(masks, -1L);
        return masks;
    }

    private static long[] patternedMasks(MaskKind kind) {
        long[] masks = new long[FaceOcclusionMask.WORDS_PER_MODEL];
        for (int face = 0; face < ForgeModelAtlasLayout.FACE_COUNT; face++) {
            for (int v = 0; v < FaceOcclusionMask.RESOLUTION; v++) {
                for (int u = 0; u < FaceOcclusionMask.RESOLUTION; u++) {
                    boolean written = switch (kind) {
                        case TARGET -> u == 3 || v == 9;
                        case COVERING -> u == 3 || v == 9 || u == 12;
                        case PARTIAL -> u == 3 && v < 9;
                    };
                    if (written) {
                        int pixel = u + v * FaceOcclusionMask.RESOLUTION;
                        int offset = FaceOcclusionMask.faceOffset(face) + pixel / Long.SIZE;
                        masks[offset] |= 1L << (pixel & (Long.SIZE - 1));
                    }
                }
            }
        }
        return masks;
    }

    private static long repeatedFaceMetadata(int faceByte, int globalByte) {
        long metadata = ((long) globalByte & 0xFFFFL) << 48;
        for (int face = 0; face < ForgeModelAtlasLayout.FACE_COUNT; face++) {
            metadata |= ((long) faceByte & 0xFFL) << (face * Byte.SIZE);
        }
        return metadata;
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

    private record BorderSnapshot(int faceQuads, int firstFace) {
    }

    private enum NeighborKind {
        AIR,
        COVERING,
        PARTIAL
    }

    private enum MaskKind {
        TARGET,
        COVERING,
        PARTIAL
    }
}
