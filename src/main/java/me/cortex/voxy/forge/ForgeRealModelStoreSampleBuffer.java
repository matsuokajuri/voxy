package me.cortex.voxy.forge;

import com.mojang.blaze3d.systems.RenderSystem;
import org.lwjgl.system.MemoryUtil;

import static org.lwjgl.opengl.GL15C.GL_DYNAMIC_DRAW;
import static org.lwjgl.opengl.GL15C.glDeleteBuffers;
import static org.lwjgl.opengl.GL45C.glCreateBuffers;
import static org.lwjgl.opengl.GL45C.nglGetNamedBufferSubData;
import static org.lwjgl.opengl.GL45C.nglNamedBufferData;

final class ForgeRealModelStoreSampleBuffer {
    private int modelDataBufferId;
    private int modelColourBufferId;
    private int recordCount;
    private long modelDataBytes;
    private long modelColourBytes;
    private long generation = -1L;
    private String dimensionId = "none";
    private String lastUploadError = "none";

    boolean upload(ForgeRealModelStoreSampleRecord record, long generation, String dimensionId) {
        requireRenderThread("upload real ModelStore sample buffers");
        if (record == null) {
            this.closeOnRenderThread();
            this.lastUploadError = "no-real-model-record-sample";
            return false;
        }

        long dataBytes = ForgeRealModelStoreSampleRecord.BYTES;
        long colourBytes = Integer.BYTES;
        long dataPtr = 0L;
        long colourPtr = 0L;
        try {
            dataPtr = MemoryUtil.nmemAlloc(dataBytes);
            colourPtr = MemoryUtil.nmemAlloc(colourBytes);
            long dataOffset = 0L;
            for (int word : record.toWords()) {
                MemoryUtil.memPutInt(dataPtr + dataOffset, word);
                dataOffset += Integer.BYTES;
            }
            MemoryUtil.memPutInt(colourPtr, record.modelColour());

            if (this.modelDataBufferId == 0) {
                this.modelDataBufferId = glCreateBuffers();
            }
            if (this.modelColourBufferId == 0) {
                this.modelColourBufferId = glCreateBuffers();
            }
            nglNamedBufferData(this.modelDataBufferId, dataBytes, dataPtr, GL_DYNAMIC_DRAW);
            nglNamedBufferData(this.modelColourBufferId, colourBytes, colourPtr, GL_DYNAMIC_DRAW);
            this.recordCount = 1;
            this.modelDataBytes = dataBytes;
            this.modelColourBytes = colourBytes;
            this.generation = generation;
            this.dimensionId = dimensionId == null ? "none" : dimensionId;
            this.lastUploadError = "none";
            return true;
        } catch (RuntimeException e) {
            this.lastUploadError = e.getClass().getSimpleName() + ": " + e.getMessage();
            this.closeOnRenderThread();
            return false;
        } finally {
            if (dataPtr != 0L) {
                MemoryUtil.nmemFree(dataPtr);
            }
            if (colourPtr != 0L) {
                MemoryUtil.nmemFree(colourPtr);
            }
        }
    }

    int[] readbackModelDataWords() {
        requireRenderThread("read back real ModelStore sample modelData buffer");
        if (!this.isModelDataCreated()) {
            throw new IllegalStateException("real model record sample modelData buffer is not created");
        }
        int[] words = new int[ForgeRealModelStoreSampleRecord.WORDS];
        long ptr = MemoryUtil.nmemAlloc(this.modelDataBytes);
        try {
            nglGetNamedBufferSubData(this.modelDataBufferId, 0L, this.modelDataBytes, ptr);
            for (int i = 0; i < words.length; i++) {
                words[i] = MemoryUtil.memGetInt(ptr + i * (long) Integer.BYTES);
            }
            return words;
        } finally {
            MemoryUtil.nmemFree(ptr);
        }
    }

    int[] readbackModelColours() {
        requireRenderThread("read back real ModelStore sample modelColour buffer");
        if (!this.isModelColourCreated()) {
            throw new IllegalStateException("real model record sample modelColour buffer is not created");
        }
        int[] colours = new int[1];
        long ptr = MemoryUtil.nmemAlloc(this.modelColourBytes);
        try {
            nglGetNamedBufferSubData(this.modelColourBufferId, 0L, this.modelColourBytes, ptr);
            colours[0] = MemoryUtil.memGetInt(ptr);
            return colours;
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
        if (this.modelDataBufferId != 0) {
            glDeleteBuffers(this.modelDataBufferId);
        }
        if (this.modelColourBufferId != 0) {
            glDeleteBuffers(this.modelColourBufferId);
        }
        this.modelDataBufferId = 0;
        this.modelColourBufferId = 0;
        this.recordCount = 0;
        this.modelDataBytes = 0L;
        this.modelColourBytes = 0L;
        this.generation = -1L;
        this.dimensionId = "none";
    }

    boolean isModelDataCreated() {
        return this.modelDataBufferId != 0 && this.recordCount > 0 && this.modelDataBytes > 0L;
    }

    boolean isModelColourCreated() {
        return this.modelColourBufferId != 0 && this.recordCount > 0 && this.modelColourBytes > 0L;
    }

    boolean isStale(long currentGeneration, String currentDimension) {
        return this.isModelDataCreated()
                && (this.generation != currentGeneration
                || currentDimension == null
                || !this.dimensionId.equals(currentDimension));
    }

    long modelDataBytes() {
        return this.modelDataBytes;
    }

    long modelColourBytes() {
        return this.modelColourBytes;
    }

    long generation() {
        return this.generation;
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
