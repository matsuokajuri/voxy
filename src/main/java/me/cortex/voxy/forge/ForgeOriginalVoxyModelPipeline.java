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
import net.minecraft.client.Minecraft;
import net.minecraft.core.SectionPos;
import org.joml.Matrix4f;
import org.joml.FrustumIntersection;
import org.joml.Matrix4fc;
import org.lwjgl.system.MemoryStack;

import java.util.List;
import java.util.Optional;

import static org.lwjgl.opengl.GL11C.GL_VIEWPORT;
import static org.lwjgl.opengl.GL11C.GL_BLEND;
import static org.lwjgl.opengl.GL11C.GL_BACK;
import static org.lwjgl.opengl.GL11C.GL_COLOR_WRITEMASK;
import static org.lwjgl.opengl.GL11C.GL_CULL_FACE;
import static org.lwjgl.opengl.GL11C.GL_DEPTH_TEST;
import static org.lwjgl.opengl.GL11C.GL_DEPTH_WRITEMASK;
import static org.lwjgl.opengl.GL11C.GL_FRONT;
import static org.lwjgl.opengl.GL11C.GL_STENCIL_TEST;
import static org.lwjgl.opengl.GL11C.GL_TEXTURE_BINDING_2D;
import static org.lwjgl.opengl.GL11C.GL_TEXTURE_2D;
import static org.lwjgl.opengl.GL11C.GL_UNPACK_ALIGNMENT;
import static org.lwjgl.opengl.GL11C.GL_UNPACK_ROW_LENGTH;
import static org.lwjgl.opengl.GL11C.GL_UNPACK_SKIP_PIXELS;
import static org.lwjgl.opengl.GL11C.GL_UNPACK_SKIP_ROWS;
import static org.lwjgl.opengl.GL11C.glBindTexture;
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
import static org.lwjgl.opengl.GL11C.glPixelStorei;
import static org.lwjgl.opengl.GL11C.GL_FRONT_AND_BACK;
import static org.lwjgl.opengl.GL12C.GL_UNPACK_IMAGE_HEIGHT;
import static org.lwjgl.opengl.GL12C.GL_UNPACK_SKIP_IMAGES;
import static org.lwjgl.opengl.GL13C.GL_ACTIVE_TEXTURE;
import static org.lwjgl.opengl.GL13C.GL_TEXTURE0;
import static org.lwjgl.opengl.GL13C.glActiveTexture;
import static org.lwjgl.opengl.GL14C.GL_BLEND_DST_ALPHA;
import static org.lwjgl.opengl.GL14C.GL_BLEND_DST_RGB;
import static org.lwjgl.opengl.GL14C.GL_BLEND_SRC_ALPHA;
import static org.lwjgl.opengl.GL14C.GL_BLEND_SRC_RGB;
import static org.lwjgl.opengl.GL14C.glBlendFuncSeparate;
import static org.lwjgl.opengl.GL15C.glBindBuffer;
import static org.lwjgl.opengl.GL15C.GL_ARRAY_BUFFER;
import static org.lwjgl.opengl.GL15C.GL_ARRAY_BUFFER_BINDING;
import static org.lwjgl.opengl.GL31C.GL_UNIFORM_BUFFER;
import static org.lwjgl.opengl.GL31C.GL_UNIFORM_BUFFER_BINDING;
import static org.lwjgl.opengl.GL31C.GL_UNIFORM_BUFFER_SIZE;
import static org.lwjgl.opengl.GL31C.GL_UNIFORM_BUFFER_START;
import static org.lwjgl.opengl.GL32C.glGetInteger64i;
import static org.lwjgl.opengl.GL32C.GL_PROVOKING_VERTEX;
import static org.lwjgl.opengl.GL32C.glProvokingVertex;
import static org.lwjgl.opengl.GL40C.GL_DRAW_INDIRECT_BUFFER;
import static org.lwjgl.opengl.GL40C.GL_DRAW_INDIRECT_BUFFER_BINDING;
import static org.lwjgl.opengl.ARBIndirectParameters.GL_PARAMETER_BUFFER_ARB;
import static org.lwjgl.opengl.ARBIndirectParameters.GL_PARAMETER_BUFFER_BINDING_ARB;
import static org.lwjgl.opengl.GL20C.GL_CURRENT_PROGRAM;
import static org.lwjgl.opengl.GL20C.GL_STENCIL_BACK_FAIL;
import static org.lwjgl.opengl.GL20C.GL_STENCIL_BACK_FUNC;
import static org.lwjgl.opengl.GL20C.GL_STENCIL_BACK_PASS_DEPTH_FAIL;
import static org.lwjgl.opengl.GL20C.GL_STENCIL_BACK_PASS_DEPTH_PASS;
import static org.lwjgl.opengl.GL20C.GL_STENCIL_BACK_REF;
import static org.lwjgl.opengl.GL20C.GL_STENCIL_BACK_VALUE_MASK;
import static org.lwjgl.opengl.GL20C.GL_STENCIL_BACK_WRITEMASK;
import static org.lwjgl.opengl.GL20C.glStencilFuncSeparate;
import static org.lwjgl.opengl.GL20C.glStencilMaskSeparate;
import static org.lwjgl.opengl.GL20C.glStencilOpSeparate;
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
import static org.lwjgl.opengl.GL42C.GL_IMAGE_BINDING_ACCESS;
import static org.lwjgl.opengl.GL42C.GL_IMAGE_BINDING_FORMAT;
import static org.lwjgl.opengl.GL42C.GL_IMAGE_BINDING_LAYER;
import static org.lwjgl.opengl.GL42C.GL_IMAGE_BINDING_LAYERED;
import static org.lwjgl.opengl.GL42C.GL_IMAGE_BINDING_LEVEL;
import static org.lwjgl.opengl.GL42C.GL_IMAGE_BINDING_NAME;
import static org.lwjgl.opengl.GL42C.GL_PIXEL_BUFFER_BARRIER_BIT;
import static org.lwjgl.opengl.GL42C.glBindImageTexture;
import static org.lwjgl.opengl.GL42C.glMemoryBarrier;
import static org.lwjgl.opengl.GL43C.GL_DISPATCH_INDIRECT_BUFFER;
import static org.lwjgl.opengl.GL43C.GL_DISPATCH_INDIRECT_BUFFER_BINDING;
import static org.lwjgl.opengl.GL43C.GL_SHADER_STORAGE_BUFFER;
import static org.lwjgl.opengl.GL43C.GL_SHADER_STORAGE_BUFFER_BINDING;
import static org.lwjgl.opengl.GL43C.GL_SHADER_STORAGE_BUFFER_SIZE;
import static org.lwjgl.opengl.GL43C.GL_SHADER_STORAGE_BUFFER_START;
import static org.lwjgl.opengl.GL30C.glBindBufferRange;

public final class ForgeOriginalVoxyModelPipeline {
    private final ForgeVoxyInstance instance;
    private final UnifiedServiceThreadPool serviceThreadPool = new UnifiedServiceThreadPool();
    //The full outer lifecycle owner (original VoxyRenderSystem port); null until started.
    private ForgeOriginalVoxyRenderSystem renderSystem;
    private final LongOpenHashSet pendingChunkBoundAdds = new LongOpenHashSet();
    private final LongOpenHashSet pendingChunkBoundRemoves = new LongOpenHashSet();
    private boolean startRequested;
    private boolean startQueuedOnRenderThread;
    private boolean ownerReady;
    private boolean stale;
    private long lifecycleGeneration;
    private long startQueuedGeneration = -1L;
    private boolean oculusReloadFlagCurrentlyObserved;
    private boolean oculusReloadDuplicatePollLogged;
    private boolean originalServiceThreadConfigOwnerReady;
    private boolean originalEmbeddiumBuilderThreadSharingEnabled;
    private String originalServiceThreadPolicyFailureReason = "none";
    private String lastLifecycleEvent = "initialized";
    private boolean renderEmbeddiumCutoutActive;
    private boolean serviceThreadPoolShutdown;
    private long chunkBoundOwnerGeneration;
    private long preparedOculusViewportGeneration = Long.MIN_VALUE;
    private ForgeOriginalVoxyRenderSystem preparedOculusViewportOwner;
    ForgeOriginalVoxyModelPipeline(ForgeVoxyInstance instance) {
        this.instance = instance;
    }

    /** Mirrors original MixinIrisRenderingPipeline#voxy$injectViewportSetup. */
    public void prepareOculusViewportFromCapturedState() {
        if (!RenderSystem.isOnRenderThread()
                || !ForgeOriginalVoxyOculusPipelineBridge.shaderpackActive()
                || ForgeOriginalVoxyOculusPipelineBridge.shadowActive()) {
            return;
        }
        ForgeOriginalVoxyRenderStateCapture.CapturedViewport captured =
                ForgeOriginalVoxyRenderStateCapture.viewportCopy();
        if (captured == null) {
            return;
        }
        ForgeOriginalVoxyRenderSystem currentRenderSystem;
        synchronized (this) {
            if (!this.ownerReady || this.stale || this.renderSystem == null) {
                return;
            }
            currentRenderSystem = this.renderSystem;
        }
        ViewportSelector selector = currentRenderSystem.viewportSelector();
        MDICViewport viewport = selector == null ? null : selector.getViewport();
        if (viewport == null) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        int width = minecraft.getMainRenderTarget().width;
        int height = minecraft.getMainRenderTarget().height;
        float[] renderScale = currentRenderSystem.renderPipeline().renderScalingFactor();
        if (renderScale != null) {
            width = (int) (width * renderScale[0]);
            height = (int) (height * renderScale[1]);
        }
        if (width <= 0 || height <= 0) {
            return;
        }
        Matrix4f vanillaProjection = new Matrix4f(captured.projection());
        Matrix4f voxyProjection = computeProjectionMat(
                currentRenderSystem.renderPipeline().properties(),
                vanillaProjection,
                captured.projection());
        viewport.setVanillaProjection(vanillaProjection)
                .setProjection(voxyProjection)
                .setModelView(captured.modelView())
                .setCamera(captured.cameraX(), captured.cameraY(), captured.cameraZ())
                .setScreenSize(width, height)
                .setFogParameters(ForgeOriginalVoxyFogParameters.captureFromRenderSystem(
                        ForgeVoxyConfig.ORIGINAL_VOXY_USE_ENVIRONMENTAL_FOG.get()))
                .update();
        viewport.frameId++;
        this.preparedOculusViewportGeneration = captured.generation();
        this.preparedOculusViewportOwner = currentRenderSystem;
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

    void refreshOriginalServiceThreadPolicy() {
        this.updateDedicatedThreads();
    }

    synchronized void updateOriginalRenderDistance(float renderDistance) {
        if (this.renderSystem != null && this.ownerReady && !this.stale) {
            this.renderSystem.setRenderDistance(renderDistance);
        }
    }

    void addDebugInfo(List<String> debug) {
        ForgeOriginalVoxyRenderSystem active;
        synchronized (this) {
            active = this.ownerReady && !this.stale ? this.renderSystem : null;
        }
        if (active != null) {
            active.addDebugInfo(debug);
        }
    }

    void shutdownForClientStop(Runnable continuation) {
        this.markStaleAndClear(
                RenderSystem.isOnRenderThread() ? "client-shutdown" : "client-shutdown-deferred",
                continuation);
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

    synchronized void requestStart(String reason) {
        this.lifecycleGeneration++;
        this.startRequested = true;
        this.stale = false;
        this.startQueuedGeneration = -1L;
        this.lastLifecycleEvent = safeReason(reason);
    }

    void clientTick() {
        if (!ForgeVoxyConfig.ENABLED.get() || !ForgeVoxyConfig.RENDERING_ENABLED.get()) {
            boolean hasOwnerOrPendingStart;
            synchronized (this) {
                hasOwnerOrPendingStart = this.ownerReady
                        || this.startRequested
                        || this.startQueuedOnRenderThread
                        || this.renderSystem != null;
            }
            if (hasOwnerOrPendingStart) {
                this.markStaleAndClear("rendering-disabled");
            }
            return;
        }
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
        if (renderSystem != null && renderSystem.modelService().hasPendingUploads()) {
            this.runOnRenderThread(() -> this.processFactoryUploads(renderSystem));
        }
    }

    synchronized void resetChunkBoundTracker() {
        this.pendingChunkBoundAdds.clear();
        this.pendingChunkBoundRemoves.clear();
        if (this.renderSystem != null) {
            this.renderSystem.chunkBoundRenderer().reset();
        }
    }

    synchronized boolean isChunkBoundTrackerActive() {
        return this.ownerReady && !this.stale && this.renderSystem != null;
    }

    synchronized long chunkBoundOwnerGeneration() {
        return this.ownerReady && !this.stale && this.renderSystem != null
                ? this.chunkBoundOwnerGeneration
                : -1L;
    }

    synchronized boolean hasActiveRenderOwner() {
        return this.ownerReady && !this.stale && this.renderSystem != null;
    }

    void trackChunkBoundSection(boolean wasBuilt, int x, int y, int z) {
        long pos = SectionPos.asLong(x, y, z);
        ChunkBoundRenderer renderer;
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

    private void replayPendingChunkBoundSections(ChunkBoundRenderer renderer) {
        this.pendingChunkBoundRemoves.forEach(renderer::removeSection);
        this.pendingChunkBoundAdds.forEach(renderer::addSection);
        this.pendingChunkBoundRemoves.clear();
        this.pendingChunkBoundAdds.clear();
    }


    void markResourceReload() {
        this.markStaleAndClear("resource-reload");
    }

    void markConfigurationReload() {
        boolean shouldRestart;
        synchronized (this) {
            shouldRestart = this.ownerReady || this.startRequested || this.renderSystem != null;
        }
        if (!shouldRestart) {
            return;
        }
        this.markStaleAndClear("configuration-reload");
        if (ForgeVoxyConfig.ENABLED.get() && ForgeVoxyConfig.RENDERING_ENABLED.get()) {
            this.requestStart("configuration-reload");
        }
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
                this.lastLifecycleEvent = "oculus-world-rendering-settings-reload";
                return;
            }
        }
        this.markStaleAndClear("oculus-world-rendering-settings-reload");
        if (shouldRestart) {
            this.requestStart("oculus-world-rendering-settings-reload");
        }
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
        if (minecraft.level == null
                || minecraft.player == null
                || !ForgeVoxyConfig.ENABLED.get()
                || !ForgeVoxyConfig.RENDERING_ENABLED.get()) {
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
                if (!this.oculusReloadDuplicatePollLogged) {
                    this.oculusReloadDuplicatePollLogged = true;
                    Logger.info("Debounced repeated Oculus WorldRenderingSettings reload flag while waiting for Oculus to consume it.");
                }
                return false;
            }
            this.oculusReloadFlagCurrentlyObserved = true;
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
        if (!ForgeVoxyConfig.ENABLED.get() || !ForgeVoxyConfig.RENDERING_ENABLED.get()) {
            this.markStaleAndClear("rendering-disabled-before-start");
            return;
        }
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
            // normal path; without a shaderpack the original rethrows the construction failure.
            if (ForgeOriginalVoxyOculusPipelineBridge.shaderpackActive()) {
                ForgeOriginalVoxyOculusPipelineBridge.disableShaders();
                this.recordFailure("original-render-system-construction-" + e.getClass().getSimpleName() + ":" + e.getMessage());
                return;
            }
            throw e;
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
            this.startRequested = false;
            this.stale = false;
            this.chunkBoundOwnerGeneration++;
            this.lastLifecycleEvent = "start-on-render-thread";
            this.replayPendingChunkBoundSections(renderSystem.chunkBoundRenderer());
        }
    }

    public void renderEmbeddiumCutout(
            ChunkRenderMatrices matrices,
            double cameraX,
            double cameraY,
            double cameraZ) {
        this.renderEmbeddiumCutout(matrices, cameraX, cameraY, cameraZ, null);
    }

    public void renderEmbeddiumCutout(
            ChunkRenderMatrices matrices,
            double cameraX,
            double cameraY,
            double cameraZ,
            FrustumIntersection suppliedFrustum) {
        if (!ForgeVoxyConfig.isEnabledEarlySafe()) {
            return;
        }
        if (!RenderSystem.isOnRenderThread()) {
            this.recordNonFatalFailure("original-hoc-embeddium-cutout-not-render-thread");
            return;
        }
        if (this.renderEmbeddiumCutoutActive) {
            this.recordNonFatalFailure("original-hoc-embeddium-cutout-reentrant");
            return;
        }
        this.renderEmbeddiumCutoutActive = true;
        boolean capturedRenderState = false;
        boolean postDynamicWorkEligible = false;
        boolean timingStarted = false;
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
            MDICViewport viewport;
            ViewportSelector selector = renderSystem.viewportSelector();
            HierarchicalOcclusionTraverser traversal = renderSystem.traversal();
            MDICSectionRenderer sectionRenderer = renderSystem.sectionRenderer();
            ForgeOriginalVoxyRenderPipeline renderPipeline = renderSystem.renderPipeline();
            ChunkBoundRenderer chunkBoundRenderer = renderSystem.chunkBoundRenderer();
            BasicSectionGeometryData geometryData = renderSystem.geometryData();
            AsyncNodeManager geometrySync = renderSystem.nodeManager();
            NodeCleaner cleaner = renderSystem.nodeCleaner();
            RenderGenerationService renderGeneration = renderSystem.renderGenerationService();
            ModelBakerySubsystem modelBakery = renderSystem.modelService();
            ModelStore modelStore = modelBakery.getStore();
            if (renderPipeline.oculusPipelineGenerationStale()) {
                //Oculus swapped its pipeline after this owner captured its pipeline data (the
                // Forge reload-edge rebuild can race Oculus's lazy pipeline creation, unlike the
                // original allChanged ordering where Iris creates the pipeline first). Rendering
                // with the stale capture draws against destroyed shaderpack targets, so restart
                // the owner against the live pipeline instead.
                Logger.info("Oculus pipeline generation changed since Voxy owner capture, restarting the owner.");
                this.markStaleAndClear("oculus-pipeline-generation-mismatch");
                this.requestStart("oculus-pipeline-generation-mismatch");
                return;
            }
            viewport = selector == null ? null : selector.getViewport();
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.level == null || minecraft.player == null || matrices == null) {
                return;
            }
            if (viewport == null || traversal == null || sectionRenderer == null || renderPipeline == null || chunkBoundRenderer == null || geometryData == null
                    || modelStore == null || !traversal.ready()) {
                //A null viewport during the Oculus shadow pass is the expected per-frame skip, not
                // a failure state.
                if (viewport == null && selector != null && selector.lastSelectionWasExpectedSkip()) {
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
            TimingStatistics.resetSamplers();
            TimingStatistics.all.start();
            GPUTiming.INSTANCE.marker();
            TimingStatistics.main.start();
            timingStarted = true;
            oldRenderState = captureOriginalVoxyRenderState(renderPipeline);
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
            ForgeOriginalVoxyRenderStateCapture.CapturedViewport capturedViewport =
                    ForgeOriginalVoxyRenderStateCapture.viewportCopy();
            boolean usePreparedOculusViewport = ForgeOriginalVoxyOculusPipelineBridge.shaderpackActive()
                    && capturedViewport != null
                    && this.preparedOculusViewportOwner == renderSystem
                    && this.preparedOculusViewportGeneration == capturedViewport.generation();
            if (usePreparedOculusViewport) {
                // The original Iris path only refreshes the viewport once in beginLevelRendering;
                // preserve those camera matrices and merely take the now-current terrain fog.
                viewport.setFogParameters(fogParameters);
            } else {
                Matrix4f vanillaProjection = new Matrix4f(matrices.projection());
                Matrix4f modelView = new Matrix4f(matrices.modelView());
                Matrix4f rawMinecraftProjection = ForgeOriginalVoxyRenderStateCapture.projectionCopy();
                if (rawMinecraftProjection == null) {
                    this.recordNonFatalFailure("original-hoc-raw-projection-not-captured");
                    return;
                }
                Matrix4f voxyProjection = computeProjectionMat(
                        RenderProperties.getRenderProperties(),
                        vanillaProjection,
                        rawMinecraftProjection);
                viewport.setVanillaProjection(vanillaProjection)
                        .setProjection(voxyProjection)
                        .setModelView(modelView)
                        .setCamera(cameraX, cameraY, cameraZ)
                        .setScreenSize(width, height)
                        .setFogParameters(fogParameters)
                        .update();
                viewport.frameId++;
            }
            if (suppliedFrustum != null) {
                viewport.copyFrustumFrom(suppliedFrustum);
            }
            glViewport(0, 0, viewport.width, viewport.height);
            renderPipeline.preSetup(viewport);
            TimingStatistics.E.start();
            try {
                chunkBoundRenderer.render(viewport, originalFrexActive());
            } finally {
                TimingStatistics.E.stopIfRunning();
            }
            GPUTiming.INSTANCE.marker();
            int depthTexture = renderPipeline.setup(
                    viewport,
                    oldFramebuffer,
                    oldRenderState.readFramebuffer(),
                    sourceWidth,
                    sourceHeight);
            postDynamicWorkEligible = true;
            postDynamicCameraX = cameraX;
            postDynamicCameraZ = cameraZ;
            GPUTiming.INSTANCE.marker("RO");
            sectionRenderer.renderOpaque(viewport, geometryData, modelStore, renderPipeline);
            viewport.buildHizFromSourceDepth(depthTexture, width, height);
            GPUTiming.INSTANCE.marker("I");
            this.runOriginalInnerPrimaryWorkWithTraversal(
                    viewport,
                    geometrySync,
                    cleaner,
                    traversal,
                    renderGeneration,
                    renderSystem);
            GPUTiming.INSTANCE.marker();
            sectionRenderer.buildDrawCalls(viewport, geometryData, RenderProperties.getRenderProperties());
            GPUTiming.INSTANCE.marker("TP");
            sectionRenderer.renderTemporal(viewport, geometryData, modelStore, renderPipeline);
            sectionRenderer.postOpaquePreperation(viewport);
            renderPipeline.postOpaquePreTranslucent(viewport, oldFramebuffer, true);
            GPUTiming.INSTANCE.marker("RT");
            sectionRenderer.renderTranslucent(viewport, geometryData, modelStore, renderPipeline);
            GPUTiming.INSTANCE.marker();
            renderPipeline.finish(viewport, oldFramebuffer, sourceWidth, sourceHeight, true);
            GPUTiming.INSTANCE.marker();
            synchronized (this) {
                if (this.ownerReady && !this.stale) {
                    this.lastLifecycleEvent = "embeddium-cutout-original-run-pipeline-order";
                }
            }
        } finally {
            try {
                if (timingStarted) {
                    TimingStatistics.main.stopIfRunning();
                    TimingStatistics.postDynamic.start();
                }
                try {
                    if (postDynamicWorkEligible) {
                        this.runOriginalPostDynamicWorkAfterCommandGeneration(postDynamicCameraX, postDynamicCameraZ);
                    }
                } finally {
                    if (capturedRenderState) {
                        restoreOriginalVoxyRenderState(oldRenderState);
                    }
                }
            } finally {
                if (timingStarted) {
                    GPUTiming.INSTANCE.marker();
                    TimingStatistics.postDynamic.stopIfRunning();
                    GPUTiming.INSTANCE.tick();
                    TimingStatistics.all.stopIfRunning();
                }
                this.renderEmbeddiumCutoutActive = false;
            }
        }
    }

    public MultiThreadPrioritySemaphore.Block createEmbeddiumBuilderSemaphoreBlock() {
        if (!ForgeVoxyConfig.ENABLED.get() || !this.instance.isSessionRuntimeActive()) {
            return null;
        }
        this.updateDedicatedThreads();
        if (!this.originalEmbeddiumBuilderThreadSharingEnabled) {
            return null;
        }
        return this.serviceThreadPool.groupSemaphore.createBlock();
    }

    private synchronized void updateDedicatedThreads() {
        if (!ForgeVoxyConfig.ENABLED.get() || !this.instance.isSessionRuntimeActive()) {
            this.originalServiceThreadConfigOwnerReady = false;
            this.originalEmbeddiumBuilderThreadSharingEnabled = false;
            this.originalServiceThreadPolicyFailureReason = "no-active-voxy-session";
            return;
        }
        if (this.serviceThreadPoolShutdown) {
            this.originalServiceThreadConfigOwnerReady = false;
            this.originalServiceThreadPolicyFailureReason = "service-thread-pool-shutdown";
            return;
        }
        ForgeOriginalVoxyServiceThreadPolicy.Selection selection = ForgeOriginalVoxyServiceThreadPolicy.select();
        this.originalServiceThreadConfigOwnerReady = selection.configOwnerReady();
        this.originalEmbeddiumBuilderThreadSharingEnabled = selection.useEmbeddiumBuilderThreads();
        this.originalServiceThreadPolicyFailureReason = selection.failureReason();
        if (!selection.configOwnerReady()) {
            return;
        }
        if (this.serviceThreadPool.setNumThreads(selection.dedicatedThreadCount())) {
            Logger.info("Dedicated voxy thread pool size: " + selection.dedicatedThreadCount());
        }
    }

    private void markStaleAndClear(String event) {
        this.markStaleAndClear(event, null);
    }

    private void markStaleAndClear(String event, Runnable continuation) {
        long cleanupGeneration;
        ForgeOriginalVoxyRenderSystem renderSystem;
        synchronized (this) {
            this.lifecycleGeneration++;
            cleanupGeneration = this.lifecycleGeneration;
            this.startRequested = false;
            this.startQueuedOnRenderThread = false;
            this.startQueuedGeneration = -1L;
            this.ownerReady = false;
            this.pendingChunkBoundAdds.clear();
            this.pendingChunkBoundRemoves.clear();
            this.stale = true;
            this.lastLifecycleEvent = safeReason(event);
            renderSystem = this.renderSystem;
            this.renderSystem = null;
        }
        ForgeOriginalVoxyRenderStateCapture.clear();
        this.runOnRenderThread(() -> {
            try {
                boolean cleanupCurrent;
                synchronized (this) {
                    cleanupCurrent = cleanupGeneration == this.lifecycleGeneration || !this.ownerReady;
                    if (!cleanupCurrent) {
                        this.lastLifecycleEvent = "cleanup-skipped-global-flush-stale-generation";
                    }
                }
                if (renderSystem != null) {
                    //Full original VoxyRenderSystem.shutdown() order: flush, callbacks, node manager,
                    // model bakery, render generation, traversal, cleaner, geometry, chunk-bound,
                    // viewport selector, pipeline last, flush, release world ref. The global download
                    // stream flush is skipped when a newer lifecycle generation already owns it.
                    renderSystem.shutdown(cleanupCurrent);
                } else if (cleanupCurrent && DownloadStream.isReady()) {
                    DownloadStream.instance().flushWaitClear();
                }
            } finally {
                if (continuation != null) {
                    continuation.run();
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
        if (!RenderSystem.isOnRenderThread()) {
            throw new IllegalStateException("Original Voxy model factory uploads must run on the render thread");
        }
        synchronized (this) {
            if (!this.ownerReady || this.stale || renderSystem != this.renderSystem) {
                this.lastLifecycleEvent = "factory-upload-skipped-stale-generation";
                return;
            }
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            return;
        }
        //Original ModelBakerySubsystem.tick(): worker death propagates to the render caller.
        try {
            renderSystem.modelService().tick();
        } catch (RuntimeException e) {
            //The factory retains the failed native payload. Resolve every process-global upload
            // target while its buffers still exist, then let factory.free() release that payload
            // exactly once during owner teardown. Cleanup failures never replace the root cause.
            throw preserveFactoryTickFailure(
                    e,
                    () -> {
                        if (UploadStream.isReady()) {
                            UploadStream.instance().commitPendingCopiesBeforeOwnerTeardown();
                        }
                    },
                    () -> this.markStaleAndClear("model-factory-tick-failure-" + e.getClass().getSimpleName()));
        }
    }

    static RuntimeException preserveFactoryTickFailure(
            RuntimeException failure,
            Runnable pendingUploadCleanup,
            Runnable ownerCleanup) {
        runCleanupSuppressingFailure(failure, pendingUploadCleanup);
        runCleanupSuppressingFailure(failure, ownerCleanup);
        return failure;
    }

    private static void runCleanupSuppressingFailure(RuntimeException failure, Runnable cleanup) {
        try {
            cleanup.run();
        } catch (Throwable cleanupFailure) {
            if (cleanupFailure != failure) {
                failure.addSuppressed(cleanupFailure);
            }
        }
    }

    private void runOriginalInnerPrimaryWorkBeforeTraversal(
            AsyncNodeManager geometrySync,
            NodeCleaner cleaner,
            HierarchicalOcclusionTraverser traversal) {
        if (DownloadStream.isReady()) {
            DownloadStream.instance().tick();
        }
        if (geometrySync != null && traversal != null) {
            geometrySync.tick(traversal.getNodeBuffer(), cleaner);
        }
        if (cleaner != null && traversal != null) {
            cleaner.tick(traversal.getNodeBuffer());
        }
        glMemoryBarrier(GL_FRAMEBUFFER_BARRIER_BIT | GL_PIXEL_BUFFER_BARRIER_BIT);
    }

    private void runOriginalInnerPrimaryWorkWithTraversal(
            MDICViewport viewport,
            AsyncNodeManager geometrySync,
            NodeCleaner cleaner,
            HierarchicalOcclusionTraverser traversal,
            RenderGenerationService renderGeneration,
            ForgeOriginalVoxyRenderSystem renderSystem) {
        int iterations = 0;
        do {
            this.runOriginalInnerPrimaryWorkBeforeTraversal(geometrySync, cleaner, traversal);
            traversal.doTraversal(viewport);
            iterations++;
        } while (iterations < 16 && this.originalFrexStillHasWork(geometrySync, renderGeneration, renderSystem));
    }

    private boolean originalFrexStillHasWork(
            AsyncNodeManager geometrySync,
            RenderGenerationService renderGeneration,
            ForgeOriginalVoxyRenderSystem renderSystem) {
        if (!originalFrexActive()) {
            return false;
        }
        if (UploadStream.isReady()) {
            UploadStream.instance().tick();
        }
        if (renderSystem != null) {
            this.processFactoryUploads(renderSystem);
        }
        glFinish();
        return geometrySync != null && geometrySync.hasWork()
                || renderGeneration != null && renderGeneration.getTaskCount() != 0
                || renderSystem != null && !renderSystem.modelService().areQueuesEmpty();
    }

    private static boolean originalFrexActive() {
        //FREX flawless-frames is a Fabric entrypoint contract. There is no Forge 1.20.1
        //equivalent in the project dependency/runtime surface, so this optional loop is N/A.
        return false;
    }

    private static boolean activeShaderpackMissingBlockStateIds(ForgeOculusWorldRenderingSettingsBridge.Result blockStateIds) {
        return blockStateIds.blockStateIds() == null && ForgeOriginalVoxyOculusPipelineBridge.shaderpackActive();
    }

    private void runOriginalPostDynamicWorkAfterCommandGeneration(double cameraX, double cameraZ) {
        PrintfDebugUtil.tick();
        if (UploadStream.isReady()) {
            UploadStream.instance().tick();
        }
        this.processRenderDistanceTrackerOnRenderThread(cameraX, cameraZ);
        ForgeOriginalVoxyRenderSystem renderSystem;
        synchronized (this) {
            renderSystem = this.ownerReady && !this.stale ? this.renderSystem : null;
        }
        if (renderSystem != null) {
            this.processFactoryUploads(renderSystem);
        }
    }

    private static IndexedBufferBindings captureIndexedBufferBindings(
            int[] bindingIndices,
            int bindingPname,
            int startPname,
            int sizePname) {
        int[] buffers = new int[bindingIndices.length];
        long[] starts = new long[bindingIndices.length];
        long[] sizes = new long[bindingIndices.length];
        for (int i = 0; i < bindingIndices.length; i++) {
            int bindingIndex = bindingIndices[i];
            buffers[i] = glGetIntegeri(bindingPname, bindingIndex);
            starts[i] = glGetInteger64i(startPname, bindingIndex);
            sizes[i] = glGetInteger64i(sizePname, bindingIndex);
        }
        return new IndexedBufferBindings(bindingIndices, buffers, starts, sizes);
    }

    private static void restoreIndexedBufferBindings(int target, IndexedBufferBindings bindings) {
        if (bindings == null) {
            return;
        }
        for (int i = 0; i < bindings.buffers().length; i++) {
            int bindingIndex = bindings.bindingIndices()[i];
            int buffer = bindings.buffers()[i];
            long start = bindings.starts()[i];
            long size = bindings.sizes()[i];
            if (buffer == 0 || size <= 0L) {
                glBindBufferBase(target, bindingIndex, buffer);
            } else {
                //The GL exposes the effective indexed range, not whether Base or Range created it.
                //Rebinding that queried range reproduces the indexed state exactly.
                glBindBufferRange(target, bindingIndex, buffer, start, size);
            }
        }
    }

    private static StencilFaceState captureStencilFace(boolean back) {
        return new StencilFaceState(
                glGetInteger(back ? GL_STENCIL_BACK_FUNC : GL_STENCIL_FUNC),
                glGetInteger(back ? GL_STENCIL_BACK_REF : GL_STENCIL_REF),
                glGetInteger(back ? GL_STENCIL_BACK_VALUE_MASK : GL_STENCIL_VALUE_MASK),
                glGetInteger(back ? GL_STENCIL_BACK_WRITEMASK : GL_STENCIL_WRITEMASK),
                glGetInteger(back ? GL_STENCIL_BACK_FAIL : GL_STENCIL_FAIL),
                glGetInteger(back ? GL_STENCIL_BACK_PASS_DEPTH_FAIL : GL_STENCIL_PASS_DEPTH_FAIL),
                glGetInteger(back ? GL_STENCIL_BACK_PASS_DEPTH_PASS : GL_STENCIL_PASS_DEPTH_PASS));
    }

    private static void restoreStencilFace(int face, StencilFaceState state) {
        glStencilFuncSeparate(face, state.func(), state.ref(), state.valueMask());
        glStencilMaskSeparate(face, state.writeMask());
        glStencilOpSeparate(face, state.fail(), state.passDepthFail(), state.passDepthPass());
    }

    private static ImageUnitState captureImageUnit(int unit) {
        return new ImageUnitState(
                glGetIntegeri(GL_IMAGE_BINDING_NAME, unit),
                glGetIntegeri(GL_IMAGE_BINDING_LEVEL, unit),
                glGetIntegeri(GL_IMAGE_BINDING_LAYERED, unit) != 0,
                glGetIntegeri(GL_IMAGE_BINDING_LAYER, unit),
                glGetIntegeri(GL_IMAGE_BINDING_ACCESS, unit),
                glGetIntegeri(GL_IMAGE_BINDING_FORMAT, unit));
    }

    private static OriginalVoxyRenderState captureOriginalVoxyRenderState(
            ForgeOriginalVoxyRenderPipeline renderPipeline) {
        int activeTexture = glGetInteger(GL_ACTIVE_TEXTURE);
        int textureUnitCount = renderPipeline.embeddiumTextureBindingCount();
        int[] textureBindings = new int[textureUnitCount];
        int[] samplerBindings = new int[textureUnitCount];
        for (int i = 0; i < textureBindings.length; i++) {
            glActiveTexture(GL_TEXTURE0 + i);
            textureBindings[i] = glGetInteger(GL_TEXTURE_BINDING_2D);
            samplerBindings[i] = glGetIntegeri(GL_SAMPLER_BINDING, i);
        }
        glActiveTexture(activeTexture);
        ForgeOriginalVoxyTextureBindings.Binding[] oculusNon2DTextureBindings =
                renderPipeline.captureOculusNon2DTextureBindings();
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
        IndexedBufferBindings uniformBufferBindings = captureIndexedBufferBindings(
                renderPipeline.embeddiumUniformBufferBindingIndices(),
                GL_UNIFORM_BUFFER_BINDING,
                GL_UNIFORM_BUFFER_START,
                GL_UNIFORM_BUFFER_SIZE);
        IndexedBufferBindings shaderStorageBufferBindings = captureIndexedBufferBindings(
                renderPipeline.embeddiumShaderStorageBufferBindingIndices(),
                GL_SHADER_STORAGE_BUFFER_BINDING,
                GL_SHADER_STORAGE_BUFFER_START,
                GL_SHADER_STORAGE_BUFFER_SIZE);
        int[] polygonMode = new int[2];
        glGetIntegerv(GL_POLYGON_MODE, polygonMode);
        StencilFaceState frontStencil = captureStencilFace(false);
        StencilFaceState backStencil = captureStencilFace(true);
        boolean nvRepresentativeSupported = org.lwjgl.opengl.GL.getCapabilities().GL_NV_representative_fragment_test;
        return new OriginalVoxyRenderState(
                glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING),
                glGetInteger(GL_READ_FRAMEBUFFER_BINDING),
                viewport,
                shaderStorageBufferBindings,
                uniformBufferBindings,
                glGetInteger(GL_SHADER_STORAGE_BUFFER_BINDING),
                glGetInteger(GL_UNIFORM_BUFFER_BINDING),
                glGetInteger(GL_ARRAY_BUFFER_BINDING),
                glGetInteger(GL_DRAW_INDIRECT_BUFFER_BINDING),
                glGetInteger(GL_DISPATCH_INDIRECT_BUFFER_BINDING),
                glGetInteger(GL_PARAMETER_BUFFER_BINDING_ARB),
                textureBindings,
                samplerBindings,
                oculusNon2DTextureBindings,
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
                frontStencil,
                backStencil,
                polygonMode[0],
                glGetInteger(GL_PROVOKING_VERTEX),
                glGetInteger(GL_FRONT_FACE),
                glGetInteger(GL_UNPACK_ROW_LENGTH),
                glGetInteger(GL_UNPACK_SKIP_PIXELS),
                glGetInteger(GL_UNPACK_SKIP_ROWS),
                glGetInteger(GL_UNPACK_IMAGE_HEIGHT),
                glGetInteger(GL_UNPACK_SKIP_IMAGES),
                glGetInteger(GL_UNPACK_ALIGNMENT),
                captureImageUnit(0),
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
                glActiveTexture(GL_TEXTURE0 + i);
                glBindTexture(GL_TEXTURE_2D, textureBindings[i]);
                glBindSampler(i, samplerBindings[i]);
            }
        }
        ForgeOriginalVoxyTextureBindings.restore(state.oculusNon2DTextureBindings());
        restoreIndexedBufferBindings(GL_SHADER_STORAGE_BUFFER, state.shaderStorageBufferBindings());
        restoreIndexedBufferBindings(GL_UNIFORM_BUFFER, state.uniformBufferBindings());
        //Range/Base mutate the corresponding generic binding, so restore generic state last.
        glBindBuffer(GL_SHADER_STORAGE_BUFFER, state.shaderStorageBufferBinding());
        glBindBuffer(GL_UNIFORM_BUFFER, state.uniformBufferBinding());
        glBindBuffer(GL_ARRAY_BUFFER, state.arrayBuffer());
        glBindBuffer(GL_DRAW_INDIRECT_BUFFER, state.drawIndirectBuffer());
        glBindBuffer(GL_DISPATCH_INDIRECT_BUFFER, state.dispatchIndirectBuffer());
        glBindBuffer(GL_PARAMETER_BUFFER_ARB, state.parameterBuffer());
        glPixelStorei(GL_UNPACK_ROW_LENGTH, state.unpackRowLength());
        glPixelStorei(GL_UNPACK_SKIP_PIXELS, state.unpackSkipPixels());
        glPixelStorei(GL_UNPACK_SKIP_ROWS, state.unpackSkipRows());
        glPixelStorei(GL_UNPACK_IMAGE_HEIGHT, state.unpackImageHeight());
        glPixelStorei(GL_UNPACK_SKIP_IMAGES, state.unpackSkipImages());
        glPixelStorei(GL_UNPACK_ALIGNMENT, state.unpackAlignment());
        ImageUnitState imageUnit0 = state.imageUnit0();
        glBindImageTexture(
                0,
                imageUnit0.texture(),
                imageUnit0.level(),
                imageUnit0.layered(),
                imageUnit0.layer(),
                imageUnit0.access(),
                imageUnit0.format());
        glDepthFunc(state.depthFunc());
        glBlendFuncSeparate(state.blendSrcRgb(), state.blendDstRgb(), state.blendSrcAlpha(), state.blendDstAlpha());
        restoreStencilFace(GL_FRONT, state.frontStencil());
        restoreStencilFace(GL_BACK, state.backStencil());
        //Core profile only permits FRONT_AND_BACK here; its two queried modes are therefore equal.
        glPolygonMode(GL_FRONT_AND_BACK, state.polygonMode());
        glProvokingVertex(state.provokingVertex());
        glFrontFace(state.frontFace());
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
            IndexedBufferBindings shaderStorageBufferBindings,
            IndexedBufferBindings uniformBufferBindings,
            int shaderStorageBufferBinding,
            int uniformBufferBinding,
            int arrayBuffer,
            int drawIndirectBuffer,
            int dispatchIndirectBuffer,
            int parameterBuffer,
            int[] textureBindings,
            int[] samplerBindings,
            ForgeOriginalVoxyTextureBindings.Binding[] oculusNon2DTextureBindings,
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
            StencilFaceState frontStencil,
            StencilFaceState backStencil,
            int polygonMode,
            int provokingVertex,
            int frontFace,
            int unpackRowLength,
            int unpackSkipPixels,
            int unpackSkipRows,
            int unpackImageHeight,
            int unpackSkipImages,
            int unpackAlignment,
            ImageUnitState imageUnit0,
            boolean nvRepresentativeFragmentTestSupported,
            boolean nvRepresentativeFragmentTestEnabled) {
    }

    private record IndexedBufferBindings(int[] bindingIndices, int[] buffers, long[] starts, long[] sizes) {
    }

    private record StencilFaceState(
            int func,
            int ref,
            int valueMask,
            int writeMask,
            int fail,
            int passDepthFail,
            int passDepthPass) {
    }

    private record ImageUnitState(
            int texture,
            int level,
            boolean layered,
            int layer,
            int access,
            int format) {
    }

    private void processRenderDistanceTrackerOnRenderThread(double cameraX, double cameraZ) {
        if (!RenderSystem.isOnRenderThread()) {
            this.recordFailure("render-distance-tracker-not-render-thread");
            return;
        }
        RenderDistanceTracker tracker;
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
        this.lastLifecycleEvent = "failure";
        Logger.error("Original Voxy render pipeline failure: "
                + (reason == null || reason.isBlank() ? "unspecified" : reason));
    }

    private synchronized boolean isStartGenerationCurrent(long expectedGeneration) {
        if (expectedGeneration == this.lifecycleGeneration && this.startRequested && !this.ownerReady) {
            return true;
        }
        this.lastLifecycleEvent = "start-install-skipped-stale-generation";
        return false;
    }

    private String lastLoggedNonFatalReason = "";

    private synchronized void recordNonFatalFailure(String reason) {
        String normalized = reason == null || reason.isBlank() ? "unspecified" : reason.replace(' ', '-');
        if (!normalized.equals(this.lastLoggedNonFatalReason)) {
            this.lastLoggedNonFatalReason = normalized;
            Logger.warn("Original Voxy non-fatal render failure: " + normalized);
        }
    }

    private static Matrix4f computeProjectionMat(
            RenderProperties properties,
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
