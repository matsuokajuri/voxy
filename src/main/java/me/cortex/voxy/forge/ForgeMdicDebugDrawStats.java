package me.cortex.voxy.forge;

record ForgeMdicDebugDrawStats(
        String stage,
        boolean drawEnabled,
        boolean actualDrawEnabled,
        boolean shaderSupported,
        boolean shaderCompiled,
        boolean programCreated,
        boolean multiDrawSupported,
        boolean indirectSupported,
        boolean drawIdSupported,
        boolean baseInstanceSupported,
        String configuredDrawMode,
        String effectiveDrawMode,
        String autoModeSelectedReason,
        String autoModeFallbackReason,
        boolean loopShaderCompiled,
        boolean multiDrawShaderCompiled,
        boolean indirectShaderCompiled,
        boolean commandBufferCreated,
        boolean commandListValid,
        boolean commandListStale,
        boolean commandBufferStale,
        boolean derivedIndirectCommandBufferCreated,
        long derivedIndirectCommandBufferBytes,
        boolean derivedIndirectCommandBufferStale,
        boolean hasHeap,
        boolean heapCreated,
        long currentHeapGeneration,
        String currentDimension,
        long commandListGeneration,
        long commandBufferGeneration,
        String commandListDimension,
        String commandBufferDimension,
        int commandCount,
        long commandRecords,
        int maxCommands,
        int maxRecords,
        double alpha,
        boolean ignoreDepth,
        boolean doubleSided,
        int lastFrameApiDrawCalls,
        int lastFrameLogicalCommands,
        long lastFrameVertices,
        long drawApiCallsIssued,
        long logicalCommandsDrawn,
        long verticesDrawn,
        double lastFrameRenderMs,
        double maxFrameRenderMs,
        double avgFrameRenderMs,
        boolean lastFrameOverBudget,
        long overBudgetFrames,
        double frameBudgetMs,
        String lastGlError,
        String lastGlErrorStage,
        long glErrorCount,
        long stateRestoreFailures,
        String lastStateRestoreError,
        String lastRenderSkippedReason,
        String lastDrawError,
        boolean lastIndirectAuditOk,
        long indirectAuditRuns,
        long indirectAuditFailures,
        String lastIndirectAuditError,
        double lastIndirectAuditDurationMs,
        int lastAuditedIndirectCommands,
        long lastAuditedIndirectBytes,
        int lastInvalidIndirectCommands,
        boolean lastIndirectCommandBufferMatch,
        long lastIndirectAuditVertices,
        long stressRuns,
        long stressFailures,
        String lastStressError,
        double lastStressDurationMs,
        boolean lastStressPlanOk,
        boolean lastStressBuildOk,
        boolean lastStressAuditOk,
        boolean lastStressDrawEnableOk,
        boolean lastStressDrawStatusOk,
        boolean lastStressDrawDisableOk,
        boolean lastStressDrawClearOk,
        boolean lastStressHeapClearOk,
        boolean lastStressRebuildOk,
        boolean lastStressRedrawOk,
        boolean lastStressLoopOk,
        boolean lastStressMultiDrawOk,
        boolean lastStressIndirectOk,
        boolean lastStressAutoOk,
        boolean lastStressDerivedIndirectAuditOk,
        boolean lastStressSourceRegressionOk,
        boolean lastStressBucketDrawOk,
        boolean lastStressBucketIndirectAuditOk,
        boolean lastStressDirectionalFaceMaskOk,
        boolean lastStressFaceMaskAuditOk,
        long lastStressGlErrorCount,
        long lastStressStateRestoreFailures
) {
    int lastFrameDrawCalls() {
        return this.lastFrameApiDrawCalls;
    }

    int lastFrameCommands() {
        return this.lastFrameLogicalCommands;
    }

    long drawCallsIssued() {
        return this.drawApiCallsIssued;
    }

    long commandsDrawn() {
        return this.logicalCommandsDrawn;
    }

    String debugDrawMode() {
        return this.effectiveDrawMode;
    }
}
