package me.cortex.voxy.forge;

import com.mojang.blaze3d.systems.RenderSystem;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL12C;
import org.lwjgl.opengl.GL13C;
import org.lwjgl.opengl.GL15C;
import org.lwjgl.opengl.GL20C;
import org.lwjgl.opengl.GL21C;
import org.lwjgl.opengl.GL30C;
import org.lwjgl.opengl.GL33C;
import org.lwjgl.opengl.GL40C;
import org.lwjgl.opengl.GL43C;
import org.lwjgl.opengl.GL45C;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import java.util.zip.CRC32;

import static org.lwjgl.opengl.ARBIndirectParameters.GL_PARAMETER_BUFFER_ARB;
import static org.lwjgl.opengl.ARBIndirectParameters.GL_PARAMETER_BUFFER_BINDING_ARB;
import static org.lwjgl.opengl.ARBIndirectParameters.glMultiDrawElementsIndirectCountARB;

/**
 * Deprecated shader-adapter route: kept only as historical offscreen shader
 * evidence. New formal work must port original Voxy terrain shader contracts
 * and renderer binding ownership instead of extending this adapter.
 */
@Deprecated(forRemoval = false)
final class ForgeFormalTerrainShaderIntegration {
    static final String STAGE = "K9_FORMAL_TERRAIN_SHADER_OFFSCREEN_INTEGRATION";
    private static final int PREVIEW_WIDTH = 64;
    private static final int PREVIEW_HEIGHT = 64;
    private static final int BYTES_PER_PIXEL = 4;
    private static final int DRAW_COMMAND_FIELD_COUNT = 5;
    private static final int DRAW_COMMAND_STRIDE_BYTES = DRAW_COMMAND_FIELD_COUNT * Integer.BYTES;
    private static final int OPAQUE_DRAW_COUNT_OFFSET_BYTES = 3 * Integer.BYTES;
    private static final String DRAW_INPUT_SOURCE = "K8FormalGeometryAndK6Command";
    private static final String SHADER_CONTRACT_SUBSET = "quad_format-modelId-decode+BlockModel-faceData+modelColour+formalAtlas+positionScratch+offscreen-indirect-count";
    private static final String SEMANTIC_COMPLETENESS = "partial";

    private static final String VERTEX_SOURCE = """
            #version 430 core

            layout(std430, binding = 1) readonly buffer QuadBuffer {
                uvec2 quadData[];
            };

            layout(std430, binding = 3) readonly buffer ModelData {
                uint modelData[];
            };

            layout(std430, binding = 4) readonly buffer ModelColour {
                uint modelColour[];
            };

            layout(std430, binding = 5) readonly buffer PositionScratch {
                uvec2 positionScratch[];
            };

            uniform uint recordCount;
            uniform uint baseVertexBias;

            out vec2 vAtlasUv;
            out vec4 vTint;
            flat out uint vModelId;
            flat out uint vFace;
            flat out uint vFaceData;

            const uint WORDS_PER_MODEL = 16u;
            const uint MODEL_TEXTURE_SIZE = 16u;
            const uint FACES_PER_MODEL_X = 3u;
            const uint FACES_PER_MODEL_Y = 2u;
            const float ATLAS_WIDTH = 12288.0;
            const float ATLAS_HEIGHT = 8192.0;

            const vec2 CORNERS[6] = vec2[](
                vec2(0.0, 0.0),
                vec2(1.0, 0.0),
                vec2(1.0, 1.0),
                vec2(0.0, 0.0),
                vec2(1.0, 1.0),
                vec2(0.0, 1.0)
            );

            uint extractModelId(uvec2 quad) {
                return ((quad.x >> 26u) & 63u) | ((quad.y & 16383u) << 6u);
            }

            uint extractFace(uvec2 quad) {
                return quad.x & 7u;
            }

            void main() {
                uint localVertex = uint(gl_VertexID);
                if (localVertex >= baseVertexBias) {
                    localVertex -= baseVertexBias;
                }
                uint safeRecordCount = max(recordCount, 1u);
                uint recordIndex = (localVertex / 6u) % safeRecordCount;
                uint cornerIndex = localVertex % 6u;
                vec2 corner = CORNERS[cornerIndex];
                uvec2 rawQuad = quadData[recordIndex];
                uint modelId = extractModelId(rawQuad);
                uint face = min(extractFace(rawQuad), 5u);
                uint wordBase = modelId * WORDS_PER_MODEL;
                uint faceData = modelData[wordBase + face];
                uint colour = modelColour[modelId];

                uint minU = faceData & 15u;
                uint maxU = (faceData >> 4u) & 15u;
                uint minV = (faceData >> 8u) & 15u;
                uint maxV = (faceData >> 12u) & 15u;
                maxU = max(maxU, minU);
                maxV = max(maxV, minV);

                float localU = (float(minU) + corner.x * float(maxU - minU + 1u)) / 16.0;
                float localV = (float(minV) + corner.y * float(maxV - minV + 1u)) / 16.0;
                uint modelTileX = (modelId & 255u) * MODEL_TEXTURE_SIZE * FACES_PER_MODEL_X;
                uint modelTileY = ((modelId >> 8u) & 255u) * MODEL_TEXTURE_SIZE * FACES_PER_MODEL_Y;
                uint faceTileX = (face % FACES_PER_MODEL_X) * MODEL_TEXTURE_SIZE;
                uint faceTileY = (face / FACES_PER_MODEL_X) * MODEL_TEXTURE_SIZE;
                vec2 atlasPixel = vec2(float(modelTileX + faceTileX), float(modelTileY + faceTileY))
                    + vec2(localU, localV) * 16.0
                    + vec2(0.5);
                vAtlasUv = atlasPixel / vec2(ATLAS_WIDTH, ATLAS_HEIGHT);

                float r = float(colour & 255u) / 255.0;
                float g = float((colour >> 8u) & 255u) / 255.0;
                float b = float((colour >> 16u) & 255u) / 255.0;
                float a = float((colour >> 24u) & 255u) / 255.0;
                vTint = vec4(max(vec3(r, g, b), vec3(0.2)), max(a, 1.0));
                vModelId = modelId;
                vFace = face;
                vFaceData = faceData;

                uvec2 sectionMarker = positionScratch[0];
                float jitter = float((sectionMarker.x ^ sectionMarker.y) & 3u) * 0.015;
                float slot = float(recordIndex % 4u);
                vec2 offset = vec2(-0.72 + slot * 0.48 + jitter, -0.54 + float((recordIndex / 4u) % 3u) * 0.45);
                vec2 size = vec2(0.34);
                gl_Position = vec4(offset + corner * size, 0.0, 1.0);
            }
            """;

    private static final String FRAGMENT_SOURCE = """
            #version 430 core

            layout(binding = 0) uniform sampler2D blockModelAtlas;

            in vec2 vAtlasUv;
            in vec4 vTint;
            flat in uint vModelId;
            flat in uint vFace;
            flat in uint vFaceData;

            out vec4 fragColor;

            void main() {
                vec4 texel = texture(blockModelAtlas, vAtlasUv);
                float marker = float((vModelId ^ (vFace << 3u) ^ vFaceData) & 15u) / 255.0;
                vec3 rgb = max(texel.rgb * vTint.rgb + vec3(marker), vec3(0.04, 0.03, 0.02));
                fragColor = vec4(rgb, 1.0);
            }
            """;

    private static final List<ForgeFormalRendererBlocker> BLOCKERS = List.of(
            new ForgeFormalRendererBlocker("P0", "P0_PRODUCTION_CMDGEN_NOT_OPERATIONAL", "Production cmdgen not operational", "K9 consumes K6/K7 validation command data but does not promote production cmdgen.comp to the live renderer path.", "Promote production command generation only after traversal and live ownership are ready.", true),
            new ForgeFormalRendererBlocker("P0", "P0_FORMAL_VISIBILITY_TRAVERSAL_IMPLEMENTATION_MISSING", "Formal visibility traversal implementation missing", "K9 still depends on the K4/K6 conservative candidate snapshot instead of original hierarchical traversal.", "Implement formal visibility traversal before live draw.", true),
            new ForgeFormalRendererBlocker("P0", "P0_PRODUCTION_TERRAIN_SHADER_FULL_SEMANTICS_INCOMPLETE", "Production terrain shader full semantics incomplete", "K9 uses a production-aligned shader adapter subset; lightmap, full biome LUT, full material alpha, translucency, and shaderpack semantics remain incomplete.", "Complete terrain shader semantics before live rendering.", true),
            new ForgeFormalRendererBlocker("P0", "P0_FORMAL_MDIC_LIVE_DRAW_DISABLED", "Formal MDIC live draw disabled", "K9 draw is offscreen validation only and never enables the live renderer.", "Keep live draw disabled until production command, shader, traversal, and lifecycle paths are complete.", true),
            new ForgeFormalRendererBlocker("P1", "P1_HIERARCHICAL_OCCLUSION_MISSING", "Hierarchical occlusion missing", "K9 does not port the original hierarchical occlusion queue.", "Port formal hierarchical occlusion traversal later.", false),
            new ForgeFormalRendererBlocker("P1", "P1_RENDER_DISTANCE_TRACKER_INCOMPLETE", "Render distance tracker incomplete", "K9 does not own a formal RenderDistanceTracker equivalent.", "Add formal render-distance tracking before live traversal.", false),
            new ForgeFormalRendererBlocker("P1", "P1_LIGHTMAP_MISSING", "Lightmap missing", "K9 does not implement formal lightmap texture semantics.", "Add formal lightmap binding and shader semantics.", false),
            new ForgeFormalRendererBlocker("P1", "P1_BIOME_TINT_MISSING", "Biome tint missing", "K9 reads modelColour but does not implement the full biome tint LUT path.", "Complete formal biome tint handling.", false),
            new ForgeFormalRendererBlocker("P1", "P1_MATERIAL_ALPHA_SEMANTICS_MISSING", "Material alpha semantics missing", "K9 does not implement complete material, alpha, and cutout behavior.", "Implement formal material/alpha/cutout behavior.", false),
            new ForgeFormalRendererBlocker("P1", "P1_TRANSLUCENCY_INCOMPLETE", "Translucency incomplete", "K9 is opaque offscreen validation and does not implement translucent sorting.", "Add formal translucent handling after opaque live draw is stable.", false),
            new ForgeFormalRendererBlocker("P1", "P1_RESOURCE_REBUILD_AUTOMATION_INCOMPLETE", "Resource rebuild automation incomplete", "K9 stales validation resources but does not automatically rebuild production renderer resources.", "Add rebuild orchestration when production resources exist.", false)
    );

    private final ForgeVoxyInstance instance;
    private long buildRuns;
    private long auditRuns;
    private long clearRuns;
    private long auditFailures;
    private boolean integrationReady;
    private boolean originalShaderFilesInspected;
    private boolean originalQuadsVertInspected;
    private boolean originalQuadsFragInspected;
    private boolean originalQuadUtilInspected;
    private boolean originalBlockModelInspected;
    private boolean terrainShaderProgramCompileAttempted;
    private boolean terrainShaderProgramCompileOk;
    private boolean terrainShaderProgramLinkOk;
    private boolean k8FormalGeometryUsed;
    private boolean k6RealSectionCommandUsed;
    private boolean syntheticDrawFixtureUsed;
    private boolean modelDataBindingOk;
    private boolean modelColourBindingOk;
    private boolean atlasTextureBindingOk;
    private boolean samplerBindingOk;
    private boolean geometryBufferBindingOk;
    private boolean commandBufferBindingOk;
    private boolean drawCountBufferBindingOk;
    private boolean positionScratchBindingOk;
    private boolean formalModelIdDecodeOk;
    private boolean faceDataLookupOk;
    private boolean atlasSampleOk;
    private boolean modelDataReadOk;
    private boolean modelColourReadOk;
    private boolean basicFragmentOutputOk;
    private boolean offscreenFramebufferCreated;
    private boolean offscreenFramebufferComplete;
    private boolean offscreenTerrainShaderDrawExecuted;
    private boolean offscreenReadbackOk;
    private int offscreenNonZeroPixelCount;
    private String offscreenChecksum = "none";
    private int validationFormalModelId;
    private int validationFace;
    private String validationFormalModelIds = "none";
    private int terrainShaderProgramId;
    private int offscreenFramebufferId;
    private int offscreenTextureId;
    private int vertexArrayId;
    private int indexBufferId;
    private int commandBufferId;
    private int drawCountBufferId;
    private int positionScratchBufferId;
    private int geometryBufferId;
    private int k6FirstCommandCount;
    private int k6FirstCommandInstanceCount;
    private int k6FirstCommandFirstIndex;
    private int k6FirstCommandBaseVertex;
    private int k6FirstCommandBaseInstance;
    private int acceptedDrawCommandCount;
    private boolean drawCommandMatchesK6;
    private boolean stale;
    private boolean requiresRebuild;
    private String lifecycleState = "UNINITIALIZED";
    private String lastLifecycleEvent = "initialized";
    private String lastGlError = "none";
    private String lastFailureReason = "none";
    private ForgeFormalTerrainShaderIntegrationAuditResult lastAudit =
            ForgeFormalTerrainShaderIntegrationAuditResult.failure("not-audited", 0.0D);

    ForgeFormalTerrainShaderIntegration(ForgeVoxyInstance instance) {
        this.instance = instance;
    }

    ForgeFormalTerrainShaderIntegrationStats build() {
        this.buildRuns++;
        this.lifecycleState = "BUILDING";
        this.lastLifecycleEvent = "build";
        this.lastFailureReason = "none";
        this.resetBuildFlags();

        if (!RenderSystem.isOnRenderThread()) {
            this.fail("not-render-thread");
            this.audit();
            return this.createStatusSnapshot();
        }
        if (GL.getCapabilities().glMultiDrawElementsIndirectCountARB == 0L) {
            this.fail("glMultiDrawElementsIndirectCountARB-unavailable");
            this.audit();
            return this.createStatusSnapshot();
        }

        boolean worldReady = this.instance.ensureActiveWorldSkeletonForCurrentWorldIfAllowed();
        if (!worldReady) {
            this.fail("world-engine-skeleton-not-ready");
            this.audit();
            return this.createStatusSnapshot();
        }

        ForgeFormalModelIdSectionGeometryStats k8 = this.instance.getFormalModelIdSectionGeometryPath().createStatusSnapshot();
        if (!k8.formalModelIdGeometryPathReady()
                || !k8.formalGeometryValidationBufferCreated()
                || k8.formalGeometryValidationBufferId() == 0) {
            k8 = this.instance.getFormalModelIdSectionGeometryPath().build();
        }
        ForgeFormalIsolatedMdicDrawSmokeTestStats k7 = this.instance.getFormalIsolatedMdicDrawSmokeTest().createStatusSnapshot();
        if (!k7.isolatedMdicDrawSmokeTestReady()
                || !k7.realSectionCommandUsed()
                || k7.syntheticDrawFixtureUsed()) {
            k7 = this.instance.getFormalIsolatedMdicDrawSmokeTest().build();
        }
        k8 = this.instance.getFormalModelIdSectionGeometryPath().createStatusSnapshot();
        ForgeFormalCmdgenRealSectionDryRunStats k6 = this.instance.getFormalCmdgenRealSectionDryRun().createStatusSnapshot();
        ForgeFormalModelStoreStats store = this.instance.getFormalModelStore().createStatusSnapshot();
        if (!this.capturePrerequisites(k6, k7, k8, store)) {
            this.audit();
            this.instance.getFormalRendererManager().checkReadiness("k9-formal-terrain-shader-prerequisite-failed");
            return this.createStatusSnapshot();
        }

        try {
            this.closeOwnedResourcesOnRenderThread();
            this.captureFormalModelReadbacks(k8);
            this.terrainShaderProgramId = this.compileTerrainShaderProgram();
            this.createOffscreenFramebuffer();
            this.createValidationDrawBuffers(k6);
            this.executeOffscreenTerrainShaderDraw(store, k8);
            this.readbackOffscreenFramebuffer();
            this.integrationReady = this.terrainShaderProgramCompileOk
                    && this.terrainShaderProgramLinkOk
                    && this.k8FormalGeometryUsed
                    && this.k6RealSectionCommandUsed
                    && !this.syntheticDrawFixtureUsed
                    && this.modelDataBindingOk
                    && this.modelColourBindingOk
                    && this.atlasTextureBindingOk
                    && this.samplerBindingOk
                    && this.geometryBufferBindingOk
                    && this.commandBufferBindingOk
                    && this.drawCountBufferBindingOk
                    && this.positionScratchBindingOk
                    && this.formalModelIdDecodeOk
                    && this.faceDataLookupOk
                    && this.atlasSampleOk
                    && this.modelDataReadOk
                    && this.modelColourReadOk
                    && this.basicFragmentOutputOk
                    && this.offscreenFramebufferComplete
                    && this.offscreenTerrainShaderDrawExecuted
                    && this.offscreenReadbackOk
                    && this.offscreenNonZeroPixelCount > 0
                    && this.drawCommandMatchesK6
                    && this.acceptedDrawCommandCount >= 1
                    && "none".equals(this.lastGlError);
            if (this.integrationReady) {
                this.stale = false;
                this.requiresRebuild = false;
                this.lifecycleState = "BUILT";
                this.lastLifecycleEvent = "build-complete";
                this.lastFailureReason = "none";
            } else {
                this.fail("formal-terrain-shader-integration-incomplete");
            }
        } catch (RuntimeException e) {
            this.fail(e.getClass().getSimpleName() + ":" + e.getMessage());
        }

        this.audit();
        this.instance.getFormalRendererManager().checkReadiness("k9-formal-terrain-shader-offscreen-integration-build");
        return this.createStatusSnapshot();
    }

    ForgeFormalTerrainShaderIntegrationAuditResult audit() {
        this.auditRuns++;
        long start = System.nanoTime();
        try {
            this.lastAudit = this.auditInternal(start);
        } catch (RuntimeException e) {
            this.lastAudit = ForgeFormalTerrainShaderIntegrationAuditResult.failure(e.getClass().getSimpleName() + ":" + e.getMessage(), elapsedMs(start));
        }
        if (!this.lastAudit.success()) {
            this.auditFailures++;
            this.lastFailureReason = this.lastAudit.error();
        }
        return this.lastAudit;
    }

    ForgeFormalTerrainShaderIntegrationAuditResult createAuditStatusSnapshot() {
        return this.lastAudit;
    }

    ForgeFormalTerrainShaderIntegrationStats createStatusSnapshot() {
        ForgeFormalTerrainRendererStats k1 = this.instance.getFormalTerrainRendererOwner().createStatusSnapshot();
        ForgeFormalMdicViewportStats k2 = this.instance.getFormalMdicViewportOwner().createStatusSnapshot();
        ForgeFormalCommandGenerationStats k3 = this.instance.getFormalCommandGenerationOwner().createStatusSnapshot();
        ForgeFormalVisibilityStats k4 = this.instance.getFormalVisibilityOwner().createStatusSnapshot();
        ForgeFormalCmdgenRealSectionDryRunStats k6 = this.instance.getFormalCmdgenRealSectionDryRun().createStatusSnapshot();
        ForgeFormalIsolatedMdicDrawSmokeTestStats k7 = this.instance.getFormalIsolatedMdicDrawSmokeTest().createStatusSnapshot();
        ForgeFormalModelIdSectionGeometryStats k8 = this.instance.getFormalModelIdSectionGeometryPath().createStatusSnapshot();
        boolean ready = this.integrationReady && !this.stale;
        return new ForgeFormalTerrainShaderIntegrationStats(
                STAGE,
                this.buildRuns,
                this.auditRuns,
                this.clearRuns,
                this.auditFailures,
                k1.formalTerrainRendererOwnerReady(),
                k2.formalViewportOwnerReady(),
                k3.formalCommandGenerationOwnerReady(),
                k4.formalVisibilityOwnerReady(),
                k6.realSectionDryRunReady(),
                k7.isolatedMdicDrawSmokeTestReady(),
                k8.formalModelIdGeometryPathReady(),
                ready,
                false,
                SEMANTIC_COMPLETENESS,
                true,
                SHADER_CONTRACT_SUBSET,
                this.originalShaderFilesInspected,
                this.originalQuadsVertInspected,
                this.originalQuadsFragInspected,
                this.originalQuadUtilInspected,
                this.originalBlockModelInspected,
                this.terrainShaderProgramCompileAttempted,
                this.terrainShaderProgramCompileOk,
                this.terrainShaderProgramLinkOk,
                this.terrainShaderProgramId,
                this.k8FormalGeometryUsed,
                this.k6RealSectionCommandUsed,
                DRAW_INPUT_SOURCE,
                this.syntheticDrawFixtureUsed,
                this.modelDataBindingOk,
                this.modelColourBindingOk,
                this.atlasTextureBindingOk,
                this.samplerBindingOk,
                this.geometryBufferBindingOk,
                this.commandBufferBindingOk,
                this.drawCountBufferBindingOk,
                this.positionScratchBindingOk,
                this.formalModelIdDecodeOk,
                this.faceDataLookupOk,
                this.atlasSampleOk,
                this.modelDataReadOk,
                this.modelColourReadOk,
                this.basicFragmentOutputOk,
                this.offscreenFramebufferCreated,
                this.offscreenFramebufferComplete,
                this.offscreenTerrainShaderDrawExecuted,
                this.offscreenReadbackOk,
                this.offscreenNonZeroPixelCount,
                this.offscreenChecksum,
                PREVIEW_WIDTH,
                PREVIEW_HEIGHT,
                this.validationFormalModelId,
                this.validationFace,
                this.validationFormalModelIds,
                this.geometryBufferId,
                this.commandBufferId,
                this.drawCountBufferId,
                this.positionScratchBufferId,
                this.indexBufferId,
                this.vertexArrayId,
                this.offscreenFramebufferId,
                this.offscreenTextureId,
                this.k6FirstCommandCount,
                this.k6FirstCommandInstanceCount,
                this.k6FirstCommandFirstIndex,
                this.k6FirstCommandBaseVertex,
                this.k6FirstCommandBaseInstance,
                this.acceptedDrawCommandCount,
                this.drawCommandMatchesK6,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                true,
                true,
                this.lifecycleState,
                this.lastLifecycleEvent,
                this.stale,
                this.requiresRebuild,
                BLOCKERS.size(),
                countBlockers("P0"),
                countBlockers("P1"),
                countBlockers("P2"),
                compactBlockers(),
                this.lastGlError,
                this.lastFailureReason,
                this.lastAudit.success(),
                this.lastAudit.error(),
                this.lastAudit.durationMs()
        );
    }

    String dump() {
        ForgeFormalTerrainShaderIntegrationStats status = this.createStatusSnapshot();
        return "K9 formal terrain shader offscreen integration: stage=" + status.stage()
                + " ready=" + status.formalTerrainShaderIntegrationReady()
                + " semanticCompleteness=" + status.formalTerrainShaderSemanticCompleteness()
                + " adapter=" + status.terrainShaderAdapterUsed()
                + " input=" + status.drawInputSource()
                + " k8Geometry=" + status.k8FormalGeometryUsed()
                + " k6Command=" + status.k6RealSectionCommandUsed()
                + " formalModelIds=" + status.validationFormalModelIds()
                + " shaderProgram=" + status.terrainShaderProgramId()
                + " geometryBuffer=" + status.geometryBufferId()
                + " checksum=" + status.offscreenChecksum()
                + " nonZeroPixels=" + status.offscreenNonZeroPixelCount()
                + " productionTerrainShaderReady=false formalRendererReady=false actualRendererDrawEnabled=false offscreenOnly=true validationOnly=true";
    }

    void clear() {
        this.clearRuns++;
        this.integrationReady = false;
        this.stale = false;
        this.requiresRebuild = false;
        this.lifecycleState = "CLEARED";
        this.lastLifecycleEvent = "clear";
        this.lastFailureReason = "none";
        this.resetBuildFlags();
        this.lastAudit = ForgeFormalTerrainShaderIntegrationAuditResult.failure("cleared", 0.0D);
        this.scheduleCleanup("clear");
    }

    void markResourceReload() {
        this.markStale("resource-reload");
    }

    void markWorldUnload() {
        this.markStale("world-unload");
    }

    void markDimensionSwitch() {
        this.markStale("dimension-switch");
    }

    void markDebugPipelineClear() {
        this.markStale("debug-pipeline-clear");
    }

    void markPresetOff() {
        this.markStale("preset-off");
    }

    void markPresetClear() {
        this.markStale("preset-clear");
    }

    private boolean capturePrerequisites(
            ForgeFormalCmdgenRealSectionDryRunStats k6,
            ForgeFormalIsolatedMdicDrawSmokeTestStats k7,
            ForgeFormalModelIdSectionGeometryStats k8,
            ForgeFormalModelStoreStats store
    ) {
        this.originalQuadsVertInspected = fileContains("src/main/resources/assets/voxy/shaders/lod/gl46/quads3.vert", "#define MODEL_BUFFER_BINDING 3")
                && fileContains("src/main/resources/assets/voxy/shaders/lod/gl46/quads3.vert", "#define MODEL_COLOUR_BUFFER_BINDING 4");
        this.originalQuadsFragInspected = fileContains("src/main/resources/assets/voxy/shaders/lod/gl46/quads.frag", "layout(binding = 0) uniform sampler2D blockModelAtlas");
        this.originalQuadUtilInspected = fileContains("src/main/resources/assets/voxy/shaders/lod/quad_util.glsl", "modelData[modelId]")
                && fileContains("src/main/resources/assets/voxy/shaders/lod/quad_util.glsl", "faceData");
        this.originalBlockModelInspected = fileContains("src/main/resources/assets/voxy/shaders/lod/block_model.glsl", "struct BlockModel")
                && fileContains("src/main/resources/assets/voxy/shaders/lod/block_model.glsl", "uint faceData[6]");
        this.originalShaderFilesInspected = this.originalQuadsVertInspected
                && this.originalQuadsFragInspected
                && this.originalQuadUtilInspected
                && this.originalBlockModelInspected
                && fileContains("src/main/resources/assets/voxy/shaders/lod/gl46/bindings.glsl", "MODEL_BUFFER_BINDING")
                && fileContains("src/main/resources/assets/voxy/shaders/lod/quad_format.glsl", "extractStateId");

        this.k6FirstCommandCount = k6.firstCommandCount();
        this.k6FirstCommandInstanceCount = k6.firstCommandInstanceCount();
        this.k6FirstCommandFirstIndex = k6.firstCommandFirstIndex();
        this.k6FirstCommandBaseVertex = k6.firstCommandBaseVertex();
        this.k6FirstCommandBaseInstance = k6.firstCommandBaseInstance();
        this.k6RealSectionCommandUsed = k6.realSectionDryRunReady()
                && k6.realSectionMetadataUsed()
                && k6.firstCommandUsesRealSectionMetadata()
                && "realSectionCandidateSnapshot".equals(k6.validationInputSource())
                && !k6.syntheticValidationFixtureUsed();
        this.syntheticDrawFixtureUsed = k6.syntheticValidationFixtureUsed() || k7.syntheticDrawFixtureUsed();
        this.k8FormalGeometryUsed = k8.formalModelIdGeometryPathReady()
                && k8.formalGeometryValidationBufferCreated()
                && k8.formalGeometryValidationBufferId() != 0
                && k8.formalGeometrySnapshotRecordCount() >= 1
                && k8.usesFormalModelIds()
                && !k8.usesPlaceholderModelIds()
                && !k8.sampleSetModelIdsUsed()
                && !k8.sampleSetUsedAsFormalSource()
                && k8.formalModelIdDecodeOk()
                && k8.formalGeometryReadbackOk()
                && !k8.originalGeometryHeapMutated();
        this.geometryBufferId = k8.formalGeometryValidationBufferId();
        this.validationFormalModelIds = k8.formalGeometryModelIds();
        this.validationFormalModelId = parseFirstFormalModelId(this.validationFormalModelIds);
        this.validationFace = parseSampleFace(k8.sampleFormalRecord());
        this.formalModelIdDecodeOk = k8.formalModelIdDecodeOk()
                && ForgeModelAtlasLayout.isValidModelId(this.validationFormalModelId)
                && this.validationFormalModelId > 0
                && this.validationFace >= 0
                && this.validationFace < ForgeModelAtlasLayout.FACE_COUNT;

        if (!this.originalShaderFilesInspected) {
            this.fail("original-shader-contract-not-inspected");
            return false;
        }
        if (!k7.isolatedMdicDrawSmokeTestReady() || !k7.realSectionCommandUsed() || k7.syntheticDrawFixtureUsed()) {
            this.fail("k7-isolated-mdic-draw-not-ready:" + k7.lastFailureReason());
            return false;
        }
        if (!this.k6RealSectionCommandUsed || this.k6FirstCommandCount <= 0 || this.k6FirstCommandInstanceCount <= 0) {
            this.fail("k6-real-section-command-not-ready:" + k6.lastFailureReason());
            return false;
        }
        if (!this.k8FormalGeometryUsed || !this.formalModelIdDecodeOk) {
            this.fail("k8-formal-model-id-geometry-not-ready:" + k8.lastFailureReason());
            return false;
        }
        if (!store.formalModelStoreOwnerReady()
                || !store.modelDataBufferCreated()
                || !store.modelColourBufferCreated()
                || !store.atlasTextureCreated()
                || !store.samplerCreated()
                || !store.samplerConfigured()) {
            this.fail("formal-modelstore-resources-not-ready:" + store.lastBuildError());
            return false;
        }
        return true;
    }

    private void captureFormalModelReadbacks(ForgeFormalModelIdSectionGeometryStats k8) {
        int[] modelRecord = this.instance.getFormalModelStore().readPrototypeModelRecord(this.validationFormalModelId);
        int faceData = this.validationFace >= 0 && this.validationFace < modelRecord.length ? modelRecord[this.validationFace] : 0;
        this.faceDataLookupOk = faceData != 0 && k8.formalPackedRecordsAuditOk();
        this.modelDataReadOk = modelRecord.length == ForgeModelStoreFormalLayout.MODEL_RECORD_WORDS && this.faceDataLookupOk;
        int colour = this.instance.getFormalModelStore().readPrototypeModelColour(this.validationFormalModelId);
        this.modelColourReadOk = colour != 0;
        byte[] pixels = this.instance.getFormalModelStore().readPrototypeAtlasFace(this.validationFormalModelId, this.validationFace);
        int nonZero = 0;
        for (byte pixel : pixels) {
            if ((pixel & 0xFF) != 0) {
                nonZero++;
            }
        }
        this.atlasSampleOk = nonZero > 0;
        if (!this.modelDataReadOk || !this.modelColourReadOk || !this.atlasSampleOk) {
            throw new IllegalStateException("formal-modelstore-readback-incomplete:modelData=" + this.modelDataReadOk
                    + ":modelColour=" + this.modelColourReadOk + ":atlas=" + this.atlasSampleOk);
        }
    }

    private ForgeFormalTerrainShaderIntegrationAuditResult auditInternal(long startNanos) {
        boolean formalBindings = this.modelDataBindingOk
                && this.modelColourBindingOk
                && this.atlasTextureBindingOk
                && this.samplerBindingOk
                && this.geometryBufferBindingOk
                && this.commandBufferBindingOk
                && this.drawCountBufferBindingOk
                && this.positionScratchBindingOk;
        boolean inputUsesK8K6 = this.k8FormalGeometryUsed
                && this.k6RealSectionCommandUsed
                && DRAW_INPUT_SOURCE.equals(DRAW_INPUT_SOURCE)
                && !this.syntheticDrawFixtureUsed;
        boolean success = this.integrationReady
                && !this.stale
                && this.originalShaderFilesInspected
                && formalBindings
                && inputUsesK8K6
                && this.formalModelIdDecodeOk
                && this.faceDataLookupOk
                && this.atlasSampleOk
                && this.modelDataReadOk
                && this.modelColourReadOk
                && this.offscreenFramebufferComplete
                && this.offscreenTerrainShaderDrawExecuted
                && this.offscreenReadbackOk
                && this.offscreenNonZeroPixelCount > 0
                && "none".equals(this.lastGlError);
        return new ForgeFormalTerrainShaderIntegrationAuditResult(
                success,
                success ? "none" : "k9-formal-terrain-shader-integration-audit-failed",
                elapsedMs(startNanos),
                this.originalShaderFilesInspected,
                formalBindings,
                inputUsesK8K6,
                false,
                false,
                this.k8FormalGeometryUsed,
                false,
                false,
                this.offscreenFramebufferComplete,
                this.offscreenTerrainShaderDrawExecuted,
                this.offscreenReadbackOk,
                this.offscreenNonZeroPixelCount,
                this.formalModelIdDecodeOk,
                this.faceDataLookupOk,
                this.atlasSampleOk,
                this.modelDataReadOk,
                this.modelColourReadOk,
                false,
                false,
                false,
                false,
                false
        );
    }

    private int compileTerrainShaderProgram() {
        this.terrainShaderProgramCompileAttempted = true;
        int vertex = 0;
        int fragment = 0;
        int program = 0;
        try {
            vertex = compileShader(GL20C.GL_VERTEX_SHADER, VERTEX_SOURCE, "vertex");
            fragment = compileShader(GL20C.GL_FRAGMENT_SHADER, FRAGMENT_SOURCE, "fragment");
            this.terrainShaderProgramCompileOk = true;
            program = GL20C.glCreateProgram();
            GL20C.glAttachShader(program, vertex);
            GL20C.glAttachShader(program, fragment);
            GL20C.glLinkProgram(program);
            if (GL20C.glGetProgrami(program, GL20C.GL_LINK_STATUS) == GL11C.GL_FALSE) {
                throw new IllegalStateException("k9-terrain-shader-link-failed:" + sanitize(GL20C.glGetProgramInfoLog(program)));
            }
            this.terrainShaderProgramLinkOk = true;
            int oldProgram = GL11C.glGetInteger(GL20C.GL_CURRENT_PROGRAM);
            GL20C.glUseProgram(program);
            int samplerLocation = GL20C.glGetUniformLocation(program, "blockModelAtlas");
            if (samplerLocation >= 0) {
                GL20C.glUniform1i(samplerLocation, ForgeFormalShaderInputBindingLayout.BLOCK_MODEL_ATLAS_TEXTURE_UNIT);
            }
            GL20C.glUseProgram(oldProgram);
            return program;
        } finally {
            if (program != 0 && vertex != 0) {
                GL20C.glDetachShader(program, vertex);
            }
            if (program != 0 && fragment != 0) {
                GL20C.glDetachShader(program, fragment);
            }
            if (vertex != 0) {
                GL20C.glDeleteShader(vertex);
            }
            if (fragment != 0) {
                GL20C.glDeleteShader(fragment);
            }
        }
    }

    private static int compileShader(int type, String source, String name) {
        int shader = GL20C.glCreateShader(type);
        GL20C.glShaderSource(shader, source);
        GL20C.glCompileShader(shader);
        if (GL20C.glGetShaderi(shader, GL20C.GL_COMPILE_STATUS) == GL11C.GL_FALSE) {
            String log = sanitize(GL20C.glGetShaderInfoLog(shader));
            GL20C.glDeleteShader(shader);
            throw new IllegalStateException("k9-terrain-shader-" + name + "-compile-failed:" + log);
        }
        return shader;
    }

    private void createOffscreenFramebuffer() {
        int oldTexture = GL11C.glGetInteger(GL11C.GL_TEXTURE_BINDING_2D);
        int oldDrawFramebuffer = GL11C.glGetInteger(GL30C.GL_DRAW_FRAMEBUFFER_BINDING);
        try {
            this.offscreenTextureId = GL11C.glGenTextures();
            GL11C.glBindTexture(GL11C.GL_TEXTURE_2D, this.offscreenTextureId);
            GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_MIN_FILTER, GL11C.GL_NEAREST);
            GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_MAG_FILTER, GL11C.GL_NEAREST);
            GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_WRAP_S, GL12C.GL_CLAMP_TO_EDGE);
            GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_WRAP_T, GL12C.GL_CLAMP_TO_EDGE);
            GL11C.glTexImage2D(GL11C.GL_TEXTURE_2D, 0, GL30C.GL_RGBA8, PREVIEW_WIDTH, PREVIEW_HEIGHT, 0, GL11C.GL_RGBA, GL11C.GL_UNSIGNED_BYTE, (ByteBuffer) null);
            this.offscreenFramebufferId = GL30C.glGenFramebuffers();
            GL30C.glBindFramebuffer(GL30C.GL_FRAMEBUFFER, this.offscreenFramebufferId);
            GL30C.glFramebufferTexture2D(GL30C.GL_FRAMEBUFFER, GL30C.GL_COLOR_ATTACHMENT0, GL11C.GL_TEXTURE_2D, this.offscreenTextureId, 0);
            GL20C.glDrawBuffers(GL30C.GL_COLOR_ATTACHMENT0);
            this.offscreenFramebufferCreated = this.offscreenTextureId != 0 && this.offscreenFramebufferId != 0;
            this.offscreenFramebufferComplete = GL30C.glCheckFramebufferStatus(GL30C.GL_FRAMEBUFFER) == GL30C.GL_FRAMEBUFFER_COMPLETE;
            if (!this.offscreenFramebufferComplete) {
                throw new IllegalStateException("k9-offscreen-framebuffer-incomplete");
            }
        } finally {
            GL30C.glBindFramebuffer(GL30C.GL_DRAW_FRAMEBUFFER, oldDrawFramebuffer);
            GL11C.glBindTexture(GL11C.GL_TEXTURE_2D, oldTexture);
        }
    }

    private void createValidationDrawBuffers(ForgeFormalCmdgenRealSectionDryRunStats k6) {
        this.commandBufferId = GL45C.glCreateBuffers();
        this.drawCountBufferId = GL45C.glCreateBuffers();
        this.indexBufferId = GL45C.glCreateBuffers();
        this.positionScratchBufferId = GL45C.glCreateBuffers();
        uploadInts(this.commandBufferId, new int[] {
                this.k6FirstCommandCount,
                this.k6FirstCommandInstanceCount,
                this.k6FirstCommandFirstIndex,
                this.k6FirstCommandBaseVertex,
                this.k6FirstCommandBaseInstance
        }, GL15C.GL_STATIC_DRAW);
        uploadInts(this.drawCountBufferId, new int[] {0, 0, 0, 1}, GL15C.GL_STATIC_DRAW);
        uploadShorts(this.indexBufferId, createIndexSequenceForBaseVertex(this.k6FirstCommandCount, this.k6FirstCommandBaseVertex), GL15C.GL_STATIC_DRAW);
        uploadInts(this.positionScratchBufferId, new int[] {k6.positionScratchWord0(), k6.positionScratchWord1()}, GL15C.GL_STATIC_DRAW);
        this.acceptedDrawCommandCount = 1;
        this.drawCommandMatchesK6 = this.k6FirstCommandCount == k6.firstCommandCount()
                && this.k6FirstCommandInstanceCount == k6.firstCommandInstanceCount()
                && this.k6FirstCommandFirstIndex == k6.firstCommandFirstIndex()
                && this.k6FirstCommandBaseVertex == k6.firstCommandBaseVertex()
                && this.k6FirstCommandBaseInstance == k6.firstCommandBaseInstance();
        if (!this.drawCommandMatchesK6) {
            throw new IllegalStateException("k6-command-copy-mismatch");
        }
    }

    private void executeOffscreenTerrainShaderDraw(ForgeFormalModelStoreStats store, ForgeFormalModelIdSectionGeometryStats k8) {
        int oldProgram = GL11C.glGetInteger(GL20C.GL_CURRENT_PROGRAM);
        int oldVertexArray = GL11C.glGetInteger(GL30C.GL_VERTEX_ARRAY_BINDING);
        int oldElementArray = GL11C.glGetInteger(GL15C.GL_ELEMENT_ARRAY_BUFFER_BINDING);
        int oldDrawIndirect = GL11C.glGetInteger(GL40C.GL_DRAW_INDIRECT_BUFFER_BINDING);
        int oldParameter = GL11C.glGetInteger(GL_PARAMETER_BUFFER_BINDING_ARB);
        int oldGeometryBuffer = GL30C.glGetIntegeri(GL43C.GL_SHADER_STORAGE_BUFFER_BINDING, ForgeFormalShaderInputBindingLayout.QUAD_BUFFER_BINDING_INDEX);
        int oldModelBuffer = GL30C.glGetIntegeri(GL43C.GL_SHADER_STORAGE_BUFFER_BINDING, ForgeFormalShaderInputBindingLayout.MODEL_DATA_BINDING_INDEX);
        int oldColourBuffer = GL30C.glGetIntegeri(GL43C.GL_SHADER_STORAGE_BUFFER_BINDING, ForgeFormalShaderInputBindingLayout.MODEL_COLOUR_BINDING_INDEX);
        int oldPositionScratch = GL30C.glGetIntegeri(GL43C.GL_SHADER_STORAGE_BUFFER_BINDING, ForgeFormalShaderInputBindingLayout.POSITION_SCRATCH_BINDING_INDEX);
        int oldActiveTexture = GL11C.glGetInteger(GL13C.GL_ACTIVE_TEXTURE);
        GL13C.glActiveTexture(GL13C.GL_TEXTURE0 + ForgeFormalShaderInputBindingLayout.BLOCK_MODEL_ATLAS_TEXTURE_UNIT);
        int oldTexture = GL11C.glGetInteger(GL11C.GL_TEXTURE_BINDING_2D);
        int oldSampler = GL30C.glGetIntegeri(GL33C.GL_SAMPLER_BINDING, ForgeFormalShaderInputBindingLayout.BLOCK_MODEL_ATLAS_TEXTURE_UNIT);
        int oldDrawFramebuffer = GL11C.glGetInteger(GL30C.GL_DRAW_FRAMEBUFFER_BINDING);
        int oldReadFramebuffer = GL11C.glGetInteger(GL30C.GL_READ_FRAMEBUFFER_BINDING);
        int oldDrawBuffer = GL11C.glGetInteger(GL11C.GL_DRAW_BUFFER);
        int oldReadBuffer = GL11C.glGetInteger(GL11C.GL_READ_BUFFER);
        int[] oldViewport = new int[4];
        float[] oldClearColor = new float[4];
        GL11C.glGetIntegerv(GL11C.GL_VIEWPORT, oldViewport);
        GL11C.glGetFloatv(GL11C.GL_COLOR_CLEAR_VALUE, oldClearColor);
        boolean depthEnabled = GL11C.glIsEnabled(GL11C.GL_DEPTH_TEST);
        boolean blendEnabled = GL11C.glIsEnabled(GL11C.GL_BLEND);
        boolean cullEnabled = GL11C.glIsEnabled(GL11C.GL_CULL_FACE);
        try {
            clearGlErrors();
            this.vertexArrayId = GL30C.glGenVertexArrays();
            GL30C.glBindVertexArray(this.vertexArrayId);
            GL15C.glBindBuffer(GL15C.GL_ELEMENT_ARRAY_BUFFER, this.indexBufferId);
            GL30C.glBindFramebuffer(GL30C.GL_FRAMEBUFFER, this.offscreenFramebufferId);
            GL20C.glDrawBuffers(GL30C.GL_COLOR_ATTACHMENT0);
            GL11C.glReadBuffer(GL30C.GL_COLOR_ATTACHMENT0);
            GL11C.glViewport(0, 0, PREVIEW_WIDTH, PREVIEW_HEIGHT);
            GL11C.glDisable(GL11C.GL_DEPTH_TEST);
            GL11C.glDisable(GL11C.GL_BLEND);
            GL11C.glDisable(GL11C.GL_CULL_FACE);
            GL11C.glClearColor(0.0F, 0.0F, 0.0F, 0.0F);
            GL11C.glClear(GL11C.GL_COLOR_BUFFER_BIT);

            GL20C.glUseProgram(this.terrainShaderProgramId);
            int recordCountLocation = GL20C.glGetUniformLocation(this.terrainShaderProgramId, "recordCount");
            if (recordCountLocation >= 0) {
                GL30C.glUniform1ui(recordCountLocation, Math.max(1, k8.formalGeometrySnapshotRecordCount()));
            }
            int baseVertexLocation = GL20C.glGetUniformLocation(this.terrainShaderProgramId, "baseVertexBias");
            if (baseVertexLocation >= 0) {
                GL30C.glUniform1ui(baseVertexLocation, Math.max(0, this.k6FirstCommandBaseVertex));
            }
            GL30C.glBindBufferBase(GL43C.GL_SHADER_STORAGE_BUFFER, ForgeFormalShaderInputBindingLayout.QUAD_BUFFER_BINDING_INDEX, k8.formalGeometryValidationBufferId());
            GL30C.glBindBufferBase(GL43C.GL_SHADER_STORAGE_BUFFER, ForgeFormalShaderInputBindingLayout.MODEL_DATA_BINDING_INDEX, store.modelDataBufferId());
            GL30C.glBindBufferBase(GL43C.GL_SHADER_STORAGE_BUFFER, ForgeFormalShaderInputBindingLayout.MODEL_COLOUR_BINDING_INDEX, store.modelColourBufferId());
            GL30C.glBindBufferBase(GL43C.GL_SHADER_STORAGE_BUFFER, ForgeFormalShaderInputBindingLayout.POSITION_SCRATCH_BINDING_INDEX, this.positionScratchBufferId);
            GL11C.glBindTexture(GL11C.GL_TEXTURE_2D, store.atlasTextureId());
            GL33C.glBindSampler(ForgeFormalShaderInputBindingLayout.BLOCK_MODEL_ATLAS_TEXTURE_UNIT, store.samplerId());
            GL15C.glBindBuffer(GL40C.GL_DRAW_INDIRECT_BUFFER, this.commandBufferId);
            GL15C.glBindBuffer(GL_PARAMETER_BUFFER_ARB, this.drawCountBufferId);

            this.geometryBufferBindingOk = GL30C.glGetIntegeri(GL43C.GL_SHADER_STORAGE_BUFFER_BINDING, ForgeFormalShaderInputBindingLayout.QUAD_BUFFER_BINDING_INDEX) == k8.formalGeometryValidationBufferId();
            this.modelDataBindingOk = GL30C.glGetIntegeri(GL43C.GL_SHADER_STORAGE_BUFFER_BINDING, ForgeFormalShaderInputBindingLayout.MODEL_DATA_BINDING_INDEX) == store.modelDataBufferId();
            this.modelColourBindingOk = GL30C.glGetIntegeri(GL43C.GL_SHADER_STORAGE_BUFFER_BINDING, ForgeFormalShaderInputBindingLayout.MODEL_COLOUR_BINDING_INDEX) == store.modelColourBufferId();
            this.positionScratchBindingOk = GL30C.glGetIntegeri(GL43C.GL_SHADER_STORAGE_BUFFER_BINDING, ForgeFormalShaderInputBindingLayout.POSITION_SCRATCH_BINDING_INDEX) == this.positionScratchBufferId;
            this.atlasTextureBindingOk = GL11C.glGetInteger(GL11C.GL_TEXTURE_BINDING_2D) == store.atlasTextureId();
            this.samplerBindingOk = GL30C.glGetIntegeri(GL33C.GL_SAMPLER_BINDING, ForgeFormalShaderInputBindingLayout.BLOCK_MODEL_ATLAS_TEXTURE_UNIT) == store.samplerId();
            this.commandBufferBindingOk = GL11C.glGetInteger(GL40C.GL_DRAW_INDIRECT_BUFFER_BINDING) == this.commandBufferId;
            this.drawCountBufferBindingOk = GL11C.glGetInteger(GL_PARAMETER_BUFFER_BINDING_ARB) == this.drawCountBufferId;
            if (!this.geometryBufferBindingOk
                    || !this.modelDataBindingOk
                    || !this.modelColourBindingOk
                    || !this.positionScratchBindingOk
                    || !this.atlasTextureBindingOk
                    || !this.samplerBindingOk
                    || !this.commandBufferBindingOk
                    || !this.drawCountBufferBindingOk) {
                throw new IllegalStateException("k9-formal-resource-binding-failed");
            }

            GL43C.glMemoryBarrier(GL43C.GL_SHADER_STORAGE_BARRIER_BIT | GL43C.GL_TEXTURE_FETCH_BARRIER_BIT | GL43C.GL_COMMAND_BARRIER_BIT);
            glMultiDrawElementsIndirectCountARB(GL11C.GL_TRIANGLES, GL11C.GL_UNSIGNED_SHORT, 0L, OPAQUE_DRAW_COUNT_OFFSET_BYTES, 1, 0);
            GL11C.glFinish();
            this.offscreenTerrainShaderDrawExecuted = true;
            int error = GL11C.glGetError();
            this.lastGlError = error == GL11C.GL_NO_ERROR ? "none" : glErrorName(error);
            if (error != GL11C.GL_NO_ERROR) {
                throw new IllegalStateException("k9-terrain-shader-draw-gl-error-" + this.lastGlError);
            }
        } finally {
            GL20C.glUseProgram(oldProgram);
            GL30C.glBindVertexArray(oldVertexArray);
            GL15C.glBindBuffer(GL15C.GL_ELEMENT_ARRAY_BUFFER, oldElementArray);
            GL15C.glBindBuffer(GL40C.GL_DRAW_INDIRECT_BUFFER, oldDrawIndirect);
            GL15C.glBindBuffer(GL_PARAMETER_BUFFER_ARB, oldParameter);
            GL30C.glBindBufferBase(GL43C.GL_SHADER_STORAGE_BUFFER, ForgeFormalShaderInputBindingLayout.QUAD_BUFFER_BINDING_INDEX, oldGeometryBuffer);
            GL30C.glBindBufferBase(GL43C.GL_SHADER_STORAGE_BUFFER, ForgeFormalShaderInputBindingLayout.MODEL_DATA_BINDING_INDEX, oldModelBuffer);
            GL30C.glBindBufferBase(GL43C.GL_SHADER_STORAGE_BUFFER, ForgeFormalShaderInputBindingLayout.MODEL_COLOUR_BINDING_INDEX, oldColourBuffer);
            GL30C.glBindBufferBase(GL43C.GL_SHADER_STORAGE_BUFFER, ForgeFormalShaderInputBindingLayout.POSITION_SCRATCH_BINDING_INDEX, oldPositionScratch);
            GL11C.glBindTexture(GL11C.GL_TEXTURE_2D, oldTexture);
            GL33C.glBindSampler(ForgeFormalShaderInputBindingLayout.BLOCK_MODEL_ATLAS_TEXTURE_UNIT, oldSampler);
            GL13C.glActiveTexture(oldActiveTexture);
            GL30C.glBindFramebuffer(GL30C.GL_DRAW_FRAMEBUFFER, oldDrawFramebuffer);
            GL30C.glBindFramebuffer(GL30C.GL_READ_FRAMEBUFFER, oldReadFramebuffer);
            GL20C.glDrawBuffers(oldDrawBuffer);
            GL11C.glReadBuffer(oldReadBuffer);
            GL11C.glViewport(oldViewport[0], oldViewport[1], oldViewport[2], oldViewport[3]);
            GL11C.glClearColor(oldClearColor[0], oldClearColor[1], oldClearColor[2], oldClearColor[3]);
            setEnabled(GL11C.GL_DEPTH_TEST, depthEnabled);
            setEnabled(GL11C.GL_BLEND, blendEnabled);
            setEnabled(GL11C.GL_CULL_FACE, cullEnabled);
        }
    }

    private void readbackOffscreenFramebuffer() {
        ByteBuffer buffer = MemoryUtil.memAlloc(PREVIEW_WIDTH * PREVIEW_HEIGHT * BYTES_PER_PIXEL);
        PixelStoreState pixelStore = PixelStoreState.capturePack();
        int packBufferBinding = GL11C.glGetInteger(GL21C.GL_PIXEL_PACK_BUFFER_BINDING);
        int oldReadFramebuffer = GL11C.glGetInteger(GL30C.GL_READ_FRAMEBUFFER_BINDING);
        int oldReadBuffer = GL11C.glGetInteger(GL11C.GL_READ_BUFFER);
        try {
            GL15C.glBindBuffer(GL21C.GL_PIXEL_PACK_BUFFER, 0);
            GL30C.glBindFramebuffer(GL30C.GL_READ_FRAMEBUFFER, this.offscreenFramebufferId);
            GL11C.glReadBuffer(GL30C.GL_COLOR_ATTACHMENT0);
            GL11C.glReadPixels(0, 0, PREVIEW_WIDTH, PREVIEW_HEIGHT, GL11C.GL_RGBA, GL11C.GL_UNSIGNED_BYTE, buffer);
            CRC32 crc = new CRC32();
            int nonZero = 0;
            for (int i = 0; i < PREVIEW_WIDTH * PREVIEW_HEIGHT; i++) {
                int base = i * BYTES_PER_PIXEL;
                int r = buffer.get(base) & 0xFF;
                int g = buffer.get(base + 1) & 0xFF;
                int b = buffer.get(base + 2) & 0xFF;
                int a = buffer.get(base + 3) & 0xFF;
                if ((r | g | b | a) != 0) {
                    nonZero++;
                }
                crc.update(r);
                crc.update(g);
                crc.update(b);
                crc.update(a);
            }
            this.offscreenNonZeroPixelCount = nonZero;
            this.offscreenChecksum = "0x" + Long.toHexString(crc.getValue());
            int error = GL11C.glGetError();
            this.lastGlError = error == GL11C.GL_NO_ERROR ? "none" : glErrorName(error);
            this.offscreenReadbackOk = error == GL11C.GL_NO_ERROR && nonZero > 0;
            this.basicFragmentOutputOk = this.offscreenReadbackOk;
            if (!this.offscreenReadbackOk) {
                throw new IllegalStateException("k9-offscreen-readback-failed:" + this.lastGlError + ":nonZero=" + nonZero);
            }
        } finally {
            GL15C.glBindBuffer(GL21C.GL_PIXEL_PACK_BUFFER, packBufferBinding);
            GL30C.glBindFramebuffer(GL30C.GL_READ_FRAMEBUFFER, oldReadFramebuffer);
            GL11C.glReadBuffer(oldReadBuffer);
            pixelStore.restorePack();
            MemoryUtil.memFree(buffer);
        }
    }

    private void markStale(String reason) {
        this.integrationReady = false;
        this.resetBuildFlags();
        this.stale = true;
        this.requiresRebuild = true;
        this.lifecycleState = "STALE";
        this.lastLifecycleEvent = safeReason(reason);
        this.lastFailureReason = this.lastLifecycleEvent;
        this.lastAudit = ForgeFormalTerrainShaderIntegrationAuditResult.failure(this.lastFailureReason, 0.0D);
        this.scheduleCleanup(this.lastLifecycleEvent);
    }

    private void scheduleCleanup(String reason) {
        if (RenderSystem.isOnRenderThread()) {
            this.closeOwnedResourcesOnRenderThread();
        } else {
            RenderSystem.recordRenderCall(this::closeOwnedResourcesOnRenderThread);
        }
        this.lastLifecycleEvent = safeReason(reason);
    }

    private void closeOwnedResourcesOnRenderThread() {
        if (this.terrainShaderProgramId != 0) {
            GL20C.glDeleteProgram(this.terrainShaderProgramId);
        }
        if (this.offscreenFramebufferId != 0) {
            GL30C.glDeleteFramebuffers(this.offscreenFramebufferId);
        }
        if (this.offscreenTextureId != 0) {
            GL11C.glDeleteTextures(this.offscreenTextureId);
        }
        if (this.vertexArrayId != 0) {
            GL30C.glDeleteVertexArrays(this.vertexArrayId);
        }
        if (this.indexBufferId != 0) {
            GL15C.glDeleteBuffers(this.indexBufferId);
        }
        if (this.commandBufferId != 0) {
            GL15C.glDeleteBuffers(this.commandBufferId);
        }
        if (this.drawCountBufferId != 0) {
            GL15C.glDeleteBuffers(this.drawCountBufferId);
        }
        if (this.positionScratchBufferId != 0) {
            GL15C.glDeleteBuffers(this.positionScratchBufferId);
        }
        this.terrainShaderProgramId = 0;
        this.offscreenFramebufferId = 0;
        this.offscreenTextureId = 0;
        this.vertexArrayId = 0;
        this.indexBufferId = 0;
        this.commandBufferId = 0;
        this.drawCountBufferId = 0;
        this.positionScratchBufferId = 0;
    }

    private void resetBuildFlags() {
        this.integrationReady = false;
        this.originalShaderFilesInspected = false;
        this.originalQuadsVertInspected = false;
        this.originalQuadsFragInspected = false;
        this.originalQuadUtilInspected = false;
        this.originalBlockModelInspected = false;
        this.terrainShaderProgramCompileAttempted = false;
        this.terrainShaderProgramCompileOk = false;
        this.terrainShaderProgramLinkOk = false;
        this.k8FormalGeometryUsed = false;
        this.k6RealSectionCommandUsed = false;
        this.syntheticDrawFixtureUsed = false;
        this.modelDataBindingOk = false;
        this.modelColourBindingOk = false;
        this.atlasTextureBindingOk = false;
        this.samplerBindingOk = false;
        this.geometryBufferBindingOk = false;
        this.commandBufferBindingOk = false;
        this.drawCountBufferBindingOk = false;
        this.positionScratchBindingOk = false;
        this.formalModelIdDecodeOk = false;
        this.faceDataLookupOk = false;
        this.atlasSampleOk = false;
        this.modelDataReadOk = false;
        this.modelColourReadOk = false;
        this.basicFragmentOutputOk = false;
        this.offscreenFramebufferCreated = false;
        this.offscreenFramebufferComplete = false;
        this.offscreenTerrainShaderDrawExecuted = false;
        this.offscreenReadbackOk = false;
        this.offscreenNonZeroPixelCount = 0;
        this.offscreenChecksum = "none";
        this.validationFormalModelId = 0;
        this.validationFace = 0;
        this.validationFormalModelIds = "none";
        this.geometryBufferId = 0;
        this.k6FirstCommandCount = 0;
        this.k6FirstCommandInstanceCount = 0;
        this.k6FirstCommandFirstIndex = 0;
        this.k6FirstCommandBaseVertex = 0;
        this.k6FirstCommandBaseInstance = 0;
        this.acceptedDrawCommandCount = 0;
        this.drawCommandMatchesK6 = false;
        this.lastGlError = "none";
    }

    private void fail(String reason) {
        this.integrationReady = false;
        this.lifecycleState = "FAILED";
        this.lastFailureReason = safeReason(reason);
    }

    private static short[] createIndexSequenceForBaseVertex(int count, int baseVertex) {
        short[] indices = new short[count];
        for (int i = 0; i < count; i++) {
            int desiredCorner = i % 6;
            int index = Math.floorMod(desiredCorner - baseVertex, 6);
            indices[i] = (short) index;
        }
        return indices;
    }

    private static void uploadInts(int bufferId, int[] values, int usage) {
        long ptr = MemoryUtil.nmemAlloc((long) values.length * Integer.BYTES);
        try {
            for (int i = 0; i < values.length; i++) {
                MemoryUtil.memPutInt(ptr + ((long) i * Integer.BYTES), values[i]);
            }
            GL45C.nglNamedBufferData(bufferId, (long) values.length * Integer.BYTES, ptr, usage);
        } finally {
            MemoryUtil.nmemFree(ptr);
        }
    }

    private static void uploadShorts(int bufferId, short[] values, int usage) {
        long ptr = MemoryUtil.nmemAlloc((long) values.length * Short.BYTES);
        try {
            for (int i = 0; i < values.length; i++) {
                MemoryUtil.memPutShort(ptr + ((long) i * Short.BYTES), values[i]);
            }
            GL45C.nglNamedBufferData(bufferId, (long) values.length * Short.BYTES, ptr, usage);
        } finally {
            MemoryUtil.nmemFree(ptr);
        }
    }

    private static int parseFirstFormalModelId(String ids) {
        if (ids == null || ids.isBlank() || "none".equals(ids)) {
            return 0;
        }
        String first = ids.split(",", 2)[0].trim();
        try {
            return Integer.parseInt(first);
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private static int parseSampleFace(String sampleFormalRecord) {
        if (sampleFormalRecord == null || sampleFormalRecord.isBlank() || "none".equals(sampleFormalRecord)) {
            return 0;
        }
        String hex = sampleFormalRecord.startsWith("0x") || sampleFormalRecord.startsWith("0X")
                ? sampleFormalRecord.substring(2)
                : sampleFormalRecord;
        try {
            long record = Long.parseUnsignedLong(hex, 16);
            int face = ForgeVoxyQuadEncoder.extractFace(record);
            return face >= 0 && face < ForgeModelAtlasLayout.FACE_COUNT ? face : 0;
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private static boolean fileContains(String relativePath, String marker) {
        for (Path path : List.of(Path.of(relativePath), Path.of("..").resolve(relativePath))) {
            if (!Files.exists(path)) {
                continue;
            }
            try {
                return Files.readString(path, StandardCharsets.UTF_8).contains(marker);
            } catch (Exception ignored) {
                return false;
            }
        }
        return false;
    }

    private static int countBlockers(String severity) {
        int count = 0;
        for (ForgeFormalRendererBlocker blocker : BLOCKERS) {
            if (blocker.severity().equals(severity)) {
                count++;
            }
        }
        return count;
    }

    private static String compactBlockers() {
        return BLOCKERS.stream()
                .map(ForgeFormalRendererBlocker::compact)
                .collect(Collectors.joining("|"));
    }

    private static void clearGlErrors() {
        while (GL11C.glGetError() != GL11C.GL_NO_ERROR) {
            // Drain stale GL errors before the K9 offscreen terrain shader validation draw.
        }
    }

    private static void setEnabled(int cap, boolean enabled) {
        if (enabled) {
            GL11C.glEnable(cap);
        } else {
            GL11C.glDisable(cap);
        }
    }

    private static String glErrorName(int error) {
        return switch (error) {
            case GL11C.GL_NO_ERROR -> "none";
            case GL11C.GL_INVALID_ENUM -> "GL_INVALID_ENUM";
            case GL11C.GL_INVALID_VALUE -> "GL_INVALID_VALUE";
            case GL11C.GL_INVALID_OPERATION -> "GL_INVALID_OPERATION";
            case GL11C.GL_OUT_OF_MEMORY -> "GL_OUT_OF_MEMORY";
            default -> "0x" + Integer.toHexString(error);
        };
    }

    private static String sanitize(String log) {
        if (log == null || log.isBlank()) {
            return "none";
        }
        return log.replace('\n', ' ').replace('\r', ' ').replace(' ', '_');
    }

    private static String safeReason(String reason) {
        return reason == null || reason.isBlank() ? "unspecified" : reason.replace(' ', '-');
    }

    private static double elapsedMs(long startNanos) {
        return startNanos == 0L ? 0.0D : (System.nanoTime() - startNanos) / 1_000_000.0D;
    }

    private record PixelStoreState(int alignment, int rowLength, int imageHeight, int skipRows, int skipPixels, int skipImages) {
        static PixelStoreState capturePack() {
            return new PixelStoreState(
                    GL11C.glGetInteger(GL11C.GL_PACK_ALIGNMENT),
                    GL11C.glGetInteger(GL11C.GL_PACK_ROW_LENGTH),
                    GL11C.glGetInteger(GL12C.GL_PACK_IMAGE_HEIGHT),
                    GL11C.glGetInteger(GL11C.GL_PACK_SKIP_ROWS),
                    GL11C.glGetInteger(GL11C.GL_PACK_SKIP_PIXELS),
                    GL11C.glGetInteger(GL12C.GL_PACK_SKIP_IMAGES)
            );
        }

        void restorePack() {
            GL11C.glPixelStorei(GL11C.GL_PACK_ALIGNMENT, this.alignment);
            GL11C.glPixelStorei(GL11C.GL_PACK_ROW_LENGTH, this.rowLength);
            GL11C.glPixelStorei(GL12C.GL_PACK_IMAGE_HEIGHT, this.imageHeight);
            GL11C.glPixelStorei(GL11C.GL_PACK_SKIP_ROWS, this.skipRows);
            GL11C.glPixelStorei(GL11C.GL_PACK_SKIP_PIXELS, this.skipPixels);
            GL11C.glPixelStorei(GL12C.GL_PACK_SKIP_IMAGES, this.skipImages);
        }
    }
}
