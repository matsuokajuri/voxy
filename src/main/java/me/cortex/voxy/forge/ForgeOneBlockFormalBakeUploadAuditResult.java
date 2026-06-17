package me.cortex.voxy.forge;

record ForgeOneBlockFormalBakeUploadAuditResult(
        boolean success,
        String error,
        double durationMs,
        boolean validFormalModelId,
        boolean mappingBelongsToI3,
        boolean placeholderIdUsed,
        boolean sampleSetIdUsed,
        boolean modelRecordBytesOk,
        boolean modelDataReadbackOk,
        boolean modelColourReadbackOk,
        boolean atlasReadbackOk,
        int atlasPixelMismatches,
        boolean shaderBound,
        boolean drawOccurred,
        boolean formalRendererReady,
        boolean actualDrawEnabled
) {
    static ForgeOneBlockFormalBakeUploadAuditResult failure(String error, double durationMs) {
        return new ForgeOneBlockFormalBakeUploadAuditResult(
                false,
                error == null || error.isBlank() ? "audit-failed" : error,
                durationMs,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                0,
                false,
                false,
                false,
                false
        );
    }
}
