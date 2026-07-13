package me.cortex.voxy.forge;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import me.cortex.voxy.common.config.storage.StorageBackend;
import me.cortex.voxy.common.util.MemoryBuffer;
import net.minecraft.world.level.levelgen.RandomSupport;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;
import java.util.function.LongConsumer;

/** Forge namespace port of original {@code FragmentedStorageBackendAdaptor}. */
final class FragmentedStorageBackendAdaptor extends StorageBackend {
    private final StorageBackend[] backends;

    FragmentedStorageBackendAdaptor(StorageBackend... backends) {
        this.backends = backends;
        int length = backends.length;
        if ((length & (length - 1)) != 0) {
            throw new IllegalArgumentException("Backend count not a power of 2");
        }
    }

    private int getSegmentId(long key) {
        return (int) (RandomSupport.mixStafford13(RandomSupport.mixStafford13(key) ^ key)
                & (this.backends.length - 1));
    }

    @Override
    public void iteratePositions(int level, LongConsumer consumer) {
        for (StorageBackend backend : this.backends) {
            backend.iteratePositions(level, consumer);
        }
    }

    @Override
    public MemoryBuffer getSectionData(long key, MemoryBuffer scratch) {
        return this.backends[this.getSegmentId(key)].getSectionData(key, scratch);
    }

    @Override
    public void setSectionData(long key, MemoryBuffer data) {
        this.backends[this.getSegmentId(key)].setSectionData(key, data);
    }

    @Override
    public void deleteSectionData(long key) {
        this.backends[this.getSegmentId(key)].deleteSectionData(key);
    }

    @Override
    public void putIdMapping(int id, ByteBuffer data) {
        for (StorageBackend backend : this.backends) {
            backend.putIdMapping(id, data);
        }
    }

    private record EqualingArray(byte[] bytes) {
        @Override
        public boolean equals(Object object) {
            return Arrays.equals(this.bytes, ((EqualingArray) object).bytes);
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(this.bytes);
        }
    }

    @Override
    public Int2ObjectOpenHashMap<byte[]> getIdMappingsData() {
        Object2IntOpenHashMap<Int2ObjectOpenHashMap<EqualingArray>> verification = new Object2IntOpenHashMap<>();
        Int2ObjectOpenHashMap<EqualingArray> any = null;
        for (StorageBackend backend : this.backends) {
            Int2ObjectOpenHashMap<byte[]> mappings = backend.getIdMappingsData();
            if (mappings.isEmpty()) {
                continue;
            }
            Int2ObjectOpenHashMap<EqualingArray> repackaged = new Int2ObjectOpenHashMap<>(mappings.size());
            for (var entry : mappings.int2ObjectEntrySet()) {
                repackaged.put(entry.getIntKey(), new EqualingArray(entry.getValue()));
            }
            verification.addTo(repackaged, 1);
            any = repackaged;
        }
        if (any == null) {
            return new Int2ObjectOpenHashMap<>();
        }

        if (verification.size() != 1) {
            VoxyForge.LOGGER.error("Error id mapping not matching across all fragments, attempting to recover");
            Object2IntMap.Entry<Int2ObjectOpenHashMap<EqualingArray>> maximumEntry = null;
            for (var entry : verification.object2IntEntrySet()) {
                if (maximumEntry == null || maximumEntry.getIntValue() < entry.getIntValue()) {
                    maximumEntry = entry;
                }
            }
            any = maximumEntry.getKey();
        }

        Int2ObjectOpenHashMap<byte[]> output = new Int2ObjectOpenHashMap<>(any.size());
        for (var entry : any.int2ObjectEntrySet()) {
            output.put(entry.getIntKey(), entry.getValue().bytes);
        }
        return output;
    }

    @Override
    public void flush() {
        for (StorageBackend backend : this.backends) {
            backend.flush();
        }
    }

    @Override
    public void close() {
        for (StorageBackend backend : this.backends) {
            backend.close();
        }
    }

    @Override
    public List<StorageBackend> getChildBackends() {
        return List.of(this.backends);
    }
}
