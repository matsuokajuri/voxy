package me.cortex.voxy.forge.mixin;

import me.cortex.voxy.forge.ForgeOriginalVoxyOculusShadowCasterRange;
import me.cortex.voxy.forge.VoxyForge;
import me.jellysquid.mods.sodium.client.gl.device.CommandList;
import me.jellysquid.mods.sodium.client.render.chunk.RenderSectionManager;
import net.irisshaders.iris.shadows.ShadowRenderingState;
import net.minecraft.client.multiplayer.ClientLevel;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Reserves three hidden Embeddium chunk rings for the Oculus shadow pass while
 * retaining the player's configured distance for the normal terrain pass.
 */
@Mixin(value = RenderSectionManager.class, remap = false)
public abstract class ForgeOriginalVoxyOculusShadowCasterRangeMixin {
    @Shadow
    @Final
    private int renderDistance;

    @ModifyVariable(method = "<init>", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private static int voxy$expandShadowCasterMeshRange(int visibleRenderDistanceChunks) {
        return ForgeOriginalVoxyOculusShadowCasterRange.loadedRenderDistanceChunks(
                visibleRenderDistanceChunks);
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void voxy$reportShadowCasterMeshRange(
            ClientLevel world,
            int visibleRenderDistanceChunks,
            CommandList commandList,
            CallbackInfo ci) {
        int configuredVisibleRenderDistanceChunks =
                ForgeOriginalVoxyOculusShadowCasterRange.visibleRenderDistanceChunks();
        if (this.renderDistance > configuredVisibleRenderDistanceChunks) {
            VoxyForge.LOGGER.info(
                    "Reserved Oculus shadow-caster chunk range: visible={}, loaded={}.",
                    configuredVisibleRenderDistanceChunks,
                    this.renderDistance);
        }
    }

    @Inject(method = "getSearchDistance", at = @At("RETURN"), cancellable = true)
    private void voxy$selectVisibleOrShadowCasterRange(CallbackInfoReturnable<Float> cir) {
        int visibleRenderDistanceChunks =
                ForgeOriginalVoxyOculusShadowCasterRange.visibleRenderDistanceChunks();
        if (this.renderDistance <= visibleRenderDistanceChunks) {
            return;
        }

        boolean hiddenCastersVisible = ForgeOriginalVoxyOculusShadowCasterRange.active()
                && ShadowRenderingState.areShadowsCurrentlyBeingRendered();
        if (!hiddenCastersVisible) {
            cir.setReturnValue(Math.min(
                    cir.getReturnValue(),
                    ForgeOriginalVoxyOculusShadowCasterRange.visibleRenderDistanceBlocks()));
        }
    }
}
