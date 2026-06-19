package me.cortex.voxy.forge;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
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

/**
 * Deprecated historical textured readback proof renderer.
 *
 * <p>This class is not part of the formal original-Voxy parity route. Keep it
 * isolated until command/status references are retired.</p>
 */
@Deprecated(forRemoval = false)
final class ForgeTexturedReadbackRenderer {
    static final String STAGE = "G6_17_TEXTURED_GL_HEAP_READBACK_SAMPLE";

    private static final int FLOATS_PER_VERTEX = 5;
    private static final int STRIDE_BYTES = FLOATS_PER_VERTEX * Float.BYTES;
    private static final float ALPHA = 1.0F;

    private final ForgeVoxyInstance instance;
    private final ForgeTexturedReadbackShader shader = new ForgeTexturedReadbackShader();
    private boolean enabled;
    private boolean sampleReady;
    private boolean atlasTextureReady;
    private boolean atlasPixelsUploaded;
    private boolean geometryHeapReady;
    private boolean metadataReady;
    private boolean geometryBacked;
    private boolean fallbackFixedQuad;
    private boolean stale;
    private boolean screenSpace;
    private int vaoId;
    private int vboId;
    private int textureId;
    private int sampleModelId = -1;
    private int sourceBlockStateId = -1;
    private String sourceBlockState = "none";
    private String sourceSprite = "none";
    private String sourceSpriteAtlas = "none";
    private int matchingSections;
    private int matchingRecords;
    private int builtQuads;
    private int builtVertices;
    private int uploadedVertices;
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

    ForgeTexturedReadbackRenderer(ForgeVoxyInstance instance) {
        this.instance = instance;
    }

    void register() {
        MinecraftForge.EVENT_BUS.addListener(this::onRenderLevelStage);
    }

    ForgeTexturedReadbackStats buildSample() {
        if (!RenderSystem.isOnRenderThread()) {
            this.recordSkip("NOT_RENDER_THREAD");
            return this.createStatusSnapshot();
        }

        ForgeTexturedReadbackMesh mesh = ForgeTexturedReadbackMeshBuilder.buildSample(this.instance);
        if (!mesh.success() || mesh.vertices().length == 0) {
            this.markStale(mesh.reason());
            this.geometryHeapReady = mesh.geometryHeapReady();
            this.metadataReady = mesh.metadataReady();
            return this.createStatusSnapshot();
        }

        ForgeTexturedDebugQuadSample textureSample = this.instance.getModelAtlasPixelUploader().createTexturedDebugQuadSample(0);
        if (!textureSample.ready()) {
            this.markStale("ATLAS_SAMPLE_MISSING");
            return this.createStatusSnapshot();
        }

        this.uploadMesh(mesh.vertices());
        this.textureId = textureSample.textureId();
        this.sampleModelId = mesh.sampleModelId();
        this.sourceBlockStateId = mesh.sourceBlockStateId();
        this.sourceBlockState = mesh.sourceBlockState();
        this.sourceSprite = mesh.sourceSprite();
        this.sourceSpriteAtlas = mesh.sourceSpriteAtlas();
        this.sampleReady = true;
        this.atlasTextureReady = textureSample.textureId() != 0;
        this.atlasPixelsUploaded = true;
        this.geometryHeapReady = mesh.geometryHeapReady();
        this.metadataReady = mesh.metadataReady();
        this.geometryBacked = mesh.geometryBacked();
        this.fallbackFixedQuad = mesh.fallbackFixedQuad();
        this.matchingSections = mesh.matchingSections();
        this.matchingRecords = mesh.matchingRecords();
        this.builtQuads = mesh.builtQuads();
        this.builtVertices = mesh.builtVertices();
        this.uploadedVertices = mesh.builtVertices();
        this.screenSpace = mesh.screenSpace();
        this.stale = false;
        this.lastRenderSkippedReason = "none";
        this.lastFrameDrawCalls = 0;
        this.lastFrameVertices = 0;
        return this.createStatusSnapshot();
    }

    void enable() {
        if (!this.sampleReady || this.uploadedVertices <= 0) {
            this.enabled = false;
            this.recordSkip("SAMPLE_MISSING");
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
        this.geometryBacked = false;
        this.fallbackFixedQuad = false;
        this.textureId = 0;
        this.stale = true;
        this.uploadedVertices = 0;
        this.builtVertices = 0;
        this.builtQuads = 0;
        this.matchingSections = 0;
        this.matchingRecords = 0;
        this.deleteBuffers();
        this.recordSkip(reason == null || reason.isBlank() ? "STALE" : reason);
    }

    void clear() {
        this.enabled = false;
        this.sampleReady = false;
        this.atlasTextureReady = false;
        this.atlasPixelsUploaded = false;
        this.geometryHeapReady = false;
        this.metadataReady = false;
        this.geometryBacked = false;
        this.fallbackFixedQuad = false;
        this.stale = false;
        this.screenSpace = false;
        this.textureId = 0;
        this.sampleModelId = -1;
        this.sourceBlockStateId = -1;
        this.sourceBlockState = "none";
        this.sourceSprite = "none";
        this.sourceSpriteAtlas = "none";
        this.matchingSections = 0;
        this.matchingRecords = 0;
        this.builtQuads = 0;
        this.builtVertices = 0;
        this.uploadedVertices = 0;
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

    ForgeTexturedReadbackStats createStatusSnapshot() {
        return new ForgeTexturedReadbackStats(
                STAGE,
                this.enabled,
                this.enabled && this.sampleReady && this.atlasTextureReady && this.atlasPixelsUploaded && this.uploadedVertices > 0 && !this.stale,
                this.shader.shaderCompiled(),
                this.shader.programCreated(),
                this.shader.lastShaderError(),
                this.sampleReady,
                this.atlasTextureReady,
                this.atlasPixelsUploaded,
                this.geometryHeapReady,
                this.metadataReady,
                this.geometryBacked,
                this.fallbackFixedQuad,
                this.sampleModelId,
                this.sourceBlockStateId,
                this.sourceBlockState,
                this.sourceSprite,
                this.sourceSpriteAtlas,
                this.matchingSections,
                this.matchingRecords,
                this.builtQuads,
                this.builtVertices,
                this.uploadedVertices,
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
        if (!this.sampleReady || this.uploadedVertices <= 0) {
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
            this.recordSkip("MESH_BUFFER_MISSING");
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

            Matrix4f modelView;
            Matrix4f projection;
            if (this.screenSpace) {
                modelView = new Matrix4f();
                projection = new Matrix4f();
            } else {
                Minecraft minecraft = Minecraft.getInstance();
                if (minecraft.level == null || minecraft.player == null || event.getCamera() == null) {
                    this.recordSkip("WORLD_MISSING");
                    return;
                }
                PoseStack poseStack = event.getPoseStack();
                poseStack.pushPose();
                try {
                    var cameraPos = event.getCamera().getPosition();
                    poseStack.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);
                    modelView = new Matrix4f(poseStack.last().pose());
                    projection = new Matrix4f(event.getProjectionMatrix());
                } finally {
                    poseStack.popPose();
                }
            }

            this.shader.bind(modelView, projection, ALPHA);
            GL13C.glActiveTexture(GL13C.GL_TEXTURE0);
            GL11C.glBindTexture(GL11C.GL_TEXTURE_2D, this.textureId);
            GL30C.glBindVertexArray(this.vaoId);
            GL11C.glDrawArrays(GL11C.GL_TRIANGLES, 0, this.uploadedVertices);
            drawError = GL11C.glGetError();
        } catch (RuntimeException e) {
            this.lastGlError = e.getClass().getSimpleName() + ": " + e.getMessage();
            this.lastGlErrorStage = "TEXTURED_READBACK_DRAW_EXCEPTION";
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
            this.lastGlErrorStage = "TEXTURED_READBACK_DRAW";
            this.glErrorCount++;
        } else {
            this.lastGlError = "none";
            this.lastGlErrorStage = "none";
        }
        this.lastFrameDrawCalls = 1;
        this.lastFrameVertices = this.uploadedVertices;
        this.drawCallsIssued++;
        this.verticesDrawn += this.uploadedVertices;
        this.lastRenderSkippedReason = "none";
    }

    private void uploadMesh(float[] vertices) {
        if (this.vaoId == 0) {
            this.vaoId = GL30C.glGenVertexArrays();
        }
        if (this.vboId == 0) {
            this.vboId = GL15C.glGenBuffers();
        }
        int oldVao = GL11C.glGetInteger(GL30C.GL_VERTEX_ARRAY_BINDING);
        int oldArrayBuffer = GL11C.glGetInteger(GL15C.GL_ARRAY_BUFFER_BINDING);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            FloatBuffer buffer = stack.mallocFloat(vertices.length);
            buffer.put(vertices).flip();
            GL30C.glBindVertexArray(this.vaoId);
            GL15C.glBindBuffer(GL15C.GL_ARRAY_BUFFER, this.vboId);
            GL15C.glBufferData(GL15C.GL_ARRAY_BUFFER, buffer, GL15C.GL_STATIC_DRAW);
            GL20C.glEnableVertexAttribArray(0);
            GL20C.glVertexAttribPointer(0, 3, GL11C.GL_FLOAT, false, STRIDE_BYTES, 0L);
            GL20C.glEnableVertexAttribArray(1);
            GL20C.glVertexAttribPointer(1, 2, GL11C.GL_FLOAT, false, STRIDE_BYTES, 3L * Float.BYTES);
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

    private static void drainGlErrors() {
        while (GL11C.glGetError() != GL11C.GL_NO_ERROR) {
            // Drain stale errors before this scoped debug draw.
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
