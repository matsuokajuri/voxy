package me.cortex.voxy.forge;

record ForgeFormalShaderInputConsumerAuditResult(
        boolean success,
        String error,
        double durationMs,
        boolean formalLifecycleSafeSetExists,
        boolean formalModelStoreResourcesReady,
        boolean bindingLayoutKnown,
        boolean bindingLayoutCompatible,
        boolean blockModelRecordLayoutCompatible,
        boolean safeSetModelIdsAddressable,
        boolean modelIdsFormal,
        boolean placeholderIdUsed,
        boolean sampleSetIdUsed,
        boolean sampleSetBridgeUsedAsFormalSource,
        boolean bindingValidationOk,
        boolean shaderBound,
        boolean drawOccurred,
        boolean formalRendererReady
) {
    static ForgeFormalShaderInputConsumerAuditResult failure(String error, double durationMs) {
        return new ForgeFormalShaderInputConsumerAuditResult(
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
                false
        );
    }
}
