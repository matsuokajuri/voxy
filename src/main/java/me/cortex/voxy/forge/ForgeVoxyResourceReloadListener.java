package me.cortex.voxy.forge;

import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;

/** Forge resource-reload adapter that invalidates the active original model/render owners. */
final class ForgeVoxyResourceReloadListener implements ResourceManagerReloadListener {
    private final ForgeVoxyInstance instance;

    ForgeVoxyResourceReloadListener(ForgeVoxyInstance instance) {
        this.instance = instance;
    }

    @Override
    public void onResourceManagerReload(ResourceManager resourceManager) {
        this.instance.getOriginalVoxyModelPipeline().markResourceReload();
    }

    @Override
    public String getName() {
        return "voxy_resource_reload_listener";
    }
}
