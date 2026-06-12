package me.cortex.voxy.forge;

record ForgeDirectGpuGeometryRendererStats(
        boolean enabled,
        boolean initialized,
        boolean hasHeap,
        boolean heapCreated,
        int plannedSections,
        long plannedRecords,
        int uploadedSectionCandidates,
        int invalidMetadata,
        double lastPlanDurationMs,
        String lastPlanError,
        String lastSkippedReason,
        long planRuns,
        long planFailures,
        long clearCount,
        int maxSections,
        int maxRecords,
        int drawCallsIssued,
        int verticesDrawn,
        boolean actualDrawEnabled,
        String stage
) {
}
