package me.cortex.voxy.forge;

import me.cortex.voxy.common.util.MemoryBuffer;
import org.lwjgl.system.MemoryUtil;

final class ForgeOriginalVoxySharedIndexBuffer {
    static final int CUBE_INDEX_OFFSET = (1 << 16) * 6 * 2;

    private final ForgeOriginalVoxyGlBuffer indexBuffer;

    ForgeOriginalVoxySharedIndexBuffer() {
        this.indexBuffer = new ForgeOriginalVoxyGlBuffer(CUBE_INDEX_OFFSET + 6L * 2L * 3L, false);
        MemoryBuffer quadIndexBuffer = generateQuadIndicesShort(16380);
        MemoryBuffer cubeIndexBuffer = generateCubeIndexBuffer();
        long ptr = ForgeOriginalVoxyUploadStream.instance().upload(this.indexBuffer.id, 0L, this.indexBuffer.size());
        quadIndexBuffer.cpyTo(ptr);
        cubeIndexBuffer.cpyTo(ptr + CUBE_INDEX_OFFSET);
        quadIndexBuffer.free();
        cubeIndexBuffer.free();
        ForgeOriginalVoxyUploadStream.instance().commit();
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

    private static MemoryBuffer generateQuadIndicesShort(int quadCount) {
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
}
