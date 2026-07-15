package me.cortex.voxy.client.core.rendering.section.geometry;

import me.cortex.voxy.client.core.vulkan.VoxyVulkanBuffer;
import me.cortex.voxy.client.core.vulkan.VoxyVulkanBufferUsage;
import me.cortex.voxy.common.Logger;

public class BasicSectionGeometryData implements IGeometryData {
    public static final int SECTION_METADATA_SIZE = 32;
    private final VoxyVulkanBuffer sectionMetadataBuffer;
    private final VoxyVulkanBuffer geometryBuffer;
    public final boolean isExternalGeometryBuffer;

    private final int maxSectionCount;
    private int currentSectionCount;

    public BasicSectionGeometryData(int maxSectionCount, VoxyVulkanBuffer geometryBuffer) {
        this.maxSectionCount = maxSectionCount;
        this.sectionMetadataBuffer = createStorageBuffer((long) maxSectionCount * SECTION_METADATA_SIZE,
                "SectionMetadata");
        //8 Cause a quad is 8 bytes
        if ((geometryBuffer.size()%8)!=0) {
            throw new IllegalStateException();
        }
        this.geometryBuffer = geometryBuffer;
        this.isExternalGeometryBuffer = true;
    }

    public BasicSectionGeometryData(int maxSectionCount, long geometryCapacity) {
        this.isExternalGeometryBuffer = false;
        this.maxSectionCount = maxSectionCount;
        this.sectionMetadataBuffer = createStorageBuffer((long) maxSectionCount * SECTION_METADATA_SIZE,
                "SectionMetadata");
        //8 Cause a quad is 8 bytes
        if ((geometryCapacity%8)!=0) {
            throw new IllegalStateException();
        }
        long start = System.currentTimeMillis();
        Logger.info("Creating and zeroing " + (geometryCapacity/(1024*1024)) + "MB Vulkan geometry buffer");
        this.geometryBuffer = createStorageBuffer(geometryCapacity, "GeometryData");
        long delta = System.currentTimeMillis() - start;
        Logger.info("Successfully allocated the geometry buffer in " + delta + "ms");
    }

    public void ensureAccessable(int maxElementAccess) {
        long size = (Integer.toUnsignedLong(maxElementAccess)*8L+65535L)&~65535L;
        if (size > this.geometryBuffer.size()) {
            throw new IllegalStateException("Geometry access exceeds the fully-backed Vulkan arena: "
                    + size + " > " + this.geometryBuffer.size());
        }
    }

    public VoxyVulkanBuffer getGeometryBuffer() {
        return this.geometryBuffer;
    }

    public VoxyVulkanBuffer getMetadataBuffer() {
        return this.sectionMetadataBuffer;
    }

    @Override
    public int getSectionCount() {
        return this.currentSectionCount;
    }

    public void setSectionCount(int count) {
        this.currentSectionCount = count;
    }

    public int getMaxSectionCount() {
        return this.maxSectionCount;
    }

    public long getGeometryCapacityBytes() {//In bytes
        return this.geometryBuffer.size();
    }

    @Override
    public void free() {
        this.sectionMetadataBuffer.close();
        if (!this.isExternalGeometryBuffer) {
            this.geometryBuffer.close();
        }
    }

    @Override
    public long getMaxCapacity() {
        return this.geometryBuffer.size();
    }

    /** The source-only OpenGL renderer is gated off and cannot bind Vulkan-owned storage. */
    public void rejectOpenGlBinding() {
        throw new UnsupportedOperationException("OpenGL geometry binding is retired; no fallback is available");
    }

    private static VoxyVulkanBuffer createStorageBuffer(long size, String label) {
        int roles = VoxyVulkanBufferUsage.STORAGE_COMPUTE
                | VoxyVulkanBufferUsage.STORAGE_GRAPHICS
                | VoxyVulkanBufferUsage.TRANSFER_SOURCE
                | VoxyVulkanBufferUsage.TRANSFER_DESTINATION;
        return VoxyVulkanBuffer.create(size, VoxyVulkanBufferUsage.of(roles), label);
    }
}
