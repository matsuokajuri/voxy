package me.cortex.voxy.forge;

record ForgeFormalCommandGenerationAuditResult(
        boolean success,
        String error,
        boolean k1OwnerExists,
        boolean k2ViewportOwnerExists,
        boolean formalCommandGenerationOwnerExists,
        boolean cmdgenCompInspected,
        boolean bindingsGlslInspected,
        boolean mdicViewportInspected,
        boolean mdicSectionRendererInspected,
        boolean debugMdicCommandBuffersUsedAsFormal,
        boolean commandInputContractExplicit,
        boolean commandOutputContractExplicit,
        boolean drawCommandLayoutReady,
        boolean drawCountLayoutReady,
        boolean cmdgenComputeShaderRun,
        boolean multiDrawIndirectCountCalled,
        boolean mdicSectionRendererCalled,
        boolean voxyRenderSystemCalled,
        boolean formalDrawPipelineReady,
        boolean formalRendererReady,
        boolean actualRendererDrawEnabled
) {
    static ForgeFormalCommandGenerationAuditResult failure(String error) {
        return new ForgeFormalCommandGenerationAuditResult(
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
                false
        );
    }
}
