package me.cortex.voxy.forge;

record ForgeBakedModelBridgeAuditResult(
        boolean success,
        String error,
        double durationMs,
        ForgeBakedModelBridgeStats stats
) {
    static ForgeBakedModelBridgeAuditResult failure(String error, double durationMs, ForgeBakedModelBridgeStats stats) {
        return new ForgeBakedModelBridgeAuditResult(false, error, durationMs, stats);
    }
}
