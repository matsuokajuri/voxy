package me.cortex.voxy.forge;

import com.mojang.blaze3d.systems.RenderSystem;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL12C;
import org.lwjgl.opengl.GL13C;
import org.lwjgl.opengl.GL15C;
import org.lwjgl.opengl.GL20C;
import org.lwjgl.opengl.GL21C;
import org.lwjgl.opengl.GL30C;
import org.lwjgl.opengl.GL33C;
import org.lwjgl.opengl.GL43C;
import org.lwjgl.opengl.GL45C;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.zip.CRC32;

final class ForgeFormalTexturedShaderPreview {
    static final String STAGE = "J3_FORMAL_TEXTURED_SHADER_PREVIEW_PROTOTYPE";
    private static final int PREVIEW_MODEL_ID_BINDING_INDEX = 6;
    private static final int MAX_PREVIEW_MODELS = 4;
    private static final int PREVIEW_WIDTH = 64;
    private static final int PREVIEW_HEIGHT = 64;
    private static final int BYTES_PER_PIXEL = 4;

    private static final String VERTEX_SOURCE = """
            #version 430 core

            layout(std430, binding = 3) readonly buffer ModelData {
                uint modelData[];
            };

            layout(std430, binding = 4) readonly buffer ModelColour {
                uint modelColour[];
            };

            layout(std430, binding = 6) readonly buffer PreviewIds {
                uint previewIds[];
            };

            uniform uint previewModelCount;

            out vec2 vAtlasUv;
            out vec4 vTint;
            flat out uint vModelId;
            flat out uint vFaceData;
            flat out uint vFlagsA;
            flat out uint vCustomId;

            const uint WORDS_PER_MODEL = 16u;
            const uint MODEL_TEXTURE_SIZE = 16u;
            const uint FACES_PER_MODEL_X = 3u;
            const uint FACES_PER_MODEL_Y = 2u;
            const float ATLAS_WIDTH = 12288.0;
            const float ATLAS_HEIGHT = 8192.0;

            const vec2 CORNERS[6] = vec2[](
                vec2(0.0, 0.0),
                vec2(1.0, 0.0),
                vec2(1.0, 1.0),
                vec2(0.0, 0.0),
                vec2(1.0, 1.0),
                vec2(0.0, 1.0)
            );

            void main() {
                uint vertex = uint(gl_VertexID);
                uint quadIndex = vertex / 6u;
                uint cornerIndex = vertex - quadIndex * 6u;
                uint count = max(previewModelCount, 1u);
                uint modelId = previewIds[quadIndex];
                uint wordBase = modelId * WORDS_PER_MODEL;
                uint faceData = modelData[wordBase];
                uint flagsA = modelData[wordBase + 6u];
                uint customId = modelData[wordBase + 8u];
                uint colour = modelColour[modelId];

                uint minU = faceData & 15u;
                uint maxU = (faceData >> 4u) & 15u;
                uint minV = (faceData >> 8u) & 15u;
                uint maxV = (faceData >> 12u) & 15u;
                maxU = max(maxU, minU);
                maxV = max(maxV, minV);

                vec2 corner = CORNERS[cornerIndex];
                float localU = (float(minU) + corner.x * float(maxU - minU + 1u)) / 16.0;
                float localV = (float(minV) + corner.y * float(maxV - minV + 1u)) / 16.0;
                uint baseX = (modelId & 255u) * MODEL_TEXTURE_SIZE * FACES_PER_MODEL_X;
                uint baseY = ((modelId >> 8u) & 255u) * MODEL_TEXTURE_SIZE * FACES_PER_MODEL_Y;
                vec2 atlasPixel = vec2(float(baseX), float(baseY)) + vec2(localU, localV) * 16.0 + vec2(0.5);
                vAtlasUv = atlasPixel / vec2(ATLAS_WIDTH, ATLAS_HEIGHT);

                float r = float(colour & 255u) / 255.0;
                float g = float((colour >> 8u) & 255u) / 255.0;
                float b = float((colour >> 16u) & 255u) / 255.0;
                float a = float((colour >> 24u) & 255u) / 255.0;
                vTint = vec4(r, g, b, max(a, 1.0));

                float left = -0.92 + 1.84 * (float(quadIndex) / float(count)) + 0.04;
                float right = -0.92 + 1.84 * (float(quadIndex + 1u) / float(count)) - 0.04;
                float x = mix(left, right, corner.x);
                float y = mix(-0.68, 0.68, corner.y);
                gl_Position = vec4(x, y, 0.0, 1.0);

                vModelId = modelId;
                vFaceData = faceData;
                vFlagsA = flagsA;
                vCustomId = customId;
            }
            """;

    private static final String FRAGMENT_SOURCE = """
            #version 430 core

            layout(binding = 0) uniform sampler2D blockModelAtlas;

            in vec2 vAtlasUv;
            in vec4 vTint;
            flat in uint vModelId;
            flat in uint vFaceData;
            flat in uint vFlagsA;
            flat in uint vCustomId;

            out vec4 fragColor;

            void main() {
                if ((vCustomId & 2147483648u) != 0u) {
                    discard;
                }
                vec4 texel = texture(blockModelAtlas, vAtlasUv);
                float shadedMarker = ((vFlagsA & 8u) != 0u) ? 1.0 : 1.0;
                float faceMarker = float((vFaceData ^ vModelId) & 1u) / 255.0;
                fragColor = vec4(min(vec3(1.0), texel.rgb * vTint.rgb * shadedMarker + vec3(faceMarker)), texel.a);
            }
            """;

    private final ForgeVoxyInstance instance;
    private long buildRuns;
    private long auditRuns;
    private long clearRuns;
    private long auditFailures;
    private boolean prototypeReady;
    private boolean previewReady;
    private boolean shaderCompileOk;
    private boolean programLinkOk;
    private int previewProgramId;
    private int previewFramebufferId;
    private int previewTextureId;
    private int previewVertexArrayId;
    private int previewModelIdBufferId;
    private boolean modelDataBindingOk;
    private boolean modelColourBindingOk;
    private boolean atlasTextureBindingOk;
    private boolean samplerBindingOk;
    private boolean previewFramebufferCreated;
    private boolean previewFramebufferComplete;
    private boolean previewReadbackOk;
    private int previewPixelMismatches;
    private int previewChecksumCount;
    private String previewChecksums = "";
    private int safeSetModelCount;
    private int previewModelCount;
    private String previewModelIds = "";
    private boolean faceDataUsed;
    private boolean modelColourUsed;
    private boolean atlasSampleUsed;
    private boolean visiblePreviewEnabled;
    private boolean stale;
    private boolean requiresRebuild;
    private String lifecycleState = "UNINITIALIZED";
    private String lastLifecycleEvent = "initialized";
    private String staleReason = "none";
    private String lastGlError = "none";
    private String lastFailureReason = "none";
    private ForgeFormalTexturedShaderPreviewAuditResult lastAudit =
            ForgeFormalTexturedShaderPreviewAuditResult.failure("none", 0.0D);

    ForgeFormalTexturedShaderPreview(ForgeVoxyInstance instance) {
        this.instance = instance;
    }

    ForgeFormalTexturedShaderPreviewStats build() {
        this.buildRuns++;
        this.lifecycleState = "BUILDING";
        this.lastLifecycleEvent = "build";
        this.staleReason = "none";
        this.lastFailureReason = "none";
        this.resetPreviewFlags();

        if (!RenderSystem.isOnRenderThread()) {
            this.fail("not-render-thread");
            return this.createStatusSnapshot();
        }

        ForgeFormalShaderProgramStats j2 = this.instance.getFormalShaderProgramValidator().build();
        ForgeFormalModelStoreStats store = this.instance.getFormalModelStore().createStatusSnapshot();
        ForgeMultiBlockFormalBakeUploadStats multi = this.instance.getMultiBlockFormalBakeUpload().createStatusSnapshot();
        List<ForgeFormalUploadedModelSummary> summaries = this.instance.getMultiBlockFormalBakeUpload().uploadedModelSummaries();
        this.safeSetModelCount = summaries.size();

        boolean formalIds = j2.usesFormalModelIds()
                && !j2.usesPlaceholderModelIds()
                && !j2.sampleSetModelIdsUsed()
                && !j2.sampleSetBridgeUsedAsFormalSource();
        boolean prerequisites = j2.formalShaderProgramValidationReady()
                && multi.multiBlockFormalUploadAuditReady()
                && formalResourcesReady(store)
                && j2.bindingLayoutCompatible()
                && j2.blockModelRecordLayoutCompatible()
                && formalIds
                && !summaries.isEmpty();
        if (!prerequisites) {
            this.fail(explainFailure(j2, multi, store, formalIds, summaries.isEmpty()));
            this.audit();
            this.instance.getFormalRendererManager().checkReadiness("j3-formal-textured-shader-preview-build-failed");
            return this.createStatusSnapshot();
        }

        try {
            this.closeOwnedResourcesOnRenderThread(false);
            this.previewProgramId = this.compilePreviewProgram();
            this.shaderCompileOk = true;
            this.programLinkOk = true;
            this.runOffscreenPreview(store, summaries);
            if (this.previewReadbackOk && this.previewPixelMismatches == 0) {
                this.stale = false;
                this.requiresRebuild = false;
                this.staleReason = "none";
            }
        } catch (RuntimeException e) {
            this.fail(e.getClass().getSimpleName() + ": " + e.getMessage());
        }

        this.prototypeReady = this.shaderCompileOk
                && this.programLinkOk
                && this.modelDataBindingOk
                && this.modelColourBindingOk
                && this.atlasTextureBindingOk
                && this.samplerBindingOk
                && this.previewFramebufferComplete
                && this.previewReadbackOk
                && this.previewPixelMismatches == 0
                && formalIds
                && !this.stale;
        this.previewReady = this.prototypeReady && this.previewModelCount > 0;
        if (this.prototypeReady) {
            this.lifecycleState = "BUILT";
            this.lastFailureReason = "none";
        } else if ("none".equals(this.lastFailureReason)) {
            this.fail("formal-textured-shader-preview-incomplete");
        }
        this.audit();
        this.instance.getFormalRendererManager().checkReadiness("j3-formal-textured-shader-preview-build");
        return this.createStatusSnapshot();
    }

    ForgeFormalTexturedShaderPreviewAuditResult audit() {
        this.auditRuns++;
        long start = System.nanoTime();
        try {
            this.lastAudit = this.auditInternal(start);
        } catch (RuntimeException e) {
            this.lastAudit = ForgeFormalTexturedShaderPreviewAuditResult.failure(e.getClass().getSimpleName() + ": " + e.getMessage(), elapsedMs(start));
        }
        if (!this.lastAudit.success()) {
            this.auditFailures++;
            this.lastFailureReason = this.lastAudit.error();
        }
        return this.lastAudit;
    }

    ForgeFormalTexturedShaderPreviewAuditResult createAuditStatusSnapshot() {
        return this.lastAudit;
    }

    ForgeFormalTexturedShaderPreviewStats createStatusSnapshot() {
        ForgeFormalShaderProgramStats j2 = this.instance.getFormalShaderProgramValidator().createStatusSnapshot();
        ForgeFormalModelStoreStats store = this.instance.getFormalModelStore().createStatusSnapshot();
        ForgeFormalModelBakeryLifecycleStats lifecycle = this.instance.getFormalModelBakeryLifecycle().createStatusSnapshot();
        ForgeMultiBlockFormalBakeUploadStats multi = this.instance.getMultiBlockFormalBakeUpload().createStatusSnapshot();
        boolean formalIds = j2.usesFormalModelIds()
                && !j2.usesPlaceholderModelIds()
                && !j2.sampleSetModelIdsUsed()
                && !j2.sampleSetBridgeUsedAsFormalSource();
        boolean statusPrototypeReady = this.prototypeReady
                && !this.stale
                && j2.formalShaderProgramValidationReady()
                && formalResourcesReady(store)
                && formalIds;
        boolean statusPreviewReady = statusPrototypeReady
                && this.previewReady
                && this.previewFramebufferComplete
                && this.previewReadbackOk
                && this.previewPixelMismatches == 0;
        return new ForgeFormalTexturedShaderPreviewStats(
                STAGE,
                this.buildRuns,
                this.auditRuns,
                this.clearRuns,
                this.auditFailures,
                statusPrototypeReady,
                statusPreviewReady,
                false,
                false,
                true,
                statusPreviewReady,
                false,
                this.visiblePreviewEnabled,
                false,
                false,
                false,
                this.shaderCompileOk,
                this.programLinkOk,
                this.previewProgramId,
                this.modelDataBindingOk,
                this.modelColourBindingOk,
                this.atlasTextureBindingOk,
                this.samplerBindingOk,
                j2.bindingLayoutCompatible(),
                store.formalModelStoreOwnerReady(),
                j2.formalShaderProgramValidatorReady(),
                j2.formalShaderProgramValidationReady(),
                lifecycle.reloadRebuildPrototypeReady(),
                multi.multiBlockFormalUploadAuditReady(),
                this.safeSetModelCount,
                this.previewModelCount,
                this.previewModelIds,
                formalIds,
                j2.usesPlaceholderModelIds(),
                j2.sampleSetModelIdsUsed(),
                j2.sampleSetBridgeUsedAsFormalSource(),
                this.previewFramebufferCreated,
                this.previewFramebufferComplete,
                this.previewFramebufferId,
                this.previewTextureId,
                PREVIEW_WIDTH,
                PREVIEW_HEIGHT,
                this.previewReadbackOk,
                this.previewPixelMismatches,
                this.previewChecksumCount,
                this.previewChecksums,
                this.faceDataUsed,
                this.modelColourUsed,
                this.atlasSampleUsed,
                false,
                false,
                false,
                false,
                false,
                false,
                this.stale,
                this.requiresRebuild,
                this.lifecycleState,
                this.lastLifecycleEvent,
                this.staleReason,
                this.lastGlError,
                this.lastFailureReason,
                this.lastAudit.success(),
                this.lastAudit.error(),
                this.lastAudit.durationMs()
        );
    }

    String dump() {
        ForgeFormalTexturedShaderPreviewStats status = this.createStatusSnapshot();
        return "J3 formal textured shader preview: "
                + "stage=" + status.stage()
                + " previewProgramId=" + status.previewProgramId()
                + " framebuffer=" + status.previewFramebufferId()
                + " texture=" + status.previewTextureId()
                + " previewSize=" + status.previewWidth() + "x" + status.previewHeight()
                + " previewModelIds=" + status.previewModelIds()
                + " previewChecksums=" + status.previewChecksums()
                + " previewDrawOnly=true terrainDrawStarted=false formalRendererDrawStarted=false actualRendererDrawEnabled=false"
                + " formalTexturedShaderReady=false formalRendererReady=false"
                + " lastFailureReason=" + status.lastFailureReason();
    }

    void clear() {
        this.clearRuns++;
        this.prototypeReady = false;
        this.previewReady = false;
        this.stale = false;
        this.requiresRebuild = false;
        this.visiblePreviewEnabled = false;
        this.lifecycleState = "CLEARED";
        this.lastLifecycleEvent = "clear";
        this.staleReason = "none";
        this.lastFailureReason = "none";
        this.resetPreviewFlags();
        this.lastAudit = ForgeFormalTexturedShaderPreviewAuditResult.failure("none", 0.0D);
        this.scheduleCleanup("clear");
    }

    ForgeFormalTexturedShaderPreviewStats enableVisiblePreview() {
        this.visiblePreviewEnabled = false;
        this.lastFailureReason = "visible-preview-not-implemented";
        this.lastLifecycleEvent = "visible-preview-enable-rejected";
        return this.createStatusSnapshot();
    }

    ForgeFormalTexturedShaderPreviewStats disableVisiblePreview() {
        this.visiblePreviewEnabled = false;
        this.lastLifecycleEvent = "visible-preview-disable";
        return this.createStatusSnapshot();
    }

    void markResourceReload() {
        this.markStale("resource-reload");
    }

    void markWorldUnload() {
        this.markStale("world-unload");
    }

    void markDimensionSwitch() {
        this.markStale("dimension-switch");
    }

    void markDebugPipelineClear() {
        this.markStale("debug-pipeline-clear");
    }

    void markPresetOff() {
        this.markStale("preset-off");
    }

    void markPresetClear() {
        this.markStale("preset-clear");
    }

    void markStale(String reason) {
        this.prototypeReady = false;
        this.previewReady = false;
        this.visiblePreviewEnabled = false;
        this.stale = true;
        this.requiresRebuild = true;
        this.lifecycleState = "STALE";
        this.lastLifecycleEvent = safeReason(reason);
        this.staleReason = this.lastLifecycleEvent;
        this.lastFailureReason = this.staleReason;
        this.scheduleCleanup(this.lastLifecycleEvent);
    }

    private ForgeFormalTexturedShaderPreviewAuditResult auditInternal(long startNanos) {
        ForgeFormalShaderProgramStats j2 = this.instance.getFormalShaderProgramValidator().createStatusSnapshot();
        ForgeFormalModelBakeryLifecycleStats lifecycle = this.instance.getFormalModelBakeryLifecycle().createStatusSnapshot();
        boolean formalIds = j2.usesFormalModelIds()
                && !j2.usesPlaceholderModelIds()
                && !j2.sampleSetModelIdsUsed()
                && !j2.sampleSetBridgeUsedAsFormalSource();
        boolean success = this.previewReady
                && !this.stale
                && lifecycle.reloadRebuildPrototypeReady()
                && j2.formalShaderProgramValidationReady()
                && this.shaderCompileOk
                && this.programLinkOk
                && this.modelDataBindingOk
                && this.modelColourBindingOk
                && this.atlasTextureBindingOk
                && this.samplerBindingOk
                && j2.bindingLayoutCompatible()
                && j2.blockModelRecordLayoutCompatible()
                && formalIds
                && this.previewFramebufferComplete
                && this.previewReadbackOk
                && this.previewPixelMismatches == 0
                && this.previewChecksumCount > 0
                && !this.visiblePreviewEnabled;
        return new ForgeFormalTexturedShaderPreviewAuditResult(
                success,
                success ? "none" : "formal-textured-shader-preview-audit-failed",
                elapsedMs(startNanos),
                lifecycle.reloadRebuildPrototypeReady(),
                j2.formalShaderProgramValidationReady(),
                this.shaderCompileOk,
                this.programLinkOk,
                this.modelDataBindingOk,
                this.modelColourBindingOk,
                this.atlasTextureBindingOk,
                this.samplerBindingOk,
                j2.bindingLayoutCompatible(),
                j2.blockModelRecordLayoutCompatible(),
                formalIds,
                j2.usesPlaceholderModelIds(),
                j2.sampleSetModelIdsUsed(),
                j2.sampleSetBridgeUsedAsFormalSource(),
                this.previewReady,
                this.previewFramebufferComplete,
                this.previewReadbackOk,
                this.previewPixelMismatches,
                this.previewChecksumCount,
                true,
                this.visiblePreviewEnabled,
                false,
                false,
                false,
                false,
                false
        );
    }

    private int compilePreviewProgram() {
        int vertex = 0;
        int fragment = 0;
        int program = 0;
        try {
            vertex = compileShader(GL20C.GL_VERTEX_SHADER, VERTEX_SOURCE, "vertex");
            fragment = compileShader(GL20C.GL_FRAGMENT_SHADER, FRAGMENT_SOURCE, "fragment");
            program = GL20C.glCreateProgram();
            GL20C.glAttachShader(program, vertex);
            GL20C.glAttachShader(program, fragment);
            GL20C.glLinkProgram(program);
            if (GL20C.glGetProgrami(program, GL20C.GL_LINK_STATUS) == GL11C.GL_FALSE) {
                throw new IllegalStateException("preview-link-failed:" + sanitize(GL20C.glGetProgramInfoLog(program)));
            }
            int samplerLocation = GL20C.glGetUniformLocation(program, "blockModelAtlas");
            if (samplerLocation >= 0) {
                int oldProgram = GL11C.glGetInteger(GL20C.GL_CURRENT_PROGRAM);
                GL20C.glUseProgram(program);
                GL20C.glUniform1i(samplerLocation, ForgeFormalShaderInputBindingLayout.BLOCK_MODEL_ATLAS_TEXTURE_UNIT);
                GL20C.glUseProgram(oldProgram);
            }
            return program;
        } finally {
            if (program != 0 && vertex != 0) {
                GL20C.glDetachShader(program, vertex);
            }
            if (program != 0 && fragment != 0) {
                GL20C.glDetachShader(program, fragment);
            }
            if (vertex != 0) {
                GL20C.glDeleteShader(vertex);
            }
            if (fragment != 0) {
                GL20C.glDeleteShader(fragment);
            }
        }
    }

    private static int compileShader(int type, String source, String name) {
        int shader = GL20C.glCreateShader(type);
        GL20C.glShaderSource(shader, source);
        GL20C.glCompileShader(shader);
        if (GL20C.glGetShaderi(shader, GL20C.GL_COMPILE_STATUS) == GL11C.GL_FALSE) {
            String log = sanitize(GL20C.glGetShaderInfoLog(shader));
            GL20C.glDeleteShader(shader);
            throw new IllegalStateException("preview-" + name + "-compile-failed:" + log);
        }
        return shader;
    }

    private void runOffscreenPreview(ForgeFormalModelStoreStats store, List<ForgeFormalUploadedModelSummary> summaries) {
        int count = Math.min(MAX_PREVIEW_MODELS, summaries.size());
        if (count <= 0) {
            this.fail("safe-set-empty");
            return;
        }
        int[] modelIds = new int[count];
        for (int i = 0; i < count; i++) {
            modelIds[i] = summaries.get(i).formalModelId();
        }
        this.previewModelCount = count;
        this.previewModelIds = Arrays.stream(modelIds).mapToObj(Integer::toString).collect(Collectors.joining(","));

        this.previewModelIdBufferId = GL45C.glCreateBuffers();
        long idsPtr = MemoryUtil.nmemAlloc((long) count * Integer.BYTES);
        try {
            for (int i = 0; i < count; i++) {
                MemoryUtil.memPutInt(idsPtr + ((long) i * Integer.BYTES), modelIds[i]);
            }
            GL45C.nglNamedBufferData(this.previewModelIdBufferId, (long) count * Integer.BYTES, idsPtr, GL15C.GL_STATIC_DRAW);
        } finally {
            MemoryUtil.nmemFree(idsPtr);
        }
        this.createFramebuffer();
        this.drawPreview(store, count);
        this.readbackPreview(modelIds);
    }

    private void createFramebuffer() {
        int oldTexture = GL11C.glGetInteger(GL11C.GL_TEXTURE_BINDING_2D);
        int oldDrawFramebuffer = GL11C.glGetInteger(GL30C.GL_DRAW_FRAMEBUFFER_BINDING);
        try {
            this.previewTextureId = GL11C.glGenTextures();
            GL11C.glBindTexture(GL11C.GL_TEXTURE_2D, this.previewTextureId);
            GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_MIN_FILTER, GL11C.GL_NEAREST);
            GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_MAG_FILTER, GL11C.GL_NEAREST);
            GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_WRAP_S, GL12C.GL_CLAMP_TO_EDGE);
            GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_WRAP_T, GL12C.GL_CLAMP_TO_EDGE);
            GL11C.glTexImage2D(GL11C.GL_TEXTURE_2D, 0, GL30C.GL_RGBA8, PREVIEW_WIDTH, PREVIEW_HEIGHT, 0, GL11C.GL_RGBA, GL11C.GL_UNSIGNED_BYTE, (ByteBuffer) null);
            this.previewFramebufferId = GL30C.glGenFramebuffers();
            GL30C.glBindFramebuffer(GL30C.GL_FRAMEBUFFER, this.previewFramebufferId);
            GL30C.glFramebufferTexture2D(GL30C.GL_FRAMEBUFFER, GL30C.GL_COLOR_ATTACHMENT0, GL11C.GL_TEXTURE_2D, this.previewTextureId, 0);
            GL20C.glDrawBuffers(GL30C.GL_COLOR_ATTACHMENT0);
            this.previewFramebufferCreated = this.previewFramebufferId != 0 && this.previewTextureId != 0;
            this.previewFramebufferComplete = GL30C.glCheckFramebufferStatus(GL30C.GL_FRAMEBUFFER) == GL30C.GL_FRAMEBUFFER_COMPLETE;
            if (!this.previewFramebufferComplete) {
                throw new IllegalStateException("preview-framebuffer-incomplete");
            }
        } finally {
            GL30C.glBindFramebuffer(GL30C.GL_DRAW_FRAMEBUFFER, oldDrawFramebuffer);
            GL11C.glBindTexture(GL11C.GL_TEXTURE_2D, oldTexture);
        }
    }

    private void drawPreview(ForgeFormalModelStoreStats store, int count) {
        int oldProgram = GL11C.glGetInteger(GL20C.GL_CURRENT_PROGRAM);
        int oldVertexArray = GL11C.glGetInteger(GL30C.GL_VERTEX_ARRAY_BINDING);
        int oldModelBuffer = GL30C.glGetIntegeri(GL43C.GL_SHADER_STORAGE_BUFFER_BINDING, ForgeFormalShaderInputBindingLayout.MODEL_DATA_BINDING_INDEX);
        int oldColourBuffer = GL30C.glGetIntegeri(GL43C.GL_SHADER_STORAGE_BUFFER_BINDING, ForgeFormalShaderInputBindingLayout.MODEL_COLOUR_BINDING_INDEX);
        int oldPreviewIdBuffer = GL30C.glGetIntegeri(GL43C.GL_SHADER_STORAGE_BUFFER_BINDING, PREVIEW_MODEL_ID_BINDING_INDEX);
        int oldActiveTexture = GL11C.glGetInteger(GL13C.GL_ACTIVE_TEXTURE);
        GL13C.glActiveTexture(GL13C.GL_TEXTURE0 + ForgeFormalShaderInputBindingLayout.BLOCK_MODEL_ATLAS_TEXTURE_UNIT);
        int oldTexture = GL11C.glGetInteger(GL11C.GL_TEXTURE_BINDING_2D);
        int oldSampler = GL30C.glGetIntegeri(GL33C.GL_SAMPLER_BINDING, ForgeFormalShaderInputBindingLayout.BLOCK_MODEL_ATLAS_TEXTURE_UNIT);
        int oldDrawFramebuffer = GL11C.glGetInteger(GL30C.GL_DRAW_FRAMEBUFFER_BINDING);
        int oldReadFramebuffer = GL11C.glGetInteger(GL30C.GL_READ_FRAMEBUFFER_BINDING);
        int oldDrawBuffer = GL11C.glGetInteger(GL11C.GL_DRAW_BUFFER);
        int oldReadBuffer = GL11C.glGetInteger(GL11C.GL_READ_BUFFER);
        int[] oldViewport = new int[4];
        float[] oldClearColor = new float[4];
        GL11C.glGetIntegerv(GL11C.GL_VIEWPORT, oldViewport);
        GL11C.glGetFloatv(GL11C.GL_COLOR_CLEAR_VALUE, oldClearColor);
        boolean depthEnabled = GL11C.glIsEnabled(GL11C.GL_DEPTH_TEST);
        boolean blendEnabled = GL11C.glIsEnabled(GL11C.GL_BLEND);
        boolean cullEnabled = GL11C.glIsEnabled(GL11C.GL_CULL_FACE);

        try {
            clearGlErrors();
            GL30C.glBindFramebuffer(GL30C.GL_FRAMEBUFFER, this.previewFramebufferId);
            GL20C.glDrawBuffers(GL30C.GL_COLOR_ATTACHMENT0);
            GL11C.glReadBuffer(GL30C.GL_COLOR_ATTACHMENT0);
            GL11C.glViewport(0, 0, PREVIEW_WIDTH, PREVIEW_HEIGHT);
            GL11C.glDisable(GL11C.GL_DEPTH_TEST);
            GL11C.glDisable(GL11C.GL_BLEND);
            GL11C.glDisable(GL11C.GL_CULL_FACE);
            GL11C.glClearColor(0.0F, 0.0F, 0.0F, 0.0F);
            GL11C.glClear(GL11C.GL_COLOR_BUFFER_BIT);

            this.previewVertexArrayId = GL30C.glGenVertexArrays();
            GL30C.glBindVertexArray(this.previewVertexArrayId);
            GL20C.glUseProgram(this.previewProgramId);
            int countLocation = GL20C.glGetUniformLocation(this.previewProgramId, "previewModelCount");
            if (countLocation >= 0) {
                GL30C.glUniform1ui(countLocation, count);
            }
            GL30C.glBindBufferBase(GL43C.GL_SHADER_STORAGE_BUFFER, ForgeFormalShaderInputBindingLayout.MODEL_DATA_BINDING_INDEX, store.modelDataBufferId());
            GL30C.glBindBufferBase(GL43C.GL_SHADER_STORAGE_BUFFER, ForgeFormalShaderInputBindingLayout.MODEL_COLOUR_BINDING_INDEX, store.modelColourBufferId());
            GL30C.glBindBufferBase(GL43C.GL_SHADER_STORAGE_BUFFER, PREVIEW_MODEL_ID_BINDING_INDEX, this.previewModelIdBufferId);
            GL11C.glBindTexture(GL11C.GL_TEXTURE_2D, store.atlasTextureId());
            GL33C.glBindSampler(ForgeFormalShaderInputBindingLayout.BLOCK_MODEL_ATLAS_TEXTURE_UNIT, store.samplerId());

            this.modelDataBindingOk = GL30C.glGetIntegeri(GL43C.GL_SHADER_STORAGE_BUFFER_BINDING, ForgeFormalShaderInputBindingLayout.MODEL_DATA_BINDING_INDEX) == store.modelDataBufferId();
            this.modelColourBindingOk = GL30C.glGetIntegeri(GL43C.GL_SHADER_STORAGE_BUFFER_BINDING, ForgeFormalShaderInputBindingLayout.MODEL_COLOUR_BINDING_INDEX) == store.modelColourBufferId();
            this.atlasTextureBindingOk = GL11C.glGetInteger(GL11C.GL_TEXTURE_BINDING_2D) == store.atlasTextureId();
            this.samplerBindingOk = GL30C.glGetIntegeri(GL33C.GL_SAMPLER_BINDING, ForgeFormalShaderInputBindingLayout.BLOCK_MODEL_ATLAS_TEXTURE_UNIT) == store.samplerId();

            GL11C.glDrawArrays(GL11C.GL_TRIANGLES, 0, count * 6);
            GL11C.glFinish();
            int error = GL11C.glGetError();
            this.lastGlError = error == GL11C.GL_NO_ERROR ? "none" : glErrorName(error);
            if (error != GL11C.GL_NO_ERROR) {
                throw new IllegalStateException("preview-draw-gl-error-" + this.lastGlError);
            }
            this.faceDataUsed = true;
            this.modelColourUsed = true;
            this.atlasSampleUsed = true;
        } finally {
            GL20C.glUseProgram(oldProgram);
            GL30C.glBindVertexArray(oldVertexArray);
            GL30C.glBindBufferBase(GL43C.GL_SHADER_STORAGE_BUFFER, ForgeFormalShaderInputBindingLayout.MODEL_DATA_BINDING_INDEX, oldModelBuffer);
            GL30C.glBindBufferBase(GL43C.GL_SHADER_STORAGE_BUFFER, ForgeFormalShaderInputBindingLayout.MODEL_COLOUR_BINDING_INDEX, oldColourBuffer);
            GL30C.glBindBufferBase(GL43C.GL_SHADER_STORAGE_BUFFER, PREVIEW_MODEL_ID_BINDING_INDEX, oldPreviewIdBuffer);
            GL11C.glBindTexture(GL11C.GL_TEXTURE_2D, oldTexture);
            GL33C.glBindSampler(ForgeFormalShaderInputBindingLayout.BLOCK_MODEL_ATLAS_TEXTURE_UNIT, oldSampler);
            GL13C.glActiveTexture(oldActiveTexture);
            GL30C.glBindFramebuffer(GL30C.GL_DRAW_FRAMEBUFFER, oldDrawFramebuffer);
            GL30C.glBindFramebuffer(GL30C.GL_READ_FRAMEBUFFER, oldReadFramebuffer);
            GL20C.glDrawBuffers(oldDrawBuffer);
            GL11C.glReadBuffer(oldReadBuffer);
            GL11C.glViewport(oldViewport[0], oldViewport[1], oldViewport[2], oldViewport[3]);
            GL11C.glClearColor(oldClearColor[0], oldClearColor[1], oldClearColor[2], oldClearColor[3]);
            setEnabled(GL11C.GL_DEPTH_TEST, depthEnabled);
            setEnabled(GL11C.GL_BLEND, blendEnabled);
            setEnabled(GL11C.GL_CULL_FACE, cullEnabled);
        }
    }

    private void readbackPreview(int[] modelIds) {
        ByteBuffer buffer = MemoryUtil.memAlloc(PREVIEW_WIDTH * PREVIEW_HEIGHT * BYTES_PER_PIXEL);
        PixelStoreState pixelStore = PixelStoreState.capturePack();
        int packBufferBinding = GL11C.glGetInteger(GL21C.GL_PIXEL_PACK_BUFFER_BINDING);
        int oldReadFramebuffer = GL11C.glGetInteger(GL30C.GL_READ_FRAMEBUFFER_BINDING);
        int oldReadBuffer = GL11C.glGetInteger(GL11C.GL_READ_BUFFER);
        try {
            GL15C.glBindBuffer(GL21C.GL_PIXEL_PACK_BUFFER, 0);
            GL30C.glBindFramebuffer(GL30C.GL_READ_FRAMEBUFFER, this.previewFramebufferId);
            GL11C.glReadBuffer(GL30C.GL_COLOR_ATTACHMENT0);
            PixelStoreState.applyTightPack();
            clearGlErrors();
            GL11C.glReadPixels(0, 0, PREVIEW_WIDTH, PREVIEW_HEIGHT, GL11C.GL_RGBA, GL11C.GL_UNSIGNED_BYTE, buffer);
            int error = GL11C.glGetError();
            this.lastGlError = error == GL11C.GL_NO_ERROR ? "none" : glErrorName(error);
            if (error != GL11C.GL_NO_ERROR) {
                this.previewReadbackOk = false;
                this.previewPixelMismatches++;
                return;
            }
            byte[] pixels = new byte[PREVIEW_WIDTH * PREVIEW_HEIGHT * BYTES_PER_PIXEL];
            buffer.position(0);
            buffer.get(pixels);
            this.previewChecksums = computeRegionChecksums(modelIds, pixels);
            this.previewChecksumCount = modelIds.length;
            this.previewPixelMismatches = countTransparentRegions(modelIds.length, pixels);
            this.previewReadbackOk = this.previewPixelMismatches == 0;
        } finally {
            GL30C.glBindFramebuffer(GL30C.GL_READ_FRAMEBUFFER, oldReadFramebuffer);
            GL11C.glReadBuffer(oldReadBuffer);
            GL15C.glBindBuffer(GL21C.GL_PIXEL_PACK_BUFFER, packBufferBinding);
            pixelStore.restorePack();
            MemoryUtil.memFree(buffer);
        }
    }

    private static String computeRegionChecksums(int[] modelIds, byte[] pixels) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < modelIds.length; i++) {
            if (i > 0) {
                builder.append(',');
            }
            CRC32 crc = new CRC32();
            int x0 = (PREVIEW_WIDTH * i) / modelIds.length;
            int x1 = (PREVIEW_WIDTH * (i + 1)) / modelIds.length;
            for (int y = 0; y < PREVIEW_HEIGHT; y++) {
                int offset = ((y * PREVIEW_WIDTH) + x0) * BYTES_PER_PIXEL;
                int length = Math.max(0, (x1 - x0) * BYTES_PER_PIXEL);
                crc.update(pixels, offset, length);
            }
            builder.append("model").append(modelIds[i]).append("=0x").append(Long.toHexString(crc.getValue()).toUpperCase());
        }
        return builder.toString();
    }

    private static int countTransparentRegions(int regionCount, byte[] pixels) {
        int failures = 0;
        for (int i = 0; i < regionCount; i++) {
            int x0 = (PREVIEW_WIDTH * i) / regionCount;
            int x1 = (PREVIEW_WIDTH * (i + 1)) / regionCount;
            long alphaSum = 0L;
            long rgbSum = 0L;
            for (int y = 0; y < PREVIEW_HEIGHT; y++) {
                for (int x = x0; x < x1; x++) {
                    int offset = ((y * PREVIEW_WIDTH) + x) * BYTES_PER_PIXEL;
                    rgbSum += pixels[offset] & 0xFF;
                    rgbSum += pixels[offset + 1] & 0xFF;
                    rgbSum += pixels[offset + 2] & 0xFF;
                    alphaSum += pixels[offset + 3] & 0xFF;
                }
            }
            if (alphaSum == 0L || rgbSum == 0L) {
                failures++;
            }
        }
        return failures;
    }

    private void resetPreviewFlags() {
        this.prototypeReady = false;
        this.previewReady = false;
        this.shaderCompileOk = false;
        this.programLinkOk = false;
        this.modelDataBindingOk = false;
        this.modelColourBindingOk = false;
        this.atlasTextureBindingOk = false;
        this.samplerBindingOk = false;
        this.previewFramebufferCreated = false;
        this.previewFramebufferComplete = false;
        this.previewReadbackOk = false;
        this.previewPixelMismatches = 0;
        this.previewChecksumCount = 0;
        this.previewChecksums = "";
        this.safeSetModelCount = 0;
        this.previewModelCount = 0;
        this.previewModelIds = "";
        this.faceDataUsed = false;
        this.modelColourUsed = false;
        this.atlasSampleUsed = false;
        this.lastGlError = "none";
    }

    private void fail(String reason) {
        this.prototypeReady = false;
        this.previewReady = false;
        this.lifecycleState = "FAILED";
        this.lastFailureReason = safeReason(reason);
    }

    private void scheduleCleanup(String reason) {
        if (RenderSystem.isOnRenderThread()) {
            this.closeOwnedResourcesOnRenderThread(true);
        } else {
            RenderSystem.recordRenderCall(() -> this.closeOwnedResourcesOnRenderThread(true));
        }
        this.lastLifecycleEvent = safeReason(reason);
    }

    private void closeOwnedResourcesOnRenderThread(boolean resetProgramState) {
        if (this.previewProgramId != 0) {
            GL20C.glDeleteProgram(this.previewProgramId);
        }
        if (this.previewFramebufferId != 0) {
            GL30C.glDeleteFramebuffers(this.previewFramebufferId);
        }
        if (this.previewTextureId != 0) {
            GL11C.glDeleteTextures(this.previewTextureId);
        }
        if (this.previewVertexArrayId != 0) {
            GL30C.glDeleteVertexArrays(this.previewVertexArrayId);
        }
        if (this.previewModelIdBufferId != 0) {
            GL15C.glDeleteBuffers(this.previewModelIdBufferId);
        }
        this.previewProgramId = 0;
        this.previewFramebufferId = 0;
        this.previewTextureId = 0;
        this.previewVertexArrayId = 0;
        this.previewModelIdBufferId = 0;
        if (resetProgramState) {
            this.shaderCompileOk = false;
            this.programLinkOk = false;
        }
    }

    private static boolean formalResourcesReady(ForgeFormalModelStoreStats store) {
        return store.formalModelStoreOwnerReady()
                && !store.stale()
                && !store.requiresRebuild()
                && store.modelDataBufferCreated()
                && store.modelDataBufferBytes() >= ForgeFormalModelStore.MODEL_DATA_BYTES
                && store.modelColourBufferCreated()
                && store.modelColourBufferBytes() >= ForgeFormalModelStore.MODEL_COLOUR_BYTES
                && store.atlasTextureCreated()
                && store.fullAtlasTextureCreated()
                && store.actualAtlasWidth() == ForgeModelAtlasLayout.ATLAS_WIDTH
                && store.actualAtlasHeight() == ForgeModelAtlasLayout.ATLAS_HEIGHT
                && store.samplerCreated()
                && store.samplerConfigured();
    }

    private static String explainFailure(
            ForgeFormalShaderProgramStats j2,
            ForgeMultiBlockFormalBakeUploadStats multi,
            ForgeFormalModelStoreStats store,
            boolean formalIds,
            boolean noModels
    ) {
        if (!j2.formalShaderProgramValidationReady()) {
            return "j2-formal-shader-program-validation-not-ready:" + j2.lastFailureReason();
        }
        if (!multi.multiBlockFormalUploadAuditReady()) {
            return "i6-multi-block-formal-upload-audit-not-ready";
        }
        if (!formalResourcesReady(store)) {
            return "formal-model-store-resources-not-ready:" + store.lastBuildError() + ":" + store.lastAllocationError();
        }
        if (!j2.bindingLayoutCompatible() || !j2.blockModelRecordLayoutCompatible()) {
            return "formal-binding-or-record-layout-not-compatible";
        }
        if (!formalIds) {
            return "formal-model-ids-not-clean";
        }
        if (noModels) {
            return "safe-set-empty";
        }
        return "unknown";
    }

    private static void setEnabled(int flag, boolean enabled) {
        if (enabled) {
            GL11C.glEnable(flag);
        } else {
            GL11C.glDisable(flag);
        }
    }

    private static void clearGlErrors() {
        while (GL11C.glGetError() != GL11C.GL_NO_ERROR) {
            // Drain stale GL errors before the isolated preview draw.
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

    private static String sanitize(String log) {
        if (log == null || log.isBlank()) {
            return "none";
        }
        return log.replace('\n', ' ').replace('\r', ' ').replace(' ', '_');
    }

    private static String safeReason(String reason) {
        return reason == null || reason.isBlank() ? "unspecified" : reason.replace(' ', '-');
    }

    private static double elapsedMs(long startNanos) {
        return startNanos == 0L ? 0.0D : (System.nanoTime() - startNanos) / 1_000_000.0D;
    }

    private record PixelStoreState(int alignment, int rowLength, int imageHeight, int skipRows, int skipPixels, int skipImages) {
        static PixelStoreState capturePack() {
            return new PixelStoreState(
                    GL11C.glGetInteger(GL11C.GL_PACK_ALIGNMENT),
                    GL11C.glGetInteger(GL11C.GL_PACK_ROW_LENGTH),
                    GL11C.glGetInteger(GL12C.GL_PACK_IMAGE_HEIGHT),
                    GL11C.glGetInteger(GL11C.GL_PACK_SKIP_ROWS),
                    GL11C.glGetInteger(GL11C.GL_PACK_SKIP_PIXELS),
                    GL11C.glGetInteger(GL12C.GL_PACK_SKIP_IMAGES)
            );
        }

        static void applyTightPack() {
            GL11C.glPixelStorei(GL11C.GL_PACK_ALIGNMENT, 1);
            GL11C.glPixelStorei(GL11C.GL_PACK_ROW_LENGTH, 0);
            GL11C.glPixelStorei(GL12C.GL_PACK_IMAGE_HEIGHT, 0);
            GL11C.glPixelStorei(GL11C.GL_PACK_SKIP_ROWS, 0);
            GL11C.glPixelStorei(GL11C.GL_PACK_SKIP_PIXELS, 0);
            GL11C.glPixelStorei(GL12C.GL_PACK_SKIP_IMAGES, 0);
        }

        void restorePack() {
            GL11C.glPixelStorei(GL11C.GL_PACK_ALIGNMENT, this.alignment);
            GL11C.glPixelStorei(GL11C.GL_PACK_ROW_LENGTH, this.rowLength);
            GL11C.glPixelStorei(GL12C.GL_PACK_IMAGE_HEIGHT, this.imageHeight);
            GL11C.glPixelStorei(GL11C.GL_PACK_SKIP_ROWS, this.skipRows);
            GL11C.glPixelStorei(GL11C.GL_PACK_SKIP_PIXELS, this.skipPixels);
            GL11C.glPixelStorei(GL12C.GL_PACK_SKIP_IMAGES, this.skipImages);
        }
    }
}
