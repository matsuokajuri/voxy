package me.cortex.voxy.forge;

import net.minecraftforge.fml.ModList;

final class ForgeFrontendCompat {
    static final String EMBEDDIUM_MOD_ID = "embeddium";
    static final String OCULUS_MOD_ID = "oculus";

    private ForgeFrontendCompat() {
    }

    static ForgeFrontendCompatStats createStatusSnapshot() {
        ModList mods = ModList.get();
        boolean embeddiumLoaded = mods.isLoaded(EMBEDDIUM_MOD_ID);
        boolean oculusLoaded = mods.isLoaded(OCULUS_MOD_ID);
        boolean ready = embeddiumLoaded && oculusLoaded;

        return new ForgeFrontendCompatStats(
                "FORGE_FRONTEND_COMPAT_EMBEDDIUM_OCULUS",
                true,
                true,
                false,
                false,
                false,
                true,
                true,
                embeddiumLoaded,
                oculusLoaded,
                modVersion(mods, EMBEDDIUM_MOD_ID),
                modVersion(mods, OCULUS_MOD_ID),
                ready,
                true,
                true,
                false,
                false,
                false,
                false,
                ready ? "none" : missingReason(embeddiumLoaded, oculusLoaded)
        );
    }

    private static String modVersion(ModList mods, String modId) {
        return mods.getModContainerById(modId)
                .map(container -> container.getModInfo().getVersion().toString())
                .orElse("not-loaded");
    }

    private static String missingReason(boolean embeddiumLoaded, boolean oculusLoaded) {
        if (!embeddiumLoaded && !oculusLoaded) {
            return "missing-embeddium-and-oculus";
        }
        if (!embeddiumLoaded) {
            return "missing-embeddium";
        }
        return "missing-oculus";
    }
}
