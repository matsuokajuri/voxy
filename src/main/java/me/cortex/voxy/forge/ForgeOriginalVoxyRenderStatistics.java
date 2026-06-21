package me.cortex.voxy.forge;

import me.cortex.voxy.common.world.WorldEngine;

final class ForgeOriginalVoxyRenderStatistics {
    static boolean enabled = false;

    static final int[] hierarchicalTraversalCounts = new int[WorldEngine.MAX_LOD_LAYER + 1];
    static final int[] hierarchicalRenderSections = new int[WorldEngine.MAX_LOD_LAYER + 1];
    static final int[] visibleSections = new int[WorldEngine.MAX_LOD_LAYER + 1];
    static final int[] quadCount = new int[WorldEngine.MAX_LOD_LAYER + 1];

    private ForgeOriginalVoxyRenderStatistics() {
    }
}
