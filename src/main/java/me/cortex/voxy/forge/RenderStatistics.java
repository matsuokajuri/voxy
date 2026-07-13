package me.cortex.voxy.forge;

import me.cortex.voxy.common.world.WorldEngine;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

final class RenderStatistics {
    static boolean enabled = false;

    static final int[] hierarchicalTraversalCounts = new int[WorldEngine.MAX_LOD_LAYER + 1];
    static final int[] hierarchicalRenderSections = new int[WorldEngine.MAX_LOD_LAYER + 1];
    static final int[] visibleSections = new int[WorldEngine.MAX_LOD_LAYER + 1];
    static final int[] quadCount = new int[WorldEngine.MAX_LOD_LAYER + 1];

    private RenderStatistics() {
    }

    static void addDebug(List<String> debug) {
        if (!enabled) {
            return;
        }
        debug.add("HTC: [" + joinReversed(hierarchicalTraversalCounts) + "]");
        debug.add("HRS: [" + joinReversed(hierarchicalRenderSections) + "]");
        debug.add("VS: [" + joinReversed(visibleSections) + "]");
        debug.add("QC: [" + joinReversed(quadCount) + "]");
    }

    private static String joinReversed(int[] values) {
        return Arrays.stream(flipCopy(values)).mapToObj(Integer::toString).collect(Collectors.joining(", "));
    }

    private static int[] flipCopy(int[] array) {
        int[] copy = new int[array.length];
        int index = copy.length;
        for (int value : array) {
            copy[--index] = value;
        }
        return copy;
    }
}
