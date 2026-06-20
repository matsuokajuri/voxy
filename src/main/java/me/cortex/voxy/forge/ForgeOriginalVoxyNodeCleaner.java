package me.cortex.voxy.forge;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import me.cortex.voxy.common.util.MemoryBuffer;
import org.lwjgl.system.MemoryUtil;

import static org.lwjgl.opengl.GL11C.GL_FALSE;
import static org.lwjgl.opengl.GL11C.GL_UNSIGNED_INT;
import static org.lwjgl.opengl.GL15C.glDeleteBuffers;
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
import static org.lwjgl.opengl.GL30C.GL_R32UI;
import static org.lwjgl.opengl.GL30C.GL_RED_INTEGER;
import static org.lwjgl.opengl.GL30C.glBindBufferBase;
import static org.lwjgl.opengl.GL30C.glBindBufferRange;
import static org.lwjgl.opengl.GL30C.glUniform1ui;
import static org.lwjgl.opengl.GL42C.glMemoryBarrier;
import static org.lwjgl.opengl.GL43C.GL_COMPUTE_SHADER;
import static org.lwjgl.opengl.GL43C.GL_SHADER_STORAGE_BARRIER_BIT;
import static org.lwjgl.opengl.GL43C.GL_SHADER_STORAGE_BUFFER;
import static org.lwjgl.opengl.GL43C.glDispatchCompute;
import static org.lwjgl.opengl.GL45C.glCreateBuffers;
import static org.lwjgl.opengl.GL45C.nglClearNamedBufferData;
import static org.lwjgl.opengl.GL45C.nglNamedBufferStorage;

final class ForgeOriginalVoxyNodeCleaner {
    private static final int SORTING_WORKER_SIZE = 64;
    private static final int WORK_PER_THREAD = 8;
    static final int OUTPUT_COUNT = 256;

    private final int maxNodeCount;
    private int sorterProgramId;
    private int resultTransformerProgramId;
    private int batchClearProgramId;
    private int visibilityBufferId;
    private int outputBufferId;
    private int visibilityId;
    private long tickCount;
    private long updateIdsCount;
    private long removeBatchDownloadCount;
    private String lastLifecycleEvent = "created";
    private String lastFailureReason = "none";

    ForgeOriginalVoxyNodeCleaner(int maxNodeCount) {
        this.maxNodeCount = maxNodeCount;
    }

    void buildOnRenderThread() {
        this.freeOnRenderThread();
        this.sorterProgramId = compileComputeProgram(withDefines(SORT_VISIBILITY_SOURCE,
                "WORK_SIZE", SORTING_WORKER_SIZE,
                "ELEMS_PER_THREAD", WORK_PER_THREAD,
                "OUTPUT_SIZE", OUTPUT_COUNT,
                "VISIBILITY_BUFFER_BINDING", 1,
                "OUTPUT_BUFFER_BINDING", 2,
                "NODE_DATA_BINDING", 3));
        this.resultTransformerProgramId = compileComputeProgram(withDefines(RESULT_TRANSFORMER_SOURCE,
                "OUTPUT_SIZE", OUTPUT_COUNT,
                "MIN_ID_BUFFER_BINDING", 0,
                "NODE_BUFFER_BINDING", 1,
                "OUTPUT_BUFFER_BINDING", 2,
                "VISIBILITY_BUFFER_BINDING", 3));
        this.batchClearProgramId = compileComputeProgram(withDefines(BATCH_VISIBILITY_SET_SOURCE,
                "VISIBILITY_BUFFER_BINDING", 0,
                "LIST_BUFFER_BINDING", 1));
        this.visibilityBufferId = createStorageBuffer((long) this.maxNodeCount * Integer.BYTES, true);
        this.outputBufferId = createStorageBuffer((long) OUTPUT_COUNT * Integer.BYTES + (long) OUTPUT_COUNT * 2L * Integer.BYTES, false);
        this.lastLifecycleEvent = "build-on-render-thread";
        this.lastFailureReason = "none";
    }

    void tickOnRenderThread(ForgeOriginalVoxyAsyncNodeGeometrySync nodeManager) {
        if (!this.ready() || !nodeManager.nodeBufferReady()) {
            return;
        }
        this.visibilityId++;
        this.tickCount++;
        if (!this.shouldCleanGeometry(nodeManager)) {
            return;
        }

        long scratch = MemoryUtil.nmemAlloc(Integer.BYTES);
        try {
            MemoryUtil.memPutInt(scratch, this.maxNodeCount - 2);
            nglClearNamedBufferData(this.outputBufferId, GL_R32UI, GL_RED_INTEGER, GL_UNSIGNED_INT, scratch);
        } finally {
            MemoryUtil.nmemFree(scratch);
        }

        glUseProgram(this.sorterProgramId);
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 1, this.visibilityBufferId);
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 2, this.outputBufferId);
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 3, nodeManager.nodeBufferId());
        glMemoryBarrier(GL_SHADER_STORAGE_BARRIER_BIT);
        int groups = (nodeManager.currentMaxNodeId() + (SORTING_WORKER_SIZE * WORK_PER_THREAD) - 1)
                / (SORTING_WORKER_SIZE * WORK_PER_THREAD);
        if (groups > 0) {
            glDispatchCompute(groups, 1, 1);
        }

        glUseProgram(this.resultTransformerProgramId);
        glBindBufferRange(GL_SHADER_STORAGE_BUFFER, 0, this.outputBufferId, 0L, (long) Integer.BYTES * OUTPUT_COUNT);
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 1, nodeManager.nodeBufferId());
        glBindBufferRange(
                GL_SHADER_STORAGE_BUFFER,
                2,
                this.outputBufferId,
                (long) Integer.BYTES * OUTPUT_COUNT,
                (long) Integer.BYTES * 2L * OUTPUT_COUNT);
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 3, this.visibilityBufferId);
        glUniform1ui(0, this.visibilityId);
        glMemoryBarrier(GL_SHADER_STORAGE_BARRIER_BIT);
        glDispatchCompute(1, 1, 1);
        glMemoryBarrier(GL_SHADER_STORAGE_BARRIER_BIT);
        glUseProgram(0);

        ForgeOriginalVoxyDownloadStream.instance().download(
                this.outputBufferId,
                this.outputBufferSize(),
                (long) Integer.BYTES * OUTPUT_COUNT,
                (long) Integer.BYTES * 2L * OUTPUT_COUNT,
                buffer -> {
                    nodeManager.submitRemoveBatch(buffer.copy());
                    this.removeBatchDownloadCount++;
                });
    }

    void updateIds(IntOpenHashSet collection) {
        if (!this.ready() || collection.isEmpty()) {
            return;
        }
        int count = collection.size();
        long addr = ForgeOriginalVoxyUploadStream.instance().rawUploadAddress(count * Integer.BYTES);
        long ptr = ForgeOriginalVoxyUploadStream.instance().getBaseAddress() + addr;
        var iter = collection.intIterator();
        while (iter.hasNext()) {
            MemoryUtil.memPutInt(ptr, iter.nextInt());
            ptr += Integer.BYTES;
        }
        ForgeOriginalVoxyUploadStream.instance().commit();

        glUseProgram(this.batchClearProgramId);
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 0, this.visibilityBufferId);
        glBindBufferRange(
                GL_SHADER_STORAGE_BUFFER,
                1,
                ForgeOriginalVoxyUploadStream.instance().getRawBufferId(),
                addr,
                ForgeOriginalVoxyUploadStream.alignUpAlloc(count * Integer.BYTES));
        glUniform1ui(0, count);
        glUniform1ui(1, this.visibilityId);
        glMemoryBarrier(GL_SHADER_STORAGE_BARRIER_BIT);
        glDispatchCompute((count + 127) / 128, 1, 1);
        glMemoryBarrier(GL_SHADER_STORAGE_BARRIER_BIT);
        glUseProgram(0);
        this.updateIdsCount++;
    }

    int visibilityBufferId() {
        return this.visibilityBufferId;
    }

    int visibilityId() {
        return this.visibilityId;
    }

    boolean ready() {
        return this.sorterProgramId != 0
                && this.resultTransformerProgramId != 0
                && this.batchClearProgramId != 0
                && this.visibilityBufferId != 0
                && this.outputBufferId != 0;
    }

    void freeOnRenderThread() {
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
        if (this.visibilityBufferId != 0) {
            glDeleteBuffers(this.visibilityBufferId);
            this.visibilityBufferId = 0;
        }
        if (this.outputBufferId != 0) {
            glDeleteBuffers(this.outputBufferId);
            this.outputBufferId = 0;
        }
        this.lastLifecycleEvent = "free-on-render-thread";
    }

    private boolean shouldCleanGeometry(ForgeOriginalVoxyAsyncNodeGeometrySync nodeManager) {
        return nodeManager.geometryCapacityBytes() - nodeManager.usedGeometryBytes() < 256_000_000L;
    }

    private long outputBufferSize() {
        return (long) OUTPUT_COUNT * Integer.BYTES + (long) OUTPUT_COUNT * 2L * Integer.BYTES;
    }

    private static int createStorageBuffer(long size, boolean fillMinusOne) {
        int buffer = glCreateBuffers();
        nglNamedBufferStorage(buffer, size, 0L, 0);
        if (fillMinusOne) {
            long scratch = MemoryUtil.nmemAlloc(Integer.BYTES);
            try {
                MemoryUtil.memPutInt(scratch, -1);
                nglClearNamedBufferData(buffer, GL_R32UI, GL_RED_INTEGER, GL_UNSIGNED_INT, scratch);
            } finally {
                MemoryUtil.nmemFree(scratch);
            }
        }
        return buffer;
    }

    private static String withDefines(String source, Object... defines) {
        StringBuilder builder = new StringBuilder("#version 460 core\n");
        for (int i = 0; i < defines.length; i += 2) {
            builder.append("#define ").append(defines[i]).append(' ').append(defines[i + 1]).append('\n');
        }
        builder.append(source);
        return builder.toString();
    }

    private static int compileComputeProgram(String source) {
        int shader = glCreateShader(GL_COMPUTE_SHADER);
        glShaderSource(shader, source);
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

    private static final String POS_UTIL_SOURCE = """
#ifndef _POS_UTIL_DECL
#define _POS_UTIL_DECL
uint getLoDLevel(uvec2 packedPos) {
    return packedPos.x>>28;
}
ivec3 getLoDPosition(uvec2 packedPos) {
    int y = ((int(packedPos.x)<<4)>>24);
    int x = (int(packedPos.y)<<4)>>8;
    int z = int((packedPos.x&((1u<<20)-1))<<4);
    z |= int(packedPos.y>>28);
    z <<= 8;
    z >>= 8;
    return ivec3(x,y,z);
}
#endif
""";

    private static final String NODE_SOURCE = POS_UTIL_SOURCE + """
layout(binding = NODE_DATA_BINDING, std430) restrict buffer NodeData {
    uvec4[] nodes;
};
struct UnpackedNode {
    uint nodeId;
    uvec2 rawPos;
    ivec3 pos;
    uint lodLevel;
    uint flags;
    uint meshPtr;
    uint childPtr;
};
#define NULL_NODE ((1<<24)-1)
#define EMPTY_QUEUE_ID ((1<<24)-2)
#define NULL_MESH ((1<<24)-1)
#define EMPTY_MESH ((1<<24)-2)
uvec4 unpackNode(out UnpackedNode node, uint nodeId) {
    uvec4 compactedNode = nodes[nodeId];
    node.nodeId = nodeId;
    node.lodLevel = getLoDLevel(compactedNode.xy);
    node.rawPos = compactedNode.xy;
    node.pos = getLoDPosition(compactedNode.xy);
    node.meshPtr = compactedNode.z&0xFFFFFFu;
    node.childPtr = compactedNode.w&0xFFFFFFu;
    node.flags = ((compactedNode.z>>24)&0xFFu) | (((compactedNode.w>>24)&0xFFu)<<8);
    return compactedNode;
}
bool hasMesh(in UnpackedNode node) {
    return node.meshPtr != NULL_MESH;
}
bool isEmptyMesh(in UnpackedNode node) {
    return node.meshPtr == EMPTY_MESH;
}
bool hasChildren(in UnpackedNode node) {
    return node.childPtr != NULL_NODE;
}
bool childListIsEmpty(in UnpackedNode node) {
    return node.childPtr == EMPTY_QUEUE_ID;
}
bool hasRequested(in UnpackedNode node) {
    return (node.flags&1u) != 0u;
}
uint getChildCount(in UnpackedNode node) {
    return ((node.flags >> 2)&7U)+1;
}
uint getChildPtr(in UnpackedNode node) {
    return node.childPtr;
}
""";

    private static final String SORT_VISIBILITY_SOURCE = """
layout(local_size_x=WORK_SIZE, local_size_y=1) in;
""" + NODE_SOURCE + """
#define OPS_PER_THREAD (OUTPUT_SIZE/WORK_SIZE)
layout(binding = VISIBILITY_BUFFER_BINDING, std430) restrict readonly buffer VisibilityDataBuffer {
    uint[] visibility;
};
layout(binding = OUTPUT_BUFFER_BINDING, std430) restrict volatile buffer MinimumVisibilityBuffer {
    uint minVisIds[OUTPUT_SIZE];
};
uint atomicDerefMaxExchangeGlobal(uint atId, uint id) {
    const uint value = visibility[id];
    while (true) {
        const uint existingId = minVisIds[atId];
        if (existingId == id) {
            return uint(-1);
        }
        if (visibility[existingId&((1u<<31)-1)] <= value) {
            return id;
        }
        const uint c = atomicCompSwap(minVisIds[atId], existingId, id);
        if (c == existingId) {
            return existingId;
        }
    }
}
void bubbleSortGlobal(uint start, uint id) {
    for (uint i = start; i < OUTPUT_SIZE; i++) {
        id = atomicDerefMaxExchangeGlobal(i, id);
        if (id == uint(-1)) {
            break;
        }
    }
}
shared uint initalSort[OUTPUT_SIZE];
uint atomicDerefMaxExchangeLocal(uint atId, uint id) {
    const uint value = visibility[id];
    while (true) {
        const uint existingId = initalSort[atId];
        if (existingId == id) {
            return uint(-1);
        }
        if (visibility[existingId&((1u<<31)-1)] <= value) {
            return id;
        }
        const uint c = atomicCompSwap(initalSort[atId], existingId, id);
        if (c == existingId) {
            return existingId;
        }
    }
}
void bubbleSortInital(uint vis, uint id) {
    uint start = 0;
    if (visibility[initalSort[(OUTPUT_SIZE-1)>>1]] <= vis) {
        start = (OUTPUT_SIZE-1)>>1;
    }
    for (uint i = start; i < OUTPUT_SIZE; i++) {
        id = atomicDerefMaxExchangeLocal(i, id);
    }
}
bool shouldSortId(uint id) {
    UnpackedNode node;
    if (unpackNode(node, id)==uvec4(-1)) {
        return false;
    }
    if (isEmptyMesh(node) || (!hasMesh(node))) {
        return false;
    }
    if (node.lodLevel == 4) {
        return false;
    }
    if (hasRequested(node)) {
        return false;
    }
    return true;
}
void main() {
    for (uint i = 0; i < OPS_PER_THREAD; i++) {
        uint id = (gl_LocalInvocationID.x*OPS_PER_THREAD) + i;
        initalSort[id] = minVisIds[id]|(1u<<31);
    }
    barrier();
    for (uint i = 0; i < ELEMS_PER_THREAD; i++) {
        uint id = gl_GlobalInvocationID.x*ELEMS_PER_THREAD+i;
        uint vis = visibility[id];
        if (vis == uint(-1)) {
            continue;
        }
        if (visibility[minVisIds[OUTPUT_SIZE-1]] <= vis) {
            continue;
        }
        if (!shouldSortId(id)) {
            continue;
        }
        bubbleSortInital(vis, id);
    }
    barrier();
    for (uint i = 0; i < OPS_PER_THREAD; i++) {
        barrier();
        uint id = (gl_LocalInvocationID.x*OPS_PER_THREAD)+i;
        uint sid = initalSort[id];
        if ((sid&(1u<<31)) != 0) {
            continue;
        }
        uint vis = visibility[sid];
        if (visibility[minVisIds[OUTPUT_SIZE-1]] <= vis) {
            continue;
        }
        uint start = id;
        if (visibility[minVisIds[(id+OUTPUT_SIZE)>>1]] <= vis) {
            start = (id+OUTPUT_SIZE)>>1;
        }
        bubbleSortGlobal(start, sid);
    }
}
""";

    private static final String RESULT_TRANSFORMER_SOURCE = """
layout(local_size_x=OUTPUT_SIZE) in;
layout(binding = MIN_ID_BUFFER_BINDING, std430) restrict readonly buffer VisibilityDataBuffer {
    uint minVisIds[OUTPUT_SIZE];
};
layout(binding = NODE_BUFFER_BINDING, std430) restrict readonly buffer NodeData {
    uvec4[] nodes;
};
layout(binding = OUTPUT_BUFFER_BINDING, std430) restrict writeonly buffer OutputBuffer {
    uvec2 outputBuffer[OUTPUT_SIZE];
};
layout(binding = VISIBILITY_BUFFER_BINDING, std430) restrict buffer VisibilityBuffer {
    uint[] visibility;
};
layout(location=0) uniform uint visibilityCounter;
void main() {
    uint id = minVisIds[gl_LocalInvocationID.x];
    uvec4 node = nodes[id];
    uvec2 res = node.xy;
    if (all(equal(node, uvec4(-1)))) {
        res = uvec2(-1);
    }
    outputBuffer[gl_LocalInvocationID.x] = res;
    visibility[id] = visibilityCounter + 60;
}
""";

    private static final String BATCH_VISIBILITY_SET_SOURCE = """
layout(local_size_x=128) in;
layout(binding = VISIBILITY_BUFFER_BINDING, std430) restrict writeonly buffer VisibilityDataBuffer {
    uint[] visiblity;
};
layout(binding = LIST_BUFFER_BINDING, std430) restrict readonly buffer SetListBuffer {
    uint[] ids;
};
layout(location=0) uniform uint count;
layout(location=1) uniform uint setTo;
void main() {
    uint id = gl_GlobalInvocationID.x;
    if (count <= id) {
        return;
    }
    uint pos = ids[id];
    visiblity[pos&((1u<<31)-1)] = mix(setTo, uint(-1), (pos&(1u<<31)) == 0);
}
""";
}
