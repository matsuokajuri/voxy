package me.cortex.voxy.forge.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import me.cortex.voxy.common.Logger;
import me.cortex.voxy.forge.ForgeVoxyInstance;
import me.jellysquid.mods.sodium.client.render.SodiumWorldRenderer;
import me.jellysquid.mods.sodium.client.render.chunk.ChunkRenderMatrices;
import me.jellysquid.mods.sodium.client.render.viewport.CameraTransform;
import net.minecraft.client.renderer.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = SodiumWorldRenderer.class, remap = false)
public class ForgeOriginalVoxyEmbeddiumSodiumWorldRendererMixin {
    @Unique
    private static boolean voxy$loggedChunkLayerHook;

    @Inject(method = "drawChunkLayer", at = @At("TAIL"))
    private void voxy$runOriginalCutoutCommandGeneration(
            RenderType renderLayer,
            PoseStack matrixStack,
            double x,
            double y,
            double z,
            CallbackInfo ci) {
        if (renderLayer == RenderType.solid()) {
            if (!voxy$loggedChunkLayerHook) {
                voxy$loggedChunkLayerHook = true;
                Logger.info("Original Voxy Embeddium solid layer command-generation hook reached.");
            }
            ForgeVoxyInstance.INSTANCE.getOriginalVoxyModelPipeline()
                    .renderEmbeddiumCutout(ChunkRenderMatrices.from(matrixStack), new CameraTransform(x, y, z));
        }
    }
}
