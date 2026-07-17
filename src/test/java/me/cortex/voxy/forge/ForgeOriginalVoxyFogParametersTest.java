package me.cortex.voxy.forge;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ForgeOriginalVoxyFogParametersTest {
    @Test
    void ordinaryTerrainFogIsRenderDistanceFog() {
        assertTrue(ForgeOriginalVoxyFogParameters.isRenderDistanceFog(
                96.0F,
                96.0F,
                true,
                false,
                false));
    }

    @Test
    void netherDimensionFogIsRenderDistanceFogDespiteHalfDistanceEnd() {
        assertTrue(ForgeOriginalVoxyFogParameters.isRenderDistanceFog(
                48.0F,
                96.0F,
                true,
                true,
                false));
    }

    @Test
    void fluidsAndPriorityMobEffectsRemainEnvironmentalFog() {
        assertFalse(ForgeOriginalVoxyFogParameters.isRenderDistanceFog(
                96.0F,
                96.0F,
                false,
                false,
                false));
        assertFalse(ForgeOriginalVoxyFogParameters.isRenderDistanceFog(
                48.0F,
                96.0F,
                true,
                true,
                true));
    }
}
