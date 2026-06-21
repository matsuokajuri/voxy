package me.cortex.voxy.forge;

import org.lwjgl.opengl.GL;

import java.util.Locale;

import static org.lwjgl.opengl.GL11C.GL_VENDOR;
import static org.lwjgl.opengl.GL11C.glGetString;
import static org.lwjgl.opengl.GL32C.glGetInteger64;
import static org.lwjgl.opengl.NVXGPUMemoryInfo.GL_GPU_MEMORY_INFO_DEDICATED_VIDMEM_NVX;

final class ForgeOriginalVoxyGlCapabilities {
    static final ForgeOriginalVoxyGlCapabilities INSTANCE = new ForgeOriginalVoxyGlCapabilities();

    final boolean canQueryGpuMemory;
    final long totalDedicatedMemory;
    final boolean isAmd;

    private ForgeOriginalVoxyGlCapabilities() {
        var capabilities = GL.getCapabilities();
        this.canQueryGpuMemory = capabilities.GL_NVX_gpu_memory_info;
        String vendor = glGetString(GL_VENDOR);
        String normalizedVendor = vendor == null ? "" : vendor.toLowerCase(Locale.ROOT);
        this.isAmd = normalizedVendor.contains("amd") || normalizedVendor.contains("radeon");
        this.totalDedicatedMemory = this.canQueryGpuMemory
                ? glGetInteger64(GL_GPU_MEMORY_INFO_DEDICATED_VIDMEM_NVX) * 1024L
                : -1L;
    }
}
