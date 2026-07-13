package me.cortex.voxy.forge;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import me.cortex.voxy.common.config.section.SectionStorage;
import me.cortex.voxy.common.util.MemoryBuffer;
import me.cortex.voxy.common.world.WorldEngine;
import me.cortex.voxy.common.world.WorldSection;
import me.cortex.voxy.common.world.service.SectionSavingService;
import me.cortex.voxy.common.world.service.VoxelIngestService;
import me.cortex.voxy.commonImpl.ImportManager;
import me.cortex.voxy.config.ForgeVoxyConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.CustomizeGuiOverlayEvent;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.GameShuttingDownEvent;
import net.minecraftforge.event.TickEvent;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.LongConsumer;

public final class ForgeVoxyInstance {
    public static final ForgeVoxyInstance INSTANCE = new ForgeVoxyInstance();

    private ForgeOriginalVoxyPersistentStorage.Identity activeStorageIdentity;
    //Original VoxyInstance owns all live worlds in a WorldIdentifier-keyed map. The Forge
    //renderer still has one selected world, but late events from another ClientLevel must be
    //routed through their own identifier instead of whichever renderer happens to be active.
    private final Map<ForgeOriginalVoxyPersistentStorage.Identity, OwnedWorld> activeWorlds = new HashMap<>();
    private final ForgeOriginalVoxyModelPipeline originalVoxyModelPipeline = new ForgeOriginalVoxyModelPipeline(this);
    private final SectionSavingService originalVoxySectionSavingService =
            new SectionSavingService(this.originalVoxyModelPipeline.getServiceManager());
    private final VoxelIngestService originalVoxyIngestService =
            new VoxelIngestService(this.originalVoxyModelPipeline.getServiceManager());
    private final ImportManager originalVoxyImportManager = new ForgeOriginalVoxyClientImportManager();
    private final ForgeChunkIngestManager chunkIngestManager = new ForgeChunkIngestManager(this);
    private final ForgeModelBridgeResourceReloadTracker modelBridgeResourceReloadTracker = new ForgeModelBridgeResourceReloadTracker(this);
    private long persistentStorageOpenCount;
    private long persistentStorageReuseCount;
    private String activeClientDimension;
    private volatile boolean shuttingDown;

    private ForgeVoxyInstance() {
    }

    public void register() {
        VoxelIngestService.setAutoIngestTarget(chunk -> ForgeVoxyConfig.ENABLED.get()
                && ForgeVoxyConfig.INGEST_ENABLED.get()
                && chunk.getLevel() instanceof ClientLevel level
                ? this.getEngineForLevel(level).orElse(null)
                : null);
        VoxelIngestService.setActiveService(this.originalVoxyIngestService);
        MinecraftForge.EVENT_BUS.addListener(this::onClientLogin);
        MinecraftForge.EVENT_BUS.addListener(this::onClientLogout);
        MinecraftForge.EVENT_BUS.addListener(this::onClientTick);
        MinecraftForge.EVENT_BUS.addListener(this::onRegisterClientCommands);
        MinecraftForge.EVENT_BUS.addListener(this::onGameShuttingDown);
        MinecraftForge.EVENT_BUS.addListener(this::onRenderFog);
        MinecraftForge.EVENT_BUS.addListener(this::onDebugText);
        this.chunkIngestManager.register();
    }

    //Original Voxy disables vanilla's render-distance fog whenever LOD rendering is active
    // (MixinFogRenderer pushes FogData.renderDistanceStart/End to infinity) so vanilla terrain
    // does not fade into a fog band right before the LOD picks up. 1.20.1 has one combined fog
    // state, so this uses the same classification as ForgeOriginalVoxyFogParameters: only fog
    // ending near the vanilla render distance is render-distance fog; environmental fog
    // (water/lava/powder snow by type; blindness/darkness/nether thickness by short distance)
    // is left untouched. The Oculus shaderpack path manages its own fog and is skipped.
    private void onRenderFog(net.minecraftforge.client.event.ViewportEvent.RenderFog event) {
        if (event.getMode() != net.minecraft.client.renderer.FogRenderer.FogMode.FOG_TERRAIN) {
            return;
        }
        if (event.getType() != net.minecraft.world.level.material.FogType.NONE) {
            return;
        }
        if (!ForgeVoxyConfig.isEnabledEarlySafe()
                || !this.originalVoxyModelPipeline.isChunkBoundTrackerActive()
                || ForgeOriginalVoxyOculusPipelineBridge.shaderpackActive()) {
            return;
        }
        if (!ForgeOriginalVoxyFogParameters.isRenderDistanceFog(event.getFarPlaneDistance())) {
            return;
        }
        event.setNearPlaneDistance(9_999_999.0F);
        event.setFarPlaneDistance(9_999_999.0F);
        event.setCanceled(true);
    }

    public synchronized WorldEngine getActiveWorld() {
        OwnedWorld owned = this.activeStorageIdentity == null
                ? null
                : this.activeWorlds.get(this.activeStorageIdentity);
        return owned != null && owned.world().isLive() ? owned.world() : null;
    }

    public synchronized Optional<WorldEngine> getCurrentEngineOptional() {
        if (this.shuttingDown) {
            return Optional.empty();
        }
        return Optional.ofNullable(this.getActiveWorld());
    }

    public Optional<WorldEngine> getEngineForLevel(ClientLevel level) {
        if (this.shuttingDown) {
            return Optional.empty();
        }
        ForgeOriginalVoxyPersistentStorage.Identity identity =
                ForgeOriginalVoxyPersistentStorage.identityForLevel(Minecraft.getInstance(), level);
        synchronized (this) {
            if (this.shuttingDown) {
                return Optional.empty();
            }
            OwnedWorld owned = this.activeWorlds.get(identity);
            if (owned == null || !owned.world().isLive()) {
                if (ForgeOriginalVoxyPersistentStorage.disabled(identity)) {
                    return Optional.empty();
                }
                owned = this.createOwnedWorld(identity);
            }
            owned.world().markActive();
            return Optional.of(owned.world());
        }
    }

    public int getStorageWriteCount() {
        OwnedWorld owned = this.getActiveOwnedWorld();
        return owned == null ? 0 : owned.counters().storageWriteCount().get();
    }

    public PersistentStorageStatus createPersistentStorageStatusSnapshot() {
        ForgeOriginalVoxyPersistentStorage.Identity identity = this.activeStorageIdentity;
        ForgeOriginalVoxyStorageConfig.Loaded config = identity == null
                ? null
                : ForgeOriginalVoxyPersistentStorage.configuration(identity);
        OwnedWorld owned = this.getActiveOwnedWorld();
        StorageCounters counters = owned == null ? null : owned.counters();
        return new PersistentStorageStatus(
                identity != null && owned != null && owned.world().isLive(),
                config != null && config.ready(),
                config == null ? "none" : config.path().toString(),
                config == null ? "none" : config.source(),
                config != null && config.config().disabled,
                config == null ? "none" : config.backendChain(),
                identity == null ? "none" : identity.worldIdentifier().toString(),
                identity == null ? "none" : identity.storagePath().toString(),
                counters == null ? 0 : counters.storageLoadHitCount().get(),
                counters == null ? 0 : counters.storageLoadMissCount().get(),
                counters == null ? 0 : counters.storageWriteCount().get(),
                counters == null ? 0 : counters.storageMappingLoadCount().get(),
                counters == null ? 0 : counters.storageMappingWriteCount().get(),
                this.persistentStorageOpenCount,
                this.persistentStorageReuseCount,
                this.getInactiveWorldCount());
    }

    public ForgeOriginalVoxyModelPipeline getOriginalVoxyModelPipeline() {
        return this.originalVoxyModelPipeline;
    }

    public void resetOriginalVoxyChunkBoundTracker() {
        this.originalVoxyModelPipeline.resetChunkBoundTracker();
    }

    public boolean isOriginalVoxyChunkBoundTrackerActive() {
        return this.originalVoxyModelPipeline.isChunkBoundTrackerActive();
    }

    public void trackOriginalVoxyChunkBoundSection(boolean wasBuilt, int x, int y, int z) {
        this.originalVoxyModelPipeline.trackChunkBoundSection(wasBuilt, x, y, z);
    }

    public void markOriginalVoxyOculusWorldRenderingSettingsReload() {
        this.originalVoxyModelPipeline.markOculusWorldRenderingSettingsReload();
    }

    public ForgeChunkIngestManager getChunkIngestManager() {
        return this.chunkIngestManager;
    }

    public VoxelIngestService getIngestService() {
        return this.originalVoxyIngestService;
    }

    public ImportManager getImportManager() {
        return this.originalVoxyImportManager;
    }

    boolean canRunOriginalVoxyImportWork() {
        return this.originalVoxySectionSavingService.getTaskCount() < 1200;
    }

    public ForgeModelBridgeResourceReloadTracker getModelBridgeResourceReloadTracker() {
        return this.modelBridgeResourceReloadTracker;
    }

    public boolean reloadOriginalVoxyRuntime() {
        Minecraft minecraft = Minecraft.getInstance();
        if (this.shuttingDown || minecraft.level == null || minecraft.player == null) {
            return false;
        }

        this.cancelAllImports();
        this.chunkIngestManager.clear();
        this.modelBridgeResourceReloadTracker.clear();
        this.originalVoxyModelPipeline.markWorldUnload();
        this.activeClientDimension = minecraft.level.dimension().location().toString();
        this.closeActiveWorld();
        this.originalVoxyModelPipeline.refreshOriginalServiceThreadPolicy();
        boolean selected = this.ensureOriginalVoxyActiveWorldForCurrentWorld();
        minecraft.levelRenderer.allChanged();
        VoxyForge.LOGGER.info("Reloaded Voxy Forge original-parity runtime for the current client world.");
        return selected;
    }

    private void onDebugText(CustomizeGuiOverlayEvent.DebugText event) {
        List<String> left = event.getLeft();
        ForgeOriginalVoxyModelPipeline.DebugStatus renderer = this.originalVoxyModelPipeline.createDebugStatus();
        left.add((renderer.rendererActive()
                ? net.minecraft.ChatFormatting.GREEN
                : net.minecraft.ChatFormatting.YELLOW) + "voxy-forge");
        this.addDebug(left);
        left.add("Voxy renderer: active=" + renderer.rendererActive()
                + " drawObserved=" + renderer.visibleDrawObserved()
                + " lifecycle=" + renderer.lifecycleState());
    }

    private synchronized void addDebug(List<String> debug) {
        debug.add("MemoryBuffer, Count/Size (mb): " + MemoryBuffer.getCount()
                + "/" + MemoryBuffer.getTotalSize() / 1_000_000);
        StringBuilder activeSections = new StringBuilder();
        for (OwnedWorld owned : this.activeWorlds.values()) {
            if (!activeSections.isEmpty()) {
                activeSections.append(", ");
            }
            activeSections.append(owned.world().getActiveSectionCount());
        }
        debug.add("I/S/AWSC: " + this.originalVoxyIngestService.getTaskCount()
                + "/" + this.originalVoxySectionSavingService.getTaskCount()
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
        if (minecraft.level == null || minecraft.player == null) {
            this.cleanIdleWorlds();
            return;
        }
        this.cleanIdleWorlds();

        String dimension = minecraft.level.dimension().location().toString();
        if (this.activeClientDimension == null) {
            this.activeClientDimension = dimension;
        } else if (!this.activeClientDimension.equals(dimension)) {
            //Detect and detach the old renderer owner before ensure/select. Previously ensure returned
            //the old global pointer first, allowing new-dimension chunks to enter the old WorldEngine.
            this.activeClientDimension = dimension;
            this.chunkIngestManager.clear();
            this.modelBridgeResourceReloadTracker.clear();
            this.originalVoxyModelPipeline.markDimensionSwitch();
            this.closeActiveWorld();
            VoxyForge.LOGGER.info("Cleared Voxy parity pipeline state after client dimension switch to {}.", dimension);
        }
        if (ForgeVoxyConfig.ENABLED.get()) {
            this.ensureOriginalVoxyActiveWorldForCurrentWorld();
        }
        this.originalVoxyModelPipeline.clientTick();
    }

    private void onClientLogin(ClientPlayerNetworkEvent.LoggingIn event) {
        this.ensureOriginalVoxyActiveWorldForCurrentWorld();
    }

    public boolean ensureOriginalVoxyActiveWorldForCurrentWorld() {
        if (this.shuttingDown) {
            return false;
        }
        if (!ForgeVoxyConfig.ENABLED.get()) {
            return false;
        }
        var minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            return false;
        }

        return this.selectOrCreateWorld(ForgeOriginalVoxyPersistentStorage.identityForCurrentWorld(minecraft));
    }

    private synchronized boolean selectOrCreateWorld(ForgeOriginalVoxyPersistentStorage.Identity identity) {
        if (this.shuttingDown) {
            return false;
        }
        if (ForgeOriginalVoxyPersistentStorage.disabled(identity)) {
            return false;
        }
        //The original renderer chooses a WorldEngine by identifier. Forge's single model-pipeline
        //adapter must first receive markDimensionSwitch(), so an early render callback may not
        //silently retarget that adapter before the END-tick lifecycle transition runs.
        if (this.activeStorageIdentity != null && !this.activeStorageIdentity.equals(identity)) {
            return false;
        }

        OwnedWorld existing = this.activeWorlds.get(identity);
        if (existing != null && existing.world().isLive()) {
            boolean newlySelected = !identity.equals(this.activeStorageIdentity);
            this.activeStorageIdentity = identity;
            existing.world().markActive();
            if (newlySelected) {
                this.persistentStorageReuseCount++;
                VoxyForge.LOGGER.info(
                        "Reused original Voxy persistent WorldEngine {} at {}.",
                        identity.worldIdentifier(),
                        identity.storagePath());
            }
            return true;
        }

        OwnedWorld created = this.createOwnedWorld(identity);
        this.activeStorageIdentity = identity;
        return created.world().isLive();
    }

    private OwnedWorld createOwnedWorld(ForgeOriginalVoxyPersistentStorage.Identity identity) {
        StorageCounters counters = new StorageCounters();
        var storage = new CountingSectionStorage(
                ForgeOriginalVoxyPersistentStorage.open(identity),
                counters.storageWriteCount(),
                counters.storageLoadHitCount(),
                counters.storageLoadMissCount(),
                counters.storageMappingLoadCount(),
                counters.storageMappingWriteCount());
        this.originalVoxyModelPipeline.ensureOriginalServiceThreads();
        WorldEngine world = new WorldEngine(storage, this);
        world.setSaveCallback(this.originalVoxySectionSavingService::enqueueSave);
        OwnedWorld owned = new OwnedWorld(world, counters);
        this.activeWorlds.put(identity, owned);
        this.persistentStorageOpenCount++;
        VoxyForge.LOGGER.info(
                "Created Voxy WorldEngine using original persistent storage chain {} at {}.",
                identity.worldIdentifier(),
                identity.storagePath());
        return owned;
    }

    private void onClientLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        this.chunkIngestManager.clear();
        this.modelBridgeResourceReloadTracker.clear();
        this.originalVoxyModelPipeline.markWorldUnload();
        this.activeClientDimension = null;
        this.closeActiveWorld();
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
        }

        VoxyForge.LOGGER.info("Shutting down Voxy Forge original-parity instance.");
        VoxelIngestService.setAutoIngestTarget(null);
        VoxelIngestService.setActiveService(null);
        this.chunkIngestManager.clear();
        this.modelBridgeResourceReloadTracker.clear();

        //Original VoxyInstance.shutdown() cancels every active import before the ingest/saving
        //services and the shared service pool are stopped. Import tasks own WorldEngine refs.
        this.cancelAllImports();

        boolean renderCleanupComplete = this.originalVoxyModelPipeline.shutdownForClientStop();
        this.activeClientDimension = null;
        this.closeActiveWorld();
        if (!renderCleanupComplete) {
            VoxyForge.LOGGER.warn(
                    "Skipped terminal Voxy service shutdown because render-resource cleanup was deferred off the render thread.");
            return;
        }
        //Original VoxyClientInstance.shutdown(): free the render resource cache since the entire
        // instance is freed (this is the only point the reused geometry buffer is truly deleted).
        ForgeOriginalVoxyRenderResourceReuse.clearResources();

        try {
            this.originalVoxyIngestService.shutdown();
        } catch (Exception e) {
            VoxyForge.LOGGER.error("Failed to shut down Voxy ingest service.", e);
        }
        try {
            this.originalVoxySectionSavingService.shutdown();
        } catch (Exception e) {
            VoxyForge.LOGGER.error("Failed to shut down Voxy section saving service.", e);
        }
        this.freeAllWorldsForShutdown();
        try {
            this.originalVoxyModelPipeline.shutdownOriginalServiceThreads();
        } catch (Exception e) {
            VoxyForge.LOGGER.error("Failed to shut down Voxy original service thread pool.", e);
        }
        VoxyForge.LOGGER.info("Voxy Forge original-parity instance shutdown complete.");
    }

    public synchronized void closeActiveWorld() {
        this.activeStorageIdentity = null;
        this.cleanIdleWorlds();
    }

    private synchronized void cleanIdleWorlds() {
        Iterator<Map.Entry<ForgeOriginalVoxyPersistentStorage.Identity, OwnedWorld>> iterator =
                this.activeWorlds.entrySet().iterator();
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
            if (entry.getKey().equals(this.activeStorageIdentity)) {
                this.activeStorageIdentity = null;
            }
            VoxyForge.LOGGER.info("Closed idle Voxy persistent WorldEngine at {}.", entry.getKey().storagePath());
            iterator.remove();
        }
    }

    private void freeAllWorldsForShutdown() {
        this.activeStorageIdentity = null;
        for (Map.Entry<ForgeOriginalVoxyPersistentStorage.Identity, OwnedWorld> entry : this.activeWorlds.entrySet()) {
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
        this.activeWorlds.clear();
    }

    private void cancelAllImports() {
        ArrayList<WorldEngine> worlds;
        synchronized (this) {
            worlds = new ArrayList<>(this.activeWorlds.size());
            for (OwnedWorld owned : this.activeWorlds.values()) {
                worlds.add(owned.world());
            }
        }
        for (WorldEngine world : worlds) {
            this.originalVoxyImportManager.cancelImport(world);
        }
    }

    private synchronized OwnedWorld getActiveOwnedWorld() {
        return this.activeStorageIdentity == null ? null : this.activeWorlds.get(this.activeStorageIdentity);
    }

    private synchronized int getInactiveWorldCount() {
        return this.activeWorlds.size() - (this.getActiveWorld() == null ? 0 : 1);
    }

    public boolean isRunning() {
        return !this.shuttingDown;
    }

    private record OwnedWorld(WorldEngine world, StorageCounters counters) {
    }

    private record StorageCounters(
            AtomicInteger storageWriteCount,
            AtomicInteger storageLoadHitCount,
            AtomicInteger storageLoadMissCount,
            AtomicInteger storageMappingLoadCount,
            AtomicInteger storageMappingWriteCount) {
        private StorageCounters() {
            this(new AtomicInteger(), new AtomicInteger(), new AtomicInteger(), new AtomicInteger(), new AtomicInteger());
        }
    }

    private static final class CountingSectionStorage extends SectionStorage {
        private final SectionStorage delegate;
        private final AtomicInteger storageWriteCount;
        private final AtomicInteger storageLoadHitCount;
        private final AtomicInteger storageLoadMissCount;
        private final AtomicInteger storageMappingLoadCount;
        private final AtomicInteger storageMappingWriteCount;

        private CountingSectionStorage(
                SectionStorage delegate,
                AtomicInteger storageWriteCount,
                AtomicInteger storageLoadHitCount,
                AtomicInteger storageLoadMissCount,
                AtomicInteger storageMappingLoadCount,
                AtomicInteger storageMappingWriteCount) {
            this.delegate = delegate;
            this.storageWriteCount = storageWriteCount;
            this.storageLoadHitCount = storageLoadHitCount;
            this.storageLoadMissCount = storageLoadMissCount;
            this.storageMappingLoadCount = storageMappingLoadCount;
            this.storageMappingWriteCount = storageMappingWriteCount;
        }

        @Override
        public int loadSection(WorldSection into) {
            int result = this.delegate.loadSection(into);
            if (result == 0) {
                this.storageLoadHitCount.incrementAndGet();
            } else if (result == 1) {
                this.storageLoadMissCount.incrementAndGet();
            }
            return result;
        }

        @Override
        public void saveSection(WorldSection section) {
            this.delegate.saveSection(section);
            this.storageWriteCount.incrementAndGet();
        }

        @Override
        public void putIdMapping(int id, ByteBuffer data) {
            this.delegate.putIdMapping(id, data);
            this.storageMappingWriteCount.incrementAndGet();
        }

        @Override
        public Int2ObjectOpenHashMap<byte[]> getIdMappingsData() {
            Int2ObjectOpenHashMap<byte[]> mappings = this.delegate.getIdMappingsData();
            this.storageMappingLoadCount.addAndGet(mappings.size());
            return mappings;
        }

        @Override
        public void flush() {
            this.delegate.flush();
        }

        @Override
        public void close() {
            this.delegate.close();
        }

        @Override
        public void iteratePositions(int level, LongConsumer callback) {
            this.delegate.iteratePositions(level, callback);
        }
    }

    public record PersistentStorageStatus(
            boolean persistentStorageReady,
            boolean storageConfigReady,
            String storageConfigPath,
            String storageConfigSource,
            boolean storageDisabled,
            String backendChain,
            String worldIdentifier,
            String storagePath,
            int sectionLoadHits,
            int sectionLoadMisses,
            int sectionWrites,
            int mappingEntriesLoaded,
            int mappingWrites,
            long openCount,
            long reuseCount,
            int closingWorldCount) {
    }
}
