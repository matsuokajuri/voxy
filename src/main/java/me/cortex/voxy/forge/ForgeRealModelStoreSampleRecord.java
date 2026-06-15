package me.cortex.voxy.forge;

import java.util.Arrays;
import java.util.List;

record ForgeRealModelStoreSampleRecord(
        int modelId,
        int blockStateId,
        String blockState,
        String renderLayer,
        String sourceSprite,
        String sourceSpriteAtlas,
        int[] faceDataWords,
        int flagsA,
        int colourTint,
        int customId,
        int modelColour,
        List<ForgeRealModelStoreFaceSample> faces,
        int tintedFaces,
        int untintedFaces
) {
    static final String LAYOUT_VERSION = "REAL_MODEL_RECORD_SAMPLE_V1";
    static final int WORDS = ForgeModelStoreFormalLayout.MODEL_RECORD_WORDS;
    static final int BYTES = ForgeModelStoreFormalLayout.MODEL_RECORD_BYTES;
    static final boolean FORMAL_LAYOUT_COMPATIBLE = false;
    static final int WORD_SAMPLE_MARKER = 9;
    static final int WORD_MODEL_ID = 10;
    static final int WORD_BLOCK_STATE_ID = 11;
    static final int WORD_SOURCE_SPRITE_HASH = 12;
    static final int WORD_FACE_MASK = 13;
    static final int WORD_TINT_COUNTS = 14;
    static final int WORD_LAYOUT_MARKER = 15;
    static final int SAMPLE_MARKER = 0x524D5350; // RMSP: real model sample.
    static final int LAYOUT_MARKER = 0x524D5631; // RMV1.

    int[] toWords() {
        int[] words = new int[WORDS];
        for (int i = 0; i < ForgeModelStoreFormalLayout.FACE_DATA_WORDS; i++) {
            words[i] = i < this.faceDataWords.length ? this.faceDataWords[i] : -1;
        }
        words[ForgeModelStoreFormalLayout.WORD_FLAGS_A] = this.flagsA;
        words[ForgeModelStoreFormalLayout.WORD_COLOUR_TINT] = this.colourTint;
        words[ForgeModelStoreFormalLayout.WORD_CUSTOM_ID] = this.customId;
        words[WORD_SAMPLE_MARKER] = SAMPLE_MARKER;
        words[WORD_MODEL_ID] = this.modelId;
        words[WORD_BLOCK_STATE_ID] = this.blockStateId;
        words[WORD_SOURCE_SPRITE_HASH] = this.sourceSprite.hashCode();
        words[WORD_FACE_MASK] = this.faceMask();
        words[WORD_TINT_COUNTS] = (this.tintedFaces & 0xFFFF) | ((this.untintedFaces & 0xFFFF) << 16);
        words[WORD_LAYOUT_MARKER] = LAYOUT_MARKER;
        return words;
    }

    boolean matchesWords(int[] words, int wordOffset) {
        if (words == null || wordOffset < 0 || wordOffset + WORDS > words.length) {
            return false;
        }
        return Arrays.equals(this.toWords(), Arrays.copyOfRange(words, wordOffset, wordOffset + WORDS));
    }

    int faceCount() {
        int count = 0;
        for (ForgeRealModelStoreFaceSample face : this.faces) {
            if (face.hasQuad()) {
                count++;
            }
        }
        return count;
    }

    int faceMask() {
        int mask = 0;
        for (int i = 0; i < this.faces.size(); i++) {
            if (this.faces.get(i).hasQuad()) {
                mask |= 1 << i;
            }
        }
        return mask;
    }

    ForgeRealModelStoreFaceSample sampleFace() {
        for (ForgeRealModelStoreFaceSample face : this.faces) {
            if (face.hasQuad()) {
                return face;
            }
        }
        return this.faces.isEmpty() ? ForgeRealModelStoreFaceSample.empty("none") : this.faces.get(0);
    }
}
