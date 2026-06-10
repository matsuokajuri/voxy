package me.cortex.voxy.common.config.storage.inmemory;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import me.cortex.voxy.common.config.storage.StorageBackend;
import me.cortex.voxy.common.util.MemoryBuffer;
import me.cortex.voxy.common.world.WorldSection;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.util.function.LongConsumer;

//TODO: replace synchronize with StampedLock
public class MemoryStorageBackend extends StorageBackend {
    private final Long2ObjectMap<MemoryBuffer>[] maps;
    private final Int2ObjectMap<ByteBuffer> idMappings = new Int2ObjectOpenHashMap<>();

    public MemoryStorageBackend() {
        this(4);
    }

    private Long2ObjectMap<MemoryBuffer> getMap(long key) {
        return this.maps[(int) (mixStafford13(mixStafford13(key)^key)&(this.maps.length-1))];
    }

    private static long mixStafford13(long value) {
        value = (value ^ (value >>> 30)) * 0xbf58476d1ce4e5b9L;
        value = (value ^ (value >>> 27)) * 0x94d049bb133111ebL;
        return value ^ (value >>> 31);
    }

    @Override
    public void iteratePositions(int level, LongConsumer consumer) {
        LongConsumer filtered = consumer;
        if (level != -1) {
            filtered = (key) -> {
                if (WorldSection.getLevel(key) == level) {
                    consumer.accept(key);
                }
            };
        }
        for (var map : this.maps) {
            synchronized (map) {
                map.keySet().forEach(filtered);
            }
        }
    }

    public MemoryStorageBackend(int slicesBitCount) {
        this.maps = new Long2ObjectMap[1<<slicesBitCount];
        for (int i = 0; i < this.maps.length; i++) {
            this.maps[i] = new Long2ObjectOpenHashMap<>();
        }
    }

    @Override
    public MemoryBuffer getSectionData(long key, MemoryBuffer scratch) {
        var map = this.getMap(key);
        synchronized (map) {
            var data = map.get(key);
            if (data != null) {
                data.cpyTo(scratch.address);
                return scratch.subSize(data.size);
            } else {
                return null;
            }
        }
    }

    @Override
    public void setSectionData(long key, MemoryBuffer data) {
        var map = this.getMap(key);
        synchronized (map) {
            var cpy = data.copy();
            var old = map.put(key, cpy);
            if (old != null) {
                old.free();
            }
        }
    }

    @Override
    public void deleteSectionData(long key) {
        var map = this.getMap(key);
        synchronized (map) {
            var data = map.remove(key);
            if (data != null) {
                data.free();
            }
        }
    }

    @Override
    public void putIdMapping(int id, ByteBuffer data) {
        synchronized (this.idMappings) {
            var cpy = MemoryUtil.memAlloc(data.remaining());
            MemoryUtil.memCopy(data, cpy);
            var prev = this.idMappings.put(id, cpy);
            if (prev != null) {
                MemoryUtil.memFree(prev);
            }
        }
    }

    @Override
    public Int2ObjectOpenHashMap<byte[]> getIdMappingsData() {
        Int2ObjectOpenHashMap<byte[]> out = new Int2ObjectOpenHashMap<>();
        synchronized (this.idMappings) {
            for (var entry : this.idMappings.int2ObjectEntrySet()) {
                var buf = new byte[entry.getValue().remaining()];
                entry.getValue().get(buf);
                entry.getValue().rewind();
                out.put(entry.getIntKey(), buf);
            }
            return out;
        }
    }

    @Override
    public void flush() {

    }

    @Override
    public void close() {
        for (var map : this.maps) {
            map.values().forEach(MemoryBuffer::free);
        }
        this.idMappings.values().forEach(MemoryUtil::memFree);
    }
}
