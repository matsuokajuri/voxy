package me.cortex.voxy.forge;

import com.mojang.blaze3d.systems.RenderSystem;
import org.lwjgl.system.MemoryUtil;

import java.util.List;

import static org.lwjgl.opengl.GL15C.GL_DYNAMIC_DRAW;
import static org.lwjgl.opengl.GL15C.glDeleteBuffers;
import static org.lwjgl.opengl.GL45C.glCreateBuffers;
import static org.lwjgl.opengl.GL45C.nglGetNamedBufferSubData;
import static org.lwjgl.opengl.GL45C.nglNamedBufferData;

final class ForgeModelDataBuffer {
    private int modelDataBufferId;
    private int modelColourBufferId;
    private int recordCount;
    private long modelDataBytes;
    private long modelColourBytes;
    private long generation = -1L;
    private String dimensionId = "none";
    private String lastUploadError = "none";

    boolean upload(List<ForgeModelStoreRecord> records, long generation, String dimensionId) {
        requireRenderThread("upload placeholder model data buffers");
        if (records == null || records.isEmpty()) {
            this.closeOnRenderThread();
            this.lastUploadError = "no-placeholder-records";
            return false;
        }

        long dataBytes = (long) records.size() * ForgeModelStoreLayout.BYTES;
        long colourBytes = (long) records.size() * Integer.BYTES;
        if (dataBytes <= 0L || dataBytes > Integer.MAX_VALUE || colourBytes <= 0L || colourBytes > Integer.MAX_VALUE) {
            this.lastUploadError = "invalid-size:modelData=" + dataBytes + ",modelColour=" + colourBytes;
            return false;
        }

        long dataPtr = 0L;
        long colourPtr = 0L;
        try {
            dataPtr = MemoryUtil.nmemAlloc(dataBytes);
            colourPtr = MemoryUtil.nmemAlloc(colourBytes);
            long dataOffset = 0L;
            long colourOffset = 0L;
            for (ForgeModelStoreRecord record : records) {
                for (int word : record.toWords()) {
                    MemoryUtil.memPutInt(dataPtr + dataOffset, word);
                    dataOffset += Integer.BYTES;
                }
                MemoryUtil.memPutInt(colourPtr + colourOffset, record.debugColour());
                colourOffset += Integer.BYTES;
            }

            if (this.modelDataBufferId == 0) {
                this.modelDataBufferId = glCreateBuffers();
            }
            if (this.modelColourBufferId == 0) {
                this.modelColourBufferId = glCreateBuffers();
            }
            nglNamedBufferData(this.modelDataBufferId, dataBytes, dataPtr, GL_DYNAMIC_DRAW);
            nglNamedBufferData(this.modelColourBufferId, colourBytes, colourPtr, GL_DYNAMIC_DRAW);
            this.recordCount = records.size();
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
        requireRenderThread("read back placeholder model data buffer");
        if (!this.isModelDataCreated()) {
            throw new IllegalStateException("placeholder modelData buffer is not created");
        }
        int[] words = new int[this.recordCount * ForgeModelStoreLayout.WORDS];
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
        requireRenderThread("read back placeholder modelColour buffer");
        if (!this.isModelColourCreated()) {
            throw new IllegalStateException("placeholder modelColour buffer is not created");
        }
        int[] colours = new int[this.recordCount];
        long ptr = MemoryUtil.nmemAlloc(this.modelColourBytes);
        try {
            nglGetNamedBufferSubData(this.modelColourBufferId, 0L, this.modelColourBytes, ptr);
            for (int i = 0; i < colours.length; i++) {
                colours[i] = MemoryUtil.memGetInt(ptr + i * (long) Integer.BYTES);
            }
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

    int recordCount() {
        return this.recordCount;
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
