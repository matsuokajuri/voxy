package me.cortex.voxy.forge;

import java.util.Arrays;
import java.util.List;

final class ForgeGpuGeometryAuditor {
    private static final int BUCKET_COUNT = 8;
    private static final int RECORDS_PER_BUCKET = 2;
    private static final int MAX_RECORD_READBACK = 64;

    private ForgeGpuGeometryAuditor() {
    }

    static ForgeGpuGeometryAuditResult audit(
            ForgeGpuGeometryHeap heap,
            List<Integer> sectionIds,
            ForgeSectionGeometryManager geometryManager,
            int maxSections
    ) {
        if (!heap.isCreated()) {
            return ForgeGpuGeometryAuditResult.failure("heap-not-created");
        }
        if (sectionIds == null || sectionIds.isEmpty()) {
            return ForgeGpuGeometryAuditResult.failure("no-uploaded-sections");
        }

        MutableAudit audit = new MutableAudit();
        int limit = Math.min(4, Math.max(1, maxSections));
        for (Integer id : sectionIds) {
            if (id == null || id < 0) {
                continue;
            }
            auditSection(heap, geometryManager, id, audit);
            if (audit.auditedSections >= limit) {
                break;
            }
        }
        if (audit.auditedSections == 0) {
            return ForgeGpuGeometryAuditResult.failure("no-auditable-section");
        }
        return audit.toResult();
    }

    private static void auditSection(
            ForgeGpuGeometryHeap heap,
            ForgeSectionGeometryManager geometryManager,
            int sectionId,
            MutableAudit audit
    ) {
        audit.auditedSections++;
        audit.lastAuditedSectionId = sectionId;
        try {
            int[] words = heap.readbackMetadata(sectionId);
            DecodedMetadata metadata = decodeMetadata(words);
            audit.lastAuditedPosition = metadata.position;
            audit.lastAuditedGeometryPtr = metadata.geometryPtr;
            audit.lastMetadataWords = formatMetadata(words);
            audit.lastDecodedMetadata = metadata.format();

            String metadataError = validateMetadata(heap, geometryManager, sectionId, metadata, words);
            if (!"none".equals(metadataError)) {
                audit.invalidMetadata++;
                audit.lastAuditError = metadataError;
                return;
            }

            auditRecords(heap, metadata, audit);
        } catch (RuntimeException e) {
            audit.invalidMetadata++;
            audit.lastAuditError = e.getClass().getSimpleName() + ": " + e.getMessage();
        }
    }

    private static DecodedMetadata decodeMetadata(int[] words) {
        if (words == null || words.length != ForgeSectionGeometryMetadata.METADATA_WORDS) {
            throw new IllegalArgumentException("metadata readback did not return 8 words");
        }
        long position = ((long) words[0] << 32) | (words[1] & 0xFFFFFFFFL);
        int geometryPtr = words[3];
        int[] deltas = new int[] {
                low(words[4]),
                high(words[4]),
                low(words[5]),
                high(words[5]),
                low(words[6]),
                high(words[6]),
                low(words[7]),
                high(words[7])
        };
        int[] offsets = new int[BUCKET_COUNT];
        offsets[0] = 0;
        int cursor = 0;
        for (int i = 0; i < BUCKET_COUNT - 1; i++) {
            cursor += deltas[i];
            offsets[i + 1] = cursor;
        }
        int itemCount = cursor + deltas[BUCKET_COUNT - 1];
        return new DecodedMetadata(position, words[2], geometryPtr, offsets, deltas, itemCount);
    }

    private static String validateMetadata(
            ForgeGpuGeometryHeap heap,
            ForgeSectionGeometryManager geometryManager,
            int sectionId,
            DecodedMetadata metadata,
            int[] words
    ) {
        if ((Integer.toUnsignedLong(metadata.geometryPtr) & 127L) != 0L) {
            return "geometry pointer is not 128-item aligned";
        }
        if (metadata.itemCount < 0) {
            return "negative item count";
        }
        int previous = 0;
        for (int i = 0; i < metadata.offsets.length; i++) {
            int offset = metadata.offsets[i];
            if (offset < previous) {
                return "offsets are not monotonic at index " + i;
            }
            if (offset > metadata.itemCount) {
                return "offset " + i + " exceeds item count";
            }
            previous = offset;
        }
        long geometryEndBytes = (Integer.toUnsignedLong(metadata.geometryPtr) + metadata.itemCount) * ForgeGpuGeometryHeap.GEOMETRY_RECORD_BYTES;
        if (geometryEndBytes > heap.geometryCapacityBytes()) {
            return "geometry readback range exceeds heap capacity";
        }
        long metadataEndBytes = ((long) sectionId + 1L) * ForgeGpuGeometryHeap.METADATA_BYTES;
        if (metadataEndBytes > heap.metadataCapacityBytes()) {
            return "metadata readback range exceeds heap capacity";
        }

        int[] expectedWords = geometryManager.createMetadataWordsSnapshot(sectionId);
        if (expectedWords.length == words.length && !isAllZero(expectedWords)) {
            long expectedPosition = ((long) expectedWords[0] << 32) | (expectedWords[1] & 0xFFFFFFFFL);
            if (expectedPosition != metadata.position) {
                return "CPU metadata position cross-check mismatch";
            }
        }
        return "none";
    }

    private static void auditRecords(ForgeGpuGeometryHeap heap, DecodedMetadata metadata, MutableAudit audit) {
        StringBuilder decoded = new StringBuilder();
        int remaining = MAX_RECORD_READBACK;
        for (int bucket = 0; bucket < BUCKET_COUNT && remaining > 0; bucket++) {
            int start = metadata.offsets[bucket];
            int end = bucket + 1 < BUCKET_COUNT ? metadata.offsets[bucket + 1] : metadata.itemCount;
            int bucketSize = end - start;
            if (bucketSize <= 0) {
                audit.emptyBuckets++;
                continue;
            }
            audit.nonEmptyBuckets++;
            int count = Math.min(Math.min(RECORDS_PER_BUCKET, bucketSize), remaining);
            long[] records = heap.readbackGeometry(metadata.geometryPtr + start, count);
            for (int i = 0; i < records.length; i++) {
                long record = records[i];
                int length = ForgeVoxyQuadEncoder.extractLength(record);
                int width = ForgeVoxyQuadEncoder.extractWidth(record);
                audit.maxQuadLength = Math.max(audit.maxQuadLength, length);
                audit.maxQuadWidth = Math.max(audit.maxQuadWidth, width);
                if (!isRecordValid(record)) {
                    audit.invalidGeometryRecords++;
                    audit.lastAuditError = "invalid quad record in bucket " + bucket;
                }
                if (decoded.length() != 0) {
                    decoded.append(';');
                }
                decoded.append("bucket=").append(bucket)
                        .append(" index=").append(start + i)
                        .append(' ')
                        .append(ForgeVoxyQuadEncoder.formatRecordHex(record))
                        .append(' ')
                        .append(ForgeVoxyQuadEncoder.decodeRecord(record));
                audit.decodedRecords++;
                remaining--;
            }
        }
        audit.lastDecodedRecords = decoded.length() == 0 ? "none" : decoded.toString();
    }

    private static boolean isRecordValid(long record) {
        int face = ForgeVoxyQuadEncoder.extractFace(record);
        int length = ForgeVoxyQuadEncoder.extractLength(record);
        int width = ForgeVoxyQuadEncoder.extractWidth(record);
        int x = ForgeVoxyQuadEncoder.extractLocalX(record);
        int y = ForgeVoxyQuadEncoder.extractLocalY(record);
        int z = ForgeVoxyQuadEncoder.extractLocalZ(record);
        return face >= 0 && face <= 5
                && length >= 1 && length <= 16
                && width >= 1 && width <= 16
                && x >= 0 && x <= 31
                && y >= 0 && y <= 31
                && z >= 0 && z <= 31;
    }

    private static String formatMetadata(int[] words) {
        if (words == null || words.length == 0) {
            return "[]";
        }
        StringBuilder builder = new StringBuilder("[");
        for (int i = 0; i < words.length; i++) {
            if (i != 0) {
                builder.append(',');
            }
            builder.append(String.format("0x%08X", words[i]));
        }
        return builder.append(']').toString();
    }

    private static boolean isAllZero(int[] words) {
        for (int word : words) {
            if (word != 0) {
                return false;
            }
        }
        return true;
    }

    private static int low(int word) {
        return word & 0xFFFF;
    }

    private static int high(int word) {
        return (word >>> 16) & 0xFFFF;
    }

    private record DecodedMetadata(long position, int aabb, int geometryPtr, int[] offsets, int[] deltas, int itemCount) {
        private String format() {
            return "position=" + Long.toUnsignedString(this.position)
                    + " aabb=" + ForgeVoxyBuiltSectionBuilder.formatAabb(this.aabb)
                    + " geometryPtr=" + Integer.toUnsignedLong(this.geometryPtr)
                    + " offsets=" + Arrays.toString(this.offsets)
                    + " deltas=" + Arrays.toString(this.deltas)
                    + " itemCount=" + this.itemCount;
        }
    }

    private static final class MutableAudit {
        private int auditedSections;
        private int decodedRecords;
        private int invalidMetadata;
        private int invalidGeometryRecords;
        private int emptyBuckets;
        private int nonEmptyBuckets;
        private int maxQuadLength;
        private int maxQuadWidth;
        private int lastAuditedSectionId = -1;
        private long lastAuditedPosition;
        private int lastAuditedGeometryPtr = -1;
        private String lastAuditError = "none";
        private String lastMetadataWords = "none";
        private String lastDecodedMetadata = "none";
        private String lastDecodedRecords = "none";

        private ForgeGpuGeometryAuditResult toResult() {
            return ForgeGpuGeometryAuditResult.success(
                    this.auditedSections,
                    this.decodedRecords,
                    this.invalidMetadata,
                    this.invalidGeometryRecords,
                    this.emptyBuckets,
                    this.nonEmptyBuckets,
                    this.maxQuadLength,
                    this.maxQuadWidth,
                    this.lastAuditedSectionId,
                    this.lastAuditedPosition,
                    this.lastAuditedGeometryPtr,
                    this.lastAuditError,
                    this.lastMetadataWords,
                    this.lastDecodedMetadata,
                    this.lastDecodedRecords
            );
        }
    }
}
