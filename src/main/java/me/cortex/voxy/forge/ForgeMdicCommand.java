package me.cortex.voxy.forge;

record ForgeMdicCommand(
        int sectionId,
        int geometryPtr,
        int recordStart,
        int recordCount,
        int sectionOriginXBits,
        int sectionOriginYBits,
        int sectionOriginZBits,
        int bucketMask,
        int metadataIndex,
        int lodLevel,
        int flags,
        int generation
) {
    private static final int GEOMETRY_PTR_ALIGNMENT_ITEMS = 128;

    long vertexCount() {
        return (long) this.recordCount * 6L;
    }

    int[] toWords() {
        int[] words = new int[ForgeMdicCommandLayout.WORDS];
        words[ForgeMdicCommandLayout.WORD_SECTION_ID] = this.sectionId;
        words[ForgeMdicCommandLayout.WORD_GEOMETRY_PTR] = this.geometryPtr;
        words[ForgeMdicCommandLayout.WORD_RECORD_START] = this.recordStart;
        words[ForgeMdicCommandLayout.WORD_RECORD_COUNT] = this.recordCount;
        words[ForgeMdicCommandLayout.WORD_ORIGIN_X] = this.sectionOriginXBits;
        words[ForgeMdicCommandLayout.WORD_ORIGIN_Y] = this.sectionOriginYBits;
        words[ForgeMdicCommandLayout.WORD_ORIGIN_Z] = this.sectionOriginZBits;
        words[ForgeMdicCommandLayout.WORD_BUCKET_MASK] = this.bucketMask;
        words[ForgeMdicCommandLayout.WORD_METADATA_INDEX] = this.metadataIndex;
        words[ForgeMdicCommandLayout.WORD_LOD_LEVEL] = this.lodLevel;
        words[ForgeMdicCommandLayout.WORD_FLAGS] = this.flags;
        words[ForgeMdicCommandLayout.WORD_GENERATION] = this.generation;
        return words;
    }

    static ForgeMdicCommand fromWords(int[] words, int offset) {
        if (words == null || offset < 0 || offset + ForgeMdicCommandLayout.WORDS > words.length) {
            throw new IllegalArgumentException("MDIC command word range is invalid");
        }
        return new ForgeMdicCommand(
                words[offset + ForgeMdicCommandLayout.WORD_SECTION_ID],
                words[offset + ForgeMdicCommandLayout.WORD_GEOMETRY_PTR],
                words[offset + ForgeMdicCommandLayout.WORD_RECORD_START],
                words[offset + ForgeMdicCommandLayout.WORD_RECORD_COUNT],
                words[offset + ForgeMdicCommandLayout.WORD_ORIGIN_X],
                words[offset + ForgeMdicCommandLayout.WORD_ORIGIN_Y],
                words[offset + ForgeMdicCommandLayout.WORD_ORIGIN_Z],
                words[offset + ForgeMdicCommandLayout.WORD_BUCKET_MASK],
                words[offset + ForgeMdicCommandLayout.WORD_METADATA_INDEX],
                words[offset + ForgeMdicCommandLayout.WORD_LOD_LEVEL],
                words[offset + ForgeMdicCommandLayout.WORD_FLAGS],
                words[offset + ForgeMdicCommandLayout.WORD_GENERATION]
        );
    }

    boolean encodeDecodeMatches() {
        return this.matches(fromWords(this.toWords(), 0));
    }

    String validate(long expectedHeapGeneration) {
        if (!this.encodeDecodeMatches()) {
            return "encode-decode-mismatch";
        }
        if (this.sectionId < 0) {
            return "negative-section-id";
        }
        if (this.geometryPtr < 0) {
            return "negative-geometry-ptr";
        }
        if (this.geometryPtr % GEOMETRY_PTR_ALIGNMENT_ITEMS != 0) {
            return "geometry-ptr-not-128-item-aligned";
        }
        if (this.recordStart < 0) {
            return "negative-record-start";
        }
        if (this.recordCount < 0) {
            return "negative-record-count";
        }
        if (this.recordCount > 0 && this.bucketMask == 0) {
            return "non-empty-command-with-empty-bucket-mask";
        }
        if (this.metadataIndex < 0) {
            return "negative-metadata-index";
        }
        if (this.lodLevel < 0) {
            return "negative-lod-level";
        }
        if (this.generation != (int) expectedHeapGeneration) {
            return "generation-mismatch";
        }
        return "none";
    }

    boolean matches(ForgeMdicCommand other) {
        return other != null
                && this.sectionId == other.sectionId
                && this.geometryPtr == other.geometryPtr
                && this.recordStart == other.recordStart
                && this.recordCount == other.recordCount
                && this.sectionOriginXBits == other.sectionOriginXBits
                && this.sectionOriginYBits == other.sectionOriginYBits
                && this.sectionOriginZBits == other.sectionOriginZBits
                && this.bucketMask == other.bucketMask
                && this.metadataIndex == other.metadataIndex
                && this.lodLevel == other.lodLevel
                && this.flags == other.flags
                && this.generation == other.generation;
    }
}
