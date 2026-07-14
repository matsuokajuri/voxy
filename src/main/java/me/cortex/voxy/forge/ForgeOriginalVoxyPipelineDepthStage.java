package me.cortex.voxy.forge;

import me.cortex.voxy.common.util.TrackedObject;
import org.lwjgl.system.MemoryStack;

import static org.lwjgl.opengl.GL11C.GL_COLOR_WRITEMASK;
import static org.lwjgl.opengl.GL11C.GL_ALWAYS;
import static org.lwjgl.opengl.GL11C.GL_DEPTH_FUNC;
import static org.lwjgl.opengl.GL11C.GL_DEPTH_TEST;
import static org.lwjgl.opengl.GL11C.GL_DEPTH_WRITEMASK;
import static org.lwjgl.opengl.GL11C.GL_EQUAL;
import static org.lwjgl.opengl.GL11C.GL_KEEP;
import static org.lwjgl.opengl.GL11C.GL_NEAREST;
import static org.lwjgl.opengl.GL11C.GL_REPLACE;
import static org.lwjgl.opengl.GL11C.GL_STENCIL_FAIL;
import static org.lwjgl.opengl.GL11C.GL_STENCIL_FUNC;
import static org.lwjgl.opengl.GL11C.GL_STENCIL_PASS_DEPTH_FAIL;
import static org.lwjgl.opengl.GL11C.GL_STENCIL_PASS_DEPTH_PASS;
import static org.lwjgl.opengl.GL11C.GL_STENCIL_REF;
import static org.lwjgl.opengl.GL11C.GL_STENCIL_TEST;
import static org.lwjgl.opengl.GL11C.GL_STENCIL_VALUE_MASK;
import static org.lwjgl.opengl.GL11C.GL_STENCIL_WRITEMASK;
import static org.lwjgl.opengl.GL11C.GL_TEXTURE_BINDING_2D;
import static org.lwjgl.opengl.GL11C.GL_TEXTURE_MAG_FILTER;
import static org.lwjgl.opengl.GL11C.GL_TEXTURE_MIN_FILTER;
import static org.lwjgl.opengl.GL11C.glColorMask;
import static org.lwjgl.opengl.GL11C.glDepthMask;
import static org.lwjgl.opengl.GL11C.glDisable;
import static org.lwjgl.opengl.GL11C.glEnable;
import static org.lwjgl.opengl.GL11C.glGetBoolean;
import static org.lwjgl.opengl.GL11C.glGetBooleanv;
import static org.lwjgl.opengl.GL11C.glStencilFunc;
import static org.lwjgl.opengl.GL11C.glStencilMask;
import static org.lwjgl.opengl.GL11C.glStencilOp;
import static org.lwjgl.opengl.GL11C.glIsEnabled;
import static org.lwjgl.opengl.GL13C.GL_ACTIVE_TEXTURE;
import static org.lwjgl.opengl.GL13C.GL_TEXTURE0;
import static org.lwjgl.opengl.GL13C.glActiveTexture;
import static org.lwjgl.opengl.GL20C.GL_CURRENT_PROGRAM;
import static org.lwjgl.opengl.GL20C.glUniform2f;
import static org.lwjgl.opengl.GL20C.glUseProgram;
import static org.lwjgl.opengl.GL30C.GL_DEPTH24_STENCIL8;
import static org.lwjgl.opengl.GL30C.GL_DEPTH_ATTACHMENT;
import static org.lwjgl.opengl.GL30C.GL_DEPTH_STENCIL;
import static org.lwjgl.opengl.GL30C.GL_DRAW_FRAMEBUFFER_BINDING;
import static org.lwjgl.opengl.GL30C.GL_DRAW_FRAMEBUFFER;
import static org.lwjgl.opengl.GL30C.GL_FRAMEBUFFER;
import static org.lwjgl.opengl.GL30C.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME;
import static org.lwjgl.opengl.GL30C.GL_READ_FRAMEBUFFER;
import static org.lwjgl.opengl.GL30C.GL_READ_FRAMEBUFFER_BINDING;
import static org.lwjgl.opengl.GL30C.GL_VERTEX_ARRAY_BINDING;
import static org.lwjgl.opengl.GL30C.glBindFramebuffer;
import static org.lwjgl.opengl.GL30C.glBindVertexArray;
import static org.lwjgl.opengl.GL30C.glGetIntegeri;
import static org.lwjgl.opengl.GL33C.GL_SAMPLER_BINDING;
import static org.lwjgl.opengl.GL33C.glBindSampler;
import static org.lwjgl.opengl.GL33C.glDeleteSamplers;
import static org.lwjgl.opengl.GL33C.glGenSamplers;
import static org.lwjgl.opengl.GL33C.glSamplerParameteri;
import static org.lwjgl.opengl.GL45C.glBindTextureUnit;
import static org.lwjgl.opengl.GL45C.glClearNamedFramebufferfi;
import static org.lwjgl.opengl.GL45C.glGetNamedFramebufferAttachmentParameteri;
import static org.lwjgl.opengl.GL11C.glGetInteger;
import static org.lwjgl.opengl.GL11C.glDepthFunc;

final class ForgeOriginalVoxyPipelineDepthStage extends TrackedObject {
    private static final int MAX_EMPTY_SOURCE_WARNINGS = 8;

    private final RenderProperties properties;
    private final DepthFramebuffer framebuffer = new DepthFramebuffer(GL_DEPTH24_STENCIL8);
    private final FullscreenBlit depthStencilSetup;
    private final int depthSamplerId = glGenSamplers();
    private long emptyDepthSourceWarningCount;

    ForgeOriginalVoxyPipelineDepthStage(RenderProperties properties) {
        this.properties = properties;
        try {
            glSamplerParameteri(this.depthSamplerId, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
            glSamplerParameteri(this.depthSamplerId, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
            this.depthStencilSetup = new FullscreenBlit(
                    properties,
                    "voxy:post/fullscreen2.vert",
                    "voxy:post/setup_stencil_depth.frag");
        } catch (RuntimeException e) {
            this.framebuffer.free();
            glDeleteSamplers(this.depthSamplerId);
            this.free0();
            throw e;
        }
    }

    int setupDepthTexture(
            int sourceFramebuffer,
            int alternateSourceFramebuffer,
            int srcWidth,
            int srcHeight,
            int width,
            int height) {
        SourceDepth sourceDepth = this.selectSourceDepth(
                sourceFramebuffer,
                alternateSourceFramebuffer,
                srcWidth,
                srcHeight);
        if (sourceDepth.textureId() == 0) {
            throw new IllegalStateException("source-depth-texture-missing");
        }
        GlState state = GlState.capture();
        try {
            this.framebuffer.resize(width, height);
            glClearNamedFramebufferfi(this.framebuffer.framebufferId(), GL_DEPTH_STENCIL, 0, this.properties.clearDepth(), 1);
            glBindFramebuffer(GL_FRAMEBUFFER, this.framebuffer.framebufferId());

            glEnable(GL_DEPTH_TEST);
            glDepthFunc(GL_ALWAYS);

            glEnable(GL_STENCIL_TEST);
            glStencilOp(GL_KEEP, GL_KEEP, GL_REPLACE);
            glStencilFunc(GL_ALWAYS, 0, 0xFF);
            glStencilMask(0xFF);

            this.depthStencilSetup.bind();
            glBindTextureUnit(0, sourceDepth.textureId());
            glBindSampler(0, this.depthSamplerId);
            glUniform2f(1, ((float) width) / srcWidth, ((float) height) / srcHeight);
            glDepthMask(true);
            glColorMask(false, false, false, false);
            this.depthStencilSetup.blit();

            glDepthFunc(this.properties.closerEqualDepthCompare());
            glColorMask(true, true, true, true);
            glStencilOp(GL_KEEP, GL_KEEP, GL_KEEP);
            glStencilFunc(GL_EQUAL, 1, 0xFF);

            return this.framebuffer.getDepthTex();
        } finally {
            state.restore();
        }
    }

    private SourceDepth selectSourceDepth(int sourceFramebuffer, int alternateSourceFramebuffer, int srcWidth, int srcHeight) {
        SourceDepth primary = this.readSourceDepthAttachment(sourceFramebuffer);
        if (primary.textureId() != 0 || alternateSourceFramebuffer == 0 || alternateSourceFramebuffer == sourceFramebuffer) {
            return primary;
        }
        SourceDepth alternate = this.readSourceDepthAttachment(alternateSourceFramebuffer);
        if (alternate.textureId() != 0) {
            if (this.emptyDepthSourceWarningCount < MAX_EMPTY_SOURCE_WARNINGS) {
                this.emptyDepthSourceWarningCount++;
                VoxyForge.LOGGER.warn(
                        "Original source depth draw framebuffer had no depth attachment; using read framebuffer depth instead: drawFramebuffer={} readFramebuffer={} readDepthTexture={} src={}x{} warning={}",
                        sourceFramebuffer,
                        alternateSourceFramebuffer,
                        alternate.textureId(),
                        srcWidth,
                        srcHeight,
                        this.emptyDepthSourceWarningCount);
            }
            return alternate;
        }
        return primary;
    }

    private SourceDepth readSourceDepthAttachment(int sourceFramebuffer) {
        int sourceDepthTexture = sourceFramebuffer == 0
                ? 0
                : glGetNamedFramebufferAttachmentParameteri(
                sourceFramebuffer,
                GL_DEPTH_ATTACHMENT,
                GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME);
        return new SourceDepth(sourceFramebuffer, sourceDepthTexture);
    }

    private record SourceDepth(int framebufferId, int textureId) {
    }

    void bindFramebuffer() {
        glBindFramebuffer(GL_FRAMEBUFFER, this.framebuffer.framebufferId());
    }

    int framebufferId() {
        return this.framebuffer.framebufferId();
    }

    int getDepthTex() {
        return this.framebuffer.getDepthTex();
    }

    int getDepthAttachmentType() {
        return this.framebuffer.getDepthAttachmentType();
    }

    @Override
    public void free() {
        this.free0();
        this.depthStencilSetup.free();
        this.framebuffer.free();
        glDeleteSamplers(this.depthSamplerId);
    }

    private record GlState(
            int drawFramebuffer,
            int readFramebuffer,
            int currentProgram,
            int vertexArray,
            boolean depthEnabled,
            int depthFunc,
            boolean depthMask,
            boolean stencilEnabled,
            int stencilFunc,
            int stencilRef,
            int stencilValueMask,
            int stencilWriteMask,
            int stencilFail,
            int stencilPassDepthFail,
            int stencilPassDepthPass,
            boolean colorMaskR,
            boolean colorMaskG,
            boolean colorMaskB,
            boolean colorMaskA,
            int activeTexture,
            int textureUnit0,
            int samplerUnit0) {

        static GlState capture() {
            try (MemoryStack stack = MemoryStack.stackPush()) {
                var colorMask = stack.malloc(4);
                glGetBooleanv(GL_COLOR_WRITEMASK, colorMask);
                int activeTexture = glGetInteger(GL_ACTIVE_TEXTURE);
                glActiveTexture(GL_TEXTURE0);
                int textureUnit0 = glGetInteger(GL_TEXTURE_BINDING_2D);
                glActiveTexture(activeTexture);
                return new GlState(
                        glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING),
                        glGetInteger(GL_READ_FRAMEBUFFER_BINDING),
                        glGetInteger(GL_CURRENT_PROGRAM),
                        glGetInteger(GL_VERTEX_ARRAY_BINDING),
                        glIsEnabled(GL_DEPTH_TEST),
                        glGetInteger(GL_DEPTH_FUNC),
                        glGetBoolean(GL_DEPTH_WRITEMASK),
                        glIsEnabled(GL_STENCIL_TEST),
                        glGetInteger(GL_STENCIL_FUNC),
                        glGetInteger(GL_STENCIL_REF),
                        glGetInteger(GL_STENCIL_VALUE_MASK),
                        glGetInteger(GL_STENCIL_WRITEMASK),
                        glGetInteger(GL_STENCIL_FAIL),
                        glGetInteger(GL_STENCIL_PASS_DEPTH_FAIL),
                        glGetInteger(GL_STENCIL_PASS_DEPTH_PASS),
                        colorMask.get(0) != 0,
                        colorMask.get(1) != 0,
                        colorMask.get(2) != 0,
                        colorMask.get(3) != 0,
                        activeTexture,
                        textureUnit0,
                        glGetIntegeri(GL_SAMPLER_BINDING, 0));
            }
        }

        void restore() {
            glBindFramebuffer(GL_DRAW_FRAMEBUFFER, this.drawFramebuffer);
            glBindFramebuffer(GL_READ_FRAMEBUFFER, this.readFramebuffer);
            glUseProgram(this.currentProgram);
            glBindVertexArray(this.vertexArray);
            if (this.depthEnabled) {
                glEnable(GL_DEPTH_TEST);
            } else {
                glDisable(GL_DEPTH_TEST);
            }
            glDepthFunc(this.depthFunc);
            glDepthMask(this.depthMask);
            if (this.stencilEnabled) {
                glEnable(GL_STENCIL_TEST);
            } else {
                glDisable(GL_STENCIL_TEST);
            }
            glStencilFunc(this.stencilFunc, this.stencilRef, this.stencilValueMask);
            glStencilMask(this.stencilWriteMask);
            glStencilOp(this.stencilFail, this.stencilPassDepthFail, this.stencilPassDepthPass);
            glColorMask(this.colorMaskR, this.colorMaskG, this.colorMaskB, this.colorMaskA);
            ForgeOriginalVoxyTextureBindings.bind2D(0, this.textureUnit0);
            glBindSampler(0, this.samplerUnit0);
            glActiveTexture(this.activeTexture);
        }
    }
}
