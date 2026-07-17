package me.cortex.voxy.forge;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("bobby-exact-artifact")
class ForgeBobbyReforgedExactArtifactTest {
    private static final String EXACT_FILENAME = "bobby-1.20.1_v5.0.1.jar";
    private static final String EXACT_SHA256 = "4eb8296c24fa88cfc27145dc006b8acdea75d5f5175dd8b49dc5ef9709e66ee4";

    @Test
    void exactArtifactMatchesTheActiveForgeUnloadSplit() throws Exception {
        String configuredPath = System.getProperty("voxyBobbyDevJar", "");
        assertFalse(configuredPath.isBlank(), "The exact-artifact task must supply voxyBobbyDevJar");
        Path artifact = Path.of(configuredPath).toRealPath();

        assertEquals(EXACT_FILENAME, artifact.getFileName().toString());
        assertEquals(EXACT_SHA256, sha256(artifact));

        try (JarFile jar = new JarFile(artifact.toFile())) {
            String metadata = entryText(jar, "META-INF/mods.toml");
            String mixins = entryText(jar, "bobby.mixins.json");
            String refmap = entryText(jar, "bobby-refmap.json");

            assertTrue(metadata.contains("modId = \"bobby\""));
            // CurseForge calls this file 5.0.1, while the published JAR retains 5.0.0 metadata.
            assertTrue(metadata.contains("version = \"5.0.0\""));
            assertTrue(metadata.contains("side = \"CLIENT\""));
            assertTrue(mixins.contains("\"required\": true"));
            assertTrue(mixins.contains("\"defaultRequire\": 1"));
            assertTrue(mixins.contains("\"ClientChunkManagerMixin\""));
            assertTrue(mixins.contains("\"sodium.SodiumChunkManagerMixin\""));
            assertTrue(refmap.contains("\"unload\": \"Lnet/minecraft/client/multiplayer/ClientChunkCache;m_104455_(II)V\""));

            assertEntry(jar, "de/johni0702/minecraft/bobby/mixin/ClientChunkManagerMixin.class");
            assertEntry(jar, "de/johni0702/minecraft/bobby/mixin/sodium/SodiumChunkManagerMixin.class");
        }

        String clientCache = Files.readString(Path.of(
                "src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyClientChunkCacheMixin.java"));
        String renderManager = Files.readString(Path.of(
                "src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyEmbeddiumRenderSectionManagerMixin.java"));

        assertTrue(clientCache.contains("ModList.get().isLoaded(\"bobby\")"));
        assertTrue(clientCache.contains("@Inject(method = \"drop\", at = @At(\"HEAD\"))"));
        assertTrue(clientCache.contains("VoxelIngestService.tryAutoIngestChunk(chunk)"));
        assertTrue(renderManager.contains("if (VOXY_BOBBY_REFORGED_INSTALLED"));
        assertTrue(renderManager.contains("private void voxy$ingestOnChunkRemove"));
    }

    private static void assertEntry(JarFile jar, String path) {
        assertNotNull(jar.getJarEntry(path), path);
    }

    private static String entryText(JarFile jar, String path) throws IOException {
        JarEntry entry = jar.getJarEntry(path);
        assertNotNull(entry, path);
        try (InputStream input = jar.getInputStream(entry)) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static String sha256(Path path) throws IOException, NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream input = Files.newInputStream(path)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                digest.update(buffer, 0, read);
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }
}
