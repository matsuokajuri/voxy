package me.cortex.voxy.forge;

final class ForgeModelStoreLayout {
    static final String LAYOUT_VERSION = "PLACEHOLDER_MODEL_RECORD_V1";
    static final int WORDS = 16;
    static final int BYTES = WORDS * Integer.BYTES;
    static final boolean FORMAL_LAYOUT_COMPATIBLE = false;

    static final int WORD_MODEL_ID = 0;
    static final int WORD_BLOCK_STATE_ID = 1;
    static final int WORD_FLAGS = 2;
    static final int WORD_DEBUG_COLOUR = 3;
    static final int WORD_LAYOUT_MARKER = 4;

    static final int FLAG_PLACEHOLDER = 1;
    static final int FLAG_MISSING_TEXTURE = 1 << 1;
    static final int FLAG_NO_ATLAS = 1 << 2;
    static final int FLAG_NO_FACE_DATA = 1 << 3;
    static final int FLAG_NO_REAL_MODEL_METADATA = 1 << 4;

    static final int PLACEHOLDER_FLAGS = FLAG_PLACEHOLDER
            | FLAG_MISSING_TEXTURE
            | FLAG_NO_ATLAS
            | FLAG_NO_FACE_DATA
            | FLAG_NO_REAL_MODEL_METADATA;
    static final int LAYOUT_MARKER = 0x504D5354; // PMST: placeholder model store.

    private ForgeModelStoreLayout() {
    }
}
