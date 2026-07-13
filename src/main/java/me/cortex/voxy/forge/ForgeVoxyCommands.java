package me.cortex.voxy.forge;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import me.cortex.voxy.common.world.WorldEngine;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.storage.LevelResource;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

public final class ForgeVoxyCommands {
    private ForgeVoxyCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        var root = Commands.literal("voxy")
                .then(Commands.literal("reload")
                        .executes(ctx -> reload(ctx.getSource())));

        root.then(importCommands());
        root.then(debugCommands());
        dispatcher.register(root);
    }

    private static int reload(CommandSourceStack source) {
        if (!ForgeVoxyInstance.INSTANCE.reloadOriginalVoxyRuntime()) {
            source.sendFailure(Component.literal("Voxy must be enabled in an active client world to use this command"));
            return 1;
        }
        return 0;
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> importCommands() {
        return Commands.literal("import")
                .then(Commands.literal("world")
                        .then(Commands.argument("world_name", StringArgumentType.string())
                                .suggests(ForgeVoxyCommands::importWorldSuggester)
                                .executes(ForgeVoxyCommands::importWorld)))
                .then(Commands.literal("bobby")
                        .then(Commands.argument("world_name", StringArgumentType.string())
                                .suggests(ForgeVoxyCommands::importBobbySuggester)
                                .executes(ForgeVoxyCommands::importBobby)))
                .then(Commands.literal("distant_horizons")
                        .then(Commands.argument("sqlDbPath", StringArgumentType.string())
                                .executes(ForgeVoxyCommands::importDistantHorizons)))
                .then(Commands.literal("raw")
                        .then(Commands.argument("path", StringArgumentType.string())
                                .executes(ForgeVoxyCommands::importRaw)))
                .then(Commands.literal("zip")
                        .then(Commands.argument("zipPath", StringArgumentType.string())
                                .executes(ForgeVoxyCommands::importZip)
                                .then(Commands.argument("innerPath", StringArgumentType.string())
                                        .executes(ForgeVoxyCommands::importZip))))
                .then(Commands.literal("current")
                        .executes(ForgeVoxyCommands::importCurrentWorld))
                .then(Commands.literal("cancel")
                        .executes(ForgeVoxyCommands::cancelImport));
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> debugCommands() {
        return Commands.literal("debug")
                .then(Commands.literal("verifyTLNChildMask")
                        .executes(context -> verifyTopLevelNodeMasks(context, false))
                        .then(Commands.argument("attemptRepair", BoolArgumentType.bool())
                                .executes(context -> verifyTopLevelNodeMasks(
                                        context,
                                        BoolArgumentType.getBool(context, "attemptRepair")))));
    }

    private static int verifyTopLevelNodeMasks(
            CommandContext<CommandSourceStack> context,
            boolean attemptRepair) {
        WorldEngine engine = ForgeVoxyInstance.INSTANCE.getCurrentEngineOptional().orElse(null);
        if (engine == null) {
            context.getSource().sendFailure(Component.literal(
                    "Voxy must be enabled in an active client world to use this command"));
            return 1;
        }
        DebugUtils.verifyAllTopLevelNodes(engine, attemptRepair);
        return 0;
    }

    private static boolean fileBasedImporter(File directory) {
        ForgeVoxyInstance instance = ForgeVoxyInstance.INSTANCE;
        Minecraft minecraft = Minecraft.getInstance();
        WorldEngine engine = instance.getCurrentEngineOptional().orElse(null);
        if (engine == null || minecraft.level == null) {
            return false;
        }
        return instance.getImportManager().makeAndRunIfNone(engine, () -> {
            WorldImporter importer = new WorldImporter(
                    engine,
                    minecraft.level,
                    instance.getOriginalVoxyModelPipeline().getServiceManager(),
                    instance::canRunOriginalVoxyImportWork);
            importer.importRegionDirectoryAsync(directory);
            return importer;
        });
    }

    private static int importRaw(CommandContext<CommandSourceStack> context) {
        return fileBasedImporter(new File(StringArgumentType.getString(context, "path"))) ? 0 : 1;
    }

    private static int importCurrentWorld(CommandContext<CommandSourceStack> context) {
        Minecraft minecraft = Minecraft.getInstance();
        var localServer = minecraft.getSingleplayerServer();
        if (localServer == null || minecraft.level == null) {
            context.getSource().sendFailure(Component.literal("You must be in single player to use this command"));
            return 1;
        }
        Path regionPath = DimensionType.getStorageFolder(
                        minecraft.level.dimension(),
                        localServer.getWorldPath(LevelResource.ROOT))
                .resolve("region");
        if (!Files.isDirectory(regionPath)) {
            context.getSource().sendFailure(Component.literal("Cannot find region folder for current dimension"));
            return 1;
        }
        return fileBasedImporter(regionPath.toFile()) ? 0 : 1;
    }

    private static int importWorld(CommandContext<CommandSourceStack> context) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return 1;
        }
        String name = StringArgumentType.getString(context, "world_name");
        Path file = minecraft.gameDirectory.toPath().resolve("saves").resolve(name);
        String normalizedName = name.toLowerCase(Locale.ROOT);
        if (normalizedName.endsWith("/")) {
            normalizedName = normalizedName.substring(0, normalizedName.length() - 1);
        }
        if (Files.exists(file.resolve("level.dat"))) {
            File dimensionRegions = DimensionType.getStorageFolder(minecraft.level.dimension(), file)
                    .resolve("region")
                    .toFile();
            return dimensionRegions.isDirectory() && fileBasedImporter(dimensionRegions) ? 0 : 1;
        }
        if (!normalizedName.endsWith("region")) {
            file = file.resolve("region");
        }
        return fileBasedImporter(file.toFile()) ? 0 : 1;
    }

    private static int importBobby(CommandContext<CommandSourceStack> context) {
        String worldName = StringArgumentType.getString(context, "world_name");
        Path directory = Minecraft.getInstance().gameDirectory.toPath().resolve(".bobby").resolve(worldName);
        return fileBasedImporter(directory.toFile()) ? 0 : 1;
    }

    private static int importDistantHorizons(CommandContext<CommandSourceStack> context) {
        File requestedPath = new File(StringArgumentType.getString(context, "sqlDbPath"));
        File databaseFile = ForgeOriginalVoxyDhImporter.resolveDatabaseFile(requestedPath);
        if (databaseFile == null || !databaseFile.isFile()) {
            context.getSource().sendFailure(Component.literal(
                    "Cannot find Distant Horizons database: "
                            + (databaseFile == null ? "null" : databaseFile.getAbsolutePath())));
            return 1;
        }

        ForgeVoxyInstance instance = ForgeVoxyInstance.INSTANCE;
        Minecraft minecraft = Minecraft.getInstance();
        WorldEngine engine = instance.getCurrentEngineOptional().orElse(null);
        if (engine == null || minecraft.level == null) {
            context.getSource().sendFailure(Component.literal(
                    "Voxy must be enabled in an active client world to import Distant Horizons data"));
            return 1;
        }

        boolean started = instance.getImportManager().makeAndRunIfNone(engine, () ->
                new ForgeOriginalVoxyDhImporter(
                        databaseFile,
                        engine,
                        minecraft.level,
                        instance.getOriginalVoxyModelPipeline().getServiceManager(),
                        instance::canRunOriginalVoxyImportWork));
        if (!started) {
            context.getSource().sendFailure(Component.literal("A Voxy import is already active"));
            return 1;
        }
        return 0;
    }

    private static int importZip(CommandContext<CommandSourceStack> context) {
        File zip = new File(StringArgumentType.getString(context, "zipPath"));
        String innerDirectory = "region/";
        try {
            innerDirectory = StringArgumentType.getString(context, "innerPath");
        } catch (IllegalArgumentException ignored) {
        }

        ForgeVoxyInstance instance = ForgeVoxyInstance.INSTANCE;
        Minecraft minecraft = Minecraft.getInstance();
        WorldEngine engine = instance.getCurrentEngineOptional().orElse(null);
        if (engine == null || minecraft.level == null) {
            return 1;
        }
        String finalInnerDirectory = innerDirectory;
        return instance.getImportManager().makeAndRunIfNone(engine, () -> {
            WorldImporter importer = new WorldImporter(
                    engine,
                    minecraft.level,
                    instance.getOriginalVoxyModelPipeline().getServiceManager(),
                    instance::canRunOriginalVoxyImportWork);
            importer.importZippedRegionDirectoryAsync(zip, finalInnerDirectory);
            return importer;
        }) ? 0 : 1;
    }

    private static int cancelImport(CommandContext<CommandSourceStack> context) {
        WorldEngine engine = ForgeVoxyInstance.INSTANCE.getCurrentEngineOptional().orElse(null);
        return engine != null && ForgeVoxyInstance.INSTANCE.getImportManager().cancelImport(engine) ? 0 : 1;
    }

    private static CompletableFuture<Suggestions> importWorldSuggester(
            CommandContext<CommandSourceStack> context,
            SuggestionsBuilder builder) {
        return fileDirectorySuggester(Minecraft.getInstance().gameDirectory.toPath().resolve("saves"), builder);
    }

    private static CompletableFuture<Suggestions> importBobbySuggester(
            CommandContext<CommandSourceStack> context,
            SuggestionsBuilder builder) {
        return fileDirectorySuggester(Minecraft.getInstance().gameDirectory.toPath().resolve(".bobby"), builder);
    }

    private static CompletableFuture<Suggestions> fileDirectorySuggester(Path directory, SuggestionsBuilder builder) {
        String value = builder.getRemaining().replace("\\\\", "\\").replace("\\", "/");
        if (value.startsWith("\"")) {
            value = value.substring(1);
        }
        if (value.endsWith("\"")) {
            value = value.substring(0, value.length() - 1);
        }
        String remaining = value;
        if (value.contains("/")) {
            int index = value.lastIndexOf('/');
            remaining = value.substring(index + 1);
            try {
                directory = directory.resolve(value.substring(0, index));
            } catch (Exception e) {
                return Suggestions.empty();
            }
            value = value.substring(0, index + 1);
        } else {
            value = "";
        }

        try (var worlds = Files.list(directory)) {
            for (Path world : worlds.toList()) {
                if (!Files.isDirectory(world)) {
                    continue;
                }
                String worldName = world.getFileName().toString();
                if (worldName.equals(remaining)) {
                    continue;
                }
                if (SharedSuggestionProvider.matchesSubStr(remaining, worldName)
                        || SharedSuggestionProvider.matchesSubStr(remaining, '"' + worldName)) {
                    builder.suggest(StringArgumentType.escapeIfRequired(value + worldName + "/"));
                }
            }
        } catch (IOException ignored) {
        }
        return builder.buildFuture();
    }

}
