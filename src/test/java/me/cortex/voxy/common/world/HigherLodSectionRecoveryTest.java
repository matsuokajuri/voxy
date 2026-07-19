package me.cortex.voxy.common.world;

import me.cortex.voxy.common.config.section.SectionSerializationStorage;
import me.cortex.voxy.common.config.section.SectionStorage;
import me.cortex.voxy.common.config.storage.inmemory.MemoryStorageBackend;
import me.cortex.voxy.common.util.MemoryBuffer;
import me.cortex.voxy.common.world.other.Mapper;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class HigherLodSectionRecoveryTest {
    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void corruptParentIsRebuiltAndPersistedFromEightChildren() {
        MemoryStorageBackend backend = new MemoryStorageBackend();
        SectionSerializationStorage storage = new SectionSerializationStorage(backend);
        saveChildren(storage, -1);
        long parentKey = WorldEngine.getWorldSectionId(1, 0, 0, 0);
        putWrongSectionUnder(backend, parentKey);

        WorldEngine engine = new WorldEngine(storage);
        WorldSection recovered = engine.acquireIfExists(1, 0, 0, 0);
        try {
            assertNotNull(recovered);
            for (int childY = 0; childY < 2; childY++) {
                for (int childZ = 0; childZ < 2; childZ++) {
                    for (int childX = 0; childX < 2; childX++) {
                        int childIndex = WorldSection.getChildIndex(childX, childY, childZ);
                        assertEquals(
                                childIndex + 1,
                                Mapper.getLightId(value(recovered,
                                        childX == 0 ? 0 : 20,
                                        childY == 0 ? 0 : 20,
                                        childZ == 0 ? 0 : 20)));
                    }
                }
            }

            WorldSection persisted = WorldSection._createRawUntrackedUnsafeSection(1, 0, 0, 0);
            try {
                assertEquals(SectionStorage.LOAD_OK, storage.loadSection(persisted));
                assertEquals(value(recovered, 20, 20, 20), value(persisted, 20, 20, 20));
            } finally {
                persisted._releaseArray();
            }
        } finally {
            recovered.release();
            engine.free();
        }
    }

    @Test
    void corruptParentRemainsUnavailableWhenAnyChildIsMissing() {
        MemoryStorageBackend backend = new MemoryStorageBackend();
        SectionSerializationStorage storage = new SectionSerializationStorage(backend);
        saveChildren(storage, 7);
        long parentKey = WorldEngine.getWorldSectionId(1, 0, 0, 0);
        putWrongSectionUnder(backend, parentKey);

        WorldEngine engine = new WorldEngine(storage);
        MemoryBuffer scratch = new MemoryBuffer(SectionSerializationStorage.BIGGEST_SERIALIZED_SECTION_SIZE + 1024L);
        MemoryBuffer persistedBytes = null;
        try {
            assertNull(engine.acquireIfExists(1, 0, 0, 0));
            assertNull(engine.acquireIfExists(1, 0, 0, 0));
            WorldSection temporaryAir = engine.acquire(1, 0, 0, 0);
            try {
                assertEquals(SectionStorage.LOAD_UNAVAILABLE, temporaryAir.getStorageLoadStatus());
            } finally {
                temporaryAir.release();
            }
            persistedBytes = backend.getSectionData(parentKey, scratch);
            assertNotNull(persistedBytes);
        } finally {
            if (persistedBytes == null) {
                scratch.free();
            } else {
                persistedBytes.free();
            }
            engine.free();
        }
    }

    private static void saveChildren(SectionSerializationStorage storage, int omittedChild) {
        for (int childY = 0; childY < 2; childY++) {
            for (int childZ = 0; childZ < 2; childZ++) {
                for (int childX = 0; childX < 2; childX++) {
                    int childIndex = WorldSection.getChildIndex(childX, childY, childZ);
                    if (childIndex == omittedChild) {
                        continue;
                    }
                    WorldSection child = WorldSection._createRawUntrackedUnsafeSection(
                            0,
                            childX,
                            childY,
                            childZ);
                    try {
                        Arrays.fill(child._unsafeGetRawDataArray(), Mapper.airWithLight(childIndex + 1));
                        storage.saveSection(child);
                    } finally {
                        child._releaseArray();
                    }
                }
            }
        }
    }

    private static void putWrongSectionUnder(MemoryStorageBackend backend, long targetKey) {
        WorldSection wrong = WorldSection._createRawUntrackedUnsafeSection(1, 9, 9, 9);
        try {
            Arrays.fill(wrong._unsafeGetRawDataArray(), Mapper.airWithLight(15));
            backend.setSectionData(targetKey, SaveLoadSystem3.serialize(wrong));
        } finally {
            wrong._releaseArray();
        }
    }

    private static long value(WorldSection section, int x, int y, int z) {
        return section._unsafeGetRawDataArray()[WorldSection.getIndex(x, y, z)];
    }
}
