package me.cortex.voxy.forge;

import com.mojang.blaze3d.systems.RenderSystem;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import me.cortex.voxy.common.world.WorldEngine;
import me.cortex.voxy.common.world.other.Mapper;
import net.minecraft.client.Minecraft;

import java.util.Arrays;
import java.util.Optional;
import java.util.concurrent.ConcurrentLinkedDeque;

final class ForgeOriginalVoxyModelPipeline {
    static final String STAGE = "L0_L4_ORIGINAL_VOXY_MODEL_PIPELINE_PARITY";

    private final ForgeVoxyInstance instance;
    private final ConcurrentLinkedDeque<Integer> blockBakeQueue = new ConcurrentLinkedDeque<>();
    private final ConcurrentLinkedDeque<Mapper.BiomeEntry> biomeQueue = new ConcurrentLinkedDeque<>();
    private final IntOpenHashSet seenBlockBakeRequests = new IntOpenHashSet(6000);
    private WorldEngine world;
    private boolean startRequested;
    private boolean startQueuedOnRenderThread;
    private boolean ownerReady;
    private boolean mapperBiomeCallbackAttached;
    private boolean existingBiomeEntriesQueued;
    private boolean stale;
    private boolean requiresRebuild;
    private long startRequests;
    private long startRuns;
    private long tickRuns;
    private long uploadTickRuns;
    private long blockBakeRequests;
    private long clearRuns;
    private String lifecycleState = "UNINITIALIZED";
    private String lastLifecycleEvent = "initialized";
    private String lastFailureReason = "none";

    ForgeOriginalVoxyModelPipeline(ForgeVoxyInstance instance) {
        this.instance = instance;
    }

    synchronized ForgeOriginalVoxyModelPipelineStats requestStart(String reason) {
        this.startRequests++;
        this.startRequested = true;
        this.stale = false;
        this.requiresRebuild = false;
        this.lifecycleState = "START_REQUESTED";
        this.lastLifecycleEvent = safeReason(reason);
        this.lastFailureReason = "none";
        return this.createStatusSnapshot();
    }

    void clientTick() {
        if (this.shouldScheduleStart()) {
            this.runOnRenderThread(this::startOnRenderThread);
        }
        synchronized (this) {
            if (this.ownerReady && !this.stale) {
                this.tickRuns++;
                this.lifecycleState = "RUNNING";
                this.lastLifecycleEvent = "tick";
            }
        }
    }

    synchronized ForgeOriginalVoxyModelPipelineStats requestBlockBake(int blockStateId) {
        this.blockBakeRequests++;
        if (!this.ownerReady || this.stale) {
            this.lastFailureReason = "original-model-pipeline-owner-not-ready";
            return this.createStatusSnapshot();
        }
        if (blockStateId <= 0) {
            this.lastFailureReason = "invalid-block-state-id-" + blockStateId;
            return this.createStatusSnapshot();
        }
        if (this.seenBlockBakeRequests.add(blockStateId)) {
            this.blockBakeQueue.add(blockStateId);
        }
        this.lastFailureReason = "model-factory-port-pending";
        this.lastLifecycleEvent = "request-block-bake-queued";
        return this.createStatusSnapshot();
    }

    synchronized ForgeOriginalVoxyModelPipelineStats createStatusSnapshot() {
        Mapper mapper = this.world == null ? null : this.world.getMapper();
        int mapperBlockStateCount = mapper == null ? 0 : mapper.getBlockStateCount();
        int mapperBiomeCount = mapper == null ? 0 : mapper.getBiomeEntries().length;
        return new ForgeOriginalVoxyModelPipelineStats(
                STAGE,
                this.startRequests,
                this.startRuns,
                this.tickRuns,
                this.uploadTickRuns,
                this.blockBakeRequests,
                this.clearRuns,
                this.startRequested,
                this.startQueuedOnRenderThread,
                this.ownerReady && !this.stale,
                false,
                false,
                false,
                false,
                this.mapperBiomeCallbackAttached && this.ownerReady && !this.stale,
                false,
                this.existingBiomeEntriesQueued && this.ownerReady && !this.stale,
                true,
                false,
                false,
                false,
                false,
                false,
                this.stale,
                this.requiresRebuild,
                mapperBlockStateCount,
                mapperBiomeCount,
                this.blockBakeQueue.size() + this.biomeQueue.size(),
                0,
                this.blockBakeQueue.isEmpty() && this.biomeQueue.isEmpty(),
                this.lifecycleState,
                this.lastLifecycleEvent,
                this.lastFailureReason
        );
    }

    void markResourceReload() {
        this.markStaleAndClear("resource-reload");
    }

    void markWorldUnload() {
        this.markStaleAndClear("world-unload");
    }

    void markDimensionSwitch() {
        this.markStaleAndClear("dimension-switch");
    }

    void markDebugPipelineClear() {
        this.markStaleAndClear("debug-pipeline-clear");
    }

    void markPresetOff() {
        this.markStaleAndClear("preset-off");
    }

    void markPresetClear() {
        this.markStaleAndClear("preset-clear");
    }

    void clear() {
        this.markStaleAndClear("clear");
    }

    private synchronized boolean shouldScheduleStart() {
        if (!this.startRequested || this.ownerReady || this.startQueuedOnRenderThread) {
            return false;
        }
        this.startQueuedOnRenderThread = true;
        return true;
    }

    private void startOnRenderThread() {
        synchronized (this) {
            this.startQueuedOnRenderThread = false;
        }
        if (!RenderSystem.isOnRenderThread()) {
            this.recordFailure("start-not-on-render-thread");
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            this.recordFailure("no-active-client-world");
            return;
        }
        if (!this.instance.ensureActiveWorldSkeletonForCurrentWorldIfAllowed()) {
            this.recordFailure("active-world-engine-missing");
            return;
        }
        Optional<WorldEngine> engine = this.instance.getCurrentEngineOptional();
        if (engine.isEmpty()) {
            this.recordFailure("active-world-engine-not-live");
            return;
        }

        WorldEngine targetWorld = engine.get();
        Mapper mapper = targetWorld.getMapper();
        this.queueExistingBiomes(mapper);
        mapper.setBiomeCallback(this::queueBiome);
        synchronized (this) {
            this.world = targetWorld;
            this.ownerReady = true;
            this.mapperBiomeCallbackAttached = true;
            this.existingBiomeEntriesQueued = true;
            this.startRequested = false;
            this.stale = false;
            this.requiresRebuild = false;
            this.startRuns++;
            this.lifecycleState = "OWNER_STARTED_MODEL_FACTORY_PENDING";
            this.lastLifecycleEvent = "start-on-render-thread";
            this.lastFailureReason = "model-factory-port-pending";
        }
    }

    private void queueExistingBiomes(Mapper mapper) {
        Arrays.stream(mapper.getBiomeEntries()).forEach(this::queueBiome);
    }

    private void queueBiome(Mapper.BiomeEntry biomeEntry) {
        if (biomeEntry != null) {
            this.biomeQueue.add(biomeEntry);
        }
    }

    private void markStaleAndClear(String event) {
        WorldEngine callbackWorld;
        synchronized (this) {
            this.clearRuns++;
            this.startRequested = false;
            this.startQueuedOnRenderThread = false;
            this.ownerReady = false;
            this.mapperBiomeCallbackAttached = false;
            this.existingBiomeEntriesQueued = false;
            this.stale = true;
            this.requiresRebuild = true;
            this.lifecycleState = "STALE";
            this.lastLifecycleEvent = safeReason(event);
            this.lastFailureReason = "none";
            this.blockBakeQueue.clear();
            this.biomeQueue.clear();
            this.seenBlockBakeRequests.clear();
            callbackWorld = this.world;
            this.world = null;
        }
        if (callbackWorld != null) {
            this.runOnRenderThread(() -> {
                callbackWorld.getMapper().setBiomeCallback(null);
                callbackWorld.getMapper().setStateCallback(null);
            });
        }
    }

    private void runOnRenderThread(Runnable task) {
        if (RenderSystem.isOnRenderThread()) {
            task.run();
        } else {
            RenderSystem.recordRenderCall(task::run);
        }
    }

    private synchronized void recordFailure(String reason) {
        this.startRequested = false;
        this.startQueuedOnRenderThread = false;
        this.ownerReady = false;
        this.stale = true;
        this.requiresRebuild = true;
        this.lifecycleState = "FAILED_SAFE";
        this.lastLifecycleEvent = "failure";
        this.lastFailureReason = reason == null || reason.isBlank() ? "unspecified" : reason;
    }

    private static String safeReason(String reason) {
        return reason == null || reason.isBlank() ? "unspecified" : reason.replace(' ', '-');
    }
}
