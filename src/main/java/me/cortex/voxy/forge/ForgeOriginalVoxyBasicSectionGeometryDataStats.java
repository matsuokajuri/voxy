package me.cortex.voxy.forge;

record ForgeOriginalVoxyBasicSectionGeometryDataStats(
        boolean originalBasicSectionGeometryDataReady,
        boolean renderThreadGeometryDataStoreReady,
        boolean originalGeometryCapacityPolicyUsed,
        boolean metadataBufferReady,
        boolean geometryBufferReady,
        boolean externalGeometryBuffer,
        boolean sparseGeometryBuffer,
        boolean sparseBufferSupported,
        boolean nvidiaWindowsSparseWorkaroundUsed,
        int maxSectionCount,
        int currentSectionCount,
        int metadataBufferId,
        int geometryBufferId,
        long metadataCapacityBytes,
        long geometryCapacityBytes,
        long sparseCommitmentBytes,
        long generation,
        long buildCount,
        long freeCount,
        int lastGlError,
        String lastLifecycleEvent,
        String lastFailureReason
) {
    static ForgeOriginalVoxyBasicSectionGeometryDataStats unavailable(String reason) {
        return new ForgeOriginalVoxyBasicSectionGeometryDataStats(
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                0,
                0,
                0,
                0,
                0L,
                0L,
                0L,
                0L,
                0L,
                0L,
                0,
                "unavailable",
                reason == null || reason.isBlank() ? "basic-section-geometry-data-unavailable" : reason
        );
    }
}
