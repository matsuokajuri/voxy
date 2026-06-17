package me.cortex.voxy.forge;

record ForgeFormalModelIdSectionGeometryAuditResult(
        boolean success,
        String error,
        double durationMs,
        boolean originalRenderDataFactoryInspected,
        boolean originalModelFactoryInspected,
        boolean originalQuadFormatInspected,
        boolean k8InputUsesRealSectionData,
        boolean formalModelIdsBackedByRealBake,
        boolean placeholderIdsUsedAsFormalIds,
        boolean sampleSetUsedAsFormalSource,
        boolean formalGeometrySnapshotIsolated,
        boolean originalGeometryHeapMutated,
        boolean liveRendererConsumesK8Geometry,
        boolean mainFramebufferDraw,
        boolean mdicSectionRendererCalled,
        boolean voxyRenderSystemCalled,
        boolean formalModelIdDecodeOk,
        boolean formalGeometryReadbackOk,
        boolean formalRendererReady,
        boolean actualRendererDrawEnabled
) {
    static ForgeFormalModelIdSectionGeometryAuditResult failure(String error, double durationMs) {
        return new ForgeFormalModelIdSectionGeometryAuditResult(
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
                false,
                false,
                false,
                false
        );
    }
}
