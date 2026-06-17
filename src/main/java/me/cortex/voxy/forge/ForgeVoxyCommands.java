package me.cortex.voxy.forge;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import me.cortex.voxy.common.world.service.VoxelIngestService;
import me.cortex.voxy.config.SimpleGpuMeshLoadedChunkSkipMode;
import me.cortex.voxy.config.SimpleGpuMeshSource;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.chunk.LevelChunk;

public final class ForgeVoxyCommands {
    private static final SimpleGpuMeshSource RECOMMENDED_MESH_SOURCE = SimpleGpuMeshSource.BUILT_SECTION;
    private static final SimpleGpuMeshSource FALLBACK_MESH_SOURCE = SimpleGpuMeshSource.CPU_MESH;
    private static final String CPU_MESH_SOURCE_DESCRIPTION = "legacy PoC source / fallback";
    private static final String BUILT_SECTION_SOURCE_DESCRIPTION = "recommended renderer migration source";
    private static final String GL_HEAP_READBACK_SOURCE_DESCRIPTION = "debug source decoded from upload-only GL heap readback";

    private ForgeVoxyCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("voxy")
                .then(Commands.literal("ingest_current_chunk")
                        .executes(ctx -> ingestCurrentChunk(ctx.getSource())))
                .then(Commands.literal("build_current_chunk_mesh")
                        .executes(ctx -> buildCurrentChunkMesh(ctx.getSource())))
                .then(Commands.literal("build_current_chunk_model_mesh")
                        .executes(ctx -> buildCurrentChunkModelMesh(ctx.getSource())))
                .then(Commands.literal("build_current_chunk_cpu_mesh")
                        .executes(ctx -> buildCurrentChunkCpuMesh(ctx.getSource())))
                .then(Commands.literal("build_current_chunk_built_section")
                        .executes(ctx -> buildCurrentChunkBuiltSection(ctx.getSource())))
                .then(Commands.literal("built_section_cache_status")
                        .executes(ctx -> builtSectionCacheStatus(ctx.getSource())))
                .then(Commands.literal("built_section_cache_clear")
                        .executes(ctx -> clearBuiltSectionCache(ctx.getSource())))
                .then(Commands.literal("built_section_build_clear")
                        .executes(ctx -> clearBuiltSectionBuildState(ctx.getSource())))
                .then(Commands.literal("geometry_manager_consume_current_chunk")
                        .executes(ctx -> consumeCurrentChunkGeometryManager(ctx.getSource())))
                .then(Commands.literal("geometry_manager_status")
                        .executes(ctx -> geometryManagerStatus(ctx.getSource())))
                .then(Commands.literal("geometry_manager_dump_sample")
                        .executes(ctx -> geometryManagerDumpSample(ctx.getSource())))
                .then(Commands.literal("geometry_manager_clear")
                        .executes(ctx -> clearGeometryManager(ctx.getSource())))
                .then(Commands.literal("geometry_manager_consume_clear")
                        .executes(ctx -> clearGeometryManagerConsumeState(ctx.getSource())))
                .then(Commands.literal("geometry_gpu_upload_status")
                        .executes(ctx -> geometryGpuUploadStatus(ctx.getSource())))
                .then(Commands.literal("geometry_gpu_validate_sample")
                        .executes(ctx -> geometryGpuValidateSample(ctx.getSource())))
                .then(Commands.literal("geometry_gpu_audit_sample")
                        .executes(ctx -> geometryGpuAuditSample(ctx.getSource())))
                .then(Commands.literal("geometry_gpu_audit_status")
                        .executes(ctx -> geometryGpuAuditStatus(ctx.getSource())))
                .then(Commands.literal("geometry_gpu_audit_clear")
                        .executes(ctx -> geometryGpuAuditClear(ctx.getSource())))
                .then(Commands.literal("geometry_gpu_stress_status")
                        .executes(ctx -> geometryGpuStressStatus(ctx.getSource())))
                .then(Commands.literal("geometry_gpu_stress_clear")
                        .executes(ctx -> geometryGpuStressClear(ctx.getSource())))
                .then(Commands.literal("geometry_gpu_stress_once")
                        .executes(ctx -> geometryGpuStressOnce(ctx.getSource())))
                .then(Commands.literal("geometry_gpu_visualize_sample")
                        .executes(ctx -> geometryGpuVisualizeSample(ctx.getSource())))
                .then(Commands.literal("geometry_gpu_visualize_status")
                        .executes(ctx -> geometryGpuVisualizeStatus(ctx.getSource())))
                .then(Commands.literal("geometry_gpu_visualize_clear")
                        .executes(ctx -> geometryGpuVisualizeClear(ctx.getSource())))
                .then(Commands.literal("geometry_gpu_visualize_enable")
                        .executes(ctx -> setGeometryGpuVisualization(ctx.getSource(), true)))
                .then(Commands.literal("geometry_gpu_visualize_disable")
                        .executes(ctx -> setGeometryGpuVisualization(ctx.getSource(), false)))
                .then(Commands.literal("geometry_gpu_readback_mesh_build")
                        .executes(ctx -> geometryGpuReadbackMeshBuild(ctx.getSource())))
                .then(Commands.literal("geometry_gpu_readback_mesh_status")
                        .executes(ctx -> geometryGpuReadbackMeshStatus(ctx.getSource())))
                .then(Commands.literal("geometry_gpu_readback_mesh_clear")
                        .executes(ctx -> geometryGpuReadbackMeshClear(ctx.getSource())))
                .then(Commands.literal("geometry_gpu_readback_mesh_refresh_enable")
                        .executes(ctx -> setGeometryGpuReadbackMeshAutoRefresh(ctx.getSource(), true)))
                .then(Commands.literal("geometry_gpu_readback_mesh_refresh_disable")
                        .executes(ctx -> setGeometryGpuReadbackMeshAutoRefresh(ctx.getSource(), false)))
                .then(Commands.literal("geometry_gpu_readback_mesh_refresh_once")
                        .executes(ctx -> geometryGpuReadbackMeshRefreshOnce(ctx.getSource())))
                .then(Commands.literal("geometry_gpu_upload_enable")
                        .executes(ctx -> setGeometryGpuUpload(ctx.getSource(), true)))
                .then(Commands.literal("geometry_gpu_upload_disable")
                        .executes(ctx -> setGeometryGpuUpload(ctx.getSource(), false)))
                .then(Commands.literal("geometry_gpu_upload_clear")
                        .executes(ctx -> clearGeometryGpuUpload(ctx.getSource())))
                .then(Commands.literal("direct_gl_renderer_status")
                        .executes(ctx -> directGlRendererStatus(ctx.getSource())))
                .then(Commands.literal("direct_gl_renderer_enable")
                        .executes(ctx -> setDirectGlRenderer(ctx.getSource(), true)))
                .then(Commands.literal("direct_gl_renderer_disable")
                        .executes(ctx -> setDirectGlRenderer(ctx.getSource(), false)))
                .then(Commands.literal("direct_gl_renderer_clear")
                        .executes(ctx -> clearDirectGlRenderer(ctx.getSource())))
                .then(Commands.literal("direct_gl_renderer_plan_sample")
                        .executes(ctx -> directGlRendererPlanSample(ctx.getSource())))
                .then(Commands.literal("direct_gl_renderer_build_draw_list")
                        .executes(ctx -> directGlRendererBuildDrawList(ctx.getSource())))
                .then(Commands.literal("direct_gl_renderer_draw_enable")
                        .executes(ctx -> setDirectGlRendererDraw(ctx.getSource(), true)))
                .then(Commands.literal("direct_gl_renderer_draw_disable")
                        .executes(ctx -> setDirectGlRendererDraw(ctx.getSource(), false)))
                .then(Commands.literal("direct_gl_renderer_draw_mode")
                        .then(Commands.literal("loop")
                                .executes(ctx -> setDirectGlRendererDrawMode(ctx.getSource(), ForgeDirectGpuGeometryDrawMode.LOOP_PER_SECTION)))
                        .then(Commands.literal("multi_draw_arrays")
                                .executes(ctx -> setDirectGlRendererDrawMode(ctx.getSource(), ForgeDirectGpuGeometryDrawMode.MULTI_DRAW_ARRAYS)))
                        .then(Commands.literal("indirect")
                                .executes(ctx -> setDirectGlRendererDrawMode(ctx.getSource(), ForgeDirectGpuGeometryDrawMode.MULTI_DRAW_ARRAYS_INDIRECT)))
                        .then(Commands.literal("multi_draw_arrays_indirect")
                                .executes(ctx -> setDirectGlRendererDrawMode(ctx.getSource(), ForgeDirectGpuGeometryDrawMode.MULTI_DRAW_ARRAYS_INDIRECT)))
                        .then(Commands.literal("auto")
                                .executes(ctx -> setDirectGlRendererDrawMode(ctx.getSource(), ForgeDirectGpuGeometryDrawMode.AUTO))))
                .then(Commands.literal("direct_gl_renderer_shader_status")
                        .executes(ctx -> directGlRendererShaderStatus(ctx.getSource())))
                .then(Commands.literal("direct_gl_renderer_draw_list_status")
                        .executes(ctx -> directGlRendererStatus(ctx.getSource())))
                .then(Commands.literal("direct_gl_renderer_auto_plan_enable")
                        .executes(ctx -> setDirectGlRendererAutoPlan(ctx.getSource(), true)))
                .then(Commands.literal("direct_gl_renderer_auto_plan_disable")
                        .executes(ctx -> setDirectGlRendererAutoPlan(ctx.getSource(), false)))
                .then(Commands.literal("direct_gl_renderer_auto_plan_once")
                        .executes(ctx -> directGlRendererAutoPlanOnce(ctx.getSource())))
                .then(Commands.literal("direct_gl_renderer_auto_plan_status")
                        .executes(ctx -> directGlRendererStatus(ctx.getSource())))
                .then(Commands.literal("direct_gl_renderer_indirect_audit")
                        .executes(ctx -> directGlRendererIndirectAudit(ctx.getSource())))
                .then(Commands.literal("direct_gl_renderer_indirect_audit_status")
                        .executes(ctx -> directGlRendererIndirectAuditStatus(ctx.getSource())))
                .then(Commands.literal("direct_gl_renderer_indirect_audit_clear")
                        .executes(ctx -> directGlRendererIndirectAuditClear(ctx.getSource())))
                .then(Commands.literal("direct_gl_renderer_stress_once")
                        .executes(ctx -> directGlRendererStressOnce(ctx.getSource())))
                .then(Commands.literal("direct_gl_renderer_stress_status")
                        .executes(ctx -> directGlRendererStressStatus(ctx.getSource())))
                .then(Commands.literal("direct_gl_renderer_stress_clear")
                        .executes(ctx -> directGlRendererStressClear(ctx.getSource())))
                .then(Commands.literal("direct_gl_mdic_plan_sample")
                        .executes(ctx -> directGlMdicPlanSample(ctx.getSource())))
                .then(Commands.literal("direct_gl_mdic_build_buffer")
                        .executes(ctx -> directGlMdicBuildBuffer(ctx.getSource())))
                .then(Commands.literal("direct_gl_mdic_status")
                        .executes(ctx -> directGlMdicStatus(ctx.getSource())))
                .then(Commands.literal("direct_gl_mdic_clear")
                        .executes(ctx -> directGlMdicClear(ctx.getSource())))
                .then(Commands.literal("direct_gl_mdic_audit")
                        .executes(ctx -> directGlMdicAudit(ctx.getSource())))
                .then(Commands.literal("direct_gl_mdic_audit_status")
                        .executes(ctx -> directGlMdicAuditStatus(ctx.getSource())))
                .then(Commands.literal("direct_gl_mdic_audit_clear")
                        .executes(ctx -> directGlMdicAuditClear(ctx.getSource())))
                .then(Commands.literal("direct_gl_mdic_stress_once")
                        .executes(ctx -> directGlMdicStressOnce(ctx.getSource())))
                .then(Commands.literal("direct_gl_mdic_stress_status")
                        .executes(ctx -> directGlMdicStressStatus(ctx.getSource())))
                .then(Commands.literal("direct_gl_mdic_stress_clear")
                        .executes(ctx -> directGlMdicStressClear(ctx.getSource())))
                .then(Commands.literal("direct_gl_mdic_draw_enable")
                        .executes(ctx -> setDirectGlMdicDraw(ctx.getSource(), true)))
                .then(Commands.literal("direct_gl_mdic_draw_disable")
                        .executes(ctx -> setDirectGlMdicDraw(ctx.getSource(), false)))
                .then(Commands.literal("direct_gl_mdic_draw_status")
                        .executes(ctx -> directGlMdicDrawStatus(ctx.getSource())))
                .then(Commands.literal("direct_gl_mdic_shader_status")
                        .executes(ctx -> directGlMdicShaderStatus(ctx.getSource())))
                .then(Commands.literal("direct_gl_mdic_draw_mode")
                        .then(Commands.literal("loop")
                                .executes(ctx -> setDirectGlMdicDrawMode(ctx.getSource(), ForgeMdicDebugDrawMode.LOOP_PER_COMMAND)))
                        .then(Commands.literal("multi_draw_arrays")
                                .executes(ctx -> setDirectGlMdicDrawMode(ctx.getSource(), ForgeMdicDebugDrawMode.MULTI_DRAW_ARRAYS)))
                        .then(Commands.literal("indirect")
                                .executes(ctx -> setDirectGlMdicDrawMode(ctx.getSource(), ForgeMdicDebugDrawMode.MULTI_DRAW_ARRAYS_INDIRECT)))
                        .then(Commands.literal("multi_draw_arrays_indirect")
                                .executes(ctx -> setDirectGlMdicDrawMode(ctx.getSource(), ForgeMdicDebugDrawMode.MULTI_DRAW_ARRAYS_INDIRECT)))
                        .then(Commands.literal("elements_indirect")
                                .executes(ctx -> setDirectGlMdicDrawMode(ctx.getSource(), ForgeMdicDebugDrawMode.MULTI_DRAW_ELEMENTS_INDIRECT)))
                        .then(Commands.literal("multi_draw_elements_indirect")
                                .executes(ctx -> setDirectGlMdicDrawMode(ctx.getSource(), ForgeMdicDebugDrawMode.MULTI_DRAW_ELEMENTS_INDIRECT)))
                        .then(Commands.literal("elements_indirect_count")
                                .executes(ctx -> setDirectGlMdicDrawMode(ctx.getSource(), ForgeMdicDebugDrawMode.MULTI_DRAW_ELEMENTS_INDIRECT_COUNT)))
                        .then(Commands.literal("multi_draw_elements_indirect_count")
                                .executes(ctx -> setDirectGlMdicDrawMode(ctx.getSource(), ForgeMdicDebugDrawMode.MULTI_DRAW_ELEMENTS_INDIRECT_COUNT)))
                        .then(Commands.literal("count")
                                .executes(ctx -> setDirectGlMdicDrawMode(ctx.getSource(), ForgeMdicDebugDrawMode.MULTI_DRAW_ELEMENTS_INDIRECT_COUNT)))
                        .then(Commands.literal("auto")
                                .executes(ctx -> setDirectGlMdicDrawMode(ctx.getSource(), ForgeMdicDebugDrawMode.AUTO))))
                .then(Commands.literal("direct_gl_mdic_draw_clear")
                        .executes(ctx -> directGlMdicDrawClear(ctx.getSource())))
                .then(Commands.literal("direct_gl_mdic_draw_indirect_audit")
                        .executes(ctx -> directGlMdicDrawIndirectAudit(ctx.getSource())))
                .then(Commands.literal("direct_gl_mdic_draw_indirect_audit_status")
                        .executes(ctx -> directGlMdicDrawIndirectAuditStatus(ctx.getSource())))
                .then(Commands.literal("direct_gl_mdic_draw_indirect_audit_clear")
                        .executes(ctx -> directGlMdicDrawIndirectAuditClear(ctx.getSource())))
                .then(Commands.literal("direct_gl_mdic_draw_elements_indirect_audit")
                        .executes(ctx -> directGlMdicDrawElementsIndirectAudit(ctx.getSource())))
                .then(Commands.literal("direct_gl_mdic_draw_elements_indirect_audit_status")
                        .executes(ctx -> directGlMdicDrawElementsIndirectAuditStatus(ctx.getSource())))
                .then(Commands.literal("direct_gl_mdic_draw_elements_indirect_audit_clear")
                        .executes(ctx -> directGlMdicDrawElementsIndirectAuditClear(ctx.getSource())))
                .then(Commands.literal("direct_gl_mdic_draw_count_audit")
                        .executes(ctx -> directGlMdicDrawCountAudit(ctx.getSource())))
                .then(Commands.literal("direct_gl_mdic_draw_count_audit_status")
                        .executes(ctx -> directGlMdicDrawCountAuditStatus(ctx.getSource())))
                .then(Commands.literal("direct_gl_mdic_draw_count_audit_clear")
                        .executes(ctx -> directGlMdicDrawCountAuditClear(ctx.getSource())))
                .then(Commands.literal("direct_gl_mdic_draw_stress_once")
                        .executes(ctx -> directGlMdicDrawStressOnce(ctx.getSource())))
                .then(Commands.literal("direct_gl_mdic_draw_stress_status")
                        .executes(ctx -> directGlMdicDrawStressStatus(ctx.getSource())))
                .then(Commands.literal("direct_gl_mdic_draw_stress_clear")
                        .executes(ctx -> directGlMdicDrawStressClear(ctx.getSource())))
                .then(Commands.literal("model_bridge_check")
                        .executes(ctx -> modelBridgeCheck(ctx.getSource())))
                .then(Commands.literal("model_bridge_status")
                        .executes(ctx -> modelBridgeStatus(ctx.getSource())))
                .then(Commands.literal("model_bridge_clear")
                        .executes(ctx -> modelBridgeClear(ctx.getSource())))
                .then(Commands.literal("model_bridge_dump_sample")
                        .executes(ctx -> modelBridgeDumpSample(ctx.getSource())))
                .then(Commands.literal("model_store_skeleton_build")
                        .executes(ctx -> modelStoreSkeletonBuild(ctx.getSource())))
                .then(Commands.literal("model_store_skeleton_status")
                        .executes(ctx -> modelStoreSkeletonStatus(ctx.getSource())))
                .then(Commands.literal("model_store_skeleton_audit")
                        .executes(ctx -> modelStoreSkeletonAudit(ctx.getSource())))
                .then(Commands.literal("model_store_skeleton_audit_status")
                        .executes(ctx -> modelStoreSkeletonAuditStatus(ctx.getSource())))
                .then(Commands.literal("model_store_skeleton_dump_sample")
                        .executes(ctx -> modelStoreSkeletonDumpSample(ctx.getSource())))
                .then(Commands.literal("model_store_skeleton_clear")
                        .executes(ctx -> modelStoreSkeletonClear(ctx.getSource())))
                .then(Commands.literal("model_store_layout_audit")
                        .executes(ctx -> modelStoreLayoutAudit(ctx.getSource())))
                .then(Commands.literal("model_store_layout_audit_status")
                        .executes(ctx -> modelStoreLayoutAuditStatus(ctx.getSource())))
                .then(Commands.literal("model_store_layout_audit_clear")
                        .executes(ctx -> modelStoreLayoutAuditClear(ctx.getSource())))
                .then(Commands.literal("model_bridge_resource_reload_status")
                        .executes(ctx -> modelBridgeResourceReloadStatus(ctx.getSource())))
                .then(Commands.literal("model_bridge_resource_reload_clear_stats")
                        .executes(ctx -> modelBridgeResourceReloadClearStats(ctx.getSource())))
                .then(Commands.literal("model_bridge_simulate_resource_reload")
                        .executes(ctx -> modelBridgeSimulateResourceReload(ctx.getSource())))
                .then(Commands.literal("baked_model_bridge_check")
                        .executes(ctx -> bakedModelBridgeCheck(ctx.getSource())))
                .then(Commands.literal("baked_model_bridge_status")
                        .executes(ctx -> bakedModelBridgeStatus(ctx.getSource())))
                .then(Commands.literal("baked_model_bridge_audit")
                        .executes(ctx -> bakedModelBridgeAudit(ctx.getSource())))
                .then(Commands.literal("baked_model_bridge_audit_status")
                        .executes(ctx -> bakedModelBridgeAuditStatus(ctx.getSource())))
                .then(Commands.literal("baked_model_bridge_dump_sample")
                        .executes(ctx -> bakedModelBridgeDumpSample(ctx.getSource())))
                .then(Commands.literal("baked_model_bridge_clear")
                        .executes(ctx -> bakedModelBridgeClear(ctx.getSource())))
                .then(Commands.literal("model_store_real_sample_build")
                        .executes(ctx -> modelStoreRealSampleBuild(ctx.getSource())))
                .then(Commands.literal("model_store_real_sample_status")
                        .executes(ctx -> modelStoreRealSampleStatus(ctx.getSource())))
                .then(Commands.literal("model_store_real_sample_audit")
                        .executes(ctx -> modelStoreRealSampleAudit(ctx.getSource())))
                .then(Commands.literal("model_store_real_sample_audit_status")
                        .executes(ctx -> modelStoreRealSampleAuditStatus(ctx.getSource())))
                .then(Commands.literal("model_store_real_sample_dump")
                        .executes(ctx -> modelStoreRealSampleDump(ctx.getSource())))
                .then(Commands.literal("model_store_real_sample_clear")
                        .executes(ctx -> modelStoreRealSampleClear(ctx.getSource())))
                .then(Commands.literal("model_sample_set_build")
                        .executes(ctx -> modelSampleSetBuild(ctx.getSource())))
                .then(Commands.literal("model_sample_set_status")
                        .executes(ctx -> modelSampleSetStatus(ctx.getSource())))
                .then(Commands.literal("model_sample_set_audit")
                        .executes(ctx -> modelSampleSetAudit(ctx.getSource())))
                .then(Commands.literal("model_sample_set_audit_status")
                        .executes(ctx -> modelSampleSetAuditStatus(ctx.getSource())))
                .then(Commands.literal("model_sample_set_dump")
                        .executes(ctx -> modelSampleSetDump(ctx.getSource())))
                .then(Commands.literal("model_sample_set_clear")
                        .executes(ctx -> modelSampleSetClear(ctx.getSource())))
                .then(Commands.literal("model_atlas_skeleton_build")
                        .executes(ctx -> modelAtlasSkeletonBuild(ctx.getSource())))
                .then(Commands.literal("model_atlas_skeleton_status")
                        .executes(ctx -> modelAtlasSkeletonStatus(ctx.getSource())))
                .then(Commands.literal("model_atlas_skeleton_audit")
                        .executes(ctx -> modelAtlasSkeletonAudit(ctx.getSource())))
                .then(Commands.literal("model_atlas_skeleton_audit_status")
                        .executes(ctx -> modelAtlasSkeletonAuditStatus(ctx.getSource())))
                .then(Commands.literal("model_atlas_skeleton_dump_sample")
                        .executes(ctx -> modelAtlasSkeletonDumpSample(ctx.getSource())))
                .then(Commands.literal("model_atlas_skeleton_clear")
                        .executes(ctx -> modelAtlasSkeletonClear(ctx.getSource())))
                .then(Commands.literal("model_atlas_upload_sample")
                        .executes(ctx -> modelAtlasUploadSample(ctx.getSource())))
                .then(Commands.literal("model_atlas_upload_status")
                        .executes(ctx -> modelAtlasUploadStatus(ctx.getSource())))
                .then(Commands.literal("model_atlas_upload_audit")
                        .executes(ctx -> modelAtlasUploadAudit(ctx.getSource())))
                .then(Commands.literal("model_atlas_upload_audit_status")
                        .executes(ctx -> modelAtlasUploadAuditStatus(ctx.getSource())))
                .then(Commands.literal("model_atlas_upload_dump_sample")
                        .executes(ctx -> modelAtlasUploadDumpSample(ctx.getSource())))
                .then(Commands.literal("model_atlas_upload_clear")
                        .executes(ctx -> modelAtlasUploadClear(ctx.getSource())))
                .then(Commands.literal("model_atlas_upload_sample_set")
                        .executes(ctx -> modelAtlasUploadSampleSet(ctx.getSource())))
                .then(Commands.literal("model_atlas_upload_sample_set_status")
                        .executes(ctx -> modelAtlasUploadSampleSetStatus(ctx.getSource())))
                .then(Commands.literal("model_atlas_upload_sample_set_audit")
                        .executes(ctx -> modelAtlasUploadSampleSetAudit(ctx.getSource())))
                .then(Commands.literal("model_atlas_upload_sample_set_dump")
                        .executes(ctx -> modelAtlasUploadSampleSetDump(ctx.getSource())))
                .then(Commands.literal("formal_shader_input_bridge_build")
                        .executes(ctx -> formalShaderInputBridgeBuild(ctx.getSource())))
                .then(Commands.literal("formal_shader_input_bridge_status")
                        .executes(ctx -> formalShaderInputBridgeStatus(ctx.getSource())))
                .then(Commands.literal("formal_shader_input_bridge_audit")
                        .executes(ctx -> formalShaderInputBridgeAudit(ctx.getSource())))
                .then(Commands.literal("formal_shader_input_bridge_audit_status")
                        .executes(ctx -> formalShaderInputBridgeAuditStatus(ctx.getSource())))
                .then(Commands.literal("formal_shader_input_bridge_clear")
                        .executes(ctx -> formalShaderInputBridgeClear(ctx.getSource())))
                .then(Commands.literal("formal_model_store_build")
                        .executes(ctx -> formalModelStoreBuild(ctx.getSource())))
                .then(Commands.literal("formal_model_store_status")
                        .executes(ctx -> formalModelStoreStatus(ctx.getSource())))
                .then(Commands.literal("formal_model_store_audit")
                        .executes(ctx -> formalModelStoreAudit(ctx.getSource())))
                .then(Commands.literal("formal_model_store_audit_status")
                        .executes(ctx -> formalModelStoreAuditStatus(ctx.getSource())))
                .then(Commands.literal("formal_model_store_clear")
                        .executes(ctx -> formalModelStoreClear(ctx.getSource())))
                .then(Commands.literal("formal_model_store_dump_layout")
                        .executes(ctx -> formalModelStoreDumpLayout(ctx.getSource())))
                .then(Commands.literal("formal_model_factory_request_current")
                        .executes(ctx -> formalModelFactoryRequestCurrent(ctx.getSource())))
                .then(Commands.literal("formal_model_factory_request_blockstate")
                        .then(Commands.argument("blockStateId", IntegerArgumentType.integer())
                                .executes(ctx -> formalModelFactoryRequestBlockState(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "blockStateId")))))
                .then(Commands.literal("formal_model_factory_process_skeleton")
                        .executes(ctx -> formalModelFactoryProcessSkeleton(ctx.getSource())))
                .then(Commands.literal("formal_model_factory_status")
                        .executes(ctx -> formalModelFactoryStatus(ctx.getSource())))
                .then(Commands.literal("formal_model_factory_audit")
                        .executes(ctx -> formalModelFactoryAudit(ctx.getSource())))
                .then(Commands.literal("formal_model_factory_audit_status")
                        .executes(ctx -> formalModelFactoryAuditStatus(ctx.getSource())))
                .then(Commands.literal("formal_model_factory_clear")
                        .executes(ctx -> formalModelFactoryClear(ctx.getSource())))
                .then(Commands.literal("formal_model_factory_dump_mappings")
                        .executes(ctx -> formalModelFactoryDumpMappings(ctx.getSource())))
                .then(Commands.literal("formal_model_factory_bake_one_current")
                        .executes(ctx -> formalModelFactoryBakeOneCurrent(ctx.getSource())))
                .then(Commands.literal("formal_model_factory_bake_one_status")
                        .executes(ctx -> formalModelFactoryBakeOneStatus(ctx.getSource())))
                .then(Commands.literal("formal_model_factory_bake_one_audit")
                        .executes(ctx -> formalModelFactoryBakeOneAudit(ctx.getSource())))
                .then(Commands.literal("formal_model_factory_bake_one_dump")
                        .executes(ctx -> formalModelFactoryBakeOneDump(ctx.getSource())))
                .then(Commands.literal("formal_model_factory_bake_one_clear")
                        .executes(ctx -> formalModelFactoryBakeOneClear(ctx.getSource())))
                .then(Commands.literal("qa_i4_formal_bake_upload")
                        .executes(ctx -> qaI4FormalBakeUpload(ctx.getSource())))
                .then(Commands.literal("formal_model_factory_bake_multi_safe")
                        .executes(ctx -> formalModelFactoryBakeMultiSafe(ctx.getSource())))
                .then(Commands.literal("formal_model_factory_bake_multi_status")
                        .executes(ctx -> formalModelFactoryBakeMultiStatus(ctx.getSource())))
                .then(Commands.literal("formal_model_factory_bake_multi_audit")
                        .executes(ctx -> formalModelFactoryBakeMultiAudit(ctx.getSource())))
                .then(Commands.literal("formal_model_factory_bake_multi_dump")
                        .executes(ctx -> formalModelFactoryBakeMultiDump(ctx.getSource())))
                .then(Commands.literal("formal_model_factory_bake_multi_clear")
                        .executes(ctx -> formalModelFactoryBakeMultiClear(ctx.getSource())))
                .then(Commands.literal("qa_i5_multi_block_bake_upload")
                        .executes(ctx -> qaI5MultiBlockBakeUpload(ctx.getSource())))
                .then(Commands.literal("formal_model_bakery_lifecycle_status")
                        .executes(ctx -> formalModelBakeryLifecycleStatus(ctx.getSource())))
                .then(Commands.literal("formal_model_bakery_lifecycle_audit")
                        .executes(ctx -> formalModelBakeryLifecycleAudit(ctx.getSource())))
                .then(Commands.literal("formal_model_bakery_lifecycle_rebuild_safe_set")
                        .executes(ctx -> formalModelBakeryLifecycleRebuildSafeSet(ctx.getSource())))
                .then(Commands.literal("formal_model_bakery_lifecycle_dump")
                        .executes(ctx -> formalModelBakeryLifecycleDump(ctx.getSource())))
                .then(Commands.literal("formal_model_bakery_lifecycle_clear")
                        .executes(ctx -> formalModelBakeryLifecycleClear(ctx.getSource())))
                .then(Commands.literal("qa_i6_model_lifecycle_rebuild")
                        .executes(ctx -> qaI6ModelLifecycleRebuild(ctx.getSource())))
                .then(Commands.literal("formal_shader_input_build")
                        .executes(ctx -> formalShaderInputBuild(ctx.getSource())))
                .then(Commands.literal("formal_shader_input_status")
                        .executes(ctx -> formalShaderInputStatus(ctx.getSource())))
                .then(Commands.literal("formal_shader_input_audit")
                        .executes(ctx -> formalShaderInputAudit(ctx.getSource())))
                .then(Commands.literal("formal_shader_input_dump")
                        .executes(ctx -> formalShaderInputDump(ctx.getSource())))
                .then(Commands.literal("formal_shader_input_clear")
                        .executes(ctx -> formalShaderInputClear(ctx.getSource())))
                .then(Commands.literal("qa_j1_formal_shader_input")
                        .executes(ctx -> qaJ1FormalShaderInput(ctx.getSource())))
                .then(Commands.literal("formal_shader_program_build")
                        .executes(ctx -> formalShaderProgramBuild(ctx.getSource())))
                .then(Commands.literal("formal_shader_program_status")
                        .executes(ctx -> formalShaderProgramStatus(ctx.getSource())))
                .then(Commands.literal("formal_shader_program_audit")
                        .executes(ctx -> formalShaderProgramAudit(ctx.getSource())))
                .then(Commands.literal("formal_shader_program_dump")
                        .executes(ctx -> formalShaderProgramDump(ctx.getSource())))
                .then(Commands.literal("formal_shader_program_clear")
                        .executes(ctx -> formalShaderProgramClear(ctx.getSource())))
                .then(Commands.literal("qa_j2_formal_shader_program")
                        .executes(ctx -> qaJ2FormalShaderProgram(ctx.getSource())))
                .then(Commands.literal("formal_textured_shader_build")
                        .executes(ctx -> formalTexturedShaderBuild(ctx.getSource())))
                .then(Commands.literal("formal_textured_shader_status")
                        .executes(ctx -> formalTexturedShaderStatus(ctx.getSource())))
                .then(Commands.literal("formal_textured_shader_audit")
                        .executes(ctx -> formalTexturedShaderAudit(ctx.getSource())))
                .then(Commands.literal("formal_textured_shader_dump")
                        .executes(ctx -> formalTexturedShaderDump(ctx.getSource())))
                .then(Commands.literal("formal_textured_shader_clear")
                        .executes(ctx -> formalTexturedShaderClear(ctx.getSource())))
                .then(Commands.literal("formal_textured_shader_preview_enable")
                        .executes(ctx -> formalTexturedShaderPreviewEnable(ctx.getSource())))
                .then(Commands.literal("formal_textured_shader_preview_disable")
                        .executes(ctx -> formalTexturedShaderPreviewDisable(ctx.getSource())))
                .then(Commands.literal("qa_j3_formal_textured_shader_preview")
                        .executes(ctx -> qaJ3FormalTexturedShaderPreview(ctx.getSource())))
                .then(Commands.literal("formal_packed_quad_preview_build")
                        .executes(ctx -> formalPackedQuadPreviewBuild(ctx.getSource())))
                .then(Commands.literal("formal_packed_quad_preview_status")
                        .executes(ctx -> formalPackedQuadPreviewStatus(ctx.getSource())))
                .then(Commands.literal("formal_packed_quad_preview_audit")
                        .executes(ctx -> formalPackedQuadPreviewAudit(ctx.getSource())))
                .then(Commands.literal("formal_packed_quad_preview_dump")
                        .executes(ctx -> formalPackedQuadPreviewDump(ctx.getSource())))
                .then(Commands.literal("formal_packed_quad_preview_clear")
                        .executes(ctx -> formalPackedQuadPreviewClear(ctx.getSource())))
                .then(Commands.literal("qa_j4_formal_packed_quad_preview")
                        .executes(ctx -> qaJ4FormalPackedQuadPreview(ctx.getSource())))
                .then(Commands.literal("formal_terrain_record_bridge_build")
                        .executes(ctx -> formalTerrainRecordBridgeBuild(ctx.getSource())))
                .then(Commands.literal("formal_terrain_record_bridge_status")
                        .executes(ctx -> formalTerrainRecordBridgeStatus(ctx.getSource())))
                .then(Commands.literal("formal_terrain_record_bridge_audit")
                        .executes(ctx -> formalTerrainRecordBridgeAudit(ctx.getSource())))
                .then(Commands.literal("formal_terrain_record_bridge_dump")
                        .executes(ctx -> formalTerrainRecordBridgeDump(ctx.getSource())))
                .then(Commands.literal("formal_terrain_record_bridge_clear")
                        .executes(ctx -> formalTerrainRecordBridgeClear(ctx.getSource())))
                .then(Commands.literal("qa_j5_real_terrain_record_bridge")
                        .executes(ctx -> qaJ5RealTerrainRecordBridge(ctx.getSource())))
                .then(Commands.literal("formal_terrain_renderer_owner_enable")
                        .executes(ctx -> formalTerrainRendererOwnerEnable(ctx.getSource())))
                .then(Commands.literal("formal_terrain_renderer_owner_status")
                        .executes(ctx -> formalTerrainRendererOwnerStatus(ctx.getSource())))
                .then(Commands.literal("formal_terrain_renderer_owner_check")
                        .executes(ctx -> formalTerrainRendererOwnerCheck(ctx.getSource())))
                .then(Commands.literal("formal_terrain_renderer_owner_audit")
                        .executes(ctx -> formalTerrainRendererOwnerAudit(ctx.getSource())))
                .then(Commands.literal("formal_terrain_renderer_owner_dump")
                        .executes(ctx -> formalTerrainRendererOwnerDump(ctx.getSource())))
                .then(Commands.literal("formal_terrain_renderer_owner_clear")
                        .executes(ctx -> formalTerrainRendererOwnerClear(ctx.getSource())))
                .then(Commands.literal("qa_k1_formal_terrain_renderer_owner")
                        .executes(ctx -> qaK1FormalTerrainRendererOwner(ctx.getSource())))
                .then(Commands.literal("formal_mdic_viewport_owner_enable")
                        .executes(ctx -> formalMdicViewportOwnerEnable(ctx.getSource())))
                .then(Commands.literal("formal_mdic_viewport_owner_status")
                        .executes(ctx -> formalMdicViewportOwnerStatus(ctx.getSource())))
                .then(Commands.literal("formal_mdic_viewport_owner_check")
                        .executes(ctx -> formalMdicViewportOwnerCheck(ctx.getSource())))
                .then(Commands.literal("formal_mdic_viewport_owner_audit")
                        .executes(ctx -> formalMdicViewportOwnerAudit(ctx.getSource())))
                .then(Commands.literal("formal_mdic_viewport_owner_dump")
                        .executes(ctx -> formalMdicViewportOwnerDump(ctx.getSource())))
                .then(Commands.literal("formal_mdic_viewport_owner_clear")
                        .executes(ctx -> formalMdicViewportOwnerClear(ctx.getSource())))
                .then(Commands.literal("qa_k2_formal_mdic_viewport_owner")
                        .executes(ctx -> qaK2FormalMdicViewportOwner(ctx.getSource())))
                .then(Commands.literal("formal_renderer_check")
                        .executes(ctx -> formalRendererCheck(ctx.getSource())))
                .then(Commands.literal("formal_renderer_status")
                        .executes(ctx -> formalRendererStatus(ctx.getSource())))
                .then(Commands.literal("formal_renderer_enable")
                        .executes(ctx -> formalRendererEnable(ctx.getSource())))
                .then(Commands.literal("formal_renderer_disable")
                        .executes(ctx -> formalRendererDisable(ctx.getSource())))
                .then(Commands.literal("formal_renderer_clear")
                        .executes(ctx -> formalRendererClear(ctx.getSource())))
                .then(Commands.literal("formal_renderer_dump_blockers")
                        .executes(ctx -> formalRendererDumpBlockers(ctx.getSource())))
                .then(Commands.literal("textured_debug_quad_build_sample")
                        .executes(ctx -> texturedDebugQuadBuildSample(ctx.getSource())))
                .then(Commands.literal("textured_debug_quad_enable")
                        .executes(ctx -> texturedDebugQuadEnable(ctx.getSource())))
                .then(Commands.literal("textured_debug_quad_disable")
                        .executes(ctx -> texturedDebugQuadDisable(ctx.getSource())))
                .then(Commands.literal("textured_debug_quad_status")
                        .executes(ctx -> texturedDebugQuadStatus(ctx.getSource())))
                .then(Commands.literal("textured_debug_quad_clear")
                        .executes(ctx -> texturedDebugQuadClear(ctx.getSource())))
                .then(Commands.literal("textured_readback_build_sample")
                        .executes(ctx -> texturedReadbackBuildSample(ctx.getSource())))
                .then(Commands.literal("textured_readback_enable")
                        .executes(ctx -> texturedReadbackEnable(ctx.getSource())))
                .then(Commands.literal("textured_readback_disable")
                        .executes(ctx -> texturedReadbackDisable(ctx.getSource())))
                .then(Commands.literal("textured_readback_status")
                        .executes(ctx -> texturedReadbackStatus(ctx.getSource())))
                .then(Commands.literal("textured_readback_clear")
                        .executes(ctx -> texturedReadbackClear(ctx.getSource())))
                .then(Commands.literal("textured_mdic_debug_build")
                        .executes(ctx -> texturedMdicDebugBuild(ctx.getSource())))
                .then(Commands.literal("textured_mdic_debug_input_mode")
                        .then(Commands.literal("sample_set")
                                .executes(ctx -> texturedMdicDebugInputMode(ctx.getSource(), ForgeTexturedMdicDebugInputMode.SAMPLE_SET_DIRECT)))
                        .then(Commands.literal("formal_bridge")
                                .executes(ctx -> texturedMdicDebugInputMode(ctx.getSource(), ForgeTexturedMdicDebugInputMode.FORMAL_INPUT_BRIDGE))))
                .then(Commands.literal("textured_mdic_debug_enable")
                        .executes(ctx -> texturedMdicDebugEnable(ctx.getSource())))
                .then(Commands.literal("textured_mdic_debug_disable")
                        .executes(ctx -> texturedMdicDebugDisable(ctx.getSource())))
                .then(Commands.literal("textured_mdic_debug_status")
                        .executes(ctx -> texturedMdicDebugStatus(ctx.getSource())))
                .then(Commands.literal("textured_mdic_debug_clear")
                        .executes(ctx -> texturedMdicDebugClear(ctx.getSource())))
                .then(Commands.literal("mesh_cache_status")
                        .executes(ctx -> meshCacheStatus(ctx.getSource())))
                .then(Commands.literal("mesh_cache_clear")
                        .executes(ctx -> clearMeshCache(ctx.getSource())))
                .then(Commands.literal("gpu_mesh_status")
                        .executes(ctx -> gpuMeshStatus(ctx.getSource())))
                .then(Commands.literal("gpu_mesh_source")
                        .then(Commands.literal("cpu")
                                .executes(ctx -> setGpuMeshSource(ctx.getSource(), SimpleGpuMeshSource.CPU_MESH)))
                        .then(Commands.literal("built_section")
                                .executes(ctx -> setGpuMeshSource(ctx.getSource(), SimpleGpuMeshSource.BUILT_SECTION)))
                        .then(Commands.literal("gl_heap_readback")
                                .executes(ctx -> setGpuMeshSource(ctx.getSource(), SimpleGpuMeshSource.GL_HEAP_READBACK))))
                .then(Commands.literal("lod_visibility_status")
                        .executes(ctx -> lodVisibilityStatus(ctx.getSource())))
                .then(Commands.literal("lod_overlay_debug")
                        .executes(ctx -> lodOverlayDebug(ctx.getSource())))
                .then(Commands.literal("lod_mode_advice")
                        .executes(ctx -> lodModeAdvice(ctx.getSource())))
                .then(Commands.literal("preset")
                        .then(Commands.literal("off")
                                .executes(ctx -> applyPresetOff(ctx.getSource())))
                        .then(Commands.literal("overlay")
                                .executes(ctx -> applyPresetOverlay(ctx.getSource())))
                        .then(Commands.literal("lod")
                                .executes(ctx -> applyPresetLod(ctx.getSource())))
                        .then(Commands.literal("lod_built_section")
                                .executes(ctx -> applyPresetLodBuiltSection(ctx.getSource())))
                        .then(Commands.literal("geometry_manager")
                                .executes(ctx -> applyPresetGeometryManager(ctx.getSource())))
                        .then(Commands.literal("gl_heap_visualize")
                                .executes(ctx -> applyPresetGlHeapVisualize(ctx.getSource())))
                        .then(Commands.literal("gl_heap_readback")
                                .executes(ctx -> applyPresetGlHeapReadback(ctx.getSource())))
                        .then(Commands.literal("direct_gl_debug")
                                .executes(ctx -> applyPresetDirectGlDebug(ctx.getSource())))
                        .then(Commands.literal("mdic_skeleton")
                                .executes(ctx -> applyPresetMdicSkeleton(ctx.getSource())))
                        .then(Commands.literal("mdic_debug")
                                .executes(ctx -> applyPresetMdicDebug(ctx.getSource())))
                        .then(Commands.literal("formal_renderer_skeleton")
                                .executes(ctx -> applyPresetFormalRendererSkeleton(ctx.getSource())))
                        .then(Commands.literal("formal_model_store_skeleton")
                                .executes(ctx -> applyPresetFormalModelStoreSkeleton(ctx.getSource())))
                        .then(Commands.literal("formal_model_factory_skeleton")
                                .executes(ctx -> applyPresetFormalModelFactorySkeleton(ctx.getSource())))
                        .then(Commands.literal("formal_one_block_bake_upload")
                                .executes(ctx -> applyPresetFormalOneBlockBakeUpload(ctx.getSource())))
                        .then(Commands.literal("formal_multi_block_bake_upload")
                                .executes(ctx -> applyPresetFormalMultiBlockBakeUpload(ctx.getSource())))
                        .then(Commands.literal("formal_model_lifecycle_rebuild")
                                .executes(ctx -> applyPresetFormalModelLifecycleRebuild(ctx.getSource())))
                        .then(Commands.literal("formal_shader_input_skeleton")
                                .executes(ctx -> applyPresetFormalShaderInputSkeleton(ctx.getSource())))
                        .then(Commands.literal("formal_shader_program_validation")
                                .executes(ctx -> applyPresetFormalShaderProgramValidation(ctx.getSource())))
                        .then(Commands.literal("formal_textured_shader_preview")
                                .executes(ctx -> applyPresetFormalTexturedShaderPreview(ctx.getSource())))
                        .then(Commands.literal("formal_packed_quad_preview")
                                .executes(ctx -> applyPresetFormalPackedQuadPreview(ctx.getSource())))
                        .then(Commands.literal("formal_terrain_record_bridge")
                                .executes(ctx -> applyPresetFormalTerrainRecordBridge(ctx.getSource())))
                        .then(Commands.literal("formal_terrain_renderer_owner_no_draw")
                                .executes(ctx -> applyPresetFormalTerrainRendererOwnerNoDraw(ctx.getSource())))
                        .then(Commands.literal("formal_mdic_viewport_owner_no_draw")
                                .executes(ctx -> applyPresetFormalMdicViewportOwnerNoDraw(ctx.getSource())))
                        .then(Commands.literal("clear")
                                .executes(ctx -> clearPreset(ctx.getSource())))
                        .then(Commands.literal("status")
                                .executes(ctx -> presetStatus(ctx.getSource()))))
                .then(Commands.literal("gpu_mesh_clear")
                        .executes(ctx -> clearGpuMeshCache(ctx.getSource())))
                .then(Commands.literal("mesh_build_clear")
                        .executes(ctx -> clearMeshBuildState(ctx.getSource())))
                .then(Commands.literal("debug_pipeline_status")
                        .executes(ctx -> meshCacheStatus(ctx.getSource())))
                .then(Commands.literal("debug_pipeline_clear")
                        .executes(ctx -> clearDebugPipeline(ctx.getSource())))
                .then(Commands.literal("ingest_status")
                        .executes(ctx -> ingestStatus(ctx.getSource())))
                .then(Commands.literal("ingest_clear_cache")
                        .executes(ctx -> clearIngestCache(ctx.getSource()))));
    }

    private static int ingestCurrentChunk(CommandSourceStack source) {
        var minecraft = Minecraft.getInstance();
        var player = minecraft.player;
        var level = minecraft.level;
        if (player == null || level == null) {
            source.sendFailure(Component.literal("Voxy: no client world is active."));
            return 0;
        }

        var engine = ForgeVoxyInstance.INSTANCE.getCurrentEngineOptional();
        if (engine.isEmpty()) {
            String reason = ForgeVoxyRuntimeOverrides.enabledWorldEngineSkeleton()
                    ? "no WorldEngine is active for the current world"
                    : "enableWorldEngineSkeleton is false";
            source.sendFailure(Component.literal("Voxy: cannot ingest current chunk; " + reason + "."));
            return 0;
        }

        try {
            long start = System.nanoTime();
            int chunkX = player.chunkPosition().x;
            int chunkZ = player.chunkPosition().z;
            LevelChunk chunk = level.getChunk(chunkX, chunkZ);
            if (chunk == null) {
                source.sendFailure(Component.literal("Voxy: current chunk is not loaded."));
                return 0;
            }

            int storageWritesBefore = ForgeVoxyInstance.INSTANCE.getStorageWriteCount();
            var stats = VoxelIngestService.ingestChunkWithStats(engine.get(), chunk);
            int storageWrites = ForgeVoxyInstance.INSTANCE.getStorageWriteCount() - storageWritesBefore;
            stats = stats.withStorageWrites(storageWrites);
            double elapsedMs = (System.nanoTime() - start) / 1_000_000.0;
            String dimension = level.dimension().location().toString();
            String message = String.format(
                    "Voxy: ingested %s chunk %d,%d: converted=%d nonAirSections=%d nonAirVoxels=%d worldUpdates=%d storageWrites=%d elapsed=%.2fms",
                    dimension,
                    chunkX,
                    chunkZ,
                    stats.convertedSections(),
                    stats.nonAirSections(),
                    stats.nonAirVoxels(),
                    stats.worldUpdates(),
                    stats.storageWrites(),
                    elapsedMs
            );
            VoxyForge.LOGGER.info(message);
            source.sendSuccess(() -> Component.literal(message), false);
            return stats.convertedSections();
        } catch (Exception e) {
            VoxyForge.LOGGER.error("Failed to ingest current chunk", e);
            source.sendFailure(Component.literal("Voxy: chunk ingest failed: " + e.getMessage()));
            return 0;
        }
    }

    private static int buildCurrentChunkMesh(CommandSourceStack source) {
        var minecraft = Minecraft.getInstance();
        var player = minecraft.player;
        var level = minecraft.level;
        if (player == null || level == null) {
            source.sendFailure(Component.literal("Voxy: no client world is active."));
            return 0;
        }

        var engine = ForgeVoxyInstance.INSTANCE.getCurrentEngineOptional();
        if (engine.isEmpty()) {
            String reason = ForgeVoxyRuntimeOverrides.enabledWorldEngineSkeleton()
                    ? "no WorldEngine is active for the current world"
                    : "enableWorldEngineSkeleton is false";
            source.sendFailure(Component.literal("Voxy: cannot build current chunk mesh; " + reason + "."));
            return 0;
        }

        try {
            int chunkX = player.chunkPosition().x;
            int chunkZ = player.chunkPosition().z;
            LevelChunk chunk = level.getChunkSource().getChunk(chunkX, chunkZ, ChunkStatus.FULL, false);
            if (chunk == null) {
                source.sendFailure(Component.literal("Voxy: current chunk is not loaded."));
                return 0;
            }

            String dimension = level.dimension().location().toString();
            var stats = ForgeOffscreenMeshBuildValidator.buildCurrentChunk(engine.get(), chunk, dimension);
            if (stats.sectionsFound() == 0) {
                source.sendFailure(Component.literal("Voxy: no ingested Voxy section found for current chunk; run /voxy ingest_current_chunk first."));
                return 0;
            }

            String message = String.format(
                    "Voxy mesh: %s chunk %d,%d sectionsFound=%d sectionsBuilt=%d nonAirVoxels=%d quads=%d vertices=%d bytes=%d elapsed=%.2fms",
                    stats.dimension(),
                    stats.chunkX(),
                    stats.chunkZ(),
                    stats.sectionsFound(),
                    stats.sectionsBuilt(),
                    stats.nonAirVoxels(),
                    stats.quads(),
                    stats.vertices(),
                    stats.estimatedBytes(),
                    stats.elapsedMs()
            );
            VoxyForge.LOGGER.info(message);
            source.sendSuccess(() -> Component.literal(message), false);
            return stats.sectionsBuilt();
        } catch (Exception e) {
            VoxyForge.LOGGER.error("Failed to build current chunk mesh stats", e);
            source.sendFailure(Component.literal("Voxy: mesh build failed: " + e.getMessage()));
            return 0;
        }
    }

    private static int buildCurrentChunkModelMesh(CommandSourceStack source) {
        var minecraft = Minecraft.getInstance();
        var player = minecraft.player;
        var level = minecraft.level;
        if (player == null || level == null) {
            source.sendFailure(Component.literal("Voxy: no client world is active."));
            return 0;
        }

        var engine = ForgeVoxyInstance.INSTANCE.getCurrentEngineOptional();
        if (engine.isEmpty()) {
            String reason = ForgeVoxyRuntimeOverrides.enabledWorldEngineSkeleton()
                    ? "no WorldEngine is active for the current world"
                    : "enableWorldEngineSkeleton is false";
            source.sendFailure(Component.literal("Voxy: cannot build current chunk model mesh; " + reason + "."));
            return 0;
        }

        try {
            int chunkX = player.chunkPosition().x;
            int chunkZ = player.chunkPosition().z;
            LevelChunk chunk = level.getChunkSource().getChunk(chunkX, chunkZ, ChunkStatus.FULL, false);
            if (chunk == null) {
                source.sendFailure(Component.literal("Voxy: current chunk is not loaded."));
                return 0;
            }

            String dimension = level.dimension().location().toString();
            var stats = ForgeModelAwareMeshBuildValidator.buildCurrentChunk(engine.get(), chunk, level, dimension);
            if (stats.sectionsFound() == 0) {
                source.sendFailure(Component.literal("Voxy: no ingested Voxy section found for current chunk; run /voxy ingest_current_chunk first."));
                return 0;
            }

            String message = String.format(
                    "Voxy model mesh: %s chunk %d,%d sectionsFound=%d sectionsBuilt=%d blocksSampled=%d bakedModels=%d quads=%d vertices=%d tinted=%d tintLookups=%d solid=%d cutout=%d translucent=%d other=%d unsupported=%d bytes=%d elapsed=%.2fms",
                    stats.dimension(),
                    stats.chunkX(),
                    stats.chunkZ(),
                    stats.sectionsFound(),
                    stats.sectionsBuilt(),
                    stats.blocksSampled(),
                    stats.bakedModelCount(),
                    stats.quads(),
                    stats.vertices(),
                    stats.tintedQuads(),
                    stats.tintLookups(),
                    stats.solidQuads(),
                    stats.cutoutQuads(),
                    stats.translucentQuads(),
                    stats.otherLayerQuads(),
                    stats.unsupportedBlocks(),
                    stats.estimatedBytes(),
                    stats.elapsedMs()
            );
            VoxyForge.LOGGER.info(message);
            source.sendSuccess(() -> Component.literal(message), false);
            return stats.sectionsBuilt();
        } catch (Exception e) {
            VoxyForge.LOGGER.error("Failed to build current chunk model mesh stats", e);
            source.sendFailure(Component.literal("Voxy: model mesh build failed: " + e.getMessage()));
            return 0;
        }
    }

    private static int buildCurrentChunkCpuMesh(CommandSourceStack source) {
        var minecraft = Minecraft.getInstance();
        var player = minecraft.player;
        var level = minecraft.level;
        if (player == null || level == null) {
            source.sendFailure(Component.literal("Voxy: no client world is active."));
            return 0;
        }

        var engine = ForgeVoxyInstance.INSTANCE.getCurrentEngineOptional();
        if (engine.isEmpty()) {
            String reason = ForgeVoxyRuntimeOverrides.enabledWorldEngineSkeleton()
                    ? "no WorldEngine is active for the current world"
                    : "enableWorldEngineSkeleton is false";
            source.sendFailure(Component.literal("Voxy: cannot build current chunk CPU mesh; " + reason + "."));
            return 0;
        }

        try {
            int chunkX = player.chunkPosition().x;
            int chunkZ = player.chunkPosition().z;
            LevelChunk chunk = level.getChunkSource().getChunk(chunkX, chunkZ, ChunkStatus.FULL, false);
            if (chunk == null) {
                source.sendFailure(Component.literal("Voxy: current chunk is not loaded."));
                return 0;
            }

            String dimension = level.dimension().location().toString();
            var result = ForgeCpuMeshBuilder.buildCurrentChunk(engine.get(), chunk, level, dimension);
            var stats = result.stats();
            if (stats.sectionsFound() == 0) {
                source.sendFailure(Component.literal("Voxy: no ingested Voxy section found for current chunk; run /voxy ingest_current_chunk first."));
                return 0;
            }

            var cache = ForgeVoxyInstance.INSTANCE.getCpuMeshCache();
            cache.setActiveDimension(dimension);
            int cacheEntriesWritten = cache.putAll(result.sections());
            stats = stats.withCacheEntriesWritten(cacheEntriesWritten);
            var cacheStatus = cache.createStatusSnapshot();
            String message = String.format(
                    "Voxy CPU mesh: %s chunk %d,%d sectionsBuilt=%d layers=%s quads=%d vertices=%d bytes=%d cacheWritten=%d cacheEntries=%d elapsed=%.2fms",
                    stats.dimension(),
                    stats.chunkX(),
                    stats.chunkZ(),
                    stats.sectionsBuilt(),
                    stats.layerSummary(),
                    stats.quads(),
                    stats.vertices(),
                    stats.estimatedBytes(),
                    stats.cacheEntriesWritten(),
                    cacheStatus.entries(),
                    stats.elapsedMs()
            );
            VoxyForge.LOGGER.info(message);
            source.sendSuccess(() -> Component.literal(message), false);
            return stats.sectionsBuilt();
        } catch (OutOfMemoryError e) {
            VoxyForge.LOGGER.error("Failed to allocate current chunk CPU mesh", e);
            source.sendFailure(Component.literal("Voxy: CPU mesh build ran out of memory."));
            return 0;
        } catch (Exception e) {
            VoxyForge.LOGGER.error("Failed to build current chunk CPU mesh", e);
            source.sendFailure(Component.literal("Voxy: CPU mesh build failed: " + e.getMessage()));
            return 0;
        }
    }

    private static int buildCurrentChunkBuiltSection(CommandSourceStack source) {
        var minecraft = Minecraft.getInstance();
        var player = minecraft.player;
        var level = minecraft.level;
        if (player == null || level == null) {
            source.sendFailure(Component.literal("Voxy: no client world is active."));
            return 0;
        }

        var engine = ForgeVoxyInstance.INSTANCE.getCurrentEngineOptional();
        if (engine.isEmpty()) {
            String reason = ForgeVoxyRuntimeOverrides.enabledWorldEngineSkeleton()
                    ? "no WorldEngine is active for the current world"
                    : "enableWorldEngineSkeleton is false";
            source.sendFailure(Component.literal("Voxy: cannot build current chunk BuiltSection data; " + reason + "."));
            return 0;
        }

        ForgeCpuMeshBuildResult cpuResult = null;
        try {
            long start = System.nanoTime();
            int chunkX = player.chunkPosition().x;
            int chunkZ = player.chunkPosition().z;
            LevelChunk chunk = level.getChunkSource().getChunk(chunkX, chunkZ, ChunkStatus.FULL, false);
            if (chunk == null) {
                source.sendFailure(Component.literal("Voxy: current chunk is not loaded."));
                return 0;
            }

            String dimension = level.dimension().location().toString();
            cpuResult = ForgeCpuMeshBuilder.buildCurrentChunk(engine.get(), chunk, level, dimension);
            var cpuStats = cpuResult.stats();
            if (cpuStats.sectionsFound() == 0) {
                source.sendFailure(Component.literal("Voxy: no ingested Voxy section found for current chunk; run /voxy ingest_current_chunk first."));
                return 0;
            }
            if (cpuResult.sections().isEmpty()) {
                source.sendFailure(Component.literal("Voxy: CPU mesh builder produced no section mesh for current chunk."));
                return 0;
            }

            var builtResult = ForgeVoxyBuiltSectionBuilder.fromCpuMesh(cpuResult);
            var cache = ForgeVoxyInstance.INSTANCE.getVoxyGeometryCache();
            cache.setActiveDimension(dimension);
            int cacheEntriesWritten = cache.putAll(builtResult.sections());
            double elapsedMs = (System.nanoTime() - start) / 1_000_000.0;
            var stats = builtResult.stats()
                    .withCacheEntriesWritten(cacheEntriesWritten)
                    .withElapsedMs(elapsedMs);
            var cacheStatus = cache.createStatusSnapshot();
            String message = String.format(
                    "Voxy BuiltSection CPU: %s chunk %d,%d sourceCpuEntries=%d emptySections=%d sectionsBuilt=%d builtSectionCount=%d naiveQuads=%d mergedQuads=%d quadsAfterMerge=%d mergeRatio=%.3f averageQuadArea=%.2f maxQuadLength=%d maxQuadWidth=%d skippedTranslucent=%d skippedNonMergeable=%d quads=%d geometryBytes=%d occupancyBytes=%d offsetsSemantic=%s geometryFormat=%s knownBits=%s knownFields=%s uniqueModelIds=%d missingModelIds=%d modelIdOverflow=%d uniqueBiomeIds=%d missingBiomeIds=%d biomeIdOverflow=%d missingTexture=%d missingGreedy=%d finalFormat=%s occupancyPresent=%s samplePosition=%s sampleAabb=%s sampleRecord=%s decoded=\"%s\" sampleMergedRecord=%s sampleMergedDecoded=\"%s\" offsets=%s namedOffsets=%s cacheWritten=%d cacheEntries=%d elapsed=%.2fms",
                    stats.dimension(),
                    stats.chunkX(),
                    stats.chunkZ(),
                    stats.sourceCpuEntries(),
                    stats.emptySections(),
                    cpuStats.sectionsBuilt(),
                    stats.sectionsBuilt(),
                    stats.naiveQuads(),
                    stats.mergedQuads(),
                    stats.quadsAfterMerge(),
                    stats.mergeRatio(),
                    stats.averageQuadArea(),
                    stats.maxQuadLength(),
                    stats.maxQuadWidth(),
                    stats.skippedTranslucent(),
                    stats.skippedNonMergeable(),
                    stats.totalQuads(),
                    stats.geometryBytes(),
                    stats.occupancyBytes(),
                    stats.offsetsSemantic(),
                    stats.geometryFormat(),
                    stats.knownBitsMask(),
                    stats.knownFields(),
                    stats.uniqueModelIds(),
                    stats.missingModelId(),
                    stats.modelIdOverflow(),
                    stats.uniqueBiomeIds(),
                    stats.missingBiomeId(),
                    stats.biomeIdOverflow(),
                    stats.missingTexture(),
                    stats.missingGreedy(),
                    stats.finalRendererFormat(),
                    stats.occupancyPresent(),
                    stats.samplePosition(),
                    stats.aabbSample(),
                    stats.sampleRecordHex(),
                    stats.sampleDecodedRecord(),
                    stats.sampleMergedRecordHex(),
                    stats.sampleMergedDecodedRecord(),
                    stats.offsetsSummary(),
                    stats.offsetsNamed(),
                    stats.cacheEntriesWritten(),
                    cacheStatus.entries(),
                    stats.elapsedMs()
            );
            VoxyForge.LOGGER.info(message);
            source.sendSuccess(() -> Component.literal(message + " (partial adapter format; not final renderer format)"), false);
            return stats.sectionsBuilt();
        } catch (OutOfMemoryError e) {
            VoxyForge.LOGGER.error("Failed to allocate current chunk BuiltSection data", e);
            source.sendFailure(Component.literal("Voxy: BuiltSection build ran out of memory."));
            return 0;
        } catch (Exception e) {
            VoxyForge.LOGGER.error("Failed to build current chunk BuiltSection data", e);
            source.sendFailure(Component.literal("Voxy: BuiltSection build failed: " + e.getMessage()));
            return 0;
        } finally {
            closeTemporaryCpuMesh(cpuResult);
        }
    }

    private static int builtSectionCacheStatus(CommandSourceStack source) {
        var status = ForgeVoxyInstance.INSTANCE.getVoxyGeometryCache().createStatusSnapshot();
        var buildStatus = ForgeVoxyInstance.INSTANCE.getBuiltSectionBuildManager().createStatusSnapshot();
        var gpuStatus = ForgeVoxyInstance.INSTANCE.getGpuMeshCache().createStatusSnapshot();
        var uploadStatus = ForgeVoxyInstance.INSTANCE.getGpuMeshUploadManager().getLastStatus();
        boolean gpuUsesBuiltSectionCache = ForgeGpuMeshUploadManager.getConfiguredSource() == SimpleGpuMeshSource.BUILT_SECTION;
        String buildDimension = buildStatus.dimension() == null ? "none" : buildStatus.dimension();
        String message = String.format(
                "Voxy BuiltSection cache: recommendedSource=%s fallbackSource=%s gpuUsesThisCache=%s currentSource=%s currentSourceRole=%s auto=%s engine=%s buildDim=%s queue=%d builtRecords=%d failedRecords=%d radius=%d maxPerTick=%d cooldown=%d lastBuildMs=%.2f avgBuildMs=%.2f estimatedGpuUploadedFromBuiltSections=%d lastConsumerSource=%s lastConsumerPending=%d lastConsumerUploaded=%d lastConsumerFailed=%d lastDecodeMs=%.2f avgDecodeMs=%.2f entries=%d/%d totalSections=%d totalNaiveQuads=%d totalMergedQuads=%d totalAverageQuadArea=%.2f totalSkippedTranslucent=%d totalSkippedNonMergeable=%d totalQuads=%d totalGeometryBytes=%d totalOccupancyBytes=%d finalFormatCount=%d partialFormatCount=%d partialOriginalBitLayoutCount=%d uniqueModelIds=%d missingModelRecords=%d runtimeModelMapperSize=%d uniqueBiomeIds=%d missingBiomeRecords=%d geometryFormat=%s closed=%d evicted=%d replaced=%d firstPosition=%s firstAabb=%s firstOffsets=%s firstNamedOffsets=%s offsetsSemantic=%s sampleRecord=%s decoded=\"%s\"",
                RECOMMENDED_MESH_SOURCE,
                FALLBACK_MESH_SOURCE,
                gpuUsesBuiltSectionCache,
                ForgeGpuMeshUploadManager.getConfiguredSource(),
                describeMeshSource(ForgeGpuMeshUploadManager.getConfiguredSource()),
                buildStatus.autoEnabled(),
                buildStatus.enginePresent(),
                buildDimension,
                buildStatus.queuedChunks(),
                buildStatus.builtChunks(),
                buildStatus.failedChunks(),
                buildStatus.radius(),
                buildStatus.maxChunksPerTick(),
                buildStatus.cooldownTicks(),
                buildStatus.lastBuildMs(),
                buildStatus.averageMs(),
                gpuUsesBuiltSectionCache ? gpuStatus.buffers() : 0,
                uploadStatus.source(),
                uploadStatus.pendingUploads(),
                uploadStatus.uploadedThisFrame(),
                uploadStatus.failedThisFrame(),
                uploadStatus.lastBuiltSectionDecodeMs(),
                uploadStatus.averageBuiltSectionDecodeMs(),
                status.entries(),
                status.maxEntries(),
                status.entries(),
                status.totalNaiveQuads(),
                status.totalMergedQuads(),
                status.totalAverageQuadArea(),
                status.totalSkippedTranslucent(),
                status.totalSkippedNonMergeable(),
                status.totalQuads(),
                status.totalGeometryBytes(),
                status.totalOccupancyBytes(),
                status.finalFormatEntries(),
                status.partialFormatEntries(),
                status.partialOriginalBitLayoutEntries(),
                status.totalUniqueModelIds(),
                status.missingModelRecords(),
                status.runtimeModelMapperSize(),
                status.totalUniqueBiomeIds(),
                status.missingBiomeRecords(),
                status.geometryFormat(),
                status.closedCount(),
                status.evictedCount(),
                status.replacedCount(),
                status.firstEntryPosition(),
                status.firstEntryAabb(),
                status.firstEntryOffsets(),
                status.firstEntryNamedOffsets(),
                ForgeVoxyBuiltSectionBuilder.OFFSETS_SEMANTIC,
                status.sampleRecordHex(),
                status.sampleDecodedRecord()
        );
        message = message + modelStoreFormalLayoutStatusSuffix();
        String displayMessage = message;
        source.sendSuccess(() -> Component.literal(displayMessage), false);
        return status.entries();
    }

    private static int clearBuiltSectionCache(CommandSourceStack source) {
        ForgeVoxyInstance.INSTANCE.getBuiltSectionBuildManager().clear();
        ForgeVoxyInstance.INSTANCE.getVoxyGeometryCache().clear();
        ForgeVoxyInstance.INSTANCE.getSectionGeometryConsumeManager().clear();
        ForgeVoxyInstance.INSTANCE.getGpuGeometryUploadManager().clear();
        boolean clearedGpuBuffers = ForgeGpuMeshUploadManager.getConfiguredSource() == SimpleGpuMeshSource.BUILT_SECTION;
        if (clearedGpuBuffers) {
            ForgeVoxyInstance.INSTANCE.getGpuMeshUploadManager().clear();
        }
        String message = "Voxy: cleared CPU-only BuiltSection cache, closed all partial geometry buffers, and cleared auto BuiltSection build records."
                + " CPU-only section geometry manager state and upload-only GL geometry heap were also cleared because they are derived from BuiltSection cache."
                + (clearedGpuBuffers ? " Current source is BUILT_SECTION, so simple GPU buffers were also cleared to avoid orphan renders." : " Simple GPU buffers were left intact because the active source is not BUILT_SECTION.");
        message = message + modelStoreFormalLayoutStatusSuffix() + bakedModelBridgeStatusSuffix() + realModelStoreSampleStatusSuffix() + modelSampleSetStatusSuffix() + modelAtlasSkeletonStatusSuffix() + modelAtlasUploadStatusSuffix() + modelAtlasSampleSetUploadStatusSuffix() + formalShaderInputBridgeStatusSuffix() + formalModelStoreStatusSuffix() + formalModelFactoryStatusSuffix() + modelBridgeResourceReloadStatusSuffix();
        String displayMessage = message;
        source.sendSuccess(() -> Component.literal(displayMessage), false);
        return 1;
    }

    private static int clearBuiltSectionBuildState(CommandSourceStack source) {
        ForgeVoxyInstance.INSTANCE.getBuiltSectionBuildManager().clear();
        source.sendSuccess(() -> Component.literal("Voxy: cleared auto BuiltSection build queue and built-record. The BuiltSection cache was left intact."), false);
        return 1;
    }

    private static int consumeCurrentChunkGeometryManager(CommandSourceStack source) {
        var minecraft = Minecraft.getInstance();
        var player = minecraft.player;
        var level = minecraft.level;
        if (player == null || level == null) {
            source.sendFailure(Component.literal("Voxy: no client world is active."));
            return 0;
        }

        if (ForgeVoxyInstance.INSTANCE.getCurrentEngineOptional().isEmpty()) {
            String reason = ForgeVoxyRuntimeOverrides.enabledWorldEngineSkeleton()
                    ? "no WorldEngine is active for the current world"
                    : "enableWorldEngineSkeleton is false";
            source.sendFailure(Component.literal("Voxy: cannot consume BuiltSection geometry; " + reason + "."));
            return 0;
        }

        try {
            String dimension = level.dimension().location().toString();
            int chunkX = player.chunkPosition().x;
            int chunkZ = player.chunkPosition().z;
            var builtSections = ForgeVoxyInstance.INSTANCE.getVoxyGeometryCache().createChunkSnapshot(dimension, chunkX, chunkZ);
            if (builtSections.isEmpty()) {
                source.sendFailure(Component.literal("Voxy: no BuiltSection cache entries for current chunk; run /voxy build_current_chunk_built_section first or use /voxy preset lod_built_section."));
                return 0;
            }

            var manager = ForgeVoxyInstance.INSTANCE.getSectionGeometryManager();
            var result = manager.consumeChunk(dimension, chunkX, chunkZ, builtSections);
            var status = result.status();
            String message = String.format(
                    "Voxy section geometry manager consume: %s chunk %d,%d cacheSections=%d consumed=%d replaced=%d skippedClosed=%d skippedEmpty=%d skippedWrongChunk=%d allocatedIds=%s geometryItems=%d geometryBytes=%d activeSections=%d/%d usedItems=%d usedBytes=%d uploadIntents=%d uploadIntentItems=%d uploadIntentBytes=%d removeIntents=%d removeIntentItems=%d removeIntentBytes=%d dirtyMetadataIds=%d metadataValid=%d metadataInvalid=%d lastMetadataError=%s sampleId=%d metadataWords=%s decoded=\"%s\"",
                    result.dimension(),
                    result.chunkX(),
                    result.chunkZ(),
                    result.cacheSections(),
                    result.consumedSections(),
                    result.replacedSections(),
                    result.skippedClosed(),
                    result.skippedEmpty(),
                    result.skippedWrongChunk(),
                    result.formatAllocatedIds(),
                    result.geometryItems(),
                    result.geometryBytes(),
                    status.activeSections(),
                    status.maxSections(),
                    status.usedGeometryItems(),
                    status.usedGeometryBytes(),
                    status.uploadIntents(),
                    status.uploadIntentItems(),
                    status.uploadIntentBytes(),
                    status.removeIntents(),
                    status.removeIntentItems(),
                    status.removeIntentBytes(),
                    status.dirtyMetadataIds(),
                    status.metadataValid(),
                    status.metadataInvalid(),
                    status.lastMetadataError(),
                    status.sampleSectionId(),
                    status.sampleMetadataWords(),
                    status.sampleMetadataDecoded()
            );
            source.sendSuccess(() -> Component.literal(message + " (CPU-only metadata/intents; no GL upload)"), false);
            return result.consumedSections();
        } catch (Exception e) {
            VoxyForge.LOGGER.error("Failed to consume current chunk BuiltSection geometry", e);
            source.sendFailure(Component.literal("Voxy: section geometry manager consume failed: " + e.getMessage()));
            return 0;
        }
    }

    private static int geometryManagerStatus(CommandSourceStack source) {
        var status = ForgeVoxyInstance.INSTANCE.getSectionGeometryManager().createStatusSnapshot();
        var consumeStatus = ForgeVoxyInstance.INSTANCE.getSectionGeometryConsumeManager().createStatusSnapshot();
        String message = String.format(
                "Voxy section geometry manager: autoConsume=%s engine=%s consumeDim=%s queue=%d consumedRecords=%d failedRecords=%d radius=%d maxPerTick=%d cooldown=%d lastConsumeMs=%.2f avgConsumeMs=%.2f lastFailure=%s activeDimension=%s activeSections=%d/%d sectionIdHighWaterMark=%d metadataSlots=%d metadataValid=%d metadataInvalid=%d lastMetadataError=%s usedGeometryItems=%d usedGeometryBytes=%d arenaSizeItems=%d arenaLimitItems=%d arenaFreeItems=%d arenaFreeBlocks=%d arenaLargestFreeBlockItems=%d uploadIntents=%d uploadIntentItems=%d uploadIntentBytes=%d removeIntents=%d removeIntentItems=%d removeIntentBytes=%d dirtyMetadataIds=%d totalUploads=%d totalRemoves=%d totalReplacements=%d clears=%d sampleId=%d metadataWords=%s decoded=\"%s\" sampleOffsets=%s sampleDeltas=%s sampleUploadHash=%d sampleRecords=%s orphanTracking=distance-prune-and-lifecycle-clear cpuOnly=true gl=false",
                consumeStatus.autoEnabled(),
                consumeStatus.enginePresent(),
                consumeStatus.dimension() == null ? "none" : consumeStatus.dimension(),
                consumeStatus.queuedSections(),
                consumeStatus.consumedRecords(),
                consumeStatus.failedRecords(),
                consumeStatus.radius(),
                consumeStatus.maxSectionsPerTick(),
                consumeStatus.cooldownTicks(),
                consumeStatus.lastConsumeMs(),
                consumeStatus.averageMs(),
                consumeStatus.lastFailureReason(),
                status.activeDimension(),
                status.activeSections(),
                status.maxSections(),
                status.sectionIdHighWaterMark(),
                status.metadataSlots(),
                status.metadataValid(),
                status.metadataInvalid(),
                status.lastMetadataError(),
                status.usedGeometryItems(),
                status.usedGeometryBytes(),
                status.arenaSizeItems(),
                status.arenaLimitItems(),
                status.arenaFreeItems(),
                status.arenaFreeBlocks(),
                status.arenaLargestFreeBlockItems(),
                status.uploadIntents(),
                status.uploadIntentItems(),
                status.uploadIntentBytes(),
                status.removeIntents(),
                status.removeIntentItems(),
                status.removeIntentBytes(),
                status.dirtyMetadataIds(),
                status.totalUploads(),
                status.totalRemoves(),
                status.totalReplacements(),
                status.clearCount(),
                status.sampleSectionId(),
                status.sampleMetadataWords(),
                status.sampleMetadataDecoded(),
                status.sampleOffsets(),
                status.sampleDeltas(),
                status.sampleUploadIntentHash(),
                status.sampleFirstRecordsDecoded()
        );
        source.sendSuccess(() -> Component.literal(message), false);
        return status.activeSections();
    }

    private static int geometryManagerDumpSample(CommandSourceStack source) {
        String message = ForgeVoxyInstance.INSTANCE.getSectionGeometryManager().createSampleDump();
        source.sendSuccess(() -> Component.literal(message), false);
        return ForgeVoxyInstance.INSTANCE.getSectionGeometryManager().createStatusSnapshot().activeSections();
    }

    private static int clearGeometryManager(CommandSourceStack source) {
        ForgeVoxyInstance.INSTANCE.getSectionGeometryConsumeManager().clear();
        ForgeVoxyInstance.INSTANCE.getGpuGeometryUploadManager().clear();
        source.sendSuccess(() -> Component.literal("Voxy: cleared CPU-only section geometry manager ids, heap allocation intents, metadata, upload intents, remove intents, dirty metadata ids, auto consume queue/records, and upload-only GL geometry heap. BuiltSection cache was left intact."), false);
        return 1;
    }

    private static int clearGeometryManagerConsumeState(CommandSourceStack source) {
        ForgeVoxyInstance.INSTANCE.getSectionGeometryConsumeManager().clearRecords();
        source.sendSuccess(() -> Component.literal("Voxy: cleared CPU-only section geometry auto consume queue and records. Section geometry manager data was left intact."), false);
        return 1;
    }

    private static int geometryGpuUploadStatus(CommandSourceStack source) {
        var status = ForgeVoxyInstance.INSTANCE.getGpuGeometryUploadManager().createStatusSnapshot();
        var geometryStatus = ForgeVoxyInstance.INSTANCE.getSectionGeometryManager().createStatusSnapshot();
        String message = String.format(
                "Voxy upload-only GL geometry heap: enabled=%s reason=%s heapCreated=%s geometryCapacityBytes=%d metadataCapacityBytes=%d uploadedGeometryBytes=%d uploadedSections=%d metadataWrites=%d removeIntentsProcessed=%d pendingUploadIntents=%d pendingRemoveIntents=%d pendingDirtyMetadata=%d cpuUploadIntents=%d cpuRemoveIntents=%d cpuDirtyMetadata=%d failures=%d lastError=%s lastUploadMs=%.2f avgUploadMs=%.2f validationRuns=%d validationFailures=%d lastValidationError=%s lastValidationSectionId=%d lastValidationGeometryPtr=%s lastMetadataMatch=%s lastGeometryMatch=%s releasedBuffers=%d clears=%d renderThreadOnly=%s draws=false rendererUsesHeap=false",
                status.enabled(),
                status.reason(),
                status.heapCreated(),
                status.geometryCapacityBytes(),
                status.metadataCapacityBytes(),
                status.uploadedGeometryBytes(),
                status.uploadedSections(),
                status.metadataWrites(),
                status.removeIntentsProcessed(),
                status.pendingUploadIntents(),
                status.pendingRemoveIntents(),
                status.pendingDirtyMetadata(),
                geometryStatus.uploadIntents(),
                geometryStatus.removeIntents(),
                geometryStatus.dirtyMetadataIds(),
                status.failures(),
                status.lastError(),
                status.lastUploadMs(),
                status.averageUploadMs(),
                status.validationRuns(),
                status.validationFailures(),
                status.lastValidationError(),
                status.lastValidationSectionId(),
                formatGeometryPointer(status.lastValidationGeometryPtr()),
                status.lastMetadataMatch(),
                status.lastGeometryMatch(),
                status.releasedBuffers(),
                status.clearCount(),
                status.renderThreadOnly()
        );
        String displayMessage = message;
        source.sendSuccess(() -> Component.literal(displayMessage), false);
        return status.enabled() ? 1 : 0;
    }

    private static int geometryGpuValidateSample(CommandSourceStack source) {
        ForgeGpuGeometryValidationResult result = ForgeVoxyInstance.INSTANCE.getGpuGeometryUploadManager().validateSample(8);
        String message = String.format(
                "Voxy upload-only GL geometry validation: success=%s reason=%s sectionId=%d geometryPtr=%s recordCount=%d metadataMatch=%s geometryMatch=%s expectedMetadataHash=%d actualMetadataHash=%d expectedGeometrySampleHash=%d actualGeometrySampleHash=%d expectedMetadata=%s actualMetadata=%s expectedRecords=%s actualRecords=%s readbackApi=glGetNamedBufferSubData renderThreadOnly=true draws=false",
                result.success(),
                result.reason(),
                result.sectionId(),
                formatGeometryPointer(result.geometryPtr()),
                result.recordCount(),
                result.metadataMatch(),
                result.geometryMatch(),
                result.expectedMetadataHash(),
                result.actualMetadataHash(),
                result.expectedGeometrySampleHash(),
                result.actualGeometrySampleHash(),
                result.expectedMetadataWords(),
                result.actualMetadataWords(),
                result.expectedGeometryRecords(),
                result.actualGeometryRecords()
        );
        if (result.success()) {
            source.sendSuccess(() -> Component.literal(message), false);
            return 1;
        }
        source.sendFailure(Component.literal(message));
        return 0;
    }

    private static int geometryGpuAuditSample(CommandSourceStack source) {
        ForgeGpuGeometryAuditResult result = ForgeVoxyInstance.INSTANCE.getGpuGeometryUploadManager().auditSample();
        String message = String.format(
                "Voxy upload-only GL geometry audit: success=%s reason=%s auditedSections=%d decodedRecords=%d invalidMetadata=%d invalidGeometryRecords=%d emptyBuckets=%d nonEmptyBuckets=%d maxQuadLength=%d maxQuadWidth=%d lastAuditedSectionId=%d lastAuditedPosition=%s lastAuditedGeometryPtr=%s lastAuditError=%s metadataWords=%s decodedMetadata=\"%s\" decodedRecords=\"%s\" readbackApi=glGetNamedBufferSubData renderThreadOnly=true drainsIntents=false draws=false",
                result.success(),
                result.reason(),
                result.auditedSections(),
                result.decodedRecords(),
                result.invalidMetadata(),
                result.invalidGeometryRecords(),
                result.emptyBuckets(),
                result.nonEmptyBuckets(),
                result.maxQuadLength(),
                result.maxQuadWidth(),
                result.lastAuditedSectionId(),
                Long.toUnsignedString(result.lastAuditedPosition()),
                formatGeometryPointer(result.lastAuditedGeometryPtr()),
                result.lastAuditError(),
                result.lastMetadataWords(),
                result.lastDecodedMetadata(),
                result.lastDecodedRecords()
        );
        if (result.success()) {
            source.sendSuccess(() -> Component.literal(message), false);
            return result.decodedRecords();
        }
        source.sendFailure(Component.literal(message));
        return 0;
    }

    private static int geometryGpuAuditStatus(CommandSourceStack source) {
        ForgeGpuGeometryAuditStats audit = ForgeVoxyInstance.INSTANCE.getGpuGeometryUploadManager().createAuditStatusSnapshot();
        ForgeGpuGeometryStats upload = ForgeVoxyInstance.INSTANCE.getGpuGeometryUploadManager().createStatusSnapshot();
        String message = String.format(
                "Voxy upload-only GL geometry audit status: auditRuns=%d auditFailures=%d lastAuditError=%s lastAuditDurationMs=%.2f lastAuditedSections=%d lastDecodedRecords=%d lastInvalidMetadata=%d lastInvalidGeometryRecords=%d lastEmptyBuckets=%d lastNonEmptyBuckets=%d lastMaxQuadLength=%d lastMaxQuadWidth=%d lastAuditedSectionId=%d lastAuditedGeometryPtr=%s uploadEnabled=%s heapCreated=%s uploadedSections=%d renderThreadOnly=true drainsIntents=false draws=false",
                audit.auditRuns(),
                audit.auditFailures(),
                audit.lastAuditError(),
                audit.lastAuditDurationMs(),
                audit.lastAuditedSections(),
                audit.lastDecodedRecords(),
                audit.lastInvalidMetadata(),
                audit.lastInvalidGeometryRecords(),
                audit.lastEmptyBuckets(),
                audit.lastNonEmptyBuckets(),
                audit.lastMaxQuadLength(),
                audit.lastMaxQuadWidth(),
                audit.lastAuditedSectionId(),
                formatGeometryPointer(audit.lastAuditedGeometryPtr()),
                upload.enabled(),
                upload.heapCreated(),
                upload.uploadedSections()
        );
        source.sendSuccess(() -> Component.literal(message), false);
        return 1;
    }

    private static int geometryGpuAuditClear(CommandSourceStack source) {
        ForgeVoxyInstance.INSTANCE.getGpuGeometryUploadManager().clearAuditStats();
        source.sendSuccess(() -> Component.literal("Voxy: cleared upload-only GL geometry audit counters and last decoded sample. GL buffers and CPU geometry intents were left intact."), false);
        return 1;
    }

    private static int geometryGpuStressStatus(CommandSourceStack source) {
        ForgeGpuGeometryStressStats stress = ForgeVoxyInstance.INSTANCE.getGpuGeometryUploadManager().createStressStatusSnapshot();
        ForgeGpuGeometryStats upload = ForgeVoxyInstance.INSTANCE.getGpuGeometryUploadManager().createStatusSnapshot();
        String message = String.format(
                "Voxy upload-only GL geometry stress: stressRuns=%d stressFailures=%d lastStressError=%s lastStressStartedAt=%s lastStressFinishedAt=%s lastStressDurationMs=%.2f lastBeforeClearUploadedSections=%d lastAfterReenableUploadedSections=%d lastBeforeClearGeometryBytes=%d lastAfterReenableGeometryBytes=%d lastValidationAfterReenableMetadataMatch=%s lastValidationAfterReenableGeometryMatch=%s uploadEnabled=%s heapCreated=%s uploadedSections=%d uploadedGeometryBytes=%d validationRuns=%d validationFailures=%d renderThreadOnly=true draws=false",
                stress.stressRuns(),
                stress.stressFailures(),
                stress.lastStressError(),
                stress.lastStressStartedAt(),
                stress.lastStressFinishedAt(),
                stress.lastStressDurationMs(),
                stress.lastBeforeClearUploadedSections(),
                stress.lastAfterReenableUploadedSections(),
                stress.lastBeforeClearGeometryBytes(),
                stress.lastAfterReenableGeometryBytes(),
                stress.lastValidationAfterReenableMetadataMatch(),
                stress.lastValidationAfterReenableGeometryMatch(),
                upload.enabled(),
                upload.heapCreated(),
                upload.uploadedSections(),
                upload.uploadedGeometryBytes(),
                upload.validationRuns(),
                upload.validationFailures()
        );
        source.sendSuccess(() -> Component.literal(message), false);
        return 1;
    }

    private static int geometryGpuStressClear(CommandSourceStack source) {
        ForgeVoxyInstance.INSTANCE.getGpuGeometryUploadManager().clearStressStats();
        source.sendSuccess(() -> Component.literal("Voxy: cleared upload-only GL geometry heap stress counters and last-run lifetime stats. GL buffers and CPU geometry intents were left intact."), false);
        return 1;
    }

    private static int geometryGpuStressOnce(CommandSourceStack source) {
        ForgeGpuGeometryStressStats stress = ForgeVoxyInstance.INSTANCE.getGpuGeometryUploadManager().stressOnce();
        String message = String.format(
                "Voxy upload-only GL geometry stress once: success=%s stressRuns=%d stressFailures=%d lastStressError=%s lastStressDurationMs=%.2f beforeSections=%d afterSections=%d beforeGeometryBytes=%d afterGeometryBytes=%d validationAfterReenableMetadataMatch=%s validationAfterReenableGeometryMatch=%s enabledAfterRun=%s heapCreatedAfterRun=%s draws=false rendererUsesHeap=false",
                stress.stressFailures() == 0 || "none".equals(stress.lastStressError()),
                stress.stressRuns(),
                stress.stressFailures(),
                stress.lastStressError(),
                stress.lastStressDurationMs(),
                stress.lastBeforeClearUploadedSections(),
                stress.lastAfterReenableUploadedSections(),
                stress.lastBeforeClearGeometryBytes(),
                stress.lastAfterReenableGeometryBytes(),
                stress.lastValidationAfterReenableMetadataMatch(),
                stress.lastValidationAfterReenableGeometryMatch(),
                ForgeGpuGeometryUploadManager.isEnabled(),
                ForgeVoxyInstance.INSTANCE.getGpuGeometryUploadManager().createStatusSnapshot().heapCreated()
        );
        if ("none".equals(stress.lastStressError())) {
            source.sendSuccess(() -> Component.literal(message), false);
            return 1;
        }
        source.sendFailure(Component.literal(message));
        return 0;
    }

    private static int geometryGpuVisualizeSample(CommandSourceStack source) {
        ForgeGpuGeometryVisualizationResult result = ForgeGpuGeometryVisualizationBuilder.buildSample(ForgeVoxyInstance.INSTANCE);
        String message = String.format(
                "Voxy GL heap readback visualization sample: success=%s reason=%s builtSections=%d recordsRead=%d quads=%d vertices=%d invalidMetadata=%d invalidRecords=%d skippedBuckets=%d durationMs=%.2f lastSectionId=%d lastPosition=%s lastGeometryPtr=%s lastError=%s lastDecodedRecord=\"%s\" lastSource=%s readbackApi=glGetNamedBufferSubData drainsIntents=false formalRenderer=false",
                result.success(),
                result.reason(),
                result.builtSections(),
                result.recordsRead(),
                result.quads(),
                result.vertices(),
                result.invalidMetadata(),
                result.invalidRecords(),
                result.skippedBuckets(),
                result.durationMs(),
                result.lastSectionId(),
                Long.toUnsignedString(result.lastPosition()),
                formatGeometryPointer(result.lastGeometryPtr()),
                result.lastError(),
                result.lastDecodedRecord(),
                result.lastSource()
        );
        if (result.success()) {
            source.sendSuccess(() -> Component.literal(message), false);
            return result.quads();
        }
        source.sendFailure(Component.literal(message));
        return 0;
    }

    private static int geometryGpuVisualizeStatus(CommandSourceStack source) {
        var status = ForgeVoxyInstance.INSTANCE.getGpuGeometryVisualizationCache().createStatusSnapshot();
        var build = status.lastBuildResult();
        var render = ForgeVoxyInstance.INSTANCE.getGpuGeometryReadbackDebugRenderer().getLastFrameStats();
        String message = String.format(
                "Voxy GL heap readback visualization: enabled=%s cacheSections=%d cacheQuads=%d cacheVertices=%d lastBuildSections=%d lastBuildRecordsRead=%d lastBuildQuads=%d lastBuildVertices=%d lastBuildInvalidMetadata=%d lastBuildInvalidRecords=%d lastBuildSkippedBuckets=%d lastBuildDurationMs=%.2f lastBuildError=%s lastRenderedQuads=%d lastRenderedVertices=%d lastRenderDistanceChunks=%d lastSource=%s render=%s renderReason=%s renderSections=%d alpha=%.2f ignoreDepth=%s doubleSided=%s stage=%s maxSections=%d maxRecords=%d clears=%d formalRenderer=false",
                ForgeVoxyRuntimeOverrides.enableGeometryGpuVisualization(),
                status.cacheSections(),
                status.cacheQuads(),
                status.cacheVertices(),
                build.builtSections(),
                build.recordsRead(),
                build.quads(),
                build.vertices(),
                build.invalidMetadata(),
                build.invalidRecords(),
                build.skippedBuckets(),
                build.durationMs(),
                build.lastError(),
                render.renderedQuads(),
                render.renderedVertices(),
                render.renderDistanceChunks(),
                render.source(),
                render.rendered(),
                render.reason(),
                render.renderedSections(),
                ForgeGpuGeometryReadbackDebugRenderer.getConfiguredAlpha(),
                ForgeGpuGeometryReadbackDebugRenderer.shouldIgnoreDepth(),
                ForgeGpuGeometryReadbackDebugRenderer.shouldRenderDoubleSided(),
                ForgeGpuGeometryReadbackDebugRenderer.getRenderStageName(),
                ForgeGpuGeometryVisualizationBuilder.getConfiguredMaxSections(),
                ForgeGpuGeometryVisualizationBuilder.getConfiguredMaxRecords(),
                status.clearCount()
        );
        source.sendSuccess(() -> Component.literal(message), false);
        return status.cacheQuads();
    }

    private static int geometryGpuVisualizeClear(CommandSourceStack source) {
        ForgeVoxyInstance.INSTANCE.getGpuGeometryVisualizationCache().clear();
        ForgeVoxyInstance.INSTANCE.getGpuGeometryReadbackDebugRenderer().clearStats();
        source.sendSuccess(() -> Component.literal("Voxy: cleared GL heap readback visualization cache and render stats. Upload-only GL heap, CPU BuiltSection cache, and CPU section geometry manager were left intact."), false);
        return 1;
    }

    private static int setGeometryGpuVisualization(CommandSourceStack source, boolean enabled) {
        ForgeVoxyRuntimeOverrides.setGeometryGpuVisualization(enabled);
        if (!enabled) {
            ForgeVoxyInstance.INSTANCE.getGpuGeometryVisualizationCache().clear();
            ForgeVoxyInstance.INSTANCE.getGpuGeometryReadbackDebugRenderer().clearStats();
        }
        String message = enabled
                ? "Voxy GL heap readback visualization: runtime-only debug renderer enabled. Use /voxy geometry_gpu_visualize_sample to read back GL heap data into a temporary visualization cache. This was not written to toml."
                : "Voxy GL heap readback visualization: runtime-only debug renderer disabled and visualization cache was cleared. This was not written to toml.";
        source.sendSuccess(() -> Component.literal(message), false);
        return 1;
    }

    private static int geometryGpuReadbackMeshBuild(CommandSourceStack source) {
        ForgeGpuGeometryReadbackMeshResult result = ForgeGpuGeometryReadbackMeshBuilder.buildSample(ForgeVoxyInstance.INSTANCE);
        if (ForgeGpuMeshUploadManager.getConfiguredSource() == SimpleGpuMeshSource.GL_HEAP_READBACK) {
            ForgeVoxyInstance.INSTANCE.getGpuMeshUploadManager().clear();
        }
        String message = String.format(
                "Voxy GL heap readback simple mesh build: success=%s reason=%s builtSections=%d recordsRead=%d quads=%d vertices=%d invalidMetadata=%d invalidRecords=%d durationMs=%.2f lastSectionId=%d lastPosition=%s lastGeometryPtr=%s lastError=%s lastDecodedRecord=\"%s\" lastSource=%s sourceReady=%s maxSections=%d maxRecords=%d readbackApi=glGetNamedBufferSubData drainsIntents=false formalRenderer=false simpleGpuSource=%s",
                result.success(),
                result.reason(),
                result.builtSections(),
                result.recordsRead(),
                result.quads(),
                result.vertices(),
                result.invalidMetadata(),
                result.invalidRecords(),
                result.durationMs(),
                result.lastSectionId(),
                Long.toUnsignedString(result.lastPosition()),
                formatGeometryPointer(result.lastGeometryPtr()),
                result.lastError(),
                result.lastDecodedRecord(),
                result.lastSource(),
                result.quads() > 0,
                ForgeGpuGeometryReadbackMeshBuilder.getConfiguredMaxSections(),
                ForgeGpuGeometryReadbackMeshBuilder.getConfiguredMaxRecords(),
                ForgeGpuMeshUploadManager.getConfiguredSource()
        );
        if (result.success()) {
            source.sendSuccess(() -> Component.literal(message), false);
            return result.quads();
        }
        source.sendFailure(Component.literal(message));
        return 0;
    }

    private static int geometryGpuReadbackMeshStatus(CommandSourceStack source) {
        var status = ForgeVoxyInstance.INSTANCE.getGpuGeometryReadbackMeshCache().createStatusSnapshot();
        var refresh = ForgeVoxyInstance.INSTANCE.getGpuGeometryReadbackMeshRefreshManager().createStatusSnapshot();
        var build = status.lastBuildResult();
        String message = String.format(
                "Voxy GL heap readback simple mesh: enabled=%s sourceReady=%s cacheSections=%d cacheQuads=%d cacheVertices=%d cacheBytes=%d lastBuildSections=%d lastBuildRecordsRead=%d lastBuildQuads=%d lastBuildVertices=%d lastBuildInvalidMetadata=%d lastBuildInvalidRecords=%d lastBuildDurationMs=%.2f lastBuildError=%s lastSource=%s clears=%d simpleGpuSource=%s simpleGpuEnabled=%s autoRefresh=%s refreshCooldownTicks=%d ticksUntilNextRefresh=%d refreshRuns=%d refreshSkipped=%d refreshFailures=%d lastRefreshReason=%s lastRefreshSkippedReason=%s lastRefreshDurationMs=%.2f lastRefreshSections=%d lastRefreshRecords=%d lastRefreshQuads=%d lastRefreshVertices=%d lastRefreshError=%s refreshMaxSections=%d refreshMaxRecords=%d refreshOnlySourceActive=%s refreshOnlyRendererEnabled=%s formalRenderer=false",
                ForgeGpuMeshUploadManager.getConfiguredSource() == SimpleGpuMeshSource.GL_HEAP_READBACK,
                status.cacheQuads() > 0,
                status.cacheSections(),
                status.cacheQuads(),
                status.cacheVertices(),
                status.cacheBytes(),
                build.builtSections(),
                build.recordsRead(),
                build.quads(),
                build.vertices(),
                build.invalidMetadata(),
                build.invalidRecords(),
                build.durationMs(),
                build.lastError(),
                build.lastSource(),
                status.clearCount(),
                ForgeGpuMeshUploadManager.getConfiguredSource(),
                ForgeVoxyRuntimeOverrides.enableSimpleGpuMeshRenderer(),
                refresh.autoRefresh(),
                refresh.refreshCooldownTicks(),
                refresh.ticksUntilNextRefresh(),
                refresh.refreshRuns(),
                refresh.refreshSkipped(),
                refresh.refreshFailures(),
                refresh.lastRefreshReason(),
                refresh.lastRefreshSkippedReason(),
                refresh.lastRefreshDurationMs(),
                refresh.lastRefreshSections(),
                refresh.lastRefreshRecords(),
                refresh.lastRefreshQuads(),
                refresh.lastRefreshVertices(),
                refresh.lastRefreshError(),
                refresh.refreshMaxSections(),
                refresh.refreshMaxRecords(),
                refresh.onlyWhenSourceActive(),
                refresh.onlyWhenRendererEnabled()
        );
        source.sendSuccess(() -> Component.literal(message), false);
        return status.cacheQuads();
    }

    private static int geometryGpuReadbackMeshClear(CommandSourceStack source) {
        ForgeVoxyInstance.INSTANCE.getGpuGeometryReadbackMeshCache().clear();
        ForgeVoxyInstance.INSTANCE.getGpuGeometryReadbackMeshRefreshManager().clear();
        if (ForgeGpuMeshUploadManager.getConfiguredSource() == SimpleGpuMeshSource.GL_HEAP_READBACK) {
            ForgeVoxyInstance.INSTANCE.getGpuMeshUploadManager().clear();
        }
        source.sendSuccess(() -> Component.literal("Voxy: cleared GL_HEAP_READBACK simple mesh cache. Upload-only GL heap, CPU BuiltSection cache, and CPU section geometry manager were left intact."), false);
        return 1;
    }

    private static int setGeometryGpuReadbackMeshAutoRefresh(CommandSourceStack source, boolean enabled) {
        ForgeVoxyRuntimeOverrides.setGeometryGpuReadbackMeshAutoRefresh(enabled);
        if (enabled) {
            ForgeVoxyInstance.INSTANCE.getGpuGeometryReadbackMeshRefreshManager().requestRefresh(ForgeGpuGeometryReadbackMeshRefreshManager.REASON_MANUAL_COMMAND);
        } else {
            ForgeVoxyInstance.INSTANCE.getGpuGeometryReadbackMeshRefreshManager().clear();
        }
        String message = enabled
                ? "Voxy GL_HEAP_READBACK mesh auto refresh: runtime-only enabled. It will refresh only while source/renderer gates allow it and never writes toml."
                : "Voxy GL_HEAP_READBACK mesh auto refresh: runtime-only disabled and refresh state was cleared. Existing readback mesh cache was left intact.";
        source.sendSuccess(() -> Component.literal(message), false);
        return 1;
    }

    private static int geometryGpuReadbackMeshRefreshOnce(CommandSourceStack source) {
        var attempt = ForgeVoxyInstance.INSTANCE.getGpuGeometryReadbackMeshRefreshManager().refreshNow(ForgeGpuGeometryReadbackMeshRefreshManager.REASON_MANUAL_COMMAND);
        var result = attempt.result();
        var status = attempt.status();
        String message = String.format(
                "Voxy GL heap readback simple mesh refresh: success=%s skipped=%s skippedReason=%s reason=%s builtSections=%d recordsRead=%d quads=%d vertices=%d invalidMetadata=%d invalidRecords=%d durationMs=%.2f refreshRuns=%d refreshSkipped=%d refreshFailures=%d ticksUntilNextRefresh=%d lastRefreshError=%s source=%s autoRefresh=%s",
                attempt.success(),
                attempt.skipped(),
                attempt.skippedReason(),
                result.reason(),
                result.builtSections(),
                result.recordsRead(),
                result.quads(),
                result.vertices(),
                result.invalidMetadata(),
                result.invalidRecords(),
                result.durationMs(),
                status.refreshRuns(),
                status.refreshSkipped(),
                status.refreshFailures(),
                status.ticksUntilNextRefresh(),
                status.lastRefreshError(),
                ForgeGpuMeshUploadManager.getConfiguredSource(),
                status.autoRefresh()
        );
        if (attempt.success()) {
            source.sendSuccess(() -> Component.literal(message), false);
            return Math.max(1, result.quads());
        }
        source.sendFailure(Component.literal(message));
        return 0;
    }

    private static int setGeometryGpuUpload(CommandSourceStack source, boolean enabled) {
        ForgeVoxyRuntimeOverrides.setGeometryGpuUpload(enabled);
        if (!enabled) {
            ForgeVoxyInstance.INSTANCE.getGpuGeometryUploadManager().clear();
            ForgeVoxyInstance.INSTANCE.getTexturedReadbackRenderer().markStale("geometry-gpu-upload-disabled");
            ForgeVoxyInstance.INSTANCE.getTexturedMdicDebugRenderer().markStale("geometry-gpu-upload-disabled");
        }
        String message = enabled
                ? "Voxy upload-only GL geometry heap: runtime-only upload enabled. It will copy CPU section geometry upload/metadata intents into small GL buffers on the render thread; it does not render or draw."
                : "Voxy upload-only GL geometry heap: runtime-only upload disabled, GL buffers were released, and GL heap readback visualization/readback-mesh caches were cleared. This was not written to toml.";
        source.sendSuccess(() -> Component.literal(message), false);
        return 1;
    }

    private static int clearGeometryGpuUpload(CommandSourceStack source) {
        ForgeVoxyInstance.INSTANCE.getGpuGeometryUploadManager().clear();
        ForgeVoxyInstance.INSTANCE.getDirectGpuGeometryRenderer().clear();
        ForgeVoxyInstance.INSTANCE.getMdicCommandManager().clear();
        ForgeVoxyInstance.INSTANCE.getMdicDebugRenderer().clear();
        ForgeVoxyInstance.INSTANCE.getTexturedReadbackRenderer().markStale("geometry-gpu-upload-clear");
        ForgeVoxyInstance.INSTANCE.getTexturedMdicDebugRenderer().markStale("geometry-gpu-upload-clear");
        source.sendSuccess(() -> Component.literal("Voxy: released upload-only GL geometry heap buffers, cleared GPU upload processed-state, cleared direct GL renderer skeleton state, cleared MDIC command skeleton/debug draw state, and cleared GL heap readback visualization/readback-mesh caches. CPU section geometry manager intents were left intact and can re-upload."), false);
        if (ForgeGpuMeshUploadManager.getConfiguredSource() == SimpleGpuMeshSource.GL_HEAP_READBACK
                && ForgeVoxyRuntimeOverrides.enableGeometryGpuReadbackMeshAutoRefresh()) {
            ForgeVoxyInstance.INSTANCE.getGpuGeometryReadbackMeshRefreshManager().requestRefresh(ForgeGpuGeometryReadbackMeshRefreshManager.REASON_UPLOAD_CLEAR_REBUILD);
        }
        return 1;
    }

    private static int directGlRendererStatus(CommandSourceStack source) {
        var status = ForgeVoxyInstance.INSTANCE.getDirectGpuGeometryRenderer().createStatusSnapshot();
        String message = String.format(
                "Voxy direct GL renderer: stage=%s enabled=%s initialized=%s hasHeap=%s heapCreated=%s configuredDrawMode=%s effectiveDrawMode=%s autoModeSelectedReason=%s autoModeFallbackReason=%s multiDrawSupported=%s multiDrawUnsupportedReason=%s indirectSupported=%s indirectUnsupportedReason=%s multiDrawIndirectSupported=%s drawIndirectBufferSupported=%s drawIdSupported=%s baseInstanceSupported=%s shaderSupported=%s shaderCompiled=%s programCreated=%s loopShaderCompiled=%s multiDrawShaderCompiled=%s multiDrawProgramCreated=%s indirectShaderCompiled=%s indirectProgramCreated=%s drawItemSsboCreated=%s drawItemSsboBytes=%d drawItemSsboGeneration=%d indirectCommandBufferCreated=%s indirectCommandBufferBytes=%d indirectCommandBufferGeneration=%d usesSsbo=%s lastIndirectAuditOk=%s auditRuns=%d auditFailures=%d lastIndirectAuditError=%s lastIndirectAuditDurationMs=%.2f lastAuditedDrawItems=%d lastAuditedCommandBytes=%d lastAuditedDrawItemBytes=%d lastCommandBufferMatch=%s lastDrawItemBufferMatch=%s lastAuditHeapGeneration=%d lastAuditDimension=%s lastInvalidCommands=%d lastInvalidDrawItems=%d lastAuditedVertices=%d drawListValid=%s drawListStale=%s drawItems=%d drawListRecords=%d drawListVertices=%d plannedSections=%d plannedRecords=%d plannedVertices=%d candidateSections=%d acceptedSections=%d rejectedByRadius=%d rejectedByFrustum=%d frustumAvailable=%s cameraChunk=%s cameraSection=%s skippedSections=%d skippedRecords=%d selectionMode=%s uploadedSectionCandidates=%d invalidMetadata=%d lastPlanDurationMs=%.2f lastPlanError=%s lastSkippedReason=%s planRuns=%d planFailures=%d autoPlan=%s autoPlanCooldownTicks=%d ticksUntilNextAutoPlan=%d lastAutoPlanReason=%s lastAutoPlanSkippedReason=%s lastAutoPlanDurationMs=%.2f autoPlanRuns=%d autoPlanSkipped=%d lastAutoPlanFailures=%d drawListRuns=%d drawListFailures=%d lastDrawListMs=%.2f maxDrawListMs=%.2f lastDrawListError=%s lastDrawListSkippedReason=%s drawListHeapGeneration=%d currentHeapGeneration=%d drawListDimension=%s currentDimension=%s clears=%d maxPlanCandidates=%d maxDrawSections=%d maxDrawRecords=%d maxRecordsPerSection=%d renderDistanceChunks=%d alpha=%.2f ignoreDepth=%s doubleSided=%s actualDrawEnabled=%s lastFrameApiDrawCalls=%d lastFrameLogicalDrawItems=%d lastFrameVertices=%d drawApiCallsIssued=%d logicalDrawItemsIssued=%d verticesDrawn=%d lastFrameRenderMs=%.2f maxFrameRenderMs=%.2f avgFrameRenderMs=%.2f lastFrameOverBudget=%s overBudgetFrames=%d frameBudgetMs=%.2f planBudgetMs=%.2f lastDrawError=%s lastRenderSkippedReason=%s lastGlError=%s lastGlErrorStage=%s glErrorCount=%d lastPreExistingGlError=%s lastStateRestoreError=%s stateRestoreFailures=%d stressRuns=%d stressFailures=%d lastStressError=%s lastStressDurationMs=%.2f lastStressPlannedSections=%d lastStressPlannedRecords=%d lastStressDrawListValid=%s lastStressShaderSupported=%s lastStressSourceRegressionOk=%s lastStressLoopOk=%s lastStressMultiDrawOk=%s lastStressIndirectOk=%s lastStressIndirectAuditOk=%s lastStressGlErrorCount=%d lastStressStateRestoreFailures=%d mdic=false vboOwner=upload-only-heap",
                status.stage(),
                status.enabled(),
                status.initialized(),
                status.hasHeap(),
                status.heapCreated(),
                status.configuredDrawMode(),
                status.effectiveDrawMode(),
                status.autoModeSelectedReason(),
                status.autoModeFallbackReason(),
                status.multiDrawSupported(),
                status.multiDrawUnsupportedReason(),
                status.indirectSupported(),
                status.indirectUnsupportedReason(),
                status.multiDrawIndirectSupported(),
                status.drawIndirectBufferSupported(),
                status.drawIdSupported(),
                status.baseInstanceSupported(),
                status.shaderSupported(),
                status.shaderCompiled(),
                status.programCreated(),
                status.loopShaderCompiled(),
                status.multiDrawShaderCompiled(),
                status.multiDrawProgramCreated(),
                status.indirectShaderCompiled(),
                status.indirectProgramCreated(),
                status.drawItemSsboCreated(),
                status.drawItemSsboBytes(),
                status.drawItemSsboGeneration(),
                status.indirectCommandBufferCreated(),
                status.indirectCommandBufferBytes(),
                status.indirectCommandBufferGeneration(),
                status.usesSsbo(),
                status.lastIndirectAuditOk(),
                status.auditRuns(),
                status.auditFailures(),
                status.lastIndirectAuditError(),
                status.lastIndirectAuditDurationMs(),
                status.lastAuditedDrawItems(),
                status.lastAuditedCommandBytes(),
                status.lastAuditedDrawItemBytes(),
                status.lastCommandBufferMatch(),
                status.lastDrawItemBufferMatch(),
                status.lastAuditHeapGeneration(),
                status.lastAuditDimension(),
                status.lastInvalidCommands(),
                status.lastInvalidDrawItems(),
                status.lastAuditedVertices(),
                status.drawListValid(),
                status.drawListStale(),
                status.drawItems(),
                status.drawListRecords(),
                status.drawListVertices(),
                status.plannedSections(),
                status.plannedRecords(),
                status.plannedVertices(),
                status.candidateSections(),
                status.acceptedSections(),
                status.rejectedByRadius(),
                status.rejectedByFrustum(),
                status.frustumAvailable(),
                status.cameraChunk(),
                status.cameraSection(),
                status.skippedSections(),
                status.skippedRecords(),
                status.selectionMode(),
                status.uploadedSectionCandidates(),
                status.invalidMetadata(),
                status.lastPlanDurationMs(),
                status.lastPlanError(),
                status.lastSkippedReason(),
                status.planRuns(),
                status.planFailures(),
                status.autoPlan(),
                status.autoPlanCooldownTicks(),
                status.ticksUntilNextAutoPlan(),
                status.lastAutoPlanReason(),
                status.lastAutoPlanSkippedReason(),
                status.lastAutoPlanDurationMs(),
                status.autoPlanRuns(),
                status.autoPlanSkipped(),
                status.lastAutoPlanFailures(),
                status.drawListBuildRuns(),
                status.drawListBuildFailures(),
                status.lastDrawListBuildDurationMs(),
                status.maxDrawListBuildDurationMs(),
                status.lastDrawListError(),
                status.lastDrawListSkippedReason(),
                status.drawListHeapGeneration(),
                status.currentHeapGeneration(),
                status.drawListDimension(),
                status.currentDimension(),
                status.clearCount(),
                status.maxPlanCandidates(),
                status.maxDrawSections(),
                status.maxDrawRecords(),
                status.maxRecordsPerSection(),
                status.renderDistanceChunks(),
                status.debugAlpha(),
                status.ignoreDepth(),
                status.doubleSided(),
                status.actualDrawEnabled(),
                status.lastFrameDrawCalls(),
                status.lastFrameDrawItems(),
                status.lastFrameVertices(),
                status.drawCallsIssued(),
                status.logicalDrawItemsIssued(),
                status.verticesDrawn(),
                status.lastDrawDurationMs(),
                status.maxFrameRenderMs(),
                status.avgFrameRenderMs(),
                status.lastFrameOverBudget(),
                status.overBudgetFrames(),
                status.frameBudgetMs(),
                status.planBudgetMs(),
                status.lastDrawError(),
                status.lastRenderSkippedReason(),
                status.lastGlError(),
                status.lastGlErrorStage(),
                status.glErrorCount(),
                status.lastPreExistingGlError(),
                status.lastStateRestoreError(),
                status.stateRestoreFailures(),
                status.stressRuns(),
                status.stressFailures(),
                status.lastStressError(),
                status.lastStressDurationMs(),
                status.lastStressPlannedSections(),
                status.lastStressPlannedRecords(),
                status.lastStressDrawListValid(),
                status.lastStressShaderSupported(),
                status.lastStressSourceRegressionOk(),
                status.lastStressLoopOk(),
                status.lastStressMultiDrawOk(),
                status.lastStressIndirectOk(),
                status.lastStressIndirectAuditOk(),
                status.lastStressGlErrorCount(),
                status.lastStressStateRestoreFailures()
        );
        String displayMessage = message;
        source.sendSuccess(() -> Component.literal(displayMessage), false);
        return status.enabled() ? 1 : 0;
    }

    private static int setDirectGlRenderer(CommandSourceStack source, boolean enabled) {
        ForgeVoxyRuntimeOverrides.setDirectGpuGeometryRenderer(enabled);
        if (enabled) {
            ForgeVoxyInstance.INSTANCE.getDirectGpuGeometryRenderer().markEnabledRuntime();
        } else {
            ForgeVoxyInstance.INSTANCE.getDirectGpuGeometryRenderer().clear();
        }
        String message = enabled
                ? "Voxy direct GL renderer: runtime-only enabled. G5.7 can audit indirect command buffers and draw item SSBOs; actualDrawEnabled stays false until /voxy direct_gl_renderer_draw_enable, and drawMode defaults to LOOP_PER_SECTION. Optional modes: multi_draw_arrays, indirect, and auto."
                : "Voxy direct GL renderer: runtime-only disabled, actual draw disabled, and direct renderer state was cleared. This was not written to toml.";
        source.sendSuccess(() -> Component.literal(message), false);
        return 1;
    }

    private static int setDirectGlRendererAutoPlan(CommandSourceStack source, boolean enabled) {
        ForgeVoxyRuntimeOverrides.setDirectGpuGeometryAutoPlan(enabled);
        if (enabled) {
            ForgeVoxyInstance.INSTANCE.getDirectGpuGeometryRenderer().markEnabledRuntime();
            ForgeVoxyInstance.INSTANCE.getDirectGpuGeometryRenderer().requestAutoPlan(ForgeDirectGpuGeometryRenderer.REASON_MANUAL_COMMAND);
        }
        String message = enabled
                ? "Voxy direct GL renderer: auto plan enabled runtime-only. It is cooldown and camera-move bounded; actual direct draw is still controlled separately."
                : "Voxy direct GL renderer: auto plan disabled runtime-only. Existing draw list was kept for inspection.";
        source.sendSuccess(() -> Component.literal(message), false);
        return 1;
    }

    private static int clearDirectGlRenderer(CommandSourceStack source) {
        ForgeVoxyInstance.INSTANCE.getDirectGpuGeometryRenderer().clear();
        source.sendSuccess(() -> Component.literal("Voxy direct GL renderer: state, draw list, and debug shader were cleared. CPU caches, SectionGeometryManager, and upload-only GL heap were left intact."), false);
        return 1;
    }

    private static int directGlRendererPlanSample(CommandSourceStack source) {
        var result = ForgeVoxyInstance.INSTANCE.getDirectGpuGeometryRenderer().planSample();
        var status = ForgeVoxyInstance.INSTANCE.getDirectGpuGeometryRenderer().createStatusSnapshot();
        String message = String.format(
                "Voxy direct GL renderer plan: success=%s skippedReason=%s error=%s uploadedSectionCandidates=%d plannedSections=%d plannedRecords=%d plannedVertices=%d skippedSections=%d skippedRecords=%d selectionMode=%s invalidMetadata=%d durationMs=%.2f drawListValid=%s drawItems=%d drawListStale=%s drawCallsIssued=%d verticesDrawn=%d actualDrawEnabled=%s stage=%s readback=metadata-only shaderSupported=%s mdic=false",
                result.success(),
                result.skippedReason(),
                result.error(),
                result.uploadedSectionCandidates(),
                result.plannedSections(),
                result.plannedRecords(),
                result.plannedVertices(),
                result.skippedSections(),
                result.skippedRecords(),
                result.selectionMode(),
                result.invalidMetadata(),
                result.durationMs(),
                status.drawListValid(),
                status.drawItems(),
                status.drawListStale(),
                status.drawCallsIssued(),
                status.verticesDrawn(),
                status.actualDrawEnabled(),
                status.stage(),
                status.shaderSupported()
        );
        if (result.success()) {
            source.sendSuccess(() -> Component.literal(message), false);
            return Math.max(1, result.plannedSections());
        }
        source.sendFailure(Component.literal(message));
        return 0;
    }

    private static int directGlRendererAutoPlanOnce(CommandSourceStack source) {
        var result = ForgeVoxyInstance.INSTANCE.getDirectGpuGeometryRenderer().autoPlanNow(ForgeDirectGpuGeometryRenderer.REASON_MANUAL_COMMAND);
        String message = String.format(
                "Voxy direct GL renderer auto plan: success=%s skippedReason=%s error=%s uploadedSectionCandidates=%d candidateSections=%d acceptedSections=%d drawItems=%d drawRecords=%d drawVertices=%d rejectedByRadius=%d rejectedByFrustum=%d selectionMode=%s frustumAvailable=%s cameraChunk=%s cameraSection=%s heapGeneration=%d invalidMetadata=%d durationMs=%.2f autoPlan=%s actualDrawEnabled=%s stage=%s",
                result.success(),
                result.skippedReason(),
                result.error(),
                result.uploadedSectionCandidates(),
                result.candidateSections(),
                result.acceptedSections(),
                result.drawItems(),
                result.drawRecords(),
                result.drawVertices(),
                result.rejectedByRadius(),
                result.rejectedByFrustum(),
                result.selectionMode(),
                result.frustumAvailable(),
                result.cameraChunk(),
                result.cameraSection(),
                result.heapGeneration(),
                result.invalidMetadata(),
                result.durationMs(),
                ForgeDirectGpuGeometryRendererConfig.autoPlanEnabled(),
                ForgeDirectGpuGeometryRendererConfig.actualDrawEnabled(),
                ForgeDirectGpuGeometryRenderState.STAGE
        );
        if (result.success()) {
            source.sendSuccess(() -> Component.literal(message), false);
            return Math.max(1, result.drawItems());
        }
        source.sendFailure(Component.literal(message));
        return 0;
    }

    private static int directGlRendererBuildDrawList(CommandSourceStack source) {
        var result = ForgeVoxyInstance.INSTANCE.getDirectGpuGeometryRenderer().buildDrawList();
        String message = String.format(
                "Voxy direct GL renderer draw list: success=%s skippedReason=%s error=%s uploadedSectionCandidates=%d candidateSections=%d acceptedSections=%d drawItems=%d drawRecords=%d drawVertices=%d skippedSections=%d skippedRecords=%d rejectedByRadius=%d rejectedByFrustum=%d selectionMode=%s frustumAvailable=%s cameraChunk=%s cameraSection=%s heapGeneration=%d invalidMetadata=%d durationMs=%.2f maxPlanCandidates=%d maxDrawSections=%d maxDrawRecords=%d maxRecordsPerSection=%d actualDrawEnabled=%s stage=%s readback=metadata-only draw=false",
                result.success(),
                result.skippedReason(),
                result.error(),
                result.uploadedSectionCandidates(),
                result.candidateSections(),
                result.acceptedSections(),
                result.drawItems(),
                result.drawRecords(),
                result.drawVertices(),
                result.skippedSections(),
                result.skippedRecords(),
                result.rejectedByRadius(),
                result.rejectedByFrustum(),
                result.selectionMode(),
                result.frustumAvailable(),
                result.cameraChunk(),
                result.cameraSection(),
                result.heapGeneration(),
                result.invalidMetadata(),
                result.durationMs(),
                ForgeDirectGpuGeometryRendererConfig.maxPlanCandidates(),
                ForgeDirectGpuGeometryRendererConfig.maxDrawSections(),
                ForgeDirectGpuGeometryRendererConfig.maxDrawRecords(),
                ForgeDirectGpuGeometryRendererConfig.maxRecordsPerSection(),
                ForgeDirectGpuGeometryRendererConfig.actualDrawEnabled(),
                ForgeDirectGpuGeometryRenderState.STAGE
        );
        if (result.success()) {
            source.sendSuccess(() -> Component.literal(message), false);
            return Math.max(1, result.drawItems());
        }
        source.sendFailure(Component.literal(message));
        return 0;
    }

    private static int setDirectGlRendererDrawMode(CommandSourceStack source, ForgeDirectGpuGeometryDrawMode mode) {
        ForgeDirectGpuGeometryRenderer renderer = ForgeVoxyInstance.INSTANCE.getDirectGpuGeometryRenderer();
        ForgeVoxyRuntimeOverrides.setDirectGpuGeometryRenderer(true);
        renderer.setDrawMode(mode);
        renderer.markEnabledRuntime();
        var status = renderer.createStatusSnapshot();
        String message = String.format(
                "Voxy direct GL renderer: configuredDrawMode=%s effectiveDrawMode=%s autoModeSelectedReason=%s autoModeFallbackReason=%s multiDrawSupported=%s multiDrawUnsupportedReason=%s indirectSupported=%s indirectUnsupportedReason=%s multiDrawIndirectSupported=%s drawIndirectBufferSupported=%s drawIdSupported=%s baseInstanceSupported=%s drawItemSsboCreated=%s drawItemSsboGeneration=%d indirectCommandBufferCreated=%s indirectCommandBufferGeneration=%d. This is runtime-only and was not written to toml.",
                status.configuredDrawMode(),
                status.effectiveDrawMode(),
                status.autoModeSelectedReason(),
                status.autoModeFallbackReason(),
                status.multiDrawSupported(),
                status.multiDrawUnsupportedReason(),
                status.indirectSupported(),
                status.indirectUnsupportedReason(),
                status.multiDrawIndirectSupported(),
                status.drawIndirectBufferSupported(),
                status.drawIdSupported(),
                status.baseInstanceSupported(),
                status.drawItemSsboCreated(),
                status.drawItemSsboGeneration(),
                status.indirectCommandBufferCreated(),
                status.indirectCommandBufferGeneration()
        );
        source.sendSuccess(() -> Component.literal(message), false);
        return 1;
    }

    private static int setDirectGlRendererDraw(CommandSourceStack source, boolean enabled) {
        ForgeVoxyRuntimeOverrides.setDirectGpuGeometryRendererActualDraw(enabled);
        if (!enabled) {
            ForgeVoxyInstance.INSTANCE.getDirectGpuGeometryRenderer().markEnabledRuntime();
            source.sendSuccess(() -> Component.literal("Voxy direct GL renderer: actual draw disabled. Draw list and shader state were kept for inspection; no toml was written."), false);
            return 1;
        }

        ForgeVoxyInstance.INSTANCE.getDirectGpuGeometryRenderer().markEnabledRuntime();
        ForgeDirectGpuGeometryShader.ShaderStatus shader = ForgeVoxyInstance.INSTANCE.getDirectGpuGeometryRenderer().prepareShaderForConfiguredMode();
        ForgeDirectGpuGeometryRendererStats rendererStatus = ForgeVoxyInstance.INSTANCE.getDirectGpuGeometryRenderer().createStatusSnapshot();
        boolean shaderReady = "MULTI_DRAW_ARRAYS_INDIRECT".equals(rendererStatus.effectiveDrawMode())
                ? shader.indirectOk()
                : "MULTI_DRAW_ARRAYS".equals(rendererStatus.effectiveDrawMode()) ? shader.multiDrawOk() : shader.ok();
        if (!shaderReady) {
            String message = String.format(
                    "Voxy direct GL renderer: actual draw requested, but shader is not ready. configuredDrawMode=%s effectiveDrawMode=%s shaderSupported=%s shaderCompiled=%s programCreated=%s multiDrawSupported=%s multiDrawShaderCompiled=%s indirectSupported=%s indirectShaderCompiled=%s indirectUnsupportedReason=%s unsupportedReason=%s multiDrawUnsupportedReason=%s lastShaderError=%s lastMultiDrawShaderError=%s lastIndirectShaderError=%s glVersion=%s glslVersion=%s. No toml was written.",
                    rendererStatus.configuredDrawMode(),
                    rendererStatus.effectiveDrawMode(),
                    shader.shaderSupported(),
                    shader.shaderCompiled(),
                    shader.programCreated(),
                    shader.multiDrawSupported(),
                    shader.multiDrawShaderCompiled(),
                    shader.indirectSupported(),
                    shader.indirectShaderCompiled(),
                    shader.indirectUnsupportedReason(),
                    shader.unsupportedReason(),
                    shader.multiDrawUnsupportedReason(),
                    shader.lastShaderError(),
                    shader.lastMultiDrawShaderError(),
                    shader.lastIndirectShaderError(),
                    shader.glVersion(),
                    shader.glslVersion()
            );
            source.sendFailure(Component.literal(message));
            return 0;
        }

        source.sendSuccess(() -> Component.literal("Voxy direct GL renderer: actual draw enabled runtime-only. Build a draw list with /voxy direct_gl_renderer_build_draw_list or /voxy direct_gl_renderer_auto_plan_once. configuredDrawMode=" + rendererStatus.configuredDrawMode() + " effectiveDrawMode=" + rendererStatus.effectiveDrawMode() + " stage=" + ForgeDirectGpuGeometryRenderState.STAGE + "."), false);
        return 1;
    }

    private static int directGlRendererShaderStatus(CommandSourceStack source) {
        ForgeDirectGpuGeometryShader.ShaderStatus status = ForgeVoxyInstance.INSTANCE.getDirectGpuGeometryRenderer().createShaderStatusSnapshot();
        ForgeDirectGpuGeometryRendererStats rendererStatus = ForgeVoxyInstance.INSTANCE.getDirectGpuGeometryRenderer().createStatusSnapshot();
        String message = String.format(
                "Voxy direct GL shader: shaderSupported=%s shaderCompiled=%s programCreated=%s loopShaderCompiled=%s multiDrawSupported=%s drawIdSupported=%s multiDrawShaderCompiled=%s multiDrawProgramCreated=%s indirectSupported=%s multiDrawIndirectSupported=%s drawIndirectBufferSupported=%s baseInstanceSupported=%s indirectShaderCompiled=%s indirectProgramCreated=%s configuredDrawMode=%s effectiveDrawMode=%s autoModeSelectedReason=%s autoModeFallbackReason=%s drawItemSsboCreated=%s drawItemSsboBytes=%d drawItemSsboGeneration=%d indirectCommandBufferCreated=%s indirectCommandBufferBytes=%d indirectCommandBufferGeneration=%d lastShaderError=%s lastMultiDrawShaderError=%s lastIndirectShaderError=%s unsupportedReason=%s multiDrawUnsupportedReason=%s indirectUnsupportedReason=%s glVersion=%s glslVersion=%s usesSsbo=%s stage=%s",
                status.shaderSupported(),
                status.shaderCompiled(),
                status.programCreated(),
                status.loopShaderCompiled(),
                status.multiDrawSupported(),
                status.drawIdSupported(),
                status.multiDrawShaderCompiled(),
                status.multiDrawProgramCreated(),
                status.indirectSupported(),
                status.multiDrawIndirectSupported(),
                status.drawIndirectBufferSupported(),
                status.baseInstanceSupported(),
                status.indirectShaderCompiled(),
                status.indirectProgramCreated(),
                rendererStatus.configuredDrawMode(),
                rendererStatus.effectiveDrawMode(),
                rendererStatus.autoModeSelectedReason(),
                rendererStatus.autoModeFallbackReason(),
                rendererStatus.drawItemSsboCreated(),
                rendererStatus.drawItemSsboBytes(),
                rendererStatus.drawItemSsboGeneration(),
                rendererStatus.indirectCommandBufferCreated(),
                rendererStatus.indirectCommandBufferBytes(),
                rendererStatus.indirectCommandBufferGeneration(),
                status.lastShaderError(),
                status.lastMultiDrawShaderError(),
                status.lastIndirectShaderError(),
                status.unsupportedReason(),
                status.multiDrawUnsupportedReason(),
                status.indirectUnsupportedReason(),
                status.glVersion(),
                status.glslVersion(),
                status.usesSsbo(),
                ForgeDirectGpuGeometryRenderState.STAGE
        );
        source.sendSuccess(() -> Component.literal(message), false);
        return status.ok() || status.multiDrawOk() || status.indirectOk() ? 1 : 0;
    }

    private static int directGlRendererIndirectAudit(CommandSourceStack source) {
        ForgeDirectGpuGeometryIndirectAuditResult result = ForgeVoxyInstance.INSTANCE.getDirectGpuGeometryRenderer().auditIndirectBuffers();
        String message = String.format(
                "Voxy direct GL renderer indirect audit: success=%s error=%s durationMs=%.2f auditedDrawItems=%d commandBytes=%d drawItemBytes=%d commandBufferMatch=%s drawItemBufferMatch=%s heapGeneration=%d dimension=%s invalidCommands=%d invalidDrawItems=%d auditedVertices=%d readbackApi=glGetNamedBufferSubData drainsIntents=false stage=%s",
                result.success(),
                result.error(),
                result.durationMs(),
                result.auditedDrawItems(),
                result.commandBytes(),
                result.drawItemBytes(),
                result.commandBufferMatch(),
                result.drawItemBufferMatch(),
                result.heapGeneration(),
                result.dimensionId(),
                result.invalidCommands(),
                result.invalidDrawItems(),
                result.auditedVertices(),
                ForgeDirectGpuGeometryRenderState.STAGE
        );
        if (result.success()) {
            source.sendSuccess(() -> Component.literal(message), false);
            return Math.max(1, result.auditedDrawItems());
        }
        source.sendFailure(Component.literal(message));
        return 0;
    }

    private static int directGlRendererIndirectAuditStatus(CommandSourceStack source) {
        var status = ForgeVoxyInstance.INSTANCE.getDirectGpuGeometryRenderer().createStatusSnapshot();
        String message = String.format(
                "Voxy direct GL renderer indirect audit: lastIndirectAuditOk=%s auditRuns=%d auditFailures=%d lastIndirectAuditError=%s lastIndirectAuditDurationMs=%.2f lastAuditedDrawItems=%d lastAuditedCommandBytes=%d lastAuditedDrawItemBytes=%d lastCommandBufferMatch=%s lastDrawItemBufferMatch=%s lastAuditHeapGeneration=%d lastAuditDimension=%s lastInvalidCommands=%d lastInvalidDrawItems=%d lastAuditedVertices=%d drawListValid=%s drawListStale=%s drawItems=%d indirectCommandBufferCreated=%s indirectCommandBufferGeneration=%d drawItemSsboCreated=%s drawItemSsboGeneration=%d currentHeapGeneration=%d currentDimension=%s stage=%s",
                status.lastIndirectAuditOk(),
                status.auditRuns(),
                status.auditFailures(),
                status.lastIndirectAuditError(),
                status.lastIndirectAuditDurationMs(),
                status.lastAuditedDrawItems(),
                status.lastAuditedCommandBytes(),
                status.lastAuditedDrawItemBytes(),
                status.lastCommandBufferMatch(),
                status.lastDrawItemBufferMatch(),
                status.lastAuditHeapGeneration(),
                status.lastAuditDimension(),
                status.lastInvalidCommands(),
                status.lastInvalidDrawItems(),
                status.lastAuditedVertices(),
                status.drawListValid(),
                status.drawListStale(),
                status.drawItems(),
                status.indirectCommandBufferCreated(),
                status.indirectCommandBufferGeneration(),
                status.drawItemSsboCreated(),
                status.drawItemSsboGeneration(),
                status.currentHeapGeneration(),
                status.currentDimension(),
                status.stage()
        );
        source.sendSuccess(() -> Component.literal(message), false);
        return status.auditFailures() == 0 ? 1 : 0;
    }

    private static int directGlRendererIndirectAuditClear(CommandSourceStack source) {
        ForgeVoxyInstance.INSTANCE.getDirectGpuGeometryRenderer().clearIndirectAuditStats();
        source.sendSuccess(() -> Component.literal("Voxy direct GL renderer indirect audit: audit counters cleared. Draw list, shaders, buffers, CPU caches, simple renderer, and upload-only GL heap were left unchanged."), false);
        return 1;
    }

    private static int directGlRendererStressOnce(CommandSourceStack source) {
        long start = System.nanoTime();
        ForgeDirectGpuGeometryRenderer renderer = ForgeVoxyInstance.INSTANCE.getDirectGpuGeometryRenderer();
        boolean sourceRegressionOk = false;
        boolean shaderSupported = false;
        boolean drawListValid = false;
        boolean loopOk = false;
        boolean multiDrawOk = false;
        boolean indirectOk = false;
        boolean indirectAuditOk = false;
        int plannedSections = 0;
        long plannedRecords = 0L;
        long glErrorCount = 0L;
        long stateRestoreFailures = 0L;
        String error = "none";
        try {
            ForgeVoxyRuntimeOverrides.setDirectGpuGeometryRenderer(true);
            ForgeVoxyRuntimeOverrides.setGeometryGpuUpload(true);
            ForgeVoxyRuntimeOverrides.setDirectGpuGeometryRendererActualDraw(false);
            renderer.markEnabledRuntime();

            renderer.setDrawMode(ForgeDirectGpuGeometryDrawMode.LOOP_PER_SECTION);
            var loopPlan = renderer.buildDrawList();
            ForgeDirectGpuGeometryShader.ShaderStatus loopShader = renderer.prepareShaderForConfiguredMode();
            shaderSupported = loopShader.shaderSupported();
            loopOk = loopPlan.success() && loopShader.ok();
            if (!loopOk && "none".equals(error)) {
                error = "loop=" + (loopPlan.success() ? loopShader.lastShaderError() : loopPlan.error());
            }

            renderer.setDrawMode(ForgeDirectGpuGeometryDrawMode.MULTI_DRAW_ARRAYS);
            var multiPlan = renderer.buildDrawList();
            ForgeDirectGpuGeometryShader.ShaderStatus multiShader = renderer.prepareShaderForConfiguredMode();
            var multiStatus = renderer.createStatusSnapshot();
            multiDrawOk = multiPlan.success() && multiShader.multiDrawOk() && multiStatus.drawItemSsboCreated();
            if (!multiDrawOk && "none".equals(error)) {
                error = "multi=" + (multiPlan.success() ? multiShader.lastMultiDrawShaderError() : multiPlan.error());
            }

            renderer.setDrawMode(ForgeDirectGpuGeometryDrawMode.MULTI_DRAW_ARRAYS_INDIRECT);
            var indirectPlan = renderer.buildDrawList();
            ForgeDirectGpuGeometryShader.ShaderStatus indirectShader = renderer.prepareShaderForConfiguredMode();
            var indirectStatus = renderer.createStatusSnapshot();
            indirectOk = indirectPlan.success()
                    && indirectShader.indirectOk()
                    && indirectStatus.drawItemSsboCreated()
                    && indirectStatus.indirectCommandBufferCreated();
            plannedSections = indirectPlan.drawItems();
            plannedRecords = indirectPlan.drawRecords();
            drawListValid = indirectPlan.success();
            if (!indirectOk && "none".equals(error)) {
                error = "indirect=" + (indirectPlan.success() ? indirectShader.lastIndirectShaderError() : indirectPlan.error());
            }
            ForgeDirectGpuGeometryIndirectAuditResult firstAudit = renderer.auditIndirectBuffers();
            indirectAuditOk = firstAudit.success();
            if (!firstAudit.success() && "none".equals(error)) {
                error = "indirectAudit=" + firstAudit.error();
            }

            renderer.clear();
            ForgeVoxyRuntimeOverrides.setDirectGpuGeometryRenderer(true);
            renderer.markEnabledRuntime();
            renderer.setDrawMode(ForgeDirectGpuGeometryDrawMode.MULTI_DRAW_ARRAYS_INDIRECT);

            var rebuildPlan = renderer.buildDrawList();
            drawListValid = drawListValid && rebuildPlan.success();
            plannedSections = rebuildPlan.drawItems();
            plannedRecords = rebuildPlan.drawRecords();
            if (!rebuildPlan.success() && "none".equals(error)) {
                error = "rebuildPlan=" + rebuildPlan.error();
            }
            ForgeDirectGpuGeometryIndirectAuditResult secondAudit = renderer.auditIndirectBuffers();
            indirectAuditOk = indirectAuditOk && secondAudit.success();
            if (!secondAudit.success() && "none".equals(error)) {
                error = "rebuildAudit=" + secondAudit.error();
            }
            ForgeVoxyRuntimeOverrides.setDirectGpuGeometryRendererActualDraw(true);

            ForgeVoxyRuntimeOverrides.setSimpleGpuMeshSource(SimpleGpuMeshSource.BUILT_SECTION);
            ForgeVoxyInstance.INSTANCE.getGpuMeshUploadManager().clear();
            boolean builtSectionSourceOk = ForgeGpuMeshUploadManager.getConfiguredSource() == SimpleGpuMeshSource.BUILT_SECTION;
            ForgeVoxyRuntimeOverrides.setGlHeapReadbackMeshSource();
            ForgeVoxyInstance.INSTANCE.getGpuMeshUploadManager().clear();
            boolean glHeapSourceOk = ForgeGpuMeshUploadManager.getConfiguredSource() == SimpleGpuMeshSource.GL_HEAP_READBACK;
            sourceRegressionOk = builtSectionSourceOk && glHeapSourceOk;
            if (!sourceRegressionOk && "none".equals(error)) {
                error = "source-regression-failed";
            }

            var finalStatus = renderer.createStatusSnapshot();
            glErrorCount = finalStatus.glErrorCount();
            stateRestoreFailures = finalStatus.stateRestoreFailures();
        } catch (RuntimeException e) {
            error = e.getClass().getSimpleName() + ": " + e.getMessage();
        }

        boolean success = "none".equals(error) && loopOk && multiDrawOk && indirectOk && indirectAuditOk && sourceRegressionOk;
        double durationMs = (System.nanoTime() - start) / 1_000_000.0D;
        renderer.recordStressResult(new ForgeDirectGpuGeometryRenderer.StressResult(
                success,
                error,
                durationMs,
                plannedSections,
                plannedRecords,
                drawListValid,
                shaderSupported,
                sourceRegressionOk,
                loopOk,
                multiDrawOk,
                indirectOk,
                indirectAuditOk,
                glErrorCount,
                stateRestoreFailures
        ));

        String message = String.format(
                "Voxy direct GL renderer stress: success=%s error=%s durationMs=%.2f plannedSections=%d plannedRecords=%d drawListValid=%s shaderSupported=%s loopOk=%s multiDrawOk=%s indirectOk=%s indirectAuditOk=%s sourceRegressionOk=%s glErrorCount=%d stateRestoreFailures=%d stage=%s",
                success,
                error,
                durationMs,
                plannedSections,
                plannedRecords,
                drawListValid,
                shaderSupported,
                loopOk,
                multiDrawOk,
                indirectOk,
                indirectAuditOk,
                sourceRegressionOk,
                glErrorCount,
                stateRestoreFailures,
                ForgeDirectGpuGeometryRenderState.STAGE
        );
        if (success) {
            source.sendSuccess(() -> Component.literal(message), false);
            return 1;
        }
        source.sendFailure(Component.literal(message));
        return 0;
    }

    private static int directGlRendererStressStatus(CommandSourceStack source) {
        var status = ForgeVoxyInstance.INSTANCE.getDirectGpuGeometryRenderer().createStatusSnapshot();
        String message = String.format(
                "Voxy direct GL renderer stress: stressRuns=%d stressFailures=%d lastStressError=%s lastStressDurationMs=%.2f lastStressPlannedSections=%d lastStressPlannedRecords=%d lastStressDrawListValid=%s lastStressShaderSupported=%s lastStressLoopOk=%s lastStressMultiDrawOk=%s lastStressIndirectOk=%s lastStressIndirectAuditOk=%s lastStressSourceRegressionOk=%s lastStressGlErrorCount=%d lastStressStateRestoreFailures=%d stage=%s",
                status.stressRuns(),
                status.stressFailures(),
                status.lastStressError(),
                status.lastStressDurationMs(),
                status.lastStressPlannedSections(),
                status.lastStressPlannedRecords(),
                status.lastStressDrawListValid(),
                status.lastStressShaderSupported(),
                status.lastStressLoopOk(),
                status.lastStressMultiDrawOk(),
                status.lastStressIndirectOk(),
                status.lastStressIndirectAuditOk(),
                status.lastStressSourceRegressionOk(),
                status.lastStressGlErrorCount(),
                status.lastStressStateRestoreFailures(),
                status.stage()
        );
        source.sendSuccess(() -> Component.literal(message), false);
        return status.stressFailures() == 0 ? 1 : 0;
    }

    private static int directGlRendererStressClear(CommandSourceStack source) {
        ForgeVoxyInstance.INSTANCE.getDirectGpuGeometryRenderer().clearStressStats();
        source.sendSuccess(() -> Component.literal("Voxy direct GL renderer stress: stress counters cleared. Renderer draw list, shader, CPU caches, simple renderer, and GL heap were left unchanged."), false);
        return 1;
    }

    private static int directGlMdicPlanSample(CommandSourceStack source) {
        ForgeMdicCommandPlanner.PlanResult result = ForgeVoxyInstance.INSTANCE.getMdicCommandManager().planSample();
        ForgeMdicCommandStats status = ForgeVoxyInstance.INSTANCE.getMdicCommandManager().createStatusSnapshot();
        String message = String.format(
                "Voxy MDIC skeleton plan: success=%s skippedReason=%s error=%s layoutVersion=%s wordsPerCommand=%d bytesPerCommand=%d uploadedSectionCandidates=%d candidateSections=%d acceptedSections=%d selectionMode=%s effectiveSelectionMode=%s selectionFallbackReason=%s frustumAvailable=%s frustumAgeMs=%.2f cameraPosition=%s cameraChunk=%s cameraSection=%s renderDistanceChunks=%d rejectedByRadius=%d rejectedByFrustum=%d rejectedByBudget=%d rejectedByMissingMetadata=%d nearestAcceptedDistance=%.2f farthestAcceptedDistance=%.2f maxPlanCandidates=%d maxSections=%d maxCommands=%d maxRecords=%d invalidMetadata=%d skippedSections=%d skippedRecords=%d bucketAware=%s includedTranslucent=%s includedDoubleSided=%s includedDirectional=%s directionalFaceMask=%s faceMaskFallbackAllWhenInside=%s faceMaskFallbackReason=%s faceMaskCommandsAccepted=%d faceMaskCommandsRejected=%d rejectedDirectionalBuckets=%d insideSectionFallbacks=%d missingCameraFallbacks=%d missingAabbFallbacks=%d plannedCommands=%d sectionCount=%d bucketCommands=%d sectionCommands=%d plannedRecords=%d plannedVertices=%d commandsPerSectionMin=%d commandsPerSectionMax=%d bucket0Commands=%d bucket1Commands=%d bucket2Commands=%d bucket3Commands=%d bucket4Commands=%d bucket5Commands=%d bucket6Commands=%d bucket7Commands=%d bucket2RejectedByFaceMask=%d bucket3RejectedByFaceMask=%d bucket4RejectedByFaceMask=%d bucket5RejectedByFaceMask=%d bucket6RejectedByFaceMask=%d bucket7RejectedByFaceMask=%d skippedEmptyBuckets=%d skippedTranslucentCommands=%d skippedBucketCommands=%d minRecordCount=%d maxRecordCount=%d avgRecordCount=%.2f bucketMaskOr=0x%02X bucketMaskAnd=0x%02X generation=%d dimension=%s durationMs=%.2f actualDrawEnabled=false stage=%s draw=false renderer=none",
                result.success(),
                result.skippedReason(),
                result.error(),
                status.layoutVersion(),
                status.wordsPerCommand(),
                status.bytesPerCommand(),
                result.uploadedSectionCandidates(),
                result.candidateSections(),
                status.lastPlanAcceptedSections(),
                status.selectionMode(),
                status.effectiveSelectionMode(),
                status.selectionFallbackReason(),
                status.frustumAvailable(),
                status.frustumAgeMs(),
                status.cameraPosition(),
                status.cameraChunk(),
                status.cameraSection(),
                status.renderDistanceChunks(),
                status.rejectedByRadius(),
                status.rejectedByFrustum(),
                status.rejectedByBudget(),
                status.rejectedByMissingMetadata(),
                status.nearestAcceptedDistance(),
                status.farthestAcceptedDistance(),
                status.maxPlanCandidates(),
                status.maxSections(),
                status.maxCommands(),
                status.maxRecords(),
                result.invalidMetadata(),
                result.skippedSections(),
                result.skippedRecords(),
                status.bucketAware(),
                status.includedTranslucent(),
                status.includedDoubleSided(),
                status.includedDirectional(),
                status.directionalFaceMask(),
                status.faceMaskFallbackAllWhenInside(),
                status.faceMaskFallbackReason(),
                status.faceMaskCommandsAccepted(),
                status.faceMaskCommandsRejected(),
                status.rejectedDirectionalBuckets(),
                status.insideSectionFallbacks(),
                status.missingCameraFallbacks(),
                status.missingAabbFallbacks(),
                status.commandCount(),
                status.sectionCount(),
                status.bucketCommands(),
                status.sectionCommands(),
                status.commandRecords(),
                status.commandVertices(),
                status.commandsPerSectionMin(),
                status.commandsPerSectionMax(),
                status.bucket0Commands(),
                status.bucket1Commands(),
                status.bucket2Commands(),
                status.bucket3Commands(),
                status.bucket4Commands(),
                status.bucket5Commands(),
                status.bucket6Commands(),
                status.bucket7Commands(),
                status.bucket2RejectedByFaceMask(),
                status.bucket3RejectedByFaceMask(),
                status.bucket4RejectedByFaceMask(),
                status.bucket5RejectedByFaceMask(),
                status.bucket6RejectedByFaceMask(),
                status.bucket7RejectedByFaceMask(),
                status.skippedEmptyBuckets(),
                status.skippedTranslucentCommands(),
                status.skippedBucketCommands(),
                status.minRecordCount(),
                status.maxRecordCount(),
                status.avgRecordCount(),
                status.bucketMaskOr(),
                status.bucketMaskAnd(),
                status.currentHeapGeneration(),
                status.commandListDimension(),
                result.durationMs(),
                status.stage()
        );
        if (result.success()) {
            source.sendSuccess(() -> Component.literal(message), false);
            return Math.max(1, status.commandCount());
        }
        source.sendFailure(Component.literal(message));
        return 0;
    }

    private static int directGlMdicBuildBuffer(CommandSourceStack source) {
        boolean success = ForgeVoxyInstance.INSTANCE.getMdicCommandManager().buildBuffer();
        ForgeMdicCommandStats status = ForgeVoxyInstance.INSTANCE.getMdicCommandManager().createStatusSnapshot();
        String message = String.format(
                "Voxy MDIC skeleton buffer: success=%s layoutVersion=%s commandListValid=%s commandListStale=%s commandBufferStale=%s lastStaleReason=%s commandCount=%d commandRecords=%d commandBufferCreated=%s commandBufferBytes=%d commandBufferGeneration=%d commandBufferDimension=%s currentHeapGeneration=%d currentDimension=%s lastBuildBufferDurationMs=%.2f lastError=%s actualDrawEnabled=false draw=false stage=%s",
                success,
                status.layoutVersion(),
                status.commandListValid(),
                status.commandListStale(),
                status.commandBufferStale(),
                status.lastStaleReason(),
                status.commandCount(),
                status.commandRecords(),
                status.commandBufferCreated(),
                status.commandBufferBytes(),
                status.commandBufferGeneration(),
                status.commandBufferDimension(),
                status.currentHeapGeneration(),
                status.currentDimension(),
                status.lastBuildBufferDurationMs(),
                status.lastError(),
                status.stage()
        );
        if (success) {
            source.sendSuccess(() -> Component.literal(message), false);
            return Math.max(1, status.commandCount());
        }
        source.sendFailure(Component.literal(message));
        return 0;
    }

    private static int directGlMdicAudit(CommandSourceStack source) {
        ForgeMdicCommandAuditResult result = ForgeVoxyInstance.INSTANCE.getMdicCommandManager().audit();
        String message = String.format(
                "Voxy MDIC skeleton audit: success=%s error=%s durationMs=%.2f commandBufferMatch=%s layoutMatch=%s generationMatch=%s dimensionMatch=%s invalidCommands=%d invalidLayoutCommands=%d invalidGenerationCommands=%d invalidDimensionCommands=%d invalidBucketMaskCommands=%d invalidBucketRangeCommands=%d invalidBucketOffsetCommands=%d invalidGeometryPtrCommands=%d invalidFaceMaskCommands=%d lastFaceMaskAuditOk=%s lastSelectionAuditOk=%s invalidSelectionCommands=%d invalidRadiusCommands=%d invalidFrustumCommands=%d auditedCommands=%d auditedRecords=%d auditedBytes=%d heapGeneration=%d dimension=%s layoutVersion=%s readbackApi=glGetNamedBufferSubData actualDrawEnabled=false draw=false stage=%s",
                result.success(),
                result.error(),
                result.durationMs(),
                result.commandBufferMatch(),
                result.layoutMatch(),
                result.generationMatch(),
                result.dimensionMatch(),
                result.invalidCommands(),
                result.invalidLayoutCommands(),
                result.invalidGenerationCommands(),
                result.invalidDimensionCommands(),
                result.invalidBucketMaskCommands(),
                result.invalidBucketRangeCommands(),
                result.invalidBucketOffsetCommands(),
                result.invalidGeometryPtrCommands(),
                result.invalidFaceMaskCommands(),
                result.faceMaskAuditOk(),
                result.selectionAuditOk(),
                result.invalidSelectionCommands(),
                result.invalidRadiusCommands(),
                result.invalidFrustumCommands(),
                result.auditedCommands(),
                result.auditedRecords(),
                result.auditedBytes(),
                result.heapGeneration(),
                result.dimensionId(),
                ForgeMdicCommandLayout.LAYOUT_VERSION,
                ForgeMdicCommandLayout.STAGE
        );
        if (result.success()) {
            source.sendSuccess(() -> Component.literal(message), false);
            return Math.max(1, result.auditedCommands());
        }
        source.sendFailure(Component.literal(message));
        return 0;
    }

    private static int directGlMdicStatus(CommandSourceStack source) {
        ForgeMdicCommandStats status = ForgeVoxyInstance.INSTANCE.getMdicCommandManager().createStatusSnapshot();
        ForgeMdicDebugDrawStats drawStatus = ForgeVoxyInstance.INSTANCE.getMdicDebugRenderer().createStatusSnapshot();
        String message = String.format(
                "Voxy MDIC skeleton: stage=%s layoutVersion=%s wordsPerCommand=%d bytesPerCommand=%d enabled=%s actualDrawEnabled=%s hasHeap=%s heapCreated=%s currentHeapGeneration=%d currentDimension=%s commandListValid=%s commandListStale=%s commandBufferStale=%s lastStaleReason=%s bucketAware=%s includedTranslucent=%s includedDoubleSided=%s includedDirectional=%s directionalFaceMask=%s faceMaskFallbackAllWhenInside=%s faceMaskFallbackReason=%s faceMaskCommandsAccepted=%d faceMaskCommandsRejected=%d rejectedDirectionalBuckets=%d insideSectionFallbacks=%d missingCameraFallbacks=%d missingAabbFallbacks=%d commandCount=%d sectionCount=%d bucketCommands=%d sectionCommands=%d commandRecords=%d commandVertices=%d commandsPerSectionMin=%d commandsPerSectionMax=%d bucket0Commands=%d bucket1Commands=%d bucket2Commands=%d bucket3Commands=%d bucket4Commands=%d bucket5Commands=%d bucket6Commands=%d bucket7Commands=%d bucket2RejectedByFaceMask=%d bucket3RejectedByFaceMask=%d bucket4RejectedByFaceMask=%d bucket5RejectedByFaceMask=%d bucket6RejectedByFaceMask=%d bucket7RejectedByFaceMask=%d skippedEmptyBuckets=%d skippedTranslucentCommands=%d skippedBucketCommands=%d minRecordCount=%d maxRecordCount=%d avgRecordCount=%.2f nonEmptyBucketCommands=%d emptyBucketCommands=%d bucketMaskOr=0x%02X bucketMaskAnd=0x%02X minGeometryPtr=%d maxGeometryPtr=%d skippedSections=%d skippedRecords=%d selectionMode=%s effectiveSelectionMode=%s selectionFallbackReason=%s frustumAvailable=%s frustumAgeMs=%.2f cameraPosition=%s cameraChunk=%s cameraSection=%s renderDistanceChunks=%d candidateSections=%d acceptedSections=%d rejectedByRadius=%d rejectedByFrustum=%d rejectedByBudget=%d rejectedByMissingMetadata=%d nearestAcceptedDistance=%.2f farthestAcceptedDistance=%.2f maxPlanCandidates=%d maxSections=%d maxCommands=%d maxRecords=%d commandListDimension=%s commandListGeneration=%d commandBufferCreated=%s commandBufferBytes=%d commandBufferGeneration=%d commandBufferDimension=%s lastPlanDurationMs=%.2f lastBuildBufferDurationMs=%.2f lastError=%s lastAuditOk=%s lastCommandBufferMatch=%s lastLayoutMatch=%s lastGenerationMatch=%s lastDimensionMatch=%s lastInvalidCommands=%d lastInvalidLayoutCommands=%d lastInvalidGenerationCommands=%d lastInvalidDimensionCommands=%d lastInvalidBucketMaskCommands=%d lastInvalidBucketRangeCommands=%d lastInvalidBucketOffsetCommands=%d lastInvalidGeometryPtrCommands=%d lastInvalidFaceMaskCommands=%d lastFaceMaskAuditOk=%s lastSelectionAuditOk=%s invalidSelectionCommands=%d invalidRadiusCommands=%d invalidFrustumCommands=%d auditRuns=%d auditFailures=%d lastAuditedCommands=%d lastAuditedRecords=%d lastAuditedBytes=%d lastAuditHeapGeneration=%d lastAuditDimension=%s mdicDebugDrawEnabled=%s mdicDebugActualDraw=%s mdicDebugStage=%s mdicDebugConfiguredDrawMode=%s mdicDebugEffectiveDrawMode=%s mdicDebugDerivedIndirectCommandBufferCreated=%s mdicDebugDerivedIndirectCommandBufferBytes=%d mdicDebugSharedIndexBufferCreated=%s mdicDebugSharedIndexBufferBytes=%d mdicDebugElementsIndirectCommandBufferCreated=%s mdicDebugElementsIndirectCommandBufferBytes=%d mdicDebugLastFrameApiDrawCalls=%d mdicDebugLastFrameLogicalCommands=%d mdicDebugLastFrameIndices=%d mdicDebugLastGlError=%s mdicDebugStateRestoreFailures=%d mdicDebugLastRenderSkippedReason=%s formalMdicRenderer=false voxyRenderSystem=false",
                status.stage(),
                status.layoutVersion(),
                status.wordsPerCommand(),
                status.bytesPerCommand(),
                status.enabled(),
                status.actualDrawEnabled(),
                status.hasHeap(),
                status.heapCreated(),
                status.currentHeapGeneration(),
                status.currentDimension(),
                status.commandListValid(),
                status.commandListStale(),
                status.commandBufferStale(),
                status.lastStaleReason(),
                status.bucketAware(),
                status.includedTranslucent(),
                status.includedDoubleSided(),
                status.includedDirectional(),
                status.directionalFaceMask(),
                status.faceMaskFallbackAllWhenInside(),
                status.faceMaskFallbackReason(),
                status.faceMaskCommandsAccepted(),
                status.faceMaskCommandsRejected(),
                status.rejectedDirectionalBuckets(),
                status.insideSectionFallbacks(),
                status.missingCameraFallbacks(),
                status.missingAabbFallbacks(),
                status.commandCount(),
                status.sectionCount(),
                status.bucketCommands(),
                status.sectionCommands(),
                status.commandRecords(),
                status.commandVertices(),
                status.commandsPerSectionMin(),
                status.commandsPerSectionMax(),
                status.bucket0Commands(),
                status.bucket1Commands(),
                status.bucket2Commands(),
                status.bucket3Commands(),
                status.bucket4Commands(),
                status.bucket5Commands(),
                status.bucket6Commands(),
                status.bucket7Commands(),
                status.bucket2RejectedByFaceMask(),
                status.bucket3RejectedByFaceMask(),
                status.bucket4RejectedByFaceMask(),
                status.bucket5RejectedByFaceMask(),
                status.bucket6RejectedByFaceMask(),
                status.bucket7RejectedByFaceMask(),
                status.skippedEmptyBuckets(),
                status.skippedTranslucentCommands(),
                status.skippedBucketCommands(),
                status.minRecordCount(),
                status.maxRecordCount(),
                status.avgRecordCount(),
                status.nonEmptyBucketCommands(),
                status.emptyBucketCommands(),
                status.bucketMaskOr(),
                status.bucketMaskAnd(),
                status.minGeometryPtr(),
                status.maxGeometryPtr(),
                status.skippedSections(),
                status.skippedRecords(),
                status.selectionMode(),
                status.effectiveSelectionMode(),
                status.selectionFallbackReason(),
                status.frustumAvailable(),
                status.frustumAgeMs(),
                status.cameraPosition(),
                status.cameraChunk(),
                status.cameraSection(),
                status.renderDistanceChunks(),
                status.lastPlanCandidateSections(),
                status.lastPlanAcceptedSections(),
                status.rejectedByRadius(),
                status.rejectedByFrustum(),
                status.rejectedByBudget(),
                status.rejectedByMissingMetadata(),
                status.nearestAcceptedDistance(),
                status.farthestAcceptedDistance(),
                status.maxPlanCandidates(),
                status.maxSections(),
                status.maxCommands(),
                status.maxRecords(),
                status.commandListDimension(),
                status.commandListGeneration(),
                status.commandBufferCreated(),
                status.commandBufferBytes(),
                status.commandBufferGeneration(),
                status.commandBufferDimension(),
                status.lastPlanDurationMs(),
                status.lastBuildBufferDurationMs(),
                status.lastError(),
                status.lastAuditOk(),
                status.lastCommandBufferMatch(),
                status.lastLayoutMatch(),
                status.lastGenerationMatch(),
                status.lastDimensionMatch(),
                status.lastInvalidCommands(),
                status.lastInvalidLayoutCommands(),
                status.lastInvalidGenerationCommands(),
                status.lastInvalidDimensionCommands(),
                status.lastInvalidBucketMaskCommands(),
                status.lastInvalidBucketRangeCommands(),
                status.lastInvalidBucketOffsetCommands(),
                status.lastInvalidGeometryPtrCommands(),
                status.lastInvalidFaceMaskCommands(),
                status.lastFaceMaskAuditOk(),
                status.lastSelectionAuditOk(),
                status.lastInvalidSelectionCommands(),
                status.lastInvalidRadiusCommands(),
                status.lastInvalidFrustumCommands(),
                status.auditRuns(),
                status.auditFailures(),
                status.lastAuditedCommands(),
                status.lastAuditedRecords(),
                status.lastAuditedBytes(),
                status.lastAuditHeapGeneration(),
                status.lastAuditDimension(),
                drawStatus.drawEnabled(),
                drawStatus.actualDrawEnabled(),
                drawStatus.stage(),
                drawStatus.configuredDrawMode(),
                drawStatus.effectiveDrawMode(),
                drawStatus.derivedIndirectCommandBufferCreated(),
                drawStatus.derivedIndirectCommandBufferBytes(),
                drawStatus.sharedIndexBufferCreated(),
                drawStatus.sharedIndexBufferBytes(),
                drawStatus.elementsIndirectCommandBufferCreated(),
                drawStatus.elementsIndirectCommandBufferBytes(),
                drawStatus.lastFrameApiDrawCalls(),
                drawStatus.lastFrameLogicalCommands(),
                drawStatus.lastFrameIndices(),
                drawStatus.lastGlError(),
                drawStatus.stateRestoreFailures(),
                drawStatus.lastRenderSkippedReason()
        );
        message = message + String.format(
                " mdicDebugElementsIndirectCountSupported=%s mdicDebugDrawCountBufferCreated=%s mdicDebugDrawCountBufferBytes=%d mdicDebugDrawCountValue=%d mdicDebugMaxDrawCount=%d mdicDebugLastDrawCountBufferMatch=%s mdicDebugLastInvalidDrawCount=%d",
                drawStatus.elementsIndirectCountSupported(),
                drawStatus.drawCountBufferCreated(),
                drawStatus.drawCountBufferBytes(),
                drawStatus.drawCountValue(),
                drawStatus.maxDrawCount(),
                drawStatus.lastDrawCountBufferMatch(),
                drawStatus.lastInvalidDrawCount()
        );
        String mdicStatusMessage = message;
        source.sendSuccess(() -> Component.literal(mdicStatusMessage), false);
        return status.enabled() ? 1 : 0;
    }

    private static int directGlMdicAuditStatus(CommandSourceStack source) {
        ForgeMdicCommandStats status = ForgeVoxyInstance.INSTANCE.getMdicCommandManager().createStatusSnapshot();
        String message = String.format(
                "Voxy MDIC skeleton audit: auditRuns=%d auditFailures=%d lastAuditError=%s lastAuditDurationMs=%.2f lastAuditOk=%s lastCommandBufferMatch=%s lastLayoutMatch=%s lastGenerationMatch=%s lastDimensionMatch=%s lastInvalidCommands=%d lastInvalidLayoutCommands=%d lastInvalidGenerationCommands=%d lastInvalidDimensionCommands=%d lastInvalidBucketMaskCommands=%d lastInvalidBucketRangeCommands=%d lastInvalidBucketOffsetCommands=%d lastInvalidGeometryPtrCommands=%d lastInvalidFaceMaskCommands=%d lastFaceMaskAuditOk=%s lastSelectionAuditOk=%s invalidSelectionCommands=%d invalidRadiusCommands=%d invalidFrustumCommands=%d selectionMode=%s effectiveSelectionMode=%s selectionFallbackReason=%s frustumAvailable=%s rejectedByRadius=%d rejectedByFrustum=%d directionalFaceMask=%s faceMaskFallbackReason=%s faceMaskCommandsAccepted=%d faceMaskCommandsRejected=%d rejectedDirectionalBuckets=%d bucket2RejectedByFaceMask=%d bucket3RejectedByFaceMask=%d bucket4RejectedByFaceMask=%d bucket5RejectedByFaceMask=%d bucket6RejectedByFaceMask=%d bucket7RejectedByFaceMask=%d lastAuditedCommands=%d lastAuditedRecords=%d lastAuditedBytes=%d lastAuditHeapGeneration=%d lastAuditDimension=%s bucketAware=%s bucketCommands=%d sectionCommands=%d skippedEmptyBuckets=%d skippedTranslucentCommands=%d skippedBucketCommands=%d commandListValid=%s commandListStale=%s commandBufferCreated=%s commandBufferStale=%s commandBufferBytes=%d layoutVersion=%s stage=%s",
                status.auditRuns(),
                status.auditFailures(),
                status.lastAuditError(),
                status.lastAuditDurationMs(),
                status.lastAuditOk(),
                status.lastCommandBufferMatch(),
                status.lastLayoutMatch(),
                status.lastGenerationMatch(),
                status.lastDimensionMatch(),
                status.lastInvalidCommands(),
                status.lastInvalidLayoutCommands(),
                status.lastInvalidGenerationCommands(),
                status.lastInvalidDimensionCommands(),
                status.lastInvalidBucketMaskCommands(),
                status.lastInvalidBucketRangeCommands(),
                status.lastInvalidBucketOffsetCommands(),
                status.lastInvalidGeometryPtrCommands(),
                status.lastInvalidFaceMaskCommands(),
                status.lastFaceMaskAuditOk(),
                status.lastSelectionAuditOk(),
                status.lastInvalidSelectionCommands(),
                status.lastInvalidRadiusCommands(),
                status.lastInvalidFrustumCommands(),
                status.selectionMode(),
                status.effectiveSelectionMode(),
                status.selectionFallbackReason(),
                status.frustumAvailable(),
                status.rejectedByRadius(),
                status.rejectedByFrustum(),
                status.directionalFaceMask(),
                status.faceMaskFallbackReason(),
                status.faceMaskCommandsAccepted(),
                status.faceMaskCommandsRejected(),
                status.rejectedDirectionalBuckets(),
                status.bucket2RejectedByFaceMask(),
                status.bucket3RejectedByFaceMask(),
                status.bucket4RejectedByFaceMask(),
                status.bucket5RejectedByFaceMask(),
                status.bucket6RejectedByFaceMask(),
                status.bucket7RejectedByFaceMask(),
                status.lastAuditedCommands(),
                status.lastAuditedRecords(),
                status.lastAuditedBytes(),
                status.lastAuditHeapGeneration(),
                status.lastAuditDimension(),
                status.bucketAware(),
                status.bucketCommands(),
                status.sectionCommands(),
                status.skippedEmptyBuckets(),
                status.skippedTranslucentCommands(),
                status.skippedBucketCommands(),
                status.commandListValid(),
                status.commandListStale(),
                status.commandBufferCreated(),
                status.commandBufferStale(),
                status.commandBufferBytes(),
                status.layoutVersion(),
                status.stage()
        );
        source.sendSuccess(() -> Component.literal(message), false);
        return status.auditFailures() == 0 ? 1 : 0;
    }

    private static int directGlMdicAuditClear(CommandSourceStack source) {
        ForgeVoxyInstance.INSTANCE.getMdicCommandManager().clearAuditStats();
        source.sendSuccess(() -> Component.literal("Voxy MDIC skeleton audit: audit counters cleared. Command list, command buffer, geometry heap, direct renderer, and simple renderer were left unchanged."), false);
        return 1;
    }

    private static int directGlMdicStressOnce(CommandSourceStack source) {
        ForgeMdicCommandStats status = ForgeVoxyInstance.INSTANCE.getMdicCommandManager().stressOnce();
        String message = String.format(
                "Voxy MDIC skeleton stress: success=%s stressRuns=%d stressFailures=%d lastStressError=%s lastStressDurationMs=%.2f lastStressPlanOk=%s lastStressBuildOk=%s lastStressAuditOk=%s lastStressClearOk=%s lastStressHeapClearOk=%s lastStressRebuildOk=%s lastStressReauditOk=%s lastStressSourceRegressionOk=%s lastStressBucketAwareOk=%s lastStressBucketAuditOk=%s lastStressDirectionalFaceMaskOk=%s lastStressFaceMaskAuditOk=%s lastStressVisibilityPlanOk=%s lastStressSelectionAuditOk=%s lastStressFrustumFallbackOk=%s lastStressCommandCount=%d lastStressCommandRecords=%d lastStressInvalidCommands=%d commandListValid=%s commandBufferCreated=%s layoutVersion=%s stage=%s actualDrawEnabled=false draw=false",
                "none".equals(status.lastStressError()),
                status.stressRuns(),
                status.stressFailures(),
                status.lastStressError(),
                status.lastStressDurationMs(),
                status.lastStressPlanOk(),
                status.lastStressBuildOk(),
                status.lastStressAuditOk(),
                status.lastStressClearOk(),
                status.lastStressHeapClearOk(),
                status.lastStressRebuildOk(),
                status.lastStressReauditOk(),
                status.lastStressSourceRegressionOk(),
                status.lastStressBucketAwareOk(),
                status.lastStressBucketAuditOk(),
                status.lastStressDirectionalFaceMaskOk(),
                status.lastStressFaceMaskAuditOk(),
                status.lastStressVisibilityPlanOk(),
                status.lastStressSelectionAuditOk(),
                status.lastStressFrustumFallbackOk(),
                status.lastStressCommandCount(),
                status.lastStressCommandRecords(),
                status.lastStressInvalidCommands(),
                status.commandListValid(),
                status.commandBufferCreated(),
                status.layoutVersion(),
                status.stage()
        );
        if ("none".equals(status.lastStressError())) {
            source.sendSuccess(() -> Component.literal(message), false);
            return 1;
        }
        source.sendFailure(Component.literal(message));
        return 0;
    }

    private static int directGlMdicStressStatus(CommandSourceStack source) {
        ForgeMdicCommandStats status = ForgeVoxyInstance.INSTANCE.getMdicCommandManager().createStatusSnapshot();
        String message = String.format(
                "Voxy MDIC skeleton stress: stressRuns=%d stressFailures=%d lastStressError=%s lastStressDurationMs=%.2f lastStressPlanOk=%s lastStressBuildOk=%s lastStressAuditOk=%s lastStressClearOk=%s lastStressHeapClearOk=%s lastStressRebuildOk=%s lastStressReauditOk=%s lastStressSourceRegressionOk=%s lastStressBucketAwareOk=%s lastStressBucketAuditOk=%s lastStressDirectionalFaceMaskOk=%s lastStressFaceMaskAuditOk=%s lastStressVisibilityPlanOk=%s lastStressSelectionAuditOk=%s lastStressFrustumFallbackOk=%s lastStressCommandCount=%d lastStressCommandRecords=%d lastStressInvalidCommands=%d layoutVersion=%s stage=%s actualDrawEnabled=false draw=false",
                status.stressRuns(),
                status.stressFailures(),
                status.lastStressError(),
                status.lastStressDurationMs(),
                status.lastStressPlanOk(),
                status.lastStressBuildOk(),
                status.lastStressAuditOk(),
                status.lastStressClearOk(),
                status.lastStressHeapClearOk(),
                status.lastStressRebuildOk(),
                status.lastStressReauditOk(),
                status.lastStressSourceRegressionOk(),
                status.lastStressBucketAwareOk(),
                status.lastStressBucketAuditOk(),
                status.lastStressDirectionalFaceMaskOk(),
                status.lastStressFaceMaskAuditOk(),
                status.lastStressVisibilityPlanOk(),
                status.lastStressSelectionAuditOk(),
                status.lastStressFrustumFallbackOk(),
                status.lastStressCommandCount(),
                status.lastStressCommandRecords(),
                status.lastStressInvalidCommands(),
                status.layoutVersion(),
                status.stage()
        );
        source.sendSuccess(() -> Component.literal(message), false);
        return status.stressFailures() == 0 ? 1 : 0;
    }

    private static int directGlMdicStressClear(CommandSourceStack source) {
        ForgeVoxyInstance.INSTANCE.getMdicCommandManager().clearStressStats();
        source.sendSuccess(() -> Component.literal("Voxy MDIC skeleton stress: stress counters cleared. Command list, command buffer, audit state, geometry heap, direct renderer, and simple renderer were left unchanged."), false);
        return 1;
    }

    private static int setDirectGlMdicDraw(CommandSourceStack source, boolean enabled) {
        if (enabled) {
            ForgeVoxyInstance.INSTANCE.getMdicDebugRenderer().enableDraw();
        } else {
            ForgeVoxyInstance.INSTANCE.getMdicDebugRenderer().disableDraw();
        }
        ForgeMdicDebugDrawStats status = ForgeVoxyInstance.INSTANCE.getMdicDebugRenderer().createStatusSnapshot();
        String message = enabled
                ? String.format("Voxy MDIC debug draw: runtime-only actual draw enabled. stage=%s drawEnabled=%s actualDrawEnabled=%s commandBufferCreated=%s commandListValid=%s shaderSupported=%s shaderCompiled=%s programCreated=%s configuredDrawMode=%s effectiveDrawMode=%s formalMdicRenderer=false voxyRenderSystem=false",
                status.stage(),
                status.drawEnabled(),
                status.actualDrawEnabled(),
                status.commandBufferCreated(),
                status.commandListValid(),
                status.shaderSupported(),
                status.shaderCompiled(),
                status.programCreated(),
                status.configuredDrawMode(),
                status.effectiveDrawMode())
                : String.format("Voxy MDIC debug draw: runtime-only actual draw disabled. stage=%s lastRenderSkippedReason=%s formalMdicRenderer=false voxyRenderSystem=false",
                status.stage(),
                status.lastRenderSkippedReason());
        source.sendSuccess(() -> Component.literal(message), false);
        return 1;
    }

    private static int setDirectGlMdicDrawMode(CommandSourceStack source, ForgeMdicDebugDrawMode mode) {
        ForgeVoxyInstance.INSTANCE.getMdicDebugRenderer().setDrawMode(mode);
        ForgeMdicDebugDrawStats status = ForgeVoxyInstance.INSTANCE.getMdicDebugRenderer().createStatusSnapshot();
        String message = String.format(
                "Voxy MDIC debug draw mode: configuredDrawMode=%s effectiveDrawMode=%s autoModeSelectedReason=%s autoModeFallbackReason=%s multiDrawSupported=%s indirectSupported=%s elementsIndirectSupported=%s elementsIndirectCountSupported=%s elementsIndirectUnsupportedReason=%s elementsIndirectCountUnsupportedReason=%s indirectParametersSupported=%s parameterBufferSupported=%s drawCountBufferSupported=%s drawIdSupported=%s baseInstanceSupported=%s derivedIndirectCommandBufferCreated=%s derivedIndirectCommandBufferBytes=%d elementsIndirectCommandBufferCreated=%s elementsIndirectCommandBufferBytes=%d drawCountBufferCreated=%s drawCountValue=%d maxDrawCount=%d sharedIndexBufferCreated=%s sharedIndexBufferBytes=%d. Runtime-only; no toml was written.",
                status.configuredDrawMode(),
                status.effectiveDrawMode(),
                status.autoModeSelectedReason(),
                status.autoModeFallbackReason(),
                status.multiDrawSupported(),
                status.indirectSupported(),
                status.elementsIndirectSupported(),
                status.elementsIndirectCountSupported(),
                status.elementsIndirectUnsupportedReason(),
                status.elementsIndirectCountUnsupportedReason(),
                status.indirectParametersSupported(),
                status.parameterBufferSupported(),
                status.drawCountBufferSupported(),
                status.drawIdSupported(),
                status.baseInstanceSupported(),
                status.derivedIndirectCommandBufferCreated(),
                status.derivedIndirectCommandBufferBytes(),
                status.elementsIndirectCommandBufferCreated(),
                status.elementsIndirectCommandBufferBytes(),
                status.drawCountBufferCreated(),
                status.drawCountValue(),
                status.maxDrawCount(),
                status.sharedIndexBufferCreated(),
                status.sharedIndexBufferBytes()
        );
        source.sendSuccess(() -> Component.literal(message), false);
        return 1;
    }

    private static int directGlMdicDrawStatus(CommandSourceStack source) {
        ForgeMdicDebugDrawStats status = ForgeVoxyInstance.INSTANCE.getMdicDebugRenderer().createStatusSnapshot();
        String message = String.format(
                "Voxy MDIC debug draw: stage=%s drawEnabled=%s actualDrawEnabled=%s configuredDrawMode=%s effectiveDrawMode=%s autoModeSelectedReason=%s autoModeFallbackReason=%s multiDrawSupported=%s indirectSupported=%s elementsIndirectSupported=%s elementsIndirectUnsupportedReason=%s drawIdSupported=%s baseInstanceSupported=%s shaderSupported=%s shaderCompiled=%s programCreated=%s loopShaderCompiled=%s multiDrawShaderCompiled=%s indirectShaderCompiled=%s elementsIndirectShaderCompiled=%s elementsIndirectUsesBaseInstance=%s commandBufferCreated=%s commandListValid=%s commandListStale=%s commandBufferStale=%s derivedIndirectCommandBufferCreated=%s derivedIndirectCommandBufferBytes=%d derivedIndirectCommandBufferStale=%s sharedIndexBufferCreated=%s sharedIndexBufferBytes=%d sharedIndexMaxRecords=%d sharedIndexFormat=%s elementsIndirectCommandBufferCreated=%s elementsIndirectCommandBufferBytes=%d elementsIndirectCommandBufferStale=%s lastElementsIndirectCommandBufferMatch=%s lastInvalidElementsIndirectCommands=%d hasHeap=%s heapCreated=%s currentHeapGeneration=%d currentDimension=%s commandListGeneration=%d commandBufferGeneration=%d commandListDimension=%s commandBufferDimension=%s commandCount=%d commandRecords=%d maxCommands=%d maxRecords=%d alpha=%.2f ignoreDepth=%s doubleSided=%s lastFrameApiDrawCalls=%d lastFrameLogicalCommands=%d lastFrameVertices=%d lastFrameIndices=%d drawApiCallsIssued=%d logicalCommandsDrawn=%d verticesDrawn=%d indicesDrawn=%d lastFrameRenderMs=%.2f maxFrameRenderMs=%.2f avgFrameRenderMs=%.2f lastFrameOverBudget=%s overBudgetFrames=%d frameBudgetMs=%.2f lastIndirectAuditOk=%s indirectAuditRuns=%d indirectAuditFailures=%d lastIndirectAuditError=%s lastAuditedIndirectCommands=%d lastAuditedIndirectBytes=%d lastInvalidIndirectCommands=%d lastIndirectCommandBufferMatch=%s lastIndirectAuditVertices=%d lastElementsIndirectAuditOk=%s elementsIndirectAuditRuns=%d elementsIndirectAuditFailures=%d lastElementsIndirectAuditError=%s lastAuditedElementsIndirectCommands=%d lastAuditedElementsIndirectBytes=%d lastElementsIndirectAuditIndices=%d lastElementsIndirectAuditLogicalVertices=%d lastGlError=%s lastGlErrorStage=%s glErrorCount=%d stateRestoreFailures=%d lastStateRestoreError=%s lastRenderSkippedReason=%s lastDrawError=%s stressRuns=%d stressFailures=%d lastStressError=%s formalMdicRenderer=false voxyRenderSystem=false shaderpack=false",
                status.stage(),
                status.drawEnabled(),
                status.actualDrawEnabled(),
                status.configuredDrawMode(),
                status.effectiveDrawMode(),
                status.autoModeSelectedReason(),
                status.autoModeFallbackReason(),
                status.multiDrawSupported(),
                status.indirectSupported(),
                status.elementsIndirectSupported(),
                status.elementsIndirectUnsupportedReason(),
                status.drawIdSupported(),
                status.baseInstanceSupported(),
                status.shaderSupported(),
                status.shaderCompiled(),
                status.programCreated(),
                status.loopShaderCompiled(),
                status.multiDrawShaderCompiled(),
                status.indirectShaderCompiled(),
                status.elementsIndirectShaderCompiled(),
                status.elementsIndirectUsesBaseInstance(),
                status.commandBufferCreated(),
                status.commandListValid(),
                status.commandListStale(),
                status.commandBufferStale(),
                status.derivedIndirectCommandBufferCreated(),
                status.derivedIndirectCommandBufferBytes(),
                status.derivedIndirectCommandBufferStale(),
                status.sharedIndexBufferCreated(),
                status.sharedIndexBufferBytes(),
                status.sharedIndexMaxRecords(),
                status.sharedIndexFormat(),
                status.elementsIndirectCommandBufferCreated(),
                status.elementsIndirectCommandBufferBytes(),
                status.elementsIndirectCommandBufferStale(),
                status.lastElementsIndirectCommandBufferMatch(),
                status.lastInvalidElementsIndirectCommands(),
                status.hasHeap(),
                status.heapCreated(),
                status.currentHeapGeneration(),
                status.currentDimension(),
                status.commandListGeneration(),
                status.commandBufferGeneration(),
                status.commandListDimension(),
                status.commandBufferDimension(),
                status.commandCount(),
                status.commandRecords(),
                status.maxCommands(),
                status.maxRecords(),
                status.alpha(),
                status.ignoreDepth(),
                status.doubleSided(),
                status.lastFrameApiDrawCalls(),
                status.lastFrameLogicalCommands(),
                status.lastFrameVertices(),
                status.lastFrameIndices(),
                status.drawApiCallsIssued(),
                status.logicalCommandsDrawn(),
                status.verticesDrawn(),
                status.indicesDrawn(),
                status.lastFrameRenderMs(),
                status.maxFrameRenderMs(),
                status.avgFrameRenderMs(),
                status.lastFrameOverBudget(),
                status.overBudgetFrames(),
                status.frameBudgetMs(),
                status.lastIndirectAuditOk(),
                status.indirectAuditRuns(),
                status.indirectAuditFailures(),
                status.lastIndirectAuditError(),
                status.lastAuditedIndirectCommands(),
                status.lastAuditedIndirectBytes(),
                status.lastInvalidIndirectCommands(),
                status.lastIndirectCommandBufferMatch(),
                status.lastIndirectAuditVertices(),
                status.lastElementsIndirectAuditOk(),
                status.elementsIndirectAuditRuns(),
                status.elementsIndirectAuditFailures(),
                status.lastElementsIndirectAuditError(),
                status.lastAuditedElementsIndirectCommands(),
                status.lastAuditedElementsIndirectBytes(),
                status.lastElementsIndirectAuditIndices(),
                status.lastElementsIndirectAuditLogicalVertices(),
                status.lastGlError(),
                status.lastGlErrorStage(),
                status.glErrorCount(),
                status.stateRestoreFailures(),
                status.lastStateRestoreError(),
                status.lastRenderSkippedReason(),
                status.lastDrawError(),
                status.stressRuns(),
                status.stressFailures(),
                status.lastStressError()
        );
        message = message + String.format(
                " elementsIndirectCountSupported=%s elementsIndirectCountUnsupportedReason=%s indirectParametersSupported=%s parameterBufferSupported=%s multiDrawElementsIndirectCountSupported=%s drawCountBufferSupported=%s elementsIndirectCountShaderCompiled=%s drawCountBufferCreated=%s drawCountBufferBytes=%d drawCountValue=%d maxDrawCount=%d drawCountBufferGeneration=%d drawCountBufferDimension=%s drawCountBufferStale=%s lastDrawCountAuditOk=%s drawCountAuditRuns=%d drawCountAuditFailures=%d lastDrawCountAuditError=%s lastAuditedDrawCount=%d lastAuditedMaxDrawCount=%d lastInvalidDrawCount=%d lastDrawCountBufferMatch=%s lastDrawCountAuditGeneration=%d lastDrawCountAuditDimension=%s",
                status.elementsIndirectCountSupported(),
                status.elementsIndirectCountUnsupportedReason(),
                status.indirectParametersSupported(),
                status.parameterBufferSupported(),
                status.multiDrawElementsIndirectCountSupported(),
                status.drawCountBufferSupported(),
                status.elementsIndirectCountShaderCompiled(),
                status.drawCountBufferCreated(),
                status.drawCountBufferBytes(),
                status.drawCountValue(),
                status.maxDrawCount(),
                status.drawCountBufferGeneration(),
                status.drawCountBufferDimension(),
                status.drawCountBufferStale(),
                status.lastDrawCountAuditOk(),
                status.drawCountAuditRuns(),
                status.drawCountAuditFailures(),
                status.lastDrawCountAuditError(),
                status.lastAuditedDrawCount(),
                status.lastAuditedMaxDrawCount(),
                status.lastInvalidDrawCount(),
                status.lastDrawCountBufferMatch(),
                status.lastDrawCountAuditGeneration(),
                status.lastDrawCountAuditDimension()
        );
        String displayMessage = message;
        source.sendSuccess(() -> Component.literal(displayMessage), false);
        return status.actualDrawEnabled() ? 1 : 0;
    }

    private static int directGlMdicShaderStatus(CommandSourceStack source) {
        ForgeMdicDebugShader.ShaderStatus status = ForgeVoxyInstance.INSTANCE.getMdicDebugRenderer().createShaderStatusSnapshot();
        String message = String.format(
                "Voxy MDIC debug shader: shaderSupported=%s shaderCompiled=%s programCreated=%s loopShaderCompiled=%s multiDrawSupported=%s drawIdSupported=%s multiDrawShaderCompiled=%s multiDrawProgramCreated=%s indirectSupported=%s multiDrawIndirectSupported=%s drawIndirectBufferSupported=%s baseInstanceSupported=%s indirectShaderCompiled=%s indirectProgramCreated=%s elementsIndirectSupported=%s elementsIndirectCountSupported=%s indirectParametersSupported=%s parameterBufferSupported=%s multiDrawElementsIndirectCountSupported=%s drawCountBufferSupported=%s elementsIndirectShaderCompiled=%s elementsIndirectProgramCreated=%s elementsIndirectCountShaderCompiled=%s elementsIndirectCountProgramCreated=%s elementsIndirectUsesBaseInstance=%s usesSsbo=%s lastShaderError=%s lastMultiDrawShaderError=%s lastIndirectShaderError=%s lastElementsIndirectShaderError=%s lastElementsIndirectCountShaderError=%s unsupportedReason=%s multiDrawUnsupportedReason=%s indirectUnsupportedReason=%s elementsIndirectUnsupportedReason=%s elementsIndirectCountUnsupportedReason=%s glVersion=%s glslVersion=%s stage=%s formalMdicRenderer=false voxyRenderSystem=false",
                status.shaderSupported(),
                status.shaderCompiled(),
                status.programCreated(),
                status.shaderCompiled(),
                status.multiDrawSupported(),
                status.drawIdSupported(),
                status.multiDrawShaderCompiled(),
                status.multiDrawProgramCreated(),
                status.indirectSupported(),
                status.multiDrawIndirectSupported(),
                status.drawIndirectBufferSupported(),
                status.baseInstanceSupported(),
                status.indirectShaderCompiled(),
                status.indirectProgramCreated(),
                status.elementsIndirectSupported(),
                status.elementsIndirectCountSupported(),
                status.indirectParametersSupported(),
                status.parameterBufferSupported(),
                status.multiDrawElementsIndirectCountSupported(),
                status.drawCountBufferSupported(),
                status.elementsIndirectShaderCompiled(),
                status.elementsIndirectProgramCreated(),
                status.elementsIndirectCountShaderCompiled(),
                status.elementsIndirectCountProgramCreated(),
                status.elementsIndirectUsesBaseInstance(),
                status.usesSsbo(),
                status.lastShaderError(),
                status.lastMultiDrawShaderError(),
                status.lastIndirectShaderError(),
                status.lastElementsIndirectShaderError(),
                status.lastElementsIndirectCountShaderError(),
                status.unsupportedReason(),
                status.multiDrawUnsupportedReason(),
                status.indirectUnsupportedReason(),
                status.elementsIndirectUnsupportedReason(),
                status.elementsIndirectCountUnsupportedReason(),
                status.glVersion(),
                status.glslVersion(),
                ForgeMdicDebugRenderer.STAGE
        );
        source.sendSuccess(() -> Component.literal(message), false);
        return status.ok() || status.multiDrawShaderCompiled() || status.indirectShaderCompiled() || status.elementsIndirectShaderCompiled() || status.elementsIndirectCountShaderCompiled() ? 1 : 0;
    }

    private static int directGlMdicDrawClear(CommandSourceStack source) {
        ForgeVoxyInstance.INSTANCE.getMdicDebugRenderer().clear();
        source.sendSuccess(() -> Component.literal("Voxy MDIC debug draw: shader, draw counters, derived arrays indirect command buffer, shared index buffer, elements indirect command buffer, draw count buffer, indirect audit state, elements indirect audit state, draw count audit state, and transient render state cleared. MDIC command list/buffer, upload-only GL heap, G5 direct renderer, simple renderer, CPU caches, and SectionGeometryManager were left unchanged."), false);
        return 1;
    }

    private static int directGlMdicDrawIndirectAudit(CommandSourceStack source) {
        ForgeMdicDebugIndirectAuditResult result = ForgeVoxyInstance.INSTANCE.getMdicDebugRenderer().auditDerivedIndirectBuffer();
        ForgeMdicDebugDrawStats status = ForgeVoxyInstance.INSTANCE.getMdicDebugRenderer().createStatusSnapshot();
        String message = String.format(
                "Voxy MDIC debug derived indirect audit: success=%s error=%s durationMs=%.2f auditedCommands=%d auditedBytes=%d invalidIndirectCommands=%d indirectCommandBufferMatch=%s auditedVertices=%d effectiveDrawMode=%s derivedIndirectCommandBufferCreated=%s stage=%s",
                result.success(),
                result.error(),
                result.durationMs(),
                result.auditedCommands(),
                result.auditedBytes(),
                result.invalidCommands(),
                result.commandBufferMatch(),
                result.auditedVertices(),
                status.effectiveDrawMode(),
                status.derivedIndirectCommandBufferCreated(),
                status.stage()
        );
        if (result.success()) {
            source.sendSuccess(() -> Component.literal(message), false);
            return 1;
        }
        source.sendFailure(Component.literal(message));
        return 0;
    }

    private static int directGlMdicDrawIndirectAuditStatus(CommandSourceStack source) {
        ForgeMdicDebugDrawStats status = ForgeVoxyInstance.INSTANCE.getMdicDebugRenderer().createStatusSnapshot();
        String message = String.format(
                "Voxy MDIC debug derived indirect audit: lastIndirectAuditOk=%s auditRuns=%d auditFailures=%d lastIndirectAuditError=%s lastIndirectAuditDurationMs=%.2f lastAuditedIndirectCommands=%d lastAuditedIndirectBytes=%d lastInvalidIndirectCommands=%d lastIndirectCommandBufferMatch=%s lastIndirectAuditVertices=%d derivedIndirectCommandBufferCreated=%s derivedIndirectCommandBufferBytes=%d derivedIndirectCommandBufferStale=%s commandCount=%d commandRecords=%d currentHeapGeneration=%d currentDimension=%s stage=%s",
                status.lastIndirectAuditOk(),
                status.indirectAuditRuns(),
                status.indirectAuditFailures(),
                status.lastIndirectAuditError(),
                status.lastIndirectAuditDurationMs(),
                status.lastAuditedIndirectCommands(),
                status.lastAuditedIndirectBytes(),
                status.lastInvalidIndirectCommands(),
                status.lastIndirectCommandBufferMatch(),
                status.lastIndirectAuditVertices(),
                status.derivedIndirectCommandBufferCreated(),
                status.derivedIndirectCommandBufferBytes(),
                status.derivedIndirectCommandBufferStale(),
                status.commandCount(),
                status.commandRecords(),
                status.currentHeapGeneration(),
                status.currentDimension(),
                status.stage()
        );
        source.sendSuccess(() -> Component.literal(message), false);
        return status.lastIndirectAuditOk() ? 1 : 0;
    }

    private static int directGlMdicDrawIndirectAuditClear(CommandSourceStack source) {
        ForgeVoxyInstance.INSTANCE.getMdicDebugRenderer().clearIndirectAuditStats();
        source.sendSuccess(() -> Component.literal("Voxy MDIC debug derived indirect audit: audit counters cleared. Derived indirect command buffer, MDIC command list/buffer, upload-only GL heap, and render state were left unchanged."), false);
        return 1;
    }

    private static int directGlMdicDrawElementsIndirectAudit(CommandSourceStack source) {
        ForgeMdicDebugElementsIndirectAuditResult result = ForgeVoxyInstance.INSTANCE.getMdicDebugRenderer().auditElementsIndirectBuffer();
        ForgeMdicDebugDrawStats status = ForgeVoxyInstance.INSTANCE.getMdicDebugRenderer().createStatusSnapshot();
        String message = String.format(
                "Voxy MDIC debug elements indirect audit: success=%s error=%s durationMs=%.2f auditedCommands=%d auditedBytes=%d invalidElementsIndirectCommands=%d elementsIndirectCommandBufferMatch=%s auditedIndices=%d auditedLogicalVertices=%d effectiveDrawMode=%s sharedIndexBufferCreated=%s elementsIndirectCommandBufferCreated=%s stage=%s",
                result.success(),
                result.error(),
                result.durationMs(),
                result.auditedCommands(),
                result.auditedBytes(),
                result.invalidCommands(),
                result.commandBufferMatch(),
                result.auditedIndices(),
                result.auditedLogicalVertices(),
                status.effectiveDrawMode(),
                status.sharedIndexBufferCreated(),
                status.elementsIndirectCommandBufferCreated(),
                status.stage()
        );
        if (result.success()) {
            source.sendSuccess(() -> Component.literal(message), false);
            return 1;
        }
        source.sendFailure(Component.literal(message));
        return 0;
    }

    private static int directGlMdicDrawElementsIndirectAuditStatus(CommandSourceStack source) {
        ForgeMdicDebugDrawStats status = ForgeVoxyInstance.INSTANCE.getMdicDebugRenderer().createStatusSnapshot();
        String message = String.format(
                "Voxy MDIC debug elements indirect audit: lastElementsIndirectAuditOk=%s auditRuns=%d auditFailures=%d lastElementsIndirectAuditError=%s lastElementsIndirectAuditDurationMs=%.2f lastAuditedElementsIndirectCommands=%d lastAuditedElementsIndirectBytes=%d lastInvalidElementsIndirectCommands=%d lastElementsIndirectCommandBufferMatch=%s lastElementsIndirectAuditIndices=%d lastElementsIndirectAuditLogicalVertices=%d sharedIndexBufferCreated=%s sharedIndexBufferBytes=%d sharedIndexMaxRecords=%d sharedIndexFormat=%s elementsIndirectCommandBufferCreated=%s elementsIndirectCommandBufferBytes=%d elementsIndirectCommandBufferStale=%s commandCount=%d commandRecords=%d currentHeapGeneration=%d currentDimension=%s stage=%s",
                status.lastElementsIndirectAuditOk(),
                status.elementsIndirectAuditRuns(),
                status.elementsIndirectAuditFailures(),
                status.lastElementsIndirectAuditError(),
                status.lastElementsIndirectAuditDurationMs(),
                status.lastAuditedElementsIndirectCommands(),
                status.lastAuditedElementsIndirectBytes(),
                status.lastInvalidElementsIndirectCommands(),
                status.lastElementsIndirectCommandBufferMatch(),
                status.lastElementsIndirectAuditIndices(),
                status.lastElementsIndirectAuditLogicalVertices(),
                status.sharedIndexBufferCreated(),
                status.sharedIndexBufferBytes(),
                status.sharedIndexMaxRecords(),
                status.sharedIndexFormat(),
                status.elementsIndirectCommandBufferCreated(),
                status.elementsIndirectCommandBufferBytes(),
                status.elementsIndirectCommandBufferStale(),
                status.commandCount(),
                status.commandRecords(),
                status.currentHeapGeneration(),
                status.currentDimension(),
                status.stage()
        );
        source.sendSuccess(() -> Component.literal(message), false);
        return status.lastElementsIndirectAuditOk() ? 1 : 0;
    }

    private static int directGlMdicDrawElementsIndirectAuditClear(CommandSourceStack source) {
        ForgeVoxyInstance.INSTANCE.getMdicDebugRenderer().clearElementsIndirectAuditStats();
        source.sendSuccess(() -> Component.literal("Voxy MDIC debug elements indirect audit: audit counters cleared. Shared index buffer, elements indirect command buffer, MDIC command list/buffer, upload-only GL heap, and render state were left unchanged."), false);
        return 1;
    }

    private static int directGlMdicDrawCountAudit(CommandSourceStack source) {
        ForgeMdicDebugDrawCountAuditResult result = ForgeVoxyInstance.INSTANCE.getMdicDebugRenderer().auditDrawCountBuffer();
        ForgeMdicDebugDrawStats status = ForgeVoxyInstance.INSTANCE.getMdicDebugRenderer().createStatusSnapshot();
        String message = String.format(
                "Voxy MDIC debug draw count audit: success=%s error=%s durationMs=%.2f auditedDrawCount=%d auditedMaxDrawCount=%d invalidDrawCount=%d drawCountBufferMatch=%s auditGeneration=%d auditDimension=%s effectiveDrawMode=%s elementsIndirectCountSupported=%s drawCountBufferCreated=%s drawCountBufferBytes=%d drawCountValue=%d maxDrawCount=%d stage=%s",
                result.success(),
                result.error(),
                result.durationMs(),
                result.auditedDrawCount(),
                result.auditedMaxDrawCount(),
                result.invalidDrawCount(),
                result.drawCountBufferMatch(),
                result.auditGeneration(),
                result.auditDimension(),
                status.effectiveDrawMode(),
                status.elementsIndirectCountSupported(),
                status.drawCountBufferCreated(),
                status.drawCountBufferBytes(),
                status.drawCountValue(),
                status.maxDrawCount(),
                status.stage()
        );
        if (result.success()) {
            source.sendSuccess(() -> Component.literal(message), false);
            return 1;
        }
        source.sendFailure(Component.literal(message));
        return 0;
    }

    private static int directGlMdicDrawCountAuditStatus(CommandSourceStack source) {
        ForgeMdicDebugDrawStats status = ForgeVoxyInstance.INSTANCE.getMdicDebugRenderer().createStatusSnapshot();
        String message = String.format(
                "Voxy MDIC debug draw count audit: lastDrawCountAuditOk=%s auditRuns=%d auditFailures=%d lastDrawCountAuditError=%s lastDrawCountAuditDurationMs=%.2f lastAuditedDrawCount=%d lastAuditedMaxDrawCount=%d lastInvalidDrawCount=%d lastDrawCountBufferMatch=%s lastDrawCountAuditGeneration=%d lastDrawCountAuditDimension=%s drawCountBufferCreated=%s drawCountBufferBytes=%d drawCountValue=%d maxDrawCount=%d drawCountBufferStale=%s commandCount=%d commandRecords=%d currentHeapGeneration=%d currentDimension=%s stage=%s",
                status.lastDrawCountAuditOk(),
                status.drawCountAuditRuns(),
                status.drawCountAuditFailures(),
                status.lastDrawCountAuditError(),
                status.lastDrawCountAuditDurationMs(),
                status.lastAuditedDrawCount(),
                status.lastAuditedMaxDrawCount(),
                status.lastInvalidDrawCount(),
                status.lastDrawCountBufferMatch(),
                status.lastDrawCountAuditGeneration(),
                status.lastDrawCountAuditDimension(),
                status.drawCountBufferCreated(),
                status.drawCountBufferBytes(),
                status.drawCountValue(),
                status.maxDrawCount(),
                status.drawCountBufferStale(),
                status.commandCount(),
                status.commandRecords(),
                status.currentHeapGeneration(),
                status.currentDimension(),
                status.stage()
        );
        source.sendSuccess(() -> Component.literal(message), false);
        return status.lastDrawCountAuditOk() ? 1 : 0;
    }

    private static int directGlMdicDrawCountAuditClear(CommandSourceStack source) {
        ForgeVoxyInstance.INSTANCE.getMdicDebugRenderer().clearDrawCountAuditStats();
        source.sendSuccess(() -> Component.literal("Voxy MDIC debug draw count audit: audit counters cleared. Draw count buffer, elements indirect command buffer, shared index buffer, MDIC command list/buffer, upload-only GL heap, and render state were left unchanged."), false);
        return 1;
    }

    private static int directGlMdicDrawStressOnce(CommandSourceStack source) {
        ForgeMdicDebugDrawStats status = ForgeVoxyInstance.INSTANCE.getMdicDebugRenderer().stressOnce();
        String message = String.format(
                "Voxy MDIC debug draw stress: success=%s stressRuns=%d stressFailures=%d lastStressError=%s lastStressDurationMs=%.2f lastStressPlanOk=%s lastStressBuildOk=%s lastStressAuditOk=%s lastStressDrawEnableOk=%s lastStressDrawStatusOk=%s lastStressDrawDisableOk=%s lastStressDrawClearOk=%s lastStressHeapClearOk=%s lastStressRebuildOk=%s lastStressRedrawOk=%s lastStressLoopOk=%s lastStressMultiDrawOk=%s lastStressIndirectOk=%s lastStressElementsIndirectOk=%s lastStressAutoOk=%s lastStressDerivedIndirectAuditOk=%s lastStressElementsIndirectAuditOk=%s lastStressSharedIndexBufferOk=%s lastStressSourceRegressionOk=%s lastStressBucketDrawOk=%s lastStressBucketIndirectAuditOk=%s lastStressDirectionalFaceMaskOk=%s lastStressFaceMaskAuditOk=%s lastStressVisibilityPlanOk=%s lastStressSelectionAuditOk=%s lastStressFrustumFallbackOk=%s lastStressGlErrorCount=%d lastStressStateRestoreFailures=%d stage=%s effectiveDrawMode=%s formalMdicRenderer=false voxyRenderSystem=false",
                "none".equals(status.lastStressError()),
                status.stressRuns(),
                status.stressFailures(),
                status.lastStressError(),
                status.lastStressDurationMs(),
                status.lastStressPlanOk(),
                status.lastStressBuildOk(),
                status.lastStressAuditOk(),
                status.lastStressDrawEnableOk(),
                status.lastStressDrawStatusOk(),
                status.lastStressDrawDisableOk(),
                status.lastStressDrawClearOk(),
                status.lastStressHeapClearOk(),
                status.lastStressRebuildOk(),
                status.lastStressRedrawOk(),
                status.lastStressLoopOk(),
                status.lastStressMultiDrawOk(),
                status.lastStressIndirectOk(),
                status.lastStressElementsIndirectOk(),
                status.lastStressAutoOk(),
                status.lastStressDerivedIndirectAuditOk(),
                status.lastStressElementsIndirectAuditOk(),
                status.lastStressSharedIndexBufferOk(),
                status.lastStressSourceRegressionOk(),
                status.lastStressBucketDrawOk(),
                status.lastStressBucketIndirectAuditOk(),
                status.lastStressDirectionalFaceMaskOk(),
                status.lastStressFaceMaskAuditOk(),
                status.lastStressVisibilityPlanOk(),
                status.lastStressSelectionAuditOk(),
                status.lastStressFrustumFallbackOk(),
                status.lastStressGlErrorCount(),
                status.lastStressStateRestoreFailures(),
                status.stage(),
                status.effectiveDrawMode()
        );
        message = message + String.format(
                " lastStressElementsIndirectCountOk=%s lastStressDrawCountAuditOk=%s lastStressParameterBufferOk=%s",
                status.lastStressElementsIndirectCountOk(),
                status.lastStressDrawCountAuditOk(),
                status.lastStressParameterBufferOk()
        );
        String displayMessage = message;
        if ("none".equals(status.lastStressError())) {
            source.sendSuccess(() -> Component.literal(displayMessage), false);
            return 1;
        }
        source.sendFailure(Component.literal(displayMessage));
        return 0;
    }

    private static int directGlMdicDrawStressStatus(CommandSourceStack source) {
        ForgeMdicDebugDrawStats status = ForgeVoxyInstance.INSTANCE.getMdicDebugRenderer().createStatusSnapshot();
        String message = String.format(
                "Voxy MDIC debug draw stress: stressRuns=%d stressFailures=%d lastStressError=%s lastStressDurationMs=%.2f lastStressPlanOk=%s lastStressBuildOk=%s lastStressAuditOk=%s lastStressDrawEnableOk=%s lastStressDrawStatusOk=%s lastStressDrawDisableOk=%s lastStressDrawClearOk=%s lastStressHeapClearOk=%s lastStressRebuildOk=%s lastStressRedrawOk=%s lastStressLoopOk=%s lastStressMultiDrawOk=%s lastStressIndirectOk=%s lastStressElementsIndirectOk=%s lastStressAutoOk=%s lastStressDerivedIndirectAuditOk=%s lastStressElementsIndirectAuditOk=%s lastStressSharedIndexBufferOk=%s lastStressSourceRegressionOk=%s lastStressBucketDrawOk=%s lastStressBucketIndirectAuditOk=%s lastStressDirectionalFaceMaskOk=%s lastStressFaceMaskAuditOk=%s lastStressVisibilityPlanOk=%s lastStressSelectionAuditOk=%s lastStressFrustumFallbackOk=%s lastStressGlErrorCount=%d lastStressStateRestoreFailures=%d stage=%s effectiveDrawMode=%s formalMdicRenderer=false voxyRenderSystem=false",
                status.stressRuns(),
                status.stressFailures(),
                status.lastStressError(),
                status.lastStressDurationMs(),
                status.lastStressPlanOk(),
                status.lastStressBuildOk(),
                status.lastStressAuditOk(),
                status.lastStressDrawEnableOk(),
                status.lastStressDrawStatusOk(),
                status.lastStressDrawDisableOk(),
                status.lastStressDrawClearOk(),
                status.lastStressHeapClearOk(),
                status.lastStressRebuildOk(),
                status.lastStressRedrawOk(),
                status.lastStressLoopOk(),
                status.lastStressMultiDrawOk(),
                status.lastStressIndirectOk(),
                status.lastStressElementsIndirectOk(),
                status.lastStressAutoOk(),
                status.lastStressDerivedIndirectAuditOk(),
                status.lastStressElementsIndirectAuditOk(),
                status.lastStressSharedIndexBufferOk(),
                status.lastStressSourceRegressionOk(),
                status.lastStressBucketDrawOk(),
                status.lastStressBucketIndirectAuditOk(),
                status.lastStressDirectionalFaceMaskOk(),
                status.lastStressFaceMaskAuditOk(),
                status.lastStressVisibilityPlanOk(),
                status.lastStressSelectionAuditOk(),
                status.lastStressFrustumFallbackOk(),
                status.lastStressGlErrorCount(),
                status.lastStressStateRestoreFailures(),
                status.stage(),
                status.effectiveDrawMode()
        );
        message = message + String.format(
                " lastStressElementsIndirectCountOk=%s lastStressDrawCountAuditOk=%s lastStressParameterBufferOk=%s",
                status.lastStressElementsIndirectCountOk(),
                status.lastStressDrawCountAuditOk(),
                status.lastStressParameterBufferOk()
        );
        String displayMessage = message;
        source.sendSuccess(() -> Component.literal(displayMessage), false);
        return status.stressFailures() == 0 ? 1 : 0;
    }

    private static int directGlMdicDrawStressClear(CommandSourceStack source) {
        ForgeVoxyInstance.INSTANCE.getMdicDebugRenderer().clearStressStats();
        source.sendSuccess(() -> Component.literal("Voxy MDIC debug draw stress: stress counters cleared. Draw state, command list/buffer, upload-only GL heap, direct renderer, simple renderer, CPU caches, and SectionGeometryManager were left unchanged."), false);
        return 1;
    }

    private static int directGlMdicClear(CommandSourceStack source) {
        ForgeVoxyInstance.INSTANCE.getMdicCommandManager().clear();
        ForgeVoxyInstance.INSTANCE.getMdicDebugRenderer().clear();
        ForgeVoxyInstance.INSTANCE.getTexturedMdicDebugRenderer().markStale("mdic-command-clear");
        source.sendSuccess(() -> Component.literal("Voxy MDIC skeleton: command list, command buffer, audit state, and minimal MDIC debug draw state cleared. Stress counters are retained but not bound to any old buffer. CPU caches, SectionGeometryManager, upload-only GL heap, G5 direct renderer, and simple renderer were left intact."), false);
        return 1;
    }

    private static int modelBridgeCheck(CommandSourceStack source) {
        ForgeModelBridgeAuditResult result = ForgeVoxyInstance.INSTANCE.getModelBridgeReadiness().check();
        ForgeModelBridgeReadinessStats status = result.stats();
        String message = String.format(
                "Voxy model bridge check: success=%s error=%s stage=%s checkRuns=%d durationMs=%.2f placeholderModelIdsPresent=%s stablePlaceholderModelIds=%s canMapModelIdToBlockState=%s placeholderModelStoreReady=%s placeholderModelDataBufferReady=%s placeholderModelColourBufferReady=%s realModelStoreReady=%s realModelFactoryReady=%s modelBakeryBridgeReady=%s textureAtlasReady=%s realModelDataBufferReady=%s realModelColourBufferReady=%s biomeTintReady=%s lightmapReady=%s resourceReloadReady=%s formalShaderInputsReady=%s formalModelBridgeReady=%s placeholderModelIdCount=%d builtSectionUniqueModelIds=%d missingModelRecords=%d currentDimension=%s activeWorldEnginePresent=%s blockStateIdSource=%s sampleModelId=%d sampleBlockStateId=%d sampleBlockState=\"%s\" sampleIsPlaceholder=%s sampleHasRealModelMetadata=%s sampleHasTextureMetadata=%s sampleNote=%s draw=false textureAtlasUpload=false formalRenderer=false",
                result.success(),
                result.error(),
                status.stage(),
                status.checkRuns(),
                result.durationMs(),
                status.placeholderModelIdsPresent(),
                status.stablePlaceholderModelIds(),
                status.canMapModelIdToBlockState(),
                status.placeholderModelStoreReady(),
                status.placeholderModelDataBufferReady(),
                status.placeholderModelColourBufferReady(),
                status.realModelStoreReady(),
                status.realModelFactoryReady(),
                status.modelBakeryBridgeReady(),
                status.textureAtlasReady(),
                status.realModelDataBufferReady(),
                status.realModelColourBufferReady(),
                status.biomeTintReady(),
                status.lightmapReady(),
                status.resourceReloadReady(),
                status.formalShaderInputsReady(),
                status.formalModelBridgeReady(),
                status.placeholderModelIdCount(),
                status.builtSectionUniqueModelIds(),
                status.missingModelRecords(),
                status.currentDimension(),
                status.activeWorldEnginePresent(),
                status.blockStateIdSource(),
                status.sampleModelId(),
                status.sampleBlockStateId(),
                status.sampleBlockState(),
                status.sampleIsPlaceholder(),
                status.sampleHasRealModelMetadata(),
                status.sampleHasTextureMetadata(),
                status.sampleNote()
        );
        message = message + modelStoreFormalLayoutStatusSuffix() + bakedModelBridgeStatusSuffix() + realModelStoreSampleStatusSuffix() + modelSampleSetStatusSuffix() + modelAtlasSkeletonStatusSuffix() + modelAtlasUploadStatusSuffix() + modelAtlasSampleSetUploadStatusSuffix() + formalShaderInputBridgeStatusSuffix() + formalModelStoreStatusSuffix() + formalModelFactoryStatusSuffix() + modelBridgeResourceReloadStatusSuffix();
        String displayMessage = message;
        source.sendSuccess(() -> Component.literal(displayMessage), false);
        return result.success() ? 1 : 0;
    }

    private static int modelBridgeStatus(CommandSourceStack source) {
        ForgeModelBridgeReadinessStats status = ForgeVoxyInstance.INSTANCE.getModelBridgeReadiness().createStatusSnapshot();
        String message = String.format(
                "Voxy model bridge status: stage=%s checkRuns=%d clearRuns=%d lastCheckError=%s lastCheckDurationMs=%.2f placeholderModelIdsPresent=%s stablePlaceholderModelIds=%s canMapModelIdToBlockState=%s placeholderModelStoreReady=%s placeholderModelDataBufferReady=%s placeholderModelColourBufferReady=%s realModelStoreReady=%s realModelFactoryReady=%s modelBakeryBridgeReady=%s textureAtlasReady=%s realModelDataBufferReady=%s realModelColourBufferReady=%s biomeTintReady=%s lightmapReady=%s resourceReloadReady=%s formalShaderInputsReady=%s formalModelBridgeReady=%s placeholderModelIdCount=%d builtSectionUniqueModelIds=%d missingModelRecords=%d currentDimension=%s activeWorldEnginePresent=%s blockStateIdSource=%s sampleModelId=%d sampleBlockStateId=%d sampleBlockState=\"%s\" sampleIsPlaceholder=%s sampleHasRealModelMetadata=%s sampleHasTextureMetadata=%s sampleNote=%s placeholderModelStoreIsNotRealModelStore=%s modelStoreBlocker=missing-real-ModelStore textureAtlasBlocker=no-atlas-upload formalShaderInputBlocker=debug-shader-only draw=false formalRenderer=false",
                status.stage(),
                status.checkRuns(),
                status.clearRuns(),
                status.lastCheckError(),
                status.lastCheckDurationMs(),
                status.placeholderModelIdsPresent(),
                status.stablePlaceholderModelIds(),
                status.canMapModelIdToBlockState(),
                status.placeholderModelStoreReady(),
                status.placeholderModelDataBufferReady(),
                status.placeholderModelColourBufferReady(),
                status.realModelStoreReady(),
                status.realModelFactoryReady(),
                status.modelBakeryBridgeReady(),
                status.textureAtlasReady(),
                status.realModelDataBufferReady(),
                status.realModelColourBufferReady(),
                status.biomeTintReady(),
                status.lightmapReady(),
                status.resourceReloadReady(),
                status.formalShaderInputsReady(),
                status.formalModelBridgeReady(),
                status.placeholderModelIdCount(),
                status.builtSectionUniqueModelIds(),
                status.missingModelRecords(),
                status.currentDimension(),
                status.activeWorldEnginePresent(),
                status.blockStateIdSource(),
                status.sampleModelId(),
                status.sampleBlockStateId(),
                status.sampleBlockState(),
                status.sampleIsPlaceholder(),
                status.sampleHasRealModelMetadata(),
                status.sampleHasTextureMetadata(),
                status.sampleNote(),
                status.placeholderModelStoreReady()
        );
        message = message + modelStoreFormalLayoutStatusSuffix() + bakedModelBridgeStatusSuffix() + realModelStoreSampleStatusSuffix() + modelSampleSetStatusSuffix() + modelAtlasSkeletonStatusSuffix() + modelAtlasUploadStatusSuffix() + modelAtlasSampleSetUploadStatusSuffix() + formalModelStoreStatusSuffix() + formalModelFactoryStatusSuffix() + modelBridgeResourceReloadStatusSuffix();
        String displayMessage = message;
        source.sendSuccess(() -> Component.literal(displayMessage), false);
        return status.checkRuns() > 0 ? 1 : 0;
    }

    private static int modelBridgeClear(CommandSourceStack source) {
        ForgeVoxyInstance.INSTANCE.getModelBridgeReadiness().clear();
        ForgeVoxyInstance.INSTANCE.getBakedModelBridge().clear();
        ForgeVoxyInstance.INSTANCE.getRealModelStoreSample().clear();
        ForgeVoxyInstance.INSTANCE.getModelAtlasSkeleton().clear();
        ForgeVoxyInstance.INSTANCE.getModelAtlasPixelUploader().clear();
        ForgeVoxyInstance.INSTANCE.getFormalShaderInputBridge().clear();
        ForgeVoxyInstance.INSTANCE.getTexturedDebugQuadRenderer().markStale("model-bridge-clear");
        ForgeVoxyInstance.INSTANCE.getTexturedReadbackRenderer().markStale("model-bridge-clear");
        ForgeVoxyInstance.INSTANCE.getTexturedMdicDebugRenderer().markStale("model-bridge-clear");
        source.sendSuccess(() -> Component.literal("Voxy model bridge readiness: cleared no-draw readiness stats, base model sample state, baked model bridge samples, real ModelStore record sample buffers, atlas ownership skeleton state, and sample atlas pixel upload state. Placeholder mapper entries, BuiltSection cache, upload-only GL heap, MDIC debug renderer, simple renderer, and CPU caches were left unchanged."), false);
        return 1;
    }

    private static int modelBridgeDumpSample(CommandSourceStack source) {
        String message = ForgeVoxyInstance.INSTANCE.getModelBridgeReadiness().dumpSample();
        source.sendSuccess(() -> Component.literal(message), false);
        return ForgeVoxyInstance.INSTANCE.getModelBridgeReadiness().createStatusSnapshot().sampleModelId() >= 0 ? 1 : 0;
    }

    private static int modelStoreSkeletonBuild(CommandSourceStack source) {
        ForgeModelStoreStats status = ForgeVoxyInstance.INSTANCE.getModelStoreSkeleton().build();
        String message = String.format(
                "Voxy placeholder ModelStore build: success=%s stage=%s layoutVersion=%s formalLayoutCompatible=%s placeholderModelStoreReady=%s placeholderModelRecords=%d placeholderModelRecordBytes=%d placeholderModelDataBufferCreated=%s placeholderModelDataBufferBytes=%d placeholderModelColourBufferCreated=%s placeholderModelColourBufferBytes=%d generation=%d dimension=%s durationMs=%.2f lastBuildError=%s realModelStoreReady=false textureAtlasReady=false formalShaderInputsReady=false formalModelBridgeReady=false draw=false atlasUpload=false renderer=none",
                status.placeholderModelStoreReady(),
                status.stage(),
                status.layoutVersion(),
                status.formalLayoutCompatible(),
                status.placeholderModelStoreReady(),
                status.placeholderModelRecords(),
                status.placeholderModelRecordBytes(),
                status.placeholderModelDataBufferCreated(),
                status.placeholderModelDataBufferBytes(),
                status.placeholderModelColourBufferCreated(),
                status.placeholderModelColourBufferBytes(),
                status.generation(),
                status.dimension(),
                status.lastBuildDurationMs(),
                status.lastBuildError()
        );
        message = message + realModelStoreSampleStatusSuffix() + modelSampleSetStatusSuffix() + modelAtlasSkeletonStatusSuffix() + modelAtlasUploadStatusSuffix() + modelAtlasSampleSetUploadStatusSuffix();
        String displayMessage = message;
        source.sendSuccess(() -> Component.literal(displayMessage), false);
        return status.placeholderModelStoreReady() ? 1 : 0;
    }

    private static int modelStoreSkeletonStatus(CommandSourceStack source) {
        ForgeModelStoreStats status = ForgeVoxyInstance.INSTANCE.getModelStoreSkeleton().createStatusSnapshot();
        String message = String.format(
                "Voxy placeholder ModelStore status: stage=%s layoutVersion=%s placeholderLayoutVersion=%s formalModelLayoutVersion=%s formalModelRecordBytes=%d formalLayoutKnown=%s fieldMappingReady=%s faceDataMappingReady=%s atlasUvMappingReady=%s materialMappingReady=%s formalLayoutCompatible=%s buildRuns=%d clearRuns=%d lastBuildError=%s lastBuildDurationMs=%.2f placeholderModelStoreReady=%s placeholderModelRecords=%d placeholderModelRecordBytes=%d placeholderModelDataBufferCreated=%s placeholderModelDataBufferReady=%s placeholderModelDataBufferBytes=%d placeholderModelColourBufferCreated=%s placeholderModelColourBufferReady=%s placeholderModelColourBufferBytes=%d realModelStoreReady=%s realModelFactoryReady=%s modelBakeryBridgeReady=%s textureAtlasReady=%s realModelDataBufferReady=%s realModelColourBufferReady=%s biomeTintReady=%s lightmapReady=%s resourceReloadReady=%s formalShaderInputsReady=%s formalModelBridgeReady=%s generation=%d dimension=%s bufferStale=%s lastAuditOk=%s lastModelDataBufferMatch=%s lastModelColourBufferMatch=%s lastInvalidRecords=%d auditRuns=%d auditFailures=%d draw=false atlasUpload=false renderer=none",
                status.stage(),
                status.layoutVersion(),
                status.placeholderLayoutVersion(),
                status.formalModelLayoutVersion(),
                status.formalModelRecordBytes(),
                status.formalLayoutKnown(),
                status.fieldMappingReady(),
                status.faceDataMappingReady(),
                status.atlasUvMappingReady(),
                status.materialMappingReady(),
                status.formalLayoutCompatible(),
                status.buildRuns(),
                status.clearRuns(),
                status.lastBuildError(),
                status.lastBuildDurationMs(),
                status.placeholderModelStoreReady(),
                status.placeholderModelRecords(),
                status.placeholderModelRecordBytes(),
                status.placeholderModelDataBufferCreated(),
                status.placeholderModelDataBufferReady(),
                status.placeholderModelDataBufferBytes(),
                status.placeholderModelColourBufferCreated(),
                status.placeholderModelColourBufferReady(),
                status.placeholderModelColourBufferBytes(),
                status.realModelStoreReady(),
                status.realModelFactoryReady(),
                status.modelBakeryBridgeReady(),
                status.textureAtlasReady(),
                status.realModelDataBufferReady(),
                status.realModelColourBufferReady(),
                status.biomeTintReady(),
                status.lightmapReady(),
                status.resourceReloadReady(),
                status.formalShaderInputsReady(),
                status.formalModelBridgeReady(),
                status.generation(),
                status.dimension(),
                status.bufferStale(),
                status.lastAuditOk(),
                status.lastModelDataBufferMatch(),
                status.lastModelColourBufferMatch(),
                status.lastInvalidRecords(),
                status.auditRuns(),
                status.auditFailures()
        );
        message = message + realModelStoreSampleStatusSuffix() + modelSampleSetStatusSuffix() + modelAtlasSkeletonStatusSuffix() + modelAtlasUploadStatusSuffix() + modelAtlasSampleSetUploadStatusSuffix();
        String displayMessage = message;
        source.sendSuccess(() -> Component.literal(displayMessage), false);
        return status.placeholderModelStoreReady() ? 1 : 0;
    }

    private static int modelStoreSkeletonAudit(CommandSourceStack source) {
        ForgeModelStoreAuditResult result = ForgeVoxyInstance.INSTANCE.getModelStoreSkeleton().audit();
        String message = String.format(
                "Voxy placeholder ModelStore audit: success=%s error=%s durationMs=%.2f auditedRecords=%d auditedBytes=%d invalidRecords=%d modelDataBufferMatch=%s modelColourBufferMatch=%s generation=%d dimension=%s layoutVersion=%s formalLayoutCompatible=%s readbackApi=glGetNamedBufferSubData draw=false atlasUpload=false renderer=none",
                result.success(),
                result.error(),
                result.durationMs(),
                result.auditedRecords(),
                result.auditedBytes(),
                result.invalidRecords(),
                result.modelDataBufferMatch(),
                result.modelColourBufferMatch(),
                result.generation(),
                result.dimension(),
                ForgeModelStoreLayout.LAYOUT_VERSION,
                ForgeModelStoreLayout.FORMAL_LAYOUT_COMPATIBLE
        );
        source.sendSuccess(() -> Component.literal(message), false);
        return result.success() ? 1 : 0;
    }

    private static int modelStoreSkeletonAuditStatus(CommandSourceStack source) {
        ForgeModelStoreStats status = ForgeVoxyInstance.INSTANCE.getModelStoreSkeleton().createStatusSnapshot();
        String message = String.format(
                "Voxy placeholder ModelStore audit: auditRuns=%d auditFailures=%d lastAuditOk=%s lastAuditError=%s lastAuditDurationMs=%.2f lastAuditedRecords=%d lastAuditedBytes=%d lastInvalidRecords=%d lastModelDataBufferMatch=%s lastModelColourBufferMatch=%s placeholderModelRecords=%d placeholderModelDataBufferCreated=%s placeholderModelColourBufferCreated=%s generation=%d dimension=%s layoutVersion=%s formalLayoutCompatible=%s",
                status.auditRuns(),
                status.auditFailures(),
                status.lastAuditOk(),
                status.lastAuditError(),
                status.lastAuditDurationMs(),
                status.lastAuditedRecords(),
                status.lastAuditedBytes(),
                status.lastInvalidRecords(),
                status.lastModelDataBufferMatch(),
                status.lastModelColourBufferMatch(),
                status.placeholderModelRecords(),
                status.placeholderModelDataBufferCreated(),
                status.placeholderModelColourBufferCreated(),
                status.generation(),
                status.dimension(),
                status.layoutVersion(),
                status.formalLayoutCompatible()
        );
        source.sendSuccess(() -> Component.literal(message), false);
        return status.lastAuditOk() ? 1 : 0;
    }

    private static int modelStoreSkeletonDumpSample(CommandSourceStack source) {
        String message = ForgeVoxyInstance.INSTANCE.getModelStoreSkeleton().dumpSample();
        source.sendSuccess(() -> Component.literal(message), false);
        return ForgeVoxyInstance.INSTANCE.getModelStoreSkeleton().createStatusSnapshot().sampleModelId() >= 0 ? 1 : 0;
    }

    private static int modelStoreSkeletonClear(CommandSourceStack source) {
        ForgeVoxyInstance.INSTANCE.getModelStoreSkeleton().clear();
        source.sendSuccess(() -> Component.literal("Voxy placeholder ModelStore skeleton: cleared CPU placeholder records, placeholder modelData/modelColour GL buffers, and audit state. Placeholder mapper entries, real ModelStore state, texture atlas, GL geometry heap, MDIC debug renderer, simple renderer, and CPU caches were left unchanged."), false);
        return 1;
    }

    private static int modelStoreLayoutAudit(CommandSourceStack source) {
        ForgeModelStoreLayoutAuditResult result = ForgeVoxyInstance.INSTANCE.getModelStoreLayoutAuditor().audit();
        String message = String.format(
                "Voxy formal ModelStore layout audit: success=%s error=%s durationMs=%.2f layoutAuditRuns=%d layoutAuditFailures=%d formalModelLayoutVersion=%s formalModelRecordBytes=%d placeholderRecordBytes=%d formalLayoutKnown=%s knownFieldCount=%d unknownFieldCount=%d faceDataLayoutKnown=%s flagsLayoutKnown=%s colourTintLayoutKnown=%s customIdLayoutKnown=%s atlasUvLayoutKnown=%s materialLayoutKnown=%s fieldMappingReady=%s formalLayoutCompatible=%s placeholderGap=\"%s\" fieldSummary=\"%s\" formalModelBridgeReady=false draw=false atlasUpload=false renderer=none",
                result.success(),
                result.error(),
                result.durationMs(),
                ForgeVoxyInstance.INSTANCE.getModelStoreLayoutAuditor().layoutAuditRuns(),
                ForgeVoxyInstance.INSTANCE.getModelStoreLayoutAuditor().layoutAuditFailures(),
                result.formalLayoutVersion(),
                result.formalModelRecordBytes(),
                result.placeholderRecordBytes(),
                result.formalLayoutKnown(),
                result.knownFieldCount(),
                result.unknownFieldCount(),
                result.faceDataLayoutKnown(),
                result.flagsLayoutKnown(),
                result.colourTintLayoutKnown(),
                result.customIdLayoutKnown(),
                result.atlasUvLayoutKnown(),
                result.materialLayoutKnown(),
                result.fieldMappingReady(),
                result.formalLayoutCompatible(),
                result.placeholderGapSummary(),
                ForgeModelStoreFormalLayout.fieldSummary()
        );
        source.sendSuccess(() -> Component.literal(message), false);
        return result.success() ? 1 : 0;
    }

    private static int modelStoreLayoutAuditStatus(CommandSourceStack source) {
        ForgeModelStoreLayoutAuditor auditor = ForgeVoxyInstance.INSTANCE.getModelStoreLayoutAuditor();
        ForgeModelStoreLayoutAuditResult result = auditor.lastResult();
        String message = String.format(
                "Voxy formal ModelStore layout audit: layoutAuditRuns=%d layoutAuditFailures=%d lastAuditOk=%s lastAuditError=%s lastAuditDurationMs=%.2f formalModelLayoutVersion=%s formalModelRecordBytes=%d placeholderRecordBytes=%d formalLayoutKnown=%s knownFieldCount=%d unknownFieldCount=%d faceDataLayoutKnown=%s flagsLayoutKnown=%s colourTintLayoutKnown=%s customIdLayoutKnown=%s atlasUvLayoutKnown=%s materialLayoutKnown=%s fieldMappingReady=%s faceDataMappingReady=%s atlasUvMappingReady=%s materialMappingReady=%s formalLayoutCompatible=%s formalModelBridgeReady=false draw=false atlasUpload=false renderer=none",
                auditor.layoutAuditRuns(),
                auditor.layoutAuditFailures(),
                result.success(),
                result.error(),
                result.durationMs(),
                result.formalLayoutVersion(),
                result.formalModelRecordBytes(),
                result.placeholderRecordBytes(),
                result.formalLayoutKnown(),
                result.knownFieldCount(),
                result.unknownFieldCount(),
                result.faceDataLayoutKnown(),
                result.flagsLayoutKnown(),
                result.colourTintLayoutKnown(),
                result.customIdLayoutKnown(),
                result.atlasUvLayoutKnown(),
                result.materialLayoutKnown(),
                result.fieldMappingReady(),
                ForgeModelStoreFormalLayout.FACE_DATA_MAPPING_READY,
                ForgeModelStoreFormalLayout.ATLAS_UV_MAPPING_READY,
                ForgeModelStoreFormalLayout.MATERIAL_MAPPING_READY,
                result.formalLayoutCompatible()
        );
        source.sendSuccess(() -> Component.literal(message), false);
        return auditor.layoutAuditRuns() > 0L && auditor.layoutAuditFailures() == 0L ? 1 : 0;
    }

    private static int modelStoreLayoutAuditClear(CommandSourceStack source) {
        ForgeVoxyInstance.INSTANCE.getModelStoreLayoutAuditor().clear();
        source.sendSuccess(() -> Component.literal("Voxy formal ModelStore layout audit: cleared layout audit counters. Placeholder ModelStore buffers, model bridge readiness, GL geometry heap, MDIC debug renderer, simple renderer, and CPU caches were left unchanged."), false);
        return 1;
    }

    private static int modelBridgeResourceReloadStatus(CommandSourceStack source) {
        ForgeModelBridgeResourceReloadStats status = ForgeVoxyInstance.INSTANCE.getModelBridgeResourceReloadTracker().createStatusSnapshot();
        source.sendSuccess(() -> Component.literal("Voxy model bridge resource reload: " + formatModelBridgeResourceReloadStatus(status)), false);
        return status.reloadLifecycleSkeletonReady() ? 1 : 0;
    }

    private static int modelBridgeResourceReloadClearStats(CommandSourceStack source) {
        ForgeVoxyInstance.INSTANCE.getModelBridgeResourceReloadTracker().clearStats();
        ForgeModelBridgeResourceReloadStats status = ForgeVoxyInstance.INSTANCE.getModelBridgeResourceReloadTracker().createStatusSnapshot();
        source.sendSuccess(() -> Component.literal("Voxy model bridge resource reload stats clear: " + formatModelBridgeResourceReloadStatus(status) + " Model bridge resources, atlas buffers, GL geometry heap, MDIC command buffers, existing MDIC debug renderer, simple renderer, and CPU SectionGeometryManager were left unchanged."), false);
        return 1;
    }

    private static int modelBridgeSimulateResourceReload(CommandSourceStack source) {
        ForgeModelBridgeResourceReloadStats status = ForgeVoxyInstance.INSTANCE.getModelBridgeResourceReloadTracker().simulateReload("command-simulated-resource-reload");
        source.sendSuccess(() -> Component.literal("Voxy model bridge resource reload simulation: " + formatModelBridgeResourceReloadStatus(status) + " GL geometry heap, MDIC command buffers, MDIC debug renderer, simple renderer, and CPU SectionGeometryManager were left unchanged."), false);
        return status.reloadLifecycleSkeletonReady() ? 1 : 0;
    }

    private static int bakedModelBridgeCheck(CommandSourceStack source) {
        ForgeBakedModelBridgeStats status = ForgeVoxyInstance.INSTANCE.getBakedModelBridge().check();
        String message = "Voxy baked model bridge check: " + formatBakedModelBridgeStatus(status);
        source.sendSuccess(() -> Component.literal(message), false);
        return status.bakedModelBridgeReady() ? 1 : 0;
    }

    private static int bakedModelBridgeStatus(CommandSourceStack source) {
        ForgeBakedModelBridgeStats status = ForgeVoxyInstance.INSTANCE.getBakedModelBridge().createStatusSnapshot();
        source.sendSuccess(() -> Component.literal("Voxy baked model bridge status: " + formatBakedModelBridgeStatus(status)), false);
        return status.checkRuns() > 0L ? 1 : 0;
    }

    private static int bakedModelBridgeAudit(CommandSourceStack source) {
        ForgeBakedModelBridgeAuditResult result = ForgeVoxyInstance.INSTANCE.getBakedModelBridge().audit();
        ForgeBakedModelBridgeStats status = result.stats();
        String message = String.format(
                "Voxy baked model bridge audit: success=%s error=%s durationMs=%.2f sampledModelIds=%d sampledBlockStates=%d sampledBakedModels=%d sampledQuads=%d sampledSprites=%d missingBakedModels=%d missingSprites=%d fluidLikeSamples=%d emptyModelSamples=%d unsupportedSamples=%d renderLayerReadable=%s spriteAtlasReadable=%s spriteUvReadable=%s minecraftBlockAtlasAccessible=%s blockAtlasLocation=%s customAtlasUploadReady=false formalTextureAtlasReady=false formalModelBridgeReady=false draw=false atlasUpload=false renderer=none",
                result.success(),
                result.error(),
                result.durationMs(),
                status.sampledModelIds(),
                status.sampledBlockStates(),
                status.sampledBakedModels(),
                status.sampledQuads(),
                status.sampledSprites(),
                status.missingBakedModels(),
                status.missingSprites(),
                status.fluidLikeSamples(),
                status.emptyModelSamples(),
                status.unsupportedSamples(),
                status.renderLayerReadable(),
                status.spriteAtlasReadable(),
                status.spriteUvReadable(),
                status.minecraftBlockAtlasAccessible(),
                status.blockAtlasLocation()
        );
        source.sendSuccess(() -> Component.literal(message), false);
        return result.success() ? 1 : 0;
    }

    private static int bakedModelBridgeAuditStatus(CommandSourceStack source) {
        ForgeBakedModelBridgeAuditResult result = ForgeVoxyInstance.INSTANCE.getBakedModelBridge().createAuditStatusSnapshot();
        ForgeBakedModelBridgeStats status = result.stats();
        String message = String.format(
                "Voxy baked model bridge audit: auditRuns=%d auditFailures=%d lastAuditOk=%s lastAuditError=%s lastAuditDurationMs=%.2f sampledModelIds=%d sampledBlockStates=%d sampledBakedModels=%d sampledQuads=%d sampledSprites=%d missingBakedModels=%d missingSprites=%d fluidLikeSamples=%d emptyModelSamples=%d unsupportedSamples=%d bakedModelSamplesStale=%s spriteSamplesStale=%s formalModelBridgeReady=false",
                status.auditRuns(),
                status.auditFailures(),
                status.lastAuditOk(),
                status.lastAuditError(),
                status.lastAuditDurationMs(),
                status.sampledModelIds(),
                status.sampledBlockStates(),
                status.sampledBakedModels(),
                status.sampledQuads(),
                status.sampledSprites(),
                status.missingBakedModels(),
                status.missingSprites(),
                status.fluidLikeSamples(),
                status.emptyModelSamples(),
                status.unsupportedSamples(),
                status.bakedModelSamplesStale(),
                status.spriteSamplesStale()
        );
        source.sendSuccess(() -> Component.literal(message), false);
        return result.success() ? 1 : 0;
    }

    private static int bakedModelBridgeDumpSample(CommandSourceStack source) {
        String message = ForgeVoxyInstance.INSTANCE.getBakedModelBridge().dumpSample();
        source.sendSuccess(() -> Component.literal(message), false);
        return ForgeVoxyInstance.INSTANCE.getBakedModelBridge().createStatusSnapshot().sampleModelId() >= 0 ? 1 : 0;
    }

    private static int bakedModelBridgeClear(CommandSourceStack source) {
        ForgeVoxyInstance.INSTANCE.getBakedModelBridge().clear();
        source.sendSuccess(() -> Component.literal("Voxy baked model bridge: cleared no-draw baked model, baked quad, sprite, audit, and reload-stale sample state. Placeholder mapper entries, placeholder ModelStore buffers, GL geometry heap, MDIC debug renderer, simple renderer, and CPU caches were left unchanged."), false);
        return 1;
    }

    private static int modelStoreRealSampleBuild(CommandSourceStack source) {
        ForgeRealModelStoreSampleStats status = ForgeVoxyInstance.INSTANCE.getRealModelStoreSample().build();
        source.sendSuccess(() -> Component.literal("Voxy real ModelStore sample build: " + formatRealModelStoreSampleStatus(status) + modelAtlasSkeletonStatusSuffix() + modelAtlasUploadStatusSuffix()), false);
        return status.realModelRecordSampleReady() ? 1 : 0;
    }

    private static int modelStoreRealSampleStatus(CommandSourceStack source) {
        ForgeRealModelStoreSampleStats status = ForgeVoxyInstance.INSTANCE.getRealModelStoreSample().createStatusSnapshot();
        source.sendSuccess(() -> Component.literal("Voxy real ModelStore sample status: " + formatRealModelStoreSampleStatus(status) + modelAtlasSkeletonStatusSuffix() + modelAtlasUploadStatusSuffix()), false);
        return status.buildRuns() > 0L || status.realModelRecordSampleStale() ? 1 : 0;
    }

    private static int modelStoreRealSampleAudit(CommandSourceStack source) {
        ForgeRealModelStoreSampleAuditResult result = ForgeVoxyInstance.INSTANCE.getRealModelStoreSample().audit();
        ForgeRealModelStoreSampleStats status = ForgeVoxyInstance.INSTANCE.getRealModelStoreSample().createStatusSnapshot();
        String message = String.format(
                "Voxy real ModelStore sample audit: success=%s error=%s durationMs=%.2f auditedRecords=%d auditedBytes=%d invalidRecords=%d lastModelDataBufferMatch=%s lastModelColourBufferMatch=%s layoutVersion=%s formalLayoutCompatible=%s sourceModelId=%d sourceBlockStateId=%d sourceBlockState=\"%s\" sourceSprite=%s faceDataEncoded=%s faceDataFormalCompatible=%s realTextureAtlasUploadReady=false formalTexturedShaderReady=false formalModelBridgeReady=false draw=false atlasUpload=false renderer=none",
                result.success(),
                result.error(),
                result.durationMs(),
                result.auditedRecords(),
                result.auditedBytes(),
                result.invalidRecords(),
                result.modelDataBufferMatch(),
                result.modelColourBufferMatch(),
                status.realModelRecordLayoutVersion(),
                status.formalLayoutCompatible(),
                status.sourceModelId(),
                status.sourceBlockStateId(),
                status.sourceBlockState(),
                status.sourceSprite(),
                status.faceDataEncoded(),
                status.faceDataFormalCompatible()
        );
        source.sendSuccess(() -> Component.literal(message), false);
        return result.success() ? 1 : 0;
    }

    private static int modelStoreRealSampleAuditStatus(CommandSourceStack source) {
        ForgeRealModelStoreSampleStats status = ForgeVoxyInstance.INSTANCE.getRealModelStoreSample().createStatusSnapshot();
        String message = String.format(
                "Voxy real ModelStore sample audit: auditRuns=%d auditFailures=%d lastAuditOk=%s lastAuditError=%s lastAuditDurationMs=%.2f lastAuditedRecords=%d lastAuditedBytes=%d lastInvalidRecords=%d lastModelDataBufferMatch=%s lastModelColourBufferMatch=%s realModelRecordSampleReady=%s realModelRecordSampleBufferReady=%s generation=%d dimension=%s layoutVersion=%s formalLayoutCompatible=%s formalModelBridgeReady=false",
                status.auditRuns(),
                status.auditFailures(),
                status.lastAuditOk(),
                status.lastAuditError(),
                status.lastAuditDurationMs(),
                status.lastAuditedRecords(),
                status.lastAuditedBytes(),
                status.lastInvalidRecords(),
                status.lastModelDataBufferMatch(),
                status.lastModelColourBufferMatch(),
                status.realModelRecordSampleReady(),
                status.realModelRecordSampleBufferReady(),
                status.generation(),
                status.dimension(),
                status.realModelRecordLayoutVersion(),
                status.formalLayoutCompatible()
        );
        source.sendSuccess(() -> Component.literal(message), false);
        return status.lastAuditOk() ? 1 : 0;
    }

    private static int modelStoreRealSampleDump(CommandSourceStack source) {
        String message = ForgeVoxyInstance.INSTANCE.getRealModelStoreSample().dumpSample();
        source.sendSuccess(() -> Component.literal(message), false);
        return ForgeVoxyInstance.INSTANCE.getRealModelStoreSample().createStatusSnapshot().realModelRecordSampleReady() ? 1 : 0;
    }

    private static int modelStoreRealSampleClear(CommandSourceStack source) {
        ForgeVoxyInstance.INSTANCE.getRealModelStoreSample().clear();
        ForgeVoxyInstance.INSTANCE.getModelSampleSet().markStale("real-model-sample-clear");
        ForgeVoxyInstance.INSTANCE.getModelAtlasSampleSetUploader().markStale("real-model-sample-clear");
        ForgeVoxyInstance.INSTANCE.getFormalShaderInputBridge().markStale("real-model-sample-clear");
        ForgeVoxyInstance.INSTANCE.getModelAtlasSkeleton().markStale("real-model-sample-clear");
        ForgeVoxyInstance.INSTANCE.getModelAtlasPixelUploader().markStale("real-model-sample-clear");
        ForgeVoxyInstance.INSTANCE.getTexturedReadbackRenderer().markStale("real-model-sample-clear");
        ForgeVoxyInstance.INSTANCE.getTexturedMdicDebugRenderer().markStale("real-model-sample-clear");
        source.sendSuccess(() -> Component.literal("Voxy real ModelStore sample: cleared CPU real-ish record sample, no-draw modelData/modelColour sample buffers, and audit state. The no-draw atlas skeleton and sample atlas pixel upload were marked stale because their sample coordinates derive from the real sample. Placeholder ModelStore buffers, baked model bridge samples, GL geometry heap, MDIC debug renderer, simple renderer, and CPU caches were left unchanged."), false);
        return 1;
    }

    private static int modelSampleSetBuild(CommandSourceStack source) {
        ForgeModelSampleSetStats status = ForgeVoxyInstance.INSTANCE.getModelSampleSet().build();
        ForgeVoxyInstance.INSTANCE.getModelAtlasSampleSetUploader().markStale("model-sample-set-rebuilt");
        ForgeVoxyInstance.INSTANCE.getFormalShaderInputBridge().markStale("model-sample-set-rebuilt");
        ForgeVoxyInstance.INSTANCE.getTexturedMdicDebugRenderer().markStale("model-sample-set-rebuilt");
        source.sendSuccess(() -> Component.literal("Voxy model sample set build: " + formatModelSampleSetStatus(status) + modelAtlasSampleSetUploadStatusSuffix()), false);
        return status.sampleSetReady() ? 1 : 0;
    }

    private static int modelSampleSetStatus(CommandSourceStack source) {
        ForgeModelSampleSetStats status = ForgeVoxyInstance.INSTANCE.getModelSampleSet().createStatusSnapshot();
        source.sendSuccess(() -> Component.literal("Voxy model sample set status: " + formatModelSampleSetStatus(status) + modelAtlasSampleSetUploadStatusSuffix()), false);
        return status.sampleSetReady() || status.sampleSetStale() ? 1 : 0;
    }

    private static int modelSampleSetAudit(CommandSourceStack source) {
        ForgeModelSampleSetAuditResult result = ForgeVoxyInstance.INSTANCE.getModelSampleSet().audit();
        ForgeModelSampleSetStats status = ForgeVoxyInstance.INSTANCE.getModelSampleSet().createStatusSnapshot();
        String message = String.format(
                "Voxy model sample set audit: success=%s error=%s durationMs=%.2f auditRuns=%d auditFailures=%d auditedRecords=%d auditedBytes=%d invalidRecords=%d lastModelDataBufferMatch=%s lastModelColourBufferMatch=%s sampleSetReady=%s acceptedSamples=%d sampleModelIds=%s sampleBlockStates=%s formalLayoutCompatible=false formalModelBridgeReady=false draw=false atlasUpload=false renderer=none",
                result.success(),
                result.error(),
                result.durationMs(),
                status.auditRuns(),
                status.auditFailures(),
                result.auditedRecords(),
                result.auditedBytes(),
                result.invalidRecords(),
                result.modelDataBufferMatch(),
                result.modelColourBufferMatch(),
                status.sampleSetReady(),
                status.acceptedSamples(),
                status.sampleModelIds(),
                status.sampleBlockStates()
        );
        source.sendSuccess(() -> Component.literal(message), false);
        return result.success() ? 1 : 0;
    }

    private static int modelSampleSetAuditStatus(CommandSourceStack source) {
        ForgeModelSampleSetStats status = ForgeVoxyInstance.INSTANCE.getModelSampleSet().createStatusSnapshot();
        String message = String.format(
                "Voxy model sample set audit: auditRuns=%d auditFailures=%d lastAuditOk=%s lastAuditError=%s lastAuditDurationMs=%.2f lastAuditedRecords=%d lastAuditedBytes=%d lastInvalidRecords=%d lastModelDataBufferMatch=%s lastModelColourBufferMatch=%s sampleSetReady=%s acceptedSamples=%d sampleSetStale=%s formalLayoutCompatible=false formalModelBridgeReady=false",
                status.auditRuns(),
                status.auditFailures(),
                status.lastAuditOk(),
                status.lastAuditError(),
                status.lastAuditDurationMs(),
                status.lastAuditedRecords(),
                status.lastAuditedBytes(),
                status.lastInvalidRecords(),
                status.lastModelDataBufferMatch(),
                status.lastModelColourBufferMatch(),
                status.sampleSetReady(),
                status.acceptedSamples(),
                status.sampleSetStale()
        );
        source.sendSuccess(() -> Component.literal(message), false);
        return status.lastAuditOk() ? 1 : 0;
    }

    private static int modelSampleSetDump(CommandSourceStack source) {
        String message = ForgeVoxyInstance.INSTANCE.getModelSampleSet().dump();
        source.sendSuccess(() -> Component.literal(message), false);
        return ForgeVoxyInstance.INSTANCE.getModelSampleSet().createStatusSnapshot().sampleSetReady() ? 1 : 0;
    }

    private static int modelSampleSetClear(CommandSourceStack source) {
        ForgeVoxyInstance.INSTANCE.getModelSampleSet().clear();
        ForgeVoxyInstance.INSTANCE.getModelAtlasSampleSetUploader().markStale("model-sample-set-clear");
        ForgeVoxyInstance.INSTANCE.getFormalShaderInputBridge().markStale("model-sample-set-clear");
        ForgeVoxyInstance.INSTANCE.getTexturedMdicDebugRenderer().markStale("model-sample-set-clear");
        source.sendSuccess(() -> Component.literal("Voxy model sample set: cleared multi-block sample records, no-draw sample-set modelData/modelColour buffer, and audit state. Sample-set atlas upload and textured MDIC debug were marked stale. GL geometry heap, existing MDIC command/debug renderer, simple renderer, and CPU caches were left unchanged."), false);
        return 1;
    }

    private static int modelAtlasSkeletonBuild(CommandSourceStack source) {
        ForgeModelAtlasStats status = ForgeVoxyInstance.INSTANCE.getModelAtlasSkeleton().build();
        ForgeVoxyInstance.INSTANCE.getModelAtlasPixelUploader().markStale("atlas-skeleton-rebuilt");
        ForgeVoxyInstance.INSTANCE.getModelAtlasSampleSetUploader().markStale("atlas-skeleton-rebuilt");
        ForgeVoxyInstance.INSTANCE.getFormalShaderInputBridge().markStale("atlas-skeleton-rebuilt");
        ForgeVoxyInstance.INSTANCE.getTexturedReadbackRenderer().markStale("atlas-skeleton-rebuilt");
        ForgeVoxyInstance.INSTANCE.getTexturedMdicDebugRenderer().markStale("atlas-skeleton-rebuilt");
        source.sendSuccess(() -> Component.literal("Voxy model atlas skeleton build: " + formatModelAtlasSkeletonStatus(status) + modelAtlasUploadStatusSuffix()), false);
        return status.atlasSkeletonReady() ? 1 : 0;
    }

    private static int modelAtlasSkeletonStatus(CommandSourceStack source) {
        ForgeModelAtlasStats status = ForgeVoxyInstance.INSTANCE.getModelAtlasSkeleton().createStatusSnapshot();
        source.sendSuccess(() -> Component.literal("Voxy model atlas skeleton status: " + formatModelAtlasSkeletonStatus(status) + modelAtlasUploadStatusSuffix()), false);
        return status.atlasSkeletonReady() || status.atlasSkeletonStale() ? 1 : 0;
    }

    private static int modelAtlasSkeletonAudit(CommandSourceStack source) {
        ForgeModelAtlasAuditResult result = ForgeVoxyInstance.INSTANCE.getModelAtlasSkeleton().audit();
        ForgeModelAtlasStats status = ForgeVoxyInstance.INSTANCE.getModelAtlasSkeleton().createStatusSnapshot();
        String message = String.format(
                "Voxy model atlas skeleton audit: success=%s error=%s durationMs=%.2f auditRuns=%d auditFailures=%d invalidLayout=%d invalidModelCoordinate=%d invalidFaceTileCoordinate=%d sampleModelId=%d voxyAtlasBaseX=%d voxyAtlasBaseY=%d atlasPixelsUploaded=false realTextureDataReady=false customAtlasUploadReady=false formalTextureAtlasReady=false formalModelBridgeReady=false draw=false",
                result.success(),
                result.error(),
                result.durationMs(),
                status.auditRuns(),
                status.auditFailures(),
                result.invalidLayout(),
                result.invalidModelCoordinate(),
                result.invalidFaceTileCoordinate(),
                status.sampleModelId(),
                status.voxyAtlasBaseX(),
                status.voxyAtlasBaseY()
        );
        source.sendSuccess(() -> Component.literal(message), false);
        return result.success() ? 1 : 0;
    }

    private static int modelAtlasSkeletonAuditStatus(CommandSourceStack source) {
        ForgeModelAtlasStats status = ForgeVoxyInstance.INSTANCE.getModelAtlasSkeleton().createStatusSnapshot();
        String message = String.format(
                "Voxy model atlas skeleton audit: auditRuns=%d auditFailures=%d lastAuditOk=%s lastAuditError=%s lastAuditDurationMs=%.2f invalidLayout=%d invalidModelCoordinate=%d invalidFaceTileCoordinate=%d atlasSkeletonReady=%s atlasLayoutReady=%s atlasOwnershipReady=%s atlasPixelsUploaded=false formalTextureAtlasReady=false formalModelBridgeReady=false",
                status.auditRuns(),
                status.auditFailures(),
                status.lastAuditOk(),
                status.lastAuditError(),
                status.lastAuditDurationMs(),
                status.invalidLayout(),
                status.invalidModelCoordinate(),
                status.invalidFaceTileCoordinate(),
                status.atlasSkeletonReady(),
                status.atlasLayoutReady(),
                status.atlasOwnershipReady()
        );
        source.sendSuccess(() -> Component.literal(message), false);
        return status.lastAuditOk() ? 1 : 0;
    }

    private static int modelAtlasSkeletonDumpSample(CommandSourceStack source) {
        String message = ForgeVoxyInstance.INSTANCE.getModelAtlasSkeleton().dumpSample();
        source.sendSuccess(() -> Component.literal(message), false);
        return ForgeVoxyInstance.INSTANCE.getModelAtlasSkeleton().createStatusSnapshot().sampleModelId() >= 0 ? 1 : 0;
    }

    private static int modelAtlasSkeletonClear(CommandSourceStack source) {
        ForgeVoxyInstance.INSTANCE.getModelAtlasSkeleton().clear();
        ForgeVoxyInstance.INSTANCE.getModelAtlasPixelUploader().markStale("atlas-skeleton-clear");
        ForgeVoxyInstance.INSTANCE.getModelAtlasSampleSetUploader().markStale("atlas-skeleton-clear");
        ForgeVoxyInstance.INSTANCE.getFormalShaderInputBridge().markStale("atlas-skeleton-clear");
        ForgeVoxyInstance.INSTANCE.getTexturedDebugQuadRenderer().markStale("atlas-skeleton-clear");
        ForgeVoxyInstance.INSTANCE.getTexturedReadbackRenderer().markStale("atlas-skeleton-clear");
        ForgeVoxyInstance.INSTANCE.getTexturedMdicDebugRenderer().markStale("atlas-skeleton-clear");
        source.sendSuccess(() -> Component.literal("Voxy model atlas skeleton: cleared no-draw atlas layout ownership, sample coordinate state, and audit state. Any sample atlas pixel upload and tiny textured debug quad were marked stale. GL geometry heap, MDIC debug renderer, simple renderer, and CPU caches were left unchanged."), false);
        return 1;
    }

    private static int modelAtlasUploadSample(CommandSourceStack source) {
        ForgeModelAtlasUploadStats status = ForgeVoxyInstance.INSTANCE.getModelAtlasPixelUploader().uploadSample();
        ForgeVoxyInstance.INSTANCE.getTexturedDebugQuadRenderer().markStale("atlas-upload-rebuilt");
        ForgeVoxyInstance.INSTANCE.getTexturedReadbackRenderer().markStale("atlas-upload-rebuilt");
        ForgeVoxyInstance.INSTANCE.getTexturedMdicDebugRenderer().markStale("atlas-upload-rebuilt");
        source.sendSuccess(() -> Component.literal("Voxy model atlas upload sample: " + formatModelAtlasUploadStatus(status)), false);
        return status.lastUploadOk() ? 1 : 0;
    }

    private static int modelAtlasUploadStatus(CommandSourceStack source) {
        ForgeModelAtlasUploadStats status = ForgeVoxyInstance.INSTANCE.getModelAtlasPixelUploader().createStatusSnapshot();
        source.sendSuccess(() -> Component.literal("Voxy model atlas upload status: " + formatModelAtlasUploadStatus(status)), false);
        return status.sampleAtlasPixelsUploaded() || status.atlasPixelsStale() ? 1 : 0;
    }

    private static int modelAtlasUploadAudit(CommandSourceStack source) {
        ForgeModelAtlasUploadAuditResult result = ForgeVoxyInstance.INSTANCE.getModelAtlasPixelUploader().audit();
        ForgeModelAtlasUploadStats status = ForgeVoxyInstance.INSTANCE.getModelAtlasPixelUploader().createStatusSnapshot();
        String message = String.format(
                "Voxy model atlas upload audit: success=%s error=%s durationMs=%.2f uploadRuns=%d uploadFailures=%d auditRuns=%d auditFailures=%d lastUploadedFaces=%d lastUploadedPixels=%d lastMissingFaces=%d lastPixelMismatches=%d lastAtlasReadbackOk=%s atlasTextureObjectCreated=%s fullAtlasTextureCreated=%s debugSmallAtlasFallback=%s atlasWidth=%d atlasHeight=%d actualTextureWidth=%d actualTextureHeight=%d atlasFormat=%s sampleModelId=%d sourceBlockState=\"%s\" sourceSprite=%s sourceSpriteAtlas=%s face0Checksum=%s face1Checksum=%s face2Checksum=%s face3Checksum=%s face4Checksum=%s face5Checksum=%s sampleAtlasPixelsUploaded=%s realAtlasPixelUploadReady=%s formalTextureAtlasReady=false formalTexturedShaderReady=false formalModelBridgeReady=false draw=false renderer=none",
                result.success(),
                result.error(),
                result.durationMs(),
                status.uploadRuns(),
                status.uploadFailures(),
                status.auditRuns(),
                status.auditFailures(),
                result.auditedFaces(),
                result.auditedPixels(),
                result.missingFaces(),
                result.pixelMismatches(),
                result.atlasReadbackOk(),
                status.atlasTextureObjectCreated(),
                status.fullAtlasTextureCreated(),
                status.debugSmallAtlasFallback(),
                status.atlasWidth(),
                status.atlasHeight(),
                status.actualTextureWidth(),
                status.actualTextureHeight(),
                status.atlasFormat(),
                status.sampleModelId(),
                status.sourceBlockState(),
                status.sourceSprite(),
                status.sourceSpriteAtlas(),
                status.face0Checksum(),
                status.face1Checksum(),
                status.face2Checksum(),
                status.face3Checksum(),
                status.face4Checksum(),
                status.face5Checksum(),
                status.sampleAtlasPixelsUploaded(),
                status.realAtlasPixelUploadReady()
        );
        source.sendSuccess(() -> Component.literal(message), false);
        return result.success() ? 1 : 0;
    }

    private static int modelAtlasUploadAuditStatus(CommandSourceStack source) {
        ForgeModelAtlasUploadStats status = ForgeVoxyInstance.INSTANCE.getModelAtlasPixelUploader().createStatusSnapshot();
        String message = String.format(
                "Voxy model atlas upload audit: uploadRuns=%d uploadFailures=%d auditRuns=%d auditFailures=%d lastUploadOk=%s lastUploadError=%s lastAuditOk=%s lastAuditError=%s lastAuditDurationMs=%.2f lastUploadedFaces=%d lastUploadedPixels=%d lastMissingFaces=%d lastPixelMismatches=%d lastAtlasReadbackOk=%s face0Checksum=%s face1Checksum=%s face2Checksum=%s face3Checksum=%s face4Checksum=%s face5Checksum=%s atlasPixelsUploaded=%s sampleAtlasPixelsUploaded=%s realAtlasPixelUploadReady=%s formalTextureAtlasReady=false formalTexturedShaderReady=false formalModelBridgeReady=false",
                status.uploadRuns(),
                status.uploadFailures(),
                status.auditRuns(),
                status.auditFailures(),
                status.lastUploadOk(),
                status.lastUploadError(),
                status.lastAuditOk(),
                status.lastAuditError(),
                status.lastAuditDurationMs(),
                status.lastUploadedFaces(),
                status.lastUploadedPixels(),
                status.lastMissingFaces(),
                status.lastPixelMismatches(),
                status.lastAtlasReadbackOk(),
                status.face0Checksum(),
                status.face1Checksum(),
                status.face2Checksum(),
                status.face3Checksum(),
                status.face4Checksum(),
                status.face5Checksum(),
                status.atlasPixelsUploaded(),
                status.sampleAtlasPixelsUploaded(),
                status.realAtlasPixelUploadReady()
        );
        source.sendSuccess(() -> Component.literal(message), false);
        return status.lastAuditOk() ? 1 : 0;
    }

    private static int modelAtlasUploadDumpSample(CommandSourceStack source) {
        String message = ForgeVoxyInstance.INSTANCE.getModelAtlasPixelUploader().dumpSample();
        source.sendSuccess(() -> Component.literal(message), false);
        return ForgeVoxyInstance.INSTANCE.getModelAtlasPixelUploader().createStatusSnapshot().sampleAtlasPixelsUploaded() ? 1 : 0;
    }

    private static int modelAtlasUploadClear(CommandSourceStack source) {
        ForgeVoxyInstance.INSTANCE.getModelAtlasPixelUploader().clear();
        ForgeVoxyInstance.INSTANCE.getModelAtlasSampleSetUploader().clear();
        ForgeVoxyInstance.INSTANCE.getFormalShaderInputBridge().markStale("atlas-upload-clear");
        ForgeVoxyInstance.INSTANCE.getTexturedDebugQuadRenderer().markStale("atlas-upload-clear");
        ForgeVoxyInstance.INSTANCE.getTexturedReadbackRenderer().markStale("atlas-upload-clear");
        ForgeVoxyInstance.INSTANCE.getTexturedMdicDebugRenderer().markStale("atlas-upload-clear");
        source.sendSuccess(() -> Component.literal("Voxy model atlas upload sample: cleared Forge-owned single-sample and sample-set atlas textures, uploaded pixel CPU copies, upload audit state, and stale flags. Tiny/textured debug renderers were marked stale. Formal renderer, MDIC debug renderer, GL geometry heap, simple renderer, and CPU caches were left unchanged."), false);
        return 1;
    }

    private static int modelAtlasUploadSampleSet(CommandSourceStack source) {
        ForgeModelAtlasSampleSetUploadStats status = ForgeVoxyInstance.INSTANCE.getModelAtlasSampleSetUploader().uploadSampleSet();
        ForgeVoxyInstance.INSTANCE.getFormalShaderInputBridge().markStale("atlas-sample-set-upload-rebuilt");
        ForgeVoxyInstance.INSTANCE.getTexturedMdicDebugRenderer().markStale("atlas-sample-set-upload-rebuilt");
        source.sendSuccess(() -> Component.literal("Voxy model atlas upload sample set: " + formatModelAtlasSampleSetUploadStatus(status)), false);
        return status.lastUploadOk() ? 1 : 0;
    }

    private static int modelAtlasUploadSampleSetStatus(CommandSourceStack source) {
        ForgeModelAtlasSampleSetUploadStats status = ForgeVoxyInstance.INSTANCE.getModelAtlasSampleSetUploader().createStatusSnapshot();
        source.sendSuccess(() -> Component.literal("Voxy model atlas upload sample set status: " + formatModelAtlasSampleSetUploadStatus(status)), false);
        return status.sampleSetAtlasUploadReady() || status.atlasSampleSetStale() ? 1 : 0;
    }

    private static int modelAtlasUploadSampleSetAudit(CommandSourceStack source) {
        ForgeModelAtlasSampleSetUploadAuditResult result = ForgeVoxyInstance.INSTANCE.getModelAtlasSampleSetUploader().audit();
        ForgeModelAtlasSampleSetUploadStats status = ForgeVoxyInstance.INSTANCE.getModelAtlasSampleSetUploader().createStatusSnapshot();
        String message = String.format(
                "Voxy model atlas upload sample set audit: success=%s error=%s durationMs=%.2f uploadRuns=%d uploadFailures=%d auditRuns=%d auditFailures=%d uploadedModelCount=%d uploadedFaceCount=%d uploadedPixels=%d missingFaces=%d pixelMismatches=%d lastAtlasReadbackOk=%s atlasTextureObjectCreated=%s fullAtlasTextureCreated=%s debugSmallAtlasFallback=%s sampleModelIds=%s sampleBlockStates=%s sampleSprites=%s sampleSetAtlasUploadReady=%s formalTextureAtlasReady=false formalTexturedShaderReady=false formalModelBridgeReady=false draw=false renderer=none",
                result.success(),
                result.error(),
                result.durationMs(),
                status.uploadRuns(),
                status.uploadFailures(),
                status.auditRuns(),
                status.auditFailures(),
                status.uploadedModelCount(),
                result.auditedFaces(),
                result.auditedPixels(),
                result.missingFaces(),
                result.pixelMismatches(),
                result.atlasReadbackOk(),
                status.atlasTextureObjectCreated(),
                status.fullAtlasTextureCreated(),
                status.debugSmallAtlasFallback(),
                status.sampleModelIds(),
                status.sampleBlockStates(),
                status.sampleSprites(),
                status.sampleSetAtlasUploadReady()
        );
        source.sendSuccess(() -> Component.literal(message), false);
        return result.success() ? 1 : 0;
    }

    private static int modelAtlasUploadSampleSetDump(CommandSourceStack source) {
        String message = ForgeVoxyInstance.INSTANCE.getModelAtlasSampleSetUploader().dumpSampleSet();
        source.sendSuccess(() -> Component.literal(message), false);
        return ForgeVoxyInstance.INSTANCE.getModelAtlasSampleSetUploader().createStatusSnapshot().sampleSetAtlasUploadReady() ? 1 : 0;
    }

    private static int formalShaderInputBridgeBuild(CommandSourceStack source) {
        ForgeFormalShaderInputStats status = ForgeVoxyInstance.INSTANCE.getFormalShaderInputBridge().build();
        ForgeVoxyInstance.INSTANCE.getTexturedMdicDebugRenderer().markStale("formal-shader-input-bridge-rebuilt");
        source.sendSuccess(() -> Component.literal("Voxy formal shader input bridge build: " + formatFormalShaderInputBridgeStatus(status)), false);
        return status.formalShaderInputBridgeReady() ? 1 : 0;
    }

    private static int formalShaderInputBridgeStatus(CommandSourceStack source) {
        ForgeFormalShaderInputStats status = ForgeVoxyInstance.INSTANCE.getFormalShaderInputBridge().createStatusSnapshot();
        source.sendSuccess(() -> Component.literal("Voxy formal shader input bridge status: " + formatFormalShaderInputBridgeStatus(status)), false);
        return status.formalShaderInputBridgeReady() || status.formalShaderInputBridgeStale() ? 1 : 0;
    }

    private static int formalShaderInputBridgeAudit(CommandSourceStack source) {
        ForgeFormalShaderInputAuditResult result = ForgeVoxyInstance.INSTANCE.getFormalShaderInputBridge().audit();
        ForgeFormalShaderInputStats status = ForgeVoxyInstance.INSTANCE.getFormalShaderInputBridge().createStatusSnapshot();
        String message = String.format(
                "Voxy formal shader input bridge audit: success=%s error=%s durationMs=%.2f auditRuns=%d auditFailures=%d modelDataBufferMatch=%s modelColourBufferMatch=%s atlasUploadStillValid=%s sampleModelCount=%d invalidModelRecords=%d invalidColourRecords=%d missingAtlasTiles=%d formalShaderInputBridgeReady=%s usesSampleSetModelData=%s usesRealModelStore=%s formalTexturedShaderReady=false formalModelBridgeReady=false renderer=none formalRenderer=false",
                result.success(),
                result.error(),
                result.durationMs(),
                status.auditRuns(),
                status.auditFailures(),
                result.modelDataBufferMatch(),
                result.modelColourBufferMatch(),
                result.atlasUploadStillValid(),
                result.sampleModelCount(),
                result.invalidModelRecords(),
                result.invalidColourRecords(),
                result.missingAtlasTiles(),
                status.formalShaderInputBridgeReady(),
                status.usesSampleSetModelData(),
                status.usesRealModelStore()
        );
        source.sendSuccess(() -> Component.literal(message), false);
        return result.success() ? 1 : 0;
    }

    private static int formalShaderInputBridgeAuditStatus(CommandSourceStack source) {
        ForgeFormalShaderInputStats status = ForgeVoxyInstance.INSTANCE.getFormalShaderInputBridge().createStatusSnapshot();
        String message = String.format(
                "Voxy formal shader input bridge audit: auditRuns=%d auditFailures=%d lastAuditOk=%s lastAuditError=%s lastAuditDurationMs=%.2f modelDataBufferMatch=%s modelColourBufferMatch=%s atlasUploadStillValid=%s sampleModelCount=%d invalidModelRecords=%d invalidColourRecords=%d missingAtlasTiles=%d formalShaderInputBridgeReady=%s formalShaderInputBridgeStale=%s formalTexturedShaderReady=false formalModelBridgeReady=false",
                status.auditRuns(),
                status.auditFailures(),
                status.lastAuditOk(),
                status.lastAuditError(),
                status.lastAuditDurationMs(),
                status.modelDataBufferMatch(),
                status.modelColourBufferMatch(),
                status.atlasUploadStillValid(),
                status.sampleModelCount(),
                status.invalidModelRecords(),
                status.invalidColourRecords(),
                status.missingAtlasTiles(),
                status.formalShaderInputBridgeReady(),
                status.formalShaderInputBridgeStale()
        );
        source.sendSuccess(() -> Component.literal(message), false);
        return status.lastAuditOk() ? 1 : 0;
    }

    private static int formalShaderInputBridgeClear(CommandSourceStack source) {
        ForgeVoxyInstance.INSTANCE.getFormalShaderInputBridge().clear();
        ForgeVoxyInstance.INSTANCE.getTexturedMdicDebugRenderer().markStale("formal-shader-input-bridge-clear");
        ForgeFormalShaderInputStats status = ForgeVoxyInstance.INSTANCE.getFormalShaderInputBridge().createStatusSnapshot();
        source.sendSuccess(() -> Component.literal("Voxy formal shader input bridge clear: " + formatFormalShaderInputBridgeStatus(status) + " Sample-set records, atlas upload, GL geometry heap, existing MDIC debug renderer, simple renderer, and CPU caches were left unchanged."), false);
        return 1;
    }

    private static int formalModelStoreBuild(CommandSourceStack source) {
        ForgeFormalModelStoreStats status = ForgeVoxyInstance.INSTANCE.getFormalModelStore().build();
        source.sendSuccess(() -> Component.literal("Voxy formal ModelStore build: " + formatFormalModelStoreStatus(status) + " No bake, shader bind, formal draw, MDICSectionRenderer, or VoxyRenderSystem call was performed."), false);
        return status.formalModelStoreSkeletonReady() ? 1 : 0;
    }

    private static int formalModelStoreStatus(CommandSourceStack source) {
        ForgeFormalModelStoreStats status = ForgeVoxyInstance.INSTANCE.getFormalModelStore().createStatusSnapshot();
        source.sendSuccess(() -> Component.literal("Voxy formal ModelStore status: " + formatFormalModelStoreStatus(status)), false);
        return status.formalModelStoreSkeletonReady() || status.stale() ? 1 : 0;
    }

    private static int formalModelStoreAudit(CommandSourceStack source) {
        ForgeFormalModelStoreAuditResult audit = ForgeVoxyInstance.INSTANCE.getFormalModelStore().audit();
        ForgeFormalModelStoreStats status = ForgeVoxyInstance.INSTANCE.getFormalModelStore().createStatusSnapshot();
        source.sendSuccess(() -> Component.literal("Voxy formal ModelStore audit: " + formatFormalModelStoreAudit(audit) + " " + formatFormalModelStoreStatus(status)), false);
        return audit.success() ? 1 : 0;
    }

    private static int formalModelStoreAuditStatus(CommandSourceStack source) {
        ForgeFormalModelStoreAuditResult audit = ForgeVoxyInstance.INSTANCE.getFormalModelStore().createAuditStatusSnapshot();
        source.sendSuccess(() -> Component.literal("Voxy formal ModelStore audit status: " + formatFormalModelStoreAudit(audit)), false);
        return audit.success() ? 1 : 0;
    }

    private static int formalModelStoreClear(CommandSourceStack source) {
        ForgeVoxyInstance.INSTANCE.getFormalModelStore().clear();
        ForgeFormalModelStoreStats status = ForgeVoxyInstance.INSTANCE.getFormalModelStore().createStatusSnapshot();
        source.sendSuccess(() -> Component.literal("Voxy formal ModelStore clear: " + formatFormalModelStoreStatus(status) + " GL geometry heap, MDIC command buffer, existing MDIC debug renderer, textured MDIC debug renderer, sample-set debug resources, simple renderer, and CPU caches were left unchanged."), false);
        return 1;
    }

    private static int formalModelStoreDumpLayout(CommandSourceStack source) {
        String message = ForgeVoxyInstance.INSTANCE.getFormalModelStore().dumpLayout();
        source.sendSuccess(() -> Component.literal(message), false);
        return 1;
    }

    private static int formalModelFactoryRequestCurrent(CommandSourceStack source) {
        ForgeFormalModelFactoryStats status = ForgeVoxyInstance.INSTANCE.getFormalModelFactory().requestCurrent();
        source.sendSuccess(() -> Component.literal("Voxy formal ModelFactory request current: " + formatFormalModelFactoryStatus(status) + " No BakedModel bake, formal ModelStore upload, atlas pixel upload, shader bind, or draw was performed."), false);
        return status.lastRequestBlockStateId() >= 0 && "none".equals(status.lastFailureReason()) ? 1 : 0;
    }

    private static int formalModelFactoryRequestBlockState(CommandSourceStack source, int blockStateId) {
        ForgeFormalModelFactoryStats status = ForgeVoxyInstance.INSTANCE.getFormalModelFactory().requestBlockState(blockStateId);
        source.sendSuccess(() -> Component.literal("Voxy formal ModelFactory request blockstate: " + formatFormalModelFactoryStatus(status) + " No BakedModel bake, formal ModelStore upload, atlas pixel upload, shader bind, or draw was performed."), false);
        return status.lastRequestBlockStateId() == blockStateId && "none".equals(status.lastFailureReason()) ? 1 : 0;
    }

    private static int formalModelFactoryProcessSkeleton(CommandSourceStack source) {
        ForgeFormalModelFactoryStats status = ForgeVoxyInstance.INSTANCE.getFormalModelFactory().processSkeleton();
        source.sendSuccess(() -> Component.literal("Voxy formal ModelFactory process skeleton: " + formatFormalModelFactoryStatus(status) + " Formal model ids are skeleton ids only; real bake/upload is still disabled."), false);
        return status.formalModelFactorySkeletonReady() ? 1 : 0;
    }

    private static int formalModelFactoryStatus(CommandSourceStack source) {
        ForgeFormalModelFactoryStats status = ForgeVoxyInstance.INSTANCE.getFormalModelFactory().createStatusSnapshot();
        source.sendSuccess(() -> Component.literal("Voxy formal ModelFactory status: " + formatFormalModelFactoryStatus(status)), false);
        return status.formalModelFactorySkeletonReady() || status.stale() ? 1 : 0;
    }

    private static int formalModelFactoryAudit(CommandSourceStack source) {
        ForgeFormalModelFactoryAuditResult audit = ForgeVoxyInstance.INSTANCE.getFormalModelFactory().audit();
        ForgeFormalModelFactoryStats status = ForgeVoxyInstance.INSTANCE.getFormalModelFactory().createStatusSnapshot();
        source.sendSuccess(() -> Component.literal("Voxy formal ModelFactory audit: " + formatFormalModelFactoryAudit(audit) + " " + formatFormalModelFactoryStatus(status)), false);
        return audit.success() ? 1 : 0;
    }

    private static int formalModelFactoryAuditStatus(CommandSourceStack source) {
        ForgeFormalModelFactoryAuditResult audit = ForgeVoxyInstance.INSTANCE.getFormalModelFactory().createAuditStatusSnapshot();
        source.sendSuccess(() -> Component.literal("Voxy formal ModelFactory audit status: " + formatFormalModelFactoryAudit(audit)), false);
        return audit.success() ? 1 : 0;
    }

    private static int formalModelFactoryClear(CommandSourceStack source) {
        ForgeVoxyInstance.INSTANCE.getFormalModelFactory().clear();
        ForgeFormalModelFactoryStats status = ForgeVoxyInstance.INSTANCE.getFormalModelFactory().createStatusSnapshot();
        source.sendSuccess(() -> Component.literal("Voxy formal ModelFactory clear: " + formatFormalModelFactoryStatus(status) + " Formal ModelStore owner, GL geometry heap, MDIC command buffer, existing MDIC debug renderer, textured MDIC debug renderer, simple renderer, and CPU caches were left unchanged."), false);
        return 1;
    }

    private static int formalModelFactoryDumpMappings(CommandSourceStack source) {
        String message = ForgeVoxyInstance.INSTANCE.getFormalModelFactory().dumpMappings();
        source.sendSuccess(() -> Component.literal(message), false);
        return 1;
    }

    private static int formalModelFactoryBakeOneCurrent(CommandSourceStack source) {
        ForgeOneBlockFormalBakeUploadStats status = ForgeVoxyInstance.INSTANCE.getOneBlockFormalBakeUpload().bakeOneCurrent();
        ForgeVoxyInstance.INSTANCE.getFormalRendererManager().checkReadiness("i4-bake-one-current");
        source.sendSuccess(() -> Component.literal("Voxy I4 one-block bake/upload: " + formatOneBlockFormalBakeUploadStatus(status)), false);
        return status.oneBlockFormalUploadReady() && status.oneBlockRealBakeReady() ? 1 : 0;
    }

    private static int formalModelFactoryBakeOneStatus(CommandSourceStack source) {
        ForgeOneBlockFormalBakeUploadStats status = ForgeVoxyInstance.INSTANCE.getOneBlockFormalBakeUpload().createStatusSnapshot();
        source.sendSuccess(() -> Component.literal("Voxy I4 one-block bake/upload status: " + formatOneBlockFormalBakeUploadStatus(status)), false);
        return status.oneBlockBakePrototypeReady() || status.stale() ? 1 : 0;
    }

    private static int formalModelFactoryBakeOneAudit(CommandSourceStack source) {
        ForgeOneBlockFormalBakeUploadAuditResult audit = ForgeVoxyInstance.INSTANCE.getOneBlockFormalBakeUpload().audit();
        ForgeOneBlockFormalBakeUploadStats status = ForgeVoxyInstance.INSTANCE.getOneBlockFormalBakeUpload().createStatusSnapshot();
        source.sendSuccess(() -> Component.literal("Voxy I4 one-block bake/upload audit: " + formatOneBlockFormalBakeUploadAudit(audit) + " " + formatOneBlockFormalBakeUploadStatus(status)), false);
        return audit.success() ? 1 : 0;
    }

    private static int formalModelFactoryBakeOneDump(CommandSourceStack source) {
        String message = ForgeVoxyInstance.INSTANCE.getOneBlockFormalBakeUpload().dump();
        source.sendSuccess(() -> Component.literal(message), false);
        return ForgeVoxyInstance.INSTANCE.getOneBlockFormalBakeUpload().createStatusSnapshot().oneBlockBakePrototypeReady() ? 1 : 0;
    }

    private static int formalModelFactoryBakeOneClear(CommandSourceStack source) {
        ForgeVoxyInstance.INSTANCE.getOneBlockFormalBakeUpload().clear();
        ForgeOneBlockFormalBakeUploadStats status = ForgeVoxyInstance.INSTANCE.getOneBlockFormalBakeUpload().createStatusSnapshot();
        source.sendSuccess(() -> Component.literal("Voxy I4 one-block bake/upload clear: " + formatOneBlockFormalBakeUploadStatus(status) + " Formal ModelStore owner resources, GL heap, debug renderers, and sample-set resources were left unchanged."), false);
        return 1;
    }

    private static int qaI4FormalBakeUpload(CommandSourceStack source) {
        ForgeOneBlockFormalBakeUploadStats buildStatus = ForgeVoxyInstance.INSTANCE.getOneBlockFormalBakeUpload().bakeOneCurrent();
        ForgeOneBlockFormalBakeUploadAuditResult audit = ForgeVoxyInstance.INSTANCE.getOneBlockFormalBakeUpload().audit();
        ForgeFormalRendererStats rendererStatus = ForgeVoxyInstance.INSTANCE.getFormalRendererManager().checkReadiness("qa-i4-formal-bake-upload");
        ForgeOneBlockFormalBakeUploadStats status = ForgeVoxyInstance.INSTANCE.getOneBlockFormalBakeUpload().createStatusSnapshot();
        String message = "Voxy QA I4 formal bake/upload: "
                + formatOneBlockFormalBakeUploadStatus(status)
                + " "
                + formatOneBlockFormalBakeUploadAudit(audit)
                + " "
                + formatFormalRendererStatus(rendererStatus)
                + " no formal shader bind, formal draw, MDICSectionRenderer, or VoxyRenderSystem call was performed.";
        source.sendSuccess(() -> Component.literal(message), false);
        return buildStatus.oneBlockFormalUploadReady()
                && audit.success()
                && status.usesFormalModelIds()
                && !status.usesPlaceholderModelIds()
                && !status.sampleSetModelIdsUsed()
                && !status.formalRendererReady()
                && !status.actualDrawEnabled() ? 1 : 0;
    }

    private static int formalModelFactoryBakeMultiSafe(CommandSourceStack source) {
        ForgeMultiBlockFormalBakeUploadStats status = ForgeVoxyInstance.INSTANCE.getMultiBlockFormalBakeUpload().bakeMultiSafe();
        ForgeVoxyInstance.INSTANCE.getFormalRendererManager().checkReadiness("i5-bake-multi-safe");
        source.sendSuccess(() -> Component.literal("Voxy I5 multi-block bake/upload: " + formatMultiBlockFormalBakeUploadStatus(status)), false);
        return status.multiBlockFormalUploadReady() && status.multiBlockFormalBakeReady() ? 1 : 0;
    }

    private static int formalModelFactoryBakeMultiStatus(CommandSourceStack source) {
        ForgeMultiBlockFormalBakeUploadStats status = ForgeVoxyInstance.INSTANCE.getMultiBlockFormalBakeUpload().createStatusSnapshot();
        source.sendSuccess(() -> Component.literal("Voxy I5 multi-block bake/upload status: " + formatMultiBlockFormalBakeUploadStatus(status)), false);
        return status.multiBlockBakePrototypeReady() || status.stale() ? 1 : 0;
    }

    private static int formalModelFactoryBakeMultiAudit(CommandSourceStack source) {
        ForgeMultiBlockFormalBakeUploadAuditResult audit = ForgeVoxyInstance.INSTANCE.getMultiBlockFormalBakeUpload().audit();
        ForgeMultiBlockFormalBakeUploadStats status = ForgeVoxyInstance.INSTANCE.getMultiBlockFormalBakeUpload().createStatusSnapshot();
        source.sendSuccess(() -> Component.literal("Voxy I5 multi-block bake/upload audit: " + formatMultiBlockFormalBakeUploadAudit(audit) + " " + formatMultiBlockFormalBakeUploadStatus(status)), false);
        return audit.success() ? 1 : 0;
    }

    private static int formalModelFactoryBakeMultiDump(CommandSourceStack source) {
        String message = ForgeVoxyInstance.INSTANCE.getMultiBlockFormalBakeUpload().dump();
        source.sendSuccess(() -> Component.literal(message), false);
        return ForgeVoxyInstance.INSTANCE.getMultiBlockFormalBakeUpload().createStatusSnapshot().multiBlockBakePrototypeReady() ? 1 : 0;
    }

    private static int formalModelFactoryBakeMultiClear(CommandSourceStack source) {
        ForgeVoxyInstance.INSTANCE.getMultiBlockFormalBakeUpload().clear();
        ForgeMultiBlockFormalBakeUploadStats status = ForgeVoxyInstance.INSTANCE.getMultiBlockFormalBakeUpload().createStatusSnapshot();
        source.sendSuccess(() -> Component.literal("Voxy I5 multi-block bake/upload clear: " + formatMultiBlockFormalBakeUploadStatus(status) + " Formal ModelStore owner resources, GL heap, debug renderers, and sample-set resources were left unchanged."), false);
        return 1;
    }

    private static int qaI5MultiBlockBakeUpload(CommandSourceStack source) {
        ForgeMultiBlockFormalBakeUploadStats buildStatus = ForgeVoxyInstance.INSTANCE.getMultiBlockFormalBakeUpload().bakeMultiSafe();
        ForgeMultiBlockFormalBakeUploadAuditResult audit = ForgeVoxyInstance.INSTANCE.getMultiBlockFormalBakeUpload().audit();
        ForgeFormalRendererStats rendererStatus = ForgeVoxyInstance.INSTANCE.getFormalRendererManager().checkReadiness("qa-i5-multi-block-bake-upload");
        ForgeMultiBlockFormalBakeUploadStats status = ForgeVoxyInstance.INSTANCE.getMultiBlockFormalBakeUpload().createStatusSnapshot();
        String message = "Voxy QA I5 multi-block formal bake/upload: "
                + formatMultiBlockFormalBakeUploadStatus(status)
                + " "
                + formatMultiBlockFormalBakeUploadAudit(audit)
                + " "
                + formatFormalRendererStatus(rendererStatus)
                + " no formal shader bind, formal draw, MDICSectionRenderer, VoxyRenderSystem, or sample-set formal upload path was used.";
        source.sendSuccess(() -> Component.literal(message), false);
        return buildStatus.multiBlockFormalUploadReady()
                && audit.success()
                && status.acceptedBlockStateCount() >= 3
                && status.uploadedModelRecordCount() >= 3
                && status.uploadedFaceTileCount() >= 18
                && status.modelDataReadbackOk()
                && status.modelColourReadbackOk()
                && status.atlasReadbackOk()
                && status.atlasPixelMismatches() == 0
                && status.usesFormalModelIds()
                && !status.usesPlaceholderModelIds()
                && !status.sampleSetModelIdsUsed()
                && !status.formalRendererReady()
                && !status.actualDrawEnabled() ? 1 : 0;
    }

    private static int formalModelBakeryLifecycleStatus(CommandSourceStack source) {
        ForgeFormalModelBakeryLifecycleStats status = ForgeVoxyInstance.INSTANCE.getFormalModelBakeryLifecycle().createStatusSnapshot();
        source.sendSuccess(() -> Component.literal("Voxy I6 formal ModelBakery lifecycle status: " + formatFormalModelBakeryLifecycleStatus(status)), false);
        return status.formalModelBakeryLifecycleSkeletonReady() || status.stale() ? 1 : 0;
    }

    private static int formalModelBakeryLifecycleAudit(CommandSourceStack source) {
        ForgeFormalModelBakeryLifecycleAuditResult audit = ForgeVoxyInstance.INSTANCE.getFormalModelBakeryLifecycle().audit();
        ForgeFormalModelBakeryLifecycleStats status = ForgeVoxyInstance.INSTANCE.getFormalModelBakeryLifecycle().createStatusSnapshot();
        source.sendSuccess(() -> Component.literal("Voxy I6 formal ModelBakery lifecycle audit: " + formatFormalModelBakeryLifecycleAudit(audit) + " " + formatFormalModelBakeryLifecycleStatus(status)), false);
        return audit.success() ? 1 : 0;
    }

    private static int formalModelBakeryLifecycleRebuildSafeSet(CommandSourceStack source) {
        ForgeFormalModelBakeryLifecycleStats status = ForgeVoxyInstance.INSTANCE.getFormalModelBakeryLifecycle().rebuildSafeSet("command-rebuild-safe-set");
        ForgeFormalModelBakeryLifecycleAuditResult audit = ForgeVoxyInstance.INSTANCE.getFormalModelBakeryLifecycle().createAuditStatusSnapshot();
        ForgeFormalRendererStats rendererStatus = ForgeVoxyInstance.INSTANCE.getFormalRendererManager().checkReadiness("i6-rebuild-safe-set");
        source.sendSuccess(() -> Component.literal("Voxy I6 formal ModelBakery lifecycle rebuild: "
                + formatFormalModelBakeryLifecycleStatus(status)
                + " "
                + formatFormalModelBakeryLifecycleAudit(audit)
                + " "
                + formatFormalRendererStatus(rendererStatus)), false);
        return status.reloadRebuildPrototypeReady()
                && status.aliasSafeDedupeReady()
                && audit.success()
                && !status.formalRendererReady()
                && !status.actualDrawEnabled() ? 1 : 0;
    }

    private static int formalModelBakeryLifecycleDump(CommandSourceStack source) {
        String message = ForgeVoxyInstance.INSTANCE.getFormalModelBakeryLifecycle().dump();
        source.sendSuccess(() -> Component.literal(message), false);
        return ForgeVoxyInstance.INSTANCE.getFormalModelBakeryLifecycle().createStatusSnapshot().formalModelBakeryLifecycleSkeletonReady() ? 1 : 0;
    }

    private static int formalModelBakeryLifecycleClear(CommandSourceStack source) {
        ForgeVoxyInstance.INSTANCE.getFormalModelBakeryLifecycle().clear();
        ForgeFormalModelBakeryLifecycleStats status = ForgeVoxyInstance.INSTANCE.getFormalModelBakeryLifecycle().createStatusSnapshot();
        source.sendSuccess(() -> Component.literal("Voxy I6 formal ModelBakery lifecycle clear: " + formatFormalModelBakeryLifecycleStatus(status) + " Formal ModelStore owner resources, GL heap, debug renderers, and sample-set resources were left unchanged."), false);
        return 1;
    }

    private static int qaI6ModelLifecycleRebuild(CommandSourceStack source) {
        ForgeFormalModelBakeryLifecycleStats qaStatus = ForgeVoxyInstance.INSTANCE.getFormalModelBakeryLifecycle().runQaLifecycleRebuild();
        ForgeFormalModelBakeryLifecycleAuditResult audit = ForgeVoxyInstance.INSTANCE.getFormalModelBakeryLifecycle().createAuditStatusSnapshot();
        ForgeFormalRendererStats rendererStatus = ForgeVoxyInstance.INSTANCE.getFormalRendererManager().checkReadiness("qa-i6-model-lifecycle-rebuild");
        ForgeFormalModelBakeryLifecycleStats status = ForgeVoxyInstance.INSTANCE.getFormalModelBakeryLifecycle().createStatusSnapshot();
        String message = "Voxy QA I6 formal model lifecycle rebuild: "
                + formatFormalModelBakeryLifecycleStatus(status)
                + " "
                + formatFormalModelBakeryLifecycleAudit(audit)
                + " "
                + formatFormalRendererStatus(rendererStatus)
                + " The QA path used the I2 formal ModelStore owner and I5 formal upload path, then simulated reload invalidation and rebuilt the safe set. No formal shader bind, formal draw, MDICSectionRenderer, VoxyRenderSystem, or sample-set formal upload path was used.";
        source.sendSuccess(() -> Component.literal(message), false);
        return qaStatus.reloadRebuildPrototypeReady()
                && status.aliasSafeDedupeReady()
                && status.multiBlockFormalUploadAuditReady()
                && audit.success()
                && status.illegalDuplicateMappingCount() == 0
                && status.modelDataReadbackOk()
                && status.modelColourReadbackOk()
                && status.atlasReadbackOk()
                && status.atlasPixelMismatches() == 0
                && status.usesFormalModelIds()
                && !status.usesPlaceholderModelIds()
                && !status.sampleSetModelIdsUsed()
                && !status.realModelFactoryReady()
                && !status.realModelBakeryReady()
                && !status.realModelStoreReady()
                && !status.formalRendererReady()
                && !status.actualDrawEnabled() ? 1 : 0;
    }

    private static int formalShaderInputBuild(CommandSourceStack source) {
        ForgeFormalShaderInputConsumerStats status = ForgeVoxyInstance.INSTANCE.getFormalShaderInputConsumer().build();
        ForgeFormalRendererStats rendererStatus = ForgeVoxyInstance.INSTANCE.getFormalRendererManager().checkReadiness("j1-formal-shader-input-build-command");
        source.sendSuccess(() -> Component.literal("Voxy J1 formal shader input build: "
                + formatFormalShaderInputConsumerStatus(status)
                + " "
                + formatFormalRendererStatus(rendererStatus)
                + " No formal shader was bound and no terrain draw was performed."), false);
        return status.formalShaderInputConsumerReady()
                && status.noDraw()
                && !status.formalShaderBound()
                && !status.actualDrawEnabled()
                && !status.formalRendererReady() ? 1 : 0;
    }

    private static int formalShaderInputStatus(CommandSourceStack source) {
        ForgeFormalShaderInputConsumerStats status = ForgeVoxyInstance.INSTANCE.getFormalShaderInputConsumer().createStatusSnapshot();
        source.sendSuccess(() -> Component.literal("Voxy J1 formal shader input status: " + formatFormalShaderInputConsumerStatus(status)), false);
        return status.formalShaderInputConsumerReady() || status.stale() ? 1 : 0;
    }

    private static int formalShaderInputAudit(CommandSourceStack source) {
        ForgeFormalShaderInputConsumerAuditResult audit = ForgeVoxyInstance.INSTANCE.getFormalShaderInputConsumer().audit();
        ForgeFormalShaderInputConsumerStats status = ForgeVoxyInstance.INSTANCE.getFormalShaderInputConsumer().createStatusSnapshot();
        ForgeFormalRendererStats rendererStatus = ForgeVoxyInstance.INSTANCE.getFormalRendererManager().checkReadiness("j1-formal-shader-input-audit-command");
        source.sendSuccess(() -> Component.literal("Voxy J1 formal shader input audit: "
                + formatFormalShaderInputConsumerAudit(audit)
                + " "
                + formatFormalShaderInputConsumerStatus(status)
                + " "
                + formatFormalRendererStatus(rendererStatus)), false);
        return audit.success() ? 1 : 0;
    }

    private static int formalShaderInputDump(CommandSourceStack source) {
        String message = ForgeVoxyInstance.INSTANCE.getFormalShaderInputConsumer().dump();
        source.sendSuccess(() -> Component.literal(message), false);
        return ForgeVoxyInstance.INSTANCE.getFormalShaderInputConsumer().createStatusSnapshot().formalShaderInputConsumerReady() ? 1 : 0;
    }

    private static int formalShaderInputClear(CommandSourceStack source) {
        ForgeVoxyInstance.INSTANCE.getFormalShaderInputConsumer().clear();
        ForgeFormalShaderInputConsumerStats status = ForgeVoxyInstance.INSTANCE.getFormalShaderInputConsumer().createStatusSnapshot();
        source.sendSuccess(() -> Component.literal("Voxy J1 formal shader input clear: "
                + formatFormalShaderInputConsumerStatus(status)
                + " Formal ModelStore owner, GL heap, debug renderers, and sample-set resources were left unchanged."), false);
        return 1;
    }

    private static int qaJ1FormalShaderInput(CommandSourceStack source) {
        ForgeFormalShaderInputConsumerStats buildStatus = ForgeVoxyInstance.INSTANCE.getFormalShaderInputConsumer().build();
        ForgeFormalShaderInputConsumerAuditResult audit = ForgeVoxyInstance.INSTANCE.getFormalShaderInputConsumer().createAuditStatusSnapshot();
        ForgeFormalRendererStats rendererStatus = ForgeVoxyInstance.INSTANCE.getFormalRendererManager().checkReadiness("qa-j1-formal-shader-input");
        ForgeFormalShaderInputConsumerStats status = ForgeVoxyInstance.INSTANCE.getFormalShaderInputConsumer().createStatusSnapshot();
        String message = "Voxy QA J1 formal shader input: "
                + formatFormalShaderInputConsumerStatus(status)
                + " "
                + formatFormalShaderInputConsumerAudit(audit)
                + " "
                + formatFormalRendererStatus(rendererStatus)
                + " The QA path rebuilt the I6 safe set into the I2 formal ModelStore owner, validated formal resource bindings, and did not use the sample-set bridge as a formal source. No formal shader bind, terrain draw, MDICSectionRenderer, or VoxyRenderSystem call was performed.";
        source.sendSuccess(() -> Component.literal(message), false);
        return buildStatus.formalShaderInputConsumerReady()
                && audit.success()
                && status.formalModelStoreOwnerReady()
                && status.reloadRebuildPrototypeReady()
                && status.multiBlockFormalUploadAuditReady()
                && status.modelDataBufferReady()
                && status.modelColourBufferReady()
                && status.atlasTextureReady()
                && status.samplerReady()
                && status.bindingLayoutKnown()
                && status.bindingLayoutCompatible()
                && status.blockModelRecordLayoutCompatible()
                && status.safeSetModelIdsAddressable()
                && status.usesFormalModelIds()
                && !status.usesPlaceholderModelIds()
                && !status.sampleSetModelIdsUsed()
                && !status.sampleSetBridgeUsedAsFormalSource()
                && !status.formalShaderBound()
                && !status.actualDrawEnabled()
                && !status.formalRendererReady() ? 1 : 0;
    }

    private static int formalShaderProgramBuild(CommandSourceStack source) {
        ForgeFormalShaderProgramStats status = ForgeVoxyInstance.INSTANCE.getFormalShaderProgramValidator().build();
        ForgeFormalRendererStats rendererStatus = ForgeVoxyInstance.INSTANCE.getFormalRendererManager().checkReadiness("j2-formal-shader-program-build-command");
        source.sendSuccess(() -> Component.literal("Voxy J2 formal shader program build: "
                + formatFormalShaderProgramStatus(status)
                + " "
                + formatFormalRendererStatus(rendererStatus)
                + " Audit-only validation program only; no terrain draw or formal renderer draw was started."), false);
        return status.formalShaderProgramValidatorReady()
                && status.validationShaderCompileAttempted()
                && status.validationShaderCompileOk()
                && status.validationProgramLinkOk()
                && !status.terrainDrawStarted()
                && !status.visibleDrawStarted()
                && !status.actualDrawEnabled()
                && !status.formalRendererReady() ? 1 : 0;
    }

    private static int formalShaderProgramStatus(CommandSourceStack source) {
        ForgeFormalShaderProgramStats status = ForgeVoxyInstance.INSTANCE.getFormalShaderProgramValidator().createStatusSnapshot();
        source.sendSuccess(() -> Component.literal("Voxy J2 formal shader program status: " + formatFormalShaderProgramStatus(status)), false);
        return status.formalShaderProgramValidatorReady() || status.stale() ? 1 : 0;
    }

    private static int formalShaderProgramAudit(CommandSourceStack source) {
        ForgeFormalShaderProgramAuditResult audit = ForgeVoxyInstance.INSTANCE.getFormalShaderProgramValidator().audit();
        ForgeFormalShaderProgramStats status = ForgeVoxyInstance.INSTANCE.getFormalShaderProgramValidator().createStatusSnapshot();
        ForgeFormalRendererStats rendererStatus = ForgeVoxyInstance.INSTANCE.getFormalRendererManager().checkReadiness("j2-formal-shader-program-audit-command");
        source.sendSuccess(() -> Component.literal("Voxy J2 formal shader program audit: "
                + formatFormalShaderProgramAudit(audit)
                + " "
                + formatFormalShaderProgramStatus(status)
                + " "
                + formatFormalRendererStatus(rendererStatus)), false);
        return audit.success() ? 1 : 0;
    }

    private static int formalShaderProgramDump(CommandSourceStack source) {
        String message = ForgeVoxyInstance.INSTANCE.getFormalShaderProgramValidator().dump();
        source.sendSuccess(() -> Component.literal(message), false);
        return ForgeVoxyInstance.INSTANCE.getFormalShaderProgramValidator().createStatusSnapshot().formalShaderProgramValidatorReady() ? 1 : 0;
    }

    private static int formalShaderProgramClear(CommandSourceStack source) {
        ForgeVoxyInstance.INSTANCE.getFormalShaderProgramValidator().clear();
        ForgeFormalShaderProgramStats status = ForgeVoxyInstance.INSTANCE.getFormalShaderProgramValidator().createStatusSnapshot();
        source.sendSuccess(() -> Component.literal("Voxy J2 formal shader program clear: "
                + formatFormalShaderProgramStatus(status)
                + " Formal ModelStore owner, GL heap, debug renderers, and sample-set resources were left unchanged."), false);
        return 1;
    }

    private static int qaJ2FormalShaderProgram(CommandSourceStack source) {
        ForgeFormalShaderProgramStats buildStatus = ForgeVoxyInstance.INSTANCE.getFormalShaderProgramValidator().build();
        ForgeFormalShaderProgramAuditResult audit = ForgeVoxyInstance.INSTANCE.getFormalShaderProgramValidator().createAuditStatusSnapshot();
        ForgeFormalRendererStats rendererStatus = ForgeVoxyInstance.INSTANCE.getFormalRendererManager().checkReadiness("qa-j2-formal-shader-program");
        ForgeFormalShaderProgramStats status = ForgeVoxyInstance.INSTANCE.getFormalShaderProgramValidator().createStatusSnapshot();
        String message = "Voxy QA J2 formal shader program: "
                + formatFormalShaderProgramStatus(status)
                + " "
                + formatFormalShaderProgramAudit(audit)
                + " "
                + formatFormalRendererStatus(rendererStatus)
                + " The QA path rebuilt the I6 safe set if needed, validated J1 formal inputs, compiled/linked an audit-only compute shader, bound formal ModelStore resources, ran compact GPU readback validation, and did not draw terrain, call MDICSectionRenderer, call VoxyRenderSystem, or use sample-set data as a formal source.";
        source.sendSuccess(() -> Component.literal(message), false);
        return buildStatus.formalShaderProgramValidatorReady()
                && status.validationShaderCompileAttempted()
                && status.validationShaderCompileOk()
                && status.validationProgramLinkOk()
                && status.modelDataBindingOk()
                && status.modelColourBindingOk()
                && status.atlasTextureBindingOk()
                && status.samplerBindingOk()
                && status.bindingLayoutCompatible()
                && status.blockModelRecordLayoutCompatible()
                && status.safeSetModelCount() >= 3
                && status.validatedModelCount() >= 1
                && status.gpuValidationAttempted()
                && status.gpuValidationOk()
                && status.validationReadbackOk()
                && status.validationFailureCount() == 0
                && status.usesFormalModelIds()
                && !status.usesPlaceholderModelIds()
                && !status.sampleSetModelIdsUsed()
                && !status.sampleSetBridgeUsedAsFormalSource()
                && !status.terrainDrawStarted()
                && !status.visibleDrawStarted()
                && !status.actualDrawEnabled()
                && !status.formalTexturedShaderReady()
                && !status.formalRendererReady()
                && audit.success()
                && !rendererStatus.formalRendererReady()
                && !rendererStatus.actualDrawEnabled() ? 1 : 0;
    }

    private static int formalTexturedShaderBuild(CommandSourceStack source) {
        ForgeFormalTexturedShaderPreviewStats status = ForgeVoxyInstance.INSTANCE.getFormalTexturedShaderPreview().build();
        ForgeFormalRendererStats rendererStatus = ForgeVoxyInstance.INSTANCE.getFormalRendererManager().checkReadiness("j3-formal-textured-shader-preview-build-command");
        source.sendSuccess(() -> Component.literal("Voxy J3 formal textured shader preview build: "
                + formatFormalTexturedShaderPreviewStatus(status)
                + " "
                + formatFormalRendererStatus(rendererStatus)
                + " Offscreen preview draw only; no terrain draw or formal renderer draw was started."), false);
        return status.formalTexturedShaderPrototypeReady()
                && status.previewDrawOnly()
                && status.offscreenPreviewReady()
                && status.previewReadbackOk()
                && !status.terrainDrawStarted()
                && !status.formalRendererDrawStarted()
                && !status.actualRendererDrawEnabled()
                && !status.formalRendererReady() ? 1 : 0;
    }

    private static int formalTexturedShaderStatus(CommandSourceStack source) {
        ForgeFormalTexturedShaderPreviewStats status = ForgeVoxyInstance.INSTANCE.getFormalTexturedShaderPreview().createStatusSnapshot();
        source.sendSuccess(() -> Component.literal("Voxy J3 formal textured shader preview status: " + formatFormalTexturedShaderPreviewStatus(status)), false);
        return status.formalTexturedShaderPrototypeReady() || status.stale() ? 1 : 0;
    }

    private static int formalTexturedShaderAudit(CommandSourceStack source) {
        ForgeFormalTexturedShaderPreviewAuditResult audit = ForgeVoxyInstance.INSTANCE.getFormalTexturedShaderPreview().audit();
        ForgeFormalTexturedShaderPreviewStats status = ForgeVoxyInstance.INSTANCE.getFormalTexturedShaderPreview().createStatusSnapshot();
        ForgeFormalRendererStats rendererStatus = ForgeVoxyInstance.INSTANCE.getFormalRendererManager().checkReadiness("j3-formal-textured-shader-preview-audit-command");
        source.sendSuccess(() -> Component.literal("Voxy J3 formal textured shader preview audit: "
                + formatFormalTexturedShaderPreviewAudit(audit)
                + " "
                + formatFormalTexturedShaderPreviewStatus(status)
                + " "
                + formatFormalRendererStatus(rendererStatus)), false);
        return audit.success() ? 1 : 0;
    }

    private static int formalTexturedShaderDump(CommandSourceStack source) {
        String message = ForgeVoxyInstance.INSTANCE.getFormalTexturedShaderPreview().dump();
        source.sendSuccess(() -> Component.literal(message), false);
        return ForgeVoxyInstance.INSTANCE.getFormalTexturedShaderPreview().createStatusSnapshot().formalTexturedShaderPrototypeReady() ? 1 : 0;
    }

    private static int formalTexturedShaderClear(CommandSourceStack source) {
        ForgeVoxyInstance.INSTANCE.getFormalTexturedShaderPreview().clear();
        ForgeFormalTexturedShaderPreviewStats status = ForgeVoxyInstance.INSTANCE.getFormalTexturedShaderPreview().createStatusSnapshot();
        source.sendSuccess(() -> Component.literal("Voxy J3 formal textured shader preview clear: "
                + formatFormalTexturedShaderPreviewStatus(status)
                + " Formal ModelStore owner, GL heap, debug renderers, and sample-set resources were left unchanged."), false);
        return 1;
    }

    private static int formalTexturedShaderPreviewEnable(CommandSourceStack source) {
        ForgeFormalTexturedShaderPreviewStats status = ForgeVoxyInstance.INSTANCE.getFormalTexturedShaderPreview().enableVisiblePreview();
        source.sendSuccess(() -> Component.literal("Voxy J3 visible formal textured shader preview enable rejected: "
                + formatFormalTexturedShaderPreviewStatus(status)
                + " Visible preview is intentionally not implemented in J3 QA; offscreen readback remains the validation path."), false);
        return !status.visiblePreviewEnabled() && !status.actualRendererDrawEnabled() ? 1 : 0;
    }

    private static int formalTexturedShaderPreviewDisable(CommandSourceStack source) {
        ForgeFormalTexturedShaderPreviewStats status = ForgeVoxyInstance.INSTANCE.getFormalTexturedShaderPreview().disableVisiblePreview();
        source.sendSuccess(() -> Component.literal("Voxy J3 formal textured shader preview disable: " + formatFormalTexturedShaderPreviewStatus(status)), false);
        return !status.visiblePreviewEnabled() ? 1 : 0;
    }

    private static int qaJ3FormalTexturedShaderPreview(CommandSourceStack source) {
        ForgeFormalTexturedShaderPreviewStats buildStatus = ForgeVoxyInstance.INSTANCE.getFormalTexturedShaderPreview().build();
        ForgeFormalTexturedShaderPreviewAuditResult audit = ForgeVoxyInstance.INSTANCE.getFormalTexturedShaderPreview().createAuditStatusSnapshot();
        ForgeFormalRendererStats rendererStatus = ForgeVoxyInstance.INSTANCE.getFormalRendererManager().checkReadiness("qa-j3-formal-textured-shader-preview");
        ForgeFormalTexturedShaderPreviewStats status = ForgeVoxyInstance.INSTANCE.getFormalTexturedShaderPreview().createStatusSnapshot();
        String message = "Voxy QA J3 formal textured shader preview: "
                + formatFormalTexturedShaderPreviewStatus(status)
                + " "
                + formatFormalTexturedShaderPreviewAudit(audit)
                + " "
                + formatFormalRendererStatus(rendererStatus)
                + " The QA path rebuilt the I6 safe set if needed, validated J2 formal shader inputs, compiled/linked a formal textured preview shader, rendered selected formal model ids only to an offscreen framebuffer, read back preview checksums, and did not draw terrain, call MDICSectionRenderer, call VoxyRenderSystem, or use sample-set data as a formal source.";
        source.sendSuccess(() -> Component.literal(message), false);
        return buildStatus.formalTexturedShaderPrototypeReady()
                && status.shaderCompileOk()
                && status.programLinkOk()
                && status.modelDataBindingOk()
                && status.modelColourBindingOk()
                && status.atlasTextureBindingOk()
                && status.samplerBindingOk()
                && status.bindingLayoutCompatible()
                && status.safeSetModelCount() >= 3
                && status.previewModelCount() >= 1
                && status.previewFramebufferCreated()
                && status.previewFramebufferComplete()
                && status.previewReadbackOk()
                && status.previewPixelMismatches() == 0
                && status.usesFormalModelIds()
                && !status.usesPlaceholderModelIds()
                && !status.sampleSetModelIdsUsed()
                && !status.sampleSetBridgeUsedAsFormalSource()
                && status.previewDrawOnly()
                && !status.terrainDrawStarted()
                && !status.formalRendererDrawStarted()
                && !status.actualRendererDrawEnabled()
                && !status.formalTexturedShaderReady()
                && !status.formalRendererReady()
                && audit.success()
                && !rendererStatus.formalRendererReady()
                && !rendererStatus.actualDrawEnabled() ? 1 : 0;
    }

    private static int formalPackedQuadPreviewBuild(CommandSourceStack source) {
        ForgeFormalPackedQuadPreviewStats status = ForgeVoxyInstance.INSTANCE.getFormalPackedQuadPreview().build();
        ForgeFormalRendererStats rendererStatus = ForgeVoxyInstance.INSTANCE.getFormalRendererManager().checkReadiness("j4-formal-packed-quad-preview-build-command");
        source.sendSuccess(() -> Component.literal("Voxy J4 formal packed quad preview build: "
                + formatFormalPackedQuadPreviewStatus(status)
                + " "
                + formatFormalRendererStatus(rendererStatus)
                + " Offscreen packed-quad preview only; no terrain draw, formal renderer draw, MDICSectionRenderer, or VoxyRenderSystem call was started."), false);
        return status.formalPackedQuadPreviewReady()
                && status.packedQuadShaderPreviewReady()
                && status.temporaryFormalQuadBufferCreated()
                && status.temporaryFormalQuadCount() > 0
                && status.usesFormalModelIds()
                && !status.usesPlaceholderModelIds()
                && !status.sampleSetModelIdsUsed()
                && status.originalGeometryUntouched()
                && status.previewReadbackOk()
                && status.previewPixelMismatches() == 0
                && !status.terrainDrawStarted()
                && !status.formalRendererDrawStarted()
                && !status.actualRendererDrawEnabled()
                && !status.formalTexturedShaderReady()
                && !status.formalRendererReady() ? 1 : 0;
    }

    private static int formalPackedQuadPreviewStatus(CommandSourceStack source) {
        ForgeFormalPackedQuadPreviewStats status = ForgeVoxyInstance.INSTANCE.getFormalPackedQuadPreview().createStatusSnapshot();
        source.sendSuccess(() -> Component.literal("Voxy J4 formal packed quad preview status: " + formatFormalPackedQuadPreviewStatus(status)), false);
        return status.formalPackedQuadPreviewReady() || status.stale() ? 1 : 0;
    }

    private static int formalPackedQuadPreviewAudit(CommandSourceStack source) {
        ForgeFormalPackedQuadPreviewAuditResult audit = ForgeVoxyInstance.INSTANCE.getFormalPackedQuadPreview().audit();
        ForgeFormalPackedQuadPreviewStats status = ForgeVoxyInstance.INSTANCE.getFormalPackedQuadPreview().createStatusSnapshot();
        ForgeFormalRendererStats rendererStatus = ForgeVoxyInstance.INSTANCE.getFormalRendererManager().checkReadiness("j4-formal-packed-quad-preview-audit-command");
        source.sendSuccess(() -> Component.literal("Voxy J4 formal packed quad preview audit: "
                + formatFormalPackedQuadPreviewAudit(audit)
                + " "
                + formatFormalPackedQuadPreviewStatus(status)
                + " "
                + formatFormalRendererStatus(rendererStatus)), false);
        return audit.success() ? 1 : 0;
    }

    private static int formalPackedQuadPreviewDump(CommandSourceStack source) {
        String message = ForgeVoxyInstance.INSTANCE.getFormalPackedQuadPreview().dump();
        source.sendSuccess(() -> Component.literal(message), false);
        return ForgeVoxyInstance.INSTANCE.getFormalPackedQuadPreview().createStatusSnapshot().formalPackedQuadPreviewReady() ? 1 : 0;
    }

    private static int formalPackedQuadPreviewClear(CommandSourceStack source) {
        ForgeVoxyInstance.INSTANCE.getFormalPackedQuadPreview().clear();
        ForgeFormalPackedQuadPreviewStats status = ForgeVoxyInstance.INSTANCE.getFormalPackedQuadPreview().createStatusSnapshot();
        source.sendSuccess(() -> Component.literal("Voxy J4 formal packed quad preview clear: "
                + formatFormalPackedQuadPreviewStatus(status)
                + " Formal ModelStore owner, original GL geometry heap, debug renderers, and sample-set resources were left unchanged."), false);
        return 1;
    }

    private static int qaJ4FormalPackedQuadPreview(CommandSourceStack source) {
        ForgeFormalPackedQuadPreviewStats buildStatus = ForgeVoxyInstance.INSTANCE.getFormalPackedQuadPreview().build();
        ForgeFormalPackedQuadPreviewAuditResult audit = ForgeVoxyInstance.INSTANCE.getFormalPackedQuadPreview().createAuditStatusSnapshot();
        ForgeFormalRendererStats rendererStatus = ForgeVoxyInstance.INSTANCE.getFormalRendererManager().checkReadiness("qa-j4-formal-packed-quad-preview");
        ForgeFormalPackedQuadPreviewStats status = ForgeVoxyInstance.INSTANCE.getFormalPackedQuadPreview().createStatusSnapshot();
        String message = "Voxy QA J4 formal packed quad preview: "
                + formatFormalPackedQuadPreviewStatus(status)
                + " "
                + formatFormalPackedQuadPreviewAudit(audit)
                + " "
                + formatFormalRendererStatus(rendererStatus)
                + " The QA path rebuilt the I6 safe set if needed, validated J3 formal textured preview resources, mapped packed quad-style records to formal model ids in a temporary isolated buffer, rendered only to an offscreen framebuffer, and did not touch the original geometry heap, draw terrain, call MDICSectionRenderer, call VoxyRenderSystem, or use sample-set data as a formal source.";
        source.sendSuccess(() -> Component.literal(message), false);
        return buildStatus.formalPackedQuadPreviewReady()
                && status.packedQuadShaderPreviewReady()
                && status.temporaryFormalQuadBufferCreated()
                && status.temporaryFormalQuadCount() >= 1
                && status.usesFormalModelIds()
                && !status.usesPlaceholderModelIds()
                && !status.sampleSetModelIdsUsed()
                && !status.sampleSetBridgeUsedAsFormalSource()
                && status.originalGeometryUntouched()
                && status.quadRecordDecodeOk()
                && status.modelIdDecodeOk()
                && status.faceDecodeOk()
                && status.previewFramebufferComplete()
                && status.previewReadbackOk()
                && status.previewPixelMismatches() == 0
                && !status.terrainDrawStarted()
                && !status.formalRendererDrawStarted()
                && !status.actualRendererDrawEnabled()
                && !status.formalTexturedShaderReady()
                && !status.formalRendererReady()
                && audit.success()
                && !rendererStatus.formalRendererReady()
                && !rendererStatus.actualDrawEnabled() ? 1 : 0;
    }

    private static int formalTerrainRecordBridgeBuild(CommandSourceStack source) {
        ForgeFormalTerrainPackedRecordBridgeStats status = ForgeVoxyInstance.INSTANCE.getFormalTerrainPackedRecordBridge().build();
        ForgeFormalRendererStats rendererStatus = ForgeVoxyInstance.INSTANCE.getFormalRendererManager().checkReadiness("j5-real-terrain-record-bridge-build-command");
        String message = "Voxy J5 real terrain record bridge build: "
                + formatFormalTerrainRecordBridgeStatus(status)
                + " "
                + formatFormalRendererStatus(rendererStatus)
                + " Offscreen preview only; source terrain/BuiltSection records are copied into an isolated temporary formal quad buffer and original geometry remains untouched.";
        VoxyForge.LOGGER.info(message);
        source.sendSuccess(() -> Component.literal(message), false);
        return status.realTerrainPackedRecordBridgeReady()
                && status.realTerrainRecordsUsed()
                && !status.syntheticFallbackUsed()
                && status.sourceRecordsAccepted() >= 1
                && status.temporaryFormalQuadBufferCreated()
                && status.temporaryFormalQuadCount() >= 1
                && status.previewReadbackOk()
                && status.previewPixelMismatches() == 0
                && !status.terrainDrawStarted()
                && !status.formalRendererDrawStarted()
                && !status.actualRendererDrawEnabled()
                && !status.formalRendererReady() ? 1 : 0;
    }

    private static int formalTerrainRecordBridgeStatus(CommandSourceStack source) {
        ForgeFormalTerrainPackedRecordBridgeStats status = ForgeVoxyInstance.INSTANCE.getFormalTerrainPackedRecordBridge().createStatusSnapshot();
        String message = "Voxy J5 real terrain record bridge status: " + formatFormalTerrainRecordBridgeStatus(status);
        VoxyForge.LOGGER.info(message);
        source.sendSuccess(() -> Component.literal(message), false);
        return status.formalTerrainPackedRecordBridgeReady() || status.stale() ? 1 : 0;
    }

    private static int formalTerrainRecordBridgeAudit(CommandSourceStack source) {
        ForgeFormalTerrainPackedRecordBridgeAuditResult audit = ForgeVoxyInstance.INSTANCE.getFormalTerrainPackedRecordBridge().audit();
        ForgeFormalTerrainPackedRecordBridgeStats status = ForgeVoxyInstance.INSTANCE.getFormalTerrainPackedRecordBridge().createStatusSnapshot();
        ForgeFormalRendererStats rendererStatus = ForgeVoxyInstance.INSTANCE.getFormalRendererManager().checkReadiness("j5-real-terrain-record-bridge-audit-command");
        String message = "Voxy J5 real terrain record bridge audit: "
                + formatFormalTerrainRecordBridgeAudit(audit)
                + " "
                + formatFormalTerrainRecordBridgeStatus(status)
                + " "
                + formatFormalRendererStatus(rendererStatus);
        VoxyForge.LOGGER.info(message);
        source.sendSuccess(() -> Component.literal(message), false);
        return audit.success() ? 1 : 0;
    }

    private static int formalTerrainRecordBridgeDump(CommandSourceStack source) {
        String message = ForgeVoxyInstance.INSTANCE.getFormalTerrainPackedRecordBridge().dump();
        VoxyForge.LOGGER.info(message);
        source.sendSuccess(() -> Component.literal(message), false);
        return ForgeVoxyInstance.INSTANCE.getFormalTerrainPackedRecordBridge().createStatusSnapshot().formalTerrainPackedRecordBridgeReady() ? 1 : 0;
    }

    private static int formalTerrainRecordBridgeClear(CommandSourceStack source) {
        ForgeVoxyInstance.INSTANCE.getFormalTerrainPackedRecordBridge().clear();
        ForgeFormalTerrainPackedRecordBridgeStats status = ForgeVoxyInstance.INSTANCE.getFormalTerrainPackedRecordBridge().createStatusSnapshot();
        String message = "Voxy J5 real terrain record bridge clear: "
                + formatFormalTerrainRecordBridgeStatus(status)
                + " Formal ModelStore owner, original GL geometry heap, debug renderers, and sample-set resources were left unchanged.";
        VoxyForge.LOGGER.info(message);
        source.sendSuccess(() -> Component.literal(message), false);
        return 1;
    }

    private static int qaJ5RealTerrainRecordBridge(CommandSourceStack source) {
        ForgeFormalTerrainPackedRecordBridgeStats buildStatus = ForgeVoxyInstance.INSTANCE.getFormalTerrainPackedRecordBridge().build();
        ForgeFormalTerrainPackedRecordBridgeAuditResult audit = ForgeVoxyInstance.INSTANCE.getFormalTerrainPackedRecordBridge().createAuditStatusSnapshot();
        ForgeFormalRendererStats rendererStatus = ForgeVoxyInstance.INSTANCE.getFormalRendererManager().checkReadiness("qa-j5-real-terrain-record-bridge");
        ForgeFormalTerrainPackedRecordBridgeStats status = ForgeVoxyInstance.INSTANCE.getFormalTerrainPackedRecordBridge().createStatusSnapshot();
        String message = "Voxy QA J5 real terrain packed-record formal model-id bridge: "
                + formatFormalTerrainRecordBridgeStatus(status)
                + " "
                + formatFormalTerrainRecordBridgeAudit(audit)
                + " "
                + formatFormalRendererStatus(rendererStatus)
                + " The QA path rebuilds the I6 safe set, creates real current-world BuiltSection packed records, maps their blockState source to formal model ids, rewrites only a temporary isolated preview buffer, renders offscreen through the J4 preview path, and does not mutate the original geometry heap, draw live terrain, call MDICSectionRenderer, call VoxyRenderSystem, or use sample-set data as a formal source.";
        VoxyForge.LOGGER.info(message);
        source.sendSuccess(() -> Component.literal(message), false);
        return buildStatus.formalTerrainPackedRecordBridgeReady()
                && status.realTerrainPackedRecordBridgeReady()
                && status.realTerrainRecordsUsed()
                && !status.syntheticFallbackUsed()
                && status.sourceRecordsAccepted() >= 1
                && status.temporaryFormalQuadBufferCreated()
                && status.temporaryFormalQuadCount() >= 1
                && status.usesFormalModelIds()
                && !status.usesPlaceholderModelIds()
                && !status.sampleSetModelIdsUsed()
                && status.blockStateSourceAvailable()
                && status.formalModelIdLookupOk()
                && status.temporaryModelIdRewriteOk()
                && status.originalRecordUnchanged()
                && status.originalGeometryUntouched()
                && status.originalGeometryHeapUntouched()
                && status.previewReadbackOk()
                && status.previewPixelMismatches() == 0
                && !status.terrainDrawStarted()
                && !status.formalRendererDrawStarted()
                && !status.actualRendererDrawEnabled()
                && !status.formalTexturedShaderReady()
                && !status.formalRendererReady()
                && audit.success()
                && !rendererStatus.formalRendererReady()
                && !rendererStatus.actualDrawEnabled() ? 1 : 0;
    }

    private static int formalTerrainRendererOwnerEnable(CommandSourceStack source) {
        ForgeFormalTerrainRendererStats status = ForgeVoxyInstance.INSTANCE.getFormalTerrainRendererOwner().enable("command-enable");
        ForgeFormalRendererStats rendererStatus = ForgeVoxyInstance.INSTANCE.getFormalRendererManager().checkReadiness("k1-formal-terrain-renderer-owner-enable");
        String message = "Voxy K1 formal terrain renderer owner enable: "
                + formatFormalTerrainRendererOwnerStatus(status)
                + " "
                + formatFormalRendererStatus(rendererStatus)
                + " No live terrain draw, MDICSectionRenderer call, VoxyRenderSystem call, or formal MDIC draw was started.";
        source.sendSuccess(() -> Component.literal(message), false);
        return status.formalTerrainRendererOwnerReady()
                && status.noDraw()
                && !status.formalTerrainRendererReady()
                && !status.actualRendererDrawEnabled()
                && !rendererStatus.formalRendererReady()
                && !rendererStatus.actualDrawEnabled() ? 1 : 0;
    }

    private static int formalTerrainRendererOwnerStatus(CommandSourceStack source) {
        ForgeFormalTerrainRendererStats status = ForgeVoxyInstance.INSTANCE.getFormalTerrainRendererOwner().createStatusSnapshot();
        source.sendSuccess(() -> Component.literal("Voxy K1 formal terrain renderer owner status: " + formatFormalTerrainRendererOwnerStatus(status)), false);
        return status.formalTerrainRendererOwnerReady() || status.stale() ? 1 : 0;
    }

    private static int formalTerrainRendererOwnerCheck(CommandSourceStack source) {
        ForgeFormalTerrainRendererStats status = ForgeVoxyInstance.INSTANCE.getFormalTerrainRendererOwner().check("command-check");
        ForgeFormalRendererStats rendererStatus = ForgeVoxyInstance.INSTANCE.getFormalRendererManager().checkReadiness("k1-formal-terrain-renderer-owner-check");
        source.sendSuccess(() -> Component.literal("Voxy K1 formal terrain renderer owner check: "
                + formatFormalTerrainRendererOwnerStatus(status)
                + " "
                + formatFormalRendererStatus(rendererStatus)), false);
        return status.formalTerrainRendererOwnerReady() && status.noDraw() ? 1 : 0;
    }

    private static int formalTerrainRendererOwnerAudit(CommandSourceStack source) {
        ForgeFormalTerrainRendererAuditResult audit = ForgeVoxyInstance.INSTANCE.getFormalTerrainRendererOwner().audit();
        ForgeFormalTerrainRendererStats status = ForgeVoxyInstance.INSTANCE.getFormalTerrainRendererOwner().createStatusSnapshot();
        ForgeFormalRendererStats rendererStatus = ForgeVoxyInstance.INSTANCE.getFormalRendererManager().checkReadiness("k1-formal-terrain-renderer-owner-audit");
        source.sendSuccess(() -> Component.literal("Voxy K1 formal terrain renderer owner audit: "
                + formatFormalTerrainRendererOwnerAudit(audit)
                + " "
                + formatFormalTerrainRendererOwnerStatus(status)
                + " "
                + formatFormalRendererStatus(rendererStatus)), false);
        return audit.success() ? 1 : 0;
    }

    private static int formalTerrainRendererOwnerDump(CommandSourceStack source) {
        ForgeFormalTerrainRendererStats status = ForgeVoxyInstance.INSTANCE.getFormalTerrainRendererOwner().check("command-dump");
        String blockers = ForgeVoxyInstance.INSTANCE.getFormalTerrainRendererOwner().dumpBlockers();
        source.sendSuccess(() -> Component.literal("Voxy K1 formal terrain renderer owner dump: blockers=" + blockers + " " + formatFormalTerrainRendererOwnerStatus(status)), false);
        return status.blockerCount() > 0 ? 1 : 0;
    }

    private static int formalTerrainRendererOwnerClear(CommandSourceStack source) {
        ForgeFormalTerrainRendererStats status = ForgeVoxyInstance.INSTANCE.getFormalTerrainRendererOwner().clear("command-clear");
        source.sendSuccess(() -> Component.literal("Voxy K1 formal terrain renderer owner clear: "
                + formatFormalTerrainRendererOwnerStatus(status)
                + " Only K1 owner lifecycle/status was cleared; GL geometry heap, MDIC command buffers, debug renderers, J-stage previews, and formal ModelStore resources were left unchanged."), false);
        return 1;
    }

    private static int qaK1FormalTerrainRendererOwner(CommandSourceStack source) {
        ForgeFormalTerrainRendererStats enableStatus = ForgeVoxyInstance.INSTANCE.getFormalTerrainRendererOwner().enable("qa-k1-formal-terrain-renderer-owner");
        ForgeFormalTerrainRendererStats checkStatus = ForgeVoxyInstance.INSTANCE.getFormalTerrainRendererOwner().check("qa-k1-formal-terrain-renderer-owner");
        ForgeFormalTerrainRendererAuditResult audit = ForgeVoxyInstance.INSTANCE.getFormalTerrainRendererOwner().audit();
        ForgeFormalTerrainRendererStats status = ForgeVoxyInstance.INSTANCE.getFormalTerrainRendererOwner().createStatusSnapshot();
        ForgeFormalRendererStats rendererStatus = ForgeVoxyInstance.INSTANCE.getFormalRendererManager().checkReadiness("qa-k1-formal-terrain-renderer-owner");
        String message = "Voxy QA K1 formal terrain renderer owner no-draw skeleton: "
                + formatFormalTerrainRendererOwnerStatus(status)
                + " "
                + formatFormalTerrainRendererOwnerAudit(audit)
                + " "
                + formatFormalRendererStatus(rendererStatus)
                + " K1 references formal model/shader/J5 readiness, defines viewport/command/visibility/draw placeholders as missing, keeps J-stage previews separated, and starts no live terrain draw.";
        VoxyForge.LOGGER.info(message);
        source.sendSuccess(() -> Component.literal(message), false);
        return enableStatus.formalTerrainRendererOwnerReady()
                && checkStatus.k0VerdictReadyForK1()
                && audit.success()
                && status.formalTerrainRendererOwnerReady()
                && status.formalTerrainRendererLifecycleReady()
                && !status.formalTerrainRendererReady()
                && !status.actualRendererDrawEnabled()
                && status.noDraw()
                && status.k0VerdictReadyForK1()
                && status.originalVoxyAlignmentPreserved()
                && status.debugRendererIsolationOk()
                && status.previewSystemsSeparated()
                && !status.sampleSetUsedAsFormalSource()
                && status.p0BlockerCount() >= 1
                && !rendererStatus.formalRendererReady()
                && !rendererStatus.actualDrawEnabled() ? 1 : 0;
    }

    private static int formalMdicViewportOwnerEnable(CommandSourceStack source) {
        ForgeVoxyInstance.INSTANCE.getFormalTerrainRendererOwner().enable("k2-formal-mdic-viewport-owner-enable");
        ForgeFormalMdicViewportStats status = ForgeVoxyInstance.INSTANCE.getFormalMdicViewportOwner().enable("command-enable");
        ForgeFormalRendererStats rendererStatus = ForgeVoxyInstance.INSTANCE.getFormalRendererManager().checkReadiness("k2-formal-mdic-viewport-owner-enable");
        source.sendSuccess(() -> Component.literal("Voxy K2 formal MDIC viewport owner enable: "
                + formatFormalMdicViewportOwnerStatus(status)
                + " "
                + formatFormalRendererStatus(rendererStatus)
                + " No formal draw, cmdgen, glMultiDrawElementsIndirectCountARB, MDICSectionRenderer, or VoxyRenderSystem call was started."), false);
        return status.formalViewportOwnerReady()
                && status.formalCommandBufferOwnerReady()
                && status.formalVisibilityOwnerReady()
                && status.noDraw()
                && !status.actualRendererDrawEnabled()
                && !rendererStatus.formalRendererReady()
                && !rendererStatus.actualDrawEnabled() ? 1 : 0;
    }

    private static int formalMdicViewportOwnerStatus(CommandSourceStack source) {
        ForgeFormalMdicViewportStats status = ForgeVoxyInstance.INSTANCE.getFormalMdicViewportOwner().createStatusSnapshot();
        source.sendSuccess(() -> Component.literal("Voxy K2 formal MDIC viewport owner status: " + formatFormalMdicViewportOwnerStatus(status)), false);
        return status.formalViewportOwnerReady() || status.stale() ? 1 : 0;
    }

    private static int formalMdicViewportOwnerCheck(CommandSourceStack source) {
        ForgeFormalMdicViewportStats status = ForgeVoxyInstance.INSTANCE.getFormalMdicViewportOwner().check("command-check");
        ForgeFormalRendererStats rendererStatus = ForgeVoxyInstance.INSTANCE.getFormalRendererManager().checkReadiness("k2-formal-mdic-viewport-owner-check");
        source.sendSuccess(() -> Component.literal("Voxy K2 formal MDIC viewport owner check: "
                + formatFormalMdicViewportOwnerStatus(status)
                + " "
                + formatFormalRendererStatus(rendererStatus)), false);
        return status.formalViewportOwnerReady()
                && status.formalCommandBufferOwnerReady()
                && status.formalVisibilityOwnerReady()
                && status.noDraw() ? 1 : 0;
    }

    private static int formalMdicViewportOwnerAudit(CommandSourceStack source) {
        ForgeFormalMdicViewportAuditResult audit = ForgeVoxyInstance.INSTANCE.getFormalMdicViewportOwner().audit();
        ForgeFormalMdicViewportStats status = ForgeVoxyInstance.INSTANCE.getFormalMdicViewportOwner().createStatusSnapshot();
        ForgeFormalRendererStats rendererStatus = ForgeVoxyInstance.INSTANCE.getFormalRendererManager().checkReadiness("k2-formal-mdic-viewport-owner-audit");
        source.sendSuccess(() -> Component.literal("Voxy K2 formal MDIC viewport owner audit: "
                + formatFormalMdicViewportOwnerAudit(audit)
                + " "
                + formatFormalMdicViewportOwnerStatus(status)
                + " "
                + formatFormalRendererStatus(rendererStatus)), false);
        return audit.success() ? 1 : 0;
    }

    private static int formalMdicViewportOwnerDump(CommandSourceStack source) {
        ForgeFormalMdicViewportStats status = ForgeVoxyInstance.INSTANCE.getFormalMdicViewportOwner().check("command-dump");
        String blockers = ForgeVoxyInstance.INSTANCE.getFormalMdicViewportOwner().dumpBlockers();
        source.sendSuccess(() -> Component.literal("Voxy K2 formal MDIC viewport owner dump: blockers=" + blockers + " " + formatFormalMdicViewportOwnerStatus(status)), false);
        return status.blockerCount() > 0 ? 1 : 0;
    }

    private static int formalMdicViewportOwnerClear(CommandSourceStack source) {
        ForgeFormalMdicViewportStats status = ForgeVoxyInstance.INSTANCE.getFormalMdicViewportOwner().clear("command-clear");
        source.sendSuccess(() -> Component.literal("Voxy K2 formal MDIC viewport owner clear: "
                + formatFormalMdicViewportOwnerStatus(status)
                + " Only K2 owner lifecycle/status was cleared; debug MDIC command buffers, GL geometry heap, J-stage previews, and formal ModelStore resources were left unchanged."), false);
        return 1;
    }

    private static int qaK2FormalMdicViewportOwner(CommandSourceStack source) {
        ForgeFormalTerrainRendererStats terrainStatus = ForgeVoxyInstance.INSTANCE.getFormalTerrainRendererOwner().enable("qa-k2-formal-mdic-viewport-owner");
        ForgeFormalMdicViewportStats enableStatus = ForgeVoxyInstance.INSTANCE.getFormalMdicViewportOwner().enable("qa-k2-formal-mdic-viewport-owner");
        ForgeFormalMdicViewportStats checkStatus = ForgeVoxyInstance.INSTANCE.getFormalMdicViewportOwner().check("qa-k2-formal-mdic-viewport-owner");
        ForgeFormalMdicViewportAuditResult audit = ForgeVoxyInstance.INSTANCE.getFormalMdicViewportOwner().audit();
        ForgeFormalMdicViewportStats status = ForgeVoxyInstance.INSTANCE.getFormalMdicViewportOwner().createStatusSnapshot();
        ForgeFormalTerrainRendererStats terrainOwnerStatus = ForgeVoxyInstance.INSTANCE.getFormalTerrainRendererOwner().check("qa-k2-formal-mdic-viewport-owner");
        ForgeFormalRendererStats rendererStatus = ForgeVoxyInstance.INSTANCE.getFormalRendererManager().checkReadiness("qa-k2-formal-mdic-viewport-owner");
        String message = "Voxy QA K2 formal MDIC viewport / command / visibility owner no-draw skeleton: "
                + formatFormalMdicViewportOwnerStatus(status)
                + " "
                + formatFormalMdicViewportOwnerAudit(audit)
                + " "
                + formatFormalTerrainRendererOwnerStatus(terrainOwnerStatus)
                + " "
                + formatFormalRendererStatus(rendererStatus)
                + " K2 creates formal logical owners for viewport, draw command, draw count, visibility, render-list/indirect lookup, and position scratch resources; debug MDIC command buffers are not used as formal resources and no draw/cmdgen call is issued.";
        VoxyForge.LOGGER.info(message);
        source.sendSuccess(() -> Component.literal(message), false);
        return terrainStatus.formalTerrainRendererOwnerReady()
                && enableStatus.formalViewportOwnerReady()
                && checkStatus.formalCommandBufferOwnerReady()
                && status.formalDrawCommandBufferOwnerReady()
                && status.formalDrawCountBufferOwnerReady()
                && status.formalVisibilityOwnerReady()
                && status.formalRenderListOwnerReady()
                && status.formalPositionScratchOwnerReady()
                && !status.debugMdicCommandBuffersUsedAsFormal()
                && status.debugRendererIsolationOk()
                && !status.formalDrawPipelineReady()
                && !status.formalRendererReady()
                && !status.actualRendererDrawEnabled()
                && status.noDraw()
                && status.p0BlockerCount() >= 1
                && audit.success()
                && !rendererStatus.formalRendererReady()
                && !rendererStatus.actualDrawEnabled() ? 1 : 0;
    }

    private static int formalRendererCheck(CommandSourceStack source) {
        ForgeFormalRendererStats status = ForgeVoxyInstance.INSTANCE.getFormalRendererManager().checkReadiness("command-check");
        source.sendSuccess(() -> Component.literal("Voxy formal renderer check: " + formatFormalRendererStatus(status)), false);
        return status.formalRendererSkeletonReady() ? 1 : 0;
    }

    private static int formalRendererStatus(CommandSourceStack source) {
        ForgeFormalRendererStats status = ForgeVoxyInstance.INSTANCE.getFormalRendererManager().createStatusSnapshot();
        source.sendSuccess(() -> Component.literal("Voxy formal renderer status: " + formatFormalRendererStatus(status)), false);
        return status.formalRendererSkeletonReady() || status.formalRendererStale() ? 1 : 0;
    }

    private static int formalRendererEnable(CommandSourceStack source) {
        ForgeFormalRendererStats status = ForgeVoxyInstance.INSTANCE.getFormalRendererManager().enable("command-enable");
        source.sendSuccess(() -> Component.literal("Voxy formal renderer enable: " + formatFormalRendererStatus(status) + " No formal draw was started."), false);
        return status.enabled() && status.noDraw() && !status.actualDrawEnabled() ? 1 : 0;
    }

    private static int formalRendererDisable(CommandSourceStack source) {
        ForgeFormalRendererStats status = ForgeVoxyInstance.INSTANCE.getFormalRendererManager().disable("command-disable");
        source.sendSuccess(() -> Component.literal("Voxy formal renderer disable: " + formatFormalRendererStatus(status)), false);
        return 1;
    }

    private static int formalRendererClear(CommandSourceStack source) {
        ForgeFormalRendererStats status = ForgeVoxyInstance.INSTANCE.getFormalRendererManager().clear("command-clear");
        source.sendSuccess(() -> Component.literal("Voxy formal renderer clear: " + formatFormalRendererStatus(status) + " Only the H2 formal renderer skeleton status/lifecycle state was cleared; GL heap, MDIC command buffer, existing MDIC debug renderer, textured MDIC debug renderer, simple renderer, model bridge samples, and atlas samples were left unchanged."), false);
        return 1;
    }

    private static int formalRendererDumpBlockers(CommandSourceStack source) {
        ForgeFormalRendererStats status = ForgeVoxyInstance.INSTANCE.getFormalRendererManager().checkReadiness("command-dump-blockers");
        String blockers = ForgeVoxyInstance.INSTANCE.getFormalRendererManager().dumpBlockers();
        source.sendSuccess(() -> Component.literal("Voxy formal renderer blockers: " + blockers + " " + formatFormalRendererStatus(status)), false);
        return status.blockerCount() > 0 ? 1 : 0;
    }

    private static int texturedDebugQuadBuildSample(CommandSourceStack source) {
        ForgeTexturedDebugStats status = ForgeVoxyInstance.INSTANCE.getTexturedDebugQuadRenderer().buildSample();
        source.sendSuccess(() -> Component.literal("Voxy textured debug quad build sample: " + formatTexturedDebugQuadStatus(status)), false);
        return status.sampleReady() && status.atlasTextureReady() && status.atlasPixelsUploaded() ? 1 : 0;
    }

    private static int texturedDebugQuadEnable(CommandSourceStack source) {
        ForgeVoxyInstance.INSTANCE.getTexturedDebugQuadRenderer().enable();
        ForgeTexturedDebugStats status = ForgeVoxyInstance.INSTANCE.getTexturedDebugQuadRenderer().createStatusSnapshot();
        source.sendSuccess(() -> Component.literal("Voxy textured debug quad enable: " + formatTexturedDebugQuadStatus(status)), false);
        return status.enabled() ? 1 : 0;
    }

    private static int texturedDebugQuadDisable(CommandSourceStack source) {
        ForgeVoxyInstance.INSTANCE.getTexturedDebugQuadRenderer().disable();
        ForgeTexturedDebugStats status = ForgeVoxyInstance.INSTANCE.getTexturedDebugQuadRenderer().createStatusSnapshot();
        source.sendSuccess(() -> Component.literal("Voxy textured debug quad disable: " + formatTexturedDebugQuadStatus(status)), false);
        return 1;
    }

    private static int texturedDebugQuadStatus(CommandSourceStack source) {
        ForgeTexturedDebugStats status = ForgeVoxyInstance.INSTANCE.getTexturedDebugQuadRenderer().createStatusSnapshot();
        source.sendSuccess(() -> Component.literal("Voxy textured debug quad status: " + formatTexturedDebugQuadStatus(status)), false);
        return status.actualDrawEnabled() || status.texturedDebugQuadStale() ? 1 : 0;
    }

    private static int texturedDebugQuadClear(CommandSourceStack source) {
        ForgeVoxyInstance.INSTANCE.getTexturedDebugQuadRenderer().clear();
        ForgeTexturedDebugStats status = ForgeVoxyInstance.INSTANCE.getTexturedDebugQuadRenderer().createStatusSnapshot();
        source.sendSuccess(() -> Component.literal("Voxy textured debug quad clear: " + formatTexturedDebugQuadStatus(status) + " Atlas upload, MDIC debug renderer, GL geometry heap, simple renderer, and CPU caches were left unchanged."), false);
        return 1;
    }

    private static int texturedReadbackBuildSample(CommandSourceStack source) {
        ForgeTexturedReadbackStats status = ForgeVoxyInstance.INSTANCE.getTexturedReadbackRenderer().buildSample();
        source.sendSuccess(() -> Component.literal("Voxy textured readback build sample: " + formatTexturedReadbackStatus(status)), false);
        return status.sampleReady() && status.atlasTextureReady() && status.atlasPixelsUploaded() && status.builtQuads() > 0 ? 1 : 0;
    }

    private static int texturedReadbackEnable(CommandSourceStack source) {
        ForgeVoxyInstance.INSTANCE.getTexturedReadbackRenderer().enable();
        ForgeTexturedReadbackStats status = ForgeVoxyInstance.INSTANCE.getTexturedReadbackRenderer().createStatusSnapshot();
        source.sendSuccess(() -> Component.literal("Voxy textured readback enable: " + formatTexturedReadbackStatus(status)), false);
        return status.enabled() ? 1 : 0;
    }

    private static int texturedReadbackDisable(CommandSourceStack source) {
        ForgeVoxyInstance.INSTANCE.getTexturedReadbackRenderer().disable();
        ForgeTexturedReadbackStats status = ForgeVoxyInstance.INSTANCE.getTexturedReadbackRenderer().createStatusSnapshot();
        source.sendSuccess(() -> Component.literal("Voxy textured readback disable: " + formatTexturedReadbackStatus(status)), false);
        return 1;
    }

    private static int texturedReadbackStatus(CommandSourceStack source) {
        ForgeTexturedReadbackStats status = ForgeVoxyInstance.INSTANCE.getTexturedReadbackRenderer().createStatusSnapshot();
        source.sendSuccess(() -> Component.literal("Voxy textured readback status: " + formatTexturedReadbackStatus(status)), false);
        return status.actualDrawEnabled() || status.texturedReadbackStale() ? 1 : 0;
    }

    private static int texturedReadbackClear(CommandSourceStack source) {
        ForgeVoxyInstance.INSTANCE.getTexturedReadbackRenderer().clear();
        ForgeTexturedReadbackStats status = ForgeVoxyInstance.INSTANCE.getTexturedReadbackRenderer().createStatusSnapshot();
        source.sendSuccess(() -> Component.literal("Voxy textured readback clear: " + formatTexturedReadbackStatus(status) + " Atlas upload, MDIC debug renderer, GL geometry heap, simple renderer, and CPU caches were left unchanged."), false);
        return 1;
    }

    private static int texturedMdicDebugBuild(CommandSourceStack source) {
        ForgeTexturedMdicDebugStats status = ForgeVoxyInstance.INSTANCE.getTexturedMdicDebugRenderer().build();
        source.sendSuccess(() -> Component.literal("Voxy textured MDIC debug build: " + formatTexturedMdicDebugStatus(status)), false);
        return status.sampleReady() && status.atlasTextureReady() && status.atlasPixelsUploaded() && status.mdicCommandReady() ? 1 : 0;
    }

    private static int texturedMdicDebugInputMode(CommandSourceStack source, ForgeTexturedMdicDebugInputMode mode) {
        ForgeVoxyInstance.INSTANCE.getTexturedMdicDebugRenderer().setInputMode(mode);
        ForgeTexturedMdicDebugStats status = ForgeVoxyInstance.INSTANCE.getTexturedMdicDebugRenderer().createStatusSnapshot();
        source.sendSuccess(() -> Component.literal("Voxy textured MDIC debug input mode: " + formatTexturedMdicDebugStatus(status)), false);
        return 1;
    }

    private static int texturedMdicDebugEnable(CommandSourceStack source) {
        ForgeVoxyInstance.INSTANCE.getTexturedMdicDebugRenderer().enable();
        ForgeTexturedMdicDebugStats status = ForgeVoxyInstance.INSTANCE.getTexturedMdicDebugRenderer().createStatusSnapshot();
        source.sendSuccess(() -> Component.literal("Voxy textured MDIC debug enable: " + formatTexturedMdicDebugStatus(status)), false);
        return status.enabled() ? 1 : 0;
    }

    private static int texturedMdicDebugDisable(CommandSourceStack source) {
        ForgeVoxyInstance.INSTANCE.getTexturedMdicDebugRenderer().disable();
        ForgeTexturedMdicDebugStats status = ForgeVoxyInstance.INSTANCE.getTexturedMdicDebugRenderer().createStatusSnapshot();
        source.sendSuccess(() -> Component.literal("Voxy textured MDIC debug disable: " + formatTexturedMdicDebugStatus(status)), false);
        return 1;
    }

    private static int texturedMdicDebugStatus(CommandSourceStack source) {
        ForgeTexturedMdicDebugStats status = ForgeVoxyInstance.INSTANCE.getTexturedMdicDebugRenderer().createStatusSnapshot();
        source.sendSuccess(() -> Component.literal("Voxy textured MDIC debug status: " + formatTexturedMdicDebugStatus(status)), false);
        return status.actualDrawEnabled() || status.texturedMdicDebugStale() ? 1 : 0;
    }

    private static int texturedMdicDebugClear(CommandSourceStack source) {
        ForgeVoxyInstance.INSTANCE.getTexturedMdicDebugRenderer().clear();
        ForgeTexturedMdicDebugStats status = ForgeVoxyInstance.INSTANCE.getTexturedMdicDebugRenderer().createStatusSnapshot();
        source.sendSuccess(() -> Component.literal("Voxy textured MDIC debug clear: " + formatTexturedMdicDebugStatus(status) + " Atlas upload, existing MDIC debug renderer, GL geometry heap, simple renderer, and CPU caches were left unchanged."), false);
        return 1;
    }

    private static String modelStoreFormalLayoutStatusSuffix() {
        return String.format(
                " placeholderLayoutVersion=%s formalModelLayoutVersion=%s formalModelRecordBytes=%d formalLayoutKnown=%s knownFieldCount=%d unknownFieldCount=%d faceDataLayoutKnown=%s flagsLayoutKnown=%s colourTintLayoutKnown=%s customIdLayoutKnown=%s atlasUvLayoutKnown=%s materialLayoutKnown=%s fieldMappingReady=%s faceDataMappingReady=%s atlasUvMappingReady=%s materialMappingReady=%s formalLayoutCompatible=false formalModelBridgeReady=false",
                ForgeModelStoreLayout.LAYOUT_VERSION,
                ForgeModelStoreFormalLayout.LAYOUT_VERSION,
                ForgeModelStoreFormalLayout.MODEL_RECORD_BYTES,
                ForgeModelStoreFormalLayout.FORMAL_LAYOUT_KNOWN,
                ForgeModelStoreFormalLayout.knownFieldCount(),
                ForgeModelStoreFormalLayout.unknownFieldCount(),
                ForgeModelStoreFormalLayout.FACE_DATA_LAYOUT_KNOWN,
                ForgeModelStoreFormalLayout.FLAGS_LAYOUT_KNOWN,
                ForgeModelStoreFormalLayout.COLOUR_TINT_LAYOUT_KNOWN,
                ForgeModelStoreFormalLayout.CUSTOM_ID_LAYOUT_KNOWN,
                ForgeModelStoreFormalLayout.ATLAS_UV_LAYOUT_KNOWN,
                ForgeModelStoreFormalLayout.MATERIAL_LAYOUT_KNOWN,
                ForgeModelStoreFormalLayout.FIELD_MAPPING_READY,
                ForgeModelStoreFormalLayout.FACE_DATA_MAPPING_READY,
                ForgeModelStoreFormalLayout.ATLAS_UV_MAPPING_READY,
                ForgeModelStoreFormalLayout.MATERIAL_MAPPING_READY
        );
    }

    private static String bakedModelBridgeStatusSuffix() {
        return " " + formatBakedModelBridgeStatus(ForgeVoxyInstance.INSTANCE.getBakedModelBridge().createStatusSnapshot());
    }

    private static String realModelStoreSampleStatusSuffix() {
        return " " + formatRealModelStoreSampleStatus(ForgeVoxyInstance.INSTANCE.getRealModelStoreSample().createStatusSnapshot());
    }

    private static String modelSampleSetStatusSuffix() {
        return " " + formatModelSampleSetStatus(ForgeVoxyInstance.INSTANCE.getModelSampleSet().createStatusSnapshot());
    }

    private static String modelAtlasSkeletonStatusSuffix() {
        return " " + formatModelAtlasSkeletonStatus(ForgeVoxyInstance.INSTANCE.getModelAtlasSkeleton().createStatusSnapshot());
    }

    private static String modelAtlasUploadStatusSuffix() {
        return " " + formatModelAtlasUploadStatus(ForgeVoxyInstance.INSTANCE.getModelAtlasPixelUploader().createStatusSnapshot());
    }

    private static String modelAtlasSampleSetUploadStatusSuffix() {
        return " " + formatModelAtlasSampleSetUploadStatus(ForgeVoxyInstance.INSTANCE.getModelAtlasSampleSetUploader().createStatusSnapshot());
    }

    private static String formalShaderInputBridgeStatusSuffix() {
        return " " + formatFormalShaderInputBridgeStatus(ForgeVoxyInstance.INSTANCE.getFormalShaderInputBridge().createStatusSnapshot());
    }

    private static String formalModelStoreStatusSuffix() {
        return " " + formatFormalModelStoreStatus(ForgeVoxyInstance.INSTANCE.getFormalModelStore().createStatusSnapshot());
    }

    private static String formalModelFactoryStatusSuffix() {
        return " " + formatFormalModelFactoryStatus(ForgeVoxyInstance.INSTANCE.getFormalModelFactory().createStatusSnapshot());
    }

    private static String formalRendererStatusSuffix() {
        return " " + formatFormalRendererStatus(ForgeVoxyInstance.INSTANCE.getFormalRendererManager().createStatusSnapshot());
    }

    private static String formatFormalModelStoreStatus(ForgeFormalModelStoreStats status) {
        return String.format(
                "stage=%s buildRuns=%d clearRuns=%d auditRuns=%d auditFailures=%d lastBuildError=%s lastAllocationError=%s lastBuildDurationMs=%.2f formalModelStoreSkeletonReady=%s formalModelStoreOwnerReady=%s formalModelStoreReady=%s noBake=%s noDraw=%s enabled=%s lifecycleState=%s formalLayoutKnown=%s modelSize=%d modelCapacity=%d modelDataBufferCreated=%s modelDataBufferId=%d modelDataBufferBytes=%d modelColourBufferCreated=%s modelColourBufferId=%d modelColourBufferBytes=%d atlasLayoutReady=%s atlasTextureCreated=%s atlasTextureId=%d fullAtlasTextureCreated=%s debugSmallAtlasFallback=%s allocationFailed=%s atlasWidth=%d atlasHeight=%d actualAtlasWidth=%d actualAtlasHeight=%d atlasFormat=%s atlasPixelsUploaded=%s samplerCreated=%s samplerId=%d samplerConfigured=%s resourceReloadAware=%s stale=%s requiresRebuild=%s realModelFactoryReady=%s realModelBakeryReady=%s realModelRecordsUploaded=%s realAtlasPixelsUploaded=%s formalShaderBound=%s formalRendererReady=%s resourceReloadSeen=%s worldUnloadSeen=%s dimensionSwitchSeen=%s debugPipelineClearSeen=%s presetOffSeen=%s presetClearSeen=%s lastLifecycleEvent=%s staleReason=%s cleanupScheduled=%s cleanupOnRenderThread=%s cleanupCompleted=%s cleanupFailures=%d lastAuditOk=%s lastAuditError=%s invalidLayout=%d invalidBufferSize=%d invalidAtlasState=%d unexpectedRecordsUploaded=%s unexpectedPixelsUploaded=%s renderer=none draw=false formalShader=false sampleSet=false",
                status.stage(),
                status.buildRuns(),
                status.clearRuns(),
                status.auditRuns(),
                status.auditFailures(),
                status.lastBuildError(),
                status.lastAllocationError(),
                status.lastBuildDurationMs(),
                status.formalModelStoreSkeletonReady(),
                status.formalModelStoreOwnerReady(),
                status.formalModelStoreReady(),
                status.noBake(),
                status.noDraw(),
                status.enabled(),
                status.lifecycleState(),
                status.formalLayoutKnown(),
                status.modelSize(),
                status.modelCapacity(),
                status.modelDataBufferCreated(),
                status.modelDataBufferId(),
                status.modelDataBufferBytes(),
                status.modelColourBufferCreated(),
                status.modelColourBufferId(),
                status.modelColourBufferBytes(),
                status.atlasLayoutReady(),
                status.atlasTextureCreated(),
                status.atlasTextureId(),
                status.fullAtlasTextureCreated(),
                status.debugSmallAtlasFallback(),
                status.allocationFailed(),
                status.atlasWidth(),
                status.atlasHeight(),
                status.actualAtlasWidth(),
                status.actualAtlasHeight(),
                status.atlasFormat(),
                status.atlasPixelsUploaded(),
                status.samplerCreated(),
                status.samplerId(),
                status.samplerConfigured(),
                status.resourceReloadAware(),
                status.stale(),
                status.requiresRebuild(),
                status.realModelFactoryReady(),
                status.realModelBakeryReady(),
                status.realModelRecordsUploaded(),
                status.realAtlasPixelsUploaded(),
                status.formalShaderBound(),
                status.formalRendererReady(),
                status.resourceReloadSeen(),
                status.worldUnloadSeen(),
                status.dimensionSwitchSeen(),
                status.debugPipelineClearSeen(),
                status.presetOffSeen(),
                status.presetClearSeen(),
                status.lastLifecycleEvent(),
                status.staleReason(),
                status.cleanupScheduled(),
                status.cleanupOnRenderThread(),
                status.cleanupCompleted(),
                status.cleanupFailures(),
                status.lastAuditOk(),
                status.lastAuditError(),
                status.invalidLayout(),
                status.invalidBufferSize(),
                status.invalidAtlasState(),
                status.unexpectedRecordsUploaded(),
                status.unexpectedPixelsUploaded()
        );
    }

    private static String formatFormalModelStoreAudit(ForgeFormalModelStoreAuditResult audit) {
        return String.format(
                "success=%s error=%s durationMs=%.2f invalidLayout=%d invalidBufferSize=%d invalidAtlasState=%d unexpectedRecordsUploaded=%s unexpectedPixelsUploaded=%s modelDataZeroSample=%s modelColourZeroSample=%s readbackApi=glGetNamedBufferSubData atlasReadback=skipped noBake=true noDraw=true formalModelStoreReady=false",
                audit.success(),
                audit.error(),
                audit.durationMs(),
                audit.invalidLayout(),
                audit.invalidBufferSize(),
                audit.invalidAtlasState(),
                audit.unexpectedRecordsUploaded(),
                audit.unexpectedPixelsUploaded(),
                audit.modelDataZeroSample(),
                audit.modelColourZeroSample()
        );
    }

    private static String formatFormalModelFactoryStatus(ForgeFormalModelFactoryStats status) {
        return String.format(
                "stage=%s requestRuns=%d processRuns=%d clearRuns=%d auditRuns=%d auditFailures=%d lastProcessError=%s lastProcessDurationMs=%.2f formalModelFactorySkeletonReady=%s formalModelFactoryLifecycleReady=%s formalModelFactoryReady=%s noBake=%s noUpload=%s noDraw=%s enabled=%s lifecycleState=%s requestedCount=%d seenBlockStateCount=%d pendingCount=%d inFlightCount=%d completedSkeletonCount=%d failedCount=%d formalModelIdsAssigned=%s formalModelIdsBackedByRealBake=%s nextFormalModelId=%d idMappingsSize=%d metadataCacheSize=%d fluidStateLutSize=%d modelTexture2idSize=%d formalModelStoreOwnerReady=%s formalModelStoreReady=%s realModelRecordsUploaded=%s realAtlasPixelsUploaded=%s usesPlaceholderModelIds=%s usesFormalModelIds=%s sampleSetModelIdsUsed=%s resourceReloadAware=%s stale=%s requiresRebuild=%s lastRequestBlockStateId=%d lastAssignedFormalModelId=%d lastFailureReason=%s resourceReloadSeen=%s worldUnloadSeen=%s dimensionSwitchSeen=%s debugPipelineClearSeen=%s presetOffSeen=%s presetClearSeen=%s lastLifecycleEvent=%s staleReason=%s lastAuditOk=%s lastAuditError=%s duplicateMappings=%d invalidFormalModelIds=%d unexpectedRealBake=%s unexpectedUpload=%s sampleSetMisuse=%s renderer=none draw=false bake=false upload=false formalShader=false sampleSet=false",
                status.stage(),
                status.requestRuns(),
                status.processRuns(),
                status.clearRuns(),
                status.auditRuns(),
                status.auditFailures(),
                status.lastProcessError(),
                status.lastProcessDurationMs(),
                status.formalModelFactorySkeletonReady(),
                status.formalModelFactoryLifecycleReady(),
                status.formalModelFactoryReady(),
                status.noBake(),
                status.noUpload(),
                status.noDraw(),
                status.enabled(),
                status.lifecycleState(),
                status.requestedCount(),
                status.seenBlockStateCount(),
                status.pendingCount(),
                status.inFlightCount(),
                status.completedSkeletonCount(),
                status.failedCount(),
                status.formalModelIdsAssigned(),
                status.formalModelIdsBackedByRealBake(),
                status.nextFormalModelId(),
                status.idMappingsSize(),
                status.metadataCacheSize(),
                status.fluidStateLutSize(),
                status.modelTexture2idSize(),
                status.formalModelStoreOwnerReady(),
                status.formalModelStoreReady(),
                status.realModelRecordsUploaded(),
                status.realAtlasPixelsUploaded(),
                status.usesPlaceholderModelIds(),
                status.usesFormalModelIds(),
                status.sampleSetModelIdsUsed(),
                status.resourceReloadAware(),
                status.stale(),
                status.requiresRebuild(),
                status.lastRequestBlockStateId(),
                status.lastAssignedFormalModelId(),
                status.lastFailureReason(),
                status.resourceReloadSeen(),
                status.worldUnloadSeen(),
                status.dimensionSwitchSeen(),
                status.debugPipelineClearSeen(),
                status.presetOffSeen(),
                status.presetClearSeen(),
                status.lastLifecycleEvent(),
                status.staleReason(),
                status.lastAuditOk(),
                status.lastAuditError(),
                status.duplicateMappings(),
                status.invalidFormalModelIds(),
                status.unexpectedRealBake(),
                status.unexpectedUpload(),
                status.sampleSetMisuse()
        );
    }

    private static String formatFormalModelFactoryAudit(ForgeFormalModelFactoryAuditResult audit) {
        return String.format(
                "success=%s error=%s durationMs=%.2f duplicateMappings=%d invalidFormalModelIds=%d unexpectedRealBake=%s unexpectedUpload=%s sampleSetMisuse=%s noBake=true noUpload=true noDraw=true formalModelFactoryReady=false",
                audit.success(),
                audit.error(),
                audit.durationMs(),
                audit.duplicateMappings(),
                audit.invalidFormalModelIds(),
                audit.unexpectedRealBake(),
                audit.unexpectedUpload(),
                audit.sampleSetMisuse()
        );
    }

    private static String formatOneBlockFormalBakeUploadStatus(ForgeOneBlockFormalBakeUploadStats status) {
        return String.format(
                "stage=%s buildRuns=%d auditRuns=%d clearRuns=%d uploadFailures=%d oneBlockBakePrototypeReady=%s oneBlockRealBakeReady=%s oneBlockFormalUploadReady=%s oneBlockFormalUploadAuditReady=%s oneBlockFormalModelRecordUploaded=%s oneBlockFormalAtlasPixelsUploaded=%s oneBlockFormalUploadAuditOk=%s sourceBlockStateId=%d sourceBlockState=\"%s\" sourceRenderLayer=%s sourceBakedModelClass=%s sourcePrimarySprite=%s sourcePrimarySpriteAtlas=%s sourceTinted=%s sourceFluid=%s formalModelId=%d usesFormalModelIds=%s usesPlaceholderModelIds=%s sampleSetModelIdsUsed=%s formalModelIdsBackedByRealBake=%s modelRecordBytes=%d modelRecordUploaded=%s modelDataWriteOffset=%d modelColourUploaded=%s modelColourWriteOffset=%d colourTint=%d flagsA=%d faceDataWrittenCount=%d missingFaceCount=%d fallbackFaceCount=%d atlasPixelsUploaded=%s uploadedFaceTiles=%d uploadedPixels=%d atlasBaseX=%d atlasBaseY=%d atlasReadbackOk=%s atlasPixelMismatches=%d faceChecksums=%s modelDataReadbackOk=%s modelColourReadbackOk=%s formalModelStoreOwnerReady=%s realModelFactoryReady=%s realModelBakeryReady=%s realModelStoreReady=%s formalRendererReady=%s actualDrawEnabled=%s noDraw=%s noShaderBind=%s stale=%s requiresRebuild=%s lifecycleState=%s lastLifecycleEvent=%s staleReason=%s lastFailureReason=%s resourceReloadSeen=%s worldUnloadSeen=%s dimensionSwitchSeen=%s debugPipelineClearSeen=%s presetOffSeen=%s presetClearSeen=%s lastAuditOk=%s lastAuditError=%s lastAuditDurationMs=%.2f renderer=none draw=false formalShader=false MDICSectionRenderer=false VoxyRenderSystem=false",
                status.stage(),
                status.buildRuns(),
                status.auditRuns(),
                status.clearRuns(),
                status.uploadFailures(),
                status.oneBlockBakePrototypeReady(),
                status.oneBlockRealBakeReady(),
                status.oneBlockFormalUploadReady(),
                status.oneBlockFormalUploadAuditReady(),
                status.oneBlockFormalModelRecordUploaded(),
                status.oneBlockFormalAtlasPixelsUploaded(),
                status.oneBlockFormalUploadAuditOk(),
                status.sourceBlockStateId(),
                status.sourceBlockState(),
                status.sourceRenderLayer(),
                status.sourceBakedModelClass(),
                status.sourcePrimarySprite(),
                status.sourcePrimarySpriteAtlas(),
                status.sourceTinted(),
                status.sourceFluid(),
                status.formalModelId(),
                status.usesFormalModelIds(),
                status.usesPlaceholderModelIds(),
                status.sampleSetModelIdsUsed(),
                status.formalModelIdsBackedByRealBake(),
                status.modelRecordBytes(),
                status.modelRecordUploaded(),
                status.modelDataWriteOffset(),
                status.modelColourUploaded(),
                status.modelColourWriteOffset(),
                status.colourTint(),
                status.flagsA(),
                status.faceDataWrittenCount(),
                status.missingFaceCount(),
                status.fallbackFaceCount(),
                status.atlasPixelsUploaded(),
                status.uploadedFaceTiles(),
                status.uploadedPixels(),
                status.atlasBaseX(),
                status.atlasBaseY(),
                status.atlasReadbackOk(),
                status.atlasPixelMismatches(),
                status.faceChecksums(),
                status.modelDataReadbackOk(),
                status.modelColourReadbackOk(),
                status.formalModelStoreOwnerReady(),
                status.realModelFactoryReady(),
                status.realModelBakeryReady(),
                status.realModelStoreReady(),
                status.formalRendererReady(),
                status.actualDrawEnabled(),
                status.noDraw(),
                status.noShaderBind(),
                status.stale(),
                status.requiresRebuild(),
                status.lifecycleState(),
                status.lastLifecycleEvent(),
                status.staleReason(),
                status.lastFailureReason(),
                status.resourceReloadSeen(),
                status.worldUnloadSeen(),
                status.dimensionSwitchSeen(),
                status.debugPipelineClearSeen(),
                status.presetOffSeen(),
                status.presetClearSeen(),
                status.lastAuditOk(),
                status.lastAuditError(),
                status.lastAuditDurationMs()
        );
    }

    private static String formatOneBlockFormalBakeUploadAudit(ForgeOneBlockFormalBakeUploadAuditResult audit) {
        return String.format(
                "auditSuccess=%s auditError=%s auditDurationMs=%.2f validFormalModelId=%s mappingBelongsToI3=%s placeholderIdUsed=%s sampleSetIdUsed=%s modelRecordBytesOk=%s modelDataReadbackOk=%s modelColourReadbackOk=%s atlasReadbackOk=%s atlasPixelMismatches=%d shaderBound=%s drawOccurred=%s formalRendererReady=%s actualDrawEnabled=%s",
                audit.success(),
                audit.error(),
                audit.durationMs(),
                audit.validFormalModelId(),
                audit.mappingBelongsToI3(),
                audit.placeholderIdUsed(),
                audit.sampleSetIdUsed(),
                audit.modelRecordBytesOk(),
                audit.modelDataReadbackOk(),
                audit.modelColourReadbackOk(),
                audit.atlasReadbackOk(),
                audit.atlasPixelMismatches(),
                audit.shaderBound(),
                audit.drawOccurred(),
                audit.formalRendererReady(),
                audit.actualDrawEnabled()
        );
    }

    private static String formatMultiBlockFormalBakeUploadStatus(ForgeMultiBlockFormalBakeUploadStats status) {
        return String.format(
                "stage=%s buildRuns=%d auditRuns=%d clearRuns=%d uploadFailures=%d multiBlockBakePrototypeReady=%s multiBlockFormalBakeReady=%s multiBlockFormalUploadReady=%s multiBlockFormalUploadAuditReady=%s multiBlockFormalRecordsUploaded=%s multiBlockFormalAtlasPixelsUploaded=%s multiBlockFormalUploadAuditOk=%s requestedBlockStateCount=%d acceptedBlockStateCount=%d rejectedBlockStateCount=%d uniqueFormalModelCount=%d modelTexture2idSize=%d dedupedModelCount=%d dedupeHitCount=%d dedupeMissCount=%d uploadedModelRecordCount=%d uploadedModelColourCount=%d uploadedAtlasModelCount=%d uploadedFaceTileCount=%d uploadedPixels=%d atlasPixelMismatches=%d modelDataReadbackOk=%s modelColourReadbackOk=%s atlasReadbackOk=%s unsupportedAirCount=%d unsupportedFluidCount=%d unsupportedTranslucentCount=%d unsupportedCutoutCount=%d unsupportedTintedCount=%d unsupportedAnimatedCount=%d unsupportedMissingSpriteCount=%d unsupportedNoModelCount=%d unsupportedNoQuadsCount=%d unsupportedRenderLayerCount=%d usesFormalModelIds=%s usesPlaceholderModelIds=%s sampleSetModelIdsUsed=%s basicModelDedupeReady=%s unsupportedPolicyReady=%s reloadRebuildReady=%s realModelFactoryReady=%s realModelBakeryReady=%s realModelStoreReady=%s formalTexturedShaderReady=%s formalRendererReady=%s actualDrawEnabled=%s noDraw=%s noShaderBind=%s stale=%s requiresRebuild=%s lifecycleState=%s lastLifecycleEvent=%s staleReason=%s lastFailureReason=%s acceptedBlockStates=\"%s\" acceptedFormalModelIds=%s rejectedBlockStates=\"%s\" faceChecksums=%s formalModelStoreOwnerReady=%s resourceReloadSeen=%s worldUnloadSeen=%s dimensionSwitchSeen=%s debugPipelineClearSeen=%s presetOffSeen=%s presetClearSeen=%s lastAuditOk=%s lastAuditError=%s lastAuditDurationMs=%.2f renderer=none draw=false formalShader=false MDICSectionRenderer=false VoxyRenderSystem=false",
                status.stage(),
                status.buildRuns(),
                status.auditRuns(),
                status.clearRuns(),
                status.uploadFailures(),
                status.multiBlockBakePrototypeReady(),
                status.multiBlockFormalBakeReady(),
                status.multiBlockFormalUploadReady(),
                status.multiBlockFormalUploadAuditReady(),
                status.multiBlockFormalRecordsUploaded(),
                status.multiBlockFormalAtlasPixelsUploaded(),
                status.multiBlockFormalUploadAuditOk(),
                status.requestedBlockStateCount(),
                status.acceptedBlockStateCount(),
                status.rejectedBlockStateCount(),
                status.uniqueFormalModelCount(),
                status.modelTexture2idSize(),
                status.dedupedModelCount(),
                status.dedupeHitCount(),
                status.dedupeMissCount(),
                status.uploadedModelRecordCount(),
                status.uploadedModelColourCount(),
                status.uploadedAtlasModelCount(),
                status.uploadedFaceTileCount(),
                status.uploadedPixels(),
                status.atlasPixelMismatches(),
                status.modelDataReadbackOk(),
                status.modelColourReadbackOk(),
                status.atlasReadbackOk(),
                status.unsupportedAirCount(),
                status.unsupportedFluidCount(),
                status.unsupportedTranslucentCount(),
                status.unsupportedCutoutCount(),
                status.unsupportedTintedCount(),
                status.unsupportedAnimatedCount(),
                status.unsupportedMissingSpriteCount(),
                status.unsupportedNoModelCount(),
                status.unsupportedNoQuadsCount(),
                status.unsupportedRenderLayerCount(),
                status.usesFormalModelIds(),
                status.usesPlaceholderModelIds(),
                status.sampleSetModelIdsUsed(),
                status.basicModelDedupeReady(),
                status.unsupportedPolicyReady(),
                status.reloadRebuildReady(),
                status.realModelFactoryReady(),
                status.realModelBakeryReady(),
                status.realModelStoreReady(),
                status.formalTexturedShaderReady(),
                status.formalRendererReady(),
                status.actualDrawEnabled(),
                status.noDraw(),
                status.noShaderBind(),
                status.stale(),
                status.requiresRebuild(),
                status.lifecycleState(),
                status.lastLifecycleEvent(),
                status.staleReason(),
                status.lastFailureReason(),
                status.acceptedBlockStates(),
                status.acceptedFormalModelIds(),
                status.rejectedBlockStates(),
                status.faceChecksums(),
                status.formalModelStoreOwnerReady(),
                status.resourceReloadSeen(),
                status.worldUnloadSeen(),
                status.dimensionSwitchSeen(),
                status.debugPipelineClearSeen(),
                status.presetOffSeen(),
                status.presetClearSeen(),
                status.lastAuditOk(),
                status.lastAuditError(),
                status.lastAuditDurationMs()
        );
    }

    private static String formatMultiBlockFormalBakeUploadAudit(ForgeMultiBlockFormalBakeUploadAuditResult audit) {
        return String.format(
                "auditSuccess=%s auditError=%s auditDurationMs=%.2f allAcceptedHaveFormalModelIds=%s placeholderIdUsed=%s sampleSetIdUsed=%s allModelRecordBytesOk=%s modelDataReadbackOk=%s modelColourReadbackOk=%s atlasReadbackOk=%s atlasPixelMismatches=%d dedupeConsistent=%s unsupportedPolicyReady=%s shaderBound=%s drawOccurred=%s formalRendererReady=%s actualDrawEnabled=%s",
                audit.success(),
                audit.error(),
                audit.durationMs(),
                audit.allAcceptedHaveFormalModelIds(),
                audit.placeholderIdUsed(),
                audit.sampleSetIdUsed(),
                audit.allModelRecordBytesOk(),
                audit.modelDataReadbackOk(),
                audit.modelColourReadbackOk(),
                audit.atlasReadbackOk(),
                audit.atlasPixelMismatches(),
                audit.dedupeConsistent(),
                audit.unsupportedPolicyReady(),
                audit.shaderBound(),
                audit.drawOccurred(),
                audit.formalRendererReady(),
                audit.actualDrawEnabled()
        );
    }

    private static String formatFormalModelBakeryLifecycleStatus(ForgeFormalModelBakeryLifecycleStats status) {
        return String.format(
                "stage=%s rebuildRuns=%d auditRuns=%d clearRuns=%d formalModelBakeryLifecycleSkeletonReady=%s formalModelBakeryLifecycleReady=%s reloadRebuildPrototypeReady=%s aliasSafeDedupeReady=%s safeSetRequestedBlockStateCount=%d safeSetAcceptedBlockStateCount=%d safeSetUploadedModelCount=%d canonicalModelCount=%d dedupeAliasCount=%d dedupeHitCount=%d dedupeMissCount=%d aliasedBlockStateCount=%d illegalDuplicateMappingCount=%d resourceGeneration=%d modelLifecycleGeneration=%d uploadGeneration=%d lastRebuildReason=%s lastRebuildModelCount=%d lastRebuildAuditOk=%s stale=%s requiresRebuild=%s multiBlockFormalBakeReady=%s multiBlockFormalUploadReady=%s multiBlockFormalUploadAuditReady=%s modelDataReadbackOk=%s modelColourReadbackOk=%s atlasReadbackOk=%s atlasPixelMismatches=%d usesFormalModelIds=%s usesPlaceholderModelIds=%s sampleSetModelIdsUsed=%s realModelFactoryReady=%s realModelBakeryReady=%s realModelStoreReady=%s formalTexturedShaderReady=%s formalRendererReady=%s actualDrawEnabled=%s enabled=%s lifecycleState=%s lastLifecycleEvent=%s staleReason=%s lastFailureReason=%s resourceReloadSeen=%s worldUnloadSeen=%s dimensionSwitchSeen=%s debugPipelineClearSeen=%s presetOffSeen=%s presetClearSeen=%s lastAuditOk=%s lastAuditError=%s lastAuditDurationMs=%.2f canonicalMappings=\"%s\" aliasMappings=\"%s\" renderer=none draw=false formalShader=false MDICSectionRenderer=false VoxyRenderSystem=false",
                status.stage(),
                status.rebuildRuns(),
                status.auditRuns(),
                status.clearRuns(),
                status.formalModelBakeryLifecycleSkeletonReady(),
                status.formalModelBakeryLifecycleReady(),
                status.reloadRebuildPrototypeReady(),
                status.aliasSafeDedupeReady(),
                status.safeSetRequestedBlockStateCount(),
                status.safeSetAcceptedBlockStateCount(),
                status.safeSetUploadedModelCount(),
                status.canonicalModelCount(),
                status.dedupeAliasCount(),
                status.dedupeHitCount(),
                status.dedupeMissCount(),
                status.aliasedBlockStateCount(),
                status.illegalDuplicateMappingCount(),
                status.resourceGeneration(),
                status.modelLifecycleGeneration(),
                status.uploadGeneration(),
                status.lastRebuildReason(),
                status.lastRebuildModelCount(),
                status.lastRebuildAuditOk(),
                status.stale(),
                status.requiresRebuild(),
                status.multiBlockFormalBakeReady(),
                status.multiBlockFormalUploadReady(),
                status.multiBlockFormalUploadAuditReady(),
                status.modelDataReadbackOk(),
                status.modelColourReadbackOk(),
                status.atlasReadbackOk(),
                status.atlasPixelMismatches(),
                status.usesFormalModelIds(),
                status.usesPlaceholderModelIds(),
                status.sampleSetModelIdsUsed(),
                status.realModelFactoryReady(),
                status.realModelBakeryReady(),
                status.realModelStoreReady(),
                status.formalTexturedShaderReady(),
                status.formalRendererReady(),
                status.actualDrawEnabled(),
                status.enabled(),
                status.lifecycleState(),
                status.lastLifecycleEvent(),
                status.staleReason(),
                status.lastFailureReason(),
                status.resourceReloadSeen(),
                status.worldUnloadSeen(),
                status.dimensionSwitchSeen(),
                status.debugPipelineClearSeen(),
                status.presetOffSeen(),
                status.presetClearSeen(),
                status.lastAuditOk(),
                status.lastAuditError(),
                status.lastAuditDurationMs(),
                status.canonicalMappings(),
                status.aliasMappings()
        );
    }

    private static String formatFormalModelBakeryLifecycleAudit(ForgeFormalModelBakeryLifecycleAuditResult audit) {
        return String.format(
                "auditSuccess=%s auditError=%s auditDurationMs=%.2f formalModelStoreOwnerExists=%s formalModelFactoryLifecycleExists=%s multiBlockUploadConsistent=%s everyAcceptedBlockHasFormalModelId=%s placeholderIdUsed=%s sampleSetIdUsed=%s aliasMappingsValid=%s illegalDuplicateMappingCount=%d modelDataReadbackOk=%s modelColourReadbackOk=%s atlasReadbackOk=%s atlasPixelMismatches=%d generationCountersValid=%s shaderBound=%s drawOccurred=%s formalRendererReady=%s actualDrawEnabled=%s",
                audit.success(),
                audit.error(),
                audit.durationMs(),
                audit.formalModelStoreOwnerExists(),
                audit.formalModelFactoryLifecycleExists(),
                audit.multiBlockUploadConsistent(),
                audit.everyAcceptedBlockHasFormalModelId(),
                audit.placeholderIdUsed(),
                audit.sampleSetIdUsed(),
                audit.aliasMappingsValid(),
                audit.illegalDuplicateMappingCount(),
                audit.modelDataReadbackOk(),
                audit.modelColourReadbackOk(),
                audit.atlasReadbackOk(),
                audit.atlasPixelMismatches(),
                audit.generationCountersValid(),
                audit.shaderBound(),
                audit.drawOccurred(),
                audit.formalRendererReady(),
                audit.actualDrawEnabled()
        );
    }

    private static String formatFormalShaderInputConsumerStatus(ForgeFormalShaderInputConsumerStats status) {
        return String.format(
                "stage=%s buildRuns=%d auditRuns=%d clearRuns=%d auditFailures=%d formalShaderInputConsumerReady=%s formalShaderInputReady=%s formalShaderInputContractReady=%s formalShaderBound=%s actualDrawEnabled=%s noDraw=%s formalModelStoreOwnerReady=%s formalModelBakeryLifecycleSkeletonReady=%s reloadRebuildPrototypeReady=%s multiBlockFormalUploadAuditReady=%s modelDataBufferReady=%s modelDataBufferId=%d modelDataBufferBytes=%d modelColourBufferReady=%s modelColourBufferId=%d modelColourBufferBytes=%d atlasTextureReady=%s atlasTextureId=%d samplerReady=%s samplerId=%d bindingLayoutKnown=%s bindingLayoutCompatible=%s modelDataBindingIndex=%d modelColourBindingIndex=%d atlasTextureUnit=%d blockModelRecordSize=%d blockModelRecordLayoutCompatible=%s safeSetModelCount=%d safeSetModelIds=%s safeSetModelIdsAddressable=%s usesFormalModelIds=%s usesPlaceholderModelIds=%s sampleSetModelIdsUsed=%s sampleSetBridgeUsedAsFormalSource=%s shaderCompileAttempted=%s shaderCompileOk=%s programLinkOk=%s gpuValidationAttempted=%s gpuValidationOk=%s gpuValidationSkippedReason=\"%s\" bindingValidationAttempted=%s bindingValidationOk=%s lastGlError=%s formalTexturedShaderReady=%s formalRendererReady=%s stale=%s requiresRebuild=%s lifecycleState=%s lastLifecycleEvent=%s staleReason=%s lastFailureReason=%s lastAuditOk=%s lastAuditError=%s lastAuditDurationMs=%.2f renderer=none terrainDraw=false formalShader=false MDICSectionRenderer=false VoxyRenderSystem=false sampleSetBridgeFormalSource=false",
                status.stage(),
                status.buildRuns(),
                status.auditRuns(),
                status.clearRuns(),
                status.auditFailures(),
                status.formalShaderInputConsumerReady(),
                status.formalShaderInputReady(),
                status.formalShaderInputContractReady(),
                status.formalShaderBound(),
                status.actualDrawEnabled(),
                status.noDraw(),
                status.formalModelStoreOwnerReady(),
                status.formalModelBakeryLifecycleSkeletonReady(),
                status.reloadRebuildPrototypeReady(),
                status.multiBlockFormalUploadAuditReady(),
                status.modelDataBufferReady(),
                status.modelDataBufferId(),
                status.modelDataBufferBytes(),
                status.modelColourBufferReady(),
                status.modelColourBufferId(),
                status.modelColourBufferBytes(),
                status.atlasTextureReady(),
                status.atlasTextureId(),
                status.samplerReady(),
                status.samplerId(),
                status.bindingLayoutKnown(),
                status.bindingLayoutCompatible(),
                status.modelDataBindingIndex(),
                status.modelColourBindingIndex(),
                status.atlasTextureUnit(),
                status.blockModelRecordSize(),
                status.blockModelRecordLayoutCompatible(),
                status.safeSetModelCount(),
                status.safeSetModelIds(),
                status.safeSetModelIdsAddressable(),
                status.usesFormalModelIds(),
                status.usesPlaceholderModelIds(),
                status.sampleSetModelIdsUsed(),
                status.sampleSetBridgeUsedAsFormalSource(),
                status.shaderCompileAttempted(),
                status.shaderCompileOk(),
                status.programLinkOk(),
                status.gpuValidationAttempted(),
                status.gpuValidationOk(),
                status.gpuValidationSkippedReason(),
                status.bindingValidationAttempted(),
                status.bindingValidationOk(),
                status.lastGlError(),
                status.formalTexturedShaderReady(),
                status.formalRendererReady(),
                status.stale(),
                status.requiresRebuild(),
                status.lifecycleState(),
                status.lastLifecycleEvent(),
                status.staleReason(),
                status.lastFailureReason(),
                status.lastAuditOk(),
                status.lastAuditError(),
                status.lastAuditDurationMs()
        );
    }

    private static String formatFormalShaderInputConsumerAudit(ForgeFormalShaderInputConsumerAuditResult audit) {
        return String.format(
                "auditSuccess=%s auditError=%s auditDurationMs=%.2f formalLifecycleSafeSetExists=%s formalModelStoreResourcesReady=%s bindingLayoutKnown=%s bindingLayoutCompatible=%s blockModelRecordLayoutCompatible=%s safeSetModelIdsAddressable=%s modelIdsFormal=%s placeholderIdUsed=%s sampleSetIdUsed=%s sampleSetBridgeUsedAsFormalSource=%s bindingValidationOk=%s shaderBound=%s drawOccurred=%s formalRendererReady=%s",
                audit.success(),
                audit.error(),
                audit.durationMs(),
                audit.formalLifecycleSafeSetExists(),
                audit.formalModelStoreResourcesReady(),
                audit.bindingLayoutKnown(),
                audit.bindingLayoutCompatible(),
                audit.blockModelRecordLayoutCompatible(),
                audit.safeSetModelIdsAddressable(),
                audit.modelIdsFormal(),
                audit.placeholderIdUsed(),
                audit.sampleSetIdUsed(),
                audit.sampleSetBridgeUsedAsFormalSource(),
                audit.bindingValidationOk(),
                audit.shaderBound(),
                audit.drawOccurred(),
                audit.formalRendererReady()
        );
    }

    private static String formatFormalShaderProgramStatus(ForgeFormalShaderProgramStats status) {
        return String.format(
                "stage=%s buildRuns=%d auditRuns=%d clearRuns=%d auditFailures=%d formalShaderProgramValidatorReady=%s formalShaderProgramValidationReady=%s validationShaderCompileAttempted=%s validationShaderCompileOk=%s validationProgramLinkOk=%s validationProgramId=%d formalShaderProgramBoundForValidation=%s terrainDrawStarted=%s visibleDrawStarted=%s actualDrawEnabled=%s noDraw=%s formalModelStoreOwnerReady=%s formalShaderInputConsumerReady=%s reloadRebuildPrototypeReady=%s multiBlockFormalUploadAuditReady=%s modelDataBindingOk=%s modelColourBindingOk=%s atlasTextureBindingOk=%s samplerBindingOk=%s bindingLayoutCompatible=%s modelDataBindingIndex=%d modelColourBindingIndex=%d atlasTextureUnit=%d validationResultBindingIndex=%d validationModelIdBindingIndex=%d blockModelRecordLayoutCompatible=%s blockModelRecordSize=%d safeSetModelCount=%d validatedModelCount=%d validatedModelIds=%s gpuValidationAttempted=%s gpuValidationOk=%s gpuValidationSkippedReason=\"%s\" validationReadbackOk=%s validationFailureCount=%d usesFormalModelIds=%s usesPlaceholderModelIds=%s sampleSetModelIdsUsed=%s sampleSetBridgeUsedAsFormalSource=%s formalShaderInputContractReady=%s formalTexturedShaderReady=%s formalRendererReady=%s stale=%s requiresRebuild=%s lifecycleState=%s lastLifecycleEvent=%s staleReason=%s lastGlError=%s lastFailureReason=%s lastAuditOk=%s lastAuditError=%s lastAuditDurationMs=%.2f renderer=none terrainDraw=false visibleDraw=false formalRenderer=false MDICSectionRenderer=false VoxyRenderSystem=false sampleSetBridgeFormalSource=false",
                status.stage(),
                status.buildRuns(),
                status.auditRuns(),
                status.clearRuns(),
                status.auditFailures(),
                status.formalShaderProgramValidatorReady(),
                status.formalShaderProgramValidationReady(),
                status.validationShaderCompileAttempted(),
                status.validationShaderCompileOk(),
                status.validationProgramLinkOk(),
                status.validationProgramId(),
                status.formalShaderProgramBoundForValidation(),
                status.terrainDrawStarted(),
                status.visibleDrawStarted(),
                status.actualDrawEnabled(),
                status.noDraw(),
                status.formalModelStoreOwnerReady(),
                status.formalShaderInputConsumerReady(),
                status.reloadRebuildPrototypeReady(),
                status.multiBlockFormalUploadAuditReady(),
                status.modelDataBindingOk(),
                status.modelColourBindingOk(),
                status.atlasTextureBindingOk(),
                status.samplerBindingOk(),
                status.bindingLayoutCompatible(),
                status.modelDataBindingIndex(),
                status.modelColourBindingIndex(),
                status.atlasTextureUnit(),
                status.validationResultBindingIndex(),
                status.validationModelIdBindingIndex(),
                status.blockModelRecordLayoutCompatible(),
                status.blockModelRecordSize(),
                status.safeSetModelCount(),
                status.validatedModelCount(),
                status.validatedModelIds(),
                status.gpuValidationAttempted(),
                status.gpuValidationOk(),
                status.gpuValidationSkippedReason(),
                status.validationReadbackOk(),
                status.validationFailureCount(),
                status.usesFormalModelIds(),
                status.usesPlaceholderModelIds(),
                status.sampleSetModelIdsUsed(),
                status.sampleSetBridgeUsedAsFormalSource(),
                status.formalShaderInputContractReady(),
                status.formalTexturedShaderReady(),
                status.formalRendererReady(),
                status.stale(),
                status.requiresRebuild(),
                status.lifecycleState(),
                status.lastLifecycleEvent(),
                status.staleReason(),
                status.lastGlError(),
                status.lastFailureReason(),
                status.lastAuditOk(),
                status.lastAuditError(),
                status.lastAuditDurationMs()
        );
    }

    private static String formatFormalShaderProgramAudit(ForgeFormalShaderProgramAuditResult audit) {
        return String.format(
                "auditSuccess=%s auditError=%s auditDurationMs=%.2f i6SafeSetExists=%s j1FormalShaderInputValid=%s shaderCompileOk=%s programLinkOk=%s modelDataBindingOk=%s modelColourBindingOk=%s atlasTextureBindingOk=%s samplerBindingOk=%s bindingLayoutCompatible=%s blockModelRecordLayoutCompatible=%s formalModelIdsAddressable=%s placeholderIdUsed=%s sampleSetIdUsed=%s sampleSetBridgeUsedAsFormalSource=%s gpuValidationOk=%s validationReadbackOk=%s validationFailureCount=%d terrainDrawStarted=%s visibleDrawStarted=%s actualDrawEnabled=%s formalRendererReady=%s",
                audit.success(),
                audit.error(),
                audit.durationMs(),
                audit.i6SafeSetExists(),
                audit.j1FormalShaderInputValid(),
                audit.shaderCompileOk(),
                audit.programLinkOk(),
                audit.modelDataBindingOk(),
                audit.modelColourBindingOk(),
                audit.atlasTextureBindingOk(),
                audit.samplerBindingOk(),
                audit.bindingLayoutCompatible(),
                audit.blockModelRecordLayoutCompatible(),
                audit.formalModelIdsAddressable(),
                audit.placeholderIdUsed(),
                audit.sampleSetIdUsed(),
                audit.sampleSetBridgeUsedAsFormalSource(),
                audit.gpuValidationOk(),
                audit.validationReadbackOk(),
                audit.validationFailureCount(),
                audit.terrainDrawStarted(),
                audit.visibleDrawStarted(),
                audit.actualDrawEnabled(),
                audit.formalRendererReady()
        );
    }

    private static String formatFormalTexturedShaderPreviewStatus(ForgeFormalTexturedShaderPreviewStats status) {
        return String.format(
                "stage=%s buildRuns=%d auditRuns=%d clearRuns=%d auditFailures=%d formalTexturedShaderPrototypeReady=%s formalTexturedShaderPreviewReady=%s formalTexturedShaderReady=%s formalRendererReady=%s previewDrawOnly=%s offscreenPreviewReady=%s visiblePreviewSupported=%s visiblePreviewEnabled=%s terrainDrawStarted=%s formalRendererDrawStarted=%s actualRendererDrawEnabled=%s shaderCompileOk=%s programLinkOk=%s previewProgramId=%d modelDataBindingOk=%s modelColourBindingOk=%s atlasTextureBindingOk=%s samplerBindingOk=%s bindingLayoutCompatible=%s formalModelStoreOwnerReady=%s formalShaderProgramValidatorReady=%s formalShaderProgramValidationReady=%s reloadRebuildPrototypeReady=%s multiBlockFormalUploadAuditReady=%s safeSetModelCount=%d previewModelCount=%d previewModelIds=%s usesFormalModelIds=%s usesPlaceholderModelIds=%s sampleSetModelIdsUsed=%s sampleSetBridgeUsedAsFormalSource=%s previewFramebufferCreated=%s previewFramebufferComplete=%s previewFramebufferId=%d previewTextureId=%d previewWidth=%d previewHeight=%d previewReadbackOk=%s previewPixelMismatches=%d previewChecksumCount=%d previewChecksums=%s faceDataUsed=%s modelColourUsed=%s atlasSampleUsed=%s lightmapReady=%s biomeLutReady=%s materialSemanticsReady=%s alphaCutoutReady=%s translucentReady=%s shaderpackReady=%s stale=%s requiresRebuild=%s lifecycleState=%s lastLifecycleEvent=%s staleReason=%s lastGlError=%s lastFailureReason=%s lastAuditOk=%s lastAuditError=%s lastAuditDurationMs=%.2f renderer=preview-only terrainDraw=false formalRendererDraw=false MDICSectionRenderer=false VoxyRenderSystem=false sampleSetBridgeFormalSource=false",
                status.stage(),
                status.buildRuns(),
                status.auditRuns(),
                status.clearRuns(),
                status.auditFailures(),
                status.formalTexturedShaderPrototypeReady(),
                status.formalTexturedShaderPreviewReady(),
                status.formalTexturedShaderReady(),
                status.formalRendererReady(),
                status.previewDrawOnly(),
                status.offscreenPreviewReady(),
                status.visiblePreviewSupported(),
                status.visiblePreviewEnabled(),
                status.terrainDrawStarted(),
                status.formalRendererDrawStarted(),
                status.actualRendererDrawEnabled(),
                status.shaderCompileOk(),
                status.programLinkOk(),
                status.previewProgramId(),
                status.modelDataBindingOk(),
                status.modelColourBindingOk(),
                status.atlasTextureBindingOk(),
                status.samplerBindingOk(),
                status.bindingLayoutCompatible(),
                status.formalModelStoreOwnerReady(),
                status.formalShaderProgramValidatorReady(),
                status.formalShaderProgramValidationReady(),
                status.reloadRebuildPrototypeReady(),
                status.multiBlockFormalUploadAuditReady(),
                status.safeSetModelCount(),
                status.previewModelCount(),
                status.previewModelIds(),
                status.usesFormalModelIds(),
                status.usesPlaceholderModelIds(),
                status.sampleSetModelIdsUsed(),
                status.sampleSetBridgeUsedAsFormalSource(),
                status.previewFramebufferCreated(),
                status.previewFramebufferComplete(),
                status.previewFramebufferId(),
                status.previewTextureId(),
                status.previewWidth(),
                status.previewHeight(),
                status.previewReadbackOk(),
                status.previewPixelMismatches(),
                status.previewChecksumCount(),
                status.previewChecksums(),
                status.faceDataUsed(),
                status.modelColourUsed(),
                status.atlasSampleUsed(),
                status.lightmapReady(),
                status.biomeLutReady(),
                status.materialSemanticsReady(),
                status.alphaCutoutReady(),
                status.translucentReady(),
                status.shaderpackReady(),
                status.stale(),
                status.requiresRebuild(),
                status.lifecycleState(),
                status.lastLifecycleEvent(),
                status.staleReason(),
                status.lastGlError(),
                status.lastFailureReason(),
                status.lastAuditOk(),
                status.lastAuditError(),
                status.lastAuditDurationMs()
        );
    }

    private static String formatFormalTexturedShaderPreviewAudit(ForgeFormalTexturedShaderPreviewAuditResult audit) {
        return String.format(
                "auditSuccess=%s auditError=%s auditDurationMs=%.2f i6SafeSetExists=%s j2ValidationReady=%s shaderCompileOk=%s programLinkOk=%s modelDataBindingOk=%s modelColourBindingOk=%s atlasTextureBindingOk=%s samplerBindingOk=%s bindingLayoutCompatible=%s blockModelRecordLayoutCompatible=%s formalModelIdsAddressable=%s placeholderIdUsed=%s sampleSetIdUsed=%s sampleSetBridgeUsedAsFormalSource=%s offscreenPreviewReady=%s previewFramebufferComplete=%s previewReadbackOk=%s previewPixelMismatches=%d previewChecksumCount=%d previewDrawOnly=%s visiblePreviewEnabled=%s terrainDrawStarted=%s formalRendererDrawStarted=%s actualRendererDrawEnabled=%s formalTexturedShaderReady=%s formalRendererReady=%s",
                audit.success(),
                audit.error(),
                audit.durationMs(),
                audit.i6SafeSetExists(),
                audit.j2ValidationReady(),
                audit.shaderCompileOk(),
                audit.programLinkOk(),
                audit.modelDataBindingOk(),
                audit.modelColourBindingOk(),
                audit.atlasTextureBindingOk(),
                audit.samplerBindingOk(),
                audit.bindingLayoutCompatible(),
                audit.blockModelRecordLayoutCompatible(),
                audit.formalModelIdsAddressable(),
                audit.placeholderIdUsed(),
                audit.sampleSetIdUsed(),
                audit.sampleSetBridgeUsedAsFormalSource(),
                audit.offscreenPreviewReady(),
                audit.previewFramebufferComplete(),
                audit.previewReadbackOk(),
                audit.previewPixelMismatches(),
                audit.previewChecksumCount(),
                audit.previewDrawOnly(),
                audit.visiblePreviewEnabled(),
                audit.terrainDrawStarted(),
                audit.formalRendererDrawStarted(),
                audit.actualRendererDrawEnabled(),
                audit.formalTexturedShaderReady(),
                audit.formalRendererReady()
        );
    }

    private static String formatFormalPackedQuadPreviewStatus(ForgeFormalPackedQuadPreviewStats status) {
        return String.format(
                "stage=%s buildRuns=%d auditRuns=%d clearRuns=%d auditFailures=%d formalPackedQuadPreviewReady=%s packedQuadShaderPreviewReady=%s formalTexturedShaderReady=%s formalRendererReady=%s previewDrawOnly=%s offscreenPreviewReady=%s terrainDrawStarted=%s formalRendererDrawStarted=%s actualRendererDrawEnabled=%s originalGeometryUntouched=%s sourceRecordsScanned=%d sourceRecordsAccepted=%d sourceRecordsRejected=%d recordsRejectedNoBlockState=%d recordsRejectedNoFormalModelId=%d recordsRejectedUnsafeModelId=%d realTerrainRecordsUsed=%s syntheticFallbackUsed=%s syntheticFallbackReason=%s temporaryFormalQuadBufferCreated=%s temporaryFormalQuadBufferId=%d temporaryFormalQuadCount=%d temporaryFormalVertexCount=%d temporaryFormalModelIds=%s usesFormalModelIds=%s usesPlaceholderModelIds=%s sampleSetModelIdsUsed=%s sampleSetBridgeUsedAsFormalSource=%s shaderCompileOk=%s programLinkOk=%s previewProgramId=%d modelDataBindingOk=%s modelColourBindingOk=%s atlasTextureBindingOk=%s samplerBindingOk=%s bindingLayoutCompatible=%s quadRecordDecodeOk=%s modelIdDecodeOk=%s faceDecodeOk=%s faceDataUsed=%s atlasSampleUsed=%s modelColourUsed=%s previewFramebufferCreated=%s previewFramebufferComplete=%s previewFramebufferId=%d previewTextureId=%d previewWidth=%d previewHeight=%d previewReadbackOk=%s previewPixelMismatches=%d previewChecksumCount=%d previewChecksums=%s lightmapReady=%s biomeLutReady=%s materialSemanticsReady=%s alphaCutoutReady=%s translucentReady=%s shaderpackReady=%s stale=%s requiresRebuild=%s lifecycleState=%s lastLifecycleEvent=%s staleReason=%s lastGlError=%s lastFailureReason=%s lastAuditOk=%s lastAuditError=%s lastAuditDurationMs=%.2f renderer=packed-quad-preview-only terrainDraw=false formalRendererDraw=false MDICSectionRenderer=false VoxyRenderSystem=false originalGeometryHeapModified=false sampleSetBridgeFormalSource=false",
                status.stage(),
                status.buildRuns(),
                status.auditRuns(),
                status.clearRuns(),
                status.auditFailures(),
                status.formalPackedQuadPreviewReady(),
                status.packedQuadShaderPreviewReady(),
                status.formalTexturedShaderReady(),
                status.formalRendererReady(),
                status.previewDrawOnly(),
                status.offscreenPreviewReady(),
                status.terrainDrawStarted(),
                status.formalRendererDrawStarted(),
                status.actualRendererDrawEnabled(),
                status.originalGeometryUntouched(),
                status.sourceRecordsScanned(),
                status.sourceRecordsAccepted(),
                status.sourceRecordsRejected(),
                status.recordsRejectedNoBlockState(),
                status.recordsRejectedNoFormalModelId(),
                status.recordsRejectedUnsafeModelId(),
                status.realTerrainRecordsUsed(),
                status.syntheticFallbackUsed(),
                status.syntheticFallbackReason(),
                status.temporaryFormalQuadBufferCreated(),
                status.temporaryFormalQuadBufferId(),
                status.temporaryFormalQuadCount(),
                status.temporaryFormalVertexCount(),
                status.temporaryFormalModelIds(),
                status.usesFormalModelIds(),
                status.usesPlaceholderModelIds(),
                status.sampleSetModelIdsUsed(),
                status.sampleSetBridgeUsedAsFormalSource(),
                status.shaderCompileOk(),
                status.programLinkOk(),
                status.previewProgramId(),
                status.modelDataBindingOk(),
                status.modelColourBindingOk(),
                status.atlasTextureBindingOk(),
                status.samplerBindingOk(),
                status.bindingLayoutCompatible(),
                status.quadRecordDecodeOk(),
                status.modelIdDecodeOk(),
                status.faceDecodeOk(),
                status.faceDataUsed(),
                status.atlasSampleUsed(),
                status.modelColourUsed(),
                status.previewFramebufferCreated(),
                status.previewFramebufferComplete(),
                status.previewFramebufferId(),
                status.previewTextureId(),
                status.previewWidth(),
                status.previewHeight(),
                status.previewReadbackOk(),
                status.previewPixelMismatches(),
                status.previewChecksumCount(),
                status.previewChecksums(),
                status.lightmapReady(),
                status.biomeLutReady(),
                status.materialSemanticsReady(),
                status.alphaCutoutReady(),
                status.translucentReady(),
                status.shaderpackReady(),
                status.stale(),
                status.requiresRebuild(),
                status.lifecycleState(),
                status.lastLifecycleEvent(),
                status.staleReason(),
                status.lastGlError(),
                status.lastFailureReason(),
                status.lastAuditOk(),
                status.lastAuditError(),
                status.lastAuditDurationMs()
        );
    }

    private static String formatFormalPackedQuadPreviewAudit(ForgeFormalPackedQuadPreviewAuditResult audit) {
        return String.format(
                "auditSuccess=%s auditError=%s auditDurationMs=%.2f i6SafeSetExists=%s j3PreviewWorks=%s packedQuadRecordsMapped=%s placeholderIdUsed=%s sampleSetIdUsed=%s sampleSetBridgeUsedAsFormalSource=%s originalGeometryUntouched=%s temporaryFormalQuadBufferIsolated=%s temporaryFormalQuadBufferCreated=%s quadRecordDecodeOk=%s modelIdDecodeOk=%s faceDecodeOk=%s offscreenPreviewReady=%s previewFramebufferComplete=%s previewReadbackOk=%s previewPixelMismatches=%d previewChecksumCount=%d previewDrawOnly=%s terrainDrawStarted=%s formalRendererDrawStarted=%s actualRendererDrawEnabled=%s formalTexturedShaderReady=%s formalRendererReady=%s",
                audit.success(),
                audit.error(),
                audit.durationMs(),
                audit.i6SafeSetExists(),
                audit.j3PreviewWorks(),
                audit.packedQuadRecordsMapped(),
                audit.placeholderIdUsed(),
                audit.sampleSetIdUsed(),
                audit.sampleSetBridgeUsedAsFormalSource(),
                audit.originalGeometryUntouched(),
                audit.temporaryFormalQuadBufferIsolated(),
                audit.temporaryFormalQuadBufferCreated(),
                audit.quadRecordDecodeOk(),
                audit.modelIdDecodeOk(),
                audit.faceDecodeOk(),
                audit.offscreenPreviewReady(),
                audit.previewFramebufferComplete(),
                audit.previewReadbackOk(),
                audit.previewPixelMismatches(),
                audit.previewChecksumCount(),
                audit.previewDrawOnly(),
                audit.terrainDrawStarted(),
                audit.formalRendererDrawStarted(),
                audit.actualRendererDrawEnabled(),
                audit.formalTexturedShaderReady(),
                audit.formalRendererReady()
        );
    }

    private static String formatFormalTerrainRecordBridgeStatus(ForgeFormalTerrainPackedRecordBridgeStats status) {
        return String.format(
                "stage=%s buildRuns=%d auditRuns=%d clearRuns=%d auditFailures=%d formalTerrainPackedRecordBridgeReady=%s realTerrainPackedRecordBridgeReady=%s packedQuadShaderPreviewReady=%s formalTexturedShaderReady=%s formalRendererReady=%s previewDrawOnly=%s offscreenPreviewReady=%s terrainDrawStarted=%s formalRendererDrawStarted=%s actualRendererDrawEnabled=%s sourceKind=%s sourceWorldPosition=%s sourceSectionPosition=%s sourceRecordsScanned=%d sourceRecordsAccepted=%d sourceRecordsRejected=%d recordsRejectedNoBlockState=%d recordsRejectedNoFormalModelId=%d recordsRejectedUnsafeModelId=%d recordsRejectedUnsupportedBlock=%d realTerrainRecordsUsed=%s syntheticFallbackUsed=%s syntheticFallbackReason=%s blockStateSourceAvailable=%s formalModelIdLookupOk=%s formalModelIdsBackedByRealBake=%s temporaryModelIdRewriteOk=%s originalRecordUnchanged=%s originalGeometryUntouched=%s originalGeometryHeapUntouched=%s temporaryFormalQuadBufferCreated=%s temporaryFormalQuadBufferOwnedByJ5=%s temporaryFormalQuadCount=%d temporaryFormalVertexCount=%d temporaryFormalModelIds=%s usesFormalModelIds=%s usesPlaceholderModelIds=%s sampleSetModelIdsUsed=%s sampleSetBridgeUsedAsFormalSource=%s quadRecordDecodeOk=%s modelIdDecodeOk=%s faceDecodeOk=%s faceDataUsed=%s atlasSampleUsed=%s modelColourUsed=%s previewFramebufferComplete=%s previewReadbackOk=%s previewPixelMismatches=%d previewChecksumCount=%d previewChecksums=%s lightmapReady=%s biomeLutReady=%s materialSemanticsReady=%s alphaCutoutReady=%s translucentReady=%s shaderpackReady=%s stale=%s requiresRebuild=%s lifecycleState=%s lastLifecycleEvent=%s staleReason=%s lastGlError=%s lastFailureReason=%s lastAuditOk=%s lastAuditError=%s lastAuditDurationMs=%.2f renderer=terrain-record-bridge-preview-only terrainDraw=false formalRendererDraw=false MDICSectionRenderer=false VoxyRenderSystem=false originalGeometryHeapModified=false sampleSetBridgeFormalSource=false",
                status.stage(),
                status.buildRuns(),
                status.auditRuns(),
                status.clearRuns(),
                status.auditFailures(),
                status.formalTerrainPackedRecordBridgeReady(),
                status.realTerrainPackedRecordBridgeReady(),
                status.packedQuadShaderPreviewReady(),
                status.formalTexturedShaderReady(),
                status.formalRendererReady(),
                status.previewDrawOnly(),
                status.offscreenPreviewReady(),
                status.terrainDrawStarted(),
                status.formalRendererDrawStarted(),
                status.actualRendererDrawEnabled(),
                status.sourceKind(),
                status.sourceWorldPosition(),
                status.sourceSectionPosition(),
                status.sourceRecordsScanned(),
                status.sourceRecordsAccepted(),
                status.sourceRecordsRejected(),
                status.recordsRejectedNoBlockState(),
                status.recordsRejectedNoFormalModelId(),
                status.recordsRejectedUnsafeModelId(),
                status.recordsRejectedUnsupportedBlock(),
                status.realTerrainRecordsUsed(),
                status.syntheticFallbackUsed(),
                status.syntheticFallbackReason(),
                status.blockStateSourceAvailable(),
                status.formalModelIdLookupOk(),
                status.formalModelIdsBackedByRealBake(),
                status.temporaryModelIdRewriteOk(),
                status.originalRecordUnchanged(),
                status.originalGeometryUntouched(),
                status.originalGeometryHeapUntouched(),
                status.temporaryFormalQuadBufferCreated(),
                status.temporaryFormalQuadBufferOwnedByJ5(),
                status.temporaryFormalQuadCount(),
                status.temporaryFormalVertexCount(),
                status.temporaryFormalModelIds(),
                status.usesFormalModelIds(),
                status.usesPlaceholderModelIds(),
                status.sampleSetModelIdsUsed(),
                status.sampleSetBridgeUsedAsFormalSource(),
                status.quadRecordDecodeOk(),
                status.modelIdDecodeOk(),
                status.faceDecodeOk(),
                status.faceDataUsed(),
                status.atlasSampleUsed(),
                status.modelColourUsed(),
                status.previewFramebufferComplete(),
                status.previewReadbackOk(),
                status.previewPixelMismatches(),
                status.previewChecksumCount(),
                status.previewChecksums(),
                status.lightmapReady(),
                status.biomeLutReady(),
                status.materialSemanticsReady(),
                status.alphaCutoutReady(),
                status.translucentReady(),
                status.shaderpackReady(),
                status.stale(),
                status.requiresRebuild(),
                status.lifecycleState(),
                status.lastLifecycleEvent(),
                status.staleReason(),
                status.lastGlError(),
                status.lastFailureReason(),
                status.lastAuditOk(),
                status.lastAuditError(),
                status.lastAuditDurationMs()
        );
    }

    private static String formatFormalTerrainRecordBridgeAudit(ForgeFormalTerrainPackedRecordBridgeAuditResult audit) {
        return String.format(
                "auditSuccess=%s auditError=%s auditDurationMs=%.2f i6SafeSetExists=%s j4PreviewPathExists=%s realTerrainRecordsUsed=%s syntheticFallbackUsed=%s acceptedRecordsHaveFormalMapping=%s formalModelIdsBackedByRealBake=%s placeholderIdUsed=%s sampleSetIdUsed=%s sampleSetBridgeUsedAsFormalSource=%s originalRecordUnchanged=%s originalGeometryUntouched=%s originalGeometryHeapUntouched=%s temporaryFormalQuadBufferIsolated=%s offscreenPreviewReady=%s previewFramebufferComplete=%s previewReadbackOk=%s previewPixelMismatches=%d previewChecksumCount=%d terrainDrawStarted=%s formalRendererDrawStarted=%s actualRendererDrawEnabled=%s formalRendererReady=%s",
                audit.success(),
                audit.error(),
                audit.durationMs(),
                audit.i6SafeSetExists(),
                audit.j4PreviewPathExists(),
                audit.realTerrainRecordsUsed(),
                audit.syntheticFallbackUsed(),
                audit.acceptedRecordsHaveFormalMapping(),
                audit.formalModelIdsBackedByRealBake(),
                audit.placeholderIdUsed(),
                audit.sampleSetIdUsed(),
                audit.sampleSetBridgeUsedAsFormalSource(),
                audit.originalRecordUnchanged(),
                audit.originalGeometryUntouched(),
                audit.originalGeometryHeapUntouched(),
                audit.temporaryFormalQuadBufferIsolated(),
                audit.offscreenPreviewReady(),
                audit.previewFramebufferComplete(),
                audit.previewReadbackOk(),
                audit.previewPixelMismatches(),
                audit.previewChecksumCount(),
                audit.terrainDrawStarted(),
                audit.formalRendererDrawStarted(),
                audit.actualRendererDrawEnabled(),
                audit.formalRendererReady()
        );
    }

    private static String formatFormalTerrainRendererOwnerStatus(ForgeFormalTerrainRendererStats status) {
        return String.format(
                "stage=%s formalTerrainRendererOwnerReady=%s formalTerrainRendererLifecycleReady=%s formalTerrainRendererReady=%s actualRendererDrawEnabled=%s noDraw=%s enabled=%s k0AlignmentAuditReady=%s k0VerdictReadyForK1=%s originalVoxyAlignmentPreserved=%s formalModelStoreOwnerReady=%s formalModelBakeryLifecycleSkeletonReady=%s realTerrainPackedRecordBridgeReady=%s formalShaderProgramValidationReady=%s formalTexturedShaderPrototypeReady=%s formalPackedQuadPreviewReady=%s geometryHeapReady=%s sectionGeometryManagerReady=%s formalViewportOwnerReady=%s formalCommandBufferOwnerReady=%s formalVisibilityOwnerReady=%s formalDrawPipelineReady=%s globalFormalModelIdGeometryReady=%s formalTerrainShaderReady=%s debugRendererIsolationOk=%s previewSystemsSeparated=%s sampleSetUsedAsFormalSource=%s lifecycleState=%s lastLifecycleEvent=%s stale=%s requiresRebuild=%s blockerCount=%d p0BlockerCount=%d p1BlockerCount=%d p2BlockerCount=%d blockers=%s resourceReloadSeen=%s worldUnloadSeen=%s dimensionSwitchSeen=%s debugPipelineClearSeen=%s presetOffSeen=%s presetClearSeen=%s mdicSectionRendererCalled=%s voxyRenderSystemCalled=%s liveTerrainDrawStarted=%s formalMdicDrawStarted=%s lastFailureReason=%s lastAuditOk=%s lastAuditError=%s renderer=formal-terrain-renderer-owner formalRenderer=false draw=false",
                status.stage(),
                status.formalTerrainRendererOwnerReady(),
                status.formalTerrainRendererLifecycleReady(),
                status.formalTerrainRendererReady(),
                status.actualRendererDrawEnabled(),
                status.noDraw(),
                status.enabled(),
                status.k0AlignmentAuditReady(),
                status.k0VerdictReadyForK1(),
                status.originalVoxyAlignmentPreserved(),
                status.formalModelStoreOwnerReady(),
                status.formalModelBakeryLifecycleSkeletonReady(),
                status.realTerrainPackedRecordBridgeReady(),
                status.formalShaderProgramValidationReady(),
                status.formalTexturedShaderPrototypeReady(),
                status.formalPackedQuadPreviewReady(),
                status.geometryHeapReady(),
                status.sectionGeometryManagerReady(),
                status.formalViewportOwnerReady(),
                status.formalCommandBufferOwnerReady(),
                status.formalVisibilityOwnerReady(),
                status.formalDrawPipelineReady(),
                status.globalFormalModelIdGeometryReady(),
                status.formalTerrainShaderReady(),
                status.debugRendererIsolationOk(),
                status.previewSystemsSeparated(),
                status.sampleSetUsedAsFormalSource(),
                status.lifecycleState(),
                status.lastLifecycleEvent(),
                status.stale(),
                status.requiresRebuild(),
                status.blockerCount(),
                status.p0BlockerCount(),
                status.p1BlockerCount(),
                status.p2BlockerCount(),
                status.blockers(),
                status.resourceReloadSeen(),
                status.worldUnloadSeen(),
                status.dimensionSwitchSeen(),
                status.debugPipelineClearSeen(),
                status.presetOffSeen(),
                status.presetClearSeen(),
                status.mdicSectionRendererCalled(),
                status.voxyRenderSystemCalled(),
                status.liveTerrainDrawStarted(),
                status.formalMdicDrawStarted(),
                status.lastFailureReason(),
                status.lastAuditOk(),
                status.lastAuditError()
        );
    }

    private static String formatFormalTerrainRendererOwnerAudit(ForgeFormalTerrainRendererAuditResult audit) {
        return String.format(
                "k1AuditOk=%s k1AuditError=%s k0AuditDocExists=%s k0VerdictReadyForK1=%s ownerExists=%s originalVoxyAlignmentPreserved=%s previewSystemsSeparated=%s debugRenderersIsolated=%s sampleSetUsedAsFormalSource=%s terrainDrawStarted=%s formalRendererDrawStarted=%s mdicSectionRendererCalled=%s voxyRenderSystemCalled=%s formalTerrainRendererReady=%s actualRendererDrawEnabled=%s",
                audit.success(),
                audit.error(),
                audit.k0AuditDocExists(),
                audit.k0VerdictReadyForK1(),
                audit.ownerExists(),
                audit.originalVoxyAlignmentPreserved(),
                audit.previewSystemsSeparated(),
                audit.debugRenderersIsolated(),
                audit.sampleSetUsedAsFormalSource(),
                audit.terrainDrawStarted(),
                audit.formalRendererDrawStarted(),
                audit.mdicSectionRendererCalled(),
                audit.voxyRenderSystemCalled(),
                audit.formalTerrainRendererReady(),
                audit.actualRendererDrawEnabled()
        );
    }

    private static String formatFormalMdicViewportOwnerStatus(ForgeFormalMdicViewportStats status) {
        return String.format(
                "stage=%s formalTerrainRendererOwnerReady=%s formalViewportOwnerReady=%s formalCommandBufferOwnerReady=%s formalDrawCommandBufferOwnerReady=%s formalDrawCountBufferOwnerReady=%s formalVisibilityOwnerReady=%s formalRenderListOwnerReady=%s formalIndirectLookupOwnerReady=%s formalPositionScratchOwnerReady=%s formalCommandGenerationOwnerReady=%s formalDrawPipelineReady=%s formalRendererReady=%s actualRendererDrawEnabled=%s noDraw=%s enabled=%s originalVoxyMdicViewportAlignmentChecked=%s originalVoxyMdicSectionRendererAlignmentChecked=%s debugMdicCommandBuffersUsedAsFormal=%s debugRendererIsolationOk=%s drawCommandBufferCreated=%s drawCommandBufferAllocated=%s drawCommandBufferId=%d drawCommandBufferBytes=%d drawCommandCapacity=%d drawCommandLogicalOnly=%s drawCountBufferCreated=%s drawCountBufferAllocated=%s drawCountBufferId=%d drawCountBufferBytes=%d drawCountCapacity=%d drawCountLogicalOnly=%s visibilityBufferCreated=%s visibilityBufferAllocated=%s visibilityBufferId=%d visibilityBufferBytes=%d visibilityCapacity=%d visibilityLogicalOnly=%s renderListOrIndirectLookupCreated=%s renderListOrIndirectLookupAllocated=%s renderListOrIndirectLookupBufferId=%d renderListOrIndirectLookupBytes=%d renderListCapacity=%d renderListLogicalOnly=%s positionScratchCreated=%s positionScratchAllocated=%s positionScratchBufferId=%d positionScratchBytes=%d positionScratchCapacity=%d positionScratchLogicalOnly=%s allocationDeferredReason=%s lifecycleState=%s lastLifecycleEvent=%s lifecycleGeneration=%d stale=%s requiresRebuild=%s blockerCount=%d p0BlockerCount=%d p1BlockerCount=%d p2BlockerCount=%d blockers=%s resourceReloadSeen=%s worldUnloadSeen=%s dimensionSwitchSeen=%s debugPipelineClearSeen=%s presetOffSeen=%s presetClearSeen=%s drawCommandExecuted=%s multiDrawIndirectCountCalled=%s formalCmdgenExecuted=%s mdicSectionRendererCalled=%s voxyRenderSystemCalled=%s lastFailureReason=%s lastAuditOk=%s lastAuditError=%s renderer=formal-mdic-viewport-owner formalRenderer=false draw=false",
                status.stage(),
                status.formalTerrainRendererOwnerReady(),
                status.formalViewportOwnerReady(),
                status.formalCommandBufferOwnerReady(),
                status.formalDrawCommandBufferOwnerReady(),
                status.formalDrawCountBufferOwnerReady(),
                status.formalVisibilityOwnerReady(),
                status.formalRenderListOwnerReady(),
                status.formalIndirectLookupOwnerReady(),
                status.formalPositionScratchOwnerReady(),
                status.formalCommandGenerationOwnerReady(),
                status.formalDrawPipelineReady(),
                status.formalRendererReady(),
                status.actualRendererDrawEnabled(),
                status.noDraw(),
                status.enabled(),
                status.originalVoxyMdicViewportAlignmentChecked(),
                status.originalVoxyMdicSectionRendererAlignmentChecked(),
                status.debugMdicCommandBuffersUsedAsFormal(),
                status.debugRendererIsolationOk(),
                status.drawCommandBufferCreated(),
                status.drawCommandBufferAllocated(),
                status.drawCommandBufferId(),
                status.drawCommandBufferBytes(),
                status.drawCommandCapacity(),
                status.drawCommandLogicalOnly(),
                status.drawCountBufferCreated(),
                status.drawCountBufferAllocated(),
                status.drawCountBufferId(),
                status.drawCountBufferBytes(),
                status.drawCountCapacity(),
                status.drawCountLogicalOnly(),
                status.visibilityBufferCreated(),
                status.visibilityBufferAllocated(),
                status.visibilityBufferId(),
                status.visibilityBufferBytes(),
                status.visibilityCapacity(),
                status.visibilityLogicalOnly(),
                status.renderListOrIndirectLookupCreated(),
                status.renderListOrIndirectLookupAllocated(),
                status.renderListOrIndirectLookupBufferId(),
                status.renderListOrIndirectLookupBytes(),
                status.renderListCapacity(),
                status.renderListLogicalOnly(),
                status.positionScratchCreated(),
                status.positionScratchAllocated(),
                status.positionScratchBufferId(),
                status.positionScratchBytes(),
                status.positionScratchCapacity(),
                status.positionScratchLogicalOnly(),
                status.allocationDeferredReason(),
                status.lifecycleState(),
                status.lastLifecycleEvent(),
                status.lifecycleGeneration(),
                status.stale(),
                status.requiresRebuild(),
                status.blockerCount(),
                status.p0BlockerCount(),
                status.p1BlockerCount(),
                status.p2BlockerCount(),
                status.blockers(),
                status.resourceReloadSeen(),
                status.worldUnloadSeen(),
                status.dimensionSwitchSeen(),
                status.debugPipelineClearSeen(),
                status.presetOffSeen(),
                status.presetClearSeen(),
                status.drawCommandExecuted(),
                status.multiDrawIndirectCountCalled(),
                status.formalCmdgenExecuted(),
                status.mdicSectionRendererCalled(),
                status.voxyRenderSystemCalled(),
                status.lastFailureReason(),
                status.lastAuditOk(),
                status.lastAuditError()
        );
    }

    private static String formatFormalMdicViewportOwnerAudit(ForgeFormalMdicViewportAuditResult audit) {
        return String.format(
                "k2AuditOk=%s k2AuditError=%s k1OwnerExists=%s formalViewportOwnerExists=%s formalCommandOwnershipExplicit=%s formalVisibilityOwnershipExplicit=%s originalVoxyMdicViewportInspected=%s originalVoxyMdicSectionRendererInspected=%s debugMdicCommandBuffersUsedAsFormal=%s debugRendererStartedOrReplaced=%s drawCommandExecuted=%s multiDrawIndirectCountCalled=%s formalCmdgenExecuted=%s mdicSectionRendererCalled=%s voxyRenderSystemCalled=%s formalRendererReady=%s actualRendererDrawEnabled=%s",
                audit.success(),
                audit.error(),
                audit.k1OwnerExists(),
                audit.formalViewportOwnerExists(),
                audit.formalCommandOwnershipExplicit(),
                audit.formalVisibilityOwnershipExplicit(),
                audit.originalVoxyMdicViewportInspected(),
                audit.originalVoxyMdicSectionRendererInspected(),
                audit.debugMdicCommandBuffersUsedAsFormal(),
                audit.debugRendererStartedOrReplaced(),
                audit.drawCommandExecuted(),
                audit.multiDrawIndirectCountCalled(),
                audit.formalCmdgenExecuted(),
                audit.mdicSectionRendererCalled(),
                audit.voxyRenderSystemCalled(),
                audit.formalRendererReady(),
                audit.actualRendererDrawEnabled()
        );
    }

    private static String formatFormalRendererStatus(ForgeFormalRendererStats status) {
        return String.format(
                "stage=%s formalRendererSkeletonReady=%s formalRendererReady=%s actualDrawEnabled=%s noDraw=%s enabled=%s lifecycleState=%s formalRendererOwnershipReady=%s lastEnableReason=%s lastDisableReason=%s lastClearReason=%s lastLifecycleEvent=%s lastCheckAt=%s lastCheckReason=%s lastStaleReason=%s requiresRecheck=%s readinessGeneration=%d lifecycleGeneration=%d geometryHeapReady=%s metadataReady=%s sectionGeometryManagerReady=%s mdicCommandReady=%s mdicDrawCountReady=%s modelBridgeReady=%s formalShaderInputBridgeReady=%s atlasReady=%s resourceReloadReady=%s worldEngineReady=%s dimensionReady=%s infrastructureReady=%s debugProofReady=%s sampleBridgeReady=%s oneBlockBakePrototypeReady=%s oneBlockFormalUploadReady=%s oneBlockFormalUploadAuditReady=%s multiBlockBakePrototypeReady=%s multiBlockFormalUploadReady=%s multiBlockFormalUploadAuditReady=%s formalModelBakeryLifecycleSkeletonReady=%s reloadRebuildPrototypeReady=%s aliasSafeDedupeReady=%s formalModelFactorySkeletonReady=%s formalModelFactoryLifecycleReady=%s formalModelStoreSkeletonReady=%s formalModelStoreOwnerReady=%s formalShaderInputConsumerReady=%s formalShaderInputBindingLayoutKnown=%s formalShaderInputBindingLayoutCompatible=%s formalShaderProgramValidatorReady=%s formalShaderProgramValidationReady=%s validationShaderCompileOk=%s validationProgramLinkOk=%s gpuValidationOk=%s formalTexturedShaderPrototypeReady=%s formalTexturedShaderPreviewReady=%s formalPackedQuadPreviewReady=%s packedQuadShaderPreviewReady=%s packedQuadModelIdBridgeReady=%s formalTerrainPackedRecordBridgeReady=%s realTerrainPackedRecordBridgeReady=%s temporaryFormalQuadBufferCreated=%s originalGeometryUntouched=%s originalGeometryHeapUntouched=%s realTerrainRecordsUsed=%s syntheticFallbackUsed=%s formalTerrainRendererOwnerReady=%s formalTerrainRendererLifecycleReady=%s k0AlignmentAuditReady=%s k0VerdictReadyForK1=%s originalVoxyAlignmentPreserved=%s formalViewportOwnerReady=%s formalCommandBufferOwnerReady=%s formalVisibilityOwnerReady=%s formalDrawPipelineReady=%s globalFormalModelIdGeometryReady=%s formalTerrainShaderReady=%s previewSystemsSeparated=%s sampleSetUsedAsFormalSource=%s terrainDrawStarted=%s formalRendererDrawStarted=%s actualRendererDrawEnabled=%s formalShaderInputContractReady=%s formalPrerequisitesReady=%s realModelFactoryReady=%s realModelBakeryReady=%s realModelStoreReady=%s formalShaderReady=%s formalTextureAtlasReady=%s formalTexturedShaderReady=%s formalTraversalReady=%s formalVisibilityTraversalReady=%s formalVoxyRenderSystemReady=%s shaderpackIntegrationReady=%s blockerCount=%d p0BlockerCount=%d p1BlockerCount=%d p2BlockerCount=%d formalDrawBlockingBlockerCount=%d blockers=%s worldUnloadSeen=%s dimensionSwitchSeen=%s resourceReloadSeen=%s debugPipelineClearSeen=%s presetOffSeen=%s presetClearSeen=%s formalRendererStale=%s debugRenderersIsolated=%s existingMdicDebugTouched=%s texturedMdicDebugTouched=%s actualDrawStartedByFormalRenderer=%s renderer=formal-renderer-skeleton formalRenderer=false draw=false",
                status.stage(),
                status.formalRendererSkeletonReady(),
                status.formalRendererReady(),
                status.actualDrawEnabled(),
                status.noDraw(),
                status.enabled(),
                status.lifecycleState(),
                status.formalRendererOwnershipReady(),
                status.lastEnableReason(),
                status.lastDisableReason(),
                status.lastClearReason(),
                status.lastLifecycleEvent(),
                status.lastCheckAt(),
                status.lastCheckReason(),
                status.lastStaleReason(),
                status.requiresRecheck(),
                status.readinessGeneration(),
                status.lifecycleGeneration(),
                status.geometryHeapReady(),
                status.metadataReady(),
                status.sectionGeometryManagerReady(),
                status.mdicCommandReady(),
                status.mdicDrawCountReady(),
                status.modelBridgeReady(),
                status.formalShaderInputBridgeReady(),
                status.atlasReady(),
                status.resourceReloadReady(),
                status.worldEngineReady(),
                status.dimensionReady(),
                status.infrastructureReady(),
                status.debugProofReady(),
                status.sampleBridgeReady(),
                status.oneBlockBakePrototypeReady(),
                status.oneBlockFormalUploadReady(),
                status.oneBlockFormalUploadAuditReady(),
                status.multiBlockBakePrototypeReady(),
                status.multiBlockFormalUploadReady(),
                status.multiBlockFormalUploadAuditReady(),
                status.formalModelBakeryLifecycleSkeletonReady(),
                status.reloadRebuildPrototypeReady(),
                status.aliasSafeDedupeReady(),
                status.formalModelFactorySkeletonReady(),
                status.formalModelFactoryLifecycleReady(),
                status.formalModelStoreSkeletonReady(),
                status.formalModelStoreOwnerReady(),
                status.formalShaderInputConsumerReady(),
                status.formalShaderInputBindingLayoutKnown(),
                status.formalShaderInputBindingLayoutCompatible(),
                status.formalShaderProgramValidatorReady(),
                status.formalShaderProgramValidationReady(),
                status.validationShaderCompileOk(),
                status.validationProgramLinkOk(),
                status.gpuValidationOk(),
                status.formalTexturedShaderPrototypeReady(),
                status.formalTexturedShaderPreviewReady(),
                status.formalPackedQuadPreviewReady(),
                status.packedQuadShaderPreviewReady(),
                status.packedQuadModelIdBridgeReady(),
                status.formalTerrainPackedRecordBridgeReady(),
                status.realTerrainPackedRecordBridgeReady(),
                status.temporaryFormalQuadBufferCreated(),
                status.originalGeometryUntouched(),
                status.originalGeometryHeapUntouched(),
                status.realTerrainRecordsUsed(),
                status.syntheticFallbackUsed(),
                status.formalTerrainRendererOwnerReady(),
                status.formalTerrainRendererLifecycleReady(),
                status.k0AlignmentAuditReady(),
                status.k0VerdictReadyForK1(),
                status.originalVoxyAlignmentPreserved(),
                status.formalViewportOwnerReady(),
                status.formalCommandBufferOwnerReady(),
                status.formalVisibilityOwnerReady(),
                status.formalDrawPipelineReady(),
                status.globalFormalModelIdGeometryReady(),
                status.formalTerrainShaderReady(),
                status.previewSystemsSeparated(),
                status.sampleSetUsedAsFormalSource(),
                status.terrainDrawStarted(),
                status.formalRendererDrawStarted(),
                status.actualRendererDrawEnabled(),
                status.formalShaderInputContractReady(),
                status.formalPrerequisitesReady(),
                status.realModelFactoryReady(),
                status.realModelBakeryReady(),
                status.realModelStoreReady(),
                status.formalShaderReady(),
                status.formalTextureAtlasReady(),
                status.formalTexturedShaderReady(),
                status.formalTraversalReady(),
                status.formalVisibilityTraversalReady(),
                status.formalVoxyRenderSystemReady(),
                status.shaderpackIntegrationReady(),
                status.blockerCount(),
                status.p0BlockerCount(),
                status.p1BlockerCount(),
                status.p2BlockerCount(),
                status.formalDrawBlockingBlockerCount(),
                status.blockers(),
                status.worldUnloadSeen(),
                status.dimensionSwitchSeen(),
                status.resourceReloadSeen(),
                status.debugPipelineClearSeen(),
                status.presetOffSeen(),
                status.presetClearSeen(),
                status.formalRendererStale(),
                status.debugRenderersIsolated(),
                status.existingMdicDebugTouched(),
                status.texturedMdicDebugTouched(),
                status.actualDrawStartedByFormalRenderer()
        );
    }

    private static String texturedDebugQuadStatusSuffix() {
        return " " + formatTexturedDebugQuadStatus(ForgeVoxyInstance.INSTANCE.getTexturedDebugQuadRenderer().createStatusSnapshot());
    }

    private static String texturedReadbackStatusSuffix() {
        return " " + formatTexturedReadbackStatus(ForgeVoxyInstance.INSTANCE.getTexturedReadbackRenderer().createStatusSnapshot());
    }

    private static String texturedMdicDebugStatusSuffix() {
        return " " + formatTexturedMdicDebugStatus(ForgeVoxyInstance.INSTANCE.getTexturedMdicDebugRenderer().createStatusSnapshot());
    }

    private static String modelBridgeResourceReloadStatusSuffix() {
        return " " + formatModelBridgeResourceReloadStatus(ForgeVoxyInstance.INSTANCE.getModelBridgeResourceReloadTracker().createStatusSnapshot());
    }

    private static String formatFormalShaderInputBridgeStatus(ForgeFormalShaderInputStats status) {
        return String.format(
                "formalShaderInputStage=%s buildRuns=%d clearRuns=%d auditRuns=%d auditFailures=%d lastBuildError=%s lastBuildDurationMs=%.2f formalShaderInputBridgeReady=%s sampleModelDataBufferReady=%s sampleModelColourBufferReady=%s sampleAtlasTextureReady=%s modelDataBindingReady=%s modelColourBindingReady=%s atlasSamplerBindingReady=%s usesSampleSetModelData=%s usesRealModelStore=%s formalTexturedShaderReady=%s formalModelBridgeReady=%s sampleModelCount=%d sampleModelIds=%s maxModelId=%d modelDataBufferId=%d modelColourBufferId=%d modelValidityBufferId=%d atlasTextureObjectId=%d fullAtlasTextureCreated=%s atlasWidth=%d atlasHeight=%d modelDataBindingIndex=%d modelColourBindingIndex=%d modelValidityBindingIndex=%d atlasTextureUnit=%d usesSparseModelIdIndex=%s formalShaderInputBridgeStale=%s lastReloadInvalidatedFormalShaderInputBridge=%s lastAuditOk=%s lastAuditError=%s lastAuditDurationMs=%.2f modelDataBufferMatch=%s modelColourBufferMatch=%s atlasUploadStillValid=%s invalidModelRecords=%d invalidColourRecords=%d missingAtlasTiles=%d sampleOnly=true renderer=none formalRenderer=false",
                status.stage(),
                status.buildRuns(),
                status.clearRuns(),
                status.auditRuns(),
                status.auditFailures(),
                status.lastBuildError(),
                status.lastBuildDurationMs(),
                status.formalShaderInputBridgeReady(),
                status.sampleModelDataBufferReady(),
                status.sampleModelColourBufferReady(),
                status.sampleAtlasTextureReady(),
                status.modelDataBindingReady(),
                status.modelColourBindingReady(),
                status.atlasSamplerBindingReady(),
                status.usesSampleSetModelData(),
                status.usesRealModelStore(),
                status.formalTexturedShaderReady(),
                status.formalModelBridgeReady(),
                status.sampleModelCount(),
                status.sampleModelIds(),
                status.maxModelId(),
                status.modelDataBufferId(),
                status.modelColourBufferId(),
                status.modelValidityBufferId(),
                status.atlasTextureObjectId(),
                status.fullAtlasTextureCreated(),
                status.atlasWidth(),
                status.atlasHeight(),
                status.modelDataBindingIndex(),
                status.modelColourBindingIndex(),
                status.modelValidityBindingIndex(),
                status.atlasTextureUnit(),
                status.usesSparseModelIdIndex(),
                status.formalShaderInputBridgeStale(),
                status.lastReloadInvalidatedFormalShaderInputBridge(),
                status.lastAuditOk(),
                status.lastAuditError(),
                status.lastAuditDurationMs(),
                status.modelDataBufferMatch(),
                status.modelColourBufferMatch(),
                status.atlasUploadStillValid(),
                status.invalidModelRecords(),
                status.invalidColourRecords(),
                status.missingAtlasTiles()
        );
    }

    private static String formatTexturedDebugQuadStatus(ForgeTexturedDebugStats status) {
        return String.format(
                "stage=%s enabled=%s actualDrawEnabled=%s shaderCompiled=%s programCreated=%s lastShaderError=%s sampleReady=%s atlasTextureReady=%s atlasPixelsUploaded=%s sampleModelId=%d sourceBlockStateId=%d sourceBlockState=\"%s\" sourceSprite=%s sourceSpriteAtlas=%s sourceFace=%s uvMin=%s uvMax=%s lastFrameDrawCalls=%d lastFrameVertices=%d drawCallsIssued=%d verticesDrawn=%d lastGlError=%s lastGlErrorStage=%s glErrorCount=%d stateRestoreFailures=%d lastStateRestoreError=%s lastRenderSkippedReason=%s texturedDebugQuadStale=%s formalTexturedShaderReady=%s formalModelBridgeReady=%s renderer=tiny-textured-debug-quad formalRenderer=false mdicRenderer=false",
                status.stage(),
                status.enabled(),
                status.actualDrawEnabled(),
                status.shaderCompiled(),
                status.programCreated(),
                status.lastShaderError(),
                status.sampleReady(),
                status.atlasTextureReady(),
                status.atlasPixelsUploaded(),
                status.sampleModelId(),
                status.sourceBlockStateId(),
                status.sourceBlockState(),
                status.sourceSprite(),
                status.sourceSpriteAtlas(),
                status.sourceFace(),
                status.uvMin(),
                status.uvMax(),
                status.lastFrameDrawCalls(),
                status.lastFrameVertices(),
                status.drawCallsIssued(),
                status.verticesDrawn(),
                status.lastGlError(),
                status.lastGlErrorStage(),
                status.glErrorCount(),
                status.stateRestoreFailures(),
                status.lastStateRestoreError(),
                status.lastRenderSkippedReason(),
                status.texturedDebugQuadStale(),
                status.formalTexturedShaderReady(),
                status.formalModelBridgeReady()
        );
    }

    private static String formatTexturedReadbackStatus(ForgeTexturedReadbackStats status) {
        return String.format(
                "stage=%s enabled=%s actualDrawEnabled=%s shaderCompiled=%s programCreated=%s lastShaderError=%s sampleReady=%s atlasTextureReady=%s atlasPixelsUploaded=%s geometryHeapReady=%s metadataReady=%s geometryBacked=%s fallbackFixedQuad=%s sampleModelId=%d sourceBlockStateId=%d sourceBlockState=\"%s\" sourceSprite=%s sourceSpriteAtlas=%s matchingSections=%d matchingRecords=%d builtQuads=%d builtVertices=%d uploadedVertices=%d lastFrameDrawCalls=%d lastFrameVertices=%d drawCallsIssued=%d verticesDrawn=%d lastGlError=%s lastGlErrorStage=%s glErrorCount=%d stateRestoreFailures=%d lastStateRestoreError=%s lastRenderSkippedReason=%s texturedReadbackStale=%s formalTexturedShaderReady=%s formalModelBridgeReady=%s renderer=textured-gl-heap-readback debugRenderer=true formalRenderer=false mdicRenderer=false",
                status.stage(),
                status.enabled(),
                status.actualDrawEnabled(),
                status.shaderCompiled(),
                status.programCreated(),
                status.lastShaderError(),
                status.sampleReady(),
                status.atlasTextureReady(),
                status.atlasPixelsUploaded(),
                status.geometryHeapReady(),
                status.metadataReady(),
                status.geometryBacked(),
                status.fallbackFixedQuad(),
                status.sampleModelId(),
                status.sourceBlockStateId(),
                status.sourceBlockState(),
                status.sourceSprite(),
                status.sourceSpriteAtlas(),
                status.matchingSections(),
                status.matchingRecords(),
                status.builtQuads(),
                status.builtVertices(),
                status.uploadedVertices(),
                status.lastFrameDrawCalls(),
                status.lastFrameVertices(),
                status.drawCallsIssued(),
                status.verticesDrawn(),
                status.lastGlError(),
                status.lastGlErrorStage(),
                status.glErrorCount(),
                status.stateRestoreFailures(),
                status.lastStateRestoreError(),
                status.lastRenderSkippedReason(),
                status.texturedReadbackStale(),
                status.formalTexturedShaderReady(),
                status.formalModelBridgeReady()
        );
    }

    private static String formatTexturedMdicDebugStatus(ForgeTexturedMdicDebugStats status) {
        return String.format(
                "stage=%s enabled=%s actualDrawEnabled=%s shaderCompiled=%s programCreated=%s lastShaderError=%s sampleReady=%s atlasTextureReady=%s atlasPixelsUploaded=%s geometryHeapReady=%s metadataReady=%s mdicCommandReady=%s drawMode=%s effectiveDrawMode=%s inputMode=%s usesModelDataLookup=%s usesSampleModelValidityTable=%s elementsIndirectCountSupported=%s unsupportedReason=%s shaderSideModelFilter=%s cpuPrefilteredCommands=%s notPerformanceRepresentative=%s notFormalCmdgen=%s multiModelTexturedMdicDebug=%s sampleModelCount=%d sampleModelIds=%s sampleModelId=%d sourceBlockStateId=%d sourceBlockState=\"%s\" sourceSprite=%s sourceSpriteAtlas=%s matchingRecordsKnown=%s matchingRecords=%d commandCount=%d lastFrameApiDrawCalls=%d lastFrameLogicalCommands=%d lastFrameVertices=%d drawCallsIssued=%d verticesDrawn=%d lastGlError=%s lastGlErrorStage=%s glErrorCount=%d stateRestoreFailures=%d lastStateRestoreError=%s lastRenderSkippedReason=%s visibleTexturedMdicGeometry=%s texturedMdicDebugStale=%s notFormalShader=%s formalTexturedShaderReady=%s formalModelBridgeReady=%s renderer=textured-mdic-debug-one-or-sample-set debugRenderer=true formalRenderer=false mdicRenderer=false",
                status.stage(),
                status.enabled(),
                status.actualDrawEnabled(),
                status.shaderCompiled(),
                status.programCreated(),
                status.lastShaderError(),
                status.sampleReady(),
                status.atlasTextureReady(),
                status.atlasPixelsUploaded(),
                status.geometryHeapReady(),
                status.metadataReady(),
                status.mdicCommandReady(),
                status.drawMode(),
                status.effectiveDrawMode(),
                status.inputMode(),
                status.usesModelDataLookup(),
                status.usesSampleModelValidityTable(),
                status.elementsIndirectCountSupported(),
                status.unsupportedReason(),
                status.shaderSideModelFilter(),
                status.cpuPrefilteredCommands(),
                status.notPerformanceRepresentative(),
                status.notFormalCmdgen(),
                status.multiModelTexturedMdicDebug(),
                status.sampleModelCount(),
                status.sampleModelIds(),
                status.sampleModelId(),
                status.sourceBlockStateId(),
                status.sourceBlockState(),
                status.sourceSprite(),
                status.sourceSpriteAtlas(),
                status.matchingRecordsKnown(),
                status.matchingRecords(),
                status.commandCount(),
                status.lastFrameApiDrawCalls(),
                status.lastFrameLogicalCommands(),
                status.lastFrameVertices(),
                status.drawCallsIssued(),
                status.verticesDrawn(),
                status.lastGlError(),
                status.lastGlErrorStage(),
                status.glErrorCount(),
                status.stateRestoreFailures(),
                status.lastStateRestoreError(),
                status.lastRenderSkippedReason(),
                status.visibleTexturedMdicGeometry(),
                status.texturedMdicDebugStale(),
                status.notFormalShader(),
                status.formalTexturedShaderReady(),
                status.formalModelBridgeReady()
        );
    }

    private static String formatModelSampleSetStatus(ForgeModelSampleSetStats status) {
        return String.format(
                "stage=%s buildRuns=%d clearRuns=%d auditRuns=%d auditFailures=%d lastBuildError=%s lastBuildDurationMs=%.2f sampleSetReady=%s requestedSamples=%d acceptedSamples=%d rejectedSamples=%d sampleModelIds=%s sampleBlockStates=%s sampleSprites=%s solidSamples=%d fluidRejected=%d translucentRejected=%d missingModelRejected=%d missingSpriteRejected=%d unsupportedRejected=%d recordBytesPerModel=%d totalModelRecordBytes=%d formalLayoutCompatible=%s formalModelBridgeReady=%s sampleSetStale=%s lastReloadInvalidatedSampleSet=%s generation=%d dimension=%s bufferStale=%s lastAuditOk=%s lastAuditError=%s lastAuditDurationMs=%.2f lastAuditedRecords=%d lastAuditedBytes=%d lastInvalidRecords=%d lastModelDataBufferMatch=%s lastModelColourBufferMatch=%s draw=false renderer=none",
                status.stage(),
                status.buildRuns(),
                status.clearRuns(),
                status.auditRuns(),
                status.auditFailures(),
                status.lastBuildError(),
                status.lastBuildDurationMs(),
                status.sampleSetReady(),
                status.requestedSamples(),
                status.acceptedSamples(),
                status.rejectedSamples(),
                status.sampleModelIds(),
                status.sampleBlockStates(),
                status.sampleSprites(),
                status.solidSamples(),
                status.fluidRejected(),
                status.translucentRejected(),
                status.missingModelRejected(),
                status.missingSpriteRejected(),
                status.unsupportedRejected(),
                status.recordBytesPerModel(),
                status.totalModelRecordBytes(),
                status.formalLayoutCompatible(),
                status.formalModelBridgeReady(),
                status.sampleSetStale(),
                status.lastReloadInvalidatedSampleSet(),
                status.generation(),
                status.dimension(),
                status.bufferStale(),
                status.lastAuditOk(),
                status.lastAuditError(),
                status.lastAuditDurationMs(),
                status.lastAuditedRecords(),
                status.lastAuditedBytes(),
                status.lastInvalidRecords(),
                status.lastModelDataBufferMatch(),
                status.lastModelColourBufferMatch()
        );
    }

    private static String formatModelAtlasSampleSetUploadStatus(ForgeModelAtlasSampleSetUploadStats status) {
        return String.format(
                "atlasSampleSetUploadStage=%s uploadRuns=%d uploadFailures=%d auditRuns=%d auditFailures=%d clearRuns=%d lastUploadOk=%s lastUploadError=%s lastUploadDurationMs=%.2f sampleSetAtlasUploadReady=%s atlasTextureObjectCreated=%s atlasTextureObjectId=%d fullAtlasTextureCreated=%s debugSmallAtlasFallback=%s atlasWidth=%d atlasHeight=%d actualTextureWidth=%d actualTextureHeight=%d atlasFormat=%s atlasPixelsUploaded=%s uploadedModelCount=%d uploadedFaceCount=%d uploadedPixels=%d missingFaces=%d pixelMismatches=%d sampleSetUploadReady=%s realAtlasPixelUploadReady=%s formalTextureAtlasReady=%s formalTexturedShaderReady=%s formalModelBridgeReady=%s atlasSampleSetStale=%s lastReloadInvalidatedAtlasSampleSet=%s lastAuditOk=%s lastAuditError=%s lastAuditDurationMs=%.2f lastAuditedModels=%d lastUploadedFaces=%d lastUploadedPixels=%d lastMissingFaces=%d lastPixelMismatches=%d lastAtlasReadbackOk=%s sampleModelIds=%s sampleBlockStates=%s sampleSprites=%s firstSampleModelId=%d firstSourceBlockState=\"%s\" firstSourceSprite=%s firstFace0Tile=%s firstFace0Checksum=%s draw=false renderer=none",
                status.stage(),
                status.uploadRuns(),
                status.uploadFailures(),
                status.auditRuns(),
                status.auditFailures(),
                status.clearRuns(),
                status.lastUploadOk(),
                status.lastUploadError(),
                status.lastUploadDurationMs(),
                status.sampleSetAtlasUploadReady(),
                status.atlasTextureObjectCreated(),
                status.atlasTextureObjectId(),
                status.fullAtlasTextureCreated(),
                status.debugSmallAtlasFallback(),
                status.atlasWidth(),
                status.atlasHeight(),
                status.actualTextureWidth(),
                status.actualTextureHeight(),
                status.atlasFormat(),
                status.atlasPixelsUploaded(),
                status.uploadedModelCount(),
                status.uploadedFaceCount(),
                status.uploadedPixels(),
                status.missingFaces(),
                status.pixelMismatches(),
                status.sampleSetUploadReady(),
                status.realAtlasPixelUploadReady(),
                status.formalTextureAtlasReady(),
                status.formalTexturedShaderReady(),
                status.formalModelBridgeReady(),
                status.atlasSampleSetStale(),
                status.lastReloadInvalidatedAtlasSampleSet(),
                status.lastAuditOk(),
                status.lastAuditError(),
                status.lastAuditDurationMs(),
                status.lastAuditedModels(),
                status.lastUploadedFaces(),
                status.lastUploadedPixels(),
                status.lastMissingFaces(),
                status.lastPixelMismatches(),
                status.lastAtlasReadbackOk(),
                status.sampleModelIds(),
                status.sampleBlockStates(),
                status.sampleSprites(),
                status.firstSampleModelId(),
                status.firstSourceBlockState(),
                status.firstSourceSprite(),
                status.firstFace0Tile(),
                status.firstFace0Checksum()
        );
    }

    private static String formatModelAtlasUploadStatus(ForgeModelAtlasUploadStats status) {
        return String.format(
                "atlasUploadStage=%s uploadRuns=%d uploadFailures=%d auditRuns=%d auditFailures=%d clearRuns=%d lastUploadOk=%s lastUploadError=%s lastUploadDurationMs=%.2f atlasTextureObjectCreated=%s atlasTextureObjectId=%d fullAtlasTextureCreated=%s debugSmallAtlasFallback=%s atlasWidth=%d atlasHeight=%d actualTextureWidth=%d actualTextureHeight=%d atlasFormat=%s atlasPixelsUploaded=%s uploadedFaces=%d uploadedPixels=%d missingFaces=%d sampleAtlasPixelsUploaded=%s realAtlasPixelUploadReady=%s formalTextureAtlasReady=%s formalTexturedShaderReady=%s formalModelBridgeReady=%s atlasPixelsStale=%s lastReloadInvalidatedAtlasPixels=%s lastAuditOk=%s lastAuditError=%s lastAuditDurationMs=%.2f lastUploadedFaces=%d lastUploadedPixels=%d lastMissingFaces=%d lastPixelMismatches=%d lastAtlasReadbackOk=%s sampleModelId=%d sourceBlockStateId=%d sourceBlockState=\"%s\" sourceSprite=%s sourceSpriteAtlas=%s face0Tile=%s face0Checksum=%s face1Tile=%s face1Checksum=%s face2Tile=%s face2Checksum=%s face3Tile=%s face3Checksum=%s face4Tile=%s face4Checksum=%s face5Tile=%s face5Checksum=%s draw=false renderer=none",
                status.stage(),
                status.uploadRuns(),
                status.uploadFailures(),
                status.auditRuns(),
                status.auditFailures(),
                status.clearRuns(),
                status.lastUploadOk(),
                status.lastUploadError(),
                status.lastUploadDurationMs(),
                status.atlasTextureObjectCreated(),
                status.atlasTextureObjectId(),
                status.fullAtlasTextureCreated(),
                status.debugSmallAtlasFallback(),
                status.atlasWidth(),
                status.atlasHeight(),
                status.actualTextureWidth(),
                status.actualTextureHeight(),
                status.atlasFormat(),
                status.atlasPixelsUploaded(),
                status.uploadedFaces(),
                status.uploadedPixels(),
                status.missingFaces(),
                status.sampleAtlasPixelsUploaded(),
                status.realAtlasPixelUploadReady(),
                status.formalTextureAtlasReady(),
                status.formalTexturedShaderReady(),
                status.formalModelBridgeReady(),
                status.atlasPixelsStale(),
                status.lastReloadInvalidatedAtlasPixels(),
                status.lastAuditOk(),
                status.lastAuditError(),
                status.lastAuditDurationMs(),
                status.lastUploadedFaces(),
                status.lastUploadedPixels(),
                status.lastMissingFaces(),
                status.lastPixelMismatches(),
                status.lastAtlasReadbackOk(),
                status.sampleModelId(),
                status.sourceBlockStateId(),
                status.sourceBlockState(),
                status.sourceSprite(),
                status.sourceSpriteAtlas(),
                status.face0Tile(),
                status.face0Checksum(),
                status.face1Tile(),
                status.face1Checksum(),
                status.face2Tile(),
                status.face2Checksum(),
                status.face3Tile(),
                status.face3Checksum(),
                status.face4Tile(),
                status.face4Checksum(),
                status.face5Tile(),
                status.face5Checksum()
        );
    }

    private static String formatModelAtlasSkeletonStatus(ForgeModelAtlasStats status) {
        return String.format(
                "atlasStage=%s buildRuns=%d clearRuns=%d auditRuns=%d auditFailures=%d lastBuildError=%s lastBuildDurationMs=%.2f atlasSkeletonReady=%s atlasLayoutReady=%s atlasOwnershipReady=%s atlasTextureObjectCreated=%s atlasSamplerReady=%s atlasTextureObjectId=%d atlasSamplerId=%d modelTextureSize=%d modelGridWidth=%d modelGridHeight=%d atlasWidth=%d atlasHeight=%d facesPerModelX=%d facesPerModelY=%d faceTileOrderKnown=%s atlasPixelsUploaded=%s realTextureDataReady=%s customAtlasUploadReady=%s formalTextureAtlasReady=%s formalModelBridgeReady=%s atlasSkeletonStale=%s lastReloadInvalidatedAtlasSkeleton=%s layoutVersion=%s lastAuditOk=%s lastAuditError=%s lastAuditDurationMs=%.2f invalidLayout=%d invalidModelCoordinate=%d invalidFaceTileCoordinate=%d sampleModelId=%d sourceBlockState=\"%s\" sourceSprite=%s minecraftSpriteAtlas=%s voxyAtlasBaseX=%d voxyAtlasBaseY=%d face0Tile=%s face1Tile=%s face2Tile=%s face3Tile=%s face4Tile=%s face5Tile=%s draw=false atlasUpload=false renderer=none",
                status.stage(),
                status.buildRuns(),
                status.clearRuns(),
                status.auditRuns(),
                status.auditFailures(),
                status.lastBuildError(),
                status.lastBuildDurationMs(),
                status.atlasSkeletonReady(),
                status.atlasLayoutReady(),
                status.atlasOwnershipReady(),
                status.atlasTextureObjectCreated(),
                status.atlasSamplerReady(),
                status.atlasTextureObjectId(),
                status.atlasSamplerId(),
                status.modelTextureSize(),
                status.modelGridWidth(),
                status.modelGridHeight(),
                status.atlasWidth(),
                status.atlasHeight(),
                status.facesPerModelX(),
                status.facesPerModelY(),
                status.faceTileOrderKnown(),
                status.atlasPixelsUploaded(),
                status.realTextureDataReady(),
                status.customAtlasUploadReady(),
                status.formalTextureAtlasReady(),
                status.formalModelBridgeReady(),
                status.atlasSkeletonStale(),
                status.lastReloadInvalidatedAtlasSkeleton(),
                status.layoutVersion(),
                status.lastAuditOk(),
                status.lastAuditError(),
                status.lastAuditDurationMs(),
                status.invalidLayout(),
                status.invalidModelCoordinate(),
                status.invalidFaceTileCoordinate(),
                status.sampleModelId(),
                status.sourceBlockState(),
                status.sourceSprite(),
                status.minecraftSpriteAtlas(),
                status.voxyAtlasBaseX(),
                status.voxyAtlasBaseY(),
                status.face0Tile(),
                status.face1Tile(),
                status.face2Tile(),
                status.face3Tile(),
                status.face4Tile(),
                status.face5Tile()
        );
    }

    private static String formatRealModelStoreSampleStatus(ForgeRealModelStoreSampleStats status) {
        return String.format(
                "realModelRecordStage=%s buildRuns=%d clearRuns=%d auditRuns=%d auditFailures=%d lastBuildError=%s lastBuildDurationMs=%.2f realModelRecordSampleReady=%s realModelRecordSampleBufferReady=%s realModelRecordSampleCount=%d realModelRecordBytes=%d realModelRecordLayoutVersion=%s formalLayoutCompatible=%s sourceModelId=%d sourceBlockStateId=%d sourceBlockState=\"%s\" sourceRenderLayer=%s sourceSprite=%s sourceSpriteAtlas=%s sourceQuadCount=%d realFaceDataSampleReady=%s faceDataEncoded=%s faceDataFormalCompatible=%s realSpriteUvSampleReady=%s modelColourSampleReady=%s modelColourTintedFaces=%d modelColourUntintedFaces=%d biomeTintReady=%s realTextureAtlasUploadReady=%s formalTexturedShaderReady=%s formalModelBridgeReady=%s realModelRecordSampleStale=%s lastReloadInvalidatedRealModelRecordSample=%s generation=%d dimension=%s bufferStale=%s lastAuditOk=%s lastAuditError=%s lastAuditDurationMs=%.2f lastAuditedRecords=%d lastAuditedBytes=%d lastInvalidRecords=%d lastModelDataBufferMatch=%s lastModelColourBufferMatch=%s faceCount=%d sampleFace=%s direction=%s uMin/vMin/uMax/vMax=%s/%s tintIndex=%d hasTint=%s cullDirection=%s verticesLength=%d sampleFaceDataWord=0x%08X draw=false atlasUpload=false renderer=none",
                status.stage(),
                status.buildRuns(),
                status.clearRuns(),
                status.auditRuns(),
                status.auditFailures(),
                status.lastBuildError(),
                status.lastBuildDurationMs(),
                status.realModelRecordSampleReady(),
                status.realModelRecordSampleBufferReady(),
                status.realModelRecordSampleCount(),
                status.realModelRecordBytes(),
                status.realModelRecordLayoutVersion(),
                status.formalLayoutCompatible(),
                status.sourceModelId(),
                status.sourceBlockStateId(),
                status.sourceBlockState(),
                status.sourceRenderLayer(),
                status.sourceSprite(),
                status.sourceSpriteAtlas(),
                status.sourceQuadCount(),
                status.realFaceDataSampleReady(),
                status.faceDataEncoded(),
                status.faceDataFormalCompatible(),
                status.realSpriteUvSampleReady(),
                status.modelColourSampleReady(),
                status.modelColourTintedFaces(),
                status.modelColourUntintedFaces(),
                status.biomeTintReady(),
                status.realTextureAtlasUploadReady(),
                status.formalTexturedShaderReady(),
                status.formalModelBridgeReady(),
                status.realModelRecordSampleStale(),
                status.lastReloadInvalidatedRealModelRecordSample(),
                status.generation(),
                status.dimension(),
                status.bufferStale(),
                status.lastAuditOk(),
                status.lastAuditError(),
                status.lastAuditDurationMs(),
                status.lastAuditedRecords(),
                status.lastAuditedBytes(),
                status.lastInvalidRecords(),
                status.lastModelDataBufferMatch(),
                status.lastModelColourBufferMatch(),
                status.faceCount(),
                status.sampleFaceDirection(),
                status.sampleFaceDirection(),
                status.sampleFaceUvMin(),
                status.sampleFaceUvMax(),
                status.sampleFaceTintIndex(),
                status.sampleFaceHasTint(),
                status.sampleFaceCullDirection(),
                status.sampleFaceVerticesLength(),
                status.sampleFaceDataWord()
        );
    }

    private static String formatBakedModelBridgeStatus(ForgeBakedModelBridgeStats status) {
        return String.format(
                "bakedModelStage=%s checkRuns=%d clearRuns=%d auditRuns=%d auditFailures=%d lastCheckError=%s lastCheckDurationMs=%.2f lastAuditOk=%s lastAuditError=%s lastAuditDurationMs=%.2f placeholderModelIdsPresent=%s canMapModelIdToBlockState=%s bakedModelBridgeReady=%s bakedModelSamplesReady=%s sampledModelIds=%d sampledBlockStates=%d sampledBakedModels=%d sampledQuads=%d sampledSprites=%d missingBakedModels=%d missingSprites=%d fluidLikeSamples=%d emptyModelSamples=%d unsupportedSamples=%d renderLayerReadable=%s spriteAtlasReadable=%s spriteUvReadable=%s minecraftBlockAtlasAccessible=%s blockAtlasLocation=%s customAtlasOwnershipReady=%s customAtlasUploadReady=%s formalTextureAtlasReady=%s formalModelBridgeReady=%s bakedModelSamplesStale=%s spriteSamplesStale=%s lastReloadInvalidatedBakedModelSamples=%s sampleModelId=%d sampleBlockStateId=%d sampleBlockState=\"%s\" fluidLike=%s bakedModelClass=%s renderLayer=%s quadCount=%d quadDirection=%s quadTintIndex=%d quadHasTint=%s quadSpriteName=%s quadSpriteAtlas=%s quadUvMin=%s quadUvMax=%s quadCullDirection=%s quadVerticesLength=%d hasRealModelMetadata=%s hasAtlasSprite=%s hasAtlasUpload=%s draw=false atlasUpload=false renderer=none",
                status.stage(),
                status.checkRuns(),
                status.clearRuns(),
                status.auditRuns(),
                status.auditFailures(),
                status.lastCheckError(),
                status.lastCheckDurationMs(),
                status.lastAuditOk(),
                status.lastAuditError(),
                status.lastAuditDurationMs(),
                status.placeholderModelIdsPresent(),
                status.canMapModelIdToBlockState(),
                status.bakedModelBridgeReady(),
                status.bakedModelSamplesReady(),
                status.sampledModelIds(),
                status.sampledBlockStates(),
                status.sampledBakedModels(),
                status.sampledQuads(),
                status.sampledSprites(),
                status.missingBakedModels(),
                status.missingSprites(),
                status.fluidLikeSamples(),
                status.emptyModelSamples(),
                status.unsupportedSamples(),
                status.renderLayerReadable(),
                status.spriteAtlasReadable(),
                status.spriteUvReadable(),
                status.minecraftBlockAtlasAccessible(),
                status.blockAtlasLocation(),
                status.customAtlasOwnershipReady(),
                status.customAtlasUploadReady(),
                status.formalTextureAtlasReady(),
                status.formalModelBridgeReady(),
                status.bakedModelSamplesStale(),
                status.spriteSamplesStale(),
                status.lastReloadInvalidatedBakedModelSamples(),
                status.sampleModelId(),
                status.sampleBlockStateId(),
                status.sampleBlockState(),
                status.sampleFluidLike(),
                status.sampleBakedModelClass(),
                status.sampleRenderLayer(),
                status.sampleQuadCount(),
                status.sampleQuadDirection(),
                status.sampleQuadTintIndex(),
                status.sampleQuadHasTint(),
                status.sampleQuadSpriteName(),
                status.sampleQuadSpriteAtlas(),
                status.sampleQuadUvMin(),
                status.sampleQuadUvMax(),
                status.sampleQuadCullDirection(),
                status.sampleQuadVerticesLength(),
                status.sampleHasRealModelMetadata(),
                status.sampleHasAtlasSprite(),
                status.sampleHasAtlasUpload()
        );
    }

    private static String formatModelBridgeResourceReloadStatus(ForgeModelBridgeResourceReloadStats status) {
        return String.format(
                "reloadLifecycleSkeletonReady=%s realResourceReloadListenerReady=%s resourceReloadReady=%s reloadEventsSeen=%d realReloadEventsSeen=%d simulatedReloadEventsSeen=%d lastReloadSource=%s lastReloadStartedAt=%s lastReloadFinishedAt=%s lastReloadThread=%s reloadCleanupScheduled=%s reloadCleanupOnRenderThread=%s reloadCleanupCompleted=%s reloadCleanupFailures=%d modelBridgeInvalidatedOnReload=%s placeholderBuffersInvalidatedOnReload=%s placeholderBuffersStale=%s realModelStoreStale=%s textureAtlasStale=%s formalShaderInputsStale=%s bakedModelSamplesStale=%s spriteSamplesStale=%s lastReloadInvalidatedBakedModelSamples=%s realModelRecordSampleStale=%s lastReloadInvalidatedRealModelRecordSample=%s sampleSetStale=%s atlasSkeletonStale=%s lastReloadInvalidatedAtlasSkeleton=%s atlasPixelsStale=%s lastReloadInvalidatedAtlasPixels=%s formalShaderInputBridgeStale=%s texturedDebugQuadStale=%s texturedReadbackStale=%s texturedMdicDebugStale=%s lastReloadReason=%s formalModelBridgeReady=false realTextureAtlasUpload=false",
                status.reloadLifecycleSkeletonReady(),
                status.realResourceReloadListenerReady(),
                status.resourceReloadReady(),
                status.reloadEventsSeen(),
                status.realReloadEventsSeen(),
                status.simulatedReloadEventsSeen(),
                status.lastReloadSource(),
                status.lastReloadStartedAt(),
                status.lastReloadFinishedAt(),
                status.lastReloadThread(),
                status.reloadCleanupScheduled(),
                status.reloadCleanupOnRenderThread(),
                status.reloadCleanupCompleted(),
                status.reloadCleanupFailures(),
                status.modelBridgeInvalidatedOnReload(),
                status.placeholderBuffersInvalidatedOnReload(),
                status.placeholderBuffersStale(),
                status.realModelStoreStale(),
                status.textureAtlasStale(),
                status.formalShaderInputsStale(),
                status.bakedModelSamplesStale(),
                status.spriteSamplesStale(),
                status.lastReloadInvalidatedBakedModelSamples(),
                status.realModelRecordSampleStale(),
                status.lastReloadInvalidatedRealModelRecordSample(),
                status.sampleSetStale(),
                status.atlasSkeletonStale(),
                status.lastReloadInvalidatedAtlasSkeleton(),
                status.atlasPixelsStale(),
                status.lastReloadInvalidatedAtlasPixels(),
                status.formalShaderInputBridgeStale(),
                status.texturedDebugQuadStale(),
                status.texturedReadbackStale(),
                status.texturedMdicDebugStale(),
                status.lastReloadReason()
        );
    }

    private static int meshCacheStatus(CommandSourceStack source) {
        var cache = ForgeVoxyInstance.INSTANCE.getCpuMeshCache();
        var status = cache.createStatusSnapshot();
        var minecraft = Minecraft.getInstance();
        String bounds = "bounds=none";
        String currentDimension = "none";
        String playerChunk = "none";
        if (minecraft.level != null && minecraft.player != null) {
            String dimension = minecraft.level.dimension().location().toString();
            int chunkX = minecraft.player.chunkPosition().x;
            int chunkZ = minecraft.player.chunkPosition().z;
            currentDimension = dimension;
            playerChunk = chunkX + "," + chunkZ;
            bounds = formatBounds(cache.createBoundsSnapshot(dimension, chunkX, chunkZ));
        }

        var ingestStatus = ForgeVoxyInstance.INSTANCE.getChunkIngestManager().createStatusSnapshot();
        var renderStats = ForgeVoxyInstance.INSTANCE.getDebugMeshRenderer().getLastFrameStats();
        var meshBuildStatus = ForgeVoxyInstance.INSTANCE.getCpuMeshBuildManager().createStatusSnapshot();
        var gpuCacheStatus = ForgeVoxyInstance.INSTANCE.getGpuMeshCache().createStatusSnapshot();
        var gpuUploadStatus = ForgeVoxyInstance.INSTANCE.getGpuMeshUploadManager().getLastStatus();
        var gpuRenderStats = ForgeVoxyInstance.INSTANCE.getSimpleGpuMeshRenderer().getLastFrameStats();
        var presetStatus = ForgeVoxyRuntimeOverrides.createStatusSnapshot();
        String message = String.format(
                "Voxy debug pipeline: preset=%s overrides=%s engineConfig=%s engine=%s autoIngest=%s autoBuild=%s render=%s simpleGpu=%s gpuSource=%s sourceRole=%s recommendedSource=%s fallbackSource=%s dim=%s playerChunk=%s ingestQueue=%d ingestedRecords=%d avgIngestMs=%.2f buildQueue=%d builtRecords=%d failedRecords=%d avgBuildMs=%.2f lastBuildMs=%.2f cache=%d/%d vertices=%d quads=%d bytes=%d dimensions=%s layers=%s gpuBuffers=%d/%d gpuPending=%d gpuRendered=%d gpuChunks=%d gpuVertices=%d gpuLimited=%d gpuSkippedNear=%d gpuSkippedLoaded=%d gpuSkippedLoadedState=%d gpuSkippedRenderDistance=%d gpuSkippedFar=%d gpuAvgRenderMs=%.2f gpuMinDistance=%d gpuMaxDistance=%d gpuRenderLoaded=%s gpuSkipMode=%s gpuLoadedMargin=%d gpuKeepCached=%s maxRendered=%d ignoreDepth=%s alpha=%.2f verticalOffset=%.3f stage=%s %s %s",
                presetStatus.presetName(),
                presetStatus.hasOverrides(),
                ForgeVoxyRuntimeOverrides.enabledWorldEngineSkeleton(),
                meshBuildStatus.enginePresent(),
                ingestStatus.autoEnabled(),
                meshBuildStatus.autoEnabled(),
                ForgeVoxyRuntimeOverrides.enableDebugMeshRenderer(),
                ForgeVoxyRuntimeOverrides.enableSimpleGpuMeshRenderer(),
                ForgeGpuMeshUploadManager.getConfiguredSource(),
                describeMeshSource(ForgeGpuMeshUploadManager.getConfiguredSource()),
                RECOMMENDED_MESH_SOURCE,
                FALLBACK_MESH_SOURCE,
                currentDimension,
                playerChunk,
                ingestStatus.queuedChunks(),
                ingestStatus.ingestedChunks(),
                ingestStatus.averageMs(),
                meshBuildStatus.queuedChunks(),
                meshBuildStatus.builtChunks(),
                meshBuildStatus.failedChunks(),
                meshBuildStatus.averageMs(),
                meshBuildStatus.lastBuildMs(),
                status.entries(),
                status.maxEntries(),
                status.totalVertices(),
                status.totalQuads(),
                status.totalBytes(),
                status.dimensions(),
                status.layers(),
                gpuCacheStatus.buffers(),
                gpuCacheStatus.maxBuffers(),
                gpuUploadStatus.pendingUploads(),
                gpuRenderStats.renderedBuffers(),
                gpuRenderStats.renderedChunks(),
                gpuRenderStats.renderedVertices(),
                gpuRenderStats.limitedBuffers(),
                gpuRenderStats.skippedNear(),
                gpuRenderStats.skippedLoaded(),
                gpuRenderStats.skippedLoadedState(),
                gpuRenderStats.skippedRenderDistance(),
                gpuRenderStats.skippedByDistance(),
                gpuRenderStats.averageRenderMs(),
                ForgeSimpleGpuMeshRenderer.getConfiguredMinRenderDistanceChunks(),
                ForgeGpuMeshUploadManager.getConfiguredRenderDistanceChunks(),
                ForgeSimpleGpuMeshRenderer.shouldRenderLoadedChunks(),
                ForgeSimpleGpuMeshRenderer.getConfiguredLoadedChunkSkipMode(),
                ForgeSimpleGpuMeshRenderer.getConfiguredLoadedChunkMargin(),
                ForgeGpuMeshUploadManager.keepCachedChunks(),
                ForgeSimpleGpuMeshRenderer.getConfiguredMaxRenderedBuffers(),
                ForgeSimpleGpuMeshRenderer.shouldIgnoreDepth(),
                ForgeDebugMeshRenderer.getConfiguredAlpha(),
                ForgeDebugMeshRenderer.getConfiguredVerticalOffsetBlocks(),
                ForgeDebugMeshRenderer.getRenderStageName(),
                formatRenderStats(renderStats),
                bounds
        );
        source.sendSuccess(() -> Component.literal(message), false);
        return status.entries();
    }

    private static int gpuMeshStatus(CommandSourceStack source) {
        var cacheStatus = ForgeVoxyInstance.INSTANCE.getGpuMeshCache().createStatusSnapshot();
        var builtSectionStatus = ForgeVoxyInstance.INSTANCE.getVoxyGeometryCache().createStatusSnapshot();
        var builtSectionBuildStatus = ForgeVoxyInstance.INSTANCE.getBuiltSectionBuildManager().createStatusSnapshot();
        var readbackMeshStatus = ForgeVoxyInstance.INSTANCE.getGpuGeometryReadbackMeshCache().createStatusSnapshot();
        var readbackRefreshStatus = ForgeVoxyInstance.INSTANCE.getGpuGeometryReadbackMeshRefreshManager().createStatusSnapshot();
        var uploadStatus = ForgeVoxyInstance.INSTANCE.getGpuMeshUploadManager().getLastStatus();
        var renderStats = ForgeVoxyInstance.INSTANCE.getSimpleGpuMeshRenderer().getLastFrameStats();
        var minecraft = Minecraft.getInstance();
        String currentDimension = "none";
        String playerChunk = "none";
        LodVisibilityContext visibilityContext = createLodVisibilityContext(minecraft);
        int clientRenderDistance = visibilityContext == null ? -1 : visibilityContext.clientRenderDistance();
        int renderableCachedChunks = visibilityContext == null ? 0 : visibilityContext.visibility().renderableChunks();
        String noRenderReason = formatGpuNoRenderReason(renderStats, visibilityContext);
        String advice = formatLodVisibilityAdvice(cacheStatus, renderStats, visibilityContext);
        if (minecraft.level != null && minecraft.player != null) {
            currentDimension = minecraft.level.dimension().location().toString();
            playerChunk = minecraft.player.chunkPosition().x + "," + minecraft.player.chunkPosition().z;
        }
        String message = String.format(
                "Voxy simple GPU mesh: preset=%s overrides=%s enabled=%s source=%s sourceRole=%s uploadSource=%s recommendedSource=%s fallbackSource=%s engine=%s currentDim=%s playerChunk=%s vanillaRenderDistance=%d minDistance=%d maxDistance=%d renderLoadedChunks=%s skipMode=%s loadedMargin=%d keepCached=%s buffers=%d/%d renderableCachedChunks=%d vertices=%d quads=%d bytes=%d dimensions=%s layers=%s builtSectionCache=%d/%d builtSectionAuto=%s builtSectionBuiltRecords=%d builtSectionFailedRecords=%d builtSectionCandidates=%d glHeapReadbackCache=%d glHeapReadbackQuads=%d glHeapReadbackVertices=%d glHeapReadbackLastSource=%s glHeapReadbackLastError=%s glHeapReadbackAutoRefresh=%s glHeapReadbackRefreshRuns=%d glHeapReadbackRefreshSkipped=%d glHeapReadbackRefreshFailures=%d glHeapReadbackTicksUntilNextRefresh=%d glHeapReadbackLastRefreshReason=%s glHeapReadbackLastSkippedReason=%s glHeapReadbackLastRefreshDurationMs=%.2f glHeapReadbackLastRefreshSections=%d glHeapReadbackLastRefreshRecords=%d glHeapReadbackLastRefreshQuads=%d glHeapReadbackLastRefreshVertices=%d readbackCandidates=%d pendingUploads=%d uploadBudget=%d uploadedLast=%d failedLast=%d skippedTranslucentUpload=%d builtDoubleSidedAsSingle=%d skippedBuiltInvalid=%d cpuCandidates=%d cpuSkippedDistance=%d cpuLimited=%d sourceSwitchCount=%d orphanReconciled=%d orphanReconciledTotal=%d lastBuiltSectionDecodeMs=%.2f avgBuiltSectionDecodeMs=%.2f avgUploadMs=%.2f colorMode=%s ignoreDepth=%s verticalOffset=%.3f render=%s reason=%s noRenderReason=%s renderDim=%s candidateBuffers=%d renderedBuffers=%d renderedChunks=%d renderedVertices=%d skippedNear=%d skippedLoaded=%d skippedLoadedState=%d skippedRenderDistance=%d skippedFar=%d skippedDimension=%d skippedReleased=%d limitedRender=%d skippedTranslucentRender=%d lastRenderMs=%.2f avgRenderMs=%.2f maxRendered=%d alpha=%.2f stage=%s debugRenderer=%s advice=%s",
                ForgeVoxyRuntimeOverrides.presetName(),
                ForgeVoxyRuntimeOverrides.hasOverrides(),
                ForgeVoxyRuntimeOverrides.enableSimpleGpuMeshRenderer(),
                ForgeGpuMeshUploadManager.getConfiguredSource(),
                describeMeshSource(ForgeGpuMeshUploadManager.getConfiguredSource()),
                uploadStatus.source(),
                RECOMMENDED_MESH_SOURCE,
                FALLBACK_MESH_SOURCE,
                ForgeVoxyInstance.INSTANCE.getCurrentEngineOptional().isPresent(),
                currentDimension,
                playerChunk,
                clientRenderDistance,
                ForgeSimpleGpuMeshRenderer.getConfiguredMinRenderDistanceChunks(),
                ForgeGpuMeshUploadManager.getConfiguredRenderDistanceChunks(),
                ForgeSimpleGpuMeshRenderer.shouldRenderLoadedChunks(),
                ForgeSimpleGpuMeshRenderer.getConfiguredLoadedChunkSkipMode(),
                ForgeSimpleGpuMeshRenderer.getConfiguredLoadedChunkMargin(),
                ForgeGpuMeshUploadManager.keepCachedChunks(),
                cacheStatus.buffers(),
                cacheStatus.maxBuffers(),
                renderableCachedChunks,
                cacheStatus.totalVertices(),
                cacheStatus.totalQuads(),
                cacheStatus.totalBytes(),
                cacheStatus.dimensions(),
                cacheStatus.layers(),
                builtSectionStatus.entries(),
                builtSectionStatus.maxEntries(),
                builtSectionBuildStatus.autoEnabled(),
                builtSectionBuildStatus.builtChunks(),
                builtSectionBuildStatus.failedChunks(),
                uploadStatus.candidateBuiltSectionEntries(),
                readbackMeshStatus.cacheSections(),
                readbackMeshStatus.cacheQuads(),
                readbackMeshStatus.cacheVertices(),
                readbackMeshStatus.lastBuildResult().lastSource(),
                readbackMeshStatus.lastBuildResult().lastError(),
                readbackRefreshStatus.autoRefresh(),
                readbackRefreshStatus.refreshRuns(),
                readbackRefreshStatus.refreshSkipped(),
                readbackRefreshStatus.refreshFailures(),
                readbackRefreshStatus.ticksUntilNextRefresh(),
                readbackRefreshStatus.lastRefreshReason(),
                readbackRefreshStatus.lastRefreshSkippedReason(),
                readbackRefreshStatus.lastRefreshDurationMs(),
                readbackRefreshStatus.lastRefreshSections(),
                readbackRefreshStatus.lastRefreshRecords(),
                readbackRefreshStatus.lastRefreshQuads(),
                readbackRefreshStatus.lastRefreshVertices(),
                uploadStatus.candidateGlHeapReadbackEntries(),
                uploadStatus.pendingUploads(),
                uploadStatus.uploadBudget(),
                uploadStatus.uploadedThisFrame(),
                uploadStatus.failedThisFrame(),
                uploadStatus.skippedTranslucent(),
                uploadStatus.builtSectionDoubleSidedRecords(),
                uploadStatus.skippedBuiltSectionInvalid(),
                uploadStatus.candidateCpuEntries(),
                uploadStatus.skippedCpuByDistance(),
                uploadStatus.limitedCpuEntries(),
                uploadStatus.sourceSwitchCount(),
                uploadStatus.orphanReconciled(),
                uploadStatus.orphanReconciledTotal(),
                uploadStatus.lastBuiltSectionDecodeMs(),
                uploadStatus.averageBuiltSectionDecodeMs(),
                uploadStatus.averageUploadMs(),
                uploadStatus.useOriginalColors() ? "original" : "layer-debug",
                ForgeSimpleGpuMeshRenderer.shouldIgnoreDepth(),
                ForgeSimpleGpuMeshRenderer.getConfiguredVerticalOffsetBlocks(),
                renderStats.rendered(),
                renderStats.reason(),
                noRenderReason,
                renderStats.dimension(),
                renderStats.candidateBuffers(),
                renderStats.renderedBuffers(),
                renderStats.renderedChunks(),
                renderStats.renderedVertices(),
                renderStats.skippedNear(),
                renderStats.skippedLoaded(),
                renderStats.skippedLoadedState(),
                renderStats.skippedRenderDistance(),
                renderStats.skippedByDistance(),
                renderStats.skippedByDimension(),
                renderStats.skippedReleased(),
                renderStats.limitedBuffers(),
                renderStats.skippedTranslucent(),
                renderStats.lastRenderMs(),
                renderStats.averageRenderMs(),
                ForgeSimpleGpuMeshRenderer.getConfiguredMaxRenderedBuffers(),
                ForgeSimpleGpuMeshRenderer.getConfiguredAlpha(),
                ForgeSimpleGpuMeshRenderer.getRenderStageName(),
                ForgeVoxyRuntimeOverrides.enableDebugMeshRenderer(),
                advice
        );
        source.sendSuccess(() -> Component.literal(message), false);
        return cacheStatus.buffers();
    }

    private static int setGpuMeshSource(CommandSourceStack source, SimpleGpuMeshSource meshSource) {
        if (meshSource == SimpleGpuMeshSource.GL_HEAP_READBACK) {
            ForgeVoxyRuntimeOverrides.setGlHeapReadbackMeshSource();
            ForgeVoxyInstance.INSTANCE.getGpuGeometryVisualizationCache().clear();
            ForgeVoxyInstance.INSTANCE.getGpuGeometryReadbackDebugRenderer().clearStats();
            ForgeVoxyInstance.INSTANCE.getGpuGeometryReadbackMeshRefreshManager().requestRefresh(ForgeGpuGeometryReadbackMeshRefreshManager.REASON_SOURCE_SWITCH);
        } else {
            ForgeVoxyRuntimeOverrides.setSimpleGpuMeshSource(meshSource);
            ForgeVoxyInstance.INSTANCE.getGpuGeometryReadbackMeshCache().clear();
            ForgeVoxyInstance.INSTANCE.getGpuGeometryReadbackMeshRefreshManager().clear();
        }
        ForgeVoxyInstance.INSTANCE.getGpuMeshUploadManager().clear();
        String message = "Voxy simple GPU mesh source: runtime-only source set to " + meshSource
                + " (" + describeMeshSource(meshSource) + "). Recommended source for renderer migration is "
                + RECOMMENDED_MESH_SOURCE + "; fallback source is " + FALLBACK_MESH_SOURCE
                + ". GPU buffers were cleared and will be rebuilt from the selected source. "
                + (meshSource == SimpleGpuMeshSource.GL_HEAP_READBACK
                ? "Simple GPU renderer and overlay-friendly filters were enabled for GL heap readback debugging; auto refresh will run if enabled, or use /voxy geometry_gpu_readback_mesh_refresh_once after upload status shows uploadedSections > 0. "
                : "GL_HEAP_READBACK debug mesh cache was cleared to avoid source pollution. ")
                + "This was not written to toml.";
        source.sendSuccess(() -> Component.literal(message), false);
        return 1;
    }

    private static int lodVisibilityStatus(CommandSourceStack source) {
        var minecraft = Minecraft.getInstance();
        LodVisibilityContext context = createLodVisibilityContext(minecraft);
        if (context == null) {
            source.sendFailure(Component.literal("Voxy LoD visibility: no client world is active."));
            return 0;
        }

        var cacheStatus = ForgeVoxyInstance.INSTANCE.getGpuMeshCache().createStatusSnapshot();
        var renderStats = ForgeVoxyInstance.INSTANCE.getSimpleGpuMeshRenderer().getLastFrameStats();
        var visibility = context.visibility();
        String advice = formatLodVisibilityAdvice(cacheStatus, renderStats, context);
        String message = String.format(
                "Voxy LoD visibility: preset=%s overrides=%s dim=%s playerChunk=%d,%d vanillaRenderDistance=%d minDistance=%d maxDistance=%d renderLoadedChunks=%s skipMode=%s loadedMargin=%d keepCached=%s gpuBuffers=%d/%d cachedChunks=%d windowChunks=%d loadedStateChunks=%d chunksInsideVanillaDistance=%d chunksOutsideVanillaDistance=%d renderableChunks=%d candidateBuffers=%d renderableBuffers=%d skippedNear=%d skippedFar=%d skippedLoadedChunks=%d skippedLoadedBuffers=%d skippedLoadedStateChunks=%d skippedLoadedStateBuffers=%d skippedRenderDistanceChunks=%d skippedRenderDistanceBuffers=%d skippedDimension=%d skippedReleased=%d skippedTranslucent=%d nearestRenderable=%s farthestRenderable=%s advice=%s",
                ForgeVoxyRuntimeOverrides.presetName(),
                ForgeVoxyRuntimeOverrides.hasOverrides(),
                context.dimension(),
                context.playerChunkX(),
                context.playerChunkZ(),
                context.clientRenderDistance(),
                context.minDistance(),
                context.maxDistance(),
                context.renderLoadedChunks(),
                context.loadedChunkSkipMode(),
                context.loadedChunkMargin(),
                context.keepCachedChunks(),
                cacheStatus.buffers(),
                cacheStatus.maxBuffers(),
                visibility.cachedChunks(),
                visibility.chunksWithinDistanceWindow(),
                visibility.loadedStateChunks(),
                visibility.chunksWithinVanillaRenderDistance(),
                visibility.chunksOutsideVanillaRenderDistance(),
                visibility.renderableChunks(),
                visibility.candidateBuffers(),
                visibility.renderableBuffers(),
                visibility.skippedNear(),
                visibility.skippedFar(),
                visibility.skippedLoadedChunks(),
                visibility.skippedLoaded(),
                visibility.skippedLoadedStateChunks(),
                visibility.skippedLoadedState(),
                visibility.skippedRenderDistanceChunks(),
                visibility.skippedRenderDistance(),
                visibility.skippedByDimension(),
                visibility.skippedReleased(),
                visibility.skippedTranslucent(),
                formatDistance(visibility.nearestRenderableDistance()),
                formatDistance(visibility.farthestRenderableDistance()),
                advice
        );
        source.sendSuccess(() -> Component.literal(message), false);
        return visibility.renderableChunks();
    }

    private static int lodOverlayDebug(CommandSourceStack source) {
        return applyPresetOverlay(source);
    }

    private static int lodModeAdvice(CommandSourceStack source) {
        var minecraft = Minecraft.getInstance();
        LodVisibilityContext context = createLodVisibilityContext(minecraft);
        if (context == null) {
            source.sendFailure(Component.literal("Voxy LoD advice: no client world is active."));
            return 0;
        }

        var cacheStatus = ForgeVoxyInstance.INSTANCE.getGpuMeshCache().createStatusSnapshot();
        var renderStats = ForgeVoxyInstance.INSTANCE.getSimpleGpuMeshRenderer().getLastFrameStats();
        var visibility = context.visibility();
        String advice = formatLodVisibilityAdvice(cacheStatus, renderStats, context);
        String noRenderReason = formatGpuNoRenderReason(renderStats, context);
        String message = String.format(
                "Voxy LoD advice: preset=%s overrides=%s dim=%s playerChunk=%d,%d minecraftRenderDistance=%d lodMin=%d lodMax=%d renderLoadedChunks=%s skipMode=%s loadedMargin=%d cachedChunks=%d windowChunks=%d loadedStateChunks=%d chunksInsideVanillaDistance=%d chunksOutsideVanillaDistance=%d gpuBuffers=%d/%d renderableChunks=%d renderedBuffers=%d candidateBuffers=%d skippedLoadedChunks=%d skippedLoadedBuffers=%d skippedLoadedStateChunks=%d skippedLoadedStateBuffers=%d skippedRenderDistanceChunks=%d skippedRenderDistanceBuffers=%d skippedNear=%d skippedFar=%d noRenderReason=%s recommendation=%s",
                ForgeVoxyRuntimeOverrides.presetName(),
                ForgeVoxyRuntimeOverrides.hasOverrides(),
                context.dimension(),
                context.playerChunkX(),
                context.playerChunkZ(),
                context.clientRenderDistance(),
                context.minDistance(),
                context.maxDistance(),
                context.renderLoadedChunks(),
                context.loadedChunkSkipMode(),
                context.loadedChunkMargin(),
                visibility.cachedChunks(),
                visibility.chunksWithinDistanceWindow(),
                visibility.loadedStateChunks(),
                visibility.chunksWithinVanillaRenderDistance(),
                visibility.chunksOutsideVanillaRenderDistance(),
                cacheStatus.buffers(),
                cacheStatus.maxBuffers(),
                visibility.renderableChunks(),
                renderStats.renderedBuffers(),
                renderStats.candidateBuffers(),
                visibility.skippedLoadedChunks(),
                visibility.skippedLoaded(),
                visibility.skippedLoadedStateChunks(),
                visibility.skippedLoadedState(),
                visibility.skippedRenderDistanceChunks(),
                visibility.skippedRenderDistance(),
                visibility.skippedNear(),
                visibility.skippedFar(),
                noRenderReason,
                advice
        );
        source.sendSuccess(() -> Component.literal(message), false);
        return visibility.renderableChunks();
    }

    private static int applyPresetOff(CommandSourceStack source) {
        ForgeVoxyRuntimeOverrides.applyOffPreset();
        clearRuntimePipeline();
        ForgeVoxyInstance.INSTANCE.getFormalRendererManager().markPresetOff();
        ForgeVoxyInstance.INSTANCE.getFormalModelStore().markPresetOff();
        ForgeVoxyInstance.INSTANCE.getFormalModelFactory().markPresetOff();
        ForgeVoxyInstance.INSTANCE.getOneBlockFormalBakeUpload().markPresetOff();
        ForgeVoxyInstance.INSTANCE.getMultiBlockFormalBakeUpload().markPresetOff();
        ForgeVoxyInstance.INSTANCE.getFormalModelBakeryLifecycle().markPresetOff();
        ForgeVoxyInstance.INSTANCE.getFormalShaderInputConsumer().markPresetOff();
        ForgeVoxyInstance.INSTANCE.getFormalShaderProgramValidator().markPresetOff();
        ForgeVoxyInstance.INSTANCE.getFormalTexturedShaderPreview().markPresetOff();
        ForgeVoxyInstance.INSTANCE.getFormalPackedQuadPreview().markPresetOff();
        ForgeVoxyInstance.INSTANCE.getFormalTerrainPackedRecordBridge().markPresetOff();
        ForgeVoxyInstance.INSTANCE.getFormalTerrainRendererOwner().markPresetOff();
        ForgeVoxyInstance.INSTANCE.getFormalMdicViewportOwner().markPresetOff();
        ForgeVoxyInstance.INSTANCE.closeActiveWorld();
        source.sendSuccess(() -> Component.literal("Voxy preset off: runtime overrides disabled engine, auto ingest, auto CPU mesh build, auto BuiltSection build, auto geometry-manager consume, upload-only GL geometry heap, direct GL renderer skeleton, MDIC command skeleton/debug draw, simple GPU renderer, and debug renderer. Overrides are not written to toml."), false);
        return 1;
    }

    private static int applyPresetOverlay(CommandSourceStack source) {
        ForgeVoxyRuntimeOverrides.applyOverlayPreset();
        boolean engineReady = ForgeVoxyInstance.INSTANCE.ensureActiveWorldSkeletonForCurrentWorldIfAllowed();
        String message = "Voxy preset overlay: runtime-only overlay debug applied, not written to toml. "
                + "Effective values: engine=true autoIngest=true autoCpuMesh=true simpleGpu=true source="
                + ForgeGpuMeshUploadManager.getConfiguredSource()
                + " debugRenderer=false minDistance=0 maxDistance=64 renderLoadedChunks=true skipMode=DISABLED loadedMargin=0 keepCached=true colors=layer-debug ignoreDepth=true verticalOffset=0.05. "
                + (engineReady ? "WorldEngine is active." : "No active client world was found; enter or re-enter a world to create the WorldEngine.");
        source.sendSuccess(() -> Component.literal(message), false);
        return 1;
    }

    private static int applyPresetLod(CommandSourceStack source) {
        ForgeVoxyRuntimeOverrides.applyLodPreset();
        boolean engineReady = ForgeVoxyInstance.INSTANCE.ensureActiveWorldSkeletonForCurrentWorldIfAllowed();
        String message = "Voxy preset lod: runtime-only cached LoD mode applied, not written to toml. "
                + "Effective values: engine=true autoIngest=true autoCpuMesh=true simpleGpu=true source="
                + ForgeGpuMeshUploadManager.getConfiguredSource()
                + " (" + CPU_MESH_SOURCE_DESCRIPTION + ") debugRenderer=false minDistance=5 maxDistance=64 renderLoadedChunks=false skipMode=BY_RENDER_DISTANCE loadedMargin=0 keepCached=true colors=original ignoreDepth=false verticalOffset=0.0. "
                + "Use /voxy preset lod_built_section for the recommended BuiltSection migration source. "
                + (engineReady ? "WorldEngine is active." : "No active client world was found; enter or re-enter a world to create the WorldEngine.");
        source.sendSuccess(() -> Component.literal(message), false);
        return 1;
    }

    private static int applyPresetLodBuiltSection(CommandSourceStack source) {
        ForgeVoxyRuntimeOverrides.applyLodBuiltSectionPreset();
        ForgeVoxyInstance.INSTANCE.getGpuMeshUploadManager().clear();
        boolean engineReady = ForgeVoxyInstance.INSTANCE.ensureActiveWorldSkeletonForCurrentWorldIfAllowed();
        String message = "Voxy preset lod_built_section: runtime-only BuiltSection cached LoD mode applied, not written to toml. "
                + "Effective values: engine=true autoIngest=true autoCpuMesh=false autoBuiltSection=true simpleGpu=true source=BUILT_SECTION "
                + "(" + BUILT_SECTION_SOURCE_DESCRIPTION + ") debugRenderer=false minDistance=5 maxDistance=64 renderLoadedChunks=false skipMode=BY_RENDER_DISTANCE loadedMargin=0 keepCached=true colors=original ignoreDepth=false verticalOffset=0.0. "
                + "Switch back with /voxy gpu_mesh_source cpu if you need the CPU_MESH fallback. "
                + "GPU buffers were cleared so the selected source can rebuild cleanly. "
                + (engineReady ? "WorldEngine is active." : "No active client world was found; enter or re-enter a world to create the WorldEngine.");
        source.sendSuccess(() -> Component.literal(message), false);
        return 1;
    }

    private static int applyPresetGeometryManager(CommandSourceStack source) {
        ForgeVoxyRuntimeOverrides.applyGeometryManagerPreset();
        boolean engineReady = ForgeVoxyInstance.INSTANCE.ensureActiveWorldSkeletonForCurrentWorldIfAllowed();
        String message = "Voxy preset geometry_manager: runtime-only CPU geometry manager consume mode applied, not written to toml. "
                + "Effective values forced: engine=true autoIngest=true autoBuiltSection=true autoGeometryConsume=true geometryGpuUpload=false autoCpuMesh=false. "
                + "Simple renderer/source settings were left as their current effective values: simpleGpu="
                + ForgeVoxyRuntimeOverrides.enableSimpleGpuMeshRenderer()
                + " source=" + ForgeGpuMeshUploadManager.getConfiguredSource()
                + ". This path records section ids, metadata, upload intents, and remove intents only; it does not upload GL buffers. "
                + (engineReady ? "WorldEngine is active." : "No active client world was found; enter or re-enter a world to create the WorldEngine.");
        source.sendSuccess(() -> Component.literal(message), false);
        return 1;
    }

    private static int applyPresetGlHeapVisualize(CommandSourceStack source) {
        ForgeVoxyRuntimeOverrides.applyGlHeapVisualizePreset();
        ForgeVoxyInstance.INSTANCE.getGpuMeshUploadManager().clear();
        ForgeVoxyInstance.INSTANCE.getGpuMeshCache().clear();
        ForgeVoxyInstance.INSTANCE.getGpuGeometryVisualizationCache().clear();
        ForgeVoxyInstance.INSTANCE.getGpuGeometryReadbackDebugRenderer().clearStats();
        boolean engineReady = ForgeVoxyInstance.INSTANCE.ensureActiveWorldSkeletonForCurrentWorldIfAllowed();
        String message = "Voxy preset gl_heap_visualize: runtime-only GL heap readback visualization mode applied, not written to toml. "
                + "Effective values forced: engine=true autoIngest=true autoBuiltSection=true autoGeometryConsume=true geometryGpuUpload=true geometryGpuVisualization=true simpleGpu=false debugRenderer=false. "
                + "This path reads the upload-only GL heap on command, builds a temporary debug visualization cache, and draws that cache only; it is not MDIC/VoxyRenderSystem/simple GPU renderer. "
                + "Run /voxy geometry_gpu_visualize_sample after geometry_gpu_upload_status shows uploadedSections > 0. "
                + (engineReady ? "WorldEngine is active." : "No active client world was found; enter or re-enter a world to create the WorldEngine.");
        source.sendSuccess(() -> Component.literal(message), false);
        return 1;
    }

    private static int applyPresetGlHeapReadback(CommandSourceStack source) {
        ForgeVoxyRuntimeOverrides.applyGlHeapReadbackPreset();
        ForgeVoxyInstance.INSTANCE.getGpuMeshUploadManager().clear();
        ForgeVoxyInstance.INSTANCE.getGpuMeshCache().clear();
        ForgeVoxyInstance.INSTANCE.getGpuGeometryVisualizationCache().clear();
        ForgeVoxyInstance.INSTANCE.getGpuGeometryReadbackDebugRenderer().clearStats();
        ForgeVoxyInstance.INSTANCE.getGpuGeometryReadbackMeshCache().clear();
        ForgeVoxyInstance.INSTANCE.getGpuGeometryReadbackMeshRefreshManager().clear();
        if (ForgeGpuGeometryReadbackMeshRefreshManager.refreshOnPreset()) {
            ForgeVoxyInstance.INSTANCE.getGpuGeometryReadbackMeshRefreshManager().requestRefresh(ForgeGpuGeometryReadbackMeshRefreshManager.REASON_PRESET);
        }
        boolean engineReady = ForgeVoxyInstance.INSTANCE.ensureActiveWorldSkeletonForCurrentWorldIfAllowed();
        String message = "Voxy preset gl_heap_readback: runtime-only GL heap readback simple GPU source applied, not written to toml. "
                + "Effective values forced: engine=true autoIngest=true autoBuiltSection=true autoGeometryConsume=true geometryGpuUpload=true simpleGpu=true source=GL_HEAP_READBACK readbackAutoRefresh=true geometryGpuVisualization=false debugRenderer=false minDistance=0 maxDistance=64 renderLoadedChunks=true skipMode=DISABLED keepCached=true colors=original ignoreDepth=true verticalOffset=0.05. "
                + "This path rate-limits upload-only GL heap readback into a simple GPU mesh cache, then uses the existing vanilla VertexBuffer renderer; it is not MDIC/VoxyRenderSystem. "
                + "Auto refresh is requested now and will retry after upload status shows uploadedSections > 0; use /voxy geometry_gpu_readback_mesh_refresh_once for a manual refresh. "
                + (engineReady ? "WorldEngine is active." : "No active client world was found; enter or re-enter a world to create the WorldEngine.");
        source.sendSuccess(() -> Component.literal(message), false);
        return 1;
    }

    private static int applyPresetDirectGlDebug(CommandSourceStack source) {
        ForgeVoxyRuntimeOverrides.applyDirectGlDebugPreset();
        ForgeVoxyInstance.INSTANCE.getGpuMeshUploadManager().clear();
        ForgeVoxyInstance.INSTANCE.getGpuGeometryVisualizationCache().clear();
        ForgeVoxyInstance.INSTANCE.getGpuGeometryReadbackMeshCache().clear();
        ForgeVoxyInstance.INSTANCE.getGpuGeometryReadbackMeshRefreshManager().clear();
        ForgeVoxyInstance.INSTANCE.getGpuGeometryReadbackDebugRenderer().clearStats();
        ForgeVoxyInstance.INSTANCE.getDirectGpuGeometryRenderer().clear();
        ForgeVoxyInstance.INSTANCE.getDirectGpuGeometryRenderer().setDrawMode(ForgeDirectGpuGeometryDrawMode.LOOP_PER_SECTION);
        ForgeVoxyInstance.INSTANCE.getDirectGpuGeometryRenderer().markEnabledRuntime();
        ForgeVoxyInstance.INSTANCE.getDirectGpuGeometryRenderer().requestAutoPlan(ForgeDirectGpuGeometryRenderer.REASON_PRESET);
        boolean engineReady = ForgeVoxyInstance.INSTANCE.ensureActiveWorldSkeletonForCurrentWorldIfAllowed();
        String message = "Voxy preset direct_gl_debug: runtime-only G5.7 direct GL debug renderer applied, not written to toml. "
                + "Effective values forced: engine=true autoIngest=true autoBuiltSection=true autoGeometryConsume=true geometryGpuUpload=true directGlRenderer=true directAutoPlan=true simpleGpu=false readbackAutoRefresh=false geometryGpuVisualization=false debugRenderer=false. "
                + "actualDrawEnabled=false and configuredDrawMode=LOOP_PER_SECTION by default. Auto plan may build a camera/radius-aware draw list after upload status shows uploadedSections > 0; run /voxy direct_gl_renderer_draw_mode multi_draw_arrays or /voxy direct_gl_renderer_draw_mode indirect plus /voxy direct_gl_renderer_draw_enable to test optional paths. "
                + "This does not enable MDIC, VoxyRenderSystem, shaderpack, or the simple renderer. "
                + (engineReady ? "WorldEngine is active." : "No active client world was found; enter or re-enter a world to create the WorldEngine.");
        source.sendSuccess(() -> Component.literal(message), false);
        return 1;
    }

    private static int applyPresetMdicSkeleton(CommandSourceStack source) {
        ForgeVoxyRuntimeOverrides.applyMdicSkeletonPreset();
        ForgeVoxyInstance.INSTANCE.getGpuMeshUploadManager().clear();
        ForgeVoxyInstance.INSTANCE.getGpuMeshCache().clear();
        ForgeVoxyInstance.INSTANCE.getGpuGeometryVisualizationCache().clear();
        ForgeVoxyInstance.INSTANCE.getGpuGeometryReadbackMeshCache().clear();
        ForgeVoxyInstance.INSTANCE.getGpuGeometryReadbackMeshRefreshManager().clear();
        ForgeVoxyInstance.INSTANCE.getGpuGeometryReadbackDebugRenderer().clearStats();
        ForgeVoxyInstance.INSTANCE.getDirectGpuGeometryRenderer().clear();
        ForgeVoxyInstance.INSTANCE.getMdicCommandManager().clear();
        ForgeVoxyInstance.INSTANCE.getMdicDebugRenderer().clear();
        boolean engineReady = ForgeVoxyInstance.INSTANCE.ensureActiveWorldSkeletonForCurrentWorldIfAllowed();
        String message = "Voxy preset mdic_skeleton: runtime-only G6.5 visibility-aware directional face-mask bucket-aware MDIC command-buffer skeleton applied, not written to toml. "
                + "Effective values forced: engine=true autoIngest=true autoBuiltSection=true autoGeometryConsume=true geometryGpuUpload=true mdicCommandSkeleton=true mdicCommandBucketAware=true directionalFaceMask=true selectionMode=AUTO frustumFallbackToRadius=true maxPlanCandidates=512 maxSections=32 maxCommands=256 maxRecords=65536 includeTranslucent=false includeDoubleSided=true includeDirectional=true simpleGpu=false directGlRenderer=false directActualDraw=false readbackAutoRefresh=false geometryGpuVisualization=false debugRenderer=false. "
                + "mdic_skeleton is command-buffer skeleton only; no renderer draw is issued. Use /voxy direct_gl_mdic_plan_sample, /voxy direct_gl_mdic_build_buffer, /voxy direct_gl_mdic_audit, or /voxy direct_gl_mdic_stress_once after geometry_gpu_upload_status shows uploadedSections > 0. "
                + "This does not enable MDICSectionRenderer, VoxyRenderSystem, shaderpack, Embeddium/Oculus/Sodium/Iris, or mixins. "
                + (engineReady ? "WorldEngine is active." : "No active client world was found; enter or re-enter a world to create the WorldEngine.");
        source.sendSuccess(() -> Component.literal(message), false);
        return 1;
    }

    private static int applyPresetMdicDebug(CommandSourceStack source) {
        ForgeVoxyRuntimeOverrides.applyMdicDebugPreset();
        ForgeVoxyInstance.INSTANCE.getGpuMeshUploadManager().clear();
        ForgeVoxyInstance.INSTANCE.getGpuMeshCache().clear();
        ForgeVoxyInstance.INSTANCE.getGpuGeometryVisualizationCache().clear();
        ForgeVoxyInstance.INSTANCE.getGpuGeometryReadbackMeshCache().clear();
        ForgeVoxyInstance.INSTANCE.getGpuGeometryReadbackMeshRefreshManager().clear();
        ForgeVoxyInstance.INSTANCE.getGpuGeometryReadbackDebugRenderer().clearStats();
        ForgeVoxyInstance.INSTANCE.getDirectGpuGeometryRenderer().clear();
        ForgeVoxyInstance.INSTANCE.getMdicCommandManager().clear();
        ForgeVoxyInstance.INSTANCE.getMdicDebugRenderer().clear();
        ForgeVoxyInstance.INSTANCE.getMdicDebugRenderer().setDrawMode(ForgeMdicDebugDrawMode.LOOP_PER_COMMAND);
        boolean engineReady = ForgeVoxyInstance.INSTANCE.ensureActiveWorldSkeletonForCurrentWorldIfAllowed();
        String message = "Voxy preset mdic_debug: runtime-only G6.7 DrawElementsIndirectCount CPU-count-buffer visibility-aware directional face-mask bucket-aware MDIC command-buffer debug draw preset applied, not written to toml. "
                + "Effective values forced: engine=true autoIngest=true autoBuiltSection=true autoGeometryConsume=true geometryGpuUpload=true mdicCommandSkeleton=true mdicCommandBucketAware=true directionalFaceMask=true selectionMode=AUTO frustumFallbackToRadius=true maxPlanCandidates=512 maxSections=32 maxCommands=256 maxRecords=65536 mdicDebugDraw=true includeTranslucent=false includeDoubleSided=true includeDirectional=true simpleGpu=false directGlRenderer=false directActualDraw=false readbackAutoRefresh=false geometryGpuVisualization=false debugRenderer=false. "
                + "actualDrawEnabled=false and configuredDrawMode=LOOP_PER_COMMAND by default; run /voxy direct_gl_mdic_plan_sample, /voxy direct_gl_mdic_build_buffer, /voxy direct_gl_mdic_audit, optional /voxy direct_gl_mdic_draw_mode auto or elements_indirect_count, then /voxy direct_gl_mdic_draw_enable to draw visibility-aware face-mask bucket debug geometry. Use /voxy direct_gl_mdic_draw_stress_once for G6.7 loop/multi/arrays-indirect/elements-indirect/count visibility stress. "
                + "This does not enable MDICSectionRenderer, VoxyRenderSystem, shaderpack, Embeddium/Oculus/Sodium/Iris, or mixins. "
                + (engineReady ? "WorldEngine is active." : "No active client world was found; enter or re-enter a world to create the WorldEngine.");
        source.sendSuccess(() -> Component.literal(message), false);
        return 1;
    }

    private static int applyPresetFormalRendererSkeleton(CommandSourceStack source) {
        ForgeVoxyRuntimeOverrides.applyFormalRendererSkeletonPreset();
        boolean engineReady = ForgeVoxyInstance.INSTANCE.ensureActiveWorldSkeletonForCurrentWorldIfAllowed();
        ForgeVoxyInstance.INSTANCE.getFormalRendererManager().enable("preset-formal-renderer-skeleton");
        ForgeFormalRendererStats status = ForgeVoxyInstance.INSTANCE.getFormalRendererManager().checkReadiness("preset-formal-renderer-skeleton");
        String message = "Voxy preset formal_renderer_skeleton: runtime-only H2 formal renderer no-draw lifecycle/status skeleton applied, not written to toml. "
                + "No formal draw is enabled, no shader is bound, MDICSectionRenderer and VoxyRenderSystem are not called, and existing debug renderers are not enabled by this preset. "
                + (engineReady ? "WorldEngine is active. " : "No active client world was found; enter or re-enter a world to create the WorldEngine. ")
                + formatFormalRendererStatus(status);
        source.sendSuccess(() -> Component.literal(message), false);
        return status.formalRendererSkeletonReady() && status.noDraw() && !status.actualDrawEnabled() ? 1 : 0;
    }

    private static int applyPresetFormalModelStoreSkeleton(CommandSourceStack source) {
        ForgeVoxyRuntimeOverrides.applyFormalModelStoreSkeletonPreset();
        boolean engineReady = ForgeVoxyInstance.INSTANCE.ensureActiveWorldSkeletonForCurrentWorldIfAllowed();
        ForgeFormalModelStoreStats storeStatus = ForgeVoxyInstance.INSTANCE.getFormalModelStore().build();
        ForgeFormalModelStoreAuditResult audit = ForgeVoxyInstance.INSTANCE.getFormalModelStore().audit();
        ForgeVoxyInstance.INSTANCE.getFormalRendererManager().enable("preset-formal-model-store-skeleton");
        ForgeFormalRendererStats rendererStatus = ForgeVoxyInstance.INSTANCE.getFormalRendererManager().checkReadiness("preset-formal-model-store-skeleton");
        String message = "Voxy preset formal_model_store_skeleton: runtime-only I2 formal ModelStore ownership skeleton applied, not written to toml. "
                + "No bake, formal shader bind, formal draw, MDICSectionRenderer, VoxyRenderSystem, sample-set build, or real atlas pixel upload was performed. "
                + (engineReady ? "WorldEngine is active. " : "No active client world was found; enter or re-enter a world to create the WorldEngine. ")
                + formatFormalModelStoreStatus(storeStatus)
                + " "
                + formatFormalModelStoreAudit(audit)
                + " "
                + formatFormalRendererStatus(rendererStatus);
        source.sendSuccess(() -> Component.literal(message), false);
        return storeStatus.formalModelStoreSkeletonReady() && audit.success() && rendererStatus.noDraw() && !rendererStatus.actualDrawEnabled() ? 1 : 0;
    }

    private static int applyPresetFormalModelFactorySkeleton(CommandSourceStack source) {
        ForgeVoxyRuntimeOverrides.applyFormalModelFactorySkeletonPreset();
        boolean engineReady = ForgeVoxyInstance.INSTANCE.ensureActiveWorldSkeletonForCurrentWorldIfAllowed();
        ForgeFormalModelFactoryStats requestStatus = ForgeVoxyInstance.INSTANCE.getFormalModelFactory().requestCurrent();
        ForgeFormalModelFactoryStats processStatus = ForgeVoxyInstance.INSTANCE.getFormalModelFactory().processSkeleton();
        ForgeFormalModelFactoryAuditResult audit = ForgeVoxyInstance.INSTANCE.getFormalModelFactory().audit();
        ForgeVoxyInstance.INSTANCE.getFormalRendererManager().enable("preset-formal-model-factory-skeleton");
        ForgeFormalRendererStats rendererStatus = ForgeVoxyInstance.INSTANCE.getFormalRendererManager().checkReadiness("preset-formal-model-factory-skeleton");
        String message = "Voxy preset formal_model_factory_skeleton: runtime-only I3 formal ModelFactory lifecycle skeleton applied, not written to toml. "
                + "No real bake, formal ModelStore upload, atlas pixel upload, formal shader bind, formal draw, MDICSectionRenderer, or VoxyRenderSystem call was performed. "
                + (engineReady ? "WorldEngine is active. " : "No active client world was found; current-block request may fail until a world is active. ")
                + formatFormalModelFactoryStatus(requestStatus)
                + " "
                + formatFormalModelFactoryStatus(processStatus)
                + " "
                + formatFormalModelFactoryAudit(audit)
                + " "
                + formatFormalRendererStatus(rendererStatus);
        source.sendSuccess(() -> Component.literal(message), false);
        return processStatus.formalModelFactorySkeletonReady() && audit.success() && rendererStatus.noDraw() && !rendererStatus.actualDrawEnabled() ? 1 : 0;
    }

    private static int applyPresetFormalOneBlockBakeUpload(CommandSourceStack source) {
        ForgeVoxyRuntimeOverrides.applyFormalModelFactorySkeletonPreset();
        boolean engineReady = ForgeVoxyInstance.INSTANCE.ensureActiveWorldSkeletonForCurrentWorldIfAllowed();
        ForgeOneBlockFormalBakeUploadStats buildStatus = ForgeVoxyInstance.INSTANCE.getOneBlockFormalBakeUpload().bakeOneCurrent();
        ForgeOneBlockFormalBakeUploadAuditResult audit = ForgeVoxyInstance.INSTANCE.getOneBlockFormalBakeUpload().audit();
        ForgeFormalRendererStats rendererStatus = ForgeVoxyInstance.INSTANCE.getFormalRendererManager().checkReadiness("preset-formal-one-block-bake-upload");
        ForgeOneBlockFormalBakeUploadStats status = ForgeVoxyInstance.INSTANCE.getOneBlockFormalBakeUpload().createStatusSnapshot();
        String message = "Voxy preset formal_one_block_bake_upload: runtime-only I4 one-block real bake/upload prototype applied, not written to toml. "
                + "Exactly one safe solid block is baked and uploaded into the I2 formal ModelStore owner. No formal shader bind, formal draw, MDICSectionRenderer, VoxyRenderSystem, multi-block bake, or sample-set upload path was used. "
                + (engineReady ? "WorldEngine is active. " : "No active client world was found; safe fallback block selection may still use vanilla registry. ")
                + formatOneBlockFormalBakeUploadStatus(status)
                + " "
                + formatOneBlockFormalBakeUploadAudit(audit)
                + " "
                + formatFormalRendererStatus(rendererStatus);
        source.sendSuccess(() -> Component.literal(message), false);
        return buildStatus.oneBlockFormalUploadReady()
                && audit.success()
                && rendererStatus.noDraw()
                && !rendererStatus.actualDrawEnabled()
                && !rendererStatus.formalRendererReady() ? 1 : 0;
    }

    private static int applyPresetFormalMultiBlockBakeUpload(CommandSourceStack source) {
        ForgeVoxyRuntimeOverrides.applyFormalModelFactorySkeletonPreset();
        boolean engineReady = ForgeVoxyInstance.INSTANCE.ensureActiveWorldSkeletonForCurrentWorldIfAllowed();
        ForgeMultiBlockFormalBakeUploadStats buildStatus = ForgeVoxyInstance.INSTANCE.getMultiBlockFormalBakeUpload().bakeMultiSafe();
        ForgeMultiBlockFormalBakeUploadAuditResult audit = ForgeVoxyInstance.INSTANCE.getMultiBlockFormalBakeUpload().audit();
        ForgeFormalRendererStats rendererStatus = ForgeVoxyInstance.INSTANCE.getFormalRendererManager().checkReadiness("preset-formal-multi-block-bake-upload");
        ForgeMultiBlockFormalBakeUploadStats status = ForgeVoxyInstance.INSTANCE.getMultiBlockFormalBakeUpload().createStatusSnapshot();
        String message = "Voxy preset formal_multi_block_bake_upload: runtime-only I5 multi-block real bake/upload and dedupe prototype applied, not written to toml. "
                + "Several safe solid blocks are baked and uploaded into the I2 formal ModelStore owner. No formal shader bind, formal draw, MDICSectionRenderer, VoxyRenderSystem, fluid support, broad biome LUT, or sample-set upload path was used. "
                + (engineReady ? "WorldEngine is active. " : "No active client world was found; safe fallback block selection may still use vanilla registry. ")
                + formatMultiBlockFormalBakeUploadStatus(status)
                + " "
                + formatMultiBlockFormalBakeUploadAudit(audit)
                + " "
                + formatFormalRendererStatus(rendererStatus);
        source.sendSuccess(() -> Component.literal(message), false);
        return buildStatus.multiBlockFormalUploadReady()
                && audit.success()
                && rendererStatus.noDraw()
                && !rendererStatus.actualDrawEnabled()
                && !rendererStatus.formalRendererReady() ? 1 : 0;
    }

    private static int applyPresetFormalModelLifecycleRebuild(CommandSourceStack source) {
        ForgeVoxyRuntimeOverrides.applyFormalModelLifecycleRebuildPreset();
        boolean engineReady = ForgeVoxyInstance.INSTANCE.ensureActiveWorldSkeletonForCurrentWorldIfAllowed();
        ForgeFormalModelBakeryLifecycleStats status = ForgeVoxyInstance.INSTANCE.getFormalModelBakeryLifecycle().runQaLifecycleRebuild();
        ForgeFormalModelBakeryLifecycleAuditResult audit = ForgeVoxyInstance.INSTANCE.getFormalModelBakeryLifecycle().createAuditStatusSnapshot();
        ForgeFormalRendererStats rendererStatus = ForgeVoxyInstance.INSTANCE.getFormalRendererManager().checkReadiness("preset-formal-model-lifecycle-rebuild");
        String message = "Voxy preset formal_model_lifecycle_rebuild: runtime-only I6 formal ModelBakery lifecycle rebuild and alias-safe dedupe prototype applied, not written to toml. "
                + "The preset runs multi-block formal bake/upload, simulates reload invalidation, rebuilds the safe set, and audits explicit dedupe alias semantics. No formal shader bind, formal draw, MDICSectionRenderer, VoxyRenderSystem, async bake thread, broad fluid support, or sample-set upload path was used. "
                + (engineReady ? "WorldEngine is active. " : "No active client world was found; safe fallback block selection may still use vanilla registry. ")
                + formatFormalModelBakeryLifecycleStatus(status)
                + " "
                + formatFormalModelBakeryLifecycleAudit(audit)
                + " "
                + formatFormalRendererStatus(rendererStatus);
        source.sendSuccess(() -> Component.literal(message), false);
        return status.reloadRebuildPrototypeReady()
                && status.aliasSafeDedupeReady()
                && audit.success()
                && rendererStatus.noDraw()
                && !rendererStatus.actualDrawEnabled()
                && !rendererStatus.formalRendererReady() ? 1 : 0;
    }

    private static int applyPresetFormalShaderInputSkeleton(CommandSourceStack source) {
        ForgeVoxyRuntimeOverrides.applyFormalShaderInputSkeletonPreset();
        boolean engineReady = ForgeVoxyInstance.INSTANCE.ensureActiveWorldSkeletonForCurrentWorldIfAllowed();
        ForgeFormalShaderInputConsumerStats status = ForgeVoxyInstance.INSTANCE.getFormalShaderInputConsumer().build();
        ForgeFormalShaderInputConsumerAuditResult audit = ForgeVoxyInstance.INSTANCE.getFormalShaderInputConsumer().createAuditStatusSnapshot();
        ForgeFormalRendererStats rendererStatus = ForgeVoxyInstance.INSTANCE.getFormalRendererManager().checkReadiness("preset-formal-shader-input-skeleton");
        String message = "Voxy preset formal_shader_input_skeleton: runtime-only J1 formal shader input consumption skeleton applied, not written to toml. "
                + "The preset rebuilds the I6 safe set if needed, validates formal ModelStore resource handles and binding points, and performs no formal shader bind or terrain draw. "
                + (engineReady ? "WorldEngine is active. " : "No active client world was found; safe fallback block selection may still use vanilla registry. ")
                + formatFormalShaderInputConsumerStatus(status)
                + " "
                + formatFormalShaderInputConsumerAudit(audit)
                + " "
                + formatFormalRendererStatus(rendererStatus);
        source.sendSuccess(() -> Component.literal(message), false);
        return status.formalShaderInputConsumerReady()
                && audit.success()
                && rendererStatus.noDraw()
                && !rendererStatus.actualDrawEnabled()
                && !rendererStatus.formalRendererReady() ? 1 : 0;
    }

    private static int applyPresetFormalShaderProgramValidation(CommandSourceStack source) {
        ForgeVoxyRuntimeOverrides.applyFormalShaderProgramValidationPreset();
        boolean engineReady = ForgeVoxyInstance.INSTANCE.ensureActiveWorldSkeletonForCurrentWorldIfAllowed();
        ForgeFormalShaderProgramStats status = ForgeVoxyInstance.INSTANCE.getFormalShaderProgramValidator().build();
        ForgeFormalShaderProgramAuditResult audit = ForgeVoxyInstance.INSTANCE.getFormalShaderProgramValidator().createAuditStatusSnapshot();
        ForgeFormalRendererStats rendererStatus = ForgeVoxyInstance.INSTANCE.getFormalRendererManager().checkReadiness("preset-formal-shader-program-validation");
        String message = "Voxy preset formal_shader_program_validation: runtime-only J2 formal shader program validation prototype applied, not written to toml. "
                + "The preset rebuilds the I6 safe set if needed, validates J1 formal inputs, compiles/links an audit-only compute shader, runs compact GPU readback validation, and performs no terrain or formal renderer draw. "
                + (engineReady ? "WorldEngine is active. " : "No active client world was found; safe fallback block selection may still use vanilla registry. ")
                + formatFormalShaderProgramStatus(status)
                + " "
                + formatFormalShaderProgramAudit(audit)
                + " "
                + formatFormalRendererStatus(rendererStatus);
        source.sendSuccess(() -> Component.literal(message), false);
        return status.formalShaderProgramValidatorReady()
                && status.validationShaderCompileOk()
                && status.validationProgramLinkOk()
                && status.gpuValidationOk()
                && audit.success()
                && rendererStatus.noDraw()
                && !rendererStatus.actualDrawEnabled()
                && !rendererStatus.formalRendererReady() ? 1 : 0;
    }

    private static int applyPresetFormalTexturedShaderPreview(CommandSourceStack source) {
        ForgeVoxyRuntimeOverrides.applyFormalTexturedShaderPreviewPreset();
        boolean engineReady = ForgeVoxyInstance.INSTANCE.ensureActiveWorldSkeletonForCurrentWorldIfAllowed();
        ForgeFormalTexturedShaderPreviewStats status = ForgeVoxyInstance.INSTANCE.getFormalTexturedShaderPreview().build();
        ForgeFormalTexturedShaderPreviewAuditResult audit = ForgeVoxyInstance.INSTANCE.getFormalTexturedShaderPreview().createAuditStatusSnapshot();
        ForgeFormalRendererStats rendererStatus = ForgeVoxyInstance.INSTANCE.getFormalRendererManager().checkReadiness("preset-formal-textured-shader-preview");
        String message = "Voxy preset formal_textured_shader_preview: runtime-only J3 formal textured shader preview prototype applied, not written to toml. "
                + "The preset rebuilds the I6 safe set if needed, validates J2 formal shader inputs, renders selected formal model ids only to an offscreen preview framebuffer, reads back preview checksums, and performs no terrain or formal renderer draw. "
                + (engineReady ? "WorldEngine is active. " : "No active client world was found; safe fallback block selection may still use vanilla registry. ")
                + formatFormalTexturedShaderPreviewStatus(status)
                + " "
                + formatFormalTexturedShaderPreviewAudit(audit)
                + " "
                + formatFormalRendererStatus(rendererStatus);
        source.sendSuccess(() -> Component.literal(message), false);
        return status.formalTexturedShaderPrototypeReady()
                && status.previewReadbackOk()
                && status.previewPixelMismatches() == 0
                && audit.success()
                && !status.terrainDrawStarted()
                && !status.formalRendererDrawStarted()
                && !status.actualRendererDrawEnabled()
                && rendererStatus.noDraw()
                && !rendererStatus.actualDrawEnabled()
                && !rendererStatus.formalRendererReady() ? 1 : 0;
    }

    private static int applyPresetFormalPackedQuadPreview(CommandSourceStack source) {
        ForgeVoxyRuntimeOverrides.applyFormalPackedQuadPreviewPreset();
        boolean engineReady = ForgeVoxyInstance.INSTANCE.ensureActiveWorldSkeletonForCurrentWorldIfAllowed();
        ForgeFormalPackedQuadPreviewStats status = ForgeVoxyInstance.INSTANCE.getFormalPackedQuadPreview().build();
        ForgeFormalPackedQuadPreviewAuditResult audit = ForgeVoxyInstance.INSTANCE.getFormalPackedQuadPreview().createAuditStatusSnapshot();
        ForgeFormalRendererStats rendererStatus = ForgeVoxyInstance.INSTANCE.getFormalRendererManager().checkReadiness("preset-formal-packed-quad-preview");
        String message = "Voxy preset formal_packed_quad_preview: runtime-only J4 formal packed-quad shader geometry preview applied, not written to toml. "
                + "The preset rebuilds the I6 safe set if needed, validates J3 formal textured preview resources, maps packed quad-style records to formal model ids in an isolated temporary buffer, renders only to an offscreen preview framebuffer, and performs no live terrain draw or formal renderer draw. "
                + (status.syntheticFallbackUsed() ? "Synthetic packed-quad fallback was used and is reported as such. " : "")
                + (engineReady ? "WorldEngine is active. " : "No active client world was found; safe fallback block selection may still use vanilla registry. ")
                + formatFormalPackedQuadPreviewStatus(status)
                + " "
                + formatFormalPackedQuadPreviewAudit(audit)
                + " "
                + formatFormalRendererStatus(rendererStatus);
        source.sendSuccess(() -> Component.literal(message), false);
        return status.formalPackedQuadPreviewReady()
                && status.packedQuadShaderPreviewReady()
                && status.temporaryFormalQuadBufferCreated()
                && status.temporaryFormalQuadCount() >= 1
                && status.usesFormalModelIds()
                && !status.usesPlaceholderModelIds()
                && !status.sampleSetModelIdsUsed()
                && status.originalGeometryUntouched()
                && status.previewReadbackOk()
                && status.previewPixelMismatches() == 0
                && audit.success()
                && !status.terrainDrawStarted()
                && !status.formalRendererDrawStarted()
                && !status.actualRendererDrawEnabled()
                && rendererStatus.noDraw()
                && !rendererStatus.actualDrawEnabled()
                && !rendererStatus.formalRendererReady() ? 1 : 0;
    }

    private static int applyPresetFormalTerrainRecordBridge(CommandSourceStack source) {
        ForgeVoxyRuntimeOverrides.applyFormalTerrainRecordBridgePreset();
        boolean engineReady = ForgeVoxyInstance.INSTANCE.ensureActiveWorldSkeletonForCurrentWorldIfAllowed();
        ForgeFormalTerrainPackedRecordBridgeStats status = ForgeVoxyInstance.INSTANCE.getFormalTerrainPackedRecordBridge().build();
        ForgeFormalTerrainPackedRecordBridgeAuditResult audit = ForgeVoxyInstance.INSTANCE.getFormalTerrainPackedRecordBridge().createAuditStatusSnapshot();
        ForgeFormalRendererStats rendererStatus = ForgeVoxyInstance.INSTANCE.getFormalRendererManager().checkReadiness("preset-formal-terrain-record-bridge");
        String message = "Voxy preset formal_terrain_record_bridge: runtime-only J5 real terrain packed-record formal model-id bridge applied, not written to toml. "
                + "The preset rebuilds the I6 safe set, creates real current-world BuiltSection packed records, maps blockState sources to formal model ids, rewrites only an isolated temporary preview buffer, renders offscreen through J4, and performs no live terrain draw or formal renderer draw. "
                + (!status.realTerrainRecordsUsed() ? "No synthetic fallback is accepted for J5 success; this status should be treated as partial if realTerrainRecordsUsed=false. " : "")
                + (engineReady ? "WorldEngine is active. " : "No active client world was found before the preset attempted to create the skeleton. ")
                + formatFormalTerrainRecordBridgeStatus(status)
                + " "
                + formatFormalTerrainRecordBridgeAudit(audit)
                + " "
                + formatFormalRendererStatus(rendererStatus);
        VoxyForge.LOGGER.info(message);
        source.sendSuccess(() -> Component.literal(message), false);
        return status.formalTerrainPackedRecordBridgeReady()
                && status.realTerrainPackedRecordBridgeReady()
                && status.realTerrainRecordsUsed()
                && !status.syntheticFallbackUsed()
                && status.sourceRecordsAccepted() >= 1
                && status.temporaryFormalQuadBufferCreated()
                && status.temporaryFormalQuadCount() >= 1
                && status.previewReadbackOk()
                && status.previewPixelMismatches() == 0
                && audit.success()
                && rendererStatus.noDraw()
                && !rendererStatus.actualDrawEnabled()
                && !rendererStatus.formalRendererReady() ? 1 : 0;
    }

    private static int applyPresetFormalTerrainRendererOwnerNoDraw(CommandSourceStack source) {
        ForgeVoxyRuntimeOverrides.applyFormalTerrainRendererOwnerNoDrawPreset();
        boolean engineReady = ForgeVoxyInstance.INSTANCE.ensureActiveWorldSkeletonForCurrentWorldIfAllowed();
        ForgeFormalTerrainRendererStats enableStatus = ForgeVoxyInstance.INSTANCE.getFormalTerrainRendererOwner().enable("preset-formal-terrain-renderer-owner-no-draw");
        ForgeFormalTerrainRendererStats checkStatus = ForgeVoxyInstance.INSTANCE.getFormalTerrainRendererOwner().check("preset-formal-terrain-renderer-owner-no-draw");
        ForgeFormalTerrainRendererAuditResult audit = ForgeVoxyInstance.INSTANCE.getFormalTerrainRendererOwner().audit();
        ForgeFormalTerrainRendererStats status = ForgeVoxyInstance.INSTANCE.getFormalTerrainRendererOwner().createStatusSnapshot();
        ForgeFormalRendererStats rendererStatus = ForgeVoxyInstance.INSTANCE.getFormalRendererManager().checkReadiness("preset-formal-terrain-renderer-owner-no-draw");
        String message = "Voxy preset formal_terrain_renderer_owner_no_draw: runtime-only K1 formal terrain renderer owner no-draw skeleton applied, not written to toml. "
                + "The preset enables only the K1 owner shell, checks the K0 alignment verdict, reports formal viewport/command/visibility placeholders as blockers, and does not enable live terrain draw, formal MDIC draw, MDICSectionRenderer, VoxyRenderSystem, or debug renderers. "
                + (engineReady ? "WorldEngine is active. " : "No active client world was found; K1 owner remains no-draw and can still report static readiness. ")
                + formatFormalTerrainRendererOwnerStatus(status)
                + " "
                + formatFormalTerrainRendererOwnerAudit(audit)
                + " "
                + formatFormalRendererStatus(rendererStatus);
        source.sendSuccess(() -> Component.literal(message), false);
        return enableStatus.formalTerrainRendererOwnerReady()
                && checkStatus.k0VerdictReadyForK1()
                && audit.success()
                && status.noDraw()
                && !status.actualRendererDrawEnabled()
                && !status.formalTerrainRendererReady()
                && !rendererStatus.actualDrawEnabled()
                && !rendererStatus.formalRendererReady() ? 1 : 0;
    }

    private static int applyPresetFormalMdicViewportOwnerNoDraw(CommandSourceStack source) {
        ForgeVoxyRuntimeOverrides.applyFormalMdicViewportOwnerNoDrawPreset();
        boolean engineReady = ForgeVoxyInstance.INSTANCE.ensureActiveWorldSkeletonForCurrentWorldIfAllowed();
        ForgeFormalTerrainRendererStats terrainStatus = ForgeVoxyInstance.INSTANCE.getFormalTerrainRendererOwner().enable("preset-formal-mdic-viewport-owner-no-draw");
        ForgeFormalMdicViewportStats enableStatus = ForgeVoxyInstance.INSTANCE.getFormalMdicViewportOwner().enable("preset-formal-mdic-viewport-owner-no-draw");
        ForgeFormalMdicViewportStats checkStatus = ForgeVoxyInstance.INSTANCE.getFormalMdicViewportOwner().check("preset-formal-mdic-viewport-owner-no-draw");
        ForgeFormalMdicViewportAuditResult audit = ForgeVoxyInstance.INSTANCE.getFormalMdicViewportOwner().audit();
        ForgeFormalMdicViewportStats status = ForgeVoxyInstance.INSTANCE.getFormalMdicViewportOwner().createStatusSnapshot();
        ForgeFormalTerrainRendererStats terrainOwnerStatus = ForgeVoxyInstance.INSTANCE.getFormalTerrainRendererOwner().check("preset-formal-mdic-viewport-owner-no-draw");
        ForgeFormalRendererStats rendererStatus = ForgeVoxyInstance.INSTANCE.getFormalRendererManager().checkReadiness("preset-formal-mdic-viewport-owner-no-draw");
        String message = "Voxy preset formal_mdic_viewport_owner_no_draw: runtime-only K2 formal viewport / command / visibility ownership skeleton applied, not written to toml. "
                + "The preset enables only no-draw formal owners, records MDICViewport-shaped logical resources, keeps draw command/count/visibility/render-list/position scratch allocations deferred, and does not run cmdgen, glMultiDrawElementsIndirectCountARB, MDICSectionRenderer, VoxyRenderSystem, or debug renderers. "
                + (engineReady ? "WorldEngine is active. " : "No active client world was found; K2 owner remains no-draw and can still report static readiness. ")
                + formatFormalMdicViewportOwnerStatus(status)
                + " "
                + formatFormalMdicViewportOwnerAudit(audit)
                + " "
                + formatFormalTerrainRendererOwnerStatus(terrainOwnerStatus)
                + " "
                + formatFormalRendererStatus(rendererStatus);
        source.sendSuccess(() -> Component.literal(message), false);
        return terrainStatus.formalTerrainRendererOwnerReady()
                && enableStatus.formalViewportOwnerReady()
                && checkStatus.formalCommandBufferOwnerReady()
                && audit.success()
                && status.noDraw()
                && !status.actualRendererDrawEnabled()
                && !status.formalRendererReady()
                && !rendererStatus.actualDrawEnabled()
                && !rendererStatus.formalRendererReady() ? 1 : 0;
    }

    private static int clearPreset(CommandSourceStack source) {
        ForgeVoxyRuntimeOverrides.clear();
        ForgeVoxyInstance.INSTANCE.getGpuGeometryReadbackMeshCache().clear();
        ForgeVoxyInstance.INSTANCE.getGpuGeometryReadbackMeshRefreshManager().clear();
        ForgeVoxyInstance.INSTANCE.getGpuMeshUploadManager().clear();
        ForgeVoxyInstance.INSTANCE.getDirectGpuGeometryRenderer().clear();
        ForgeVoxyInstance.INSTANCE.getMdicCommandManager().clear();
        ForgeVoxyInstance.INSTANCE.getMdicDebugRenderer().clear();
        ForgeVoxyInstance.INSTANCE.getModelBridgeReadiness().clear();
        ForgeVoxyInstance.INSTANCE.getModelStoreSkeleton().clear();
        ForgeVoxyInstance.INSTANCE.getModelStoreLayoutAuditor().clear();
        ForgeVoxyInstance.INSTANCE.getModelBridgeResourceReloadTracker().clear();
        ForgeVoxyInstance.INSTANCE.getBakedModelBridge().clear();
        ForgeVoxyInstance.INSTANCE.getRealModelStoreSample().clear();
        ForgeVoxyInstance.INSTANCE.getModelSampleSet().clear();
        ForgeVoxyInstance.INSTANCE.getModelAtlasSkeleton().clear();
        ForgeVoxyInstance.INSTANCE.getModelAtlasPixelUploader().clear();
        ForgeVoxyInstance.INSTANCE.getModelAtlasSampleSetUploader().clear();
        ForgeVoxyInstance.INSTANCE.getFormalShaderInputBridge().clear();
        ForgeVoxyInstance.INSTANCE.getFormalModelStore().clear();
        ForgeVoxyInstance.INSTANCE.getFormalModelFactory().clear();
        ForgeVoxyInstance.INSTANCE.getOneBlockFormalBakeUpload().clear();
        ForgeVoxyInstance.INSTANCE.getMultiBlockFormalBakeUpload().clear();
        ForgeVoxyInstance.INSTANCE.getFormalModelBakeryLifecycle().clear();
        ForgeVoxyInstance.INSTANCE.getFormalShaderInputConsumer().clear();
        ForgeVoxyInstance.INSTANCE.getFormalShaderProgramValidator().clear();
        ForgeVoxyInstance.INSTANCE.getFormalTexturedShaderPreview().clear();
        ForgeVoxyInstance.INSTANCE.getFormalPackedQuadPreview().clear();
        ForgeVoxyInstance.INSTANCE.getFormalTerrainPackedRecordBridge().clear();
        ForgeVoxyInstance.INSTANCE.getFormalTerrainRendererOwner().clear("preset-clear");
        ForgeVoxyInstance.INSTANCE.getFormalMdicViewportOwner().clear("preset-clear");
        ForgeVoxyInstance.INSTANCE.getTexturedDebugQuadRenderer().clear();
        ForgeVoxyInstance.INSTANCE.getTexturedReadbackRenderer().clear();
        ForgeVoxyInstance.INSTANCE.getTexturedMdicDebugRenderer().clear();
        if (!ForgeVoxyRuntimeOverrides.enabledWorldEngineSkeleton()) {
            clearRuntimePipeline();
            ForgeVoxyInstance.INSTANCE.closeActiveWorld();
        }
        ForgeVoxyInstance.INSTANCE.getFormalRendererManager().markPresetClear();
        ForgeVoxyInstance.INSTANCE.getFormalModelStore().markPresetClear();
        ForgeVoxyInstance.INSTANCE.getFormalModelFactory().markPresetClear();
        ForgeVoxyInstance.INSTANCE.getOneBlockFormalBakeUpload().markPresetClear();
        ForgeVoxyInstance.INSTANCE.getMultiBlockFormalBakeUpload().markPresetClear();
        ForgeVoxyInstance.INSTANCE.getFormalModelBakeryLifecycle().markPresetClear();
        ForgeVoxyInstance.INSTANCE.getFormalShaderInputConsumer().markPresetClear();
        ForgeVoxyInstance.INSTANCE.getFormalShaderProgramValidator().markPresetClear();
        ForgeVoxyInstance.INSTANCE.getFormalTexturedShaderPreview().markPresetClear();
        ForgeVoxyInstance.INSTANCE.getFormalPackedQuadPreview().markPresetClear();
        ForgeVoxyInstance.INSTANCE.getFormalTerrainPackedRecordBridge().markPresetClear();
        ForgeVoxyInstance.INSTANCE.getFormalTerrainRendererOwner().markPresetClear();
        ForgeVoxyInstance.INSTANCE.getFormalMdicViewportOwner().markPresetClear();
        source.sendSuccess(() -> Component.literal("Voxy preset clear: runtime overrides cleared; effective values now come from the toml config."), false);
        return 1;
    }

    private static int presetStatus(CommandSourceStack source) {
        var status = ForgeVoxyRuntimeOverrides.createStatusSnapshot();
        String message = String.format(
                "Voxy preset status: active=%s overrides=%s engine=%s(%s) autoIngest=%s(%s) autoCpuMesh=%s(%s) autoBuiltSection=%s(%s) autoGeometryConsume=%s(%s) geometryGpuUpload=%s(%s) geometryGpuVisualization=%s(%s) readbackAutoRefresh=%s(%s) directGlRenderer=%s(%s) directAutoPlan=%s(%s) directActualDraw=%s(%s) visualizationAlpha=%.2f(%s) visualizationIgnoreDepth=%s(%s) visualizationDoubleSided=%s(%s) simpleGpu=%s(%s) debugRenderer=%s(%s) source=%s(%s) sourceRole=%s recommendedSource=%s fallbackSource=%s minDistance=%d(%s) maxDistance=%d(%s) renderLoadedChunks=%s(%s) skipMode=%s(%s) loadedMargin=%d(%s) keepCached=%s(%s) colors=%s(%s) simpleIgnoreDepth=%s(%s) simpleVerticalOffset=%.3f(%s) simpleAlpha=%.2f(%s) debugAlpha=%.2f(%s)",
                status.presetName(),
                status.hasOverrides(),
                status.enableWorldEngineSkeleton(),
                status.enableWorldEngineSkeletonSource(),
                status.enableAutoChunkIngest(),
                status.enableAutoChunkIngestSource(),
                status.enableAutoCpuMeshBuild(),
                status.enableAutoCpuMeshBuildSource(),
                status.enableAutoBuiltSectionBuild(),
                status.enableAutoBuiltSectionBuildSource(),
                status.enableAutoGeometryManagerConsume(),
                status.enableAutoGeometryManagerConsumeSource(),
                status.enableGeometryGpuUpload(),
                status.enableGeometryGpuUploadSource(),
                status.enableGeometryGpuVisualization(),
                status.enableGeometryGpuVisualizationSource(),
                status.enableGeometryGpuReadbackMeshAutoRefresh(),
                status.enableGeometryGpuReadbackMeshAutoRefreshSource(),
                status.enableDirectGpuGeometryRenderer(),
                status.enableDirectGpuGeometryRendererSource(),
                status.enableDirectGpuGeometryAutoPlan(),
                status.enableDirectGpuGeometryAutoPlanSource(),
                status.directGpuGeometryRendererActualDraw(),
                status.directGpuGeometryRendererActualDrawSource(),
                status.geometryGpuVisualizationAlpha(),
                status.geometryGpuVisualizationAlphaSource(),
                status.geometryGpuVisualizationIgnoreDepth(),
                status.geometryGpuVisualizationIgnoreDepthSource(),
                status.geometryGpuVisualizationDoubleSided(),
                status.geometryGpuVisualizationDoubleSidedSource(),
                status.enableSimpleGpuMeshRenderer(),
                status.enableSimpleGpuMeshRendererSource(),
                status.enableDebugMeshRenderer(),
                status.enableDebugMeshRendererSource(),
                status.simpleGpuMeshSource(),
                status.simpleGpuMeshSourceSource(),
                describeMeshSource(status.simpleGpuMeshSource()),
                RECOMMENDED_MESH_SOURCE,
                FALLBACK_MESH_SOURCE,
                status.simpleGpuMeshMinRenderDistanceChunks(),
                status.simpleGpuMeshMinRenderDistanceChunksSource(),
                status.simpleGpuMeshRenderDistanceChunks(),
                status.simpleGpuMeshRenderDistanceChunksSource(),
                status.simpleGpuMeshRenderLoadedChunks(),
                status.simpleGpuMeshRenderLoadedChunksSource(),
                status.simpleGpuMeshLoadedChunkSkipMode(),
                status.simpleGpuMeshLoadedChunkSkipModeSource(),
                status.simpleGpuMeshLoadedChunkMargin(),
                status.simpleGpuMeshLoadedChunkMarginSource(),
                status.simpleGpuMeshKeepCachedChunks(),
                status.simpleGpuMeshKeepCachedChunksSource(),
                status.simpleGpuMeshUseOriginalColors() ? "original" : "layer-debug",
                status.simpleGpuMeshUseOriginalColorsSource(),
                status.simpleGpuMeshIgnoreDepth(),
                status.simpleGpuMeshIgnoreDepthSource(),
                status.simpleGpuMeshVerticalOffset(),
                status.simpleGpuMeshVerticalOffsetSource(),
                status.simpleGpuMeshAlpha(),
                status.simpleGpuMeshAlphaSource(),
                status.debugMeshAlpha(),
                status.debugMeshAlphaSource()
        );
        source.sendSuccess(() -> Component.literal(message), false);
        return status.hasOverrides() ? 1 : 0;
    }

    private static String formatRenderStats(ForgeDebugMeshRenderer.FrameStats stats) {
        return String.format(
                "lastRender=%s reason=%s renderDim=%s candidates=%d renderedEntries=%d emittedVertices=%d skippedDistance=%d skippedDimension=%d skippedReleased=%d skippedLimited=%d skippedTranslucent=%d skippedEmpty=%d wireframe=%s ignoreDepth=%s alphaByte=%d offset=%.3f",
                stats.rendered(),
                stats.reason(),
                stats.dimension(),
                stats.candidateEntries(),
                stats.renderedEntries(),
                stats.emittedVertices(),
                stats.skippedByDistanceEntries(),
                stats.skippedByDimensionEntries(),
                stats.skippedReleasedEntries(),
                stats.limitedEntries(),
                stats.skippedTranslucentEntries(),
                stats.skippedEmptyEntries(),
                stats.wireframe(),
                stats.ignoreDepth(),
                stats.alphaByte(),
                stats.verticalOffset()
        );
    }

    private static LodVisibilityContext createLodVisibilityContext(Minecraft minecraft) {
        if (minecraft.level == null || minecraft.player == null) {
            return null;
        }

        String dimension = minecraft.level.dimension().location().toString();
        int playerChunkX = minecraft.player.chunkPosition().x;
        int playerChunkZ = minecraft.player.chunkPosition().z;
        int maxDistance = ForgeGpuMeshUploadManager.getConfiguredRenderDistanceChunks();
        int minDistance = Math.min(ForgeSimpleGpuMeshRenderer.getConfiguredMinRenderDistanceChunks(), maxDistance);
        boolean renderLoadedChunks = ForgeSimpleGpuMeshRenderer.shouldRenderLoadedChunks();
        SimpleGpuMeshLoadedChunkSkipMode loadedChunkSkipMode = ForgeSimpleGpuMeshRenderer.getConfiguredLoadedChunkSkipMode();
        int loadedChunkMargin = ForgeSimpleGpuMeshRenderer.getConfiguredLoadedChunkMargin();
        int clientRenderDistance = getClientRenderDistanceChunks(minecraft);
        boolean keepCachedChunks = ForgeGpuMeshUploadManager.keepCachedChunks();
        ForgeGpuMeshCache.VisibilitySnapshot visibility = ForgeVoxyInstance.INSTANCE.getGpuMeshCache().createVisibilitySnapshot(
                dimension,
                playerChunkX,
                playerChunkZ,
                minDistance,
                maxDistance,
                renderLoadedChunks,
                loadedChunkSkipMode,
                loadedChunkMargin,
                clientRenderDistance,
                minecraft.level::hasChunk
        );
        return new LodVisibilityContext(
                dimension,
                playerChunkX,
                playerChunkZ,
                clientRenderDistance,
                minDistance,
                maxDistance,
                renderLoadedChunks,
                loadedChunkSkipMode,
                loadedChunkMargin,
                keepCachedChunks,
                visibility
        );
    }

    private static int getClientRenderDistanceChunks(Minecraft minecraft) {
        try {
            return minecraft.options.renderDistance().get();
        } catch (RuntimeException e) {
            return -1;
        }
    }

    private static String formatLodVisibilityAdvice(
            ForgeGpuMeshCache.StatusSnapshot cacheStatus,
            ForgeSimpleGpuMeshRenderer.FrameStats renderStats,
            LodVisibilityContext context
    ) {
        if (context == null) {
            return "open a client world first";
        }
        if (!ForgeVoxyRuntimeOverrides.enabledWorldEngineSkeleton()
                || !ForgeVoxyRuntimeOverrides.enableAutoChunkIngest()
                || !ForgeVoxyRuntimeOverrides.enableSimpleGpuMeshRenderer()) {
            return "enable engine, auto ingest, and simple GPU renderer; defaults stay off";
        }
        if (ForgeGpuMeshUploadManager.getConfiguredSource() == SimpleGpuMeshSource.BUILT_SECTION
                && !ForgeVoxyRuntimeOverrides.enableAutoBuiltSectionBuild()) {
            return "source=BUILT_SECTION needs auto BuiltSection build; use /voxy preset lod_built_section for a runtime-only test setup";
        }
        if (ForgeGpuMeshUploadManager.getConfiguredSource() == SimpleGpuMeshSource.CPU_MESH
                && !ForgeVoxyRuntimeOverrides.enableAutoCpuMeshBuild()) {
            return "source=CPU_MESH needs auto CPU mesh build; use /voxy preset lod for a runtime-only test setup";
        }
        if (cacheStatus.buffers() == 0 || context.visibility().cachedBuffers() == 0) {
            return "no GPU buffers yet; fly to let chunks cache, or run ingest/build commands, and check auto ingest/build are enabled";
        }
        if (context.visibility().renderableChunks() == 0) {
            if (context.visibility().skippedRenderDistanceChunks() > 0 && !context.renderLoadedChunks()) {
                return "cached chunks are still inside the Minecraft render-distance neighborhood; lower Minecraft render distance to 4-6, fly farther, or use /voxy preset overlay for overlay debugging";
            }
            if (context.visibility().skippedLoadedStateChunks() > 0 && !context.renderLoadedChunks()) {
                return "cached chunks are still reported loaded by ClientLevel.hasChunk; use BY_RENDER_DISTANCE or DISABLED skip mode for LoD visibility testing";
            }
            if (context.visibility().skippedFar() > 0 && context.visibility().candidateBuffers() == 0) {
                return "cached chunks are beyond simpleGpuMeshRenderDistanceChunks; raise it, for example to 64";
            }
            if (context.visibility().skippedNear() > 0 && context.visibility().candidateBuffers() == 0) {
                return "cached chunks are inside simpleGpuMeshMinRenderDistanceChunks; lower min distance or fly farther then look back";
            }
            if (context.visibility().chunksWithinDistanceWindow() == 0) {
                return "no cached chunks currently fall inside the LoD min/max distance window";
            }
            return "chunks are in range but not renderable yet; wait for GPU upload or check skipped/released/translucent counters";
        }
        if (!renderStats.rendered() && renderStats.candidateBuffers() == 0) {
            return "renderable cached chunks exist, but the last render frame saw no candidates; wait one frame or rerun status after moving the camera";
        }
        return "LoD window has renderable cached chunks; if you still cannot see them, look away from loaded terrain or use renderLoadedChunks=true for overlay debugging";
    }

    private static String formatGpuNoRenderReason(ForgeSimpleGpuMeshRenderer.FrameStats renderStats, LodVisibilityContext context) {
        if (renderStats.renderedBuffers() > 0) {
            return "rendering";
        }
        if (context == null) {
            return "world-missing";
        }
        if (renderStats.candidateBuffers() <= 0 && context.visibility().candidateBuffers() <= 0) {
            if (context.visibility().skippedFar() > 0) {
                return "far filter: cached mesh is outside simpleGpuMeshRenderDistanceChunks";
            }
            if (context.visibility().skippedNear() > 0) {
                return "near filter: cached mesh is inside simpleGpuMeshMinRenderDistanceChunks";
            }
            if (context.visibility().skippedByDimension() > 0) {
                return "dimension filter: cached mesh belongs to another dimension";
            }
            return "no candidate cached mesh in the current LoD distance window";
        }
        if (!context.renderLoadedChunks()
                && (renderStats.skippedRenderDistance() > 0 || context.visibility().skippedRenderDistance() > 0)) {
            return "render-distance loaded filter: cached mesh is still inside Minecraft render distance plus margin";
        }
        if (!context.renderLoadedChunks()
                && (renderStats.skippedLoadedState() > 0 || context.visibility().skippedLoadedState() > 0)) {
            return "loaded-state filter: ClientLevel.hasChunk still reports cached mesh chunks as loaded";
        }
        if (renderStats.skippedNear() > 0 || context.visibility().skippedNear() > 0) {
            return "near filter: lower simpleGpuMeshMinRenderDistanceChunks for overlay debug";
        }
        if (renderStats.skippedByDistance() > 0 || context.visibility().skippedFar() > 0) {
            return "far filter: raise simpleGpuMeshRenderDistanceChunks";
        }
        if (renderStats.skippedByDimension() > 0 || context.visibility().skippedByDimension() > 0) {
            return "dimension filter: current cache does not match the active dimension";
        }
        if (renderStats.skippedReleased() > 0 || context.visibility().skippedReleased() > 0) {
            return "released buffer filter: GPU buffers were closed or missing";
        }
        if (renderStats.skippedTranslucent() > 0 || context.visibility().skippedTranslucent() > 0) {
            return "translucent filter: simple GPU renderer skips translucent mesh";
        }
        return renderStats.reason();
    }

    private static String formatDistance(int distance) {
        return distance < 0 ? "none" : Integer.toString(distance);
    }

    private static String describeMeshSource(SimpleGpuMeshSource source) {
        if (source == SimpleGpuMeshSource.BUILT_SECTION) {
            return BUILT_SECTION_SOURCE_DESCRIPTION;
        }
        if (source == SimpleGpuMeshSource.GL_HEAP_READBACK) {
            return GL_HEAP_READBACK_SOURCE_DESCRIPTION;
        }
        return CPU_MESH_SOURCE_DESCRIPTION;
    }

    private static String formatGeometryPointer(int geometryPtr) {
        return geometryPtr < 0 ? "none" : Long.toUnsignedString(Integer.toUnsignedLong(geometryPtr));
    }

    private static String formatBounds(ForgeCpuMeshCache.BoundsSnapshot bounds) {
        if (!bounds.available()) {
            return "bounds=none";
        }
        return String.format(
                "bounds=%s chunk %d,%d entries=%d min=%.2f,%.2f,%.2f max=%.2f,%.2f,%.2f",
                bounds.dimension(),
                bounds.chunkX(),
                bounds.chunkZ(),
                bounds.entries(),
                bounds.minX(),
                bounds.minY(),
                bounds.minZ(),
                bounds.maxX(),
                bounds.maxY(),
                bounds.maxZ()
        );
    }

    private static void closeTemporaryCpuMesh(ForgeCpuMeshBuildResult cpuResult) {
        if (cpuResult == null) {
            return;
        }
        for (ForgeCpuBuiltSection section : cpuResult.sections()) {
            if (section != null) {
                section.close();
            }
        }
    }

    private record LodVisibilityContext(
            String dimension,
            int playerChunkX,
            int playerChunkZ,
            int clientRenderDistance,
            int minDistance,
            int maxDistance,
            boolean renderLoadedChunks,
            SimpleGpuMeshLoadedChunkSkipMode loadedChunkSkipMode,
            int loadedChunkMargin,
            boolean keepCachedChunks,
            ForgeGpuMeshCache.VisibilitySnapshot visibility
    ) {
    }

    private static void clearRuntimePipeline() {
        ForgeVoxyInstance.INSTANCE.getChunkIngestManager().clear();
        ForgeVoxyInstance.INSTANCE.getCpuMeshBuildManager().clear();
        ForgeVoxyInstance.INSTANCE.getBuiltSectionBuildManager().clear();
        ForgeVoxyInstance.INSTANCE.getCpuMeshCache().clear();
        ForgeVoxyInstance.INSTANCE.getVoxyGeometryCache().clear();
        ForgeVoxyInstance.INSTANCE.getSectionGeometryConsumeManager().clear();
        ForgeVoxyInstance.INSTANCE.getGpuMeshUploadManager().clear();
        ForgeVoxyInstance.INSTANCE.getGpuGeometryUploadManager().clear();
        ForgeVoxyInstance.INSTANCE.getGpuGeometryVisualizationCache().clear();
        ForgeVoxyInstance.INSTANCE.getGpuGeometryReadbackMeshCache().clear();
        ForgeVoxyInstance.INSTANCE.getGpuGeometryReadbackMeshRefreshManager().clear();
        ForgeVoxyInstance.INSTANCE.getGpuGeometryReadbackDebugRenderer().clearStats();
        ForgeVoxyInstance.INSTANCE.getDirectGpuGeometryRenderer().clear();
        ForgeVoxyInstance.INSTANCE.getMdicCommandManager().clear();
        ForgeVoxyInstance.INSTANCE.getMdicDebugRenderer().clear();
        ForgeVoxyInstance.INSTANCE.getModelBridgeReadiness().clear();
        ForgeVoxyInstance.INSTANCE.getModelStoreSkeleton().clear();
        ForgeVoxyInstance.INSTANCE.getModelStoreLayoutAuditor().clear();
        ForgeVoxyInstance.INSTANCE.getModelBridgeResourceReloadTracker().clear();
        ForgeVoxyInstance.INSTANCE.getBakedModelBridge().clear();
        ForgeVoxyInstance.INSTANCE.getRealModelStoreSample().clear();
        ForgeVoxyInstance.INSTANCE.getModelSampleSet().clear();
        ForgeVoxyInstance.INSTANCE.getModelAtlasSkeleton().clear();
        ForgeVoxyInstance.INSTANCE.getModelAtlasPixelUploader().clear();
        ForgeVoxyInstance.INSTANCE.getModelAtlasSampleSetUploader().clear();
        ForgeVoxyInstance.INSTANCE.getFormalShaderInputBridge().clear();
        ForgeVoxyInstance.INSTANCE.getFormalModelStore().markDebugPipelineClear();
        ForgeVoxyInstance.INSTANCE.getFormalModelFactory().markDebugPipelineClear();
        ForgeVoxyInstance.INSTANCE.getOneBlockFormalBakeUpload().markDebugPipelineClear();
        ForgeVoxyInstance.INSTANCE.getMultiBlockFormalBakeUpload().markDebugPipelineClear();
        ForgeVoxyInstance.INSTANCE.getFormalModelBakeryLifecycle().markDebugPipelineClear();
        ForgeVoxyInstance.INSTANCE.getFormalShaderInputConsumer().markDebugPipelineClear();
        ForgeVoxyInstance.INSTANCE.getFormalShaderProgramValidator().markDebugPipelineClear();
        ForgeVoxyInstance.INSTANCE.getFormalTexturedShaderPreview().markDebugPipelineClear();
        ForgeVoxyInstance.INSTANCE.getFormalPackedQuadPreview().markDebugPipelineClear();
        ForgeVoxyInstance.INSTANCE.getFormalTerrainPackedRecordBridge().markDebugPipelineClear();
        ForgeVoxyInstance.INSTANCE.getFormalTerrainRendererOwner().markDebugPipelineClear();
        ForgeVoxyInstance.INSTANCE.getFormalMdicViewportOwner().markDebugPipelineClear();
        ForgeVoxyInstance.INSTANCE.getTexturedDebugQuadRenderer().clear();
        ForgeVoxyInstance.INSTANCE.getTexturedReadbackRenderer().clear();
        ForgeVoxyInstance.INSTANCE.getTexturedMdicDebugRenderer().clear();
        ForgeVoxyInstance.INSTANCE.getGpuMeshCache().clear();
        ForgeVoxyInstance.INSTANCE.getFormalRendererManager().markDebugPipelineClear();
    }

    private static int clearMeshCache(CommandSourceStack source) {
        ForgeVoxyInstance.INSTANCE.getCpuMeshCache().clear();
        ForgeVoxyInstance.INSTANCE.getVoxyGeometryCache().clear();
        ForgeVoxyInstance.INSTANCE.getSectionGeometryConsumeManager().clear();
        ForgeVoxyInstance.INSTANCE.getCpuMeshBuildManager().clear();
        ForgeVoxyInstance.INSTANCE.getBuiltSectionBuildManager().clear();
        ForgeVoxyInstance.INSTANCE.getGpuMeshUploadManager().clear();
        ForgeVoxyInstance.INSTANCE.getGpuGeometryUploadManager().clear();
        ForgeVoxyInstance.INSTANCE.getGpuGeometryVisualizationCache().clear();
        ForgeVoxyInstance.INSTANCE.getGpuGeometryReadbackMeshCache().clear();
        ForgeVoxyInstance.INSTANCE.getGpuGeometryReadbackMeshRefreshManager().clear();
        ForgeVoxyInstance.INSTANCE.getGpuGeometryReadbackDebugRenderer().clearStats();
        ForgeVoxyInstance.INSTANCE.getDirectGpuGeometryRenderer().clear();
        ForgeVoxyInstance.INSTANCE.getMdicCommandManager().clear();
        ForgeVoxyInstance.INSTANCE.getMdicDebugRenderer().clear();
        ForgeVoxyInstance.INSTANCE.getModelBridgeReadiness().clear();
        ForgeVoxyInstance.INSTANCE.getModelStoreSkeleton().clear();
        ForgeVoxyInstance.INSTANCE.getModelStoreLayoutAuditor().clear();
        ForgeVoxyInstance.INSTANCE.getModelBridgeResourceReloadTracker().clear();
        ForgeVoxyInstance.INSTANCE.getBakedModelBridge().clear();
        ForgeVoxyInstance.INSTANCE.getRealModelStoreSample().clear();
        ForgeVoxyInstance.INSTANCE.getModelSampleSet().clear();
        ForgeVoxyInstance.INSTANCE.getModelAtlasSkeleton().clear();
        ForgeVoxyInstance.INSTANCE.getModelAtlasPixelUploader().clear();
        ForgeVoxyInstance.INSTANCE.getModelAtlasSampleSetUploader().clear();
        ForgeVoxyInstance.INSTANCE.getFormalShaderInputBridge().clear();
        ForgeVoxyInstance.INSTANCE.getTexturedDebugQuadRenderer().clear();
        ForgeVoxyInstance.INSTANCE.getTexturedReadbackRenderer().clear();
        ForgeVoxyInstance.INSTANCE.getTexturedMdicDebugRenderer().clear();
        source.sendSuccess(() -> Component.literal("Voxy: cleared CPU mesh cache, CPU-only BuiltSection cache, CPU-only section geometry manager, simple GPU mesh cache, upload-only GL geometry heap, direct GL renderer skeleton state, MDIC command skeleton/debug draw state, GL heap readback visualization/readback-mesh caches, readback mesh auto-refresh state, auto mesh build record, and auto BuiltSection build record."), false);
        return 1;
    }

    private static int clearGpuMeshCache(CommandSourceStack source) {
        ForgeVoxyInstance.INSTANCE.getGpuMeshUploadManager().clear();
        source.sendSuccess(() -> Component.literal("Voxy: cleared simple GPU mesh buffers. CPU mesh and BuiltSection caches were left intact and can re-upload."), false);
        return 1;
    }

    private static int clearMeshBuildState(CommandSourceStack source) {
        ForgeVoxyInstance.INSTANCE.getCpuMeshBuildManager().clear();
        source.sendSuccess(() -> Component.literal("Voxy: cleared auto CPU mesh build queue and built-record."), false);
        return 1;
    }

    private static int clearDebugPipeline(CommandSourceStack source) {
        ForgeVoxyInstance.INSTANCE.getChunkIngestManager().clear();
        ForgeVoxyInstance.INSTANCE.getCpuMeshBuildManager().clear();
        ForgeVoxyInstance.INSTANCE.getBuiltSectionBuildManager().clear();
        ForgeVoxyInstance.INSTANCE.getCpuMeshCache().clear();
        ForgeVoxyInstance.INSTANCE.getVoxyGeometryCache().clear();
        ForgeVoxyInstance.INSTANCE.getSectionGeometryConsumeManager().clear();
        ForgeVoxyInstance.INSTANCE.getGpuMeshUploadManager().clear();
        ForgeVoxyInstance.INSTANCE.getGpuGeometryUploadManager().clear();
        ForgeVoxyInstance.INSTANCE.getGpuGeometryVisualizationCache().clear();
        ForgeVoxyInstance.INSTANCE.getGpuGeometryReadbackMeshCache().clear();
        ForgeVoxyInstance.INSTANCE.getGpuGeometryReadbackMeshRefreshManager().clear();
        ForgeVoxyInstance.INSTANCE.getGpuGeometryReadbackDebugRenderer().clearStats();
        ForgeVoxyInstance.INSTANCE.getDirectGpuGeometryRenderer().clear();
        ForgeVoxyInstance.INSTANCE.getMdicCommandManager().clear();
        ForgeVoxyInstance.INSTANCE.getMdicDebugRenderer().clear();
        ForgeVoxyInstance.INSTANCE.getModelBridgeReadiness().clear();
        ForgeVoxyInstance.INSTANCE.getModelStoreSkeleton().clear();
        ForgeVoxyInstance.INSTANCE.getModelStoreLayoutAuditor().clear();
        ForgeVoxyInstance.INSTANCE.getModelBridgeResourceReloadTracker().clear();
        ForgeVoxyInstance.INSTANCE.getBakedModelBridge().clear();
        ForgeVoxyInstance.INSTANCE.getRealModelStoreSample().clear();
        ForgeVoxyInstance.INSTANCE.getModelAtlasSkeleton().clear();
        ForgeVoxyInstance.INSTANCE.getModelAtlasPixelUploader().clear();
        ForgeVoxyInstance.INSTANCE.getFormalModelStore().markDebugPipelineClear();
        ForgeVoxyInstance.INSTANCE.getFormalModelFactory().markDebugPipelineClear();
        ForgeVoxyInstance.INSTANCE.getOneBlockFormalBakeUpload().markDebugPipelineClear();
        ForgeVoxyInstance.INSTANCE.getMultiBlockFormalBakeUpload().markDebugPipelineClear();
        ForgeVoxyInstance.INSTANCE.getFormalModelBakeryLifecycle().markDebugPipelineClear();
        ForgeVoxyInstance.INSTANCE.getFormalShaderInputConsumer().markDebugPipelineClear();
        ForgeVoxyInstance.INSTANCE.getFormalShaderProgramValidator().markDebugPipelineClear();
        ForgeVoxyInstance.INSTANCE.getFormalTexturedShaderPreview().markDebugPipelineClear();
        ForgeVoxyInstance.INSTANCE.getFormalPackedQuadPreview().markDebugPipelineClear();
        ForgeVoxyInstance.INSTANCE.getFormalTerrainPackedRecordBridge().markDebugPipelineClear();
        ForgeVoxyInstance.INSTANCE.getFormalTerrainRendererOwner().markDebugPipelineClear();
        ForgeVoxyInstance.INSTANCE.getFormalMdicViewportOwner().markDebugPipelineClear();
        ForgeVoxyInstance.INSTANCE.getTexturedDebugQuadRenderer().clear();
        ForgeVoxyInstance.INSTANCE.getTexturedReadbackRenderer().clear();
        ForgeVoxyInstance.INSTANCE.getTexturedMdicDebugRenderer().clear();
        ForgeVoxyInstance.INSTANCE.getFormalRendererManager().markDebugPipelineClear();
        source.sendSuccess(() -> Component.literal("Voxy: cleared debug pipeline ingest records, mesh build records, BuiltSection build records, CPU mesh cache, CPU-only BuiltSection cache, CPU-only section geometry manager, simple GPU mesh cache, upload-only GL geometry heap, direct GL renderer skeleton state, MDIC command skeleton/debug draw state, GL heap readback visualization/readback-mesh caches, and readback mesh auto-refresh state."), false);
        return 1;
    }

    private static int ingestStatus(CommandSourceStack source) {
        var status = ForgeVoxyInstance.INSTANCE.getChunkIngestManager().createStatusSnapshot();
        String dimension = status.dimension() == null ? "none" : status.dimension();
        String message = String.format(
                "Voxy ingest: engine=%s auto=%s dimension=%s queued=%d ingested=%d radius=%d maxPerTick=%d cooldown=%d avgMs=%.2f",
                status.enginePresent(),
                status.autoEnabled(),
                dimension,
                status.queuedChunks(),
                status.ingestedChunks(),
                status.radius(),
                status.maxChunksPerTick(),
                status.cooldownTicks(),
                status.averageMs()
        );
        source.sendSuccess(() -> Component.literal(message), false);
        return 1;
    }

    private static int clearIngestCache(CommandSourceStack source) {
        ForgeVoxyInstance.INSTANCE.getChunkIngestManager().clear();
        ForgeVoxyInstance.INSTANCE.getCpuMeshBuildManager().clear();
        ForgeVoxyInstance.INSTANCE.getBuiltSectionBuildManager().clear();
        source.sendSuccess(() -> Component.literal("Voxy: cleared auto ingest queue/cache, auto CPU mesh build queue/records, and auto BuiltSection build queue/records."), false);
        return 1;
    }
}
