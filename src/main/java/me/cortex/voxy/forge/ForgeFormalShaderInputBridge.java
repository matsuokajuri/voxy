package me.cortex.voxy.forge;

import com.mojang.blaze3d.systems.RenderSystem;
import org.lwjgl.system.MemoryUtil;

import java.util.List;

import static org.lwjgl.opengl.GL15C.GL_DYNAMIC_DRAW;
import static org.lwjgl.opengl.GL15C.glDeleteBuffers;
import static org.lwjgl.opengl.GL45C.glCreateBuffers;
import static org.lwjgl.opengl.GL45C.nglGetNamedBufferSubData;
import static org.lwjgl.opengl.GL45C.nglNamedBufferData;

final class ForgeFormalShaderInputBridge {
    static final String STAGE = "G6_20_FORMAL_SHADER_INPUT_BRIDGE_SAMPLE_SET";
    static final int MODEL_DATA_BINDING_INDEX = 3;
    static final int MODEL_COLOUR_BINDING_INDEX = 4;
    static final int MODEL_VALIDITY_BINDING_INDEX = 6;
    static final int ATLAS_TEXTURE_UNIT = 0;
    static final int MAX_SUPPORTED_MODEL_ID = 0xFFFF;

    private final ForgeVoxyInstance instance;
    private long buildRuns;
    private long clearRuns;
    private long auditRuns;
    private long auditFailures;
    private String lastBuildError = "none";
    private double lastBuildDurationMs;
    private ForgeFormalShaderInputAuditResult lastAudit = ForgeFormalShaderInputAuditResult.failure("none", 0.0D);
    private int modelDataBufferId;
    private int modelColourBufferId;
    private int modelValidityBufferId;
    private int atlasTextureObjectId;
    private boolean fullAtlasTextureCreated;
    private int atlasWidth;
    private int atlasHeight;
    private int maxModelId = -1;
    private int[] sampleModelIds = new int[0];
    private boolean stale;
    private boolean lastReloadInvalidatedFormalShaderInputBridge;

    ForgeFormalShaderInputBridge(ForgeVoxyInstance instance) {
        this.instance = instance;
    }

    ForgeFormalShaderInputStats build() {
        this.buildRuns++;
        long start = System.nanoTime();
        if (!RenderSystem.isOnRenderThread()) {
            this.failBuild("not-render-thread", start);
            return this.createStatusSnapshot();
        }
        try {
            ForgeModelSampleSetStats sampleSetStatus = this.instance.getModelSampleSet().createStatusSnapshot();
            if (!sampleSetStatus.sampleSetReady()) {
                sampleSetStatus = this.instance.getModelSampleSet().build();
            }
            ForgeModelAtlasSampleSetUploadStats atlasStatus = this.instance.getModelAtlasSampleSetUploader().createStatusSnapshot();
            if (!atlasStatus.sampleSetAtlasUploadReady()) {
                atlasStatus = this.instance.getModelAtlasSampleSetUploader().uploadSampleSet();
            }
            List<ForgeRealModelStoreSampleRecord> records = this.instance.getModelSampleSet().records();
            if (!sampleSetStatus.sampleSetReady() || records.isEmpty()) {
                this.failBuild("model-sample-set-missing", start);
                return this.createStatusSnapshot();
            }
            if (!atlasStatus.sampleSetAtlasUploadReady() || atlasStatus.atlasTextureObjectId() == 0) {
                this.failBuild("sample-set-atlas-missing", start);
                return this.createStatusSnapshot();
            }

            int maxId = records.stream().mapToInt(ForgeRealModelStoreSampleRecord::modelId).max().orElse(-1);
            if (maxId < 0 || maxId > MAX_SUPPORTED_MODEL_ID) {
                this.failBuild("unsupported-model-id-" + maxId, start);
                return this.createStatusSnapshot();
            }
            if (!this.uploadSparseBuffers(records, maxId)) {
                this.failBuild(this.lastBuildError, start);
                return this.createStatusSnapshot();
            }

            this.maxModelId = maxId;
            this.sampleModelIds = records.stream().mapToInt(ForgeRealModelStoreSampleRecord::modelId).toArray();
            this.atlasTextureObjectId = atlasStatus.atlasTextureObjectId();
            this.fullAtlasTextureCreated = atlasStatus.fullAtlasTextureCreated();
            this.atlasWidth = atlasStatus.actualTextureWidth();
            this.atlasHeight = atlasStatus.actualTextureHeight();
            this.stale = false;
            this.lastReloadInvalidatedFormalShaderInputBridge = false;
            this.lastBuildError = "none";
            this.lastBuildDurationMs = elapsedMs(start);
            this.lastAudit = ForgeFormalShaderInputAuditResult.failure("none", 0.0D);
            return this.createStatusSnapshot();
        } catch (RuntimeException e) {
            this.closeOnRenderThread();
            this.failBuild(e.getClass().getSimpleName() + ": " + e.getMessage(), start);
            return this.createStatusSnapshot();
        }
    }

    ForgeFormalShaderInputAuditResult audit() {
        this.auditRuns++;
        long start = System.nanoTime();
        ForgeFormalShaderInputAuditResult result;
        if (!RenderSystem.isOnRenderThread()) {
            result = ForgeFormalShaderInputAuditResult.failure("not-render-thread", elapsedMs(start));
        } else if (!this.isReady()) {
            result = ForgeFormalShaderInputAuditResult.failure("formal-shader-input-bridge-not-ready", elapsedMs(start));
        } else {
            try {
                result = this.compareReadback(start);
            } catch (RuntimeException e) {
                result = ForgeFormalShaderInputAuditResult.failure(e.getClass().getSimpleName() + ": " + e.getMessage(), elapsedMs(start));
            }
        }
        this.lastAudit = result;
        if (!result.success()) {
            this.auditFailures++;
        }
        return result;
    }

    ForgeFormalShaderInputStats createStatusSnapshot() {
        boolean ready = this.isReady();
        ForgeModelAtlasSampleSetUploadStats atlasStatus = this.instance.getModelAtlasSampleSetUploader().createStatusSnapshot();
        boolean atlasStillValid = ready
                && atlasStatus.sampleSetAtlasUploadReady()
                && atlasStatus.atlasTextureObjectId() == this.atlasTextureObjectId
                && atlasStatus.atlasPixelsUploaded();
        return new ForgeFormalShaderInputStats(
                STAGE,
                this.buildRuns,
                this.clearRuns,
                this.auditRuns,
                this.auditFailures,
                this.lastBuildError,
                this.lastBuildDurationMs,
                ready && atlasStillValid,
                this.modelDataBufferId != 0 && this.maxModelId >= 0,
                this.modelColourBufferId != 0 && this.maxModelId >= 0,
                atlasStillValid,
                this.modelDataBufferId != 0,
                this.modelColourBufferId != 0,
                this.atlasTextureObjectId != 0,
                true,
                false,
                false,
                false,
                this.sampleModelIds.length,
                this.sampleModelIdsString(),
                this.maxModelId,
                this.modelDataBufferId,
                this.modelColourBufferId,
                this.modelValidityBufferId,
                this.atlasTextureObjectId,
                this.fullAtlasTextureCreated,
                this.atlasWidth,
                this.atlasHeight,
                MODEL_DATA_BINDING_INDEX,
                MODEL_COLOUR_BINDING_INDEX,
                MODEL_VALIDITY_BINDING_INDEX,
                ATLAS_TEXTURE_UNIT,
                true,
                this.stale || (ready && !atlasStillValid),
                this.lastReloadInvalidatedFormalShaderInputBridge,
                this.lastAudit.success(),
                this.lastAudit.error(),
                this.lastAudit.durationMs(),
                this.lastAudit.modelDataBufferMatch(),
                this.lastAudit.modelColourBufferMatch(),
                this.lastAudit.atlasUploadStillValid(),
                this.lastAudit.invalidModelRecords(),
                this.lastAudit.invalidColourRecords(),
                this.lastAudit.missingAtlasTiles()
        );
    }

    boolean isReady() {
        return this.modelDataBufferId != 0
                && this.modelColourBufferId != 0
                && this.modelValidityBufferId != 0
                && this.atlasTextureObjectId != 0
                && this.maxModelId >= 0
                && this.sampleModelIds.length > 0
                && !this.stale;
    }

    int modelDataBufferId() {
        return this.modelDataBufferId;
    }

    int modelColourBufferId() {
        return this.modelColourBufferId;
    }

    int modelValidityBufferId() {
        return this.modelValidityBufferId;
    }

    int atlasTextureObjectId() {
        return this.atlasTextureObjectId;
    }

    boolean fullAtlasTextureCreated() {
        return this.fullAtlasTextureCreated;
    }

    int atlasWidth() {
        return this.atlasWidth;
    }

    int atlasHeight() {
        return this.atlasHeight;
    }

    int maxModelId() {
        return this.maxModelId;
    }

    int[] sampleModelIds() {
        int[] ids = new int[this.sampleModelIds.length];
        System.arraycopy(this.sampleModelIds, 0, ids, 0, ids.length);
        return ids;
    }

    void markStale(String reason) {
        this.close();
        this.stale = true;
        this.lastReloadInvalidatedFormalShaderInputBridge = true;
        this.lastBuildError = reason == null || reason.isBlank() ? "stale" : reason;
        this.lastAudit = ForgeFormalShaderInputAuditResult.failure(this.lastBuildError, 0.0D);
    }

    void clear() {
        this.clearRuns++;
        this.close();
        this.lastBuildError = "none";
        this.lastBuildDurationMs = 0.0D;
        this.auditRuns = 0L;
        this.auditFailures = 0L;
        this.lastAudit = ForgeFormalShaderInputAuditResult.failure("none", 0.0D);
        this.stale = false;
        this.lastReloadInvalidatedFormalShaderInputBridge = false;
    }

    void close() {
        if (RenderSystem.isOnRenderThread()) {
            this.closeOnRenderThread();
        } else {
            RenderSystem.recordRenderCall(this::closeOnRenderThread);
        }
    }

    private boolean uploadSparseBuffers(List<ForgeRealModelStoreSampleRecord> records, int maxId) {
        long modelWords = (long) (maxId + 1) * ForgeRealModelStoreSampleRecord.WORDS;
        long dataBytes = modelWords * Integer.BYTES;
        long colourBytes = (long) (maxId + 1) * Integer.BYTES;
        long validityBytes = colourBytes;
        long dataPtr = 0L;
        long colourPtr = 0L;
        long validityPtr = 0L;
        try {
            dataPtr = MemoryUtil.nmemCalloc(modelWords, Integer.BYTES);
            colourPtr = MemoryUtil.nmemCalloc(maxId + 1L, Integer.BYTES);
            validityPtr = MemoryUtil.nmemCalloc(maxId + 1L, Integer.BYTES);
            for (long i = 0; i <= maxId; i++) {
                MemoryUtil.memPutInt(colourPtr + i * Integer.BYTES, -1);
            }
            for (ForgeRealModelStoreSampleRecord record : records) {
                int modelId = record.modelId();
                int[] words = record.toWords();
                long wordBase = (long) modelId * ForgeRealModelStoreSampleRecord.WORDS;
                for (int i = 0; i < words.length; i++) {
                    MemoryUtil.memPutInt(dataPtr + (wordBase + i) * Integer.BYTES, words[i]);
                }
                MemoryUtil.memPutInt(colourPtr + (long) modelId * Integer.BYTES, record.modelColour());
                MemoryUtil.memPutInt(validityPtr + (long) modelId * Integer.BYTES, 1);
            }
            if (this.modelDataBufferId == 0) {
                this.modelDataBufferId = glCreateBuffers();
            }
            if (this.modelColourBufferId == 0) {
                this.modelColourBufferId = glCreateBuffers();
            }
            if (this.modelValidityBufferId == 0) {
                this.modelValidityBufferId = glCreateBuffers();
            }
            nglNamedBufferData(this.modelDataBufferId, dataBytes, dataPtr, GL_DYNAMIC_DRAW);
            nglNamedBufferData(this.modelColourBufferId, colourBytes, colourPtr, GL_DYNAMIC_DRAW);
            nglNamedBufferData(this.modelValidityBufferId, validityBytes, validityPtr, GL_DYNAMIC_DRAW);
            return true;
        } catch (RuntimeException e) {
            this.lastBuildError = e.getClass().getSimpleName() + ": " + e.getMessage();
            this.closeOnRenderThread();
            return false;
        } finally {
            if (dataPtr != 0L) {
                MemoryUtil.nmemFree(dataPtr);
            }
            if (colourPtr != 0L) {
                MemoryUtil.nmemFree(colourPtr);
            }
            if (validityPtr != 0L) {
                MemoryUtil.nmemFree(validityPtr);
            }
        }
    }

    private ForgeFormalShaderInputAuditResult compareReadback(long start) {
        List<ForgeRealModelStoreSampleRecord> records = this.instance.getModelSampleSet().records();
        int modelWordCount = (this.maxModelId + 1) * ForgeRealModelStoreSampleRecord.WORDS;
        int colourCount = this.maxModelId + 1;
        int[] modelWords = this.readbackInts(this.modelDataBufferId, (long) modelWordCount * Integer.BYTES, modelWordCount);
        int[] colours = this.readbackInts(this.modelColourBufferId, (long) colourCount * Integer.BYTES, colourCount);
        int[] validity = this.readbackInts(this.modelValidityBufferId, (long) colourCount * Integer.BYTES, colourCount);
        int invalidModels = 0;
        int invalidColours = 0;
        for (ForgeRealModelStoreSampleRecord record : records) {
            int modelId = record.modelId();
            if (modelId < 0 || modelId > this.maxModelId || validity[modelId] != 1
                    || !record.matchesWords(modelWords, modelId * ForgeRealModelStoreSampleRecord.WORDS)) {
                invalidModels++;
            }
            if (modelId < 0 || modelId >= colours.length || colours[modelId] != record.modelColour()) {
                invalidColours++;
            }
        }
        ForgeModelAtlasSampleSetUploadStats atlasStatus = this.instance.getModelAtlasSampleSetUploader().createStatusSnapshot();
        boolean atlasValid = atlasStatus.sampleSetAtlasUploadReady()
                && atlasStatus.atlasTextureObjectId() == this.atlasTextureObjectId
                && atlasStatus.atlasPixelsUploaded();
        int missingAtlasTiles = atlasValid ? 0 : records.size() * ForgeModelAtlasLayout.FACE_COUNT;
        boolean success = invalidModels == 0 && invalidColours == 0 && atlasValid && !records.isEmpty();
        return new ForgeFormalShaderInputAuditResult(
                success,
                success ? "none" : "formal-shader-input-bridge-mismatch",
                elapsedMs(start),
                invalidModels == 0,
                invalidColours == 0,
                atlasValid,
                records.size(),
                invalidModels,
                invalidColours,
                missingAtlasTiles
        );
    }

    private int[] readbackInts(int bufferId, long bytes, int count) {
        int[] values = new int[count];
        long ptr = MemoryUtil.nmemAlloc(bytes);
        try {
            nglGetNamedBufferSubData(bufferId, 0L, bytes, ptr);
            for (int i = 0; i < values.length; i++) {
                values[i] = MemoryUtil.memGetInt(ptr + i * (long) Integer.BYTES);
            }
            return values;
        } finally {
            MemoryUtil.nmemFree(ptr);
        }
    }

    private void closeOnRenderThread() {
        if (this.modelDataBufferId != 0) {
            glDeleteBuffers(this.modelDataBufferId);
        }
        if (this.modelColourBufferId != 0) {
            glDeleteBuffers(this.modelColourBufferId);
        }
        if (this.modelValidityBufferId != 0) {
            glDeleteBuffers(this.modelValidityBufferId);
        }
        this.modelDataBufferId = 0;
        this.modelColourBufferId = 0;
        this.modelValidityBufferId = 0;
        this.atlasTextureObjectId = 0;
        this.fullAtlasTextureCreated = false;
        this.atlasWidth = 0;
        this.atlasHeight = 0;
        this.maxModelId = -1;
        this.sampleModelIds = new int[0];
    }

    private void failBuild(String error, long start) {
        this.lastBuildError = error == null || error.isBlank() ? "build-failed" : error;
        this.lastBuildDurationMs = elapsedMs(start);
    }

    private String sampleModelIdsString() {
        if (this.sampleModelIds.length == 0) {
            return "none";
        }
        StringBuilder builder = new StringBuilder();
        for (int modelId : this.sampleModelIds) {
            if (builder.length() > 0) {
                builder.append(',');
            }
            builder.append(modelId);
        }
        return builder.toString();
    }

    private static double elapsedMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000.0D;
    }
}
