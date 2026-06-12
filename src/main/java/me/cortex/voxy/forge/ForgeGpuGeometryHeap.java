package me.cortex.voxy.forge;

import com.mojang.blaze3d.systems.RenderSystem;
import org.lwjgl.system.MemoryUtil;

import static org.lwjgl.opengl.GL15C.GL_DYNAMIC_DRAW;
import static org.lwjgl.opengl.GL15C.glDeleteBuffers;
import static org.lwjgl.opengl.GL45C.glCreateBuffers;
import static org.lwjgl.opengl.GL45C.nglNamedBufferData;
import static org.lwjgl.opengl.GL45C.nglNamedBufferSubData;

public final class ForgeGpuGeometryHeap {
    static final int GEOMETRY_RECORD_BYTES = Long.BYTES;
    static final int METADATA_BYTES = ForgeSectionGeometryMetadata.SECTION_METADATA_SIZE_BYTES;

    private int geometryBufferId;
    private int metadataBufferId;
    private long geometryCapacityBytes;
    private long metadataCapacityBytes;

    public boolean isCreated() {
        return this.geometryBufferId != 0 && this.metadataBufferId != 0;
    }

    public long geometryCapacityBytes() {
        return this.geometryCapacityBytes;
    }

    public long metadataCapacityBytes() {
        return this.metadataCapacityBytes;
    }

    public void ensureCreated(long geometryCapacityBytes, long metadataCapacityBytes) {
        requireRenderThread("create upload-only geometry heap");
        if (geometryCapacityBytes <= 0 || metadataCapacityBytes <= 0) {
            throw new IllegalArgumentException("GL geometry heap capacities must be positive");
        }
        if (this.isCreated()
                && this.geometryCapacityBytes == geometryCapacityBytes
                && this.metadataCapacityBytes == metadataCapacityBytes) {
            return;
        }

        this.closeOnRenderThread();
        int geometry = 0;
        int metadata = 0;
        try {
            geometry = glCreateBuffers();
            metadata = glCreateBuffers();
            nglNamedBufferData(geometry, geometryCapacityBytes, 0L, GL_DYNAMIC_DRAW);
            nglNamedBufferData(metadata, metadataCapacityBytes, 0L, GL_DYNAMIC_DRAW);
            this.geometryBufferId = geometry;
            this.metadataBufferId = metadata;
            this.geometryCapacityBytes = geometryCapacityBytes;
            this.metadataCapacityBytes = metadataCapacityBytes;
        } catch (RuntimeException e) {
            if (geometry != 0) {
                glDeleteBuffers(geometry);
            }
            if (metadata != 0) {
                glDeleteBuffers(metadata);
            }
            this.geometryBufferId = 0;
            this.metadataBufferId = 0;
            this.geometryCapacityBytes = 0;
            this.metadataCapacityBytes = 0;
            throw e;
        }
    }

    public void uploadGeometry(int geometryPtrItems, long[] records) {
        requireRenderThread("upload geometry records");
        if (!this.isCreated()) {
            throw new IllegalStateException("GL geometry heap is not created");
        }
        if (records == null || records.length == 0) {
            return;
        }
        long offsetBytes = Integer.toUnsignedLong(geometryPtrItems) * GEOMETRY_RECORD_BYTES;
        long byteSize = (long) records.length * GEOMETRY_RECORD_BYTES;
        if (offsetBytes < 0 || offsetBytes + byteSize > this.geometryCapacityBytes) {
            throw new IllegalArgumentException("Geometry upload exceeds GL heap capacity: offset=" + offsetBytes + " bytes=" + byteSize + " capacity=" + this.geometryCapacityBytes);
        }

        long ptr = MemoryUtil.nmemAlloc(byteSize);
        try {
            for (int i = 0; i < records.length; i++) {
                MemoryUtil.memPutLong(ptr + i * (long) GEOMETRY_RECORD_BYTES, records[i]);
            }
            nglNamedBufferSubData(this.geometryBufferId, offsetBytes, byteSize, ptr);
        } finally {
            MemoryUtil.nmemFree(ptr);
        }
    }

    public void uploadMetadata(int sectionId, int[] metadataWords) {
        requireRenderThread("upload section metadata");
        if (!this.isCreated()) {
            throw new IllegalStateException("GL geometry heap is not created");
        }
        if (metadataWords == null || metadataWords.length != ForgeSectionGeometryMetadata.METADATA_WORDS) {
            throw new IllegalArgumentException("Section metadata upload requires 8 int words");
        }
        long offsetBytes = (long) sectionId * METADATA_BYTES;
        if (sectionId < 0 || offsetBytes + METADATA_BYTES > this.metadataCapacityBytes) {
            throw new IllegalArgumentException("Metadata upload exceeds GL heap capacity: sectionId=" + sectionId + " offset=" + offsetBytes + " capacity=" + this.metadataCapacityBytes);
        }

        long ptr = MemoryUtil.nmemAlloc(METADATA_BYTES);
        try {
            for (int i = 0; i < metadataWords.length; i++) {
                MemoryUtil.memPutInt(ptr + i * (long) Integer.BYTES, metadataWords[i]);
            }
            nglNamedBufferSubData(this.metadataBufferId, offsetBytes, METADATA_BYTES, ptr);
        } finally {
            MemoryUtil.nmemFree(ptr);
        }
    }

    public int closeOnRenderThread() {
        requireRenderThread("close upload-only geometry heap");
        int released = 0;
        if (this.geometryBufferId != 0) {
            glDeleteBuffers(this.geometryBufferId);
            this.geometryBufferId = 0;
            released++;
        }
        if (this.metadataBufferId != 0) {
            glDeleteBuffers(this.metadataBufferId);
            this.metadataBufferId = 0;
            released++;
        }
        this.geometryCapacityBytes = 0;
        this.metadataCapacityBytes = 0;
        return released;
    }

    private static void requireRenderThread(String action) {
        if (!RenderSystem.isOnRenderThread()) {
            throw new IllegalStateException("Cannot " + action + " outside the render thread");
        }
    }
}
