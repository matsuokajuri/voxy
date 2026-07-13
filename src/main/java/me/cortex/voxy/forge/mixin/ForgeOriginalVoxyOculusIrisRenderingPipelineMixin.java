package me.cortex.voxy.forge.mixin;

import me.cortex.voxy.forge.ForgeOriginalVoxyOculusPatchDataAccess;
import me.cortex.voxy.forge.ForgeOriginalVoxyOculusPipelineDataAccess;
import me.cortex.voxy.forge.ForgeOriginalVoxyOculusRenderPipelineData;
import me.cortex.voxy.forge.ForgeOriginalVoxyOculusShaderPatch;
import net.irisshaders.iris.gl.buffer.ShaderStorageBufferHolder;
import net.irisshaders.iris.pipeline.IrisRenderingPipeline;
import net.irisshaders.iris.shaderpack.programs.ProgramSet;
import net.irisshaders.iris.uniforms.FrameUpdateNotifier;
import net.irisshaders.iris.uniforms.custom.CustomUniforms;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = IrisRenderingPipeline.class, remap = false)
public class ForgeOriginalVoxyOculusIrisRenderingPipelineMixin implements ForgeOriginalVoxyOculusPatchDataAccess, ForgeOriginalVoxyOculusPipelineDataAccess {
    @Shadow
    @Final
    private CustomUniforms customUniforms;
    @Shadow
    private ShaderStorageBufferHolder shaderStorageBufferHolder;
    @Shadow
    @Final
    private FrameUpdateNotifier updateNotifier;
    @Unique
    private ForgeOriginalVoxyOculusShaderPatch voxy$patchData;
    @Unique
    private ForgeOriginalVoxyOculusRenderPipelineData voxy$pipelineData;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void voxy$injectPipeline(ProgramSet programSet, CallbackInfo ci) {
        this.voxy$patchData = ((ForgeOriginalVoxyOculusPatchDataAccess) programSet).voxy$getPatchData();
        if (this.voxy$patchData != null) {
            this.voxy$pipelineData = ForgeOriginalVoxyOculusRenderPipelineData.buildPipeline(
                    (IrisRenderingPipeline) (Object) this,
                    this.voxy$patchData,
                    this.customUniforms,
                    this.updateNotifier,
                    this.shaderStorageBufferHolder);
        }
    }

    @Override
    public ForgeOriginalVoxyOculusShaderPatch voxy$getPatchData() {
        return this.voxy$patchData;
    }

    @Override
    public ForgeOriginalVoxyOculusRenderPipelineData voxy$getPipelineData() {
        return this.voxy$pipelineData;
    }
}
