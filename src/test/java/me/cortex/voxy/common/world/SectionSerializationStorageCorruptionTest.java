package me.cortex.voxy.common.world;

import me.cortex.voxy.common.config.section.SectionSerializationStorage;
import me.cortex.voxy.common.config.storage.inmemory.MemoryStorageBackend;
import me.cortex.voxy.common.util.MemoryBuffer;
import me.cortex.voxy.common.world.other.Mapper;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SectionSerializationStorageCorruptionTest {
    @Test
    void mismatchedSectionKeyIsDeletedAndRecoveredAsAir() {
        MemoryStorageBackend backend = new MemoryStorageBackend();
        SectionSerializationStorage storage = new SectionSerializationStorage(backend);
        WorldSection source = WorldSection._createRawUntrackedUnsafeSection(0, 1, 2, 3);
        WorldSection target = WorldSection._createRawUntrackedUnsafeSection(0, 4, 5, 6);
        MemoryBuffer scratch = new MemoryBuffer(SectionSerializationStorage.BIGGEST_SERIALIZED_SECTION_SIZE + 1024L);
        try {
            Arrays.fill(source._unsafeGetRawDataArray(), Mapper.AIR + 1L);
            Arrays.fill(target._unsafeGetRawDataArray(), Mapper.AIR + 2L);

            //Store valid bytes under the wrong section key. The embedded key check is the original
            // corruption boundary exercised by SectionSerializationStorage.loadSection().
            backend.setSectionData(target.key, SaveLoadSystem3.serialize(source));

            assertEquals(-1, storage.loadSection(target));
            assertTrue(Arrays.stream(target._unsafeGetRawDataArray()).allMatch(value -> value == Mapper.AIR));
            assertNull(backend.getSectionData(target.key, scratch));
        } finally {
            scratch.free();
            storage.close();
        }
    }
}
