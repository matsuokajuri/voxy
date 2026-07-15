package me.cortex.voxy.client.core.vulkan;

import me.cortex.voxy.client.core.rendering.section.geometry.BasicSectionGeometryData;
import me.cortex.voxy.client.core.vulkan.shader.VulkanComputePipeline;
import me.cortex.voxy.client.core.vulkan.shader.VulkanPushDescriptors;
import me.cortex.voxy.client.core.vulkan.shader.VulkanShaderCompiler;
import me.cortex.voxy.client.core.vulkan.shader.VulkanShaderStage;
import me.cortex.voxy.common.util.MemoryBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.VK12;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Map;

/** Vulkan expression of AsyncNodeManager's original memcpy/scatter GPU operations. */
public final class VulkanAsyncGeometryUpdates implements AutoCloseable {
    private static final long INITIAL_HEADER_CAPACITY = 1L << 16;
    private static final long INITIAL_DATA_CAPACITY = 1L << 20;
    private static final long INITIAL_SCATTER_CAPACITY = 8192L * 2L;

    private final VulkanUploadStream uploads = new VulkanUploadStream();
    private final VulkanComputePipeline multiMemcpy;
    private final VulkanComputePipeline scatterWrite;
    private VoxyVulkanBuffer headerScratch;
    private VoxyVulkanBuffer dataScratch;
    private VoxyVulkanBuffer scatterScratch;
    private boolean closed;

    public VulkanAsyncGeometryUpdates() {
        VulkanComputePipeline createdMultiMemcpy = null;
        VulkanComputePipeline createdScatterWrite = null;
        VoxyVulkanBuffer createdHeaderScratch = null;
        VoxyVulkanBuffer createdDataScratch = null;
        VoxyVulkanBuffer createdScatterScratch = null;
        try {
            try (VulkanShaderCompiler compiler = new VulkanShaderCompiler()) {
                createdMultiMemcpy = new VulkanComputePipeline(
                    compiler.compile("voxy:util/memcpy.comp", VulkanShaderStage.COMPUTE, Map.of(
                            "INPUT_HEADER_BUFFER_BINDING", "0",
                            "INPUT_DATA_BUFFER_BINDING", "1",
                            "OUTPUT_BUFFER_BINDING", "2"
                    )),
                    "Voxy AsyncNode memcpy"
                );
                createdScatterWrite = new VulkanComputePipeline(
                    compiler.compile("voxy:util/scatter.comp", VulkanShaderStage.COMPUTE, Map.of(
                            "INPUT_BUFFER_BINDING", "0",
                            "OUTPUT_BUFFER1_BINDING", "1",
                            "OUTPUT_BUFFER2_BINDING", "2"
                    )),
                    "Voxy AsyncNode scatter"
                );
            }
            createdHeaderScratch = createScratch(INITIAL_HEADER_CAPACITY, "AsyncNode memcpy headers");
            createdDataScratch = createScratch(INITIAL_DATA_CAPACITY, "AsyncNode memcpy data");
            createdScatterScratch = createScratch(INITIAL_SCATTER_CAPACITY, "AsyncNode scatter data");
        } catch (RuntimeException | Error exception) {
            if (createdScatterScratch != null) createdScatterScratch.close();
            if (createdDataScratch != null) createdDataScratch.close();
            if (createdHeaderScratch != null) createdHeaderScratch.close();
            if (createdScatterWrite != null) createdScatterWrite.close();
            if (createdMultiMemcpy != null) createdMultiMemcpy.close();
            throw exception;
        }
        this.multiMemcpy = createdMultiMemcpy;
        this.scatterWrite = createdScatterWrite;
        this.headerScratch = createdHeaderScratch;
        this.dataScratch = createdDataScratch;
        this.scatterScratch = createdScatterScratch;
    }

    public void copyGeometry(
            BasicSectionGeometryData geometry,
            MemoryBuffer headers,
            int copyCount,
            MemoryBuffer data,
            int dataBytes,
            int maxElementAccess
    ) {
        this.ensureOpen();
        if (copyCount <= 0 || dataBytes <= 0 || dataBytes > data.size) {
            throw new IllegalArgumentException("Async geometry memcpy payload is invalid");
        }
        geometry.ensureAccessable(maxElementAccess);
        long writtenRange = Math.multiplyExact(Integer.toUnsignedLong(maxElementAccess), 8L);
        int headerBytes = Math.multiplyExact(copyCount, 16);
        if (headerBytes > headers.size) throw new IllegalArgumentException("Async geometry memcpy headers are truncated");
        this.headerScratch = ensureCapacity(this.headerScratch, headerBytes, "AsyncNode memcpy headers");
        this.dataScratch = ensureCapacity(this.dataScratch, dataBytes, "AsyncNode memcpy data");
        MemoryUtil.memCopy(headers.address, this.uploads.upload(this.headerScratch, 0L, headerBytes).address(), headerBytes);
        MemoryUtil.memCopy(data.address, this.uploads.upload(this.dataScratch, 0L, dataBytes).address(), dataBytes);
        this.uploads.commit();

        VoxyVulkanBuffer geometryBuffer = geometry.getGeometryBuffer();
        VulkanPushDescriptors descriptors = new VulkanPushDescriptors()
                .storageBuffer(0, this.headerScratch, 0L, headerBytes)
                .storageBuffer(1, this.dataScratch, 0L, dataBytes)
                .storageBuffer(2, geometryBuffer, 0L, geometryBuffer.size());
        VulkanCommandRecorder.record(commandBuffer -> {
            // The original GL owner barriers before and after this dispatch. The write-before
            // dependency is essential once an earlier frame has consumed this arena range.
            VulkanSync.bufferBarrier(
                    commandBuffer,
                    geometryBuffer.vkBuffer(),
                    0L,
                    writtenRange,
                    geometryBuffer.policy().declaredAccess(),
                    VulkanSync.COMPUTE_STORAGE_WRITE
            );
            this.multiMemcpy.bind(commandBuffer, descriptors, null);
            VK12.vkCmdDispatch(commandBuffer, copyCount, 1, 1);
            VulkanSync.bufferBarrier(
                    commandBuffer,
                    geometryBuffer.vkBuffer(),
                    0L,
                    writtenRange,
                    VulkanSync.COMPUTE_STORAGE_WRITE,
                    geometryBuffer.policy().consumerAccess()
            );
        });
    }

    public void scatter(
            VoxyVulkanBuffer nodeBuffer,
            BasicSectionGeometryData geometry,
            MemoryBuffer data,
            int streamBytes,
            int writeCount
    ) {
        this.ensureOpen();
        if (writeCount <= 0 || streamBytes <= 0 || streamBytes > data.size || streamBytes % 80 != 0) {
            throw new IllegalArgumentException("Async scatter payload is invalid");
        }
        this.scatterScratch = ensureCapacity(this.scatterScratch, streamBytes, "AsyncNode scatter data");
        MemoryUtil.memCopy(data.address, this.uploads.upload(this.scatterScratch, 0L, streamBytes).address(), streamBytes);
        this.uploads.commit();

        VoxyVulkanBuffer metadataBuffer = geometry.getMetadataBuffer();
        VulkanPushDescriptors descriptors = new VulkanPushDescriptors()
                .storageBuffer(0, this.scatterScratch, 0L, streamBytes)
                .storageBuffer(1, nodeBuffer, 0L, nodeBuffer.size())
                .storageBuffer(2, metadataBuffer, 0L, metadataBuffer.size());
        try (MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer push = stack.malloc(Integer.BYTES).order(ByteOrder.nativeOrder());
            push.putInt(writeCount).flip();
            VulkanCommandRecorder.record(commandBuffer -> {
                // Scatter writes sparse uvec4 records, so synchronize the complete outputs before
                // overwriting them; both buffers may still be read by prior traversal/draw work.
                VulkanSync.bufferBarrier(
                        commandBuffer,
                        nodeBuffer.vkBuffer(),
                        0L,
                        nodeBuffer.size(),
                        nodeBuffer.policy().declaredAccess(),
                        VulkanSync.COMPUTE_STORAGE_WRITE
                );
                VulkanSync.bufferBarrier(
                        commandBuffer,
                        metadataBuffer.vkBuffer(),
                        0L,
                        metadataBuffer.size(),
                        metadataBuffer.policy().declaredAccess(),
                        VulkanSync.COMPUTE_STORAGE_WRITE
                );
                this.scatterWrite.bind(commandBuffer, descriptors, push);
                VK12.vkCmdDispatch(commandBuffer, (writeCount + 127) / 128, 1, 1);
                VulkanSync.bufferBarrier(
                        commandBuffer, nodeBuffer.vkBuffer(), 0L, nodeBuffer.size(),
                        VulkanSync.COMPUTE_STORAGE_WRITE, nodeBuffer.policy().consumerAccess()
                );
                VulkanSync.bufferBarrier(
                        commandBuffer,
                        metadataBuffer.vkBuffer(),
                        0L,
                        metadataBuffer.size(),
                        VulkanSync.COMPUTE_STORAGE_WRITE,
                        metadataBuffer.policy().consumerAccess()
                );
            });
        }
    }

    private static VoxyVulkanBuffer ensureCapacity(VoxyVulkanBuffer current, long required, String label) {
        if (required <= current.size()) return current;
        long newCapacity = current.size();
        while (newCapacity < required) newCapacity = Math.multiplyExact(newCapacity, 2L);
        VoxyVulkanBuffer replacement = createScratch(newCapacity, label);
        current.close();
        return replacement;
    }

    private static VoxyVulkanBuffer createScratch(long size, String label) {
        int roles = VoxyVulkanBufferUsage.STORAGE_COMPUTE | VoxyVulkanBufferUsage.TRANSFER_DESTINATION;
        return VoxyVulkanBuffer.create(size, VoxyVulkanBufferUsage.of(roles), label);
    }

    private void ensureOpen() {
        if (this.closed) throw new IllegalStateException("Vulkan async geometry updates are closed");
    }

    @Override
    public void close() {
        if (!this.closed) {
            this.closed = true;
            this.uploads.commit();
            this.headerScratch.close();
            this.dataScratch.close();
            this.scatterScratch.close();
            this.multiMemcpy.close();
            this.scatterWrite.close();
        }
    }
}
