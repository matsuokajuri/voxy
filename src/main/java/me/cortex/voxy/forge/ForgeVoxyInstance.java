package me.cortex.voxy.forge;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import me.cortex.voxy.common.config.section.SectionStorage;
import me.cortex.voxy.common.world.WorldEngine;
import me.cortex.voxy.common.world.WorldSection;
import me.cortex.voxy.common.world.service.SectionSavingService;
import me.cortex.voxy.common.world.service.VoxelIngestService;
import me.cortex.voxy.config.ForgeVoxyConfig;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.GameShuttingDownEvent;
import net.minecraftforge.event.TickEvent;

import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.LongConsumer;

public final class ForgeVoxyInstance {
    public static final ForgeVoxyInstance INSTANCE = new ForgeVoxyInstance();

    private WorldEngine activeWorld;
    private ForgeOriginalVoxyPersistentStorage.Identity activeStorageIdentity;
    private final ArrayDeque<ClosingWorld> closingWorlds = new ArrayDeque<>();
    private final ForgeOriginalVoxyModelPipeline originalVoxyModelPipeline = new ForgeOriginalVoxyModelPipeline(this);
    private final SectionSavingService originalVoxySectionSavingService =
            new SectionSavingService(this.originalVoxyModelPipeline.getServiceManager());
    private final VoxelIngestService originalVoxyIngestService =
            new VoxelIngestService(this.originalVoxyModelPipeline.getServiceManager());
    private final ForgeChunkIngestManager chunkIngestManager = new ForgeChunkIngestManager(this);
    private final ForgeModelBridgeResourceReloadTracker modelBridgeResourceReloadTracker = new ForgeModelBridgeResourceReloadTracker(this);
    private final AtomicInteger storageWriteCount = new AtomicInteger();
    private final AtomicInteger storageLoadHitCount = new AtomicInteger();
    private final AtomicInteger storageLoadMissCount = new AtomicInteger();
    private final AtomicInteger storageMappingLoadCount = new AtomicInteger();
    private final AtomicInteger storageMappingWriteCount = new AtomicInteger();
    private long persistentStorageOpenCount;
    private long persistentStorageReuseCount;
    private String activeClientDimension;
    private boolean shuttingDown;

    private ForgeVoxyInstance() {
    }

    public void register() {
        VoxelIngestService.setAutoIngestTarget(chunk -> this.getCurrentEngineOptional().orElse(null));
        VoxelIngestService.setActiveService(this.originalVoxyIngestService);
        MinecraftForge.EVENT_BUS.addListener(this::onClientLogin);
        MinecraftForge.EVENT_BUS.addListener(this::onClientLogout);
        MinecraftForge.EVENT_BUS.addListener(this::onClientTick);
        MinecraftForge.EVENT_BUS.addListener(this::onRegisterClientCommands);
        MinecraftForge.EVENT_BUS.addListener(this::onGameShuttingDown);
        MinecraftForge.EVENT_BUS.addListener(this::onRenderFog);
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

    public WorldEngine getActiveWorld() {
        return this.activeWorld;
    }

    public Optional<WorldEngine> getCurrentEngineOptional() {
        if (this.shuttingDown) {
            return Optional.empty();
        }
        return this.activeWorld != null && this.activeWorld.isLive()
                ? Optional.of(this.activeWorld)
                : Optional.empty();
    }

    public int getStorageWriteCount() {
        return this.storageWriteCount.get();
    }

    public PersistentStorageStatus createPersistentStorageStatusSnapshot() {
        ForgeOriginalVoxyPersistentStorage.Identity identity = this.activeStorageIdentity;
        return new PersistentStorageStatus(
                identity != null && this.activeWorld != null && this.activeWorld.isLive(),
                "Serializer->ZSTD(level=1)->RocksDB",
                identity == null ? "none" : identity.worldIdentifier().toString(),
                identity == null ? "none" : identity.storagePath().toString(),
                this.storageLoadHitCount.get(),
                this.storageLoadMissCount.get(),
                this.storageWriteCount.get(),
                this.storageMappingLoadCount.get(),
                this.storageMappingWriteCount.get(),
                this.persistentStorageOpenCount,
                this.persistentStorageReuseCount,
                this.closingWorlds.size());
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

    public ForgeModelBridgeResourceReloadTracker getModelBridgeResourceReloadTracker() {
        return this.modelBridgeResourceReloadTracker;
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
            this.drainClosingWorlds();
            return;
        }
        this.drainClosingWorlds();
        if (ForgeVoxyConfig.ENABLED.get()) {
            this.ensureOriginalVoxyActiveWorldForCurrentWorld();
        }

        String dimension = minecraft.level.dimension().location().toString();
        if (this.activeClientDimension == null) {
            this.activeClientDimension = dimension;
            this.originalVoxyModelPipeline.clientTick();
            return;
        }
        if (this.activeClientDimension.equals(dimension)) {
            this.originalVoxyModelPipeline.clientTick();
            return;
        }

        this.activeClientDimension = dimension;
        this.chunkIngestManager.clear();
        this.modelBridgeResourceReloadTracker.clear();
        this.originalVoxyModelPipeline.markDimensionSwitch();
        this.closeActiveWorld();
        this.ensureOriginalVoxyActiveWorldForCurrentWorld();
        VoxyForge.LOGGER.info("Cleared Voxy parity pipeline state after client dimension switch to {}.", dimension);
    }

    private void onClientLogin(ClientPlayerNetworkEvent.LoggingIn event) {
        this.ensureOriginalVoxyActiveWorldForCurrentWorld();
    }

    public boolean ensureActiveWorldSkeletonForCurrentWorldIfAllowed() {
        if (this.shuttingDown) {
            return false;
        }
        if (!ForgeVoxyRuntimeOverrides.enabledWorldEngineSkeleton()) {
            return false;
        }
        if (this.activeWorld != null && this.activeWorld.isLive()) {
            return true;
        }

        var minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            return false;
        }

        this.activeClientDimension = minecraft.level.dimension().location().toString();
        this.createActiveWorldSkeleton();
        return true;
    }

    public boolean ensureOriginalVoxyActiveWorldForCurrentWorld() {
        if (this.shuttingDown) {
            return false;
        }
        if (!ForgeVoxyConfig.ENABLED.get()) {
            return false;
        }
        if (this.activeWorld != null && this.activeWorld.isLive()) {
            return true;
        }

        var minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            return false;
        }

        this.activeClientDimension = minecraft.level.dimension().location().toString();
        this.createActiveWorldSkeleton();
        return true;
    }

    private void createActiveWorldSkeleton() {
        Minecraft minecraft = Minecraft.getInstance();
        ForgeOriginalVoxyPersistentStorage.Identity identity =
                ForgeOriginalVoxyPersistentStorage.identityForCurrentWorld(minecraft);
        this.storageWriteCount.set(0);
        this.storageLoadHitCount.set(0);
        this.storageLoadMissCount.set(0);
        this.storageMappingLoadCount.set(0);
        this.storageMappingWriteCount.set(0);

        Iterator<ClosingWorld> iterator = this.closingWorlds.iterator();
        while (iterator.hasNext()) {
            ClosingWorld closing = iterator.next();
            if (closing.identity().equals(identity) && closing.world().isLive()) {
                iterator.remove();
                this.activeWorld = closing.world();
                this.activeStorageIdentity = identity;
                this.activeWorld.markActive();
                this.persistentStorageReuseCount++;
                VoxyForge.LOGGER.info(
                        "Reused original Voxy persistent WorldEngine {} at {}.",
                        identity.worldIdentifier(),
                        identity.storagePath());
                return;
            }
        }

        var storage = new CountingSectionStorage(
                ForgeOriginalVoxyPersistentStorage.open(identity),
                this.storageWriteCount,
                this.storageLoadHitCount,
                this.storageLoadMissCount,
                this.storageMappingLoadCount,
                this.storageMappingWriteCount);
        this.originalVoxyModelPipeline.ensureOriginalServiceThreads();
        this.activeWorld = new WorldEngine(storage, this);
        this.activeStorageIdentity = identity;
        this.activeWorld.setSaveCallback(this.originalVoxySectionSavingService::enqueueSave);
        this.persistentStorageOpenCount++;
        VoxyForge.LOGGER.info(
                "Created Voxy WorldEngine using original persistent storage chain {} at {}.",
                identity.worldIdentifier(),
                identity.storagePath());
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
        this.drainClosingWorldsForShutdown();
        if (!this.closingWorlds.isEmpty()) {
            VoxyForge.LOGGER.warn(
                    "Skipped freeing {} Voxy world(s) during client shutdown because they still had live references.",
                    this.closingWorlds.size());
        }
        try {
            this.originalVoxyModelPipeline.shutdownOriginalServiceThreads();
        } catch (Exception e) {
            VoxyForge.LOGGER.error("Failed to shut down Voxy original service thread pool.", e);
        }
        VoxyForge.LOGGER.info("Voxy Forge original-parity instance shutdown complete.");
    }

    public void closeActiveWorld() {
        if (this.activeWorld != null) {
            if (this.activeStorageIdentity == null) {
                throw new IllegalStateException("Active Voxy world has no persistent storage identity");
            }
            this.closingWorlds.add(new ClosingWorld(this.activeStorageIdentity, this.activeWorld));
            this.activeWorld = null;
            this.activeStorageIdentity = null;
        }
        this.drainClosingWorlds();
    }

    private void drainClosingWorlds() {
        Iterator<ClosingWorld> iterator = this.closingWorlds.iterator();
        while (iterator.hasNext()) {
            ClosingWorld closing = iterator.next();
            WorldEngine world = closing.world();
            if (!world.isLive()) {
                iterator.remove();
                continue;
            }
            if (!world.isWorldIdle()) {
                continue;
            }
            world.free();
            VoxyForge.LOGGER.info("Closed Voxy persistent WorldEngine at {}.", closing.identity().storagePath());
            iterator.remove();
        }
    }

    private void drainClosingWorldsForShutdown() {
        Iterator<ClosingWorld> iterator = this.closingWorlds.iterator();
        while (iterator.hasNext()) {
            ClosingWorld closing = iterator.next();
            WorldEngine world = closing.world();
            if (!world.isLive()) {
                iterator.remove();
                continue;
            }
            if (world.isWorldUsed()) {
                continue;
            }
            world.free();
            VoxyForge.LOGGER.info(
                    "Closed Voxy persistent WorldEngine during client shutdown at {}.",
                    closing.identity().storagePath());
            iterator.remove();
        }
    }

    private record ClosingWorld(ForgeOriginalVoxyPersistentStorage.Identity identity, WorldEngine world) {
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
