package me.cortex.voxy.forge;

import com.mojang.blaze3d.systems.RenderSystem;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import me.cortex.voxy.common.world.WorldEngine;
import me.cortex.voxy.common.world.other.Mapper;
import net.minecraft.client.Minecraft;

import java.util.Arrays;
import java.util.Optional;
import java.util.concurrent.locks.LockSupport;

final class ForgeOriginalVoxyModelPipeline {
    static final String STAGE = "L0_L4_ORIGINAL_VOXY_MODEL_PIPELINE_PARITY";
    private static final int MAX_MODEL_UPLOADS_PER_TICK = 2;

    private final ForgeVoxyInstance instance;
    private final IntOpenHashSet seenBlockBakeRequests = new IntOpenHashSet(6000);
    private WorldEngine world;
    private ForgeOriginalVoxyModelFactory modelFactory;
    private ForgeOriginalVoxyRenderGenerationService renderGenerationService;
    private Thread processingThread;
    private volatile boolean processingThreadRunning;
    private volatile Throwable processingThreadException;
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
    private long workerUnparkRuns;
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
        if (this.processingThreadException != null) {
            Throwable exception = this.processingThreadException;
            this.processingThreadException = null;
            this.recordFailure("model-factory-processor-" + exception.getClass().getSimpleName() + ":" + exception.getMessage());
            return;
        }
        ForgeOriginalVoxyModelFactory factory;
        synchronized (this) {
            if (this.ownerReady && !this.stale) {
                this.tickRuns++;
                this.lifecycleState = "RUNNING";
                this.lastLifecycleEvent = "tick";
            }
            factory = this.modelFactory;
        }
        if (factory != null && factory.hasPendingUploads()) {
            this.runOnRenderThread(() -> this.processFactoryUploads(factory));
        }
        if (ForgeOriginalVoxyUploadStream.isReady()) {
            this.runOnRenderThread(() -> {
                if (ForgeOriginalVoxyUploadStream.isReady()) {
                    ForgeOriginalVoxyUploadStream.instance().tick();
                }
            });
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
            this.modelFactory.addEntry(blockStateId);
            this.unparkProcessingThread();
        }
        this.lastFailureReason = this.modelFactory.createStatusSnapshot().lastFailureReason();
        this.lastLifecycleEvent = "request-block-bake-queued";
        return this.createStatusSnapshot();
    }

    synchronized ForgeOriginalVoxyModelPipelineStats enqueueRenderGenerationTask(long sectionKey) {
        if (!this.ownerReady || this.stale || this.renderGenerationService == null) {
            this.lastFailureReason = "original-render-generation-service-not-ready";
            return this.createStatusSnapshot();
        }
        this.renderGenerationService.enqueueTask(sectionKey);
        this.lastLifecycleEvent = "render-generation-task-queued";
        this.lastFailureReason = "none";
        return this.createStatusSnapshot();
    }

    synchronized ForgeOriginalVoxyModelPipelineStats createStatusSnapshot() {
        Mapper mapper = this.world == null ? null : this.world.getMapper();
        int mapperBlockStateCount = mapper == null ? 0 : mapper.getBlockStateCount();
        int mapperBiomeCount = mapper == null ? 0 : mapper.getBiomeEntries().length;
        ForgeOriginalVoxyModelFactoryStats factory = this.modelFactory == null
                ? ForgeOriginalVoxyModelFactoryStats.unavailable("model-factory-not-started")
                : this.modelFactory.createStatusSnapshot();
        ForgeOriginalVoxyRenderGenerationStats renderGeneration = this.renderGenerationService == null
                ? ForgeOriginalVoxyRenderGenerationStats.unavailable("render-generation-not-started")
                : this.renderGenerationService.createStatusSnapshot();
        boolean workerReady = this.processingThreadRunning
                && this.processingThread != null
                && this.processingThread.isAlive()
                && this.processingThreadException == null;
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
                factory.factoryReady(),
                factory.originalModelFactoryUsed(),
                factory.originalSoftwareModelTextureBakeryUsed(),
                factory.originalModelStoreUsed(),
                this.mapperBiomeCallbackAttached && this.ownerReady && !this.stale,
                false,
                this.existingBiomeEntriesQueued && this.ownerReady && !this.stale,
                renderGeneration.originalRenderDataFactoryUsed(),
                false,
                false,
                false,
                false,
                false,
                this.stale,
                this.requiresRebuild,
                mapperBlockStateCount,
                mapperBiomeCount,
                factory.queuedBlockBakeCount() + factory.inFlightBlockBakeCount(),
                factory.completedModelCount(),
                workerReady,
                factory.renderThreadUploadPathUsed(),
                factory.idMappingsReady(),
                factory.metadataCacheReady(),
                factory.fluidStateLutReady(),
                factory.modelTextureDedupeReady(),
                factory.uploadResultQueueReady(),
                factory.mipChainAtlasUploadReady(),
                factory.textureUtilsOriginalHelpersPorted(),
                factory.textureUtilsByteForByteAuditReady(),
                factory.biomeColourLutUploadReady(),
                factory.uploadStreamPersistentMappedReady(),
                renderGeneration.originalModelMissRequestRequeueUsed(),
                renderGeneration.renderGenerationServiceReady(),
                renderGeneration.originalRenderDataFactoryUsed(),
                renderGeneration.originalBuildTaskPriorityUsed(),
                renderGeneration.originalHoldingSectionPolicyUsed(),
                renderGeneration.originalServiceManagerParityReady(),
                renderGeneration.taskQueueCount(),
                renderGeneration.taskMapCount(),
                renderGeneration.holdingSectionCount(),
                renderGeneration.enqueuedTaskCount(),
                renderGeneration.processedTaskCount(),
                renderGeneration.completedMeshCount(),
                renderGeneration.emptyMeshCount(),
                renderGeneration.requeueCount(),
                renderGeneration.replacedTaskCount(),
                renderGeneration.modelMissRequestCount(),
                renderGeneration.innerModelRequestScanCount(),
                renderGeneration.outerModelRequestScanCount(),
                renderGeneration.failedMeshCount(),
                renderGeneration.lastTaskPosition(),
                renderGeneration.lastLifecycleEvent(),
                renderGeneration.lastFailureReason(),
                factory.queuedBlockBakeCount(),
                factory.queuedBiomeCount(),
                factory.queuedUploadResultCount(),
                factory.biomeUploadResultCount(),
                factory.inFlightBlockBakeCount(),
                factory.completedModelCount(),
                factory.uploadedModelRecordCount(),
                factory.uploadedModelColourCount(),
                factory.uploadedAtlasFaceCount(),
                factory.dedupeHitCount(),
                factory.dedupeMissCount(),
                factory.failedBakeCount(),
                factory.modelTexture2idSize(),
                factory.nextModelId(),
                factory.lastRequestedBlockStateId(),
                factory.lastUploadedBlockStateId(),
                factory.lastUploadedModelId(),
                factory.lastDuplicateBlockStateId(),
                factory.lastDuplicateModelId(),
                factory.queuedBlockBakeCount() == 0 && factory.inFlightBlockBakeCount() == 0,
                this.lifecycleState,
                this.lastLifecycleEvent,
                "none".equals(this.lastFailureReason) ? factory.lastFailureReason() : this.lastFailureReason
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
        this.stopProcessingThread();
        ForgeOriginalVoxyModelFactory factory = new ForgeOriginalVoxyModelFactory(mapper, this.instance.getFormalModelStore());
        factory.prepareOnRenderThread(minecraft);
        ForgeOriginalVoxyRenderGenerationService renderGeneration =
                new ForgeOriginalVoxyRenderGenerationService(targetWorld, this, factory, false);
        synchronized (this) {
            this.world = targetWorld;
            this.modelFactory = factory;
            this.renderGenerationService = renderGeneration;
            this.ownerReady = true;
            this.mapperBiomeCallbackAttached = true;
            this.existingBiomeEntriesQueued = true;
            this.startRequested = false;
            this.stale = false;
            this.requiresRebuild = false;
            this.startRuns++;
            this.lifecycleState = "OWNER_STARTED_MODEL_FACTORY_READY";
            this.lastLifecycleEvent = "start-on-render-thread";
            this.lastFailureReason = "none";
        }
        mapper.setBiomeCallback(this::queueBiome);
        Arrays.stream(mapper.getBiomeEntries()).forEach(factory::addBiome);
        this.startProcessingThread(factory);
        this.unparkProcessingThread();
    }

    private void queueBiome(Mapper.BiomeEntry biomeEntry) {
        ForgeOriginalVoxyModelFactory factory;
        synchronized (this) {
            factory = this.modelFactory;
        }
        if (biomeEntry != null && factory != null) {
            factory.addBiome(biomeEntry);
            this.unparkProcessingThread();
        }
    }

    private void markStaleAndClear(String event) {
        WorldEngine callbackWorld;
        ForgeOriginalVoxyModelFactory factory;
        ForgeOriginalVoxyRenderGenerationService renderGeneration;
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
            this.seenBlockBakeRequests.clear();
            callbackWorld = this.world;
            factory = this.modelFactory;
            renderGeneration = this.renderGenerationService;
            this.world = null;
            this.modelFactory = null;
            this.renderGenerationService = null;
        }
        this.stopProcessingThread();
        if (renderGeneration != null) {
            renderGeneration.shutdown();
        }
        if (factory != null) {
            factory.shutdown();
        }
        if (callbackWorld != null) {
            this.runOnRenderThread(() -> {
                callbackWorld.getMapper().setBiomeCallback(null);
                callbackWorld.getMapper().setStateCallback(null);
            });
        }
    }

    private void startProcessingThread(ForgeOriginalVoxyModelFactory factory) {
        this.processingThreadException = null;
        this.processingThreadRunning = true;
        Thread thread = new Thread(() -> {
            while (this.processingThreadRunning) {
                while (this.processingThreadRunning && factory.processAllThings()) {
                    // Drain model and biome work like original ModelBakerySubsystem.
                }
                if (this.processingThreadRunning) {
                    LockSupport.park();
                }
            }
        }, "Model factory processor");
        thread.setUncaughtExceptionHandler((t, e) -> {
            this.processingThreadRunning = false;
            this.processingThreadException = e == null ? new RuntimeException("unhandled-model-factory-exception") : e;
        });
        thread.start();
        synchronized (this) {
            this.processingThread = thread;
        }
    }

    private void stopProcessingThread() {
        Thread thread;
        synchronized (this) {
            thread = this.processingThread;
            this.processingThread = null;
            this.processingThreadRunning = false;
        }
        if (thread != null) {
            LockSupport.unpark(thread);
            try {
                thread.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                this.recordFailure("model-factory-processor-join-interrupted");
            }
        }
    }

    private void unparkProcessingThread() {
        Thread thread = this.processingThread;
        if (thread != null) {
            this.workerUnparkRuns++;
            LockSupport.unpark(thread);
        }
    }

    private void runOnRenderThread(Runnable task) {
        if (RenderSystem.isOnRenderThread()) {
            task.run();
        } else {
            RenderSystem.recordRenderCall(task::run);
        }
    }

    private void processFactoryUploads(ForgeOriginalVoxyModelFactory factory) {
        if (!RenderSystem.isOnRenderThread()) {
            this.recordFailure("model-factory-upload-not-render-thread");
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            return;
        }
        int processed = factory.processUploadsOnRenderThread(MAX_MODEL_UPLOADS_PER_TICK);
        if (processed > 0) {
            synchronized (this) {
                this.uploadTickRuns++;
                this.lifecycleState = "RUNNING_MODEL_UPLOADS";
                this.lastLifecycleEvent = "model-factory-upload-tick";
                this.lastFailureReason = factory.createStatusSnapshot().lastFailureReason();
            }
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
