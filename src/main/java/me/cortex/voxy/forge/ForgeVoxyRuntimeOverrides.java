package me.cortex.voxy.forge;

import me.cortex.voxy.config.ForgeVoxyConfig;

public final class ForgeVoxyRuntimeOverrides {
    private static final String SOURCE_CONFIG = "config";
    private static final String SOURCE_OVERRIDE = "runtime override";

    private static String presetName = "none";
    private static Boolean enableWorldEngineSkeleton;
    private static Boolean enableAutoChunkIngest;
    private static Boolean enableAutoCpuMeshBuild;
    private static Boolean enableSimpleGpuMeshRenderer;
    private static Boolean enableDebugMeshRenderer;
    private static Integer simpleGpuMeshMinRenderDistanceChunks;
    private static Integer simpleGpuMeshRenderDistanceChunks;
    private static Boolean simpleGpuMeshRenderLoadedChunks;
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
        enableSimpleGpuMeshRenderer = false;
        enableDebugMeshRenderer = false;
    }

    public static synchronized void applyOverlayPreset() {
        clearInternal();
        presetName = "overlay";
        enableWorldEngineSkeleton = true;
        enableAutoChunkIngest = true;
        enableAutoCpuMeshBuild = true;
        enableSimpleGpuMeshRenderer = true;
        enableDebugMeshRenderer = false;
        simpleGpuMeshMinRenderDistanceChunks = 0;
        simpleGpuMeshRenderDistanceChunks = 64;
        simpleGpuMeshRenderLoadedChunks = true;
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
        enableSimpleGpuMeshRenderer = true;
        enableDebugMeshRenderer = false;
        simpleGpuMeshMinRenderDistanceChunks = 5;
        simpleGpuMeshRenderDistanceChunks = 64;
        simpleGpuMeshRenderLoadedChunks = false;
        simpleGpuMeshKeepCachedChunks = true;
        simpleGpuMeshUseOriginalColors = true;
        simpleGpuMeshIgnoreDepth = false;
        simpleGpuMeshVerticalOffset = 0.0D;
        simpleGpuMeshAlpha = 1.0D;
    }

    public static synchronized void clear() {
        clearInternal();
    }

    private static void clearInternal() {
        presetName = "none";
        enableWorldEngineSkeleton = null;
        enableAutoChunkIngest = null;
        enableAutoCpuMeshBuild = null;
        enableSimpleGpuMeshRenderer = null;
        enableDebugMeshRenderer = null;
        simpleGpuMeshMinRenderDistanceChunks = null;
        simpleGpuMeshRenderDistanceChunks = null;
        simpleGpuMeshRenderLoadedChunks = null;
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
                enableSimpleGpuMeshRenderer(),
                source(enableSimpleGpuMeshRenderer),
                enableDebugMeshRenderer(),
                source(enableDebugMeshRenderer),
                simpleGpuMeshMinRenderDistanceChunks(),
                source(simpleGpuMeshMinRenderDistanceChunks),
                simpleGpuMeshRenderDistanceChunks(),
                source(simpleGpuMeshRenderDistanceChunks),
                simpleGpuMeshRenderLoadedChunks(),
                source(simpleGpuMeshRenderLoadedChunks),
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

    public static synchronized boolean enableSimpleGpuMeshRenderer() {
        return value(enableSimpleGpuMeshRenderer, ForgeVoxyConfig.ENABLE_SIMPLE_GPU_MESH_RENDERER.get());
    }

    public static synchronized boolean enableDebugMeshRenderer() {
        return value(enableDebugMeshRenderer, ForgeVoxyConfig.ENABLE_DEBUG_MESH_RENDERER.get());
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
                || enableSimpleGpuMeshRenderer != null
                || enableDebugMeshRenderer != null
                || simpleGpuMeshMinRenderDistanceChunks != null
                || simpleGpuMeshRenderDistanceChunks != null
                || simpleGpuMeshRenderLoadedChunks != null
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
            boolean enableSimpleGpuMeshRenderer,
            String enableSimpleGpuMeshRendererSource,
            boolean enableDebugMeshRenderer,
            String enableDebugMeshRendererSource,
            int simpleGpuMeshMinRenderDistanceChunks,
            String simpleGpuMeshMinRenderDistanceChunksSource,
            int simpleGpuMeshRenderDistanceChunks,
            String simpleGpuMeshRenderDistanceChunksSource,
            boolean simpleGpuMeshRenderLoadedChunks,
            String simpleGpuMeshRenderLoadedChunksSource,
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
