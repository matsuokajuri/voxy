package me.cortex.voxy.forge;

record ForgeOriginalVoxyRenderGenerationStats(
        boolean renderGenerationServiceReady,
        boolean originalRenderDataFactoryUsed,
        boolean originalBuildTaskPriorityUsed,
        boolean originalHoldingSectionPolicyUsed,
        boolean originalModelMissRequestRequeueUsed,
        boolean originalServiceManagerParityReady,
        int taskQueueCount,
        int taskMapCount,
        int holdingSectionCount,
        long enqueuedTaskCount,
        long processedTaskCount,
        long completedMeshCount,
        long emptyMeshCount,
        long requeueCount,
        long replacedTaskCount,
        long modelMissRequestCount,
        long innerModelRequestScanCount,
        long outerModelRequestScanCount,
        long failedMeshCount,
        long lastTaskPosition,
        String lastLifecycleEvent,
        String lastFailureReason
) {
    static ForgeOriginalVoxyRenderGenerationStats unavailable(String reason) {
        return new ForgeOriginalVoxyRenderGenerationStats(
                false,
                false,
                false,
                false,
                false,
                false,
                0,
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
                0L,
                0L,
                "unavailable",
                reason == null || reason.isBlank() ? "render-generation-unavailable" : reason
        );
    }
}
