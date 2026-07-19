package me.cortex.voxy.common.world.other;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import me.cortex.voxy.common.config.storage.inmemory.MemoryStorageBackend;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MappingStorageVersioningTest {
    private static final int STONE_KEY = (1 << 30) | 1;

    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void legacyMappingsAreBackedUpAndVersionedWithoutChangingIds() {
        MemoryStorageBackend backend = new MemoryStorageBackend();
        byte[] stone = new Mapper.StateEntry(1, Blocks.STONE.defaultBlockState()).serialize();
        put(backend, STONE_KEY, stone);
        try {
            Mapper mapper = new Mapper(backend);

            assertEquals(2, mapper.getBlockStateCount());
            Int2ObjectOpenHashMap<byte[]> stored = backend.getIdMappingsData();
            MappingStorageMetadata.Manifest manifest = MappingStorageMetadata.decodeManifest(
                    stored.get(MappingStorageMetadata.MANIFEST_KEY));
            MappingStorageMetadata.Backup backup = MappingStorageMetadata.decodeBackup(
                    stored.get(MappingStorageMetadata.BACKUP_KEY));
            assertEquals(MappingStorageMetadata.State.COMMITTED, manifest.state());
            assertEquals(MappingStorageMetadata.CURRENT_SCHEMA_VERSION, manifest.schemaVersion());
            assertEquals(currentDataVersion(), manifest.dataVersion());
            assertArrayEquals(stone, stored.get(STONE_KEY));
            assertArrayEquals(stone, backup.mappings().get(STONE_KEY));

            Mapper reopened = new Mapper(backend);
            assertEquals(2, reopened.getBlockStateCount());
        } finally {
            backend.close();
        }
    }

    @Test
    void newerDataVersionIsRefusedWithoutMutatingStorage() {
        MemoryStorageBackend backend = new MemoryStorageBackend();
        put(backend, MappingStorageMetadata.MANIFEST_KEY, MappingStorageMetadata.encodeManifest(
                new MappingStorageMetadata.Manifest(
                        MappingStorageMetadata.CURRENT_SCHEMA_VERSION,
                        currentDataVersion() + 1,
                        MappingStorageMetadata.State.COMMITTED,
                        MappingStorageMetadata.CURRENT_SCHEMA_VERSION,
                        currentDataVersion() + 1,
                        "future")));
        Int2ObjectOpenHashMap<byte[]> before = backend.getIdMappingsData();
        try {
            IllegalStateException failure = assertThrows(IllegalStateException.class, () -> new Mapper(backend));
            assertTrue(failure.getMessage().contains("newer than the running version"));
            assertSnapshotsEqual(before, backend.getIdMappingsData());
        } finally {
            backend.close();
        }
    }

    @Test
    void futureInterruptedUpgradeBackupIsRefusedWithoutMutatingStorage() {
        MemoryStorageBackend backend = new MemoryStorageBackend();
        byte[] damagedStone = new byte[] {1};
        byte[] stone = new Mapper.StateEntry(1, Blocks.STONE.defaultBlockState()).serialize();
        Int2ObjectOpenHashMap<byte[]> backupMappings = new Int2ObjectOpenHashMap<>();
        backupMappings.put(STONE_KEY, stone);
        put(backend, STONE_KEY, damagedStone);
        put(backend, MappingStorageMetadata.BACKUP_KEY, MappingStorageMetadata.encodeBackup(
                new MappingStorageMetadata.Backup(0, currentDataVersion() + 1, backupMappings)));
        put(backend, MappingStorageMetadata.MANIFEST_KEY, MappingStorageMetadata.encodeManifest(
                new MappingStorageMetadata.Manifest(
                        0,
                        0,
                        MappingStorageMetadata.State.UPGRADING,
                        MappingStorageMetadata.CURRENT_SCHEMA_VERSION,
                        currentDataVersion(),
                        "future backup")));
        Int2ObjectOpenHashMap<byte[]> before = backend.getIdMappingsData();
        try {
            IllegalStateException failure = assertThrows(IllegalStateException.class, () -> new Mapper(backend));
            assertTrue(failure.getMessage().contains("newer than the running version"));
            assertSnapshotsEqual(before, backend.getIdMappingsData());
        } finally {
            backend.close();
        }
    }

    @Test
    void negativeMetadataVersionsAreRejectedAtTheEnvelopeBoundary() {
        Int2ObjectOpenHashMap<byte[]> mappings = new Int2ObjectOpenHashMap<>();
        assertThrows(
                IllegalStateException.class,
                () -> new MappingStorageMetadata.Manifest(
                        -1,
                        0,
                        MappingStorageMetadata.State.COMMITTED,
                        MappingStorageMetadata.CURRENT_SCHEMA_VERSION,
                        currentDataVersion(),
                        "negative"));
        assertThrows(
                IllegalStateException.class,
                () -> new MappingStorageMetadata.Backup(0, -1, mappings));
    }

    @Test
    void interruptedUpgradeRestoresVerifiedBackupBeforeRetry() {
        MemoryStorageBackend backend = new MemoryStorageBackend();
        byte[] stone = new Mapper.StateEntry(1, Blocks.STONE.defaultBlockState()).serialize();
        Int2ObjectOpenHashMap<byte[]> backupMappings = new Int2ObjectOpenHashMap<>();
        backupMappings.put(STONE_KEY, stone);
        put(backend, STONE_KEY, new byte[] {1});
        put(backend, MappingStorageMetadata.BACKUP_KEY, MappingStorageMetadata.encodeBackup(
                new MappingStorageMetadata.Backup(0, 0, backupMappings)));
        put(backend, MappingStorageMetadata.MANIFEST_KEY, MappingStorageMetadata.encodeManifest(
                new MappingStorageMetadata.Manifest(
                        0,
                        0,
                        MappingStorageMetadata.State.UPGRADING,
                        MappingStorageMetadata.CURRENT_SCHEMA_VERSION,
                        currentDataVersion(),
                        "simulated crash")));
        try {
            Mapper mapper = new Mapper(backend);

            assertEquals(Blocks.STONE.defaultBlockState(), mapper.getBlockStateFromBlockId(1));
            Int2ObjectOpenHashMap<byte[]> stored = backend.getIdMappingsData();
            assertArrayEquals(stone, stored.get(STONE_KEY));
            assertEquals(
                    MappingStorageMetadata.State.COMMITTED,
                    MappingStorageMetadata.decodeManifest(
                            stored.get(MappingStorageMetadata.MANIFEST_KEY)).state());
        } finally {
            backend.close();
        }
    }

    @Test
    void failedUpgradeRestoresOriginalBytesAndLeavesAuditState() {
        FailCommittedManifestStorage backend = new FailCommittedManifestStorage();
        byte[] stone = new Mapper.StateEntry(1, Blocks.STONE.defaultBlockState()).serialize();
        put(backend, STONE_KEY, stone);
        try {
            assertThrows(IllegalStateException.class, () -> new Mapper(backend));

            Int2ObjectOpenHashMap<byte[]> stored = backend.getIdMappingsData();
            assertArrayEquals(stone, stored.get(STONE_KEY));
            MappingStorageMetadata.Manifest manifest = MappingStorageMetadata.decodeManifest(
                    stored.get(MappingStorageMetadata.MANIFEST_KEY));
            assertEquals(MappingStorageMetadata.State.FAILED, manifest.state());
            assertTrue(manifest.detail().contains("simulated committed-manifest failure"));
            assertArrayEquals(
                    stone,
                    MappingStorageMetadata.decodeBackup(
                            stored.get(MappingStorageMetadata.BACKUP_KEY)).mappings().get(STONE_KEY));
        } finally {
            backend.close();
        }
    }

    private static int currentDataVersion() {
        return SharedConstants.getCurrentVersion().getDataVersion().getVersion();
    }

    private static void put(MemoryStorageBackend backend, int key, byte[] value) {
        MappingStorageMetadata.putBytes(backend, key, value);
    }

    private static void assertSnapshotsEqual(
            Int2ObjectOpenHashMap<byte[]> expected,
            Int2ObjectOpenHashMap<byte[]> actual) {
        assertEquals(expected.size(), actual.size());
        for (var entry : expected.int2ObjectEntrySet()) {
            assertTrue(Arrays.equals(entry.getValue(), actual.get(entry.getIntKey())));
        }
    }

    private static final class FailCommittedManifestStorage extends MemoryStorageBackend {
        private boolean failCommittedManifest = true;

        @Override
        public void putIdMapping(int id, ByteBuffer data) {
            if (id == MappingStorageMetadata.MANIFEST_KEY && this.failCommittedManifest) {
                byte[] encoded = new byte[data.remaining()];
                data.duplicate().get(encoded);
                if (MappingStorageMetadata.decodeManifest(encoded).state()
                        == MappingStorageMetadata.State.COMMITTED) {
                    this.failCommittedManifest = false;
                    throw new IllegalStateException("simulated committed-manifest failure");
                }
            }
            super.putIdMapping(id, data);
        }
    }
}
