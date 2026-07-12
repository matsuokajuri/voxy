package me.cortex.voxy.forge;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import me.cortex.voxy.common.config.storage.StorageBackend;
import me.cortex.voxy.common.util.MemoryBuffer;

import java.nio.ByteBuffer;
import java.util.function.LongConsumer;

/** Forge namespace port of original {@code ReadonlyCachingLayer}, including its upstream lifecycle behavior. */
final class ForgeOriginalVoxyReadonlyCachingLayer extends StorageBackend {
    private final StorageBackend cache;
    private final StorageBackend onMiss;

    ForgeOriginalVoxyReadonlyCachingLayer(StorageBackend cache, StorageBackend onMiss) {
        this.cache = cache;
        this.onMiss = onMiss;
    }

    @Override
    public MemoryBuffer getSectionData(long key, MemoryBuffer scratch) {
        MemoryBuffer result = this.cache.getSectionData(key, scratch);
        if (result != null) {
            return result;
        }
        result = this.onMiss.getSectionData(key, scratch);
        if (result != null) {
            this.cache.setSectionData(key, result);
        }
        return result;
    }

    @Override
    public void iteratePositions(int level, LongConsumer consumer) {
        throw new IllegalStateException("Not yet implemented");
    }

    @Override
    public void setSectionData(long key, MemoryBuffer data) {
        this.cache.setSectionData(key, data);
    }

    @Override
    public void deleteSectionData(long key) {
        this.cache.deleteSectionData(key);
    }

    @Override
    public void putIdMapping(int id, ByteBuffer data) {
        this.cache.putIdMapping(id, data);
    }

    @Override
    public Int2ObjectOpenHashMap<byte[]> getIdMappingsData() {
        return this.onMiss.getIdMappingsData();
    }

    @Override
    public void flush() {
        this.cache.close();
        this.onMiss.close();
    }

    @Override
    public void close() {
        this.cache.close();
        this.onMiss.close();
    }
}
