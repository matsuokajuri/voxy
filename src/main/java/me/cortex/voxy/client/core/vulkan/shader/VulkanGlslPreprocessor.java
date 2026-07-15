package me.cortex.voxy.client.core.vulkan.shader;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Preserves Voxy's source/include/define contract while translating GL namespaces to Vulkan. */
public final class VulkanGlslPreprocessor {
    private static final Pattern BINDING_LAYOUT = Pattern.compile(
            "^(\\s*)layout\\(([^)]*?)binding\\s*=\\s*([0-9]+)([^)]*)\\)(.*)$"
    );
    private static final Pattern LOCATION_UNIFORM = Pattern.compile(
            "^\\s*layout\\(\\s*location\\s*=\\s*([0-9]+)\\s*\\)"
                    + "\\s*uniform\\s+([A-Za-z_][A-Za-z0-9_]*)\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*;\\s*$"
    );
    private static final Pattern PLAIN_SAMPLER = Pattern.compile(
            "^(\\s*)uniform\\s+(sampler[A-Za-z0-9_]*)\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*;\\s*$"
    );

    private VulkanGlslPreprocessor() {
    }

    /** Injects the original builder defines before shaderc resolves conditionals and symbolic bindings. */
    public static String prepareForShaderc(String source, Map<String, String> externalDefines) {
        StringBuilder injected = new StringBuilder();
        externalDefines.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> injected
                .append("#define ").append(entry.getKey()).append(' ').append(entry.getValue()).append('\n'));
        injected.append("#define gl_VertexID gl_VertexIndex\n");
        // OpenGL gl_InstanceID excludes baseInstance while Vulkan gl_InstanceIndex includes it.
        // Keep gl_BaseInstance separate so shaders that explicitly add it preserve their original indexing.
        injected.append("#define gl_InstanceID (gl_InstanceIndex - gl_BaseInstance)\n");

        List<String> lines = new ArrayList<>(List.of(source.split("\\R", -1)));
        int versionLine = findVersionLine(lines);
        lines.add(versionLine + 1, injected.toString());
        return String.join("\n", lines);
    }

    /** Translates fully preprocessed GLSL, so inactive branches cannot create overlapping push members. */
    public static Result transformPreprocessed(String source) {

        List<PushUniform> pushUniforms = new ArrayList<>();
        List<String> transformedLines = new ArrayList<>();
        int unnamedSamplerBinding = 0;
        for (String line : source.split("\\R", -1)) {
            // Minecraft 26.2 VulkanRenderPass always records viewport minDepth=0 and maxDepth=1.
            // GLSL's OpenGL-only gl_DepthRange block is therefore the exact constants below.
            line = line.replace("gl_DepthRange.diff", "1.0f")
                    .replace("gl_DepthRange.near", "0.0f")
                    .replace("gl_DepthRange.far", "1.0f");
            Matcher location = LOCATION_UNIFORM.matcher(line);
            if (location.matches()) {
                int originalLocation = Integer.parseInt(location.group(1));
                pushUniforms.add(new PushUniform(originalLocation, location.group(2), location.group(3)));
                continue;
            }

            Matcher binding = BINDING_LAYOUT.matcher(line);
            if (binding.matches()) {
                int originalBinding = Integer.parseInt(binding.group(3));
                VulkanDescriptorKind kind = classifyDescriptor(binding.group(5));
                int mappedBinding = kind.mapOriginalBinding(originalBinding);
                String qualifiers = normalizeQualifiers(binding.group(2), binding.group(4));
                transformedLines.add(binding.group(1) + "layout(set = 0, binding = " + mappedBinding
                        + (qualifiers.isEmpty() ? "" : ", " + qualifiers) + ")" + binding.group(5));
                continue;
            }

            Matcher sampler = PLAIN_SAMPLER.matcher(line);
            if (sampler.matches()) {
                int mappedBinding = VulkanDescriptorKind.COMBINED_IMAGE_SAMPLER
                        .mapOriginalBinding(unnamedSamplerBinding++);
                transformedLines.add(sampler.group(1) + "layout(set = 0, binding = " + mappedBinding + ") uniform "
                        + sampler.group(2) + " " + sampler.group(3) + ";");
                continue;
            }
            transformedLines.add(line);
        }

        pushUniforms.sort(Comparator.comparingInt(PushUniform::location));
        int pushConstantSize = 0;
        List<PushConstant> pushLayout = new ArrayList<>();
        StringBuilder injected = new StringBuilder();
        if (!pushUniforms.isEmpty()) {
            injected.append("layout(push_constant, std430) uniform VoxyPushConstants {\n");
            for (PushUniform uniform : pushUniforms) {
                int alignment = typeAlignment(uniform.type());
                int offset = alignUp(pushConstantSize, alignment);
                int size = typeSize(uniform.type());
                injected.append("    layout(offset = ").append(offset).append(") ")
                        .append(uniform.type()).append(" pc_").append(uniform.name()).append(";\n");
                pushLayout.add(new PushConstant(uniform.name(), uniform.location(), uniform.type(), offset, size));
                pushConstantSize = offset + size;
            }
            injected.append("} voxyPush;\n");
            for (PushUniform uniform : pushUniforms) {
                injected.append("#define ").append(uniform.name()).append(" voxyPush.pc_")
                        .append(uniform.name()).append('\n');
            }
        }

        int versionLine = findVersionLine(transformedLines);
        transformedLines.add(versionLine + 1, injected.toString());
        return new Result(String.join("\n", transformedLines), pushConstantSize, List.copyOf(pushLayout));
    }

    private static int findVersionLine(List<String> lines) {
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).stripLeading().startsWith("#version")) return i;
        }
        throw new IllegalArgumentException("Voxy shader source has no #version directive");
    }

    private static String normalizeQualifiers(String beforeBinding, String afterBinding) {
        Set<String> qualifiers = new LinkedHashSet<>();
        for (String token : (beforeBinding + "," + afterBinding).split(",")) {
            String qualifier = token.trim();
            if (qualifier.isEmpty() || qualifier.matches("set\\s*=.*") || qualifier.matches("binding\\s*=.*")) continue;
            qualifiers.add(qualifier);
        }
        return String.join(", ", qualifiers);
    }

    private static VulkanDescriptorKind classifyDescriptor(String declaration) {
        if (Pattern.compile("\\bsampler[A-Za-z0-9_]*\\b").matcher(declaration).find()) {
            return VulkanDescriptorKind.COMBINED_IMAGE_SAMPLER;
        }
        if (Pattern.compile("\\b[ui]?image[A-Za-z0-9_]*\\b").matcher(declaration).find()) {
            return VulkanDescriptorKind.STORAGE_IMAGE;
        }
        if (Pattern.compile("\\bbuffer\\b").matcher(declaration).find()) {
            return VulkanDescriptorKind.STORAGE_BUFFER;
        }
        if (Pattern.compile("\\buniform\\b").matcher(declaration).find()) {
            return VulkanDescriptorKind.UNIFORM_BUFFER;
        }
        throw new IllegalArgumentException("Cannot classify Vulkan descriptor declaration: " + declaration);
    }

    private static int typeSize(String type) {
        return switch (type) {
            case "bool", "int", "uint", "float" -> 4;
            case "bvec2", "ivec2", "uvec2", "vec2" -> 8;
            case "bvec3", "bvec4", "ivec3", "ivec4", "uvec3", "uvec4", "vec3", "vec4" -> 16;
            case "mat2" -> 32;
            case "mat3" -> 48;
            case "mat4" -> 64;
            default -> throw new IllegalArgumentException("Unsupported GL uniform type for Vulkan push constants: " + type);
        };
    }

    private static int typeAlignment(String type) {
        return switch (type) {
            case "bool", "int", "uint", "float" -> 4;
            case "bvec2", "ivec2", "uvec2", "vec2" -> 8;
            case "bvec3", "bvec4", "ivec3", "ivec4", "uvec3", "uvec4", "vec3", "vec4",
                    "mat2", "mat3", "mat4" -> 16;
            default -> throw new IllegalArgumentException("Unsupported GL uniform type for Vulkan push constants: " + type);
        };
    }

    private static int alignUp(int value, int alignment) {
        return Math.addExact(value, alignment - 1) & -alignment;
    }

    private record PushUniform(int location, String type, String name) {
    }

    public record PushConstant(String name, int originalLocation, String type, int offset, int size) {
    }

    public record Result(String source, int pushConstantSize, List<PushConstant> pushConstants) {
    }
}
