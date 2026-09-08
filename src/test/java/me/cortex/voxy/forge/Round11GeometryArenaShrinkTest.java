package me.cortex.voxy.forge;

import me.cortex.voxy.common.util.AllocationArena;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class Round11GeometryArenaShrinkTest {
    @Test
    void shrinkingAtTailReducesHeapHighWaterMark() {
        AllocationArena arena = new AllocationArena();
        assertEquals(0, arena.alloc(512));
        assertEquals(384, arena.shrink(0, 128));
        assertEquals(128, arena.getSize());
        assertEquals(128, arena.getSize(0));
        assertEquals(0, arena.numFreeBlocks());
        assertEquals(128, arena.free(0));
        assertEquals(0, arena.getSize());
    }

    @Test
    void releasedInteriorTailCoalescesWithAdjacentFreedAllocation() {
        AllocationArena arena = new AllocationArena();
        long first = arena.alloc(512);
        long second = arena.alloc(256);
        long third = arena.alloc(128);
        assertEquals(384, arena.shrink(first, 128));
        assertEquals(1, arena.numFreeBlocks());
        arena.free(second);
        assertEquals(640, arena.getLargestFreeBlockSize(0));
        assertEquals(128, arena.alloc(640));
        arena.free(first);
        arena.free(128);
        arena.free(third);
        assertEquals(0, arena.getSize());
    }

    @Test
    void shrinkCanRestoreItsReservationWithOriginalExpand() {
        AllocationArena arena = new AllocationArena();
        arena.alloc(512);
        arena.alloc(128);
        assertEquals(384, arena.shrink(0, 128));
        assertTrue(arena.expand(0, 384));
        assertEquals(512, arena.getSize(0));
        assertEquals(0, arena.numFreeBlocks());
        arena.free(0);
        arena.free(512);
        assertEquals(0, arena.getSize());
    }

    @Test
    void invalidShrinkDoesNotChangeAllocation() {
        AllocationArena arena = new AllocationArena();
        arena.alloc(128);
        assertThrows(IllegalArgumentException.class, () -> arena.shrink(0, 0));
        assertThrows(IllegalArgumentException.class, () -> arena.shrink(0, 129));
        assertThrows(IllegalArgumentException.class, () -> arena.shrink(64, 32));
        assertEquals(0, arena.shrink(0, 128));
        assertEquals(128, arena.getSize(0));
        assertEquals(128, arena.getSize());
        arena.free(0);
    }

    @Test
    void seededResizeRemoveReusePreservesNonOverlappingReservations() {
        for (int seed = 0; seed < 16; seed++) {
            Random random = new Random(seed);
            AllocationArena arena = new AllocationArena();
            arena.setLimit(65_536);
            ArrayList<Reservation> live = new ArrayList<>();
            for (int step = 0; step < 2000; step++) {
                String context = "seed=" + seed + ", step=" + step;
                if (live.isEmpty() || random.nextInt(3) == 0) {
                    int size = random.nextInt(512) + 1;
                    long addr = arena.alloc(size);
                    if (addr != AllocationArena.SIZE_LIMIT) live.add(new Reservation(addr, size));
                } else {
                    int index = random.nextInt(live.size());
                    Reservation old = live.get(index);
                    if (random.nextBoolean()) {
                        int smaller = random.nextInt(old.size) + 1;
                        assertEquals(old.size - smaller, arena.shrink(old.addr, smaller), context);
                        live.set(index, new Reservation(old.addr, smaller));
                    } else {
                        assertEquals(old.size, arena.free(old.addr), context);
                        live.remove(index);
                    }
                }
                live.sort((left, right) -> Long.compare(left.addr, right.addr));
                long end = 0;
                for (Reservation reservation : live) {
                    assertTrue(reservation.addr >= end, context);
                    assertEquals(reservation.size, arena.getSize(reservation.addr), context);
                    end = reservation.addr + reservation.size;
                }
                assertEquals(end, arena.getSize(), context);
            }
            for (Reservation reservation : live) arena.free(reservation.addr);
            assertEquals(0, arena.getSize(), "seed=" + seed);
            assertEquals(0, arena.numFreeBlocks(), "seed=" + seed);
        }
    }

    private record Reservation(long addr, int size) {}
}
