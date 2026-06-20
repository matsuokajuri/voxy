package me.cortex.voxy.forge;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

final class ForgeVoxyParityCommands {
    private ForgeVoxyParityCommands() {
    }

    static void register(LiteralArgumentBuilder<CommandSourceStack> root) {
        root.then(Commands.literal("original_voxy_model_pipeline_start")
                        .executes(ctx -> originalVoxyModelPipelineStart(ctx.getSource())))
                .then(Commands.literal("original_voxy_model_pipeline_status")
                        .executes(ctx -> originalVoxyModelPipelineStatus(ctx.getSource())))
                .then(Commands.literal("original_voxy_model_pipeline_request_blockstate")
                        .then(Commands.argument("blockStateId", IntegerArgumentType.integer(1))
                                .executes(ctx -> originalVoxyModelPipelineRequestBlockState(
                                        ctx.getSource(),
                                        IntegerArgumentType.getInteger(ctx, "blockStateId")))))
                .then(Commands.literal("original_voxy_model_pipeline_clear")
                        .executes(ctx -> originalVoxyModelPipelineClear(ctx.getSource())));
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
                + " renderGenerationModelMissRequestRequeueReady=" + status.renderGenerationModelMissRequestRequeueReady()
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
}
