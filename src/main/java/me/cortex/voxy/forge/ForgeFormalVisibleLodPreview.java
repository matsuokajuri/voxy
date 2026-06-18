package me.cortex.voxy.forge;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import me.cortex.voxy.common.world.WorldEngine;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.common.MinecraftForge;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL12C;
import org.lwjgl.opengl.GL13C;
import org.lwjgl.opengl.GL15C;
import org.lwjgl.opengl.GL20C;
import org.lwjgl.opengl.GL21C;
import org.lwjgl.opengl.GL30C;
import org.lwjgl.opengl.GL33C;
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
import java.util.Locale;
import java.util.stream.Collectors;
import java.util.zip.CRC32;

final class ForgeFormalVisibleLodPreview {
    static final String STAGE = "K10_FORMAL_VISIBLE_LOD_PREVIEW_DEBUG_TOGGLE";
    static final String K12_K13_STAGE = "K12_K13_FORMAL_VISIBLE_PREVIEW_PREPARE_BUDGET_AND_REUSE";
    static final String K14_K16_STAGE = "K14_K16_FORMAL_MULTI_SECTION_PREVIEW_PIPELINE";
    static final String K17_K18_STAGE = "K17_K18_FORMAL_WORLD_PLACED_PREVIEW_GEOMETRY";
    static final String K19_K20_STAGE = "K19_K20_FORMAL_SECTION_METADATA_POSITION_SCRATCH_PREVIEW_ALIGNMENT";
    private static final boolean DEFAULT_ENABLED = false;
    private static final boolean DEBUG_OPT_IN_ONLY = true;
    private static final int QA_VISIBLE_FRAMES = 1;
    private static final int READBACK_SIZE = 64;
    private static final int BYTES_PER_PIXEL = 4;
    private static final int DRAW_COMMAND_FIELD_COUNT = 5;
    private static final int OBSERVE_PREPARE_FRAME_BUDGET = 2400;
    private static final int OBSERVE_PREPARE_RETRY_INTERVAL_FRAMES = 20;
    private static final double OBSERVE_PREPARE_STEP_WARN_MS = 250.0D;
    private static final float NORMAL_PREVIEW_DISTANCE = 8.0F;
    private static final float NORMAL_PREVIEW_SCALE = 1.45F;
    private static final float OBSERVE_PREVIEW_DISTANCE = 5.0F;
    private static final float OBSERVE_PREVIEW_SCALE = 1.75F;
    private static final int MULTI_SECTION_PREVIEW_MAX_RECORDS = 16;
    private static final int OBSERVE_MAX_DRAW_INDICES = MULTI_SECTION_PREVIEW_MAX_RECORDS * 6;
    private static final int PREVIEW_SECTION_DATA_BINDING_INDEX = 6;
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

            struct SectionMeta {
                uvec4 a;
                uvec4 b;
            };

            layout(std430, binding = 6) readonly buffer PreviewSectionMetadata {
                SectionMeta sectionData[];
            };

            uniform mat4 modelViewMatrix;
            uniform mat4 projectionMatrix;
            uniform vec3 previewCenter;
            uniform vec3 previewRight;
            uniform vec3 previewUp;
            uniform float previewScale;
            uniform uint observeMode;
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

            const vec2 CORNERS[4] = vec2[](
                vec2(0.0, 0.0),
                vec2(0.0, 1.0),
                vec2(1.0, 0.0),
                vec2(1.0, 1.0)
            );

            uint extractModelId(uvec2 quad) {
                return ((quad.x >> 26u) & 63u) | ((quad.y & 16383u) << 6u);
            }

            uint extractFace(uvec2 quad) {
                return quad.x & 7u;
            }

            uint extractLength(uvec2 quad) {
                return ((quad.x >> 3u) & 15u) + 1u;
            }

            uint extractWidth(uvec2 quad) {
                return ((quad.x >> 7u) & 15u) + 1u;
            }

            vec3 extractLocalPos(uvec2 quad) {
                return vec3(
                    float((quad.x >> 21u) & 31u),
                    float((quad.x >> 16u) & 31u),
                    float((quad.x >> 11u) & 31u)
                );
            }

            uint getLoDLevel(uvec2 packedPos) {
                return packedPos.x >> 28u;
            }

            ivec3 getLoDPosition(uvec2 packedPos) {
                int y = ((int(packedPos.x) << 4) >> 24);
                int x = (int(packedPos.y) << 4) >> 8;
                int z = int((packedPos.x & ((1u << 20u) - 1u)) << 4);
                z |= int(packedPos.y >> 28u);
                z <<= 8;
                z >>= 8;
                return ivec3(x, y, z);
            }

            vec3 quadCornerOffset(uint face, vec2 corner, vec2 size) {
                uint axis = face >> 1u;
                if (axis == 0u) {
                    return vec3(corner.x * size.x, 0.0, corner.y * size.y);
                }
                if (axis == 1u) {
                    return vec3(corner.x * size.x, corner.y * size.y, 0.0);
                }
                return vec3(0.0, corner.x * size.x, corner.y * size.y);
            }

            void main() {
                uint localVertex = uint(gl_VertexID);
                if (localVertex >= baseVertexBias) {
                    localVertex -= baseVertexBias;
                }
                uint safeRecordCount = max(recordCount, 1u);
                uint recordIndex = (localVertex >> 2u) % safeRecordCount;
                uint cornerIndex = localVertex & 3u;
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

                SectionMeta section = sectionData[recordIndex];
                uvec2 rawSectionPos = section.a.xy;
                uvec2 scratchSectionPos = positionScratch[recordIndex];
                if (any(notEqual(scratchSectionPos, rawSectionPos))) {
                    rawSectionPos = scratchSectionPos;
                }
                uint lodLevel = min(getLoDLevel(rawSectionPos), 12u);
                float lodScale = float(1u << lodLevel);
                vec3 sectionBase = vec3(getLoDPosition(rawSectionPos)) * lodScale * 32.0;
                vec3 localPos = extractLocalPos(rawQuad);
                vec2 quadSize = vec2(float(extractLength(rawQuad)), float(extractWidth(rawQuad)));
                vec3 worldPos = sectionBase + (localPos + quadCornerOffset(face, corner, quadSize)) * lodScale;
                if (observeMode != 0u) {
                    worldPos += vec3(0.0, 0.035 * previewScale, 0.0);
                }
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

            uniform uint observeMode;
            uniform vec3 observeTint;

            out vec4 fragColor;

            void main() {
                vec4 texel = texture(blockModelAtlas, vAtlasUv);
                float marker = float((vModelId ^ (vFace << 3u) ^ vFaceData) & 15u) / 180.0;
                vec3 rgb = max(texel.rgb * vTint.rgb + vec3(marker, marker * 0.45, 0.04), vec3(0.08, 0.04, 0.02));
                if (observeMode != 0u) {
                    rgb = max(mix(rgb, observeTint, 0.68), observeTint * 0.55);
                }
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
    private long previewRebuildRuns;
    private long shaderCompileRuns;
    private long glAllocationRuns;
    private long readbackRuns;
    private long renderLogRuns;
    private long renderHookInvocationCount;
    private long renderHookDisabledEarlyReturnCount;
    private long renderHookStaleEarlyReturnCount;
    private boolean renderHookRegistered;
    private boolean duplicateHookRegistrationDetected;
    private boolean ownerReady;
    private boolean visiblePreviewEnabled;
    private boolean observeModeEnabled;
    private boolean observeDrawPaused;
    private boolean observeModeDebugTintUsed;
    private float observeModeScale = OBSERVE_PREVIEW_SCALE;
    private boolean observeModeCameraRelative;
    private boolean observeEnableRequested;
    private boolean observeEnableHandledOnRenderThread;
    private boolean observeEnableCommandReturnedQuickly;
    private long observeEnableCommandDurationMillis;
    private boolean observeEnableDidGlWorkOnCommandThread;
    private boolean observeEnableDidReadbackOnCommandThread;
    private boolean observeEnableDidSynchronousRebuild;
    private boolean observeEnableFailedSafely;
    private boolean observeEnableTimeoutReproduced;
    private String lastObserveEnableFailureReason = "none";
    private String lastObserveEnableExceptionClass = "none";
    private String lastObserveEnableExceptionMessage = "none";
    private boolean observePrepareRequested;
    private boolean observePrepareInProgress;
    private boolean observePrepareCompleted;
    private boolean observePrepareFailedSafely;
    private boolean observePrepareAutoEnable;
    private boolean observeAutoEnabledAfterPrepare;
    private boolean observePrepareFrameBudgetExceeded;
    private int observePrepareStep;
    private int observePrepareFrameCount;
    private int observePrepareNextAttemptFrame;
    private String lastObservePrepareStepName = "none";
    private String lastObservePrepareFailureReason = "none";
    private long prepareRunCount;
    private long prepareReuseCount;
    private long prepareStartedNanos;
    private boolean prepareTimingReady;
    private boolean prepareReuseReady;
    private boolean lastPrepareReusedExistingResources;
    private boolean unnecessaryRebuildDetected;
    private boolean prepareStepBudgetExceeded;
    private double lastPrepareTotalDurationMs;
    private double lastPrepareStepDurationMs;
    private double maxPrepareStepDurationMs;
    private String slowestPrepareStepName = "none";
    private String prepareBlockingStep = "none";
    private String prepareBlockingReason = "none";
    private double prepareEnsureWorldDurationMs;
    private double prepareK6DurationMs;
    private double prepareK7DurationMs;
    private double prepareK8DurationMs;
    private double prepareK9DurationMs;
    private double prepareK10DurationMs;
    private String prepareTimingSummary = "none";
    private String previewWorldBounds = "none";
    private double previewCameraDistance;
    private boolean visiblePreviewDefaultDisabledVerified = true;
    private boolean visiblePreviewWasEnabledDuringQa;
    private boolean visiblePreviewDisabledAfterQa;
    private boolean qaAutoDisablePending;
    private int qaFramesRemaining;
    private boolean readbackPending;
    private boolean qaReadbackAllowed;
    private boolean deferredQaPending;
    private boolean deferredObserveQa;
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
    private int uniformModelViewMatrix = -1;
    private int uniformProjectionMatrix = -1;
    private int uniformPreviewCenter = -1;
    private int uniformPreviewRight = -1;
    private int uniformPreviewUp = -1;
    private int uniformPreviewScale = -1;
    private int uniformObserveMode = -1;
    private int uniformObserveTint = -1;
    private int uniformRecordCount = -1;
    private int uniformBaseVertexBias = -1;
    private int uniformBlockModelAtlas = -1;
    private boolean visiblePreviewDrawExecuted;
    private int visiblePreviewFrameCount;
    private boolean minecraftMainFramebufferDrawn;
    private boolean visiblePreviewDrawCallOk;
    private boolean visiblePreviewLightweightDrawPath = true;
    private boolean visiblePreviewDirectDrawUsed;
    private boolean visiblePreviewDrawArraysUsed;
    private boolean visiblePreviewIndirectCountDrawUsed;
    private boolean visiblePreviewValidationChecksOnly;
    private int visiblePreviewCommandIndexCount;
    private int visiblePreviewDrawIndexCount;
    private int visiblePreviewDrawIndexCap = OBSERVE_MAX_DRAW_INDICES;
    private boolean visiblePreviewDrawCountCapped;
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
    private int modelDataBufferId;
    private int modelColourBufferId;
    private int atlasTextureId;
    private int samplerId;
    private int formalGeometryRecordCount;
    private boolean geometryBufferOwnedByK10;
    private boolean multiSectionPreviewPipelineReady;
    private boolean multiSectionPreviewInputReady;
    private boolean multiSectionFormalGeometryUsed;
    private boolean multiSectionPreviewBudgetReady;
    private int multiSectionPreviewSectionCount;
    private int multiSectionPreviewInputRecordCount;
    private int multiSectionPreviewDrawRecordLimit = MULTI_SECTION_PREVIEW_MAX_RECORDS;
    private int multiSectionPreviewDrawRecordCount;
    private boolean multiSectionPreviewDrawCapped;
    private String multiSectionPreviewSectionPositions = "none";
    private String multiSectionPreviewModelIds = "none";
    private boolean singleSectionFallbackUsed;
    private boolean multiSectionSyntheticFallbackUsed;
    private boolean worldPlacedPreviewReady;
    private boolean worldPlacedPreviewUsed;
    private boolean packedQuadLocalPositionUsed;
    private boolean sectionWorldBaseUsed;
    private boolean sectionLodScaleUsed;
    private boolean previewSectionSidecarBufferCreated;
    private int previewSectionDataBufferId;
    private int worldPlacedPreviewRecordCount;
    private String worldPlacedPreviewSectionBases = "none";
    private boolean cameraBillboardFallbackUsed;
    private boolean sectionMetadataPreviewReady;
    private boolean sectionMetadataPathUsed;
    private boolean positionScratchPathUsed;
    private boolean previewSectionMetadataBufferCreated;
    private int previewSectionMetadataBufferId;
    private int previewSectionMetadataRecordCount;
    private int positionScratchEntryCount;
    private String previewSectionMetadataRawPositions = "none";
    private boolean originalSectionMetadataLayoutUsed;
    private boolean cmdgenPositionScratchSemanticsUsed;
    private boolean previewSectionSidecarFallbackUsed;
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
    private boolean perFrameRebuildDetected;
    private boolean perFrameReadbackDetected;
    private boolean perFrameShaderCompileDetected;
    private boolean perFrameGlAllocationDetected;
    private boolean perFrameLogSpamDetected;
    private long lastFrameDrawTimeNanos;
    private long totalFrameDrawTimeNanos;
    private long maxFrameDrawTimeNanos;
    private boolean renderHookEarlyReturnWhenDisabled;
    private boolean renderHookEarlyReturnWhenStale;
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
        if (this.renderHookRegistered) {
            this.duplicateHookRegistrationDetected = true;
            return;
        }
        MinecraftForge.EVENT_BUS.addListener(this::onRenderLevelStage);
        this.renderHookRegistered = true;
    }

    ForgeFormalVisibleLodPreviewStats build() {
        this.buildRuns++;
        if (this.buildRuns > 1) {
            this.previewRebuildRuns++;
        }
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
            this.createValidationDrawBuffers(k6, k8);
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
                    && this.sectionMetadataPreviewReady
                    && this.sectionMetadataPathUsed
                    && this.positionScratchPathUsed
                    && this.acceptedDrawCommandCount >= 1;
            this.multiSectionPreviewPipelineReady = this.ownerReady
                    && this.multiSectionPreviewInputReady
                    && this.multiSectionFormalGeometryUsed
                    && this.multiSectionPreviewBudgetReady
                    && this.multiSectionPreviewDrawRecordCount > 1
                    && !this.singleSectionFallbackUsed
                    && !this.multiSectionSyntheticFallbackUsed;
            this.worldPlacedPreviewReady = this.ownerReady
                    && this.worldPlacedPreviewUsed
                    && this.sectionMetadataPreviewReady
                    && this.worldPlacedPreviewRecordCount > 0
                    && !this.cameraBillboardFallbackUsed;
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
        this.observeModeEnabled = false;
        this.observeDrawPaused = false;
        this.observeModeDebugTintUsed = false;
        this.observeModeCameraRelative = false;
        this.observeModeScale = NORMAL_PREVIEW_SCALE;
        this.readbackPending = false;
        this.qaReadbackAllowed = false;
        this.lifecycleState = "VISIBLE_PREVIEW_ENABLED";
        this.lastLifecycleEvent = safeReason(reason);
        this.lastFailureReason = "none";
        return this.createStatusSnapshot();
    }

    ForgeFormalVisibleLodPreviewStats observeEnable(String reason) {
        this.enableRuns++;
        this.observeEnableRequested = false;
        this.observeEnableHandledOnRenderThread = RenderSystem.isOnRenderThread();
        this.observeEnableCommandReturnedQuickly = true;
        this.observeEnableCommandDurationMillis = 0L;
        this.observeEnableDidGlWorkOnCommandThread = false;
        this.observeEnableDidReadbackOnCommandThread = false;
        this.observeEnableDidSynchronousRebuild = false;
        this.observeEnableFailedSafely = false;
        this.observeEnableTimeoutReproduced = false;
        this.lastObserveEnableFailureReason = "none";
        this.lastObserveEnableExceptionClass = "none";
        this.lastObserveEnableExceptionMessage = "none";
        ForgeFormalVisibleLodPreviewStats status = this.ownerReady && !this.stale
                ? this.createStatusSnapshot()
                : this.build();
        if (!status.visibleLodPreviewOwnerReady()) {
            return this.createStatusSnapshot();
        }
        this.visiblePreviewEnabled = true;
        this.observeModeEnabled = true;
        this.observeDrawPaused = false;
        this.observeModeDebugTintUsed = true;
        this.observeModeCameraRelative = true;
        this.observeModeScale = OBSERVE_PREVIEW_SCALE;
        this.readbackPending = false;
        this.qaReadbackAllowed = false;
        this.lifecycleState = "VISIBLE_PREVIEW_OBSERVE_ENABLED";
        this.lastLifecycleEvent = safeReason(reason);
        this.lastFailureReason = "none";
        return this.createStatusSnapshot();
    }

    ForgeFormalVisibleLodPreviewStats requestObserveEnable(String reason) {
        long startNanos = System.nanoTime();
        long buildRunsBeforeCommand = this.buildRuns;
        long shaderCompileRunsBeforeCommand = this.shaderCompileRuns;
        long glAllocationRunsBeforeCommand = this.glAllocationRuns;
        long readbackRunsBeforeCommand = this.readbackRuns;
        this.enableRuns++;
        if (!this.ownerReady || this.stale) {
            this.observeEnableRequested = false;
            this.observeEnableHandledOnRenderThread = false;
            this.observeEnableCommandDurationMillis = Math.max(0L, (System.nanoTime() - startNanos) / 1_000_000L);
            this.observeEnableCommandReturnedQuickly = this.observeEnableCommandDurationMillis < 250L;
            this.observeEnableDidGlWorkOnCommandThread = false;
            this.observeEnableDidReadbackOnCommandThread = false;
            this.observeEnableDidSynchronousRebuild = false;
            this.observeEnableFailedSafely = true;
            this.observeEnableTimeoutReproduced = false;
            this.lastObserveEnableFailureReason = "preview-not-prepared-run-formal_visible_lod_preview_prepare";
            this.lastObserveEnableExceptionClass = "none";
            this.lastObserveEnableExceptionMessage = "none";
            this.visiblePreviewEnabled = false;
            this.observeModeEnabled = false;
            this.observeDrawPaused = false;
            this.lifecycleState = "OBSERVE_ENABLE_REJECTED_PREVIEW_NOT_PREPARED";
            this.lastLifecycleEvent = safeReason(reason);
            this.lastFailureReason = this.lastObserveEnableFailureReason;
            return this.createStatusSnapshot();
        }
        this.observeEnableRequested = true;
        this.observeEnableHandledOnRenderThread = false;
        this.observeEnableCommandReturnedQuickly = false;
        this.observeEnableCommandDurationMillis = 0L;
        this.observeEnableDidGlWorkOnCommandThread = false;
        this.observeEnableDidReadbackOnCommandThread = false;
        this.observeEnableDidSynchronousRebuild = false;
        this.observeEnableFailedSafely = false;
        this.observeEnableTimeoutReproduced = false;
        this.lastObserveEnableFailureReason = "pending-render-thread";
        this.lastObserveEnableExceptionClass = "none";
        this.lastObserveEnableExceptionMessage = "none";
        this.visiblePreviewEnabled = false;
        this.observeModeEnabled = false;
        this.observeDrawPaused = false;
        this.observeModeDebugTintUsed = true;
        this.observeModeCameraRelative = true;
        this.observeModeScale = OBSERVE_PREVIEW_SCALE;
        this.readbackPending = false;
        this.qaReadbackAllowed = false;
        this.qaAutoDisablePending = false;
        this.qaFramesRemaining = 0;
        this.lifecycleState = "VISIBLE_PREVIEW_OBSERVE_PREPARE_REQUESTED";
        this.lastLifecycleEvent = safeReason(reason);
        this.lastFailureReason = "none";
        this.observeEnableCommandDurationMillis = Math.max(0L, (System.nanoTime() - startNanos) / 1_000_000L);
        this.observeEnableDidSynchronousRebuild = this.buildRuns != buildRunsBeforeCommand;
        this.observeEnableDidReadbackOnCommandThread = this.readbackRuns != readbackRunsBeforeCommand;
        this.observeEnableDidGlWorkOnCommandThread = this.glAllocationRuns != glAllocationRunsBeforeCommand
                || this.shaderCompileRuns != shaderCompileRunsBeforeCommand;
        this.observeEnableCommandReturnedQuickly = this.observeEnableCommandDurationMillis < 250L
                && !this.observeEnableDidSynchronousRebuild
                && !this.observeEnableDidReadbackOnCommandThread
                && !this.observeEnableDidGlWorkOnCommandThread;
        return this.createStatusSnapshot();
    }

    ForgeFormalVisibleLodPreviewStats requestPreviewPrepare(String reason) {
        long startNanos = System.nanoTime();
        long buildRunsBeforeCommand = this.buildRuns;
        long shaderCompileRunsBeforeCommand = this.shaderCompileRuns;
        long glAllocationRunsBeforeCommand = this.glAllocationRuns;
        long readbackRunsBeforeCommand = this.readbackRuns;
        this.prepareRunCount++;
        this.observeEnableRequested = false;
        this.observeEnableHandledOnRenderThread = false;
        this.observeEnableCommandDurationMillis = 0L;
        this.observeEnableDidGlWorkOnCommandThread = false;
        this.observeEnableDidReadbackOnCommandThread = false;
        this.observeEnableDidSynchronousRebuild = false;
        this.observeEnableFailedSafely = false;
        this.observeEnableTimeoutReproduced = false;
        this.lastObserveEnableFailureReason = "none";
        this.lastObserveEnableExceptionClass = "none";
        this.lastObserveEnableExceptionMessage = "none";
        if (this.observePrepareRequested || this.observePrepareInProgress) {
            this.observeEnableCommandDurationMillis = Math.max(0L, (System.nanoTime() - startNanos) / 1_000_000L);
            this.observeEnableCommandReturnedQuickly = this.observeEnableCommandDurationMillis < 250L;
            this.lastPrepareReusedExistingResources = false;
            this.prepareReuseReady = false;
            this.unnecessaryRebuildDetected = false;
            this.lastLifecycleEvent = safeReason(reason);
            this.lastObservePrepareStepName = "prepare-already-in-progress";
            return this.createStatusSnapshot();
        }
        if (this.ownerReady && !this.stale) {
            this.prepareReuseCount++;
            this.observePrepareRequested = false;
            this.observePrepareInProgress = false;
            this.observePrepareCompleted = true;
            this.observePrepareFailedSafely = false;
            this.observePrepareAutoEnable = false;
            this.observeAutoEnabledAfterPrepare = false;
            this.observePrepareFrameBudgetExceeded = false;
            this.lastObservePrepareStepName = "already-prepared";
            this.lastObservePrepareFailureReason = "none";
            this.visiblePreviewEnabled = false;
            this.observeModeEnabled = false;
            this.lifecycleState = "VISIBLE_PREVIEW_ALREADY_PREPARED_DISABLED";
            this.lastLifecycleEvent = safeReason(reason);
            this.lastFailureReason = "none";
            this.observeEnableCommandDurationMillis = Math.max(0L, (System.nanoTime() - startNanos) / 1_000_000L);
            this.observeEnableCommandReturnedQuickly = this.observeEnableCommandDurationMillis < 250L;
            this.prepareTimingReady = true;
            this.prepareReuseReady = true;
            this.lastPrepareReusedExistingResources = true;
            this.unnecessaryRebuildDetected = this.buildRuns != buildRunsBeforeCommand
                    || this.readbackRuns != readbackRunsBeforeCommand
                    || this.glAllocationRuns != glAllocationRunsBeforeCommand
                    || this.shaderCompileRuns != shaderCompileRunsBeforeCommand;
            this.prepareStepBudgetExceeded = false;
            this.prepareStartedNanos = 0L;
            this.lastPrepareTotalDurationMs = this.observeEnableCommandDurationMillis;
            this.lastPrepareStepDurationMs = 0.0D;
            this.maxPrepareStepDurationMs = 0.0D;
            this.slowestPrepareStepName = "already-prepared";
            this.prepareBlockingStep = "none";
            this.prepareBlockingReason = "none";
            this.prepareEnsureWorldDurationMs = 0.0D;
            this.prepareK6DurationMs = 0.0D;
            this.prepareK7DurationMs = 0.0D;
            this.prepareK8DurationMs = 0.0D;
            this.prepareK9DurationMs = 0.0D;
            this.prepareK10DurationMs = 0.0D;
            this.prepareTimingSummary = "reused-existing-resources";
            return this.createStatusSnapshot();
        }
        this.resetPrepareTimingForNewRun();
        this.observePrepareRequested = true;
        this.observePrepareInProgress = false;
        this.observePrepareCompleted = false;
        this.observePrepareFailedSafely = false;
        this.observePrepareAutoEnable = false;
        this.observeAutoEnabledAfterPrepare = false;
        this.observePrepareFrameBudgetExceeded = false;
        this.observePrepareStep = 0;
        this.observePrepareFrameCount = 0;
        this.observePrepareNextAttemptFrame = 0;
        this.lastObservePrepareStepName = "prepare-requested";
        this.lastObservePrepareFailureReason = "none";
        this.visiblePreviewEnabled = false;
        this.observeModeEnabled = false;
        this.observeDrawPaused = false;
        this.readbackPending = false;
        this.qaReadbackAllowed = false;
        this.qaAutoDisablePending = false;
        this.qaFramesRemaining = 0;
        this.lifecycleState = "VISIBLE_PREVIEW_PREPARE_REQUESTED";
        this.lastLifecycleEvent = safeReason(reason);
        this.lastFailureReason = "none";
        this.observeEnableCommandDurationMillis = Math.max(0L, (System.nanoTime() - startNanos) / 1_000_000L);
        this.observeEnableDidSynchronousRebuild = this.buildRuns != buildRunsBeforeCommand;
        this.observeEnableDidReadbackOnCommandThread = this.readbackRuns != readbackRunsBeforeCommand;
        this.observeEnableDidGlWorkOnCommandThread = this.glAllocationRuns != glAllocationRunsBeforeCommand
                || this.shaderCompileRuns != shaderCompileRunsBeforeCommand;
        this.observeEnableCommandReturnedQuickly = this.observeEnableCommandDurationMillis < 250L
                && !this.observeEnableDidSynchronousRebuild
                && !this.observeEnableDidReadbackOnCommandThread
                && !this.observeEnableDidGlWorkOnCommandThread;
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
        this.deferredObserveQa = false;
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

    ForgeFormalVisibleLodPreviewStats runObservePerformanceQa() {
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
        this.deferredObserveQa = true;
        this.deferredQaStep = 0;
        this.visiblePreviewWasEnabledDuringQa = false;
        this.visiblePreviewDisabledAfterQa = false;
        this.qaAutoDisablePending = false;
        this.qaFramesRemaining = 0;
        this.readbackPending = false;
        this.qaReadbackAllowed = false;
        this.firstDrawLogged = false;
        this.lifecycleState = "VISIBLE_PREVIEW_OBSERVE_QA_SCHEDULED";
        this.lastLifecycleEvent = "observe-performance-qa-scheduled";
        this.lastFailureReason = "none";
        VoxyForge.LOGGER.info("Voxy K10.1 visible LoD preview observe/performance QA scheduled: stepCount=5 previewDefaultEnabled=false debugOptInOnly=true");
        this.instance.getFormalRendererManager().checkReadiness("qa-k10-visible-preview-observe-performance-scheduled");
        return this.createStatusSnapshot();
    }

    ForgeFormalVisibleLodPreviewStats disable(String reason) {
        this.disableRuns++;
        this.visiblePreviewEnabled = false;
        this.observeModeEnabled = false;
        this.observeDrawPaused = false;
        this.observeModeDebugTintUsed = false;
        this.observeModeCameraRelative = false;
        this.observeEnableRequested = false;
        this.observeEnableFailedSafely = false;
        this.cancelObservePrepare("disable");
        this.readbackPending = false;
        this.qaReadbackAllowed = false;
        this.qaAutoDisablePending = false;
        this.qaFramesRemaining = 0;
        this.renderHookEarlyReturnWhenDisabled = true;
        this.lifecycleState = "DISABLED";
        this.lastLifecycleEvent = safeReason(reason);
        this.lastFailureReason = "none";
        return this.createStatusSnapshot();
    }

    ForgeFormalVisibleLodPreviewStats pauseObserveDraw(String reason) {
        this.observeDrawPaused = true;
        this.lifecycleState = "VISIBLE_PREVIEW_OBSERVE_DRAW_PAUSED";
        this.lastLifecycleEvent = safeReason(reason);
        this.lastFailureReason = "none";
        this.renderHookEarlyReturnWhenDisabled = true;
        return this.createStatusSnapshot();
    }

    ForgeFormalVisibleLodPreviewStats resumeObserveDraw(String reason) {
        this.observeDrawPaused = false;
        this.lifecycleState = this.observeModeEnabled ? "VISIBLE_PREVIEW_OBSERVE_DRAW_RESUMED" : "VISIBLE_PREVIEW_DRAW_RESUMED";
        this.lastLifecycleEvent = safeReason(reason);
        this.lastFailureReason = "none";
        return this.createStatusSnapshot();
    }

    boolean isObserveDrawPaused() {
        return this.observeDrawPaused;
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
                this.buildRuns,
                this.previewRebuildRuns,
                this.visiblePreviewFrameCount,
                this.renderHookInvocationCount,
                this.shaderCompileRuns,
                this.glAllocationRuns,
                this.readbackRuns,
                this.renderLogRuns,
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
                this.observeModeEnabled,
                this.observeModeDebugTintUsed,
                this.observeModeScale,
                this.observeModeCameraRelative,
                this.observeEnableRequested,
                this.observeEnableHandledOnRenderThread,
                this.observeEnableCommandReturnedQuickly,
                this.observeEnableCommandDurationMillis,
                this.observeEnableDidGlWorkOnCommandThread,
                this.observeEnableDidReadbackOnCommandThread,
                this.observeEnableDidSynchronousRebuild,
                this.observeEnableFailedSafely,
                this.observeEnableTimeoutReproduced,
                this.lastObserveEnableFailureReason,
                this.lastObserveEnableExceptionClass,
                this.lastObserveEnableExceptionMessage,
                this.observePrepareRequested,
                this.observePrepareInProgress,
                this.observePrepareCompleted,
                this.observePrepareFailedSafely,
                this.observeAutoEnabledAfterPrepare,
                this.observePrepareFrameBudgetExceeded,
                this.observePrepareStep,
                this.observePrepareFrameCount,
                this.observePrepareNextAttemptFrame,
                this.lastObservePrepareStepName,
                this.lastObservePrepareFailureReason,
                K12_K13_STAGE,
                this.prepareRunCount,
                this.prepareReuseCount,
                this.prepareTimingReady,
                this.prepareReuseReady,
                this.lastPrepareReusedExistingResources,
                this.unnecessaryRebuildDetected,
                this.prepareStepBudgetExceeded,
                OBSERVE_PREPARE_STEP_WARN_MS,
                this.lastPrepareTotalDurationMs,
                this.lastPrepareStepDurationMs,
                this.maxPrepareStepDurationMs,
                this.slowestPrepareStepName,
                this.prepareBlockingStep,
                this.prepareBlockingReason,
                this.prepareEnsureWorldDurationMs,
                this.prepareK6DurationMs,
                this.prepareK7DurationMs,
                this.prepareK8DurationMs,
                this.prepareK9DurationMs,
                this.prepareK10DurationMs,
                this.prepareTimingSummary,
                K14_K16_STAGE,
                this.multiSectionPreviewPipelineReady,
                this.multiSectionPreviewInputReady,
                this.multiSectionFormalGeometryUsed,
                this.multiSectionPreviewBudgetReady,
                this.multiSectionPreviewSectionCount,
                this.multiSectionPreviewInputRecordCount,
                this.multiSectionPreviewDrawRecordLimit,
                this.multiSectionPreviewDrawRecordCount,
                this.multiSectionPreviewDrawCapped,
                this.multiSectionPreviewSectionPositions,
                this.multiSectionPreviewModelIds,
                this.singleSectionFallbackUsed,
                this.multiSectionSyntheticFallbackUsed,
                K17_K18_STAGE,
                this.worldPlacedPreviewReady,
                this.worldPlacedPreviewUsed,
                this.packedQuadLocalPositionUsed,
                this.sectionWorldBaseUsed,
                this.sectionLodScaleUsed,
                this.previewSectionSidecarBufferCreated,
                this.previewSectionDataBufferId,
                this.worldPlacedPreviewRecordCount,
                this.worldPlacedPreviewSectionBases,
                this.cameraBillboardFallbackUsed,
                K19_K20_STAGE,
                this.sectionMetadataPreviewReady,
                this.sectionMetadataPathUsed,
                this.positionScratchPathUsed,
                this.previewSectionMetadataBufferCreated,
                this.previewSectionMetadataBufferId,
                this.previewSectionMetadataRecordCount,
                this.positionScratchEntryCount,
                this.previewSectionMetadataRawPositions,
                this.originalSectionMetadataLayoutUsed,
                this.cmdgenPositionScratchSemanticsUsed,
                this.previewSectionSidecarFallbackUsed,
                this.previewWorldBounds,
                this.previewCameraDistance,
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
                this.visiblePreviewLightweightDrawPath,
                this.visiblePreviewDirectDrawUsed,
                this.visiblePreviewDrawArraysUsed,
                this.visiblePreviewIndirectCountDrawUsed,
                this.visiblePreviewValidationChecksOnly,
                this.visiblePreviewCommandIndexCount,
                this.visiblePreviewDrawIndexCount,
                this.visiblePreviewDrawIndexCap,
                this.visiblePreviewDrawCountCapped,
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
                this.perFrameRebuildDetected,
                this.perFrameReadbackDetected,
                this.perFrameShaderCompileDetected,
                this.perFrameGlAllocationDetected,
                this.perFrameLogSpamDetected,
                this.duplicateHookRegistrationDetected,
                this.lastFrameDrawTimeNanos,
                this.visiblePreviewFrameCount <= 0 ? 0L : this.totalFrameDrawTimeNanos / this.visiblePreviewFrameCount,
                this.maxFrameDrawTimeNanos,
                this.renderHookEarlyReturnWhenDisabled,
                this.renderHookEarlyReturnWhenStale,
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
        this.observeEnableRequested = false;
        this.observeEnableFailedSafely = false;
        this.cancelObservePrepare("clear");
        this.stale = false;
        this.requiresRebuild = false;
        this.lifecycleState = "CLEARED";
        this.lastLifecycleEvent = "clear";
        this.lastFailureReason = "none";
        this.resetBuildFlags();
        this.renderHookEarlyReturnWhenDisabled = true;
        this.lastAudit = ForgeFormalVisibleLodPreviewAuditResult.failure("cleared", 0.0D);
        this.scheduleCleanup("clear");
    }

    void markResourceReload() {
        if (this.observePrepareRequested || this.observePrepareInProgress) {
            this.visiblePreviewEnabled = false;
            this.observeModeEnabled = false;
            this.ownerReady = false;
            this.stale = true;
            this.requiresRebuild = true;
            this.lifecycleState = "VISIBLE_PREVIEW_OBSERVE_PREPARE_RESOURCE_RELOAD_SEEN";
            this.lastLifecycleEvent = "resource-reload-during-observe-prepare";
            this.lastFailureReason = "observe-prepare-resource-reload-rebuild-continues";
            this.lastObservePrepareStepName = "resource-reload-rebuild-continues";
            this.renderHookEarlyReturnWhenStale = true;
            this.lastAudit = ForgeFormalVisibleLodPreviewAuditResult.failure(this.lastFailureReason, 0.0D);
            this.scheduleCleanup(this.lastLifecycleEvent);
            return;
        }
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
        this.renderHookInvocationCount++;
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
        if (this.observeEnableRequested) {
            this.handleObserveEnableRequestOnRenderThread();
        }
        if (this.observePrepareRequested && !this.observePrepareInProgress) {
            this.startObservePrepareOnRenderThread("prepare-request");
        }
        if (this.observePrepareInProgress) {
            this.runObservePrepareStep();
        }
        if (!this.visiblePreviewEnabled) {
            this.renderHookDisabledEarlyReturnCount++;
            this.renderHookEarlyReturnWhenDisabled = true;
            return;
        }
        if (this.observeDrawPaused) {
            this.renderHookDisabledEarlyReturnCount++;
            this.renderHookEarlyReturnWhenDisabled = true;
            return;
        }
        if (!this.ownerReady || this.stale) {
            this.lastFailureReason = "render-hook-owner-not-ready";
            this.visiblePreviewEnabled = false;
            this.renderHookStaleEarlyReturnCount++;
            this.renderHookEarlyReturnWhenStale = true;
            return;
        }
        try {
            long buildRunsBeforeFrame = this.buildRuns;
            long readbackRunsBeforeFrame = this.readbackRuns;
            long shaderCompileRunsBeforeFrame = this.shaderCompileRuns;
            long glAllocationRunsBeforeFrame = this.glAllocationRuns;
            long renderLogRunsBeforeFrame = this.renderLogRuns;
            long startNanos = System.nanoTime();
            this.executeVisiblePreviewDraw(event, minecraft);
            long elapsed = System.nanoTime() - startNanos;
            this.lastFrameDrawTimeNanos = elapsed;
            this.totalFrameDrawTimeNanos += elapsed;
            this.maxFrameDrawTimeNanos = Math.max(this.maxFrameDrawTimeNanos, elapsed);
            this.visiblePreviewFrameCount++;
            this.perFrameRebuildDetected |= this.buildRuns != buildRunsBeforeFrame;
            this.perFrameReadbackDetected |= this.readbackRuns != readbackRunsBeforeFrame && this.visiblePreviewFrameCount > 1;
            this.perFrameShaderCompileDetected |= this.shaderCompileRuns != shaderCompileRunsBeforeFrame;
            this.perFrameGlAllocationDetected |= this.glAllocationRuns != glAllocationRunsBeforeFrame;
            this.perFrameLogSpamDetected |= this.renderLogRuns - renderLogRunsBeforeFrame > 1;
            if (!this.firstDrawLogged) {
                this.firstDrawLogged = true;
                ForgeFormalRendererStats rendererStatus = this.instance.getFormalRendererManager().checkReadiness("k10-visible-preview-first-draw");
                this.renderLogRuns++;
                VoxyForge.LOGGER.info("Voxy K10 visible LoD preview first draw: {} {}", this.dump(), ForgeVoxyCommands.formatFormalRendererStatusForLog(rendererStatus));
            }
            if (this.qaAutoDisablePending) {
                this.qaFramesRemaining--;
                if (this.qaFramesRemaining <= 0) {
                    this.visiblePreviewEnabled = false;
                    this.visiblePreviewDisabledAfterQa = true;
                    this.qaAutoDisablePending = false;
                    this.readbackPending = false;
                    this.qaReadbackAllowed = false;
                    this.renderHookEarlyReturnWhenDisabled = true;
                    this.lifecycleState = "DISABLED_AFTER_QA";
                    this.lastLifecycleEvent = "qa-auto-disable-after-visible-preview";
                    ForgeFormalVisibleLodPreviewAuditResult audit = this.audit();
                    ForgeFormalRendererStats rendererStatus = this.instance.getFormalRendererManager().checkReadiness("k10-visible-preview-qa-auto-disabled");
                    this.renderLogRuns++;
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

    private void handleObserveEnableRequestOnRenderThread() {
        this.observeEnableHandledOnRenderThread = true;
        this.observeEnableRequested = false;
        this.lastObserveEnableExceptionClass = "none";
        this.lastObserveEnableExceptionMessage = "none";
        try {
            if (!RenderSystem.isOnRenderThread()) {
                this.failObserveEnableSafely("observe-enable-not-render-thread", null);
                return;
            }
            if (!this.ownerReady || this.stale) {
                this.failObserveEnableSafely("preview-not-prepared-run-formal_visible_lod_preview_prepare", null);
                return;
            }
            this.enableObserveFromPreparedResources("observe-enable-render-thread", false);
        } catch (RuntimeException e) {
            this.failObserveEnableSafely(e.getClass().getSimpleName() + ":" + e.getMessage(), e);
        }
    }

    private void startObservePrepareOnRenderThread(String reason) {
        this.observePrepareRequested = false;
        this.observePrepareInProgress = true;
        this.observePrepareCompleted = false;
        this.observePrepareFailedSafely = false;
        this.observeAutoEnabledAfterPrepare = false;
        this.observePrepareFrameBudgetExceeded = false;
        this.observePrepareStep = 0;
        this.observePrepareFrameCount = 0;
        this.observePrepareNextAttemptFrame = 0;
        this.lastObservePrepareStepName = "start:" + safeReason(reason);
        this.lastObservePrepareFailureReason = "none";
        this.prepareStartedNanos = System.nanoTime();
        this.prepareTimingReady = false;
        this.prepareReuseReady = false;
        this.lastPrepareReusedExistingResources = false;
        this.unnecessaryRebuildDetected = false;
        this.prepareBlockingStep = "none";
        this.prepareBlockingReason = "none";
        this.prepareTimingSummary = "running";
        this.visiblePreviewEnabled = false;
        this.observeModeEnabled = false;
        this.readbackPending = false;
        this.qaReadbackAllowed = false;
        this.lifecycleState = "VISIBLE_PREVIEW_OBSERVE_PREPARE_IN_PROGRESS";
        this.lastLifecycleEvent = "observe-prepare-start";
        this.lastFailureReason = "observe-prepare-in-progress";
        this.lastObserveEnableFailureReason = "observe-prepare-in-progress";
    }

    private void runObservePrepareStep() {
        this.observePrepareFrameCount++;
        if (this.observePrepareFrameCount > OBSERVE_PREPARE_FRAME_BUDGET) {
            String reason = "observe-prepare-frame-budget-exceeded:" + OBSERVE_PREPARE_FRAME_BUDGET;
            this.setPrepareBlocking("frame-budget", reason);
            this.failObservePrepareSafely(reason, null);
            return;
        }
        if (this.observePrepareFrameCount < this.observePrepareNextAttemptFrame) {
            return;
        }
        try {
            switch (this.observePrepareStep) {
                case 0 -> {
                    long stepStart = System.nanoTime();
                    String stepName = "ensure-world";
                    this.lastObservePrepareStepName = stepName;
                    if (!this.instance.ensureActiveWorldSkeletonForCurrentWorldIfAllowed()) {
                        String reason = "observe-prepare-world-engine-skeleton-not-ready";
                        this.recordPrepareStepTiming(stepName, stepStart);
                        this.setPrepareBlocking(stepName, reason);
                        this.failObservePrepareSafely(reason, null);
                        return;
                    }
                    this.recordPrepareStepTiming(stepName, stepStart);
                    this.observePrepareStep++;
                }
                case 1 -> {
                    long stepStart = System.nanoTime();
                    String stepName = "k6-real-section-dry-run";
                    this.lastObservePrepareStepName = stepName;
                    ForgeFormalCmdgenRealSectionDryRunStats k6 = this.instance.getFormalCmdgenRealSectionDryRun().build();
                    if (!k6.realSectionDryRunReady()) {
                        String reason = "observe-prepare-k6-not-ready:" + k6.lastFailureReason();
                        this.recordPrepareStepTiming(stepName, stepStart);
                        this.setPrepareBlocking(stepName, reason);
                        this.waitForObservePreparePrerequisite(reason, false);
                        return;
                    }
                    this.recordPrepareStepTiming(stepName, stepStart);
                    this.observePrepareStep++;
                }
                case 2 -> {
                    long stepStart = System.nanoTime();
                    String stepName = "k7-isolated-mdic-draw";
                    this.lastObservePrepareStepName = stepName;
                    ForgeFormalIsolatedMdicDrawSmokeTestStats k7 = this.instance.getFormalIsolatedMdicDrawSmokeTest().build();
                    if (!k7.isolatedMdicDrawSmokeTestReady()) {
                        ForgeFormalCmdgenRealSectionDryRunStats k6 = this.instance.getFormalCmdgenRealSectionDryRun().createStatusSnapshot();
                        String reason = "observe-prepare-k7-not-ready:" + k7.lastFailureReason() + ":k6=" + k6.lastFailureReason();
                        this.recordPrepareStepTiming(stepName, stepStart);
                        this.setPrepareBlocking(stepName, reason);
                        this.waitForObservePreparePrerequisite(reason, true);
                        return;
                    }
                    this.recordPrepareStepTiming(stepName, stepStart);
                    this.observePrepareStep++;
                }
                case 3 -> {
                    long stepStart = System.nanoTime();
                    String stepName = "k8-formal-model-id-geometry";
                    this.lastObservePrepareStepName = stepName;
                    ForgeFormalModelIdSectionGeometryStats k8 = this.instance.getFormalModelIdSectionGeometryPath().build();
                    if (!k8.formalModelIdGeometryPathReady()) {
                        String reason = "observe-prepare-k8-not-ready:" + k8.lastFailureReason();
                        this.recordPrepareStepTiming(stepName, stepStart);
                        this.setPrepareBlocking(stepName, reason);
                        this.waitForObservePreparePrerequisite(reason, true);
                        return;
                    }
                    this.recordPrepareStepTiming(stepName, stepStart);
                    this.observePrepareStep++;
                }
                case 4 -> {
                    long stepStart = System.nanoTime();
                    String stepName = "k9-terrain-shader-integration";
                    this.lastObservePrepareStepName = stepName;
                    ForgeFormalTerrainShaderIntegrationStats k9 = this.instance.getFormalTerrainShaderIntegration().build();
                    if (!k9.formalTerrainShaderIntegrationReady()) {
                        String reason = "observe-prepare-k9-not-ready:" + k9.lastFailureReason();
                        this.recordPrepareStepTiming(stepName, stepStart);
                        this.setPrepareBlocking(stepName, reason);
                        this.waitForObservePreparePrerequisite(reason, true);
                        return;
                    }
                    this.recordPrepareStepTiming(stepName, stepStart);
                    this.observePrepareStep++;
                }
                case 5 -> {
                    long stepStart = System.nanoTime();
                    String stepName = "k10-visible-preview-build";
                    this.lastObservePrepareStepName = stepName;
                    ForgeFormalVisibleLodPreviewStats status = this.build();
                    this.restoreObserveEnableCommandSafetyAfterRenderPrepare();
                    if (!status.visibleLodPreviewOwnerReady()) {
                        String reason = "observe-prepare-k10-not-ready:" + status.lastFailureReason();
                        this.recordPrepareStepTiming(stepName, stepStart);
                        this.setPrepareBlocking(stepName, reason);
                        this.waitForObservePreparePrerequisite(reason, true);
                        return;
                    }
                    this.recordPrepareStepTiming(stepName, stepStart);
                    this.observePrepareStep++;
                }
                case 6 -> {
                    long stepStart = System.nanoTime();
                    this.lastObservePrepareStepName = this.observePrepareAutoEnable ? "enable-observe" : "prepared-disabled";
                    this.observePrepareInProgress = false;
                    this.observePrepareCompleted = true;
                    this.observePrepareFailedSafely = false;
                    this.lastObservePrepareFailureReason = "none";
                    if (this.observePrepareAutoEnable) {
                        if (!this.enableObserveFromPreparedResources("observe-prepare-complete", true)) {
                            String reason = "observe-prepare-enable-failed:ownerReady=" + this.ownerReady + ":stale=" + this.stale;
                            this.recordPrepareStepTiming(this.lastObservePrepareStepName, stepStart);
                            this.setPrepareBlocking(this.lastObservePrepareStepName, reason);
                            this.failObservePrepareSafely(reason, null);
                            return;
                        }
                        this.observeAutoEnabledAfterPrepare = true;
                        this.instance.getFormalRendererManager().checkReadiness("k10-observe-prepare-complete");
                    } else {
                        this.visiblePreviewEnabled = false;
                        this.observeModeEnabled = false;
                        this.observeAutoEnabledAfterPrepare = false;
                        this.lifecycleState = "VISIBLE_PREVIEW_PREPARED_DISABLED";
                        this.lastLifecycleEvent = "k11-visible-preview-prepare-complete";
                        this.lastFailureReason = "none";
                        this.renderHookEarlyReturnWhenDisabled = true;
                        this.instance.getFormalRendererManager().checkReadiness("k11-visible-preview-prepare-complete");
                    }
                    this.recordPrepareStepTiming(this.lastObservePrepareStepName, stepStart);
                    this.finishPrepareTiming();
                }
                default -> this.observePrepareInProgress = false;
            }
        } catch (RuntimeException e) {
            this.setPrepareBlocking(this.lastObservePrepareStepName, e.getClass().getSimpleName() + ":" + e.getMessage());
            this.failObservePrepareSafely(e.getClass().getSimpleName() + ":" + e.getMessage(), e);
        }
    }

    private void resetPrepareTimingForNewRun() {
        this.prepareStartedNanos = 0L;
        this.prepareTimingReady = false;
        this.prepareReuseReady = false;
        this.lastPrepareReusedExistingResources = false;
        this.unnecessaryRebuildDetected = false;
        this.prepareStepBudgetExceeded = false;
        this.lastPrepareTotalDurationMs = 0.0D;
        this.lastPrepareStepDurationMs = 0.0D;
        this.maxPrepareStepDurationMs = 0.0D;
        this.slowestPrepareStepName = "none";
        this.prepareBlockingStep = "none";
        this.prepareBlockingReason = "none";
        this.prepareEnsureWorldDurationMs = 0.0D;
        this.prepareK6DurationMs = 0.0D;
        this.prepareK7DurationMs = 0.0D;
        this.prepareK8DurationMs = 0.0D;
        this.prepareK9DurationMs = 0.0D;
        this.prepareK10DurationMs = 0.0D;
        this.prepareTimingSummary = "pending";
    }

    private void recordPrepareStepTiming(String stepName, long stepStartNanos) {
        double durationMs = elapsedMs(stepStartNanos);
        this.lastPrepareStepDurationMs = durationMs;
        switch (stepName) {
            case "ensure-world" -> this.prepareEnsureWorldDurationMs = durationMs;
            case "k6-real-section-dry-run" -> this.prepareK6DurationMs = durationMs;
            case "k7-isolated-mdic-draw" -> this.prepareK7DurationMs = durationMs;
            case "k8-formal-model-id-geometry" -> this.prepareK8DurationMs = durationMs;
            case "k9-terrain-shader-integration" -> this.prepareK9DurationMs = durationMs;
            case "k10-visible-preview-build" -> this.prepareK10DurationMs = durationMs;
            default -> {
                // Final enable/disabled steps are tracked as last/slowest only.
            }
        }
        if (durationMs >= this.maxPrepareStepDurationMs) {
            this.maxPrepareStepDurationMs = durationMs;
            this.slowestPrepareStepName = stepName;
        }
        this.prepareStepBudgetExceeded |= durationMs > OBSERVE_PREPARE_STEP_WARN_MS;
        this.prepareTimingSummary = this.formatPrepareTimingSummary("running");
    }

    private void finishPrepareTiming() {
        this.lastPrepareTotalDurationMs = elapsedMs(this.prepareStartedNanos);
        this.prepareTimingReady = true;
        this.prepareReuseReady = true;
        this.lastPrepareReusedExistingResources = false;
        this.prepareBlockingStep = "none";
        this.prepareBlockingReason = "none";
        this.prepareTimingSummary = this.formatPrepareTimingSummary("complete");
    }

    private void setPrepareBlocking(String stepName, String reason) {
        this.prepareBlockingStep = safeReason(stepName);
        this.prepareBlockingReason = safeReason(reason);
        this.prepareTimingSummary = this.formatPrepareTimingSummary("blocked");
    }

    private String formatPrepareTimingSummary(String state) {
        return String.format(Locale.ROOT,
                "state=%s,totalMs=%.2f,slowest=%s,slowestMs=%.2f,ensureMs=%.2f,k6Ms=%.2f,k7Ms=%.2f,k8Ms=%.2f,k9Ms=%.2f,k10Ms=%.2f",
                state,
                this.lastPrepareTotalDurationMs,
                this.slowestPrepareStepName,
                this.maxPrepareStepDurationMs,
                this.prepareEnsureWorldDurationMs,
                this.prepareK6DurationMs,
                this.prepareK7DurationMs,
                this.prepareK8DurationMs,
                this.prepareK9DurationMs,
                this.prepareK10DurationMs);
    }

    private boolean enableObserveFromPreparedResources(String reason, boolean autoEnabledAfterPrepare) {
        if (!this.ownerReady || this.stale) {
            return false;
        }
        this.visiblePreviewEnabled = true;
        this.observeModeEnabled = true;
        this.observeModeDebugTintUsed = true;
        this.observeModeCameraRelative = true;
        this.observeModeScale = OBSERVE_PREVIEW_SCALE;
        this.readbackPending = false;
        this.qaReadbackAllowed = false;
        this.firstDrawLogged = false;
        this.lifecycleState = autoEnabledAfterPrepare
                ? "VISIBLE_PREVIEW_OBSERVE_ENABLED_AFTER_PREPARE"
                : "VISIBLE_PREVIEW_OBSERVE_ENABLED_RENDER_THREAD";
        this.lastLifecycleEvent = safeReason(reason);
        this.lastFailureReason = "none";
        this.lastObserveEnableFailureReason = "none";
        this.observeEnableFailedSafely = false;
        this.observeEnableTimeoutReproduced = false;
        this.observePrepareFailedSafely = false;
        return true;
    }

    private void restoreObserveEnableCommandSafetyAfterRenderPrepare() {
        this.observeEnableHandledOnRenderThread = true;
        this.observeEnableCommandReturnedQuickly = true;
        this.observeEnableDidGlWorkOnCommandThread = false;
        this.observeEnableDidReadbackOnCommandThread = false;
        this.observeEnableDidSynchronousRebuild = false;
        this.observeEnableTimeoutReproduced = false;
        this.lastObserveEnableExceptionClass = "none";
        this.lastObserveEnableExceptionMessage = "none";
    }

    private void waitForObservePreparePrerequisite(String reason, boolean rewindToK6) {
        String safeReason = safeReason(reason);
        if (rewindToK6) {
            this.observePrepareStep = 1;
        }
        this.observePrepareNextAttemptFrame = this.observePrepareFrameCount + OBSERVE_PREPARE_RETRY_INTERVAL_FRAMES;
        this.lastObservePrepareFailureReason = safeReason;
        this.lastObservePrepareStepName = "waiting:" + safeReason;
        this.lastObserveEnableFailureReason = safeReason;
        this.lifecycleState = "VISIBLE_PREVIEW_OBSERVE_PREPARE_WAITING";
        this.lastLifecycleEvent = "observe-prepare-waiting";
        this.lastFailureReason = safeReason;
        this.visiblePreviewEnabled = false;
        this.observeModeEnabled = false;
        this.readbackPending = false;
        this.qaReadbackAllowed = false;
        this.renderHookEarlyReturnWhenDisabled = true;
    }

    private void failObservePrepareSafely(String reason, RuntimeException exception) {
        String safeReason = safeReason(reason);
        this.observePrepareRequested = false;
        this.observePrepareInProgress = false;
        this.observePrepareCompleted = false;
        this.observePrepareFailedSafely = true;
        this.observePrepareAutoEnable = false;
        this.observeAutoEnabledAfterPrepare = false;
        this.observePrepareFrameBudgetExceeded = safeReason.contains("frame-budget-exceeded");
        this.observePrepareNextAttemptFrame = 0;
        this.lastObservePrepareFailureReason = safeReason;
        this.observeEnableFailedSafely = true;
        this.lastObserveEnableFailureReason = safeReason;
        this.lastObserveEnableExceptionClass = exception == null ? "none" : exception.getClass().getName();
        this.lastObserveEnableExceptionMessage = exception == null ? "none" : safeReason(exception.getMessage());
        this.visiblePreviewEnabled = false;
        this.observeModeEnabled = false;
        this.observeModeDebugTintUsed = false;
        this.observeModeCameraRelative = false;
        this.readbackPending = false;
        this.qaReadbackAllowed = false;
        this.lifecycleState = "OBSERVE_PREPARE_FAILED_SAFE";
        this.lastLifecycleEvent = "observe-prepare-failed-safe";
        this.lastFailureReason = safeReason;
        this.lastPrepareTotalDurationMs = elapsedMs(this.prepareStartedNanos);
        this.prepareTimingReady = true;
        this.prepareReuseReady = false;
        this.lastPrepareReusedExistingResources = false;
        this.prepareTimingSummary = this.formatPrepareTimingSummary("failed");
        this.renderHookEarlyReturnWhenDisabled = true;
        this.renderHookEarlyReturnWhenStale = true;
    }

    private void failObserveEnableSafely(String reason, RuntimeException exception) {
        String safeReason = safeReason(reason);
        this.observeEnableRequested = false;
        this.observeEnableFailedSafely = true;
        this.observeEnableTimeoutReproduced = false;
        this.lastObserveEnableFailureReason = safeReason;
        this.lastObserveEnableExceptionClass = exception == null ? "none" : exception.getClass().getName();
        this.lastObserveEnableExceptionMessage = exception == null ? "none" : safeReason(exception.getMessage());
        this.visiblePreviewEnabled = false;
        this.observeModeEnabled = false;
        this.observeModeDebugTintUsed = false;
        this.observeModeCameraRelative = false;
        this.readbackPending = false;
        this.qaReadbackAllowed = false;
        this.lifecycleState = "OBSERVE_ENABLE_FAILED_SAFE";
        this.lastLifecycleEvent = "observe-enable-failed-safe";
        this.lastFailureReason = safeReason;
        this.renderHookEarlyReturnWhenDisabled = true;
        this.renderHookEarlyReturnWhenStale = true;
    }

    private void cancelObservePrepare(String reason) {
        this.observePrepareRequested = false;
        this.observePrepareInProgress = false;
        this.observePrepareCompleted = false;
        this.observePrepareFailedSafely = false;
        this.observePrepareAutoEnable = false;
        this.observeAutoEnabledAfterPrepare = false;
        this.observePrepareFrameBudgetExceeded = false;
        this.observePrepareStep = 0;
        this.observePrepareFrameCount = 0;
        this.observePrepareNextAttemptFrame = 0;
        this.lastObservePrepareStepName = "cancelled:" + safeReason(reason);
        this.lastObservePrepareFailureReason = "none";
        this.prepareReuseReady = false;
        this.lastPrepareReusedExistingResources = false;
        this.prepareBlockingStep = "cancelled";
        this.prepareBlockingReason = safeReason(reason);
        this.prepareTimingSummary = this.formatPrepareTimingSummary("cancelled");
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
                    ForgeFormalVisibleLodPreviewStats status = this.deferredObserveQa
                            ? this.observeEnable("qa-k10-visible-preview-observe-performance")
                            : this.enable("qa-k10-formal-visible-lod-preview");
                    if (status.visibleLodPreviewOwnerReady()) {
                        this.visiblePreviewWasEnabledDuringQa = true;
                        this.visiblePreviewDisabledAfterQa = false;
                        this.qaAutoDisablePending = true;
                        this.qaFramesRemaining = this.deferredObserveQa ? Math.max(QA_VISIBLE_FRAMES, 3) : QA_VISIBLE_FRAMES;
                        this.readbackPending = true;
                        this.qaReadbackAllowed = true;
                        this.firstDrawLogged = false;
                        this.lifecycleState = this.deferredObserveQa ? "VISIBLE_PREVIEW_OBSERVE_QA_ENABLED" : "VISIBLE_PREVIEW_QA_ENABLED";
                        this.lastLifecycleEvent = this.deferredObserveQa ? "observe-performance-qa-enable" : "qa-enable";
                        this.deferredQaPending = false;
                        this.deferredObserveQa = false;
                        this.instance.getFormalRendererManager().checkReadiness("qa-k10-formal-visible-lod-preview-enabled");
                        VoxyForge.LOGGER.info("Voxy K10 visible LoD preview QA enabled: {}", this.dump());
                    } else {
                        this.deferredQaPending = false;
                        this.deferredObserveQa = false;
                        this.audit();
                        this.instance.getFormalRendererManager().checkReadiness("qa-k10-formal-visible-lod-preview-enable-failed");
                        VoxyForge.LOGGER.info("Voxy K10 visible LoD preview QA enable failed: {}", this.dump());
                    }
                }
                default -> {
                    this.deferredQaPending = false;
                    this.deferredObserveQa = false;
                }
            }
        } catch (RuntimeException e) {
            this.deferredQaPending = false;
            this.deferredObserveQa = false;
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
        this.modelDataBufferId = store.modelDataBufferId();
        this.modelColourBufferId = store.modelColourBufferId();
        this.atlasTextureId = store.atlasTextureId();
        this.samplerId = store.samplerId();
        this.formalGeometryRecordCount = k8.formalGeometrySnapshotRecordCount();
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
                && this.visiblePreviewLightweightDrawPath
                && this.visiblePreviewDirectDrawUsed
                && !this.visiblePreviewIndirectCountDrawUsed
                && this.formalModelIdDecodeOk
                && this.faceDataLookupOk
                && this.atlasSampleOk
                && this.modelDataReadOk
                && this.modelColourReadOk
                && qaDisableRequirementMet
                && !this.perFrameRebuildDetected
                && !this.perFrameReadbackDetected
                && !this.perFrameShaderCompileDetected
                && !this.perFrameGlAllocationDetected
                && !this.perFrameLogSpamDetected
                && !this.duplicateHookRegistrationDetected
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
        this.shaderCompileRuns++;
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
            this.captureVisiblePreviewUniformLocations(program);
            if (this.uniformBlockModelAtlas >= 0) {
                GL20C.glUniform1i(this.uniformBlockModelAtlas, ForgeFormalShaderInputBindingLayout.BLOCK_MODEL_ATLAS_TEXTURE_UNIT);
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

    private void createValidationDrawBuffers(ForgeFormalCmdgenRealSectionDryRunStats k6, ForgeFormalModelIdSectionGeometryStats k8) {
        this.glAllocationRuns++;
        ForgeFormalModelIdSectionGeometryPath.PreviewRecord[] previewRecords = this.instance.getFormalModelIdSectionGeometryPath()
                .copyPreviewRecordsInterleavedDetailed(MULTI_SECTION_PREVIEW_MAX_RECORDS);
        if (previewRecords.length == 0) {
            throw new IllegalStateException("empty-multi-section-preview-records");
        }
        long[] previewRecordValues = copyPreviewRecordValues(previewRecords);
        int[] previewSectionMetadata = createPreviewSectionMetadata(previewRecords);
        int[] previewPositionScratch = createPreviewPositionScratch(previewRecords);
        this.geometryBufferId = GL45C.glCreateBuffers();
        this.previewSectionDataBufferId = GL45C.glCreateBuffers();
        this.previewSectionMetadataBufferId = this.previewSectionDataBufferId;
        this.geometryBufferOwnedByK10 = true;
        this.commandBufferId = GL45C.glCreateBuffers();
        this.drawCountBufferId = GL45C.glCreateBuffers();
        this.indexBufferId = GL45C.glCreateBuffers();
        this.positionScratchBufferId = GL45C.glCreateBuffers();
        this.vertexArrayId = GL30C.glGenVertexArrays();
        uploadLongs(this.geometryBufferId, previewRecordValues, GL15C.GL_STATIC_DRAW);
        uploadInts(this.previewSectionDataBufferId, previewSectionMetadata, GL15C.GL_STATIC_DRAW);
        uploadInts(this.commandBufferId, new int[] {
                this.k6FirstCommandCount,
                this.k6FirstCommandInstanceCount,
                this.k6FirstCommandFirstIndex,
                this.k6FirstCommandBaseVertex,
                this.k6FirstCommandBaseInstance
        }, GL15C.GL_STATIC_DRAW);
        uploadInts(this.drawCountBufferId, new int[] {0, 0, 0, 1}, GL15C.GL_STATIC_DRAW);
        int indexBufferCount = Math.max(0, this.k6FirstCommandFirstIndex) + Math.max(0, this.k6FirstCommandCount);
        uploadShorts(this.indexBufferId, createVoxyQuadIndexSequence(indexBufferCount), GL15C.GL_STATIC_DRAW);
        uploadInts(this.positionScratchBufferId, previewPositionScratch, GL15C.GL_STATIC_DRAW);
        this.acceptedDrawCommandCount = 1;
        this.drawCommandMatchesK6 = this.k6FirstCommandCount == k6.firstCommandCount()
                && this.k6FirstCommandInstanceCount == k6.firstCommandInstanceCount()
                && this.k6FirstCommandFirstIndex == k6.firstCommandFirstIndex()
                && this.k6FirstCommandBaseVertex == k6.firstCommandBaseVertex()
                && this.k6FirstCommandBaseInstance == k6.firstCommandBaseInstance();
        if (!this.drawCommandMatchesK6) {
            throw new IllegalStateException("k6-command-copy-mismatch");
        }
        this.formalGeometryRecordCount = previewRecords.length;
        this.multiSectionPreviewSectionCount = k8.formalGeometrySnapshotSectionCount();
        this.multiSectionPreviewInputRecordCount = k8.formalGeometrySnapshotRecordCount();
        this.multiSectionPreviewDrawRecordLimit = MULTI_SECTION_PREVIEW_MAX_RECORDS;
        this.multiSectionPreviewDrawRecordCount = Math.min(previewRecords.length, MULTI_SECTION_PREVIEW_MAX_RECORDS);
        this.multiSectionPreviewDrawCapped = k8.formalGeometrySnapshotRecordCount() > this.multiSectionPreviewDrawRecordCount;
        this.multiSectionPreviewSectionPositions = this.instance.getFormalModelIdSectionGeometryPath().previewSectionPositionsSummary();
        this.multiSectionPreviewModelIds = collectPreviewModelIds(previewRecordValues);
        this.multiSectionPreviewInputReady = k8.formalGeometrySnapshotSectionCount() > 1
                && k8.formalGeometrySnapshotRecordCount() > 1
                && k8.realSectionInputUsed();
        this.multiSectionFormalGeometryUsed = this.geometryBufferId != 0
                && this.geometryBufferOwnedByK10
                && previewRecords.length > 1
                && this.k8FormalGeometryUsed
                && !this.syntheticDrawFixtureUsed;
        this.multiSectionPreviewBudgetReady = previewRecords.length <= MULTI_SECTION_PREVIEW_MAX_RECORDS
                && this.multiSectionPreviewDrawRecordLimit == MULTI_SECTION_PREVIEW_MAX_RECORDS;
        this.singleSectionFallbackUsed = k8.formalGeometrySnapshotSectionCount() <= 1;
        this.multiSectionSyntheticFallbackUsed = this.syntheticDrawFixtureUsed;
        this.previewSectionSidecarBufferCreated = false;
        this.worldPlacedPreviewRecordCount = previewRecords.length;
        this.worldPlacedPreviewSectionBases = formatPreviewSectionBases(previewRecords);
        this.packedQuadLocalPositionUsed = true;
        this.sectionWorldBaseUsed = true;
        this.sectionLodScaleUsed = true;
        this.cameraBillboardFallbackUsed = false;
        this.previewSectionMetadataBufferCreated = this.previewSectionMetadataBufferId != 0;
        this.previewSectionMetadataRecordCount = previewRecords.length;
        this.positionScratchEntryCount = previewRecords.length;
        this.previewSectionMetadataRawPositions = formatPreviewSectionRawPositions(previewRecords);
        this.originalSectionMetadataLayoutUsed = true;
        this.cmdgenPositionScratchSemanticsUsed = true;
        this.previewSectionSidecarFallbackUsed = false;
        this.sectionMetadataPathUsed = this.previewSectionMetadataBufferCreated
                && this.previewSectionMetadataRecordCount == previewRecords.length
                && this.originalSectionMetadataLayoutUsed;
        this.positionScratchPathUsed = this.positionScratchBufferId != 0
                && this.positionScratchEntryCount == previewRecords.length
                && this.cmdgenPositionScratchSemanticsUsed;
        this.sectionMetadataPreviewReady = this.sectionMetadataPathUsed
                && this.positionScratchPathUsed
                && !this.previewSectionSidecarFallbackUsed;
        this.worldPlacedPreviewUsed = this.previewSectionSidecarBufferCreated
                || this.sectionMetadataPreviewReady;
        this.worldPlacedPreviewUsed = this.worldPlacedPreviewUsed
                && this.packedQuadLocalPositionUsed
                && this.sectionWorldBaseUsed
                && this.sectionLodScaleUsed
                && !this.cameraBillboardFallbackUsed;
        this.worldPlacedPreviewReady = this.worldPlacedPreviewUsed
                && this.multiSectionFormalGeometryUsed
                && this.worldPlacedPreviewRecordCount > 0;
    }

    private void executeVisiblePreviewDraw(
            RenderLevelStageEvent event,
            Minecraft minecraft
    ) {
        int oldProgram = GL11C.glGetInteger(GL20C.GL_CURRENT_PROGRAM);
        int oldVertexArray = GL11C.glGetInteger(GL30C.GL_VERTEX_ARRAY_BINDING);
        int oldElementArray = GL11C.glGetInteger(GL15C.GL_ELEMENT_ARRAY_BUFFER_BINDING);
        int oldGeometryBuffer = GL30C.glGetIntegeri(GL43C.GL_SHADER_STORAGE_BUFFER_BINDING, ForgeFormalShaderInputBindingLayout.QUAD_BUFFER_BINDING_INDEX);
        int oldModelBuffer = GL30C.glGetIntegeri(GL43C.GL_SHADER_STORAGE_BUFFER_BINDING, ForgeFormalShaderInputBindingLayout.MODEL_DATA_BINDING_INDEX);
        int oldColourBuffer = GL30C.glGetIntegeri(GL43C.GL_SHADER_STORAGE_BUFFER_BINDING, ForgeFormalShaderInputBindingLayout.MODEL_COLOUR_BINDING_INDEX);
        int oldPositionScratch = GL30C.glGetIntegeri(GL43C.GL_SHADER_STORAGE_BUFFER_BINDING, ForgeFormalShaderInputBindingLayout.POSITION_SCRATCH_BINDING_INDEX);
        int oldPreviewSectionData = GL30C.glGetIntegeri(GL43C.GL_SHADER_STORAGE_BUFFER_BINDING, PREVIEW_SECTION_DATA_BINDING_INDEX);
        int oldActiveTexture = GL11C.glGetInteger(GL13C.GL_ACTIVE_TEXTURE);
        GL13C.glActiveTexture(GL13C.GL_TEXTURE0 + ForgeFormalShaderInputBindingLayout.BLOCK_MODEL_ATLAS_TEXTURE_UNIT);
        int oldTexture = GL11C.glGetInteger(GL11C.GL_TEXTURE_BINDING_2D);
        int oldSampler = GL30C.glGetIntegeri(GL33C.GL_SAMPLER_BINDING, ForgeFormalShaderInputBindingLayout.BLOCK_MODEL_ATLAS_TEXTURE_UNIT);
        boolean depthEnabled = GL11C.glIsEnabled(GL11C.GL_DEPTH_TEST);
        boolean blendEnabled = GL11C.glIsEnabled(GL11C.GL_BLEND);
        boolean cullEnabled = GL11C.glIsEnabled(GL11C.GL_CULL_FACE);
        boolean depthMask = GL11C.glGetBoolean(GL11C.GL_DEPTH_WRITEMASK);
        try {
            boolean validationChecksThisFrame = this.qaReadbackAllowed || !this.visiblePreviewDrawCallOk;
            if (validationChecksThisFrame) {
                clearGlErrors();
            }
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
            float previewScale = this.observeModeEnabled ? this.observeModeScale : NORMAL_PREVIEW_SCALE;
            double previewDistance = this.observeModeEnabled ? OBSERVE_PREVIEW_DISTANCE : NORMAL_PREVIEW_DISTANCE;
            Vec3 center = cameraPos.add(look.scale(previewDistance)).add(0.0D, this.observeModeEnabled ? 0.8D : 0.25D, 0.0D);
            this.previewCameraDistance = center.distanceTo(cameraPos);
            this.observeModeCameraRelative = this.observeModeEnabled;
            this.previewWorldBounds = formatPreviewBounds(center, previewScale);

            poseStack.pushPose();
            try {
                poseStack.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);
                Matrix4f modelView = poseStack.last().pose();
                Matrix4f projection = event.getProjectionMatrix();
                GL20C.glUseProgram(this.visiblePreviewShaderProgramId);
                uniformMatrix4f(this.uniformModelViewMatrix, modelView);
                uniformMatrix4f(this.uniformProjectionMatrix, projection);
                uniform3f(this.uniformPreviewCenter, center);
                uniform3f(this.uniformPreviewRight, right);
                uniform3f(this.uniformPreviewUp, up);
                uniform1f(this.uniformPreviewScale, previewScale);
                uniform1ui(this.uniformObserveMode, this.observeModeEnabled ? 1 : 0);
                uniform3f(this.uniformObserveTint, 0.18F, 1.0F, 0.25F);
                uniform1ui(this.uniformRecordCount, Math.max(1, this.formalGeometryRecordCount));
                uniform1ui(this.uniformBaseVertexBias, Math.max(0, this.k6FirstCommandBaseVertex));

                GL30C.glBindBufferBase(GL43C.GL_SHADER_STORAGE_BUFFER, ForgeFormalShaderInputBindingLayout.QUAD_BUFFER_BINDING_INDEX, this.geometryBufferId);
                GL30C.glBindBufferBase(GL43C.GL_SHADER_STORAGE_BUFFER, ForgeFormalShaderInputBindingLayout.MODEL_DATA_BINDING_INDEX, this.modelDataBufferId);
                GL30C.glBindBufferBase(GL43C.GL_SHADER_STORAGE_BUFFER, ForgeFormalShaderInputBindingLayout.MODEL_COLOUR_BINDING_INDEX, this.modelColourBufferId);
                GL30C.glBindBufferBase(GL43C.GL_SHADER_STORAGE_BUFFER, ForgeFormalShaderInputBindingLayout.POSITION_SCRATCH_BINDING_INDEX, this.positionScratchBufferId);
                GL30C.glBindBufferBase(GL43C.GL_SHADER_STORAGE_BUFFER, PREVIEW_SECTION_DATA_BINDING_INDEX, this.previewSectionDataBufferId);
                GL11C.glBindTexture(GL11C.GL_TEXTURE_2D, this.atlasTextureId);
                GL33C.glBindSampler(ForgeFormalShaderInputBindingLayout.BLOCK_MODEL_ATLAS_TEXTURE_UNIT, this.samplerId);

                if (validationChecksThisFrame) {
                    boolean bindingsOk = GL30C.glGetIntegeri(GL43C.GL_SHADER_STORAGE_BUFFER_BINDING, ForgeFormalShaderInputBindingLayout.QUAD_BUFFER_BINDING_INDEX) == this.geometryBufferId
                            && GL30C.glGetIntegeri(GL43C.GL_SHADER_STORAGE_BUFFER_BINDING, ForgeFormalShaderInputBindingLayout.MODEL_DATA_BINDING_INDEX) == this.modelDataBufferId
                            && GL30C.glGetIntegeri(GL43C.GL_SHADER_STORAGE_BUFFER_BINDING, ForgeFormalShaderInputBindingLayout.MODEL_COLOUR_BINDING_INDEX) == this.modelColourBufferId
                            && GL30C.glGetIntegeri(GL43C.GL_SHADER_STORAGE_BUFFER_BINDING, ForgeFormalShaderInputBindingLayout.POSITION_SCRATCH_BINDING_INDEX) == this.positionScratchBufferId
                            && GL30C.glGetIntegeri(GL43C.GL_SHADER_STORAGE_BUFFER_BINDING, PREVIEW_SECTION_DATA_BINDING_INDEX) == this.previewSectionDataBufferId
                            && GL11C.glGetInteger(GL11C.GL_TEXTURE_BINDING_2D) == this.atlasTextureId
                            && GL30C.glGetIntegeri(GL33C.GL_SAMPLER_BINDING, ForgeFormalShaderInputBindingLayout.BLOCK_MODEL_ATLAS_TEXTURE_UNIT) == this.samplerId;
                    if (!bindingsOk) {
                        throw new IllegalStateException("k10-formal-resource-binding-failed");
                    }
                    this.visiblePreviewValidationChecksOnly = true;
                }

                if (validationChecksThisFrame) {
                    GL43C.glMemoryBarrier(GL43C.GL_SHADER_STORAGE_BARRIER_BIT | GL43C.GL_TEXTURE_FETCH_BARRIER_BIT);
                }
                int commandIndexCount = Math.max(0, this.k6FirstCommandCount);
                int recordIndexCap = Math.max(1, this.multiSectionPreviewDrawRecordCount) * 6;
                int drawIndexCap = Math.min(OBSERVE_MAX_DRAW_INDICES, recordIndexCap);
                int drawIndexCount = Math.min(commandIndexCount, drawIndexCap);
                this.visiblePreviewCommandIndexCount = commandIndexCount;
                this.visiblePreviewDrawIndexCount = drawIndexCount;
                this.visiblePreviewDrawIndexCap = drawIndexCap;
                this.visiblePreviewDrawCountCapped = drawIndexCount < commandIndexCount;
                GL11C.glDrawElements(
                        GL11C.GL_TRIANGLES,
                        drawIndexCount,
                        GL11C.GL_UNSIGNED_SHORT,
                        (long) Math.max(0, this.k6FirstCommandFirstIndex) * Short.BYTES
                );
                this.visiblePreviewLightweightDrawPath = true;
                this.visiblePreviewDirectDrawUsed = true;
                this.visiblePreviewDrawArraysUsed = false;
                this.visiblePreviewIndirectCountDrawUsed = false;
                this.worldSpacePreview = true;
                this.previewCameraRelativeTransformOk = true;
                this.projectionMatrixUsed = true;
                this.modelViewMatrixUsed = true;
                this.visiblePreviewDrawExecuted = true;
                this.minecraftMainFramebufferDrawn = true;
                this.visiblePreviewDrawCallOk = true;
                if (validationChecksThisFrame) {
                    int error = GL11C.glGetError();
                    this.lastGlError = error == GL11C.GL_NO_ERROR ? "none" : glErrorName(error);
                    if (error != GL11C.GL_NO_ERROR) {
                        throw new IllegalStateException("k10-visible-preview-draw-gl-error-" + this.lastGlError);
                    }
                }
                if (this.readbackPending && this.qaReadbackAllowed) {
                    this.readbackPending = false;
                    this.readbackMainFramebufferSample();
                }
            } finally {
                poseStack.popPose();
            }
        } finally {
            GL20C.glUseProgram(oldProgram);
            GL30C.glBindVertexArray(oldVertexArray);
            GL15C.glBindBuffer(GL15C.GL_ELEMENT_ARRAY_BUFFER, oldElementArray);
            GL30C.glBindBufferBase(GL43C.GL_SHADER_STORAGE_BUFFER, ForgeFormalShaderInputBindingLayout.QUAD_BUFFER_BINDING_INDEX, oldGeometryBuffer);
            GL30C.glBindBufferBase(GL43C.GL_SHADER_STORAGE_BUFFER, ForgeFormalShaderInputBindingLayout.MODEL_DATA_BINDING_INDEX, oldModelBuffer);
            GL30C.glBindBufferBase(GL43C.GL_SHADER_STORAGE_BUFFER, ForgeFormalShaderInputBindingLayout.MODEL_COLOUR_BINDING_INDEX, oldColourBuffer);
            GL30C.glBindBufferBase(GL43C.GL_SHADER_STORAGE_BUFFER, ForgeFormalShaderInputBindingLayout.POSITION_SCRATCH_BINDING_INDEX, oldPositionScratch);
            GL30C.glBindBufferBase(GL43C.GL_SHADER_STORAGE_BUFFER, PREVIEW_SECTION_DATA_BINDING_INDEX, oldPreviewSectionData);
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
        this.readbackRuns++;
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
        this.observeEnableRequested = false;
        this.observeEnableFailedSafely = false;
        this.cancelObservePrepare(reason);
        this.resetBuildFlags();
        this.renderHookEarlyReturnWhenStale = true;
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
        if (this.geometryBufferOwnedByK10 && this.geometryBufferId != 0) {
            GL15C.glDeleteBuffers(this.geometryBufferId);
        }
        if (this.previewSectionDataBufferId != 0) {
            GL15C.glDeleteBuffers(this.previewSectionDataBufferId);
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
        this.geometryBufferOwnedByK10 = false;
        this.previewSectionDataBufferId = 0;
        this.previewSectionMetadataBufferId = 0;
        this.indexBufferId = 0;
        this.commandBufferId = 0;
        this.drawCountBufferId = 0;
        this.positionScratchBufferId = 0;
        this.readbackPending = false;
        this.qaReadbackAllowed = false;
        this.resetVisiblePreviewUniformLocations();
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
        this.readbackPending = false;
        this.qaReadbackAllowed = false;
        this.firstDrawLogged = false;
        this.observeModeEnabled = false;
        this.observeDrawPaused = false;
        this.observeModeDebugTintUsed = false;
        this.observeModeScale = OBSERVE_PREVIEW_SCALE;
        this.observeModeCameraRelative = false;
        this.observeEnableRequested = false;
        this.observeEnableHandledOnRenderThread = false;
        this.observeEnableCommandReturnedQuickly = false;
        this.observeEnableCommandDurationMillis = 0L;
        this.observeEnableDidGlWorkOnCommandThread = false;
        this.observeEnableDidReadbackOnCommandThread = false;
        this.observeEnableDidSynchronousRebuild = false;
        this.observeEnableFailedSafely = false;
        this.observeEnableTimeoutReproduced = false;
        this.lastObserveEnableFailureReason = "none";
        this.lastObserveEnableExceptionClass = "none";
        this.lastObserveEnableExceptionMessage = "none";
        this.previewWorldBounds = "none";
        this.previewCameraDistance = 0.0D;
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
        this.visiblePreviewLightweightDrawPath = true;
        this.visiblePreviewDirectDrawUsed = false;
        this.visiblePreviewDrawArraysUsed = false;
        this.visiblePreviewIndirectCountDrawUsed = false;
        this.visiblePreviewValidationChecksOnly = false;
        this.visiblePreviewCommandIndexCount = 0;
        this.visiblePreviewDrawIndexCount = 0;
        this.visiblePreviewDrawIndexCap = OBSERVE_MAX_DRAW_INDICES;
        this.visiblePreviewDrawCountCapped = false;
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
        this.modelDataBufferId = 0;
        this.modelColourBufferId = 0;
        this.atlasTextureId = 0;
        this.samplerId = 0;
        this.formalGeometryRecordCount = 0;
        this.geometryBufferOwnedByK10 = false;
        this.multiSectionPreviewPipelineReady = false;
        this.multiSectionPreviewInputReady = false;
        this.multiSectionFormalGeometryUsed = false;
        this.multiSectionPreviewBudgetReady = false;
        this.multiSectionPreviewSectionCount = 0;
        this.multiSectionPreviewInputRecordCount = 0;
        this.multiSectionPreviewDrawRecordLimit = MULTI_SECTION_PREVIEW_MAX_RECORDS;
        this.multiSectionPreviewDrawRecordCount = 0;
        this.multiSectionPreviewDrawCapped = false;
        this.multiSectionPreviewSectionPositions = "none";
        this.multiSectionPreviewModelIds = "none";
        this.singleSectionFallbackUsed = false;
        this.multiSectionSyntheticFallbackUsed = false;
        this.worldPlacedPreviewReady = false;
        this.worldPlacedPreviewUsed = false;
        this.packedQuadLocalPositionUsed = false;
        this.sectionWorldBaseUsed = false;
        this.sectionLodScaleUsed = false;
        this.previewSectionSidecarBufferCreated = false;
        this.previewSectionDataBufferId = 0;
        this.worldPlacedPreviewRecordCount = 0;
        this.worldPlacedPreviewSectionBases = "none";
        this.cameraBillboardFallbackUsed = false;
        this.sectionMetadataPreviewReady = false;
        this.sectionMetadataPathUsed = false;
        this.positionScratchPathUsed = false;
        this.previewSectionMetadataBufferCreated = false;
        this.previewSectionMetadataBufferId = 0;
        this.previewSectionMetadataRecordCount = 0;
        this.positionScratchEntryCount = 0;
        this.previewSectionMetadataRawPositions = "none";
        this.originalSectionMetadataLayoutUsed = false;
        this.cmdgenPositionScratchSemanticsUsed = false;
        this.previewSectionSidecarFallbackUsed = false;
        this.k6FirstCommandCount = 0;
        this.k6FirstCommandInstanceCount = 0;
        this.k6FirstCommandFirstIndex = 0;
        this.k6FirstCommandBaseVertex = 0;
        this.k6FirstCommandBaseInstance = 0;
        this.acceptedDrawCommandCount = 0;
        this.drawCommandMatchesK6 = false;
        this.perFrameRebuildDetected = false;
        this.perFrameReadbackDetected = false;
        this.perFrameShaderCompileDetected = false;
        this.perFrameGlAllocationDetected = false;
        this.perFrameLogSpamDetected = false;
        this.lastFrameDrawTimeNanos = 0L;
        this.totalFrameDrawTimeNanos = 0L;
        this.maxFrameDrawTimeNanos = 0L;
        this.renderHookEarlyReturnWhenDisabled = false;
        this.renderHookEarlyReturnWhenStale = false;
        this.lastGlError = "none";
    }

    private void fail(String reason) {
        this.ownerReady = false;
        this.lifecycleState = "FAILED";
        this.lastFailureReason = safeReason(reason);
    }

    private static short[] createVoxyQuadIndexSequence(int indexCount) {
        int quadCount = Math.max(1, (indexCount + 5) / 6);
        short[] indices = new short[quadCount * 6];
        int ptr = 0;
        for (int base = 0; base < quadCount * 4; base += 4) {
            indices[ptr++] = (short) (base + 1);
            indices[ptr++] = (short) (base + 2);
            indices[ptr++] = (short) base;
            indices[ptr++] = (short) (base + 1);
            indices[ptr++] = (short) (base + 3);
            indices[ptr++] = (short) (base + 2);
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

    private static void uploadLongs(int bufferId, long[] values, int usage) {
        long ptr = MemoryUtil.nmemAlloc((long) values.length * Long.BYTES);
        try {
            for (int i = 0; i < values.length; i++) {
                MemoryUtil.memPutLong(ptr + ((long) i * Long.BYTES), values[i]);
            }
            GL45C.nglNamedBufferData(bufferId, (long) values.length * Long.BYTES, ptr, usage);
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

    private static long[] copyPreviewRecordValues(ForgeFormalModelIdSectionGeometryPath.PreviewRecord[] records) {
        long[] values = new long[records.length];
        for (int i = 0; i < records.length; i++) {
            values[i] = records[i].record();
        }
        return values;
    }

    private static int[] createPreviewSectionMetadata(ForgeFormalModelIdSectionGeometryPath.PreviewRecord[] records) {
        int[] values = new int[records.length * 8];
        for (int i = 0; i < records.length; i++) {
            int[] raw = rawSectionPositionWords(records[i].sectionPosition());
            int base = i * 8;
            values[base] = raw[0];
            values[base + 1] = raw[1];
            values[base + 2] = 0;
            values[base + 3] = i;
            values[base + 4] = 0;
            values[base + 5] = 1 << 16;
            values[base + 6] = 0;
            values[base + 7] = 0;
        }
        return values;
    }

    private static int[] createPreviewPositionScratch(ForgeFormalModelIdSectionGeometryPath.PreviewRecord[] records) {
        int[] values = new int[records.length * 2];
        for (int i = 0; i < records.length; i++) {
            int[] raw = rawSectionPositionWords(records[i].sectionPosition());
            values[i * 2] = raw[0];
            values[i * 2 + 1] = raw[1];
        }
        return values;
    }

    private static int[] rawSectionPositionWords(long position) {
        return new int[] {(int) (position >>> 32), (int) position};
    }

    private static String formatPreviewSectionBases(ForgeFormalModelIdSectionGeometryPath.PreviewRecord[] records) {
        return java.util.Arrays.stream(records)
                .map(record -> {
                    long position = record.sectionPosition();
                    int level = Math.max(0, WorldEngine.getLevel(position));
                    int scale = 1 << Math.min(level, 12);
                    int x = Math.round(WorldEngine.getX(position) * 32.0F * scale);
                    int y = Math.round(WorldEngine.getY(position) * 32.0F * scale);
                    int z = Math.round(WorldEngine.getZ(position) * 32.0F * scale);
                    return x + "," + y + "," + z + "@" + scale;
                })
                .distinct()
                .limit(8)
                .collect(Collectors.joining("|"));
    }

    private static String formatPreviewSectionRawPositions(ForgeFormalModelIdSectionGeometryPath.PreviewRecord[] records) {
        return java.util.Arrays.stream(records)
                .map(record -> {
                    int[] raw = rawSectionPositionWords(record.sectionPosition());
                    return Integer.toUnsignedString(raw[0]) + ":" + Integer.toUnsignedString(raw[1]);
                })
                .distinct()
                .limit(8)
                .collect(Collectors.joining("|"));
    }

    private static String collectPreviewModelIds(long[] records) {
        return java.util.Arrays.stream(records)
                .mapToInt(ForgeVoxyQuadEncoder::extractModelId)
                .distinct()
                .limit(16)
                .mapToObj(String::valueOf)
                .collect(Collectors.joining(","));
    }

    private void captureVisiblePreviewUniformLocations(int program) {
        this.uniformModelViewMatrix = GL20C.glGetUniformLocation(program, "modelViewMatrix");
        this.uniformProjectionMatrix = GL20C.glGetUniformLocation(program, "projectionMatrix");
        this.uniformPreviewCenter = GL20C.glGetUniformLocation(program, "previewCenter");
        this.uniformPreviewRight = GL20C.glGetUniformLocation(program, "previewRight");
        this.uniformPreviewUp = GL20C.glGetUniformLocation(program, "previewUp");
        this.uniformPreviewScale = GL20C.glGetUniformLocation(program, "previewScale");
        this.uniformObserveMode = GL20C.glGetUniformLocation(program, "observeMode");
        this.uniformObserveTint = GL20C.glGetUniformLocation(program, "observeTint");
        this.uniformRecordCount = GL20C.glGetUniformLocation(program, "recordCount");
        this.uniformBaseVertexBias = GL20C.glGetUniformLocation(program, "baseVertexBias");
        this.uniformBlockModelAtlas = GL20C.glGetUniformLocation(program, "blockModelAtlas");
    }

    private void resetVisiblePreviewUniformLocations() {
        this.uniformModelViewMatrix = -1;
        this.uniformProjectionMatrix = -1;
        this.uniformPreviewCenter = -1;
        this.uniformPreviewRight = -1;
        this.uniformPreviewUp = -1;
        this.uniformPreviewScale = -1;
        this.uniformObserveMode = -1;
        this.uniformObserveTint = -1;
        this.uniformRecordCount = -1;
        this.uniformBaseVertexBias = -1;
        this.uniformBlockModelAtlas = -1;
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

    private static void uniformMatrix4f(int location, Matrix4f matrix) {
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

    private static void uniform3f(int location, Vec3 value) {
        if (location >= 0) {
            GL20C.glUniform3f(location, (float) value.x, (float) value.y, (float) value.z);
        }
    }

    private static void uniform3f(int program, String name, float x, float y, float z) {
        int location = GL20C.glGetUniformLocation(program, name);
        if (location >= 0) {
            GL20C.glUniform3f(location, x, y, z);
        }
    }

    private static void uniform3f(int location, float x, float y, float z) {
        if (location >= 0) {
            GL20C.glUniform3f(location, x, y, z);
        }
    }

    private static void uniform1f(int program, String name, float value) {
        int location = GL20C.glGetUniformLocation(program, name);
        if (location >= 0) {
            GL20C.glUniform1f(location, value);
        }
    }

    private static void uniform1f(int location, float value) {
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

    private static void uniform1ui(int location, int value) {
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

    private static String formatPreviewBounds(Vec3 center, float scale) {
        double radius = Math.max(0.25D, scale * 2.25D);
        return String.format(
                "min=(%.2f,%.2f,%.2f),max=(%.2f,%.2f,%.2f)",
                center.x - radius,
                center.y - radius,
                center.z - radius,
                center.x + radius,
                center.y + radius,
                center.z + radius
        ).replace(' ', '_');
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
