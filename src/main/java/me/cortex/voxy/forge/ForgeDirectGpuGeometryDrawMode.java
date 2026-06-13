package me.cortex.voxy.forge;

enum ForgeDirectGpuGeometryDrawMode {
    LOOP_PER_SECTION,
    MULTI_DRAW_ARRAYS,
    MULTI_DRAW_ARRAYS_INDIRECT,
    AUTO;

    String commandName() {
        if (this == MULTI_DRAW_ARRAYS_INDIRECT) {
            return "multi_draw_arrays_indirect";
        }
        if (this == MULTI_DRAW_ARRAYS) {
            return "multi_draw_arrays";
        }
        if (this == AUTO) {
            return "auto";
        }
        return "loop";
    }
}
