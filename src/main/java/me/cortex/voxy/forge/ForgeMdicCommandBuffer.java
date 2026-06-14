package me.cortex.voxy.forge;

import com.mojang.blaze3d.systems.RenderSystem;
import org.lwjgl.system.MemoryUtil;

import static org.lwjgl.opengl.GL15C.GL_DYNAMIC_DRAW;
import static org.lwjgl.opengl.GL15C.glDeleteBuffers;
import static org.lwjgl.opengl.GL45C.glCreateBuffers;
import static org.lwjgl.opengl.GL45C.nglGetNamedBufferSubData;
import static org.lwjgl.opengl.GL45C.nglNamedBufferData;

final class ForgeMdicCommandBuffer {
    private int bufferId;
    private int commandCount;
    private long bytes;
    private long heapGeneration = -1L;
    private long commandListBuildTimeMillis;
    private String dimensionId = "none";
    private String lastUploadError = "none";

    boolean upload(ForgeMdicCommandList commandList) {
        requireRenderThread("upload MDIC skeleton command buffer");
        if (commandList == null || !commandList.isValid()) {
            this.closeOnRenderThread();
            this.lastUploadError = "command-list-empty";
            return false;
        }
        if (this.matches(commandList)) {
            this.lastUploadError = "none";
            return true;
        }

        int count = commandList.commandCount();
        long byteSize = (long) count * ForgeMdicCommandLayout.BYTES;
        if (count <= 0 || byteSize <= 0 || byteSize > Integer.MAX_VALUE) {
            this.lastUploadError = "invalid-size:" + byteSize;
            return false;
        }

        long ptr = MemoryUtil.nmemAlloc(byteSize);
        try {
            long offset = 0L;
            for (ForgeMdicCommand command : commandList.commands()) {
                int[] words = command.toWords();
                for (int word : words) {
                    MemoryUtil.memPutInt(ptr + offset, word);
                    offset += Integer.BYTES;
                }
            }
            if (this.bufferId == 0) {
                this.bufferId = glCreateBuffers();
            }
            nglNamedBufferData(this.bufferId, byteSize, ptr, GL_DYNAMIC_DRAW);
            this.commandCount = count;
            this.bytes = byteSize;
            this.heapGeneration = commandList.heapGeneration();
            this.commandListBuildTimeMillis = commandList.buildTimeMillis();
            this.dimensionId = commandList.dimensionId();
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

    boolean matches(ForgeMdicCommandList commandList) {
        return commandList != null
                && commandList.isValid()
                && this.bufferId != 0
                && this.commandCount == commandList.commandCount()
                && this.heapGeneration == commandList.heapGeneration()
                && this.commandListBuildTimeMillis == commandList.buildTimeMillis()
                && this.dimensionId.equals(commandList.dimensionId());
    }

    boolean isStale(long currentHeapGeneration, String currentDimension) {
        return this.isCreated()
                && (this.heapGeneration != currentHeapGeneration
                || currentDimension == null
                || !this.dimensionId.equals(currentDimension));
    }

    int[] readbackWords() {
        requireRenderThread("read back MDIC skeleton command buffer");
        if (this.bufferId == 0 || this.commandCount <= 0 || this.bytes <= 0) {
            throw new IllegalStateException("MDIC skeleton command buffer is not created");
        }
        if (this.bytes > Integer.MAX_VALUE) {
            throw new IllegalStateException("MDIC skeleton command buffer is too large to audit: " + this.bytes);
        }

        int[] words = new int[this.commandCount * ForgeMdicCommandLayout.WORDS];
        long ptr = MemoryUtil.nmemAlloc(this.bytes);
        try {
            nglGetNamedBufferSubData(this.bufferId, 0L, this.bytes, ptr);
            for (int i = 0; i < words.length; i++) {
                words[i] = MemoryUtil.memGetInt(ptr + i * (long) Integer.BYTES);
            }
            return words;
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
        this.commandCount = 0;
        this.bytes = 0L;
        this.heapGeneration = -1L;
        this.commandListBuildTimeMillis = 0L;
        this.dimensionId = "none";
    }

    boolean isCreated() {
        return this.bufferId != 0;
    }

    int bufferIdForDebugRenderer() {
        return this.bufferId;
    }

    long bytes() {
        return this.bytes;
    }

    long heapGeneration() {
        return this.heapGeneration;
    }

    int commandCount() {
        return this.commandCount;
    }

    long commandListBuildTimeMillis() {
        return this.commandListBuildTimeMillis;
    }

    String dimensionId() {
        return this.dimensionId;
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
