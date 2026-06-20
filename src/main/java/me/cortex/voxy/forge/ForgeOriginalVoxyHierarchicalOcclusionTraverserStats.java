package me.cortex.voxy.forge;

record ForgeOriginalVoxyHierarchicalOcclusionTraverserStats(
        boolean originalHierarchicalOcclusionTraverserReady,
        boolean traversalProgramReady,
        boolean nodeBufferReady,
        boolean requestBufferReady,
        boolean topNodeBufferReady,
        boolean queueMetaBufferReady,
        boolean scratchQueueBuffersReady,
        boolean hizSamplerReady,
        int topNodeCount,
        long traversalRunCount,
        long requestBatchForwardCount,
        String lastLifecycleEvent,
        String lastFailureReason
) {
    static ForgeOriginalVoxyHierarchicalOcclusionTraverserStats unavailable(String reason) {
        return new ForgeOriginalVoxyHierarchicalOcclusionTraverserStats(
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                0,
                0L,
                0L,
                "unavailable",
                reason == null || reason.isBlank() ? "hierarchical-occlusion-traverser-unavailable" : reason
        );
    }
}
