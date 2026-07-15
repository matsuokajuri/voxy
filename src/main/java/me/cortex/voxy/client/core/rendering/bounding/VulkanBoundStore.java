package me.cortex.voxy.client.core.rendering.bounding;

import me.cortex.voxy.client.core.rendering.VulkanViewport;
import me.cortex.voxy.client.core.vulkan.VoxyVulkanBuffer;

/** Vulkan resource contract for the original chunk-bound position stores. */
public interface VulkanBoundStore extends AutoCloseable {
    VoxyVulkanBuffer getBuffer();

    int getCount();

    default void preRender(VulkanViewport<?> viewport) {
    }

    default void postRender(VulkanViewport<?> viewport) {
    }

    @Override
    void close();
}
