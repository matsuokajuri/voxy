package me.cortex.voxy.forge.mixin;

import me.cortex.voxy.config.ForgeVoxyConfig;
import me.cortex.voxy.forge.ForgeOriginalVoxyOculusPatchDataAccess;
import me.cortex.voxy.forge.ForgeOriginalVoxyOculusPipelineBridge;
import me.cortex.voxy.forge.ForgeOriginalVoxyOculusShaderPatch;
import net.irisshaders.iris.shaderpack.ShaderPack;
import net.irisshaders.iris.shaderpack.include.AbsolutePackPath;
import net.irisshaders.iris.shaderpack.programs.ProgramSet;
import net.irisshaders.iris.shaderpack.properties.PackRenderTargetDirectives;
import net.irisshaders.iris.shaderpack.properties.ShaderProperties;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Function;

@Mixin(value = ProgramSet.class, remap = false)
public class ForgeOriginalVoxyOculusProgramSetMixin implements ForgeOriginalVoxyOculusPatchDataAccess {
    @Unique
    private ForgeOriginalVoxyOculusShaderPatch voxy$patchData;

    @Redirect(
            method = "<init>",
            at = @At(
                    value = "FIELD",
                    target = "Lnet/irisshaders/iris/shaderpack/properties/PackRenderTargetDirectives;BASELINE_SUPPORTED_RENDER_TARGETS:Ljava/util/Set;"))
    private Set<Integer> voxy$baselineSupportedRenderTargets(
            AbsolutePackPath directory,
            Function<AbsolutePackPath, String> sourceProvider,
            ShaderProperties shaderProperties,
            ShaderPack pack) {
        return voxy$mergeVoxyRenderTargets(
                PackRenderTargetDirectives.BASELINE_SUPPORTED_RENDER_TARGETS,
                directory,
                sourceProvider);
    }

    @Unique
    private static Set<Integer> voxy$mergeVoxyRenderTargets(
            Set<Integer> supportedRenderTargets,
            AbsolutePackPath directory,
            Function<AbsolutePackPath, String> sourceProvider) {
        Set<Integer> extraTargets = Set.of();
        if (ForgeVoxyConfig.isEnabledEarlySafe()) {
            extraTargets = ForgeOriginalVoxyOculusShaderPatch.collectRequestedRenderTargets(directory, sourceProvider);
        }
        if (extraTargets.isEmpty()) {
            return supportedRenderTargets;
        }
        Set<Integer> mergedTargets = new LinkedHashSet<>(supportedRenderTargets);
        for (Integer target : extraTargets) {
            if (target != null && !PackRenderTargetDirectives.BASELINE_SUPPORTED_RENDER_TARGETS.contains(target)) {
                mergedTargets.add(target);
            }
        }
        return mergedTargets;
    }

    @Inject(method = "<init>", at = @At("TAIL"))
    private void voxy$injectPatchMaker(
            AbsolutePackPath directory,
            Function<AbsolutePackPath, String> sourceProvider,
            ShaderProperties shaderProperties,
            ShaderPack pack,
            CallbackInfo ci) {
        ForgeOriginalVoxyOculusShaderPatch patch = null;
        if (ForgeOriginalVoxyOculusPipelineBridge.shouldExposeVoxyShaderpackPatch()) {
            patch = ForgeOriginalVoxyOculusShaderPatch.makePatch(pack, directory, sourceProvider);
        }
        this.voxy$patchData = patch;
        ForgeOriginalVoxyOculusPipelineBridge.captureCurrentShaderpackPatch(patch);
    }

    @Override
    public ForgeOriginalVoxyOculusShaderPatch voxy$getPatchData() {
        return this.voxy$patchData;
    }
}
