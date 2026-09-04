package me.cortex.voxy.forge;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import me.cortex.voxy.common.util.MemoryBuffer;
import org.lwjgl.system.MemoryUtil;

import static org.lwjgl.opengl.GL11C.GL_FALSE;
import static org.lwjgl.opengl.GL20C.GL_COMPILE_STATUS;
import static org.lwjgl.opengl.GL20C.GL_LINK_STATUS;
import static org.lwjgl.opengl.GL20C.glAttachShader;
import static org.lwjgl.opengl.GL20C.glCompileShader;
import static org.lwjgl.opengl.GL20C.glCreateProgram;
import static org.lwjgl.opengl.GL20C.glCreateShader;
import static org.lwjgl.opengl.GL20C.glDeleteProgram;
import static org.lwjgl.opengl.GL20C.glDeleteShader;
import static org.lwjgl.opengl.GL20C.glGetProgramInfoLog;
import static org.lwjgl.opengl.GL20C.glGetProgrami;
import static org.lwjgl.opengl.GL20C.glGetShaderInfoLog;
import static org.lwjgl.opengl.GL20C.glGetShaderi;
import static org.lwjgl.opengl.GL20C.glLinkProgram;
import static org.lwjgl.opengl.GL20C.glShaderSource;
import static org.lwjgl.opengl.GL20C.glUseProgram;
import static org.lwjgl.opengl.GL30C.glBindBufferBase;
import static org.lwjgl.opengl.GL30C.glBindBufferRange;
import static org.lwjgl.opengl.GL30C.glUniform1ui;
import static org.lwjgl.opengl.GL42C.glMemoryBarrier;
import static org.lwjgl.opengl.GL43C.GL_COMPUTE_SHADER;
import static org.lwjgl.opengl.GL43C.GL_SHADER_STORAGE_BARRIER_BIT;
import static org.lwjgl.opengl.GL43C.GL_SHADER_STORAGE_BUFFER;
import static org.lwjgl.opengl.GL43C.glDispatchCompute;

final class NodeCleaner {
    private static final int SORTING_WORKER_SIZE = GpuBufferLayout.CLEANER_LOCAL_SIZE;
    private static final int WORK_PER_THREAD = GpuBufferLayout.CLEANER_ELEMENTS_PER_THREAD;
    private static final int BATCH_WORKER_SIZE = 128;
    static final int OUTPUT_COUNT = GpuBufferLayout.CLEANER_OUTPUT_CAPACITY;

    private final AsyncNodeManager nodeManager;
    private int sorterProgramId;
    private int resultTransformerProgramId;
    private int batchClearProgramId;
    private GlBuffer visibilityBuffer;
    private GlBuffer outputBuffer;
    private int visibilityId;
    NodeCleaner(AsyncNodeManager nodeManager) {
        this.nodeManager = nodeManager;
    }

    void buildOnRenderThread() {
        this.free();
        this.sorterProgramId = compileComputeProgram(buildSorterSource());
        this.resultTransformerProgramId = compileComputeProgram(buildResultTransformerSource());
        this.batchClearProgramId = compileComputeProgram(buildBatchClearSource());
        this.visibilityBuffer = new GlBuffer((long) this.nodeManager.maxNodeCount * Integer.BYTES, false)
                .fill(-1)
                .name("NodeCleaner visibility");
        this.outputBuffer = new GlBuffer(GpuBufferLayout.CLEANER_OUTPUT_BYTES, false)
                .name("NodeCleaner output");
    }

    void tick(GlBuffer nodeDataBuffer) {
        if (!this.ready()) {
            return;
        }
        this.visibilityId++;
        if (!this.shouldCleanGeometry()) {
            return;
        }

        // This is a fixed set of 256 candidates, not an append queue. An empty slot must never
        // alias an allocated node near the end of the node arena.
        this.outputBuffer.fill(-1);
        int nodeCount = nodeCountForMaxId(this.nodeManager.getCurrentMaxNodeId(), this.nodeManager.maxNodeCount);

        glUseProgram(this.sorterProgramId);
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 1, this.visibilityBuffer.id);
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 2, this.outputBuffer.id);
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 3, nodeDataBuffer.id);
        glUniform1ui(0, nodeCount);
        glMemoryBarrier(GL_SHADER_STORAGE_BARRIER_BIT);
        int groups = sorterWorkGroupCount(nodeCount);
        if (groups > 0) {
            glDispatchCompute(groups, 1, 1);
        }

        glUseProgram(this.resultTransformerProgramId);
        glBindBufferRange(GL_SHADER_STORAGE_BUFFER, 0, this.outputBuffer.id, 0L, GpuBufferLayout.CLEANER_ID_BYTES);
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 1, nodeDataBuffer.id);
        glBindBufferRange(
                GL_SHADER_STORAGE_BUFFER,
                2,
                this.outputBuffer.id,
                GpuBufferLayout.CLEANER_ID_BYTES,
                GpuBufferLayout.CLEANER_POSITION_BYTES);
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 3, this.visibilityBuffer.id);
        glUniform1ui(0, this.visibilityId);
        glUniform1ui(1, nodeCount);
        glMemoryBarrier(GL_SHADER_STORAGE_BARRIER_BIT);
        glDispatchCompute(1, 1, 1);
        glMemoryBarrier(GL_SHADER_STORAGE_BARRIER_BIT);
        glUseProgram(0);

        DownloadStream.instance().download(
                this.outputBuffer.id,
                this.outputBuffer.size(),
                GpuBufferLayout.CLEANER_ID_BYTES,
                GpuBufferLayout.CLEANER_POSITION_BYTES,
                buffer -> this.nodeManager.submitRemoveBatch(copyRemovalPositions(buffer)));
    }

    void updateIds(IntOpenHashSet collection) {
        if (!this.ready() || collection.isEmpty()) {
            return;
        }
        int count = collection.size();
        long addr = UploadStream.instance().rawUploadAddress(count * Integer.BYTES);
        long ptr = UploadStream.instance().getBaseAddress() + addr;
        var iter = collection.intIterator();
        while (iter.hasNext()) {
            MemoryUtil.memPutInt(ptr, iter.nextInt());
            ptr += Integer.BYTES;
        }
        UploadStream.instance().commit();

        glUseProgram(this.batchClearProgramId);
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 0, this.visibilityBuffer.id);
        glBindBufferRange(
                GL_SHADER_STORAGE_BUFFER,
                1,
                UploadStream.instance().getRawBufferId(),
                addr,
                UploadStream.alignUpAlloc(count * Integer.BYTES));
        glUniform1ui(0, count);
        glUniform1ui(1, this.visibilityId);
        glMemoryBarrier(GL_SHADER_STORAGE_BARRIER_BIT);
        glDispatchCompute((count + BATCH_WORKER_SIZE - 1) / BATCH_WORKER_SIZE, 1, 1);
        glMemoryBarrier(GL_SHADER_STORAGE_BARRIER_BIT);
        glUseProgram(0);
    }

    int visibilityBufferId() {
        return this.visibilityBuffer == null ? 0 : this.visibilityBuffer.id;
    }

    int visibilityId() {
        return this.visibilityId;
    }

    boolean ready() {
        return this.sorterProgramId != 0
                && this.resultTransformerProgramId != 0
                && this.batchClearProgramId != 0
                && this.visibilityBuffer != null
                && this.outputBuffer != null;
    }

    void free() {
        if (this.sorterProgramId != 0) {
            glDeleteProgram(this.sorterProgramId);
            this.sorterProgramId = 0;
        }
        if (this.resultTransformerProgramId != 0) {
            glDeleteProgram(this.resultTransformerProgramId);
            this.resultTransformerProgramId = 0;
        }
        if (this.batchClearProgramId != 0) {
            glDeleteProgram(this.batchClearProgramId);
            this.batchClearProgramId = 0;
        }
        if (this.visibilityBuffer != null) {
            this.visibilityBuffer.free();
            this.visibilityBuffer = null;
        }
        if (this.outputBuffer != null) {
            this.outputBuffer.free();
            this.outputBuffer = null;
        }
    }

    private boolean shouldCleanGeometry() {
        return this.nodeManager.getGeometryCapacity() - this.nodeManager.getUsedGeometryCapacity() < 256_000_000L;
    }

    static int nodeCountForMaxId(int maxNodeId, int capacity) {
        if (capacity < 0 || maxNodeId < -1 || maxNodeId >= capacity) {
            throw new IllegalArgumentException("Cleaner node range outside its arena: max="
                    + maxNodeId + ", capacity=" + capacity);
        }
        // HierarchicalBitSet.getMaxIndex() is inclusive, with -1 for an empty arena.
        return maxNodeId + 1;
    }

    static int sorterWorkGroupCount(int nodeCount) {
        if (nodeCount < 0) {
            throw new IllegalArgumentException("Negative cleaner node count");
        }
        int elementsPerGroup = SORTING_WORKER_SIZE * WORK_PER_THREAD;
        return (int) ((nodeCount + (long) elementsPerGroup - 1L) / elementsPerGroup);
    }

    static MemoryBuffer copyRemovalPositions(MemoryBuffer buffer) {
        if (buffer.size != GpuBufferLayout.CLEANER_POSITION_BYTES) {
            throw new IllegalArgumentException("Cleaner removal batch must contain exactly "
                    + OUTPUT_COUNT + " positions, got " + buffer.size + " bytes");
        }
        return buffer.copy();
    }

    static String buildSorterSource() {
        String source = PrintfDebugUtil.processShader(
                ShaderLoader.parse("voxy:lod/hierarchical/cleaner/sort_visibility.comp"));
        return withDefinesSource(source,
                "WORK_SIZE", SORTING_WORKER_SIZE,
                "ELEMS_PER_THREAD", WORK_PER_THREAD,
                "OUTPUT_SIZE", OUTPUT_COUNT,
                "MAX_LOD", GpuBufferLayout.MAX_LOD,
                "VISIBILITY_BUFFER_BINDING", 1,
                "OUTPUT_BUFFER_BINDING", 2,
                "NODE_DATA_BINDING", 3);
    }

    static String buildResultTransformerSource() {
        return withDefines("voxy:lod/hierarchical/cleaner/result_transformer.comp",
                "OUTPUT_SIZE", OUTPUT_COUNT,
                "MIN_ID_BUFFER_BINDING", 0,
                "NODE_BUFFER_BINDING", 1,
                "OUTPUT_BUFFER_BINDING", 2,
                "VISIBILITY_BUFFER_BINDING", 3);
    }

    static String buildBatchClearSource() {
        return withDefines("voxy:lod/hierarchical/cleaner/batch_visibility_set.comp",
                "WORK_SIZE", BATCH_WORKER_SIZE,
                "VISIBILITY_BUFFER_BINDING", 0,
                "LIST_BUFFER_BINDING", 1);
    }

    private static String withDefines(String shaderId, Object... defines) {
        return withDefinesSource(ShaderLoader.parse(shaderId), defines);
    }

    private static String withDefinesSource(String source, Object... defines) {
        int versionEnd = source.indexOf('\n');
        if (versionEnd < 0) {
            throw new IllegalStateException("Shader source has no version line");
        }
        StringBuilder builder = new StringBuilder(source.length() + defines.length * 24);
        builder.append(source, 0, versionEnd + 1);
        for (int i = 0; i < defines.length; i += 2) {
            builder.append("#define ").append(defines[i]).append(' ').append(defines[i + 1]).append('\n');
        }
        builder.append(source, versionEnd + 1, source.length());
        return builder.toString();
    }

    private static int compileComputeProgram(String source) {
        int shader = glCreateShader(GL_COMPUTE_SHADER);
        glShaderSource(shader, ForgeOriginalVoxyShaderCompiler.prepareSource(source));
        glCompileShader(shader);
        if (glGetShaderi(shader, GL_COMPILE_STATUS) == GL_FALSE) {
            String log = glGetShaderInfoLog(shader);
            glDeleteShader(shader);
            throw new IllegalStateException("Original node cleaner compute shader compile failed: " + log);
        }
        int program = glCreateProgram();
        glAttachShader(program, shader);
        glLinkProgram(program);
        glDeleteShader(shader);
        if (glGetProgrami(program, GL_LINK_STATUS) == GL_FALSE) {
            String log = glGetProgramInfoLog(program);
            glDeleteProgram(program);
            throw new IllegalStateException("Original node cleaner compute shader link failed: " + log);
        }
        return program;
    }

}
