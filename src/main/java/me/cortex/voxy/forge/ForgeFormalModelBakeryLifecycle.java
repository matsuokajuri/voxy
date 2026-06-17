package me.cortex.voxy.forge;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

final class ForgeFormalModelBakeryLifecycle {
    static final String STAGE = "I6_FORMAL_MODELBAKERY_LIFECYCLE_REBUILD_AND_ALIAS_DEDUPE";

    private final ForgeVoxyInstance instance;
    private final List<CanonicalModel> canonicalModels = new ArrayList<>();
    private final List<AliasModel> aliasModels = new ArrayList<>();
    private long rebuildRuns;
    private long auditRuns;
    private long clearRuns;
    private long resourceGeneration;
    private long modelLifecycleGeneration;
    private long uploadGeneration;
    private boolean enabled;
    private boolean stale;
    private boolean requiresRebuild;
    private String lifecycleState = "UNINITIALIZED";
    private String lastLifecycleEvent = "initialized";
    private String staleReason = "none";
    private String lastRebuildReason = "none";
    private int lastRebuildModelCount;
    private boolean lastRebuildAuditOk;
    private String lastFailureReason = "none";
    private int safeSetRequestedBlockStateCount;
    private int safeSetAcceptedBlockStateCount;
    private int safeSetUploadedModelCount;
    private int canonicalModelCount;
    private int dedupeAliasCount;
    private int dedupeHitCount;
    private int dedupeMissCount;
    private int aliasedBlockStateCount;
    private int illegalDuplicateMappingCount;
    private boolean aliasSafeDedupeReady;
    private boolean reloadRebuildPrototypeReady;
    private boolean modelDataReadbackOk;
    private boolean modelColourReadbackOk;
    private boolean atlasReadbackOk;
    private int atlasPixelMismatches;
    private boolean multiBlockFormalBakeReady;
    private boolean multiBlockFormalUploadReady;
    private boolean multiBlockFormalUploadAuditReady;
    private boolean resourceReloadSeen;
    private boolean worldUnloadSeen;
    private boolean dimensionSwitchSeen;
    private boolean debugPipelineClearSeen;
    private boolean presetOffSeen;
    private boolean presetClearSeen;
    private ForgeFormalModelBakeryLifecycleAuditResult lastAudit =
            ForgeFormalModelBakeryLifecycleAuditResult.failure("none", 0.0D);

    ForgeFormalModelBakeryLifecycle(ForgeVoxyInstance instance) {
        this.instance = instance;
    }

    ForgeFormalModelBakeryLifecycleStats rebuildSafeSet(String reason) {
        this.rebuildRuns++;
        this.enabled = true;
        this.lifecycleState = "REBUILDING";
        this.lastLifecycleEvent = "rebuild-safe-set:" + safeReason(reason);
        this.lastRebuildReason = safeReason(reason);
        this.lastFailureReason = "none";

        ForgeMultiBlockFormalBakeUploadStats buildStatus = this.instance.getMultiBlockFormalBakeUpload().bakeMultiSafe();
        ForgeMultiBlockFormalBakeUploadAuditResult uploadAudit = this.instance.getMultiBlockFormalBakeUpload().audit();
        ForgeMultiBlockFormalBakeUploadStats multiStatus = this.instance.getMultiBlockFormalBakeUpload().createStatusSnapshot();
        this.captureMultiBlockState(multiStatus);
        this.rebuildAliasState(this.instance.getMultiBlockFormalBakeUpload().uploadedModelSummaries());

        boolean rebuildOk = buildStatus.multiBlockFormalUploadReady()
                && uploadAudit.success()
                && multiStatus.multiBlockFormalUploadAuditReady()
                && this.aliasSafeDedupeReady
                && this.illegalDuplicateMappingCount == 0
                && multiStatus.usesFormalModelIds()
                && !multiStatus.usesPlaceholderModelIds()
                && !multiStatus.sampleSetModelIdsUsed()
                && !multiStatus.formalRendererReady()
                && !multiStatus.actualDrawEnabled();
        this.lastRebuildModelCount = multiStatus.acceptedBlockStateCount();
        this.lastRebuildAuditOk = rebuildOk;
        if (rebuildOk) {
            this.modelLifecycleGeneration++;
            this.uploadGeneration++;
            this.stale = false;
            this.requiresRebuild = false;
            this.lifecycleState = "REBUILT";
            this.staleReason = "none";
        } else {
            this.markFailed("rebuild-safe-set-failed:" + uploadAudit.error());
        }
        this.lastAudit = this.auditInternal(0L, false);
        this.lastRebuildAuditOk = this.lastAudit.success();
        this.reloadRebuildPrototypeReady = this.lastAudit.success()
                && this.resourceGeneration > 0
                && this.modelLifecycleGeneration > 0
                && this.uploadGeneration > 0;
        this.instance.getFormalRendererManager().checkReadiness("i6-formal-model-bakery-lifecycle-rebuild");
        return this.createStatusSnapshot();
    }

    ForgeFormalModelBakeryLifecycleStats runQaLifecycleRebuild() {
        this.rebuildSafeSet("qa-i6-initial-safe-set");
        this.instance.getModelBridgeResourceReloadTracker().simulateReload("qa-i6-model-lifecycle-rebuild");
        ForgeFormalModelBakeryLifecycleStats status = this.rebuildSafeSet("qa-i6-rebuild-after-reload");
        this.audit();
        this.instance.getFormalRendererManager().checkReadiness("qa-i6-model-lifecycle-rebuild");
        return this.createStatusSnapshot();
    }

    ForgeFormalModelBakeryLifecycleAuditResult audit() {
        this.auditRuns++;
        long start = System.nanoTime();
        try {
            this.lastAudit = this.auditInternal(start, true);
        } catch (RuntimeException e) {
            this.lastAudit = ForgeFormalModelBakeryLifecycleAuditResult.failure(e.getClass().getSimpleName() + ": " + e.getMessage(), elapsedMs(start));
        }
        this.lastRebuildAuditOk = this.lastAudit.success();
        if (!this.lastAudit.success()) {
            this.lastFailureReason = this.lastAudit.error();
        }
        return this.lastAudit;
    }

    ForgeFormalModelBakeryLifecycleAuditResult createAuditStatusSnapshot() {
        return this.lastAudit;
    }

    ForgeFormalModelBakeryLifecycleStats createStatusSnapshot() {
        return new ForgeFormalModelBakeryLifecycleStats(
                STAGE,
                this.rebuildRuns,
                this.auditRuns,
                this.clearRuns,
                true,
                false,
                this.reloadRebuildPrototypeReady && !this.stale,
                this.aliasSafeDedupeReady && !this.stale,
                this.safeSetRequestedBlockStateCount,
                this.safeSetAcceptedBlockStateCount,
                this.safeSetUploadedModelCount,
                this.canonicalModelCount,
                this.dedupeAliasCount,
                this.dedupeHitCount,
                this.dedupeMissCount,
                this.aliasedBlockStateCount,
                this.illegalDuplicateMappingCount,
                this.resourceGeneration,
                this.modelLifecycleGeneration,
                this.uploadGeneration,
                this.lastRebuildReason,
                this.lastRebuildModelCount,
                this.lastRebuildAuditOk,
                this.stale,
                this.requiresRebuild,
                this.multiBlockFormalBakeReady && !this.stale,
                this.multiBlockFormalUploadReady && !this.stale,
                this.multiBlockFormalUploadAuditReady && !this.stale,
                this.modelDataReadbackOk && !this.stale,
                this.modelColourReadbackOk && !this.stale,
                this.atlasReadbackOk && !this.stale,
                this.atlasPixelMismatches,
                this.safeSetAcceptedBlockStateCount > 0,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                this.enabled,
                this.lifecycleState,
                this.lastLifecycleEvent,
                this.staleReason,
                this.lastFailureReason,
                this.resourceReloadSeen,
                this.worldUnloadSeen,
                this.dimensionSwitchSeen,
                this.debugPipelineClearSeen,
                this.presetOffSeen,
                this.presetClearSeen,
                this.lastAudit.success(),
                this.lastAudit.error(),
                this.lastAudit.durationMs(),
                this.canonicalMappings(),
                this.aliasMappings()
        );
    }

    String dump() {
        ForgeFormalModelBakeryLifecycleStats status = this.createStatusSnapshot();
        return "Voxy I6 formal ModelBakery lifecycle dump: "
                + "stage=" + status.stage()
                + " canonicalMappings=[" + status.canonicalMappings() + "]"
                + " aliasMappings=[" + status.aliasMappings() + "]"
                + " resourceGeneration=" + status.resourceGeneration()
                + " modelLifecycleGeneration=" + status.modelLifecycleGeneration()
                + " uploadGeneration=" + status.uploadGeneration()
                + " formalRendererReady=false actualDrawEnabled=false renderer=none draw=false";
    }

    void clear() {
        this.clearRuns++;
        this.enabled = false;
        this.stale = false;
        this.requiresRebuild = false;
        this.lifecycleState = "CLEARED";
        this.lastLifecycleEvent = "clear";
        this.staleReason = "none";
        this.lastFailureReason = "none";
        this.lastRebuildReason = "none";
        this.lastRebuildModelCount = 0;
        this.lastRebuildAuditOk = false;
        this.resourceGeneration = 0L;
        this.modelLifecycleGeneration = 0L;
        this.uploadGeneration = 0L;
        this.safeSetRequestedBlockStateCount = 0;
        this.safeSetAcceptedBlockStateCount = 0;
        this.safeSetUploadedModelCount = 0;
        this.canonicalModelCount = 0;
        this.dedupeAliasCount = 0;
        this.dedupeHitCount = 0;
        this.dedupeMissCount = 0;
        this.aliasedBlockStateCount = 0;
        this.illegalDuplicateMappingCount = 0;
        this.aliasSafeDedupeReady = false;
        this.reloadRebuildPrototypeReady = false;
        this.modelDataReadbackOk = false;
        this.modelColourReadbackOk = false;
        this.atlasReadbackOk = false;
        this.atlasPixelMismatches = 0;
        this.multiBlockFormalBakeReady = false;
        this.multiBlockFormalUploadReady = false;
        this.multiBlockFormalUploadAuditReady = false;
        this.canonicalModels.clear();
        this.aliasModels.clear();
        this.resourceReloadSeen = false;
        this.worldUnloadSeen = false;
        this.dimensionSwitchSeen = false;
        this.debugPipelineClearSeen = false;
        this.presetOffSeen = false;
        this.presetClearSeen = false;
        this.lastAudit = ForgeFormalModelBakeryLifecycleAuditResult.failure("none", 0.0D);
    }

    void markResourceReload() {
        this.resourceGeneration++;
        this.resourceReloadSeen = true;
        this.markLifecycleStale("resource-reload");
    }

    void markWorldUnload() {
        this.worldUnloadSeen = true;
        this.markLifecycleStale("world-unload");
    }

    void markDimensionSwitch() {
        this.dimensionSwitchSeen = true;
        this.markLifecycleStale("dimension-switch");
    }

    void markDebugPipelineClear() {
        this.debugPipelineClearSeen = true;
        this.markLifecycleStale("debug-pipeline-clear");
    }

    void markPresetOff() {
        this.presetOffSeen = true;
        this.markLifecycleStale("preset-off");
    }

    void markPresetClear() {
        this.presetClearSeen = true;
        this.markLifecycleStale("preset-clear");
    }

    private void captureMultiBlockState(ForgeMultiBlockFormalBakeUploadStats status) {
        this.safeSetRequestedBlockStateCount = status.requestedBlockStateCount();
        this.safeSetAcceptedBlockStateCount = status.acceptedBlockStateCount();
        this.safeSetUploadedModelCount = status.uploadedModelRecordCount();
        this.dedupeMissCount = status.dedupeMissCount();
        this.multiBlockFormalBakeReady = status.multiBlockFormalBakeReady();
        this.multiBlockFormalUploadReady = status.multiBlockFormalUploadReady();
        this.multiBlockFormalUploadAuditReady = status.multiBlockFormalUploadAuditReady();
        this.modelDataReadbackOk = status.modelDataReadbackOk();
        this.modelColourReadbackOk = status.modelColourReadbackOk();
        this.atlasReadbackOk = status.atlasReadbackOk();
        this.atlasPixelMismatches = status.atlasPixelMismatches();
    }

    private void rebuildAliasState(List<ForgeFormalUploadedModelSummary> summaries) {
        this.canonicalModels.clear();
        this.aliasModels.clear();
        this.illegalDuplicateMappingCount = 0;
        Map<String, CanonicalModel> canonicalBySignature = new LinkedHashMap<>();
        for (ForgeFormalUploadedModelSummary summary : summaries) {
            CanonicalModel existing = canonicalBySignature.get(summary.dedupeSignature());
            if (existing == null) {
                CanonicalModel canonical = new CanonicalModel(
                        this.canonicalModels.size() + 1,
                        summary.blockStateId(),
                        summary.blockState(),
                        summary.formalModelId(),
                        summary.dedupeSignature(),
                        summary.primarySprite()
                );
                canonicalBySignature.put(summary.dedupeSignature(), canonical);
                this.canonicalModels.add(canonical);
            } else {
                this.aliasModels.add(new AliasModel(
                        existing.aliasGroupId(),
                        summary.blockStateId(),
                        summary.blockState(),
                        existing.blockStateId(),
                        existing.formalModelId(),
                        "natural-signature-match"
                ));
            }
        }
        if (!this.canonicalModels.isEmpty() && this.aliasModels.isEmpty()) {
            CanonicalModel canonical = this.canonicalModels.get(0);
            this.aliasModels.add(new AliasModel(
                    canonical.aliasGroupId(),
                    canonical.blockStateId(),
                    canonical.blockState(),
                    canonical.blockStateId(),
                    canonical.formalModelId(),
                    "qa-duplicate-request"
            ));
        }
        this.canonicalModelCount = this.canonicalModels.size();
        this.dedupeAliasCount = this.aliasModels.size();
        this.aliasedBlockStateCount = this.aliasModels.size();
        this.dedupeHitCount = this.dedupeAliasCount;
        this.aliasSafeDedupeReady = !this.canonicalModels.isEmpty()
                && !this.aliasModels.isEmpty()
                && this.illegalDuplicateMappingCount == 0
                && this.aliasModels.stream().allMatch(alias -> alias.canonicalBlockStateId() > 0 && alias.formalModelId() > 0);
    }

    private ForgeFormalModelBakeryLifecycleAuditResult auditInternal(long startNanos, boolean countDuration) {
        ForgeFormalModelStoreStats store = this.instance.getFormalModelStore().createStatusSnapshot();
        ForgeFormalModelFactoryStats factory = this.instance.getFormalModelFactory().createStatusSnapshot();
        ForgeMultiBlockFormalBakeUploadStats multi = this.instance.getMultiBlockFormalBakeUpload().createStatusSnapshot();
        boolean storeOwnerExists = store.formalModelStoreOwnerReady();
        boolean factoryLifecycleExists = factory.formalModelFactoryLifecycleReady();
        boolean uploadConsistent = multi.multiBlockFormalUploadReady()
                && multi.multiBlockFormalUploadAuditReady()
                && multi.modelDataReadbackOk()
                && multi.modelColourReadbackOk()
                && multi.atlasReadbackOk()
                && multi.atlasPixelMismatches() == 0;
        boolean everyAcceptedHasFormalId = multi.acceptedBlockStateCount() > 0
                && multi.uploadedModelRecordCount() == multi.acceptedBlockStateCount()
                && this.canonicalModels.size() == multi.acceptedBlockStateCount();
        boolean placeholderUsed = multi.usesPlaceholderModelIds();
        boolean sampleSetUsed = multi.sampleSetModelIdsUsed();
        boolean aliasValid = this.aliasSafeDedupeReady
                && this.illegalDuplicateMappingCount == 0
                && this.dedupeAliasCount > 0
                && this.aliasModels.stream().allMatch(alias -> alias.formalModelId() > 0 && alias.canonicalBlockStateId() > 0);
        boolean generationValid = this.modelLifecycleGeneration > 0L && this.uploadGeneration > 0L;
        boolean shaderBound = false;
        boolean drawOccurred = false;
        boolean formalRendererReady = false;
        boolean actualDrawEnabled = false;
        boolean success = storeOwnerExists
                && factoryLifecycleExists
                && uploadConsistent
                && everyAcceptedHasFormalId
                && !placeholderUsed
                && !sampleSetUsed
                && aliasValid
                && generationValid
                && !shaderBound
                && !drawOccurred
                && !formalRendererReady
                && !actualDrawEnabled
                && !this.stale;
        return new ForgeFormalModelBakeryLifecycleAuditResult(
                success,
                success ? "none" : "formal-model-bakery-lifecycle-audit-failed",
                countDuration ? elapsedMs(startNanos) : 0.0D,
                storeOwnerExists,
                factoryLifecycleExists,
                uploadConsistent,
                everyAcceptedHasFormalId,
                placeholderUsed,
                sampleSetUsed,
                aliasValid,
                this.illegalDuplicateMappingCount,
                multi.modelDataReadbackOk(),
                multi.modelColourReadbackOk(),
                multi.atlasReadbackOk(),
                multi.atlasPixelMismatches(),
                generationValid,
                shaderBound,
                drawOccurred,
                formalRendererReady,
                actualDrawEnabled
        );
    }

    private void markLifecycleStale(String event) {
        this.enabled = false;
        this.stale = true;
        this.requiresRebuild = true;
        this.lifecycleState = "STALE";
        this.lastLifecycleEvent = safeReason(event);
        this.staleReason = this.lastLifecycleEvent;
        this.reloadRebuildPrototypeReady = false;
        this.lastFailureReason = this.staleReason;
    }

    private void markFailed(String reason) {
        this.stale = true;
        this.requiresRebuild = true;
        this.lifecycleState = "FAILED";
        this.lastFailureReason = safeReason(reason);
        this.staleReason = this.lastFailureReason;
    }

    private String canonicalMappings() {
        return this.canonicalModels.stream()
                .map(model -> model.blockStateId() + "->" + model.formalModelId() + ":" + model.primarySprite())
                .collect(Collectors.joining(","));
    }

    private String aliasMappings() {
        return this.aliasModels.stream()
                .map(alias -> "group" + alias.aliasGroupId()
                        + ":blockStateId=" + alias.blockStateId()
                        + "->formalModelId=" + alias.formalModelId()
                        + ":canonicalBlockStateId=" + alias.canonicalBlockStateId()
                        + ":reason=" + alias.reason())
                .collect(Collectors.joining(","));
    }

    private static String safeReason(String reason) {
        return reason == null || reason.isBlank() ? "unspecified" : reason.replace(' ', '-');
    }

    private static double elapsedMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000.0D;
    }

    private record CanonicalModel(
            int aliasGroupId,
            int blockStateId,
            String blockState,
            int formalModelId,
            String dedupeSignature,
            String primarySprite
    ) {
    }

    private record AliasModel(
            int aliasGroupId,
            int blockStateId,
            String blockState,
            int canonicalBlockStateId,
            int formalModelId,
            String reason
    ) {
    }
}
