package me.cortex.voxy.forge.mixin;

import me.cortex.voxy.forge.ForgeOriginalVoxyShaderLoadError;
import me.cortex.voxy.forge.VoxyForge;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.shaderpack.ShaderPack;
import net.irisshaders.iris.shaderpack.materialmap.NamespacedId;
import net.irisshaders.iris.shaderpack.programs.ProgramSet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Mirrors original MixinIris: a bad optional Voxy patch disables that pipeline instead of Oculus. */
@Mixin(value = Iris.class, remap = false)
public class ForgeOriginalVoxyOculusIrisMixin {
    @Redirect(
            method = "createPipeline",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/irisshaders/iris/shaderpack/ShaderPack;getProgramSet(Lnet/irisshaders/iris/shaderpack/materialmap/NamespacedId;)Lnet/irisshaders/iris/shaderpack/programs/ProgramSet;"))
    private static ProgramSet voxy$handleMalformedPatch(ShaderPack shaderPack, NamespacedId dimension) {
        try {
            return shaderPack.getProgramSet(dimension);
        } catch (ForgeOriginalVoxyShaderLoadError error) {
            VoxyForge.LOGGER.error("Failed to load Voxy shaderpack patch; disabling this Oculus pipeline.", error);
            return null;
        }
    }
}
