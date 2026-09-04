package me.cortex.voxy.common.world;

import me.cortex.voxy.common.config.section.SectionSerializationStorage;
import me.cortex.voxy.common.config.section.SectionStorage;
import me.cortex.voxy.common.config.storage.inmemory.MemoryStorageBackend;
import me.cortex.voxy.common.voxelization.VoxelizedSection;
import me.cortex.voxy.common.world.other.Mapper;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class WorldUpdaterMissingSectionVisibilityTest {
    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void realVoxelizedIngestIsVisibleInsideDirtyCallbackAndAfterSavedSecondaryReuse() throws Exception {
        SectionSerializationStorage storage = new SectionSerializationStorage(new MemoryStorageBackend());
        WorldEngine engine = new WorldEngine(storage);
        WorldSection[] held = new WorldSection[WorldEngine.MAX_LOD_LAYER + 1];
        try (ActiveSectionDirtySaveConcurrencyTest.ControlledSaver saver =
                     new ActiveSectionDirtySaveConcurrencyTest.ControlledSaver(engine, false)) {
            try {
                VoxelizedSection input = VoxelizedSection.createEmpty().setPosition(1874, 8, 1874);
                long stone = Mapper.composeMappingId((byte) 15,
                        engine.getMapper().getIdForBlockState(Blocks.STONE.defaultBlockState()), 0);
                Arrays.fill(input.section, stone); // Uniform real blocks have the same value at every mip level.
                input.lvl0NonAirCount = 4096;
                for (int level = 0; level < held.length; level++) {
                    held[level] = engine.acquire(level, input.x >> (level + 1), input.y >> (level + 1), input.z >> (level + 1));
                    assertEquals(SectionStorage.LOAD_MISSING, held[level].getStorageLoadStatus());
                    assertNull(engine.acquireIfExists(held[level].key), "unmodified missing storage must not look authoritative");
                }
                WorldSection[] originalObjects = held.clone();
                List<String> invisibleCallbacks = new ArrayList<>();
                List<String> invisibleCachedReads = new ArrayList<>();
                engine.setDirtyCallback((section, flags, neighborMask) -> {
                    WorldSection visible = engine.acquireIfExists(section.key);
                    if (visible == null) {
                        invisibleCallbacks.add(WorldEngine.pprintPos(section.key));
                    } else {
                        if (visible != section) invisibleCallbacks.add("different object " + WorldEngine.pprintPos(section.key));
                        visible.release();
                    }
                });

                WorldUpdater.insertUpdate(engine, input);
                for (int level = 0; level < held.length; level++) {
                    assertIngestedData(held[level], input, stone);
                    WorldSection visible = engine.acquireIfExists(held[level].key);
                    if (visible == null) {
                        invisibleCachedReads.add(WorldEngine.pprintPos(held[level].key));
                    } else {
                        try {
                            assertSame(held[level], visible);
                            assertIngestedData(visible, input, stone);
                        } finally {
                            visible.release();
                        }
                    }
                }
                assertAll("ingested missing sections must become authoritative before remesh notification",
                        () -> assertTrue(invisibleCallbacks.isEmpty(), "dirty callback still received null: " + invisibleCallbacks),
                        () -> assertTrue(invisibleCachedReads.isEmpty(), "cached acquireIfExists still returned null: " + invisibleCachedReads));

                releaseHeld(held);
                saver.awaitSettled();
                assertEquals(0, engine.getActiveSectionCount());
                for (int level = 0; level < originalObjects.length; level++) {
                    WorldSection visible = engine.acquireIfExists(originalObjects[level].key);
                    assertNotNull(visible, "saving and secondary reuse must not reinstate the old missing status");
                    try {
                        assertSame(originalObjects[level], visible, "exercise secondary reuse of the exact formerly-missing object");
                        assertIngestedData(visible, input, stone);
                    } finally {
                        visible.release();
                    }
                    WorldSection loaded = WorldSection._createRawUntrackedUnsafeSection(level,
                            input.x >> (level + 1), input.y >> (level + 1), input.z >> (level + 1));
                    try {
                        assertEquals(SectionStorage.LOAD_OK, storage.loadSection(loaded));
                        assertIngestedData(loaded, input, stone);
                    } finally {
                        loaded._releaseArray();
                    }
                }
            } finally {
                releaseHeld(held);
                saver.awaitSettled();
            }
        } finally {
            engine.free();
        }
    }

    @Test
    void dontSaveDataChangeIsVisibleBeforeCallbackWithoutInventingAPersistedSection() {
        SectionSerializationStorage storage = new SectionSerializationStorage(new MemoryStorageBackend());
        WorldEngine engine = new WorldEngine(storage);
        WorldSection section = engine.acquire(0, 937, 4, 937);
        try {
            assertEquals(SectionStorage.LOAD_MISSING, section.getStorageLoadStatus());
            AtomicBoolean callbackSawData = new AtomicBoolean();
            engine.setDirtyCallback((changed, flags, neighbors) -> {
                WorldSection visible = engine.acquireIfExists(changed.key);
                if (visible != null) {
                    callbackSawData.set(visible == changed && visible._unsafeGetRawDataArray()[0] == Mapper.airWithLight(3));
                    visible.release();
                }
            });
            section._unsafeGetRawDataArray()[0] = Mapper.airWithLight(3);
            engine.markDirty(section, WorldEngine.UPDATE_TYPE_BLOCK_BIT | WorldEngine.UPDATE_TYPE_DONT_SAVE, 0);
            assertTrue(callbackSawData.get(), "DONT_SAVE controls persistence, not visibility of a genuine in-memory change");
            assertFalse(section.isDirty);
            WorldSection persisted = WorldSection._createRawUntrackedUnsafeSection(0, 937, 4, 937);
            try {
                assertEquals(SectionStorage.LOAD_MISSING, storage.loadSection(persisted));
            } finally {
                persisted._releaseArray();
            }
        } finally {
            section.release();
            engine.free();
        }
    }

    @Test
    void unchangedMissingAndUnavailableCorruptOrRecoveredStatusesAreNotBlindlyPromoted() {
        SectionSerializationStorage storage = new SectionSerializationStorage(new MemoryStorageBackend());
        WorldEngine engine = new WorldEngine(storage);
        try {
            WorldSection missing = engine.acquire(0, 937, 4, 937);
            try {
                engine.markDirty(missing, WorldEngine.UPDATE_TYPE_DONT_SAVE, 0);
                assertEquals(SectionStorage.LOAD_MISSING, missing.getStorageLoadStatus());
                assertNull(engine.acquireIfExists(missing.key));
            } finally {
                missing.release();
            }
            for (int status : new int[]{SectionStorage.LOAD_UNAVAILABLE, SectionStorage.LOAD_CORRUPT, SectionStorage.LOAD_RECOVERED}) {
                WorldSection section = engine.acquire(0, 950 + status, 4, 937);
                try {
                    section._setStorageLoadStatus(status);
                    section._unsafeGetRawDataArray()[0] = Mapper.airWithLight(3);
                    engine.markDirty(section, WorldEngine.UPDATE_TYPE_BLOCK_BIT | WorldEngine.UPDATE_TYPE_DONT_SAVE, 0);
                    assertEquals(status, section.getStorageLoadStatus(), "do not turn partial/unavailable data into authoritative data or downgrade recovery");
                    WorldSection visible = engine.acquireIfExists(section.key);
                    if (status == SectionStorage.LOAD_RECOVERED) {
                        assertNotNull(visible);
                        visible.release();
                    } else {
                        assertNull(visible);
                    }
                } finally {
                    section.release();
                }
            }
        } finally {
            engine.free();
        }
    }

    @Test
    void actualCorruptPersistedLevelZeroDataRemainsUnavailableAfterAPartialInMemoryChange() {
        MemoryStorageBackend backend = new MemoryStorageBackend();
        SectionSerializationStorage storage = new SectionSerializationStorage(backend);
        long key = WorldEngine.getWorldSectionId(0, 937, 4, 937);
        WorldSection wrong = WorldSection._createRawUntrackedUnsafeSection(0, 1, 2, 3);
        try {
            backend.setSectionData(key, SaveLoadSystem3.serialize(wrong));
        } finally {
            wrong._releaseArray();
        }
        WorldEngine engine = new WorldEngine(storage);
        WorldSection unavailable = engine.acquire(key);
        try {
            assertEquals(SectionStorage.LOAD_UNAVAILABLE, unavailable.getStorageLoadStatus());
            unavailable._unsafeGetRawDataArray()[0] = Mapper.airWithLight(9);
            engine.markDirty(unavailable, WorldEngine.UPDATE_TYPE_BLOCK_BIT | WorldEngine.UPDATE_TYPE_DONT_SAVE, 0);
            assertEquals(SectionStorage.LOAD_UNAVAILABLE, unavailable.getStorageLoadStatus());
            assertNull(engine.acquireIfExists(key));
            WorldSection verifyOriginal = WorldSection._createRawUntrackedUnsafeSection(0, 937, 4, 937);
            try {
                assertEquals(SectionStorage.LOAD_CORRUPT, storage.loadSection(verifyOriginal), "no replacement data was persisted over the corrupt original");
            } finally {
                verifyOriginal._releaseArray();
            }
        } finally {
            unavailable.release();
            engine.free();
        }
    }

    private static void assertIngestedData(WorldSection section, VoxelizedSection input, long value) {
        int level = section.lvl;
        int mask = (1 << (level + 1)) - 1;
        int x = (input.x & mask) << (4 - level);
        int y = (input.y & mask) << (4 - level);
        int z = (input.z & mask) << (4 - level);
        assertEquals(value, section._unsafeGetRawDataArray()[WorldSection.getIndex(x, y, z)]);
        int nonAir = 0;
        for (long block : section._unsafeGetRawDataArray()) if (!Mapper.isAir(block)) nonAir++;
        assertEquals(4096 >> (level * 3), nonAir, "real voxel/mip contents at level " + level);
        if (level == 0) {
            assertEquals(4096, section.getNonEmptyBlockCount());
            assertEquals(0xff, Byte.toUnsignedInt(section.getNonEmptyChildren()));
        } else {
            int childIndex = WorldSection.getChildIndex(input.x >> level, input.y >> level, input.z >> level);
            assertEquals(1 << childIndex, Byte.toUnsignedInt(section.getNonEmptyChildren()));
        }
    }

    private static void releaseHeld(WorldSection[] held) {
        for (int i = 0; i < held.length; i++) {
            WorldSection section = held[i];
            if (section != null) {
                held[i] = null;
                section.release();
            }
        }
    }
}
