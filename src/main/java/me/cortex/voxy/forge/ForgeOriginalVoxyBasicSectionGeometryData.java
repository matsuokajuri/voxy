package me.cortex.voxy.forge;

import com.mojang.blaze3d.systems.RenderSystem;
import org.lwjgl.system.Platform;

import static org.lwjgl.opengl.ARBSparseBuffer.GL_SPARSE_STORAGE_BIT_ARB;
import static org.lwjgl.opengl.ARBSparseBuffer.glBufferPageCommitmentARB;
import static org.lwjgl.opengl.GL.getCapabilities;
import static org.lwjgl.opengl.GL11C.GL_NO_ERROR;
import static org.lwjgl.opengl.GL11C.GL_OUT_OF_MEMORY;
import static org.lwjgl.opengl.GL11C.GL_UNSIGNED_BYTE;
import static org.lwjgl.opengl.GL11C.GL_VENDOR;
import static org.lwjgl.opengl.GL11C.glFinish;
import static org.lwjgl.opengl.GL11C.glGetError;
import static org.lwjgl.opengl.GL11C.glGetString;
import static org.lwjgl.opengl.GL15C.GL_ARRAY_BUFFER;
import static org.lwjgl.opengl.GL15C.glBindBuffer;
import static org.lwjgl.opengl.GL15C.glDeleteBuffers;
import static org.lwjgl.opengl.GL30C.GL_R8UI;
import static org.lwjgl.opengl.GL30C.GL_RED_INTEGER;
import static org.lwjgl.opengl.GL43C.GL_MAX_SHADER_STORAGE_BLOCK_SIZE;
import static org.lwjgl.opengl.GL45C.glCreateBuffers;
import static org.lwjgl.opengl.GL45C.nglClearNamedBufferData;
import static org.lwjgl.opengl.GL45C.nglNamedBufferStorage;
import static org.lwjgl.opengl.GL32C.glGetInteger64;
import static org.lwjgl.opengl.NVXGPUMemoryInfo.GL_GPU_MEMORY_INFO_CURRENT_AVAILABLE_VIDMEM_NVX;

final class ForgeOriginalVoxyBasicSectionGeometryData {
    static final int SECTION_METADATA_SIZE = 32;
    private static final long MIN_GEOMETRY_CAPACITY_BYTES = 512L * 1024L * 1024L;
    private static final long NVIDIA_LINUX_GEOMETRY_CAP_BYTES = 2000L * 1024L * 1024L;
    private static final long GPU_MEMORY_HEADROOM_BYTES = (long) (1.5D * 1024D * 1024D * 1024D);
    private static final long GPU_MEMORY_RELEASE_WAIT_TIMEOUT_MS = 400L;

    private final int maxSectionCount;
    private int sectionMetadataBufferId;
    private int geometryBufferId;
    private boolean externalGeometryBuffer;
    private boolean sparseGeometryBuffer;
    private boolean sparseBufferSupported;
    private boolean nvidiaWindowsSparseWorkaroundUsed;
    private int currentSectionCount;
    private long metadataCapacityBytes;
    private long geometryCapacityBytes;
    private long sparseCommitmentBytes;
    private long generation;
    private long buildCount;
    private long freeCount;
    private int lastGlError;
    private String lastLifecycleEvent = "created";
    private String lastFailureReason = "none";

    ForgeOriginalVoxyBasicSectionGeometryData(int maxSectionCount) {
        this.maxSectionCount = maxSectionCount;
    }

    String buildOnRenderThread() {
        requireRenderThread("build original BasicSectionGeometryData");
        this.freeOnRenderThread();
        this.metadataCapacityBytes = (long) this.maxSectionCount * SECTION_METADATA_SIZE;
        this.geometryCapacityBytes = computeOriginalGeometryCapacityBytes();
        if (this.geometryCapacityBytes % Long.BYTES != 0) {
            this.recordFailure("geometry-capacity-not-8-byte-aligned");
            return this.lastFailureReason;
        }

        int metadataBuffer = 0;
        int geometryBuffer = 0;
        try {
            metadataBuffer = createStorageBuffer(this.metadataCapacityBytes, 0, true);
            GeometryAllocation geometry = createGeometryBuffer(this.geometryCapacityBytes);
            geometryBuffer = geometry.bufferId;
            this.sectionMetadataBufferId = metadataBuffer;
            this.geometryBufferId = geometryBuffer;
            this.sparseGeometryBuffer = geometry.sparse;
            this.sparseBufferSupported = getCapabilities().GL_ARB_sparse_buffer;
            this.nvidiaWindowsSparseWorkaroundUsed = geometry.nvidiaWindowsSparseWorkaroundUsed;
            this.externalGeometryBuffer = false;
            this.currentSectionCount = 0;
            this.sparseCommitmentBytes = 0L;
            this.generation++;
            this.buildCount++;
            this.lastLifecycleEvent = "build-on-render-thread";
            this.lastFailureReason = "none";
            return "none";
        } catch (RuntimeException e) {
            if (geometryBuffer != 0) {
                glDeleteBuffers(geometryBuffer);
            }
            if (metadataBuffer != 0) {
                glDeleteBuffers(metadataBuffer);
            }
            this.sectionMetadataBufferId = 0;
            this.geometryBufferId = 0;
            this.recordFailure("basic-section-geometry-data-" + e.getClass().getSimpleName() + ":" + e.getMessage());
            return this.lastFailureReason;
        }
    }

    void ensureAccessable(int maxElementAccess) {
        requireRenderThread("ensure original geometry sparse access");
        long size = (Integer.toUnsignedLong(maxElementAccess) * Long.BYTES + 65535L) & ~65535L;
        if (this.sparseGeometryBuffer && this.sparseCommitmentBytes < size) {
            glBindBuffer(GL_ARRAY_BUFFER, this.geometryBufferId);
            size += 65536L * 1024L;
            glBufferPageCommitmentARB(GL_ARRAY_BUFFER, this.sparseCommitmentBytes, size - this.sparseCommitmentBytes, true);
            glBindBuffer(GL_ARRAY_BUFFER, 0);
            this.sparseCommitmentBytes = size;
        }
    }

    void setSectionCount(int count) {
        this.currentSectionCount = count;
    }

    int sectionCount() {
        return this.currentSectionCount;
    }

    int metadataBufferId() {
        return this.sectionMetadataBufferId;
    }

    int geometryBufferId() {
        return this.geometryBufferId;
    }

    long geometryCapacityBytes() {
        return this.geometryCapacityBytes;
    }

    void freeOnRenderThread() {
        requireRenderThread("free original BasicSectionGeometryData");
        if (this.sectionMetadataBufferId != 0) {
            glDeleteBuffers(this.sectionMetadataBufferId);
            this.sectionMetadataBufferId = 0;
        }
        if (this.geometryBufferId != 0) {
            long gpuMemoryBeforeFree = -1L;
            long committedSparseBytes = this.sparseCommitmentBytes;
            if (getCapabilities().GL_NVX_gpu_memory_info) {
                glFinish();
                gpuMemoryBeforeFree = queryFreeDedicatedGpuMemoryBytes();
            }
            if (this.sparseGeometryBuffer) {
                glBindBuffer(GL_ARRAY_BUFFER, this.geometryBufferId);
                glBufferPageCommitmentARB(GL_ARRAY_BUFFER, 0, this.sparseCommitmentBytes, false);
                glBindBuffer(GL_ARRAY_BUFFER, 0);
            }
            glFinish();
            if (!this.externalGeometryBuffer) {
                glDeleteBuffers(this.geometryBufferId);
                glFinish();
                if (gpuMemoryBeforeFree != -1L) {
                    long releaseSize = (long) (this.geometryCapacityBytes * 0.75D);
                    if (this.sparseGeometryBuffer) {
                        releaseSize = (long) (committedSparseBytes * 0.75D);
                    }
                    if (queryFreeDedicatedGpuMemoryBytes() - gpuMemoryBeforeFree <= releaseSize) {
                        long start = System.currentTimeMillis();
                        while (System.currentTimeMillis() - start < GPU_MEMORY_RELEASE_WAIT_TIMEOUT_MS) {
                            glFinish();
                            if (queryFreeDedicatedGpuMemoryBytes() - gpuMemoryBeforeFree > releaseSize) {
                                break;
                            }
                        }
                        if (queryFreeDedicatedGpuMemoryBytes() - gpuMemoryBeforeFree <= releaseSize) {
                            VoxyForge.LOGGER.warn(
                                    "Failed to wait for original geometry buffer memory to be freed; this could indicate a driver issue");
                        }
                    }
                }
            }
            this.geometryBufferId = 0;
            this.freeCount++;
            this.generation++;
        }
        this.currentSectionCount = 0;
        this.sparseCommitmentBytes = 0L;
        this.lastLifecycleEvent = "free-on-render-thread";
    }

    ForgeOriginalVoxyBasicSectionGeometryDataStats createStatusSnapshot() {
        boolean ready = this.sectionMetadataBufferId != 0 && this.geometryBufferId != 0;
        return new ForgeOriginalVoxyBasicSectionGeometryDataStats(
                ready,
                ready,
                true,
                this.sectionMetadataBufferId != 0,
                this.geometryBufferId != 0,
                this.externalGeometryBuffer,
                this.sparseGeometryBuffer,
                this.sparseBufferSupported,
                this.nvidiaWindowsSparseWorkaroundUsed,
                this.maxSectionCount,
                this.currentSectionCount,
                this.sectionMetadataBufferId,
                this.geometryBufferId,
                this.metadataCapacityBytes,
                this.geometryCapacityBytes,
                this.sparseCommitmentBytes,
                this.generation,
                this.buildCount,
                this.freeCount,
                this.lastGlError,
                this.lastLifecycleEvent,
                this.lastFailureReason
        );
    }

    private static GeometryAllocation createGeometryBuffer(long geometryCapacityBytes) {
        var capabilities = getCapabilities();
        boolean sparseSupported = capabilities.GL_ARB_sparse_buffer;
        boolean isNvidiaWindows = isNvidiaVendor() && Platform.get() == Platform.WINDOWS && sparseSupported;
        int buffer = 0;
        glGetError();
        if (!isNvidiaWindows) {
            buffer = createStorageBuffer(geometryCapacityBytes, 0, false);
        } else {
            VoxyForge.LOGGER.info("Running on nvidia, using original sparse buffer allocation workaround");
        }

        int error = glGetError();
        if (error != GL_NO_ERROR || buffer == 0) {
            if ((buffer == 0 || error == GL_OUT_OF_MEMORY) && sparseSupported) {
                if (buffer != 0) {
                    glDeleteBuffers(buffer);
                }
                buffer = createStorageBuffer(geometryCapacityBytes, GL_SPARSE_STORAGE_BIT_ARB, false);
                error = glGetError();
                if (error != GL_NO_ERROR) {
                    glDeleteBuffers(buffer);
                    throw new IllegalStateException("Unable to allocate geometry buffer using sparse workaround, got gl error " + error);
                }
                return new GeometryAllocation(buffer, true, isNvidiaWindows);
            }
            throw new IllegalStateException("Unable to allocate geometry buffer, got gl error " + error);
        }
        return new GeometryAllocation(buffer, false, false);
    }

    private static int createStorageBuffer(long size, int flags, boolean zero) {
        int buffer = glCreateBuffers();
        nglNamedBufferStorage(buffer, size, 0L, flags);
        if ((flags & GL_SPARSE_STORAGE_BIT_ARB) == 0 && zero) {
            nglClearNamedBufferData(buffer, GL_R8UI, GL_RED_INTEGER, GL_UNSIGNED_BYTE, 0L);
        }
        return buffer;
    }

    private static long computeOriginalGeometryCapacityBytes() {
        long ssboMaxSize = glGetInteger64(GL_MAX_SHADER_STORAGE_BLOCK_SIZE);
        long geometryCapacity = Math.min(roundUpPowerOfTwo(ssboMaxSize) << 1, 1L << 32) - 1024L;
        if (isIntelVendor()) {
            geometryCapacity = Math.max(geometryCapacity, 1L << 30);
        }
        if (isNvidiaVendor() && Platform.get() == Platform.LINUX) {
            geometryCapacity = Math.min(geometryCapacity, NVIDIA_LINUX_GEOMETRY_CAP_BYTES);
        }
        geometryCapacity = Math.max(MIN_GEOMETRY_CAPACITY_BYTES, geometryCapacity);
        if (getCapabilities().GL_NVX_gpu_memory_info) {
            long limit = glGetInteger64(GL_GPU_MEMORY_INFO_CURRENT_AVAILABLE_VIDMEM_NVX) * 1024L - GPU_MEMORY_HEADROOM_BYTES;
            limit = Math.max(MIN_GEOMETRY_CAPACITY_BYTES, limit);
            geometryCapacity = Math.min(geometryCapacity, limit);
        }
        String override = System.getProperty("voxy.geometryBufferSizeOverrideMB", "");
        if (!override.isEmpty()) {
            geometryCapacity = Long.parseLong(override) * 1024L * 1024L;
        }
        return geometryCapacity & ~(Long.BYTES - 1L);
    }

    private static long queryFreeDedicatedGpuMemoryBytes() {
        return glGetInteger64(GL_GPU_MEMORY_INFO_CURRENT_AVAILABLE_VIDMEM_NVX) * 1024L;
    }

    private static long roundUpPowerOfTwo(long value) {
        if (value <= 1L) {
            return 1L;
        }
        return 1L << (64 - Long.numberOfLeadingZeros(value - 1L));
    }

    private static boolean isNvidiaVendor() {
        String vendor = glGetString(GL_VENDOR);
        return vendor != null && vendor.toLowerCase(java.util.Locale.ROOT).contains("nvidia");
    }

    private static boolean isIntelVendor() {
        String vendor = glGetString(GL_VENDOR);
        return vendor != null && vendor.toLowerCase(java.util.Locale.ROOT).contains("intel");
    }

    private void recordFailure(String reason) {
        this.lastGlError = glGetError();
        this.lastLifecycleEvent = "failure";
        this.lastFailureReason = reason == null || reason.isBlank() ? "unspecified" : reason;
    }

    private static void requireRenderThread(String action) {
        if (!RenderSystem.isOnRenderThread()) {
            throw new IllegalStateException("Cannot " + action + " outside the render thread");
        }
    }

    private record GeometryAllocation(int bufferId, boolean sparse, boolean nvidiaWindowsSparseWorkaroundUsed) {
    }
}
