#define SENTINAL_OUT_OF_BOUNDS uint(-1)

layout(location = NODE_QUEUE_INDEX_BINDING) uniform uint queueIdx;

layout(binding = NODE_QUEUE_META_BINDING, std430) restrict buffer NodeQueueMeta {
    uvec4 nodeQueueMetadata[MAX_ITERATIONS];
};

layout(binding = NODE_QUEUE_SOURCE_BINDING, std430) restrict readonly buffer NodeQueueSource {
    uint[] nodeQueueSource;
};

layout(binding = NODE_QUEUE_SINK_BINDING, std430) restrict writeonly buffer NodeQueueSink {
    uint[] nodeQueueSink;
};

uint getCurrentNode() {
    if (queueIdx >= uint(MAX_ITERATIONS)) {
        atomicOr(requestQueueIndex.y, uint(HOC_OVERFLOW_SOURCE));
        return SENTINAL_OUT_OF_BOUNDS;
    }
    uint sourceCapacity = uint(nodeQueueSource.length());
    uint validCount = min(nodeQueueMetadata[queueIdx].w, sourceCapacity);
    if (gl_GlobalInvocationID.x == 0u && nodeQueueMetadata[queueIdx].w > sourceCapacity) {
        atomicOr(requestQueueIndex.y, uint(HOC_OVERFLOW_SOURCE));
    }
    if (validCount <= gl_GlobalInvocationID.x) {
        return SENTINAL_OUT_OF_BOUNDS;
    }
    return nodeQueueSource[gl_GlobalInvocationID.x];
}


uint nodePushIndex = SENTINAL_OUT_OF_BOUNDS;
uint nodePushRemaining = 0u;
bool pushNodesInit(uint nodeCount) {
    nodePushIndex = SENTINAL_OUT_OF_BOUNDS;
    nodePushRemaining = 0u;
    // A whole child list belongs to one parent: never publish a partial descent.
    if (queueIdx >= uint(MAX_ITERATIONS - 1)) {
        atomicOr(requestQueueIndex.y, uint(HOC_OVERFLOW_LAST_ITERATION));
        return false;
    }
    uint capacity = uint(nodeQueueSink.length());
    uint count = atomicAdd(nodeQueueMetadata[queueIdx + 1u].w, 0u);
    for (;;) {
        if (nodeCount > capacity || count > capacity - nodeCount) {
            atomicOr(requestQueueIndex.y, uint(HOC_OVERFLOW_CHILD_QUEUE));
            return false;
        }
        uint nextCount = count + nodeCount;
        uint previous = atomicCompSwap(nodeQueueMetadata[queueIdx + 1u].w, count, nextCount);
        if (previous == count) {
            // Only successfully reserved entries contribute to indirect dispatch. Every
            // slot is filled by the caller before the inter-dispatch storage barrier.
            uint groups = (nextCount + uint(LOCAL_SIZE - 1)) >> LOCAL_SIZE_BITS;
            atomicMax(nodeQueueMetadata[queueIdx + 1u].x, groups);
            nodePushIndex = count;
            nodePushRemaining = nodeCount;
            return true;
        }
        count = previous;
    }
}

void pushNode(uint nodeId) {
    if (nodePushRemaining == 0u || nodePushIndex >= uint(nodeQueueSink.length())) {
        atomicOr(requestQueueIndex.y, uint(HOC_OVERFLOW_CHILD_QUEUE));
        return;
    }
    nodeQueueSink[nodePushIndex++] = nodeId;
    nodePushRemaining--;
}

#define SIMPLE_QUEUE(type, name, bindingIndex) layout(binding = bindingIndex, std430) restrict buffer name##Struct { \
    type name##Index; \
    type##[] name; \
};
