package me.cortex.voxy.forge;

record ForgeFormalTerrainPackedRecordBridgeAuditResult(
        boolean success,
        String error,
        double durationMs,
        boolean i6SafeSetExists,
        boolean j4PreviewPathExists,
        boolean realTerrainRecordsUsed,
        boolean syntheticFallbackUsed,
        boolean acceptedRecordsHaveFormalMapping,
        boolean formalModelIdsBackedByRealBake,
        boolean placeholderIdUsed,
        boolean sampleSetIdUsed,
        boolean sampleSetBridgeUsedAsFormalSource,
        boolean originalRecordUnchanged,
        boolean originalGeometryUntouched,
        boolean originalGeometryHeapUntouched,
        boolean temporaryFormalQuadBufferIsolated,
        boolean offscreenPreviewReady,
        boolean previewFramebufferComplete,
        boolean previewReadbackOk,
        int previewPixelMismatches,
        int previewChecksumCount,
        boolean terrainDrawStarted,
        boolean formalRendererDrawStarted,
        boolean actualRendererDrawEnabled,
        boolean formalRendererReady
) {
    static ForgeFormalTerrainPackedRecordBridgeAuditResult failure(String error, double durationMs) {
        return new ForgeFormalTerrainPackedRecordBridgeAuditResult(
                false,
                error == null || error.isBlank() ? "unknown" : error,
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
                0,
                0,
                false,
                false,
                false,
                false
        );
    }
}
