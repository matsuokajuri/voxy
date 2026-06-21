package me.cortex.voxy.forge.mixin;

import me.cortex.voxy.config.ForgeVoxyConfig;
import me.cortex.voxy.forge.ForgeOriginalVoxyOculusPatchDataAccess;
import me.cortex.voxy.forge.ForgeOriginalVoxyOculusShaderPatch;
import net.irisshaders.iris.shaderpack.ShaderPack;
import net.irisshaders.iris.shaderpack.include.AbsolutePackPath;
import net.irisshaders.iris.shaderpack.programs.ProgramSet;
import net.irisshaders.iris.shaderpack.properties.ShaderProperties;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Function;

@Mixin(value = ProgramSet.class, remap = false)
public class ForgeOriginalVoxyOculusProgramSetMixin implements ForgeOriginalVoxyOculusPatchDataAccess {
    @Unique
    private ForgeOriginalVoxyOculusShaderPatch voxy$patchData;

    @Inject(
            method = "<init>",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/irisshaders/iris/shaderpack/programs/ProgramSet;locateDirectives()V",
                    shift = At.Shift.BEFORE))
    private void voxy$injectPatchMaker(
            AbsolutePackPath directory,
            Function<AbsolutePackPath, String> sourceProvider,
            ShaderProperties shaderProperties,
            ShaderPack pack,
            CallbackInfo ci) {
        if (ForgeVoxyConfig.ENABLED.get()) {
            this.voxy$patchData = ForgeOriginalVoxyOculusShaderPatch.makePatch(pack, directory, sourceProvider);
        }
    }

    @Override
    public ForgeOriginalVoxyOculusShaderPatch voxy$getPatchData() {
        return this.voxy$patchData;
    }
}
