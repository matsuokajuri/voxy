package me.cortex.voxy.forge.mixin;

import me.cortex.voxy.forge.ForgeVoxyInstance;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Minecraft 1.20.1 anchor for original Voxy's post-level-detach session shutdown. */
@Mixin(Minecraft.class)
public class ForgeOriginalVoxyMinecraftMixin {
    @Inject(method = "clearLevel(Lnet/minecraft/client/gui/screens/Screen;)V", at = @At("TAIL"))
    private void voxy$endOriginalSession(Screen screen, CallbackInfo ci) {
        ForgeVoxyInstance.INSTANCE.endOriginalVoxyClientSession();
    }
}
