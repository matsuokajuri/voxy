package me.cortex.voxy.forge;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.material.FogType;

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
        Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        if ((!useEnvironmentalFog || isRenderDistanceFog(camera, camera.getFluidInCamera(), end))
                && !fogIsVeryClose) {
            start = DISABLED_FOG_DISTANCE;
            end = DISABLED_FOG_DISTANCE;
        }
        return new ForgeOriginalVoxyFogParameters(start, end, red, green, blue, alpha);
    }

    static float vanillaRenderDistanceBlocks() {
        return Minecraft.getInstance().options.getEffectiveRenderDistance() * 16.0F;
    }

    //1.20.1 exposes a single fog state. Ordinary terrain fog ends at the vanilla render distance,
    // while DimensionSpecialEffects#isFoggyAt (the Nether path) uses a separate half-distance
    // formula. Both are the old-version equivalents of original Voxy's renderDistanceStart/End
    // and must be disabled. Fluid fog and priority mob-effect fog are environmentalStart/End and
    // remain intact. This classification is shared with the RenderFog listener so capture and the
    // live RenderSystem state cannot diverge.
    static boolean isRenderDistanceFog(Camera camera, FogType fogType, float fogEnd) {
        Minecraft minecraft = Minecraft.getInstance();
        boolean clearAir = fogType == FogType.NONE;
        boolean priorityMobEffectFog = camera.getEntity() instanceof LivingEntity living
                && (living.hasEffect(MobEffects.BLINDNESS) || living.hasEffect(MobEffects.DARKNESS));
        boolean dimensionDistanceFog = false;
        if (clearAir && minecraft.level != null) {
            dimensionDistanceFog = minecraft.level.effects().isFoggyAt(
                    Mth.floor(camera.getPosition().x),
                    Mth.floor(camera.getPosition().z));
        }
        return isRenderDistanceFog(
                fogEnd,
                vanillaRenderDistanceBlocks(),
                clearAir,
                dimensionDistanceFog,
                priorityMobEffectFog);
    }

    static boolean isRenderDistanceFog(
            float fogEnd,
            float vanillaRenderDistance,
            boolean clearAir,
            boolean dimensionDistanceFog,
            boolean priorityMobEffectFog) {
        if (!clearAir || priorityMobEffectFog) {
            return false;
        }
        return dimensionDistanceFog || fogEnd >= vanillaRenderDistance * 0.75F;
    }
}
