package me.cortex.voxy.forge;

import com.mojang.blaze3d.systems.RenderSystem;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import me.cortex.voxy.common.Logger;
import me.cortex.voxy.common.thread.MultiThreadPrioritySemaphore;
import me.cortex.voxy.common.thread.ServiceManager;
import me.cortex.voxy.common.thread.UnifiedServiceThreadPool;
import me.cortex.voxy.common.world.WorldEngine;
import me.cortex.voxy.common.world.other.Mapper;
import me.cortex.voxy.config.ForgeVoxyConfig;
import me.jellysquid.mods.sodium.client.render.chunk.ChunkRenderMatrices;
import me.jellysquid.mods.sodium.client.render.viewport.CameraTransform;
import net.minecraft.client.Minecraft;
import net.minecraft.core.SectionPos;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.lwjgl.system.MemoryStack;

import java.util.Optional;

import static org.lwjgl.opengl.GL11C.GL_VIEWPORT;
import static org.lwjgl.opengl.GL11C.GL_BLEND;
import static org.lwjgl.opengl.GL11C.GL_COLOR_WRITEMASK;
import static org.lwjgl.opengl.GL11C.GL_CULL_FACE;
import static org.lwjgl.opengl.GL11C.GL_DEPTH_TEST;
import static org.lwjgl.opengl.GL11C.GL_DEPTH_WRITEMASK;
import static org.lwjgl.opengl.GL11C.GL_STENCIL_TEST;
import static org.lwjgl.opengl.GL11C.GL_TEXTURE_BINDING_2D;
import static org.lwjgl.opengl.GL11C.glColorMask;
import static org.lwjgl.opengl.GL11C.glDepthMask;
import static org.lwjgl.opengl.GL11C.glDisable;
import static org.lwjgl.opengl.GL11C.glEnable;
import static org.lwjgl.opengl.GL11C.glFinish;
import static org.lwjgl.opengl.GL11C.glGetBoolean;
import static org.lwjgl.opengl.GL11C.glGetBooleanv;
import static org.lwjgl.opengl.GL11C.glGetInteger;
import static org.lwjgl.opengl.GL11C.glGetIntegerv;
import static org.lwjgl.opengl.GL11C.glIsEnabled;
import static org.lwjgl.opengl.GL11C.glViewport;
import static org.lwjgl.opengl.GL11C.GL_DEPTH_FUNC;
import static org.lwjgl.opengl.GL11C.GL_FRONT_FACE;
import static org.lwjgl.opengl.GL11C.GL_POLYGON_MODE;
import static org.lwjgl.opengl.GL11C.GL_STENCIL_FAIL;
import static org.lwjgl.opengl.GL11C.GL_STENCIL_FUNC;
import static org.lwjgl.opengl.GL11C.GL_STENCIL_PASS_DEPTH_FAIL;
import static org.lwjgl.opengl.GL11C.GL_STENCIL_PASS_DEPTH_PASS;
import static org.lwjgl.opengl.GL11C.GL_STENCIL_REF;
import static org.lwjgl.opengl.GL11C.GL_STENCIL_VALUE_MASK;
import static org.lwjgl.opengl.GL11C.GL_STENCIL_WRITEMASK;
import static org.lwjgl.opengl.GL11C.glDepthFunc;
import static org.lwjgl.opengl.GL11C.glFrontFace;
import static org.lwjgl.opengl.GL11C.glPolygonMode;
import static org.lwjgl.opengl.GL11C.glStencilFunc;
import static org.lwjgl.opengl.GL11C.glStencilMask;
import static org.lwjgl.opengl.GL11C.glStencilOp;
import static org.lwjgl.opengl.GL11C.GL_FRONT_AND_BACK;
import static org.lwjgl.opengl.GL13C.GL_ACTIVE_TEXTURE;
import static org.lwjgl.opengl.GL13C.GL_TEXTURE0;
import static org.lwjgl.opengl.GL13C.glActiveTexture;
import static org.lwjgl.opengl.GL14C.GL_BLEND_DST_ALPHA;
import static org.lwjgl.opengl.GL14C.GL_BLEND_DST_RGB;
import static org.lwjgl.opengl.GL14C.GL_BLEND_SRC_ALPHA;
import static org.lwjgl.opengl.GL14C.GL_BLEND_SRC_RGB;
import static org.lwjgl.opengl.GL14C.glBlendFuncSeparate;
import static org.lwjgl.opengl.GL15C.glBindBuffer;
import static org.lwjgl.opengl.GL31C.GL_MAX_UNIFORM_BUFFER_BINDINGS;
import static org.lwjgl.opengl.GL31C.GL_UNIFORM_BUFFER;
import static org.lwjgl.opengl.GL31C.GL_UNIFORM_BUFFER_BINDING;
import static org.lwjgl.opengl.GL32C.GL_PROVOKING_VERTEX;
import static org.lwjgl.opengl.GL32C.glProvokingVertex;
import static org.lwjgl.opengl.GL40C.GL_DRAW_INDIRECT_BUFFER;
import static org.lwjgl.opengl.GL40C.GL_DRAW_INDIRECT_BUFFER_BINDING;
import static org.lwjgl.opengl.ARBIndirectParameters.GL_PARAMETER_BUFFER_ARB;
import static org.lwjgl.opengl.ARBIndirectParameters.GL_PARAMETER_BUFFER_BINDING_ARB;
import static org.lwjgl.opengl.GL20C.GL_CURRENT_PROGRAM;
import static org.lwjgl.opengl.GL20C.GL_MAX_COMBINED_TEXTURE_IMAGE_UNITS;
import static org.lwjgl.opengl.GL20C.glUseProgram;
import static org.lwjgl.opengl.GL30C.GL_DRAW_FRAMEBUFFER_BINDING;
import static org.lwjgl.opengl.GL30C.GL_READ_FRAMEBUFFER;
import static org.lwjgl.opengl.GL30C.GL_DRAW_FRAMEBUFFER;
import static org.lwjgl.opengl.GL30C.GL_READ_FRAMEBUFFER_BINDING;
import static org.lwjgl.opengl.GL30C.GL_VERTEX_ARRAY_BINDING;
import static org.lwjgl.opengl.GL30C.glBindBufferBase;
import static org.lwjgl.opengl.GL30C.glBindFramebuffer;
import static org.lwjgl.opengl.GL30C.glBindVertexArray;
import static org.lwjgl.opengl.GL30C.glGetIntegeri;
import static org.lwjgl.opengl.GL33C.GL_SAMPLER_BINDING;
import static org.lwjgl.opengl.GL33C.glBindSampler;
import static org.lwjgl.opengl.GL42C.GL_FRAMEBUFFER_BARRIER_BIT;
import static org.lwjgl.opengl.GL42C.GL_PIXEL_BUFFER_BARRIER_BIT;
import static org.lwjgl.opengl.GL42C.glMemoryBarrier;
import static org.lwjgl.opengl.GL43C.GL_SHADER_STORAGE_BUFFER;
import static org.lwjgl.opengl.GL43C.GL_SHADER_STORAGE_BUFFER_BINDING;
import static org.lwjgl.opengl.GL43C.GL_MAX_SHADER_STORAGE_BUFFER_BINDINGS;
import static org.lwjgl.opengl.GL45C.glBindTextureUnit;

public final class ForgeOriginalVoxyModelPipeline {
    static final String STAGE = "ORIGINAL_VOXY_MODEL_PIPELINE_PARITY";
    private static final int MAX_MODEL_UPLOADS_PER_TICK = 2;
    private static final int ORIGINAL_GEOMETRY_MAX_SECTION_COUNT = 1 << 20;
    private static final boolean AUDIT_OCULUS_MATRICES = Boolean.getBoolean("voxy.forge.auditOculusMatrices");
    private static final boolean AUDIT_CHUNK_BOUND = Boolean.getBoolean("voxy.forge.auditChunkBound");

    private final ForgeVoxyInstance instance;
    private final UnifiedServiceThreadPool serviceThreadPool = new UnifiedServiceThreadPool();
    //The full outer lifecycle owner (original VoxyRenderSystem port); null until started.
    private ForgeOriginalVoxyRenderSystem renderSystem;
    private final LongOpenHashSet pendingChunkBoundAdds = new LongOpenHashSet();
    private final LongOpenHashSet pendingChunkBoundRemoves = new LongOpenHashSet();
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
    private long lifecycleGeneration;
    private long startQueuedGeneration = -1L;
    private long queuedStartStaleSkipCount;
    private long queuedCleanupStaleSkipCount;
    private long queuedFactoryUploadStaleSkipCount;
    private long oculusReloadPollCount;
    private long oculusReloadEdgeCount;
    private long oculusReloadDuplicatePollCount;
    private boolean oculusReloadFlagCurrentlyObserved;
    private boolean oculusReloadDuplicatePollLogged;
    private String lastQueuedLifecycleSkipReason = "none";
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
    private long originalVisibleFrameRunCount;
    private long originalVisibleFrameSkippedCount;
    private long originalVisibleFrameFailureCount;
    private long originalVisibleFrameDrawSubmissionCount;
    private boolean originalVoxyRunPipelineOrderUsed;
    private boolean originalVisibleMdicDrawSubmissionUsed;
    private boolean originalPostFrameDynamicWorkUsed;
    private boolean originalMdicOpaqueDrawSubmitted;
    private boolean originalMdicTemporalDrawSubmitted;
    private boolean originalMdicTranslucentDrawSubmitted;
    private boolean originalPipelineFinishCalled;
    private boolean originalVisibleRendererStateRestoreUsed;
    private boolean originalChunkBoundDepthPassUsed;
    private boolean originalViewportFogParametersUsed;
    private boolean originalFinalBlitEnvironmentalFogEnabled;
    private boolean originalFinalBlitEnvironmentalFogUniformsUsed;
    private int originalVisibleFrameLastFramebuffer;
    private int originalVisibleFrameLastViewportWidth;
    private int originalVisibleFrameLastViewportHeight;
    private float originalVisibleFrameLastFogStart;
    private float originalVisibleFrameLastFogEnd;
    private boolean originalLifecycleDownloadFlushUsed;
    private boolean originalRenderStateCaptureClearUsed;
    private long originalChunkBoundDepthPassRuns;
    private int originalChunkBoundTrackedSectionCount;
    private long originalOculusViewportCapturedGeneration;
    private long originalOculusViewportAppliedGeneration;
    private long originalOculusViewportApplyCount;
    private int originalOculusMatrixAuditRuns;
    private boolean serviceThreadPoolShutdown;
    private long chunkBoundAuditLastNanos;
    private long chunkBoundAuditLastAdds;
    private long chunkBoundAuditLastRemoves;
    private long chunkBoundAuditLastClears;
    private int chunkBoundAuditFramesInWindow;

    ForgeOriginalVoxyModelPipeline(ForgeVoxyInstance instance) {
        this.instance = instance;
    }

    ServiceManager getServiceManager() {
        return this.serviceThreadPool.serviceManager;
    }

    void ensureOriginalServiceThreads() {
        synchronized (this) {
            if (this.serviceThreadPoolShutdown) {
                this.recordNonFatalFailure("original-service-thread-pool-shutdown");
                return;
            }
        }
        this.updateDedicatedThreads();
    }

    boolean shutdownForClientStop() {
        if (!RenderSystem.isOnRenderThread()) {
            this.markStaleAndClear("client-shutdown-deferred");
            return false;
        }
        this.markStaleAndClear("client-shutdown");
        return true;
    }

    void shutdownOriginalServiceThreads() {
        synchronized (this) {
            if (this.serviceThreadPoolShutdown) {
                return;
            }
            this.serviceThreadPoolShutdown = true;
        }
        this.serviceThreadPool.shutdown();
    }

    synchronized ForgeOriginalVoxyModelPipelineStats requestStart(String reason) {
        this.startRequests++;
        this.lifecycleGeneration++;
        this.startRequested = true;
        this.stale = false;
        this.requiresRebuild = false;
        this.startQueuedGeneration = -1L;
        this.lifecycleState = "START_REQUESTED";
        this.lastLifecycleEvent = safeReason(reason);
        this.lastFailureReason = "none";
        return this.createStatusSnapshot();
    }

    void clientTick() {
        this.consumeOculusWorldRenderingSettingsReload();
        if (this.shouldRequestClientWorldStart()) {
            this.requestStart("client-world-ready");
        }
        long startGeneration = this.scheduleStartOnRenderThread();
        if (startGeneration >= 0L) {
            this.runOnRenderThread(() -> this.startOnRenderThread(startGeneration));
        }
        ForgeOriginalVoxyRenderSystem renderSystem;
        synchronized (this) {
            renderSystem = this.renderSystem;
        }
        Throwable workerException = renderSystem == null ? null : renderSystem.modelService().processingThreadException();
        if (workerException != null) {
            //Original ModelBakerySubsystem.tick() rethrows worker death; the Forge adapter tears
            // the owner down and records FAILED_SAFE instead of crashing the client.
            this.markStaleAndClear("model-factory-processor-failure");
            this.recordFailure("model-factory-processor-" + workerException.getClass().getSimpleName() + ":" + workerException.getMessage());
            return;
        }
        synchronized (this) {
            if (this.ownerReady && !this.stale) {
                this.tickRuns++;
                this.lifecycleState = "RUNNING";
                this.lastLifecycleEvent = "tick";
            }
        }
        if (renderSystem != null && renderSystem.modelService().hasPendingUploads()) {
            this.runOnRenderThread(() -> this.processFactoryUploads(renderSystem));
        }
    }

    synchronized boolean shouldSuppressDeprecatedVisibleRoutes() {
        return this.startRequested || this.startQueuedOnRenderThread || (this.ownerReady && !this.stale);
    }

    synchronized void resetChunkBoundTracker() {
        this.pendingChunkBoundAdds.clear();
        this.pendingChunkBoundRemoves.clear();
        if (this.renderSystem != null) {
            this.renderSystem.chunkBoundRenderer().reset();
            this.originalChunkBoundTrackedSectionCount = 0;
        }
    }

    synchronized boolean isChunkBoundTrackerActive() {
        return this.ownerReady && !this.stale && this.renderSystem != null;
    }

    void trackChunkBoundSection(boolean wasBuilt, int x, int y, int z) {
        long pos = SectionPos.asLong(x, y, z);
        ForgeOriginalVoxyChunkBoundRenderer renderer;
        synchronized (this) {
            renderer = this.ownerReady && !this.stale && this.renderSystem != null
                    ? this.renderSystem.chunkBoundRenderer()
                    : null;
            if (renderer == null) {
                if (wasBuilt) {
                    if (!this.pendingChunkBoundAdds.remove(pos)) {
                        this.pendingChunkBoundRemoves.add(pos);
                    }
                } else if (!this.pendingChunkBoundRemoves.remove(pos)) {
                    this.pendingChunkBoundAdds.add(pos);
                }
                return;
            }
        }
        if (wasBuilt) {
            renderer.removeSection(pos);
        } else {
            renderer.addSection(pos);
        }
    }

    private void replayPendingChunkBoundSections(ForgeOriginalVoxyChunkBoundRenderer renderer) {
        this.pendingChunkBoundRemoves.forEach(renderer::removeSection);
        this.pendingChunkBoundAdds.forEach(renderer::addSection);
        this.pendingChunkBoundRemoves.clear();
        this.pendingChunkBoundAdds.clear();
        this.originalChunkBoundTrackedSectionCount = renderer.trackedSectionCount();
    }

    synchronized ForgeOriginalVoxyModelPipelineStats requestBlockBake(int blockStateId) {
        this.requestBlockBakeInternal(blockStateId);
        return this.createStatusSnapshot();
    }

    synchronized void requestBlockBakeInternal(int blockStateId) {
        ForgeOriginalVoxyRenderSystem renderSystem = this.renderSystem;
        if (!this.ownerReady || this.stale || renderSystem == null) {
            this.blockBakeRequests++;
            this.lastFailureReason = "original-model-pipeline-owner-not-ready";
            return;
        }
        if (blockStateId <= 0) {
            this.blockBakeRequests++;
            this.lastFailureReason = "invalid-block-state-id-" + blockStateId;
            return;
        }
        //Original ModelBakerySubsystem.requestBlockBake owns dedupe/enqueue/unpark; the render
        // generation service also calls it directly, matching original ownership.
        renderSystem.modelService().requestBlockBake(blockStateId);
        this.lastFailureReason = renderSystem.modelService().factory.lastFailureReason();
        this.lastLifecycleEvent = "request-block-bake-queued";
    }

    synchronized ForgeOriginalVoxyModelPipelineStats enqueueRenderGenerationTask(long sectionKey) {
        if (!this.ownerReady || this.stale || this.renderSystem == null) {
            this.lastFailureReason = "original-async-node-manager-not-ready";
            return this.createStatusSnapshot();
        }
        this.renderSystem.nodeManager().addTopLevel(sectionKey);
        this.lastLifecycleEvent = "top-level-node-request-queued";
        this.lastFailureReason = "none";
        return this.createStatusSnapshot();
    }

    synchronized ForgeOriginalVoxyMdicCommandGenerationStats createMdicCommandGenerationStatusSnapshot() {
        return this.renderSystem == null
                ? ForgeOriginalVoxyMdicCommandGenerationStats.unavailable("original-mdic-command-generator-not-started")
                : this.renderSystem.sectionRenderer().createStatusSnapshot();
    }

    synchronized ForgeOriginalVoxyMdicCommandGenerationStats requestMdicCommandGenerationReadbackAudit() {
        if (this.renderSystem == null || !this.ownerReady || this.stale) {
            return ForgeOriginalVoxyMdicCommandGenerationStats.unavailable("original-mdic-command-generator-not-ready");
        }
        this.renderSystem.traversal().requestReadbackAudit();
        this.renderSystem.sectionRenderer().requestReadbackAudit();
        this.lastLifecycleEvent = "original-mdic-command-generation-readback-audit-requested";
        return this.renderSystem.sectionRenderer().createStatusSnapshot();
    }

    public void applyCapturedOculusViewport() {
        if (!RenderSystem.isOnRenderThread()) {
            this.recordNonFatalFailure("original-oculus-viewport-setup-not-render-thread");
            return;
        }
        ForgeOriginalVoxyRenderStateCapture.CapturedViewportParameters captured =
                ForgeOriginalVoxyRenderStateCapture.oculusViewportParametersCopy();
        if (captured == null) {
            this.recordNonFatalFailure("original-oculus-viewport-parameters-not-captured");
            return;
        }
        synchronized (this) {
            this.originalOculusViewportCapturedGeneration = captured.generation();
            this.originalOculusViewportAppliedGeneration = captured.generation();
            this.originalOculusViewportApplyCount++;
            this.lastLifecycleEvent = "oculus-begin-level-rendering-viewport-captured";
            this.lastFailureReason = "none";
        }
    }

    synchronized ForgeOriginalVoxyModelPipelineStats createStatusSnapshot() {
        ForgeOriginalVoxyRenderSystem renderSystem = this.renderSystem;
        ForgeOriginalVoxyModelBakerySubsystem modelBakery = renderSystem == null ? null : renderSystem.modelService();
        ForgeOriginalVoxyRenderPipeline renderPipeline = renderSystem == null ? null : renderSystem.renderPipeline();
        ForgeOriginalVoxyViewportSelector viewportSelector = renderSystem == null ? null : renderSystem.viewportSelector();
        Mapper mapper = renderSystem == null ? null : renderSystem.getEngine().getMapper();
        int mapperBlockStateCount = mapper == null ? 0 : mapper.getBlockStateCount();
        int mapperBiomeCount = mapper == null ? 0 : mapper.getBiomeEntries().length;
        ForgeOriginalVoxyModelFactoryStats factory = modelBakery == null
                ? ForgeOriginalVoxyModelFactoryStats.unavailable("model-factory-not-started")
                : modelBakery.factory.createStatusSnapshot();
        ForgeOriginalVoxyRenderGenerationStats renderGeneration = renderSystem == null
                ? ForgeOriginalVoxyRenderGenerationStats.unavailable("render-generation-not-started")
                : renderSystem.renderGenerationService().createStatusSnapshot();
        ForgeOriginalVoxyBasicAsyncGeometryStats geometry = renderSystem == null
                ? ForgeOriginalVoxyBasicAsyncGeometryStats.unavailable("basic-async-geometry-not-started")
                : renderSystem.geometryManager().createStatusSnapshot(true);
        ForgeOriginalVoxyBasicSectionGeometryDataStats geometryData = renderSystem == null
                ? ForgeOriginalVoxyBasicSectionGeometryDataStats.unavailable("basic-section-geometry-data-not-started")
                : renderSystem.geometryData().createStatusSnapshot();
        ForgeOriginalVoxyAsyncNodeGeometrySyncStats nodeSync = renderSystem == null
                ? ForgeOriginalVoxyAsyncNodeGeometrySyncStats.unavailable("async-node-geometry-sync-not-started")
                : renderSystem.nodeManager().createStatusSnapshot();
        ForgeOriginalVoxyHierarchicalOcclusionTraverserStats hoc = renderSystem == null
                ? ForgeOriginalVoxyHierarchicalOcclusionTraverserStats.unavailable("hierarchical-occlusion-traverser-not-started")
                : renderSystem.traversal().createStatusSnapshot();
        boolean workerReady = modelBakery != null && modelBakery.workerReady();
        boolean viewportSelectorReady = viewportSelector != null && viewportSelector.ready();
        boolean viewportSelectorDefaultReady = viewportSelector != null && viewportSelector.defaultViewportReady();
        int viewportSelectorExtraViewportCount = viewportSelector == null ? 0 : viewportSelector.extraViewportCount();
        String viewportSelectorLastSelectedKey = viewportSelector == null ? "none" : viewportSelector.lastSelectedKey();
        boolean mdicViewportOwnerReady = viewportSelectorReady;
        boolean hizOwnerReady = viewportSelector != null && viewportSelector.hizOwnerReady();
        boolean hocOwnerReady = hoc.originalHierarchicalOcclusionTraverserReady();
        boolean hizTraversalExecutableReady = viewportSelector != null && viewportSelector.selectedHizTraversalReady();
        boolean hocExecutableReady = hocOwnerReady && mdicViewportOwnerReady && hizTraversalExecutableReady;
        boolean visibleRendererOwnerReady = this.ownerReady
                && !this.stale
                && mdicViewportOwnerReady
                && hocExecutableReady
                && renderSystem != null
                && renderPipeline != null
                && renderPipeline.ready()
                && renderSystem.sectionRenderer().ready();
        boolean finalBlitEnvironmentalFogEnabled = renderPipeline != null
                ? renderPipeline.useEnvironmentalFog()
                : this.originalFinalBlitEnvironmentalFogEnabled;
        boolean oculusShaderpackPipelineActive = renderPipeline != null
                && renderPipeline.oculusShaderpackActive();
        boolean oculusShaderpackPipelineDataReady = renderPipeline != null
                && renderPipeline.oculusPipelineDataReady();
        boolean oculusShaderpackPatchShaderUsed = renderPipeline != null
                && renderPipeline.oculusShaderPatchReady();
        boolean oculusShaderpackBindingsUsed = renderPipeline != null
                && renderPipeline.oculusShaderBindingsReady();
        boolean oculusShaderpackDrawTargetsUsed = renderPipeline != null
                && renderPipeline.oculusDrawTargetsReady();
        boolean oculusShaderpackUniformsUsed = renderPipeline != null
                && renderPipeline.oculusUniformsReady();
        boolean oculusShaderpackSsboBindingsReady = renderPipeline != null
                && renderPipeline.oculusSsboBindingsReady();
        boolean oculusShaderpackImageBindingsReady = renderPipeline != null
                && renderPipeline.oculusImageBindingsReady();
        boolean oculusShaderpackBlendStateReady = renderPipeline != null
                && renderPipeline.oculusBlendReady();
        boolean oculusShaderpackTaaReady = renderPipeline != null
                && renderPipeline.oculusTaaReady();
        String oculusShaderpackSource = renderPipeline == null
                ? "original-render-pipeline-not-started"
                : renderPipeline.oculusPipelineSource();
        String oculusShaderpackFailureReason = renderPipeline == null
                ? "none"
                : renderPipeline.oculusPipelineFailureReason();
        return new ForgeOriginalVoxyModelPipelineStats(
                STAGE,
                this.startRequests,
                this.startRuns,
                this.tickRuns,
                this.uploadTickRuns,
                this.blockBakeRequests + (modelBakery == null ? 0L : modelBakery.blockBakeRequestCount()),
                this.clearRuns,
                this.originalLifecycleDownloadFlushUsed,
                this.originalRenderStateCaptureClearUsed,
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
                true,
                true,
                false,
                false,
                new ForgeOriginalVoxyVisibleRendererStats(
                        "VIII_ORIGINAL_VISIBLE_RENDERER_CHAIN",
                        visibleRendererOwnerReady,
                        this.originalVoxyRunPipelineOrderUsed && this.ownerReady && !this.stale,
                        this.originalVisibleMdicDrawSubmissionUsed && this.ownerReady && !this.stale,
                        this.originalPostFrameDynamicWorkUsed && this.ownerReady && !this.stale,
                        this.originalMdicOpaqueDrawSubmitted && this.ownerReady && !this.stale,
                        this.originalMdicTemporalDrawSubmitted && this.ownerReady && !this.stale,
                        this.originalMdicTranslucentDrawSubmitted && this.ownerReady && !this.stale,
                        this.originalPipelineFinishCalled && this.ownerReady && !this.stale,
                        this.originalVisibleRendererStateRestoreUsed && this.ownerReady && !this.stale,
                        this.originalViewportFogParametersUsed && this.ownerReady && !this.stale,
                        finalBlitEnvironmentalFogEnabled && this.ownerReady && !this.stale,
                        this.originalFinalBlitEnvironmentalFogUniformsUsed && this.ownerReady && !this.stale,
                        oculusShaderpackPipelineActive,
                        oculusShaderpackPipelineDataReady,
                        oculusShaderpackPatchShaderUsed,
                        oculusShaderpackBindingsUsed,
                        oculusShaderpackDrawTargetsUsed,
                        oculusShaderpackUniformsUsed,
                        oculusShaderpackSsboBindingsReady,
                        oculusShaderpackImageBindingsReady,
                        oculusShaderpackBlendStateReady,
                        oculusShaderpackTaaReady,
                        oculusShaderpackSource,
                        oculusShaderpackFailureReason,
                        this.originalVisibleFrameLastFogStart,
                        this.originalVisibleFrameLastFogEnd,
                        this.originalVisibleFrameRunCount,
                        this.originalVisibleFrameSkippedCount,
                        this.originalVisibleFrameFailureCount,
                        this.originalVisibleFrameDrawSubmissionCount,
                        this.originalVisibleFrameLastFramebuffer,
                        this.originalVisibleFrameLastViewportWidth,
                        this.originalVisibleFrameLastViewportHeight,
                        true),
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
                renderSystem != null && this.ownerReady && !this.stale,
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

    void markOculusWorldRenderingSettingsReload() {
        boolean shouldRestart;
        synchronized (this) {
            if ("oculus-world-rendering-settings-reload".equals(this.lastLifecycleEvent)
                    && (this.stale || this.startRequested)) {
                return;
            }
            shouldRestart = this.ownerReady || this.startRequested;
            if (!this.ownerReady && !this.startRequested) {
                this.requiresRebuild = true;
                this.lifecycleState = "RELOAD_PENDING";
                this.lastLifecycleEvent = "oculus-world-rendering-settings-reload";
                this.lastFailureReason = "none";
                return;
            }
        }
        this.markStaleAndClear("oculus-world-rendering-settings-reload");
        if (shouldRestart) {
            this.requestStart("oculus-world-rendering-settings-reload");
        }
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

    private synchronized long scheduleStartOnRenderThread() {
        if (!this.startRequested || this.ownerReady || this.startQueuedOnRenderThread) {
            return -1L;
        }
        this.startQueuedOnRenderThread = true;
        this.startQueuedGeneration = this.lifecycleGeneration;
        return this.startQueuedGeneration;
    }

    private boolean shouldRequestClientWorldStart() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null || !ForgeVoxyConfig.ENABLED.get()) {
            return false;
        }
        synchronized (this) {
            if (this.ownerReady || this.startRequested || this.startQueuedOnRenderThread) {
                return false;
            }
            if ("failure".equals(this.lastLifecycleEvent)) {
                return false;
            }
        }
        ForgeOculusWorldRenderingSettingsBridge.Result blockStateIds =
                ForgeOculusWorldRenderingSettingsBridge.getBlockStateIds();
        if (!blockStateIds.ready()) {
            this.recordNonFatalFailure(blockStateIds.failureReason());
            return false;
        }
        if (activeShaderpackMissingBlockStateIds(blockStateIds)) {
            this.recordNonFatalFailure("oculus-block-state-id-map-null-for-active-shaderpack");
            return false;
        }
        return this.instance.ensureOriginalVoxyActiveWorldForCurrentWorld();
    }

    private boolean consumeOculusWorldRenderingSettingsReload() {
        ForgeOculusWorldRenderingSettingsBridge.ReloadState reloadState =
                ForgeOculusWorldRenderingSettingsBridge.isReloadRequired();
        synchronized (this) {
            this.oculusReloadPollCount++;
        }
        if (!reloadState.ready()) {
            this.recordNonFatalFailure(reloadState.failureReason());
            return false;
        }
        if (!reloadState.reloadRequired()) {
            synchronized (this) {
                this.oculusReloadFlagCurrentlyObserved = false;
                this.oculusReloadDuplicatePollLogged = false;
            }
            return false;
        }
        synchronized (this) {
            if (this.oculusReloadFlagCurrentlyObserved) {
                this.oculusReloadDuplicatePollCount++;
                if (!this.oculusReloadDuplicatePollLogged) {
                    this.oculusReloadDuplicatePollLogged = true;
                    Logger.info("Debounced repeated Oculus WorldRenderingSettings reload flag while waiting for Oculus to consume it.");
                }
                return false;
            }
            this.oculusReloadFlagCurrentlyObserved = true;
            this.oculusReloadEdgeCount++;
        }
        Logger.info("Observed Oculus WorldRenderingSettings reload edge for Voxy owner rebuild.");
        this.markOculusWorldRenderingSettingsReload();
        return true;
    }

    private void startOnRenderThread(long expectedGeneration) {
        synchronized (this) {
            if (expectedGeneration != this.lifecycleGeneration
                    || !this.startQueuedOnRenderThread
                    || this.startQueuedGeneration != expectedGeneration
                    || !this.startRequested
                    || this.ownerReady) {
                this.queuedStartStaleSkipCount++;
                this.lastQueuedLifecycleSkipReason = "start-stale-generation";
                this.lastLifecycleEvent = "start-skipped-stale-generation";
                return;
            }
            this.startQueuedOnRenderThread = false;
            this.startQueuedGeneration = -1L;
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
        ForgeOculusWorldRenderingSettingsBridge.Result blockStateIds = ForgeOculusWorldRenderingSettingsBridge.getBlockStateIds();
        if (!blockStateIds.ready()) {
            this.recordFailure(blockStateIds.failureReason());
            return;
        }
        if (activeShaderpackMissingBlockStateIds(blockStateIds)) {
            this.recordFailure("oculus-block-state-id-map-null-for-active-shaderpack");
            return;
        }
        this.updateDedicatedThreads();
        if (!this.originalServiceThreadConfigOwnerReady) {
            this.recordFailure("original-service-thread-config-not-ready:" + this.originalServiceThreadPolicyFailureReason);
            return;
        }
        ForgeOriginalVoxyRenderSystem previousSystem;
        synchronized (this) {
            previousSystem = this.renderSystem;
            this.renderSystem = null;
            if (previousSystem != null) {
                this.blockBakeRequests += previousSystem.modelService().blockBakeRequestCount();
            }
        }
        if (previousSystem != null) {
            //Original recreate path (MixinLevelRenderer.allChanged) shuts the previous renderer
            // down before constructing the new one; this covers a FAILED_SAFE owner that a
            // command-driven restart would otherwise overwrite without teardown.
            previousSystem.shutdown(true);
        }
        //The complete original VoxyRenderSystem constructor boundary: world ref first, model
        // bakery (store/factory/worker thread), render generation, geometry owners, node
        // manager + callbacks + start, pipeline, traversal late-stage compile, section
        // renderer, viewport selector, render distance tracker, chunk-bound renderer.
        ForgeOriginalVoxyRenderSystem renderSystem;
        try {
            renderSystem = new ForgeOriginalVoxyRenderSystem(
                    targetWorld,
                    this.serviceThreadPool.serviceManager,
                    minecraft,
                    blockStateIds.blockStateIds(),
                    blockStateIds.source());
        } catch (RuntimeException e) {
            //Mirror original MixinLevelRenderer.voxy$createRenderer: construction failure with an
            // active shaderpack disables Oculus shaders so the triggered reload retries on the
            // normal path; without a shaderpack original rethrows while the Forge adapter records
            // FAILED_SAFE instead of crashing the client.
            if (ForgeOriginalVoxyOculusPipelineBridge.shaderpackActive()) {
                ForgeOriginalVoxyOculusPipelineBridge.disableShaders();
            }
            this.recordFailure("original-render-system-construction-" + e.getClass().getSimpleName() + ":" + e.getMessage());
            return;
        }
        //Original mixin calls instance.updateDedicatedThreads() after creating the renderer.
        this.updateDedicatedThreads();
        if (!this.isStartGenerationCurrent(expectedGeneration)) {
            renderSystem.shutdown(false);
            return;
        }
        synchronized (this) {
            this.renderSystem = renderSystem;
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
            this.replayPendingChunkBoundSections(renderSystem.chunkBoundRenderer());
        }
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
        boolean visibleFrameCompleted = false;
        boolean opaqueSubmitted = false;
        boolean temporalSubmitted = false;
        boolean translucentSubmitted = false;
        boolean finishCalled = false;
        boolean capturedRenderState = false;
        boolean postDynamicWorkEligible = false;
        double postDynamicCameraX = 0.0D;
        double postDynamicCameraZ = 0.0D;
        int oldFramebuffer = 0;
        OriginalVoxyRenderState oldRenderState = null;
        try {
            ForgeOriginalVoxyRenderSystem renderSystem;
            synchronized (this) {
                if (!this.ownerReady || this.stale || this.renderSystem == null) {
                    return;
                }
                renderSystem = this.renderSystem;
            }
            ForgeOriginalVoxyMdicViewport viewport;
            ForgeOriginalVoxyViewportSelector selector = renderSystem.viewportSelector();
            ForgeOriginalVoxyHierarchicalOcclusionTraverser traversal = renderSystem.traversal();
            ForgeOriginalVoxyMdicSectionRenderer sectionRenderer = renderSystem.sectionRenderer();
            ForgeOriginalVoxyRenderPipeline renderPipeline = renderSystem.renderPipeline();
            ForgeOriginalVoxyChunkBoundRenderer chunkBoundRenderer = renderSystem.chunkBoundRenderer();
            ForgeOriginalVoxyBasicSectionGeometryData geometryData = renderSystem.geometryData();
            ForgeOriginalVoxyAsyncNodeGeometrySync geometrySync = renderSystem.nodeManager();
            ForgeOriginalVoxyNodeCleaner cleaner = renderSystem.nodeCleaner();
            ForgeOriginalVoxyRenderGenerationService renderGeneration = renderSystem.renderGenerationService();
            ForgeOriginalVoxyModelBakerySubsystem modelBakery = renderSystem.modelService();
            ForgeOriginalVoxyModelStore modelStore = modelBakery.getStore();
            viewport = selector == null ? null : selector.getViewport();
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.level == null || minecraft.player == null || matrices == null || camera == null) {
                return;
            }
            if (viewport == null || traversal == null || sectionRenderer == null || renderPipeline == null || chunkBoundRenderer == null || geometryData == null
                    || modelStore == null || !traversal.ready()) {
                //A null viewport during the Oculus shadow pass is the expected per-frame skip, not
                // a failure state.
                if (viewport == null && selector != null && selector.lastSelectionWasOculusShadowSkip()) {
                    return;
                }
                this.recordNonFatalFailure("visible-frame-not-ready:"
                        + (viewport == null ? "viewport," : "")
                        + (traversal == null ? "traversal," : "")
                        + (sectionRenderer == null ? "sectionRenderer," : "")
                        + (renderPipeline == null ? "renderPipeline," : "")
                        + (chunkBoundRenderer == null ? "chunkBoundRenderer," : "")
                        + (geometryData == null ? "geometryData," : "")
                        + (modelStore == null ? "modelStore," : "")
                        + (traversal != null && !traversal.ready() ? "traversal-not-ready" : ""));
                return;
            }
            oldRenderState = captureOriginalVoxyRenderState();
            oldFramebuffer = oldRenderState.drawFramebuffer();
            if (oldFramebuffer == 0) {
                this.recordNonFatalFailure("original-hoc-embeddium-cutout-default-framebuffer");
                return;
            }
            capturedRenderState = true;
            int sourceWidth = oldRenderState.viewport()[2];
            int sourceHeight = oldRenderState.viewport()[3];
            if (sourceWidth <= 0 || sourceHeight <= 0) {
                this.recordNonFatalFailure("original-hoc-embeddium-cutout-invalid-viewport");
                return;
            }
            int width = sourceWidth;
            int height = sourceHeight;
            float[] renderScale = renderPipeline.renderScalingFactor();
            if (renderScale != null) {
                width = (int) (width * renderScale[0]);
                height = (int) (height * renderScale[1]);
            }
            if (width <= 0 || height <= 0) {
                this.recordNonFatalFailure("original-hoc-embeddium-cutout-invalid-scaled-viewport");
                return;
            }
            ForgeOriginalVoxyFogParameters fogParameters = ForgeOriginalVoxyFogParameters.captureFromRenderSystem(
                    ForgeVoxyConfig.ORIGINAL_VOXY_USE_ENVIRONMENTAL_FOG.get());
            Matrix4f vanillaProjection = new Matrix4f(matrices.projection());
            Matrix4f modelView = new Matrix4f(matrices.modelView());
            Matrix4f rawMinecraftProjection = ForgeOriginalVoxyRenderStateCapture.projectionCopy();
            if (rawMinecraftProjection == null) {
                this.recordNonFatalFailure("original-hoc-raw-projection-not-captured");
                return;
            }
            if (renderPipeline.oculusShaderpackActive()) {
                ForgeOriginalVoxyRenderStateCapture.CapturedViewportParameters captured =
                        ForgeOriginalVoxyRenderStateCapture.oculusViewportParametersCopy();
                long capturedGeneration = captured == null ? 0L : captured.generation();
                if (AUDIT_OCULUS_MATRICES && this.originalOculusMatrixAuditRuns < 8 && captured != null) {
                    this.originalOculusMatrixAuditRuns++;
                    Logger.info(String.format(
                            "Original Oculus matrix audit: run=%d generation=%d projectionDiff=%.8f modelViewDiff=%.8f cameraDiff=[%.5f,%.5f,%.5f] sourceViewport=%dx%d scaledViewport=%dx%d",
                            this.originalOculusMatrixAuditRuns,
                            capturedGeneration,
                            maxMatrixAbsDiff(captured.vanillaProjection(), matrices.projection()),
                            maxMatrixAbsDiff(captured.modelView(), matrices.modelView()),
                            captured.cameraX() - camera.x,
                            captured.cameraY() - camera.y,
                            captured.cameraZ() - camera.z,
                            sourceWidth,
                            sourceHeight,
                            width,
                            height));
                }
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
                    .setFogParameters(fogParameters)
                    .update();
            viewport.frameId++;
            glViewport(0, 0, viewport.width, viewport.height);
            renderPipeline.preSetup(viewport);
            chunkBoundRenderer.render(viewport, originalFrexActive());
            int depthTexture = renderPipeline.setup(
                    viewport,
                    oldFramebuffer,
                    oldRenderState.readFramebuffer(),
                    sourceWidth,
                    sourceHeight);
            postDynamicWorkEligible = true;
            postDynamicCameraX = camera.x;
            postDynamicCameraZ = camera.z;
            sectionRenderer.renderOpaque(viewport, geometryData, modelStore, renderPipeline);
            viewport.buildHizFromSourceDepth(depthTexture, width, height);
            this.runOriginalInnerPrimaryWorkWithTraversal(
                    viewport,
                    geometrySync,
                    cleaner,
                    traversal,
                    renderGeneration,
                    renderSystem);
            sectionRenderer.buildDrawCalls(viewport, geometryData, ForgeOriginalVoxyRenderProperties.getRenderProperties());
            commandGenerationCompleted = true;
            sectionRenderer.renderTemporal(viewport, geometryData, modelStore, renderPipeline);
            sectionRenderer.postOpaquePreperation(viewport);
            renderPipeline.postOpaquePreTranslucent(viewport, oldFramebuffer, true);
            sectionRenderer.renderTranslucent(viewport, geometryData, modelStore, renderPipeline);
            opaqueSubmitted = sectionRenderer.hasOpaqueDrawCountReadback();
            temporalSubmitted = sectionRenderer.hasTemporalOpaqueDrawCountReadback();
            translucentSubmitted = sectionRenderer.hasTranslucentDrawCountReadback();
            renderPipeline.finish(viewport, oldFramebuffer, sourceWidth, sourceHeight, true);
            finishCalled = true;
            visibleFrameCompleted = true;
            if (AUDIT_CHUNK_BOUND) {
                this.auditChunkBoundHandoff(viewport, chunkBoundRenderer, sectionRenderer, geometrySync);
            }
            synchronized (this) {
                if (this.ownerReady && !this.stale) {
                    this.originalVisibleFrameRunCount++;
                    if (opaqueSubmitted || temporalSubmitted || translucentSubmitted) {
                        this.originalVisibleFrameDrawSubmissionCount++;
                    }
                    this.originalVoxyRunPipelineOrderUsed = true;
                    this.originalVisibleMdicDrawSubmissionUsed = opaqueSubmitted || temporalSubmitted || translucentSubmitted;
                    this.originalMdicOpaqueDrawSubmitted = opaqueSubmitted;
                    this.originalMdicTemporalDrawSubmitted = temporalSubmitted;
                    this.originalMdicTranslucentDrawSubmitted = translucentSubmitted;
                    this.originalPipelineFinishCalled = finishCalled;
                    this.originalChunkBoundDepthPassUsed = true;
                    this.originalChunkBoundDepthPassRuns++;
                    this.originalChunkBoundTrackedSectionCount = chunkBoundRenderer.trackedSectionCount();
                    this.originalViewportFogParametersUsed = true;
                    this.originalFinalBlitEnvironmentalFogEnabled = renderPipeline.useEnvironmentalFog();
                    this.originalFinalBlitEnvironmentalFogUniformsUsed = renderPipeline.useEnvironmentalFog();
                    this.originalVisibleFrameLastFramebuffer = oldFramebuffer;
                    this.originalVisibleFrameLastViewportWidth = width;
                    this.originalVisibleFrameLastViewportHeight = height;
                    this.originalVisibleFrameLastFogStart = fogParameters.environmentalStart();
                    this.originalVisibleFrameLastFogEnd = fogParameters.environmentalEnd();
                    this.lifecycleState = "RUNNING_ORIGINAL_VISIBLE_MDIC_FRAME";
                    this.lastLifecycleEvent = "embeddium-cutout-original-run-pipeline-order";
                    this.lastFailureReason = "none";
                }
            }
        } catch (RuntimeException e) {
            synchronized (this) {
                this.originalVisibleFrameFailureCount++;
            }
            this.recordNonFatalFailure("original-hoc-embeddium-cutout-" + e.getClass().getSimpleName() + ":" + e.getMessage());
        } finally {
            try {
                try {
                    if (postDynamicWorkEligible) {
                        try {
                            this.runOriginalPostDynamicWorkAfterCommandGeneration(postDynamicCameraX, postDynamicCameraZ);
                            synchronized (this) {
                                this.originalPostFrameDynamicWorkUsed = true;
                            }
                        } catch (RuntimeException e) {
                            this.recordNonFatalFailure("original-post-dynamic-" + e.getClass().getSimpleName() + ":" + e.getMessage());
                        }
                    }
                    if (!visibleFrameCompleted && !commandGenerationCompleted) {
                        synchronized (this) {
                            this.originalVisibleFrameSkippedCount++;
                        }
                    }
                } finally {
                    if (capturedRenderState) {
                        restoreOriginalVoxyRenderState(oldRenderState);
                        synchronized (this) {
                            this.originalVisibleRendererStateRestoreUsed = true;
                        }
                    }
                }
            } finally {
                this.renderEmbeddiumCutoutActive = false;
            }
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
        if (this.serviceThreadPoolShutdown) {
            this.originalServiceThreadConfigOwnerReady = false;
            this.originalServiceThreadPolicyFailureReason = "service-thread-pool-shutdown";
            return;
        }
        ForgeOriginalVoxyServiceThreadPolicy.Selection selection = ForgeOriginalVoxyServiceThreadPolicy.select();
        this.originalServiceThreadConfigOwnerReady = selection.configOwnerReady();
        this.originalEmbeddiumBuilderThreadSharingEnabled = selection.useEmbeddiumBuilderThreads();
        this.originalEmbeddiumBuilderThreadSharingReady = selection.useEmbeddiumBuilderThreads()
                && selection.embeddiumBuilderThreadCountAvailable();
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
        long cleanupGeneration;
        ForgeOriginalVoxyRenderSystem renderSystem;
        synchronized (this) {
            this.lifecycleGeneration++;
            cleanupGeneration = this.lifecycleGeneration;
            this.clearRuns++;
            this.startRequested = false;
            this.startQueuedOnRenderThread = false;
            this.startQueuedGeneration = -1L;
            this.ownerReady = false;
            this.pendingChunkBoundAdds.clear();
            this.pendingChunkBoundRemoves.clear();
            this.mapperBiomeCallbackAttached = false;
            this.existingBiomeEntriesQueued = false;
            this.stale = true;
            this.requiresRebuild = true;
            this.lifecycleState = "STALE";
            this.lastLifecycleEvent = safeReason(event);
            this.lastFailureReason = "none";
            renderSystem = this.renderSystem;
            this.renderSystem = null;
            if (renderSystem != null) {
                //Fold the per-lifecycle model-bakery request counter into the cumulative stat.
                this.blockBakeRequests += renderSystem.modelService().blockBakeRequestCount();
            }
        }
        ForgeOriginalVoxyRenderStateCapture.clear();
        synchronized (this) {
            this.originalRenderStateCaptureClearUsed = true;
        }
        this.runOnRenderThread(() -> {
            boolean cleanupCurrent;
            synchronized (this) {
                cleanupCurrent = cleanupGeneration == this.lifecycleGeneration || !this.ownerReady;
                if (!cleanupCurrent) {
                    this.queuedCleanupStaleSkipCount++;
                    this.lastQueuedLifecycleSkipReason = "cleanup-stale-generation";
                    this.lastLifecycleEvent = "cleanup-skipped-global-flush-stale-generation";
                }
            }
            boolean flushedDownloadStream = false;
            if (renderSystem != null) {
                //Full original VoxyRenderSystem.shutdown() order: flush, callbacks, node manager,
                // model bakery, render generation, traversal, cleaner, geometry, chunk-bound,
                // viewport selector, pipeline last, flush, release world ref. The global download
                // stream flush is skipped when a newer lifecycle generation already owns it.
                flushedDownloadStream = renderSystem.shutdown(cleanupCurrent);
            } else if (cleanupCurrent && ForgeOriginalVoxyDownloadStream.isReady()) {
                ForgeOriginalVoxyDownloadStream.instance().flushWaitClear();
                flushedDownloadStream = true;
            }
            if (flushedDownloadStream) {
                synchronized (this) {
                    this.originalLifecycleDownloadFlushUsed = true;
                }
            }
        });
    }

    private void runOnRenderThread(Runnable task) {
        if (RenderSystem.isOnRenderThread()) {
            task.run();
        } else {
            RenderSystem.recordRenderCall(task::run);
        }
    }

    private void processFactoryUploads(ForgeOriginalVoxyRenderSystem renderSystem) {
        this.processFactoryUploads(renderSystem, MAX_MODEL_UPLOADS_PER_TICK);
    }

    private void processFactoryUploads(ForgeOriginalVoxyRenderSystem renderSystem, int maxUploads) {
        if (!RenderSystem.isOnRenderThread()) {
            this.recordFailure("model-factory-upload-not-render-thread");
            return;
        }
        synchronized (this) {
            if (!this.ownerReady || this.stale || renderSystem != this.renderSystem) {
                this.queuedFactoryUploadStaleSkipCount++;
                this.lastQueuedLifecycleSkipReason = "factory-upload-stale-generation";
                this.lastLifecycleEvent = "factory-upload-skipped-stale-generation";
                return;
            }
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            return;
        }
        //Original ModelBakerySubsystem.tick(): rethrows worker death, drains uploads. Original
        // crashes on worker death; the Forge adapter records FAILED_SAFE and lets the next
        // client tick run the full teardown (this can sit mid-frame, so nothing is freed here).
        int processed;
        try {
            processed = renderSystem.modelService().tick(maxUploads);
        } catch (RuntimeException e) {
            this.recordFailure("model-factory-processor-" + e.getClass().getSimpleName() + ":" + e.getMessage());
            return;
        }
        if (processed > 0) {
            synchronized (this) {
                this.uploadTickRuns++;
                this.lifecycleState = "RUNNING_MODEL_UPLOADS";
                this.lastLifecycleEvent = "model-factory-upload-tick";
                this.lastFailureReason = renderSystem.modelService().factory.createStatusSnapshot().lastFailureReason();
            }
        }
    }

    //Gated by -Dvoxy.forge.auditChunkBound: once per second, log the vanilla/LOD boundary handoff
    // ground truth — chunk-bound mask churn (adds/removes/clears applied), tracked mask size, and
    // the MDIC draw counts (opaque/temporal/translucent + render-list sections). Boundary flicker
    // hypotheses separate on these: abnormal mask churn (tracker feed), temporal count stuck at 0
    // while opaque changes (temporal pass not covering newly exposed sections), or render-list
    // collapse (traversal). The draw-count read forces a GPU sync, hence the 1s rate limit.
    private void auditChunkBoundHandoff(
            ForgeOriginalVoxyMdicViewport viewport,
            ForgeOriginalVoxyChunkBoundRenderer chunkBoundRenderer,
            ForgeOriginalVoxyMdicSectionRenderer sectionRenderer,
            ForgeOriginalVoxyAsyncNodeGeometrySync geometrySync) {
        this.chunkBoundAuditFramesInWindow++;
        long now = System.nanoTime();
        if (now - this.chunkBoundAuditLastNanos < 1_000_000_000L) {
            return;
        }
        long adds = chunkBoundRenderer.addsAppliedCount();
        long removes = chunkBoundRenderer.removesAppliedCount();
        long clears = chunkBoundRenderer.depthBoundClearCount();
        int[] drawCounts = sectionRenderer.auditDrawCounts(viewport);
        Logger.info(String.format(
                "Voxy chunk-bound audit: frames=%d tracked=%d adds/s=%d removes/s=%d clears/s=%d opaqueDraws=%d temporalDraws=%d translucentDraws=%d renderListSections=%d geometryQueue=%d",
                this.chunkBoundAuditFramesInWindow,
                chunkBoundRenderer.trackedSectionCount(),
                adds - this.chunkBoundAuditLastAdds,
                removes - this.chunkBoundAuditLastRemoves,
                clears - this.chunkBoundAuditLastClears,
                drawCounts[0],
                drawCounts[2],
                drawCounts[1],
                drawCounts[3],
                geometrySync == null ? -1 : geometrySync.createStatusSnapshot().queuedGeometryResults()));
        this.chunkBoundAuditLastNanos = now;
        this.chunkBoundAuditLastAdds = adds;
        this.chunkBoundAuditLastRemoves = removes;
        this.chunkBoundAuditLastClears = clears;
        this.chunkBoundAuditFramesInWindow = 0;
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

    private void runOriginalInnerPrimaryWorkWithTraversal(
            ForgeOriginalVoxyMdicViewport viewport,
            ForgeOriginalVoxyAsyncNodeGeometrySync geometrySync,
            ForgeOriginalVoxyNodeCleaner cleaner,
            ForgeOriginalVoxyHierarchicalOcclusionTraverser traversal,
            ForgeOriginalVoxyRenderGenerationService renderGeneration,
            ForgeOriginalVoxyRenderSystem renderSystem) {
        int iterations = 0;
        do {
            this.runOriginalInnerPrimaryWorkBeforeTraversal(geometrySync, cleaner);
            traversal.doTraversal(viewport);
            traversal.runReadbackAuditIfRequested(viewport);
            iterations++;
        } while (iterations < 16 && this.originalFrexStillHasWork(geometrySync, renderGeneration, renderSystem));
    }

    private boolean originalFrexStillHasWork(
            ForgeOriginalVoxyAsyncNodeGeometrySync geometrySync,
            ForgeOriginalVoxyRenderGenerationService renderGeneration,
            ForgeOriginalVoxyRenderSystem renderSystem) {
        if (!originalFrexActive()) {
            return false;
        }
        if (ForgeOriginalVoxyUploadStream.isReady()) {
            ForgeOriginalVoxyUploadStream.instance().tick();
        }
        if (renderSystem != null) {
            this.processFactoryUploads(renderSystem, 100_000_000);
        }
        glFinish();
        return geometrySync != null && geometrySync.hasWork()
                || renderGeneration != null && renderGeneration.taskQueueCount() != 0
                || renderSystem != null && !renderSystem.modelService().areQueuesEmpty();
    }

    private static boolean originalFrexActive() {
        try {
            Class<?> type = Class.forName("me.cortex.voxy.client.VoxyClient");
            Object result = type.getMethod("isFrexActive").invoke(null);
            return Boolean.TRUE.equals(result);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ignored) {
            return false;
        }
    }

    private static boolean activeShaderpackMissingBlockStateIds(ForgeOculusWorldRenderingSettingsBridge.Result blockStateIds) {
        return blockStateIds.blockStateIds() == null && ForgeOriginalVoxyOculusPipelineBridge.shaderpackActive();
    }

    private void runOriginalPostDynamicWorkAfterCommandGeneration(double cameraX, double cameraZ) {
        if (ForgeOriginalVoxyUploadStream.isReady()) {
            ForgeOriginalVoxyUploadStream.instance().tick();
        }
        this.processRenderDistanceTrackerOnRenderThread(cameraX, cameraZ);
        ForgeOriginalVoxyRenderSystem renderSystem;
        synchronized (this) {
            renderSystem = this.ownerReady && !this.stale ? this.renderSystem : null;
        }
        if (renderSystem != null) {
            this.processFactoryUploads(renderSystem, Integer.MAX_VALUE);
        }
    }

    private static int[] captureShaderStorageBufferBindings() {
        int bindingCount = Math.max(16, glGetInteger(GL_MAX_SHADER_STORAGE_BUFFER_BINDINGS));
        int[] oldBufferBindings = new int[bindingCount];
        for (int i = 0; i < oldBufferBindings.length; i++) {
            oldBufferBindings[i] = glGetIntegeri(GL_SHADER_STORAGE_BUFFER_BINDING, i);
        }
        return oldBufferBindings;
    }

    private static OriginalVoxyRenderState captureOriginalVoxyRenderState() {
        int activeTexture = glGetInteger(GL_ACTIVE_TEXTURE);
        int textureUnitCount = Math.max(12, glGetInteger(GL_MAX_COMBINED_TEXTURE_IMAGE_UNITS));
        int[] textureBindings = new int[textureUnitCount];
        int[] samplerBindings = new int[textureUnitCount];
        for (int i = 0; i < textureBindings.length; i++) {
            glActiveTexture(GL_TEXTURE0 + i);
            textureBindings[i] = glGetInteger(GL_TEXTURE_BINDING_2D);
            samplerBindings[i] = glGetIntegeri(GL_SAMPLER_BINDING, i);
        }
        glActiveTexture(activeTexture);
        int[] viewport = new int[4];
        glGetIntegerv(GL_VIEWPORT, viewport);
        boolean[] colorMask = new boolean[4];
        try (MemoryStack stack = MemoryStack.stackPush()) {
            var mask = stack.malloc(4);
            glGetBooleanv(GL_COLOR_WRITEMASK, mask);
            colorMask[0] = mask.get(0) != 0;
            colorMask[1] = mask.get(1) != 0;
            colorMask[2] = mask.get(2) != 0;
            colorMask[3] = mask.get(3) != 0;
        }
        int uniformBindingCount = Math.min(16, Math.max(8, glGetInteger(GL_MAX_UNIFORM_BUFFER_BINDINGS)));
        int[] uniformBufferBindings = new int[uniformBindingCount];
        for (int i = 0; i < uniformBufferBindings.length; i++) {
            uniformBufferBindings[i] = glGetIntegeri(GL_UNIFORM_BUFFER_BINDING, i);
        }
        int[] polygonMode = new int[2];
        glGetIntegerv(GL_POLYGON_MODE, polygonMode);
        boolean nvRepresentativeSupported = org.lwjgl.opengl.GL.getCapabilities().GL_NV_representative_fragment_test;
        return new OriginalVoxyRenderState(
                glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING),
                glGetInteger(GL_READ_FRAMEBUFFER_BINDING),
                viewport,
                captureShaderStorageBufferBindings(),
                textureBindings,
                samplerBindings,
                glGetInteger(GL_CURRENT_PROGRAM),
                glGetInteger(GL_VERTEX_ARRAY_BINDING),
                glIsEnabled(GL_DEPTH_TEST),
                glIsEnabled(GL_STENCIL_TEST),
                glIsEnabled(GL_BLEND),
                glIsEnabled(GL_CULL_FACE),
                glGetBoolean(GL_DEPTH_WRITEMASK),
                colorMask[0],
                colorMask[1],
                colorMask[2],
                colorMask[3],
                activeTexture,
                glGetInteger(GL_DEPTH_FUNC),
                glGetInteger(GL_BLEND_SRC_RGB),
                glGetInteger(GL_BLEND_DST_RGB),
                glGetInteger(GL_BLEND_SRC_ALPHA),
                glGetInteger(GL_BLEND_DST_ALPHA),
                glGetInteger(GL_STENCIL_FUNC),
                glGetInteger(GL_STENCIL_REF),
                glGetInteger(GL_STENCIL_VALUE_MASK),
                glGetInteger(GL_STENCIL_WRITEMASK),
                glGetInteger(GL_STENCIL_FAIL),
                glGetInteger(GL_STENCIL_PASS_DEPTH_FAIL),
                glGetInteger(GL_STENCIL_PASS_DEPTH_PASS),
                polygonMode[0],
                glGetInteger(GL_PROVOKING_VERTEX),
                glGetInteger(GL_FRONT_FACE),
                uniformBufferBindings,
                glGetInteger(GL_DRAW_INDIRECT_BUFFER_BINDING),
                glGetInteger(GL_PARAMETER_BUFFER_BINDING_ARB),
                nvRepresentativeSupported,
                nvRepresentativeSupported
                        && glIsEnabled(org.lwjgl.opengl.NVRepresentativeFragmentTest.GL_REPRESENTATIVE_FRAGMENT_TEST_NV));
    }

    private static void restoreOriginalVoxyRenderState(OriginalVoxyRenderState state) {
        if (state == null) {
            return;
        }
        glBindFramebuffer(GL_DRAW_FRAMEBUFFER, state.drawFramebuffer());
        glBindFramebuffer(GL_READ_FRAMEBUFFER, state.readFramebuffer());
        int[] viewport = state.viewport();
        if (viewport != null && viewport.length >= 4) {
            glViewport(viewport[0], viewport[1], viewport[2], viewport[3]);
        }
        glUseProgram(state.currentProgram());
        setCapability(GL_DEPTH_TEST, state.depthTestEnabled());
        setCapability(GL_STENCIL_TEST, state.stencilTestEnabled());
        setCapability(GL_BLEND, state.blendEnabled());
        setCapability(GL_CULL_FACE, state.cullEnabled());
        glDepthMask(state.depthMask());
        glColorMask(state.colorMaskR(), state.colorMaskG(), state.colorMaskB(), state.colorMaskA());
        glBindVertexArray(state.vertexArray());
        int[] textureBindings = state.textureBindings();
        int[] samplerBindings = state.samplerBindings();
        if (textureBindings != null && samplerBindings != null) {
            int count = Math.min(textureBindings.length, samplerBindings.length);
            for (int i = 0; i < count; i++) {
                glBindTextureUnit(i, textureBindings[i]);
                glBindSampler(i, samplerBindings[i]);
            }
        }
        int[] bufferBindings = state.shaderStorageBufferBindings();
        if (bufferBindings != null) {
            for (int i = 0; i < bufferBindings.length; i++) {
                glBindBufferBase(GL_SHADER_STORAGE_BUFFER, i, bufferBindings[i]);
            }
        }
        glDepthFunc(state.depthFunc());
        glBlendFuncSeparate(state.blendSrcRgb(), state.blendDstRgb(), state.blendSrcAlpha(), state.blendDstAlpha());
        glStencilFunc(state.stencilFunc(), state.stencilRef(), state.stencilValueMask());
        glStencilMask(state.stencilWriteMask());
        glStencilOp(state.stencilFail(), state.stencilPassDepthFail(), state.stencilPassDepthPass());
        glPolygonMode(GL_FRONT_AND_BACK, state.polygonMode());
        glProvokingVertex(state.provokingVertex());
        glFrontFace(state.frontFace());
        int[] uniformBindings = state.uniformBufferBindings();
        if (uniformBindings != null) {
            for (int i = 0; i < uniformBindings.length; i++) {
                glBindBufferBase(GL_UNIFORM_BUFFER, i, uniformBindings[i]);
            }
        }
        glBindBuffer(GL_DRAW_INDIRECT_BUFFER, state.drawIndirectBuffer());
        glBindBuffer(GL_PARAMETER_BUFFER_ARB, state.parameterBuffer());
        if (state.nvRepresentativeFragmentTestSupported()) {
            setCapability(
                    org.lwjgl.opengl.NVRepresentativeFragmentTest.GL_REPRESENTATIVE_FRAGMENT_TEST_NV,
                    state.nvRepresentativeFragmentTestEnabled());
        }
        glActiveTexture(state.activeTexture());
    }

    private static void setCapability(int capability, boolean enabled) {
        if (enabled) {
            glEnable(capability);
        } else {
            glDisable(capability);
        }
    }

    private record OriginalVoxyRenderState(
            int drawFramebuffer,
            int readFramebuffer,
            int[] viewport,
            int[] shaderStorageBufferBindings,
            int[] textureBindings,
            int[] samplerBindings,
            int currentProgram,
            int vertexArray,
            boolean depthTestEnabled,
            boolean stencilTestEnabled,
            boolean blendEnabled,
            boolean cullEnabled,
            boolean depthMask,
            boolean colorMaskR,
            boolean colorMaskG,
            boolean colorMaskB,
            boolean colorMaskA,
            int activeTexture,
            int depthFunc,
            int blendSrcRgb,
            int blendDstRgb,
            int blendSrcAlpha,
            int blendDstAlpha,
            int stencilFunc,
            int stencilRef,
            int stencilValueMask,
            int stencilWriteMask,
            int stencilFail,
            int stencilPassDepthFail,
            int stencilPassDepthPass,
            int polygonMode,
            int provokingVertex,
            int frontFace,
            int[] uniformBufferBindings,
            int drawIndirectBuffer,
            int parameterBuffer,
            boolean nvRepresentativeFragmentTestSupported,
            boolean nvRepresentativeFragmentTestEnabled) {
    }

    private void processRenderDistanceTrackerOnRenderThread(double cameraX, double cameraZ) {
        if (!RenderSystem.isOnRenderThread()) {
            this.recordFailure("render-distance-tracker-not-render-thread");
            return;
        }
        ForgeOriginalVoxyRenderDistanceTracker tracker;
        synchronized (this) {
            if (!this.ownerReady || this.stale || this.renderSystem == null) {
                return;
            }
            tracker = this.renderSystem.renderDistanceTracker();
        }
        tracker.setCenterAndProcess(cameraX, cameraZ);
    }

    private synchronized void recordFailure(String reason) {
        this.lifecycleGeneration++;
        this.startRequested = false;
        this.startQueuedOnRenderThread = false;
        this.startQueuedGeneration = -1L;
        this.ownerReady = false;
        this.stale = true;
        this.requiresRebuild = true;
        this.lifecycleState = "FAILED_SAFE";
        this.lastLifecycleEvent = "failure";
        this.lastFailureReason = reason == null || reason.isBlank() ? "unspecified" : reason;
    }

    private synchronized boolean isStartGenerationCurrent(long expectedGeneration) {
        if (expectedGeneration == this.lifecycleGeneration && this.startRequested && !this.ownerReady) {
            return true;
        }
        this.queuedStartStaleSkipCount++;
        this.lastQueuedLifecycleSkipReason = "start-install-stale-generation";
        this.lastLifecycleEvent = "start-install-skipped-stale-generation";
        return false;
    }

    private String lastLoggedNonFatalReason = "";

    private synchronized void recordNonFatalFailure(String reason) {
        this.lastLifecycleEvent = "non-fatal-render-failure";
        String normalized = reason == null || reason.isBlank() ? "unspecified" : reason.replace(' ', '-');
        //Dedup against the last LOGGED reason (lastFailureReason gets reset to "none" by successful
        // paths between frames, which would defeat transition-based dedup and spam the log).
        if (!normalized.equals(this.lastLoggedNonFatalReason)) {
            this.lastLoggedNonFatalReason = normalized;
            Logger.warn("Original Voxy non-fatal render failure: " + normalized);
        }
        this.lastFailureReason = normalized;
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

    private static float maxMatrixAbsDiff(Matrix4fc a, Matrix4fc b) {
        float max = 0.0F;
        max = Math.max(max, Math.abs(a.m00() - b.m00()));
        max = Math.max(max, Math.abs(a.m01() - b.m01()));
        max = Math.max(max, Math.abs(a.m02() - b.m02()));
        max = Math.max(max, Math.abs(a.m03() - b.m03()));
        max = Math.max(max, Math.abs(a.m10() - b.m10()));
        max = Math.max(max, Math.abs(a.m11() - b.m11()));
        max = Math.max(max, Math.abs(a.m12() - b.m12()));
        max = Math.max(max, Math.abs(a.m13() - b.m13()));
        max = Math.max(max, Math.abs(a.m20() - b.m20()));
        max = Math.max(max, Math.abs(a.m21() - b.m21()));
        max = Math.max(max, Math.abs(a.m22() - b.m22()));
        max = Math.max(max, Math.abs(a.m23() - b.m23()));
        max = Math.max(max, Math.abs(a.m30() - b.m30()));
        max = Math.max(max, Math.abs(a.m31() - b.m31()));
        max = Math.max(max, Math.abs(a.m32() - b.m32()));
        return Math.max(max, Math.abs(a.m33() - b.m33()));
    }

    private static float minecraftRenderDistance() {
        return Minecraft.getInstance().options.getEffectiveRenderDistance() * 16.0F;
    }

    private static String safeReason(String reason) {
        return reason == null || reason.isBlank() ? "unspecified" : reason.replace(' ', '-');
    }
}
