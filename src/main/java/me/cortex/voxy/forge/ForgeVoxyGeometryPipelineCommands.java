package me.cortex.voxy.forge;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import me.cortex.voxy.config.SimpleGpuMeshSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

/**
 * Geometry and ingest command registration for the original-Voxy parity route.
 */
final class ForgeVoxyGeometryPipelineCommands {
    private ForgeVoxyGeometryPipelineCommands() {
    }

    static void register(LiteralArgumentBuilder<CommandSourceStack> root) {
        root.then(Commands.literal("ingest_current_chunk")
                        .executes(ctx -> ForgeVoxyCommands.ingestCurrentChunk(ctx.getSource())))
                .then(Commands.literal("build_current_chunk_mesh")
                        .executes(ctx -> ForgeVoxyCommands.buildCurrentChunkMesh(ctx.getSource())))
                .then(Commands.literal("build_current_chunk_model_mesh")
                        .executes(ctx -> ForgeVoxyCommands.buildCurrentChunkModelMesh(ctx.getSource())))
                .then(Commands.literal("build_current_chunk_cpu_mesh")
                        .executes(ctx -> ForgeVoxyCommands.buildCurrentChunkCpuMesh(ctx.getSource())))
                .then(Commands.literal("build_current_chunk_built_section")
                        .executes(ctx -> ForgeVoxyCommands.buildCurrentChunkBuiltSection(ctx.getSource())))
                .then(Commands.literal("built_section_cache_status")
                        .executes(ctx -> ForgeVoxyCommands.builtSectionCacheStatus(ctx.getSource())))
                .then(Commands.literal("built_section_cache_clear")
                        .executes(ctx -> ForgeVoxyCommands.clearBuiltSectionCache(ctx.getSource())))
                .then(Commands.literal("built_section_build_clear")
                        .executes(ctx -> ForgeVoxyCommands.clearBuiltSectionBuildState(ctx.getSource())))
                .then(Commands.literal("geometry_manager_consume_current_chunk")
                        .executes(ctx -> ForgeVoxyCommands.consumeCurrentChunkGeometryManager(ctx.getSource())))
                .then(Commands.literal("geometry_manager_status")
                        .executes(ctx -> ForgeVoxyCommands.geometryManagerStatus(ctx.getSource())))
                .then(Commands.literal("geometry_manager_dump_sample")
                        .executes(ctx -> ForgeVoxyCommands.geometryManagerDumpSample(ctx.getSource())))
                .then(Commands.literal("geometry_manager_clear")
                        .executes(ctx -> ForgeVoxyCommands.clearGeometryManager(ctx.getSource())))
                .then(Commands.literal("geometry_manager_consume_clear")
                        .executes(ctx -> ForgeVoxyCommands.clearGeometryManagerConsumeState(ctx.getSource())))
                .then(Commands.literal("mesh_cache_status")
                        .executes(ctx -> ForgeVoxyCommands.meshCacheStatus(ctx.getSource())))
                .then(Commands.literal("mesh_cache_clear")
                        .executes(ctx -> ForgeVoxyCommands.clearMeshCache(ctx.getSource())))
                .then(Commands.literal("gpu_mesh_status")
                        .executes(ctx -> ForgeVoxyCommands.gpuMeshStatus(ctx.getSource())))
                .then(Commands.literal("gpu_mesh_source")
                        .then(Commands.literal("cpu")
                                .executes(ctx -> ForgeVoxyCommands.setGpuMeshSource(ctx.getSource(), SimpleGpuMeshSource.CPU_MESH)))
                        .then(Commands.literal("built_section")
                                .executes(ctx -> ForgeVoxyCommands.setGpuMeshSource(ctx.getSource(), SimpleGpuMeshSource.BUILT_SECTION)))
                        .then(Commands.literal("gl_heap_readback")
                                .executes(ctx -> ForgeVoxyCommands.setGpuMeshSource(ctx.getSource(), SimpleGpuMeshSource.GL_HEAP_READBACK))))
                .then(Commands.literal("lod_visibility_status")
                        .executes(ctx -> ForgeVoxyCommands.lodVisibilityStatus(ctx.getSource())))
                .then(Commands.literal("lod_overlay_debug")
                        .executes(ctx -> ForgeVoxyCommands.lodOverlayDebug(ctx.getSource())))
                .then(Commands.literal("lod_mode_advice")
                        .executes(ctx -> ForgeVoxyCommands.lodModeAdvice(ctx.getSource())))
                .then(Commands.literal("gpu_mesh_clear")
                        .executes(ctx -> ForgeVoxyCommands.clearGpuMeshCache(ctx.getSource())))
                .then(Commands.literal("mesh_build_clear")
                        .executes(ctx -> ForgeVoxyCommands.clearMeshBuildState(ctx.getSource())))
                .then(Commands.literal("debug_pipeline_status")
                        .executes(ctx -> ForgeVoxyCommands.meshCacheStatus(ctx.getSource())))
                .then(Commands.literal("debug_pipeline_clear")
                        .executes(ctx -> ForgeVoxyCommands.clearDebugPipeline(ctx.getSource())))
                .then(Commands.literal("ingest_status")
                        .executes(ctx -> ForgeVoxyCommands.ingestStatus(ctx.getSource())))
                .then(Commands.literal("ingest_clear_cache")
                        .executes(ctx -> ForgeVoxyCommands.clearIngestCache(ctx.getSource())));
    }
}
