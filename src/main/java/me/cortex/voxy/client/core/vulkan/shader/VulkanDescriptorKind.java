package me.cortex.voxy.client.core.vulkan.shader;

import org.lwjgl.util.spvc.Spvc;
import org.lwjgl.vulkan.VK12;

public enum VulkanDescriptorKind {
    STORAGE_BUFFER(0, Spvc.SPVC_RESOURCE_TYPE_STORAGE_BUFFER, VK12.VK_DESCRIPTOR_TYPE_STORAGE_BUFFER),
    UNIFORM_BUFFER(64, Spvc.SPVC_RESOURCE_TYPE_UNIFORM_BUFFER, VK12.VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER),
    COMBINED_IMAGE_SAMPLER(128, Spvc.SPVC_RESOURCE_TYPE_SAMPLED_IMAGE, VK12.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER),
    STORAGE_IMAGE(192, Spvc.SPVC_RESOURCE_TYPE_STORAGE_IMAGE, VK12.VK_DESCRIPTOR_TYPE_STORAGE_IMAGE);

    private static final int ORIGINAL_NAMESPACE_SIZE = 64;

    private final int bindingBase;
    private final int spvcResourceType;
    private final int vkDescriptorType;

    VulkanDescriptorKind(int bindingBase, int spvcResourceType, int vkDescriptorType) {
        this.bindingBase = bindingBase;
        this.spvcResourceType = spvcResourceType;
        this.vkDescriptorType = vkDescriptorType;
    }

    public int mapOriginalBinding(int originalBinding) {
        if (originalBinding < 0 || originalBinding >= ORIGINAL_NAMESPACE_SIZE) {
            throw new IllegalArgumentException("Original " + this + " binding is outside [0, "
                    + ORIGINAL_NAMESPACE_SIZE + "): " + originalBinding);
        }
        return this.bindingBase + originalBinding;
    }

    public int spvcResourceType() {
        return this.spvcResourceType;
    }

    public int vkDescriptorType() {
        return this.vkDescriptorType;
    }
}
