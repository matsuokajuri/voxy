package me.cortex.voxy.forge;

import org.joml.Matrix4f;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import static org.lwjgl.opengl.GL11C.GL_FALSE;
import static org.lwjgl.opengl.GL11C.GL_LINEAR;
import static org.lwjgl.opengl.GL11C.GL_NEAREST;
import static org.lwjgl.opengl.GL11C.GL_NEAREST_MIPMAP_NEAREST;
import static org.lwjgl.opengl.GL11C.GL_TEXTURE_MAG_FILTER;
import static org.lwjgl.opengl.GL11C.GL_TEXTURE_MIN_FILTER;
import static org.lwjgl.opengl.GL11C.GL_TEXTURE_WRAP_S;
import static org.lwjgl.opengl.GL11C.GL_TEXTURE_WRAP_T;
import static org.lwjgl.opengl.GL11C.GL_TRUE;
import static org.lwjgl.opengl.GL12C.GL_CLAMP_TO_EDGE;
import static org.lwjgl.opengl.GL14C.GL_TEXTURE_COMPARE_MODE;
import static org.lwjgl.opengl.GL15C.GL_READ_WRITE;
import static org.lwjgl.opengl.GL20C.GL_COMPILE_STATUS;
import static org.lwjgl.opengl.GL20C.GL_LINK_STATUS;
import static org.lwjgl.opengl.GL20C.glAttachShader;
import static org.lwjgl.opengl.GL20C.glCompileShader;
import static org.lwjgl.opengl.GL20C.glCreateProgram;
import static org.lwjgl.opengl.GL20C.glCreateShader;
import static org.lwjgl.opengl.GL20C.glDeleteProgram;
import static org.lwjgl.opengl.GL20C.glDeleteShader;
import static org.lwjgl.opengl.GL20C.glGetProgramInfoLog;
import static org.lwjgl.opengl.GL20C.glGetProgrami;
import static org.lwjgl.opengl.GL20C.glGetShaderInfoLog;
import static org.lwjgl.opengl.GL20C.glGetShaderi;
import static org.lwjgl.opengl.GL20C.glLinkProgram;
import static org.lwjgl.opengl.GL20C.glUseProgram;
import static org.lwjgl.opengl.GL20C.nglShaderSource;
import static org.lwjgl.opengl.GL20C.nglUniformMatrix4fv;
import static org.lwjgl.opengl.GL30C.GL_DEPTH_ATTACHMENT;
import static org.lwjgl.opengl.GL30C.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME;
import static org.lwjgl.opengl.GL30C.GL_RGBA8;
import static org.lwjgl.opengl.GL33C.GL_NONE;
import static org.lwjgl.opengl.GL33C.glBindSampler;
import static org.lwjgl.opengl.GL33C.glDeleteSamplers;
import static org.lwjgl.opengl.GL33C.glSamplerParameteri;
import static org.lwjgl.opengl.GL43C.GL_COMPUTE_SHADER;
import static org.lwjgl.opengl.GL43C.glBindImageTexture;
import static org.lwjgl.opengl.GL43C.glDispatchCompute;
import static org.lwjgl.opengl.GL45C.glBindTextureUnit;
import static org.lwjgl.opengl.GL45C.glCreateSamplers;
import static org.lwjgl.opengl.GL45C.glGetNamedFramebufferAttachmentParameteri;

final class ForgeOriginalVoxySSAO {
    enum SSAOMode {
        AUTO,
        BASIC,
        BETTER,
        BEST
    }

    private final int programId;
    private final boolean betterSsao;
    private final int samples;
    private final int depthSamplerId;
    private long computeCount;

    static ForgeOriginalVoxySSAO create(ForgeOriginalVoxyRenderProperties properties, SSAOMode mode) {
        if (mode == SSAOMode.BASIC) {
            return new ForgeOriginalVoxySSAO(properties, false, 0);
        } else if (mode == SSAOMode.BETTER) {
            return new ForgeOriginalVoxySSAO(properties, true, 12);
        } else if (mode == SSAOMode.BEST) {
            return new ForgeOriginalVoxySSAO(properties, true, 24);
        } else if (mode == SSAOMode.AUTO) {
            ForgeOriginalVoxyGlCapabilities capabilities = ForgeOriginalVoxyGlCapabilities.INSTANCE;
            if (capabilities.canQueryGpuMemory) {
                if (capabilities.totalDedicatedMemory < 2_500_000_000L) {
                    return create(properties, SSAOMode.BASIC);
                } else if (capabilities.totalDedicatedMemory < 7_000_000_000L) {
                    return create(properties, SSAOMode.BETTER);
                }
                return create(properties, SSAOMode.BEST);
            } else if (capabilities.isAmd) {
                return create(properties, SSAOMode.BETTER);
            }
            return create(properties, SSAOMode.BASIC);
        }
        throw new IllegalArgumentException("Unknown SSAO mode " + mode);
    }

    private ForgeOriginalVoxySSAO(ForgeOriginalVoxyRenderProperties properties, boolean betterSsao, int samples) {
        this.betterSsao = betterSsao;
        this.samples = samples;
        this.depthSamplerId = glCreateSamplers();
        try {
            String source = properties.injectDefines(ForgeOriginalVoxyShaderSource.parse("voxy:post/ssao.comp"));
            if (betterSsao) {
                source = injectDefines(source,
                        "BETTER_SSAO",
                        samples == 0 ? null : "SSAO_STEPS " + samples,
                        "USE_GENERATED_SAMPLE_POINTS");
                source = source.replace("%%CONST_ARRAY%%", samplePointArray(samples));
            }
            this.programId = compileComputeProgram(source);
            if (betterSsao) {
                glSamplerParameteri(this.depthSamplerId, GL_TEXTURE_MIN_FILTER, GL_NEAREST_MIPMAP_NEAREST);
                glSamplerParameteri(this.depthSamplerId, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
            } else {
                glSamplerParameteri(this.depthSamplerId, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
                glSamplerParameteri(this.depthSamplerId, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
            }
            glSamplerParameteri(this.depthSamplerId, GL_TEXTURE_COMPARE_MODE, GL_NONE);
            glSamplerParameteri(this.depthSamplerId, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
            glSamplerParameteri(this.depthSamplerId, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        } catch (RuntimeException e) {
            glDeleteSamplers(this.depthSamplerId);
            throw e;
        }
    }

    void compute(
            ForgeOriginalVoxyMdicViewport viewport,
            int colourOutTextureId,
            int colourInTextureId,
            int baseDepthTextureId,
            int sourceFramebuffer) {
        glUseProgram(this.programId);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            long ptr = stack.nmalloc(4 * 4 * 4);
            Matrix4f scratch = new Matrix4f();
            if (this.betterSsao) {
                viewport.projection.getToAddress(ptr);
                nglUniformMatrix4fv(4, 1, false, ptr);
                viewport.projection.invert(scratch).getToAddress(ptr);
                nglUniformMatrix4fv(5, 1, false, ptr);
                viewport.modelView.getToAddress(ptr);
                nglUniformMatrix4fv(6, 1, false, ptr);
                viewport.vanillaProjection.invert(scratch).getToAddress(ptr);
                nglUniformMatrix4fv(7, 1, false, ptr);
            } else {
                viewport.MVP.getToAddress(ptr);
                nglUniformMatrix4fv(3, 1, false, ptr);
                viewport.MVP.invert(scratch).getToAddress(ptr);
                nglUniformMatrix4fv(4, 1, false, ptr);
            }
        }

        glBindImageTexture(0, colourOutTextureId, 0, false, 0, GL_READ_WRITE, GL_RGBA8);
        glBindTextureUnit(1, colourInTextureId);
        glBindSampler(1, 0);
        glBindTextureUnit(2, baseDepthTextureId);
        glBindSampler(2, this.depthSamplerId);

        if (this.betterSsao) {
            int depthTexture = glGetNamedFramebufferAttachmentParameteri(
                    sourceFramebuffer,
                    GL_DEPTH_ATTACHMENT,
                    GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME);
            glBindTextureUnit(3, depthTexture);
            glBindSampler(3, this.depthSamplerId);
        }

        glDispatchCompute((viewport.width + 7) / 8, (viewport.height + 7) / 8, 1);

        glBindTextureUnit(1, 0);
        glBindSampler(1, 0);
        glBindTextureUnit(2, 0);
        glBindSampler(2, 0);
        glBindTextureUnit(3, 0);
        glBindSampler(3, 0);
        this.computeCount++;
    }

    boolean ready() {
        return this.programId != 0 && this.depthSamplerId != 0;
    }

    long computeCount() {
        return this.computeCount;
    }

    String modeName() {
        return this.betterSsao ? "new (" + this.samples + " spp)" : "basic";
    }

    void free() {
        glDeleteSamplers(this.depthSamplerId);
        glDeleteProgram(this.programId);
    }

    private static String samplePointArray(int samples) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < samples; i++) {
            float a = (((float) i) + 0.5f) * (1.0f / samples);
            float base = (float) (i * (1.0 / 1.6180339887) + 0.5);
            float r = (float) Math.sqrt(base % 1);
            float theta = a * 6.2831853f;
            if (i > 0) {
                builder.append(", ");
            }
            builder.append("vec2(")
                    .append((float) (r * Math.cos(theta)))
                    .append("f, ")
                    .append((float) (r * Math.sin(theta)))
                    .append("f)");
        }
        return builder.toString();
    }

    private static String injectDefines(String source, String... defines) {
        StringBuilder builder = new StringBuilder();
        for (String define : defines) {
            if (define != null) {
                builder.append("#define ").append(define).append('\n');
            }
        }
        int split = source.indexOf('\n');
        if (split < 0) {
            return source + '\n' + builder;
        }
        return source.substring(0, split + 1) + builder + source.substring(split + 1);
    }

    private static int compileComputeProgram(String source) {
        int shader = glCreateShader(GL_COMPUTE_SHADER);
        long ptr = MemoryUtil.memAddress(MemoryUtil.memUTF8(source, true));
        try (MemoryStack stack = MemoryStack.stackPush()) {
            nglShaderSource(shader, 1, stack.pointers(ptr).address0(), 0L);
        } finally {
            MemoryUtil.nmemFree(ptr);
        }
        glCompileShader(shader);
        if (glGetShaderi(shader, GL_COMPILE_STATUS) == GL_FALSE) {
            String log = glGetShaderInfoLog(shader);
            glDeleteShader(shader);
            throw new IllegalStateException("Original SSAO shader compile failed: " + log);
        }
        int program = glCreateProgram();
        glAttachShader(program, shader);
        glLinkProgram(program);
        glDeleteShader(shader);
        if (glGetProgrami(program, GL_LINK_STATUS) != GL_TRUE) {
            String log = glGetProgramInfoLog(program);
            glDeleteProgram(program);
            throw new IllegalStateException("Original SSAO shader link failed: " + log);
        }
        return program;
    }
}
