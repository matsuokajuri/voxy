package me.cortex.voxy.forge;

import me.cortex.voxy.config.ForgeVoxyConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
        assertEquals(ForgeOriginalVoxySSAO.SSAOMode.BEST, ForgeOriginalVoxySSAO.modeFromConfig("best"));
        assertEquals(ForgeOriginalVoxySSAO.SSAOMode.AUTO, ForgeOriginalVoxySSAO.modeFromConfig(null));
        assertEquals(ForgeOriginalVoxySSAO.SSAOMode.AUTO, ForgeOriginalVoxySSAO.modeFromConfig("unknown"));
    }
}
