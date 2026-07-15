package me.cortex.voxy.client.core.vulkan.shader;

import com.mojang.blaze3d.vulkan.Destroyable;
import com.mojang.blaze3d.vulkan.VulkanDevice;
import com.mojang.blaze3d.vulkan.VulkanUtils;
import me.cortex.voxy.client.core.vulkan.VoxyVulkanContext;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VK12;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkComputePipelineCreateInfo;
import org.lwjgl.vulkan.VkPipelineShaderStageCreateInfo;

import java.nio.ByteBuffer;
import java.nio.LongBuffer;
import java.util.List;

/** Raw compute pipeline bridge used where Blaze3D has no compute/storage API. */
public final class VulkanComputePipeline implements AutoCloseable, Destroyable {
    private final VulkanDevice device;
    private final String label;
    private final VulkanShaderModule module;
    private final VulkanPipelineLayout layout;
    private final long pipeline;
    private boolean closed;

    public VulkanComputePipeline(VulkanShaderModule module, String label) {
        if (module.stage() != VulkanShaderStage.COMPUTE) {
            module.close();
            throw new IllegalArgumentException("A compute pipeline requires a compute shader module");
        }
        this.device = VoxyVulkanContext.get().vulkanDevice();
        this.label = label;
        this.module = module;
        VulkanPipelineLayout createdLayout;
        try {
            createdLayout = new VulkanPipelineLayout(List.of(module), label);
        } catch (RuntimeException | Error exception) {
            module.close();
            throw exception;
        }
        this.layout = createdLayout;

        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkPipelineShaderStageCreateInfo stage = VkPipelineShaderStageCreateInfo.calloc(stack).sType$Default()
                    .stage(VK12.VK_SHADER_STAGE_COMPUTE_BIT)
                    .module(module.vkShaderModule())
                    .pName(stack.UTF8("main"));
            VkComputePipelineCreateInfo.Buffer pipelineInfo = VkComputePipelineCreateInfo.calloc(1, stack)
                    .sType$Default()
                    .stage(stage)
                    .layout(this.layout.vkPipelineLayout());
            LongBuffer pointer = stack.callocLong(1);
            int result = VK12.vkCreateComputePipelines(
                    this.device.vkDevice(), VoxyVulkanContext.get().pipelineCache().handle(), pipelineInfo, null, pointer
            );
            if (result != VK12.VK_SUCCESS) {
                this.layout.close();
                this.module.close();
            }
            VulkanUtils.crashIfFailure(this.device, result, "Failed to create Voxy compute pipeline " + label);
            this.pipeline = pointer.get(0);
        }
        this.device.instance().debug().setObjectName(
                this.device.vkDevice(), VK12.VK_OBJECT_TYPE_PIPELINE, this.pipeline, label
        );
    }

    public void bind(VkCommandBuffer commandBuffer, VulkanPushDescriptors descriptors, ByteBuffer pushConstants) {
        if (this.closed) throw new IllegalStateException("Vulkan compute pipeline is closed: " + this.label);
        VK12.vkCmdBindPipeline(commandBuffer, VK12.VK_PIPELINE_BIND_POINT_COMPUTE, this.pipeline);
        descriptors.push(commandBuffer, this.layout, VK12.VK_PIPELINE_BIND_POINT_COMPUTE);
        int requiredPushSize = this.layout.pushConstantSize();
        int suppliedPushSize = pushConstants == null ? 0 : pushConstants.remaining();
        if (suppliedPushSize != requiredPushSize) {
            throw new IllegalArgumentException("Compute pipeline " + this.label + " requires " + requiredPushSize
                    + " push-constant bytes but received " + suppliedPushSize);
        }
        if (requiredPushSize > 0) {
            VK12.vkCmdPushConstants(
                    commandBuffer, this.layout.vkPipelineLayout(), VK12.VK_SHADER_STAGE_COMPUTE_BIT, 0, pushConstants
            );
        }
    }

    public VulkanPipelineLayout layout() {
        return this.layout;
    }

    @Override
    public void close() {
        if (!this.closed) {
            this.closed = true;
            this.device.createCommandEncoder().queueForDestroy(this);
            this.layout.close();
            this.module.close();
        }
    }

    @Override
    public void destroy() {
        VK12.vkDestroyPipeline(this.device.vkDevice(), this.pipeline, null);
    }
}
