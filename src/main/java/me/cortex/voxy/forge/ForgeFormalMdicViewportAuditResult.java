package me.cortex.voxy.forge;

record ForgeFormalMdicViewportAuditResult(
        boolean success,
        String error,
        boolean k1OwnerExists,
        boolean formalViewportOwnerExists,
        boolean formalCommandOwnershipExplicit,
        boolean formalVisibilityOwnershipExplicit,
        boolean originalVoxyMdicViewportInspected,
        boolean originalVoxyMdicSectionRendererInspected,
        boolean debugMdicCommandBuffersUsedAsFormal,
        boolean debugRendererStartedOrReplaced,
        boolean drawCommandExecuted,
        boolean multiDrawIndirectCountCalled,
        boolean formalCmdgenExecuted,
        boolean mdicSectionRendererCalled,
        boolean voxyRenderSystemCalled,
        boolean formalRendererReady,
        boolean actualRendererDrawEnabled
) {
    static ForgeFormalMdicViewportAuditResult failure(String error) {
        return new ForgeFormalMdicViewportAuditResult(
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
                false
        );
    }
}
