package me.cortex.voxy.forge;

public record ForgeGpuGeometryVisualizationResult(
        boolean success,
        String reason,
        int builtSections,
        int recordsRead,
        int quads,
        int vertices,
        int invalidMetadata,
        int invalidRecords,
        int skippedBuckets,
        double durationMs,
        int lastSectionId,
        long lastPosition,
        int lastGeometryPtr,
        String lastError,
        String lastDecodedRecord,
        String lastSource
) {
    static ForgeGpuGeometryVisualizationResult failure(String reason) {
        return new ForgeGpuGeometryVisualizationResult(
                false,
                reason == null ? "unknown" : reason,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0.0D,
                -1,
                0L,
                -1,
                reason == null ? "unknown" : reason,
                "none",
                ForgeGpuGeometryVisualizationCache.SOURCE
        );
    }
}
