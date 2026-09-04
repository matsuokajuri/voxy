package me.cortex.voxy.forge;

import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import me.cortex.voxy.config.ForgeVoxyConfig;
import me.cortex.voxy.common.util.MemoryBuffer;
import me.cortex.voxy.common.util.TrackedObject;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import java.util.List;

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
import static org.lwjgl.opengl.GL45C.glCopyNamedBufferSubData;
import static org.lwjgl.opengl.GL45C.glDeleteProgram;
import static org.lwjgl.opengl.GL45C.glDeleteShader;
import static org.lwjgl.opengl.GL45C.glGetProgramInfoLog;
import static org.lwjgl.opengl.GL45C.glGetProgrami;
import static org.lwjgl.opengl.GL45C.glGetShaderInfoLog;
import static org.lwjgl.opengl.GL45C.glGetShaderi;
import static org.lwjgl.opengl.GL45C.glLinkProgram;
import static org.lwjgl.opengl.GL45C.glPixelStorei;
import static org.lwjgl.opengl.GL45C.nglClearNamedBufferSubData;
import static org.lwjgl.opengl.GL45C.nglShaderSource;
import static org.lwjgl.opengl.GL45C.glCompileShader;
import static org.lwjgl.opengl.GL45C.glUseProgram;
import static org.lwjgl.opengl.GL20C.glAttachShader;
import static org.lwjgl.opengl.GL20C.GL_COMPILE_STATUS;
import static org.lwjgl.opengl.GL20C.GL_LINK_STATUS;

final class HierarchicalOcclusionTraverser extends TrackedObject {
    private static final boolean HIERARCHICAL_SHADER_DEBUG =
            System.getProperty("voxy.hierarchicalShaderDebug", "false").equals("true");
    static final int MAX_REQUEST_QUEUE_SIZE = GpuBufferLayout.HOC_REQUEST_CAPACITY;
    static final int MAX_QUEUE_SIZE = GpuBufferLayout.HOC_QUEUE_CAPACITY;

    private static final int MAX_ITERATIONS = GpuBufferLayout.MAX_ITERATIONS;
    private static final int LOCAL_WORK_SIZE_BITS = 5;
    static final int OVERFLOW_REQUEST = 1;
    static final int OVERFLOW_CHILD_QUEUE = 1 << 1;
    static final int OVERFLOW_LAST_ITERATION = 1 << 2;
    static final int OVERFLOW_RENDER_LIST = 1 << 3;
    static final int OVERFLOW_SOURCE = 1 << 4;
    static final int OVERFLOW_INVALID_NODE = 1 << 5;
    private static final int KNOWN_OVERFLOW_FLAGS = (1 << 6) - 1;
    private static final long OVERFLOW_LOG_INTERVAL_NS = 5_000_000_000L;
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
    private static final int STATISTICS_BUFFER_BINDING = bindingCounter++;
    private static final long SCRATCH = MemoryUtil.nmemAlloc(32);

    private final AsyncNodeManager nodeSync;
    private final NodeCleaner nodeCleaner;
    private final RenderGenerationService meshGen;
    private final GlBuffer requestBuffer = new GlBuffer(GpuBufferLayout.HOC_REQUEST_BUFFER_BYTES).zero();
    private final GlBuffer nodeBuffer;
    private final GlBuffer uniformBuffer = new GlBuffer(1024).zero();
    private final GlBuffer topNodeIds = new GlBuffer(GpuBufferLayout.HOC_QUEUE_BUFFER_BYTES).zero();
    private final GlBuffer queueMetaBuffer = new GlBuffer(GpuBufferLayout.HOC_METADATA_BUFFER_BYTES).zero();
    private final GlBuffer queueDispatchBuffer = new GlBuffer(GpuBufferLayout.DISPATCH_BYTES).zero();
    private final GlBuffer scratchQueueA = new GlBuffer(GpuBufferLayout.HOC_QUEUE_BUFFER_BYTES).zero();
    private final GlBuffer scratchQueueB = new GlBuffer(GpuBufferLayout.HOC_QUEUE_BUFFER_BYTES).zero();
    private final GlBuffer statisticsBuffer = new GlBuffer(1024).zero();
    private final Int2IntOpenHashMap topNode2idxMapping = new Int2IntOpenHashMap();
    private final int[] idx2topNodeMapping = new int[MAX_QUEUE_SIZE];
    private final int hizSampler = glGenSamplers();
    private int traversalProgramId;
    private ForgeOriginalVoxyRenderPipeline taaPipeline;
    private boolean taaInjected;
    private int topNodeCount;
    private int pendingRecoveryFlags;
    private int observedOverflowFlags;
    private long overflowFrames;
    private long requestDeferredFrames;
    private long recoveryTraversals;
    private long lastOverflowLogNs;

    HierarchicalOcclusionTraverser(
            AsyncNodeManager nodeSync,
            NodeCleaner nodeCleaner,
            RenderGenerationService meshGen) {
        this.nodeSync = nodeSync;
        this.nodeCleaner = nodeCleaner;
        this.meshGen = meshGen;
        this.nodeBuffer = new GlBuffer((long) nodeSync.maxNodeCount * 16L).fill(-1);
        this.nodeSync.setTLNAddRemoveCallbacks(this::addTLN, this::remTLN);
        this.topNode2idxMapping.defaultReturnValue(-1);

        glSamplerParameteri(this.hizSampler, GL_TEXTURE_MIN_FILTER, GL_NEAREST_MIPMAP_NEAREST);
        glSamplerParameteri(this.hizSampler, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glSamplerParameteri(this.hizSampler, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        glSamplerParameteri(this.hizSampler, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
    }

    String lateStageCompile(RenderProperties properties, ForgeOriginalVoxyRenderPipeline pipeline) {
        try {
            if (this.traversalProgramId != 0) {
                glDeleteProgram(this.traversalProgramId);
                this.traversalProgramId = 0;
            }
            this.taaPipeline = null;
            this.taaInjected = false;
            String taa = pipeline == null ? null : pipeline.taaFunction("getTAA");
            if (taa != null) {
                this.taaPipeline = pipeline;
                this.taaInjected = true;
            }
            this.traversalProgramId = compileComputeProgram(buildTraversalSource(properties, taa));
            return "none";
        } catch (RuntimeException e) {
            return this.recordFailure("original-hoc-" + e.getClass().getSimpleName() + ":" + e.getMessage());
        }
    }

    boolean ready() {
        return this.traversalProgramId != 0
                && this.nodeBuffer.id != 0
                && this.requestBuffer.id != 0
                && this.topNodeIds.id != 0
                && this.queueMetaBuffer.id != 0
                && this.queueDispatchBuffer.id != 0
                && this.scratchQueueA.id != 0
                && this.scratchQueueB.id != 0;
    }

    GlBuffer getNodeBuffer() {
        return this.nodeBuffer;
    }

    void addDebug(List<String> debug) {
        if (this.topNodeCount > this.idx2topNodeMapping.length / 2) {
            debug.add("TLN#: " + this.topNodeCount);
        }
        if (this.overflowFrames != 0L || this.requestDeferredFrames != 0L) {
            debug.add("HOC capacity: flags=0x" + Integer.toHexString(this.observedOverflowFlags)
                    + " overflowFrames=" + this.overflowFrames + " requestDeferredFrames=" + this.requestDeferredFrames
                    + " retries=" + this.recoveryTraversals);
        }
    }

    void doTraversal(MDICViewport viewport) {
        if (!this.ready()) {
            this.recordFailure("hierarchical-occlusion-traverser-not-ready");
            return;
        }
        if (viewport.hizTextureId() == 0) {
            this.recordFailure("hierarchical-occlusion-hiz-not-built");
            return;
        }
        if (this.pendingRecoveryFlags != 0) {
            // No failed reservation survives a frame: metadata/render-list are rebuilt below,
            // the request header was reset after its asynchronous copy, and rejected requests
            // were never marked requested. Revisit the same original top-node hierarchy.
            this.pendingRecoveryFlags = 0;
            this.recoveryTraversals++;
        }
        this.uploadUniform(viewport);
        glUseProgram(this.traversalProgramId);
        if (this.taaInjected && this.taaPipeline != null) {
            this.taaPipeline.bindUniforms();
        }
        this.bindings(viewport);
        if (RenderStatistics.enabled) {
            this.statisticsBuffer.zero();
        }
        nglClearNamedBufferSubData(viewport.renderListBufferId(), GL_R32UI, 0L, Integer.BYTES, GL_RED_INTEGER, GL_UNSIGNED_INT, 0L);
        this.traverseInternal();
        this.downloadResetRequestQueue();
        if (RenderStatistics.enabled) {
            DownloadStream.instance().download(
                    this.statisticsBuffer.id,
                    this.statisticsBuffer.size(),
                    down -> {
                        for (int i = 0; i < MAX_ITERATIONS; i++) {
                            RenderStatistics.hierarchicalTraversalCounts[i] =
                                    MemoryUtil.memGetInt(down.address + i * 4L);
                        }
                        for (int i = 0; i < MAX_ITERATIONS; i++) {
                            RenderStatistics.hierarchicalRenderSections[i] =
                                    MemoryUtil.memGetInt(down.address + MAX_ITERATIONS * 4L + i * 4L);
                        }
                    });
        }
        glBindSampler(0, 0);
        ForgeOriginalVoxyTextureBindings.bind2D(0, 0);
        glUseProgram(0);
    }

    @Override
    public void free() {
        this.free0();
        if (this.traversalProgramId != 0) {
            glDeleteProgram(this.traversalProgramId);
            this.traversalProgramId = 0;
        }
        this.requestBuffer.free();
        this.nodeBuffer.free();
        this.uniformBuffer.free();
        this.queueMetaBuffer.free();
        this.queueDispatchBuffer.free();
        this.topNodeIds.free();
        this.scratchQueueA.free();
        this.scratchQueueB.free();
        this.statisticsBuffer.free();
        glDeleteSamplers(this.hizSampler);
    }

    private void addTLN(int id) {
        if (id < 0 || id >= this.nodeSync.maxNodeCount) {
            throw new IllegalArgumentException("Invalid top-level node id " + id);
        }
        if (this.topNodeCount >= this.topNodeIds.size() / Integer.BYTES) {
            throw new IllegalStateException("Original HOC top-level node count exceeded capacity");
        }
        if (this.topNode2idxMapping.containsKey(id)) {
            throw new IllegalStateException("Duplicate top-level node id " + id);
        }
        int arrayIndex = this.topNodeCount;
        MemoryUtil.memPutInt(SCRATCH, id);
        nglClearNamedBufferSubData(this.topNodeIds.id, GL_R32UI, arrayIndex * 4L, 4L, GL_RED_INTEGER, GL_UNSIGNED_INT, SCRATCH);
        this.topNode2idxMapping.put(id, arrayIndex);
        this.idx2topNodeMapping[arrayIndex] = id;
        this.topNodeCount++;
    }

    static String buildTraversalSource(RenderProperties properties, String taa) {
        String source = ShaderLoader.parse("voxy:lod/hierarchical/traversal_dev.comp");
        if (taa != null) {
            source += "\n\n\n\n" + taa;
            source = withDefines(source, "TAA", 1);
        }
        source = properties.injectDefines(source);
        source = applyOriginalPrintfProcessor(source);
        if (HIERARCHICAL_SHADER_DEBUG) {
            source = withDefines(source, "DEBUG", 1);
        }
        if (RenderStatistics.enabled) {
            source = withDefines(source,
                    "HAS_STATISTICS", 1,
                    "STATISTICS_BUFFER_BINDING", STATISTICS_BUFFER_BINDING);
        }
        return withDefines(source,
                "MAX_ITERATIONS", MAX_ITERATIONS,
                "MAX_LOD", GpuBufferLayout.MAX_LOD,
                "LOCAL_SIZE_BITS", LOCAL_WORK_SIZE_BITS,
                "MAX_REQUEST_QUEUE_SIZE", MAX_REQUEST_QUEUE_SIZE,
                "HOC_OVERFLOW_REQUEST", OVERFLOW_REQUEST,
                "HOC_OVERFLOW_CHILD_QUEUE", OVERFLOW_CHILD_QUEUE,
                "HOC_OVERFLOW_LAST_ITERATION", OVERFLOW_LAST_ITERATION,
                "HOC_OVERFLOW_RENDER_LIST", OVERFLOW_RENDER_LIST,
                "HOC_OVERFLOW_SOURCE", OVERFLOW_SOURCE,
                "HOC_OVERFLOW_INVALID_NODE", OVERFLOW_INVALID_NODE,
                "HIZ_BINDING", 0,
                "SCENE_UNIFORM_BINDING", SCENE_UNIFORM_BINDING,
                "REQUEST_QUEUE_BINDING", REQUEST_QUEUE_BINDING,
                "RENDER_QUEUE_BINDING", RENDER_QUEUE_BINDING,
                "NODE_DATA_BINDING", NODE_DATA_BINDING,
                "NODE_QUEUE_INDEX_BINDING", NODE_QUEUE_INDEX_BINDING,
                "NODE_QUEUE_META_BINDING", NODE_QUEUE_META_BINDING,
                "NODE_QUEUE_SOURCE_BINDING", NODE_QUEUE_SOURCE_BINDING,
                "NODE_QUEUE_SINK_BINDING", NODE_QUEUE_SINK_BINDING,
                "RENDER_TRACKER_BINDING", RENDER_TRACKER_BINDING);
    }

    private void remTLN(int id) {
        int index = this.topNode2idxMapping.get(id);
        if (index == -1) {
            throw new IllegalStateException("Missing top-level node id " + id);
        }
        this.topNode2idxMapping.remove(id);
        this.topNodeCount--;
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

    private void uploadUniform(MDICViewport viewport) {
        long ptr = UploadStream.instance().upload(this.uniformBuffer.id, 0L, 1024L);
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
        MemoryUtil.memPutInt(ptr, (int) Math.min(GpuBufferLayout.HOC_RENDER_LIST_CAPACITY,
                viewport.renderListBufferSize() / Integer.BYTES - 1L));
        ptr += 4L;
        MemoryUtil.memPutInt(ptr, this.nodeCleaner.visibilityId());
        ptr += 4L;

        final double targetCount = 4000.0D;
        double fillness = Math.max(0.0D, (targetCount - this.meshGen.getTaskCount()) / targetCount);
        fillness *= fillness;
        int requestSize = (int) Math.ceil(fillness * MAX_REQUEST_QUEUE_SIZE);
        MemoryUtil.memPutInt(ptr, Math.max(0, Math.min(MAX_REQUEST_QUEUE_SIZE, requestSize)));
        ptr += 4L;

        double renderDistance = ForgeVoxyConfig.ORIGINAL_VOXY_SECTION_RENDER_DISTANCE.get();
        MemoryUtil.memPutFloat(ptr, (float) Math.pow(renderDistance * 16.0D * 32.0D, 2.0D));
        UploadStream.instance().commit();
    }

    private void bindings(MDICViewport viewport) {
        glBindBuffer(GL_DISPATCH_INDIRECT_BUFFER, this.queueDispatchBuffer.id);
        glBindTextureUnit(0, viewport.hizTextureId());
        glBindSampler(0, this.hizSampler);
        glBindBufferBase(GL_UNIFORM_BUFFER, SCENE_UNIFORM_BINDING, this.uniformBuffer.id);
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, REQUEST_QUEUE_BINDING, this.requestBuffer.id);
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, RENDER_QUEUE_BINDING, viewport.renderListBufferId());
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, NODE_DATA_BINDING, this.nodeBuffer.id);
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, NODE_QUEUE_META_BINDING, this.queueMetaBuffer.id);
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, RENDER_TRACKER_BINDING, this.nodeCleaner.visibilityBufferId());
        if (RenderStatistics.enabled) {
            glBindBufferBase(GL_SHADER_STORAGE_BUFFER, STATISTICS_BUFFER_BINDING, this.statisticsBuffer.id);
        }
        PrintfDebugUtil.bind();
    }

    private void traverseInternal() {
        glPixelStorei(GL_UNPACK_ROW_LENGTH, 0);
        glPixelStorei(GL_UNPACK_SKIP_PIXELS, 0);
        glPixelStorei(GL_UNPACK_SKIP_ROWS, 0);
        glPixelStorei(GL_UNPACK_IMAGE_HEIGHT, 0);
        glPixelStorei(GL_UNPACK_SKIP_IMAGES, 0);

        int firstDispatchSize = dispatchGroupsForCount(this.topNodeCount);
        long ptr = UploadStream.instance().upload(this.queueMetaBuffer.id, 0L, GpuBufferLayout.HOC_METADATA_BUFFER_BYTES);
        MemoryUtil.memPutInt(ptr, firstDispatchSize);
        MemoryUtil.memPutInt(ptr + 4L, 1);
        MemoryUtil.memPutInt(ptr + 8L, 1);
        MemoryUtil.memPutInt(ptr + 12L, this.topNodeCount);
        for (int i = 1; i < MAX_ITERATIONS; i++) {
            long base = ptr + i * (long) GpuBufferLayout.HOC_QUEUE_META_BYTES;
            MemoryUtil.memPutInt(base, 0);
            MemoryUtil.memPutInt(base + 4L, 1);
            MemoryUtil.memPutInt(base + 8L, 1);
            MemoryUtil.memPutInt(base + 12L, 0);
        }
        UploadStream.instance().commit();

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
            dispatchQueuedIteration(this.queueMetaBuffer.id, this.queueDispatchBuffer.id, iteration);
        }
        glMemoryBarrier(GL_SHADER_STORAGE_BARRIER_BIT | GL_COMMAND_BARRIER_BIT);
    }

    private static String applyOriginalPrintfProcessor(String source) {
        return PrintfDebugUtil.processShader(source);
    }

    static int dispatchGroupsForCount(int count) {
        if (count < 0 || count > MAX_QUEUE_SIZE) {
            throw new IllegalArgumentException("HOC queue count outside capacity: " + count);
        }
        return (count + (1 << LOCAL_WORK_SIZE_BITS) - 1) >> LOCAL_WORK_SIZE_BITS;
    }

    static long queueDispatchArgumentOffset(int iteration) {
        if (iteration < 0 || iteration >= MAX_ITERATIONS) {
            throw new IllegalArgumentException("HOC iteration outside metadata: " + iteration);
        }
        return iteration * (long) GpuBufferLayout.HOC_QUEUE_META_BYTES;
    }

    static void dispatchQueuedIteration(int metadataBufferId, int snapshotBufferId, int iteration) {
        // Traversal writes later counters in the same 80-byte metadata object. Keep the
        // indirect command processor on a separate, shader-read-only 12-byte snapshot:
        // aliasing writable SSBO metadata and indirect arguments loses empty->nonempty
        // dispatches on the qualified NVIDIA driver even with storage/command barriers.
        glMemoryBarrier(GL_SHADER_STORAGE_BARRIER_BIT | GL_BUFFER_UPDATE_BARRIER_BIT | GL_COMMAND_BARRIER_BIT);
        glCopyNamedBufferSubData(metadataBufferId, snapshotBufferId,
                queueDispatchArgumentOffset(iteration), 0L, GpuBufferLayout.DISPATCH_BYTES);
        glMemoryBarrier(GL_BUFFER_UPDATE_BARRIER_BIT | GL_COMMAND_BARRIER_BIT);
        glBindBuffer(GL_DISPATCH_INDIRECT_BUFFER, snapshotBufferId);
        glDispatchComputeIndirect(0L);
    }

    private void downloadResetRequestQueue() {
        glMemoryBarrier(GL_SHADER_STORAGE_BARRIER_BIT);
        DownloadStream.instance().download(
                this.requestBuffer.id,
                this.requestBuffer.size(),
                0L,
                this.requestBuffer.size(),
                (DownloadStream.DownloadResultConsumer) this::forwardDownloadResult);
        // Header x is the successful request count; y is a per-traversal overflow bitmask.
        // The existing asynchronous copy includes both before either is reset for retry.
        nglClearNamedBufferSubData(this.requestBuffer.id, GL_R32UI, 0L,
                GpuBufferLayout.HOC_REQUEST_HEADER_BYTES, GL_RED_INTEGER, GL_UNSIGNED_INT, 0L);
    }

    private void forwardDownloadResult(long ptr, long size) {
        if (size < GpuBufferLayout.HOC_REQUEST_HEADER_BYTES) {
            this.recordFailure("original-hoc-request-header-truncated-" + size);
            return;
        }
        int rawCount = MemoryUtil.memGetInt(ptr);
        this.observeOverflow(MemoryUtil.memGetInt(ptr + Integer.BYTES), rawCount);
        int count = readableRequestCount(rawCount, size);
        if (count < 0) {
            this.recordFailure("original-hoc-request-count-extreme-" + rawCount);
            return;
        }
        if (count != rawCount) {
            this.recordFailure("original-hoc-request-count-clamped-" + rawCount + "-to-" + count);
        }
        if (count == 0) {
            return;
        }
        MemoryBuffer buffer = new MemoryBuffer(count * (long) GpuBufferLayout.HOC_REQUEST_BYTES
                + GpuBufferLayout.HOC_REQUEST_HEADER_BYTES).cpyFrom(ptr);
        MemoryUtil.memPutInt(buffer.address, count);
        this.nodeSync.submitRequestBatch(buffer);
    }

    static int readableRequestCount(int count, long downloadedBytes) {
        if (count < 0 || count > 50_000 || downloadedBytes < GpuBufferLayout.HOC_REQUEST_HEADER_BYTES) {
            return -1;
        }
        long downloadedCapacity = (downloadedBytes - GpuBufferLayout.HOC_REQUEST_HEADER_BYTES)
                / GpuBufferLayout.HOC_REQUEST_BYTES;
        return (int) Math.min(count, Math.min(MAX_REQUEST_QUEUE_SIZE, downloadedCapacity));
    }

    private void observeOverflow(int flags, int successfulRequests) {
        if (flags == 0) {
            return;
        }
        this.observedOverflowFlags |= flags;
        this.pendingRecoveryFlags |= flags;
        if ((flags & OVERFLOW_REQUEST) != 0) {
            this.requestDeferredFrames++;
        }
        if ((flags & ~OVERFLOW_REQUEST) == 0) {
            // The configured soft request budget is intentional backpressure, including
            // zero while mesh work is saturated. Do not report that as a GPU memory fault.
            if (this.requestDeferredFrames == 1L) {
                VoxyForge.LOGGER.debug("Original HOC request budget deferred work: successfulRequests={}; "
                        + "rejected attempts remain unrequested and retry next traversal", successfulRequests);
            }
            return;
        }
        this.overflowFrames++;
        long now = System.nanoTime();
        if (this.overflowFrames == 1L || now - this.lastOverflowLogNs >= OVERFLOW_LOG_INTERVAL_NS) {
            this.lastOverflowLogNs = now;
            VoxyForge.LOGGER.warn("Original HOC bounded queue overflow: flags=0x{}, successfulRequests={}, "
                            + "overflowFrames={}; counters contain successful reservations only, rejected attempts "
                            + "are not published. Next traversal resets queues and retries the original top-node hierarchy; "
                            + "rejected requests remain unrequested, child overflow uses an existing parent mesh when present.",
                    Integer.toHexString(flags), successfulRequests, this.overflowFrames);
        }
        if ((flags & ~KNOWN_OVERFLOW_FLAGS) != 0) {
            this.recordFailure("original-hoc-unknown-overflow-flags-" + Integer.toHexString(flags));
        }
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
        source = ForgeOriginalVoxyShaderCompiler.prepareSource(source);
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

    private String recordFailure(String reason) {
        String normalized = reason == null || reason.isBlank() ? "unspecified" : reason.replace(' ', '-');
        VoxyForge.LOGGER.error("Original hierarchical occlusion traverser failure: {}", normalized);
        return normalized;
    }
}
