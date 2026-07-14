package me.cortex.voxy.forge;

import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import me.cortex.voxy.common.Logger;
import me.cortex.voxy.common.world.WorldEngine;

import static me.cortex.voxy.common.world.WorldEngine.UPDATE_TYPE_CHILD_EXISTENCE_BIT;

/** Minecraft-1.20.1 adapter for the original top-level-node child-mask verifier. */
final class DebugUtils {
    private DebugUtils() {
    }

    static void verifyAllTopLevelNodes(WorldEngine engine, boolean attemptRepair) {
        engine.markActive();
        Thread worker = new Thread(() -> {
            engine.acquireRef();
            try {
                Logger.info("Verifying top level node masks, start");
                Logger.showInHUD("Starting tln child verification"
                        + (attemptRepair ? " attempting repairs on error" : ""));
                LongArrayFIFOQueue positions = new LongArrayFIFOQueue();
                engine.storage.iteratePositions(WorldEngine.MAX_LOD_LAYER, positions::enqueue);
                Logger.info("Verifying " + positions.size() + " top level nodes");
                while (!positions.isEmpty() && engine.isOwningSessionRunning()) {
                    long position = positions.dequeueLong();
                    verifyTopNodeChildren(
                            engine,
                            WorldEngine.getX(position),
                            WorldEngine.getY(position),
                            WorldEngine.getZ(position),
                            attemptRepair);
                }
                if (!engine.isOwningSessionRunning()) {
                    Logger.info("Verification aborted due to shutdown");
                } else {
                    Logger.info("Verification complete");
                    Logger.showInHUD("Verification complete");
                }
            } finally {
                engine.releaseRef();
            }
        }, "Verification thread");
        worker.setDaemon(true);
        worker.start();
    }

    private static void verifyTopNodeChildren(
            WorldEngine world,
            int topX,
            int topY,
            int topZ,
            boolean attemptRepair) {
        boolean loggedTopPosition = false;
        for (int level = 0; level < 5; level++) {
            for (int y = (topY << 4) >> level; y < ((topY + 1) << 4) >> level; y++) {
                for (int x = (topX << 4) >> level; x < ((topX + 1) << 4) >> level; x++) {
                    for (int z = (topZ << 4) >> level; z < ((topZ + 1) << 4) >> level; z++) {
                        if (!world.isOwningSessionRunning()) {
                            return;
                        }
                        if (level == 0) {
                            var own = world.acquireIfExists(level, x, y, z);
                            if (own == null) {
                                continue;
                            }
                            if ((own.getNonEmptyChildren() != 0) ^ (own.getNonEmptyBlockCount() != 0)) {
                                if (!loggedTopPosition) {
                                    Logger.error("Error verifying top level node: " + topX + ',' + topY + ',' + topZ);
                                    loggedTopPosition = true;
                                }
                                Logger.error("Lvl 0 node not marked correctly "
                                        + WorldEngine.pprintPos(own.key)
                                        + " expected: " + (own.getNonEmptyBlockCount() != 0)
                                        + " got " + (own.getNonEmptyChildren() != 0));
                                if (attemptRepair) {
                                    own.updateLvl0State();
                                    world.markDirty(own, UPDATE_TYPE_CHILD_EXISTENCE_BIT, 0);
                                }
                            }
                            own.release();
                            continue;
                        }

                        byte expectedMask = 0;
                        for (int child = 0; child < 8; child++) {
                            var section = world.acquireIfExists(
                                    level - 1,
                                    (child & 1) + (x << 1),
                                    ((child >> 2) & 1) + (y << 1),
                                    ((child >> 1) & 1) + (z << 1));
                            if (section != null) {
                                expectedMask |= (byte) (section.getNonEmptyChildren() != 0 ? 1 << child : 0);
                                section.release();
                            }
                        }
                        var own = world.acquireIfExists(level, x, y, z);
                        if (own != null) {
                            if (own.getNonEmptyChildren() != expectedMask) {
                                if (!loggedTopPosition) {
                                    Logger.error("Error verifying top level node: " + topX + ',' + topY + ',' + topZ);
                                    loggedTopPosition = true;
                                }
                                Logger.error("Section empty child mask not correct "
                                        + WorldEngine.pprintPos(own.key)
                                        + " got: " + maskString(own.getNonEmptyChildren())
                                        + " expected: " + maskString(expectedMask));
                                if (attemptRepair) {
                                    for (int child = 0; child < 8; child++) {
                                        var section = world.acquireIfExists(
                                                level - 1,
                                                (child & 1) + (x << 1),
                                                ((child >> 2) & 1) + (y << 1),
                                                ((child >> 1) & 1) + (z << 1));
                                        if (section != null) {
                                            own.updateEmptyChildState(section);
                                            section.release();
                                        }
                                    }
                                    world.markDirty(own, UPDATE_TYPE_CHILD_EXISTENCE_BIT, 0);
                                }
                            }
                            own.release();
                        } else if (expectedMask != 0) {
                            if (!loggedTopPosition) {
                                Logger.error("Error verifying top level node: " + topX + ',' + topY + ',' + topZ);
                                loggedTopPosition = true;
                            }
                            Logger.error("Section does not exist in db but has non-empty children "
                                    + WorldEngine.pprintPos(WorldEngine.getWorldSectionId(level, x, y, z))
                                    + " has children: " + maskString(expectedMask));
                        }
                    }
                }
            }
        }
    }

    private static String maskString(byte value) {
        return String.format("%8s", Integer.toBinaryString(Byte.toUnsignedInt(value))).replace(' ', '0');
    }
}
