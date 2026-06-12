package me.cortex.voxy.forge;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class ForgeDirectGpuGeometryDrawList {
    private static final ForgeDirectGpuGeometryDrawList EMPTY = new ForgeDirectGpuGeometryDrawList(Collections.emptyList());

    private final List<ForgeDirectGpuGeometryDrawItem> items;
    private final long recordCount;
    private final long vertexCount;

    private ForgeDirectGpuGeometryDrawList(List<ForgeDirectGpuGeometryDrawItem> items) {
        this.items = Collections.unmodifiableList(new ArrayList<>(items));
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

    static ForgeDirectGpuGeometryDrawList of(List<ForgeDirectGpuGeometryDrawItem> items) {
        if (items == null || items.isEmpty()) {
            return EMPTY;
        }
        return new ForgeDirectGpuGeometryDrawList(items);
    }

    boolean isValid() {
        return !this.items.isEmpty() && this.recordCount > 0;
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
}
