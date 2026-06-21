package me.cortex.voxy.forge;

import com.mojang.blaze3d.systems.RenderSystem;

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
        if (!useEnvironmentalFog && !fogIsVeryClose) {
            start = DISABLED_FOG_DISTANCE;
            end = DISABLED_FOG_DISTANCE;
        }
        return new ForgeOriginalVoxyFogParameters(start, end, red, green, blue, alpha);
    }
}
