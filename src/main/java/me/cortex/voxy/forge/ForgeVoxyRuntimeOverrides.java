package me.cortex.voxy.forge;

import me.cortex.voxy.config.ForgeVoxyConfig;
import me.cortex.voxy.config.SimpleGpuMeshLoadedChunkSkipMode;
import me.cortex.voxy.config.SimpleGpuMeshSource;

public final class ForgeVoxyRuntimeOverrides {
    private static final String SOURCE_CONFIG = "config";
    private static final String SOURCE_OVERRIDE = "runtime override";

    private static String presetName = "none";
    private static Boolean enableWorldEngineSkeleton;
    private static Boolean enableAutoChunkIngest;
    private static Boolean enableAutoCpuMeshBuild;
    private static Boolean enableAutoBuiltSectionBuild;
    private static Boolean enableAutoGeometryManagerConsume;
    private static Boolean enableSimpleGpuMeshRenderer;
    private static Boolean enableDebugMeshRenderer;
    private static SimpleGpuMeshSource simpleGpuMeshSource;
    private static Integer simpleGpuMeshMinRenderDistanceChunks;
    private static Integer simpleGpuMeshRenderDistanceChunks;
    private static Boolean simpleGpuMeshRenderLoadedChunks;
    private static SimpleGpuMeshLoadedChunkSkipMode simpleGpuMeshLoadedChunkSkipMode;
    private static Integer simpleGpuMeshLoadedChunkMargin;
    private static Boolean simpleGpuMeshKeepCachedChunks;
    private static Boolean simpleGpuMeshUseOriginalColors;
    private static Boolean simpleGpuMeshIgnoreDepth;
    private static Double simpleGpuMeshVerticalOffset;
    private static Double simpleGpuMeshAlpha;
    private static Double debugMeshAlpha;

    private ForgeVoxyRuntimeOverrides() {
    }

    public static synchronized void applyOffPreset() {
        clearInternal();
        presetName = "off";
        enableWorldEngineSkeleton = false;
        enableAutoChunkIngest = false;
        enableAutoCpuMeshBuild = false;
        enableAutoBuiltSectionBuild = false;
        enableAutoGeometryManagerConsume = false;
        enableSimpleGpuMeshRenderer = false;
        enableDebugMeshRenderer = false;
    }

    public static synchronized void applyOverlayPreset() {
        clearInternal();
        presetName = "overlay";
        enableWorldEngineSkeleton = true;
        enableAutoChunkIngest = true;
        enableAutoCpuMeshBuild = true;
        enableAutoBuiltSectionBuild = false;
        enableSimpleGpuMeshRenderer = true;
        enableDebugMeshRenderer = false;
        simpleGpuMeshMinRenderDistanceChunks = 0;
        simpleGpuMeshRenderDistanceChunks = 64;
        simpleGpuMeshRenderLoadedChunks = true;
        simpleGpuMeshLoadedChunkSkipMode = SimpleGpuMeshLoadedChunkSkipMode.DISABLED;
        simpleGpuMeshLoadedChunkMargin = 0;
        simpleGpuMeshKeepCachedChunks = true;
        simpleGpuMeshUseOriginalColors = false;
        simpleGpuMeshIgnoreDepth = true;
        simpleGpuMeshVerticalOffset = 0.05D;
        simpleGpuMeshAlpha = 1.0D;
    }

    public static synchronized void applyLodPreset() {
        clearInternal();
        presetName = "lod";
        enableWorldEngineSkeleton = true;
        enableAutoChunkIngest = true;
        enableAutoCpuMeshBuild = true;
        enableAutoBuiltSectionBuild = false;
        enableSimpleGpuMeshRenderer = true;
        enableDebugMeshRenderer = false;
        simpleGpuMeshMinRenderDistanceChunks = 5;
        simpleGpuMeshRenderDistanceChunks = 64;
        simpleGpuMeshRenderLoadedChunks = false;
        simpleGpuMeshLoadedChunkSkipMode = SimpleGpuMeshLoadedChunkSkipMode.BY_RENDER_DISTANCE;
        simpleGpuMeshLoadedChunkMargin = 0;
        simpleGpuMeshKeepCachedChunks = true;
        simpleGpuMeshUseOriginalColors = true;
        simpleGpuMeshIgnoreDepth = false;
        simpleGpuMeshVerticalOffset = 0.0D;
        simpleGpuMeshAlpha = 1.0D;
    }

    public static synchronized void applyLodBuiltSectionPreset() {
        clearInternal();
        presetName = "lod_built_section";
        enableWorldEngineSkeleton = true;
        enableAutoChunkIngest = true;
        enableAutoCpuMeshBuild = false;
        enableAutoBuiltSectionBuild = true;
        enableAutoGeometryManagerConsume = false;
        enableSimpleGpuMeshRenderer = true;
        enableDebugMeshRenderer = false;
        simpleGpuMeshSource = SimpleGpuMeshSource.BUILT_SECTION;
        simpleGpuMeshMinRenderDistanceChunks = 5;
        simpleGpuMeshRenderDistanceChunks = 64;
        simpleGpuMeshRenderLoadedChunks = false;
        simpleGpuMeshLoadedChunkSkipMode = SimpleGpuMeshLoadedChunkSkipMode.BY_RENDER_DISTANCE;
        simpleGpuMeshLoadedChunkMargin = 0;
        simpleGpuMeshKeepCachedChunks = true;
        simpleGpuMeshUseOriginalColors = true;
        simpleGpuMeshIgnoreDepth = false;
        simpleGpuMeshVerticalOffset = 0.0D;
        simpleGpuMeshAlpha = 1.0D;
    }

    public static synchronized void applyGeometryManagerPreset() {
        presetName = "geometry_manager";
        enableWorldEngineSkeleton = true;
        enableAutoChunkIngest = true;
        enableAutoCpuMeshBuild = false;
        enableAutoBuiltSectionBuild = true;
        enableAutoGeometryManagerConsume = true;
    }

    public static synchronized void clear() {
        clearInternal();
    }

    private static void clearInternal() {
        presetName = "none";
        enableWorldEngineSkeleton = null;
        enableAutoChunkIngest = null;
        enableAutoCpuMeshBuild = null;
        enableAutoBuiltSectionBuild = null;
        enableAutoGeometryManagerConsume = null;
        enableSimpleGpuMeshRenderer = null;
        enableDebugMeshRenderer = null;
        simpleGpuMeshSource = null;
        simpleGpuMeshMinRenderDistanceChunks = null;
        simpleGpuMeshRenderDistanceChunks = null;
        simpleGpuMeshRenderLoadedChunks = null;
        simpleGpuMeshLoadedChunkSkipMode = null;
        simpleGpuMeshLoadedChunkMargin = null;
        simpleGpuMeshKeepCachedChunks = null;
        simpleGpuMeshUseOriginalColors = null;
        simpleGpuMeshIgnoreDepth = null;
        simpleGpuMeshVerticalOffset = null;
        simpleGpuMeshAlpha = null;
        debugMeshAlpha = null;
    }

    public static synchronized StatusSnapshot createStatusSnapshot() {
        return new StatusSnapshot(
                presetName,
                hasOverridesLocked(),
                enabledWorldEngineSkeleton(),
                source(enableWorldEngineSkeleton),
                enableAutoChunkIngest(),
                source(enableAutoChunkIngest),
                enableAutoCpuMeshBuild(),
                source(enableAutoCpuMeshBuild),
                enableAutoBuiltSectionBuild(),
                source(enableAutoBuiltSectionBuild),
                enableAutoGeometryManagerConsume(),
                source(enableAutoGeometryManagerConsume),
                enableSimpleGpuMeshRenderer(),
                source(enableSimpleGpuMeshRenderer),
                enableDebugMeshRenderer(),
                source(enableDebugMeshRenderer),
                simpleGpuMeshSource(),
                source(simpleGpuMeshSource),
                simpleGpuMeshMinRenderDistanceChunks(),
                source(simpleGpuMeshMinRenderDistanceChunks),
                simpleGpuMeshRenderDistanceChunks(),
                source(simpleGpuMeshRenderDistanceChunks),
                simpleGpuMeshRenderLoadedChunks(),
                source(simpleGpuMeshRenderLoadedChunks),
                simpleGpuMeshLoadedChunkSkipMode(),
                source(simpleGpuMeshLoadedChunkSkipMode),
                simpleGpuMeshLoadedChunkMargin(),
                source(simpleGpuMeshLoadedChunkMargin),
                simpleGpuMeshKeepCachedChunks(),
                source(simpleGpuMeshKeepCachedChunks),
                simpleGpuMeshUseOriginalColors(),
                source(simpleGpuMeshUseOriginalColors),
                simpleGpuMeshIgnoreDepth(),
                source(simpleGpuMeshIgnoreDepth),
                simpleGpuMeshVerticalOffset(),
                source(simpleGpuMeshVerticalOffset),
                simpleGpuMeshAlpha(),
                source(simpleGpuMeshAlpha),
                debugMeshAlpha(),
                source(debugMeshAlpha)
        );
    }

    public static synchronized String presetName() {
        return presetName;
    }

    public static synchronized boolean hasOverrides() {
        return hasOverridesLocked();
    }

    public static synchronized boolean enabledWorldEngineSkeleton() {
        return value(enableWorldEngineSkeleton, ForgeVoxyConfig.ENABLE_WORLD_ENGINE_SKELETON.get());
    }

    public static synchronized boolean enableAutoChunkIngest() {
        return value(enableAutoChunkIngest, ForgeVoxyConfig.ENABLE_AUTO_CHUNK_INGEST.get());
    }

    public static synchronized boolean enableAutoCpuMeshBuild() {
        return value(enableAutoCpuMeshBuild, ForgeVoxyConfig.ENABLE_AUTO_CPU_MESH_BUILD.get());
    }

    public static synchronized boolean enableAutoBuiltSectionBuild() {
        return value(enableAutoBuiltSectionBuild, ForgeVoxyConfig.ENABLE_AUTO_BUILT_SECTION_BUILD.get());
    }

    public static synchronized boolean enableAutoGeometryManagerConsume() {
        return value(enableAutoGeometryManagerConsume, ForgeVoxyConfig.ENABLE_AUTO_GEOMETRY_MANAGER_CONSUME.get());
    }

    public static synchronized boolean enableSimpleGpuMeshRenderer() {
        return value(enableSimpleGpuMeshRenderer, ForgeVoxyConfig.ENABLE_SIMPLE_GPU_MESH_RENDERER.get());
    }

    public static synchronized boolean enableDebugMeshRenderer() {
        return value(enableDebugMeshRenderer, ForgeVoxyConfig.ENABLE_DEBUG_MESH_RENDERER.get());
    }

    public static synchronized SimpleGpuMeshSource simpleGpuMeshSource() {
        return value(simpleGpuMeshSource, ForgeVoxyConfig.SIMPLE_GPU_MESH_SOURCE.get());
    }

    public static synchronized void setSimpleGpuMeshSource(SimpleGpuMeshSource source) {
        presetName = "custom";
        simpleGpuMeshSource = source;
    }

    public static synchronized int simpleGpuMeshMinRenderDistanceChunks() {
        return Math.min(64, Math.max(0, value(simpleGpuMeshMinRenderDistanceChunks, ForgeVoxyConfig.SIMPLE_GPU_MESH_MIN_RENDER_DISTANCE_CHUNKS.get())));
    }

    public static synchronized int simpleGpuMeshRenderDistanceChunks() {
        return Math.min(128, Math.max(0, value(simpleGpuMeshRenderDistanceChunks, ForgeVoxyConfig.SIMPLE_GPU_MESH_RENDER_DISTANCE_CHUNKS.get())));
    }

    public static synchronized boolean simpleGpuMeshRenderLoadedChunks() {
        return value(simpleGpuMeshRenderLoadedChunks, ForgeVoxyConfig.SIMPLE_GPU_MESH_RENDER_LOADED_CHUNKS.get());
    }

    public static synchronized SimpleGpuMeshLoadedChunkSkipMode simpleGpuMeshLoadedChunkSkipMode() {
        return value(simpleGpuMeshLoadedChunkSkipMode, ForgeVoxyConfig.SIMPLE_GPU_MESH_LOADED_CHUNK_SKIP_MODE.get());
    }

    public static synchronized int simpleGpuMeshLoadedChunkMargin() {
        return Math.min(8, Math.max(0, value(simpleGpuMeshLoadedChunkMargin, ForgeVoxyConfig.SIMPLE_GPU_MESH_LOADED_CHUNK_MARGIN.get())));
    }

    public static synchronized boolean simpleGpuMeshKeepCachedChunks() {
        return value(simpleGpuMeshKeepCachedChunks, ForgeVoxyConfig.SIMPLE_GPU_MESH_KEEP_CACHED_CHUNKS.get());
    }

    public static synchronized boolean simpleGpuMeshUseOriginalColors() {
        return value(simpleGpuMeshUseOriginalColors, ForgeVoxyConfig.SIMPLE_GPU_MESH_USE_ORIGINAL_COLORS.get());
    }

    public static synchronized boolean simpleGpuMeshIgnoreDepth() {
        return value(simpleGpuMeshIgnoreDepth, false);
    }

    public static synchronized double simpleGpuMeshVerticalOffset() {
        return Math.max(-2.0D, Math.min(2.0D, value(simpleGpuMeshVerticalOffset, 0.0D)));
    }

    public static synchronized double simpleGpuMeshAlpha() {
        return Math.max(0.05D, Math.min(1.0D, value(simpleGpuMeshAlpha, ForgeVoxyConfig.SIMPLE_GPU_MESH_ALPHA.get())));
    }

    public static synchronized double debugMeshAlpha() {
        return Math.max(0.05D, Math.min(1.0D, value(debugMeshAlpha, ForgeVoxyConfig.DEBUG_MESH_ALPHA.get())));
    }

    private static boolean hasOverridesLocked() {
        return enableWorldEngineSkeleton != null
                || enableAutoChunkIngest != null
                || enableAutoCpuMeshBuild != null
                || enableAutoBuiltSectionBuild != null
                || enableAutoGeometryManagerConsume != null
                || enableSimpleGpuMeshRenderer != null
                || enableDebugMeshRenderer != null
                || simpleGpuMeshSource != null
                || simpleGpuMeshMinRenderDistanceChunks != null
                || simpleGpuMeshRenderDistanceChunks != null
                || simpleGpuMeshRenderLoadedChunks != null
                || simpleGpuMeshLoadedChunkSkipMode != null
                || simpleGpuMeshLoadedChunkMargin != null
                || simpleGpuMeshKeepCachedChunks != null
                || simpleGpuMeshUseOriginalColors != null
                || simpleGpuMeshIgnoreDepth != null
                || simpleGpuMeshVerticalOffset != null
                || simpleGpuMeshAlpha != null
                || debugMeshAlpha != null;
    }

    private static boolean value(Boolean override, boolean configValue) {
        return override != null ? override : configValue;
    }

    private static int value(Integer override, int configValue) {
        return override != null ? override : configValue;
    }

    private static double value(Double override, double configValue) {
        return override != null ? override : configValue;
    }

    private static <T> T value(T override, T configValue) {
        return override != null ? override : configValue;
    }

    private static String source(Object override) {
        return override == null ? SOURCE_CONFIG : SOURCE_OVERRIDE;
    }

    public record StatusSnapshot(
            String presetName,
            boolean hasOverrides,
            boolean enableWorldEngineSkeleton,
            String enableWorldEngineSkeletonSource,
            boolean enableAutoChunkIngest,
            String enableAutoChunkIngestSource,
            boolean enableAutoCpuMeshBuild,
            String enableAutoCpuMeshBuildSource,
            boolean enableAutoBuiltSectionBuild,
            String enableAutoBuiltSectionBuildSource,
            boolean enableAutoGeometryManagerConsume,
            String enableAutoGeometryManagerConsumeSource,
            boolean enableSimpleGpuMeshRenderer,
            String enableSimpleGpuMeshRendererSource,
            boolean enableDebugMeshRenderer,
            String enableDebugMeshRendererSource,
            SimpleGpuMeshSource simpleGpuMeshSource,
            String simpleGpuMeshSourceSource,
            int simpleGpuMeshMinRenderDistanceChunks,
            String simpleGpuMeshMinRenderDistanceChunksSource,
            int simpleGpuMeshRenderDistanceChunks,
            String simpleGpuMeshRenderDistanceChunksSource,
            boolean simpleGpuMeshRenderLoadedChunks,
            String simpleGpuMeshRenderLoadedChunksSource,
            SimpleGpuMeshLoadedChunkSkipMode simpleGpuMeshLoadedChunkSkipMode,
            String simpleGpuMeshLoadedChunkSkipModeSource,
            int simpleGpuMeshLoadedChunkMargin,
            String simpleGpuMeshLoadedChunkMarginSource,
            boolean simpleGpuMeshKeepCachedChunks,
            String simpleGpuMeshKeepCachedChunksSource,
            boolean simpleGpuMeshUseOriginalColors,
            String simpleGpuMeshUseOriginalColorsSource,
            boolean simpleGpuMeshIgnoreDepth,
            String simpleGpuMeshIgnoreDepthSource,
            double simpleGpuMeshVerticalOffset,
            String simpleGpuMeshVerticalOffsetSource,
            double simpleGpuMeshAlpha,
            String simpleGpuMeshAlphaSource,
            double debugMeshAlpha,
            String debugMeshAlphaSource
    ) {
    }
}
