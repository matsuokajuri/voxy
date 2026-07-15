package me.cortex.voxy.client.core.vulkan.shader;

import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vulkan.VulkanGpuTextureView;
import me.cortex.voxy.client.core.vulkan.VoxyVulkanBuffer;
import me.cortex.voxy.client.core.vulkan.VoxyVulkanBufferUsage;
import me.cortex.voxy.client.core.vulkan.VoxyVulkanContext;
import me.cortex.voxy.client.core.vulkan.VoxyVulkanImageView;
import me.cortex.voxy.client.core.vulkan.VoxyVulkanSampler;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.KHRPushDescriptor;
import org.lwjgl.vulkan.VK12;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkDescriptorBufferInfo;
import org.lwjgl.vulkan.VkDescriptorImageInfo;
import org.lwjgl.vulkan.VkWriteDescriptorSet;

import java.util.HashMap;
import java.util.Map;

/** One-dispatch descriptor payload validated against reflected shader requirements. */
public final class VulkanPushDescriptors {
    private final Map<Integer, Binding> bindings = new HashMap<>();

    public VulkanPushDescriptors storageBuffer(int originalBinding, VoxyVulkanBuffer buffer, long offset, long size) {
        return this.buffer(VulkanDescriptorKind.STORAGE_BUFFER, originalBinding, buffer, offset, size);
    }

    public VulkanPushDescriptors uniformBuffer(int originalBinding, VoxyVulkanBuffer buffer, long offset, long size) {
        return this.buffer(VulkanDescriptorKind.UNIFORM_BUFFER, originalBinding, buffer, offset, size);
    }

    private VulkanPushDescriptors buffer(
            VulkanDescriptorKind kind,
            int originalBinding,
            VoxyVulkanBuffer buffer,
            long offset,
            long size
    ) {
        if (buffer.isClosed()) throw new IllegalStateException("Cannot bind a closed Vulkan buffer");
        if (offset < 0L || size <= 0L || offset > buffer.size() - size) {
            throw new IllegalArgumentException("Vulkan descriptor buffer range is invalid");
        }
        if (kind == VulkanDescriptorKind.STORAGE_BUFFER) {
            if (!buffer.policy().has(VoxyVulkanBufferUsage.STORAGE_COMPUTE)
                    && !buffer.policy().has(VoxyVulkanBufferUsage.STORAGE_GRAPHICS)) {
                throw new IllegalArgumentException("Storage descriptor requires a storage-buffer allocation policy");
            }
            long alignment = VoxyVulkanContext.get().capabilities().minStorageBufferOffsetAlignment();
            if (offset % alignment != 0L) {
                throw new IllegalArgumentException("Storage-buffer descriptor offset " + offset
                        + " is not aligned to " + alignment);
            }
            if (size > VoxyVulkanContext.get().capabilities().maxStorageBufferRange()) {
                throw new IllegalArgumentException("Storage-buffer descriptor range exceeds the device limit");
            }
        } else if (kind == VulkanDescriptorKind.UNIFORM_BUFFER) {
            if (!buffer.policy().has(VoxyVulkanBufferUsage.UNIFORM)) {
                throw new IllegalArgumentException("Uniform descriptor requires a uniform-buffer allocation policy");
            }
            long alignment = VoxyVulkanContext.get().capabilities().minUniformBufferOffsetAlignment();
            if (offset % alignment != 0L) {
                throw new IllegalArgumentException("Uniform-buffer descriptor offset " + offset
                        + " is not aligned to " + alignment);
            }
            if (size > VoxyVulkanContext.get().capabilities().maxUniformBufferRange()) {
                throw new IllegalArgumentException("Uniform-buffer descriptor range exceeds the device limit");
            }
        }
        this.put(kind.mapOriginalBinding(originalBinding), new BufferBinding(kind, buffer.vkBuffer(), offset, size));
        return this;
    }

    public VulkanPushDescriptors sampledImage(
            int originalBinding,
            VoxyVulkanImageView view,
            VoxyVulkanSampler sampler,
            int imageLayout
    ) {
        return this.sampledImage(originalBinding, (GpuTextureView) view, sampler, imageLayout);
    }

    public VulkanPushDescriptors sampledImage(
            int originalBinding,
            GpuTextureView view,
            VoxyVulkanSampler sampler,
            int imageLayout
    ) {
        if (view.isClosed()) throw new IllegalStateException("Cannot bind a closed Vulkan image view");
        if ((view.texture().usage() & com.mojang.blaze3d.textures.GpuTexture.USAGE_TEXTURE_BINDING) == 0) {
            throw new IllegalArgumentException("Sampled descriptor requires USAGE_TEXTURE_BINDING");
        }
        long imageView = switch (view) {
            case VoxyVulkanImageView voxyView -> voxyView.vkImageView();
            case VulkanGpuTextureView hostView -> hostView.vkImageView();
            default -> throw new IllegalArgumentException(
                    "Sampled descriptor requires a Vulkan image view, got " + view.getClass().getName()
            );
        };
        this.put(VulkanDescriptorKind.COMBINED_IMAGE_SAMPLER.mapOriginalBinding(originalBinding),
                new ImageBinding(VulkanDescriptorKind.COMBINED_IMAGE_SAMPLER, imageView, sampler.vkSampler(), imageLayout));
        return this;
    }

    public VulkanPushDescriptors storageImage(int originalBinding, VoxyVulkanImageView view, int imageLayout) {
        if ((view.image().vkUsage() & VK12.VK_IMAGE_USAGE_STORAGE_BIT) == 0) {
            throw new IllegalArgumentException("Storage-image descriptor requires VK_IMAGE_USAGE_STORAGE_BIT");
        }
        this.put(VulkanDescriptorKind.STORAGE_IMAGE.mapOriginalBinding(originalBinding),
                new ImageBinding(VulkanDescriptorKind.STORAGE_IMAGE, view.vkImageView(), 0L, imageLayout));
        return this;
    }

    private void put(int binding, Binding value) {
        if (this.bindings.putIfAbsent(binding, value) != null) {
            throw new IllegalArgumentException("Vulkan descriptor binding " + binding + " was supplied more than once");
        }
    }

    public void push(VkCommandBuffer commandBuffer, VulkanPipelineLayout layout, int pipelineBindPoint) {
        if (!this.bindings.keySet().equals(layout.descriptors().keySet())) {
            throw new IllegalStateException("Vulkan descriptor payload does not match reflected bindings; required="
                    + layout.descriptors().keySet() + ", supplied=" + this.bindings.keySet());
        }
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkWriteDescriptorSet.Buffer writes = VkWriteDescriptorSet.calloc(this.bindings.size(), stack);
            int index = 0;
            for (Map.Entry<Integer, Binding> entry : this.bindings.entrySet()) {
                VulkanPipelineLayout.DescriptorBinding required = layout.descriptors().get(entry.getKey());
                Binding supplied = entry.getValue();
                if (required.kind() != supplied.kind()) {
                    throw new IllegalStateException("Descriptor kind mismatch at binding " + entry.getKey()
                            + ": required=" + required.kind() + ", supplied=" + supplied.kind());
                }
                VkWriteDescriptorSet write = writes.get(index++)
                        .sType$Default()
                        .dstBinding(entry.getKey())
                        .descriptorType(supplied.kind().vkDescriptorType())
                        .descriptorCount(1);
                if (supplied instanceof BufferBinding buffer) {
                    VkDescriptorBufferInfo.Buffer info = VkDescriptorBufferInfo.calloc(1, stack)
                            .buffer(buffer.buffer())
                            .offset(buffer.offset())
                            .range(buffer.size());
                    write.pBufferInfo(info);
                } else if (supplied instanceof ImageBinding image) {
                    VkDescriptorImageInfo.Buffer info = VkDescriptorImageInfo.calloc(1, stack)
                            .sampler(image.sampler())
                            .imageView(image.imageView())
                            .imageLayout(image.imageLayout());
                    write.pImageInfo(info);
                }
            }
            KHRPushDescriptor.vkCmdPushDescriptorSetKHR(
                    commandBuffer, pipelineBindPoint, layout.vkPipelineLayout(), 0, writes
            );
        }
    }

    private sealed interface Binding permits BufferBinding, ImageBinding {
        VulkanDescriptorKind kind();
    }

    private record BufferBinding(VulkanDescriptorKind kind, long buffer, long offset, long size) implements Binding {
    }

    private record ImageBinding(
            VulkanDescriptorKind kind,
            long imageView,
            long sampler,
            int imageLayout
    ) implements Binding {
    }
}
