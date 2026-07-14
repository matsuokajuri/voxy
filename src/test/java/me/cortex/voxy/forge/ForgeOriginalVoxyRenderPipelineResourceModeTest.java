package me.cortex.voxy.forge;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ForgeOriginalVoxyRenderPipelineResourceModeTest {
    @Test
    void normalModeOwnsOnlyNormalPipelineResources() {
        ForgeOriginalVoxyRenderPipeline.ResourceMode mode =
                ForgeOriginalVoxyRenderPipeline.resourceMode(false);

        assertSame(ForgeOriginalVoxyRenderPipeline.ResourceMode.NORMAL, mode);
        assertTrue(mode.ownsNormalResources());
        assertFalse(mode.ownsOculusResources());
    }

    @Test
    void oculusModeOwnsOnlyOculusPipelineResources() {
        ForgeOriginalVoxyRenderPipeline.ResourceMode mode =
                ForgeOriginalVoxyRenderPipeline.resourceMode(true);

        assertSame(ForgeOriginalVoxyRenderPipeline.ResourceMode.OCULUS, mode);
        assertFalse(mode.ownsNormalResources());
        assertTrue(mode.ownsOculusResources());
    }
}
