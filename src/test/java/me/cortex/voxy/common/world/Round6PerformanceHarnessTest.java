package me.cortex.voxy.common.world;

import me.cortex.voxy.common.config.section.SectionStorage;
import me.cortex.voxy.common.util.AllocationArena;
import me.cortex.voxy.common.util.HierarchicalBitSet;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("round6-performance")
class Round6PerformanceHarnessTest {
    private static final int WAITERS = 8;

    @Test
    void measuresContendedLoadWaitAndSecondaryCacheChurn() throws Exception {
        CountDownLatch loaderEntered = new CountDownLatch(1);
        CountDownLatch releaseLoader = new CountDownLatch(1);
        CountDownLatch releaseLoadedReference = new CountDownLatch(1);
        AtomicInteger loadCalls = new AtomicInteger();
        ActiveSectionTracker tracker = new ActiveSectionTracker(3, section -> {
            loadCalls.incrementAndGet();
            loaderEntered.countDown();
            await(releaseLoader);
            return SectionStorage.LOAD_OK;
        }, 64);
        long key = WorldEngine.getWorldSectionId(0, 12, 3, -7);
        AtomicReference<WorldSection> loaderSection = new AtomicReference<>();
        Thread loader = new Thread(() -> {
            WorldSection section = tracker.acquire(key, false);
            loaderSection.set(section);
            await(releaseLoadedReference);
            section.release();
        }, "round6-loader");
        loader.start();
        assertTrue(loaderEntered.await(5, TimeUnit.SECONDS));

        CountDownLatch ready = new CountDownLatch(WAITERS);
        CountDownLatch start = new CountDownLatch(1);
        List<WorldSection> acquired = Collections.synchronizedList(new ArrayList<>());
        Thread[] waiters = new Thread[WAITERS];
        for (int i = 0; i < waiters.length; i++) {
            waiters[i] = new Thread(() -> {
                ready.countDown();
                await(start);
                WorldSection section = tracker.acquire(key, false);
                acquired.add(section);
                section.release();
            }, "round6-waiter-" + i);
            waiters[i].start();
        }
        assertTrue(ready.await(5, TimeUnit.SECONDS));
        ThreadMXBean threads = ManagementFactory.getThreadMXBean();
        boolean cpuTime = threads.isThreadCpuTimeSupported();
        if (cpuTime && !threads.isThreadCpuTimeEnabled()) {
            threads.setThreadCpuTimeEnabled(true);
        }
        long[] before = cpuTimes(threads, waiters, cpuTime);
        start.countDown();
        Thread.sleep(200L);
        long waitingCpuNanos = cpuDelta(before, cpuTimes(threads, waiters, cpuTime));
        releaseLoader.countDown();
        for (Thread waiter : waiters) {
            waiter.join(5_000L);
            assertTrue(!waiter.isAlive());
        }
        releaseLoadedReference.countDown();
        loader.join(5_000L);
        assertTrue(!loader.isAlive());
        assertEquals(1, loadCalls.get());
        assertEquals(WAITERS, acquired.size());
        WorldSection expected = loaderSection.get();
        assertNotNull(expected);
        for (WorldSection section : acquired) {
            assertTrue(section == expected);
        }

        long churnStart = System.nanoTime();
        for (int pass = 0; pass < 20; pass++) {
            for (int i = 0; i < 64; i++) {
                tracker.acquire(0, i, 4, pass & 1, false).release();
            }
        }
        long churnNanos = System.nanoTime() - churnStart;
        System.out.println("ROUND6_RESULT tracker_wait_cpu_ns=" + waitingCpuNanos
                + " cache_churn_ns=" + churnNanos
                + " loads=" + loadCalls.get()
                + " secondary=" + tracker.getSecondaryCacheSize());
    }

    @Test
    void measuresAllocatorAndConsecutiveBitSetWork() {
        AllocationArena arena = new AllocationArena();
        arena.setLimit(1L << 24);
        long[] addresses = new long[16_384];
        long arenaStart = System.nanoTime();
        for (int i = 0; i < addresses.length; i++) {
            addresses[i] = arena.alloc((i & 127) + 1);
            assertTrue(addresses[i] != AllocationArena.SIZE_LIMIT);
        }
        for (int i = 0; i < addresses.length; i += 2) {
            arena.free(addresses[i]);
        }
        for (int i = 0; i < addresses.length; i += 2) {
            addresses[i] = arena.alloc((i & 63) + 1);
            assertTrue(addresses[i] != AllocationArena.SIZE_LIMIT);
        }
        for (long address : addresses) {
            arena.free(address);
        }
        long arenaNanos = System.nanoTime() - arenaStart;
        assertEquals(0L, arena.getSize());

        HierarchicalBitSet bitSet = new HierarchicalBitSet(1 << 20);
        int[] groups = new int[16_384];
        long bitSetStart = System.nanoTime();
        for (int i = 0; i < groups.length; i++) {
            groups[i] = bitSet.allocateNextConsecutiveCounted(32);
            assertTrue(groups[i] >= 0);
        }
        for (int group : groups) {
            for (int i = 0; i < 32; i++) {
                assertTrue(bitSet.free(group + i));
            }
        }
        long bitSetNanos = System.nanoTime() - bitSetStart;
        assertEquals(0, bitSet.getCount());
        System.out.println("ROUND6_RESULT arena_ns=" + arenaNanos
                + " bitset_consecutive_ns=" + bitSetNanos);
    }

    private static long[] cpuTimes(ThreadMXBean bean, Thread[] threads, boolean enabled) {
        long[] values = new long[threads.length];
        if (!enabled) {
            return values;
        }
        for (int i = 0; i < threads.length; i++) {
            values[i] = Math.max(0L, bean.getThreadCpuTime(threads[i].getId()));
        }
        return values;
    }

    private static long cpuDelta(long[] before, long[] after) {
        long result = 0L;
        for (int i = 0; i < before.length; i++) {
            result += Math.max(0L, after[i] - before[i]);
        }
        return result;
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("round-six-benchmark-timeout");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("round-six-benchmark-interrupted", e);
        }
    }
}
