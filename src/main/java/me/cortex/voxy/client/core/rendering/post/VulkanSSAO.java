package me.cortex.voxy.client.core.rendering.post;

import com.mojang.blaze3d.textures.AddressMode;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vulkan.VulkanGpuTexture;
import com.mojang.blaze3d.vulkan.VulkanGpuTextureView;
import me.cortex.voxy.client.config.VoxyConfig;
import me.cortex.voxy.client.core.RenderProperties;
import me.cortex.voxy.client.core.SSAO;
import me.cortex.voxy.client.core.rendering.section.backend.mdic.VulkanMDICViewport;
import me.cortex.voxy.client.core.rendering.util.VulkanColorTarget;
import me.cortex.voxy.client.core.rendering.util.VulkanDepthStencilTarget;
import me.cortex.voxy.client.core.vulkan.VoxyVulkanContext;
import me.cortex.voxy.client.core.vulkan.VoxyVulkanSampler;
import me.cortex.voxy.client.core.vulkan.VulkanCommandRecorder;
import me.cortex.voxy.client.core.vulkan.VulkanHostImageStates;
import me.cortex.voxy.client.core.vulkan.VulkanImageStates;
import me.cortex.voxy.client.core.vulkan.VulkanSync;
import me.cortex.voxy.client.core.vulkan.shader.VulkanComputePipeline;
import me.cortex.voxy.client.core.vulkan.shader.VulkanPushDescriptors;
import me.cortex.voxy.client.core.vulkan.shader.VulkanShaderCompiler;
import me.cortex.voxy.client.core.vulkan.shader.VulkanShaderModule;
import me.cortex.voxy.client.core.vulkan.shader.VulkanShaderStage;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VK12;
import org.lwjgl.vulkan.VkPhysicalDeviceMemoryProperties;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalDouble;

/** Vulkan translation of the original {@link SSAO} compute owner. */
public final class VulkanSSAO implements AutoCloseable {
    private static final int NVIDIA_VENDOR_ID = 0x10DE;
    private static final int AMD_VENDOR_ID = 0x1002;
    private static final long BASIC_MEMORY_THRESHOLD = 2_500_000_000L;
    private static final long BEST_MEMORY_THRESHOLD = 7_000_000_000L;

    private final SSAO.SSAOMode mode;
    private final boolean better;
    private final int samples;
    private final VulkanComputePipeline pipeline;
    private final VoxyVulkanSampler colourSampler;
    private final VoxyVulkanSampler depthSampler;
    private boolean closed;

    public VulkanSSAO(RenderProperties properties) {
        Objects.requireNonNull(properties, "properties");
        this.mode = selectMode(VoxyConfig.CONFIG.getSSAOMode());
        this.better = this.mode != SSAO.SSAOMode.BASIC;
        this.samples = switch (this.mode) {
            case BASIC -> 0;
            case BETTER -> 12;
            case BEST -> 24;
            case AUTO -> throw new IllegalStateException("Vulkan SSAO AUTO mode was not resolved");
        };

        VulkanComputePipeline createdPipeline = null;
        VoxyVulkanSampler createdColourSampler = null;
        VoxyVulkanSampler createdDepthSampler = null;
        try {
            Map<String, String> defines = new HashMap<>(properties.shaderDefines());
            Map<String, String> replacements = Map.of();
            if (this.better) {
                defines.put("BETTER_SSAO", "");
                defines.put("SSAO_STEPS", Integer.toString(this.samples));
                defines.put("USE_GENERATED_SAMPLE_POINTS", "");
                replacements = Map.of("%%CONST_ARRAY%%", generateSamplePoints(this.samples));
            }
            try (VulkanShaderCompiler compiler = new VulkanShaderCompiler()) {
                VulkanShaderModule module = compiler.compile(
                        "voxy:post/ssao.comp",
                        VulkanShaderStage.COMPUTE,
                        defines,
                        replacements
                );
                createdPipeline = new VulkanComputePipeline(module, "Voxy " + this.mode + " SSAO");
            }
            createdColourSampler = new VoxyVulkanSampler(
                    AddressMode.REPEAT,
                    AddressMode.REPEAT,
                    FilterMode.NEAREST,
                    FilterMode.NEAREST,
                    1,
                    OptionalDouble.of(0.0)
            );
            createdDepthSampler = this.better
                    ? VoxyVulkanSampler.nearestMipmapNearest(AddressMode.CLAMP_TO_EDGE, AddressMode.CLAMP_TO_EDGE)
                    : new VoxyVulkanSampler(
                            AddressMode.CLAMP_TO_EDGE,
                            AddressMode.CLAMP_TO_EDGE,
                            FilterMode.LINEAR,
                            FilterMode.LINEAR,
                            1,
                            OptionalDouble.of(0.0)
                    );
        } catch (RuntimeException | Error exception) {
            if (createdDepthSampler != null) createdDepthSampler.close();
            if (createdColourSampler != null) createdColourSampler.close();
            if (createdPipeline != null) createdPipeline.close();
            throw exception;
        }
        this.pipeline = createdPipeline;
        this.colourSampler = createdColourSampler;
        this.depthSampler = createdDepthSampler;
    }

    public void compute(
            VulkanMDICViewport viewport,
            VulkanColorTarget output,
            VulkanColorTarget input,
            VulkanDepthStencilTarget depthStencil,
            GpuTextureView sourceDepth
    ) {
        this.ensureOpen();
        Objects.requireNonNull(viewport, "viewport");
        Objects.requireNonNull(output, "output");
        Objects.requireNonNull(input, "input");
        Objects.requireNonNull(depthStencil, "depthStencil");
        Objects.requireNonNull(sourceDepth, "sourceDepth");
        if (output == input) {
            throw new IllegalArgumentException("Voxy SSAO input and output targets must be distinct");
        }
        if (viewport.width <= 0 || viewport.height <= 0
                || output.width() != viewport.width || output.height() != viewport.height
                || input.width() != viewport.width || input.height() != viewport.height
                || depthStencil.width() != viewport.width || depthStencil.height() != viewport.height) {
            throw new IllegalArgumentException("Voxy SSAO targets must match the active viewport");
        }

        VulkanGpuTextureView hostDepthView = null;
        VulkanGpuTexture hostDepthImage = null;
        if (this.better) {
            if (!(sourceDepth instanceof VulkanGpuTextureView view)
                    || sourceDepth.isClosed()
                    || !sourceDepth.texture().getFormat().hasDepthAspect()
                    || (sourceDepth.texture().usage() & GpuTexture.USAGE_TEXTURE_BINDING) == 0
                    || sourceDepth.mipLevels() != 1
                    || sourceDepth.texture().getDepthOrLayers() != 1) {
                throw new IllegalArgumentException("Better Vulkan SSAO requires Minecraft's open single-mip depth view");
            }
            hostDepthView = view;
            hostDepthImage = view.texture();
        }

        VulkanGpuTextureView finalHostDepthView = hostDepthView;
        VulkanGpuTexture finalHostDepthImage = hostDepthImage;
        VulkanCommandRecorder.record(commandBuffer -> {
            input.transitionToSampled(commandBuffer);
            depthStencil.image().transition(
                    commandBuffer, 0, 1, 0, 1, VulkanImageStates.SHADER_SAMPLED
            );
            output.transitionToComputeStorage(commandBuffer);
            if (finalHostDepthImage != null) {
                VulkanSync.imageBarrier(
                        commandBuffer,
                        finalHostDepthImage.vkImage(),
                        VK12.VK_IMAGE_ASPECT_DEPTH_BIT,
                        sourceDepth.baseMipLevel(),
                        1,
                        0,
                        1,
                        VulkanHostImageStates.DEPTH_ATTACHMENT,
                        VulkanHostImageStates.COMPUTE_SAMPLED
                );
            }

            VulkanPushDescriptors descriptors = new VulkanPushDescriptors()
                    .storageImage(0, output.view(), VulkanImageStates.COMPUTE_STORAGE.layout())
                    .sampledImage(1, input.view(), this.colourSampler, VulkanImageStates.SHADER_SAMPLED.layout())
                    .sampledImage(
                            2,
                            depthStencil.depthSampledView(),
                            this.depthSampler,
                            VulkanImageStates.SHADER_SAMPLED.layout()
                    );
            if (finalHostDepthView != null) {
                descriptors.sampledImage(3, finalHostDepthView, this.depthSampler, VK12.VK_IMAGE_LAYOUT_GENERAL);
            }

            try (MemoryStack stack = MemoryStack.stackPush()) {
                ByteBuffer push = this.createPushConstants(stack, viewport);
                this.pipeline.bind(commandBuffer, descriptors, push);
                VK12.vkCmdDispatch(
                        commandBuffer,
                        Math.addExact(viewport.width, 7) / 8,
                        Math.addExact(viewport.height, 7) / 8,
                        1
                );
            }

            if (finalHostDepthImage != null) {
                VulkanSync.imageBarrier(
                        commandBuffer,
                        finalHostDepthImage.vkImage(),
                        VK12.VK_IMAGE_ASPECT_DEPTH_BIT,
                        sourceDepth.baseMipLevel(),
                        1,
                        0,
                        1,
                        VulkanHostImageStates.COMPUTE_SAMPLED,
                        VulkanHostImageStates.DEPTH_ATTACHMENT
                );
            }
            output.transitionToAttachment(commandBuffer);
        });
    }

    public SSAO.SSAOMode mode() {
        return this.mode;
    }

    private ByteBuffer createPushConstants(MemoryStack stack, VulkanMDICViewport viewport) {
        int size = this.better ? 4 * 16 * Float.BYTES : 2 * 16 * Float.BYTES;
        ByteBuffer push = stack.malloc(size).order(ByteOrder.nativeOrder());
        Matrix4f scratch = new Matrix4f();
        if (this.better) {
            putMatrix(push, 0, viewport.projection);
            putMatrix(push, 64, viewport.projection.invert(scratch));
            putMatrix(push, 128, viewport.modelView);
            putMatrix(push, 192, viewport.vanillaProjection.invert(scratch));
        } else {
            putMatrix(push, 0, viewport.MVP);
            putMatrix(push, 64, viewport.MVP.invert(scratch));
        }
        push.position(size).flip();
        return push;
    }

    private static void putMatrix(ByteBuffer target, int offset, Matrix4fc matrix) {
        matrix.get(offset, target);
    }

    private static SSAO.SSAOMode selectMode(SSAO.SSAOMode requested) {
        if (requested != SSAO.SSAOMode.AUTO) return requested;
        int vendorId = VoxyVulkanContext.get().capabilities().vendorId();
        if (vendorId == NVIDIA_VENDOR_ID) {
            long dedicatedMemory = largestDeviceLocalHeap();
            if (dedicatedMemory < BASIC_MEMORY_THRESHOLD) return SSAO.SSAOMode.BASIC;
            if (dedicatedMemory < BEST_MEMORY_THRESHOLD) return SSAO.SSAOMode.BETTER;
            return SSAO.SSAOMode.BEST;
        }
        if (vendorId == AMD_VENDOR_ID) return SSAO.SSAOMode.BETTER;
        return SSAO.SSAOMode.BASIC;
    }

    private static long largestDeviceLocalHeap() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkPhysicalDeviceMemoryProperties memory = VkPhysicalDeviceMemoryProperties.calloc(stack);
            VK12.vkGetPhysicalDeviceMemoryProperties(
                    VoxyVulkanContext.get().vulkanDevice().vkDevice().getPhysicalDevice(),
                    memory
            );
            long largest = 0L;
            for (int heap = 0; heap < memory.memoryHeapCount(); heap++) {
                if ((memory.memoryHeaps(heap).flags() & VK12.VK_MEMORY_HEAP_DEVICE_LOCAL_BIT) != 0) {
                    largest = Math.max(largest, memory.memoryHeaps(heap).size());
                }
            }
            return largest;
        }
    }

    private static String generateSamplePoints(int samples) {
        StringBuilder array = new StringBuilder(samples * 32);
        for (int i = 0; i < samples; i++) {
            float a = (i + 0.5f) * (1.0f / samples);
            float base = (float) (i * (1.0 / 1.6180339887) + 0.5);
            float radius = (float) Math.sqrt(base % 1.0f);
            float theta = a * 6.2831853f;
            if (i != 0) array.append(", ");
            array.append("vec2(")
                    .append((float) (radius * Math.cos(theta))).append("f, ")
                    .append((float) (radius * Math.sin(theta))).append("f)");
        }
        return array.toString();
    }

    private void ensureOpen() {
        if (this.closed) throw new IllegalStateException("Voxy Vulkan SSAO is closed");
    }

    @Override
    public void close() {
        if (this.closed) return;
        this.closed = true;
        this.depthSampler.close();
        this.colourSampler.close();
        this.pipeline.close();
    }
}
