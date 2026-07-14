package me.cortex.voxy.config;

import me.cortex.voxy.common.util.cpu.CpuLayout;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ForgeVoxyCpuLayoutParityTest {
    @Test
    void serviceThreadBoundsUseTheOriginalCpuLayoutProbe() {
        int coreCount = CpuLayout.getCoreCount();

        assertTrue(coreCount >= 1);
        assertEquals(coreCount, ForgeVoxyConfig.serviceThreadMaximum());
        assertEquals(
                Math.max((int) (coreCount / 1.5D), 1),
                ForgeVoxyConfig.ORIGINAL_VOXY_SERVICE_THREADS.getDefault());
    }
}
