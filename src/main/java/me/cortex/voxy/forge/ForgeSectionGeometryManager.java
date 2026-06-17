package me.cortex.voxy.forge;

import me.cortex.voxy.common.util.AllocationArena;
import me.cortex.voxy.common.util.HierarchicalBitSet;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;

public final class ForgeSectionGeometryManager {
    private static final int DEFAULT_MAX_SECTIONS = 1 << 20;
    private static final long DEFAULT_GEOMETRY_ITEM_LIMIT = 1L << 31;
    private static final int GEOMETRY_ELEMENT_SIZE_BYTES = Long.BYTES;

    private final int maxSections;
    private final long geometryItemLimit;
    private HierarchicalBitSet sectionIds;
    private AllocationArena geometryArena;
    private final ArrayList<ForgeSectionGeometryMetadata> metadataById = new ArrayList<>();
    private final LinkedHashMap<Key, Integer> positionToId = new LinkedHashMap<>();
    private final LinkedHashMap<Integer, ForgeSectionGeometryUploadIntent> uploadIntents = new LinkedHashMap<>();
    private final LinkedHashMap<Integer, ForgeSectionGeometryRemoveIntent> removeIntents = new LinkedHashMap<>();
    private final LinkedHashSet<Integer> dirtyMetadataIds = new LinkedHashSet<>();
    private String activeDimension;
    private long usedGeometryItems;
    private long totalUploads;
    private long totalRemoves;
    private long totalReplacements;
    private long clearCount;

    public ForgeSectionGeometryManager() {
        this(DEFAULT_MAX_SECTIONS, DEFAULT_GEOMETRY_ITEM_LIMIT);
    }

    ForgeSectionGeometryManager(int maxSections, long geometryItemLimit) {
        this.maxSections = maxSections;
        this.geometryItemLimit = geometryItemLimit;
        this.resetAllocators();
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

    public synchronized ConsumeResult consumeChunk(String dimension, int chunkX, int chunkZ, List<ForgeVoxyBuiltSection> sections) {
        this.setActiveDimension(dimension);
        int consumed = 0;
        int replaced = 0;
        int skippedClosed = 0;
        int skippedEmpty = 0;
        int skippedWrongChunk = 0;
        long geometryItems = 0;
        long geometryBytes = 0;
        var allocatedIds = new ArrayList<Integer>();

        for (ForgeVoxyBuiltSection section : sections) {
            if (section == null || section.isClosed()) {
                skippedClosed++;
                continue;
            }
            if (!dimension.equals(section.dimension()) || section.chunkX() != chunkX || section.chunkZ() != chunkZ) {
                skippedWrongChunk++;
                continue;
            }
            if (section.isEmpty() || section.geometryBuffer() == null || section.geometryBuffer().isClosed()) {
                skippedEmpty++;
                continue;
            }
            UploadResult result = this.uploadReplaceSection(section);
            consumed++;
            if (result.replaced()) {
                replaced++;
            }
            geometryItems += result.itemCount();
            geometryBytes += result.geometryBytes();
            allocatedIds.add(result.sectionId());
        }

        ForgeSectionGeometryStats status = this.createStatusSnapshot();
        return new ConsumeResult(
                dimension,
                chunkX,
                chunkZ,
                sections.size(),
                consumed,
                replaced,
                skippedClosed,
                skippedEmpty,
                skippedWrongChunk,
                geometryItems,
                geometryBytes,
                allocatedIds,
                status
        );
    }

    public synchronized SectionConsumeResult consumeSection(ForgeVoxyBuiltSection section) {
        if (section == null || section.isClosed()) {
            return new SectionConsumeResult(false, false, -1, 0, 0, "section-closed-or-null");
        }
        if (section.isEmpty() || section.geometryBuffer() == null || section.geometryBuffer().isClosed()) {
            return new SectionConsumeResult(false, false, -1, 0, 0, "section-empty-or-buffer-closed");
        }
        this.setActiveDimension(section.dimension());
        UploadResult result = this.uploadReplaceSection(section);
        return new SectionConsumeResult(
                true,
                result.replaced(),
                result.sectionId(),
                result.itemCount(),
                result.geometryBytes(),
                "ok"
        );
    }

    public synchronized boolean removeSection(String dimension, long position) {
        Integer id = this.positionToId.get(new Key(dimension, position));
        return id != null && this.removeSection(id);
    }

    public synchronized boolean hasSection(String dimension, long position) {
        return this.positionToId.containsKey(new Key(dimension, position));
    }

    public synchronized List<ForgeSectionGeometryUploadIntent> createUploadIntentSnapshot() {
        return new ArrayList<>(this.uploadIntents.values());
    }

    public synchronized List<ForgeSectionGeometryRemoveIntent> createRemoveIntentSnapshot() {
        return new ArrayList<>(this.removeIntents.values());
    }

    public synchronized List<Integer> createDirtyMetadataIdSnapshot() {
        return new ArrayList<>(this.dirtyMetadataIds);
    }

    public synchronized int[] createMetadataWordsSnapshot(int sectionId) {
        if (sectionId < 0 || sectionId >= this.metadataById.size()) {
            return new int[ForgeSectionGeometryMetadata.METADATA_WORDS];
        }
        ForgeSectionGeometryMetadata metadata = this.metadataById.get(sectionId);
        return metadata == null ? new int[ForgeSectionGeometryMetadata.METADATA_WORDS] : metadata.metadataWords();
    }

    public synchronized RealMetadataScan createRealMetadataScan(int maxAccepted) {
        int limit = Math.max(1, maxAccepted);
        var accepted = new ArrayList<RealMetadataSnapshot>();
        int candidateSectionCount = 0;
        int rejectedSectionCount = 0;
        int recordsRejectedNoMetadata = 0;
        int recordsRejectedNoGeometryPointer = 0;
        int recordsRejectedNoBucketOffsets = 0;
        int recordsRejectedNoVisibleBuckets = 0;
        int recordsRejectedUnsafeState = 0;
        int maxAcceptedSectionId = -1;
        String lastRejectedReason = "none";

        for (int sectionId = 0; sectionId < this.metadataById.size(); sectionId++) {
            ForgeSectionGeometryMetadata metadata = this.metadataById.get(sectionId);
            if (metadata == null) {
                continue;
            }
            candidateSectionCount++;

            ForgeSectionGeometryUploadIntent uploadIntent = this.uploadIntents.get(metadata.geometryPtr());
            if (uploadIntent == null) {
                recordsRejectedNoGeometryPointer++;
                rejectedSectionCount++;
                lastRejectedReason = "section-" + sectionId + ":missing-upload-intent";
                continue;
            }

            ForgeSectionGeometryMetadata.ValidationResult validation = this.validateMetadata(sectionId, metadata);
            if (!validation.valid()) {
                if (validation.error() != null && validation.error().contains("geometry pointer")) {
                    recordsRejectedNoGeometryPointer++;
                } else {
                    recordsRejectedUnsafeState++;
                }
                rejectedSectionCount++;
                lastRejectedReason = "section-" + sectionId + ":" + validation.error();
                continue;
            }

            int[] offsets;
            int[] deltas;
            int[] words;
            try {
                offsets = metadata.offsets();
                deltas = metadata.deltas();
                words = metadata.metadataWords();
            } catch (RuntimeException e) {
                recordsRejectedNoBucketOffsets++;
                rejectedSectionCount++;
                lastRejectedReason = "section-" + sectionId + ":metadata-decode-" + e.getClass().getSimpleName();
                continue;
            }

            if (offsets.length != 8 || deltas.length != 8 || words.length != ForgeSectionGeometryMetadata.METADATA_WORDS) {
                recordsRejectedNoBucketOffsets++;
                rejectedSectionCount++;
                lastRejectedReason = "section-" + sectionId + ":invalid-bucket-metadata";
                continue;
            }

            boolean hasOpaqueOrDirectionalBucket = false;
            for (int bucket = 1; bucket < deltas.length; bucket++) {
                if (deltas[bucket] > 0) {
                    hasOpaqueOrDirectionalBucket = true;
                    break;
                }
            }
            if (!hasOpaqueOrDirectionalBucket) {
                recordsRejectedNoVisibleBuckets++;
                rejectedSectionCount++;
                lastRejectedReason = "section-" + sectionId + ":no-non-translucent-buckets";
                continue;
            }

            accepted.add(new RealMetadataSnapshot(
                    sectionId,
                    metadata.dimension(),
                    metadata.chunkX(),
                    metadata.chunkZ(),
                    metadata.position(),
                    metadata.aabb(),
                    metadata.geometryPtr(),
                    metadata.itemCount(),
                    metadata.allocatedItems(),
                    offsets,
                    deltas,
                    words,
                    uploadIntent.recordHash()
            ));
            maxAcceptedSectionId = Math.max(maxAcceptedSectionId, sectionId);
            if (accepted.size() >= limit) {
                break;
            }
        }

        return new RealMetadataScan(
                "ForgeSectionGeometryManager.metadata-snapshot",
                candidateSectionCount,
                accepted.size(),
                rejectedSectionCount,
                recordsRejectedNoMetadata,
                recordsRejectedNoGeometryPointer,
                recordsRejectedNoBucketOffsets,
                recordsRejectedNoVisibleBuckets,
                recordsRejectedUnsafeState,
                maxAcceptedSectionId,
                lastRejectedReason,
                accepted
        );
    }

    public synchronized ForgeSectionGeometryStats createStatusSnapshot() {
        int sampleSectionId = -1;
        ForgeSectionGeometryMetadata sampleMetadata = null;
        for (int i = 0; i < this.metadataById.size(); i++) {
            ForgeSectionGeometryMetadata metadata = this.metadataById.get(i);
            if (metadata != null) {
                sampleSectionId = i;
                sampleMetadata = metadata;
                break;
            }
        }

        int metadataValid = 0;
        int metadataInvalid = 0;
        String lastMetadataError = "none";
        for (int i = 0; i < this.metadataById.size(); i++) {
            ForgeSectionGeometryMetadata metadata = this.metadataById.get(i);
            if (metadata == null) {
                continue;
            }
            ForgeSectionGeometryMetadata.ValidationResult validation = this.validateMetadata(i, metadata);
            if (validation.valid()) {
                metadataValid++;
            } else {
                metadataInvalid++;
                lastMetadataError = "id=" + i + " " + validation.error();
            }
        }

        long uploadIntentItems = 0;
        long uploadIntentBytes = 0;
        for (ForgeSectionGeometryUploadIntent intent : this.uploadIntents.values()) {
            uploadIntentItems += intent.itemCount();
            uploadIntentBytes += intent.sizeBytes();
        }

        long removeIntentItems = 0;
        long removeIntentBytes = 0;
        for (ForgeSectionGeometryRemoveIntent intent : this.removeIntents.values()) {
            removeIntentItems += intent.freedItems();
            removeIntentBytes += intent.freedBytes();
        }

        ForgeSectionGeometryUploadIntent sampleUpload = sampleMetadata == null ? null : this.uploadIntents.get(sampleMetadata.geometryPtr());

        return new ForgeSectionGeometryStats(
                this.activeDimension == null ? "none" : this.activeDimension,
                this.sectionIds.getCount(),
                this.maxSections,
                this.usedGeometryItems,
                this.usedGeometryItems * GEOMETRY_ELEMENT_SIZE_BYTES,
                this.geometryArena.getSize(),
                this.geometryArena.getLimit(),
                Math.max(0L, this.geometryArena.getSize() - this.usedGeometryItems),
                this.geometryArena.numFreeBlocks(),
                this.largestFreeBlockItems(),
                this.uploadIntents.size(),
                uploadIntentItems,
                uploadIntentBytes,
                this.removeIntents.size(),
                removeIntentItems,
                removeIntentBytes,
                this.dirtyMetadataIds.size(),
                this.metadataById.size(),
                metadataValid,
                metadataInvalid,
                lastMetadataError,
                this.sectionIds.getMaxIndex(),
                this.totalUploads,
                this.totalRemoves,
                this.totalReplacements,
                this.clearCount,
                sampleSectionId,
                sampleMetadata == null ? "none" : safeMetadataWords(sampleMetadata),
                sampleMetadata == null ? "none" : safeMetadataDecode(sampleMetadata),
                sampleMetadata == null ? "none" : sampleMetadata.formatOffsets(),
                sampleMetadata == null ? "none" : sampleMetadata.formatDeltas(),
                sampleUpload == null ? 0 : sampleUpload.recordHash(),
                sampleUpload == null ? "none" : formatFirstRecords(sampleUpload.recordsCopy(), 3)
        );
    }

    public synchronized String createSampleDump() {
        int sampleSectionId = -1;
        ForgeSectionGeometryMetadata sampleMetadata = null;
        for (int i = 0; i < this.metadataById.size(); i++) {
            ForgeSectionGeometryMetadata metadata = this.metadataById.get(i);
            if (metadata != null) {
                sampleSectionId = i;
                sampleMetadata = metadata;
                break;
            }
        }

        if (sampleMetadata == null) {
            return "Voxy section geometry manager sample: empty activeSections=0";
        }

        ForgeSectionGeometryUploadIntent uploadIntent = this.uploadIntents.get(sampleMetadata.geometryPtr());
        ForgeSectionGeometryMetadata.ValidationResult validation = this.validateMetadata(sampleSectionId, sampleMetadata);
        long[] records = uploadIntent == null ? new long[0] : uploadIntent.recordsCopy();
        return String.format(
                "Voxy section geometry manager sample: sectionId=%d dimension=%s chunk=%d,%d position=%s aabb=%s geometryPtr=%d itemCount=%d allocatedItems=%d offsets=%s deltas=%s metadataWords=%s decoded=\"%s\" metadataValid=%s metadataError=%s uploadIntentPresent=%s uploadIntentHash=%d uploadIntentItems=%d uploadIntentBytes=%d firstRecords=%s cpuOnly=true gl=false",
                sampleSectionId,
                sampleMetadata.dimension(),
                sampleMetadata.chunkX(),
                sampleMetadata.chunkZ(),
                Long.toUnsignedString(sampleMetadata.position()),
                ForgeVoxyBuiltSectionBuilder.formatAabb(sampleMetadata.aabb()),
                Integer.toUnsignedLong(sampleMetadata.geometryPtr()),
                sampleMetadata.itemCount(),
                sampleMetadata.allocatedItems(),
                sampleMetadata.formatOffsets(),
                sampleMetadata.formatDeltas(),
                safeMetadataWords(sampleMetadata),
                safeMetadataDecode(sampleMetadata),
                validation.valid(),
                validation.error(),
                uploadIntent != null,
                uploadIntent == null ? 0 : uploadIntent.recordHash(),
                uploadIntent == null ? 0 : uploadIntent.itemCount(),
                uploadIntent == null ? 0 : uploadIntent.sizeBytes(),
                formatFirstRecords(records, 4)
        );
    }

    public synchronized void clear() {
        this.resetAllocators();
        this.metadataById.clear();
        this.positionToId.clear();
        this.uploadIntents.clear();
        this.removeIntents.clear();
        this.dirtyMetadataIds.clear();
        this.usedGeometryItems = 0;
        this.clearCount++;
    }

    private UploadResult uploadReplaceSection(ForgeVoxyBuiltSection section) {
        Key key = Key.from(section);
        Integer oldId = this.positionToId.get(key);
        boolean replaced = false;
        if (oldId != null) {
            replaced = this.removeSection(oldId);
            if (replaced) {
                this.totalReplacements++;
            }
        }

        int sectionId = this.sectionIds.allocateNext();
        if (sectionId == HierarchicalBitSet.SET_FULL) {
            throw new IllegalStateException("CPU section geometry manager section id space is full");
        }

        ForgeVoxyGeometryBuffer buffer = section.geometryBuffer();
        long[] records = buffer.packedQuads();
        if (records == null || records.length == 0) {
            this.sectionIds.free(sectionId);
            throw new IllegalArgumentException("Cannot upload empty BuiltSection geometry");
        }

        int itemCount = records.length;
        int allocatedItems = alignGeometryItems(itemCount);
        long allocation = this.geometryArena.alloc(allocatedItems);
        if (allocation == AllocationArena.SIZE_LIMIT) {
            this.sectionIds.free(sectionId);
            throw new IllegalStateException("CPU section geometry manager heap is full; requested items=" + allocatedItems);
        }
        int geometryPtr = (int) allocation;
        this.usedGeometryItems += allocatedItems;

        int[] offsets = section.offsets();
        ForgeSectionGeometryMetadata metadata = new ForgeSectionGeometryMetadata(
                section.dimension(),
                section.chunkX(),
                section.chunkZ(),
                section.position(),
                section.aabb(),
                geometryPtr,
                itemCount,
                allocatedItems,
                offsets,
                section.childExistence()
        );

        this.ensureMetadataCapacity(sectionId);
        this.metadataById.set(sectionId, metadata);
        this.positionToId.put(key, sectionId);
        this.uploadIntents.put(geometryPtr, new ForgeSectionGeometryUploadIntent(
                sectionId,
                section.dimension(),
                section.position(),
                geometryPtr,
                records
        ));
        this.removeIntents.remove(geometryPtr);
        this.dirtyMetadataIds.add(sectionId);
        this.totalUploads++;
        return new UploadResult(sectionId, itemCount, (long) itemCount * GEOMETRY_ELEMENT_SIZE_BYTES, replaced);
    }

    private boolean removeSection(int sectionId) {
        if (sectionId < 0 || sectionId >= this.metadataById.size()) {
            return false;
        }
        ForgeSectionGeometryMetadata metadata = this.metadataById.get(sectionId);
        if (metadata == null) {
            return false;
        }
        if (!this.sectionIds.free(sectionId)) {
            return false;
        }

        this.metadataById.set(sectionId, null);
        int geometryPtr = metadata.geometryPtr();
        int freedItems = this.geometryArena.free(Integer.toUnsignedLong(geometryPtr));
        this.usedGeometryItems -= freedItems;
        this.uploadIntents.remove(geometryPtr);
        this.removeIntents.put(geometryPtr, new ForgeSectionGeometryRemoveIntent(
                sectionId,
                metadata.dimension(),
                metadata.position(),
                geometryPtr,
                freedItems,
                (long) freedItems * GEOMETRY_ELEMENT_SIZE_BYTES
        ));
        this.dirtyMetadataIds.add(sectionId);
        this.positionToId.remove(new Key(metadata.dimension(), metadata.position()));
        this.totalRemoves++;
        return true;
    }

    private void resetAllocators() {
        this.sectionIds = new HierarchicalBitSet(this.maxSections);
        this.geometryArena = new AllocationArena();
        this.geometryArena.setLimit(this.geometryItemLimit);
    }

    private void ensureMetadataCapacity(int sectionId) {
        while (this.metadataById.size() <= sectionId) {
            this.metadataById.add(null);
        }
    }

    private static int alignGeometryItems(int itemCount) {
        return (itemCount + 127) & ~127;
    }

    private ForgeSectionGeometryMetadata.ValidationResult validateMetadata(int sectionId, ForgeSectionGeometryMetadata metadata) {
        return metadata.validate(sectionId, this.uploadIntents.get(metadata.geometryPtr()));
    }

    private int largestFreeBlockItems() {
        if (this.geometryArena.numFreeBlocks() == 0) {
            return 0;
        }
        try {
            return this.geometryArena.getLargestFreeBlockSize(0);
        } catch (RuntimeException e) {
            return -1;
        }
    }

    private static String safeMetadataWords(ForgeSectionGeometryMetadata metadata) {
        try {
            return metadata.formatMetadataWords();
        } catch (RuntimeException e) {
            return "invalid:" + e.getClass().getSimpleName() + ":" + e.getMessage();
        }
    }

    private static String safeMetadataDecode(ForgeSectionGeometryMetadata metadata) {
        try {
            return metadata.decodeMetadataWords();
        } catch (RuntimeException e) {
            return "invalid:" + e.getClass().getSimpleName() + ":" + e.getMessage();
        }
    }

    private static String formatFirstRecords(long[] records, int maxRecords) {
        if (records.length == 0) {
            return "[]";
        }
        StringBuilder builder = new StringBuilder("[");
        int count = Math.min(records.length, maxRecords);
        for (int i = 0; i < count; i++) {
            if (i != 0) {
                builder.append("; ");
            }
            long record = records[i];
            builder.append(ForgeVoxyQuadEncoder.formatRecordHex(record))
                    .append(' ')
                    .append(ForgeVoxyQuadEncoder.decodeRecord(record));
        }
        if (records.length > count) {
            builder.append("; ...+").append(records.length - count);
        }
        builder.append(']');
        return builder.toString();
    }

    private record Key(String dimension, long position) {
        private static Key from(ForgeVoxyBuiltSection section) {
            return new Key(section.dimension(), section.position());
        }
    }

    private record UploadResult(int sectionId, int itemCount, long geometryBytes, boolean replaced) {
    }

    public record SectionConsumeResult(
            boolean consumed,
            boolean replaced,
            int sectionId,
            int geometryItems,
            long geometryBytes,
            String reason
    ) {
    }

    public record ConsumeResult(
            String dimension,
            int chunkX,
            int chunkZ,
            int cacheSections,
            int consumedSections,
            int replacedSections,
            int skippedClosed,
            int skippedEmpty,
            int skippedWrongChunk,
            long geometryItems,
            long geometryBytes,
            List<Integer> allocatedIds,
            ForgeSectionGeometryStats status
    ) {
        public String formatAllocatedIds() {
            if (this.allocatedIds.isEmpty()) {
                return "[]";
            }
            if (this.allocatedIds.size() <= 16) {
                return this.allocatedIds.toString();
            }
            return this.allocatedIds.subList(0, 16) + "...+" + (this.allocatedIds.size() - 16);
        }
    }

    public record RealMetadataScan(
            String source,
            int candidateSectionCount,
            int acceptedSectionCount,
            int rejectedSectionCount,
            int recordsRejectedNoMetadata,
            int recordsRejectedNoGeometryPointer,
            int recordsRejectedNoBucketOffsets,
            int recordsRejectedNoVisibleBuckets,
            int recordsRejectedUnsafeState,
            int maxAcceptedSectionId,
            String lastRejectedReason,
            List<RealMetadataSnapshot> acceptedSections
    ) {
        public RealMetadataScan {
            source = source == null || source.isBlank() ? "none" : source;
            lastRejectedReason = lastRejectedReason == null || lastRejectedReason.isBlank() ? "none" : lastRejectedReason.replace(' ', '-');
            acceptedSections = List.copyOf(acceptedSections);
        }
    }

    public record RealMetadataSnapshot(
            int sectionId,
            String dimension,
            int chunkX,
            int chunkZ,
            long position,
            int aabb,
            int geometryPtr,
            int itemCount,
            int allocatedItems,
            int[] offsets,
            int[] deltas,
            int[] metadataWords,
            int uploadIntentHash
    ) {
        public RealMetadataSnapshot {
            offsets = Arrays.copyOf(offsets, offsets.length);
            deltas = Arrays.copyOf(deltas, deltas.length);
            metadataWords = Arrays.copyOf(metadataWords, metadataWords.length);
        }

        @Override
        public int[] offsets() {
            return Arrays.copyOf(this.offsets, this.offsets.length);
        }

        @Override
        public int[] deltas() {
            return Arrays.copyOf(this.deltas, this.deltas.length);
        }

        @Override
        public int[] metadataWords() {
            return Arrays.copyOf(this.metadataWords, this.metadataWords.length);
        }
    }
}
