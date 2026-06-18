package me.cortex.voxy.forge;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.common.MinecraftForge;
import org.joml.Matrix4f;
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
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import java.util.zip.CRC32;

import static org.lwjgl.opengl.ARBIndirectParameters.GL_PARAMETER_BUFFER_ARB;
import static org.lwjgl.opengl.ARBIndirectParameters.GL_PARAMETER_BUFFER_BINDING_ARB;
import static org.lwjgl.opengl.ARBIndirectParameters.glMultiDrawElementsIndirectCountARB;

final class ForgeFormalVisibleLodPreview {
    static final String STAGE = "K10_FORMAL_VISIBLE_LOD_PREVIEW_DEBUG_TOGGLE";
    private static final boolean DEFAULT_ENABLED = false;
    private static final boolean DEBUG_OPT_IN_ONLY = true;
    private static final int QA_VISIBLE_FRAMES = 1;
    private static final int READBACK_SIZE = 64;
    private static final int BYTES_PER_PIXEL = 4;
    private static final int DRAW_COMMAND_FIELD_COUNT = 5;
    private static final int OPAQUE_DRAW_COUNT_OFFSET_BYTES = 3 * Integer.BYTES;
    private static final String DRAW_INPUT_SOURCE = "K8FormalGeometryAndK9Shader";
    private static final String RENDER_HOOK_NAME = "RenderLevelStageEvent.AFTER_TRANSLUCENT_BLOCKS";
    private static final String RENDER_HOOK_SCOPE = "K10_visible_preview_only";
    private static final String SHADER_CONTRACT_SUBSET = "k9-terrain-shader-adapter-worldspace-visible-preview";
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

            uniform mat4 modelViewMatrix;
            uniform mat4 projectionMatrix;
            uniform vec3 previewCenter;
            uniform vec3 previewRight;
            uniform vec3 previewUp;
            uniform float previewScale;
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
                vTint = vec4(max(vec3(r, g, b), vec3(0.25)), max(a, 1.0));
                vModelId = modelId;
                vFace = face;
                vFaceData = faceData;

                float slot = float(recordIndex % 4u);
                float row = float((recordIndex / 4u) % 3u);
                float jitter = float((positionScratch[0].x ^ positionScratch[0].y) & 3u) * 0.04;
                vec2 local = (corner - vec2(0.5)) * previewScale
                    + vec2((slot - 1.5) * previewScale * 1.16 + jitter, row * previewScale * 1.16);
                vec3 worldPos = previewCenter + previewRight * local.x + previewUp * local.y;
                gl_Position = projectionMatrix * modelViewMatrix * vec4(worldPos, 1.0);
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
                float marker = float((vModelId ^ (vFace << 3u) ^ vFaceData) & 15u) / 180.0;
                vec3 rgb = max(texel.rgb * vTint.rgb + vec3(marker, marker * 0.45, 0.04), vec3(0.08, 0.04, 0.02));
                fragColor = vec4(rgb, 1.0);
            }
            """;

    private static final List<ForgeFormalRendererBlocker> BLOCKERS = List.of(
            new ForgeFormalRendererBlocker("P0", "P0_PRODUCTION_CMDGEN_NOT_OPERATIONAL", "Production cmdgen not operational", "K10 uses K6/K7 validation command evidence and does not promote production cmdgen.comp to the live renderer.", "Promote production command generation only after traversal and live ownership are ready.", true),
            new ForgeFormalRendererBlocker("P0", "P0_FORMAL_VISIBILITY_TRAVERSAL_IMPLEMENTATION_MISSING", "Formal visibility traversal implementation missing", "K10 still depends on K4/K6 candidate snapshots rather than original hierarchical traversal.", "Implement formal visibility traversal before production rendering.", true),
            new ForgeFormalRendererBlocker("P0", "P0_PRODUCTION_TERRAIN_SHADER_FULL_SEMANTICS_INCOMPLETE", "Production terrain shader full semantics incomplete", "K10 reuses the K9 shader adapter subset; lightmap, full biome LUT, material alpha, translucency, and shaderpack semantics remain incomplete.", "Complete terrain shader semantics before live renderer readiness.", true),
            new ForgeFormalRendererBlocker("P0", "P0_FORMAL_MDIC_LIVE_RENDERER_NOT_INTEGRATED", "Formal MDIC live renderer not integrated", "K10 draws only an opt-in visible preview and does not integrate the production formal MDIC renderer.", "Add production formal MDIC integration in a later stage.", true),
            new ForgeFormalRendererBlocker("P0", "P0_FORMAL_RENDERER_DEFAULT_DISABLED", "Formal renderer default disabled", "K10 visible preview is disabled by default and requires an explicit command or preset.", "Keep default disabled until production lifecycle and user-facing controls are ready.", true),
            new ForgeFormalRendererBlocker("P1", "P1_HIERARCHICAL_OCCLUSION_MISSING", "Hierarchical occlusion missing", "K10 does not port the original hierarchical occlusion queue.", "Port formal hierarchical occlusion traversal later.", false),
            new ForgeFormalRendererBlocker("P1", "P1_RENDER_DISTANCE_TRACKER_INCOMPLETE", "Render distance tracker incomplete", "K10 does not own a formal RenderDistanceTracker equivalent.", "Add formal render-distance tracking before live traversal.", false),
            new ForgeFormalRendererBlocker("P1", "P1_LIGHTMAP_MISSING", "Lightmap missing", "K10 does not implement formal lightmap semantics.", "Add formal lightmap binding and shader semantics.", false),
            new ForgeFormalRendererBlocker("P1", "P1_BIOME_TINT_MISSING", "Biome tint missing", "K10 reads modelColour but does not implement the full biome tint LUT path.", "Complete formal biome tint handling.", false),
            new ForgeFormalRendererBlocker("P1", "P1_MATERIAL_ALPHA_SEMANTICS_MISSING", "Material alpha semantics missing", "K10 does not implement complete material, alpha, and cutout behavior.", "Implement formal material/alpha/cutout behavior.", false),
            new ForgeFormalRendererBlocker("P1", "P1_TRANSLUCENCY_INCOMPLETE", "Translucency incomplete", "K10 is opaque preview-only and does not implement translucent sorting.", "Add formal translucent handling after opaque preview is stable.", false),
            new ForgeFormalRendererBlocker("P1", "P1_RESOURCE_REBUILD_AUTOMATION_INCOMPLETE", "Resource rebuild automation incomplete", "K10 stales preview resources but does not automatically rebuild production renderer resources.", "Add rebuild orchestration when production resources exist.", false)
    );

    private final ForgeVoxyInstance instance;
    private long buildRuns;
    private long enableRuns;
    private long disableRuns;
    private long auditRuns;
    private long clearRuns;
    private long auditFailures;
    private boolean renderHookRegistered;
    private boolean ownerReady;
    private boolean visiblePreviewEnabled;
    private boolean visiblePreviewDefaultDisabledVerified = true;
    private boolean visiblePreviewWasEnabledDuringQa;
    private boolean visiblePreviewDisabledAfterQa;
    private boolean qaAutoDisablePending;
    private int qaFramesRemaining;
    private boolean deferredQaPending;
    private int deferredQaStep;
    private boolean firstDrawLogged;
    private boolean k8FormalGeometryUsed;
    private boolean k9TerrainShaderIntegrationUsed;
    private boolean k6RealSectionCommandUsed;
    private boolean syntheticDrawFixtureUsed;
    private boolean worldSpacePreview;
    private String previewSectionWorldPosition = "none";
    private boolean previewCameraRelativeTransformOk;
    private boolean projectionMatrixUsed;
    private boolean modelViewMatrixUsed;
    private boolean visiblePreviewShaderProgramCompileAttempted;
    private boolean visiblePreviewShaderProgramCompileOk;
    private boolean visiblePreviewShaderProgramLinkOk;
    private int visiblePreviewShaderProgramId;
    private boolean visiblePreviewDrawExecuted;
    private int visiblePreviewFrameCount;
    private boolean minecraftMainFramebufferDrawn;
    private boolean visiblePreviewDrawCallOk;
    private boolean formalModelIdDecodeOk;
    private boolean faceDataLookupOk;
    private boolean atlasSampleOk;
    private boolean modelDataReadOk;
    private boolean modelColourReadOk;
    private boolean visiblePreviewReadbackOk;
    private int visiblePreviewNonZeroPixelCount;
    private String visiblePreviewChecksum = "none";
    private int validationFormalModelId;
    private int validationFace;
    private String validationFormalModelIds = "none";
    private int geometryBufferId;
    private int commandBufferId;
    private int drawCountBufferId;
    private int positionScratchBufferId;
    private int indexBufferId;
    private int vertexArrayId;
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
    private ForgeFormalVisibleLodPreviewAuditResult lastAudit =
            ForgeFormalVisibleLodPreviewAuditResult.failure("not-audited", 0.0D);

    ForgeFormalVisibleLodPreview(ForgeVoxyInstance instance) {
        this.instance = instance;
    }

    void register() {
        MinecraftForge.EVENT_BUS.addListener(this::onRenderLevelStage);
        this.renderHookRegistered = true;
    }

    ForgeFormalVisibleLodPreviewStats build() {
        this.buildRuns++;
        this.lifecycleState = "BUILDING";
        this.lastLifecycleEvent = "build";
        this.lastFailureReason = "none";
        this.visiblePreviewDefaultDisabledVerified = !DEFAULT_ENABLED;
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
        if (!this.instance.ensureActiveWorldSkeletonForCurrentWorldIfAllowed()) {
            this.fail("world-engine-skeleton-not-ready");
            this.audit();
            return this.createStatusSnapshot();
        }

        ForgeFormalTerrainShaderIntegrationStats k9 = this.instance.getFormalTerrainShaderIntegration().build();
        ForgeFormalModelIdSectionGeometryStats k8 = this.instance.getFormalModelIdSectionGeometryPath().createStatusSnapshot();
        ForgeFormalCmdgenRealSectionDryRunStats k6 = this.instance.getFormalCmdgenRealSectionDryRun().createStatusSnapshot();
        ForgeFormalModelStoreStats store = this.instance.getFormalModelStore().createStatusSnapshot();
        if (!this.capturePrerequisites(k6, k8, k9, store)) {
            this.audit();
            this.instance.getFormalRendererManager().checkReadiness("k10-visible-lod-preview-prerequisite-failed");
            return this.createStatusSnapshot();
        }

        try {
            this.closeOwnedResourcesOnRenderThread();
            this.captureFormalModelReadbacks(k8);
            this.visiblePreviewShaderProgramId = this.compileVisiblePreviewShaderProgram();
            this.createValidationDrawBuffers(k6);
            this.ownerReady = this.renderHookRegistered
                    && this.visiblePreviewDefaultDisabledVerified
                    && this.visiblePreviewShaderProgramCompileOk
                    && this.visiblePreviewShaderProgramLinkOk
                    && this.k8FormalGeometryUsed
                    && this.k9TerrainShaderIntegrationUsed
                    && this.k6RealSectionCommandUsed
                    && !this.syntheticDrawFixtureUsed
                    && this.formalModelIdDecodeOk
                    && this.faceDataLookupOk
                    && this.atlasSampleOk
                    && this.modelDataReadOk
                    && this.modelColourReadOk
                    && this.drawCommandMatchesK6
                    && this.acceptedDrawCommandCount >= 1;
            if (this.ownerReady) {
                this.stale = false;
                this.requiresRebuild = false;
                this.lifecycleState = "BUILT_DISABLED";
                this.lastLifecycleEvent = "build-complete";
                this.lastFailureReason = "none";
            } else {
                this.fail("visible-lod-preview-owner-incomplete");
            }
        } catch (RuntimeException e) {
            this.fail(e.getClass().getSimpleName() + ":" + e.getMessage());
        }

        this.audit();
        this.instance.getFormalRendererManager().checkReadiness("k10-formal-visible-lod-preview-build");
        return this.createStatusSnapshot();
    }

    ForgeFormalVisibleLodPreviewStats enable(String reason) {
        this.enableRuns++;
        ForgeFormalVisibleLodPreviewStats status = this.ownerReady && !this.stale
                ? this.createStatusSnapshot()
                : this.build();
        if (!status.visibleLodPreviewOwnerReady()) {
            return this.createStatusSnapshot();
        }
        this.visiblePreviewEnabled = true;
        this.lifecycleState = "VISIBLE_PREVIEW_ENABLED";
        this.lastLifecycleEvent = safeReason(reason);
        this.lastFailureReason = "none";
        return this.createStatusSnapshot();
    }

    ForgeFormalVisibleLodPreviewStats runQa() {
        this.visiblePreviewDefaultDisabledVerified = !this.visiblePreviewEnabled && !DEFAULT_ENABLED;
        if (!RenderSystem.isOnRenderThread()) {
            this.fail("not-render-thread");
            this.audit();
            return this.createStatusSnapshot();
        }
        if (!this.instance.ensureActiveWorldSkeletonForCurrentWorldIfAllowed()) {
            this.fail("world-engine-skeleton-not-ready");
            this.audit();
            return this.createStatusSnapshot();
        }
        this.deferredQaPending = true;
        this.deferredQaStep = 0;
        this.visiblePreviewWasEnabledDuringQa = false;
        this.visiblePreviewDisabledAfterQa = false;
        this.qaAutoDisablePending = false;
        this.qaFramesRemaining = 0;
        this.firstDrawLogged = false;
        this.lifecycleState = "VISIBLE_PREVIEW_QA_SCHEDULED";
        this.lastLifecycleEvent = "qa-scheduled";
        this.lastFailureReason = "none";
        VoxyForge.LOGGER.info("Voxy K10 visible LoD preview QA scheduled: stepCount=5 previewDefaultEnabled=false debugOptInOnly=true");
        this.instance.getFormalRendererManager().checkReadiness("qa-k10-formal-visible-lod-preview-scheduled");
        return this.createStatusSnapshot();
    }

    ForgeFormalVisibleLodPreviewStats disable(String reason) {
        this.disableRuns++;
        this.visiblePreviewEnabled = false;
        this.qaAutoDisablePending = false;
        this.qaFramesRemaining = 0;
        this.lifecycleState = "DISABLED";
        this.lastLifecycleEvent = safeReason(reason);
        this.lastFailureReason = "none";
        this.audit();
        this.instance.getFormalRendererManager().checkReadiness("k10-visible-lod-preview-disable");
        return this.createStatusSnapshot();
    }

    ForgeFormalVisibleLodPreviewAuditResult audit() {
        this.auditRuns++;
        long start = System.nanoTime();
        try {
            this.lastAudit = this.auditInternal(start);
        } catch (RuntimeException e) {
            this.lastAudit = ForgeFormalVisibleLodPreviewAuditResult.failure(e.getClass().getSimpleName() + ":" + e.getMessage(), elapsedMs(start));
        }
        if (!this.lastAudit.success()) {
            this.auditFailures++;
            this.lastFailureReason = this.lastAudit.error();
        }
        return this.lastAudit;
    }

    ForgeFormalVisibleLodPreviewAuditResult createAuditStatusSnapshot() {
        return this.lastAudit;
    }

    ForgeFormalVisibleLodPreviewStats createStatusSnapshot() {
        ForgeFormalTerrainRendererStats k1 = this.instance.getFormalTerrainRendererOwner().createStatusSnapshot();
        ForgeFormalMdicViewportStats k2 = this.instance.getFormalMdicViewportOwner().createStatusSnapshot();
        ForgeFormalCommandGenerationStats k3 = this.instance.getFormalCommandGenerationOwner().createStatusSnapshot();
        ForgeFormalVisibilityStats k4 = this.instance.getFormalVisibilityOwner().createStatusSnapshot();
        ForgeFormalCmdgenRealSectionDryRunStats k6 = this.instance.getFormalCmdgenRealSectionDryRun().createStatusSnapshot();
        ForgeFormalIsolatedMdicDrawSmokeTestStats k7 = this.instance.getFormalIsolatedMdicDrawSmokeTest().createStatusSnapshot();
        ForgeFormalModelIdSectionGeometryStats k8 = this.instance.getFormalModelIdSectionGeometryPath().createStatusSnapshot();
        ForgeFormalTerrainShaderIntegrationStats k9 = this.instance.getFormalTerrainShaderIntegration().createStatusSnapshot();
        boolean ready = this.ownerReady && !this.stale;
        return new ForgeFormalVisibleLodPreviewStats(
                STAGE,
                this.buildRuns,
                this.enableRuns,
                this.disableRuns,
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
                k9.formalTerrainShaderIntegrationReady(),
                ready,
                this.visiblePreviewEnabled,
                DEFAULT_ENABLED,
                this.visiblePreviewDefaultDisabledVerified,
                DEBUG_OPT_IN_ONLY,
                this.visiblePreviewWasEnabledDuringQa,
                this.visiblePreviewDisabledAfterQa,
                this.visiblePreviewDrawExecuted,
                this.visiblePreviewFrameCount,
                true,
                this.minecraftMainFramebufferDrawn,
                false,
                this.renderHookRegistered,
                RENDER_HOOK_NAME,
                RENDER_HOOK_SCOPE,
                DRAW_INPUT_SOURCE,
                this.k8FormalGeometryUsed,
                this.k9TerrainShaderIntegrationUsed,
                this.k6RealSectionCommandUsed,
                this.syntheticDrawFixtureUsed,
                this.worldSpacePreview,
                this.previewSectionWorldPosition,
                this.previewCameraRelativeTransformOk,
                this.projectionMatrixUsed,
                this.modelViewMatrixUsed,
                true,
                this.visiblePreviewShaderProgramCompileAttempted,
                this.visiblePreviewShaderProgramCompileOk,
                this.visiblePreviewShaderProgramLinkOk,
                this.visiblePreviewShaderProgramId,
                this.visiblePreviewDrawCallOk,
                this.lastGlError,
                this.formalModelIdDecodeOk,
                this.faceDataLookupOk,
                this.atlasSampleOk,
                this.modelDataReadOk,
                this.modelColourReadOk,
                this.visiblePreviewReadbackOk,
                this.visiblePreviewNonZeroPixelCount,
                this.visiblePreviewChecksum,
                this.validationFormalModelId,
                this.validationFace,
                this.validationFormalModelIds,
                this.geometryBufferId,
                this.commandBufferId,
                this.drawCountBufferId,
                this.positionScratchBufferId,
                this.indexBufferId,
                this.vertexArrayId,
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
                SEMANTIC_COMPLETENESS,
                false,
                false,
                false,
                false,
                false,
                this.lifecycleState,
                this.lastLifecycleEvent,
                this.stale,
                this.requiresRebuild,
                BLOCKERS.size(),
                countBlockers("P0"),
                countBlockers("P1"),
                countBlockers("P2"),
                compactBlockers(),
                this.lastFailureReason,
                this.lastAudit.success(),
                this.lastAudit.error(),
                this.lastAudit.durationMs()
        );
    }

    String dump() {
        ForgeFormalVisibleLodPreviewStats status = this.createStatusSnapshot();
        return "K10 formal visible LoD preview: stage=" + status.stage()
                + " ownerReady=" + status.visibleLodPreviewOwnerReady()
                + " enabled=" + status.visiblePreviewEnabled()
                + " defaultEnabled=false optInOnly=true"
                + " drawExecuted=" + status.visiblePreviewDrawExecuted()
                + " mainFramebufferDrawn=" + status.minecraftMainFramebufferDrawn()
                + " disabledAfterQa=" + status.visiblePreviewDisabledAfterQa()
                + " input=" + status.drawInputSource()
                + " shaderProgram=" + status.visiblePreviewShaderProgramId()
                + " geometryBuffer=" + status.geometryBufferId()
                + " checksum=" + status.visiblePreviewChecksum()
                + " formalRendererReady=false actualRendererDrawEnabled=false productionLiveRenderer=false";
    }

    void clear() {
        this.clearRuns++;
        this.visiblePreviewEnabled = false;
        this.ownerReady = false;
        this.stale = false;
        this.requiresRebuild = false;
        this.lifecycleState = "CLEARED";
        this.lastLifecycleEvent = "clear";
        this.lastFailureReason = "none";
        this.resetBuildFlags();
        this.lastAudit = ForgeFormalVisibleLodPreviewAuditResult.failure("cleared", 0.0D);
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

    private void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }
        if (!RenderSystem.isOnRenderThread()) {
            this.lastFailureReason = "render-hook-not-render-thread";
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null || event.getCamera() == null) {
            this.lastFailureReason = "render-hook-world-missing";
            return;
        }
        if (this.deferredQaPending) {
            this.runDeferredQaStep();
        }
        if (!this.visiblePreviewEnabled) {
            return;
        }
        if (!this.ownerReady || this.stale) {
            this.lastFailureReason = "render-hook-owner-not-ready";
            this.visiblePreviewEnabled = false;
            return;
        }
        ForgeFormalModelStoreStats store = this.instance.getFormalModelStore().createStatusSnapshot();
        ForgeFormalModelIdSectionGeometryStats k8 = this.instance.getFormalModelIdSectionGeometryPath().createStatusSnapshot();
        try {
            this.executeVisiblePreviewDraw(event, minecraft, store, k8);
            this.visiblePreviewFrameCount++;
            if (!this.firstDrawLogged) {
                this.firstDrawLogged = true;
                ForgeFormalRendererStats rendererStatus = this.instance.getFormalRendererManager().checkReadiness("k10-visible-preview-first-draw");
                VoxyForge.LOGGER.info("Voxy K10 visible LoD preview first draw: {} {}", this.dump(), ForgeVoxyCommands.formatFormalRendererStatusForLog(rendererStatus));
            }
            if (this.qaAutoDisablePending) {
                this.qaFramesRemaining--;
                if (this.qaFramesRemaining <= 0) {
                    this.visiblePreviewEnabled = false;
                    this.visiblePreviewDisabledAfterQa = true;
                    this.qaAutoDisablePending = false;
                    this.lifecycleState = "DISABLED_AFTER_QA";
                    this.lastLifecycleEvent = "qa-auto-disable-after-visible-preview";
                    ForgeFormalVisibleLodPreviewAuditResult audit = this.audit();
                    ForgeFormalRendererStats rendererStatus = this.instance.getFormalRendererManager().checkReadiness("k10-visible-preview-qa-auto-disabled");
                    VoxyForge.LOGGER.info("Voxy K10 visible LoD preview auto-disabled: {} k10AuditOk={} k10AuditError={} {}",
                            this.dump(),
                            audit.success(),
                            audit.error(),
                            ForgeVoxyCommands.formatFormalRendererStatusForLog(rendererStatus));
                }
            }
        } catch (RuntimeException e) {
            this.fail(e.getClass().getSimpleName() + ":" + e.getMessage());
            this.visiblePreviewEnabled = false;
            VoxyForge.LOGGER.error("K10 formal visible LoD preview draw failed.", e);
        }
    }

    private void runDeferredQaStep() {
        try {
            switch (this.deferredQaStep) {
                case 0 -> {
                    ForgeFormalCmdgenRealSectionDryRunStats k6 = this.instance.getFormalCmdgenRealSectionDryRun().build();
                    VoxyForge.LOGGER.info("Voxy K10 visible LoD preview QA step K6: realSectionDryRunReady={} auditOk={} source={} acceptedSections={}",
                            k6.realSectionDryRunReady(),
                            k6.cmdgenDryRunAuditOk(),
                            k6.validationInputSource(),
                            k6.acceptedSectionCount());
                    this.deferredQaStep++;
                }
                case 1 -> {
                    ForgeFormalIsolatedMdicDrawSmokeTestStats k7 = this.instance.getFormalIsolatedMdicDrawSmokeTest().build();
                    VoxyForge.LOGGER.info("Voxy K10 visible LoD preview QA step K7: isolatedDrawReady={} offscreenDraw={} readbackOk={} realSectionCommandUsed={}",
                            k7.isolatedMdicDrawSmokeTestReady(),
                            k7.offscreenValidationDrawExecuted(),
                            k7.offscreenReadbackOk(),
                            k7.realSectionCommandUsed());
                    this.deferredQaStep++;
                }
                case 2 -> {
                    ForgeFormalModelIdSectionGeometryStats k8 = this.instance.getFormalModelIdSectionGeometryPath().build();
                    VoxyForge.LOGGER.info("Voxy K10 visible LoD preview QA step K8: geometryPathReady={} snapshotRecords={} usesFormalModelIds={} originalHeapMutated={}",
                            k8.formalModelIdGeometryPathReady(),
                            k8.formalGeometrySnapshotRecordCount(),
                            k8.usesFormalModelIds(),
                            k8.originalGeometryHeapMutated());
                    this.deferredQaStep++;
                }
                case 3 -> {
                    ForgeFormalTerrainShaderIntegrationStats k9 = this.instance.getFormalTerrainShaderIntegration().build();
                    VoxyForge.LOGGER.info("Voxy K10 visible LoD preview QA step K9: shaderIntegrationReady={} compileOk={} linkOk={} offscreenReadbackOk={} lastFailureReason={}",
                            k9.formalTerrainShaderIntegrationReady(),
                            k9.terrainShaderProgramCompileOk(),
                            k9.terrainShaderProgramLinkOk(),
                            k9.offscreenReadbackOk(),
                            k9.lastFailureReason());
                    this.deferredQaStep++;
                }
                case 4 -> {
                    ForgeFormalVisibleLodPreviewStats status = this.enable("qa-k10-formal-visible-lod-preview");
                    if (status.visibleLodPreviewOwnerReady()) {
                        this.visiblePreviewWasEnabledDuringQa = true;
                        this.visiblePreviewDisabledAfterQa = false;
                        this.qaAutoDisablePending = true;
                        this.qaFramesRemaining = QA_VISIBLE_FRAMES;
                        this.firstDrawLogged = false;
                        this.lifecycleState = "VISIBLE_PREVIEW_QA_ENABLED";
                        this.lastLifecycleEvent = "qa-enable";
                        this.deferredQaPending = false;
                        this.instance.getFormalRendererManager().checkReadiness("qa-k10-formal-visible-lod-preview-enabled");
                        VoxyForge.LOGGER.info("Voxy K10 visible LoD preview QA enabled: {}", this.dump());
                    } else {
                        this.deferredQaPending = false;
                        this.audit();
                        this.instance.getFormalRendererManager().checkReadiness("qa-k10-formal-visible-lod-preview-enable-failed");
                        VoxyForge.LOGGER.info("Voxy K10 visible LoD preview QA enable failed: {}", this.dump());
                    }
                }
                default -> this.deferredQaPending = false;
            }
        } catch (RuntimeException e) {
            this.deferredQaPending = false;
            this.fail(e.getClass().getSimpleName() + ":" + e.getMessage());
            this.visiblePreviewEnabled = false;
            VoxyForge.LOGGER.error("K10 formal visible LoD preview deferred QA failed.", e);
        }
    }

    private boolean capturePrerequisites(
            ForgeFormalCmdgenRealSectionDryRunStats k6,
            ForgeFormalModelIdSectionGeometryStats k8,
            ForgeFormalTerrainShaderIntegrationStats k9,
            ForgeFormalModelStoreStats store
    ) {
        boolean originalRendererHooksInspected = fileContains("src/main/java/me/cortex/voxy/client/core/VoxyRenderSystem.java", "renderOpaque")
                && fileContains("src/main/java/me/cortex/voxy/client/core/rendering/section/backend/mdic/MDICSectionRenderer.java", "glMultiDrawElementsIndirectCountARB")
                && fileContains("src/main/resources/assets/voxy/shaders/lod/gl46/quads3.vert", "MODEL_BUFFER_BINDING")
                && fileContains("src/main/resources/assets/voxy/shaders/lod/gl46/quads.frag", "blockModelAtlas")
                && fileContains("src/main/java/me/cortex/voxy/forge/ForgeTexturedMdicDebugRenderer.java", "RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS");
        if (!originalRendererHooksInspected) {
            this.fail("original-visible-renderer-contract-not-inspected");
            return false;
        }

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
        this.syntheticDrawFixtureUsed = k6.syntheticValidationFixtureUsed() || k9.syntheticDrawFixtureUsed();
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
        this.k9TerrainShaderIntegrationUsed = k9.formalTerrainShaderIntegrationReady()
                && k9.terrainShaderProgramCompileOk()
                && k9.terrainShaderProgramLinkOk()
                && k9.k8FormalGeometryUsed()
                && k9.k6RealSectionCommandUsed()
                && !k9.syntheticDrawFixtureUsed()
                && k9.modelDataBindingOk()
                && k9.modelColourBindingOk()
                && k9.atlasTextureBindingOk()
                && k9.formalModelIdDecodeOk()
                && k9.faceDataLookupOk()
                && k9.atlasSampleOk()
                && !k9.minecraftMainFramebufferDrawn()
                && !k9.liveRendererDrawExecuted();
        this.geometryBufferId = k8.formalGeometryValidationBufferId();
        this.validationFormalModelIds = k8.formalGeometryModelIds();
        this.validationFormalModelId = parseFirstFormalModelId(this.validationFormalModelIds);
        this.validationFace = parseSampleFace(k8.sampleFormalRecord());
        this.previewSectionWorldPosition = k8.sampleSectionPosition();
        this.formalModelIdDecodeOk = k8.formalModelIdDecodeOk()
                && ForgeModelAtlasLayout.isValidModelId(this.validationFormalModelId)
                && this.validationFormalModelId > 0
                && this.validationFace >= 0
                && this.validationFace < ForgeModelAtlasLayout.FACE_COUNT;

        if (!this.k9TerrainShaderIntegrationUsed) {
            this.fail("k9-terrain-shader-integration-not-ready:" + k9.lastFailureReason());
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

    private ForgeFormalVisibleLodPreviewAuditResult auditInternal(long startNanos) {
        boolean prerequisitesChecked = this.ownerReady
                && this.k8FormalGeometryUsed
                && this.k9TerrainShaderIntegrationUsed
                && this.k6RealSectionCommandUsed;
        boolean drawInputUsesK8K9 = DRAW_INPUT_SOURCE.equals(DRAW_INPUT_SOURCE)
                && this.k8FormalGeometryUsed
                && this.k9TerrainShaderIntegrationUsed
                && this.k6RealSectionCommandUsed
                && !this.syntheticDrawFixtureUsed;
        boolean qaDisableRequirementMet = !this.visiblePreviewWasEnabledDuringQa || this.visiblePreviewDisabledAfterQa;
        boolean success = prerequisitesChecked
                && this.visiblePreviewDefaultDisabledVerified
                && DEBUG_OPT_IN_ONLY
                && drawInputUsesK8K9
                && this.visiblePreviewDrawExecuted
                && this.minecraftMainFramebufferDrawn
                && this.visiblePreviewDrawCallOk
                && this.formalModelIdDecodeOk
                && this.faceDataLookupOk
                && this.atlasSampleOk
                && this.modelDataReadOk
                && this.modelColourReadOk
                && qaDisableRequirementMet
                && "none".equals(this.lastGlError);
        return new ForgeFormalVisibleLodPreviewAuditResult(
                success,
                success ? "none" : "k10-formal-visible-lod-preview-audit-failed",
                elapsedMs(startNanos),
                prerequisitesChecked,
                this.visiblePreviewDefaultDisabledVerified,
                DEBUG_OPT_IN_ONLY,
                drawInputUsesK8K9,
                false,
                false,
                false,
                false,
                this.visiblePreviewDrawExecuted,
                this.minecraftMainFramebufferDrawn,
                this.visiblePreviewDisabledAfterQa,
                false,
                false,
                false,
                false
        );
    }

    private int compileVisiblePreviewShaderProgram() {
        this.visiblePreviewShaderProgramCompileAttempted = true;
        int vertex = 0;
        int fragment = 0;
        int program = 0;
        try {
            vertex = compileShader(GL20C.GL_VERTEX_SHADER, VERTEX_SOURCE, "vertex");
            fragment = compileShader(GL20C.GL_FRAGMENT_SHADER, FRAGMENT_SOURCE, "fragment");
            this.visiblePreviewShaderProgramCompileOk = true;
            program = GL20C.glCreateProgram();
            GL20C.glAttachShader(program, vertex);
            GL20C.glAttachShader(program, fragment);
            GL20C.glLinkProgram(program);
            if (GL20C.glGetProgrami(program, GL20C.GL_LINK_STATUS) == GL11C.GL_FALSE) {
                throw new IllegalStateException("k10-visible-preview-shader-link-failed:" + sanitize(GL20C.glGetProgramInfoLog(program)));
            }
            this.visiblePreviewShaderProgramLinkOk = true;
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
            throw new IllegalStateException("k10-visible-preview-shader-" + name + "-compile-failed:" + log);
        }
        return shader;
    }

    private void createValidationDrawBuffers(ForgeFormalCmdgenRealSectionDryRunStats k6) {
        this.commandBufferId = GL45C.glCreateBuffers();
        this.drawCountBufferId = GL45C.glCreateBuffers();
        this.indexBufferId = GL45C.glCreateBuffers();
        this.positionScratchBufferId = GL45C.glCreateBuffers();
        this.vertexArrayId = GL30C.glGenVertexArrays();
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

    private void executeVisiblePreviewDraw(
            RenderLevelStageEvent event,
            Minecraft minecraft,
            ForgeFormalModelStoreStats store,
            ForgeFormalModelIdSectionGeometryStats k8
    ) {
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
        boolean depthEnabled = GL11C.glIsEnabled(GL11C.GL_DEPTH_TEST);
        boolean blendEnabled = GL11C.glIsEnabled(GL11C.GL_BLEND);
        boolean cullEnabled = GL11C.glIsEnabled(GL11C.GL_CULL_FACE);
        boolean depthMask = GL11C.glGetBoolean(GL11C.GL_DEPTH_WRITEMASK);
        try {
            clearGlErrors();
            GL30C.glBindVertexArray(this.vertexArrayId);
            GL15C.glBindBuffer(GL15C.GL_ELEMENT_ARRAY_BUFFER, this.indexBufferId);
            GL11C.glDisable(GL11C.GL_DEPTH_TEST);
            GL11C.glDisable(GL11C.GL_CULL_FACE);
            GL11C.glDisable(GL11C.GL_BLEND);
            GL11C.glDepthMask(false);

            PoseStack poseStack = event.getPoseStack();
            Vec3 cameraPos = event.getCamera().getPosition();
            Vec3 look = minecraft.player == null ? new Vec3(0.0D, 0.0D, 1.0D) : minecraft.player.getLookAngle();
            if (look.lengthSqr() < 1.0E-6D) {
                look = new Vec3(0.0D, 0.0D, 1.0D);
            }
            look = look.normalize();
            Vec3 worldUp = new Vec3(0.0D, 1.0D, 0.0D);
            Vec3 right = look.cross(worldUp);
            if (right.lengthSqr() < 1.0E-6D) {
                right = new Vec3(1.0D, 0.0D, 0.0D);
            }
            right = right.normalize();
            Vec3 up = right.cross(look).normalize();
            Vec3 center = cameraPos.add(look.scale(8.0D)).add(0.0D, 0.25D, 0.0D);

            poseStack.pushPose();
            try {
                poseStack.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);
                Matrix4f modelView = poseStack.last().pose();
                Matrix4f projection = event.getProjectionMatrix();
                GL20C.glUseProgram(this.visiblePreviewShaderProgramId);
                uniformMatrix4f(this.visiblePreviewShaderProgramId, "modelViewMatrix", modelView);
                uniformMatrix4f(this.visiblePreviewShaderProgramId, "projectionMatrix", projection);
                uniform3f(this.visiblePreviewShaderProgramId, "previewCenter", center);
                uniform3f(this.visiblePreviewShaderProgramId, "previewRight", right);
                uniform3f(this.visiblePreviewShaderProgramId, "previewUp", up);
                uniform1f(this.visiblePreviewShaderProgramId, "previewScale", 1.45F);
                uniform1ui(this.visiblePreviewShaderProgramId, "recordCount", Math.max(1, k8.formalGeometrySnapshotRecordCount()));
                uniform1ui(this.visiblePreviewShaderProgramId, "baseVertexBias", Math.max(0, this.k6FirstCommandBaseVertex));

                GL30C.glBindBufferBase(GL43C.GL_SHADER_STORAGE_BUFFER, ForgeFormalShaderInputBindingLayout.QUAD_BUFFER_BINDING_INDEX, k8.formalGeometryValidationBufferId());
                GL30C.glBindBufferBase(GL43C.GL_SHADER_STORAGE_BUFFER, ForgeFormalShaderInputBindingLayout.MODEL_DATA_BINDING_INDEX, store.modelDataBufferId());
                GL30C.glBindBufferBase(GL43C.GL_SHADER_STORAGE_BUFFER, ForgeFormalShaderInputBindingLayout.MODEL_COLOUR_BINDING_INDEX, store.modelColourBufferId());
                GL30C.glBindBufferBase(GL43C.GL_SHADER_STORAGE_BUFFER, ForgeFormalShaderInputBindingLayout.POSITION_SCRATCH_BINDING_INDEX, this.positionScratchBufferId);
                GL11C.glBindTexture(GL11C.GL_TEXTURE_2D, store.atlasTextureId());
                GL33C.glBindSampler(ForgeFormalShaderInputBindingLayout.BLOCK_MODEL_ATLAS_TEXTURE_UNIT, store.samplerId());
                GL15C.glBindBuffer(GL40C.GL_DRAW_INDIRECT_BUFFER, this.commandBufferId);
                GL15C.glBindBuffer(GL_PARAMETER_BUFFER_ARB, this.drawCountBufferId);

                boolean bindingsOk = GL30C.glGetIntegeri(GL43C.GL_SHADER_STORAGE_BUFFER_BINDING, ForgeFormalShaderInputBindingLayout.QUAD_BUFFER_BINDING_INDEX) == k8.formalGeometryValidationBufferId()
                        && GL30C.glGetIntegeri(GL43C.GL_SHADER_STORAGE_BUFFER_BINDING, ForgeFormalShaderInputBindingLayout.MODEL_DATA_BINDING_INDEX) == store.modelDataBufferId()
                        && GL30C.glGetIntegeri(GL43C.GL_SHADER_STORAGE_BUFFER_BINDING, ForgeFormalShaderInputBindingLayout.MODEL_COLOUR_BINDING_INDEX) == store.modelColourBufferId()
                        && GL30C.glGetIntegeri(GL43C.GL_SHADER_STORAGE_BUFFER_BINDING, ForgeFormalShaderInputBindingLayout.POSITION_SCRATCH_BINDING_INDEX) == this.positionScratchBufferId
                        && GL11C.glGetInteger(GL11C.GL_TEXTURE_BINDING_2D) == store.atlasTextureId()
                        && GL30C.glGetIntegeri(GL33C.GL_SAMPLER_BINDING, ForgeFormalShaderInputBindingLayout.BLOCK_MODEL_ATLAS_TEXTURE_UNIT) == store.samplerId()
                        && GL11C.glGetInteger(GL40C.GL_DRAW_INDIRECT_BUFFER_BINDING) == this.commandBufferId
                        && GL11C.glGetInteger(GL_PARAMETER_BUFFER_BINDING_ARB) == this.drawCountBufferId;
                if (!bindingsOk) {
                    throw new IllegalStateException("k10-formal-resource-binding-failed");
                }

                GL43C.glMemoryBarrier(GL43C.GL_SHADER_STORAGE_BARRIER_BIT | GL43C.GL_TEXTURE_FETCH_BARRIER_BIT | GL43C.GL_COMMAND_BARRIER_BIT);
                glMultiDrawElementsIndirectCountARB(GL11C.GL_TRIANGLES, GL11C.GL_UNSIGNED_SHORT, 0L, OPAQUE_DRAW_COUNT_OFFSET_BYTES, 1, 0);
                this.worldSpacePreview = true;
                this.previewCameraRelativeTransformOk = true;
                this.projectionMatrixUsed = true;
                this.modelViewMatrixUsed = true;
                this.visiblePreviewDrawExecuted = true;
                this.minecraftMainFramebufferDrawn = true;
                this.visiblePreviewDrawCallOk = true;
                int error = GL11C.glGetError();
                this.lastGlError = error == GL11C.GL_NO_ERROR ? "none" : glErrorName(error);
                if (error != GL11C.GL_NO_ERROR) {
                    throw new IllegalStateException("k10-visible-preview-draw-gl-error-" + this.lastGlError);
                }
                if (!this.visiblePreviewReadbackOk) {
                    this.readbackMainFramebufferSample();
                }
            } finally {
                poseStack.popPose();
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
            GL11C.glDepthMask(depthMask);
            setEnabled(GL11C.GL_DEPTH_TEST, depthEnabled);
            setEnabled(GL11C.GL_BLEND, blendEnabled);
            setEnabled(GL11C.GL_CULL_FACE, cullEnabled);
        }
    }

    private void readbackMainFramebufferSample() {
        int[] viewport = new int[4];
        GL11C.glGetIntegerv(GL11C.GL_VIEWPORT, viewport);
        int width = Math.max(1, Math.min(READBACK_SIZE, viewport[2]));
        int height = Math.max(1, Math.min(READBACK_SIZE, viewport[3]));
        int x = Math.max(0, viewport[0] + (viewport[2] - width) / 2);
        int y = Math.max(0, viewport[1] + (viewport[3] - height) / 2);
        ByteBuffer buffer = MemoryUtil.memAlloc(width * height * BYTES_PER_PIXEL);
        PixelStoreState pixelStore = PixelStoreState.capturePack();
        int packBufferBinding = GL11C.glGetInteger(GL21C.GL_PIXEL_PACK_BUFFER_BINDING);
        int oldReadFramebuffer = GL11C.glGetInteger(GL30C.GL_READ_FRAMEBUFFER_BINDING);
        int oldDrawFramebuffer = GL11C.glGetInteger(GL30C.GL_DRAW_FRAMEBUFFER_BINDING);
        int oldReadBuffer = GL11C.glGetInteger(GL11C.GL_READ_BUFFER);
        try {
            GL15C.glBindBuffer(GL21C.GL_PIXEL_PACK_BUFFER, 0);
            GL30C.glBindFramebuffer(GL30C.GL_READ_FRAMEBUFFER, oldDrawFramebuffer);
            GL11C.glReadBuffer(oldDrawFramebuffer == 0 ? GL11C.GL_BACK : GL30C.GL_COLOR_ATTACHMENT0);
            GL11C.glReadPixels(x, y, width, height, GL11C.GL_RGBA, GL11C.GL_UNSIGNED_BYTE, buffer);
            CRC32 crc = new CRC32();
            int nonZero = 0;
            for (int i = 0; i < width * height; i++) {
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
            int error = GL11C.glGetError();
            this.lastGlError = error == GL11C.GL_NO_ERROR ? "none" : glErrorName(error);
            this.visiblePreviewNonZeroPixelCount = nonZero;
            this.visiblePreviewChecksum = "0x" + Long.toHexString(crc.getValue());
            this.visiblePreviewReadbackOk = error == GL11C.GL_NO_ERROR && nonZero > 0;
        } finally {
            GL15C.glBindBuffer(GL21C.GL_PIXEL_PACK_BUFFER, packBufferBinding);
            GL30C.glBindFramebuffer(GL30C.GL_READ_FRAMEBUFFER, oldReadFramebuffer);
            GL11C.glReadBuffer(oldReadBuffer);
            pixelStore.restorePack();
            MemoryUtil.memFree(buffer);
        }
    }

    private void markStale(String reason) {
        this.visiblePreviewEnabled = false;
        this.ownerReady = false;
        this.resetBuildFlags();
        this.stale = true;
        this.requiresRebuild = true;
        this.lifecycleState = "STALE";
        this.lastLifecycleEvent = safeReason(reason);
        this.lastFailureReason = this.lastLifecycleEvent;
        this.lastAudit = ForgeFormalVisibleLodPreviewAuditResult.failure(this.lastFailureReason, 0.0D);
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
        if (this.visiblePreviewShaderProgramId != 0) {
            GL20C.glDeleteProgram(this.visiblePreviewShaderProgramId);
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
        this.visiblePreviewShaderProgramId = 0;
        this.vertexArrayId = 0;
        this.indexBufferId = 0;
        this.commandBufferId = 0;
        this.drawCountBufferId = 0;
        this.positionScratchBufferId = 0;
    }

    private void resetBuildFlags() {
        this.ownerReady = false;
        this.visiblePreviewDrawExecuted = false;
        this.visiblePreviewFrameCount = 0;
        this.minecraftMainFramebufferDrawn = false;
        this.visiblePreviewWasEnabledDuringQa = false;
        this.visiblePreviewDisabledAfterQa = false;
        this.qaAutoDisablePending = false;
        this.qaFramesRemaining = 0;
        this.firstDrawLogged = false;
        this.k8FormalGeometryUsed = false;
        this.k9TerrainShaderIntegrationUsed = false;
        this.k6RealSectionCommandUsed = false;
        this.syntheticDrawFixtureUsed = false;
        this.worldSpacePreview = false;
        this.previewSectionWorldPosition = "none";
        this.previewCameraRelativeTransformOk = false;
        this.projectionMatrixUsed = false;
        this.modelViewMatrixUsed = false;
        this.visiblePreviewShaderProgramCompileAttempted = false;
        this.visiblePreviewShaderProgramCompileOk = false;
        this.visiblePreviewShaderProgramLinkOk = false;
        this.visiblePreviewDrawCallOk = false;
        this.formalModelIdDecodeOk = false;
        this.faceDataLookupOk = false;
        this.atlasSampleOk = false;
        this.modelDataReadOk = false;
        this.modelColourReadOk = false;
        this.visiblePreviewReadbackOk = false;
        this.visiblePreviewNonZeroPixelCount = 0;
        this.visiblePreviewChecksum = "none";
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
        this.ownerReady = false;
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

    private static void uniformMatrix4f(int program, String name, Matrix4f matrix) {
        int location = GL20C.glGetUniformLocation(program, name);
        if (location < 0) {
            return;
        }
        try (MemoryStack stack = MemoryStack.stackPush()) {
            FloatBuffer buffer = stack.mallocFloat(16);
            matrix.get(buffer);
            GL20C.glUniformMatrix4fv(location, false, buffer);
        }
    }

    private static void uniform3f(int program, String name, Vec3 value) {
        int location = GL20C.glGetUniformLocation(program, name);
        if (location >= 0) {
            GL20C.glUniform3f(location, (float) value.x, (float) value.y, (float) value.z);
        }
    }

    private static void uniform1f(int program, String name, float value) {
        int location = GL20C.glGetUniformLocation(program, name);
        if (location >= 0) {
            GL20C.glUniform1f(location, value);
        }
    }

    private static void uniform1ui(int program, String name, int value) {
        int location = GL20C.glGetUniformLocation(program, name);
        if (location >= 0) {
            GL30C.glUniform1ui(location, value);
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
            // Drain stale GL errors before the K10 visible preview validation draw.
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
