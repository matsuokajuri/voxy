package me.cortex.voxy.client.core.vulkan;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.KHRSynchronization2;
import org.lwjgl.vulkan.VK12;
import org.lwjgl.vulkan.VK13;
import org.lwjgl.vulkan.VkBufferMemoryBarrier2;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkDependencyInfo;
import org.lwjgl.vulkan.VkImageMemoryBarrier2;

/** Exact stage/access dependencies used by the Vulkan translation of Voxy's GL barriers. */
public final class VulkanSync {
    public static final Access NONE = new Access(VK13.VK_PIPELINE_STAGE_2_TOP_OF_PIPE_BIT, 0L);
    public static final Access TRANSFER_READ = new Access(
            VK13.VK_PIPELINE_STAGE_2_TRANSFER_BIT,
            VK13.VK_ACCESS_2_TRANSFER_READ_BIT
    );
    public static final Access TRANSFER_WRITE = new Access(
            VK13.VK_PIPELINE_STAGE_2_TRANSFER_BIT,
            VK13.VK_ACCESS_2_TRANSFER_WRITE_BIT
    );
    public static final Access HOST_READ = new Access(
            VK13.VK_PIPELINE_STAGE_2_HOST_BIT,
            VK13.VK_ACCESS_2_HOST_READ_BIT
    );
    public static final Access COMPUTE_STORAGE_READ = new Access(
            VK13.VK_PIPELINE_STAGE_2_COMPUTE_SHADER_BIT,
            VK13.VK_ACCESS_2_SHADER_STORAGE_READ_BIT
    );
    public static final Access COMPUTE_STORAGE_WRITE = new Access(
            VK13.VK_PIPELINE_STAGE_2_COMPUTE_SHADER_BIT,
            VK13.VK_ACCESS_2_SHADER_STORAGE_WRITE_BIT
    );
    public static final Access COMPUTE_STORAGE_READ_WRITE = COMPUTE_STORAGE_READ.or(COMPUTE_STORAGE_WRITE);
    public static final Access GRAPHICS_STORAGE_READ = new Access(
            VK13.VK_PIPELINE_STAGE_2_VERTEX_SHADER_BIT | VK13.VK_PIPELINE_STAGE_2_FRAGMENT_SHADER_BIT,
            VK13.VK_ACCESS_2_SHADER_STORAGE_READ_BIT
    );
    public static final Access GRAPHICS_STORAGE_READ_WRITE = new Access(
            VK13.VK_PIPELINE_STAGE_2_VERTEX_SHADER_BIT | VK13.VK_PIPELINE_STAGE_2_FRAGMENT_SHADER_BIT,
            VK13.VK_ACCESS_2_SHADER_STORAGE_READ_BIT | VK13.VK_ACCESS_2_SHADER_STORAGE_WRITE_BIT
    );
    public static final Access INDIRECT_READ = new Access(
            VK13.VK_PIPELINE_STAGE_2_DRAW_INDIRECT_BIT,
            VK13.VK_ACCESS_2_INDIRECT_COMMAND_READ_BIT
    );
    public static final Access VERTEX_READ = new Access(
            VK13.VK_PIPELINE_STAGE_2_VERTEX_INPUT_BIT,
            VK13.VK_ACCESS_2_VERTEX_ATTRIBUTE_READ_BIT
    );
    public static final Access INDEX_READ = new Access(
            VK13.VK_PIPELINE_STAGE_2_VERTEX_INPUT_BIT,
            VK13.VK_ACCESS_2_INDEX_READ_BIT
    );
    public static final Access SHADER_UNIFORM_READ = new Access(
            VK13.VK_PIPELINE_STAGE_2_COMPUTE_SHADER_BIT
                    | VK13.VK_PIPELINE_STAGE_2_VERTEX_SHADER_BIT
                    | VK13.VK_PIPELINE_STAGE_2_FRAGMENT_SHADER_BIT,
            VK13.VK_ACCESS_2_UNIFORM_READ_BIT
    );

    private VulkanSync() {
    }

    public static void bufferBarrier(
            VkCommandBuffer commandBuffer,
            long buffer,
            long offset,
            long size,
            Access source,
            Access destination
    ) {
        if (destination.accessMask() == 0L && source.accessMask() == 0L) {
            return;
        }
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkBufferMemoryBarrier2.Buffer barrier = VkBufferMemoryBarrier2.calloc(1, stack).sType$Default();
            barrier.srcStageMask(source.stageMask());
            barrier.srcAccessMask(source.accessMask());
            barrier.dstStageMask(destination.stageMask());
            barrier.dstAccessMask(destination.accessMask());
            barrier.srcQueueFamilyIndex(VK12.VK_QUEUE_FAMILY_IGNORED);
            barrier.dstQueueFamilyIndex(VK12.VK_QUEUE_FAMILY_IGNORED);
            barrier.buffer(buffer);
            barrier.offset(offset);
            barrier.size(size);

            VkDependencyInfo dependency = VkDependencyInfo.calloc(stack).sType$Default();
            dependency.pBufferMemoryBarriers(barrier);
            KHRSynchronization2.vkCmdPipelineBarrier2KHR(commandBuffer, dependency);
        }
    }

    public static void imageBarrier(
            VkCommandBuffer commandBuffer,
            long image,
            int aspectMask,
            int baseMipLevel,
            int levelCount,
            int baseArrayLayer,
            int layerCount,
            ImageState source,
            ImageState destination
    ) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkImageMemoryBarrier2.Buffer barrier = VkImageMemoryBarrier2.calloc(1, stack).sType$Default();
            barrier.srcStageMask(source.access().stageMask());
            barrier.srcAccessMask(source.access().accessMask());
            barrier.dstStageMask(destination.access().stageMask());
            barrier.dstAccessMask(destination.access().accessMask());
            barrier.oldLayout(source.layout());
            barrier.newLayout(destination.layout());
            barrier.srcQueueFamilyIndex(VK12.VK_QUEUE_FAMILY_IGNORED);
            barrier.dstQueueFamilyIndex(VK12.VK_QUEUE_FAMILY_IGNORED);
            barrier.image(image);
            barrier.subresourceRange()
                    .aspectMask(aspectMask)
                    .baseMipLevel(baseMipLevel)
                    .levelCount(levelCount)
                    .baseArrayLayer(baseArrayLayer)
                    .layerCount(layerCount);

            VkDependencyInfo dependency = VkDependencyInfo.calloc(stack).sType$Default();
            dependency.pImageMemoryBarriers(barrier);
            KHRSynchronization2.vkCmdPipelineBarrier2KHR(commandBuffer, dependency);
        }
    }

    public record Access(long stageMask, long accessMask) {
        public Access or(Access other) {
            return new Access(this.stageMask | other.stageMask, this.accessMask | other.accessMask);
        }
    }

    public record ImageState(int layout, Access access) {
    }
}
