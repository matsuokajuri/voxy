package me.cortex.voxy.forge;

record ForgeFormalTerrainShaderIntegrationAuditResult(
        boolean success,
        String error,
        double durationMs,
        boolean originalTerrainShadersInspected,
        boolean k9ShaderPathBindsFormalModelStore,
        boolean k9DrawInputUsesK8GeometryAndK6Command,
        boolean placeholderIdsUsedAsFormalIds,
        boolean sampleSetUsedAsFormalSource,
        boolean formalGeometrySnapshotIsolated,
        boolean originalGeometryHeapMutated,
        boolean debugCommandBuffersUsedAsFormal,
        boolean offscreenFramebufferComplete,
        boolean offscreenTerrainShaderDrawExecuted,
        boolean offscreenReadbackOk,
        int offscreenNonZeroPixelCount,
        boolean formalModelIdDecodeOk,
        boolean faceDataLookupOk,
        boolean atlasSampleOk,
        boolean modelDataReadOk,
        boolean modelColourReadOk,
        boolean mainFramebufferDraw,
        boolean mdicSectionRendererCalled,
        boolean voxyRenderSystemCalled,
        boolean formalRendererReady,
        boolean actualRendererDrawEnabled
) {
    static ForgeFormalTerrainShaderIntegrationAuditResult failure(String error, double durationMs) {
        return new ForgeFormalTerrainShaderIntegrationAuditResult(
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
                0,
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
