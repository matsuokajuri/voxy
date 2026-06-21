package me.cortex.voxy.forge.mixin;

import me.cortex.voxy.forge.ForgeOriginalVoxyOculusPipelineBridge;
import me.cortex.voxy.forge.ForgeOriginalVoxyOculusVoxyUniforms;
import net.irisshaders.iris.gl.uniform.UniformHolder;
import net.irisshaders.iris.shaderpack.IdMap;
import net.irisshaders.iris.shaderpack.properties.PackDirectives;
import net.irisshaders.iris.uniforms.CommonUniforms;
import net.irisshaders.iris.uniforms.FrameUpdateNotifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = CommonUniforms.class, remap = false)
public class ForgeOriginalVoxyOculusMatrixUniformsMixin {
    @Inject(method = "addNonDynamicUniforms", at = @At("HEAD"))
    private static void voxy$injectVoxyMatrixUniforms(
            UniformHolder uniforms,
            IdMap idMap,
            PackDirectives directives,
            FrameUpdateNotifier updateNotifier,
            CallbackInfo ci) {
        if (ForgeOriginalVoxyOculusPipelineBridge.shouldExposeVoxyShaderpackPatch()) {
            ForgeOriginalVoxyOculusVoxyUniforms.addUniforms(uniforms);
        }
    }
}
