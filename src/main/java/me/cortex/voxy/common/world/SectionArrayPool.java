package me.cortex.voxy.common.world;

import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.LongSupplier;

final class SectionArrayPool {
    static final int ARRAY_BYTES = WorldSection.SECTION_VOLUME * Long.BYTES;
    static final int DEFAULT_MIN_CACHED = 32;
    static final int DEFAULT_MAX_CACHED = 400;
    private static final int BURST_ALLOCATION_COUNT = 32;
    private static final int GROW_STEP = 64;
    private static final int SHRINK_STEP = 32;
    private static final long QUIET_WINDOW_NANOS = 1_000_000_000L;

    private final int minCached;
    private final int maxCached;
    private final LongSupplier nanoTime;
    private final LongSupplier availableMemory;
    private final ConcurrentLinkedDeque<long[]> arrays = new ConcurrentLinkedDeque<>();
    private final AtomicInteger cached = new AtomicInteger();
    private final AtomicInteger releaseOperations = new AtomicInteger();
    private final AtomicInteger target;
    private final LongAdder allocations = new LongAdder();
    private final LongAdder reuses = new LongAdder();
    private final LongAdder drops = new LongAdder();
    private long allocationWindowStart;
    private int allocationsInWindow;

    SectionArrayPool() {
        this(
                DEFAULT_MIN_CACHED,
                DEFAULT_MAX_CACHED,
                System::nanoTime,
                SectionArrayPool::runtimeAvailableMemory);
    }

    SectionArrayPool(
            int minCached,
            int maxCached,
            LongSupplier nanoTime,
            LongSupplier availableMemory) {
        if (minCached < 0 || maxCached < minCached) {
            throw new IllegalArgumentException("invalid-section-array-pool-bounds");
        }
        this.minCached = minCached;
        this.maxCached = maxCached;
        this.nanoTime = nanoTime;
        this.availableMemory = availableMemory;
        this.target = new AtomicInteger(minCached);
        this.allocationWindowStart = nanoTime.getAsLong();
    }

    long[] borrow() {
        long[] array = this.arrays.poll();
        if (array != null) {
            this.cached.decrementAndGet();
            this.reuses.increment();
            return array;
        }
        this.allocations.increment();
        this.recordAllocation();
        return new long[WorldSection.SECTION_VOLUME];
    }

    void release(long[] array) {
        if (array == null || array.length != WorldSection.SECTION_VOLUME) {
            throw new IllegalArgumentException("invalid-world-section-array");
        }
        if ((this.releaseOperations.incrementAndGet() & 63) == 0) {
            this.reevaluate();
        }
        int count = this.cached.incrementAndGet();
        if (count <= this.target.get()) {
            this.arrays.add(array);
            return;
        }
        this.cached.decrementAndGet();
        this.drops.increment();
    }

    synchronized void reevaluate() {
        long available = this.availableMemory.getAsLong();
        long pressureFloor = Math.max(64L << 20, (long) ARRAY_BYTES * this.minCached * 2L);
        if (available < pressureFloor) {
            this.target.set(this.minCached);
            this.trimToTarget();
            this.allocationsInWindow = 0;
            this.allocationWindowStart = this.nanoTime.getAsLong();
            return;
        }
        long now = this.nanoTime.getAsLong();
        if (now - this.allocationWindowStart >= QUIET_WINDOW_NANOS) {
            if (this.allocationsInWindow < BURST_ALLOCATION_COUNT / 4) {
                this.target.updateAndGet(value -> Math.max(this.minCached, value - SHRINK_STEP));
                this.trimToTarget();
            }
            this.allocationsInWindow = 0;
            this.allocationWindowStart = now;
        }
    }

    int cachedCount() {
        return this.cached.get();
    }

    int targetCount() {
        return this.target.get();
    }

    long allocationCount() {
        return this.allocations.sum();
    }

    long reuseCount() {
        return this.reuses.sum();
    }

    long dropCount() {
        return this.drops.sum();
    }

    String performanceSummary() {
        return "cached=" + this.cachedCount()
                + ", target=" + this.targetCount()
                + ", allocations=" + this.allocationCount()
                + ", reuses=" + this.reuseCount()
                + ", drops=" + this.dropCount();
    }

    private synchronized void recordAllocation() {
        long now = this.nanoTime.getAsLong();
        if (now - this.allocationWindowStart >= QUIET_WINDOW_NANOS) {
            this.allocationsInWindow = 0;
            this.allocationWindowStart = now;
        }
        this.allocationsInWindow++;
        long growthReserve = (long) ARRAY_BYTES * this.maxCached * 2L;
        if (this.allocationsInWindow >= BURST_ALLOCATION_COUNT
                && this.availableMemory.getAsLong() >= growthReserve) {
            this.target.updateAndGet(value -> Math.min(this.maxCached, value + GROW_STEP));
            this.allocationsInWindow = 0;
            this.allocationWindowStart = now;
        }
    }

    private void trimToTarget() {
        while (this.cached.get() > this.target.get()) {
            long[] removed = this.arrays.poll();
            if (removed == null) {
                break;
            }
            this.cached.decrementAndGet();
            this.drops.increment();
        }
    }

    private static long runtimeAvailableMemory() {
        Runtime runtime = Runtime.getRuntime();
        return runtime.maxMemory() - (runtime.totalMemory() - runtime.freeMemory());
    }
}
