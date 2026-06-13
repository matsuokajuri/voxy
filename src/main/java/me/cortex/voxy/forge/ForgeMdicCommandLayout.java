package me.cortex.voxy.forge;

final class ForgeMdicCommandLayout {
    static final String STAGE = "G5_8_MDIC_COMMAND_BUFFER_SKELETON";
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
    static final int WORDS = 12;
    static final int BYTES = WORDS * Integer.BYTES;

    static final int FLAG_DEBUG_SKELETON = 1;

    private ForgeMdicCommandLayout() {
    }
}
