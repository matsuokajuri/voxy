package me.cortex.voxy.forge;

import java.time.Instant;

final class ForgeModelBridgeResourceReloadTracker {
    private final ForgeVoxyInstance instance;
    private long reloadEventsSeen;
    private long simulationRuns;
    private String lastReloadStartedAt = "none";
    private String lastReloadFinishedAt = "none";
    private boolean modelBridgeInvalidatedOnReload;
    private boolean placeholderBuffersInvalidatedOnReload;
    private boolean placeholderBuffersStale;
    private boolean realModelStoreStale;
    private boolean textureAtlasStale;
    private boolean formalShaderInputsStale;
    private boolean bakedModelSamplesStale;
    private boolean spriteSamplesStale;
    private boolean lastReloadInvalidatedBakedModelSamples;
    private boolean realModelRecordSampleStale;
    private boolean lastReloadInvalidatedRealModelRecordSample;
    private boolean atlasSkeletonStale;
    private boolean lastReloadInvalidatedAtlasSkeleton;
    private boolean atlasPixelsStale;
    private boolean lastReloadInvalidatedAtlasPixels;
    private String lastReloadReason = "none";

    ForgeModelBridgeResourceReloadTracker(ForgeVoxyInstance instance) {
        this.instance = instance;
    }

    ForgeModelBridgeResourceReloadStats simulateReload(String reason) {
        this.simulationRuns++;
        this.reloadEventsSeen++;
        this.lastReloadStartedAt = Instant.now().toString();
        this.modelBridgeInvalidatedOnReload = true;
        this.placeholderBuffersInvalidatedOnReload = true;
        this.placeholderBuffersStale = true;
        this.realModelStoreStale = true;
        this.textureAtlasStale = true;
        this.formalShaderInputsStale = true;
        this.bakedModelSamplesStale = true;
        this.spriteSamplesStale = true;
        this.lastReloadInvalidatedBakedModelSamples = true;
        this.realModelRecordSampleStale = true;
        this.lastReloadInvalidatedRealModelRecordSample = true;
        this.atlasSkeletonStale = true;
        this.lastReloadInvalidatedAtlasSkeleton = true;
        this.atlasPixelsStale = true;
        this.lastReloadInvalidatedAtlasPixels = true;
        this.lastReloadReason = reason == null || reason.isBlank() ? "command-simulated-resource-reload" : reason;
        this.instance.getModelBridgeReadiness().clear();
        this.instance.getModelStoreSkeleton().markStale("resource-reload-simulated");
        this.instance.getBakedModelBridge().markStale("resource-reload-simulated");
        this.instance.getRealModelStoreSample().markStale("resource-reload-simulated");
        this.instance.getModelAtlasSkeleton().markStale("resource-reload-simulated");
        this.instance.getModelAtlasPixelUploader().markStale("resource-reload-simulated");
        this.instance.getTexturedDebugQuadRenderer().markStale("resource-reload-simulated");
        this.instance.getTexturedReadbackRenderer().markStale("resource-reload-simulated");
        this.instance.getTexturedMdicDebugRenderer().markStale("resource-reload-simulated");
        this.lastReloadFinishedAt = Instant.now().toString();
        return this.createStatusSnapshot();
    }

    ForgeModelBridgeResourceReloadStats createStatusSnapshot() {
        return new ForgeModelBridgeResourceReloadStats(
                true,
                false,
                this.reloadEventsSeen,
                this.simulationRuns,
                this.lastReloadStartedAt,
                this.lastReloadFinishedAt,
                this.modelBridgeInvalidatedOnReload,
                this.placeholderBuffersInvalidatedOnReload,
                this.placeholderBuffersStale,
                this.realModelStoreStale,
                this.textureAtlasStale,
                this.formalShaderInputsStale,
                this.bakedModelSamplesStale,
                this.spriteSamplesStale,
                this.lastReloadInvalidatedBakedModelSamples,
                this.realModelRecordSampleStale,
                this.lastReloadInvalidatedRealModelRecordSample,
                this.atlasSkeletonStale,
                this.lastReloadInvalidatedAtlasSkeleton,
                this.atlasPixelsStale,
                this.lastReloadInvalidatedAtlasPixels,
                this.lastReloadReason
        );
    }

    void clear() {
        this.reloadEventsSeen = 0L;
        this.simulationRuns = 0L;
        this.lastReloadStartedAt = "none";
        this.lastReloadFinishedAt = "none";
        this.modelBridgeInvalidatedOnReload = false;
        this.placeholderBuffersInvalidatedOnReload = false;
        this.placeholderBuffersStale = false;
        this.realModelStoreStale = false;
        this.textureAtlasStale = false;
        this.formalShaderInputsStale = false;
        this.bakedModelSamplesStale = false;
        this.spriteSamplesStale = false;
        this.lastReloadInvalidatedBakedModelSamples = false;
        this.realModelRecordSampleStale = false;
        this.lastReloadInvalidatedRealModelRecordSample = false;
        this.atlasSkeletonStale = false;
        this.lastReloadInvalidatedAtlasSkeleton = false;
        this.atlasPixelsStale = false;
        this.lastReloadInvalidatedAtlasPixels = false;
        this.lastReloadReason = "none";
    }
}
