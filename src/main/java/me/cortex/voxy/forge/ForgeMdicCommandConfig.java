package me.cortex.voxy.forge;

final class ForgeMdicCommandConfig {
    private ForgeMdicCommandConfig() {
    }

    static boolean isEnabled() {
        return ForgeVoxyRuntimeOverrides.enableMdicCommandSkeleton();
    }

    static int maxSections() {
        return ForgeVoxyRuntimeOverrides.mdicCommandMaxSections();
    }

    static int maxRecords() {
        return ForgeVoxyRuntimeOverrides.mdicCommandMaxRecords();
    }

    static boolean debugLog() {
        return ForgeVoxyRuntimeOverrides.mdicCommandDebugLog();
    }
}
