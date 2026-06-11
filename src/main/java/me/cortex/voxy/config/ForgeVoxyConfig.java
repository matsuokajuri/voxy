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
        CPU_MESH_CACHE_MAX_ENTRIES = builder
                .comment("Maximum cached CPU mesh section/layer entries kept by the debug pipeline. Old entries are closed and evicted with LRU ordering.")
                .defineInRange("cpuMeshCacheMaxEntries", 2048, 1, 8192);
        BUILT_SECTION_CACHE_MAX_ENTRIES = builder
                .comment("Maximum CPU-only Voxy BuiltSection-format entries kept for renderer migration validation. This cache is used only by manual debug commands.")
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
