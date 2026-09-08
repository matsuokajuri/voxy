package me.cortex.voxy.forge;

import me.cortex.voxy.common.util.MemoryBuffer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class Round11GeometryOwnershipTest {
    @Test
    void failedAuxiliaryReleaseRollsBackNewReservationAfterWrapperWasConsumed() {
        BasicAsyncGeometryManager manager = new BasicAsyncGeometryManager(1, 1024);
        BuiltSection section = new BuiltSection(11, (byte) 0, 0, new MemoryBuffer(64), new int[8],
                new MemoryBuffer(32) {
                    @Override
                    public void free() {
                        super.free();
                        throw new IllegalStateException("injected auxiliary cleanup failure");
                    }
                });
        try {
            assertThrows(IllegalStateException.class, () -> manager.uploadSection(section));
            assertFreed(section);
            assertEquals(0, manager.getSectionCount());
            assertEquals(0, manager.getGeometryUsedBytes());
            manager.verifyIntegrity();
        } finally {
            manager.clear();
        }
    }

    @Test
    void duplicateUploadCannotFreeTheManagersPendingBuffer() {
        BasicAsyncGeometryManager manager = new BasicAsyncGeometryManager(2, 2048);
        BuiltSection original = section(11, 64, 32);
        int id = manager.uploadSection(original);
        try {
            assertThrows(IllegalStateException.class, () -> manager.uploadSection(original));
            assertFalse(original.geometryBuffer.isFreed());
            assertTrue(original.occupancy.isFreed());
            manager.verifyIntegrity();
            assertEquals(1, manager.getAllocatedSectionIdsSnapshot().size());
            assertTrue(manager.getAllocatedSectionIdsSnapshot().contains(id));
        } finally {
            manager.clear();
        }
    }

    @Test
    void failedNewHeapAllocationReleasesSectionAndIdReservation() {
        BasicAsyncGeometryManager manager = new BasicAsyncGeometryManager(1, 1024);
        BuiltSection tooLarge = section(1, 2048, 32);
        try {
            assertThrows(IllegalStateException.class, () -> manager.uploadSection(tooLarge));
            assertFreed(tooLarge);
            assertEquals(0, manager.getSectionCount());
            assertEquals(0, manager.getGeometryUsedBytes());
            assertTrue(manager.getUploads().isEmpty());
            assertTrue(manager.getUpdateIds().isEmpty());
            assertEquals(0, manager.uploadSection(section(1, 64, 0)));
        } finally {
            manager.clear();
        }
    }

    @Test
    void invalidAlignmentDoesNotReserveAnId() {
        BasicAsyncGeometryManager manager = new BasicAsyncGeometryManager(2, 4096);
        BuiltSection invalid = section(1, 9, 32);
        try {
            assertThrows(IllegalStateException.class, () -> manager.uploadSection(invalid));
            assertFreed(invalid);
            assertEquals(0, manager.getSectionCount());
            assertEquals(0, manager.getGeometryUsedBytes());
        } finally {
            manager.clear();
        }
    }

    @Test
    void failedReplacementPreservesExistingMetadataAndPendingUpload() {
        BasicAsyncGeometryManager manager = new BasicAsyncGeometryManager(2, 2048);
        BuiltSection original = section(11, 64, 0);
        int originalId = manager.uploadSection(original);
        manager.uploadSection(section(12, 64, 0));
        manager.getUpdateIds().clear();
        BuiltSection tooLarge = section(11, 2048, 32);
        try (Metadata before = metadata(manager, originalId)) {
            assertThrows(IllegalStateException.class,
                    () -> manager.uploadReplaceSection(originalId, tooLarge));
            assertFreed(tooLarge);
            assertFalse(original.geometryBuffer.isFreed());
            assertEquals(2, manager.getSectionCount());
            assertEquals(2048, manager.getGeometryUsedBytes());
            assertTrue(manager.getUpdateIds().isEmpty());
            assertTrue(manager.getHeapRemovals().isEmpty());
            try (Metadata after = metadata(manager, originalId)) {
                assertArrayEquals(before.bytes(), after.bytes());
            }
        } finally {
            manager.clear();
        }
    }

    @Test
    void invalidReplacementDoesNotRemoveExistingCoverage() {
        BasicAsyncGeometryManager manager = new BasicAsyncGeometryManager(1, 1024);
        BuiltSection original = section(11, 64, 0);
        int originalId = manager.uploadSection(original);
        BuiltSection invalid = section(11, 9, 32);
        try {
            assertThrows(IllegalStateException.class,
                    () -> manager.uploadReplaceSection(originalId, invalid));
            assertFreed(invalid);
            assertFalse(original.geometryBuffer.isFreed());
            assertEquals(1, manager.getSectionCount());
            assertEquals(1024, manager.getGeometryUsedBytes());
        } finally {
            manager.clear();
        }
    }

    @Test
    void fullCapacityReplacementReusesReservationAndReleasesAuxiliaryMemory() {
        BasicAsyncGeometryManager manager = new BasicAsyncGeometryManager(1, 1024);
        BuiltSection original = section(11, 64, 32);
        int id = manager.uploadSection(original);
        BuiltSection replacement = section(11, 512, 32);
        try {
            assertTrue(original.occupancy.isFreed());
            assertEquals(id, manager.uploadReplaceSection(id, replacement));
            assertTrue(original.geometryBuffer.isFreed());
            assertTrue(replacement.occupancy.isFreed());
            assertFalse(replacement.geometryBuffer.isFreed());
            assertEquals(1, manager.getSectionCount());
            assertEquals(1024, manager.getGeometryUsedBytes());
            assertEquals(1, manager.getUploads().size());
            manager.removeSection(id);
            assertTrue(replacement.geometryBuffer.isFreed());
            assertEquals(0, manager.getSectionCount());
            assertEquals(0, manager.getGeometryUsedBytes());
        } finally {
            manager.clear();
        }
    }

    @Test
    void shrinkingReplacementReturnsUnusedReservation() {
        BasicAsyncGeometryManager manager = new BasicAsyncGeometryManager(2, 3072);
        int id = manager.uploadSection(section(11, 2048, 0));
        manager.uploadSection(section(12, 64, 0));
        try {
            assertEquals(id, manager.uploadReplaceSection(id, section(11, 64, 0)));
            assertEquals(2048, manager.getGeometryUsedBytes());
            assertEquals(2, manager.getSectionCount());
        } finally {
            manager.clear();
        }
    }

    @Test
    void growingAtTailDoesNotNeedADuplicateReservation() {
        BasicAsyncGeometryManager manager = new BasicAsyncGeometryManager(1, 2048);
        int id = manager.uploadSection(section(11, 64, 0));
        try {
            assertEquals(id, manager.uploadReplaceSection(id, section(11, 2048, 0)));
            assertEquals(2048, manager.getGeometryUsedBytes());
            assertEquals(1, manager.getSectionCount());
        } finally {
            manager.clear();
        }
    }

    @Test
    void relocationKeepsIdAndCancelsOldPendingUpload() {
        BasicAsyncGeometryManager manager = new BasicAsyncGeometryManager(3, 4096);
        BuiltSection original = section(11, 64, 0);
        int id = manager.uploadSection(original);
        manager.uploadSection(section(12, 64, 0));
        try {
            assertEquals(id, manager.uploadReplaceSection(id, section(11, 2048, 0)));
            assertTrue(original.geometryBuffer.isFreed());
            assertEquals(2, manager.getSectionCount());
            assertEquals(3072, manager.getGeometryUsedBytes());
            assertFalse(manager.getUploads().containsKey(0));
            assertTrue(manager.getHeapRemovals().contains(0));
        } finally {
            manager.clear();
        }
    }

    @Test
    void invalidOldIdStillConsumesIncomingSectionWithoutMutatingExistingState() {
        BasicAsyncGeometryManager manager = new BasicAsyncGeometryManager(1, 1024);
        BuiltSection incoming = section(11, 64, 32);
        try {
            assertThrows(IllegalStateException.class, () -> manager.uploadReplaceSection(0, incoming));
            assertFreed(incoming);
            assertEquals(0, manager.getSectionCount());
            assertEquals(0, manager.getGeometryUsedBytes());
        } finally {
            manager.clear();
            if (!incoming.geometryBuffer.isFreed()) incoming.free();
        }
    }

    @Test
    void publishedGeometryRemovalNeedsNoCpuCopyOrReadback() {
        BasicAsyncGeometryManager manager = new BasicAsyncGeometryManager(1, 1024);
        BuiltSection original = section(11, 64, 0);
        int id = manager.uploadSection(original);
        // The formal AsyncNodeManager transfers each pending upload into its sync copy before freeing it.
        manager.getUploads().values().forEach(MemoryBuffer::free);
        manager.getUploads().clear();
        try {
            manager.removeSection(id);
            assertEquals(0, manager.getGeometryUsedBytes());
            assertTrue(manager.getUploads().isEmpty());
            assertTrue(manager.getHeapRemovals().contains(0));
            assertThrows(IllegalStateException.class, () -> manager.removeSection(id));
            assertEquals(0, manager.getSectionCount());
        } finally {
            manager.clear();
        }
    }

    private static BuiltSection section(long position, long bytes, long occupancyBytes) {
        return new BuiltSection(position, (byte) 0x80, 1, new MemoryBuffer(bytes), new int[8],
                occupancyBytes == 0 ? null : new MemoryBuffer(occupancyBytes));
    }

    private static void assertFreed(BuiltSection section) {
        assertTrue(section.geometryBuffer.isFreed());
        if (section.occupancy != null) assertTrue(section.occupancy.isFreed());
    }

    private static Metadata metadata(BasicAsyncGeometryManager manager, int id) {
        Metadata result = new Metadata(new MemoryBuffer(32));
        manager.writeMetadata(id, result.buffer.address);
        return result;
    }

    private record Metadata(MemoryBuffer buffer) implements AutoCloseable {
        byte[] bytes() {
            byte[] bytes = new byte[32];
            buffer.asByteBuffer().get(bytes);
            return bytes;
        }

        public void close() {
            buffer.free();
        }
    }
}
