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
    private static Boolean enableGeometryGpuUpload;
    private static Boolean enableGeometryGpuVisualization;
    private static Boolean enableGeometryGpuReadbackMeshAutoRefresh;
    private static Boolean enableDirectGpuGeometryRenderer;
    private static Boolean enableDirectGpuGeometryAutoPlan;
    private static Boolean enableMdicCommandSkeleton;
    private static Boolean enableMdicDebugDraw;
    private static Boolean mdicDebugDrawActualDraw;
    private static Integer mdicCommandMaxSections;
    private static Integer mdicCommandMaxRecords;
    private static Boolean mdicCommandBucketAware;
    private static Boolean mdicCommandIncludeTranslucent;
    private static Boolean mdicCommandIncludeDoubleSided;
    private static Boolean mdicCommandIncludeDirectional;
    private static Boolean mdicCommandDirectionalFaceMask;
    private static Boolean mdicCommandDirectionalFaceMaskFallbackAllWhenInside;
    private static String mdicCommandSelectionMode;
    private static Integer mdicCommandRenderDistanceChunks;
    private static Integer mdicCommandMaxPlanCandidates;
    private static Boolean mdicCommandUseFrustum;
    private static Boolean mdicCommandFrustumFallbackToRadius;
    private static Integer mdicCommandMaxCommands;
    private static Integer mdicCommandMaxCommandsPerSection;
    private static Integer mdicDebugDrawMaxCommands;
    private static Integer mdicDebugDrawMaxRecords;
    private static Integer mdicDebugDrawMaxIndexedRecords;
    private static Integer mdicDebugDrawMaxDrawCount;
    private static Boolean directGpuGeometryRendererActualDraw;
    private static Integer directGpuGeometryRendererMaxPlanCandidates;
    private static Integer directGpuGeometryRendererMaxDrawSections;
    private static Integer directGpuGeometryRendererMaxDrawRecords;
    private static Integer directGpuGeometryRendererMaxRecordsPerSection;
    private static Integer directGpuGeometryRendererRenderDistanceChunks;
    private static Double geometryGpuVisualizationAlpha;
    private static Boolean geometryGpuVisualizationIgnoreDepth;
    private static Boolean geometryGpuVisualizationDoubleSided;
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
        enableGeometryGpuUpload = false;
        enableGeometryGpuVisualization = false;
        enableGeometryGpuReadbackMeshAutoRefresh = false;
        enableDirectGpuGeometryRenderer = false;
        enableDirectGpuGeometryAutoPlan = false;
        enableMdicCommandSkeleton = false;
        enableMdicDebugDraw = false;
        mdicDebugDrawActualDraw = false;
        directGpuGeometryRendererActualDraw = false;
        directGpuGeometryRendererMaxPlanCandidates = null;
        directGpuGeometryRendererMaxDrawSections = null;
        directGpuGeometryRendererMaxDrawRecords = null;
        directGpuGeometryRendererMaxRecordsPerSection = null;
        directGpuGeometryRendererRenderDistanceChunks = null;
        enableSimpleGpuMeshRenderer = false;
        enableDebugMeshRenderer = false;
    }

    public static synchronized void applyFormalRendererSkeletonPreset() {
        clearInternal();
        presetName = "formal_renderer_skeleton";
        enableWorldEngineSkeleton = true;
        enableAutoChunkIngest = false;
        enableAutoCpuMeshBuild = false;
        enableAutoBuiltSectionBuild = false;
        enableAutoGeometryManagerConsume = false;
        enableGeometryGpuUpload = false;
        enableGeometryGpuVisualization = false;
        enableGeometryGpuReadbackMeshAutoRefresh = false;
        enableDirectGpuGeometryRenderer = false;
        enableDirectGpuGeometryAutoPlan = false;
        enableMdicCommandSkeleton = false;
        enableMdicDebugDraw = false;
        mdicDebugDrawActualDraw = false;
        directGpuGeometryRendererActualDraw = false;
        enableSimpleGpuMeshRenderer = false;
        enableDebugMeshRenderer = false;
    }

    public static synchronized void applyFormalModelStoreSkeletonPreset() {
        clearInternal();
        presetName = "formal_model_store_skeleton";
        enableWorldEngineSkeleton = true;
        enableAutoChunkIngest = false;
        enableAutoCpuMeshBuild = false;
        enableAutoBuiltSectionBuild = false;
        enableAutoGeometryManagerConsume = false;
        enableGeometryGpuUpload = false;
        enableGeometryGpuVisualization = false;
        enableGeometryGpuReadbackMeshAutoRefresh = false;
        enableDirectGpuGeometryRenderer = false;
        enableDirectGpuGeometryAutoPlan = false;
        enableMdicCommandSkeleton = false;
        enableMdicDebugDraw = false;
        mdicDebugDrawActualDraw = false;
        directGpuGeometryRendererActualDraw = false;
        enableSimpleGpuMeshRenderer = false;
        enableDebugMeshRenderer = false;
    }

    public static synchronized void applyFormalModelFactorySkeletonPreset() {
        clearInternal();
        presetName = "formal_model_factory_skeleton";
        enableWorldEngineSkeleton = true;
        enableAutoChunkIngest = false;
        enableAutoCpuMeshBuild = false;
        enableAutoBuiltSectionBuild = false;
        enableAutoGeometryManagerConsume = false;
        enableGeometryGpuUpload = false;
        enableGeometryGpuVisualization = false;
        enableGeometryGpuReadbackMeshAutoRefresh = false;
        enableDirectGpuGeometryRenderer = false;
        enableDirectGpuGeometryAutoPlan = false;
        enableMdicCommandSkeleton = false;
        enableMdicDebugDraw = false;
        mdicDebugDrawActualDraw = false;
        directGpuGeometryRendererActualDraw = false;
        enableSimpleGpuMeshRenderer = false;
        enableDebugMeshRenderer = false;
    }

    public static synchronized void applyFormalModelLifecycleRebuildPreset() {
        clearInternal();
        presetName = "formal_model_lifecycle_rebuild";
        enableWorldEngineSkeleton = true;
        enableAutoChunkIngest = false;
        enableAutoCpuMeshBuild = false;
        enableAutoBuiltSectionBuild = false;
        enableAutoGeometryManagerConsume = false;
        enableGeometryGpuUpload = false;
        enableGeometryGpuVisualization = false;
        enableGeometryGpuReadbackMeshAutoRefresh = false;
        enableDirectGpuGeometryRenderer = false;
        enableDirectGpuGeometryAutoPlan = false;
        enableMdicCommandSkeleton = false;
        enableMdicDebugDraw = false;
        mdicDebugDrawActualDraw = false;
        directGpuGeometryRendererActualDraw = false;
        enableSimpleGpuMeshRenderer = false;
        enableDebugMeshRenderer = false;
    }

    public static synchronized void applyFormalShaderInputSkeletonPreset() {
        clearInternal();
        presetName = "formal_shader_input_skeleton";
        enableWorldEngineSkeleton = true;
        enableAutoChunkIngest = false;
        enableAutoCpuMeshBuild = false;
        enableAutoBuiltSectionBuild = false;
        enableAutoGeometryManagerConsume = false;
        enableGeometryGpuUpload = false;
        enableGeometryGpuVisualization = false;
        enableGeometryGpuReadbackMeshAutoRefresh = false;
        enableDirectGpuGeometryRenderer = false;
        enableDirectGpuGeometryAutoPlan = false;
        enableMdicCommandSkeleton = false;
        enableMdicDebugDraw = false;
        mdicDebugDrawActualDraw = false;
        directGpuGeometryRendererActualDraw = false;
        enableSimpleGpuMeshRenderer = false;
        enableDebugMeshRenderer = false;
    }

    public static synchronized void applyFormalShaderProgramValidationPreset() {
        clearInternal();
        presetName = "formal_shader_program_validation";
        enableWorldEngineSkeleton = true;
        enableAutoChunkIngest = false;
        enableAutoCpuMeshBuild = false;
        enableAutoBuiltSectionBuild = false;
        enableAutoGeometryManagerConsume = false;
        enableGeometryGpuUpload = false;
        enableGeometryGpuVisualization = false;
        enableGeometryGpuReadbackMeshAutoRefresh = false;
        enableDirectGpuGeometryRenderer = false;
        enableDirectGpuGeometryAutoPlan = false;
        enableMdicCommandSkeleton = false;
        enableMdicDebugDraw = false;
        mdicDebugDrawActualDraw = false;
        directGpuGeometryRendererActualDraw = false;
        enableSimpleGpuMeshRenderer = false;
        enableDebugMeshRenderer = false;
    }

    public static synchronized void applyFormalTexturedShaderPreviewPreset() {
        clearInternal();
        presetName = "formal_textured_shader_preview";
        enableWorldEngineSkeleton = true;
        enableAutoChunkIngest = false;
        enableAutoCpuMeshBuild = false;
        enableAutoBuiltSectionBuild = false;
        enableAutoGeometryManagerConsume = false;
        enableGeometryGpuUpload = false;
        enableGeometryGpuVisualization = false;
        enableGeometryGpuReadbackMeshAutoRefresh = false;
        enableDirectGpuGeometryRenderer = false;
        enableDirectGpuGeometryAutoPlan = false;
        enableMdicCommandSkeleton = false;
        enableMdicDebugDraw = false;
        mdicDebugDrawActualDraw = false;
        directGpuGeometryRendererActualDraw = false;
        enableSimpleGpuMeshRenderer = false;
        enableDebugMeshRenderer = false;
    }

    public static synchronized void applyFormalPackedQuadPreviewPreset() {
        clearInternal();
        presetName = "formal_packed_quad_preview";
        enableWorldEngineSkeleton = true;
        enableAutoChunkIngest = false;
        enableAutoCpuMeshBuild = false;
        enableAutoBuiltSectionBuild = false;
        enableAutoGeometryManagerConsume = false;
        enableGeometryGpuUpload = false;
        enableGeometryGpuVisualization = false;
        enableGeometryGpuReadbackMeshAutoRefresh = false;
        enableDirectGpuGeometryRenderer = false;
        enableDirectGpuGeometryAutoPlan = false;
        enableMdicCommandSkeleton = false;
        enableMdicDebugDraw = false;
        mdicDebugDrawActualDraw = false;
        directGpuGeometryRendererActualDraw = false;
        enableSimpleGpuMeshRenderer = false;
        enableDebugMeshRenderer = false;
    }

    public static synchronized void applyMdicSkeletonPreset() {
        clearInternal();
        presetName = "mdic_skeleton";
        enableWorldEngineSkeleton = true;
        enableAutoChunkIngest = true;
        enableAutoCpuMeshBuild = false;
        enableAutoBuiltSectionBuild = true;
        enableAutoGeometryManagerConsume = true;
        enableGeometryGpuUpload = true;
        enableGeometryGpuVisualization = false;
        enableGeometryGpuReadbackMeshAutoRefresh = false;
        enableDirectGpuGeometryRenderer = false;
        enableDirectGpuGeometryAutoPlan = false;
        enableMdicCommandSkeleton = true;
        enableMdicDebugDraw = false;
        mdicDebugDrawActualDraw = false;
        mdicCommandMaxSections = 32;
        mdicCommandMaxRecords = 65536;
        mdicCommandBucketAware = true;
        mdicCommandIncludeTranslucent = false;
        mdicCommandIncludeDoubleSided = true;
        mdicCommandIncludeDirectional = true;
        mdicCommandDirectionalFaceMask = true;
        mdicCommandDirectionalFaceMaskFallbackAllWhenInside = true;
        mdicCommandSelectionMode = "AUTO";
        mdicCommandRenderDistanceChunks = 16;
        mdicCommandMaxPlanCandidates = 512;
        mdicCommandUseFrustum = true;
        mdicCommandFrustumFallbackToRadius = true;
        mdicCommandMaxCommands = 256;
        mdicCommandMaxCommandsPerSection = 8;
        mdicDebugDrawMaxCommands = 256;
        mdicDebugDrawMaxRecords = 65536;
        mdicDebugDrawMaxIndexedRecords = 65536;
        mdicDebugDrawMaxDrawCount = 256;
        directGpuGeometryRendererActualDraw = false;
        enableSimpleGpuMeshRenderer = false;
        enableDebugMeshRenderer = false;
    }

    public static synchronized void applyMdicDebugPreset() {
        applyMdicSkeletonPreset();
        presetName = "mdic_debug";
        enableMdicDebugDraw = true;
        mdicDebugDrawActualDraw = false;
    }

    public static synchronized void applyOverlayPreset() {
        clearInternal();
        presetName = "overlay";
        enableWorldEngineSkeleton = true;
        enableAutoChunkIngest = true;
        enableAutoCpuMeshBuild = true;
        enableAutoBuiltSectionBuild = false;
        enableGeometryGpuReadbackMeshAutoRefresh = false;
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
        enableGeometryGpuReadbackMeshAutoRefresh = false;
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
        enableGeometryGpuReadbackMeshAutoRefresh = false;
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
        enableGeometryGpuUpload = false;
        enableGeometryGpuReadbackMeshAutoRefresh = false;
    }

    public static synchronized void applyGlHeapVisualizePreset() {
        clearInternal();
        presetName = "gl_heap_visualize";
        enableWorldEngineSkeleton = true;
        enableAutoChunkIngest = true;
        enableAutoCpuMeshBuild = false;
        enableAutoBuiltSectionBuild = true;
        enableAutoGeometryManagerConsume = true;
        enableGeometryGpuUpload = true;
        enableGeometryGpuVisualization = true;
        enableGeometryGpuReadbackMeshAutoRefresh = false;
        geometryGpuVisualizationAlpha = 0.85D;
        geometryGpuVisualizationIgnoreDepth = true;
        geometryGpuVisualizationDoubleSided = true;
        enableSimpleGpuMeshRenderer = false;
        enableDebugMeshRenderer = false;
    }

    public static synchronized void applyGlHeapReadbackPreset() {
        clearInternal();
        presetName = "gl_heap_readback";
        enableWorldEngineSkeleton = true;
        enableAutoChunkIngest = true;
        enableAutoCpuMeshBuild = false;
        enableAutoBuiltSectionBuild = true;
        enableAutoGeometryManagerConsume = true;
        enableGeometryGpuUpload = true;
        enableGeometryGpuVisualization = false;
        enableGeometryGpuReadbackMeshAutoRefresh = true;
        enableSimpleGpuMeshRenderer = true;
        enableDebugMeshRenderer = false;
        simpleGpuMeshSource = SimpleGpuMeshSource.GL_HEAP_READBACK;
        simpleGpuMeshMinRenderDistanceChunks = 0;
        simpleGpuMeshRenderDistanceChunks = 64;
        simpleGpuMeshRenderLoadedChunks = true;
        simpleGpuMeshLoadedChunkSkipMode = SimpleGpuMeshLoadedChunkSkipMode.DISABLED;
        simpleGpuMeshLoadedChunkMargin = 0;
        simpleGpuMeshKeepCachedChunks = true;
        simpleGpuMeshUseOriginalColors = true;
        simpleGpuMeshIgnoreDepth = true;
        simpleGpuMeshVerticalOffset = 0.05D;
        simpleGpuMeshAlpha = 1.0D;
    }

    public static synchronized void applyDirectGlDebugPreset() {
        clearInternal();
        presetName = "direct_gl_debug";
        enableWorldEngineSkeleton = true;
        enableAutoChunkIngest = true;
        enableAutoCpuMeshBuild = false;
        enableAutoBuiltSectionBuild = true;
        enableAutoGeometryManagerConsume = true;
        enableGeometryGpuUpload = true;
        enableGeometryGpuVisualization = false;
        enableGeometryGpuReadbackMeshAutoRefresh = false;
        enableDirectGpuGeometryRenderer = true;
        enableDirectGpuGeometryAutoPlan = true;
        directGpuGeometryRendererActualDraw = false;
        directGpuGeometryRendererMaxPlanCandidates = 256;
        directGpuGeometryRendererMaxDrawSections = 16;
        directGpuGeometryRendererMaxDrawRecords = 32768;
        directGpuGeometryRendererMaxRecordsPerSection = 4096;
        directGpuGeometryRendererRenderDistanceChunks = 12;
        enableSimpleGpuMeshRenderer = false;
        enableDebugMeshRenderer = false;
    }

    public static synchronized void setGlHeapReadbackMeshSource() {
        presetName = "custom";
        enableSimpleGpuMeshRenderer = true;
        enableGeometryGpuVisualization = false;
        simpleGpuMeshSource = SimpleGpuMeshSource.GL_HEAP_READBACK;
        simpleGpuMeshMinRenderDistanceChunks = 0;
        simpleGpuMeshRenderDistanceChunks = 64;
        simpleGpuMeshRenderLoadedChunks = true;
        simpleGpuMeshLoadedChunkSkipMode = SimpleGpuMeshLoadedChunkSkipMode.DISABLED;
        simpleGpuMeshLoadedChunkMargin = 0;
        simpleGpuMeshKeepCachedChunks = true;
        simpleGpuMeshUseOriginalColors = true;
        simpleGpuMeshIgnoreDepth = true;
        simpleGpuMeshVerticalOffset = 0.05D;
        simpleGpuMeshAlpha = 1.0D;
    }

    public static synchronized void setGeometryGpuUpload(boolean enabled) {
        presetName = "custom";
        enableGeometryGpuUpload = enabled;
    }

    public static synchronized void setGeometryGpuVisualization(boolean enabled) {
        presetName = "custom";
        enableGeometryGpuVisualization = enabled;
    }

    public static synchronized void setGeometryGpuReadbackMeshAutoRefresh(boolean enabled) {
        presetName = "custom";
        enableGeometryGpuReadbackMeshAutoRefresh = enabled;
    }

    public static synchronized void setDirectGpuGeometryRenderer(boolean enabled) {
        presetName = "custom";
        enableDirectGpuGeometryRenderer = enabled;
        if (!enabled) {
            directGpuGeometryRendererActualDraw = false;
        }
    }

    public static synchronized void setDirectGpuGeometryAutoPlan(boolean enabled) {
        presetName = "custom";
        enableDirectGpuGeometryAutoPlan = enabled;
        if (enabled) {
            enableDirectGpuGeometryRenderer = true;
        }
    }

    public static synchronized void setDirectGpuGeometryRendererActualDraw(boolean enabled) {
        presetName = "custom";
        enableDirectGpuGeometryRenderer = enabled ? true : enableDirectGpuGeometryRenderer;
        directGpuGeometryRendererActualDraw = enabled;
    }

    public static synchronized void setMdicDebugDraw(boolean enabled) {
        presetName = "custom";
        enableMdicDebugDraw = enabled;
        if (!enabled) {
            mdicDebugDrawActualDraw = false;
        }
    }

    public static synchronized void setMdicDebugDrawActualDraw(boolean enabled) {
        presetName = "custom";
        enableMdicDebugDraw = enabled ? true : enableMdicDebugDraw;
        enableMdicCommandSkeleton = enabled ? true : enableMdicCommandSkeleton;
        mdicDebugDrawActualDraw = enabled;
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
        enableGeometryGpuUpload = null;
        enableGeometryGpuVisualization = null;
        enableGeometryGpuReadbackMeshAutoRefresh = null;
        enableDirectGpuGeometryRenderer = null;
        enableDirectGpuGeometryAutoPlan = null;
        enableMdicCommandSkeleton = null;
        enableMdicDebugDraw = null;
        mdicDebugDrawActualDraw = null;
        mdicCommandMaxSections = null;
        mdicCommandMaxRecords = null;
        mdicCommandBucketAware = null;
        mdicCommandIncludeTranslucent = null;
        mdicCommandIncludeDoubleSided = null;
        mdicCommandIncludeDirectional = null;
        mdicCommandDirectionalFaceMask = null;
        mdicCommandDirectionalFaceMaskFallbackAllWhenInside = null;
        mdicCommandSelectionMode = null;
        mdicCommandRenderDistanceChunks = null;
        mdicCommandMaxPlanCandidates = null;
        mdicCommandUseFrustum = null;
        mdicCommandFrustumFallbackToRadius = null;
        mdicCommandMaxCommands = null;
        mdicCommandMaxCommandsPerSection = null;
        mdicDebugDrawMaxCommands = null;
        mdicDebugDrawMaxRecords = null;
        mdicDebugDrawMaxIndexedRecords = null;
        mdicDebugDrawMaxDrawCount = null;
        directGpuGeometryRendererActualDraw = null;
        directGpuGeometryRendererMaxPlanCandidates = null;
        directGpuGeometryRendererMaxDrawSections = null;
        directGpuGeometryRendererMaxDrawRecords = null;
        directGpuGeometryRendererMaxRecordsPerSection = null;
        directGpuGeometryRendererRenderDistanceChunks = null;
        geometryGpuVisualizationAlpha = null;
        geometryGpuVisualizationIgnoreDepth = null;
        geometryGpuVisualizationDoubleSided = null;
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
                enableGeometryGpuUpload(),
                source(enableGeometryGpuUpload),
                enableGeometryGpuVisualization(),
                source(enableGeometryGpuVisualization),
                enableGeometryGpuReadbackMeshAutoRefresh(),
                source(enableGeometryGpuReadbackMeshAutoRefresh),
                enableDirectGpuGeometryRenderer(),
                source(enableDirectGpuGeometryRenderer),
                enableDirectGpuGeometryAutoPlan(),
                source(enableDirectGpuGeometryAutoPlan),
                directGpuGeometryRendererActualDraw(),
                source(directGpuGeometryRendererActualDraw),
                geometryGpuVisualizationAlpha(),
                source(geometryGpuVisualizationAlpha),
                geometryGpuVisualizationIgnoreDepth(),
                source(geometryGpuVisualizationIgnoreDepth),
                geometryGpuVisualizationDoubleSided(),
                source(geometryGpuVisualizationDoubleSided),
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

    public static synchronized boolean enableGeometryGpuUpload() {
        return value(enableGeometryGpuUpload, ForgeVoxyConfig.ENABLE_GEOMETRY_GPU_UPLOAD.get());
    }

    public static synchronized boolean enableGeometryGpuVisualization() {
        return value(enableGeometryGpuVisualization, ForgeVoxyConfig.ENABLE_GEOMETRY_GPU_VISUALIZATION.get());
    }

    public static synchronized boolean enableGeometryGpuReadbackMeshAutoRefresh() {
        return value(enableGeometryGpuReadbackMeshAutoRefresh, ForgeVoxyConfig.ENABLE_GEOMETRY_GPU_READBACK_MESH_AUTO_REFRESH.get());
    }

    public static synchronized boolean enableDirectGpuGeometryRenderer() {
        return value(enableDirectGpuGeometryRenderer, ForgeVoxyConfig.ENABLE_DIRECT_GPU_GEOMETRY_RENDERER.get());
    }

    public static synchronized boolean enableDirectGpuGeometryAutoPlan() {
        return value(enableDirectGpuGeometryAutoPlan, ForgeVoxyConfig.ENABLE_DIRECT_GPU_GEOMETRY_AUTO_PLAN.get());
    }

    public static synchronized boolean enableMdicCommandSkeleton() {
        return value(enableMdicCommandSkeleton, ForgeVoxyConfig.ENABLE_MDIC_COMMAND_SKELETON.get());
    }

    public static synchronized boolean enableMdicDebugDraw() {
        return value(enableMdicDebugDraw, ForgeVoxyConfig.ENABLE_MDIC_DEBUG_DRAW.get());
    }

    public static synchronized boolean mdicDebugDrawActualDraw() {
        return value(mdicDebugDrawActualDraw, false);
    }

    public static synchronized int mdicCommandMaxSections() {
        return Math.min(64, Math.max(1, value(mdicCommandMaxSections, ForgeVoxyConfig.MDIC_COMMAND_MAX_SECTIONS.get())));
    }

    public static synchronized int mdicCommandMaxRecords() {
        return Math.min(131072, Math.max(1, value(mdicCommandMaxRecords, ForgeVoxyConfig.MDIC_COMMAND_MAX_RECORDS.get())));
    }

    public static synchronized boolean mdicCommandBucketAware() {
        return value(mdicCommandBucketAware, ForgeVoxyConfig.MDIC_COMMAND_BUCKET_AWARE.get());
    }

    public static synchronized boolean mdicCommandIncludeTranslucent() {
        return value(mdicCommandIncludeTranslucent, ForgeVoxyConfig.MDIC_COMMAND_INCLUDE_TRANSLUCENT.get());
    }

    public static synchronized boolean mdicCommandIncludeDoubleSided() {
        return value(mdicCommandIncludeDoubleSided, ForgeVoxyConfig.MDIC_COMMAND_INCLUDE_DOUBLE_SIDED.get());
    }

    public static synchronized boolean mdicCommandIncludeDirectional() {
        return value(mdicCommandIncludeDirectional, ForgeVoxyConfig.MDIC_COMMAND_INCLUDE_DIRECTIONAL.get());
    }

    public static synchronized boolean mdicCommandDirectionalFaceMask() {
        return value(mdicCommandDirectionalFaceMask, ForgeVoxyConfig.MDIC_COMMAND_DIRECTIONAL_FACE_MASK.get());
    }

    public static synchronized boolean mdicCommandDirectionalFaceMaskFallbackAllWhenInside() {
        return value(mdicCommandDirectionalFaceMaskFallbackAllWhenInside, ForgeVoxyConfig.MDIC_COMMAND_DIRECTIONAL_FACE_MASK_FALLBACK_ALL_WHEN_INSIDE.get());
    }

    public static synchronized boolean mdicCommandDirectionalFaceMaskDebugLog() {
        return ForgeVoxyConfig.MDIC_COMMAND_DIRECTIONAL_FACE_MASK_DEBUG_LOG.get();
    }

    public static synchronized ForgeMdicCommandSelectionMode mdicCommandSelectionMode() {
        return ForgeMdicCommandSelectionMode.parse(value(mdicCommandSelectionMode, ForgeVoxyConfig.MDIC_COMMAND_SELECTION_MODE.get()));
    }

    public static synchronized int mdicCommandRenderDistanceChunks() {
        return Math.min(128, Math.max(1, value(mdicCommandRenderDistanceChunks, ForgeVoxyConfig.MDIC_COMMAND_RENDER_DISTANCE_CHUNKS.get())));
    }

    public static synchronized int mdicCommandMaxPlanCandidates() {
        return Math.min(4096, Math.max(1, value(mdicCommandMaxPlanCandidates, ForgeVoxyConfig.MDIC_COMMAND_MAX_PLAN_CANDIDATES.get())));
    }

    public static synchronized boolean mdicCommandUseFrustum() {
        return value(mdicCommandUseFrustum, ForgeVoxyConfig.MDIC_COMMAND_USE_FRUSTUM.get());
    }

    public static synchronized boolean mdicCommandFrustumFallbackToRadius() {
        return value(mdicCommandFrustumFallbackToRadius, ForgeVoxyConfig.MDIC_COMMAND_FRUSTUM_FALLBACK_TO_RADIUS.get());
    }

    public static synchronized int mdicCommandMaxCommands() {
        return Math.min(512, Math.max(1, value(mdicCommandMaxCommands, ForgeVoxyConfig.MDIC_COMMAND_MAX_COMMANDS.get())));
    }

    public static synchronized int mdicCommandMaxCommandsPerSection() {
        return Math.min(8, Math.max(1, value(mdicCommandMaxCommandsPerSection, ForgeVoxyConfig.MDIC_COMMAND_MAX_COMMANDS_PER_SECTION.get())));
    }

    public static synchronized boolean mdicCommandDebugLog() {
        return ForgeVoxyConfig.MDIC_COMMAND_DEBUG_LOG.get();
    }

    public static synchronized int mdicDebugDrawMaxCommands() {
        return Math.min(512, Math.max(1, value(mdicDebugDrawMaxCommands, ForgeVoxyConfig.MDIC_DEBUG_DRAW_MAX_COMMANDS.get())));
    }

    public static synchronized int mdicDebugDrawMaxRecords() {
        return Math.min(131072, Math.max(1, value(mdicDebugDrawMaxRecords, ForgeVoxyConfig.MDIC_DEBUG_DRAW_MAX_RECORDS.get())));
    }

    public static synchronized int mdicDebugDrawMaxIndexedRecords() {
        return Math.min(131072, Math.max(1, value(mdicDebugDrawMaxIndexedRecords, ForgeVoxyConfig.MDIC_DEBUG_DRAW_MAX_INDEXED_RECORDS.get())));
    }

    public static synchronized int mdicDebugDrawMaxDrawCount() {
        return Math.min(512, Math.max(1, value(mdicDebugDrawMaxDrawCount, ForgeVoxyConfig.MDIC_DEBUG_DRAW_MAX_DRAW_COUNT.get())));
    }

    public static synchronized double mdicDebugDrawAlpha() {
        return Math.max(0.05D, Math.min(1.0D, ForgeVoxyConfig.MDIC_DEBUG_DRAW_ALPHA.get()));
    }

    public static synchronized boolean mdicDebugDrawIgnoreDepth() {
        return ForgeVoxyConfig.MDIC_DEBUG_DRAW_IGNORE_DEPTH.get();
    }

    public static synchronized boolean mdicDebugDrawDoubleSided() {
        return ForgeVoxyConfig.MDIC_DEBUG_DRAW_DOUBLE_SIDED.get();
    }

    public static synchronized double mdicDebugDrawFrameBudgetMs() {
        return Math.max(0.1D, Math.min(100.0D, ForgeVoxyConfig.MDIC_DEBUG_DRAW_FRAME_BUDGET_MS.get()));
    }

    public static synchronized boolean mdicDebugDrawDebugLog() {
        return ForgeVoxyConfig.MDIC_DEBUG_DRAW_DEBUG_LOG.get();
    }

    public static synchronized boolean directGpuGeometryRendererActualDraw() {
        return value(directGpuGeometryRendererActualDraw, ForgeVoxyConfig.DIRECT_GPU_GEOMETRY_RENDERER_ACTUAL_DRAW.get());
    }

    public static synchronized int directGpuGeometryRendererMaxPlanCandidates() {
        return Math.min(2048, Math.max(1, value(directGpuGeometryRendererMaxPlanCandidates, ForgeVoxyConfig.DIRECT_GPU_GEOMETRY_RENDERER_MAX_PLAN_CANDIDATES.get())));
    }

    public static synchronized int directGpuGeometryRendererMaxDrawSections() {
        return Math.min(64, Math.max(1, value(directGpuGeometryRendererMaxDrawSections, ForgeVoxyConfig.DIRECT_GPU_GEOMETRY_RENDERER_MAX_DRAW_SECTIONS.get())));
    }

    public static synchronized int directGpuGeometryRendererMaxDrawRecords() {
        return Math.min(65536, Math.max(1, value(directGpuGeometryRendererMaxDrawRecords, ForgeVoxyConfig.DIRECT_GPU_GEOMETRY_RENDERER_MAX_DRAW_RECORDS.get())));
    }

    public static synchronized int directGpuGeometryRendererMaxRecordsPerSection() {
        return Math.min(16384, Math.max(1, value(directGpuGeometryRendererMaxRecordsPerSection, ForgeVoxyConfig.DIRECT_GPU_GEOMETRY_RENDERER_MAX_RECORDS_PER_SECTION.get())));
    }

    public static synchronized int directGpuGeometryRendererRenderDistanceChunks() {
        return Math.min(64, Math.max(1, value(directGpuGeometryRendererRenderDistanceChunks, ForgeVoxyConfig.DIRECT_GPU_GEOMETRY_RENDERER_RENDER_DISTANCE_CHUNKS.get())));
    }

    public static synchronized double geometryGpuVisualizationAlpha() {
        return Math.max(0.05D, Math.min(1.0D, value(geometryGpuVisualizationAlpha, ForgeVoxyConfig.GEOMETRY_GPU_VISUALIZATION_ALPHA.get())));
    }

    public static synchronized boolean geometryGpuVisualizationIgnoreDepth() {
        return value(geometryGpuVisualizationIgnoreDepth, ForgeVoxyConfig.GEOMETRY_GPU_VISUALIZATION_IGNORE_DEPTH.get());
    }

    public static synchronized boolean geometryGpuVisualizationDoubleSided() {
        return value(geometryGpuVisualizationDoubleSided, ForgeVoxyConfig.GEOMETRY_GPU_VISUALIZATION_DOUBLE_SIDED.get());
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
                || enableGeometryGpuUpload != null
                || enableGeometryGpuVisualization != null
                || enableGeometryGpuReadbackMeshAutoRefresh != null
                || enableDirectGpuGeometryRenderer != null
                || enableDirectGpuGeometryAutoPlan != null
                || enableMdicCommandSkeleton != null
                || enableMdicDebugDraw != null
                || mdicDebugDrawActualDraw != null
                || mdicCommandMaxSections != null
                || mdicCommandMaxRecords != null
                || mdicCommandBucketAware != null
                || mdicCommandIncludeTranslucent != null
                || mdicCommandIncludeDoubleSided != null
                || mdicCommandIncludeDirectional != null
                || mdicCommandDirectionalFaceMask != null
                || mdicCommandDirectionalFaceMaskFallbackAllWhenInside != null
                || mdicCommandSelectionMode != null
                || mdicCommandRenderDistanceChunks != null
                || mdicCommandMaxPlanCandidates != null
                || mdicCommandUseFrustum != null
                || mdicCommandFrustumFallbackToRadius != null
                || mdicCommandMaxCommands != null
                || mdicCommandMaxCommandsPerSection != null
                || mdicDebugDrawMaxCommands != null
                || mdicDebugDrawMaxRecords != null
                || mdicDebugDrawMaxIndexedRecords != null
                || mdicDebugDrawMaxDrawCount != null
                || directGpuGeometryRendererActualDraw != null
                || directGpuGeometryRendererMaxPlanCandidates != null
                || directGpuGeometryRendererMaxDrawSections != null
                || directGpuGeometryRendererMaxDrawRecords != null
                || directGpuGeometryRendererMaxRecordsPerSection != null
                || directGpuGeometryRendererRenderDistanceChunks != null
                || geometryGpuVisualizationAlpha != null
                || geometryGpuVisualizationIgnoreDepth != null
                || geometryGpuVisualizationDoubleSided != null
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
            boolean enableGeometryGpuUpload,
            String enableGeometryGpuUploadSource,
            boolean enableGeometryGpuVisualization,
            String enableGeometryGpuVisualizationSource,
            boolean enableGeometryGpuReadbackMeshAutoRefresh,
            String enableGeometryGpuReadbackMeshAutoRefreshSource,
            boolean enableDirectGpuGeometryRenderer,
            String enableDirectGpuGeometryRendererSource,
            boolean enableDirectGpuGeometryAutoPlan,
            String enableDirectGpuGeometryAutoPlanSource,
            boolean directGpuGeometryRendererActualDraw,
            String directGpuGeometryRendererActualDrawSource,
            double geometryGpuVisualizationAlpha,
            String geometryGpuVisualizationAlphaSource,
            boolean geometryGpuVisualizationIgnoreDepth,
            String geometryGpuVisualizationIgnoreDepthSource,
            boolean geometryGpuVisualizationDoubleSided,
            String geometryGpuVisualizationDoubleSidedSource,
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
