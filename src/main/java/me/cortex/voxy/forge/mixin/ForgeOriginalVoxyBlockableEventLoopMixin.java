package me.cortex.voxy.forge.mixin;

import net.minecraft.util.thread.BlockableEventLoop;
import org.slf4j.Logger;
import org.slf4j.Marker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Restores original Voxy's fail-fast handling for exceptions raised while the
 * initial client login packet is being applied.
 *
 * <p>Newer Minecraft routes packet failures through
 * ClientCommonPacketListenerImpl#onPacketError and then asks
 * BlockableEventLoop#isNonRecoverable. In 1.20.1 PacketUtils already propagates
 * the handler exception, but BlockableEventLoop logs and consumes it. Detecting
 * the login handler frame at that existing catch boundary preserves the same
 * observable behavior without replacing the packet pipeline.</p>
 */
@Mixin(BlockableEventLoop.class)
public class ForgeOriginalVoxyBlockableEventLoopMixin {
    @Redirect(
            method = "doRunTask",
            at = @At(
                    value = "INVOKE",
                    target = "Lorg/slf4j/Logger;error(Lorg/slf4j/Marker;Ljava/lang/String;Ljava/lang/Object;Ljava/lang/Object;)V",
                    remap = false))
    private void voxy$forceCrashOnLoginFailure(
            Logger logger,
            Marker marker,
            String format,
            Object taskName,
            Object failure) {
        if (failure instanceof Throwable throwable && voxy$isLoginHandlingFailure(throwable)) {
            if (throwable instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new RuntimeException("Force crashing due to exception during game join", throwable);
        }
        logger.error(marker, format, taskName, failure);
    }

    @Unique
    private static boolean voxy$isLoginHandlingFailure(Throwable throwable) {
        for (StackTraceElement element : throwable.getStackTrace()) {
            if (element.getClassName().equals("net.minecraft.client.multiplayer.ClientPacketListener")
                    && element.getMethodName().equals("handleLogin")) {
                return true;
            }
        }
        return false;
    }
}
