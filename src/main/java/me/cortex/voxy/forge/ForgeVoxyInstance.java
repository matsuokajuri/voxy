package me.cortex.voxy.forge;

import me.cortex.voxy.common.config.section.SectionSerializationStorage;
import me.cortex.voxy.common.config.storage.inmemory.MemoryStorageBackend;
import me.cortex.voxy.common.world.WorldEngine;
import me.cortex.voxy.config.ForgeVoxyConfig;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.common.MinecraftForge;

public final class ForgeVoxyInstance {
    public static final ForgeVoxyInstance INSTANCE = new ForgeVoxyInstance();

    private WorldEngine activeWorld;

    private ForgeVoxyInstance() {
    }

    public void register() {
        MinecraftForge.EVENT_BUS.addListener(this::onClientLogin);
        MinecraftForge.EVENT_BUS.addListener(this::onClientLogout);
    }

    public WorldEngine getActiveWorld() {
        return this.activeWorld;
    }

    private void onClientLogin(ClientPlayerNetworkEvent.LoggingIn event) {
        if (!ForgeVoxyConfig.ENABLE_WORLD_ENGINE_SKELETON.get()) {
            return;
        }
        if (this.activeWorld != null && this.activeWorld.isLive()) {
            return;
        }

        var storage = new SectionSerializationStorage(new MemoryStorageBackend());
        this.activeWorld = new WorldEngine(storage, this);
        VoxyForge.LOGGER.info("Created empty Voxy WorldEngine skeleton for client world.");
    }

    private void onClientLogout(ClientPlayerNetworkEvent.LoggingOut event) {
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
