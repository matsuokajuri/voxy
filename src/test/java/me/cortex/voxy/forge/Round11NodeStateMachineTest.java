package me.cortex.voxy.forge;

import org.junit.jupiter.api.Test;

import static me.cortex.voxy.forge.Round11NodeTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class Round11NodeStateMachineTest {
    @Test
    void nodeCapacityFailureRetainsTheSingleRequestAndItsGeometryOwnership() {
        try (var s = new Round11NodeTestSupport(1)) {
            long root = pos(2, 0, 0, 0);
            long nextRoot = pos(2, 1, 0, 0);
            s.insert(root, 1);
            assertTrue(s.manager.insertTopLevelNode(nextRoot));
            assertThrows(IllegalStateException.class, () -> s.result(nextRoot, 1, false));
            assertEquals(REQUEST, s.type(nextRoot));
            s.verify();
        }
    }

    @Test
    void failedGeometryUploadRetainsMissingParentInFlightOwnership() {
        try (var s = new Round11NodeTestSupport(128, 2048)) {
            long root = pos(2, 0, 0, 0);
            long spacer = pos(2, 1, 0, 0);
            s.insert(root, 1);
            s.split(root, 1);
            s.manager.removeNodeGeometry(root);
            s.insert(spacer, 1);
            s.manager.processChildChange(root, (byte) 0);
            assertThrows(IllegalStateException.class, () -> s.result(root, 0, false));
            assertTrue(s.store.isNodeGeometryInFlight(s.id(root)), "failed completion must not consume in-flight state");
            assertTrue(s.store.isEmptyCollapsePending(s.id(root)));
            s.verify();
        }
    }

    @Test
    void cleanerCannotCancelRequestedReplacementOfAlreadyMissingInnerGeometry() {
        try (var s = new Round11NodeTestSupport()) {
            long root = pos(2, 0, 0, 0);
            s.insert(root, 1);
            s.split(root, 1);
            s.manager.removeNodeGeometry(root);
            s.manager.processRequest(root);
            s.manager.removeNodeGeometry(root);
            assertTrue(s.store.isNodeGeometryInFlight(s.id(root)));
            assertNotEquals(0, s.watcher.get(root) & me.cortex.voxy.common.world.WorldEngine.UPDATE_TYPE_BLOCK_BIT);
            assertTrue(s.active.containsKey(child(root, 0)));
            s.verify();
        }
    }

    @Test
    void splitPublishesChangedImmediateAncestorLeafFlag() {
        try (var s = new Round11NodeTestSupport()) {
            long root = pos(3, -2, 0, 3);
            s.insert(root, 1);
            s.split(root, 1);
            int rootId = s.id(root);
            assertTrue(s.store.getAllChildrenAreLeaf(rootId));
            s.manager.getNodeUpdates().clear();
            s.split(child(root, 0), 1);
            assertFalse(s.store.getAllChildrenAreLeaf(rootId));
            assertTrue(s.manager.getNodeUpdates().contains(rootId), "ancestor GPU row must be republished");
        }
    }

    @Test
    void cleanerCollapseRestoresImmediateAncestorLeafFlagAtEachDepth() {
        try (var s = new Round11NodeTestSupport()) {
            long root = pos(3, 0, 0, 0);
            long middle = child(root, 0);
            long bottom = child(middle, 0);
            s.insert(root, 1);
            s.split(root, 1);
            s.split(middle, 1);
            s.split(bottom, 1);
            s.manager.getNodeUpdates().clear();
            s.manager.removeNodeGeometry(child(bottom, 0));
            assertTrue(s.store.getAllChildrenAreLeaf(s.id(middle)));
            assertFalse(s.store.getAllChildrenAreLeaf(s.id(root)), "middle remains an inner node");
            assertTrue(s.manager.getNodeUpdates().contains(s.id(middle)));
            s.verify();
            s.manager.removeNodeGeometry(bottom);
            assertTrue(s.store.getAllChildrenAreLeaf(s.id(root)));
            s.verify();
        }
    }

    @Test
    void zeroMaskCollapseRestoresContainingParentLeafFlag() {
        try (var s = new Round11NodeTestSupport()) {
            long root = pos(2, 0, 0, 0);
            s.insert(root, 1);
            s.split(root, 1);
            long middle = child(root, 0);
            s.split(middle, 1);
            s.manager.getNodeUpdates().clear();
            s.manager.processChildChange(middle, (byte) 0);
            assertEquals(LEAF, s.type(middle));
            assertTrue(s.store.getAllChildrenAreLeaf(s.id(root)));
            assertTrue(s.manager.getNodeUpdates().contains(s.id(root)));
            s.verify();
        }
    }

    @Test
    void zeroMaskMustNotReplaceMissingParentWithFabricatedEmptyGeometry() {
        try (var s = new Round11NodeTestSupport()) {
            long root = pos(2, 0, 0, 0);
            s.insert(root, 0x81);
            s.split(root, 1);
            int rootId = s.id(root);
            s.manager.removeNodeGeometry(root);
            assertEquals(NodeManager.NULL_GEOMETRY_ID, s.store.getNodeGeometry(rootId));
            int firstChildId = s.id(child(root, 0));
            int lastChildId = s.id(child(root, 7));
            s.manager.processChildChange(root, (byte) 0);
            assertEquals(NodeManager.NULL_GEOMETRY_ID, s.store.getNodeGeometry(rootId),
                    "only an accepted empty BuiltSection may produce EMPTY");
            assertTrue(s.store.isNodeGeometryInFlight(rootId));
            assertEquals(firstChildId, s.id(child(root, 0)), "retain valid child coverage until replacement exists");
            assertEquals(lastChildId, s.id(child(root, 7)));
            s.verify();
            s.result(root, 0, true);
            assertEquals(LEAF, s.type(root));
            assertEquals(NodeManager.EMPTY_GEOMETRY_ID, s.store.getNodeGeometry(rootId));
            assertFalse(s.active.containsKey(child(root, 0)));
            assertFalse(s.active.containsKey(child(root, 7)));
            s.verify();
        }
    }

    @Test
    void emptyTopLevelParkedRequestConvergesAndCancelsAcrossAllEightBits() {
        try (var s = new Round11NodeTestSupport()) {
            long root = pos(1, -1, 0, -1);
            s.insert(root, 0);
            for (int bit = 0; bit < 8; bit++) {
                s.manager.processRequest(root);
                assertEquals(1, s.childRequests());
                s.manager.processRequest(root);
                assertEquals(1, s.childRequests(), "duplicate request must not allocate");
                s.verify();
                s.manager.processChildChange(root, (byte) (1 << bit));
                s.result(child(root, bit), 0, true);
                assertEquals(INNER, s.type(root));
                s.verify();
                s.manager.processChildChange(root, (byte) 0);
                assertEquals(LEAF, s.type(root));
                assertEquals(0, s.childRequests());
                s.verify();
            }
            s.manager.processRequest(root);
            assertEquals(1, s.childRequests());
        }
    }

    @Test
    void singleRequestSupportsChildFirstGeometryFirstEmptyAndCancellation() {
        try (var s = new Round11NodeTestSupport()) {
            long root = pos(2, 3, 0, -3);
            assertTrue(s.manager.insertTopLevelNode(root));
            s.manager.processChildChange(root, (byte) 0x80);
            s.verify();
            s.result(root, 1, true);
            assertEquals((byte) 0x80, s.store.getNodeChildExistence(s.id(root)), "newer explicit child update wins");
            s.verify();
            s.manager.removeTopLevelNode(root);
            assertTrue(s.manager.insertTopLevelNode(root));
            s.result(root, 3, false);
            s.manager.processChildChange(root, (byte) 0x81);
            s.verify();
            s.manager.removeTopLevelNode(root);
            assertTrue(s.manager.insertTopLevelNode(root));
            s.manager.processChildChange(root, (byte) 1);
            s.manager.removeTopLevelNode(root);
            assertFalse(s.manager.processGeometryResult(BuiltSection.empty(root)));
            s.verify();
        }
    }

    @Test
    void partialChildResultsCanBeReplacedRemovedReaddedAndThenCancelled() {
        try (var s = new Round11NodeTestSupport()) {
            long root = pos(2, 0, 0, 0);
            s.insert(root, 0x81);
            s.manager.processRequest(root);
            s.result(child(root, 7), 1, false);
            s.result(child(root, 7), 1, false);
            s.verify();
            s.manager.processChildChange(root, (byte) 1);
            s.verify();
            s.manager.processChildChange(root, (byte) 0x81);
            s.result(child(root, 7), 1, true);
            s.verify();
            s.manager.processChildChange(root, (byte) 0);
            assertEquals(0, s.childRequests());
            assertEquals(1, s.geometry.getSectionCount());
            s.verify();
        }
    }

    @Test
    void compactionMovesSurvivorsAndTheirPendingChildRequestWithoutChangingGeometry() {
        try (var s = new Round11NodeTestSupport()) {
            long root = pos(3, 0, 0, 0);
            s.insert(root, 0x83);
            s.split(root, 1);
            long survivor = child(root, 7);
            int oldId = s.id(survivor);
            int oldMesh = s.store.getNodeGeometry(oldId);
            s.manager.processRequest(survivor);
            s.manager.processChildChange(root, (byte) 0x82);
            int newId = s.id(survivor);
            assertNotEquals(oldId, newId);
            assertEquals(oldMesh, s.store.getNodeGeometry(newId));
            assertTrue(s.store.isNodeRequestInFlight(newId));
            s.result(child(survivor, 0), 1, false);
            s.verify();
            int move = s.cleaner.operations.indexOf("move " + oldId + " " + newId);
            assertEquals("alloc " + newId, s.cleaner.operations.get(move - 1));
            assertEquals("free " + oldId, s.cleaner.operations.get(move + 1));
        }
    }

    @Test
    void deferredEmptyCollapseFollowsNodeCompactionUntilActualEmptyResult() {
        try (var s = new Round11NodeTestSupport()) {
            long root = pos(3, 0, 0, 0);
            long middle = child(root, 0);
            s.insert(root, 3);
            s.split(root, 1);
            s.split(middle, 1);
            s.manager.removeNodeGeometry(middle);
            s.manager.processChildChange(middle, (byte) 0);
            int oldId = s.id(middle);
            assertTrue(s.store.isEmptyCollapsePending(oldId));
            s.manager.processChildChange(root, (byte) 1);
            int movedId = s.id(middle);
            assertNotEquals(oldId, movedId);
            assertTrue(s.store.isEmptyCollapsePending(movedId));
            s.verify();
            s.result(middle, 0, true);
            assertEquals(LEAF, s.type(middle));
            assertFalse(s.store.isEmptyCollapsePending(movedId));
            assertTrue(s.store.getAllChildrenAreLeaf(s.id(root)));
            s.verify();
        }
    }

    @Test
    void newerNonzeroMaskCancelsDeferredEmptyCollapse() {
        try (var s = new Round11NodeTestSupport()) {
            long root = pos(2, 0, 0, 0);
            s.insert(root, 1);
            s.split(root, 1);
            s.manager.removeNodeGeometry(root);
            s.manager.processChildChange(root, (byte) 0);
            s.manager.processChildChange(root, (byte) 3);
            assertFalse(s.store.isEmptyCollapsePending(s.id(root)));
            s.result(root, 3, false);
            s.result(child(root, 1), 1, false);
            assertEquals(INNER, s.type(root));
            assertEquals(3, Byte.toUnsignedInt(s.store.getNodeChildExistence(s.id(root))));
            assertTrue(s.active.containsKey(child(root, 0)));
            assertTrue(s.active.containsKey(child(root, 1)));
            s.verify();
        }
    }

    @Test
    void innerNodeCanReplaceAllMaterializedChildrenWithPendingChildren() {
        try (var s = new Round11NodeTestSupport()) {
            long root = pos(2, 0, 0, 0);
            s.insert(root, 1);
            s.split(root, 1);
            s.manager.processChildChange(root, (byte) 0x80);
            assertEquals(NodeManager.SENTINEL_EMPTY_CHILD_PTR, s.store.getChildPtr(s.id(root)));
            assertFalse(s.store.getAllChildrenAreLeaf(s.id(root)));
            s.verify();
            s.result(child(root, 7), 1, false);
            assertEquals(1, s.store.getChildPtrCount(s.id(root)));
            assertTrue(s.store.getAllChildrenAreLeaf(s.id(root)));
            s.verify();
        }
    }

    @Test
    void clearRetiresParkedPartialDeferredAndMaterializedOwners() {
        try (var s = new Round11NodeTestSupport()) {
            long parked = pos(2, 0, 0, 0);
            long partial = pos(2, 1, 0, 0);
            long deferred = pos(2, 2, 0, 0);
            s.insert(parked, 0);
            s.manager.processRequest(parked);
            s.insert(partial, 3);
            s.manager.processRequest(partial);
            s.result(child(partial, 0), 1, false);
            s.insert(deferred, 1);
            s.split(deferred, 1);
            s.manager.removeNodeGeometry(deferred);
            s.manager.processChildChange(deferred, (byte) 0);
            s.verify();
            NodeManager.DiagnosticSnapshot snapshot = s.manager.diagnosticSnapshot();
            assertEquals(3, snapshot.topLevelPositions().size());
            assertThrows(UnsupportedOperationException.class, () -> snapshot.positions().clear());
            s.manager.clear();
            NodeManager.DiagnosticSnapshot empty = s.manager.diagnosticSnapshot();
            assertTrue(empty.positions().isEmpty());
            assertTrue(empty.nodeIds().isEmpty());
            assertTrue(empty.singleRequestIds().isEmpty());
            assertTrue(empty.childRequestIds().isEmpty());
            assertTrue(empty.geometryIds().isEmpty());
            assertTrue(empty.pendingNodeUpdates().isEmpty());
            s.verify();
        }
    }
}
