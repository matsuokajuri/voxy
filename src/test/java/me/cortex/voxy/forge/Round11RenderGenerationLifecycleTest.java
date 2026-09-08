package me.cortex.voxy.forge;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import me.cortex.voxy.common.config.section.SectionSerializationStorage;
import me.cortex.voxy.common.config.storage.inmemory.MemoryStorageBackend;
import me.cortex.voxy.common.thread.Service;
import me.cortex.voxy.common.thread.ServiceManager;
import me.cortex.voxy.common.util.MemoryBuffer;
import me.cortex.voxy.common.util.Pair;
import me.cortex.voxy.common.world.WorldEngine;
import me.cortex.voxy.common.world.WorldSection;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

/** Real queue, Service permits, WorldEngine and WorldSection ownership; only mesh production is controlled. */
class Round11RenderGenerationLifecycleTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void queuedDirtyRevisionsCoalesceWithoutAdditionalTasksOrPermits() throws Exception {
        try (Fixture fixture = new Fixture()) {
            long position = fixture.holdSection().key;
            fixture.router.watch(position, WorldEngine.UPDATE_TYPE_BLOCK_BIT);
            long originalToken = fixture.router.getGeometryToken(position);
            for (int revision = 0; revision < 200; revision++) {
                fixture.cache.invalidate(position);
                fixture.router.triggerRemesh(position);
            }
            long latest = fixture.router.getGeometryToken(position);
            fixture.generator.enqueueTask(position, originalToken);
            assertEquals(1, fixture.generator.getTaskCount());
            assertEquals(1, fixture.service.numJobs());
            assertEquals(1, fixture.services.released.get());
            fixture.runDirect(section -> mesh(section.key));
            BuiltSection result = fixture.results.getFirst();
            assertEquals(latest, result.watchToken);
            assertEquals(fixture.cache.snapshotEpoch(position), result.cacheEpoch);
            assertEquals(0, fixture.generator.getTaskCount());
            assertEquals(0, fixture.service.numJobs());
        }
    }

    @Test
    void cancelledQueuedTokenDoesNotAcquireOrGenerateASection() throws Exception {
        try (Fixture fixture = new Fixture()) {
            WorldSection section = fixture.holdSection();
            fixture.router.watch(section.key, WorldEngine.UPDATE_TYPE_BLOCK_BIT);
            fixture.router.unwatch(section.key, WorldEngine.UPDATE_TYPE_BLOCK_BIT);
            fixture.runDirect(ignored -> { fail("cancelled task generated a mesh"); return null; });
            assertEquals(1, section.getRefCount());
            assertTrue(fixture.results.isEmpty());
            assertEquals(0, fixture.generator.getTaskCount());
        }
    }

    @Test
    void inFlightOldResultRetainsItsIdentityWhileNewRevisionRemainsQueued() throws Exception {
        try (Fixture fixture = new Fixture()) {
            long position = fixture.holdSection().key;
            fixture.router.watch(position, WorldEngine.UPDATE_TYPE_BLOCK_BIT);
            long oldToken = fixture.router.getGeometryToken(position);
            fixture.runDirect(section -> {
                fixture.cache.invalidate(position);
                fixture.router.triggerRemesh(position);
                return mesh(position);
            });
            BuiltSection old = fixture.results.getFirst();
            assertEquals(oldToken, old.watchToken, "in-flight old data must never be stamped as the new request");
            assertFalse(fixture.router.isCurrentGeometry(position, old.watchToken));
            assertNotEquals(fixture.cache.snapshotEpoch(position), old.cacheEpoch);
            assertEquals(1, fixture.generator.getTaskCount());
            fixture.runDirect(section -> mesh(position));
            BuiltSection current = fixture.results.getLast();
            assertTrue(fixture.router.isCurrentGeometry(position, current.watchToken));
            assertEquals(fixture.cache.snapshotEpoch(position), current.cacheEpoch);
        }
    }

    @Test
    void generationFailureReleasesClaimedWorldSectionAndRemovesTask() throws Exception {
        try (Fixture fixture = new Fixture()) {
            WorldSection section = fixture.holdSection();
            fixture.router.watch(section.key, WorldEngine.UPDATE_TYPE_BLOCK_BIT);
            IllegalArgumentException failure = new IllegalArgumentException("injected generator failure");
            assertSame(failure, assertThrows(IllegalArgumentException.class,
                    () -> fixture.runDirect(ignored -> { throw failure; })));
            assertEquals(1, section.getRefCount());
            assertEquals(0, fixture.generator.getTaskCount());
            assertEquals(0, fixture.holding());
            assertTrue(fixture.results.isEmpty());
            // A failed task no longer monopolizes its position; the next genuine edit can enqueue.
            fixture.router.triggerRemesh(section.key);
            assertEquals(1, fixture.generator.getTaskCount());
        }
    }

    @Test
    void failedServiceSubmissionRollsBackQueueMapAndPermit() throws Exception {
        try (Fixture fixture = new Fixture()) {
            long position = fixture.holdSection().key;
            IllegalStateException failure = new IllegalStateException("injected service submission failure");
            fixture.services.onRelease.set(() -> { throw failure; });
            assertSame(failure, assertThrows(IllegalStateException.class,
                    () -> fixture.router.watch(position, WorldEngine.UPDATE_TYPE_BLOCK_BIT)));
            assertEquals(0, fixture.generator.getTaskCount());
            assertEquals(0, fixture.service.numJobs());
            fixture.services.onRelease.set(null);
            fixture.router.triggerRemesh(position);
            assertEquals(1, fixture.generator.getTaskCount());
            fixture.runDirect(section -> mesh(position));
        }
    }

    @Test
    void failedRetrySubmissionReturnsReferenceToTheExecutingJobFinally() throws Exception {
        try (Fixture fixture = new Fixture()) {
            WorldSection section = fixture.holdSection();
            fixture.router.watch(section.key, WorldEngine.UPDATE_TYPE_BLOCK_BIT);
            IllegalStateException failure = new IllegalStateException("injected retry submission failure");
            fixture.services.onRelease.set(() -> { throw failure; });
            assertSame(failure, assertThrows(IllegalStateException.class,
                    () -> fixture.runDirect(ignored -> { throw missingModel(); })));
            assertEquals(1, section.getRefCount());
            assertEquals(0, fixture.holding());
            assertEquals(0, fixture.generator.getTaskCount());
            assertEquals(0, fixture.service.numJobs());
            fixture.services.onRelease.set(null);
        }
    }

    @Test
    void missingModelRetryTransfersOneReferenceAndNewRevisionDetachesItOnce() throws Exception {
        try (Fixture fixture = new Fixture()) {
            WorldSection section = fixture.holdSection();
            fixture.router.watch(section.key, WorldEngine.UPDATE_TYPE_BLOCK_BIT);
            fixture.runDirect(ignored -> { throw missingModel(); });
            assertEquals(2, section.getRefCount());
            assertEquals(1, fixture.holding());
            assertEquals(1, fixture.generator.getTaskCount());
            fixture.cache.invalidate(section.key);
            fixture.router.triggerRemesh(section.key);
            assertEquals(1, section.getRefCount());
            assertEquals(0, fixture.holding());
            assertEquals(1, fixture.generator.getTaskCount());
            assertEquals(1, fixture.service.numJobs());
            fixture.runDirect(ignored -> mesh(section.key));
            assertEquals(1, section.getRefCount());
        }
    }

    @Test
    void repeatedMissingModelRetriesDoNotAccumulateReferences() throws Exception {
        try (Fixture fixture = new Fixture()) {
            WorldSection section = fixture.holdSection();
            fixture.router.watch(section.key, WorldEngine.UPDATE_TYPE_BLOCK_BIT);
            for (int retry = 0; retry < 100; retry++) {
                fixture.runDirect(ignored -> { throw missingModel(); });
                assertEquals(2, section.getRefCount(), "retry=" + retry);
                assertEquals(1, fixture.holding());
                assertEquals(1, fixture.generator.getTaskCount());
                assertEquals(1, fixture.service.numJobs());
            }
            fixture.generator.shutdown();
            assertEquals(1, section.getRefCount());
            assertEquals(0, fixture.holding());
            assertEquals(0, fixture.generator.getTaskCount());
        }
    }

    @Test
    void newerTaskQueuedDuringMissingModelFailureKeepsItsOwnRevision() throws Exception {
        try (Fixture fixture = new Fixture()) {
            WorldSection section = fixture.holdSection();
            fixture.router.watch(section.key, WorldEngine.UPDATE_TYPE_BLOCK_BIT);
            fixture.runDirect(ignored -> {
                fixture.cache.invalidate(section.key);
                fixture.router.triggerRemesh(section.key);
                throw missingModel();
            });
            assertEquals(1, section.getRefCount());
            assertEquals(0, fixture.holding());
            assertEquals(1, fixture.generator.getTaskCount());
            fixture.runDirect(ignored -> mesh(section.key));
            assertTrue(fixture.router.isCurrentGeometry(section.key, fixture.results.getFirst().watchToken));
        }
    }

    @Test
    void retryReferenceTransferIsLatchedBeforeConcurrentCoalescerCanDetachIt() throws Exception {
        try (Fixture fixture = new Fixture()) {
            WorldSection section = fixture.holdSection();
            fixture.router.watch(section.key, WorldEngine.UPDATE_TYPE_BLOCK_BIT);
            CountDownLatch retryPublication = new CountDownLatch(1);
            CountDownLatch dirtyTokenPublished = new CountDownLatch(1);
            AtomicReference<Throwable> failure = new AtomicReference<>();
            fixture.onDirty.set(dirtyTokenPublished::countDown);
            fixture.services.onRelease.set(() -> {
                retryPublication.countDown();
                await(dirtyTokenPublished);
            });
            Thread coalescer = new Thread(() -> {
                try {
                    await(retryPublication);
                    fixture.cache.invalidate(section.key);
                    fixture.router.triggerRemesh(section.key);
                } catch (Throwable throwable) {
                    failure.set(throwable);
                }
            }, "Round11 missing-model coalescer");
            coalescer.start();
            try {
                fixture.runDirect(ignored -> { throw missingModel(); });
            } finally {
                coalescer.join(10_000);
                fixture.services.onRelease.set(null);
                fixture.onDirty.set(null);
            }
            assertFalse(coalescer.isAlive());
            assertNull(failure.get());
            assertEquals(1, section.getRefCount());
            assertEquals(0, fixture.holding());
            assertEquals(1, fixture.generator.getTaskCount());
        }
    }

    @Test
    void callbackOwnsMeshEvenWhenItFreesThenThrows() throws Exception {
        try (Fixture fixture = new Fixture()) {
            WorldSection section = fixture.holdSection();
            AtomicReference<BuiltSection> delivered = new AtomicReference<>();
            IllegalStateException failure = new IllegalStateException("consumer failure");
            fixture.generator.setResultConsumer(mesh -> {
                delivered.set(mesh);
                mesh.free();
                throw failure;
            });
            fixture.router.watch(section.key, WorldEngine.UPDATE_TYPE_BLOCK_BIT);
            assertSame(failure, assertThrows(IllegalStateException.class,
                    () -> fixture.runDirect(ignored -> mesh(section.key))));
            assertTrue(delivered.get().isReleased());
            assertTrue(delivered.get().geometryBuffer.isFreed());
            assertEquals(1, section.getRefCount());
        }
    }

    @Test
    void callbackMayTransferLiveMeshBeforeThrowingWithoutProducerFreeingIt() throws Exception {
        try (Fixture fixture = new Fixture()) {
            long position = fixture.holdSection().key;
            IllegalStateException failure = new IllegalStateException("consumer threw after queue transfer");
            fixture.generator.setResultConsumer(result -> {
                fixture.results.add(result);
                throw failure;
            });
            fixture.router.watch(position, WorldEngine.UPDATE_TYPE_BLOCK_BIT);
            assertSame(failure, assertThrows(IllegalStateException.class,
                    () -> fixture.runDirect(section -> mesh(position))));
            assertFalse(fixture.results.getFirst().isReleased());
            assertFalse(fixture.results.getFirst().geometryBuffer.isFreed());
        }
    }

    @Test
    void absentResultConsumerReclaimsGeneratedMesh() throws Exception {
        try (Fixture fixture = new Fixture()) {
            long position = fixture.holdSection().key;
            fixture.generator.setResultConsumer(null);
            BuiltSection result = mesh(position);
            fixture.router.watch(position, WorldEngine.UPDATE_TYPE_BLOCK_BIT);
            fixture.runDirect(section -> result);
            assertTrue(result.isReleased());
            assertTrue(result.geometryBuffer.isFreed());
        }
    }

    @Test
    void shutdownWaitsForClaimedServiceJobAndRejectsConcurrentEnqueue() throws Exception {
        try (Fixture fixture = new Fixture()) {
            WorldSection section = fixture.holdSection();
            fixture.router.watch(section.key, WorldEngine.UPDATE_TYPE_BLOCK_BIT);
            CountDownLatch entered = new CountDownLatch(1);
            CountDownLatch finish = new CountDownLatch(1);
            AtomicReference<Throwable> failure = new AtomicReference<>();
            fixture.services.mesh.set(ignored -> {
                entered.countDown();
                await(finish);
                throw missingModel();
            });
            Thread worker = new Thread(() -> {
                try { fixture.services.tryRunAJob(); }
                catch (Throwable throwable) { failure.compareAndSet(null, throwable); }
            }, "Round11 claimed generator job");
            Thread shutdown = new Thread(() -> {
                try { fixture.generator.shutdown(); }
                catch (Throwable throwable) { failure.compareAndSet(null, throwable); }
            }, "Round11 generator shutdown");
            worker.start();
            await(entered);
            shutdown.start();
            try {
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
                while (fixture.service.isLive() && System.nanoTime() < deadline) Thread.onSpinWait();
                assertFalse(fixture.service.isLive());
                assertTrue(shutdown.isAlive(), "shutdown must await the claimed running job");
                fixture.router.triggerRemesh(section.key);
                assertEquals(0, fixture.generator.getTaskCount());
            } finally {
                finish.countDown();
                worker.join(10_000);
                shutdown.join(10_000);
            }
            assertFalse(worker.isAlive());
            assertFalse(shutdown.isAlive());
            assertNull(failure.get());
            assertEquals(1, section.getRefCount());
            assertEquals(0, fixture.holding());
            assertEquals(0, fixture.generator.getTaskCount());
            fixture.generator.enqueueTask(section.key, fixture.router.getGeometryToken(section.key));
            assertEquals(0, fixture.generator.getTaskCount());
        }
    }

    private static IdNotYetComputedException missingModel() {
        IdNotYetComputedException missing = new IdNotYetComputedException(0, false);
        // A legitimate already-computed-inner/empty-outer frontier; no GL/model baking is needed
        // to exercise the generation retry's queue and WorldSection ownership contract.
        missing.auxData = new long[0];
        missing.auxBitMsk = 0;
        return missing;
    }

    private static BuiltSection mesh(long position) {
        return new BuiltSection(position, (byte) 0, 0, new MemoryBuffer(64).zero(), new int[8], null);
    }

    private static void await(CountDownLatch latch) {
        try {
            assertTrue(latch.await(10, TimeUnit.SECONDS), "timed out waiting for deterministic schedule");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError(e);
        }
    }

    private static final class Fixture implements AutoCloseable {
        final WorldEngine world = new WorldEngine(new SectionSerializationStorage(new MemoryStorageBackend()));
        final GeometryCache cache = new GeometryCache(1 << 20);
        final ControlledServices services = new ControlledServices();
        final SectionUpdateRouter router = new SectionUpdateRouter();
        final AtomicReference<Runnable> onDirty = new AtomicReference<>();
        final ConcurrentLinkedDeque<BuiltSection> results = new ConcurrentLinkedDeque<>();
        final List<WorldSection> held = new ArrayList<>();
        final RenderGenerationService generator;
        final Service service;

        Fixture() throws Exception {
            Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
            unsafeField.setAccessible(true);
            Unsafe unsafe = (Unsafe) unsafeField.get(null);
            // ModelBakery is only a GL-facing external dependency. The actual mesh callback is
            // supplied through production MeshGenerator; no second generation state machine exists.
            ModelBakerySubsystem bakery = (ModelBakerySubsystem) unsafe.allocateInstance(ModelBakerySubsystem.class);
            generator = new RenderGenerationService(world, bakery, services, false);
            services.target.set(generator);
            service = (Service) get(generator, "service");
            generator.setGeometryCache(cache);
            generator.setTokenValidator(router::isCurrentGeometry);
            generator.setResultConsumer(results::add);
            router.setVersionedCallbacks(generator::enqueueTask, (position, token) -> {
                Runnable hook = onDirty.get();
                if (hook != null) hook.run();
                generator.enqueueTask(position, token);
            }, (section, token) -> {});
        }

        WorldSection holdSection() {
            WorldSection section = world.acquire(4, 1 + held.size(), 0, 2);
            java.util.Arrays.fill(section._unsafeGetRawDataArray(), 0L);
            // A loader miss is not existing voxel data until a real update materializes it.
            // Follow WorldUpdater's production publication contract instead of bypassing it.
            world.markDirty(section, WorldEngine.UPDATE_TYPE_BLOCK_BIT | WorldEngine.UPDATE_TYPE_DONT_SAVE, 0);
            held.add(section);
            return section;
        }

        void runDirect(RenderGenerationService.MeshGenerator mesh) {
            // Retract the real Service permit before driving its existing CPU job seam directly.
            // This variant keeps exception identity observable; the real Service executor's
            // configured exception handler reports RuntimeExceptions rather than rethrowing them.
            assertTrue(service.steal());
            generator.processJob(mesh, new IntOpenHashSet());
        }

        int holding() throws Exception {
            return ((AtomicInteger) get(generator, "holdingSectionCount")).get();
        }

        @Override
        public void close() {
            services.onRelease.set(null);
            onDirty.set(null);
            try {
                if (service.isLive()) generator.shutdown();
                services.shutdown();
            } finally {
                for (BuiltSection mesh : results) if (!mesh.isReleased()) mesh.free();
                for (WorldSection section : held) section.release();
                cache.free();
                world.free();
            }
        }
    }

    private static final class ControlledServices extends ServiceManager {
        final AtomicInteger released = new AtomicInteger();
        final AtomicReference<Runnable> onRelease = new AtomicReference<>();
        final AtomicReference<RenderGenerationService> target = new AtomicReference<>();
        final AtomicReference<RenderGenerationService.MeshGenerator> mesh = new AtomicReference<>();

        ControlledServices() { this(new AtomicReference<ControlledServices>()); }

        private ControlledServices(AtomicReference<ControlledServices> self) {
            super(count -> {
                ControlledServices value = self.get();
                value.released.addAndGet(count);
                Runnable hook = value.onRelease.get();
                if (hook != null) hook.run();
            }, count -> {});
            self.set(this);
        }

        @Override
        public Service createService(Supplier<Pair<Runnable, Runnable>> factory, long weight, String name) {
            return super.createService(() -> new Pair<>(
                    () -> target.get().processJob(mesh.get(), new IntOpenHashSet()), () -> {}), weight, name);
        }
    }

    private static Object get(Object object, String fieldName) throws Exception {
        Field field = object.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(object);
    }
}
