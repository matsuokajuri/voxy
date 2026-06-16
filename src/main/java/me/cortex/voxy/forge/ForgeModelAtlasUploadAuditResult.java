package me.cortex.voxy.forge;

record ForgeModelAtlasUploadAuditResult(
        boolean success,
        String error,
        double durationMs,
        int auditedFaces,
        int auditedPixels,
        int missingFaces,
        int pixelMismatches,
        boolean atlasReadbackOk
) {
    static ForgeModelAtlasUploadAuditResult failure(String error, double durationMs) {
        return new ForgeModelAtlasUploadAuditResult(false, error, durationMs, 0, 0, 0, 0, false);
    }
}
