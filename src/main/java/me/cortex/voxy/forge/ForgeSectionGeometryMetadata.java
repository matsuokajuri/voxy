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

    public int aabb() {
        return this.aabb;
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

    public int[] deltas() {
        int[] deltas = new int[this.offsets.length];
        for (int i = 0; i < this.offsets.length - 1; i++) {
            deltas[i] = this.offsets[i + 1] - this.offsets[i];
        }
        deltas[this.offsets.length - 1] = this.itemCount - this.offsets[this.offsets.length - 1];
        return deltas;
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

    public ValidationResult validate(int sectionId, ForgeSectionGeometryUploadIntent uploadIntent) {
        try {
            if (sectionId < 0) {
                return ValidationResult.failure("negative section id");
            }
            if (this.itemCount <= 0) {
                return ValidationResult.failure("empty geometry item count");
            }
            if (this.allocatedItems < this.itemCount) {
                return ValidationResult.failure("allocated items smaller than item count");
            }
            if ((this.allocatedItems & 127) != 0) {
                return ValidationResult.failure("allocated items are not aligned to 128-item heap blocks");
            }
            if ((Integer.toUnsignedLong(this.geometryPtr) & 127L) != 0L) {
                return ValidationResult.failure("geometry pointer is not aligned to a 128-item heap block");
            }
            if (this.offsets[0] != 0) {
                return ValidationResult.failure("offsets[0] is not zero");
            }
            int previous = 0;
            for (int i = 0; i < this.offsets.length; i++) {
                int offset = this.offsets[i];
                if (offset < previous) {
                    return ValidationResult.failure("offsets are not monotonic at index " + i);
                }
                if (offset > this.itemCount) {
                    return ValidationResult.failure("offset " + i + " exceeds item count");
                }
                previous = offset;
            }
            int[] deltas = this.deltas();
            for (int i = 0; i < deltas.length; i++) {
                int delta = deltas[i];
                if (delta < 0 || delta > 0xFFFF) {
                    return ValidationResult.failure("delta " + i + " is outside 16-bit metadata range");
                }
            }

            int[] words = this.metadataWords();
            long decodedPosition = ((long) words[0] << 32) | (words[1] & 0xFFFFFFFFL);
            if (decodedPosition != this.position) {
                return ValidationResult.failure("position high/low words do not round-trip");
            }
            if (words[2] != this.aabb) {
                return ValidationResult.failure("aabb word does not round-trip");
            }
            int expectedFirstGeometryOffset = this.geometryPtr + this.offsets[0];
            if (words[3] != expectedFirstGeometryOffset) {
                return ValidationResult.failure("geometry pointer plus first offset word does not match");
            }

            if (uploadIntent == null) {
                return ValidationResult.failure("missing upload intent for active metadata");
            }
            if (uploadIntent.sectionId() != sectionId) {
                return ValidationResult.failure("upload intent section id mismatch");
            }
            if (!uploadIntent.dimension().equals(this.dimension)) {
                return ValidationResult.failure("upload intent dimension mismatch");
            }
            if (uploadIntent.position() != this.position) {
                return ValidationResult.failure("upload intent position mismatch");
            }
            if (uploadIntent.geometryPtr() != this.geometryPtr) {
                return ValidationResult.failure("upload intent geometry pointer mismatch");
            }
            if (uploadIntent.itemCount() != this.itemCount) {
                return ValidationResult.failure("upload intent item count mismatch");
            }

            return ValidationResult.success();
        } catch (RuntimeException e) {
            return ValidationResult.failure(e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    public String formatOffsets() {
        return Arrays.toString(this.offsets);
    }

    public String formatDeltas() {
        return Arrays.toString(this.deltas());
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

    public record ValidationResult(boolean valid, String error) {
        private static ValidationResult success() {
            return new ValidationResult(true, "none");
        }

        private static ValidationResult failure(String error) {
            return new ValidationResult(false, error == null ? "unknown" : error);
        }
    }
}
