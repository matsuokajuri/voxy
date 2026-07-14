package me.cortex.voxy.forge;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShaderLoaderParityTest {
    @Test
    void packagedShaderAndImportsLoadFromClasspath() {
        String source = ShaderLoader.parse("voxy:lod/gl46/quads.frag");

        assertTrue(source.startsWith("#version 460 core\n"));
        assertTrue(source.length() > 100);
        assertFalse(source.contains("#import"));
    }

    @Test
    void malformedRootIdsUseMinecraftValidation() {
        assertThrows(RuntimeException.class, () -> ShaderLoader.parse("Voxy:lod/gl46/quads.frag"));
        assertThrows(RuntimeException.class, () -> ShaderLoader.parse("voxy:LOD/gl46/quads.frag"));
    }
}
