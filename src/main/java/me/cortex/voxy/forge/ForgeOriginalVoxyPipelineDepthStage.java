package me.cortex.voxy.forge;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import static org.lwjgl.opengl.GL11C.GL_COLOR_WRITEMASK;
import static org.lwjgl.opengl.GL11C.GL_ALWAYS;
import static org.lwjgl.opengl.GL11C.GL_DEPTH_COMPONENT;
import static org.lwjgl.opengl.GL11C.GL_DEPTH_FUNC;
import static org.lwjgl.opengl.GL11C.GL_DEPTH_TEST;
import static org.lwjgl.opengl.GL11C.GL_DEPTH_WRITEMASK;
import static org.lwjgl.opengl.GL11C.GL_EQUAL;
import static org.lwjgl.opengl.GL11C.GL_FLOAT;
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
import static org.lwjgl.opengl.GL11C.glReadPixels;
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
import static org.lwjgl.opengl.GL45C.GL_CLIP_DEPTH_MODE;
import static org.lwjgl.opengl.GL45C.GL_ZERO_TO_ONE;
import static org.lwjgl.opengl.GL11C.glGetInteger;
import static org.lwjgl.opengl.GL11C.glDepthFunc;

final class ForgeOriginalVoxyPipelineDepthStage {
    private static final int MAX_DEPTH_SOURCE_AUDITS = 8;
    private static final boolean AUDIT_SOURCE_DEPTH = Boolean.getBoolean("voxy.forge.auditSourceDepth");
    private static final float DEPTH_EPSILON = 1.0E-6F;

    private final ForgeOriginalVoxyRenderProperties properties;
    private final ForgeOriginalVoxyDepthFramebuffer framebuffer = new ForgeOriginalVoxyDepthFramebuffer(GL_DEPTH24_STENCIL8);
    private final ForgeOriginalVoxyFullscreenBlit depthStencilSetup;
    private final int depthSamplerId = glGenSamplers();
    private long setupCount;
    private long depthSourceAuditCount;
    private long emptyDepthSourceWarningCount;
    private String lastLifecycleEvent = "created";
    private String lastFailureReason = "none";

    ForgeOriginalVoxyPipelineDepthStage(ForgeOriginalVoxyRenderProperties properties) {
        this.properties = properties;
        glSamplerParameteri(this.depthSamplerId, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glSamplerParameteri(this.depthSamplerId, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        this.depthStencilSetup = new ForgeOriginalVoxyFullscreenBlit(
                properties,
                "voxy:post/fullscreen2.vert",
                "voxy:post/setup_stencil_depth.frag");
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
            this.lastLifecycleEvent = "failure";
            this.lastFailureReason = "source-depth-texture-missing";
            throw new IllegalStateException(this.lastFailureReason);
        }
        this.auditSourceDepth(sourceDepth, srcWidth, srcHeight);
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

            this.setupCount++;
            this.lastLifecycleEvent = "setup-depth-stencil";
            this.lastFailureReason = "none";
            return this.framebuffer.depthTextureId();
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
            if (this.emptyDepthSourceWarningCount < MAX_DEPTH_SOURCE_AUDITS) {
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

    private void auditSourceDepth(SourceDepth sourceDepth, int srcWidth, int srcHeight) {
        if (!AUDIT_SOURCE_DEPTH || this.depthSourceAuditCount >= MAX_DEPTH_SOURCE_AUDITS
                || sourceDepth.textureId() == 0 || srcWidth <= 0 || srcHeight <= 0) {
            return;
        }
        int oldReadFramebuffer = glGetInteger(GL_READ_FRAMEBUFFER_BINDING);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            glBindFramebuffer(GL_READ_FRAMEBUFFER, sourceDepth.framebufferId());
            long samples = stack.nmalloc(5 * Float.BYTES);
            int maxX = Math.max(0, srcWidth - 1);
            int maxY = Math.max(0, srcHeight - 1);
            readDepthSample(maxX / 2, maxY / 2, samples);
            readDepthSample(maxX / 2, (maxY * 7) / 8, samples + Float.BYTES);
            readDepthSample(maxX / 2, maxY / 8, samples + 2L * Float.BYTES);
            readDepthSample(maxX / 8, (maxY * 7) / 8, samples + 3L * Float.BYTES);
            readDepthSample((maxX * 7) / 8, (maxY * 7) / 8, samples + 4L * Float.BYTES);
            float farDepth = this.properties.isReverseZ() ? 0.0F : 1.0F;
            boolean allFar = true;
            for (int i = 0; i < 5; i++) {
                float sample = MemoryUtil.memGetFloat(samples + i * (long) Float.BYTES);
                if (Math.abs(sample - farDepth) > DEPTH_EPSILON) {
                    allFar = false;
                    break;
                }
            }
            this.depthSourceAuditCount++;
            int clipDepthMode = glGetInteger(GL_CLIP_DEPTH_MODE);
            int depthFunc = glGetInteger(GL_DEPTH_FUNC);
            VoxyForge.LOGGER.info(
                    "Original depth source audit: run={} allFar={} sourceFramebuffer={} sourceDepthTexture={} src={}x{} propsZeroOne={} propsReverseZ={} clipDepthMode={} zeroToOne={} depthFunc={} samples=[{},{},{},{},{}]",
                    this.depthSourceAuditCount,
                    allFar,
                    sourceDepth.framebufferId(),
                    sourceDepth.textureId(),
                    srcWidth,
                    srcHeight,
                    this.properties.isZero2One(),
                    this.properties.isReverseZ(),
                    clipDepthMode,
                    clipDepthMode == GL_ZERO_TO_ONE,
                    depthFunc,
                    Float.toString(MemoryUtil.memGetFloat(samples)),
                    Float.toString(MemoryUtil.memGetFloat(samples + Float.BYTES)),
                    Float.toString(MemoryUtil.memGetFloat(samples + 2L * Float.BYTES)),
                    Float.toString(MemoryUtil.memGetFloat(samples + 3L * Float.BYTES)),
                    Float.toString(MemoryUtil.memGetFloat(samples + 4L * Float.BYTES)));
        } finally {
            glBindFramebuffer(GL_READ_FRAMEBUFFER, oldReadFramebuffer);
        }
    }

    private static void readDepthSample(int x, int y, long sampleAddress) {
        glReadPixels(x, y, 1, 1, GL_DEPTH_COMPONENT, GL_FLOAT, sampleAddress);
    }

    private record SourceDepth(int framebufferId, int textureId) {
    }

    void bindFramebuffer() {
        glBindFramebuffer(GL_FRAMEBUFFER, this.framebuffer.framebufferId());
    }

    int framebufferId() {
        return this.framebuffer.framebufferId();
    }

    int depthTextureId() {
        return this.framebuffer.depthTextureId();
    }

    int depthAttachmentType() {
        return this.framebuffer.depthAttachmentType();
    }

    boolean ready() {
        return this.framebuffer.ready() && this.depthSamplerId != 0;
    }

    long setupCount() {
        return this.setupCount;
    }

    String lastLifecycleEvent() {
        return this.lastLifecycleEvent;
    }

    String lastFailureReason() {
        return this.lastFailureReason;
    }

    void freeOnRenderThread() {
        this.depthStencilSetup.free();
        this.framebuffer.free();
        glDeleteSamplers(this.depthSamplerId);
        this.lastLifecycleEvent = "free";
    }

    private record GlState(
            int drawFramebuffer,
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
            glBindFramebuffer(GL_FRAMEBUFFER, this.drawFramebuffer);
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
            glBindTextureUnit(0, this.textureUnit0);
            glBindSampler(0, this.samplerUnit0);
            glActiveTexture(this.activeTexture);
        }
    }
}
