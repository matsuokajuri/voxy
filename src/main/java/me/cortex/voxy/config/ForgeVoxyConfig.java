package me.cortex.voxy.config;

import net.minecraftforge.common.ForgeConfigSpec;

public final class ForgeVoxyConfig {
    public static final ForgeConfigSpec CLIENT_SPEC;
    public static final ForgeConfigSpec.BooleanValue ENABLED;
    public static final ForgeConfigSpec.BooleanValue ENABLE_WORLD_ENGINE_SKELETON;
    public static final ForgeConfigSpec.BooleanValue ENABLE_AUTO_CHUNK_INGEST;
    public static final ForgeConfigSpec.IntValue AUTO_INGEST_RADIUS;
    public static final ForgeConfigSpec.IntValue AUTO_INGEST_MAX_CHUNKS_PER_TICK;
    public static final ForgeConfigSpec.IntValue AUTO_INGEST_COOLDOWN_TICKS;
    public static final ForgeConfigSpec.BooleanValue ENABLE_AUTO_CPU_MESH_BUILD;
    public static final ForgeConfigSpec.IntValue AUTO_MESH_BUILD_RADIUS;
    public static final ForgeConfigSpec.IntValue AUTO_MESH_BUILD_MAX_CHUNKS_PER_TICK;
    public static final ForgeConfigSpec.IntValue AUTO_MESH_BUILD_COOLDOWN_TICKS;
    public static final ForgeConfigSpec.BooleanValue ENABLE_AUTO_BUILT_SECTION_BUILD;
    public static final ForgeConfigSpec.IntValue AUTO_BUILT_SECTION_BUILD_RADIUS;
    public static final ForgeConfigSpec.IntValue AUTO_BUILT_SECTION_MAX_CHUNKS_PER_TICK;
    public static final ForgeConfigSpec.IntValue AUTO_BUILT_SECTION_COOLDOWN_TICKS;
    public static final ForgeConfigSpec.BooleanValue ENABLE_AUTO_GEOMETRY_MANAGER_CONSUME;
    public static final ForgeConfigSpec.IntValue AUTO_GEOMETRY_CONSUME_RADIUS;
    public static final ForgeConfigSpec.IntValue AUTO_GEOMETRY_CONSUME_MAX_SECTIONS_PER_TICK;
    public static final ForgeConfigSpec.IntValue AUTO_GEOMETRY_CONSUME_COOLDOWN_TICKS;
    public static final ForgeConfigSpec.BooleanValue ENABLE_GEOMETRY_GPU_UPLOAD;
    public static final ForgeConfigSpec.IntValue GEOMETRY_GPU_HEAP_BYTES;
    public static final ForgeConfigSpec.IntValue GEOMETRY_GPU_METADATA_BYTES;
    public static final ForgeConfigSpec.IntValue GEOMETRY_GPU_MAX_UPLOADS_PER_TICK;
    public static final ForgeConfigSpec.IntValue GEOMETRY_GPU_MAX_METADATA_WRITES_PER_TICK;
    public static final ForgeConfigSpec.BooleanValue GEOMETRY_GPU_DEBUG_LOG;
    public static final ForgeConfigSpec.BooleanValue ENABLE_GEOMETRY_GPU_VISUALIZATION;
    public static final ForgeConfigSpec.IntValue GEOMETRY_GPU_VISUALIZATION_MAX_SECTIONS;
    public static final ForgeConfigSpec.IntValue GEOMETRY_GPU_VISUALIZATION_MAX_RECORDS;
    public static final ForgeConfigSpec.DoubleValue GEOMETRY_GPU_VISUALIZATION_ALPHA;
    public static final ForgeConfigSpec.BooleanValue GEOMETRY_GPU_VISUALIZATION_IGNORE_DEPTH;
    public static final ForgeConfigSpec.BooleanValue GEOMETRY_GPU_VISUALIZATION_DOUBLE_SIDED;
    public static final ForgeConfigSpec.IntValue GEOMETRY_GPU_READBACK_MESH_MAX_SECTIONS;
    public static final ForgeConfigSpec.IntValue GEOMETRY_GPU_READBACK_MESH_MAX_RECORDS;
    public static final ForgeConfigSpec.BooleanValue ENABLE_GEOMETRY_GPU_READBACK_MESH_AUTO_REFRESH;
    public static final ForgeConfigSpec.IntValue GEOMETRY_GPU_READBACK_MESH_REFRESH_COOLDOWN_TICKS;
    public static final ForgeConfigSpec.IntValue GEOMETRY_GPU_READBACK_MESH_REFRESH_MAX_SECTIONS;
    public static final ForgeConfigSpec.IntValue GEOMETRY_GPU_READBACK_MESH_REFRESH_MAX_RECORDS;
    public static final ForgeConfigSpec.BooleanValue GEOMETRY_GPU_READBACK_MESH_REFRESH_ONLY_WHEN_SOURCE_ACTIVE;
    public static final ForgeConfigSpec.BooleanValue GEOMETRY_GPU_READBACK_MESH_REFRESH_ONLY_WHEN_RENDERER_ENABLED;
    public static final ForgeConfigSpec.BooleanValue GEOMETRY_GPU_READBACK_MESH_REFRESH_ON_DIMENSION_CHANGE;
    public static final ForgeConfigSpec.BooleanValue GEOMETRY_GPU_READBACK_MESH_REFRESH_ON_PRESET;
    public static final ForgeConfigSpec.BooleanValue ENABLE_DIRECT_GPU_GEOMETRY_RENDERER;
    public static final ForgeConfigSpec.IntValue DIRECT_GPU_GEOMETRY_RENDERER_MAX_SECTIONS;
    public static final ForgeConfigSpec.IntValue DIRECT_GPU_GEOMETRY_RENDERER_MAX_RECORDS;
    public static final ForgeConfigSpec.IntValue DIRECT_GPU_GEOMETRY_RENDERER_MAX_PLAN_CANDIDATES;
    public static final ForgeConfigSpec.IntValue DIRECT_GPU_GEOMETRY_RENDERER_MAX_DRAW_SECTIONS;
    public static final ForgeConfigSpec.IntValue DIRECT_GPU_GEOMETRY_RENDERER_MAX_DRAW_RECORDS;
    public static final ForgeConfigSpec.IntValue DIRECT_GPU_GEOMETRY_RENDERER_MAX_RECORDS_PER_SECTION;
    public static final ForgeConfigSpec.IntValue DIRECT_GPU_GEOMETRY_RENDERER_RENDER_DISTANCE_CHUNKS;
    public static final ForgeConfigSpec.DoubleValue DIRECT_GPU_GEOMETRY_RENDERER_DEBUG_ALPHA;
    public static final ForgeConfigSpec.BooleanValue DIRECT_GPU_GEOMETRY_RENDERER_IGNORE_DEPTH;
    public static final ForgeConfigSpec.BooleanValue DIRECT_GPU_GEOMETRY_RENDERER_DOUBLE_SIDED;
    public static final ForgeConfigSpec.BooleanValue DIRECT_GPU_GEOMETRY_RENDERER_ACTUAL_DRAW;
    public static final ForgeConfigSpec.BooleanValue DIRECT_GPU_GEOMETRY_RENDERER_DEBUG_LOG;
    public static final ForgeConfigSpec.DoubleValue DIRECT_GPU_GEOMETRY_RENDERER_FRAME_BUDGET_MS;
    public static final ForgeConfigSpec.DoubleValue DIRECT_GPU_GEOMETRY_RENDERER_PLAN_BUDGET_MS;
    public static final ForgeConfigSpec.BooleanValue ENABLE_DIRECT_GPU_GEOMETRY_AUTO_PLAN;
    public static final ForgeConfigSpec.IntValue DIRECT_GPU_GEOMETRY_AUTO_PLAN_COOLDOWN_TICKS;
    public static final ForgeConfigSpec.IntValue DIRECT_GPU_GEOMETRY_AUTO_PLAN_MOVE_THRESHOLD_BLOCKS;
    public static final ForgeConfigSpec.BooleanValue DIRECT_GPU_GEOMETRY_AUTO_PLAN_ONLY_WHEN_ENABLED;
    public static final ForgeConfigSpec.BooleanValue DIRECT_GPU_GEOMETRY_AUTO_PLAN_ONLY_WHEN_ACTUAL_DRAW_ENABLED;
    public static final ForgeConfigSpec.BooleanValue DIRECT_GPU_GEOMETRY_AUTO_PLAN_ON_DIMENSION_CHANGE;
    public static final ForgeConfigSpec.IntValue DIRECT_GPU_GEOMETRY_AUTO_PLAN_MAX_CANDIDATES;
    public static final ForgeConfigSpec.IntValue DIRECT_GPU_GEOMETRY_AUTO_PLAN_MAX_SECTIONS;
    public static final ForgeConfigSpec.IntValue DIRECT_GPU_GEOMETRY_AUTO_PLAN_MAX_RECORDS;
    public static final ForgeConfigSpec.IntValue CPU_MESH_CACHE_MAX_ENTRIES;
    public static final ForgeConfigSpec.IntValue BUILT_SECTION_CACHE_MAX_ENTRIES;
    public static final ForgeConfigSpec.BooleanValue ENABLE_DEBUG_MESH_RENDERER;
    public static final ForgeConfigSpec.IntValue DEBUG_MESH_RENDER_DISTANCE_CHUNKS;
    public static final ForgeConfigSpec.IntValue DEBUG_MESH_MAX_RENDERED_ENTRIES;
    public static final ForgeConfigSpec.BooleanValue DEBUG_MESH_WIREFRAME;
    public static final ForgeConfigSpec.DoubleValue DEBUG_MESH_ALPHA;
    public static final ForgeConfigSpec.BooleanValue DEBUG_MESH_IGNORE_DEPTH;
    public static final ForgeConfigSpec.DoubleValue DEBUG_MESH_VERTICAL_OFFSET;
    public static final ForgeConfigSpec.BooleanValue ENABLE_SIMPLE_GPU_MESH_RENDERER;
    public static final ForgeConfigSpec.EnumValue<SimpleGpuMeshSource> SIMPLE_GPU_MESH_SOURCE;
    public static final ForgeConfigSpec.IntValue SIMPLE_GPU_MESH_MAX_UPLOADS_PER_TICK;
    public static final ForgeConfigSpec.IntValue SIMPLE_GPU_MESH_MAX_BUFFERS;
    public static final ForgeConfigSpec.IntValue SIMPLE_GPU_MESH_RENDER_DISTANCE_CHUNKS;
    public static final ForgeConfigSpec.IntValue SIMPLE_GPU_MESH_MIN_RENDER_DISTANCE_CHUNKS;
    public static final ForgeConfigSpec.IntValue SIMPLE_GPU_MESH_MAX_RENDERED_BUFFERS;
    public static final ForgeConfigSpec.DoubleValue SIMPLE_GPU_MESH_ALPHA;
    public static final ForgeConfigSpec.BooleanValue SIMPLE_GPU_MESH_USE_ORIGINAL_COLORS;
    public static final ForgeConfigSpec.BooleanValue SIMPLE_GPU_MESH_RENDER_LOADED_CHUNKS;
    public static final ForgeConfigSpec.EnumValue<SimpleGpuMeshLoadedChunkSkipMode> SIMPLE_GPU_MESH_LOADED_CHUNK_SKIP_MODE;
    public static final ForgeConfigSpec.IntValue SIMPLE_GPU_MESH_LOADED_CHUNK_MARGIN;
    public static final ForgeConfigSpec.BooleanValue SIMPLE_GPU_MESH_KEEP_CACHED_CHUNKS;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        builder.push("general");
        ENABLED = builder
                .comment("Placeholder toggle for the Forge 1.20.1 skeleton. It does not enable LoD rendering yet.")
                .define("enabled", true);
        ENABLE_WORLD_ENGINE_SKELETON = builder
                .comment("Creates an empty in-memory WorldEngine on client world join for lifecycle testing only. It does not ingest chunks or render LoD.")
                .define("enableWorldEngineSkeleton", false);
        ENABLE_AUTO_CHUNK_INGEST = builder
                .comment("Automatically ingests already-loaded client chunks into the in-memory Voxy skeleton. Requires enableWorldEngineSkeleton and does not render LoD.")
                .define("enableAutoChunkIngest", false);
        AUTO_INGEST_RADIUS = builder
                .comment("Chunk radius around the player to scan for already-loaded chunks when auto ingest is enabled.")
                .defineInRange("autoIngestRadius", 2, 0, 8);
        AUTO_INGEST_MAX_CHUNKS_PER_TICK = builder
                .comment("Maximum already-loaded chunks to ingest per client tick when auto ingest is enabled.")
                .defineInRange("autoIngestMaxChunksPerTick", 1, 1, 8);
        AUTO_INGEST_COOLDOWN_TICKS = builder
                .comment("Ticks between nearby chunk scans. Queued chunks may still be processed every tick.")
                .defineInRange("autoIngestCooldownTicks", 20, 0, 200);
        ENABLE_AUTO_CPU_MESH_BUILD = builder
                .comment("Automatically builds cached CPU mesh for already-ingested nearby chunks. Requires enableWorldEngineSkeleton and does not render by itself.")
                .define("enableAutoCpuMeshBuild", false);
        AUTO_MESH_BUILD_RADIUS = builder
                .comment("Chunk radius around the player to scan for already-ingested chunks when auto CPU mesh build is enabled.")
                .defineInRange("autoMeshBuildRadius", 2, 0, 8);
        AUTO_MESH_BUILD_MAX_CHUNKS_PER_TICK = builder
                .comment("Maximum chunks to build into CPU mesh per client tick when auto CPU mesh build is enabled.")
                .defineInRange("autoMeshBuildMaxChunksPerTick", 1, 1, 8);
        AUTO_MESH_BUILD_COOLDOWN_TICKS = builder
                .comment("Ticks between nearby chunk scans for auto CPU mesh build. Queued chunks may still be processed every tick.")
                .defineInRange("autoMeshBuildCooldownTicks", 20, 0, 200);
        ENABLE_AUTO_BUILT_SECTION_BUILD = builder
                .comment("Automatically builds CPU-only Voxy BuiltSection-format data for already-ingested nearby chunks. This is renderer migration validation only and does not render by itself.")
                .define("enableAutoBuiltSectionBuild", false);
        AUTO_BUILT_SECTION_BUILD_RADIUS = builder
                .comment("Chunk radius around the player to scan for already-ingested chunks when auto BuiltSection build is enabled.")
                .defineInRange("autoBuiltSectionBuildRadius", 2, 0, 8);
        AUTO_BUILT_SECTION_MAX_CHUNKS_PER_TICK = builder
                .comment("Maximum chunks to build into CPU-only BuiltSection data per client tick when auto BuiltSection build is enabled.")
                .defineInRange("autoBuiltSectionMaxChunksPerTick", 1, 1, 8);
        AUTO_BUILT_SECTION_COOLDOWN_TICKS = builder
                .comment("Ticks between nearby chunk scans for auto BuiltSection build. Queued chunks may still be processed every tick.")
                .defineInRange("autoBuiltSectionCooldownTicks", 20, 0, 200);
        ENABLE_AUTO_GEOMETRY_MANAGER_CONSUME = builder
                .comment("Automatically consumes CPU-only BuiltSection cache entries into the CPU-only section geometry manager. This records section ids, metadata, and upload/remove intents only; it does not upload GL buffers.")
                .define("enableAutoGeometryManagerConsume", false);
        AUTO_GEOMETRY_CONSUME_RADIUS = builder
                .comment("Chunk radius around the player to scan for BuiltSection cache entries when auto geometry-manager consume is enabled.")
                .defineInRange("autoGeometryConsumeRadius", 2, 0, 8);
        AUTO_GEOMETRY_CONSUME_MAX_SECTIONS_PER_TICK = builder
                .comment("Maximum BuiltSection entries to consume into the CPU-only section geometry manager per client tick.")
                .defineInRange("autoGeometryConsumeMaxSectionsPerTick", 4, 1, 64);
        AUTO_GEOMETRY_CONSUME_COOLDOWN_TICKS = builder
                .comment("Ticks between nearby BuiltSection cache scans for auto geometry-manager consume. Queued sections may still be processed every tick.")
                .defineInRange("autoGeometryConsumeCooldownTicks", 20, 0, 200);
        ENABLE_GEOMETRY_GPU_UPLOAD = builder
                .comment("Upload-only GL geometry heap proof of concept. It copies CPU-only section geometry manager intents into small GL buffers but does not render from them.")
                .define("enableGeometryGpuUpload", false);
        GEOMETRY_GPU_HEAP_BYTES = builder
                .comment("Byte capacity for the upload-only GL geometry heap PoC. Keep this small; the final Voxy heap is not connected here.")
                .defineInRange("geometryGpuHeapBytes", 16 * 1024 * 1024, 1024 * 1024, 256 * 1024 * 1024);
        GEOMETRY_GPU_METADATA_BYTES = builder
                .comment("Byte capacity for the upload-only GL section metadata buffer PoC.")
                .defineInRange("geometryGpuMetadataBytes", 8 * 1024 * 1024, 1024 * 1024, 64 * 1024 * 1024);
        GEOMETRY_GPU_MAX_UPLOADS_PER_TICK = builder
                .comment("Maximum CPU geometry upload intents copied into the upload-only GL heap per client tick.")
                .defineInRange("geometryGpuMaxUploadsPerTick", 4, 1, 64);
        GEOMETRY_GPU_MAX_METADATA_WRITES_PER_TICK = builder
                .comment("Maximum dirty 32-byte section metadata samples copied into the upload-only GL metadata buffer per client tick.")
                .defineInRange("geometryGpuMaxMetadataWritesPerTick", 256, 1, 4096);
        GEOMETRY_GPU_DEBUG_LOG = builder
                .comment("Logs upload-only GL geometry heap activity summaries. Useful while validating the future geometry heap migration.")
                .define("geometryGpuDebugLog", false);
        ENABLE_GEOMETRY_GPU_VISUALIZATION = builder
                .comment("Draws a temporary debug visualization built from upload-only GL heap readback. This is a validation path only and is disabled by default.")
                .define("enableGeometryGpuVisualization", false);
        GEOMETRY_GPU_VISUALIZATION_MAX_SECTIONS = builder
                .comment("Maximum uploaded sections read back by one geometry_gpu_visualize_sample command.")
                .defineInRange("geometryGpuVisualizationMaxSections", 8, 1, 32);
        GEOMETRY_GPU_VISUALIZATION_MAX_RECORDS = builder
                .comment("Maximum quad records read back by one geometry_gpu_visualize_sample command.")
                .defineInRange("geometryGpuVisualizationMaxRecords", 8192, 1, 65536);
        GEOMETRY_GPU_VISUALIZATION_ALPHA = builder
                .comment("Alpha used by the upload-only GL heap readback debug visualization.")
                .defineInRange("geometryGpuVisualizationAlpha", 0.75D, 0.05D, 1.0D);
        GEOMETRY_GPU_VISUALIZATION_IGNORE_DEPTH = builder
                .comment("Draws the upload-only GL heap readback visualization through terrain.")
                .define("geometryGpuVisualizationIgnoreDepth", false);
        GEOMETRY_GPU_VISUALIZATION_DOUBLE_SIDED = builder
                .comment("Disables culling for the upload-only GL heap readback visualization so debug quads remain visible while winding is still being audited.")
                .define("geometryGpuVisualizationDoubleSided", true);
        GEOMETRY_GPU_READBACK_MESH_MAX_SECTIONS = builder
                .comment("Maximum uploaded sections read back by one geometry_gpu_readback_mesh_build command for the simple GPU GL_HEAP_READBACK debug source.")
                .defineInRange("geometryGpuReadbackMeshMaxSections", 16, 1, 64);
        GEOMETRY_GPU_READBACK_MESH_MAX_RECORDS = builder
                .comment("Maximum quad records read back by one geometry_gpu_readback_mesh_build command for the simple GPU GL_HEAP_READBACK debug source.")
                .defineInRange("geometryGpuReadbackMeshMaxRecords", 16384, 1, 131072);
        ENABLE_GEOMETRY_GPU_READBACK_MESH_AUTO_REFRESH = builder
                .comment("Automatically refreshes the GL_HEAP_READBACK simple GPU debug source from the upload-only GL heap. Disabled by default and rate-limited.")
                .define("enableGeometryGpuReadbackMeshAutoRefresh", false);
        GEOMETRY_GPU_READBACK_MESH_REFRESH_COOLDOWN_TICKS = builder
                .comment("Ticks between automatic GL_HEAP_READBACK readback mesh refresh attempts.")
                .defineInRange("geometryGpuReadbackMeshRefreshCooldownTicks", 40, 1, 400);
        GEOMETRY_GPU_READBACK_MESH_REFRESH_MAX_SECTIONS = builder
                .comment("Maximum uploaded sections read back by one automatic GL_HEAP_READBACK refresh.")
                .defineInRange("geometryGpuReadbackMeshRefreshMaxSections", 16, 1, 64);
        GEOMETRY_GPU_READBACK_MESH_REFRESH_MAX_RECORDS = builder
                .comment("Maximum quad records read back by one automatic GL_HEAP_READBACK refresh.")
                .defineInRange("geometryGpuReadbackMeshRefreshMaxRecords", 16384, 1, 131072);
        GEOMETRY_GPU_READBACK_MESH_REFRESH_ONLY_WHEN_SOURCE_ACTIVE = builder
                .comment("When true, automatic GL_HEAP_READBACK refresh only runs while the simple GPU source is GL_HEAP_READBACK.")
                .define("geometryGpuReadbackMeshRefreshOnlyWhenSourceActive", true);
        GEOMETRY_GPU_READBACK_MESH_REFRESH_ONLY_WHEN_RENDERER_ENABLED = builder
                .comment("When true, automatic GL_HEAP_READBACK refresh only runs while the simple GPU renderer is enabled.")
                .define("geometryGpuReadbackMeshRefreshOnlyWhenRendererEnabled", true);
        GEOMETRY_GPU_READBACK_MESH_REFRESH_ON_DIMENSION_CHANGE = builder
                .comment("Requests a delayed GL_HEAP_READBACK refresh after client dimension changes when auto refresh is enabled.")
                .define("geometryGpuReadbackMeshRefreshOnDimensionChange", true);
        GEOMETRY_GPU_READBACK_MESH_REFRESH_ON_PRESET = builder
                .comment("Requests a delayed GL_HEAP_READBACK refresh when the runtime gl_heap_readback preset is applied.")
                .define("geometryGpuReadbackMeshRefreshOnPreset", true);
        ENABLE_DIRECT_GPU_GEOMETRY_RENDERER = builder
                .comment("G5.x direct GL geometry debug renderer flag. Disabled by default; actual drawing is controlled by a separate runtime-only command.")
                .define("enableDirectGpuGeometryRenderer", false);
        DIRECT_GPU_GEOMETRY_RENDERER_MAX_SECTIONS = builder
                .comment("Maximum uploaded section metadata entries inspected by one direct GL plan/build command.")
                .defineInRange("directGpuGeometryRendererMaxSections", 128, 1, 512);
        DIRECT_GPU_GEOMETRY_RENDERER_MAX_RECORDS = builder
                .comment("Maximum packed geometry records counted by one direct_gl_renderer_plan_sample command.")
                .defineInRange("directGpuGeometryRendererMaxRecords", 16384, 1, 131072);
        DIRECT_GPU_GEOMETRY_RENDERER_MAX_PLAN_CANDIDATES = builder
                .comment("Maximum uploaded section metadata entries considered by one G5.3 camera-aware direct GL draw-list build.")
                .defineInRange("directGpuGeometryRendererMaxPlanCandidates", 256, 1, 2048);
        DIRECT_GPU_GEOMETRY_RENDERER_MAX_DRAW_SECTIONS = builder
                .comment("Maximum uploaded sections drawn by the G5.3 direct GL debug renderer. Keep this conservative while the path is experimental.")
                .defineInRange("directGpuGeometryRendererMaxDrawSections", 16, 1, 64);
        DIRECT_GPU_GEOMETRY_RENDERER_MAX_DRAW_RECORDS = builder
                .comment("Maximum packed quad records drawn by one G5.3 direct GL debug draw list.")
                .defineInRange("directGpuGeometryRendererMaxDrawRecords", 32768, 1, 131072);
        DIRECT_GPU_GEOMETRY_RENDERER_MAX_RECORDS_PER_SECTION = builder
                .comment("Maximum packed quad records drawn from a single uploaded section by the G5.3 direct GL debug renderer.")
                .defineInRange("directGpuGeometryRendererMaxRecordsPerSection", 4096, 1, 16384);
        DIRECT_GPU_GEOMETRY_RENDERER_RENDER_DISTANCE_CHUNKS = builder
                .comment("Maximum chunk distance used when selecting sections for G5.3 direct GL debug draw lists.")
                .defineInRange("directGpuGeometryRendererRenderDistanceChunks", 12, 1, 64);
        DIRECT_GPU_GEOMETRY_RENDERER_DEBUG_ALPHA = builder
                .comment("Alpha used by the G5.x direct GL debug renderer.")
                .defineInRange("directGpuGeometryRendererDebugAlpha", 0.75D, 0.05D, 1.0D);
        DIRECT_GPU_GEOMETRY_RENDERER_IGNORE_DEPTH = builder
                .comment("Draws G5.x direct GL debug quads through terrain to make the debug draw easier to see.")
                .define("directGpuGeometryRendererIgnoreDepth", true);
        DIRECT_GPU_GEOMETRY_RENDERER_DOUBLE_SIDED = builder
                .comment("Disables culling for the G5.x direct GL debug renderer while winding is still being validated.")
                .define("directGpuGeometryRendererDoubleSided", true);
        DIRECT_GPU_GEOMETRY_RENDERER_ACTUAL_DRAW = builder
                .comment("Actually issues G5.x direct GL debug draw calls. Disabled by default; use the runtime command for testing.")
                .define("directGpuGeometryRendererActualDraw", false);
        DIRECT_GPU_GEOMETRY_RENDERER_DEBUG_LOG = builder
                .comment("Logs G5.x direct GL geometry renderer debug summaries.")
                .define("directGpuGeometryRendererDebugLog", false);
        DIRECT_GPU_GEOMETRY_RENDERER_FRAME_BUDGET_MS = builder
                .comment("Soft per-frame timing budget in milliseconds for the G5.x direct GL debug renderer.")
                .defineInRange("directGpuGeometryRendererFrameBudgetMs", 4.0D, 0.1D, 100.0D);
        DIRECT_GPU_GEOMETRY_RENDERER_PLAN_BUDGET_MS = builder
                .comment("Soft draw-list planning timing budget in milliseconds for the G5.x direct GL debug renderer.")
                .defineInRange("directGpuGeometryRendererPlanBudgetMs", 8.0D, 0.1D, 250.0D);
        ENABLE_DIRECT_GPU_GEOMETRY_AUTO_PLAN = builder
                .comment("Automatically refreshes the direct GL debug renderer draw list when the camera moves enough. Disabled by default.")
                .define("enableDirectGpuGeometryAutoPlan", false);
        DIRECT_GPU_GEOMETRY_AUTO_PLAN_COOLDOWN_TICKS = builder
                .comment("Ticks between automatic direct GL draw-list planning attempts.")
                .defineInRange("directGpuGeometryAutoPlanCooldownTicks", 40, 1, 400);
        DIRECT_GPU_GEOMETRY_AUTO_PLAN_MOVE_THRESHOLD_BLOCKS = builder
                .comment("Camera movement threshold in blocks before automatic direct GL draw-list planning may refresh.")
                .defineInRange("directGpuGeometryAutoPlanMoveThresholdBlocks", 32, 1, 1024);
        DIRECT_GPU_GEOMETRY_AUTO_PLAN_ONLY_WHEN_ENABLED = builder
                .comment("When true, automatic direct GL planning only runs while the direct renderer is enabled.")
                .define("directGpuGeometryAutoPlanOnlyWhenEnabled", true);
        DIRECT_GPU_GEOMETRY_AUTO_PLAN_ONLY_WHEN_ACTUAL_DRAW_ENABLED = builder
                .comment("When true, automatic direct GL planning only runs while actual direct drawing is enabled.")
                .define("directGpuGeometryAutoPlanOnlyWhenActualDrawEnabled", false);
        DIRECT_GPU_GEOMETRY_AUTO_PLAN_ON_DIMENSION_CHANGE = builder
                .comment("Requests a direct GL draw-list plan after dimension changes when auto planning is enabled.")
                .define("directGpuGeometryAutoPlanOnDimensionChange", true);
        DIRECT_GPU_GEOMETRY_AUTO_PLAN_MAX_CANDIDATES = builder
                .comment("Maximum uploaded sections considered by one automatic direct GL planning pass.")
                .defineInRange("directGpuGeometryAutoPlanMaxCandidates", 256, 1, 2048);
        DIRECT_GPU_GEOMETRY_AUTO_PLAN_MAX_SECTIONS = builder
                .comment("Maximum direct GL draw sections selected by one automatic planning pass.")
                .defineInRange("directGpuGeometryAutoPlanMaxSections", 16, 1, 64);
        DIRECT_GPU_GEOMETRY_AUTO_PLAN_MAX_RECORDS = builder
                .comment("Maximum packed quad records selected by one automatic direct GL planning pass.")
                .defineInRange("directGpuGeometryAutoPlanMaxRecords", 32768, 1, 131072);
        CPU_MESH_CACHE_MAX_ENTRIES = builder
                .comment("Maximum cached CPU mesh section/layer entries kept by the debug pipeline. Old entries are closed and evicted with LRU ordering.")
                .defineInRange("cpuMeshCacheMaxEntries", 2048, 1, 8192);
        BUILT_SECTION_CACHE_MAX_ENTRIES = builder
                .comment("Maximum CPU-only Voxy BuiltSection-format entries kept for renderer migration validation. Old entries are closed and evicted with LRU ordering.")
                .defineInRange("builtSectionCacheMaxEntries", 2048, 1, 8192);
        ENABLE_DEBUG_MESH_RENDERER = builder
                .comment("Draws cached CPU mesh sections with a temporary vanilla debug renderer. Requires enableWorldEngineSkeleton and does not use the final Voxy renderer.")
                .define("enableDebugMeshRenderer", false);
        DEBUG_MESH_RENDER_DISTANCE_CHUNKS = builder
                .comment("Chunk radius around the player used by the temporary debug mesh renderer.")
                .defineInRange("debugMeshRenderDistanceChunks", 2, 0, 8);
        DEBUG_MESH_MAX_RENDERED_ENTRIES = builder
                .comment("Maximum CPU mesh cache entries the temporary debug renderer may draw in one frame.")
                .defineInRange("debugMeshMaxRenderedEntries", 512, 1, 8192);
        DEBUG_MESH_WIREFRAME = builder
                .comment("Draws cached CPU mesh as wireframe lines instead of filled debug triangles.")
                .define("debugMeshWireframe", false);
        DEBUG_MESH_ALPHA = builder
                .comment("Alpha used by the temporary debug mesh renderer.")
                .defineInRange("debugMeshAlpha", 0.8D, 0.05D, 1.0D);
        DEBUG_MESH_IGNORE_DEPTH = builder
                .comment("Draws the temporary debug mesh through terrain. Useful when cached mesh exactly overlaps vanilla blocks.")
                .define("debugMeshIgnoreDepth", false);
        DEBUG_MESH_VERTICAL_OFFSET = builder
                .comment("Small upward offset applied only while drawing the temporary debug mesh to reduce z-fighting with vanilla terrain.")
                .defineInRange("debugMeshVerticalOffset", 0.05D, -2.0D, 2.0D);
        ENABLE_SIMPLE_GPU_MESH_RENDERER = builder
                .comment("Draws cached CPU mesh through a simple vanilla VertexBuffer renderer. This is an early Forge renderer PoC and is disabled by default.")
                .define("enableSimpleGpuMeshRenderer", false);
        SIMPLE_GPU_MESH_SOURCE = builder
                .comment("Selects the CPU data source for the simple GPU renderer. CPU_MESH is the existing path; BUILT_SECTION decodes the CPU-only Voxy BuiltSection cache; GL_HEAP_READBACK uses command-built debug mesh decoded from the upload-only GL heap.")
                .defineEnum("simpleGpuMeshSource", SimpleGpuMeshSource.CPU_MESH);
        SIMPLE_GPU_MESH_MAX_UPLOADS_PER_TICK = builder
                .comment("Maximum CPU mesh entries uploaded to vanilla VertexBuffer objects per client tick.")
                .defineInRange("simpleGpuMeshMaxUploadsPerTick", 1, 1, 16);
        SIMPLE_GPU_MESH_MAX_BUFFERS = builder
                .comment("Maximum cached vanilla VertexBuffer mesh entries. Old GPU buffers are closed and evicted with LRU ordering.")
                .defineInRange("simpleGpuMeshMaxBuffers", 2048, 1, 8192);
        SIMPLE_GPU_MESH_RENDER_DISTANCE_CHUNKS = builder
                .comment("Chunk radius around the player used by the simple vanilla GPU mesh renderer.")
                .defineInRange("simpleGpuMeshRenderDistanceChunks", 64, 0, 128);
        SIMPLE_GPU_MESH_MIN_RENDER_DISTANCE_CHUNKS = builder
                .comment("Minimum chunk distance before cached GPU mesh is rendered. This avoids drawing over nearby vanilla terrain.")
                .defineInRange("simpleGpuMeshMinRenderDistanceChunks", 4, 0, 64);
        SIMPLE_GPU_MESH_MAX_RENDERED_BUFFERS = builder
                .comment("Maximum vanilla VertexBuffer mesh entries the simple GPU renderer may draw in one frame.")
                .defineInRange("simpleGpuMeshMaxRenderedBuffers", 512, 1, 8192);
        SIMPLE_GPU_MESH_ALPHA = builder
                .comment("Global alpha multiplier used by the simple vanilla GPU mesh renderer.")
                .defineInRange("simpleGpuMeshAlpha", 1.0D, 0.05D, 1.0D);
        SIMPLE_GPU_MESH_USE_ORIGINAL_COLORS = builder
                .comment("Uses baked block/tint vertex colors for the simple GPU renderer. Disable to use bright layer debug colors.")
                .define("simpleGpuMeshUseOriginalColors", true);
        SIMPLE_GPU_MESH_RENDER_LOADED_CHUNKS = builder
                .comment("Allows the simple GPU renderer to draw chunks that are still loaded by the vanilla client. Disable to avoid overlaying nearby vanilla terrain.")
                .define("simpleGpuMeshRenderLoadedChunks", false);
        SIMPLE_GPU_MESH_LOADED_CHUNK_SKIP_MODE = builder
                .comment("Controls how the simple GPU renderer avoids drawing over vanilla terrain when simpleGpuMeshRenderLoadedChunks is false. BY_RENDER_DISTANCE skips only the Minecraft render-distance neighborhood, BY_LOADED_STATE uses ClientLevel.hasChunk, and DISABLED skips none.")
                .defineEnum("simpleGpuMeshLoadedChunkSkipMode", SimpleGpuMeshLoadedChunkSkipMode.BY_RENDER_DISTANCE);
        SIMPLE_GPU_MESH_LOADED_CHUNK_MARGIN = builder
                .comment("Extra chunk margin added to the Minecraft render distance when simpleGpuMeshLoadedChunkSkipMode is BY_RENDER_DISTANCE.")
                .defineInRange("simpleGpuMeshLoadedChunkMargin", 0, 0, 8);
        SIMPLE_GPU_MESH_KEEP_CACHED_CHUNKS = builder
                .comment("Keeps uploaded GPU mesh entries after the player leaves their upload window, until CPU/GPU cache limits or world lifecycle clear them.")
                .define("simpleGpuMeshKeepCachedChunks", true);
        builder.pop();
        CLIENT_SPEC = builder.build();
    }

    private ForgeVoxyConfig() {
    }
}
