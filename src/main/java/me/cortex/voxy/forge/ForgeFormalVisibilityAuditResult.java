package me.cortex.voxy.forge;

record ForgeFormalVisibilityAuditResult(
        boolean success,
        String error,
        boolean k1OwnerExists,
        boolean k2ViewportOwnerExists,
        boolean k3CommandGenerationOwnerExists,
        boolean formalVisibilityOwnerExists,
        boolean hierarchicalOcclusionInspected,
        boolean renderDistanceTrackerInspected,
        boolean viewportInspected,
        boolean mdicViewportInspected,
        boolean cmdgenVisibilityContractInspected,
        boolean visibilityContractExplicit,
        boolean renderListContractExplicit,
        boolean debugPlannerUsedAsFormal,
        boolean cpuCandidateSnapshotProvisional,
        boolean cmdgenComputeShaderRun,
        boolean multiDrawIndirectCountCalled,
        boolean mdicSectionRendererCalled,
        boolean voxyRenderSystemCalled,
        boolean formalDrawPipelineReady,
        boolean formalRendererReady,
        boolean actualRendererDrawEnabled
) {
    static ForgeFormalVisibilityAuditResult failure(String error) {
        return new ForgeFormalVisibilityAuditResult(
                false,
                error == null || error.isBlank() ? "unspecified" : error.replace(' ', '-'),
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false
        );
    }
}
