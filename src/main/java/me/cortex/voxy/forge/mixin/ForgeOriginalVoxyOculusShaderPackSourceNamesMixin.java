package me.cortex.voxy.forge.mixin;

import com.google.common.collect.ImmutableList;
import net.irisshaders.iris.shaderpack.include.ShaderPackSourceNames;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = ShaderPackSourceNames.class, remap = false)
public class ForgeOriginalVoxyOculusShaderPackSourceNamesMixin {
    @Inject(method = "findPotentialStarts", at = @At("RETURN"), cancellable = true)
    private static void voxy$includeVoxyPatchSidecars(CallbackInfoReturnable<ImmutableList<String>> cir) {
        cir.setReturnValue(ImmutableList.<String>builder()
                .addAll(cir.getReturnValue())
                .add("voxy.json")
                .add("voxy_opaque.glsl")
                .add("voxy_translucent.glsl")
                .add("voxy_taa.glsl")
                .build());
    }
}
