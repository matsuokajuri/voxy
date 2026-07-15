package me.cortex.voxy.client.core.rendering.util;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.textures.AddressMode;
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
import org.lwjgl.vulkan.VkRect2D;
import org.lwjgl.vulkan.VkRenderingAttachmentInfo;
import org.lwjgl.vulkan.VkRenderingInfo;
import org.lwjgl.vulkan.VkViewport;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Vulkan translation of the formal depth-attachment HiZ mip-chain owner. */
public final class VulkanHiZBuffer implements AutoCloseable {
    private static final GpuFormat FORMAT = GpuFormat.D24_UNORM_S8_UINT;
    private static final int DEPTH_ASPECT = VK12.VK_IMAGE_ASPECT_DEPTH_BIT;
    private static final VulkanSync.ImageState HOST_DEPTH_ATTACHMENT = new VulkanSync.ImageState(
            VK12.VK_IMAGE_LAYOUT_GENERAL,
            VulkanImageStates.DEPTH_STENCIL_ATTACHMENT.access()
    );
    private static final VulkanSync.ImageState HOST_DEPTH_SAMPLED = new VulkanSync.ImageState(
            VK12.VK_IMAGE_LAYOUT_GENERAL,
            new VulkanSync.Access(
                    VK13.VK_PIPELINE_STAGE_2_FRAGMENT_SHADER_BIT,
                    VK13.VK_ACCESS_2_SHADER_SAMPLED_READ_BIT
            )
    );

    private final VulkanGraphicsPipeline pipeline;
    private final VoxyVulkanSampler sampler;
    private VoxyVulkanImage image;
    private VoxyVulkanImageView sampledView;
    private final List<VoxyVulkanImageView> mipViews = new ArrayList<>();
    private int levels;
    private int width;
    private int height;
    private boolean closed;

    public VulkanHiZBuffer(RenderProperties properties) {
        Map<String, String> defines = new java.util.HashMap<>(properties.shaderDefines());
        VulkanGraphicsPipeline createdPipeline = null;
        VoxyVulkanSampler createdSampler = null;
        try {
            try (VulkanShaderCompiler compiler = new VulkanShaderCompiler()) {
                VulkanShaderModule vertex = null;
                VulkanShaderModule fragment = null;
                try {
                    vertex = compiler.compile("voxy:hiz/blit.vsh", VulkanShaderStage.VERTEX, defines);
                    fragment = compiler.compile("voxy:hiz/blit.fsh", VulkanShaderStage.FRAGMENT, defines);
                    VulkanShaderModule ownedVertex = vertex;
                    VulkanShaderModule ownedFragment = fragment;
                    vertex = null;
                    fragment = null;
                    createdPipeline = new VulkanGraphicsPipeline(
                            ownedVertex,
                            ownedFragment,
                            new VulkanGraphicsPipeline.State(
                                    VK12.VK_PRIMITIVE_TOPOLOGY_TRIANGLE_FAN,
                                    VK12.VK_POLYGON_MODE_FILL,
                                    VK12.VK_CULL_MODE_NONE,
                                    VK12.VK_FRONT_FACE_COUNTER_CLOCKWISE,
                                    true,
                                    true,
                                    VK12.VK_COMPARE_OP_ALWAYS,
                                    VulkanGraphicsPipeline.StencilState.disabled(),
                                    VulkanConst.toVk(FORMAT),
                                    VK12.VK_FORMAT_UNDEFINED,
                                    VK12.VK_SAMPLE_COUNT_1_BIT,
                                    false,
                                    List.of()
                            ),
                            "Voxy HiZ mip builder"
                    );
                } finally {
                    if (vertex != null) vertex.close();
                    if (fragment != null) fragment.close();
                }
            }
            createdSampler = VoxyVulkanSampler.nearestMipmapNearest(
                    AddressMode.CLAMP_TO_EDGE,
                    AddressMode.CLAMP_TO_EDGE
            );
        } catch (RuntimeException | Error exception) {
            if (createdSampler != null) createdSampler.close();
            if (createdPipeline != null) createdPipeline.close();
            throw exception;
        }
        this.pipeline = createdPipeline;
        this.sampler = createdSampler;
    }

    public void buildMipChain(GpuTextureView sourceDepth, int screenWidth, int screenHeight) {
        this.ensureOpen();
        if (sourceDepth.isClosed() || !sourceDepth.texture().getFormat().hasDepthAspect()) {
            throw new IllegalArgumentException("Voxy HiZ source must be an open depth image view");
        }
        VulkanGpuTextureView hostSourceView = sourceDepth instanceof VulkanGpuTextureView view ? view : null;
        VoxyVulkanImageView voxySourceView = sourceDepth instanceof VoxyVulkanImageView view ? view : null;
        if (hostSourceView == null && voxySourceView == null) {
            throw new IllegalArgumentException("Voxy HiZ requires a Vulkan depth image view");
        }
        if (voxySourceView != null
                && (voxySourceView.mipLevelCount() != 1
                || voxySourceView.layerCount() != 1
                || (voxySourceView.aspectMask() & DEPTH_ASPECT) == 0)) {
            throw new IllegalArgumentException("Voxy HiZ source must select exactly one depth mip and array layer");
        }
        int targetWidth = Integer.highestOneBit(screenWidth);
        int targetHeight = Integer.highestOneBit(screenHeight);
        if (Math.max(targetWidth, targetHeight) <= 1) {
            throw new IllegalArgumentException("Voxy HiZ dimensions are too small: " + screenWidth + "x" + screenHeight);
        }
        if (this.width != targetWidth || this.height != targetHeight) {
            this.allocate(targetWidth, targetHeight);
        }

        VulkanGpuTexture hostSourceImage = hostSourceView == null ? null : hostSourceView.texture();
        VoxyVulkanImage voxySourceImage = voxySourceView == null ? null : voxySourceView.image();
        VulkanCommandRecorder.record(commandBuffer -> {
            if (hostSourceImage != null) {
                VulkanSync.imageBarrier(
                        commandBuffer,
                        hostSourceImage.vkImage(),
                        DEPTH_ASPECT,
                        sourceDepth.baseMipLevel(),
                        1,
                        0,
                        1,
                        HOST_DEPTH_ATTACHMENT,
                        HOST_DEPTH_SAMPLED
                );
            } else {
                voxySourceImage.transition(
                        commandBuffer,
                        sourceDepth.baseMipLevel(),
                        1,
                        voxySourceView.baseArrayLayer(),
                        1,
                        VulkanImageStates.SHADER_SAMPLED
                );
            }

            int mipWidth = this.width;
            int mipHeight = this.height;
            for (int mip = 0; mip < this.levels; mip++) {
                this.image.transition(commandBuffer, mip, 1, 0, 1, VulkanImageStates.DEPTH_STENCIL_ATTACHMENT);
                GpuTextureView inputView = mip == 0 ? sourceDepth : this.mipViews.get(mip - 1);
                int inputLayout = mip == 0
                        ? hostSourceImage == null
                                ? VulkanImageStates.SHADER_SAMPLED.layout()
                                : VK12.VK_IMAGE_LAYOUT_GENERAL
                        : VulkanImageStates.SHADER_SAMPLED.layout();
                VulkanPushDescriptors descriptors = new VulkanPushDescriptors()
                        .sampledImage(0, inputView, this.sampler, inputLayout);
                this.drawMip(commandBuffer, mip, mipWidth, mipHeight, descriptors);
                this.image.transition(commandBuffer, mip, 1, 0, 1, VulkanImageStates.SHADER_SAMPLED);
                mipWidth = Math.max(mipWidth / 2, 1);
                mipHeight = Math.max(mipHeight / 2, 1);
            }

            if (hostSourceImage != null) {
                VulkanSync.imageBarrier(
                        commandBuffer,
                        hostSourceImage.vkImage(),
                        DEPTH_ASPECT,
                        sourceDepth.baseMipLevel(),
                        1,
                        0,
                        1,
                        HOST_DEPTH_SAMPLED,
                        HOST_DEPTH_ATTACHMENT
                );
            } else {
                voxySourceImage.transition(
                        commandBuffer,
                        sourceDepth.baseMipLevel(),
                        1,
                        voxySourceView.baseArrayLayer(),
                        1,
                        VulkanImageStates.DEPTH_STENCIL_ATTACHMENT
                );
            }
        });
    }

    private void drawMip(
            org.lwjgl.vulkan.VkCommandBuffer commandBuffer,
            int mip,
            int mipWidth,
            int mipHeight,
            VulkanPushDescriptors descriptors
    ) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkRenderingAttachmentInfo depthAttachment = VkRenderingAttachmentInfo.calloc(stack)
                    .sType$Default()
                    .imageView(this.mipViews.get(mip).vkImageView())
                    .imageLayout(VulkanImageStates.DEPTH_STENCIL_ATTACHMENT.layout())
                    .loadOp(VK12.VK_ATTACHMENT_LOAD_OP_DONT_CARE)
                    .storeOp(VK12.VK_ATTACHMENT_STORE_OP_STORE);
            VkRenderingInfo rendering = VkRenderingInfo.calloc(stack).sType$Default()
                    .layerCount(1)
                    .viewMask(0)
                    .pDepthAttachment(depthAttachment);
            rendering.renderArea().offset().set(0, 0);
            rendering.renderArea().extent().set(mipWidth, mipHeight);
            KHRDynamicRendering.vkCmdBeginRenderingKHR(commandBuffer, rendering);

            VkViewport.Buffer viewport = VkViewport.calloc(1, stack)
                    .x(0.0f)
                    .y(0.0f)
                    .width(mipWidth)
                    .height(mipHeight)
                    .minDepth(0.0f)
                    .maxDepth(1.0f);
            VK12.vkCmdSetViewport(commandBuffer, 0, viewport);
            VkRect2D.Buffer scissor = VkRect2D.calloc(1, stack);
            scissor.offset().set(0, 0);
            scissor.extent().set(mipWidth, mipHeight);
            VK12.vkCmdSetScissor(commandBuffer, 0, scissor);
            this.pipeline.bind(commandBuffer, descriptors, null);
            VK12.vkCmdDraw(commandBuffer, 4, 1, 0, 0);
            KHRDynamicRendering.vkCmdEndRenderingKHR(commandBuffer);
        }
    }

    private void allocate(int width, int height) {
        int createdLevels = (int) Math.ceil(Math.log(Math.max(width, height)) / Math.log(2.0));
        VoxyVulkanImage createdImage = null;
        VoxyVulkanImageView createdSampledView = null;
        List<VoxyVulkanImageView> createdMipViews = new ArrayList<>(createdLevels);
        try {
            createdImage = new VoxyVulkanImage(
                    "Voxy HiZ",
                    GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_RENDER_ATTACHMENT,
                    0,
                    FORMAT,
                    width,
                    height,
                    1,
                    createdLevels
            );
            createdSampledView = createdImage.createView(0, createdLevels, DEPTH_ASPECT);
            for (int mip = 0; mip < createdLevels; mip++) {
                createdMipViews.add(createdImage.createView(mip, 1, DEPTH_ASPECT));
            }
        } catch (RuntimeException | Error exception) {
            for (VoxyVulkanImageView view : createdMipViews) view.close();
            if (createdSampledView != null) createdSampledView.close();
            if (createdImage != null) createdImage.close();
            throw exception;
        }

        this.releaseImage();
        this.image = createdImage;
        this.sampledView = createdSampledView;
        this.mipViews.addAll(createdMipViews);
        this.levels = createdLevels;
        this.width = width;
        this.height = height;
    }

    public VoxyVulkanImageView sampledView() {
        this.ensureOpen();
        if (this.sampledView == null) throw new IllegalStateException("Voxy HiZ has not been built yet");
        return this.sampledView;
    }

    public int getPackedLevels() {
        return (this.width << 16) | this.height;
    }

    private void releaseImage() {
        if (this.sampledView != null) {
            this.sampledView.close();
            this.sampledView = null;
        }
        for (VoxyVulkanImageView view : this.mipViews) view.close();
        this.mipViews.clear();
        if (this.image != null) {
            this.image.close();
            this.image = null;
        }
    }

    private void ensureOpen() {
        if (this.closed) throw new IllegalStateException("Voxy Vulkan HiZ buffer is closed");
    }

    @Override
    public void close() {
        if (this.closed) return;
        this.closed = true;
        this.releaseImage();
        this.sampler.close();
        this.pipeline.close();
    }
}
