package me.cortex.voxy.forge;

record ForgeModelAtlasAuditResult(
        boolean success,
        String error,
        double durationMs,
        int invalidLayout,
        int invalidModelCoordinate,
        int invalidFaceTileCoordinate
) {
    static ForgeModelAtlasAuditResult failure(String error, double durationMs) {
        return new ForgeModelAtlasAuditResult(false, error, durationMs, 0, 0, 0);
    }
}
