package me.cortex.voxy.forge;

import com.mojang.blaze3d.systems.RenderSystem;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import me.cortex.voxy.common.Logger;
import me.cortex.voxy.common.thread.MultiThreadPrioritySemaphore;
import me.cortex.voxy.common.thread.UnifiedServiceThreadPool;
import me.cortex.voxy.common.world.WorldEngine;
import me.cortex.voxy.common.world.other.Mapper;
import me.cortex.voxy.config.ForgeVoxyConfig;
import me.jellysquid.mods.sodium.client.render.chunk.ChunkRenderMatrices;
import me.jellysquid.mods.sodium.client.render.viewport.CameraTransform;
import net.minecraft.client.Minecraft;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;

import java.util.Arrays;
import java.util.Optional;
import java.util.concurrent.locks.LockSupport;

import static org.lwjgl.opengl.GL11C.GL_VIEWPORT;
import static org.lwjgl.opengl.GL11C.glGetInteger;
import static org.lwjgl.opengl.GL11C.glGetIntegerv;
import static org.lwjgl.opengl.GL30C.GL_DRAW_FRAMEBUFFER_BINDING;
import static org.lwjgl.opengl.GL42C.GL_FRAMEBUFFER_BARRIER_BIT;
import static org.lwjgl.opengl.GL42C.GL_PIXEL_BUFFER_BARRIER_BIT;
import static org.lwjgl.opengl.GL42C.glMemoryBarrier;

public final class ForgeOriginalVoxyModelPipeline {
    static final String STAGE = "L0_L4_ORIGINAL_VOXY_MODEL_PIPELINE_PARITY";
    private static final int MAX_MODEL_UPLOADS_PER_TICK = 2;
    private static final int ORIGINAL_GEOMETRY_MAX_SECTION_COUNT = 1 << 20;

    private final ForgeVoxyInstance instance;
    private final IntOpenHashSet seenBlockBakeRequests = new IntOpenHashSet(6000);
    private WorldEngine world;
    private ForgeOriginalVoxyModelStore modelStore;
    private ForgeOriginalVoxyModelFactory modelFactory;
    private ForgeOriginalVoxyRenderGenerationService renderGenerationService;
    private final UnifiedServiceThreadPool serviceThreadPool = new UnifiedServiceThreadPool();
    private ForgeOriginalVoxyBasicAsyncGeometryManager asyncGeometryManager;
    private ForgeOriginalVoxyBasicSectionGeometryData basicSectionGeometryData;
    private ForgeOriginalVoxyAsyncNodeGeometrySync asyncNodeGeometrySync;
    private ForgeOriginalVoxyNodeCleaner nodeCleaner;
    private ForgeOriginalVoxyRenderDistanceTracker renderDistanceTracker;
    private ForgeOriginalVoxyHierarchicalOcclusionTraverser hierarchicalOcclusionTraverser;
    private ForgeOriginalVoxyMdicSectionRenderer mdicSectionRenderer;
    private ForgeOriginalVoxyRenderPipeline renderPipeline;
    private ForgeOriginalVoxyViewportSelector viewportSelector;
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
    private int originalServiceThreadTargetCount;
    private int originalServiceThreadDedicatedCount;
    private int originalEmbeddiumBuilderThreadCount;
    private int originalEmbeddiumBuilderSemaphoreBlockCount;
    private boolean originalServiceThreadConfigOwnerReady;
    private boolean originalEmbeddiumBuilderThreadSharingReady;
    private boolean originalEmbeddiumBuilderThreadSharingEnabled;
    private String originalServiceThreadPolicySource = "uninitialized";
    private String originalServiceThreadPolicyFailureReason = "none";
    private String lifecycleState = "UNINITIALIZED";
    private String lastLifecycleEvent = "initialized";
    private String lastFailureReason = "none";
    private boolean renderEmbeddiumCutoutActive;

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
        if (!this.ownerReady || this.stale || this.asyncNodeGeometrySync == null) {
            this.lastFailureReason = "original-async-node-manager-not-ready";
            return this.createStatusSnapshot();
        }
        this.asyncNodeGeometrySync.addTopLevel(sectionKey);
        this.lastLifecycleEvent = "top-level-node-request-queued";
        this.lastFailureReason = "none";
        return this.createStatusSnapshot();
    }

    synchronized ForgeOriginalVoxyMdicCommandGenerationStats createMdicCommandGenerationStatusSnapshot() {
        return this.mdicSectionRenderer == null
                ? ForgeOriginalVoxyMdicCommandGenerationStats.unavailable("original-mdic-command-generator-not-started")
                : this.mdicSectionRenderer.createStatusSnapshot();
    }

    synchronized ForgeOriginalVoxyMdicCommandGenerationStats requestMdicCommandGenerationReadbackAudit() {
        if (this.mdicSectionRenderer == null || !this.ownerReady || this.stale) {
            return ForgeOriginalVoxyMdicCommandGenerationStats.unavailable("original-mdic-command-generator-not-ready");
        }
        if (this.hierarchicalOcclusionTraverser != null) {
            this.hierarchicalOcclusionTraverser.requestReadbackAudit();
        }
        this.mdicSectionRenderer.requestReadbackAudit();
        this.lastLifecycleEvent = "original-mdic-command-generation-readback-audit-requested";
        return this.mdicSectionRenderer.createStatusSnapshot();
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
        ForgeOriginalVoxyBasicAsyncGeometryStats geometry = this.asyncGeometryManager == null
                ? ForgeOriginalVoxyBasicAsyncGeometryStats.unavailable("basic-async-geometry-not-started")
                : this.asyncGeometryManager.createStatusSnapshot(this.renderGenerationService != null);
        ForgeOriginalVoxyBasicSectionGeometryDataStats geometryData = this.basicSectionGeometryData == null
                ? ForgeOriginalVoxyBasicSectionGeometryDataStats.unavailable("basic-section-geometry-data-not-started")
                : this.basicSectionGeometryData.createStatusSnapshot();
        ForgeOriginalVoxyAsyncNodeGeometrySyncStats nodeSync = this.asyncNodeGeometrySync == null
                ? ForgeOriginalVoxyAsyncNodeGeometrySyncStats.unavailable("async-node-geometry-sync-not-started")
                : this.asyncNodeGeometrySync.createStatusSnapshot();
        ForgeOriginalVoxyHierarchicalOcclusionTraverserStats hoc = this.hierarchicalOcclusionTraverser == null
                ? ForgeOriginalVoxyHierarchicalOcclusionTraverserStats.unavailable("hierarchical-occlusion-traverser-not-started")
                : this.hierarchicalOcclusionTraverser.createStatusSnapshot();
        boolean workerReady = this.processingThreadRunning
                && this.processingThread != null
                && this.processingThread.isAlive()
                && this.processingThreadException == null;
        boolean viewportSelectorReady = this.viewportSelector != null && this.viewportSelector.ready();
        boolean viewportSelectorDefaultReady = this.viewportSelector != null && this.viewportSelector.defaultViewportReady();
        int viewportSelectorExtraViewportCount = this.viewportSelector == null ? 0 : this.viewportSelector.extraViewportCount();
        String viewportSelectorLastSelectedKey = this.viewportSelector == null ? "none" : this.viewportSelector.lastSelectedKey();
        boolean mdicViewportOwnerReady = viewportSelectorReady;
        boolean hizOwnerReady = this.viewportSelector != null && this.viewportSelector.hizOwnerReady();
        boolean hocOwnerReady = hoc.originalHierarchicalOcclusionTraverserReady();
        boolean hizTraversalExecutableReady = this.viewportSelector != null && this.viewportSelector.selectedHizTraversalReady();
        boolean hocExecutableReady = hocOwnerReady && mdicViewportOwnerReady && hizTraversalExecutableReady;
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
                factory.originalModelStoreReadbackAuditReady(),
                factory.modelDataReadbackOk(),
                factory.modelColourReadbackOk(),
                factory.atlasMipChainReadbackOk(),
                factory.modelStoreReadbackAuditRuns(),
                factory.modelStoreReadbackAuditFailures(),
                factory.lastAuditedModelId(),
                factory.customBlockStateIdMappingReady(),
                factory.customBlockStateIdMappingPresent(),
                factory.customBlockStateIdMappingSource(),
                factory.lastModelStoreReadbackAuditFailureReason(),
                renderGeneration.originalModelMissRequestRequeueUsed(),
                renderGeneration.renderGenerationServiceReady(),
                renderGeneration.originalRenderDataFactoryUsed(),
                geometry.originalBasicAsyncGeometryManagerUsed(),
                geometryData.originalBasicSectionGeometryDataReady(),
                geometry.originalNodeManagerParityReady(),
                nodeSync.originalGeometryCacheReady(),
                this.renderDistanceTracker != null && this.ownerReady && !this.stale,
                viewportSelectorReady && this.ownerReady && !this.stale,
                viewportSelectorDefaultReady && this.ownerReady && !this.stale,
                viewportSelectorExtraViewportCount,
                viewportSelectorLastSelectedKey,
                hocExecutableReady && this.ownerReady && !this.stale,
                hocOwnerReady && this.ownerReady && !this.stale,
                mdicViewportOwnerReady && this.ownerReady && !this.stale,
                hizOwnerReady && this.ownerReady && !this.stale,
                hizTraversalExecutableReady && this.ownerReady && !this.stale,
                hoc.topNodeCount(),
                hoc.traversalRunCount(),
                hoc.requestBatchForwardCount(),
                hoc.lastLifecycleEvent(),
                hoc.lastFailureReason(),
                geometry.renderGenerationResultConsumerAttached(),
                renderGeneration.originalBuildTaskPriorityUsed(),
                renderGeneration.originalHoldingSectionPolicyUsed(),
                renderGeneration.originalServiceManagerParityReady(),
                this.originalServiceThreadConfigOwnerReady,
                this.originalEmbeddiumBuilderThreadSharingReady,
                this.originalEmbeddiumBuilderThreadSharingEnabled,
                this.originalServiceThreadTargetCount,
                this.originalServiceThreadDedicatedCount,
                this.originalEmbeddiumBuilderThreadCount,
                this.originalEmbeddiumBuilderSemaphoreBlockCount,
                this.originalServiceThreadPolicySource,
                this.originalServiceThreadPolicyFailureReason,
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
                geometry.sectionCount(),
                geometry.maxSectionCount(),
                geometry.geometryUsedBytes(),
                geometry.geometryCapacityBytes(),
                geometry.arenaSizeItems(),
                geometry.arenaLimitItems(),
                geometry.arenaFreeBlocks(),
                geometry.pendingHeapUploads(),
                geometry.pendingHeapUploadBytes(),
                geometry.pendingHeapRemovals(),
                geometry.pendingMetadataUpdates(),
                geometry.acceptedBuiltSectionCount(),
                geometry.replacedBuiltSectionCount(),
                geometry.removedBuiltSectionCount(),
                geometry.emptyBuiltSectionCount(),
                geometry.drainedUploadCount(),
                geometry.drainedUploadBytes(),
                geometry.drainedRemoveCount(),
                geometry.drainedMetadataUpdateCount(),
                geometry.lastSectionId(),
                geometry.lastSectionPosition(),
                geometry.lastLifecycleEvent(),
                geometry.lastFailureReason(),
                geometryData.renderThreadGeometryDataStoreReady(),
                geometryData.originalGeometryCapacityPolicyUsed(),
                geometryData.metadataBufferReady(),
                geometryData.geometryBufferReady(),
                geometryData.externalGeometryBuffer(),
                geometryData.sparseGeometryBuffer(),
                geometryData.sparseBufferSupported(),
                geometryData.nvidiaWindowsSparseWorkaroundUsed(),
                geometryData.currentSectionCount(),
                geometryData.metadataBufferId(),
                geometryData.geometryBufferId(),
                geometryData.metadataCapacityBytes(),
                geometryData.sparseCommitmentBytes(),
                geometryData.generation(),
                geometryData.buildCount(),
                geometryData.freeCount(),
                geometryData.lastGlError(),
                geometryData.lastLifecycleEvent(),
                geometryData.lastFailureReason(),
                nodeSync.originalAsyncNodeManagerSyncShapeReady(),
                nodeSync.originalNodeManagerParityReady(),
                nodeSync.geometryResultQueueReady(),
                nodeSync.renderThreadTickReady(),
                nodeSync.multiMemcpyProgramReady(),
                nodeSync.scatterProgramReady(),
                nodeSync.syncResultPending(),
                nodeSync.directGeneratedSectionBridgeRemoved(),
                nodeSync.queuedGeometryResults(),
                nodeSync.trackedSectionIdCount(),
                nodeSync.submittedGeometryResultCount(),
                nodeSync.processedGeometryResultCount(),
                nodeSync.publishedSyncResultCount(),
                nodeSync.renderThreadTickCount(),
                nodeSync.geometryUploadCopyDispatchCount(),
                nodeSync.metadataScatterDispatchCount(),
                nodeSync.uploadedGeometryCopyCount(),
                nodeSync.uploadedGeometryBytes(),
                nodeSync.metadataScatterWriteCount(),
                nodeSync.currentMaxNodeId(),
                nodeSync.usedGeometryBytes(),
                nodeSync.lastLifecycleEvent(),
                nodeSync.lastFailureReason(),
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
        if (!this.instance.ensureOriginalVoxyActiveWorldForCurrentWorld()) {
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
        ForgeOriginalVoxyModelStore store = new ForgeOriginalVoxyModelStore();
        String storeError = store.build(minecraft);
        if (!"none".equals(storeError)) {
            store.free();
            this.recordFailure(storeError);
            return;
        }
        ForgeOriginalVoxyModelFactory factory = new ForgeOriginalVoxyModelFactory(mapper, store);
        factory.prepareOnRenderThread(minecraft);
        ForgeOculusWorldRenderingSettingsBridge.Result blockStateIds = ForgeOculusWorldRenderingSettingsBridge.getBlockStateIds();
        if (!blockStateIds.ready()) {
            factory.shutdown();
            store.free();
            this.recordFailure(blockStateIds.failureReason());
            return;
        }
        factory.setCustomBlockStateMapping(blockStateIds.blockStateIds(), blockStateIds.source());
        this.updateDedicatedThreads();
        if (!this.originalServiceThreadConfigOwnerReady) {
            factory.shutdown();
            store.free();
            this.recordFailure("original-service-thread-config-not-ready:" + this.originalServiceThreadPolicyFailureReason);
            return;
        }
        ForgeOriginalVoxyRenderGenerationService renderGeneration =
                new ForgeOriginalVoxyRenderGenerationService(
                        targetWorld,
                        this,
                        factory,
                        this.serviceThreadPool.serviceManager,
                        false);
        ForgeOriginalVoxyBasicSectionGeometryData geometryData = new ForgeOriginalVoxyBasicSectionGeometryData(
                ORIGINAL_GEOMETRY_MAX_SECTION_COUNT);
        String geometryDataError = geometryData.buildOnRenderThread();
        if (!"none".equals(geometryDataError)) {
            renderGeneration.shutdown();
            factory.shutdown();
            store.free();
            this.recordFailure(geometryDataError);
            return;
        }
        ForgeOriginalVoxyBasicAsyncGeometryManager geometryManager = new ForgeOriginalVoxyBasicAsyncGeometryManager(
                ORIGINAL_GEOMETRY_MAX_SECTION_COUNT,
                geometryData.geometryCapacityBytes());
        ForgeOriginalVoxyAsyncNodeGeometrySync geometrySync =
                new ForgeOriginalVoxyAsyncNodeGeometrySync(geometryManager, geometryData, renderGeneration);
        ForgeOriginalVoxyNodeCleaner cleaner = new ForgeOriginalVoxyNodeCleaner(geometrySync.maxNodeCount());
        try {
            cleaner.buildOnRenderThread();
        } catch (RuntimeException e) {
            geometryData.freeOnRenderThread();
            renderGeneration.shutdown();
            factory.shutdown();
            store.free();
            this.recordFailure("original-node-cleaner-" + e.getClass().getSimpleName() + ":" + e.getMessage());
            return;
        }
        ForgeOriginalVoxyRenderProperties renderProperties = ForgeOriginalVoxyRenderProperties.getRenderProperties();
        ForgeOriginalVoxyRenderPipeline renderPipeline;
        try {
            renderPipeline = new ForgeOriginalVoxyRenderPipeline(renderProperties);
        } catch (RuntimeException e) {
            geometrySync.stopOnRenderThread();
            cleaner.freeOnRenderThread();
            geometryData.freeOnRenderThread();
            renderGeneration.shutdown();
            factory.shutdown();
            store.free();
            this.recordFailure("original-pipeline-depth-stage-" + e.getClass().getSimpleName() + ":" + e.getMessage());
            return;
        }
        ForgeOriginalVoxyHierarchicalOcclusionTraverser hierarchicalOcclusionTraverser =
                new ForgeOriginalVoxyHierarchicalOcclusionTraverser(geometrySync, cleaner, renderGeneration);
        String traversalError = hierarchicalOcclusionTraverser.buildOnRenderThread(renderProperties);
        if (!"none".equals(traversalError)) {
            renderPipeline.freeOnRenderThread();
            hierarchicalOcclusionTraverser.freeOnRenderThread();
            geometrySync.stopOnRenderThread();
            cleaner.freeOnRenderThread();
            geometryData.freeOnRenderThread();
            renderGeneration.shutdown();
            factory.shutdown();
            store.free();
            this.recordFailure(traversalError);
            return;
        }
        ForgeOriginalVoxyMdicSectionRenderer mdicSectionRenderer = new ForgeOriginalVoxyMdicSectionRenderer();
        String cmdgenError = mdicSectionRenderer.buildOnRenderThread(renderPipeline);
        if (!"none".equals(cmdgenError)) {
            mdicSectionRenderer.freeOnRenderThread();
            renderPipeline.freeOnRenderThread();
            hierarchicalOcclusionTraverser.freeOnRenderThread();
            geometrySync.stopOnRenderThread();
            cleaner.freeOnRenderThread();
            geometryData.freeOnRenderThread();
            renderGeneration.shutdown();
            factory.shutdown();
            store.free();
            this.recordFailure(cmdgenError);
            return;
        }
        ForgeOriginalVoxyViewportSelector viewportSelector = new ForgeOriginalVoxyViewportSelector(
                () -> new ForgeOriginalVoxyMdicViewport(renderProperties, ORIGINAL_GEOMETRY_MAX_SECTION_COUNT));
        int minSec = (minecraft.level.getMinBuildHeight() >> 4) >> 5;
        int maxSec = ((minecraft.level.getMaxBuildHeight() >> 4) - 1) >> 5;
        ForgeOriginalVoxyRenderDistanceTracker renderDistanceTracker = new ForgeOriginalVoxyRenderDistanceTracker(
                40,
                minSec,
                maxSec,
                geometrySync::addTopLevel,
                geometrySync::removeTopLevel);
        renderDistanceTracker.setRenderDistance((int) Math.ceil(ForgeVoxyConfig.ORIGINAL_VOXY_SECTION_RENDER_DISTANCE.get()));
        geometrySync.start();
        renderGeneration.setResultConsumer(geometrySync::submitGeometryResult);
        synchronized (this) {
            this.world = targetWorld;
            this.modelStore = store;
            this.modelFactory = factory;
            this.renderGenerationService = renderGeneration;
            this.asyncGeometryManager = geometryManager;
            this.basicSectionGeometryData = geometryData;
            this.asyncNodeGeometrySync = geometrySync;
            this.nodeCleaner = cleaner;
            this.renderDistanceTracker = renderDistanceTracker;
            this.hierarchicalOcclusionTraverser = hierarchicalOcclusionTraverser;
            this.mdicSectionRenderer = mdicSectionRenderer;
            this.renderPipeline = renderPipeline;
            this.viewportSelector = viewportSelector;
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
        targetWorld.setDirtyCallback(geometrySync::worldEvent);
        mapper.setBiomeCallback(this::queueBiome);
        Arrays.stream(mapper.getBiomeEntries()).forEach(factory::addBiome);
        this.startProcessingThread(factory);
        this.unparkProcessingThread();
    }

    public void renderEmbeddiumCutout(ChunkRenderMatrices matrices, CameraTransform camera) {
        if (!RenderSystem.isOnRenderThread()) {
            this.recordNonFatalFailure("original-hoc-embeddium-cutout-not-render-thread");
            return;
        }
        if (this.renderEmbeddiumCutoutActive) {
            this.recordNonFatalFailure("original-hoc-embeddium-cutout-reentrant");
            return;
        }
        this.renderEmbeddiumCutoutActive = true;
        boolean commandGenerationCompleted = false;
        try {
            ForgeOriginalVoxyMdicViewport viewport;
            ForgeOriginalVoxyHierarchicalOcclusionTraverser traversal;
            ForgeOriginalVoxyMdicSectionRenderer sectionRenderer;
            ForgeOriginalVoxyRenderPipeline renderPipeline;
            ForgeOriginalVoxyBasicSectionGeometryData geometryData;
            ForgeOriginalVoxyAsyncNodeGeometrySync geometrySync;
            ForgeOriginalVoxyNodeCleaner cleaner;
            ForgeOriginalVoxyViewportSelector selector;
            synchronized (this) {
                if (!this.ownerReady || this.stale) {
                    return;
                }
                selector = this.viewportSelector;
                traversal = this.hierarchicalOcclusionTraverser;
                sectionRenderer = this.mdicSectionRenderer;
                renderPipeline = this.renderPipeline;
                geometryData = this.basicSectionGeometryData;
                geometrySync = this.asyncNodeGeometrySync;
                cleaner = this.nodeCleaner;
            }
            viewport = selector == null ? null : selector.getViewport();
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.level == null || minecraft.player == null || matrices == null || camera == null) {
                return;
            }
            if (viewport == null || traversal == null || sectionRenderer == null || renderPipeline == null || geometryData == null || !traversal.ready()) {
                return;
            }
            int drawFramebuffer = glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING);
            if (drawFramebuffer == 0) {
                this.recordNonFatalFailure("original-hoc-embeddium-cutout-default-framebuffer");
                return;
            }
            int[] viewportDims = new int[4];
            glGetIntegerv(GL_VIEWPORT, viewportDims);
            int width = viewportDims[2];
            int height = viewportDims[3];
            if (width <= 0 || height <= 0) {
                this.recordNonFatalFailure("original-hoc-embeddium-cutout-invalid-viewport");
                return;
            }
            Matrix4f vanillaProjection = new Matrix4f(matrices.projection());
            Matrix4f modelView = new Matrix4f(matrices.modelView());
            Matrix4f rawMinecraftProjection = ForgeOriginalVoxyRenderStateCapture.projectionCopy();
            if (rawMinecraftProjection == null) {
                this.recordNonFatalFailure("original-hoc-raw-projection-not-captured");
                return;
            }
            Matrix4f voxyProjection = computeProjectionMat(
                    ForgeOriginalVoxyRenderProperties.getRenderProperties(),
                    vanillaProjection,
                    rawMinecraftProjection);
            viewport.setVanillaProjection(vanillaProjection)
                    .setProjection(voxyProjection)
                    .setModelView(modelView)
                    .setCamera(camera.x, camera.y, camera.z)
                    .setScreenSize(width, height)
                    .update();
            viewport.frameId++;
            int depthTexture = renderPipeline.setup(viewport, drawFramebuffer, width, height);
            viewport.buildHizFromSourceDepth(depthTexture, width, height);
            this.runOriginalInnerPrimaryWorkBeforeTraversal(geometrySync, cleaner);
            traversal.doTraversal(viewport);
            traversal.runReadbackAuditIfRequested(viewport);
            sectionRenderer.buildDrawCalls(viewport, geometryData, ForgeOriginalVoxyRenderProperties.getRenderProperties());
            commandGenerationCompleted = true;
            synchronized (this) {
                if (this.ownerReady && !this.stale) {
                    this.lifecycleState = "RUNNING_ORIGINAL_HOC_TRAVERSAL_AND_MDIC_CMDGEN";
                    this.lastLifecycleEvent = "embeddium-cutout-hiz-hoc-traversal-and-mdic-cmdgen";
                    this.lastFailureReason = "none";
                }
            }
        } catch (RuntimeException e) {
            this.recordNonFatalFailure("original-hoc-embeddium-cutout-" + e.getClass().getSimpleName() + ":" + e.getMessage());
        } finally {
            try {
                if (commandGenerationCompleted) {
                    this.runOriginalPostDynamicWorkAfterCommandGeneration(camera.x, camera.z);
                }
            } finally {
                this.renderEmbeddiumCutoutActive = false;
            }
        }
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

    public MultiThreadPrioritySemaphore.Block createEmbeddiumBuilderSemaphoreBlock() {
        this.updateDedicatedThreads();
        if (!this.originalEmbeddiumBuilderThreadSharingEnabled) {
            return null;
        }
        synchronized (this) {
            this.originalEmbeddiumBuilderSemaphoreBlockCount++;
        }
        return this.serviceThreadPool.groupSemaphore.createBlock();
    }

    private synchronized void updateDedicatedThreads() {
        ForgeOriginalVoxyServiceThreadPolicy.Selection selection = ForgeOriginalVoxyServiceThreadPolicy.select();
        this.originalServiceThreadConfigOwnerReady = selection.configOwnerReady();
        this.originalEmbeddiumBuilderThreadSharingEnabled = selection.useEmbeddiumBuilderThreads();
        this.originalEmbeddiumBuilderThreadSharingReady = selection.useEmbeddiumBuilderThreads();
        this.originalServiceThreadTargetCount = selection.targetThreadCount();
        this.originalServiceThreadDedicatedCount = selection.dedicatedThreadCount();
        this.originalEmbeddiumBuilderThreadCount = selection.embeddiumBuilderThreadCount();
        this.originalServiceThreadPolicySource = selection.source();
        this.originalServiceThreadPolicyFailureReason = selection.failureReason();
        if (!selection.configOwnerReady()) {
            return;
        }
        if (this.serviceThreadPool.setNumThreads(selection.dedicatedThreadCount())) {
            Logger.info("Dedicated voxy thread pool size: " + selection.dedicatedThreadCount());
        }
    }

    private void markStaleAndClear(String event) {
        WorldEngine callbackWorld;
        ForgeOriginalVoxyModelFactory factory;
        ForgeOriginalVoxyModelStore store;
        ForgeOriginalVoxyRenderGenerationService renderGeneration;
        ForgeOriginalVoxyBasicAsyncGeometryManager geometryManager;
        ForgeOriginalVoxyBasicSectionGeometryData geometryData;
        ForgeOriginalVoxyAsyncNodeGeometrySync geometrySync;
        ForgeOriginalVoxyNodeCleaner cleaner;
        ForgeOriginalVoxyRenderDistanceTracker tracker;
        ForgeOriginalVoxyHierarchicalOcclusionTraverser hierarchicalOcclusionTraverser;
        ForgeOriginalVoxyMdicSectionRenderer mdicSectionRenderer;
        ForgeOriginalVoxyRenderPipeline renderPipeline;
        ForgeOriginalVoxyViewportSelector viewportSelector;
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
            store = this.modelStore;
            factory = this.modelFactory;
            renderGeneration = this.renderGenerationService;
            geometryManager = this.asyncGeometryManager;
            geometryData = this.basicSectionGeometryData;
            geometrySync = this.asyncNodeGeometrySync;
            cleaner = this.nodeCleaner;
            tracker = this.renderDistanceTracker;
            hierarchicalOcclusionTraverser = this.hierarchicalOcclusionTraverser;
            mdicSectionRenderer = this.mdicSectionRenderer;
            renderPipeline = this.renderPipeline;
            viewportSelector = this.viewportSelector;
            this.world = null;
            this.modelStore = null;
            this.modelFactory = null;
            this.renderGenerationService = null;
            this.asyncGeometryManager = null;
            this.basicSectionGeometryData = null;
            this.asyncNodeGeometrySync = null;
            this.nodeCleaner = null;
            this.renderDistanceTracker = null;
            this.hierarchicalOcclusionTraverser = null;
            this.mdicSectionRenderer = null;
            this.renderPipeline = null;
            this.viewportSelector = null;
        }
        if (callbackWorld != null) {
            callbackWorld.setDirtyCallback(null);
            callbackWorld.getMapper().setBiomeCallback(null);
            callbackWorld.getMapper().setStateCallback(null);
        }
        this.stopProcessingThread();
        this.runOnRenderThread(() -> {
            if (geometrySync != null) {
                geometrySync.stopOnRenderThread();
            }
            if (mdicSectionRenderer != null) {
                mdicSectionRenderer.freeOnRenderThread();
            }
            if (renderPipeline != null) {
                renderPipeline.freeOnRenderThread();
            }
            if (hierarchicalOcclusionTraverser != null) {
                hierarchicalOcclusionTraverser.freeOnRenderThread();
            }
            if (viewportSelector != null) {
                viewportSelector.free();
            }
            if (renderGeneration != null) {
                renderGeneration.shutdown();
            }
            if (factory != null) {
                factory.shutdown();
            }
            if (cleaner != null) {
                cleaner.freeOnRenderThread();
            }
            if (geometryManager != null) {
                geometryManager.clear();
            }
            if (geometryData != null) {
                geometryData.freeOnRenderThread();
            }
            if (store != null) {
                store.free();
            }
        });
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

    private void runOriginalInnerPrimaryWorkBeforeTraversal(
            ForgeOriginalVoxyAsyncNodeGeometrySync geometrySync,
            ForgeOriginalVoxyNodeCleaner cleaner) {
        if (ForgeOriginalVoxyDownloadStream.isReady()) {
            ForgeOriginalVoxyDownloadStream.instance().tick();
        }
        if (geometrySync != null) {
            geometrySync.tickOnRenderThread(cleaner);
        }
        if (geometrySync != null && cleaner != null) {
            cleaner.tickOnRenderThread(geometrySync);
        }
        glMemoryBarrier(GL_FRAMEBUFFER_BARRIER_BIT | GL_PIXEL_BUFFER_BARRIER_BIT);
    }

    private void runOriginalPostDynamicWorkAfterCommandGeneration(double cameraX, double cameraZ) {
        if (ForgeOriginalVoxyUploadStream.isReady()) {
            ForgeOriginalVoxyUploadStream.instance().tick();
        }
        this.processRenderDistanceTrackerOnRenderThread(cameraX, cameraZ);
    }

    private void processRenderDistanceTrackerOnRenderThread(double cameraX, double cameraZ) {
        if (!RenderSystem.isOnRenderThread()) {
            this.recordFailure("render-distance-tracker-not-render-thread");
            return;
        }
        ForgeOriginalVoxyRenderDistanceTracker tracker;
        synchronized (this) {
            if (!this.ownerReady || this.stale) {
                return;
            }
            tracker = this.renderDistanceTracker;
        }
        if (tracker == null) {
            return;
        }
        tracker.setCenterAndProcess(cameraX, cameraZ);
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

    private synchronized void recordNonFatalFailure(String reason) {
        this.lastLifecycleEvent = "non-fatal-render-failure";
        this.lastFailureReason = reason == null || reason.isBlank() ? "unspecified" : reason.replace(' ', '-');
    }

    private static Matrix4f computeProjectionMat(
            ForgeOriginalVoxyRenderProperties properties,
            Matrix4fc base,
            Matrix4fc rawMinecraftProjection) {
        Matrix4f extraProjection = rawMinecraftProjection.invert(new Matrix4f()).mul(base);
        float near = minecraftRenderDistance() <= 32.0F ? 8.0F : 16.0F;
        float far = 16.0F * 3000.0F;
        if (properties.isReverseZ()) {
            float tmp = near;
            near = far;
            far = tmp;
        }
        return extraProjection.mulLocal(new Matrix4f(rawMinecraftProjection)
                .m22((properties.isZero2One() ? far : far + near) / (near - far))
                .m32((properties.isZero2One() ? far : far + far) * near / (near - far)));
    }

    private static float minecraftRenderDistance() {
        return Minecraft.getInstance().options.getEffectiveRenderDistance() * 16.0F;
    }

    private static String safeReason(String reason) {
        return reason == null || reason.isBlank() ? "unspecified" : reason.replace(' ', '-');
    }
}
