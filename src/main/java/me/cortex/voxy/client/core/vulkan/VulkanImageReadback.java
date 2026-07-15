package me.cortex.voxy.client.core.vulkan;

import com.mojang.blaze3d.textures.GpuTexture;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.VK12;
import org.lwjgl.vulkan.VkBufferImageCopy;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** Image-to-buffer readback for Voxy-owned images, recorded in Minecraft's graphics submission. */
public final class VulkanImageReadback {
    private VulkanImageReadback() {
    }

    public static ByteBuffer readRgba8(
            VoxyVulkanImage image,
            int mipLevel,
            int arrayLayer,
            int sourceX,
            int sourceY,
            int width,
            int height
    ) {
        if (image.isClosed()) throw new IllegalStateException("Cannot read a closed Vulkan image");
        if ((image.usage() & GpuTexture.USAGE_COPY_SRC) == 0) {
            throw new IllegalArgumentException("Vulkan image readback requires USAGE_COPY_SRC");
        }
        if (image.getFormat() != com.mojang.blaze3d.GpuFormat.RGBA8_UNORM) {
            throw new IllegalArgumentException("RGBA8 readback requires RGBA8_UNORM");
        }
        if (mipLevel < 0 || mipLevel >= image.getMipLevels()
                || arrayLayer < 0 || arrayLayer >= image.getDepthOrLayers()
                || sourceX < 0 || sourceY < 0 || width <= 0 || height <= 0
                || sourceX + width > image.getWidth(mipLevel)
                || sourceY + height > image.getHeight(mipLevel)) {
            throw new IllegalArgumentException("Vulkan image readback region is invalid");
        }
        int byteCount = Math.multiplyExact(Math.multiplyExact(width, height), 4);
        VoxyVulkanBufferUsage policy = VoxyVulkanBufferUsage.of(
                VoxyVulkanBufferUsage.TRANSFER_DESTINATION | VoxyVulkanBufferUsage.TRANSFER_SOURCE
        );
        try (VoxyVulkanBuffer transfer = VoxyVulkanBuffer.createUninitialized(byteCount, policy, "Voxy image readback")) {
            VulkanSync.ImageState previous = image.state(mipLevel, arrayLayer);
            VulkanCommandRecorder.record(commandBuffer -> {
                image.transition(commandBuffer, mipLevel, 1, arrayLayer, 1, VulkanImageStates.TRANSFER_SOURCE);
                try (MemoryStack stack = MemoryStack.stackPush()) {
                    VkBufferImageCopy.Buffer region = VkBufferImageCopy.calloc(1, stack);
                    region.bufferOffset(0L).bufferRowLength(0).bufferImageHeight(0);
                    region.imageSubresource()
                            .aspectMask(com.mojang.blaze3d.vulkan.VulkanConst.formatAspectMask(image.getFormat()))
                            .mipLevel(mipLevel)
                            .baseArrayLayer(arrayLayer)
                            .layerCount(1);
                    region.imageOffset().set(sourceX, sourceY, 0);
                    region.imageExtent().set(width, height, 1);
                    VK12.vkCmdCopyImageToBuffer(
                            commandBuffer,
                            image.vkImage(),
                            VK12.VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,
                            transfer.vkBuffer(),
                            region
                    );
                }
                VulkanSync.bufferBarrier(
                        commandBuffer, transfer.vkBuffer(), 0L, byteCount,
                        VulkanSync.TRANSFER_WRITE, VulkanSync.TRANSFER_READ
                );
                image.transition(commandBuffer, mipLevel, 1, arrayLayer, 1, previous);
            });

            ByteBuffer output = ByteBuffer.allocateDirect(byteCount).order(ByteOrder.nativeOrder());
            VulkanDownloadStream download = VoxyVulkanContext.get().downloadStream();
            download.download(transfer, 0L, byteCount, (pointer, size) ->
                    output.put(MemoryUtil.memByteBuffer(pointer, Math.toIntExact(size))));
            download.flushWaitClear();
            output.flip();
            return output;
        }
    }
}
