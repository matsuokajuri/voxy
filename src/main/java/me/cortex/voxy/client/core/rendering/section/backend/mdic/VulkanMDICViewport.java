package me.cortex.voxy.client.core.rendering.section.backend.mdic;

import me.cortex.voxy.client.core.RenderProperties;
import me.cortex.voxy.client.core.rendering.VulkanViewport;
import me.cortex.voxy.client.core.rendering.hierachical.VulkanHierarchicalOcclusionTraverser;
import me.cortex.voxy.client.core.vulkan.VoxyVulkanBuffer;
import me.cortex.voxy.client.core.vulkan.VoxyVulkanBufferUsage;

/** Vulkan buffer owner with the same capacities and field roles as the original MDICViewport. */
public final class VulkanMDICViewport extends VulkanViewport<VulkanMDICViewport> {
    public final VoxyVulkanBuffer drawCountCallBuffer;
    public final VoxyVulkanBuffer drawCallBuffer;
    public final VoxyVulkanBuffer positionScratchBuffer;
    public final VoxyVulkanBuffer indirectLookupBuffer;
    public final VoxyVulkanBuffer visibilityBuffer;

    public VulkanMDICViewport(RenderProperties properties, int maxSectionCount) {
        super(properties);

        int computeIndirectRoles = VoxyVulkanBufferUsage.STORAGE_COMPUTE
                | VoxyVulkanBufferUsage.INDIRECT
                | VoxyVulkanBufferUsage.TRANSFER_DESTINATION;
        int computeGraphicsRoles = VoxyVulkanBufferUsage.STORAGE_COMPUTE
                | VoxyVulkanBufferUsage.STORAGE_GRAPHICS
                | VoxyVulkanBufferUsage.TRANSFER_DESTINATION;

        VoxyVulkanBuffer createdDrawCounts = null;
        VoxyVulkanBuffer createdDrawCalls = null;
        VoxyVulkanBuffer createdPositionScratch = null;
        VoxyVulkanBuffer createdIndirectLookup = null;
        VoxyVulkanBuffer createdVisibility = null;
        try {
            createdDrawCounts = createZeroed(1024L, computeIndirectRoles, "Voxy MDIC draw counts");
            createdDrawCalls = createZeroed(
                    5L * Integer.BYTES * (
                            MDICSectionRenderer.OPAQUE_DRAW_COUNT
                                    + MDICSectionRenderer.TRANSLUCENT_DRAW_COUNT
                                    + MDICSectionRenderer.TEMPORAL_DRAW_COUNT
                    ),
                    computeIndirectRoles,
                    "Voxy MDIC draw commands"
            );
            createdPositionScratch = createZeroed(
                    8L * 400_000L,
                    computeGraphicsRoles,
                    "Voxy MDIC position scratch"
            );
            createdIndirectLookup = VoxyVulkanBuffer.create(
                    VulkanHierarchicalOcclusionTraverser.MAX_QUEUE_SIZE * 4L + 4L,
                    VoxyVulkanBufferUsage.of(computeGraphicsRoles),
                    "Voxy MDIC section lookup"
            );
            createdVisibility = VoxyVulkanBuffer.create(
                    Math.multiplyExact(maxSectionCount, 4L),
                    VoxyVulkanBufferUsage.of(computeGraphicsRoles),
                    "Voxy MDIC visibility"
            );
        } catch (RuntimeException | Error exception) {
            if (createdVisibility != null) createdVisibility.close();
            if (createdIndirectLookup != null) createdIndirectLookup.close();
            if (createdPositionScratch != null) createdPositionScratch.close();
            if (createdDrawCalls != null) createdDrawCalls.close();
            if (createdDrawCounts != null) createdDrawCounts.close();
            this.hiZBuffer.close();
            throw exception;
        }
        this.drawCountCallBuffer = createdDrawCounts;
        this.drawCallBuffer = createdDrawCalls;
        this.positionScratchBuffer = createdPositionScratch;
        this.indirectLookupBuffer = createdIndirectLookup;
        this.visibilityBuffer = createdVisibility;
    }

    private static VoxyVulkanBuffer createZeroed(long size, int roles, String label) {
        return VoxyVulkanBuffer.create(size, VoxyVulkanBufferUsage.of(roles), label);
    }

    @Override
    public VoxyVulkanBuffer getRenderList() {
        return this.indirectLookupBuffer;
    }

    @Override
    protected void close0() {
        this.visibilityBuffer.close();
        this.indirectLookupBuffer.close();
        this.drawCountCallBuffer.close();
        this.drawCallBuffer.close();
        this.positionScratchBuffer.close();
    }
}
