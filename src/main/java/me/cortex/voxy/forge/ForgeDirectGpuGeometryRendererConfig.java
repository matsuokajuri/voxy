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
}
