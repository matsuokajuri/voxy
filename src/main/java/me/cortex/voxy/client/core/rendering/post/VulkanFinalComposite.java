package me.cortex.voxy.client.core.rendering.post;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.textures.AddressMode;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vulkan.VulkanConst;
import com.mojang.blaze3d.vulkan.VulkanGpuTexture;
import com.mojang.blaze3d.vulkan.VulkanGpuTextureView;
import me.cortex.voxy.client.config.VoxyConfig;
import me.cortex.voxy.client.core.RenderProperties;
import me.cortex.voxy.client.core.VoxyRenderSystem;
import me.cortex.voxy.client.core.rendering.section.backend.mdic.VulkanMDICViewport;
import me.cortex.voxy.client.core.rendering.util.VulkanColorTarget;
import me.cortex.voxy.client.core.rendering.util.VulkanDepthStencilTarget;
import me.cortex.voxy.client.core.rendering.util.VulkanFullscreenIndexBuffer;
import me.cortex.voxy.client.core.vulkan.VoxyVulkanSampler;
import me.cortex.voxy.client.core.vulkan.VulkanCommandRecorder;
import me.cortex.voxy.client.core.vulkan.VulkanHostImageStates;
import me.cortex.voxy.client.core.vulkan.VulkanImageStates;
import me.cortex.voxy.client.core.vulkan.VulkanSync;
import me.cortex.voxy.client.core.vulkan.shader.VulkanGraphicsPipeline;
import me.cortex.voxy.client.core.vulkan.shader.VulkanPushDescriptors;
import me.cortex.voxy.client.core.vulkan.shader.VulkanShaderCompiler;
import me.cortex.voxy.client.core.vulkan.shader.VulkanShaderModule;
import me.cortex.voxy.client.core.vulkan.shader.VulkanShaderStage;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.KHRDynamicRendering;
import org.lwjgl.vulkan.VK12;
import org.lwjgl.vulkan.VkRect2D;
import org.lwjgl.vulkan.VkRenderingAttachmentInfo;
import org.lwjgl.vulkan.VkRenderingInfo;
import org.lwjgl.vulkan.VkViewport;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalDouble;

/** Vulkan owner for NormalRenderPipeline.finish and transformBlitDepth. */
public final class VulkanFinalComposite implements AutoCloseable {
    private static final int COLOR_WRITE_MASK = VK12.VK_COLOR_COMPONENT_R_BIT
            | VK12.VK_COLOR_COMPONENT_G_BIT
            | VK12.VK_COLOR_COMPONENT_B_BIT
            | VK12.VK_COLOR_COMPONENT_A_BIT;

    private final RenderProperties properties;
    private final boolean useEnvironmentalFog;
    private final VoxyVulkanSampler depthSampler;
    private final VoxyVulkanSampler colourSampler;
    private final VulkanFullscreenIndexBuffer indexBuffer;
    private VulkanGraphicsPipeline pipeline;
    private GpuFormat pipelineColourFormat;
    private GpuFormat pipelineDepthFormat;
    private boolean closed;

    public VulkanFinalComposite(RenderProperties properties) {
        this.properties = Objects.requireNonNull(properties, "properties");
        this.useEnvironmentalFog = VoxyConfig.CONFIG.useEnvironmentalFog;
        VoxyVulkanSampler createdDepthSampler = null;
        VoxyVulkanSampler createdColourSampler = null;
        VulkanFullscreenIndexBuffer createdIndexBuffer = null;
        try {
            createdDepthSampler = new VoxyVulkanSampler(
                    AddressMode.REPEAT,
                    AddressMode.REPEAT,
                    FilterMode.NEAREST,
                    FilterMode.NEAREST,
                    1,
                    OptionalDouble.of(0.0)
            );
            createdColourSampler = new VoxyVulkanSampler(
                    AddressMode.REPEAT,
                    AddressMode.REPEAT,
                    FilterMode.NEAREST,
                    FilterMode.NEAREST,
                    1,
                    OptionalDouble.of(0.0)
            );
            createdIndexBuffer = new VulkanFullscreenIndexBuffer();
        } catch (RuntimeException | Error exception) {
            if (createdIndexBuffer != null) createdIndexBuffer.close();
            if (createdColourSampler != null) createdColourSampler.close();
            if (createdDepthSampler != null) createdDepthSampler.close();
            throw exception;
        }
        this.depthSampler = createdDepthSampler;
        this.colourSampler = createdColourSampler;
        this.indexBuffer = createdIndexBuffer;
    }

    public void render(
            VulkanMDICViewport viewport,
            VulkanColorTarget colour,
            VulkanDepthStencilTarget depthStencil,
            GpuTextureView sourceDepth,
            GpuTextureView sourceColour,
            int sourceWidth,
            int sourceHeight
    ) {
        this.ensureOpen();
        Objects.requireNonNull(viewport, "viewport");
        Objects.requireNonNull(colour, "colour");
        Objects.requireNonNull(depthStencil, "depthStencil");
        if (!(sourceDepth instanceof VulkanGpuTextureView hostDepthView)
                || !(sourceColour instanceof VulkanGpuTextureView hostColourView)) {
            throw new IllegalArgumentException("Voxy final composite requires Minecraft Vulkan target views");
        }
        if (sourceDepth.isClosed() || sourceColour.isClosed()
                || !sourceDepth.texture().getFormat().hasDepthAspect()
                || !sourceColour.texture().getFormat().hasColorAspect()
                || (sourceDepth.texture().usage() & GpuTexture.USAGE_RENDER_ATTACHMENT) == 0
                || (sourceColour.texture().usage() & GpuTexture.USAGE_RENDER_ATTACHMENT) == 0) {
            throw new IllegalArgumentException("Voxy final composite requires open render-attachment target views");
        }
        if (sourceWidth <= 0 || sourceHeight <= 0
                || sourceDepth.getWidth(0) != sourceWidth || sourceDepth.getHeight(0) != sourceHeight
                || sourceColour.getWidth(0) != sourceWidth || sourceColour.getHeight(0) != sourceHeight
                || colour.width() != viewport.width || colour.height() != viewport.height
                || depthStencil.width() != viewport.width || depthStencil.height() != viewport.height) {
            throw new IllegalArgumentException("Voxy final composite target dimensions do not match the frame");
        }

        boolean fogCoversAllRendering = viewport.fogParameters.environmentalEnd()
                < VoxyRenderSystem.getRenderDistance();
        if (fogCoversAllRendering) return;

        this.ensurePipeline(
                sourceColour.texture().getFormat(),
                sourceDepth.texture().getFormat()
        );
        VulkanGpuTexture hostDepthImage = hostDepthView.texture();
        VulkanGpuTexture hostColourImage = hostColourView.texture();
        VulkanGraphicsPipeline activePipeline = this.pipeline;
        VulkanCommandRecorder.record(commandBuffer -> {
            colour.transitionToSampled(commandBuffer);
            depthStencil.image().transition(
                    commandBuffer, 0, 1, 0, 1, VulkanImageStates.SHADER_SAMPLED
            );
            VulkanSync.imageBarrier(
                    commandBuffer,
                    hostColourImage.vkImage(),
                    VK12.VK_IMAGE_ASPECT_COLOR_BIT,
                    sourceColour.baseMipLevel(),
                    1,
                    0,
                    1,
                    VulkanHostImageStates.COLOR_ATTACHMENT,
                    VulkanHostImageStates.COLOR_ATTACHMENT
            );
            VulkanSync.imageBarrier(
                    commandBuffer,
                    hostDepthImage.vkImage(),
                    VK12.VK_IMAGE_ASPECT_DEPTH_BIT,
                    sourceDepth.baseMipLevel(),
                    1,
                    0,
                    1,
                    VulkanHostImageStates.DEPTH_ATTACHMENT,
                    VulkanHostImageStates.DEPTH_ATTACHMENT
            );

            try (MemoryStack stack = MemoryStack.stackPush()) {
                VkRenderingAttachmentInfo.Buffer colourAttachments = VkRenderingAttachmentInfo.calloc(1, stack);
                colourAttachments.get(0)
                        .sType$Default()
                        .imageView(hostColourView.vkImageView())
                        .imageLayout(VK12.VK_IMAGE_LAYOUT_GENERAL)
                        .loadOp(VK12.VK_ATTACHMENT_LOAD_OP_LOAD)
                        .storeOp(VK12.VK_ATTACHMENT_STORE_OP_STORE);
                VkRenderingAttachmentInfo depthAttachment = VkRenderingAttachmentInfo.calloc(stack)
                        .sType$Default()
                        .imageView(hostDepthView.vkImageView())
                        .imageLayout(VK12.VK_IMAGE_LAYOUT_GENERAL)
                        .loadOp(VK12.VK_ATTACHMENT_LOAD_OP_LOAD)
                        .storeOp(VK12.VK_ATTACHMENT_STORE_OP_STORE);
                VkRenderingInfo rendering = VkRenderingInfo.calloc(stack)
                        .sType$Default()
                        .layerCount(1)
                        .viewMask(0)
                        .pColorAttachments(colourAttachments)
                        .pDepthAttachment(depthAttachment);
                rendering.renderArea().offset().set(0, 0);
                rendering.renderArea().extent().set(sourceWidth, sourceHeight);
                KHRDynamicRendering.vkCmdBeginRenderingKHR(commandBuffer, rendering);

                VkViewport.Buffer vkViewport = VkViewport.calloc(1, stack)
                        .x(0.0f)
                        .y(0.0f)
                        .width(sourceWidth)
                        .height(sourceHeight)
                        .minDepth(0.0f)
                        .maxDepth(1.0f);
                VK12.vkCmdSetViewport(commandBuffer, 0, vkViewport);
                VkRect2D.Buffer scissor = VkRect2D.calloc(1, stack);
                scissor.offset().set(0, 0);
                scissor.extent().set(sourceWidth, sourceHeight);
                VK12.vkCmdSetScissor(commandBuffer, 0, scissor);

                VulkanPushDescriptors descriptors = new VulkanPushDescriptors()
                        .sampledImage(
                                0,
                                depthStencil.depthSampledView(),
                                this.depthSampler,
                                VulkanImageStates.SHADER_SAMPLED.layout()
                        )
                        .sampledImage(
                                3,
                                colour.view(),
                                this.colourSampler,
                                VulkanImageStates.SHADER_SAMPLED.layout()
                        );
                ByteBuffer push = this.createPushConstants(stack, viewport);
                activePipeline.bind(commandBuffer, descriptors, push);
                VK12.vkCmdBindIndexBuffer(
                        commandBuffer,
                        this.indexBuffer.buffer().vkBuffer(),
                        0L,
                        VK12.VK_INDEX_TYPE_UINT16
                );
                VK12.vkCmdDrawIndexed(
                        commandBuffer,
                        VulkanFullscreenIndexBuffer.INDEX_COUNT,
                        1,
                        0,
                        0,
                        0
                );
                KHRDynamicRendering.vkCmdEndRenderingKHR(commandBuffer);
            }

            VulkanSync.imageBarrier(
                    commandBuffer,
                    hostColourImage.vkImage(),
                    VK12.VK_IMAGE_ASPECT_COLOR_BIT,
                    sourceColour.baseMipLevel(),
                    1,
                    0,
                    1,
                    VulkanHostImageStates.COLOR_ATTACHMENT,
                    VulkanHostImageStates.COLOR_ATTACHMENT
            );
            VulkanSync.imageBarrier(
                    commandBuffer,
                    hostDepthImage.vkImage(),
                    VK12.VK_IMAGE_ASPECT_DEPTH_BIT,
                    sourceDepth.baseMipLevel(),
                    1,
                    0,
                    1,
                    VulkanHostImageStates.DEPTH_ATTACHMENT,
                    VulkanHostImageStates.DEPTH_ATTACHMENT
            );
        });
    }

    private void ensurePipeline(GpuFormat colourFormat, GpuFormat depthFormat) {
        if (this.pipeline != null
                && this.pipelineColourFormat == colourFormat
                && this.pipelineDepthFormat == depthFormat) {
            return;
        }
        VulkanGraphicsPipeline created = this.createPipeline(colourFormat, depthFormat);
        VulkanGraphicsPipeline retired = this.pipeline;
        this.pipeline = created;
        this.pipelineColourFormat = colourFormat;
        this.pipelineDepthFormat = depthFormat;
        if (retired != null) retired.close();
    }

    private VulkanGraphicsPipeline createPipeline(GpuFormat colourFormat, GpuFormat depthFormat) {
        Map<String, String> defines = new HashMap<>(this.properties.shaderDefines());
        defines.put("EMIT_COLOUR", "");
        if (this.useEnvironmentalFog) defines.put("USE_ENV_FOG", "");
        try (VulkanShaderCompiler compiler = new VulkanShaderCompiler()) {
            VulkanShaderModule vertex = null;
            VulkanShaderModule fragment = null;
            try {
                vertex = compiler.compile("voxy:post/fullscreen.vert", VulkanShaderStage.VERTEX, defines);
                fragment = compiler.compile(
                        "voxy:post/blit_texture_depth_cutout.frag",
                        VulkanShaderStage.FRAGMENT,
                        defines
                );
                VulkanShaderModule ownedVertex = vertex;
                VulkanShaderModule ownedFragment = fragment;
                vertex = null;
                fragment = null;
                return new VulkanGraphicsPipeline(
                        ownedVertex,
                        ownedFragment,
                        new VulkanGraphicsPipeline.State(
                                VK12.VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST,
                                VK12.VK_POLYGON_MODE_FILL,
                                VK12.VK_CULL_MODE_NONE,
                                VK12.VK_FRONT_FACE_COUNTER_CLOCKWISE,
                                true,
                                true,
                                VulkanConst.toVk(this.properties.closerEqualDepthCompare()),
                                VulkanGraphicsPipeline.StencilState.disabled(),
                                VulkanConst.toVk(depthFormat),
                                VK12.VK_FORMAT_UNDEFINED,
                                VK12.VK_SAMPLE_COUNT_1_BIT,
                                false,
                                List.of(VulkanGraphicsPipeline.ColorAttachment.alphaBlend(
                                        VulkanConst.toVk(colourFormat),
                                        COLOR_WRITE_MASK
                                ))
                        ),
                        "Voxy final normal composite " + colourFormat + "/" + depthFormat
                );
            } finally {
                if (vertex != null) vertex.close();
                if (fragment != null) fragment.close();
            }
        }
    }

    private ByteBuffer createPushConstants(MemoryStack stack, VulkanMDICViewport viewport) {
        int size = this.useEnvironmentalFog ? 160 : 128;
        ByteBuffer push = stack.malloc(size).order(ByteOrder.nativeOrder());
        putMatrix(push, 0, new Matrix4f(viewport.MVP).invert());
        putMatrix(push, 64, new Matrix4f(viewport.vanillaProjection).mul(viewport.modelView));
        if (this.useEnvironmentalFog) {
            float start = viewport.fogParameters.environmentalStart();
            float end = viewport.fogParameters.environmentalEnd();
            if (Math.abs(end - start) > 1.0f) {
                float inverseEndFogDelta = 1.0f / (end - start);
                float endDistance = Math.max(VoxyRenderSystem.getRenderDistance(), 20 * 16);
                endDistance *= (float) Math.sqrt(3.0);
                float startDelta = -start * inverseEndFogDelta;
                push.putFloat(128, inverseEndFogDelta);
                push.putFloat(132, startDelta);
                push.putFloat(136, Math.clamp(endDistance * inverseEndFogDelta + startDelta, 0.0f, 1.0f));
                push.putFloat(140, 0.0f);
                push.putFloat(144, viewport.fogParameters.red());
                push.putFloat(148, viewport.fogParameters.green());
                push.putFloat(152, viewport.fogParameters.blue());
                push.putFloat(156, viewport.fogParameters.alpha());
            } else {
                for (int offset = 128; offset < 160; offset += Float.BYTES) {
                    push.putFloat(offset, 0.0f);
                }
            }
        }
        push.position(size).flip();
        return push;
    }

    private static void putMatrix(ByteBuffer target, int offset, Matrix4fc matrix) {
        matrix.get(offset, target);
    }

    private void ensureOpen() {
        if (this.closed) throw new IllegalStateException("Voxy final composite is closed");
    }

    @Override
    public void close() {
        if (this.closed) return;
        this.closed = true;
        if (this.pipeline != null) this.pipeline.close();
        this.indexBuffer.close();
        this.colourSampler.close();
        this.depthSampler.close();
    }
}
