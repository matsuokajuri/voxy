package me.cortex.voxy.forge;

record ForgeFormalModelStoreAuditResult(
        boolean success,
        String error,
        double durationMs,
        int invalidLayout,
        int invalidBufferSize,
        int invalidAtlasState,
        boolean unexpectedRecordsUploaded,
        boolean unexpectedPixelsUploaded,
        boolean modelDataZeroSample,
        boolean modelColourZeroSample
) {
    static ForgeFormalModelStoreAuditResult failure(String error, double durationMs) {
        return new ForgeFormalModelStoreAuditResult(
                false,
                error == null || error.isBlank() ? "unknown" : error,
                durationMs,
                0,
                0,
                0,
                false,
                false,
                false,
                false
        );
    }
}
