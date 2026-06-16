package me.cortex.voxy.forge;

import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;

final class ForgeModelBridgeReloadListener implements ResourceManagerReloadListener {
    private final ForgeModelBridgeResourceReloadTracker tracker;

    ForgeModelBridgeReloadListener(ForgeModelBridgeResourceReloadTracker tracker) {
        this.tracker = tracker;
    }

    @Override
    public void onResourceManagerReload(ResourceManager resourceManager) {
        this.tracker.handleRealResourceReload();
    }

    @Override
    public String getName() {
        return "voxy_model_bridge_reload_listener";
    }
}
