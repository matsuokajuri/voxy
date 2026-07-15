package me.cortex.voxy.client.mixin.minecraft.vulkan;

import com.mojang.blaze3d.vulkan.VulkanBackend;
import com.mojang.blaze3d.vulkan.VulkanPhysicalDevice;
import com.mojang.blaze3d.vulkan.init.VulkanFeature;
import com.mojang.blaze3d.vulkan.init.VulkanPNextStruct;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.NVRepresentativeFragmentTest;
import org.lwjgl.vulkan.VK12;
import org.lwjgl.vulkan.VkPhysicalDeviceFeatures;
import org.lwjgl.vulkan.VkPhysicalDeviceFeatures2;
import org.lwjgl.vulkan.VkPhysicalDeviceRepresentativeFragmentTestFeaturesNV;
import org.lwjgl.vulkan.VkPhysicalDeviceVulkan12Features;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

@Mixin(value = VulkanBackend.class, remap = false)
public class MixinVulkanBackend {
    @Unique
    private static final VulkanFeature VOXY$REPRESENTATIVE_FRAGMENT_TEST_FEATURE = new VulkanFeature(
            new VulkanPNextStruct(
                    NVRepresentativeFragmentTest.VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_REPRESENTATIVE_FRAGMENT_TEST_FEATURES_NV,
                    VkPhysicalDeviceRepresentativeFragmentTestFeaturesNV.SIZEOF
            ),
            "representativeFragmentTest",
            VkPhysicalDeviceRepresentativeFragmentTestFeaturesNV.REPRESENTATIVEFRAGMENTTEST
    );

    @Shadow @Final @Mutable
    public static Set<VulkanFeature> REQUIRED_DEVICE_FEATURES;

    @Inject(method = "<clinit>", at = @At("TAIL"))
    private static void voxy$requireFormalRendererFeatures(CallbackInfo ci) {
        var requiredFeatures = new HashSet<>(REQUIRED_DEVICE_FEATURES);
        requiredFeatures.add(new VulkanFeature(
                VulkanBackend.VK10_FEATURES_STRUCT,
                "drawIndirectFirstInstance",
                VkPhysicalDeviceFeatures.DRAWINDIRECTFIRSTINSTANCE
        ));
        requiredFeatures.add(new VulkanFeature(
                VulkanBackend.VK10_FEATURES_STRUCT,
                "fragmentStoresAndAtomics",
                VkPhysicalDeviceFeatures.FRAGMENTSTORESANDATOMICS
        ));
        requiredFeatures.add(new VulkanFeature(
                VulkanBackend.VK12_FEATURES_STRUCT,
                "drawIndirectCount",
                VkPhysicalDeviceVulkan12Features.DRAWINDIRECTCOUNT
        ));
        REQUIRED_DEVICE_FEATURES = Set.copyOf(requiredFeatures);
    }

    @ModifyArgs(
            method = "createDevice(JLcom/mojang/blaze3d/shaders/ShaderSource;Lcom/mojang/blaze3d/shaders/GpuDebugOptions;Ljava/lang/Runnable;)Lcom/mojang/blaze3d/systems/GpuDevice;",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/blaze3d/vulkan/VulkanBackend;createDevice(Ljava/util/Collection;Lcom/mojang/blaze3d/vulkan/VulkanPhysicalDevice;Ljava/util/Set;)Lorg/lwjgl/vulkan/VkDevice;"
            )
    )
    private void voxy$enableOptionalRepresentativeFragmentTest(Args args) {
        Collection<String> deviceExtensions = args.get(0);
        VulkanPhysicalDevice physicalDevice = args.get(1);
        Set<VulkanFeature> enabledFeatures = args.get(2);
        if (!physicalDevice.hasDeviceExtension(
                NVRepresentativeFragmentTest.VK_NV_REPRESENTATIVE_FRAGMENT_TEST_EXTENSION_NAME
        )) {
            return;
        }

        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkPhysicalDeviceFeatures2 supportedFeatures = VkPhysicalDeviceFeatures2.calloc(stack).sType$Default();
            VOXY$REPRESENTATIVE_FRAGMENT_TEST_FEATURE.struct()
                    .findOrCreateStructInPNextChain(supportedFeatures, stack);
            VK12.vkGetPhysicalDeviceFeatures2(physicalDevice.vkPhysicalDevice(), supportedFeatures);
            if (!VOXY$REPRESENTATIVE_FRAGMENT_TEST_FEATURE.get(supportedFeatures)) {
                return;
            }
        }

        deviceExtensions.add(NVRepresentativeFragmentTest.VK_NV_REPRESENTATIVE_FRAGMENT_TEST_EXTENSION_NAME);
        enabledFeatures.add(VOXY$REPRESENTATIVE_FRAGMENT_TEST_FEATURE);
    }
}
