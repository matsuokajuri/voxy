package me.cortex.voxy.client.core.vulkan;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.buffers.GpuFence;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.vulkan.VulkanGpuBuffer;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.system.MemoryStack;
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
 * A callback receives a mapped pointer only after the graphics-queue submit containing the copy has completed;
 * the pointer becomes invalid for client use as soon as that callback returns.
 */
public final class VulkanDownloadStream {
    public static final long DEFAULT_CAPACITY = 1L << 25;
    private static final long FORCE_WAIT_TIMEOUT_NS = 5_000_000_000L;

    @FunctionalInterface
    public interface DownloadResultConsumer {
        void consume(long pointer, long size);
    }

    private final long capacity;
    private final ArrayList<PendingDownload> currentDownloads = new ArrayList<>();
    private final Deque<DownloadFrame> frames = new ArrayDeque<>();
    private long currentBytes;
    private long inFlightBytes;

    public VulkanDownloadStream() {
        this(DEFAULT_CAPACITY);
    }

    public VulkanDownloadStream(long capacity) {
        if (capacity <= 0L) {
            throw new IllegalArgumentException("Download capacity must be greater than zero");
        }
        this.capacity = capacity;
    }

    public void download(VoxyVulkanBuffer source, DownloadResultConsumer resultConsumer) {
        this.download(source, 0L, source.size(), resultConsumer);
    }

    public void download(VoxyVulkanBuffer source, long sourceOffset, long size, DownloadResultConsumer resultConsumer) {
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
        if (resultConsumer == null) {
            throw new NullPointerException("resultConsumer");
        }

        long alignment = Math.max(16L, VoxyVulkanContext.get().capabilities().minStorageBufferOffsetAlignment());
        long allocationSize = VulkanUploadStream.alignUp(size, alignment);
        if (allocationSize > this.capacity) {
            throw new IllegalArgumentException("Download allocation exceeds the original 32 MiB stream capacity");
        }
        if (this.inFlightBytes + allocationSize > this.capacity) {
            this.forceDrainForCapacity();
        }
        if (this.inFlightBytes + allocationSize > this.capacity) {
            throw new IllegalStateException("Unable to free enough completed Vulkan download storage");
        }

        CommandEncoder encoder = VoxyVulkanContext.get().hostDevice().createCommandEncoder();
        GpuBufferSlice.MappedView mapped = encoder.transientMemory()
                .allocateStaging(allocationSize, alignment, GpuBuffer.USAGE_COPY_DST);
        GpuBufferSlice readbackSlice = mapped.slice().slice(0L, size);
        VulkanCommandRecorder.record(commandBuffer -> {
            VulkanSync.bufferBarrier(
                    commandBuffer,
                    source.vkBuffer(),
                    sourceOffset,
                    size,
                    source.policy().declaredAccess(),
                    VulkanSync.TRANSFER_READ
            );
            try (MemoryStack stack = MemoryStack.stackPush()) {
                VkBufferCopy.Buffer region = VkBufferCopy.calloc(1, stack);
                region.srcOffset(sourceOffset);
                region.dstOffset(readbackSlice.offset());
                region.size(size);
                VK12.vkCmdCopyBuffer(
                        commandBuffer,
                        source.vkBuffer(),
                        ((VulkanGpuBuffer) readbackSlice.buffer()).vkBuffer(),
                        region
                );
            }
            VulkanSync.bufferBarrier(
                    commandBuffer,
                    ((VulkanGpuBuffer) readbackSlice.buffer()).vkBuffer(),
                    readbackSlice.offset(),
                    size,
                    VulkanSync.TRANSFER_WRITE,
                    VulkanSync.HOST_READ
            );
        });

        ByteBuffer data = mapped.data().duplicate().order(ByteOrder.nativeOrder());
        data.clear();
        data.limit((int) size);
        data = data.slice().order(ByteOrder.nativeOrder());
        this.currentDownloads.add(new PendingDownload(mapped, data, size, allocationSize, resultConsumer));
        this.currentBytes += allocationSize;
        this.inFlightBytes += allocationSize;
    }

    public void tick() {
        this.sealCurrentFrame();
        this.drainCompleted(true);
    }

    public void flushWaitClear() {
        this.sealCurrentFrame();
        if (this.frames.isEmpty()) {
            return;
        }

        VoxyVulkanContext.get().hostDevice().createCommandEncoder().submit();
        this.waitForAllFrames(true);
    }

    public void waitDiscard() {
        this.sealCurrentFrame();
        if (this.frames.isEmpty()) {
            return;
        }

        VoxyVulkanContext.get().hostDevice().createCommandEncoder().submit();
        this.waitForAllFrames(false);
    }

    private void forceDrainForCapacity() {
        this.sealCurrentFrame();
        if (this.frames.isEmpty()) {
            return;
        }

        VoxyVulkanContext.get().hostDevice().createCommandEncoder().submit();
        DownloadFrame first = this.frames.peekFirst();
        if (first == null || !first.fence().awaitCompletion(FORCE_WAIT_TIMEOUT_NS)) {
            throw new IllegalStateException("Timed out waiting for Vulkan download storage");
        }
        this.drainCompleted(true);
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
            if (runCallbacks && failure == null) {
                try {
                    download.consumer().consume(MemoryUtil.memAddress(download.data()), download.size());
                } catch (Throwable exception) {
                    failure = exception;
                }
            }
            try {
                download.mappedView().close();
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

    public long capacity() {
        return this.capacity;
    }

    public long inFlightBytes() {
        return this.inFlightBytes;
    }

    private record PendingDownload(
            GpuBufferSlice.MappedView mappedView,
            ByteBuffer data,
            long size,
            long allocationSize,
            DownloadResultConsumer consumer
    ) {
    }

    private record DownloadFrame(GpuFence fence, List<PendingDownload> downloads, long allocationBytes) {
    }
}
