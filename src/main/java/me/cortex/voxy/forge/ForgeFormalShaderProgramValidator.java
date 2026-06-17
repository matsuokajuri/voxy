package me.cortex.voxy.forge;

import com.mojang.blaze3d.systems.RenderSystem;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL13C;
import org.lwjgl.opengl.GL15C;
import org.lwjgl.opengl.GL20C;
import org.lwjgl.opengl.GL30C;
import org.lwjgl.opengl.GL33C;
import org.lwjgl.opengl.GL43C;
import org.lwjgl.opengl.GL45C;
import org.lwjgl.system.MemoryUtil;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

final class ForgeFormalShaderProgramValidator {
    static final String STAGE = "J2_FORMAL_SHADER_PROGRAM_VALIDATION_PROTOTYPE";
    private static final int VALIDATION_RESULT_BINDING_INDEX = 5;
    private static final int VALIDATION_MODEL_ID_BINDING_INDEX = 6;
    private static final int RESULT_WORDS_PER_MODEL = 6;
    private static final int MAX_VALIDATED_MODELS = 4;

    private static final String COMPUTE_SOURCE = """
            #version 430 core
            layout(local_size_x = 1, local_size_y = 1, local_size_z = 1) in;

            layout(std430, binding = 3) readonly buffer ModelData {
                uint modelData[];
            };

            layout(std430, binding = 4) readonly buffer ModelColour {
                uint modelColour[];
            };

            layout(std430, binding = 5) writeonly buffer ValidationOut {
                uint validationOut[];
            };

            layout(std430, binding = 6) readonly buffer ValidationIds {
                uint validationIds[];
            };

            layout(binding = 0) uniform sampler2D blockModelAtlas;

            const uint WORDS_PER_MODEL = 16u;
            const uint MODEL_TEXTURE_SIZE = 16u;
            const uint FACES_PER_MODEL_X = 3u;
            const uint FACES_PER_MODEL_Y = 2u;

            void main() {
                uint index = gl_GlobalInvocationID.x;
                uint modelId = validationIds[index];
                uint wordBase = modelId * WORDS_PER_MODEL;
                uint face0 = modelData[wordBase];
                uint flagsA = modelData[wordBase + 6u];
                uint colourTint = modelData[wordBase + 7u];
                uint colour = modelColour[modelId];
                uint baseX = (modelId & 255u) * MODEL_TEXTURE_SIZE * FACES_PER_MODEL_X;
                uint baseY = ((modelId >> 8u) & 255u) * MODEL_TEXTURE_SIZE * FACES_PER_MODEL_Y;
                vec4 texel = texelFetch(blockModelAtlas, ivec2(int(baseX), int(baseY)), 0);
                uint r = uint(round(clamp(texel.r, 0.0, 1.0) * 255.0));
                uint g = uint(round(clamp(texel.g, 0.0, 1.0) * 255.0));
                uint b = uint(round(clamp(texel.b, 0.0, 1.0) * 255.0));
                uint a = uint(round(clamp(texel.a, 0.0, 1.0) * 255.0));
                uint pixel = r | (g << 8u) | (b << 16u) | (a << 24u);
                uint outBase = index * 6u;
                validationOut[outBase] = modelId;
                validationOut[outBase + 1u] = face0;
                validationOut[outBase + 2u] = flagsA;
                validationOut[outBase + 3u] = colour;
                validationOut[outBase + 4u] = pixel;
                validationOut[outBase + 5u] = colourTint;
            }
            """;

    private final ForgeVoxyInstance instance;
    private long buildRuns;
    private long auditRuns;
    private long clearRuns;
    private long auditFailures;
    private boolean validatorReady;
    private boolean validationReady;
    private boolean validationShaderCompileAttempted;
    private boolean validationShaderCompileOk;
    private boolean validationProgramLinkOk;
    private int validationProgramId;
    private int validationResultBufferId;
    private int validationModelIdBufferId;
    private boolean formalShaderProgramBoundForValidation;
    private boolean modelDataBindingOk;
    private boolean modelColourBindingOk;
    private boolean atlasTextureBindingOk;
    private boolean samplerBindingOk;
    private boolean gpuValidationAttempted;
    private boolean gpuValidationOk;
    private String gpuValidationSkippedReason = "not-run";
    private boolean validationReadbackOk;
    private int validationFailureCount;
    private int safeSetModelCount;
    private int validatedModelCount;
    private String validatedModelIds = "";
    private boolean stale;
    private boolean requiresRebuild;
    private String lifecycleState = "UNINITIALIZED";
    private String lastLifecycleEvent = "initialized";
    private String staleReason = "none";
    private String lastGlError = "none";
    private String lastFailureReason = "none";
    private ForgeFormalShaderProgramAuditResult lastAudit =
            ForgeFormalShaderProgramAuditResult.failure("none", 0.0D);

    ForgeFormalShaderProgramValidator(ForgeVoxyInstance instance) {
        this.instance = instance;
    }

    ForgeFormalShaderProgramStats build() {
        this.buildRuns++;
        this.lifecycleState = "BUILDING";
        this.lastLifecycleEvent = "build";
        this.staleReason = "none";
        this.lastFailureReason = "none";
        this.resetValidationFlags();

        if (!RenderSystem.isOnRenderThread()) {
            this.fail("not-render-thread");
            return this.createStatusSnapshot();
        }

        if (!GL.getCapabilities().OpenGL43) {
            this.validationShaderCompileAttempted = false;
            this.gpuValidationSkippedReason = "opengl-4.3-compute-unavailable";
            this.fail(this.gpuValidationSkippedReason);
            return this.createStatusSnapshot();
        }

        ForgeFormalShaderInputConsumerStats inputStatus = this.instance.getFormalShaderInputConsumer().build();
        ForgeMultiBlockFormalBakeUploadStats multi = this.instance.getMultiBlockFormalBakeUpload().createStatusSnapshot();
        ForgeFormalModelStoreStats store = this.instance.getFormalModelStore().createStatusSnapshot();
        List<ForgeFormalUploadedModelSummary> summaries = this.instance.getMultiBlockFormalBakeUpload().uploadedModelSummaries();
        this.safeSetModelCount = summaries.size();
        boolean resourcesReady = formalResourcesReady(store);
        boolean bindingCompatible = inputStatus.bindingLayoutCompatible() && inputStatus.blockModelRecordLayoutCompatible();
        boolean formalIds = inputStatus.usesFormalModelIds()
                && !inputStatus.usesPlaceholderModelIds()
                && !inputStatus.sampleSetModelIdsUsed()
                && !inputStatus.sampleSetBridgeUsedAsFormalSource();

        if (!inputStatus.formalShaderInputConsumerReady()
                || !multi.multiBlockFormalUploadAuditReady()
                || !resourcesReady
                || !bindingCompatible
                || !formalIds
                || summaries.isEmpty()) {
            this.fail(explainFailure(inputStatus, multi, store, resourcesReady, bindingCompatible, formalIds, summaries.isEmpty()));
            this.audit();
            this.instance.getFormalRendererManager().checkReadiness("j2-formal-shader-program-validator-build-failed");
            return this.createStatusSnapshot();
        }

        try {
            this.closeOwnedResourcesOnRenderThread(false);
            this.validationShaderCompileAttempted = true;
            this.validationProgramId = this.compileProgram();
            this.validationShaderCompileOk = true;
            this.validationProgramLinkOk = true;
            this.runGpuValidation(store, summaries);
            if (this.gpuValidationOk && this.validationReadbackOk) {
                this.stale = false;
                this.requiresRebuild = false;
                this.staleReason = "none";
            }
        } catch (RuntimeException e) {
            this.fail(e.getClass().getSimpleName() + ": " + e.getMessage());
        }

        this.validatorReady = this.validationShaderCompileAttempted
                && this.validationShaderCompileOk
                && this.validationProgramLinkOk
                && resourcesReady
                && bindingCompatible
                && formalIds
                && this.formalShaderProgramBoundForValidation
                && this.modelDataBindingOk
                && this.modelColourBindingOk
                && this.atlasTextureBindingOk
                && this.samplerBindingOk
                && !this.stale;
        this.validationReady = this.validatorReady
                && this.gpuValidationAttempted
                && this.gpuValidationOk
                && this.validationReadbackOk
                && this.validationFailureCount == 0;
        if (this.validationReady) {
            this.lifecycleState = "BUILT";
            this.lastFailureReason = "none";
        } else if ("none".equals(this.lastFailureReason)) {
            this.fail("validation-program-build-incomplete");
        }
        this.audit();
        this.instance.getFormalRendererManager().checkReadiness("j2-formal-shader-program-validator-build");
        return this.createStatusSnapshot();
    }

    ForgeFormalShaderProgramAuditResult audit() {
        this.auditRuns++;
        long start = System.nanoTime();
        try {
            this.lastAudit = this.auditInternal(start);
        } catch (RuntimeException e) {
            this.lastAudit = ForgeFormalShaderProgramAuditResult.failure(e.getClass().getSimpleName() + ": " + e.getMessage(), elapsedMs(start));
        }
        if (!this.lastAudit.success()) {
            this.auditFailures++;
            this.lastFailureReason = this.lastAudit.error();
        }
        return this.lastAudit;
    }

    ForgeFormalShaderProgramAuditResult createAuditStatusSnapshot() {
        return this.lastAudit;
    }

    ForgeFormalShaderProgramStats createStatusSnapshot() {
        ForgeFormalShaderInputConsumerStats input = this.instance.getFormalShaderInputConsumer().createStatusSnapshot();
        ForgeFormalModelStoreStats store = this.instance.getFormalModelStore().createStatusSnapshot();
        ForgeFormalModelBakeryLifecycleStats lifecycle = this.instance.getFormalModelBakeryLifecycle().createStatusSnapshot();
        ForgeMultiBlockFormalBakeUploadStats multi = this.instance.getMultiBlockFormalBakeUpload().createStatusSnapshot();
        boolean resourcesReady = formalResourcesReady(store);
        boolean formalIds = input.usesFormalModelIds()
                && !input.usesPlaceholderModelIds()
                && !input.sampleSetModelIdsUsed()
                && !input.sampleSetBridgeUsedAsFormalSource();
        boolean statusValidatorReady = this.validatorReady
                && !this.stale
                && resourcesReady
                && input.formalShaderInputConsumerReady()
                && input.bindingLayoutCompatible()
                && input.blockModelRecordLayoutCompatible()
                && formalIds;
        boolean statusValidationReady = statusValidatorReady
                && this.validationReady
                && this.gpuValidationOk
                && this.validationReadbackOk
                && this.validationFailureCount == 0;
        return new ForgeFormalShaderProgramStats(
                STAGE,
                this.buildRuns,
                this.auditRuns,
                this.clearRuns,
                this.auditFailures,
                statusValidatorReady,
                statusValidationReady,
                this.validationShaderCompileAttempted,
                this.validationShaderCompileOk,
                this.validationProgramLinkOk,
                this.validationProgramId,
                this.formalShaderProgramBoundForValidation,
                false,
                false,
                false,
                true,
                store.formalModelStoreOwnerReady(),
                input.formalShaderInputConsumerReady(),
                lifecycle.reloadRebuildPrototypeReady(),
                multi.multiBlockFormalUploadAuditReady(),
                this.modelDataBindingOk,
                this.modelColourBindingOk,
                this.atlasTextureBindingOk,
                this.samplerBindingOk,
                input.bindingLayoutCompatible(),
                ForgeFormalShaderInputBindingLayout.MODEL_DATA_BINDING_INDEX,
                ForgeFormalShaderInputBindingLayout.MODEL_COLOUR_BINDING_INDEX,
                ForgeFormalShaderInputBindingLayout.BLOCK_MODEL_ATLAS_TEXTURE_UNIT,
                VALIDATION_RESULT_BINDING_INDEX,
                VALIDATION_MODEL_ID_BINDING_INDEX,
                input.blockModelRecordLayoutCompatible(),
                ForgeModelStoreFormalLayout.MODEL_RECORD_BYTES,
                input.safeSetModelCount(),
                this.validatedModelCount,
                this.validatedModelIds,
                this.gpuValidationAttempted,
                this.gpuValidationOk,
                this.gpuValidationSkippedReason,
                this.validationReadbackOk,
                this.validationFailureCount,
                formalIds,
                input.usesPlaceholderModelIds(),
                input.sampleSetModelIdsUsed(),
                input.sampleSetBridgeUsedAsFormalSource(),
                false,
                false,
                false,
                this.stale,
                this.requiresRebuild,
                this.lifecycleState,
                this.lastLifecycleEvent,
                this.staleReason,
                this.lastGlError,
                this.lastFailureReason,
                this.lastAudit.success(),
                this.lastAudit.error(),
                this.lastAudit.durationMs()
        );
    }

    String dump() {
        ForgeFormalShaderProgramStats status = this.createStatusSnapshot();
        return "J2 formal shader program validator: "
                + "stage=" + status.stage()
                + " validationProgramId=" + status.validationProgramId()
                + " bindings=\"" + ForgeFormalShaderInputBindingLayout.summary()
                + " validationResultBinding=" + VALIDATION_RESULT_BINDING_INDEX
                + " validationModelIdBinding=" + VALIDATION_MODEL_ID_BINDING_INDEX + "\""
                + " safeSetModelCount=" + status.safeSetModelCount()
                + " validatedModelIds=" + status.validatedModelIds()
                + " gpuValidationAttempted=" + status.gpuValidationAttempted()
                + " gpuValidationOk=" + status.gpuValidationOk()
                + " validationFailureCount=" + status.validationFailureCount()
                + " terrainDrawStarted=false visibleDrawStarted=false actualDrawEnabled=false formalRendererReady=false"
                + " lastFailureReason=" + status.lastFailureReason();
    }

    void clear() {
        this.clearRuns++;
        this.validatorReady = false;
        this.validationReady = false;
        this.stale = false;
        this.requiresRebuild = false;
        this.lifecycleState = "CLEARED";
        this.lastLifecycleEvent = "clear";
        this.staleReason = "none";
        this.lastFailureReason = "none";
        this.resetValidationFlags();
        this.lastAudit = ForgeFormalShaderProgramAuditResult.failure("none", 0.0D);
        this.scheduleCleanup("clear");
    }

    void markResourceReload() {
        this.markStale("resource-reload");
    }

    void markWorldUnload() {
        this.markStale("world-unload");
    }

    void markDimensionSwitch() {
        this.markStale("dimension-switch");
    }

    void markDebugPipelineClear() {
        this.markStale("debug-pipeline-clear");
    }

    void markPresetOff() {
        this.markStale("preset-off");
    }

    void markPresetClear() {
        this.markStale("preset-clear");
    }

    void markStale(String reason) {
        this.validatorReady = false;
        this.validationReady = false;
        this.stale = true;
        this.requiresRebuild = true;
        this.lifecycleState = "STALE";
        this.lastLifecycleEvent = safeReason(reason);
        this.staleReason = this.lastLifecycleEvent;
        this.lastFailureReason = this.staleReason;
        this.scheduleCleanup(this.lastLifecycleEvent);
    }

    private ForgeFormalShaderProgramAuditResult auditInternal(long startNanos) {
        ForgeFormalShaderInputConsumerStats input = this.instance.getFormalShaderInputConsumer().createStatusSnapshot();
        ForgeFormalModelBakeryLifecycleStats lifecycle = this.instance.getFormalModelBakeryLifecycle().createStatusSnapshot();
        boolean i6SafeSetExists = lifecycle.reloadRebuildPrototypeReady();
        boolean j1Valid = input.formalShaderInputConsumerReady();
        boolean formalIds = input.usesFormalModelIds()
                && !input.usesPlaceholderModelIds()
                && !input.sampleSetModelIdsUsed()
                && !input.sampleSetBridgeUsedAsFormalSource();
        boolean success = this.validationReady
                && !this.stale
                && i6SafeSetExists
                && j1Valid
                && this.validationShaderCompileOk
                && this.validationProgramLinkOk
                && this.modelDataBindingOk
                && this.modelColourBindingOk
                && this.atlasTextureBindingOk
                && this.samplerBindingOk
                && input.bindingLayoutCompatible()
                && input.blockModelRecordLayoutCompatible()
                && input.safeSetModelIdsAddressable()
                && formalIds
                && this.gpuValidationOk
                && this.validationReadbackOk
                && this.validationFailureCount == 0;
        return new ForgeFormalShaderProgramAuditResult(
                success,
                success ? "none" : "formal-shader-program-validation-audit-failed",
                elapsedMs(startNanos),
                i6SafeSetExists,
                j1Valid,
                this.validationShaderCompileOk,
                this.validationProgramLinkOk,
                this.modelDataBindingOk,
                this.modelColourBindingOk,
                this.atlasTextureBindingOk,
                this.samplerBindingOk,
                input.bindingLayoutCompatible(),
                input.blockModelRecordLayoutCompatible(),
                input.safeSetModelIdsAddressable(),
                input.usesPlaceholderModelIds(),
                input.sampleSetModelIdsUsed(),
                input.sampleSetBridgeUsedAsFormalSource(),
                this.gpuValidationOk,
                this.validationReadbackOk,
                this.validationFailureCount,
                false,
                false,
                false,
                false
        );
    }

    private int compileProgram() {
        int shader = 0;
        int program = 0;
        try {
            shader = GL20C.glCreateShader(GL43C.GL_COMPUTE_SHADER);
            GL20C.glShaderSource(shader, COMPUTE_SOURCE);
            GL20C.glCompileShader(shader);
            if (GL20C.glGetShaderi(shader, GL20C.GL_COMPILE_STATUS) == GL11C.GL_FALSE) {
                throw new IllegalStateException("compute-compile-failed:" + sanitize(GL20C.glGetShaderInfoLog(shader)));
            }

            program = GL20C.glCreateProgram();
            GL20C.glAttachShader(program, shader);
            GL20C.glLinkProgram(program);
            if (GL20C.glGetProgrami(program, GL20C.GL_LINK_STATUS) == GL11C.GL_FALSE) {
                throw new IllegalStateException("compute-link-failed:" + sanitize(GL20C.glGetProgramInfoLog(program)));
            }
            return program;
        } finally {
            if (program != 0 && shader != 0) {
                GL20C.glDetachShader(program, shader);
            }
            if (shader != 0) {
                GL20C.glDeleteShader(shader);
            }
        }
    }

    private void runGpuValidation(ForgeFormalModelStoreStats store, List<ForgeFormalUploadedModelSummary> summaries) {
        int count = Math.min(MAX_VALIDATED_MODELS, summaries.size());
        if (count <= 0) {
            this.gpuValidationAttempted = false;
            this.gpuValidationSkippedReason = "safe-set-empty";
            return;
        }

        int[] modelIds = new int[count];
        for (int i = 0; i < count; i++) {
            modelIds[i] = summaries.get(i).formalModelId();
        }
        this.validatedModelCount = count;
        this.validatedModelIds = Arrays.stream(modelIds).mapToObj(Integer::toString).collect(Collectors.joining(","));

        this.validationModelIdBufferId = GL45C.glCreateBuffers();
        this.validationResultBufferId = GL45C.glCreateBuffers();
        long idsPtr = MemoryUtil.nmemAlloc((long) count * Integer.BYTES);
        long zeroPtr = MemoryUtil.nmemCalloc((long) count * RESULT_WORDS_PER_MODEL, Integer.BYTES);
        try {
            for (int i = 0; i < count; i++) {
                MemoryUtil.memPutInt(idsPtr + ((long) i * Integer.BYTES), modelIds[i]);
            }
            GL45C.nglNamedBufferData(this.validationModelIdBufferId, (long) count * Integer.BYTES, idsPtr, GL15C.GL_STATIC_DRAW);
            GL45C.nglNamedBufferData(this.validationResultBufferId, (long) count * RESULT_WORDS_PER_MODEL * Integer.BYTES, zeroPtr, GL15C.GL_DYNAMIC_READ);
        } finally {
            MemoryUtil.nmemFree(idsPtr);
            MemoryUtil.nmemFree(zeroPtr);
        }

        int oldProgram = GL11C.glGetInteger(GL20C.GL_CURRENT_PROGRAM);
        int oldModelBuffer = GL30C.glGetIntegeri(GL43C.GL_SHADER_STORAGE_BUFFER_BINDING, ForgeFormalShaderInputBindingLayout.MODEL_DATA_BINDING_INDEX);
        int oldColourBuffer = GL30C.glGetIntegeri(GL43C.GL_SHADER_STORAGE_BUFFER_BINDING, ForgeFormalShaderInputBindingLayout.MODEL_COLOUR_BINDING_INDEX);
        int oldResultBuffer = GL30C.glGetIntegeri(GL43C.GL_SHADER_STORAGE_BUFFER_BINDING, VALIDATION_RESULT_BINDING_INDEX);
        int oldIdBuffer = GL30C.glGetIntegeri(GL43C.GL_SHADER_STORAGE_BUFFER_BINDING, VALIDATION_MODEL_ID_BINDING_INDEX);
        int oldActiveTexture = GL11C.glGetInteger(GL13C.GL_ACTIVE_TEXTURE);
        GL13C.glActiveTexture(GL13C.GL_TEXTURE0 + ForgeFormalShaderInputBindingLayout.BLOCK_MODEL_ATLAS_TEXTURE_UNIT);
        int oldTexture = GL11C.glGetInteger(GL11C.GL_TEXTURE_BINDING_2D);
        int oldSampler = GL30C.glGetIntegeri(GL33C.GL_SAMPLER_BINDING, ForgeFormalShaderInputBindingLayout.BLOCK_MODEL_ATLAS_TEXTURE_UNIT);

        try {
            clearGlErrors();
            GL20C.glUseProgram(this.validationProgramId);
            GL30C.glBindBufferBase(GL43C.GL_SHADER_STORAGE_BUFFER, ForgeFormalShaderInputBindingLayout.MODEL_DATA_BINDING_INDEX, store.modelDataBufferId());
            GL30C.glBindBufferBase(GL43C.GL_SHADER_STORAGE_BUFFER, ForgeFormalShaderInputBindingLayout.MODEL_COLOUR_BINDING_INDEX, store.modelColourBufferId());
            GL30C.glBindBufferBase(GL43C.GL_SHADER_STORAGE_BUFFER, VALIDATION_RESULT_BINDING_INDEX, this.validationResultBufferId);
            GL30C.glBindBufferBase(GL43C.GL_SHADER_STORAGE_BUFFER, VALIDATION_MODEL_ID_BINDING_INDEX, this.validationModelIdBufferId);
            GL11C.glBindTexture(GL11C.GL_TEXTURE_2D, store.atlasTextureId());
            GL33C.glBindSampler(ForgeFormalShaderInputBindingLayout.BLOCK_MODEL_ATLAS_TEXTURE_UNIT, store.samplerId());

            this.formalShaderProgramBoundForValidation = GL11C.glGetInteger(GL20C.GL_CURRENT_PROGRAM) == this.validationProgramId;
            this.modelDataBindingOk = GL30C.glGetIntegeri(GL43C.GL_SHADER_STORAGE_BUFFER_BINDING, ForgeFormalShaderInputBindingLayout.MODEL_DATA_BINDING_INDEX) == store.modelDataBufferId();
            this.modelColourBindingOk = GL30C.glGetIntegeri(GL43C.GL_SHADER_STORAGE_BUFFER_BINDING, ForgeFormalShaderInputBindingLayout.MODEL_COLOUR_BINDING_INDEX) == store.modelColourBufferId();
            this.atlasTextureBindingOk = GL11C.glGetInteger(GL11C.GL_TEXTURE_BINDING_2D) == store.atlasTextureId();
            this.samplerBindingOk = GL30C.glGetIntegeri(GL33C.GL_SAMPLER_BINDING, ForgeFormalShaderInputBindingLayout.BLOCK_MODEL_ATLAS_TEXTURE_UNIT) == store.samplerId();

            this.gpuValidationAttempted = true;
            GL43C.glDispatchCompute(count, 1, 1);
            GL43C.glMemoryBarrier(GL43C.GL_SHADER_STORAGE_BARRIER_BIT);
            GL11C.glFinish();
            int error = GL11C.glGetError();
            this.lastGlError = error == GL11C.GL_NO_ERROR ? "none" : glErrorName(error);
            if (error != GL11C.GL_NO_ERROR) {
                this.validationFailureCount++;
                this.gpuValidationOk = false;
                this.validationReadbackOk = false;
                this.gpuValidationSkippedReason = "gl-error-" + this.lastGlError;
                return;
            }

            int[] results = readResultBuffer(count);
            this.validationFailureCount = countValidationFailures(modelIds, results);
            this.validationReadbackOk = true;
            this.gpuValidationOk = this.validationFailureCount == 0;
            this.gpuValidationSkippedReason = "none";
        } finally {
            GL20C.glUseProgram(oldProgram);
            GL30C.glBindBufferBase(GL43C.GL_SHADER_STORAGE_BUFFER, ForgeFormalShaderInputBindingLayout.MODEL_DATA_BINDING_INDEX, oldModelBuffer);
            GL30C.glBindBufferBase(GL43C.GL_SHADER_STORAGE_BUFFER, ForgeFormalShaderInputBindingLayout.MODEL_COLOUR_BINDING_INDEX, oldColourBuffer);
            GL30C.glBindBufferBase(GL43C.GL_SHADER_STORAGE_BUFFER, VALIDATION_RESULT_BINDING_INDEX, oldResultBuffer);
            GL30C.glBindBufferBase(GL43C.GL_SHADER_STORAGE_BUFFER, VALIDATION_MODEL_ID_BINDING_INDEX, oldIdBuffer);
            GL11C.glBindTexture(GL11C.GL_TEXTURE_2D, oldTexture);
            GL33C.glBindSampler(ForgeFormalShaderInputBindingLayout.BLOCK_MODEL_ATLAS_TEXTURE_UNIT, oldSampler);
            GL13C.glActiveTexture(oldActiveTexture);
        }
    }

    private int[] readResultBuffer(int count) {
        int words = count * RESULT_WORDS_PER_MODEL;
        int[] results = new int[words];
        long ptr = MemoryUtil.nmemAlloc((long) words * Integer.BYTES);
        try {
            GL45C.nglGetNamedBufferSubData(this.validationResultBufferId, 0L, (long) words * Integer.BYTES, ptr);
            for (int i = 0; i < words; i++) {
                results[i] = MemoryUtil.memGetInt(ptr + ((long) i * Integer.BYTES));
            }
            return results;
        } finally {
            MemoryUtil.nmemFree(ptr);
        }
    }

    private int countValidationFailures(int[] modelIds, int[] results) {
        int failures = 0;
        for (int i = 0; i < modelIds.length; i++) {
            int modelId = modelIds[i];
            int base = i * RESULT_WORDS_PER_MODEL;
            int[] record = this.instance.getFormalModelStore().readPrototypeModelRecord(modelId);
            int colour = this.instance.getFormalModelStore().readPrototypeModelColour(modelId);
            int atlasPixel = firstAtlasPixel(this.instance.getFormalModelStore().readPrototypeAtlasFace(modelId, 0));
            if (results[base] != modelId) {
                failures++;
            }
            if (record.length <= ForgeModelStoreFormalLayout.WORD_FLAGS_A || results[base + 1] != record[0] || results[base + 2] != record[ForgeModelStoreFormalLayout.WORD_FLAGS_A]) {
                failures++;
            }
            if (results[base + 3] != colour) {
                failures++;
            }
            if (results[base + 4] != atlasPixel) {
                failures++;
            }
            if (record.length <= ForgeModelStoreFormalLayout.WORD_COLOUR_TINT || results[base + 5] != record[ForgeModelStoreFormalLayout.WORD_COLOUR_TINT]) {
                failures++;
            }
        }
        return failures;
    }

    private void resetValidationFlags() {
        this.validationShaderCompileAttempted = false;
        this.validationShaderCompileOk = false;
        this.validationProgramLinkOk = false;
        this.formalShaderProgramBoundForValidation = false;
        this.modelDataBindingOk = false;
        this.modelColourBindingOk = false;
        this.atlasTextureBindingOk = false;
        this.samplerBindingOk = false;
        this.gpuValidationAttempted = false;
        this.gpuValidationOk = false;
        this.gpuValidationSkippedReason = "not-run";
        this.validationReadbackOk = false;
        this.validationFailureCount = 0;
        this.safeSetModelCount = 0;
        this.validatedModelCount = 0;
        this.validatedModelIds = "";
        this.lastGlError = "none";
    }

    private void fail(String reason) {
        this.validatorReady = false;
        this.validationReady = false;
        this.lifecycleState = "FAILED";
        this.lastFailureReason = safeReason(reason);
    }

    private void scheduleCleanup(String reason) {
        if (RenderSystem.isOnRenderThread()) {
            this.closeOwnedResourcesOnRenderThread(true);
        } else {
            RenderSystem.recordRenderCall(() -> this.closeOwnedResourcesOnRenderThread(true));
        }
        this.lastLifecycleEvent = safeReason(reason);
    }

    private void closeOwnedResourcesOnRenderThread(boolean resetProgramState) {
        if (this.validationProgramId != 0) {
            GL20C.glDeleteProgram(this.validationProgramId);
        }
        if (this.validationResultBufferId != 0) {
            GL15C.glDeleteBuffers(this.validationResultBufferId);
        }
        if (this.validationModelIdBufferId != 0) {
            GL15C.glDeleteBuffers(this.validationModelIdBufferId);
        }
        this.validationProgramId = 0;
        this.validationResultBufferId = 0;
        this.validationModelIdBufferId = 0;
        if (resetProgramState) {
            this.validationShaderCompileOk = false;
            this.validationProgramLinkOk = false;
        }
    }

    private static boolean formalResourcesReady(ForgeFormalModelStoreStats store) {
        return store.formalModelStoreOwnerReady()
                && !store.stale()
                && !store.requiresRebuild()
                && store.modelDataBufferCreated()
                && store.modelDataBufferBytes() >= ForgeFormalModelStore.MODEL_DATA_BYTES
                && store.modelColourBufferCreated()
                && store.modelColourBufferBytes() >= ForgeFormalModelStore.MODEL_COLOUR_BYTES
                && store.atlasTextureCreated()
                && store.fullAtlasTextureCreated()
                && store.actualAtlasWidth() == ForgeModelAtlasLayout.ATLAS_WIDTH
                && store.actualAtlasHeight() == ForgeModelAtlasLayout.ATLAS_HEIGHT
                && store.samplerCreated()
                && store.samplerConfigured();
    }

    private static String explainFailure(
            ForgeFormalShaderInputConsumerStats input,
            ForgeMultiBlockFormalBakeUploadStats multi,
            ForgeFormalModelStoreStats store,
            boolean resourcesReady,
            boolean bindingCompatible,
            boolean formalIds,
            boolean noModels
    ) {
        if (!input.formalShaderInputConsumerReady()) {
            return "j1-formal-shader-input-consumer-not-ready:" + input.lastFailureReason();
        }
        if (!multi.multiBlockFormalUploadAuditReady()) {
            return "i6-multi-block-formal-upload-audit-not-ready";
        }
        if (!resourcesReady) {
            return "formal-model-store-resources-not-ready:" + store.lastBuildError() + ":" + store.lastAllocationError();
        }
        if (!bindingCompatible) {
            return "binding-layout-not-compatible";
        }
        if (!formalIds) {
            return "formal-model-ids-not-clean";
        }
        if (noModels) {
            return "safe-set-empty";
        }
        return "unknown";
    }

    private static int firstAtlasPixel(byte[] pixels) {
        if (pixels == null || pixels.length < ForgeModelAtlasPixelSample.BYTES_PER_PIXEL) {
            return 0;
        }
        return (pixels[0] & 0xFF)
                | ((pixels[1] & 0xFF) << 8)
                | ((pixels[2] & 0xFF) << 16)
                | ((pixels[3] & 0xFF) << 24);
    }

    private static void clearGlErrors() {
        while (GL11C.glGetError() != GL11C.GL_NO_ERROR) {
            // Drain stale GL errors before the audit-only validation dispatch.
        }
    }

    private static String glErrorName(int error) {
        return switch (error) {
            case GL11C.GL_NO_ERROR -> "none";
            case GL11C.GL_INVALID_ENUM -> "GL_INVALID_ENUM";
            case GL11C.GL_INVALID_VALUE -> "GL_INVALID_VALUE";
            case GL11C.GL_INVALID_OPERATION -> "GL_INVALID_OPERATION";
            case GL11C.GL_OUT_OF_MEMORY -> "GL_OUT_OF_MEMORY";
            default -> "0x" + Integer.toHexString(error);
        };
    }

    private static String sanitize(String log) {
        if (log == null || log.isBlank()) {
            return "none";
        }
        return log.replace('\n', ' ').replace('\r', ' ').replace(' ', '_');
    }

    private static String safeReason(String reason) {
        return reason == null || reason.isBlank() ? "unspecified" : reason.replace(' ', '-');
    }

    private static double elapsedMs(long startNanos) {
        return startNanos == 0L ? 0.0D : (System.nanoTime() - startNanos) / 1_000_000.0D;
    }
}
