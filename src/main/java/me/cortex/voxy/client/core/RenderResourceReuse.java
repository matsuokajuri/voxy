package me.cortex.voxy.client.core;

import me.cortex.voxy.client.core.model.ModelFactory;
import me.cortex.voxy.client.core.vulkan.VoxyVulkanBuffer;
import me.cortex.voxy.client.core.vulkan.VoxyVulkanBufferUsage;
import me.cortex.voxy.client.core.vulkan.VoxyVulkanContext;
import me.cortex.voxy.client.core.vulkan.VoxyVulkanImage;
import me.cortex.voxy.client.core.vulkan.VulkanImageStates;
import me.cortex.voxy.common.Logger;
import me.cortex.voxy.common.util.ThreadUtils;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.vma.Vma;
import org.lwjgl.util.vma.VmaBudget;
import org.lwjgl.vulkan.VK12;
import org.lwjgl.vulkan.VkPhysicalDeviceMemoryProperties;

import java.util.ArrayList;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.textures.GpuTexture;

//System to allow reuse/recycling of render buffer/texture allocations
// specfically the geometry buffer and texture atlas allocation
public class RenderResourceReuse {
    private static final ArrayList<VoxyVulkanImage> MODEL_TEXTURE_CACHE = new ArrayList<>();
    private static final ArrayList<VoxyVulkanBuffer> GEOMETRY_BUFFER_CACHE = new ArrayList<>();

    //Clears and frees any cached resources (used when the entire instance is shutdown)
    public static void clearResources() {
        MODEL_TEXTURE_CACHE.forEach(VoxyVulkanImage::close);
        GEOMETRY_BUFFER_CACHE.forEach(VoxyVulkanBuffer::close);
        MODEL_TEXTURE_CACHE.clear();
        GEOMETRY_BUFFER_CACHE.clear();
    }


    public static VoxyVulkanImage getOrCreateModelStoreTextureAtlas() {
        VoxyVulkanImage atlas;
        if (!MODEL_TEXTURE_CACHE.isEmpty()) {
            atlas = MODEL_TEXTURE_CACHE.removeFirst();
        } else {
            atlas = new VoxyVulkanImage(
                    "ModelTextures",
                    GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_COPY_SRC | GpuTexture.USAGE_TEXTURE_BINDING,
                    0,
                    GpuFormat.RGBA8_UNORM,
                    ModelFactory.MODEL_TEXTURE_SIZE * 3 * 256,
                    ModelFactory.MODEL_TEXTURE_SIZE * 2 * 256,
                    1,
                    Integer.numberOfTrailingZeros(ModelFactory.MODEL_TEXTURE_SIZE)
            );
        }
        return atlas.clearToZero(VulkanImageStates.SHADER_SAMPLED);
    }
    public static void giveBackModelStoreTextureAtlas(VoxyVulkanImage texture) {
        if (texture.isClosed()) throw new IllegalArgumentException("Cannot cache a closed Vulkan model atlas");
        if (MODEL_TEXTURE_CACHE.contains(texture)) {
            throw new IllegalStateException("Vulkan model atlas was returned to the reuse cache twice");
        }
        MODEL_TEXTURE_CACHE.add(texture);
    }

    static VoxyVulkanBuffer getOrCreateGeometryBuffer() {
        VoxyVulkanBuffer buffer;
        if (!GEOMETRY_BUFFER_CACHE.isEmpty()) {
            buffer = GEOMETRY_BUFFER_CACHE.removeFirst();
        } else {
            long capacity = getGeometryBufferSize();
            int roles = VoxyVulkanBufferUsage.STORAGE_COMPUTE
                    | VoxyVulkanBufferUsage.STORAGE_GRAPHICS
                    | VoxyVulkanBufferUsage.TRANSFER_SOURCE
                    | VoxyVulkanBufferUsage.TRANSFER_DESTINATION;
            // The OpenGL owner deliberately allocated and reused this arena without clearing it.
            // Only ranges referenced by freshly uploaded section metadata are observable.
            buffer = VoxyVulkanBuffer.createUninitialized(
                    capacity, VoxyVulkanBufferUsage.of(roles), "GeometryData"
            );
            Logger.info("Allocated new Vulkan geometry buffer: " + buffer.size()
                    + ", available device-local budget=" + (getAvailableDeviceLocalMemory() / (1024 * 1024)) + " MiB");
        }
        return buffer;
    }

    public static void giveBackGeometryBuffer(VoxyVulkanBuffer geometryBuffer) {
        if (geometryBuffer.isClosed()) throw new IllegalArgumentException("Cannot cache a closed Vulkan geometry buffer");
        if (GEOMETRY_BUFFER_CACHE.contains(geometryBuffer)) {
            throw new IllegalStateException("Vulkan geometry buffer was returned to the reuse cache twice");
        }
        GEOMETRY_BUFFER_CACHE.add(geometryBuffer);
    }

    private static long getGeometryBufferSize() {
        long minCapacity = 512L * 1024 * 1024;
        long maxStorageRange = VoxyVulkanContext.get().capabilities().maxStorageBufferRange();
        if (maxStorageRange < minCapacity) {
            throw new IllegalStateException("Vulkan maxStorageBufferRange is below Voxy's 512 MiB geometry minimum");
        }
        // Preserve the OpenGL owner's exact next-power-of-two-then-double capacity rule.
        // If that historical capacity cannot be bound as one Vulkan storage range, fail below.
        long roundedStorageLimit = (Long.highestOneBit(maxStorageRange - 1L) << 1) << 1;
        long geometryCapacity = Math.min(roundedStorageLimit, 1L << 32) - 1024L;
        int vendorId = VoxyVulkanContext.get().capabilities().vendorId();
        if (vendorId == 0x8086) {
            geometryCapacity = Math.max(geometryCapacity, 1L << 30);
        }
        if (vendorId == 0x10DE && ThreadUtils.isLinux) {
            geometryCapacity = Math.min(geometryCapacity, 2000L * 1024L * 1024L);
        }
        geometryCapacity = Math.max(minCapacity, geometryCapacity);
        long available = getAvailableDeviceLocalMemory();
        if (available > 0L) {
            long limit = Math.max(minCapacity, available - (long) (1.5 * 1024 * 1024 * 1024));
            geometryCapacity = Math.min(geometryCapacity, limit);
        }
        var override = System.getProperty("voxy.geometryBufferSizeOverrideMB", "");
        if (!override.isEmpty()) {
            geometryCapacity = Long.parseLong(override)*1024L*1024L;
        }
        geometryCapacity &= ~7L;
        if (geometryCapacity < minCapacity || geometryCapacity > maxStorageRange) {
            throw new IllegalArgumentException("Vulkan geometry capacity is outside [512 MiB, maxStorageBufferRange]: "
                    + geometryCapacity);
        }
        return geometryCapacity;
    }

    private static long getAvailableDeviceLocalMemory() {
        var context = VoxyVulkanContext.get();
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkPhysicalDeviceMemoryProperties memory = VkPhysicalDeviceMemoryProperties.calloc(stack);
            VK12.vkGetPhysicalDeviceMemoryProperties(
                    context.vulkanDevice().vkDevice().getPhysicalDevice(), memory
            );
            VmaBudget.Buffer budgets = VmaBudget.calloc(VK12.VK_MAX_MEMORY_HEAPS, stack);
            Vma.vmaGetHeapBudgets(context.vulkanDevice().vma(), budgets);
            long available = 0L;
            for (int heap = 0; heap < memory.memoryHeapCount(); heap++) {
                if ((memory.memoryHeaps(heap).flags() & VK12.VK_MEMORY_HEAP_DEVICE_LOCAL_BIT) != 0) {
                    VmaBudget budget = budgets.get(heap);
                    available = Math.max(available, Math.max(0L, budget.budget() - budget.usage()));
                }
            }
            return available;
        }
    }
}
