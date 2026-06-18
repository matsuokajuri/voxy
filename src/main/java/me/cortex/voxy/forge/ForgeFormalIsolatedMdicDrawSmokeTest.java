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

final class ForgeFormalIsolatedMdicDrawSmokeTest {
    static final String STAGE = "K7_FORMAL_ISOLATED_MDIC_DRAW_SMOKE_TEST_OFFSCREEN";
    private static final int PREVIEW_WIDTH = 64;
    private static final int PREVIEW_HEIGHT = 64;
    private static final int BYTES_PER_PIXEL = 4;
    private static final int DRAW_COMMAND_FIELD_COUNT = 5;
    private static final int DRAW_COMMAND_STRIDE_BYTES = DRAW_COMMAND_FIELD_COUNT * Integer.BYTES;
    private static final int DRAW_COUNT_WORDS = 4;
    private static final int OPAQUE_DRAW_COUNT_OFFSET_BYTES = 3 * Integer.BYTES;
    private static final String DRAW_INPUT_SOURCE = "realSectionCmdgenDryRun";
    private static final String K6_REAL_SECTION_INPUT_SOURCE = "realSectionCandidateSnapshot";
    private static final String CALL_SCOPE = "K7_validation_offscreen_only";
    private static final String SHADER_CONTRACT_SUBSET = "modelData+modelColour+formalAtlas+sampler+K6-indirect-count+gl_VertexID-validation-geometry";

    private static final String VERTEX_SOURCE = """
            #version 430 core

            layout(std430, binding = 3) readonly buffer ModelData {
                uint modelData[];
            };

            layout(std430, binding = 4) readonly buffer ModelColour {
                uint modelColour[];
            };

            uniform uint validationModelId;

            out vec2 vAtlasUv;
            out vec4 vTint;
            flat out uint vModelId;
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

            void main() {
                uint cornerIndex = uint(gl_VertexID) % 6u;
                vec2 corner = CORNERS[cornerIndex];
                uint modelId = validationModelId;
                uint wordBase = modelId * WORDS_PER_MODEL;
                uint faceData = modelData[wordBase];
                uint colour = modelColour[modelId];

                uint minU = faceData & 15u;
                uint maxU = (faceData >> 4u) & 15u;
                uint minV = (faceData >> 8u) & 15u;
                uint maxV = (faceData >> 12u) & 15u;
                maxU = max(maxU, minU);
                maxV = max(maxV, minV);

                float localU = (float(minU) + corner.x * float(maxU - minU + 1u)) / 16.0;
                float localV = (float(minV) + corner.y * float(maxV - minV + 1u)) / 16.0;
                uint baseX = (modelId & 255u) * MODEL_TEXTURE_SIZE * FACES_PER_MODEL_X;
                uint baseY = ((modelId >> 8u) & 255u) * MODEL_TEXTURE_SIZE * FACES_PER_MODEL_Y;
                vec2 atlasPixel = vec2(float(baseX), float(baseY)) + vec2(localU, localV) * 16.0 + vec2(0.5);
                vAtlasUv = atlasPixel / vec2(ATLAS_WIDTH, ATLAS_HEIGHT);

                float r = float(colour & 255u) / 255.0;
                float g = float((colour >> 8u) & 255u) / 255.0;
                float b = float((colour >> 16u) & 255u) / 255.0;
                float a = float((colour >> 24u) & 255u) / 255.0;
                vTint = vec4(max(vec3(r, g, b), vec3(0.25)), max(a, 1.0));
                vModelId = modelId;
                vFaceData = faceData;
                gl_Position = vec4(mix(-0.72, 0.72, corner.x), mix(-0.72, 0.72, corner.y), 0.0, 1.0);
            }
            """;

    private static final String FRAGMENT_SOURCE = """
            #version 430 core

            layout(binding = 0) uniform sampler2D blockModelAtlas;

            in vec2 vAtlasUv;
            in vec4 vTint;
            flat in uint vModelId;
            flat in uint vFaceData;

            out vec4 fragColor;

            void main() {
                vec4 texel = texture(blockModelAtlas, vAtlasUv);
                float marker = float((vModelId ^ vFaceData) & 7u) / 255.0;
                vec3 rgb = max(texel.rgb * vTint.rgb + vec3(marker), vec3(0.03, 0.02, 0.01));
                fragColor = vec4(rgb, 1.0);
            }
            """;

    private static final List<ForgeFormalRendererBlocker> BLOCKERS = List.of(
            new ForgeFormalRendererBlocker("P0", "P0_PRODUCTION_CMDGEN_NOT_OPERATIONAL", "Production cmdgen not operational", "K7 consumes K6 audit dry-run command data and does not promote production cmdgen.comp to the renderer path.", "Promote production cmdgen only after traversal, global formal model ids, and terrain shader integration are ready.", true),
            new ForgeFormalRendererBlocker("P0", "P0_FORMAL_VISIBILITY_TRAVERSAL_IMPLEMENTATION_MISSING", "Formal visibility traversal implementation missing", "K7 still depends on the K4/K6 conservative candidate snapshot instead of original hierarchical traversal.", "Implement formal visibility traversal before live command generation.", true),
            new ForgeFormalRendererBlocker("P0", "P0_GLOBAL_FORMAL_MODEL_ID_GEOMETRY_MISSING", "Global formal model-id geometry missing", "K7 uses isolated validation geometry and does not globally encode live geometry with formal model ids.", "Move formal model ids into the formal BuiltSection/RenderDataFactory path.", true),
            new ForgeFormalRendererBlocker("P0", "P0_FORMAL_TERRAIN_SHADER_INTEGRATION_MISSING", "Formal terrain shader integration missing", "K7 uses a validation shader subset, not the production terrain shader.", "Integrate the production formal terrain shader after command and geometry ownership are ready.", true),
            new ForgeFormalRendererBlocker("P0", "P0_FORMAL_MDIC_LIVE_DRAW_DISABLED", "Formal MDIC live draw disabled", "K7 draw is offscreen validation only and never enables the live renderer.", "Keep live draw disabled until production command, shader, traversal, and lifecycle paths are ready.", true),
            new ForgeFormalRendererBlocker("P1", "P1_HIERARCHICAL_OCCLUSION_MISSING", "Hierarchical occlusion missing", "K7 does not port the original hierarchical occlusion queue.", "Port formal hierarchical occlusion traversal later.", false),
            new ForgeFormalRendererBlocker("P1", "P1_RENDER_DISTANCE_TRACKER_INCOMPLETE", "Render distance tracker incomplete", "K7 does not own the formal render-distance tracker.", "Add formal render distance tracking before live traversal.", false),
            new ForgeFormalRendererBlocker("P1", "P1_LIGHTMAP_MISSING", "Lightmap missing", "K7 validation shader does not implement formal lightmap semantics.", "Add formal lightmap binding and semantics.", false),
            new ForgeFormalRendererBlocker("P1", "P1_BIOME_TINT_MISSING", "Biome tint missing", "K7 samples modelColour but does not implement broad biome LUT semantics.", "Complete formal biome tint handling.", false),
            new ForgeFormalRendererBlocker("P1", "P1_MATERIAL_ALPHA_SEMANTICS_MISSING", "Material alpha semantics missing", "K7 does not implement formal material, alpha, and cutout behavior.", "Implement formal material/alpha/cutout behavior.", false),
            new ForgeFormalRendererBlocker("P1", "P1_RESOURCE_REBUILD_AUTOMATION_INCOMPLETE", "Resource rebuild automation incomplete", "K7 stales validation resources but does not automatically rebuild production renderer resources.", "Add rebuild orchestration when production resources exist.", false)
    );

    private final ForgeVoxyInstance instance;
    private long buildRuns;
    private long auditRuns;
    private long clearRuns;
    private long auditFailures;
    private boolean smokeTestReady;
    private boolean offscreenValidationDrawReady;
    private boolean offscreenFramebufferCreated;
    private boolean offscreenFramebufferComplete;
    private boolean offscreenValidationDrawExecuted;
    private boolean offscreenReadbackOk;
    private int offscreenNonZeroPixelCount;
    private String offscreenChecksum = "none";
    private boolean realSectionCommandUsed;
    private boolean syntheticDrawFixtureUsed;
    private boolean drawCommandMatchesK6;
    private int generatedCommandCount;
    private int acceptedDrawCommandCount;
    private boolean drawCountAccepted;
    private boolean geometryInputAvailable;
    private boolean modelInputAvailable;
    private boolean shaderInputAvailable;
    private boolean formalModelIdsUsed;
    private int validationFormalModelId;
    private String validationFormalModelIds = "none";
    private boolean modelDataBindingOk;
    private boolean modelColourBindingOk;
    private boolean atlasTextureBindingOk;
    private boolean samplerBindingOk;
    private int validationProgramId;
    private int offscreenFramebufferId;
    private int offscreenTextureId;
    private int validationVertexArrayId;
    private int validationIndexBufferId;
    private int validationIndirectBufferId;
    private int validationParameterBufferId;
    private int k6FirstCommandCount;
    private int k6FirstCommandInstanceCount;
    private int k6FirstCommandFirstIndex;
    private int k6FirstCommandBaseVertex;
    private int k6FirstCommandBaseInstance;
    private int firstCommandCount;
    private int firstCommandInstanceCount;
    private int firstCommandFirstIndex;
    private int firstCommandBaseVertex;
    private int firstCommandBaseInstance;
    private boolean stale;
    private boolean requiresRebuild;
    private String lifecycleState = "UNINITIALIZED";
    private String lastLifecycleEvent = "initialized";
    private String lastGlError = "none";
    private String lastFailureReason = "none";
    private ForgeFormalIsolatedMdicDrawSmokeTestAuditResult lastAudit =
            ForgeFormalIsolatedMdicDrawSmokeTestAuditResult.failure("not-audited", 0.0D);

    ForgeFormalIsolatedMdicDrawSmokeTest(ForgeVoxyInstance instance) {
        this.instance = instance;
    }

    ForgeFormalIsolatedMdicDrawSmokeTestStats build() {
        this.buildRuns++;
        this.lifecycleState = "BUILDING";
        this.lastLifecycleEvent = "build";
        this.lastFailureReason = "none";
        this.resetSmokeFlags();

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

        ForgeFormalTerrainPackedRecordBridgeStats bridge = this.instance.getFormalTerrainPackedRecordBridge().createStatusSnapshot();
        if (!bridge.realTerrainPackedRecordBridgeReady() || bridge.stale() || bridge.requiresRebuild()) {
            bridge = this.instance.getFormalTerrainPackedRecordBridge().build();
        }
        ForgeFormalCmdgenRealSectionDryRunStats k6 = this.instance.getFormalCmdgenRealSectionDryRun().build();
        ForgeFormalModelStoreStats store = this.instance.getFormalModelStore().createStatusSnapshot();
        List<ForgeFormalUploadedModelSummary> summaries = this.instance.getMultiBlockFormalBakeUpload().uploadedModelSummaries();
        if (!this.capturePrerequisites(bridge, k6, store, summaries)) {
            this.audit();
            this.instance.getFormalRendererManager().checkReadiness("k7-isolated-mdic-draw-prerequisite-failed");
            return this.createStatusSnapshot();
        }

        try {
            this.closeOwnedResourcesOnRenderThread(false);
            this.validationProgramId = this.compileValidationProgram();
            this.shaderInputAvailable = this.validationProgramId != 0;
            this.createOffscreenFramebuffer();
            this.createValidationDrawBuffers(k6);
            this.executeOffscreenValidationDraw(store);
            this.readbackOffscreenFramebuffer();
            this.smokeTestReady = this.offscreenValidationDrawExecuted
                    && this.offscreenFramebufferComplete
                    && this.offscreenReadbackOk
                    && this.offscreenNonZeroPixelCount > 0
                    && this.drawCommandMatchesK6
                    && this.drawCountAccepted
                    && this.realSectionCommandUsed
                    && !this.syntheticDrawFixtureUsed
                    && this.modelInputAvailable
                    && this.shaderInputAvailable
                    && this.geometryInputAvailable
                    && this.formalModelIdsUsed;
            this.offscreenValidationDrawReady = this.smokeTestReady;
            if (this.smokeTestReady) {
                this.stale = false;
                this.requiresRebuild = false;
                this.lifecycleState = "BUILT";
                this.lastLifecycleEvent = "build-complete";
                this.lastFailureReason = "none";
            } else {
                this.fail("isolated-mdic-draw-smoke-test-incomplete");
            }
        } catch (RuntimeException e) {
            this.fail(e.getClass().getSimpleName() + ": " + e.getMessage());
        }

        this.audit();
        this.instance.getFormalRendererManager().checkReadiness("k7-isolated-mdic-draw-smoke-test-build");
        return this.createStatusSnapshot();
    }

    ForgeFormalIsolatedMdicDrawSmokeTestAuditResult audit() {
        this.auditRuns++;
        long start = System.nanoTime();
        try {
            this.lastAudit = this.auditInternal(start);
        } catch (RuntimeException e) {
            this.lastAudit = ForgeFormalIsolatedMdicDrawSmokeTestAuditResult.failure(e.getClass().getSimpleName() + ": " + e.getMessage(), elapsedMs(start));
        }
        if (!this.lastAudit.success()) {
            this.auditFailures++;
            this.lastFailureReason = this.lastAudit.error();
        }
        return this.lastAudit;
    }

    ForgeFormalIsolatedMdicDrawSmokeTestAuditResult createAuditStatusSnapshot() {
        return this.lastAudit;
    }

    ForgeFormalIsolatedMdicDrawSmokeTestStats createStatusSnapshot() {
        ForgeFormalTerrainRendererStats k1 = this.instance.getFormalTerrainRendererOwner().createStatusSnapshot();
        ForgeFormalMdicViewportStats k2 = this.instance.getFormalMdicViewportOwner().createStatusSnapshot();
        ForgeFormalCommandGenerationStats k3 = this.instance.getFormalCommandGenerationOwner().createStatusSnapshot();
        ForgeFormalVisibilityStats k4 = this.instance.getFormalVisibilityOwner().createStatusSnapshot();
        ForgeFormalCmdgenRealSectionDryRunStats k6 = this.instance.getFormalCmdgenRealSectionDryRun().createStatusSnapshot();
        return new ForgeFormalIsolatedMdicDrawSmokeTestStats(
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
                this.smokeTestReady && !this.stale,
                this.offscreenValidationDrawReady && !this.stale,
                this.offscreenFramebufferCreated,
                this.offscreenFramebufferComplete,
                this.offscreenValidationDrawExecuted,
                this.offscreenReadbackOk,
                this.offscreenNonZeroPixelCount,
                this.offscreenChecksum,
                PREVIEW_WIDTH,
                PREVIEW_HEIGHT,
                DRAW_INPUT_SOURCE,
                this.realSectionCommandUsed,
                this.syntheticDrawFixtureUsed,
                this.drawCommandMatchesK6,
                this.generatedCommandCount,
                this.acceptedDrawCommandCount,
                this.drawCountAccepted,
                this.geometryInputAvailable,
                this.modelInputAvailable,
                this.shaderInputAvailable,
                this.validationIndexBufferId != 0,
                false,
                false,
                this.formalModelIdsUsed,
                false,
                false,
                this.validationFormalModelId,
                this.validationFormalModelIds,
                this.validationProgramId != 0,
                false,
                false,
                SHADER_CONTRACT_SUBSET,
                this.modelDataBindingOk,
                this.modelColourBindingOk,
                this.atlasTextureBindingOk,
                this.samplerBindingOk,
                true,
                this.offscreenValidationDrawExecuted,
                this.offscreenValidationDrawExecuted ? CALL_SCOPE : "none",
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                this.offscreenValidationDrawExecuted ? false : true,
                true,
                true,
                true,
                this.validationProgramId,
                this.offscreenFramebufferId,
                this.offscreenTextureId,
                this.validationVertexArrayId,
                this.validationIndexBufferId,
                this.validationIndirectBufferId,
                this.validationParameterBufferId,
                this.k6FirstCommandCount,
                this.k6FirstCommandInstanceCount,
                this.k6FirstCommandFirstIndex,
                this.k6FirstCommandBaseVertex,
                this.k6FirstCommandBaseInstance,
                this.firstCommandCount,
                this.firstCommandInstanceCount,
                this.firstCommandFirstIndex,
                this.firstCommandBaseVertex,
                this.firstCommandBaseInstance,
                this.stale,
                this.requiresRebuild,
                this.lifecycleState,
                this.lastLifecycleEvent,
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
        ForgeFormalIsolatedMdicDrawSmokeTestStats status = this.createStatusSnapshot();
        return "K7 formal isolated MDIC draw smoke test: stage=" + status.stage()
                + " input=" + status.drawInputSource()
                + " realSectionCommandUsed=" + status.realSectionCommandUsed()
                + " syntheticDrawFixtureUsed=" + status.syntheticDrawFixtureUsed()
                + " command=[" + status.firstCommandCount()
                + "," + status.firstCommandInstanceCount()
                + "," + status.firstCommandFirstIndex()
                + "," + status.firstCommandBaseVertex()
                + "," + status.firstCommandBaseInstance() + "]"
                + " k6Command=[" + status.k6FirstCommandCount()
                + "," + status.k6FirstCommandInstanceCount()
                + "," + status.k6FirstCommandFirstIndex()
                + "," + status.k6FirstCommandBaseVertex()
                + "," + status.k6FirstCommandBaseInstance() + "]"
                + " framebuffer=" + status.offscreenFramebufferId()
                + " texture=" + status.offscreenTextureId()
                + " checksum=" + status.offscreenChecksum()
                + " nonZeroPixels=" + status.offscreenNonZeroPixelCount()
                + " scope=" + status.glMultiDrawElementsIndirectCountCallScope()
                + " liveRenderer=false formalRendererReady=false actualRendererDrawEnabled=false";
    }

    void clear() {
        this.clearRuns++;
        this.smokeTestReady = false;
        this.offscreenValidationDrawReady = false;
        this.stale = false;
        this.requiresRebuild = false;
        this.lifecycleState = "CLEARED";
        this.lastLifecycleEvent = "clear";
        this.lastFailureReason = "none";
        this.resetSmokeFlags();
        this.lastAudit = ForgeFormalIsolatedMdicDrawSmokeTestAuditResult.failure("cleared", 0.0D);
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

    private boolean capturePrerequisites(ForgeFormalTerrainPackedRecordBridgeStats bridge, ForgeFormalCmdgenRealSectionDryRunStats k6, ForgeFormalModelStoreStats store, List<ForgeFormalUploadedModelSummary> summaries) {
        this.generatedCommandCount = k6.generatedCommandCount();
        this.k6FirstCommandCount = k6.firstCommandCount();
        this.k6FirstCommandInstanceCount = k6.firstCommandInstanceCount();
        this.k6FirstCommandFirstIndex = k6.firstCommandFirstIndex();
        this.k6FirstCommandBaseVertex = k6.firstCommandBaseVertex();
        this.k6FirstCommandBaseInstance = k6.firstCommandBaseInstance();
        this.realSectionCommandUsed = k6.realSectionDryRunReady()
                && k6.realSectionMetadataUsed()
                && k6.firstCommandUsesRealSectionMetadata()
                && K6_REAL_SECTION_INPUT_SOURCE.equals(k6.validationInputSource())
                && !k6.syntheticValidationFixtureUsed();
        this.drawCountAccepted = k6.generatedDrawCount() >= 1;
        this.modelInputAvailable = bridge.realTerrainPackedRecordBridgeReady()
                && bridge.formalModelIdsBackedByRealBake()
                && bridge.usesFormalModelIds()
                && !bridge.usesPlaceholderModelIds()
                && !bridge.sampleSetModelIdsUsed()
                && store.formalModelStoreOwnerReady()
                && store.modelDataBufferCreated()
                && store.modelColourBufferCreated()
                && store.atlasTextureCreated()
                && store.samplerCreated()
                && store.samplerConfigured()
                && !summaries.isEmpty();
        if (!summaries.isEmpty()) {
            this.validationFormalModelId = summaries.get(0).formalModelId();
            this.validationFormalModelIds = summaries.stream()
                    .map(summary -> Integer.toString(summary.formalModelId()))
                    .limit(4)
                    .collect(Collectors.joining(","));
            this.formalModelIdsUsed = ForgeModelAtlasLayout.isValidModelId(this.validationFormalModelId)
                    && this.validationFormalModelId != 0
                    && bridge.formalModelIdsBackedByRealBake();
        }
        this.geometryInputAvailable = this.realSectionCommandUsed && k6.firstCommandCount() > 0;
        if (!this.realSectionCommandUsed) {
            this.fail("k6-real-section-command-not-ready:" + k6.lastFailureReason());
            return false;
        }
        if (!this.drawCountAccepted || k6.firstCommandCount() <= 0 || k6.firstCommandInstanceCount() <= 0) {
            this.fail("k6-command-not-drawable");
            return false;
        }
        if (!this.modelInputAvailable || !this.formalModelIdsUsed) {
            this.fail("formal-model-input-not-ready:" + bridge.lastFailureReason());
            return false;
        }
        return true;
    }

    private ForgeFormalIsolatedMdicDrawSmokeTestAuditResult auditInternal(long startNanos) {
        ForgeFormalTerrainRendererStats k1 = this.instance.getFormalTerrainRendererOwner().createStatusSnapshot();
        ForgeFormalMdicViewportStats k2 = this.instance.getFormalMdicViewportOwner().createStatusSnapshot();
        ForgeFormalCommandGenerationStats k3 = this.instance.getFormalCommandGenerationOwner().createStatusSnapshot();
        ForgeFormalVisibilityStats k4 = this.instance.getFormalVisibilityOwner().createStatusSnapshot();
        ForgeFormalCmdgenGpuValidationStats k5 = this.instance.getFormalCmdgenGpuValidator().createStatusSnapshot();
        ForgeFormalCmdgenRealSectionDryRunStats k6 = this.instance.getFormalCmdgenRealSectionDryRun().createStatusSnapshot();
        boolean originalDrawInspected = originalMdicSectionRendererChecked();
        boolean originalBindingsInspected = originalShaderBindingsChecked();
        boolean buffersIsolated = this.validationIndirectBufferId != 0
                && this.validationParameterBufferId != 0
                && this.validationIndexBufferId != 0
                && this.offscreenFramebufferId != 0
                && this.offscreenTextureId != 0;
        boolean success = this.smokeTestReady
                && !this.stale
                && k1.formalTerrainRendererOwnerReady()
                && k2.formalViewportOwnerReady()
                && k3.formalCommandGenerationOwnerReady()
                && k4.formalVisibilityOwnerReady()
                && k5.cmdgenValidationProgramReady()
                && k6.realSectionDryRunReady()
                && originalDrawInspected
                && originalBindingsInspected
                && this.realSectionCommandUsed
                && !this.syntheticDrawFixtureUsed
                && buffersIsolated
                && this.offscreenFramebufferComplete
                && this.offscreenValidationDrawExecuted
                && this.offscreenReadbackOk
                && this.offscreenNonZeroPixelCount > 0
                && this.drawCommandMatchesK6
                && this.drawCountAccepted
                && this.geometryInputAvailable
                && this.modelInputAvailable
                && this.shaderInputAvailable
                && !this.lastGlError.startsWith("GL_");
        return new ForgeFormalIsolatedMdicDrawSmokeTestAuditResult(
                success,
                success ? "none" : "isolated-mdic-draw-smoke-test-audit-failed",
                elapsedMs(startNanos),
                k1.formalTerrainRendererOwnerReady(),
                k2.formalViewportOwnerReady(),
                k3.formalCommandGenerationOwnerReady(),
                k4.formalVisibilityOwnerReady(),
                k5.cmdgenValidationProgramReady(),
                k6.realSectionDryRunReady(),
                originalDrawInspected,
                originalBindingsInspected,
                this.drawCommandMatchesK6,
                this.realSectionCommandUsed,
                this.syntheticDrawFixtureUsed,
                buffersIsolated,
                false,
                false,
                this.offscreenFramebufferComplete,
                this.offscreenValidationDrawExecuted,
                this.offscreenReadbackOk,
                this.offscreenNonZeroPixelCount,
                this.acceptedDrawCommandCount >= 1,
                this.drawCountAccepted,
                this.drawCommandMatchesK6,
                this.geometryInputAvailable,
                this.modelInputAvailable,
                this.shaderInputAvailable,
                this.offscreenValidationDrawExecuted,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false
        );
    }

    private int compileValidationProgram() {
        int vertex = 0;
        int fragment = 0;
        int program = 0;
        try {
            vertex = compileShader(GL20C.GL_VERTEX_SHADER, VERTEX_SOURCE, "vertex");
            fragment = compileShader(GL20C.GL_FRAGMENT_SHADER, FRAGMENT_SOURCE, "fragment");
            program = GL20C.glCreateProgram();
            GL20C.glAttachShader(program, vertex);
            GL20C.glAttachShader(program, fragment);
            GL20C.glLinkProgram(program);
            if (GL20C.glGetProgrami(program, GL20C.GL_LINK_STATUS) == GL11C.GL_FALSE) {
                throw new IllegalStateException("k7-validation-link-failed:" + sanitize(GL20C.glGetProgramInfoLog(program)));
            }
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
            throw new IllegalStateException("k7-validation-" + name + "-compile-failed:" + log);
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
                throw new IllegalStateException("k7-offscreen-framebuffer-incomplete");
            }
        } finally {
            GL30C.glBindFramebuffer(GL30C.GL_DRAW_FRAMEBUFFER, oldDrawFramebuffer);
            GL11C.glBindTexture(GL11C.GL_TEXTURE_2D, oldTexture);
        }
    }

    private void createValidationDrawBuffers(ForgeFormalCmdgenRealSectionDryRunStats k6) {
        this.firstCommandCount = k6.firstCommandCount();
        this.firstCommandInstanceCount = k6.firstCommandInstanceCount();
        this.firstCommandFirstIndex = k6.firstCommandFirstIndex();
        this.firstCommandBaseVertex = k6.firstCommandBaseVertex();
        this.firstCommandBaseInstance = k6.firstCommandBaseInstance();
        this.drawCommandMatchesK6 = this.firstCommandCount == this.k6FirstCommandCount
                && this.firstCommandInstanceCount == this.k6FirstCommandInstanceCount
                && this.firstCommandFirstIndex == this.k6FirstCommandFirstIndex
                && this.firstCommandBaseVertex == this.k6FirstCommandBaseVertex
                && this.firstCommandBaseInstance == this.k6FirstCommandBaseInstance;
        if (!this.drawCommandMatchesK6 || this.firstCommandCount <= 0) {
            throw new IllegalStateException("k6-command-copy-mismatch");
        }

        this.validationIndirectBufferId = GL45C.glCreateBuffers();
        this.validationParameterBufferId = GL45C.glCreateBuffers();
        this.validationIndexBufferId = GL45C.glCreateBuffers();
        uploadInts(this.validationIndirectBufferId, new int[] {
                this.firstCommandCount,
                this.firstCommandInstanceCount,
                this.firstCommandFirstIndex,
                this.firstCommandBaseVertex,
                this.firstCommandBaseInstance
        }, GL15C.GL_STATIC_DRAW);
        uploadInts(this.validationParameterBufferId, new int[] {0, 0, 0, 1}, GL15C.GL_STATIC_DRAW);
        uploadShorts(this.validationIndexBufferId, createIndexSequenceForBaseVertex(this.firstCommandCount, this.firstCommandBaseVertex), GL15C.GL_STATIC_DRAW);
        this.acceptedDrawCommandCount = 1;
        this.drawCountAccepted = true;
        this.geometryInputAvailable = true;
    }

    private void executeOffscreenValidationDraw(ForgeFormalModelStoreStats store) {
        int oldProgram = GL11C.glGetInteger(GL20C.GL_CURRENT_PROGRAM);
        int oldVertexArray = GL11C.glGetInteger(GL30C.GL_VERTEX_ARRAY_BINDING);
        int oldElementArray = GL11C.glGetInteger(GL15C.GL_ELEMENT_ARRAY_BUFFER_BINDING);
        int oldDrawIndirect = GL11C.glGetInteger(GL40C.GL_DRAW_INDIRECT_BUFFER_BINDING);
        int oldParameter = GL11C.glGetInteger(GL_PARAMETER_BUFFER_BINDING_ARB);
        int oldModelBuffer = GL30C.glGetIntegeri(GL43C.GL_SHADER_STORAGE_BUFFER_BINDING, ForgeFormalShaderInputBindingLayout.MODEL_DATA_BINDING_INDEX);
        int oldColourBuffer = GL30C.glGetIntegeri(GL43C.GL_SHADER_STORAGE_BUFFER_BINDING, ForgeFormalShaderInputBindingLayout.MODEL_COLOUR_BINDING_INDEX);
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
            this.validationVertexArrayId = GL30C.glGenVertexArrays();
            GL30C.glBindVertexArray(this.validationVertexArrayId);
            GL15C.glBindBuffer(GL15C.GL_ELEMENT_ARRAY_BUFFER, this.validationIndexBufferId);
            GL30C.glBindFramebuffer(GL30C.GL_FRAMEBUFFER, this.offscreenFramebufferId);
            GL20C.glDrawBuffers(GL30C.GL_COLOR_ATTACHMENT0);
            GL11C.glReadBuffer(GL30C.GL_COLOR_ATTACHMENT0);
            GL11C.glViewport(0, 0, PREVIEW_WIDTH, PREVIEW_HEIGHT);
            GL11C.glDisable(GL11C.GL_DEPTH_TEST);
            GL11C.glDisable(GL11C.GL_BLEND);
            GL11C.glDisable(GL11C.GL_CULL_FACE);
            GL11C.glClearColor(0.0F, 0.0F, 0.0F, 0.0F);
            GL11C.glClear(GL11C.GL_COLOR_BUFFER_BIT);

            GL20C.glUseProgram(this.validationProgramId);
            int modelIdLocation = GL20C.glGetUniformLocation(this.validationProgramId, "validationModelId");
            if (modelIdLocation >= 0) {
                GL30C.glUniform1ui(modelIdLocation, this.validationFormalModelId);
            }
            GL30C.glBindBufferBase(GL43C.GL_SHADER_STORAGE_BUFFER, ForgeFormalShaderInputBindingLayout.MODEL_DATA_BINDING_INDEX, store.modelDataBufferId());
            GL30C.glBindBufferBase(GL43C.GL_SHADER_STORAGE_BUFFER, ForgeFormalShaderInputBindingLayout.MODEL_COLOUR_BINDING_INDEX, store.modelColourBufferId());
            GL11C.glBindTexture(GL11C.GL_TEXTURE_2D, store.atlasTextureId());
            GL33C.glBindSampler(ForgeFormalShaderInputBindingLayout.BLOCK_MODEL_ATLAS_TEXTURE_UNIT, store.samplerId());
            GL15C.glBindBuffer(GL40C.GL_DRAW_INDIRECT_BUFFER, this.validationIndirectBufferId);
            GL15C.glBindBuffer(GL_PARAMETER_BUFFER_ARB, this.validationParameterBufferId);

            this.modelDataBindingOk = GL30C.glGetIntegeri(GL43C.GL_SHADER_STORAGE_BUFFER_BINDING, ForgeFormalShaderInputBindingLayout.MODEL_DATA_BINDING_INDEX) == store.modelDataBufferId();
            this.modelColourBindingOk = GL30C.glGetIntegeri(GL43C.GL_SHADER_STORAGE_BUFFER_BINDING, ForgeFormalShaderInputBindingLayout.MODEL_COLOUR_BINDING_INDEX) == store.modelColourBufferId();
            this.atlasTextureBindingOk = GL11C.glGetInteger(GL11C.GL_TEXTURE_BINDING_2D) == store.atlasTextureId();
            this.samplerBindingOk = GL30C.glGetIntegeri(GL33C.GL_SAMPLER_BINDING, ForgeFormalShaderInputBindingLayout.BLOCK_MODEL_ATLAS_TEXTURE_UNIT) == store.samplerId();
            if (!this.modelDataBindingOk || !this.modelColourBindingOk || !this.atlasTextureBindingOk || !this.samplerBindingOk) {
                throw new IllegalStateException("k7-formal-resource-binding-failed");
            }

            GL43C.glMemoryBarrier(GL43C.GL_SHADER_STORAGE_BARRIER_BIT | GL43C.GL_TEXTURE_FETCH_BARRIER_BIT | GL43C.GL_COMMAND_BARRIER_BIT);
            glMultiDrawElementsIndirectCountARB(GL11C.GL_TRIANGLES, GL11C.GL_UNSIGNED_SHORT, 0L, OPAQUE_DRAW_COUNT_OFFSET_BYTES, 1, 0);
            GL11C.glFinish();
            this.offscreenValidationDrawExecuted = true;
            int error = GL11C.glGetError();
            this.lastGlError = error == GL11C.GL_NO_ERROR ? "none" : glErrorName(error);
            if (error != GL11C.GL_NO_ERROR) {
                throw new IllegalStateException("k7-validation-draw-gl-error-" + this.lastGlError);
            }
        } finally {
            GL20C.glUseProgram(oldProgram);
            GL30C.glBindVertexArray(oldVertexArray);
            GL15C.glBindBuffer(GL15C.GL_ELEMENT_ARRAY_BUFFER, oldElementArray);
            GL15C.glBindBuffer(GL40C.GL_DRAW_INDIRECT_BUFFER, oldDrawIndirect);
            GL15C.glBindBuffer(GL_PARAMETER_BUFFER_ARB, oldParameter);
            GL30C.glBindBufferBase(GL43C.GL_SHADER_STORAGE_BUFFER, ForgeFormalShaderInputBindingLayout.MODEL_DATA_BINDING_INDEX, oldModelBuffer);
            GL30C.glBindBufferBase(GL43C.GL_SHADER_STORAGE_BUFFER, ForgeFormalShaderInputBindingLayout.MODEL_COLOUR_BINDING_INDEX, oldColourBuffer);
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
            if (!this.offscreenReadbackOk) {
                throw new IllegalStateException("k7-offscreen-readback-failed:" + this.lastGlError + ":nonZero=" + nonZero);
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
        this.resetSmokeFlags();
        this.stale = true;
        this.requiresRebuild = true;
        this.lifecycleState = "STALE";
        this.lastLifecycleEvent = safeReason(reason);
        this.lastFailureReason = this.lastLifecycleEvent;
        this.lastAudit = ForgeFormalIsolatedMdicDrawSmokeTestAuditResult.failure(this.lastFailureReason, 0.0D);
        this.scheduleCleanup(this.lastLifecycleEvent);
    }

    private void scheduleCleanup(String reason) {
        if (RenderSystem.isOnRenderThread()) {
            this.closeOwnedResourcesOnRenderThread(true);
        } else {
            RenderSystem.recordRenderCall(() -> this.closeOwnedResourcesOnRenderThread(true));
        }
        this.lastLifecycleEvent = safeReason(reason);
    }

    private void closeOwnedResourcesOnRenderThread(boolean resetProgramState) {
        if (this.validationProgramId != 0) {
            GL20C.glDeleteProgram(this.validationProgramId);
        }
        if (this.offscreenFramebufferId != 0) {
            GL30C.glDeleteFramebuffers(this.offscreenFramebufferId);
        }
        if (this.offscreenTextureId != 0) {
            GL11C.glDeleteTextures(this.offscreenTextureId);
        }
        if (this.validationVertexArrayId != 0) {
            GL30C.glDeleteVertexArrays(this.validationVertexArrayId);
        }
        if (this.validationIndexBufferId != 0) {
            GL15C.glDeleteBuffers(this.validationIndexBufferId);
        }
        if (this.validationIndirectBufferId != 0) {
            GL15C.glDeleteBuffers(this.validationIndirectBufferId);
        }
        if (this.validationParameterBufferId != 0) {
            GL15C.glDeleteBuffers(this.validationParameterBufferId);
        }
        this.validationProgramId = 0;
        this.offscreenFramebufferId = 0;
        this.offscreenTextureId = 0;
        this.validationVertexArrayId = 0;
        this.validationIndexBufferId = 0;
        this.validationIndirectBufferId = 0;
        this.validationParameterBufferId = 0;
        if (resetProgramState) {
            this.shaderInputAvailable = false;
        }
    }

    private void resetSmokeFlags() {
        this.smokeTestReady = false;
        this.offscreenValidationDrawReady = false;
        this.offscreenFramebufferCreated = false;
        this.offscreenFramebufferComplete = false;
        this.offscreenValidationDrawExecuted = false;
        this.offscreenReadbackOk = false;
        this.offscreenNonZeroPixelCount = 0;
        this.offscreenChecksum = "none";
        this.realSectionCommandUsed = false;
        this.syntheticDrawFixtureUsed = false;
        this.drawCommandMatchesK6 = false;
        this.generatedCommandCount = 0;
        this.acceptedDrawCommandCount = 0;
        this.drawCountAccepted = false;
        this.geometryInputAvailable = false;
        this.modelInputAvailable = false;
        this.shaderInputAvailable = false;
        this.formalModelIdsUsed = false;
        this.validationFormalModelId = 0;
        this.validationFormalModelIds = "none";
        this.modelDataBindingOk = false;
        this.modelColourBindingOk = false;
        this.atlasTextureBindingOk = false;
        this.samplerBindingOk = false;
        this.k6FirstCommandCount = 0;
        this.k6FirstCommandInstanceCount = 0;
        this.k6FirstCommandFirstIndex = 0;
        this.k6FirstCommandBaseVertex = 0;
        this.k6FirstCommandBaseInstance = 0;
        this.firstCommandCount = 0;
        this.firstCommandInstanceCount = 0;
        this.firstCommandFirstIndex = 0;
        this.firstCommandBaseVertex = 0;
        this.firstCommandBaseInstance = 0;
        this.lastGlError = "none";
    }

    private void fail(String reason) {
        this.smokeTestReady = false;
        this.offscreenValidationDrawReady = false;
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

    private static boolean originalMdicSectionRendererChecked() {
        return fileContains("src/main/java/me/cortex/voxy/client/core/rendering/section/backend/mdic/MDICSectionRenderer.java", "glMultiDrawElementsIndirectCountARB")
                && fileContains("src/main/java/me/cortex/voxy/client/core/rendering/section/backend/mdic/MDICSectionRenderer.java", "bindRenderingBuffers")
                && fileContains("src/main/java/me/cortex/voxy/client/core/rendering/section/backend/mdic/MDICSectionRenderer.java", "GL_PARAMETER_BUFFER_ARB");
    }

    private static boolean originalShaderBindingsChecked() {
        return fileContains("src/main/resources/assets/voxy/shaders/lod/gl46/bindings.glsl", "layout(binding = MODEL_BUFFER_BINDING")
                && fileContains("src/main/resources/assets/voxy/shaders/lod/gl46/bindings.glsl", "layout(binding = MODEL_COLOUR_BUFFER_BINDING")
                && fileContains("src/main/resources/assets/voxy/shaders/lod/gl46/bindings.glsl", "struct DrawCommand")
                && fileContains("src/main/resources/assets/voxy/shaders/lod/block_model.glsl", "struct BlockModel")
                && fileContains("src/main/resources/assets/voxy/shaders/lod/gl46/quads3.vert", "#define MODEL_BUFFER_BINDING 3")
                && fileContains("src/main/resources/assets/voxy/shaders/lod/gl46/quads3.vert", "#define MODEL_COLOUR_BUFFER_BINDING 4")
                && fileContains("src/main/resources/assets/voxy/shaders/lod/gl46/quads.frag", "layout(binding = 0) uniform sampler2D blockModelAtlas");
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
            // Drain stale GL errors before the K7 offscreen validation draw.
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
