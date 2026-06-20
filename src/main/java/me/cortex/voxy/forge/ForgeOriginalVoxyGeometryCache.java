package me.cortex.voxy.forge;

import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;

import java.util.concurrent.locks.ReentrantLock;

final class ForgeOriginalVoxyGeometryCache {
    private final ReentrantLock lock = new ReentrantLock();
    private long maxCombinedSize;
    private long currentSize;
    private final Long2ObjectLinkedOpenHashMap<ForgeOriginalVoxyBuiltSection> cache = new Long2ObjectLinkedOpenHashMap<>();

    ForgeOriginalVoxyGeometryCache(long maxSize) {
        this.setMaxTotalSize(maxSize);
    }

    void setMaxTotalSize(long size) {
        this.maxCombinedSize = size;
    }

    void put(ForgeOriginalVoxyBuiltSection section) {
        this.lock.lock();
        ForgeOriginalVoxyBuiltSection previous = this.cache.put(section.position, section);
        this.currentSize += section.geometryBuffer.size;
        if (previous != null) {
            this.currentSize -= previous.geometryBuffer.size;
        }
        while (this.maxCombinedSize <= this.currentSize) {
            ForgeOriginalVoxyBuiltSection entry = this.cache.removeFirst();
            this.currentSize -= entry.geometryBuffer.size;
            entry.free();
        }
        this.lock.unlock();
        if (previous != null) {
            previous.free();
        }
    }

    ForgeOriginalVoxyBuiltSection remove(long position) {
        this.lock.lock();
        ForgeOriginalVoxyBuiltSection section = this.cache.remove(position);
        if (section != null) {
            this.currentSize -= section.geometryBuffer.size;
        }
        this.lock.unlock();
        return section;
    }

    void clear(long position) {
        ForgeOriginalVoxyBuiltSection section = this.remove(position);
        if (section != null) {
            section.free();
        }
    }

    void free() {
        this.lock.lock();
        this.cache.values().forEach(ForgeOriginalVoxyBuiltSection::free);
        this.cache.clear();
        this.lock.unlock();
    }
}
