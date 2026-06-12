package me.cortex.voxy.forge;

public record ForgeGpuGeometryAuditStats(
        long auditRuns,
        long auditFailures,
        String lastAuditError,
        double lastAuditDurationMs,
        int lastAuditedSections,
        int lastDecodedRecords,
        int lastInvalidMetadata,
        int lastInvalidGeometryRecords,
        int lastEmptyBuckets,
        int lastNonEmptyBuckets,
        int lastMaxQuadLength,
        int lastMaxQuadWidth,
        int lastAuditedSectionId,
        int lastAuditedGeometryPtr
) {
    public static ForgeGpuGeometryAuditStats empty() {
        return new ForgeGpuGeometryAuditStats(
                0,
                0,
                "none",
                0.0D,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                -1,
                -1
        );
    }
}
