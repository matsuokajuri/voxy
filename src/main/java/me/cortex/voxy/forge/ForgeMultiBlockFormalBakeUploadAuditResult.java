package me.cortex.voxy.forge;

record ForgeMultiBlockFormalBakeUploadAuditResult(
        boolean success,
        String error,
        double durationMs,
        boolean allAcceptedHaveFormalModelIds,
        boolean placeholderIdUsed,
        boolean sampleSetIdUsed,
        boolean allModelRecordBytesOk,
        boolean modelDataReadbackOk,
        boolean modelColourReadbackOk,
        boolean atlasReadbackOk,
        int atlasPixelMismatches,
        boolean dedupeConsistent,
        boolean unsupportedPolicyReady,
        boolean shaderBound,
        boolean drawOccurred,
        boolean formalRendererReady,
        boolean actualDrawEnabled
) {
    static ForgeMultiBlockFormalBakeUploadAuditResult failure(String error, double durationMs) {
        return new ForgeMultiBlockFormalBakeUploadAuditResult(
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
                0,
                false,
                false,
                false,
                false,
                false,
                false
        );
    }
}
