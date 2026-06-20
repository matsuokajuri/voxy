package me.cortex.voxy.forge;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

final class ForgeVoxyParityCommands {
    private ForgeVoxyParityCommands() {
    }

    static void register(LiteralArgumentBuilder<CommandSourceStack> root) {
        root.then(Commands.literal("frontend_compat_status")
                        .executes(ctx -> frontendCompatStatus(ctx.getSource())))
                .then(Commands.literal("original_voxy_model_pipeline_start")
                        .executes(ctx -> originalVoxyModelPipelineStart(ctx.getSource())))
                .then(Commands.literal("original_voxy_model_pipeline_status")
                        .executes(ctx -> originalVoxyModelPipelineStatus(ctx.getSource())))
                .then(Commands.literal("original_voxy_model_pipeline_request_blockstate")
                        .then(Commands.argument("blockStateId", IntegerArgumentType.integer(1))
                                .executes(ctx -> originalVoxyModelPipelineRequestBlockState(
                                        ctx.getSource(),
                                        IntegerArgumentType.getInteger(ctx, "blockStateId")))))
                .then(Commands.literal("original_voxy_render_generation_enqueue_section")
                        .then(Commands.argument("sectionKey", LongArgumentType.longArg())
                                .executes(ctx -> originalVoxyRenderGenerationEnqueueSection(
                                        ctx.getSource(),
                                        LongArgumentType.getLong(ctx, "sectionKey")))))
                .then(Commands.literal("original_voxy_model_pipeline_clear")
                        .executes(ctx -> originalVoxyModelPipelineClear(ctx.getSource())));
    }

    private static int frontendCompatStatus(CommandSourceStack source) {
        ForgeFrontendCompatStats status = ForgeFrontendCompat.createStatusSnapshot();
        source.sendSuccess(() -> Component.literal(format(status)), false);
        return 1;
    }

    private static int originalVoxyModelPipelineStart(CommandSourceStack source) {
        ForgeOriginalVoxyModelPipelineStats status = ForgeVoxyInstance.INSTANCE
                .getOriginalVoxyModelPipeline()
                .requestStart("command-start");
        source.sendSuccess(() -> Component.literal(format(status)), false);
        return 1;
    }

    private static int originalVoxyModelPipelineStatus(CommandSourceStack source) {
        ForgeOriginalVoxyModelPipelineStats status = ForgeVoxyInstance.INSTANCE
                .getOriginalVoxyModelPipeline()
                .createStatusSnapshot();
        source.sendSuccess(() -> Component.literal(format(status)), false);
        return 1;
    }

    private static int originalVoxyModelPipelineRequestBlockState(CommandSourceStack source, int blockStateId) {
        ForgeOriginalVoxyModelPipelineStats status = ForgeVoxyInstance.INSTANCE
                .getOriginalVoxyModelPipeline()
                .requestBlockBake(blockStateId);
        source.sendSuccess(() -> Component.literal(format(status)), false);
        return 1;
    }

    private static int originalVoxyRenderGenerationEnqueueSection(CommandSourceStack source, long sectionKey) {
        ForgeOriginalVoxyModelPipelineStats status = ForgeVoxyInstance.INSTANCE
                .getOriginalVoxyModelPipeline()
                .enqueueRenderGenerationTask(sectionKey);
        source.sendSuccess(() -> Component.literal(format(status)), false);
        return 1;
    }

    private static int originalVoxyModelPipelineClear(CommandSourceStack source) {
        ForgeVoxyInstance.INSTANCE.getOriginalVoxyModelPipeline().clear();
        ForgeOriginalVoxyModelPipelineStats status = ForgeVoxyInstance.INSTANCE
                .getOriginalVoxyModelPipeline()
                .createStatusSnapshot();
        source.sendSuccess(() -> Component.literal(format(status)), false);
        return 1;
    }

    private static String format(ForgeOriginalVoxyModelPipelineStats status) {
        return "Voxy original model pipeline parity: "
                + "stage=" + status.stage()
                + " ownerReady=" + status.ownerReady()
                + " modelBakerySubsystemOwnerReady=" + status.modelBakerySubsystemOwnerReady()
                + " originalModelFactoryUsed=" + status.originalModelFactoryUsed()
                + " originalSoftwareModelTextureBakeryUsed=" + status.originalSoftwareModelTextureBakeryUsed()
                + " originalModelStoreUsed=" + status.originalModelStoreUsed()
                + " mapperBiomeCallbackAttached=" + status.mapperBiomeCallbackAttached()
                + " mapperStateCallbackAttached=" + status.mapperStateCallbackAttached()
                + " existingBiomeEntriesQueued=" + status.existingBiomeEntriesQueued()
                + " renderDataFactoryRequestPathExpected=" + status.renderDataFactoryRequestPathExpected()
                + " safeSetRouteUsed=" + status.safeSetRouteUsed()
                + " previewRouteUsed=" + status.previewRouteUsed()
                + " sampleSetRouteUsed=" + status.sampleSetRouteUsed()
                + " startRequested=" + status.startRequested()
                + " startQueuedOnRenderThread=" + status.startQueuedOnRenderThread()
                + " startRequests=" + status.startRequests()
                + " startRuns=" + status.startRuns()
                + " tickRuns=" + status.tickRuns()
                + " uploadTickRuns=" + status.uploadTickRuns()
                + " blockBakeRequests=" + status.blockBakeRequests()
                + " mapperBlockStateCount=" + status.mapperBlockStateCount()
                + " mapperBiomeCount=" + status.mapperBiomeCount()
                + " processingCount=" + status.processingCount()
                + " bakedModelCount=" + status.bakedModelCount()
                + " originalModelFactoryAsyncThreadReady=" + status.originalModelFactoryAsyncThreadReady()
                + " originalModelFactoryRenderThreadUploadPathUsed=" + status.originalModelFactoryRenderThreadUploadPathUsed()
                + " idMappingsReady=" + status.idMappingsReady()
                + " metadataCacheReady=" + status.metadataCacheReady()
                + " fluidStateLutReady=" + status.fluidStateLutReady()
                + " modelTextureDedupeReady=" + status.modelTextureDedupeReady()
                + " uploadResultQueueReady=" + status.uploadResultQueueReady()
                + " mipChainAtlasUploadReady=" + status.mipChainAtlasUploadReady()
                + " textureUtilsOriginalHelpersPorted=" + status.textureUtilsOriginalHelpersPorted()
                + " textureUtilsByteForByteAuditReady=" + status.textureUtilsByteForByteAuditReady()
                + " biomeColourLutUploadReady=" + status.biomeColourLutUploadReady()
                + " uploadStreamPersistentMappedReady=" + status.uploadStreamPersistentMappedReady()
                + " originalModelStoreReadbackAuditReady=" + status.originalModelStoreReadbackAuditReady()
                + " modelDataReadbackOk=" + status.modelDataReadbackOk()
                + " modelColourReadbackOk=" + status.modelColourReadbackOk()
                + " atlasMipChainReadbackOk=" + status.atlasMipChainReadbackOk()
                + " modelStoreReadbackAuditRuns=" + status.modelStoreReadbackAuditRuns()
                + " modelStoreReadbackAuditFailures=" + status.modelStoreReadbackAuditFailures()
                + " lastAuditedModelId=" + status.lastAuditedModelId()
                + " customBlockStateIdMappingReady=" + status.customBlockStateIdMappingReady()
                + " customBlockStateIdMappingPresent=" + status.customBlockStateIdMappingPresent()
                + " customBlockStateIdMappingSource=" + status.customBlockStateIdMappingSource()
                + " lastModelStoreReadbackAuditFailureReason=" + status.lastModelStoreReadbackAuditFailureReason()
                + " renderGenerationModelMissRequestRequeueReady=" + status.renderGenerationModelMissRequestRequeueReady()
                + " originalRenderGenerationServiceUsed=" + status.originalRenderGenerationServiceUsed()
                + " originalRenderDataFactoryUsed=" + status.originalRenderDataFactoryUsed()
                + " originalBasicAsyncGeometryManagerUsed=" + status.originalBasicAsyncGeometryManagerUsed()
                + " originalBasicSectionGeometryDataReady=" + status.originalBasicSectionGeometryDataReady()
                + " originalNodeManagerParityReady=" + status.originalNodeManagerParityReady()
                + " renderGenerationResultConsumerAttached=" + status.renderGenerationResultConsumerAttached()
                + " originalBuildTaskPriorityUsed=" + status.originalBuildTaskPriorityUsed()
                + " originalHoldingSectionPolicyUsed=" + status.originalHoldingSectionPolicyUsed()
                + " originalServiceManagerParityReady=" + status.originalServiceManagerParityReady()
                + " renderGenerationTaskQueueCount=" + status.renderGenerationTaskQueueCount()
                + " renderGenerationTaskMapCount=" + status.renderGenerationTaskMapCount()
                + " renderGenerationHoldingSectionCount=" + status.renderGenerationHoldingSectionCount()
                + " renderGenerationEnqueuedTaskCount=" + status.renderGenerationEnqueuedTaskCount()
                + " renderGenerationProcessedTaskCount=" + status.renderGenerationProcessedTaskCount()
                + " renderGenerationCompletedMeshCount=" + status.renderGenerationCompletedMeshCount()
                + " renderGenerationEmptyMeshCount=" + status.renderGenerationEmptyMeshCount()
                + " renderGenerationRequeueCount=" + status.renderGenerationRequeueCount()
                + " renderGenerationReplacedTaskCount=" + status.renderGenerationReplacedTaskCount()
                + " renderGenerationModelMissRequestCount=" + status.renderGenerationModelMissRequestCount()
                + " renderGenerationInnerModelRequestScanCount=" + status.renderGenerationInnerModelRequestScanCount()
                + " renderGenerationOuterModelRequestScanCount=" + status.renderGenerationOuterModelRequestScanCount()
                + " renderGenerationFailedMeshCount=" + status.renderGenerationFailedMeshCount()
                + " renderGenerationLastTaskPosition=" + status.renderGenerationLastTaskPosition()
                + " renderGenerationLastLifecycleEvent=" + status.renderGenerationLastLifecycleEvent()
                + " renderGenerationLastFailureReason=" + status.renderGenerationLastFailureReason()
                + " originalGeometrySectionCount=" + status.originalGeometrySectionCount()
                + " originalGeometryMaxSectionCount=" + status.originalGeometryMaxSectionCount()
                + " originalGeometryUsedBytes=" + status.originalGeometryUsedBytes()
                + " originalGeometryCapacityBytes=" + status.originalGeometryCapacityBytes()
                + " originalGeometryArenaSizeItems=" + status.originalGeometryArenaSizeItems()
                + " originalGeometryArenaLimitItems=" + status.originalGeometryArenaLimitItems()
                + " originalGeometryArenaFreeBlocks=" + status.originalGeometryArenaFreeBlocks()
                + " originalGeometryPendingHeapUploads=" + status.originalGeometryPendingHeapUploads()
                + " originalGeometryPendingHeapUploadBytes=" + status.originalGeometryPendingHeapUploadBytes()
                + " originalGeometryPendingHeapRemovals=" + status.originalGeometryPendingHeapRemovals()
                + " originalGeometryPendingMetadataUpdates=" + status.originalGeometryPendingMetadataUpdates()
                + " originalGeometryAcceptedBuiltSectionCount=" + status.originalGeometryAcceptedBuiltSectionCount()
                + " originalGeometryReplacedBuiltSectionCount=" + status.originalGeometryReplacedBuiltSectionCount()
                + " originalGeometryRemovedBuiltSectionCount=" + status.originalGeometryRemovedBuiltSectionCount()
                + " originalGeometryEmptyBuiltSectionCount=" + status.originalGeometryEmptyBuiltSectionCount()
                + " originalGeometryDrainedUploadCount=" + status.originalGeometryDrainedUploadCount()
                + " originalGeometryDrainedUploadBytes=" + status.originalGeometryDrainedUploadBytes()
                + " originalGeometryDrainedRemoveCount=" + status.originalGeometryDrainedRemoveCount()
                + " originalGeometryDrainedMetadataUpdateCount=" + status.originalGeometryDrainedMetadataUpdateCount()
                + " originalGeometryLastSectionId=" + status.originalGeometryLastSectionId()
                + " originalGeometryLastSectionPosition=" + status.originalGeometryLastSectionPosition()
                + " originalGeometryLastLifecycleEvent=" + status.originalGeometryLastLifecycleEvent()
                + " originalGeometryLastFailureReason=" + status.originalGeometryLastFailureReason()
                + " queuedBlockBakeCount=" + status.queuedBlockBakeCount()
                + " queuedBiomeCount=" + status.queuedBiomeCount()
                + " queuedUploadResultCount=" + status.queuedUploadResultCount()
                + " biomeUploadResultCount=" + status.biomeUploadResultCount()
                + " inFlightBlockBakeCount=" + status.inFlightBlockBakeCount()
                + " completedModelCount=" + status.completedModelCount()
                + " uploadedModelRecordCount=" + status.uploadedModelRecordCount()
                + " uploadedModelColourCount=" + status.uploadedModelColourCount()
                + " uploadedAtlasFaceCount=" + status.uploadedAtlasFaceCount()
                + " dedupeHitCount=" + status.dedupeHitCount()
                + " dedupeMissCount=" + status.dedupeMissCount()
                + " failedBakeCount=" + status.failedBakeCount()
                + " modelTexture2idSize=" + status.modelTexture2idSize()
                + " nextModelId=" + status.nextModelId()
                + " lastRequestedBlockStateId=" + status.lastRequestedBlockStateId()
                + " lastUploadedBlockStateId=" + status.lastUploadedBlockStateId()
                + " lastUploadedModelId=" + status.lastUploadedModelId()
                + " lastDuplicateBlockStateId=" + status.lastDuplicateBlockStateId()
                + " lastDuplicateModelId=" + status.lastDuplicateModelId()
                + " queuesEmpty=" + status.queuesEmpty()
                + " stale=" + status.stale()
                + " requiresRebuild=" + status.requiresRebuild()
                + " lifecycleState=" + status.lifecycleState()
                + " lastLifecycleEvent=" + status.lastLifecycleEvent()
                + " lastFailureReason=" + status.lastFailureReason()
                + " formalRendererReady=" + status.formalRendererReady()
                + " actualRendererDrawEnabled=" + status.actualRendererDrawEnabled();
    }

    private static String format(ForgeFrontendCompatStats status) {
        return "Voxy frontend compat parity: "
                + "stage=" + status.stage()
                + " forgeFrontendCompatReady=" + status.forgeFrontendCompatReady()
                + " forgeModListUsed=" + status.forgeModListUsed()
                + " fabricLoaderUsed=" + status.fabricLoaderUsed()
                + " sodiumRuntimeModIdUsed=" + status.sodiumRuntimeModIdUsed()
                + " irisRuntimeModIdUsed=" + status.irisRuntimeModIdUsed()
                + " embeddiumRequired=" + status.embeddiumRequired()
                + " oculusRequired=" + status.oculusRequired()
                + " embeddiumLoaded=" + status.embeddiumLoaded()
                + " oculusLoaded=" + status.oculusLoaded()
                + " embeddiumVersion=" + status.embeddiumVersion()
                + " oculusVersion=" + status.oculusVersion()
                + " frontendPrerequisitesReady=" + status.frontendPrerequisitesReady()
                + " sodiumApiPackageNamesExpected=" + status.sodiumApiPackageNamesExpected()
                + " irisApiPackageNamesExpected=" + status.irisApiPackageNamesExpected()
                + " javaPackageGlobalRenameAllowed=" + status.javaPackageGlobalRenameAllowed()
                + " configUiParityReady=" + status.configUiParityReady()
                + " rendererFrontendHookReady=" + status.rendererFrontendHookReady()
                + " shaderpackFrontendHookReady=" + status.shaderpackFrontendHookReady()
                + " lastFailureReason=" + status.lastFailureReason();
    }
}
