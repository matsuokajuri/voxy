package me.cortex.voxy.forge;

import me.cortex.voxy.common.util.MemoryBuffer;
import me.cortex.voxy.common.util.TrackedObject;
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

final class GlBuffer extends TrackedObject {
    private static final long SCRATCH = MemoryUtil.nmemAlloc(Integer.BYTES);
    private static int count;
    private static long totalSize;

    final int id;
    private final long size;
    private final int flags;

    GlBuffer(long size) {
        this(size, 0);
    }

    GlBuffer(MemoryBuffer buffer) {
        this(buffer.size, 0, false, buffer.address);
    }

    GlBuffer(long size, boolean zero) {
        this(size, 0, zero);
    }

    GlBuffer(long size, int flags) {
        this(size, flags, true);
    }

    GlBuffer(long size, int flags, boolean zero) {
        this(size, flags, zero, 0L);
    }

    private GlBuffer(long size, int flags, boolean zero, long data) {
        this.flags = flags;
        this.id = glCreateBuffers();
        this.size = size;
        nglNamedBufferStorage(this.id, size, data, flags);
        if ((flags & GL_SPARSE_STORAGE_BIT_ARB) == 0 && zero) {
            this.zero();
        }
        count++;
        totalSize += size;
    }

    long size() {
        return this.size;
    }

    boolean isSparse() {
        return (this.flags & GL_SPARSE_STORAGE_BIT_ARB) != 0;
    }

    GlBuffer zero() {
        nglClearNamedBufferData(this.id, GL_R8UI, GL_RED_INTEGER, GL_UNSIGNED_BYTE, 0L);
        return this;
    }

    GlBuffer fill(int data) {
        glPixelStorei(GL11.GL_UNPACK_SKIP_ROWS, 0);
        glPixelStorei(GL11.GL_UNPACK_SKIP_PIXELS, 0);
        MemoryUtil.memPutInt(SCRATCH, data);
        nglClearNamedBufferData(this.id, GL_R32UI, GL_RED_INTEGER, GL11.GL_UNSIGNED_INT, SCRATCH);
        return this;
    }

    GlBuffer zeroRange(long offset, long size) {
        nglClearNamedBufferSubData(this.id, GL_R8UI, offset, size, GL_RED_INTEGER, GL_UNSIGNED_BYTE, 0L);
        return this;
    }

    @Override
    public void free() {
        this.free0();
        glDeleteBuffers(this.id);
        count--;
        totalSize -= this.size;
    }

    static int getCount() {
        return count;
    }

    static long getTotalSize() {
        return totalSize;
    }

    GlBuffer name(String name) {
        GlDebug.buffer(name, this.id);
        return this;
    }
}
