package me.cortex.voxy.forge;

import com.mojang.brigadier.CommandDispatcher;
import me.cortex.voxy.common.world.service.VoxelIngestService;
import me.cortex.voxy.config.ForgeVoxyConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.chunk.LevelChunk;

public final class ForgeVoxyCommands {
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
                .then(Commands.literal("mesh_cache_status")
                        .executes(ctx -> meshCacheStatus(ctx.getSource())))
                .then(Commands.literal("mesh_cache_clear")
                        .executes(ctx -> clearMeshCache(ctx.getSource())))
                .then(Commands.literal("gpu_mesh_status")
                        .executes(ctx -> gpuMeshStatus(ctx.getSource())))
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
            String reason = ForgeVoxyConfig.ENABLE_WORLD_ENGINE_SKELETON.get()
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
            String reason = ForgeVoxyConfig.ENABLE_WORLD_ENGINE_SKELETON.get()
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
            String reason = ForgeVoxyConfig.ENABLE_WORLD_ENGINE_SKELETON.get()
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
        String message = String.format(
                "Voxy debug pipeline: engineConfig=%s engine=%s autoIngest=%s autoBuild=%s render=%s simpleGpu=%s dim=%s playerChunk=%s ingestQueue=%d ingestedRecords=%d avgIngestMs=%.2f buildQueue=%d builtRecords=%d failedRecords=%d avgBuildMs=%.2f lastBuildMs=%.2f cache=%d/%d vertices=%d quads=%d bytes=%d dimensions=%s layers=%s gpuBuffers=%d/%d gpuPending=%d gpuRendered=%d gpuChunks=%d gpuVertices=%d gpuLimited=%d gpuSkippedNear=%d gpuSkippedLoaded=%d gpuSkippedFar=%d gpuAvgRenderMs=%.2f gpuMinDistance=%d gpuMaxDistance=%d gpuRenderLoaded=%s gpuKeepCached=%s maxRendered=%d ignoreDepth=%s alpha=%.2f verticalOffset=%.3f stage=%s %s %s",
                ForgeVoxyConfig.ENABLE_WORLD_ENGINE_SKELETON.get(),
                meshBuildStatus.enginePresent(),
                ingestStatus.autoEnabled(),
                meshBuildStatus.autoEnabled(),
                ForgeVoxyConfig.ENABLE_DEBUG_MESH_RENDERER.get(),
                ForgeVoxyConfig.ENABLE_SIMPLE_GPU_MESH_RENDERER.get(),
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
                gpuRenderStats.skippedByDistance(),
                gpuRenderStats.averageRenderMs(),
                ForgeSimpleGpuMeshRenderer.getConfiguredMinRenderDistanceChunks(),
                ForgeGpuMeshUploadManager.getConfiguredRenderDistanceChunks(),
                ForgeSimpleGpuMeshRenderer.shouldRenderLoadedChunks(),
                ForgeGpuMeshUploadManager.keepCachedChunks(),
                ForgeSimpleGpuMeshRenderer.getConfiguredMaxRenderedBuffers(),
                ForgeVoxyConfig.DEBUG_MESH_IGNORE_DEPTH.get(),
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
        var uploadStatus = ForgeVoxyInstance.INSTANCE.getGpuMeshUploadManager().getLastStatus();
        var renderStats = ForgeVoxyInstance.INSTANCE.getSimpleGpuMeshRenderer().getLastFrameStats();
        var minecraft = Minecraft.getInstance();
        String currentDimension = "none";
        String playerChunk = "none";
        if (minecraft.level != null && minecraft.player != null) {
            currentDimension = minecraft.level.dimension().location().toString();
            playerChunk = minecraft.player.chunkPosition().x + "," + minecraft.player.chunkPosition().z;
        }
        String message = String.format(
                "Voxy simple GPU mesh: enabled=%s engine=%s currentDim=%s playerChunk=%s buffers=%d/%d vertices=%d quads=%d bytes=%d dimensions=%s layers=%s pendingUploads=%d uploadBudget=%d uploadedLast=%d failedLast=%d skippedTranslucentUpload=%d cpuCandidates=%d cpuSkippedDistance=%d cpuLimited=%d avgUploadMs=%.2f colorMode=%s keepCached=%s render=%s reason=%s renderDim=%s renderedBuffers=%d renderedChunks=%d renderedVertices=%d skippedNear=%d skippedLoaded=%d skippedFar=%d skippedDimension=%d skippedReleased=%d limitedRender=%d skippedTranslucentRender=%d lastRenderMs=%.2f avgRenderMs=%.2f minDistance=%d maxDistance=%d renderLoadedChunks=%s maxRendered=%d alpha=%.2f stage=%s debugRenderer=%s",
                ForgeVoxyConfig.ENABLE_SIMPLE_GPU_MESH_RENDERER.get(),
                ForgeVoxyInstance.INSTANCE.getCurrentEngineOptional().isPresent(),
                currentDimension,
                playerChunk,
                cacheStatus.buffers(),
                cacheStatus.maxBuffers(),
                cacheStatus.totalVertices(),
                cacheStatus.totalQuads(),
                cacheStatus.totalBytes(),
                cacheStatus.dimensions(),
                cacheStatus.layers(),
                uploadStatus.pendingUploads(),
                uploadStatus.uploadBudget(),
                uploadStatus.uploadedThisFrame(),
                uploadStatus.failedThisFrame(),
                uploadStatus.skippedTranslucent(),
                uploadStatus.candidateCpuEntries(),
                uploadStatus.skippedCpuByDistance(),
                uploadStatus.limitedCpuEntries(),
                uploadStatus.averageUploadMs(),
                uploadStatus.useOriginalColors() ? "original" : "layer-debug",
                uploadStatus.keepCachedChunks(),
                renderStats.rendered(),
                renderStats.reason(),
                renderStats.dimension(),
                renderStats.renderedBuffers(),
                renderStats.renderedChunks(),
                renderStats.renderedVertices(),
                renderStats.skippedNear(),
                renderStats.skippedLoaded(),
                renderStats.skippedByDistance(),
                renderStats.skippedByDimension(),
                renderStats.skippedReleased(),
                renderStats.limitedBuffers(),
                renderStats.skippedTranslucent(),
                renderStats.lastRenderMs(),
                renderStats.averageRenderMs(),
                ForgeSimpleGpuMeshRenderer.getConfiguredMinRenderDistanceChunks(),
                ForgeGpuMeshUploadManager.getConfiguredRenderDistanceChunks(),
                ForgeSimpleGpuMeshRenderer.shouldRenderLoadedChunks(),
                ForgeSimpleGpuMeshRenderer.getConfiguredMaxRenderedBuffers(),
                ForgeSimpleGpuMeshRenderer.getConfiguredAlpha(),
                ForgeSimpleGpuMeshRenderer.getRenderStageName(),
                ForgeVoxyConfig.ENABLE_DEBUG_MESH_RENDERER.get()
        );
        source.sendSuccess(() -> Component.literal(message), false);
        return cacheStatus.buffers();
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

    private static int clearMeshCache(CommandSourceStack source) {
        ForgeVoxyInstance.INSTANCE.getCpuMeshCache().clear();
        ForgeVoxyInstance.INSTANCE.getCpuMeshBuildManager().clear();
        ForgeVoxyInstance.INSTANCE.getGpuMeshUploadManager().clear();
        source.sendSuccess(() -> Component.literal("Voxy: cleared CPU mesh cache, GPU mesh cache, and auto mesh build record."), false);
        return 1;
    }

    private static int clearGpuMeshCache(CommandSourceStack source) {
        ForgeVoxyInstance.INSTANCE.getGpuMeshUploadManager().clear();
        source.sendSuccess(() -> Component.literal("Voxy: cleared simple GPU mesh buffers. CPU mesh cache was left intact."), false);
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
        ForgeVoxyInstance.INSTANCE.getCpuMeshCache().clear();
        ForgeVoxyInstance.INSTANCE.getGpuMeshUploadManager().clear();
        source.sendSuccess(() -> Component.literal("Voxy: cleared debug pipeline ingest records, mesh build records, CPU mesh cache, and GPU mesh cache."), false);
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
        source.sendSuccess(() -> Component.literal("Voxy: cleared auto ingest queue/cache and auto CPU mesh build queue/records."), false);
        return 1;
    }
}
