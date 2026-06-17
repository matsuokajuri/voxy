package me.cortex.voxy.forge;

record ForgeFormalModelFactoryAuditResult(
        boolean success,
        String error,
        double durationMs,
        int duplicateMappings,
        int invalidFormalModelIds,
        boolean unexpectedRealBake,
        boolean unexpectedUpload,
        boolean sampleSetMisuse
) {
    static ForgeFormalModelFactoryAuditResult failure(String error, double durationMs) {
        return new ForgeFormalModelFactoryAuditResult(
                false,
                error == null || error.isBlank() ? "unknown" : error,
                durationMs,
                0,
                0,
                false,
                false,
                false
        );
    }
}
