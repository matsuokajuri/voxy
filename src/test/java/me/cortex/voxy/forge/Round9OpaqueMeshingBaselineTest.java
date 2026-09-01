package me.cortex.voxy.forge;

import org.junit.jupiter.api.Test;
import org.lwjgl.system.MemoryUtil;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class Round9OpaqueMeshingBaselineTest {
    private static final int MODEL_ID = 23;
    private static final int BUFFER_QUAD_CAPACITY = 1 << 16;

    @Test
    void isolatedOpaqueCubeFreezesTheFormalSixFaceOutput() throws Exception {
        MeshSnapshot snapshot = meshOpaqueCube(16, 16, 16, 1);

        assertDirectionalCube(snapshot, 1, 16, 17);
        assertEquals("8ed3a5f1749194dbdfa643cee554767a45c0d40cb9b3f47e1a581181c7c398eb", snapshot.sha256());
    }

    @Test
    void sixteenCubedOpaqueVolumeGreedilyMergesToSixQuads() throws Exception {
        MeshSnapshot snapshot = meshOpaqueCube(8, 8, 8, 16);

        assertDirectionalCube(snapshot, 16, 8, 24);
        assertEquals("d8d086c905911251d26baaed74304141cd67230e76e4021d255363cb8d69f978", snapshot.sha256());
    }

    private static void assertDirectionalCube(MeshSnapshot snapshot, int faceSize, int min, int max) {
        assertEquals(6, snapshot.quadCount());
        assertArrayEquals(new int[]{0, 0, 1, 1, 1, 1, 1, 1}, snapshot.quadCounters());
        assertArrayEquals(new int[]{min, min, min}, snapshot.minimums());
        assertArrayEquals(new int[]{max, max, max}, snapshot.maximums());

        for (int face = 0; face < 6; face++) {
            long quad = snapshot.directionalQuads()[face];
            assertEquals(face, quad & 0b111L, "face index " + face);
            assertEquals(faceSize, ((quad >>> 3) & 0xFL) + 1L, "face length " + face);
            assertEquals(faceSize, ((quad >>> 7) & 0xFL) + 1L, "face width " + face);
            assertEquals(MODEL_ID, (quad >>> 26) & 0xFFFFL, "model id " + face);
        }
    }

    private static MeshSnapshot meshOpaqueCube(int minX, int minY, int minZ, int size) throws Exception {
        RenderDataFactory factory = new RenderDataFactory(null, null, false);
        try {
            long[] sectionData = field(factory, "sectionData", long[].class);
            int[] opaqueMasks = field(factory, "opaqueMasks", int[].class);
            int[] quadCounters = field(factory, "quadCounters", int[].class);
            long packedModel = ((long) MODEL_ID << 26) | RenderDataFactory.getQuadTyping(0L);
            int xMask = ((1 << size) - 1) << minX;

            for (int y = minY; y < minY + size; y++) {
                for (int z = minZ; z < minZ + size; z++) {
                    opaqueMasks[y * 32 + z] = xMask;
                    for (int x = minX; x < minX + size; x++) {
                        int index = x | (z << 5) | (y << 10);
                        sectionData[index * 2] = packedModel;
                        sectionData[index * 2 + 1] = 0L;
                    }
                }
            }

            setInt(factory, "minX", Integer.MAX_VALUE);
            setInt(factory, "minY", Integer.MAX_VALUE);
            setInt(factory, "minZ", Integer.MAX_VALUE);
            setInt(factory, "maxX", Integer.MIN_VALUE);
            setInt(factory, "maxY", Integer.MIN_VALUE);
            setInt(factory, "maxZ", Integer.MIN_VALUE);

            invoke(factory, "generateYZFaces");
            invoke(factory, "generateXFaces");

            long quadBufferPtr = field(factory, "quadBufferPtr", Long.class);
            long[] directionalQuads = new long[6];
            for (int face = 0; face < directionalQuads.length; face++) {
                long bufferOffset = (long) (face + 2) * BUFFER_QUAD_CAPACITY * Long.BYTES;
                directionalQuads[face] = MemoryUtil.memGetLong(quadBufferPtr + bufferOffset);
            }

            return new MeshSnapshot(
                    field(factory, "quadCount", Integer.class),
                    quadCounters.clone(),
                    directionalQuads,
                    new int[]{
                            field(factory, "minX", Integer.class),
                            field(factory, "minY", Integer.class),
                            field(factory, "minZ", Integer.class)},
                    new int[]{
                            field(factory, "maxX", Integer.class),
                            field(factory, "maxY", Integer.class),
                            field(factory, "maxZ", Integer.class)});
        } finally {
            factory.free();
        }
    }

    private static void invoke(RenderDataFactory factory, String name) throws Exception {
        Method method = RenderDataFactory.class.getDeclaredMethod(name);
        method.setAccessible(true);
        method.invoke(factory);
    }

    private static void setInt(RenderDataFactory factory, String name, int value) throws Exception {
        Field field = RenderDataFactory.class.getDeclaredField(name);
        field.setAccessible(true);
        field.setInt(factory, value);
    }

    @SuppressWarnings("unchecked")
    private static <T> T field(RenderDataFactory factory, String name, Class<T> type) throws Exception {
        Field field = RenderDataFactory.class.getDeclaredField(name);
        field.setAccessible(true);
        Object value = field.get(factory);
        if (type == Integer.class) {
            return (T) Integer.valueOf((int) value);
        }
        if (type == Long.class) {
            return (T) Long.valueOf((long) value);
        }
        return type.cast(value);
    }

    private record MeshSnapshot(
            int quadCount,
            int[] quadCounters,
            long[] directionalQuads,
            int[] minimums,
            int[] maximums
    ) {
        private String sha256() {
            try {
                ByteBuffer bytes = ByteBuffer.allocate(
                                Integer.BYTES
                                        + this.quadCounters.length * Integer.BYTES
                                        + this.directionalQuads.length * Long.BYTES
                                        + (this.minimums.length + this.maximums.length) * Integer.BYTES)
                        .order(ByteOrder.LITTLE_ENDIAN);
                bytes.putInt(this.quadCount);
                for (int counter : this.quadCounters) {
                    bytes.putInt(counter);
                }
                for (long quad : this.directionalQuads) {
                    bytes.putLong(quad);
                }
                for (int minimum : this.minimums) {
                    bytes.putInt(minimum);
                }
                for (int maximum : this.maximums) {
                    bytes.putInt(maximum);
                }
                return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes.array()));
            } catch (NoSuchAlgorithmException e) {
                throw new IllegalStateException(e);
            }
        }
    }
}
