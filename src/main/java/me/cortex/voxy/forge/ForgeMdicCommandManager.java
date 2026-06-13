package me.cortex.voxy.forge;

import com.mojang.blaze3d.systems.RenderSystem;
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
    private int lastInvalidCommands;
    private int lastAuditedCommands;
    private long lastAuditedBytes;
    private long lastAuditHeapGeneration = -1L;
    private String lastAuditDimension = "none";

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
        this.lastInvalidCommands = 0;
        this.lastAuditedCommands = 0;
        this.lastAuditedBytes = 0L;
        this.lastAuditHeapGeneration = -1L;
        this.lastAuditDimension = "none";
    }

    ForgeMdicCommandStats createStatusSnapshot() {
        ForgeGpuGeometryHeap heap = this.instance.getGpuGeometryUploadManager().getHeapForDebugReadback();
        boolean heapCreated = heap != null && heap.isCreated();
        long currentGeneration = heapCreated ? heap.generation() : -1L;
        boolean commandListValid = this.commandList.isValid();
        String currentDimension = currentDimensionId();
        boolean stale = commandListValid && (this.commandList.isStale(currentGeneration) || this.commandList.isDimensionMismatch(currentDimension));
        return new ForgeMdicCommandStats(
                ForgeMdicCommandLayout.STAGE,
                ForgeMdicCommandConfig.isEnabled(),
                false,
                heap != null,
                heapCreated,
                currentGeneration,
                commandListValid,
                stale,
                this.commandList.commandCount(),
                this.commandList.recordCount(),
                this.commandList.vertexCount(),
                this.commandList.skippedSections(),
                this.commandList.skippedRecords(),
                this.commandList.selectionMode(),
                this.commandList.dimensionId(),
                this.commandBuffer.isCreated(),
                this.commandBuffer.bytes(),
                this.commandBuffer.heapGeneration(),
                this.commandBuffer.dimensionId(),
                this.lastPlanDurationMs,
                this.lastBuildBufferDurationMs,
                this.lastError,
                this.lastCommandBufferMatch && this.lastInvalidCommands == 0 && this.auditRuns > 0 && this.auditFailures == 0,
                this.auditRuns,
                this.auditFailures,
                this.lastAuditError,
                this.lastAuditDurationMs,
                this.lastCommandBufferMatch,
                this.lastInvalidCommands,
                this.lastAuditedCommands,
                this.lastAuditedBytes,
                this.lastAuditHeapGeneration,
                this.lastAuditDimension
        );
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
        int commandCount = this.commandList.commandCount();
        if (words.length != commandCount * ForgeMdicCommandLayout.WORDS) {
            invalid++;
        }
        for (int i = 0; i < commandCount && i * ForgeMdicCommandLayout.WORDS + ForgeMdicCommandLayout.WORDS <= words.length; i++) {
            ForgeMdicCommand actual = ForgeMdicCommand.fromWords(words, i * ForgeMdicCommandLayout.WORDS);
            ForgeMdicCommand expected = this.commandList.commands().get(i);
            if (!expected.matches(actual)) {
                invalid++;
            }
        }
        long bytes = (long) words.length * Integer.BYTES;
        boolean match = invalid == 0 && bytes == this.commandBuffer.bytes();
        return new ForgeMdicCommandAuditResult(
                match,
                match ? "none" : "command-buffer-mismatch",
                elapsedMs(start),
                match,
                invalid,
                commandCount,
                bytes,
                this.commandList.heapGeneration(),
                this.commandList.dimensionId()
        );
    }

    private void recordAuditResult(ForgeMdicCommandAuditResult result) {
        this.lastAuditDurationMs = result.durationMs();
        this.lastAuditError = result.success() ? "none" : result.error();
        this.lastCommandBufferMatch = result.commandBufferMatch();
        this.lastInvalidCommands = result.invalidCommands();
        this.lastAuditedCommands = result.auditedCommands();
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
}
