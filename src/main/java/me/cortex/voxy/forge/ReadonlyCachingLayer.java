package me.cortex.voxy.forge;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import me.cortex.voxy.common.config.storage.StorageBackend;
import me.cortex.voxy.common.util.MemoryBuffer;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.LongConsumer;
import java.util.function.Supplier;

/** Forge namespace port of original {@code ReadonlyCachingLayer}, with Forxy enumeration and lifecycle repairs. */
final class ReadonlyCachingLayer extends StorageBackend {
    private final StorageBackend cache;
    private final StorageBackend onMiss;
    private final ReentrantLock mappingLock = new ReentrantLock();
    private final ReentrantReadWriteLock lifecycleLock = new ReentrantReadWriteLock(true);
    private boolean closed;

    ReadonlyCachingLayer(StorageBackend cache, StorageBackend onMiss) {
        this.cache = cache;
        this.onMiss = onMiss;
    }

    @Override
    public MemoryBuffer getSectionData(long key, MemoryBuffer scratch) {
        return this.withOpenReadLock(() -> {
            MemoryBuffer result = this.cache.getSectionData(key, scratch);
            if (result != null) {
                return result;
            }
            result = this.onMiss.getSectionData(key, scratch);
            if (result != null) {
                this.cache.setSectionData(key, result);
            }
            return result;
        });
    }

    @Override
    public void iteratePositions(int level, LongConsumer consumer) {
        this.withOpenReadLock(() -> {
            LongOpenHashSet emitted = new LongOpenHashSet();
            this.cache.iteratePositions(level, key -> {
                if (emitted.add(key)) {
                    consumer.accept(key);
                }
            });
            this.onMiss.iteratePositions(level, key -> {
                if (emitted.add(key)) {
                    consumer.accept(key);
                }
            });
            return null;
        });
    }

    @Override
    public void setSectionData(long key, MemoryBuffer data) {
        this.withOpenReadLock(() -> {
            this.cache.setSectionData(key, data);
            return null;
        });
    }

    @Override
    public void deleteSectionData(long key) {
        this.withOpenReadLock(() -> {
            this.cache.deleteSectionData(key);
            return null;
        });
    }

    @Override
    public void putIdMapping(int id, ByteBuffer data) {
        this.withOpenReadLock(() -> {
            this.mappingLock.lock();
            try {
                this.cache.putIdMapping(id, data);
            } finally {
                this.mappingLock.unlock();
            }
            return null;
        });
    }

    @Override
    public Int2ObjectOpenHashMap<byte[]> getIdMappingsData() {
        return this.withOpenReadLock(() -> {
            this.mappingLock.lock();
            try {
                Int2ObjectOpenHashMap<byte[]> cached = this.cache.getIdMappingsData();
                Int2ObjectOpenHashMap<byte[]> source = this.onMiss.getIdMappingsData();

                for (var entry : source.int2ObjectEntrySet()) {
                    if (cached.containsKey(entry.getIntKey())
                            && !Arrays.equals(cached.get(entry.getIntKey()), entry.getValue())) {
                        throw new IllegalStateException("Readonly cache id mapping conflict for id " + entry.getIntKey());
                    }
                }

                for (var entry : source.int2ObjectEntrySet()) {
                    if (!cached.containsKey(entry.getIntKey())) {
                        this.replicateMapping(entry.getIntKey(), entry.getValue());
                        cached.put(entry.getIntKey(), Arrays.copyOf(entry.getValue(), entry.getValue().length));
                    }
                }

                Int2ObjectOpenHashMap<byte[]> output = new Int2ObjectOpenHashMap<>(cached.size());
                for (var entry : cached.int2ObjectEntrySet()) {
                    output.put(entry.getIntKey(), Arrays.copyOf(entry.getValue(), entry.getValue().length));
                }
                return output;
            } finally {
                this.mappingLock.unlock();
            }
        });
    }

    @Override
    public void flush() {
        this.withOpenReadLock(() -> {
            this.cache.flush();
            this.onMiss.flush();
            return null;
        });
    }

    @Override
    public void close() {
        if (this.lifecycleLock.getReadHoldCount() != 0) {
            throw new IllegalStateException("Cannot close readonly cache from an active operation callback");
        }
        var lock = this.lifecycleLock.writeLock();
        lock.lock();
        try {
            if (this.closed) {
                return;
            }
            this.closed = true;
            RuntimeException failure = null;
            try {
                this.cache.close();
            } catch (RuntimeException exception) {
                failure = exception;
            }
            try {
                this.onMiss.close();
            } catch (RuntimeException exception) {
                if (failure == null) {
                    failure = exception;
                } else {
                    failure.addSuppressed(exception);
                }
            }
            if (failure != null) {
                throw failure;
            }
        } finally {
            lock.unlock();
        }
    }

    @Override
    public List<StorageBackend> getChildBackends() {
        return List.of(this.cache, this.onMiss);
    }

    private void replicateMapping(int id, byte[] data) {
        ByteBuffer buffer = MemoryUtil.memAlloc(data.length);
        try {
            buffer.put(data);
            buffer.flip();
            this.cache.putIdMapping(id, buffer);
        } finally {
            MemoryUtil.memFree(buffer);
        }
    }

    private <T> T withOpenReadLock(Supplier<T> operation) {
        var lock = this.lifecycleLock.readLock();
        lock.lock();
        try {
            if (this.closed) {
                throw new IllegalStateException("Readonly cache is closed");
            }
            return operation.get();
        } finally {
            lock.unlock();
        }
    }
}
