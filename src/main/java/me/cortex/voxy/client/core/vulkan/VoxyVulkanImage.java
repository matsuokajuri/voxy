package me.cortex.voxy.client.core.vulkan;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.vulkan.Destroyable;
import com.mojang.blaze3d.vulkan.VulkanConst;
import com.mojang.blaze3d.vulkan.VulkanDevice;
import com.mojang.blaze3d.vulkan.VulkanUtils;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.vma.Vma;
import org.lwjgl.util.vma.VmaAllocationCreateInfo;
import org.lwjgl.vulkan.VK12;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkClearColorValue;
import org.lwjgl.vulkan.VkImageCreateInfo;
import org.lwjgl.vulkan.VkImageSubresourceRange;

import java.nio.LongBuffer;
import java.util.Arrays;

/** VMA-backed image for Voxy usages that Blaze3D cannot express, notably storage images. */
public final class VoxyVulkanImage extends GpuTexture implements Destroyable {
    private final VulkanDevice device;
    private final long vkImage;
    private final long vmaAllocation;
    private final int vkUsage;
    private final VulkanSync.ImageState[] subresourceStates;
    private int views;
    private boolean closed;
    private boolean destroyed;

    public VoxyVulkanImage(
            String label,
            @GpuTexture.Usage int usage,
            int additionalVulkanUsage,
            GpuFormat format,
            int width,
            int height,
            int layers,
            int mipLevels
    ) {
        super(usage, label == null ? "VoxyImage" : label, format, width, height, layers, mipLevels);
        if (width <= 0 || height <= 0 || layers <= 0 || mipLevels <= 0) {
            throw new IllegalArgumentException("Vulkan image dimensions, layers and mip levels must be positive");
        }
        this.device = VoxyVulkanContext.get().vulkanDevice();
        this.vkUsage = VulkanConst.textureUsageToVk(usage, format) | additionalVulkanUsage;
        this.subresourceStates = new VulkanSync.ImageState[Math.multiplyExact(layers, mipLevels)];
        Arrays.fill(this.subresourceStates, VulkanImageStates.UNDEFINED);

        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkImageCreateInfo imageInfo = VkImageCreateInfo.calloc(stack).sType$Default();
            imageInfo.imageType(VK12.VK_IMAGE_TYPE_2D);
            imageInfo.extent().set(width, height, 1);
            imageInfo.mipLevels(mipLevels);
            imageInfo.arrayLayers(layers);
            imageInfo.format(VulkanConst.toVk(format));
            imageInfo.tiling(VK12.VK_IMAGE_TILING_OPTIMAL);
            imageInfo.initialLayout(VK12.VK_IMAGE_LAYOUT_UNDEFINED);
            imageInfo.usage(this.vkUsage);
            imageInfo.sharingMode(VK12.VK_SHARING_MODE_EXCLUSIVE);
            imageInfo.samples(VK12.VK_SAMPLE_COUNT_1_BIT);
            imageInfo.flags((usage & GpuTexture.USAGE_CUBEMAP_COMPATIBLE) != 0
                    ? VK12.VK_IMAGE_CREATE_CUBE_COMPATIBLE_BIT : 0);

            VmaAllocationCreateInfo allocationInfo = VmaAllocationCreateInfo.calloc(stack);
            allocationInfo.usage(Vma.VMA_MEMORY_USAGE_AUTO_PREFER_DEVICE);
            LongBuffer imagePointer = stack.callocLong(1);
            PointerBuffer allocationPointer = stack.callocPointer(1);
            int result = Vma.vmaCreateImage(
                    this.device.vma(), imageInfo, allocationInfo, imagePointer, allocationPointer, null
            );
            VulkanUtils.crashIfFailure(this.device, result, "Failed to allocate Voxy Vulkan image");
            this.vkImage = imagePointer.get(0);
            this.vmaAllocation = allocationPointer.get(0);
        }

        if (!this.getLabel().isBlank()) {
            this.device.instance().debug().setObjectName(
                    this.device.vkDevice(), VK12.VK_OBJECT_TYPE_IMAGE, this.vkImage, this.getLabel()
            );
        }
    }

    public void transition(
            VkCommandBuffer commandBuffer,
            int baseMipLevel,
            int levelCount,
            int baseArrayLayer,
            int layerCount,
            VulkanSync.ImageState destination
    ) {
        this.transition(commandBuffer, baseMipLevel, levelCount, baseArrayLayer, layerCount, destination, false);
    }

    /**
     * Emits an image dependency even when the tracked layout, stage, and access state are unchanged.
     * Consecutive dynamic-rendering passes still require ordering for attachment loads and
     * write-after-read/write-after-write hazards.
     */
    public void transitionAndSynchronize(
            VkCommandBuffer commandBuffer,
            int baseMipLevel,
            int levelCount,
            int baseArrayLayer,
            int layerCount,
            VulkanSync.ImageState destination
    ) {
        this.transition(commandBuffer, baseMipLevel, levelCount, baseArrayLayer, layerCount, destination, true);
    }

    private void transition(
            VkCommandBuffer commandBuffer,
            int baseMipLevel,
            int levelCount,
            int baseArrayLayer,
            int layerCount,
            VulkanSync.ImageState destination,
            boolean forceDependency
    ) {
        this.checkSubresources(baseMipLevel, levelCount, baseArrayLayer, layerCount);
        for (int layer = baseArrayLayer; layer < baseArrayLayer + layerCount; layer++) {
            for (int mip = baseMipLevel; mip < baseMipLevel + levelCount; mip++) {
                int index = this.stateIndex(mip, layer);
                VulkanSync.ImageState source = this.subresourceStates[index];
                if (forceDependency || !source.equals(destination)) {
                    VulkanSync.imageBarrier(
                            commandBuffer,
                            this.vkImage,
                            VulkanConst.formatAspectMask(this.getFormat()),
                            mip,
                            1,
                            layer,
                            1,
                            source,
                            destination
                    );
                    this.subresourceStates[index] = destination;
                }
            }
        }
    }

    public VulkanSync.ImageState state(int mipLevel, int arrayLayer) {
        this.checkSubresources(mipLevel, 1, arrayLayer, 1);
        return this.subresourceStates[this.stateIndex(mipLevel, arrayLayer)];
    }

    public VoxyVulkanImageView createView(int baseMipLevel, int levelCount) {
        return new VoxyVulkanImageView(this, baseMipLevel, levelCount, 0, this.getDepthOrLayers());
    }

    public VoxyVulkanImageView createView(int baseMipLevel, int levelCount, int aspectMask) {
        return new VoxyVulkanImageView(
                this, baseMipLevel, levelCount, 0, this.getDepthOrLayers(), aspectMask
        );
    }

    public VoxyVulkanImageView createView(
            int baseMipLevel,
            int levelCount,
            int baseArrayLayer,
            int layerCount
    ) {
        return new VoxyVulkanImageView(this, baseMipLevel, levelCount, baseArrayLayer, layerCount);
    }

    /** GPU-side equivalent of the original model-atlas zero initialization/reuse clear. */
    public VoxyVulkanImage clearToZero(VulkanSync.ImageState finalState) {
        if (this.closed) throw new IllegalStateException("Cannot clear a closed Vulkan image");
        if ((this.usage() & GpuTexture.USAGE_COPY_DST) == 0) {
            throw new IllegalStateException("Vulkan image clear requires USAGE_COPY_DST");
        }
        VulkanCommandRecorder.record(commandBuffer -> {
            this.transition(commandBuffer, 0, this.getMipLevels(), 0, this.getDepthOrLayers(),
                    VulkanImageStates.TRANSFER_DESTINATION);
            try (MemoryStack stack = MemoryStack.stackPush()) {
                VkClearColorValue clear = VkClearColorValue.calloc(stack);
                clear.uint32(0, 0).uint32(1, 0).uint32(2, 0).uint32(3, 0);
                VkImageSubresourceRange.Buffer range = VkImageSubresourceRange.calloc(1, stack)
                        .aspectMask(VulkanConst.formatAspectMask(this.getFormat()))
                        .baseMipLevel(0)
                        .levelCount(this.getMipLevels())
                        .baseArrayLayer(0)
                        .layerCount(this.getDepthOrLayers());
                VK12.vkCmdClearColorImage(
                        commandBuffer, this.vkImage, VK12.VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, clear, range
                );
            }
            this.transition(commandBuffer, 0, this.getMipLevels(), 0, this.getDepthOrLayers(), finalState);
        });
        return this;
    }

    void addView() {
        if (this.closed) {
            throw new IllegalStateException("Cannot create a view of a closed Vulkan image");
        }
        this.views++;
    }

    void removeView() {
        this.views--;
        if (this.views < 0) {
            throw new IllegalStateException("Voxy Vulkan image view count underflow");
        }
        this.queueDestroyIfUnused();
    }

    @Override
    public void close() {
        if (!this.closed) {
            this.closed = true;
            this.queueDestroyIfUnused();
        }
    }

    private void queueDestroyIfUnused() {
        if (this.closed && this.views == 0 && !this.destroyed) {
            this.device.createCommandEncoder().queueForDestroy(this);
            this.destroyed = true;
        }
    }

    @Override
    public boolean isClosed() {
        return this.closed;
    }

    @Override
    public void destroy() {
        Vma.vmaDestroyImage(this.device.vma(), this.vkImage, this.vmaAllocation);
    }

    public long vkImage() {
        return this.vkImage;
    }

    public int vkUsage() {
        return this.vkUsage;
    }

    private int stateIndex(int mipLevel, int arrayLayer) {
        return arrayLayer * this.getMipLevels() + mipLevel;
    }

    private void checkSubresources(int baseMipLevel, int levelCount, int baseArrayLayer, int layerCount) {
        if (baseMipLevel < 0 || levelCount <= 0 || baseMipLevel > this.getMipLevels()
                || levelCount > this.getMipLevels() - baseMipLevel) {
            throw new IllegalArgumentException("Vulkan image mip range is invalid");
        }
        if (baseArrayLayer < 0 || layerCount <= 0 || baseArrayLayer > this.getDepthOrLayers()
                || layerCount > this.getDepthOrLayers() - baseArrayLayer) {
            throw new IllegalArgumentException("Vulkan image layer range is invalid");
        }
    }
}
