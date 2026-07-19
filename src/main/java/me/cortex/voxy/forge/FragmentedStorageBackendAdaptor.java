package me.cortex.voxy.forge;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import me.cortex.voxy.common.config.storage.MappingReplicaReconciler;
import me.cortex.voxy.common.config.storage.StorageBackend;
import me.cortex.voxy.common.util.MemoryBuffer;
import net.minecraft.world.level.levelgen.RandomSupport;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.function.LongConsumer;

/** Forge namespace port of original {@code FragmentedStorageBackendAdaptor}. */
final class FragmentedStorageBackendAdaptor extends StorageBackend {
    private final StorageBackend[] backends;

    FragmentedStorageBackendAdaptor(StorageBackend... backends) {
        this.backends = backends;
        int length = backends.length;
        if (length == 0 || (length & (length - 1)) != 0) {
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

    @Override
    public Int2ObjectOpenHashMap<byte[]> getIdMappingsData() {
        try {
            return MappingReplicaReconciler.reconcile(this.backends);
        } catch (RuntimeException exception) {
            VoxyForge.LOGGER.error("Unable to safely reconcile fragmented mapping replicas", exception);
            throw exception;
        }
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
