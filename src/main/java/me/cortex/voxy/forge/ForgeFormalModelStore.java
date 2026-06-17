package me.cortex.voxy.forge;

import com.mojang.blaze3d.systems.RenderSystem;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL12C;
import org.lwjgl.opengl.GL15C;
import org.lwjgl.opengl.GL21C;
import org.lwjgl.opengl.GL30C;
import org.lwjgl.opengl.GL33C;
import org.lwjgl.opengl.GL45C;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;

final class ForgeFormalModelStore {
    static final String STAGE = "I2_FORMAL_MODELSTORE_OWNERSHIP_SKELETON";
    static final String ATLAS_FORMAT = "RGBA8";
    static final int MODEL_CAPACITY = 1 << 16;
    static final long MODEL_DATA_BYTES = (long) ForgeModelStoreFormalLayout.MODEL_RECORD_BYTES * MODEL_CAPACITY;
    static final long MODEL_COLOUR_BYTES = (long) Integer.BYTES * MODEL_CAPACITY;

    private static final int DEBUG_SMALL_ATLAS_WIDTH = ForgeModelAtlasLayout.MODEL_TEXTURE_SIZE * ForgeModelAtlasLayout.FACES_PER_MODEL_X;
    private static final int DEBUG_SMALL_ATLAS_HEIGHT = ForgeModelAtlasLayout.MODEL_TEXTURE_SIZE * ForgeModelAtlasLayout.FACES_PER_MODEL_Y;

    private long buildRuns;
    private long clearRuns;
    private long auditRuns;
    private long auditFailures;
    private String lastBuildError = "none";
    private String lastAllocationError = "none";
    private double lastBuildDurationMs;
    private ForgeFormalModelStoreLifecycleState lifecycleState = ForgeFormalModelStoreLifecycleState.UNINITIALIZED;
    private int modelDataBufferId;
    private int modelColourBufferId;
    private int atlasTextureId;
    private int samplerId;
    private int actualAtlasWidth;
    private int actualAtlasHeight;
    private boolean enabled;
    private boolean fullAtlasTextureCreated;
    private boolean debugSmallAtlasFallback;
    private boolean allocationFailed;
    private boolean samplerConfigured;
    private boolean stale;
    private boolean requiresRebuild;
    private boolean resourceReloadSeen;
    private boolean worldUnloadSeen;
    private boolean dimensionSwitchSeen;
    private boolean debugPipelineClearSeen;
    private boolean presetOffSeen;
    private boolean presetClearSeen;
    private String lastLifecycleEvent = "initialized";
    private String staleReason = "none";
    private boolean cleanupScheduled;
    private boolean cleanupOnRenderThread;
    private boolean cleanupCompleted;
    private long cleanupFailures;
    private ForgeFormalModelStoreAuditResult lastAudit = ForgeFormalModelStoreAuditResult.failure("none", 0.0D);

    ForgeFormalModelStoreStats build() {
        this.buildRuns++;
        long start = System.nanoTime();
        if (!RenderSystem.isOnRenderThread()) {
            this.failBuild("not-render-thread", start);
            return this.createStatusSnapshot();
        }

        try {
            this.closeOnRenderThread(false);
            this.allocateBuffers();
            this.allocateAtlasTexture();
            this.allocateSampler();
            this.enabled = true;
            this.stale = false;
            this.requiresRebuild = false;
            this.lastLifecycleEvent = "build";
            this.staleReason = "none";
            this.lastBuildError = "none";
            if (!this.allocationFailed) {
                this.lastAllocationError = "none";
            }
            this.lifecycleState = ForgeFormalModelStoreLifecycleState.ALLOCATED;
            this.lastAudit = ForgeFormalModelStoreAuditResult.failure("none", 0.0D);
        } catch (RuntimeException e) {
            this.closeOnRenderThread(false);
            this.failBuild(e.getClass().getSimpleName() + ": " + e.getMessage(), start);
            return this.createStatusSnapshot();
        }
        this.lastBuildDurationMs = elapsedMs(start);
        return this.createStatusSnapshot();
    }

    ForgeFormalModelStoreAuditResult audit() {
        this.auditRuns++;
        long start = System.nanoTime();
        ForgeFormalModelStoreAuditResult result;
        if (!RenderSystem.isOnRenderThread()) {
            result = ForgeFormalModelStoreAuditResult.failure("not-render-thread", elapsedMs(start));
        } else {
            try {
                result = this.auditOnRenderThread(start);
            } catch (RuntimeException e) {
                result = ForgeFormalModelStoreAuditResult.failure(e.getClass().getSimpleName() + ": " + e.getMessage(), elapsedMs(start));
            }
        }
        this.lastAudit = result;
        if (!result.success()) {
            this.auditFailures++;
        }
        return result;
    }

    ForgeFormalModelStoreStats createStatusSnapshot() {
        boolean modelDataCreated = this.modelDataBufferId != 0;
        boolean modelColourCreated = this.modelColourBufferId != 0;
        boolean atlasCreated = this.atlasTextureId != 0;
        boolean samplerCreated = this.samplerId != 0;
        boolean skeletonReady = modelDataCreated
                && modelColourCreated
                && ForgeModelStoreFormalLayout.FORMAL_LAYOUT_KNOWN
                && atlasLayoutReady()
                && samplerCreated
                && this.samplerConfigured
                && (atlasCreated || this.allocationFailed);
        return new ForgeFormalModelStoreStats(
                STAGE,
                this.buildRuns,
                this.clearRuns,
                this.auditRuns,
                this.auditFailures,
                this.lastBuildError,
                this.lastAllocationError,
                this.lastBuildDurationMs,
                skeletonReady,
                skeletonReady && !this.stale,
                false,
                true,
                true,
                this.enabled,
                this.lifecycleState.name(),
                ForgeModelStoreFormalLayout.FORMAL_LAYOUT_KNOWN,
                ForgeModelStoreFormalLayout.MODEL_RECORD_BYTES,
                MODEL_CAPACITY,
                modelDataCreated,
                this.modelDataBufferId,
                modelDataCreated ? MODEL_DATA_BYTES : 0L,
                modelColourCreated,
                this.modelColourBufferId,
                modelColourCreated ? MODEL_COLOUR_BYTES : 0L,
                atlasLayoutReady(),
                atlasCreated,
                this.atlasTextureId,
                this.fullAtlasTextureCreated,
                this.debugSmallAtlasFallback,
                this.allocationFailed,
                ForgeModelAtlasLayout.ATLAS_WIDTH,
                ForgeModelAtlasLayout.ATLAS_HEIGHT,
                this.actualAtlasWidth,
                this.actualAtlasHeight,
                ATLAS_FORMAT,
                false,
                samplerCreated,
                this.samplerId,
                this.samplerConfigured,
                true,
                this.stale,
                this.requiresRebuild,
                false,
                false,
                false,
                false,
                false,
                false,
                this.resourceReloadSeen,
                this.worldUnloadSeen,
                this.dimensionSwitchSeen,
                this.debugPipelineClearSeen,
                this.presetOffSeen,
                this.presetClearSeen,
                this.lastLifecycleEvent,
                this.staleReason,
                this.cleanupScheduled,
                this.cleanupOnRenderThread,
                this.cleanupCompleted,
                this.cleanupFailures,
                this.lastAudit.success(),
                this.lastAudit.error(),
                this.lastAudit.durationMs(),
                this.lastAudit.invalidLayout(),
                this.lastAudit.invalidBufferSize(),
                this.lastAudit.invalidAtlasState(),
                this.lastAudit.unexpectedRecordsUploaded(),
                this.lastAudit.unexpectedPixelsUploaded()
        );
    }

    ForgeFormalModelStoreAuditResult createAuditStatusSnapshot() {
        return this.lastAudit;
    }

    String dumpLayout() {
        ForgeFormalModelStoreStats status = this.createStatusSnapshot();
        return String.format(
                "Voxy formal ModelStore layout: stage=%s formalLayoutKnown=%s layoutVersion=%s modelSize=%d modelCapacity=%d modelDataBytes=%d modelColourBytes=%d atlasLayoutReady=%s modelTextureSize=%d facesPerModelX=%d facesPerModelY=%d modelGrid=%dx%d atlas=%dx%d actualAtlas=%dx%d atlasFormat=%s fullAtlasTextureCreated=%s debugSmallAtlasFallback=%s atlasPixelsUploaded=false samplerCreated=%s samplerConfigured=%s noBake=true noDraw=true realModelRecordsUploaded=false realAtlasPixelsUploaded=false formalModelStoreReady=false formalRendererReady=false fields=\"%s\"",
                status.stage(),
                status.formalLayoutKnown(),
                ForgeModelStoreFormalLayout.LAYOUT_VERSION,
                status.modelSize(),
                status.modelCapacity(),
                MODEL_DATA_BYTES,
                MODEL_COLOUR_BYTES,
                status.atlasLayoutReady(),
                ForgeModelAtlasLayout.MODEL_TEXTURE_SIZE,
                ForgeModelAtlasLayout.FACES_PER_MODEL_X,
                ForgeModelAtlasLayout.FACES_PER_MODEL_Y,
                ForgeModelAtlasLayout.MODEL_GRID_WIDTH,
                ForgeModelAtlasLayout.MODEL_GRID_HEIGHT,
                status.atlasWidth(),
                status.atlasHeight(),
                status.actualAtlasWidth(),
                status.actualAtlasHeight(),
                status.atlasFormat(),
                status.fullAtlasTextureCreated(),
                status.debugSmallAtlasFallback(),
                status.samplerCreated(),
                status.samplerConfigured(),
                ForgeModelStoreFormalLayout.fieldSummary()
        );
    }

    void markResourceReload() {
        this.markLifecycleStale("resource-reload");
        this.resourceReloadSeen = true;
    }

    void markWorldUnload() {
        this.markLifecycleStale("world-unload");
        this.worldUnloadSeen = true;
    }

    void markDimensionSwitch() {
        this.markLifecycleStale("dimension-switch");
        this.dimensionSwitchSeen = true;
    }

    void markDebugPipelineClear() {
        this.markLifecycleStale("debug-pipeline-clear");
        this.debugPipelineClearSeen = true;
    }

    void markPresetOff() {
        this.markLifecycleStale("preset-off");
        this.presetOffSeen = true;
    }

    void markPresetClear() {
        this.markLifecycleStale("preset-clear");
        this.presetClearSeen = true;
    }

    void markStale(String reason) {
        this.markLifecycleStale(reason == null || reason.isBlank() ? "stale" : reason);
    }

    void clear() {
        this.clearRuns++;
        this.enabled = false;
        this.stale = false;
        this.requiresRebuild = false;
        this.lifecycleState = ForgeFormalModelStoreLifecycleState.CLEARED;
        this.lastLifecycleEvent = "clear";
        this.staleReason = "none";
        this.resourceReloadSeen = false;
        this.worldUnloadSeen = false;
        this.dimensionSwitchSeen = false;
        this.debugPipelineClearSeen = false;
        this.presetOffSeen = false;
        this.presetClearSeen = false;
        this.auditRuns = 0L;
        this.auditFailures = 0L;
        this.lastAudit = ForgeFormalModelStoreAuditResult.failure("none", 0.0D);
        this.scheduleCleanup("clear");
    }

    private void allocateBuffers() {
        long dataPtr = 0L;
        long colourPtr = 0L;
        try {
            dataPtr = MemoryUtil.nmemCalloc(1L, MODEL_DATA_BYTES);
            colourPtr = MemoryUtil.nmemCalloc(1L, MODEL_COLOUR_BYTES);
            this.modelDataBufferId = GL45C.glCreateBuffers();
            this.modelColourBufferId = GL45C.glCreateBuffers();
            GL45C.nglNamedBufferData(this.modelDataBufferId, MODEL_DATA_BYTES, dataPtr, GL15C.GL_DYNAMIC_DRAW);
            GL45C.nglNamedBufferData(this.modelColourBufferId, MODEL_COLOUR_BYTES, colourPtr, GL15C.GL_DYNAMIC_DRAW);
        } finally {
            if (dataPtr != 0L) {
                MemoryUtil.nmemFree(dataPtr);
            }
            if (colourPtr != 0L) {
                MemoryUtil.nmemFree(colourPtr);
            }
        }
    }

    private void allocateAtlasTexture() {
        this.fullAtlasTextureCreated = false;
        this.debugSmallAtlasFallback = false;
        this.allocationFailed = false;
        this.actualAtlasWidth = 0;
        this.actualAtlasHeight = 0;

        clearGlErrors();
        int maxTextureSize = GL11C.glGetInteger(GL11C.GL_MAX_TEXTURE_SIZE);
        if (maxTextureSize >= ForgeModelAtlasLayout.ATLAS_WIDTH && maxTextureSize >= ForgeModelAtlasLayout.ATLAS_HEIGHT) {
            if (this.tryCreateTexture(ForgeModelAtlasLayout.ATLAS_WIDTH, ForgeModelAtlasLayout.ATLAS_HEIGHT, true)) {
                return;
            }
        } else {
            this.lastAllocationError = "full-atlas-exceeds-GL_MAX_TEXTURE_SIZE-" + maxTextureSize;
        }

        this.debugSmallAtlasFallback = true;
        if (this.tryCreateTexture(DEBUG_SMALL_ATLAS_WIDTH, DEBUG_SMALL_ATLAS_HEIGHT, false)) {
            return;
        }

        this.allocationFailed = true;
        this.lifecycleState = ForgeFormalModelStoreLifecycleState.ALLOCATION_FAILED;
        if ("none".equals(this.lastAllocationError)) {
            this.lastAllocationError = "atlas-texture-create-failed";
        }
    }

    private boolean tryCreateTexture(int width, int height, boolean fullAtlas) {
        clearGlErrors();
        int oldTextureBinding = GL11C.glGetInteger(GL11C.GL_TEXTURE_BINDING_2D);
        int unpackBufferBinding = GL11C.glGetInteger(GL21C.GL_PIXEL_UNPACK_BUFFER_BINDING);
        int texture = GL11C.glGenTextures();
        if (texture == 0) {
            this.lastAllocationError = "glGenTextures-returned-zero";
            return false;
        }
        int error;
        try {
            GL15C.glBindBuffer(GL21C.GL_PIXEL_UNPACK_BUFFER, 0);
            GL11C.glBindTexture(GL11C.GL_TEXTURE_2D, texture);
            GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_MIN_FILTER, GL11C.GL_NEAREST);
            GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_MAG_FILTER, GL11C.GL_NEAREST);
            GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_WRAP_S, GL12C.GL_CLAMP_TO_EDGE);
            GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_WRAP_T, GL12C.GL_CLAMP_TO_EDGE);
            GL11C.glTexImage2D(GL11C.GL_TEXTURE_2D, 0, GL30C.GL_RGBA8, width, height, 0, GL11C.GL_RGBA, GL11C.GL_UNSIGNED_BYTE, (ByteBuffer) null);
            error = GL11C.glGetError();
        } finally {
            GL11C.glBindTexture(GL11C.GL_TEXTURE_2D, oldTextureBinding);
            GL15C.glBindBuffer(GL21C.GL_PIXEL_UNPACK_BUFFER, unpackBufferBinding);
        }
        if (error != GL11C.GL_NO_ERROR) {
            GL11C.glDeleteTextures(texture);
            this.lastAllocationError = "glTexImage2D-" + glErrorName(error);
            return false;
        }

        this.atlasTextureId = texture;
        this.actualAtlasWidth = width;
        this.actualAtlasHeight = height;
        this.fullAtlasTextureCreated = fullAtlas;
        this.debugSmallAtlasFallback = !fullAtlas;
        this.lastAllocationError = "none";
        return true;
    }

    private void allocateSampler() {
        this.samplerId = GL33C.glGenSamplers();
        if (this.samplerId == 0) {
            this.samplerConfigured = false;
            this.lastAllocationError = "glGenSamplers-returned-zero";
            return;
        }
        GL33C.glSamplerParameteri(this.samplerId, GL11C.GL_TEXTURE_MIN_FILTER, GL11C.GL_NEAREST);
        GL33C.glSamplerParameteri(this.samplerId, GL11C.GL_TEXTURE_MAG_FILTER, GL11C.GL_NEAREST);
        GL33C.glSamplerParameteri(this.samplerId, GL11C.GL_TEXTURE_WRAP_S, GL12C.GL_CLAMP_TO_EDGE);
        GL33C.glSamplerParameteri(this.samplerId, GL11C.GL_TEXTURE_WRAP_T, GL12C.GL_CLAMP_TO_EDGE);
        this.samplerConfigured = GL11C.glGetError() == GL11C.GL_NO_ERROR;
        if (!this.samplerConfigured) {
            this.lastAllocationError = "sampler-config-GL-error";
        }
    }

    private ForgeFormalModelStoreAuditResult auditOnRenderThread(long startNanos) {
        int invalidLayout = 0;
        int invalidBufferSize = 0;
        int invalidAtlasState = 0;

        if (!ForgeModelStoreFormalLayout.FORMAL_LAYOUT_KNOWN
                || ForgeModelStoreFormalLayout.MODEL_RECORD_BYTES != 64
                || MODEL_CAPACITY != 65536
                || MODEL_DATA_BYTES != 4194304L
                || MODEL_COLOUR_BYTES != 262144L) {
            invalidLayout++;
        }
        if (!atlasLayoutReady()) {
            invalidLayout++;
        }
        if (this.modelDataBufferId == 0 || this.modelColourBufferId == 0) {
            invalidBufferSize++;
        }
        if (this.atlasTextureId == 0 && !this.allocationFailed) {
            invalidAtlasState++;
        }
        if (this.atlasTextureId != 0) {
            boolean fullAtlas = this.fullAtlasTextureCreated
                    && this.actualAtlasWidth == ForgeModelAtlasLayout.ATLAS_WIDTH
                    && this.actualAtlasHeight == ForgeModelAtlasLayout.ATLAS_HEIGHT;
            boolean fallbackAtlas = this.debugSmallAtlasFallback
                    && this.actualAtlasWidth == DEBUG_SMALL_ATLAS_WIDTH
                    && this.actualAtlasHeight == DEBUG_SMALL_ATLAS_HEIGHT;
            if (!fullAtlas && !fallbackAtlas) {
                invalidAtlasState++;
            }
        }
        if (this.samplerId == 0 || !this.samplerConfigured) {
            invalidAtlasState++;
        }

        boolean modelDataZero = this.modelDataBufferId != 0 && this.sampleBufferIsZero(this.modelDataBufferId, MODEL_DATA_BYTES);
        boolean modelColourZero = this.modelColourBufferId != 0 && this.sampleBufferIsZero(this.modelColourBufferId, MODEL_COLOUR_BYTES);
        if (this.modelDataBufferId != 0 && !modelDataZero) {
            invalidBufferSize++;
        }
        if (this.modelColourBufferId != 0 && !modelColourZero) {
            invalidBufferSize++;
        }

        boolean success = invalidLayout == 0
                && invalidBufferSize == 0
                && invalidAtlasState == 0
                && !this.stale;
        return new ForgeFormalModelStoreAuditResult(
                success,
                success ? "none" : "formal-model-store-skeleton-audit-failed",
                elapsedMs(startNanos),
                invalidLayout,
                invalidBufferSize,
                invalidAtlasState,
                false,
                false,
                modelDataZero,
                modelColourZero
        );
    }

    private boolean sampleBufferIsZero(int bufferId, long bytes) {
        int sampleBytes = (int) Math.min(64L, bytes);
        long ptr = MemoryUtil.nmemAlloc(sampleBytes);
        try {
            GL45C.nglGetNamedBufferSubData(bufferId, 0L, sampleBytes, ptr);
            for (int i = 0; i < sampleBytes; i++) {
                if (MemoryUtil.memGetByte(ptr + i) != 0) {
                    return false;
                }
            }
            if (bytes <= sampleBytes) {
                return true;
            }
            GL45C.nglGetNamedBufferSubData(bufferId, bytes - sampleBytes, sampleBytes, ptr);
            for (int i = 0; i < sampleBytes; i++) {
                if (MemoryUtil.memGetByte(ptr + i) != 0) {
                    return false;
                }
            }
            return true;
        } finally {
            MemoryUtil.nmemFree(ptr);
        }
    }

    private void markLifecycleStale(String event) {
        this.enabled = false;
        this.stale = true;
        this.requiresRebuild = true;
        this.lifecycleState = ForgeFormalModelStoreLifecycleState.STALE;
        this.lastLifecycleEvent = event == null || event.isBlank() ? "stale" : event.replace(' ', '-');
        this.staleReason = this.lastLifecycleEvent;
        this.scheduleCleanup(this.lastLifecycleEvent);
    }

    private void failBuild(String error, long startNanos) {
        this.lastBuildError = error == null || error.isBlank() ? "unknown" : error;
        this.lastAllocationError = this.lastBuildError;
        this.lastBuildDurationMs = elapsedMs(startNanos);
        this.enabled = false;
        this.stale = false;
        this.requiresRebuild = true;
        this.allocationFailed = true;
        this.lifecycleState = ForgeFormalModelStoreLifecycleState.ALLOCATION_FAILED;
        this.lastLifecycleEvent = "build-failed:" + this.lastBuildError;
    }

    private void scheduleCleanup(String reason) {
        this.cleanupScheduled = !RenderSystem.isOnRenderThread();
        this.cleanupOnRenderThread = RenderSystem.isOnRenderThread();
        this.cleanupCompleted = false;
        if (RenderSystem.isOnRenderThread()) {
            this.closeOnRenderThread(true);
        } else {
            RenderSystem.recordRenderCall(() -> this.closeOnRenderThread(true));
        }
        this.lastBuildError = reason == null || reason.isBlank() ? "none" : reason.replace(' ', '-');
    }

    private void closeOnRenderThread(boolean markCleanupCompleted) {
        this.cleanupOnRenderThread = RenderSystem.isOnRenderThread();
        try {
            if (this.modelDataBufferId != 0) {
                GL15C.glDeleteBuffers(this.modelDataBufferId);
            }
            if (this.modelColourBufferId != 0) {
                GL15C.glDeleteBuffers(this.modelColourBufferId);
            }
            if (this.atlasTextureId != 0) {
                GL11C.glDeleteTextures(this.atlasTextureId);
            }
            if (this.samplerId != 0) {
                GL33C.glDeleteSamplers(this.samplerId);
            }
            if (markCleanupCompleted) {
                this.cleanupCompleted = true;
            }
        } catch (RuntimeException e) {
            this.cleanupFailures++;
            this.cleanupCompleted = false;
            VoxyForge.LOGGER.error("Failed to clean formal ModelStore resources.", e);
        } finally {
            this.modelDataBufferId = 0;
            this.modelColourBufferId = 0;
            this.atlasTextureId = 0;
            this.samplerId = 0;
            this.actualAtlasWidth = 0;
            this.actualAtlasHeight = 0;
            this.fullAtlasTextureCreated = false;
            this.debugSmallAtlasFallback = false;
            this.samplerConfigured = false;
        }
    }

    private static boolean atlasLayoutReady() {
        return ForgeModelAtlasLayout.MODEL_TEXTURE_SIZE == 16
                && ForgeModelAtlasLayout.FACES_PER_MODEL_X == 3
                && ForgeModelAtlasLayout.FACES_PER_MODEL_Y == 2
                && ForgeModelAtlasLayout.MODEL_GRID_WIDTH == 256
                && ForgeModelAtlasLayout.MODEL_GRID_HEIGHT == 256
                && ForgeModelAtlasLayout.ATLAS_WIDTH == 12288
                && ForgeModelAtlasLayout.ATLAS_HEIGHT == 8192;
    }

    private static void clearGlErrors() {
        while (GL11C.glGetError() != GL11C.GL_NO_ERROR) {
            // drain
        }
    }

    private static String glErrorName(int error) {
        return switch (error) {
            case GL11C.GL_NO_ERROR -> "GL_NO_ERROR";
            case GL11C.GL_INVALID_ENUM -> "GL_INVALID_ENUM";
            case GL11C.GL_INVALID_VALUE -> "GL_INVALID_VALUE";
            case GL11C.GL_INVALID_OPERATION -> "GL_INVALID_OPERATION";
            case GL11C.GL_OUT_OF_MEMORY -> "GL_OUT_OF_MEMORY";
            default -> "GL_ERROR_" + error;
        };
    }

    private static double elapsedMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000.0D;
    }
}
