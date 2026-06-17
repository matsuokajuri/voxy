package me.cortex.voxy.forge;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

final class ForgeFormalTerrainRendererOwner {
    static final String STAGE = "K1_FORMAL_TERRAIN_RENDERER_OWNER_NO_DRAW_SKELETON";
    private static final String K0_VERDICT = "K0_VERDICT_READY_FOR_K1_NO_DRAW_FORMAL_RENDERER_OWNER";
    private static final List<Path> K0_AUDIT_DOC_CANDIDATES = List.of(
            Path.of("docs", "forge-1.20.1-k0-original-voxy-renderer-alignment-audit.md"),
            Path.of("..", "docs", "forge-1.20.1-k0-original-voxy-renderer-alignment-audit.md")
    );
    private static final List<ForgeFormalRendererBlocker> BLOCKERS = List.of(
            new ForgeFormalRendererBlocker("P0", "P0_FORMAL_VIEWPORT_OWNER_MISSING", "Formal viewport owner missing", "K1 defines the owner boundary, but no MDICViewport-equivalent resource owner exists yet.", "Add a formal viewport resource owner before live terrain draw.", true),
            new ForgeFormalRendererBlocker("P0", "P0_FORMAL_COMMAND_BUFFER_OWNER_MISSING", "Formal command buffer owner missing", "Debug command buffers are still separate proof resources and are not formal DrawCommand ownership.", "Create formal DrawCommand and draw-count buffer ownership.", true),
            new ForgeFormalRendererBlocker("P0", "P0_FORMAL_VISIBILITY_OWNER_MISSING", "Formal visibility owner missing", "No formal visibility buffer or render-list owner exists.", "Add formal visibility/render-list ownership before command generation.", true),
            new ForgeFormalRendererBlocker("P0", "P0_GLOBAL_FORMAL_MODEL_ID_GEOMETRY_MISSING", "Global formal model-id geometry missing", "J5 rewrites only isolated temporary preview buffers; live geometry still must be produced with formal ids globally.", "Move formal model id assignment into the formal BuiltSection/RenderDataFactory path.", true),
            new ForgeFormalRendererBlocker("P0", "P0_FORMAL_TERRAIN_SHADER_INTEGRATION_MISSING", "Formal terrain shader integration missing", "J-stage shaders are validation/preview programs, not the live terrain shader.", "Integrate a formal terrain shader after command and geometry ownership exist.", true),
            new ForgeFormalRendererBlocker("P0", "P0_FORMAL_MDIC_RENDERER_INTEGRATION_MISSING", "Formal MDIC renderer integration missing", "K1 does not call MDICSectionRenderer and does not own an equivalent renderer path.", "Add a formal MDIC renderer integration only after viewport, command, and visibility owners are ready.", true),
            new ForgeFormalRendererBlocker("P0", "P0_LIVE_TERRAIN_DRAW_DISABLED", "Live terrain draw disabled", "K1 is intentionally no-draw.", "Keep draw disabled until K-stage ownership blockers are resolved.", true),
            new ForgeFormalRendererBlocker("P1", "P1_LIGHTMAP_MISSING", "Lightmap missing", "Preview shaders do not provide the formal lightmap path.", "Define and validate formal lightmap binding and semantics.", false),
            new ForgeFormalRendererBlocker("P1", "P1_BIOME_TINT_MISSING", "Biome tint missing", "Safe-set modelColour validation is not the full biome tint path.", "Add formal biome tint/modelColour semantics.", false),
            new ForgeFormalRendererBlocker("P1", "P1_MATERIAL_ALPHA_SEMANTICS_MISSING", "Material alpha semantics missing", "Material, alpha, and cutout behavior are incomplete.", "Implement formal material/alpha/cutout semantics.", false),
            new ForgeFormalRendererBlocker("P1", "P1_LOD_TRAVERSAL_MISSING", "LOD traversal missing", "No HierarchicalOcclusionTraverser-equivalent owner is connected.", "Add formal visibility and LOD traversal ownership.", false),
            new ForgeFormalRendererBlocker("P1", "P1_RESOURCE_REBUILD_AUTOMATION_INCOMPLETE", "Resource rebuild automation incomplete", "I6 proves command-driven rebuild; automatic formal rebuild after reload is incomplete.", "Add real resource reload rebuild orchestration for formal renderer resources.", false)
    );

    private final ForgeVoxyInstance instance;
    private boolean enabled;
    private ForgeFormalTerrainRendererLifecycleState lifecycleState = ForgeFormalTerrainRendererLifecycleState.DISABLED;
    private long checkRuns;
    private long auditRuns;
    private long clearRuns;
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
    private ForgeFormalTerrainRendererAuditResult lastAudit = ForgeFormalTerrainRendererAuditResult.failure("not-audited");

    ForgeFormalTerrainRendererOwner(ForgeVoxyInstance instance) {
        this.instance = instance;
    }

    ForgeFormalTerrainRendererStats enable(String reason) {
        this.enabled = true;
        this.stale = false;
        this.requiresRebuild = false;
        this.lifecycleState = ForgeFormalTerrainRendererLifecycleState.ENABLED_NO_DRAW;
        this.lastLifecycleEvent = "enable:" + safeReason(reason);
        this.lastFailureReason = "none";
        return this.createStatusSnapshot();
    }

    ForgeFormalTerrainRendererStats check(String reason) {
        this.checkRuns++;
        if (this.stale) {
            this.lifecycleState = ForgeFormalTerrainRendererLifecycleState.STALE;
        } else if (this.enabled) {
            this.lifecycleState = ForgeFormalTerrainRendererLifecycleState.ENABLED_NO_DRAW;
        } else if (this.lifecycleState != ForgeFormalTerrainRendererLifecycleState.CLEARED) {
            this.lifecycleState = ForgeFormalTerrainRendererLifecycleState.DISABLED;
        }
        this.lastLifecycleEvent = "check:" + safeReason(reason) + "@" + Instant.now();
        return this.createStatusSnapshot();
    }

    ForgeFormalTerrainRendererAuditResult audit() {
        this.auditRuns++;
        ForgeFormalTerrainRendererStats status = this.createStatusSnapshot();
        String error = "none";
        if (!status.k0AlignmentAuditReady()) {
            error = "k0-audit-doc-missing";
        } else if (!status.k0VerdictReadyForK1()) {
            error = "k0-verdict-not-ready-for-k1";
        } else if (!status.formalTerrainRendererOwnerReady()) {
            error = "owner-not-ready";
        } else if (!status.originalVoxyAlignmentPreserved()) {
            error = "original-voxy-alignment-not-preserved";
        } else if (!status.previewSystemsSeparated()) {
            error = "preview-systems-not-separated";
        } else if (!status.debugRendererIsolationOk()) {
            error = "debug-renderers-not-isolated";
        } else if (status.sampleSetUsedAsFormalSource()) {
            error = "sample-set-used-as-formal-source";
        } else if (status.liveTerrainDrawStarted()) {
            error = "live-terrain-draw-started";
        } else if (status.formalMdicDrawStarted()) {
            error = "formal-mdic-draw-started";
        } else if (status.mdicSectionRendererCalled()) {
            error = "mdic-section-renderer-called";
        } else if (status.voxyRenderSystemCalled()) {
            error = "voxy-render-system-called";
        } else if (status.formalTerrainRendererReady()) {
            error = "formal-terrain-renderer-ready-should-remain-false";
        } else if (status.actualRendererDrawEnabled()) {
            error = "actual-renderer-draw-enabled";
        }
        boolean success = "none".equals(error);
        this.lastFailureReason = success ? "none" : error;
        this.lastAudit = new ForgeFormalTerrainRendererAuditResult(
                success,
                error,
                status.k0AlignmentAuditReady(),
                status.k0VerdictReadyForK1(),
                status.formalTerrainRendererOwnerReady(),
                status.originalVoxyAlignmentPreserved(),
                status.previewSystemsSeparated(),
                status.debugRendererIsolationOk(),
                status.sampleSetUsedAsFormalSource(),
                status.liveTerrainDrawStarted(),
                status.formalMdicDrawStarted(),
                status.mdicSectionRendererCalled(),
                status.voxyRenderSystemCalled(),
                status.formalTerrainRendererReady(),
                status.actualRendererDrawEnabled()
        );
        return this.lastAudit;
    }

    ForgeFormalTerrainRendererAuditResult createAuditStatusSnapshot() {
        return this.lastAudit;
    }

    ForgeFormalTerrainRendererStats clear(String reason) {
        this.clearRuns++;
        this.enabled = false;
        this.stale = false;
        this.requiresRebuild = false;
        this.lifecycleState = ForgeFormalTerrainRendererLifecycleState.CLEARED;
        this.lastLifecycleEvent = "clear:" + safeReason(reason);
        this.lastFailureReason = "none";
        this.resourceReloadSeen = false;
        this.worldUnloadSeen = false;
        this.dimensionSwitchSeen = false;
        this.debugPipelineClearSeen = false;
        this.presetOffSeen = false;
        this.presetClearSeen = false;
        this.lastAudit = ForgeFormalTerrainRendererAuditResult.failure("cleared");
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

    ForgeFormalTerrainRendererStats createStatusSnapshot() {
        K0AuditStatus k0 = readK0AuditStatus();
        ForgeFormalModelStoreStats modelStore = this.instance.getFormalModelStore().createStatusSnapshot();
        ForgeFormalModelBakeryLifecycleStats bakery = this.instance.getFormalModelBakeryLifecycle().createStatusSnapshot();
        ForgeFormalShaderProgramStats shaderProgram = this.instance.getFormalShaderProgramValidator().createStatusSnapshot();
        ForgeFormalTexturedShaderPreviewStats texturedPreview = this.instance.getFormalTexturedShaderPreview().createStatusSnapshot();
        ForgeFormalPackedQuadPreviewStats packedPreview = this.instance.getFormalPackedQuadPreview().createStatusSnapshot();
        ForgeFormalTerrainPackedRecordBridgeStats terrainBridge = this.instance.getFormalTerrainPackedRecordBridge().createStatusSnapshot();
        ForgeGpuGeometryStats geometry = this.instance.getGpuGeometryUploadManager().createStatusSnapshot();
        ForgeSectionGeometryStats section = this.instance.getSectionGeometryManager().createStatusSnapshot();
        boolean previewSystemsSeparated = !texturedPreview.formalTexturedShaderReady()
                && !texturedPreview.formalRendererReady()
                && !packedPreview.formalTexturedShaderReady()
                && !packedPreview.formalRendererReady()
                && !terrainBridge.formalTexturedShaderReady()
                && !terrainBridge.formalRendererReady();
        boolean sampleSetUsedAsFormalSource = packedPreview.sampleSetModelIdsUsed()
                || packedPreview.sampleSetBridgeUsedAsFormalSource()
                || terrainBridge.sampleSetModelIdsUsed()
                || terrainBridge.sampleSetBridgeUsedAsFormalSource();
        boolean liveTerrainDrawStarted = texturedPreview.terrainDrawStarted()
                || packedPreview.terrainDrawStarted()
                || terrainBridge.terrainDrawStarted();
        boolean formalMdicDrawStarted = texturedPreview.formalRendererDrawStarted()
                || packedPreview.formalRendererDrawStarted()
                || terrainBridge.formalRendererDrawStarted();
        boolean actualDrawEnabled = texturedPreview.actualRendererDrawEnabled()
                || packedPreview.actualRendererDrawEnabled()
                || terrainBridge.actualRendererDrawEnabled();
        boolean ownerReady = k0.docExists() && k0.verdictReady();
        boolean alignmentPreserved = ownerReady
                && previewSystemsSeparated
                && !sampleSetUsedAsFormalSource
                && !liveTerrainDrawStarted
                && !formalMdicDrawStarted
                && !actualDrawEnabled;
        return new ForgeFormalTerrainRendererStats(
                STAGE,
                this.checkRuns,
                this.auditRuns,
                this.clearRuns,
                ownerReady,
                ownerReady,
                false,
                false,
                true,
                this.enabled,
                k0.docExists(),
                k0.verdictReady(),
                alignmentPreserved,
                modelStore.formalModelStoreOwnerReady(),
                bakery.formalModelBakeryLifecycleSkeletonReady(),
                terrainBridge.realTerrainPackedRecordBridgeReady(),
                shaderProgram.formalShaderProgramValidationReady(),
                texturedPreview.formalTexturedShaderPrototypeReady(),
                packedPreview.formalPackedQuadPreviewReady(),
                geometry.heapCreated(),
                section.maxSections() > 0,
                false,
                false,
                false,
                false,
                false,
                false,
                true,
                previewSystemsSeparated,
                sampleSetUsedAsFormalSource,
                this.lifecycleState.name(),
                this.lastLifecycleEvent,
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
                liveTerrainDrawStarted,
                formalMdicDrawStarted,
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
        this.lifecycleState = ForgeFormalTerrainRendererLifecycleState.STALE;
        this.lastLifecycleEvent = safeReason(event);
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

    private static K0AuditStatus readK0AuditStatus() {
        for (Path path : K0_AUDIT_DOC_CANDIDATES) {
            if (!Files.exists(path)) {
                continue;
            }
            try {
                String content = Files.readString(path, StandardCharsets.UTF_8);
                return new K0AuditStatus(true, content.contains(K0_VERDICT));
            } catch (IOException ignored) {
                return new K0AuditStatus(true, false);
            }
        }
        return new K0AuditStatus(false, false);
    }

    private static String safeReason(String reason) {
        return reason == null || reason.isBlank() ? "unspecified" : reason.replace(' ', '-');
    }

    private record K0AuditStatus(boolean docExists, boolean verdictReady) {
    }
}
