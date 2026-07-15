package me.cortex.voxy.client.core.rendering.section.backend.mdic;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.textures.AddressMode;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vulkan.VulkanConst;
import com.mojang.blaze3d.vulkan.VulkanGpuTexture;
import com.mojang.blaze3d.vulkan.VulkanGpuTextureView;
import me.cortex.voxy.client.RenderStatistics;
import me.cortex.voxy.client.core.RenderProperties;
import me.cortex.voxy.client.core.model.ModelStore;
import me.cortex.voxy.client.core.rendering.section.geometry.BasicSectionGeometryData;
import me.cortex.voxy.client.core.rendering.util.VulkanColorTarget;
import me.cortex.voxy.client.core.rendering.util.VulkanDepthStencilTarget;
import me.cortex.voxy.client.core.rendering.util.VulkanSharedIndexBuffer;
import me.cortex.voxy.client.core.vulkan.VoxyVulkanBuffer;
import me.cortex.voxy.client.core.vulkan.VoxyVulkanBufferUsage;
import me.cortex.voxy.client.core.vulkan.VoxyVulkanContext;
import me.cortex.voxy.client.core.vulkan.VoxyVulkanImageView;
import me.cortex.voxy.client.core.vulkan.VoxyVulkanSampler;
import me.cortex.voxy.client.core.vulkan.VulkanCommandRecorder;
import me.cortex.voxy.client.core.vulkan.VulkanDownloadStream;
import me.cortex.voxy.client.core.vulkan.VulkanImageStates;
import me.cortex.voxy.client.core.vulkan.VulkanSync;
import me.cortex.voxy.client.core.vulkan.VulkanUploadStream;
import me.cortex.voxy.client.core.vulkan.shader.VulkanComputePipeline;
import me.cortex.voxy.client.core.vulkan.shader.VulkanGraphicsPipeline;
import me.cortex.voxy.client.core.vulkan.shader.VulkanPushDescriptors;
import me.cortex.voxy.client.core.vulkan.shader.VulkanShaderCompiler;
import me.cortex.voxy.client.core.vulkan.shader.VulkanShaderModule;
import me.cortex.voxy.client.core.vulkan.shader.VulkanShaderStage;
import me.cortex.voxy.common.Logger;
import me.cortex.voxy.common.world.WorldEngine;
import net.minecraft.client.Minecraft;
import org.joml.Matrix4f;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.KHRDynamicRendering;
import org.lwjgl.vulkan.VK11;
import org.lwjgl.vulkan.VK12;
import org.lwjgl.vulkan.VK13;
import org.lwjgl.vulkan.VkRect2D;
import org.lwjgl.vulkan.VkRenderingAttachmentInfo;
import org.lwjgl.vulkan.VkRenderingInfo;
import org.lwjgl.vulkan.VkViewport;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalDouble;

/** Vulkan owner for the original MDIC prep/cull/cmdgen/prefix/translucent generation chain. */
public final class VulkanMDICSectionRenderer implements AutoCloseable {
    public static final int OPAQUE_DRAW_COUNT = MDICSectionRenderer.OPAQUE_DRAW_COUNT;
    public static final int TRANSLUCENT_DRAW_COUNT = MDICSectionRenderer.TRANSLUCENT_DRAW_COUNT;
    public static final int TEMPORAL_DRAW_COUNT = MDICSectionRenderer.TEMPORAL_DRAW_COUNT;
    public static final int TRANSLUCENT_OFFSET = OPAQUE_DRAW_COUNT;
    public static final int TEMPORAL_OFFSET = TRANSLUCENT_OFFSET + TRANSLUCENT_DRAW_COUNT;

    private static final int STATISTICS_BUFFER_BINDING = 8;
    private static final int UNIFORM_BYTES = 1024;
    private static final int PREFIX_COUNT = 1024;
    private static final long CULL_COMMAND_OFFSET = 6L * Integer.BYTES;
    private static final int DRAW_INDEXED_COMMAND_BYTES = 5 * Integer.BYTES;
    private static final long OPAQUE_DRAW_COUNT_OFFSET = 3L * Integer.BYTES;
    private static final long TRANSLUCENT_DRAW_COUNT_OFFSET = 4L * Integer.BYTES;
    private static final long TEMPORAL_DRAW_COUNT_OFFSET = 5L * Integer.BYTES;
    private static final int COLOR_WRITE_MASK = VK12.VK_COLOR_COMPONENT_R_BIT
            | VK12.VK_COLOR_COMPONENT_G_BIT
            | VK12.VK_COLOR_COMPONENT_B_BIT
            | VK12.VK_COLOR_COMPONENT_A_BIT;
    private static final int DEPTH_STENCIL_ASPECTS =
            VK12.VK_IMAGE_ASPECT_DEPTH_BIT | VK12.VK_IMAGE_ASPECT_STENCIL_BIT;
    private static final VulkanSync.ImageState DEPTH_STENCIL_READ = new VulkanSync.ImageState(
            VulkanImageStates.DEPTH_STENCIL_ATTACHMENT.layout(),
            new VulkanSync.Access(
                    VK13.VK_PIPELINE_STAGE_2_EARLY_FRAGMENT_TESTS_BIT
                            | VK13.VK_PIPELINE_STAGE_2_LATE_FRAGMENT_TESTS_BIT,
                    VK13.VK_ACCESS_2_DEPTH_STENCIL_ATTACHMENT_READ_BIT
            )
    );
    private static final VulkanSync.ImageState HOST_LIGHTMAP_GENERAL = new VulkanSync.ImageState(
            VK12.VK_IMAGE_LAYOUT_GENERAL,
            new VulkanSync.Access(
                    VK13.VK_PIPELINE_STAGE_2_TRANSFER_BIT
                            | VK13.VK_PIPELINE_STAGE_2_COLOR_ATTACHMENT_OUTPUT_BIT
                            | VK13.VK_PIPELINE_STAGE_2_FRAGMENT_SHADER_BIT,
                    VK13.VK_ACCESS_2_TRANSFER_WRITE_BIT
                            | VK13.VK_ACCESS_2_COLOR_ATTACHMENT_WRITE_BIT
                            | VK13.VK_ACCESS_2_SHADER_SAMPLED_READ_BIT
            )
    );
    private static final VulkanSync.ImageState HOST_LIGHTMAP_SAMPLED = new VulkanSync.ImageState(
            VK12.VK_IMAGE_LAYOUT_GENERAL,
            new VulkanSync.Access(
                    VK13.VK_PIPELINE_STAGE_2_FRAGMENT_SHADER_BIT,
                    VK13.VK_ACCESS_2_SHADER_SAMPLED_READ_BIT
            )
    );

    private final RenderProperties properties;
    private final ModelStore modelStore;
    private final BasicSectionGeometryData geometryData;
    private final boolean statisticsEnabled;
    private final VulkanComputePipeline prepPipeline;
    private final VulkanComputePipeline commandGenPipeline;
    private final VulkanComputePipeline prefixSumPipeline;
    private final VulkanComputePipeline translucentGenPipeline;
    private final VulkanGraphicsPipeline cullPipeline;
    private final VulkanGraphicsPipeline opaqueTerrainPipeline;
    private final VulkanGraphicsPipeline translucentTerrainPipeline;
    private final VoxyVulkanBuffer uniformBuffer;
    private final VoxyVulkanBuffer distanceCountBuffer;
    private final VoxyVulkanBuffer statisticsBuffer;
    private final VulkanSharedIndexBuffer sharedIndexBuffer;
    private final VoxyVulkanSampler lightmapSampler;
    private final VoxyVulkanSampler depthSampler;
    private final VulkanUploadStream uploads = new VulkanUploadStream();
    private final VulkanDownloadStream downloads = VoxyVulkanContext.get().downloadStream();
    private boolean closed;

    public VulkanMDICSectionRenderer(
            RenderProperties properties,
            ModelStore modelStore,
            BasicSectionGeometryData geometryData
    ) {
        this.properties = properties;
        this.modelStore = modelStore;
        this.geometryData = geometryData;
        this.statisticsEnabled = RenderStatistics.enabled;

        Map<String, String> depthDefines = new HashMap<>(properties.shaderDefines());
        Map<String, String> terrainDefines = new HashMap<>(depthDefines);
        // Select the original 32-bit quad-data capability variant explicitly. The quad remains
        // the same little-endian 8-byte record; Vulkan does not need shaderInt64 for this path.
        terrainDefines.put("QUAD_DATA_USE_IVEC2", "");
        var cardinalLight = Objects.requireNonNull(
                Minecraft.getInstance().level,
                "Voxy terrain pipeline requires an active client level"
        ).cardinalLighting();
        terrainDefines.put("NO_SHADE_FACE_TINT", Float.toString(cardinalLight.up()));
        terrainDefines.put("UP_FACE_TINT", Float.toString(cardinalLight.up()));
        terrainDefines.put("DOWN_FACE_TINT", Float.toString(cardinalLight.down()));
        terrainDefines.put("Z_AXIS_FACE_TINT", Float.toString(cardinalLight.north()));
        terrainDefines.put("X_AXIS_FACE_TINT", Float.toString(cardinalLight.east()));
        Map<String, String> translucentTerrainDefines = new HashMap<>(terrainDefines);
        translucentTerrainDefines.put("TRANSLUCENT", "");
        Map<String, String> cmdgenDefines = new HashMap<>();
        cmdgenDefines.put("TRANSLUCENT_WRITE_BASE", Integer.toString(PREFIX_COUNT));
        cmdgenDefines.put("TEMPORAL_OFFSET", Integer.toString(TEMPORAL_OFFSET));
        cmdgenDefines.put("TRANSLUCENT_DISTANCE_BUFFER_BINDING", "7");
        if (this.statisticsEnabled) {
            cmdgenDefines.put("HAS_STATISTICS", "");
            cmdgenDefines.put("STATISTICS_BUFFER_BINDING", Integer.toString(STATISTICS_BUFFER_BINDING));
        }
        Map<String, String> translucentDefines = Map.of(
                "TRANSLUCENT_WRITE_BASE", Integer.toString(PREFIX_COUNT),
                "TRANSLUCENT_DISTANCE_BUFFER_BINDING", "5",
                "TRANSLUCENT_OFFSET", Integer.toString(TRANSLUCENT_OFFSET)
        );
        Map<String, String> prefixDefines = Map.of("IO_BUFFER", "0");

        VulkanComputePipeline createdPrep = null;
        VulkanComputePipeline createdCommandGen = null;
        VulkanComputePipeline createdPrefixSum = null;
        VulkanComputePipeline createdTranslucentGen = null;
        VulkanGraphicsPipeline createdCull = null;
        VulkanGraphicsPipeline createdOpaqueTerrain = null;
        VulkanGraphicsPipeline createdTranslucentTerrain = null;
        VoxyVulkanBuffer createdUniform = null;
        VoxyVulkanBuffer createdDistanceCount = null;
        VoxyVulkanBuffer createdStatistics = null;
        VulkanSharedIndexBuffer createdSharedIndices = null;
        VoxyVulkanSampler createdLightmapSampler = null;
        VoxyVulkanSampler createdDepthSampler = null;
        try {
            try (VulkanShaderCompiler compiler = new VulkanShaderCompiler()) {
                createdPrep = new VulkanComputePipeline(
                    compiler.compile("voxy:lod/gl46/prep.comp", VulkanShaderStage.COMPUTE, Map.of()),
                    "Voxy MDIC prep"
                );
                createdCommandGen = new VulkanComputePipeline(
                    compiler.compile("voxy:lod/gl46/cmdgen.comp", VulkanShaderStage.COMPUTE, cmdgenDefines),
                    "Voxy MDIC command generation"
                );
                String prefixShader = supportsSubgroupPrefixSum()
                        ? "voxy:util/prefixsum/inital3.comp"
                        : "voxy:util/prefixsum/simple.comp";
                createdPrefixSum = new VulkanComputePipeline(
                        compiler.compile(prefixShader, VulkanShaderStage.COMPUTE, prefixDefines),
                        "Voxy MDIC translucent prefix sum"
                );
                createdTranslucentGen = new VulkanComputePipeline(
                    compiler.compile(
                            "voxy:lod/gl46/buildtranslucents.comp",
                            VulkanShaderStage.COMPUTE,
                            translucentDefines
                    ),
                    "Voxy MDIC translucent command generation"
                );
                VulkanShaderModule cullVertex = null;
                VulkanShaderModule cullFragment = null;
                try {
                    cullVertex = compiler.compile(
                            "voxy:lod/gl46/cull/raster.vert",
                            VulkanShaderStage.VERTEX,
                            depthDefines
                    );
                    cullFragment = compiler.compile(
                            "voxy:lod/gl46/cull/raster.frag",
                            VulkanShaderStage.FRAGMENT,
                            depthDefines
                    );
                    VulkanGraphicsPipeline.StencilFace stencilEqual = new VulkanGraphicsPipeline.StencilFace(
                            VK12.VK_STENCIL_OP_KEEP,
                            VK12.VK_STENCIL_OP_KEEP,
                            VK12.VK_STENCIL_OP_KEEP,
                            VK12.VK_COMPARE_OP_EQUAL,
                            0xFF,
                            0,
                            1
                    );
                    int depthFormat = com.mojang.blaze3d.vulkan.VulkanConst.toVk(GpuFormat.D24_UNORM_S8_UINT);
                    VulkanShaderModule ownedVertex = cullVertex;
                    VulkanShaderModule ownedFragment = cullFragment;
                    cullVertex = null;
                    cullFragment = null;
                    createdCull = new VulkanGraphicsPipeline(
                            ownedVertex,
                            ownedFragment,
                            new VulkanGraphicsPipeline.State(
                                    VK12.VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST,
                                    VK12.VK_POLYGON_MODE_FILL,
                                    VK12.VK_CULL_MODE_NONE,
                                    VK12.VK_FRONT_FACE_COUNTER_CLOCKWISE,
                                    true,
                                    false,
                                    closerEqualCompare(properties),
                                    VulkanGraphicsPipeline.StencilState.bothFaces(stencilEqual),
                                    depthFormat,
                                    depthFormat,
                                    VK12.VK_SAMPLE_COUNT_1_BIT,
                                    VoxyVulkanContext.get().capabilities().representativeFragmentTest(),
                                    List.of()
                            ),
                            "Voxy MDIC cull raster"
                    );
                } finally {
                    if (cullVertex != null) cullVertex.close();
                    if (cullFragment != null) cullFragment.close();
                }
                createdOpaqueTerrain = createTerrainPipeline(
                        compiler,
                        terrainDefines,
                        false,
                        properties,
                        "Voxy terrain opaque/temporal"
                );
                createdTranslucentTerrain = createTerrainPipeline(
                        compiler,
                        translucentTerrainDefines,
                        true,
                        properties,
                        "Voxy terrain translucent"
                );
            }

            int uniformRoles = VoxyVulkanBufferUsage.UNIFORM | VoxyVulkanBufferUsage.TRANSFER_DESTINATION;
            int computeRoles = VoxyVulkanBufferUsage.STORAGE_COMPUTE | VoxyVulkanBufferUsage.TRANSFER_DESTINATION;
            int statisticsRoles = computeRoles | VoxyVulkanBufferUsage.TRANSFER_SOURCE;
            createdUniform = createZeroedBuffer(UNIFORM_BYTES, uniformRoles, "Voxy MDIC scene uniform");
            createdDistanceCount = createZeroedBuffer(
                    PREFIX_COUNT * 4L + TRANSLUCENT_DRAW_COUNT * 4L,
                    computeRoles,
                    "Voxy MDIC translucent distances"
            );
            createdStatistics = createZeroedBuffer(1024L, statisticsRoles, "Voxy MDIC statistics");
            createdSharedIndices = new VulkanSharedIndexBuffer();
            createdLightmapSampler = new VoxyVulkanSampler(
                    AddressMode.CLAMP_TO_EDGE,
                    AddressMode.CLAMP_TO_EDGE,
                    FilterMode.LINEAR,
                    FilterMode.LINEAR,
                    1,
                    OptionalDouble.of(0.0)
            );
            createdDepthSampler = new VoxyVulkanSampler(
                    AddressMode.CLAMP_TO_EDGE,
                    AddressMode.CLAMP_TO_EDGE,
                    FilterMode.NEAREST,
                    FilterMode.NEAREST,
                    1,
                    OptionalDouble.of(0.0)
            );
        } catch (RuntimeException | Error exception) {
            if (createdDepthSampler != null) createdDepthSampler.close();
            if (createdLightmapSampler != null) createdLightmapSampler.close();
            if (createdSharedIndices != null) createdSharedIndices.close();
            if (createdStatistics != null) createdStatistics.close();
            if (createdDistanceCount != null) createdDistanceCount.close();
            if (createdUniform != null) createdUniform.close();
            if (createdTranslucentTerrain != null) createdTranslucentTerrain.close();
            if (createdOpaqueTerrain != null) createdOpaqueTerrain.close();
            if (createdCull != null) createdCull.close();
            if (createdTranslucentGen != null) createdTranslucentGen.close();
            if (createdPrefixSum != null) createdPrefixSum.close();
            if (createdCommandGen != null) createdCommandGen.close();
            if (createdPrep != null) createdPrep.close();
            throw exception;
        }
        this.prepPipeline = createdPrep;
        this.commandGenPipeline = createdCommandGen;
        this.prefixSumPipeline = createdPrefixSum;
        this.translucentGenPipeline = createdTranslucentGen;
        this.cullPipeline = createdCull;
        this.opaqueTerrainPipeline = createdOpaqueTerrain;
        this.translucentTerrainPipeline = createdTranslucentTerrain;
        this.uniformBuffer = createdUniform;
        this.distanceCountBuffer = createdDistanceCount;
        this.statisticsBuffer = createdStatistics;
        this.sharedIndexBuffer = createdSharedIndices;
        this.lightmapSampler = createdLightmapSampler;
        this.depthSampler = createdDepthSampler;
    }

    private static VoxyVulkanBuffer createZeroedBuffer(long size, int roles, String label) {
        return VoxyVulkanBuffer.create(size, VoxyVulkanBufferUsage.of(roles), label);
    }

    private static VulkanGraphicsPipeline createTerrainPipeline(
            VulkanShaderCompiler compiler,
            Map<String, String> defines,
            boolean translucent,
            RenderProperties properties,
            String label
    ) {
        VulkanShaderModule vertex = null;
        VulkanShaderModule fragment = null;
        try {
            vertex = compiler.compile("voxy:lod/gl46/quads3.vert", VulkanShaderStage.VERTEX, defines);
            fragment = compiler.compile("voxy:lod/gl46/quads.frag", VulkanShaderStage.FRAGMENT, defines);
            VulkanGraphicsPipeline.StencilFace stencilEqual = new VulkanGraphicsPipeline.StencilFace(
                    VK12.VK_STENCIL_OP_KEEP,
                    VK12.VK_STENCIL_OP_KEEP,
                    VK12.VK_STENCIL_OP_KEEP,
                    VK12.VK_COMPARE_OP_EQUAL,
                    0xFF,
                    0,
                    1
            );
            int depthFormat = com.mojang.blaze3d.vulkan.VulkanConst.toVk(GpuFormat.D24_UNORM_S8_UINT);
            int colourFormat = com.mojang.blaze3d.vulkan.VulkanConst.toVk(VulkanColorTarget.FORMAT);
            VulkanGraphicsPipeline.ColorAttachment colourAttachment = translucent
                    ? VulkanGraphicsPipeline.ColorAttachment.alphaBlend(colourFormat, COLOR_WRITE_MASK)
                    : VulkanGraphicsPipeline.ColorAttachment.opaque(colourFormat, COLOR_WRITE_MASK);
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
                            closerEqualCompare(properties),
                            VulkanGraphicsPipeline.StencilState.bothFaces(stencilEqual),
                            depthFormat,
                            depthFormat,
                            VK12.VK_SAMPLE_COUNT_1_BIT,
                            false,
                            List.of(colourAttachment)
                    ),
                    label
            );
        } finally {
            if (vertex != null) vertex.close();
            if (fragment != null) fragment.close();
        }
    }

    public VulkanMDICViewport createViewport() {
        this.ensureOpen();
        return new VulkanMDICViewport(this.properties, this.geometryData.getMaxSectionCount());
    }

    public void renderOpaque(
            VulkanMDICViewport viewport,
            VulkanColorTarget colourTarget,
            VulkanDepthStencilTarget depthStencilTarget,
            GpuTextureView lightmap
    ) {
        this.ensureOpen();
        if (this.geometryData.getSectionCount() == 0) return;
        this.uploadUniform(viewport);
        this.uploads.commit();
        int maxDrawCount = Math.min(
                (int) (this.geometryData.getSectionCount() * 4.4 + 128),
                OPAQUE_DRAW_COUNT
        );
        this.renderTerrain(
                viewport,
                colourTarget,
                depthStencilTarget,
                lightmap,
                this.opaqueTerrainPipeline,
                0L,
                OPAQUE_DRAW_COUNT_OFFSET,
                maxDrawCount
        );
    }

    public void renderTemporal(
            VulkanMDICViewport viewport,
            VulkanColorTarget colourTarget,
            VulkanDepthStencilTarget depthStencilTarget,
            GpuTextureView lightmap
    ) {
        this.ensureOpen();
        if (this.geometryData.getSectionCount() == 0) return;
        this.renderTerrain(
                viewport,
                colourTarget,
                depthStencilTarget,
                lightmap,
                this.opaqueTerrainPipeline,
                TEMPORAL_OFFSET * (long) DRAW_INDEXED_COMMAND_BYTES,
                TEMPORAL_DRAW_COUNT_OFFSET,
                Math.min(this.geometryData.getSectionCount(), TEMPORAL_DRAW_COUNT)
        );
    }

    public void renderTranslucent(
            VulkanMDICViewport viewport,
            VulkanColorTarget colourTarget,
            VulkanDepthStencilTarget depthStencilTarget,
            GpuTextureView lightmap
    ) {
        this.ensureOpen();
        if (this.geometryData.getSectionCount() == 0) return;
        this.renderTerrain(
                viewport,
                colourTarget,
                depthStencilTarget,
                lightmap,
                this.translucentTerrainPipeline,
                TRANSLUCENT_OFFSET * (long) DRAW_INDEXED_COMMAND_BYTES,
                TRANSLUCENT_DRAW_COUNT_OFFSET,
                Math.min(this.geometryData.getSectionCount(), TRANSLUCENT_DRAW_COUNT)
        );
    }

    private void renderTerrain(
            VulkanMDICViewport viewport,
            VulkanColorTarget colourTarget,
            VulkanDepthStencilTarget depthStencilTarget,
            GpuTextureView lightmap,
            VulkanGraphicsPipeline pipeline,
            long indirectOffset,
            long drawCountOffset,
            int maxDrawCount
    ) {
        this.validateTerrainTargets(viewport, colourTarget, depthStencilTarget, lightmap);
        if (maxDrawCount <= 0) return;
        if (Integer.toUnsignedLong(maxDrawCount) > VoxyVulkanContext.get().capabilities().maxDrawIndirectCount()) {
            throw new UnsupportedOperationException(
                    "Voxy terrain requires maxDrawIndirectCount >= " + maxDrawCount
                            + ", device limit is " + VoxyVulkanContext.get().capabilities().maxDrawIndirectCount()
            );
        }

        VulkanGpuTextureView vulkanLightmapView = (VulkanGpuTextureView) lightmap;
        VulkanGpuTexture vulkanLightmap = vulkanLightmapView.texture();
        VoxyVulkanImageView depthBounds = viewport.depthBoundingBuffer.sampledView();
        VulkanCommandRecorder.record(commandBuffer -> {
            VulkanSync.imageBarrier(
                    commandBuffer,
                    vulkanLightmap.vkImage(),
                    VK12.VK_IMAGE_ASPECT_COLOR_BIT,
                    lightmap.baseMipLevel(),
                    1,
                    0,
                    1,
                    HOST_LIGHTMAP_GENERAL,
                    HOST_LIGHTMAP_SAMPLED
            );
            colourTarget.transitionToAttachment(commandBuffer);
            depthStencilTarget.image().transitionAndSynchronize(
                    commandBuffer,
                    0,
                    1,
                    0,
                    1,
                    VulkanImageStates.DEPTH_STENCIL_ATTACHMENT
            );
            viewport.depthBoundingBuffer.image().transition(
                    commandBuffer,
                    0,
                    1,
                    0,
                    1,
                    VulkanImageStates.SHADER_SAMPLED
            );

            try (MemoryStack stack = MemoryStack.stackPush()) {
                VkRenderingAttachmentInfo.Buffer colourAttachments = VkRenderingAttachmentInfo.calloc(1, stack);
                colourAttachments.get(0)
                        .sType$Default()
                        .imageView(colourTarget.view().vkImageView())
                        .imageLayout(VulkanImageStates.COLOR_ATTACHMENT.layout())
                        .loadOp(VK12.VK_ATTACHMENT_LOAD_OP_LOAD)
                        .storeOp(VK12.VK_ATTACHMENT_STORE_OP_STORE);
                VkRenderingAttachmentInfo depthAttachment = VkRenderingAttachmentInfo.calloc(stack)
                        .sType$Default()
                        .imageView(depthStencilTarget.attachmentView().vkImageView())
                        .imageLayout(VulkanImageStates.DEPTH_STENCIL_ATTACHMENT.layout())
                        .loadOp(VK12.VK_ATTACHMENT_LOAD_OP_LOAD)
                        .storeOp(VK12.VK_ATTACHMENT_STORE_OP_STORE);
                VkRenderingAttachmentInfo stencilAttachment = VkRenderingAttachmentInfo.calloc(stack)
                        .sType$Default()
                        .imageView(depthStencilTarget.attachmentView().vkImageView())
                        .imageLayout(VulkanImageStates.DEPTH_STENCIL_ATTACHMENT.layout())
                        .loadOp(VK12.VK_ATTACHMENT_LOAD_OP_LOAD)
                        .storeOp(VK12.VK_ATTACHMENT_STORE_OP_STORE);
                VkRenderingInfo rendering = VkRenderingInfo.calloc(stack)
                        .sType$Default()
                        .layerCount(1)
                        .viewMask(0)
                        .pColorAttachments(colourAttachments)
                        .pDepthAttachment(depthAttachment)
                        .pStencilAttachment(stencilAttachment);
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

                VulkanPushDescriptors descriptors = this.modelStore.bind(
                        new VulkanPushDescriptors()
                                .uniformBuffer(0, this.uniformBuffer, 0L, UNIFORM_BYTES)
                                .storageBuffer(
                                        1,
                                        this.geometryData.getGeometryBuffer(),
                                        0L,
                                        this.geometryData.getGeometryBuffer().size()
                                )
                                .storageBuffer(
                                        5,
                                        viewport.positionScratchBuffer,
                                        0L,
                                        viewport.positionScratchBuffer.size()
                                )
                                .sampledImage(1, lightmap, this.lightmapSampler, VK12.VK_IMAGE_LAYOUT_GENERAL)
                                .sampledImage(
                                        2,
                                        depthBounds,
                                        this.depthSampler,
                                        VulkanImageStates.SHADER_SAMPLED.layout()
                                ),
                        3,
                        4,
                        0
                );
                pipeline.bind(commandBuffer, descriptors, null);
                VK12.vkCmdBindIndexBuffer(
                        commandBuffer,
                        this.sharedIndexBuffer.buffer().vkBuffer(),
                        0L,
                        VK12.VK_INDEX_TYPE_UINT16
                );
                VK12.vkCmdDrawIndexedIndirectCount(
                        commandBuffer,
                        viewport.drawCallBuffer.vkBuffer(),
                        indirectOffset,
                        viewport.drawCountCallBuffer.vkBuffer(),
                        drawCountOffset,
                        maxDrawCount,
                        DRAW_INDEXED_COMMAND_BYTES
                );
                KHRDynamicRendering.vkCmdEndRenderingKHR(commandBuffer);
            }

            VulkanSync.imageBarrier(
                    commandBuffer,
                    vulkanLightmap.vkImage(),
                    VK12.VK_IMAGE_ASPECT_COLOR_BIT,
                    lightmap.baseMipLevel(),
                    1,
                    0,
                    1,
                    HOST_LIGHTMAP_SAMPLED,
                    HOST_LIGHTMAP_GENERAL
            );
        });
    }

    private void validateTerrainTargets(
            VulkanMDICViewport viewport,
            VulkanColorTarget colourTarget,
            VulkanDepthStencilTarget depthStencilTarget,
            GpuTextureView lightmap
    ) {
        Objects.requireNonNull(viewport, "viewport");
        Objects.requireNonNull(colourTarget, "colourTarget");
        Objects.requireNonNull(depthStencilTarget, "depthStencilTarget");
        Objects.requireNonNull(lightmap, "lightmap");
        if (!(lightmap instanceof VulkanGpuTextureView)
                || lightmap.isClosed()
                || lightmap.texture().getFormat() != GpuFormat.RGBA8_UNORM
                || lightmap.texture().getWidth(lightmap.baseMipLevel()) != 16
                || lightmap.texture().getHeight(lightmap.baseMipLevel()) != 16
                || lightmap.texture().getDepthOrLayers() != 1
                || lightmap.mipLevels() != 1) {
            throw new IllegalArgumentException("Voxy terrain requires Minecraft's open 16x16 Vulkan RGBA8 level lightmap");
        }
        if (viewport.width <= 0 || viewport.height <= 0
                || colourTarget.width() != viewport.width
                || colourTarget.height() != viewport.height
                || depthStencilTarget.width() != viewport.width
                || depthStencilTarget.height() != viewport.height
                || viewport.depthBoundingBuffer.width() != viewport.width
                || viewport.depthBoundingBuffer.height() != viewport.height) {
            throw new IllegalArgumentException("Voxy terrain targets must match the active viewport dimensions");
        }
        VoxyVulkanImageView colourView = colourTarget.view();
        if (colourView.image().getFormat() != VulkanColorTarget.FORMAT
                || colourView.aspectMask() != VK12.VK_IMAGE_ASPECT_COLOR_BIT
                || colourView.mipLevelCount() != 1
                || colourView.layerCount() != 1) {
            throw new IllegalArgumentException("Voxy terrain colour target must be one RGBA8 mip and layer");
        }
        this.validateDepthTarget(depthStencilTarget.attachmentView());
        VoxyVulkanImageView depthBounds = viewport.depthBoundingBuffer.sampledView();
        if (depthBounds.image().getFormat() != GpuFormat.D24_UNORM_S8_UINT
                || depthBounds.aspectMask() != VK12.VK_IMAGE_ASPECT_DEPTH_BIT
                || depthBounds.mipLevelCount() != 1
                || depthBounds.layerCount() != 1) {
            throw new IllegalArgumentException("Voxy terrain depth bounds must be one sampled Depth24 mip and layer");
        }
    }

    public void buildDrawCalls(VulkanMDICViewport viewport, VoxyVulkanImageView depthStencilView) {
        this.ensureOpen();
        if (this.geometryData.getSectionCount() == 0) return;
        this.validateDepthTarget(depthStencilView);
        this.uploadUniform(viewport);
        this.uploads.commit();
        this.distanceCountBuffer.fill(0L, PREFIX_COUNT * 4L, 0);
        if (this.statisticsEnabled) this.statisticsBuffer.zero();

        VulkanCommandRecorder.record(commandBuffer -> {
            VulkanPushDescriptors prepDescriptors = new VulkanPushDescriptors()
                    .storageBuffer(1, viewport.drawCountCallBuffer, 0L, viewport.drawCountCallBuffer.size())
                    .storageBuffer(2, viewport.indirectLookupBuffer, 0L, viewport.indirectLookupBuffer.size());
            VulkanSync.bufferBarrier(
                    commandBuffer,
                    viewport.drawCountCallBuffer.vkBuffer(),
                    0L,
                    viewport.drawCountCallBuffer.size(),
                    viewport.drawCountCallBuffer.policy().declaredAccess(),
                    VulkanSync.COMPUTE_STORAGE_WRITE
            );
            this.prepPipeline.bind(commandBuffer, prepDescriptors, null);
            VK12.vkCmdDispatch(commandBuffer, 1, 1, 1);
            VulkanSync.bufferBarrier(
                    commandBuffer,
                    viewport.drawCountCallBuffer.vkBuffer(),
                    0L,
                    viewport.drawCountCallBuffer.size(),
                    VulkanSync.COMPUTE_STORAGE_WRITE,
                    VulkanSync.COMPUTE_STORAGE_READ_WRITE.or(VulkanSync.INDIRECT_READ)
            );

            this.drawCullRaster(commandBuffer, viewport, depthStencilView);

            VulkanPushDescriptors commandDescriptors = new VulkanPushDescriptors()
                    .uniformBuffer(0, this.uniformBuffer, 0L, UNIFORM_BYTES)
                    .storageBuffer(1, viewport.drawCallBuffer, 0L, viewport.drawCallBuffer.size())
                    .storageBuffer(2, viewport.drawCountCallBuffer, 0L, viewport.drawCountCallBuffer.size())
                    .storageBuffer(3, this.geometryData.getMetadataBuffer(), 0L,
                            this.geometryData.getMetadataBuffer().size())
                    .storageBuffer(4, viewport.visibilityBuffer, 0L, viewport.visibilityBuffer.size())
                    .storageBuffer(5, viewport.indirectLookupBuffer, 0L, viewport.indirectLookupBuffer.size())
                    .storageBuffer(6, viewport.positionScratchBuffer, 0L, viewport.positionScratchBuffer.size())
                    .storageBuffer(7, this.distanceCountBuffer, 0L, this.distanceCountBuffer.size());
            if (this.statisticsEnabled) {
                commandDescriptors.storageBuffer(
                        STATISTICS_BUFFER_BINDING,
                        this.statisticsBuffer,
                        0L,
                        this.statisticsBuffer.size()
                );
            }
            this.barrierBeforeCommandGeneration(commandBuffer, viewport);
            this.commandGenPipeline.bind(commandBuffer, commandDescriptors, null);
            VK12.vkCmdDispatchIndirect(commandBuffer, viewport.drawCountCallBuffer.vkBuffer(), 0L);

            VulkanSync.bufferBarrier(
                    commandBuffer,
                    this.distanceCountBuffer.vkBuffer(),
                    0L,
                    this.distanceCountBuffer.size(),
                    VulkanSync.COMPUTE_STORAGE_READ_WRITE,
                    VulkanSync.COMPUTE_STORAGE_READ_WRITE
            );
            this.prefixSumPipeline.bind(
                    commandBuffer,
                    new VulkanPushDescriptors().storageBuffer(
                            0,
                            this.distanceCountBuffer,
                            0L,
                            this.distanceCountBuffer.size()
                    ),
                    null
            );
            VK12.vkCmdDispatch(commandBuffer, 1, 1, 1);
            VulkanSync.bufferBarrier(
                    commandBuffer,
                    this.distanceCountBuffer.vkBuffer(),
                    0L,
                    this.distanceCountBuffer.size(),
                    VulkanSync.COMPUTE_STORAGE_READ_WRITE,
                    VulkanSync.COMPUTE_STORAGE_READ
            );

            VulkanPushDescriptors translucentDescriptors = new VulkanPushDescriptors()
                    .uniformBuffer(0, this.uniformBuffer, 0L, UNIFORM_BYTES)
                    .storageBuffer(1, viewport.drawCallBuffer, 0L, viewport.drawCallBuffer.size())
                    .storageBuffer(2, viewport.drawCountCallBuffer, 0L, viewport.drawCountCallBuffer.size())
                    .storageBuffer(3, this.geometryData.getMetadataBuffer(), 0L,
                            this.geometryData.getMetadataBuffer().size())
                    .storageBuffer(4, viewport.indirectLookupBuffer, 0L, viewport.indirectLookupBuffer.size())
                    .storageBuffer(5, this.distanceCountBuffer, 0L, this.distanceCountBuffer.size());
            VulkanSync.bufferBarrier(
                    commandBuffer,
                    viewport.drawCallBuffer.vkBuffer(),
                    0L,
                    viewport.drawCallBuffer.size(),
                    VulkanSync.COMPUTE_STORAGE_READ_WRITE,
                    VulkanSync.COMPUTE_STORAGE_READ_WRITE
            );
            VulkanSync.bufferBarrier(
                    commandBuffer,
                    viewport.drawCountCallBuffer.vkBuffer(),
                    0L,
                    viewport.drawCountCallBuffer.size(),
                    VulkanSync.COMPUTE_STORAGE_READ_WRITE.or(VulkanSync.INDIRECT_READ),
                    VulkanSync.COMPUTE_STORAGE_READ_WRITE.or(VulkanSync.INDIRECT_READ)
            );
            this.translucentGenPipeline.bind(commandBuffer, translucentDescriptors, null);
            VK12.vkCmdDispatchIndirect(commandBuffer, viewport.drawCountCallBuffer.vkBuffer(), 0L);
            this.barrierForTerrainDraws(commandBuffer, viewport);
        });

        if (this.statisticsEnabled) this.downloadStatistics();
    }

    private void drawCullRaster(
            org.lwjgl.vulkan.VkCommandBuffer commandBuffer,
            VulkanMDICViewport viewport,
            VoxyVulkanImageView depthStencilView
    ) {
        VulkanSync.bufferBarrier(
                commandBuffer,
                viewport.visibilityBuffer.vkBuffer(),
                0L,
                viewport.visibilityBuffer.size(),
                viewport.visibilityBuffer.policy().declaredAccess(),
                VulkanSync.GRAPHICS_STORAGE_READ_WRITE
        );
        VulkanSync.imageBarrier(
                commandBuffer,
                depthStencilView.image().vkImage(),
                DEPTH_STENCIL_ASPECTS,
                depthStencilView.baseMipLevel(),
                1,
                depthStencilView.baseArrayLayer(),
                1,
                VulkanImageStates.DEPTH_STENCIL_ATTACHMENT,
                DEPTH_STENCIL_READ
        );

        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkRenderingAttachmentInfo depthAttachment = VkRenderingAttachmentInfo.calloc(stack)
                    .sType$Default()
                    .imageView(depthStencilView.vkImageView())
                    .imageLayout(VulkanImageStates.DEPTH_STENCIL_ATTACHMENT.layout())
                    .loadOp(VK12.VK_ATTACHMENT_LOAD_OP_LOAD)
                    .storeOp(VK12.VK_ATTACHMENT_STORE_OP_STORE);
            VkRenderingAttachmentInfo stencilAttachment = VkRenderingAttachmentInfo.calloc(stack)
                    .sType$Default()
                    .imageView(depthStencilView.vkImageView())
                    .imageLayout(VulkanImageStates.DEPTH_STENCIL_ATTACHMENT.layout())
                    .loadOp(VK12.VK_ATTACHMENT_LOAD_OP_LOAD)
                    .storeOp(VK12.VK_ATTACHMENT_STORE_OP_STORE);
            VkRenderingInfo rendering = VkRenderingInfo.calloc(stack)
                    .sType$Default()
                    .layerCount(1)
                    .viewMask(0)
                    .pDepthAttachment(depthAttachment)
                    .pStencilAttachment(stencilAttachment);
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

            VulkanPushDescriptors cullDescriptors = new VulkanPushDescriptors()
                    .uniformBuffer(0, this.uniformBuffer, 0L, UNIFORM_BYTES)
                    .storageBuffer(1, this.geometryData.getMetadataBuffer(), 0L,
                            this.geometryData.getMetadataBuffer().size())
                    .storageBuffer(2, viewport.visibilityBuffer, 0L, viewport.visibilityBuffer.size())
                    .storageBuffer(3, viewport.indirectLookupBuffer, 0L, viewport.indirectLookupBuffer.size());
            this.cullPipeline.bind(commandBuffer, cullDescriptors, null);
            VK12.vkCmdBindIndexBuffer(
                    commandBuffer,
                    this.sharedIndexBuffer.buffer().vkBuffer(),
                    0L,
                    VK12.VK_INDEX_TYPE_UINT16
            );
            VK12.vkCmdDrawIndexedIndirect(
                    commandBuffer,
                    viewport.drawCountCallBuffer.vkBuffer(),
                    CULL_COMMAND_OFFSET,
                    1,
                    DRAW_INDEXED_COMMAND_BYTES
            );
            KHRDynamicRendering.vkCmdEndRenderingKHR(commandBuffer);
        }

        VulkanSync.bufferBarrier(
                commandBuffer,
                viewport.visibilityBuffer.vkBuffer(),
                0L,
                viewport.visibilityBuffer.size(),
                VulkanSync.GRAPHICS_STORAGE_READ_WRITE,
                VulkanSync.COMPUTE_STORAGE_READ
        );
        VulkanSync.imageBarrier(
                commandBuffer,
                depthStencilView.image().vkImage(),
                DEPTH_STENCIL_ASPECTS,
                depthStencilView.baseMipLevel(),
                1,
                depthStencilView.baseArrayLayer(),
                1,
                DEPTH_STENCIL_READ,
                VulkanImageStates.DEPTH_STENCIL_ATTACHMENT
        );
    }

    private void barrierBeforeCommandGeneration(
            org.lwjgl.vulkan.VkCommandBuffer commandBuffer,
            VulkanMDICViewport viewport
    ) {
        VulkanSync.bufferBarrier(
                commandBuffer,
                viewport.drawCallBuffer.vkBuffer(),
                0L,
                viewport.drawCallBuffer.size(),
                viewport.drawCallBuffer.policy().declaredAccess(),
                VulkanSync.COMPUTE_STORAGE_WRITE
        );
        VulkanSync.bufferBarrier(
                commandBuffer,
                viewport.drawCountCallBuffer.vkBuffer(),
                0L,
                viewport.drawCountCallBuffer.size(),
                VulkanSync.COMPUTE_STORAGE_READ_WRITE.or(VulkanSync.INDIRECT_READ),
                VulkanSync.COMPUTE_STORAGE_READ_WRITE.or(VulkanSync.INDIRECT_READ)
        );
        VulkanSync.bufferBarrier(
                commandBuffer,
                viewport.positionScratchBuffer.vkBuffer(),
                0L,
                viewport.positionScratchBuffer.size(),
                viewport.positionScratchBuffer.policy().declaredAccess(),
                VulkanSync.COMPUTE_STORAGE_WRITE
        );
    }

    private void barrierForTerrainDraws(
            org.lwjgl.vulkan.VkCommandBuffer commandBuffer,
            VulkanMDICViewport viewport
    ) {
        VulkanSync.bufferBarrier(
                commandBuffer,
                viewport.drawCallBuffer.vkBuffer(),
                0L,
                viewport.drawCallBuffer.size(),
                VulkanSync.COMPUTE_STORAGE_READ_WRITE,
                VulkanSync.INDIRECT_READ
        );
        VulkanSync.bufferBarrier(
                commandBuffer,
                viewport.drawCountCallBuffer.vkBuffer(),
                0L,
                viewport.drawCountCallBuffer.size(),
                VulkanSync.COMPUTE_STORAGE_READ_WRITE.or(VulkanSync.INDIRECT_READ),
                VulkanSync.INDIRECT_READ
        );
        VulkanSync.bufferBarrier(
                commandBuffer,
                viewport.positionScratchBuffer.vkBuffer(),
                0L,
                viewport.positionScratchBuffer.size(),
                VulkanSync.COMPUTE_STORAGE_WRITE,
                VulkanSync.GRAPHICS_STORAGE_READ
        );
    }

    private void uploadUniform(VulkanMDICViewport viewport) {
        long pointer = this.uploads.upload(this.uniformBuffer, 0L, UNIFORM_BYTES).address();
        long start = pointer;
        new Matrix4f(viewport.MVP)
                .translate(-viewport.innerTranslation.x, -viewport.innerTranslation.y, -viewport.innerTranslation.z)
                .getToAddress(pointer);
        pointer += 4L * 4L * 4L;
        viewport.section.getToAddress(pointer);
        pointer += 4L * 3L;
        if (viewport.frameId < 0) {
            Logger.error("Vulkan MDIC frame ID became negative; preserving original 31-bit wrap contract");
            viewport.frameId &= 0x7fffffff;
        }
        MemoryUtil.memPutInt(pointer, viewport.frameId & 0x7fffffff);
        pointer += 4L;
        viewport.innerTranslation.getToAddress(pointer);
        pointer += 4L * 3L;
        MemoryUtil.memSet(pointer, 0, UNIFORM_BYTES - (pointer - start));
    }

    private void downloadStatistics() {
        this.downloads.download(this.statisticsBuffer, (pointer, size) -> {
            int layers = WorldEngine.MAX_LOD_LAYER + 1;
            for (int i = 0; i < layers; i++) {
                RenderStatistics.visibleSections[i] = MemoryUtil.memGetInt(pointer + i * 4L);
                RenderStatistics.quadCount[i] = MemoryUtil.memGetInt(pointer + layers * 4L + i * 4L);
            }
        });
    }

    private void validateDepthTarget(VoxyVulkanImageView view) {
        if (view.isClosed() || view.image().getFormat() != GpuFormat.D24_UNORM_S8_UINT) {
            throw new IllegalArgumentException("Vulkan MDIC cull requires the formal D24S8 offscreen depth/stencil target");
        }
        if (view.mipLevelCount() != 1 || view.layerCount() != 1
                || view.aspectMask() != DEPTH_STENCIL_ASPECTS) {
            throw new IllegalArgumentException("Vulkan MDIC cull requires one depth/stencil mip and layer");
        }
    }

    private static boolean supportsSubgroupPrefixSum() {
        var capabilities = VoxyVulkanContext.get().capabilities();
        int requiredOperations = VK11.VK_SUBGROUP_FEATURE_BASIC_BIT | VK11.VK_SUBGROUP_FEATURE_ARITHMETIC_BIT;
        return (capabilities.subgroupSupportedStages() & VK12.VK_SHADER_STAGE_COMPUTE_BIT) != 0
                && (capabilities.subgroupSupportedOperations() & requiredOperations) == requiredOperations
                && (capabilities.subgroupSize() == 32 || capabilities.subgroupSize() == 64);
    }

    private static int closerEqualCompare(RenderProperties properties) {
        return VulkanConst.toVk(properties.closerEqualDepthCompare());
    }

    public void flushDownloads() {
        this.ensureOpen();
        this.downloads.flushWaitClear();
    }

    public ModelStore modelStore() {
        return this.modelStore;
    }

    public BasicSectionGeometryData geometryData() {
        return this.geometryData;
    }

    private void ensureOpen() {
        if (this.closed) throw new IllegalStateException("Vulkan MDIC renderer is closed");
    }

    @Override
    public void close() {
        if (this.closed) return;
        this.closed = true;
        this.uploads.commit();
        this.downloads.waitDiscard();
        this.uniformBuffer.close();
        this.distanceCountBuffer.close();
        this.statisticsBuffer.close();
        this.sharedIndexBuffer.close();
        this.depthSampler.close();
        this.lightmapSampler.close();
        this.prepPipeline.close();
        this.commandGenPipeline.close();
        this.prefixSumPipeline.close();
        this.translucentGenPipeline.close();
        this.translucentTerrainPipeline.close();
        this.opaqueTerrainPipeline.close();
        this.cullPipeline.close();
    }
}
