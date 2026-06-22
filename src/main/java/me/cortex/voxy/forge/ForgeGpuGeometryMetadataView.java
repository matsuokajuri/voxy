package me.cortex.voxy.forge;

import java.util.Arrays;

record ForgeGpuGeometryMetadataView(long position, int aabb, int geometryPtr, int[] offsets, int[] deltas, int itemCount) {
    static final int BUCKET_COUNT = 8;

    static ForgeGpuGeometryMetadataView decode(int[] words) {
        if (words == null || words.length != ForgeSectionGeometryMetadata.METADATA_WORDS) {
            throw new IllegalArgumentException("section metadata must contain 8 words");
        }
        long position = ((long) words[0] << 32) | (words[1] & 0xFFFFFFFFL);
        int geometryPtr = words[3];
        int[] deltas = new int[] {
                low(words[4]),
                high(words[4]),
                low(words[5]),
                high(words[5]),
                low(words[6]),
                high(words[6]),
                low(words[7]),
                high(words[7])
        };
        int[] offsets = new int[BUCKET_COUNT];
        offsets[0] = 0;
        int cursor = 0;
        for (int i = 0; i < BUCKET_COUNT - 1; i++) {
            cursor += deltas[i];
            offsets[i + 1] = cursor;
        }
        int itemCount = cursor + deltas[BUCKET_COUNT - 1];
        return new ForgeGpuGeometryMetadataView(position, words[2], geometryPtr, offsets, deltas, itemCount);
    }

    String format() {
        return "position=" + Long.toUnsignedString(this.position)
                + " aabb=" + ForgeVoxyBuiltSectionBuilder.formatAabb(this.aabb)
                + " geometryPtr=" + Integer.toUnsignedLong(this.geometryPtr)
                + " offsets=" + Arrays.toString(this.offsets)
                + " deltas=" + Arrays.toString(this.deltas)
                + " itemCount=" + this.itemCount;
    }

    String validate(ForgeGpuGeometryHeap heap, ForgeSectionGeometryManager geometryManager, int sectionId, int[] words) {
        if ((Integer.toUnsignedLong(this.geometryPtr) & 127L) != 0L) {
            return "geometry pointer is not 128-item aligned";
        }
        if (this.itemCount < 0) {
            return "negative item count";
        }
        int previous = 0;
        for (int i = 0; i < this.offsets.length; i++) {
            int offset = this.offsets[i];
            if (offset < previous) {
                return "offsets are not monotonic at index " + i;
            }
            if (offset > this.itemCount) {
                return "offset " + i + " exceeds item count";
            }
            previous = offset;
        }
        long geometryEndBytes = (Integer.toUnsignedLong(this.geometryPtr) + this.itemCount) * ForgeGpuGeometryHeap.GEOMETRY_RECORD_BYTES;
        if (geometryEndBytes > heap.geometryCapacityBytes()) {
            return "geometry range exceeds heap capacity";
        }
        long metadataEndBytes = ((long) sectionId + 1L) * ForgeGpuGeometryHeap.METADATA_BYTES;
        if (metadataEndBytes > heap.metadataCapacityBytes()) {
            return "metadata range exceeds heap capacity";
        }

        int[] expectedWords = geometryManager.createMetadataWordsSnapshot(sectionId);
        if (expectedWords.length == words.length && !isAllZero(expectedWords)) {
            long expectedPosition = ((long) expectedWords[0] << 32) | (expectedWords[1] & 0xFFFFFFFFL);
            if (expectedPosition != this.position) {
                return "CPU metadata position cross-check mismatch";
            }
        }
        return "none";
    }

    private static boolean isAllZero(int[] words) {
        for (int word : words) {
            if (word != 0) {
                return false;
            }
        }
        return true;
    }

    private static int low(int word) {
        return word & 0xFFFF;
    }

    private static int high(int word) {
        return (word >>> 16) & 0xFFFF;
    }
}
