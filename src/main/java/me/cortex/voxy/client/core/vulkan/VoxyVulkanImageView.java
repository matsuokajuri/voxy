package me.cortex.voxy.client.core.vulkan;

import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vulkan.Destroyable;
import com.mojang.blaze3d.vulkan.VulkanConst;
import com.mojang.blaze3d.vulkan.VulkanDevice;
import com.mojang.blaze3d.vulkan.VulkanUtils;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VK12;
import org.lwjgl.vulkan.VkImageViewCreateInfo;

import java.nio.LongBuffer;

public final class VoxyVulkanImageView extends GpuTextureView implements Destroyable {
    private final VulkanDevice device;
    private final VoxyVulkanImage image;
    private final int mipLevelCount;
    private final int baseArrayLayer;
    private final int layerCount;
    private final int aspectMask;
    private final long vkImageView;
    private boolean closed;

    VoxyVulkanImageView(
            VoxyVulkanImage image,
            int baseMipLevel,
            int mipLevels,
            int baseArrayLayer,
            int layerCount
    ) {
        this(
                image, baseMipLevel, mipLevels, baseArrayLayer, layerCount,
                VulkanConst.formatAspectMask(image.getFormat())
        );
    }

    VoxyVulkanImageView(
            VoxyVulkanImage image,
            int baseMipLevel,
            int mipLevels,
            int baseArrayLayer,
            int layerCount,
            int aspectMask
    ) {
        super(image, baseMipLevel, mipLevels);
        if (baseMipLevel < 0 || mipLevels <= 0 || baseMipLevel > image.getMipLevels() - mipLevels
                || baseArrayLayer < 0 || layerCount <= 0
                || baseArrayLayer > image.getDepthOrLayers() - layerCount) {
            throw new IllegalArgumentException("Vulkan image view exceeds the image subresources");
        }
        this.device = VoxyVulkanContext.get().vulkanDevice();
        this.image = image;
        this.mipLevelCount = mipLevels;
        this.baseArrayLayer = baseArrayLayer;
        this.layerCount = layerCount;
        this.aspectMask = aspectMask;
        int formatAspects = VulkanConst.formatAspectMask(image.getFormat());
        if (aspectMask == 0 || (aspectMask & ~formatAspects) != 0) {
            throw new IllegalArgumentException("Vulkan image view aspect mask is incompatible with its format");
        }
        if (image.isClosed()) {
            throw new IllegalStateException("Cannot create a view of a closed Vulkan image");
        }

        long createdImageView;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkImageViewCreateInfo viewInfo = VkImageViewCreateInfo.calloc(stack).sType$Default();
            viewInfo.image(image.vkImage());
            if ((image.usage() & VoxyVulkanImage.USAGE_CUBEMAP_COMPATIBLE) != 0) {
                viewInfo.viewType(VK12.VK_IMAGE_VIEW_TYPE_CUBE);
            } else if (layerCount > 1) {
                viewInfo.viewType(VK12.VK_IMAGE_VIEW_TYPE_2D_ARRAY);
            } else {
                viewInfo.viewType(VK12.VK_IMAGE_VIEW_TYPE_2D);
            }
            viewInfo.format(VulkanConst.toVk(image.getFormat()));
            viewInfo.subresourceRange()
                    .aspectMask(aspectMask)
                    .baseMipLevel(baseMipLevel)
                    .levelCount(mipLevels)
                    .baseArrayLayer(baseArrayLayer)
                    .layerCount(layerCount);
            LongBuffer pointer = stack.callocLong(1);
            int result = VK12.vkCreateImageView(this.device.vkDevice(), viewInfo, null, pointer);
            VulkanUtils.crashIfFailure(this.device, result, "Failed to create Voxy Vulkan image view");
            createdImageView = pointer.get(0);
        }
        boolean registered = false;
        try {
            image.addView();
            registered = true;
            this.device.instance().debug().setObjectName(
                    this.device.vkDevice(), VK12.VK_OBJECT_TYPE_IMAGE_VIEW, createdImageView, image.getLabel() + " view"
            );
        } catch (RuntimeException | Error exception) {
            VK12.vkDestroyImageView(this.device.vkDevice(), createdImageView, null);
            if (registered) image.removeView();
            throw exception;
        }
        this.vkImageView = createdImageView;
    }

    @Override
    public void close() {
        if (!this.closed) {
            this.closed = true;
            this.device.createCommandEncoder().queueForDestroy(this);
            this.image.removeView();
        }
    }

    @Override
    public boolean isClosed() {
        return this.closed;
    }

    @Override
    public void destroy() {
        VK12.vkDestroyImageView(this.device.vkDevice(), this.vkImageView, null);
    }

    public long vkImageView() {
        if (this.closed) throw new IllegalStateException("Voxy Vulkan image view is closed");
        return this.vkImageView;
    }

    public VoxyVulkanImage image() {
        return this.image;
    }

    public int baseArrayLayer() {
        return this.baseArrayLayer;
    }

    public int mipLevelCount() {
        return this.mipLevelCount;
    }

    public int layerCount() {
        return this.layerCount;
    }

    public int aspectMask() {
        return this.aspectMask;
    }
}
