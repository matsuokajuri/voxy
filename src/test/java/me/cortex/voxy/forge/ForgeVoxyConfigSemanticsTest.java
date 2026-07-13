package me.cortex.voxy.forge;

import me.cortex.voxy.config.ForgeVoxyConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ForgeVoxyConfigSemanticsTest {
    @Test
    void cleanConfigDefaultsEnableTheFormalRouteAndIngest() {
        assertTrue(ForgeVoxyConfig.ENABLED.getDefault());
        assertTrue(ForgeVoxyConfig.RENDERING_ENABLED.getDefault());
        assertTrue(ForgeVoxyConfig.INGEST_ENABLED.getDefault());
        assertEquals("AUTO", ForgeVoxyConfig.ORIGINAL_VOXY_SSAO_MODE.getDefault());
    }

    @Test
    void ssaoModeParsingMatchesOriginalFallbackSemantics() {
        assertEquals(SSAO.SSAOMode.BEST, SSAO.modeFromConfig("best"));
        assertEquals(SSAO.SSAOMode.AUTO, SSAO.modeFromConfig(null));
        assertEquals(SSAO.SSAOMode.AUTO, SSAO.modeFromConfig("unknown"));
    }

    @Test
    void embeddiumSlidersPreserveTheOriginalMenuTransforms() {
        assertEquals(0.625D, ForgeOriginalVoxyConfigSnapshot.inputToRenderDistance(10));
        assertEquals(64.0D, ForgeOriginalVoxyConfigSnapshot.inputToRenderDistance(64 * 16));
        assertEquals(10, ForgeOriginalVoxyConfigSnapshot.renderDistanceToInput(0.625D));
        assertEquals(256, Math.round(ForgeOriginalVoxyConfigSnapshot.inputToSubdivision(100)));
        assertEquals(28, Math.round(ForgeOriginalVoxyConfigSnapshot.inputToSubdivision(0)));
        assertTrue(Math.abs(50 - ForgeOriginalVoxyConfigSnapshot.subdivisionToInput(
                ForgeOriginalVoxyConfigSnapshot.inputToSubdivision(50))) <= 1);
    }

    @Test
    void embeddiumPageAndOptionIdentifiersInitializeWithoutTypeCollisions() {
        assertDoesNotThrow(() -> Class.forName(
                "me.cortex.voxy.forge.ForgeOriginalVoxyEmbeddiumOptions"));
    }

    @Test
    void sharedApplyStateClassifiesEachRuntimeAction() {
        ForgeOriginalVoxyConfigSnapshot baseline = snapshot(8, true, 16.0D, 64.0D, true, "AUTO");

        ForgeOriginalVoxyConfigSnapshot.ChangeSet serviceThreads =
                snapshot(9, true, 16.0D, 64.0D, true, "AUTO").changesFrom(baseline);
        assertTrue(serviceThreads.threadPolicyChanged());
        assertFalse(serviceThreads.instanceReload());
        assertFalse(serviceThreads.rendererReload());
        assertFalse(serviceThreads.oculusReload());
        assertFalse(serviceThreads.renderDistanceChanged());

        ForgeOriginalVoxyConfigSnapshot.ChangeSet sharedThreads =
                snapshot(8, false, 16.0D, 64.0D, true, "AUTO").changesFrom(baseline);
        assertTrue(sharedThreads.threadPolicyChanged());
        assertTrue(sharedThreads.rendererReload());
        assertTrue(sharedThreads.vanillaRendererReload());
        assertFalse(sharedThreads.oculusReload());

        ForgeOriginalVoxyConfigSnapshot.ChangeSet renderDistance =
                snapshot(8, true, 17.0D, 64.0D, true, "AUTO").changesFrom(baseline);
        assertTrue(renderDistance.renderDistanceChanged());
        assertFalse(renderDistance.threadPolicyChanged());
        assertFalse(renderDistance.rendererReload());

        ForgeOriginalVoxyConfigSnapshot.ChangeSet shaderSettings =
                snapshot(8, true, 16.0D, 80.0D, false, "BEST").changesFrom(baseline);
        assertTrue(shaderSettings.rendererReload());
        assertTrue(shaderSettings.vanillaRendererReload());
        assertFalse(shaderSettings.oculusReload());
        assertFalse(shaderSettings.threadPolicyChanged());
        assertFalse(shaderSettings.renderDistanceChanged());

        ForgeOriginalVoxyConfigSnapshot.ChangeSet enabled = new ForgeOriginalVoxyConfigSnapshot(
                false, true, true, 8, true, 16.0D, 64.0D, true, "AUTO").changesFrom(baseline);
        assertTrue(enabled.instanceReload());
        assertTrue(enabled.oculusReload());
        assertFalse(enabled.rendererReload());

        ForgeOriginalVoxyConfigSnapshot.ChangeSet rendering = new ForgeOriginalVoxyConfigSnapshot(
                true, false, true, 8, true, 16.0D, 64.0D, true, "AUTO").changesFrom(baseline);
        assertFalse(rendering.instanceReload());
        assertTrue(rendering.rendererReload());
        assertFalse(rendering.vanillaRendererReload());
        assertTrue(rendering.oculusReload());

        ForgeOriginalVoxyConfigSnapshot.ChangeSet liveConfig = new ForgeOriginalVoxyConfigSnapshot(
                true, true, false, 8, true, 16.0D, 80.0D, true, "AUTO").changesFrom(baseline);
        assertFalse(liveConfig.instanceReload());
        assertFalse(liveConfig.rendererReload());
        assertFalse(liveConfig.oculusReload());
        assertFalse(liveConfig.threadPolicyChanged());
        assertFalse(liveConfig.renderDistanceChanged());
    }

    private static ForgeOriginalVoxyConfigSnapshot snapshot(
            int serviceThreads,
            boolean useEmbeddiumThreads,
            double renderDistance,
            double subdivisionSize,
            boolean environmentalFog,
            String ssaoMode) {
        return new ForgeOriginalVoxyConfigSnapshot(
                true,
                true,
                true,
                serviceThreads,
                useEmbeddiumThreads,
                renderDistance,
                subdivisionSize,
                environmentalFog,
                ssaoMode);
    }
}
