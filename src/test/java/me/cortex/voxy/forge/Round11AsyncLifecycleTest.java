package me.cortex.voxy.forge;

import com.mojang.blaze3d.systems.RenderSystem;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import me.cortex.voxy.common.util.MemoryBuffer;
import me.cortex.voxy.common.world.WorldEngine;
import me.cortex.voxy.common.world.WorldSection;
import org.junit.jupiter.api.Test;
import org.lwjgl.system.MemoryUtil;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/** Exercises the production CPU worker and publication objects, without constructing a renderer. */
class Round11AsyncLifecycleTest {
    @Test
    void requestWorkIsReservedBeforeQueuePublication() throws Exception {
        try (Fixture fixture = new Fixture()) {
            assertReservedPublication(fixture, "requestBatchQueue", () ->
                    fixture.manager.submitRequestBatch(new MemoryBuffer(8).zero()));
        }
    }

    @Test
    void cleanerWorkIsReservedBeforeQueuePublication() throws Exception {
        try (Fixture fixture = new Fixture()) {
            assertReservedPublication(fixture, "removeBatchQueue", () ->
                    fixture.manager.submitRemoveBatch(new MemoryBuffer(GpuBufferLayout.CLEANER_POSITION_BYTES).zero()));
        }
    }

    @Test
    void geometryWorkIsReservedBeforeQueuePublication() throws Exception {
        try (Fixture fixture = new Fixture()) {
            assertReservedPublication(fixture, "geometryUpdateQueue", () ->
                    fixture.manager.submitGeometryResult(BuiltSection.empty(0)));
        }
    }

    @Test
    void failedGeometryQueueAdmissionReleasesInputAndRollsBackReservation() throws Exception {
        try (Fixture fixture = new Fixture()) {
            IllegalStateException injected = new IllegalStateException("queue admission rejected");
            ConcurrentLinkedDeque<BuiltSection> rejecting = new ConcurrentLinkedDeque<>() {
                @Override
                public boolean add(BuiltSection section) {
                    throw injected;
                }
            };
            set(fixture.manager, "geometryUpdateQueue", rejecting);
            MemoryBuffer vertices = new MemoryBuffer(8);
            MemoryBuffer occupancy = new MemoryBuffer(8);
            BuiltSection section = new BuiltSection(0, (byte) 0, 0, vertices, new int[8], occupancy);
            assertSame(injected, assertThrows(IllegalStateException.class,
                    () -> fixture.manager.submitGeometryResult(section)));
            assertTrue(vertices.isFreed());
            assertTrue(occupancy.isFreed());
            assertEquals(0, fixture.work().get());
        }
    }

    @Test
    void negativeRequestCountIsRejectedAndOwnedBatchIsReleased() throws Exception {
        assertInvalidBatch(8, -1);
    }

    @Test
    void missingRequestHeaderIsRejectedBeforeReadingPayload() throws Exception {
        // Four allocated bytes are safe for the historical count read; the full eight-byte ABI
        // header is absent. No intentionally invalid native pointer is passed to the old code.
        assertInvalidBatch(4, 0);
    }

    @Test
    void overCapacityRequestBatchIsRejectedEvenWhenMemoryIsLargeEnough() throws Exception {
        assertInvalidBatch(8 + 51 * 8, 51);
    }

    @Test
    void truncatedRequestPayloadIsReleasedAndAccountedOnFailure() throws Exception {
        assertInvalidBatch(8, 1);
    }

    @Test
    void stopAfterWorkerFailureStillDrainsAllAcceptedInputs() throws Exception {
        try (Fixture fixture = new Fixture()) {
            MemoryBuffer request = new MemoryBuffer(8).zero();
            fixture.manager.submitRequestBatch(request);
            set(fixture.manager, "uncaughtException", new IllegalStateException("injected worker failure"));
            set(fixture.manager, "running", false);
            assertDoesNotThrow(fixture.manager::stop);
            assertTrue(request.isFreed());
            assertFalse(fixture.manager.hasWork());
        }
    }

    @Test
    void normalStopClearsTheWorkLedgerAndRejectsLateBuffers() throws Exception {
        try (Fixture fixture = new Fixture()) {
            fixture.manager.submitRequestBatch(new MemoryBuffer(8).zero());
            fixture.manager.stop();
            MemoryBuffer late = new MemoryBuffer(8).zero();
            fixture.manager.submitRequestBatch(late);
            assertTrue(late.isFreed());
            assertFalse(fixture.manager.hasWork());
            assertThrows(IllegalStateException.class, fixture.manager::stop);
        }
    }

    @Test
    void emptyBatchesConsumeExactlyOneWorkItemEach() throws Exception {
        try (Fixture fixture = new Fixture()) {
            for (int i = 0; i < 50; i++) {
                fixture.manager.submitRequestBatch(new MemoryBuffer(8).zero());
            }
            invoke(fixture.manager, "workerRun");
            assertEquals(0, fixture.work().get());
            assertTrue(((ConcurrentLinkedDeque<?>) get(fixture.manager, "requestBatchQueue")).isEmpty());
            assertNotNull(((AtomicReference<?>) get(fixture.manager, "results")).get());
        }
    }

    @Test
    void geometryCountBudgetLeavesUnconsumedWorkReserved() throws Exception {
        try (Fixture fixture = new Fixture()) {
            for (int i = 0; i < 700; i++) {
                fixture.manager.submitGeometryResult(BuiltSection.empty(i));
            }
            invoke(fixture.manager, "workerRun");
            assertEquals(400, fixture.work().get());
            assertEquals(400, ((ConcurrentLinkedDeque<?>) get(fixture.manager, "geometryUpdateQueue")).size());
            invoke(fixture.manager, "workerRun");
            assertEquals(100, fixture.work().get());
            invoke(fixture.manager, "workerRun");
            assertEquals(0, fixture.work().get());
        }
    }

    @Test
    void geometryByteBudgetDoesNotConsumeOrReleaseTheTailEarly() throws Exception {
        try (Fixture fixture = new Fixture()) {
            List<MemoryBuffer> buffers = new ArrayList<>();
            for (int index = 0; index < 12; index++) {
                MemoryBuffer buffer = new MemoryBuffer(128L << 10);
                buffers.add(buffer);
                fixture.manager.submitGeometryResult(new BuiltSection(index, (byte) 0, 0,
                        buffer, new int[8], null));
            }
            invoke(fixture.manager, "workerRun");
            assertEquals(4, fixture.work().get());
            assertEquals(4, ((ConcurrentLinkedDeque<?>) get(fixture.manager, "geometryUpdateQueue")).size());
            assertEquals(8, buffers.stream().filter(MemoryBuffer::isFreed).count());
            fixture.manager.stop();
            assertTrue(buffers.stream().allMatch(MemoryBuffer::isFreed));
        }
    }

    @Test
    void failedPublicationRetainsResultOwnershipAndDoesNotLeaveFreedUploadEntries() throws Exception {
        try (Fixture fixture = new Fixture()) {
            BasicAsyncGeometryManager geometry = (BasicAsyncGeometryManager) get(fixture.manager, "geometryManager");
            Int2ObjectOpenHashMap<MemoryBuffer> uploads = geometry.getUploads();
            uploads.put(0, new MemoryBuffer(8).zero());
            uploads.put(8, new MemoryBuffer(8).zero());
            int[] order = uploads.keySet().toIntArray();
            uploads.put(order[1], new MemoryBuffer(7).zero()).free();
            try {
                assertThrows(IllegalStateException.class, () -> invoke(fixture.manager, "publishSyncResults"));
                assertTrue(uploads.values().stream().noneMatch(MemoryBuffer::isFreed),
                        "geometry owner must not retain entries already consumed by publication");
                int retained = 0;
                for (String field : List.of("results", "resultCache1", "resultCache2")) {
                    if (((AtomicReference<?>) get(fixture.manager, field)).get() != null) {
                        retained++;
                    }
                }
                assertEquals(2, retained, "an unpublished result must remain owned for shutdown");
            } finally {
                // Baseline merge leaves freed entries behind; avoid a second free during cleanup.
                uploads.values().removeIf(MemoryBuffer::isFreed);
            }
        }
    }

    @Test
    void sameHeapPointerShrinkMergesNewGeometryAndMetadataAsOnePacket() throws Exception {
        try (Fixture fixture = new Fixture()) {
            BasicAsyncGeometryManager geometry = (BasicAsyncGeometryManager) get(fixture.manager, "geometryManager");
            int id = geometry.uploadSection(new BuiltSection(12, (byte) 0, 0,
                    new MemoryBuffer(2048).zero(), new int[8], null));
            try {
                int point = geometry.getUploads().keySet().iterator().nextInt();
                invoke(fixture.manager, "publishSyncResults");
                Object first = ((AtomicReference<?>) get(fixture.manager, "results")).get();
                MemoryBuffer replacement = new MemoryBuffer(64);
                for (int element = 0; element < 8; element++) {
                    MemoryUtil.memPutLong(replacement.address + element * 8L, 0x110000L + element);
                }
                assertEquals(id, geometry.uploadReplaceSection(id,
                        new BuiltSection(12, (byte) 0, 0, replacement, new int[8], null)));
                assertTrue(geometry.getUploads().containsKey(point));
                assertTrue(geometry.getHeapRemovals().isEmpty());
                invoke(fixture.manager, "publishSyncResults");
                Object merged = ((AtomicReference<?>) get(fixture.manager, "results")).get();
                assertSame(first, merged, "unconsumed packet is merged, not independently published");
                Object copy = get(merged, "geometryUpload");
                Int2IntOpenHashMap points = (Int2IntOpenHashMap) get(copy, "dataUploadPoints");
                assertEquals(1, points.size());
                MemoryBuffer headers = (MemoryBuffer) get(copy, "scratchHeaderBuffer");
                long header = headers.address + points.get(point) * 16L;
                assertEquals(8, MemoryUtil.memGetInt(header + 8));
                MemoryBuffer data = (MemoryBuffer) get(copy, "scratchDataBuffer");
                long source = data.address + MemoryUtil.memGetInt(header) * 8L;
                for (int element = 0; element < 8; element++) {
                    assertEquals(0x110000L + element, MemoryUtil.memGetLong(source + element * 8L));
                }
                Int2IntOpenHashMap scatter = (Int2IntOpenHashMap) get(merged, "scatterWriteLocationMap");
                MemoryBuffer metadata = (MemoryBuffer) get(merged, "scatterWriteBuffer");
                long secondHalf = metadata.address + scatter.get((id << 1) | Integer.MIN_VALUE | 1) * 16L;
                assertEquals(8, MemoryUtil.memGetInt(secondHalf + 12) >>> 16);
                assertEquals(1, get(merged, "geometrySectionCount"));
                assertEquals(1024L, get(merged, "usedGeometry"));
            } finally {
                geometry.removeSection(id);
            }
        }
    }

    @Test
    void staleChildNotificationReleasesItsWorldSectionReference() throws Exception {
        try (Fixture fixture = new Fixture()) {
            WorldSection section = worldSection();
            section.acquire();
            SectionUpdateRouter router = (SectionUpdateRouter) get(fixture.manager, "router");
            try {
                router.watch(section.key, WorldEngine.UPDATE_TYPE_CHILD_EXISTENCE_BIT);
                router.forwardEvent(section, WorldEngine.UPDATE_TYPE_CHILD_EXISTENCE_BIT);
                assertEquals(2, section.getRefCount());
                router.unwatch(section.key, WorldEngine.UPDATE_TYPE_CHILD_EXISTENCE_BIT);
                invoke(fixture.manager, "workerRun");
                assertEquals(1, section.getRefCount());
                assertEquals(0, fixture.work().get());
            } finally {
                section.release();
            }
        }
    }

    @Test
    void childTransitionExceptionStillReleasesReferenceAndConsumedWork() throws Exception {
        try (Fixture fixture = new Fixture()) {
            WorldSection section = worldSection();
            section.acquire();
            SectionUpdateRouter router = (SectionUpdateRouter) get(fixture.manager, "router");
            NodeManager nodes = (NodeManager) get(fixture.manager, "nodeManager");
            Long2IntOpenHashMap map = (Long2IntOpenHashMap) get(nodes, "activeSectionMap");
            try {
                router.watch(section.key, WorldEngine.UPDATE_TYPE_CHILD_EXISTENCE_BIT);
                router.forwardEvent(section, WorldEngine.UPDATE_TYPE_CHILD_EXISTENCE_BIT);
                // Inject an invalid single-request owner to make the real transition fail;
                // remove it below, rather than teaching production to ignore corrupted owners.
                map.put(section.key, 0b10 << 30);
                assertThrows(RuntimeException.class, () -> invoke(fixture.manager, "workerRun"));
                assertEquals(1, section.getRefCount());
                assertEquals(0, fixture.work().get());
            } finally {
                map.remove(section.key);
                router.unwatch(section.key, WorldEngine.UPDATE_TYPE_CHILD_EXISTENCE_BIT);
                section.release();
            }
        }
    }

    @Test
    void concurrentProducersAndStopReleaseAcceptedAndRejectedBuffersExactlyOnce() throws Exception {
        try (Fixture fixture = new Fixture()) {
            ConcurrentLinkedDeque<MemoryBuffer> allocated = new ConcurrentLinkedDeque<>();
            AtomicReference<Throwable> failure = new AtomicReference<>();
            CountDownLatch begin = new CountDownLatch(1);
            CountDownLatch submitted = new CountDownLatch(32);
            List<Thread> producers = new ArrayList<>();
            for (int producer = 0; producer < 4; producer++) {
                Thread thread = new Thread(() -> {
                    try {
                        assertTrue(begin.await(10, TimeUnit.SECONDS));
                        for (int index = 0; index < 1_000; index++) {
                            MemoryBuffer batch = new MemoryBuffer(8).zero();
                            allocated.add(batch);
                            fixture.manager.submitRequestBatch(batch);
                            submitted.countDown();
                        }
                    } catch (Throwable throwable) {
                        failure.compareAndSet(null, throwable);
                    }
                }, "Round11 queue producer " + producer);
                producers.add(thread);
                thread.start();
            }
            fixture.manager.start();
            begin.countDown();
            try {
                assertTrue(submitted.await(10, TimeUnit.SECONDS));
                fixture.manager.stop();
            } finally {
                for (Thread producer : producers) {
                    producer.join(10_000);
                    assertFalse(producer.isAlive());
                }
            }
            assertNull(failure.get());
            assertEquals(4_000, allocated.size());
            assertTrue(allocated.stream().allMatch(MemoryBuffer::isFreed));
            assertEquals(0, fixture.work().get());
            assertFalse(fixture.manager.hasWork());
        }
    }

    @Test
    void truncatedCleanerBatchFailsBeforeNativePositionReadsAndReleasesInput() throws Exception {
        try (Fixture fixture = new Fixture()) {
            MemoryBuffer batch = new MemoryBuffer(8).zero();
            fixture.manager.submitRemoveBatch(batch);
            assertThrows(IllegalStateException.class, () -> invoke(fixture.manager, "workerRun"));
            assertTrue(batch.isFreed());
            assertEquals(0, fixture.work().get());
        }
    }

    private static WorldSection worldSection() throws Exception {
        Class<?> tracker = Class.forName("me.cortex.voxy.common.world.WorldSection$ReleaseTracker");
        Constructor<WorldSection> constructor = WorldSection.class.getDeclaredConstructor(
                int.class, int.class, int.class, int.class, tracker);
        constructor.setAccessible(true);
        return constructor.newInstance(4, 10, 0, 10, null);
    }

    private static void assertInvalidBatch(int bytes, int count) throws Exception {
        try (Fixture fixture = new Fixture()) {
            MemoryBuffer batch = new MemoryBuffer(bytes).zero();
            MemoryUtil.memPutInt(batch.address, count);
            fixture.manager.submitRequestBatch(batch);
            try {
                assertThrows(IllegalStateException.class, () -> invoke(fixture.manager, "workerRun"));
                assertTrue(batch.isFreed(), "consumed invalid batch must be freed");
                assertEquals(0, fixture.work().get(), "consumed invalid work is not queued work");
            } finally {
                // Allows the deliberately-red baseline to finish without retaining native memory.
                if (!batch.isFreed()) {
                    batch.free();
                }
            }
        }
    }

    private static void assertReservedPublication(Fixture fixture, String queueName, Runnable submit) throws Exception {
        AtomicInteger visibleWork = new AtomicInteger(-1);
        AtomicReference<Boolean> holdsAcceptanceLock = new AtomicReference<>();
        Object acceptanceLock = get(fixture.manager, "topLevelNodeLock");
        ConcurrentLinkedDeque<Object> queue = new ConcurrentLinkedDeque<>() {
            @Override
            public boolean add(Object value) {
                boolean added = super.add(value);
                // This callback is the precise legal schedule in which the consumer sees the
                // element before ConcurrentLinkedDeque.add returns to its producer.
                visibleWork.set(fixture.work().get());
                holdsAcceptanceLock.set(Thread.holdsLock(acceptanceLock));
                return added;
            }
        };
        set(fixture.manager, queueName, queue);
        submit.run();
        assertEquals(1, visibleWork.get(), "published event must already own one work reservation");
        assertEquals(Boolean.TRUE, holdsAcceptanceLock.get(), "stop and publication must share acceptance lock");
    }

    private static final class Fixture implements AutoCloseable {
        final AsyncNodeManager manager;
        final Thread previousRenderThread;
        final List<Object> resultObjects = new ArrayList<>();

        Fixture() throws Exception {
            Field renderThread = RenderSystem.class.getDeclaredField("renderThread");
            renderThread.setAccessible(true);
            previousRenderThread = (Thread) renderThread.get(null);
            renderThread.set(null, Thread.currentThread());
            BasicSectionGeometryData data = new BasicSectionGeometryData(128);
            set(data, "geometryCapacityBytes", 128L << 20);
            // The service is an external producer dependency. Its constructor starts service-
            // manager wiring, which is unrelated to these input/worker ownership schedules.
            // The real AsyncNodeManager constructor only installs its two callbacks here.
            Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
            unsafeField.setAccessible(true);
            Unsafe unsafe = (Unsafe) unsafeField.get(null);
            RenderGenerationService service = (RenderGenerationService) unsafe.allocateInstance(RenderGenerationService.class);
            manager = new AsyncNodeManager(128, data, service);
            resultObjects.add(((AtomicReference<?>) get(manager, "resultCache1")).get());
            resultObjects.add(((AtomicReference<?>) get(manager, "resultCache2")).get());
        }

        AtomicInteger work() {
            try {
                return (AtomicInteger) get(manager, "workCounter");
            } catch (Exception e) {
                throw new AssertionError(e);
            }
        }

        @Override
        public void close() throws Exception {
            try {
                boolean alreadyFreed = resultObjects.stream().allMatch(Fixture::freed);
                if (!alreadyFreed) {
                    // Reset only the failed baseline's acceptance flag so its old stop can clean.
                    set(manager, "running", true);
                    manager.stop();
                }
            } finally {
                for (Object result : resultObjects) {
                    if (!freed(result)) {
                        invoke(result, "free");
                    }
                }
                Field field = RenderSystem.class.getDeclaredField("renderThread");
                field.setAccessible(true);
                field.set(null, previousRenderThread);
            }
        }

        private static boolean freed(Object result) {
            try {
                return ((MemoryBuffer) get(result, "scatterWriteBuffer")).isFreed();
            } catch (Exception e) {
                throw new AssertionError(e);
            }
        }
    }

    private static Object get(Object object, String fieldName) throws Exception {
        Field field = object.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(object);
    }

    private static void set(Object object, String fieldName, Object value) throws Exception {
        Field field = object.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(object, value);
    }

    private static Object invoke(Object object, String methodName) throws Exception {
        Method method = object.getClass().getDeclaredMethod(methodName);
        method.setAccessible(true);
        try {
            return method.invoke(object);
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof Exception exception) {
                throw exception;
            }
            throw (Error) e.getCause();
        }
    }
}
