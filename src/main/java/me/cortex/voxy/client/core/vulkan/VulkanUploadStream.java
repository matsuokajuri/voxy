package me.cortex.voxy.client.core.vulkan;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.vulkan.VulkanGpuBuffer;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VK12;
import org.lwjgl.vulkan.VkBufferCopy;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;

/**
 * Vulkan expression of the original UploadStream contract.
 * CPU-visible storage comes from Minecraft's transient-memory allocator, while copies are recorded
 * into Minecraft's current graphics-queue submission.
 */
public final class VulkanUploadStream {
    public static final long DEFAULT_CAPACITY = 1L << 26;

    private final long capacity;
    private final Deque<PendingCopy> pendingCopies = new ArrayDeque<>();
    private long allocatedBytesThisSubmit;

    public VulkanUploadStream() {
        this(DEFAULT_CAPACITY);
    }

    public VulkanUploadStream(long capacity) {
        if (capacity <= 0L) {
            throw new IllegalArgumentException("Upload capacity must be greater than zero");
        }
        this.capacity = capacity;
    }

    public UploadAllocation upload(VoxyVulkanBuffer target, long targetOffset, int size) {
        if (target.isClosed()) {
            throw new IllegalStateException("Cannot upload into a closed Vulkan buffer");
        }
        if ((target.usage() & GpuBuffer.USAGE_COPY_DST) == 0) {
            throw new IllegalArgumentException("Upload target must have USAGE_COPY_DST");
        }
        VulkanRanges.check(targetOffset, size, target.size(), "Upload target");

        UploadAllocation allocation = this.allocate(size);
        this.pendingCopies.addLast(new PendingCopy(allocation, target.slice(targetOffset, size)));
        return allocation;
    }

    public UploadAllocation allocate(int size) {
        if (size < 0) {
            throw new IllegalArgumentException("Upload size cannot be negative");
        }
        if (size == 0) {
            throw new IllegalArgumentException("Upload size must be greater than zero");
        }

        long alignment = Math.max(16L, VoxyVulkanContext.get().capabilities().minStorageBufferOffsetAlignment());
        long allocationSize = alignUp(size, alignment);
        if (allocationSize > this.capacity) {
            throw new IllegalArgumentException("Upload allocation exceeds the original 64 MiB stream capacity");
        }
        if (allocationSize > this.capacity - this.allocatedBytesThisSubmit) {
            this.commit();
            VoxyVulkanContext.get().hostDevice().createCommandEncoder().submit();
            this.allocatedBytesThisSubmit = 0L;
        }

        CommandEncoder encoder = VoxyVulkanContext.get().hostDevice().createCommandEncoder();
        GpuBufferSlice.MappedView mapped = encoder.transientMemory()
                .allocateStaging(allocationSize, alignment, GpuBuffer.USAGE_COPY_SRC);
        ByteBuffer data = mapped.data().duplicate().order(ByteOrder.nativeOrder());
        data.clear();
        data.limit(size);
        data = data.slice().order(ByteOrder.nativeOrder());

        this.allocatedBytesThisSubmit += allocationSize;
        return new UploadAllocation(mapped, data, size, allocationSize);
    }

    public void commit() {
        if (this.pendingCopies.isEmpty()) {
            return;
        }

        ArrayList<PendingCopy> copies = new ArrayList<>(this.pendingCopies);
        this.pendingCopies.clear();
        try {
            VulkanCommandRecorder.record(commandBuffer -> {
                for (PendingCopy copy : copies) {
                    GpuBufferSlice sourceSlice = copy.source().slice();
                    VulkanSync.bufferBarrier(
                            commandBuffer,
                            copy.target().buffer().vkBuffer(),
                            copy.target().offset(),
                            copy.target().length(),
                            copy.target().buffer().policy().declaredAccess(),
                            VulkanSync.TRANSFER_WRITE
                    );
                    try (MemoryStack stack = MemoryStack.stackPush()) {
                        VkBufferCopy.Buffer region = VkBufferCopy.calloc(1, stack);
                        region.srcOffset(sourceSlice.offset());
                        region.dstOffset(copy.target().offset());
                        region.size(sourceSlice.length());
                        VK12.vkCmdCopyBuffer(
                                commandBuffer,
                                ((VulkanGpuBuffer) sourceSlice.buffer()).vkBuffer(),
                                copy.target().buffer().vkBuffer(),
                                region
                        );
                    }
                    VulkanSync.bufferBarrier(
                            commandBuffer,
                            copy.target().buffer().vkBuffer(),
                            copy.target().offset(),
                            copy.target().length(),
                            VulkanSync.TRANSFER_WRITE,
                            copy.target().buffer().policy().consumerAccess()
                    );
                }
            });
        } finally {
            for (PendingCopy copy : copies) {
                copy.source().close();
            }
        }
    }

    public void tick() {
        this.commit();
        this.allocatedBytesThisSubmit = 0L;
    }

    public long capacity() {
        return this.capacity;
    }

    public static long alignUp(long value, long alignment) {
        if (alignment <= 0L) {
            throw new IllegalArgumentException("Alignment must be greater than zero");
        }
        return ((value + alignment - 1L) / alignment) * alignment;
    }

    public record UploadAllocation(
            GpuBufferSlice.MappedView mappedView,
            ByteBuffer data,
            int size,
            long allocationSize
    ) implements AutoCloseable {
        public long address() {
            return MemoryUtil.memAddress(this.data);
        }

        public GpuBufferSlice slice() {
            return this.mappedView.slice().slice(0L, this.size);
        }

        @Override
        public void close() {
            this.mappedView.close();
        }
    }

    private record PendingCopy(UploadAllocation source, VoxyBufferSlice target) {
        private PendingCopy(UploadAllocation source, GpuBufferSlice target) {
            this(source, new VoxyBufferSlice((VoxyVulkanBuffer) target.buffer(), target.offset(), target.length()));
        }
    }

    private record VoxyBufferSlice(VoxyVulkanBuffer buffer, long offset, long length) {
    }
}
