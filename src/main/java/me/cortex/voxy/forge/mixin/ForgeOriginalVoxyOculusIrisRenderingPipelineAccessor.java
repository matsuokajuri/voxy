package me.cortex.voxy.forge.mixin;

import net.irisshaders.iris.pipeline.IrisRenderingPipeline;
import net.irisshaders.iris.targets.RenderTargets;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = IrisRenderingPipeline.class, remap = false)
public interface ForgeOriginalVoxyOculusIrisRenderingPipelineAccessor {
    @Accessor("renderTargets")
    RenderTargets voxy$getRenderTargets();
}
