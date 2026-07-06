package me.cortex.voxy.forge;

import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import me.cortex.voxy.config.ForgeVoxyConfig;
import me.cortex.voxy.common.util.MemoryBuffer;
import me.cortex.voxy.common.world.WorldEngine;
import org.joml.Vector4f;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import static org.lwjgl.opengl.GL11.GL_FALSE;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_MAG_FILTER;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_MIN_FILTER;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_WRAP_S;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_WRAP_T;
import static org.lwjgl.opengl.GL11.GL_TRUE;
import static org.lwjgl.opengl.GL11.GL_UNSIGNED_INT;
import static org.lwjgl.opengl.GL11C.GL_NEAREST;
import static org.lwjgl.opengl.GL11C.GL_NEAREST_MIPMAP_NEAREST;
import static org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE;
import static org.lwjgl.opengl.GL12.GL_UNPACK_IMAGE_HEIGHT;
import static org.lwjgl.opengl.GL11C.GL_UNPACK_ROW_LENGTH;
import static org.lwjgl.opengl.GL12.GL_UNPACK_SKIP_IMAGES;
import static org.lwjgl.opengl.GL12.GL_UNPACK_SKIP_PIXELS;
import static org.lwjgl.opengl.GL12.GL_UNPACK_SKIP_ROWS;
import static org.lwjgl.opengl.GL30.GL_RED_INTEGER;
import static org.lwjgl.opengl.GL30C.GL_R32UI;
import static org.lwjgl.opengl.GL30C.glBindBufferBase;
import static org.lwjgl.opengl.GL31.GL_UNIFORM_BUFFER;
import static org.lwjgl.opengl.GL33.glBindSampler;
import static org.lwjgl.opengl.GL33.glGenSamplers;
import static org.lwjgl.opengl.GL33C.glDeleteSamplers;
import static org.lwjgl.opengl.GL33C.glSamplerParameteri;
import static org.lwjgl.opengl.GL42C.GL_BUFFER_UPDATE_BARRIER_BIT;
import static org.lwjgl.opengl.GL42C.glMemoryBarrier;
import static org.lwjgl.opengl.GL43C.GL_COMMAND_BARRIER_BIT;
import static org.lwjgl.opengl.GL43C.GL_COMPUTE_SHADER;
import static org.lwjgl.opengl.GL43C.GL_DISPATCH_INDIRECT_BUFFER;
import static org.lwjgl.opengl.GL43C.GL_SHADER_STORAGE_BARRIER_BIT;
import static org.lwjgl.opengl.GL43C.GL_SHADER_STORAGE_BUFFER;
import static org.lwjgl.opengl.GL43C.glDispatchCompute;
import static org.lwjgl.opengl.GL43C.glDispatchComputeIndirect;
import static org.lwjgl.opengl.GL43C.glUniform1ui;
import static org.lwjgl.opengl.GL45C.glBindBuffer;
import static org.lwjgl.opengl.GL45C.glBindTextureUnit;
import static org.lwjgl.opengl.GL45C.glCreateProgram;
import static org.lwjgl.opengl.GL45C.glCreateShader;
import static org.lwjgl.opengl.GL45C.glDeleteProgram;
import static org.lwjgl.opengl.GL45C.glDeleteShader;
import static org.lwjgl.opengl.GL45C.glGetProgramInfoLog;
import static org.lwjgl.opengl.GL45C.glGetProgrami;
import static org.lwjgl.opengl.GL45C.glGetShaderInfoLog;
import static org.lwjgl.opengl.GL45C.glGetShaderi;
import static org.lwjgl.opengl.GL45C.glLinkProgram;
import static org.lwjgl.opengl.GL45C.glPixelStorei;
import static org.lwjgl.opengl.GL45C.nglClearNamedBufferSubData;
import static org.lwjgl.opengl.GL45C.nglGetNamedBufferSubData;
import static org.lwjgl.opengl.GL45C.nglShaderSource;
import static org.lwjgl.opengl.GL45C.glCompileShader;
import static org.lwjgl.opengl.GL45C.glUseProgram;
import static org.lwjgl.opengl.GL20C.glAttachShader;
import static org.lwjgl.opengl.GL20C.GL_COMPILE_STATUS;
import static org.lwjgl.opengl.GL20C.GL_LINK_STATUS;

final class ForgeOriginalVoxyHierarchicalOcclusionTraverser {
    private static final boolean HIERARCHICAL_SHADER_DEBUG =
            System.getProperty("voxy.hierarchicalShaderDebug", "false").equals("true");
    private static final boolean ENABLE_PRINTF_DEBUGGING =
            System.getProperty("voxy.enableShaderDebugPrintf", "false").equals("true");

    static final int MAX_REQUEST_QUEUE_SIZE = 50;
    static final int MAX_QUEUE_SIZE = 200_000;

    private static final int MAX_ITERATIONS = WorldEngine.MAX_LOD_LAYER + 1;
    private static final int LOCAL_WORK_SIZE_BITS = 5;
    private static final int READBACK_AUDIT_SAMPLE_COUNT = 256;
    private static final int MAX_AUTO_READBACK_AUDITS = 20;
    private static final int HOC_AUDIT_COUNTER_COUNT = 40;
    private static final int NULL_NODE = (1 << 24) - 1;
    private static final int EMPTY_QUEUE_ID = (1 << 24) - 2;
    private static final int NULL_MESH = (1 << 24) - 1;
    private static final int EMPTY_MESH = (1 << 24) - 2;

    private static int bindingCounter = 1;
    private static final int SCENE_UNIFORM_BINDING = bindingCounter++;
    private static final int REQUEST_QUEUE_BINDING = bindingCounter++;
    private static final int RENDER_QUEUE_BINDING = bindingCounter++;
    private static final int NODE_DATA_BINDING = bindingCounter++;
    private static final int NODE_QUEUE_INDEX_BINDING = bindingCounter++;
    private static final int NODE_QUEUE_META_BINDING = bindingCounter++;
    private static final int NODE_QUEUE_SOURCE_BINDING = bindingCounter++;
    private static final int NODE_QUEUE_SINK_BINDING = bindingCounter++;
    private static final int RENDER_TRACKER_BINDING = bindingCounter++;
    private static final int HOC_AUDIT_BINDING = bindingCounter++;
    private static final long SCRATCH = MemoryUtil.nmemAlloc(32);

    private final ForgeOriginalVoxyAsyncNodeGeometrySync nodeSync;
    private final ForgeOriginalVoxyNodeCleaner nodeCleaner;
    private final ForgeOriginalVoxyRenderGenerationService meshGen;
    private final ForgeOriginalVoxyGlBuffer requestBuffer = new ForgeOriginalVoxyGlBuffer(MAX_REQUEST_QUEUE_SIZE * 8L + 8L).zero();
    private final ForgeOriginalVoxyGlBuffer nodeBuffer;
    private final ForgeOriginalVoxyGlBuffer uniformBuffer = new ForgeOriginalVoxyGlBuffer(1024).zero();
    private final ForgeOriginalVoxyGlBuffer topNodeIds = new ForgeOriginalVoxyGlBuffer(MAX_QUEUE_SIZE * 4L).zero();
    private final ForgeOriginalVoxyGlBuffer queueMetaBuffer = new ForgeOriginalVoxyGlBuffer(4L * 4L * MAX_ITERATIONS).zero();
    private final ForgeOriginalVoxyGlBuffer scratchQueueA = new ForgeOriginalVoxyGlBuffer(MAX_QUEUE_SIZE * 4L).zero();
    private final ForgeOriginalVoxyGlBuffer scratchQueueB = new ForgeOriginalVoxyGlBuffer(MAX_QUEUE_SIZE * 4L).zero();
    private final ForgeOriginalVoxyGlBuffer hocAuditBuffer = new ForgeOriginalVoxyGlBuffer(HOC_AUDIT_COUNTER_COUNT * 4L).zero();
    private final Int2IntOpenHashMap topNode2idxMapping = new Int2IntOpenHashMap();
    private final int[] idx2topNodeMapping = new int[MAX_QUEUE_SIZE];
    private final int hizSampler = glGenSamplers();
    private int traversalProgramId;
    private ForgeOriginalVoxyRenderPipeline taaPipeline;
    private boolean taaInjected;
    private int topNodeCount;
    private long traversalRunCount;
    private long requestBatchForwardCount;
    private boolean readbackAuditRequested;
    private long readbackAuditRuns;
    private String lastLifecycleEvent = "created";
    private String lastFailureReason = "none";

    ForgeOriginalVoxyHierarchicalOcclusionTraverser(
            ForgeOriginalVoxyAsyncNodeGeometrySync nodeSync,
            ForgeOriginalVoxyNodeCleaner nodeCleaner,
            ForgeOriginalVoxyRenderGenerationService meshGen) {
        this.nodeSync = nodeSync;
        this.nodeCleaner = nodeCleaner;
        this.meshGen = meshGen;
        this.nodeBuffer = new ForgeOriginalVoxyGlBuffer((long) nodeSync.maxNodeCount() * 16L).fill(-1);
        this.nodeSync.setExternalNodeBuffer(this.nodeBuffer.id);
        this.nodeSync.setTopLevelNodeCallbacks(this::addTopLevelNode, this::removeTopLevelNode);
        this.topNode2idxMapping.defaultReturnValue(-1);

        glSamplerParameteri(this.hizSampler, GL_TEXTURE_MIN_FILTER, GL_NEAREST_MIPMAP_NEAREST);
        glSamplerParameteri(this.hizSampler, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glSamplerParameteri(this.hizSampler, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        glSamplerParameteri(this.hizSampler, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
    }

    String buildOnRenderThread(ForgeOriginalVoxyRenderProperties properties, ForgeOriginalVoxyRenderPipeline pipeline) {
        try {
            if (this.traversalProgramId != 0) {
                glDeleteProgram(this.traversalProgramId);
                this.traversalProgramId = 0;
            }
            this.taaPipeline = null;
            this.taaInjected = false;
            String source = ForgeOriginalVoxyShaderSource.parse("voxy:lod/hierarchical/traversal_dev.comp");
            String taa = pipeline == null ? null : pipeline.taaFunction("getTAA");
            if (taa != null) {
                source += "\n\n\n\n" + taa;
                source = withDefines(source, "TAA", 1);
                this.taaPipeline = pipeline;
                this.taaInjected = true;
            }
            source = properties.injectDefines(source);
            source = applyOriginalPrintfProcessor(source);
            if (HIERARCHICAL_SHADER_DEBUG) {
                source = withDefines(source, "DEBUG", 1);
            }
            source = withDefines(source,
                    "MAX_ITERATIONS", MAX_ITERATIONS,
                    "LOCAL_SIZE_BITS", LOCAL_WORK_SIZE_BITS,
                    "MAX_REQUEST_QUEUE_SIZE", MAX_REQUEST_QUEUE_SIZE,
                    "HIZ_BINDING", 0,
                    "SCENE_UNIFORM_BINDING", SCENE_UNIFORM_BINDING,
                    "REQUEST_QUEUE_BINDING", REQUEST_QUEUE_BINDING,
                    "RENDER_QUEUE_BINDING", RENDER_QUEUE_BINDING,
                    "NODE_DATA_BINDING", NODE_DATA_BINDING,
                    "NODE_QUEUE_INDEX_BINDING", NODE_QUEUE_INDEX_BINDING,
                    "NODE_QUEUE_META_BINDING", NODE_QUEUE_META_BINDING,
                    "NODE_QUEUE_SOURCE_BINDING", NODE_QUEUE_SOURCE_BINDING,
                    "NODE_QUEUE_SINK_BINDING", NODE_QUEUE_SINK_BINDING,
                    "RENDER_TRACKER_BINDING", RENDER_TRACKER_BINDING,
                    "HOC_AUDIT_BINDING", HOC_AUDIT_BINDING);
            this.traversalProgramId = compileComputeProgram(source);
            this.lastLifecycleEvent = "build-on-render-thread";
            this.lastFailureReason = "none";
            return "none";
        } catch (RuntimeException e) {
            this.recordFailure("original-hoc-" + e.getClass().getSimpleName() + ":" + e.getMessage());
            return this.lastFailureReason;
        }
    }

    boolean ready() {
        return this.traversalProgramId != 0
                && this.nodeBuffer.id != 0
                && this.requestBuffer.id != 0
                && this.topNodeIds.id != 0
                && this.queueMetaBuffer.id != 0
                && this.scratchQueueA.id != 0
                && this.scratchQueueB.id != 0
                && this.hocAuditBuffer.id != 0;
    }

    void requestReadbackAudit() {
        this.readbackAuditRequested = true;
    }

    void doTraversal(ForgeOriginalVoxyMdicViewport viewport) {
        if (!this.ready()) {
            this.recordFailure("hierarchical-occlusion-traverser-not-ready");
            return;
        }
        if (viewport.hizTextureId() == 0) {
            this.recordFailure("hierarchical-occlusion-hiz-not-built");
            return;
        }
        this.uploadUniform(viewport);
        glUseProgram(this.traversalProgramId);
        if (this.taaInjected && this.taaPipeline != null) {
            this.taaPipeline.bindUniforms();
        }
        this.bindings(viewport);
        this.hocAuditBuffer.zero();
        nglClearNamedBufferSubData(viewport.renderListBufferId(), GL_R32UI, 0L, Integer.BYTES, GL_RED_INTEGER, GL_UNSIGNED_INT, 0L);
        this.traverseInternal();
        this.downloadResetRequestQueue();
        glBindSampler(0, 0);
        glBindTextureUnit(0, 0);
        glUseProgram(0);
        this.traversalRunCount++;
        this.lastLifecycleEvent = "do-traversal";
        this.lastFailureReason = "none";
    }

    void runReadbackAuditIfRequested(ForgeOriginalVoxyMdicViewport viewport) {
        if (!this.readbackAuditRequested) {
            return;
        }
        if (this.readbackAuditRuns >= MAX_AUTO_READBACK_AUDITS) {
            this.readbackAuditRequested = false;
            return;
        }
        this.readbackAuditRequested = false;
        this.readbackAuditRuns++;
        glMemoryBarrier(GL_SHADER_STORAGE_BARRIER_BIT | GL_COMMAND_BARRIER_BIT | GL_BUFFER_UPDATE_BARRIER_BIT);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            int firstTopNodeId = -1;
            int firstNodeWord0 = 0;
            int firstNodeWord1 = 0;
            int firstNodeWord2 = 0;
            int firstNodeWord3 = 0;
            if (this.topNodeCount > 0) {
                long firstTopNodePtr = stack.nmalloc(Integer.BYTES);
                nglGetNamedBufferSubData(this.topNodeIds.id, 0L, Integer.BYTES, firstTopNodePtr);
                firstTopNodeId = MemoryUtil.memGetInt(firstTopNodePtr);
                if (firstTopNodeId >= 0 && firstTopNodeId < this.nodeBuffer.size() / 16L) {
                    long nodePtr = stack.nmalloc(16);
                    nglGetNamedBufferSubData(this.nodeBuffer.id, firstTopNodeId * 16L, 16L, nodePtr);
                    firstNodeWord0 = MemoryUtil.memGetInt(nodePtr);
                    firstNodeWord1 = MemoryUtil.memGetInt(nodePtr + 4L);
                    firstNodeWord2 = MemoryUtil.memGetInt(nodePtr + 8L);
                    firstNodeWord3 = MemoryUtil.memGetInt(nodePtr + 12L);
                }
            }

            long queueMetaPtr = stack.nmalloc(16);
            nglGetNamedBufferSubData(this.queueMetaBuffer.id, 0L, 16L, queueMetaPtr);
            int queueDispatchX = MemoryUtil.memGetInt(queueMetaPtr);
            int queueDispatchY = MemoryUtil.memGetInt(queueMetaPtr + 4L);
            int queueDispatchZ = MemoryUtil.memGetInt(queueMetaPtr + 8L);
            int queueInitialCount = MemoryUtil.memGetInt(queueMetaPtr + 12L);

            int renderListCounter = -1;
            if (viewport != null && viewport.renderListBufferId() != 0) {
                long renderListPtr = stack.nmalloc(Integer.BYTES);
                nglGetNamedBufferSubData(viewport.renderListBufferId(), 0L, Integer.BYTES, renderListPtr);
                renderListCounter = MemoryUtil.memGetInt(renderListPtr);
            }
            this.auditRenderListContent(viewport, renderListCounter);

            AuditNodeSample sample = this.sampleTopNodes(stack, viewport);
            long hocAuditPtr = stack.nmalloc(HOC_AUDIT_COUNTER_COUNT * Integer.BYTES);
            nglGetNamedBufferSubData(
                    this.hocAuditBuffer.id,
                    0L,
                    HOC_AUDIT_COUNTER_COUNT * (long) Integer.BYTES,
                    hocAuditPtr);

            String auditReason = "none";
            if (this.topNodeCount == 0) {
                auditReason = "hoc-no-top-level-nodes";
            } else if (firstTopNodeId < 0) {
                auditReason = "hoc-first-top-node-id-invalid";
            } else if (firstNodeWord0 == -1 && firstNodeWord1 == -1 && firstNodeWord2 == -1 && firstNodeWord3 == -1) {
                auditReason = "hoc-first-top-node-buffer-empty";
            } else if (renderListCounter == 0) {
                auditReason = "hoc-traversal-render-list-empty";
            }
            VoxyForge.LOGGER.info(
                    "Original HOC readback audit: run={} topNodeCount={} firstTopNodeId={} firstNodeWords=[{},{},{},{}] queueMeta=[{},{},{},{}] renderListCounter={} reason={}",
                    this.readbackAuditRuns,
                    this.topNodeCount,
                    firstTopNodeId,
                    firstNodeWord0,
                    firstNodeWord1,
                    firstNodeWord2,
                    firstNodeWord3,
                    queueDispatchX,
                    queueDispatchY,
                    queueDispatchZ,
                    queueInitialCount,
                    renderListCounter,
                    auditReason);
            VoxyForge.LOGGER.info(
                    "Original HOC top-node sample: sampled={} nearCamera={} cpuFrustumInside={} cpuFrustumOutside={} renderableMesh={} renderableInside={} renderableOutside={} emptyMesh={} nullMesh={} hasChildren={} nullChild={} emptyChildList={} requested={} nearestNodeId={} nearestLod={} nearestPos=[{},{},{}] nearestMesh={} nearestChild={} nearestFlags={} nearestDistanceBlocks={} nearestRenderableNodeId={} nearestRenderableLod={} nearestRenderablePos=[{},{},{}] nearestRenderableMesh={} nearestRenderableOutsideFrustum={} nearestRenderableDistanceBlocks={}",
                    sample.sampled,
                    sample.nearCamera,
                    sample.cpuFrustumInside,
                    sample.cpuFrustumOutside,
                    sample.renderableMesh,
                    sample.renderableInside,
                    sample.renderableOutside,
                    sample.emptyMesh,
                    sample.nullMesh,
                    sample.hasChildren,
                    sample.nullChild,
                    sample.emptyChildList,
                    sample.requested,
                    sample.nearestNodeId,
                    sample.nearestLod,
                    sample.nearestX,
                    sample.nearestY,
                    sample.nearestZ,
                    sample.nearestMesh,
                    sample.nearestChild,
                    sample.nearestFlags,
                    String.format(java.util.Locale.ROOT, "%.2f", Math.sqrt(Math.max(0.0D, sample.nearestDistanceSquared))),
                    sample.nearestRenderableNodeId,
                    sample.nearestRenderableLod,
                    sample.nearestRenderableX,
                    sample.nearestRenderableY,
                    sample.nearestRenderableZ,
                    sample.nearestRenderableMesh,
                    sample.nearestRenderableOutsideFrustum,
                    String.format(java.util.Locale.ROOT, "%.2f", Math.sqrt(Math.max(0.0D, sample.nearestRenderableDistanceSquared))));
            VoxyForge.LOGGER.info(
                    "Original HOC shader audit: valid={} withinDistance={} distanceCulled={} frustumCulled={} hizCulled={} visible={} enqueueChildren={} queuedRequests={} enqueueSelf={} enqueueSelfNonEmpty={} enqueueSelfEmpty={} hasMeshBranch={} noMeshBranch={} mainInvocations={} queueOutOfBounds={} queueMetaMax={}",
                    MemoryUtil.memGetInt(hocAuditPtr),
                    MemoryUtil.memGetInt(hocAuditPtr + 4L),
                    MemoryUtil.memGetInt(hocAuditPtr + 8L),
                    MemoryUtil.memGetInt(hocAuditPtr + 12L),
                    MemoryUtil.memGetInt(hocAuditPtr + 16L),
                    MemoryUtil.memGetInt(hocAuditPtr + 20L),
                    MemoryUtil.memGetInt(hocAuditPtr + 24L),
                    MemoryUtil.memGetInt(hocAuditPtr + 28L),
                    MemoryUtil.memGetInt(hocAuditPtr + 32L),
                    MemoryUtil.memGetInt(hocAuditPtr + 36L),
                    MemoryUtil.memGetInt(hocAuditPtr + 40L),
                    MemoryUtil.memGetInt(hocAuditPtr + 44L),
                    MemoryUtil.memGetInt(hocAuditPtr + 48L),
                    MemoryUtil.memGetInt(hocAuditPtr + 52L),
                    MemoryUtil.memGetInt(hocAuditPtr + 56L),
                    MemoryUtil.memGetInt(hocAuditPtr + 60L));
            if (MemoryUtil.memGetInt(hocAuditPtr + 64L) != 0) {
                VoxyForge.LOGGER.info(
                        "Original HOC first HiZ cull: nodeId={} lod={} minZ={} maxZ={} pointSample={} depthAgainst={} mip={} minBB=[{},{}] maxBB=[{},{}] texels=[{},{}]-[{},{}]",
                        MemoryUtil.memGetInt(hocAuditPtr + 68L),
                        MemoryUtil.memGetInt(hocAuditPtr + 72L),
                        Float.intBitsToFloat(MemoryUtil.memGetInt(hocAuditPtr + 76L)),
                        Float.intBitsToFloat(MemoryUtil.memGetInt(hocAuditPtr + 80L)),
                        Float.intBitsToFloat(MemoryUtil.memGetInt(hocAuditPtr + 84L)),
                        Float.intBitsToFloat(MemoryUtil.memGetInt(hocAuditPtr + 88L)),
                        MemoryUtil.memGetInt(hocAuditPtr + 92L),
                        Float.intBitsToFloat(MemoryUtil.memGetInt(hocAuditPtr + 96L)),
                        Float.intBitsToFloat(MemoryUtil.memGetInt(hocAuditPtr + 100L)),
                        Float.intBitsToFloat(MemoryUtil.memGetInt(hocAuditPtr + 104L)),
                        Float.intBitsToFloat(MemoryUtil.memGetInt(hocAuditPtr + 108L)),
                        MemoryUtil.memGetInt(hocAuditPtr + 112L),
                        MemoryUtil.memGetInt(hocAuditPtr + 116L),
                        MemoryUtil.memGetInt(hocAuditPtr + 120L),
                        MemoryUtil.memGetInt(hocAuditPtr + 124L));
            }
        }
    }

    private AuditNodeSample sampleTopNodes(MemoryStack stack, ForgeOriginalVoxyMdicViewport viewport) {
        int sampleCount = Math.min(this.topNodeCount, READBACK_AUDIT_SAMPLE_COUNT);
        AuditNodeSample sample = new AuditNodeSample(sampleCount);
        if (sampleCount <= 0 || viewport == null) {
            return sample;
        }

        long topIdsPtr = stack.nmalloc(sampleCount * Integer.BYTES);
        nglGetNamedBufferSubData(this.topNodeIds.id, 0L, (long) sampleCount * Integer.BYTES, topIdsPtr);

        double renderDistance = ForgeVoxyConfig.ORIGINAL_VOXY_SECTION_RENDER_DISTANCE.get() * 16.0D * 32.0D;
        double renderDistanceSquared = renderDistance * renderDistance;
        double cameraX = viewport.cameraX;
        double cameraZ = viewport.cameraZ;
        long nodePtr = stack.nmalloc(16);

        for (int i = 0; i < sampleCount; i++) {
            int nodeId = MemoryUtil.memGetInt(topIdsPtr + (long) i * Integer.BYTES);
            if (nodeId < 0 || nodeId >= this.nodeBuffer.size() / 16L) {
                continue;
            }
            nglGetNamedBufferSubData(this.nodeBuffer.id, nodeId * 16L, 16L, nodePtr);
            int word0 = MemoryUtil.memGetInt(nodePtr);
            int word1 = MemoryUtil.memGetInt(nodePtr + 4L);
            int word2 = MemoryUtil.memGetInt(nodePtr + 8L);
            int word3 = MemoryUtil.memGetInt(nodePtr + 12L);
            if (word0 == -1 && word1 == -1 && word2 == -1 && word3 == -1) {
                continue;
            }

            int lod = decodeLod(word0);
            int x = decodeX(word1);
            int y = decodeY(word0);
            int z = decodeZ(word0, word1);
            int mesh = word2 & 0xFFFFFF;
            int child = word3 & 0xFFFFFF;
            int flags = ((word2 >>> 24) & 0xFF) | (((word3 >>> 24) & 0xFF) << 8);
            boolean outsideFrustum = outsideFrustum(viewport, x, y, z, lod);
            if (outsideFrustum) {
                sample.cpuFrustumOutside++;
            } else {
                sample.cpuFrustumInside++;
            }

            if (mesh == NULL_MESH) {
                sample.nullMesh++;
            } else if (mesh == EMPTY_MESH) {
                sample.emptyMesh++;
            } else {
                sample.renderableMesh++;
                if (outsideFrustum) {
                    sample.renderableOutside++;
                } else {
                    sample.renderableInside++;
                }
            }
            if (child == NULL_NODE) {
                sample.nullChild++;
            } else {
                sample.hasChildren++;
                if (child == EMPTY_QUEUE_ID) {
                    sample.emptyChildList++;
                }
            }
            if ((flags & 1) != 0) {
                sample.requested++;
            }

            double distanceSquared = nearestDistanceSquared(cameraX, cameraZ, x, z, lod);
            if (distanceSquared <= renderDistanceSquared) {
                sample.nearCamera++;
            }
            if (distanceSquared < sample.nearestDistanceSquared) {
                sample.nearestDistanceSquared = distanceSquared;
                sample.nearestNodeId = nodeId;
                sample.nearestLod = lod;
                sample.nearestX = x;
                sample.nearestY = y;
                sample.nearestZ = z;
                sample.nearestMesh = mesh;
                sample.nearestChild = child;
                sample.nearestFlags = flags;
            }
            if (mesh != NULL_MESH && mesh != EMPTY_MESH && distanceSquared < sample.nearestRenderableDistanceSquared) {
                sample.nearestRenderableDistanceSquared = distanceSquared;
                sample.nearestRenderableNodeId = nodeId;
                sample.nearestRenderableLod = lod;
                sample.nearestRenderableX = x;
                sample.nearestRenderableY = y;
                sample.nearestRenderableZ = z;
                sample.nearestRenderableMesh = mesh;
                sample.nearestRenderableOutsideFrustum = outsideFrustum;
            }
        }
        return sample;
    }

    private static boolean outsideFrustum(ForgeOriginalVoxyMdicViewport viewport, int nodeX, int nodeY, int nodeZ, int lod) {
        float baseX = (float) ((((nodeX << lod) - viewport.section.x) << 5) - viewport.innerTranslation.x);
        float baseY = (float) ((((nodeY << lod) - viewport.section.y) << 5) - viewport.innerTranslation.y);
        float baseZ = (float) ((((nodeZ << lod) - viewport.section.z) << 5) - viewport.innerTranslation.z);
        float size = (float) (32 << lod);
        return !(testPlane(viewport.frustumPlanes[0], baseX, baseY, baseZ, size)
                && testPlane(viewport.frustumPlanes[1], baseX, baseY, baseZ, size)
                && testPlane(viewport.frustumPlanes[2], baseX, baseY, baseZ, size)
                && testPlane(viewport.frustumPlanes[3], baseX, baseY, baseZ, size)
                && testPlane(viewport.frustumPlanes[4], baseX, baseY, baseZ, size));
    }

    private static boolean testPlane(Vector4f plane, float baseX, float baseY, float baseZ, float size) {
        float x = baseX + (plane.x < 0.0F ? 0.0F : size);
        float y = baseY + (plane.y < 0.0F ? 0.0F : size);
        float z = baseZ + (plane.z < 0.0F ? 0.0F : size);
        return plane.x * x + plane.y * y + plane.z * z >= -plane.w;
    }

    private static int decodeLod(int word0) {
        return word0 >>> 28;
    }

    private static int decodeX(int word1) {
        return (word1 << 4) >> 8;
    }

    private static int decodeY(int word0) {
        return (word0 << 4) >> 24;
    }

    private static int decodeZ(int word0, int word1) {
        int z = (word0 & ((1 << 20) - 1)) << 4;
        z |= word1 >>> 28;
        return (z << 8) >> 8;
    }

    private static double nearestDistanceSquared(double cameraX, double cameraZ, int nodeX, int nodeZ, int lod) {
        int minX = (nodeX << lod) << 5;
        int minZ = (nodeZ << lod) << 5;
        int size = 1 << (lod + 5);
        double dx = nearestDelta(cameraX, minX, minX + size);
        double dz = nearestDelta(cameraZ, minZ, minZ + size);
        return dx * dx + dz * dz;
    }

    private static double nearestDelta(double point, int min, int max) {
        if (point < min) {
            return min - point;
        }
        if (point > max) {
            return point - max;
        }
        return 0.0D;
    }

    private static final class AuditNodeSample {
        final int sampled;
        int nearCamera;
        int cpuFrustumInside;
        int cpuFrustumOutside;
        int renderableMesh;
        int renderableInside;
        int renderableOutside;
        int emptyMesh;
        int nullMesh;
        int hasChildren;
        int nullChild;
        int emptyChildList;
        int requested;
        int nearestNodeId = -1;
        int nearestLod = -1;
        int nearestX;
        int nearestY;
        int nearestZ;
        int nearestMesh;
        int nearestChild;
        int nearestFlags;
        double nearestDistanceSquared = Double.POSITIVE_INFINITY;
        int nearestRenderableNodeId = -1;
        int nearestRenderableLod = -1;
        int nearestRenderableX;
        int nearestRenderableY;
        int nearestRenderableZ;
        int nearestRenderableMesh;
        boolean nearestRenderableOutsideFrustum;
        double nearestRenderableDistanceSquared = Double.POSITIVE_INFINITY;

        AuditNodeSample(int sampled) {
            this.sampled = sampled;
        }
    }

    void freeOnRenderThread() {
        if (this.traversalProgramId != 0) {
            glDeleteProgram(this.traversalProgramId);
            this.traversalProgramId = 0;
        }
        this.requestBuffer.free();
        this.nodeBuffer.free();
        this.uniformBuffer.free();
        this.queueMetaBuffer.free();
        this.topNodeIds.free();
        this.scratchQueueA.free();
        this.scratchQueueB.free();
        this.hocAuditBuffer.free();
        glDeleteSamplers(this.hizSampler);
        this.lastLifecycleEvent = "free-on-render-thread";
    }

    ForgeOriginalVoxyHierarchicalOcclusionTraverserStats createStatusSnapshot() {
        return new ForgeOriginalVoxyHierarchicalOcclusionTraverserStats(
                this.ready(),
                this.traversalProgramId != 0,
                this.nodeBuffer.id != 0,
                this.requestBuffer.id != 0,
                this.topNodeIds.id != 0,
                this.queueMetaBuffer.id != 0,
                this.scratchQueueA.id != 0 && this.scratchQueueB.id != 0 && this.hocAuditBuffer.id != 0,
                this.hizSampler != 0,
                this.topNodeCount,
                this.traversalRunCount,
                this.requestBatchForwardCount,
                this.lastLifecycleEvent,
                this.lastFailureReason
        );
    }

    //Render-list content audit (XX.4): the render list is what the cull raster and cmdgen operate
    // on, rebuilt by the traversal each frame. With the node/metadata layers verified clean, this
    // discriminates the remaining suspects: duplicate/stale/out-of-range entries → traversal or
    // queue layer emits garbage; a clean list → the collapse is in the raster visibility marking.
    private void auditRenderListContent(ForgeOriginalVoxyMdicViewport viewport, int renderListCounter) {
        if (viewport == null || viewport.renderListBufferId() == 0 || renderListCounter <= 0) {
            return;
        }
        int maxEntries = (int) (viewport.renderListBufferSize() / Integer.BYTES) - 1;
        int entryCount = Math.min(renderListCounter, maxEntries);
        int sectionCount = this.nodeSync.auditGeometrySectionCount();
        IntOpenHashSet usedMeshIds = this.nodeSync.auditUsedMeshIds();
        long entriesPtr = MemoryUtil.nmemAlloc(entryCount * (long) Integer.BYTES);
        long visibilityPtr = MemoryUtil.nmemAlloc(4L);
        try {
            nglGetNamedBufferSubData(
                    viewport.renderListBufferId(), Integer.BYTES, entryCount * (long) Integer.BYTES, entriesPtr);
            IntOpenHashSet seen = new IntOpenHashSet(entryCount);
            Int2IntOpenHashMap visibilityHistogram = new Int2IntOpenHashMap();
            int duplicates = 0;
            int outOfRange = 0;
            int staleEntries = 0;
            int highBitSet = 0;
            StringBuilder badSamples = new StringBuilder();
            for (int i = 0; i < entryCount; i++) {
                int sectionId = MemoryUtil.memGetInt(entriesPtr + i * 4L);
                boolean bad = false;
                if (!seen.add(sectionId)) {
                    duplicates++;
                    bad = true;
                }
                if (sectionId < 0 || sectionId >= sectionCount) {
                    outOfRange++;
                    bad = true;
                } else {
                    if (!usedMeshIds.contains(sectionId)) {
                        staleEntries++;
                        bad = true;
                    }
                    nglGetNamedBufferSubData(viewport.visibilityBuffer.id, sectionId * 4L, 4L, visibilityPtr);
                    int visibility = MemoryUtil.memGetInt(visibilityPtr);
                    visibilityHistogram.addTo(visibility & 0x7fffffff, 1);
                    if ((visibility & 0x80000000) != 0) {
                        highBitSet++;
                    }
                }
                if (bad && duplicates + outOfRange + staleEntries <= 8) {
                    badSamples.append(" [i=").append(i).append(" section=").append(sectionId).append(']');
                }
            }
            int modalVisibility = -1;
            int modalCount = 0;
            int distinctVisibilities = visibilityHistogram.size();
            for (var entry : visibilityHistogram.int2IntEntrySet()) {
                if (entry.getIntValue() > modalCount) {
                    modalCount = entry.getIntValue();
                    modalVisibility = entry.getIntKey();
                }
            }
            VoxyForge.LOGGER.info(
                    "Original HOC render-list content audit: entries={} treeMeshes={} geometrySectionCount={} duplicates={} outOfRange={} staleEntries={} visMarkedModal={}@{} distinctVis={} visHighBitSet={} badSamples=[{}]",
                    entryCount,
                    usedMeshIds.size(),
                    sectionCount,
                    duplicates,
                    outOfRange,
                    staleEntries,
                    modalCount,
                    modalVisibility,
                    distinctVisibilities,
                    highBitSet,
                    badSamples.toString().trim());
        } finally {
            MemoryUtil.nmemFree(entriesPtr);
            MemoryUtil.nmemFree(visibilityPtr);
        }
    }

    private void addTopLevelNode(int id) {
        int arrayIndex = this.topNodeCount++;
        if (this.topNodeCount > this.topNodeIds.size() / Integer.BYTES) {
            throw new IllegalStateException("Original HOC top-level node count exceeded capacity");
        }
        MemoryUtil.memPutInt(SCRATCH, id);
        nglClearNamedBufferSubData(this.topNodeIds.id, GL_R32UI, arrayIndex * 4L, 4L, GL_RED_INTEGER, GL_UNSIGNED_INT, SCRATCH);
        if (this.topNode2idxMapping.put(id, arrayIndex) != -1) {
            throw new IllegalStateException("Duplicate top-level node id " + id);
        }
        this.idx2topNodeMapping[arrayIndex] = id;
    }

    private void removeTopLevelNode(int id) {
        int index = this.topNode2idxMapping.remove(id);
        this.topNodeCount--;
        if (index == -1) {
            throw new IllegalStateException("Missing top-level node id " + id);
        }
        if (index == this.topNodeCount) {
            return;
        }
        int endTopLevelNodeId = this.idx2topNodeMapping[this.topNodeCount];
        this.idx2topNodeMapping[index] = endTopLevelNodeId;
        if (this.topNode2idxMapping.put(endTopLevelNodeId, index) == -1) {
            throw new IllegalStateException("Missing moved top-level node id " + endTopLevelNodeId);
        }
        MemoryUtil.memPutInt(SCRATCH, endTopLevelNodeId);
        nglClearNamedBufferSubData(this.topNodeIds.id, GL_R32UI, index * 4L, 4L, GL_RED_INTEGER, GL_UNSIGNED_INT, SCRATCH);
    }

    private void uploadUniform(ForgeOriginalVoxyMdicViewport viewport) {
        long ptr = ForgeOriginalVoxyUploadStream.instance().upload(this.uniformBuffer.id, 0L, 1024L);
        viewport.MVP.getToAddress(ptr);
        ptr += 4L * 4L * 4L;
        viewport.section.getToAddress(ptr);
        ptr += 4L * 3L;
        MemoryUtil.memPutInt(ptr, viewport.packedHizLevels());
        ptr += 4L;
        viewport.innerTranslation.getToAddress(ptr);
        ptr += 4L * 3L;

        double subDivisionSize = ForgeVoxyConfig.ORIGINAL_VOXY_SUBDIVISION_SIZE.get();
        MemoryUtil.memPutFloat(ptr, (float) ((subDivisionSize * subDivisionSize) / (viewport.width * (double) viewport.height)));
        ptr += 4L;

        for (int i = 0; i < 6; i++) {
            viewport.frustumPlanes[i].getToAddress(ptr);
            ptr += 4L * 4L;
        }
        MemoryUtil.memPutInt(ptr, (int) (viewport.renderListBufferSize() / Integer.BYTES - 1));
        ptr += 4L;
        MemoryUtil.memPutInt(ptr, this.nodeCleaner.visibilityId());
        ptr += 4L;

        final double targetCount = 4000.0D;
        double fillness = Math.max(0.0D, (targetCount - this.meshGen.taskQueueCount()) / targetCount);
        fillness *= fillness;
        int requestSize = (int) Math.ceil(fillness * MAX_REQUEST_QUEUE_SIZE);
        MemoryUtil.memPutInt(ptr, Math.max(0, Math.min(MAX_REQUEST_QUEUE_SIZE, requestSize)));
        ptr += 4L;

        double renderDistance = ForgeVoxyConfig.ORIGINAL_VOXY_SECTION_RENDER_DISTANCE.get();
        MemoryUtil.memPutFloat(ptr, (float) Math.pow(renderDistance * 16.0D * 32.0D, 2.0D));
        ForgeOriginalVoxyUploadStream.instance().commit();
    }

    private void bindings(ForgeOriginalVoxyMdicViewport viewport) {
        glBindBuffer(GL_DISPATCH_INDIRECT_BUFFER, this.queueMetaBuffer.id);
        glBindTextureUnit(0, viewport.hizTextureId());
        glBindSampler(0, this.hizSampler);
        glBindBufferBase(GL_UNIFORM_BUFFER, SCENE_UNIFORM_BINDING, this.uniformBuffer.id);
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, REQUEST_QUEUE_BINDING, this.requestBuffer.id);
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, RENDER_QUEUE_BINDING, viewport.renderListBufferId());
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, NODE_DATA_BINDING, this.nodeBuffer.id);
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, NODE_QUEUE_META_BINDING, this.queueMetaBuffer.id);
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, RENDER_TRACKER_BINDING, this.nodeCleaner.visibilityBufferId());
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, HOC_AUDIT_BINDING, this.hocAuditBuffer.id);
    }

    private void traverseInternal() {
        glPixelStorei(GL_UNPACK_ROW_LENGTH, 0);
        glPixelStorei(GL_UNPACK_SKIP_PIXELS, 0);
        glPixelStorei(GL_UNPACK_SKIP_ROWS, 0);
        glPixelStorei(GL_UNPACK_IMAGE_HEIGHT, 0);
        glPixelStorei(GL_UNPACK_SKIP_IMAGES, 0);

        int firstDispatchSize = (this.topNodeCount + (1 << LOCAL_WORK_SIZE_BITS) - 1) >> LOCAL_WORK_SIZE_BITS;
        long ptr = ForgeOriginalVoxyUploadStream.instance().upload(this.queueMetaBuffer.id, 0L, 16L * MAX_ITERATIONS);
        MemoryUtil.memPutInt(ptr, firstDispatchSize);
        MemoryUtil.memPutInt(ptr + 4L, 1);
        MemoryUtil.memPutInt(ptr + 8L, 1);
        MemoryUtil.memPutInt(ptr + 12L, this.topNodeCount);
        for (int i = 1; i < MAX_ITERATIONS; i++) {
            long base = ptr + i * 16L;
            MemoryUtil.memPutInt(base, 0);
            MemoryUtil.memPutInt(base + 4L, 1);
            MemoryUtil.memPutInt(base + 8L, 1);
            MemoryUtil.memPutInt(base + 12L, 0);
        }
        ForgeOriginalVoxyUploadStream.instance().commit();

        glUniform1ui(NODE_QUEUE_INDEX_BINDING, 0);
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, NODE_QUEUE_SOURCE_BINDING, this.topNodeIds.id);
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, NODE_QUEUE_SINK_BINDING, this.scratchQueueB.id);
        glMemoryBarrier(GL_SHADER_STORAGE_BARRIER_BIT | GL_COMMAND_BARRIER_BIT | GL_BUFFER_UPDATE_BARRIER_BIT);
        if (firstDispatchSize != 0) {
            glDispatchCompute(firstDispatchSize, 1, 1);
        }
        glMemoryBarrier(GL_SHADER_STORAGE_BARRIER_BIT | GL_COMMAND_BARRIER_BIT);

        for (int iteration = 1; iteration < MAX_ITERATIONS; iteration++) {
            glUniform1ui(NODE_QUEUE_INDEX_BINDING, iteration);
            glBindBufferBase(
                    GL_SHADER_STORAGE_BUFFER,
                    NODE_QUEUE_SOURCE_BINDING,
                    (iteration & 1) == 0 ? this.scratchQueueA.id : this.scratchQueueB.id);
            glBindBufferBase(
                    GL_SHADER_STORAGE_BUFFER,
                    NODE_QUEUE_SINK_BINDING,
                    (iteration & 1) == 0 ? this.scratchQueueB.id : this.scratchQueueA.id);
            glMemoryBarrier(GL_SHADER_STORAGE_BARRIER_BIT | GL_COMMAND_BARRIER_BIT);
            glDispatchComputeIndirect(iteration * 4L * 4L);
        }
        glMemoryBarrier(GL_SHADER_STORAGE_BARRIER_BIT | GL_COMMAND_BARRIER_BIT);
    }

    private static String applyOriginalPrintfProcessor(String source) {
        if (ENABLE_PRINTF_DEBUGGING) {
            throw new IllegalStateException("Original HOC shader printf debugging processor is not ported to Forge");
        }
        return source.replace("printf", "//printf");
    }

    private void downloadResetRequestQueue() {
        glMemoryBarrier(GL_SHADER_STORAGE_BARRIER_BIT);
        ForgeOriginalVoxyDownloadStream.instance().download(
                this.requestBuffer.id,
                this.requestBuffer.size(),
                0L,
                this.requestBuffer.size(),
                (ForgeOriginalVoxyDownloadStream.DownloadResultConsumer) this::forwardDownloadResult);
        nglClearNamedBufferSubData(this.requestBuffer.id, GL_R32UI, 0L, 4L, GL_RED_INTEGER, GL_UNSIGNED_INT, 0L);
    }

    private void forwardDownloadResult(long ptr, long size) {
        int count = MemoryUtil.memGetInt(ptr);
        ptr += 8L;
        if (count < 0 || count > 50_000) {
            this.recordFailure("original-hoc-request-count-extreme-" + count);
            return;
        }
        int maxCount = (int) (this.requestBuffer.size() >> 3) - 1;
        if (count > maxCount) {
            count = maxCount;
        }
        if (count == 0) {
            return;
        }
        MemoryBuffer buffer = new MemoryBuffer(count * 8L + 8L).cpyFrom(ptr - 8L);
        MemoryUtil.memPutInt(buffer.address, count);
        this.nodeSync.submitRequestBatch(buffer);
        this.requestBatchForwardCount++;
    }

    private static String withDefines(String source, Object... defines) {
        StringBuilder builder = new StringBuilder();
        int split = source.indexOf('\n');
        builder.append(source, 0, split + 1);
        for (int i = 0; i < defines.length; i += 2) {
            builder.append("#define ").append(defines[i]).append(' ').append(defines[i + 1]).append('\n');
        }
        builder.append(source.substring(split + 1));
        return builder.toString();
    }

    private static int compileComputeProgram(String source) {
        int shader = glCreateShader(GL_COMPUTE_SHADER);
        long ptr = MemoryUtil.memAddress(MemoryUtil.memUTF8(source, true));
        try (MemoryStack stack = MemoryStack.stackPush()) {
            nglShaderSource(shader, 1, stack.pointers(ptr).address0(), 0L);
        } finally {
            MemoryUtil.nmemFree(ptr);
        }
        glCompileShader(shader);
        if (glGetShaderi(shader, GL_COMPILE_STATUS) == GL_FALSE) {
            String log = glGetShaderInfoLog(shader);
            glDeleteShader(shader);
            throw new IllegalStateException("Original HOC shader compile failed: " + log);
        }
        int program = glCreateProgram();
        glAttachShader(program, shader);
        glLinkProgram(program);
        glDeleteShader(shader);
        if (glGetProgrami(program, GL_LINK_STATUS) != GL_TRUE) {
            String log = glGetProgramInfoLog(program);
            glDeleteProgram(program);
            throw new IllegalStateException("Original HOC shader link failed: " + log);
        }
        return program;
    }

    private void recordFailure(String reason) {
        this.lastLifecycleEvent = "failure";
        this.lastFailureReason = reason == null || reason.isBlank() ? "unspecified" : reason.replace(' ', '-');
        VoxyForge.LOGGER.error("Original hierarchical occlusion traverser failure: {}", this.lastFailureReason);
    }
}
