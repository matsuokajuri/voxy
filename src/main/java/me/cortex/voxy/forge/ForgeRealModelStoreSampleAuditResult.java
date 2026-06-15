package me.cortex.voxy.forge;

record ForgeRealModelStoreSampleAuditResult(
        boolean success,
        String error,
        double durationMs,
        int auditedRecords,
        long auditedBytes,
        int invalidRecords,
        boolean modelDataBufferMatch,
        boolean modelColourBufferMatch,
        long generation,
        String dimension
) {
    static ForgeRealModelStoreSampleAuditResult failure(String error, double durationMs) {
        return new ForgeRealModelStoreSampleAuditResult(
                false,
                error == null ? "unknown" : error,
                durationMs,
                0,
                0L,
                0,
                false,
                false,
                -1L,
                "none"
        );
    }
}
