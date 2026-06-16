package me.cortex.voxy.forge;

record ForgeFormalShaderInputAuditResult(
        boolean success,
        String error,
        double durationMs,
        boolean modelDataBufferMatch,
        boolean modelColourBufferMatch,
        boolean atlasUploadStillValid,
        int sampleModelCount,
        int invalidModelRecords,
        int invalidColourRecords,
        int missingAtlasTiles
) {
    static ForgeFormalShaderInputAuditResult failure(String error, double durationMs) {
        return new ForgeFormalShaderInputAuditResult(
                false,
                error == null || error.isBlank() ? "unknown" : error,
                durationMs,
                false,
                false,
                false,
                0,
                0,
                0,
                0
        );
    }
}
