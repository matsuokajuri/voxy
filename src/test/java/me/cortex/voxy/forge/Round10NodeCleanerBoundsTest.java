package me.cortex.voxy.forge;

import me.cortex.voxy.common.util.MemoryBuffer;
import me.cortex.voxy.common.world.WorldEngine;
import org.junit.jupiter.api.Test;
import org.lwjgl.system.MemoryUtil;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Round10NodeCleanerBoundsTest {
    private static final int NODE_CAPACITY = 1 << 21;
    private static final int EMPTY_ID = -1;
    private static final int EXTERNAL_BIT = 1 << 31;
    private static final int INDEX_MASK = Integer.MAX_VALUE;

    @Test
    void inclusiveMaximumIdAndRoundedDispatchCoverZeroOneAndGroupEdges() {
        int[] counts = {0, 1, 511, 512, 513, NODE_CAPACITY};
        int[] expectedGroups = {0, 1, 1, 1, 2, 4096};
        for (int i = 0; i < counts.length; i++) {
            int count = NodeCleaner.nodeCountForMaxId(counts[i] - 1, NODE_CAPACITY);
            assertEquals(counts[i], count);
            assertEquals(expectedGroups[i], NodeCleaner.sorterWorkGroupCount(count));
            int visited = 0;
            int lastVisited = -1;
            for (int id = 0; id < expectedGroups[i] * 512; id++) {
                if (id < count) {
                    visited++;
                    lastVisited = id;
                }
            }
            assertEquals(count, visited);
            assertEquals(count - 1, lastVisited);
        }
        assertEquals(0, NodeCleaner.nodeCountForMaxId(-1, 0));
        assertThrows(IllegalArgumentException.class, () -> NodeCleaner.nodeCountForMaxId(0, 0));
        assertThrows(IllegalArgumentException.class, () -> NodeCleaner.nodeCountForMaxId(-2, NODE_CAPACITY));
        assertThrows(IllegalArgumentException.class, () -> NodeCleaner.nodeCountForMaxId(NODE_CAPACITY, NODE_CAPACITY));
        assertThrows(IllegalArgumentException.class, () -> NodeCleaner.sorterWorkGroupCount(-1));
    }

    @Test
    void productionShadersGuardEveryDereferenceAndKeepOriginalEligibility() {
        String sorter = NodeCleaner.buildSorterSource();
        assertTrue(sorter.contains("#define OUTPUT_SIZE " + NodeCleaner.OUTPUT_COUNT));
        assertTrue(sorter.contains("#define MAX_LOD " + WorldEngine.MAX_LOD_LAYER));
        assertTrue(sorter.contains("layout(location=0) uniform uint nodeCount"));
        assertTrue(sorter.contains("taggedId != CLEANER_EMPTY_ID && id < nodeCount"));
        assertTrue(sorter.contains("id < uint(visibility.length()) && id < uint(nodes.length())"));
        assertEquals(1, occurrences(sorter, "visibility["),
                "All candidate visibility reads must use the guarded, flag-stripping helper");
        assertTrue(sorter.contains("return visibility[taggedId & CLEANER_INDEX_MASK]"));
        assertTrue(sorter.contains("id = atomicDerefMaxExchangeLocal(i, id);\n"
                + "        if (id == CLEANER_EMPTY_ID) {\n            break;"));
        assertTrue(sorter.contains("(existingId & CLEANER_INDEX_MASK) == (id & CLEANER_INDEX_MASK)"));
        assertTrue(sorter.contains("if (isEmptyMesh(node) || (!hasMesh(node)))"));
        assertTrue(sorter.contains("if (node.lodLevel == MAX_LOD)"));
        assertTrue(sorter.contains("if (hasRequested(node))"));
        assertFalse(sorter.contains("node.lodLevel == 4"));

        String transformer = NodeCleaner.buildResultTransformerSource();
        assertTrue(transformer.contains("layout(location=1) uniform uint nodeCount"));
        assertTrue(transformer.indexOf("outputIndex >= OUTPUT_SIZE") < transformer.indexOf("minVisIds[outputIndex]"));
        assertTrue(transformer.indexOf("taggedId == uint(-1) || id >= nodeCount") < transformer.indexOf("nodes[id]"));
        assertTrue(transformer.indexOf("id >= uint(nodes.length()) || id >= uint(visibility.length())")
                < transformer.indexOf("nodes[id]"));
        assertTrue(transformer.contains("outputBuffer[outputIndex] = uvec2(-1)"));
        assertTrue(transformer.contains("(minVisIds[i] & CLEANER_INDEX_MASK) == id"));
        assertTrue(transformer.contains("visibility[id] = visibilityCounter + 60"));

        String batch = NodeCleaner.buildBatchClearSource();
        assertTrue(batch.contains("#define WORK_SIZE 128"));
        assertTrue(batch.indexOf("count <= id || id >= uint(ids.length())") < batch.indexOf("uint pos = ids[id]"));
        assertTrue(batch.indexOf("nodeId >= uint(visiblity.length())") < batch.indexOf("visiblity[nodeId] ="));
    }

    @Test
    void checkedKernelModelHandlesFlaggedDuplicateAndSentinelCandidates() {
        SorterModel model = new SorterModel(8, 8, 8);
        model.global[0] = 1;
        int[] local = model.seedLocal();
        assertEquals(model.visibility[1], model.readVisibility(EXTERNAL_BIT | 1));
        assertEquals(-1, model.readVisibility(EMPTY_ID));
        assertEquals(-1, model.readVisibility(EXTERNAL_BIT | 8));
        model.bubble(local, 0, 1);
        assertEquals(1, normalizedOccurrences(local, 1), "A flagged copy is the same node, not another candidate");
        model.bubble(local, 0, EMPTY_ID);
        model.bubble(local, 0, EXTERNAL_BIT | 9);
        model.bubble(local, 0, 0);
        assertEquals(0, local[0]);
        assertEquals(EXTERNAL_BIT | 1, local[1]);
        assertEquals(EMPTY_ID, local[2]);
    }

    @Test
    void checkedKernelModelLeavesUnderfullOutputsEmptyAndNeverUsesPadding() {
        for (int count : new int[]{0, 1, 17, 255, 256, 511, 512, 513, NODE_CAPACITY}) {
            SorterModel model = new SorterModel(count, count, count);
            int[] selected = model.sortPrefix();
            int expected = Math.min(NodeCleaner.OUTPUT_COUNT, count);
            Set<Integer> actual = new HashSet<>();
            for (int i = 0; i < selected.length; i++) {
                if (i < expected) {
                    assertEquals(i, selected[i], "Oldest unique candidate at slot " + i + " for " + count);
                    assertTrue(actual.add(selected[i]));
                } else {
                    assertEquals(EMPTY_ID, selected[i]);
                }
            }
        }
        SorterModel shorterPhysicalBuffers = new SorterModel(513, 511, 510);
        shorterPhysicalBuffers.sortPrefix();
        assertTrue(shorterPhysicalBuffers.highestRead < 510);
    }

    @Test
    void checkedInsertionMatchesOldestUniqueCandidatesAcrossRandomOrders() {
        for (int seed = 0; seed < 12; seed++) {
            SorterModel model = new SorterModel(513, 513, 513);
            int[] candidates = new int[1026];
            for (int i = 0; i < candidates.length; i++) {
                candidates[i] = i % 513;
            }
            Random random = new Random(seed);
            for (int i = candidates.length - 1; i > 0; i--) {
                int other = random.nextInt(i + 1);
                int swap = candidates[i];
                candidates[i] = candidates[other];
                candidates[other] = swap;
            }
            for (int candidate : candidates) {
                model.bubble(model.global, 0, candidate);
            }
            for (int i = 0; i < NodeCleaner.OUTPUT_COUNT; i++) {
                assertEquals(i, model.global[i]);
            }
        }
    }

    @Test
    void checkedTransformerModelSkipsSentinelsDuplicatesAndUnallocatedNodes() {
        int[] candidates = new int[NodeCleaner.OUTPUT_COUNT];
        Arrays.fill(candidates, EMPTY_ID);
        candidates[0] = EXTERNAL_BIT | 1;
        candidates[1] = 1;
        candidates[2] = 0;
        candidates[3] = EXTERNAL_BIT | 0;
        candidates[4] = 3;
        candidates[5] = 2;
        candidates[6] = 4;
        long[] positions = {0L, 0x12345678abcdef01L, -1L, 17L};
        int[] updates = new int[positions.length];
        long[] output = transformModel(candidates, positions, 3, 4, updates);
        assertEquals(positions[1], output[0]);
        assertEquals(0L, output[2], "World position zero is a valid position");
        for (int i = 0; i < output.length; i++) {
            if (i != 0 && i != 2) {
                assertEquals(-1L, output[i]);
            }
        }
        assertArrayEquals(new int[]{1, 1, 0, 0}, updates);
        assertArrayEquals(new int[]{1, -1}, batchModel(new int[]{EXTERNAL_BIT, 1, -1, EXTERNAL_BIT | 2}, 4, 2));
    }

    @Test
    void downloadCopiesOnlyTheFixedPositionRegionAndRejectsOtherLengths() {
        MemoryBuffer full = new MemoryBuffer(GpuBufferLayout.CLEANER_OUTPUT_BYTES);
        MemoryBuffer copy = null;
        try {
            for (int i = 0; i < NodeCleaner.OUTPUT_COUNT; i++) {
                MemoryUtil.memPutInt(full.address + i * 4L, 0x13572468);
                MemoryUtil.memPutLong(full.address + GpuBufferLayout.CLEANER_ID_BYTES + i * 8L,
                        i < 3 ? i : -1L);
            }
            var positions = MemoryBuffer.createUntrackedUnfreeableRawFrom(
                    full.address + GpuBufferLayout.CLEANER_ID_BYTES, GpuBufferLayout.CLEANER_POSITION_BYTES);
            copy = NodeCleaner.copyRemovalPositions(positions);
            assertEquals(2048L, copy.size);
            assertEquals(3072L, GpuBufferLayout.CLEANER_ID_BYTES + copy.size);
            MemoryUtil.memSet(full.address, 0, full.size);
            for (int i = 0; i < NodeCleaner.OUTPUT_COUNT; i++) {
                assertEquals(i < 3 ? i : -1L, MemoryUtil.memGetLong(copy.address + i * 8L));
            }
            assertThrows(IllegalArgumentException.class, () -> NodeCleaner.copyRemovalPositions(full));
            assertThrows(IllegalArgumentException.class, () -> NodeCleaner.copyRemovalPositions(
                    MemoryBuffer.createUntrackedUnfreeableRawFrom(full.address, GpuBufferLayout.CLEANER_POSITION_BYTES - 8L)));
        } finally {
            if (copy != null) copy.free();
            full.free();
        }
    }

    private static int occurrences(String source, String text) {
        return (source.length() - source.replace(text, "").length()) / text.length();
    }

    private static int normalizedOccurrences(int[] ids, int nodeId) {
        int count = 0;
        for (int id : ids) if ((id & INDEX_MASK) == nodeId) count++;
        return count;
    }

    // Checked CPU model of the source-locked GLSL indexing/insertion rules above. This is
    // boundary evidence only; it is deliberately not a production renderer or GPU qualification.
    private static final class SorterModel {
        final int[] visibility;
        final int[] global = new int[NodeCleaner.OUTPUT_COUNT];
        final int nodeCount;
        final int nodesLength;
        int highestRead = -1;

        SorterModel(int nodeCount, int visibilityLength, int nodesLength) {
            this.nodeCount = nodeCount;
            this.nodesLength = nodesLength;
            this.visibility = new int[visibilityLength];
            for (int i = 0; i < visibilityLength; i++) visibility[i] = i + 1;
            Arrays.fill(global, EMPTY_ID);
        }

        boolean valid(int taggedId) {
            int id = taggedId & INDEX_MASK;
            return taggedId != EMPTY_ID && id < nodeCount && id < visibility.length && id < nodesLength;
        }

        int readVisibility(int taggedId) {
            if (!valid(taggedId)) return -1;
            int id = taggedId & INDEX_MASK;
            highestRead = Math.max(highestRead, id);
            return visibility[id];
        }

        int exchange(int[] output, int at, int id) {
            if (!valid(id)) return EMPTY_ID;
            int existing = output[at];
            if ((existing & INDEX_MASK) == (id & INDEX_MASK)) return EMPTY_ID;
            if (Integer.compareUnsigned(readVisibility(existing), readVisibility(id)) <= 0) return id;
            output[at] = id;
            return existing;
        }

        void bubble(int[] output, int start, int id) {
            for (int i = start; i < output.length; i++) {
                id = exchange(output, i, id);
                if (id == EMPTY_ID) break;
            }
        }

        int[] seedLocal() {
            int[] local = global.clone();
            for (int i = 0; i < local.length; i++) local[i] |= EXTERNAL_BIT;
            return local;
        }

        int[] sortPrefix() {
            for (int group = 0; group < NodeCleaner.sorterWorkGroupCount(nodeCount); group++) {
                int[] local = seedLocal();
                for (int id = group * 512; id < (group + 1) * 512; id++) {
                    if (!valid(id)) continue;
                    int vis = readVisibility(id);
                    if (vis == -1 || Integer.compareUnsigned(readVisibility(global[255]), vis) <= 0) continue;
                    int start = Integer.compareUnsigned(readVisibility(local[127]), vis) <= 0 ? 127 : 0;
                    bubble(local, start, id);
                }
                for (int work = 0; work < 4; work++) {
                    for (int lane = 0; lane < 64; lane++) {
                        int index = lane * 4 + work;
                        int id = local[index];
                        if ((id & EXTERNAL_BIT) != 0 || !valid(id)) continue;
                        int vis = readVisibility(id);
                        if (Integer.compareUnsigned(readVisibility(global[255]), vis) <= 0) continue;
                        int middle = (index + 256) >>> 1;
                        int start = Integer.compareUnsigned(readVisibility(global[middle]), vis) <= 0 ? middle : index;
                        bubble(global, start, id);
                    }
                }
            }
            return global;
        }
    }

    private static long[] transformModel(int[] ids, long[] nodes, int nodeCount, int visibilityLength, int[] updates) {
        long[] output = new long[NodeCleaner.OUTPUT_COUNT];
        Arrays.fill(output, -1L);
        for (int slot = 0; slot < output.length; slot++) {
            int id = ids[slot] & INDEX_MASK;
            if (ids[slot] == EMPTY_ID || id >= nodeCount || id >= nodes.length || id >= visibilityLength) continue;
            boolean duplicate = false;
            for (int i = 0; i < slot; i++) duplicate |= (ids[i] & INDEX_MASK) == id;
            if (duplicate || nodes[id] == -1L) continue;
            output[slot] = nodes[id];
            updates[id]++;
        }
        return output;
    }

    private static int[] batchModel(int[] ids, int count, int capacity) {
        int[] output = new int[capacity];
        Arrays.fill(output, 9);
        for (int invocation = 0; invocation < (count + 127) / 128 * 128; invocation++) {
            if (invocation >= count || invocation >= ids.length) continue;
            int id = ids[invocation] & INDEX_MASK;
            if (id >= capacity) continue;
            output[id] = (ids[invocation] & EXTERNAL_BIT) == 0 ? -1 : 1;
        }
        return output;
    }
}
