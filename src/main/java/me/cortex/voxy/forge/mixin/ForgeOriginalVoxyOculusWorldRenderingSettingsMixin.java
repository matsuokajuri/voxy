package me.cortex.voxy.forge.mixin;

import me.cortex.voxy.forge.ForgeVoxyInstance;
import net.irisshaders.iris.shaderpack.materialmap.WorldRenderingSettings;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = WorldRenderingSettings.class, remap = false)
public class ForgeOriginalVoxyOculusWorldRenderingSettingsMixin {
    @Inject(method = "clearReloadRequired", at = @At("HEAD"))
    private void voxy$onClearReloadRequired(CallbackInfo ci) {
        ForgeVoxyInstance.INSTANCE.markOriginalVoxyOculusWorldRenderingSettingsReload();
    }
}
