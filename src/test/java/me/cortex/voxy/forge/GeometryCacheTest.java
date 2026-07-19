package me.cortex.voxy.forge;

import me.cortex.voxy.common.util.MemoryBuffer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class GeometryCacheTest {
    @Test
    void takesOwnedEntryAndTracksHitMissWithoutCloningAgain() {
        GeometryCache cache = new GeometryCache(1024L, 4);
        BuiltSection section = section(11L, 64L);
        cache.put(section, cache.snapshotEpoch(section.position));
        assertEquals(64L, cache.currentSize());
        assertSame(section, cache.take(11L));
        assertEquals(0L, cache.currentSize());
        assertEquals(1L, cache.hits());
        assertNull(cache.take(11L));
        assertEquals(1L, cache.misses());
        section.free();
        cache.free();
    }

    @Test
    void boundsBytesAndEntriesAndRejectsPreInvalidationResult() {
        GeometryCache cache = new GeometryCache(128L, 2);
        long epoch = cache.snapshotEpoch(1L);
        cache.put(section(1L, 64L), epoch);
        cache.put(section(2L, 64L), cache.snapshotEpoch(2L));
        cache.put(section(3L, 64L), cache.snapshotEpoch(3L));
        assertEquals(2, cache.entryCount());
        assertEquals(128L, cache.currentSize());
        assertEquals(1L, cache.evictions());

        long staleEpoch = cache.snapshotEpoch(4L);
        cache.invalidate(4L);
        cache.put(section(4L, 64L), staleEpoch);
        assertEquals(1L, cache.staleRejects());
        assertNull(cache.take(4L));
        cache.free();
        assertEquals(0, cache.entryCount());
        assertEquals(0L, cache.currentSize());
    }

    private static BuiltSection section(long position, long bytes) {
        return new BuiltSection(
                position,
                (byte) 0xFF,
                0,
                new MemoryBuffer(bytes),
                new int[8],
                null);
    }
}
