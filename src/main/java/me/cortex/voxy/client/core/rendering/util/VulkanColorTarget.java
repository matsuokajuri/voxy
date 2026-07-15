package me.cortex.voxy.client.core.rendering.util;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.textures.GpuTexture;
import me.cortex.voxy.client.core.vulkan.VoxyVulkanImage;
import me.cortex.voxy.client.core.vulkan.VoxyVulkanImageView;
import me.cortex.voxy.client.core.vulkan.VulkanImageStates;
import org.lwjgl.vulkan.VK12;
import org.lwjgl.vulkan.VkCommandBuffer;

/** RGBA8 offscreen colour attachment matching NormalRenderPipeline's original colour texture. */
public final class VulkanColorTarget implements AutoCloseable {
    public static final GpuFormat FORMAT = GpuFormat.RGBA8_UNORM;
    private static final int COLOR_ASPECT = VK12.VK_IMAGE_ASPECT_COLOR_BIT;

    private final String label;
    private final int additionalVulkanUsage;
    private VoxyVulkanImage image;
    private VoxyVulkanImageView view;
    private int width;
    private int height;
    private boolean closed;

    public VulkanColorTarget(String label) {
        this(label, 0);
    }

    public VulkanColorTarget(String label, int additionalVulkanUsage) {
        this.label = label;
        this.additionalVulkanUsage = additionalVulkanUsage;
    }

    public void resize(int width, int height) {
        this.ensureOpen();
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Voxy colour target dimensions must be positive");
        }
        if (this.width == width && this.height == height && this.image != null) return;

        VoxyVulkanImage createdImage = null;
        VoxyVulkanImageView createdView = null;
        try {
            createdImage = new VoxyVulkanImage(
                    this.label,
                    GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_RENDER_ATTACHMENT,
                    this.additionalVulkanUsage,
                    FORMAT,
                    width,
                    height,
                    1,
                    1
            );
            createdView = createdImage.createView(0, 1, COLOR_ASPECT);
        } catch (RuntimeException | Error exception) {
            if (createdView != null) createdView.close();
            if (createdImage != null) createdImage.close();
            throw exception;
        }

        this.releaseImage();
        this.image = createdImage;
        this.view = createdView;
        this.width = width;
        this.height = height;
    }

    public void transitionToAttachment(VkCommandBuffer commandBuffer) {
        this.ensureAllocated();
        this.image.transitionAndSynchronize(commandBuffer, 0, 1, 0, 1, VulkanImageStates.COLOR_ATTACHMENT);
    }

    public void transitionToSampled(VkCommandBuffer commandBuffer) {
        this.ensureAllocated();
        this.image.transition(commandBuffer, 0, 1, 0, 1, VulkanImageStates.SHADER_SAMPLED);
    }

    public void transitionToComputeStorage(VkCommandBuffer commandBuffer) {
        this.ensureAllocated();
        if ((this.image.vkUsage() & VK12.VK_IMAGE_USAGE_STORAGE_BIT) == 0) {
            throw new IllegalStateException("Voxy colour target was not allocated for storage-image access");
        }
        this.image.transition(commandBuffer, 0, 1, 0, 1, VulkanImageStates.COMPUTE_STORAGE);
    }

    public VoxyVulkanImageView view() {
        this.ensureAllocated();
        return this.view;
    }

    public int width() {
        return this.width;
    }

    public int height() {
        return this.height;
    }

    private void ensureAllocated() {
        this.ensureOpen();
        if (this.image == null || this.view == null) {
            throw new IllegalStateException("Voxy colour target is not allocated");
        }
    }

    private void ensureOpen() {
        if (this.closed) throw new IllegalStateException("Voxy colour target is closed");
    }

    private void releaseImage() {
        if (this.view != null) {
            this.view.close();
            this.view = null;
        }
        if (this.image != null) {
            this.image.close();
            this.image = null;
        }
    }

    @Override
    public void close() {
        if (this.closed) return;
        this.closed = true;
        this.releaseImage();
    }
}
