package me.cortex.voxy.forge;

import com.mojang.blaze3d.systems.RenderSystem;
import org.lwjgl.system.MemoryUtil;

import static org.lwjgl.opengl.GL15C.GL_DYNAMIC_DRAW;
import static org.lwjgl.opengl.GL15C.glDeleteBuffers;
import static org.lwjgl.opengl.GL45C.glCreateBuffers;
import static org.lwjgl.opengl.GL45C.nglGetNamedBufferSubData;
import static org.lwjgl.opengl.GL45C.nglNamedBufferData;

final class ForgeMdicDebugDrawCountBuffer {
    static final int BYTES = Integer.BYTES;

    private int bufferId;
    private int drawCountValue;
    private int maxDrawCount;
    private long bytes;
    private long heapGeneration = -1L;
    private long commandListBuildTimeMillis;
    private String dimensionId = "none";
    private String lastUploadError = "none";

    boolean upload(ForgeMdicCommandList commandList, int maxCommands, int maxRecords, int configuredMaxDrawCount) {
        requireRenderThread("upload MDIC debug draw count buffer");
        if (commandList == null || !commandList.isValid()) {
            this.closeOnRenderThread();
            this.lastUploadError = "command-list-empty";
            return false;
        }
        DrawCountBudget budget = DrawCountBudget.from(commandList, maxCommands, maxRecords, configuredMaxDrawCount);
        if (budget.maxDrawCount <= 0) {
            this.closeOnRenderThread();
            this.lastUploadError = "invalid-max-draw-count:" + budget.maxDrawCount;
            return false;
        }
        if (this.matches(commandList, maxCommands, maxRecords, configuredMaxDrawCount)) {
            this.lastUploadError = "none";
            return true;
        }

        long ptr = MemoryUtil.nmemAlloc(BYTES);
        try {
            MemoryUtil.memPutInt(ptr, budget.drawCountValue);
            if (this.bufferId == 0) {
                this.bufferId = glCreateBuffers();
            }
            nglNamedBufferData(this.bufferId, BYTES, ptr, GL_DYNAMIC_DRAW);
            this.drawCountValue = budget.drawCountValue;
            this.maxDrawCount = budget.maxDrawCount;
            this.bytes = BYTES;
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

    boolean matches(ForgeMdicCommandList commandList, int maxCommands, int maxRecords, int configuredMaxDrawCount) {
        if (commandList == null || !commandList.isValid() || this.bufferId == 0) {
            return false;
        }
        DrawCountBudget budget = DrawCountBudget.from(commandList, maxCommands, maxRecords, configuredMaxDrawCount);
        return this.drawCountValue == budget.drawCountValue
                && this.maxDrawCount == budget.maxDrawCount
                && this.bytes == BYTES
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

    int readbackValue() {
        requireRenderThread("read back MDIC debug draw count buffer");
        if (this.bufferId == 0 || this.bytes != BYTES) {
            throw new IllegalStateException("MDIC debug draw count buffer is not created");
        }

        long ptr = MemoryUtil.nmemAlloc(BYTES);
        try {
            nglGetNamedBufferSubData(this.bufferId, 0L, BYTES, ptr);
            return MemoryUtil.memGetInt(ptr);
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
        this.drawCountValue = 0;
        this.maxDrawCount = 0;
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

    int drawCountValue() {
        return this.drawCountValue;
    }

    int maxDrawCount() {
        return this.maxDrawCount;
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

    private record DrawCountBudget(int drawCountValue, int maxDrawCount) {
        static DrawCountBudget from(ForgeMdicCommandList commandList, int maxCommands, int maxRecords, int configuredMaxDrawCount) {
            int commandLimit = Math.min(Math.max(0, maxCommands), commandList.commandCount());
            int maxDrawCount = Math.min(Math.max(1, configuredMaxDrawCount), commandLimit);
            long recordsLeft = Math.max(0, maxRecords);
            int count = 0;
            for (int i = 0; i < commandLimit && recordsLeft > 0L && count < maxDrawCount; i++) {
                ForgeMdicCommand command = commandList.commands().get(i);
                int records = Math.max(0, command.recordCount());
                int drawnRecords = (int) Math.min(recordsLeft, records);
                recordsLeft -= drawnRecords;
                count++;
            }
            return new DrawCountBudget(count, maxDrawCount);
        }
    }
}
