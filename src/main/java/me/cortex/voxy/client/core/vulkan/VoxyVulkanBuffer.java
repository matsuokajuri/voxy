package me.cortex.voxy.client.core.vulkan;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.vulkan.VulkanConst;
import com.mojang.blaze3d.vulkan.VulkanDevice;
import com.mojang.blaze3d.vulkan.VulkanGpuBuffer;
import com.mojang.blaze3d.vulkan.VulkanUtils;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.vma.Vma;
import org.lwjgl.util.vma.VmaAllocationCreateInfo;
import org.lwjgl.vulkan.VK12;
import org.lwjgl.vulkan.VkBufferCreateInfo;

import java.nio.LongBuffer;

/**
 * Device-local Vulkan equivalent of the original unmapped {@code GlBuffer} owner.
 * The allocation belongs to Minecraft's VkDevice/VMA and is retired through its command encoder.
 */
public final class VoxyVulkanBuffer extends VulkanGpuBuffer {
    private static int count;
    private static long totalSize;

    private final VulkanDevice device;
    private final long vmaAllocation;
    private final VoxyVulkanBufferUsage policy;
    private final String label;
    private boolean closed;
    private boolean destroyed;

    public static VoxyVulkanBuffer createStorage(long size, @GpuBuffer.Usage int usage, String label) {
        // createStorage follows create()'s zero-initialized contract, so transfer-destination usage
        // is mandatory even when the caller did not request later copy uploads.
        int roles = VoxyVulkanBufferUsage.STORAGE_COMPUTE
                | VoxyVulkanBufferUsage.STORAGE_GRAPHICS
                | VoxyVulkanBufferUsage.TRANSFER_DESTINATION;
        if ((usage & GpuBuffer.USAGE_COPY_SRC) != 0) roles |= VoxyVulkanBufferUsage.TRANSFER_SOURCE;
        if ((usage & GpuBuffer.USAGE_COPY_DST) != 0) roles |= VoxyVulkanBufferUsage.TRANSFER_DESTINATION;
        if ((usage & GpuBuffer.USAGE_INDIRECT_PARAMETERS) != 0) roles |= VoxyVulkanBufferUsage.INDIRECT;
        if ((usage & GpuBuffer.USAGE_VERTEX) != 0) roles |= VoxyVulkanBufferUsage.VERTEX;
        if ((usage & GpuBuffer.USAGE_INDEX) != 0) roles |= VoxyVulkanBufferUsage.INDEX;
        if ((usage & GpuBuffer.USAGE_UNIFORM) != 0) roles |= VoxyVulkanBufferUsage.UNIFORM;
        return create(size, VoxyVulkanBufferUsage.of(roles), label);
    }

    public static VoxyVulkanBuffer create(long size, VoxyVulkanBufferUsage policy, String label) {
        if (!policy.has(VoxyVulkanBufferUsage.TRANSFER_DESTINATION)) {
            throw new IllegalArgumentException("A zero-initialized Voxy Vulkan buffer requires TRANSFER_DESTINATION");
        }
        VoxyVulkanBuffer buffer = createUninitialized(size, policy, label);
        try {
            return buffer.zero();
        } catch (RuntimeException | Error exception) {
            buffer.close();
            throw exception;
        }
    }

    public static VoxyVulkanBuffer createUninitialized(long size, VoxyVulkanBufferUsage policy, String label) {
        VulkanDevice device = VoxyVulkanContext.get().vulkanDevice();
        Allocation allocation = allocate(device, size, policy);
        return new VoxyVulkanBuffer(device, size, policy, label, allocation);
    }

    private VoxyVulkanBuffer(
            VulkanDevice device,
            long size,
            VoxyVulkanBufferUsage policy,
            String label,
            Allocation allocation
    ) {
        super(allocation.vkBuffer(), policy.minecraftUsage(), size);
        this.device = device;
        this.vmaAllocation = allocation.vmaAllocation();
        this.policy = policy;
        this.label = label == null ? "VoxyBuffer" : label;
        if (label != null && !label.isBlank()) {
            device.instance().debug().setObjectName(device.vkDevice(), VK12.VK_OBJECT_TYPE_BUFFER, this.vkBuffer(), label);
        }
        count++;
        totalSize += size;
    }

    private static Allocation allocate(VulkanDevice device, long size, VoxyVulkanBufferUsage policy) {
        if (size <= 0L) {
            throw new IllegalArgumentException("Vulkan buffer size must be greater than zero");
        }

        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkBufferCreateInfo bufferInfo = VkBufferCreateInfo.calloc(stack).sType$Default();
            bufferInfo.size(size);
            bufferInfo.usage(VulkanConst.bufferUsageToVk(policy.minecraftUsage()) | policy.additionalVulkanUsage());
            bufferInfo.sharingMode(VK12.VK_SHARING_MODE_EXCLUSIVE);

            VmaAllocationCreateInfo allocationInfo = VmaAllocationCreateInfo.calloc(stack);
            allocationInfo.usage(Vma.VMA_MEMORY_USAGE_AUTO_PREFER_DEVICE);

            LongBuffer bufferPointer = stack.callocLong(1);
            PointerBuffer allocationPointer = stack.callocPointer(1);
            int result = Vma.vmaCreateBuffer(device.vma(), bufferInfo, allocationInfo, bufferPointer, allocationPointer, null);
            VulkanUtils.crashIfFailure(device, result, "Failed to allocate Voxy Vulkan buffer");
            return new Allocation(bufferPointer.get(0), allocationPointer.get(0));
        }
    }

    @Override
    public boolean isClosed() {
        return this.closed;
    }

    @Override
    public void close() {
        if (!this.closed) {
            this.closed = true;
            this.device.createCommandEncoder().queueForDestroy(this);
        }
    }

    @Override
    public void destroy() {
        if (this.destroyed) {
            throw new IllegalStateException("Voxy Vulkan buffer was destroyed more than once");
        }
        this.destroyed = true;
        Vma.vmaDestroyBuffer(this.device.vma(), this.vkBuffer(), this.vmaAllocation);
        count--;
        totalSize -= this.size();
    }

    public VoxyVulkanBuffer zero() {
        return this.fill(0L, this.size(), 0);
    }

    public VoxyVulkanBuffer zeroRange(long offset, long size) {
        return this.fill(offset, size, 0);
    }

    public VoxyVulkanBuffer fill(int value) {
        return this.fill(0L, this.size(), value);
    }

    public VoxyVulkanBuffer fill(long offset, long size, int value) {
        if (this.closed) {
            throw new IllegalStateException("Cannot fill a closed Vulkan buffer");
        }
        if (!this.policy.has(VoxyVulkanBufferUsage.TRANSFER_DESTINATION)) {
            throw new IllegalStateException("Buffer " + this.label + " was not created as a transfer destination");
        }
        VulkanRanges.check(offset, size, this.size(), "Buffer fill");
        VulkanRanges.checkAlignment(offset, 4L, "Buffer fill offset");
        VulkanRanges.checkAlignment(size, 4L, "Buffer fill size");
        if (size == 0L) {
            return this;
        }

        VulkanCommandRecorder.record(commandBuffer -> {
            VulkanSync.bufferBarrier(
                    commandBuffer,
                    this.vkBuffer(),
                    offset,
                    size,
                    this.policy.declaredAccess(),
                    VulkanSync.TRANSFER_WRITE
            );
            VK12.vkCmdFillBuffer(commandBuffer, this.vkBuffer(), offset, size, value);
            VulkanSync.bufferBarrier(
                    commandBuffer,
                    this.vkBuffer(),
                    offset,
                    size,
                    VulkanSync.TRANSFER_WRITE,
                    this.policy.consumerAccess()
            );
        });
        return this;
    }

    @Override
    public GpuBufferSlice.MappedView map(long offset, long length, boolean read, boolean write) {
        throw new UnsupportedOperationException("Device-local Voxy Vulkan buffers are not host mapped; use the upload/download streams");
    }

    public VoxyVulkanBufferUsage policy() {
        return this.policy;
    }

    public static int getCount() {
        return count;
    }

    public static long getTotalSize() {
        return totalSize;
    }

    private record Allocation(long vkBuffer, long vmaAllocation) {
    }
}
