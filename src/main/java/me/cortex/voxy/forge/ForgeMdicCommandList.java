package me.cortex.voxy.forge;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class ForgeMdicCommandList {
    private static final ForgeMdicCommandList EMPTY = new ForgeMdicCommandList(Collections.emptyList(), -1L, "none", 0L, 0, "none", 0, 0);

    private final List<ForgeMdicCommand> commands;
    private final long heapGeneration;
    private final String dimensionId;
    private final long buildTimeMillis;
    private final long recordCount;
    private final String selectionMode;
    private final int skippedSections;
    private final long skippedRecords;

    private ForgeMdicCommandList(List<ForgeMdicCommand> commands, long heapGeneration, String dimensionId, long buildTimeMillis, long recordCount, String selectionMode, int skippedSections, long skippedRecords) {
        this.commands = Collections.unmodifiableList(new ArrayList<>(commands));
        this.heapGeneration = heapGeneration;
        this.dimensionId = dimensionId == null || dimensionId.isBlank() ? "none" : dimensionId;
        this.buildTimeMillis = buildTimeMillis;
        this.recordCount = Math.max(0L, recordCount);
        this.selectionMode = selectionMode == null ? "unknown" : selectionMode;
        this.skippedSections = Math.max(0, skippedSections);
        this.skippedRecords = Math.max(0L, skippedRecords);
    }

    static ForgeMdicCommandList empty() {
        return EMPTY;
    }

    static ForgeMdicCommandList of(List<ForgeMdicCommand> commands, long heapGeneration, String dimensionId, long recordCount, String selectionMode, int skippedSections, long skippedRecords) {
        if (commands == null || commands.isEmpty()) {
            return EMPTY;
        }
        return new ForgeMdicCommandList(commands, heapGeneration, dimensionId, System.currentTimeMillis(), recordCount, selectionMode, skippedSections, skippedRecords);
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
}
