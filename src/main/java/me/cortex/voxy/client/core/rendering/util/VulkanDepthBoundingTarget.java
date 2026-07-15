package me.cortex.voxy.client.core.rendering.util;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.textures.GpuTexture;
import me.cortex.voxy.client.core.vulkan.VoxyVulkanImage;
import me.cortex.voxy.client.core.vulkan.VoxyVulkanImageView;
import me.cortex.voxy.client.core.vulkan.VulkanCommandRecorder;
import me.cortex.voxy.client.core.vulkan.VulkanImageStates;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.KHRDynamicRendering;
import org.lwjgl.vulkan.VK12;
import org.lwjgl.vulkan.VkClearValue;
import org.lwjgl.vulkan.VkRenderingAttachmentInfo;
import org.lwjgl.vulkan.VkRenderingInfo;

/** Depth24 viewport bound target consumed by the terrain fragment depth cutout. */
public final class VulkanDepthBoundingTarget implements AutoCloseable {
    public static final GpuFormat FORMAT = GpuFormat.D24_UNORM_S8_UINT;
    private static final int DEPTH_ASPECT = VK12.VK_IMAGE_ASPECT_DEPTH_BIT;

    private VoxyVulkanImage image;
    private VoxyVulkanImageView depthView;
    private int width;
    private int height;
    private boolean closed;

    /** Matches Viewport.update(): resize and initialize only when the dimensions change. */
    public void resize(int width, int height, float inverseClearDepth) {
        this.ensureOpen();
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Voxy depth-bounding dimensions must be positive");
        }
        if (this.width == width && this.height == height && this.image != null) return;

        VoxyVulkanImage createdImage = null;
        VoxyVulkanImageView createdView = null;
        try {
            createdImage = new VoxyVulkanImage(
                    "Voxy viewport depth bounds",
                    GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_RENDER_ATTACHMENT,
                    0,
                    FORMAT,
                    width,
                    height,
                    1,
                    1
            );
            createdView = createdImage.createView(0, 1, DEPTH_ASPECT);
            recordClear(createdImage, createdView, width, height, inverseClearDepth);
        } catch (RuntimeException | Error exception) {
            if (createdView != null) createdView.close();
            if (createdImage != null) createdImage.close();
            throw exception;
        }

        this.releaseImage();
        this.image = createdImage;
        this.depthView = createdView;
        this.width = width;
        this.height = height;
    }

    /** Matches BoundRenderer's per-frame depth clear before it writes chunk bounds. */
    public void clear(float depth) {
        this.ensureAllocated();
        recordClear(this.image, this.depthView, this.width, this.height, depth);
    }

    private static void recordClear(
            VoxyVulkanImage image,
            VoxyVulkanImageView depthView,
            int width,
            int height,
            float depth
    ) {
        VulkanCommandRecorder.record(commandBuffer -> {
            image.transition(commandBuffer, 0, 1, 0, 1, VulkanImageStates.DEPTH_STENCIL_ATTACHMENT);
            try (MemoryStack stack = MemoryStack.stackPush()) {
                VkClearValue clear = VkClearValue.calloc(stack);
                clear.depthStencil().depth(depth).stencil(0);
                VkRenderingAttachmentInfo depthAttachment = VkRenderingAttachmentInfo.calloc(stack)
                        .sType$Default()
                        .imageView(depthView.vkImageView())
                        .imageLayout(VulkanImageStates.DEPTH_STENCIL_ATTACHMENT.layout())
                        .loadOp(VK12.VK_ATTACHMENT_LOAD_OP_CLEAR)
                        .storeOp(VK12.VK_ATTACHMENT_STORE_OP_STORE)
                        .clearValue(clear);
                VkRenderingInfo rendering = VkRenderingInfo.calloc(stack)
                        .sType$Default()
                        .layerCount(1)
                        .viewMask(0)
                        .pDepthAttachment(depthAttachment);
                rendering.renderArea().offset().set(0, 0);
                rendering.renderArea().extent().set(width, height);
                KHRDynamicRendering.vkCmdBeginRenderingKHR(commandBuffer, rendering);
                KHRDynamicRendering.vkCmdEndRenderingKHR(commandBuffer);
            }
            image.transition(commandBuffer, 0, 1, 0, 1, VulkanImageStates.SHADER_SAMPLED);
        });
    }

    public VoxyVulkanImageView sampledView() {
        this.ensureAllocated();
        return this.depthView;
    }

    public VoxyVulkanImage image() {
        this.ensureAllocated();
        return this.image;
    }

    public int width() {
        return this.width;
    }

    public int height() {
        return this.height;
    }

    private void ensureAllocated() {
        this.ensureOpen();
        if (this.image == null || this.depthView == null) {
            throw new IllegalStateException("Voxy depth-bounding target is not allocated");
        }
    }

    private void ensureOpen() {
        if (this.closed) throw new IllegalStateException("Voxy depth-bounding target is closed");
    }

    private void releaseImage() {
        if (this.depthView != null) {
            this.depthView.close();
            this.depthView = null;
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
