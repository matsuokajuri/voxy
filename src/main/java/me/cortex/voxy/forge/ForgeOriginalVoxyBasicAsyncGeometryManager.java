package me.cortex.voxy.forge;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import me.cortex.voxy.common.util.AllocationArena;
import me.cortex.voxy.common.util.HierarchicalBitSet;
import me.cortex.voxy.common.util.MemoryBuffer;
import org.lwjgl.system.MemoryUtil;

final class ForgeOriginalVoxyBasicAsyncGeometryManager {
    static final int SECTION_METADATA_SIZE = 32;
    private static final long GEOMETRY_ELEMENT_SIZE = Long.BYTES;

    private final int maxSectionCount;
    private final long geometryCapacityBytes;
    private final HierarchicalBitSet allocationSet;
    private final AllocationArena allocationHeap = new AllocationArena();
    private final ObjectArrayList<SectionMeta> sectionMetadata = new ObjectArrayList<>(1 << 15);
    private final IntOpenHashSet invalidatedIds = new IntOpenHashSet(1024);
    private final Int2ObjectOpenHashMap<MemoryBuffer> heapUploads = new Int2ObjectOpenHashMap<>(1024);
    private final IntOpenHashSet heapRemoveUploads = new IntOpenHashSet(1024);
    private long usedCapacity;
    private long acceptedBuiltSectionCount;
    private long replacedBuiltSectionCount;
    private long removedBuiltSectionCount;
    private long emptyBuiltSectionCount;
    private long drainedUploadCount;
    private long drainedUploadBytes;
    private long drainedRemoveCount;
    private long drainedMetadataUpdateCount;
    private int lastSectionId = -1;
    private long lastSectionPosition;
    private String lastLifecycleEvent = "created";
    private String lastFailureReason = "none";

    ForgeOriginalVoxyBasicAsyncGeometryManager(int maxSectionCount, long geometryCapacityBytes) {
        if (geometryCapacityBytes % GEOMETRY_ELEMENT_SIZE != 0) {
            throw new IllegalArgumentException("Original Voxy geometry capacity must be 8-byte aligned");
        }
        this.maxSectionCount = maxSectionCount;
        this.geometryCapacityBytes = geometryCapacityBytes;
        this.allocationSet = new HierarchicalBitSet(maxSectionCount);
        this.allocationHeap.setLimit(geometryCapacityBytes / GEOMETRY_ELEMENT_SIZE);
    }

    synchronized int uploadSection(ForgeOriginalVoxyBuiltSection section) {
        return this.uploadReplaceSection(-1, section);
    }

    synchronized int uploadReplaceSection(int oldId, ForgeOriginalVoxyBuiltSection section) {
        if (section.isEmpty()) {
            this.emptyBuiltSectionCount++;
            section.free();
            throw new IllegalArgumentException("sectionData is empty, cannot upload nothing");
        }
        if (oldId != -1) {
            this.removeSection(oldId);
            this.replacedBuiltSectionCount++;
        }

        int newId = this.allocationSet.allocateNext();
        if (newId == HierarchicalBitSet.SET_FULL) {
            section.free();
            throw new IllegalStateException("Tried adding section when section count is already at capacity");
        }
        if (newId > this.sectionMetadata.size()) {
            section.free();
            throw new IllegalStateException("Size exceeds limits: " + newId + ", " + this.sectionMetadata.size() + ", " + this.allocationSet.getCount());
        }
        if (newId < this.sectionMetadata.size() && this.sectionMetadata.get(newId) != null) {
            section.free();
            throw new IllegalStateException("Section id was allocated over existing metadata: " + newId);
        }

        SectionMeta newMeta = this.createMeta(section);
        if (newId == this.sectionMetadata.size()) {
            this.sectionMetadata.add(newMeta);
        } else if (this.sectionMetadata.set(newId, newMeta) != null) {
            section.free();
            throw new IllegalStateException("Section id metadata slot was unexpectedly occupied: " + newId);
        }

        this.invalidatedIds.add(newId);
        this.acceptedBuiltSectionCount++;
        this.lastSectionId = newId;
        this.lastSectionPosition = section.position;
        this.lastLifecycleEvent = oldId == -1 ? "upload-section" : "upload-replace-section";
        this.lastFailureReason = "none";
        return newId;
    }

    synchronized void removeSection(int id) {
        if (!this.allocationSet.free(id)) {
            throw new IllegalStateException("Id was not already allocated. id: " + id);
        }
        SectionMeta oldMetadata = this.sectionMetadata.set(id, null);
        int ptr = oldMetadata.geometryPtr;
        this.usedCapacity -= this.allocationHeap.free(Integer.toUnsignedLong(ptr));
        MemoryBuffer upload = this.heapUploads.remove(ptr);
        if (upload != null) {
            upload.free();
        }
        this.heapRemoveUploads.add(ptr);
        this.invalidatedIds.add(id);
        this.removedBuiltSectionCount++;
        this.lastSectionId = id;
        this.lastSectionPosition = oldMetadata.position;
        this.lastLifecycleEvent = "remove-section";
        this.lastFailureReason = "none";
    }

    synchronized void discardEmptySection(ForgeOriginalVoxyBuiltSection section) {
        this.emptyBuiltSectionCount++;
        this.lastSectionPosition = section.position;
        this.lastLifecycleEvent = "discard-empty-section";
        section.free();
    }

    synchronized void writeMetadata(int sectionId, long ptr) {
        SectionMeta section = this.sectionMetadata.get(sectionId);
        if (section == null) {
            MemoryUtil.memSet(ptr, 0, SECTION_METADATA_SIZE);
        } else {
            section.writeMetadata(ptr);
        }
    }

    synchronized void writeMetadataSplit(int sectionId, long ptrA, long ptrB) {
        SectionMeta section = this.sectionMetadata.get(sectionId);
        if (section == null) {
            MemoryUtil.memSet(ptrA, 0, 16);
            MemoryUtil.memSet(ptrB, 0, 16);
        } else {
            section.writeMetadataSplitParts(ptrA, ptrB);
        }
    }

    synchronized ForgeOriginalVoxyBasicAsyncGeometryStats createStatusSnapshot(boolean resultConsumerAttached) {
        long pendingUploadBytes = 0L;
        for (MemoryBuffer upload : this.heapUploads.values()) {
            pendingUploadBytes += upload.size;
        }
        return new ForgeOriginalVoxyBasicAsyncGeometryStats(
                true,
                false,
                false,
                resultConsumerAttached,
                this.allocationSet.getCount(),
                this.maxSectionCount,
                this.usedCapacity * GEOMETRY_ELEMENT_SIZE,
                this.geometryCapacityBytes,
                this.allocationHeap.getSize(),
                this.allocationHeap.getLimit(),
                this.allocationHeap.numFreeBlocks(),
                this.heapUploads.size(),
                pendingUploadBytes,
                this.heapRemoveUploads.size(),
                this.invalidatedIds.size(),
                this.acceptedBuiltSectionCount,
                this.replacedBuiltSectionCount,
                this.removedBuiltSectionCount,
                this.emptyBuiltSectionCount,
                this.drainedUploadCount,
                this.drainedUploadBytes,
                this.drainedRemoveCount,
                this.drainedMetadataUpdateCount,
                this.lastSectionId,
                this.lastSectionPosition,
                this.lastLifecycleEvent,
                this.lastFailureReason
        );
    }

    synchronized Int2ObjectOpenHashMap<MemoryBuffer> getUploads() {
        return this.heapUploads;
    }

    synchronized IntOpenHashSet getHeapRemovals() {
        return this.heapRemoveUploads;
    }

    synchronized IntOpenHashSet getUpdateIds() {
        return this.invalidatedIds;
    }

    synchronized int getSectionCount() {
        return this.allocationSet.getCount();
    }

    synchronized long getGeometryUsedBytes() {
        return this.usedCapacity * GEOMETRY_ELEMENT_SIZE;
    }

    synchronized void drainPendingSyncEventsForCurrentParityOwner() {
        if (!this.heapUploads.isEmpty()) {
            var iter = this.heapUploads.int2ObjectEntrySet().fastIterator();
            while (iter.hasNext()) {
                var entry = iter.next();
                MemoryBuffer buffer = entry.getValue();
                this.drainedUploadCount++;
                this.drainedUploadBytes += buffer.size;
                buffer.free();
            }
            this.heapUploads.clear();
        }
        this.drainedRemoveCount += this.heapRemoveUploads.size();
        this.heapRemoveUploads.clear();
        this.drainedMetadataUpdateCount += this.invalidatedIds.size();
        this.invalidatedIds.clear();
        this.lastLifecycleEvent = "drain-pending-sync-events";
    }

    synchronized void clear() {
        for (MemoryBuffer upload : this.heapUploads.values()) {
            upload.free();
        }
        this.heapUploads.clear();
        this.heapRemoveUploads.clear();
        this.invalidatedIds.clear();
        this.sectionMetadata.clear();
        this.allocationHeap.reset();
        this.allocationHeap.setLimit(this.geometryCapacityBytes / GEOMETRY_ELEMENT_SIZE);
        this.usedCapacity = 0L;
        this.lastSectionId = -1;
        this.lastSectionPosition = 0L;
        this.lastLifecycleEvent = "clear";
        this.lastFailureReason = "none";
    }

    private SectionMeta createMeta(ForgeOriginalVoxyBuiltSection section) {
        if (section.geometryBuffer.size % GEOMETRY_ELEMENT_SIZE != 0) {
            section.free();
            throw new IllegalStateException("Original Voxy geometry buffer is not 8-byte aligned");
        }
        int size = (int) (section.geometryBuffer.size / GEOMETRY_ELEMENT_SIZE);
        int upsized = (size + 127) & ~127;
        int addr = (int) this.allocationHeap.alloc(upsized);
        if (addr == AllocationArena.SIZE_LIMIT) {
            section.free();
            this.lastFailureReason = "geometry-oom-requested-elements-" + size;
            throw new IllegalStateException("Geometry OOM. requested allocation size (in elements): " + size
                    + ", Heap size at top remaining: " + (this.allocationHeap.getLimit() - this.allocationHeap.getSize())
                    + ", used elements: " + this.usedCapacity);
        }
        this.usedCapacity += upsized;
        if (this.heapUploads.put(addr, section.geometryBuffer) != null) {
            section.free();
            throw new IllegalStateException("Duplicate geometry heap upload address: " + addr);
        }
        this.heapRemoveUploads.remove(addr);
        return new SectionMeta(section.position, section.aabb, addr, size, section.offsets, section.childExistence);
    }

    private record SectionMeta(long position, int aabb, int geometryPtr, int itemCount, int[] offsets, byte childExistence) {
        private void writeMetadata(long ptr) {
            this.writeMetadataSplitParts(ptr, ptr + 16);
        }

        private void writeMetadataSplitParts(long ptrA, long ptrB) {
            MemoryUtil.memPutInt(ptrA, (int) (this.position >> 32)); ptrA += 4;
            MemoryUtil.memPutInt(ptrA, (int) this.position); ptrA += 4;
            MemoryUtil.memPutInt(ptrA, this.aabb); ptrA += 4;
            MemoryUtil.memPutInt(ptrA, this.geometryPtr + this.offsets[0]); ptrA += 4;

            MemoryUtil.memPutInt(ptrB, (this.offsets[1] - this.offsets[0]) | ((this.offsets[2] - this.offsets[1]) << 16)); ptrB += 4;
            MemoryUtil.memPutInt(ptrB, (this.offsets[3] - this.offsets[2]) | ((this.offsets[4] - this.offsets[3]) << 16)); ptrB += 4;
            MemoryUtil.memPutInt(ptrB, (this.offsets[5] - this.offsets[4]) | ((this.offsets[6] - this.offsets[5]) << 16)); ptrB += 4;
            MemoryUtil.memPutInt(ptrB, (this.offsets[7] - this.offsets[6]) | ((this.itemCount - this.offsets[7]) << 16));
        }
    }
}
