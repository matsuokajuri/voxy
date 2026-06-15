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
import static org.lwjgl.opengl.GL15C.GL_ELEMENT_ARRAY_BUFFER;
import static org.lwjgl.opengl.GL15C.GL_ELEMENT_ARRAY_BUFFER_BINDING;
import static org.lwjgl.opengl.GL15C.glBindBuffer;
import static org.lwjgl.opengl.GL20C.GL_CURRENT_PROGRAM;
import static org.lwjgl.opengl.GL20C.glUseProgram;
import static org.lwjgl.opengl.GL30C.GL_VERTEX_ARRAY_BINDING;
import static org.lwjgl.opengl.GL30C.glBindBufferBase;
import static org.lwjgl.opengl.GL30C.glBindVertexArray;
import static org.lwjgl.opengl.GL40C.GL_DRAW_INDIRECT_BUFFER;
import static org.lwjgl.opengl.GL40C.GL_DRAW_INDIRECT_BUFFER_BINDING;
import static org.lwjgl.opengl.GL30C.glGetIntegeri;
import static org.lwjgl.opengl.GL43C.GL_SHADER_STORAGE_BUFFER;
import static org.lwjgl.opengl.GL43C.GL_SHADER_STORAGE_BUFFER_BINDING;

final class ForgeMdicDebugRenderStateGuard {
    private final int currentProgram;
    private final int vertexArrayBinding;
    private final int arrayBufferBinding;
    private final int elementArrayBufferBinding;
    private final int drawIndirectBufferBinding;
    private final int geometryStorageBinding;
    private final int directDrawItemStorageBinding;
    private final int mdicCommandStorageBinding;
    private final boolean depthTestEnabled;
    private final boolean depthMask;
    private final boolean blendEnabled;
    private final boolean cullEnabled;

    private ForgeMdicDebugRenderStateGuard() {
        this.currentProgram = glGetInteger(GL_CURRENT_PROGRAM);
        this.vertexArrayBinding = glGetInteger(GL_VERTEX_ARRAY_BINDING);
        this.arrayBufferBinding = glGetInteger(GL_ARRAY_BUFFER_BINDING);
        this.elementArrayBufferBinding = glGetInteger(GL_ELEMENT_ARRAY_BUFFER_BINDING);
        this.drawIndirectBufferBinding = glGetInteger(GL_DRAW_INDIRECT_BUFFER_BINDING);
        this.geometryStorageBinding = glGetIntegeri(GL_SHADER_STORAGE_BUFFER_BINDING, 0);
        this.directDrawItemStorageBinding = glGetIntegeri(GL_SHADER_STORAGE_BUFFER_BINDING, ForgeDirectGpuGeometryDrawItemBuffer.BINDING_INDEX);
        this.mdicCommandStorageBinding = glGetIntegeri(GL_SHADER_STORAGE_BUFFER_BINDING, ForgeMdicDebugShader.COMMAND_BINDING_INDEX);
        this.depthTestEnabled = glIsEnabled(GL_DEPTH_TEST);
        this.depthMask = glGetBoolean(GL_DEPTH_WRITEMASK);
        this.blendEnabled = glIsEnabled(GL_BLEND);
        this.cullEnabled = glIsEnabled(GL_CULL_FACE);
    }

    static ForgeMdicDebugRenderStateGuard capture() {
        if (!RenderSystem.isOnRenderThread()) {
            throw new IllegalStateException("not-render-thread");
        }
        return new ForgeMdicDebugRenderStateGuard();
    }

    RestoreResult restore() {
        if (!RenderSystem.isOnRenderThread()) {
            return new RestoreResult(false, "not-render-thread");
        }
        try {
            glUseProgram(this.currentProgram);
            glBindVertexArray(this.vertexArrayBinding);
            glBindBuffer(GL_ARRAY_BUFFER, this.arrayBufferBinding);
            glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, this.elementArrayBufferBinding);
            glBindBuffer(GL_DRAW_INDIRECT_BUFFER, this.drawIndirectBufferBinding);
            glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 0, this.geometryStorageBinding);
            glBindBufferBase(GL_SHADER_STORAGE_BUFFER, ForgeDirectGpuGeometryDrawItemBuffer.BINDING_INDEX, this.directDrawItemStorageBinding);
            glBindBufferBase(GL_SHADER_STORAGE_BUFFER, ForgeMdicDebugShader.COMMAND_BINDING_INDEX, this.mdicCommandStorageBinding);
            restoreDepthTest();
            restoreBlend();
            restoreCull();
            RenderSystem.depthMask(this.depthMask);
            int error = glGetError();
            return error == GL_NO_ERROR
                    ? new RestoreResult(true, "none")
                    : new RestoreResult(false, ForgeMdicDebugShader.formatGlError(error));
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
