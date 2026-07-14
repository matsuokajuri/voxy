package me.cortex.voxy.common.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TrackedObjectFlagTest {
    @Test
    void originalVerificationFlagKeepsExactDefaultAndCaseSemantics() {
        assertTrue(TrackedObject.resolveTrackObjectAllocations(null));
        assertTrue(TrackedObject.resolveTrackObjectAllocations("true"));
        assertFalse(TrackedObject.resolveTrackObjectAllocations("false"));
        assertFalse(TrackedObject.resolveTrackObjectAllocations("TRUE"));
    }
}
