package me.cortex.voxy.forge;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

final class ForgeFormalCommandGenerationOwner {
    static final String STAGE = "K3_FORMAL_COMMAND_GENERATION_OWNERSHIP_SKELETON";
    private static final int DRAW_COMMAND_FIELD_COUNT = 5;
    private static final int DRAW_COMMAND_STRIDE_BYTES = DRAW_COMMAND_FIELD_COUNT * Integer.BYTES;
    private static final int DRAW_COUNT_BUFFER_STRIDE_BYTES = Integer.BYTES;
    private static final int DRAW_COUNT_BUFFER_HEADER_WORDS = 6;
    private static final int DRAW_COUNT_BUFFER_LAYOUT_BYTES = (DRAW_COUNT_BUFFER_HEADER_WORDS + DRAW_COMMAND_FIELD_COUNT) * Integer.BYTES;
    private static final int DRAW_BUFFER_BINDING = 1;
    private static final int DRAW_COUNT_BUFFER_BINDING = 2;
    private static final int SECTION_METADATA_BUFFER_BINDING = 3;
    private static final int VISIBILITY_BUFFER_BINDING = 4;
    private static final int INDIRECT_LOOKUP_BUFFER_BINDING = 5;
    private static final int POSITION_SCRATCH_BUFFER_BINDING = 6;
    private static final int TRANSLUCENT_DISTANCE_BUFFER_BINDING = 7;
    private static final String DEFERRED_REASON = "K3-no-draw-command-generation-contract-only";
    private static final List<ForgeFormalRendererBlocker> BLOCKERS = List.of(
            new ForgeFormalRendererBlocker("P0", "P0_FORMAL_COMMAND_GENERATION_GPU_PROGRAM_MISSING", "Formal command generation GPU program missing", "K3 defines the cmdgen contract but does not compile or dispatch the formal command-generation shader.", "Add a formal command-generation GPU program after visibility traversal and geometry id ownership are ready.", true),
            new ForgeFormalRendererBlocker("P0", "P0_FORMAL_VISIBILITY_TRAVERSAL_MISSING", "Formal visibility traversal missing", "K3 requires visibility input, but no formal traversal fills it.", "Add formal visibility and LOD traversal ownership.", true),
            new ForgeFormalRendererBlocker("P0", "P0_GLOBAL_FORMAL_MODEL_ID_GEOMETRY_MISSING", "Global formal model-id geometry missing", "Live geometry is not globally encoded with formal model ids.", "Move formal model ids into the formal BuiltSection/RenderDataFactory path.", true),
            new ForgeFormalRendererBlocker("P0", "P0_FORMAL_TERRAIN_SHADER_INTEGRATION_MISSING", "Formal terrain shader integration missing", "K3 produces only a no-draw contract and does not bind a terrain shader.", "Integrate the formal terrain shader after command generation can produce valid commands.", true),
            new ForgeFormalRendererBlocker("P0", "P0_FORMAL_MDIC_DRAW_DISABLED", "Formal MDIC draw disabled", "K3 does not call glMultiDrawElementsIndirectCountARB.", "Keep draw disabled until formal command generation, visibility, and shader integration are ready.", true),
            new ForgeFormalRendererBlocker("P1", "P1_LIGHTMAP_MISSING", "Lightmap missing", "Formal lightmap input remains incomplete.", "Add formal lightmap binding and semantics.", false),
            new ForgeFormalRendererBlocker("P1", "P1_BIOME_TINT_MISSING", "Biome tint missing", "Formal biome tint/modelColour semantics remain incomplete.", "Complete formal biome tint handling.", false),
            new ForgeFormalRendererBlocker("P1", "P1_MATERIAL_ALPHA_SEMANTICS_MISSING", "Material alpha semantics missing", "Formal material, alpha, and cutout semantics remain incomplete.", "Implement formal material/alpha/cutout behavior.", false),
            new ForgeFormalRendererBlocker("P1", "P1_RESOURCE_REBUILD_AUTOMATION_INCOMPLETE", "Resource rebuild automation incomplete", "K3 stales the owner but does not rebuild command-generation resources automatically.", "Add rebuild orchestration once the command-generation program exists.", false)
    );

    private final ForgeVoxyInstance instance;
    private boolean enabled;
    private ForgeFormalCommandGenerationLifecycleState lifecycleState = ForgeFormalCommandGenerationLifecycleState.DISABLED;
    private long checkRuns;
    private long auditRuns;
    private long clearRuns;
    private long lifecycleGeneration;
    private boolean stale;
    private boolean requiresRebuild;
    private String lastLifecycleEvent = "initialized";
    private String lastFailureReason = "none";
    private boolean resourceReloadSeen;
    private boolean worldUnloadSeen;
    private boolean dimensionSwitchSeen;
    private boolean debugPipelineClearSeen;
    private boolean presetOffSeen;
    private boolean presetClearSeen;
    private ForgeFormalCommandGenerationAuditResult lastAudit = ForgeFormalCommandGenerationAuditResult.failure("not-audited");

    ForgeFormalCommandGenerationOwner(ForgeVoxyInstance instance) {
        this.instance = instance;
    }

    ForgeFormalCommandGenerationStats enable(String reason) {
        this.enabled = true;
        this.stale = false;
        this.requiresRebuild = false;
        this.lifecycleState = ForgeFormalCommandGenerationLifecycleState.ENABLED_NO_DRAW;
        this.lastLifecycleEvent = "enable:" + safeReason(reason);
        this.lastFailureReason = "none";
        this.lifecycleGeneration++;
        return this.createStatusSnapshot();
    }

    ForgeFormalCommandGenerationStats check(String reason) {
        this.checkRuns++;
        if (this.stale) {
            this.lifecycleState = ForgeFormalCommandGenerationLifecycleState.STALE;
        } else if (this.enabled) {
            this.lifecycleState = ForgeFormalCommandGenerationLifecycleState.ENABLED_NO_DRAW;
        } else if (this.lifecycleState != ForgeFormalCommandGenerationLifecycleState.CLEARED) {
            this.lifecycleState = ForgeFormalCommandGenerationLifecycleState.DISABLED;
        }
        this.lastLifecycleEvent = "check:" + safeReason(reason) + "@" + Instant.now();
        return this.createStatusSnapshot();
    }

    ForgeFormalCommandGenerationAuditResult audit() {
        this.auditRuns++;
        ForgeFormalCommandGenerationStats status = this.createStatusSnapshot();
        String error = "none";
        if (!status.formalTerrainRendererOwnerReady()) {
            error = "k1-owner-not-ready";
        } else if (!status.formalViewportOwnerReady()) {
            error = "k2-viewport-owner-not-ready";
        } else if (!status.formalCommandBufferOwnerReady()) {
            error = "formal-command-buffer-owner-not-ready";
        } else if (!status.formalCommandGenerationOwnerReady()) {
            error = "formal-command-generation-owner-not-ready";
        } else if (!status.originalVoxyCmdgenAlignmentChecked()) {
            error = "original-cmdgen-not-inspected";
        } else if (!status.originalVoxyBindingsAlignmentChecked()) {
            error = "original-bindings-not-inspected";
        } else if (!status.originalVoxyMdicViewportAlignmentChecked()) {
            error = "original-mdic-viewport-not-inspected";
        } else if (!status.originalVoxyMdicSectionRendererAlignmentChecked()) {
            error = "original-mdic-section-renderer-not-inspected";
        } else if (status.debugMdicCommandBuffersUsedAsFormal()) {
            error = "debug-mdic-command-buffers-used-as-formal";
        } else if (!status.commandGenerationInputsKnown()) {
            error = "command-generation-input-contract-unknown";
        } else if (!status.commandGenerationOutputsKnown()) {
            error = "command-generation-output-contract-unknown";
        } else if (!status.formalDrawCommandLayoutReady()) {
            error = "draw-command-layout-not-ready";
        } else if (!status.formalDrawCountLayoutReady()) {
            error = "draw-count-layout-not-ready";
        } else if (status.cmdgenComputeShaderRun()) {
            error = "cmdgen-compute-shader-run";
        } else if (status.glDispatchComputeIndirectCalled()) {
            error = "dispatch-compute-indirect-called";
        } else if (status.glMultiDrawElementsIndirectCountCalled()) {
            error = "multi-draw-indirect-count-called";
        } else if (status.mdicSectionRendererCalled()) {
            error = "mdic-section-renderer-called";
        } else if (status.voxyRenderSystemCalled()) {
            error = "voxy-render-system-called";
        } else if (status.formalDrawPipelineReady()) {
            error = "formal-draw-pipeline-ready-should-remain-false";
        } else if (status.formalRendererReady()) {
            error = "formal-renderer-ready-should-remain-false";
        } else if (status.actualRendererDrawEnabled()) {
            error = "actual-renderer-draw-enabled";
        }
        boolean success = "none".equals(error);
        this.lastFailureReason = success ? "none" : error;
        this.lastAudit = new ForgeFormalCommandGenerationAuditResult(
                success,
                error,
                status.formalTerrainRendererOwnerReady(),
                status.formalViewportOwnerReady(),
                status.formalCommandGenerationOwnerReady(),
                status.originalVoxyCmdgenAlignmentChecked(),
                status.originalVoxyBindingsAlignmentChecked(),
                status.originalVoxyMdicViewportAlignmentChecked(),
                status.originalVoxyMdicSectionRendererAlignmentChecked(),
                status.debugMdicCommandBuffersUsedAsFormal(),
                status.commandGenerationInputsKnown(),
                status.commandGenerationOutputsKnown(),
                status.formalDrawCommandLayoutReady(),
                status.formalDrawCountLayoutReady(),
                status.cmdgenComputeShaderRun(),
                status.glMultiDrawElementsIndirectCountCalled(),
                status.mdicSectionRendererCalled(),
                status.voxyRenderSystemCalled(),
                status.formalDrawPipelineReady(),
                status.formalRendererReady(),
                status.actualRendererDrawEnabled()
        );
        return this.lastAudit;
    }

    ForgeFormalCommandGenerationAuditResult createAuditStatusSnapshot() {
        return this.lastAudit;
    }

    ForgeFormalCommandGenerationStats clear(String reason) {
        this.clearRuns++;
        this.enabled = false;
        this.stale = false;
        this.requiresRebuild = false;
        this.lifecycleState = ForgeFormalCommandGenerationLifecycleState.CLEARED;
        this.lastLifecycleEvent = "clear:" + safeReason(reason);
        this.lastFailureReason = "none";
        this.resourceReloadSeen = false;
        this.worldUnloadSeen = false;
        this.dimensionSwitchSeen = false;
        this.debugPipelineClearSeen = false;
        this.presetOffSeen = false;
        this.presetClearSeen = false;
        this.lifecycleGeneration++;
        this.lastAudit = ForgeFormalCommandGenerationAuditResult.failure("cleared");
        return this.createStatusSnapshot();
    }

    boolean isOwnerShellReady() {
        return this.enabled
                && drawCommandLayoutReady()
                && drawCountLayoutReady()
                && originalCmdgenChecked()
                && originalBindingsChecked();
    }

    boolean isContractReady() {
        return this.isOwnerShellReady() && commandGenerationInputsKnown() && commandGenerationOutputsKnown();
    }

    boolean isDrawCommandLayoutReady() {
        return drawCommandLayoutReady();
    }

    boolean isDrawCountLayoutReady() {
        return drawCountLayoutReady();
    }

    void markResourceReload() {
        this.resourceReloadSeen = true;
        this.markStale("resource-reload");
    }

    void markWorldUnload() {
        this.worldUnloadSeen = true;
        this.markStale("world-unload");
    }

    void markDimensionSwitch() {
        this.dimensionSwitchSeen = true;
        this.markStale("dimension-switch");
    }

    void markDebugPipelineClear() {
        this.debugPipelineClearSeen = true;
        this.markStale("debug-pipeline-clear");
    }

    void markPresetOff() {
        this.presetOffSeen = true;
        this.markStale("preset-off");
    }

    void markPresetClear() {
        this.presetClearSeen = true;
        this.markStale("preset-clear");
    }

    ForgeFormalCommandGenerationStats createStatusSnapshot() {
        ForgeFormalTerrainRendererStats terrain = this.instance.getFormalTerrainRendererOwner().createStatusSnapshot();
        ForgeFormalMdicViewportStats viewport = this.instance.getFormalMdicViewportOwner().createStatusSnapshot();
        boolean commandOwnerReady = terrain.formalTerrainRendererOwnerReady()
                && viewport.formalViewportOwnerReady()
                && viewport.formalCommandBufferOwnerReady()
                && this.isOwnerShellReady();
        boolean contractReady = commandOwnerReady && this.isContractReady();
        return new ForgeFormalCommandGenerationStats(
                STAGE,
                this.checkRuns,
                this.auditRuns,
                this.clearRuns,
                terrain.formalTerrainRendererOwnerReady(),
                viewport.formalViewportOwnerReady(),
                viewport.formalCommandBufferOwnerReady(),
                commandOwnerReady,
                contractReady,
                drawCommandLayoutReady(),
                drawCountLayoutReady(),
                false,
                false,
                false,
                true,
                this.enabled,
                originalCmdgenChecked(),
                originalBindingsChecked(),
                viewport.originalVoxyMdicViewportAlignmentChecked(),
                viewport.originalVoxyMdicSectionRendererAlignmentChecked(),
                false,
                true,
                true,
                DRAW_COMMAND_STRIDE_BYTES,
                DRAW_COMMAND_FIELD_COUNT,
                true,
                true,
                DRAW_COUNT_BUFFER_STRIDE_BYTES,
                DRAW_COUNT_BUFFER_HEADER_WORDS,
                DRAW_COUNT_BUFFER_LAYOUT_BYTES,
                "matched-no-draw-contract",
                "none",
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                commandGenerationInputsKnown(),
                commandGenerationOutputsKnown(),
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                DRAW_BUFFER_BINDING,
                DRAW_COUNT_BUFFER_BINDING,
                SECTION_METADATA_BUFFER_BINDING,
                VISIBILITY_BUFFER_BINDING,
                INDIRECT_LOOKUP_BUFFER_BINDING,
                POSITION_SCRATCH_BUFFER_BINDING,
                TRANSLUCENT_DISTANCE_BUFFER_BINDING,
                true,
                false,
                DEFERRED_REASON,
                this.lifecycleState.name(),
                this.lastLifecycleEvent,
                this.lifecycleGeneration,
                this.stale,
                this.requiresRebuild,
                BLOCKERS.size(),
                countBlockers("P0"),
                countBlockers("P1"),
                countBlockers("P2"),
                compactBlockers(),
                this.resourceReloadSeen,
                this.worldUnloadSeen,
                this.dimensionSwitchSeen,
                this.debugPipelineClearSeen,
                this.presetOffSeen,
                this.presetClearSeen,
                this.lastFailureReason,
                this.lastAudit.success(),
                this.lastAudit.error()
        );
    }

    String dumpBlockers() {
        return BLOCKERS.stream()
                .map(ForgeFormalRendererBlocker::detailed)
                .collect(Collectors.joining(","));
    }

    private void markStale(String event) {
        this.enabled = false;
        this.stale = true;
        this.requiresRebuild = true;
        this.lifecycleState = ForgeFormalCommandGenerationLifecycleState.STALE;
        this.lastLifecycleEvent = safeReason(event);
        this.lifecycleGeneration++;
    }

    private static boolean drawCommandLayoutReady() {
        return DRAW_COMMAND_FIELD_COUNT == 5 && DRAW_COMMAND_STRIDE_BYTES == 20;
    }

    private static boolean drawCountLayoutReady() {
        return DRAW_COUNT_BUFFER_HEADER_WORDS == 6 && DRAW_COUNT_BUFFER_LAYOUT_BYTES == 44;
    }

    private static boolean commandGenerationInputsKnown() {
        return true;
    }

    private static boolean commandGenerationOutputsKnown() {
        return true;
    }

    private static boolean originalCmdgenChecked() {
        return fileContains("src/main/resources/assets/voxy/shaders/lod/gl46/cmdgen.comp", "#define DRAW_BUFFER_BINDING 1")
                && fileContains("src/main/resources/assets/voxy/shaders/lod/gl46/cmdgen.comp", "writeCmd")
                && fileContains("src/main/resources/assets/voxy/shaders/lod/gl46/cmdgen.comp", "opaqueDrawCount")
                && fileContains("src/main/resources/assets/voxy/shaders/lod/gl46/cmdgen.comp", "positionBuffer[drawId]");
    }

    private static boolean originalBindingsChecked() {
        return fileContains("src/main/resources/assets/voxy/shaders/lod/gl46/bindings.glsl", "struct DrawCommand")
                && fileContains("src/main/resources/assets/voxy/shaders/lod/gl46/bindings.glsl", "uint  count")
                && fileContains("src/main/resources/assets/voxy/shaders/lod/gl46/bindings.glsl", "uint opaqueDrawCount")
                && fileContains("src/main/resources/assets/voxy/shaders/lod/gl46/bindings.glsl", "DrawCommand cullDrawIndirectCommand");
    }

    private static boolean fileContains(String relativePath, String marker) {
        for (Path path : List.of(Path.of(relativePath), Path.of("..").resolve(relativePath))) {
            if (!Files.exists(path)) {
                continue;
            }
            try {
                return Files.readString(path, StandardCharsets.UTF_8).contains(marker);
            } catch (IOException ignored) {
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

    private static String safeReason(String reason) {
        return reason == null || reason.isBlank() ? "unspecified" : reason.replace(' ', '-');
    }
}
