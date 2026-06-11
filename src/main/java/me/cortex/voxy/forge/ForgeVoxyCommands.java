package me.cortex.voxy.forge;

import com.mojang.brigadier.CommandDispatcher;
import me.cortex.voxy.common.world.service.VoxelIngestService;
import me.cortex.voxy.config.SimpleGpuMeshLoadedChunkSkipMode;
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
                .then(Commands.literal("build_current_chunk_built_section")
                        .executes(ctx -> buildCurrentChunkBuiltSection(ctx.getSource())))
                .then(Commands.literal("built_section_cache_status")
                        .executes(ctx -> builtSectionCacheStatus(ctx.getSource())))
                .then(Commands.literal("built_section_cache_clear")
                        .executes(ctx -> clearBuiltSectionCache(ctx.getSource())))
                .then(Commands.literal("built_section_build_clear")
                        .executes(ctx -> clearBuiltSectionBuildState(ctx.getSource())))
                .then(Commands.literal("mesh_cache_status")
                        .executes(ctx -> meshCacheStatus(ctx.getSource())))
                .then(Commands.literal("mesh_cache_clear")
                        .executes(ctx -> clearMeshCache(ctx.getSource())))
                .then(Commands.literal("gpu_mesh_status")
                        .executes(ctx -> gpuMeshStatus(ctx.getSource())))
                .then(Commands.literal("lod_visibility_status")
                        .executes(ctx -> lodVisibilityStatus(ctx.getSource())))
                .then(Commands.literal("lod_overlay_debug")
                        .executes(ctx -> lodOverlayDebug(ctx.getSource())))
                .then(Commands.literal("lod_mode_advice")
                        .executes(ctx -> lodModeAdvice(ctx.getSource())))
                .then(Commands.literal("preset")
                        .then(Commands.literal("off")
                                .executes(ctx -> applyPresetOff(ctx.getSource())))
                        .then(Commands.literal("overlay")
                                .executes(ctx -> applyPresetOverlay(ctx.getSource())))
                        .then(Commands.literal("lod")
                                .executes(ctx -> applyPresetLod(ctx.getSource())))
                        .then(Commands.literal("clear")
                                .executes(ctx -> clearPreset(ctx.getSource())))
                        .then(Commands.literal("status")
                                .executes(ctx -> presetStatus(ctx.getSource()))))
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
            String reason = ForgeVoxyRuntimeOverrides.enabledWorldEngineSkeleton()
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
            String reason = ForgeVoxyRuntimeOverrides.enabledWorldEngineSkeleton()
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
            String reason = ForgeVoxyRuntimeOverrides.enabledWorldEngineSkeleton()
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
            String reason = ForgeVoxyRuntimeOverrides.enabledWorldEngineSkeleton()
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

    private static int buildCurrentChunkBuiltSection(CommandSourceStack source) {
        var minecraft = Minecraft.getInstance();
        var player = minecraft.player;
        var level = minecraft.level;
        if (player == null || level == null) {
            source.sendFailure(Component.literal("Voxy: no client world is active."));
            return 0;
        }

        var engine = ForgeVoxyInstance.INSTANCE.getCurrentEngineOptional();
        if (engine.isEmpty()) {
            String reason = ForgeVoxyRuntimeOverrides.enabledWorldEngineSkeleton()
                    ? "no WorldEngine is active for the current world"
                    : "enableWorldEngineSkeleton is false";
            source.sendFailure(Component.literal("Voxy: cannot build current chunk BuiltSection data; " + reason + "."));
            return 0;
        }

        ForgeCpuMeshBuildResult cpuResult = null;
        try {
            long start = System.nanoTime();
            int chunkX = player.chunkPosition().x;
            int chunkZ = player.chunkPosition().z;
            LevelChunk chunk = level.getChunkSource().getChunk(chunkX, chunkZ, ChunkStatus.FULL, false);
            if (chunk == null) {
                source.sendFailure(Component.literal("Voxy: current chunk is not loaded."));
                return 0;
            }

            String dimension = level.dimension().location().toString();
            cpuResult = ForgeCpuMeshBuilder.buildCurrentChunk(engine.get(), chunk, level, dimension);
            var cpuStats = cpuResult.stats();
            if (cpuStats.sectionsFound() == 0) {
                source.sendFailure(Component.literal("Voxy: no ingested Voxy section found for current chunk; run /voxy ingest_current_chunk first."));
                return 0;
            }
            if (cpuResult.sections().isEmpty()) {
                source.sendFailure(Component.literal("Voxy: CPU mesh builder produced no section mesh for current chunk."));
                return 0;
            }

            var builtResult = ForgeVoxyBuiltSectionBuilder.fromCpuMesh(cpuResult);
            var cache = ForgeVoxyInstance.INSTANCE.getVoxyGeometryCache();
            cache.setActiveDimension(dimension);
            int cacheEntriesWritten = cache.putAll(builtResult.sections());
            double elapsedMs = (System.nanoTime() - start) / 1_000_000.0;
            var stats = builtResult.stats()
                    .withCacheEntriesWritten(cacheEntriesWritten)
                    .withElapsedMs(elapsedMs);
            var cacheStatus = cache.createStatusSnapshot();
            String message = String.format(
                    "Voxy BuiltSection CPU: %s chunk %d,%d sourceCpuEntries=%d emptySections=%d sectionsBuilt=%d builtSectionCount=%d naiveQuads=%d mergedQuads=%d quadsAfterMerge=%d mergeRatio=%.3f averageQuadArea=%.2f maxQuadLength=%d maxQuadWidth=%d skippedTranslucent=%d skippedNonMergeable=%d quads=%d geometryBytes=%d occupancyBytes=%d offsetsSemantic=%s geometryFormat=%s knownBits=%s knownFields=%s uniqueModelIds=%d missingModelIds=%d modelIdOverflow=%d uniqueBiomeIds=%d missingBiomeIds=%d biomeIdOverflow=%d missingTexture=%d missingGreedy=%d finalFormat=%s occupancyPresent=%s samplePosition=%s sampleAabb=%s sampleRecord=%s decoded=\"%s\" sampleMergedRecord=%s sampleMergedDecoded=\"%s\" offsets=%s namedOffsets=%s cacheWritten=%d cacheEntries=%d elapsed=%.2fms",
                    stats.dimension(),
                    stats.chunkX(),
                    stats.chunkZ(),
                    stats.sourceCpuEntries(),
                    stats.emptySections(),
                    cpuStats.sectionsBuilt(),
                    stats.sectionsBuilt(),
                    stats.naiveQuads(),
                    stats.mergedQuads(),
                    stats.quadsAfterMerge(),
                    stats.mergeRatio(),
                    stats.averageQuadArea(),
                    stats.maxQuadLength(),
                    stats.maxQuadWidth(),
                    stats.skippedTranslucent(),
                    stats.skippedNonMergeable(),
                    stats.totalQuads(),
                    stats.geometryBytes(),
                    stats.occupancyBytes(),
                    stats.offsetsSemantic(),
                    stats.geometryFormat(),
                    stats.knownBitsMask(),
                    stats.knownFields(),
                    stats.uniqueModelIds(),
                    stats.missingModelId(),
                    stats.modelIdOverflow(),
                    stats.uniqueBiomeIds(),
                    stats.missingBiomeId(),
                    stats.biomeIdOverflow(),
                    stats.missingTexture(),
                    stats.missingGreedy(),
                    stats.finalRendererFormat(),
                    stats.occupancyPresent(),
                    stats.samplePosition(),
                    stats.aabbSample(),
                    stats.sampleRecordHex(),
                    stats.sampleDecodedRecord(),
                    stats.sampleMergedRecordHex(),
                    stats.sampleMergedDecodedRecord(),
                    stats.offsetsSummary(),
                    stats.offsetsNamed(),
                    stats.cacheEntriesWritten(),
                    cacheStatus.entries(),
                    stats.elapsedMs()
            );
            VoxyForge.LOGGER.info(message);
            source.sendSuccess(() -> Component.literal(message + " (partial adapter format; not final renderer format)"), false);
            return stats.sectionsBuilt();
        } catch (OutOfMemoryError e) {
            VoxyForge.LOGGER.error("Failed to allocate current chunk BuiltSection data", e);
            source.sendFailure(Component.literal("Voxy: BuiltSection build ran out of memory."));
            return 0;
        } catch (Exception e) {
            VoxyForge.LOGGER.error("Failed to build current chunk BuiltSection data", e);
            source.sendFailure(Component.literal("Voxy: BuiltSection build failed: " + e.getMessage()));
            return 0;
        } finally {
            closeTemporaryCpuMesh(cpuResult);
        }
    }

    private static int builtSectionCacheStatus(CommandSourceStack source) {
        var status = ForgeVoxyInstance.INSTANCE.getVoxyGeometryCache().createStatusSnapshot();
        var buildStatus = ForgeVoxyInstance.INSTANCE.getBuiltSectionBuildManager().createStatusSnapshot();
        String buildDimension = buildStatus.dimension() == null ? "none" : buildStatus.dimension();
        String message = String.format(
                "Voxy BuiltSection cache: auto=%s engine=%s buildDim=%s queue=%d builtRecords=%d failedRecords=%d radius=%d maxPerTick=%d cooldown=%d lastBuildMs=%.2f avgBuildMs=%.2f entries=%d/%d totalSections=%d totalNaiveQuads=%d totalMergedQuads=%d totalAverageQuadArea=%.2f totalSkippedTranslucent=%d totalSkippedNonMergeable=%d totalQuads=%d totalGeometryBytes=%d totalOccupancyBytes=%d finalFormatCount=%d partialFormatCount=%d partialOriginalBitLayoutCount=%d uniqueModelIds=%d missingModelRecords=%d runtimeModelMapperSize=%d uniqueBiomeIds=%d missingBiomeRecords=%d geometryFormat=%s closed=%d evicted=%d replaced=%d firstPosition=%s firstAabb=%s firstOffsets=%s firstNamedOffsets=%s offsetsSemantic=%s sampleRecord=%s decoded=\"%s\"",
                buildStatus.autoEnabled(),
                buildStatus.enginePresent(),
                buildDimension,
                buildStatus.queuedChunks(),
                buildStatus.builtChunks(),
                buildStatus.failedChunks(),
                buildStatus.radius(),
                buildStatus.maxChunksPerTick(),
                buildStatus.cooldownTicks(),
                buildStatus.lastBuildMs(),
                buildStatus.averageMs(),
                status.entries(),
                status.maxEntries(),
                status.entries(),
                status.totalNaiveQuads(),
                status.totalMergedQuads(),
                status.totalAverageQuadArea(),
                status.totalSkippedTranslucent(),
                status.totalSkippedNonMergeable(),
                status.totalQuads(),
                status.totalGeometryBytes(),
                status.totalOccupancyBytes(),
                status.finalFormatEntries(),
                status.partialFormatEntries(),
                status.partialOriginalBitLayoutEntries(),
                status.totalUniqueModelIds(),
                status.missingModelRecords(),
                status.runtimeModelMapperSize(),
                status.totalUniqueBiomeIds(),
                status.missingBiomeRecords(),
                status.geometryFormat(),
                status.closedCount(),
                status.evictedCount(),
                status.replacedCount(),
                status.firstEntryPosition(),
                status.firstEntryAabb(),
                status.firstEntryOffsets(),
                status.firstEntryNamedOffsets(),
                ForgeVoxyBuiltSectionBuilder.OFFSETS_SEMANTIC,
                status.sampleRecordHex(),
                status.sampleDecodedRecord()
        );
        source.sendSuccess(() -> Component.literal(message), false);
        return status.entries();
    }

    private static int clearBuiltSectionCache(CommandSourceStack source) {
        ForgeVoxyInstance.INSTANCE.getBuiltSectionBuildManager().clear();
        ForgeVoxyInstance.INSTANCE.getVoxyGeometryCache().clear();
        source.sendSuccess(() -> Component.literal("Voxy: cleared CPU-only BuiltSection cache, closed all partial geometry buffers, and cleared auto BuiltSection build records."), false);
        return 1;
    }

    private static int clearBuiltSectionBuildState(CommandSourceStack source) {
        ForgeVoxyInstance.INSTANCE.getBuiltSectionBuildManager().clear();
        source.sendSuccess(() -> Component.literal("Voxy: cleared auto BuiltSection build queue and built-record. The BuiltSection cache was left intact."), false);
        return 1;
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
        var presetStatus = ForgeVoxyRuntimeOverrides.createStatusSnapshot();
        String message = String.format(
                "Voxy debug pipeline: preset=%s overrides=%s engineConfig=%s engine=%s autoIngest=%s autoBuild=%s render=%s simpleGpu=%s dim=%s playerChunk=%s ingestQueue=%d ingestedRecords=%d avgIngestMs=%.2f buildQueue=%d builtRecords=%d failedRecords=%d avgBuildMs=%.2f lastBuildMs=%.2f cache=%d/%d vertices=%d quads=%d bytes=%d dimensions=%s layers=%s gpuBuffers=%d/%d gpuPending=%d gpuRendered=%d gpuChunks=%d gpuVertices=%d gpuLimited=%d gpuSkippedNear=%d gpuSkippedLoaded=%d gpuSkippedLoadedState=%d gpuSkippedRenderDistance=%d gpuSkippedFar=%d gpuAvgRenderMs=%.2f gpuMinDistance=%d gpuMaxDistance=%d gpuRenderLoaded=%s gpuSkipMode=%s gpuLoadedMargin=%d gpuKeepCached=%s maxRendered=%d ignoreDepth=%s alpha=%.2f verticalOffset=%.3f stage=%s %s %s",
                presetStatus.presetName(),
                presetStatus.hasOverrides(),
                ForgeVoxyRuntimeOverrides.enabledWorldEngineSkeleton(),
                meshBuildStatus.enginePresent(),
                ingestStatus.autoEnabled(),
                meshBuildStatus.autoEnabled(),
                ForgeVoxyRuntimeOverrides.enableDebugMeshRenderer(),
                ForgeVoxyRuntimeOverrides.enableSimpleGpuMeshRenderer(),
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
                gpuRenderStats.skippedLoadedState(),
                gpuRenderStats.skippedRenderDistance(),
                gpuRenderStats.skippedByDistance(),
                gpuRenderStats.averageRenderMs(),
                ForgeSimpleGpuMeshRenderer.getConfiguredMinRenderDistanceChunks(),
                ForgeGpuMeshUploadManager.getConfiguredRenderDistanceChunks(),
                ForgeSimpleGpuMeshRenderer.shouldRenderLoadedChunks(),
                ForgeSimpleGpuMeshRenderer.getConfiguredLoadedChunkSkipMode(),
                ForgeSimpleGpuMeshRenderer.getConfiguredLoadedChunkMargin(),
                ForgeGpuMeshUploadManager.keepCachedChunks(),
                ForgeSimpleGpuMeshRenderer.getConfiguredMaxRenderedBuffers(),
                ForgeSimpleGpuMeshRenderer.shouldIgnoreDepth(),
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
        LodVisibilityContext visibilityContext = createLodVisibilityContext(minecraft);
        int clientRenderDistance = visibilityContext == null ? -1 : visibilityContext.clientRenderDistance();
        int renderableCachedChunks = visibilityContext == null ? 0 : visibilityContext.visibility().renderableChunks();
        String noRenderReason = formatGpuNoRenderReason(renderStats, visibilityContext);
        String advice = formatLodVisibilityAdvice(cacheStatus, renderStats, visibilityContext);
        if (minecraft.level != null && minecraft.player != null) {
            currentDimension = minecraft.level.dimension().location().toString();
            playerChunk = minecraft.player.chunkPosition().x + "," + minecraft.player.chunkPosition().z;
        }
        String message = String.format(
                "Voxy simple GPU mesh: preset=%s overrides=%s enabled=%s engine=%s currentDim=%s playerChunk=%s vanillaRenderDistance=%d minDistance=%d maxDistance=%d renderLoadedChunks=%s skipMode=%s loadedMargin=%d keepCached=%s buffers=%d/%d renderableCachedChunks=%d vertices=%d quads=%d bytes=%d dimensions=%s layers=%s pendingUploads=%d uploadBudget=%d uploadedLast=%d failedLast=%d skippedTranslucentUpload=%d cpuCandidates=%d cpuSkippedDistance=%d cpuLimited=%d avgUploadMs=%.2f colorMode=%s ignoreDepth=%s verticalOffset=%.3f render=%s reason=%s noRenderReason=%s renderDim=%s candidateBuffers=%d renderedBuffers=%d renderedChunks=%d renderedVertices=%d skippedNear=%d skippedLoaded=%d skippedLoadedState=%d skippedRenderDistance=%d skippedFar=%d skippedDimension=%d skippedReleased=%d limitedRender=%d skippedTranslucentRender=%d lastRenderMs=%.2f avgRenderMs=%.2f maxRendered=%d alpha=%.2f stage=%s debugRenderer=%s advice=%s",
                ForgeVoxyRuntimeOverrides.presetName(),
                ForgeVoxyRuntimeOverrides.hasOverrides(),
                ForgeVoxyRuntimeOverrides.enableSimpleGpuMeshRenderer(),
                ForgeVoxyInstance.INSTANCE.getCurrentEngineOptional().isPresent(),
                currentDimension,
                playerChunk,
                clientRenderDistance,
                ForgeSimpleGpuMeshRenderer.getConfiguredMinRenderDistanceChunks(),
                ForgeGpuMeshUploadManager.getConfiguredRenderDistanceChunks(),
                ForgeSimpleGpuMeshRenderer.shouldRenderLoadedChunks(),
                ForgeSimpleGpuMeshRenderer.getConfiguredLoadedChunkSkipMode(),
                ForgeSimpleGpuMeshRenderer.getConfiguredLoadedChunkMargin(),
                ForgeGpuMeshUploadManager.keepCachedChunks(),
                cacheStatus.buffers(),
                cacheStatus.maxBuffers(),
                renderableCachedChunks,
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
                ForgeSimpleGpuMeshRenderer.shouldIgnoreDepth(),
                ForgeSimpleGpuMeshRenderer.getConfiguredVerticalOffsetBlocks(),
                renderStats.rendered(),
                renderStats.reason(),
                noRenderReason,
                renderStats.dimension(),
                renderStats.candidateBuffers(),
                renderStats.renderedBuffers(),
                renderStats.renderedChunks(),
                renderStats.renderedVertices(),
                renderStats.skippedNear(),
                renderStats.skippedLoaded(),
                renderStats.skippedLoadedState(),
                renderStats.skippedRenderDistance(),
                renderStats.skippedByDistance(),
                renderStats.skippedByDimension(),
                renderStats.skippedReleased(),
                renderStats.limitedBuffers(),
                renderStats.skippedTranslucent(),
                renderStats.lastRenderMs(),
                renderStats.averageRenderMs(),
                ForgeSimpleGpuMeshRenderer.getConfiguredMaxRenderedBuffers(),
                ForgeSimpleGpuMeshRenderer.getConfiguredAlpha(),
                ForgeSimpleGpuMeshRenderer.getRenderStageName(),
                ForgeVoxyRuntimeOverrides.enableDebugMeshRenderer(),
                advice
        );
        source.sendSuccess(() -> Component.literal(message), false);
        return cacheStatus.buffers();
    }

    private static int lodVisibilityStatus(CommandSourceStack source) {
        var minecraft = Minecraft.getInstance();
        LodVisibilityContext context = createLodVisibilityContext(minecraft);
        if (context == null) {
            source.sendFailure(Component.literal("Voxy LoD visibility: no client world is active."));
            return 0;
        }

        var cacheStatus = ForgeVoxyInstance.INSTANCE.getGpuMeshCache().createStatusSnapshot();
        var renderStats = ForgeVoxyInstance.INSTANCE.getSimpleGpuMeshRenderer().getLastFrameStats();
        var visibility = context.visibility();
        String advice = formatLodVisibilityAdvice(cacheStatus, renderStats, context);
        String message = String.format(
                "Voxy LoD visibility: preset=%s overrides=%s dim=%s playerChunk=%d,%d vanillaRenderDistance=%d minDistance=%d maxDistance=%d renderLoadedChunks=%s skipMode=%s loadedMargin=%d keepCached=%s gpuBuffers=%d/%d cachedChunks=%d windowChunks=%d loadedStateChunks=%d chunksInsideVanillaDistance=%d chunksOutsideVanillaDistance=%d renderableChunks=%d candidateBuffers=%d renderableBuffers=%d skippedNear=%d skippedFar=%d skippedLoadedChunks=%d skippedLoadedBuffers=%d skippedLoadedStateChunks=%d skippedLoadedStateBuffers=%d skippedRenderDistanceChunks=%d skippedRenderDistanceBuffers=%d skippedDimension=%d skippedReleased=%d skippedTranslucent=%d nearestRenderable=%s farthestRenderable=%s advice=%s",
                ForgeVoxyRuntimeOverrides.presetName(),
                ForgeVoxyRuntimeOverrides.hasOverrides(),
                context.dimension(),
                context.playerChunkX(),
                context.playerChunkZ(),
                context.clientRenderDistance(),
                context.minDistance(),
                context.maxDistance(),
                context.renderLoadedChunks(),
                context.loadedChunkSkipMode(),
                context.loadedChunkMargin(),
                context.keepCachedChunks(),
                cacheStatus.buffers(),
                cacheStatus.maxBuffers(),
                visibility.cachedChunks(),
                visibility.chunksWithinDistanceWindow(),
                visibility.loadedStateChunks(),
                visibility.chunksWithinVanillaRenderDistance(),
                visibility.chunksOutsideVanillaRenderDistance(),
                visibility.renderableChunks(),
                visibility.candidateBuffers(),
                visibility.renderableBuffers(),
                visibility.skippedNear(),
                visibility.skippedFar(),
                visibility.skippedLoadedChunks(),
                visibility.skippedLoaded(),
                visibility.skippedLoadedStateChunks(),
                visibility.skippedLoadedState(),
                visibility.skippedRenderDistanceChunks(),
                visibility.skippedRenderDistance(),
                visibility.skippedByDimension(),
                visibility.skippedReleased(),
                visibility.skippedTranslucent(),
                formatDistance(visibility.nearestRenderableDistance()),
                formatDistance(visibility.farthestRenderableDistance()),
                advice
        );
        source.sendSuccess(() -> Component.literal(message), false);
        return visibility.renderableChunks();
    }

    private static int lodOverlayDebug(CommandSourceStack source) {
        return applyPresetOverlay(source);
    }

    private static int lodModeAdvice(CommandSourceStack source) {
        var minecraft = Minecraft.getInstance();
        LodVisibilityContext context = createLodVisibilityContext(minecraft);
        if (context == null) {
            source.sendFailure(Component.literal("Voxy LoD advice: no client world is active."));
            return 0;
        }

        var cacheStatus = ForgeVoxyInstance.INSTANCE.getGpuMeshCache().createStatusSnapshot();
        var renderStats = ForgeVoxyInstance.INSTANCE.getSimpleGpuMeshRenderer().getLastFrameStats();
        var visibility = context.visibility();
        String advice = formatLodVisibilityAdvice(cacheStatus, renderStats, context);
        String noRenderReason = formatGpuNoRenderReason(renderStats, context);
        String message = String.format(
                "Voxy LoD advice: preset=%s overrides=%s dim=%s playerChunk=%d,%d minecraftRenderDistance=%d lodMin=%d lodMax=%d renderLoadedChunks=%s skipMode=%s loadedMargin=%d cachedChunks=%d windowChunks=%d loadedStateChunks=%d chunksInsideVanillaDistance=%d chunksOutsideVanillaDistance=%d gpuBuffers=%d/%d renderableChunks=%d renderedBuffers=%d candidateBuffers=%d skippedLoadedChunks=%d skippedLoadedBuffers=%d skippedLoadedStateChunks=%d skippedLoadedStateBuffers=%d skippedRenderDistanceChunks=%d skippedRenderDistanceBuffers=%d skippedNear=%d skippedFar=%d noRenderReason=%s recommendation=%s",
                ForgeVoxyRuntimeOverrides.presetName(),
                ForgeVoxyRuntimeOverrides.hasOverrides(),
                context.dimension(),
                context.playerChunkX(),
                context.playerChunkZ(),
                context.clientRenderDistance(),
                context.minDistance(),
                context.maxDistance(),
                context.renderLoadedChunks(),
                context.loadedChunkSkipMode(),
                context.loadedChunkMargin(),
                visibility.cachedChunks(),
                visibility.chunksWithinDistanceWindow(),
                visibility.loadedStateChunks(),
                visibility.chunksWithinVanillaRenderDistance(),
                visibility.chunksOutsideVanillaRenderDistance(),
                cacheStatus.buffers(),
                cacheStatus.maxBuffers(),
                visibility.renderableChunks(),
                renderStats.renderedBuffers(),
                renderStats.candidateBuffers(),
                visibility.skippedLoadedChunks(),
                visibility.skippedLoaded(),
                visibility.skippedLoadedStateChunks(),
                visibility.skippedLoadedState(),
                visibility.skippedRenderDistanceChunks(),
                visibility.skippedRenderDistance(),
                visibility.skippedNear(),
                visibility.skippedFar(),
                noRenderReason,
                advice
        );
        source.sendSuccess(() -> Component.literal(message), false);
        return visibility.renderableChunks();
    }

    private static int applyPresetOff(CommandSourceStack source) {
        ForgeVoxyRuntimeOverrides.applyOffPreset();
        clearRuntimePipeline();
        ForgeVoxyInstance.INSTANCE.closeActiveWorld();
        source.sendSuccess(() -> Component.literal("Voxy preset off: runtime overrides disabled engine, auto ingest, auto CPU mesh build, simple GPU renderer, and debug renderer. Overrides are not written to toml."), false);
        return 1;
    }

    private static int applyPresetOverlay(CommandSourceStack source) {
        ForgeVoxyRuntimeOverrides.applyOverlayPreset();
        boolean engineReady = ForgeVoxyInstance.INSTANCE.ensureActiveWorldSkeletonForCurrentWorldIfAllowed();
        String message = "Voxy preset overlay: runtime-only overlay debug applied, not written to toml. "
                + "Effective values: engine=true autoIngest=true autoCpuMesh=true simpleGpu=true debugRenderer=false minDistance=0 maxDistance=64 renderLoadedChunks=true skipMode=DISABLED loadedMargin=0 keepCached=true colors=layer-debug ignoreDepth=true verticalOffset=0.05. "
                + (engineReady ? "WorldEngine is active." : "No active client world was found; enter or re-enter a world to create the WorldEngine.");
        source.sendSuccess(() -> Component.literal(message), false);
        return 1;
    }

    private static int applyPresetLod(CommandSourceStack source) {
        ForgeVoxyRuntimeOverrides.applyLodPreset();
        boolean engineReady = ForgeVoxyInstance.INSTANCE.ensureActiveWorldSkeletonForCurrentWorldIfAllowed();
        String message = "Voxy preset lod: runtime-only cached LoD mode applied, not written to toml. "
                + "Effective values: engine=true autoIngest=true autoCpuMesh=true simpleGpu=true debugRenderer=false minDistance=5 maxDistance=64 renderLoadedChunks=false skipMode=BY_RENDER_DISTANCE loadedMargin=0 keepCached=true colors=original ignoreDepth=false verticalOffset=0.0. "
                + (engineReady ? "WorldEngine is active." : "No active client world was found; enter or re-enter a world to create the WorldEngine.");
        source.sendSuccess(() -> Component.literal(message), false);
        return 1;
    }

    private static int clearPreset(CommandSourceStack source) {
        ForgeVoxyRuntimeOverrides.clear();
        if (!ForgeVoxyRuntimeOverrides.enabledWorldEngineSkeleton()) {
            clearRuntimePipeline();
            ForgeVoxyInstance.INSTANCE.closeActiveWorld();
        }
        source.sendSuccess(() -> Component.literal("Voxy preset clear: runtime overrides cleared; effective values now come from the toml config."), false);
        return 1;
    }

    private static int presetStatus(CommandSourceStack source) {
        var status = ForgeVoxyRuntimeOverrides.createStatusSnapshot();
        String message = String.format(
                "Voxy preset status: active=%s overrides=%s engine=%s(%s) autoIngest=%s(%s) autoCpuMesh=%s(%s) simpleGpu=%s(%s) debugRenderer=%s(%s) minDistance=%d(%s) maxDistance=%d(%s) renderLoadedChunks=%s(%s) skipMode=%s(%s) loadedMargin=%d(%s) keepCached=%s(%s) colors=%s(%s) simpleIgnoreDepth=%s(%s) simpleVerticalOffset=%.3f(%s) simpleAlpha=%.2f(%s) debugAlpha=%.2f(%s)",
                status.presetName(),
                status.hasOverrides(),
                status.enableWorldEngineSkeleton(),
                status.enableWorldEngineSkeletonSource(),
                status.enableAutoChunkIngest(),
                status.enableAutoChunkIngestSource(),
                status.enableAutoCpuMeshBuild(),
                status.enableAutoCpuMeshBuildSource(),
                status.enableSimpleGpuMeshRenderer(),
                status.enableSimpleGpuMeshRendererSource(),
                status.enableDebugMeshRenderer(),
                status.enableDebugMeshRendererSource(),
                status.simpleGpuMeshMinRenderDistanceChunks(),
                status.simpleGpuMeshMinRenderDistanceChunksSource(),
                status.simpleGpuMeshRenderDistanceChunks(),
                status.simpleGpuMeshRenderDistanceChunksSource(),
                status.simpleGpuMeshRenderLoadedChunks(),
                status.simpleGpuMeshRenderLoadedChunksSource(),
                status.simpleGpuMeshLoadedChunkSkipMode(),
                status.simpleGpuMeshLoadedChunkSkipModeSource(),
                status.simpleGpuMeshLoadedChunkMargin(),
                status.simpleGpuMeshLoadedChunkMarginSource(),
                status.simpleGpuMeshKeepCachedChunks(),
                status.simpleGpuMeshKeepCachedChunksSource(),
                status.simpleGpuMeshUseOriginalColors() ? "original" : "layer-debug",
                status.simpleGpuMeshUseOriginalColorsSource(),
                status.simpleGpuMeshIgnoreDepth(),
                status.simpleGpuMeshIgnoreDepthSource(),
                status.simpleGpuMeshVerticalOffset(),
                status.simpleGpuMeshVerticalOffsetSource(),
                status.simpleGpuMeshAlpha(),
                status.simpleGpuMeshAlphaSource(),
                status.debugMeshAlpha(),
                status.debugMeshAlphaSource()
        );
        source.sendSuccess(() -> Component.literal(message), false);
        return status.hasOverrides() ? 1 : 0;
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

    private static LodVisibilityContext createLodVisibilityContext(Minecraft minecraft) {
        if (minecraft.level == null || minecraft.player == null) {
            return null;
        }

        String dimension = minecraft.level.dimension().location().toString();
        int playerChunkX = minecraft.player.chunkPosition().x;
        int playerChunkZ = minecraft.player.chunkPosition().z;
        int maxDistance = ForgeGpuMeshUploadManager.getConfiguredRenderDistanceChunks();
        int minDistance = Math.min(ForgeSimpleGpuMeshRenderer.getConfiguredMinRenderDistanceChunks(), maxDistance);
        boolean renderLoadedChunks = ForgeSimpleGpuMeshRenderer.shouldRenderLoadedChunks();
        SimpleGpuMeshLoadedChunkSkipMode loadedChunkSkipMode = ForgeSimpleGpuMeshRenderer.getConfiguredLoadedChunkSkipMode();
        int loadedChunkMargin = ForgeSimpleGpuMeshRenderer.getConfiguredLoadedChunkMargin();
        int clientRenderDistance = getClientRenderDistanceChunks(minecraft);
        boolean keepCachedChunks = ForgeGpuMeshUploadManager.keepCachedChunks();
        ForgeGpuMeshCache.VisibilitySnapshot visibility = ForgeVoxyInstance.INSTANCE.getGpuMeshCache().createVisibilitySnapshot(
                dimension,
                playerChunkX,
                playerChunkZ,
                minDistance,
                maxDistance,
                renderLoadedChunks,
                loadedChunkSkipMode,
                loadedChunkMargin,
                clientRenderDistance,
                minecraft.level::hasChunk
        );
        return new LodVisibilityContext(
                dimension,
                playerChunkX,
                playerChunkZ,
                clientRenderDistance,
                minDistance,
                maxDistance,
                renderLoadedChunks,
                loadedChunkSkipMode,
                loadedChunkMargin,
                keepCachedChunks,
                visibility
        );
    }

    private static int getClientRenderDistanceChunks(Minecraft minecraft) {
        try {
            return minecraft.options.renderDistance().get();
        } catch (RuntimeException e) {
            return -1;
        }
    }

    private static String formatLodVisibilityAdvice(
            ForgeGpuMeshCache.StatusSnapshot cacheStatus,
            ForgeSimpleGpuMeshRenderer.FrameStats renderStats,
            LodVisibilityContext context
    ) {
        if (context == null) {
            return "open a client world first";
        }
        if (!ForgeVoxyRuntimeOverrides.enabledWorldEngineSkeleton()
                || !ForgeVoxyRuntimeOverrides.enableAutoChunkIngest()
                || !ForgeVoxyRuntimeOverrides.enableAutoCpuMeshBuild()
                || !ForgeVoxyRuntimeOverrides.enableSimpleGpuMeshRenderer()) {
            return "enable engine, auto ingest, auto CPU mesh build, and simple GPU renderer; defaults stay off";
        }
        if (cacheStatus.buffers() == 0 || context.visibility().cachedBuffers() == 0) {
            return "no GPU buffers yet; fly to let chunks cache, or run ingest/build commands, and check auto ingest/build are enabled";
        }
        if (context.visibility().renderableChunks() == 0) {
            if (context.visibility().skippedRenderDistanceChunks() > 0 && !context.renderLoadedChunks()) {
                return "cached chunks are still inside the Minecraft render-distance neighborhood; lower Minecraft render distance to 4-6, fly farther, or use /voxy preset overlay for overlay debugging";
            }
            if (context.visibility().skippedLoadedStateChunks() > 0 && !context.renderLoadedChunks()) {
                return "cached chunks are still reported loaded by ClientLevel.hasChunk; use BY_RENDER_DISTANCE or DISABLED skip mode for LoD visibility testing";
            }
            if (context.visibility().skippedFar() > 0 && context.visibility().candidateBuffers() == 0) {
                return "cached chunks are beyond simpleGpuMeshRenderDistanceChunks; raise it, for example to 64";
            }
            if (context.visibility().skippedNear() > 0 && context.visibility().candidateBuffers() == 0) {
                return "cached chunks are inside simpleGpuMeshMinRenderDistanceChunks; lower min distance or fly farther then look back";
            }
            if (context.visibility().chunksWithinDistanceWindow() == 0) {
                return "no cached chunks currently fall inside the LoD min/max distance window";
            }
            return "chunks are in range but not renderable yet; wait for GPU upload or check skipped/released/translucent counters";
        }
        if (!renderStats.rendered() && renderStats.candidateBuffers() == 0) {
            return "renderable cached chunks exist, but the last render frame saw no candidates; wait one frame or rerun status after moving the camera";
        }
        return "LoD window has renderable cached chunks; if you still cannot see them, look away from loaded terrain or use renderLoadedChunks=true for overlay debugging";
    }

    private static String formatGpuNoRenderReason(ForgeSimpleGpuMeshRenderer.FrameStats renderStats, LodVisibilityContext context) {
        if (renderStats.renderedBuffers() > 0) {
            return "rendering";
        }
        if (context == null) {
            return "world-missing";
        }
        if (renderStats.candidateBuffers() <= 0 && context.visibility().candidateBuffers() <= 0) {
            if (context.visibility().skippedFar() > 0) {
                return "far filter: cached mesh is outside simpleGpuMeshRenderDistanceChunks";
            }
            if (context.visibility().skippedNear() > 0) {
                return "near filter: cached mesh is inside simpleGpuMeshMinRenderDistanceChunks";
            }
            if (context.visibility().skippedByDimension() > 0) {
                return "dimension filter: cached mesh belongs to another dimension";
            }
            return "no candidate cached mesh in the current LoD distance window";
        }
        if (!context.renderLoadedChunks()
                && (renderStats.skippedRenderDistance() > 0 || context.visibility().skippedRenderDistance() > 0)) {
            return "render-distance loaded filter: cached mesh is still inside Minecraft render distance plus margin";
        }
        if (!context.renderLoadedChunks()
                && (renderStats.skippedLoadedState() > 0 || context.visibility().skippedLoadedState() > 0)) {
            return "loaded-state filter: ClientLevel.hasChunk still reports cached mesh chunks as loaded";
        }
        if (renderStats.skippedNear() > 0 || context.visibility().skippedNear() > 0) {
            return "near filter: lower simpleGpuMeshMinRenderDistanceChunks for overlay debug";
        }
        if (renderStats.skippedByDistance() > 0 || context.visibility().skippedFar() > 0) {
            return "far filter: raise simpleGpuMeshRenderDistanceChunks";
        }
        if (renderStats.skippedByDimension() > 0 || context.visibility().skippedByDimension() > 0) {
            return "dimension filter: current cache does not match the active dimension";
        }
        if (renderStats.skippedReleased() > 0 || context.visibility().skippedReleased() > 0) {
            return "released buffer filter: GPU buffers were closed or missing";
        }
        if (renderStats.skippedTranslucent() > 0 || context.visibility().skippedTranslucent() > 0) {
            return "translucent filter: simple GPU renderer skips translucent mesh";
        }
        return renderStats.reason();
    }

    private static String formatDistance(int distance) {
        return distance < 0 ? "none" : Integer.toString(distance);
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

    private static void closeTemporaryCpuMesh(ForgeCpuMeshBuildResult cpuResult) {
        if (cpuResult == null) {
            return;
        }
        for (ForgeCpuBuiltSection section : cpuResult.sections()) {
            if (section != null) {
                section.close();
            }
        }
    }

    private record LodVisibilityContext(
            String dimension,
            int playerChunkX,
            int playerChunkZ,
            int clientRenderDistance,
            int minDistance,
            int maxDistance,
            boolean renderLoadedChunks,
            SimpleGpuMeshLoadedChunkSkipMode loadedChunkSkipMode,
            int loadedChunkMargin,
            boolean keepCachedChunks,
            ForgeGpuMeshCache.VisibilitySnapshot visibility
    ) {
    }

    private static void clearRuntimePipeline() {
        ForgeVoxyInstance.INSTANCE.getChunkIngestManager().clear();
        ForgeVoxyInstance.INSTANCE.getCpuMeshBuildManager().clear();
        ForgeVoxyInstance.INSTANCE.getBuiltSectionBuildManager().clear();
        ForgeVoxyInstance.INSTANCE.getCpuMeshCache().clear();
        ForgeVoxyInstance.INSTANCE.getVoxyGeometryCache().clear();
        ForgeVoxyInstance.INSTANCE.getGpuMeshUploadManager().clear();
        ForgeVoxyInstance.INSTANCE.getGpuMeshCache().clear();
    }

    private static int clearMeshCache(CommandSourceStack source) {
        ForgeVoxyInstance.INSTANCE.getCpuMeshCache().clear();
        ForgeVoxyInstance.INSTANCE.getVoxyGeometryCache().clear();
        ForgeVoxyInstance.INSTANCE.getCpuMeshBuildManager().clear();
        ForgeVoxyInstance.INSTANCE.getBuiltSectionBuildManager().clear();
        ForgeVoxyInstance.INSTANCE.getGpuMeshUploadManager().clear();
        source.sendSuccess(() -> Component.literal("Voxy: cleared CPU mesh cache, CPU-only BuiltSection cache, GPU mesh cache, auto mesh build record, and auto BuiltSection build record."), false);
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
        ForgeVoxyInstance.INSTANCE.getBuiltSectionBuildManager().clear();
        ForgeVoxyInstance.INSTANCE.getCpuMeshCache().clear();
        ForgeVoxyInstance.INSTANCE.getVoxyGeometryCache().clear();
        ForgeVoxyInstance.INSTANCE.getGpuMeshUploadManager().clear();
        source.sendSuccess(() -> Component.literal("Voxy: cleared debug pipeline ingest records, mesh build records, BuiltSection build records, CPU mesh cache, CPU-only BuiltSection cache, and GPU mesh cache."), false);
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
        ForgeVoxyInstance.INSTANCE.getBuiltSectionBuildManager().clear();
        source.sendSuccess(() -> Component.literal("Voxy: cleared auto ingest queue/cache, auto CPU mesh build queue/records, and auto BuiltSection build queue/records."), false);
        return 1;
    }
}
