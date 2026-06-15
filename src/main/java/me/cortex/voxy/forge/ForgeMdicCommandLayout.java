package me.cortex.voxy.forge;

final class ForgeMdicCommandLayout {
    static final String STAGE = "G6_3_BUCKET_AWARE_MDIC_DEBUG_DRAW";
    static final String LAYOUT_VERSION = "G6_3_MDIC_BUCKET_COMMAND_V1";
    static final int WORD_SECTION_ID = 0;
    static final int WORD_GEOMETRY_PTR = 1;
    static final int WORD_RECORD_START = 2;
    static final int WORD_RECORD_COUNT = 3;
    static final int WORD_ORIGIN_X = 4;
    static final int WORD_ORIGIN_Y = 5;
    static final int WORD_ORIGIN_Z = 6;
    static final int WORD_BUCKET_MASK = 7;
    static final int WORD_METADATA_INDEX = 8;
    static final int WORD_LOD_LEVEL = 9;
    static final int WORD_FLAGS = 10;
    static final int WORD_GENERATION = 11;
    static final int WORDS_PER_COMMAND = 12;
    static final int BYTES_PER_COMMAND = WORDS_PER_COMMAND * Integer.BYTES;
    static final int WORDS = WORDS_PER_COMMAND;
    static final int BYTES = BYTES_PER_COMMAND;

    static final int FLAG_DEBUG_SKELETON = 1;
    static final int FLAG_BUCKET_COMMAND = 1 << 1;

    private ForgeMdicCommandLayout() {
    }
}
