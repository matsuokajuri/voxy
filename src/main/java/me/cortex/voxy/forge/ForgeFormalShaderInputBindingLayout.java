package me.cortex.voxy.forge;

final class ForgeFormalShaderInputBindingLayout {
    static final int QUAD_BUFFER_BINDING_INDEX = 1;
    static final int MODEL_DATA_BINDING_INDEX = 3;
    static final int MODEL_COLOUR_BINDING_INDEX = 4;
    static final int POSITION_SCRATCH_BINDING_INDEX = 5;
    static final int BLOCK_MODEL_ATLAS_TEXTURE_UNIT = 0;

    private ForgeFormalShaderInputBindingLayout() {
    }

    static boolean bindingLayoutKnown() {
        return QUAD_BUFFER_BINDING_INDEX == 1
                && MODEL_DATA_BINDING_INDEX == 3
                && MODEL_COLOUR_BINDING_INDEX == 4
                && POSITION_SCRATCH_BINDING_INDEX == 5
                && BLOCK_MODEL_ATLAS_TEXTURE_UNIT == 0;
    }

    static boolean recordLayoutCompatible() {
        return ForgeModelStoreFormalLayout.MODEL_RECORD_BYTES == 64
                && ForgeModelStoreFormalLayout.MODEL_RECORD_WORDS == 16
                && ForgeModelStoreFormalLayout.FACE_DATA_LAYOUT_KNOWN
                && ForgeModelStoreFormalLayout.FLAGS_LAYOUT_KNOWN
                && ForgeModelStoreFormalLayout.COLOUR_TINT_LAYOUT_KNOWN
                && ForgeModelStoreFormalLayout.CUSTOM_ID_LAYOUT_KNOWN;
    }

    static String summary() {
        return "QUAD_BUFFER_BINDING=1 MODEL_BUFFER_BINDING=3 MODEL_COLOUR_BUFFER_BINDING=4 POSITION_SCRATCH_BINDING=5 BLOCK_MODEL_TEXTURE_BINDING=0 blockModelRecordBytes=64";
    }
}
