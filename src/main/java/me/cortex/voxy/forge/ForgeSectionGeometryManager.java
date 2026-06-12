package me.cortex.voxy.forge;

import me.cortex.voxy.common.util.AllocationArena;
import me.cortex.voxy.common.util.HierarchicalBitSet;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

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

        return new ForgeSectionGeometryStats(
                this.activeDimension == null ? "none" : this.activeDimension,
                this.sectionIds.getCount(),
                this.maxSections,
                this.usedGeometryItems,
                this.usedGeometryItems * GEOMETRY_ELEMENT_SIZE_BYTES,
                this.geometryArena.getSize(),
                this.geometryArena.getLimit(),
                this.uploadIntents.size(),
                this.removeIntents.size(),
                this.dirtyMetadataIds.size(),
                this.metadataById.size(),
                this.totalUploads,
                this.totalRemoves,
                this.totalReplacements,
                this.clearCount,
                sampleSectionId,
                sampleMetadata == null ? "none" : sampleMetadata.formatMetadataWords(),
                sampleMetadata == null ? "none" : sampleMetadata.decodeMetadataWords()
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

    private record Key(String dimension, long position) {
        private static Key from(ForgeVoxyBuiltSection section) {
            return new Key(section.dimension(), section.position());
        }
    }

    private record UploadResult(int sectionId, int itemCount, long geometryBytes, boolean replaced) {
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
}
