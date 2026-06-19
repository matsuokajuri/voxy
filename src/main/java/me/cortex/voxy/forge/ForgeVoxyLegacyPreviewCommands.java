package me.cortex.voxy.forge;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

/**
 * Legacy preview/sample command registration split out of ForgeVoxyCommands.
 *
 * <p>These commands are retained only while their deprecated preview owners are
 * still compiled. They must not become part of the original-Voxy parity route.</p>
 */
@Deprecated(forRemoval = false)
final class ForgeVoxyLegacyPreviewCommands {
    private ForgeVoxyLegacyPreviewCommands() {
    }

    static void register(LiteralArgumentBuilder<CommandSourceStack> root) {
        root.then(Commands.literal("formal_textured_shader_build")
                        .executes(ctx -> ForgeVoxyCommands.formalTexturedShaderBuild(ctx.getSource())))
                .then(Commands.literal("formal_textured_shader_status")
                        .executes(ctx -> ForgeVoxyCommands.formalTexturedShaderStatus(ctx.getSource())))
                .then(Commands.literal("formal_textured_shader_audit")
                        .executes(ctx -> ForgeVoxyCommands.formalTexturedShaderAudit(ctx.getSource())))
                .then(Commands.literal("formal_textured_shader_dump")
                        .executes(ctx -> ForgeVoxyCommands.formalTexturedShaderDump(ctx.getSource())))
                .then(Commands.literal("formal_textured_shader_clear")
                        .executes(ctx -> ForgeVoxyCommands.formalTexturedShaderClear(ctx.getSource())))
                .then(Commands.literal("formal_textured_shader_preview_enable")
                        .executes(ctx -> ForgeVoxyCommands.formalTexturedShaderPreviewEnable(ctx.getSource())))
                .then(Commands.literal("formal_textured_shader_preview_disable")
                        .executes(ctx -> ForgeVoxyCommands.formalTexturedShaderPreviewDisable(ctx.getSource())))
                .then(Commands.literal("qa_j3_formal_textured_shader_preview")
                        .executes(ctx -> ForgeVoxyCommands.qaJ3FormalTexturedShaderPreview(ctx.getSource())))
                .then(Commands.literal("formal_packed_quad_preview_build")
                        .executes(ctx -> ForgeVoxyCommands.formalPackedQuadPreviewBuild(ctx.getSource())))
                .then(Commands.literal("formal_packed_quad_preview_status")
                        .executes(ctx -> ForgeVoxyCommands.formalPackedQuadPreviewStatus(ctx.getSource())))
                .then(Commands.literal("formal_packed_quad_preview_audit")
                        .executes(ctx -> ForgeVoxyCommands.formalPackedQuadPreviewAudit(ctx.getSource())))
                .then(Commands.literal("formal_packed_quad_preview_dump")
                        .executes(ctx -> ForgeVoxyCommands.formalPackedQuadPreviewDump(ctx.getSource())))
                .then(Commands.literal("formal_packed_quad_preview_clear")
                        .executes(ctx -> ForgeVoxyCommands.formalPackedQuadPreviewClear(ctx.getSource())))
                .then(Commands.literal("qa_j4_formal_packed_quad_preview")
                        .executes(ctx -> ForgeVoxyCommands.qaJ4FormalPackedQuadPreview(ctx.getSource())))
                .then(Commands.literal("formal_terrain_record_bridge_build")
                        .executes(ctx -> ForgeVoxyCommands.formalTerrainRecordBridgeBuild(ctx.getSource())))
                .then(Commands.literal("formal_terrain_record_bridge_status")
                        .executes(ctx -> ForgeVoxyCommands.formalTerrainRecordBridgeStatus(ctx.getSource())))
                .then(Commands.literal("formal_terrain_record_bridge_audit")
                        .executes(ctx -> ForgeVoxyCommands.formalTerrainRecordBridgeAudit(ctx.getSource())))
                .then(Commands.literal("formal_terrain_record_bridge_dump")
                        .executes(ctx -> ForgeVoxyCommands.formalTerrainRecordBridgeDump(ctx.getSource())))
                .then(Commands.literal("formal_terrain_record_bridge_clear")
                        .executes(ctx -> ForgeVoxyCommands.formalTerrainRecordBridgeClear(ctx.getSource())))
                .then(Commands.literal("qa_j5_real_terrain_record_bridge")
                        .executes(ctx -> ForgeVoxyCommands.qaJ5RealTerrainRecordBridge(ctx.getSource())))
                .then(Commands.literal("textured_debug_quad_build_sample")
                        .executes(ctx -> ForgeVoxyCommands.texturedDebugQuadBuildSample(ctx.getSource())))
                .then(Commands.literal("textured_debug_quad_enable")
                        .executes(ctx -> ForgeVoxyCommands.texturedDebugQuadEnable(ctx.getSource())))
                .then(Commands.literal("textured_debug_quad_disable")
                        .executes(ctx -> ForgeVoxyCommands.texturedDebugQuadDisable(ctx.getSource())))
                .then(Commands.literal("textured_debug_quad_status")
                        .executes(ctx -> ForgeVoxyCommands.texturedDebugQuadStatus(ctx.getSource())))
                .then(Commands.literal("textured_debug_quad_clear")
                        .executes(ctx -> ForgeVoxyCommands.texturedDebugQuadClear(ctx.getSource())))
                .then(Commands.literal("textured_readback_build_sample")
                        .executes(ctx -> ForgeVoxyCommands.texturedReadbackBuildSample(ctx.getSource())))
                .then(Commands.literal("textured_readback_enable")
                        .executes(ctx -> ForgeVoxyCommands.texturedReadbackEnable(ctx.getSource())))
                .then(Commands.literal("textured_readback_disable")
                        .executes(ctx -> ForgeVoxyCommands.texturedReadbackDisable(ctx.getSource())))
                .then(Commands.literal("textured_readback_status")
                        .executes(ctx -> ForgeVoxyCommands.texturedReadbackStatus(ctx.getSource())))
                .then(Commands.literal("textured_readback_clear")
                        .executes(ctx -> ForgeVoxyCommands.texturedReadbackClear(ctx.getSource())))
                .then(Commands.literal("textured_mdic_debug_build")
                        .executes(ctx -> ForgeVoxyCommands.texturedMdicDebugBuild(ctx.getSource())))
                .then(Commands.literal("textured_mdic_debug_input_mode")
                        .then(Commands.literal("sample_set")
                                .executes(ctx -> ForgeVoxyCommands.texturedMdicDebugInputMode(ctx.getSource(), ForgeTexturedMdicDebugInputMode.SAMPLE_SET_DIRECT)))
                        .then(Commands.literal("formal_bridge")
                                .executes(ctx -> ForgeVoxyCommands.texturedMdicDebugInputMode(ctx.getSource(), ForgeTexturedMdicDebugInputMode.FORMAL_INPUT_BRIDGE))))
                .then(Commands.literal("textured_mdic_debug_enable")
                        .executes(ctx -> ForgeVoxyCommands.texturedMdicDebugEnable(ctx.getSource())))
                .then(Commands.literal("textured_mdic_debug_disable")
                        .executes(ctx -> ForgeVoxyCommands.texturedMdicDebugDisable(ctx.getSource())))
                .then(Commands.literal("textured_mdic_debug_status")
                        .executes(ctx -> ForgeVoxyCommands.texturedMdicDebugStatus(ctx.getSource())))
                .then(Commands.literal("textured_mdic_debug_clear")
                        .executes(ctx -> ForgeVoxyCommands.texturedMdicDebugClear(ctx.getSource())));
    }
}
