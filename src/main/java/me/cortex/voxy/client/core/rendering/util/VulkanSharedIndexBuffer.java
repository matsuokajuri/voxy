package me.cortex.voxy.client.core.rendering.util;

import me.cortex.voxy.client.core.vulkan.VoxyVulkanBuffer;
import me.cortex.voxy.client.core.vulkan.VoxyVulkanBufferUsage;
import me.cortex.voxy.client.core.vulkan.VulkanUploadStream;
import me.cortex.voxy.common.util.MemoryBuffer;
import org.lwjgl.system.MemoryUtil;

/** Vulkan uint16 representation of the original shared quad/cull index data. */
public final class VulkanSharedIndexBuffer implements AutoCloseable {
    public static final int QUAD_COUNT = 16_380;
    public static final int CULL_FIRST_INDEX = (1 << 16) * 6 * 2;
    public static final int CULL_INDEX_COUNT = 6 * 2 * 3;

    private final VulkanUploadStream uploads = new VulkanUploadStream();
    private final VoxyVulkanBuffer buffer;
    private boolean closed;

    public VulkanSharedIndexBuffer() {
        long size = Math.addExact(Math.multiplyExact((long) CULL_FIRST_INDEX, Short.BYTES),
                CULL_INDEX_COUNT * (long) Short.BYTES);
        VoxyVulkanBuffer createdBuffer = VoxyVulkanBuffer.create(
                size,
                VoxyVulkanBufferUsage.of(
                        VoxyVulkanBufferUsage.INDEX | VoxyVulkanBufferUsage.TRANSFER_DESTINATION
                ),
                "Voxy shared quad/cull indices"
        );

        MemoryBuffer quads = null;
        MemoryBuffer cube = null;
        try {
            quads = generateQuadIndicesShort();
            cube = generateCubeIndicesShort();
            quads.cpyTo(this.uploads.upload(createdBuffer, 0L, Math.toIntExact(quads.size)).address());
            cube.cpyTo(this.uploads.upload(
                    createdBuffer,
                    Math.multiplyExact((long) CULL_FIRST_INDEX, Short.BYTES),
                    Math.toIntExact(cube.size)
            ).address());
            this.uploads.commit();
        } catch (RuntimeException | Error exception) {
            // Release mapped staging allocations before queuing the partially-created target for destruction.
            this.uploads.commit();
            createdBuffer.close();
            throw exception;
        } finally {
            if (quads != null) quads.free();
            if (cube != null) cube.free();
        }
        this.buffer = createdBuffer;
    }

    private static MemoryBuffer generateQuadIndicesShort() {
        MemoryBuffer data = new MemoryBuffer(QUAD_COUNT * 6L * Short.BYTES);
        long pointer = data.address;
        for (int vertex = 0; vertex < QUAD_COUNT * 4; vertex += 4) {
            MemoryUtil.memPutShort(pointer, (short) (vertex + 1));
            MemoryUtil.memPutShort(pointer + 2L, (short) (vertex + 2));
            MemoryUtil.memPutShort(pointer + 4L, (short) vertex);
            MemoryUtil.memPutShort(pointer + 6L, (short) (vertex + 1));
            MemoryUtil.memPutShort(pointer + 8L, (short) (vertex + 3));
            MemoryUtil.memPutShort(pointer + 10L, (short) (vertex + 2));
            pointer += 12L;
        }
        return data;
    }

    private static MemoryBuffer generateCubeIndicesShort() {
        short[] indices = {
                0, 1, 2, 3, 2, 1,
                6, 5, 4, 5, 6, 7,
                0, 4, 1, 5, 1, 4,
                3, 6, 2, 6, 3, 7,
                2, 4, 0, 4, 2, 6,
                1, 5, 3, 7, 3, 5
        };
        MemoryBuffer data = new MemoryBuffer(indices.length * (long) Short.BYTES);
        long pointer = data.address;
        for (short index : indices) {
            MemoryUtil.memPutShort(pointer, index);
            pointer += Short.BYTES;
        }
        return data;
    }

    public VoxyVulkanBuffer buffer() {
        if (this.closed) throw new IllegalStateException("Vulkan shared index buffer is closed");
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
