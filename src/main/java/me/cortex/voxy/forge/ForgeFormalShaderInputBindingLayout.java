package me.cortex.voxy.forge;

final class ForgeFormalShaderInputBindingLayout {
    static final int MODEL_DATA_BINDING_INDEX = 3;
    static final int MODEL_COLOUR_BINDING_INDEX = 4;
    static final int BLOCK_MODEL_ATLAS_TEXTURE_UNIT = 0;

    private ForgeFormalShaderInputBindingLayout() {
    }

    static boolean bindingLayoutKnown() {
        return MODEL_DATA_BINDING_INDEX == 3
                && MODEL_COLOUR_BINDING_INDEX == 4
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
        return "MODEL_BUFFER_BINDING=3 MODEL_COLOUR_BUFFER_BINDING=4 BLOCK_MODEL_TEXTURE_BINDING=0 blockModelRecordBytes=64";
    }
}
