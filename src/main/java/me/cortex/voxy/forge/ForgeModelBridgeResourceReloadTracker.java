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
        this.lastReloadReason = reason == null || reason.isBlank() ? "command-simulated-resource-reload" : reason;
        this.instance.getModelBridgeReadiness().clear();
        this.instance.getModelStoreSkeleton().markStale("resource-reload-simulated");
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
        this.lastReloadReason = "none";
    }
}
