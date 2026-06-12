package me.cortex.voxy.forge;

import java.util.List;

final class ForgeGpuGeometryAuditor {
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
            ForgeGpuGeometryDecodedMetadata metadata = ForgeGpuGeometryDecodedMetadata.decode(words);
            audit.lastAuditedPosition = metadata.position();
            audit.lastAuditedGeometryPtr = metadata.geometryPtr();
            audit.lastMetadataWords = formatMetadata(words);
            audit.lastDecodedMetadata = metadata.format();

            String metadataError = metadata.validate(heap, geometryManager, sectionId, words);
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

    private static void auditRecords(ForgeGpuGeometryHeap heap, ForgeGpuGeometryDecodedMetadata metadata, MutableAudit audit) {
        StringBuilder decoded = new StringBuilder();
        int remaining = MAX_RECORD_READBACK;
        for (int bucket = 0; bucket < ForgeGpuGeometryDecodedMetadata.BUCKET_COUNT && remaining > 0; bucket++) {
            int start = metadata.offsets()[bucket];
            int end = bucket + 1 < ForgeGpuGeometryDecodedMetadata.BUCKET_COUNT ? metadata.offsets()[bucket + 1] : metadata.itemCount();
            int bucketSize = end - start;
            if (bucketSize <= 0) {
                audit.emptyBuckets++;
                continue;
            }
            audit.nonEmptyBuckets++;
            int count = Math.min(Math.min(RECORDS_PER_BUCKET, bucketSize), remaining);
            long[] records = heap.readbackGeometry(metadata.geometryPtr() + start, count);
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
