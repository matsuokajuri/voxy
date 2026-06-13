package me.cortex.voxy.forge;

import com.mojang.brigadier.CommandDispatcher;
import me.cortex.voxy.common.world.service.VoxelIngestService;
import me.cortex.voxy.config.SimpleGpuMeshLoadedChunkSkipMode;
import me.cortex.voxy.config.SimpleGpuMeshSource;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.chunk.LevelChunk;

public final class ForgeVoxyCommands {
    private static final SimpleGpuMeshSource RECOMMENDED_MESH_SOURCE = SimpleGpuMeshSource.BUILT_SECTION;
    private static final SimpleGpuMeshSource FALLBACK_MESH_SOURCE = SimpleGpuMeshSource.CPU_MESH;
    private static final String CPU_MESH_SOURCE_DESCRIPTION = "legacy PoC source / fallback";
    private static final String BUILT_SECTION_SOURCE_DESCRIPTION = "recommended renderer migration source";
    private static final String GL_HEAP_READBACK_SOURCE_DESCRIPTION = "debug source decoded from upload-only GL heap readback";

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
                .then(Commands.literal("geometry_manager_consume_current_chunk")
                        .executes(ctx -> consumeCurrentChunkGeometryManager(ctx.getSource())))
                .then(Commands.literal("geometry_manager_status")
                        .executes(ctx -> geometryManagerStatus(ctx.getSource())))
                .then(Commands.literal("geometry_manager_dump_sample")
                        .executes(ctx -> geometryManagerDumpSample(ctx.getSource())))
                .then(Commands.literal("geometry_manager_clear")
                        .executes(ctx -> clearGeometryManager(ctx.getSource())))
                .then(Commands.literal("geometry_manager_consume_clear")
                        .executes(ctx -> clearGeometryManagerConsumeState(ctx.getSource())))
                .then(Commands.literal("geometry_gpu_upload_status")
                        .executes(ctx -> geometryGpuUploadStatus(ctx.getSource())))
                .then(Commands.literal("geometry_gpu_validate_sample")
                        .executes(ctx -> geometryGpuValidateSample(ctx.getSource())))
                .then(Commands.literal("geometry_gpu_audit_sample")
                        .executes(ctx -> geometryGpuAuditSample(ctx.getSource())))
                .then(Commands.literal("geometry_gpu_audit_status")
                        .executes(ctx -> geometryGpuAuditStatus(ctx.getSource())))
                .then(Commands.literal("geometry_gpu_audit_clear")
                        .executes(ctx -> geometryGpuAuditClear(ctx.getSource())))
                .then(Commands.literal("geometry_gpu_stress_status")
                        .executes(ctx -> geometryGpuStressStatus(ctx.getSource())))
                .then(Commands.literal("geometry_gpu_stress_clear")
                        .executes(ctx -> geometryGpuStressClear(ctx.getSource())))
                .then(Commands.literal("geometry_gpu_stress_once")
                        .executes(ctx -> geometryGpuStressOnce(ctx.getSource())))
                .then(Commands.literal("geometry_gpu_visualize_sample")
                        .executes(ctx -> geometryGpuVisualizeSample(ctx.getSource())))
                .then(Commands.literal("geometry_gpu_visualize_status")
                        .executes(ctx -> geometryGpuVisualizeStatus(ctx.getSource())))
                .then(Commands.literal("geometry_gpu_visualize_clear")
                        .executes(ctx -> geometryGpuVisualizeClear(ctx.getSource())))
                .then(Commands.literal("geometry_gpu_visualize_enable")
                        .executes(ctx -> setGeometryGpuVisualization(ctx.getSource(), true)))
                .then(Commands.literal("geometry_gpu_visualize_disable")
                        .executes(ctx -> setGeometryGpuVisualization(ctx.getSource(), false)))
                .then(Commands.literal("geometry_gpu_readback_mesh_build")
                        .executes(ctx -> geometryGpuReadbackMeshBuild(ctx.getSource())))
                .then(Commands.literal("geometry_gpu_readback_mesh_status")
                        .executes(ctx -> geometryGpuReadbackMeshStatus(ctx.getSource())))
                .then(Commands.literal("geometry_gpu_readback_mesh_clear")
                        .executes(ctx -> geometryGpuReadbackMeshClear(ctx.getSource())))
                .then(Commands.literal("geometry_gpu_readback_mesh_refresh_enable")
                        .executes(ctx -> setGeometryGpuReadbackMeshAutoRefresh(ctx.getSource(), true)))
                .then(Commands.literal("geometry_gpu_readback_mesh_refresh_disable")
                        .executes(ctx -> setGeometryGpuReadbackMeshAutoRefresh(ctx.getSource(), false)))
                .then(Commands.literal("geometry_gpu_readback_mesh_refresh_once")
                        .executes(ctx -> geometryGpuReadbackMeshRefreshOnce(ctx.getSource())))
                .then(Commands.literal("geometry_gpu_upload_enable")
                        .executes(ctx -> setGeometryGpuUpload(ctx.getSource(), true)))
                .then(Commands.literal("geometry_gpu_upload_disable")
                        .executes(ctx -> setGeometryGpuUpload(ctx.getSource(), false)))
                .then(Commands.literal("geometry_gpu_upload_clear")
                        .executes(ctx -> clearGeometryGpuUpload(ctx.getSource())))
                .then(Commands.literal("direct_gl_renderer_status")
                        .executes(ctx -> directGlRendererStatus(ctx.getSource())))
                .then(Commands.literal("direct_gl_renderer_enable")
                        .executes(ctx -> setDirectGlRenderer(ctx.getSource(), true)))
                .then(Commands.literal("direct_gl_renderer_disable")
                        .executes(ctx -> setDirectGlRenderer(ctx.getSource(), false)))
                .then(Commands.literal("direct_gl_renderer_clear")
                        .executes(ctx -> clearDirectGlRenderer(ctx.getSource())))
                .then(Commands.literal("direct_gl_renderer_plan_sample")
                        .executes(ctx -> directGlRendererPlanSample(ctx.getSource())))
                .then(Commands.literal("direct_gl_renderer_build_draw_list")
                        .executes(ctx -> directGlRendererBuildDrawList(ctx.getSource())))
                .then(Commands.literal("direct_gl_renderer_draw_enable")
                        .executes(ctx -> setDirectGlRendererDraw(ctx.getSource(), true)))
                .then(Commands.literal("direct_gl_renderer_draw_disable")
                        .executes(ctx -> setDirectGlRendererDraw(ctx.getSource(), false)))
                .then(Commands.literal("direct_gl_renderer_shader_status")
                        .executes(ctx -> directGlRendererShaderStatus(ctx.getSource())))
                .then(Commands.literal("direct_gl_renderer_draw_list_status")
                        .executes(ctx -> directGlRendererStatus(ctx.getSource())))
                .then(Commands.literal("direct_gl_renderer_auto_plan_enable")
                        .executes(ctx -> setDirectGlRendererAutoPlan(ctx.getSource(), true)))
                .then(Commands.literal("direct_gl_renderer_auto_plan_disable")
                        .executes(ctx -> setDirectGlRendererAutoPlan(ctx.getSource(), false)))
                .then(Commands.literal("direct_gl_renderer_auto_plan_once")
                        .executes(ctx -> directGlRendererAutoPlanOnce(ctx.getSource())))
                .then(Commands.literal("direct_gl_renderer_auto_plan_status")
                        .executes(ctx -> directGlRendererStatus(ctx.getSource())))
                .then(Commands.literal("mesh_cache_status")
                        .executes(ctx -> meshCacheStatus(ctx.getSource())))
                .then(Commands.literal("mesh_cache_clear")
                        .executes(ctx -> clearMeshCache(ctx.getSource())))
                .then(Commands.literal("gpu_mesh_status")
                        .executes(ctx -> gpuMeshStatus(ctx.getSource())))
                .then(Commands.literal("gpu_mesh_source")
                        .then(Commands.literal("cpu")
                                .executes(ctx -> setGpuMeshSource(ctx.getSource(), SimpleGpuMeshSource.CPU_MESH)))
                        .then(Commands.literal("built_section")
                                .executes(ctx -> setGpuMeshSource(ctx.getSource(), SimpleGpuMeshSource.BUILT_SECTION)))
                        .then(Commands.literal("gl_heap_readback")
                                .executes(ctx -> setGpuMeshSource(ctx.getSource(), SimpleGpuMeshSource.GL_HEAP_READBACK))))
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
                        .then(Commands.literal("lod_built_section")
                                .executes(ctx -> applyPresetLodBuiltSection(ctx.getSource())))
                        .then(Commands.literal("geometry_manager")
                                .executes(ctx -> applyPresetGeometryManager(ctx.getSource())))
                        .then(Commands.literal("gl_heap_visualize")
                                .executes(ctx -> applyPresetGlHeapVisualize(ctx.getSource())))
                        .then(Commands.literal("gl_heap_readback")
                                .executes(ctx -> applyPresetGlHeapReadback(ctx.getSource())))
                        .then(Commands.literal("direct_gl_debug")
                                .executes(ctx -> applyPresetDirectGlDebug(ctx.getSource())))
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
        var gpuStatus = ForgeVoxyInstance.INSTANCE.getGpuMeshCache().createStatusSnapshot();
        var uploadStatus = ForgeVoxyInstance.INSTANCE.getGpuMeshUploadManager().getLastStatus();
        boolean gpuUsesBuiltSectionCache = ForgeGpuMeshUploadManager.getConfiguredSource() == SimpleGpuMeshSource.BUILT_SECTION;
        String buildDimension = buildStatus.dimension() == null ? "none" : buildStatus.dimension();
        String message = String.format(
                "Voxy BuiltSection cache: recommendedSource=%s fallbackSource=%s gpuUsesThisCache=%s currentSource=%s currentSourceRole=%s auto=%s engine=%s buildDim=%s queue=%d builtRecords=%d failedRecords=%d radius=%d maxPerTick=%d cooldown=%d lastBuildMs=%.2f avgBuildMs=%.2f estimatedGpuUploadedFromBuiltSections=%d lastConsumerSource=%s lastConsumerPending=%d lastConsumerUploaded=%d lastConsumerFailed=%d lastDecodeMs=%.2f avgDecodeMs=%.2f entries=%d/%d totalSections=%d totalNaiveQuads=%d totalMergedQuads=%d totalAverageQuadArea=%.2f totalSkippedTranslucent=%d totalSkippedNonMergeable=%d totalQuads=%d totalGeometryBytes=%d totalOccupancyBytes=%d finalFormatCount=%d partialFormatCount=%d partialOriginalBitLayoutCount=%d uniqueModelIds=%d missingModelRecords=%d runtimeModelMapperSize=%d uniqueBiomeIds=%d missingBiomeRecords=%d geometryFormat=%s closed=%d evicted=%d replaced=%d firstPosition=%s firstAabb=%s firstOffsets=%s firstNamedOffsets=%s offsetsSemantic=%s sampleRecord=%s decoded=\"%s\"",
                RECOMMENDED_MESH_SOURCE,
                FALLBACK_MESH_SOURCE,
                gpuUsesBuiltSectionCache,
                ForgeGpuMeshUploadManager.getConfiguredSource(),
                describeMeshSource(ForgeGpuMeshUploadManager.getConfiguredSource()),
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
                gpuUsesBuiltSectionCache ? gpuStatus.buffers() : 0,
                uploadStatus.source(),
                uploadStatus.pendingUploads(),
                uploadStatus.uploadedThisFrame(),
                uploadStatus.failedThisFrame(),
                uploadStatus.lastBuiltSectionDecodeMs(),
                uploadStatus.averageBuiltSectionDecodeMs(),
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
        ForgeVoxyInstance.INSTANCE.getSectionGeometryConsumeManager().clear();
        ForgeVoxyInstance.INSTANCE.getGpuGeometryUploadManager().clear();
        boolean clearedGpuBuffers = ForgeGpuMeshUploadManager.getConfiguredSource() == SimpleGpuMeshSource.BUILT_SECTION;
        if (clearedGpuBuffers) {
            ForgeVoxyInstance.INSTANCE.getGpuMeshUploadManager().clear();
        }
        String message = "Voxy: cleared CPU-only BuiltSection cache, closed all partial geometry buffers, and cleared auto BuiltSection build records."
                + " CPU-only section geometry manager state and upload-only GL geometry heap were also cleared because they are derived from BuiltSection cache."
                + (clearedGpuBuffers ? " Current source is BUILT_SECTION, so simple GPU buffers were also cleared to avoid orphan renders." : " Simple GPU buffers were left intact because the active source is not BUILT_SECTION.");
        source.sendSuccess(() -> Component.literal(message), false);
        return 1;
    }

    private static int clearBuiltSectionBuildState(CommandSourceStack source) {
        ForgeVoxyInstance.INSTANCE.getBuiltSectionBuildManager().clear();
        source.sendSuccess(() -> Component.literal("Voxy: cleared auto BuiltSection build queue and built-record. The BuiltSection cache was left intact."), false);
        return 1;
    }

    private static int consumeCurrentChunkGeometryManager(CommandSourceStack source) {
        var minecraft = Minecraft.getInstance();
        var player = minecraft.player;
        var level = minecraft.level;
        if (player == null || level == null) {
            source.sendFailure(Component.literal("Voxy: no client world is active."));
            return 0;
        }

        if (ForgeVoxyInstance.INSTANCE.getCurrentEngineOptional().isEmpty()) {
            String reason = ForgeVoxyRuntimeOverrides.enabledWorldEngineSkeleton()
                    ? "no WorldEngine is active for the current world"
                    : "enableWorldEngineSkeleton is false";
            source.sendFailure(Component.literal("Voxy: cannot consume BuiltSection geometry; " + reason + "."));
            return 0;
        }

        try {
            String dimension = level.dimension().location().toString();
            int chunkX = player.chunkPosition().x;
            int chunkZ = player.chunkPosition().z;
            var builtSections = ForgeVoxyInstance.INSTANCE.getVoxyGeometryCache().createChunkSnapshot(dimension, chunkX, chunkZ);
            if (builtSections.isEmpty()) {
                source.sendFailure(Component.literal("Voxy: no BuiltSection cache entries for current chunk; run /voxy build_current_chunk_built_section first or use /voxy preset lod_built_section."));
                return 0;
            }

            var manager = ForgeVoxyInstance.INSTANCE.getSectionGeometryManager();
            var result = manager.consumeChunk(dimension, chunkX, chunkZ, builtSections);
            var status = result.status();
            String message = String.format(
                    "Voxy section geometry manager consume: %s chunk %d,%d cacheSections=%d consumed=%d replaced=%d skippedClosed=%d skippedEmpty=%d skippedWrongChunk=%d allocatedIds=%s geometryItems=%d geometryBytes=%d activeSections=%d/%d usedItems=%d usedBytes=%d uploadIntents=%d uploadIntentItems=%d uploadIntentBytes=%d removeIntents=%d removeIntentItems=%d removeIntentBytes=%d dirtyMetadataIds=%d metadataValid=%d metadataInvalid=%d lastMetadataError=%s sampleId=%d metadataWords=%s decoded=\"%s\"",
                    result.dimension(),
                    result.chunkX(),
                    result.chunkZ(),
                    result.cacheSections(),
                    result.consumedSections(),
                    result.replacedSections(),
                    result.skippedClosed(),
                    result.skippedEmpty(),
                    result.skippedWrongChunk(),
                    result.formatAllocatedIds(),
                    result.geometryItems(),
                    result.geometryBytes(),
                    status.activeSections(),
                    status.maxSections(),
                    status.usedGeometryItems(),
                    status.usedGeometryBytes(),
                    status.uploadIntents(),
                    status.uploadIntentItems(),
                    status.uploadIntentBytes(),
                    status.removeIntents(),
                    status.removeIntentItems(),
                    status.removeIntentBytes(),
                    status.dirtyMetadataIds(),
                    status.metadataValid(),
                    status.metadataInvalid(),
                    status.lastMetadataError(),
                    status.sampleSectionId(),
                    status.sampleMetadataWords(),
                    status.sampleMetadataDecoded()
            );
            source.sendSuccess(() -> Component.literal(message + " (CPU-only metadata/intents; no GL upload)"), false);
            return result.consumedSections();
        } catch (Exception e) {
            VoxyForge.LOGGER.error("Failed to consume current chunk BuiltSection geometry", e);
            source.sendFailure(Component.literal("Voxy: section geometry manager consume failed: " + e.getMessage()));
            return 0;
        }
    }

    private static int geometryManagerStatus(CommandSourceStack source) {
        var status = ForgeVoxyInstance.INSTANCE.getSectionGeometryManager().createStatusSnapshot();
        var consumeStatus = ForgeVoxyInstance.INSTANCE.getSectionGeometryConsumeManager().createStatusSnapshot();
        String message = String.format(
                "Voxy section geometry manager: autoConsume=%s engine=%s consumeDim=%s queue=%d consumedRecords=%d failedRecords=%d radius=%d maxPerTick=%d cooldown=%d lastConsumeMs=%.2f avgConsumeMs=%.2f lastFailure=%s activeDimension=%s activeSections=%d/%d sectionIdHighWaterMark=%d metadataSlots=%d metadataValid=%d metadataInvalid=%d lastMetadataError=%s usedGeometryItems=%d usedGeometryBytes=%d arenaSizeItems=%d arenaLimitItems=%d arenaFreeItems=%d arenaFreeBlocks=%d arenaLargestFreeBlockItems=%d uploadIntents=%d uploadIntentItems=%d uploadIntentBytes=%d removeIntents=%d removeIntentItems=%d removeIntentBytes=%d dirtyMetadataIds=%d totalUploads=%d totalRemoves=%d totalReplacements=%d clears=%d sampleId=%d metadataWords=%s decoded=\"%s\" sampleOffsets=%s sampleDeltas=%s sampleUploadHash=%d sampleRecords=%s orphanTracking=distance-prune-and-lifecycle-clear cpuOnly=true gl=false",
                consumeStatus.autoEnabled(),
                consumeStatus.enginePresent(),
                consumeStatus.dimension() == null ? "none" : consumeStatus.dimension(),
                consumeStatus.queuedSections(),
                consumeStatus.consumedRecords(),
                consumeStatus.failedRecords(),
                consumeStatus.radius(),
                consumeStatus.maxSectionsPerTick(),
                consumeStatus.cooldownTicks(),
                consumeStatus.lastConsumeMs(),
                consumeStatus.averageMs(),
                consumeStatus.lastFailureReason(),
                status.activeDimension(),
                status.activeSections(),
                status.maxSections(),
                status.sectionIdHighWaterMark(),
                status.metadataSlots(),
                status.metadataValid(),
                status.metadataInvalid(),
                status.lastMetadataError(),
                status.usedGeometryItems(),
                status.usedGeometryBytes(),
                status.arenaSizeItems(),
                status.arenaLimitItems(),
                status.arenaFreeItems(),
                status.arenaFreeBlocks(),
                status.arenaLargestFreeBlockItems(),
                status.uploadIntents(),
                status.uploadIntentItems(),
                status.uploadIntentBytes(),
                status.removeIntents(),
                status.removeIntentItems(),
                status.removeIntentBytes(),
                status.dirtyMetadataIds(),
                status.totalUploads(),
                status.totalRemoves(),
                status.totalReplacements(),
                status.clearCount(),
                status.sampleSectionId(),
                status.sampleMetadataWords(),
                status.sampleMetadataDecoded(),
                status.sampleOffsets(),
                status.sampleDeltas(),
                status.sampleUploadIntentHash(),
                status.sampleFirstRecordsDecoded()
        );
        source.sendSuccess(() -> Component.literal(message), false);
        return status.activeSections();
    }

    private static int geometryManagerDumpSample(CommandSourceStack source) {
        String message = ForgeVoxyInstance.INSTANCE.getSectionGeometryManager().createSampleDump();
        source.sendSuccess(() -> Component.literal(message), false);
        return ForgeVoxyInstance.INSTANCE.getSectionGeometryManager().createStatusSnapshot().activeSections();
    }

    private static int clearGeometryManager(CommandSourceStack source) {
        ForgeVoxyInstance.INSTANCE.getSectionGeometryConsumeManager().clear();
        ForgeVoxyInstance.INSTANCE.getGpuGeometryUploadManager().clear();
        source.sendSuccess(() -> Component.literal("Voxy: cleared CPU-only section geometry manager ids, heap allocation intents, metadata, upload intents, remove intents, dirty metadata ids, auto consume queue/records, and upload-only GL geometry heap. BuiltSection cache was left intact."), false);
        return 1;
    }

    private static int clearGeometryManagerConsumeState(CommandSourceStack source) {
        ForgeVoxyInstance.INSTANCE.getSectionGeometryConsumeManager().clearRecords();
        source.sendSuccess(() -> Component.literal("Voxy: cleared CPU-only section geometry auto consume queue and records. Section geometry manager data was left intact."), false);
        return 1;
    }

    private static int geometryGpuUploadStatus(CommandSourceStack source) {
        var status = ForgeVoxyInstance.INSTANCE.getGpuGeometryUploadManager().createStatusSnapshot();
        var geometryStatus = ForgeVoxyInstance.INSTANCE.getSectionGeometryManager().createStatusSnapshot();
        String message = String.format(
                "Voxy upload-only GL geometry heap: enabled=%s reason=%s heapCreated=%s geometryCapacityBytes=%d metadataCapacityBytes=%d uploadedGeometryBytes=%d uploadedSections=%d metadataWrites=%d removeIntentsProcessed=%d pendingUploadIntents=%d pendingRemoveIntents=%d pendingDirtyMetadata=%d cpuUploadIntents=%d cpuRemoveIntents=%d cpuDirtyMetadata=%d failures=%d lastError=%s lastUploadMs=%.2f avgUploadMs=%.2f validationRuns=%d validationFailures=%d lastValidationError=%s lastValidationSectionId=%d lastValidationGeometryPtr=%s lastMetadataMatch=%s lastGeometryMatch=%s releasedBuffers=%d clears=%d renderThreadOnly=%s draws=false rendererUsesHeap=false",
                status.enabled(),
                status.reason(),
                status.heapCreated(),
                status.geometryCapacityBytes(),
                status.metadataCapacityBytes(),
                status.uploadedGeometryBytes(),
                status.uploadedSections(),
                status.metadataWrites(),
                status.removeIntentsProcessed(),
                status.pendingUploadIntents(),
                status.pendingRemoveIntents(),
                status.pendingDirtyMetadata(),
                geometryStatus.uploadIntents(),
                geometryStatus.removeIntents(),
                geometryStatus.dirtyMetadataIds(),
                status.failures(),
                status.lastError(),
                status.lastUploadMs(),
                status.averageUploadMs(),
                status.validationRuns(),
                status.validationFailures(),
                status.lastValidationError(),
                status.lastValidationSectionId(),
                formatGeometryPointer(status.lastValidationGeometryPtr()),
                status.lastMetadataMatch(),
                status.lastGeometryMatch(),
                status.releasedBuffers(),
                status.clearCount(),
                status.renderThreadOnly()
        );
        source.sendSuccess(() -> Component.literal(message), false);
        return status.enabled() ? 1 : 0;
    }

    private static int geometryGpuValidateSample(CommandSourceStack source) {
        ForgeGpuGeometryValidationResult result = ForgeVoxyInstance.INSTANCE.getGpuGeometryUploadManager().validateSample(8);
        String message = String.format(
                "Voxy upload-only GL geometry validation: success=%s reason=%s sectionId=%d geometryPtr=%s recordCount=%d metadataMatch=%s geometryMatch=%s expectedMetadataHash=%d actualMetadataHash=%d expectedGeometrySampleHash=%d actualGeometrySampleHash=%d expectedMetadata=%s actualMetadata=%s expectedRecords=%s actualRecords=%s readbackApi=glGetNamedBufferSubData renderThreadOnly=true draws=false",
                result.success(),
                result.reason(),
                result.sectionId(),
                formatGeometryPointer(result.geometryPtr()),
                result.recordCount(),
                result.metadataMatch(),
                result.geometryMatch(),
                result.expectedMetadataHash(),
                result.actualMetadataHash(),
                result.expectedGeometrySampleHash(),
                result.actualGeometrySampleHash(),
                result.expectedMetadataWords(),
                result.actualMetadataWords(),
                result.expectedGeometryRecords(),
                result.actualGeometryRecords()
        );
        if (result.success()) {
            source.sendSuccess(() -> Component.literal(message), false);
            return 1;
        }
        source.sendFailure(Component.literal(message));
        return 0;
    }

    private static int geometryGpuAuditSample(CommandSourceStack source) {
        ForgeGpuGeometryAuditResult result = ForgeVoxyInstance.INSTANCE.getGpuGeometryUploadManager().auditSample();
        String message = String.format(
                "Voxy upload-only GL geometry audit: success=%s reason=%s auditedSections=%d decodedRecords=%d invalidMetadata=%d invalidGeometryRecords=%d emptyBuckets=%d nonEmptyBuckets=%d maxQuadLength=%d maxQuadWidth=%d lastAuditedSectionId=%d lastAuditedPosition=%s lastAuditedGeometryPtr=%s lastAuditError=%s metadataWords=%s decodedMetadata=\"%s\" decodedRecords=\"%s\" readbackApi=glGetNamedBufferSubData renderThreadOnly=true drainsIntents=false draws=false",
                result.success(),
                result.reason(),
                result.auditedSections(),
                result.decodedRecords(),
                result.invalidMetadata(),
                result.invalidGeometryRecords(),
                result.emptyBuckets(),
                result.nonEmptyBuckets(),
                result.maxQuadLength(),
                result.maxQuadWidth(),
                result.lastAuditedSectionId(),
                Long.toUnsignedString(result.lastAuditedPosition()),
                formatGeometryPointer(result.lastAuditedGeometryPtr()),
                result.lastAuditError(),
                result.lastMetadataWords(),
                result.lastDecodedMetadata(),
                result.lastDecodedRecords()
        );
        if (result.success()) {
            source.sendSuccess(() -> Component.literal(message), false);
            return result.decodedRecords();
        }
        source.sendFailure(Component.literal(message));
        return 0;
    }

    private static int geometryGpuAuditStatus(CommandSourceStack source) {
        ForgeGpuGeometryAuditStats audit = ForgeVoxyInstance.INSTANCE.getGpuGeometryUploadManager().createAuditStatusSnapshot();
        ForgeGpuGeometryStats upload = ForgeVoxyInstance.INSTANCE.getGpuGeometryUploadManager().createStatusSnapshot();
        String message = String.format(
                "Voxy upload-only GL geometry audit status: auditRuns=%d auditFailures=%d lastAuditError=%s lastAuditDurationMs=%.2f lastAuditedSections=%d lastDecodedRecords=%d lastInvalidMetadata=%d lastInvalidGeometryRecords=%d lastEmptyBuckets=%d lastNonEmptyBuckets=%d lastMaxQuadLength=%d lastMaxQuadWidth=%d lastAuditedSectionId=%d lastAuditedGeometryPtr=%s uploadEnabled=%s heapCreated=%s uploadedSections=%d renderThreadOnly=true drainsIntents=false draws=false",
                audit.auditRuns(),
                audit.auditFailures(),
                audit.lastAuditError(),
                audit.lastAuditDurationMs(),
                audit.lastAuditedSections(),
                audit.lastDecodedRecords(),
                audit.lastInvalidMetadata(),
                audit.lastInvalidGeometryRecords(),
                audit.lastEmptyBuckets(),
                audit.lastNonEmptyBuckets(),
                audit.lastMaxQuadLength(),
                audit.lastMaxQuadWidth(),
                audit.lastAuditedSectionId(),
                formatGeometryPointer(audit.lastAuditedGeometryPtr()),
                upload.enabled(),
                upload.heapCreated(),
                upload.uploadedSections()
        );
        source.sendSuccess(() -> Component.literal(message), false);
        return 1;
    }

    private static int geometryGpuAuditClear(CommandSourceStack source) {
        ForgeVoxyInstance.INSTANCE.getGpuGeometryUploadManager().clearAuditStats();
        source.sendSuccess(() -> Component.literal("Voxy: cleared upload-only GL geometry audit counters and last decoded sample. GL buffers and CPU geometry intents were left intact."), false);
        return 1;
    }

    private static int geometryGpuStressStatus(CommandSourceStack source) {
        ForgeGpuGeometryStressStats stress = ForgeVoxyInstance.INSTANCE.getGpuGeometryUploadManager().createStressStatusSnapshot();
        ForgeGpuGeometryStats upload = ForgeVoxyInstance.INSTANCE.getGpuGeometryUploadManager().createStatusSnapshot();
        String message = String.format(
                "Voxy upload-only GL geometry stress: stressRuns=%d stressFailures=%d lastStressError=%s lastStressStartedAt=%s lastStressFinishedAt=%s lastStressDurationMs=%.2f lastBeforeClearUploadedSections=%d lastAfterReenableUploadedSections=%d lastBeforeClearGeometryBytes=%d lastAfterReenableGeometryBytes=%d lastValidationAfterReenableMetadataMatch=%s lastValidationAfterReenableGeometryMatch=%s uploadEnabled=%s heapCreated=%s uploadedSections=%d uploadedGeometryBytes=%d validationRuns=%d validationFailures=%d renderThreadOnly=true draws=false",
                stress.stressRuns(),
                stress.stressFailures(),
                stress.lastStressError(),
                stress.lastStressStartedAt(),
                stress.lastStressFinishedAt(),
                stress.lastStressDurationMs(),
                stress.lastBeforeClearUploadedSections(),
                stress.lastAfterReenableUploadedSections(),
                stress.lastBeforeClearGeometryBytes(),
                stress.lastAfterReenableGeometryBytes(),
                stress.lastValidationAfterReenableMetadataMatch(),
                stress.lastValidationAfterReenableGeometryMatch(),
                upload.enabled(),
                upload.heapCreated(),
                upload.uploadedSections(),
                upload.uploadedGeometryBytes(),
                upload.validationRuns(),
                upload.validationFailures()
        );
        source.sendSuccess(() -> Component.literal(message), false);
        return 1;
    }

    private static int geometryGpuStressClear(CommandSourceStack source) {
        ForgeVoxyInstance.INSTANCE.getGpuGeometryUploadManager().clearStressStats();
        source.sendSuccess(() -> Component.literal("Voxy: cleared upload-only GL geometry heap stress counters and last-run lifetime stats. GL buffers and CPU geometry intents were left intact."), false);
        return 1;
    }

    private static int geometryGpuStressOnce(CommandSourceStack source) {
        ForgeGpuGeometryStressStats stress = ForgeVoxyInstance.INSTANCE.getGpuGeometryUploadManager().stressOnce();
        String message = String.format(
                "Voxy upload-only GL geometry stress once: success=%s stressRuns=%d stressFailures=%d lastStressError=%s lastStressDurationMs=%.2f beforeSections=%d afterSections=%d beforeGeometryBytes=%d afterGeometryBytes=%d validationAfterReenableMetadataMatch=%s validationAfterReenableGeometryMatch=%s enabledAfterRun=%s heapCreatedAfterRun=%s draws=false rendererUsesHeap=false",
                stress.stressFailures() == 0 || "none".equals(stress.lastStressError()),
                stress.stressRuns(),
                stress.stressFailures(),
                stress.lastStressError(),
                stress.lastStressDurationMs(),
                stress.lastBeforeClearUploadedSections(),
                stress.lastAfterReenableUploadedSections(),
                stress.lastBeforeClearGeometryBytes(),
                stress.lastAfterReenableGeometryBytes(),
                stress.lastValidationAfterReenableMetadataMatch(),
                stress.lastValidationAfterReenableGeometryMatch(),
                ForgeGpuGeometryUploadManager.isEnabled(),
                ForgeVoxyInstance.INSTANCE.getGpuGeometryUploadManager().createStatusSnapshot().heapCreated()
        );
        if ("none".equals(stress.lastStressError())) {
            source.sendSuccess(() -> Component.literal(message), false);
            return 1;
        }
        source.sendFailure(Component.literal(message));
        return 0;
    }

    private static int geometryGpuVisualizeSample(CommandSourceStack source) {
        ForgeGpuGeometryVisualizationResult result = ForgeGpuGeometryVisualizationBuilder.buildSample(ForgeVoxyInstance.INSTANCE);
        String message = String.format(
                "Voxy GL heap readback visualization sample: success=%s reason=%s builtSections=%d recordsRead=%d quads=%d vertices=%d invalidMetadata=%d invalidRecords=%d skippedBuckets=%d durationMs=%.2f lastSectionId=%d lastPosition=%s lastGeometryPtr=%s lastError=%s lastDecodedRecord=\"%s\" lastSource=%s readbackApi=glGetNamedBufferSubData drainsIntents=false formalRenderer=false",
                result.success(),
                result.reason(),
                result.builtSections(),
                result.recordsRead(),
                result.quads(),
                result.vertices(),
                result.invalidMetadata(),
                result.invalidRecords(),
                result.skippedBuckets(),
                result.durationMs(),
                result.lastSectionId(),
                Long.toUnsignedString(result.lastPosition()),
                formatGeometryPointer(result.lastGeometryPtr()),
                result.lastError(),
                result.lastDecodedRecord(),
                result.lastSource()
        );
        if (result.success()) {
            source.sendSuccess(() -> Component.literal(message), false);
            return result.quads();
        }
        source.sendFailure(Component.literal(message));
        return 0;
    }

    private static int geometryGpuVisualizeStatus(CommandSourceStack source) {
        var status = ForgeVoxyInstance.INSTANCE.getGpuGeometryVisualizationCache().createStatusSnapshot();
        var build = status.lastBuildResult();
        var render = ForgeVoxyInstance.INSTANCE.getGpuGeometryReadbackDebugRenderer().getLastFrameStats();
        String message = String.format(
                "Voxy GL heap readback visualization: enabled=%s cacheSections=%d cacheQuads=%d cacheVertices=%d lastBuildSections=%d lastBuildRecordsRead=%d lastBuildQuads=%d lastBuildVertices=%d lastBuildInvalidMetadata=%d lastBuildInvalidRecords=%d lastBuildSkippedBuckets=%d lastBuildDurationMs=%.2f lastBuildError=%s lastRenderedQuads=%d lastRenderedVertices=%d lastRenderDistanceChunks=%d lastSource=%s render=%s renderReason=%s renderSections=%d alpha=%.2f ignoreDepth=%s doubleSided=%s stage=%s maxSections=%d maxRecords=%d clears=%d formalRenderer=false",
                ForgeVoxyRuntimeOverrides.enableGeometryGpuVisualization(),
                status.cacheSections(),
                status.cacheQuads(),
                status.cacheVertices(),
                build.builtSections(),
                build.recordsRead(),
                build.quads(),
                build.vertices(),
                build.invalidMetadata(),
                build.invalidRecords(),
                build.skippedBuckets(),
                build.durationMs(),
                build.lastError(),
                render.renderedQuads(),
                render.renderedVertices(),
                render.renderDistanceChunks(),
                render.source(),
                render.rendered(),
                render.reason(),
                render.renderedSections(),
                ForgeGpuGeometryReadbackDebugRenderer.getConfiguredAlpha(),
                ForgeGpuGeometryReadbackDebugRenderer.shouldIgnoreDepth(),
                ForgeGpuGeometryReadbackDebugRenderer.shouldRenderDoubleSided(),
                ForgeGpuGeometryReadbackDebugRenderer.getRenderStageName(),
                ForgeGpuGeometryVisualizationBuilder.getConfiguredMaxSections(),
                ForgeGpuGeometryVisualizationBuilder.getConfiguredMaxRecords(),
                status.clearCount()
        );
        source.sendSuccess(() -> Component.literal(message), false);
        return status.cacheQuads();
    }

    private static int geometryGpuVisualizeClear(CommandSourceStack source) {
        ForgeVoxyInstance.INSTANCE.getGpuGeometryVisualizationCache().clear();
        ForgeVoxyInstance.INSTANCE.getGpuGeometryReadbackDebugRenderer().clearStats();
        source.sendSuccess(() -> Component.literal("Voxy: cleared GL heap readback visualization cache and render stats. Upload-only GL heap, CPU BuiltSection cache, and CPU section geometry manager were left intact."), false);
        return 1;
    }

    private static int setGeometryGpuVisualization(CommandSourceStack source, boolean enabled) {
        ForgeVoxyRuntimeOverrides.setGeometryGpuVisualization(enabled);
        if (!enabled) {
            ForgeVoxyInstance.INSTANCE.getGpuGeometryVisualizationCache().clear();
            ForgeVoxyInstance.INSTANCE.getGpuGeometryReadbackDebugRenderer().clearStats();
        }
        String message = enabled
                ? "Voxy GL heap readback visualization: runtime-only debug renderer enabled. Use /voxy geometry_gpu_visualize_sample to read back GL heap data into a temporary visualization cache. This was not written to toml."
                : "Voxy GL heap readback visualization: runtime-only debug renderer disabled and visualization cache was cleared. This was not written to toml.";
        source.sendSuccess(() -> Component.literal(message), false);
        return 1;
    }

    private static int geometryGpuReadbackMeshBuild(CommandSourceStack source) {
        ForgeGpuGeometryReadbackMeshResult result = ForgeGpuGeometryReadbackMeshBuilder.buildSample(ForgeVoxyInstance.INSTANCE);
        if (ForgeGpuMeshUploadManager.getConfiguredSource() == SimpleGpuMeshSource.GL_HEAP_READBACK) {
            ForgeVoxyInstance.INSTANCE.getGpuMeshUploadManager().clear();
        }
        String message = String.format(
                "Voxy GL heap readback simple mesh build: success=%s reason=%s builtSections=%d recordsRead=%d quads=%d vertices=%d invalidMetadata=%d invalidRecords=%d durationMs=%.2f lastSectionId=%d lastPosition=%s lastGeometryPtr=%s lastError=%s lastDecodedRecord=\"%s\" lastSource=%s sourceReady=%s maxSections=%d maxRecords=%d readbackApi=glGetNamedBufferSubData drainsIntents=false formalRenderer=false simpleGpuSource=%s",
                result.success(),
                result.reason(),
                result.builtSections(),
                result.recordsRead(),
                result.quads(),
                result.vertices(),
                result.invalidMetadata(),
                result.invalidRecords(),
                result.durationMs(),
                result.lastSectionId(),
                Long.toUnsignedString(result.lastPosition()),
                formatGeometryPointer(result.lastGeometryPtr()),
                result.lastError(),
                result.lastDecodedRecord(),
                result.lastSource(),
                result.quads() > 0,
                ForgeGpuGeometryReadbackMeshBuilder.getConfiguredMaxSections(),
                ForgeGpuGeometryReadbackMeshBuilder.getConfiguredMaxRecords(),
                ForgeGpuMeshUploadManager.getConfiguredSource()
        );
        if (result.success()) {
            source.sendSuccess(() -> Component.literal(message), false);
            return result.quads();
        }
        source.sendFailure(Component.literal(message));
        return 0;
    }

    private static int geometryGpuReadbackMeshStatus(CommandSourceStack source) {
        var status = ForgeVoxyInstance.INSTANCE.getGpuGeometryReadbackMeshCache().createStatusSnapshot();
        var refresh = ForgeVoxyInstance.INSTANCE.getGpuGeometryReadbackMeshRefreshManager().createStatusSnapshot();
        var build = status.lastBuildResult();
        String message = String.format(
                "Voxy GL heap readback simple mesh: enabled=%s sourceReady=%s cacheSections=%d cacheQuads=%d cacheVertices=%d cacheBytes=%d lastBuildSections=%d lastBuildRecordsRead=%d lastBuildQuads=%d lastBuildVertices=%d lastBuildInvalidMetadata=%d lastBuildInvalidRecords=%d lastBuildDurationMs=%.2f lastBuildError=%s lastSource=%s clears=%d simpleGpuSource=%s simpleGpuEnabled=%s autoRefresh=%s refreshCooldownTicks=%d ticksUntilNextRefresh=%d refreshRuns=%d refreshSkipped=%d refreshFailures=%d lastRefreshReason=%s lastRefreshSkippedReason=%s lastRefreshDurationMs=%.2f lastRefreshSections=%d lastRefreshRecords=%d lastRefreshQuads=%d lastRefreshVertices=%d lastRefreshError=%s refreshMaxSections=%d refreshMaxRecords=%d refreshOnlySourceActive=%s refreshOnlyRendererEnabled=%s formalRenderer=false",
                ForgeGpuMeshUploadManager.getConfiguredSource() == SimpleGpuMeshSource.GL_HEAP_READBACK,
                status.cacheQuads() > 0,
                status.cacheSections(),
                status.cacheQuads(),
                status.cacheVertices(),
                status.cacheBytes(),
                build.builtSections(),
                build.recordsRead(),
                build.quads(),
                build.vertices(),
                build.invalidMetadata(),
                build.invalidRecords(),
                build.durationMs(),
                build.lastError(),
                build.lastSource(),
                status.clearCount(),
                ForgeGpuMeshUploadManager.getConfiguredSource(),
                ForgeVoxyRuntimeOverrides.enableSimpleGpuMeshRenderer(),
                refresh.autoRefresh(),
                refresh.refreshCooldownTicks(),
                refresh.ticksUntilNextRefresh(),
                refresh.refreshRuns(),
                refresh.refreshSkipped(),
                refresh.refreshFailures(),
                refresh.lastRefreshReason(),
                refresh.lastRefreshSkippedReason(),
                refresh.lastRefreshDurationMs(),
                refresh.lastRefreshSections(),
                refresh.lastRefreshRecords(),
                refresh.lastRefreshQuads(),
                refresh.lastRefreshVertices(),
                refresh.lastRefreshError(),
                refresh.refreshMaxSections(),
                refresh.refreshMaxRecords(),
                refresh.onlyWhenSourceActive(),
                refresh.onlyWhenRendererEnabled()
        );
        source.sendSuccess(() -> Component.literal(message), false);
        return status.cacheQuads();
    }

    private static int geometryGpuReadbackMeshClear(CommandSourceStack source) {
        ForgeVoxyInstance.INSTANCE.getGpuGeometryReadbackMeshCache().clear();
        ForgeVoxyInstance.INSTANCE.getGpuGeometryReadbackMeshRefreshManager().clear();
        if (ForgeGpuMeshUploadManager.getConfiguredSource() == SimpleGpuMeshSource.GL_HEAP_READBACK) {
            ForgeVoxyInstance.INSTANCE.getGpuMeshUploadManager().clear();
        }
        source.sendSuccess(() -> Component.literal("Voxy: cleared GL_HEAP_READBACK simple mesh cache. Upload-only GL heap, CPU BuiltSection cache, and CPU section geometry manager were left intact."), false);
        return 1;
    }

    private static int setGeometryGpuReadbackMeshAutoRefresh(CommandSourceStack source, boolean enabled) {
        ForgeVoxyRuntimeOverrides.setGeometryGpuReadbackMeshAutoRefresh(enabled);
        if (enabled) {
            ForgeVoxyInstance.INSTANCE.getGpuGeometryReadbackMeshRefreshManager().requestRefresh(ForgeGpuGeometryReadbackMeshRefreshManager.REASON_MANUAL_COMMAND);
        } else {
            ForgeVoxyInstance.INSTANCE.getGpuGeometryReadbackMeshRefreshManager().clear();
        }
        String message = enabled
                ? "Voxy GL_HEAP_READBACK mesh auto refresh: runtime-only enabled. It will refresh only while source/renderer gates allow it and never writes toml."
                : "Voxy GL_HEAP_READBACK mesh auto refresh: runtime-only disabled and refresh state was cleared. Existing readback mesh cache was left intact.";
        source.sendSuccess(() -> Component.literal(message), false);
        return 1;
    }

    private static int geometryGpuReadbackMeshRefreshOnce(CommandSourceStack source) {
        var attempt = ForgeVoxyInstance.INSTANCE.getGpuGeometryReadbackMeshRefreshManager().refreshNow(ForgeGpuGeometryReadbackMeshRefreshManager.REASON_MANUAL_COMMAND);
        var result = attempt.result();
        var status = attempt.status();
        String message = String.format(
                "Voxy GL heap readback simple mesh refresh: success=%s skipped=%s skippedReason=%s reason=%s builtSections=%d recordsRead=%d quads=%d vertices=%d invalidMetadata=%d invalidRecords=%d durationMs=%.2f refreshRuns=%d refreshSkipped=%d refreshFailures=%d ticksUntilNextRefresh=%d lastRefreshError=%s source=%s autoRefresh=%s",
                attempt.success(),
                attempt.skipped(),
                attempt.skippedReason(),
                result.reason(),
                result.builtSections(),
                result.recordsRead(),
                result.quads(),
                result.vertices(),
                result.invalidMetadata(),
                result.invalidRecords(),
                result.durationMs(),
                status.refreshRuns(),
                status.refreshSkipped(),
                status.refreshFailures(),
                status.ticksUntilNextRefresh(),
                status.lastRefreshError(),
                ForgeGpuMeshUploadManager.getConfiguredSource(),
                status.autoRefresh()
        );
        if (attempt.success()) {
            source.sendSuccess(() -> Component.literal(message), false);
            return Math.max(1, result.quads());
        }
        source.sendFailure(Component.literal(message));
        return 0;
    }

    private static int setGeometryGpuUpload(CommandSourceStack source, boolean enabled) {
        ForgeVoxyRuntimeOverrides.setGeometryGpuUpload(enabled);
        if (!enabled) {
            ForgeVoxyInstance.INSTANCE.getGpuGeometryUploadManager().clear();
        }
        String message = enabled
                ? "Voxy upload-only GL geometry heap: runtime-only upload enabled. It will copy CPU section geometry upload/metadata intents into small GL buffers on the render thread; it does not render or draw."
                : "Voxy upload-only GL geometry heap: runtime-only upload disabled, GL buffers were released, and GL heap readback visualization/readback-mesh caches were cleared. This was not written to toml.";
        source.sendSuccess(() -> Component.literal(message), false);
        return 1;
    }

    private static int clearGeometryGpuUpload(CommandSourceStack source) {
        ForgeVoxyInstance.INSTANCE.getGpuGeometryUploadManager().clear();
        ForgeVoxyInstance.INSTANCE.getDirectGpuGeometryRenderer().clear();
        source.sendSuccess(() -> Component.literal("Voxy: released upload-only GL geometry heap buffers, cleared GPU upload processed-state, cleared direct GL renderer skeleton state, and cleared GL heap readback visualization/readback-mesh caches. CPU section geometry manager intents were left intact and can re-upload."), false);
        if (ForgeGpuMeshUploadManager.getConfiguredSource() == SimpleGpuMeshSource.GL_HEAP_READBACK
                && ForgeVoxyRuntimeOverrides.enableGeometryGpuReadbackMeshAutoRefresh()) {
            ForgeVoxyInstance.INSTANCE.getGpuGeometryReadbackMeshRefreshManager().requestRefresh(ForgeGpuGeometryReadbackMeshRefreshManager.REASON_UPLOAD_CLEAR_REBUILD);
        }
        return 1;
    }

    private static int directGlRendererStatus(CommandSourceStack source) {
        var status = ForgeVoxyInstance.INSTANCE.getDirectGpuGeometryRenderer().createStatusSnapshot();
        String message = String.format(
                "Voxy direct GL renderer: enabled=%s initialized=%s hasHeap=%s heapCreated=%s shaderSupported=%s shaderCompiled=%s programCreated=%s drawListValid=%s drawListStale=%s drawItems=%d drawListRecords=%d drawListVertices=%d plannedSections=%d plannedRecords=%d plannedVertices=%d candidateSections=%d acceptedSections=%d rejectedByRadius=%d rejectedByFrustum=%d frustumAvailable=%s cameraChunk=%s cameraSection=%s skippedSections=%d skippedRecords=%d selectionMode=%s uploadedSectionCandidates=%d invalidMetadata=%d lastPlanDurationMs=%.2f lastPlanError=%s lastSkippedReason=%s planRuns=%d planFailures=%d autoPlan=%s autoPlanCooldownTicks=%d ticksUntilNextAutoPlan=%d lastAutoPlanReason=%s lastAutoPlanSkippedReason=%s lastAutoPlanDurationMs=%.2f autoPlanRuns=%d autoPlanSkipped=%d lastAutoPlanFailures=%d autoPlanMaxCandidates=%d autoPlanMaxSections=%d autoPlanMaxRecords=%d autoPlanMoveThresholdBlocks=%d drawListRuns=%d drawListFailures=%d lastDrawListMs=%.2f lastDrawListError=%s lastDrawListSkippedReason=%s heapGeneration=%d currentHeapGeneration=%d clears=%d maxSections=%d maxRecords=%d maxPlanCandidates=%d maxDrawSections=%d maxDrawRecords=%d maxRecordsPerSection=%d renderDistanceChunks=%d alpha=%.2f ignoreDepth=%s doubleSided=%s actualDrawEnabled=%s drawCallsIssued=%d verticesDrawn=%d lastFrameDrawCalls=%d lastFrameDrawItems=%d lastFrameVertices=%d lastDrawMs=%.2f lastDrawError=%s lastGlError=%s stage=%s usesSsbo=%s mdic=false vboOwner=upload-only-heap",
                status.enabled(),
                status.initialized(),
                status.hasHeap(),
                status.heapCreated(),
                status.shaderSupported(),
                status.shaderCompiled(),
                status.programCreated(),
                status.drawListValid(),
                status.drawListStale(),
                status.drawItems(),
                status.drawListRecords(),
                status.drawListVertices(),
                status.plannedSections(),
                status.plannedRecords(),
                status.plannedVertices(),
                status.candidateSections(),
                status.acceptedSections(),
                status.rejectedByRadius(),
                status.rejectedByFrustum(),
                status.frustumAvailable(),
                status.cameraChunk(),
                status.cameraSection(),
                status.skippedSections(),
                status.skippedRecords(),
                status.selectionMode(),
                status.uploadedSectionCandidates(),
                status.invalidMetadata(),
                status.lastPlanDurationMs(),
                status.lastPlanError(),
                status.lastSkippedReason(),
                status.planRuns(),
                status.planFailures(),
                status.autoPlan(),
                status.autoPlanCooldownTicks(),
                status.ticksUntilNextAutoPlan(),
                status.lastAutoPlanReason(),
                status.lastAutoPlanSkippedReason(),
                status.lastAutoPlanDurationMs(),
                status.autoPlanRuns(),
                status.autoPlanSkipped(),
                status.lastAutoPlanFailures(),
                status.autoPlanMaxCandidates(),
                status.autoPlanMaxSections(),
                status.autoPlanMaxRecords(),
                status.autoPlanMoveThresholdBlocks(),
                status.drawListBuildRuns(),
                status.drawListBuildFailures(),
                status.lastDrawListBuildDurationMs(),
                status.lastDrawListError(),
                status.lastDrawListSkippedReason(),
                status.drawListHeapGeneration(),
                status.currentHeapGeneration(),
                status.clearCount(),
                status.maxSections(),
                status.maxRecords(),
                status.maxPlanCandidates(),
                status.maxDrawSections(),
                status.maxDrawRecords(),
                status.maxRecordsPerSection(),
                status.renderDistanceChunks(),
                status.debugAlpha(),
                status.ignoreDepth(),
                status.doubleSided(),
                status.actualDrawEnabled(),
                status.drawCallsIssued(),
                status.verticesDrawn(),
                status.lastFrameDrawCalls(),
                status.lastFrameDrawItems(),
                status.lastFrameVertices(),
                status.lastDrawDurationMs(),
                status.lastDrawError(),
                status.lastGlError(),
                status.stage(),
                status.usesSsbo()
        );
        source.sendSuccess(() -> Component.literal(message), false);
        return status.enabled() ? 1 : 0;
    }

    private static int setDirectGlRenderer(CommandSourceStack source, boolean enabled) {
        ForgeVoxyRuntimeOverrides.setDirectGpuGeometryRenderer(enabled);
        if (enabled) {
            ForgeVoxyInstance.INSTANCE.getDirectGpuGeometryRenderer().markEnabledRuntime();
        } else {
            ForgeVoxyInstance.INSTANCE.getDirectGpuGeometryRenderer().clear();
        }
        String message = enabled
                ? "Voxy direct GL renderer: runtime-only enabled. G5.3 can build a camera/radius-aware direct draw list, but actualDrawEnabled stays false until /voxy direct_gl_renderer_draw_enable."
                : "Voxy direct GL renderer: runtime-only disabled, actual draw disabled, and direct renderer state was cleared. This was not written to toml.";
        source.sendSuccess(() -> Component.literal(message), false);
        return 1;
    }

    private static int setDirectGlRendererAutoPlan(CommandSourceStack source, boolean enabled) {
        ForgeVoxyRuntimeOverrides.setDirectGpuGeometryAutoPlan(enabled);
        if (enabled) {
            ForgeVoxyInstance.INSTANCE.getDirectGpuGeometryRenderer().markEnabledRuntime();
            ForgeVoxyInstance.INSTANCE.getDirectGpuGeometryRenderer().requestAutoPlan(ForgeDirectGpuGeometryRenderer.REASON_MANUAL_COMMAND);
        }
        String message = enabled
                ? "Voxy direct GL renderer: auto plan enabled runtime-only. It is cooldown and camera-move bounded; actual direct draw is still controlled separately."
                : "Voxy direct GL renderer: auto plan disabled runtime-only. Existing draw list was kept for inspection.";
        source.sendSuccess(() -> Component.literal(message), false);
        return 1;
    }

    private static int clearDirectGlRenderer(CommandSourceStack source) {
        ForgeVoxyInstance.INSTANCE.getDirectGpuGeometryRenderer().clear();
        source.sendSuccess(() -> Component.literal("Voxy direct GL renderer: state, draw list, and debug shader were cleared. CPU caches, SectionGeometryManager, and upload-only GL heap were left intact."), false);
        return 1;
    }

    private static int directGlRendererPlanSample(CommandSourceStack source) {
        var result = ForgeVoxyInstance.INSTANCE.getDirectGpuGeometryRenderer().planSample();
        var status = ForgeVoxyInstance.INSTANCE.getDirectGpuGeometryRenderer().createStatusSnapshot();
        String message = String.format(
                "Voxy direct GL renderer plan: success=%s skippedReason=%s error=%s uploadedSectionCandidates=%d plannedSections=%d plannedRecords=%d plannedVertices=%d skippedSections=%d skippedRecords=%d selectionMode=%s invalidMetadata=%d durationMs=%.2f drawListValid=%s drawItems=%d drawListStale=%s drawCallsIssued=%d verticesDrawn=%d actualDrawEnabled=%s stage=%s readback=metadata-only shaderSupported=%s mdic=false",
                result.success(),
                result.skippedReason(),
                result.error(),
                result.uploadedSectionCandidates(),
                result.plannedSections(),
                result.plannedRecords(),
                result.plannedVertices(),
                result.skippedSections(),
                result.skippedRecords(),
                result.selectionMode(),
                result.invalidMetadata(),
                result.durationMs(),
                status.drawListValid(),
                status.drawItems(),
                status.drawListStale(),
                status.drawCallsIssued(),
                status.verticesDrawn(),
                status.actualDrawEnabled(),
                status.stage(),
                status.shaderSupported()
        );
        if (result.success()) {
            source.sendSuccess(() -> Component.literal(message), false);
            return Math.max(1, result.plannedSections());
        }
        source.sendFailure(Component.literal(message));
        return 0;
    }

    private static int directGlRendererAutoPlanOnce(CommandSourceStack source) {
        var result = ForgeVoxyInstance.INSTANCE.getDirectGpuGeometryRenderer().autoPlanNow(ForgeDirectGpuGeometryRenderer.REASON_MANUAL_COMMAND);
        String message = String.format(
                "Voxy direct GL renderer auto plan: success=%s skippedReason=%s error=%s uploadedSectionCandidates=%d candidateSections=%d acceptedSections=%d drawItems=%d drawRecords=%d drawVertices=%d rejectedByRadius=%d rejectedByFrustum=%d selectionMode=%s frustumAvailable=%s cameraChunk=%s cameraSection=%s heapGeneration=%d invalidMetadata=%d durationMs=%.2f autoPlan=%s actualDrawEnabled=%s stage=%s",
                result.success(),
                result.skippedReason(),
                result.error(),
                result.uploadedSectionCandidates(),
                result.candidateSections(),
                result.acceptedSections(),
                result.drawItems(),
                result.drawRecords(),
                result.drawVertices(),
                result.rejectedByRadius(),
                result.rejectedByFrustum(),
                result.selectionMode(),
                result.frustumAvailable(),
                result.cameraChunk(),
                result.cameraSection(),
                result.heapGeneration(),
                result.invalidMetadata(),
                result.durationMs(),
                ForgeDirectGpuGeometryRendererConfig.autoPlanEnabled(),
                ForgeDirectGpuGeometryRendererConfig.actualDrawEnabled(),
                ForgeDirectGpuGeometryRenderState.STAGE
        );
        if (result.success()) {
            source.sendSuccess(() -> Component.literal(message), false);
            return Math.max(1, result.drawItems());
        }
        source.sendFailure(Component.literal(message));
        return 0;
    }

    private static int directGlRendererBuildDrawList(CommandSourceStack source) {
        var result = ForgeVoxyInstance.INSTANCE.getDirectGpuGeometryRenderer().buildDrawList();
        String message = String.format(
                "Voxy direct GL renderer draw list: success=%s skippedReason=%s error=%s uploadedSectionCandidates=%d candidateSections=%d acceptedSections=%d drawItems=%d drawRecords=%d drawVertices=%d skippedSections=%d skippedRecords=%d rejectedByRadius=%d rejectedByFrustum=%d selectionMode=%s frustumAvailable=%s cameraChunk=%s cameraSection=%s heapGeneration=%d invalidMetadata=%d durationMs=%.2f maxPlanCandidates=%d maxDrawSections=%d maxDrawRecords=%d maxRecordsPerSection=%d actualDrawEnabled=%s stage=%s readback=metadata-only draw=false",
                result.success(),
                result.skippedReason(),
                result.error(),
                result.uploadedSectionCandidates(),
                result.candidateSections(),
                result.acceptedSections(),
                result.drawItems(),
                result.drawRecords(),
                result.drawVertices(),
                result.skippedSections(),
                result.skippedRecords(),
                result.rejectedByRadius(),
                result.rejectedByFrustum(),
                result.selectionMode(),
                result.frustumAvailable(),
                result.cameraChunk(),
                result.cameraSection(),
                result.heapGeneration(),
                result.invalidMetadata(),
                result.durationMs(),
                ForgeDirectGpuGeometryRendererConfig.maxPlanCandidates(),
                ForgeDirectGpuGeometryRendererConfig.maxDrawSections(),
                ForgeDirectGpuGeometryRendererConfig.maxDrawRecords(),
                ForgeDirectGpuGeometryRendererConfig.maxRecordsPerSection(),
                ForgeDirectGpuGeometryRendererConfig.actualDrawEnabled(),
                ForgeDirectGpuGeometryRenderState.STAGE
        );
        if (result.success()) {
            source.sendSuccess(() -> Component.literal(message), false);
            return Math.max(1, result.drawItems());
        }
        source.sendFailure(Component.literal(message));
        return 0;
    }

    private static int setDirectGlRendererDraw(CommandSourceStack source, boolean enabled) {
        ForgeVoxyRuntimeOverrides.setDirectGpuGeometryRendererActualDraw(enabled);
        if (!enabled) {
            ForgeVoxyInstance.INSTANCE.getDirectGpuGeometryRenderer().markEnabledRuntime();
            source.sendSuccess(() -> Component.literal("Voxy direct GL renderer: actual draw disabled. Draw list and shader state were kept for inspection; no toml was written."), false);
            return 1;
        }

        ForgeVoxyInstance.INSTANCE.getDirectGpuGeometryRenderer().markEnabledRuntime();
        ForgeDirectGpuGeometryShader.ShaderStatus shader = ForgeVoxyInstance.INSTANCE.getDirectGpuGeometryRenderer().prepareShader();
        if (!shader.ok()) {
            String message = String.format(
                    "Voxy direct GL renderer: actual draw requested, but shader is not ready. shaderSupported=%s shaderCompiled=%s programCreated=%s unsupportedReason=%s lastShaderError=%s glVersion=%s glslVersion=%s. No toml was written.",
                    shader.shaderSupported(),
                    shader.shaderCompiled(),
                    shader.programCreated(),
                    shader.unsupportedReason(),
                    shader.lastShaderError(),
                    shader.glVersion(),
                    shader.glslVersion()
            );
            source.sendFailure(Component.literal(message));
            return 0;
        }

        source.sendSuccess(() -> Component.literal("Voxy direct GL renderer: actual draw enabled runtime-only. Build a draw list with /voxy direct_gl_renderer_build_draw_list or /voxy direct_gl_renderer_auto_plan_once; this still uses the isolated G5.3 camera-aware debug path only."), false);
        return 1;
    }

    private static int directGlRendererShaderStatus(CommandSourceStack source) {
        ForgeDirectGpuGeometryShader.ShaderStatus status = ForgeVoxyInstance.INSTANCE.getDirectGpuGeometryRenderer().createShaderStatusSnapshot();
        String message = String.format(
                "Voxy direct GL shader: shaderSupported=%s shaderCompiled=%s programCreated=%s lastShaderError=%s unsupportedReason=%s glVersion=%s glslVersion=%s usesSsbo=%s stage=%s",
                status.shaderSupported(),
                status.shaderCompiled(),
                status.programCreated(),
                status.lastShaderError(),
                status.unsupportedReason(),
                status.glVersion(),
                status.glslVersion(),
                status.usesSsbo(),
                ForgeDirectGpuGeometryRenderState.STAGE
        );
        source.sendSuccess(() -> Component.literal(message), false);
        return status.ok() ? 1 : 0;
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
                "Voxy debug pipeline: preset=%s overrides=%s engineConfig=%s engine=%s autoIngest=%s autoBuild=%s render=%s simpleGpu=%s gpuSource=%s sourceRole=%s recommendedSource=%s fallbackSource=%s dim=%s playerChunk=%s ingestQueue=%d ingestedRecords=%d avgIngestMs=%.2f buildQueue=%d builtRecords=%d failedRecords=%d avgBuildMs=%.2f lastBuildMs=%.2f cache=%d/%d vertices=%d quads=%d bytes=%d dimensions=%s layers=%s gpuBuffers=%d/%d gpuPending=%d gpuRendered=%d gpuChunks=%d gpuVertices=%d gpuLimited=%d gpuSkippedNear=%d gpuSkippedLoaded=%d gpuSkippedLoadedState=%d gpuSkippedRenderDistance=%d gpuSkippedFar=%d gpuAvgRenderMs=%.2f gpuMinDistance=%d gpuMaxDistance=%d gpuRenderLoaded=%s gpuSkipMode=%s gpuLoadedMargin=%d gpuKeepCached=%s maxRendered=%d ignoreDepth=%s alpha=%.2f verticalOffset=%.3f stage=%s %s %s",
                presetStatus.presetName(),
                presetStatus.hasOverrides(),
                ForgeVoxyRuntimeOverrides.enabledWorldEngineSkeleton(),
                meshBuildStatus.enginePresent(),
                ingestStatus.autoEnabled(),
                meshBuildStatus.autoEnabled(),
                ForgeVoxyRuntimeOverrides.enableDebugMeshRenderer(),
                ForgeVoxyRuntimeOverrides.enableSimpleGpuMeshRenderer(),
                ForgeGpuMeshUploadManager.getConfiguredSource(),
                describeMeshSource(ForgeGpuMeshUploadManager.getConfiguredSource()),
                RECOMMENDED_MESH_SOURCE,
                FALLBACK_MESH_SOURCE,
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
        var builtSectionStatus = ForgeVoxyInstance.INSTANCE.getVoxyGeometryCache().createStatusSnapshot();
        var builtSectionBuildStatus = ForgeVoxyInstance.INSTANCE.getBuiltSectionBuildManager().createStatusSnapshot();
        var readbackMeshStatus = ForgeVoxyInstance.INSTANCE.getGpuGeometryReadbackMeshCache().createStatusSnapshot();
        var readbackRefreshStatus = ForgeVoxyInstance.INSTANCE.getGpuGeometryReadbackMeshRefreshManager().createStatusSnapshot();
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
                "Voxy simple GPU mesh: preset=%s overrides=%s enabled=%s source=%s sourceRole=%s uploadSource=%s recommendedSource=%s fallbackSource=%s engine=%s currentDim=%s playerChunk=%s vanillaRenderDistance=%d minDistance=%d maxDistance=%d renderLoadedChunks=%s skipMode=%s loadedMargin=%d keepCached=%s buffers=%d/%d renderableCachedChunks=%d vertices=%d quads=%d bytes=%d dimensions=%s layers=%s builtSectionCache=%d/%d builtSectionAuto=%s builtSectionBuiltRecords=%d builtSectionFailedRecords=%d builtSectionCandidates=%d glHeapReadbackCache=%d glHeapReadbackQuads=%d glHeapReadbackVertices=%d glHeapReadbackLastSource=%s glHeapReadbackLastError=%s glHeapReadbackAutoRefresh=%s glHeapReadbackRefreshRuns=%d glHeapReadbackRefreshSkipped=%d glHeapReadbackRefreshFailures=%d glHeapReadbackTicksUntilNextRefresh=%d glHeapReadbackLastRefreshReason=%s glHeapReadbackLastSkippedReason=%s glHeapReadbackLastRefreshDurationMs=%.2f glHeapReadbackLastRefreshSections=%d glHeapReadbackLastRefreshRecords=%d glHeapReadbackLastRefreshQuads=%d glHeapReadbackLastRefreshVertices=%d readbackCandidates=%d pendingUploads=%d uploadBudget=%d uploadedLast=%d failedLast=%d skippedTranslucentUpload=%d builtDoubleSidedAsSingle=%d skippedBuiltInvalid=%d cpuCandidates=%d cpuSkippedDistance=%d cpuLimited=%d sourceSwitchCount=%d orphanReconciled=%d orphanReconciledTotal=%d lastBuiltSectionDecodeMs=%.2f avgBuiltSectionDecodeMs=%.2f avgUploadMs=%.2f colorMode=%s ignoreDepth=%s verticalOffset=%.3f render=%s reason=%s noRenderReason=%s renderDim=%s candidateBuffers=%d renderedBuffers=%d renderedChunks=%d renderedVertices=%d skippedNear=%d skippedLoaded=%d skippedLoadedState=%d skippedRenderDistance=%d skippedFar=%d skippedDimension=%d skippedReleased=%d limitedRender=%d skippedTranslucentRender=%d lastRenderMs=%.2f avgRenderMs=%.2f maxRendered=%d alpha=%.2f stage=%s debugRenderer=%s advice=%s",
                ForgeVoxyRuntimeOverrides.presetName(),
                ForgeVoxyRuntimeOverrides.hasOverrides(),
                ForgeVoxyRuntimeOverrides.enableSimpleGpuMeshRenderer(),
                ForgeGpuMeshUploadManager.getConfiguredSource(),
                describeMeshSource(ForgeGpuMeshUploadManager.getConfiguredSource()),
                uploadStatus.source(),
                RECOMMENDED_MESH_SOURCE,
                FALLBACK_MESH_SOURCE,
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
                builtSectionStatus.entries(),
                builtSectionStatus.maxEntries(),
                builtSectionBuildStatus.autoEnabled(),
                builtSectionBuildStatus.builtChunks(),
                builtSectionBuildStatus.failedChunks(),
                uploadStatus.candidateBuiltSectionEntries(),
                readbackMeshStatus.cacheSections(),
                readbackMeshStatus.cacheQuads(),
                readbackMeshStatus.cacheVertices(),
                readbackMeshStatus.lastBuildResult().lastSource(),
                readbackMeshStatus.lastBuildResult().lastError(),
                readbackRefreshStatus.autoRefresh(),
                readbackRefreshStatus.refreshRuns(),
                readbackRefreshStatus.refreshSkipped(),
                readbackRefreshStatus.refreshFailures(),
                readbackRefreshStatus.ticksUntilNextRefresh(),
                readbackRefreshStatus.lastRefreshReason(),
                readbackRefreshStatus.lastRefreshSkippedReason(),
                readbackRefreshStatus.lastRefreshDurationMs(),
                readbackRefreshStatus.lastRefreshSections(),
                readbackRefreshStatus.lastRefreshRecords(),
                readbackRefreshStatus.lastRefreshQuads(),
                readbackRefreshStatus.lastRefreshVertices(),
                uploadStatus.candidateGlHeapReadbackEntries(),
                uploadStatus.pendingUploads(),
                uploadStatus.uploadBudget(),
                uploadStatus.uploadedThisFrame(),
                uploadStatus.failedThisFrame(),
                uploadStatus.skippedTranslucent(),
                uploadStatus.builtSectionDoubleSidedRecords(),
                uploadStatus.skippedBuiltSectionInvalid(),
                uploadStatus.candidateCpuEntries(),
                uploadStatus.skippedCpuByDistance(),
                uploadStatus.limitedCpuEntries(),
                uploadStatus.sourceSwitchCount(),
                uploadStatus.orphanReconciled(),
                uploadStatus.orphanReconciledTotal(),
                uploadStatus.lastBuiltSectionDecodeMs(),
                uploadStatus.averageBuiltSectionDecodeMs(),
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

    private static int setGpuMeshSource(CommandSourceStack source, SimpleGpuMeshSource meshSource) {
        if (meshSource == SimpleGpuMeshSource.GL_HEAP_READBACK) {
            ForgeVoxyRuntimeOverrides.setGlHeapReadbackMeshSource();
            ForgeVoxyInstance.INSTANCE.getGpuGeometryVisualizationCache().clear();
            ForgeVoxyInstance.INSTANCE.getGpuGeometryReadbackDebugRenderer().clearStats();
            ForgeVoxyInstance.INSTANCE.getGpuGeometryReadbackMeshRefreshManager().requestRefresh(ForgeGpuGeometryReadbackMeshRefreshManager.REASON_SOURCE_SWITCH);
        } else {
            ForgeVoxyRuntimeOverrides.setSimpleGpuMeshSource(meshSource);
            ForgeVoxyInstance.INSTANCE.getGpuGeometryReadbackMeshCache().clear();
            ForgeVoxyInstance.INSTANCE.getGpuGeometryReadbackMeshRefreshManager().clear();
        }
        ForgeVoxyInstance.INSTANCE.getGpuMeshUploadManager().clear();
        String message = "Voxy simple GPU mesh source: runtime-only source set to " + meshSource
                + " (" + describeMeshSource(meshSource) + "). Recommended source for renderer migration is "
                + RECOMMENDED_MESH_SOURCE + "; fallback source is " + FALLBACK_MESH_SOURCE
                + ". GPU buffers were cleared and will be rebuilt from the selected source. "
                + (meshSource == SimpleGpuMeshSource.GL_HEAP_READBACK
                ? "Simple GPU renderer and overlay-friendly filters were enabled for GL heap readback debugging; auto refresh will run if enabled, or use /voxy geometry_gpu_readback_mesh_refresh_once after upload status shows uploadedSections > 0. "
                : "GL_HEAP_READBACK debug mesh cache was cleared to avoid source pollution. ")
                + "This was not written to toml.";
        source.sendSuccess(() -> Component.literal(message), false);
        return 1;
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
        source.sendSuccess(() -> Component.literal("Voxy preset off: runtime overrides disabled engine, auto ingest, auto CPU mesh build, auto BuiltSection build, auto geometry-manager consume, upload-only GL geometry heap, direct GL renderer skeleton, simple GPU renderer, and debug renderer. Overrides are not written to toml."), false);
        return 1;
    }

    private static int applyPresetOverlay(CommandSourceStack source) {
        ForgeVoxyRuntimeOverrides.applyOverlayPreset();
        boolean engineReady = ForgeVoxyInstance.INSTANCE.ensureActiveWorldSkeletonForCurrentWorldIfAllowed();
        String message = "Voxy preset overlay: runtime-only overlay debug applied, not written to toml. "
                + "Effective values: engine=true autoIngest=true autoCpuMesh=true simpleGpu=true source="
                + ForgeGpuMeshUploadManager.getConfiguredSource()
                + " debugRenderer=false minDistance=0 maxDistance=64 renderLoadedChunks=true skipMode=DISABLED loadedMargin=0 keepCached=true colors=layer-debug ignoreDepth=true verticalOffset=0.05. "
                + (engineReady ? "WorldEngine is active." : "No active client world was found; enter or re-enter a world to create the WorldEngine.");
        source.sendSuccess(() -> Component.literal(message), false);
        return 1;
    }

    private static int applyPresetLod(CommandSourceStack source) {
        ForgeVoxyRuntimeOverrides.applyLodPreset();
        boolean engineReady = ForgeVoxyInstance.INSTANCE.ensureActiveWorldSkeletonForCurrentWorldIfAllowed();
        String message = "Voxy preset lod: runtime-only cached LoD mode applied, not written to toml. "
                + "Effective values: engine=true autoIngest=true autoCpuMesh=true simpleGpu=true source="
                + ForgeGpuMeshUploadManager.getConfiguredSource()
                + " (" + CPU_MESH_SOURCE_DESCRIPTION + ") debugRenderer=false minDistance=5 maxDistance=64 renderLoadedChunks=false skipMode=BY_RENDER_DISTANCE loadedMargin=0 keepCached=true colors=original ignoreDepth=false verticalOffset=0.0. "
                + "Use /voxy preset lod_built_section for the recommended BuiltSection migration source. "
                + (engineReady ? "WorldEngine is active." : "No active client world was found; enter or re-enter a world to create the WorldEngine.");
        source.sendSuccess(() -> Component.literal(message), false);
        return 1;
    }

    private static int applyPresetLodBuiltSection(CommandSourceStack source) {
        ForgeVoxyRuntimeOverrides.applyLodBuiltSectionPreset();
        ForgeVoxyInstance.INSTANCE.getGpuMeshUploadManager().clear();
        boolean engineReady = ForgeVoxyInstance.INSTANCE.ensureActiveWorldSkeletonForCurrentWorldIfAllowed();
        String message = "Voxy preset lod_built_section: runtime-only BuiltSection cached LoD mode applied, not written to toml. "
                + "Effective values: engine=true autoIngest=true autoCpuMesh=false autoBuiltSection=true simpleGpu=true source=BUILT_SECTION "
                + "(" + BUILT_SECTION_SOURCE_DESCRIPTION + ") debugRenderer=false minDistance=5 maxDistance=64 renderLoadedChunks=false skipMode=BY_RENDER_DISTANCE loadedMargin=0 keepCached=true colors=original ignoreDepth=false verticalOffset=0.0. "
                + "Switch back with /voxy gpu_mesh_source cpu if you need the CPU_MESH fallback. "
                + "GPU buffers were cleared so the selected source can rebuild cleanly. "
                + (engineReady ? "WorldEngine is active." : "No active client world was found; enter or re-enter a world to create the WorldEngine.");
        source.sendSuccess(() -> Component.literal(message), false);
        return 1;
    }

    private static int applyPresetGeometryManager(CommandSourceStack source) {
        ForgeVoxyRuntimeOverrides.applyGeometryManagerPreset();
        boolean engineReady = ForgeVoxyInstance.INSTANCE.ensureActiveWorldSkeletonForCurrentWorldIfAllowed();
        String message = "Voxy preset geometry_manager: runtime-only CPU geometry manager consume mode applied, not written to toml. "
                + "Effective values forced: engine=true autoIngest=true autoBuiltSection=true autoGeometryConsume=true geometryGpuUpload=false autoCpuMesh=false. "
                + "Simple renderer/source settings were left as their current effective values: simpleGpu="
                + ForgeVoxyRuntimeOverrides.enableSimpleGpuMeshRenderer()
                + " source=" + ForgeGpuMeshUploadManager.getConfiguredSource()
                + ". This path records section ids, metadata, upload intents, and remove intents only; it does not upload GL buffers. "
                + (engineReady ? "WorldEngine is active." : "No active client world was found; enter or re-enter a world to create the WorldEngine.");
        source.sendSuccess(() -> Component.literal(message), false);
        return 1;
    }

    private static int applyPresetGlHeapVisualize(CommandSourceStack source) {
        ForgeVoxyRuntimeOverrides.applyGlHeapVisualizePreset();
        ForgeVoxyInstance.INSTANCE.getGpuMeshUploadManager().clear();
        ForgeVoxyInstance.INSTANCE.getGpuMeshCache().clear();
        ForgeVoxyInstance.INSTANCE.getGpuGeometryVisualizationCache().clear();
        ForgeVoxyInstance.INSTANCE.getGpuGeometryReadbackDebugRenderer().clearStats();
        boolean engineReady = ForgeVoxyInstance.INSTANCE.ensureActiveWorldSkeletonForCurrentWorldIfAllowed();
        String message = "Voxy preset gl_heap_visualize: runtime-only GL heap readback visualization mode applied, not written to toml. "
                + "Effective values forced: engine=true autoIngest=true autoBuiltSection=true autoGeometryConsume=true geometryGpuUpload=true geometryGpuVisualization=true simpleGpu=false debugRenderer=false. "
                + "This path reads the upload-only GL heap on command, builds a temporary debug visualization cache, and draws that cache only; it is not MDIC/VoxyRenderSystem/simple GPU renderer. "
                + "Run /voxy geometry_gpu_visualize_sample after geometry_gpu_upload_status shows uploadedSections > 0. "
                + (engineReady ? "WorldEngine is active." : "No active client world was found; enter or re-enter a world to create the WorldEngine.");
        source.sendSuccess(() -> Component.literal(message), false);
        return 1;
    }

    private static int applyPresetGlHeapReadback(CommandSourceStack source) {
        ForgeVoxyRuntimeOverrides.applyGlHeapReadbackPreset();
        ForgeVoxyInstance.INSTANCE.getGpuMeshUploadManager().clear();
        ForgeVoxyInstance.INSTANCE.getGpuMeshCache().clear();
        ForgeVoxyInstance.INSTANCE.getGpuGeometryVisualizationCache().clear();
        ForgeVoxyInstance.INSTANCE.getGpuGeometryReadbackDebugRenderer().clearStats();
        ForgeVoxyInstance.INSTANCE.getGpuGeometryReadbackMeshCache().clear();
        ForgeVoxyInstance.INSTANCE.getGpuGeometryReadbackMeshRefreshManager().clear();
        if (ForgeGpuGeometryReadbackMeshRefreshManager.refreshOnPreset()) {
            ForgeVoxyInstance.INSTANCE.getGpuGeometryReadbackMeshRefreshManager().requestRefresh(ForgeGpuGeometryReadbackMeshRefreshManager.REASON_PRESET);
        }
        boolean engineReady = ForgeVoxyInstance.INSTANCE.ensureActiveWorldSkeletonForCurrentWorldIfAllowed();
        String message = "Voxy preset gl_heap_readback: runtime-only GL heap readback simple GPU source applied, not written to toml. "
                + "Effective values forced: engine=true autoIngest=true autoBuiltSection=true autoGeometryConsume=true geometryGpuUpload=true simpleGpu=true source=GL_HEAP_READBACK readbackAutoRefresh=true geometryGpuVisualization=false debugRenderer=false minDistance=0 maxDistance=64 renderLoadedChunks=true skipMode=DISABLED keepCached=true colors=original ignoreDepth=true verticalOffset=0.05. "
                + "This path rate-limits upload-only GL heap readback into a simple GPU mesh cache, then uses the existing vanilla VertexBuffer renderer; it is not MDIC/VoxyRenderSystem. "
                + "Auto refresh is requested now and will retry after upload status shows uploadedSections > 0; use /voxy geometry_gpu_readback_mesh_refresh_once for a manual refresh. "
                + (engineReady ? "WorldEngine is active." : "No active client world was found; enter or re-enter a world to create the WorldEngine.");
        source.sendSuccess(() -> Component.literal(message), false);
        return 1;
    }

    private static int applyPresetDirectGlDebug(CommandSourceStack source) {
        ForgeVoxyRuntimeOverrides.applyDirectGlDebugPreset();
        ForgeVoxyInstance.INSTANCE.getGpuMeshUploadManager().clear();
        ForgeVoxyInstance.INSTANCE.getGpuGeometryVisualizationCache().clear();
        ForgeVoxyInstance.INSTANCE.getGpuGeometryReadbackMeshCache().clear();
        ForgeVoxyInstance.INSTANCE.getGpuGeometryReadbackMeshRefreshManager().clear();
        ForgeVoxyInstance.INSTANCE.getGpuGeometryReadbackDebugRenderer().clearStats();
        ForgeVoxyInstance.INSTANCE.getDirectGpuGeometryRenderer().clear();
        ForgeVoxyInstance.INSTANCE.getDirectGpuGeometryRenderer().markEnabledRuntime();
        ForgeVoxyInstance.INSTANCE.getDirectGpuGeometryRenderer().requestAutoPlan(ForgeDirectGpuGeometryRenderer.REASON_PRESET);
        boolean engineReady = ForgeVoxyInstance.INSTANCE.ensureActiveWorldSkeletonForCurrentWorldIfAllowed();
        String message = "Voxy preset direct_gl_debug: runtime-only G5.3 direct GL debug renderer applied, not written to toml. "
                + "Effective values forced: engine=true autoIngest=true autoBuiltSection=true autoGeometryConsume=true geometryGpuUpload=true directGlRenderer=true directAutoPlan=true simpleGpu=false readbackAutoRefresh=false geometryGpuVisualization=false debugRenderer=false. "
                + "actualDrawEnabled=false by default. Auto plan may build a camera/radius-aware draw list after upload status shows uploadedSections > 0; run /voxy direct_gl_renderer_draw_enable to draw it or /voxy direct_gl_renderer_auto_plan_once for a manual plan. "
                + "This does not enable MDIC, VoxyRenderSystem, shaderpack, or the simple renderer. "
                + (engineReady ? "WorldEngine is active." : "No active client world was found; enter or re-enter a world to create the WorldEngine.");
        source.sendSuccess(() -> Component.literal(message), false);
        return 1;
    }

    private static int clearPreset(CommandSourceStack source) {
        ForgeVoxyRuntimeOverrides.clear();
        ForgeVoxyInstance.INSTANCE.getGpuGeometryReadbackMeshCache().clear();
        ForgeVoxyInstance.INSTANCE.getGpuGeometryReadbackMeshRefreshManager().clear();
        ForgeVoxyInstance.INSTANCE.getGpuMeshUploadManager().clear();
        ForgeVoxyInstance.INSTANCE.getDirectGpuGeometryRenderer().clear();
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
                "Voxy preset status: active=%s overrides=%s engine=%s(%s) autoIngest=%s(%s) autoCpuMesh=%s(%s) autoBuiltSection=%s(%s) autoGeometryConsume=%s(%s) geometryGpuUpload=%s(%s) geometryGpuVisualization=%s(%s) readbackAutoRefresh=%s(%s) directGlRenderer=%s(%s) directAutoPlan=%s(%s) directActualDraw=%s(%s) visualizationAlpha=%.2f(%s) visualizationIgnoreDepth=%s(%s) visualizationDoubleSided=%s(%s) simpleGpu=%s(%s) debugRenderer=%s(%s) source=%s(%s) sourceRole=%s recommendedSource=%s fallbackSource=%s minDistance=%d(%s) maxDistance=%d(%s) renderLoadedChunks=%s(%s) skipMode=%s(%s) loadedMargin=%d(%s) keepCached=%s(%s) colors=%s(%s) simpleIgnoreDepth=%s(%s) simpleVerticalOffset=%.3f(%s) simpleAlpha=%.2f(%s) debugAlpha=%.2f(%s)",
                status.presetName(),
                status.hasOverrides(),
                status.enableWorldEngineSkeleton(),
                status.enableWorldEngineSkeletonSource(),
                status.enableAutoChunkIngest(),
                status.enableAutoChunkIngestSource(),
                status.enableAutoCpuMeshBuild(),
                status.enableAutoCpuMeshBuildSource(),
                status.enableAutoBuiltSectionBuild(),
                status.enableAutoBuiltSectionBuildSource(),
                status.enableAutoGeometryManagerConsume(),
                status.enableAutoGeometryManagerConsumeSource(),
                status.enableGeometryGpuUpload(),
                status.enableGeometryGpuUploadSource(),
                status.enableGeometryGpuVisualization(),
                status.enableGeometryGpuVisualizationSource(),
                status.enableGeometryGpuReadbackMeshAutoRefresh(),
                status.enableGeometryGpuReadbackMeshAutoRefreshSource(),
                status.enableDirectGpuGeometryRenderer(),
                status.enableDirectGpuGeometryRendererSource(),
                status.enableDirectGpuGeometryAutoPlan(),
                status.enableDirectGpuGeometryAutoPlanSource(),
                status.directGpuGeometryRendererActualDraw(),
                status.directGpuGeometryRendererActualDrawSource(),
                status.geometryGpuVisualizationAlpha(),
                status.geometryGpuVisualizationAlphaSource(),
                status.geometryGpuVisualizationIgnoreDepth(),
                status.geometryGpuVisualizationIgnoreDepthSource(),
                status.geometryGpuVisualizationDoubleSided(),
                status.geometryGpuVisualizationDoubleSidedSource(),
                status.enableSimpleGpuMeshRenderer(),
                status.enableSimpleGpuMeshRendererSource(),
                status.enableDebugMeshRenderer(),
                status.enableDebugMeshRendererSource(),
                status.simpleGpuMeshSource(),
                status.simpleGpuMeshSourceSource(),
                describeMeshSource(status.simpleGpuMeshSource()),
                RECOMMENDED_MESH_SOURCE,
                FALLBACK_MESH_SOURCE,
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
                || !ForgeVoxyRuntimeOverrides.enableSimpleGpuMeshRenderer()) {
            return "enable engine, auto ingest, and simple GPU renderer; defaults stay off";
        }
        if (ForgeGpuMeshUploadManager.getConfiguredSource() == SimpleGpuMeshSource.BUILT_SECTION
                && !ForgeVoxyRuntimeOverrides.enableAutoBuiltSectionBuild()) {
            return "source=BUILT_SECTION needs auto BuiltSection build; use /voxy preset lod_built_section for a runtime-only test setup";
        }
        if (ForgeGpuMeshUploadManager.getConfiguredSource() == SimpleGpuMeshSource.CPU_MESH
                && !ForgeVoxyRuntimeOverrides.enableAutoCpuMeshBuild()) {
            return "source=CPU_MESH needs auto CPU mesh build; use /voxy preset lod for a runtime-only test setup";
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

    private static String describeMeshSource(SimpleGpuMeshSource source) {
        if (source == SimpleGpuMeshSource.BUILT_SECTION) {
            return BUILT_SECTION_SOURCE_DESCRIPTION;
        }
        if (source == SimpleGpuMeshSource.GL_HEAP_READBACK) {
            return GL_HEAP_READBACK_SOURCE_DESCRIPTION;
        }
        return CPU_MESH_SOURCE_DESCRIPTION;
    }

    private static String formatGeometryPointer(int geometryPtr) {
        return geometryPtr < 0 ? "none" : Long.toUnsignedString(Integer.toUnsignedLong(geometryPtr));
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
        ForgeVoxyInstance.INSTANCE.getSectionGeometryConsumeManager().clear();
        ForgeVoxyInstance.INSTANCE.getGpuMeshUploadManager().clear();
        ForgeVoxyInstance.INSTANCE.getGpuGeometryUploadManager().clear();
        ForgeVoxyInstance.INSTANCE.getGpuGeometryVisualizationCache().clear();
        ForgeVoxyInstance.INSTANCE.getGpuGeometryReadbackMeshCache().clear();
        ForgeVoxyInstance.INSTANCE.getGpuGeometryReadbackMeshRefreshManager().clear();
        ForgeVoxyInstance.INSTANCE.getGpuGeometryReadbackDebugRenderer().clearStats();
        ForgeVoxyInstance.INSTANCE.getDirectGpuGeometryRenderer().clear();
        ForgeVoxyInstance.INSTANCE.getGpuMeshCache().clear();
    }

    private static int clearMeshCache(CommandSourceStack source) {
        ForgeVoxyInstance.INSTANCE.getCpuMeshCache().clear();
        ForgeVoxyInstance.INSTANCE.getVoxyGeometryCache().clear();
        ForgeVoxyInstance.INSTANCE.getSectionGeometryConsumeManager().clear();
        ForgeVoxyInstance.INSTANCE.getCpuMeshBuildManager().clear();
        ForgeVoxyInstance.INSTANCE.getBuiltSectionBuildManager().clear();
        ForgeVoxyInstance.INSTANCE.getGpuMeshUploadManager().clear();
        ForgeVoxyInstance.INSTANCE.getGpuGeometryUploadManager().clear();
        ForgeVoxyInstance.INSTANCE.getGpuGeometryVisualizationCache().clear();
        ForgeVoxyInstance.INSTANCE.getGpuGeometryReadbackMeshCache().clear();
        ForgeVoxyInstance.INSTANCE.getGpuGeometryReadbackMeshRefreshManager().clear();
        ForgeVoxyInstance.INSTANCE.getGpuGeometryReadbackDebugRenderer().clearStats();
        ForgeVoxyInstance.INSTANCE.getDirectGpuGeometryRenderer().clear();
        source.sendSuccess(() -> Component.literal("Voxy: cleared CPU mesh cache, CPU-only BuiltSection cache, CPU-only section geometry manager, simple GPU mesh cache, upload-only GL geometry heap, direct GL renderer skeleton state, GL heap readback visualization/readback-mesh caches, readback mesh auto-refresh state, auto mesh build record, and auto BuiltSection build record."), false);
        return 1;
    }

    private static int clearGpuMeshCache(CommandSourceStack source) {
        ForgeVoxyInstance.INSTANCE.getGpuMeshUploadManager().clear();
        source.sendSuccess(() -> Component.literal("Voxy: cleared simple GPU mesh buffers. CPU mesh and BuiltSection caches were left intact and can re-upload."), false);
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
        ForgeVoxyInstance.INSTANCE.getSectionGeometryConsumeManager().clear();
        ForgeVoxyInstance.INSTANCE.getGpuMeshUploadManager().clear();
        ForgeVoxyInstance.INSTANCE.getGpuGeometryUploadManager().clear();
        ForgeVoxyInstance.INSTANCE.getGpuGeometryVisualizationCache().clear();
        ForgeVoxyInstance.INSTANCE.getGpuGeometryReadbackMeshCache().clear();
        ForgeVoxyInstance.INSTANCE.getGpuGeometryReadbackMeshRefreshManager().clear();
        ForgeVoxyInstance.INSTANCE.getGpuGeometryReadbackDebugRenderer().clearStats();
        ForgeVoxyInstance.INSTANCE.getDirectGpuGeometryRenderer().clear();
        source.sendSuccess(() -> Component.literal("Voxy: cleared debug pipeline ingest records, mesh build records, BuiltSection build records, CPU mesh cache, CPU-only BuiltSection cache, CPU-only section geometry manager, simple GPU mesh cache, upload-only GL geometry heap, direct GL renderer skeleton state, GL heap readback visualization/readback-mesh caches, and readback mesh auto-refresh state."), false);
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
