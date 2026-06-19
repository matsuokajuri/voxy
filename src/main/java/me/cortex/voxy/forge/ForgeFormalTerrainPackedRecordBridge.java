package me.cortex.voxy.forge;

import com.mojang.blaze3d.systems.RenderSystem;
import me.cortex.voxy.common.world.WorldEngine;
import me.cortex.voxy.common.world.service.VoxelIngestService;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.stream.Collectors;

/**
 * Deprecated bridge route: kept only as historical proof that real terrain
 * records could be mapped. New formal work must generate packed records
 * through the original Voxy RenderDataFactory-style path, not this bridge.
 */
@Deprecated(forRemoval = false)
final class ForgeFormalTerrainPackedRecordBridge {
    static final String STAGE = "J5_REAL_TERRAIN_PACKED_RECORD_FORMAL_MODEL_ID_BRIDGE";
    private static final int SEARCH_RADIUS_CHUNKS = 2;

    private final ForgeVoxyInstance instance;
    private long buildRuns;
    private long auditRuns;
    private long clearRuns;
    private long auditFailures;
    private boolean bridgeReady;
    private boolean stale;
    private boolean requiresRebuild;
    private String lifecycleState = "UNINITIALIZED";
    private String lastLifecycleEvent = "initialized";
    private String staleReason = "none";
    private String lastFailureReason = "none";
    private String sourceKind = "none";
    private String sourceWorldPosition = "none";
    private String sourceSectionPosition = "none";
    private int sourceRecordsScanned;
    private int sourceRecordsAccepted;
    private int sourceRecordsRejected;
    private int recordsRejectedNoBlockState;
    private int recordsRejectedNoFormalModelId;
    private int recordsRejectedUnsafeModelId;
    private int recordsRejectedUnsupportedBlock;
    private boolean blockStateSourceAvailable;
    private boolean formalModelIdLookupOk;
    private boolean formalModelIdsBackedByRealBake;
    private boolean temporaryModelIdRewriteOk;
    private boolean originalRecordUnchanged;
    private boolean originalGeometryUntouched = true;
    private boolean originalGeometryHeapUntouched = true;
    private String lastGlError = "none";
    private AcceptedRecord firstAcceptedRecord;
    private ForgeFormalTerrainPackedRecordBridgeAuditResult lastAudit =
            ForgeFormalTerrainPackedRecordBridgeAuditResult.failure("none", 0.0D);

    ForgeFormalTerrainPackedRecordBridge(ForgeVoxyInstance instance) {
        this.instance = instance;
    }

    ForgeFormalTerrainPackedRecordBridgeStats build() {
        this.buildRuns++;
        this.lifecycleState = "BUILDING";
        this.lastLifecycleEvent = "build";
        this.staleReason = "none";
        this.lastFailureReason = "none";
        this.resetBridgeFlags();

        if (!RenderSystem.isOnRenderThread()) {
            this.fail("not-render-thread");
            return this.createStatusSnapshot();
        }

        boolean worldReady = this.instance.ensureActiveWorldSkeletonForCurrentWorldIfAllowed();
        if (!worldReady) {
            this.fail("world-engine-skeleton-not-ready");
            this.audit();
            return this.createStatusSnapshot();
        }

        ForgeFormalModelBakeryLifecycleStats lifecycle = this.instance.getFormalModelBakeryLifecycle().runQaLifecycleRebuild();
        if (!lifecycle.reloadRebuildPrototypeReady()
                || !lifecycle.multiBlockFormalUploadAuditReady()
                || lifecycle.stale()
                || lifecycle.requiresRebuild()) {
            this.fail("i6-safe-set-not-ready:" + lifecycle.lastFailureReason());
            this.audit();
            this.instance.getFormalRendererManager().checkReadiness("j5-terrain-record-bridge-i6-failed");
            return this.createStatusSnapshot();
        }

        if (!this.prepareRealTerrainBuiltSections()) {
            if ("none".equals(this.lastFailureReason)) {
                this.fail("no-real-terrain-built-section-records-mapped");
            }
            this.audit();
            this.instance.getFormalRendererManager().checkReadiness("j5-terrain-record-bridge-no-real-records");
            return this.createStatusSnapshot();
        }

        ForgeFormalPackedQuadPreviewStats preview = this.instance.getFormalPackedQuadPreview().build();
        this.capturePreviewStatus(preview);
        this.originalRecordUnchanged = this.verifyOriginalRecordUnchanged();

        this.bridgeReady = preview.formalPackedQuadPreviewReady()
                && preview.packedQuadShaderPreviewReady()
                && preview.realTerrainRecordsUsed()
                && !preview.syntheticFallbackUsed()
                && preview.sourceRecordsAccepted() >= 1
                && preview.temporaryFormalQuadBufferCreated()
                && preview.temporaryFormalQuadCount() >= 1
                && preview.previewReadbackOk()
                && preview.previewPixelMismatches() == 0
                && preview.usesFormalModelIds()
                && !preview.usesPlaceholderModelIds()
                && !preview.sampleSetModelIdsUsed()
                && !preview.sampleSetBridgeUsedAsFormalSource()
                && this.sourceRecordsAccepted >= 1
                && this.blockStateSourceAvailable
                && this.formalModelIdLookupOk
                && this.formalModelIdsBackedByRealBake
                && this.temporaryModelIdRewriteOk
                && this.originalRecordUnchanged
                && this.originalGeometryUntouched
                && this.originalGeometryHeapUntouched
                && !preview.terrainDrawStarted()
                && !preview.formalRendererDrawStarted()
                && !preview.actualRendererDrawEnabled()
                && !preview.formalRendererReady();
        if (this.bridgeReady) {
            this.stale = false;
            this.requiresRebuild = false;
            this.lifecycleState = "BUILT";
            this.lastLifecycleEvent = "build-complete";
            this.lastFailureReason = "none";
        } else if ("none".equals(this.lastFailureReason)) {
            this.fail("real-terrain-record-bridge-incomplete:" + preview.lastFailureReason());
        }
        this.audit();
        this.instance.getFormalRendererManager().checkReadiness("j5-real-terrain-record-bridge-build");
        return this.createStatusSnapshot();
    }

    ForgeFormalTerrainPackedRecordBridgeAuditResult audit() {
        this.auditRuns++;
        long start = System.nanoTime();
        try {
            this.lastAudit = this.auditInternal(start);
        } catch (RuntimeException e) {
            this.lastAudit = ForgeFormalTerrainPackedRecordBridgeAuditResult.failure(e.getClass().getSimpleName() + ": " + e.getMessage(), elapsedMs(start));
        }
        if (!this.lastAudit.success()) {
            this.auditFailures++;
            this.lastFailureReason = this.lastAudit.error();
        }
        return this.lastAudit;
    }

    ForgeFormalTerrainPackedRecordBridgeAuditResult createAuditStatusSnapshot() {
        return this.lastAudit;
    }

    ForgeFormalTerrainPackedRecordBridgeStats createStatusSnapshot() {
        ForgeFormalPackedQuadPreviewStats preview = this.instance.getFormalPackedQuadPreview().createStatusSnapshot();
        boolean ready = this.bridgeReady
                && !this.stale
                && preview.formalPackedQuadPreviewReady()
                && preview.realTerrainRecordsUsed()
                && !preview.syntheticFallbackUsed();
        return new ForgeFormalTerrainPackedRecordBridgeStats(
                STAGE,
                this.buildRuns,
                this.auditRuns,
                this.clearRuns,
                this.auditFailures,
                ready,
                ready,
                preview.packedQuadShaderPreviewReady(),
                false,
                false,
                true,
                preview.offscreenPreviewReady(),
                false,
                false,
                false,
                this.sourceKind,
                this.sourceWorldPosition,
                this.sourceSectionPosition,
                this.sourceRecordsScanned,
                this.sourceRecordsAccepted,
                this.sourceRecordsRejected,
                this.recordsRejectedNoBlockState,
                this.recordsRejectedNoFormalModelId,
                this.recordsRejectedUnsafeModelId,
                this.recordsRejectedUnsupportedBlock,
                preview.realTerrainRecordsUsed(),
                preview.syntheticFallbackUsed(),
                preview.syntheticFallbackReason(),
                this.blockStateSourceAvailable,
                this.formalModelIdLookupOk,
                this.formalModelIdsBackedByRealBake,
                this.temporaryModelIdRewriteOk,
                this.originalRecordUnchanged,
                this.originalGeometryUntouched,
                this.originalGeometryHeapUntouched,
                preview.temporaryFormalQuadBufferCreated(),
                ready && preview.temporaryFormalQuadBufferCreated(),
                preview.temporaryFormalQuadCount(),
                preview.temporaryFormalVertexCount(),
                preview.temporaryFormalModelIds(),
                preview.usesFormalModelIds(),
                false,
                false,
                false,
                preview.quadRecordDecodeOk(),
                preview.modelIdDecodeOk(),
                preview.faceDecodeOk(),
                preview.faceDataUsed(),
                preview.atlasSampleUsed(),
                preview.modelColourUsed(),
                preview.previewFramebufferComplete(),
                preview.previewReadbackOk(),
                preview.previewPixelMismatches(),
                preview.previewChecksumCount(),
                preview.previewChecksums(),
                false,
                false,
                false,
                false,
                false,
                false,
                this.stale,
                this.requiresRebuild,
                this.lifecycleState,
                this.lastLifecycleEvent,
                this.staleReason,
                this.lastGlError,
                this.lastFailureReason,
                this.lastAudit.success(),
                this.lastAudit.error(),
                this.lastAudit.durationMs()
        );
    }

    String dump() {
        ForgeFormalTerrainPackedRecordBridgeStats status = this.createStatusSnapshot();
        return "J5 real terrain packed-record formal model-id bridge: "
                + "stage=" + status.stage()
                + " sourceKind=" + status.sourceKind()
                + " sourceWorldPosition=" + status.sourceWorldPosition()
                + " sourceSectionPosition=" + status.sourceSectionPosition()
                + " sourceRecordsScanned=" + status.sourceRecordsScanned()
                + " sourceRecordsAccepted=" + status.sourceRecordsAccepted()
                + " sourceRecordsRejected=" + status.sourceRecordsRejected()
                + " realTerrainRecordsUsed=" + status.realTerrainRecordsUsed()
                + " syntheticFallbackUsed=" + status.syntheticFallbackUsed()
                + " temporaryFormalQuadCount=" + status.temporaryFormalQuadCount()
                + " temporaryFormalModelIds=" + status.temporaryFormalModelIds()
                + " previewChecksums=" + status.previewChecksums()
                + " originalRecordUnchanged=" + status.originalRecordUnchanged()
                + " originalGeometryHeapUntouched=" + status.originalGeometryHeapUntouched()
                + " terrainDrawStarted=false formalRendererDrawStarted=false actualRendererDrawEnabled=false"
                + " formalTexturedShaderReady=false formalRendererReady=false lastFailureReason=" + status.lastFailureReason();
    }

    void clear() {
        this.clearRuns++;
        this.bridgeReady = false;
        this.stale = false;
        this.requiresRebuild = false;
        this.lifecycleState = "CLEARED";
        this.lastLifecycleEvent = "clear";
        this.staleReason = "none";
        this.lastFailureReason = "none";
        this.resetBridgeFlags();
        this.lastAudit = ForgeFormalTerrainPackedRecordBridgeAuditResult.failure("none", 0.0D);
        this.instance.getFormalPackedQuadPreview().clear();
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

    void markStale(String reason) {
        this.bridgeReady = false;
        this.stale = true;
        this.requiresRebuild = true;
        this.lifecycleState = "STALE";
        this.lastLifecycleEvent = safeReason(reason);
        this.staleReason = this.lastLifecycleEvent;
        this.lastFailureReason = this.staleReason;
    }

    private boolean prepareRealTerrainBuiltSections() {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null || minecraft.player == null) {
            this.fail("no-active-client-world");
            return false;
        }
        Optional<WorldEngine> engine = this.instance.getCurrentEngineOptional();
        if (engine.isEmpty()) {
            this.fail("no-active-world-engine");
            return false;
        }

        List<ForgeFormalUploadedModelSummary> summaries = this.instance.getMultiBlockFormalBakeUpload().uploadedModelSummaries();
        if (summaries.isEmpty()) {
            this.fail("i6-uploaded-model-summaries-empty");
            return false;
        }
        Map<Integer, ForgeFormalUploadedModelSummary> summariesByBlockState = summaries.stream()
                .collect(Collectors.toMap(ForgeFormalUploadedModelSummary::blockStateId, summary -> summary, (a, b) -> a, LinkedHashMap::new));

        String dimension = level.dimension().location().toString();
        BlockPos playerPos = minecraft.player.blockPosition();
        this.sourceWorldPosition = playerPos.getX() + "," + playerPos.getY() + "," + playerPos.getZ();
        int centerChunkX = minecraft.player.chunkPosition().x;
        int centerChunkZ = minecraft.player.chunkPosition().z;
        this.sourceKind = "current-world-built-section";
        this.instance.getVoxyGeometryCache().setActiveDimension(dimension);

        for (int radius = 0; radius <= SEARCH_RADIUS_CHUNKS && this.sourceRecordsAccepted == 0; radius++) {
            for (int dx = -radius; dx <= radius && this.sourceRecordsAccepted == 0; dx++) {
                for (int dz = -radius; dz <= radius && this.sourceRecordsAccepted == 0; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) {
                        continue;
                    }
                    this.buildAndScanLoadedChunk(engine.get(), level, dimension, centerChunkX + dx, centerChunkZ + dz, summariesByBlockState);
                }
            }
        }
        if (this.sourceRecordsAccepted == 0 && "none".equals(this.lastFailureReason)) {
            this.fail(this.sourceRecordsScanned == 0 ? "no-real-built-section-records-produced" : "no-real-built-section-records-mapped-to-formal-ids");
        }
        return this.sourceRecordsAccepted > 0;
    }

    private void buildAndScanLoadedChunk(
            WorldEngine engine,
            ClientLevel level,
            String dimension,
            int chunkX,
            int chunkZ,
            Map<Integer, ForgeFormalUploadedModelSummary> summariesByBlockState
    ) {
        LevelChunk chunk = getLoadedChunk(level, chunkX, chunkZ);
        if (chunk == null) {
            return;
        }

        ForgeCpuMeshBuildResult cpuResult = null;
        try {
            VoxelIngestService.ingestChunkWithStats(engine, chunk);
            cpuResult = ForgeCpuMeshBuilder.buildCurrentChunk(engine, chunk, level, dimension);
            if (cpuResult.stats().sectionsFound() == 0 || cpuResult.sections().isEmpty()) {
                return;
            }
            ForgeVoxyBuiltSectionBuildResult builtResult = ForgeVoxyBuiltSectionBuilder.fromCpuMesh(cpuResult);
            if (builtResult.sections().isEmpty()) {
                return;
            }
            if ("none".equals(this.sourceSectionPosition)) {
                this.sourceSectionPosition = builtResult.stats().samplePosition();
            }
            this.instance.getVoxyGeometryCache().putAll(builtResult.sections());
            this.scanBuiltSections(builtResult.sections(), summariesByBlockState);
        } catch (RuntimeException e) {
            this.lastFailureReason = "terrain-source-build-error:" + e.getClass().getSimpleName() + ":" + safeReason(e.getMessage());
        } finally {
            closeTemporaryCpuMesh(cpuResult);
        }
    }

    private void scanBuiltSections(List<ForgeVoxyBuiltSection> sections, Map<Integer, ForgeFormalUploadedModelSummary> summariesByBlockState) {
        for (ForgeVoxyBuiltSection section : sections) {
            ForgeVoxyGeometryBuffer buffer = section.geometryBuffer();
            if (buffer == null || buffer.isClosed()) {
                continue;
            }
            for (long record : buffer.packedQuads()) {
                this.sourceRecordsScanned++;
                int legacyModelId = ForgeVoxyQuadEncoder.extractModelId(record);
                OptionalInt blockStateId = ForgeVoxyModelIdMapper.INSTANCE.blockStateIdForModelId(legacyModelId);
                if (blockStateId.isEmpty()) {
                    this.recordsRejectedNoBlockState++;
                    this.sourceRecordsRejected++;
                    continue;
                }
                ForgeFormalUploadedModelSummary summary = summariesByBlockState.get(blockStateId.getAsInt());
                if (summary == null) {
                    this.recordsRejectedUnsupportedBlock++;
                    this.sourceRecordsRejected++;
                    continue;
                }
                Optional<ForgeFormalModelIdMapping> mapping = this.instance.getFormalModelFactory().mappingForBlockStateId(blockStateId.getAsInt());
                if (mapping.isEmpty()) {
                    this.recordsRejectedNoFormalModelId++;
                    this.sourceRecordsRejected++;
                    continue;
                }
                int formalModelId = summary.formalModelId();
                if (mapping.get().formalModelId() != formalModelId || !ForgeModelAtlasLayout.isValidModelId(formalModelId)) {
                    this.recordsRejectedUnsafeModelId++;
                    this.sourceRecordsRejected++;
                    continue;
                }
                long formalRecord = ForgeVoxyQuadEncoder.replaceModelId(record, formalModelId);
                boolean rewriteOk = ForgeVoxyQuadEncoder.extractModelId(formalRecord) == formalModelId;
                this.temporaryModelIdRewriteOk = this.temporaryModelIdRewriteOk || rewriteOk;
                this.blockStateSourceAvailable = true;
                this.formalModelIdLookupOk = true;
                this.formalModelIdsBackedByRealBake = true;
                this.sourceRecordsAccepted++;
                if (this.firstAcceptedRecord == null) {
                    this.firstAcceptedRecord = new AcceptedRecord(
                            section.dimension(),
                            section.position(),
                            record,
                            formalRecord,
                            blockStateId.getAsInt(),
                            legacyModelId,
                            formalModelId
                    );
                }
            }
        }
    }

    private boolean verifyOriginalRecordUnchanged() {
        if (this.firstAcceptedRecord == null) {
            return false;
        }
        ForgeVoxyBuiltSection section = this.instance.getVoxyGeometryCache().findLiveSection(this.firstAcceptedRecord.dimension(), this.firstAcceptedRecord.sectionPosition());
        if (section == null || section.geometryBuffer() == null || section.geometryBuffer().isClosed()) {
            return false;
        }
        boolean originalFound = false;
        boolean rewrittenFound = false;
        for (long record : section.geometryBuffer().packedQuads()) {
            if (record == this.firstAcceptedRecord.originalRecord()) {
                originalFound = true;
            }
            if (record == this.firstAcceptedRecord.formalRecord() && record != this.firstAcceptedRecord.originalRecord()) {
                rewrittenFound = true;
            }
        }
        return originalFound && !rewrittenFound;
    }

    private void capturePreviewStatus(ForgeFormalPackedQuadPreviewStats preview) {
        this.lastGlError = preview.lastGlError();
        this.originalGeometryUntouched = preview.originalGeometryUntouched();
        this.originalGeometryHeapUntouched = true;
        if (preview.sourceRecordsScanned() > this.sourceRecordsScanned) {
            this.sourceRecordsScanned = preview.sourceRecordsScanned();
        }
        if (preview.sourceRecordsAccepted() > this.sourceRecordsAccepted) {
            this.sourceRecordsAccepted = preview.sourceRecordsAccepted();
        }
        if (preview.sourceRecordsRejected() > this.sourceRecordsRejected) {
            this.sourceRecordsRejected = preview.sourceRecordsRejected();
        }
        if (preview.recordsRejectedNoBlockState() > this.recordsRejectedNoBlockState) {
            this.recordsRejectedNoBlockState = preview.recordsRejectedNoBlockState();
        }
        if (preview.recordsRejectedNoFormalModelId() > this.recordsRejectedNoFormalModelId) {
            this.recordsRejectedNoFormalModelId = preview.recordsRejectedNoFormalModelId();
        }
        if (preview.recordsRejectedUnsafeModelId() > this.recordsRejectedUnsafeModelId) {
            this.recordsRejectedUnsafeModelId = preview.recordsRejectedUnsafeModelId();
        }
    }

    private ForgeFormalTerrainPackedRecordBridgeAuditResult auditInternal(long startNanos) {
        ForgeFormalModelBakeryLifecycleStats lifecycle = this.instance.getFormalModelBakeryLifecycle().createStatusSnapshot();
        ForgeFormalPackedQuadPreviewStats preview = this.instance.getFormalPackedQuadPreview().createStatusSnapshot();
        boolean mappingOk = this.sourceRecordsAccepted > 0 && this.blockStateSourceAvailable && this.formalModelIdLookupOk;
        boolean success = this.bridgeReady
                && !this.stale
                && lifecycle.reloadRebuildPrototypeReady()
                && preview.formalPackedQuadPreviewReady()
                && preview.realTerrainRecordsUsed()
                && !preview.syntheticFallbackUsed()
                && mappingOk
                && this.formalModelIdsBackedByRealBake
                && !preview.usesPlaceholderModelIds()
                && !preview.sampleSetModelIdsUsed()
                && !preview.sampleSetBridgeUsedAsFormalSource()
                && this.originalRecordUnchanged
                && this.originalGeometryUntouched
                && this.originalGeometryHeapUntouched
                && preview.temporaryFormalQuadBufferCreated()
                && preview.previewFramebufferComplete()
                && preview.previewReadbackOk()
                && preview.previewPixelMismatches() == 0
                && !preview.terrainDrawStarted()
                && !preview.formalRendererDrawStarted()
                && !preview.actualRendererDrawEnabled()
                && !preview.formalRendererReady();
        return new ForgeFormalTerrainPackedRecordBridgeAuditResult(
                success,
                success ? "none" : "j5-real-terrain-record-bridge-audit-failed",
                elapsedMs(startNanos),
                lifecycle.reloadRebuildPrototypeReady(),
                preview.formalPackedQuadPreviewReady() || preview.packedQuadShaderPreviewReady(),
                preview.realTerrainRecordsUsed(),
                preview.syntheticFallbackUsed(),
                mappingOk,
                this.formalModelIdsBackedByRealBake,
                preview.usesPlaceholderModelIds(),
                preview.sampleSetModelIdsUsed(),
                preview.sampleSetBridgeUsedAsFormalSource(),
                this.originalRecordUnchanged,
                this.originalGeometryUntouched,
                this.originalGeometryHeapUntouched,
                preview.temporaryFormalQuadBufferCreated(),
                preview.offscreenPreviewReady(),
                preview.previewFramebufferComplete(),
                preview.previewReadbackOk(),
                preview.previewPixelMismatches(),
                preview.previewChecksumCount(),
                preview.terrainDrawStarted(),
                preview.formalRendererDrawStarted(),
                preview.actualRendererDrawEnabled(),
                preview.formalRendererReady()
        );
    }

    private void resetBridgeFlags() {
        this.bridgeReady = false;
        this.stale = false;
        this.requiresRebuild = false;
        this.sourceKind = "none";
        this.sourceWorldPosition = "none";
        this.sourceSectionPosition = "none";
        this.sourceRecordsScanned = 0;
        this.sourceRecordsAccepted = 0;
        this.sourceRecordsRejected = 0;
        this.recordsRejectedNoBlockState = 0;
        this.recordsRejectedNoFormalModelId = 0;
        this.recordsRejectedUnsafeModelId = 0;
        this.recordsRejectedUnsupportedBlock = 0;
        this.blockStateSourceAvailable = false;
        this.formalModelIdLookupOk = false;
        this.formalModelIdsBackedByRealBake = false;
        this.temporaryModelIdRewriteOk = false;
        this.originalRecordUnchanged = false;
        this.originalGeometryUntouched = true;
        this.originalGeometryHeapUntouched = true;
        this.lastGlError = "none";
        this.firstAcceptedRecord = null;
    }

    private void fail(String reason) {
        this.bridgeReady = false;
        this.lifecycleState = "FAILED";
        this.lastFailureReason = safeReason(reason);
    }

    private static LevelChunk getLoadedChunk(ClientLevel level, int chunkX, int chunkZ) {
        if (!level.hasChunk(chunkX, chunkZ)) {
            return null;
        }
        return level.getChunkSource().getChunk(chunkX, chunkZ, ChunkStatus.FULL, false);
    }

    private static void closeTemporaryCpuMesh(ForgeCpuMeshBuildResult cpuResult) {
        if (cpuResult == null) {
            return;
        }
        for (ForgeCpuBuiltSection section : cpuResult.sections()) {
            if (section != null) {
                section.close();
            }
        }
    }

    private static String safeReason(String reason) {
        return reason == null || reason.isBlank() ? "unspecified" : reason.replace(' ', '-');
    }

    private static double elapsedMs(long startNanos) {
        return startNanos == 0L ? 0.0D : (System.nanoTime() - startNanos) / 1_000_000.0D;
    }

    private record AcceptedRecord(
            String dimension,
            long sectionPosition,
            long originalRecord,
            long formalRecord,
            int blockStateId,
            int legacyModelId,
            int formalModelId
    ) {
    }
}
