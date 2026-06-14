package me.cortex.voxy.forge;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class ForgeMdicCommandList {
    private static final ForgeMdicCommandList EMPTY = new ForgeMdicCommandList(Collections.emptyList(), -1L, "none", 0L, 0, "none", 0, 0, 0, 0);

    private final List<ForgeMdicCommand> commands;
    private final long heapGeneration;
    private final String dimensionId;
    private final long buildTimeMillis;
    private final long recordCount;
    private final String selectionMode;
    private final int skippedSections;
    private final long skippedRecords;
    private final int planCandidateSections;
    private final int planAcceptedSections;
    private final int minRecordCount;
    private final int maxRecordCount;
    private final double avgRecordCount;
    private final int nonEmptyBucketCommands;
    private final int emptyBucketCommands;
    private final int bucketMaskOr;
    private final int bucketMaskAnd;
    private final int minGeometryPtr;
    private final int maxGeometryPtr;

    private ForgeMdicCommandList(List<ForgeMdicCommand> commands, long heapGeneration, String dimensionId, long buildTimeMillis, long recordCount, String selectionMode, int skippedSections, long skippedRecords, int planCandidateSections, int planAcceptedSections) {
        this.commands = Collections.unmodifiableList(new ArrayList<>(commands));
        this.heapGeneration = heapGeneration;
        this.dimensionId = dimensionId == null || dimensionId.isBlank() ? "none" : dimensionId;
        this.buildTimeMillis = buildTimeMillis;
        this.recordCount = Math.max(0L, recordCount);
        this.selectionMode = selectionMode == null ? "unknown" : selectionMode;
        this.skippedSections = Math.max(0, skippedSections);
        this.skippedRecords = Math.max(0L, skippedRecords);
        this.planCandidateSections = Math.max(0, planCandidateSections);
        this.planAcceptedSections = Math.max(0, planAcceptedSections);

        int minRecords = Integer.MAX_VALUE;
        int maxRecords = 0;
        int nonEmptyBuckets = 0;
        int emptyBuckets = 0;
        int maskOr = 0;
        int maskAnd = this.commands.isEmpty() ? 0 : -1;
        int minPtr = Integer.MAX_VALUE;
        int maxPtr = 0;
        long totalRecords = 0L;
        for (ForgeMdicCommand command : this.commands) {
            int records = Math.max(0, command.recordCount());
            minRecords = Math.min(minRecords, records);
            maxRecords = Math.max(maxRecords, records);
            totalRecords += records;
            if (command.bucketMask() == 0) {
                emptyBuckets++;
            } else {
                nonEmptyBuckets++;
            }
            maskOr |= command.bucketMask();
            maskAnd &= command.bucketMask();
            minPtr = Math.min(minPtr, command.geometryPtr());
            maxPtr = Math.max(maxPtr, command.geometryPtr());
        }
        this.minRecordCount = this.commands.isEmpty() ? 0 : minRecords;
        this.maxRecordCount = maxRecords;
        this.avgRecordCount = this.commands.isEmpty() ? 0.0D : (double) totalRecords / (double) this.commands.size();
        this.nonEmptyBucketCommands = nonEmptyBuckets;
        this.emptyBucketCommands = emptyBuckets;
        this.bucketMaskOr = maskOr;
        this.bucketMaskAnd = this.commands.isEmpty() ? 0 : maskAnd;
        this.minGeometryPtr = this.commands.isEmpty() ? 0 : minPtr;
        this.maxGeometryPtr = maxPtr;
    }

    static ForgeMdicCommandList empty() {
        return EMPTY;
    }

    static ForgeMdicCommandList of(List<ForgeMdicCommand> commands, long heapGeneration, String dimensionId, long recordCount, String selectionMode, int skippedSections, long skippedRecords, int planCandidateSections, int planAcceptedSections) {
        if (commands == null || commands.isEmpty()) {
            return EMPTY;
        }
        return new ForgeMdicCommandList(commands, heapGeneration, dimensionId, System.currentTimeMillis(), recordCount, selectionMode, skippedSections, skippedRecords, planCandidateSections, planAcceptedSections);
    }

    boolean isValid() {
        return !this.commands.isEmpty() && this.recordCount > 0;
    }

    boolean isStale(long currentHeapGeneration) {
        return this.isValid() && this.heapGeneration != currentHeapGeneration;
    }

    boolean isDimensionMismatch(String currentDimension) {
        return this.isValid() && currentDimension != null && !this.dimensionId.equals(currentDimension);
    }

    List<ForgeMdicCommand> commands() {
        return this.commands;
    }

    int commandCount() {
        return this.commands.size();
    }

    long recordCount() {
        return this.recordCount;
    }

    long vertexCount() {
        return this.recordCount * 6L;
    }

    long heapGeneration() {
        return this.heapGeneration;
    }

    String dimensionId() {
        return this.dimensionId;
    }

    long buildTimeMillis() {
        return this.buildTimeMillis;
    }

    String selectionMode() {
        return this.selectionMode;
    }

    int skippedSections() {
        return this.skippedSections;
    }

    long skippedRecords() {
        return this.skippedRecords;
    }

    int planCandidateSections() {
        return this.planCandidateSections;
    }

    int planAcceptedSections() {
        return this.planAcceptedSections;
    }

    int minRecordCount() {
        return this.minRecordCount;
    }

    int maxRecordCount() {
        return this.maxRecordCount;
    }

    double avgRecordCount() {
        return this.avgRecordCount;
    }

    int nonEmptyBucketCommands() {
        return this.nonEmptyBucketCommands;
    }

    int emptyBucketCommands() {
        return this.emptyBucketCommands;
    }

    int bucketMaskOr() {
        return this.bucketMaskOr;
    }

    int bucketMaskAnd() {
        return this.bucketMaskAnd;
    }

    int minGeometryPtr() {
        return this.minGeometryPtr;
    }

    int maxGeometryPtr() {
        return this.maxGeometryPtr;
    }
}
