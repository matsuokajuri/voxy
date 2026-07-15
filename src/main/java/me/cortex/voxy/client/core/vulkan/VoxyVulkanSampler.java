package me.cortex.voxy.client.core.vulkan;

import com.mojang.blaze3d.textures.AddressMode;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.vulkan.Destroyable;
import com.mojang.blaze3d.vulkan.VulkanConst;
import com.mojang.blaze3d.vulkan.VulkanDevice;
import com.mojang.blaze3d.vulkan.VulkanGpuSampler;
import com.mojang.blaze3d.vulkan.VulkanUtils;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VK12;
import org.lwjgl.vulkan.VkSamplerCreateInfo;

import java.nio.LongBuffer;
import java.util.OptionalDouble;

/** Host-owned sampler with a Vulkan handle for Voxy descriptor writes. */
public final class VoxyVulkanSampler implements AutoCloseable, Destroyable {
    private final GpuSampler sampler;
    private final VulkanDevice rawDevice;
    private final long rawSampler;
    private boolean closed;

    public VoxyVulkanSampler(
            AddressMode addressModeU,
            AddressMode addressModeV,
            FilterMode minFilter,
            FilterMode magFilter,
            int maxAnisotropy,
            OptionalDouble maxLod
    ) {
        this.sampler = VoxyVulkanContext.get().vulkanDevice().createSampler(
                addressModeU, addressModeV, minFilter, magFilter, maxAnisotropy, maxLod
        );
        this.rawDevice = null;
        this.rawSampler = 0L;
    }

    private VoxyVulkanSampler(VulkanDevice rawDevice, long rawSampler) {
        this.sampler = null;
        this.rawDevice = rawDevice;
        this.rawSampler = rawSampler;
    }

    /** Exact Vulkan equivalent of GL_NEAREST_MIPMAP_NEAREST used by HiZ. */
    public static VoxyVulkanSampler nearestMipmapNearest(AddressMode addressModeU, AddressMode addressModeV) {
        VulkanDevice device = VoxyVulkanContext.get().vulkanDevice();
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkSamplerCreateInfo createInfo = VkSamplerCreateInfo.calloc(stack).sType$Default()
                    .magFilter(VK12.VK_FILTER_NEAREST)
                    .minFilter(VK12.VK_FILTER_NEAREST)
                    .mipmapMode(VK12.VK_SAMPLER_MIPMAP_MODE_NEAREST)
                    .addressModeU(VulkanConst.toVk(addressModeU))
                    .addressModeV(VulkanConst.toVk(addressModeV))
                    .addressModeW(VK12.VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE)
                    .mipLodBias(0.0f)
                    .anisotropyEnable(false)
                    .compareEnable(false)
                    .minLod(0.0f)
                    .maxLod(1000.0f)
                    .unnormalizedCoordinates(false);
            LongBuffer pointer = stack.callocLong(1);
            VulkanUtils.crashIfFailure(
                    device,
                    VK12.vkCreateSampler(device.vkDevice(), createInfo, null, pointer),
                    "Failed to create Voxy nearest-mipmap-nearest sampler"
            );
            return new VoxyVulkanSampler(device, pointer.get(0));
        }
    }

    public long vkSampler() {
        if (this.closed) {
            throw new IllegalStateException("Voxy Vulkan sampler is closed");
        }
        return this.sampler != null ? ((VulkanGpuSampler) this.sampler).vkSampler() : this.rawSampler;
    }

    public GpuSampler hostSampler() {
        if (this.sampler == null) {
            throw new UnsupportedOperationException("This exact Vulkan sampler has no Blaze3D sampler representation");
        }
        return this.sampler;
    }

    @Override
    public void close() {
        if (!this.closed) {
            this.closed = true;
            if (this.sampler != null) {
                this.sampler.close();
            } else {
                this.rawDevice.createCommandEncoder().queueForDestroy(this);
            }
        }
    }

    @Override
    public void destroy() {
        if (this.rawDevice == null || this.rawSampler == 0L) {
            throw new IllegalStateException("Blaze3D-owned sampler cannot be destroyed through the raw owner");
        }
        VK12.vkDestroySampler(this.rawDevice.vkDevice(), this.rawSampler, null);
    }
}
