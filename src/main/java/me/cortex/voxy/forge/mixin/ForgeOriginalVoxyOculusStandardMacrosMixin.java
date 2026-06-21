package me.cortex.voxy.forge.mixin;

import com.google.common.collect.ImmutableList;
import me.cortex.voxy.forge.ForgeOriginalVoxyOculusPipelineBridge;
import me.cortex.voxy.forge.ForgeOriginalVoxyOculusShaderPatch;
import net.irisshaders.iris.gl.shader.StandardMacros;
import net.irisshaders.iris.helpers.StringPair;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = StandardMacros.class, remap = false)
public class ForgeOriginalVoxyOculusStandardMacrosMixin {
    @Inject(method = "createStandardEnvironmentDefines", at = @At("RETURN"), cancellable = true)
    private static void voxy$injectVoxyDefine(CallbackInfoReturnable<ImmutableList<StringPair>> cir) {
        if (!ForgeOriginalVoxyOculusPipelineBridge.shouldExposeVoxyShaderpackPatch()) {
            return;
        }
        ImmutableList<StringPair> existing = cir.getReturnValue();
        for (StringPair pair : existing) {
            if ("VOXY".equals(pair.key())) {
                return;
            }
        }
        cir.setReturnValue(ImmutableList.<StringPair>builder()
                .addAll(existing)
                .add(new StringPair(
                        "VOXY",
                        Integer.toString(ForgeOriginalVoxyOculusShaderPatch.SHADER_DEFINE_VERSION)))
                .build());
    }
}
