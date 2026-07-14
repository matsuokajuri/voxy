package me.cortex.voxy.forge.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import me.cortex.voxy.forge.ForgeOriginalVoxyOculusPipelineBridge;
import me.cortex.voxy.forge.ForgeOriginalVoxyRenderStateCapture;
import me.cortex.voxy.forge.ForgeVoxyInstance;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static org.lwjgl.opengl.GL11C.glViewport;

@Mixin(LevelRenderer.class)
public class ForgeOriginalVoxyLevelRendererRenderStateCaptureMixin {
    @Shadow
    private @Nullable ClientLevel level;

    //Original MixinLevelRenderer shuts the renderer down at this exact boundary, before
    //LevelRenderer#setLevel rebuilds level-dependent render state. The object-identity comparison
    //is intentional: a replacement ClientLevel must rebuild Voxy even when its dimension key is
    //unchanged.
    @Inject(method = "setLevel", at = @At("HEAD"))
    private void voxy$shutdownOriginalRendererForLevelSwitch(ClientLevel level, CallbackInfo ci) {
        if (this.level != level) {
            ForgeVoxyInstance.INSTANCE.onOriginalVoxyClientLevelSwitch(level);
        }
    }

    @Inject(method = "renderLevel", at = @At("HEAD"))
    private void voxy$captureOriginalRawProjection(
            PoseStack poseStack,
            float tickDelta,
            long limitTime,
            boolean renderBlockOutline,
            Camera camera,
            GameRenderer gameRenderer,
            LightTexture lightTexture,
            Matrix4f projection,
            CallbackInfo ci) {
        ForgeOriginalVoxyRenderStateCapture.captureProjection(projection);
        ForgeOriginalVoxyRenderStateCapture.captureLightTexture(lightTexture);
        if (voxy$shouldResetViewport(
                ForgeVoxyInstance.INSTANCE.hasActiveOriginalVoxyRenderOwner(),
                ForgeOriginalVoxyOculusPipelineBridge.shaderpackActive(),
                ForgeOriginalVoxyOculusPipelineBridge.shadowActive())) {
            glViewport(0, 0, Minecraft.getInstance().getMainRenderTarget().width, Minecraft.getInstance().getMainRenderTarget().height);
        }
    }

    @Unique
    private static boolean voxy$shouldResetViewport(
            boolean liveRenderOwner,
            boolean shaderpackActive,
            boolean shadowActive) {
        return liveRenderOwner && shaderpackActive && !shadowActive;
    }
}
