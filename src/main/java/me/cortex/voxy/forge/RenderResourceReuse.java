package me.cortex.voxy.forge;

import java.util.ArrayDeque;

import static org.lwjgl.opengl.ARBSparseBuffer.glBufferPageCommitmentARB;
import static org.lwjgl.opengl.GL15C.GL_ARRAY_BUFFER;
import static org.lwjgl.opengl.GL15C.glBindBuffer;
import static org.lwjgl.opengl.GL15C.glDeleteBuffers;

/**
 * Port of original {@code me.cortex.voxy.client.core.RenderResourceReuse} for the Forge parity
 * route: the geometry buffer is cached and REUSED across owner rebuilds instead of being freed
 * and reallocated per lifecycle, and is only truly released at full instance shutdown.
 *
 * This is not just an allocation-cost optimisation: the original never opens a
 * free-then-reallocate window for this driver-heavy allocation during a rebuild, which the
 * XX.4 investigation implicated in NVIDIA VRAM aliasing between the dying and the new
 * lifecycle's buffers (shaderpack-switch LOD holes / missing water). Reuse also keeps GL
 * command ordering on one buffer object, so late reads from the old lifecycle order correctly
 * against the new lifecycle's writes.
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
            boolean nvidiaWindowsSparseWorkaroundUsed,
            long committedSparseBytes) {
    }

    private static final ArrayDeque<ReusedGeometryBuffer> GEOMETRY_BUFFER_CACHE = new ArrayDeque<>();

    private RenderResourceReuse() {
    }

    static ReusedGeometryBuffer getOrCreateGeometryBuffer() {
        ReusedGeometryBuffer cached = GEOMETRY_BUFFER_CACHE.poll();
        if (cached != null) {
            VoxyForge.LOGGER.info(
                    "Reusing original Voxy geometry buffer {} ({} bytes, sparse={}, committed={} bytes).",
                    cached.bufferId(),
                    cached.capacityBytes(),
                    cached.sparse(),
                    cached.committedSparseBytes());
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
                allocation.nvidiaWindowsSparseWorkaroundUsed(),
                0L);
    }

    static void giveBackGeometryBuffer(ReusedGeometryBuffer buffer) {
        GEOMETRY_BUFFER_CACHE.add(buffer);
    }

    //Clears and frees any cached resources (used when the entire instance is shutdown)
    static void clearResources() {
        ModelStore.clearCachedModelStoreTextureAtlases();
        ReusedGeometryBuffer buffer = GEOMETRY_BUFFER_CACHE.poll();
        while (buffer != null) {
            if (buffer.sparse() && buffer.committedSparseBytes() > 0L) {
                glBindBuffer(GL_ARRAY_BUFFER, buffer.bufferId());
                glBufferPageCommitmentARB(GL_ARRAY_BUFFER, 0L, buffer.committedSparseBytes(), false);
                glBindBuffer(GL_ARRAY_BUFFER, 0);
            }
            glDeleteBuffers(buffer.bufferId());
            buffer = GEOMETRY_BUFFER_CACHE.poll();
        }
    }
}
