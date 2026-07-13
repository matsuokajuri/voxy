package me.cortex.voxy.forge;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import me.cortex.voxy.common.config.compressors.StorageCompressor;
import me.cortex.voxy.common.config.storage.StorageBackend;
import me.cortex.voxy.common.util.MemoryBuffer;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.function.LongConsumer;

/** Forge namespace port of original {@code CompressionStorageAdaptor}. */
final class CompressionStorageAdaptor extends StorageBackend {
    private final StorageCompressor compressor;
    private final StorageBackend delegate;

    CompressionStorageAdaptor(StorageCompressor compressor, StorageBackend delegate) {
        this.compressor = compressor;
        this.delegate = delegate;
    }

    @Override
    public MemoryBuffer getSectionData(long key, MemoryBuffer scratch) {
        MemoryBuffer data = this.delegate.getSectionData(key, scratch);
        return data == null ? null : this.compressor.decompress(data);
    }

    @Override
    public void setSectionData(long key, MemoryBuffer data) {
        this.delegate.setSectionData(key, this.compressor.compress(data));
    }

    @Override
    public void deleteSectionData(long key) {
        this.delegate.deleteSectionData(key);
    }

    @Override
    public void putIdMapping(int id, ByteBuffer data) {
        this.delegate.putIdMapping(id, data);
    }

    @Override
    public Int2ObjectOpenHashMap<byte[]> getIdMappingsData() {
        return this.delegate.getIdMappingsData();
    }

    @Override
    public void iteratePositions(int level, LongConsumer consumer) {
        this.delegate.iteratePositions(level, consumer);
    }

    @Override
    public void flush() {
        this.delegate.flush();
    }

    @Override
    public void close() {
        this.compressor.close();
        this.delegate.close();
    }

    @Override
    public List<StorageBackend> getChildBackends() {
        return List.of(this.delegate);
    }
}
