package me.cortex.voxy.forge;

import me.cortex.voxy.common.util.TrackedObject;
import java.util.Arrays;

import static org.lwjgl.opengl.GL11C.GL_DEPTH_COMPONENT;
import static org.lwjgl.opengl.GL11C.GL_NEAREST;
import static org.lwjgl.opengl.GL11C.GL_NONE;
import static org.lwjgl.opengl.GL11C.GL_TEXTURE_2D;
import static org.lwjgl.opengl.GL11C.GL_TEXTURE_MAG_FILTER;
import static org.lwjgl.opengl.GL11C.GL_TEXTURE_MIN_FILTER;
import static org.lwjgl.opengl.GL11C.glGetError;
import static org.lwjgl.opengl.GL30C.GL_COLOR_ATTACHMENT0;
import static org.lwjgl.opengl.GL30C.GL_DEPTH24_STENCIL8;
import static org.lwjgl.opengl.GL30C.GL_FRAMEBUFFER;
import static org.lwjgl.opengl.GL30C.GL_FRAMEBUFFER_COMPLETE;
import static org.lwjgl.opengl.GL30C.GL_RGBA8;
import static org.lwjgl.opengl.GL30C.glBindFramebuffer;
import static org.lwjgl.opengl.GL43C.GL_DEPTH_STENCIL_TEXTURE_MODE;
import static org.lwjgl.opengl.GL45C.glCheckNamedFramebufferStatus;
import static org.lwjgl.opengl.GL45C.glCreateFramebuffers;
import static org.lwjgl.opengl.GL45C.glCreateTextures;
import static org.lwjgl.opengl.GL45C.glDeleteFramebuffers;
import static org.lwjgl.opengl.GL45C.glDeleteTextures;
import static org.lwjgl.opengl.GL45C.glNamedFramebufferDrawBuffer;
import static org.lwjgl.opengl.GL45C.glNamedFramebufferDrawBuffers;
import static org.lwjgl.opengl.GL45C.glNamedFramebufferReadBuffer;
import static org.lwjgl.opengl.GL45C.glNamedFramebufferTexture;
import static org.lwjgl.opengl.GL45C.glTextureParameteri;
import static org.lwjgl.opengl.GL45C.glTextureStorage2D;

final class ForgeOriginalVoxyNormalPipelineTargets extends TrackedObject {
    private final int ssaoFramebufferId = glCreateFramebuffers();
    private final DepthFramebuffer translucentDepthFramebuffer =
            new DepthFramebuffer(GL_DEPTH24_STENCIL8);
    private int colourTextureId;
    private int colourSsaoTextureId;
    private int width;
    private int height;
    private boolean ownsColourTextures;
    private boolean externalDrawTargets;
    private int opaqueAttachmentCount;
    private int translucentAttachmentCount;
    private int[] opaqueExternalTextureIds = new int[0];
    private int[] translucentExternalTextureIds = new int[0];

    boolean resize(ForgeOriginalVoxyPipelineDepthStage depthStage, int width, int height) {
        return this.resize(depthStage, width, height, null);
    }

    boolean resize(
            ForgeOriginalVoxyPipelineDepthStage depthStage,
            int width,
            int height,
            ForgeOriginalVoxyOculusRenderPipelineData oculusPipelineData) {
        if (oculusPipelineData != null) {
            return this.resizeExternal(depthStage, width, height, oculusPipelineData);
        }
        if (this.colourTextureId != 0
                && this.ownsColourTextures
                && !this.externalDrawTargets
                && this.width == width
                && this.height == height) {
            return false;
        }
        this.deleteOwnedTextures();
        this.colourTextureId = createColourTexture(width, height);
        this.colourSsaoTextureId = createColourTexture(width, height);
        this.ownsColourTextures = true;
        this.externalDrawTargets = false;
        this.opaqueExternalTextureIds = new int[0];
        this.translucentExternalTextureIds = new int[0];
        glNamedFramebufferTexture(depthStage.framebufferId(), GL_COLOR_ATTACHMENT0, this.colourTextureId, 0);
        glNamedFramebufferTexture(this.ssaoFramebufferId, depthStage.getDepthAttachmentType(), depthStage.getDepthTex(), 0);
        glNamedFramebufferTexture(this.ssaoFramebufferId, GL_COLOR_ATTACHMENT0, this.colourSsaoTextureId, 0);
        this.setDrawBuffers(depthStage.framebufferId(), new int[]{GL_COLOR_ATTACHMENT0});
        this.setDrawBuffers(this.ssaoFramebufferId, new int[]{GL_COLOR_ATTACHMENT0});
        this.detachUnusedColourAttachments(depthStage.framebufferId(), 1, this.opaqueAttachmentCount);
        this.detachUnusedColourAttachments(this.ssaoFramebufferId, 1, this.translucentAttachmentCount);
        this.opaqueAttachmentCount = 1;
        this.translucentAttachmentCount = 1;
        glTextureParameteri(depthStage.getDepthTex(), GL_DEPTH_STENCIL_TEXTURE_MODE, GL_DEPTH_COMPONENT);
        this.verifyFramebuffer(depthStage.framebufferId(), "normal-pipeline-opaque-framebuffer");
        this.verifyFramebuffer(this.ssaoFramebufferId, "normal-pipeline-ssao-framebuffer");
        this.width = width;
        this.height = height;
        return true;
    }

    void bindOpaqueFramebuffer(ForgeOriginalVoxyPipelineDepthStage depthStage) {
        glBindFramebuffer(GL_FRAMEBUFFER, depthStage.framebufferId());
    }

    void bindTranslucentFramebuffer() {
        glBindFramebuffer(GL_FRAMEBUFFER, this.ssaoFramebufferId);
    }

    int translucentFramebufferId() {
        return this.ssaoFramebufferId;
    }

    boolean opaqueDrawTargetReady() {
        return this.colourTextureId != 0;
    }

    boolean translucentDrawTargetReady() {
        return this.ssaoFramebufferId != 0
                && this.colourSsaoTextureId != 0
                && (!this.externalDrawTargets || this.translucentDepthFramebuffer.depthTextureReady());
    }

    int colourTextureId() {
        return this.colourTextureId;
    }

    int colourSsaoTextureId() {
        return this.colourSsaoTextureId;
    }

    int translucentDepthTextureId() {
        return this.translucentDepthFramebuffer.getDepthTex();
    }

    @Override
    public void free() {
        this.free0();
        this.deleteOwnedTextures();
        this.translucentDepthFramebuffer.free();
        glDeleteFramebuffers(this.ssaoFramebufferId);
    }

    private boolean resizeExternal(
            ForgeOriginalVoxyPipelineDepthStage depthStage,
            int width,
            int height,
            ForgeOriginalVoxyOculusRenderPipelineData data) {
        if (this.externalDrawTargets
                && this.width == width
                && this.height == height
                && Arrays.equals(this.opaqueExternalTextureIds, data.opaqueDrawTargets)
                && Arrays.equals(this.translucentExternalTextureIds, data.translucentDrawTargets)) {
            this.translucentDepthFramebuffer.resize(width, height);
            glNamedFramebufferTexture(
                    this.ssaoFramebufferId,
                    depthStage.getDepthAttachmentType(),
                    this.translucentDepthFramebuffer.getDepthTex(),
                    0);
            glTextureParameteri(
                    this.translucentDepthFramebuffer.getDepthTex(),
                    GL_DEPTH_STENCIL_TEXTURE_MODE,
                    GL_DEPTH_COMPONENT);
            return false;
        }
        this.deleteOwnedTextures();
        this.externalDrawTargets = true;
        this.ownsColourTextures = false;
        this.colourTextureId = firstTextureOrZero(data.opaqueDrawTargets);
        this.colourSsaoTextureId = firstTextureOrZero(data.translucentDrawTargets);
        this.opaqueExternalTextureIds = Arrays.copyOf(data.opaqueDrawTargets, data.opaqueDrawTargets.length);
        this.translucentExternalTextureIds = Arrays.copyOf(data.translucentDrawTargets, data.translucentDrawTargets.length);
        this.attachDrawTargets(depthStage.framebufferId(), data.opaqueDrawTargets, "oculus-opaque-draw-targets");
        this.attachDrawTargets(this.ssaoFramebufferId, data.translucentDrawTargets, "oculus-translucent-draw-targets");
        this.detachUnusedColourAttachments(depthStage.framebufferId(), data.opaqueDrawTargets.length, this.opaqueAttachmentCount);
        this.detachUnusedColourAttachments(
                this.ssaoFramebufferId,
                data.translucentDrawTargets.length,
                this.translucentAttachmentCount);
        this.opaqueAttachmentCount = data.opaqueDrawTargets.length;
        this.translucentAttachmentCount = data.translucentDrawTargets.length;
        this.translucentDepthFramebuffer.resize(width, height);
        glNamedFramebufferTexture(
                this.ssaoFramebufferId,
                depthStage.getDepthAttachmentType(),
                this.translucentDepthFramebuffer.getDepthTex(),
                0);
        glTextureParameteri(depthStage.getDepthTex(), GL_DEPTH_STENCIL_TEXTURE_MODE, GL_DEPTH_COMPONENT);
        glTextureParameteri(
                this.translucentDepthFramebuffer.getDepthTex(),
                GL_DEPTH_STENCIL_TEXTURE_MODE,
                GL_DEPTH_COMPONENT);
        this.verifyFramebuffer(depthStage.framebufferId(), "oculus-opaque-framebuffer");
        this.verifyFramebuffer(this.ssaoFramebufferId, "oculus-translucent-framebuffer");
        this.width = width;
        this.height = height;
        return true;
    }

    private void attachDrawTargets(int framebufferId, int[] textureIds, String name) {
        int[] drawBuffers = new int[textureIds.length];
        for (int i = 0; i < textureIds.length; i++) {
            if (textureIds[i] == 0) {
                throw new IllegalStateException(name + "-missing-texture-" + i);
            }
            int attachment = GL_COLOR_ATTACHMENT0 + i;
            drawBuffers[i] = attachment;
            glNamedFramebufferTexture(framebufferId, attachment, textureIds[i], 0);
        }
        this.setDrawBuffers(framebufferId, drawBuffers);
    }

    private void setDrawBuffers(int framebufferId, int[] drawBuffers) {
        if (drawBuffers.length == 0) {
            glNamedFramebufferDrawBuffer(framebufferId, GL_NONE);
            glNamedFramebufferReadBuffer(framebufferId, GL_NONE);
        } else {
            glNamedFramebufferDrawBuffers(framebufferId, drawBuffers);
            glNamedFramebufferReadBuffer(framebufferId, drawBuffers[0]);
        }
    }

    private void detachUnusedColourAttachments(int framebufferId, int newCount, int previousCount) {
        for (int i = newCount; i < previousCount; i++) {
            glNamedFramebufferTexture(framebufferId, GL_COLOR_ATTACHMENT0 + i, 0, 0);
        }
    }

    private void verifyFramebuffer(int framebufferId, String name) {
        int status = glCheckNamedFramebufferStatus(framebufferId, GL_FRAMEBUFFER);
        if (status != GL_FRAMEBUFFER_COMPLETE) {
            throw new IllegalStateException(name + "-incomplete-" + status + "-gl-" + glGetError());
        }
    }

    private void deleteOwnedTextures() {
        if (this.ownsColourTextures && this.colourTextureId != 0) {
            ForgeOriginalVoxyGlResourceStatistics.textureFreed(this.colourTextureId);
            glDeleteTextures(this.colourTextureId);
        }
        if (this.ownsColourTextures && this.colourSsaoTextureId != 0) {
            ForgeOriginalVoxyGlResourceStatistics.textureFreed(this.colourSsaoTextureId);
            glDeleteTextures(this.colourSsaoTextureId);
        }
        this.colourTextureId = 0;
        this.colourSsaoTextureId = 0;
        this.ownsColourTextures = false;
        this.opaqueExternalTextureIds = new int[0];
        this.translucentExternalTextureIds = new int[0];
    }

    private static int createColourTexture(int width, int height) {
        int textureId = glCreateTextures(GL_TEXTURE_2D);
        ForgeOriginalVoxyGlResourceStatistics.textureAllocated(
                textureId,
                (long) width * height * Integer.BYTES);
        glTextureStorage2D(textureId, 1, GL_RGBA8, width, height);
        glTextureParameteri(textureId, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTextureParameteri(textureId, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        return textureId;
    }

    private static int firstTextureOrZero(int[] textureIds) {
        return textureIds.length == 0 ? 0 : textureIds[0];
    }
}
