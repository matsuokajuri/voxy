package me.cortex.voxy.forge;

import me.cortex.voxy.common.world.WorldEngine;

/** Shared allocation/GLSL contract for the original HOC and MDIC buffer layouts. */
final class GpuBufferLayout {
    static final int DISPATCH_BYTES = 3 * Integer.BYTES;
    static final int MAX_LOD = WorldEngine.MAX_LOD_LAYER;
    static final int MAX_ITERATIONS = MAX_LOD + 1;
    static final int HOC_REQUEST_CAPACITY = 50;
    static final int HOC_QUEUE_CAPACITY = 200_000;
    static final int HOC_RENDER_LIST_CAPACITY = 200_000;
    static final int HOC_REQUEST_HEADER_BYTES = 8;
    static final int HOC_REQUEST_BYTES = 8;
    static final int HOC_QUEUE_META_BYTES = 16;
    static final long HOC_REQUEST_BUFFER_BYTES = HOC_REQUEST_HEADER_BYTES + HOC_REQUEST_CAPACITY * 8L;
    static final long HOC_QUEUE_BUFFER_BYTES = HOC_QUEUE_CAPACITY * 4L;
    static final long HOC_METADATA_BUFFER_BYTES = MAX_ITERATIONS * 16L;
    static final long HOC_RENDER_LIST_BYTES = Integer.BYTES + HOC_RENDER_LIST_CAPACITY * 4L;

    static final int OPAQUE_CAPACITY = 400_000;
    static final int TRANSLUCENT_CAPACITY = 100_000;
    static final int TEMPORAL_CAPACITY = 100_000;
    static final int TRANSLUCENT_OFFSET = OPAQUE_CAPACITY;
    static final int TEMPORAL_OFFSET = TRANSLUCENT_OFFSET + TRANSLUCENT_CAPACITY;
    static final int DRAW_COMMAND_BYTES = 5 * Integer.BYTES;
    static final int DRAW_COUNT_BYTES = 1024;
    static final int DRAW_COUNT_ABI_BYTES = 6 * Integer.BYTES + DRAW_COMMAND_BYTES;
    static final long DRAW_BUFFER_BYTES = (long) (TEMPORAL_OFFSET + TEMPORAL_CAPACITY) * DRAW_COMMAND_BYTES;
    static final int POSITION_CAPACITY = OPAQUE_CAPACITY;
    static final long POSITION_BUFFER_BYTES = POSITION_CAPACITY * 8L;
    static final int TRANSLUCENT_BUCKETS = 1024;
    static final int TRANSLUCENT_PREFIX_SNAPSHOT_BASE = TRANSLUCENT_BUCKETS + TRANSLUCENT_CAPACITY;
    static final long TRANSLUCENT_DISTANCE_ABI_BYTES = TRANSLUCENT_PREFIX_SNAPSHOT_BASE * 4L;
    // Immutable bucket starts follow the original histogram/list prefix; command/list offsets do not move.
    static final long TRANSLUCENT_DISTANCE_BYTES = TRANSLUCENT_DISTANCE_ABI_BYTES + TRANSLUCENT_BUCKETS * 4L;

    static final int CLEANER_OUTPUT_CAPACITY = 256;
    static final int CLEANER_LOCAL_SIZE = 64;
    static final int CLEANER_ELEMENTS_PER_THREAD = 8;
    static final long CLEANER_ID_BYTES = CLEANER_OUTPUT_CAPACITY * 4L;
    static final long CLEANER_POSITION_BYTES = CLEANER_OUTPUT_CAPACITY * 8L;
    static final long CLEANER_OUTPUT_BYTES = CLEANER_ID_BYTES + CLEANER_POSITION_BYTES;

    private GpuBufferLayout() {
    }
}
