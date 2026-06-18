package me.cortex.voxy.forge;

record ForgeFormalVisibleLodPreviewAuditResult(
        boolean success,
        String error,
        double durationMs,
        boolean prerequisitesChecked,
        boolean previewDisabledByDefault,
        boolean explicitOptInRequired,
        boolean drawInputUsesK8AndK9,
        boolean placeholderIdsUsedAsFormalIds,
        boolean sampleSetUsedAsFormalSource,
        boolean originalGeometryHeapMutated,
        boolean debugCommandBuffersUsedAsFormal,
        boolean visiblePreviewDrawExecuted,
        boolean minecraftMainFramebufferDrawn,
        boolean previewDisabledAfterQa,
        boolean mdicSectionRendererCalled,
        boolean voxyRenderSystemCalled,
        boolean formalRendererReady,
        boolean actualRendererDrawEnabled
) {
    static ForgeFormalVisibleLodPreviewAuditResult failure(String error, double durationMs) {
        return new ForgeFormalVisibleLodPreviewAuditResult(
                false,
                error == null || error.isBlank() ? "unspecified" : error,
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
                false,
                false
        );
    }
}
