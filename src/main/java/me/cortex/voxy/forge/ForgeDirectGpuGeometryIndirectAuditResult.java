package me.cortex.voxy.forge;

record ForgeDirectGpuGeometryIndirectAuditResult(
        boolean success,
        String error,
        double durationMs,
        int auditedDrawItems,
        long commandBytes,
        long drawItemBytes,
        boolean commandBufferMatch,
        boolean drawItemBufferMatch,
        long heapGeneration,
        String dimensionId,
        int invalidCommands,
        int invalidDrawItems,
        long auditedVertices
) {
    static ForgeDirectGpuGeometryIndirectAuditResult failure(String error, double durationMs) {
        return new ForgeDirectGpuGeometryIndirectAuditResult(
                false,
                error == null || error.isBlank() ? "unknown" : error,
                Math.max(0.0D, durationMs),
                0,
                0L,
                0L,
                false,
                false,
                -1L,
                "none",
                0,
                0,
                0L
        );
    }
}
