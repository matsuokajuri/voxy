package me.cortex.voxy.forge;

import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

final class ForgeVoxyClientModEvents {
    private ForgeVoxyClientModEvents() {
    }

    static void register(IEventBus modBus) {
        modBus.addListener(ForgeVoxyClientModEvents::onClientSetup);
        modBus.addListener(ForgeVoxyClientModEvents::onRegisterClientReloadListeners);
    }

    private static void onClientSetup(FMLClientSetupEvent event) {
        ForgeVoxyInstance.INSTANCE.register();
        VoxyForge.LOGGER.info("Voxy Forge client parity adapters registered. Game dir: {}", VoxyForge.PLATFORM.getGameDir());
    }

    private static void onRegisterClientReloadListeners(RegisterClientReloadListenersEvent event) {
        ForgeVoxyInstance.INSTANCE.getModelBridgeResourceReloadTracker().registerClientReloadListeners(event);
        VoxyForge.LOGGER.info("Registered Voxy model bridge client resource reload listener.");
    }
}
