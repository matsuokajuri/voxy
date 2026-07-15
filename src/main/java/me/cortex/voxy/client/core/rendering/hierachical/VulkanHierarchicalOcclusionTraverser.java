package me.cortex.voxy.client.core.rendering.hierachical;

import com.mojang.blaze3d.textures.AddressMode;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import me.cortex.voxy.client.RenderStatistics;
import me.cortex.voxy.client.config.VoxyConfig;
import me.cortex.voxy.client.core.RenderProperties;
import me.cortex.voxy.client.core.rendering.VulkanViewport;
import me.cortex.voxy.client.core.rendering.building.RenderGenerationService;
import me.cortex.voxy.client.core.vulkan.VoxyVulkanBuffer;
import me.cortex.voxy.client.core.vulkan.VoxyVulkanBufferUsage;
import me.cortex.voxy.client.core.vulkan.VoxyVulkanContext;
import me.cortex.voxy.client.core.vulkan.VoxyVulkanSampler;
import me.cortex.voxy.client.core.vulkan.VulkanCommandRecorder;
import me.cortex.voxy.client.core.vulkan.VulkanDownloadStream;
import me.cortex.voxy.client.core.vulkan.VulkanImageStates;
import me.cortex.voxy.client.core.vulkan.VulkanSync;
import me.cortex.voxy.client.core.vulkan.VulkanUploadStream;
import me.cortex.voxy.client.core.vulkan.shader.VulkanComputePipeline;
import me.cortex.voxy.client.core.vulkan.shader.VulkanPushDescriptors;
import me.cortex.voxy.client.core.vulkan.shader.VulkanShaderCompiler;
import me.cortex.voxy.client.core.vulkan.shader.VulkanShaderStage;
import me.cortex.voxy.common.Logger;
import me.cortex.voxy.common.util.MemoryBuffer;
import me.cortex.voxy.common.world.WorldEngine;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.VK12;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Vulkan expression of Voxy's original GPU hierarchical occlusion traversal owner. */
public final class VulkanHierarchicalOcclusionTraverser implements AutoCloseable {
    public static final int MAX_REQUEST_QUEUE_SIZE = 50;
    public static final int MAX_QUEUE_SIZE = 200_000;

    private static final int MAX_ITERATIONS = WorldEngine.MAX_LOD_LAYER + 1;
    private static final int LOCAL_WORK_SIZE_BITS = 5;
    private static final int LOCAL_WORK_SIZE = 1 << LOCAL_WORK_SIZE_BITS;
    private static final int UNIFORM_BYTES = 1024;
    private static final long QUEUE_META_STRIDE = 4L * Integer.BYTES;

    private static final int HIZ_BINDING = 0;
    private static final int SCENE_UNIFORM_BINDING = 1;
    private static final int REQUEST_QUEUE_BINDING = 2;
    private static final int RENDER_QUEUE_BINDING = 3;
    private static final int NODE_DATA_BINDING = 4;
    private static final int NODE_QUEUE_INDEX_BINDING = 5;
    private static final int NODE_QUEUE_META_BINDING = 6;
    private static final int NODE_QUEUE_SOURCE_BINDING = 7;
    private static final int NODE_QUEUE_SINK_BINDING = 8;
    private static final int RENDER_TRACKER_BINDING = 9;
    private static final int STATISTICS_BUFFER_BINDING = 10;

    private final AsyncNodeManager nodeManager;
    private final VulkanNodeCleaner nodeCleaner;
    private final RenderGenerationService meshGen;
    private final boolean statisticsEnabled;
    private final VulkanComputePipeline traversal;
    private final VoxyVulkanSampler hizSampler;
    private final VulkanUploadStream uploads = new VulkanUploadStream();
    private final VulkanDownloadStream downloads = VoxyVulkanContext.get().downloadStream();

    private final VoxyVulkanBuffer requestBuffer;
    private final VoxyVulkanBuffer nodeBuffer;
    private final VoxyVulkanBuffer uniformBuffer;
    private final VoxyVulkanBuffer statisticsBuffer;
    private final VoxyVulkanBuffer topNodeIds;
    private final VoxyVulkanBuffer queueMetaBuffer;
    private final VoxyVulkanBuffer scratchQueueA;
    private final VoxyVulkanBuffer scratchQueueB;

    private int topNodeCount;
    private final Int2IntOpenHashMap topNode2idxMapping = new Int2IntOpenHashMap();
    private final int[] idx2topNodeMapping = new int[MAX_QUEUE_SIZE];
    private boolean closed;

    public VulkanHierarchicalOcclusionTraverser(
            AsyncNodeManager nodeManager,
            VulkanNodeCleaner nodeCleaner,
            RenderGenerationService meshGen,
            RenderProperties properties
    ) {
        if (HierarchicalOcclusionTraverser.HIERARCHICAL_SHADER_DEBUG) {
            throw new UnsupportedOperationException(
                    "Vulkan HOC shader printf is not part of the formal renderer and cannot replace the traversal path"
            );
        }
        this.nodeManager = nodeManager;
        this.nodeCleaner = nodeCleaner;
        this.meshGen = meshGen;
        this.statisticsEnabled = RenderStatistics.enabled;

        Map<String, String> defines = new HashMap<>(properties.shaderDefines());
        defines.put("MAX_ITERATIONS", Integer.toString(MAX_ITERATIONS));
        defines.put("LOCAL_SIZE_BITS", Integer.toString(LOCAL_WORK_SIZE_BITS));
        defines.put("MAX_REQUEST_QUEUE_SIZE", Integer.toString(MAX_REQUEST_QUEUE_SIZE));
        defines.put("HIZ_BINDING", Integer.toString(HIZ_BINDING));
        defines.put("SCENE_UNIFORM_BINDING", Integer.toString(SCENE_UNIFORM_BINDING));
        defines.put("REQUEST_QUEUE_BINDING", Integer.toString(REQUEST_QUEUE_BINDING));
        defines.put("RENDER_QUEUE_BINDING", Integer.toString(RENDER_QUEUE_BINDING));
        defines.put("NODE_DATA_BINDING", Integer.toString(NODE_DATA_BINDING));
        defines.put("NODE_QUEUE_INDEX_BINDING", Integer.toString(NODE_QUEUE_INDEX_BINDING));
        defines.put("NODE_QUEUE_META_BINDING", Integer.toString(NODE_QUEUE_META_BINDING));
        defines.put("NODE_QUEUE_SOURCE_BINDING", Integer.toString(NODE_QUEUE_SOURCE_BINDING));
        defines.put("NODE_QUEUE_SINK_BINDING", Integer.toString(NODE_QUEUE_SINK_BINDING));
        defines.put("RENDER_TRACKER_BINDING", Integer.toString(RENDER_TRACKER_BINDING));
        if (this.statisticsEnabled) {
            defines.put("HAS_STATISTICS", "");
            defines.put("STATISTICS_BUFFER_BINDING", Integer.toString(STATISTICS_BUFFER_BINDING));
        }
        VulkanComputePipeline createdTraversal = null;
        VoxyVulkanBuffer createdRequest = null;
        VoxyVulkanBuffer createdNodes = null;
        VoxyVulkanBuffer createdUniform = null;
        VoxyVulkanBuffer createdStatistics = null;
        VoxyVulkanBuffer createdTopNodeIds = null;
        VoxyVulkanBuffer createdQueueMeta = null;
        VoxyVulkanBuffer createdScratchA = null;
        VoxyVulkanBuffer createdScratchB = null;
        VoxyVulkanSampler createdHizSampler = null;
        try {
            try (VulkanShaderCompiler compiler = new VulkanShaderCompiler()) {
                createdTraversal = new VulkanComputePipeline(
                        compiler.compile(
                                "voxy:lod/hierarchical/traversal_dev.comp",
                                VulkanShaderStage.COMPUTE,
                                defines
                        ),
                        "Voxy hierarchical occlusion traversal"
                );
            }

            int requestRoles = VoxyVulkanBufferUsage.STORAGE_COMPUTE
                    | VoxyVulkanBufferUsage.TRANSFER_SOURCE
                    | VoxyVulkanBufferUsage.TRANSFER_DESTINATION;
            int nodeRoles = VoxyVulkanBufferUsage.STORAGE_COMPUTE
                    | VoxyVulkanBufferUsage.TRANSFER_DESTINATION;
            int uniformRoles = VoxyVulkanBufferUsage.UNIFORM
                    | VoxyVulkanBufferUsage.TRANSFER_DESTINATION;
            int statisticsRoles = VoxyVulkanBufferUsage.STORAGE_COMPUTE
                    | VoxyVulkanBufferUsage.TRANSFER_SOURCE
                    | VoxyVulkanBufferUsage.TRANSFER_DESTINATION;
            int queueRoles = VoxyVulkanBufferUsage.STORAGE_COMPUTE
                    | VoxyVulkanBufferUsage.TRANSFER_DESTINATION;
            int queueMetaRoles = queueRoles | VoxyVulkanBufferUsage.INDIRECT;

            createdRequest = VoxyVulkanBuffer.create(
                    MAX_REQUEST_QUEUE_SIZE * 8L + 8L,
                    VoxyVulkanBufferUsage.of(requestRoles),
                    "Voxy HOC requests"
            );
            createdNodes = createFilledBuffer(
                    Math.multiplyExact(nodeManager.maxNodeCount, 16L),
                    nodeRoles,
                    "Voxy HOC nodes",
                    -1
            );
            createdUniform = VoxyVulkanBuffer.create(
                    UNIFORM_BYTES,
                    VoxyVulkanBufferUsage.of(uniformRoles),
                    "Voxy HOC scene uniform"
            );
            createdStatistics = VoxyVulkanBuffer.create(
                    1024L,
                    VoxyVulkanBufferUsage.of(statisticsRoles),
                    "Voxy HOC statistics"
            );
            createdTopNodeIds = VoxyVulkanBuffer.create(
                    MAX_QUEUE_SIZE * (long) Integer.BYTES,
                    VoxyVulkanBufferUsage.of(queueRoles),
                    "Voxy HOC top nodes"
            );
            createdQueueMeta = VoxyVulkanBuffer.create(
                    QUEUE_META_STRIDE * MAX_ITERATIONS,
                    VoxyVulkanBufferUsage.of(queueMetaRoles),
                    "Voxy HOC queue metadata"
            );
            createdScratchA = VoxyVulkanBuffer.create(
                    MAX_QUEUE_SIZE * (long) Integer.BYTES,
                    VoxyVulkanBufferUsage.of(queueRoles),
                    "Voxy HOC queue A"
            );
            createdScratchB = VoxyVulkanBuffer.create(
                    MAX_QUEUE_SIZE * (long) Integer.BYTES,
                    VoxyVulkanBufferUsage.of(queueRoles),
                    "Voxy HOC queue B"
            );
            createdHizSampler = VoxyVulkanSampler.nearestMipmapNearest(
                    AddressMode.CLAMP_TO_EDGE,
                    AddressMode.CLAMP_TO_EDGE
            );
        } catch (RuntimeException | Error exception) {
            if (createdHizSampler != null) createdHizSampler.close();
            if (createdScratchB != null) createdScratchB.close();
            if (createdScratchA != null) createdScratchA.close();
            if (createdQueueMeta != null) createdQueueMeta.close();
            if (createdTopNodeIds != null) createdTopNodeIds.close();
            if (createdStatistics != null) createdStatistics.close();
            if (createdUniform != null) createdUniform.close();
            if (createdNodes != null) createdNodes.close();
            if (createdRequest != null) createdRequest.close();
            if (createdTraversal != null) createdTraversal.close();
            throw exception;
        }
        this.traversal = createdTraversal;
        this.requestBuffer = createdRequest;
        this.nodeBuffer = createdNodes;
        this.uniformBuffer = createdUniform;
        this.statisticsBuffer = createdStatistics;
        this.topNodeIds = createdTopNodeIds;
        this.queueMetaBuffer = createdQueueMeta;
        this.scratchQueueA = createdScratchA;
        this.scratchQueueB = createdScratchB;
        this.hizSampler = createdHizSampler;

        this.topNode2idxMapping.defaultReturnValue(-1);
        this.nodeManager.setTLNAddRemoveCallbacks(this::addTLN, this::remTLN);
    }

    private static VoxyVulkanBuffer createFilledBuffer(long size, int roles, String label, int value) {
        VoxyVulkanBuffer buffer = VoxyVulkanBuffer.create(size, VoxyVulkanBufferUsage.of(roles), label);
        try {
            return buffer.fill(value);
        } catch (RuntimeException | Error exception) {
            buffer.close();
            throw exception;
        }
    }

    private void addTLN(int id) {
        this.ensureOpen();
        int index = this.topNodeCount++;
        if (this.topNodeCount > this.idx2topNodeMapping.length) {
            throw new IllegalStateException("Top level node count exceeds the traversal queue capacity");
        }
        MemoryUtil.memPutInt(this.uploads.upload(this.topNodeIds, index * 4L, 4).address(), id);
        if (this.topNode2idxMapping.put(id, index) != -1) throw new IllegalStateException("Duplicate top level node");
        this.idx2topNodeMapping[index] = id;
    }

    private void remTLN(int id) {
        this.ensureOpen();
        int index = this.topNode2idxMapping.remove(id);
        this.topNodeCount--;
        if (index == -1) throw new IllegalStateException("Unknown top level node");
        if (index == this.topNodeCount) return;

        int endId = this.idx2topNodeMapping[this.topNodeCount];
        this.idx2topNodeMapping[index] = endId;
        if (this.topNode2idxMapping.put(endId, index) == -1) {
            throw new IllegalStateException("Moved top level node was absent from the reverse mapping");
        }
        MemoryUtil.memPutInt(this.uploads.upload(this.topNodeIds, index * 4L, 4).address(), endId);
    }

    public void doTraversal(VulkanViewport<?> viewport) {
        this.ensureOpen();
        this.uploadUniform(viewport);
        this.uploadQueueMetadata();
        this.uploads.commit();

        if (this.statisticsEnabled) this.statisticsBuffer.zero();
        viewport.getRenderList().fill(0L, Integer.BYTES, 0);

        int firstDispatchSize = (this.topNodeCount + LOCAL_WORK_SIZE - 1) >> LOCAL_WORK_SIZE_BITS;
        VulkanCommandRecorder.record(commandBuffer -> {
            try (MemoryStack stack = MemoryStack.stackPush()) {
                this.prepareTraversalBuffers(commandBuffer, viewport);

                if (firstDispatchSize != 0) {
                    this.traversal.bind(
                            commandBuffer,
                            this.descriptors(viewport, this.topNodeIds, this.scratchQueueB),
                            queueIndexPush(stack, 0)
                    );
                    VK12.vkCmdDispatch(commandBuffer, firstDispatchSize, 1, 1);
                }

                for (int iteration = 1; iteration < MAX_ITERATIONS; iteration++) {
                    VoxyVulkanBuffer source = (iteration & 1) == 0 ? this.scratchQueueA : this.scratchQueueB;
                    VoxyVulkanBuffer sink = (iteration & 1) == 0 ? this.scratchQueueB : this.scratchQueueA;
                    this.barrierBetweenIterations(commandBuffer, source, sink);
                    this.traversal.bind(
                            commandBuffer,
                            this.descriptors(viewport, source, sink),
                            queueIndexPush(stack, iteration)
                    );
                    VK12.vkCmdDispatchIndirect(
                            commandBuffer,
                            this.queueMetaBuffer.vkBuffer(),
                            iteration * QUEUE_META_STRIDE
                    );
                }

                VulkanSync.bufferBarrier(
                        commandBuffer,
                        viewport.getRenderList().vkBuffer(),
                        0L,
                        viewport.getRenderList().size(),
                        VulkanSync.COMPUTE_STORAGE_READ_WRITE,
                        viewport.getRenderList().policy().consumerAccess()
                );
                VulkanSync.bufferBarrier(
                        commandBuffer,
                        this.nodeCleaner.visibilityBuffer().vkBuffer(),
                        0L,
                        this.nodeCleaner.visibilityBuffer().size(),
                        VulkanSync.COMPUTE_STORAGE_WRITE,
                        this.nodeCleaner.visibilityBuffer().policy().consumerAccess()
                );
                // traversal_dev.comp may set the requested flag through markRequested(). Publish
                // those persistent node writes before a later NodeCleaner read or async scatter
                // overwrite, matching the original final GL_SHADER_STORAGE_BARRIER_BIT.
                VulkanSync.bufferBarrier(
                        commandBuffer,
                        this.nodeBuffer.vkBuffer(),
                        0L,
                        this.nodeBuffer.size(),
                        VulkanSync.COMPUTE_STORAGE_READ_WRITE,
                        this.nodeBuffer.policy().consumerAccess()
                );
            }
        });

        this.downloadResetRequestQueue();
        if (this.statisticsEnabled) this.downloadStatistics();
    }

    private void prepareTraversalBuffers(org.lwjgl.vulkan.VkCommandBuffer commandBuffer, VulkanViewport<?> viewport) {
        VulkanSync.bufferBarrier(
                commandBuffer,
                this.nodeBuffer.vkBuffer(),
                0L,
                this.nodeBuffer.size(),
                this.nodeBuffer.policy().declaredAccess(),
                VulkanSync.COMPUTE_STORAGE_READ_WRITE
        );
        VulkanSync.bufferBarrier(
                commandBuffer,
                this.requestBuffer.vkBuffer(),
                0L,
                this.requestBuffer.size(),
                this.requestBuffer.policy().declaredAccess(),
                VulkanSync.COMPUTE_STORAGE_READ_WRITE
        );
        VulkanSync.bufferBarrier(
                commandBuffer,
                this.scratchQueueA.vkBuffer(),
                0L,
                this.scratchQueueA.size(),
                this.scratchQueueA.policy().declaredAccess(),
                VulkanSync.COMPUTE_STORAGE_READ_WRITE
        );
        VulkanSync.bufferBarrier(
                commandBuffer,
                this.scratchQueueB.vkBuffer(),
                0L,
                this.scratchQueueB.size(),
                this.scratchQueueB.policy().declaredAccess(),
                VulkanSync.COMPUTE_STORAGE_READ_WRITE
        );
        VulkanSync.bufferBarrier(
                commandBuffer,
                this.queueMetaBuffer.vkBuffer(),
                0L,
                this.queueMetaBuffer.size(),
                this.queueMetaBuffer.policy().declaredAccess(),
                VulkanSync.COMPUTE_STORAGE_READ_WRITE.or(VulkanSync.INDIRECT_READ)
        );
        VulkanSync.bufferBarrier(
                commandBuffer,
                this.nodeCleaner.visibilityBuffer().vkBuffer(),
                0L,
                this.nodeCleaner.visibilityBuffer().size(),
                this.nodeCleaner.visibilityBuffer().policy().declaredAccess(),
                VulkanSync.COMPUTE_STORAGE_WRITE
        );
        VulkanSync.bufferBarrier(
                commandBuffer,
                viewport.getRenderList().vkBuffer(),
                0L,
                viewport.getRenderList().size(),
                viewport.getRenderList().policy().declaredAccess(),
                VulkanSync.COMPUTE_STORAGE_READ_WRITE
        );
    }

    private void barrierBetweenIterations(
            org.lwjgl.vulkan.VkCommandBuffer commandBuffer,
            VoxyVulkanBuffer source,
            VoxyVulkanBuffer sink
    ) {
        VulkanSync.bufferBarrier(
                commandBuffer,
                this.queueMetaBuffer.vkBuffer(),
                0L,
                this.queueMetaBuffer.size(),
                VulkanSync.COMPUTE_STORAGE_READ_WRITE.or(VulkanSync.INDIRECT_READ),
                VulkanSync.COMPUTE_STORAGE_READ_WRITE.or(VulkanSync.INDIRECT_READ)
        );
        VulkanSync.bufferBarrier(
                commandBuffer,
                source.vkBuffer(),
                0L,
                source.size(),
                VulkanSync.COMPUTE_STORAGE_WRITE,
                VulkanSync.COMPUTE_STORAGE_READ
        );
        VulkanSync.bufferBarrier(
                commandBuffer,
                sink.vkBuffer(),
                0L,
                sink.size(),
                VulkanSync.COMPUTE_STORAGE_READ_WRITE,
                VulkanSync.COMPUTE_STORAGE_WRITE
        );
    }

    private VulkanPushDescriptors descriptors(
            VulkanViewport<?> viewport,
            VoxyVulkanBuffer source,
            VoxyVulkanBuffer sink
    ) {
        VulkanPushDescriptors descriptors = new VulkanPushDescriptors()
                .sampledImage(HIZ_BINDING, viewport.hiZBuffer.sampledView(), this.hizSampler,
                        VulkanImageStates.SHADER_SAMPLED.layout())
                .uniformBuffer(SCENE_UNIFORM_BINDING, this.uniformBuffer, 0L, UNIFORM_BYTES)
                .storageBuffer(REQUEST_QUEUE_BINDING, this.requestBuffer, 0L, this.requestBuffer.size())
                .storageBuffer(RENDER_QUEUE_BINDING, viewport.getRenderList(), 0L, viewport.getRenderList().size())
                .storageBuffer(NODE_DATA_BINDING, this.nodeBuffer, 0L, this.nodeBuffer.size())
                .storageBuffer(NODE_QUEUE_META_BINDING, this.queueMetaBuffer, 0L, this.queueMetaBuffer.size())
                .storageBuffer(NODE_QUEUE_SOURCE_BINDING, source, 0L, source.size())
                .storageBuffer(NODE_QUEUE_SINK_BINDING, sink, 0L, sink.size())
                .storageBuffer(RENDER_TRACKER_BINDING, this.nodeCleaner.visibilityBuffer(), 0L,
                        this.nodeCleaner.visibilityBuffer().size());
        if (this.statisticsEnabled) {
            descriptors.storageBuffer(
                    STATISTICS_BUFFER_BINDING,
                    this.statisticsBuffer,
                    0L,
                    this.statisticsBuffer.size()
            );
        }
        return descriptors;
    }

    private static ByteBuffer queueIndexPush(MemoryStack stack, int iteration) {
        return stack.malloc(Integer.BYTES)
                .order(ByteOrder.nativeOrder())
                .putInt(iteration)
                .flip();
    }

    private void uploadUniform(VulkanViewport<?> viewport) {
        long pointer = this.uploads.upload(this.uniformBuffer, 0L, UNIFORM_BYTES).address();
        long start = pointer;
        viewport.MVP.getToAddress(pointer);
        pointer += 4L * 4L * 4L;
        viewport.section.getToAddress(pointer);
        pointer += 4L * 3L;
        MemoryUtil.memPutInt(pointer, viewport.hiZBuffer.getPackedLevels());
        pointer += 4L;
        viewport.innerTranslation.getToAddress(pointer);
        pointer += 4L * 3L;
        float subdivisionArea = VoxyConfig.CONFIG.subDivisionSize * VoxyConfig.CONFIG.subDivisionSize;
        MemoryUtil.memPutFloat(pointer, subdivisionArea / (viewport.width * (float) viewport.height));
        pointer += 4L;
        for (int i = 0; i < 6; i++) {
            viewport.frustumPlanes[i].getToAddress(pointer);
            pointer += 4L * 4L;
        }
        MemoryUtil.memPutInt(pointer, (int) (viewport.getRenderList().size() / 4L - 1L));
        pointer += 4L;
        MemoryUtil.memPutInt(pointer, this.nodeCleaner.visibilityId());
        pointer += 4L;

        final double targetCount = 4000.0;
        double inverseFullness = Math.max(0.0, (targetCount - this.meshGen.getTaskCount()) / targetCount);
        inverseFullness *= inverseFullness;
        int requestSize = (int) Math.ceil(inverseFullness * MAX_REQUEST_QUEUE_SIZE);
        MemoryUtil.memPutInt(pointer, Math.max(0, Math.min(MAX_REQUEST_QUEUE_SIZE, requestSize)));
        pointer += 4L;
        MemoryUtil.memPutFloat(pointer, (float) Math.pow(VoxyConfig.CONFIG.sectionRenderDistance * 16.0 * 32.0, 2.0));
        pointer += 4L;

        MemoryUtil.memSet(pointer, 0, UNIFORM_BYTES - (pointer - start));
    }

    private void uploadQueueMetadata() {
        long pointer = this.uploads.upload(
                this.queueMetaBuffer,
                0L,
                Math.toIntExact(this.queueMetaBuffer.size())
        ).address();
        int firstDispatchSize = (this.topNodeCount + LOCAL_WORK_SIZE - 1) >> LOCAL_WORK_SIZE_BITS;
        MemoryUtil.memPutInt(pointer, firstDispatchSize);
        MemoryUtil.memPutInt(pointer + 4L, 1);
        MemoryUtil.memPutInt(pointer + 8L, 1);
        MemoryUtil.memPutInt(pointer + 12L, this.topNodeCount);
        for (int i = 1; i < MAX_ITERATIONS; i++) {
            long entry = pointer + i * QUEUE_META_STRIDE;
            MemoryUtil.memPutInt(entry, 0);
            MemoryUtil.memPutInt(entry + 4L, 1);
            MemoryUtil.memPutInt(entry + 8L, 1);
            MemoryUtil.memPutInt(entry + 12L, 0);
        }
    }

    private void downloadResetRequestQueue() {
        this.downloads.download(this.requestBuffer, this::forwardDownloadResult);
        this.requestBuffer.fill(0L, Integer.BYTES, 0);
    }

    private void forwardDownloadResult(long pointer, long size) {
        int count = MemoryUtil.memGetInt(pointer);
        pointer += 8L;
        if (count < 0 || count > 50_000) {
            Logger.error(new IllegalStateException(
                    "Vulkan HOC request count has an unexpected extreme value: " + count
            ));
            return;
        }
        int capacity = (int) ((this.requestBuffer.size() >> 3) - 1L);
        count = Math.min(count, capacity);
        if (count != 0) {
            MemoryBuffer buffer = new MemoryBuffer(count * 8L + 8L).cpyFrom(pointer - 8L);
            MemoryUtil.memPutInt(buffer.address, count);
            this.nodeManager.submitRequestBatch(buffer);
        }
    }

    private void downloadStatistics() {
        this.downloads.download(this.statisticsBuffer, (pointer, size) -> {
            for (int i = 0; i < MAX_ITERATIONS; i++) {
                RenderStatistics.hierarchicalTraversalCounts[i] = MemoryUtil.memGetInt(pointer + i * 4L);
                RenderStatistics.hierarchicalRenderSections[i] = MemoryUtil.memGetInt(
                        pointer + MAX_ITERATIONS * 4L + i * 4L
                );
            }
        });
    }

    /** Completes request/statistics callbacks before AsyncNodeManager.stop(). */
    public void flushDownloads() {
        this.ensureOpen();
        this.downloads.flushWaitClear();
    }

    public VoxyVulkanBuffer getNodeBuffer() {
        this.ensureOpen();
        return this.nodeBuffer;
    }

    public void addDebug(List<String> debug) {
        if (this.topNodeCount > this.idx2topNodeMapping.length / 2) {
            debug.add("TLN#: " + this.topNodeCount);
        }
    }

    private void ensureOpen() {
        if (this.closed) throw new IllegalStateException("Vulkan HOC traverser is closed");
    }

    @Override
    public void close() {
        if (this.closed) return;
        this.closed = true;
        this.uploads.commit();
        this.downloads.waitDiscard();
        this.requestBuffer.close();
        this.nodeBuffer.close();
        this.uniformBuffer.close();
        this.statisticsBuffer.close();
        this.topNodeIds.close();
        this.queueMetaBuffer.close();
        this.scratchQueueA.close();
        this.scratchQueueB.close();
        this.hizSampler.close();
        this.traversal.close();
    }
}
