package me.cortex.voxy.forge;

record ForgeModelBridgeAuditResult(
        boolean success,
        String error,
        double durationMs,
        ForgeModelBridgeReadinessStats stats
) {
}
