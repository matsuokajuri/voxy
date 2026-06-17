package me.cortex.voxy.forge;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

final class ForgeFormalVisibilityOwner {
    static final String STAGE = "K4_FORMAL_VISIBILITY_RENDER_LIST_OWNERSHIP_SKELETON";
    private static final String DEFERRED_REASON = "K4-no-draw-logical-owner";
    private static final List<ForgeFormalRendererBlocker> BLOCKERS = List.of(
            new ForgeFormalRendererBlocker("P0", "P0_FORMAL_VISIBILITY_TRAVERSAL_IMPLEMENTATION_MISSING", "Formal visibility traversal implementation missing", "K4 owns the visibility/render-list contract but does not implement HierarchicalOcclusionTraverser-equivalent traversal.", "Implement formal traversal after the owner contract is stable.", true),
            new ForgeFormalRendererBlocker("P0", "P0_FORMAL_COMMAND_GENERATION_GPU_PROGRAM_MISSING", "Formal command generation GPU program missing", "K4 does not run cmdgen.comp and therefore cannot produce formal draw commands.", "Add a formal command-generation GPU program after traversal and geometry id ownership are ready.", true),
            new ForgeFormalRendererBlocker("P0", "P0_GLOBAL_FORMAL_MODEL_ID_GEOMETRY_MISSING", "Global formal model-id geometry missing", "Live geometry is not globally encoded with formal model ids.", "Move formal model ids into the formal BuiltSection/RenderDataFactory path.", true),
            new ForgeFormalRendererBlocker("P0", "P0_FORMAL_TERRAIN_SHADER_INTEGRATION_MISSING", "Formal terrain shader integration missing", "No live formal terrain shader is bound or consumed by K4.", "Integrate the formal terrain shader after command and visibility inputs are operational.", true),
            new ForgeFormalRendererBlocker("P0", "P0_FORMAL_MDIC_DRAW_DISABLED", "Formal MDIC draw disabled", "K4 remains no-draw and never calls glMultiDrawElementsIndirectCountARB.", "Keep formal draw disabled until K-stage ownership blockers are resolved.", true),
            new ForgeFormalRendererBlocker("P1", "P1_HIERARCHICAL_OCCLUSION_MISSING", "Hierarchical occlusion missing", "K4 records the original traversal contract but does not port the hierarchical occlusion queue.", "Port the traversal owner and queue resources in a later stage.", false),
            new ForgeFormalRendererBlocker("P1", "P1_RENDER_DISTANCE_TRACKER_INCOMPLETE", "Render distance tracker incomplete", "K4 does not yet own a formal RenderDistanceTracker equivalent.", "Add formal render-distance tracking before live traversal.", false),
            new ForgeFormalRendererBlocker("P1", "P1_LIGHTMAP_MISSING", "Lightmap missing", "Formal lightmap input remains incomplete.", "Add formal lightmap binding and semantics.", false),
            new ForgeFormalRendererBlocker("P1", "P1_BIOME_TINT_MISSING", "Biome tint missing", "Formal biome tint/modelColour semantics remain incomplete.", "Complete formal biome tint handling.", false),
            new ForgeFormalRendererBlocker("P1", "P1_MATERIAL_ALPHA_SEMANTICS_MISSING", "Material alpha semantics missing", "Formal material, alpha, and cutout semantics remain incomplete.", "Implement formal material/alpha/cutout behavior.", false),
            new ForgeFormalRendererBlocker("P1", "P1_RESOURCE_REBUILD_AUTOMATION_INCOMPLETE", "Resource rebuild automation incomplete", "K4 stales visibility ownership but does not automatically rebuild traversal state.", "Add rebuild orchestration when traversal resources become operational.", false)
    );

    private final ForgeVoxyInstance instance;
    private boolean enabled;
    private ForgeFormalVisibilityLifecycleState lifecycleState = ForgeFormalVisibilityLifecycleState.DISABLED;
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
    private ForgeFormalVisibilityAuditResult lastAudit = ForgeFormalVisibilityAuditResult.failure("not-audited");

    ForgeFormalVisibilityOwner(ForgeVoxyInstance instance) {
        this.instance = instance;
    }

    ForgeFormalVisibilityStats enable(String reason) {
        this.enabled = true;
        this.stale = false;
        this.requiresRebuild = false;
        this.lifecycleState = ForgeFormalVisibilityLifecycleState.ENABLED_NO_DRAW;
        this.lastLifecycleEvent = "enable:" + safeReason(reason);
        this.lastFailureReason = "none";
        this.lifecycleGeneration++;
        return this.createStatusSnapshot();
    }

    ForgeFormalVisibilityStats check(String reason) {
        this.checkRuns++;
        if (this.stale) {
            this.lifecycleState = ForgeFormalVisibilityLifecycleState.STALE;
        } else if (this.enabled) {
            this.lifecycleState = ForgeFormalVisibilityLifecycleState.ENABLED_NO_DRAW;
        } else if (this.lifecycleState != ForgeFormalVisibilityLifecycleState.CLEARED) {
            this.lifecycleState = ForgeFormalVisibilityLifecycleState.DISABLED;
        }
        this.lastLifecycleEvent = "check:" + safeReason(reason) + "@" + Instant.now();
        return this.createStatusSnapshot();
    }

    ForgeFormalVisibilityAuditResult audit() {
        this.auditRuns++;
        ForgeFormalVisibilityStats status = this.createStatusSnapshot();
        String error = "none";
        if (!status.formalTerrainRendererOwnerReady()) {
            error = "k1-owner-not-ready";
        } else if (!status.formalViewportOwnerReady()) {
            error = "k2-viewport-owner-not-ready";
        } else if (!status.formalCommandGenerationOwnerReady()) {
            error = "k3-command-generation-owner-not-ready";
        } else if (!status.formalVisibilityOwnerReady()) {
            error = "formal-visibility-owner-not-ready";
        } else if (!status.originalVoxyHierarchicalOcclusionAlignmentChecked()) {
            error = "original-hierarchical-occlusion-not-inspected";
        } else if (!status.originalVoxyRenderDistanceTrackerAlignmentChecked()) {
            error = "original-render-distance-tracker-not-inspected";
        } else if (!status.originalVoxyViewportAlignmentChecked()) {
            error = "original-viewport-not-inspected";
        } else if (!status.originalVoxyMdicViewportAlignmentChecked()) {
            error = "original-mdic-viewport-not-inspected";
        } else if (!status.originalVoxyCmdgenVisibilityAlignmentChecked()) {
            error = "original-cmdgen-visibility-contract-not-inspected";
        } else if (!status.visibilityBufferContractKnown()) {
            error = "visibility-buffer-contract-unknown";
        } else if (!status.renderListContractKnown()) {
            error = "render-list-contract-unknown";
        } else if (status.debugPlannerUsedAsFormal()) {
            error = "debug-planner-used-as-formal";
        } else if (status.cmdgenComputeShaderRun()) {
            error = "cmdgen-compute-shader-run";
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
        this.lastAudit = new ForgeFormalVisibilityAuditResult(
                success,
                error,
                status.formalTerrainRendererOwnerReady(),
                status.formalViewportOwnerReady(),
                status.formalCommandGenerationOwnerReady(),
                status.formalVisibilityOwnerReady(),
                status.originalVoxyHierarchicalOcclusionAlignmentChecked(),
                status.originalVoxyRenderDistanceTrackerAlignmentChecked(),
                status.originalVoxyViewportAlignmentChecked(),
                status.originalVoxyMdicViewportAlignmentChecked(),
                status.originalVoxyCmdgenVisibilityAlignmentChecked(),
                status.visibilityBufferContractKnown(),
                status.renderListContractKnown(),
                status.debugPlannerUsedAsFormal(),
                status.cpuCandidateSnapshotUsed(),
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

    ForgeFormalVisibilityAuditResult createAuditStatusSnapshot() {
        return this.lastAudit;
    }

    ForgeFormalVisibilityStats clear(String reason) {
        this.clearRuns++;
        this.enabled = false;
        this.stale = false;
        this.requiresRebuild = false;
        this.lifecycleState = ForgeFormalVisibilityLifecycleState.CLEARED;
        this.lastLifecycleEvent = "clear:" + safeReason(reason);
        this.lastFailureReason = "none";
        this.resourceReloadSeen = false;
        this.worldUnloadSeen = false;
        this.dimensionSwitchSeen = false;
        this.debugPipelineClearSeen = false;
        this.presetOffSeen = false;
        this.presetClearSeen = false;
        this.lifecycleGeneration++;
        this.lastAudit = ForgeFormalVisibilityAuditResult.failure("cleared");
        return this.createStatusSnapshot();
    }

    boolean isOwnerShellReady() {
        return this.enabled
                && originalHierarchicalOcclusionChecked()
                && originalRenderDistanceTrackerChecked()
                && originalViewportChecked()
                && originalMdicViewportChecked()
                && originalCmdgenVisibilityChecked()
                && visibilityContractKnown()
                && renderListContractKnown()
                && indirectLookupContractKnown();
    }

    boolean isVisibilityContractReady() {
        return this.isOwnerShellReady();
    }

    boolean isRenderListContractReady() {
        return this.isOwnerShellReady();
    }

    boolean isVisibilityTraversalImplemented() {
        return false;
    }

    boolean isHierarchicalOcclusionReady() {
        return false;
    }

    boolean isRenderDistanceTrackerReady() {
        return false;
    }

    boolean isCpuCandidateSnapshotReady() {
        return true;
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

    ForgeFormalVisibilityStats createStatusSnapshot() {
        ForgeFormalTerrainRendererStats terrain = this.instance.getFormalTerrainRendererOwner().createStatusSnapshot();
        ForgeFormalMdicViewportStats viewport = this.instance.getFormalMdicViewportOwner().createStatusSnapshot();
        ForgeFormalCommandGenerationStats command = this.instance.getFormalCommandGenerationOwner().createStatusSnapshot();
        ForgeSectionGeometryStats section = this.instance.getSectionGeometryManager().createStatusSnapshot();
        int candidateCount = Math.max(section.metadataValid(), 0);
        boolean snapshotReady = true;
        boolean snapshotUsed = candidateCount > 0 || section.maxSections() > 0;
        String candidateSource = section.maxSections() > 0
                ? "ForgeSectionGeometryManager.metadata-snapshot"
                : "none";
        boolean ownerReady = terrain.formalTerrainRendererOwnerReady()
                && viewport.formalViewportOwnerReady()
                && command.formalCommandGenerationOwnerReady()
                && this.isOwnerShellReady();
        return new ForgeFormalVisibilityStats(
                STAGE,
                this.checkRuns,
                this.auditRuns,
                this.clearRuns,
                terrain.formalTerrainRendererOwnerReady(),
                viewport.formalViewportOwnerReady(),
                command.formalCommandGenerationOwnerReady(),
                ownerReady,
                ownerReady,
                ownerReady,
                ownerReady,
                ownerReady,
                false,
                false,
                false,
                true,
                this.enabled,
                originalHierarchicalOcclusionChecked(),
                originalRenderDistanceTrackerChecked(),
                originalViewportChecked(),
                originalMdicViewportChecked(),
                originalCmdgenVisibilityChecked(),
                false,
                false,
                false,
                snapshotReady,
                snapshotUsed,
                candidateCount,
                candidateSource,
                candidateCount > 0,
                candidateCount > 0,
                visibilityContractKnown(),
                renderListContractKnown(),
                indirectLookupContractKnown(),
                visibilityContractKnown(),
                renderListContractKnown() && indirectLookupContractKnown(),
                false,
                true,
                false,
                false,
                false,
                false,
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
        this.lifecycleState = ForgeFormalVisibilityLifecycleState.STALE;
        this.lastLifecycleEvent = safeReason(event);
        this.lifecycleGeneration++;
    }

    private static boolean visibilityContractKnown() {
        return originalMdicViewportChecked()
                && originalCmdgenVisibilityChecked();
    }

    private static boolean renderListContractKnown() {
        return originalHierarchicalOcclusionChecked()
                && originalViewportChecked()
                && originalMdicViewportChecked();
    }

    private static boolean indirectLookupContractKnown() {
        return originalMdicViewportChecked()
                && originalCmdgenVisibilityChecked();
    }

    private static boolean originalHierarchicalOcclusionChecked() {
        return fileContains("src/main/java/me/cortex/voxy/client/core/rendering/hierachical/HierarchicalOcclusionTraverser.java", "class HierarchicalOcclusionTraverser")
                && fileContains("src/main/java/me/cortex/voxy/client/core/rendering/hierachical/HierarchicalOcclusionTraverser.java", "RENDER_QUEUE_BINDING")
                && fileContains("src/main/java/me/cortex/voxy/client/core/rendering/hierachical/HierarchicalOcclusionTraverser.java", "viewport.getRenderList()")
                && fileContains("src/main/java/me/cortex/voxy/client/core/rendering/hierachical/HierarchicalOcclusionTraverser.java", "MAX_QUEUE_SIZE");
    }

    private static boolean originalRenderDistanceTrackerChecked() {
        return fileContains("src/main/java/me/cortex/voxy/client/core/rendering/RenderDistanceTracker.java", "class RenderDistanceTracker")
                && fileContains("src/main/java/me/cortex/voxy/client/core/rendering/RenderDistanceTracker.java", "setCenterAndProcess")
                && fileContains("src/main/java/me/cortex/voxy/client/core/rendering/RenderDistanceTracker.java", "addTopLevelNode")
                && fileContains("src/main/java/me/cortex/voxy/client/core/rendering/RenderDistanceTracker.java", "WorldEngine.getWorldSectionId");
    }

    private static boolean originalViewportChecked() {
        return fileContains("src/main/java/me/cortex/voxy/client/core/rendering/Viewport.java", "abstract class Viewport")
                && fileContains("src/main/java/me/cortex/voxy/client/core/rendering/Viewport.java", "public abstract GlBuffer getRenderList()")
                && fileContains("src/main/java/me/cortex/voxy/client/core/rendering/ViewportSelector.java", "class ViewportSelector");
    }

    private static boolean originalMdicViewportChecked() {
        return fileContains("src/main/java/me/cortex/voxy/client/core/rendering/section/backend/mdic/MDICViewport.java", "indirectLookupBuffer")
                && fileContains("src/main/java/me/cortex/voxy/client/core/rendering/section/backend/mdic/MDICViewport.java", "visibilityBuffer")
                && fileContains("src/main/java/me/cortex/voxy/client/core/rendering/section/backend/mdic/MDICViewport.java", "getRenderList()");
    }

    private static boolean originalCmdgenVisibilityChecked() {
        return fileContains("src/main/resources/assets/voxy/shaders/lod/gl46/cmdgen.comp", "uint sectionId = indirectLookup")
                && fileContains("src/main/resources/assets/voxy/shaders/lod/gl46/cmdgen.comp", "visibilityData[sectionId]")
                && fileContains("src/main/resources/assets/voxy/shaders/lod/gl46/bindings.glsl", "uint sectionCount")
                && fileContains("src/main/resources/assets/voxy/shaders/lod/gl46/bindings.glsl", "uint indirectLookup[]");
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
