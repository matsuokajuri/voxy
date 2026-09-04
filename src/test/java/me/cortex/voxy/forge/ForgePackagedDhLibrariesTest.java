package me.cortex.voxy.forge;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@Tag("packaged-artifact")
class ForgePackagedDhLibrariesTest {
    @Test
    void releaseJarLeavesSqliteExternalAndPreservesOtherPackagedLibraries() throws Exception {
        Path releaseJar = Path.of(System.getProperty("voxyPackagedJar"));
        try (ZipFile archive = new ZipFile(releaseJar.toFile())) {
            var metadataEntry = archive.getEntry("META-INF/jarjar/metadata.json");
            assertNotNull(metadataEntry, "Release JAR must contain JarJar metadata");
            try (var reader = new InputStreamReader(archive.getInputStream(metadataEntry), StandardCharsets.UTF_8)) {
                for (var entry : JsonParser.parseReader(reader).getAsJsonObject().getAsJsonArray("jars")) {
                    JsonObject dependency = entry.getAsJsonObject();
                    JsonObject id = dependency.getAsJsonObject("identifier");
                    assertFalse("org.xerial".equals(id.get("group").getAsString())
                                    && "sqlite-jdbc".equals(id.get("artifact").getAsString()),
                            "SQLite is an optional external DH-import dependency, not a bundled library");
                    assertNotNull(archive.getEntry(dependency.get("path").getAsString()),
                            "Every declared JarJar dependency must retain its payload");
                }
            }
            assertFalse(archive.stream().anyMatch(entry -> entry.getName().startsWith("org/sqlite/")
                            || entry.getName().contains("sqlite-jdbc")),
                    "Release JAR must not embed SQLite classes, native libraries, or a driver JAR");
            assertNotNull(archive.getEntry("me/cortex/voxy/dependency/xz/XZInputStream.class"));
        }
    }
}
