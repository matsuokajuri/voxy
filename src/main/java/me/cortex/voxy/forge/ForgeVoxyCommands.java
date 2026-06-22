package me.cortex.voxy.forge;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

public final class ForgeVoxyCommands {
    private ForgeVoxyCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        var root = Commands.literal("voxy")
                .then(Commands.literal("parity_route_status")
                        .executes(ctx -> parityRouteStatus(ctx.getSource())));

        ForgeVoxyParityCommands.register(root);
        dispatcher.register(root);
    }

    private static int parityRouteStatus(CommandSourceStack source) {
        String message = String.join(" ",
                "Voxy parity route:",
                "originalVoxySourceBaseline=true",
                "deprecatedPrototypeRoutesAbsent=true",
                "formalRendererReady=false",
                "actualRendererDrawEnabled=false",
                "newWorkTarget=original-voxy-parity");
        source.sendSuccess(() -> Component.literal(message), false);
        return 1;
    }

}
