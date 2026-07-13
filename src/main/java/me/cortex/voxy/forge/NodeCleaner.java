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
    private static final int SORTING_WORKER_SIZE = 64;
    private static final int WORK_PER_THREAD = 8;
    static final int OUTPUT_COUNT = 256;

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
        this.resultTransformerProgramId = compileComputeProgram(withDefines(
                "voxy:lod/hierarchical/cleaner/result_transformer.comp",
                "OUTPUT_SIZE", OUTPUT_COUNT,
                "MIN_ID_BUFFER_BINDING", 0,
                "NODE_BUFFER_BINDING", 1,
                "OUTPUT_BUFFER_BINDING", 2,
                "VISIBILITY_BUFFER_BINDING", 3));
        this.batchClearProgramId = compileComputeProgram(withDefines(
                "voxy:lod/hierarchical/cleaner/batch_visibility_set.comp",
                "VISIBILITY_BUFFER_BINDING", 0,
                "LIST_BUFFER_BINDING", 1));
        this.visibilityBuffer = new GlBuffer((long) this.nodeManager.maxNodeCount * Integer.BYTES, false)
                .fill(-1)
                .name("NodeCleaner visibility");
        this.outputBuffer = new GlBuffer(this.outputBufferSize(), false)
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

        this.outputBuffer.fill(this.nodeManager.maxNodeCount - 2);

        glUseProgram(this.sorterProgramId);
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 1, this.visibilityBuffer.id);
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 2, this.outputBuffer.id);
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 3, nodeDataBuffer.id);
        glMemoryBarrier(GL_SHADER_STORAGE_BARRIER_BIT);
        int groups = (this.nodeManager.getCurrentMaxNodeId() + (SORTING_WORKER_SIZE * WORK_PER_THREAD) - 1)
                / (SORTING_WORKER_SIZE * WORK_PER_THREAD);
        if (groups > 0) {
            glDispatchCompute(groups, 1, 1);
        }

        glUseProgram(this.resultTransformerProgramId);
        glBindBufferRange(GL_SHADER_STORAGE_BUFFER, 0, this.outputBuffer.id, 0L, (long) Integer.BYTES * OUTPUT_COUNT);
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 1, nodeDataBuffer.id);
        glBindBufferRange(
                GL_SHADER_STORAGE_BUFFER,
                2,
                this.outputBuffer.id,
                (long) Integer.BYTES * OUTPUT_COUNT,
                (long) Integer.BYTES * 2L * OUTPUT_COUNT);
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 3, this.visibilityBuffer.id);
        glUniform1ui(0, this.visibilityId);
        glMemoryBarrier(GL_SHADER_STORAGE_BARRIER_BIT);
        glDispatchCompute(1, 1, 1);
        glMemoryBarrier(GL_SHADER_STORAGE_BARRIER_BIT);
        glUseProgram(0);

        DownloadStream.instance().download(
                this.outputBuffer.id,
                this.outputBufferSize(),
                (long) Integer.BYTES * OUTPUT_COUNT,
                (long) Integer.BYTES * 2L * OUTPUT_COUNT,
                buffer -> this.nodeManager.submitRemoveBatch(buffer.copy()));
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
        glDispatchCompute((count + 127) / 128, 1, 1);
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

    private long outputBufferSize() {
        return (long) OUTPUT_COUNT * Integer.BYTES + (long) OUTPUT_COUNT * 2L * Integer.BYTES;
    }

    static String buildSorterSource() {
        String source = PrintfDebugUtil.processShader(
                ShaderLoader.parse("voxy:lod/hierarchical/cleaner/sort_visibility.comp"));
        return withDefinesSource(source,
                "WORK_SIZE", SORTING_WORKER_SIZE,
                "ELEMS_PER_THREAD", WORK_PER_THREAD,
                "OUTPUT_SIZE", OUTPUT_COUNT,
                "VISIBILITY_BUFFER_BINDING", 1,
                "OUTPUT_BUFFER_BINDING", 2,
                "NODE_DATA_BINDING", 3);
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
