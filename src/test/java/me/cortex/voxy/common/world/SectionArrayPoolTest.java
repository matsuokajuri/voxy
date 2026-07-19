package me.cortex.voxy.common.world;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SectionArrayPoolTest {
    @Test
    void growsForAllocationBurstAndReusesReturnedArrays() {
        AtomicLong now = new AtomicLong();
        AtomicLong available = new AtomicLong(1L << 40);
        SectionArrayPool pool = new SectionArrayPool(2, 130, now::get, available::get);
        List<long[]> arrays = new ArrayList<>();
        for (int i = 0; i < 64; i++) {
            arrays.add(pool.borrow());
        }
        assertTrue(pool.targetCount() > 2);
        arrays.forEach(pool::release);
        assertEquals(arrays.size(), pool.cachedCount());
        assertTrue(pool.cachedCount() <= pool.targetCount());

        SectionArrayPool reusePool = new SectionArrayPool(1, 2, now::get, available::get);
        long[] expected = reusePool.borrow();
        reusePool.release(expected);
        assertSame(expected, reusePool.borrow());
        assertEquals(1L, reusePool.reuseCount());
    }

    @Test
    void trimsToMinimumUnderMemoryPressureAndAfterQuietWork() {
        AtomicLong now = new AtomicLong();
        AtomicLong available = new AtomicLong(1L << 40);
        SectionArrayPool pool = new SectionArrayPool(2, 130, now::get, available::get);
        List<long[]> arrays = new ArrayList<>();
        for (int i = 0; i < 64; i++) {
            arrays.add(pool.borrow());
        }
        arrays.forEach(pool::release);
        available.set(0L);
        arrays.clear();
        for (int i = 0; i < 64; i++) {
            arrays.add(pool.borrow());
        }
        arrays.forEach(pool::release);
        assertEquals(2, pool.targetCount());
        assertEquals(2, pool.cachedCount());
        assertTrue(pool.dropCount() > 0L);

        available.set(1L << 40);
        now.set(2_000_000_000L);
        pool.reevaluate();
        assertEquals(2, pool.targetCount());
    }
}
