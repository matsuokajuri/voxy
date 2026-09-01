package me.cortex.voxy.forge;

import me.cortex.voxy.common.world.other.Mapper;
import org.junit.jupiter.api.Test;
import org.lwjgl.system.MemoryUtil;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class Round9MeshingLightingParityTest {
    private static final int OPAQUE_MODEL = 21;
    private static final int FLAT_MODEL = 22;
    private static final int SELF_LIT_MODEL = 23;
    private static final int FLUID_MODEL = 24;
    private static final int EMISSIVE_MODEL = 25;
    private static final int PLANE_SIZE = 32 * 32;
    private static final int BUFFER_QUAD_CAPACITY = 1 << 16;
    private static final int[] NEIGHBOR_SLOT_BY_FACE = {2, 3, 4, 5, 0, 1};
    private static final int SELF_LIGHT = 0x2A;
    private static final int NEIGHBOR_LIGHT = 0xB4;
    private static final int EMISSIVE_RESULT_LIGHT = 0xF4;
    private static final long OPAQUE_METADATA = repeatedFaceMetadata(0b1_0111, 0b100_0000);
    private static final long FLAT_METADATA = repeatedFaceMetadata(0b1_0100, 0);
    private static final long SELF_LIT_METADATA = repeatedFaceMetadata(0b1100, 0b10);
    private static final long FLUID_METADATA = repeatedFaceMetadata(0b1100, 0b11_0010);
    private static final long EMISSIVE_METADATA = repeatedFaceMetadata(
            0b1_0111,
            0b100_0000 | (15 << 7));
    private static final Unsafe UNSAFE = unsafe();

    @Test
    void everyAxisAndDirectionSelectsTheSameLightOwnerInsideAndAtSectionBorders()
            throws Exception {
        for (int face = 0; face < ForgeModelAtlasLayout.FACE_COUNT; face++) {
            for (boolean outer : new boolean[]{false, true}) {
                assertLight(face, outer, MaterialKind.OPAQUE, NEIGHBOR_LIGHT);
                assertLight(face, outer, MaterialKind.FLAT_NON_OPAQUE, NEIGHBOR_LIGHT);
                assertLight(face, outer, MaterialKind.SELF_LIT_TRANSLUCENT, SELF_LIGHT);
                assertLight(face, outer, MaterialKind.FLUID, SELF_LIGHT);
                assertLight(face, outer, MaterialKind.EMISSIVE, EMISSIVE_RESULT_LIGHT);
            }
        }
    }

    private static void assertLight(int face, boolean outer, MaterialKind material, int expected)
            throws Exception {
        long quad = meshFace(face, outer, material);
        assertEquals(modelId(material), RenderFaceDecision.modelId(quad),
                material + " model face=" + face + " outer=" + outer);
        assertEquals(expected, (quad >>> 55) & 0xFFL,
                material + " light face=" + face + " outer=" + outer);
    }

    private static long meshFace(int face, boolean outer, MaterialKind material) throws Exception {
        RenderDataFactory factory = new RenderDataFactory(null, fakeModelFactory(), false);
        try {
            int axis = face >> 1;
            int[] position = {7, 11, 13};
            int coordinateIndex = switch (axis) {
                case 0 -> 1;
                case 1 -> 2;
                case 2 -> 0;
                default -> throw new IllegalArgumentException("Invalid axis " + axis);
            };
            position[coordinateIndex] = outer ? ((face & 1) == 0 ? 0 : 31) : 10;
            int x = position[0];
            int y = position[1];
            int z = position[2];
            putCurrent(factory, x, y, z, material);

            if (outer) {
                long[] neighboringFaces = field(factory, "neighboringFaces", long[].class);
                int planeIndex = switch (axis) {
                    case 0 -> z * 32 + x;
                    case 1 -> y * 32 + x;
                    case 2 -> y * 32 + z;
                    default -> throw new IllegalArgumentException("Invalid axis " + axis);
                };
                neighboringFaces[NEIGHBOR_SLOT_BY_FACE[face] * PLANE_SIZE + planeIndex]
                        = Mapper.airWithLight(NEIGHBOR_LIGHT);
            } else {
                int[] neighbor = position.clone();
                neighbor[coordinateIndex] += (face & 1) == 0 ? -1 : 1;
                int neighborIndex = neighbor[0] | (neighbor[2] << 5) | (neighbor[1] << 10);
                field(factory, "sectionData", long[].class)[neighborIndex * 2]
                        = (long) NEIGHBOR_LIGHT << 55;
            }

            invoke(factory, face < 4 ? "generateYZFaces" : "generateXFaces");
            List<Long> quads = allQuads(factory);
            int expectedPosition = position[coordinateIndex];
            return quads.stream()
                    .filter(quad -> (quad & 0b111L) == face)
                    .filter(quad -> RenderFaceDecision.modelId(quad) == modelId(material))
                    .filter(quad -> facePosition(quad) == expectedPosition)
                    .findFirst()
                    .orElseThrow(() -> new AssertionError(
                            "Missing " + material + " face=" + face + " outer=" + outer + " quads=" + quads));
        } finally {
            factory.free();
        }
    }

    private static void putCurrent(
            RenderDataFactory factory,
            int x,
            int y,
            int z,
            MaterialKind material
    ) throws Exception {
        int index = x | (z << 5) | (y << 10);
        long metadata = metadata(material);
        field(factory, "sectionData", long[].class)[index * 2]
                = ((long) modelId(material) << 26)
                | RenderDataFactory.getQuadTyping(metadata)
                | ((long) SELF_LIGHT << 55);
        field(factory, "sectionData", long[].class)[index * 2 + 1] = metadata;
        int row = y * 32 + z;
        switch (material) {
            case OPAQUE, EMISSIVE -> field(factory, "opaqueMasks", int[].class)[row] |= 1 << x;
            case FLAT_NON_OPAQUE, SELF_LIT_TRANSLUCENT ->
                    field(factory, "nonOpaqueMasks", int[].class)[row] |= 1 << x;
            case FLUID -> field(factory, "fluidMasks", int[].class)[row] |= 1 << x;
        }
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
        metadata[OPAQUE_MODEL] = OPAQUE_METADATA;
        metadata[FLAT_MODEL] = FLAT_METADATA;
        metadata[SELF_LIT_MODEL] = SELF_LIT_METADATA;
        metadata[FLUID_MODEL] = FLUID_METADATA;
        metadata[EMISSIVE_MODEL] = EMISSIVE_METADATA;
        long[][] masks = new long[1 << 16][];
        masks[OPAQUE_MODEL] = fullMasks();
        masks[FLAT_MODEL] = fullMasks();
        masks[SELF_LIT_MODEL] = fullMasks();
        masks[FLUID_MODEL] = fullMasks();
        masks[EMISSIVE_MODEL] = fullMasks();
        int[] fluidLut = new int[1 << 16];
        Arrays.fill(fluidLut, -1);
        fluidLut[FLUID_MODEL] = FLUID_MODEL;
        putObject(factory, "idMappings", mappings);
        putObject(factory, "metadataCache", metadata);
        putObject(factory, "faceOcclusionMasks", masks);
        putObject(factory, "fluidStateLUT", fluidLut);
        return factory;
    }

    private static int modelId(MaterialKind material) {
        return switch (material) {
            case OPAQUE -> OPAQUE_MODEL;
            case FLAT_NON_OPAQUE -> FLAT_MODEL;
            case SELF_LIT_TRANSLUCENT -> SELF_LIT_MODEL;
            case FLUID -> FLUID_MODEL;
            case EMISSIVE -> EMISSIVE_MODEL;
        };
    }

    private static long metadata(MaterialKind material) {
        return switch (material) {
            case OPAQUE -> OPAQUE_METADATA;
            case FLAT_NON_OPAQUE -> FLAT_METADATA;
            case SELF_LIT_TRANSLUCENT -> SELF_LIT_METADATA;
            case FLUID -> FLUID_METADATA;
            case EMISSIVE -> EMISSIVE_METADATA;
        };
    }

    private static long[] fullMasks() {
        long[] masks = new long[FaceOcclusionMask.WORDS_PER_MODEL];
        Arrays.fill(masks, -1L);
        return masks;
    }

    private static long repeatedFaceMetadata(int faceByte, int global) {
        long metadata = ((long) global & 0xFFFFL) << 48;
        for (int face = 0; face < ForgeModelAtlasLayout.FACE_COUNT; face++) {
            metadata |= ((long) faceByte & 0xFFL) << (face * Byte.SIZE);
        }
        return metadata;
    }

    private static int facePosition(long quad) {
        int face = (int) (quad & 0b111L);
        int axis = face >> 1;
        int shift = axis == 0 ? 16 : axis == 1 ? 11 : 21;
        return (int) ((quad >>> shift) & 0x1FL);
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

    private enum MaterialKind {
        OPAQUE,
        FLAT_NON_OPAQUE,
        SELF_LIT_TRANSLUCENT,
        FLUID,
        EMISSIVE
    }
}
