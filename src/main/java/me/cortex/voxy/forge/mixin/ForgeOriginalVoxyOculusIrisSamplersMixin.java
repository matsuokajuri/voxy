package me.cortex.voxy.forge.mixin;

import com.google.common.collect.ImmutableSet;
import me.cortex.voxy.forge.ForgeOriginalVoxyOculusSamplers;
import net.irisshaders.iris.gl.sampler.SamplerHolder;
import net.irisshaders.iris.pipeline.IrisRenderingPipeline;
import net.irisshaders.iris.pipeline.WorldRenderingPipeline;
import net.irisshaders.iris.samplers.IrisSamplers;
import net.irisshaders.iris.targets.RenderTargets;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Supplier;

@Mixin(value = IrisSamplers.class, remap = false)
public class ForgeOriginalVoxyOculusIrisSamplersMixin {
    @Inject(method = "addRenderTargetSamplers", at = @At("TAIL"))
    private static void voxy$injectVoxySamplers(
            SamplerHolder samplers,
            Supplier<ImmutableSet<Integer>> flipped,
            RenderTargets renderTargets,
            boolean isFullscreenPass,
            WorldRenderingPipeline pipeline,
            CallbackInfo ci) {
        if (pipeline instanceof IrisRenderingPipeline irisPipeline) {
            ForgeOriginalVoxyOculusSamplers.addSamplers(irisPipeline, samplers);
        }
    }
}
