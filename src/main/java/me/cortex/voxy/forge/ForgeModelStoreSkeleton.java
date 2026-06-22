package me.cortex.voxy.forge;

import com.mojang.blaze3d.systems.RenderSystem;
import me.cortex.voxy.common.world.WorldEngine;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

final class ForgeModelStoreSkeleton {
    static final String STAGE = "G6_10_PLACEHOLDER_MODELSTORE_SKELETON";
    private static final int MAX_PLACEHOLDER_RECORDS = 1 << 16;

    private final ForgeVoxyInstance instance;
    private final ForgeModelDataBuffer dataBuffer = new ForgeModelDataBuffer();
    private List<ForgeModelStoreRecord> records = List.of();
    private long buildRuns;
    private long clearRuns;
    private long generation = -1L;
    private String dimensionId = "none";
    private String staleReason = "none";
    private String lastBuildError = "none";
    private double lastBuildDurationMs;
    private long auditRuns;
    private long auditFailures;
    private ForgeModelStoreAuditResult lastAudit = ForgeModelStoreAuditResult.failure("none", 0.0D);

    ForgeModelStoreSkeleton(ForgeVoxyInstance instance) {
        this.instance = instance;
    }

    ForgeModelStoreStats build() {
        this.buildRuns++;
        long start = System.nanoTime();
        if (!RenderSystem.isOnRenderThread()) {
            this.lastBuildError = "not-render-thread";
            this.lastBuildDurationMs = elapsedMs(start);
            return this.createStatusSnapshot();
        }

        List<ForgeVoxyModelIdMapper.ModelIdMapping> mappings = ForgeVoxyModelIdMapper.INSTANCE.createSnapshot(MAX_PLACEHOLDER_RECORDS);
        if (mappings.isEmpty()) {
            this.records = List.of();
            this.dataBuffer.closeOnRenderThread();
            this.lastBuildError = "no-placeholder-model-ids";
            this.lastBuildDurationMs = elapsedMs(start);
            return this.createStatusSnapshot();
        }

        List<ForgeModelStoreRecord> newRecords = new ArrayList<>(mappings.size());
        for (ForgeVoxyModelIdMapper.ModelIdMapping mapping : mappings) {
            newRecords.add(ForgeModelStoreRecord.placeholder(mapping));
        }

        long nextGeneration = this.generation + 1L;
        String dimension = currentDimension();
        boolean uploaded = this.dataBuffer.upload(newRecords, nextGeneration, dimension);
        this.records = List.copyOf(newRecords);
        if (uploaded) {
            this.generation = nextGeneration;
            this.dimensionId = dimension;
            this.staleReason = "none";
            this.lastBuildError = "none";
        } else {
            this.lastBuildError = this.dataBuffer.lastUploadError();
        }
        this.lastBuildDurationMs = elapsedMs(start);
        this.lastAudit = ForgeModelStoreAuditResult.failure("none", 0.0D);
        return this.createStatusSnapshot();
    }

    ForgeModelStoreAuditResult audit() {
        this.auditRuns++;
        long start = System.nanoTime();
        ForgeModelStoreAuditResult result;
        if (!RenderSystem.isOnRenderThread()) {
            result = ForgeModelStoreAuditResult.failure("not-render-thread", elapsedMs(start));
        } else if (this.records.isEmpty()) {
            result = ForgeModelStoreAuditResult.failure("no-placeholder-records", elapsedMs(start));
        } else if (!this.dataBuffer.isModelDataCreated()) {
            result = ForgeModelStoreAuditResult.failure("model-data-buffer-missing", elapsedMs(start));
        } else {
            try {
                result = this.compareReadback(start);
            } catch (RuntimeException e) {
                result = ForgeModelStoreAuditResult.failure(e.getClass().getSimpleName() + ": " + e.getMessage(), elapsedMs(start));
            }
        }
        this.lastAudit = result;
        if (!result.success()) {
            this.auditFailures++;
        }
        return result;
    }

    ForgeModelStoreStats createStatusSnapshot() {
        ForgeModelStoreRecord sample = this.records.isEmpty() ? null : this.records.get(0);
        boolean dataCreated = this.dataBuffer.isModelDataCreated();
        boolean colourCreated = this.dataBuffer.isModelColourCreated();
        boolean storeReady = !this.records.isEmpty() && dataCreated && "none".equals(this.lastBuildError);
        return new ForgeModelStoreStats(
                STAGE,
                ForgeModelStoreLayout.LAYOUT_VERSION,
                ForgeModelStoreLayout.FORMAL_LAYOUT_COMPATIBLE,
                ForgeModelStoreLayout.LAYOUT_VERSION,
                ForgeOriginalVoxyModelStoreLayoutSpec.LAYOUT_VERSION,
                ForgeOriginalVoxyModelStoreLayoutSpec.MODEL_RECORD_BYTES,
                ForgeOriginalVoxyModelStoreLayoutSpec.FORMAL_LAYOUT_KNOWN,
                ForgeOriginalVoxyModelStoreLayoutSpec.FIELD_MAPPING_READY,
                ForgeOriginalVoxyModelStoreLayoutSpec.FACE_DATA_MAPPING_READY,
                ForgeOriginalVoxyModelStoreLayoutSpec.ATLAS_UV_MAPPING_READY,
                ForgeOriginalVoxyModelStoreLayoutSpec.MATERIAL_MAPPING_READY,
                this.buildRuns,
                this.clearRuns,
                this.lastBuildError,
                this.lastBuildDurationMs,
                storeReady,
                this.records.size(),
                ForgeModelStoreLayout.BYTES,
                dataCreated,
                dataCreated,
                this.dataBuffer.modelDataBytes(),
                colourCreated,
                colourCreated,
                this.dataBuffer.modelColourBytes(),
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
                this.generation,
                this.dimensionId,
                this.dataBuffer.isStale(this.generation, currentDimension()),
                this.auditRuns,
                this.auditFailures,
                this.lastAudit.success(),
                this.lastAudit.error(),
                this.lastAudit.durationMs(),
                this.lastAudit.auditedRecords(),
                this.lastAudit.auditedBytes(),
                this.lastAudit.invalidRecords(),
                this.lastAudit.modelDataBufferMatch(),
                this.lastAudit.modelColourBufferMatch(),
                sample == null ? -1 : sample.modelId(),
                sample == null ? -1 : sample.blockStateId(),
                sample == null ? "none" : this.blockStateString(sample.blockStateId()).orElse("unavailable"),
                true,
                false,
                false,
                false,
                false
        );
    }

    void clear() {
        this.clearRuns++;
        this.records = List.of();
        this.generation = -1L;
        this.dimensionId = "none";
        this.staleReason = "none";
        this.lastBuildError = "none";
        this.lastBuildDurationMs = 0.0D;
        this.auditRuns = 0L;
        this.auditFailures = 0L;
        this.lastAudit = ForgeModelStoreAuditResult.failure("none", 0.0D);
        this.dataBuffer.close();
    }

    void markStale(String reason) {
        this.records = List.of();
        this.generation = -1L;
        this.dimensionId = "none";
        this.staleReason = reason == null || reason.isBlank() ? "stale" : reason;
        this.lastBuildError = this.staleReason;
        this.lastAudit = ForgeModelStoreAuditResult.failure(this.staleReason, 0.0D);
        this.dataBuffer.close();
    }

    private ForgeModelStoreAuditResult compareReadback(long start) {
        int[] modelWords = this.dataBuffer.readbackModelDataWords();
        int[] colours = this.dataBuffer.readbackModelColours();
        int invalidRecords = 0;
        boolean dataMatch = modelWords.length == this.records.size() * ForgeModelStoreLayout.WORDS;
        boolean colourMatch = colours.length == this.records.size();

        if (dataMatch) {
            for (int i = 0; i < this.records.size(); i++) {
                ForgeModelStoreRecord record = this.records.get(i);
                if (!record.matchesWords(modelWords, i * ForgeModelStoreLayout.WORDS)) {
                    invalidRecords++;
                    dataMatch = false;
                }
                if (colourMatch && colours[i] != record.debugColour()) {
                    invalidRecords++;
                    colourMatch = false;
                }
            }
        } else {
            invalidRecords += this.records.size();
        }

        boolean success = dataMatch && colourMatch && invalidRecords == 0;
        return new ForgeModelStoreAuditResult(
                success,
                success ? "none" : "buffer-mismatch",
                elapsedMs(start),
                this.records.size(),
                this.dataBuffer.modelDataBytes(),
                invalidRecords,
                dataMatch,
                colourMatch,
                this.generation,
                this.dimensionId
        );
    }

    private Optional<String> blockStateString(int blockStateId) {
        Optional<WorldEngine> engine = this.instance.getCurrentEngineOptional();
        if (engine.isPresent()) {
            try {
                return Optional.of(engine.get().getMapper().getBlockStateFromBlockId(blockStateId).toString());
            } catch (RuntimeException ignored) {
                // Fall through to the vanilla registry best-effort path below.
            }
        }
        try {
            BlockState state = Block.BLOCK_STATE_REGISTRY.byId(blockStateId);
            return state == null ? Optional.empty() : Optional.of(state.toString());
        } catch (RuntimeException ignored) {
            return Optional.empty();
        }
    }

    private static String currentDimension() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.level == null ? "none" : minecraft.level.dimension().location().toString();
    }

    private static double elapsedMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000.0D;
    }
}
