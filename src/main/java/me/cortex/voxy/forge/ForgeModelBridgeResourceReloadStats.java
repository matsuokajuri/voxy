package me.cortex.voxy.forge;

record ForgeModelBridgeResourceReloadStats(
        boolean reloadLifecycleReady,
        boolean realResourceReloadListenerReady,
        boolean resourceReloadReady,
        long reloadEventsSeen,
        long realReloadEventsSeen,
        long simulatedReloadEventsSeen,
        String lastReloadStartedAt,
        String lastReloadFinishedAt,
        String lastReloadSource,
        String lastReloadThread,
        boolean reloadCleanupScheduled,
        boolean reloadCleanupOnRenderThread,
        boolean reloadCleanupCompleted,
        long reloadCleanupFailures,
        boolean originalModelResourcesStale,
        boolean originalVoxyPipelineReloadMarked,
        String lastReloadReason
) {
    static ForgeModelBridgeResourceReloadStats empty() {
        return new ForgeModelBridgeResourceReloadStats(
                true,
                false,
                false,
                0L,
                0L,
                0L,
                "none",
                "none",
                "NONE",
                "none",
                false,
                false,
                false,
                0L,
                false,
                false,
                "none"
        );
    }
}
