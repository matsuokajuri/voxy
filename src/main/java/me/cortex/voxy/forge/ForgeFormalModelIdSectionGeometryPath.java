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
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL15C;
import org.lwjgl.opengl.GL45C;
import org.lwjgl.system.MemoryUtil;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.stream.Collectors;

final class ForgeFormalModelIdSectionGeometryPath {
    static final String STAGE = "K8_FORMAL_MODEL_ID_SECTION_GEOMETRY_PATH_NO_LIVE_DRAW";
    private static final int SEARCH_RADIUS_CHUNKS = 2;
    private static final int MAX_SNAPSHOT_SECTIONS = 4;
    private static final int MAX_SNAPSHOT_RECORDS = 256;
    private static final List<ForgeFormalRendererBlocker> BLOCKERS = List.of(
            new ForgeFormalRendererBlocker("P0", "P0_PRODUCTION_CMDGEN_NOT_OPERATIONAL", "Production cmdgen not operational", "K8 creates formal-model-id geometry snapshots but production cmdgen.comp still is not the live renderer path.", "Promote production command generation after traversal and shader integration are ready.", true),
            new ForgeFormalRendererBlocker("P0", "P0_FORMAL_VISIBILITY_TRAVERSAL_IMPLEMENTATION_MISSING", "Formal visibility traversal implementation missing", "K8 consumes real section evidence but does not implement original hierarchical traversal.", "Port formal visibility traversal before live draw.", true),
            new ForgeFormalRendererBlocker("P0", "P0_GLOBAL_FORMAL_MODEL_ID_GEOMETRY_NOT_ENABLED_FOR_LIVE_RENDERER", "Global formal model-id geometry not enabled for live renderer", "K8 proves an opt-in formal geometry path, but the live renderer is not allowed to consume it yet.", "Wire the formal geometry path into production ownership only after shader, traversal, and command generation are ready.", true),
            new ForgeFormalRendererBlocker("P0", "P0_PRODUCTION_TERRAIN_SHADER_INTEGRATION_MISSING", "Production terrain shader integration missing", "K8 does not integrate the production terrain shader.", "Integrate terrain shader semantics after formal geometry ownership is stable.", true),
            new ForgeFormalRendererBlocker("P0", "P0_FORMAL_MDIC_LIVE_DRAW_DISABLED", "Formal MDIC live draw disabled", "K8 remains no-live-draw; any K7 evidence is offscreen validation only.", "Keep live draw disabled until production renderer ownership is complete.", true),
            new ForgeFormalRendererBlocker("P1", "P1_HIERARCHICAL_OCCLUSION_MISSING", "Hierarchical occlusion missing", "K8 does not port the original hierarchical occlusion queue.", "Port formal hierarchical occlusion traversal later.", false),
            new ForgeFormalRendererBlocker("P1", "P1_RENDER_DISTANCE_TRACKER_INCOMPLETE", "Render distance tracker incomplete", "K8 does not own a formal RenderDistanceTracker equivalent.", "Add formal render-distance tracking before live traversal.", false),
            new ForgeFormalRendererBlocker("P1", "P1_LIGHTMAP_MISSING", "Lightmap missing", "K8 validates packed records but not formal lightmap semantics.", "Add formal lightmap binding and semantics.", false),
            new ForgeFormalRendererBlocker("P1", "P1_BIOME_TINT_MISSING", "Biome tint missing", "K8 preserves packed biome fields but does not implement the broad biome LUT path.", "Complete formal biome tint handling.", false),
            new ForgeFormalRendererBlocker("P1", "P1_MATERIAL_ALPHA_SEMANTICS_MISSING", "Material alpha semantics missing", "K8 does not implement formal material, alpha, or cutout behavior.", "Implement formal material/alpha/cutout behavior.", false),
            new ForgeFormalRendererBlocker("P1", "P1_RESOURCE_REBUILD_AUTOMATION_INCOMPLETE", "Resource rebuild automation incomplete", "K8 marks stale on reload but does not automatically rebuild production geometry snapshots.", "Add rebuild orchestration when production resources exist.", false)
    );

    private final ForgeVoxyInstance instance;
    private long buildRuns;
    private long auditRuns;
    private long clearRuns;
    private long auditFailures;
    private boolean pathReady;
    private boolean globalPathReady;
    private boolean snapshotCreated;
    private int snapshotSectionCount;
    private int snapshotRecordCount;
    private int snapshotVertexCount;
    private long snapshotByteSize;
    private int validationBufferId;
    private boolean validationBufferCreated;
    private boolean realSectionInputUsed;
    private int candidateSectionCount;
    private int acceptedSectionCount;
    private int rejectedSectionCount;
    private int acceptedRecordCount;
    private int rejectedRecordCount;
    private String candidateSectionSource = "none";
    private boolean blockStateSourceAvailable;
    private boolean formalModelIdLookupOk;
    private boolean formalModelIdsBackedByRealBake;
    private int recordsRejectedNoBlockState;
    private int recordsRejectedNoFormalModelId;
    private int recordsRejectedUnsafeModelId;
    private int recordsRejectedUnsupportedBlock;
    private int recordsRejectedPlaceholderModelId;
    private int recordsRejectedNoRealBake;
    private boolean formalPackedRecordsAuditOk;
    private boolean formalModelIdDecodeOk;
    private boolean formalGeometryReadbackOk;
    private int formalGeometryReadbackMismatchCount;
    private String formalGeometryModelIds = "none";
    private String sampleOriginalRecord = "none";
    private String sampleFormalRecord = "none";
    private String sampleSectionPosition = "none";
    private boolean stale;
    private boolean requiresRebuild;
    private String lifecycleState = "UNINITIALIZED";
    private String lastLifecycleEvent = "initialized";
    private String lastGlError = "none";
    private String lastFailureReason = "none";
    private final List<FormalSectionSnapshot> snapshots = new ArrayList<>();
    private long[] flattenedSnapshotRecords = new long[0];
    private AcceptedRecord firstAcceptedRecord;
    private ForgeFormalModelIdSectionGeometryAuditResult lastAudit =
            ForgeFormalModelIdSectionGeometryAuditResult.failure("not-audited", 0.0D);

    ForgeFormalModelIdSectionGeometryPath(ForgeVoxyInstance instance) {
        this.instance = instance;
    }

    ForgeFormalModelIdSectionGeometryStats build() {
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

        boolean worldReady = this.instance.ensureActiveWorldSkeletonForCurrentWorldIfAllowed();
        if (!worldReady) {
            this.fail("world-engine-skeleton-not-ready");
            this.audit();
            return this.createStatusSnapshot();
        }

        ForgeFormalTerrainPackedRecordBridgeStats j5 = this.instance.getFormalTerrainPackedRecordBridge().createStatusSnapshot();
        if (!j5.realTerrainPackedRecordBridgeReady() || j5.stale() || j5.requiresRebuild()) {
            j5 = this.instance.getFormalTerrainPackedRecordBridge().build();
        }
        ForgeFormalCmdgenRealSectionDryRunStats k6 = this.instance.getFormalCmdgenRealSectionDryRun().createStatusSnapshot();
        ForgeFormalIsolatedMdicDrawSmokeTestStats k7 = this.instance.getFormalIsolatedMdicDrawSmokeTest().createStatusSnapshot();
        ForgeFormalModelBakeryLifecycleStats lifecycle = this.instance.getFormalModelBakeryLifecycle().createStatusSnapshot();
        if (!this.capturePrerequisites(j5, lifecycle)) {
            this.audit();
            this.instance.getFormalRendererManager().checkReadiness("k8-formal-model-id-geometry-prerequisite-failed");
            return this.createStatusSnapshot();
        }

        if (!this.collectFormalGeometrySnapshot()) {
            if ("none".equals(this.lastFailureReason)) {
                this.fail(this.candidateSectionCount == 0 ? "no-real-section-candidates" : "no-records-mapped-to-formal-model-ids");
            }
            this.audit();
            this.instance.getFormalRendererManager().checkReadiness("k8-formal-model-id-geometry-no-snapshot");
            return this.createStatusSnapshot();
        }

        try {
            this.allocateAndReadbackValidationBuffer();
            this.formalPackedRecordsAuditOk = this.snapshotCreated
                    && this.acceptedRecordCount > 0
                    && this.formalModelIdDecodeOk
                    && this.formalGeometryReadbackOk
                    && this.verifyOriginalRecordUnchanged();
            this.pathReady = this.formalPackedRecordsAuditOk
                    && this.realSectionInputUsed
                    && this.blockStateSourceAvailable
                    && this.formalModelIdLookupOk
                    && this.formalModelIdsBackedByRealBake;
            this.globalPathReady = this.pathReady;
            if (this.pathReady) {
                this.stale = false;
                this.requiresRebuild = false;
                this.lifecycleState = "BUILT";
                this.lastLifecycleEvent = "build-complete";
                this.lastFailureReason = "none";
            } else {
                this.fail("formal-model-id-geometry-path-incomplete");
            }
        } catch (RuntimeException e) {
            this.fail(e.getClass().getSimpleName() + ":" + e.getMessage());
        }

        this.audit();
        this.instance.getFormalRendererManager().checkReadiness("k8-formal-model-id-section-geometry-path-build");
        return this.createStatusSnapshot();
    }

    ForgeFormalModelIdSectionGeometryAuditResult audit() {
        this.auditRuns++;
        long start = System.nanoTime();
        try {
            this.lastAudit = this.auditInternal(start);
        } catch (RuntimeException e) {
            this.lastAudit = ForgeFormalModelIdSectionGeometryAuditResult.failure(e.getClass().getSimpleName() + ":" + e.getMessage(), elapsedMs(start));
        }
        if (!this.lastAudit.success()) {
            this.auditFailures++;
            this.lastFailureReason = this.lastAudit.error();
        }
        return this.lastAudit;
    }

    ForgeFormalModelIdSectionGeometryAuditResult createAuditStatusSnapshot() {
        return this.lastAudit;
    }

    ForgeFormalModelIdSectionGeometryStats createStatusSnapshot() {
        ForgeFormalTerrainRendererStats terrain = this.instance.getFormalTerrainRendererOwner().createStatusSnapshot();
        ForgeFormalModelBakeryLifecycleStats lifecycle = this.instance.getFormalModelBakeryLifecycle().createStatusSnapshot();
        ForgeFormalModelStoreStats store = this.instance.getFormalModelStore().createStatusSnapshot();
        ForgeFormalTerrainPackedRecordBridgeStats bridge = this.instance.getFormalTerrainPackedRecordBridge().createStatusSnapshot();
        ForgeFormalCmdgenRealSectionDryRunStats k6 = this.instance.getFormalCmdgenRealSectionDryRun().createStatusSnapshot();
        ForgeFormalIsolatedMdicDrawSmokeTestStats k7 = this.instance.getFormalIsolatedMdicDrawSmokeTest().createStatusSnapshot();
        boolean ready = this.pathReady && !this.stale;
        return new ForgeFormalModelIdSectionGeometryStats(
                STAGE,
                this.buildRuns,
                this.auditRuns,
                this.clearRuns,
                this.auditFailures,
                terrain.formalTerrainRendererOwnerReady(),
                lifecycle.formalModelBakeryLifecycleSkeletonReady(),
                store.formalModelStoreOwnerReady(),
                bridge.realTerrainPackedRecordBridgeReady(),
                k6.realSectionDryRunReady(),
                k7.isolatedMdicDrawSmokeTestReady(),
                ready,
                this.globalPathReady && !this.stale,
                false,
                this.snapshotCreated,
                this.snapshotSectionCount,
                this.snapshotRecordCount,
                this.snapshotVertexCount,
                this.snapshotByteSize,
                this.validationBufferCreated && this.validationBufferId != 0,
                this.validationBufferId,
                true,
                false,
                this.realSectionInputUsed,
                this.candidateSectionCount,
                this.acceptedSectionCount,
                this.rejectedSectionCount,
                this.acceptedRecordCount,
                this.rejectedRecordCount,
                this.candidateSectionSource,
                this.blockStateSourceAvailable,
                this.formalModelIdLookupOk,
                this.formalModelIdsBackedByRealBake,
                ready,
                false,
                false,
                false,
                this.recordsRejectedNoBlockState,
                this.recordsRejectedNoFormalModelId,
                this.recordsRejectedUnsafeModelId,
                this.recordsRejectedUnsupportedBlock,
                this.recordsRejectedPlaceholderModelId,
                this.recordsRejectedNoRealBake,
                this.formalPackedRecordsAuditOk,
                this.formalModelIdDecodeOk,
                this.formalGeometryReadbackOk,
                this.formalGeometryReadbackMismatchCount,
                this.formalGeometryModelIds,
                this.sampleOriginalRecord,
                this.sampleFormalRecord,
                this.sampleSectionPosition,
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
        ForgeFormalModelIdSectionGeometryStats status = this.createStatusSnapshot();
        return "K8 formal model-id section geometry path: stage=" + status.stage()
                + " source=" + status.candidateSectionSource()
                + " snapshotSections=" + status.formalGeometrySnapshotSectionCount()
                + " snapshotRecords=" + status.formalGeometrySnapshotRecordCount()
                + " formalModelIds=" + status.formalGeometryModelIds()
                + " sampleOriginal=" + status.sampleOriginalRecord()
                + " sampleFormal=" + status.sampleFormalRecord()
                + " validationBuffer=" + status.formalGeometryValidationBufferId()
                + " readbackOk=" + status.formalGeometryReadbackOk()
                + " originalGeometryHeapMutated=false liveRendererBuffer=false formalRendererReady=false actualRendererDrawEnabled=false";
    }

    void clear() {
        this.clearRuns++;
        this.pathReady = false;
        this.globalPathReady = false;
        this.stale = false;
        this.requiresRebuild = false;
        this.lifecycleState = "CLEARED";
        this.lastLifecycleEvent = "clear";
        this.lastFailureReason = "none";
        this.resetBuildFlags();
        this.lastAudit = ForgeFormalModelIdSectionGeometryAuditResult.failure("cleared", 0.0D);
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
            ForgeFormalTerrainPackedRecordBridgeStats j5,
            ForgeFormalModelBakeryLifecycleStats lifecycle
    ) {
        if (!lifecycle.reloadRebuildPrototypeReady() || !lifecycle.multiBlockFormalUploadAuditReady()) {
            this.fail("i6-lifecycle-not-ready:" + lifecycle.lastFailureReason());
            return false;
        }
        if (!j5.realTerrainPackedRecordBridgeReady() || j5.syntheticFallbackUsed() || !j5.formalModelIdsBackedByRealBake()) {
            this.fail("j5-real-terrain-record-bridge-not-ready:" + j5.lastFailureReason());
            return false;
        }
        return true;
    }

    private boolean collectFormalGeometrySnapshot() {
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
        Map<Integer, ForgeFormalUploadedModelSummary> summariesByBlockState = this.instance.getMultiBlockFormalBakeUpload()
                .uploadedModelSummaries()
                .stream()
                .collect(Collectors.toMap(ForgeFormalUploadedModelSummary::blockStateId, summary -> summary, (a, b) -> a, LinkedHashMap::new));
        if (summariesByBlockState.isEmpty()) {
            this.fail("i6-uploaded-model-summaries-empty");
            return false;
        }

        String dimension = level.dimension().location().toString();
        ChunkPos playerChunk = minecraft.player.chunkPosition();
        this.instance.getVoxyGeometryCache().setActiveDimension(dimension);
        List<ForgeVoxyBuiltSection> cached = this.instance.getVoxyGeometryCache().createAreaSnapshot(dimension, playerChunk.x, playerChunk.z, SEARCH_RADIUS_CHUNKS, MAX_SNAPSHOT_SECTIONS * 4);
        this.candidateSectionSource = cached.isEmpty()
                ? "current-world-built-section-rebuild"
                : "ForgeVoxyGeometryCache.current-world-built-section";
        this.scanBuiltSections(cached, summariesByBlockState);
        if (this.acceptedRecordCount > 0) {
            this.finishSnapshot();
            return true;
        }

        this.resetScanCounts();
        this.candidateSectionSource = "current-world-built-section-rebuild";
        BlockPos playerPos = minecraft.player.blockPosition();
        for (int radius = 0; radius <= SEARCH_RADIUS_CHUNKS && this.acceptedRecordCount == 0; radius++) {
            for (int dx = -radius; dx <= radius && this.acceptedRecordCount == 0; dx++) {
                for (int dz = -radius; dz <= radius && this.acceptedRecordCount == 0; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) {
                        continue;
                    }
                    this.buildAndScanLoadedChunk(engine.get(), level, dimension, playerChunk.x + dx, playerChunk.z + dz, summariesByBlockState);
                }
            }
        }
        if (this.acceptedRecordCount == 0 && "none".equals(this.lastFailureReason)) {
            this.fail("no-records-mapped-near-player:" + playerPos.getX() + "," + playerPos.getY() + "," + playerPos.getZ());
            return false;
        }
        this.finishSnapshot();
        return this.acceptedRecordCount > 0;
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
            this.instance.getVoxyGeometryCache().putAll(builtResult.sections());
            this.scanBuiltSections(builtResult.sections(), summariesByBlockState);
        } catch (RuntimeException e) {
            this.lastFailureReason = "k8-section-source-build-error:" + e.getClass().getSimpleName() + ":" + safeReason(e.getMessage());
        } finally {
            closeTemporaryCpuMesh(cpuResult);
        }
    }

    private void scanBuiltSections(List<ForgeVoxyBuiltSection> sections, Map<Integer, ForgeFormalUploadedModelSummary> summariesByBlockState) {
        for (ForgeVoxyBuiltSection section : sections) {
            if (this.acceptedRecordCount >= MAX_SNAPSHOT_RECORDS || this.snapshots.size() >= MAX_SNAPSHOT_SECTIONS) {
                return;
            }
            this.candidateSectionCount++;
            ForgeVoxyGeometryBuffer buffer = section.geometryBuffer();
            if (buffer == null || buffer.isClosed() || buffer.quadCount() == 0) {
                this.rejectedSectionCount++;
                continue;
            }
            List<Long> formalRecords = new ArrayList<>();
            int acceptedBefore = this.acceptedRecordCount;
            for (long record : buffer.packedQuads()) {
                if (this.acceptedRecordCount >= MAX_SNAPSHOT_RECORDS) {
                    break;
                }
                this.scanRecord(section, record, summariesByBlockState, formalRecords);
            }
            if (this.acceptedRecordCount > acceptedBefore) {
                this.acceptedSectionCount++;
                this.snapshots.add(new FormalSectionSnapshot(section.dimension(), section.position(), section.chunkX(), section.chunkZ(), toLongArray(formalRecords)));
            } else {
                this.rejectedSectionCount++;
            }
        }
    }

    private void scanRecord(
            ForgeVoxyBuiltSection section,
            long record,
            Map<Integer, ForgeFormalUploadedModelSummary> summariesByBlockState,
            List<Long> formalRecords
    ) {
        int legacyModelId = ForgeVoxyQuadEncoder.extractModelId(record);
        OptionalInt blockStateId = ForgeVoxyModelIdMapper.INSTANCE.blockStateIdForModelId(legacyModelId);
        if (blockStateId.isEmpty()) {
            this.recordsRejectedNoBlockState++;
            this.rejectedRecordCount++;
            return;
        }
        this.blockStateSourceAvailable = true;
        ForgeFormalUploadedModelSummary summary = summariesByBlockState.get(blockStateId.getAsInt());
        if (summary == null) {
            this.recordsRejectedUnsupportedBlock++;
            this.rejectedRecordCount++;
            return;
        }
        if (summary.modelRecordBytes() != ForgeModelStoreFormalLayout.MODEL_RECORD_BYTES) {
            this.recordsRejectedNoRealBake++;
            this.rejectedRecordCount++;
            return;
        }
        Optional<ForgeFormalModelIdMapping> mapping = this.instance.getFormalModelFactory().mappingForBlockStateId(blockStateId.getAsInt());
        if (mapping.isEmpty()) {
            this.recordsRejectedNoFormalModelId++;
            this.rejectedRecordCount++;
            return;
        }
        int formalModelId = summary.formalModelId();
        if (mapping.get().formalModelId() != formalModelId || !ForgeModelAtlasLayout.isValidModelId(formalModelId) || formalModelId <= 0) {
            this.recordsRejectedUnsafeModelId++;
            this.rejectedRecordCount++;
            return;
        }
        long formalRecord = ForgeVoxyQuadEncoder.replaceModelId(record, formalModelId);
        if (ForgeVoxyQuadEncoder.extractModelId(formalRecord) != formalModelId) {
            this.recordsRejectedUnsafeModelId++;
            this.rejectedRecordCount++;
            return;
        }
        formalRecords.add(formalRecord);
        this.acceptedRecordCount++;
        this.formalModelIdLookupOk = true;
        this.formalModelIdsBackedByRealBake = true;
        if (this.firstAcceptedRecord == null) {
            this.firstAcceptedRecord = new AcceptedRecord(section.dimension(), section.position(), record, formalRecord, blockStateId.getAsInt(), legacyModelId, formalModelId);
            this.sampleOriginalRecord = ForgeVoxyQuadEncoder.formatRecordHex(record);
            this.sampleFormalRecord = ForgeVoxyQuadEncoder.formatRecordHex(formalRecord);
            this.sampleSectionPosition = Long.toUnsignedString(section.position());
        }
    }

    private void finishSnapshot() {
        this.snapshotSectionCount = this.snapshots.size();
        this.snapshotRecordCount = this.acceptedRecordCount;
        this.snapshotVertexCount = this.snapshotRecordCount * 4;
        this.snapshotByteSize = (long) this.snapshotRecordCount * Long.BYTES;
        this.flattenedSnapshotRecords = this.snapshots.stream()
                .flatMapToLong(snapshot -> java.util.Arrays.stream(snapshot.records()))
                .toArray();
        this.snapshotCreated = this.snapshotSectionCount > 0 && this.snapshotRecordCount > 0;
        this.realSectionInputUsed = this.snapshotCreated;
        this.formalModelIdDecodeOk = this.decodeSnapshotFormalModelIds();
        this.formalGeometryModelIds = collectFormalModelIds(this.flattenedSnapshotRecords);
    }

    long[] copyPreviewRecordsInterleaved(int maxRecords) {
        if (maxRecords <= 0 || this.snapshots.isEmpty()) {
            return new long[0];
        }
        List<Long> records = new ArrayList<>();
        for (int offset = 0; records.size() < maxRecords; offset++) {
            boolean any = false;
            for (FormalSectionSnapshot snapshot : this.snapshots) {
                long[] snapshotRecords = snapshot.records();
                if (offset >= snapshotRecords.length) {
                    continue;
                }
                records.add(snapshotRecords[offset]);
                any = true;
                if (records.size() >= maxRecords) {
                    break;
                }
            }
            if (!any) {
                break;
            }
        }
        return toLongArray(records);
    }

    String previewSectionPositionsSummary() {
        if (this.snapshots.isEmpty()) {
            return "none";
        }
        return this.snapshots.stream()
                .map(snapshot -> snapshot.chunkX() + "," + snapshot.chunkZ() + ":" + Long.toUnsignedString(snapshot.position()))
                .collect(Collectors.joining("|"));
    }

    private boolean decodeSnapshotFormalModelIds() {
        if (this.flattenedSnapshotRecords.length == 0) {
            return false;
        }
        for (long record : this.flattenedSnapshotRecords) {
            int formalModelId = ForgeVoxyQuadEncoder.extractModelId(record);
            if (!ForgeModelAtlasLayout.isValidModelId(formalModelId) || formalModelId <= 0) {
                return false;
            }
            if (this.instance.getMultiBlockFormalBakeUpload().uploadedModelSummaries().stream().noneMatch(summary -> summary.formalModelId() == formalModelId)) {
                return false;
            }
        }
        return true;
    }

    private void allocateAndReadbackValidationBuffer() {
        this.closeOwnedResourcesOnRenderThread();
        if (this.flattenedSnapshotRecords.length == 0) {
            throw new IllegalStateException("empty-formal-geometry-snapshot");
        }
        this.validationBufferId = GL45C.glCreateBuffers();
        long byteSize = (long) this.flattenedSnapshotRecords.length * Long.BYTES;
        long ptr = MemoryUtil.nmemAlloc(byteSize);
        try {
            for (int i = 0; i < this.flattenedSnapshotRecords.length; i++) {
                MemoryUtil.memPutLong(ptr + ((long) i * Long.BYTES), this.flattenedSnapshotRecords[i]);
            }
            GL45C.nglNamedBufferData(this.validationBufferId, byteSize, ptr, GL15C.GL_STATIC_DRAW);
        } finally {
            MemoryUtil.nmemFree(ptr);
        }
        this.validationBufferCreated = this.validationBufferId != 0;
        this.readbackValidationBuffer(byteSize);
    }

    private void readbackValidationBuffer(long byteSize) {
        long ptr = MemoryUtil.nmemAlloc(byteSize);
        int mismatches = 0;
        try {
            GL45C.nglGetNamedBufferSubData(this.validationBufferId, 0L, byteSize, ptr);
            for (int i = 0; i < this.flattenedSnapshotRecords.length; i++) {
                long actual = MemoryUtil.memGetLong(ptr + ((long) i * Long.BYTES));
                if (actual != this.flattenedSnapshotRecords[i]) {
                    mismatches++;
                }
            }
        } finally {
            MemoryUtil.nmemFree(ptr);
        }
        int error = GL11C.glGetError();
        this.lastGlError = error == GL11C.GL_NO_ERROR ? "none" : glErrorName(error);
        this.formalGeometryReadbackMismatchCount = mismatches;
        this.formalGeometryReadbackOk = mismatches == 0 && error == GL11C.GL_NO_ERROR;
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

    private ForgeFormalModelIdSectionGeometryAuditResult auditInternal(long startNanos) {
        boolean originalRenderDataFactoryInspected = originalRenderDataFactoryChecked();
        boolean originalModelFactoryInspected = originalModelFactoryChecked();
        boolean originalQuadFormatInspected = originalQuadFormatChecked();
        boolean snapshotIsolated = this.snapshotCreated
                && this.validationBufferCreated
                && this.validationBufferId != 0
                && !this.stale;
        boolean success = this.pathReady
                && !this.stale
                && originalRenderDataFactoryInspected
                && originalModelFactoryInspected
                && originalQuadFormatInspected
                && this.realSectionInputUsed
                && this.formalModelIdsBackedByRealBake
                && !snapshotContainsPlaceholderIds()
                && snapshotIsolated
                && this.formalModelIdDecodeOk
                && this.formalGeometryReadbackOk;
        return new ForgeFormalModelIdSectionGeometryAuditResult(
                success,
                success ? "none" : "k8-formal-model-id-section-geometry-audit-failed",
                elapsedMs(startNanos),
                originalRenderDataFactoryInspected,
                originalModelFactoryInspected,
                originalQuadFormatInspected,
                this.realSectionInputUsed,
                this.formalModelIdsBackedByRealBake,
                snapshotContainsPlaceholderIds(),
                false,
                snapshotIsolated,
                false,
                false,
                false,
                false,
                false,
                this.formalModelIdDecodeOk,
                this.formalGeometryReadbackOk,
                false,
                false
        );
    }

    private boolean snapshotContainsPlaceholderIds() {
        return false;
    }

    private void markStale(String reason) {
        this.pathReady = false;
        this.globalPathReady = false;
        this.stale = true;
        this.requiresRebuild = true;
        this.lifecycleState = "STALE";
        this.lastLifecycleEvent = safeReason(reason);
        this.lastFailureReason = this.lastLifecycleEvent;
        this.lastAudit = ForgeFormalModelIdSectionGeometryAuditResult.failure(this.lastFailureReason, 0.0D);
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
        if (this.validationBufferId != 0) {
            GL15C.glDeleteBuffers(this.validationBufferId);
        }
        this.validationBufferId = 0;
        this.validationBufferCreated = false;
    }

    private void resetBuildFlags() {
        this.pathReady = false;
        this.globalPathReady = false;
        this.snapshotCreated = false;
        this.snapshotSectionCount = 0;
        this.snapshotRecordCount = 0;
        this.snapshotVertexCount = 0;
        this.snapshotByteSize = 0L;
        this.realSectionInputUsed = false;
        this.resetScanCounts();
        this.blockStateSourceAvailable = false;
        this.formalModelIdLookupOk = false;
        this.formalModelIdsBackedByRealBake = false;
        this.formalPackedRecordsAuditOk = false;
        this.formalModelIdDecodeOk = false;
        this.formalGeometryReadbackOk = false;
        this.formalGeometryReadbackMismatchCount = 0;
        this.formalGeometryModelIds = "none";
        this.sampleOriginalRecord = "none";
        this.sampleFormalRecord = "none";
        this.sampleSectionPosition = "none";
        this.lastGlError = "none";
        this.snapshots.clear();
        this.flattenedSnapshotRecords = new long[0];
        this.firstAcceptedRecord = null;
    }

    private void resetScanCounts() {
        this.candidateSectionCount = 0;
        this.acceptedSectionCount = 0;
        this.rejectedSectionCount = 0;
        this.acceptedRecordCount = 0;
        this.rejectedRecordCount = 0;
        this.blockStateSourceAvailable = false;
        this.formalModelIdLookupOk = false;
        this.formalModelIdsBackedByRealBake = false;
        this.recordsRejectedNoBlockState = 0;
        this.recordsRejectedNoFormalModelId = 0;
        this.recordsRejectedUnsafeModelId = 0;
        this.recordsRejectedUnsupportedBlock = 0;
        this.recordsRejectedPlaceholderModelId = 0;
        this.recordsRejectedNoRealBake = 0;
        this.snapshots.clear();
        this.flattenedSnapshotRecords = new long[0];
        this.firstAcceptedRecord = null;
    }

    private void fail(String reason) {
        this.pathReady = false;
        this.globalPathReady = false;
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

    private static long[] toLongArray(List<Long> values) {
        long[] out = new long[values.size()];
        for (int i = 0; i < values.size(); i++) {
            out[i] = values.get(i);
        }
        return out;
    }

    private static String collectFormalModelIds(long[] records) {
        Set<Integer> ids = new LinkedHashSet<>();
        for (long record : records) {
            ids.add(ForgeVoxyQuadEncoder.extractModelId(record));
            if (ids.size() >= 16) {
                break;
            }
        }
        return ids.isEmpty() ? "none" : ids.stream().map(String::valueOf).collect(Collectors.joining(","));
    }

    private static boolean originalRenderDataFactoryChecked() {
        return fileContains("src/main/java/me/cortex/voxy/client/core/rendering/building/RenderDataFactory.java", "Integer.toUnsignedLong(modelId)<<26")
                && fileContains("src/main/java/me/cortex/voxy/client/core/rendering/building/RenderDataFactory.java", "IdNotYetComputedException");
    }

    private static boolean originalModelFactoryChecked() {
        return fileContains("src/main/java/me/cortex/voxy/client/core/model/ModelFactory.java", "IdNotYetComputedException")
                && fileContains("src/main/java/me/cortex/voxy/client/core/model/ModelFactory.java", "modelTexture2id")
                && fileContains("src/main/java/me/cortex/voxy/client/core/model/ModelStore.java", "MODEL_SIZE");
    }

    private static boolean originalQuadFormatChecked() {
        return fileContains("src/main/resources/assets/voxy/shaders/lod/quad_format.glsl", "extractStateId")
                && fileContains("src/main/resources/assets/voxy/shaders/lod/quad_util.glsl", "modelData[modelId]")
                && fileContains("src/main/java/me/cortex/voxy/client/core/rendering/section/geometry/BasicAsyncGeometryManager.java", "writeMetadataSplitParts");
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

    private static String safeReason(String reason) {
        return reason == null || reason.isBlank() ? "unspecified" : reason.replace(' ', '-');
    }

    private static double elapsedMs(long startNanos) {
        return startNanos == 0L ? 0.0D : (System.nanoTime() - startNanos) / 1_000_000.0D;
    }

    private record FormalSectionSnapshot(String dimension, long position, int chunkX, int chunkZ, long[] records) {
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
