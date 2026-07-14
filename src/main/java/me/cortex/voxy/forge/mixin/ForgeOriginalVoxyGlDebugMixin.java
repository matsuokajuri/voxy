package me.cortex.voxy.forge.mixin;

import com.mojang.blaze3d.platform.GlDebug;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * Minecraft 1.20.1 counterpart to original Voxy's {@code MixinGlDebug}.
 *
 * <p>The upstream target moved from {@code com.mojang.blaze3d.platform.GlDebug}
 * to {@code com.mojang.blaze3d.opengl.GlDebug} after 1.20.1. The logging call
 * and its ownership are otherwise the same, so preserve the original behavior
 * at the 1.20.1 call site.</p>
 */
@Mixin(GlDebug.class)
public class ForgeOriginalVoxyGlDebugMixin {
    @Redirect(
            method = "printDebugLog",
            at = @At(
                    value = "INVOKE",
                    target = "Lorg/slf4j/Logger;info(Ljava/lang/String;Ljava/lang/Object;)V",
                    remap = false))
    private static void voxy$annotateVoxyDebugMessage(Logger logger, String format, Object message) {
        if (message == null
                || !"com.mojang.blaze3d.platform.GlDebug$LogEntry".equals(message.getClass().getName())) {
            logger.info(format, message);
            return;
        }

        Throwable origin = new Throwable(message.toString());
        StackTraceElement[] trace = origin.getStackTrace();
        if (!voxy$isCausedByVoxy(trace)) {
            logger.info(format, message);
            return;
        }
        if (voxy$isCapabilityShaderCompileTest(trace)) {
            return;
        }
        logger.info(format + '\n' + voxy$stackTrace(origin), origin);
    }

    @Unique
    private static String voxy$stackTrace(Throwable throwable) {
        StringWriter output = new StringWriter();
        throwable.printStackTrace(new PrintWriter(output));
        return output.toString();
    }

    @Unique
    private static boolean voxy$isCausedByVoxy(StackTraceElement[] trace) {
        for (StackTraceElement element : trace) {
            if (element.getClassName().startsWith("me.cortex.voxy")) {
                return true;
            }
        }
        return false;
    }

    @Unique
    private static boolean voxy$isCapabilityShaderCompileTest(StackTraceElement[] trace) {
        for (StackTraceElement element : trace) {
            String className = element.getClassName();
            String methodName = element.getMethodName();
            if ((className.equals("me.cortex.voxy.client.core.gl.Capabilities")
                    && methodName.equals("testShaderCompilesOk"))
                    || (className.equals("me.cortex.voxy.forge.Capabilities")
                    && methodName.equals("testShaderCompiles"))) {
                return true;
            }
        }
        return false;
    }
}
