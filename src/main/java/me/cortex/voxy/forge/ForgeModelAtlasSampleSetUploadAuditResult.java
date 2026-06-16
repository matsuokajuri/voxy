package me.cortex.voxy.forge;

record ForgeModelAtlasSampleSetUploadAuditResult(
        boolean success,
        String error,
        double durationMs,
        int auditedModels,
        int auditedFaces,
        int auditedPixels,
        int missingFaces,
        int pixelMismatches,
        boolean atlasReadbackOk
) {
    static ForgeModelAtlasSampleSetUploadAuditResult failure(String error, double durationMs) {
        return new ForgeModelAtlasSampleSetUploadAuditResult(false, error, durationMs, 0, 0, 0, 0, 0, false);
    }
}
