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

final class Round9FluidMeshingContractTest {
    private static final int WATER_MODEL = 23;
    private static final int LAVA_MODEL = 24;
    private static final int CONTAINED_MODEL = 25;
    private static final int OPAQUE_MODEL = 26;
    private static final int WATER_BLOCK = 1;
    private static final int LAVA_BLOCK = 2;
    private static final int CONTAINED_BLOCK = 3;
    private static final int OPAQUE_BLOCK = 4;
    private static final int PLANE_SIZE = 32 * 32;
    private static final int BUFFER_QUAD_CAPACITY = 1 << 16;
    private static final int[] NEIGHBOR_SLOT_BY_FACE = {2, 3, 4, 5, 0, 1};
    private static final long WATER_METADATA = repeatedFaceMetadata(0b1100, 0b11_0010);
    private static final long LAVA_METADATA = repeatedFaceMetadata(0b1100, 0b11_0010);
    private static final long CONTAINED_METADATA = repeatedFaceMetadata(0b1_0100, 0b1000);
    private static final long OPAQUE_METADATA = repeatedFaceMetadata(0b1_0111, 0b100_0000);
    private static final Unsafe UNSAFE = unsafe();

    @Test
    void pureFluidBordersHandleAirSameDifferentContainedAndOpaqueNeighboursInAllDirections()
            throws Exception {
        for (int face = 0; face < ForgeModelAtlasLayout.FACE_COUNT; face++) {
            assertEquals(1, boundary(face, CellKind.WATER, CellKind.AIR).count(face),
                    "water against air " + face);
            assertEquals(0, boundary(face, CellKind.WATER, CellKind.WATER).count(face),
                    "same water " + face);
            assertEquals(0, boundary(face, CellKind.WATER, CellKind.CONTAINED_WATER).count(face),
                    "water against contained water " + face);
            assertEquals(0, boundary(face, CellKind.WATER, CellKind.OPAQUE).count(face),
                    "water against opaque " + face);
            assertEquals(1, boundary(face, CellKind.WATER, CellKind.LAVA).count(face),
                    "different fluids remain visible " + face);
        }
    }

    @Test
    void containedFluidUsesTheFluidModelAndNeverTheBaseModel() throws Exception {
        for (int face = 0; face < ForgeModelAtlasLayout.FACE_COUNT; face++) {
            MeshSnapshot exposed = boundary(face, CellKind.CONTAINED_WATER, CellKind.AIR);
            assertEquals(1, exposed.count(face), "contained water against air " + face);
            assertEquals(WATER_MODEL, RenderFaceDecision.modelId(exposed.only(face)),
                    "contained fluid model " + face);
            assertEquals(0, boundary(face, CellKind.CONTAINED_WATER, CellKind.WATER).count(face),
                    "contained water against pure water " + face);
            assertEquals(0,
                    boundary(face, CellKind.CONTAINED_WATER, CellKind.CONTAINED_WATER).count(face),
                    "contained water against contained water " + face);
            assertEquals(0, boundary(face, CellKind.CONTAINED_WATER, CellKind.OPAQUE).count(face),
                    "contained water against opaque " + face);
        }
    }

    @Test
    void internalDifferentFluidsEmitBothMaterialFacesButEqualFluidsEmitNone() throws Exception {
        for (int axis = 0; axis < 3; axis++) {
            int positiveFace = (axis << 1) | 1;
            int negativeFace = axis << 1;
            int lowerCoordinate = 10;

            MeshSnapshot different = internalPair(axis, CellKind.WATER, CellKind.LAVA);
            assertEquals(1, different.countAt(positiveFace, lowerCoordinate),
                    "water side of different-fluid interface axis " + axis);
            assertEquals(1, different.countAt(negativeFace, lowerCoordinate + 1),
                    "lava side of different-fluid interface axis " + axis);
            assertEquals(WATER_MODEL,
                    RenderFaceDecision.modelId(different.onlyAt(positiveFace, lowerCoordinate)),
                    "water interface model axis " + axis);
            assertEquals(LAVA_MODEL,
                    RenderFaceDecision.modelId(different.onlyAt(negativeFace, lowerCoordinate + 1)),
                    "lava interface model axis " + axis);

            MeshSnapshot same = internalPair(axis, CellKind.WATER, CellKind.WATER);
            assertEquals(0, same.countAt(positiveFace, lowerCoordinate),
                    "same-fluid positive interface axis " + axis);
            assertEquals(0, same.countAt(negativeFace, lowerCoordinate + 1),
                    "same-fluid negative interface axis " + axis);

            MeshSnapshot contained = internalPair(axis, CellKind.CONTAINED_WATER, CellKind.WATER);
            assertEquals(0, contained.countAt(positiveFace, lowerCoordinate),
                    "contained/pure same fluid positive axis " + axis);
            assertEquals(0, contained.countAt(negativeFace, lowerCoordinate + 1),
                    "contained/pure same fluid negative axis " + axis);
        }
    }

    private static MeshSnapshot boundary(int face, CellKind current, CellKind neighbor) throws Exception {
        RenderDataFactory factory = new RenderDataFactory(null, fakeModelFactory(), false);
        try {
            int x = face == 4 ? 0 : face == 5 ? 31 : 7;
            int y = face == 0 ? 0 : face == 1 ? 31 : 11;
            int z = face == 2 ? 0 : face == 3 ? 31 : 13;
            if (current != CellKind.AIR) {
                putCell(factory, x, y, z, current);
            }
            if (neighbor != CellKind.AIR) {
                long[] neighboringFaces = field(factory, "neighboringFaces", long[].class);
                int planeIndex = switch (face >> 1) {
                    case 0 -> z * 32 + x;
                    case 1 -> y * 32 + x;
                    case 2 -> y * 32 + z;
                    default -> throw new IllegalArgumentException("Invalid face " + face);
                };
                int slot = NEIGHBOR_SLOT_BY_FACE[face] * PLANE_SIZE + planeIndex;
                neighboringFaces[slot] = Mapper.composeMappingId(
                        (byte) 0x3C,
                        blockId(neighbor),
                        0);
            }
            invoke(factory, face < 4 ? "generateYZFaces" : "generateXFaces");
            return translucentSnapshot(factory);
        } finally {
            factory.free();
        }
    }

    private static MeshSnapshot internalPair(int axis, CellKind first, CellKind second) throws Exception {
        RenderDataFactory factory = new RenderDataFactory(null, fakeModelFactory(), false);
        try {
            int[] firstPos = {8, 8, 8};
            int[] secondPos = {8, 8, 8};
            int coordinateIndex = switch (axis) {
                case 0 -> 1;
                case 1 -> 2;
                case 2 -> 0;
                default -> throw new IllegalArgumentException("Invalid axis " + axis);
            };
            firstPos[coordinateIndex] = 10;
            secondPos[coordinateIndex] = 11;
            putCell(factory, firstPos[0], firstPos[1], firstPos[2], first);
            putCell(factory, secondPos[0], secondPos[1], secondPos[2], second);
            invoke(factory, axis < 2 ? "generateYZFaces" : "generateXFaces");
            return translucentSnapshot(factory);
        } finally {
            factory.free();
        }
    }

    private static void putCell(RenderDataFactory factory, int x, int y, int z, CellKind kind)
            throws Exception {
        int index = x | (z << 5) | (y << 10);
        long metadata = metadata(kind);
        long[] sectionData = field(factory, "sectionData", long[].class);
        sectionData[index * 2] = ((long) modelId(kind) << 26)
                | RenderDataFactory.getQuadTyping(metadata)
                | (0xA5L << 55);
        sectionData[index * 2 + 1] = metadata;
        field(factory, "fluidMasks", int[].class)[y * 32 + z] |= 1 << x;
        if (kind == CellKind.CONTAINED_WATER) {
            field(factory, "nonOpaqueMasks", int[].class)[y * 32 + z] |= 1 << x;
        } else if (kind == CellKind.OPAQUE) {
            field(factory, "opaqueMasks", int[].class)[y * 32 + z] |= 1 << x;
        }
    }

    private static MeshSnapshot translucentSnapshot(RenderDataFactory factory) throws Exception {
        int count = field(factory, "quadCounters", int[].class)[0];
        long pointer = field(factory, "quadBufferPtr", Long.class);
        List<Long> quads = new ArrayList<>(count);
        long bucketOffset = 0L * BUFFER_QUAD_CAPACITY * Long.BYTES;
        for (int index = 0; index < count; index++) {
            quads.add(MemoryUtil.memGetLong(pointer + bucketOffset + (long) index * Long.BYTES));
        }
        return new MeshSnapshot(quads);
    }

    private static ModelFactory fakeModelFactory() throws Exception {
        ModelFactory factory = (ModelFactory) UNSAFE.allocateInstance(ModelFactory.class);
        int[] mappings = new int[1 << 20];
        Arrays.fill(mappings, -1);
        mappings[WATER_BLOCK] = WATER_MODEL;
        mappings[LAVA_BLOCK] = LAVA_MODEL;
        mappings[CONTAINED_BLOCK] = CONTAINED_MODEL;
        mappings[OPAQUE_BLOCK] = OPAQUE_MODEL;

        long[] metadata = new long[1 << 16];
        metadata[WATER_MODEL] = WATER_METADATA;
        metadata[LAVA_MODEL] = LAVA_METADATA;
        metadata[CONTAINED_MODEL] = CONTAINED_METADATA;
        metadata[OPAQUE_MODEL] = OPAQUE_METADATA;

        long[][] masks = new long[1 << 16][];
        masks[WATER_MODEL] = fullMasks();
        masks[LAVA_MODEL] = fullMasks();
        masks[CONTAINED_MODEL] = partialMasks();
        masks[OPAQUE_MODEL] = fullMasks();

        int[] fluidLut = new int[1 << 16];
        Arrays.fill(fluidLut, -1);
        fluidLut[WATER_MODEL] = WATER_MODEL;
        fluidLut[LAVA_MODEL] = LAVA_MODEL;
        fluidLut[CONTAINED_MODEL] = WATER_MODEL;

        putObject(factory, "idMappings", mappings);
        putObject(factory, "metadataCache", metadata);
        putObject(factory, "faceOcclusionMasks", masks);
        putObject(factory, "fluidStateLUT", fluidLut);
        return factory;
    }

    private static long[] fullMasks() {
        long[] masks = new long[FaceOcclusionMask.WORDS_PER_MODEL];
        Arrays.fill(masks, -1L);
        return masks;
    }

    private static long[] partialMasks() {
        long[] masks = new long[FaceOcclusionMask.WORDS_PER_MODEL];
        for (int face = 0; face < ForgeModelAtlasLayout.FACE_COUNT; face++) {
            for (int v = 8; v < FaceOcclusionMask.RESOLUTION; v++) {
                for (int u = 0; u < FaceOcclusionMask.RESOLUTION; u++) {
                    int pixel = u + v * FaceOcclusionMask.RESOLUTION;
                    int offset = FaceOcclusionMask.faceOffset(face) + pixel / Long.SIZE;
                    masks[offset] |= 1L << (pixel & (Long.SIZE - 1));
                }
            }
        }
        return masks;
    }

    private static int modelId(CellKind kind) {
        return switch (kind) {
            case WATER -> WATER_MODEL;
            case LAVA -> LAVA_MODEL;
            case CONTAINED_WATER -> CONTAINED_MODEL;
            case OPAQUE -> OPAQUE_MODEL;
            case AIR -> 0;
        };
    }

    private static int blockId(CellKind kind) {
        return switch (kind) {
            case WATER -> WATER_BLOCK;
            case LAVA -> LAVA_BLOCK;
            case CONTAINED_WATER -> CONTAINED_BLOCK;
            case OPAQUE -> OPAQUE_BLOCK;
            case AIR -> 0;
        };
    }

    private static long metadata(CellKind kind) {
        return switch (kind) {
            case WATER -> WATER_METADATA;
            case LAVA -> LAVA_METADATA;
            case CONTAINED_WATER -> CONTAINED_METADATA;
            case OPAQUE -> OPAQUE_METADATA;
            case AIR -> 0L;
        };
    }

    private static long repeatedFaceMetadata(int faceByte, int globalByte) {
        long metadata = ((long) globalByte & 0xFFFFL) << 48;
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

    private enum CellKind {
        AIR,
        WATER,
        LAVA,
        CONTAINED_WATER,
        OPAQUE
    }

    private record MeshSnapshot(List<Long> quads) {
        int count(int face) {
            return (int) this.quads.stream().filter(quad -> (quad & 0b111L) == face).count();
        }

        int countAt(int face, int position) {
            return (int) this.quads.stream()
                    .filter(quad -> (quad & 0b111L) == face && facePosition(quad) == position)
                    .count();
        }

        long only(int face) {
            return this.quads.stream().filter(quad -> (quad & 0b111L) == face).findFirst().orElseThrow();
        }

        long onlyAt(int face, int position) {
            return this.quads.stream()
                    .filter(quad -> (quad & 0b111L) == face && facePosition(quad) == position)
                    .findFirst()
                    .orElseThrow();
        }
    }
}
