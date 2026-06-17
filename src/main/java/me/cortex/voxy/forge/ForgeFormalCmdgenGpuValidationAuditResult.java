package me.cortex.voxy.forge;

record ForgeFormalCmdgenGpuValidationAuditResult(
        boolean success,
        String error,
        double durationMs,
        boolean k1OwnerExists,
        boolean k2ViewportOwnerExists,
        boolean k3CommandGenerationOwnerExists,
        boolean k4VisibilityOwnerExists,
        boolean cmdgenCompInspected,
        boolean bindingsGlslInspected,
        boolean validationBuffersIsolated,
        boolean debugMdicCommandBuffersUsedAsFormal,
        boolean validationDispatchRun,
        boolean liveCommandGenerationRun,
        boolean drawCommandReadbackOk,
        boolean drawCountReadbackOk,
        boolean positionScratchReadbackOk,
        int generatedCommandCount,
        int generatedDrawCount,
        boolean multiDrawIndirectCountCalled,
        boolean mdicSectionRendererCalled,
        boolean voxyRenderSystemCalled,
        boolean formalDrawPipelineReady,
        boolean formalRendererReady,
        boolean actualRendererDrawEnabled
) {
    static ForgeFormalCmdgenGpuValidationAuditResult failure(String error, double durationMs) {
        return new ForgeFormalCmdgenGpuValidationAuditResult(
                false,
                error == null || error.isBlank() ? "unspecified" : error.replace(' ', '-'),
                durationMs,
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
                0,
                0,
                false,
                false,
                false,
                false,
                false,
                false
        );
    }
}
