package me.cortex.voxy.forge;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import me.cortex.voxy.common.util.MemoryBuffer;
import me.cortex.voxy.common.world.WorldEngine;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Exercises the production CPU owners; only their external watch/cleaner callbacks are recorded. */
final class Round11NodeTestSupport implements AutoCloseable {
    static final int ID_MASK = (1 << 24) - 1;
    static final int TYPE_MASK = 3 << 30;
    static final int LEAF = 0;
    static final int INNER = 1 << 30;
    static final int REQUEST = 2 << 30;

    final BasicAsyncGeometryManager geometry;
    final Watcher watcher = new Watcher();
    final Cleaner cleaner = new Cleaner();
    final NodeManager manager;
    final NodeStore store;
    final Long2IntOpenHashMap active;
    final IntOpenHashSet roots = new IntOpenHashSet();
    final int initialBuffers = MemoryBuffer.getCount();

    Round11NodeTestSupport() {
        this(8192);
    }

    Round11NodeTestSupport(int nodeCapacity) {
        this(nodeCapacity, 16L << 20);
    }

    Round11NodeTestSupport(int nodeCapacity, long geometryCapacity) {
        geometry = new BasicAsyncGeometryManager(8192, geometryCapacity);
        manager = new NodeManager(nodeCapacity, geometry, watcher);
        store = field(manager, "nodeData");
        active = field(manager, "activeSectionMap");
        manager.setClear(cleaner);
        manager.setTLNCallbacks(id -> assertTrue(roots.add(id)), id -> assertTrue(roots.remove(id)));
    }

    static long pos(int level, int x, int y, int z) {
        return WorldEngine.getWorldSectionId(level, x, y, z);
    }

    static long child(long pos, int bit) {
        return pos(WorldEngine.getLevel(pos) - 1,
                (WorldEngine.getX(pos) << 1) | (bit & 1),
                (WorldEngine.getY(pos) << 1) | ((bit >>> 2) & 1),
                (WorldEngine.getZ(pos) << 1) | ((bit >>> 1) & 1));
    }

    void insert(long pos, int mask) {
        assertTrue(manager.insertTopLevelNode(pos));
        result(pos, mask, false);
        verify();
    }

    void result(long pos, int mask, boolean empty) {
        BuiltSection section = empty ? BuiltSection.emptyWithChildren(pos, (byte) mask)
                : new BuiltSection(pos, (byte) mask, 0, new MemoryBuffer(8).zero(), new int[8], null);
        if (!manager.processGeometryResult(section)) {
            section.free();
            fail("unexpected rejection at " + WorldEngine.pprintPos(pos));
        }
    }

    void split(long pos, int childMask) {
        manager.processRequest(pos);
        int mask = Byte.toUnsignedInt(store.getNodeChildExistence(id(pos)));
        for (int bit = 0; bit < 8; bit++) {
            if ((mask & (1 << bit)) != 0) {
                result(child(pos, bit), childMask, false);
            }
        }
        verify();
    }

    int id(long pos) {
        assertTrue(active.containsKey(pos), "missing " + WorldEngine.pprintPos(pos));
        assertNotEquals(REQUEST, active.get(pos) & TYPE_MASK);
        return active.get(pos) & ID_MASK;
    }

    int type(long pos) {
        return active.get(pos) & TYPE_MASK;
    }

    int childRequests() {
        return field(manager, "activeNodeRequestCount");
    }

    void verify() {
        manager.verifyIntegrity(watcher.types.keySet(), cleaner.ids);
        assertEquals(roots, field(manager, "topLevelNodeIds"));
        IntOpenHashSet geometryIds = new IntOpenHashSet();
        for (long pos : active.keySet()) {
            int encoded = active.get(pos);
            int mesh;
            if ((encoded & TYPE_MASK) == REQUEST) {
                Object request;
                if ((encoded & (1 << 29)) == 0) {
                    ExpandingObjectAllocationList<?> requests = field(manager, "singleRequests");
                    request = requests.get(encoded & ID_MASK);
                    mesh = field(request, "mesh");
                } else {
                    ExpandingObjectAllocationList<?> requests = field(manager, "childRequests");
                    request = requests.get(encoded & ID_MASK);
                    int[] children = field(request, "childStates");
                    int bit = (WorldEngine.getX(pos) & 1) | ((WorldEngine.getY(pos) & 1) << 2)
                            | ((WorldEngine.getZ(pos) & 1) << 1);
                    mesh = children[bit];
                }
            } else {
                mesh = store.getNodeGeometry(encoded & ID_MASK);
            }
            if (mesh >= 0) {
                assertTrue(geometryIds.add(mesh), "duplicate geometry owner " + mesh);
            }
        }
        assertEquals(geometryIds, geometry.getAllocatedSectionIdsSnapshot(), "orphaned geometry");
    }

    @Override
    public void close() {
        try {
            // Remove only roots still active; this also exercises recursive cleanup after a failed assertion.
            it.unimi.dsi.fastutil.longs.LongOpenHashSet positions = field(manager, "topLevelNodes");
            for (long pos : positions.toLongArray()) {
                manager.removeTopLevelNode(pos);
            }
            verify();
            assertEquals(0, geometry.getSectionCount());
            assertEquals(0, store.getNodeCount());
            assertEquals(0, childRequests());
            assertTrue(watcher.types.isEmpty());
            assertTrue(cleaner.ids.isEmpty());
        } finally {
            geometry.clear();
        }
        assertEquals(initialBuffers, MemoryBuffer.getCount(), "native buffer leak");
    }

    @SuppressWarnings("unchecked")
    static <T> T field(Object owner, String name) {
        try {
            Field field = owner.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return (T) field.get(owner);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    static final class Watcher implements ISectionWatcher {
        final Long2IntOpenHashMap types = new Long2IntOpenHashMap();

        @Override
        public boolean watch(long position, int flags) {
            int old = types.get(position);
            types.put(position, old | flags);
            return (flags & ~old) != 0;
        }

        @Override
        public boolean unwatch(long position, int flags) {
            assertTrue(types.containsKey(position), "unwatch without watch");
            int remaining = types.get(position) & ~flags;
            if (remaining == 0) {
                types.remove(position);
            } else {
                types.put(position, remaining);
            }
            return remaining == 0;
        }

        @Override
        public int get(long position) {
            return types.get(position);
        }
    }

    static final class Cleaner implements NodeManager.Cleaner {
        final IntOpenHashSet ids = new IntOpenHashSet();
        final List<String> operations = new ArrayList<>();

        @Override
        public void alloc(int id) {
            assertTrue(ids.add(id), "duplicate cleaner allocation");
            operations.add("alloc " + id);
        }

        @Override
        public void move(int from, int to) {
            assertTrue(ids.contains(from));
            assertTrue(ids.contains(to));
            operations.add("move " + from + " " + to);
        }

        @Override
        public void free(int id) {
            assertTrue(ids.remove(id), "duplicate cleaner free");
            operations.add("free " + id);
        }
    }
}
