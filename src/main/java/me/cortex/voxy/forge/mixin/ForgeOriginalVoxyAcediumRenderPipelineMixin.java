package me.cortex.voxy.forge.mixin;

import me.cortex.voxy.forge.ForgeVoxyInstance;
import me.jellysquid.mods.sodium.client.render.chunk.ChunkRenderMatrices;
import me.jellysquid.mods.sodium.client.render.viewport.Viewport;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Original Nvidium render hook adapted to the Acedium Forge 1.20.1 fork. */
@Pseudo
@Mixin(targets = "me.cortex.nvidium.RenderPipeline", remap = false)
public class ForgeOriginalVoxyAcediumRenderPipelineMixin {
    @Inject(method = "renderFrame", at = @At("RETURN"), require = 0)
    private void voxy$renderAfterAcediumTerrain(
            Viewport viewport,
            ChunkRenderMatrices matrices,
            double cameraX,
            double cameraY,
            double cameraZ,
            CallbackInfo ci) {
        ForgeVoxyInstance.INSTANCE.renderOriginalVoxyAfterTerrain(matrices, viewport.getTransform());
    }
}
