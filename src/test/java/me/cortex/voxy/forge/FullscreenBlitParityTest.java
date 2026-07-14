package me.cortex.voxy.forge;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FullscreenBlitParityTest {
    @Test
    void builderDefinesAreAppliedToBothShaderStages() {
        FullscreenBlitShaderSources sources = FullscreenBlitShaderSources.prepare(
                new RenderProperties(true, true, false),
                "#version 460 core\nVERTEX_BODY",
                "#version 460 core\nFRAGMENT_BODY",
                "USE_ENV_FOG",
                "EMIT_COLOUR");

        for (String source : new String[]{sources.vertex(), sources.fragment()}) {
            assertTrue(source.startsWith("#version 460 core\n"));
            assertEquals(1, occurrences(source, "#define USE_ZERO_ONE_DEPTH\n"));
            assertEquals(1, occurrences(source, "#define USE_REVERSE_Z\n"));
            assertEquals(1, occurrences(source, "#define USE_ENV_FOG\n"));
            assertEquals(1, occurrences(source, "#define EMIT_COLOUR\n"));
        }
    }

    @Test
    void instancesOwnOnlyTheirShaderProgram() throws ClassNotFoundException {
        Class<?> blitClass = Class.forName(
                "me.cortex.voxy.forge.FullscreenBlit",
                false,
                FullscreenBlitParityTest.class.getClassLoader());
        Set<String> instanceFields = Arrays.stream(blitClass.getDeclaredFields())
                .filter(field -> !Modifier.isStatic(field.getModifiers()))
                .map(field -> field.getName())
                .collect(Collectors.toSet());

        assertEquals(Set.of("programId"), instanceFields);
        assertFalse(Arrays.stream(blitClass.getDeclaredFields())
                .anyMatch(field -> field.getName().equals("EMPTY_VAO")));
        assertFalse(Arrays.stream(blitClass.getDeclaredFields())
                .anyMatch(field -> field.getType() == GlBuffer.class));
    }

    private static int occurrences(String value, String target) {
        return (value.length() - value.replace(target, "").length()) / target.length();
    }
}
