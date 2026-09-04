package me.cortex.voxy.common.world;

import me.cortex.voxy.common.config.section.SectionSerializationStorage;
import me.cortex.voxy.common.config.section.SectionStorage;
import me.cortex.voxy.common.config.storage.inmemory.MemoryStorageBackend;
import me.cortex.voxy.common.world.other.Mapper;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;

import static org.junit.jupiter.api.Assertions.*;

/** Real tracker/dirty/save ownership with controlled saver scheduling, no production hook. */
class ActiveSectionDirtySaveConcurrencyTest {
    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void secondaryReuseAndTheFirstReferenceRemainInsideTheSameSliceLock() throws Exception {
        String source = Files.readString(Path.of("src/main/java/me/cortex/voxy/common/world/ActiveSectionTracker.java"));
        int remove = source.indexOf("section = this.secondaryCaches[index].remove(key);");
        int lock = source.lastIndexOf("lock.writeLock()", remove);
        int prime = source.indexOf("section.primeForReuse();", remove);
        int firstReference = source.indexOf("section.acquire(1);", prime);
        int unlock = source.indexOf("lock.unlockWrite(stamp);", remove);
        assertTrue(lock >= 0 && lock < remove);
        assertTrue(remove < prime && prime < firstReference && firstReference < unlock,
                "a historical unload callback must not enter between same-object reactivation and its first reference");
        assertTrue(source.substring(firstReference, unlock).contains("finally"));
    }

    @Test
    void dirtyNotificationAndRedirtyDuringSaveRetainReferencesUntilTheLatestWriteIsSaved() throws Exception {
        SectionSerializationStorage storage = new SectionSerializationStorage(new MemoryStorageBackend());
        WorldEngine engine = new WorldEngine(storage);
        long key = WorldEngine.getWorldSectionId(3, 116, 0, 117);
        CountDownLatch notificationEntered = new CountDownLatch(1);
        CountDownLatch finishNotification = new CountDownLatch(1);
        AtomicInteger notifications = new AtomicInteger();
        engine.setDirtyCallback((section, flags, neighbors) -> {
            assertFalse(section.isFreed());
            assertTrue(section.getRefCount() > 0, "the notifying caller must still own a section reference");
            if (notifications.getAndIncrement() == 0) {
                notificationEntered.countDown();
                await(finishNotification);
            }
        });
        ExecutorService writer = Executors.newSingleThreadExecutor();
        try (ControlledSaver saver = new ControlledSaver(engine, true)) {
            Future<WorldSection> firstWrite = writer.submit(() -> {
                WorldSection section = engine.acquire(key);
                try {
                    section._unsafeGetRawDataArray()[0] = Mapper.airWithLight(3);
                    engine.markDirty(section);
                    return section;
                } finally {
                    section.release();
                }
            });
            assertTrue(notificationEntered.await(10, TimeUnit.SECONDS));
            WorldSection duringNotification = engine.acquire(key);
            try {
                assertTrue(duringNotification.getRefCount() >= 2);
                assertFalse(duringNotification.isDirty, "WorldEngine notifies before setting dirty; the caller ref protects this window");
            } finally {
                duringNotification.release();
            }
            assertEquals(1, engine.getActiveSectionCount());
            finishNotification.countDown();
            WorldSection expected = firstWrite.get(10, TimeUnit.SECONDS);
            assertTrue(saver.firstSaveEntered.await(10, TimeUnit.SECONDS));
            assertSame(expected, saver.firstSection.get());
            assertFalse(expected.isFreed());
            assertEquals(1, expected.getRefCount(), "the saver owns the sole remaining reference");
            assertFalse(expected.isDirty);
            assertFalse(expected.inSaveQueue);

            WorldSection redirtied = engine.acquire(key);
            try {
                assertSame(expected, redirtied);
                redirtied._unsafeGetRawDataArray()[0] = Mapper.airWithLight(11);
                engine.markDirty(redirtied);
            } finally {
                redirtied.release();
            }
            assertTrue(expected.isDirty, "a new write after the saver cleared dirty must schedule a later save");
            assertEquals(1, expected.getRefCount());
            saver.continueFirstSave.countDown();
            saver.awaitSettled();
            assertTrue(saver.completed.get() >= 2, "redirty must not disappear inside the first save");
            assertEquals(0, engine.getActiveSectionCount());

            WorldSection reused = engine.acquire(key);
            try {
                assertSame(expected, reused, "this checks the secondary-cache object reactivation path");
                assertFalse(reused.isFreed());
                assertFalse(reused.isDirty || reused.inSaveQueue);
                assertEquals(1, reused.getRefCount());
                assertEquals(Mapper.airWithLight(11), reused._unsafeGetRawDataArray()[0]);
            } finally {
                reused.release();
            }
            assertPersisted(storage, key, Mapper.airWithLight(11));
            assertTrue(tracker(engine).getSecondaryCacheHits() > 0);
        } finally {
            finishNotification.countDown();
            writer.shutdownNow();
            assertTrue(writer.awaitTermination(10, TimeUnit.SECONDS));
            engine.free();
        }
    }

    @Test
    void concurrentReleaseReacquireDirtyAndSaveReuseLeavesNoPoisonedEntriesOrUnpersistedFinalWrites() throws Exception {
        SectionSerializationStorage storage = new SectionSerializationStorage(new MemoryStorageBackend());
        WorldEngine engine = new WorldEngine(storage);
        int keyCount = 16, workerCount = 8;
        long[] keys = new long[keyCount];
        for (int i = 0; i < keyCount; i++) keys[i] = WorldEngine.getWorldSectionId(3, 116 + i, 0, 117);
        engine.setDirtyCallback((section, flags, neighbors) -> {
            assertFalse(section.isFreed());
            assertTrue(section.getRefCount() > 0);
        });
        ExecutorService workers = Executors.newFixedThreadPool(workerCount);
        CountDownLatch start = new CountDownLatch(1);
        try (ControlledSaver saver = new ControlledSaver(engine, false)) {
            // Seed real secondary entries so all workers exercise reactivation, not only loads.
            for (long key : keys) engine.acquire(key).release();
            List<Future<?>> work = new ArrayList<>();
            for (int worker = 0; worker < workerCount; worker++) {
                int workerId = worker;
                work.add(workers.submit(() -> {
                    await(start);
                    for (int iteration = 0; iteration < 2_000; iteration++) {
                        long key = keys[(workerId + iteration * 7) % keyCount];
                        WorldSection section = engine.acquire(key);
                        try {
                            assertFalse(section.isFreed());
                            assertTrue(section.getRefCount() > 0);
                            section._unsafeGetRawDataArray()[0] = Mapper.airWithLight((workerId + iteration) & 15);
                            engine.markDirty(section);
                            if ((iteration & 7) == 0) {
                                WorldSection nested = engine.acquire(key);
                                try {
                                    assertSame(section, nested);
                                } finally {
                                    nested.release();
                                }
                            }
                        } finally {
                            section.release();
                        }
                    }
                }));
            }
            start.countDown();
            for (Future<?> future : work) future.get(30, TimeUnit.SECONDS);

            // A last single-writer update supplies unambiguous persistence truth per key.
            for (int i = 0; i < keyCount; i++) {
                WorldSection section = engine.acquire(keys[i]);
                try {
                    section._unsafeGetRawDataArray()[0] = Mapper.airWithLight(i);
                    engine.markDirty(section);
                } finally {
                    section.release();
                }
            }
            saver.awaitSettled();
            assertEquals(0, engine.getActiveSectionCount());
            assertEquals(0, saver.pending.get());
            assertNull(saver.failure.get());
            for (int i = 0; i < keyCount; i++) {
                WorldSection section = engine.acquire(keys[i]);
                try {
                    assertFalse(section.isFreed());
                    assertFalse(section.isDirty || section.inSaveQueue);
                    assertEquals(1, section.getRefCount(), "no leaked acquire after saver and workers drained");
                    assertEquals(Mapper.airWithLight(i), section._unsafeGetRawDataArray()[0]);
                } finally {
                    section.release();
                }
                assertPersisted(storage, keys[i], Mapper.airWithLight(i));
            }
            assertEquals(0, engine.getActiveSectionCount());
            assertTrue(tracker(engine).getSecondaryCacheHits() >= keyCount);
        } finally {
            start.countDown();
            workers.shutdownNow();
            assertTrue(workers.awaitTermination(10, TimeUnit.SECONDS));
            engine.free();
        }
    }

    private static ActiveSectionTracker tracker(WorldEngine engine) throws ReflectiveOperationException {
        Field field = WorldEngine.class.getDeclaredField("sectionTracker");
        field.setAccessible(true);
        return (ActiveSectionTracker) field.get(engine);
    }

    private static void assertPersisted(SectionStorage storage, long key, long expected) {
        WorldSection reloaded = WorldSection._createRawUntrackedUnsafeSection(
                WorldEngine.getLevel(key), WorldEngine.getX(key), WorldEngine.getY(key), WorldEngine.getZ(key));
        try {
            assertEquals(SectionStorage.LOAD_OK, storage.loadSection(reloaded));
            assertEquals(expected, reloaded._unsafeGetRawDataArray()[0]);
        } finally {
            reloaded._releaseArray();
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            assertTrue(latch.await(10, TimeUnit.SECONDS), "controlled section lifecycle schedule timed out");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError(exception);
        }
    }

    static final class ControlledSaver implements AutoCloseable {
        final CountDownLatch firstSaveEntered = new CountDownLatch(1);
        final CountDownLatch continueFirstSave = new CountDownLatch(1);
        final AtomicReference<WorldSection> firstSection = new AtomicReference<>();
        final AtomicReference<Throwable> failure = new AtomicReference<>();
        final AtomicInteger pending = new AtomicInteger();
        final AtomicInteger completed = new AtomicInteger();
        private final AtomicInteger started = new AtomicInteger();
        private final ExecutorService worker;
        private final WorldEngine engine;
        private final boolean pauseFirst;

        ControlledSaver(WorldEngine engine, boolean pauseFirst) {
            this.engine = engine;
            this.pauseFirst = pauseFirst;
            this.worker = Executors.newFixedThreadPool(pauseFirst ? 1 : 4);
            engine.setSaveCallback(this::enqueue);
        }

        private boolean enqueue(WorldEngine world, WorldSection section, boolean nonBlocking, boolean alreadyAcquired) {
            if (!section.exchangeIsInSaveQueue(true)) return false;
            if (!alreadyAcquired) section.acquire();
            this.pending.incrementAndGet();
            this.worker.execute(() -> {
                try {
                    // Same ordering/reference transfer as SectionSavingService.processJob.
                    assertFalse(section.isFreed());
                    assertTrue(section.getRefCount() > 0);
                    section.setNotDirty();
                    if (section.exchangeIsInSaveQueue(false)) {
                        if (this.started.getAndIncrement() == 0 && this.pauseFirst) {
                            this.firstSection.set(section);
                            this.firstSaveEntered.countDown();
                            await(this.continueFirstSave);
                        }
                        world.storage.saveSection(section);
                        this.completed.incrementAndGet();
                    }
                } catch (Throwable throwable) {
                    this.failure.compareAndSet(null, throwable);
                } finally {
                    try {
                        section.release();
                    } catch (Throwable throwable) {
                        this.failure.compareAndSet(null, throwable);
                    } finally {
                        this.pending.decrementAndGet();
                    }
                }
            });
            return true;
        }

        void awaitSettled() {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
            while (this.pending.get() != 0 || this.engine.getActiveSectionCount() != 0) {
                assertNull(this.failure.get(), "save/release thread failed");
                assertTrue(System.nanoTime() < deadline, "section references/save work did not drain");
                LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(1));
            }
            assertNull(this.failure.get());
        }

        @Override
        public void close() throws InterruptedException {
            this.continueFirstSave.countDown();
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
            while (this.pending.get() != 0 && System.nanoTime() < deadline) {
                LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(1));
            }
            this.worker.shutdown();
            if (!this.worker.awaitTermination(10, TimeUnit.SECONDS)) {
                this.worker.shutdownNow();
                assertTrue(this.worker.awaitTermination(10, TimeUnit.SECONDS));
            }
            assertEquals(0, this.pending.get());
        }
    }
}
