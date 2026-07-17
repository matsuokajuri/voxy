package me.cortex.voxy.forge;

import me.cortex.voxy.common.config.ConfigBuildCtx;
import me.cortex.voxy.common.config.section.SectionStorage;
import me.cortex.voxy.common.world.WorldSection;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ForgeOriginalVoxyZstdPersistenceTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void defaultRocksDbZstdSerializerChainSurvivesRestart() {
        Path basePath = this.temporaryDirectory.resolve("voxy");
        String worldIdentifier = "round-one-world";
        WorldSection source = WorldSection._createRawUntrackedUnsafeSection(1, 2, 3, 4);
        for (int index = 0; index < source._unsafeGetRawDataArray().length; index++) {
            source._unsafeGetRawDataArray()[index] = 0x1000_0000L + index % 257L;
        }
        long[] expected = Arrays.copyOf(
                source._unsafeGetRawDataArray(),
                source._unsafeGetRawDataArray().length);

        ForgeOriginalVoxyStorageConfig.Loaded firstConfiguration =
                ForgeOriginalVoxyStorageConfig.loadOrCreate(basePath);
        assertEquals("Serializer->ZSTD(level=1)->RocksDB", firstConfiguration.backendChain());
        SectionStorage firstStorage = open(firstConfiguration, basePath, worldIdentifier);
        try {
            firstStorage.saveSection(source);
            firstStorage.flush();
        } finally {
            firstStorage.close();
        }

        ForgeOriginalVoxyStorageConfig.Loaded restartedConfiguration =
                ForgeOriginalVoxyStorageConfig.loadOrCreate(basePath);
        assertEquals("loaded", restartedConfiguration.source());
        SectionStorage restartedStorage = open(restartedConfiguration, basePath, worldIdentifier);
        try {
            WorldSection restored = WorldSection._createRawUntrackedUnsafeSection(1, 2, 3, 4);
            assertEquals(0, restartedStorage.loadSection(restored));
            assertArrayEquals(expected, restored._unsafeGetRawDataArray());
        } finally {
            restartedStorage.close();
        }
    }

    private static SectionStorage open(
            ForgeOriginalVoxyStorageConfig.Loaded configuration,
            Path basePath,
            String worldIdentifier) {
        ConfigBuildCtx context = new ConfigBuildCtx();
        context.setProperty(ConfigBuildCtx.BASE_SAVE_PATH, basePath.toString());
        context.setProperty(ConfigBuildCtx.WORLD_IDENTIFIER, worldIdentifier);
        context.setProperty(ConfigBuildCtx.PLAYER_UUID, "round-one-player");
        context.pushPath(ConfigBuildCtx.DEFAULT_STORAGE_PATH);
        return configuration.config().sectionStorageConfig.build(context);
    }
}
