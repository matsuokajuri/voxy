package me.cortex.voxy.forge;

import me.cortex.voxy.common.util.TrackedObject;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import static org.lwjgl.opengl.ARBShaderImageLoadStore.GL_TEXTURE_FETCH_BARRIER_BIT;
import static org.lwjgl.opengl.GL11.GL_FALSE;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_MAG_FILTER;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_MIN_FILTER;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_WRAP_S;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_WRAP_T;
import static org.lwjgl.opengl.GL11.GL_TRUE;
import static org.lwjgl.opengl.GL11C.GL_ALWAYS;
import static org.lwjgl.opengl.GL11C.GL_DEPTH_TEST;
import static org.lwjgl.opengl.GL11C.GL_NONE;
import static org.lwjgl.opengl.GL11C.GL_TEXTURE_2D;
import static org.lwjgl.opengl.GL11C.glDepthFunc;
import static org.lwjgl.opengl.GL11C.glDepthMask;
import static org.lwjgl.opengl.GL11C.glDisable;
import static org.lwjgl.opengl.GL11C.glEnable;
import static org.lwjgl.opengl.GL11C.glGetInteger;
import static org.lwjgl.opengl.GL11C.glViewport;
import static org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE;
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
import static org.lwjgl.opengl.GL20C.glUniform1i;
import static org.lwjgl.opengl.GL20C.nglShaderSource;
import static org.lwjgl.opengl.GL20C.glUseProgram;
import static org.lwjgl.opengl.GL30C.GL_DEPTH_ATTACHMENT;
import static org.lwjgl.opengl.GL30C.GL_DEPTH24_STENCIL8;
import static org.lwjgl.opengl.GL30C.GL_DRAW_FRAMEBUFFER_BINDING;
import static org.lwjgl.opengl.GL30C.GL_FRAMEBUFFER;
import static org.lwjgl.opengl.GL30C.GL_FRAMEBUFFER_COMPLETE;
import static org.lwjgl.opengl.GL30C.glBindFramebuffer;
import static org.lwjgl.opengl.GL30C.glBindVertexArray;
import static org.lwjgl.opengl.GL30C.glDrawArrays;
import static org.lwjgl.opengl.GL33C.GL_NEAREST_MIPMAP_NEAREST;
import static org.lwjgl.opengl.GL33C.GL_TEXTURE_COMPARE_MODE;
import static org.lwjgl.opengl.GL33C.glBindSampler;
import static org.lwjgl.opengl.GL33C.glDeleteSamplers;
import static org.lwjgl.opengl.GL33C.glGenSamplers;
import static org.lwjgl.opengl.GL33C.glSamplerParameteri;
import static org.lwjgl.opengl.GL42C.GL_FRAMEBUFFER_BARRIER_BIT;
import static org.lwjgl.opengl.GL42C.glMemoryBarrier;
import static org.lwjgl.opengl.GL45C.glBindTextureUnit;
import static org.lwjgl.opengl.GL45C.glCheckNamedFramebufferStatus;
import static org.lwjgl.opengl.GL45C.glCreateFramebuffers;
import static org.lwjgl.opengl.GL45C.glCreateTextures;
import static org.lwjgl.opengl.GL45C.glDeleteFramebuffers;
import static org.lwjgl.opengl.GL45C.glDeleteTextures;
import static org.lwjgl.opengl.GL45C.glNamedFramebufferDrawBuffer;
import static org.lwjgl.opengl.GL45C.glNamedFramebufferTexture;
import static org.lwjgl.opengl.GL45C.glTextureBarrier;
import static org.lwjgl.opengl.GL45C.glTextureParameteri;
import static org.lwjgl.opengl.GL45C.glTextureStorage2D;

final class HiZBuffer extends TrackedObject {
    private final RenderProperties properties;
    private final int type;
    private final int framebufferId = glCreateFramebuffers();
    private final int samplerId = glGenSamplers();
    private final int programId;
    private int textureId;
    private int levels;
    private int width;
    private int height;

    HiZBuffer(RenderProperties properties) {
        this(properties, GL_DEPTH24_STENCIL8);
    }

    HiZBuffer(RenderProperties properties, int type) {
        this.properties = properties;
        this.type = type;
        glNamedFramebufferDrawBuffer(this.framebufferId, GL_NONE);
        try {
            this.programId = compileProgram(
                    properties.injectDefines(ShaderLoader.parse("voxy:hiz/blit.vsh")),
                    properties.injectDefines(ShaderLoader.parse("voxy:hiz/blit.fsh")));
        } catch (RuntimeException e) {
            glDeleteFramebuffers(this.framebufferId);
            glDeleteSamplers(this.samplerId);
            this.free0();
            throw e;
        }
        GlDebug.framebuffer("HiZ", this.framebufferId);
        GlDebug.program("HiZ Builder", this.programId);
    }

    void buildMipChain(int sourceDepthTextureId, int sourceWidth, int sourceHeight) {
        int targetWidth = Integer.highestOneBit(Math.max(1, sourceWidth));
        int targetHeight = Integer.highestOneBit(Math.max(1, sourceHeight));
        if (this.width != targetWidth || this.height != targetHeight || this.textureId == 0) {
            if (this.textureId != 0) {
                ForgeOriginalVoxyGlResourceStatistics.textureFreed(this.textureId);
                glDeleteTextures(this.textureId);
                this.textureId = 0;
            }
            this.alloc(targetWidth, targetHeight);
        }

        glBindVertexArray(ForgeOriginalVoxyEmptyVertexArray.id());
        int boundFramebuffer = glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING);
        glUseProgram(this.programId);
        glBindFramebuffer(GL_FRAMEBUFFER, this.framebufferId);
        glDepthFunc(GL_ALWAYS);
        glDepthMask(true);
        glEnable(GL_DEPTH_TEST);
        glBindTextureUnit(0, sourceDepthTextureId);
        glBindSampler(0, this.samplerId);
        glUniform1i(0, 0);

        int currentWidth = this.width;
        int currentHeight = this.height;
        for (int level = 0; level < this.levels; level++) {
            glNamedFramebufferTexture(this.framebufferId, GL_DEPTH_ATTACHMENT, this.textureId, level);
            glViewport(0, 0, currentWidth, currentHeight);
            currentWidth = Math.max(currentWidth / 2, 1);
            currentHeight = Math.max(currentHeight / 2, 1);
            glDrawArrays(org.lwjgl.opengl.GL11C.GL_TRIANGLE_FAN, 0, 4);
            glTextureBarrier();
            glMemoryBarrier(GL_FRAMEBUFFER_BARRIER_BIT | GL_TEXTURE_FETCH_BARRIER_BIT);
            glTextureParameteri(this.textureId, org.lwjgl.opengl.GL12C.GL_TEXTURE_BASE_LEVEL, level);
            glTextureParameteri(this.textureId, org.lwjgl.opengl.GL12C.GL_TEXTURE_MAX_LEVEL, level);
            if (level == 0) {
                glBindTextureUnit(0, this.textureId);
            }
        }
        glTextureParameteri(this.textureId, org.lwjgl.opengl.GL12C.GL_TEXTURE_BASE_LEVEL, 0);
        glTextureParameteri(this.textureId, org.lwjgl.opengl.GL12C.GL_TEXTURE_MAX_LEVEL, 1000);
        glDepthFunc(this.properties.closerEqualDepthCompare());
        glDisable(GL_DEPTH_TEST);
        glBindFramebuffer(GL_FRAMEBUFFER, boundFramebuffer);
        glViewport(0, 0, sourceWidth, sourceHeight);
        glBindVertexArray(0);
        glUseProgram(0);
    }

    int getHizTextureId() {
        return this.textureId;
    }

    int getPackedLevels() {
        return (this.width << 16) | this.height;
    }

    @Override
    public void free() {
        this.free0();
        if (this.textureId != 0) {
            ForgeOriginalVoxyGlResourceStatistics.textureFreed(this.textureId);
            glDeleteTextures(this.textureId);
            this.textureId = 0;
        }
        glDeleteFramebuffers(this.framebufferId);
        glDeleteSamplers(this.samplerId);
        glDeleteProgram(this.programId);
    }

    private void alloc(int width, int height) {
        this.levels = (int) Math.ceil(Math.log(Math.max(width, height)) / Math.log(2));
        this.levels = Math.max(1, this.levels);
        this.textureId = glCreateTextures(GL_TEXTURE_2D);
        GlDebug.texture("HiZ", this.textureId);
        ForgeOriginalVoxyGlResourceStatistics.textureAllocated(
                this.textureId,
                ForgeOriginalVoxyGlResourceStatistics.mipChainBytes(width, height, this.levels, Integer.BYTES));
        glTextureStorage2D(this.textureId, this.levels, this.type, width, height);
        glTextureParameteri(this.textureId, GL_TEXTURE_MIN_FILTER, GL_NEAREST_MIPMAP_NEAREST);
        glTextureParameteri(this.textureId, GL_TEXTURE_MAG_FILTER, org.lwjgl.opengl.GL11C.GL_NEAREST);
        glTextureParameteri(this.textureId, GL_TEXTURE_COMPARE_MODE, GL_NONE);
        glTextureParameteri(this.textureId, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTextureParameteri(this.textureId, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);

        glSamplerParameteri(this.samplerId, GL_TEXTURE_MIN_FILTER, GL_NEAREST_MIPMAP_NEAREST);
        glSamplerParameteri(this.samplerId, GL_TEXTURE_MAG_FILTER, org.lwjgl.opengl.GL11C.GL_NEAREST);
        glSamplerParameteri(this.samplerId, GL_TEXTURE_COMPARE_MODE, GL_NONE);
        glSamplerParameteri(this.samplerId, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glSamplerParameteri(this.samplerId, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);

        this.width = width;
        this.height = height;
        glNamedFramebufferTexture(this.framebufferId, GL_DEPTH_ATTACHMENT, this.textureId, 0);
        int status = glCheckNamedFramebufferStatus(this.framebufferId, GL_FRAMEBUFFER);
        if (status != GL_FRAMEBUFFER_COMPLETE) {
            throw new IllegalStateException("hiz-framebuffer-incomplete-" + status);
        }
    }

    private static int compileProgram(String vertexSource, String fragmentSource) {
        int vertex = compileShader(org.lwjgl.opengl.GL20C.GL_VERTEX_SHADER, vertexSource);
        int fragment = compileShader(org.lwjgl.opengl.GL20C.GL_FRAGMENT_SHADER, fragmentSource);
        int program = glCreateProgram();
        glAttachShader(program, vertex);
        glAttachShader(program, fragment);
        glLinkProgram(program);
        glDeleteShader(vertex);
        glDeleteShader(fragment);
        if (glGetProgrami(program, GL_LINK_STATUS) != GL_TRUE) {
            String log = glGetProgramInfoLog(program);
            glDeleteProgram(program);
            throw new IllegalStateException("Original HiZ shader link failed: " + log);
        }
        return program;
    }

    private static int compileShader(int type, String source) {
        int shader = glCreateShader(type);
        source = ForgeOriginalVoxyShaderCompiler.prepareSource(source);
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
            throw new IllegalStateException("Original HiZ shader compile failed: " + log);
        }
        return shader;
    }
}
