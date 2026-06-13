package me.cortex.voxy.forge;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class ForgeDirectGpuGeometryDrawList {
    private static final ForgeDirectGpuGeometryDrawList EMPTY = new ForgeDirectGpuGeometryDrawList(Collections.emptyList(), -1L, 0, 0, "none");

    private final List<ForgeDirectGpuGeometryDrawItem> items;
    private final long heapGeneration;
    private final int skippedSections;
    private final long skippedRecords;
    private final String selectionMode;
    private final long recordCount;
    private final long vertexCount;

    private ForgeDirectGpuGeometryDrawList(List<ForgeDirectGpuGeometryDrawItem> items, long heapGeneration, int skippedSections, long skippedRecords, String selectionMode) {
        this.items = Collections.unmodifiableList(new ArrayList<>(items));
        this.heapGeneration = heapGeneration;
        this.skippedSections = Math.max(0, skippedSections);
        this.skippedRecords = Math.max(0, skippedRecords);
        this.selectionMode = selectionMode == null ? "unknown" : selectionMode;
        long records = 0;
        long vertices = 0;
        for (ForgeDirectGpuGeometryDrawItem item : this.items) {
            records += item.recordCount();
            vertices += item.vertexCount();
        }
        this.recordCount = records;
        this.vertexCount = vertices;
    }

    static ForgeDirectGpuGeometryDrawList empty() {
        return EMPTY;
    }

    static ForgeDirectGpuGeometryDrawList of(List<ForgeDirectGpuGeometryDrawItem> items, long heapGeneration, int skippedSections, long skippedRecords, String selectionMode) {
        if (items == null || items.isEmpty()) {
            return EMPTY;
        }
        return new ForgeDirectGpuGeometryDrawList(items, heapGeneration, skippedSections, skippedRecords, selectionMode);
    }

    boolean isValid() {
        return !this.items.isEmpty() && this.recordCount > 0;
    }

    boolean isStale(long currentHeapGeneration) {
        return this.isValid() && this.heapGeneration != currentHeapGeneration;
    }

    List<ForgeDirectGpuGeometryDrawItem> items() {
        return this.items;
    }

    int itemCount() {
        return this.items.size();
    }

    long recordCount() {
        return this.recordCount;
    }

    long vertexCount() {
        return this.vertexCount;
    }

    long heapGeneration() {
        return this.heapGeneration;
    }

    int skippedSections() {
        return this.skippedSections;
    }

    long skippedRecords() {
        return this.skippedRecords;
    }

    String selectionMode() {
        return this.selectionMode;
    }
}
