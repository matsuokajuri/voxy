package me.cortex.voxy.forge;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ForgeOriginalVoxyOculusShaderPatchTest {
    private static final Path JSON_DUMP = Path.of("JSON_DUMP.txt");

    @Test
    @ResourceLock("JSON_DUMP.txt")
    void unknownBlendValueFailsInsideTheOriginalLoadBoundary() throws IOException {
        boolean dumpExisted = Files.exists(JSON_DUMP);
        byte[] previousDump = dumpExisted ? Files.readAllBytes(JSON_DUMP) : null;
        try {
            String patchJson = patchJson("{\"0\": {}}", false);
            ForgeOriginalVoxyShaderLoadError error = assertThrows(
                    ForgeOriginalVoxyShaderLoadError.class,
                    () -> makePatch(patchJson));
            assertInstanceOf(NullPointerException.class, error.getCause());
        } finally {
            if (dumpExisted) {
                Files.write(JSON_DUMP, previousDump);
            } else {
                Files.deleteIfExists(JSON_DUMP);
            }
        }
    }

    @Test
    void shortBlendArrayKeepsOnlyMappingsAccumulatedBeforeTheBadEntry()
            throws ReflectiveOperationException {
        String patchJson = patchJson("{\"0\": \"off\", \"1\": [], \"2\": \"off\"}", true);
        ForgeOriginalVoxyOculusShaderPatch patch = makePatch(patchJson);

        assertNotNull(patch);
        Int2ObjectMap<?> blending = blending(patch);
        assertEquals(1, blending.size());
        assertTrue(blending.containsKey(0));
        assertFalse(blending.containsKey(1));
        assertFalse(blending.containsKey(2));
    }

    private static String patchJson(String blending, boolean includeOpaquePatch) {
        String opaquePatch = includeOpaquePatch
                ? "\"opaquePatchData\": \"void main() {}\","
                : "";
        return """
                {
                  "version": 1,
                  "opaqueDrawBuffers": [0],
                  "translucentDrawBuffers": [0, 1, 2],
                  "uniforms": [],
                  %s
                  "blending": %s
                }
                """.formatted(opaquePatch, blending);
    }

    private static ForgeOriginalVoxyOculusShaderPatch makePatch(String patchJson)
            throws ReflectiveOperationException {
        Class<?> shaderPackClass = Class.forName("net.irisshaders.iris.shaderpack.ShaderPack");
        Class<?> absolutePathClass = Class.forName(
                "net.irisshaders.iris.shaderpack.include.AbsolutePackPath");
        Object directory = absolutePathClass
                .getMethod("fromAbsolutePath", String.class)
                .invoke(null, "/shaders");
        Method makePatch = ForgeOriginalVoxyOculusShaderPatch.class.getMethod(
                "makePatch",
                shaderPackClass,
                absolutePathClass,
                Function.class);
        try {
            return (ForgeOriginalVoxyOculusShaderPatch) makePatch.invoke(
                    null,
                    null,
                    directory,
                    sourceProvider(patchJson));
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            if (e.getCause() instanceof Error error) {
                throw error;
            }
            throw e;
        }
    }

    private static Function<Object, String> sourceProvider(String patchJson) {
        return path -> {
            try {
                String value = (String) path.getClass().getMethod("getPathString").invoke(path);
                return value.endsWith("voxy.json") ? patchJson : null;
            } catch (ReflectiveOperationException e) {
                throw new RuntimeException(e);
            }
        };
    }

    private static Int2ObjectMap<?> blending(ForgeOriginalVoxyOculusShaderPatch patch)
            throws ReflectiveOperationException {
        Field patchDataField = ForgeOriginalVoxyOculusShaderPatch.class.getDeclaredField("patchData");
        patchDataField.setAccessible(true);
        Object patchData = patchDataField.get(patch);
        Field blendingField = patchData.getClass().getDeclaredField("blending");
        blendingField.setAccessible(true);
        return (Int2ObjectMap<?>) blendingField.get(patchData);
    }
}
