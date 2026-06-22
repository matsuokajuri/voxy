package me.cortex.voxy.forge;

import me.cortex.voxy.config.ForgeVoxyConfig;

import java.util.HashSet;
import java.util.Iterator;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ForgeVoxyGeometryCache {
    private static final int FALLBACK_MAX_ENTRIES = 2048;

    private final LinkedHashMap<Key, ForgeVoxyBuiltSection> entries = new LinkedHashMap<>(64, 0.75f, true);
    private String activeDimension;
    private long evictedCount;
    private long closedCount;
    private long replacedCount;

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

    public synchronized int putAll(List<ForgeVoxyBuiltSection> sections) {
        int written = 0;
        for (ForgeVoxyBuiltSection section : sections) {
            this.put(section);
            written++;
        }
        return written;
    }

    public synchronized void put(ForgeVoxyBuiltSection section) {
        Key key = Key.from(section);
        ForgeVoxyBuiltSection previous = this.entries.put(key, section);
        if (previous != null) {
            previous.close();
            this.closedCount++;
            this.replacedCount++;
        }
        this.trimToLimit();
    }

    public synchronized void clear() {
        for (ForgeVoxyBuiltSection section : this.entries.values()) {
            section.close();
            this.closedCount++;
        }
        this.entries.clear();
    }

    public synchronized boolean hasChunkEntries(String dimension, int chunkX, int chunkZ) {
        for (ForgeVoxyBuiltSection section : this.entries.values()) {
            if (!section.dimension().equals(dimension) || section.chunkX() != chunkX || section.chunkZ() != chunkZ) {
                continue;
            }
            if (!section.isClosed() && !section.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    public synchronized List<ForgeVoxyBuiltSection> createChunkSnapshot(String dimension, int chunkX, int chunkZ) {
        this.trimToLimit();
        var snapshot = new ArrayList<ForgeVoxyBuiltSection>();
        for (ForgeVoxyBuiltSection section : this.entries.values()) {
            if (!section.dimension().equals(dimension) || section.chunkX() != chunkX || section.chunkZ() != chunkZ) {
                continue;
            }
            if (section.isClosed() || section.isEmpty() || section.geometryBuffer() == null || section.geometryBuffer().isClosed()) {
                continue;
            }
            snapshot.add(section);
        }
        return snapshot;
    }

    public synchronized List<ForgeVoxyBuiltSection> createAreaSnapshot(String dimension, int centerChunkX, int centerChunkZ, int radius, int maxEntries) {
        this.trimToLimit();
        var snapshot = new ArrayList<ForgeVoxyBuiltSection>();
        int limit = Math.max(1, maxEntries);
        for (ForgeVoxyBuiltSection section : this.entries.values()) {
            if (!section.dimension().equals(dimension)) {
                continue;
            }
            if (Math.abs(section.chunkX() - centerChunkX) > radius || Math.abs(section.chunkZ() - centerChunkZ) > radius) {
                continue;
            }
            if (section.isClosed() || section.isEmpty() || section.geometryBuffer() == null || section.geometryBuffer().isClosed()) {
                continue;
            }
            snapshot.add(section);
        }
        snapshot.sort(Comparator
                .comparingInt((ForgeVoxyBuiltSection section) -> chebyshevDistance(section, centerChunkX, centerChunkZ))
                .thenComparingInt(section -> manhattanDistance(section, centerChunkX, centerChunkZ))
                .thenComparingInt(ForgeVoxyBuiltSection::chunkX)
                .thenComparingInt(ForgeVoxyBuiltSection::chunkZ)
                .thenComparingLong(ForgeVoxyBuiltSection::position));
        if (snapshot.size() > limit) {
            return new ArrayList<>(snapshot.subList(0, limit));
        }
        return snapshot;
    }

    public synchronized ForgeVoxyBuiltSection findLiveSection(String dimension, long position) {
        ForgeVoxyBuiltSection section = this.entries.get(new Key(dimension, position));
        if (section == null || section.isClosed() || section.isEmpty() || section.geometryBuffer() == null || section.geometryBuffer().isClosed()) {
            return null;
        }
        return section;
    }

    public synchronized RenderSnapshot createUploadSnapshot(String dimension, int maxUploadedEntries) {
        this.trimToLimit();
        var snapshot = new ArrayList<ForgeVoxyBuiltSection>();
        int skippedByDimension = 0;
        int skippedReleased = 0;
        int limitedEntries = 0;
        int maxEntries = Math.max(1, maxUploadedEntries);
        for (ForgeVoxyBuiltSection section : this.entries.values()) {
            if (!section.dimension().equals(dimension)) {
                skippedByDimension++;
                continue;
            }
            if (section.isClosed() || section.isEmpty() || section.geometryBuffer() == null || section.geometryBuffer().isClosed()) {
                skippedReleased++;
                continue;
            }
            if (snapshot.size() >= maxEntries) {
                limitedEntries++;
                continue;
            }
            snapshot.add(section);
        }
        return new RenderSnapshot(snapshot, skippedByDimension, skippedReleased, limitedEntries);
    }

    public synchronized StatusSnapshot createStatusSnapshot() {
        this.trimToLimit();
        long totalQuads = 0;
        long totalBytes = 0;
        long totalOccupancyBytes = 0;
        long finalFormatEntries = 0;
        long partialFormatEntries = 0;
        long partialOriginalBitLayoutEntries = 0;
        long missingModelRecords = 0;
        long missingBiomeRecords = 0;
        long totalNaiveQuads = 0;
        long totalMergedQuads = 0;
        long totalCoveredQuadArea = 0;
        long totalSkippedTranslucent = 0;
        long totalSkippedNonMergeable = 0;
        var uniqueModelIds = new HashSet<Integer>();
        var uniqueBiomeIds = new HashSet<Integer>();
        String geometryFormat = "none";
        String firstPosition = "none";
        String firstAabb = "none";
        String firstOffsets = "none";
        String firstNamedOffsets = "none";
        String sampleRecordHex = "none";
        String sampleDecodedRecord = "none";

        for (ForgeVoxyBuiltSection section : this.entries.values()) {
            totalQuads += section.quadCount();
            totalBytes += section.geometryBytes();
            totalOccupancyBytes += section.occupancyBytes();
            totalNaiveQuads += section.naiveQuads();
            totalMergedQuads += section.mergedQuads();
            totalCoveredQuadArea += section.coveredQuadArea();
            totalSkippedTranslucent += section.skippedTranslucent();
            totalSkippedNonMergeable += section.skippedNonMergeable();
            ForgeVoxyGeometryBuffer buffer = section.geometryBuffer();
            if (buffer != null && !buffer.isClosed()) {
                for (long record : buffer.packedQuads()) {
                    int modelId = ForgeVoxyQuadEncoder.extractModelId(record);
                    int biomeId = ForgeVoxyQuadEncoder.extractBiomeId(record);
                    if (modelId == 0) {
                        missingModelRecords++;
                    } else {
                        uniqueModelIds.add(modelId);
                    }
                    uniqueBiomeIds.add(biomeId);
                }
            }
            if (ForgeVoxyGeometryBuffer.PARTIAL_ORIGINAL_BIT_LAYOUT_FORMAT.equals(section.geometryFormat())) {
                partialOriginalBitLayoutEntries++;
            }
            if (section.isFinalRendererFormat()) {
                finalFormatEntries++;
            } else {
                partialFormatEntries++;
            }
            if ("none".equals(firstPosition)) {
                geometryFormat = section.geometryFormat();
                firstPosition = Long.toUnsignedString(section.position());
                firstAabb = ForgeVoxyBuiltSectionBuilder.formatAabb(section.aabb());
                firstOffsets = ForgeVoxyBuiltSectionBuilder.formatOffsets(section.offsets());
                firstNamedOffsets = ForgeVoxyBuiltSectionBuilder.formatNamedOffsets(section.offsets());
                if (section.hasSampleRecord()) {
                    long record = section.sampleRecord();
                    sampleRecordHex = ForgeVoxyQuadEncoder.formatRecordHex(record);
                    sampleDecodedRecord = ForgeVoxyQuadEncoder.decodeRecord(record);
                }
            }
        }

        return new StatusSnapshot(
                this.entries.size(),
                this.getConfiguredMaxEntries(),
                totalQuads,
                totalBytes,
                totalOccupancyBytes,
                this.closedCount,
                this.evictedCount,
                this.replacedCount,
                finalFormatEntries,
                partialFormatEntries,
                partialOriginalBitLayoutEntries,
                uniqueModelIds.size(),
                missingModelRecords,
                uniqueBiomeIds.size(),
                missingBiomeRecords,
                ForgeVoxyModelIdMapper.INSTANCE.uniqueModelCount(),
                totalNaiveQuads,
                totalMergedQuads,
                totalQuads == 0 ? 0.0 : (double) totalCoveredQuadArea / totalQuads,
                totalSkippedTranslucent,
                totalSkippedNonMergeable,
                geometryFormat,
                firstPosition,
                firstAabb,
                firstOffsets,
                firstNamedOffsets,
                sampleRecordHex,
                sampleDecodedRecord
        );
    }

    private void trimToLimit() {
        int maxEntries = this.getConfiguredMaxEntries();
        Iterator<Map.Entry<Key, ForgeVoxyBuiltSection>> iterator = this.entries.entrySet().iterator();
        while (this.entries.size() > maxEntries && iterator.hasNext()) {
            Map.Entry<Key, ForgeVoxyBuiltSection> eldest = iterator.next();
            eldest.getValue().close();
            this.closedCount++;
            this.evictedCount++;
            iterator.remove();
        }
    }

    private int getConfiguredMaxEntries() {
        try {
            return Math.min(8192, Math.max(1, ForgeVoxyConfig.BUILT_SECTION_CACHE_MAX_ENTRIES.get()));
        } catch (IllegalStateException e) {
            return FALLBACK_MAX_ENTRIES;
        }
    }

    private static int chebyshevDistance(ForgeVoxyBuiltSection section, int centerChunkX, int centerChunkZ) {
        return Math.max(Math.abs(section.chunkX() - centerChunkX), Math.abs(section.chunkZ() - centerChunkZ));
    }

    private static int manhattanDistance(ForgeVoxyBuiltSection section, int centerChunkX, int centerChunkZ) {
        return Math.abs(section.chunkX() - centerChunkX) + Math.abs(section.chunkZ() - centerChunkZ);
    }

    public record StatusSnapshot(
            int entries,
            int maxEntries,
            long totalQuads,
            long totalGeometryBytes,
            long totalOccupancyBytes,
            long closedCount,
            long evictedCount,
            long replacedCount,
            long finalFormatEntries,
            long partialFormatEntries,
            long partialOriginalBitLayoutEntries,
            int totalUniqueModelIds,
            long missingModelRecords,
            int totalUniqueBiomeIds,
            long missingBiomeRecords,
            int runtimeModelMapperSize,
            long totalNaiveQuads,
            long totalMergedQuads,
            double totalAverageQuadArea,
            long totalSkippedTranslucent,
            long totalSkippedNonMergeable,
            String geometryFormat,
            String firstEntryPosition,
            String firstEntryAabb,
            String firstEntryOffsets,
            String firstEntryNamedOffsets,
            String sampleRecordHex,
            String sampleDecodedRecord
    ) {
    }

    public record RenderSnapshot(
            List<ForgeVoxyBuiltSection> sections,
            int skippedByDimension,
            int skippedReleased,
            int limitedEntries
    ) {
    }

    private record Key(String dimension, long position) {
        private static Key from(ForgeVoxyBuiltSection section) {
            return new Key(section.dimension(), section.position());
        }
    }
}
