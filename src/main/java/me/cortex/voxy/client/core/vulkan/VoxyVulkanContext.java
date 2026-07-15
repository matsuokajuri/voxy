package me.cortex.voxy.client.core.vulkan;

import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vulkan.VulkanBackend;
import com.mojang.blaze3d.vulkan.VulkanDevice;
import me.cortex.voxy.client.mixin.minecraft.AccessorGpuDevice;
import me.cortex.voxy.client.core.vulkan.shader.VulkanPipelineCache;
import me.cortex.voxy.common.Logger;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.NVRepresentativeFragmentTest;
import org.lwjgl.vulkan.VK12;
import org.lwjgl.vulkan.VkPhysicalDeviceFeatures2;
import org.lwjgl.vulkan.VkPhysicalDeviceProperties2;
import org.lwjgl.vulkan.VkPhysicalDevicePushDescriptorPropertiesKHR;
import org.lwjgl.vulkan.VkPhysicalDeviceRepresentativeFragmentTestFeaturesNV;
import org.lwjgl.vulkan.VkPhysicalDeviceSubgroupProperties;
import org.lwjgl.vulkan.VkPhysicalDeviceVulkan12Features;

import java.util.Objects;

@Environment(EnvType.CLIENT)
public final class VoxyVulkanContext {
    private static final long REQUIRED_MAX_DRAW_INDIRECT_COUNT = 400_000L;
    // The complete normal-renderer source chain is connected through the Minecraft target
    // composite. Runtime validation remains a correctness gate, not a route-selection fallback.
    private static final boolean FORMAL_RENDERER_CONNECTED = true;
    private static VoxyVulkanContext INSTANCE;

    private final GpuDevice hostDevice;
    private final VulkanDevice vulkanDevice;
    private final HostCapabilities capabilities;
    private VulkanPipelineCache pipelineCache;
    private VulkanDownloadStream downloadStream;

    private VoxyVulkanContext(GpuDevice hostDevice, VulkanDevice vulkanDevice, HostCapabilities capabilities) {
        this.hostDevice = hostDevice;
        this.vulkanDevice = vulkanDevice;
        this.capabilities = capabilities;
    }

    public static synchronized VoxyVulkanContext initialize(GpuDevice hostDevice) {
        RenderSystem.assertOnRenderThread();
        Objects.requireNonNull(hostDevice, "hostDevice");

        if (INSTANCE != null) {
            if (INSTANCE.hostDevice != hostDevice) {
                throw new IllegalStateException("Voxy Vulkan context is already bound to another Minecraft GpuDevice");
            }
            return INSTANCE;
        }

        if (RenderSystem.getDevice() != hostDevice) {
            throw new IllegalStateException("Voxy must use the GpuDevice owned by Minecraft RenderSystem");
        }

        var backend = ((AccessorGpuDevice) hostDevice).voxy$getBackend();
        if (!(backend instanceof VulkanDevice vulkanDevice)) {
            throw new UnsupportedOperationException(
                    "Voxy 26.2-vulkan requires Minecraft's Vulkan backend; actual backend is "
                            + hostDevice.getDeviceInfo().backendName()
                            + ". No OpenGL fallback is available."
            );
        }

        if (vulkanDevice.vkDevice() == null || vulkanDevice.vma() == 0L) {
            throw new IllegalStateException("Minecraft Vulkan backend did not expose a valid VkDevice/VMA host contract");
        }

        boolean drawIndirectCountRequired = VulkanBackend.REQUIRED_DEVICE_FEATURES.stream()
                .anyMatch(feature -> feature.name().equals("drawIndirectCount"));
        if (!drawIndirectCountRequired) {
            throw new IllegalStateException("drawIndirectCount was not added to Minecraft's required Vulkan device features");
        }
        boolean drawIndirectFirstInstanceRequired = VulkanBackend.REQUIRED_DEVICE_FEATURES.stream()
                .anyMatch(feature -> feature.name().equals("drawIndirectFirstInstance"));
        if (!drawIndirectFirstInstanceRequired) {
            throw new IllegalStateException(
                    "drawIndirectFirstInstance was not added to Minecraft's required Vulkan device features"
            );
        }

        HostCapabilities capabilities = queryHostCapabilities(vulkanDevice);
        if (!capabilities.drawIndirectCount()) {
            throw new UnsupportedOperationException(
                    "The selected Vulkan physical device does not support drawIndirectCount; Voxy cannot use a fallback draw path"
            );
        }
        if (!capabilities.drawIndirectFirstInstance()) {
            throw new UnsupportedOperationException(
                    "The selected Vulkan physical device does not support drawIndirectFirstInstance required by Voxy MDIC"
            );
        }
        if (!capabilities.pushDescriptors()) {
            throw new UnsupportedOperationException("Minecraft Vulkan device is missing VK_KHR_push_descriptor");
        }
        if (!capabilities.synchronization2()) {
            throw new UnsupportedOperationException("Minecraft Vulkan device is missing VK_KHR_synchronization2");
        }
        if (!capabilities.dynamicRendering()) {
            throw new UnsupportedOperationException("Minecraft Vulkan device is missing VK_KHR_dynamic_rendering");
        }
        if (capabilities.maxPushDescriptors() <= 0) {
            throw new UnsupportedOperationException("The selected Vulkan device reported no usable push descriptors");
        }
        if (capabilities.maxDrawIndirectCount() < REQUIRED_MAX_DRAW_INDIRECT_COUNT) {
            throw new UnsupportedOperationException(
                    "Voxy MDIC requires maxDrawIndirectCount >= " + REQUIRED_MAX_DRAW_INDIRECT_COUNT
                            + "; selected device reports " + capabilities.maxDrawIndirectCount()
            );
        }

        INSTANCE = new VoxyVulkanContext(hostDevice, vulkanDevice, capabilities);
        INSTANCE.logHostContract();
        return INSTANCE;
    }

    private static HostCapabilities queryHostCapabilities(VulkanDevice vulkanDevice) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            var physicalDevice = vulkanDevice.vkDevice().getPhysicalDevice();
            boolean representativeFragmentTestExtension = vulkanDevice.getDeviceInfo().underlyingExtensions().stream()
                    .anyMatch(name -> name.startsWith(
                            NVRepresentativeFragmentTest.VK_NV_REPRESENTATIVE_FRAGMENT_TEST_EXTENSION_NAME
                    ));

            var vulkan12Features = VkPhysicalDeviceVulkan12Features.calloc(stack).sType$Default();
            var features = VkPhysicalDeviceFeatures2.calloc(stack).sType$Default();
            features.pNext(vulkan12Features);
            var representativeFragmentTestFeatures =
                    VkPhysicalDeviceRepresentativeFragmentTestFeaturesNV.calloc(stack).sType$Default();
            if (representativeFragmentTestExtension) {
                vulkan12Features.pNext(representativeFragmentTestFeatures.address());
            }
            VK12.vkGetPhysicalDeviceFeatures2(physicalDevice, features);

            var subgroupProperties = VkPhysicalDeviceSubgroupProperties.calloc(stack).sType$Default();
            var properties = VkPhysicalDeviceProperties2.calloc(stack).sType$Default();
            properties.pNext(subgroupProperties.address());
            VK12.vkGetPhysicalDeviceProperties2(physicalDevice, properties);

            var pushDescriptorProperties = VkPhysicalDevicePushDescriptorPropertiesKHR.calloc(stack).sType$Default();
            var pushDescriptorPropertiesQuery = VkPhysicalDeviceProperties2.calloc(stack).sType$Default();
            pushDescriptorPropertiesQuery.pNext(pushDescriptorProperties.address());
            VK12.vkGetPhysicalDeviceProperties2(physicalDevice, pushDescriptorPropertiesQuery);

            var limits = properties.properties().limits();
            boolean pushDescriptors = vulkanDevice.getDeviceInfo().underlyingExtensions().stream()
                    .anyMatch(name -> name.startsWith("VK_KHR_push_descriptor"));
            boolean synchronization2 = vulkanDevice.getDeviceInfo().underlyingExtensions().stream()
                    .anyMatch(name -> name.startsWith("VK_KHR_synchronization2"));
            boolean dynamicRendering = vulkanDevice.getDeviceInfo().underlyingExtensions().stream()
                    .anyMatch(name -> name.startsWith("VK_KHR_dynamic_rendering"));

            return new HostCapabilities(
                    properties.properties().vendorID(),
                    vulkan12Features.drawIndirectCount(),
                    features.features().shaderInt64(),
                    features.features().drawIndirectFirstInstance(),
                    representativeFragmentTestExtension
                            && representativeFragmentTestFeatures.representativeFragmentTest(),
                    pushDescriptors,
                    synchronization2,
                    dynamicRendering,
                    Integer.toUnsignedLong(limits.maxStorageBufferRange()),
                    limits.minStorageBufferOffsetAlignment(),
                    Integer.toUnsignedLong(limits.maxUniformBufferRange()),
                    limits.minUniformBufferOffsetAlignment(),
                    limits.maxPushConstantsSize(),
                    pushDescriptorProperties.maxPushDescriptors(),
                    Integer.toUnsignedLong(limits.maxDrawIndirectCount()),
                    limits.maxPerStageDescriptorStorageBuffers(),
                    limits.maxDescriptorSetStorageBuffers(),
                    limits.maxPerStageDescriptorSampledImages(),
                    limits.maxPerStageDescriptorStorageImages(),
                    limits.maxDescriptorSetSampledImages(),
                    limits.maxDescriptorSetStorageImages(),
                    limits.maxComputeSharedMemorySize(),
                    limits.maxComputeWorkGroupInvocations(),
                    subgroupProperties.subgroupSize(),
                    subgroupProperties.supportedStages(),
                    subgroupProperties.supportedOperations()
            );
        }
    }

    private void logHostContract() {
        var info = this.hostDevice.getDeviceInfo();
        Logger.info(
                "Voxy Vulkan host contract established:",
                "device=" + info.name() + ",",
                "vendor=" + info.vendorName() + ",",
                "driver=" + info.driverInfo() + ",",
                "graphicsQueueFamily=" + this.vulkanDevice.graphicsQueue().queueFamilyIndex() + ",",
                "computeQueueFamily=" + this.vulkanDevice.computeQueue().queueFamilyIndex() + ",",
                "transferQueueFamily=" + this.vulkanDevice.transferQueue().queueFamilyIndex()
        );
        Logger.info(
                "Voxy Vulkan limits:",
                "vendorId=0x" + Integer.toHexString(this.capabilities.vendorId()) + ",",
                "shaderInt64Available=" + this.capabilities.shaderInt64Available() + ",",
                "drawIndirectFirstInstance=" + this.capabilities.drawIndirectFirstInstance() + ",",
                "representativeFragmentTest=" + this.capabilities.representativeFragmentTest() + ",",
                "maxStorageBufferRange=" + this.capabilities.maxStorageBufferRange() + ",",
                "minStorageBufferOffsetAlignment=" + this.capabilities.minStorageBufferOffsetAlignment() + ",",
                "maxUniformBufferRange=" + this.capabilities.maxUniformBufferRange() + ",",
                "minUniformBufferOffsetAlignment=" + this.capabilities.minUniformBufferOffsetAlignment() + ",",
                "maxPushConstantsSize=" + this.capabilities.maxPushConstantsSize() + ",",
                "maxPushDescriptors=" + this.capabilities.maxPushDescriptors() + ",",
                "maxDrawIndirectCount=" + this.capabilities.maxDrawIndirectCount() + ",",
                "maxPerStageDescriptorStorageBuffers=" + this.capabilities.maxPerStageDescriptorStorageBuffers() + ",",
                "maxDescriptorSetStorageBuffers=" + this.capabilities.maxDescriptorSetStorageBuffers() + ",",
                "maxComputeSharedMemorySize=" + this.capabilities.maxComputeSharedMemorySize() + ",",
                "maxComputeWorkGroupInvocations=" + this.capabilities.maxComputeWorkGroupInvocations() + ",",
                "subgroupSize=" + this.capabilities.subgroupSize() + ",",
                "subgroupStages=0x" + Integer.toHexString(this.capabilities.subgroupSupportedStages()) + ",",
                "subgroupOperations=0x" + Integer.toHexString(this.capabilities.subgroupSupportedOperations())
        );
    }

    public static synchronized void shutdown() {
        if (INSTANCE != null) {
            Logger.info("Releasing Voxy Vulkan host context before Minecraft destroys its Vulkan device");
            if (INSTANCE.downloadStream != null) {
                INSTANCE.downloadStream.close();
                INSTANCE.downloadStream = null;
            }
            if (INSTANCE.pipelineCache != null) {
                INSTANCE.pipelineCache.close();
                INSTANCE.pipelineCache = null;
            }
            INSTANCE = null;
        }
    }

    public static VoxyVulkanContext get() {
        VoxyVulkanContext instance = INSTANCE;
        if (instance == null) {
            throw new IllegalStateException("Voxy Vulkan context has not been initialized");
        }
        return instance;
    }

    public static boolean isInitialized() {
        return INSTANCE != null;
    }

    public static boolean isFormalRendererConnected() {
        return FORMAL_RENDERER_CONNECTED;
    }

    public GpuDevice hostDevice() {
        return this.hostDevice;
    }

    public VulkanDevice vulkanDevice() {
        return this.vulkanDevice;
    }

    public HostCapabilities capabilities() {
        return this.capabilities;
    }

    public synchronized VulkanPipelineCache pipelineCache() {
        if (this.pipelineCache == null) {
            this.pipelineCache = new VulkanPipelineCache();
        }
        return this.pipelineCache;
    }

    /** Global 32 MiB readback ring matching the original DownloadStream.INSTANCE ownership. */
    public synchronized VulkanDownloadStream downloadStream() {
        if (this.downloadStream == null) {
            this.downloadStream = new VulkanDownloadStream();
        }
        return this.downloadStream;
    }

    public record HostCapabilities(
            int vendorId,
            boolean drawIndirectCount,
            boolean shaderInt64Available,
            boolean drawIndirectFirstInstance,
            boolean representativeFragmentTest,
            boolean pushDescriptors,
            boolean synchronization2,
            boolean dynamicRendering,
            long maxStorageBufferRange,
            long minStorageBufferOffsetAlignment,
            long maxUniformBufferRange,
            long minUniformBufferOffsetAlignment,
            int maxPushConstantsSize,
            int maxPushDescriptors,
            long maxDrawIndirectCount,
            int maxPerStageDescriptorStorageBuffers,
            int maxDescriptorSetStorageBuffers,
            int maxPerStageDescriptorSampledImages,
            int maxPerStageDescriptorStorageImages,
            int maxDescriptorSetSampledImages,
            int maxDescriptorSetStorageImages,
            int maxComputeSharedMemorySize,
            int maxComputeWorkGroupInvocations,
            int subgroupSize,
            int subgroupSupportedStages,
            int subgroupSupportedOperations
    ) {
    }
}
