package me.cortex.voxy.forge;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import me.cortex.voxy.common.config.storage.StorageBackend;
import me.cortex.voxy.common.util.MemoryBuffer;
import me.cortex.voxy.common.util.UnsafeUtil;
import me.cortex.voxy.common.world.WorldEngine;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.params.ScanParams;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Function;
import java.util.function.LongConsumer;

/** Forge namespace port of original {@code RedisStorageBackend}. */
final class RedisStorageBackend extends StorageBackend {
    private static final int HASH_SCAN_BATCH_SIZE = 512;

    private final JedisPool pool;
    private final String user;
    private final String password;
    private final byte[] world;
    private final byte[] mappings;
    private final ReentrantReadWriteLock lifecycleLock = new ReentrantReadWriteLock(true);
    private boolean closed;

    RedisStorageBackend(String host, int port, String prefix) {
        this(host, port, prefix, null, null);
    }

    RedisStorageBackend(
            String host,
            int port,
            String prefix,
            String user,
            String password) {
        this(new JedisPool(host, port), prefix, user, password);
    }

    RedisStorageBackend(JedisPool pool, String prefix, String user, String password) {
        this.pool = Objects.requireNonNull(pool, "pool");
        this.user = user;
        this.password = password;
        this.world = (prefix + "world_sections").getBytes(StandardCharsets.UTF_8);
        this.mappings = (prefix + "id_mappings").getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public void iteratePositions(int level, LongConsumer consumer) {
        this.withJedis(jedis -> {
            LongOpenHashSet emitted = new LongOpenHashSet();
            byte[] cursor = ScanParams.SCAN_POINTER_START_BINARY;
            ScanParams scanParameters = new ScanParams().count(HASH_SCAN_BATCH_SIZE);
            boolean complete;
            do {
                var page = jedis.hscan(this.world, cursor, scanParameters);
                for (var entry : page.getResult()) {
                    long key = bytesToLong(entry.getKey());
                    if ((level == -1 || WorldEngine.getLevel(key) == level) && emitted.add(key)) {
                        consumer.accept(key);
                    }
                }
                cursor = page.getCursorAsBytes();
                complete = page.isCompleteIteration();
            } while (!complete);
            return null;
        });
    }

    @Override
    public MemoryBuffer getSectionData(long key, MemoryBuffer scratch) {
        return this.withJedis(jedis -> {
            byte[] result = jedis.hget(this.world, longToBytes(key));
            if (result == null) {
                return null;
            }
            UnsafeUtil.memcpy(result, scratch.address);
            return scratch.subSize(result.length);
        });
    }

    @Override
    public void setSectionData(long key, MemoryBuffer data) {
        this.withJedis(jedis -> {
            byte[] buffer = new byte[(int) data.size];
            UnsafeUtil.memcpy(data.address, buffer);
            jedis.hset(this.world, longToBytes(key), buffer);
            return null;
        });
    }

    @Override
    public void deleteSectionData(long key) {
        this.withJedis(jedis -> {
            jedis.hdel(this.world, longToBytes(key));
            return null;
        });
    }

    @Override
    public void putIdMapping(int id, ByteBuffer data) {
        this.withJedis(jedis -> {
            byte[] buffer = new byte[data.remaining()];
            data.get(buffer);
            data.rewind();
            jedis.hset(this.mappings, intToBytes(id), buffer);
            return null;
        });
    }

    @Override
    public Int2ObjectOpenHashMap<byte[]> getIdMappingsData() {
        return this.withJedis(jedis -> {
            var storedMappings = jedis.hgetAll(this.mappings);
            Int2ObjectOpenHashMap<byte[]> output = new Int2ObjectOpenHashMap<>();
            if (storedMappings == null) {
                return output;
            }
            for (var entry : storedMappings.entrySet()) {
                output.put(bytesToInt(entry.getKey()), entry.getValue());
            }
            return output;
        });
    }

    @Override
    public void flush() {
        this.withOpenReadLock(() -> null);
    }

    @Override
    public void close() {
        if (this.lifecycleLock.getReadHoldCount() != 0) {
            throw new IllegalStateException("Cannot close Redis storage from an active operation callback");
        }
        var lock = this.lifecycleLock.writeLock();
        lock.lock();
        try {
            if (this.closed) {
                return;
            }
            this.closed = true;
            this.pool.close();
        } finally {
            lock.unlock();
        }
    }

    private <T> T withJedis(Function<Jedis, T> operation) {
        return this.withOpenReadLock(() -> {
            try (Jedis jedis = this.pool.getResource()) {
                if (this.user != null) {
                    jedis.auth(this.user, this.password);
                }
                return operation.apply(jedis);
            }
        });
    }

    private <T> T withOpenReadLock(java.util.function.Supplier<T> operation) {
        var lock = this.lifecycleLock.readLock();
        lock.lock();
        try {
            if (this.closed) {
                throw new IllegalStateException("Redis storage is closed");
            }
            return operation.get();
        } finally {
            lock.unlock();
        }
    }

    private static byte[] intToBytes(int value) {
        return new byte[]{(byte) (value >> 24), (byte) (value >> 16), (byte) (value >> 8), (byte) value};
    }

    private static int bytesToInt(byte[] value) {
        return (Byte.toUnsignedInt(value[0]) << 24)
                | (Byte.toUnsignedInt(value[1]) << 16)
                | (Byte.toUnsignedInt(value[2]) << 8)
                | Byte.toUnsignedInt(value[3]);
    }

    private static byte[] longToBytes(long value) {
        byte[] result = new byte[Long.BYTES];
        for (int i = Long.BYTES - 1; i >= 0; i--) {
            result[i] = (byte) (value & 0xFF);
            value >>= Byte.SIZE;
        }
        return result;
    }

    private static long bytesToLong(byte[] value) {
        if (value.length != Long.BYTES) {
            throw new IllegalStateException("Invalid Redis section-position key length " + value.length);
        }
        long result = 0;
        for (byte part : value) {
            result = (result << Byte.SIZE) | Byte.toUnsignedLong(part);
        }
        return result;
    }
}
