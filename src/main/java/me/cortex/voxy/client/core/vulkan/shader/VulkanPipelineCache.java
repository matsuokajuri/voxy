package me.cortex.voxy.client.core.vulkan.shader;

import com.mojang.blaze3d.vulkan.Destroyable;
import com.mojang.blaze3d.vulkan.VulkanDevice;
import com.mojang.blaze3d.vulkan.VulkanUtils;
import me.cortex.voxy.client.core.vulkan.VoxyVulkanContext;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VK12;
import org.lwjgl.vulkan.VkPipelineCacheCreateInfo;

/** Device-scoped cache shared by all Voxy compute and graphics pipeline creation. */
public final class VulkanPipelineCache implements AutoCloseable, Destroyable {
    private final VulkanDevice device;
    private final long handle;
    private boolean closed;

    public VulkanPipelineCache() {
        this.device = VoxyVulkanContext.get().vulkanDevice();
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkPipelineCacheCreateInfo info = VkPipelineCacheCreateInfo.calloc(stack).sType$Default();
            var pointer = stack.callocLong(1);
            int result = VK12.vkCreatePipelineCache(this.device.vkDevice(), info, null, pointer);
            VulkanUtils.crashIfFailure(this.device, result, "Failed to create Voxy Vulkan pipeline cache");
            this.handle = pointer.get(0);
        }
        this.device.instance().debug().setObjectName(
                this.device.vkDevice(), VK12.VK_OBJECT_TYPE_PIPELINE_CACHE, this.handle, "Voxy pipeline cache"
        );
    }

    public long handle() {
        if (this.closed) throw new IllegalStateException("Voxy Vulkan pipeline cache is closed");
        return this.handle;
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
        VK12.vkDestroyPipelineCache(this.device.vkDevice(), this.handle, null);
    }
}
