package me.cortex.voxy.forge.mixin;

import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ForgeOriginalVoxyDiagnosticMixinTest {
    @Test
    void glDebugClassificationMatchesOriginalVoxyRules() {
        StackTraceElement voxyFrame = new StackTraceElement(
                "me.cortex.voxy.forge.MDICSectionRenderer",
                "render",
                "MDICSectionRenderer.java",
                1);
        StackTraceElement foreignFrame = new StackTraceElement(
                "net.minecraft.client.Minecraft",
                "run",
                "Minecraft.java",
                1);
        StackTraceElement capabilityProbe = new StackTraceElement(
                "me.cortex.voxy.client.core.gl.Capabilities",
                "testShaderCompilesOk",
                "Capabilities.java",
                1);
        StackTraceElement forgeCapabilityProbe = new StackTraceElement(
                "me.cortex.voxy.forge.Capabilities",
                "testShaderCompiles",
                "Capabilities.java",
                1);

        assertTrue(invokeStaticBoolean(
                ForgeOriginalVoxyGlDebugMixin.class,
                "voxy$isCausedByVoxy",
                StackTraceElement[].class,
                new StackTraceElement[]{foreignFrame, voxyFrame}));
        assertFalse(invokeStaticBoolean(
                ForgeOriginalVoxyGlDebugMixin.class,
                "voxy$isCausedByVoxy",
                StackTraceElement[].class,
                new StackTraceElement[]{foreignFrame}));
        assertTrue(invokeStaticBoolean(
                ForgeOriginalVoxyGlDebugMixin.class,
                "voxy$isCapabilityShaderCompileTest",
                StackTraceElement[].class,
                new StackTraceElement[]{voxyFrame, capabilityProbe}));
        assertTrue(invokeStaticBoolean(
                ForgeOriginalVoxyGlDebugMixin.class,
                "voxy$isCapabilityShaderCompileTest",
                StackTraceElement[].class,
                new StackTraceElement[]{voxyFrame, forgeCapabilityProbe}));
    }

    @Test
    void onlyClientLoginHandlerFailuresBecomeFatal() {
        RuntimeException loginFailure = failureWithFrame(
                "net.minecraft.client.multiplayer.ClientPacketListener",
                "handleLogin");
        RuntimeException ordinaryFailure = failureWithFrame(
                "net.minecraft.client.multiplayer.ClientPacketListener",
                "handleChunkBlocksUpdate");

        assertTrue(isLoginHandlingFailure(loginFailure));
        assertFalse(isLoginHandlingFailure(ordinaryFailure));
    }

    private static boolean isLoginHandlingFailure(Throwable failure) {
        return invokeStaticBoolean(
                ForgeOriginalVoxyBlockableEventLoopMixin.class,
                "voxy$isLoginHandlingFailure",
                Throwable.class,
                failure);
    }

    private static boolean invokeStaticBoolean(
            Class<?> owner,
            String methodName,
            Class<?> argumentType,
            Object argument) {
        try {
            var method = owner.getDeclaredMethod(methodName, argumentType);
            method.setAccessible(true);
            return (boolean) method.invoke(null, argument);
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException exception) {
            throw new AssertionError("Could not invoke the private mixin classification helper", exception);
        }
    }

    private static RuntimeException failureWithFrame(String className, String methodName) {
        RuntimeException failure = new RuntimeException("test");
        failure.setStackTrace(new StackTraceElement[]{
                new StackTraceElement(className, methodName, "ClientPacketListener.java", 1)
        });
        return failure;
    }
}
