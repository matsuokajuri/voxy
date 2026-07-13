package me.cortex.voxy.forge.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import me.cortex.voxy.common.util.ThreadUtils;
import me.cortex.voxy.forge.GPUSelectorWindows2;
import net.minecraft.client.Minecraft;
import net.minecraft.util.TimeSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(Minecraft.class)
public class ForgeOriginalVoxyGpuSelectMixin {
    // Original Voxy hoists this work ahead of window creation at an Options#save call that
    // does not exist in Minecraft 1.20.1. initBackendSystem is the equivalent 1.20.1 boundary:
    // it is the sole backend-init call and immediately precedes VirtualScreen#newWindow.
    // Mixin 0.8.5 only permits callback injection at restricted constructor points, so redirect
    // this single static invocation and preserve its result instead of weakening the injection.
    @Redirect(
            method = "<init>(Lnet/minecraft/client/main/GameConfig;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/blaze3d/systems/RenderSystem;initBackendSystem()Lnet/minecraft/util/TimeSource$NanoTimeSource;"))
    private TimeSource.NanoTimeSource voxy$selectGpuAndRaiseThreadPriority() {
        String property = System.getProperty("voxy.forceGpuSelectionIndex", "NO");
        if (!property.equals("NO")) {
            GPUSelectorWindows2.doSelector(Integer.parseInt(property));
        }

        Thread.currentThread().setPriority(Thread.MAX_PRIORITY);
        ThreadUtils.SetSelfThreadPriorityWin32(ThreadUtils.WIN32_THREAD_PRIORITY_TIME_CRITICAL);
        return RenderSystem.initBackendSystem();
    }
}
