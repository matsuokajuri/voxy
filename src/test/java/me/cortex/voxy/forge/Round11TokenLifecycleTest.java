package me.cortex.voxy.forge;

import me.cortex.voxy.common.world.WorldEngine;
import me.cortex.voxy.common.world.WorldSection;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class Round11TokenLifecycleTest {
    @Test
    void duplicateWatchPreservesTokenButDirtyEventAndRewatchAdvanceIt() {
        SectionUpdateRouter router = new SectionUpdateRouter();
        List<Long> initial = new ArrayList<>(), dirty = new ArrayList<>();
        router.setVersionedCallbacks((position, token) -> initial.add(token),
                (position, token) -> dirty.add(token), (section, token) -> {});
        assertFalse(router.isCurrentGeometry(12, 0));
        assertTrue(router.watch(12, WorldEngine.UPDATE_TYPE_BLOCK_BIT));
        long original = initial.get(0);
        assertFalse(router.watch(12, WorldEngine.UPDATE_TYPE_BLOCK_BIT));
        assertEquals(1, initial.size());
        assertTrue(router.isCurrentGeometry(12, original));
        router.triggerRemesh(12);
        long revised = dirty.get(0);
        assertTrue(revised > original);
        assertFalse(router.isCurrentGeometry(12, original));
        assertTrue(router.isCurrentGeometry(12, revised));
        assertTrue(router.unwatch(12, WorldEngine.UPDATE_TYPE_BLOCK_BIT));
        assertFalse(router.isCurrentGeometry(12, revised));
        router.watch(12, WorldEngine.UPDATE_TYPE_BLOCK_BIT);
        assertTrue(initial.get(1) > revised);
    }

    @Test
    void childLifetimeSurvivesGeometryEditsAndPartialUnwatch() throws Exception {
        WorldSection section = section();
        section.acquire();
        try {
            SectionUpdateRouter router = new SectionUpdateRouter();
            List<Long> children = new ArrayList<>();
            router.setVersionedCallbacks((position, token) -> {}, (position, token) -> {},
                    (changed, token) -> children.add(token));
            router.watch(section.key, WorldEngine.DEFAULT_UPDATE_FLAGS);
            router.forwardEvent(section, WorldEngine.DEFAULT_UPDATE_FLAGS);
            long child = children.get(0);
            long geometry = router.getGeometryToken(section.key);
            router.forwardEvent(section, WorldEngine.DEFAULT_UPDATE_FLAGS);
            assertEquals(child, children.get(1));
            assertTrue(router.isCurrentChild(section.key, child));
            assertFalse(router.isCurrentGeometry(section.key, geometry));
            assertFalse(router.unwatch(section.key, WorldEngine.UPDATE_TYPE_BLOCK_BIT));
            assertTrue(router.isCurrentChild(section.key, child));
            assertEquals(0, router.getGeometryToken(section.key));
            assertTrue(router.unwatch(section.key, WorldEngine.UPDATE_TYPE_CHILD_EXISTENCE_BIT));
            assertFalse(router.isCurrentChild(section.key, child));
            router.watch(section.key, WorldEngine.UPDATE_TYPE_CHILD_EXISTENCE_BIT);
            router.forwardEvent(section, WorldEngine.UPDATE_TYPE_CHILD_EXISTENCE_BIT);
            assertNotEquals(child, children.get(children.size() - 1));
        } finally {
            section.release();
        }
    }

    @Test
    void initialCallbackOvertakenByUnwatchRewatchCannotRepresentNewOwner() throws Exception {
        SectionUpdateRouter router = new SectionUpdateRouter();
        CountDownLatch firstCaptured = new CountDownLatch(1), allowFirst = new CountDownLatch(1);
        AtomicInteger calls = new AtomicInteger();
        ConcurrentLinkedDeque<Long> deliveries = new ConcurrentLinkedDeque<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        router.setVersionedCallbacks((position, token) -> {
            if (calls.getAndIncrement() == 0) {
                firstCaptured.countDown();
                await(allowFirst);
            }
            deliveries.add(token);
        }, (position, token) -> {}, (section, token) -> {});
        Thread first = new Thread(() -> {
            try { router.watch(12, WorldEngine.UPDATE_TYPE_BLOCK_BIT); }
            catch (Throwable throwable) { failure.set(throwable); }
        }, "Round11 delayed initial callback");
        first.start();
        await(firstCaptured);
        try {
            router.unwatch(12, WorldEngine.UPDATE_TYPE_BLOCK_BIT);
            router.watch(12, WorldEngine.UPDATE_TYPE_BLOCK_BIT);
        } finally {
            allowFirst.countDown();
            first.join(10_000);
        }
        assertFalse(first.isAlive());
        assertNull(failure.get());
        assertEquals(2, deliveries.size());
        assertTrue(router.isCurrentGeometry(12, deliveries.getFirst()));
        assertFalse(router.isCurrentGeometry(12, deliveries.getLast()));
    }

    @Test
    void reorderedDirtyCallbacksHaveStrictlyIdentifiableCurrentRevision() throws Exception {
        SectionUpdateRouter router = new SectionUpdateRouter();
        CountDownLatch olderCaptured = new CountDownLatch(1), allowOlder = new CountDownLatch(1);
        AtomicInteger calls = new AtomicInteger();
        ConcurrentLinkedDeque<Long> deliveries = new ConcurrentLinkedDeque<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        router.setVersionedCallbacks((position, token) -> {}, (position, token) -> {
            if (calls.getAndIncrement() == 0) {
                olderCaptured.countDown();
                await(allowOlder);
            }
            deliveries.add(token);
        }, (section, token) -> {});
        router.watch(12, WorldEngine.UPDATE_TYPE_BLOCK_BIT);
        Thread older = new Thread(() -> {
            try { router.triggerRemesh(12); }
            catch (Throwable throwable) { failure.set(throwable); }
        }, "Round11 delayed dirty callback");
        older.start();
        await(olderCaptured);
        try {
            router.triggerRemesh(12);
        } finally {
            allowOlder.countDown();
            older.join(10_000);
        }
        assertFalse(older.isAlive());
        assertNull(failure.get());
        assertEquals(2, deliveries.size());
        assertTrue(router.isCurrentGeometry(12, deliveries.getFirst()));
        assertFalse(router.isCurrentGeometry(12, deliveries.getLast()));
    }

    @Test
    void builtSectionClonePreservesContentEpochAndDeliveryTokenButNotOwnershipState() {
        BuiltSection original = BuiltSection.empty(12).withCacheEpoch(55).withWatchToken(89);
        BuiltSection clone = original.clone();
        try {
            original.free();
            assertTrue(original.isReleased());
            assertFalse(clone.isReleased());
            assertEquals(55, clone.cacheEpoch);
            assertEquals(89, clone.watchToken);
            assertThrows(IllegalStateException.class, () -> original.withWatchToken(90));
            assertThrows(IllegalStateException.class, original::clone);
            assertThrows(IllegalStateException.class, original::free);
        } finally {
            clone.free();
        }
    }

    private static WorldSection section() throws Exception {
        Class<?> tracker = Class.forName("me.cortex.voxy.common.world.WorldSection$ReleaseTracker");
        Constructor<WorldSection> constructor = WorldSection.class.getDeclaredConstructor(
                int.class, int.class, int.class, int.class, tracker);
        constructor.setAccessible(true);
        return constructor.newInstance(4, 1, 0, 2, null);
    }

    private static void await(CountDownLatch latch) {
        try {
            assertTrue(latch.await(10, TimeUnit.SECONDS));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError(e);
        }
    }
}
