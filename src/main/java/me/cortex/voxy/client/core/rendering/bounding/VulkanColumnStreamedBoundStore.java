package me.cortex.voxy.client.core.rendering.bounding;

import me.cortex.voxy.client.core.rendering.VulkanViewport;
import me.cortex.voxy.client.core.vulkan.VoxyVulkanBuffer;
import me.cortex.voxy.client.core.vulkan.VoxyVulkanBufferUsage;
import me.cortex.voxy.client.core.vulkan.VulkanUploadStream;
import net.caffeinemc.mods.sodium.client.render.chunk.map.ChunkTrackerHolder;
import net.minecraft.client.Minecraft;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ChunkPos;
import org.lwjgl.system.MemoryUtil;

/** Vulkan owner preserving ColumnStreamedBoundStore's exact ready-column emission. */
public final class VulkanColumnStreamedBoundStore implements VulkanBoundStore {
    private static final int INITIAL_MAX_CHUNK_COUNT = 1 << 12;

    private final VulkanUploadStream uploads = new VulkanUploadStream();
    private VoxyVulkanBuffer chunkPosBuffer = createBuffer(INITIAL_MAX_CHUNK_COUNT);
    private int count;
    private boolean closed;

    @Override
    public void preRender(VulkanViewport<?> viewport) {
        this.ensureOpen();
        float renderDistance = Minecraft.getInstance().options.getEffectiveRenderDistance() * 16.0f;
        int capacity = Math.toIntExact(this.chunkPosBuffer.size() / Long.BYTES);
        long address = this.uploads.upload(
                this.chunkPosBuffer,
                0L,
                Math.toIntExact(this.chunkPosBuffer.size())
        ).address();
        this.count = findEmitBoundingChunks(viewport, renderDistance, capacity, address);
        this.uploads.commit();
        if (this.count < 0) {
            int requiredCount = -this.count;
            VoxyVulkanBuffer replacement = createBuffer(requiredCount);
            long replacementAddress = this.uploads.upload(
                    replacement,
                    0L,
                    Math.multiplyExact(requiredCount, Long.BYTES)
            ).address();
            int emitted = findEmitBoundingChunks(viewport, renderDistance, requiredCount, replacementAddress);
            if (emitted < 0) {
                this.uploads.commit();
                replacement.close();
                throw new IllegalStateException(
                        "Vulkan column bound store did not fit its exact requested capacity: " + emitted
                );
            }
            this.uploads.commit();
            VoxyVulkanBuffer old = this.chunkPosBuffer;
            this.chunkPosBuffer = replacement;
            old.close();
            this.count = emitted;
        }
    }

    private static int findEmitBoundingChunks(
            VulkanViewport<?> viewport,
            float searchDistance,
            int capacity,
            long writePointer
    ) {
        int blockY = (int) Math.floor(viewport.cameraY);
        float fractionalY = (float) (viewport.cameraY - (int) viewport.cameraY);

        int minChunkY = blockY >> 4;
        int maxChunkY = blockY >> 4;
        while (testYPos(blockY, fractionalY, minChunkY, searchDistance)) minChunkY--;
        minChunkY++;
        while (testYPos(blockY, fractionalY, maxChunkY, searchDistance)) maxChunkY++;
        maxChunkY--;

        int count = 0;
        var tracker = ChunkTrackerHolder.get(Minecraft.getInstance().level);
        if (tracker != null) {
            var iterator = tracker.getReadyChunks().longIterator();
            while (iterator.hasNext()) {
                long column = iterator.nextLong();
                for (int chunkY = minChunkY + 1; chunkY < maxChunkY; chunkY++) {
                    if (count++ < capacity) {
                        putPos(
                                writePointer,
                                SectionPos.asLong(ChunkPos.getX(column), chunkY, ChunkPos.getZ(column))
                        );
                        writePointer += Long.BYTES;
                    }
                }
            }
        }
        return count > capacity ? -count : count;
    }

    private static void putPos(long pointer, long pos) {
        pos = BoundStorePosition.transform(pos);
        MemoryUtil.memPutInt(pointer, (int) (pos & 0xFFFFFFFFL));
        MemoryUtil.memPutInt(pointer + Integer.BYTES, (int) ((pos >>> 32) & 0xFFFFFFFFL));
    }

    private static boolean testYPos(int blockY, float fractionalY, int chunkY, float distance) {
        int relativeY = chunkY * 16 - blockY;
        float deltaY = (float) nearestToZero(relativeY - 1, relativeY + 17) - fractionalY;
        return Math.abs(deltaY) < distance;
    }

    private static int nearestToZero(int min, int max) {
        int clamped = 0;
        if (min > 0) clamped = min;
        if (max < 0) clamped = max;
        return clamped;
    }

    private static VoxyVulkanBuffer createBuffer(int count) {
        return VoxyVulkanBuffer.create(
                Math.multiplyExact(count, (long) Long.BYTES),
                VoxyVulkanBufferUsage.of(
                        VoxyVulkanBufferUsage.STORAGE_GRAPHICS
                                | VoxyVulkanBufferUsage.TRANSFER_DESTINATION
                ),
                "Voxy column chunk bounds"
        );
    }

    @Override
    public VoxyVulkanBuffer getBuffer() {
        this.ensureOpen();
        return this.chunkPosBuffer;
    }

    @Override
    public int getCount() {
        this.ensureOpen();
        return this.count;
    }

    private void ensureOpen() {
        if (this.closed) throw new IllegalStateException("Vulkan column bound store is closed");
    }

    @Override
    public void close() {
        if (this.closed) return;
        this.closed = true;
        this.uploads.commit();
        this.chunkPosBuffer.close();
    }
}
