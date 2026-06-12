package me.cortex.voxy.forge;

import java.util.Arrays;

public final class ForgeSectionGeometryUploadIntent {
    private final int sectionId;
    private final String dimension;
    private final long position;
    private final int geometryPtr;
    private final int itemCount;
    private final long[] records;

    ForgeSectionGeometryUploadIntent(int sectionId, String dimension, long position, int geometryPtr, long[] records) {
        this.sectionId = sectionId;
        this.dimension = dimension;
        this.position = position;
        this.geometryPtr = geometryPtr;
        this.records = Arrays.copyOf(records, records.length);
        this.itemCount = records.length;
    }

    public int sectionId() {
        return this.sectionId;
    }

    public String dimension() {
        return this.dimension;
    }

    public long position() {
        return this.position;
    }

    public int geometryPtr() {
        return this.geometryPtr;
    }

    public int itemCount() {
        return this.itemCount;
    }

    public long sizeBytes() {
        return (long) this.itemCount * Long.BYTES;
    }

    public int recordHash() {
        return Arrays.hashCode(this.records);
    }

    public long[] recordsCopy() {
        return Arrays.copyOf(this.records, this.records.length);
    }
}
