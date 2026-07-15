package me.cortex.voxy.client.core.rendering.util;

import me.cortex.voxy.client.core.vulkan.VoxyVulkanBuffer;
import me.cortex.voxy.client.core.vulkan.VoxyVulkanBufferUsage;
import me.cortex.voxy.client.core.vulkan.VulkanUploadStream;
import me.cortex.voxy.common.util.MemoryBuffer;
import org.lwjgl.system.MemoryUtil;

/** Vulkan uint16 translation of SharedIndexBuffer.INSTANCE_BB_BYTE's 32 packed cubes. */
public final class VulkanBoundingIndexBuffer implements AutoCloseable {
    public static final int CUBES_PER_BATCH = 32;
    public static final int INDICES_PER_CUBE = 6 * 2 * 3;

    private static final short[] CUBE_INDICES = {
            0, 1, 2, 3, 2, 1,
            6, 5, 4, 5, 6, 7,
            0, 4, 1, 5, 1, 4,
            3, 6, 2, 6, 3, 7,
            2, 4, 0, 4, 2, 6,
            1, 5, 3, 7, 3, 5
    };

    private final VulkanUploadStream uploads = new VulkanUploadStream();
    private final VoxyVulkanBuffer buffer;
    private boolean closed;

    public VulkanBoundingIndexBuffer() {
        long indexCount = CUBES_PER_BATCH * (long) INDICES_PER_CUBE;
        VoxyVulkanBuffer createdBuffer = VoxyVulkanBuffer.create(
                Math.multiplyExact(indexCount, Short.BYTES),
                VoxyVulkanBufferUsage.of(
                        VoxyVulkanBufferUsage.INDEX | VoxyVulkanBufferUsage.TRANSFER_DESTINATION
                ),
                "Voxy packed chunk-bound indices"
        );
        MemoryBuffer data = null;
        try {
            data = new MemoryBuffer(createdBuffer.size());
            long pointer = data.address;
            for (int cube = 0; cube < CUBES_PER_BATCH; cube++) {
                int baseVertex = cube * 8;
                for (short index : CUBE_INDICES) {
                    MemoryUtil.memPutShort(pointer, (short) (baseVertex + index));
                    pointer += Short.BYTES;
                }
            }
            data.cpyTo(this.uploads.upload(
                    createdBuffer,
                    0L,
                    Math.toIntExact(data.size)
            ).address());
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
        if (this.closed) throw new IllegalStateException("Vulkan bounding index buffer is closed");
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
