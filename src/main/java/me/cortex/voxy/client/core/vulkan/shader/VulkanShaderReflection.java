package me.cortex.voxy.client.core.vulkan.shader;

import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.util.spvc.Spvc;
import org.lwjgl.util.spvc.SpvcReflectedResource;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public record VulkanShaderReflection(
        List<Descriptor> descriptors,
        int pushConstantSize,
        List<Integer> pushConstantMemberOffsets,
        List<Integer> declaredCapabilities
) {
    private static final int SPV_DECORATION_BINDING = 33;
    private static final int SPV_DECORATION_DESCRIPTOR_SET = 34;
    private static final int SPV_CAPABILITY_INT64 = 11;

    public static VulkanShaderReflection reflect(ByteBuffer spirv) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            PointerBuffer pointer = stack.callocPointer(1);
            check(Spvc.spvc_context_create(pointer), "create SPIRV-Cross context");
            long context = pointer.get(0);
            try {
                check(Spvc.spvc_context_parse_spirv(context, spirv.asIntBuffer(), spirv.remaining() / 4, pointer),
                        "parse SPIR-V");
                long ir = pointer.get(0);
                check(Spvc.spvc_context_create_compiler(context, 0, ir, 1, pointer), "create SPIR-V reflector");
                long compiler = pointer.get(0);
                check(Spvc.spvc_compiler_create_shader_resources(compiler, pointer), "enumerate SPIR-V resources");
                long resources = pointer.get(0);

                List<Descriptor> descriptors = new ArrayList<>();
                for (VulkanDescriptorKind kind : VulkanDescriptorKind.values()) {
                    collectDescriptors(stack, compiler, resources, kind, descriptors);
                }
                validateDescriptorBindings(descriptors);
                PushConstantReflection pushConstants = collectPushConstants(stack, compiler, resources);
                List<Integer> capabilities = collectDeclaredCapabilities(stack, compiler);
                return new VulkanShaderReflection(
                        List.copyOf(descriptors),
                        pushConstants.size(),
                        pushConstants.memberOffsets(),
                        capabilities
                );
            } finally {
                Spvc.spvc_context_destroy(context);
            }
        }
    }

    private static List<Integer> collectDeclaredCapabilities(MemoryStack stack, long compiler) {
        PointerBuffer listPointer = stack.callocPointer(1);
        PointerBuffer countPointer = stack.callocPointer(1);
        check(Spvc.spvc_compiler_get_declared_capabilities(compiler, listPointer, countPointer),
                "list declared SPIR-V capabilities");
        int count = Math.toIntExact(countPointer.get(0));
        if (count == 0) return List.of();
        IntBuffer capabilities = MemoryUtil.memIntBuffer(listPointer.get(0), count);
        List<Integer> result = new ArrayList<>(count);
        for (int i = 0; i < count; i++) result.add(capabilities.get(i));
        return List.copyOf(result);
    }

    public boolean usesInt64() {
        return this.declaredCapabilities.contains(SPV_CAPABILITY_INT64);
    }

    private static void collectDescriptors(
            MemoryStack stack,
            long compiler,
            long resources,
            VulkanDescriptorKind kind,
            List<Descriptor> output
    ) {
        PointerBuffer listPointer = stack.callocPointer(1);
        PointerBuffer countPointer = stack.callocPointer(1);
        check(Spvc.spvc_resources_get_resource_list_for_type(
                resources, kind.spvcResourceType(), listPointer, countPointer
        ), "list " + kind + " resources");
        int count = Math.toIntExact(countPointer.get(0));
        SpvcReflectedResource.Buffer list = SpvcReflectedResource.create(listPointer.get(0), count);
        for (int i = 0; i < count; i++) {
            SpvcReflectedResource resource = list.get(i);
            int set = Spvc.spvc_compiler_get_decoration(compiler, resource.id(), SPV_DECORATION_DESCRIPTOR_SET);
            if (set != 0) throw new IllegalStateException("Voxy descriptor is not in set 0: " + resource.nameString());
            int binding = Spvc.spvc_compiler_get_decoration(compiler, resource.id(), SPV_DECORATION_BINDING);
            output.add(new Descriptor(resource.nameString(), binding, kind, reflectedMemberOffsets(stack, compiler, resource)));
        }
    }

    private static List<Integer> reflectedMemberOffsets(
            MemoryStack stack,
            long compiler,
            SpvcReflectedResource resource
    ) {
        long type = Spvc.spvc_compiler_get_type_handle(compiler, resource.base_type_id());
        int members = Spvc.spvc_type_get_num_member_types(type);
        if (members == 0) return List.of();
        IntBuffer offset = stack.callocInt(1);
        List<Integer> offsets = new ArrayList<>(members);
        for (int i = 0; i < members; i++) {
            check(Spvc.spvc_compiler_type_struct_member_offset(compiler, type, i, offset),
                    "reflect member offset for " + resource.nameString());
            offsets.add(offset.get(0));
        }
        return List.copyOf(offsets);
    }

    private static PushConstantReflection collectPushConstants(MemoryStack stack, long compiler, long resources) {
        PointerBuffer listPointer = stack.callocPointer(1);
        PointerBuffer countPointer = stack.callocPointer(1);
        check(Spvc.spvc_resources_get_resource_list_for_type(
                resources, Spvc.SPVC_RESOURCE_TYPE_PUSH_CONSTANT, listPointer, countPointer
        ), "list push constants");
        int count = Math.toIntExact(countPointer.get(0));
        if (count == 0) return new PushConstantReflection(0, List.of());
        if (count != 1) throw new IllegalStateException("Voxy shader has more than one push-constant block");
        SpvcReflectedResource resource = SpvcReflectedResource.create(listPointer.get(0));
        long type = Spvc.spvc_compiler_get_type_handle(compiler, resource.base_type_id());
        PointerBuffer size = stack.callocPointer(1);
        check(Spvc.spvc_compiler_get_declared_struct_size(compiler, type, size), "reflect push-constant size");
        int members = Spvc.spvc_type_get_num_member_types(type);
        IntBuffer offset = stack.callocInt(1);
        List<Integer> offsets = new ArrayList<>(members);
        for (int i = 0; i < members; i++) {
            check(Spvc.spvc_compiler_type_struct_member_offset(compiler, type, i, offset),
                    "reflect push-constant member offset");
            offsets.add(offset.get(0));
        }
        return new PushConstantReflection(Math.toIntExact(size.get(0)), List.copyOf(offsets));
    }

    private static void validateDescriptorBindings(List<Descriptor> descriptors) {
        Map<Integer, Descriptor> bindings = new HashMap<>();
        for (Descriptor descriptor : descriptors) {
            Descriptor previous = bindings.putIfAbsent(descriptor.binding(), descriptor);
            if (previous != null) {
                throw new IllegalStateException("Vulkan descriptor binding " + descriptor.binding()
                        + " is declared more than once: " + previous.name() + " and " + descriptor.name());
            }
        }
    }

    private static void check(int result, String operation) {
        if (result != Spvc.SPVC_SUCCESS) {
            throw new IllegalStateException("Failed to " + operation + " (SPIRV-Cross error " + result + ")");
        }
    }

    public record Descriptor(
            String name,
            int binding,
            VulkanDescriptorKind kind,
            List<Integer> memberOffsets
    ) {
    }

    private record PushConstantReflection(int size, List<Integer> memberOffsets) {
    }
}
