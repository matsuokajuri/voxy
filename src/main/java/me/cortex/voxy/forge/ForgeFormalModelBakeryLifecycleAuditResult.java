package me.cortex.voxy.forge;

record ForgeFormalModelBakeryLifecycleAuditResult(
        boolean success,
        String error,
        double durationMs,
        boolean formalModelStoreOwnerExists,
        boolean formalModelFactoryLifecycleExists,
        boolean multiBlockUploadConsistent,
        boolean everyAcceptedBlockHasFormalModelId,
        boolean placeholderIdUsed,
        boolean sampleSetIdUsed,
        boolean aliasMappingsValid,
        int illegalDuplicateMappingCount,
        boolean modelDataReadbackOk,
        boolean modelColourReadbackOk,
        boolean atlasReadbackOk,
        int atlasPixelMismatches,
        boolean generationCountersValid,
        boolean shaderBound,
        boolean drawOccurred,
        boolean formalRendererReady,
        boolean actualDrawEnabled
) {
    static ForgeFormalModelBakeryLifecycleAuditResult failure(String error, double durationMs) {
        return new ForgeFormalModelBakeryLifecycleAuditResult(
                false,
                error == null || error.isBlank() ? "audit-failed" : error,
                durationMs,
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
                0,
                false,
                false,
                false,
                false,
                false
        );
    }
}
