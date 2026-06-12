package me.cortex.voxy.forge;

public record ForgeGpuGeometryReadbackMeshResult(
        boolean success,
        String reason,
        int builtSections,
        int recordsRead,
        int quads,
        int vertices,
        int invalidMetadata,
        int invalidRecords,
        double durationMs,
        int lastSectionId,
        long lastPosition,
        int lastGeometryPtr,
        String lastError,
        String lastDecodedRecord,
        String lastSource
) {
    static ForgeGpuGeometryReadbackMeshResult failure(String reason) {
        String safeReason = reason == null ? "unknown" : reason;
        return new ForgeGpuGeometryReadbackMeshResult(
                false,
                safeReason,
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
                safeReason,
                "none",
                ForgeGpuGeometryReadbackMeshCache.SOURCE
        );
    }
}
