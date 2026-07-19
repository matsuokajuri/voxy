package me.cortex.voxy.common.util;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AllocationArenaTest {
    @Test
    void rejectsUnrepresentableLimitInsteadOfCorruptingPackedFreeBlocks() {
        AllocationArena arena = new AllocationArena();
        assertThrows(IllegalArgumentException.class,
                () -> arena.setLimit(AllocationArena.MAX_ALLOCATION_SIZE + 1L));
        arena.setLimit(AllocationArena.MAX_ALLOCATION_SIZE);
        long address = arena.alloc((int) AllocationArena.MAX_ALLOCATION_SIZE);
        assertEquals(0L, address);
        assertEquals(AllocationArena.MAX_ALLOCATION_SIZE, arena.getSize(address));
        assertEquals((int) AllocationArena.MAX_ALLOCATION_SIZE, arena.free(address));
        assertEquals(0L, arena.getSize());
    }

    @Test
    void randomizedBestFitAllocationsNeverOverlapAndFullyCoalesce() {
        AllocationArena arena = new AllocationArena();
        arena.setLimit(1L << 20);
        Random random = new Random(0xA110CA7EL);
        List<Block> live = new ArrayList<>();
        for (int operation = 0; operation < 20_000; operation++) {
            if (!live.isEmpty() && random.nextInt(3) == 0) {
                Block removed = live.remove(random.nextInt(live.size()));
                assertEquals(removed.size, arena.free(removed.address));
            } else {
                int size = random.nextInt(512) + 1;
                long address = arena.alloc(size);
                if (address != AllocationArena.SIZE_LIMIT) {
                    live.add(new Block(address, size));
                    assertNoOverlap(live);
                }
            }
        }
        for (Block block : live) {
            arena.free(block.address);
        }
        assertEquals(0L, arena.getSize());
        assertEquals(0, arena.numFreeBlocks());
        assertFalse(arena.expand(0L, 1));
        assertThrows(IllegalArgumentException.class, () -> arena.alloc(0));
        assertThrows(IllegalArgumentException.class, () -> arena.expand(0L, 0));
    }

    private static void assertNoOverlap(List<Block> blocks) {
        List<Block> ordered = new ArrayList<>(blocks);
        ordered.sort(Comparator.comparingLong(Block::address));
        for (int i = 1; i < ordered.size(); i++) {
            Block previous = ordered.get(i - 1);
            Block current = ordered.get(i);
            assertTrue(previous.address + previous.size <= current.address);
        }
    }

    private record Block(long address, int size) {
    }
}
