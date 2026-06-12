package me.cortex.voxy.forge;

import me.cortex.voxy.config.ForgeVoxyConfig;

final class ForgeDirectGpuGeometryRendererConfig {
    private ForgeDirectGpuGeometryRendererConfig() {
    }

    static boolean isEnabled() {
        return ForgeVoxyRuntimeOverrides.enableDirectGpuGeometryRenderer();
    }

    static int maxSections() {
        return Math.min(128, Math.max(1, ForgeVoxyConfig.DIRECT_GPU_GEOMETRY_RENDERER_MAX_SECTIONS.get()));
    }

    static int maxRecords() {
        return Math.min(131072, Math.max(1, ForgeVoxyConfig.DIRECT_GPU_GEOMETRY_RENDERER_MAX_RECORDS.get()));
    }

    static boolean debugLog() {
        return ForgeVoxyConfig.DIRECT_GPU_GEOMETRY_RENDERER_DEBUG_LOG.get();
    }
}
