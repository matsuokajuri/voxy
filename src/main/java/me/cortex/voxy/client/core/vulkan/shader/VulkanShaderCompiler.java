package me.cortex.voxy.client.core.vulkan.shader;

import me.cortex.voxy.client.core.gl.shader.ShaderLoader;
import me.cortex.voxy.client.core.gl.shader.ShaderType;
import me.cortex.voxy.client.core.rendering.util.PrintfDebugUtil;
import me.cortex.voxy.client.core.vulkan.VoxyVulkanContext;
import me.cortex.voxy.common.util.ThreadUtils;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.util.shaderc.Shaderc;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

/** shaderc owner for Voxy's compute and graphics SPIR-V modules. */
public final class VulkanShaderCompiler implements AutoCloseable {
    private final long compiler;
    private final long options;
    private boolean closed;

    public VulkanShaderCompiler() {
        long createdCompiler = Shaderc.shaderc_compiler_initialize();
        long createdOptions = Shaderc.shaderc_compile_options_initialize();
        if (createdCompiler == 0L || createdOptions == 0L) {
            if (createdOptions != 0L) Shaderc.shaderc_compile_options_release(createdOptions);
            if (createdCompiler != 0L) Shaderc.shaderc_compiler_release(createdCompiler);
            throw new IllegalStateException("Failed to initialize shaderc for Voxy Vulkan shaders");
        }
        this.compiler = createdCompiler;
        this.options = createdOptions;
        Shaderc.shaderc_compile_options_set_target_env(
                this.options, Shaderc.shaderc_target_env_vulkan, Shaderc.shaderc_env_version_vulkan_1_2
        );
        Shaderc.shaderc_compile_options_set_source_language(this.options, Shaderc.shaderc_source_language_glsl);
        // Minecraft 26.2's Vulkan compiler uses this for legacy GLSL interfaces without explicit locations.
        Shaderc.shaderc_compile_options_set_auto_map_locations(this.options, true);
        Shaderc.shaderc_compile_options_set_optimization_level(this.options, Shaderc.shaderc_optimization_level_performance);
        Shaderc.shaderc_compile_options_set_generate_debug_info(this.options);
    }

    public VulkanShaderModule compile(String id, VulkanShaderStage stage, Map<String, String> defines) {
        return this.compile(id, stage, defines, Map.of());
    }

    public VulkanShaderModule compile(
            String id,
            VulkanShaderStage stage,
            Map<String, String> defines,
            Map<String, String> replacements
    ) {
        Map<String, String> effectiveDefines = new HashMap<>(defines);
        // Preserve Shader.Builder.compile()'s implicit platform contract without touching OpenGL Capabilities.
        if (VoxyVulkanContext.get().capabilities().vendorId() == 0x8086) {
            effectiveDefines.putIfAbsent("IS_INTEL", "");
        }
        if (ThreadUtils.isWindows) {
            effectiveDefines.putIfAbsent("IS_WINDOWS", "");
        }
        String source = ShaderLoader.parse(id);
        if (source.contains("printf(")) {
            if (PrintfDebugUtil.ENABLE_PRINTF_DEBUGGING) {
                throw new UnsupportedOperationException(
                        "Vulkan shader printf is not migrated; disable voxy.enableShaderDebugPrintf"
                );
            }
            ShaderType originalStage = switch (stage) {
                case VERTEX -> ShaderType.VERTEX;
                case FRAGMENT -> ShaderType.FRAGMENT;
                case COMPUTE -> ShaderType.COMPUTE;
            };
            source = PrintfDebugUtil.PRINTF_processor.process(originalStage, source);
        }
        for (var replacement : replacements.entrySet()) {
            if (!source.contains(replacement.getKey())) {
                throw new IllegalArgumentException("Vulkan shader replacement token is missing in " + id + ": "
                        + replacement.getKey());
            }
            source = source.replace(replacement.getKey(), replacement.getValue());
        }
        String prepared = VulkanGlslPreprocessor.prepareForShaderc(source, effectiveDefines);
        String preprocessed = this.preprocess(id, stage, prepared);
        VulkanGlslPreprocessor.Result transformed = VulkanGlslPreprocessor.transformPreprocessed(preprocessed);
        return this.compileSource(id, stage, transformed);
    }

    private String preprocess(String id, VulkanShaderStage stage, String preparedSource) {
        long result = Shaderc.shaderc_compile_into_preprocessed_text(
                this.compiler, preparedSource, stage.shadercKind(), id, "main", this.options
        );
        if (result == 0L) throw new IllegalStateException("shaderc returned no preprocessing result for " + id);
        try {
            int status = Shaderc.shaderc_result_get_compilation_status(result);
            if (status != Shaderc.shaderc_compilation_status_success) {
                throw new IllegalArgumentException("Failed to preprocess Vulkan shader " + id + ":\n"
                        + Shaderc.shaderc_result_get_error_message(result));
            }
            return MemoryUtil.memUTF8(Shaderc.shaderc_result_get_bytes(result));
        } finally {
            Shaderc.shaderc_result_release(result);
        }
    }

    public VulkanShaderModule compileSource(
            String name,
            VulkanShaderStage stage,
            VulkanGlslPreprocessor.Result transformed
    ) {
        if (this.closed) throw new IllegalStateException("Voxy Vulkan shader compiler is closed");
        if (transformed.pushConstantSize() > VoxyVulkanContext.get().capabilities().maxPushConstantsSize()) {
            throw new IllegalArgumentException("Voxy shader " + name + " requires " + transformed.pushConstantSize()
                    + " push-constant bytes, device limit is "
                    + VoxyVulkanContext.get().capabilities().maxPushConstantsSize());
        }

        long result = Shaderc.shaderc_compile_into_spv(
                this.compiler, transformed.source(), stage.shadercKind(), name, "main", this.options
        );
        if (result == 0L) throw new IllegalStateException("shaderc returned no result for " + name);
        ByteBuffer copy = null;
        try {
            int status = Shaderc.shaderc_result_get_compilation_status(result);
            if (status != Shaderc.shaderc_compilation_status_success) {
                String error = Shaderc.shaderc_result_get_error_message(result);
                throw new IllegalArgumentException("Failed to compile Vulkan shader " + name + ":\n"
                        + error + sourceExcerpt(transformed.source(), error));
            }
            ByteBuffer spirv = Shaderc.shaderc_result_get_bytes(result);
            copy = MemoryUtil.memAlloc(spirv.remaining());
            copy.put(spirv).flip();
            VulkanShaderReflection reflection = VulkanShaderReflection.reflect(copy.duplicate());
            if (reflection.usesInt64()) {
                throw new IllegalStateException(
                        "Voxy Vulkan shader " + name
                                + " declared SPIR-V Int64 despite the required original ivec2 quad path"
                );
            }
            if (reflection.pushConstantSize() != transformed.pushConstantSize()) {
                throw new IllegalStateException("Push-constant reflection mismatch for " + name + ": source="
                        + transformed.pushConstantSize() + ", SPIR-V=" + reflection.pushConstantSize());
            }
            var sourceOffsets = transformed.pushConstants().stream()
                    .map(VulkanGlslPreprocessor.PushConstant::offset).toList();
            if (!reflection.pushConstantMemberOffsets().equals(sourceOffsets)) {
                throw new IllegalStateException("Push-constant member offsets changed for " + name
                        + ": source=" + sourceOffsets + ", SPIR-V=" + reflection.pushConstantMemberOffsets());
            }
            return new VulkanShaderModule(name, stage, copy, reflection, transformed.pushConstants());
        } finally {
            if (copy != null) MemoryUtil.memFree(copy);
            Shaderc.shaderc_result_release(result);
        }
    }

    private static String sourceExcerpt(String source, String error) {
        var matcher = Pattern.compile(":(\\d+):").matcher(error);
        if (!matcher.find()) return "";
        int target = Integer.parseInt(matcher.group(1));
        String[] lines = source.split("\\R", -1);
        int start = Math.max(1, target - 2);
        int end = Math.min(lines.length, target + 2);
        StringBuilder excerpt = new StringBuilder("\nTransformed source excerpt:\n");
        for (int line = start; line <= end; line++) {
            excerpt.append(line == target ? "> " : "  ").append(line).append(": ")
                    .append(lines[line - 1]).append('\n');
        }
        return excerpt.toString();
    }

    @Override
    public void close() {
        if (!this.closed) {
            this.closed = true;
            Shaderc.shaderc_compile_options_release(this.options);
            Shaderc.shaderc_compiler_release(this.compiler);
        }
    }
}
