package me.cortex.voxy.forge;

import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ForgeIngestRetryQueueTest {
    @Test
    void unrelatedSectionSuccessCannotCancelAnotherSectionsPendingChunkRetry() {
        var pendingChunks = new ArrayDeque<Long>();
        var queuedChunks = new HashSet<Long>();
        long key = 0x1234_5678_9ABC_DEF0L;

        ForgeIngestRetryQueue.updateDeferredRetryState(pendingChunks, queuedChunks, key, true);
        assertEquals(1, pendingChunks.size());
        assertTrue(queuedChunks.contains(key));

        ForgeIngestRetryQueue.updateDeferredRetryState(pendingChunks, queuedChunks, key, false);
        assertEquals(1, pendingChunks.size());
        assertTrue(queuedChunks.contains(key));

        ForgeIngestRetryQueue.updateDeferredRetryState(pendingChunks, queuedChunks, key, true);
        assertEquals(1, pendingChunks.size());
        assertEquals(key, pendingChunks.getFirst());
        assertEquals(1, queuedChunks.size());
        assertTrue(queuedChunks.contains(key));

        assertEquals(key, ForgeIngestRetryQueue.pollDeferredRetryState(pendingChunks, queuedChunks));
        assertTrue(pendingChunks.isEmpty());
        assertTrue(queuedChunks.isEmpty());
    }

    @Test
    void trustedFullChunkCompletionCancelsDeferredRetryWithoutDequeScan() {
        var pendingChunks = new ArrayDeque<Long>();
        var queuedChunks = new HashSet<Long>();
        long key = 0x1234_5678_9ABC_DEF0L;

        ForgeIngestRetryQueue.updateDeferredRetryState(pendingChunks, queuedChunks, key, true);
        ForgeIngestRetryQueue.cancelDeferredRetryState(queuedChunks, key);

        assertFalse(queuedChunks.contains(key));
        assertEquals(Long.MIN_VALUE,
                ForgeIngestRetryQueue.pollDeferredRetryState(pendingChunks, queuedChunks));
        assertTrue(pendingChunks.isEmpty());
    }
}
