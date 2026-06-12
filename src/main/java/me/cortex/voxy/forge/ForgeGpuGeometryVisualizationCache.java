package me.cortex.voxy.forge;

import java.util.ArrayList;
import java.util.List;

public final class ForgeGpuGeometryVisualizationCache {
    static final String SOURCE = "GL_HEAP_READBACK";
    static final int VERTEX_STRIDE_INTS = 4;
    static final int X_OFFSET = 0;
    static final int Y_OFFSET = 1;
    static final int Z_OFFSET = 2;
    static final int COLOR_OFFSET = 3;

    private final ArrayList<Entry> entries = new ArrayList<>();
    private ForgeGpuGeometryVisualizationResult lastBuildResult = ForgeGpuGeometryVisualizationResult.failure("not-built");
    private long clearCount;

    public synchronized void replaceAll(List<Entry> nextEntries, ForgeGpuGeometryVisualizationResult result) {
        this.entries.clear();
        if (nextEntries != null && !nextEntries.isEmpty()) {
            this.entries.addAll(nextEntries);
        }
        this.lastBuildResult = result == null ? ForgeGpuGeometryVisualizationResult.failure("missing-result") : result;
    }

    public synchronized void clear() {
        this.entries.clear();
        this.lastBuildResult = ForgeGpuGeometryVisualizationResult.failure("cache-cleared");
        this.clearCount++;
    }

    public synchronized RenderSnapshot createRenderSnapshot(String dimension, int centerChunkX, int centerChunkZ, int radiusChunks) {
        ArrayList<Entry> renderEntries = new ArrayList<>();
        int skippedDimension = 0;
        int skippedDistance = 0;
        int radius = Math.max(0, radiusChunks);
        for (Entry entry : this.entries) {
            if (!entry.dimension().equals(dimension)) {
                skippedDimension++;
                continue;
            }
            if (radius > 0) {
                int distance = Math.max(Math.abs(entry.chunkX() - centerChunkX), Math.abs(entry.chunkZ() - centerChunkZ));
                if (distance > radius) {
                    skippedDistance++;
                    continue;
                }
            }
            renderEntries.add(entry);
        }
        return new RenderSnapshot(renderEntries, skippedDimension, skippedDistance);
    }

    public synchronized StatusSnapshot createStatusSnapshot() {
        int quads = 0;
        int vertices = 0;
        for (Entry entry : this.entries) {
            quads += entry.quadCount();
            vertices += entry.vertexCount();
        }
        return new StatusSnapshot(
                this.entries.size(),
                quads,
                vertices,
                this.clearCount,
                this.lastBuildResult
        );
    }

    public record Entry(
            String dimension,
            int sectionId,
            long position,
            int chunkX,
            int chunkZ,
            int quadCount,
            int[] vertexData
    ) {
        int vertexCount() {
            return this.vertexData == null ? 0 : this.vertexData.length / VERTEX_STRIDE_INTS;
        }
    }

    public record RenderSnapshot(List<Entry> entries, int skippedDimension, int skippedDistance) {
    }

    public record StatusSnapshot(
            int cacheSections,
            int cacheQuads,
            int cacheVertices,
            long clearCount,
            ForgeGpuGeometryVisualizationResult lastBuildResult
    ) {
    }
}
