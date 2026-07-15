package me.cortex.voxy.client.core.vulkan.shader;

import com.mojang.blaze3d.vulkan.Destroyable;
import com.mojang.blaze3d.vulkan.VulkanDevice;
import com.mojang.blaze3d.vulkan.VulkanUtils;
import me.cortex.voxy.client.core.vulkan.VoxyVulkanContext;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VK12;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkGraphicsPipelineCreateInfo;
import org.lwjgl.vulkan.VkPipelineColorBlendAttachmentState;
import org.lwjgl.vulkan.VkPipelineColorBlendStateCreateInfo;
import org.lwjgl.vulkan.VkPipelineDepthStencilStateCreateInfo;
import org.lwjgl.vulkan.VkPipelineDynamicStateCreateInfo;
import org.lwjgl.vulkan.VkPipelineInputAssemblyStateCreateInfo;
import org.lwjgl.vulkan.VkPipelineMultisampleStateCreateInfo;
import org.lwjgl.vulkan.VkPipelineRepresentativeFragmentTestStateCreateInfoNV;
import org.lwjgl.vulkan.VkPipelineRasterizationStateCreateInfo;
import org.lwjgl.vulkan.VkPipelineRenderingCreateInfoKHR;
import org.lwjgl.vulkan.VkPipelineShaderStageCreateInfo;
import org.lwjgl.vulkan.VkPipelineVertexInputStateCreateInfo;
import org.lwjgl.vulkan.VkPipelineViewportStateCreateInfo;
import org.lwjgl.vulkan.VkStencilOpState;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.List;

/**
 * Raw dynamic-rendering graphics pipeline for Voxy shaders that source vertices from built-ins/SSBOs.
 * Attachment formats and fixed-function state are explicit and must mirror the corresponding GL owner.
 */
public final class VulkanGraphicsPipeline implements AutoCloseable, Destroyable {
    private final VulkanDevice device;
    private final String label;
    private final VulkanShaderModule vertex;
    private final VulkanShaderModule fragment;
    private final VulkanPipelineLayout layout;
    private final long pipeline;
    private boolean closed;

    public VulkanGraphicsPipeline(
            VulkanShaderModule vertex,
            VulkanShaderModule fragment,
            State state,
            String label
    ) {
        if (vertex.stage() != VulkanShaderStage.VERTEX || fragment.stage() != VulkanShaderStage.FRAGMENT) {
            vertex.close();
            fragment.close();
            throw new IllegalArgumentException("A Voxy graphics pipeline requires one vertex and one fragment module");
        }
        try {
            state.validate();
        } catch (RuntimeException | Error exception) {
            vertex.close();
            fragment.close();
            throw exception;
        }
        this.device = VoxyVulkanContext.get().vulkanDevice();
        this.label = label;
        this.vertex = vertex;
        this.fragment = fragment;
        try {
            this.layout = new VulkanPipelineLayout(List.of(vertex, fragment), label);
        } catch (RuntimeException | Error exception) {
            this.vertex.close();
            this.fragment.close();
            throw exception;
        }

        long createdPipeline = 0L;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer main = stack.UTF8("main");
            VkPipelineShaderStageCreateInfo.Buffer stages = VkPipelineShaderStageCreateInfo.calloc(2, stack);
            stages.get(0).sType$Default().stage(VK12.VK_SHADER_STAGE_VERTEX_BIT)
                    .module(vertex.vkShaderModule()).pName(main);
            stages.get(1).sType$Default().stage(VK12.VK_SHADER_STAGE_FRAGMENT_BIT)
                    .module(fragment.vkShaderModule()).pName(main);

            VkPipelineVertexInputStateCreateInfo vertexInput = VkPipelineVertexInputStateCreateInfo.calloc(stack)
                    .sType$Default();
            VkPipelineInputAssemblyStateCreateInfo inputAssembly = VkPipelineInputAssemblyStateCreateInfo.calloc(stack)
                    .sType$Default().topology(state.topology());
            VkPipelineRasterizationStateCreateInfo rasterization = VkPipelineRasterizationStateCreateInfo.calloc(stack)
                    .sType$Default()
                    .polygonMode(state.polygonMode())
                    .cullMode(state.cullMode())
                    .frontFace(state.frontFace())
                    .lineWidth(1.0f);
            VkPipelineDepthStencilStateCreateInfo depthStencil = VkPipelineDepthStencilStateCreateInfo.calloc(stack)
                    .sType$Default()
                    .depthTestEnable(state.depthTest())
                    .depthWriteEnable(state.depthWrite())
                    .depthCompareOp(state.depthCompareOp())
                    .depthBoundsTestEnable(false)
                    .stencilTestEnable(state.stencil().enabled());
            state.stencil().front().writeTo(depthStencil.front());
            state.stencil().back().writeTo(depthStencil.back());
            VkPipelineColorBlendAttachmentState.Buffer blendAttachments =
                    VkPipelineColorBlendAttachmentState.calloc(state.colorAttachments().size(), stack);
            for (int i = 0; i < state.colorAttachments().size(); i++) {
                ColorAttachment attachment = state.colorAttachments().get(i);
                blendAttachments.get(i)
                        .colorWriteMask(attachment.writeMask())
                        .blendEnable(attachment.blend())
                        .srcColorBlendFactor(attachment.srcColorFactor())
                        .dstColorBlendFactor(attachment.dstColorFactor())
                        .colorBlendOp(attachment.colorOp())
                        .srcAlphaBlendFactor(attachment.srcAlphaFactor())
                        .dstAlphaBlendFactor(attachment.dstAlphaFactor())
                        .alphaBlendOp(attachment.alphaOp());
            }
            VkPipelineColorBlendStateCreateInfo colorBlend = VkPipelineColorBlendStateCreateInfo.calloc(stack)
                    .sType$Default().pAttachments(blendAttachments);
            VkPipelineViewportStateCreateInfo viewport = VkPipelineViewportStateCreateInfo.calloc(stack)
                    .sType$Default().viewportCount(1).scissorCount(1);
            VkPipelineMultisampleStateCreateInfo multisample = VkPipelineMultisampleStateCreateInfo.calloc(stack)
                    .sType$Default().rasterizationSamples(state.samples());
            VkPipelineDynamicStateCreateInfo dynamic = VkPipelineDynamicStateCreateInfo.calloc(stack)
                    .sType$Default().pDynamicStates(stack.ints(
                            VK12.VK_DYNAMIC_STATE_VIEWPORT,
                            VK12.VK_DYNAMIC_STATE_SCISSOR
                    ));

            VkPipelineRenderingCreateInfoKHR rendering = VkPipelineRenderingCreateInfoKHR.calloc(stack)
                    .sType$Default()
                    .depthAttachmentFormat(state.depthStencilFormat())
                    .stencilAttachmentFormat(state.stencilAttachmentFormat());
            IntBuffer colorFormats = stack.mallocInt(state.colorAttachments().size());
            for (ColorAttachment attachment : state.colorAttachments()) colorFormats.put(attachment.format());
            colorFormats.flip();
            rendering.pColorAttachmentFormats(colorFormats);

            long pipelinePNext = rendering.address();
            if (state.representativeFragmentTest()) {
                VkPipelineRepresentativeFragmentTestStateCreateInfoNV representativeFragmentTest =
                        VkPipelineRepresentativeFragmentTestStateCreateInfoNV.calloc(stack)
                                .sType$Default()
                                .pNext(rendering.address())
                                .representativeFragmentTestEnable(true);
                pipelinePNext = representativeFragmentTest.address();
            }

            VkGraphicsPipelineCreateInfo.Buffer info = VkGraphicsPipelineCreateInfo.calloc(1, stack)
                    .sType$Default()
                    .pStages(stages)
                    .pVertexInputState(vertexInput)
                    .pInputAssemblyState(inputAssembly)
                    .pRasterizationState(rasterization)
                    .pDepthStencilState(depthStencil)
                    .pColorBlendState(colorBlend)
                    .pViewportState(viewport)
                    .pMultisampleState(multisample)
                    .pDynamicState(dynamic)
                    .layout(this.layout.vkPipelineLayout())
                    .pNext(pipelinePNext);
            var pointer = stack.callocLong(1);
            int result = VK12.vkCreateGraphicsPipelines(
                    this.device.vkDevice(), VoxyVulkanContext.get().pipelineCache().handle(), info, null, pointer
            );
            VulkanUtils.crashIfFailure(this.device, result, "Failed to create Voxy graphics pipeline " + label);
            createdPipeline = pointer.get(0);
        } catch (RuntimeException | Error exception) {
            this.layout.close();
            this.vertex.close();
            this.fragment.close();
            throw exception;
        }
        this.pipeline = createdPipeline;
        this.device.instance().debug().setObjectName(
                this.device.vkDevice(), VK12.VK_OBJECT_TYPE_PIPELINE, this.pipeline, label
        );
    }

    public void bind(VkCommandBuffer commandBuffer, VulkanPushDescriptors descriptors, ByteBuffer pushConstants) {
        if (this.closed) throw new IllegalStateException("Vulkan graphics pipeline is closed: " + this.label);
        VK12.vkCmdBindPipeline(commandBuffer, VK12.VK_PIPELINE_BIND_POINT_GRAPHICS, this.pipeline);
        descriptors.push(commandBuffer, this.layout, VK12.VK_PIPELINE_BIND_POINT_GRAPHICS);
        int requiredPushSize = this.layout.pushConstantSize();
        int suppliedPushSize = pushConstants == null ? 0 : pushConstants.remaining();
        if (suppliedPushSize != requiredPushSize) {
            throw new IllegalArgumentException("Graphics pipeline " + this.label + " requires " + requiredPushSize
                    + " push-constant bytes but received " + suppliedPushSize);
        }
        if (requiredPushSize > 0) {
            VK12.vkCmdPushConstants(
                    commandBuffer, this.layout.vkPipelineLayout(), this.layout.shaderStages(), 0, pushConstants
            );
        }
    }

    public VulkanPipelineLayout layout() {
        return this.layout;
    }

    @Override
    public void close() {
        if (!this.closed) {
            this.closed = true;
            this.device.createCommandEncoder().queueForDestroy(this);
            this.layout.close();
            this.vertex.close();
            this.fragment.close();
        }
    }

    @Override
    public void destroy() {
        VK12.vkDestroyPipeline(this.device.vkDevice(), this.pipeline, null);
    }

    public record State(
            int topology,
            int polygonMode,
            int cullMode,
            int frontFace,
            boolean depthTest,
            boolean depthWrite,
            int depthCompareOp,
            StencilState stencil,
            int depthStencilFormat,
            int stencilAttachmentFormat,
            int samples,
            boolean representativeFragmentTest,
            List<ColorAttachment> colorAttachments
    ) {
        public State {
            if (stencil == null) throw new IllegalArgumentException("Vulkan graphics pipeline stencil state is null");
            colorAttachments = List.copyOf(colorAttachments);
        }

        void validate() {
            if (samples == 0) throw new IllegalArgumentException("Vulkan graphics pipeline sample count is zero");
            if ((depthTest || depthWrite) && depthStencilFormat == VK12.VK_FORMAT_UNDEFINED) {
                throw new IllegalArgumentException("Depth state requires an explicit attachment format");
            }
            if (stencil.enabled() && stencilAttachmentFormat == VK12.VK_FORMAT_UNDEFINED) {
                throw new IllegalArgumentException("Stencil state requires an explicit stencil attachment format");
            }
            if (representativeFragmentTest
                    && !VoxyVulkanContext.get().capabilities().representativeFragmentTest()) {
                throw new IllegalArgumentException(
                        "Representative-fragment test state requires the enabled VK_NV_representative_fragment_test feature"
                );
            }
        }
    }

    public record StencilState(boolean enabled, StencilFace front, StencilFace back) {
        public StencilState {
            if (front == null || back == null) {
                throw new IllegalArgumentException("Vulkan graphics pipeline stencil faces must be explicit");
            }
        }

        public static StencilState disabled() {
            StencilFace keep = StencilFace.keepAlways();
            return new StencilState(false, keep, keep);
        }

        public static StencilState bothFaces(StencilFace face) {
            return new StencilState(true, face, face);
        }
    }

    public record StencilFace(
            int failOp,
            int passOp,
            int depthFailOp,
            int compareOp,
            int compareMask,
            int writeMask,
            int reference
    ) {
        public static StencilFace keepAlways() {
            return new StencilFace(
                    VK12.VK_STENCIL_OP_KEEP,
                    VK12.VK_STENCIL_OP_KEEP,
                    VK12.VK_STENCIL_OP_KEEP,
                    VK12.VK_COMPARE_OP_ALWAYS,
                    0xFF,
                    0xFF,
                    0
            );
        }

        private void writeTo(VkStencilOpState target) {
            target.failOp(this.failOp)
                    .passOp(this.passOp)
                    .depthFailOp(this.depthFailOp)
                    .compareOp(this.compareOp)
                    .compareMask(this.compareMask)
                    .writeMask(this.writeMask)
                    .reference(this.reference);
        }
    }

    public record ColorAttachment(
            int format,
            int writeMask,
            boolean blend,
            int srcColorFactor,
            int dstColorFactor,
            int colorOp,
            int srcAlphaFactor,
            int dstAlphaFactor,
            int alphaOp
    ) {
        public static ColorAttachment opaque(int format, int writeMask) {
            return new ColorAttachment(
                    format, writeMask, false,
                    VK12.VK_BLEND_FACTOR_ONE, VK12.VK_BLEND_FACTOR_ZERO, VK12.VK_BLEND_OP_ADD,
                    VK12.VK_BLEND_FACTOR_ONE, VK12.VK_BLEND_FACTOR_ZERO, VK12.VK_BLEND_OP_ADD
            );
        }

        public static ColorAttachment alphaBlend(int format, int writeMask) {
            return new ColorAttachment(
                    format, writeMask, true,
                    VK12.VK_BLEND_FACTOR_SRC_ALPHA, VK12.VK_BLEND_FACTOR_ONE_MINUS_SRC_ALPHA, VK12.VK_BLEND_OP_ADD,
                    VK12.VK_BLEND_FACTOR_ONE, VK12.VK_BLEND_FACTOR_ONE_MINUS_SRC_ALPHA, VK12.VK_BLEND_OP_ADD
            );
        }
    }
}
