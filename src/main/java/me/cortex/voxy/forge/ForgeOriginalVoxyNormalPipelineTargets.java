package me.cortex.voxy.forge;

import static org.lwjgl.opengl.GL11C.GL_DEPTH_COMPONENT;
import static org.lwjgl.opengl.GL11C.GL_NEAREST;
import static org.lwjgl.opengl.GL11C.GL_NONE;
import static org.lwjgl.opengl.GL11C.GL_TEXTURE_2D;
import static org.lwjgl.opengl.GL11C.GL_TEXTURE_MAG_FILTER;
import static org.lwjgl.opengl.GL11C.GL_TEXTURE_MIN_FILTER;
import static org.lwjgl.opengl.GL11C.glGetError;
import static org.lwjgl.opengl.GL30C.GL_COLOR_ATTACHMENT0;
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

final class ForgeOriginalVoxyNormalPipelineTargets {
    private final int ssaoFramebufferId = glCreateFramebuffers();
    private int colourTextureId;
    private int colourSsaoTextureId;
    private int width;
    private int height;
    private boolean ownsColourTextures;
    private boolean externalDrawTargets;
    private int opaqueAttachmentCount;
    private int translucentAttachmentCount;
    private long resizeCount;
    private String lastLifecycleEvent = "created";
    private String lastFailureReason = "none";

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
        glNamedFramebufferTexture(depthStage.framebufferId(), GL_COLOR_ATTACHMENT0, this.colourTextureId, 0);
        glNamedFramebufferTexture(this.ssaoFramebufferId, depthStage.depthAttachmentType(), depthStage.depthTextureId(), 0);
        glNamedFramebufferTexture(this.ssaoFramebufferId, GL_COLOR_ATTACHMENT0, this.colourSsaoTextureId, 0);
        this.setDrawBuffers(depthStage.framebufferId(), new int[]{GL_COLOR_ATTACHMENT0});
        this.setDrawBuffers(this.ssaoFramebufferId, new int[]{GL_COLOR_ATTACHMENT0});
        this.detachUnusedColourAttachments(depthStage.framebufferId(), 1, this.opaqueAttachmentCount);
        this.detachUnusedColourAttachments(this.ssaoFramebufferId, 1, this.translucentAttachmentCount);
        this.opaqueAttachmentCount = 1;
        this.translucentAttachmentCount = 1;
        glTextureParameteri(depthStage.depthTextureId(), GL_DEPTH_STENCIL_TEXTURE_MODE, GL_DEPTH_COMPONENT);
        this.verifyFramebuffer(depthStage.framebufferId(), "normal-pipeline-opaque-framebuffer");
        this.verifyFramebuffer(this.ssaoFramebufferId, "normal-pipeline-ssao-framebuffer");
        this.width = width;
        this.height = height;
        this.resizeCount++;
        this.lastLifecycleEvent = "resize";
        this.lastFailureReason = "none";
        return true;
    }

    void bindOpaqueFramebuffer(ForgeOriginalVoxyPipelineDepthStage depthStage) {
        glBindFramebuffer(GL_FRAMEBUFFER, depthStage.framebufferId());
        this.lastLifecycleEvent = "bind-opaque";
    }

    void bindTranslucentFramebuffer() {
        glBindFramebuffer(GL_FRAMEBUFFER, this.ssaoFramebufferId);
        this.lastLifecycleEvent = "bind-translucent";
    }

    int translucentFramebufferId() {
        return this.ssaoFramebufferId;
    }

    boolean opaqueDrawTargetReady() {
        return this.externalDrawTargets || this.colourTextureId != 0;
    }

    boolean translucentDrawTargetReady() {
        return this.ssaoFramebufferId != 0 && (this.externalDrawTargets || this.colourSsaoTextureId != 0);
    }

    boolean usingExternalDrawTargets() {
        return this.externalDrawTargets;
    }

    int colourTextureId() {
        return this.colourTextureId;
    }

    int colourSsaoTextureId() {
        return this.colourSsaoTextureId;
    }

    long resizeCount() {
        return this.resizeCount;
    }

    String lastLifecycleEvent() {
        return this.lastLifecycleEvent;
    }

    String lastFailureReason() {
        return this.lastFailureReason;
    }

    void freeOnRenderThread() {
        this.deleteOwnedTextures();
        glDeleteFramebuffers(this.ssaoFramebufferId);
        this.lastLifecycleEvent = "free";
    }

    private boolean resizeExternal(
            ForgeOriginalVoxyPipelineDepthStage depthStage,
            int width,
            int height,
        ForgeOriginalVoxyOculusRenderPipelineData data) {
        if (this.externalDrawTargets && this.width == width && this.height == height) {
            glNamedFramebufferTexture(this.ssaoFramebufferId, depthStage.depthAttachmentType(), depthStage.depthTextureId(), 0);
            return false;
        }
        this.deleteOwnedTextures();
        this.externalDrawTargets = true;
        this.ownsColourTextures = false;
        this.colourTextureId = firstTextureOrZero(data.opaqueDrawTargets);
        this.colourSsaoTextureId = firstTextureOrZero(data.translucentDrawTargets);
        this.attachDrawTargets(depthStage.framebufferId(), data.opaqueDrawTargets, "oculus-opaque-draw-targets");
        this.attachDrawTargets(this.ssaoFramebufferId, data.translucentDrawTargets, "oculus-translucent-draw-targets");
        this.detachUnusedColourAttachments(depthStage.framebufferId(), data.opaqueDrawTargets.length, this.opaqueAttachmentCount);
        this.detachUnusedColourAttachments(
                this.ssaoFramebufferId,
                data.translucentDrawTargets.length,
                this.translucentAttachmentCount);
        this.opaqueAttachmentCount = data.opaqueDrawTargets.length;
        this.translucentAttachmentCount = data.translucentDrawTargets.length;
        glNamedFramebufferTexture(this.ssaoFramebufferId, depthStage.depthAttachmentType(), depthStage.depthTextureId(), 0);
        glTextureParameteri(depthStage.depthTextureId(), GL_DEPTH_STENCIL_TEXTURE_MODE, GL_DEPTH_COMPONENT);
        this.verifyFramebuffer(depthStage.framebufferId(), "oculus-opaque-framebuffer");
        this.verifyFramebuffer(this.ssaoFramebufferId, "oculus-translucent-framebuffer");
        this.width = width;
        this.height = height;
        this.resizeCount++;
        this.lastLifecycleEvent = "resize-external-draw-targets";
        this.lastFailureReason = "none";
        return true;
    }

    private void attachDrawTargets(int framebufferId, int[] textureIds, String name) {
        int[] drawBuffers = new int[textureIds.length];
        for (int i = 0; i < textureIds.length; i++) {
            if (textureIds[i] == 0) {
                this.lastLifecycleEvent = "failure";
                this.lastFailureReason = name + "-missing-texture-" + i;
                throw new IllegalStateException(this.lastFailureReason);
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
            this.lastLifecycleEvent = "failure";
            this.lastFailureReason = name + "-incomplete-" + status + "-gl-" + glGetError();
            throw new IllegalStateException(this.lastFailureReason);
        }
    }

    private void deleteOwnedTextures() {
        if (this.ownsColourTextures && this.colourTextureId != 0) {
            glDeleteTextures(this.colourTextureId);
        }
        if (this.ownsColourTextures && this.colourSsaoTextureId != 0) {
            glDeleteTextures(this.colourSsaoTextureId);
        }
        this.colourTextureId = 0;
        this.colourSsaoTextureId = 0;
        this.ownsColourTextures = false;
    }

    private static int createColourTexture(int width, int height) {
        int textureId = glCreateTextures(GL_TEXTURE_2D);
        glTextureStorage2D(textureId, 1, GL_RGBA8, width, height);
        glTextureParameteri(textureId, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTextureParameteri(textureId, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        return textureId;
    }

    private static int firstTextureOrZero(int[] textureIds) {
        return textureIds.length == 0 ? 0 : textureIds[0];
    }
}
