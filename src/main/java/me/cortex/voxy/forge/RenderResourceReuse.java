package me.cortex.voxy.forge;

import java.util.ArrayDeque;

import static org.lwjgl.opengl.GL15C.glDeleteBuffers;

/**
 * Port of original {@code me.cortex.voxy.client.core.RenderResourceReuse} for the Forge parity
 * route: the geometry buffer is cached and REUSED across owner rebuilds instead of being freed
 * and reallocated per lifecycle, and is only truly released at full instance shutdown.
 *
 * Reuse preserves the original ownership/lifetime contract, avoids allocation churn across
 * owner rebuilds, and keeps GL command ordering on one buffer object.
 *
 * The model-atlas texture reuse half of the original class is adapted through
 * {@code ModelStore}'s static texture cache; {@link #clearResources()} owns its
 * terminal deletion together with the geometry cache, matching the original instance shutdown.
 */
final class RenderResourceReuse {
    record ReusedGeometryBuffer(
            int bufferId,
            long capacityBytes,
            boolean sparse,
            boolean nvidiaWindowsSparseWorkaroundUsed) {
    }

    private static final ArrayDeque<ReusedGeometryBuffer> GEOMETRY_BUFFER_CACHE = new ArrayDeque<>();

    private RenderResourceReuse() {
    }

    static ReusedGeometryBuffer getOrCreateGeometryBuffer() {
        ReusedGeometryBuffer cached = GEOMETRY_BUFFER_CACHE.poll();
        if (cached != null) {
            VoxyForge.LOGGER.info(
                    "Reusing original Voxy geometry buffer {} ({} bytes, sparse={}).",
                    cached.bufferId(),
                    cached.capacityBytes(),
                    cached.sparse());
            return cached;
        }
        long capacityBytes = BasicSectionGeometryData.selectGeometryCapacityBytes();
        BasicSectionGeometryData.GeometryAllocation allocation =
                BasicSectionGeometryData.createGeometryBuffer(capacityBytes);
        VoxyForge.LOGGER.info(
                "Allocated new original Voxy geometry buffer {} ({} bytes, sparse={}).",
                allocation.bufferId(),
                capacityBytes,
                allocation.sparse());
        return new ReusedGeometryBuffer(
                allocation.bufferId(),
                capacityBytes,
                allocation.sparse(),
                allocation.nvidiaWindowsSparseWorkaroundUsed());
    }

    static void giveBackGeometryBuffer(ReusedGeometryBuffer buffer) {
        GEOMETRY_BUFFER_CACHE.add(buffer);
    }

    //Clears and frees any cached resources (used when the entire instance is shutdown)
    static void clearResources() {
        ModelStore.clearCachedModelStoreTextureAtlases();
        ReusedGeometryBuffer buffer = GEOMETRY_BUFFER_CACHE.poll();
        while (buffer != null) {
            glDeleteBuffers(buffer.bufferId());
            buffer = GEOMETRY_BUFFER_CACHE.poll();
        }
    }
}
