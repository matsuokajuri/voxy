package me.cortex.voxy.forge;

final class ForgeMdicDebugDrawConfig {
    private ForgeMdicDebugDrawConfig() {
    }

    static boolean isEnabled() {
        return ForgeVoxyRuntimeOverrides.enableMdicDebugDraw();
    }

    static boolean actualDrawEnabled() {
        return ForgeVoxyRuntimeOverrides.mdicDebugDrawActualDraw();
    }

    static int maxCommands() {
        return ForgeVoxyRuntimeOverrides.mdicDebugDrawMaxCommands();
    }

    static int maxRecords() {
        return ForgeVoxyRuntimeOverrides.mdicDebugDrawMaxRecords();
    }

    static double alpha() {
        return ForgeVoxyRuntimeOverrides.mdicDebugDrawAlpha();
    }

    static boolean ignoreDepth() {
        return ForgeVoxyRuntimeOverrides.mdicDebugDrawIgnoreDepth();
    }

    static boolean doubleSided() {
        return ForgeVoxyRuntimeOverrides.mdicDebugDrawDoubleSided();
    }

    static boolean debugLog() {
        return ForgeVoxyRuntimeOverrides.mdicDebugDrawDebugLog();
    }
}
