package me.cortex.voxy.forge;

import me.cortex.voxy.common.config.section.SectionSerializationStorage;
import me.cortex.voxy.common.config.storage.inmemory.MemoryStorageBackend;
import me.cortex.voxy.common.world.WorldEngine;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

public final class ForgeVoxyInstance {
    public static final ForgeVoxyInstance INSTANCE = new ForgeVoxyInstance();

    private WorldEngine activeWorld;
    private final ForgeChunkIngestManager chunkIngestManager = new ForgeChunkIngestManager(this);
    private final ForgeCpuMeshBuildManager cpuMeshBuildManager = new ForgeCpuMeshBuildManager(this);
    private final ForgeCpuMeshCache cpuMeshCache = new ForgeCpuMeshCache();
    private final ForgeVoxyBuiltSectionBuildManager builtSectionBuildManager = new ForgeVoxyBuiltSectionBuildManager(this);
    private final ForgeVoxyGeometryCache voxyGeometryCache = new ForgeVoxyGeometryCache();
    private final ForgeDebugMeshRenderer debugMeshRenderer = new ForgeDebugMeshRenderer(this);
    private final ForgeGpuMeshCache gpuMeshCache = new ForgeGpuMeshCache();
    private final ForgeGpuMeshUploadManager gpuMeshUploadManager = new ForgeGpuMeshUploadManager(this);
    private final ForgeSimpleGpuMeshRenderer simpleGpuMeshRenderer = new ForgeSimpleGpuMeshRenderer(this);
    private final AtomicInteger storageWriteCount = new AtomicInteger();
    private String activeClientDimension;

    private ForgeVoxyInstance() {
    }

    public void register() {
        MinecraftForge.EVENT_BUS.addListener(this::onClientLogin);
        MinecraftForge.EVENT_BUS.addListener(this::onClientLogout);
        MinecraftForge.EVENT_BUS.addListener(this::onClientTick);
        MinecraftForge.EVENT_BUS.addListener(this::onRegisterClientCommands);
        this.chunkIngestManager.register();
        this.cpuMeshBuildManager.register();
        this.builtSectionBuildManager.register();
        this.debugMeshRenderer.register();
        this.gpuMeshUploadManager.register();
        this.simpleGpuMeshRenderer.register();
    }

    public WorldEngine getActiveWorld() {
        return this.activeWorld;
    }

    public Optional<WorldEngine> getCurrentEngineOptional() {
        return this.activeWorld != null && this.activeWorld.isLive()
                ? Optional.of(this.activeWorld)
                : Optional.empty();
    }

    public int getStorageWriteCount() {
        return this.storageWriteCount.get();
    }

    public ForgeChunkIngestManager getChunkIngestManager() {
        return this.chunkIngestManager;
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

    public ForgeDebugMeshRenderer getDebugMeshRenderer() {
        return this.debugMeshRenderer;
    }

    public ForgeGpuMeshCache getGpuMeshCache() {
        return this.gpuMeshCache;
    }

    public ForgeGpuMeshUploadManager getGpuMeshUploadManager() {
        return this.gpuMeshUploadManager;
    }

    public ForgeSimpleGpuMeshRenderer getSimpleGpuMeshRenderer() {
        return this.simpleGpuMeshRenderer;
    }

    private void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        ForgeVoxyCommands.register(event.getDispatcher());
    }

    private void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        var minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            return;
        }

        String dimension = minecraft.level.dimension().location().toString();
        if (this.activeClientDimension == null) {
            this.activeClientDimension = dimension;
            return;
        }
        if (this.activeClientDimension.equals(dimension)) {
            return;
        }

        this.activeClientDimension = dimension;
        this.chunkIngestManager.clear();
        this.cpuMeshBuildManager.clear();
        this.builtSectionBuildManager.clear();
        this.cpuMeshCache.setActiveDimension(dimension);
        this.voxyGeometryCache.setActiveDimension(dimension);
        this.gpuMeshUploadManager.clear();
        this.gpuMeshCache.setActiveDimension(dimension);
        this.closeActiveWorld();
        if (ForgeVoxyRuntimeOverrides.enabledWorldEngineSkeleton()) {
            this.createActiveWorldSkeleton();
        }
        VoxyForge.LOGGER.info("Cleared Voxy debug pipeline state after client dimension switch to {}.", dimension);
    }

    private void onClientLogin(ClientPlayerNetworkEvent.LoggingIn event) {
        if (!ForgeVoxyRuntimeOverrides.enabledWorldEngineSkeleton()) {
            return;
        }
        if (this.activeWorld != null && this.activeWorld.isLive()) {
            return;
        }

        this.createActiveWorldSkeleton();
    }

    public boolean ensureActiveWorldSkeletonForCurrentWorldIfAllowed() {
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

    private void createActiveWorldSkeleton() {
        var storage = new SectionSerializationStorage(new MemoryStorageBackend());
        this.storageWriteCount.set(0);
        this.activeWorld = new WorldEngine(storage, this);
        this.activeWorld.setSaveCallback((engine, section, nonBlocking, sectionAlreadyAcquired) -> {
            try {
                section.setNotDirty();
                engine.storage.saveSection(section);
                this.storageWriteCount.incrementAndGet();
            } catch (Exception e) {
                VoxyForge.LOGGER.error("Failed to synchronously save Voxy skeleton section", e);
            }
            return false;
        });
        VoxyForge.LOGGER.info("Created empty Voxy WorldEngine skeleton for client world.");
    }

    private void onClientLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        this.chunkIngestManager.clear();
        this.cpuMeshBuildManager.clear();
        this.builtSectionBuildManager.clear();
        this.cpuMeshCache.clear();
        this.voxyGeometryCache.clear();
        this.gpuMeshUploadManager.clear();
        this.gpuMeshCache.clear();
        this.activeClientDimension = null;
        this.closeActiveWorld();
    }

    public void closeActiveWorld() {
        if (this.activeWorld == null) {
            return;
        }

        try {
            this.activeWorld.free();
            VoxyForge.LOGGER.info("Closed Voxy WorldEngine skeleton.");
        } finally {
            this.activeWorld = null;
        }
    }
}
