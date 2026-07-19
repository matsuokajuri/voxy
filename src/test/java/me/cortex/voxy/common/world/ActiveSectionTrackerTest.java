package me.cortex.voxy.common.world;

import me.cortex.voxy.common.config.section.SectionStorage;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ActiveSectionTrackerTest {
    @Test
    void contendedAcquireLoadsOnceAndReservesEveryWaitingReference() throws Exception {
        int callers = 16;
        CountDownLatch loaderEntered = new CountDownLatch(1);
        CountDownLatch releaseLoader = new CountDownLatch(1);
        CountDownLatch acquired = new CountDownLatch(callers);
        AtomicInteger loadCalls = new AtomicInteger();
        ActiveSectionTracker tracker = new ActiveSectionTracker(4, section -> {
            loadCalls.incrementAndGet();
            loaderEntered.countDown();
            await(releaseLoader);
            return SectionStorage.LOAD_OK;
        }, 32);
        long key = WorldEngine.getWorldSectionId(0, 4, 5, 6);
        ExecutorService executor = Executors.newFixedThreadPool(callers);
        try {
            List<Future<WorldSection>> futures = new ArrayList<>();
            for (int i = 0; i < callers; i++) {
                futures.add(executor.submit(() -> {
                    WorldSection section = tracker.acquire(key, false);
                    acquired.countDown();
                    assertTrue(acquired.await(5, TimeUnit.SECONDS));
                    section.release();
                    return section;
                }));
            }
            assertTrue(loaderEntered.await(5, TimeUnit.SECONDS));
            releaseLoader.countDown();
            WorldSection expected = futures.get(0).get(5, TimeUnit.SECONDS);
            for (Future<WorldSection> future : futures) {
                assertSame(expected, future.get(5, TimeUnit.SECONDS));
            }
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        }
        assertEquals(1, loadCalls.get());
        assertEquals(0, tracker.getLoadedCacheCount());
        assertEquals(1, tracker.getSecondaryCacheSize());
        assertTrue(tracker.getLoaderWaitCount() > 0L);
    }

    @Test
    void nullOnEmptyAndSecondaryReusePreserveStatusWithoutReload() {
        AtomicInteger loadCalls = new AtomicInteger();
        ActiveSectionTracker tracker = new ActiveSectionTracker(3, section -> {
            loadCalls.incrementAndGet();
            return SectionStorage.LOAD_MISSING;
        }, 8);
        assertNull(tracker.acquire(0, 9, 2, 1, true));
        assertNull(tracker.acquire(0, 9, 2, 1, true));
        WorldSection temporaryAir = tracker.acquire(0, 9, 2, 1, false);
        assertNotNull(temporaryAir);
        assertEquals(SectionStorage.LOAD_MISSING, temporaryAir.getStorageLoadStatus());
        temporaryAir.release();
        assertEquals(1, loadCalls.get());
        assertEquals(2L, tracker.getSecondaryCacheHits());
        assertEquals(1L, tracker.getSecondaryCacheMisses());
    }

    @Test
    void shardedSecondaryCacheRemainsWithinGlobalBudget() {
        ActiveSectionTracker tracker = new ActiveSectionTracker(
                4,
                section -> SectionStorage.LOAD_OK,
                17);
        for (int i = 0; i < 2_000; i++) {
            tracker.acquire(0, i, 0, i * 31, false).release();
        }
        assertTrue(tracker.getSecondaryCacheSize() <= 17);
        assertTrue(tracker.getSecondaryCacheEvictions() > 0L);
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("active-section-tracker-test-timeout");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("active-section-tracker-test-interrupted", e);
        }
    }
}
