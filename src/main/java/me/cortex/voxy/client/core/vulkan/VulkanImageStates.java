package me.cortex.voxy.client.core.vulkan;

import org.lwjgl.vulkan.VK12;
import org.lwjgl.vulkan.VK13;

/** Canonical image states used by the Voxy Vulkan resource graph. */
public final class VulkanImageStates {
    public static final VulkanSync.ImageState UNDEFINED = new VulkanSync.ImageState(
            VK12.VK_IMAGE_LAYOUT_UNDEFINED,
            VulkanSync.NONE
    );
    public static final VulkanSync.ImageState TRANSFER_DESTINATION = new VulkanSync.ImageState(
            VK12.VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
            VulkanSync.TRANSFER_WRITE
    );
    public static final VulkanSync.ImageState TRANSFER_SOURCE = new VulkanSync.ImageState(
            VK12.VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,
            VulkanSync.TRANSFER_READ
    );
    public static final VulkanSync.ImageState COMPUTE_STORAGE = new VulkanSync.ImageState(
            VK12.VK_IMAGE_LAYOUT_GENERAL,
            VulkanSync.COMPUTE_STORAGE_READ_WRITE
    );
    public static final VulkanSync.ImageState SHADER_SAMPLED = new VulkanSync.ImageState(
            VK12.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL,
            new VulkanSync.Access(
                    VK13.VK_PIPELINE_STAGE_2_COMPUTE_SHADER_BIT
                            | VK13.VK_PIPELINE_STAGE_2_VERTEX_SHADER_BIT
                            | VK13.VK_PIPELINE_STAGE_2_FRAGMENT_SHADER_BIT,
                    VK13.VK_ACCESS_2_SHADER_SAMPLED_READ_BIT
            )
    );
    public static final VulkanSync.ImageState COLOR_ATTACHMENT = new VulkanSync.ImageState(
            VK12.VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL,
            new VulkanSync.Access(
                    VK13.VK_PIPELINE_STAGE_2_COLOR_ATTACHMENT_OUTPUT_BIT,
                    VK13.VK_ACCESS_2_COLOR_ATTACHMENT_READ_BIT | VK13.VK_ACCESS_2_COLOR_ATTACHMENT_WRITE_BIT
            )
    );
    public static final VulkanSync.ImageState DEPTH_STENCIL_ATTACHMENT = new VulkanSync.ImageState(
            VK12.VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL,
            new VulkanSync.Access(
                    VK13.VK_PIPELINE_STAGE_2_EARLY_FRAGMENT_TESTS_BIT | VK13.VK_PIPELINE_STAGE_2_LATE_FRAGMENT_TESTS_BIT,
                    VK13.VK_ACCESS_2_DEPTH_STENCIL_ATTACHMENT_READ_BIT | VK13.VK_ACCESS_2_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT
            )
    );

    private VulkanImageStates() {
    }
}
