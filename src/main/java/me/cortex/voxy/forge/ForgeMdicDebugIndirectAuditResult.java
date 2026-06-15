package me.cortex.voxy.forge;

record ForgeMdicDebugIndirectAuditResult(
        boolean success,
        String error,
        double durationMs,
        int auditedCommands,
        long auditedBytes,
        int invalidCommands,
        boolean commandBufferMatch,
        long auditedVertices
) {
    static ForgeMdicDebugIndirectAuditResult failure(String error, double durationMs) {
        return new ForgeMdicDebugIndirectAuditResult(false, error, durationMs, 0, 0L, 0, false, 0L);
    }
}
