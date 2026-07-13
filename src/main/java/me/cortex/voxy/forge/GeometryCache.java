package me.cortex.voxy.forge;

import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;

import java.util.concurrent.locks.ReentrantLock;

final class GeometryCache {
    private final ReentrantLock lock = new ReentrantLock();
    private long maxCombinedSize;
    private long currentSize;
    private final Long2ObjectLinkedOpenHashMap<BuiltSection> cache = new Long2ObjectLinkedOpenHashMap<>();

    GeometryCache(long maxSize) {
        this.setMaxTotalSize(maxSize);
    }

    void setMaxTotalSize(long size) {
        this.maxCombinedSize = size;
    }

    void put(BuiltSection section) {
        this.lock.lock();
        BuiltSection previous = this.cache.put(section.position, section);
        this.currentSize += section.geometryBuffer.size;
        if (previous != null) {
            this.currentSize -= previous.geometryBuffer.size;
        }
        while (this.maxCombinedSize <= this.currentSize) {
            BuiltSection entry = this.cache.removeFirst();
            this.currentSize -= entry.geometryBuffer.size;
            entry.free();
        }
        this.lock.unlock();
        if (previous != null) {
            previous.free();
        }
    }

    BuiltSection remove(long position) {
        this.lock.lock();
        BuiltSection section = this.cache.remove(position);
        if (section != null) {
            this.currentSize -= section.geometryBuffer.size;
        }
        this.lock.unlock();
        return section;
    }

    void clear(long position) {
        BuiltSection section = this.remove(position);
        if (section != null) {
            section.free();
        }
    }

    void free() {
        this.lock.lock();
        this.cache.values().forEach(BuiltSection::free);
        this.cache.clear();
        this.lock.unlock();
    }
}
