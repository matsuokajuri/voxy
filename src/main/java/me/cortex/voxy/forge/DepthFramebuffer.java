package me.cortex.voxy.forge;

import me.cortex.voxy.common.util.TrackedObject;
import org.lwjgl.system.MemoryStack;

import static org.lwjgl.opengl.GL11C.GL_DEPTH;
import static org.lwjgl.opengl.GL11C.GL_NEAREST;
import static org.lwjgl.opengl.GL14C.GL_DEPTH_COMPONENT24;
import static org.lwjgl.opengl.GL11C.GL_TEXTURE_2D;
import static org.lwjgl.opengl.GL11C.GL_TEXTURE_MAG_FILTER;
import static org.lwjgl.opengl.GL11C.GL_TEXTURE_MIN_FILTER;
import static org.lwjgl.opengl.GL11C.GL_TEXTURE_WRAP_S;
import static org.lwjgl.opengl.GL11C.GL_TEXTURE_WRAP_T;
import static org.lwjgl.opengl.GL12C.GL_CLAMP_TO_EDGE;
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
import static org.lwjgl.opengl.GL45C.glTextureParameteri;
import static org.lwjgl.opengl.GL45C.glTextureStorage2D;
import static org.lwjgl.opengl.GL45C.nglClearNamedFramebufferfv;
import static org.lwjgl.opengl.GL45C.nglClearNamedFramebufferiv;

final class DepthFramebuffer extends TrackedObject {
    private final int depthType;
    private final int framebufferId = glCreateFramebuffers();
    private int depthTextureId;
    private int width;
    private int height;

    DepthFramebuffer() {
        this(GL_DEPTH_COMPONENT24);
    }

    DepthFramebuffer(int depthType) {
        this.depthType = depthType;
    }

    boolean resize(int width, int height) {
        if (this.depthTextureId != 0 && this.width == width && this.height == height) {
            return false;
        }
        if (this.depthTextureId != 0) {
            ForgeOriginalVoxyGlResourceStatistics.textureFreed(this.depthTextureId);
            glDeleteTextures(this.depthTextureId);
            this.depthTextureId = 0;
        }
        this.depthTextureId = glCreateTextures(GL_TEXTURE_2D);
        ForgeOriginalVoxyGlResourceStatistics.textureAllocated(
                this.depthTextureId,
                (long) width * height * Integer.BYTES);
        glTextureStorage2D(this.depthTextureId, 1, this.depthType, width, height);
        glTextureParameteri(this.depthTextureId, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTextureParameteri(this.depthTextureId, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glTextureParameteri(this.depthTextureId, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTextureParameteri(this.depthTextureId, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        glNamedFramebufferTexture(this.framebufferId, this.getDepthAttachmentType(), this.depthTextureId, 0);
        int status = glCheckNamedFramebufferStatus(this.framebufferId, GL_FRAMEBUFFER);
        if (status != GL_FRAMEBUFFER_COMPLETE) {
            throw new IllegalStateException("depth-framebuffer-incomplete-" + status);
        }
        this.width = width;
        this.height = height;
        return true;
    }

    int getDepthAttachmentType() {
        return this.depthType == GL_DEPTH24_STENCIL8 ? GL_DEPTH_STENCIL_ATTACHMENT : GL_DEPTH_ATTACHMENT;
    }

    void clear(float depth) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            nglClearNamedFramebufferfv(this.framebufferId, GL_DEPTH, 0, stack.nfloat(depth));
        }
    }

    void clearStencil(int value) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            nglClearNamedFramebufferiv(this.framebufferId, GL_STENCIL, 0, stack.nint(value));
        }
    }

    void bind() {
        glBindFramebuffer(GL_FRAMEBUFFER, this.framebufferId);
    }

    int framebufferId() {
        return this.framebufferId;
    }

    int getDepthTex() {
        return this.depthTextureId;
    }

    int getFormat() {
        return this.depthType;
    }

    boolean depthTextureReady() {
        return this.depthTextureId != 0;
    }

    @Override
    public void free() {
        this.free0();
        if (this.depthTextureId != 0) {
            ForgeOriginalVoxyGlResourceStatistics.textureFreed(this.depthTextureId);
            glDeleteTextures(this.depthTextureId);
            this.depthTextureId = 0;
        }
        glDeleteFramebuffers(this.framebufferId);
    }
}
