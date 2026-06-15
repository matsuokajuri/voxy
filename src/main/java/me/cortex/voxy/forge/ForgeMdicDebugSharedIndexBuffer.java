package me.cortex.voxy.forge;

import com.mojang.blaze3d.systems.RenderSystem;
import org.lwjgl.system.MemoryUtil;

import static org.lwjgl.opengl.GL15C.GL_STATIC_DRAW;
import static org.lwjgl.opengl.GL15C.glDeleteBuffers;
import static org.lwjgl.opengl.GL45C.glCreateBuffers;
import static org.lwjgl.opengl.GL45C.nglNamedBufferData;

final class ForgeMdicDebugSharedIndexBuffer {
    static final int INDICES_PER_QUAD = 6;
    static final int VERTICES_PER_QUAD = 4;
    static final String FORMAT = "GL_UNSIGNED_INT";

    private int bufferId;
    private int maxRecords;
    private long bytes;
    private String lastUploadError = "none";

    boolean ensure(int requestedMaxRecords) {
        requireRenderThread("upload MDIC debug shared index buffer");
        int records = Math.max(1, requestedMaxRecords);
        long indexCount = (long) records * INDICES_PER_QUAD;
        long byteSize = indexCount * Integer.BYTES;
        if (byteSize <= 0L || byteSize > Integer.MAX_VALUE) {
            this.lastUploadError = "invalid-size:" + byteSize;
            this.closeOnRenderThread();
            return false;
        }
        if (this.bufferId != 0 && this.maxRecords >= records && this.bytes >= byteSize) {
            this.lastUploadError = "none";
            return true;
        }

        long ptr = MemoryUtil.nmemAlloc(byteSize);
        try {
            long offset = 0L;
            for (int record = 0; record < records; record++) {
                int base = record * VERTICES_PER_QUAD;
                MemoryUtil.memPutInt(ptr + offset, base);
                MemoryUtil.memPutInt(ptr + offset + 4L, base + 1);
                MemoryUtil.memPutInt(ptr + offset + 8L, base + 2);
                MemoryUtil.memPutInt(ptr + offset + 12L, base + 2);
                MemoryUtil.memPutInt(ptr + offset + 16L, base + 3);
                MemoryUtil.memPutInt(ptr + offset + 20L, base);
                offset += INDICES_PER_QUAD * (long) Integer.BYTES;
            }
            if (this.bufferId == 0) {
                this.bufferId = glCreateBuffers();
            }
            nglNamedBufferData(this.bufferId, byteSize, ptr, GL_STATIC_DRAW);
            this.maxRecords = records;
            this.bytes = byteSize;
            this.lastUploadError = "none";
            return true;
        } catch (RuntimeException e) {
            this.lastUploadError = e.getClass().getSimpleName() + ": " + e.getMessage();
            this.closeOnRenderThread();
            return false;
        } finally {
            MemoryUtil.nmemFree(ptr);
        }
    }

    void close() {
        if (RenderSystem.isOnRenderThread()) {
            this.closeOnRenderThread();
        } else {
            RenderSystem.recordRenderCall(this::closeOnRenderThread);
        }
    }

    void closeOnRenderThread() {
        if (this.bufferId != 0) {
            glDeleteBuffers(this.bufferId);
        }
        this.bufferId = 0;
        this.maxRecords = 0;
        this.bytes = 0L;
    }

    int bufferId() {
        return this.bufferId;
    }

    boolean isCreated() {
        return this.bufferId != 0;
    }

    int maxRecords() {
        return this.maxRecords;
    }

    long bytes() {
        return this.bytes;
    }

    String lastUploadError() {
        return this.lastUploadError;
    }

    private static void requireRenderThread(String action) {
        if (!RenderSystem.isOnRenderThread()) {
            throw new IllegalStateException("Cannot " + action + " outside the render thread");
        }
    }
}
