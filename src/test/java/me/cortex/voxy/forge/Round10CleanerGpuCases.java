package me.cortex.voxy.forge;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.opengl.GL46C.*;

/** Real production Cleaner kernels, invoked only inside Round10GpuExecutionTest's context. */
final class Round10CleanerGpuCases {
    private static final int CANARY = 0x5a31c7e9;
    private static final int EXTERNAL_BIT = 1 << 31;
    private static final int EMPTY = -1;
    private static final int VISIBILITY_COUNTER = 200;

    private Round10CleanerGpuCases() {
    }

    static void runAll() {
        int sorter = 0, transformer = 0, batch = 0;
        try {
            sorter = compile(NodeCleaner.buildSorterSource());
            transformer = compile(NodeCleaner.buildResultTransformerSource());
            batch = compile(NodeCleaner.buildBatchClearSource());
            for (int count : new int[]{0, 1, 17, 255, 256, 511, 512, 513}) {
                int physicalCount = Math.max(1, count);
                int[] eligible = count <= 256 ? sequence(count) : new int[]{0, count - 1};
                sortAndTransform(sorter, transformer, count, physicalCount, eligible, false);
            }
            // The former ceil(maxId/512) skipped ID 512. The final workgroup now safely
            // scans ID 512 while ignoring its 511 rounded-up invocations beyond capacity.
            sortAndTransform(sorter, transformer, 513, 513, new int[]{512}, false);
            // Independently enforce actual SSBO range lengths, even with an oversized uniform.
            sortAndTransform(sorter, transformer, 513, 512, new int[]{511}, false);
            // Real maximum allocation and dispatch, with one eligible final node.
            sortAndTransform(sorter, transformer, 1 << 21, 1 << 21, new int[]{(1 << 21) - 1}, false);
            // Existing global candidates are copied to shared memory with the high bit set.
            // Re-visiting them exercises flagged dereferences and duplicate/sentinel exits.
            sortAndTransform(sorter, transformer, 256, 256, sequence(256), true);
            transformInvalidAndDuplicateIds(transformer);
            batchUpdatesRespectFlagAndPhysicalRanges(batch);
            assertEquals(GL_NO_ERROR, glGetError(), "All production Cleaner compute paths must leave GL clean");
        } finally {
            glUseProgram(0);
            for (int binding = 0; binding < 4; binding++) glBindBufferBase(GL_SHADER_STORAGE_BUFFER, binding, 0);
            if (batch != 0) glDeleteProgram(batch);
            if (transformer != 0) glDeleteProgram(transformer);
            if (sorter != 0) glDeleteProgram(sorter);
        }
    }

    private static int compile(String source) {
        return Round10GpuExecutionTest.computeProgram(ForgeOriginalVoxyShaderCompiler.prepareSource(source));
    }

    private static void sortAndTransform(int sorter, int transformer, int nodeCount, int physicalCount,
                                         int[] eligible, boolean seedExisting) {
        int[] nodes = new int[physicalCount * 4];
        int[] visibility = new int[physicalCount];
        Arrays.fill(nodes, EMPTY);
        Arrays.fill(visibility, EMPTY);
        for (int id : eligible) {
            putNode(nodes, id, id + 1);
            visibility[id] = id + 1;
        }
        int[] outputData = new int[(int) (GpuBufferLayout.CLEANER_OUTPUT_BYTES / Integer.BYTES)];
        Arrays.fill(outputData, EMPTY);
        if (seedExisting) {
            System.arraycopy(eligible, 0, outputData, 0, eligible.length);
        }
        try (GuardedBuffer nodeBuffer = new GuardedBuffer(nodes);
             GuardedBuffer visibilityBuffer = new GuardedBuffer(visibility);
             GuardedBuffer output = new GuardedBuffer(outputData)) {
            glUseProgram(sorter);
            visibilityBuffer.bind(1);
            output.bindRange(2, 0L, GpuBufferLayout.CLEANER_ID_BYTES);
            nodeBuffer.bind(3);
            glUniform1ui(0, nodeCount);
            int groups = NodeCleaner.sorterWorkGroupCount(nodeCount);
            if (groups > 0) glDispatchCompute(groups, 1, 1);
            barrier();
            int[] selected = output.read(0L, NodeCleaner.OUTPUT_COUNT);
            Set<Integer> actual = new HashSet<>();
            for (int id : selected) {
                if (id == EMPTY) continue;
                assertTrue(id >= 0 && id < physicalCount && id < nodeCount,
                        "Cleaner selected an invalid/tagged ID " + Integer.toUnsignedString(id));
                assertTrue(actual.add(id), "Cleaner emitted duplicate candidate " + id);
            }
            assertEquals(asSet(eligible), actual, "Eligible prefix must not be lost: count=" + nodeCount);

            bindTransformer(transformer, output, nodeBuffer, visibilityBuffer, nodeCount);
            glDispatchCompute(1, 1, 1);
            barrier();
            // This is exactly the formal download window: 256 IDs precede 256 uvec2 positions.
            int[] downloadedPositions = output.read(GpuBufferLayout.CLEANER_ID_BYTES,
                    (int) (GpuBufferLayout.CLEANER_POSITION_BYTES / Integer.BYTES));
            assertEquals(512, downloadedPositions.length);
            for (int slot = 0; slot < NodeCleaner.OUTPUT_COUNT; slot++) {
                int id = selected[slot];
                assertEquals(id == EMPTY ? EMPTY : 0x00123456, downloadedPositions[slot * 2]);
                assertEquals(id == EMPTY ? EMPTY : id + 1, downloadedPositions[slot * 2 + 1]);
            }
            assertArrayEquals(selected, output.read(0L, NodeCleaner.OUTPUT_COUNT), "Transformer cannot overwrite its ID window");
            for (int id : eligible) {
                assertEquals(VISIBILITY_COUNTER + 60, visibilityBuffer.read(id * 4L, 1)[0]);
            }
            nodeBuffer.assertGuards();
            visibilityBuffer.assertGuards();
            output.assertGuards();
            assertEquals(GL_NO_ERROR, glGetError(), "Cleaner count=" + nodeCount + " physical=" + physicalCount);
        }
    }

    private static void transformInvalidAndDuplicateIds(int transformer) {
        int[] nodes = new int[5 * 4];
        Arrays.fill(nodes, EMPTY);
        putNode(nodes, 0, 0);
        nodes[0] = 0; // Position zero is valid, not an empty sentinel.
        putNode(nodes, 1, 101);
        putNode(nodes, 2, 102);
        putNode(nodes, 4, 104);
        int[] visibility = {10, 11, 12, 13, 14};
        int[] data = new int[(int) (GpuBufferLayout.CLEANER_OUTPUT_BYTES / 4L)];
        Arrays.fill(data, EMPTY);
        data[0] = EXTERNAL_BIT | 1;
        data[1] = 1;
        data[2] = 2;
        data[3] = EXTERNAL_BIT | 2;
        data[4] = 5; // Outside physical node/visibility ranges.
        data[5] = 3; // Unallocated node.
        data[6] = 0;
        data[7] = EXTERNAL_BIT | 0;
        data[8] = 4; // Physical node exists but is outside the live prefix.
        Arrays.fill(data, NodeCleaner.OUTPUT_COUNT, data.length, CANARY);
        try (GuardedBuffer nodeBuffer = new GuardedBuffer(nodes);
             GuardedBuffer visibilityBuffer = new GuardedBuffer(visibility);
             GuardedBuffer output = new GuardedBuffer(data)) {
            bindTransformer(transformer, output, nodeBuffer, visibilityBuffer, 4);
            glDispatchCompute(2, 1, 1); // Extra group must not touch the fixed 256-item output.
            barrier();
            int[] positions = output.read(GpuBufferLayout.CLEANER_ID_BYTES, 512);
            for (int slot = 0; slot < NodeCleaner.OUTPUT_COUNT; slot++) {
                int first = EMPTY, second = EMPTY;
                if (slot == 0) { first = 0x00123456; second = 101; }
                if (slot == 2) { first = 0x00123456; second = 102; }
                if (slot == 6) { first = 0; second = 0; }
                assertEquals(first, positions[slot * 2], "Transformer slot=" + slot);
                assertEquals(second, positions[slot * 2 + 1], "Transformer slot=" + slot);
            }
            assertArrayEquals(new int[]{260, 260, 260, 13, 14}, visibilityBuffer.read(0, 5));
            assertArrayEquals(Arrays.copyOf(data, NodeCleaner.OUTPUT_COUNT), output.read(0, NodeCleaner.OUTPUT_COUNT));
            nodeBuffer.assertGuards();
            visibilityBuffer.assertGuards();
            output.assertGuards();
            assertEquals(GL_NO_ERROR, glGetError());
        }
    }

    private static void batchUpdatesRespectFlagAndPhysicalRanges(int batch) {
        int[] ids = {EXTERNAL_BIT, 1, EMPTY, EXTERNAL_BIT | 7, EXTERNAL_BIT, 3};
        try (GuardedBuffer visibility = new GuardedBuffer(new int[]{10, 11, 12});
             GuardedBuffer list = new GuardedBuffer(ids)) {
            glUseProgram(batch);
            visibility.bind(0);
            list.bind(1);
            glUniform1ui(0, ids.length + 1); // Count cannot authorize reading past the actual range.
            glUniform1ui(1, VISIBILITY_COUNTER);
            glDispatchCompute(1, 1, 1);
            barrier();
            assertArrayEquals(new int[]{VISIBILITY_COUNTER, EMPTY, 12}, visibility.read(0L, 3));
            assertArrayEquals(ids, list.read(0L, ids.length));
            visibility.assertGuards();
            list.assertGuards();
            assertEquals(GL_NO_ERROR, glGetError());
        }
    }

    private static void bindTransformer(int transformer, GuardedBuffer output, GuardedBuffer nodes,
                                        GuardedBuffer visibility, int nodeCount) {
        glUseProgram(transformer);
        output.bindRange(0, 0L, GpuBufferLayout.CLEANER_ID_BYTES);
        nodes.bind(1);
        output.bindRange(2, GpuBufferLayout.CLEANER_ID_BYTES, GpuBufferLayout.CLEANER_POSITION_BYTES);
        visibility.bind(3);
        glUniform1ui(0, VISIBILITY_COUNTER);
        glUniform1ui(1, nodeCount);
    }

    private static void putNode(int[] data, int id, int lowPosition) {
        data[id * 4] = 0x00123456; // LOD zero: eligible under the original top-LOD rule.
        data[id * 4 + 1] = lowPosition;
        data[id * 4 + 2] = 0; // Nonempty mesh, no requested flag.
        data[id * 4 + 3] = 0x00ffffff; // No children; eligibility semantics are not modified here.
    }

    private static int[] sequence(int size) {
        int[] result = new int[size];
        for (int i = 0; i < size; i++) result[i] = i;
        return result;
    }

    private static Set<Integer> asSet(int[] ids) {
        Set<Integer> result = new HashSet<>();
        for (int id : ids) result.add(id);
        return result;
    }

    private static void barrier() {
        glMemoryBarrier(GL_SHADER_STORAGE_BARRIER_BIT | GL_BUFFER_UPDATE_BARRIER_BIT);
    }

    private static final class GuardedBuffer implements AutoCloseable {
        final int id;
        final int guardWords;
        final long payloadBytes;

        GuardedBuffer(int[] payload) {
            int alignment = glGetInteger(GL_SHADER_STORAGE_BUFFER_OFFSET_ALIGNMENT);
            assertEquals(0L, GpuBufferLayout.CLEANER_ID_BYTES % alignment,
                    "Formal Cleaner ID/position split must be bindable on this context");
            this.guardWords = Math.max(64, alignment) / Integer.BYTES;
            this.payloadBytes = payload.length * 4L;
            int[] all = new int[guardWords + payload.length + guardWords];
            Arrays.fill(all, CANARY);
            System.arraycopy(payload, 0, all, guardWords, payload.length);
            this.id = glCreateBuffers();
            glNamedBufferData(id, all, GL_DYNAMIC_COPY);
        }

        void bind(int binding) {
            bindRange(binding, 0, payloadBytes);
        }

        void bindRange(int binding, long offset, long length) {
            assertTrue(offset >= 0 && length > 0 && offset + length <= payloadBytes);
            glBindBufferRange(GL_SHADER_STORAGE_BUFFER, binding, id, guardWords * 4L + offset, length);
        }

        int[] read(long offset, int words) {
            assertTrue(offset >= 0 && offset + words * 4L <= payloadBytes);
            int[] result = new int[words];
            glGetNamedBufferSubData(id, guardWords * 4L + offset, result);
            return result;
        }

        void assertGuards() {
            int[] before = new int[guardWords], after = new int[guardWords];
            glGetNamedBufferSubData(id, 0, before);
            glGetNamedBufferSubData(id, guardWords * 4L + payloadBytes, after);
            for (int value : before) assertEquals(CANARY, value, "Buffer prefix sentinel changed");
            for (int value : after) assertEquals(CANARY, value, "Buffer suffix sentinel changed");
        }

        @Override
        public void close() {
            glDeleteBuffers(id);
        }
    }
}
