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
    public static final ForgeConfigSpec.BooleanValue ENABLE_DEBUG_MESH_RENDERER;
    public static final ForgeConfigSpec.IntValue DEBUG_MESH_RENDER_DISTANCE_CHUNKS;
    public static final ForgeConfigSpec.BooleanValue DEBUG_MESH_WIREFRAME;
    public static final ForgeConfigSpec.DoubleValue DEBUG_MESH_ALPHA;
    public static final ForgeConfigSpec.BooleanValue DEBUG_MESH_IGNORE_DEPTH;
    public static final ForgeConfigSpec.DoubleValue DEBUG_MESH_VERTICAL_OFFSET;

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
        ENABLE_DEBUG_MESH_RENDERER = builder
                .comment("Draws cached CPU mesh sections with a temporary vanilla debug renderer. Requires enableWorldEngineSkeleton and does not use the final Voxy renderer.")
                .define("enableDebugMeshRenderer", false);
        DEBUG_MESH_RENDER_DISTANCE_CHUNKS = builder
                .comment("Chunk radius around the player used by the temporary debug mesh renderer.")
                .defineInRange("debugMeshRenderDistanceChunks", 2, 0, 8);
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
        builder.pop();
        CLIENT_SPEC = builder.build();
    }

    private ForgeVoxyConfig() {
    }
}
