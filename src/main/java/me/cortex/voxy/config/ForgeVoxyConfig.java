package me.cortex.voxy.config;

import net.minecraftforge.common.ForgeConfigSpec;

public final class ForgeVoxyConfig {
    public static final ForgeConfigSpec CLIENT_SPEC;
    public static final ForgeConfigSpec.BooleanValue ENABLED;
    public static final ForgeConfigSpec.IntValue ORIGINAL_VOXY_SERVICE_THREADS;
    public static final ForgeConfigSpec.BooleanValue ORIGINAL_VOXY_USE_EMBEDDIUM_BUILDER_THREADS;
    public static final ForgeConfigSpec.DoubleValue ORIGINAL_VOXY_SECTION_RENDER_DISTANCE;
    public static final ForgeConfigSpec.DoubleValue ORIGINAL_VOXY_SUBDIVISION_SIZE;
    public static final ForgeConfigSpec.BooleanValue ORIGINAL_VOXY_USE_ENVIRONMENTAL_FOG;
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
    public static final ForgeConfigSpec.BooleanValue ENABLE_MDIC_COMMAND_SKELETON;
    public static final ForgeConfigSpec.IntValue MDIC_COMMAND_MAX_SECTIONS;
    public static final ForgeConfigSpec.IntValue MDIC_COMMAND_MAX_RECORDS;
    public static final ForgeConfigSpec.BooleanValue MDIC_COMMAND_BUCKET_AWARE;
    public static final ForgeConfigSpec.BooleanValue MDIC_COMMAND_INCLUDE_TRANSLUCENT;
    public static final ForgeConfigSpec.BooleanValue MDIC_COMMAND_INCLUDE_DOUBLE_SIDED;
    public static final ForgeConfigSpec.BooleanValue MDIC_COMMAND_INCLUDE_DIRECTIONAL;
    public static final ForgeConfigSpec.BooleanValue MDIC_COMMAND_DIRECTIONAL_FACE_MASK;
    public static final ForgeConfigSpec.BooleanValue MDIC_COMMAND_DIRECTIONAL_FACE_MASK_FALLBACK_ALL_WHEN_INSIDE;
    public static final ForgeConfigSpec.BooleanValue MDIC_COMMAND_DIRECTIONAL_FACE_MASK_DEBUG_LOG;
    public static final ForgeConfigSpec.ConfigValue<String> MDIC_COMMAND_SELECTION_MODE;
    public static final ForgeConfigSpec.IntValue MDIC_COMMAND_RENDER_DISTANCE_CHUNKS;
    public static final ForgeConfigSpec.IntValue MDIC_COMMAND_MAX_PLAN_CANDIDATES;
    public static final ForgeConfigSpec.BooleanValue MDIC_COMMAND_USE_FRUSTUM;
    public static final ForgeConfigSpec.BooleanValue MDIC_COMMAND_FRUSTUM_FALLBACK_TO_RADIUS;
    public static final ForgeConfigSpec.IntValue MDIC_COMMAND_MAX_COMMANDS;
    public static final ForgeConfigSpec.IntValue MDIC_COMMAND_MAX_COMMANDS_PER_SECTION;
    public static final ForgeConfigSpec.BooleanValue MDIC_COMMAND_DEBUG_LOG;
    public static final ForgeConfigSpec.IntValue CPU_MESH_CACHE_MAX_ENTRIES;
    public static final ForgeConfigSpec.IntValue BUILT_SECTION_CACHE_MAX_ENTRIES;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        builder.push("general");
        ENABLED = builder
                .comment("Enables the Forge original-Voxy parity route.")
                .define("enabled", true);
        ORIGINAL_VOXY_SERVICE_THREADS = builder
                .comment("Original Voxy serviceThreads target. The effective dedicated Voxy pool subtracts Embeddium builder threads when originalVoxyUseEmbeddiumBuilderThreads is true.")
                .defineInRange("originalVoxyServiceThreads", defaultServiceThreads(), 1, maxServiceThreads());
        ORIGINAL_VOXY_USE_EMBEDDIUM_BUILDER_THREADS = builder
                .comment("Forge equivalent of original Voxy's Sodium-thread sharing option, using Embeddium builder workers when available.")
                .define("originalVoxyUseEmbeddiumBuilderThreads", true);
        ORIGINAL_VOXY_SECTION_RENDER_DISTANCE = builder
                .comment("Forge equivalent of original VoxyConfig.CONFIG.sectionRenderDistance, used by RenderDistanceTracker and hierarchical traversal.")
                .defineInRange("originalVoxySectionRenderDistance", 16.0D, 1.0D, 512.0D);
        ORIGINAL_VOXY_SUBDIVISION_SIZE = builder
                .comment("Forge equivalent of original VoxyConfig.CONFIG.subDivisionSize, used by hierarchical traversal screen-space descent.")
                .defineInRange("originalVoxySubDivisionSize", 64.0D, 1.0D, 512.0D);
        ORIGINAL_VOXY_USE_ENVIRONMENTAL_FOG = builder
                .comment("Forge equivalent of original VoxyConfig.CONFIG.useEnvironmentalFog for the original-shaped render pipeline final blit.")
                .define("originalVoxyUseEnvironmentalFog", true);

        ENABLE_WORLD_ENGINE_SKELETON = builder
                .comment("Creates the in-memory WorldEngine owner for the current Forge parity path.")
                .define("enableWorldEngineSkeleton", false);
        ENABLE_AUTO_CHUNK_INGEST = builder
                .comment("Automatically ingests already-loaded client chunks into the in-memory Voxy world when the owner is enabled.")
                .define("enableAutoChunkIngest", false);
        AUTO_INGEST_RADIUS = builder
                .comment("Chunk radius around the player to scan for already-loaded chunks when auto ingest is enabled.")
                .defineInRange("autoIngestRadius", 2, 0, 8);
        AUTO_INGEST_MAX_CHUNKS_PER_TICK = builder
                .comment("Maximum already-loaded chunks to ingest per client tick when auto ingest is enabled.")
                .defineInRange("autoIngestMaxChunksPerTick", 1, 1, 8);
        AUTO_INGEST_COOLDOWN_TICKS = builder
                .comment("Ticks between stationary nearby-chunk scans. A scan also happens immediately when the player crosses into a new chunk, so this only bounds stationary rediscovery latency.")
                .defineInRange("autoIngestCooldownTicks", 4, 0, 200);

        ENABLE_AUTO_CPU_MESH_BUILD = builder
                .comment("Automatically builds cached CPU mesh for already-ingested nearby chunks.")
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
                .comment("Automatically builds Voxy BuiltSection-format data for already-ingested nearby chunks.")
                .define("enableAutoBuiltSectionBuild", false);
        AUTO_BUILT_SECTION_BUILD_RADIUS = builder
                .comment("Chunk radius around the player to scan for already-ingested chunks when auto BuiltSection build is enabled.")
                .defineInRange("autoBuiltSectionBuildRadius", 2, 0, 8);
        AUTO_BUILT_SECTION_MAX_CHUNKS_PER_TICK = builder
                .comment("Maximum chunks to build into BuiltSection data per client tick when auto BuiltSection build is enabled.")
                .defineInRange("autoBuiltSectionMaxChunksPerTick", 1, 1, 8);
        AUTO_BUILT_SECTION_COOLDOWN_TICKS = builder
                .comment("Ticks between nearby chunk scans for auto BuiltSection build. Queued chunks may still be processed every tick.")
                .defineInRange("autoBuiltSectionCooldownTicks", 20, 0, 200);

        ENABLE_AUTO_GEOMETRY_MANAGER_CONSUME = builder
                .comment("Automatically consumes BuiltSection cache entries into the section geometry manager.")
                .define("enableAutoGeometryManagerConsume", false);
        AUTO_GEOMETRY_CONSUME_RADIUS = builder
                .comment("Chunk radius around the player to scan for BuiltSection cache entries when auto geometry-manager consume is enabled.")
                .defineInRange("autoGeometryConsumeRadius", 2, 0, 8);
        AUTO_GEOMETRY_CONSUME_MAX_SECTIONS_PER_TICK = builder
                .comment("Maximum BuiltSection entries to consume into the section geometry manager per client tick.")
                .defineInRange("autoGeometryConsumeMaxSectionsPerTick", 4, 1, 64);
        AUTO_GEOMETRY_CONSUME_COOLDOWN_TICKS = builder
                .comment("Ticks between nearby BuiltSection cache scans for auto geometry-manager consume. Queued sections may still be processed every tick.")
                .defineInRange("autoGeometryConsumeCooldownTicks", 20, 0, 200);

        ENABLE_GEOMETRY_GPU_UPLOAD = builder
                .comment("Uploads section geometry manager intents into the Forge GL geometry heap used by the current MDIC command generation bridge.")
                .define("enableGeometryGpuUpload", false);
        GEOMETRY_GPU_HEAP_BYTES = builder
                .comment("Byte capacity for the Forge GL geometry heap.")
                .defineInRange("geometryGpuHeapBytes", 16 * 1024 * 1024, 1024 * 1024, 256 * 1024 * 1024);
        GEOMETRY_GPU_METADATA_BYTES = builder
                .comment("Byte capacity for the Forge GL section metadata buffer.")
                .defineInRange("geometryGpuMetadataBytes", 8 * 1024 * 1024, 1024 * 1024, 64 * 1024 * 1024);
        GEOMETRY_GPU_MAX_UPLOADS_PER_TICK = builder
                .comment("Maximum section geometry upload intents copied into the Forge GL heap per client tick.")
                .defineInRange("geometryGpuMaxUploadsPerTick", 4, 1, 64);
        GEOMETRY_GPU_MAX_METADATA_WRITES_PER_TICK = builder
                .comment("Maximum dirty section metadata entries copied into the Forge GL metadata buffer per client tick.")
                .defineInRange("geometryGpuMaxMetadataWritesPerTick", 256, 1, 4096);
        GEOMETRY_GPU_DEBUG_LOG = builder
                .comment("Logs Forge GL geometry heap upload summaries.")
                .define("geometryGpuDebugLog", false);

        ENABLE_MDIC_COMMAND_SKELETON = builder
                .comment("Enables the Forge MDIC command generation bridge over the current geometry heap.")
                .define("enableMdicCommandSkeleton", false);
        MDIC_COMMAND_MAX_SECTIONS = builder
                .comment("Maximum uploaded sections selected by one MDIC command planning pass.")
                .defineInRange("mdicCommandMaxSections", 32, 1, 64);
        MDIC_COMMAND_MAX_RECORDS = builder
                .comment("Maximum packed geometry records referenced by one MDIC command list.")
                .defineInRange("mdicCommandMaxRecords", 65536, 1, 131072);
        MDIC_COMMAND_BUCKET_AWARE = builder
                .comment("Plans commands per non-empty Voxy geometry bucket.")
                .define("mdicCommandBucketAware", true);
        MDIC_COMMAND_INCLUDE_TRANSLUCENT = builder
                .comment("Includes bucket 0 translucent records in bucket-aware MDIC commands.")
                .define("mdicCommandIncludeTranslucent", false);
        MDIC_COMMAND_INCLUDE_DOUBLE_SIDED = builder
                .comment("Includes bucket 1 double-sided records in bucket-aware MDIC commands.")
                .define("mdicCommandIncludeDoubleSided", true);
        MDIC_COMMAND_INCLUDE_DIRECTIONAL = builder
                .comment("Includes directional buckets 2..7 in bucket-aware MDIC commands.")
                .define("mdicCommandIncludeDirectional", true);
        MDIC_COMMAND_DIRECTIONAL_FACE_MASK = builder
                .comment("Filters directional MDIC bucket commands by camera position relative to the uploaded section AABB.")
                .define("mdicCommandDirectionalFaceMask", true);
        MDIC_COMMAND_DIRECTIONAL_FACE_MASK_FALLBACK_ALL_WHEN_INSIDE = builder
                .comment("Keeps all directional buckets when the camera is inside a section AABB.")
                .define("mdicCommandDirectionalFaceMaskFallbackAllWhenInside", true);
        MDIC_COMMAND_DIRECTIONAL_FACE_MASK_DEBUG_LOG = builder
                .comment("Logs directional face-mask MDIC command planning summaries.")
                .define("mdicCommandDirectionalFaceMaskDebugLog", false);
        MDIC_COMMAND_SELECTION_MODE = builder
                .comment("MDIC command planning selection mode: AUTO, FRUSTUM_RADIUS, RADIUS, NEAREST_CAMERA, or FIRST_N.")
                .define("mdicCommandSelectionMode", "AUTO");
        MDIC_COMMAND_RENDER_DISTANCE_CHUNKS = builder
                .comment("Chunk radius used by MDIC command planning when RADIUS or FRUSTUM_RADIUS selection is active.")
                .defineInRange("mdicCommandRenderDistanceChunks", 16, 1, 128);
        MDIC_COMMAND_MAX_PLAN_CANDIDATES = builder
                .comment("Maximum uploaded section metadata entries inspected by one MDIC command planning pass.")
                .defineInRange("mdicCommandMaxPlanCandidates", 512, 1, 4096);
        MDIC_COMMAND_USE_FRUSTUM = builder
                .comment("Attempts to use the cached frustum for MDIC command planning.")
                .define("mdicCommandUseFrustum", true);
        MDIC_COMMAND_FRUSTUM_FALLBACK_TO_RADIUS = builder
                .comment("Falls back from FRUSTUM_RADIUS/AUTO to radius selection when no reliable frustum snapshot is available.")
                .define("mdicCommandFrustumFallbackToRadius", true);
        MDIC_COMMAND_MAX_COMMANDS = builder
                .comment("Maximum bucket-aware MDIC commands generated by one planning pass.")
                .defineInRange("mdicCommandMaxCommands", 256, 1, 512);
        MDIC_COMMAND_MAX_COMMANDS_PER_SECTION = builder
                .comment("Maximum bucket commands generated for a single uploaded section.")
                .defineInRange("mdicCommandMaxCommandsPerSection", 8, 1, 8);
        MDIC_COMMAND_DEBUG_LOG = builder
                .comment("Logs MDIC command planning/upload summaries.")
                .define("mdicCommandDebugLog", false);

        CPU_MESH_CACHE_MAX_ENTRIES = builder
                .comment("Maximum cached CPU mesh section/layer entries. Old entries are closed and evicted with LRU ordering.")
                .defineInRange("cpuMeshCacheMaxEntries", 2048, 1, 8192);
        BUILT_SECTION_CACHE_MAX_ENTRIES = builder
                .comment("Maximum cached Voxy BuiltSection-format entries. Old entries are closed and evicted with LRU ordering.")
                .defineInRange("builtSectionCacheMaxEntries", 2048, 1, 8192);
        builder.pop();
        CLIENT_SPEC = builder.build();
    }

    private ForgeVoxyConfig() {
    }

    public static boolean isEnabledEarlySafe() {
        try {
            return ENABLED.get();
        } catch (IllegalStateException ignored) {
            return true;
        }
    }

    private static int defaultServiceThreads() {
        return Math.max((int) (ForgeOriginalVoxyCpuLayout.getCoreCount() / 1.5D), 1);
    }

    private static int maxServiceThreads() {
        return Math.max(ForgeOriginalVoxyCpuLayout.getCoreCount(), 1);
    }
}
