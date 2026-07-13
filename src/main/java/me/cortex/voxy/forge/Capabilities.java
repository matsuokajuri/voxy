package me.cortex.voxy.forge;

import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL20C;
import org.lwjgl.opengl.GL30C;
import org.lwjgl.system.MemoryUtil;

import java.util.Locale;

import static org.lwjgl.opengl.GL11.GL_FLOAT;
import static org.lwjgl.opengl.GL11.GL_NEAREST;
import static org.lwjgl.opengl.GL11.GL_RED;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_2D;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_MAG_FILTER;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_MIN_FILTER;
import static org.lwjgl.opengl.GL11.GL_UNSIGNED_INT;
import static org.lwjgl.opengl.GL11C.GL_VENDOR;
import static org.lwjgl.opengl.GL11C.GL_VERSION;
import static org.lwjgl.opengl.GL11C.glGetInteger;
import static org.lwjgl.opengl.GL11C.glGetString;
import static org.lwjgl.opengl.GL15C.GL_READ_ONLY;
import static org.lwjgl.opengl.GL15C.glBindBuffer;
import static org.lwjgl.opengl.GL15C.glDeleteBuffers;
import static org.lwjgl.opengl.GL20C.GL_COMPILE_STATUS;
import static org.lwjgl.opengl.GL20C.GL_TRUE;
import static org.lwjgl.opengl.GL20C.glDeleteProgram;
import static org.lwjgl.opengl.GL20C.glDeleteShader;
import static org.lwjgl.opengl.GL20C.glUseProgram;
import static org.lwjgl.opengl.GL30C.GL_DEPTH24_STENCIL8;
import static org.lwjgl.opengl.GL30C.GL_DEPTH_STENCIL_ATTACHMENT;
import static org.lwjgl.opengl.GL30C.GL_R32F;
import static org.lwjgl.opengl.GL30C.GL_RED_INTEGER;
import static org.lwjgl.opengl.GL30C.glBindBufferBase;
import static org.lwjgl.opengl.GL32C.glGetInteger64;
import static org.lwjgl.opengl.GL43C.GL_COMPUTE_SHADER;
import static org.lwjgl.opengl.GL43C.GL_MAX_SHADER_STORAGE_BLOCK_SIZE;
import static org.lwjgl.opengl.GL43C.GL_SHADER_STORAGE_BUFFER;
import static org.lwjgl.opengl.GL43C.GL_SHADER_STORAGE_BUFFER_OFFSET_ALIGNMENT;
import static org.lwjgl.opengl.GL43C.glDispatchCompute;
import static org.lwjgl.opengl.GL45C.GL_DYNAMIC_STORAGE_BIT;
import static org.lwjgl.opengl.GL45C.GL_MAP_READ_BIT;
import static org.lwjgl.opengl.GL45C.glBindTextureUnit;
import static org.lwjgl.opengl.GL45C.glClearNamedFramebufferfi;
import static org.lwjgl.opengl.GL45C.glCreateBuffers;
import static org.lwjgl.opengl.GL45C.glCreateFramebuffers;
import static org.lwjgl.opengl.GL45C.glCreateTextures;
import static org.lwjgl.opengl.GL45C.glDeleteFramebuffers;
import static org.lwjgl.opengl.GL45C.glDeleteTextures;
import static org.lwjgl.opengl.GL45C.glFinish;
import static org.lwjgl.opengl.GL45C.glNamedBufferStorage;
import static org.lwjgl.opengl.GL45C.glNamedFramebufferTexture;
import static org.lwjgl.opengl.GL45C.glTextureParameteri;
import static org.lwjgl.opengl.GL45C.glTextureStorage2D;
import static org.lwjgl.opengl.GL45C.glUnmapNamedBuffer;
import static org.lwjgl.opengl.GL45C.nglClearNamedBufferSubData;
import static org.lwjgl.opengl.GL45C.nglMapNamedBuffer;
import static org.lwjgl.opengl.NVXGPUMemoryInfo.GL_GPU_MEMORY_INFO_DEDICATED_VIDMEM_NVX;

final class Capabilities {
    static final Capabilities INSTANCE = new Capabilities();

    final boolean canQueryGpuMemory;
    final boolean repFragTest;
    final boolean meshShaders;
    final boolean INT64_t;
    final long totalDedicatedMemory;
    final long totalDynamicMemory;
    final long ssboMaxSize;
    final int ssboBindingAlignment;
    final boolean compute;
    final boolean indirectParameters;
    final boolean subgroup;
    final boolean sparseBuffer;
    final boolean isIntel;
    final boolean isNvidia;
    final boolean isAmd;
    final boolean isMesa;
    final boolean nvBarryCoords;
    final boolean hasBrokenDepthSampler;

    private Capabilities() {
        var capabilities = GL.getCapabilities();
        this.compute = capabilities.glDispatchComputeIndirect != 0L;
        this.indirectParameters = capabilities.glMultiDrawElementsIndirectCountARB != 0L;
        this.sparseBuffer = capabilities.GL_ARB_sparse_buffer;
        this.repFragTest = capabilities.GL_NV_representative_fragment_test;
        this.meshShaders = capabilities.GL_NV_mesh_shader;
        this.canQueryGpuMemory = capabilities.GL_NVX_gpu_memory_info;
        this.INT64_t = testShaderCompiles("""
                #version 430
                #extension GL_ARB_gpu_shader_int64 : require
                layout(local_size_x=32) in;
                void main() {
                    uint64_t a = 1234;
                }
                """);
        String vendor = glGetString(GL_VENDOR);
        String normalizedVendor = vendor == null ? "" : vendor.toLowerCase(Locale.ROOT);
        String version = glGetString(GL_VERSION);
        this.isMesa = version != null && version.toLowerCase(Locale.ROOT).contains("mesa");
        this.isIntel = normalizedVendor.contains("intel");
        this.isNvidia = normalizedVendor.contains("nvidia");
        this.isAmd = normalizedVendor.contains("amd") || normalizedVendor.contains("radeon");
        this.nvBarryCoords = capabilities.GL_NV_fragment_shader_barycentric;
        this.ssboMaxSize = glGetInteger64(GL_MAX_SHADER_STORAGE_BLOCK_SIZE);
        this.ssboBindingAlignment = glGetInteger(GL_SHADER_STORAGE_BUFFER_OFFSET_ALIGNMENT);
        if (this.canQueryGpuMemory) {
            this.totalDedicatedMemory = glGetInteger64(GL_GPU_MEMORY_INFO_DEDICATED_VIDMEM_NVX) * 1024L;
            this.totalDynamicMemory =
                    glGetInteger64(org.lwjgl.opengl.NVXGPUMemoryInfo.GL_GPU_MEMORY_INFO_TOTAL_AVAILABLE_MEMORY_NVX) * 1024L
                            - this.totalDedicatedMemory;
        } else {
            this.totalDedicatedMemory = -1L;
            this.totalDynamicMemory = -1L;
        }
        this.subgroup = capabilities.GL_KHR_shader_subgroup
                && testShaderCompiles("""
                #version 430
                #extension GL_KHR_shader_subgroup_basic : require
                #extension GL_KHR_shader_subgroup_arithmetic : require
                layout(local_size_x=32) in;
                void main() {
                    uint a = subgroupExclusiveAdd(gl_LocalInvocationIndex);
                }
                """);
        this.hasBrokenDepthSampler = this.compute && this.isAmd && testDepthSampler();
    }

    static void init() {
    }

    long getFreeDedicatedGpuMemory() {
        if (!this.canQueryGpuMemory) {
            throw new IllegalStateException("Cannot query gpu memory, missing extension");
        }
        return glGetInteger64(
                org.lwjgl.opengl.NVXGPUMemoryInfo.GL_GPU_MEMORY_INFO_CURRENT_AVAILABLE_VIDMEM_NVX) * 1024L;
    }

    boolean systemSupported() {
        return this.compute && this.indirectParameters && !this.hasBrokenDepthSampler;
    }

    String unsupportedReason() {
        if (this.hasBrokenDepthSampler) {
            return "broken-amd-depth-sampler";
        }
        if (!this.compute) {
            return "compute-shaders-unavailable";
        }
        if (!this.indirectParameters) {
            return "indirect-parameters-unavailable";
        }
        return "none";
    }

    private static boolean testShaderCompiles(String source) {
        int shader = GL20C.glCreateShader(GL_COMPUTE_SHADER);
        GL20C.glShaderSource(shader, source);
        GL20C.glCompileShader(shader);
        boolean compiled = GL20C.glGetShaderi(shader, GL_COMPILE_STATUS) == GL_TRUE;
        GL20C.glDeleteShader(shader);
        return compiled;
    }

    private static boolean testDepthSampler() {
        String source = """
                #version 460 core
                layout(local_size_x=16,local_size_y=16) in;

                layout(binding = 0) uniform sampler2D depthSampler;
                layout(binding = 1) buffer OutData {
                    float[] outData;
                };

                layout(location = 2) uniform int dynamicSampleThing;
                layout(location = 3) uniform float sampleData;

                void main() {
                    if (abs(texelFetch(depthSampler, ivec2(gl_GlobalInvocationID.xy), dynamicSampleThing).r-sampleData)>0.000001f) {
                        outData[0] = 1.0;
                    }
                }
                """;
        int shader = GL20C.glCreateShader(GL_COMPUTE_SHADER);
        GL20C.glShaderSource(shader, source);
        GL20C.glCompileShader(shader);
        if (GL20C.glGetShaderi(shader, GL_COMPILE_STATUS) != GL_TRUE) {
            String log = GL20C.glGetShaderInfoLog(shader);
            GL20C.glDeleteShader(shader);
            throw new IllegalStateException("Depth-sampler probe shader compilation failed: " + log);
        }
        int program = GL20C.glCreateProgram();
        GL20C.glAttachShader(program, shader);
        GL20C.glLinkProgram(program);
        GL20C.glDeleteShader(shader);

        int buffer = glCreateBuffers();
        glNamedBufferStorage(buffer, 4096L, GL_DYNAMIC_STORAGE_BIT | GL_MAP_READ_BIT);
        int texture = glCreateTextures(GL_TEXTURE_2D);
        glTextureStorage2D(texture, 2, GL_DEPTH24_STENCIL8, 256, 256);
        glTextureParameteri(texture, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTextureParameteri(texture, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        int framebuffer = glCreateFramebuffers();
        boolean correct = true;
        try {
            for (int level = 0; level <= 1; level++) {
                glNamedFramebufferTexture(framebuffer, GL_DEPTH_STENCIL_ATTACHMENT, texture, level);
                for (int i = 0; i <= 10; i++) {
                    float value = i / 10.0F;
                    nglClearNamedBufferSubData(buffer, GL_R32F, 0L, 4096L, GL_RED, GL_FLOAT, 0L);
                    glClearNamedFramebufferfi(framebuffer, GL30C.GL_DEPTH_STENCIL, 0, value, 1);
                    glUseProgram(program);
                    GL20C.glUniform1i(2, level);
                    GL20C.glUniform1f(3, value);
                    glBindTextureUnit(0, texture);
                    glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 1, buffer);
                    glDispatchCompute(256 >> (level + 4), 256 >> (level + 4), 1);
                    glFinish();
                    long pointer = nglMapNamedBuffer(buffer, GL_READ_ONLY);
                    float result = MemoryUtil.memGetFloat(pointer);
                    glUnmapNamedBuffer(buffer);
                    glUseProgram(0);
                    glBindTextureUnit(0, 0);
                    glBindBuffer(GL_SHADER_STORAGE_BUFFER, 0);
                    correct &= result == 0.0F;
                }
            }
        } finally {
            glDeleteFramebuffers(framebuffer);
            glDeleteTextures(texture);
            glDeleteBuffers(buffer);
            glDeleteProgram(program);
        }
        return !correct;
    }
}
