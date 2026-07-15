package me.cortex.voxy.client.core.vulkan;

import com.mojang.blaze3d.buffers.GpuBuffer;
import org.lwjgl.vulkan.VK12;

/** Declares every role a Voxy buffer has so allocation and barriers derive from one contract. */
public final class VoxyVulkanBufferUsage {
    public static final int STORAGE_COMPUTE = 1;
    public static final int STORAGE_GRAPHICS = 1 << 1;
    public static final int INDIRECT = 1 << 2;
    public static final int VERTEX = 1 << 3;
    public static final int INDEX = 1 << 4;
    public static final int UNIFORM = 1 << 5;
    public static final int TRANSFER_SOURCE = 1 << 6;
    public static final int TRANSFER_DESTINATION = 1 << 7;

    private final int roles;
    private final int minecraftUsage;
    private final int additionalVulkanUsage;
    private final VulkanSync.Access consumerAccess;
    private final VulkanSync.Access declaredAccess;
    private final int requiredAlignment;

    private VoxyVulkanBufferUsage(
            int roles,
            int minecraftUsage,
            int additionalVulkanUsage,
            VulkanSync.Access consumerAccess,
            VulkanSync.Access declaredAccess,
            int requiredAlignment
    ) {
        this.roles = roles;
        this.minecraftUsage = minecraftUsage;
        this.additionalVulkanUsage = additionalVulkanUsage;
        this.consumerAccess = consumerAccess;
        this.declaredAccess = declaredAccess;
        this.requiredAlignment = requiredAlignment;
    }

    public static VoxyVulkanBufferUsage of(int roles) {
        if (roles == 0) {
            throw new IllegalArgumentException("A Voxy Vulkan buffer must have at least one role");
        }

        int minecraftUsage = 0;
        int vulkanUsage = 0;
        int alignment = 4;
        VulkanSync.Access consumers = VulkanSync.NONE;

        if ((roles & STORAGE_COMPUTE) != 0) {
            vulkanUsage |= VK12.VK_BUFFER_USAGE_STORAGE_BUFFER_BIT;
            consumers = consumers.or(VulkanSync.COMPUTE_STORAGE_READ_WRITE);
            alignment = Math.max(alignment, storageAlignment());
        }
        if ((roles & STORAGE_GRAPHICS) != 0) {
            vulkanUsage |= VK12.VK_BUFFER_USAGE_STORAGE_BUFFER_BIT;
            consumers = consumers.or(VulkanSync.GRAPHICS_STORAGE_READ_WRITE);
            alignment = Math.max(alignment, storageAlignment());
        }
        if ((roles & INDIRECT) != 0) {
            minecraftUsage |= GpuBuffer.USAGE_INDIRECT_PARAMETERS;
            consumers = consumers.or(VulkanSync.INDIRECT_READ);
            alignment = Math.max(alignment, 4);
        }
        if ((roles & VERTEX) != 0) {
            minecraftUsage |= GpuBuffer.USAGE_VERTEX;
            consumers = consumers.or(VulkanSync.VERTEX_READ);
        }
        if ((roles & INDEX) != 0) {
            minecraftUsage |= GpuBuffer.USAGE_INDEX;
            consumers = consumers.or(VulkanSync.INDEX_READ);
        }
        if ((roles & UNIFORM) != 0) {
            minecraftUsage |= GpuBuffer.USAGE_UNIFORM;
            consumers = consumers.or(VulkanSync.SHADER_UNIFORM_READ);
            long uniformAlignment = Math.max(16L,
                    VoxyVulkanContext.get().capabilities().minUniformBufferOffsetAlignment());
            if (uniformAlignment > Integer.MAX_VALUE) {
                throw new IllegalStateException("Vulkan uniform-buffer alignment exceeds Java addressable range: "
                        + uniformAlignment);
            }
            alignment = Math.max(alignment, (int) uniformAlignment);
        }
        if ((roles & TRANSFER_SOURCE) != 0) {
            minecraftUsage |= GpuBuffer.USAGE_COPY_SRC;
        }
        if ((roles & TRANSFER_DESTINATION) != 0) {
            minecraftUsage |= GpuBuffer.USAGE_COPY_DST;
        }

        VulkanSync.Access declared = consumers;
        if ((roles & TRANSFER_SOURCE) != 0) {
            declared = declared.or(VulkanSync.TRANSFER_READ);
        }
        if ((roles & TRANSFER_DESTINATION) != 0) {
            declared = declared.or(VulkanSync.TRANSFER_WRITE);
        }

        return new VoxyVulkanBufferUsage(roles, minecraftUsage, vulkanUsage, consumers, declared, alignment);
    }

    private static int storageAlignment() {
        long alignment = Math.max(4L, VoxyVulkanContext.get().capabilities().minStorageBufferOffsetAlignment());
        if (alignment > Integer.MAX_VALUE) {
            throw new IllegalStateException("Vulkan storage-buffer alignment exceeds Java addressable range: " + alignment);
        }
        return (int) alignment;
    }

    public int roles() {
        return this.roles;
    }

    @GpuBuffer.Usage
    public int minecraftUsage() {
        return this.minecraftUsage;
    }

    public int additionalVulkanUsage() {
        return this.additionalVulkanUsage;
    }

    public VulkanSync.Access consumerAccess() {
        return this.consumerAccess;
    }

    /** Every access allowed by this allocation, used before overwriting a range during reuse. */
    public VulkanSync.Access declaredAccess() {
        return this.declaredAccess;
    }

    public int requiredAlignment() {
        return this.requiredAlignment;
    }

    public boolean has(int role) {
        return (this.roles & role) != 0;
    }
}
