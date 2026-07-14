package me.cortex.voxy.forge;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

final class ViewportSelector {
    private static final Object DEFAULT_VIEWPORT_KEY = new Object();
    static final String OCULUS_SHADOW_SKIPPED_KEY = "oculus-shadow-skipped";

    private final Supplier<MDICViewport> creator;
    private final MDICViewport defaultViewport;
    private final Map<Object, MDICViewport> extraViewports = new HashMap<>();
    private String lastSelectedKey = "default";

    ViewportSelector(Supplier<MDICViewport> creator) {
        this.creator = creator;
        this.defaultViewport = creator.get();
    }

    MDICViewport getViewport() {
        //The stuck-ACTIVE protection (shaderpack must actually be active) lives inside
        // ForgeOculusShadowStateBridge.shadowActive() so all callers share it.
        if (ForgeOculusShadowStateBridge.shadowActive()) {
            this.lastSelectedKey = OCULUS_SHADOW_SKIPPED_KEY;
            return null;
        }
        Object vivecraftPass = ForgeVivecraftRenderPassBridge.currentNonVanillaRenderPass();
        if (vivecraftPass != null) {
            return this.select(vivecraftPass, "vivecraft-" + String.valueOf(vivecraftPass));
        }
        return this.select(DEFAULT_VIEWPORT_KEY, "default");
    }

    boolean ready() {
        if (!this.defaultViewport.ready()) {
            return false;
        }
        for (MDICViewport viewport : this.extraViewports.values()) {
            if (!viewport.ready()) {
                return false;
            }
        }
        return true;
    }

    boolean lastSelectionWasOculusShadowSkip() {
        return OCULUS_SHADOW_SKIPPED_KEY.equals(this.lastSelectedKey);
    }

    void free() {
        this.defaultViewport.free();
        for (MDICViewport viewport : this.extraViewports.values()) {
            viewport.free();
        }
        this.extraViewports.clear();
        this.lastSelectedKey = "none";
    }

    private MDICViewport select(Object key, String name) {
        MDICViewport viewport = key == DEFAULT_VIEWPORT_KEY
                ? this.defaultViewport
                : this.extraViewports.computeIfAbsent(key, ignored -> this.creator.get());
        this.lastSelectedKey = name;
        return viewport;
    }
}
