package me.cortex.voxy.forge;

record ForgeModelStoreLayoutAuditResult(
        boolean success,
        String error,
        double durationMs,
        String formalLayoutVersion,
        int formalModelRecordBytes,
        int placeholderRecordBytes,
        boolean formalLayoutKnown,
        int knownFieldCount,
        int unknownFieldCount,
        boolean faceDataLayoutKnown,
        boolean flagsLayoutKnown,
        boolean colourTintLayoutKnown,
        boolean customIdLayoutKnown,
        boolean atlasUvLayoutKnown,
        boolean materialLayoutKnown,
        boolean fieldMappingReady,
        boolean formalLayoutCompatible,
        String placeholderGapSummary
) {
    static ForgeModelStoreLayoutAuditResult failure(String error, double durationMs) {
        return new ForgeModelStoreLayoutAuditResult(
                false,
                error == null ? "unknown" : error,
                durationMs,
                ForgeOriginalVoxyModelStoreLayoutSpec.LAYOUT_VERSION,
                ForgeOriginalVoxyModelStoreLayoutSpec.MODEL_RECORD_BYTES,
                ForgeModelStoreLayout.BYTES,
                ForgeOriginalVoxyModelStoreLayoutSpec.FORMAL_LAYOUT_KNOWN,
                ForgeOriginalVoxyModelStoreLayoutSpec.knownFieldCount(),
                ForgeOriginalVoxyModelStoreLayoutSpec.unknownFieldCount(),
                ForgeOriginalVoxyModelStoreLayoutSpec.FACE_DATA_LAYOUT_KNOWN,
                ForgeOriginalVoxyModelStoreLayoutSpec.FLAGS_LAYOUT_KNOWN,
                ForgeOriginalVoxyModelStoreLayoutSpec.COLOUR_TINT_LAYOUT_KNOWN,
                ForgeOriginalVoxyModelStoreLayoutSpec.CUSTOM_ID_LAYOUT_KNOWN,
                ForgeOriginalVoxyModelStoreLayoutSpec.ATLAS_UV_LAYOUT_KNOWN,
                ForgeOriginalVoxyModelStoreLayoutSpec.MATERIAL_LAYOUT_KNOWN,
                false,
                false,
                "placeholder-does-not-contain-faceData-atlas-material-or-real-tint-metadata"
        );
    }
}
