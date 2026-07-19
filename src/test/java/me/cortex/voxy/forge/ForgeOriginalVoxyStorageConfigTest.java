package me.cortex.voxy.forge;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ForgeOriginalVoxyStorageConfigTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void loadsOriginalMemoryStorageType() throws Exception {
        String json = """
                {
                  "version": 1,
                  "disabled": false,
                  "sectionStorageConfig": {
                    "TYPE": "Serializer",
                    "storage": {
                      "TYPE": "Memory"
                    }
                  }
                }
                """;
        Path configPath = this.temporaryDirectory.resolve("config.json");
        Files.writeString(configPath, json);

        ForgeOriginalVoxyStorageConfig.Loaded loaded =
                ForgeOriginalVoxyStorageConfig.loadOrCreate(this.temporaryDirectory);

        assertTrue(loaded.ready());
        assertEquals("loaded", loaded.source());
        assertEquals("Serializer->Memory", loaded.backendChain());
        assertTrue(Files.readString(configPath).contains("\"TYPE\": \"Memory\""));
    }

    @Test
    void resetsUnknownCustomStorageConfigLikeOriginalStorageConfigUtil() throws Exception {
        String json = """
                {
                  "version": 1,
                  "disabled": false,
                  "sectionStorageConfig": {
                    "TYPE": "Serializer",
                    "storage": {
                      "TYPE": "ThirdPartyBackend",
                      "importantCustomSetting": "keep-me"
                    }
                  }
                }
                """;
        Path configPath = this.temporaryDirectory.resolve("config.json");
        Files.writeString(configPath, json);

        ForgeOriginalVoxyStorageConfig.Loaded loaded =
                ForgeOriginalVoxyStorageConfig.loadOrCreate(this.temporaryDirectory);

        assertTrue(loaded.ready());
        assertEquals("load-failure-reset", loaded.source());
        assertEquals("Serializer->ZSTD(level=1)->RocksDB", loaded.backendChain());
        String rewritten = Files.readString(configPath);
        assertTrue(rewritten.contains("\"TYPE\": \"RocksDB\""));
        assertFalse(rewritten.contains("ThirdPartyBackend"));
    }

    @Test
    void rereadsConfigurationInsteadOfKeepingProcessGlobalCache() throws Exception {
        Path configPath = this.temporaryDirectory.resolve("config.json");
        Files.writeString(configPath, configWithBackend("Memory"));
        ForgeOriginalVoxyStorageConfig.Loaded first =
                ForgeOriginalVoxyStorageConfig.loadOrCreate(this.temporaryDirectory);

        Files.writeString(configPath, configWithBackend("RocksDB"));
        ForgeOriginalVoxyStorageConfig.Loaded second =
                ForgeOriginalVoxyStorageConfig.loadOrCreate(this.temporaryDirectory);

        assertEquals("Serializer->Memory", first.backendChain());
        assertEquals("Serializer->RocksDB", second.backendChain());
    }

    @Test
    void refusesUnimplementedConditionalConfigWithoutOverwritingIt() throws Exception {
        String json = configWithBackend("ConditionalConfig");
        Path configPath = this.temporaryDirectory.resolve("config.json");
        Files.writeString(configPath, json);

        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> ForgeOriginalVoxyStorageConfig.loadOrCreate(this.temporaryDirectory));

        assertTrue(failure.getMessage().contains("preserved unchanged"));
        assertEquals(json, Files.readString(configPath));
    }

    private static String configWithBackend(String type) {
        return """
                {
                  "version": 1,
                  "disabled": false,
                  "sectionStorageConfig": {
                    "TYPE": "Serializer",
                    "storage": {
                      "TYPE": "%s"
                    }
                  }
                }
                """.formatted(type);
    }
}
