package me.cortex.voxy.client.core.vulkan;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vulkan.VulkanCommandEncoder;
import com.mojang.blaze3d.vulkan.VulkanUtils;
import org.lwjgl.vulkan.VK12;
import org.lwjgl.vulkan.VkCommandBuffer;

/** Records raw Vulkan commands into Minecraft's current graphics-queue submission. */
public final class VulkanCommandRecorder {
    private VulkanCommandRecorder() {
    }

    public static void record(Command command) {
        RenderSystem.assertOnRenderThread();
        VulkanCommandEncoder encoder = VoxyVulkanContext.get().vulkanDevice().createCommandEncoder();
        VkCommandBuffer commandBuffer = encoder.allocateAndBeginTransientCommandBuffer();
        command.record(commandBuffer);
        VulkanUtils.crashIfFailure(
                VoxyVulkanContext.get().vulkanDevice(),
                VK12.vkEndCommandBuffer(commandBuffer),
                "Failed to finish Voxy Vulkan command buffer"
        );
        encoder.execute(commandBuffer);
    }

    @FunctionalInterface
    public interface Command {
        void record(VkCommandBuffer commandBuffer);
    }
}
