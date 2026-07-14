package me.cortex.voxy.forge;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class IdNotYetComputedExceptionTest {
    @Test
    void constructorPreservesOriginalNoStackNoSuppressionSemantics() {
        IdNotYetComputedException exception = new IdNotYetComputedException(37, true);

        assertEquals(37, exception.id);
        assertEquals(true, exception.isIdBlockId);
        assertNull(exception.getMessage());
        assertNull(exception.getCause());
        assertEquals(0, exception.getStackTrace().length);

        exception.addSuppressed(new IllegalStateException("must not be retained"));
        assertEquals(0, exception.getSuppressed().length);
        assertSame(exception, exception.fillInStackTrace());
        assertEquals(0, exception.getStackTrace().length);
    }
}
