package me.cortex.voxy.forge;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

/**
 * Preset command registration split out of ForgeVoxyCommands.
 *
 * <p>Many presets still point at deprecated proof routes. Keeping the subtree
 * isolated makes that retirement explicit.</p>
 */
@Deprecated(forRemoval = false)
final class ForgeVoxyPresetCommands {
    private ForgeVoxyPresetCommands() {
    }

    static void register(LiteralArgumentBuilder<CommandSourceStack> root) {
        root.then(Commands.literal("preset")
                .then(Commands.literal("off")
                        .executes(ctx -> ForgeVoxyCommands.applyPresetOff(ctx.getSource())))
                .then(Commands.literal("overlay")
                        .executes(ctx -> ForgeVoxyCommands.applyPresetOverlay(ctx.getSource())))
                .then(Commands.literal("lod")
                        .executes(ctx -> ForgeVoxyCommands.applyPresetLod(ctx.getSource())))
                .then(Commands.literal("lod_built_section")
                        .executes(ctx -> ForgeVoxyCommands.applyPresetLodBuiltSection(ctx.getSource())))
                .then(Commands.literal("geometry_manager")
                        .executes(ctx -> ForgeVoxyCommands.applyPresetGeometryManager(ctx.getSource())))
                .then(Commands.literal("gl_heap_visualize")
                        .executes(ctx -> ForgeVoxyCommands.applyPresetGlHeapVisualize(ctx.getSource())))
                .then(Commands.literal("gl_heap_readback")
                        .executes(ctx -> ForgeVoxyCommands.applyPresetGlHeapReadback(ctx.getSource())))
                .then(Commands.literal("direct_gl_debug")
                        .executes(ctx -> ForgeVoxyCommands.applyPresetDirectGlDebug(ctx.getSource())))
                .then(Commands.literal("mdic_skeleton")
                        .executes(ctx -> ForgeVoxyCommands.applyPresetMdicSkeleton(ctx.getSource())))
                .then(Commands.literal("mdic_debug")
                        .executes(ctx -> ForgeVoxyCommands.applyPresetMdicDebug(ctx.getSource())))
                .then(Commands.literal("formal_renderer_skeleton")
                        .executes(ctx -> ForgeVoxyCommands.applyPresetFormalRendererSkeleton(ctx.getSource())))
                .then(Commands.literal("formal_model_store_skeleton")
                        .executes(ctx -> ForgeVoxyCommands.applyPresetFormalModelStoreSkeleton(ctx.getSource())))
                .then(Commands.literal("formal_model_factory_skeleton")
                        .executes(ctx -> ForgeVoxyCommands.applyPresetFormalModelFactorySkeleton(ctx.getSource())))
                .then(Commands.literal("formal_one_block_bake_upload")
                        .executes(ctx -> ForgeVoxyCommands.applyPresetFormalOneBlockBakeUpload(ctx.getSource())))
                .then(Commands.literal("formal_multi_block_bake_upload")
                        .executes(ctx -> ForgeVoxyCommands.applyPresetFormalMultiBlockBakeUpload(ctx.getSource())))
                .then(Commands.literal("formal_model_lifecycle_rebuild")
                        .executes(ctx -> ForgeVoxyCommands.applyPresetFormalModelLifecycleRebuild(ctx.getSource())))
                .then(Commands.literal("formal_shader_input_skeleton")
                        .executes(ctx -> ForgeVoxyCommands.applyPresetFormalShaderInputSkeleton(ctx.getSource())))
                .then(Commands.literal("formal_shader_program_validation")
                        .executes(ctx -> ForgeVoxyCommands.applyPresetFormalShaderProgramValidation(ctx.getSource())))
                .then(Commands.literal("formal_textured_shader_preview")
                        .executes(ctx -> ForgeVoxyCommands.applyPresetFormalTexturedShaderPreview(ctx.getSource())))
                .then(Commands.literal("formal_packed_quad_preview")
                        .executes(ctx -> ForgeVoxyCommands.applyPresetFormalPackedQuadPreview(ctx.getSource())))
                .then(Commands.literal("formal_terrain_record_bridge")
                        .executes(ctx -> ForgeVoxyCommands.applyPresetFormalTerrainRecordBridge(ctx.getSource())))
                .then(Commands.literal("formal_terrain_renderer_owner_no_draw")
                        .executes(ctx -> ForgeVoxyCommands.applyPresetFormalTerrainRendererOwnerNoDraw(ctx.getSource())))
                .then(Commands.literal("formal_mdic_viewport_owner_no_draw")
                        .executes(ctx -> ForgeVoxyCommands.applyPresetFormalMdicViewportOwnerNoDraw(ctx.getSource())))
                .then(Commands.literal("formal_command_generation_owner_no_draw")
                        .executes(ctx -> ForgeVoxyCommands.applyPresetFormalCommandGenerationOwnerNoDraw(ctx.getSource())))
                .then(Commands.literal("formal_visibility_owner_no_draw")
                        .executes(ctx -> ForgeVoxyCommands.applyPresetFormalVisibilityOwnerNoDraw(ctx.getSource())))
                .then(Commands.literal("formal_cmdgen_gpu_validation_no_draw")
                        .executes(ctx -> ForgeVoxyCommands.applyPresetFormalCmdgenGpuValidationNoDraw(ctx.getSource())))
                .then(Commands.literal("formal_cmdgen_real_section_dry_run_no_draw")
                        .executes(ctx -> ForgeVoxyCommands.applyPresetFormalCmdgenRealSectionDryRunNoDraw(ctx.getSource())))
                .then(Commands.literal("formal_isolated_mdic_draw_offscreen")
                        .executes(ctx -> ForgeVoxyCommands.applyPresetFormalIsolatedMdicDrawOffscreen(ctx.getSource())))
                .then(Commands.literal("formal_model_id_geometry_path_no_live_draw")
                        .executes(ctx -> ForgeVoxyCommands.applyPresetFormalModelIdGeometryPathNoLiveDraw(ctx.getSource())))
                .then(Commands.literal("formal_terrain_shader_integration_offscreen")
                        .executes(ctx -> ForgeVoxyCommands.applyPresetFormalTerrainShaderIntegrationOffscreen(ctx.getSource())))
                .then(Commands.literal("formal_visible_lod_preview_debug")
                        .executes(ctx -> ForgeVoxyCommands.applyPresetFormalVisibleLodPreviewDebug(ctx.getSource())))
                .then(Commands.literal("clear")
                        .executes(ctx -> ForgeVoxyCommands.clearPreset(ctx.getSource())))
                .then(Commands.literal("status")
                        .executes(ctx -> ForgeVoxyCommands.presetStatus(ctx.getSource()))));
    }
}
