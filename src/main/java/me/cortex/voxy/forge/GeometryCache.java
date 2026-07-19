package me.cortex.voxy.forge;

import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;

import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLongArray;
import java.util.concurrent.atomic.LongAdder;

final class GeometryCache {
    private static final int DEFAULT_MAX_ENTRIES = 2_048;
    private static final int EPOCH_STRIPES = 1 << 12;
    private final ReentrantLock lock = new ReentrantLock();
    private final AtomicLongArray epochs = new AtomicLongArray(EPOCH_STRIPES);
    private final AtomicInteger entries = new AtomicInteger();
    private long maxCombinedSize;
    private final int maxEntries;
    private long currentSize;
    private final LongAdder hits = new LongAdder();
    private final LongAdder misses = new LongAdder();
    private final LongAdder evictions = new LongAdder();
    private final LongAdder staleRejects = new LongAdder();
    private final Long2ObjectLinkedOpenHashMap<BuiltSection> cache = new Long2ObjectLinkedOpenHashMap<>();

    GeometryCache(long maxSize) {
        this(maxSize, DEFAULT_MAX_ENTRIES);
    }

    GeometryCache(long maxSize, int maxEntries) {
        if (maxSize < 0L || maxEntries < 1) {
            throw new IllegalArgumentException("invalid-geometry-cache-bound");
        }
        this.maxCombinedSize = maxSize;
        this.maxEntries = maxEntries;
    }

    long snapshotEpoch(long position) {
        return this.epochs.get(epochIndex(position));
    }

    void put(BuiltSection section, long expectedEpoch) {
        int epochIndex = epochIndex(section.position);
        if (this.epochs.get(epochIndex) != expectedEpoch) {
            this.staleRejects.increment();
            section.free();
            return;
        }
        BuiltSection previous;
        this.lock.lock();
        try {
            if (this.epochs.get(epochIndex) != expectedEpoch) {
                this.staleRejects.increment();
                section.free();
                return;
            }
            previous = this.cache.put(section.position, section);
            this.currentSize += sizeOf(section);
            if (previous != null) {
                this.currentSize -= sizeOf(previous);
            } else {
                this.entries.incrementAndGet();
            }
            while (this.currentSize > this.maxCombinedSize || this.cache.size() > this.maxEntries) {
                BuiltSection entry = this.cache.removeFirst();
                this.currentSize -= sizeOf(entry);
                this.entries.decrementAndGet();
                entry.free();
                this.evictions.increment();
            }
        } finally {
            this.lock.unlock();
        }
        if (previous != null) {
            previous.free();
        }
    }

    BuiltSection take(long position) {
        if (this.entries.get() == 0) {
            this.misses.increment();
            return null;
        }
        this.lock.lock();
        try {
            BuiltSection section = this.cache.remove(position);
            if (section != null) {
                this.currentSize -= sizeOf(section);
                this.entries.decrementAndGet();
                this.hits.increment();
            } else {
                this.misses.increment();
            }
            return section;
        } finally {
            this.lock.unlock();
        }
    }

    void invalidate(long position) {
        this.epochs.incrementAndGet(epochIndex(position));
        BuiltSection section;
        this.lock.lock();
        try {
            section = this.cache.remove(position);
            if (section != null) {
                this.currentSize -= sizeOf(section);
                this.entries.decrementAndGet();
            }
        } finally {
            this.lock.unlock();
        }
        if (section != null) {
            section.free();
        }
    }

    void free() {
        this.lock.lock();
        try {
            this.cache.values().forEach(BuiltSection::free);
            this.cache.clear();
            this.entries.set(0);
            this.currentSize = 0L;
            for (int i = 0; i < EPOCH_STRIPES; i++) {
                this.epochs.incrementAndGet(i);
            }
        } finally {
            this.lock.unlock();
        }
    }

    long currentSize() {
        this.lock.lock();
        try {
            return this.currentSize;
        } finally {
            this.lock.unlock();
        }
    }

    int entryCount() {
        return this.entries.get();
    }

    long hits() {
        return this.hits.sum();
    }

    long misses() {
        return this.misses.sum();
    }

    long evictions() {
        return this.evictions.sum();
    }

    long staleRejects() {
        return this.staleRejects.sum();
    }

    private static long sizeOf(BuiltSection section) {
        long size = section.geometryBuffer == null ? 0L : section.geometryBuffer.size;
        return size + (section.occupancy == null ? 0L : section.occupancy.size);
    }

    private static int epochIndex(long position) {
        position = (position ^ position >>> 30) * -4658895280553007687L;
        position = (position ^ position >>> 27) * -7723592293110705685L;
        return (int) ((position ^ position >>> 31) & (EPOCH_STRIPES - 1));
    }
}
