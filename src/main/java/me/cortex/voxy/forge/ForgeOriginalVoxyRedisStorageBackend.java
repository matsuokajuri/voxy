package me.cortex.voxy.forge;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import me.cortex.voxy.common.config.storage.StorageBackend;
import me.cortex.voxy.common.util.MemoryBuffer;
import me.cortex.voxy.common.util.UnsafeUtil;
import redis.clients.jedis.JedisPool;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.function.LongConsumer;

/** Forge namespace port of original {@code RedisStorageBackend}. */
final class ForgeOriginalVoxyRedisStorageBackend extends StorageBackend {
    private final JedisPool pool;
    private final String user;
    private final String password;
    private final byte[] world;
    private final byte[] mappings;

    ForgeOriginalVoxyRedisStorageBackend(String host, int port, String prefix) {
        this(host, port, prefix, null, null);
    }

    ForgeOriginalVoxyRedisStorageBackend(
            String host,
            int port,
            String prefix,
            String user,
            String password) {
        this.pool = new JedisPool(host, port);
        this.user = user;
        this.password = password;
        this.world = (prefix + "world_sections").getBytes(StandardCharsets.UTF_8);
        this.mappings = (prefix + "id_mappings").getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public void iteratePositions(int level, LongConsumer consumer) {
        throw new IllegalStateException("Not yet implemented");
    }

    @Override
    public MemoryBuffer getSectionData(long key, MemoryBuffer scratch) {
        try (var jedis = this.pool.getResource()) {
            if (this.user != null) {
                jedis.auth(this.user, this.password);
            }
            byte[] result = jedis.hget(this.world, longToBytes(key));
            if (result == null) {
                return null;
            }
            UnsafeUtil.memcpy(result, scratch.address);
            return scratch.subSize(result.length);
        }
    }

    @Override
    public void setSectionData(long key, MemoryBuffer data) {
        try (var jedis = this.pool.getResource()) {
            if (this.user != null) {
                jedis.auth(this.user, this.password);
            }
            byte[] buffer = new byte[(int) data.size];
            UnsafeUtil.memcpy(data.address, buffer);
            jedis.hset(this.world, longToBytes(key), buffer);
        }
    }

    @Override
    public void deleteSectionData(long key) {
        try (var jedis = this.pool.getResource()) {
            if (this.user != null) {
                jedis.auth(this.user, this.password);
            }
            jedis.hdel(this.world, longToBytes(key));
        }
    }

    @Override
    public void putIdMapping(int id, ByteBuffer data) {
        try (var jedis = this.pool.getResource()) {
            if (this.user != null) {
                jedis.auth(this.user, this.password);
            }
            byte[] buffer = new byte[data.remaining()];
            data.get(buffer);
            data.rewind();
            jedis.hset(this.mappings, intToBytes(id), buffer);
        }
    }

    @Override
    public Int2ObjectOpenHashMap<byte[]> getIdMappingsData() {
        try (var jedis = this.pool.getResource()) {
            if (this.user != null) {
                jedis.auth(this.user, this.password);
            }
            var storedMappings = jedis.hgetAll(this.mappings);
            Int2ObjectOpenHashMap<byte[]> output = new Int2ObjectOpenHashMap<>();
            if (storedMappings == null) {
                return output;
            }
            for (var entry : storedMappings.entrySet()) {
                output.put(bytesToInt(entry.getKey()), entry.getValue());
            }
            return output;
        }
    }

    @Override
    public void flush() {
    }

    @Override
    public void close() {
        this.pool.close();
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
}
