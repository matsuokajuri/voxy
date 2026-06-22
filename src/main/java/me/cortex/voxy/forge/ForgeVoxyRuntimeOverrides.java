package me.cortex.voxy.forge;

import me.cortex.voxy.config.ForgeVoxyConfig;

public final class ForgeVoxyRuntimeOverrides {
    private ForgeVoxyRuntimeOverrides() {
    }

    public static synchronized void clear() {
    }

    public static synchronized boolean enabledWorldEngineSkeleton() {
        return ForgeVoxyConfig.ENABLE_WORLD_ENGINE_SKELETON.get();
    }

    public static synchronized boolean enableAutoChunkIngest() {
        return ForgeVoxyConfig.ENABLE_AUTO_CHUNK_INGEST.get();
    }

    public static synchronized boolean enableAutoCpuMeshBuild() {
        return ForgeVoxyConfig.ENABLE_AUTO_CPU_MESH_BUILD.get();
    }

    public static synchronized boolean enableAutoBuiltSectionBuild() {
        return ForgeVoxyConfig.ENABLE_AUTO_BUILT_SECTION_BUILD.get();
    }

    public static synchronized boolean enableAutoGeometryManagerConsume() {
        return ForgeVoxyConfig.ENABLE_AUTO_GEOMETRY_MANAGER_CONSUME.get();
    }

    public static synchronized boolean enableGeometryGpuUpload() {
        return ForgeVoxyConfig.ENABLE_GEOMETRY_GPU_UPLOAD.get();
    }

    public static synchronized boolean enableMdicCommandSkeleton() {
        return ForgeVoxyConfig.ENABLE_MDIC_COMMAND_SKELETON.get();
    }

    public static synchronized int mdicCommandMaxSections() {
        return Math.min(64, Math.max(1, ForgeVoxyConfig.MDIC_COMMAND_MAX_SECTIONS.get()));
    }

    public static synchronized int mdicCommandMaxRecords() {
        return Math.min(131072, Math.max(1, ForgeVoxyConfig.MDIC_COMMAND_MAX_RECORDS.get()));
    }

    public static synchronized boolean mdicCommandBucketAware() {
        return ForgeVoxyConfig.MDIC_COMMAND_BUCKET_AWARE.get();
    }

    public static synchronized boolean mdicCommandIncludeTranslucent() {
        return ForgeVoxyConfig.MDIC_COMMAND_INCLUDE_TRANSLUCENT.get();
    }

    public static synchronized boolean mdicCommandIncludeDoubleSided() {
        return ForgeVoxyConfig.MDIC_COMMAND_INCLUDE_DOUBLE_SIDED.get();
    }

    public static synchronized boolean mdicCommandIncludeDirectional() {
        return ForgeVoxyConfig.MDIC_COMMAND_INCLUDE_DIRECTIONAL.get();
    }

    public static synchronized boolean mdicCommandDirectionalFaceMask() {
        return ForgeVoxyConfig.MDIC_COMMAND_DIRECTIONAL_FACE_MASK.get();
    }

    public static synchronized boolean mdicCommandDirectionalFaceMaskFallbackAllWhenInside() {
        return ForgeVoxyConfig.MDIC_COMMAND_DIRECTIONAL_FACE_MASK_FALLBACK_ALL_WHEN_INSIDE.get();
    }

    public static synchronized boolean mdicCommandDirectionalFaceMaskDebugLog() {
        return ForgeVoxyConfig.MDIC_COMMAND_DIRECTIONAL_FACE_MASK_DEBUG_LOG.get();
    }

    public static synchronized ForgeMdicCommandSelectionMode mdicCommandSelectionMode() {
        return ForgeMdicCommandSelectionMode.parse(ForgeVoxyConfig.MDIC_COMMAND_SELECTION_MODE.get());
    }

    public static synchronized int mdicCommandRenderDistanceChunks() {
        return Math.min(128, Math.max(1, ForgeVoxyConfig.MDIC_COMMAND_RENDER_DISTANCE_CHUNKS.get()));
    }

    public static synchronized int mdicCommandMaxPlanCandidates() {
        return Math.min(4096, Math.max(1, ForgeVoxyConfig.MDIC_COMMAND_MAX_PLAN_CANDIDATES.get()));
    }

    public static synchronized boolean mdicCommandUseFrustum() {
        return ForgeVoxyConfig.MDIC_COMMAND_USE_FRUSTUM.get();
    }

    public static synchronized boolean mdicCommandFrustumFallbackToRadius() {
        return ForgeVoxyConfig.MDIC_COMMAND_FRUSTUM_FALLBACK_TO_RADIUS.get();
    }

    public static synchronized int mdicCommandMaxCommands() {
        return Math.min(512, Math.max(1, ForgeVoxyConfig.MDIC_COMMAND_MAX_COMMANDS.get()));
    }

    public static synchronized int mdicCommandMaxCommandsPerSection() {
        return Math.min(8, Math.max(1, ForgeVoxyConfig.MDIC_COMMAND_MAX_COMMANDS_PER_SECTION.get()));
    }

    public static synchronized boolean mdicCommandDebugLog() {
        return ForgeVoxyConfig.MDIC_COMMAND_DEBUG_LOG.get();
    }
}
