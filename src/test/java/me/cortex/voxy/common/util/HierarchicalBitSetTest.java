package me.cortex.voxy.common.util;

import org.junit.jupiter.api.Test;

import java.util.BitSet;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HierarchicalBitSetTest {
    @Test
    void constructorAndQueriesRejectIndicesOutsideTheLogicalLimit() {
        assertThrows(IllegalArgumentException.class, () -> new HierarchicalBitSet(-1));
        HierarchicalBitSet bitSet = new HierarchicalBitSet(1);
        assertThrows(IndexOutOfBoundsException.class, () -> bitSet.isSet(-1));
        assertThrows(IndexOutOfBoundsException.class, () -> bitSet.isSet(1));
    }

    @Test
    void consecutiveAllocationCanExactlyFillLimitIncludingCount64() {
        HierarchicalBitSet bitSet = new HierarchicalBitSet(64);
        assertEquals(0, bitSet.allocateNextConsecutiveCounted(64));
        assertEquals(64, bitSet.getCount());
        assertEquals(63, bitSet.getMaxIndex());
        assertEquals(HierarchicalBitSet.LIMIT_REACHED, bitSet.allocateNextConsecutiveCounted(1));
        for (int i = 63; i >= 0; i--) {
            assertTrue(bitSet.free(i));
            assertEquals(i - 1, bitSet.getMaxIndex());
        }
        assertThrows(IllegalArgumentException.class, () -> bitSet.allocateNextConsecutiveCounted(0));
        assertThrows(IllegalArgumentException.class, () -> bitSet.allocateNextConsecutiveCounted(65));
    }

    @Test
    void randomizedOperationsMatchLowestFreeReferenceOrder() {
        int limit = 16_381;
        HierarchicalBitSet actual = new HierarchicalBitSet(limit);
        BitSet expected = new BitSet(limit);
        Random random = new Random(0xB175E7L);
        for (int operation = 0; operation < 50_000; operation++) {
            int action = random.nextInt(5);
            if (action == 0) {
                int expectedId = expected.nextClearBit(0);
                int actualId = actual.allocateNext();
                if (expectedId >= limit) {
                    assertEquals(HierarchicalBitSet.SET_FULL, actualId);
                } else {
                    assertEquals(expectedId, actualId);
                    expected.set(expectedId);
                }
            } else if (action == 1) {
                int count = random.nextInt(64) + 1;
                int expectedId = findFreeRun(expected, limit, count);
                int actualId = actual.allocateNextConsecutiveCounted(count);
                if (expected.cardinality() + count > limit) {
                    assertEquals(HierarchicalBitSet.LIMIT_REACHED, actualId);
                } else if (expectedId < 0) {
                    assertEquals(HierarchicalBitSet.SET_FULL, actualId);
                } else {
                    assertEquals(expectedId, actualId);
                    expected.set(expectedId, expectedId + count);
                }
            } else if (!expected.isEmpty()) {
                int ordinal = random.nextInt(expected.cardinality());
                int id = nthSetBit(expected, ordinal);
                assertTrue(actual.free(id));
                expected.clear(id);
                assertFalse(actual.free(id));
            }
            assertEquals(expected.cardinality(), actual.getCount());
            assertEquals(expected.length() - 1, actual.getMaxIndex());
        }
    }

    private static int findFreeRun(BitSet bits, int limit, int count) {
        int start = bits.nextClearBit(0);
        while (start + count <= limit) {
            int occupied = bits.nextSetBit(start);
            if (occupied < 0 || occupied >= start + count) {
                return start;
            }
            start = bits.nextClearBit(occupied + 1);
        }
        return -1;
    }

    private static int nthSetBit(BitSet bits, int ordinal) {
        int bit = bits.nextSetBit(0);
        while (ordinal-- > 0) {
            bit = bits.nextSetBit(bit + 1);
        }
        return bit;
    }
}
