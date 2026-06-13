package me.cortex.voxy.forge;

import com.mojang.blaze3d.systems.RenderSystem;

import static org.lwjgl.opengl.GL11C.GL_BLEND;
import static org.lwjgl.opengl.GL11C.GL_CULL_FACE;
import static org.lwjgl.opengl.GL11C.GL_DEPTH_TEST;
import static org.lwjgl.opengl.GL11C.GL_DEPTH_WRITEMASK;
import static org.lwjgl.opengl.GL11C.GL_NO_ERROR;
import static org.lwjgl.opengl.GL11C.glGetBoolean;
import static org.lwjgl.opengl.GL11C.glGetError;
import static org.lwjgl.opengl.GL11C.glGetInteger;
import static org.lwjgl.opengl.GL11C.glIsEnabled;
import static org.lwjgl.opengl.GL15C.GL_ARRAY_BUFFER;
import static org.lwjgl.opengl.GL15C.GL_ARRAY_BUFFER_BINDING;
import static org.lwjgl.opengl.GL15C.glBindBuffer;
import static org.lwjgl.opengl.GL20C.GL_CURRENT_PROGRAM;
import static org.lwjgl.opengl.GL20C.glUseProgram;
import static org.lwjgl.opengl.GL30C.GL_VERTEX_ARRAY_BINDING;
import static org.lwjgl.opengl.GL30C.glBindBufferBase;
import static org.lwjgl.opengl.GL30C.glBindVertexArray;
import static org.lwjgl.opengl.GL30C.glGetIntegeri;
import static org.lwjgl.opengl.GL40C.GL_DRAW_INDIRECT_BUFFER;
import static org.lwjgl.opengl.GL40C.GL_DRAW_INDIRECT_BUFFER_BINDING;
import static org.lwjgl.opengl.GL43C.GL_SHADER_STORAGE_BUFFER;
import static org.lwjgl.opengl.GL43C.GL_SHADER_STORAGE_BUFFER_BINDING;

final class ForgeDirectGpuGeometryRenderStateGuard {
    private final int currentProgram;
    private final int vertexArrayBinding;
    private final int arrayBufferBinding;
    private final int drawIndirectBufferBinding;
    private final int shaderStorageBufferBinding0;
    private final int shaderStorageBufferBinding1;
    private final boolean depthTestEnabled;
    private final boolean depthMask;
    private final boolean blendEnabled;
    private final boolean cullEnabled;

    private ForgeDirectGpuGeometryRenderStateGuard() {
        this.currentProgram = glGetInteger(GL_CURRENT_PROGRAM);
        this.vertexArrayBinding = glGetInteger(GL_VERTEX_ARRAY_BINDING);
        this.arrayBufferBinding = glGetInteger(GL_ARRAY_BUFFER_BINDING);
        this.drawIndirectBufferBinding = glGetInteger(GL_DRAW_INDIRECT_BUFFER_BINDING);
        this.shaderStorageBufferBinding0 = glGetIntegeri(GL_SHADER_STORAGE_BUFFER_BINDING, 0);
        this.shaderStorageBufferBinding1 = glGetIntegeri(GL_SHADER_STORAGE_BUFFER_BINDING, ForgeDirectGpuGeometryDrawItemBuffer.BINDING_INDEX);
        this.depthTestEnabled = glIsEnabled(GL_DEPTH_TEST);
        this.depthMask = glGetBoolean(GL_DEPTH_WRITEMASK);
        this.blendEnabled = glIsEnabled(GL_BLEND);
        this.cullEnabled = glIsEnabled(GL_CULL_FACE);
    }

    static ForgeDirectGpuGeometryRenderStateGuard capture() {
        if (!RenderSystem.isOnRenderThread()) {
            throw new IllegalStateException("not-render-thread");
        }
        return new ForgeDirectGpuGeometryRenderStateGuard();
    }

    RestoreResult restore() {
        if (!RenderSystem.isOnRenderThread()) {
            return new RestoreResult(false, "not-render-thread");
        }
        try {
            glUseProgram(this.currentProgram);
            glBindVertexArray(this.vertexArrayBinding);
            glBindBuffer(GL_ARRAY_BUFFER, this.arrayBufferBinding);
            glBindBuffer(GL_DRAW_INDIRECT_BUFFER, this.drawIndirectBufferBinding);
            glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 0, this.shaderStorageBufferBinding0);
            glBindBufferBase(GL_SHADER_STORAGE_BUFFER, ForgeDirectGpuGeometryDrawItemBuffer.BINDING_INDEX, this.shaderStorageBufferBinding1);
            restoreDepthTest();
            restoreBlend();
            restoreCull();
            RenderSystem.depthMask(this.depthMask);
            int error = glGetError();
            return error == GL_NO_ERROR
                    ? new RestoreResult(true, "none")
                    : new RestoreResult(false, ForgeDirectGpuGeometryShader.formatGlError(error));
        } catch (RuntimeException e) {
            return new RestoreResult(false, e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    private void restoreDepthTest() {
        if (this.depthTestEnabled) {
            RenderSystem.enableDepthTest();
        } else {
            RenderSystem.disableDepthTest();
        }
    }

    private void restoreBlend() {
        if (this.blendEnabled) {
            RenderSystem.enableBlend();
        } else {
            RenderSystem.disableBlend();
        }
    }

    private void restoreCull() {
        if (this.cullEnabled) {
            RenderSystem.enableCull();
        } else {
            RenderSystem.disableCull();
        }
    }

    record RestoreResult(boolean success, String error) {
    }
}
