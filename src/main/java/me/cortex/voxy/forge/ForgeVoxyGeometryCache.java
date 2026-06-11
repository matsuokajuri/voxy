package me.cortex.voxy.forge;

import me.cortex.voxy.config.ForgeVoxyConfig;

import java.util.Iterator;
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

    public synchronized StatusSnapshot createStatusSnapshot() {
        this.trimToLimit();
        long totalQuads = 0;
        long totalBytes = 0;
        long totalOccupancyBytes = 0;
        long finalFormatEntries = 0;
        long partialFormatEntries = 0;
        long partialOriginalBitLayoutEntries = 0;
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
            String geometryFormat,
            String firstEntryPosition,
            String firstEntryAabb,
            String firstEntryOffsets,
            String firstEntryNamedOffsets,
            String sampleRecordHex,
            String sampleDecodedRecord
    ) {
    }

    private record Key(String dimension, long position) {
        private static Key from(ForgeVoxyBuiltSection section) {
            return new Key(section.dimension(), section.position());
        }
    }
}
