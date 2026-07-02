package me.cortex.voxy.forge;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import me.cortex.voxy.common.config.section.SectionStorage;
import me.cortex.voxy.common.config.section.SectionSerializationStorage;
import me.cortex.voxy.common.config.storage.inmemory.MemoryStorageBackend;
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
    private final ArrayDeque<WorldEngine> closingWorlds = new ArrayDeque<>();
    private final ForgeOriginalVoxyModelPipeline originalVoxyModelPipeline = new ForgeOriginalVoxyModelPipeline(this);
    private final SectionSavingService originalVoxySectionSavingService =
            new SectionSavingService(this.originalVoxyModelPipeline.getServiceManager());
    private final VoxelIngestService originalVoxyIngestService =
            new VoxelIngestService(this.originalVoxyModelPipeline.getServiceManager());
    private final ForgeChunkIngestManager chunkIngestManager = new ForgeChunkIngestManager(this);
    private final ForgeCpuMeshBuildManager cpuMeshBuildManager = new ForgeCpuMeshBuildManager(this);
    private final ForgeCpuMeshCache cpuMeshCache = new ForgeCpuMeshCache();
    private final ForgeVoxyBuiltSectionBuildManager builtSectionBuildManager = new ForgeVoxyBuiltSectionBuildManager(this);
    private final ForgeVoxyGeometryCache voxyGeometryCache = new ForgeVoxyGeometryCache();
    private final ForgeSectionGeometryManager sectionGeometryManager = new ForgeSectionGeometryManager();
    private final ForgeSectionGeometryConsumeManager sectionGeometryConsumeManager = new ForgeSectionGeometryConsumeManager(this);
    private final ForgeGpuGeometryUploadManager gpuGeometryUploadManager = new ForgeGpuGeometryUploadManager(this);
    private final ForgeMdicCommandManager mdicCommandManager = new ForgeMdicCommandManager(this);
    private final ForgeModelBridgeResourceReloadTracker modelBridgeResourceReloadTracker = new ForgeModelBridgeResourceReloadTracker(this);
    private final AtomicInteger storageWriteCount = new AtomicInteger();
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
        this.chunkIngestManager.register();
        this.cpuMeshBuildManager.register();
        this.builtSectionBuildManager.register();
        this.sectionGeometryConsumeManager.register();
        this.gpuGeometryUploadManager.register();
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

    public ForgeCpuMeshBuildManager getCpuMeshBuildManager() {
        return this.cpuMeshBuildManager;
    }

    public ForgeCpuMeshCache getCpuMeshCache() {
        return this.cpuMeshCache;
    }

    public ForgeVoxyBuiltSectionBuildManager getBuiltSectionBuildManager() {
        return this.builtSectionBuildManager;
    }

    public ForgeVoxyGeometryCache getVoxyGeometryCache() {
        return this.voxyGeometryCache;
    }

    public ForgeSectionGeometryManager getSectionGeometryManager() {
        return this.sectionGeometryManager;
    }

    public ForgeSectionGeometryConsumeManager getSectionGeometryConsumeManager() {
        return this.sectionGeometryConsumeManager;
    }

    public ForgeGpuGeometryUploadManager getGpuGeometryUploadManager() {
        return this.gpuGeometryUploadManager;
    }

    public ForgeMdicCommandManager getMdicCommandManager() {
        return this.mdicCommandManager;
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
        this.cpuMeshBuildManager.clear();
        this.builtSectionBuildManager.clear();
        this.cpuMeshCache.setActiveDimension(dimension);
        this.voxyGeometryCache.setActiveDimension(dimension);
        this.sectionGeometryConsumeManager.clear();
        this.gpuGeometryUploadManager.clear();
        this.mdicCommandManager.clear();
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
        this.storageWriteCount.set(0);
        var storage = new CountingSectionStorage(
                new SectionSerializationStorage(new MemoryStorageBackend()),
                this.storageWriteCount);
        this.originalVoxyModelPipeline.ensureOriginalServiceThreads();
        this.activeWorld = new WorldEngine(storage, this);
        this.activeWorld.setSaveCallback(this.originalVoxySectionSavingService::enqueueSave);
        VoxyForge.LOGGER.info("Created Voxy WorldEngine using original section saving service.");
    }

    private void onClientLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        this.chunkIngestManager.clear();
        this.cpuMeshBuildManager.clear();
        this.builtSectionBuildManager.clear();
        this.cpuMeshCache.clear();
        this.voxyGeometryCache.clear();
        this.sectionGeometryConsumeManager.clear();
        this.gpuGeometryUploadManager.clear();
        this.mdicCommandManager.clear();
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
        this.cpuMeshBuildManager.clear();
        this.builtSectionBuildManager.clear();
        this.cpuMeshCache.clear();
        this.voxyGeometryCache.clear();
        this.sectionGeometryConsumeManager.clear();
        this.gpuGeometryUploadManager.clear();
        this.mdicCommandManager.clear();
        this.modelBridgeResourceReloadTracker.clear();

        boolean renderCleanupComplete = this.originalVoxyModelPipeline.shutdownForClientStop();
        this.activeClientDimension = null;
        this.closeActiveWorld();
        if (!renderCleanupComplete) {
            VoxyForge.LOGGER.warn(
                    "Skipped terminal Voxy service shutdown because render-resource cleanup was deferred off the render thread.");
            return;
        }

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
            this.closingWorlds.add(this.activeWorld);
            this.activeWorld = null;
        }
        this.drainClosingWorlds();
    }

    private void drainClosingWorlds() {
        Iterator<WorldEngine> iterator = this.closingWorlds.iterator();
        while (iterator.hasNext()) {
            WorldEngine world = iterator.next();
            if (!world.isLive()) {
                iterator.remove();
                continue;
            }
            if (!world.isWorldIdle()) {
                continue;
            }
            world.free();
            VoxyForge.LOGGER.info("Closed Voxy WorldEngine.");
            iterator.remove();
        }
    }

    private void drainClosingWorldsForShutdown() {
        Iterator<WorldEngine> iterator = this.closingWorlds.iterator();
        while (iterator.hasNext()) {
            WorldEngine world = iterator.next();
            if (!world.isLive()) {
                iterator.remove();
                continue;
            }
            if (world.isWorldUsed()) {
                continue;
            }
            world.free();
            VoxyForge.LOGGER.info("Closed Voxy WorldEngine during client shutdown.");
            iterator.remove();
        }
    }

    private static final class CountingSectionStorage extends SectionStorage {
        private final SectionStorage delegate;
        private final AtomicInteger storageWriteCount;

        private CountingSectionStorage(SectionStorage delegate, AtomicInteger storageWriteCount) {
            this.delegate = delegate;
            this.storageWriteCount = storageWriteCount;
        }

        @Override
        public int loadSection(WorldSection into) {
            return this.delegate.loadSection(into);
        }

        @Override
        public void saveSection(WorldSection section) {
            this.delegate.saveSection(section);
            this.storageWriteCount.incrementAndGet();
        }

        @Override
        public void putIdMapping(int id, ByteBuffer data) {
            this.delegate.putIdMapping(id, data);
        }

        @Override
        public Int2ObjectOpenHashMap<byte[]> getIdMappingsData() {
            return this.delegate.getIdMappingsData();
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
}
