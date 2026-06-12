package me.cortex.voxy.forge;

public record ForgeGpuGeometryStats(
        boolean enabled,
        String reason,
        boolean heapCreated,
        long geometryCapacityBytes,
        long metadataCapacityBytes,
        long uploadedGeometryBytes,
        long uploadedSections,
        long metadataWrites,
        long removeIntentsProcessed,
        int pendingUploadIntents,
        int pendingRemoveIntents,
        int pendingDirtyMetadata,
        long failures,
        String lastError,
        double lastUploadMs,
        double averageUploadMs,
        int releasedBuffers,
        long clearCount,
        long validationRuns,
        long validationFailures,
        String lastValidationError,
        int lastValidationSectionId,
        int lastValidationGeometryPtr,
        boolean lastMetadataMatch,
        boolean lastGeometryMatch,
        boolean renderThreadOnly
) {
    public static ForgeGpuGeometryStats disabled() {
        return skipped("disabled", false, 0, 0, 0, 0, 0);
    }

    public static ForgeGpuGeometryStats skipped(
            String reason,
            boolean heapCreated,
            long geometryCapacityBytes,
            long metadataCapacityBytes,
            int pendingUploadIntents,
            int pendingRemoveIntents,
            int pendingDirtyMetadata
    ) {
        return new ForgeGpuGeometryStats(
                false,
                reason,
                heapCreated,
                geometryCapacityBytes,
                metadataCapacityBytes,
                0,
                0,
                0,
                0,
                pendingUploadIntents,
                pendingRemoveIntents,
                pendingDirtyMetadata,
                0,
                "none",
                0.0D,
                0.0D,
                0,
                0,
                0,
                0,
                "none",
                -1,
                -1,
                false,
                false,
                true
        );
    }
}
