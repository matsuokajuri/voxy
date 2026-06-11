package me.cortex.voxy.forge;

import me.cortex.voxy.config.ForgeVoxyConfig;
import me.cortex.voxy.config.SimpleGpuMeshLoadedChunkSkipMode;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ForgeGpuMeshCache {
    private static final int DEFAULT_MAX_BUFFERS = 2048;

    private final int fallbackMaxBuffers;
    private final LinkedHashMap<ForgeCpuMeshCache.Key, ForgeGpuMeshBuffer> buffers;
    private String activeDimension;

    ForgeGpuMeshCache() {
        this(DEFAULT_MAX_BUFFERS);
    }

    ForgeGpuMeshCache(int maxBuffers) {
        this.fallbackMaxBuffers = Math.max(1, maxBuffers);
        this.buffers = new LinkedHashMap<>(64, 0.75f, true);
    }

    public synchronized void setActiveDimension(String dimension) {
        if (dimension == null) {
            return;
        }
        if (this.activeDimension == null) {
            this.activeDimension = dimension;
            return;
        }
        if (!this.activeDimension.equals(dimension)) {
            this.clear();
            this.activeDimension = dimension;
        }
    }

    public synchronized boolean hasMatching(ForgeCpuBuiltSection section, int colorModeStamp) {
        ForgeGpuMeshBuffer buffer = this.buffers.get(ForgeCpuMeshCache.Key.from(section));
        return buffer != null && !buffer.isClosed()
                && buffer.sourceHash() == section.sourceHash()
                && buffer.colorModeStamp() == colorModeStamp;
    }

    public synchronized void put(ForgeGpuMeshBuffer buffer) {
        ForgeGpuMeshBuffer previous = this.buffers.put(buffer.key(), buffer);
        if (previous != null) {
            previous.close();
        }
        this.trimToLimit();
    }

    public synchronized void clear() {
        for (ForgeGpuMeshBuffer buffer : this.buffers.values()) {
            buffer.close();
        }
        this.buffers.clear();
    }

    public synchronized void retainOnly(String dimension, Set<ForgeCpuMeshCache.Key> liveCpuKeys) {
        Iterator<Map.Entry<ForgeCpuMeshCache.Key, ForgeGpuMeshBuffer>> iterator = this.buffers.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<ForgeCpuMeshCache.Key, ForgeGpuMeshBuffer> entry = iterator.next();
            ForgeCpuMeshCache.Key key = entry.getKey();
            if (!key.dimension().equals(dimension) || !liveCpuKeys.contains(key)) {
                entry.getValue().close();
                iterator.remove();
            }
        }
    }

    public synchronized RenderSnapshot createRenderSnapshot(String dimension, int centerChunkX, int centerChunkZ, int radiusChunks, int maxRenderedBuffers) {
        var snapshot = new ArrayList<ForgeGpuMeshBuffer>();
        int skippedByDimension = 0;
        int skippedByDistance = 0;
        int skippedReleased = 0;
        int limitedBuffers = 0;
        int maxBuffers = Math.max(1, maxRenderedBuffers);
        for (ForgeGpuMeshBuffer buffer : this.buffers.values()) {
            if (!buffer.dimension().equals(dimension)) {
                skippedByDimension++;
                continue;
            }
            if (Math.abs(buffer.chunkX() - centerChunkX) > radiusChunks || Math.abs(buffer.chunkZ() - centerChunkZ) > radiusChunks) {
                skippedByDistance++;
                continue;
            }
            if (buffer.isClosed() || buffer.vertexBuffer() == null) {
                skippedReleased++;
                continue;
            }
            if (snapshot.size() >= maxBuffers) {
                limitedBuffers++;
                continue;
            }
            snapshot.add(buffer);
        }
        return new RenderSnapshot(snapshot, skippedByDimension, skippedByDistance, skippedReleased, limitedBuffers);
    }

    public synchronized VisibilitySnapshot createVisibilitySnapshot(
            String dimension,
            int centerChunkX,
            int centerChunkZ,
            int minDistanceChunks,
            int maxDistanceChunks,
            boolean renderLoadedChunks,
            SimpleGpuMeshLoadedChunkSkipMode loadedChunkSkipMode,
            int loadedChunkMargin,
            int clientRenderDistance,
            ForgeGpuMeshLoadedChunkFilter.ChunkLoadChecker chunkLoadChecker
    ) {
        this.trimToLimit();
        int minDistance = Math.max(0, Math.min(minDistanceChunks, maxDistanceChunks));
        int maxDistance = Math.max(0, maxDistanceChunks);
        var cachedChunks = new HashSet<Long>();
        var windowChunks = new HashSet<Long>();
        var loadedStateChunks = new HashSet<Long>();
        var vanillaRenderDistanceChunks = new HashSet<Long>();
        var outsideVanillaRenderDistanceChunks = new HashSet<Long>();
        var skippedLoadedStateChunks = new HashSet<Long>();
        var skippedRenderDistanceChunks = new HashSet<Long>();
        var renderableChunks = new HashSet<Long>();
        int cachedBuffers = 0;
        int candidateBuffers = 0;
        int renderableBuffers = 0;
        int skippedByDimension = 0;
        int skippedNear = 0;
        int skippedFar = 0;
        int skippedLoadedState = 0;
        int skippedRenderDistance = 0;
        int skippedReleased = 0;
        int skippedTranslucent = 0;
        int nearestRenderableDistance = Integer.MAX_VALUE;
        int farthestRenderableDistance = -1;

        for (ForgeGpuMeshBuffer buffer : this.buffers.values()) {
            if (!buffer.dimension().equals(dimension)) {
                skippedByDimension++;
                continue;
            }
            if (buffer.isClosed() || buffer.vertexBuffer() == null) {
                skippedReleased++;
                continue;
            }

            long chunkKey = chunkKey(buffer.chunkX(), buffer.chunkZ());
            cachedBuffers++;
            cachedChunks.add(chunkKey);

            int distance = Math.max(Math.abs(buffer.chunkX() - centerChunkX), Math.abs(buffer.chunkZ() - centerChunkZ));
            if (distance < minDistance) {
                skippedNear++;
                continue;
            }
            if (distance > maxDistance) {
                skippedFar++;
                continue;
            }

            candidateBuffers++;
            windowChunks.add(chunkKey);
            if (ForgeGpuMeshLoadedChunkFilter.isLoaded(chunkLoadChecker, buffer.chunkX(), buffer.chunkZ())) {
                loadedStateChunks.add(chunkKey);
            }
            if (ForgeGpuMeshLoadedChunkFilter.isInsideRenderDistance(buffer.chunkX(), buffer.chunkZ(), centerChunkX, centerChunkZ, clientRenderDistance, 0)) {
                vanillaRenderDistanceChunks.add(chunkKey);
            } else {
                outsideVanillaRenderDistanceChunks.add(chunkKey);
            }

            ForgeGpuMeshLoadedChunkFilter.SkipReason skipReason = ForgeGpuMeshLoadedChunkFilter.evaluate(
                    renderLoadedChunks,
                    loadedChunkSkipMode,
                    loadedChunkMargin,
                    buffer.chunkX(),
                    buffer.chunkZ(),
                    centerChunkX,
                    centerChunkZ,
                    clientRenderDistance,
                    chunkLoadChecker
            );
            if (skipReason == ForgeGpuMeshLoadedChunkFilter.SkipReason.LOADED_STATE) {
                skippedLoadedState++;
                skippedLoadedStateChunks.add(chunkKey);
                continue;
            }
            if (skipReason == ForgeGpuMeshLoadedChunkFilter.SkipReason.RENDER_DISTANCE) {
                skippedRenderDistance++;
                skippedRenderDistanceChunks.add(chunkKey);
                continue;
            }
            if (buffer.layer() == ForgeCpuMeshLayer.TRANSLUCENT) {
                skippedTranslucent++;
                continue;
            }

            renderableBuffers++;
            renderableChunks.add(chunkKey);
            nearestRenderableDistance = Math.min(nearestRenderableDistance, distance);
            farthestRenderableDistance = Math.max(farthestRenderableDistance, distance);
        }

        if (renderableChunks.isEmpty()) {
            nearestRenderableDistance = -1;
        }

        return new VisibilitySnapshot(
                cachedBuffers,
                cachedChunks.size(),
                candidateBuffers,
                windowChunks.size(),
                loadedStateChunks.size(),
                vanillaRenderDistanceChunks.size(),
                outsideVanillaRenderDistanceChunks.size(),
                renderableBuffers,
                renderableChunks.size(),
                skippedByDimension,
                skippedNear,
                skippedFar,
                skippedLoadedState,
                skippedLoadedStateChunks.size(),
                skippedRenderDistance,
                skippedRenderDistanceChunks.size(),
                skippedReleased,
                skippedTranslucent,
                nearestRenderableDistance,
                farthestRenderableDistance
        );
    }

    public synchronized StatusSnapshot createStatusSnapshot() {
        this.trimToLimit();
        long vertices = 0;
        long quads = 0;
        long bytes = 0;
        Map<String, Integer> dimensionCounts = new LinkedHashMap<>();
        EnumMap<ForgeCpuMeshLayer, Integer> layerCounts = new EnumMap<>(ForgeCpuMeshLayer.class);

        for (ForgeGpuMeshBuffer buffer : this.buffers.values()) {
            vertices += buffer.vertexCount();
            quads += buffer.quadCount();
            bytes += buffer.sizeBytes();
            dimensionCounts.merge(buffer.dimension(), 1, Integer::sum);
            layerCounts.merge(buffer.layer(), 1, Integer::sum);
        }

        return new StatusSnapshot(
                this.buffers.size(),
                this.getConfiguredMaxBuffers(),
                vertices,
                quads,
                bytes,
                formatDimensionCounts(dimensionCounts),
                formatLayerCounts(layerCounts)
        );
    }

    private void trimToLimit() {
        int maxBuffers = this.getConfiguredMaxBuffers();
        Iterator<Map.Entry<ForgeCpuMeshCache.Key, ForgeGpuMeshBuffer>> iterator = this.buffers.entrySet().iterator();
        while (this.buffers.size() > maxBuffers && iterator.hasNext()) {
            Map.Entry<ForgeCpuMeshCache.Key, ForgeGpuMeshBuffer> eldest = iterator.next();
            eldest.getValue().close();
            iterator.remove();
        }
    }

    private int getConfiguredMaxBuffers() {
        try {
            return Math.min(8192, Math.max(1, ForgeVoxyConfig.SIMPLE_GPU_MESH_MAX_BUFFERS.get()));
        } catch (IllegalStateException e) {
            return this.fallbackMaxBuffers;
        }
    }

    private static String formatDimensionCounts(Map<String, Integer> counts) {
        if (counts.isEmpty()) {
            return "none";
        }

        StringBuilder builder = new StringBuilder();
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            if (builder.length() != 0) {
                builder.append(',');
            }
            builder.append(entry.getKey()).append('=').append(entry.getValue());
        }
        return builder.toString();
    }

    private static String formatLayerCounts(EnumMap<ForgeCpuMeshLayer, Integer> counts) {
        if (counts.isEmpty()) {
            return "none";
        }

        StringBuilder builder = new StringBuilder();
        for (ForgeCpuMeshLayer layer : ForgeCpuMeshLayer.values()) {
            Integer count = counts.get(layer);
            if (count == null || count == 0) {
                continue;
            }
            if (builder.length() != 0) {
                builder.append(',');
            }
            builder.append(layer.displayName).append('=').append(count);
        }
        return builder.toString();
    }

    private static long chunkKey(int chunkX, int chunkZ) {
        return ((long) chunkX & 0xFFFFFFFFL) | (((long) chunkZ & 0xFFFFFFFFL) << 32);
    }

    public record StatusSnapshot(
            int buffers,
            int maxBuffers,
            long totalVertices,
            long totalQuads,
            long totalBytes,
            String dimensions,
            String layers
    ) {
    }

    public record RenderSnapshot(
            List<ForgeGpuMeshBuffer> buffers,
            int skippedByDimension,
            int skippedByDistance,
            int skippedReleased,
            int limitedBuffers
    ) {
    }

    public record VisibilitySnapshot(
            int cachedBuffers,
            int cachedChunks,
            int candidateBuffers,
            int chunksWithinDistanceWindow,
            int loadedStateChunks,
            int chunksWithinVanillaRenderDistance,
            int chunksOutsideVanillaRenderDistance,
            int renderableBuffers,
            int renderableChunks,
            int skippedByDimension,
            int skippedNear,
            int skippedFar,
            int skippedLoadedState,
            int skippedLoadedStateChunks,
            int skippedRenderDistance,
            int skippedRenderDistanceChunks,
            int skippedReleased,
            int skippedTranslucent,
            int nearestRenderableDistance,
            int farthestRenderableDistance
    ) {
        public int skippedLoaded() {
            return this.skippedLoadedState + this.skippedRenderDistance;
        }

        public int skippedLoadedChunks() {
            return this.skippedLoadedStateChunks + this.skippedRenderDistanceChunks;
        }
    }
}
