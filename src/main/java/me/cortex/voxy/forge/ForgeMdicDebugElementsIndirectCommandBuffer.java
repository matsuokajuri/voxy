package me.cortex.voxy.forge;

import com.mojang.blaze3d.systems.RenderSystem;
import org.lwjgl.system.MemoryUtil;

import static org.lwjgl.opengl.GL15C.GL_DYNAMIC_DRAW;
import static org.lwjgl.opengl.GL15C.glDeleteBuffers;
import static org.lwjgl.opengl.GL45C.glCreateBuffers;
import static org.lwjgl.opengl.GL45C.nglGetNamedBufferSubData;
import static org.lwjgl.opengl.GL45C.nglNamedBufferData;

final class ForgeMdicDebugElementsIndirectCommandBuffer {
    static final int WORDS_PER_COMMAND = 5;
    static final int BYTES_PER_COMMAND = WORDS_PER_COMMAND * Integer.BYTES;

    private int bufferId;
    private int commandCount;
    private long bytes;
    private long heapGeneration = -1L;
    private long commandListBuildTimeMillis;
    private String dimensionId = "none";
    private String lastUploadError = "none";

    boolean upload(ForgeMdicCommandList commandList, int maxCommands, int maxRecords) {
        requireRenderThread("upload MDIC debug elements indirect command buffer");
        if (commandList == null || !commandList.isValid()) {
            this.closeOnRenderThread();
            this.lastUploadError = "command-list-empty";
            return false;
        }
        ElementsBudget budget = ElementsBudget.from(commandList, maxCommands, maxRecords);
        if (budget.commandCount <= 0 || budget.bytes <= 0L || budget.bytes > Integer.MAX_VALUE) {
            this.closeOnRenderThread();
            this.lastUploadError = "invalid-size:" + budget.bytes;
            return false;
        }
        if (this.matches(commandList, maxCommands, maxRecords)) {
            this.lastUploadError = "none";
            return true;
        }

        long ptr = MemoryUtil.nmemAlloc(budget.bytes);
        try {
            long offset = 0L;
            long recordsLeft = Math.max(0, maxRecords);
            int commandIndex = 0;
            for (ForgeMdicCommand command : commandList.commands()) {
                if (commandIndex >= budget.commandCount || recordsLeft <= 0L) {
                    break;
                }
                int records = Math.max(0, command.recordCount());
                int drawnRecords = (int) Math.min(recordsLeft, records);
                int indexCount = drawnRecords * ForgeMdicDebugSharedIndexBuffer.INDICES_PER_QUAD;
                MemoryUtil.memPutInt(ptr + offset, indexCount);
                MemoryUtil.memPutInt(ptr + offset + 4L, 1);
                MemoryUtil.memPutInt(ptr + offset + 8L, 0);
                MemoryUtil.memPutInt(ptr + offset + 12L, 0);
                MemoryUtil.memPutInt(ptr + offset + 16L, commandIndex);
                recordsLeft -= drawnRecords;
                commandIndex++;
                offset += BYTES_PER_COMMAND;
            }
            if (this.bufferId == 0) {
                this.bufferId = glCreateBuffers();
            }
            nglNamedBufferData(this.bufferId, budget.bytes, ptr, GL_DYNAMIC_DRAW);
            this.commandCount = budget.commandCount;
            this.bytes = budget.bytes;
            this.heapGeneration = commandList.heapGeneration();
            this.commandListBuildTimeMillis = commandList.buildTimeMillis();
            this.dimensionId = commandList.dimensionId();
            this.lastUploadError = "none";
            return true;
        } catch (RuntimeException e) {
            this.lastUploadError = e.getClass().getSimpleName() + ": " + e.getMessage();
            this.closeOnRenderThread();
            return false;
        } finally {
            MemoryUtil.nmemFree(ptr);
        }
    }

    boolean matches(ForgeMdicCommandList commandList, int maxCommands, int maxRecords) {
        if (commandList == null || !commandList.isValid() || this.bufferId == 0) {
            return false;
        }
        ElementsBudget budget = ElementsBudget.from(commandList, maxCommands, maxRecords);
        return this.commandCount == budget.commandCount
                && this.bytes == budget.bytes
                && this.heapGeneration == commandList.heapGeneration()
                && this.commandListBuildTimeMillis == commandList.buildTimeMillis()
                && this.dimensionId.equals(commandList.dimensionId());
    }

    boolean isStale(long currentHeapGeneration, String currentDimension) {
        if (this.bufferId == 0) {
            return false;
        }
        return this.heapGeneration != currentHeapGeneration || !this.dimensionId.equals(currentDimension);
    }

    int[] readbackWords() {
        requireRenderThread("read back MDIC debug elements indirect command buffer");
        if (this.bufferId == 0 || this.commandCount <= 0 || this.bytes <= 0L) {
            throw new IllegalStateException("MDIC debug elements indirect command buffer is not created");
        }
        if (this.bytes > Integer.MAX_VALUE) {
            throw new IllegalStateException("MDIC debug elements indirect command buffer is too large to audit: " + this.bytes);
        }

        int[] words = new int[this.commandCount * WORDS_PER_COMMAND];
        long ptr = MemoryUtil.nmemAlloc(this.bytes);
        try {
            nglGetNamedBufferSubData(this.bufferId, 0L, this.bytes, ptr);
            for (int i = 0; i < words.length; i++) {
                words[i] = MemoryUtil.memGetInt(ptr + i * (long) Integer.BYTES);
            }
            return words;
        } finally {
            MemoryUtil.nmemFree(ptr);
        }
    }

    void close() {
        if (RenderSystem.isOnRenderThread()) {
            this.closeOnRenderThread();
        } else {
            RenderSystem.recordRenderCall(this::closeOnRenderThread);
        }
    }

    void closeOnRenderThread() {
        if (this.bufferId != 0) {
            glDeleteBuffers(this.bufferId);
        }
        this.bufferId = 0;
        this.commandCount = 0;
        this.bytes = 0L;
        this.heapGeneration = -1L;
        this.commandListBuildTimeMillis = 0L;
        this.dimensionId = "none";
    }

    int bufferId() {
        return this.bufferId;
    }

    boolean isCreated() {
        return this.bufferId != 0;
    }

    int commandCount() {
        return this.commandCount;
    }

    long bytes() {
        return this.bytes;
    }

    long heapGeneration() {
        return this.heapGeneration;
    }

    String dimensionId() {
        return this.dimensionId;
    }

    String lastUploadError() {
        return this.lastUploadError;
    }

    private static void requireRenderThread(String action) {
        if (!RenderSystem.isOnRenderThread()) {
            throw new IllegalStateException("Cannot " + action + " outside the render thread");
        }
    }

    private record ElementsBudget(int commandCount, long bytes) {
        static ElementsBudget from(ForgeMdicCommandList commandList, int maxCommands, int maxRecords) {
            int limit = Math.min(Math.max(0, maxCommands), commandList.commandCount());
            long recordsLeft = Math.max(0, maxRecords);
            int count = 0;
            for (int i = 0; i < limit && recordsLeft > 0L; i++) {
                ForgeMdicCommand command = commandList.commands().get(i);
                int records = Math.max(0, command.recordCount());
                if (records == 0) {
                    count++;
                    continue;
                }
                int drawnRecords = (int) Math.min(recordsLeft, records);
                recordsLeft -= drawnRecords;
                count++;
            }
            return new ElementsBudget(count, (long) count * BYTES_PER_COMMAND);
        }
    }
}
