package me.cortex.voxy.forge;

public record ForgeGpuGeometryAuditResult(
        boolean success,
        String reason,
        int auditedSections,
        int decodedRecords,
        int invalidMetadata,
        int invalidGeometryRecords,
        int emptyBuckets,
        int nonEmptyBuckets,
        int maxQuadLength,
        int maxQuadWidth,
        int lastAuditedSectionId,
        long lastAuditedPosition,
        int lastAuditedGeometryPtr,
        String lastAuditError,
        String lastMetadataWords,
        String lastDecodedMetadata,
        String lastDecodedRecords
) {
    public static ForgeGpuGeometryAuditResult failure(String reason) {
        String message = reason == null ? "unknown" : reason;
        return new ForgeGpuGeometryAuditResult(
                false,
                message,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                -1,
                0,
                -1,
                message,
                "none",
                "none",
                "none"
        );
    }

    static ForgeGpuGeometryAuditResult success(
            int auditedSections,
            int decodedRecords,
            int invalidMetadata,
            int invalidGeometryRecords,
            int emptyBuckets,
            int nonEmptyBuckets,
            int maxQuadLength,
            int maxQuadWidth,
            int lastAuditedSectionId,
            long lastAuditedPosition,
            int lastAuditedGeometryPtr,
            String lastAuditError,
            String lastMetadataWords,
            String lastDecodedMetadata,
            String lastDecodedRecords
    ) {
        boolean success = invalidMetadata == 0 && invalidGeometryRecords == 0 && decodedRecords > 0;
        String error = lastAuditError == null ? "unknown" : lastAuditError;
        if (!success && "none".equals(error) && decodedRecords <= 0) {
            error = "no-decoded-records";
        }
        String reason = success ? "ok" : error;
        return new ForgeGpuGeometryAuditResult(
                success,
                reason,
                auditedSections,
                decodedRecords,
                invalidMetadata,
                invalidGeometryRecords,
                emptyBuckets,
                nonEmptyBuckets,
                maxQuadLength,
                maxQuadWidth,
                lastAuditedSectionId,
                lastAuditedPosition,
                lastAuditedGeometryPtr,
                success ? "none" : error,
                lastMetadataWords,
                lastDecodedMetadata,
                lastDecodedRecords
        );
    }
}
