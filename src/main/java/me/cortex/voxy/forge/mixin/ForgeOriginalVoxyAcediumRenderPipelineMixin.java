package me.cortex.voxy.forge.mixin;

import me.cortex.voxy.forge.ForgeVoxyInstance;
import me.jellysquid.mods.sodium.client.render.chunk.ChunkRenderMatrices;
import me.jellysquid.mods.sodium.client.render.viewport.Viewport;
import me.jellysquid.mods.sodium.client.render.viewport.frustum.Frustum;
import me.jellysquid.mods.sodium.client.render.viewport.frustum.SimpleFrustum;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Original Nvidium render hook adapted to the Acedium Forge 1.20.1 fork. */
@Pseudo
@Mixin(targets = "me.cortex.nvidium.RenderPipeline", remap = false)
public class ForgeOriginalVoxyAcediumRenderPipelineMixin {
    @Inject(
            method = "renderFrame(Lme/jellysquid/mods/sodium/client/render/viewport/Viewport;"
                    + "Lme/jellysquid/mods/sodium/client/render/chunk/ChunkRenderMatrices;DDD)V",
            at = @At("RETURN"),
            require = 1)
    private void voxy$renderAfterAcediumTerrain(
            Viewport viewport,
            ChunkRenderMatrices matrices,
            double cameraX,
            double cameraY,
            double cameraZ,
            CallbackInfo ci) {
        Frustum frustum = ((ForgeOriginalVoxyEmbeddiumViewportAccessor) (Object) viewport)
                .voxy$getFrustum();
        if (!(frustum instanceof SimpleFrustum)) {
            throw new IllegalStateException(
                    "Acedium supplied unsupported Embeddium frustum " + frustum.getClass().getName());
        }
        ForgeVoxyInstance.INSTANCE.renderOriginalVoxyAfterTerrain(
                matrices,
                cameraX,
                cameraY,
                cameraZ,
                ((ForgeOriginalVoxyEmbeddiumSimpleFrustumAccessor) (Object) frustum)
                        .voxy$getFrustumIntersection());
    }
}
