package me.cortex.voxy.forge;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicIntegerArray;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CPU differential evidence for the GLSL CAS reservation algorithm, tied to the formal
 * shader by source contracts. This does not stand in for executing the production GPU path.
 */
class Round10HocQueueSafetyTest {
    private static final int UNWRITTEN = 0x6b1d_4e29;

    @Test
    void firstDispatchOnlyAcceptsCountsThatFitTheAllocatedTopNodeQueue() {
        int capacity = GpuBufferLayout.HOC_QUEUE_CAPACITY;
        assertEquals(0, HierarchicalOcclusionTraverser.dispatchGroupsForCount(0));
        assertEquals(1, HierarchicalOcclusionTraverser.dispatchGroupsForCount(1));
        assertEquals(1, HierarchicalOcclusionTraverser.dispatchGroupsForCount(31));
        assertEquals(1, HierarchicalOcclusionTraverser.dispatchGroupsForCount(32));
        assertEquals(2, HierarchicalOcclusionTraverser.dispatchGroupsForCount(33));
        assertEquals((capacity - 1 + 31) / 32,
                HierarchicalOcclusionTraverser.dispatchGroupsForCount(capacity - 1));
        assertEquals((capacity + 31) / 32,
                HierarchicalOcclusionTraverser.dispatchGroupsForCount(capacity));
        assertThrows(IllegalArgumentException.class,
                () -> HierarchicalOcclusionTraverser.dispatchGroupsForCount(capacity + 1));
        assertThrows(IllegalArgumentException.class,
                () -> HierarchicalOcclusionTraverser.dispatchGroupsForCount(-1));
    }

    @Test
    void requestDownloadRetainsTheHardCapAndNeverCopiesAnIncompleteEntry() {
        long bytes = GpuBufferLayout.HOC_REQUEST_BUFFER_BYTES;
        assertEquals(408, bytes);
        for (int count : new int[]{0, 1, 49, 50, 51, 50_000}) {
            assertEquals(Math.min(count, 50), HierarchicalOcclusionTraverser.readableRequestCount(count, bytes));
        }
        assertEquals(0, HierarchicalOcclusionTraverser.readableRequestCount(1, 8));
        assertEquals(49, HierarchicalOcclusionTraverser.readableRequestCount(50, 8 + 49 * 8 + 7));
        assertEquals(-1, HierarchicalOcclusionTraverser.readableRequestCount(-1, bytes));
        assertEquals(-1, HierarchicalOcclusionTraverser.readableRequestCount(50_001, bytes));
        assertEquals(-1, HierarchicalOcclusionTraverser.readableRequestCount(0, 7));
    }

    @Test
    void everyIterationSnapshotsOnlyItsTwelveByteDispatchPrefixInsideTheOriginalMetadataAbi() {
        assertEquals(12, GpuBufferLayout.DISPATCH_BYTES);
        assertEquals(80, GpuBufferLayout.HOC_METADATA_BUFFER_BYTES);
        for (int iteration = 0; iteration < GpuBufferLayout.MAX_ITERATIONS; iteration++) {
            long offset = HierarchicalOcclusionTraverser.queueDispatchArgumentOffset(iteration);
            assertEquals(iteration * 16L, offset);
            assertTrue(offset + GpuBufferLayout.DISPATCH_BYTES <= GpuBufferLayout.HOC_METADATA_BUFFER_BYTES);
            assertEquals(offset + 12, offset + GpuBufferLayout.HOC_QUEUE_META_BYTES - Integer.BYTES,
                    "the successful item count remains outside the dispatch snapshot");
        }
        assertThrows(IllegalArgumentException.class, () -> HierarchicalOcclusionTraverser.queueDispatchArgumentOffset(-1));
        assertThrows(IllegalArgumentException.class,
                () -> HierarchicalOcclusionTraverser.queueDispatchArgumentOffset(GpuBufferLayout.MAX_ITERATIONS));
    }

    @Test
    void formalIndirectDispatchNeverAliasesTheShaderWritableMetadataObject() throws IOException {
        String owner = source("src/main/java/me/cortex/voxy/forge/HierarchicalOcclusionTraverser.java");
        String dispatch = function(owner, "static void dispatchQueuedIteration(int metadataBufferId, int snapshotBufferId, int iteration)");
        assertTrue(owner.contains("queueDispatchBuffer = new GlBuffer(GpuBufferLayout.DISPATCH_BYTES).zero()"));
        assertTrue(owner.contains("&& this.queueDispatchBuffer.id != 0"));
        assertTrue(owner.contains("this.queueDispatchBuffer.free();"));
        assertFalse(owner.contains("glBindBuffer(GL_DISPATCH_INDIRECT_BUFFER, this.queueMetaBuffer.id)"));
        assertTrue(owner.contains("dispatchQueuedIteration(this.queueMetaBuffer.id, this.queueDispatchBuffer.id, iteration)"));
        assertBefore(dispatch, "GL_BUFFER_UPDATE_BARRIER_BIT", "glCopyNamedBufferSubData(metadataBufferId, snapshotBufferId");
        assertBefore(dispatch, "queueDispatchArgumentOffset(iteration), 0L, GpuBufferLayout.DISPATCH_BYTES", "glDispatchComputeIndirect(0L)");
        assertBefore(dispatch, "glBindBuffer(GL_DISPATCH_INDIRECT_BUFFER, snapshotBufferId)", "glDispatchComputeIndirect(0L)");
        assertFalse(dispatch.contains("glGet"), "dispatch arguments remain GPU-side without synchronous readback");
    }

    @Test
    void request49And50And51PublishOnlyWrittenEntriesAndRetryRejectedNodes() {
        for (int attempts : new int[]{0, 49, 50, 51}) {
            QueueModel queue = new QueueModel(50, 0);
            boolean[] requested = new boolean[attempts];
            for (int i = 0; i < attempts; i++) {
                requested[i] = queue.append(1, i + 1) >= 0;
            }
            assertEquals(Math.min(50, attempts), queue.count.get());
            assertEquals(Math.max(0, attempts - 50), queue.rejected.get());
            queue.assertPublishedAndSentinels();
            if (attempts == 51) {
                assertFalse(requested[50], "a rejected request must not become permanently requested");
                QueueModel nextTraversal = new QueueModel(50, 0);
                requested[50] = nextTraversal.append(1, 51) >= 0;
                assertTrue(requested[50]);
                assertEquals(1, nextTraversal.count.get());
                nextTraversal.assertPublishedAndSentinels();
            }
        }
        // The intentional soft budget can be below the hard limit, including zero.
        QueueModel throttled = new QueueModel(0, 0);
        assertEquals(-1, throttled.append(1, 1));
        assertEquals(0, throttled.count.get());
        throttled.assertPublishedAndSentinels();
    }

    @Test
    void childBatchOfEightIsAllOrNothingAtEveryTailBoundary() {
        int capacity = GpuBufferLayout.HOC_QUEUE_CAPACITY;
        for (int remaining = 0; remaining <= 9; remaining++) {
            QueueModel queue = new QueueModel(capacity, capacity - remaining);
            int start = queue.appendChildren(3, 8, 1);
            assertEquals(remaining >= 8 ? capacity - remaining : -1, start);
            assertEquals(remaining >= 8 ? capacity - remaining + 8 : capacity - remaining, queue.count.get());
            assertTrue(queue.dispatchGroups.get() <= (capacity + 31) / 32);
            queue.assertPublishedAndSentinels();
        }
        QueueModel empty = new QueueModel(0, 0);
        assertEquals(0, empty.append(0, 1));
        assertEquals(-1, empty.append(1, 1));
        empty.assertPublishedAndSentinels();
    }

    @Test
    void finalAndInvalidIterationsCannotTouchTheNextMetadataRecord() {
        QueueModel queue = new QueueModel(32, 0);
        for (int queueIdx : new int[]{GpuBufferLayout.MAX_ITERATIONS - 1, GpuBufferLayout.MAX_ITERATIONS}) {
            assertEquals(-1, queue.appendChildren(queueIdx, 8, 1));
            assertEquals(0, queue.count.get());
            assertEquals(0, queue.dispatchGroups.get());
            queue.assertPublishedAndSentinels();
        }
        assertEquals(2, queue.finalIterationRejected.get());
        assertEquals(0, queue.appendChildren(GpuBufferLayout.MAX_ITERATIONS - 2, 8, 1));
        assertEquals(8, queue.count.get());
        assertEquals(1, queue.dispatchGroups.get());
        queue.assertPublishedAndSentinels();
    }

    @Test
    void successfulBatchCountsProduceTheExactRoundedUpIndirectDispatch() {
        QueueModel queue = new QueueModel(65, 0);
        for (int count = 0; count < 65; count++) {
            assertEquals(count, queue.append(1, count + 1));
            assertEquals((count + 1 + 31) / 32, queue.dispatchGroups.get());
        }
        assertEquals(-1, queue.append(1, 100));
        assertEquals(3, queue.dispatchGroups.get());
        queue.assertPublishedAndSentinels();
    }

    @Test
    void parallelReservationsAtTheTailNeverOverlapOrPublishPartialChildren() throws Exception {
        QueueModel queue = new QueueModel(257, 242);
        int workers = 32;
        ExecutorService executor = Executors.newFixedThreadPool(workers);
        CountDownLatch ready = new CountDownLatch(workers);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < workers; i++) {
                int marker = i + 1;
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    assertTrue(start.await(10, TimeUnit.SECONDS));
                    return queue.appendChildren(0, 8, marker);
                }));
            }
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();
            long successes = 0;
            for (Future<Integer> future : futures) {
                if (future.get(10, TimeUnit.SECONDS) >= 0) {
                    successes++;
                }
            }
            assertEquals(1, successes, "15 available slots cannot admit two 8-child lists");
            assertEquals(250, queue.count.get());
            assertEquals(workers - successes, queue.rejected.get());
            // The remaining 7 slots remain usable by independent smaller reservations.
            for (int i = 0; i < 7; i++) {
                assertEquals(250 + i, queue.append(1, 100 + i));
            }
            assertEquals(-1, queue.append(1, 200));
            queue.assertPublishedAndSentinels();
        } finally {
            start.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
        }
    }

    @Test
    void randomizedReservationStreamMatchesSerialBoundedAppendOracle() {
        Random random = new Random(0x10_cafe);
        for (int scenario = 0; scenario < 128; scenario++) {
            int capacity = random.nextInt(258);
            int initial = random.nextInt(capacity + 1);
            QueueModel queue = new QueueModel(capacity, initial);
            int expectedCount = initial;
            int expectedRejected = 0;
            for (int operation = 0; operation < 256; operation++) {
                int batch = random.nextInt(9);
                int expectedStart = batch <= capacity - expectedCount ? expectedCount : -1;
                int actualStart = queue.append(batch, operation + 1);
                assertEquals(expectedStart, actualStart,
                        "capacity=" + capacity + " count=" + expectedCount + " batch=" + batch);
                if (expectedStart >= 0) {
                    expectedCount += batch;
                } else {
                    expectedRejected++;
                }
                assertEquals(expectedCount, queue.count.get());
                assertEquals((expectedCount + 31) / 32, queue.dispatchGroups.get());
            }
            assertEquals(expectedRejected, queue.rejected.get());
            queue.assertPublishedAndSentinels();
        }
    }

    @Test
    void sourceConsumerClampsCorruptUnsignedCountsAndRoundedUpInvocations() {
        int capacity = GpuBufferLayout.HOC_QUEUE_CAPACITY;
        for (int rawCount : new int[]{0, capacity - 1, capacity, capacity + 1, -1}) {
            int validCount = (int) Math.min(Integer.toUnsignedLong(rawCount), capacity);
            assertFalse(sourceReadable(rawCount, capacity, 0, validCount));
            if (validCount > 0) {
                assertTrue(sourceReadable(rawCount, capacity, 0, validCount - 1));
            }
            assertFalse(sourceReadable(rawCount, capacity, GpuBufferLayout.MAX_ITERATIONS, 0));
        }
    }

    @Test
    void formalShadersUseCasCountsActualBufferBoundsAndUnconditionalFinalGuards() throws IOException {
        String queue = source("src/main/resources/assets/voxy/shaders/lod/hierarchical/queue.glsl");
        String traversal = source("src/main/resources/assets/voxy/shaders/lod/hierarchical/traversal_dev.comp");
        String current = function(queue, "uint getCurrentNode()");
        String reserve = function(queue, "bool pushNodesInit(uint nodeCount)");
        String push = function(queue, "void pushNode(uint nodeId)");
        String request = function(traversal, "void addRequest(inout UnpackedNode node)");
        String render = function(traversal, "void enqueueSelfForRender(in UnpackedNode node)");

        assertBefore(current, "queueIdx >= uint(MAX_ITERATIONS)", "nodeQueueMetadata[queueIdx].w");
        assertTrue(current.contains("min(nodeQueueMetadata[queueIdx].w, sourceCapacity)"));
        assertBefore(current, "validCount <= gl_GlobalInvocationID.x", "nodeQueueSource[gl_GlobalInvocationID.x]");
        assertBefore(reserve, "queueIdx >= uint(MAX_ITERATIONS - 1)", "nodeQueueMetadata[queueIdx + 1u]");
        assertTrue(reserve.contains("nodeCount > capacity || count > capacity - nodeCount"));
        assertTrue(reserve.contains("atomicCompSwap(nodeQueueMetadata[queueIdx + 1u].w, count, nextCount)"));
        assertTrue(reserve.contains("(nextCount + uint(LOCAL_SIZE - 1)) >> LOCAL_SIZE_BITS"));
        assertTrue(reserve.contains("atomicMax(nodeQueueMetadata[queueIdx + 1u].x, groups)"));
        assertFalse(reserve.contains("#ifdef DEBUG"));
        assertBefore(push, "nodePushRemaining == 0u", "nodeQueueSink[nodePushIndex++]");
        assertTrue(push.contains("nodePushIndex >= uint(nodeQueueSink.length())"));

        assertTrue(request.contains("min(requestQueueSize, min(uint(MAX_REQUEST_QUEUE_SIZE), uint(requestQueue.length())))"));
        assertTrue(request.contains("atomicCompSwap(requestQueueIndex.x, count, count + 1u)"));
        assertBefore(request, "if (previous == count)", "requestQueue[count] = getRawPos(node)");
        assertBefore(request, "requestQueue[count] = getRawPos(node)", "markRequested(node)");
        assertFalse(request.contains("atomicAdd(requestQueueIndex.x, 1)"));
        assertTrue(render.contains("min(renderQueueMaxSize, uint(renderQueue.length()))"));
        assertBefore(render, "if (!hasMesh(node))", "atomicCompSwap(renderQueueIndex, count, count + 1u)");
        assertTrue(render.contains("atomicCompSwap(renderQueueIndex, count, count + 1u)"));
        assertFalse(render.contains("atomicAdd(renderQueueIndex, 1)"));
        assertBefore(render, "if (previous == count)", "renderQueue[count] = getMesh(node)");
    }

    @Test
    void formalRecoveryKeepsTheOriginalParentRouteAndUsesOnlyTheExistingAsyncDownload() throws IOException {
        String owner = source("src/main/java/me/cortex/voxy/forge/HierarchicalOcclusionTraverser.java");
        String traversal = source("src/main/resources/assets/voxy/shaders/lod/hierarchical/traversal_dev.comp");
        String children = function(traversal, "bool enqueueChildren(in UnpackedNode node)");
        assertBefore(children, "children > nodeCapacity || ptr > nodeCapacity - children", "pushNodesInit(children)");
        assertTrue(traversal.contains("if (!enqueueChildren(node) && hasMesh(node))"));
        assertTrue(traversal.contains("enqueueSelfForRender(node);"));
        assertTrue(traversal.contains("nodeId >= uint(nodes.length()) || nodeId >= uint(lastRenderFrame.length())"));
        assertTrue(traversal.contains("node.lodLevel > uint(MAX_LOD)"));
        assertBefore(traversal, "uvec2 requestQueueIndex", "#import <voxy:lod/hierarchical/queue.glsl>");

        String reset = function(owner, "private void downloadResetRequestQueue()");
        assertBefore(reset, "DownloadStream.instance().download", "nglClearNamedBufferSubData");
        assertTrue(reset.contains("GpuBufferLayout.HOC_REQUEST_HEADER_BYTES"));
        assertFalse(owner.contains("glFinish"));
        assertFalse(owner.contains("glGetNamedBufferSubData"));
        assertTrue(owner.contains("MemoryUtil.memGetInt(ptr + Integer.BYTES)"));
        assertTrue(owner.contains("counters contain successful reservations only"));
        assertTrue(owner.contains("this.pendingRecoveryFlags = 0;"));
        assertTrue(owner.contains("this.recoveryTraversals++;"));
        assertTrue(owner.contains("GpuBufferLayout.HOC_METADATA_BUFFER_BYTES"));
    }

    private static boolean sourceReadable(int rawCount, int capacity, int queueIdx, int invocation) {
        return queueIdx < GpuBufferLayout.MAX_ITERATIONS
                && invocation >= 0
                && invocation < Math.min(Integer.toUnsignedLong(rawCount), capacity);
    }

    private static void assertBefore(String source, String first, String second) {
        int firstIndex = source.indexOf(first);
        int secondIndex = source.indexOf(second);
        assertTrue(firstIndex >= 0, "missing " + first);
        assertTrue(secondIndex > firstIndex, first + " must precede " + second);
    }

    private static String source(String path) throws IOException {
        return Files.readString(Path.of(path));
    }

    private static String function(String source, String signature) {
        int start = source.indexOf(signature);
        assertTrue(start >= 0, "missing " + signature);
        int brace = source.indexOf('{', start);
        int depth = 1;
        int end = brace + 1;
        while (depth != 0 && end < source.length()) {
            char character = source.charAt(end++);
            if (character == '{') depth++;
            if (character == '}') depth--;
        }
        assertEquals(0, depth, "unbalanced function " + signature);
        return source.substring(start, end);
    }

    private static final class QueueModel {
        final int capacity;
        final AtomicInteger count;
        final AtomicInteger dispatchGroups;
        final AtomicInteger rejected = new AtomicInteger();
        final AtomicInteger finalIterationRejected = new AtomicInteger();
        final AtomicIntegerArray memory;

        QueueModel(int capacity, int initialCount) {
            this.capacity = capacity;
            this.count = new AtomicInteger(initialCount);
            this.dispatchGroups = new AtomicInteger((initialCount + 31) / 32);
            this.memory = new AtomicIntegerArray(capacity + 2);
            for (int i = 0; i < this.memory.length(); i++) {
                this.memory.set(i, i < initialCount ? 0 : UNWRITTEN);
            }
        }

        int appendChildren(int queueIdx, int children, int marker) {
            if (queueIdx >= GpuBufferLayout.MAX_ITERATIONS - 1) {
                this.finalIterationRejected.incrementAndGet();
                return -1;
            }
            return this.append(children, marker);
        }

        int append(int amount, int marker) {
            int observed = this.count.get();
            for (;;) {
                // Unsigned comparison mirrors the shader even for corrupt 0xffffffff counts.
                if (amount > this.capacity || Integer.toUnsignedLong(observed) > this.capacity - amount) {
                    this.rejected.incrementAndGet();
                    return -1;
                }
                int nextCount = observed + amount;
                if (this.count.compareAndSet(observed, nextCount)) {
                    this.dispatchGroups.accumulateAndGet((nextCount + 31) / 32, Math::max);
                    for (int index = observed; index < nextCount; index++) {
                        assertTrue(this.memory.compareAndSet(index, UNWRITTEN, marker), "overlapping reservation at " + index);
                    }
                    return observed;
                }
                observed = this.count.get();
            }
        }

        void assertPublishedAndSentinels() {
            int published = this.count.get();
            assertTrue(published >= 0 && published <= this.capacity);
            for (int i = 0; i < this.memory.length(); i++) {
                if (i < published) {
                    assertTrue(this.memory.get(i) != UNWRITTEN, "published but unwritten slot " + i);
                } else {
                    assertEquals(UNWRITTEN, this.memory.get(i), "tail/sentinel was written at " + i);
                }
            }
        }
    }
}
