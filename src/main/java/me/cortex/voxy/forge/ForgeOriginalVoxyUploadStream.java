package me.cortex.voxy.forge;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import me.cortex.voxy.common.util.AllocationArena;
import me.cortex.voxy.common.util.MemoryBuffer;
import org.lwjgl.system.MemoryUtil;

import java.util.ArrayDeque;
import java.util.Deque;

import static me.cortex.voxy.common.util.AllocationArena.SIZE_LIMIT;
import static org.lwjgl.opengl.GL11C.glFinish;
import static org.lwjgl.opengl.GL15C.glDeleteBuffers;
import static org.lwjgl.opengl.GL30C.GL_MAP_FLUSH_EXPLICIT_BIT;
import static org.lwjgl.opengl.GL30C.GL_MAP_UNSYNCHRONIZED_BIT;
import static org.lwjgl.opengl.GL30C.GL_MAP_WRITE_BIT;
import static org.lwjgl.opengl.GL32C.GL_SIGNALED;
import static org.lwjgl.opengl.GL32C.GL_SYNC_GPU_COMMANDS_COMPLETE;
import static org.lwjgl.opengl.GL32C.GL_SYNC_STATUS;
import static org.lwjgl.opengl.GL32C.GL_UNSIGNALED;
import static org.lwjgl.opengl.GL32C.glDeleteSync;
import static org.lwjgl.opengl.GL32C.glFenceSync;
import static org.lwjgl.opengl.GL32C.nglGetSynciv;
import static org.lwjgl.opengl.GL42C.GL_BUFFER_UPDATE_BARRIER_BIT;
import static org.lwjgl.opengl.GL42C.glMemoryBarrier;
import static org.lwjgl.opengl.GL43C.GL_SHADER_STORAGE_BUFFER_OFFSET_ALIGNMENT;
import static org.lwjgl.opengl.GL44C.GL_CLIENT_STORAGE_BIT;
import static org.lwjgl.opengl.GL44C.GL_MAP_COHERENT_BIT;
import static org.lwjgl.opengl.GL44C.GL_MAP_PERSISTENT_BIT;
import static org.lwjgl.opengl.GL45C.glCopyNamedBufferSubData;
import static org.lwjgl.opengl.GL45C.glCreateBuffers;
import static org.lwjgl.opengl.GL45C.glFlushMappedNamedBufferRange;
import static org.lwjgl.opengl.GL45C.glGetInteger;
import static org.lwjgl.opengl.GL45C.glNamedBufferStorage;
import static org.lwjgl.opengl.GL45C.glUnmapNamedBuffer;
import static org.lwjgl.opengl.GL45C.nglMapNamedBufferRange;

final class ForgeOriginalVoxyUploadStream {
    private static final boolean USE_COHERENT = false;
    private static final long DEFAULT_SIZE = 1L << 26;
    private static ForgeOriginalVoxyUploadStream instance;

    private final int baseAllocationAlignment;
    private final AllocationArena allocationArena = new AllocationArena();
    private final int uploadBufferId;
    private final long uploadBufferSize;
    private final long uploadBufferAddress;
    private final Deque<UploadFrame> frames = new ArrayDeque<>();
    private final LongArrayList thisFrameAllocations = new LongArrayList();
    private final Deque<UploadData> uploadList = new ArrayDeque<>();
    private long caddr = -1L;
    private long offset;

    private ForgeOriginalVoxyUploadStream(long size) {
        this.baseAllocationAlignment = Math.max(glGetInteger(GL_SHADER_STORAGE_BUFFER_OFFSET_ALIGNMENT), 16);
        this.uploadBufferId = glCreateBuffers();
        this.uploadBufferSize = size;
        int flags = GL_CLIENT_STORAGE_BIT
                | GL_MAP_WRITE_BIT
                | GL_MAP_UNSYNCHRONIZED_BIT
                | (USE_COHERENT ? GL_MAP_COHERENT_BIT : GL_MAP_FLUSH_EXPLICIT_BIT);
        glNamedBufferStorage(
                this.uploadBufferId,
                size,
                GL_MAP_PERSISTENT_BIT | (flags & (GL_MAP_COHERENT_BIT | GL_MAP_WRITE_BIT | GL_CLIENT_STORAGE_BIT)));
        this.uploadBufferAddress = nglMapNamedBufferRange(
                this.uploadBufferId,
                0L,
                size,
                (flags & (GL_MAP_WRITE_BIT | GL_MAP_UNSYNCHRONIZED_BIT | GL_MAP_FLUSH_EXPLICIT_BIT))
                        | GL_MAP_PERSISTENT_BIT);
        this.allocationArena.setLimit(size);
    }

    static ForgeOriginalVoxyUploadStream instance() {
        if (instance == null) {
            instance = new ForgeOriginalVoxyUploadStream(DEFAULT_SIZE);
        }
        return instance;
    }

    static boolean isReady() {
        return instance != null && instance.uploadBufferId != 0 && instance.uploadBufferAddress != 0L;
    }

    long upload(int bufferId, long destOffset, MemoryBuffer data) {
        data.cpyTo(this.upload(bufferId, destOffset, data.size));
        return data.size;
    }

    long upload(int bufferId, long destOffset, long size) {
        long addr = this.rawUploadAddress((int) size);
        this.uploadList.add(new UploadData(bufferId, addr, destOffset, size));
        return this.uploadBufferAddress + addr;
    }

    long rawUpload(int size) {
        return this.uploadBufferAddress + this.rawUploadAddress(size);
    }

    long rawUploadAddress(int size) {
        if (size < 0) {
            throw new IllegalStateException("Negative size");
        }

        size = alignUp(size, this.baseAllocationAlignment);
        if (size > this.uploadBufferSize) {
            throw new IllegalArgumentException();
        }

        long addr;
        if (this.caddr == -1L || !this.allocationArena.expand(this.caddr, size)) {
            if (!USE_COHERENT && this.caddr != -1L) {
                glFlushMappedNamedBufferRange(this.uploadBufferId, this.caddr, this.offset);
            }
            this.caddr = this.allocationArena.alloc(size);
            if (this.caddr == SIZE_LIMIT) {
                VoxyForge.LOGGER.error("Upload stream full, preemptively committing, this could cause bad things to happen");
                int attempts = 10;
                while (--attempts != 0 && this.caddr == SIZE_LIMIT) {
                    glFinish();
                    this.tick(false);
                    this.caddr = this.allocationArena.alloc(size);
                }
                if (this.caddr == SIZE_LIMIT) {
                    throw new IllegalStateException("Could not allocate memory segment big enough for upload even after force flush");
                }
            }
            this.thisFrameAllocations.add(this.caddr);
            this.offset = size;
            addr = this.caddr;
        } else {
            addr = this.caddr + this.offset;
            this.offset += size;
        }

        if (this.caddr + size > this.uploadBufferSize) {
            throw new IllegalStateException();
        }
        return addr;
    }

    void commit() {
        if (!USE_COHERENT && this.caddr != -1L) {
            glFlushMappedNamedBufferRange(this.uploadBufferId, this.caddr, this.offset);
        }
        if (this.uploadList.isEmpty()) {
            return;
        }

        glMemoryBarrier(GL_BUFFER_UPDATE_BARRIER_BIT);
        for (UploadData entry : this.uploadList) {
            glCopyNamedBufferSubData(
                    this.uploadBufferId,
                    entry.targetBufferId,
                    entry.uploadOffset,
                    entry.targetOffset,
                    entry.size);
        }
        this.uploadList.clear();
        glMemoryBarrier(GL_BUFFER_UPDATE_BARRIER_BIT);
        this.caddr = -1L;
        this.offset = 0L;
    }

    void tick() {
        this.tick(true);
    }

    private void tick(boolean commit) {
        if (commit) {
            this.commit();
        }

        if (!this.thisFrameAllocations.isEmpty()) {
            this.frames.add(new UploadFrame(new Fence(), new LongArrayList(this.thisFrameAllocations)));
            this.thisFrameAllocations.clear();
        }

        while (!this.frames.isEmpty()) {
            if (!this.frames.peek().fence.signaled()) {
                break;
            }
            UploadFrame frame = this.frames.pop();
            frame.allocations.forEach(this.allocationArena::free);
            frame.fence.free();
        }
    }

    long getBaseAddress() {
        return this.uploadBufferAddress;
    }

    int getRawBufferId() {
        return this.uploadBufferId;
    }

    private static int alignUp(int val, int alignment) {
        return ((val + alignment - 1) / alignment) * alignment;
    }

    private record UploadFrame(Fence fence, LongArrayList allocations) {
    }

    private record UploadData(int targetBufferId, long uploadOffset, long targetOffset, long size) {
    }

    private static final class Fence {
        private static final long SCRATCH = MemoryUtil.nmemCalloc(1L, 4L);
        private final long fence = glFenceSync(GL_SYNC_GPU_COMMANDS_COMPLETE, 0);
        private boolean signaled;

        boolean signaled() {
            if (!this.signaled) {
                MemoryUtil.memPutInt(SCRATCH, -1);
                nglGetSynciv(this.fence, GL_SYNC_STATUS, 1, 0L, SCRATCH);
                int val = MemoryUtil.memGetInt(SCRATCH);
                if (val == GL_SIGNALED) {
                    this.signaled = true;
                } else if (val != GL_UNSIGNALED) {
                    throw new IllegalStateException("Unknown data from glGetSync: " + val);
                }
            }
            return this.signaled;
        }

        void free() {
            glDeleteSync(this.fence);
        }
    }

    void free() {
        this.commit();
        while (!this.frames.isEmpty()) {
            UploadFrame frame = this.frames.pop();
            frame.fence.free();
        }
        glUnmapNamedBuffer(this.uploadBufferId);
        glDeleteBuffers(this.uploadBufferId);
        if (instance == this) {
            instance = null;
        }
    }
}
