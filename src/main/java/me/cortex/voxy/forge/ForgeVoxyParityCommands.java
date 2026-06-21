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
                .then(Commands.literal("original_voxy_mdic_cmdgen_status")
                        .executes(ctx -> originalVoxyMdicCmdgenStatus(ctx.getSource())))
                .then(Commands.literal("original_voxy_mdic_cmdgen_request_audit")
                        .executes(ctx -> originalVoxyMdicCmdgenRequestAudit(ctx.getSource())))
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

    private static int originalVoxyMdicCmdgenStatus(CommandSourceStack source) {
        ForgeOriginalVoxyMdicCommandGenerationStats status = ForgeVoxyInstance.INSTANCE
                .getOriginalVoxyModelPipeline()
                .createMdicCommandGenerationStatusSnapshot();
        source.sendSuccess(() -> Component.literal(format(status)), false);
        return 1;
    }

    private static int originalVoxyMdicCmdgenRequestAudit(CommandSourceStack source) {
        ForgeOriginalVoxyMdicCommandGenerationStats status = ForgeVoxyInstance.INSTANCE
                .getOriginalVoxyModelPipeline()
                .requestMdicCommandGenerationReadbackAudit();
        source.sendSuccess(() -> Component.literal("Voxy original MDIC cmdgen audit requested: " + format(status)), false);
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
        ForgeOriginalVoxyVisibleRendererStats visible = status.visibleRenderer();
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
                + " originalVisibleRendererStage=" + visible.originalVisibleRendererStage()
                + " originalVisibleRendererOwnerReady=" + visible.originalVisibleRendererOwnerReady()
                + " originalVoxyRunPipelineOrderUsed=" + visible.originalVoxyRunPipelineOrderUsed()
                + " originalVisibleMdicDrawSubmissionUsed=" + visible.originalVisibleMdicDrawSubmissionUsed()
                + " originalPostFrameDynamicWorkUsed=" + visible.originalPostFrameDynamicWorkUsed()
                + " originalMdicOpaqueDrawSubmitted=" + visible.originalMdicOpaqueDrawSubmitted()
                + " originalMdicTemporalDrawSubmitted=" + visible.originalMdicTemporalDrawSubmitted()
                + " originalMdicTranslucentDrawSubmitted=" + visible.originalMdicTranslucentDrawSubmitted()
                + " originalPipelineFinishCalled=" + visible.originalPipelineFinishCalled()
                + " originalVisibleRendererStateRestoreUsed=" + visible.originalVisibleRendererStateRestoreUsed()
                + " originalViewportFogParametersUsed=" + visible.originalViewportFogParametersUsed()
                + " originalFinalBlitEnvironmentalFogEnabled=" + visible.originalFinalBlitEnvironmentalFogEnabled()
                + " originalFinalBlitEnvironmentalFogUniformsUsed=" + visible.originalFinalBlitEnvironmentalFogUniformsUsed()
                + " originalVisibleFrameLastFogStart=" + visible.originalVisibleFrameLastFogStart()
                + " originalVisibleFrameLastFogEnd=" + visible.originalVisibleFrameLastFogEnd()
                + " originalVisibleFrameRunCount=" + visible.originalVisibleFrameRunCount()
                + " originalVisibleFrameSkippedCount=" + visible.originalVisibleFrameSkippedCount()
                + " originalVisibleFrameFailureCount=" + visible.originalVisibleFrameFailureCount()
                + " originalVisibleFrameDrawSubmissionCount=" + visible.originalVisibleFrameDrawSubmissionCount()
                + " originalVisibleFrameLastFramebuffer=" + visible.originalVisibleFrameLastFramebuffer()
                + " originalVisibleFrameLastViewportWidth=" + visible.originalVisibleFrameLastViewportWidth()
                + " originalVisibleFrameLastViewportHeight=" + visible.originalVisibleFrameLastViewportHeight()
                + " originalVisibleRendererUsesPreviewRoute=" + visible.originalVisibleRendererUsesPreviewRoute()
                + " startRequested=" + status.startRequested()
                + " startQueuedOnRenderThread=" + status.startQueuedOnRenderThread()
                + " startRequests=" + status.startRequests()
                + " startRuns=" + status.startRuns()
                + " tickRuns=" + status.tickRuns()
                + " uploadTickRuns=" + status.uploadTickRuns()
                + " blockBakeRequests=" + status.blockBakeRequests()
                + " originalLifecycleDownloadFlushUsed=" + status.originalLifecycleDownloadFlushUsed()
                + " originalRenderStateCaptureClearUsed=" + status.originalRenderStateCaptureClearUsed()
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
                + " originalAsyncNodeGeometryCacheReady=" + status.originalAsyncNodeGeometryCacheReady()
                + " originalRenderDistanceTrackerReady=" + status.originalRenderDistanceTrackerReady()
                + " originalViewportSelectorReady=" + status.originalViewportSelectorReady()
                + " originalViewportSelectorDefaultReady=" + status.originalViewportSelectorDefaultReady()
                + " originalViewportSelectorExtraViewportCount=" + status.originalViewportSelectorExtraViewportCount()
                + " originalViewportSelectorLastSelectedKey=" + status.originalViewportSelectorLastSelectedKey()
                + " originalHierarchicalOcclusionTraverserReady=" + status.originalHierarchicalOcclusionTraverserReady()
                + " originalHierarchicalOcclusionTraverserOwnerReady=" + status.originalHierarchicalOcclusionTraverserOwnerReady()
                + " originalMdicViewportOwnerReady=" + status.originalMdicViewportOwnerReady()
                + " originalHizOwnerReady=" + status.originalHizOwnerReady()
                + " originalHizTraversalExecutableReady=" + status.originalHizTraversalExecutableReady()
                + " originalHocTopNodeCount=" + status.originalHocTopNodeCount()
                + " originalHocTraversalRunCount=" + status.originalHocTraversalRunCount()
                + " originalHocRequestBatchForwardCount=" + status.originalHocRequestBatchForwardCount()
                + " originalHocLastLifecycleEvent=" + status.originalHocLastLifecycleEvent()
                + " originalHocLastFailureReason=" + status.originalHocLastFailureReason()
                + " renderGenerationResultConsumerAttached=" + status.renderGenerationResultConsumerAttached()
                + " originalBuildTaskPriorityUsed=" + status.originalBuildTaskPriorityUsed()
                + " originalHoldingSectionPolicyUsed=" + status.originalHoldingSectionPolicyUsed()
                + " originalServiceManagerParityReady=" + status.originalServiceManagerParityReady()
                + " originalServiceThreadConfigOwnerReady=" + status.originalServiceThreadConfigOwnerReady()
                + " originalEmbeddiumBuilderThreadSharingReady=" + status.originalEmbeddiumBuilderThreadSharingReady()
                + " originalEmbeddiumBuilderThreadSharingEnabled=" + status.originalEmbeddiumBuilderThreadSharingEnabled()
                + " originalServiceThreadTargetCount=" + status.originalServiceThreadTargetCount()
                + " originalServiceThreadDedicatedCount=" + status.originalServiceThreadDedicatedCount()
                + " originalEmbeddiumBuilderThreadCount=" + status.originalEmbeddiumBuilderThreadCount()
                + " originalEmbeddiumBuilderSemaphoreBlockCount=" + status.originalEmbeddiumBuilderSemaphoreBlockCount()
                + " originalServiceThreadPolicySource=" + status.originalServiceThreadPolicySource()
                + " originalServiceThreadPolicyFailureReason=" + status.originalServiceThreadPolicyFailureReason()
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
                + " originalGeometryDataRenderThreadStoreReady=" + status.originalGeometryDataRenderThreadStoreReady()
                + " originalGeometryDataCapacityPolicyUsed=" + status.originalGeometryDataCapacityPolicyUsed()
                + " originalGeometryDataMetadataBufferReady=" + status.originalGeometryDataMetadataBufferReady()
                + " originalGeometryDataGeometryBufferReady=" + status.originalGeometryDataGeometryBufferReady()
                + " originalGeometryDataExternalGeometryBuffer=" + status.originalGeometryDataExternalGeometryBuffer()
                + " originalGeometryDataSparseGeometryBuffer=" + status.originalGeometryDataSparseGeometryBuffer()
                + " originalGeometryDataSparseBufferSupported=" + status.originalGeometryDataSparseBufferSupported()
                + " originalGeometryDataNvidiaWindowsSparseWorkaroundUsed=" + status.originalGeometryDataNvidiaWindowsSparseWorkaroundUsed()
                + " originalGeometryDataCurrentSectionCount=" + status.originalGeometryDataCurrentSectionCount()
                + " originalGeometryDataMetadataBufferId=" + status.originalGeometryDataMetadataBufferId()
                + " originalGeometryDataGeometryBufferId=" + status.originalGeometryDataGeometryBufferId()
                + " originalGeometryDataMetadataCapacityBytes=" + status.originalGeometryDataMetadataCapacityBytes()
                + " originalGeometryDataSparseCommitmentBytes=" + status.originalGeometryDataSparseCommitmentBytes()
                + " originalGeometryDataGeneration=" + status.originalGeometryDataGeneration()
                + " originalGeometryDataBuildCount=" + status.originalGeometryDataBuildCount()
                + " originalGeometryDataFreeCount=" + status.originalGeometryDataFreeCount()
                + " originalGeometryDataLastGlError=" + status.originalGeometryDataLastGlError()
                + " originalGeometryDataLastLifecycleEvent=" + status.originalGeometryDataLastLifecycleEvent()
                + " originalGeometryDataLastFailureReason=" + status.originalGeometryDataLastFailureReason()
                + " originalAsyncNodeManagerSyncShapeReady=" + status.originalAsyncNodeManagerSyncShapeReady()
                + " originalAsyncNodeManagerFullParityReady=" + status.originalAsyncNodeManagerFullParityReady()
                + " originalAsyncNodeGeometryResultQueueReady=" + status.originalAsyncNodeGeometryResultQueueReady()
                + " originalAsyncNodeRenderThreadTickReady=" + status.originalAsyncNodeRenderThreadTickReady()
                + " originalAsyncNodeMultiMemcpyProgramReady=" + status.originalAsyncNodeMultiMemcpyProgramReady()
                + " originalAsyncNodeScatterProgramReady=" + status.originalAsyncNodeScatterProgramReady()
                + " originalAsyncNodeSyncResultPending=" + status.originalAsyncNodeSyncResultPending()
                + " originalDirectGeneratedSectionBridgeRemoved=" + status.originalDirectGeneratedSectionBridgeRemoved()
                + " originalAsyncNodeQueuedGeometryResults=" + status.originalAsyncNodeQueuedGeometryResults()
                + " originalAsyncNodeTrackedSectionIdCount=" + status.originalAsyncNodeTrackedSectionIdCount()
                + " originalAsyncNodeSubmittedGeometryResultCount=" + status.originalAsyncNodeSubmittedGeometryResultCount()
                + " originalAsyncNodeProcessedGeometryResultCount=" + status.originalAsyncNodeProcessedGeometryResultCount()
                + " originalAsyncNodePublishedSyncResultCount=" + status.originalAsyncNodePublishedSyncResultCount()
                + " originalAsyncNodeRenderThreadTickCount=" + status.originalAsyncNodeRenderThreadTickCount()
                + " originalAsyncNodeGeometryUploadCopyDispatchCount=" + status.originalAsyncNodeGeometryUploadCopyDispatchCount()
                + " originalAsyncNodeMetadataScatterDispatchCount=" + status.originalAsyncNodeMetadataScatterDispatchCount()
                + " originalAsyncNodeUploadedGeometryCopyCount=" + status.originalAsyncNodeUploadedGeometryCopyCount()
                + " originalAsyncNodeUploadedGeometryBytes=" + status.originalAsyncNodeUploadedGeometryBytes()
                + " originalAsyncNodeMetadataScatterWriteCount=" + status.originalAsyncNodeMetadataScatterWriteCount()
                + " originalAsyncNodeCurrentMaxNodeId=" + status.originalAsyncNodeCurrentMaxNodeId()
                + " originalAsyncNodeUsedGeometryBytes=" + status.originalAsyncNodeUsedGeometryBytes()
                + " originalAsyncNodeLastLifecycleEvent=" + status.originalAsyncNodeLastLifecycleEvent()
                + " originalAsyncNodeLastFailureReason=" + status.originalAsyncNodeLastFailureReason()
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

    private static String format(ForgeOriginalVoxyMdicCommandGenerationStats status) {
        return "Voxy original MDIC command generation parity: "
                + "stage=" + status.stage()
                + " originalMdicCommandGenerationOwnerReady=" + status.originalMdicCommandGenerationOwnerReady()
                + " originalMdicViewportCmdgenInputParityReady=" + status.originalMdicViewportCmdgenInputParityReady()
                + " originalCmdgenCompOutputParityReady=" + status.originalCmdgenCompOutputParityReady()
                + " originalCmdgenReadbackAuditReady=" + status.originalCmdgenReadbackAuditReady()
                + " originalCmdgenBarrierAuditReady=" + status.originalCmdgenBarrierAuditReady()
                + " productionCmdgenCompUsed=" + status.productionCmdgenCompUsed()
                + " productionPrepCompUsed=" + status.productionPrepCompUsed()
                + " productionCullRasterUsed=" + status.productionCullRasterUsed()
                + " prepProgramReady=" + status.prepProgramReady()
                + " cullProgramReady=" + status.cullProgramReady()
                + " cmdgenProgramReady=" + status.cmdgenProgramReady()
                + " uniformBufferReady=" + status.uniformBufferReady()
                + " distanceCountBufferReady=" + status.distanceCountBufferReady()
                + " sharedIndexBufferReady=" + status.sharedIndexBufferReady()
                + " vertexArrayReady=" + status.vertexArrayReady()
                + " viewportBuffersReady=" + status.viewportBuffersReady()
                + " geometryMetadataBufferReady=" + status.geometryMetadataBufferReady()
                + " geometryBufferReady=" + status.geometryBufferReady()
                + " renderListInputReady=" + status.renderListInputReady()
                + " visibilityBufferReady=" + status.visibilityBufferReady()
                + " positionScratchBufferReady=" + status.positionScratchBufferReady()
                + " prepDispatchCount=" + status.prepDispatchCount()
                + " cullRasterCount=" + status.cullRasterCount()
                + " cmdgenDispatchCount=" + status.cmdgenDispatchCount()
                + " readbackAuditRuns=" + status.readbackAuditRuns()
                + " readbackAuditFailures=" + status.readbackAuditFailures()
                + " renderListSectionCount=" + status.renderListSectionCount()
                + " cmdGenDispatchX=" + status.cmdGenDispatchX()
                + " cmdGenDispatchY=" + status.cmdGenDispatchY()
                + " cmdGenDispatchZ=" + status.cmdGenDispatchZ()
                + " opaqueDrawCount=" + status.opaqueDrawCount()
                + " translucentDrawCount=" + status.translucentDrawCount()
                + " temporalOpaqueDrawCount=" + status.temporalOpaqueDrawCount()
                + " cullCommandCount=" + status.cullCommandCount()
                + " cullCommandInstanceCount=" + status.cullCommandInstanceCount()
                + " cullCommandFirstIndex=" + status.cullCommandFirstIndex()
                + " firstCommandCount=" + status.firstCommandCount()
                + " firstCommandInstanceCount=" + status.firstCommandInstanceCount()
                + " firstCommandFirstIndex=" + status.firstCommandFirstIndex()
                + " firstCommandBaseVertex=" + status.firstCommandBaseVertex()
                + " firstCommandBaseInstance=" + status.firstCommandBaseInstance()
                + " firstPositionScratchWord0=" + status.firstPositionScratchWord0()
                + " firstPositionScratchWord1=" + status.firstPositionScratchWord1()
                + " drawCommandStride20Bytes=" + status.drawCommandStride20Bytes()
                + " drawCountLayoutMatchesOriginal=" + status.drawCountLayoutMatchesOriginal()
                + " cullCommandLayoutMatchesOriginal=" + status.cullCommandLayoutMatchesOriginal()
                + " positionScratchReadbackOk=" + status.positionScratchReadbackOk()
                + " noDebugCommandBuffersUsed=" + status.noDebugCommandBuffersUsed()
                + " mdicSectionRendererCalled=" + status.mdicSectionRendererCalled()
                + " voxyRenderSystemCalled=" + status.voxyRenderSystemCalled()
                + " terrainDrawCalled=" + status.terrainDrawCalled()
                + " glMultiDrawElementsIndirectCountCalled=" + status.glMultiDrawElementsIndirectCountCalled()
                + " formalRendererReady=" + status.formalRendererReady()
                + " actualRendererDrawEnabled=" + status.actualRendererDrawEnabled()
                + " formalDrawPipelineReady=" + status.formalDrawPipelineReady()
                + " lastGlError=" + status.lastGlError()
                + " lifecycleState=" + status.lifecycleState()
                + " lastLifecycleEvent=" + status.lastLifecycleEvent()
                + " lastFailureReason=" + status.lastFailureReason();
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
