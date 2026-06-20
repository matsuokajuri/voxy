package me.cortex.voxy.config;

import oshi.SystemInfo;

final class ForgeOriginalVoxyCpuLayout {
    private ForgeOriginalVoxyCpuLayout() {
    }

    static int getCoreCount() {
        try {
            int physicalCoreCount = new SystemInfo()
                    .getHardware()
                    .getProcessor()
                    .getPhysicalProcessorCount();
            if (physicalCoreCount > 0) {
                return physicalCoreCount;
            }
        } catch (Exception ignored) {
            // The original CpuLayout also falls back when platform probing fails.
        }
        return Runtime.getRuntime().availableProcessors();
    }
}
