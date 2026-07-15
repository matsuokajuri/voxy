package me.cortex.voxy.client.core.rendering.hierachical;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import me.cortex.voxy.client.core.vulkan.VoxyVulkanBuffer;
import me.cortex.voxy.client.core.vulkan.VoxyVulkanBufferUsage;
import me.cortex.voxy.client.core.vulkan.VulkanCommandRecorder;
import me.cortex.voxy.client.core.vulkan.VulkanDownloadStream;
import me.cortex.voxy.client.core.vulkan.VulkanSync;
import me.cortex.voxy.client.core.vulkan.VulkanUploadStream;
import me.cortex.voxy.client.core.vulkan.shader.VulkanComputePipeline;
import me.cortex.voxy.client.core.vulkan.shader.VulkanPushDescriptors;
import me.cortex.voxy.client.core.vulkan.shader.VulkanShaderCompiler;
import me.cortex.voxy.client.core.vulkan.shader.VulkanShaderStage;
import me.cortex.voxy.common.util.MemoryBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.VK12;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Map;

/** Vulkan owner for the original three-stage NodeCleaner compute/readback chain. */
public final class VulkanNodeCleaner implements AutoCloseable {
    private static final int SORTING_WORKER_SIZE = 64;
    private static final int WORK_PER_THREAD = 8;
    public static final int OUTPUT_COUNT = 256;
    private static final long MIN_ID_BYTES = OUTPUT_COUNT * Integer.BYTES;
    private static final long REMOVE_OUTPUT_BYTES = OUTPUT_COUNT * 2L * Integer.BYTES;
    private static final long OUTPUT_BUFFER_BYTES = MIN_ID_BYTES + REMOVE_OUTPUT_BYTES;
    private static final long INITIAL_ID_LIST_BYTES = 4096L;

    private final AsyncNodeManager nodeManager;
    private final VulkanComputePipeline sorter;
    private final VulkanComputePipeline resultTransformer;
    private final VulkanComputePipeline batchClear;
    private final VulkanUploadStream uploads = new VulkanUploadStream();
    private final VulkanDownloadStream downloads = new VulkanDownloadStream();
    private final VoxyVulkanBuffer visibilityBuffer;
    private final VoxyVulkanBuffer outputBuffer;
    private VoxyVulkanBuffer idListBuffer;
    private int visibilityId;
    private boolean closed;

    public VulkanNodeCleaner(AsyncNodeManager nodeManager) {
        this.nodeManager = nodeManager;
        VulkanComputePipeline createdSorter = null;
        VulkanComputePipeline createdResultTransformer = null;
        VulkanComputePipeline createdBatchClear = null;
        VoxyVulkanBuffer createdVisibility = null;
        VoxyVulkanBuffer createdOutput = null;
        VoxyVulkanBuffer createdIdList = null;
        try {
            try (VulkanShaderCompiler compiler = new VulkanShaderCompiler()) {
                createdSorter = new VulkanComputePipeline(
                    compiler.compile(
                            "voxy:lod/hierarchical/cleaner/sort_visibility.comp",
                            VulkanShaderStage.COMPUTE,
                            Map.of(
                                    "WORK_SIZE", Integer.toString(SORTING_WORKER_SIZE),
                                    "ELEMS_PER_THREAD", Integer.toString(WORK_PER_THREAD),
                                    "OUTPUT_SIZE", Integer.toString(OUTPUT_COUNT),
                                    "VISIBILITY_BUFFER_BINDING", "1",
                                    "OUTPUT_BUFFER_BINDING", "2",
                                    "NODE_DATA_BINDING", "3"
                            )
                    ),
                    "Voxy NodeCleaner visibility sort"
                );
                createdResultTransformer = new VulkanComputePipeline(
                    compiler.compile(
                            "voxy:lod/hierarchical/cleaner/result_transformer.comp",
                            VulkanShaderStage.COMPUTE,
                            Map.of(
                                    "OUTPUT_SIZE", Integer.toString(OUTPUT_COUNT),
                                    "MIN_ID_BUFFER_BINDING", "0",
                                    "NODE_BUFFER_BINDING", "1",
                                    "OUTPUT_BUFFER_BINDING", "2",
                                    "VISIBILITY_BUFFER_BINDING", "3"
                            )
                    ),
                    "Voxy NodeCleaner result transform"
                );
                createdBatchClear = new VulkanComputePipeline(
                    compiler.compile(
                            "voxy:lod/hierarchical/cleaner/batch_visibility_set.comp",
                            VulkanShaderStage.COMPUTE,
                            Map.of(
                                    "VISIBILITY_BUFFER_BINDING", "0",
                                    "LIST_BUFFER_BINDING", "1"
                            )
                    ),
                    "Voxy NodeCleaner visibility reset"
                );
            }

            int visibilityRoles = VoxyVulkanBufferUsage.STORAGE_COMPUTE
                    | VoxyVulkanBufferUsage.TRANSFER_DESTINATION;
            int outputRoles = VoxyVulkanBufferUsage.STORAGE_COMPUTE
                    | VoxyVulkanBufferUsage.TRANSFER_SOURCE
                    | VoxyVulkanBufferUsage.TRANSFER_DESTINATION;
            int idListRoles = VoxyVulkanBufferUsage.STORAGE_COMPUTE
                    | VoxyVulkanBufferUsage.TRANSFER_DESTINATION;
            createdVisibility = createFilledBuffer(
                    Math.multiplyExact(nodeManager.maxNodeCount, (long) Integer.BYTES),
                    visibilityRoles,
                    "Voxy NodeCleaner visibility",
                    -1
            );
            createdOutput = VoxyVulkanBuffer.create(
                    OUTPUT_BUFFER_BYTES,
                    VoxyVulkanBufferUsage.of(outputRoles),
                    "Voxy NodeCleaner output"
            );
            createdIdList = VoxyVulkanBuffer.create(
                    INITIAL_ID_LIST_BYTES,
                    VoxyVulkanBufferUsage.of(idListRoles),
                    "Voxy NodeCleaner ID updates"
            );
        } catch (RuntimeException | Error exception) {
            if (createdIdList != null) createdIdList.close();
            if (createdOutput != null) createdOutput.close();
            if (createdVisibility != null) createdVisibility.close();
            if (createdBatchClear != null) createdBatchClear.close();
            if (createdResultTransformer != null) createdResultTransformer.close();
            if (createdSorter != null) createdSorter.close();
            throw exception;
        }
        this.sorter = createdSorter;
        this.resultTransformer = createdResultTransformer;
        this.batchClear = createdBatchClear;
        this.visibilityBuffer = createdVisibility;
        this.outputBuffer = createdOutput;
        this.idListBuffer = createdIdList;
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

    public void tick(VoxyVulkanBuffer nodeDataBuffer) {
        this.ensureOpen();
        this.downloads.tick();
        this.visibilityId++;
        if (!this.shouldCleanGeometry()) return;

        this.outputBuffer.fill(this.nodeManager.maxNodeCount - 2);
        VulkanPushDescriptors sorterDescriptors = new VulkanPushDescriptors()
                .storageBuffer(1, this.visibilityBuffer, 0L, this.visibilityBuffer.size())
                .storageBuffer(2, this.outputBuffer, 0L, this.outputBuffer.size())
                .storageBuffer(3, nodeDataBuffer, 0L, nodeDataBuffer.size());
        VulkanPushDescriptors transformDescriptors = new VulkanPushDescriptors()
                .storageBuffer(0, this.outputBuffer, 0L, MIN_ID_BYTES)
                .storageBuffer(1, nodeDataBuffer, 0L, nodeDataBuffer.size())
                .storageBuffer(2, this.outputBuffer, MIN_ID_BYTES, REMOVE_OUTPUT_BYTES)
                .storageBuffer(3, this.visibilityBuffer, 0L, this.visibilityBuffer.size());
        try (MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer visibilityPush = stack.malloc(Integer.BYTES).order(ByteOrder.nativeOrder());
            visibilityPush.putInt(this.visibilityId).flip();
            int sortGroups = (this.nodeManager.getCurrentMaxNodeId()
                    + SORTING_WORKER_SIZE * WORK_PER_THREAD - 1) / (SORTING_WORKER_SIZE * WORK_PER_THREAD);
            VulkanCommandRecorder.record(commandBuffer -> {
                VulkanSync.bufferBarrier(
                        commandBuffer,
                        this.visibilityBuffer.vkBuffer(),
                        0L,
                        this.visibilityBuffer.size(),
                        this.visibilityBuffer.policy().declaredAccess(),
                        VulkanSync.COMPUTE_STORAGE_READ_WRITE
                );
                VulkanSync.bufferBarrier(
                        commandBuffer,
                        nodeDataBuffer.vkBuffer(),
                        0L,
                        nodeDataBuffer.size(),
                        nodeDataBuffer.policy().declaredAccess(),
                        VulkanSync.COMPUTE_STORAGE_READ
                );
                this.sorter.bind(commandBuffer, sorterDescriptors, null);
                VK12.vkCmdDispatch(commandBuffer, sortGroups, 1, 1);
                VulkanSync.bufferBarrier(
                        commandBuffer,
                        this.outputBuffer.vkBuffer(),
                        0L,
                        this.outputBuffer.size(),
                        VulkanSync.COMPUTE_STORAGE_READ_WRITE,
                        VulkanSync.COMPUTE_STORAGE_READ_WRITE
                );
                this.resultTransformer.bind(commandBuffer, transformDescriptors, visibilityPush);
                VK12.vkCmdDispatch(commandBuffer, 1, 1, 1);
                VulkanSync.bufferBarrier(
                        commandBuffer,
                        this.visibilityBuffer.vkBuffer(),
                        0L,
                        this.visibilityBuffer.size(),
                        VulkanSync.COMPUTE_STORAGE_WRITE,
                        this.visibilityBuffer.policy().consumerAccess()
                );
            });
        }

        this.downloads.download(
                this.outputBuffer,
                MIN_ID_BYTES,
                REMOVE_OUTPUT_BYTES,
                (pointer, size) -> this.nodeManager.submitRemoveBatch(new MemoryBuffer(size).cpyFrom(pointer))
        );
    }

    public void updateIds(IntOpenHashSet collection) {
        this.ensureOpen();
        if (collection.isEmpty()) return;

        int count = collection.size();
        int bytes = Math.multiplyExact(count, Integer.BYTES);
        this.idListBuffer = ensureCapacity(this.idListBuffer, bytes);
        long pointer = this.uploads.upload(this.idListBuffer, 0L, bytes).address();
        var iterator = collection.iterator();
        while (iterator.hasNext()) {
            MemoryUtil.memPutInt(pointer, iterator.nextInt());
            pointer += Integer.BYTES;
        }
        this.uploads.commit();

        VulkanPushDescriptors descriptors = new VulkanPushDescriptors()
                .storageBuffer(0, this.visibilityBuffer, 0L, this.visibilityBuffer.size())
                .storageBuffer(1, this.idListBuffer, 0L, bytes);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer push = stack.malloc(2 * Integer.BYTES).order(ByteOrder.nativeOrder());
            push.putInt(count).putInt(this.visibilityId).flip();
            VulkanCommandRecorder.record(commandBuffer -> {
                VulkanSync.bufferBarrier(
                        commandBuffer,
                        this.visibilityBuffer.vkBuffer(),
                        0L,
                        this.visibilityBuffer.size(),
                        this.visibilityBuffer.policy().declaredAccess(),
                        VulkanSync.COMPUTE_STORAGE_WRITE
                );
                this.batchClear.bind(commandBuffer, descriptors, push);
                VK12.vkCmdDispatch(commandBuffer, (count + 127) / 128, 1, 1);
                VulkanSync.bufferBarrier(
                        commandBuffer,
                        this.visibilityBuffer.vkBuffer(),
                        0L,
                        this.visibilityBuffer.size(),
                        VulkanSync.COMPUTE_STORAGE_WRITE,
                        this.visibilityBuffer.policy().consumerAccess()
                );
            });
        }
    }

    private boolean shouldCleanGeometry() {
        long remaining = this.nodeManager.getGeometryCapacity() - this.nodeManager.getUsedGeometryCapacity();
        return remaining < 256_000_000L;
    }

    private static VoxyVulkanBuffer ensureCapacity(VoxyVulkanBuffer current, long requiredBytes) {
        if (requiredBytes <= current.size()) return current;
        long capacity = current.size();
        while (capacity < requiredBytes) capacity = Math.multiplyExact(capacity, 2L);
        int roles = VoxyVulkanBufferUsage.STORAGE_COMPUTE | VoxyVulkanBufferUsage.TRANSFER_DESTINATION;
        VoxyVulkanBuffer replacement = VoxyVulkanBuffer.create(
                capacity,
                VoxyVulkanBufferUsage.of(roles),
                "Voxy NodeCleaner ID updates"
        );
        current.close();
        return replacement;
    }

    public VoxyVulkanBuffer visibilityBuffer() {
        this.ensureOpen();
        return this.visibilityBuffer;
    }

    public int visibilityId() {
        return this.visibilityId;
    }

    /**
     * Completes every queued cleanup readback while the AsyncNodeManager is still accepting
     * removal batches. The formal owner must call this before stopping the node manager.
     */
    public void flushDownloads() {
        this.ensureOpen();
        this.downloads.flushWaitClear();
    }

    private void ensureOpen() {
        if (this.closed) throw new IllegalStateException("Vulkan NodeCleaner is closed");
    }

    @Override
    public void close() {
        if (this.closed) return;
        this.closed = true;
        this.uploads.commit();
        // The formal renderer flushes callbacks before AsyncNodeManager.stop(). Once the manager
        // has stopped, running a late callback would call addWork() on a stopped owner.
        this.downloads.waitDiscard();
        this.visibilityBuffer.close();
        this.outputBuffer.close();
        this.idListBuffer.close();
        this.sorter.close();
        this.resultTransformer.close();
        this.batchClear.close();
    }
}
