package me.cortex.voxy.forge;

import me.cortex.voxy.common.config.storage.inmemory.MemoryStorageBackend;
import me.cortex.voxy.common.world.other.MappingStorageMetadata;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FragmentedMappingRecoveryTest {
    private static final int BLOCK_1 = (1 << 30) | 1;
    private static final int BLOCK_2 = (1 << 30) | 2;

    @Test
    void repairsMissingAndPartialReplicasFromVerifiedSuperset() {
        MemoryStorageBackend complete = new MemoryStorageBackend();
        MemoryStorageBackend partial = new MemoryStorageBackend();
        put(complete, BLOCK_1, 11);
        put(complete, BLOCK_2, 22);
        put(partial, BLOCK_1, 11);
        FragmentedStorageBackendAdaptor fragmented =
                new FragmentedStorageBackendAdaptor(complete, partial);
        try {
            assertEquals(2, fragmented.getIdMappingsData().size());
            assertArrayEquals(new byte[] {11}, partial.getIdMappingsData().get(BLOCK_1));
            assertArrayEquals(new byte[] {22}, partial.getIdMappingsData().get(BLOCK_2));
        } finally {
            fragmented.close();
        }
    }

    @Test
    void repairsDivergentReplicaOnlyWithStrictMajority() {
        MemoryStorageBackend first = new MemoryStorageBackend();
        MemoryStorageBackend second = new MemoryStorageBackend();
        MemoryStorageBackend divergent = new MemoryStorageBackend();
        put(first, BLOCK_1, 31);
        put(second, BLOCK_1, 31);
        put(divergent, BLOCK_1, 99);
        FragmentedStorageBackendAdaptor fragmented =
                new FragmentedStorageBackendAdaptor(first, second, divergent, new MemoryStorageBackend());
        try {
            assertArrayEquals(new byte[] {31}, fragmented.getIdMappingsData().get(BLOCK_1));
            assertArrayEquals(new byte[] {31}, divergent.getIdMappingsData().get(BLOCK_1));
        } finally {
            fragmented.close();
        }
    }

    @Test
    void refusesTiedConflictsWithoutDestroyingEitherCopy() {
        MemoryStorageBackend first = new MemoryStorageBackend();
        MemoryStorageBackend second = new MemoryStorageBackend();
        put(first, BLOCK_1, 41);
        put(second, BLOCK_1, 42);
        FragmentedStorageBackendAdaptor fragmented =
                new FragmentedStorageBackendAdaptor(first, second);
        try {
            assertThrows(IllegalStateException.class, fragmented::getIdMappingsData);
            assertArrayEquals(new byte[] {41}, first.getIdMappingsData().get(BLOCK_1));
            assertArrayEquals(new byte[] {42}, second.getIdMappingsData().get(BLOCK_1));
        } finally {
            fragmented.close();
        }
    }

    private static void put(MemoryStorageBackend backend, int key, int value) {
        MappingStorageMetadata.putBytes(backend, key, new byte[] {(byte) value});
    }
}
