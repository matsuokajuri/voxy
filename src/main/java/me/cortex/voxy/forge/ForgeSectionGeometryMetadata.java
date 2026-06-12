package me.cortex.voxy.forge;

import java.util.Arrays;

public final class ForgeSectionGeometryMetadata {
    public static final int SECTION_METADATA_SIZE_BYTES = 32;
    public static final int METADATA_WORDS = SECTION_METADATA_SIZE_BYTES / Integer.BYTES;

    private final String dimension;
    private final int chunkX;
    private final int chunkZ;
    private final long position;
    private final int aabb;
    private final int geometryPtr;
    private final int itemCount;
    private final int allocatedItems;
    private final int[] offsets;
    private final byte childExistence;

    ForgeSectionGeometryMetadata(
            String dimension,
            int chunkX,
            int chunkZ,
            long position,
            int aabb,
            int geometryPtr,
            int itemCount,
            int allocatedItems,
            int[] offsets,
            byte childExistence
    ) {
        if (offsets == null || offsets.length != 8) {
            throw new IllegalArgumentException("Section geometry metadata requires 8 offsets");
        }
        this.dimension = dimension;
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        this.position = position;
        this.aabb = aabb;
        this.geometryPtr = geometryPtr;
        this.itemCount = itemCount;
        this.allocatedItems = allocatedItems;
        this.offsets = Arrays.copyOf(offsets, offsets.length);
        this.childExistence = childExistence;
    }

    public String dimension() {
        return this.dimension;
    }

    public int chunkX() {
        return this.chunkX;
    }

    public int chunkZ() {
        return this.chunkZ;
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

    public int allocatedItems() {
        return this.allocatedItems;
    }

    public byte childExistence() {
        return this.childExistence;
    }

    public long geometryBytes() {
        return (long) this.itemCount * Long.BYTES;
    }

    public long allocatedBytes() {
        return (long) this.allocatedItems * Long.BYTES;
    }

    public int[] offsets() {
        return Arrays.copyOf(this.offsets, this.offsets.length);
    }

    public int[] metadataWords() {
        return new int[] {
                (int) (this.position >> 32),
                (int) this.position,
                this.aabb,
                this.geometryPtr + this.offsets[0],
                packDeltaPair(this.offsets[1] - this.offsets[0], this.offsets[2] - this.offsets[1]),
                packDeltaPair(this.offsets[3] - this.offsets[2], this.offsets[4] - this.offsets[3]),
                packDeltaPair(this.offsets[5] - this.offsets[4], this.offsets[6] - this.offsets[5]),
                packDeltaPair(this.offsets[7] - this.offsets[6], this.itemCount - this.offsets[7])
        };
    }

    public String formatMetadataWords() {
        int[] words = this.metadataWords();
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < words.length; i++) {
            if (i != 0) {
                builder.append(',');
            }
            builder.append(String.format("0x%08X", words[i]));
        }
        return builder.toString();
    }

    public String decodeMetadataWords() {
        int[] words = this.metadataWords();
        return String.format(
                "position=%s aabb=%s geometryPtr=%d firstGeometryOffset=%d deltas=[%d,%d,%d,%d,%d,%d,%d,%d] itemCount=%d allocatedItems=%d childExistence=%d chunk=%d,%d dimension=%s",
                Long.toUnsignedString(((long) words[0] << 32) | (words[1] & 0xFFFFFFFFL)),
                ForgeVoxyBuiltSectionBuilder.formatAabb(words[2]),
                Integer.toUnsignedLong(this.geometryPtr),
                Integer.toUnsignedLong(words[3]),
                unpackLow(words[4]),
                unpackHigh(words[4]),
                unpackLow(words[5]),
                unpackHigh(words[5]),
                unpackLow(words[6]),
                unpackHigh(words[6]),
                unpackLow(words[7]),
                unpackHigh(words[7]),
                this.itemCount,
                this.allocatedItems,
                Byte.toUnsignedInt(this.childExistence),
                this.chunkX,
                this.chunkZ,
                this.dimension
        );
    }

    private static int packDeltaPair(int low, int high) {
        validateDelta(low);
        validateDelta(high);
        return low | (high << 16);
    }

    private static void validateDelta(int delta) {
        if (delta < 0 || delta > 0xFFFF) {
            throw new IllegalArgumentException("Section geometry metadata offset delta out of 16-bit range: " + delta);
        }
    }

    private static int unpackLow(int packed) {
        return packed & 0xFFFF;
    }

    private static int unpackHigh(int packed) {
        return (packed >>> 16) & 0xFFFF;
    }
}
