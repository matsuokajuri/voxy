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
                        .executes(ctx -> parityRouteStatus(ctx.getSource())))
                .then(Commands.literal("original_voxy_storage_status")
                        .executes(ctx -> originalVoxyStorageStatus(ctx.getSource())));

        ForgeVoxyParityCommands.register(root);
        dispatcher.register(root);
    }

    private static int parityRouteStatus(CommandSourceStack source) {
        ForgeOriginalVoxyModelPipeline pipeline = ForgeVoxyInstance.INSTANCE.getOriginalVoxyModelPipeline();
        ForgeOriginalVoxyModelPipelineStats model = pipeline.createStatusSnapshot();
        ForgeOriginalVoxyMdicCommandGenerationStats mdic = pipeline.createMdicCommandGenerationStatusSnapshot();
        String message = String.join(" ",
                "Voxy parity route:",
                "originalVoxySourceBaseline=true",
                "legacyPrototypeRouteStillPresent=true",
                "legacyPrototypeRouteDrivesVisibleRenderer=false",
                "formalRendererReady=" + model.formalRendererReady(),
                "actualRendererDrawEnabled=" + model.actualRendererDrawEnabled(),
                "formalDrawPipelineReady=" + mdic.formalDrawPipelineReady(),
                "earlyUsableLodRendererReady=retired",
                "wholeOriginalModParity=false",
                "newWorkTarget=original-user-features-and-importers");
        source.sendSuccess(() -> Component.literal(message), false);
        return 1;
    }

    private static int originalVoxyStorageStatus(CommandSourceStack source) {
        ForgeVoxyInstance.PersistentStorageStatus status =
                ForgeVoxyInstance.INSTANCE.createPersistentStorageStatusSnapshot();
        String message = String.join(" ",
                "Voxy original storage:",
                "persistentStorageReady=" + status.persistentStorageReady(),
                "storageConfigReady=" + status.storageConfigReady(),
                "storageConfigPath=" + status.storageConfigPath(),
                "storageConfigSource=" + status.storageConfigSource(),
                "storageDisabled=" + status.storageDisabled(),
                "backendChain=" + status.backendChain(),
                "worldIdentifier=" + status.worldIdentifier(),
                "storagePath=" + status.storagePath(),
                "sectionLoadHits=" + status.sectionLoadHits(),
                "sectionLoadMisses=" + status.sectionLoadMisses(),
                "sectionWrites=" + status.sectionWrites(),
                "mappingEntriesLoaded=" + status.mappingEntriesLoaded(),
                "mappingWrites=" + status.mappingWrites(),
                "openCount=" + status.openCount(),
                "reuseCount=" + status.reuseCount(),
                "closingWorldCount=" + status.closingWorldCount());
        source.sendSuccess(() -> Component.literal(message), false);
        return 1;
    }

}
