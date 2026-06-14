package me.cortex.voxy.forge;

import com.mojang.blaze3d.systems.RenderSystem;
import me.cortex.voxy.config.ForgeVoxyConfig;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class ForgeGpuGeometryUploadManager {
    private final ForgeVoxyInstance instance;
    private final ForgeGpuGeometryHeap heap = new ForgeGpuGeometryHeap();
    private final HashMap<Integer, Integer> uploadedGeometryHashes = new HashMap<>();
    private final HashMap<Integer, Integer> uploadedMetadataHashes = new HashMap<>();
    private final Set<Integer> processedRemovePointers = new HashSet<>();

    private ForgeGpuGeometryStats lastStatus = ForgeGpuGeometryStats.disabled();
    private long uploadedGeometryBytes;
    private long uploadedSections;
    private long metadataWrites;
    private long removeIntentsProcessed;
    private long failures;
    private String lastError = "none";
    private double lastUploadMs;
    private double uploadWindowMs;
    private long uploadWindowCount;
    private long clearCount;
    private long validationRuns;
    private long validationFailures;
    private String lastValidationError = "none";
    private int lastValidationSectionId = -1;
    private int lastValidationGeometryPtr = -1;
    private boolean lastMetadataMatch;
    private boolean lastGeometryMatch;
    private long stressRuns;
    private long stressFailures;
    private String lastStressError = "none";
    private String lastStressStartedAt = "none";
    private String lastStressFinishedAt = "none";
    private double lastStressDurationMs;
    private long lastBeforeClearUploadedSections;
    private long lastAfterReenableUploadedSections;
    private long lastBeforeClearGeometryBytes;
    private long lastAfterReenableGeometryBytes;
    private boolean lastValidationAfterReenableMetadataMatch;
    private boolean lastValidationAfterReenableGeometryMatch;
    private long auditRuns;
    private long auditFailures;
    private double lastAuditDurationMs;
    private ForgeGpuGeometryAuditResult lastAuditResult = ForgeGpuGeometryAuditResult.failure("none");
    private int releasedBuffers;
    private boolean renderCallQueued;
    private boolean disabledByFailure;

    ForgeGpuGeometryUploadManager(ForgeVoxyInstance instance) {
        this.instance = instance;
    }

    public void register() {
        MinecraftForge.EVENT_BUS.addListener(this::onClientTick);
    }

    public ForgeGpuGeometryStats getLastStatus() {
        return this.lastStatus;
    }

    ForgeGpuGeometryHeap getHeapForDebugReadback() {
        return this.heap;
    }

    List<Integer> createUploadedSectionIdSnapshot() {
        return new ArrayList<>(this.uploadedMetadataHashes.keySet());
    }

    public ForgeGpuGeometryStats createStatusSnapshot() {
        IntentCounts counts = this.countPendingIntents();
        return new ForgeGpuGeometryStats(
                isEnabled() && !this.disabledByFailure,
                this.disabledByFailure ? "disabled-after-failure" : (isEnabled() ? "ok" : "disabled"),
                this.heap.isCreated(),
                this.heap.geometryCapacityBytes(),
                this.heap.metadataCapacityBytes(),
                this.uploadedGeometryBytes,
                this.uploadedSections,
                this.metadataWrites,
                this.removeIntentsProcessed,
                counts.pendingUploads(),
                counts.pendingRemoves(),
                counts.pendingMetadata(),
                this.failures,
                this.lastError,
                this.lastUploadMs,
                this.averageUploadMs(),
                this.releasedBuffers,
                this.clearCount,
                this.validationRuns,
                this.validationFailures,
                this.lastValidationError,
                this.lastValidationSectionId,
                this.lastValidationGeometryPtr,
                this.lastMetadataMatch,
                this.lastGeometryMatch,
                true
        );
    }

    public ForgeGpuGeometryStressStats createStressStatusSnapshot() {
        return new ForgeGpuGeometryStressStats(
                this.stressRuns,
                this.stressFailures,
                this.lastStressError,
                this.lastStressStartedAt,
                this.lastStressFinishedAt,
                this.lastStressDurationMs,
                this.lastBeforeClearUploadedSections,
                this.lastAfterReenableUploadedSections,
                this.lastBeforeClearGeometryBytes,
                this.lastAfterReenableGeometryBytes,
                this.lastValidationAfterReenableMetadataMatch,
                this.lastValidationAfterReenableGeometryMatch
        );
    }

    public ForgeGpuGeometryAuditStats createAuditStatusSnapshot() {
        return new ForgeGpuGeometryAuditStats(
                this.auditRuns,
                this.auditFailures,
                this.lastAuditResult.lastAuditError(),
                this.lastAuditDurationMs,
                this.lastAuditResult.auditedSections(),
                this.lastAuditResult.decodedRecords(),
                this.lastAuditResult.invalidMetadata(),
                this.lastAuditResult.invalidGeometryRecords(),
                this.lastAuditResult.emptyBuckets(),
                this.lastAuditResult.nonEmptyBuckets(),
                this.lastAuditResult.maxQuadLength(),
                this.lastAuditResult.maxQuadWidth(),
                this.lastAuditResult.lastAuditedSectionId(),
                this.lastAuditResult.lastAuditedGeometryPtr()
        );
    }

    public void clearAuditStats() {
        this.auditRuns = 0;
        this.auditFailures = 0;
        this.lastAuditDurationMs = 0.0D;
        this.lastAuditResult = ForgeGpuGeometryAuditResult.failure("none");
    }

    public void clearStressStats() {
        this.stressRuns = 0;
        this.stressFailures = 0;
        this.lastStressError = "none";
        this.lastStressStartedAt = "none";
        this.lastStressFinishedAt = "none";
        this.lastStressDurationMs = 0.0D;
        this.lastBeforeClearUploadedSections = 0;
        this.lastAfterReenableUploadedSections = 0;
        this.lastBeforeClearGeometryBytes = 0;
        this.lastAfterReenableGeometryBytes = 0;
        this.lastValidationAfterReenableMetadataMatch = false;
        this.lastValidationAfterReenableGeometryMatch = false;
    }

    public void clear() {
        this.uploadedGeometryHashes.clear();
        this.uploadedMetadataHashes.clear();
        this.processedRemovePointers.clear();
        this.uploadedGeometryBytes = 0;
        this.uploadedSections = 0;
        this.metadataWrites = 0;
        this.removeIntentsProcessed = 0;
        this.failures = 0;
        this.lastError = "none";
        this.lastUploadMs = 0.0D;
        this.uploadWindowMs = 0.0D;
        this.uploadWindowCount = 0;
        this.clearCount++;
        this.disabledByFailure = false;
        this.lastValidationError = "none";
        this.lastValidationSectionId = -1;
        this.lastValidationGeometryPtr = -1;
        this.lastMetadataMatch = false;
        this.lastGeometryMatch = false;
        this.lastAuditResult = ForgeGpuGeometryAuditResult.failure("none");
        this.lastAuditDurationMs = 0.0D;
        this.releasedBuffers += this.closeHeapSafely();
        this.lastStatus = ForgeGpuGeometryStats.disabled();
        this.instance.getGpuGeometryVisualizationCache().clear();
        this.instance.getGpuGeometryReadbackMeshCache().clear();
        this.instance.getGpuGeometryReadbackMeshRefreshManager().clear();
        if (ForgeGpuMeshUploadManager.getConfiguredSource() == me.cortex.voxy.config.SimpleGpuMeshSource.GL_HEAP_READBACK) {
            this.instance.getGpuMeshUploadManager().clear();
        }
        this.instance.getGpuGeometryReadbackDebugRenderer().clearStats();
        this.instance.getMdicCommandManager().clear();
    }

    public ForgeGpuGeometryStressStats stressOnce() {
        this.stressRuns++;
        long startedNanos = System.nanoTime();
        this.lastStressStartedAt = Instant.now().toString();
        this.lastStressFinishedAt = "running";
        this.lastStressDurationMs = 0.0D;
        this.lastStressError = "running";
        this.lastBeforeClearUploadedSections = 0;
        this.lastAfterReenableUploadedSections = 0;
        this.lastBeforeClearGeometryBytes = 0;
        this.lastAfterReenableGeometryBytes = 0;
        this.lastValidationAfterReenableMetadataMatch = false;
        this.lastValidationAfterReenableGeometryMatch = false;

        if (!RenderSystem.isOnRenderThread()) {
            return this.finishStressFailure(startedNanos, "not-render-thread");
        }
        ForgeVoxyRuntimeOverrides.setGeometryGpuUpload(true);
        if (!isEnabled()) {
            return this.finishStressFailure(startedNanos, "geometry-gpu-upload-disabled");
        }
        if (this.instance.getCurrentEngineOptional().isEmpty()) {
            return this.finishStressFailure(startedNanos, "no-engine");
        }

        try {
            this.disabledByFailure = false;
            ForgeGpuGeometryValidationResult beforeValidation = this.processAndValidateStressSample(4);
            this.lastBeforeClearUploadedSections = this.uploadedSections;
            this.lastBeforeClearGeometryBytes = this.uploadedGeometryBytes;
            if (!beforeValidation.success()) {
                return this.finishStressFailure(startedNanos, "before-clear-validation-" + beforeValidation.reason());
            }

            this.clear();
            if (this.heap.isCreated()) {
                return this.finishStressFailure(startedNanos, "heap-still-created-after-clear");
            }

            ForgeVoxyRuntimeOverrides.setGeometryGpuUpload(true);
            this.disabledByFailure = false;
            ForgeGpuGeometryValidationResult afterValidation = this.processAndValidateStressSample(4);
            this.lastAfterReenableUploadedSections = this.uploadedSections;
            this.lastAfterReenableGeometryBytes = this.uploadedGeometryBytes;
            this.lastValidationAfterReenableMetadataMatch = afterValidation.metadataMatch();
            this.lastValidationAfterReenableGeometryMatch = afterValidation.geometryMatch();
            if (!afterValidation.success()) {
                return this.finishStressFailure(startedNanos, "after-reenable-validation-" + afterValidation.reason());
            }
            return this.finishStressSuccess(startedNanos);
        } catch (RuntimeException e) {
            this.recordFailure("stress " + e.getClass().getSimpleName() + ": " + e.getMessage());
            return this.finishStressFailure(startedNanos, e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    public ForgeGpuGeometryValidationResult validateSample(int maxRecords) {
        this.validationRuns++;
        if (!RenderSystem.isOnRenderThread()) {
            return this.recordValidationResult(ForgeGpuGeometryValidationResult.failure("not-render-thread"));
        }
        if (!isEnabled()) {
            return this.recordValidationResult(ForgeGpuGeometryValidationResult.failure("geometry-gpu-upload-disabled"));
        }
        if (!this.heap.isCreated()) {
            return this.recordValidationResult(ForgeGpuGeometryValidationResult.failure("heap-not-created"));
        }

        int recordLimit = Math.min(16, Math.max(1, maxRecords));
        try {
            List<ForgeSectionGeometryUploadIntent> uploads = this.instance.getSectionGeometryManager().createUploadIntentSnapshot();
            for (ForgeSectionGeometryUploadIntent intent : uploads) {
                Integer uploadedHash = this.uploadedGeometryHashes.get(intent.geometryPtr());
                if (uploadedHash == null || uploadedHash != intent.recordHash()) {
                    continue;
                }
                int[] expectedMetadata = this.instance.getSectionGeometryManager().createMetadataWordsSnapshot(intent.sectionId());
                Integer metadataHash = this.uploadedMetadataHashes.get(intent.sectionId());
                if (metadataHash == null || metadataHash != metadataHash(expectedMetadata)) {
                    continue;
                }

                long[] expectedRecords = Arrays.copyOf(intent.recordsCopy(), Math.min(recordLimit, intent.itemCount()));
                int[] actualMetadata = this.heap.readbackMetadata(intent.sectionId());
                long[] actualRecords = this.heap.readbackGeometry(intent.geometryPtr(), expectedRecords.length);
                return this.recordValidationResult(ForgeGpuGeometryValidationResult.success(
                        intent.sectionId(),
                        intent.geometryPtr(),
                        expectedMetadata,
                        actualMetadata,
                        expectedRecords,
                        actualRecords
                ));
            }
            return this.recordValidationResult(ForgeGpuGeometryValidationResult.failure("no-uploaded-sample"));
        } catch (RuntimeException e) {
            this.recordFailure("validation " + e.getClass().getSimpleName() + ": " + e.getMessage());
            return this.recordValidationResult(ForgeGpuGeometryValidationResult.failure(e.getClass().getSimpleName() + ": " + e.getMessage()));
        }
    }

    public ForgeGpuGeometryAuditResult auditSample() {
        this.auditRuns++;
        long start = System.nanoTime();
        ForgeGpuGeometryAuditResult result;
        if (!RenderSystem.isOnRenderThread()) {
            result = ForgeGpuGeometryAuditResult.failure("not-render-thread");
        } else if (!isEnabled()) {
            result = ForgeGpuGeometryAuditResult.failure("geometry-gpu-upload-disabled");
        } else if (!this.heap.isCreated()) {
            result = ForgeGpuGeometryAuditResult.failure("heap-not-created");
        } else {
            try {
                var sectionIds = new ArrayList<>(this.uploadedMetadataHashes.keySet());
                Collections.sort(sectionIds);
                result = ForgeGpuGeometryAuditor.audit(this.heap, sectionIds, this.instance.getSectionGeometryManager(), 1);
            } catch (RuntimeException e) {
                this.recordFailure("audit " + e.getClass().getSimpleName() + ": " + e.getMessage());
                result = ForgeGpuGeometryAuditResult.failure(e.getClass().getSimpleName() + ": " + e.getMessage());
            }
        }

        this.lastAuditDurationMs = (System.nanoTime() - start) / 1_000_000.0D;
        this.lastAuditResult = result;
        if (!result.success()) {
            this.auditFailures++;
        }
        return result;
    }

    private void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        if (!isEnabled() || this.instance.getCurrentEngineOptional().isEmpty()) {
            if (this.heap.isCreated()) {
                this.clear();
            } else {
                IntentCounts counts = this.countPendingIntents();
                this.lastStatus = ForgeGpuGeometryStats.skipped(
                        isEnabled() ? "no-engine" : "disabled",
                        false,
                        0,
                        0,
                        counts.pendingUploads(),
                        counts.pendingRemoves(),
                        counts.pendingMetadata()
                );
            }
            return;
        }

        if (this.disabledByFailure) {
            this.lastStatus = this.createStatusSnapshot();
            return;
        }

        if (RenderSystem.isOnRenderThread()) {
            this.processOnRenderThread();
            return;
        }

        if (!this.renderCallQueued) {
            this.renderCallQueued = true;
            RenderSystem.recordRenderCall(() -> {
                this.renderCallQueued = false;
                this.processOnRenderThread();
            });
        }
    }

    private void processOnRenderThread() {
        if (!RenderSystem.isOnRenderThread()) {
            this.recordFailure("process called off render thread");
            return;
        }
        if (!isEnabled() || this.instance.getCurrentEngineOptional().isEmpty()) {
            return;
        }

        long start = System.nanoTime();
        int uploadedThisTick = 0;
        int metadataThisTick = 0;
        int removesThisTick = 0;
        try {
            this.heap.ensureCreated(getGeometryCapacityBytes(), getMetadataCapacityBytes());
            removesThisTick = this.processRemoveIntents();
            uploadedThisTick = this.processUploadIntents(getMaxUploadsPerTick());
            metadataThisTick = this.processMetadataWrites(getMaxMetadataWritesPerTick());
        } catch (RuntimeException e) {
            this.recordFailure(e.getClass().getSimpleName() + ": " + e.getMessage());
            this.disabledByFailure = true;
            VoxyForge.LOGGER.error("Disabled upload-only Voxy GL geometry heap after failure", e);
        }

        double elapsedMs = (System.nanoTime() - start) / 1_000_000.0D;
        if (uploadedThisTick > 0 || metadataThisTick > 0 || removesThisTick > 0) {
            this.lastUploadMs = elapsedMs;
            this.uploadWindowCount++;
            this.uploadWindowMs += elapsedMs;
            if (ForgeVoxyConfig.GEOMETRY_GPU_DEBUG_LOG.get()) {
                VoxyForge.LOGGER.info(
                        "Voxy upload-only GL geometry heap tick: uploads={} metadata={} removes={} ms={}",
                        uploadedThisTick,
                        metadataThisTick,
                        removesThisTick,
                        String.format("%.2f", elapsedMs)
                );
            }
        }
        this.lastStatus = this.createStatusSnapshot();
    }

    void processForDebugCommand(int passes) {
        if (!RenderSystem.isOnRenderThread()) {
            this.recordFailure("debug command process called off render thread");
            return;
        }
        int boundedPasses = Math.max(1, Math.min(8, passes));
        for (int pass = 0; pass < boundedPasses; pass++) {
            this.processOnRenderThread();
        }
    }

    private int processUploadIntents(int maxUploads) {
        List<ForgeSectionGeometryUploadIntent> uploads = this.instance.getSectionGeometryManager().createUploadIntentSnapshot();
        int uploaded = 0;
        for (ForgeSectionGeometryUploadIntent intent : uploads) {
            if (uploaded >= maxUploads) {
                break;
            }
            int currentHash = intent.recordHash();
            Integer uploadedHash = this.uploadedGeometryHashes.get(intent.geometryPtr());
            if (uploadedHash != null && uploadedHash == currentHash) {
                continue;
            }

            try {
                this.heap.uploadGeometry(intent.geometryPtr(), intent.recordsCopy());
                this.uploadedGeometryHashes.put(intent.geometryPtr(), currentHash);
                this.uploadedGeometryBytes += intent.sizeBytes();
                this.uploadedSections++;
                uploaded++;
            } catch (RuntimeException e) {
                this.recordFailure("geometry ptr=" + Integer.toUnsignedLong(intent.geometryPtr()) + " " + e.getClass().getSimpleName() + ": " + e.getMessage());
                if (isCapacityFailure(e)) {
                    throw e;
                }
            }
        }
        return uploaded;
    }

    private int processMetadataWrites(int maxWrites) {
        List<Integer> dirtyIds = this.instance.getSectionGeometryManager().createDirtyMetadataIdSnapshot();
        int writes = 0;
        for (Integer id : dirtyIds) {
            if (writes >= maxWrites) {
                break;
            }
            if (id == null || id < 0) {
                continue;
            }
            int[] words = this.instance.getSectionGeometryManager().createMetadataWordsSnapshot(id);
            int hash = metadataHash(words);
            Integer uploadedHash = this.uploadedMetadataHashes.get(id);
            if (uploadedHash != null && uploadedHash == hash) {
                continue;
            }

            try {
                this.heap.uploadMetadata(id, words);
                this.uploadedMetadataHashes.put(id, hash);
                this.metadataWrites++;
                writes++;
            } catch (RuntimeException e) {
                this.recordFailure("metadata id=" + id + " " + e.getClass().getSimpleName() + ": " + e.getMessage());
                if (isCapacityFailure(e)) {
                    throw e;
                }
            }
        }
        return writes;
    }

    private int processRemoveIntents() {
        List<ForgeSectionGeometryRemoveIntent> removes = this.instance.getSectionGeometryManager().createRemoveIntentSnapshot();
        int processed = 0;
        for (ForgeSectionGeometryRemoveIntent intent : removes) {
            if (this.processedRemovePointers.add(intent.geometryPtr())) {
                this.uploadedGeometryHashes.remove(intent.geometryPtr());
                this.uploadedMetadataHashes.remove(intent.sectionId());
                this.removeIntentsProcessed++;
                processed++;
            }
        }
        return processed;
    }

    private IntentCounts countPendingIntents() {
        int pendingUploads = 0;
        for (ForgeSectionGeometryUploadIntent intent : this.instance.getSectionGeometryManager().createUploadIntentSnapshot()) {
            Integer hash = this.uploadedGeometryHashes.get(intent.geometryPtr());
            if (hash == null || hash != intent.recordHash()) {
                pendingUploads++;
            }
        }

        int pendingRemoves = 0;
        for (ForgeSectionGeometryRemoveIntent intent : this.instance.getSectionGeometryManager().createRemoveIntentSnapshot()) {
            if (!this.processedRemovePointers.contains(intent.geometryPtr())) {
                pendingRemoves++;
            }
        }

        int pendingMetadata = 0;
        for (Integer id : this.instance.getSectionGeometryManager().createDirtyMetadataIdSnapshot()) {
            if (id == null || id < 0) {
                continue;
            }
            int hash = metadataHash(this.instance.getSectionGeometryManager().createMetadataWordsSnapshot(id));
            Integer uploadedHash = this.uploadedMetadataHashes.get(id);
            if (uploadedHash == null || uploadedHash != hash) {
                pendingMetadata++;
            }
        }

        return new IntentCounts(pendingUploads, pendingRemoves, pendingMetadata);
    }

    private int closeHeapSafely() {
        if (!this.heap.isCreated()) {
            return 0;
        }
        if (RenderSystem.isOnRenderThread()) {
            return this.heap.closeOnRenderThread();
        }
        RenderSystem.recordRenderCall(this.heap::closeOnRenderThread);
        return 0;
    }

    private void recordFailure(String message) {
        this.failures++;
        this.lastError = message == null ? "unknown" : message;
        this.lastStatus = this.createStatusSnapshot();
    }

    private ForgeGpuGeometryValidationResult recordValidationResult(ForgeGpuGeometryValidationResult result) {
        this.lastValidationSectionId = result.sectionId();
        this.lastValidationGeometryPtr = result.geometryPtr();
        this.lastMetadataMatch = result.metadataMatch();
        this.lastGeometryMatch = result.geometryMatch();
        this.lastValidationError = result.success() ? "none" : result.reason();
        if (!result.success()) {
            this.validationFailures++;
        }
        this.lastStatus = this.createStatusSnapshot();
        return result;
    }

    private ForgeGpuGeometryValidationResult processAndValidateStressSample(int passes) {
        ForgeGpuGeometryValidationResult result = ForgeGpuGeometryValidationResult.failure("no-validation-attempt");
        int attempts = Math.max(1, passes);
        for (int i = 0; i < attempts; i++) {
            this.processOnRenderThread();
            result = this.validateSample(8);
            if (result.success()) {
                return result;
            }
            if (!"no-uploaded-sample".equals(result.reason()) && !"heap-not-created".equals(result.reason())) {
                return result;
            }
        }
        return result;
    }

    private ForgeGpuGeometryStressStats finishStressSuccess(long startedNanos) {
        this.lastStressError = "none";
        this.lastStressFinishedAt = Instant.now().toString();
        this.lastStressDurationMs = (System.nanoTime() - startedNanos) / 1_000_000.0D;
        return this.createStressStatusSnapshot();
    }

    private ForgeGpuGeometryStressStats finishStressFailure(long startedNanos, String reason) {
        this.stressFailures++;
        this.lastStressError = reason == null ? "unknown" : reason;
        this.lastStressFinishedAt = Instant.now().toString();
        this.lastStressDurationMs = (System.nanoTime() - startedNanos) / 1_000_000.0D;
        return this.createStressStatusSnapshot();
    }

    private double averageUploadMs() {
        return this.uploadWindowCount == 0 ? 0.0D : this.uploadWindowMs / this.uploadWindowCount;
    }

    private static boolean isCapacityFailure(RuntimeException e) {
        String message = e.getMessage();
        return message != null && message.contains("capacity");
    }

    private static int metadataHash(int[] words) {
        int hash = 1;
        if (words == null) {
            return 0;
        }
        for (int word : words) {
            hash = 31 * hash + word;
        }
        return hash;
    }

    public static boolean isEnabled() {
        return ForgeVoxyRuntimeOverrides.enableGeometryGpuUpload();
    }

    public static long getGeometryCapacityBytes() {
        return Math.min(256L * 1024L * 1024L, Math.max(1024L * 1024L, ForgeVoxyConfig.GEOMETRY_GPU_HEAP_BYTES.get()));
    }

    public static long getMetadataCapacityBytes() {
        return Math.min(64L * 1024L * 1024L, Math.max(1024L * 1024L, ForgeVoxyConfig.GEOMETRY_GPU_METADATA_BYTES.get()));
    }

    public static int getMaxUploadsPerTick() {
        return Math.min(64, Math.max(1, ForgeVoxyConfig.GEOMETRY_GPU_MAX_UPLOADS_PER_TICK.get()));
    }

    public static int getMaxMetadataWritesPerTick() {
        return Math.min(4096, Math.max(1, ForgeVoxyConfig.GEOMETRY_GPU_MAX_METADATA_WRITES_PER_TICK.get()));
    }

    private record IntentCounts(int pendingUploads, int pendingRemoves, int pendingMetadata) {
    }
}
