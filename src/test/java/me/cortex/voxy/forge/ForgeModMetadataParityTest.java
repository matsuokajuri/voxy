package me.cortex.voxy.forge;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
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

    private static byte[] resource(String path) throws IOException {
        try (InputStream input = ForgeModMetadataParityTest.class.getResourceAsStream(path)) {
            assertNotNull(input, "missing processed test resource: " + path);
            return input.readAllBytes();
        }
    }
}
