package me.cortex.voxy.forge;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ForgeOriginalVoxyOculusShadowCasterRangeTest {
    @Test
    void reservesThreeHiddenChunkRings() {
        assertEquals(9, ForgeOriginalVoxyOculusShadowCasterRange.expandRenderDistanceChunks(6));
    }

    @Test
    void doesNotExceedMinecraftMaximumRenderDistance() {
        assertEquals(32, ForgeOriginalVoxyOculusShadowCasterRange.expandRenderDistanceChunks(30));
        assertEquals(32, ForgeOriginalVoxyOculusShadowCasterRange.expandRenderDistanceChunks(32));
    }
}
