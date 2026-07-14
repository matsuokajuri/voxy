package me.cortex.voxy.forge;

import me.cortex.voxy.common.util.MemoryBuffer;
import org.lwjgl.system.MemoryUtil;

final class SharedIndexBuffer {
    static final int CUBE_INDEX_OFFSET = (1 << 16) * 6 * 2;
    static final SharedIndexBuffer INSTANCE = new SharedIndexBuffer();
    static final SharedIndexBuffer INSTANCE_BYTE = new SharedIndexBuffer(true);
    static final SharedIndexBuffer INSTANCE_BB_BYTE = new SharedIndexBuffer(true, true);

    private final GlBuffer indexBuffer;

    SharedIndexBuffer() {
        this.indexBuffer = new GlBuffer(CUBE_INDEX_OFFSET + 6L * 2L * 3L, false);
        MemoryBuffer quadIndexBuffer = generateQuadIndicesShort(16380);
        MemoryBuffer cubeIndexBuffer = generateCubeIndexBuffer();
        long ptr = UploadStream.instance().upload(this.indexBuffer.id, 0L, this.indexBuffer.size());
        quadIndexBuffer.cpyTo(ptr);
        cubeIndexBuffer.cpyTo(ptr + CUBE_INDEX_OFFSET);
        quadIndexBuffer.free();
        cubeIndexBuffer.free();
        UploadStream.instance().commit();
    }

    private SharedIndexBuffer(boolean byteIndices) {
        this.indexBuffer = new GlBuffer((1 << 8) * 6L + 6L * 2L * 3L, false);
        MemoryBuffer quadIndexBuffer = generateQuadIndicesByte(63);
        MemoryBuffer cubeIndexBuffer = generateCubeIndexBuffer();
        long ptr = UploadStream.instance().upload(this.indexBuffer.id, 0L, this.indexBuffer.size());
        quadIndexBuffer.cpyTo(ptr);
        cubeIndexBuffer.cpyTo(ptr + (1 << 8) * 6L);
        quadIndexBuffer.free();
        cubeIndexBuffer.free();
    }

    private SharedIndexBuffer(boolean byteIndices, boolean boundingBoxes) {
        this.indexBuffer = new GlBuffer(6L * 2L * 3L * (256 / 8), false);
        MemoryBuffer cubeIndexBuffer = generateByteCubesIndexBuffer(256 / 8);
        cubeIndexBuffer.cpyTo(UploadStream.instance().upload(this.indexBuffer.id, 0L, this.indexBuffer.size()));
        UploadStream.instance().commit();
        cubeIndexBuffer.free();
    }

    int id() {
        return this.indexBuffer.id;
    }

    boolean ready() {
        return this.indexBuffer.id != 0;
    }

    void free() {
        this.indexBuffer.free();
    }

    static void freeAll() {
        INSTANCE.free();
        INSTANCE_BYTE.free();
        INSTANCE_BB_BYTE.free();
    }

    private static MemoryBuffer generateCubeIndexBuffer() {
        MemoryBuffer buffer = new MemoryBuffer(6L * 2L * 3L);
        long ptr = buffer.address;
        MemoryUtil.memSet(ptr, 0, buffer.size);

        MemoryUtil.memPutByte(ptr++, (byte) 0);
        MemoryUtil.memPutByte(ptr++, (byte) 1);
        MemoryUtil.memPutByte(ptr++, (byte) 2);
        MemoryUtil.memPutByte(ptr++, (byte) 3);
        MemoryUtil.memPutByte(ptr++, (byte) 2);
        MemoryUtil.memPutByte(ptr++, (byte) 1);

        MemoryUtil.memPutByte(ptr++, (byte) 6);
        MemoryUtil.memPutByte(ptr++, (byte) 5);
        MemoryUtil.memPutByte(ptr++, (byte) 4);
        MemoryUtil.memPutByte(ptr++, (byte) 5);
        MemoryUtil.memPutByte(ptr++, (byte) 6);
        MemoryUtil.memPutByte(ptr++, (byte) 7);

        MemoryUtil.memPutByte(ptr++, (byte) 0);
        MemoryUtil.memPutByte(ptr++, (byte) 4);
        MemoryUtil.memPutByte(ptr++, (byte) 1);
        MemoryUtil.memPutByte(ptr++, (byte) 5);
        MemoryUtil.memPutByte(ptr++, (byte) 1);
        MemoryUtil.memPutByte(ptr++, (byte) 4);

        MemoryUtil.memPutByte(ptr++, (byte) 3);
        MemoryUtil.memPutByte(ptr++, (byte) 6);
        MemoryUtil.memPutByte(ptr++, (byte) 2);
        MemoryUtil.memPutByte(ptr++, (byte) 6);
        MemoryUtil.memPutByte(ptr++, (byte) 3);
        MemoryUtil.memPutByte(ptr++, (byte) 7);

        MemoryUtil.memPutByte(ptr++, (byte) 2);
        MemoryUtil.memPutByte(ptr++, (byte) 4);
        MemoryUtil.memPutByte(ptr++, (byte) 0);
        MemoryUtil.memPutByte(ptr++, (byte) 4);
        MemoryUtil.memPutByte(ptr++, (byte) 2);
        MemoryUtil.memPutByte(ptr++, (byte) 6);

        MemoryUtil.memPutByte(ptr++, (byte) 1);
        MemoryUtil.memPutByte(ptr++, (byte) 5);
        MemoryUtil.memPutByte(ptr++, (byte) 3);
        MemoryUtil.memPutByte(ptr++, (byte) 7);
        MemoryUtil.memPutByte(ptr++, (byte) 3);
        MemoryUtil.memPutByte(ptr, (byte) 5);
        return buffer;
    }

    private static MemoryBuffer generateByteCubesIndexBuffer(int count) {
        MemoryBuffer buffer = new MemoryBuffer((long) count * 6L * 2L * 3L);
        long ptr = buffer.address;
        MemoryUtil.memSet(ptr, 0, buffer.size);
        for (int i = 0; i < count; i++) {
            int base = i * 8;
            MemoryUtil.memPutByte(ptr++, (byte) base);
            MemoryUtil.memPutByte(ptr++, (byte) (base + 1));
            MemoryUtil.memPutByte(ptr++, (byte) (base + 2));
            MemoryUtil.memPutByte(ptr++, (byte) (base + 3));
            MemoryUtil.memPutByte(ptr++, (byte) (base + 2));
            MemoryUtil.memPutByte(ptr++, (byte) (base + 1));
            MemoryUtil.memPutByte(ptr++, (byte) (base + 6));
            MemoryUtil.memPutByte(ptr++, (byte) (base + 5));
            MemoryUtil.memPutByte(ptr++, (byte) (base + 4));
            MemoryUtil.memPutByte(ptr++, (byte) (base + 5));
            MemoryUtil.memPutByte(ptr++, (byte) (base + 6));
            MemoryUtil.memPutByte(ptr++, (byte) (base + 7));
            MemoryUtil.memPutByte(ptr++, (byte) base);
            MemoryUtil.memPutByte(ptr++, (byte) (base + 4));
            MemoryUtil.memPutByte(ptr++, (byte) (base + 1));
            MemoryUtil.memPutByte(ptr++, (byte) (base + 5));
            MemoryUtil.memPutByte(ptr++, (byte) (base + 1));
            MemoryUtil.memPutByte(ptr++, (byte) (base + 4));
            MemoryUtil.memPutByte(ptr++, (byte) (base + 3));
            MemoryUtil.memPutByte(ptr++, (byte) (base + 6));
            MemoryUtil.memPutByte(ptr++, (byte) (base + 2));
            MemoryUtil.memPutByte(ptr++, (byte) (base + 6));
            MemoryUtil.memPutByte(ptr++, (byte) (base + 3));
            MemoryUtil.memPutByte(ptr++, (byte) (base + 7));
            MemoryUtil.memPutByte(ptr++, (byte) (base + 2));
            MemoryUtil.memPutByte(ptr++, (byte) (base + 4));
            MemoryUtil.memPutByte(ptr++, (byte) base);
            MemoryUtil.memPutByte(ptr++, (byte) (base + 4));
            MemoryUtil.memPutByte(ptr++, (byte) (base + 2));
            MemoryUtil.memPutByte(ptr++, (byte) (base + 6));
            MemoryUtil.memPutByte(ptr++, (byte) (base + 1));
            MemoryUtil.memPutByte(ptr++, (byte) (base + 5));
            MemoryUtil.memPutByte(ptr++, (byte) (base + 3));
            MemoryUtil.memPutByte(ptr++, (byte) (base + 7));
            MemoryUtil.memPutByte(ptr++, (byte) (base + 3));
            MemoryUtil.memPutByte(ptr++, (byte) (base + 5));
        }
        return buffer;
    }

    static MemoryBuffer generateQuadIndicesByte(int quadCount) {
        if ((quadCount * 4) >= 1 << 8) {
            throw new IllegalArgumentException("Quad count too large");
        }
        MemoryBuffer buffer = new MemoryBuffer(quadCount * 6L);
        long ptr = buffer.address;
        for (int i = 0; i < quadCount * 4; i += 4) {
            MemoryUtil.memPutByte(ptr, (byte) (i + 1));
            MemoryUtil.memPutByte(ptr + 1L, (byte) (i + 2));
            MemoryUtil.memPutByte(ptr + 2L, (byte) i);
            MemoryUtil.memPutByte(ptr + 3L, (byte) (i + 1));
            MemoryUtil.memPutByte(ptr + 4L, (byte) (i + 3));
            MemoryUtil.memPutByte(ptr + 5L, (byte) (i + 2));
            ptr += 6L;
        }
        return buffer;
    }

    static MemoryBuffer generateQuadIndicesShort(int quadCount) {
        if ((quadCount * 4) >= 1 << 16) {
            throw new IllegalArgumentException("Quad count too large");
        }
        MemoryBuffer buffer = new MemoryBuffer(quadCount * 6L * 2L);
        long ptr = buffer.address;
        for (int i = 0; i < quadCount * 4; i += 4) {
            MemoryUtil.memPutShort(ptr, (short) (i + 1));
            MemoryUtil.memPutShort(ptr + 2L, (short) (i + 2));
            MemoryUtil.memPutShort(ptr + 4L, (short) i);
            MemoryUtil.memPutShort(ptr + 6L, (short) (i + 1));
            MemoryUtil.memPutShort(ptr + 8L, (short) (i + 3));
            MemoryUtil.memPutShort(ptr + 10L, (short) (i + 2));
            ptr += 12L;
        }
        return buffer;
    }

    static MemoryBuffer generateQuadIndicesInt(int quadCount) {
        // Keep the original Voxy allocation contract byte-for-byte, including its currently
        // unused short-sized allocation for this int writer.
        MemoryBuffer buffer = new MemoryBuffer(quadCount * 6L * 2L);
        long ptr = buffer.address;
        for (int i = 0; i < quadCount * 4; i += 4) {
            MemoryUtil.memPutInt(ptr, i);
            MemoryUtil.memPutInt(ptr + 4L, i + 1);
            MemoryUtil.memPutInt(ptr + 8L, i + 2);
            MemoryUtil.memPutInt(ptr + 12L, i + 1);
            MemoryUtil.memPutInt(ptr + 16L, i + 3);
            MemoryUtil.memPutInt(ptr + 20L, i + 2);
            ptr += 6L * Integer.BYTES;
        }
        return buffer;
    }
}
