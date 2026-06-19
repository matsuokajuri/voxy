package me.cortex.voxy.forge;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

/**
 * Formal renderer status command registration.
 *
 * <p>This registrar keeps the non-preview formal renderer status surface out of
 * the legacy monolithic command file while the handler implementations are
 * still being untangled.</p>
 */
final class ForgeVoxyFormalRendererCommands {
    private ForgeVoxyFormalRendererCommands() {
    }

    static void register(LiteralArgumentBuilder<CommandSourceStack> root) {
        root.then(Commands.literal("formal_renderer_check")
                        .executes(ctx -> ForgeVoxyCommands.formalRendererCheck(ctx.getSource())))
                .then(Commands.literal("formal_renderer_status")
                        .executes(ctx -> ForgeVoxyCommands.formalRendererStatus(ctx.getSource())))
                .then(Commands.literal("formal_renderer_enable")
                        .executes(ctx -> ForgeVoxyCommands.formalRendererEnable(ctx.getSource())))
                .then(Commands.literal("formal_renderer_disable")
                        .executes(ctx -> ForgeVoxyCommands.formalRendererDisable(ctx.getSource())))
                .then(Commands.literal("formal_renderer_clear")
                        .executes(ctx -> ForgeVoxyCommands.formalRendererClear(ctx.getSource())))
                .then(Commands.literal("formal_renderer_dump_blockers")
                        .executes(ctx -> ForgeVoxyCommands.formalRendererDumpBlockers(ctx.getSource())));
    }
}
