package me.cortex.voxy.forge;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;

record ForgeOriginalVoxyFogParameters(
        float environmentalStart,
        float environmentalEnd,
        float red,
        float green,
        float blue,
        float alpha
) {
    private static final float DISABLED_FOG_DISTANCE = 99_999_999.0F;

    static ForgeOriginalVoxyFogParameters disabled() {
        return new ForgeOriginalVoxyFogParameters(
                DISABLED_FOG_DISTANCE,
                DISABLED_FOG_DISTANCE,
                0.0F,
                0.0F,
                0.0F,
                0.0F);
    }

    static ForgeOriginalVoxyFogParameters captureFromRenderSystem(boolean useEnvironmentalFog) {
        float start = RenderSystem.getShaderFogStart();
        float end = RenderSystem.getShaderFogEnd();
        float[] colour = RenderSystem.getShaderFogColor();
        float red = colour.length > 0 ? colour[0] : 0.0F;
        float green = colour.length > 1 ? colour[1] : 0.0F;
        float blue = colour.length > 2 ? colour[2] : 0.0F;
        float alpha = colour.length > 3 ? colour[3] : 1.0F;

        boolean fogIsVeryClose = end < 10.0F;
        //1.20.1 exposes a single fog state, so the terrain render-distance fog (its end tracks the
        // vanilla render distance) arrives here too. Original Voxy's environmentalEnd carries only
        // genuinely environmental fog (weather/lava/nether); letting render-distance fog through
        // made fogCoversAllRendering true every frame on the no-shaderpack path, which skipped the
        // final blit and blanked all LOD. Classify near-render-distance fog as non-environmental.
        float renderDistanceBlocks = Minecraft.getInstance().options.getEffectiveRenderDistance() * 16.0F;
        boolean renderDistanceFog = end >= renderDistanceBlocks * 0.75F;
        if ((!useEnvironmentalFog || renderDistanceFog) && !fogIsVeryClose) {
            start = DISABLED_FOG_DISTANCE;
            end = DISABLED_FOG_DISTANCE;
        }
        return new ForgeOriginalVoxyFogParameters(start, end, red, green, blue, alpha);
    }
}
