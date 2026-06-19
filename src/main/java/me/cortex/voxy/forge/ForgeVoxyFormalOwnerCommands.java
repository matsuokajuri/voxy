package me.cortex.voxy.forge;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

/**
 * Formal owner skeleton command registration split out of ForgeVoxyCommands.
 *
 * <p>These K1-K4 owner commands are still skeleton/status surfaces. They do not
 * imply formal renderer readiness.</p>
 */
final class ForgeVoxyFormalOwnerCommands {
    private ForgeVoxyFormalOwnerCommands() {
    }

    static void register(LiteralArgumentBuilder<CommandSourceStack> root) {
        root.then(Commands.literal("formal_terrain_renderer_owner_enable")
                        .executes(ctx -> ForgeVoxyCommands.formalTerrainRendererOwnerEnable(ctx.getSource())))
                .then(Commands.literal("formal_terrain_renderer_owner_status")
                        .executes(ctx -> ForgeVoxyCommands.formalTerrainRendererOwnerStatus(ctx.getSource())))
                .then(Commands.literal("formal_terrain_renderer_owner_check")
                        .executes(ctx -> ForgeVoxyCommands.formalTerrainRendererOwnerCheck(ctx.getSource())))
                .then(Commands.literal("formal_terrain_renderer_owner_audit")
                        .executes(ctx -> ForgeVoxyCommands.formalTerrainRendererOwnerAudit(ctx.getSource())))
                .then(Commands.literal("formal_terrain_renderer_owner_dump")
                        .executes(ctx -> ForgeVoxyCommands.formalTerrainRendererOwnerDump(ctx.getSource())))
                .then(Commands.literal("formal_terrain_renderer_owner_clear")
                        .executes(ctx -> ForgeVoxyCommands.formalTerrainRendererOwnerClear(ctx.getSource())))
                .then(Commands.literal("qa_k1_formal_terrain_renderer_owner")
                        .executes(ctx -> ForgeVoxyCommands.qaK1FormalTerrainRendererOwner(ctx.getSource())))
                .then(Commands.literal("formal_mdic_viewport_owner_enable")
                        .executes(ctx -> ForgeVoxyCommands.formalMdicViewportOwnerEnable(ctx.getSource())))
                .then(Commands.literal("formal_mdic_viewport_owner_status")
                        .executes(ctx -> ForgeVoxyCommands.formalMdicViewportOwnerStatus(ctx.getSource())))
                .then(Commands.literal("formal_mdic_viewport_owner_check")
                        .executes(ctx -> ForgeVoxyCommands.formalMdicViewportOwnerCheck(ctx.getSource())))
                .then(Commands.literal("formal_mdic_viewport_owner_audit")
                        .executes(ctx -> ForgeVoxyCommands.formalMdicViewportOwnerAudit(ctx.getSource())))
                .then(Commands.literal("formal_mdic_viewport_owner_dump")
                        .executes(ctx -> ForgeVoxyCommands.formalMdicViewportOwnerDump(ctx.getSource())))
                .then(Commands.literal("formal_mdic_viewport_owner_clear")
                        .executes(ctx -> ForgeVoxyCommands.formalMdicViewportOwnerClear(ctx.getSource())))
                .then(Commands.literal("qa_k2_formal_mdic_viewport_owner")
                        .executes(ctx -> ForgeVoxyCommands.qaK2FormalMdicViewportOwner(ctx.getSource())))
                .then(Commands.literal("formal_command_generation_owner_enable")
                        .executes(ctx -> ForgeVoxyCommands.formalCommandGenerationOwnerEnable(ctx.getSource())))
                .then(Commands.literal("formal_command_generation_owner_status")
                        .executes(ctx -> ForgeVoxyCommands.formalCommandGenerationOwnerStatus(ctx.getSource())))
                .then(Commands.literal("formal_command_generation_owner_check")
                        .executes(ctx -> ForgeVoxyCommands.formalCommandGenerationOwnerCheck(ctx.getSource())))
                .then(Commands.literal("formal_command_generation_owner_audit")
                        .executes(ctx -> ForgeVoxyCommands.formalCommandGenerationOwnerAudit(ctx.getSource())))
                .then(Commands.literal("formal_command_generation_owner_dump")
                        .executes(ctx -> ForgeVoxyCommands.formalCommandGenerationOwnerDump(ctx.getSource())))
                .then(Commands.literal("formal_command_generation_owner_clear")
                        .executes(ctx -> ForgeVoxyCommands.formalCommandGenerationOwnerClear(ctx.getSource())))
                .then(Commands.literal("qa_k3_formal_command_generation_owner")
                        .executes(ctx -> ForgeVoxyCommands.qaK3FormalCommandGenerationOwner(ctx.getSource())))
                .then(Commands.literal("formal_visibility_owner_enable")
                        .executes(ctx -> ForgeVoxyCommands.formalVisibilityOwnerEnable(ctx.getSource())))
                .then(Commands.literal("formal_visibility_owner_status")
                        .executes(ctx -> ForgeVoxyCommands.formalVisibilityOwnerStatus(ctx.getSource())))
                .then(Commands.literal("formal_visibility_owner_check")
                        .executes(ctx -> ForgeVoxyCommands.formalVisibilityOwnerCheck(ctx.getSource())))
                .then(Commands.literal("formal_visibility_owner_audit")
                        .executes(ctx -> ForgeVoxyCommands.formalVisibilityOwnerAudit(ctx.getSource())))
                .then(Commands.literal("formal_visibility_owner_dump")
                        .executes(ctx -> ForgeVoxyCommands.formalVisibilityOwnerDump(ctx.getSource())))
                .then(Commands.literal("formal_visibility_owner_clear")
                        .executes(ctx -> ForgeVoxyCommands.formalVisibilityOwnerClear(ctx.getSource())))
                .then(Commands.literal("qa_k4_formal_visibility_owner")
                        .executes(ctx -> ForgeVoxyCommands.qaK4FormalVisibilityOwner(ctx.getSource())));
    }
}
