package me.cortex.voxy.forge;

import org.lwjgl.opengl.GL11;
import org.lwjgl.system.MemoryUtil;

import static org.lwjgl.opengl.ARBSparseBuffer.GL_SPARSE_STORAGE_BIT_ARB;
import static org.lwjgl.opengl.GL11.GL_UNSIGNED_BYTE;
import static org.lwjgl.opengl.GL15C.glDeleteBuffers;
import static org.lwjgl.opengl.GL11C.glPixelStorei;
import static org.lwjgl.opengl.GL30C.GL_R32UI;
import static org.lwjgl.opengl.GL30C.GL_R8UI;
import static org.lwjgl.opengl.GL30C.GL_RED_INTEGER;
import static org.lwjgl.opengl.GL45C.glCreateBuffers;
import static org.lwjgl.opengl.GL45C.nglClearNamedBufferData;
import static org.lwjgl.opengl.GL45C.nglClearNamedBufferSubData;
import static org.lwjgl.opengl.GL45C.nglNamedBufferStorage;

final class ForgeOriginalVoxyGlBuffer {
    private static final long SCRATCH = MemoryUtil.nmemAlloc(Integer.BYTES);

    final int id;
    private final long size;
    private final int flags;

    ForgeOriginalVoxyGlBuffer(long size) {
        this(size, 0, true);
    }

    ForgeOriginalVoxyGlBuffer(long size, boolean zero) {
        this(size, 0, zero);
    }

    ForgeOriginalVoxyGlBuffer(long size, int flags, boolean zero) {
        this.flags = flags;
        this.id = glCreateBuffers();
        this.size = size;
        nglNamedBufferStorage(this.id, size, 0L, flags);
        if ((flags & GL_SPARSE_STORAGE_BIT_ARB) == 0 && zero) {
            this.zero();
        }
    }

    long size() {
        return this.size;
    }

    ForgeOriginalVoxyGlBuffer zero() {
        nglClearNamedBufferData(this.id, GL_R8UI, GL_RED_INTEGER, GL_UNSIGNED_BYTE, 0L);
        return this;
    }

    ForgeOriginalVoxyGlBuffer fill(int data) {
        glPixelStorei(GL11.GL_UNPACK_SKIP_ROWS, 0);
        glPixelStorei(GL11.GL_UNPACK_SKIP_PIXELS, 0);
        MemoryUtil.memPutInt(SCRATCH, data);
        nglClearNamedBufferData(this.id, GL_R32UI, GL_RED_INTEGER, GL11.GL_UNSIGNED_INT, SCRATCH);
        return this;
    }

    void clearRange(long offset, long size) {
        nglClearNamedBufferSubData(this.id, GL_R8UI, offset, size, GL_RED_INTEGER, GL_UNSIGNED_BYTE, 0L);
    }

    void free() {
        glDeleteBuffers(this.id);
    }
}
