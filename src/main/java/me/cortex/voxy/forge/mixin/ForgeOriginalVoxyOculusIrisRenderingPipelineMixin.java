package me.cortex.voxy.forge.mixin;

import me.cortex.voxy.forge.ForgeOriginalVoxyOculusPatchDataAccess;
import me.cortex.voxy.forge.ForgeOriginalVoxyOculusPipelineDataAccess;
import me.cortex.voxy.forge.ForgeOriginalVoxyOculusRenderPipelineData;
import me.cortex.voxy.forge.ForgeOriginalVoxyOculusShaderPatch;
import net.irisshaders.iris.gl.buffer.ShaderStorageBufferHolder;
import net.irisshaders.iris.pipeline.IrisRenderingPipeline;
import net.irisshaders.iris.shaderpack.programs.ProgramSet;
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
    @Unique
    private ForgeOriginalVoxyOculusShaderPatch voxy$patchData;
    @Unique
    private ForgeOriginalVoxyOculusRenderPipelineData voxy$pipelineData;

    @Inject(
            method = "<init>",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/irisshaders/iris/pipeline/transform/ShaderPrinter;resetPrintState()V",
                    shift = At.Shift.AFTER))
    private void voxy$injectPatchDataStore(ProgramSet programSet, CallbackInfo ci) {
        this.voxy$patchData = ((ForgeOriginalVoxyOculusPatchDataAccess) programSet).voxy$getPatchData();
    }

    @Inject(
            method = "<init>",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/irisshaders/iris/pipeline/IrisRenderingPipeline;createSetupComputes([Lnet/irisshaders/iris/shaderpack/programs/ComputeSource;Lnet/irisshaders/iris/shaderpack/programs/ProgramSet;Lnet/irisshaders/iris/shaderpack/texture/TextureStage;)[Lnet/irisshaders/iris/gl/program/ComputeProgram;"))
    private void voxy$injectPipeline(ProgramSet programSet, CallbackInfo ci) {
        if (this.voxy$patchData != null) {
            this.voxy$pipelineData = ForgeOriginalVoxyOculusRenderPipelineData.buildPipeline(
                    (IrisRenderingPipeline) (Object) this,
                    this.voxy$patchData,
                    this.customUniforms,
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
