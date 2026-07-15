package me.cortex.voxy.client.core.vulkan;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.buffers.GpuFence;
import com.mojang.blaze3d.vulkan.VulkanGpuBuffer;
import me.cortex.voxy.common.util.AllocationArena;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.VK12;
import org.lwjgl.vulkan.VkBufferCopy;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Vulkan equivalent of the original asynchronous DownloadStream.
 * GPU copies target one persistently mapped host-visible ring owned by this stream. Each allocation remains
 * reserved until the graphics-queue fence for its frame completes, and the callback pointer remains valid only
 * for the duration of that callback.
 */
public final class VulkanDownloadStream implements AutoCloseable {
    public static final long DEFAULT_CAPACITY = 1L << 25;
    private static final long FORCE_WAIT_TIMEOUT_NS = 5_000_000_000L;

    @FunctionalInterface
    public interface DownloadResultConsumer {
        void consume(long pointer, long size);
    }

    private final long capacity;
    private final AllocationArena allocationArena = new AllocationArena();
    private final GpuBuffer downloadBuffer;
    private final VulkanGpuBuffer vkDownloadBuffer;
    private final GpuBufferSlice.MappedView mappedView;
    private final ByteBuffer mappedData;
    private final long mappedBaseAddress;
    private final ArrayList<PendingDownload> currentDownloads = new ArrayList<>();
    private final Deque<DownloadFrame> frames = new ArrayDeque<>();
    private long currentBytes;
    private long inFlightBytes;
    private boolean closed;

    public VulkanDownloadStream() {
        this(DEFAULT_CAPACITY);
    }

    public VulkanDownloadStream(long capacity) {
        if (capacity <= 0L) {
            throw new IllegalArgumentException("Download capacity must be greater than zero");
        }
        if (capacity > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Download capacity cannot exceed Java's mapped-buffer limit");
        }

        Resources resources = createResources(capacity);
        this.capacity = capacity;
        this.downloadBuffer = resources.buffer();
        this.vkDownloadBuffer = resources.vkBuffer();
        this.mappedView = resources.mappedView();
        this.mappedData = resources.mappedData();
        this.mappedBaseAddress = MemoryUtil.memAddress(this.mappedData);
        this.allocationArena.setLimit(capacity);
    }

    private static Resources createResources(long capacity) {
        // Locked Minecraft 26.2 VulkanGpuBuffer.Direct requires HOST_VISIBLE | HOST_COHERENT for every
        // MAP_READ/MAP_WRITE allocation and additionally prefers HOST_CACHED for MAP_READ. Fence completion plus
        // TRANSFER_WRITE -> HOST_READ is therefore sufficient; a VMA invalidate is neither needed nor exposed.
        GpuBuffer buffer = VoxyVulkanContext.get().vulkanDevice().createBuffer(
                () -> "Voxy persistent download stream",
                GpuBuffer.USAGE_MAP_READ | GpuBuffer.USAGE_HINT_CLIENT_STORAGE | GpuBuffer.USAGE_COPY_DST,
                capacity
        );
        if (!(buffer instanceof VulkanGpuBuffer vkBuffer)) {
            buffer.close();
            throw new IllegalStateException("Minecraft Vulkan device returned a non-Vulkan download buffer");
        }

        GpuBufferSlice.MappedView mapped = null;
        try {
            mapped = buffer.map(true, false);
            ByteBuffer data = mapped.data().duplicate().order(ByteOrder.nativeOrder());
            data.clear();
            return new Resources(buffer, vkBuffer, mapped, data);
        } catch (RuntimeException | Error failure) {
            if (mapped != null) {
                try {
                    mapped.close();
                } catch (Throwable closeFailure) {
                    failure.addSuppressed(closeFailure);
                }
            }
            try {
                buffer.close();
            } catch (Throwable closeFailure) {
                failure.addSuppressed(closeFailure);
            }
            throw failure;
        }
    }

    public void download(VoxyVulkanBuffer source, DownloadResultConsumer resultConsumer) {
        this.download(source, 0L, source.size(), resultConsumer);
    }

    public void download(VoxyVulkanBuffer source, long sourceOffset, long size, DownloadResultConsumer resultConsumer) {
        this.ensureOpen();
        if (source.isClosed()) {
            throw new IllegalStateException("Cannot download from a closed Vulkan buffer");
        }
        if ((source.usage() & GpuBuffer.USAGE_COPY_SRC) == 0) {
            throw new IllegalArgumentException("Download source must have USAGE_COPY_SRC");
        }
        VulkanRanges.check(sourceOffset, size, source.size(), "Download source");
        if (size == 0L) throw new IllegalArgumentException("Download size must be greater than zero");
        if (size > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("A single download cannot exceed 2 GiB");
        }
        if ((sourceOffset & 3L) != 0L || (size & 3L) != 0L) {
            throw new IllegalArgumentException("Vulkan buffer downloads require 4-byte aligned offsets and sizes");
        }
        if (resultConsumer == null) {
            throw new NullPointerException("resultConsumer");
        }

        long alignment = Math.max(16L, VoxyVulkanContext.get().capabilities().minStorageBufferOffsetAlignment());
        long allocationSize = VulkanUploadStream.alignUp(size, alignment);
        if (allocationSize > this.capacity) {
            throw new IllegalArgumentException("Download allocation exceeds the original 32 MiB stream capacity");
        }

        int allocationBytes = Math.toIntExact(allocationSize);
        long allocationOffset = this.allocate(allocationBytes);
        try {
            VulkanCommandRecorder.record(commandBuffer -> {
                VulkanSync.bufferBarrier(
                        commandBuffer,
                        source.vkBuffer(),
                        sourceOffset,
                        size,
                        source.policy().declaredAccess(),
                        VulkanSync.TRANSFER_READ
                );
                VulkanSync.bufferBarrier(
                        commandBuffer,
                        this.vkDownloadBuffer.vkBuffer(),
                        allocationOffset,
                        size,
                        VulkanSync.HOST_READ,
                        VulkanSync.TRANSFER_WRITE
                );
                try (MemoryStack stack = MemoryStack.stackPush()) {
                    VkBufferCopy.Buffer region = VkBufferCopy.calloc(1, stack);
                    region.srcOffset(sourceOffset);
                    region.dstOffset(allocationOffset);
                    region.size(size);
                    VK12.vkCmdCopyBuffer(
                            commandBuffer,
                            source.vkBuffer(),
                            this.vkDownloadBuffer.vkBuffer(),
                            region
                    );
                }
                VulkanSync.bufferBarrier(
                        commandBuffer,
                        this.vkDownloadBuffer.vkBuffer(),
                        allocationOffset,
                        size,
                        VulkanSync.TRANSFER_WRITE,
                        VulkanSync.HOST_READ
                );
            });
        } catch (RuntimeException | Error failure) {
            this.allocationArena.free(allocationOffset);
            throw failure;
        }

        this.currentDownloads.add(new PendingDownload(
                allocationOffset,
                size,
                allocationSize,
                resultConsumer
        ));
        this.currentBytes += allocationSize;
        this.inFlightBytes += allocationSize;
    }

    private long allocate(int allocationBytes) {
        long allocationOffset = this.allocationArena.alloc(allocationBytes);
        if (allocationOffset == AllocationArena.SIZE_LIMIT) {
            this.sealCurrentFrame();
            if (!this.frames.isEmpty()) {
                VoxyVulkanContext.get().hostDevice().createCommandEncoder().submit();
            }
            while (allocationOffset == AllocationArena.SIZE_LIMIT && !this.frames.isEmpty()) {
                DownloadFrame first = this.frames.peekFirst();
                if (first == null || !first.fence().awaitCompletion(FORCE_WAIT_TIMEOUT_NS)) {
                    throw new IllegalStateException("Timed out waiting for Vulkan download storage");
                }
                this.releaseFrame(this.frames.removeFirst(), true);
                allocationOffset = this.allocationArena.alloc(allocationBytes);
            }
        }
        if (allocationOffset == AllocationArena.SIZE_LIMIT) {
            throw new IllegalStateException("Unable to free enough completed Vulkan download storage");
        }
        return allocationOffset;
    }

    public void tick() {
        this.ensureOpen();
        this.sealCurrentFrame();
        this.drainCompleted(true);
    }

    public void flushWaitClear() {
        this.ensureOpen();
        this.sealCurrentFrame();
        if (this.frames.isEmpty()) {
            return;
        }

        VoxyVulkanContext.get().hostDevice().createCommandEncoder().submit();
        this.waitForAllFrames(true);
    }

    public void waitDiscard() {
        this.ensureOpen();
        this.sealCurrentFrame();
        if (this.frames.isEmpty()) {
            return;
        }

        VoxyVulkanContext.get().hostDevice().createCommandEncoder().submit();
        this.waitForAllFrames(false);
    }

    private void sealCurrentFrame() {
        if (this.currentDownloads.isEmpty()) {
            return;
        }

        GpuFence fence = VoxyVulkanContext.get().hostDevice().createCommandEncoder().createFence();
        this.frames.addLast(new DownloadFrame(fence, List.copyOf(this.currentDownloads), this.currentBytes));
        this.currentDownloads.clear();
        this.currentBytes = 0L;
    }

    private void waitForAllFrames(boolean runCallbacks) {
        while (!this.frames.isEmpty()) {
            DownloadFrame frame = this.frames.peekFirst();
            if (!frame.fence().awaitCompletion(FORCE_WAIT_TIMEOUT_NS)) {
                throw new IllegalStateException("Timed out waiting for Vulkan download completion");
            }
            this.releaseFrame(this.frames.removeFirst(), runCallbacks);
        }
    }

    private void drainCompleted(boolean runCallbacks) {
        while (!this.frames.isEmpty()) {
            DownloadFrame frame = this.frames.peekFirst();
            if (!frame.fence().awaitCompletion(0L)) {
                break;
            }
            this.releaseFrame(this.frames.removeFirst(), runCallbacks);
        }
    }

    private void releaseFrame(DownloadFrame frame, boolean runCallbacks) {
        Throwable failure = null;
        for (PendingDownload download : frame.downloads()) {
            if (runCallbacks) {
                try {
                    download.consumer().consume(this.mappedBaseAddress + download.allocationOffset(), download.size());
                } catch (Throwable exception) {
                    failure = mergeFailure(failure, exception);
                }
            }
            try {
                this.allocationArena.free(download.allocationOffset());
            } catch (Throwable exception) {
                failure = mergeFailure(failure, exception);
            }
        }
        try {
            frame.fence().close();
        } catch (Throwable exception) {
            failure = mergeFailure(failure, exception);
        }
        this.inFlightBytes -= frame.allocationBytes();
        if (this.inFlightBytes < 0L) {
            failure = mergeFailure(failure, new IllegalStateException("Vulkan download byte accounting underflow"));
        }
        rethrowFailure(failure);
    }

    private static Throwable mergeFailure(Throwable failure, Throwable next) {
        if (failure == null) return next;
        if (failure != next) failure.addSuppressed(next);
        return failure;
    }

    private static void rethrowFailure(Throwable failure) {
        if (failure == null) return;
        if (failure instanceof RuntimeException exception) {
            throw exception;
        }
        if (failure instanceof Error error) {
            throw error;
        }
        throw new IllegalStateException("Unexpected checked failure while releasing Vulkan downloads", failure);
    }

    private void ensureOpen() {
        if (this.closed) {
            throw new IllegalStateException("Vulkan download stream is closed");
        }
    }

    public long capacity() {
        return this.capacity;
    }

    public long inFlightBytes() {
        return this.inFlightBytes;
    }

    @Override
    public void close() {
        if (this.closed) return;
        this.waitDiscard();
        this.closed = true;

        Throwable failure = null;
        try {
            this.mappedView.close();
        } catch (Throwable exception) {
            failure = exception;
        }
        try {
            this.downloadBuffer.close();
        } catch (Throwable exception) {
            failure = mergeFailure(failure, exception);
        }
        rethrowFailure(failure);
    }

    private record Resources(
            GpuBuffer buffer,
            VulkanGpuBuffer vkBuffer,
            GpuBufferSlice.MappedView mappedView,
            ByteBuffer mappedData
    ) {
    }

    private record PendingDownload(
            long allocationOffset,
            long size,
            long allocationSize,
            DownloadResultConsumer consumer
    ) {
    }

    private record DownloadFrame(GpuFence fence, List<PendingDownload> downloads, long allocationBytes) {
    }
}
