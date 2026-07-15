package me.cortex.voxy.client.core.vulkan.shader;

import org.lwjgl.util.shaderc.Shaderc;
import org.lwjgl.vulkan.VK12;

public enum VulkanShaderStage {
    VERTEX(Shaderc.shaderc_vertex_shader, VK12.VK_SHADER_STAGE_VERTEX_BIT),
    FRAGMENT(Shaderc.shaderc_fragment_shader, VK12.VK_SHADER_STAGE_FRAGMENT_BIT),
    COMPUTE(Shaderc.shaderc_compute_shader, VK12.VK_SHADER_STAGE_COMPUTE_BIT);

    private final int shadercKind;
    private final int vkStage;

    VulkanShaderStage(int shadercKind, int vkStage) {
        this.shadercKind = shadercKind;
        this.vkStage = vkStage;
    }

    public int shadercKind() {
        return this.shadercKind;
    }

    public int vkStage() {
        return this.vkStage;
    }
}
