package me.cortex.voxy.forge;

import me.cortex.voxy.common.world.WorldEngine;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.concurrent.locks.StampedLock;

import static org.junit.jupiter.api.Assertions.*;

class Round11RouterLifecycleTest {
    @Test
    void rejectedUnwatchDoesNotStrandTheRouterStripeLock() throws Exception {
        SectionUpdateRouter router = new SectionUpdateRouter();
        assertThrows(IllegalStateException.class, () -> router.unwatch(0, WorldEngine.DEFAULT_UPDATE_FLAGS));
        Field field = SectionUpdateRouter.class.getDeclaredField("locks");
        field.setAccessible(true);
        for (StampedLock lock : (StampedLock[]) field.get(router)) {
            long stamp = lock.tryWriteLock();
            assertNotEquals(0, stamp, "rejected unwatch retained a read/write stamp");
            lock.unlockWrite(stamp);
        }
    }

    @Test
    void callbackFailureDoesNotStrandTheRouterStripeLock() throws Exception {
        SectionUpdateRouter router = new SectionUpdateRouter();
        router.setCallbacks(position -> { throw new IllegalStateException("callback"); }, position -> {}, section -> {});
        assertThrows(IllegalStateException.class, () -> router.watch(0, WorldEngine.DEFAULT_UPDATE_FLAGS));
        Field field = SectionUpdateRouter.class.getDeclaredField("locks");
        field.setAccessible(true);
        for (StampedLock lock : (StampedLock[]) field.get(router)) {
            long stamp = lock.tryWriteLock();
            assertNotEquals(0, stamp);
            lock.unlockWrite(stamp);
        }
    }
}
