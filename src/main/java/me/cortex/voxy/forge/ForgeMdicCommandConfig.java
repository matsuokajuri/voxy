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

    static boolean bucketAware() {
        return ForgeVoxyRuntimeOverrides.mdicCommandBucketAware();
    }

    static boolean includeTranslucent() {
        return ForgeVoxyRuntimeOverrides.mdicCommandIncludeTranslucent();
    }

    static boolean includeDoubleSided() {
        return ForgeVoxyRuntimeOverrides.mdicCommandIncludeDoubleSided();
    }

    static boolean includeDirectional() {
        return ForgeVoxyRuntimeOverrides.mdicCommandIncludeDirectional();
    }

    static boolean directionalFaceMask() {
        return ForgeVoxyRuntimeOverrides.mdicCommandDirectionalFaceMask();
    }

    static boolean directionalFaceMaskFallbackAllWhenInside() {
        return ForgeVoxyRuntimeOverrides.mdicCommandDirectionalFaceMaskFallbackAllWhenInside();
    }

    static boolean directionalFaceMaskDebugLog() {
        return ForgeVoxyRuntimeOverrides.mdicCommandDirectionalFaceMaskDebugLog();
    }

    static int maxCommands() {
        return ForgeVoxyRuntimeOverrides.mdicCommandMaxCommands();
    }

    static int maxCommandsPerSection() {
        return ForgeVoxyRuntimeOverrides.mdicCommandMaxCommandsPerSection();
    }

    static boolean debugLog() {
        return ForgeVoxyRuntimeOverrides.mdicCommandDebugLog();
    }
}
