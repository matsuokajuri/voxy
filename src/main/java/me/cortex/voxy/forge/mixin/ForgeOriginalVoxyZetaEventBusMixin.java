package me.cortex.voxy.forge.mixin;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Collections;
import java.util.Map;

/** Protects Zeta 1.0-31's listener bookkeeping during Forge's parallel mod construction. */
@Pseudo
@Mixin(targets = "org.violetmoon.zetaimplforge.event.ForgeZetaEventBus", remap = false)
public abstract class ForgeOriginalVoxyZetaEventBusMixin {
    @Shadow(remap = false)
    @Final
    @Mutable
    private Map<Object, Object> convertedHandlers;

    @Inject(method = "<init>", at = @At("RETURN"), remap = false, require = 1)
    private void voxy$synchronizeConvertedHandlers(CallbackInfo ci) {
        // The exact 1.0-31 owner writes via Map.put in subscribeMethod and removes via
        // Map.remove in unsubscribeMethod, outside its Forge bus/remapper locks. Wrap before
        // publication so every access uses the same monitor. Keep the original map, keys,
        // values and null semantics; do not skip listeners or serialize event dispatch.
        this.convertedHandlers = Collections.synchronizedMap(this.convertedHandlers);
    }
}
