package me.cortex.voxy.client.core.vulkan;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** Synchronous startup readback used by the original software model bakery's atlas snapshot. */
public final class VulkanTextureReadback {
    private static final long TIMEOUT_NS = 5_000_000_000L;

    private VulkanTextureReadback() {
    }

    public static int[] readRgba8(GpuTexture texture, int mipLevel) {
        RenderSystem.assertOnRenderThread();
        if (texture.getFormat() != GpuFormat.RGBA8_UNORM) {
            throw new IllegalArgumentException("RGBA8 readback requires RGBA8_UNORM, got " + texture.getFormat());
        }
        if ((texture.usage() & GpuTexture.USAGE_COPY_SRC) == 0) {
            throw new IllegalArgumentException("RGBA8 readback source requires USAGE_COPY_SRC");
        }
        if (mipLevel < 0 || mipLevel >= texture.getMipLevels()) {
            throw new IllegalArgumentException("RGBA8 readback mip level is invalid: " + mipLevel);
        }

        int width = texture.getWidth(mipLevel);
        int height = texture.getHeight(mipLevel);
        long byteCount = Math.multiplyExact(Math.multiplyExact((long) width, height), 4L);
        if (byteCount > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("RGBA8 texture is too large for the software bakery: " + byteCount);
        }

        GpuBuffer readback = VoxyVulkanContext.get().vulkanDevice().createBuffer(
                () -> "Voxy block-atlas readback",
                GpuBuffer.USAGE_MAP_READ | GpuBuffer.USAGE_COPY_DST,
                byteCount
        );
        CommandEncoder encoder = VoxyVulkanContext.get().hostDevice().createCommandEncoder();
        var fence = encoder.createFence();
        try {
            encoder.copyTextureToBuffer(texture, readback, 0L, () -> { }, mipLevel);
            encoder.submit();
            if (!fence.awaitCompletion(TIMEOUT_NS)) {
                throw new IllegalStateException("Timed out reading Minecraft's Vulkan block atlas");
            }

            int[] result = new int[Math.multiplyExact(width, height)];
            try (GpuBufferSlice.MappedView mapped = readback.map(true, false)) {
                ByteBuffer bytes = mapped.data().duplicate().order(ByteOrder.nativeOrder());
                bytes.clear();
                bytes.asIntBuffer().get(result);
            }
            return result;
        } finally {
            fence.close();
            readback.close();
        }
    }
}
