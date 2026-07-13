package me.cortex.voxy.forge;

/** Forge-side names for the original ModelStore/ModelFactory packed record offsets. */
final class ForgeModelStoreLayoutSpec {
    static final int MODEL_RECORD_BYTES = 64;
    static final int MODEL_RECORD_WORDS = 16;
    static final int FACE_DATA_WORDS = 6;
    static final int WORD_FLAGS_A = 6;
    static final int WORD_COLOUR_TINT = 7;
    static final int WORD_CUSTOM_ID = 8;

    private ForgeModelStoreLayoutSpec() {
    }
}
