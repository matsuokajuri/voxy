package me.cortex.voxy.common.world;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Deterministic final-claim contracts, without timing sleeps or a production test hook. */
class WorldSectionFreeClaimRegressionTest {
    @Test
    void dirtyZeroReferenceClaimMustRemainLiveInsteadOfPoisoningTheActiveEntry() {
        WorldSection section = WorldSection._createRawUntrackedUnsafeSection(3, 116, 0, 117);
        try {
            section.markDirty();
            assertEquals(0, section.getRefCount());
            assertClaimDeclinedWithoutMutation(section);
            assertTrue(section.isDirty);
            assertTrue(section.shouldSave());
            assertTrue(section.tryAcquire(), "a declined free claim must remain reacquirable");
            section.setNotDirty();
            section.release(false, 0);
            assertTrue(section.trySetFreed(), "the same object can be freed once saving clears dirty state");
        } finally {
            section._releaseArray();
        }
    }

    @Test
    void queuedZeroReferenceClaimMustRemainLiveInsteadOfPoisoningTheActiveEntry() {
        WorldSection section = WorldSection._createRawUntrackedUnsafeSection(3, 116, 0, 117);
        try {
            assertTrue(section.exchangeIsInSaveQueue(true));
            assertEquals(0, section.getRefCount());
            assertClaimDeclinedWithoutMutation(section);
            assertTrue(section.inSaveQueue);
            assertTrue(section.tryAcquire(), "the save owner must still be able to retain a declined claim");
            assertTrue(section.exchangeIsInSaveQueue(false));
            section.release(false, 0);
            assertTrue(section.trySetFreed());
        } finally {
            section._releaseArray();
        }
    }

    @Test
    void failedAcquireOnFreedSectionMustNotManufactureReferences() {
        for (int count : new int[]{1, 3}) {
            WorldSection section = WorldSection._createRawUntrackedUnsafeSection(3, 116, 0, 117);
            try {
                assertTrue(section.trySetFreed());
                assertThrows(IllegalStateException.class, () -> section.acquire(count));
                assertAll("failed acquire is an observation, not a state mutation",
                        () -> assertTrue(section.isFreed()),
                        () -> assertEquals(0, section.getRefCount()),
                        () -> assertEquals(0, (int) WorldSection.ATOMIC_STATE_HANDLE.get(section)),
                        () -> assertFalse(section.tryAcquire()));
            } finally {
                section._releaseArray();
            }
        }
    }

    @Test
    void cleanZeroReferenceClaimStillSucceedsAndPositiveReferenceClaimDoesNot() {
        WorldSection section = WorldSection._createRawUntrackedUnsafeSection(3, 116, 0, 117);
        try {
            section.acquire();
            assertFalse(section.trySetFreed());
            assertEquals(1, section.getRefCount());
            section.release(false, 0);
            assertTrue(section.trySetFreed());
            assertTrue(section.isFreed());
        } finally {
            section._releaseArray();
        }
    }

    private static void assertClaimDeclinedWithoutMutation(WorldSection section) {
        boolean claimed = false;
        Throwable failure = null;
        try {
            claimed = section.trySetFreed();
        } catch (Throwable throwable) {
            failure = throwable;
        }
        boolean actualClaimed = claimed;
        Throwable actualFailure = failure;
        assertAll("a racing dirty/queued transition must abort the claim, never leave state=0 in an active holder",
                () -> assertNull(actualFailure, "free claim must not throw after first setting the live bit to zero"),
                () -> assertFalse(actualClaimed),
                () -> assertFalse(section.isFreed()),
                () -> assertEquals(0, section.getRefCount()),
                () -> assertEquals(1, (int) WorldSection.ATOMIC_STATE_HANDLE.get(section)));
    }
}
