package me.cortex.voxy.client.core.vulkan;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.vulkan.VulkanConst;
import com.mojang.blaze3d.vulkan.VulkanGpuBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VK12;
import org.lwjgl.vulkan.VkBufferImageCopy;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** Tightly-packed buffer-to-image uploads recorded into Minecraft's current submission. */
public final class VulkanImageTransfer {
    private VulkanImageTransfer() {
    }

    public static void upload(
            VoxyVulkanImage image,
            int mipLevel,
            int arrayLayer,
            int destinationX,
            int destinationY,
            int width,
            int height,
            ByteBuffer source,
            VulkanSync.ImageState finalState
    ) {
        if (image.isClosed()) throw new IllegalStateException("Cannot upload to a closed Vulkan image");
        if ((image.usage() & VoxyVulkanImage.USAGE_COPY_DST) == 0) {
            throw new IllegalArgumentException("Vulkan image upload destination requires USAGE_COPY_DST");
        }
        if (mipLevel < 0 || mipLevel >= image.getMipLevels() || arrayLayer < 0
                || arrayLayer >= image.getDepthOrLayers()) {
            throw new IllegalArgumentException("Vulkan image upload subresource is invalid");
        }
        if (destinationX < 0 || destinationY < 0 || width <= 0 || height <= 0
                || destinationX + width > image.getWidth(mipLevel)
                || destinationY + height > image.getHeight(mipLevel)) {
            throw new IllegalArgumentException("Vulkan image upload rectangle is invalid");
        }
        int byteCount = Math.multiplyExact(Math.multiplyExact(width, height), image.getFormat().blockSize());
        if (source.remaining() != byteCount) {
            throw new IllegalArgumentException("Vulkan image upload requires exactly " + byteCount
                    + " bytes but received " + source.remaining());
        }

        CommandEncoder encoder = VoxyVulkanContext.get().hostDevice().createCommandEncoder();
        long alignment = Math.max(4, image.getFormat().byteAlignment());
        GpuBufferSlice.MappedView mapped = encoder.transientMemory()
                .allocateStaging(byteCount, alignment, GpuBuffer.USAGE_COPY_SRC);
        ByteBuffer target = mapped.data().duplicate().order(ByteOrder.nativeOrder());
        target.clear();
        target.limit(byteCount);
        target.put(source.duplicate());

        GpuBufferSlice staging = mapped.slice().slice(0L, byteCount);
        try {
            VulkanCommandRecorder.record(commandBuffer -> {
                image.transition(commandBuffer, mipLevel, 1, arrayLayer, 1, VulkanImageStates.TRANSFER_DESTINATION);
                try (MemoryStack stack = MemoryStack.stackPush()) {
                    VkBufferImageCopy.Buffer region = VkBufferImageCopy.calloc(1, stack);
                    region.bufferOffset(staging.offset());
                    region.bufferRowLength(0);
                    region.bufferImageHeight(0);
                    region.imageSubresource()
                            .aspectMask(VulkanConst.formatAspectMask(image.getFormat()))
                            .mipLevel(mipLevel)
                            .baseArrayLayer(arrayLayer)
                            .layerCount(1);
                    region.imageOffset().set(destinationX, destinationY, 0);
                    region.imageExtent().set(width, height, 1);
                    VK12.vkCmdCopyBufferToImage(
                            commandBuffer,
                            ((VulkanGpuBuffer) staging.buffer()).vkBuffer(),
                            image.vkImage(),
                            VK12.VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
                            region
                    );
                }
                image.transition(commandBuffer, mipLevel, 1, arrayLayer, 1, finalState);
            });
        } finally {
            mapped.close();
        }
    }
}
