package me.cortex.voxy.forge;

import me.cortex.voxy.common.world.WorldEngine;

import java.util.function.LongConsumer;

final class ForgeOriginalVoxyRenderDistanceTracker {
    private static final int CHECK_DISTANCE_BLOCKS = 128;
    private final LongConsumer addTopLevelNode;
    private final LongConsumer removeTopLevelNode;
    private final int processRate;
    private final int minSec;
    private final int maxSec;
    private ForgeOriginalVoxyRingTracker tracker;
    private int renderDistance;
    private double posX;
    private double posZ;

    ForgeOriginalVoxyRenderDistanceTracker(
            int rate,
            int minSec,
            int maxSec,
            LongConsumer addTopLevelNode,
            LongConsumer removeTopLevelNode) {
        this.addTopLevelNode = addTopLevelNode;
        this.removeTopLevelNode = removeTopLevelNode;
        this.renderDistance = 2;
        this.tracker = new ForgeOriginalVoxyRingTracker(this.renderDistance, 0, 0, true);
        this.processRate = rate;
        this.minSec = minSec;
        this.maxSec = maxSec;
    }

    void setRenderDistance(int renderDistance) {
        if (renderDistance == this.renderDistance) {
            return;
        }
        this.renderDistance = renderDistance;
        this.tracker.unload();
        this.tracker = new ForgeOriginalVoxyRingTracker(this.tracker, renderDistance, ((int) this.posX) >> 9, ((int) this.posZ) >> 9, true);
    }

    boolean setCenterAndProcess(double x, double z) {
        double dx = this.posX - x;
        double dz = this.posZ - z;
        if (CHECK_DISTANCE_BLOCKS * CHECK_DISTANCE_BLOCKS < dx * dx + dz * dz) {
            this.posX = x;
            this.posZ = z;
            this.tracker.moveCenter(((int) x) >> 9, ((int) z) >> 9);
        }
        return this.tracker.process(this.processRate, this::add, this::rem) != 0;
    }

    private void add(int x, int z) {
        for (int y = this.minSec; y <= this.maxSec; y++) {
            this.addTopLevelNode.accept(WorldEngine.getWorldSectionId(4, x, y, z));
        }
    }

    private void rem(int x, int z) {
        for (int y = this.minSec; y <= this.maxSec; y++) {
            this.removeTopLevelNode.accept(WorldEngine.getWorldSectionId(4, x, y, z));
        }
    }
}
