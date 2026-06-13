package me.cortex.voxy.forge;

import me.cortex.voxy.config.ForgeVoxyConfig;

final class ForgeDirectGpuGeometryRendererConfig {
    private ForgeDirectGpuGeometryRendererConfig() {
    }

    static boolean isEnabled() {
        return ForgeVoxyRuntimeOverrides.enableDirectGpuGeometryRenderer();
    }

    static int maxSections() {
        return Math.min(512, Math.max(1, ForgeVoxyConfig.DIRECT_GPU_GEOMETRY_RENDERER_MAX_SECTIONS.get()));
    }

    static int maxRecords() {
        return Math.min(131072, Math.max(1, ForgeVoxyConfig.DIRECT_GPU_GEOMETRY_RENDERER_MAX_RECORDS.get()));
    }

    static int maxPlanCandidates() {
        return ForgeVoxyRuntimeOverrides.directGpuGeometryRendererMaxPlanCandidates();
    }

    static int maxDrawSections() {
        return ForgeVoxyRuntimeOverrides.directGpuGeometryRendererMaxDrawSections();
    }

    static int maxDrawRecords() {
        return ForgeVoxyRuntimeOverrides.directGpuGeometryRendererMaxDrawRecords();
    }

    static int maxRecordsPerSection() {
        return ForgeVoxyRuntimeOverrides.directGpuGeometryRendererMaxRecordsPerSection();
    }

    static int renderDistanceChunks() {
        return ForgeVoxyRuntimeOverrides.directGpuGeometryRendererRenderDistanceChunks();
    }

    static double debugAlpha() {
        return Math.max(0.05D, Math.min(1.0D, ForgeVoxyConfig.DIRECT_GPU_GEOMETRY_RENDERER_DEBUG_ALPHA.get()));
    }

    static boolean ignoreDepth() {
        return ForgeVoxyConfig.DIRECT_GPU_GEOMETRY_RENDERER_IGNORE_DEPTH.get();
    }

    static boolean doubleSided() {
        return ForgeVoxyConfig.DIRECT_GPU_GEOMETRY_RENDERER_DOUBLE_SIDED.get();
    }

    static boolean actualDrawEnabled() {
        return ForgeVoxyRuntimeOverrides.directGpuGeometryRendererActualDraw();
    }

    static boolean debugLog() {
        return ForgeVoxyConfig.DIRECT_GPU_GEOMETRY_RENDERER_DEBUG_LOG.get();
    }

    static boolean autoPlanEnabled() {
        return ForgeVoxyRuntimeOverrides.enableDirectGpuGeometryAutoPlan();
    }

    static int autoPlanCooldownTicks() {
        return Math.min(400, Math.max(1, ForgeVoxyConfig.DIRECT_GPU_GEOMETRY_AUTO_PLAN_COOLDOWN_TICKS.get()));
    }

    static int autoPlanMoveThresholdBlocks() {
        return Math.min(1024, Math.max(1, ForgeVoxyConfig.DIRECT_GPU_GEOMETRY_AUTO_PLAN_MOVE_THRESHOLD_BLOCKS.get()));
    }

    static boolean autoPlanOnlyWhenEnabled() {
        return ForgeVoxyConfig.DIRECT_GPU_GEOMETRY_AUTO_PLAN_ONLY_WHEN_ENABLED.get();
    }

    static boolean autoPlanOnlyWhenActualDrawEnabled() {
        return ForgeVoxyConfig.DIRECT_GPU_GEOMETRY_AUTO_PLAN_ONLY_WHEN_ACTUAL_DRAW_ENABLED.get();
    }

    static boolean autoPlanOnDimensionChange() {
        return ForgeVoxyConfig.DIRECT_GPU_GEOMETRY_AUTO_PLAN_ON_DIMENSION_CHANGE.get();
    }

    static int autoPlanMaxCandidates() {
        return Math.min(2048, Math.max(1, ForgeVoxyConfig.DIRECT_GPU_GEOMETRY_AUTO_PLAN_MAX_CANDIDATES.get()));
    }

    static int autoPlanMaxSections() {
        return Math.min(64, Math.max(1, ForgeVoxyConfig.DIRECT_GPU_GEOMETRY_AUTO_PLAN_MAX_SECTIONS.get()));
    }

    static int autoPlanMaxRecords() {
        return Math.min(131072, Math.max(1, ForgeVoxyConfig.DIRECT_GPU_GEOMETRY_AUTO_PLAN_MAX_RECORDS.get()));
    }
}
