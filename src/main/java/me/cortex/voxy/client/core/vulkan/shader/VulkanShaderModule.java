package me.cortex.voxy.client.core.vulkan.shader;

import com.mojang.blaze3d.vulkan.Destroyable;
import com.mojang.blaze3d.vulkan.VulkanDevice;
import com.mojang.blaze3d.vulkan.VulkanUtils;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VK12;
import org.lwjgl.vulkan.VkShaderModuleCreateInfo;

import java.nio.ByteBuffer;
import java.nio.LongBuffer;

public final class VulkanShaderModule implements AutoCloseable, Destroyable {
    private final VulkanDevice device;
    private final String name;
    private final VulkanShaderStage stage;
    private final VulkanShaderReflection reflection;
    private final java.util.List<VulkanGlslPreprocessor.PushConstant> pushConstants;
    private final long vkShaderModule;
    private boolean closed;

    VulkanShaderModule(
            String name,
            VulkanShaderStage stage,
            ByteBuffer spirv,
            VulkanShaderReflection reflection,
            java.util.List<VulkanGlslPreprocessor.PushConstant> pushConstants
    ) {
        this.device = me.cortex.voxy.client.core.vulkan.VoxyVulkanContext.get().vulkanDevice();
        this.name = name;
        this.stage = stage;
        this.reflection = reflection;
        this.pushConstants = java.util.List.copyOf(pushConstants);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkShaderModuleCreateInfo moduleInfo = VkShaderModuleCreateInfo.calloc(stack).sType$Default().pCode(spirv);
            LongBuffer pointer = stack.callocLong(1);
            int result = VK12.vkCreateShaderModule(this.device.vkDevice(), moduleInfo, null, pointer);
            VulkanUtils.crashIfFailure(this.device, result, "Failed to create Voxy shader module " + name);
            this.vkShaderModule = pointer.get(0);
        }
        this.device.instance().debug().setObjectName(
                this.device.vkDevice(), VK12.VK_OBJECT_TYPE_SHADER_MODULE, this.vkShaderModule, name
        );
    }

    public String name() {
        return this.name;
    }

    public VulkanShaderStage stage() {
        return this.stage;
    }

    public VulkanShaderReflection reflection() {
        return this.reflection;
    }

    public java.util.List<VulkanGlslPreprocessor.PushConstant> pushConstants() {
        return this.pushConstants;
    }

    public long vkShaderModule() {
        if (this.closed) throw new IllegalStateException("Vulkan shader module is closed: " + this.name);
        return this.vkShaderModule;
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
        VK12.vkDestroyShaderModule(this.device.vkDevice(), this.vkShaderModule, null);
    }
}
