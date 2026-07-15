package me.cortex.voxy.client.core.rendering.util;

import me.cortex.voxy.client.core.vulkan.VoxyVulkanBuffer;
import me.cortex.voxy.client.core.vulkan.VoxyVulkanBufferUsage;
import me.cortex.voxy.client.core.vulkan.VulkanUploadStream;
import me.cortex.voxy.common.util.MemoryBuffer;
import org.lwjgl.system.MemoryUtil;

/** Uint16 representation of the original shared byte-index fullscreen quad. */
public final class VulkanFullscreenIndexBuffer implements AutoCloseable {
    public static final int INDEX_COUNT = 6;
    private static final short[] INDICES = {1, 2, 0, 1, 3, 2};

    private final VulkanUploadStream uploads = new VulkanUploadStream();
    private final VoxyVulkanBuffer buffer;
    private boolean closed;

    public VulkanFullscreenIndexBuffer() {
        VoxyVulkanBuffer createdBuffer = VoxyVulkanBuffer.create(
                INDEX_COUNT * (long) Short.BYTES,
                VoxyVulkanBufferUsage.of(
                        VoxyVulkanBufferUsage.INDEX | VoxyVulkanBufferUsage.TRANSFER_DESTINATION
                ),
                "Voxy fullscreen quad indices"
        );
        MemoryBuffer data = null;
        try {
            data = new MemoryBuffer(createdBuffer.size());
            long pointer = data.address;
            for (short index : INDICES) {
                MemoryUtil.memPutShort(pointer, index);
                pointer += Short.BYTES;
            }
            data.cpyTo(this.uploads.upload(createdBuffer, 0L, Math.toIntExact(data.size)).address());
            this.uploads.commit();
        } catch (RuntimeException | Error exception) {
            this.uploads.commit();
            createdBuffer.close();
            throw exception;
        } finally {
            if (data != null) data.free();
        }
        this.buffer = createdBuffer;
    }

    public VoxyVulkanBuffer buffer() {
        if (this.closed) throw new IllegalStateException("Voxy fullscreen index buffer is closed");
        return this.buffer;
    }

    @Override
    public void close() {
        if (this.closed) return;
        this.closed = true;
        this.uploads.commit();
        this.buffer.close();
    }
}
