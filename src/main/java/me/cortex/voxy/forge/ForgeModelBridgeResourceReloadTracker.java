package me.cortex.voxy.forge;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;

import java.time.Instant;

final class ForgeModelBridgeResourceReloadTracker {
    private final ForgeVoxyInstance instance;
    private boolean realResourceReloadListenerReady;
    private long reloadEventsSeen;
    private long realReloadEventsSeen;
    private long simulatedReloadEventsSeen;
    private String lastReloadStartedAt = "none";
    private String lastReloadFinishedAt = "none";
    private String lastReloadSource = "NONE";
    private String lastReloadThread = "none";
    private boolean reloadCleanupScheduled;
    private boolean reloadCleanupOnRenderThread;
    private boolean reloadCleanupCompleted;
    private long reloadCleanupFailures;
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
    private boolean sampleSetStale;
    private boolean atlasSkeletonStale;
    private boolean lastReloadInvalidatedAtlasSkeleton;
    private boolean atlasPixelsStale;
    private boolean lastReloadInvalidatedAtlasPixels;
    private boolean formalShaderInputBridgeStale;
    private boolean texturedDebugQuadStale;
    private boolean texturedReadbackStale;
    private boolean texturedMdicDebugStale;
    private String lastReloadReason = "none";

    ForgeModelBridgeResourceReloadTracker(ForgeVoxyInstance instance) {
        this.instance = instance;
    }

    void registerClientReloadListeners(RegisterClientReloadListenersEvent event) {
        this.realResourceReloadListenerReady = true;
        event.registerReloadListener(new ForgeModelBridgeReloadListener(this));
    }

    ForgeModelBridgeResourceReloadStats handleRealResourceReload() {
        return this.handleReload("REAL_RESOURCE_RELOAD", "forge-client-resource-reload", true);
    }

    ForgeModelBridgeResourceReloadStats simulateReload(String reason) {
        return this.handleReload("SIMULATED", reason, false);
    }

    private ForgeModelBridgeResourceReloadStats handleReload(String source, String reason, boolean real) {
        if (real) {
            this.realReloadEventsSeen++;
        } else {
            this.simulatedReloadEventsSeen++;
        }
        this.reloadEventsSeen++;
        this.lastReloadStartedAt = Instant.now().toString();
        this.lastReloadSource = source == null || source.isBlank() ? "UNKNOWN" : source;
        this.lastReloadThread = Thread.currentThread().getName();
        this.reloadCleanupOnRenderThread = RenderSystem.isOnRenderThread();
        this.reloadCleanupScheduled = !this.reloadCleanupOnRenderThread;
        this.reloadCleanupCompleted = false;
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
        this.sampleSetStale = true;
        this.atlasSkeletonStale = true;
        this.lastReloadInvalidatedAtlasSkeleton = true;
        this.atlasPixelsStale = true;
        this.lastReloadInvalidatedAtlasPixels = true;
        this.formalShaderInputBridgeStale = true;
        this.texturedDebugQuadStale = true;
        this.texturedReadbackStale = true;
        this.texturedMdicDebugStale = true;
        this.lastReloadReason = reason == null || reason.isBlank() ? (real ? "forge-client-resource-reload" : "command-simulated-resource-reload") : reason;
        String staleReason = real ? "resource-reload-event" : "resource-reload-simulated";
        try {
            this.instance.getModelBridgeReadiness().clear();
            this.instance.getModelStoreSkeleton().markStale(staleReason);
            this.instance.getBakedModelBridge().markStale(staleReason);
            this.instance.getRealModelStoreSample().markStale(staleReason);
            this.instance.getModelSampleSet().markStale(staleReason);
            this.instance.getModelAtlasSkeleton().markStale(staleReason);
            this.instance.getModelAtlasPixelUploader().markStale(staleReason);
            this.instance.getModelAtlasSampleSetUploader().markStale(staleReason);
            this.instance.getFormalShaderInputBridge().markStale(staleReason);
            this.instance.getTexturedDebugQuadRenderer().markStale(staleReason);
            this.instance.getTexturedReadbackRenderer().markStale(staleReason);
            this.instance.getTexturedMdicDebugRenderer().markStale(staleReason);
            this.reloadCleanupCompleted = true;
        } catch (RuntimeException e) {
            this.reloadCleanupFailures++;
            this.reloadCleanupCompleted = false;
            VoxyForge.LOGGER.error("Failed to stale model bridge resources after {}.", this.lastReloadSource, e);
        }
        this.lastReloadFinishedAt = Instant.now().toString();
        return this.createStatusSnapshot();
    }

    ForgeModelBridgeResourceReloadStats createStatusSnapshot() {
        return new ForgeModelBridgeResourceReloadStats(
                true,
                this.realResourceReloadListenerReady,
                this.realResourceReloadListenerReady,
                this.reloadEventsSeen,
                this.realReloadEventsSeen,
                this.simulatedReloadEventsSeen,
                this.lastReloadStartedAt,
                this.lastReloadFinishedAt,
                this.lastReloadSource,
                this.lastReloadThread,
                this.reloadCleanupScheduled,
                this.reloadCleanupOnRenderThread,
                this.reloadCleanupCompleted,
                this.reloadCleanupFailures,
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
                this.sampleSetStale,
                this.atlasSkeletonStale,
                this.lastReloadInvalidatedAtlasSkeleton,
                this.atlasPixelsStale,
                this.lastReloadInvalidatedAtlasPixels,
                this.formalShaderInputBridgeStale,
                this.texturedDebugQuadStale,
                this.texturedReadbackStale,
                this.texturedMdicDebugStale,
                this.lastReloadReason
        );
    }

    void clear() {
        this.clearStats();
    }

    void clearStats() {
        this.reloadEventsSeen = 0L;
        this.realReloadEventsSeen = 0L;
        this.simulatedReloadEventsSeen = 0L;
        this.lastReloadStartedAt = "none";
        this.lastReloadFinishedAt = "none";
        this.lastReloadSource = "NONE";
        this.lastReloadThread = "none";
        this.reloadCleanupScheduled = false;
        this.reloadCleanupOnRenderThread = false;
        this.reloadCleanupCompleted = false;
        this.reloadCleanupFailures = 0L;
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
        this.sampleSetStale = false;
        this.atlasSkeletonStale = false;
        this.lastReloadInvalidatedAtlasSkeleton = false;
        this.atlasPixelsStale = false;
        this.lastReloadInvalidatedAtlasPixels = false;
        this.formalShaderInputBridgeStale = false;
        this.texturedDebugQuadStale = false;
        this.texturedReadbackStale = false;
        this.texturedMdicDebugStale = false;
        this.lastReloadReason = "none";
    }
}
