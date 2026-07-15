package me.cortex.voxy.client.core.rendering.bounding;

import com.mojang.blaze3d.vulkan.VulkanConst;
import me.cortex.voxy.client.core.RenderProperties;
import me.cortex.voxy.client.core.rendering.VulkanViewport;
import me.cortex.voxy.client.core.rendering.util.VulkanBoundingIndexBuffer;
import me.cortex.voxy.client.core.rendering.util.VulkanDepthBoundingTarget;
import me.cortex.voxy.client.core.vulkan.VoxyVulkanBuffer;
import me.cortex.voxy.client.core.vulkan.VoxyVulkanBufferUsage;
import me.cortex.voxy.client.core.vulkan.VulkanCommandRecorder;
import me.cortex.voxy.client.core.vulkan.VulkanImageStates;
import me.cortex.voxy.client.core.vulkan.VulkanUploadStream;
import me.cortex.voxy.client.core.vulkan.shader.VulkanGraphicsPipeline;
import me.cortex.voxy.client.core.vulkan.shader.VulkanPushDescriptors;
import me.cortex.voxy.client.core.vulkan.shader.VulkanShaderCompiler;
import me.cortex.voxy.client.core.vulkan.shader.VulkanShaderModule;
import me.cortex.voxy.client.core.vulkan.shader.VulkanShaderStage;
import net.minecraft.client.Minecraft;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector3i;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.KHRDynamicRendering;
import org.lwjgl.vulkan.VK12;
import org.lwjgl.vulkan.VkRect2D;
import org.lwjgl.vulkan.VkRenderingAttachmentInfo;
import org.lwjgl.vulkan.VkRenderingInfo;
import org.lwjgl.vulkan.VkViewport;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Vulkan translation of BoundRenderer's depth-only packed-AABB raster pass. */
public final class VulkanBoundRenderer implements AutoCloseable {
    private static final int UNIFORM_BYTES = 128;

    private final RenderProperties properties;
    private final VulkanGraphicsPipeline pipeline;
    private final VoxyVulkanBuffer uniformBuffer;
    private final VulkanBoundingIndexBuffer indexBuffer;
    private final VulkanUploadStream uploads = new VulkanUploadStream();
    private boolean closed;

    public VulkanBoundRenderer(RenderProperties properties) {
        this.properties = Objects.requireNonNull(properties, "properties");
        Map<String, String> defines = new HashMap<>(properties.shaderDefines());

        VulkanGraphicsPipeline createdPipeline = null;
        VoxyVulkanBuffer createdUniform = null;
        VulkanBoundingIndexBuffer createdIndices = null;
        try {
            try (VulkanShaderCompiler compiler = new VulkanShaderCompiler()) {
                VulkanShaderModule vertex = null;
                VulkanShaderModule fragment = null;
                try {
                    vertex = compiler.compile(
                            "voxy:chunkoutline/outline.vsh",
                            VulkanShaderStage.VERTEX,
                            defines
                    );
                    fragment = compiler.compile(
                            "voxy:chunkoutline/outline.fsh",
                            VulkanShaderStage.FRAGMENT,
                            defines
                    );
                    VulkanShaderModule ownedVertex = vertex;
                    VulkanShaderModule ownedFragment = fragment;
                    vertex = null;
                    fragment = null;
                    createdPipeline = new VulkanGraphicsPipeline(
                            ownedVertex,
                            ownedFragment,
                            new VulkanGraphicsPipeline.State(
                                    VK12.VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST,
                                    VK12.VK_POLYGON_MODE_FILL,
                                    VK12.VK_CULL_MODE_BACK_BIT,
                                    VK12.VK_FRONT_FACE_CLOCKWISE,
                                    true,
                                    true,
                                    furtherCompare(properties),
                                    VulkanGraphicsPipeline.StencilState.disabled(),
                                    VulkanConst.toVk(VulkanDepthBoundingTarget.FORMAT),
                                    VK12.VK_FORMAT_UNDEFINED,
                                    VK12.VK_SAMPLE_COUNT_1_BIT,
                                    false,
                                    List.of()
                            ),
                            "Voxy chunk depth bounds"
                    );
                } finally {
                    if (vertex != null) vertex.close();
                    if (fragment != null) fragment.close();
                }
            }
            createdUniform = VoxyVulkanBuffer.create(
                    UNIFORM_BYTES,
                    VoxyVulkanBufferUsage.of(
                            VoxyVulkanBufferUsage.UNIFORM
                                    | VoxyVulkanBufferUsage.TRANSFER_DESTINATION
                    ),
                    "Voxy chunk-bound scene uniform"
            );
            createdIndices = new VulkanBoundingIndexBuffer();
        } catch (RuntimeException | Error exception) {
            if (createdIndices != null) createdIndices.close();
            if (createdUniform != null) createdUniform.close();
            if (createdPipeline != null) createdPipeline.close();
            throw exception;
        }
        this.pipeline = createdPipeline;
        this.uniformBuffer = createdUniform;
        this.indexBuffer = createdIndices;
    }

    public void render(VulkanViewport<?> viewport, VulkanBoundStore store) {
        this.ensureOpen();
        Objects.requireNonNull(viewport, "viewport");
        Objects.requireNonNull(store, "store");
        if (viewport.width <= 0 || viewport.height <= 0) {
            throw new IllegalArgumentException("Voxy bound target requires positive viewport dimensions");
        }

        store.preRender(viewport);
        int count = store.getCount();
        VulkanDepthBoundingTarget target = viewport.depthBoundingBuffer;
        target.clear(this.properties.inverseClearDepth());
        if (count == 0) return;

        this.uploadUniform(viewport);
        this.uploads.commit();
        VoxyVulkanBuffer positions = store.getBuffer();
        VulkanPushDescriptors descriptors = new VulkanPushDescriptors()
                .uniformBuffer(0, this.uniformBuffer, 0L, UNIFORM_BYTES)
                .storageBuffer(1, positions, 0L, positions.size());

        VulkanCommandRecorder.record(commandBuffer -> {
            target.image().transition(
                    commandBuffer,
                    0,
                    1,
                    0,
                    1,
                    VulkanImageStates.DEPTH_STENCIL_ATTACHMENT
            );
            try (MemoryStack stack = MemoryStack.stackPush()) {
                VkRenderingAttachmentInfo depthAttachment = VkRenderingAttachmentInfo.calloc(stack)
                        .sType$Default()
                        .imageView(target.sampledView().vkImageView())
                        .imageLayout(VulkanImageStates.DEPTH_STENCIL_ATTACHMENT.layout())
                        .loadOp(VK12.VK_ATTACHMENT_LOAD_OP_LOAD)
                        .storeOp(VK12.VK_ATTACHMENT_STORE_OP_STORE);
                VkRenderingInfo rendering = VkRenderingInfo.calloc(stack)
                        .sType$Default()
                        .layerCount(1)
                        .viewMask(0)
                        .pDepthAttachment(depthAttachment);
                rendering.renderArea().offset().set(0, 0);
                rendering.renderArea().extent().set(viewport.width, viewport.height);
                KHRDynamicRendering.vkCmdBeginRenderingKHR(commandBuffer, rendering);

                VkViewport.Buffer vkViewport = VkViewport.calloc(1, stack)
                        .x(0.0f)
                        .y(0.0f)
                        .width(viewport.width)
                        .height(viewport.height)
                        .minDepth(0.0f)
                        .maxDepth(1.0f);
                VK12.vkCmdSetViewport(commandBuffer, 0, vkViewport);
                VkRect2D.Buffer scissor = VkRect2D.calloc(1, stack);
                scissor.offset().set(0, 0);
                scissor.extent().set(viewport.width, viewport.height);
                VK12.vkCmdSetScissor(commandBuffer, 0, scissor);

                this.pipeline.bind(commandBuffer, descriptors, null);
                VK12.vkCmdBindIndexBuffer(
                        commandBuffer,
                        this.indexBuffer.buffer().vkBuffer(),
                        0L,
                        VK12.VK_INDEX_TYPE_UINT16
                );
                if (count >= VulkanBoundingIndexBuffer.CUBES_PER_BATCH) {
                    VK12.vkCmdDrawIndexed(
                            commandBuffer,
                            VulkanBoundingIndexBuffer.INDICES_PER_CUBE
                                    * VulkanBoundingIndexBuffer.CUBES_PER_BATCH,
                            count / VulkanBoundingIndexBuffer.CUBES_PER_BATCH,
                            0,
                            0,
                            0
                    );
                }
                int remainder = count % VulkanBoundingIndexBuffer.CUBES_PER_BATCH;
                if (remainder != 0) {
                    VK12.vkCmdDrawIndexed(
                            commandBuffer,
                            VulkanBoundingIndexBuffer.INDICES_PER_CUBE * remainder,
                            1,
                            0,
                            0,
                            (count / VulkanBoundingIndexBuffer.CUBES_PER_BATCH)
                                    * VulkanBoundingIndexBuffer.CUBES_PER_BATCH
                    );
                }
                KHRDynamicRendering.vkCmdEndRenderingKHR(commandBuffer);
            }
            target.image().transition(
                    commandBuffer,
                    0,
                    1,
                    0,
                    1,
                    VulkanImageStates.SHADER_SAMPLED
            );
        });
        store.postRender(viewport);
    }

    private void uploadUniform(VulkanViewport<?> viewport) {
        long pointer = this.uploads.upload(this.uniformBuffer, 0L, UNIFORM_BYTES).address();
        long matrixPointer = pointer;
        pointer += 4L * 4L * Float.BYTES;

        int blockX = (int) Math.floor(viewport.cameraX);
        int blockY = (int) Math.floor(viewport.cameraY);
        int blockZ = (int) Math.floor(viewport.cameraZ);
        new Vector3i(blockX, blockY, blockZ).getToAddress(pointer);
        pointer += 4L * Integer.BYTES;

        Vector3f innerBlock = new Vector3f(
                (float) (viewport.cameraX - blockX),
                (float) (viewport.cameraY - blockY),
                (float) (viewport.cameraZ - blockZ)
        );
        innerBlock.getToAddress(pointer);
        pointer += 3L * Float.BYTES;
        MemoryUtil.memPutFloat(
                pointer,
                Minecraft.getInstance().options.getEffectiveRenderDistance() * 16.0f
        );

        new Matrix4f(viewport.MVP)
                .translate(-innerBlock.x, -innerBlock.y, -innerBlock.z)
                .getToAddress(matrixPointer);
    }

    private static int furtherCompare(RenderProperties properties) {
        return VulkanConst.toVk(properties.furtherDepthCompare());
    }

    private void ensureOpen() {
        if (this.closed) throw new IllegalStateException("Voxy Vulkan bound renderer is closed");
    }

    @Override
    public void close() {
        if (this.closed) return;
        this.closed = true;
        this.uploads.commit();
        this.indexBuffer.close();
        this.uniformBuffer.close();
        this.pipeline.close();
    }
}
