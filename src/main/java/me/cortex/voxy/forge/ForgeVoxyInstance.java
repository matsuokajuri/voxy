package me.cortex.voxy.forge;

import me.cortex.voxy.common.config.section.SectionSerializationStorage;
import me.cortex.voxy.common.config.storage.inmemory.MemoryStorageBackend;
import me.cortex.voxy.common.world.WorldEngine;
import me.cortex.voxy.config.ForgeVoxyConfig;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.common.MinecraftForge;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

public final class ForgeVoxyInstance {
    public static final ForgeVoxyInstance INSTANCE = new ForgeVoxyInstance();

    private WorldEngine activeWorld;
    private final ForgeChunkIngestManager chunkIngestManager = new ForgeChunkIngestManager(this);
    private final ForgeCpuMeshCache cpuMeshCache = new ForgeCpuMeshCache();
    private final ForgeDebugMeshRenderer debugMeshRenderer = new ForgeDebugMeshRenderer(this);
    private final AtomicInteger storageWriteCount = new AtomicInteger();

    private ForgeVoxyInstance() {
    }

    public void register() {
        MinecraftForge.EVENT_BUS.addListener(this::onClientLogin);
        MinecraftForge.EVENT_BUS.addListener(this::onClientLogout);
        MinecraftForge.EVENT_BUS.addListener(this::onRegisterClientCommands);
        this.chunkIngestManager.register();
        this.debugMeshRenderer.register();
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

    public ForgeCpuMeshCache getCpuMeshCache() {
        return this.cpuMeshCache;
    }

    private void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        ForgeVoxyCommands.register(event.getDispatcher());
    }

    private void onClientLogin(ClientPlayerNetworkEvent.LoggingIn event) {
        if (!ForgeVoxyConfig.ENABLE_WORLD_ENGINE_SKELETON.get()) {
            return;
        }
        if (this.activeWorld != null && this.activeWorld.isLive()) {
            return;
        }

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
        this.cpuMeshCache.clear();
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
