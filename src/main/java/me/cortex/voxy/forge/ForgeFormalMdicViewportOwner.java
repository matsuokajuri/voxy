package me.cortex.voxy.forge;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

final class ForgeFormalMdicViewportOwner {
    static final String STAGE = "K2_FORMAL_VIEWPORT_COMMAND_VISIBILITY_OWNERSHIP_SKELETON";
    private static final int OPAQUE_DRAW_COUNT = 400_000;
    private static final int TRANSLUCENT_DRAW_COUNT = 100_000;
    private static final int TEMPORAL_DRAW_COUNT = 100_000;
    private static final int DRAW_COMMAND_WORDS = 5;
    private static final int DRAW_COMMAND_BYTES = DRAW_COMMAND_WORDS * Integer.BYTES;
    private static final int DRAW_COMMAND_CAPACITY = OPAQUE_DRAW_COUNT + TRANSLUCENT_DRAW_COUNT + TEMPORAL_DRAW_COUNT;
    private static final int DRAW_COUNT_BUFFER_BYTES = 1024;
    private static final int DRAW_COUNT_CAPACITY = DRAW_COUNT_BUFFER_BYTES / Integer.BYTES;
    private static final int RENDER_LIST_CAPACITY = 200_000;
    private static final long RENDER_LIST_BYTES = RENDER_LIST_CAPACITY * (long) Integer.BYTES + Integer.BYTES;
    private static final int POSITION_SCRATCH_CAPACITY = 400_000;
    private static final long POSITION_SCRATCH_BYTES = POSITION_SCRATCH_CAPACITY * 8L;
    private static final String DEFERRED_REASON = "K2-no-draw-logical-owner";
    private static final String K0_VERDICT = "K0_VERDICT_READY_FOR_K1_NO_DRAW_FORMAL_RENDERER_OWNER";
    private static final List<Path> K0_AUDIT_DOC_CANDIDATES = List.of(
            Path.of("docs", "forge-1.20.1-k0-original-voxy-renderer-alignment-audit.md"),
            Path.of("..", "docs", "forge-1.20.1-k0-original-voxy-renderer-alignment-audit.md")
    );
    private static final List<ForgeFormalRendererBlocker> BLOCKERS = List.of(
            new ForgeFormalRendererBlocker("P0", "P0_FORMAL_COMMAND_GENERATION_GPU_PROGRAM_MISSING", "Formal command generation GPU program missing", "K3 can own the command-generation contract, but no formal cmdgen path fills K2 command resources.", "Add operational command generation after visibility and geometry model-id ownership are ready.", true),
            new ForgeFormalRendererBlocker("P0", "P0_FORMAL_VISIBILITY_TRAVERSAL_MISSING", "Formal visibility traversal missing", "K2 owns visibility placeholders, but no traversal fills visibility/render-list data.", "Add formal visibility and LOD traversal ownership.", true),
            new ForgeFormalRendererBlocker("P0", "P0_GLOBAL_FORMAL_MODEL_ID_GEOMETRY_MISSING", "Global formal model-id geometry missing", "J5 rewrites only temporary preview records; live geometry is not globally formal-id encoded.", "Move formal model ids into the formal BuiltSection/RenderDataFactory path.", true),
            new ForgeFormalRendererBlocker("P0", "P0_FORMAL_TERRAIN_SHADER_INTEGRATION_MISSING", "Formal terrain shader integration missing", "No live formal terrain shader is bound or consumed by K2.", "Integrate the formal terrain shader after command resources are filled.", true),
            new ForgeFormalRendererBlocker("P0", "P0_FORMAL_MDIC_DRAW_DISABLED", "Formal MDIC draw disabled", "K2 is intentionally no-draw and does not call glMultiDrawElementsIndirectCountARB.", "Keep formal draw disabled until command generation and shader integration are ready.", true),
            new ForgeFormalRendererBlocker("P1", "P1_LIGHTMAP_MISSING", "Lightmap missing", "Formal lightmap binding/semantics remain incomplete.", "Add formal lightmap input after shader integration begins.", false),
            new ForgeFormalRendererBlocker("P1", "P1_BIOME_TINT_MISSING", "Biome tint missing", "Formal biome tint/modelColour semantics remain incomplete.", "Complete the formal biome tint path.", false),
            new ForgeFormalRendererBlocker("P1", "P1_MATERIAL_ALPHA_SEMANTICS_MISSING", "Material alpha semantics missing", "Formal material, alpha, and cutout semantics remain incomplete.", "Implement formal material/alpha/cutout behavior.", false),
            new ForgeFormalRendererBlocker("P1", "P1_RESOURCE_REBUILD_AUTOMATION_INCOMPLETE", "Resource rebuild automation incomplete", "K2 can stale resources but does not rebuild formal command resources automatically after reload.", "Add automatic rebuild orchestration after owner resources become real GL allocations.", false)
    );

    private final ForgeVoxyInstance instance;
    private boolean enabled;
    private ForgeFormalMdicViewportLifecycleState lifecycleState = ForgeFormalMdicViewportLifecycleState.DISABLED;
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
    private ForgeFormalMdicViewportAuditResult lastAudit = ForgeFormalMdicViewportAuditResult.failure("not-audited");

    ForgeFormalMdicViewportOwner(ForgeVoxyInstance instance) {
        this.instance = instance;
    }

    ForgeFormalMdicViewportStats enable(String reason) {
        this.enabled = true;
        this.stale = false;
        this.requiresRebuild = false;
        this.lifecycleState = ForgeFormalMdicViewportLifecycleState.ENABLED_NO_DRAW;
        this.lastLifecycleEvent = "enable:" + safeReason(reason);
        this.lastFailureReason = "none";
        this.lifecycleGeneration++;
        return this.createStatusSnapshot();
    }

    ForgeFormalMdicViewportStats check(String reason) {
        this.checkRuns++;
        if (this.stale) {
            this.lifecycleState = ForgeFormalMdicViewportLifecycleState.STALE;
        } else if (this.enabled) {
            this.lifecycleState = ForgeFormalMdicViewportLifecycleState.ENABLED_NO_DRAW;
        } else if (this.lifecycleState != ForgeFormalMdicViewportLifecycleState.CLEARED) {
            this.lifecycleState = ForgeFormalMdicViewportLifecycleState.DISABLED;
        }
        this.lastLifecycleEvent = "check:" + safeReason(reason) + "@" + Instant.now();
        return this.createStatusSnapshot();
    }

    ForgeFormalMdicViewportAuditResult audit() {
        this.auditRuns++;
        ForgeFormalMdicViewportStats status = this.createStatusSnapshot();
        String error = "none";
        if (!status.formalTerrainRendererOwnerReady()) {
            error = "k1-owner-not-ready";
        } else if (!status.formalViewportOwnerReady()) {
            error = "formal-viewport-owner-not-ready";
        } else if (!status.formalCommandBufferOwnerReady()) {
            error = "formal-command-ownership-not-ready";
        } else if (!status.formalVisibilityOwnerReady()) {
            error = "formal-visibility-ownership-not-ready";
        } else if (!status.originalVoxyMdicViewportAlignmentChecked()) {
            error = "original-mdic-viewport-not-inspected";
        } else if (!status.originalVoxyMdicSectionRendererAlignmentChecked()) {
            error = "original-mdic-section-renderer-not-inspected";
        } else if (status.debugMdicCommandBuffersUsedAsFormal()) {
            error = "debug-mdic-command-buffers-used-as-formal";
        } else if (!status.debugRendererIsolationOk()) {
            error = "debug-renderer-isolation-failed";
        } else if (status.drawCommandExecuted()) {
            error = "draw-command-executed";
        } else if (status.multiDrawIndirectCountCalled()) {
            error = "multi-draw-indirect-count-called";
        } else if (status.formalCmdgenExecuted()) {
            error = "formal-cmdgen-executed";
        } else if (status.mdicSectionRendererCalled()) {
            error = "mdic-section-renderer-called";
        } else if (status.voxyRenderSystemCalled()) {
            error = "voxy-render-system-called";
        } else if (status.formalRendererReady()) {
            error = "formal-renderer-ready-should-remain-false";
        } else if (status.actualRendererDrawEnabled()) {
            error = "actual-renderer-draw-enabled";
        }
        boolean success = "none".equals(error);
        this.lastFailureReason = success ? "none" : error;
        this.lastAudit = new ForgeFormalMdicViewportAuditResult(
                success,
                error,
                status.formalTerrainRendererOwnerReady(),
                status.formalViewportOwnerReady(),
                status.formalCommandBufferOwnerReady(),
                status.formalVisibilityOwnerReady(),
                status.originalVoxyMdicViewportAlignmentChecked(),
                status.originalVoxyMdicSectionRendererAlignmentChecked(),
                status.debugMdicCommandBuffersUsedAsFormal(),
                !status.debugRendererIsolationOk(),
                status.drawCommandExecuted(),
                status.multiDrawIndirectCountCalled(),
                status.formalCmdgenExecuted(),
                status.mdicSectionRendererCalled(),
                status.voxyRenderSystemCalled(),
                status.formalRendererReady(),
                status.actualRendererDrawEnabled()
        );
        return this.lastAudit;
    }

    ForgeFormalMdicViewportAuditResult createAuditStatusSnapshot() {
        return this.lastAudit;
    }

    ForgeFormalMdicViewportStats clear(String reason) {
        this.clearRuns++;
        this.enabled = false;
        this.stale = false;
        this.requiresRebuild = false;
        this.lifecycleState = ForgeFormalMdicViewportLifecycleState.CLEARED;
        this.lastLifecycleEvent = "clear:" + safeReason(reason);
        this.lastFailureReason = "none";
        this.resourceReloadSeen = false;
        this.worldUnloadSeen = false;
        this.dimensionSwitchSeen = false;
        this.debugPipelineClearSeen = false;
        this.presetOffSeen = false;
        this.presetClearSeen = false;
        this.lifecycleGeneration++;
        this.lastAudit = ForgeFormalMdicViewportAuditResult.failure("cleared");
        return this.createStatusSnapshot();
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

    ForgeFormalMdicViewportStats createStatusSnapshot() {
        ForgeSectionGeometryStats section = this.instance.getSectionGeometryManager().createStatusSnapshot();
        boolean k1OwnerReady = k0VerdictReady();
        boolean mdicViewportChecked = fileContains("src/main/java/me/cortex/voxy/client/core/rendering/section/backend/mdic/MDICViewport.java", "drawCountCallBuffer")
                && fileContains("src/main/java/me/cortex/voxy/client/core/rendering/section/backend/mdic/MDICViewport.java", "indirectLookupBuffer")
                && fileContains("src/main/java/me/cortex/voxy/client/core/rendering/section/backend/mdic/MDICViewport.java", "positionScratchBuffer");
        boolean mdicRendererChecked = fileContains("src/main/java/me/cortex/voxy/client/core/rendering/section/backend/mdic/MDICSectionRenderer.java", "glMultiDrawElementsIndirectCountARB")
                && fileContains("src/main/java/me/cortex/voxy/client/core/rendering/section/backend/mdic/MDICSectionRenderer.java", "bindRenderingBuffers")
                && fileContains("src/main/java/me/cortex/voxy/client/core/rendering/section/backend/mdic/MDICSectionRenderer.java", "cmdgen.comp");
        int visibilityCapacity = Math.max(section.maxSections(), 0);
        long visibilityBytes = visibilityCapacity * (long) Integer.BYTES;
        boolean ownerReady = k1OwnerReady;
        boolean commandOwnersReady = ownerReady;
        boolean visibilityOwnerReady = ownerReady;
        boolean commandGenerationOwnerReady = this.instance.getFormalCommandGenerationOwner().isOwnerShellReady();
        return new ForgeFormalMdicViewportStats(
                STAGE,
                this.checkRuns,
                this.auditRuns,
                this.clearRuns,
                ownerReady,
                ownerReady,
                commandOwnersReady,
                commandOwnersReady,
                commandOwnersReady,
                visibilityOwnerReady,
                visibilityOwnerReady,
                visibilityOwnerReady,
                visibilityOwnerReady,
                commandGenerationOwnerReady,
                false,
                false,
                false,
                true,
                this.enabled,
                mdicViewportChecked,
                mdicRendererChecked,
                false,
                true,
                true,
                false,
                -1,
                DRAW_COMMAND_CAPACITY * (long) DRAW_COMMAND_BYTES,
                DRAW_COMMAND_CAPACITY,
                true,
                true,
                false,
                -1,
                DRAW_COUNT_BUFFER_BYTES,
                DRAW_COUNT_CAPACITY,
                true,
                true,
                false,
                -1,
                visibilityBytes,
                visibilityCapacity,
                true,
                true,
                false,
                -1,
                RENDER_LIST_BYTES,
                RENDER_LIST_CAPACITY,
                true,
                true,
                false,
                -1,
                POSITION_SCRATCH_BYTES,
                POSITION_SCRATCH_CAPACITY,
                true,
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
                false,
                false,
                false,
                false,
                false,
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
        this.lifecycleState = ForgeFormalMdicViewportLifecycleState.STALE;
        this.lastLifecycleEvent = safeReason(event);
        this.lifecycleGeneration++;
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

    private static boolean k0VerdictReady() {
        for (Path path : K0_AUDIT_DOC_CANDIDATES) {
            if (!Files.exists(path)) {
                continue;
            }
            try {
                return Files.readString(path, StandardCharsets.UTF_8).contains(K0_VERDICT);
            } catch (IOException ignored) {
                return false;
            }
        }
        return false;
    }

    private static String safeReason(String reason) {
        return reason == null || reason.isBlank() ? "unspecified" : reason.replace(' ', '-');
    }
}
