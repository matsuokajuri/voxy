package me.cortex.voxy.forge;

import com.mojang.blaze3d.systems.RenderSystem;
import me.cortex.voxy.common.util.MemoryBuffer;
import me.cortex.voxy.common.thread.MultiThreadPrioritySemaphore;
import me.cortex.voxy.common.thread.ServiceManager;
import me.cortex.voxy.common.world.WorldEngine;
import me.cortex.voxy.common.world.service.SectionSavingService;
import me.cortex.voxy.common.world.service.VoxelIngestService;
import me.cortex.voxy.commonImpl.ImportManager;
import me.cortex.voxy.config.ForgeVoxyConfig;
import net.minecraft.client.Minecraft;
import org.joml.FrustumIntersection;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.CustomizeGuiOverlayEvent;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.GameShuttingDownEvent;
import net.minecraftforge.event.TickEvent;

import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.List;

public final class ForgeVoxyInstance {
    public static final ForgeVoxyInstance INSTANCE = new ForgeVoxyInstance();

    private final ForgeIngestRetryQueue ingestRetryQueue = new ForgeIngestRetryQueue(this);
    //Forge events are process-scoped, while original Voxy owns these resources per network
    //session. Keeping the event shell stable and replacing this owner on login/logout preserves
    //that lifetime without making mixins retain a stale instance.
    private SessionRuntime sessionRuntime;
    private Object attemptedConnection;
    private boolean sessionShutdownInProgress;
    private boolean gpuDebugEnabled;
    private volatile boolean shuttingDown;
    private boolean terminalCleanupComplete;

    private ForgeVoxyInstance() {
    }

    synchronized boolean isRunning() {
        return this.isSessionRuntimeActive();
    }

    synchronized boolean isSessionRuntimeActive() {
        return !this.shuttingDown
                && this.sessionRuntime != null
                && this.sessionRuntime.running;
    }

    public void register() {
        MinecraftForge.EVENT_BUS.addListener(this::onClientLogin);
        MinecraftForge.EVENT_BUS.addListener(this::onClientTick);
        MinecraftForge.EVENT_BUS.addListener(this::onRegisterClientCommands);
        MinecraftForge.EVENT_BUS.addListener(this::onGameShuttingDown);
        MinecraftForge.EVENT_BUS.addListener(this::onRenderFog);
        MinecraftForge.EVENT_BUS.addListener(this::onDebugText);
        this.ingestRetryQueue.register();
    }

    private WorldEngine resolveAutoIngestTarget(LevelChunk chunk) {
        if (!ForgeVoxyConfig.ENABLED.get()
                || !ForgeVoxyConfig.INGEST_ENABLED.get()
                || !this.isSessionRuntimeActive()) {
            return null;
        }
        if (chunk.getLevel() instanceof ClientLevel level) {
            return this.getEngineForLevel(level).orElse(null);
        }
        if (!(chunk.getLevel() instanceof ServerLevel serverLevel)) {
            return null;
        }

        //Chunky generates ServerLevel chunks. Only bridge the current level of this physical
        //client's own integrated server; a multiplayer client has no integrated server and can
        //therefore never route dedicated-server chunks into its client WorldEngine.
        Minecraft minecraft = Minecraft.getInstance();
        var integratedServer = minecraft.getSingleplayerServer();
        ClientLevel clientLevel = minecraft.level;
        if (integratedServer == null
                || clientLevel == null
                || serverLevel.getServer() != integratedServer
                || integratedServer.getLevel(clientLevel.dimension()) != serverLevel) {
            return null;
        }

        WorldIdentifier clientIdentifier =
                ((IWorldGetIdentifier) clientLevel).voxy$getIdentifier();
        if (clientIdentifier == null
                || !clientIdentifier.equals(WorldIdentifier.fromServerLevel(serverLevel))) {
            return null;
        }
        //The client may finish a dimension switch while the integrated server callback runs.
        if (minecraft.level != clientLevel) {
            return null;
        }
        return this.getEngineForLevel(clientLevel).orElse(null);
    }

    //Original Voxy disables vanilla's render-distance fog whenever LOD rendering is active
    // (MixinFogRenderer pushes FogData.renderDistanceStart/End to infinity) so vanilla terrain
    // does not fade into a fog band right before the LOD picks up. 1.20.1 has one combined fog
    // state, so this uses the same classification as ForgeOriginalVoxyFogParameters: ordinary
    // distance fog and DimensionSpecialEffects#isFoggyAt distance fog are disabled, while
    // environmental fog (water/lava/powder snow and blindness/darkness) is left untouched. The
    // Oculus shaderpack path manages its own fog and is skipped.
    private void onRenderFog(net.minecraftforge.client.event.ViewportEvent.RenderFog event) {
        if (event.getMode() != net.minecraft.client.renderer.FogRenderer.FogMode.FOG_TERRAIN) {
            return;
        }
        if (!ForgeVoxyConfig.isEnabledEarlySafe()
                || !this.isOriginalVoxyChunkBoundTrackerActive()
                || ForgeOriginalVoxyOculusPipelineBridge.shaderpackActive()) {
            return;
        }
        if (ForgeVoxyConfig.ORIGINAL_VOXY_USE_ENVIRONMENTAL_FOG.get()) {
            if (event.getType() != net.minecraft.world.level.material.FogType.NONE
                    || !ForgeOriginalVoxyFogParameters.isRenderDistanceFog(
                            event.getCamera(),
                            event.getType(),
                            event.getFarPlaneDistance())) {
                return;
            }
        } else if (event.getFarPlaneDistance() < 10.0F) {
            //Original keeps only the very-close blindness/darkness-style safety fog.
            return;
        }
        event.setNearPlaneDistance(9_999_999.0F);
        event.setFarPlaneDistance(9_999_999.0F);
        event.setCanceled(true);
    }

    public synchronized WorldEngine getActiveWorld() {
        SessionRuntime runtime = this.sessionRuntime;
        if (this.shuttingDown || runtime == null || !runtime.running) {
            return null;
        }
        OwnedWorld owned = runtime.activeStorageIdentity == null
                ? null
                : runtime.activeWorlds.get(runtime.activeStorageIdentity);
        return owned != null && owned.world().isLive() ? owned.world() : null;
    }

    public synchronized Optional<WorldEngine> getCurrentEngineOptional() {
        if (!this.isSessionRuntimeActive()) {
            return Optional.empty();
        }
        return Optional.ofNullable(this.getActiveWorld());
    }

    public Optional<WorldEngine> getEngineForLevel(ClientLevel level) {
        synchronized (this) {
            SessionRuntime runtime = this.sessionRuntime;
            if (this.shuttingDown || runtime == null || !runtime.running) {
                return Optional.empty();
            }
            Minecraft minecraft = Minecraft.getInstance();
            if (!acceptsSessionLevel(
                    runtime.connection,
                    currentConnectionToken(minecraft),
                    runtime.levels,
                    level,
                    minecraft.level)) {
                //A process-global Forge callback can arrive after reconnect. Original Voxy's
                // per-session instance cannot see that old ClientLevel, so reject it here too.
                return Optional.empty();
            }
            ForgeOriginalVoxyPersistentStorage.Identity identity = runtime.storage.identityForLevel(level);
            OwnedWorld owned = runtime.activeWorlds.get(identity);
            if (owned == null || !owned.world().isLive()) {
                owned = this.createOwnedWorld(runtime, identity);
            }
            owned.world().markActive();
            return Optional.of(owned.world());
        }
    }

    public ForgeOriginalVoxyModelPipeline getOriginalVoxyModelPipeline() {
        synchronized (this) {
            SessionRuntime runtime = this.sessionRuntime;
            return this.shuttingDown || runtime == null || !runtime.running
                    ? null
                    : runtime.modelPipeline;
        }
    }

    public boolean hasActiveOriginalVoxyRenderOwner() {
        ForgeOriginalVoxyModelPipeline pipeline;
        synchronized (this) {
            SessionRuntime runtime = this.sessionRuntime;
            if (this.shuttingDown || runtime == null || !runtime.running) {
                return false;
            }
            pipeline = runtime.modelPipeline;
        }
        return pipeline.hasActiveRenderOwner();
    }

    ServiceManager getOriginalVoxyServiceManager() {
        ForgeOriginalVoxyModelPipeline pipeline = this.getOriginalVoxyModelPipeline();
        return pipeline == null ? null : pipeline.getServiceManager();
    }

    public void renderOriginalVoxyAfterTerrain(
            me.jellysquid.mods.sodium.client.render.chunk.ChunkRenderMatrices matrices,
            me.jellysquid.mods.sodium.client.render.viewport.CameraTransform camera) {
        if (camera == null) {
            return;
        }
        this.renderOriginalVoxyAfterTerrain(matrices, camera.x, camera.y, camera.z);
    }

    public void renderOriginalVoxyAfterTerrain(
            me.jellysquid.mods.sodium.client.render.chunk.ChunkRenderMatrices matrices,
            double cameraX,
            double cameraY,
            double cameraZ) {
        this.renderOriginalVoxyAfterTerrain(matrices, cameraX, cameraY, cameraZ, null);
    }

    public void renderOriginalVoxyAfterTerrain(
            me.jellysquid.mods.sodium.client.render.chunk.ChunkRenderMatrices matrices,
            double cameraX,
            double cameraY,
            double cameraZ,
            FrustumIntersection frustum) {
        ForgeOriginalVoxyModelPipeline pipeline = this.getOriginalVoxyModelPipeline();
        if (pipeline != null) {
            pipeline.renderEmbeddiumCutout(matrices, cameraX, cameraY, cameraZ, frustum);
        }
    }

    void markOriginalVoxyResourceReload() {
        ForgeOriginalVoxyModelPipeline pipeline = this.getOriginalVoxyModelPipeline();
        if (pipeline != null) {
            pipeline.markResourceReload();
        }
    }

    void refreshOriginalVoxyServiceThreadPolicy() {
        ForgeOriginalVoxyModelPipeline pipeline = this.getOriginalVoxyModelPipeline();
        if (pipeline != null && ForgeVoxyConfig.ENABLED.get()) {
            pipeline.refreshOriginalServiceThreadPolicy();
        }
    }

    void updateOriginalVoxyRenderDistance(float renderDistance) {
        ForgeOriginalVoxyModelPipeline pipeline = this.getOriginalVoxyModelPipeline();
        if (pipeline != null) {
            pipeline.updateOriginalRenderDistance(renderDistance);
        }
    }

    public MultiThreadPrioritySemaphore.Block createEmbeddiumBuilderSemaphoreBlock() {
        ForgeOriginalVoxyModelPipeline pipeline = this.getOriginalVoxyModelPipeline();
        if (pipeline == null || !ForgeVoxyConfig.ENABLED.get()) {
            return null;
        }
        return pipeline.createEmbeddiumBuilderSemaphoreBlock();
    }

    public void resetOriginalVoxyChunkBoundTracker() {
        ForgeOriginalVoxyModelPipeline pipeline = this.getOriginalVoxyModelPipeline();
        if (pipeline != null) {
            pipeline.resetChunkBoundTracker();
        }
    }

    public boolean isOriginalVoxyChunkBoundTrackerActive() {
        ForgeOriginalVoxyModelPipeline pipeline = this.getOriginalVoxyModelPipeline();
        return pipeline != null && pipeline.isChunkBoundTrackerActive();
    }

    public long originalVoxyChunkBoundOwnerGeneration() {
        ForgeOriginalVoxyModelPipeline pipeline = this.getOriginalVoxyModelPipeline();
        return pipeline == null ? -1L : pipeline.chunkBoundOwnerGeneration();
    }

    public void trackOriginalVoxyChunkBoundSection(boolean wasBuilt, int x, int y, int z) {
        ForgeOriginalVoxyModelPipeline pipeline = this.getOriginalVoxyModelPipeline();
        if (pipeline != null) {
            pipeline.trackChunkBoundSection(wasBuilt, x, y, z);
        }
    }

    public void markOriginalVoxyOculusWorldRenderingSettingsReload() {
        ForgeOriginalVoxyModelPipeline pipeline = this.getOriginalVoxyModelPipeline();
        if (pipeline != null) {
            pipeline.markOculusWorldRenderingSettingsReload();
        }
    }

    public boolean ingestChunkWithLightRetry(LevelChunk chunk) {
        return this.ingestRetryQueue.ingestChunk(chunk);
    }

    public boolean ingestSectionWithLightRetry(ClientLevel level, LevelChunk chunk, int sectionY) {
        return this.ingestRetryQueue.ingestSection(level, chunk, sectionY);
    }

    public VoxelIngestService getIngestService() {
        synchronized (this) {
            SessionRuntime runtime = this.sessionRuntime;
            return this.shuttingDown || runtime == null || !runtime.running
                    ? null
                    : runtime.ingestService;
        }
    }

    public ImportManager getImportManager() {
        synchronized (this) {
            SessionRuntime runtime = this.sessionRuntime;
            return this.shuttingDown || runtime == null || !runtime.running
                    ? null
                    : runtime.importManager;
        }
    }

    boolean canRunOriginalVoxyImportWork() {
        synchronized (this) {
            return this.sessionRuntime != null
                    && !this.shuttingDown
                    && this.sessionRuntime.running
                    && this.sessionRuntime.savingService.getTaskCount() < 1200;
        }
    }

    public boolean reloadOriginalVoxyRuntime() {
        Minecraft minecraft = Minecraft.getInstance();
        if (this.shuttingDown) {
            return false;
        }
        synchronized (this) {
            this.attemptedConnection = null;
        }
        this.shutdownSession(ForgeVoxyConfig.ENABLED.get()
                ? "configuration-reload"
                : "configuration-disabled");
        boolean selected = false;
        if (ForgeVoxyConfig.ENABLED.get()
                && minecraft.level != null
                && minecraft.player != null
                && this.startSessionRuntime(minecraft)) {
            selected = this.ensureOriginalVoxyActiveWorldForCurrentWorld();
        }
        if (minecraft.levelRenderer != null) {
            minecraft.levelRenderer.allChanged();
        }
        if (ForgeVoxyConfig.ENABLED.get()) {
            VoxyForge.LOGGER.info("Reloaded Voxy Forge original-parity runtime for the current client world.");
        }
        return !ForgeVoxyConfig.ENABLED.get() || selected;
    }

    void reloadOriginalVoxyRenderer(boolean rebuildVanillaRenderer) {
        ForgeOriginalVoxyModelPipeline pipeline = this.getOriginalVoxyModelPipeline();
        if (this.shuttingDown || pipeline == null) {
            return;
        }
        pipeline.markConfigurationReload();
        Minecraft minecraft = Minecraft.getInstance();
        if (rebuildVanillaRenderer && minecraft.levelRenderer != null) {
            minecraft.levelRenderer.allChanged();
        }
    }

    private void onDebugText(CustomizeGuiOverlayEvent.DebugText event) {
        // Forge posts DebugText even when the vanilla debug overlay is hidden. Original Voxy's
        // entries are owned by the debug screen, so they must not become an independent HUD.
        if (!Minecraft.getInstance().options.renderDebug
                || !ForgeVoxyConfig.ENABLED.get()
                || !this.isSessionRuntimeActive()) {
            return;
        }
        List<String> left = event.getLeft();
        this.addDebug(left);
        ForgeOriginalVoxyModelPipeline pipeline = this.getOriginalVoxyModelPipeline();
        if (pipeline == null) {
            return;
        }
        pipeline.addDebugInfo(left);
        TimingStatistics.update();
        left.add("Voxy frame runtime (millis): "
                + TimingStatistics.dynamic.pVal() + ", "
                + TimingStatistics.main.pVal() + ", "
                + TimingStatistics.postDynamic.pVal() + ", "
                + TimingStatistics.all.pVal());
        left.add("Extra time: "
                + TimingStatistics.A.pVal() + ", "
                + TimingStatistics.B.pVal() + ", "
                + TimingStatistics.C.pVal() + ", "
                + TimingStatistics.D.pVal());
        left.add("Extra 2 time: "
                + TimingStatistics.E.pVal() + ", "
                + TimingStatistics.F.pVal() + ", "
                + TimingStatistics.G.pVal() + ", "
                + TimingStatistics.H.pVal() + ", "
                + TimingStatistics.I.pVal());
        String gpuTiming = GPUTiming.INSTANCE.getDebug();
        if (!gpuTiming.isEmpty()) {
            left.add(gpuTiming);
        }
        PrintfDebugUtil.addToOut(left);
    }

    private synchronized void addDebug(List<String> debug) {
        SessionRuntime runtime = this.sessionRuntime;
        if (runtime == null || !runtime.running) {
            return;
        }
        debug.add("Buf/Tex [#/Mb]: ["
                + GlBuffer.getCount() + "/"
                + GlBuffer.getTotalSize() / 1_000_000L + "],["
                + ForgeOriginalVoxyGlResourceStatistics.textureCount() + "/"
                + ForgeOriginalVoxyGlResourceStatistics.textureBytes() / 1_000_000L + "]");
        debug.add("MemoryBuffer, Count/Size (mb): " + MemoryBuffer.getCount()
                + "/" + MemoryBuffer.getTotalSize() / 1_000_000);
        StringBuilder activeSections = new StringBuilder();
        for (OwnedWorld owned : runtime.activeWorlds.values()) {
            if (!activeSections.isEmpty()) {
                activeSections.append(", ");
            }
            activeSections.append(owned.world().getActiveSectionCount());
        }
        debug.add("I/S/AWSC: " + runtime.ingestService.getTaskCount()
                + "/" + runtime.savingService.getTaskCount()
                + "/[" + activeSections + "]");
    }

    private void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        ForgeVoxyCommands.register(event.getDispatcher());
    }

    private void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (this.shuttingDown) {
            return;
        }

        var minecraft = Minecraft.getInstance();
        if (!ForgeVoxyConfig.ENABLED.get()) {
            this.shutdownSession("configuration-disabled-tick");
            this.setGpuDebugEnabled(false, minecraft);
            return;
        }
        if (minecraft.level != null && minecraft.player != null && !this.isSessionRuntimeActive()) {
            this.startSessionRuntime(minecraft);
        }
        SessionRuntime runtime;
        synchronized (this) {
            runtime = this.sessionRuntime;
        }
        if (runtime == null || !runtime.running) {
            this.setGpuDebugEnabled(false, minecraft);
            return;
        }
        boolean renderDebug = minecraft.options.renderDebug;
        this.setGpuDebugEnabled(renderDebug, minecraft);
        if (minecraft.level == null || minecraft.player == null) {
            return;
        }

        this.ensureOriginalVoxyActiveWorldForCurrentWorld();
        runtime.modelPipeline.clientTick();
    }

    private void onClientLogin(ClientPlayerNetworkEvent.LoggingIn event) {
        // The pre-level Mixin normally created (or rejected) this connection exactly once.
        // Keep the Forge event as a fallback without reparsing a disabled/invalid config.
        this.startSessionRuntime(Minecraft.getInstance());
        this.ensureOriginalVoxyActiveWorldForCurrentWorld();
    }

    /**
     * Starts the original per-connection owner before Minecraft installs the new client level.
     *
     * <p>Original Voxy injects near the beginning of {@code ClientPacketListener#handleLogin},
     * before {@code Minecraft#setLevel}. Forge's {@link ClientPlayerNetworkEvent.LoggingIn}
     * fires later, after {@code LevelRenderer#setLevel} has already allowed Embeddium to create
     * its chunk job queue. Keeping this early adapter lets that queue join Voxy's original
     * unified service pool; the Forge event remains the post-level world-selection fallback.</p>
     */
    public void beginOriginalVoxyClientSession(ClientPacketListener connection) {
        this.startSessionRuntime(Minecraft.getInstance(), connection, true);
    }

    /** Ends the session after Minecraft and Embeddium have detached the client level. */
    public void endOriginalVoxyClientSession() {
        synchronized (this) {
            this.attemptedConnection = null;
        }
        this.shutdownSession("client-disconnect");
    }

    /**
     * Mirrors original {@code MixinLevelRenderer#setLevel} at its HEAD injection boundary.
     *
     * <p>This callback must run before LevelRenderer and Embeddium rebuild against the replacement
     * level. In particular, clearing the process-owned light retry queue at END tick would also
     * discard deferred entries produced by the new level during that rebuild.</p>
     */
    public void onOriginalVoxyClientLevelSwitch(ClientLevel targetLevel) {
        SessionRuntime runtime;
        synchronized (this) {
            runtime = this.sessionRuntime;
            if (runtime == null || !runtime.running) {
                return;
            }
        }

        this.ingestRetryQueue.clear();
        runtime.modelPipeline.markDimensionSwitch();
        this.closeActiveWorld(runtime);
        VoxyForge.LOGGER.info(
                "Cleared Voxy parity pipeline state at LevelRenderer#setLevel HEAD for {}.",
                targetLevel == null ? "<no-level>" : targetLevel.dimension().location());
    }

    public boolean ensureOriginalVoxyActiveWorldForCurrentWorld() {
        var minecraft = Minecraft.getInstance();
        if (this.shuttingDown
                || !ForgeVoxyConfig.ENABLED.get()
                || minecraft.level == null
                || minecraft.player == null) {
            return false;
        }
        synchronized (this) {
            SessionRuntime runtime = this.sessionRuntime;
            if (runtime == null || !runtime.running) {
                return false;
            }
            runtime.levels.add(minecraft.level);
            return this.selectOrCreateWorld(
                    runtime,
                    runtime.storage.identityForCurrentWorld(minecraft));
        }
    }

    private boolean selectOrCreateWorld(
            SessionRuntime runtime,
            ForgeOriginalVoxyPersistentStorage.Identity identity) {
        if (this.shuttingDown || runtime != this.sessionRuntime || !runtime.running) {
            return false;
        }
        //The original renderer chooses a WorldEngine by identifier. Forge's single model-pipeline
        //adapter must first receive markDimensionSwitch(), so an early render callback may not
        //silently retarget that adapter before the LevelRenderer#setLevel HEAD transition runs.
        if (runtime.activeStorageIdentity != null && !runtime.activeStorageIdentity.equals(identity)) {
            return false;
        }

        OwnedWorld existing = runtime.activeWorlds.get(identity);
        if (existing != null && existing.world().isLive()) {
            boolean newlySelected = !identity.equals(runtime.activeStorageIdentity);
            runtime.activeStorageIdentity = identity;
            existing.world().markActive();
            if (newlySelected) {
                VoxyForge.LOGGER.info(
                        "Reused original Voxy persistent WorldEngine {} at {}.",
                        identity.worldIdentifier(),
                        identity.storagePath());
            }
            return true;
        }

        OwnedWorld created = this.createOwnedWorld(runtime, identity);
        runtime.activeStorageIdentity = identity;
        return created.world().isLive();
    }

    private OwnedWorld createOwnedWorld(
            SessionRuntime runtime,
            ForgeOriginalVoxyPersistentStorage.Identity identity) {
        var storage = runtime.storage.open(identity);
        runtime.modelPipeline.ensureOriginalServiceThreads();
        WorldEngine world = new WorldEngine(storage, runtime);
        world.setSaveCallback(runtime.savingService::enqueueSave);
        OwnedWorld owned = new OwnedWorld(world);
        runtime.activeWorlds.put(identity, owned);
        VoxyForge.LOGGER.info(
                "Created Voxy WorldEngine using original persistent storage chain {} at {}.",
                identity.worldIdentifier(),
                identity.storagePath());
        return owned;
    }

    private void onGameShuttingDown(GameShuttingDownEvent event) {
        this.shutdown();
    }

    public void shutdown() {
        synchronized (this) {
            if (this.shuttingDown) {
                return;
            }
            this.shuttingDown = true;
            this.attemptedConnection = null;
        }

        VoxyForge.LOGGER.info("Shutting down Voxy Forge original-parity instance.");
        if (!this.shutdownSession("client-stop")) {
            this.completeTerminalShutdown();
        }
    }

    private void setGpuDebugEnabled(boolean enabled, Minecraft minecraft) {
        synchronized (this) {
            if (enabled == this.gpuDebugEnabled) {
                return;
            }
            this.gpuDebugEnabled = enabled;
        }
        RenderStatistics.enabled = enabled;
        GPUTiming.INSTANCE.setEnabled(enabled);
        if (this.isSessionRuntimeActive()
                && minecraft.level != null
                && minecraft.levelRenderer != null) {
            //Original DebugEntries rebuilds the renderer because HAS_STATISTICS is a shader define.
            minecraft.levelRenderer.allChanged();
        }
    }

    private boolean startSessionRuntime(Minecraft minecraft) {
        return this.startSessionRuntime(minecraft, currentConnectionToken(minecraft), false);
    }

    private boolean startSessionRuntime(
            Minecraft minecraft,
            Object connection,
            boolean explicitPreLevelConnection) {
        if (!ForgeVoxyConfig.ENABLED.get()) {
            return false;
        }
        synchronized (this) {
            if (this.shuttingDown
                    || this.sessionShutdownInProgress
                    || this.sessionRuntime != null
                    || connection == null
                    || this.attemptedConnection == connection) {
                return false;
            }
            //Original ClientSessionEvents attempts construction once per network session. A
            //disabled or invalid storage config must not be reparsed every client tick.
            this.attemptedConnection = connection;
        }

        ForgeOriginalVoxyPersistentStorage.Session storage;
        try {
            ClientPacketListener storageConnection = connection instanceof ClientPacketListener listener
                    ? listener
                    : minecraft.getConnection();
            storage = ForgeOriginalVoxyPersistentStorage.beginSession(minecraft, storageConnection);
        } catch (RuntimeException e) {
            VoxyForge.LOGGER.error("Failed to initialize original Voxy session storage.", e);
            // Original Voxy lets unexpected directory/config-write failures escape its
            // VoxyClientInstance constructor so the login error boundary can fail fast.
            throw e;
        }
        if (!storage.configuration().ready()) {
            VoxyForge.LOGGER.error(
                    "Not creating original Voxy session because storage config could not be loaded safely: {}",
                    storage.configuration().path());
            return false;
        }
        if (storage.configuration().config().disabled) {
            VoxyForge.LOGGER.info(
                    "Not creating original Voxy session because storage config disables it: {}",
                    storage.configuration().path());
            return false;
        }

        SessionRuntime created = new SessionRuntime(this, connection, storage);
        synchronized (this) {
            if (this.shuttingDown
                    || this.sessionShutdownInProgress
                    || this.sessionRuntime != null
                    || !ForgeVoxyConfig.ENABLED.get()
                    || (!explicitPreLevelConnection && connection != currentConnectionToken(minecraft))) {
                created.running = false;
            } else {
                this.sessionRuntime = created;
            }
        }
        if (!created.running) {
            created.stopWorldCleaner();
            created.ingestService.shutdown();
            created.savingService.shutdown();
            created.modelPipeline.shutdownOriginalServiceThreads();
            return false;
        }

        VoxelIngestService.setAutoIngestTarget(this::resolveAutoIngestTarget);
        VoxelIngestService.setActiveService(created.ingestService);
        VoxyForge.LOGGER.info(
                "Created original Voxy network-session runtime using {} from {}.",
                storage.configuration().backendChain(),
                storage.configuration().path());
        return true;
    }

    /** @return whether teardown is active and terminal cleanup must wait for its continuation. */
    private boolean shutdownSession(String reason) {
        SessionRuntime runtime;
        synchronized (this) {
            runtime = this.sessionRuntime;
            if (runtime == null) {
                return this.sessionShutdownInProgress;
            }
            this.sessionRuntime = null;
            this.sessionShutdownInProgress = true;
            runtime.running = false;
        }

        VoxelIngestService.setAutoIngestTarget(null);
        VoxelIngestService.setActiveService(null);
        this.ingestRetryQueue.clear();
        this.setGpuDebugEnabled(false, Minecraft.getInstance());
        runtime.stopWorldCleaner();

        //Original VoxyInstance shuts every import down before stopping ingest/saving services.
        this.cancelAllImports(runtime);
        VoxyForge.LOGGER.info("Shutting down original Voxy network-session runtime: {}.", reason);
        try {
            runtime.modelPipeline.shutdownForClientStop(
                    () -> this.finishSessionShutdown(runtime, reason));
        } catch (RuntimeException e) {
            VoxyForge.LOGGER.error("Failed to schedule Voxy render-owner cleanup; continuing session shutdown.", e);
            this.finishSessionShutdown(runtime, reason);
        }
        return true;
    }

    private void finishSessionShutdown(SessionRuntime runtime, String reason) {
        synchronized (runtime) {
            if (runtime.cleanupComplete) {
                return;
            }
            runtime.cleanupComplete = true;
        }
        try {
            //Original VoxyClientInstance clears this cache once the entire session instance ends.
            RenderResourceReuse.clearResources();
        } catch (Exception e) {
            VoxyForge.LOGGER.error("Failed to clear Voxy render-resource reuse cache.", e);
        }
        try {
            runtime.ingestService.shutdown();
        } catch (Exception e) {
            VoxyForge.LOGGER.error("Failed to shut down Voxy ingest service.", e);
        }
        try {
            runtime.savingService.shutdown();
        } catch (Exception e) {
            VoxyForge.LOGGER.error("Failed to shut down Voxy section saving service.", e);
        }
        try {
            this.freeAllWorldsForShutdown(runtime);
        } catch (Exception e) {
            VoxyForge.LOGGER.error("Failed to free every Voxy world for the ending session.", e);
        }
        try {
            runtime.modelPipeline.shutdownOriginalServiceThreads();
        } catch (Exception e) {
            VoxyForge.LOGGER.error("Failed to shut down Voxy original service thread pool.", e);
        }

        boolean finishTerminal;
        synchronized (this) {
            this.sessionShutdownInProgress = false;
            finishTerminal = this.shuttingDown;
        }
        VoxyForge.LOGGER.info("Original Voxy network-session runtime shutdown complete: {}.", reason);
        if (finishTerminal) {
            this.completeTerminalShutdown();
        }
    }

    private void completeTerminalShutdown() {
        if (!RenderSystem.isOnRenderThread()) {
            RenderSystem.recordRenderCall(this::completeTerminalShutdown);
            return;
        }
        synchronized (this) {
            if (this.terminalCleanupComplete || this.sessionShutdownInProgress) {
                return;
            }
            this.terminalCleanupComplete = true;
        }
        VoxelIngestService.setAutoIngestTarget(null);
        VoxelIngestService.setActiveService(null);
        try {
            ForgeOriginalVoxyClientRuntime.shutdown();
        } catch (Exception e) {
            VoxyForge.LOGGER.error("Failed to shut down Voxy client runtime.", e);
        }
        try {
            GPUTiming.INSTANCE.free();
        } catch (Exception e) {
            VoxyForge.LOGGER.error("Failed to free Voxy GPU timing resources.", e);
        }
        VoxyForge.LOGGER.info("Voxy Forge original-parity instance shutdown complete.");
    }

    private synchronized void closeActiveWorld(SessionRuntime runtime) {
        if (runtime != this.sessionRuntime || !runtime.running) {
            return;
        }
        runtime.activeStorageIdentity = null;
        this.cleanIdleWorlds(runtime);
    }

    private synchronized void cleanIdleWorlds(SessionRuntime runtime) {
        if (!runtime.running || runtime != this.sessionRuntime) {
            return;
        }
        Iterator<Map.Entry<ForgeOriginalVoxyPersistentStorage.Identity, OwnedWorld>> iterator =
                runtime.activeWorlds.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<ForgeOriginalVoxyPersistentStorage.Identity, OwnedWorld> entry = iterator.next();
            WorldEngine world = entry.getValue().world();
            if (!world.isLive()) {
                iterator.remove();
                continue;
            }
            if (!world.isWorldIdle()) {
                continue;
            }
            world.free();
            if (entry.getKey().equals(runtime.activeStorageIdentity)) {
                runtime.activeStorageIdentity = null;
            }
            VoxyForge.LOGGER.info("Closed idle Voxy persistent WorldEngine at {}.", entry.getKey().storagePath());
            iterator.remove();
        }
    }

    private void freeAllWorldsForShutdown(SessionRuntime runtime) {
        runtime.activeStorageIdentity = null;
        for (Map.Entry<ForgeOriginalVoxyPersistentStorage.Identity, OwnedWorld> entry : runtime.activeWorlds.entrySet()) {
            WorldEngine world = entry.getValue().world();
            if (!world.isLive()) {
                continue;
            }
            while (world.isWorldUsed()) {
                try {
                    Thread.sleep(10L);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Interrupted while waiting for Voxy world references to drain", e);
                }
            }
            world.free();
            VoxyForge.LOGGER.info(
                    "Closed Voxy persistent WorldEngine during client shutdown at {}.",
                    entry.getKey().storagePath());
        }
        runtime.activeWorlds.clear();
    }

    private void cancelAllImports(SessionRuntime runtime) {
        ArrayList<WorldEngine> worlds;
        synchronized (this) {
            worlds = new ArrayList<>(runtime.activeWorlds.size());
            for (OwnedWorld owned : runtime.activeWorlds.values()) {
                worlds.add(owned.world());
            }
        }
        for (WorldEngine world : worlds) {
            runtime.importManager.cancelImport(world);
        }
    }

    private synchronized OwnedWorld getActiveOwnedWorld() {
        SessionRuntime runtime = this.sessionRuntime;
        return runtime == null || runtime.activeStorageIdentity == null
                ? null
                : runtime.activeWorlds.get(runtime.activeStorageIdentity);
    }

    private static Object currentConnectionToken(Minecraft minecraft) {
        Object connection = minecraft.getConnection();
        return connection != null ? connection : minecraft.getSingleplayerServer();
    }

    private static final class SessionRuntime implements WorldEngine.LifecycleOwner {
        private final Object connection;
        private final ForgeOriginalVoxyPersistentStorage.Session storage;
        private final ForgeOriginalVoxyModelPipeline modelPipeline;
        private final SectionSavingService savingService;
        private final VoxelIngestService ingestService;
        private final ImportManager importManager;
        //Forge's process-global callbacks need to recognize late work from another level in this
        //network session, but original Voxy never retains ClientLevel objects in its session owner.
        //Weak identity membership preserves that stale-callback guard without pinning every old
        //level (and its chunk/entity state) until disconnect.
        private final WeakIdentitySet<ClientLevel> levels = new WeakIdentitySet<>();
        //Original VoxyInstance owns all live worlds in a WorldIdentifier-keyed map. The Forge
        //renderer still selects one, while late callbacks within this session retain their own id.
        private final Map<ForgeOriginalVoxyPersistentStorage.Identity, OwnedWorld> activeWorlds = new HashMap<>();
        private ForgeOriginalVoxyPersistentStorage.Identity activeStorageIdentity;
        private volatile boolean running = true;
        private boolean cleanupComplete;
        private final Thread worldCleaner;

        private SessionRuntime(
                ForgeVoxyInstance owner,
                Object connection,
                ForgeOriginalVoxyPersistentStorage.Session storage) {
            this.connection = connection;
            this.storage = storage;
            this.modelPipeline = new ForgeOriginalVoxyModelPipeline(owner);
            this.savingService = new SectionSavingService(this.modelPipeline.getServiceManager());
            this.ingestService = new VoxelIngestService(this.modelPipeline.getServiceManager());
            this.importManager = new ClientImportManager();
            this.worldCleaner = new Thread(() -> {
                try {
                    while (this.running) {
                        Thread.sleep(1_000L);
                        owner.cleanIdleWorlds(this);
                    }
                } catch (InterruptedException ignored) {
                    //The session is ending.
                } catch (Exception e) {
                    VoxyForge.LOGGER.error("Exception in original Voxy active-world cleaner.", e);
                }
            }, "Active world cleaner");
            this.worldCleaner.setPriority(Thread.MIN_PRIORITY);
            this.worldCleaner.setDaemon(true);
            this.worldCleaner.start();
        }

        @Override
        public boolean isRunning() {
            return this.running;
        }

        private void stopWorldCleaner() {
            this.worldCleaner.interrupt();
            try {
                this.worldCleaner.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                VoxyForge.LOGGER.error("Interrupted while stopping original Voxy world cleaner.", e);
            }
        }
    }

    static <T> boolean acceptsSessionLevel(
            Object runtimeConnection,
            Object currentConnection,
            WeakIdentitySet<T> levels,
            T level,
            T currentLevel) {
        if (runtimeConnection != currentConnection) {
            return false;
        }
        if (level == currentLevel) {
            levels.add(level);
            return true;
        }
        return levels.contains(level);
    }

    static final class WeakIdentitySet<T> {
        private final List<WeakReference<T>> values = new ArrayList<>();

        boolean add(T value) {
            if (value == null) {
                throw new NullPointerException("value");
            }
            if (this.contains(value)) {
                return false;
            }
            this.values.add(new WeakReference<>(value));
            return true;
        }

        boolean contains(T value) {
            if (value == null) {
                return false;
            }
            boolean found = false;
            Iterator<WeakReference<T>> iterator = this.values.iterator();
            while (iterator.hasNext()) {
                T existing = iterator.next().get();
                if (existing == null) {
                    iterator.remove();
                } else if (existing == value) {
                    found = true;
                }
            }
            return found;
        }
    }

    private record OwnedWorld(WorldEngine world) {
    }

}
