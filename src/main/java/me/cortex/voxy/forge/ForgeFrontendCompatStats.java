package me.cortex.voxy.forge;

record ForgeFrontendCompatStats(
        String stage,
        boolean forgeFrontendCompatReady,
        boolean forgeModListUsed,
        boolean fabricLoaderUsed,
        boolean sodiumRuntimeModIdUsed,
        boolean irisRuntimeModIdUsed,
        boolean embeddiumRequired,
        boolean oculusRequired,
        boolean embeddiumLoaded,
        boolean oculusLoaded,
        String embeddiumVersion,
        String oculusVersion,
        boolean frontendPrerequisitesReady,
        boolean sodiumApiPackageNamesExpected,
        boolean irisApiPackageNamesExpected,
        boolean javaPackageGlobalRenameAllowed,
        boolean configUiParityReady,
        boolean rendererFrontendHookReady,
        boolean shaderpackFrontendHookReady,
        String lastFailureReason
) {
}
