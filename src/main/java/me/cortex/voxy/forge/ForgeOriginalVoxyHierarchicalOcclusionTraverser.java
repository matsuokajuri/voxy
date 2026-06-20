package me.cortex.voxy.forge;

import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import me.cortex.voxy.config.ForgeVoxyConfig;
import me.cortex.voxy.common.util.MemoryBuffer;
import me.cortex.voxy.common.world.WorldEngine;
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
    private final Int2IntOpenHashMap topNode2idxMapping = new Int2IntOpenHashMap();
    private final int[] idx2topNodeMapping = new int[MAX_QUEUE_SIZE];
    private final int hizSampler = glGenSamplers();
    private int traversalProgramId;
    private int topNodeCount;
    private long traversalRunCount;
    private long requestBatchForwardCount;
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

    String buildOnRenderThread(ForgeOriginalVoxyRenderProperties properties) {
        try {
            if (this.traversalProgramId != 0) {
                glDeleteProgram(this.traversalProgramId);
                this.traversalProgramId = 0;
            }
            String source = properties.injectDefines(ForgeOriginalVoxyShaderSource.parse("voxy:lod/hierarchical/traversal_dev.comp"));
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
                    "RENDER_TRACKER_BINDING", RENDER_TRACKER_BINDING);
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
                && this.scratchQueueB.id != 0;
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
        this.bindings(viewport);
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
                this.scratchQueueA.id != 0 && this.scratchQueueB.id != 0,
                this.hizSampler != 0,
                this.topNodeCount,
                this.traversalRunCount,
                this.requestBatchForwardCount,
                this.lastLifecycleEvent,
                this.lastFailureReason
        );
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
    }

    private void traverseInternal() {
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
