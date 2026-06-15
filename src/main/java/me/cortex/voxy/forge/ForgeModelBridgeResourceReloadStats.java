package me.cortex.voxy.forge;

record ForgeModelBridgeResourceReloadStats(
        boolean reloadLifecycleSkeletonReady,
        boolean resourceReloadReady,
        long reloadEventsSeen,
        long lastReloadSimulationRuns,
        String lastReloadStartedAt,
        String lastReloadFinishedAt,
        boolean modelBridgeInvalidatedOnReload,
        boolean placeholderBuffersInvalidatedOnReload,
        boolean placeholderBuffersStale,
        boolean realModelStoreStale,
        boolean textureAtlasStale,
        boolean formalShaderInputsStale,
        String lastReloadReason
) {
    static ForgeModelBridgeResourceReloadStats empty() {
        return new ForgeModelBridgeResourceReloadStats(
                true,
                false,
                0L,
                0L,
                "none",
                "none",
                false,
                false,
                false,
                false,
                false,
                false,
                "none"
        );
    }
}
