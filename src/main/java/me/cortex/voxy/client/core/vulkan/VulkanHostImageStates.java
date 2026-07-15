package me.cortex.voxy.client.core.vulkan;

import org.lwjgl.vulkan.VK12;
import org.lwjgl.vulkan.VK13;

/** Explicit GENERAL-layout contracts for images owned by Minecraft's Vulkan backend. */
public final class VulkanHostImageStates {
    public static final VulkanSync.ImageState COLOR_ATTACHMENT = new VulkanSync.ImageState(
            VK12.VK_IMAGE_LAYOUT_GENERAL,
            new VulkanSync.Access(
                    VK13.VK_PIPELINE_STAGE_2_TRANSFER_BIT | VK13.VK_PIPELINE_STAGE_2_COLOR_ATTACHMENT_OUTPUT_BIT,
                    VK13.VK_ACCESS_2_TRANSFER_WRITE_BIT
                            | VK13.VK_ACCESS_2_COLOR_ATTACHMENT_READ_BIT
                            | VK13.VK_ACCESS_2_COLOR_ATTACHMENT_WRITE_BIT
            )
    );

    public static final VulkanSync.ImageState DEPTH_ATTACHMENT = new VulkanSync.ImageState(
            VK12.VK_IMAGE_LAYOUT_GENERAL,
            VulkanImageStates.DEPTH_STENCIL_ATTACHMENT.access().or(VulkanSync.TRANSFER_WRITE)
    );

    public static final VulkanSync.ImageState COMPUTE_SAMPLED = new VulkanSync.ImageState(
            VK12.VK_IMAGE_LAYOUT_GENERAL,
            new VulkanSync.Access(
                    VK13.VK_PIPELINE_STAGE_2_COMPUTE_SHADER_BIT,
                    VK13.VK_ACCESS_2_SHADER_SAMPLED_READ_BIT
            )
    );

    private VulkanHostImageStates() {
    }
}
