package me.cortex.voxy.forge;

import org.lwjgl.system.MemoryStack;

import static org.lwjgl.opengl.GL11C.GL_DEPTH;
import static org.lwjgl.opengl.GL14C.GL_DEPTH_COMPONENT24;
import static org.lwjgl.opengl.GL30C.GL_DEPTH24_STENCIL8;
import static org.lwjgl.opengl.GL30C.GL_DEPTH_ATTACHMENT;
import static org.lwjgl.opengl.GL30C.GL_DEPTH_STENCIL_ATTACHMENT;
import static org.lwjgl.opengl.GL30C.GL_FRAMEBUFFER;
import static org.lwjgl.opengl.GL30C.GL_FRAMEBUFFER_COMPLETE;
import static org.lwjgl.opengl.GL30C.GL_STENCIL;
import static org.lwjgl.opengl.GL30C.glBindFramebuffer;
import static org.lwjgl.opengl.GL45C.glCheckNamedFramebufferStatus;
import static org.lwjgl.opengl.GL45C.glCreateFramebuffers;
import static org.lwjgl.opengl.GL45C.glCreateTextures;
import static org.lwjgl.opengl.GL45C.glDeleteFramebuffers;
import static org.lwjgl.opengl.GL45C.glDeleteTextures;
import static org.lwjgl.opengl.GL45C.glNamedFramebufferTexture;
import static org.lwjgl.opengl.GL45C.glTextureStorage2D;
import static org.lwjgl.opengl.GL45C.nglClearNamedFramebufferfv;
import static org.lwjgl.opengl.GL45C.nglClearNamedFramebufferiv;

final class ForgeOriginalVoxyDepthFramebuffer {
    private final int depthType;
    private final int framebufferId = glCreateFramebuffers();
    private int depthTextureId;
    private int width;
    private int height;
    private long resizeCount;
    private long clearCount;
    private String lastLifecycleEvent = "created";
    private String lastFailureReason = "none";

    ForgeOriginalVoxyDepthFramebuffer() {
        this(GL_DEPTH_COMPONENT24);
    }

    ForgeOriginalVoxyDepthFramebuffer(int depthType) {
        this.depthType = depthType;
    }

    boolean resize(int width, int height) {
        if (this.depthTextureId != 0 && this.width == width && this.height == height) {
            return false;
        }
        if (this.depthTextureId != 0) {
            glDeleteTextures(this.depthTextureId);
            this.depthTextureId = 0;
        }
        this.depthTextureId = glCreateTextures(org.lwjgl.opengl.GL11C.GL_TEXTURE_2D);
        glTextureStorage2D(this.depthTextureId, 1, this.depthType, width, height);
        glNamedFramebufferTexture(this.framebufferId, this.depthAttachmentType(), this.depthTextureId, 0);
        int status = glCheckNamedFramebufferStatus(this.framebufferId, GL_FRAMEBUFFER);
        if (status != GL_FRAMEBUFFER_COMPLETE) {
            this.lastLifecycleEvent = "failure";
            this.lastFailureReason = "depth-framebuffer-incomplete-" + status;
            throw new IllegalStateException(this.lastFailureReason);
        }
        this.width = width;
        this.height = height;
        this.resizeCount++;
        this.lastLifecycleEvent = "resize";
        this.lastFailureReason = "none";
        return true;
    }

    int depthAttachmentType() {
        return this.depthType == GL_DEPTH24_STENCIL8 ? GL_DEPTH_STENCIL_ATTACHMENT : GL_DEPTH_ATTACHMENT;
    }

    void clear(float depth) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            nglClearNamedFramebufferfv(this.framebufferId, GL_DEPTH, 0, stack.nfloat(depth));
        }
        this.clearCount++;
        this.lastLifecycleEvent = "clear-depth";
    }

    void clearStencil(int value) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            nglClearNamedFramebufferiv(this.framebufferId, GL_STENCIL, 0, stack.nint(value));
        }
        this.lastLifecycleEvent = "clear-stencil";
    }

    void bind() {
        glBindFramebuffer(GL_FRAMEBUFFER, this.framebufferId);
    }

    int framebufferId() {
        return this.framebufferId;
    }

    int depthTextureId() {
        return this.depthTextureId;
    }

    boolean ready() {
        return this.framebufferId != 0;
    }

    boolean depthTextureReady() {
        return this.depthTextureId != 0;
    }

    int width() {
        return this.width;
    }

    int height() {
        return this.height;
    }

    long resizeCount() {
        return this.resizeCount;
    }

    long clearCount() {
        return this.clearCount;
    }

    String lastLifecycleEvent() {
        return this.lastLifecycleEvent;
    }

    String lastFailureReason() {
        return this.lastFailureReason;
    }

    void free() {
        if (this.depthTextureId != 0) {
            glDeleteTextures(this.depthTextureId);
            this.depthTextureId = 0;
        }
        glDeleteFramebuffers(this.framebufferId);
        this.lastLifecycleEvent = "free";
    }
}
