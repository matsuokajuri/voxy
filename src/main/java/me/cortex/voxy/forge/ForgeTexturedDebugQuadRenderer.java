package me.cortex.voxy.forge;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.common.MinecraftForge;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL13C;
import org.lwjgl.opengl.GL15C;
import org.lwjgl.opengl.GL20C;
import org.lwjgl.opengl.GL30C;
import org.lwjgl.system.MemoryStack;

import java.nio.FloatBuffer;

final class ForgeTexturedDebugQuadRenderer {
    static final String STAGE = "G6_16_TINY_TEXTURED_DEBUG_QUAD";

    private static final int VERTEX_COUNT = 6;
    private static final int FLOATS_PER_VERTEX = 4;
    private static final int STRIDE_BYTES = FLOATS_PER_VERTEX * Float.BYTES;
    private static final float SCREEN_QUAD_HALF_SIZE = 0.33F;
    private static final float ALPHA = 1.0F;

    private final ForgeVoxyInstance instance;
    private final ForgeTexturedDebugShader shader = new ForgeTexturedDebugShader();
    private boolean enabled;
    private boolean sampleReady;
    private boolean atlasTextureReady;
    private boolean atlasPixelsUploaded;
    private boolean stale;
    private int vaoId;
    private int vboId;
    private int textureId;
    private int sampleModelId = -1;
    private int sourceBlockStateId = -1;
    private String sourceBlockState = "none";
    private String sourceSprite = "none";
    private String sourceSpriteAtlas = "none";
    private String sourceFace = "none";
    private String uvMin = "none";
    private String uvMax = "none";
    private int lastFrameDrawCalls;
    private int lastFrameVertices;
    private long drawCallsIssued;
    private long verticesDrawn;
    private String lastGlError = "none";
    private String lastGlErrorStage = "none";
    private long glErrorCount;
    private long stateRestoreFailures;
    private String lastStateRestoreError = "none";
    private String lastRenderSkippedReason = "CLEARED";

    ForgeTexturedDebugQuadRenderer(ForgeVoxyInstance instance) {
        this.instance = instance;
    }

    void register() {
        MinecraftForge.EVENT_BUS.addListener(this::onRenderLevelStage);
    }

    ForgeTexturedDebugStats buildSample() {
        if (!RenderSystem.isOnRenderThread()) {
            this.recordSkip("NOT_RENDER_THREAD");
            return this.createStatusSnapshot();
        }

        ForgeModelAtlasUploadStats uploadStatus = this.instance.getModelAtlasPixelUploader().createStatusSnapshot();
        if (!uploadStatus.sampleAtlasPixelsUploaded()) {
            uploadStatus = this.instance.getModelAtlasPixelUploader().uploadSample();
        }
        if (!uploadStatus.sampleAtlasPixelsUploaded()) {
            this.markStale("ATLAS_PIXELS_MISSING");
            return this.createStatusSnapshot();
        }

        ForgeTexturedDebugQuadSample sample = this.instance.getModelAtlasPixelUploader().createTexturedDebugQuadSample(0);
        if (!sample.ready()) {
            this.markStale("SAMPLE_MISSING");
            return this.createStatusSnapshot();
        }

        this.buildQuadBuffer(sample);
        this.textureId = sample.textureId();
        this.sampleModelId = sample.modelId();
        this.sourceBlockStateId = sample.blockStateId();
        this.sourceBlockState = sample.blockState();
        this.sourceSprite = sample.sourceSprite();
        this.sourceSpriteAtlas = sample.sourceSpriteAtlas();
        this.sourceFace = sample.faceName();
        this.sampleReady = true;
        this.atlasTextureReady = sample.textureId() != 0;
        this.atlasPixelsUploaded = true;
        this.stale = false;
        this.lastRenderSkippedReason = "none";
        return this.createStatusSnapshot();
    }

    void enable() {
        if (!this.sampleReady) {
            this.recordSkip("SAMPLE_MISSING");
            this.enabled = false;
            return;
        }
        this.enabled = true;
        this.lastRenderSkippedReason = "none";
    }

    void disable() {
        this.enabled = false;
        this.recordSkip("DISABLED");
    }

    void markStale(String reason) {
        this.enabled = false;
        this.sampleReady = false;
        this.atlasTextureReady = false;
        this.atlasPixelsUploaded = false;
        this.textureId = 0;
        this.stale = true;
        this.recordSkip(reason == null || reason.isBlank() ? "STALE" : reason);
    }

    void clear() {
        this.enabled = false;
        this.sampleReady = false;
        this.atlasTextureReady = false;
        this.atlasPixelsUploaded = false;
        this.stale = false;
        this.textureId = 0;
        this.sampleModelId = -1;
        this.sourceBlockStateId = -1;
        this.sourceBlockState = "none";
        this.sourceSprite = "none";
        this.sourceSpriteAtlas = "none";
        this.sourceFace = "none";
        this.uvMin = "none";
        this.uvMax = "none";
        this.lastFrameDrawCalls = 0;
        this.lastFrameVertices = 0;
        this.drawCallsIssued = 0L;
        this.verticesDrawn = 0L;
        this.lastGlError = "none";
        this.lastGlErrorStage = "none";
        this.glErrorCount = 0L;
        this.stateRestoreFailures = 0L;
        this.lastStateRestoreError = "none";
        this.lastRenderSkippedReason = "CLEARED";
        this.deleteBuffers();
        this.shader.close();
    }

    ForgeTexturedDebugStats createStatusSnapshot() {
        return new ForgeTexturedDebugStats(
                STAGE,
                this.enabled,
                this.enabled && this.sampleReady && this.atlasTextureReady && this.atlasPixelsUploaded && !this.stale,
                this.shader.shaderCompiled(),
                this.shader.programCreated(),
                this.shader.lastShaderError(),
                this.sampleReady,
                this.atlasTextureReady,
                this.atlasPixelsUploaded,
                this.sampleModelId,
                this.sourceBlockStateId,
                this.sourceBlockState,
                this.sourceSprite,
                this.sourceSpriteAtlas,
                this.sourceFace,
                this.uvMin,
                this.uvMax,
                this.lastFrameDrawCalls,
                this.lastFrameVertices,
                this.drawCallsIssued,
                this.verticesDrawn,
                this.lastGlError,
                this.lastGlErrorStage,
                this.glErrorCount,
                this.stateRestoreFailures,
                this.lastStateRestoreError,
                this.lastRenderSkippedReason,
                this.stale,
                false,
                false
        );
    }

    private void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }
        if (!this.enabled) {
            this.recordSkip("DISABLED");
            return;
        }
        if (!this.sampleReady) {
            this.recordSkip("SAMPLE_MISSING");
            return;
        }
        ForgeModelAtlasUploadStats atlasStatus = this.instance.getModelAtlasPixelUploader().createStatusSnapshot();
        if (!atlasStatus.sampleAtlasPixelsUploaded()
                || atlasStatus.atlasTextureObjectId() == 0
                || atlasStatus.atlasTextureObjectId() != this.textureId) {
            this.markStale("ATLAS_STALE");
            return;
        }
        if (this.vaoId == 0 || this.vboId == 0) {
            this.recordSkip("QUAD_BUFFER_MISSING");
            return;
        }
        if (!RenderSystem.isOnRenderThread()) {
            this.recordSkip("NOT_RENDER_THREAD");
            return;
        }
        if (!this.shader.ensureReady()) {
            this.recordSkip("SHADER_UNAVAILABLE");
            return;
        }

        drainGlErrors();
        StateGuard guard = null;
        boolean restoreFailed = false;
        String restoreError = "none";
        int drawError = GL11C.GL_NO_ERROR;
        try {
            guard = StateGuard.capture();
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.disableDepthTest();
            RenderSystem.disableCull();
            RenderSystem.depthMask(false);

            this.shader.bind(new Matrix4f(), ALPHA);
            GL13C.glActiveTexture(GL13C.GL_TEXTURE0);
            GL11C.glBindTexture(GL11C.GL_TEXTURE_2D, this.textureId);
            GL30C.glBindVertexArray(this.vaoId);
            GL11C.glDrawArrays(GL11C.GL_TRIANGLES, 0, VERTEX_COUNT);
            drawError = GL11C.glGetError();
        } catch (RuntimeException e) {
            this.lastGlError = e.getClass().getSimpleName() + ": " + e.getMessage();
            this.lastGlErrorStage = "TEXTURED_DEBUG_DRAW_EXCEPTION";
            this.glErrorCount++;
            this.recordSkip("DRAW_EXCEPTION");
            return;
        } finally {
            try {
                this.shader.unbind();
                if (guard != null) {
                    guard.restore();
                }
            } catch (RuntimeException e) {
                restoreFailed = true;
                restoreError = e.getClass().getSimpleName() + ": " + e.getMessage();
            }
        }

        if (restoreFailed) {
            this.stateRestoreFailures++;
            this.lastStateRestoreError = restoreError;
        } else {
            this.lastStateRestoreError = "none";
        }
        if (drawError != GL11C.GL_NO_ERROR) {
            this.lastGlError = glErrorName(drawError);
            this.lastGlErrorStage = "TEXTURED_DEBUG_DRAW";
            this.glErrorCount++;
        } else {
            this.lastGlError = "none";
            this.lastGlErrorStage = "none";
        }
        this.lastFrameDrawCalls = 1;
        this.lastFrameVertices = VERTEX_COUNT;
        this.drawCallsIssued++;
        this.verticesDrawn += VERTEX_COUNT;
        this.lastRenderSkippedReason = "none";
    }

    private void buildQuadBuffer(ForgeTexturedDebugQuadSample sample) {
        float u0 = sample.tileX() / (float) sample.textureWidth();
        float v0 = sample.tileY() / (float) sample.textureHeight();
        float u1 = (sample.tileX() + ForgeModelAtlasLayout.MODEL_TEXTURE_SIZE) / (float) sample.textureWidth();
        float v1 = (sample.tileY() + ForgeModelAtlasLayout.MODEL_TEXTURE_SIZE) / (float) sample.textureHeight();
        this.uvMin = formatUv(u0, v0);
        this.uvMax = formatUv(u1, v1);

        float x0 = -SCREEN_QUAD_HALF_SIZE;
        float x1 = SCREEN_QUAD_HALF_SIZE;
        float y0 = -SCREEN_QUAD_HALF_SIZE;
        float y1 = SCREEN_QUAD_HALF_SIZE;
        float[] vertices = {
                x0, y0, u0, v0,
                x1, y0, u1, v0,
                x1, y1, u1, v1,
                x1, y1, u1, v1,
                x0, y1, u0, v1,
                x0, y0, u0, v0
        };

        int oldVao = GL11C.glGetInteger(GL30C.GL_VERTEX_ARRAY_BINDING);
        int oldArrayBuffer = GL11C.glGetInteger(GL15C.GL_ARRAY_BUFFER_BINDING);
        if (this.vaoId == 0) {
            this.vaoId = GL30C.glGenVertexArrays();
        }
        if (this.vboId == 0) {
            this.vboId = GL15C.glGenBuffers();
        }
        try (MemoryStack stack = MemoryStack.stackPush()) {
            FloatBuffer buffer = stack.mallocFloat(vertices.length);
            buffer.put(vertices).flip();
            GL30C.glBindVertexArray(this.vaoId);
            GL15C.glBindBuffer(GL15C.GL_ARRAY_BUFFER, this.vboId);
            GL15C.glBufferData(GL15C.GL_ARRAY_BUFFER, buffer, GL15C.GL_STATIC_DRAW);
            GL20C.glEnableVertexAttribArray(0);
            GL20C.glVertexAttribPointer(0, 2, GL11C.GL_FLOAT, false, STRIDE_BYTES, 0L);
            GL20C.glEnableVertexAttribArray(1);
            GL20C.glVertexAttribPointer(1, 2, GL11C.GL_FLOAT, false, STRIDE_BYTES, 2L * Float.BYTES);
        } finally {
            GL30C.glBindVertexArray(oldVao);
            GL15C.glBindBuffer(GL15C.GL_ARRAY_BUFFER, oldArrayBuffer);
        }
    }

    private void deleteBuffers() {
        int oldVao = this.vaoId;
        int oldVbo = this.vboId;
        this.vaoId = 0;
        this.vboId = 0;
        if (oldVao != 0) {
            if (RenderSystem.isOnRenderThread()) {
                GL30C.glDeleteVertexArrays(oldVao);
            } else {
                RenderSystem.recordRenderCall(() -> GL30C.glDeleteVertexArrays(oldVao));
            }
        }
        if (oldVbo != 0) {
            if (RenderSystem.isOnRenderThread()) {
                GL15C.glDeleteBuffers(oldVbo);
            } else {
                RenderSystem.recordRenderCall(() -> GL15C.glDeleteBuffers(oldVbo));
            }
        }
    }

    private void recordSkip(String reason) {
        this.lastFrameDrawCalls = 0;
        this.lastFrameVertices = 0;
        this.lastRenderSkippedReason = reason == null || reason.isBlank() ? "UNKNOWN" : reason;
    }

    private static String formatUv(float u, float v) {
        return String.format("%.6f,%.6f", u, v);
    }

    private static void drainGlErrors() {
        while (GL11C.glGetError() != GL11C.GL_NO_ERROR) {
            // Drain stale errors before the tiny textured debug draw.
        }
    }

    private static String glErrorName(int error) {
        return switch (error) {
            case GL11C.GL_NO_ERROR -> "none";
            case GL11C.GL_INVALID_ENUM -> "GL_INVALID_ENUM";
            case GL11C.GL_INVALID_VALUE -> "GL_INVALID_VALUE";
            case GL11C.GL_INVALID_OPERATION -> "GL_INVALID_OPERATION";
            case GL11C.GL_OUT_OF_MEMORY -> "GL_OUT_OF_MEMORY";
            default -> "0x" + Integer.toHexString(error);
        };
    }

    private record StateGuard(
            int program,
            int vertexArray,
            int arrayBuffer,
            int activeTexture,
            int texture0Binding,
            boolean depthTest,
            boolean blend,
            boolean cull,
            boolean depthMask
    ) {
        static StateGuard capture() {
            int active = GL11C.glGetInteger(GL13C.GL_ACTIVE_TEXTURE);
            GL13C.glActiveTexture(GL13C.GL_TEXTURE0);
            int textureBinding = GL11C.glGetInteger(GL11C.GL_TEXTURE_BINDING_2D);
            GL13C.glActiveTexture(active);
            return new StateGuard(
                    GL11C.glGetInteger(GL20C.GL_CURRENT_PROGRAM),
                    GL11C.glGetInteger(GL30C.GL_VERTEX_ARRAY_BINDING),
                    GL11C.glGetInteger(GL15C.GL_ARRAY_BUFFER_BINDING),
                    active,
                    textureBinding,
                    GL11C.glIsEnabled(GL11C.GL_DEPTH_TEST),
                    GL11C.glIsEnabled(GL11C.GL_BLEND),
                    GL11C.glIsEnabled(GL11C.GL_CULL_FACE),
                    GL11C.glGetBoolean(GL11C.GL_DEPTH_WRITEMASK)
            );
        }

        void restore() {
            GL20C.glUseProgram(this.program);
            GL30C.glBindVertexArray(this.vertexArray);
            GL15C.glBindBuffer(GL15C.GL_ARRAY_BUFFER, this.arrayBuffer);
            GL13C.glActiveTexture(GL13C.GL_TEXTURE0);
            GL11C.glBindTexture(GL11C.GL_TEXTURE_2D, this.texture0Binding);
            GL13C.glActiveTexture(this.activeTexture);
            if (this.depthTest) {
                GL11C.glEnable(GL11C.GL_DEPTH_TEST);
            } else {
                GL11C.glDisable(GL11C.GL_DEPTH_TEST);
            }
            if (this.blend) {
                GL11C.glEnable(GL11C.GL_BLEND);
            } else {
                GL11C.glDisable(GL11C.GL_BLEND);
            }
            if (this.cull) {
                GL11C.glEnable(GL11C.GL_CULL_FACE);
            } else {
                GL11C.glDisable(GL11C.GL_CULL_FACE);
            }
            GL11C.glDepthMask(this.depthMask);
        }
    }
}
