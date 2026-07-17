package me.cortex.voxy.forge;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ForgeModMetadataParityTest {
    @Test
    void processedForgeMetadataExposesThePackagedOriginalIcon() throws IOException {
        String metadata = new String(resource("/META-INF/mods.toml"), StandardCharsets.UTF_8);
        assertTrue(metadata.contains("logoFile=\"assets/voxy/icon.png\""));

        byte[] sourceIcon = Files.readAllBytes(Path.of(
                "src/main/resources/assets/voxy/icon.png"));
        assertArrayEquals(sourceIcon, resource("/assets/voxy/icon.png"));
    }

    @Test
    void frontendMetadataMatchesOriginalDependencyPolicy() throws IOException {
        String metadata = new String(resource("/META-INF/mods.toml"), StandardCharsets.UTF_8);
        String embeddium = dependencyBlock(metadata, "embeddium");
        String oculus = dependencyBlock(metadata, "oculus");

        assertTrue(embeddium.contains("mandatory=true"));
        assertTrue(oculus.contains("mandatory=false"));
        assertFalse(oculus.contains("mandatory=true"));
    }

    private static String dependencyBlock(String metadata, String modId) {
        int start = metadata.indexOf("modId=\"" + modId + "\"");
        assertTrue(start >= 0, "missing dependency block for " + modId);
        int end = metadata.indexOf("[[dependencies.", start);
        return end < 0 ? metadata.substring(start) : metadata.substring(start, end);
    }

    private static byte[] resource(String path) throws IOException {
        try (InputStream input = ForgeModMetadataParityTest.class.getResourceAsStream(path)) {
            assertNotNull(input, "missing processed test resource: " + path);
            return input.readAllBytes();
        }
    }
}
