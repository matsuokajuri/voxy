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
    private boolean modelStoreSkeletonStale;
    private boolean originalVoxyPipelineReloadMarked;
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
        this.modelStoreSkeletonStale = true;
        this.originalVoxyPipelineReloadMarked = true;
        this.lastReloadReason = reason == null || reason.isBlank() ? (real ? "forge-client-resource-reload" : "command-simulated-resource-reload") : reason;
        String staleReason = real ? "resource-reload-event" : "resource-reload-simulated";
        try {
            this.instance.getModelStoreSkeleton().markStale(staleReason);
            this.instance.getOriginalVoxyModelPipeline().markResourceReload();
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
                this.modelStoreSkeletonStale,
                this.originalVoxyPipelineReloadMarked,
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
        this.modelStoreSkeletonStale = false;
        this.originalVoxyPipelineReloadMarked = false;
        this.lastReloadReason = "none";
    }
}
