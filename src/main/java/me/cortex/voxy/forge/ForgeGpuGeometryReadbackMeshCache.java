package me.cortex.voxy.forge;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ForgeGpuGeometryReadbackMeshCache {
    static final String SOURCE = "GL_HEAP_READBACK";

    private final LinkedHashMap<ForgeCpuMeshCache.Key, ForgeCpuBuiltSection> entries = new LinkedHashMap<>(32, 0.75F, true);
    private ForgeGpuGeometryReadbackMeshResult lastBuildResult = ForgeGpuGeometryReadbackMeshResult.failure("not-built");
    private long clearCount;

    public synchronized void replaceAll(List<ForgeCpuBuiltSection> sections, ForgeGpuGeometryReadbackMeshResult result) {
        this.clearEntriesOnly();
        if (sections != null) {
            for (ForgeCpuBuiltSection section : sections) {
                if (section == null || section.meshBuffer() == null || section.meshBuffer().isClosed() || section.vertexCount() == 0) {
                    if (section != null) {
                        section.close();
                    }
                    continue;
                }
                ForgeCpuBuiltSection previous = this.entries.put(ForgeCpuMeshCache.Key.from(section), section);
                if (previous != null) {
                    previous.close();
                }
            }
        }
        this.lastBuildResult = result == null ? ForgeGpuGeometryReadbackMeshResult.failure("missing-result") : result;
    }

    public synchronized void clear() {
        this.clearEntriesOnly();
        this.lastBuildResult = ForgeGpuGeometryReadbackMeshResult.failure("cache-cleared");
        this.clearCount++;
    }

    public synchronized ForgeCpuMeshCache.RenderSnapshot createUploadSnapshot(String dimension, int maxUploadedEntries) {
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
        return new ForgeCpuMeshCache.RenderSnapshot(snapshot, skippedByDimension, 0, skippedReleased, limitedEntries);
    }

    public synchronized Set<ForgeCpuMeshCache.Key> createKeySnapshot(String dimension) {
        var keys = new HashSet<ForgeCpuMeshCache.Key>();
        for (Map.Entry<ForgeCpuMeshCache.Key, ForgeCpuBuiltSection> entry : this.entries.entrySet()) {
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

    public synchronized StatusSnapshot createStatusSnapshot() {
        int quads = 0;
        int vertices = 0;
        long bytes = 0;
        for (ForgeCpuBuiltSection section : this.entries.values()) {
            quads += section.quadCount();
            vertices += section.vertexCount();
            bytes += section.sizeBytes();
        }
        return new StatusSnapshot(this.entries.size(), quads, vertices, bytes, this.clearCount, this.lastBuildResult);
    }

    private void clearEntriesOnly() {
        for (ForgeCpuBuiltSection section : this.entries.values()) {
            section.close();
        }
        this.entries.clear();
    }

    public record StatusSnapshot(
            int cacheSections,
            int cacheQuads,
            int cacheVertices,
            long cacheBytes,
            long clearCount,
            ForgeGpuGeometryReadbackMeshResult lastBuildResult
    ) {
    }
}
