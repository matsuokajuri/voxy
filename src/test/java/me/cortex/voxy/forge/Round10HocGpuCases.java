package me.cortex.voxy.forge;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.opengl.GL46C.*;

/** Test-only small-buffer execution of the unchanged formal HOC helper functions. */
final class Round10HocGpuCases {
    private static final int SENTINEL = 0x61bd_792e;
    private static final int GUARD_INTS = 4;
    private static final int INPUT_COUNT_LOCATION = 20;
    private static final int BATCH_LOCATION = 21;

    static void runAll() {
        String source = formalSource(null);
        compileFullTraversal(source);
        compileFullTraversal(formalSource("vec2 getTAA() { return vec2(0.0); }"));
        executeChildReservations(source);
        executeIndirectIterationChain(source);
        executeSourceReads(source);
        executeRequestReservations(source);
        executeRenderReservations(source);
        assertEquals(GL_NO_ERROR, glGetError());
        System.out.println("ROUND10_HOC_GPU=full-traversal-and-TAA-compile; child-CAS/batch8/final-iteration; independent-indirect-empty/nonempty-chain; "
                + "source-clamp; requests-0/49/50/51+retry; render-count/empty/missing; all-guards-intact");
    }

    private static void executeIndirectIterationChain(String source) {
        final int capacity = 65;
        String wrapped = wrap(source, """
                layout(location=21) uniform uint testBranching;
                layout(std430,binding=0) buffer ChainOutput {
                    uint visits[MAX_ITERATIONS];
                    uint observed[];
                };
                void main() {
                    uint nodeId=getCurrentNode();
                    if (nodeId==SENTINAL_OUT_OF_BOUNDS) return;
                    atomicAdd(visits[queueIdx],1u);
                    observed[queueIdx*65u+gl_GlobalInvocationID.x]=nodeId;
                    if (queueIdx<uint(MAX_ITERATIONS-1) && pushNodesInit(testBranching)) {
                        for (uint child=0; child<testBranching; child++) pushNode(nodeId*testBranching+child);
                    }
                }
                """);
        int program = Round10GpuExecutionTest.computeProgram(wrapped);
        try (GuardedBuffer request = new GuardedBuffer(new int[2]);
             GuardedBuffer meta = new GuardedBuffer(metadata());
             GuardedBuffer snapshot = new GuardedBuffer(new int[GpuBufferLayout.DISPATCH_BYTES / Integer.BYTES]);
             GuardedBuffer top = new GuardedBuffer(filled(capacity, SENTINEL));
             GuardedBuffer scratchA = new GuardedBuffer(filled(capacity, SENTINEL));
             GuardedBuffer scratchB = new GuardedBuffer(filled(capacity, SENTINEL));
             GuardedBuffer output = new GuardedBuffer(filled(GpuBufferLayout.MAX_ITERATIONS * (capacity + 1), SENTINEL))) {
            // Reuse the exact same GL objects, alternating zero work and nonzero work.
            // The branching=2 frame has distinct counts/dispatch arguments at offsets
            // 16/32/48/64, so copying the wrong record cannot accidentally pass.
            for (int[] frame : new int[][]{{0, 1}, {33, 1}, {0, 1}, {1, 1}, {65, 1}, {0, 2}, {4, 2}, {0, 1}, {31, 1}, {32, 1}, {33, 1}}) {
                int roots = frame[0], branching = frame[1];
                int[] metadata = metadata();
                metadata[0] = (roots + 31) / 32;
                metadata[3] = roots;
                meta.replace(metadata);
                request.replace(new int[2]);
                int[] topIds = filled(capacity, SENTINEL);
                for (int i = 0; i < roots; i++) topIds[i] = i + 1;
                top.replace(topIds);
                scratchA.replace(filled(capacity, SENTINEL));
                scratchB.replace(filled(capacity, SENTINEL));
                int[] initialOutput = filled(GpuBufferLayout.MAX_ITERATIONS * (capacity + 1), SENTINEL);
                Arrays.fill(initialOutput, 0, GpuBufferLayout.MAX_ITERATIONS, 0);
                output.replace(initialOutput);
                request.bindStorage(define(source, "REQUEST_QUEUE_BINDING"));
                meta.bindStorage(define(source, "NODE_QUEUE_META_BINDING"));
                output.bindStorage(0);
                glUseProgram(program);
                glUniform1ui(BATCH_LOCATION, branching);
                glUniform1ui(define(source, "NODE_QUEUE_INDEX_BINDING"), 0);
                top.bindStorage(define(source, "NODE_QUEUE_SOURCE_BINDING"));
                scratchB.bindStorage(define(source, "NODE_QUEUE_SINK_BINDING"));
                glMemoryBarrier(GL_BUFFER_UPDATE_BARRIER_BIT | GL_SHADER_STORAGE_BARRIER_BIT | GL_COMMAND_BARRIER_BIT);
                if (roots != 0) glDispatchCompute((roots + 31) / 32, 1, 1);
                for (int iteration = 1; iteration < GpuBufferLayout.MAX_ITERATIONS; iteration++) {
                    glUniform1ui(define(source, "NODE_QUEUE_INDEX_BINDING"), iteration);
                    ((iteration & 1) == 0 ? scratchA : scratchB).bindStorage(define(source, "NODE_QUEUE_SOURCE_BINDING"));
                    ((iteration & 1) == 0 ? scratchB : scratchA).bindStorage(define(source, "NODE_QUEUE_SINK_BINDING"));
                    HierarchicalOcclusionTraverser.dispatchQueuedIteration(meta.id, snapshot.id, iteration);
                }
                glMemoryBarrier(GL_BUFFER_UPDATE_BARRIER_BIT | GL_SHADER_STORAGE_BARRIER_BIT | GL_COMMAND_BARRIER_BIT);
                // Read only after the full indirect chain; no CPU count fixup or readback
                // is allowed between the metadata producer and its next consumer.
                int[] actual = output.read(), actualMetadata = meta.read();
                int expectedCount = roots, firstId = 1;
                for (int iteration = 0; iteration < GpuBufferLayout.MAX_ITERATIONS; iteration++) {
                    assertEquals(expectedCount, actual[iteration], "chain visits " + Arrays.toString(frame) + " iteration=" + iteration);
                    assertEquals((expectedCount + 31) / 32, actualMetadata[iteration * 4]);
                    assertEquals(1, actualMetadata[iteration * 4 + 1]);
                    assertEquals(1, actualMetadata[iteration * 4 + 2]);
                    assertEquals(expectedCount, actualMetadata[iteration * 4 + 3]);
                    Set<Integer> nodes = new HashSet<>();
                    for (int invocation = 0; invocation < capacity; invocation++) {
                        int value = actual[GpuBufferLayout.MAX_ITERATIONS + iteration * capacity + invocation];
                        if (invocation < expectedCount) {
                            assertTrue(value >= firstId && value < firstId + expectedCount);
                            assertTrue(nodes.add(value), "duplicate source node in indirect traversal");
                        } else {
                            assertEquals(SENTINEL, value, "indirect dispatch consumed an unwritten source slot");
                        }
                    }
                    if (iteration + 1 < GpuBufferLayout.MAX_ITERATIONS) {
                        expectedCount *= branching;
                        firstId *= branching;
                    }
                }
                int[] dispatch = snapshot.read();
                assertArrayEquals(new int[]{(expectedCount + 31) / 32, 1, 1}, Arrays.copyOf(dispatch, 3));
                assertEquals(snapshot.id, glGetInteger(GL_DISPATCH_INDIRECT_BUFFER_BINDING));
                assertEquals(0, request.read()[1], "this chain fits normal queue capacity without overflow");
                request.assertGuard(); meta.assertGuard(); snapshot.assertGuard(); top.assertGuard();
                scratchA.assertGuard(); scratchB.assertGuard(); output.assertGuard();
                assertEquals(GL_NO_ERROR, glGetError());
            }
        } finally {
            glBindBuffer(GL_DISPATCH_INDIRECT_BUFFER, 0);
            glUseProgram(0);
            glDeleteProgram(program);
        }
    }

    private static String formalSource(String taa) {
        return ForgeOriginalVoxyShaderCompiler.prepareSource(
                HierarchicalOcclusionTraverser.buildTraversalSource(new RenderProperties(false, false, false), taa));
    }

    private static void compileFullTraversal(String source) {
        int program = Round10GpuExecutionTest.computeProgram(source);
        try {
            assertEquals(GL_TRUE, glGetProgrami(program, GL_LINK_STATUS));
        } finally {
            glDeleteProgram(program);
        }
    }

    private static void executeChildReservations(String source) {
        String wrapped = wrap(source, """
                layout(location=20) uniform uint testInputCount;
                layout(location=21) uniform uint testBatch;
                void main() {
                    uint id = gl_GlobalInvocationID.x;
                    if (id >= testInputCount) return;
                    if (pushNodesInit(testBatch)) {
                        for (uint child=0; child<testBatch; child++) pushNode(id*testBatch+child+1u);
                    }
                }
                """);
        int program = Round10GpuExecutionTest.computeProgram(wrapped);
        try {
            int[][] cases = {
                    {32, 0, 0, 8, 0}, {32, 0, 3, 8, 0}, {32, 0, 4, 8, 0}, {32, 0, 5, 8, 0},
                    {33, 32, 128, 1, 0}, {33, 26, 128, 8, 0}, {32, 24, 128, 8, 0},
                    {32, 32, 1, 0, 0}, {32, 0, 128, 8, 4}, {32, 0, 128, 8, 5}
            };
            for (int[] test : cases) {
                int capacity = test[0], initialCount = test[1], invocations = test[2], batch = test[3], iteration = test[4];
                int[] initialMetadata = metadata();
                if (iteration + 1 < GpuBufferLayout.MAX_ITERATIONS) {
                    initialMetadata[(iteration + 1) * 4] = (initialCount + 31) / 32;
                    initialMetadata[(iteration + 1) * 4 + 3] = initialCount;
                }
                int[] initialSink = filled(capacity, SENTINEL);
                for (int i = 0; i < initialCount; i++) initialSink[i] = 0x4000_0000 + i;
                try (GuardedBuffer request = new GuardedBuffer(new int[2]);
                     GuardedBuffer meta = new GuardedBuffer(initialMetadata);
                     GuardedBuffer sink = new GuardedBuffer(initialSink)) {
                    request.bindStorage(define(source, "REQUEST_QUEUE_BINDING"));
                    meta.bindStorage(define(source, "NODE_QUEUE_META_BINDING"));
                    sink.bindStorage(define(source, "NODE_QUEUE_SINK_BINDING"));
                    glUseProgram(program);
                    glUniform1ui(define(source, "NODE_QUEUE_INDEX_BINDING"), iteration);
                    glUniform1ui(INPUT_COUNT_LOCATION, invocations);
                    glUniform1ui(BATCH_LOCATION, batch);
                    dispatch(invocations);
                    int[] actualMeta = meta.read();
                    int[] actualSink = sink.read();
                    int flags = request.read()[1];
                    if (iteration >= GpuBufferLayout.MAX_ITERATIONS - 1) {
                        assertArrayEquals(initialMetadata, Arrays.copyOf(actualMeta, initialMetadata.length));
                        assertEquals(HierarchicalOcclusionTraverser.OVERFLOW_LAST_ITERATION, flags);
                    } else {
                        int acceptedBatches = batch == 0 ? invocations : Math.min(invocations, (capacity - initialCount) / batch);
                        int successfulCount = initialCount + acceptedBatches * batch;
                        int countOffset = (iteration + 1) * 4;
                        assertEquals(successfulCount, actualMeta[countOffset + 3], Arrays.toString(test));
                        assertEquals((successfulCount + 31) / 32, actualMeta[countOffset]);
                        assertEquals(acceptedBatches < invocations ? HierarchicalOcclusionTraverser.OVERFLOW_CHILD_QUEUE : 0, flags);
                        Set<Integer> values = new HashSet<>();
                        for (int i = initialCount; i < successfulCount; i++) {
                            assertTrue(actualSink[i] > 0 && actualSink[i] <= invocations * batch);
                            assertTrue(values.add(actualSink[i]), "GPU wrote an overlapping/duplicate child reservation");
                        }
                        if (batch > 0) {
                            for (int value : values) {
                                int first = ((value - 1) / batch) * batch + 1;
                                for (int child = 0; child < batch; child++) {
                                    assertTrue(values.contains(first + child), "partial child batch was published");
                                }
                            }
                        }
                        for (int i = successfulCount; i < capacity; i++) assertEquals(SENTINEL, actualSink[i]);
                        for (int i = 0; i < initialMetadata.length; i++) {
                            if (i != countOffset && i != countOffset + 3) assertEquals(initialMetadata[i], actualMeta[i]);
                        }
                    }
                    for (int i = 0; i < initialCount; i++) assertEquals(initialSink[i], actualSink[i]);
                    request.assertGuard(); meta.assertGuard(); sink.assertGuard();
                    assertEquals(GL_NO_ERROR, glGetError(), Arrays.toString(test));
                }
            }
        } finally {
            glUseProgram(0);
            glDeleteProgram(program);
        }
    }

    private static void executeSourceReads(String source) {
        int program = Round10GpuExecutionTest.computeProgram(wrap(source, """
                layout(std430,binding=0) buffer TestOutput { uint testResult[]; };
                void main() { testResult[gl_GlobalInvocationID.x] = getCurrentNode(); }
                """));
        try {
            int capacity = 32;
            for (int[] test : new int[][]{{0, 0}, {31, 0}, {32, 0}, {33, 0}, {-1, 0}, {33, 4}, {0, 5}}) {
                int rawCount = test[0], iteration = test[1];
                int[] initialMeta = metadata();
                if (iteration < GpuBufferLayout.MAX_ITERATIONS) initialMeta[iteration * 4 + 3] = rawCount;
                int[] sourceIds = new int[capacity];
                for (int i = 0; i < capacity; i++) sourceIds[i] = i + 1;
                try (GuardedBuffer request = new GuardedBuffer(new int[2]);
                     GuardedBuffer meta = new GuardedBuffer(initialMeta);
                     GuardedBuffer input = new GuardedBuffer(sourceIds);
                     GuardedBuffer output = new GuardedBuffer(filled(64, SENTINEL))) {
                    request.bindStorage(define(source, "REQUEST_QUEUE_BINDING"));
                    meta.bindStorage(define(source, "NODE_QUEUE_META_BINDING"));
                    input.bindStorage(define(source, "NODE_QUEUE_SOURCE_BINDING"));
                    output.bindStorage(0);
                    glUseProgram(program);
                    glUniform1ui(define(source, "NODE_QUEUE_INDEX_BINDING"), iteration);
                    dispatch(64);
                    int valid = iteration >= GpuBufferLayout.MAX_ITERATIONS ? 0 : (int) Math.min(Integer.toUnsignedLong(rawCount), capacity);
                    int[] actual = output.read();
                    for (int i = 0; i < 64; i++) assertEquals(i < valid ? i + 1 : -1, actual[i]);
                    int expectedFlags = iteration >= GpuBufferLayout.MAX_ITERATIONS || Integer.toUnsignedLong(rawCount) > capacity
                            ? HierarchicalOcclusionTraverser.OVERFLOW_SOURCE : 0;
                    assertEquals(expectedFlags, request.read()[1]);
                    assertArrayEquals(initialMeta, Arrays.copyOf(meta.read(), initialMeta.length));
                    request.assertGuard(); meta.assertGuard(); input.assertGuard(); output.assertGuard();
                    assertEquals(GL_NO_ERROR, glGetError());
                }
            }
        } finally {
            glUseProgram(0);
            glDeleteProgram(program);
        }
    }

    private static void executeRequestReservations(String source) {
        int program = Round10GpuExecutionTest.computeProgram(wrap(source, """
                layout(location=20) uniform uint testInputCount;
                void main() {
                    uint id=gl_GlobalInvocationID.x;
                    if (id >= testInputCount) return;
                    UnpackedNode node;
                    unpackNode(node, id);
                    addRequest(node);
                }
                """));
        try {
            for (int[] test : new int[][]{{50, 50, 0}, {50, 50, 49}, {50, 50, 50}, {50, 50, 51}, {50, 49, 51}, {50, 0, 64}, {4, 50, 5}}) {
                int capacity = test[0], softLimit = test[1], invocations = test[2];
                int nodeCount = Math.max(1, invocations);
                try (GuardedBuffer request = new GuardedBuffer(requestData(capacity));
                     GuardedBuffer nodes = new GuardedBuffer(nodes(nodeCount, 0));
                     GuardedBuffer uniform = new GuardedBuffer(scene(capacity, softLimit, 12345))) {
                    request.bindStorage(define(source, "REQUEST_QUEUE_BINDING"));
                    nodes.bindStorage(define(source, "NODE_DATA_BINDING"));
                    uniform.bindUniform(define(source, "SCENE_UNIFORM_BINDING"));
                    glUseProgram(program);
                    glUniform1ui(INPUT_COUNT_LOCATION, invocations);
                    dispatch(invocations);
                    int expectedCount = Math.min(invocations, Math.min(capacity, Math.min(softLimit, 50)));
                    Set<Integer> accepted = assertRequestsMatchFlags(request.read(), nodes.read(), invocations, expectedCount);
                    assertEquals(invocations > expectedCount ? HierarchicalOcclusionTraverser.OVERFLOW_REQUEST : 0, request.read()[1]);
                    if (invocations == 51 && capacity == 50 && softLimit == 50) {
                        int rejected = -1;
                        for (int i = 0; i < invocations; i++) if (!accepted.contains(i)) rejected = i;
                        assertTrue(rejected >= 0);
                        request.write(0, 0); request.write(1, 0);
                        dispatch(invocations);
                        int[] retry = request.read();
                        assertEquals(1, retry[0], "only the previously rejected request must retry");
                        assertEquals(rejected, retry[2]);
                        assertEquals(0, retry[1]);
                        int[] afterRetry = nodes.read();
                        for (int i = 0; i < invocations; i++) assertNotEquals(0, afterRetry[i * 4 + 2] & (1 << 24));
                    }
                    request.assertGuard(); nodes.assertGuard(); uniform.assertGuard();
                    assertEquals(GL_NO_ERROR, glGetError(), Arrays.toString(test));
                }
            }
        } finally {
            glUseProgram(0);
            glDeleteProgram(program);
        }
    }

    private static Set<Integer> assertRequestsMatchFlags(int[] request, int[] nodes, int invocations, int expectedCount) {
        assertEquals(expectedCount, request[0]);
        Set<Integer> accepted = new HashSet<>();
        for (int i = 0; i < expectedCount; i++) {
            int id = request[2 + i * 2];
            assertTrue(id >= 0 && id < invocations);
            assertEquals(id + 1000, request[3 + i * 2]);
            assertTrue(accepted.add(id));
        }
        for (int i = 0; i < invocations; i++) {
            assertEquals(accepted.contains(i), (nodes[i * 4 + 2] & (1 << 24)) != 0,
                    "requested flag must exactly match a successfully published request");
        }
        return accepted;
    }

    private static void executeRenderReservations(String source) {
        int program = Round10GpuExecutionTest.computeProgram(wrap(source, """
                layout(location=20) uniform uint testInputCount;
                void main() {
                    uint id=gl_GlobalInvocationID.x;
                    if (id >= testInputCount) return;
                    UnpackedNode node;
                    unpackNode(node, id);
                    enqueueSelfForRender(node);
                }
                """));
        try {
            // kind 0 = normal, 1 = empty mesh, 2 = missing mesh.
            for (int[] test : new int[][]{{0, 0, 0}, {0, 32, 0}, {0, 33, 0}, {0, 34, 0}, {32, 128, 0}, {0, 16, 1}, {0, 16, 2}, {33, 16, 1}}) {
                int capacity = 33, initial = test[0], invocations = test[1], kind = test[2];
                int[] initialRender = filled(capacity + 1, SENTINEL);
                initialRender[0] = initial;
                for (int i = 0; i < initial; i++) initialRender[i + 1] = 0x4000_0000 + i;
                try (GuardedBuffer request = new GuardedBuffer(new int[2]);
                     GuardedBuffer render = new GuardedBuffer(initialRender);
                     GuardedBuffer nodes = new GuardedBuffer(nodes(Math.max(1, invocations), kind));
                     GuardedBuffer tracker = new GuardedBuffer(new int[Math.max(1, invocations)]);
                     GuardedBuffer uniform = new GuardedBuffer(scene(capacity, 0, 12345))) {
                    request.bindStorage(define(source, "REQUEST_QUEUE_BINDING"));
                    render.bindStorage(define(source, "RENDER_QUEUE_BINDING"));
                    nodes.bindStorage(define(source, "NODE_DATA_BINDING"));
                    tracker.bindStorage(define(source, "RENDER_TRACKER_BINDING"));
                    uniform.bindUniform(define(source, "SCENE_UNIFORM_BINDING"));
                    glUseProgram(program);
                    glUniform1ui(INPUT_COUNT_LOCATION, invocations);
                    dispatch(invocations);
                    int acceptedCount = kind == 0 ? Math.min(invocations, capacity - initial) : 0;
                    int[] actualRender = render.read(), actualTracker = tracker.read();
                    assertEquals(initial + acceptedCount, actualRender[0]);
                    Set<Integer> acceptedIds = new HashSet<>();
                    for (int i = initial; i < initial + acceptedCount; i++) {
                        int id = actualRender[i + 1] - 1;
                        assertTrue(id >= 0 && id < invocations);
                        assertTrue(acceptedIds.add(id));
                    }
                    for (int i = 0; i < invocations; i++) {
                        boolean tracked = kind == 1 ? initial < capacity : kind == 0 && acceptedIds.contains(i);
                        assertEquals(tracked ? 12345 : 0, actualTracker[i]);
                    }
                    for (int i = 0; i < initial; i++) assertEquals(initialRender[i + 1], actualRender[i + 1]);
                    for (int i = initial + acceptedCount; i < capacity; i++) assertEquals(SENTINEL, actualRender[i + 1]);
                    assertEquals(kind == 0 && acceptedCount < invocations ? HierarchicalOcclusionTraverser.OVERFLOW_RENDER_LIST : 0, request.read()[1]);
                    request.assertGuard(); render.assertGuard(); nodes.assertGuard(); tracker.assertGuard(); uniform.assertGuard();
                    assertEquals(GL_NO_ERROR, glGetError(), Arrays.toString(test));
                }
            }
        } finally {
            glUseProgram(0);
            glDeleteProgram(program);
        }
    }

    private static String wrap(String source, String main) {
        assertTrue(source.contains("void main()"));
        return source.replaceFirst("void main\\(\\)", "void originalTraversalMain()") + '\n' + main;
    }

    private static int define(String source, String name) {
        Matcher matcher = Pattern.compile("(?m)^#define\\s+" + Pattern.quote(name) + "\\s+(\\d+)\\s*$").matcher(source);
        assertTrue(matcher.find(), "missing production define " + name);
        return Integer.parseInt(matcher.group(1));
    }

    private static int[] metadata() {
        int[] values = new int[GpuBufferLayout.MAX_ITERATIONS * 4];
        for (int i = 0; i < GpuBufferLayout.MAX_ITERATIONS; i++) values[i * 4 + 1] = values[i * 4 + 2] = 1;
        return values;
    }

    private static int[] scene(int renderCapacity, int requestBudget, int frame) {
        int[] values = new int[256];
        values[48] = renderCapacity;
        values[49] = frame;
        values[50] = requestBudget;
        return values;
    }

    private static int[] nodes(int count, int kind) {
        int[] values = new int[count * 4];
        for (int i = 0; i < count; i++) {
            values[i * 4] = i;
            values[i * 4 + 1] = i + 1000;
            values[i * 4 + 2] = kind == 1 ? 0x00ff_fffe : kind == 2 ? 0x00ff_ffff : i + 1;
            values[i * 4 + 3] = 0x00ff_ffff;
        }
        return values;
    }

    private static int[] requestData(int capacity) {
        int[] values = filled(2 + capacity * 2, SENTINEL);
        values[0] = values[1] = 0;
        return values;
    }

    private static int[] filled(int size, int value) {
        int[] values = new int[size];
        Arrays.fill(values, value);
        return values;
    }

    private static void dispatch(int invocations) {
        glMemoryBarrier(GL_BUFFER_UPDATE_BARRIER_BIT | GL_SHADER_STORAGE_BARRIER_BIT);
        glDispatchCompute(Math.max(1, (invocations + 31) / 32), 1, 1);
        glMemoryBarrier(GL_BUFFER_UPDATE_BARRIER_BIT | GL_SHADER_STORAGE_BARRIER_BIT | GL_COMMAND_BARRIER_BIT);
    }

    private static final class GuardedBuffer implements AutoCloseable {
        final int id;
        final int activeInts;

        GuardedBuffer(int[] initial) {
            assertTrue(initial.length > 0);
            this.activeInts = initial.length;
            int[] storage = Arrays.copyOf(initial, initial.length + GUARD_INTS);
            Arrays.fill(storage, initial.length, storage.length, SENTINEL);
            this.id = glCreateBuffers();
            try {
                glNamedBufferData(this.id, storage, GL_DYNAMIC_READ);
            } catch (Throwable throwable) {
                glDeleteBuffers(this.id);
                throw throwable;
            }
        }

        void bindStorage(int binding) {
            // The guard is allocated but not exposed: .length() sees only the tested capacity.
            glBindBufferRange(GL_SHADER_STORAGE_BUFFER, binding, this.id, 0L, this.activeInts * 4L);
        }

        void bindUniform(int binding) {
            glBindBufferRange(GL_UNIFORM_BUFFER, binding, this.id, 0L, this.activeInts * 4L);
        }

        void write(int index, int value) {
            glNamedBufferSubData(this.id, index * 4L, new int[]{value});
        }

        void replace(int[] values) {
            assertEquals(this.activeInts, values.length);
            glNamedBufferSubData(this.id, 0L, values);
        }

        int[] read() {
            int[] values = new int[this.activeInts + GUARD_INTS];
            glGetNamedBufferSubData(this.id, 0, values);
            return values;
        }

        void assertGuard() {
            int[] values = this.read();
            for (int i = this.activeInts; i < values.length; i++) assertEquals(SENTINEL, values[i], "GPU buffer guard was overwritten");
        }

        @Override
        public void close() {
            glDeleteBuffers(this.id);
        }
    }

    private Round10HocGpuCases() {
    }
}
