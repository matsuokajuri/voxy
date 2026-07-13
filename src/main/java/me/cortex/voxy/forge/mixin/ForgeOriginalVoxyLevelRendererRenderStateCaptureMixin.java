package me.cortex.voxy.forge.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import me.cortex.voxy.forge.ForgeOriginalVoxyOculusPipelineBridge;
import me.cortex.voxy.forge.ForgeOriginalVoxyRenderStateCapture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static org.lwjgl.opengl.GL11C.glViewport;

@Mixin(LevelRenderer.class)
public class ForgeOriginalVoxyLevelRendererRenderStateCaptureMixin {
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
        if (ForgeOriginalVoxyOculusPipelineBridge.shaderpackActive()
                && !ForgeOriginalVoxyOculusPipelineBridge.shadowActive()) {
            glViewport(0, 0, Minecraft.getInstance().getMainRenderTarget().width, Minecraft.getInstance().getMainRenderTarget().height);
        }
    }
}
