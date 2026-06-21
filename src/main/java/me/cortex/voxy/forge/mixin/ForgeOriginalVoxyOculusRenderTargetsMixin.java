package me.cortex.voxy.forge.mixin;

import me.cortex.voxy.forge.VoxyForge;
import net.irisshaders.iris.gl.texture.DepthBufferFormat;
import net.irisshaders.iris.shaderpack.properties.PackDirectives;
import net.irisshaders.iris.shaderpack.properties.PackRenderTargetDirectives;
import net.irisshaders.iris.targets.RenderTarget;
import net.irisshaders.iris.targets.RenderTargets;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Arrays;
import java.util.Map;

@Mixin(value = RenderTargets.class, remap = false)
public class ForgeOriginalVoxyOculusRenderTargetsMixin {
    @Shadow
    @Final
    @Mutable
    private RenderTarget[] targets;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void voxy$expandSparseHighRenderTargets(
            int width,
            int height,
            int depthTexture,
            int depthBufferVersion,
            DepthBufferFormat depthFormat,
            Map<Integer, PackRenderTargetDirectives.RenderTargetSettings> targetSettings,
            PackDirectives packDirectives,
            CallbackInfo ci) {
        int requiredTargetCount = this.targets.length;
        for (Integer target : targetSettings.keySet()) {
            if (target != null && target >= requiredTargetCount) {
                requiredTargetCount = target + 1;
            }
        }
        if (requiredTargetCount > this.targets.length) {
            int oldTargetCount = this.targets.length;
            this.targets = Arrays.copyOf(this.targets, requiredTargetCount);
            VoxyForge.LOGGER.info(
                    "Expanded Oculus sparse render target table from {} to {} entries for high-index shaderpack targets.",
                    oldTargetCount,
                    requiredTargetCount);
        }
    }
}
