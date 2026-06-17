package me.cortex.voxy.forge;

record ForgeFormalShaderProgramAuditResult(
        boolean success,
        String error,
        double durationMs,
        boolean i6SafeSetExists,
        boolean j1FormalShaderInputValid,
        boolean shaderCompileOk,
        boolean programLinkOk,
        boolean modelDataBindingOk,
        boolean modelColourBindingOk,
        boolean atlasTextureBindingOk,
        boolean samplerBindingOk,
        boolean bindingLayoutCompatible,
        boolean blockModelRecordLayoutCompatible,
        boolean formalModelIdsAddressable,
        boolean placeholderIdUsed,
        boolean sampleSetIdUsed,
        boolean sampleSetBridgeUsedAsFormalSource,
        boolean gpuValidationOk,
        boolean validationReadbackOk,
        int validationFailureCount,
        boolean terrainDrawStarted,
        boolean visibleDrawStarted,
        boolean actualDrawEnabled,
        boolean formalRendererReady
) {
    static ForgeFormalShaderProgramAuditResult failure(String error, double durationMs) {
        return new ForgeFormalShaderProgramAuditResult(
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
                false,
                false,
                false,
                false
        );
    }
}
