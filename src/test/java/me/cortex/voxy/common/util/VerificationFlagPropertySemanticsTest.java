package me.cortex.voxy.common.util;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class VerificationFlagPropertySemanticsTest {
    private static final String RESULT_PREFIX = "VOXY_PROPERTY_RESULT=";

    @Test
    void memoryBufferTrackingRequiresExactLowercaseTrue() throws Exception {
        assertEquals("false", probe(
                "voxy.trackBuffers",
                "TRUE",
                "me.cortex.voxy.common.util.MemoryBuffer",
                "TRACK_MEMORY_BUFFERS"));
        assertEquals("true", probe(
                "voxy.trackBuffers",
                "true",
                "me.cortex.voxy.common.util.MemoryBuffer",
                "TRACK_MEMORY_BUFFERS"));
    }

    @Test
    void worldSectionVerificationRequiresExactLowercaseTrue() throws Exception {
        assertEquals("false", probe(
                "voxy.verifyWorldSectionExecution",
                "TRUE",
                "me.cortex.voxy.common.world.WorldSection",
                "VERIFY_WORLD_SECTION_EXECUTION"));
        assertEquals("true", probe(
                "voxy.verifyWorldSectionExecution",
                "true",
                "me.cortex.voxy.common.world.WorldSection",
                "VERIFY_WORLD_SECTION_EXECUTION"));
    }

    @Test
    void builtSectionVerificationRequiresExactLowercaseTrue() throws Exception {
        assertEquals("false", probe(
                "voxy.verifyBuiltSectionOffsets",
                "TRUE",
                "me.cortex.voxy.forge.BuiltSection",
                "VERIFY_BUILT_SECTION_OFFSETS"));
        assertEquals("true", probe(
                "voxy.verifyBuiltSectionOffsets",
                "true",
                "me.cortex.voxy.forge.BuiltSection",
                "VERIFY_BUILT_SECTION_OFFSETS"));
    }

    @Test
    void glDebugRequiresExactLowercaseTrue() throws Exception {
        assertEquals("false", probe(
                "voxy.glDebug",
                "TRUE",
                "me.cortex.voxy.forge.GlDebug",
                "ENABLED"));
        assertEquals("true", probe(
                "voxy.glDebug",
                "true",
                "me.cortex.voxy.forge.GlDebug",
                "ENABLED"));
    }

    private static String probe(String property, String value, String className, String fieldName)
            throws Exception {
        Path javaExecutable = Path.of(
                System.getProperty("java.home"),
                "bin",
                System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java");
        Process process = new ProcessBuilder(
                javaExecutable.toString(),
                "-D" + property + "=" + value,
                "-cp",
                System.getProperty("java.class.path"),
                VerificationFlagPropertySemanticsTest.class.getName(),
                className,
                fieldName)
                .redirectErrorStream(true)
                .start();

        assertTrue(process.waitFor(30, TimeUnit.SECONDS), "Property probe timed out");
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertEquals(0, process.exitValue(), output);
        int resultIndex = output.lastIndexOf(RESULT_PREFIX);
        assertTrue(resultIndex >= 0, output);
        return output.substring(resultIndex + RESULT_PREFIX.length()).trim();
    }

    public static void main(String[] args) throws Exception {
        Field field = Class.forName(args[0]).getDeclaredField(args[1]);
        field.setAccessible(true);
        System.out.println(RESULT_PREFIX + field.getBoolean(null));
    }
}
