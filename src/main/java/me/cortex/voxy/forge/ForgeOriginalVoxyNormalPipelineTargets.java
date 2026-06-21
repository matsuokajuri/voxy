package me.cortex.voxy.forge;

import static org.lwjgl.opengl.GL11C.GL_DEPTH_COMPONENT;
import static org.lwjgl.opengl.GL11C.GL_NEAREST;
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
import static org.lwjgl.opengl.GL45C.glNamedFramebufferTexture;
import static org.lwjgl.opengl.GL45C.glTextureParameteri;
import static org.lwjgl.opengl.GL45C.glTextureStorage2D;

final class ForgeOriginalVoxyNormalPipelineTargets {
    private final int ssaoFramebufferId = glCreateFramebuffers();
    private int colourTextureId;
    private int colourSsaoTextureId;
    private int width;
    private int height;
    private long resizeCount;
    private String lastLifecycleEvent = "created";
    private String lastFailureReason = "none";

    boolean resize(ForgeOriginalVoxyPipelineDepthStage depthStage, int width, int height) {
        if (this.colourTextureId != 0 && this.width == width && this.height == height) {
            return false;
        }
        this.deleteTextures();
        this.colourTextureId = createColourTexture(width, height);
        this.colourSsaoTextureId = createColourTexture(width, height);
        glNamedFramebufferTexture(depthStage.framebufferId(), GL_COLOR_ATTACHMENT0, this.colourTextureId, 0);
        glNamedFramebufferTexture(this.ssaoFramebufferId, depthStage.depthAttachmentType(), depthStage.depthTextureId(), 0);
        glNamedFramebufferTexture(this.ssaoFramebufferId, GL_COLOR_ATTACHMENT0, this.colourSsaoTextureId, 0);
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

    boolean opaqueDrawTargetReady() {
        return this.colourTextureId != 0;
    }

    boolean translucentDrawTargetReady() {
        return this.colourSsaoTextureId != 0 && this.ssaoFramebufferId != 0;
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
        this.deleteTextures();
        glDeleteFramebuffers(this.ssaoFramebufferId);
        this.lastLifecycleEvent = "free";
    }

    private void verifyFramebuffer(int framebufferId, String name) {
        int status = glCheckNamedFramebufferStatus(framebufferId, GL_FRAMEBUFFER);
        if (status != GL_FRAMEBUFFER_COMPLETE) {
            this.lastLifecycleEvent = "failure";
            this.lastFailureReason = name + "-incomplete-" + status + "-gl-" + glGetError();
            throw new IllegalStateException(this.lastFailureReason);
        }
    }

    private void deleteTextures() {
        if (this.colourTextureId != 0) {
            glDeleteTextures(this.colourTextureId);
            this.colourTextureId = 0;
        }
        if (this.colourSsaoTextureId != 0) {
            glDeleteTextures(this.colourSsaoTextureId);
            this.colourSsaoTextureId = 0;
        }
    }

    private static int createColourTexture(int width, int height) {
        int textureId = glCreateTextures(GL_TEXTURE_2D);
        glTextureStorage2D(textureId, 1, GL_RGBA8, width, height);
        glTextureParameteri(textureId, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTextureParameteri(textureId, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        return textureId;
    }
}
