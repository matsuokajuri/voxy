package me.cortex.voxy.forge;

record ForgeOriginalVoxyVisibleRendererStats(
        String originalVisibleRendererStage,
        boolean originalVisibleRendererOwnerReady,
        boolean originalVoxyRunPipelineOrderUsed,
        boolean originalVisibleMdicDrawSubmissionUsed,
        boolean originalPostFrameDynamicWorkUsed,
        boolean originalMdicOpaqueDrawSubmitted,
        boolean originalMdicTemporalDrawSubmitted,
        boolean originalMdicTranslucentDrawSubmitted,
        boolean originalPipelineFinishCalled,
        boolean originalVisibleRendererStateRestoreUsed,
        long originalVisibleFrameRunCount,
        long originalVisibleFrameSkippedCount,
        long originalVisibleFrameFailureCount,
        long originalVisibleFrameDrawSubmissionCount,
        int originalVisibleFrameLastFramebuffer,
        int originalVisibleFrameLastViewportWidth,
        int originalVisibleFrameLastViewportHeight,
        boolean originalVisibleRendererUsesPreviewRoute
) {
}
