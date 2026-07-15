package me.cortex.voxy.client.core.vulkan.shader;

import com.mojang.blaze3d.vulkan.Destroyable;
import com.mojang.blaze3d.vulkan.VulkanDevice;
import com.mojang.blaze3d.vulkan.VulkanUtils;
import me.cortex.voxy.client.core.vulkan.VoxyVulkanContext;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.KHRPushDescriptor;
import org.lwjgl.vulkan.VK12;
import org.lwjgl.vulkan.VkDescriptorSetLayoutBinding;
import org.lwjgl.vulkan.VkDescriptorSetLayoutCreateInfo;
import org.lwjgl.vulkan.VkPipelineLayoutCreateInfo;
import org.lwjgl.vulkan.VkPushConstantRange;

import java.nio.LongBuffer;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Descriptor/push-constant contract shared by Voxy Vulkan compute and graphics pipelines. */
public final class VulkanPipelineLayout implements AutoCloseable, Destroyable {
    private final VulkanDevice device;
    private final Map<Integer, DescriptorBinding> descriptors;
    private final int pushConstantSize;
    private final int shaderStages;
    private final long descriptorSetLayout;
    private final long pipelineLayout;
    private boolean closed;

    public VulkanPipelineLayout(Collection<VulkanShaderModule> modules, String label) {
        if (modules.isEmpty()) throw new IllegalArgumentException("A Vulkan pipeline layout requires at least one shader");
        this.device = VoxyVulkanContext.get().vulkanDevice();

        Map<Integer, DescriptorBinding> merged = new LinkedHashMap<>();
        int stages = 0;
        int pushSize = 0;
        List<VulkanGlslPreprocessor.PushConstant> pushLayout = null;
        for (VulkanShaderModule module : modules) {
            int stage = module.stage().vkStage();
            stages |= stage;
            pushSize = Math.max(pushSize, module.reflection().pushConstantSize());
            if (!module.pushConstants().isEmpty()) {
                if (pushLayout == null) {
                    pushLayout = module.pushConstants();
                } else if (!pushLayout.equals(module.pushConstants())) {
                    throw new IllegalStateException("Cross-stage push-constant layout mismatch: "
                            + pushLayout + " vs " + module.pushConstants());
                }
            }
            for (VulkanShaderReflection.Descriptor descriptor : module.reflection().descriptors()) {
                DescriptorBinding incoming = new DescriptorBinding(
                        descriptor.binding(), descriptor.kind(), stage, descriptor.name(), descriptor.memberOffsets()
                );
                DescriptorBinding previous = merged.get(descriptor.binding());
                if (previous == null) {
                    merged.put(descriptor.binding(), incoming);
                } else {
                    if (previous.kind() != incoming.kind()
                            || !previous.memberOffsets().equals(incoming.memberOffsets())) {
                        throw new IllegalStateException("Cross-stage descriptor mismatch at binding "
                                + descriptor.binding() + ": " + previous + " vs " + incoming);
                    }
                    merged.put(descriptor.binding(), previous.withStages(previous.stageFlags() | stage));
                }
            }
        }
        if (pushSize > VoxyVulkanContext.get().capabilities().maxPushConstantsSize()) {
            throw new IllegalArgumentException("Voxy pipeline requires " + pushSize
                    + " push-constant bytes, device limit is "
                    + VoxyVulkanContext.get().capabilities().maxPushConstantsSize());
        }
        if (merged.size() > VoxyVulkanContext.get().capabilities().maxPushDescriptors()) {
            throw new IllegalArgumentException("Voxy pipeline requires " + merged.size()
                    + " push descriptors, device limit is "
                    + VoxyVulkanContext.get().capabilities().maxPushDescriptors());
        }
        this.descriptors = Map.copyOf(merged);
        this.pushConstantSize = pushSize;
        this.shaderStages = stages;

        try (MemoryStack stack = MemoryStack.stackPush()) {
            List<DescriptorBinding> sorted = merged.values().stream()
                    .sorted(Comparator.comparingInt(DescriptorBinding::binding)).toList();
            VkDescriptorSetLayoutBinding.Buffer bindings = VkDescriptorSetLayoutBinding.calloc(sorted.size(), stack);
            for (int i = 0; i < sorted.size(); i++) {
                DescriptorBinding binding = sorted.get(i);
                bindings.get(i)
                        .binding(binding.binding())
                        .descriptorType(binding.kind().vkDescriptorType())
                        .descriptorCount(1)
                        .stageFlags(binding.stageFlags());
            }

            VkDescriptorSetLayoutCreateInfo setInfo = VkDescriptorSetLayoutCreateInfo.calloc(stack).sType$Default()
                    .flags(KHRPushDescriptor.VK_DESCRIPTOR_SET_LAYOUT_CREATE_PUSH_DESCRIPTOR_BIT_KHR)
                    .pBindings(bindings);
            LongBuffer pointer = stack.callocLong(1);
            int result = VK12.vkCreateDescriptorSetLayout(this.device.vkDevice(), setInfo, null, pointer);
            VulkanUtils.crashIfFailure(this.device, result, "Failed to create Voxy push-descriptor layout " + label);
            this.descriptorSetLayout = pointer.get(0);

            VkPipelineLayoutCreateInfo pipelineInfo = VkPipelineLayoutCreateInfo.calloc(stack).sType$Default()
                    .pSetLayouts(stack.longs(this.descriptorSetLayout));
            if (pushSize > 0) {
                VkPushConstantRange.Buffer range = VkPushConstantRange.calloc(1, stack)
                        .stageFlags(stages)
                        .offset(0)
                        .size(pushSize);
                pipelineInfo.pPushConstantRanges(range);
            }
            pointer.clear();
            result = VK12.vkCreatePipelineLayout(this.device.vkDevice(), pipelineInfo, null, pointer);
            if (result != VK12.VK_SUCCESS) {
                VK12.vkDestroyDescriptorSetLayout(this.device.vkDevice(), this.descriptorSetLayout, null);
            }
            VulkanUtils.crashIfFailure(this.device, result, "Failed to create Voxy pipeline layout " + label);
            this.pipelineLayout = pointer.get(0);
        }
        this.device.instance().debug().setObjectName(
                this.device.vkDevice(), VK12.VK_OBJECT_TYPE_DESCRIPTOR_SET_LAYOUT, this.descriptorSetLayout,
                label + " descriptor layout"
        );
        this.device.instance().debug().setObjectName(
                this.device.vkDevice(), VK12.VK_OBJECT_TYPE_PIPELINE_LAYOUT, this.pipelineLayout, label + " pipeline layout"
        );
    }

    public Map<Integer, DescriptorBinding> descriptors() {
        return this.descriptors;
    }

    public int pushConstantSize() {
        return this.pushConstantSize;
    }

    public int shaderStages() {
        return this.shaderStages;
    }

    public long vkPipelineLayout() {
        if (this.closed) throw new IllegalStateException("Vulkan pipeline layout is closed");
        return this.pipelineLayout;
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
        VK12.vkDestroyPipelineLayout(this.device.vkDevice(), this.pipelineLayout, null);
        VK12.vkDestroyDescriptorSetLayout(this.device.vkDevice(), this.descriptorSetLayout, null);
    }

    public record DescriptorBinding(
            int binding,
            VulkanDescriptorKind kind,
            int stageFlags,
            String name,
            List<Integer> memberOffsets
    ) {
        DescriptorBinding withStages(int stages) {
            return new DescriptorBinding(this.binding, this.kind, stages, this.name, this.memberOffsets);
        }
    }
}
