package me.cortex.voxy.forge.mixin;

import me.cortex.voxy.forge.ForgeOriginalVoxyOculusShadowCasterRange;
import net.minecraft.client.server.IntegratedServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Supplies the hidden shadow-caster rings in single-player without changing the saved client option. */
@Mixin(IntegratedServer.class)
public class ForgeOriginalVoxyOculusIntegratedServerShadowCasterRangeMixin {
    @Redirect(
            method = "tickServer",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/lang/Math;max(II)I",
                    ordinal = 0))
    private int voxy$expandIntegratedServerViewDistance(int minimum, int configured) {
        int visibleRenderDistanceChunks = Math.max(minimum, configured);
        return ForgeOriginalVoxyOculusShadowCasterRange.loadedRenderDistanceChunks(
                visibleRenderDistanceChunks);
    }
}
