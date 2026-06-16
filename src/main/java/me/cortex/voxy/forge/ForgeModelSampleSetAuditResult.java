package me.cortex.voxy.forge;

record ForgeModelSampleSetAuditResult(
        boolean success,
        String error,
        double durationMs,
        int auditedRecords,
        int auditedBytes,
        int invalidRecords,
        boolean modelDataBufferMatch,
        boolean modelColourBufferMatch
) {
    static ForgeModelSampleSetAuditResult failure(String error, double durationMs) {
        return new ForgeModelSampleSetAuditResult(false, error, durationMs, 0, 0, 0, false, false);
    }
}
