package me.cortex.voxy.forge;

record ForgeMdicCommandAuditResult(
        boolean success,
        String error,
        double durationMs,
        boolean commandBufferMatch,
        int invalidCommands,
        int auditedCommands,
        long auditedBytes,
        long heapGeneration,
        String dimensionId
) {
    static ForgeMdicCommandAuditResult failure(String error, double durationMs) {
        return new ForgeMdicCommandAuditResult(false, error == null ? "unknown" : error, durationMs, false, 0, 0, 0L, -1L, "none");
    }
}
