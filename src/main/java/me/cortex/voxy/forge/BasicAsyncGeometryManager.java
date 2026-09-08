package me.cortex.voxy.forge;

import it.unimi.dsi.fastutil.HashCommon;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import me.cortex.voxy.common.util.AllocationArena;
import me.cortex.voxy.common.util.HierarchicalBitSet;
import me.cortex.voxy.common.util.MemoryBuffer;
import org.lwjgl.system.MemoryUtil;

final class BasicAsyncGeometryManager {
    static final int SECTION_METADATA_SIZE = 32;
    private static final long GEOMETRY_ELEMENT_SIZE = Long.BYTES;

    private final int maxSectionCount;
    private final long geometryCapacityBytes;
    private final HierarchicalBitSet allocationSet;
    private final AllocationArena allocationHeap = new AllocationArena();
    private final ObjectArrayList<SectionMeta> sectionMetadata = new ObjectArrayList<>(1 << 15);
    private final ReservedIdSet invalidatedIds = new ReservedIdSet(1024);
    private final ReservedUploadMap heapUploads = new ReservedUploadMap(1024);
    private final ReservedIdSet heapRemoveUploads = new ReservedIdSet(1024);
    private long usedCapacity;

    BasicAsyncGeometryManager(int maxSectionCount, long geometryCapacityBytes) {
        if (geometryCapacityBytes % GEOMETRY_ELEMENT_SIZE != 0) {
            throw new IllegalArgumentException("Original Voxy geometry capacity must be 8-byte aligned");
        }
        this.maxSectionCount = maxSectionCount;
        this.geometryCapacityBytes = geometryCapacityBytes;
        this.allocationSet = new HierarchicalBitSet(maxSectionCount);
        this.allocationHeap.setLimit(geometryCapacityBytes / GEOMETRY_ELEMENT_SIZE);
    }

    long geometryCapacityBytes() {
        return this.geometryCapacityBytes;
    }

    synchronized int uploadSection(BuiltSection section) {
        return this.uploadReplaceSection(-1, section);
    }

    // Consumes section on success AND failure. Only the vertex buffer crosses into the upload map;
    // BuiltSection.detachGeometryBuffer releases CPU-only occupancy. Expected validation/capacity
    // failures leave the previous geometry and every pending publication record unchanged.
    synchronized int uploadReplaceSection(int oldId, BuiltSection section) {
        if (section.isReleased()) {
            throw new IllegalStateException("Cannot upload an already-consumed BuiltSection");
        }
        int newId = -1;
        int addr = -1;
        SectionMeta oldMetadata = null;
        long previousAllocationSize = 0;
        boolean newHeapReservation = false;
        boolean committed = false;
        boolean handoffCompleted = false;
        try {
            int size = validateGeometry(section);
            int allocationSize = (size + 127) & ~127;
            if (oldId != -1) {
                oldMetadata = this.requireSection(oldId);
                // Every accepted reservation is exactly the original 128-element rounding. Read
                // it from immutable metadata instead of allocating an arena tree iterator per mesh.
                previousAllocationSize = (oldMetadata.itemCount + 127) & ~127;
                newId = oldId;
            } else {
                newId = this.allocationSet.allocateNext();
                if (newId == HierarchicalBitSet.SET_FULL) {
                    throw new IllegalStateException("Tried adding section when section count is already at capacity");
                }
                if (newId > this.sectionMetadata.size()
                        || (newId < this.sectionMetadata.size() && this.sectionMetadata.get(newId) != null)) {
                    throw new IllegalStateException("Section id metadata slot was unexpectedly occupied: " + newId);
                }
            }

            if (oldMetadata != null && allocationSize <= previousAllocationSize) {
                addr = oldMetadata.geometryPtr;
                if (allocationSize < previousAllocationSize) {
                    this.usedCapacity -= this.allocationHeap.shrink(Integer.toUnsignedLong(addr), allocationSize);
                }
            } else if (oldMetadata != null && this.allocationHeap.expand(
                    Integer.toUnsignedLong(oldMetadata.geometryPtr), (int) (allocationSize - previousAllocationSize))) {
                addr = oldMetadata.geometryPtr;
                this.usedCapacity += allocationSize - previousAllocationSize;
            } else {
                addr = (int) this.allocationHeap.alloc(allocationSize);
                if (addr == AllocationArena.SIZE_LIMIT) {
                    throw new IllegalStateException("Geometry OOM. requested allocation size (in elements): " + size
                            + ", Heap size at top remaining: " + (this.allocationHeap.getLimit() - this.allocationHeap.getSize())
                            + ", used elements: " + this.usedCapacity);
                }
                newHeapReservation = true;
                this.usedCapacity += allocationSize;
            }
            if ((oldMetadata == null || oldMetadata.geometryPtr != addr) && this.heapUploads.containsKey(addr)) {
                throw new IllegalStateException("Duplicate geometry heap upload address: " + addr);
            }
            SectionMeta newMetadata = new SectionMeta(section.position, section.aabb, addr, size,
                    section.offsets, section.childExistence);
            // Complete allocations/validation before transferring ownership or removing old coverage.
            if (newId == this.sectionMetadata.size()) {
                this.sectionMetadata.ensureCapacity(newId + 1);
            }
            this.heapUploads.reserve(this.heapUploads.size() + 1);
            this.heapRemoveUploads.reserve(this.heapRemoveUploads.size() + 1);
            this.invalidatedIds.reserve(this.invalidatedIds.size() + 1);
            MemoryBuffer buffer = section.detachGeometryBuffer();
            handoffCompleted = true;
            if (oldMetadata != null && oldMetadata.geometryPtr != addr) {
                this.releaseHeap(oldMetadata);
            }
            MemoryBuffer displacedUpload = this.heapUploads.put(addr, buffer);
            if (displacedUpload != null) {
                displacedUpload.free();
            }
            this.heapRemoveUploads.remove(addr);
            if (newId == this.sectionMetadata.size()) {
                this.sectionMetadata.add(newMetadata);
            } else {
                this.sectionMetadata.set(newId, newMetadata);
            }
            this.invalidatedIds.add(newId);
            committed = true;
            return newId;
        } finally {
            if (!committed && !handoffCompleted) {
                // Roll back reservations while the old metadata/pending buffer still own coverage.
                try {
                    if (newHeapReservation) {
                        this.usedCapacity -= this.allocationHeap.free(Integer.toUnsignedLong(addr));
                    } else if (oldMetadata != null && addr != -1) {
                        long currentSize = this.allocationHeap.getSize(Integer.toUnsignedLong(addr));
                        if (currentSize > previousAllocationSize) {
                            this.usedCapacity -= this.allocationHeap.shrink(Integer.toUnsignedLong(addr), (int) previousAllocationSize);
                        } else if (currentSize < previousAllocationSize) {
                            if (!this.allocationHeap.expand(Integer.toUnsignedLong(addr), (int) (previousAllocationSize - currentSize))) {
                                throw new IllegalStateException("Geometry reservation rollback could not restore its own released tail");
                            }
                            this.usedCapacity += previousAllocationSize - currentSize;
                        }
                    }
                } finally {
                    if (oldId == -1 && newId != -1) {
                        this.allocationSet.free(newId);
                    }
                    if (!section.isReleased()) section.free();
                }
            }
        }
    }

    synchronized void removeSection(int id) {
        SectionMeta oldMetadata = this.requireSection(id);
        if (!this.allocationSet.free(id)) {
            throw new IllegalStateException("Id was not already allocated. id: " + id);
        }
        this.sectionMetadata.set(id, null);
        this.releaseHeap(oldMetadata);
        this.invalidatedIds.add(id);
    }

    // Pending CPU uploads are freed here. Already published/GPU-only data has no CPU clone to cache;
    // removing it releases the arena range, and a later request uses normal mesh regeneration.
    private void releaseHeap(SectionMeta oldMetadata) {
        int ptr = oldMetadata.geometryPtr;
        this.usedCapacity -= this.allocationHeap.free(Integer.toUnsignedLong(ptr));
        MemoryBuffer upload = this.heapUploads.remove(ptr);
        if (upload != null) {
            upload.free();
        }
        this.heapRemoveUploads.add(ptr);
    }

    private SectionMeta requireSection(int id) {
        if (id < 0 || id >= this.sectionMetadata.size() || id >= this.maxSectionCount
                || !this.allocationSet.isSet(id) || this.sectionMetadata.get(id) == null) {
            throw new IllegalStateException("Id was not already allocated. id: " + id);
        }
        return this.sectionMetadata.get(id);
    }

    synchronized void discardEmptySection(BuiltSection section) {
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
                buffer.free();
            }
            this.heapUploads.clear();
        }
        this.heapRemoveUploads.clear();
        this.invalidatedIds.clear();
    }

    synchronized void clear() {
        for (MemoryBuffer upload : this.heapUploads.values()) {
            upload.free();
        }
        for (int id = 0; id < this.sectionMetadata.size(); id++) {
            if (this.sectionMetadata.get(id) != null && this.allocationSet.isSet(id)) {
                this.allocationSet.free(id);
            }
        }
        this.heapUploads.clear();
        this.heapRemoveUploads.clear();
        this.invalidatedIds.clear();
        this.sectionMetadata.clear();
        this.allocationHeap.reset();
        this.allocationHeap.setLimit(this.geometryCapacityBytes / GEOMETRY_ELEMENT_SIZE);
        this.usedCapacity = 0L;
    }

    private static int validateGeometry(BuiltSection section) {
        if (section.isEmpty()) {
            throw new IllegalArgumentException("sectionData is empty, cannot upload nothing");
        }
        section.geometryBuffer.assertNotFreed();
        if (section.geometryBuffer.size % GEOMETRY_ELEMENT_SIZE != 0) {
            throw new IllegalStateException("Original Voxy geometry buffer is not 8-byte aligned");
        }
        long size = section.geometryBuffer.size / GEOMETRY_ELEMENT_SIZE;
        if (size <= 0 || ((size + 127) & ~127L) > AllocationArena.MAX_ALLOCATION_SIZE) {
            throw new IllegalStateException("Original Voxy geometry exceeds the packed arena allocation range: " + size);
        }
        if (section.offsets == null || section.offsets.length != 8) {
            throw new IllegalStateException("Original Voxy geometry requires eight render-category offsets");
        }
        if (section.occupancy != null) {
            section.occupancy.assertNotFreed();
        }
        return (int) size;
    }

    synchronized IntOpenHashSet getAllocatedSectionIdsSnapshot() {
        IntOpenHashSet ids = new IntOpenHashSet();
        for (int id = 0; id < this.sectionMetadata.size(); id++) {
            if (this.sectionMetadata.get(id) != null) ids.add(id);
        }
        return ids;
    }

    synchronized long getSectionPosition(int id) {
        return this.requireSection(id).position;
    }

    synchronized void verifyIntegrity() {
        IntOpenHashSet ids = this.getAllocatedSectionIdsSnapshot();
        IntOpenHashSet pointers = new IntOpenHashSet();
        long reserved = 0;
        for (int id = 0; id < this.sectionMetadata.size(); id++) {
            if (this.allocationSet.isSet(id) != ids.contains(id)) {
                throw new IllegalStateException("Geometry metadata/allocation mismatch: " + id);
            }
            SectionMeta metadata = this.sectionMetadata.get(id);
            if (metadata != null) {
                if (!pointers.add(metadata.geometryPtr)) {
                    throw new IllegalStateException("Duplicate geometry heap ownership: " + metadata.geometryPtr);
                }
                long allocationSize = this.allocationHeap.getSize(Integer.toUnsignedLong(metadata.geometryPtr));
                if (allocationSize != ((metadata.itemCount + 127) & ~127)) {
                    throw new IllegalStateException("Geometry metadata/reservation rounding mismatch: " + id);
                }
                reserved += allocationSize;
            }
        }
        if (ids.size() != this.allocationSet.getCount() || reserved != this.usedCapacity) {
            throw new IllegalStateException("Geometry allocation accounting mismatch");
        }
        for (var entry : this.heapUploads.int2ObjectEntrySet()) {
            if (!pointers.contains(entry.getIntKey()) || this.heapRemoveUploads.contains(entry.getIntKey())) {
                throw new IllegalStateException("Pending geometry upload has no unique live reservation");
            }
            entry.getValue().assertNotFreed();
        }
    }

    // Minecraft 1.20.1 supplies fastutil 8.5.9: map/set ensureCapacity is PRIVATE there,
    // although it is public in compile-time 8.5.12. The same original sizing mechanism is
    // available through the stable protected subclass contract in both versions. Keep all
    // reservations before BuiltSection handoff; do not replace them with post-handoff growth.
    private static final class ReservedUploadMap extends Int2ObjectOpenHashMap<MemoryBuffer> {
        private ReservedUploadMap(int expected) { super(expected); }
        private void reserve(int expected) {
            int needed = HashCommon.arraySize(expected, this.f);
            if (needed > this.n) this.rehash(needed);
        }
    }

    private static final class ReservedIdSet extends IntOpenHashSet {
        private ReservedIdSet(int expected) { super(expected); }
        private void reserve(int expected) {
            int needed = HashCommon.arraySize(expected, this.f);
            if (needed > this.n) this.rehash(needed);
        }
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
