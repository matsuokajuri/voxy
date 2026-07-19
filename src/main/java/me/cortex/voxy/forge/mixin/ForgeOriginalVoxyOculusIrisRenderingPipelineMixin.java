package me.cortex.voxy.forge.mixin;

import me.cortex.voxy.forge.ForgeOriginalVoxyOculusPatchDataAccess;
import me.cortex.voxy.forge.ForgeOriginalVoxyOculusPipelineDataAccess;
import me.cortex.voxy.forge.ForgeOriginalVoxyOculusRenderPipelineData;
import me.cortex.voxy.forge.ForgeOriginalVoxyOculusShaderPatch;
import me.cortex.voxy.forge.ForgeVoxyInstance;
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

    // Original Voxy applies the LevelRenderer-captured camera matrices here, before Iris begins
    // mutating texture/framebuffer state. The Forge port previously rebuilt the viewport from the
    // later Embeddium terrain-pass matrices, which are not the same depth-history owner under
    // Oculus and makes a shadow crossing the vanilla/LOD seam reproject inconsistently.
    @Inject(
            method = "beginLevelRendering",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/blaze3d/systems/RenderSystem;activeTexture(I)V",
                    shift = At.Shift.BEFORE),
            remap = false)
    private void voxy$injectViewportSetup(CallbackInfo ci) {
        var pipeline = ForgeVoxyInstance.INSTANCE.getOriginalVoxyModelPipeline();
        if (pipeline != null) {
            pipeline.prepareOculusViewportFromCapturedState();
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
