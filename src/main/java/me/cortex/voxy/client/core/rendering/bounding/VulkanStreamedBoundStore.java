package me.cortex.voxy.client.core.rendering.bounding;

import me.cortex.voxy.client.core.rendering.VulkanViewport;
import me.cortex.voxy.client.core.vulkan.VoxyVulkanBuffer;
import me.cortex.voxy.client.core.vulkan.VoxyVulkanBufferUsage;
import me.cortex.voxy.client.core.vulkan.VulkanUploadStream;
import me.cortex.voxy.common.util.UnsafeUtil;

import java.util.Arrays;

/** Vulkan owner preserving StreamedBoundStore's exact CPU list and upload contract. */
public final class VulkanStreamedBoundStore implements VulkanBoundStore {
    private static final int INITIAL_INT_CAPACITY = (1 << 12) * 2;

    private final VulkanUploadStream uploads = new VulkanUploadStream();
    private VoxyVulkanBuffer chunkPosBuffer = createBuffer(INITIAL_INT_CAPACITY * (long) Integer.BYTES);
    private int count;
    private boolean didChange;
    private int[] visibleSections = new int[INITIAL_INT_CAPACITY];
    private boolean closed;

    @Override
    public void preRender(VulkanViewport<?> viewport) {
        this.ensureOpen();
        if (this.count == 0 || !this.didChange) return;
        long requiredBytes = Math.multiplyExact(this.count, (long) Integer.BYTES);
        if (requiredBytes > this.chunkPosBuffer.size()) {
            VoxyVulkanBuffer replacement = createBuffer((long) Math.ceil(this.count * 1.25) * Integer.BYTES);
            VoxyVulkanBuffer old = this.chunkPosBuffer;
            this.chunkPosBuffer = replacement;
            old.close();
        }
        long address = this.uploads.upload(this.chunkPosBuffer, 0L, Math.toIntExact(requiredBytes)).address();
        UnsafeUtil.memcpy(this.visibleSections, this.count, address);
        this.uploads.commit();
        this.didChange = false;
    }

    public void reset() {
        this.ensureOpen();
        this.count = 0;
        this.didChange = true;
    }

    public void put(long pos) {
        this.ensureOpen();
        pos = BoundStorePosition.transform(pos);
        this.visibleSections[this.count++] = (int) (pos & 0xFFFFFFFFL);
        this.visibleSections[this.count++] = (int) ((pos >>> 32) & 0xFFFFFFFFL);
        if (this.count >= this.visibleSections.length - 2) {
            this.visibleSections = Arrays.copyOf(
                    this.visibleSections,
                    (int) (this.visibleSections.length * 1.25)
            );
        }
    }

    @Override
    public VoxyVulkanBuffer getBuffer() {
        this.ensureOpen();
        return this.chunkPosBuffer;
    }

    @Override
    public int getCount() {
        this.ensureOpen();
        return this.count >> 1;
    }

    private static VoxyVulkanBuffer createBuffer(long bytes) {
        return VoxyVulkanBuffer.create(
                bytes,
                VoxyVulkanBufferUsage.of(
                        VoxyVulkanBufferUsage.STORAGE_GRAPHICS
                                | VoxyVulkanBufferUsage.TRANSFER_DESTINATION
                ),
                "Voxy streamed chunk bounds"
        );
    }

    private void ensureOpen() {
        if (this.closed) throw new IllegalStateException("Vulkan streamed bound store is closed");
    }

    @Override
    public void close() {
        if (this.closed) return;
        this.closed = true;
        this.uploads.commit();
        this.chunkPosBuffer.close();
    }
}
