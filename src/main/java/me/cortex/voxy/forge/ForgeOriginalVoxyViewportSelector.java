package me.cortex.voxy.forge;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

final class ForgeOriginalVoxyViewportSelector {
    private static final Object DEFAULT_VIEWPORT_KEY = new Object();
    private static final Object OCULUS_SHADOW_VIEWPORT_KEY = new Object();

    private final Supplier<ForgeOriginalVoxyMdicViewport> creator;
    private final ForgeOriginalVoxyMdicViewport defaultViewport;
    private final Map<Object, ForgeOriginalVoxyMdicViewport> extraViewports = new HashMap<>();
    private ForgeOriginalVoxyMdicViewport lastSelectedViewport;
    private String lastSelectedKey = "default";

    ForgeOriginalVoxyViewportSelector(Supplier<ForgeOriginalVoxyMdicViewport> creator) {
        this.creator = creator;
        this.defaultViewport = creator.get();
        this.lastSelectedViewport = this.defaultViewport;
    }

    ForgeOriginalVoxyMdicViewport getViewport() {
        Object vivecraftPass = ForgeVivecraftRenderPassBridge.currentNonVanillaRenderPass();
        if (vivecraftPass != null) {
            return this.select(vivecraftPass, "vivecraft-" + String.valueOf(vivecraftPass));
        }
        if (ForgeOculusShadowStateBridge.shadowActive()) {
            return this.select(OCULUS_SHADOW_VIEWPORT_KEY, "oculus-shadow");
        }
        return this.select(DEFAULT_VIEWPORT_KEY, "default");
    }

    boolean ready() {
        if (!this.defaultViewport.ready()) {
            return false;
        }
        for (ForgeOriginalVoxyMdicViewport viewport : this.extraViewports.values()) {
            if (!viewport.ready()) {
                return false;
            }
        }
        return true;
    }

    boolean defaultViewportReady() {
        return this.defaultViewport.ready();
    }

    boolean hizOwnerReady() {
        if (!this.defaultViewport.hizOwnerReady()) {
            return false;
        }
        for (ForgeOriginalVoxyMdicViewport viewport : this.extraViewports.values()) {
            if (!viewport.hizOwnerReady()) {
                return false;
            }
        }
        return true;
    }

    boolean selectedHizTraversalReady() {
        return this.lastSelectedViewport != null && this.lastSelectedViewport.hizTraversalReady();
    }

    int extraViewportCount() {
        return this.extraViewports.size();
    }

    String lastSelectedKey() {
        return this.lastSelectedKey;
    }

    ForgeOriginalVoxyMdicViewport lastSelectedViewport() {
        return this.lastSelectedViewport;
    }

    void free() {
        this.defaultViewport.free();
        for (ForgeOriginalVoxyMdicViewport viewport : this.extraViewports.values()) {
            viewport.free();
        }
        this.extraViewports.clear();
        this.lastSelectedViewport = null;
        this.lastSelectedKey = "none";
    }

    private ForgeOriginalVoxyMdicViewport select(Object key, String name) {
        ForgeOriginalVoxyMdicViewport viewport = key == DEFAULT_VIEWPORT_KEY
                ? this.defaultViewport
                : this.extraViewports.computeIfAbsent(key, ignored -> this.creator.get());
        this.lastSelectedViewport = viewport;
        this.lastSelectedKey = name;
        return viewport;
    }
}
