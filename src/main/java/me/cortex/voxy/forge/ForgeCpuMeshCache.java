package me.cortex.voxy.forge;

import java.util.EnumMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ForgeCpuMeshCache {
    private static final int DEFAULT_MAX_ENTRIES = 512;

    private final int maxEntries;
    private final LinkedHashMap<Key, ForgeCpuBuiltSection> entries;
    private String activeDimension;

    public ForgeCpuMeshCache() {
        this(DEFAULT_MAX_ENTRIES);
    }

    public ForgeCpuMeshCache(int maxEntries) {
        this.maxEntries = Math.max(1, maxEntries);
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

    public synchronized StatusSnapshot createStatusSnapshot() {
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
                this.maxEntries,
                vertices,
                quads,
                bytes,
                formatDimensionCounts(dimensionCounts),
                formatLayerCounts(layerCounts)
        );
    }

    private void trimToLimit() {
        Iterator<Map.Entry<Key, ForgeCpuBuiltSection>> iterator = this.entries.entrySet().iterator();
        while (this.entries.size() > this.maxEntries && iterator.hasNext()) {
            Map.Entry<Key, ForgeCpuBuiltSection> eldest = iterator.next();
            eldest.getValue().close();
            iterator.remove();
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

    public record Key(
            String dimension,
            int chunkX,
            int chunkZ,
            long sectionPosition,
            ForgeCpuMeshLayer layer
    ) {
        private static Key from(ForgeCpuBuiltSection section) {
            return new Key(section.dimension(), section.chunkX(), section.chunkZ(), section.sectionPosition(), section.layer());
        }
    }
}
