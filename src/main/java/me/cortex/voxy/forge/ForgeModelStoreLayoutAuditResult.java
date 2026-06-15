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
                ForgeModelStoreFormalLayout.LAYOUT_VERSION,
                ForgeModelStoreFormalLayout.MODEL_RECORD_BYTES,
                ForgeModelStoreLayout.BYTES,
                ForgeModelStoreFormalLayout.FORMAL_LAYOUT_KNOWN,
                ForgeModelStoreFormalLayout.knownFieldCount(),
                ForgeModelStoreFormalLayout.unknownFieldCount(),
                ForgeModelStoreFormalLayout.FACE_DATA_LAYOUT_KNOWN,
                ForgeModelStoreFormalLayout.FLAGS_LAYOUT_KNOWN,
                ForgeModelStoreFormalLayout.COLOUR_TINT_LAYOUT_KNOWN,
                ForgeModelStoreFormalLayout.CUSTOM_ID_LAYOUT_KNOWN,
                ForgeModelStoreFormalLayout.ATLAS_UV_LAYOUT_KNOWN,
                ForgeModelStoreFormalLayout.MATERIAL_LAYOUT_KNOWN,
                false,
                false,
                "placeholder-does-not-contain-faceData-atlas-material-or-real-tint-metadata"
        );
    }
}
