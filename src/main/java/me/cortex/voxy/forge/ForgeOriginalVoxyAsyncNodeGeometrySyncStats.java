package me.cortex.voxy.forge;

record ForgeOriginalVoxyAsyncNodeGeometrySyncStats(
        boolean originalAsyncNodeManagerSyncShapeReady,
        boolean originalNodeManagerParityReady,
        boolean originalGeometryCacheReady,
        boolean geometryResultQueueReady,
        boolean renderThreadTickReady,
        boolean multiMemcpyProgramReady,
        boolean scatterProgramReady,
        boolean syncResultPending,
        boolean directGeneratedSectionBridgeRemoved,
        int queuedGeometryResults,
        int trackedSectionIdCount,
        long submittedGeometryResultCount,
        long processedGeometryResultCount,
        long publishedSyncResultCount,
        long renderThreadTickCount,
        long geometryUploadCopyDispatchCount,
        long metadataScatterDispatchCount,
        long uploadedGeometryCopyCount,
        long uploadedGeometryBytes,
        long metadataScatterWriteCount,
        int currentMaxNodeId,
        long usedGeometryBytes,
        String lastLifecycleEvent,
        String lastFailureReason
) {
    static ForgeOriginalVoxyAsyncNodeGeometrySyncStats unavailable(String reason) {
        return new ForgeOriginalVoxyAsyncNodeGeometrySyncStats(
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
                0L,
                0L,
                0L,
                0L,
                0L,
                0L,
                0L,
                0L,
                0L,
                0,
                0L,
                "unavailable",
                reason == null || reason.isBlank() ? "async-node-geometry-sync-unavailable" : reason
        );
    }
}
