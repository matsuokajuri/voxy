package me.cortex.voxy.forge;

import me.cortex.voxy.config.ForgeVoxyConfig;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ForgeCpuMeshCache {
    private static final int DEFAULT_MAX_ENTRIES = 2048;

    private final int fallbackMaxEntries;
    private final LinkedHashMap<Key, ForgeCpuBuiltSection> entries;
    private String activeDimension;

    public ForgeCpuMeshCache() {
        this(DEFAULT_MAX_ENTRIES);
    }

    public ForgeCpuMeshCache(int maxEntries) {
        this.fallbackMaxEntries = Math.max(1, maxEntries);
        this.entries = new LinkedHashMap<>(64, 0.75f, true);
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

    public synchronized int putAll(List<ForgeCpuBuiltSection> sections) {
        int written = 0;
        for (ForgeCpuBuiltSection section : sections) {
            this.put(section);
            written++;
        }
        return written;
    }

    public synchronized void put(ForgeCpuBuiltSection section) {
        Key key = Key.from(section);
        ForgeCpuBuiltSection previous = this.entries.put(key, section);
        if (previous != null) {
            previous.close();
        }
        this.trimToLimit();
    }

    public synchronized void clear() {
        for (ForgeCpuBuiltSection section : this.entries.values()) {
            section.close();
        }
        this.entries.clear();
    }

    public synchronized void clearDimension(String dimension) {
        Iterator<Map.Entry<Key, ForgeCpuBuiltSection>> iterator = this.entries.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Key, ForgeCpuBuiltSection> entry = iterator.next();
            if (entry.getKey().dimension.equals(dimension)) {
                entry.getValue().close();
                iterator.remove();
            }
        }
    }

    public synchronized boolean hasChunkEntries(String dimension, int chunkX, int chunkZ) {
        for (ForgeCpuBuiltSection section : this.entries.values()) {
            if (section.dimension().equals(dimension) && section.chunkX() == chunkX && section.chunkZ() == chunkZ) {
                ForgeCpuMeshBuffer buffer = section.meshBuffer();
                if (buffer != null && !buffer.isClosed()) {
                    return true;
                }
            }
        }
        return false;
    }

    public synchronized List<ForgeCpuBuiltSection> snapshotNear(String dimension, int centerChunkX, int centerChunkZ, int radiusChunks) {
        var snapshot = new ArrayList<ForgeCpuBuiltSection>();
        for (ForgeCpuBuiltSection section : this.entries.values()) {
            if (!section.dimension().equals(dimension)) {
                continue;
            }
            if (Math.abs(section.chunkX() - centerChunkX) > radiusChunks || Math.abs(section.chunkZ() - centerChunkZ) > radiusChunks) {
                continue;
            }
            if (section.meshBuffer() == null || section.meshBuffer().isClosed()) {
                continue;
            }
            snapshot.add(section);
        }
        return snapshot;
    }

    public synchronized RenderSnapshot createRenderSnapshot(String dimension, int centerChunkX, int centerChunkZ, int radiusChunks, int maxRenderedEntries) {
        var snapshot = new ArrayList<ForgeCpuBuiltSection>();
        int skippedByDimension = 0;
        int skippedByDistance = 0;
        int skippedReleased = 0;
        int limitedEntries = 0;
        int maxEntries = Math.max(1, maxRenderedEntries);
        for (ForgeCpuBuiltSection section : this.entries.values()) {
            if (!section.dimension().equals(dimension)) {
                skippedByDimension++;
                continue;
            }
            if (Math.abs(section.chunkX() - centerChunkX) > radiusChunks || Math.abs(section.chunkZ() - centerChunkZ) > radiusChunks) {
                skippedByDistance++;
                continue;
            }
            ForgeCpuMeshBuffer buffer = section.meshBuffer();
            if (buffer == null || buffer.isClosed()) {
                skippedReleased++;
                continue;
            }
            if (snapshot.size() >= maxEntries) {
                limitedEntries++;
                continue;
            }
            snapshot.add(section);
        }
        return new RenderSnapshot(snapshot, skippedByDimension, skippedByDistance, skippedReleased, limitedEntries);
    }

    public synchronized RenderSnapshot createUploadSnapshot(String dimension, int maxUploadedEntries) {
        this.trimToLimit();
        var snapshot = new ArrayList<ForgeCpuBuiltSection>();
        int skippedByDimension = 0;
        int skippedReleased = 0;
        int limitedEntries = 0;
        int maxEntries = Math.max(1, maxUploadedEntries);
        for (ForgeCpuBuiltSection section : this.entries.values()) {
            if (!section.dimension().equals(dimension)) {
                skippedByDimension++;
                continue;
            }
            ForgeCpuMeshBuffer buffer = section.meshBuffer();
            if (buffer == null || buffer.isClosed()) {
                skippedReleased++;
                continue;
            }
            if (snapshot.size() >= maxEntries) {
                limitedEntries++;
                continue;
            }
            snapshot.add(section);
        }
        return new RenderSnapshot(snapshot, skippedByDimension, 0, skippedReleased, limitedEntries);
    }

    public synchronized Set<Key> createKeySnapshot(String dimension) {
        this.trimToLimit();
        var keys = new HashSet<Key>();
        for (Map.Entry<Key, ForgeCpuBuiltSection> entry : this.entries.entrySet()) {
            if (!entry.getKey().dimension().equals(dimension)) {
                continue;
            }
            ForgeCpuMeshBuffer buffer = entry.getValue().meshBuffer();
            if (buffer == null || buffer.isClosed()) {
                continue;
            }
            keys.add(entry.getKey());
        }
        return keys;
    }

    public synchronized BoundsSnapshot createBoundsSnapshot(String dimension, int chunkX, int chunkZ) {
        int entries = 0;
        long vertices = 0;
        float minX = Float.POSITIVE_INFINITY;
        float minY = Float.POSITIVE_INFINITY;
        float minZ = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY;
        float maxY = Float.NEGATIVE_INFINITY;
        float maxZ = Float.NEGATIVE_INFINITY;

        for (ForgeCpuBuiltSection section : this.entries.values()) {
            if (!section.dimension().equals(dimension) || section.chunkX() != chunkX || section.chunkZ() != chunkZ) {
                continue;
            }
            ForgeCpuMeshBuffer buffer = section.meshBuffer();
            if (buffer == null || buffer.isClosed()) {
                continue;
            }
            int[] data = buffer.vertexData();
            if (data == null) {
                continue;
            }

            entries++;
            vertices += buffer.vertexCount();
            for (int vertex = 0; vertex < buffer.vertexCount(); vertex++) {
                int offset = vertex * ForgeCpuMeshBuffer.VERTEX_STRIDE_INTS;
                float x = Float.intBitsToFloat(data[offset]);
                float y = Float.intBitsToFloat(data[offset + 1]);
                float z = Float.intBitsToFloat(data[offset + 2]);
                minX = Math.min(minX, x);
                minY = Math.min(minY, y);
                minZ = Math.min(minZ, z);
                maxX = Math.max(maxX, x);
                maxY = Math.max(maxY, y);
                maxZ = Math.max(maxZ, z);
            }
        }

        if (entries == 0) {
            return BoundsSnapshot.empty(dimension, chunkX, chunkZ);
        }
        return new BoundsSnapshot(true, dimension, chunkX, chunkZ, entries, vertices, minX, minY, minZ, maxX, maxY, maxZ);
    }

    public synchronized StatusSnapshot createStatusSnapshot() {
        this.trimToLimit();
        long vertices = 0;
        long quads = 0;
        long bytes = 0;
        Map<String, Integer> dimensionCounts = new LinkedHashMap<>();
        EnumMap<ForgeCpuMeshLayer, Integer> layerCounts = new EnumMap<>(ForgeCpuMeshLayer.class);

        for (ForgeCpuBuiltSection section : this.entries.values()) {
            vertices += section.vertexCount();
            quads += section.quadCount();
            bytes += section.sizeBytes();
            dimensionCounts.merge(section.dimension(), 1, Integer::sum);
            layerCounts.merge(section.layer(), 1, Integer::sum);
        }

        return new StatusSnapshot(
                this.entries.size(),
                this.getConfiguredMaxEntries(),
                vertices,
                quads,
                bytes,
                formatDimensionCounts(dimensionCounts),
                formatLayerCounts(layerCounts)
        );
    }

    private void trimToLimit() {
        int maxEntries = this.getConfiguredMaxEntries();
        Iterator<Map.Entry<Key, ForgeCpuBuiltSection>> iterator = this.entries.entrySet().iterator();
        while (this.entries.size() > maxEntries && iterator.hasNext()) {
            Map.Entry<Key, ForgeCpuBuiltSection> eldest = iterator.next();
            eldest.getValue().close();
            iterator.remove();
        }
    }

    private int getConfiguredMaxEntries() {
        try {
            return Math.min(8192, Math.max(1, ForgeVoxyConfig.CPU_MESH_CACHE_MAX_ENTRIES.get()));
        } catch (IllegalStateException e) {
            return this.fallbackMaxEntries;
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

    public record StatusSnapshot(
            int entries,
            int maxEntries,
            long totalVertices,
            long totalQuads,
            long totalBytes,
            String dimensions,
            String layers
    ) {
    }

    public record BoundsSnapshot(
            boolean available,
            String dimension,
            int chunkX,
            int chunkZ,
            int entries,
            long vertices,
            float minX,
            float minY,
            float minZ,
            float maxX,
            float maxY,
            float maxZ
    ) {
        private static BoundsSnapshot empty(String dimension, int chunkX, int chunkZ) {
            return new BoundsSnapshot(false, dimension, chunkX, chunkZ, 0, 0, 0, 0, 0, 0, 0, 0);
        }
    }

    public record RenderSnapshot(
            List<ForgeCpuBuiltSection> sections,
            int skippedByDimension,
            int skippedByDistance,
            int skippedReleased,
            int limitedEntries
    ) {
    }

    public record Key(
            String dimension,
            int chunkX,
            int chunkZ,
            long sectionPosition,
            ForgeCpuMeshLayer layer
    ) {
        public static Key from(ForgeCpuBuiltSection section) {
            return new Key(section.dimension(), section.chunkX(), section.chunkZ(), section.sectionPosition(), section.layer());
        }
    }
}
