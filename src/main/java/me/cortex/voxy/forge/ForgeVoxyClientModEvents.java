package me.cortex.voxy.forge;

import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

final class ForgeVoxyClientModEvents {
    private ForgeVoxyClientModEvents() {
    }

    static void register(IEventBus modBus) {
        ModLoadingContext.get().registerExtensionPoint(
                ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new ConfigScreenHandler.ConfigScreenFactory(ForgeOriginalVoxyEmbeddiumOptions::createScreen));
        modBus.addListener(ForgeVoxyClientModEvents::onClientSetup);
        modBus.addListener(ForgeVoxyClientModEvents::onRegisterClientReloadListeners);
    }

    private static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            if (!ForgeOriginalVoxyClientRuntime.initialize()) {
                return;
            }
            ForgeOriginalVoxyEmbeddiumOptions.register();
            ForgeVoxyInstance.INSTANCE.register();
            VoxyForge.LOGGER.info("Voxy Forge client adapters registered");
        });
    }

    private static void onRegisterClientReloadListeners(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener(new ForgeVoxyResourceReloadListener(ForgeVoxyInstance.INSTANCE));
        VoxyForge.LOGGER.info("Registered Voxy client resource reload listener");
    }
}
