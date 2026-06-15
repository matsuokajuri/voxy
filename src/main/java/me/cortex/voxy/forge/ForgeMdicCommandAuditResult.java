package me.cortex.voxy.forge;

record ForgeMdicCommandAuditResult(
        boolean success,
        String error,
        double durationMs,
        boolean commandBufferMatch,
        boolean layoutMatch,
        boolean generationMatch,
        boolean dimensionMatch,
        int invalidCommands,
        int invalidLayoutCommands,
        int invalidGenerationCommands,
        int invalidDimensionCommands,
        int invalidBucketMaskCommands,
        int invalidBucketRangeCommands,
        int invalidBucketOffsetCommands,
        int invalidGeometryPtrCommands,
        int invalidFaceMaskCommands,
        boolean faceMaskAuditOk,
        int auditedCommands,
        long auditedRecords,
        long auditedBytes,
        long heapGeneration,
        String dimensionId
) {
    static ForgeMdicCommandAuditResult failure(String error, double durationMs) {
        return new ForgeMdicCommandAuditResult(false, error == null ? "unknown" : error, durationMs, false, false, false, false, 0, 0, 0, 0, 0, 0, 0, 0, 0, false, 0, 0L, 0L, -1L, "none");
    }
}
