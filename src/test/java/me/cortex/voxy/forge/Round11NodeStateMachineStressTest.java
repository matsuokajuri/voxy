package me.cortex.voxy.forge;

import me.cortex.voxy.common.util.MemoryBuffer;
import me.cortex.voxy.common.world.WorldEngine;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static me.cortex.voxy.forge.Round11NodeTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

/** Deterministic external event sequences against the real node/geometry owners, not a replica renderer. */
class Round11NodeStateMachineStressTest {
    private static final long FIRST_SEED = 0x524F554E44313100L;
    private static final long[] ROOTS = {
            pos(3, -2, 0, -2), pos(3, -1, 0, -2), pos(3, 0, 0, -2), pos(3, 1, 0, -2)
    };

    @Test
    void fixedSeedsPreserveHierarchyAndResourcesAcross64000Events() {
        runSeeds(32, 2_000);
    }

    @Test
    @Tag("round11-long")
    void longFixedSeedsPreserveHierarchyAndResourcesAcrossOneMillionEvents() {
        runSeeds(100, 10_000);
    }

    private static void runSeeds(int seedCount, int steps) {
        Stats stats = new Stats();
        for (int index = 0; index < seedCount; index++) {
            long seed = FIRST_SEED + index;
            List<Event> history = new ArrayList<>();
            try (var state = new Round11NodeTestSupport()) {
                Random random = new Random(seed);
                for (int step = 0; step < steps; step++) {
                    Event event = nextEvent(state, random);
                    history.add(event);
                    NodeManager.DiagnosticSnapshot before = state.manager.diagnosticSnapshot();
                    apply(state, event);
                    state.verify();
                    stats.observe(before, state.manager.diagnosticSnapshot());
                    // A published CPU upload relinquishes the buffer, not its section ID.
                    if ((step & 31) == 31) {
                        state.geometry.drainPendingSyncEventsForCurrentParityOwner();
                        state.manager.getNodeUpdates().clear();
                    }
                }
            } catch (Throwable failure) {
                List<Event> replay = minimize(history, failureSignature(failure));
                throw new AssertionError("Round11 seed=" + seed + " failed after " + history.size()
                        + " events; minimized replay=" + replay, failure);
            }
        }
        assertTrue(stats.splits > seedCount, "sequences must reach materialized splits, not only parked requests");
        assertTrue(stats.collapses > seedCount, "sequences must reach real hierarchy collapse");
        assertTrue(stats.moves > seedCount, "sequences must exercise child-array compaction/expansion");
        assertTrue(stats.deferred > seedCount, "sequences must exercise missing-parent coverage retention");
        assertEquals(0, stats.minimumLevel, "sequences must materialize every LOD depth down to LOD0");
        System.out.println("ROUND11_NODE_STRESS seeds=" + seedCount + " events=" + (long) seedCount * steps
                + " splits=" + stats.splits + " collapses=" + stats.collapses + " nodeMoves=" + stats.moves
                + " deferredCollapseStates=" + stats.deferred + " deepestMaterializedLod=" + stats.minimumLevel);
    }

    private static Event nextEvent(Round11NodeTestSupport state, Random random) {
        NodeManager.DiagnosticSnapshot snapshot = state.manager.diagnosticSnapshot();
        List<Long> active = snapshot.positions().keySet().stream().sorted().toList();
        long root = ROOTS[random.nextInt(ROOTS.length)];
        int selector = random.nextInt(100);
        if (active.isEmpty() || selector < 7) {
            return new Event(snapshot.topLevelPositions().contains(root) ? Kind.REMOVE : Kind.ADD, root, 0, false);
        }
        long position = active.get(random.nextInt(active.size()));
        // Favor sparse masks so requests complete often enough to exercise deep topology,
        // while still retaining dense masks and every signed high-bit combination.
        int mask = 0;
        if (WorldEngine.getLevel(position) != 0) {
            mask = random.nextInt(4) == 0 ? random.nextInt(256)
                    : (1 << random.nextInt(8)) | (random.nextBoolean() ? 1 << random.nextInt(8) : 0);
        }
        if (selector < 43) {
            if ((state.watcher.get(position) & WorldEngine.UPDATE_TYPE_BLOCK_BIT) == 0) {
                return new Event(Kind.REQUEST, position, 0, false);
            }
            return new Event(Kind.GEOMETRY, position, mask, random.nextInt(5) == 0);
        }
        if (selector < 66) {
            return new Event(Kind.CHILD_MASK, position, random.nextInt(5) == 0 ? 0 : mask, false);
        }
        if (selector < 87) {
            return new Event(Kind.REQUEST, position, 0, false);
        }
        return new Event(Kind.CLEAN, position, 0, false);
    }

    private static void apply(Round11NodeTestSupport state, Event event) {
        NodeManager.DiagnosticSnapshot before = state.manager.diagnosticSnapshot();
        NodeManager.NodeState node = before.positions().get(event.position);
        int type = node == null ? -1 : node.type() & TYPE_MASK;
        switch (event.kind) {
            case ADD -> {
                if (!before.topLevelPositions().contains(event.position)) {
                    assertTrue(state.manager.insertTopLevelNode(event.position));
                }
            }
            case REMOVE -> {
                if (before.topLevelPositions().contains(event.position)) {
                    state.manager.removeTopLevelNode(event.position);
                }
            }
            case GEOMETRY -> {
                // An unwatched/missing result has an explicit caller-retains contract.
                BuiltSection result = event.empty ? BuiltSection.emptyWithChildren(event.position, (byte) event.mask)
                        : new BuiltSection(event.position, (byte) event.mask, 0,
                        new MemoryBuffer(8).zero(), new int[8], null);
                if (!state.manager.processGeometryResult(result)) {
                    result.free();
                }
            }
            case CHILD_MASK -> {
                if (node != null) {
                    state.manager.processChildChange(event.position, (byte) event.mask);
                }
            }
            case REQUEST -> {
                if (node != null && type != REQUEST && WorldEngine.getLevel(event.position) > 0
                        && !node.requestInFlight()
                        && (type == INNER || node.childMask() != 0
                        || before.topLevelPositions().contains(event.position))) {
                    state.manager.processRequest(event.position);
                }
            }
            case CLEAN -> {
                if (node != null && type != REQUEST
                        && (type == INNER || !before.topLevelPositions().contains(event.position))) {
                    state.manager.removeNodeGeometry(event.position);
                }
            }
        }
    }

    // Delta-debug only on failure. External events are positions, never internal IDs, so
    // shortened replays stay meaningful when deletion/compaction reuses a different slot.
    private static List<Event> minimize(List<Event> original, String signature) {
        List<Event> reduced = new ArrayList<>(original);
        for (int width = Math.max(1, reduced.size() / 2); width > 0; width /= 2) {
            for (int offset = 0; offset + width <= reduced.size();) {
                List<Event> candidate = new ArrayList<>(reduced);
                candidate.subList(offset, offset + width).clear();
                if (!candidate.isEmpty() && failsWith(candidate, signature)) {
                    reduced = candidate;
                } else {
                    offset += width;
                }
            }
        }
        return List.copyOf(reduced);
    }

    private static boolean failsWith(List<Event> events, String signature) {
        try (var state = new Round11NodeTestSupport()) {
            for (Event event : events) {
                apply(state, event);
                state.verify();
            }
            return false;
        } catch (Throwable failure) {
            return failureSignature(failure).equals(signature);
        }
    }

    private static String failureSignature(Throwable failure) {
        return failure.getClass().getName() + ":" + failure.getMessage();
    }

    private enum Kind { ADD, REMOVE, GEOMETRY, CHILD_MASK, REQUEST, CLEAN }
    private record Event(Kind kind, long position, int mask, boolean empty) { }

    private static final class Stats {
        long splits;
        long collapses;
        long moves;
        long deferred;
        int minimumLevel = 3;

        void observe(NodeManager.DiagnosticSnapshot before, NodeManager.DiagnosticSnapshot after) {
            for (var entry : after.positions().entrySet()) {
                NodeManager.NodeState current = entry.getValue();
                int currentType = current.type() & TYPE_MASK;
                if (currentType != REQUEST) {
                    minimumLevel = Math.min(minimumLevel, WorldEngine.getLevel(entry.getKey()));
                }
                if (current.emptyCollapsePending()) {
                    deferred++;
                }
                NodeManager.NodeState previous = before.positions().get(entry.getKey());
                if (previous == null) {
                    continue;
                }
                int previousType = previous.type() & TYPE_MASK;
                if (previousType == LEAF && currentType == INNER) {
                    splits++;
                } else if (previousType == INNER && currentType == LEAF) {
                    collapses++;
                }
                if (currentType != REQUEST && previousType != REQUEST && current.id() != previous.id()) {
                    moves++;
                }
            }
        }
    }
}
