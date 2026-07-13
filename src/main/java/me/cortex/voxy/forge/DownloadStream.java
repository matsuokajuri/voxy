package me.cortex.voxy.forge;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import me.cortex.voxy.common.util.AllocationArena;
import me.cortex.voxy.common.util.MemoryBuffer;
import me.cortex.voxy.common.util.TrackedObject;
import org.lwjgl.system.MemoryUtil;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.function.Consumer;

import static me.cortex.voxy.common.util.AllocationArena.SIZE_LIMIT;
import static org.lwjgl.opengl.GL11C.glFinish;
import static org.lwjgl.opengl.GL15C.glDeleteBuffers;
import static org.lwjgl.opengl.GL30C.GL_MAP_READ_BIT;
import static org.lwjgl.opengl.GL32C.GL_SIGNALED;
import static org.lwjgl.opengl.GL32C.GL_SYNC_GPU_COMMANDS_COMPLETE;
import static org.lwjgl.opengl.GL32C.GL_SYNC_STATUS;
import static org.lwjgl.opengl.GL32C.GL_UNSIGNALED;
import static org.lwjgl.opengl.GL32C.glDeleteSync;
import static org.lwjgl.opengl.GL32C.glFenceSync;
import static org.lwjgl.opengl.GL32C.nglGetSynciv;
import static org.lwjgl.opengl.GL42C.GL_BUFFER_UPDATE_BARRIER_BIT;
import static org.lwjgl.opengl.GL42C.glMemoryBarrier;
import static org.lwjgl.opengl.GL44C.GL_CLIENT_MAPPED_BUFFER_BARRIER_BIT;
import static org.lwjgl.opengl.GL44C.GL_CLIENT_STORAGE_BIT;
import static org.lwjgl.opengl.GL44C.GL_MAP_PERSISTENT_BIT;
import static org.lwjgl.opengl.GL45C.glCopyNamedBufferSubData;
import static org.lwjgl.opengl.GL45C.glCreateBuffers;
import static org.lwjgl.opengl.GL45C.glNamedBufferStorage;
import static org.lwjgl.opengl.GL45C.glUnmapNamedBuffer;
import static org.lwjgl.opengl.GL45C.nglMapNamedBufferRange;

final class DownloadStream extends TrackedObject {
    private static final long DEFAULT_SIZE = 1L << 25;
    private static DownloadStream instance;

    private final AllocationArena allocationArena = new AllocationArena();
    private final int downloadBufferId;
    private final long downloadBufferSize;
    private final long downloadBufferAddress;
    private final Deque<DownloadFrame> frames = new ArrayDeque<>();
    private final LongArrayList thisFrameAllocations = new LongArrayList();
    private final Deque<DownloadData> downloadList = new ArrayDeque<>();
    private final ArrayList<DownloadData> thisFrameDownloadList = new ArrayList<>();
    private long caddr = -1L;
    private long offset;

    private DownloadStream(long size) {
        this.downloadBufferId = glCreateBuffers();
        GlDebug.buffer("DownloadStream", this.downloadBufferId);
        this.downloadBufferSize = size;
        glNamedBufferStorage(this.downloadBufferId, size, GL_MAP_PERSISTENT_BIT | GL_MAP_READ_BIT | GL_CLIENT_STORAGE_BIT);
        this.downloadBufferAddress = nglMapNamedBufferRange(
                this.downloadBufferId,
                0L,
                size,
                GL_MAP_PERSISTENT_BIT | GL_MAP_READ_BIT);
        this.allocationArena.setLimit(size);
    }

    static DownloadStream instance() {
        if (instance == null) {
            instance = new DownloadStream(DEFAULT_SIZE);
        }
        return instance;
    }

    static boolean isReady() {
        return instance != null && instance.downloadBufferId != 0 && instance.downloadBufferAddress != 0L;
    }

    void download(int bufferId, long bufferSize, Consumer<MemoryBuffer> consumer) {
        this.download(bufferId, bufferSize, 0L, bufferSize, consumer);
    }

    void download(int bufferId, long bufferSize, long downloadOffset, long size, Consumer<MemoryBuffer> consumer) {
        this.download(bufferId, bufferSize, downloadOffset, size, (ptr, actualSize) ->
                consumer.accept(MemoryBuffer.createUntrackedUnfreeableRawFrom(ptr, actualSize)));
    }

    void download(int bufferId, long bufferSize, long downloadOffset, long size, DownloadResultConsumer consumer) {
        if (size > Integer.MAX_VALUE || size <= 0L || downloadOffset + size > bufferSize) {
            throw new IllegalArgumentException();
        }
        long addr;
        if (this.caddr == -1L || !this.allocationArena.expand(this.caddr, (int) size)) {
            this.caddr = this.allocationArena.alloc((int) size);
            if (this.caddr == SIZE_LIMIT) {
                VoxyForge.LOGGER.warn("Download stream full, preemptively committing");
                this.commit();
                int attempts = 10;
                while (--attempts != 0 && this.caddr == SIZE_LIMIT) {
                    glFinish();
                    this.tick();
                    this.caddr = this.allocationArena.alloc((int) size);
                }
                if (this.caddr == SIZE_LIMIT) {
                    throw new IllegalStateException("Could not allocate download memory segment even after force flush");
                }
            }
            this.thisFrameAllocations.add(this.caddr);
            this.offset = size;
            addr = this.caddr;
        } else {
            addr = this.caddr + this.offset;
            this.offset += size;
        }
        if (this.caddr + size > this.downloadBufferSize) {
            throw new IllegalStateException();
        }
        this.downloadList.add(new DownloadData(bufferId, addr, downloadOffset, size, consumer));
        this.commit();
    }

    void commit() {
        if (this.downloadList.isEmpty()) {
            return;
        }
        glMemoryBarrier(GL_BUFFER_UPDATE_BARRIER_BIT);
        for (DownloadData entry : this.downloadList) {
            glCopyNamedBufferSubData(
                    entry.targetBufferId,
                    this.downloadBufferId,
                    entry.targetOffset,
                    entry.downloadStreamOffset,
                    entry.size);
        }
        glMemoryBarrier(GL_CLIENT_MAPPED_BUFFER_BARRIER_BIT | GL_BUFFER_UPDATE_BARRIER_BIT);
        this.thisFrameDownloadList.addAll(this.downloadList);
        this.downloadList.clear();
        this.caddr = -1L;
        this.offset = 0L;
    }

    void tick() {
        this.commit();
        if (!this.thisFrameAllocations.isEmpty()) {
            this.frames.add(new DownloadFrame(
                    new Fence(),
                    new LongArrayList(this.thisFrameAllocations),
                    new ArrayList<>(this.thisFrameDownloadList)));
            this.thisFrameAllocations.clear();
            this.thisFrameDownloadList.clear();
        }
        while (!this.frames.isEmpty()) {
            if (!this.frames.peek().fence.signaled()) {
                break;
            }
            DownloadFrame frame = this.frames.pop();
            for (DownloadData data : frame.data) {
                data.consumer.consume(this.downloadBufferAddress + data.downloadStreamOffset, data.size);
            }
            frame.allocations.forEach(this.allocationArena::free);
            frame.fence.free();
        }
    }

    void flushWaitClear() {
        glFinish();
        this.tick();
        Fence fence = new Fence();
        glFinish();
        while (!fence.signaled()) {
            glFinish();
            Thread.onSpinWait();
        }
        fence.free();
        this.tick();
        if (!this.frames.isEmpty()) {
            throw new IllegalStateException();
        }
    }

    @Override
    public void free() {
        this.free0();
        this.commit();
        while (!this.frames.isEmpty()) {
            DownloadFrame frame = this.frames.pop();
            frame.fence.free();
        }
        glUnmapNamedBuffer(this.downloadBufferId);
        glDeleteBuffers(this.downloadBufferId);
        if (instance == this) {
            instance = null;
        }
    }

    interface DownloadResultConsumer {
        void consume(long ptr, long size);
    }

    private record DownloadFrame(Fence fence, LongArrayList allocations, ArrayList<DownloadData> data) {
    }

    private record DownloadData(int targetBufferId, long downloadStreamOffset, long targetOffset, long size, DownloadResultConsumer consumer) {
    }

    private static final class Fence extends TrackedObject {
        private static final long SCRATCH = MemoryUtil.nmemCalloc(1L, Integer.BYTES);
        private final long fence = glFenceSync(GL_SYNC_GPU_COMMANDS_COMPLETE, 0);
        private boolean signaled;

        boolean signaled() {
            if (!this.signaled) {
                MemoryUtil.memPutInt(SCRATCH, -1);
                nglGetSynciv(this.fence, GL_SYNC_STATUS, 1, 0L, SCRATCH);
                int value = MemoryUtil.memGetInt(SCRATCH);
                if (value == GL_SIGNALED) {
                    this.signaled = true;
                } else if (value != GL_UNSIGNALED) {
                    throw new IllegalStateException("Unknown data from glGetSync: " + value);
                }
            }
            return this.signaled;
        }

        @Override
        public void free() {
            this.free0();
            glDeleteSync(this.fence);
        }
    }
}
