package me.cortex.voxy.client.core.rendering.util;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.textures.AddressMode;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vulkan.VulkanConst;
import com.mojang.blaze3d.vulkan.VulkanGpuTexture;
import com.mojang.blaze3d.vulkan.VulkanGpuTextureView;
import me.cortex.voxy.client.core.RenderProperties;
import me.cortex.voxy.client.core.vulkan.VoxyVulkanImage;
import me.cortex.voxy.client.core.vulkan.VoxyVulkanImageView;
import me.cortex.voxy.client.core.vulkan.VoxyVulkanSampler;
import me.cortex.voxy.client.core.vulkan.VulkanCommandRecorder;
import me.cortex.voxy.client.core.vulkan.VulkanImageStates;
import me.cortex.voxy.client.core.vulkan.VulkanSync;
import me.cortex.voxy.client.core.vulkan.shader.VulkanGraphicsPipeline;
import me.cortex.voxy.client.core.vulkan.shader.VulkanPushDescriptors;
import me.cortex.voxy.client.core.vulkan.shader.VulkanShaderCompiler;
import me.cortex.voxy.client.core.vulkan.shader.VulkanShaderModule;
import me.cortex.voxy.client.core.vulkan.shader.VulkanShaderStage;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.KHRDynamicRendering;
import org.lwjgl.vulkan.VK12;
import org.lwjgl.vulkan.VK13;
import org.lwjgl.vulkan.VkClearValue;
import org.lwjgl.vulkan.VkRect2D;
import org.lwjgl.vulkan.VkRenderingAttachmentInfo;
import org.lwjgl.vulkan.VkRenderingInfo;
import org.lwjgl.vulkan.VkViewport;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;

/** Formal D24S8 mask target used by the original normal render pipeline. */
public final class VulkanDepthStencilTarget implements AutoCloseable {
    private static final GpuFormat FORMAT = GpuFormat.D24_UNORM_S8_UINT;
    private static final int DEPTH_ASPECT = VK12.VK_IMAGE_ASPECT_DEPTH_BIT;
    private static final int DEPTH_STENCIL_ASPECTS =
            VK12.VK_IMAGE_ASPECT_DEPTH_BIT | VK12.VK_IMAGE_ASPECT_STENCIL_BIT;
    private static final VulkanSync.ImageState HOST_DEPTH_ATTACHMENT = new VulkanSync.ImageState(
            VK12.VK_IMAGE_LAYOUT_GENERAL,
            // Minecraft may leave an otherwise untouched frame depth image at its transfer clear.
            // Cover both legal host producers before Voxy samples the selected depth mip.
            VulkanImageStates.DEPTH_STENCIL_ATTACHMENT.access().or(VulkanSync.TRANSFER_WRITE)
    );
    private static final VulkanSync.ImageState HOST_DEPTH_SAMPLED = new VulkanSync.ImageState(
            VK12.VK_IMAGE_LAYOUT_GENERAL,
            new VulkanSync.Access(
                    VK13.VK_PIPELINE_STAGE_2_FRAGMENT_SHADER_BIT,
                    VK13.VK_ACCESS_2_SHADER_SAMPLED_READ_BIT
            )
    );

    private final RenderProperties properties;
    private final VulkanGraphicsPipeline setupPipeline;
    private final VoxyVulkanSampler sourceSampler;
    private VoxyVulkanImage image;
    private VoxyVulkanImageView attachmentView;
    private VoxyVulkanImageView depthSampledView;
    private int width;
    private int height;
    private boolean closed;

    public VulkanDepthStencilTarget(RenderProperties properties) {
        this.properties = properties;
        Map<String, String> defines = new HashMap<>(properties.shaderDefines());
        VulkanGraphicsPipeline createdPipeline = null;
        VoxyVulkanSampler createdSampler = null;
        try {
            try (VulkanShaderCompiler compiler = new VulkanShaderCompiler()) {
                VulkanShaderModule vertex = null;
                VulkanShaderModule fragment = null;
                try {
                    vertex = compiler.compile("voxy:post/fullscreen2.vert", VulkanShaderStage.VERTEX, defines);
                    fragment = compiler.compile(
                            "voxy:post/setup_stencil_depth.frag",
                            VulkanShaderStage.FRAGMENT,
                            defines
                    );
                    VulkanShaderModule ownedVertex = vertex;
                    VulkanShaderModule ownedFragment = fragment;
                    vertex = null;
                    fragment = null;
                    createdPipeline = createSetupPipeline(properties, ownedVertex, ownedFragment);
                } finally {
                    if (vertex != null) vertex.close();
                    if (fragment != null) fragment.close();
                }
            }
            createdSampler = new VoxyVulkanSampler(
                    AddressMode.REPEAT,
                    AddressMode.REPEAT,
                    FilterMode.NEAREST,
                    FilterMode.NEAREST,
                    1,
                    OptionalDouble.of(0.0)
            );
        } catch (RuntimeException | Error exception) {
            if (createdSampler != null) createdSampler.close();
            if (createdPipeline != null) createdPipeline.close();
            throw exception;
        }
        this.setupPipeline = createdPipeline;
        this.sourceSampler = createdSampler;
    }

    private static VulkanGraphicsPipeline createSetupPipeline(
            RenderProperties properties,
            VulkanShaderModule vertex,
            VulkanShaderModule fragment
    ) {
            VulkanGraphicsPipeline.StencilFace replaceWithZero = new VulkanGraphicsPipeline.StencilFace(
                    VK12.VK_STENCIL_OP_KEEP,
                    VK12.VK_STENCIL_OP_REPLACE,
                    VK12.VK_STENCIL_OP_KEEP,
                    VK12.VK_COMPARE_OP_ALWAYS,
                    0xFF,
                    0xFF,
                    0
            );
            int format = VulkanConst.toVk(FORMAT);
            return new VulkanGraphicsPipeline(
                    vertex,
                    fragment,
                    new VulkanGraphicsPipeline.State(
                            VK12.VK_PRIMITIVE_TOPOLOGY_TRIANGLE_FAN,
                            VK12.VK_POLYGON_MODE_FILL,
                            VK12.VK_CULL_MODE_NONE,
                            VK12.VK_FRONT_FACE_COUNTER_CLOCKWISE,
                            true,
                            true,
                            VK12.VK_COMPARE_OP_ALWAYS,
                            VulkanGraphicsPipeline.StencilState.bothFaces(replaceWithZero),
                            format,
                            format,
                            VK12.VK_SAMPLE_COUNT_1_BIT,
                            false,
                            List.of()
                    ),
                    "Voxy depth/stencil Minecraft mask"
            );
    }

    public void setup(
            GpuTextureView sourceDepth,
            int sourceWidth,
            int sourceHeight,
            int targetWidth,
            int targetHeight
    ) {
        this.ensureOpen();
        if (!(sourceDepth instanceof VulkanGpuTextureView sourceView)) {
            throw new IllegalArgumentException("Voxy depth/stencil setup requires Minecraft's Vulkan depth view");
        }
        if (sourceDepth.isClosed()
                || !sourceDepth.texture().getFormat().hasDepthAspect()
                || sourceDepth.texture().getDepthOrLayers() != 1
                || sourceDepth.mipLevels() != 1) {
            throw new IllegalArgumentException(
                    "Voxy depth/stencil setup source must be an open, single-layer, single-mip depth view"
            );
        }
        if (sourceWidth <= 0 || sourceHeight <= 0 || targetWidth <= 0 || targetHeight <= 0) {
            throw new IllegalArgumentException("Voxy depth/stencil target dimensions must be positive");
        }
        if (sourceWidth != sourceDepth.getWidth(0) || sourceHeight != sourceDepth.getHeight(0)) {
            throw new IllegalArgumentException("Voxy depth/stencil source dimensions must match the selected depth view");
        }
        if (this.width != targetWidth || this.height != targetHeight) {
            this.allocate(targetWidth, targetHeight);
        }

        VulkanGpuTexture sourceImage = sourceView.texture();
        VulkanCommandRecorder.record(commandBuffer -> {
            VulkanSync.imageBarrier(
                    commandBuffer,
                    sourceImage.vkImage(),
                    DEPTH_ASPECT,
                    sourceDepth.baseMipLevel(),
                    1,
                    0,
                    1,
                    HOST_DEPTH_ATTACHMENT,
                    HOST_DEPTH_SAMPLED
            );
            this.image.transitionAndSynchronize(
                    commandBuffer, 0, 1, 0, 1, VulkanImageStates.DEPTH_STENCIL_ATTACHMENT
            );

            try (MemoryStack stack = MemoryStack.stackPush()) {
                VkClearValue depthStencilClear = VkClearValue.calloc(stack);
                depthStencilClear.depthStencil()
                        .depth(this.properties.clearDepth())
                        .stencil(1);
                VkRenderingAttachmentInfo depthAttachment = VkRenderingAttachmentInfo.calloc(stack)
                        .sType$Default()
                        .imageView(this.attachmentView.vkImageView())
                        .imageLayout(VulkanImageStates.DEPTH_STENCIL_ATTACHMENT.layout())
                        .loadOp(VK12.VK_ATTACHMENT_LOAD_OP_CLEAR)
                        .storeOp(VK12.VK_ATTACHMENT_STORE_OP_STORE)
                        .clearValue(depthStencilClear);
                VkRenderingAttachmentInfo stencilAttachment = VkRenderingAttachmentInfo.calloc(stack)
                        .sType$Default()
                        .imageView(this.attachmentView.vkImageView())
                        .imageLayout(VulkanImageStates.DEPTH_STENCIL_ATTACHMENT.layout())
                        .loadOp(VK12.VK_ATTACHMENT_LOAD_OP_CLEAR)
                        .storeOp(VK12.VK_ATTACHMENT_STORE_OP_STORE)
                        .clearValue(depthStencilClear);
                VkRenderingInfo rendering = VkRenderingInfo.calloc(stack)
                        .sType$Default()
                        .layerCount(1)
                        .viewMask(0)
                        .pDepthAttachment(depthAttachment)
                        .pStencilAttachment(stencilAttachment);
                rendering.renderArea().offset().set(0, 0);
                rendering.renderArea().extent().set(this.width, this.height);
                KHRDynamicRendering.vkCmdBeginRenderingKHR(commandBuffer, rendering);

                VkViewport.Buffer viewport = VkViewport.calloc(1, stack)
                        .x(0.0f)
                        .y(0.0f)
                        .width(this.width)
                        .height(this.height)
                        .minDepth(0.0f)
                        .maxDepth(1.0f);
                VK12.vkCmdSetViewport(commandBuffer, 0, viewport);
                VkRect2D.Buffer scissor = VkRect2D.calloc(1, stack);
                scissor.offset().set(0, 0);
                scissor.extent().set(this.width, this.height);
                VK12.vkCmdSetScissor(commandBuffer, 0, scissor);

                VulkanPushDescriptors descriptors = new VulkanPushDescriptors()
                        .sampledImage(0, sourceDepth, this.sourceSampler, VK12.VK_IMAGE_LAYOUT_GENERAL);
                ByteBuffer push = stack.malloc(2 * Float.BYTES).order(ByteOrder.nativeOrder());
                push.putFloat(this.width / (float) sourceWidth)
                        .putFloat(this.height / (float) sourceHeight)
                        .flip();
                this.setupPipeline.bind(commandBuffer, descriptors, push);
                VK12.vkCmdDraw(commandBuffer, 4, 1, 0, 0);
                KHRDynamicRendering.vkCmdEndRenderingKHR(commandBuffer);
            }

            VulkanSync.imageBarrier(
                    commandBuffer,
                    sourceImage.vkImage(),
                    DEPTH_ASPECT,
                    sourceDepth.baseMipLevel(),
                    1,
                    0,
                    1,
                    HOST_DEPTH_SAMPLED,
                    HOST_DEPTH_ATTACHMENT
            );
        });
    }

    private void allocate(int width, int height) {
        VoxyVulkanImage createdImage = null;
        VoxyVulkanImageView createdAttachment = null;
        VoxyVulkanImageView createdDepthSampled = null;
        try {
            createdImage = new VoxyVulkanImage(
                    "Voxy normal depth/stencil",
                    GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_RENDER_ATTACHMENT,
                    0,
                    FORMAT,
                    width,
                    height,
                    1,
                    1
            );
            createdAttachment = createdImage.createView(0, 1, DEPTH_STENCIL_ASPECTS);
            createdDepthSampled = createdImage.createView(0, 1, DEPTH_ASPECT);
        } catch (RuntimeException | Error exception) {
            if (createdDepthSampled != null) createdDepthSampled.close();
            if (createdAttachment != null) createdAttachment.close();
            if (createdImage != null) createdImage.close();
            throw exception;
        }

        this.releaseImage();
        this.image = createdImage;
        this.attachmentView = createdAttachment;
        this.depthSampledView = createdDepthSampled;
        this.width = width;
        this.height = height;
    }

    public VoxyVulkanImage image() {
        this.ensureAllocated();
        return this.image;
    }

    public VoxyVulkanImageView attachmentView() {
        this.ensureAllocated();
        return this.attachmentView;
    }

    public VoxyVulkanImageView depthSampledView() {
        this.ensureAllocated();
        return this.depthSampledView;
    }

    public int width() {
        return this.width;
    }

    public int height() {
        return this.height;
    }

    private void ensureAllocated() {
        this.ensureOpen();
        if (this.image == null || this.attachmentView == null || this.depthSampledView == null) {
            throw new IllegalStateException("Voxy depth/stencil target is not allocated");
        }
    }

    private void releaseImage() {
        if (this.attachmentView != null) {
            this.attachmentView.close();
            this.attachmentView = null;
        }
        if (this.depthSampledView != null) {
            this.depthSampledView.close();
            this.depthSampledView = null;
        }
        if (this.image != null) {
            this.image.close();
            this.image = null;
        }
    }

    private void ensureOpen() {
        if (this.closed) throw new IllegalStateException("Voxy Vulkan depth/stencil target is closed");
    }

    @Override
    public void close() {
        if (this.closed) return;
        this.closed = true;
        this.releaseImage();
        this.sourceSampler.close();
        this.setupPipeline.close();
    }
}
