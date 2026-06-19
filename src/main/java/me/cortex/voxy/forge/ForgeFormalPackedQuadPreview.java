package me.cortex.voxy.forge;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.zip.CRC32;

/**
 * Deprecated prototype route: kept only as historical packed-quad preview
 * evidence. New formal geometry work must follow original Voxy
 * RenderDataFactory and geometry-manager ownership instead.
 */
@Deprecated(forRemoval = false)
final class ForgeFormalPackedQuadPreview {
    static final String STAGE = "J4_FORMAL_PACKED_QUAD_SHADER_GEOMETRY_PREVIEW";
    private static final int PACKED_QUAD_BINDING_INDEX = 7;
    private static final int MAX_PREVIEW_QUADS = 8;
    private static final int PREVIEW_WIDTH = 128;
    private static final int PREVIEW_HEIGHT = 128;
    private static final int BYTES_PER_PIXEL = 4;

    private static final String VERTEX_SOURCE = """
            #version 430 core

            layout(std430, binding = 3) readonly buffer ModelData {
                uint modelData[];
            };

            layout(std430, binding = 4) readonly buffer ModelColour {
                uint modelColour[];
            };

            layout(std430, binding = 7) readonly buffer PackedQuads {
                uvec2 packedRecords[];
            };

            uniform uint previewQuadCount;

            out vec2 vAtlasUv;
            out vec4 vTint;
            flat out uint vModelId;
            flat out uint vFace;
            flat out uint vFaceData;

            const uint WORDS_PER_MODEL = 16u;
            const uint MODEL_TEXTURE_SIZE = 16u;
            const uint FACES_PER_MODEL_X = 3u;
            const float ATLAS_WIDTH = 12288.0;
            const float ATLAS_HEIGHT = 8192.0;
            const uint COLUMNS = 4u;

            const vec2 CORNERS[6] = vec2[](
                vec2(0.0, 0.0),
                vec2(1.0, 0.0),
                vec2(1.0, 1.0),
                vec2(0.0, 0.0),
                vec2(1.0, 1.0),
                vec2(0.0, 1.0)
            );

            uint extractBits(uvec2 record, uint shift, uint bits) {
                if (shift < 32u) {
                    uint available = 32u - shift;
                    uint value = record.x >> shift;
                    if (available < bits) {
                        value |= record.y << available;
                    }
                    return value & ((1u << bits) - 1u);
                }
                return (record.y >> (shift - 32u)) & ((1u << bits) - 1u);
            }

            void main() {
                uint vertex = uint(gl_VertexID);
                uint quadIndex = vertex / 6u;
                uint cornerIndex = vertex - quadIndex * 6u;
                uvec2 record = packedRecords[quadIndex];

                uint face = min(extractBits(record, 0u, 3u), 5u);
                uint length = extractBits(record, 3u, 4u) + 1u;
                uint width = extractBits(record, 7u, 4u) + 1u;
                uint localZ = extractBits(record, 11u, 5u);
                uint localY = extractBits(record, 16u, 5u);
                uint localX = extractBits(record, 21u, 5u);
                uint modelId = extractBits(record, 26u, 16u);

                uint wordBase = modelId * WORDS_PER_MODEL;
                uint faceData = modelData[wordBase + face];
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
                uint baseX = (modelId & 255u) * MODEL_TEXTURE_SIZE * FACES_PER_MODEL_X + (face % 3u) * MODEL_TEXTURE_SIZE;
                uint baseY = ((modelId >> 8u) & 255u) * MODEL_TEXTURE_SIZE * 2u + (face / 3u) * MODEL_TEXTURE_SIZE;
                vec2 atlasPixel = vec2(float(baseX), float(baseY)) + vec2(localU, localV) * 16.0 + vec2(0.5);
                vAtlasUv = atlasPixel / vec2(ATLAS_WIDTH, ATLAS_HEIGHT);

                float r = float(colour & 255u) / 255.0;
                float g = float((colour >> 8u) & 255u) / 255.0;
                float b = float((colour >> 16u) & 255u) / 255.0;
                float a = float((colour >> 24u) & 255u) / 255.0;
                vTint = vec4(r, g, b, max(a, 1.0));

                uint column = quadIndex % COLUMNS;
                uint row = quadIndex / COLUMNS;
                float cellW = 1.84 / float(COLUMNS);
                float cellH = 1.62 / 2.0;
                float left = -0.92 + float(column) * cellW + 0.05;
                float right = left + cellW - 0.10;
                float top = 0.82 - float(row) * cellH - 0.06;
                float bottom = top - cellH + 0.12;
                float nudgeX = (float(localX & 3u) - 1.5) * 0.01;
                float nudgeY = (float((localY + localZ) & 3u) - 1.5) * 0.01;
                float x = mix(left, right, corner.x) + nudgeX;
                float y = mix(bottom, top, corner.y) + nudgeY;
                gl_Position = vec4(x, y, 0.0, 1.0);

                vModelId = modelId;
                vFace = face;
                vFaceData = faceData ^ length ^ width;
            }
            """;

    private static final String FRAGMENT_SOURCE = """
            #version 430 core

            layout(binding = 0) uniform sampler2D blockModelAtlas;

            in vec2 vAtlasUv;
            in vec4 vTint;
            flat in uint vModelId;
            flat in uint vFace;
            flat in uint vFaceData;

            out vec4 fragColor;

            void main() {
                vec4 texel = texture(blockModelAtlas, vAtlasUv);
                float marker = float((vFaceData ^ vModelId ^ vFace) & 1u) / 255.0;
                fragColor = vec4(min(vec3(1.0), texel.rgb * vTint.rgb + vec3(marker)), texel.a);
            }
            """;

    private final ForgeVoxyInstance instance;
    private long buildRuns;
    private long auditRuns;
    private long clearRuns;
    private long auditFailures;
    private boolean previewReady;
    private boolean shaderCompileOk;
    private boolean programLinkOk;
    private int previewProgramId;
    private int previewFramebufferId;
    private int previewTextureId;
    private int previewVertexArrayId;
    private int temporaryFormalQuadBufferId;
    private boolean temporaryFormalQuadBufferCreated;
    private int temporaryFormalQuadCount;
    private int temporaryFormalVertexCount;
    private String temporaryFormalModelIds = "";
    private int sourceRecordsScanned;
    private int sourceRecordsAccepted;
    private int sourceRecordsRejected;
    private int recordsRejectedNoBlockState;
    private int recordsRejectedNoFormalModelId;
    private int recordsRejectedUnsafeModelId;
    private boolean realTerrainRecordsUsed;
    private boolean syntheticFallbackUsed;
    private String syntheticFallbackReason = "none";
    private boolean modelDataBindingOk;
    private boolean modelColourBindingOk;
    private boolean atlasTextureBindingOk;
    private boolean samplerBindingOk;
    private boolean quadRecordDecodeOk;
    private boolean modelIdDecodeOk;
    private boolean faceDecodeOk;
    private boolean faceDataUsed;
    private boolean atlasSampleUsed;
    private boolean modelColourUsed;
    private boolean previewFramebufferCreated;
    private boolean previewFramebufferComplete;
    private boolean previewReadbackOk;
    private int previewPixelMismatches;
    private int previewChecksumCount;
    private String previewChecksums = "";
    private boolean stale;
    private boolean requiresRebuild;
    private String lifecycleState = "UNINITIALIZED";
    private String lastLifecycleEvent = "initialized";
    private String staleReason = "none";
    private String lastGlError = "none";
    private String lastFailureReason = "none";
    private ForgeFormalPackedQuadPreviewAuditResult lastAudit =
            ForgeFormalPackedQuadPreviewAuditResult.failure("none", 0.0D);

    ForgeFormalPackedQuadPreview(ForgeVoxyInstance instance) {
        this.instance = instance;
    }

    ForgeFormalPackedQuadPreviewStats build() {
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

        ForgeFormalTexturedShaderPreviewStats j3 = this.instance.getFormalTexturedShaderPreview().build();
        ForgeFormalModelStoreStats store = this.instance.getFormalModelStore().createStatusSnapshot();
        List<ForgeFormalUploadedModelSummary> summaries = this.instance.getMultiBlockFormalBakeUpload().uploadedModelSummaries();
        boolean prerequisites = j3.formalTexturedShaderPrototypeReady()
                && j3.previewReadbackOk()
                && j3.previewPixelMismatches() == 0
                && this.formalResourcesReady(store)
                && !summaries.isEmpty()
                && j3.usesFormalModelIds()
                && !j3.usesPlaceholderModelIds()
                && !j3.sampleSetModelIdsUsed()
                && !j3.sampleSetBridgeUsedAsFormalSource();
        if (!prerequisites) {
            this.fail("j3-or-formal-resources-not-ready:" + j3.lastFailureReason());
            this.audit();
            this.instance.getFormalRendererManager().checkReadiness("j4-formal-packed-quad-preview-build-failed");
            return this.createStatusSnapshot();
        }

        List<PreviewQuad> quads = this.collectPreviewQuads(summaries);
        if (quads.isEmpty()) {
            this.fail("no-safe-packed-quad-records");
            this.audit();
            this.instance.getFormalRendererManager().checkReadiness("j4-formal-packed-quad-preview-empty");
            return this.createStatusSnapshot();
        }

        try {
            this.closeOwnedResourcesOnRenderThread(false);
            this.previewProgramId = this.compilePreviewProgram();
            this.shaderCompileOk = true;
            this.programLinkOk = true;
            this.createTemporaryQuadBuffer(quads);
            this.createFramebuffer();
            this.drawPreview(store, quads.size());
            this.readbackPreview(quads);
            if (this.previewReadbackOk && this.previewPixelMismatches == 0) {
                this.stale = false;
                this.requiresRebuild = false;
                this.staleReason = "none";
            }
        } catch (RuntimeException e) {
            this.fail(e.getClass().getSimpleName() + ": " + e.getMessage());
        }

        this.previewReady = this.shaderCompileOk
                && this.programLinkOk
                && this.temporaryFormalQuadBufferCreated
                && this.temporaryFormalQuadCount > 0
                && this.modelDataBindingOk
                && this.modelColourBindingOk
                && this.atlasTextureBindingOk
                && this.samplerBindingOk
                && this.quadRecordDecodeOk
                && this.modelIdDecodeOk
                && this.faceDecodeOk
                && this.previewFramebufferComplete
                && this.previewReadbackOk
                && this.previewPixelMismatches == 0
                && !this.stale;
        if (this.previewReady) {
            this.lifecycleState = "BUILT";
            this.lastLifecycleEvent = "build-complete";
            this.lastFailureReason = "none";
        } else if ("none".equals(this.lastFailureReason)) {
            this.fail("formal-packed-quad-preview-incomplete");
        }
        this.audit();
        this.instance.getFormalRendererManager().checkReadiness("j4-formal-packed-quad-preview-build");
        return this.createStatusSnapshot();
    }

    ForgeFormalPackedQuadPreviewAuditResult audit() {
        this.auditRuns++;
        long start = System.nanoTime();
        try {
            this.lastAudit = this.auditInternal(start);
        } catch (RuntimeException e) {
            this.lastAudit = ForgeFormalPackedQuadPreviewAuditResult.failure(e.getClass().getSimpleName() + ": " + e.getMessage(), elapsedMs(start));
        }
        if (!this.lastAudit.success()) {
            this.auditFailures++;
            this.lastFailureReason = this.lastAudit.error();
        }
        return this.lastAudit;
    }

    ForgeFormalPackedQuadPreviewAuditResult createAuditStatusSnapshot() {
        return this.lastAudit;
    }

    ForgeFormalPackedQuadPreviewStats createStatusSnapshot() {
        ForgeFormalTexturedShaderPreviewStats j3 = this.instance.getFormalTexturedShaderPreview().createStatusSnapshot();
        boolean usesFormalIds = this.temporaryFormalQuadCount > 0 && !this.temporaryFormalModelIds.isBlank();
        boolean ready = this.previewReady
                && !this.stale
                && j3.formalTexturedShaderPrototypeReady()
                && this.temporaryFormalQuadBufferCreated
                && this.previewReadbackOk
                && this.previewPixelMismatches == 0;
        return new ForgeFormalPackedQuadPreviewStats(
                STAGE,
                this.buildRuns,
                this.auditRuns,
                this.clearRuns,
                this.auditFailures,
                ready,
                ready,
                false,
                false,
                true,
                ready,
                false,
                false,
                false,
                true,
                this.sourceRecordsScanned,
                this.sourceRecordsAccepted,
                this.sourceRecordsRejected,
                this.recordsRejectedNoBlockState,
                this.recordsRejectedNoFormalModelId,
                this.recordsRejectedUnsafeModelId,
                this.realTerrainRecordsUsed,
                this.syntheticFallbackUsed,
                this.syntheticFallbackReason,
                this.temporaryFormalQuadBufferCreated,
                this.temporaryFormalQuadBufferId,
                this.temporaryFormalQuadCount,
                this.temporaryFormalVertexCount,
                this.temporaryFormalModelIds,
                usesFormalIds,
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
                ForgeFormalShaderInputBindingLayout.bindingLayoutKnown() && ForgeFormalShaderInputBindingLayout.recordLayoutCompatible(),
                this.quadRecordDecodeOk,
                this.modelIdDecodeOk,
                this.faceDecodeOk,
                this.faceDataUsed,
                this.atlasSampleUsed,
                this.modelColourUsed,
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
        ForgeFormalPackedQuadPreviewStats status = this.createStatusSnapshot();
        return "J4 formal packed quad shader geometry preview: "
                + "stage=" + status.stage()
                + " sourceRecordsScanned=" + status.sourceRecordsScanned()
                + " sourceRecordsAccepted=" + status.sourceRecordsAccepted()
                + " sourceRecordsRejected=" + status.sourceRecordsRejected()
                + " realTerrainRecordsUsed=" + status.realTerrainRecordsUsed()
                + " syntheticFallbackUsed=" + status.syntheticFallbackUsed()
                + " syntheticFallbackReason=" + status.syntheticFallbackReason()
                + " temporaryFormalQuadCount=" + status.temporaryFormalQuadCount()
                + " temporaryFormalModelIds=" + status.temporaryFormalModelIds()
                + " previewChecksums=" + status.previewChecksums()
                + " originalGeometryUntouched=true terrainDrawStarted=false formalRendererDrawStarted=false actualRendererDrawEnabled=false"
                + " formalTexturedShaderReady=false formalRendererReady=false lastFailureReason=" + status.lastFailureReason();
    }

    void clear() {
        this.clearRuns++;
        this.previewReady = false;
        this.stale = false;
        this.requiresRebuild = false;
        this.lifecycleState = "CLEARED";
        this.lastLifecycleEvent = "clear";
        this.staleReason = "none";
        this.lastFailureReason = "none";
        this.resetPreviewFlags();
        this.lastAudit = ForgeFormalPackedQuadPreviewAuditResult.failure("none", 0.0D);
        this.scheduleCleanup("clear");
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
        this.previewReady = false;
        this.stale = true;
        this.requiresRebuild = true;
        this.lifecycleState = "STALE";
        this.lastLifecycleEvent = safeReason(reason);
        this.staleReason = this.lastLifecycleEvent;
        this.lastFailureReason = this.staleReason;
        this.scheduleCleanup(this.lastLifecycleEvent);
    }

    private ForgeFormalPackedQuadPreviewAuditResult auditInternal(long startNanos) {
        ForgeFormalTexturedShaderPreviewStats j3 = this.instance.getFormalTexturedShaderPreview().createStatusSnapshot();
        boolean mapped = this.temporaryFormalQuadCount > 0 && !this.temporaryFormalModelIds.isBlank();
        boolean success = this.previewReady
                && !this.stale
                && j3.formalTexturedShaderPrototypeReady()
                && mapped
                && this.temporaryFormalQuadBufferCreated
                && this.quadRecordDecodeOk
                && this.modelIdDecodeOk
                && this.faceDecodeOk
                && this.previewFramebufferComplete
                && this.previewReadbackOk
                && this.previewPixelMismatches == 0
                && this.previewChecksumCount > 0;
        return new ForgeFormalPackedQuadPreviewAuditResult(
                success,
                success ? "none" : "formal-packed-quad-preview-audit-failed",
                elapsedMs(startNanos),
                this.instance.getFormalModelBakeryLifecycle().createStatusSnapshot().reloadRebuildPrototypeReady(),
                j3.formalTexturedShaderPrototypeReady(),
                mapped,
                false,
                false,
                false,
                true,
                this.temporaryFormalQuadBufferCreated,
                this.temporaryFormalQuadBufferCreated,
                this.quadRecordDecodeOk,
                this.modelIdDecodeOk,
                this.faceDecodeOk,
                this.createStatusSnapshot().offscreenPreviewReady(),
                this.previewFramebufferComplete,
                this.previewReadbackOk,
                this.previewPixelMismatches,
                this.previewChecksumCount,
                true,
                false,
                false,
                false,
                false,
                false
        );
    }

    private List<PreviewQuad> collectPreviewQuads(List<ForgeFormalUploadedModelSummary> summaries) {
        List<PreviewQuad> result = new ArrayList<>();
        Map<Integer, ForgeFormalUploadedModelSummary> summariesByBlockState = summaries.stream()
                .collect(Collectors.toMap(ForgeFormalUploadedModelSummary::blockStateId, summary -> summary, (a, b) -> a, LinkedHashMap::new));
        this.collectRealTerrainRecords(summariesByBlockState, result);
        if (!result.isEmpty()) {
            this.realTerrainRecordsUsed = true;
            this.syntheticFallbackUsed = false;
            this.syntheticFallbackReason = "none";
            return result;
        }
        this.realTerrainRecordsUsed = false;
        this.syntheticFallbackUsed = true;
        this.syntheticFallbackReason = this.sourceRecordsScanned == 0 ? "no-live-built-section-packed-records" : "no-real-records-mapped-to-i6-safe-set-formal-ids";
        int count = Math.min(MAX_PREVIEW_QUADS, summaries.size());
        for (int i = 0; i < count; i++) {
            ForgeFormalUploadedModelSummary summary = summaries.get(i);
            int face = i % ForgeModelAtlasLayout.FACE_COUNT;
            long record = ForgeVoxyQuadEncoder.packPreviewRecord(
                    face,
                    2 + (i % 4) * 6,
                    8 + (i / 4) * 8,
                    2 + (i % 3) * 6,
                    4,
                    4,
                    summary.formalModelId(),
                    0,
                    0xF0
            );
            result.add(new PreviewQuad(record, summary.formalModelId(), summary.blockStateId(), false));
        }
        this.sourceRecordsAccepted = result.size();
        return result;
    }

    private void collectRealTerrainRecords(Map<Integer, ForgeFormalUploadedModelSummary> summariesByBlockState, List<PreviewQuad> result) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            return;
        }
        String dimension = minecraft.level.dimension().location().toString();
        int centerChunkX = minecraft.player.chunkPosition().x;
        int centerChunkZ = minecraft.player.chunkPosition().z;
        List<ForgeVoxyBuiltSection> sections = this.instance.getVoxyGeometryCache().createAreaSnapshot(dimension, centerChunkX, centerChunkZ, 4, 32);
        for (ForgeVoxyBuiltSection section : sections) {
            ForgeVoxyGeometryBuffer buffer = section.geometryBuffer();
            if (buffer == null || buffer.isClosed()) {
                continue;
            }
            for (long record : buffer.packedQuads()) {
                this.sourceRecordsScanned++;
                if (result.size() >= MAX_PREVIEW_QUADS) {
                    return;
                }
                int legacyModelId = ForgeVoxyQuadEncoder.extractModelId(record);
                OptionalInt blockStateId = ForgeVoxyModelIdMapper.INSTANCE.blockStateIdForModelId(legacyModelId);
                if (blockStateId.isEmpty()) {
                    this.recordsRejectedNoBlockState++;
                    this.sourceRecordsRejected++;
                    continue;
                }
                ForgeFormalUploadedModelSummary summary = summariesByBlockState.get(blockStateId.getAsInt());
                if (summary == null) {
                    this.recordsRejectedNoFormalModelId++;
                    this.sourceRecordsRejected++;
                    continue;
                }
                Optional<ForgeFormalModelIdMapping> mapping = this.instance.getFormalModelFactory().mappingForBlockStateId(blockStateId.getAsInt());
                if (mapping.isEmpty() || mapping.get().formalModelId() != summary.formalModelId() || !ForgeModelAtlasLayout.isValidModelId(summary.formalModelId())) {
                    this.recordsRejectedUnsafeModelId++;
                    this.sourceRecordsRejected++;
                    continue;
                }
                long formalRecord = ForgeVoxyQuadEncoder.replaceModelId(record, summary.formalModelId());
                result.add(new PreviewQuad(formalRecord, summary.formalModelId(), blockStateId.getAsInt(), true));
                this.sourceRecordsAccepted++;
            }
        }
    }

    private void createTemporaryQuadBuffer(List<PreviewQuad> quads) {
        int count = Math.min(MAX_PREVIEW_QUADS, quads.size());
        Set<Integer> modelIds = new LinkedHashSet<>();
        this.temporaryFormalQuadBufferId = GL45C.glCreateBuffers();
        long ptr = MemoryUtil.nmemAlloc((long) count * Long.BYTES);
        try {
            for (int i = 0; i < count; i++) {
                long record = quads.get(i).record();
                MemoryUtil.memPutInt(ptr + ((long) i * Long.BYTES), (int) record);
                MemoryUtil.memPutInt(ptr + ((long) i * Long.BYTES) + Integer.BYTES, (int) (record >>> 32));
                modelIds.add(quads.get(i).formalModelId());
            }
            GL45C.nglNamedBufferData(this.temporaryFormalQuadBufferId, (long) count * Long.BYTES, ptr, GL15C.GL_STATIC_DRAW);
        } finally {
            MemoryUtil.nmemFree(ptr);
        }
        this.temporaryFormalQuadBufferCreated = this.temporaryFormalQuadBufferId != 0;
        this.temporaryFormalQuadCount = count;
        this.temporaryFormalVertexCount = count * 6;
        this.temporaryFormalModelIds = modelIds.stream().map(String::valueOf).collect(Collectors.joining(","));
        this.quadRecordDecodeOk = quads.stream().limit(count).allMatch(quad -> ForgeVoxyQuadEncoder.extractFace(quad.record()) >= 0 && ForgeVoxyQuadEncoder.extractFace(quad.record()) <= 5);
        this.modelIdDecodeOk = quads.stream().limit(count).allMatch(quad -> ForgeVoxyQuadEncoder.extractModelId(quad.record()) == quad.formalModelId());
        this.faceDecodeOk = this.quadRecordDecodeOk;
    }

    private int compilePreviewProgram() {
        int vertexShader = compileShader(GL20C.GL_VERTEX_SHADER, VERTEX_SOURCE);
        int fragmentShader = compileShader(GL20C.GL_FRAGMENT_SHADER, FRAGMENT_SOURCE);
        int program = GL20C.glCreateProgram();
        GL20C.glAttachShader(program, vertexShader);
        GL20C.glAttachShader(program, fragmentShader);
        GL20C.glLinkProgram(program);
        GL20C.glDeleteShader(vertexShader);
        GL20C.glDeleteShader(fragmentShader);
        if (GL20C.glGetProgrami(program, GL20C.GL_LINK_STATUS) != GL11C.GL_TRUE) {
            String log = GL20C.glGetProgramInfoLog(program);
            GL20C.glDeleteProgram(program);
            throw new IllegalStateException("packed-quad-preview-link-failed:" + sanitize(log));
        }
        return program;
    }

    private static int compileShader(int type, String source) {
        int shader = GL20C.glCreateShader(type);
        GL20C.glShaderSource(shader, source);
        GL20C.glCompileShader(shader);
        if (GL20C.glGetShaderi(shader, GL20C.GL_COMPILE_STATUS) != GL11C.GL_TRUE) {
            String log = GL20C.glGetShaderInfoLog(shader);
            GL20C.glDeleteShader(shader);
            throw new IllegalStateException("packed-quad-preview-compile-failed:" + sanitize(log));
        }
        return shader;
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
                throw new IllegalStateException("packed-quad-preview-framebuffer-incomplete");
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
        int oldPackedBuffer = GL30C.glGetIntegeri(GL43C.GL_SHADER_STORAGE_BUFFER_BINDING, PACKED_QUAD_BINDING_INDEX);
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
            int countLocation = GL20C.glGetUniformLocation(this.previewProgramId, "previewQuadCount");
            if (countLocation >= 0) {
                GL30C.glUniform1ui(countLocation, count);
            }
            GL30C.glBindBufferBase(GL43C.GL_SHADER_STORAGE_BUFFER, ForgeFormalShaderInputBindingLayout.MODEL_DATA_BINDING_INDEX, store.modelDataBufferId());
            GL30C.glBindBufferBase(GL43C.GL_SHADER_STORAGE_BUFFER, ForgeFormalShaderInputBindingLayout.MODEL_COLOUR_BINDING_INDEX, store.modelColourBufferId());
            GL30C.glBindBufferBase(GL43C.GL_SHADER_STORAGE_BUFFER, PACKED_QUAD_BINDING_INDEX, this.temporaryFormalQuadBufferId);
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
                throw new IllegalStateException("packed-quad-preview-draw-gl-error-" + this.lastGlError);
            }
            this.faceDataUsed = true;
            this.atlasSampleUsed = true;
            this.modelColourUsed = true;
        } finally {
            GL20C.glUseProgram(oldProgram);
            GL30C.glBindVertexArray(oldVertexArray);
            GL30C.glBindBufferBase(GL43C.GL_SHADER_STORAGE_BUFFER, ForgeFormalShaderInputBindingLayout.MODEL_DATA_BINDING_INDEX, oldModelBuffer);
            GL30C.glBindBufferBase(GL43C.GL_SHADER_STORAGE_BUFFER, ForgeFormalShaderInputBindingLayout.MODEL_COLOUR_BINDING_INDEX, oldColourBuffer);
            GL30C.glBindBufferBase(GL43C.GL_SHADER_STORAGE_BUFFER, PACKED_QUAD_BINDING_INDEX, oldPackedBuffer);
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

    private void readbackPreview(List<PreviewQuad> quads) {
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
            int count = Math.min(MAX_PREVIEW_QUADS, quads.size());
            this.previewChecksums = computeCellChecksums(quads, count, pixels);
            this.previewChecksumCount = count;
            this.previewPixelMismatches = countBlankCells(count, pixels);
            this.previewReadbackOk = this.previewPixelMismatches == 0;
        } finally {
            GL30C.glBindFramebuffer(GL30C.GL_READ_FRAMEBUFFER, oldReadFramebuffer);
            GL11C.glReadBuffer(oldReadBuffer);
            GL15C.glBindBuffer(GL21C.GL_PIXEL_PACK_BUFFER, packBufferBinding);
            pixelStore.restorePack();
            MemoryUtil.memFree(buffer);
        }
    }

    private static String computeCellChecksums(List<PreviewQuad> quads, int count, byte[] pixels) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < count; i++) {
            if (i > 0) {
                builder.append(',');
            }
            CRC32 crc = new CRC32();
            Cell cell = cellFor(i);
            for (int y = cell.y0(); y < cell.y1(); y++) {
                int offset = ((y * PREVIEW_WIDTH) + cell.x0()) * BYTES_PER_PIXEL;
                int length = Math.max(0, (cell.x1() - cell.x0()) * BYTES_PER_PIXEL);
                crc.update(pixels, offset, length);
            }
            builder.append("quad").append(i)
                    .append("_model").append(quads.get(i).formalModelId())
                    .append("=0x").append(Long.toHexString(crc.getValue()).toUpperCase());
        }
        return builder.toString();
    }

    private static int countBlankCells(int count, byte[] pixels) {
        int failures = 0;
        for (int i = 0; i < count; i++) {
            Cell cell = cellFor(i);
            long alphaSum = 0L;
            long rgbSum = 0L;
            for (int y = cell.y0(); y < cell.y1(); y++) {
                for (int x = cell.x0(); x < cell.x1(); x++) {
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

    private static Cell cellFor(int index) {
        int column = index % 4;
        int row = index / 4;
        int x0 = (PREVIEW_WIDTH * column) / 4;
        int x1 = (PREVIEW_WIDTH * (column + 1)) / 4;
        int y0 = row == 0 ? PREVIEW_HEIGHT / 2 : 0;
        int y1 = row == 0 ? PREVIEW_HEIGHT : PREVIEW_HEIGHT / 2;
        return new Cell(x0, x1, y0, y1);
    }

    private void resetPreviewFlags() {
        this.previewReady = false;
        this.shaderCompileOk = false;
        this.programLinkOk = false;
        this.temporaryFormalQuadBufferCreated = false;
        this.temporaryFormalQuadCount = 0;
        this.temporaryFormalVertexCount = 0;
        this.temporaryFormalModelIds = "";
        this.sourceRecordsScanned = 0;
        this.sourceRecordsAccepted = 0;
        this.sourceRecordsRejected = 0;
        this.recordsRejectedNoBlockState = 0;
        this.recordsRejectedNoFormalModelId = 0;
        this.recordsRejectedUnsafeModelId = 0;
        this.realTerrainRecordsUsed = false;
        this.syntheticFallbackUsed = false;
        this.syntheticFallbackReason = "none";
        this.modelDataBindingOk = false;
        this.modelColourBindingOk = false;
        this.atlasTextureBindingOk = false;
        this.samplerBindingOk = false;
        this.quadRecordDecodeOk = false;
        this.modelIdDecodeOk = false;
        this.faceDecodeOk = false;
        this.faceDataUsed = false;
        this.atlasSampleUsed = false;
        this.modelColourUsed = false;
        this.previewFramebufferCreated = false;
        this.previewFramebufferComplete = false;
        this.previewReadbackOk = false;
        this.previewPixelMismatches = 0;
        this.previewChecksumCount = 0;
        this.previewChecksums = "";
        this.lastGlError = "none";
    }

    private void fail(String reason) {
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
        if (this.temporaryFormalQuadBufferId != 0) {
            GL15C.glDeleteBuffers(this.temporaryFormalQuadBufferId);
        }
        this.previewProgramId = 0;
        this.previewFramebufferId = 0;
        this.previewTextureId = 0;
        this.previewVertexArrayId = 0;
        this.temporaryFormalQuadBufferId = 0;
        if (resetProgramState) {
            this.shaderCompileOk = false;
            this.programLinkOk = false;
        }
    }

    private boolean formalResourcesReady(ForgeFormalModelStoreStats store) {
        return store.formalModelStoreOwnerReady()
                && !store.stale()
                && !store.requiresRebuild()
                && store.modelDataBufferCreated()
                && store.modelColourBufferCreated()
                && store.atlasTextureCreated()
                && store.fullAtlasTextureCreated()
                && store.samplerCreated()
                && store.samplerConfigured();
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

    private record PreviewQuad(long record, int formalModelId, int blockStateId, boolean realTerrainRecord) {
    }

    private record Cell(int x0, int x1, int y0, int y1) {
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
