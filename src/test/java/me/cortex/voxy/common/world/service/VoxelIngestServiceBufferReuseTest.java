package me.cortex.voxy.common.world.service;

import me.cortex.voxy.common.voxelization.VoxelizedSection;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

class VoxelIngestServiceBufferReuseTest {
    @Test
    void reusesOneVoxelizedSectionBackingArrayPerThread() throws InterruptedException {
        VoxelizedSection first = VoxelIngestService.acquireCachedSection(1, 2, 3);
        long[] firstBacking = first.section;
        firstBacking[0] = 0x1234L;

        VoxelizedSection second = VoxelIngestService.acquireCachedSection(4, 5, 6);
        assertSame(first, second);
        assertSame(firstBacking, second.section);
        assertEquals(0x1234L, second.section[0], "Buffer acquisition itself must not allocate or clear the array");
        assertEquals(4, second.x);
        assertEquals(5, second.y);
        assertEquals(6, second.z);

        AtomicReference<VoxelizedSection> otherThreadSection = new AtomicReference<>();
        Thread otherThread = new Thread(
                () -> otherThreadSection.set(VoxelIngestService.acquireCachedSection(7, 8, 9)),
                "voxy-section-cache-test");
        otherThread.start();
        otherThread.join();

        assertNotSame(first, otherThreadSection.get());
        assertNotSame(firstBacking, otherThreadSection.get().section);
    }
}
