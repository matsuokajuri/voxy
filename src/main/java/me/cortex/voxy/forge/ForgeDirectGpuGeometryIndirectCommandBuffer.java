package me.cortex.voxy.forge;

import com.mojang.blaze3d.systems.RenderSystem;
import org.lwjgl.system.MemoryUtil;

import static org.lwjgl.opengl.GL15C.GL_DYNAMIC_DRAW;
import static org.lwjgl.opengl.GL15C.glDeleteBuffers;
import static org.lwjgl.opengl.GL45C.glCreateBuffers;
import static org.lwjgl.opengl.GL45C.nglNamedBufferData;

final class ForgeDirectGpuGeometryIndirectCommandBuffer {
    private static final int WORDS_PER_COMMAND = 4;
    private static final int BYTES_PER_COMMAND = WORDS_PER_COMMAND * Integer.BYTES;

    private int bufferId;
    private int commandCount;
    private long bytes;
    private long heapGeneration = -1L;
    private long drawListBuildTimeMillis;
    private String lastUploadError = "none";

    boolean upload(ForgeDirectGpuGeometryDrawList drawList) {
        requireRenderThread("upload direct GL indirect command buffer");
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
        long byteSize = (long) count * BYTES_PER_COMMAND;
        if (count <= 0 || byteSize <= 0 || byteSize > Integer.MAX_VALUE) {
            this.lastUploadError = "invalid-size:" + byteSize;
            return false;
        }

        long ptr = MemoryUtil.nmemAlloc(byteSize);
        try {
            long offset = 0L;
            int drawIndex = 0;
            for (ForgeDirectGpuGeometryDrawItem item : drawList.items()) {
                MemoryUtil.memPutInt(ptr + offset, item.vertexCount());
                MemoryUtil.memPutInt(ptr + offset + 4L, 1);
                MemoryUtil.memPutInt(ptr + offset + 8L, 0);
                MemoryUtil.memPutInt(ptr + offset + 12L, drawIndex++);
                offset += BYTES_PER_COMMAND;
            }
            if (this.bufferId == 0) {
                this.bufferId = glCreateBuffers();
            }
            nglNamedBufferData(this.bufferId, byteSize, ptr, GL_DYNAMIC_DRAW);
            this.commandCount = count;
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
                && this.commandCount == drawList.itemCount()
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
        this.commandCount = 0;
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
