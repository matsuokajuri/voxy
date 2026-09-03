package me.cortex.voxy.forge.mixin;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ForgeOriginalVoxyGlDebugMixinCompatibilityTest {
    @Test
    void formatsTheSameMinecraft1201LogEntryPayload() throws ReflectiveOperationException {
        assertEquals(
                "id=7, source=SHADER COMPILER, type=ERROR, severity=HIGH, message='boom'",
                invokeStatic(
                        "voxy$formatDebugMessage",
                        new Class<?>[] {int.class, int.class, int.class, int.class, String.class},
                        33352,
                        33356,
                        7,
                        37190,
                        "boom"));
    }

    @Test
    void preservesOriginalVoxyTraceClassification() throws ReflectiveOperationException {
        StackTraceElement voxyFrame = new StackTraceElement(
                "me.cortex.voxy.forge.ModelFactory",
                "processModelResult",
                "ModelFactory.java",
                1);
        StackTraceElement capabilityFrame = new StackTraceElement(
                "me.cortex.voxy.forge.Capabilities",
                "testShaderCompiles",
                "Capabilities.java",
                1);
        StackTraceElement foreignFrame = new StackTraceElement(
                "example.ForeignRenderer",
                "draw",
                "ForeignRenderer.java",
                1);

        assertTrue((boolean) invokeStatic(
                "voxy$isCausedByVoxy",
                new Class<?>[] {StackTraceElement[].class},
                (Object) new StackTraceElement[] {voxyFrame}));
        assertFalse((boolean) invokeStatic(
                "voxy$isCausedByVoxy",
                new Class<?>[] {StackTraceElement[].class},
                (Object) new StackTraceElement[] {foreignFrame}));
        assertTrue((boolean) invokeStatic(
                "voxy$isCapabilityShaderCompileTest",
                new Class<?>[] {StackTraceElement[].class},
                (Object) new StackTraceElement[] {capabilityFrame}));
        assertFalse((boolean) invokeStatic(
                "voxy$isCapabilityShaderCompileTest",
                new Class<?>[] {StackTraceElement[].class},
                (Object) new StackTraceElement[] {voxyFrame}));
    }

    @Test
    void injectsBeforeRedirectBasedGlDebugModsWithoutOwningTheirCall() throws IOException {
        String source = Files.readString(Path.of(
                "src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyGlDebugMixin.java"));

        assertTrue(source.contains("@Mixin(value = GlDebug.class, priority = 1100)"));
        assertTrue(source.contains("@Inject("));
        assertTrue(source.contains("cancellable = true"));
        assertTrue(source.contains("require = 0"));
        assertFalse(source.contains("@Redirect("));
        assertFalse(source.contains("mixinextras"));
        assertTrue(source.contains("private static String voxy$formatDebugMessage("));
        assertTrue(source.contains("private static boolean voxy$isCausedByVoxy("));
        assertTrue(source.contains("private static boolean voxy$isCapabilityShaderCompileTest("));
    }

    private static Object invokeStatic(String name, Class<?>[] parameterTypes, Object... arguments)
            throws ReflectiveOperationException {
        Method method = ForgeOriginalVoxyGlDebugMixin.class.getDeclaredMethod(name, parameterTypes);
        method.setAccessible(true);
        try {
            return method.invoke(null, arguments);
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw exception;
        }
    }
}
