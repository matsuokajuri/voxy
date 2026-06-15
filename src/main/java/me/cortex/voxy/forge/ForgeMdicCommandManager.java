package me.cortex.voxy.forge;

import com.mojang.blaze3d.systems.RenderSystem;
import me.cortex.voxy.config.SimpleGpuMeshSource;
import net.minecraft.client.Minecraft;

final class ForgeMdicCommandManager {
    private final ForgeVoxyInstance instance;
    private final ForgeMdicCommandBuffer commandBuffer = new ForgeMdicCommandBuffer();
    private ForgeMdicCommandList commandList = ForgeMdicCommandList.empty();
    private double lastPlanDurationMs;
    private double lastBuildBufferDurationMs;
    private String lastError = "none";
    private long auditRuns;
    private long auditFailures;
    private String lastAuditError = "none";
    private double lastAuditDurationMs;
    private boolean lastCommandBufferMatch;
    private boolean lastLayoutMatch;
    private boolean lastGenerationMatch;
    private boolean lastDimensionMatch;
    private int lastInvalidCommands;
    private int lastInvalidLayoutCommands;
    private int lastInvalidGenerationCommands;
    private int lastInvalidDimensionCommands;
    private int lastInvalidBucketMaskCommands;
    private int lastInvalidBucketRangeCommands;
    private int lastInvalidBucketOffsetCommands;
    private int lastInvalidGeometryPtrCommands;
    private int lastAuditedCommands;
    private long lastAuditedRecords;
    private long lastAuditedBytes;
    private long lastAuditHeapGeneration = -1L;
    private String lastAuditDimension = "none";
    private long stressRuns;
    private long stressFailures;
    private String lastStressError = "none";
    private double lastStressDurationMs;
    private boolean lastStressPlanOk;
    private boolean lastStressBuildOk;
    private boolean lastStressAuditOk;
    private boolean lastStressClearOk;
    private boolean lastStressHeapClearOk;
    private boolean lastStressRebuildOk;
    private boolean lastStressReauditOk;
    private boolean lastStressSourceRegressionOk;
    private boolean lastStressBucketAwareOk;
    private boolean lastStressBucketAuditOk;
    private int lastStressCommandCount;
    private long lastStressCommandRecords;
    private int lastStressInvalidCommands;

    ForgeMdicCommandManager(ForgeVoxyInstance instance) {
        this.instance = instance;
    }

    ForgeMdicCommandPlanner.PlanResult planSample() {
        ForgeMdicCommandPlanner.PlanResult result = ForgeMdicCommandPlanner.plan(this.instance, ForgeMdicCommandConfig.maxSections(), ForgeMdicCommandConfig.maxRecords());
        this.lastPlanDurationMs = result.durationMs();
        this.lastError = result.success() ? "none" : result.error();
        if (result.success()) {
            this.commandList = result.commandList();
            this.commandBuffer.close();
            this.clearAuditStats();
        }
        if (ForgeMdicCommandConfig.debugLog()) {
            VoxyForge.LOGGER.info("Voxy MDIC skeleton planned commands={} records={} success={} error={}",
                    result.commandList().commandCount(),
                    result.commandList().recordCount(),
                    result.success(),
                    result.error());
        }
        return result;
    }

    boolean buildBuffer() {
        long start = System.nanoTime();
        if (!RenderSystem.isOnRenderThread()) {
            this.lastError = "not-render-thread";
            this.lastBuildBufferDurationMs = elapsedMs(start);
            return false;
        }
        if (!this.commandList.isValid()) {
            this.lastError = "command-list-empty";
            this.lastBuildBufferDurationMs = elapsedMs(start);
            return false;
        }
        ForgeGpuGeometryHeap heap = this.instance.getGpuGeometryUploadManager().getHeapForDebugReadback();
        if (heap == null || !heap.isCreated()) {
            this.lastError = "heap-not-created";
            this.lastBuildBufferDurationMs = elapsedMs(start);
            return false;
        }
        String currentDimension = currentDimensionId();
        if (this.commandList.isStale(heap.generation())) {
            this.lastError = "command-list-stale";
            this.lastBuildBufferDurationMs = elapsedMs(start);
            return false;
        }
        if (this.commandList.isDimensionMismatch(currentDimension)) {
            this.lastError = "dimension-mismatch:" + this.commandList.dimensionId() + "!=" + currentDimension;
            this.lastBuildBufferDurationMs = elapsedMs(start);
            return false;
        }
        boolean uploaded = this.commandBuffer.upload(this.commandList);
        this.lastBuildBufferDurationMs = elapsedMs(start);
        this.lastError = uploaded ? "none" : this.commandBuffer.lastUploadError();
        return uploaded;
    }

    ForgeMdicCommandAuditResult audit() {
        this.auditRuns++;
        long start = System.nanoTime();
        ForgeMdicCommandAuditResult result;
        if (!RenderSystem.isOnRenderThread()) {
            result = ForgeMdicCommandAuditResult.failure("not-render-thread", elapsedMs(start));
        } else if (!this.commandList.isValid()) {
            result = ForgeMdicCommandAuditResult.failure("command-list-empty", elapsedMs(start));
        } else if (!this.commandBuffer.isCreated()) {
            result = ForgeMdicCommandAuditResult.failure("command-buffer-missing", elapsedMs(start));
        } else {
            try {
                result = this.compareCommandBuffer(start);
            } catch (RuntimeException e) {
                result = ForgeMdicCommandAuditResult.failure(e.getClass().getSimpleName() + ": " + e.getMessage(), elapsedMs(start));
            }
        }
        this.recordAuditResult(result);
        return result;
    }

    void clear() {
        this.commandList = ForgeMdicCommandList.empty();
        this.commandBuffer.close();
        this.lastPlanDurationMs = 0.0D;
        this.lastBuildBufferDurationMs = 0.0D;
        this.lastError = "none";
        this.clearAuditStats();
    }

    void clearAuditStats() {
        this.auditRuns = 0L;
        this.auditFailures = 0L;
        this.lastAuditError = "none";
        this.lastAuditDurationMs = 0.0D;
        this.lastCommandBufferMatch = false;
        this.lastLayoutMatch = false;
        this.lastGenerationMatch = false;
        this.lastDimensionMatch = false;
        this.lastInvalidCommands = 0;
        this.lastInvalidLayoutCommands = 0;
        this.lastInvalidGenerationCommands = 0;
        this.lastInvalidDimensionCommands = 0;
        this.lastInvalidBucketMaskCommands = 0;
        this.lastInvalidBucketRangeCommands = 0;
        this.lastInvalidBucketOffsetCommands = 0;
        this.lastInvalidGeometryPtrCommands = 0;
        this.lastAuditedCommands = 0;
        this.lastAuditedRecords = 0L;
        this.lastAuditedBytes = 0L;
        this.lastAuditHeapGeneration = -1L;
        this.lastAuditDimension = "none";
    }

    void clearStressStats() {
        this.stressRuns = 0L;
        this.stressFailures = 0L;
        this.lastStressError = "none";
        this.lastStressDurationMs = 0.0D;
        this.lastStressPlanOk = false;
        this.lastStressBuildOk = false;
        this.lastStressAuditOk = false;
        this.lastStressClearOk = false;
        this.lastStressHeapClearOk = false;
        this.lastStressRebuildOk = false;
        this.lastStressReauditOk = false;
        this.lastStressSourceRegressionOk = false;
        this.lastStressBucketAwareOk = false;
        this.lastStressBucketAuditOk = false;
        this.lastStressCommandCount = 0;
        this.lastStressCommandRecords = 0L;
        this.lastStressInvalidCommands = 0;
    }

    ForgeMdicCommandStats stressOnce() {
        this.stressRuns++;
        long start = System.nanoTime();
        this.lastStressError = "running";
        this.lastStressPlanOk = false;
        this.lastStressBuildOk = false;
        this.lastStressAuditOk = false;
        this.lastStressClearOk = false;
        this.lastStressHeapClearOk = false;
        this.lastStressRebuildOk = false;
        this.lastStressReauditOk = false;
        this.lastStressSourceRegressionOk = false;
        this.lastStressBucketAwareOk = false;
        this.lastStressBucketAuditOk = false;
        this.lastStressCommandCount = 0;
        this.lastStressCommandRecords = 0L;
        this.lastStressInvalidCommands = 0;

        String error = "none";
        try {
            if (!RenderSystem.isOnRenderThread()) {
                error = "not-render-thread";
            } else {
                ForgeGpuGeometryUploadManager uploadManager = this.instance.getGpuGeometryUploadManager();
                ForgeVoxyRuntimeOverrides.applyMdicSkeletonPreset();
                this.instance.ensureActiveWorldSkeletonForCurrentWorldIfAllowed();
                uploadManager.processForDebugCommand(6);

                ForgeMdicCommandPlanner.PlanResult firstPlan = this.planSample();
                this.lastStressPlanOk = firstPlan.success();
                this.lastStressBuildOk = this.lastStressPlanOk && this.buildBuffer();
                ForgeMdicCommandAuditResult firstAudit = this.audit();
                this.lastStressAuditOk = this.lastStressBuildOk && firstAudit.success();
                this.lastStressBucketAwareOk = firstPlan.success()
                        && this.commandList.bucketAware()
                        && this.commandList.bucketCommands() > 0
                        && this.commandList.sectionCommands() == 0;
                this.lastStressBucketAuditOk = this.lastStressAuditOk
                        && firstAudit.invalidBucketMaskCommands() == 0
                        && firstAudit.invalidBucketRangeCommands() == 0
                        && firstAudit.invalidBucketOffsetCommands() == 0;
                if (!this.lastStressPlanOk && "none".equals(error)) {
                    error = "first-plan=" + firstPlan.error();
                } else if (!this.lastStressBuildOk && "none".equals(error)) {
                    error = "first-build=" + this.lastError;
                } else if (!this.lastStressAuditOk && "none".equals(error)) {
                    error = "first-audit=" + firstAudit.error();
                } else if (!this.lastStressBucketAwareOk && "none".equals(error)) {
                    error = "first-bucket-aware-plan-failed";
                } else if (!this.lastStressBucketAuditOk && "none".equals(error)) {
                    error = "first-bucket-audit-failed";
                }

                this.lastStressCommandCount = this.commandList.commandCount();
                this.lastStressCommandRecords = this.commandList.recordCount();
                this.lastStressInvalidCommands = firstAudit.invalidCommands();

                this.clear();
                ForgeMdicCommandStats cleared = this.createStatusSnapshot();
                this.lastStressClearOk = !cleared.commandListValid() && !cleared.commandBufferCreated() && cleared.auditRuns() == 0;
                if (!this.lastStressClearOk && "none".equals(error)) {
                    error = "clear-did-not-reset-mdic-state";
                }

                ForgeMdicCommandPlanner.PlanResult secondPlan = this.planSample();
                boolean secondBuild = secondPlan.success() && this.buildBuffer();
                ForgeMdicCommandAuditResult secondAudit = this.audit();
                boolean secondRebuildOk = secondPlan.success() && secondBuild;
                boolean secondAuditOk = secondBuild && secondAudit.success();
                if (!secondRebuildOk && "none".equals(error)) {
                    error = "second-rebuild=" + (secondPlan.success() ? this.lastError : secondPlan.error());
                } else if (!secondAuditOk && "none".equals(error)) {
                    error = "second-audit=" + secondAudit.error();
                }

                uploadManager.clear();
                ForgeMdicCommandStats afterHeapClear = this.createStatusSnapshot();
                this.lastStressHeapClearOk = !afterHeapClear.commandListValid()
                        && !afterHeapClear.commandBufferCreated()
                        && ("HEAP_MISSING".equals(afterHeapClear.lastStaleReason()) || "COMMAND_LIST_MISSING".equals(afterHeapClear.lastStaleReason()));
                if (!this.lastStressHeapClearOk && "none".equals(error)) {
                    error = "heap-clear-did-not-reset-mdic-state";
                }

                ForgeVoxyRuntimeOverrides.setGeometryGpuUpload(true);
                uploadManager.processForDebugCommand(8);
                ForgeMdicCommandPlanner.PlanResult thirdPlan = this.planSample();
                boolean thirdBuild = thirdPlan.success() && this.buildBuffer();
                ForgeMdicCommandAuditResult thirdAudit = this.audit();
                boolean thirdRebuildOk = thirdPlan.success() && thirdBuild;
                boolean thirdAuditOk = thirdBuild && thirdAudit.success();
                this.lastStressRebuildOk = secondRebuildOk && thirdRebuildOk;
                this.lastStressReauditOk = secondAuditOk && thirdAuditOk;
                this.lastStressBucketAwareOk = this.lastStressBucketAwareOk
                        && thirdPlan.success()
                        && this.commandList.bucketAware()
                        && this.commandList.bucketCommands() > 0
                        && this.commandList.sectionCommands() == 0;
                this.lastStressBucketAuditOk = this.lastStressBucketAuditOk
                        && thirdAudit.success()
                        && thirdAudit.invalidBucketMaskCommands() == 0
                        && thirdAudit.invalidBucketRangeCommands() == 0
                        && thirdAudit.invalidBucketOffsetCommands() == 0;
                if (!thirdRebuildOk && "none".equals(error)) {
                    error = "post-heap-rebuild=" + (thirdPlan.success() ? this.lastError : thirdPlan.error());
                } else if (!thirdAuditOk && "none".equals(error)) {
                    error = "post-heap-audit=" + thirdAudit.error();
                } else if (!this.lastStressBucketAwareOk && "none".equals(error)) {
                    error = "post-heap-bucket-aware-plan-failed";
                } else if (!this.lastStressBucketAuditOk && "none".equals(error)) {
                    error = "post-heap-bucket-audit-failed";
                }

                this.lastStressCommandCount = this.commandList.commandCount();
                this.lastStressCommandRecords = this.commandList.recordCount();
                this.lastStressInvalidCommands = thirdAudit.invalidCommands();

                ForgeVoxyRuntimeOverrides.applyLodBuiltSectionPreset();
                this.instance.getGpuMeshUploadManager().clear();
                boolean builtSectionSourceOk = ForgeGpuMeshUploadManager.getConfiguredSource() == SimpleGpuMeshSource.BUILT_SECTION;
                ForgeVoxyRuntimeOverrides.applyGlHeapReadbackPreset();
                this.instance.getGpuMeshUploadManager().clear();
                boolean glHeapSourceOk = ForgeGpuMeshUploadManager.getConfiguredSource() == SimpleGpuMeshSource.GL_HEAP_READBACK;
                this.lastStressSourceRegressionOk = builtSectionSourceOk && glHeapSourceOk;
                if (!this.lastStressSourceRegressionOk && "none".equals(error)) {
                    error = "source-regression-failed";
                }

                ForgeVoxyRuntimeOverrides.applyMdicSkeletonPreset();
                this.instance.ensureActiveWorldSkeletonForCurrentWorldIfAllowed();
                uploadManager.processForDebugCommand(4);
            }
        } catch (RuntimeException e) {
            error = e.getClass().getSimpleName() + ": " + e.getMessage();
        }

        boolean success = "none".equals(error)
                && this.lastStressPlanOk
                && this.lastStressBuildOk
                && this.lastStressAuditOk
                && this.lastStressClearOk
                && this.lastStressHeapClearOk
                && this.lastStressRebuildOk
                && this.lastStressReauditOk
                && this.lastStressSourceRegressionOk
                && this.lastStressBucketAwareOk
                && this.lastStressBucketAuditOk;
        this.lastStressDurationMs = elapsedMs(start);
        this.lastStressError = success ? "none" : error;
        if (!success) {
            this.stressFailures++;
        }
        return this.createStatusSnapshot();
    }

    ForgeMdicCommandStats createStatusSnapshot() {
        ForgeGpuGeometryHeap heap = this.instance.getGpuGeometryUploadManager().getHeapForDebugReadback();
        boolean heapCreated = heap != null && heap.isCreated();
        long currentGeneration = heapCreated ? heap.generation() : -1L;
        boolean commandListValid = this.commandList.isValid();
        String currentDimension = currentDimensionId();
        boolean listStale = commandListValid && (this.commandList.isStale(currentGeneration) || this.commandList.isDimensionMismatch(currentDimension));
        boolean bufferStale = this.commandBuffer.isStale(currentGeneration, currentDimension);
        String staleReason = staleReason(heapCreated, currentGeneration, currentDimension, commandListValid, listStale, bufferStale);
        return new ForgeMdicCommandStats(
                ForgeMdicCommandLayout.STAGE,
                ForgeMdicCommandLayout.LAYOUT_VERSION,
                ForgeMdicCommandLayout.WORDS_PER_COMMAND,
                ForgeMdicCommandLayout.BYTES_PER_COMMAND,
                ForgeMdicCommandConfig.isEnabled(),
                false,
                heap != null,
                heapCreated,
                currentGeneration,
                currentDimension,
                commandListValid,
                listStale,
                bufferStale,
                staleReason,
                this.commandList.bucketAware(),
                this.commandList.includedTranslucent(),
                this.commandList.includedDoubleSided(),
                this.commandList.includedDirectional(),
                this.commandList.commandCount(),
                this.commandList.sectionCount(),
                this.commandList.bucketCommands(),
                this.commandList.sectionCommands(),
                this.commandList.recordCount(),
                this.commandList.vertexCount(),
                this.commandList.minRecordCount(),
                this.commandList.maxRecordCount(),
                this.commandList.avgRecordCount(),
                this.commandList.nonEmptyBucketCommands(),
                this.commandList.emptyBucketCommands(),
                this.commandList.bucketMaskOr(),
                this.commandList.bucketMaskAnd(),
                this.commandList.minGeometryPtr(),
                this.commandList.maxGeometryPtr(),
                this.commandList.commandsPerSectionMin(),
                this.commandList.commandsPerSectionMax(),
                this.commandList.bucketCommandCount(0),
                this.commandList.bucketCommandCount(1),
                this.commandList.bucketCommandCount(2),
                this.commandList.bucketCommandCount(3),
                this.commandList.bucketCommandCount(4),
                this.commandList.bucketCommandCount(5),
                this.commandList.bucketCommandCount(6),
                this.commandList.bucketCommandCount(7),
                this.commandList.skippedSections(),
                this.commandList.skippedRecords(),
                this.commandList.skippedTranslucentCommands(),
                this.commandList.skippedEmptyBuckets(),
                this.commandList.skippedBucketCommands(),
                this.commandList.selectionMode(),
                this.commandList.planCandidateSections(),
                this.commandList.planAcceptedSections(),
                this.commandList.dimensionId(),
                this.commandList.heapGeneration(),
                this.commandBuffer.isCreated(),
                this.commandBuffer.bytes(),
                this.commandBuffer.heapGeneration(),
                this.commandBuffer.dimensionId(),
                this.lastPlanDurationMs,
                this.lastBuildBufferDurationMs,
                this.lastError,
                this.lastCommandBufferMatch
                        && this.lastLayoutMatch
                        && this.lastGenerationMatch
                        && this.lastDimensionMatch
                        && this.lastInvalidCommands == 0
                        && this.lastInvalidBucketMaskCommands == 0
                        && this.lastInvalidBucketRangeCommands == 0
                        && this.lastInvalidBucketOffsetCommands == 0
                        && this.auditRuns > 0
                        && this.auditFailures == 0,
                this.auditRuns,
                this.auditFailures,
                this.lastAuditError,
                this.lastAuditDurationMs,
                this.lastCommandBufferMatch,
                this.lastLayoutMatch,
                this.lastGenerationMatch,
                this.lastDimensionMatch,
                this.lastInvalidCommands,
                this.lastInvalidLayoutCommands,
                this.lastInvalidGenerationCommands,
                this.lastInvalidDimensionCommands,
                this.lastInvalidBucketMaskCommands,
                this.lastInvalidBucketRangeCommands,
                this.lastInvalidBucketOffsetCommands,
                this.lastInvalidGeometryPtrCommands,
                this.lastAuditedCommands,
                this.lastAuditedRecords,
                this.lastAuditedBytes,
                this.lastAuditHeapGeneration,
                this.lastAuditDimension,
                this.stressRuns,
                this.stressFailures,
                this.lastStressError,
                this.lastStressDurationMs,
                this.lastStressPlanOk,
                this.lastStressBuildOk,
                this.lastStressAuditOk,
                this.lastStressClearOk,
                this.lastStressHeapClearOk,
                this.lastStressRebuildOk,
                this.lastStressReauditOk,
                this.lastStressSourceRegressionOk,
                this.lastStressBucketAwareOk,
                this.lastStressBucketAuditOk,
                this.lastStressCommandCount,
                this.lastStressCommandRecords,
                this.lastStressInvalidCommands
        );
    }

    ForgeMdicCommandList commandListForDebugDraw() {
        return this.commandList;
    }

    ForgeMdicCommandBuffer commandBufferForDebugDraw() {
        return this.commandBuffer;
    }

    private String staleReason(boolean heapCreated, long currentGeneration, String currentDimension, boolean commandListValid, boolean listStale, boolean bufferStale) {
        if (!heapCreated) {
            return "HEAP_MISSING";
        }
        if (!commandListValid) {
            return "COMMAND_LIST_MISSING";
        }
        if (this.commandList.isStale(currentGeneration) || this.commandBuffer.isCreated() && this.commandBuffer.heapGeneration() != currentGeneration) {
            return "HEAP_GENERATION_CHANGED";
        }
        if (this.commandList.isDimensionMismatch(currentDimension) || this.commandBuffer.isCreated() && !this.commandBuffer.dimensionId().equals(currentDimension)) {
            return "DIMENSION_CHANGED";
        }
        if (!this.commandBuffer.isCreated()) {
            return "COMMAND_BUFFER_MISSING";
        }
        if (listStale || bufferStale) {
            return "UNKNOWN_STALE";
        }
        return "none";
    }

    private ForgeMdicCommandAuditResult compareCommandBuffer(long start) {
        ForgeGpuGeometryHeap heap = this.instance.getGpuGeometryUploadManager().getHeapForDebugReadback();
        if (heap == null || !heap.isCreated()) {
            return ForgeMdicCommandAuditResult.failure("heap-not-created", elapsedMs(start));
        }
        String currentDimension = currentDimensionId();
        if (this.commandList.isStale(heap.generation())) {
            return ForgeMdicCommandAuditResult.failure("command-list-stale", elapsedMs(start));
        }
        if (this.commandList.isDimensionMismatch(currentDimension)) {
            return ForgeMdicCommandAuditResult.failure("dimension-mismatch:" + this.commandList.dimensionId() + "!=" + currentDimension, elapsedMs(start));
        }
        if (!this.commandBuffer.matches(this.commandList)) {
            return ForgeMdicCommandAuditResult.failure("command-buffer-generation-mismatch", elapsedMs(start));
        }
        int[] words = this.commandBuffer.readbackWords();
        int invalid = 0;
        int invalidLayout = 0;
        int invalidGeneration = 0;
        int invalidDimension = 0;
        int invalidBucketMask = 0;
        int invalidBucketRange = 0;
        int invalidBucketOffset = 0;
        int invalidGeometryPtr = 0;
        long auditedRecords = 0L;
        int commandCount = this.commandList.commandCount();
        boolean layoutMatch = words.length == commandCount * ForgeMdicCommandLayout.WORDS_PER_COMMAND
                && this.commandBuffer.bytes() == (long) commandCount * ForgeMdicCommandLayout.BYTES_PER_COMMAND;
        if (!layoutMatch) {
            invalid++;
            invalidLayout++;
        }
        boolean generationMatch = this.commandList.heapGeneration() == heap.generation()
                && this.commandBuffer.heapGeneration() == heap.generation();
        boolean dimensionMatch = this.commandList.dimensionId().equals(currentDimension)
                && this.commandBuffer.dimensionId().equals(currentDimension);
        if (!generationMatch) {
            invalidGeneration++;
        }
        if (!dimensionMatch) {
            invalidDimension++;
        }
        for (int i = 0; i < commandCount && i * ForgeMdicCommandLayout.WORDS_PER_COMMAND + ForgeMdicCommandLayout.WORDS_PER_COMMAND <= words.length; i++) {
            ForgeMdicCommand actual = ForgeMdicCommand.fromWords(words, i * ForgeMdicCommandLayout.WORDS_PER_COMMAND);
            ForgeMdicCommand expected = this.commandList.commands().get(i);
            auditedRecords += Math.max(0, actual.recordCount());
            if (!expected.matches(actual)) {
                invalid++;
            }
            String validation = actual.validate(this.commandList.heapGeneration());
            if (!"none".equals(validation)) {
                invalid++;
                if (validation.contains("encode") || validation.contains("section") || validation.contains("record") || validation.contains("metadata") || validation.contains("lod")) {
                    invalidLayout++;
                }
                if (validation.contains("generation")) {
                    invalidGeneration++;
                }
                if (validation.contains("bucket")) {
                    invalidBucketMask++;
                }
                if (validation.contains("geometry-ptr")) {
                    invalidGeometryPtr++;
                }
            }
            BucketValidation bucketValidation = validateBucketCommand(heap, actual);
            if (!bucketValidation.success()) {
                invalid++;
                invalidBucketMask += bucketValidation.invalidMask();
                invalidBucketRange += bucketValidation.invalidRange();
                invalidBucketOffset += bucketValidation.invalidOffset();
            }
            if (expected.recordCount() != actual.recordCount()
                    || expected.geometryPtr() != actual.geometryPtr()
                    || expected.recordStart() != actual.recordStart()
                    || expected.sectionId() != actual.sectionId()
                    || expected.metadataIndex() != actual.metadataIndex()
                    || expected.lodLevel() != actual.lodLevel()
                    || expected.flags() != actual.flags()) {
                invalidLayout++;
            }
            if (actual.generation() != (int) heap.generation()) {
                invalidGeneration++;
            }
            if (actual.recordCount() > 0 && actual.bucketMask() == 0) {
                invalidBucketMask++;
            }
            if (this.commandList.bucketAware() && !actual.isBucketCommand()) {
                invalidBucketMask++;
            }
            if (actual.geometryPtr() < 0 || actual.geometryPtr() % 128 != 0) {
                invalidGeometryPtr++;
            }
        }
        long bytes = (long) words.length * Integer.BYTES;
        if (auditedRecords != this.commandList.recordCount()) {
            invalid++;
            invalidLayout++;
        }
        boolean match = invalid == 0
                && layoutMatch
                && generationMatch
                && dimensionMatch
                && bytes == this.commandBuffer.bytes()
                && auditedRecords == this.commandList.recordCount();
        return new ForgeMdicCommandAuditResult(
                match,
                match ? "none" : "command-buffer-mismatch",
                elapsedMs(start),
                match,
                layoutMatch && invalidLayout == 0,
                generationMatch && invalidGeneration == 0,
                dimensionMatch && invalidDimension == 0,
                invalid,
                invalidLayout,
                invalidGeneration,
                invalidDimension,
                invalidBucketMask,
                invalidBucketRange,
                invalidBucketOffset,
                invalidGeometryPtr,
                commandCount,
                auditedRecords,
                bytes,
                this.commandList.heapGeneration(),
                this.commandList.dimensionId()
        );
    }

    private static BucketValidation validateBucketCommand(ForgeGpuGeometryHeap heap, ForgeMdicCommand command) {
        if (!command.isBucketCommand()) {
            return BucketValidation.ok();
        }
        int invalidMask = 0;
        int invalidRange = 0;
        int invalidOffset = 0;
        int bucket = command.bucketIndex();
        if (bucket < 0) {
            invalidMask++;
            return new BucketValidation(invalidMask, invalidRange, invalidOffset);
        }
        if (!bucketAllowedByConfig(bucket)) {
            invalidMask++;
        }
        int[] words = heap.readbackMetadata(command.metadataIndex());
        ForgeGpuGeometryDecodedMetadata metadata = ForgeGpuGeometryDecodedMetadata.decode(words);
        int start = metadata.offsets()[bucket];
        int end = bucket == ForgeGpuGeometryDecodedMetadata.BUCKET_COUNT - 1
                ? metadata.itemCount()
                : metadata.offsets()[bucket + 1];
        int count = Math.max(0, end - start);
        if (command.recordStart() != start) {
            invalidOffset++;
        }
        if (command.recordCount() != count) {
            invalidOffset++;
        }
        if (command.recordStart() < 0 || command.recordCount() < 0 || command.recordStart() + command.recordCount() > metadata.itemCount()) {
            invalidRange++;
        }
        if (count <= 0) {
            invalidRange++;
        }
        return new BucketValidation(invalidMask, invalidRange, invalidOffset);
    }

    private static boolean bucketAllowedByConfig(int bucket) {
        if (bucket == 0) {
            return ForgeMdicCommandConfig.includeTranslucent();
        }
        if (bucket == 1) {
            return ForgeMdicCommandConfig.includeDoubleSided();
        }
        return bucket >= 2 && bucket < ForgeGpuGeometryDecodedMetadata.BUCKET_COUNT && ForgeMdicCommandConfig.includeDirectional();
    }

    private void recordAuditResult(ForgeMdicCommandAuditResult result) {
        this.lastAuditDurationMs = result.durationMs();
        this.lastAuditError = result.success() ? "none" : result.error();
        this.lastCommandBufferMatch = result.commandBufferMatch();
        this.lastLayoutMatch = result.layoutMatch();
        this.lastGenerationMatch = result.generationMatch();
        this.lastDimensionMatch = result.dimensionMatch();
        this.lastInvalidCommands = result.invalidCommands();
        this.lastInvalidLayoutCommands = result.invalidLayoutCommands();
        this.lastInvalidGenerationCommands = result.invalidGenerationCommands();
        this.lastInvalidDimensionCommands = result.invalidDimensionCommands();
        this.lastInvalidBucketMaskCommands = result.invalidBucketMaskCommands();
        this.lastInvalidBucketRangeCommands = result.invalidBucketRangeCommands();
        this.lastInvalidBucketOffsetCommands = result.invalidBucketOffsetCommands();
        this.lastInvalidGeometryPtrCommands = result.invalidGeometryPtrCommands();
        this.lastAuditedCommands = result.auditedCommands();
        this.lastAuditedRecords = result.auditedRecords();
        this.lastAuditedBytes = result.auditedBytes();
        this.lastAuditHeapGeneration = result.heapGeneration();
        this.lastAuditDimension = result.dimensionId();
        if (!result.success()) {
            this.auditFailures++;
        }
    }

    private static String currentDimensionId() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return "none";
        }
        return minecraft.level.dimension().location().toString();
    }

    private static double elapsedMs(long start) {
        return (System.nanoTime() - start) / 1_000_000.0D;
    }

    private record BucketValidation(int invalidMask, int invalidRange, int invalidOffset) {
        static BucketValidation ok() {
            return new BucketValidation(0, 0, 0);
        }

        boolean success() {
            return this.invalidMask == 0 && this.invalidRange == 0 && this.invalidOffset == 0;
        }
    }
}
