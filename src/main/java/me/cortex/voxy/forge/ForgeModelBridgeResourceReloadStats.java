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
        boolean bakedModelSamplesStale,
        boolean spriteSamplesStale,
        boolean lastReloadInvalidatedBakedModelSamples,
        boolean realModelRecordSampleStale,
        boolean lastReloadInvalidatedRealModelRecordSample,
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
                false,
                false,
                false,
                false,
                false,
                "none"
        );
    }
}
