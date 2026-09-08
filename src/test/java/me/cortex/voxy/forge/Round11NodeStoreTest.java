package me.cortex.voxy.forge;

import me.cortex.voxy.common.util.MemoryBuffer;
import org.junit.jupiter.api.Test;
import org.lwjgl.system.MemoryUtil;

import static org.junit.jupiter.api.Assertions.*;

class Round11NodeStoreTest {
    @Test
    void failedBatchFreeIsAtomicAndInvalidCountsAreRejected() {
        NodeStore store = new NodeStore(16);
        int base = store.allocate(3);
        store.free(base + 1);
        assertThrows(IllegalStateException.class, () -> store.free(base, 3));
        assertTrue(store.nodeExists(base), "validation must happen before any free");
        assertTrue(store.nodeExists(base + 2));
        assertEquals(2, store.getNodeCount());
        assertThrows(IllegalArgumentException.class, () -> store.free(base, 0));
        assertThrows(IllegalArgumentException.class, () -> store.free(base, -1));
    }

    @Test
    void copyReuseAndSentinelsPreserveExactSixteenByteGpuContract() {
        NodeStore store = new NodeStore(16);
        int source = store.allocate();
        int dest = store.allocate();
        long position = Round11NodeTestSupport.pos(4, -17, 2, 33);
        store.setNodePosition(source, position);
        store.setNodeGeometry(source, NodeManager.EMPTY_GEOMETRY_ID);
        store.setChildPtr(source, NodeManager.SENTINEL_EMPTY_CHILD_PTR);
        store.setChildPtrCount(source, 8);
        store.setNodeChildExistence(source, (byte) 0x81);
        store.markRequestInFlight(source);
        store.setNodeRequest(source, 37);
        store.markNodeGeometryInFlight(source);
        store.setAllChildrenAreLeaf(source, true);
        store.setEmptyCollapsePending(source, true);
        store.copyNode(source, dest);
        store.free(source);
        assertTrue(store.isEmptyCollapsePending(dest));
        MemoryBuffer gpu = new MemoryBuffer(24).zero();
        try {
            store.writeNode(gpu.address, dest);
            assertEquals((int) (position >>> 32), MemoryUtil.memGetInt(gpu.address));
            assertEquals((int) position, MemoryUtil.memGetInt(gpu.address + 4));
            assertEquals(0x3DFFFFFE, MemoryUtil.memGetInt(gpu.address + 8));
            assertEquals(0x00FFFFFE, MemoryUtil.memGetInt(gpu.address + 12));
            assertEquals(0L, MemoryUtil.memGetLong(gpu.address + 16), "GPU write is exactly 16 bytes");
            store.writeNode(gpu.address, source);
            assertEquals(-1L, MemoryUtil.memGetLong(gpu.address));
            assertEquals(-1L, MemoryUtil.memGetLong(gpu.address + 8));
            int reused = store.allocate();
            assertEquals(source, reused);
            assertEquals(NodeManager.NULL_GEOMETRY_ID, store.getNodeGeometry(reused));
            assertEquals(-1, store.getChildPtr(reused));
            assertEquals(NodeManager.NULL_REQUEST_ID, store.getNodeRequest(reused));
            assertFalse(store.isNodeRequestInFlight(reused));
            assertFalse(store.isNodeGeometryInFlight(reused));
            assertFalse(store.getAllChildrenAreLeaf(reused));
            assertFalse(store.isEmptyCollapsePending(reused));
        } finally {
            gpu.free();
        }
    }

    @Test
    void geometryRequestChildCountAndReservedIdsHaveDistinctBounds() {
        NodeStore store = new NodeStore(16);
        int node = store.allocate();
        for (int geometry : new int[]{NodeManager.NULL_GEOMETRY_ID, NodeManager.EMPTY_GEOMETRY_ID,
                0, NodeStore.MAX_GEOMETRY_ID}) {
            store.setNodeGeometry(node, geometry);
            assertEquals(geometry, store.getNodeGeometry(node));
        }
        assertThrows(IllegalArgumentException.class, () -> store.setNodeGeometry(node, -3));
        assertThrows(IllegalArgumentException.class, () -> store.setNodeGeometry(node, NodeStore.MAX_GEOMETRY_ID + 1));
        assertThrows(IllegalArgumentException.class, () -> store.setChildPtrCount(node, 0));
        assertThrows(IllegalArgumentException.class, () -> store.setChildPtrCount(node, 9));
        assertThrows(IllegalStateException.class, () -> store.setNodeRequest(node, -1));
        assertThrows(IllegalStateException.class, () -> store.setNodeRequest(node, NodeStore.REQUEST_ID_MSK + 1));
        assertThrows(IllegalArgumentException.class, () -> store.setChildPtr(node, NodeStore.NODE_ID_MSK));
        assertThrows(IllegalArgumentException.class, () -> store.setChildPtr(node, -2));
    }
}
