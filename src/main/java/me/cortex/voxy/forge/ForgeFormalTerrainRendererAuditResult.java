package me.cortex.voxy.forge;

record ForgeFormalTerrainRendererAuditResult(
        boolean success,
        String error,
        boolean k0AuditDocExists,
        boolean k0VerdictReadyForK1,
        boolean ownerExists,
        boolean originalVoxyAlignmentPreserved,
        boolean previewSystemsSeparated,
        boolean debugRenderersIsolated,
        boolean sampleSetUsedAsFormalSource,
        boolean terrainDrawStarted,
        boolean formalRendererDrawStarted,
        boolean mdicSectionRendererCalled,
        boolean voxyRenderSystemCalled,
        boolean formalTerrainRendererReady,
        boolean actualRendererDrawEnabled
) {
    static ForgeFormalTerrainRendererAuditResult failure(String error) {
        return new ForgeFormalTerrainRendererAuditResult(
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
                false
        );
    }
}
