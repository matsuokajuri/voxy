package me.cortex.voxy.forge;

import com.mojang.brigadier.CommandDispatcher;
import me.cortex.voxy.common.world.service.VoxelIngestService;
import me.cortex.voxy.config.ForgeVoxyConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.chunk.LevelChunk;

public final class ForgeVoxyCommands {
    private ForgeVoxyCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("voxy")
                .then(Commands.literal("ingest_current_chunk")
                        .executes(ctx -> ingestCurrentChunk(ctx.getSource()))));
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
            String reason = ForgeVoxyConfig.ENABLE_WORLD_ENGINE_SKELETON.get()
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
}
