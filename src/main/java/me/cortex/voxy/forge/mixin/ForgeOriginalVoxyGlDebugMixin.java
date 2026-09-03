package me.cortex.voxy.forge.mixin;

import com.mojang.blaze3d.platform.GlDebug;
import org.slf4j.Logger;
import org.lwjgl.opengl.GLDebugMessageCallback;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

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
@Mixin(value = GlDebug.class, priority = 1100)
public class ForgeOriginalVoxyGlDebugMixin {
    @Shadow
    @Final
    private static Logger LOGGER;

    @Inject(
            method = "printDebugLog",
            at = @At(
                    value = "INVOKE",
                    target = "Lorg/slf4j/Logger;info(Ljava/lang/String;Ljava/lang/Object;)V",
                    remap = false),
            cancellable = true,
            require = 0)
    private static void voxy$annotateVoxyDebugMessage(
            int source,
            int type,
            int id,
            int severity,
            int messageLength,
            long messagePointer,
            long userParam,
            CallbackInfo ci) {
        Throwable origin = new Throwable(voxy$formatDebugMessage(
                source,
                type,
                id,
                severity,
                GLDebugMessageCallback.getMessage(messageLength, messagePointer)));
        StackTraceElement[] trace = origin.getStackTrace();
        if (!voxy$isCausedByVoxy(trace)) {
            return;
        }
        if (voxy$isCapabilityShaderCompileTest(trace)) {
            ci.cancel();
            return;
        }
        LOGGER.info("OpenGL debug message: {}\n" + voxy$stackTrace(origin), origin);
        ci.cancel();
    }

    @Unique
    private static String voxy$formatDebugMessage(
            int source,
            int type,
            int id,
            int severity,
            String message) {
        return "id=" + id
                + ", source=" + GlDebug.sourceToString(source)
                + ", type=" + GlDebug.typeToString(type)
                + ", severity=" + GlDebug.severityToString(severity)
                + ", message='" + message + '\'';
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
