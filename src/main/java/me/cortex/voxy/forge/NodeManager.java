package me.cortex.voxy.forge;

import it.unimi.dsi.fastutil.ints.IntConsumer;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import me.cortex.voxy.common.Logger;
import me.cortex.voxy.common.world.WorldEngine;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static me.cortex.voxy.common.world.WorldEngine.MAX_LOD_LAYER;
import static me.cortex.voxy.common.world.WorldEngine.UPDATE_TYPE_BLOCK_BIT;

final class NodeManager {
    static final int NULL_GEOMETRY_ID = -1;
    static final int EMPTY_GEOMETRY_ID = -2;
    static final int NULL_REQUEST_ID = NodeStore.REQUEST_ID_MSK;
    static final int SENTINEL_EMPTY_CHILD_PTR = NodeStore.NODE_ID_MSK - 1;

    private static final boolean VERIFY_NODE_MANAGER_OPERATIONS = true;
    private static final int NODE_ID_MSK = (1 << 24) - 1;
    private static final int NODE_TYPE_MSK = 0b11 << 30;
    private static final int NODE_TYPE_LEAF = 0b00 << 30;
    private static final int NODE_TYPE_INNER = 0b01 << 30;
    private static final int NODE_TYPE_REQUEST = 0b10 << 30;
    private static final int REQUEST_TYPE_SINGLE = 0b0 << 29;
    private static final int REQUEST_TYPE_CHILD = 0b1 << 29;
    private static final int REQUEST_TYPE_MSK = 0b1 << 29;

    private final ExpandingObjectAllocationList<ForgeOriginalVoxySingleNodeRequest> singleRequests =
            new ExpandingObjectAllocationList<>(ForgeOriginalVoxySingleNodeRequest[]::new, NodeStore.REQUEST_ID_MSK);
    private final ExpandingObjectAllocationList<ForgeOriginalVoxyNodeChildRequest> childRequests =
            new ExpandingObjectAllocationList<>(ForgeOriginalVoxyNodeChildRequest[]::new, NodeStore.REQUEST_ID_MSK);
    private final IntOpenHashSet nodeUpdates = new IntOpenHashSet();
    private final BasicAsyncGeometryManager geometryManager;
    private final ISectionWatcher watcher;
    private final Long2IntOpenHashMap activeSectionMap = new Long2IntOpenHashMap();
    private final NodeStore nodeData;
    private final IntOpenHashSet topLevelNodeIds = new IntOpenHashSet();
    private final LongOpenHashSet topLevelNodes = new LongOpenHashSet();
    private int activeNodeRequestCount;
    private IntConsumer topLevelNodeIdAddedCallback;
    private IntConsumer topLevelNodeIdRemovedCallback;
    private Cleaner cleanerInterface;

    interface Cleaner {
        void alloc(int id);

        void move(int from, int to);

        void free(int id);
    }

    NodeManager(
            int maxNodeCount,
            BasicAsyncGeometryManager geometryManager,
            ISectionWatcher watcher) {
        if ((maxNodeCount & (maxNodeCount - 1)) != 0) {
            throw new IllegalArgumentException("Max node count must be a power of 2");
        }
        if (maxNodeCount > (1 << 24)) {
            throw new IllegalArgumentException("Max node count cannot exceed 2^24");
        }
        this.activeSectionMap.defaultReturnValue(-1);
        this.geometryManager = geometryManager;
        this.watcher = watcher;
        this.nodeData = new NodeStore(maxNodeCount);
    }

    void setClear(Cleaner callback) {
        this.cleanerInterface = callback;
    }

    void setTLNCallbacks(IntConsumer onAdd, IntConsumer onRemove) {
        this.topLevelNodeIdAddedCallback = onAdd;
        this.topLevelNodeIdRemovedCallback = onRemove;
    }

    boolean insertTopLevelNode(long pos) {
        assertPosValid(pos);
        if (this.activeSectionMap.containsKey(pos)) {
            Logger.error("Tried inserting top level pos " + WorldEngine.pprintPos(pos) + " but it was in active map, discarding!");
            return false;
        }
        ForgeOriginalVoxySingleNodeRequest request = new ForgeOriginalVoxySingleNodeRequest(pos);
        int id = this.singleRequests.put(request);
        this.watcher.watch(pos, WorldEngine.DEFAULT_UPDATE_FLAGS);
        this.activeSectionMap.put(pos, id | NODE_TYPE_REQUEST | REQUEST_TYPE_SINGLE);
        this.topLevelNodes.add(pos);
        return true;
    }

    void removeTopLevelNode(long pos) {
        if (!this.topLevelNodes.remove(pos)) {
            throw new IllegalStateException("Position not in top level map: " + WorldEngine.pprintPos(pos));
        }
        int nodeId = this.activeSectionMap.get(pos);
        if (nodeId == -1) {
            throw new IllegalStateException("Tried removing top level pos " + WorldEngine.pprintPos(pos) + " but it was not in active map");
        }
        if ((nodeId & NODE_TYPE_MSK) != NODE_TYPE_REQUEST) {
            int id = nodeId & NODE_ID_MSK;
            if (!this.topLevelNodeIds.remove(id)) {
                throw new IllegalStateException("Node id was not in top level node ids: " + nodeId);
            }
            if (this.topLevelNodeIdRemovedCallback != null) {
                this.topLevelNodeIdRemovedCallback.accept(id);
            }
        }
        this.recurseRemoveNode(pos);
    }

    boolean processGeometryResult(BuiltSection sectionResult) {
        long pos = sectionResult.position;
        int nodeId = this.activeSectionMap.get(pos);
        if (nodeId == -1) {
            return false;
        }
        if ((nodeId & NODE_TYPE_MSK) == NODE_TYPE_REQUEST) {
            if ((nodeId & REQUEST_TYPE_MSK) == REQUEST_TYPE_SINGLE) {
                ForgeOriginalVoxySingleNodeRequest request = this.singleRequests.get(nodeId & NODE_ID_MSK);
                request.setMesh(this.uploadReplaceSection(request.getMesh(), sectionResult));
                if (!request.hasChildExistenceSet()) {
                    request.setChildExistence(sectionResult.childExistence);
                }
                if (request.isSatisfied()) {
                    this.finishRequest(request);
                }
            } else if ((nodeId & REQUEST_TYPE_MSK) == REQUEST_TYPE_CHILD) {
                ForgeOriginalVoxyNodeChildRequest request = this.childRequests.get(nodeId & NODE_ID_MSK);
                int childId = getChildIdx(pos);
                request.setChildMesh(childId, this.uploadReplaceSection(request.getChildMesh(childId), sectionResult));
                if (!request.hasChildChildExistence(childId)) {
                    request.setChildChildExistence(childId, sectionResult.childExistence);
                }
                if (request.isSatisfied()) {
                    this.finishRequest(nodeId & NODE_ID_MSK, request);
                }
            } else {
                throw new IllegalStateException();
            }
            return true;
        }
        if ((nodeId & NODE_TYPE_MSK) != NODE_TYPE_INNER && (nodeId & NODE_TYPE_MSK) != NODE_TYPE_LEAF) {
            throw new IllegalStateException();
        }
        nodeId &= NODE_ID_MSK;
        if ((this.watcher.get(pos) & UPDATE_TYPE_BLOCK_BIT) == 0) {
            if (this.nodeData.isNodeGeometryInFlight(nodeId)) {
                throw new IllegalStateException();
            }
            Logger.warn("Recieved geometry update but not watching it, discarding");
            return false;
        }
        int geometryChange = this.updateNodeGeometry(nodeId, sectionResult);
        // Upload failure consumes the incoming BuiltSection but retains the old mesh.
        // Retain the corresponding in-flight node state as well until upload succeeds.
        this.nodeData.unmarkNodeGeometryInFlight(nodeId);
        if (geometryChange != 0) {
            this.invalidateNode(nodeId);
        }
        if (this.nodeData.isEmptyCollapsePending(nodeId)) {
            // The replacement is now a real mesh or a real EMPTY result. Only now may
            // the last valid child coverage and its watches be retired.
            this.nodeData.setEmptyCollapsePending(nodeId, false);
            this.updateChildSectionsInner(pos, nodeId, (byte) 0);
        }
        return true;
    }

    void processChildChange(long pos, byte childExistence) {
        int nodeId = this.activeSectionMap.get(pos);
        if (nodeId == -1) {
            Logger.warn("Got child change for pos " + WorldEngine.pprintPos(pos) + " but it was not in active map, ignoring!");
            return;
        }
        if ((nodeId & NODE_TYPE_MSK) == NODE_TYPE_REQUEST) {
            if ((nodeId & REQUEST_TYPE_MSK) == REQUEST_TYPE_SINGLE) {
                ForgeOriginalVoxySingleNodeRequest request = this.singleRequests.get(nodeId & NODE_ID_MSK);
                request.setChildExistence(childExistence);
                if (request.isSatisfied()) {
                    this.finishRequest(request);
                }
            } else if ((nodeId & REQUEST_TYPE_MSK) == REQUEST_TYPE_CHILD) {
                ForgeOriginalVoxyNodeChildRequest request = this.childRequests.get(nodeId & NODE_ID_MSK);
                request.setChildChildExistence(getChildIdx(pos), childExistence);
                if (request.isSatisfied()) {
                    this.finishRequest(nodeId & NODE_ID_MSK, request);
                }
            } else {
                throw new IllegalStateException();
            }
        } else if ((nodeId & NODE_TYPE_MSK) == NODE_TYPE_INNER) {
            this.updateChildSectionsInner(pos, nodeId & NODE_ID_MSK, childExistence);
        } else if ((nodeId & NODE_TYPE_MSK) == NODE_TYPE_LEAF) {
            this.updateChildSectionsLeaf(pos, nodeId & NODE_ID_MSK, childExistence);
        }
    }

    void processRequest(long pos) {
        int nodeId = this.activeSectionMap.get(pos);
        if (nodeId == -1) {
            return;
        }
        int nodeType = nodeId & NODE_TYPE_MSK;
        nodeId &= NODE_ID_MSK;
        if (nodeType == NODE_TYPE_REQUEST) {
            Logger.error("Tried processing request for pos: " + WorldEngine.pprintPos(pos) + " but its type was a request, ignoring!");
            return;
        }
        if (nodeType != NODE_TYPE_LEAF && nodeType != NODE_TYPE_INNER) {
            throw new IllegalStateException("Unknown node type: " + nodeType);
        }
        if (WorldEngine.getLevel(pos) == 0) {
            Logger.error("Requests cannot exist for bottom level nodes. at: " + WorldEngine.pprintPos(pos) + ". Ignoring request");
            return;
        }
        if (nodeType == NODE_TYPE_LEAF) {
            if (this.nodeData.getNodeGeometry(nodeId) == NULL_GEOMETRY_ID) {
                Logger.warn("Got request for leaf that doesnt have geometry, this should not be possible at pos " + WorldEngine.pprintPos(pos));
                if (!this.watcher.watch(pos, WorldEngine.UPDATE_TYPE_BLOCK_BIT)) {
                    Logger.warn("Node: " + nodeId + " at pos: " + WorldEngine.pprintPos(pos) + " got update request, but geometry was already being watched");
                }
                return;
            }
            if (this.nodeData.isNodeRequestInFlight(nodeId)) {
                Logger.warn("Tried processing a node that already has a request in flight: " + nodeId + " pos: " + WorldEngine.pprintPos(pos) + " ignoring");
                return;
            }
            this.nodeData.markRequestInFlight(nodeId);
            this.makeLeafChildRequest(nodeId);
        } else {
            this.processInnerRequest(pos, nodeId);
        }
    }

    void removeNodeGeometry(long pos) {
        int nodeId = this.activeSectionMap.get(pos);
        if (nodeId == -1) {
            return;
        }
        int nodeType = nodeId & NODE_TYPE_MSK;
        nodeId &= NODE_ID_MSK;
        if (nodeType == NODE_TYPE_REQUEST) {
            return;
        }
        if (nodeType == NODE_TYPE_INNER) {
            this.clearGeometryInternal(pos, nodeId);
        } else {
            if (this.topLevelNodes.contains(pos)) {
                int geo = this.nodeData.getNodeGeometry(nodeId);
                if (geo != NULL_GEOMETRY_ID && geo != EMPTY_GEOMETRY_ID) {
                    Logger.warn("Tried removing geometry from top level node which is not allowed, disregarding request");
                }
                return;
            }
            this.processLeafGeometryRemoval(pos);
        }
    }

    IntOpenHashSet getNodeUpdates() {
        return this.nodeUpdates;
    }

    void writeNode(int node, long address) {
        this.nodeData.writeNode(address, node);
    }

    int getCurrentMaxNodeId() {
        return this.nodeData.getEndNodeId();
    }

    /** Single-owner shutdown, after the async worker has stopped. */
    void clear() {
        for (long position : this.topLevelNodes.toLongArray()) {
            this.removeTopLevelNode(position);
        }
        this.verifyIntegrity();
        if (!this.activeSectionMap.isEmpty() || this.nodeData.getNodeCount() != 0
                || this.singleRequests.count() != 0 || this.childRequests.count() != 0
                || !this.topLevelNodeIds.isEmpty() || this.activeNodeRequestCount != 0) {
            throw new IllegalStateException("Node hierarchy retained owners after shutdown");
        }
        // No future GPU consumer exists during shutdown. Removal callbacks still run,
        // but retaining tombstone uploads here would only preserve dead work.
        this.nodeUpdates.clear();
    }

    record NodeState(int type, int id, int geometryId, int childMask, int childPointer,
                     int childCount, int requestId, int requestMask, boolean requestInFlight,
                     boolean geometryInFlight, boolean allChildrenLeaf, boolean emptyCollapsePending) { }

    record DiagnosticSnapshot(Map<Long, NodeState> positions, Set<Integer> nodeIds,
                              Set<Integer> singleRequestIds, Set<Integer> childRequestIds,
                              Set<Integer> geometryIds, Set<Long> topLevelPositions,
                              Set<Integer> topLevelIds, Set<Integer> pendingNodeUpdates,
                              int activeChildRequestCount) { }

    /** Immutable on-demand view; call only on the node owner thread or after it has stopped. */
    DiagnosticSnapshot diagnosticSnapshot() {
        Map<Long, NodeState> positions = new HashMap<>();
        Set<Integer> nodes = new HashSet<>();
        Set<Integer> singles = new HashSet<>();
        Set<Integer> children = new HashSet<>();
        Set<Integer> geometry = new HashSet<>();
        for (var entry : this.activeSectionMap.long2IntEntrySet()) {
            long pos = entry.getLongKey();
            int encoded = entry.getIntValue();
            int type = encoded & NODE_TYPE_MSK;
            int id = encoded & NODE_ID_MSK;
            NodeState state;
            if (type == NODE_TYPE_REQUEST) {
                if ((encoded & REQUEST_TYPE_MSK) == REQUEST_TYPE_SINGLE) {
                    ForgeOriginalVoxySingleNodeRequest request = this.singleRequests.get(id);
                    singles.add(id);
                    state = new NodeState(type | REQUEST_TYPE_SINGLE, id, request.getMesh(),
                            request.hasChildExistenceSet() ? Byte.toUnsignedInt(request.getChildExistence()) : -1,
                            -1, 0, id, 0, true, !request.hasMeshSet(), false, false);
                } else {
                    ForgeOriginalVoxyNodeChildRequest request = this.childRequests.get(id);
                    int index = getChildIdx(pos);
                    children.add(id);
                    state = new NodeState(type | REQUEST_TYPE_CHILD, id, request.getChildMesh(index),
                            request.hasChildChildExistence(index) ? Byte.toUnsignedInt(request.getChildChildExistence(index)) : -1,
                            -1, 0, id, Byte.toUnsignedInt(request.getMsk()), true,
                            request.getChildMesh(index) == NULL_GEOMETRY_ID, false, false);
                }
            } else {
                nodes.add(id);
                int requestId = this.nodeData.getNodeRequest(id);
                int requestMask = 0;
                if (this.nodeData.isNodeRequestInFlight(id)) {
                    children.add(requestId);
                    requestMask = Byte.toUnsignedInt(this.childRequests.get(requestId).getMsk());
                }
                int ptr = this.nodeData.getChildPtr(id);
                state = new NodeState(type, id, this.nodeData.getNodeGeometry(id),
                        Byte.toUnsignedInt(this.nodeData.getNodeChildExistence(id)), ptr,
                        ptr == -1 || ptr == SENTINEL_EMPTY_CHILD_PTR ? 0 : this.nodeData.getChildPtrCount(id),
                        requestId, requestMask, this.nodeData.isNodeRequestInFlight(id),
                        this.nodeData.isNodeGeometryInFlight(id), this.nodeData.getAllChildrenAreLeaf(id),
                        this.nodeData.isEmptyCollapsePending(id));
            }
            if (state.geometryId() >= 0 && !geometry.add(state.geometryId())) {
                throw new IllegalStateException("Geometry has more than one node/request owner: " + state.geometryId());
            }
            positions.put(pos, state);
        }
        return new DiagnosticSnapshot(Map.copyOf(positions), Set.copyOf(nodes), Set.copyOf(singles),
                Set.copyOf(children), Set.copyOf(geometry), Set.copyOf(this.topLevelNodes),
                Set.copyOf(this.topLevelNodeIds), Set.copyOf(this.nodeUpdates), this.activeNodeRequestCount);
    }

    private void updateChildSectionsLeaf(long pos, int nodeId, byte childExistence) {
        // Match the inner-node route: completion observes the new topology mask, not
        // the superseded one that the incoming child notification is replacing.
        this.nodeData.setNodeChildExistence(nodeId, childExistence);
        if (this.nodeData.isNodeRequestInFlight(nodeId)) {
            int requestId = this.nodeData.getNodeRequest(nodeId);
            ForgeOriginalVoxyNodeChildRequest request = this.childRequests.get(requestId);
            if (request.getPosition() != pos) {
                throw new IllegalStateException("Request is not at pos");
            }
            byte oldMask = request.getMsk();
            byte change = (byte) (oldMask ^ childExistence);
            byte remove = (byte) (change & oldMask);
            for (int i = 0; i < 8; i++) {
                if ((remove & (1 << i)) == 0) {
                    continue;
                }
                long childPos = makeChildPos(pos, i);
                int meshId = request.removeAndUnRequire(i);
                if (meshId != NULL_GEOMETRY_ID && meshId != EMPTY_GEOMETRY_ID) {
                    this.removeGeometryCached(childPos, meshId);
                }
                if (this.activeSectionMap.remove(childPos) != (requestId | NODE_TYPE_REQUEST | REQUEST_TYPE_CHILD)) {
                    throw new IllegalStateException("Child position did not belong to its cancelling request");
                }
                if (!this.watcher.unwatch(childPos, WorldEngine.DEFAULT_UPDATE_FLAGS)) {
                    throw new IllegalStateException("Child pos was not being watched");
                }
            }
            byte add = (byte) (change & childExistence);
            for (int i = 0; i < 8; i++) {
                if ((add & (1 << i)) == 0) {
                    continue;
                }
                request.addChildRequirement(i);
                long childPos = makeChildPos(pos, i);
                if (this.activeSectionMap.put(childPos, requestId | NODE_TYPE_REQUEST | REQUEST_TYPE_CHILD) != -1) {
                    throw new IllegalStateException("Child pos was already in active section tracker but was part of a request");
                }
                if (!this.watcher.watch(childPos, WorldEngine.DEFAULT_UPDATE_FLAGS)) {
                    throw new IllegalStateException("Child pos update router issue");
                }
            }
            if (request.isSatisfied()) {
                this.finishRequest(requestId, request);
            }
        }
        this.invalidateNode(nodeId);
    }

    private void updateChildSectionsInner(long pos, int nodeId, byte childExistence) {
        if (childExistence == 0 && this.nodeData.getNodeGeometry(nodeId) == NULL_GEOMETRY_ID) {
            // Upstream replaced NULL with EMPTY after deleting the children. That loses
            // visible coverage and confuses missing data with an accepted empty mesh.
            // Keep the last complete topology until the requested replacement arrives.
            this.nodeData.setEmptyCollapsePending(nodeId, true);
            this.processInnerRequest(pos, nodeId);
            return;
        }
        this.nodeData.setEmptyCollapsePending(nodeId, false);
        byte existence = this.nodeData.getNodeChildExistence(nodeId);
        byte add = (byte) ((existence ^ childExistence) & childExistence);
        if (add != 0) {
            if (!this.nodeData.isNodeRequestInFlight(nodeId)) {
                ForgeOriginalVoxyNodeChildRequest request = new ForgeOriginalVoxyNodeChildRequest(pos);
                int requestId = this.childRequests.put(request);
                this.nodeData.markRequestInFlight(nodeId);
                this.nodeData.setNodeRequest(nodeId, requestId);
                this.activeNodeRequestCount++;
            }
            int requestId = this.nodeData.getNodeRequest(nodeId);
            ForgeOriginalVoxyNodeChildRequest request = this.childRequests.get(requestId);
            if (request.getPosition() != pos) {
                throw new IllegalStateException("Request is not at pos");
            }
            for (int i = 0; i < 8; i++) {
                if ((add & (1 << i)) == 0) {
                    continue;
                }
                request.addChildRequirement(i);
                long childPos = makeChildPos(pos, i);
                if (this.activeSectionMap.put(childPos, requestId | NODE_TYPE_REQUEST | REQUEST_TYPE_CHILD) != -1) {
                    throw new IllegalStateException("Child pos was already in active section tracker but was part of a request");
                }
                if (!this.watcher.watch(childPos, WorldEngine.DEFAULT_UPDATE_FLAGS)) {
                    throw new IllegalStateException("Child pos update router issue");
                }
            }
        }

        this.nodeData.setNodeChildExistence(nodeId, childExistence);
        int remove = ((existence ^ childExistence) & existence) & 0xFF;
        if (remove != 0) {
            if (this.nodeData.isNodeRequestInFlight(nodeId)) {
                int requestId = this.nodeData.getNodeRequest(nodeId);
                ForgeOriginalVoxyNodeChildRequest request = this.childRequests.get(requestId);
                if (request.getPosition() != pos) {
                    throw new IllegalStateException("Request is not at pos");
                }
                int reqRemove = Byte.toUnsignedInt(request.getMsk()) & remove;
                for (int i = 0; i < 8; i++) {
                    if ((reqRemove & (1 << i)) == 0) {
                        continue;
                    }
                    long childPos = makeChildPos(pos, i);
                    int meshId = request.removeAndUnRequire(i);
                    if (meshId != NULL_GEOMETRY_ID && meshId != EMPTY_GEOMETRY_ID) {
                        this.removeGeometryCached(childPos, meshId);
                    }
                    int childNodeId = this.activeSectionMap.remove(childPos);
                    if (childNodeId != (requestId | NODE_TYPE_REQUEST | REQUEST_TYPE_CHILD)) {
                        throw new IllegalStateException("Child position did not belong to its cancelling request");
                    }
                    if (!this.watcher.unwatch(childPos, WorldEngine.DEFAULT_UPDATE_FLAGS)) {
                        throw new IllegalStateException("Child pos was not being watched");
                    }
                }
                remove ^= reqRemove;
            }
            if (remove != 0) {
                this.compactExistingChildrenAfterRemoval(pos, nodeId, childExistence, remove);
            }
            if (this.nodeData.isNodeRequestInFlight(nodeId)) {
                int requestId = this.nodeData.getNodeRequest(nodeId);
                ForgeOriginalVoxyNodeChildRequest request = this.childRequests.get(requestId);
                if (request.getPosition() != pos) {
                    throw new IllegalStateException("Request is not at pos");
                }
                if (request.isSatisfied()) {
                    this.finishRequest(requestId, request);
                }
            }
        }
        if (childExistence == 0) {
            this.transformInnerToLeaf(pos, nodeId);
        }
        this.invalidateNode(nodeId);
    }

    private void compactExistingChildrenAfterRemoval(long pos, int nodeId, byte childExistence, int remove) {
        int oldPtr = this.nodeData.getChildPtr(nodeId);
        int oldCount = this.nodeData.getChildPtrCount(nodeId);
        if (oldPtr == -1) {
            throw new IllegalStateException();
        }
        int oldExistence = 0;
        for (int i = 0; i < oldCount; i++) {
            if (!this.nodeData.nodeExists(i + oldPtr)) {
                throw new IllegalStateException();
            }
            oldExistence |= 1 << getChildIdx(this.nodeData.nodePosition(i + oldPtr));
        }
        if ((remove & oldExistence) != remove) {
            throw new IllegalStateException();
        }
        int remaining = remove ^ oldExistence;
        if (remaining == 0) {
            if (childExistence != 0 && !this.nodeData.isNodeRequestInFlight(nodeId)) {
                throw new IllegalStateException();
            }
            this.nodeData.setAllChildrenAreLeaf(nodeId, false);
            this.nodeData.setChildPtr(nodeId, SENTINEL_EMPTY_CHILD_PTR);
            this.nodeData.setChildPtrCount(nodeId, 8);
            for (int i = 0; i < 8; i++) {
                if ((remove & (1 << i)) == 0) {
                    continue;
                }
                this.recurseRemoveNode(makeChildPos(pos, i));
            }
        } else {
            int newCount = Integer.bitCount(remaining);
            int newPtr = this.nodeData.allocate(newCount);
            int prevChildId = oldPtr - 1;
            int newChildId = newPtr - 1;
            boolean allChildNodesLeaf = true;
            for (int i = 0; i < 8; i++) {
                if ((oldExistence & (1 << i)) == 0) {
                    continue;
                }
                prevChildId++;
                if ((remove & (1 << i)) != 0) {
                    this.recurseRemoveNode(makeChildPos(pos, i));
                } else {
                    newChildId++;
                    long childPos = this.nodeData.nodePosition(prevChildId);
                    if (childPos != makeChildPos(pos, i)) {
                        throw new IllegalStateException();
                    }
                    this.nodeData.copyNode(prevChildId, newChildId);
                    this.clearAllocId(newChildId);
                    this.clearMoveId(prevChildId, newChildId);
                    this.clearFreeId(prevChildId);
                    int prevNodeId = this.activeSectionMap.get(childPos);
                    if ((prevNodeId & NODE_TYPE_MSK) == NODE_TYPE_REQUEST) {
                        throw new IllegalStateException();
                    }
                    if ((prevNodeId & NODE_ID_MSK) != prevChildId) {
                        throw new IllegalStateException("State inconsistency");
                    }
                    allChildNodesLeaf &= (prevNodeId & NODE_TYPE_MSK) == NODE_TYPE_LEAF;
                    this.activeSectionMap.put(childPos, (prevNodeId & NODE_TYPE_MSK) | newChildId);
                    this.nodeData.free(prevChildId);
                    this.invalidateNode(prevChildId);
                    this.invalidateNode(newChildId);
                }
            }
            this.nodeData.setAllChildrenAreLeaf(nodeId, allChildNodesLeaf);
            this.nodeData.setChildPtr(nodeId, newPtr);
            this.nodeData.setChildPtrCount(nodeId, newCount);
            if (VERIFY_NODE_MANAGER_OPERATIONS) {
                for (int i = 0; i < oldCount; i++) {
                    if (this.nodeData.nodeExists(i + oldPtr)) {
                        throw new IllegalStateException();
                    }
                }
            }
        }
        this.invalidateNode(nodeId);
    }

    private void transformInnerToLeaf(long pos, int nodeId) {
        if (this.nodeData.isNodeRequestInFlight(nodeId)) {
            throw new IllegalStateException();
        }
        if (this.nodeData.getNodeGeometry(nodeId) == NULL_GEOMETRY_ID) {
            throw new IllegalStateException("Cannot retire child coverage without a completed parent geometry result");
        }
        if (this.nodeData.getChildPtr(nodeId) != SENTINEL_EMPTY_CHILD_PTR) {
            throw new IllegalStateException();
        }
        this.nodeData.setChildPtr(nodeId, -1);
        this.activeSectionMap.put(pos, NODE_TYPE_LEAF | nodeId);
        this.nodeData.setAllChildrenAreLeaf(nodeId, false);
        this.nodeData.setEmptyCollapsePending(nodeId, false);
        this.invalidateNode(nodeId);
        this.refreshContainingParentLeafFlag(pos);
    }

    private void recurseRemoveChildNodes(long pos) {
        this.recurseRemoveNode(pos, true);
    }

    private void recurseRemoveNode(long pos) {
        this.recurseRemoveNode(pos, false);
    }

    private void recurseRemoveNode(long pos, boolean onlyRemoveChildren) {
        int nodeId = onlyRemoveChildren ? this.activeSectionMap.get(pos) : this.activeSectionMap.remove(pos);
        if (nodeId == -1) {
            throw new IllegalStateException("Cannot remove pos that doesnt exist");
        }
        int type = nodeId & NODE_TYPE_MSK;
        if (type == NODE_TYPE_INNER || type == NODE_TYPE_LEAF) {
            nodeId &= NODE_ID_MSK;
            if (!this.nodeData.nodeExists(nodeId)) {
                throw new IllegalStateException("Node exists in section map but not in nodeData");
            }
            byte childExistence = this.nodeData.getNodeChildExistence(nodeId);
            if (this.nodeData.isNodeRequestInFlight(nodeId)) {
                int reqId = this.nodeData.getNodeRequest(nodeId);
                ForgeOriginalVoxyNodeChildRequest req = this.childRequests.get(reqId);
                childExistence ^= req.getMsk();
                this.removeRequest(reqId, req, pos);
                if (onlyRemoveChildren) {
                    this.nodeData.unmarkRequestInFlight(nodeId);
                    this.nodeData.setNodeRequest(nodeId, NULL_REQUEST_ID);
                }
            }
            if (type == NODE_TYPE_INNER) {
                this.verifyInnerChildrenBeforeRemoval(pos, nodeId, childExistence);
                for (int i = 0; i < 8; i++) {
                    if ((childExistence & (1 << i)) != 0) {
                        this.recurseRemoveNode(makeChildPos(pos, i));
                    }
                }
                if (onlyRemoveChildren) {
                    this.nodeData.setChildPtr(nodeId, -1);
                }
            }
            if (!onlyRemoveChildren) {
                int meshId = this.nodeData.getNodeGeometry(nodeId);
                if (meshId != EMPTY_GEOMETRY_ID && meshId != NULL_GEOMETRY_ID) {
                    this.removeGeometryCached(pos, meshId);
                }
                this.nodeData.free(nodeId);
                this.clearFreeId(nodeId);
                this.invalidateNode(nodeId);
                if (!this.watcher.unwatch(pos, WorldEngine.DEFAULT_UPDATE_FLAGS)) {
                    throw new IllegalStateException("Pos was not being watched");
                }
            } else {
                this.nodeData.setAllChildrenAreLeaf(nodeId, false);
                this.invalidateNode(nodeId);
            }
        } else if (type == NODE_TYPE_REQUEST) {
            if (!this.watcher.unwatch(pos, WorldEngine.DEFAULT_UPDATE_FLAGS)) {
                throw new IllegalStateException("Pos was not being watched");
            }
            if ((nodeId & REQUEST_TYPE_MSK) == REQUEST_TYPE_SINGLE) {
                int requestId = nodeId & NODE_ID_MSK;
                ForgeOriginalVoxySingleNodeRequest req = this.singleRequests.get(requestId);
                if (req.getPosition() != pos) {
                    throw new IllegalStateException();
                }
                this.singleRequests.release(requestId);
                if (req.hasMeshSet()) {
                    int meshId = req.getMesh();
                    if (meshId != EMPTY_GEOMETRY_ID && meshId != NULL_GEOMETRY_ID) {
                        this.removeGeometryCached(pos, meshId);
                    }
                }
            } else {
                int requestId = nodeId & NODE_ID_MSK;
                ForgeOriginalVoxyNodeChildRequest req = this.childRequests.get(requestId);
                if (req.getPosition() != pos) {
                    throw new IllegalStateException();
                }
                this.removeRequest(requestId, req, pos);
            }
        } else {
            throw new IllegalStateException();
        }
    }

    private void removeRequest(int requestId, ForgeOriginalVoxyNodeChildRequest request, long pos) {
        for (int i = 0; i < 8; i++) {
            if ((request.getMsk() & (1 << i)) == 0) {
                continue;
            }
            long childPos = makeChildPos(pos, i);
            int meshId = request.getChildMesh(i);
            if (meshId != EMPTY_GEOMETRY_ID && meshId != NULL_GEOMETRY_ID) {
                this.removeGeometryCached(childPos, meshId);
            }
            int childId = this.activeSectionMap.remove(childPos);
            if (childId == -1) {
                throw new IllegalStateException("Child not in activeMap");
            }
            if ((childId & NODE_TYPE_MSK) != NODE_TYPE_REQUEST
                    || (childId & REQUEST_TYPE_MSK) != REQUEST_TYPE_CHILD
                    || (childId & NODE_ID_MSK) != requestId) {
                throw new IllegalStateException("Invalid child active state map: " + childId);
            }
            if (!this.watcher.unwatch(childPos, WorldEngine.DEFAULT_UPDATE_FLAGS)) {
                throw new IllegalStateException("Pos was not being watched");
            }
        }
        this.childRequests.release(requestId);
        this.activeNodeRequestCount--;
    }

    private void finishRequest(ForgeOriginalVoxySingleNodeRequest request) {
        int requestEntry = this.activeSectionMap.get(request.getPosition());
        if ((requestEntry & (NODE_TYPE_MSK | REQUEST_TYPE_MSK)) != (NODE_TYPE_REQUEST | REQUEST_TYPE_SINGLE)
                || this.singleRequests.get(requestEntry & NODE_ID_MSK) != request) {
            throw new IllegalStateException("Single request has no matching position owner");
        }
        // Allocation may fail. Keep the request and its already uploaded mesh owned until
        // a node slot is successfully acquired, so failure cleanup can still retire them.
        int id = this.nodeData.allocate();
        this.nodeData.setNodePosition(id, request.getPosition());
        this.nodeData.setNodeGeometry(id, request.getMesh());
        this.nodeData.setNodeChildExistence(id, request.getChildExistence());
        this.activeSectionMap.put(request.getPosition(), id | NODE_TYPE_LEAF);
        this.singleRequests.release(requestEntry & NODE_ID_MSK);
        this.invalidateNode(id);
        if (!this.topLevelNodeIds.add(id)) {
            throw new IllegalStateException();
        }
        this.clearAllocId(id);
        if (this.topLevelNodeIdAddedCallback != null) {
            this.topLevelNodeIdAddedCallback.accept(id);
        }
    }

    private void finishRequest(int requestId, ForgeOriginalVoxyNodeChildRequest request) {
        int parentNodeId = this.activeSectionMap.get(request.getPosition());
        if (parentNodeId == -1 || (parentNodeId & NODE_TYPE_MSK) == NODE_TYPE_REQUEST) {
            throw new IllegalStateException("finishRequest tried to finish for missing/request parent " + WorldEngine.pprintPos(request.getPosition()));
        }
        int parentNodeType = parentNodeId & NODE_TYPE_MSK;
        parentNodeId &= NODE_ID_MSK;
        if (!this.nodeData.isNodeRequestInFlight(parentNodeId)
                || this.nodeData.getNodeRequest(parentNodeId) != requestId
                || this.childRequests.get(requestId) != request || !request.isSatisfied()) {
            throw new IllegalStateException("Completing child request has no matching satisfied parent owner");
        }
        if (request.getMsk() == 0) {
            this.childRequests.release(requestId);
            this.nodeData.setNodeRequest(parentNodeId, NULL_REQUEST_ID);
            this.nodeData.unmarkRequestInFlight(parentNodeId);
            this.activeNodeRequestCount--;
            this.invalidateNode(parentNodeId);
            return;
        }
        if (parentNodeType == NODE_TYPE_LEAF) {
            this.finishLeafChildRequest(requestId, request, parentNodeId);
        } else if (parentNodeType == NODE_TYPE_INNER) {
            this.finishInnerChildRequest(requestId, request, parentNodeId);
        } else {
            throw new IllegalStateException();
        }
    }

    private void finishLeafChildRequest(int requestId, ForgeOriginalVoxyNodeChildRequest request, int parentNodeId) {
        int mask = Byte.toUnsignedInt(request.getMsk());
        if (mask != Byte.toUnsignedInt(this.nodeData.getNodeChildExistence(parentNodeId))) {
            throw new IllegalStateException("Leaf request mask does not match parent topology");
        }
        int base = this.nodeData.allocate(Integer.bitCount(mask));
        int offset = -1;
        for (int childIdx = 0; childIdx < 8; childIdx++) {
            if ((mask & (1 << childIdx)) == 0) {
                continue;
            }
            offset++;
            long childPos = makeChildPos(request.getPosition(), childIdx);
            int childNodeId = base + offset;
            this.nodeData.setNodePosition(childNodeId, childPos);
            byte childExistence = request.getChildChildExistence(childIdx);
            // A real empty/bottom-level result is valid, including child bit 7. The
            // existence notification may also become empty while generation is in flight.
            this.nodeData.setNodeChildExistence(childNodeId, childExistence);
            this.nodeData.setNodeGeometry(childNodeId, request.getChildMesh(childIdx));
            this.invalidateNode(childNodeId);
            int previousId = this.activeSectionMap.put(childPos, childNodeId | NODE_TYPE_LEAF);
            if (previousId != (requestId | NODE_TYPE_REQUEST | REQUEST_TYPE_CHILD)) {
                throw new IllegalStateException("Materialized child did not belong to its completing request: " + previousId);
            }
            this.clearAllocId(childNodeId);
        }
        this.childRequests.release(requestId);
        this.nodeData.setChildPtr(parentNodeId, base);
        this.nodeData.setChildPtrCount(parentNodeId, Integer.bitCount(mask));
        this.nodeData.setNodeRequest(parentNodeId, NULL_REQUEST_ID);
        this.activeNodeRequestCount--;
        this.nodeData.unmarkRequestInFlight(parentNodeId);
        if ((this.activeSectionMap.put(request.getPosition(), NODE_TYPE_INNER | parentNodeId) & NODE_TYPE_MSK) != NODE_TYPE_LEAF) {
            throw new IllegalStateException();
        }
        this.invalidateNode(parentNodeId);
        this.nodeData.setAllChildrenAreLeaf(parentNodeId, true);
        this.refreshContainingParentLeafFlag(request.getPosition());
    }

    private void finishInnerChildRequest(int requestId, ForgeOriginalVoxyNodeChildRequest request, int parentNodeId) {
        int oldChildPtr = this.nodeData.getChildPtr(parentNodeId);
        int oldChildCount = this.nodeData.getChildPtrCount(parentNodeId);
        if (oldChildPtr == -1) {
            throw new IllegalStateException();
        }
        int existingChildMask = 0;
        if (oldChildPtr != SENTINEL_EMPTY_CHILD_PTR) {
            for (int i = 0; i < oldChildCount; i++) {
                if (!this.nodeData.nodeExists(i + oldChildPtr)) {
                    throw new IllegalStateException();
                }
                existingChildMask |= 1 << getChildIdx(this.nodeData.nodePosition(i + oldChildPtr));
            }
        }
        int requestMask = Byte.toUnsignedInt(request.getMsk());
        if ((byte) (existingChildMask | requestMask) != this.nodeData.getNodeChildExistence(parentNodeId)) {
            throw new IllegalStateException("node data existence state does not match pointer mask");
        }
        if ((requestMask & existingChildMask) != 0) {
            throw new IllegalStateException("Overlapping child data");
        }
        int newMask = requestMask | existingChildMask;
        int newChildPtr = this.nodeData.allocate(Integer.bitCount(newMask));
        int childId = newChildPtr - 1;
        int previousChildId = oldChildPtr - 1;
        for (int i = 0; i < 8; i++) {
            if ((newMask & (1 << i)) == 0) {
                continue;
            }
            childId++;
            if ((requestMask & (1 << i)) != 0) {
                long childPos = makeChildPos(request.getPosition(), i);
                this.nodeData.setNodePosition(childId, childPos);
                this.nodeData.setNodeChildExistence(childId, request.getChildChildExistence(i));
                this.nodeData.setNodeGeometry(childId, request.getChildMesh(i));
                this.invalidateNode(childId);
                int previousId = this.activeSectionMap.put(childPos, childId | NODE_TYPE_LEAF);
                if (previousId != (requestId | NODE_TYPE_REQUEST | REQUEST_TYPE_CHILD)) {
                    throw new IllegalStateException("Materialized child did not belong to its completing request: " + previousId);
                }
                this.clearAllocId(childId);
            } else {
                previousChildId++;
                long pos = this.nodeData.nodePosition(previousChildId);
                this.nodeData.copyNode(previousChildId, childId);
                this.clearAllocId(childId);
                this.clearMoveId(previousChildId, childId);
                this.clearFreeId(previousChildId);
                int previousNodeId = this.activeSectionMap.get(pos);
                if ((previousNodeId & NODE_TYPE_MSK) == NODE_TYPE_REQUEST) {
                    throw new IllegalStateException();
                }
                if ((previousNodeId & NODE_ID_MSK) != previousChildId) {
                    throw new IllegalStateException("State inconsistency");
                }
                this.activeSectionMap.put(pos, (previousNodeId & NODE_TYPE_MSK) | childId);
                this.invalidateNode(previousChildId);
                this.invalidateNode(childId);
            }
        }
        if (oldChildPtr != SENTINEL_EMPTY_CHILD_PTR) {
            this.nodeData.free(oldChildPtr, oldChildCount);
        }
        if (oldChildPtr == SENTINEL_EMPTY_CHILD_PTR) {
            this.nodeData.setAllChildrenAreLeaf(parentNodeId, true);
        }
        this.childRequests.release(requestId);
        this.nodeData.setChildPtr(parentNodeId, newChildPtr);
        this.nodeData.setChildPtrCount(parentNodeId, Integer.bitCount(newMask));
        this.nodeData.setNodeRequest(parentNodeId, NULL_REQUEST_ID);
        this.activeNodeRequestCount--;
        this.nodeData.unmarkRequestInFlight(parentNodeId);
        this.invalidateNode(parentNodeId);
    }

    private void makeLeafChildRequest(int nodeId) {
        long pos = this.nodeData.nodePosition(nodeId);
        byte childExistence = this.nodeData.getNodeChildExistence(nodeId);
        if (childExistence == 0 && !this.topLevelNodes.contains(pos)) {
            Logger.warn("Not creating a leaf request with existence mask of 0 at pos", WorldEngine.pprintPos(pos));
            this.nodeData.unmarkRequestInFlight(nodeId);
            this.invalidateNode(nodeId);
            return;
        }
        ForgeOriginalVoxyNodeChildRequest request = new ForgeOriginalVoxyNodeChildRequest(pos);
        int requestId = this.childRequests.put(request);
        for (int i = 0; i < 8; i++) {
            if ((childExistence & (1 << i)) == 0) {
                continue;
            }
            long childPos = makeChildPos(pos, i);
            request.addChildRequirement(i);
            int previousId = this.activeSectionMap.put(childPos, requestId | NODE_TYPE_REQUEST | REQUEST_TYPE_CHILD);
            if (previousId != -1) {
                throw new IllegalStateException("Leaf request creation failed for " + WorldEngine.pprintPos(childPos));
            }
            if (!this.watcher.watch(childPos, WorldEngine.DEFAULT_UPDATE_FLAGS)) {
                throw new IllegalStateException("Failed to watch childPos");
            }
        }
        this.nodeData.setNodeRequest(nodeId, requestId);
        this.activeNodeRequestCount++;
        // Empty top-level requests intentionally park until child-existence changes, as
        // in the original owner. Publish the in-flight marker even with a zero mask.
        this.invalidateNode(nodeId);
    }

    private void processInnerRequest(long pos, int nodeId) {
        int geometry = this.nodeData.getNodeGeometry(nodeId);
        if (VERIFY_NODE_MANAGER_OPERATIONS) {
            boolean isWatchingUpdate = (this.watcher.get(pos) & UPDATE_TYPE_BLOCK_BIT) != 0;
            boolean inflight = this.nodeData.isNodeGeometryInFlight(nodeId);
            if (inflight && !isWatchingUpdate) {
                throw new IllegalStateException();
            }
            if (geometry != NULL_GEOMETRY_ID && inflight && geometry != EMPTY_GEOMETRY_ID) {
                throw new IllegalStateException();
            }
        }
        if (!this.nodeData.isNodeGeometryInFlight(nodeId)) {
            if (!this.watcher.watch(pos, WorldEngine.UPDATE_TYPE_BLOCK_BIT)) {
                this.invalidateNode(nodeId);
            } else {
                this.nodeData.markNodeGeometryInFlight(nodeId);
            }
        }
    }

    private void processLeafGeometryRemoval(long childPos) {
        long parentPos = makeParentPos(childPos);
        int parentId = this.activeSectionMap.get(parentPos);
        if (parentId == -1) {
            throw new IllegalStateException("Parent node must exist");
        }
        if ((parentId & NODE_TYPE_MSK) != NODE_TYPE_INNER) {
            throw new IllegalStateException("Parent node must be an inner node");
        }
        parentId &= NODE_ID_MSK;
        int parentGeometry = this.nodeData.getNodeGeometry(parentId);
        if (parentGeometry == NULL_GEOMETRY_ID) {
            this.processRequest(parentPos);
        } else {
            this.recurseRemoveChildNodes(parentPos);
            int old = this.activeSectionMap.put(parentPos, NODE_TYPE_LEAF | parentId);
            if (old == -1 || (old & NODE_TYPE_MSK) != NODE_TYPE_INNER || (old & NODE_ID_MSK) != parentId) {
                throw new IllegalStateException();
            }
            this.nodeData.setAllChildrenAreLeaf(parentId, false);
            this.nodeData.setEmptyCollapsePending(parentId, false);
            this.refreshContainingParentLeafFlag(parentPos);
        }
    }

    private void refreshContainingParentLeafFlag(long pos) {
        if (this.topLevelNodes.contains(pos)) {
            return;
        }
        int encodedParent = this.activeSectionMap.get(makeParentPos(pos));
        if (encodedParent == -1 || (encodedParent & NODE_TYPE_MSK) != NODE_TYPE_INNER) {
            throw new IllegalStateException("Transitioning node has no inner parent");
        }
        int parent = encodedParent & NODE_ID_MSK;
        int childPtr = this.nodeData.getChildPtr(parent);
        boolean allLeaf = childPtr != -1 && childPtr != SENTINEL_EMPTY_CHILD_PTR;
        if (allLeaf) {
            for (int i = 0; i < this.nodeData.getChildPtrCount(parent); i++) {
                int child = this.activeSectionMap.get(this.nodeData.nodePosition(childPtr + i));
                if (child == -1 || (child & NODE_ID_MSK) != childPtr + i) {
                    throw new IllegalStateException("Parent child pointer has no matching position owner");
                }
                allLeaf &= (child & NODE_TYPE_MSK) == NODE_TYPE_LEAF;
            }
        }
        if (allLeaf != this.nodeData.getAllChildrenAreLeaf(parent)) {
            this.nodeData.setAllChildrenAreLeaf(parent, allLeaf);
            this.invalidateNode(parent);
        }
        // This flag describes immediate children. Higher ancestors keep the same child
        // node types, so propagating the Boolean itself upward would be incorrect.
    }

    private void clearGeometryInternal(long pos, int nodeId) {
        int meshId = this.nodeData.getNodeGeometry(nodeId);
        if (meshId != NULL_GEOMETRY_ID && meshId != EMPTY_GEOMETRY_ID) {
            if (this.watcher.unwatch(pos, WorldEngine.UPDATE_TYPE_BLOCK_BIT)) {
                throw new IllegalStateException("Unwatching position for geometry removal at " + WorldEngine.pprintPos(pos) + " resulted in full removal");
            }
            this.removeGeometryCached(pos, meshId);
            this.nodeData.setNodeGeometry(nodeId, NULL_GEOMETRY_ID);
            this.invalidateNode(nodeId);
            this.nodeData.unmarkNodeGeometryInFlight(nodeId);
        }
    }

    private int uploadReplaceSection(int meshId, BuiltSection section) {
        if (section.isEmpty()) {
            if (meshId != NULL_GEOMETRY_ID && meshId != EMPTY_GEOMETRY_ID) {
                this.geometryManager.removeSection(meshId);
            }
            section.free();
            return EMPTY_GEOMETRY_ID;
        }
        if (meshId != NULL_GEOMETRY_ID && meshId != EMPTY_GEOMETRY_ID) {
            return this.geometryManager.uploadReplaceSection(meshId, section);
        }
        return this.geometryManager.uploadSection(section);
    }

    private int updateNodeGeometry(int node, BuiltSection geometry) {
        int previousGeometry = this.nodeData.getNodeGeometry(node);
        int newGeometry = EMPTY_GEOMETRY_ID;
        if (previousGeometry != EMPTY_GEOMETRY_ID && previousGeometry != NULL_GEOMETRY_ID) {
            if (!geometry.isEmpty()) {
                newGeometry = this.geometryManager.uploadReplaceSection(previousGeometry, geometry);
            } else {
                this.geometryManager.removeSection(previousGeometry);
                geometry.free();
            }
        } else if (!geometry.isEmpty()) {
            newGeometry = this.geometryManager.uploadSection(geometry);
        } else {
            geometry.free();
        }
        if (previousGeometry != newGeometry) {
            this.nodeData.setNodeGeometry(node, newGeometry);
        }
        if (previousGeometry == newGeometry) {
            return 0;
        }
        if (previousGeometry == EMPTY_GEOMETRY_ID || previousGeometry == NULL_GEOMETRY_ID) {
            return 1;
        }
        return 2;
    }

    private void removeGeometryCached(long pos, int id) {
        this.geometryManager.removeSection(id);
    }

    private void verifyInnerChildrenBeforeRemoval(long pos, int nodeId, byte childExistence) {
        if (!VERIFY_NODE_MANAGER_OPERATIONS) {
            return;
        }
        byte mask = 0;
        int childPtr = this.nodeData.getChildPtr(nodeId);
        if (childPtr == -1) {
            throw new IllegalStateException();
        }
        if (childPtr != SENTINEL_EMPTY_CHILD_PTR) {
            int childCount = this.nodeData.getChildPtrCount(nodeId);
            if (Integer.bitCount(Byte.toUnsignedInt(childExistence)) != childCount) {
                throw new IllegalStateException();
            }
            for (int i = 0; i < childCount; i++) {
                if (!this.nodeData.nodeExists(i + childPtr)) {
                    throw new IllegalStateException();
                }
                long childPos = this.nodeData.nodePosition(i + childPtr);
                if (makeParentPos(childPos) != pos) {
                    throw new IllegalStateException();
                }
                mask |= (byte) (1 << getChildIdx(childPos));
            }
        }
        if (mask != childExistence) {
            throw new IllegalStateException();
        }
    }

    private void clearAllocId(int id) {
        if (this.cleanerInterface != null) {
            this.cleanerInterface.alloc(id);
        }
    }

    private void clearMoveId(int from, int to) {
        if (this.cleanerInterface != null) {
            this.cleanerInterface.move(from, to);
        }
    }

    private void clearFreeId(int id) {
        if (this.cleanerInterface != null) {
            this.cleanerInterface.free(id);
        }
    }

    private void invalidateNode(int nodeId) {
        this.nodeUpdates.add(nodeId);
    }

    private int verifyRequest(
            long pos,
            int node,
            int activeChildMask,
            LongOpenHashSet seenPositions,
            IntOpenHashSet seenNodes) {
        if (!this.nodeData.isNodeRequestInFlight(node)) {
            return 0;
        }
        int requestId = this.nodeData.getNodeRequest(node);
        ForgeOriginalVoxyNodeChildRequest request = this.childRequests.get(requestId);
        if (request.getPosition() != pos) {
            throw new IllegalStateException();
        }
        int requestMask = Byte.toUnsignedInt(request.getMsk());
        if ((activeChildMask & requestMask) != 0) {
            throw new IllegalStateException();
        }
        for (int i = 0; i < 8; i++) {
            if ((requestMask & (1 << i)) == 0) {
                continue;
            }
            long childPos = makeChildPos(pos, i);
            int childNode = this.activeSectionMap.get(childPos);
            if (childNode == -1
                    || (childNode & NODE_TYPE_MSK) != NODE_TYPE_REQUEST
                    || (childNode & REQUEST_TYPE_MSK) != REQUEST_TYPE_CHILD
                    || (childNode & NODE_ID_MSK) != requestId) {
                throw new IllegalStateException();
            }
            this.verifyNode(childPos, seenPositions, seenNodes);
        }
        return requestMask;
    }

    private void verifyNode(long pos, LongOpenHashSet seenPositions, IntOpenHashSet seenNodes) {
        int encodedNode = this.activeSectionMap.get(pos);
        if (encodedNode == -1
                || (this.watcher.get(pos) & WorldEngine.UPDATE_TYPE_CHILD_EXISTENCE_BIT) == 0
                || !seenPositions.add(pos)) {
            throw new IllegalStateException();
        }

        int type = encodedNode & NODE_TYPE_MSK;
        if (type == NODE_TYPE_REQUEST) {
            int requestId = encodedNode & NODE_ID_MSK;
            if ((encodedNode & REQUEST_TYPE_MSK) == REQUEST_TYPE_SINGLE) {
                if (!this.topLevelNodes.contains(pos)) {
                    throw new IllegalStateException();
                }
                ForgeOriginalVoxySingleNodeRequest request = this.singleRequests.get(requestId);
                if (request.getPosition() != pos) {
                    throw new IllegalStateException();
                }
            } else {
                ForgeOriginalVoxyNodeChildRequest request = this.childRequests.get(requestId);
                if (request.getPosition() != makeParentPos(pos)) {
                    throw new IllegalStateException();
                }
            }
            return;
        }

        int node = encodedNode & NODE_ID_MSK;
        if (!this.nodeData.nodeExists(node)
                || this.nodeData.nodePosition(node) != pos
                || (this.nodeData.getNodeRequest(node) != NULL_REQUEST_ID)
                != this.nodeData.isNodeRequestInFlight(node)) {
            throw new IllegalStateException();
        }
        if (this.nodeData.isNodeRequestInFlight(node)) {
            ForgeOriginalVoxyNodeChildRequest request =
                    this.childRequests.get(this.nodeData.getNodeRequest(node));
            if (request == null || request.getPosition() != pos) {
                throw new IllegalStateException();
            }
            if (request.isSatisfied()
                    && !(type == NODE_TYPE_LEAF && this.topLevelNodes.contains(pos) && request.getMsk() == 0)) {
                throw new IllegalStateException();
            }
        }

        boolean hasGeometry = this.nodeData.getNodeGeometry(node) != NULL_GEOMETRY_ID;
        boolean watchingGeometry = (this.watcher.get(pos) & UPDATE_TYPE_BLOCK_BIT) != 0;
        boolean awaitingGeometry = this.nodeData.isNodeGeometryInFlight(node);
        if (this.nodeData.isEmptyCollapsePending(node)
                && (type != NODE_TYPE_INNER || hasGeometry || !awaitingGeometry)) {
            throw new IllegalStateException("Deferred empty collapse must retain an inner node awaiting real geometry");
        }
        if ((hasGeometry || awaitingGeometry) != watchingGeometry) {
            throw new IllegalStateException();
        }
        if (hasGeometry
                && awaitingGeometry
                && this.nodeData.getNodeGeometry(node) != EMPTY_GEOMETRY_ID) {
            throw new IllegalStateException();
        }
        if (!seenNodes.add(node)) {
            throw new IllegalStateException();
        }

        if (type == NODE_TYPE_INNER) {
            int childPtr = this.nodeData.getChildPtr(node);
            int childCount = this.nodeData.getChildPtrCount(node);
            int activeChildMask = 0;
            if (childPtr == -1) {
                throw new IllegalStateException();
            }
            if (childPtr != SENTINEL_EMPTY_CHILD_PTR) {
                boolean allChildrenLeaf = true;
                for (int i = 0; i < childCount; i++) {
                    int childNodeId = childPtr + i;
                    if (!this.nodeData.nodeExists(childNodeId)) {
                        throw new IllegalStateException();
                    }
                    long childPos = this.nodeData.nodePosition(childNodeId);
                    if (makeParentPos(childPos) != pos) {
                        throw new IllegalStateException();
                    }
                    activeChildMask |= 1 << getChildIdx(childPos);
                    int childNode = this.activeSectionMap.get(childPos);
                    if (childNode == -1) {
                        throw new IllegalStateException();
                    }
                    if ((childNode & NODE_TYPE_MSK) != NODE_TYPE_LEAF) {
                        allChildrenLeaf = false;
                    }
                    this.verifyNode(childPos, seenPositions, seenNodes);
                }
                if (this.nodeData.getAllChildrenAreLeaf(node) != allChildrenLeaf) {
                    throw new IllegalStateException();
                }
            } else {
                if (this.nodeData.getAllChildrenAreLeaf(node)) {
                    throw new IllegalStateException();
                }
                childCount = 0;
            }
            int childExistence = activeChildMask
                    | this.verifyRequest(pos, node, activeChildMask, seenPositions, seenNodes);
            if (childExistence != Byte.toUnsignedInt(this.nodeData.getNodeChildExistence(node))
                    || childExistence == 0) {
                throw new IllegalStateException();
            }
        } else if (type == NODE_TYPE_LEAF) {
            if (this.nodeData.getAllChildrenAreLeaf(node)
                    || this.nodeData.getChildPtr(node) != -1
                    || this.nodeData.getNodeGeometry(node) == NULL_GEOMETRY_ID) {
                throw new IllegalStateException();
            }
            if (WorldEngine.getLevel(pos) == 0) {
                if (this.nodeData.isNodeRequestInFlight(node)) {
                    throw new IllegalStateException();
                }
            } else if (this.nodeData.isNodeRequestInFlight(node)) {
                int childExistence = this.verifyRequest(pos, node, 0, seenPositions, seenNodes);
                if (childExistence != Byte.toUnsignedInt(this.nodeData.getNodeChildExistence(node))) {
                    throw new IllegalStateException();
                }
            }
        } else {
            throw new IllegalStateException();
        }
    }

    void verifyIntegrity() {
        this.verifyIntegrity(null, null);
    }

    void verifyIntegrity(LongSet watchingPositions, IntSet nodes) {
        LongOpenHashSet seenPositions = new LongOpenHashSet();
        IntOpenHashSet seenNodes = new IntOpenHashSet();
        for (long pos : this.topLevelNodes) {
            this.verifyNode(pos, seenPositions, seenNodes);
        }

        LongSet activePositions = this.activeSectionMap.keySet();
        if (!seenPositions.containsAll(activePositions)
                || !activePositions.containsAll(seenPositions)
                || seenNodes.size() != this.nodeData.getNodeCount()) {
            throw new IllegalStateException();
        }
        for (int node : seenNodes) {
            if (!this.nodeData.nodeExists(node)) {
                throw new IllegalStateException();
            }
        }
        if (this.activeNodeRequestCount != this.childRequests.count()) {
            throw new IllegalStateException();
        }
        if (watchingPositions != null
                && (!watchingPositions.containsAll(activePositions)
                || !activePositions.containsAll(watchingPositions))) {
            throw new IllegalStateException();
        }
        if (nodes != null
                && (!nodes.containsAll(seenNodes) || !seenNodes.containsAll(nodes))) {
            throw new IllegalStateException();
        }

        IntSet topLevelIds = new IntOpenHashSet(this.topLevelNodeIds.size());
        for (long pos : this.topLevelNodes) {
            int node = this.activeSectionMap.get(pos);
            if (node == -1) {
                throw new IllegalStateException();
            }
            if ((node & NODE_TYPE_MSK) != NODE_TYPE_REQUEST
                    && !topLevelIds.add(node & NODE_ID_MSK)) {
                throw new IllegalStateException();
            }
        }
        if (!this.topLevelNodeIds.containsAll(topLevelIds)
                || !topLevelIds.containsAll(this.topLevelNodeIds)) {
            throw new IllegalStateException();
        }
        DiagnosticSnapshot snapshot = this.diagnosticSnapshot();
        if (snapshot.singleRequestIds().size() != this.singleRequests.count()
                || snapshot.childRequestIds().size() != this.childRequests.count()) {
            throw new IllegalStateException("Request allocation has no hierarchy owner");
        }
        this.geometryManager.verifyIntegrity();
        if (!snapshot.geometryIds().equals(this.geometryManager.getAllocatedSectionIdsSnapshot())) {
            throw new IllegalStateException("Geometry allocation and node/request ownership disagree");
        }
        for (var entry : snapshot.positions().entrySet()) {
            int geometryId = entry.getValue().geometryId();
            if (geometryId >= 0 && this.geometryManager.getSectionPosition(geometryId) != entry.getKey()) {
                throw new IllegalStateException("Geometry belongs to a different node/request position: " + geometryId);
            }
        }
    }

    private static void assertPosValid(long pos) {
        int lvl = WorldEngine.getLevel(pos);
        int x = WorldEngine.getX(pos);
        int y = WorldEngine.getY(pos);
        int z = WorldEngine.getZ(pos);
        if (WorldEngine.getWorldSectionId(lvl, x, y, z) != pos) {
            throw new IllegalStateException("Reconstructed pos not same as original");
        }
        x <<= lvl;
        y <<= lvl;
        z <<= lvl;
        long p2 = WorldEngine.getWorldSectionId(0, x, y, z);
        if (WorldEngine.getLevel(p2) != 0 || WorldEngine.getX(p2) != x || WorldEngine.getY(p2) != y || WorldEngine.getZ(p2) != z) {
            throw new IllegalStateException("Position not valid at all levels: " + WorldEngine.pprintPos(pos));
        }
    }

    private static int getChildIdx(long pos) {
        int x = WorldEngine.getX(pos);
        int y = WorldEngine.getY(pos);
        int z = WorldEngine.getZ(pos);
        return (x & 1) | ((y & 1) << 2) | ((z & 1) << 1);
    }

    private static long makeChildPos(long basePos, int addin) {
        int lvl = WorldEngine.getLevel(basePos);
        if (lvl == 0) {
            throw new IllegalArgumentException("Cannot create a child lower than lod level 0");
        }
        return WorldEngine.getWorldSectionId(lvl - 1,
                (WorldEngine.getX(basePos) << 1) | (addin & 1),
                (WorldEngine.getY(basePos) << 1) | ((addin >> 2) & 1),
                (WorldEngine.getZ(basePos) << 1) | ((addin >> 1) & 1));
    }

    private static long makeParentPos(long pos) {
        int lvl = WorldEngine.getLevel(pos);
        if (lvl == MAX_LOD_LAYER) {
            throw new IllegalArgumentException("Cannot create a parent higher than LoD " + MAX_LOD_LAYER);
        }
        return WorldEngine.getWorldSectionId(lvl + 1,
                WorldEngine.getX(pos) >> 1,
                WorldEngine.getY(pos) >> 1,
                WorldEngine.getZ(pos) >> 1);
    }

    private static final class ForgeOriginalVoxySingleNodeRequest {
        private final long nodePos;
        private int mesh = -1;
        private byte childExistence;
        private int setMask;

        private ForgeOriginalVoxySingleNodeRequest(long nodePos) {
            this.nodePos = nodePos;
        }

        private void setChildExistence(byte childExistence) {
            this.setMask |= 2;
            this.childExistence = childExistence;
        }

        private int setMesh(int mesh) {
            this.setMask |= 1;
            int previous = this.mesh;
            this.mesh = mesh;
            return previous;
        }

        private boolean isSatisfied() {
            return this.setMask == 3;
        }

        private long getPosition() {
            return this.nodePos;
        }

        private int getMesh() {
            return this.mesh;
        }

        private byte getChildExistence() {
            return this.childExistence;
        }

        private boolean hasChildExistenceSet() {
            return (this.setMask & 2) != 0;
        }

        private boolean hasMeshSet() {
            return (this.setMask & 1) != 0;
        }
    }

    private static final class ForgeOriginalVoxyNodeChildRequest {
        private final long nodePos;
        private final int[] childStates = new int[]{-1, -1, -1, -1, -1, -1, -1, -1};
        private final byte[] childChildExistence = new byte[]{0, 0, 0, 0, 0, 0, 0, 0};
        private byte results;
        private byte mask;
        private byte existenceMask;

        private ForgeOriginalVoxyNodeChildRequest(long nodePos) {
            this.nodePos = nodePos;
        }

        private int getChildMesh(int childIdx) {
            if ((this.mask & (1 << childIdx)) == 0) {
                throw new IllegalStateException("Tried getting mesh result of child not in mask");
            }
            return this.childStates[childIdx];
        }

        private void setChildChildExistence(int childIdx, byte childExistence) {
            if ((this.mask & (1 << childIdx)) == 0) {
                throw new IllegalStateException("Tried setting child child existence in request when child isnt in mask");
            }
            this.childChildExistence[childIdx] = childExistence;
            this.existenceMask |= (byte) (1 << childIdx);
        }

        private boolean hasChildChildExistence(int childIdx) {
            if ((this.mask & (1 << childIdx)) == 0) {
                throw new IllegalStateException("Tried getting child child existence set of child not in mask");
            }
            return (this.existenceMask & (1 << childIdx)) != 0;
        }

        private byte getChildChildExistence(int childIdx) {
            if (!this.hasChildChildExistence(childIdx)) {
                throw new IllegalStateException("Tried getting child child existence when child child existence for child was not set");
            }
            return this.childChildExistence[childIdx];
        }

        private int setChildMesh(int childIdx, int mesh) {
            if ((this.mask & (1 << childIdx)) == 0) {
                throw new IllegalStateException("Tried setting child mesh when child isnt in mask");
            }
            boolean firstInsert = (this.results & (1 << childIdx)) == 0;
            this.results |= (byte) (1 << childIdx);
            int previous = this.childStates[childIdx];
            this.childStates[childIdx] = mesh;
            return firstInsert ? -1 : previous;
        }

        private int removeAndUnRequire(int childIdx) {
            byte childMask = (byte) (1 << childIdx);
            if ((this.mask & childMask) == 0) {
                throw new IllegalStateException("Tried removing and unmasking child that was never masked");
            }
            byte previous = this.results;
            this.results &= (byte) ~childMask;
            this.mask &= (byte) ~childMask;
            this.existenceMask &= (byte) ~childMask;
            int mesh = this.childStates[childIdx];
            this.childStates[childIdx] = -1;
            return (previous & childMask) == 0 ? -1 : mesh;
        }

        private void addChildRequirement(int childIdx) {
            byte childMask = (byte) (1 << childIdx);
            if ((this.mask & childMask) != 0) {
                throw new IllegalStateException("Child already required!");
            }
            this.mask |= childMask;
        }

        private boolean isSatisfied() {
            return (this.results & this.existenceMask & this.mask) == this.mask;
        }

        private long getPosition() {
            return this.nodePos;
        }

        private byte getMsk() {
            return this.mask;
        }
    }
}
