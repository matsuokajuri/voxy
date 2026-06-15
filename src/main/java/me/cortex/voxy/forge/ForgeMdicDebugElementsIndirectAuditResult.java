package me.cortex.voxy.forge;

record ForgeMdicDebugElementsIndirectAuditResult(
        boolean success,
        String error,
        double durationMs,
        int auditedCommands,
        long auditedBytes,
        int invalidCommands,
        boolean commandBufferMatch,
        long auditedIndices,
        long auditedLogicalVertices
) {
    static ForgeMdicDebugElementsIndirectAuditResult failure(String error, double durationMs) {
        return new ForgeMdicDebugElementsIndirectAuditResult(false, error, durationMs, 0, 0L, 0, false, 0L, 0L);
    }
}
