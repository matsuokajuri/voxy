package me.cortex.voxy.forge;

record ForgeMdicDebugDrawCountAuditResult(
        boolean success,
        String error,
        double durationMs,
        int auditedDrawCount,
        int auditedMaxDrawCount,
        int invalidDrawCount,
        boolean drawCountBufferMatch,
        long auditGeneration,
        String auditDimension
) {
    static ForgeMdicDebugDrawCountAuditResult failure(String error, double durationMs) {
        return new ForgeMdicDebugDrawCountAuditResult(false, error, durationMs, 0, 0, 1, false, -1L, "none");
    }
}
