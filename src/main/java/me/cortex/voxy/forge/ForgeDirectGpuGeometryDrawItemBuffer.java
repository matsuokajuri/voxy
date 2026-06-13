package me.cortex.voxy.forge;

import com.mojang.blaze3d.systems.RenderSystem;
import org.lwjgl.system.MemoryUtil;

import static org.lwjgl.opengl.GL15C.GL_DYNAMIC_DRAW;
import static org.lwjgl.opengl.GL15C.glDeleteBuffers;
import static org.lwjgl.opengl.GL45C.glCreateBuffers;
import static org.lwjgl.opengl.GL45C.nglNamedBufferData;

final class ForgeDirectGpuGeometryDrawItemBuffer {
    static final int BINDING_INDEX = 1;
    private static final int WORDS_PER_ITEM = 8;
    private static final int BYTES_PER_ITEM = WORDS_PER_ITEM * Integer.BYTES;

    private int bufferId;
    private int itemCount;
    private long bytes;
    private long heapGeneration = -1L;
    private long drawListBuildTimeMillis;
    private String lastUploadError = "none";

    boolean upload(ForgeDirectGpuGeometryDrawList drawList) {
        requireRenderThread("upload direct GL draw item buffer");
        if (drawList == null || !drawList.isValid()) {
            this.closeOnRenderThread();
            this.lastUploadError = "draw-list-empty";
            return false;
        }
        if (this.matches(drawList)) {
            this.lastUploadError = "none";
            return true;
        }

        int count = drawList.itemCount();
        long byteSize = (long) count * BYTES_PER_ITEM;
        if (count <= 0 || byteSize <= 0 || byteSize > Integer.MAX_VALUE) {
            this.lastUploadError = "invalid-size:" + byteSize;
            return false;
        }

        long ptr = MemoryUtil.nmemAlloc(byteSize);
        try {
            long offset = 0L;
            for (ForgeDirectGpuGeometryDrawItem item : drawList.items()) {
                MemoryUtil.memPutInt(ptr + offset, item.baseRecord());
                MemoryUtil.memPutInt(ptr + offset + 4L, item.recordCount());
                MemoryUtil.memPutInt(ptr + offset + 8L, item.sectionId());
                MemoryUtil.memPutInt(ptr + offset + 12L, item.bucketMask());
                MemoryUtil.memPutInt(ptr + offset + 16L, Float.floatToRawIntBits(item.originX()));
                MemoryUtil.memPutInt(ptr + offset + 20L, Float.floatToRawIntBits(item.originY()));
                MemoryUtil.memPutInt(ptr + offset + 24L, Float.floatToRawIntBits(item.originZ()));
                MemoryUtil.memPutInt(ptr + offset + 28L, Float.floatToRawIntBits(item.scale()));
                offset += BYTES_PER_ITEM;
            }
            if (this.bufferId == 0) {
                this.bufferId = glCreateBuffers();
            }
            nglNamedBufferData(this.bufferId, byteSize, ptr, GL_DYNAMIC_DRAW);
            this.itemCount = count;
            this.bytes = byteSize;
            this.heapGeneration = drawList.heapGeneration();
            this.drawListBuildTimeMillis = drawList.buildTimeMillis();
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

    boolean matches(ForgeDirectGpuGeometryDrawList drawList) {
        return drawList != null
                && drawList.isValid()
                && this.bufferId != 0
                && this.itemCount == drawList.itemCount()
                && this.heapGeneration == drawList.heapGeneration()
                && this.drawListBuildTimeMillis == drawList.buildTimeMillis();
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
        this.itemCount = 0;
        this.bytes = 0L;
        this.heapGeneration = -1L;
        this.drawListBuildTimeMillis = 0L;
    }

    int bufferId() {
        return this.bufferId;
    }

    boolean isCreated() {
        return this.bufferId != 0;
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
