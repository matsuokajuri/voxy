package me.cortex.voxy.forge;

import me.cortex.voxy.common.util.MemoryBuffer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class Round11GeometryCacheOwnershipTest {
    @Test
    void duplicateTransferIsRejectedWithoutFreeingTheOwnedEntry() {
        GeometryCache cache = new GeometryCache(4096, 4);
        BuiltSection section = section(1, 64, 32);
        long epoch = cache.snapshotEpoch(1);
        cache.put(section, epoch);
        try {
            assertThrows(IllegalStateException.class, () -> cache.put(section, epoch));
            assertFalse(section.geometryBuffer.isFreed());
            assertFalse(section.occupancy.isFreed());
            assertEquals(1, cache.entryCount());
            assertEquals(96, cache.currentSize());
            assertSame(section, cache.take(1));
        } finally {
            if (!section.geometryBuffer.isFreed()) section.free();
            // The baseline may leave a self-freed object in the cache; detach it before cleaning up.
            cache.take(1);
            cache.free();
        }
    }

    @Test
    void replacedEvictedAndStaleSectionsReleaseBothBuffersExactlyOnce() {
        GeometryCache cache = new GeometryCache(96, 1);
        BuiltSection first = section(1, 64, 32);
        BuiltSection replacement = section(1, 64, 32);
        BuiltSection second = section(2, 64, 32);
        cache.put(first, cache.snapshotEpoch(1));
        cache.put(replacement, cache.snapshotEpoch(1));
        assertFreed(first);
        cache.put(second, cache.snapshotEpoch(2));
        assertFreed(replacement);
        long staleEpoch = cache.snapshotEpoch(2);
        cache.invalidate(2);
        assertFreed(second);
        BuiltSection stale = section(2, 64, 32);
        cache.put(stale, staleEpoch);
        assertFreed(stale);
        cache.free();
        cache.free();
        assertEquals(0, cache.currentSize());
        assertEquals(0, cache.entryCount());
    }

    @Test
    void shutdownRejectsEvenFreshEpochLateResults() {
        GeometryCache cache = new GeometryCache(4096, 4);
        cache.free();
        BuiltSection late = section(1, 64, 32);
        try {
            cache.put(late, cache.snapshotEpoch(1));
            assertFreed(late);
            assertEquals(0, cache.currentSize());
            assertEquals(0, cache.entryCount());
            assertNull(cache.take(1));
        } finally {
            cache.free();
        }
    }

    @Test
    void zeroByteBudgetImmediatelyReleasesOversizedSection() {
        GeometryCache cache = new GeometryCache(0, 1);
        BuiltSection large = section(1, 64, 32);
        cache.put(large, cache.snapshotEpoch(1));
        assertFreed(large);
        assertEquals(0, cache.currentSize());
        assertEquals(0, cache.entryCount());
        cache.free();
    }

    private static BuiltSection section(long position, long bytes, long occupancyBytes) {
        return new BuiltSection(position, (byte) 0, 0, new MemoryBuffer(bytes), new int[8],
                new MemoryBuffer(occupancyBytes));
    }

    private static void assertFreed(BuiltSection section) {
        assertTrue(section.geometryBuffer.isFreed());
        assertTrue(section.occupancy.isFreed());
    }
}
