package me.cortex.voxy.forge;

record ForgeFormalRendererReadiness(
        boolean geometryHeapReady,
        boolean metadataReady,
        boolean sectionGeometryManagerReady,
        boolean mdicCommandReady,
        boolean mdicDrawCountReady,
        boolean modelBridgeReady,
        boolean formalShaderInputBridgeReady,
        boolean atlasReady,
        boolean resourceReloadReady,
        boolean worldEngineReady,
        boolean dimensionReady
) {
}
