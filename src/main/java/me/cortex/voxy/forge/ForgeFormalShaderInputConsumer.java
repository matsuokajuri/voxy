package me.cortex.voxy.forge;

import com.mojang.blaze3d.systems.RenderSystem;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL13C;
import org.lwjgl.opengl.GL30C;
import org.lwjgl.opengl.GL33C;
import org.lwjgl.opengl.GL43C;

import java.util.List;
import java.util.stream.Collectors;

final class ForgeFormalShaderInputConsumer {
    static final String STAGE = "J1_FORMAL_SHADER_INPUT_CONSUMPTION_SKELETON";

    private final ForgeVoxyInstance instance;
    private long buildRuns;
    private long auditRuns;
    private long clearRuns;
    private long auditFailures;
    private boolean consumerReady;
    private boolean bindingValidationAttempted;
    private boolean bindingValidationOk;
    private String lastGlError = "none";
    private boolean stale;
    private boolean requiresRebuild;
    private String lifecycleState = "UNINITIALIZED";
    private String lastLifecycleEvent = "initialized";
    private String staleReason = "none";
    private String lastFailureReason = "none";
    private ForgeFormalShaderInputConsumerAuditResult lastAudit =
            ForgeFormalShaderInputConsumerAuditResult.failure("none", 0.0D);

    ForgeFormalShaderInputConsumer(ForgeVoxyInstance instance) {
        this.instance = instance;
    }

    ForgeFormalShaderInputConsumerStats build() {
        this.buildRuns++;
        this.lifecycleState = "BUILDING";
        this.lastLifecycleEvent = "build";
        this.lastFailureReason = "none";
        this.bindingValidationAttempted = false;
        this.bindingValidationOk = false;
        this.lastGlError = "none";

        if (!RenderSystem.isOnRenderThread()) {
            this.fail("not-render-thread");
            return this.createStatusSnapshot();
        }

        ForgeFormalModelBakeryLifecycleStats lifecycle = this.instance.getFormalModelBakeryLifecycle().createStatusSnapshot();
        if (!lifecycle.reloadRebuildPrototypeReady() || lifecycle.stale() || lifecycle.requiresRebuild()) {
            lifecycle = this.instance.getFormalModelBakeryLifecycle().runQaLifecycleRebuild();
        }

        ForgeMultiBlockFormalBakeUploadStats multi = this.instance.getMultiBlockFormalBakeUpload().createStatusSnapshot();
        if (!multi.multiBlockFormalUploadAuditReady()) {
            this.instance.getMultiBlockFormalBakeUpload().audit();
            multi = this.instance.getMultiBlockFormalBakeUpload().createStatusSnapshot();
        }

        ForgeFormalModelStoreStats store = this.instance.getFormalModelStore().createStatusSnapshot();
        boolean resourcesReady = formalResourcesReady(store);
        boolean layoutKnown = ForgeFormalShaderInputBindingLayout.bindingLayoutKnown();
        boolean recordLayoutCompatible = ForgeFormalShaderInputBindingLayout.recordLayoutCompatible();
        boolean bindingCompatible = resourcesReady && layoutKnown && recordLayoutCompatible;
        boolean idsAddressable = this.safeSetModelIdsAddressable(store);
        boolean formalIds = multi.usesFormalModelIds() && !multi.usesPlaceholderModelIds() && !multi.sampleSetModelIdsUsed();

        if (bindingCompatible) {
            this.bindingValidationAttempted = true;
            this.bindingValidationOk = this.validateBindUnbind(store);
        }

        if (lifecycle.reloadRebuildPrototypeReady() && multi.multiBlockFormalUploadAuditReady()) {
            this.stale = false;
            this.requiresRebuild = false;
            this.staleReason = "none";
        }

        this.consumerReady = lifecycle.formalModelBakeryLifecycleSkeletonReady()
                && lifecycle.reloadRebuildPrototypeReady()
                && multi.multiBlockFormalUploadAuditReady()
                && resourcesReady
                && bindingCompatible
                && idsAddressable
                && formalIds
                && this.bindingValidationOk
                && !this.stale;

        if (this.consumerReady) {
            this.lifecycleState = "BUILT";
            this.stale = false;
            this.requiresRebuild = false;
            this.staleReason = "none";
            this.lastFailureReason = "none";
        } else if ("none".equals(this.lastFailureReason)) {
            this.fail(explainFailure(lifecycle, multi, store, resourcesReady, bindingCompatible, idsAddressable, formalIds));
        }

        this.audit();
        this.instance.getFormalRendererManager().checkReadiness("j1-formal-shader-input-consumer-build");
        return this.createStatusSnapshot();
    }

    ForgeFormalShaderInputConsumerAuditResult audit() {
        this.auditRuns++;
        long start = System.nanoTime();
        try {
            this.lastAudit = this.auditInternal(start);
        } catch (RuntimeException e) {
            this.lastAudit = ForgeFormalShaderInputConsumerAuditResult.failure(e.getClass().getSimpleName() + ": " + e.getMessage(), elapsedMs(start));
        }
        if (!this.lastAudit.success()) {
            this.auditFailures++;
            this.lastFailureReason = this.lastAudit.error();
        }
        return this.lastAudit;
    }

    ForgeFormalShaderInputConsumerAuditResult createAuditStatusSnapshot() {
        return this.lastAudit;
    }

    ForgeFormalShaderInputConsumerStats createStatusSnapshot() {
        ForgeFormalModelStoreStats store = this.instance.getFormalModelStore().createStatusSnapshot();
        ForgeFormalModelBakeryLifecycleStats lifecycle = this.instance.getFormalModelBakeryLifecycle().createStatusSnapshot();
        ForgeMultiBlockFormalBakeUploadStats multi = this.instance.getMultiBlockFormalBakeUpload().createStatusSnapshot();
        List<ForgeFormalUploadedModelSummary> summaries = this.instance.getMultiBlockFormalBakeUpload().uploadedModelSummaries();
        boolean resourcesReady = formalResourcesReady(store);
        boolean layoutKnown = ForgeFormalShaderInputBindingLayout.bindingLayoutKnown();
        boolean recordLayoutCompatible = ForgeFormalShaderInputBindingLayout.recordLayoutCompatible();
        boolean bindingCompatible = resourcesReady && layoutKnown && recordLayoutCompatible;
        boolean idsAddressable = this.safeSetModelIdsAddressable(store);
        boolean formalIds = multi.usesFormalModelIds() && !multi.usesPlaceholderModelIds() && !multi.sampleSetModelIdsUsed();
        boolean ready = this.consumerReady
                && !this.stale
                && resourcesReady
                && bindingCompatible
                && idsAddressable
                && formalIds
                && lifecycle.reloadRebuildPrototypeReady()
                && multi.multiBlockFormalUploadAuditReady();
        return new ForgeFormalShaderInputConsumerStats(
                STAGE,
                this.buildRuns,
                this.auditRuns,
                this.clearRuns,
                this.auditFailures,
                ready,
                false,
                false,
                false,
                false,
                true,
                store.formalModelStoreOwnerReady(),
                lifecycle.formalModelBakeryLifecycleSkeletonReady(),
                lifecycle.reloadRebuildPrototypeReady(),
                multi.multiBlockFormalUploadAuditReady(),
                store.modelDataBufferCreated() && store.modelDataBufferBytes() >= ForgeFormalModelStore.MODEL_DATA_BYTES,
                store.modelDataBufferId(),
                store.modelDataBufferBytes(),
                store.modelColourBufferCreated() && store.modelColourBufferBytes() >= ForgeFormalModelStore.MODEL_COLOUR_BYTES,
                store.modelColourBufferId(),
                store.modelColourBufferBytes(),
                store.atlasTextureCreated() && store.fullAtlasTextureCreated(),
                store.atlasTextureId(),
                store.samplerCreated() && store.samplerConfigured(),
                store.samplerId(),
                layoutKnown,
                bindingCompatible,
                ForgeFormalShaderInputBindingLayout.MODEL_DATA_BINDING_INDEX,
                ForgeFormalShaderInputBindingLayout.MODEL_COLOUR_BINDING_INDEX,
                ForgeFormalShaderInputBindingLayout.BLOCK_MODEL_ATLAS_TEXTURE_UNIT,
                ForgeModelStoreFormalLayout.MODEL_RECORD_BYTES,
                recordLayoutCompatible,
                summaries.size(),
                summaries.stream().map(summary -> Integer.toString(summary.formalModelId())).collect(Collectors.joining(",")),
                idsAddressable,
                formalIds,
                multi.usesPlaceholderModelIds(),
                multi.sampleSetModelIdsUsed(),
                false,
                false,
                false,
                false,
                false,
                false,
                "J1 keeps GPU validation compile/pass disabled; it validates formal owner handles, binding layout, and GL bind/unbind only.",
                this.bindingValidationAttempted,
                this.bindingValidationOk,
                this.lastGlError,
                false,
                false,
                this.stale,
                this.requiresRebuild,
                this.lifecycleState,
                this.lastLifecycleEvent,
                this.staleReason,
                this.lastFailureReason,
                this.lastAudit.success(),
                this.lastAudit.error(),
                this.lastAudit.durationMs()
        );
    }

    String dump() {
        ForgeFormalShaderInputConsumerStats status = this.createStatusSnapshot();
        return String.format(
                "J1 formal shader input consumer: %s safeSetModelIds=%s bindingLayout=\"%s\" modelDataBufferId=%d modelColourBufferId=%d atlasTextureId=%d samplerId=%d shaderCompileAttempted=false gpuValidationAttempted=false noDraw=true formalRendererReady=false lastFailureReason=%s",
                status.stage(),
                status.safeSetModelIds(),
                ForgeFormalShaderInputBindingLayout.summary(),
                status.modelDataBufferId(),
                status.modelColourBufferId(),
                status.atlasTextureId(),
                status.samplerId(),
                status.lastFailureReason()
        );
    }

    void clear() {
        this.clearRuns++;
        this.consumerReady = false;
        this.bindingValidationAttempted = false;
        this.bindingValidationOk = false;
        this.lastGlError = "none";
        this.stale = false;
        this.requiresRebuild = false;
        this.lifecycleState = "CLEARED";
        this.lastLifecycleEvent = "clear";
        this.staleReason = "none";
        this.lastFailureReason = "none";
        this.lastAudit = ForgeFormalShaderInputConsumerAuditResult.failure("none", 0.0D);
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
        this.consumerReady = false;
        this.stale = true;
        this.requiresRebuild = true;
        this.lifecycleState = "STALE";
        this.lastLifecycleEvent = safeReason(reason);
        this.staleReason = this.lastLifecycleEvent;
        this.lastFailureReason = this.staleReason;
    }

    private ForgeFormalShaderInputConsumerAuditResult auditInternal(long startNanos) {
        ForgeFormalModelStoreStats store = this.instance.getFormalModelStore().createStatusSnapshot();
        ForgeFormalModelBakeryLifecycleStats lifecycle = this.instance.getFormalModelBakeryLifecycle().createStatusSnapshot();
        ForgeMultiBlockFormalBakeUploadStats multi = this.instance.getMultiBlockFormalBakeUpload().createStatusSnapshot();
        boolean resourcesReady = formalResourcesReady(store);
        boolean layoutKnown = ForgeFormalShaderInputBindingLayout.bindingLayoutKnown();
        boolean recordLayoutCompatible = ForgeFormalShaderInputBindingLayout.recordLayoutCompatible();
        boolean bindingCompatible = resourcesReady && layoutKnown && recordLayoutCompatible;
        boolean idsAddressable = this.safeSetModelIdsAddressable(store);
        boolean formalIds = multi.usesFormalModelIds() && !multi.usesPlaceholderModelIds() && !multi.sampleSetModelIdsUsed();
        boolean success = this.consumerReady
                && !this.stale
                && lifecycle.reloadRebuildPrototypeReady()
                && multi.multiBlockFormalUploadAuditReady()
                && resourcesReady
                && bindingCompatible
                && idsAddressable
                && formalIds
                && this.bindingValidationOk;
        return new ForgeFormalShaderInputConsumerAuditResult(
                success,
                success ? "none" : "formal-shader-input-consumer-audit-failed",
                elapsedMs(startNanos),
                lifecycle.reloadRebuildPrototypeReady(),
                resourcesReady,
                layoutKnown,
                bindingCompatible,
                recordLayoutCompatible,
                idsAddressable,
                formalIds,
                multi.usesPlaceholderModelIds(),
                multi.sampleSetModelIdsUsed(),
                false,
                this.bindingValidationOk,
                false,
                false,
                false
        );
    }

    private boolean validateBindUnbind(ForgeFormalModelStoreStats store) {
        if (!RenderSystem.isOnRenderThread()) {
            this.lastGlError = "not-render-thread";
            return false;
        }
        int modelBinding = ForgeFormalShaderInputBindingLayout.MODEL_DATA_BINDING_INDEX;
        int colourBinding = ForgeFormalShaderInputBindingLayout.MODEL_COLOUR_BINDING_INDEX;
        int atlasUnit = ForgeFormalShaderInputBindingLayout.BLOCK_MODEL_ATLAS_TEXTURE_UNIT;
        int oldModelBuffer = GL30C.glGetIntegeri(GL43C.GL_SHADER_STORAGE_BUFFER_BINDING, modelBinding);
        int oldColourBuffer = GL30C.glGetIntegeri(GL43C.GL_SHADER_STORAGE_BUFFER_BINDING, colourBinding);
        int oldActiveTexture = GL11C.glGetInteger(GL13C.GL_ACTIVE_TEXTURE);
        GL13C.glActiveTexture(GL13C.GL_TEXTURE0 + atlasUnit);
        int oldTexture = GL11C.glGetInteger(GL11C.GL_TEXTURE_BINDING_2D);
        int oldSampler = GL30C.glGetIntegeri(GL33C.GL_SAMPLER_BINDING, atlasUnit);
        try {
            clearGlErrors();
            GL30C.glBindBufferBase(GL43C.GL_SHADER_STORAGE_BUFFER, modelBinding, store.modelDataBufferId());
            GL30C.glBindBufferBase(GL43C.GL_SHADER_STORAGE_BUFFER, colourBinding, store.modelColourBufferId());
            GL11C.glBindTexture(GL11C.GL_TEXTURE_2D, store.atlasTextureId());
            GL33C.glBindSampler(atlasUnit, store.samplerId());
            int error = GL11C.glGetError();
            this.lastGlError = error == GL11C.GL_NO_ERROR ? "none" : glErrorName(error);
            return error == GL11C.GL_NO_ERROR;
        } finally {
            GL30C.glBindBufferBase(GL43C.GL_SHADER_STORAGE_BUFFER, modelBinding, oldModelBuffer);
            GL30C.glBindBufferBase(GL43C.GL_SHADER_STORAGE_BUFFER, colourBinding, oldColourBuffer);
            GL11C.glBindTexture(GL11C.GL_TEXTURE_2D, oldTexture);
            GL33C.glBindSampler(atlasUnit, oldSampler);
            GL13C.glActiveTexture(oldActiveTexture);
        }
    }

    private boolean safeSetModelIdsAddressable(ForgeFormalModelStoreStats store) {
        List<ForgeFormalUploadedModelSummary> summaries = this.instance.getMultiBlockFormalBakeUpload().uploadedModelSummaries();
        if (summaries.isEmpty()) {
            return false;
        }
        for (ForgeFormalUploadedModelSummary summary : summaries) {
            int modelId = summary.formalModelId();
            if (!ForgeModelAtlasLayout.isValidModelId(modelId) || modelId == 0) {
                return false;
            }
            if (summary.modelRecordBytes() != ForgeModelStoreFormalLayout.MODEL_RECORD_BYTES) {
                return false;
            }
            if (this.instance.getFormalModelStore().modelDataWriteOffset(modelId) + ForgeModelStoreFormalLayout.MODEL_RECORD_BYTES > store.modelDataBufferBytes()) {
                return false;
            }
            if (this.instance.getFormalModelStore().modelColourWriteOffset(modelId) + Integer.BYTES > store.modelColourBufferBytes()) {
                return false;
            }
        }
        return true;
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

    private void fail(String reason) {
        this.consumerReady = false;
        this.lifecycleState = "FAILED";
        this.lastFailureReason = safeReason(reason);
    }

    private static String explainFailure(
            ForgeFormalModelBakeryLifecycleStats lifecycle,
            ForgeMultiBlockFormalBakeUploadStats multi,
            ForgeFormalModelStoreStats store,
            boolean resourcesReady,
            boolean bindingCompatible,
            boolean idsAddressable,
            boolean formalIds
    ) {
        if (!lifecycle.reloadRebuildPrototypeReady()) {
            return "i6-reload-rebuild-not-ready";
        }
        if (!multi.multiBlockFormalUploadAuditReady()) {
            return "i5-multi-block-upload-audit-not-ready";
        }
        if (!resourcesReady) {
            return "formal-model-store-resources-not-ready:" + store.lastBuildError() + ":" + store.lastAllocationError();
        }
        if (!bindingCompatible) {
            return "formal-shader-input-binding-layout-not-compatible";
        }
        if (!idsAddressable) {
            return "safe-set-model-ids-not-addressable";
        }
        if (!formalIds) {
            return "safe-set-model-ids-not-formal";
        }
        return "binding-validation-failed";
    }

    private static void clearGlErrors() {
        while (GL11C.glGetError() != GL11C.GL_NO_ERROR) {
            // Drain stale GL errors before the bind validation probe.
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

    private static String safeReason(String reason) {
        return reason == null || reason.isBlank() ? "unspecified" : reason.replace(' ', '-');
    }

    private static double elapsedMs(long startNanos) {
        return startNanos == 0L ? 0.0D : (System.nanoTime() - startNanos) / 1_000_000.0D;
    }
}
