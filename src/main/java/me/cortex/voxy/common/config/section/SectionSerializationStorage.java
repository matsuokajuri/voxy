package me.cortex.voxy.common.config.section;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import me.cortex.voxy.common.Logger;
import me.cortex.voxy.common.config.storage.StorageBackend;
import me.cortex.voxy.common.util.ThreadLocalMemoryBuffer;
import me.cortex.voxy.common.world.SaveLoadSystem3;
import me.cortex.voxy.common.world.WorldSection;

import java.nio.ByteBuffer;
import java.util.function.LongConsumer;

public class SectionSerializationStorage extends SectionStorage {
    public static final int MINIMUM_SERIALIZED_SECTION_SIZE =
            Long.BYTES * 3 + WorldSection.SECTION_VOLUME * Short.BYTES;
    public static final int BIGGEST_SERIALIZED_SECTION_SIZE = 32 * 32 * 32 * 8 * 2 + 8;

    private final StorageBackend backend;
    public SectionSerializationStorage(StorageBackend storageBackend) {
        this.backend = storageBackend;
    }

    private static final ThreadLocalMemoryBuffer MEMORY_CACHE = new ThreadLocalMemoryBuffer(BIGGEST_SERIALIZED_SECTION_SIZE + 1024);

    public int loadSection(WorldSection into) {
        var data = this.backend.getSectionData(into.key, MEMORY_CACHE.get().createUntrackedUnfreeableReference());
        if (data != null) {
            if (!SaveLoadSystem3.deserialize(into, data)) {
                Logger.error("Persisted section " + into.key
                        + " is corrupt; retaining its bytes until child recovery is resolved");
                return LOAD_CORRUPT;
            } else {
                return LOAD_OK;
            }
        } else {
            //TODO: if we need to fetch an lod from a server, send the request here and block until the request is finished
            // the response should be put into the local db so that future data can just use that
            // the server can also send arbitrary updates to the client for arbitrary lods
            return LOAD_MISSING;
        }
    }


    @Override
    public void saveSection(WorldSection section) {
        var saveData = SaveLoadSystem3.serialize(section);
        this.backend.setSectionData(section.key, saveData);
        //Note that savedData isnt freed (the save system uses a cache)
    }

    @Override
    public void putIdMapping(int id, ByteBuffer data) {
        this.backend.putIdMapping(id, data);
    }

    @Override
    public Int2ObjectOpenHashMap<byte[]> getIdMappingsData() {
        return this.backend.getIdMappingsData();
    }

    @Override
    public void flush() {
        this.backend.flush();
    }

    @Override
    public void close() {
        this.backend.close();
    }

    @Override
    public void iteratePositions(int level, LongConsumer consumer) {
        this.backend.iteratePositions(level, consumer);
    }
}
