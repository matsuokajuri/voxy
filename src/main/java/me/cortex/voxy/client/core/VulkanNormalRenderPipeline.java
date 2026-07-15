package me.cortex.voxy.client.core;

import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vulkan.VulkanGpuTextureView;
import me.cortex.voxy.client.VoxyClient;
import me.cortex.voxy.client.core.rendering.bounding.VulkanBoundRenderer;
import me.cortex.voxy.client.core.rendering.bounding.VulkanBoundStore;
import me.cortex.voxy.client.core.rendering.hierachical.AsyncNodeManager;
import me.cortex.voxy.client.core.rendering.hierachical.VulkanHierarchicalOcclusionTraverser;
import me.cortex.voxy.client.core.rendering.hierachical.VulkanNodeCleaner;
import me.cortex.voxy.client.core.rendering.section.backend.mdic.VulkanMDICSectionRenderer;
import me.cortex.voxy.client.core.rendering.section.backend.mdic.VulkanMDICViewport;
import me.cortex.voxy.client.core.rendering.util.VulkanColorTarget;
import me.cortex.voxy.client.core.rendering.util.VulkanDepthStencilTarget;
import me.cortex.voxy.client.core.util.IrisUtil;

import java.util.Objects;
import java.util.function.BooleanSupplier;

/**
 * Vulkan owner for the normal pipeline through the original temporal terrain pass.
 * SSAO, translucent terrain and final composite remain the next Round VII contract.
 */
public final class VulkanNormalRenderPipeline implements AutoCloseable {
    private final RenderProperties properties;
    private final AsyncNodeManager nodeManager;
    private final VulkanNodeCleaner nodeCleaner;
    private final VulkanHierarchicalOcclusionTraverser traversal;
    private final BooleanSupplier frexStillHasWork;
    private final VulkanDepthStencilTarget depthStencilTarget;
    private final VulkanColorTarget colourTarget;
    private final VulkanBoundRenderer boundRenderer;
    private VulkanMDICSectionRenderer sectionRenderer;
    private boolean closed;

    public VulkanNormalRenderPipeline(
            RenderProperties properties,
            AsyncNodeManager nodeManager,
            VulkanNodeCleaner nodeCleaner,
            VulkanHierarchicalOcclusionTraverser traversal,
            BooleanSupplier frexStillHasWork
    ) {
        this.properties = Objects.requireNonNull(properties, "properties");
        this.nodeManager = Objects.requireNonNull(nodeManager, "nodeManager");
        this.nodeCleaner = Objects.requireNonNull(nodeCleaner, "nodeCleaner");
        this.traversal = Objects.requireNonNull(traversal, "traversal");
        this.frexStillHasWork = Objects.requireNonNull(frexStillHasWork, "frexStillHasWork");

        VulkanDepthStencilTarget createdDepthStencil = null;
        VulkanColorTarget createdColour = null;
        VulkanBoundRenderer createdBounds = null;
        try {
            createdDepthStencil = new VulkanDepthStencilTarget(this.properties);
            createdColour = new VulkanColorTarget("Voxy normal terrain colour");
            createdBounds = new VulkanBoundRenderer(this.properties);
        } catch (RuntimeException | Error exception) {
            if (createdBounds != null) createdBounds.close();
            if (createdColour != null) createdColour.close();
            if (createdDepthStencil != null) createdDepthStencil.close();
            throw exception;
        }
        this.depthStencilTarget = createdDepthStencil;
        this.colourTarget = createdColour;
        this.boundRenderer = createdBounds;
    }

    /** Mirrors AbstractRenderPipeline.setSectionRenderer's one-time construction-cycle handoff. */
    public void setSectionRenderer(VulkanMDICSectionRenderer sectionRenderer) {
        this.ensureOpen();
        if (this.sectionRenderer != null) {
            throw new IllegalStateException("Vulkan section renderer is already attached to the normal pipeline");
        }
        this.sectionRenderer = Objects.requireNonNull(sectionRenderer, "sectionRenderer");
    }

    /**
     * Executes the exact normal-pipeline prefix ending after the temporal indirect-count draw.
     */
    public FrameTargets runFrontHalf(
            VulkanMDICViewport viewport,
            GpuTextureView sourceDepth,
            GpuTextureView sourceColour,
            int sourceWidth,
            int sourceHeight,
            GpuTextureView lightmap,
            VulkanBoundStore boundStore
    ) {
        this.ensureOpen();
        VulkanMDICSectionRenderer renderer = this.requireSectionRenderer();
        Objects.requireNonNull(viewport, "viewport");
        Objects.requireNonNull(sourceDepth, "sourceDepth");
        Objects.requireNonNull(sourceColour, "sourceColour");
        Objects.requireNonNull(lightmap, "lightmap");
        Objects.requireNonNull(boundStore, "boundStore");
        if (viewport.width <= 0 || viewport.height <= 0) {
            throw new IllegalArgumentException("Voxy Vulkan viewport dimensions must be positive");
        }
        validateHostFrameTargets(sourceDepth, sourceColour, sourceWidth, sourceHeight);

        // VoxyRenderSystem renders/clears the chunk depth bounds before entering AbstractRenderPipeline.runPipeline.
        if (!VoxyClient.disableSodiumChunkRender() && !IrisUtil.irisShadowActive()) {
            this.boundRenderer.render(viewport, boundStore);
        } else {
            viewport.depthBoundingBuffer.clear(this.properties.inverseClearDepth());
        }

        // NormalRenderPipeline.setup: resize offscreen owners, then rebuild the Minecraft depth/stencil mask.
        this.colourTarget.resize(viewport.width, viewport.height);
        this.depthStencilTarget.setup(
                sourceDepth,
                sourceWidth,
                sourceHeight,
                viewport.width,
                viewport.height
        );

        renderer.renderOpaque(viewport, this.colourTarget, this.depthStencilTarget, lightmap);

        int occlusionDebug = VoxyClient.getOcclusionDebugState();
        if (occlusionDebug == 0) {
            viewport.hiZBuffer.buildMipChain(
                    this.depthStencilTarget.depthSampledView(),
                    viewport.width,
                    viewport.height
            );
            do {
                this.nodeManager.tick(this.traversal.getNodeBuffer(), this.nodeCleaner);
                this.nodeCleaner.tick(this.traversal.getNodeBuffer());
                this.traversal.doTraversal(viewport);
            } while (this.frexStillHasWork.getAsBoolean());
        }

        if (occlusionDebug <= 1) {
            renderer.buildDrawCalls(viewport, this.depthStencilTarget.attachmentView());
        }

        renderer.renderTemporal(viewport, this.colourTarget, this.depthStencilTarget, lightmap);
        return new FrameTargets(this.colourTarget, this.depthStencilTarget);
    }

    /**
     * Preserves AbstractRenderPipeline.runPipeline's source-depth/source-colour handoff. The colour
     * view is consumed by the later composite stage; validating it here keeps that host owner in the
     * same formal frame call instead of reacquiring or guessing a Minecraft target later.
     */
    private static void validateHostFrameTargets(
            GpuTextureView sourceDepth,
            GpuTextureView sourceColour,
            int sourceWidth,
            int sourceHeight
    ) {
        if (!(sourceDepth instanceof VulkanGpuTextureView)
                || !(sourceColour instanceof VulkanGpuTextureView)) {
            throw new IllegalArgumentException("Voxy normal pipeline requires Minecraft Vulkan target views");
        }
        if (sourceDepth.isClosed() || sourceColour.isClosed()) {
            throw new IllegalArgumentException("Voxy normal pipeline host target views must remain open");
        }
        if (!sourceDepth.texture().getFormat().hasDepthAspect()
                || !sourceColour.texture().getFormat().hasColorAspect()) {
            throw new IllegalArgumentException("Voxy normal pipeline requires depth and colour host aspects");
        }
        if (sourceDepth.mipLevels() != 1
                || sourceColour.mipLevels() != 1
                || sourceDepth.texture().getDepthOrLayers() != 1
                || sourceColour.texture().getDepthOrLayers() != 1) {
            throw new IllegalArgumentException("Voxy normal pipeline host targets must be single-mip, single-layer views");
        }
        if (sourceWidth <= 0 || sourceHeight <= 0
                || sourceDepth.getWidth(0) != sourceWidth
                || sourceDepth.getHeight(0) != sourceHeight
                || sourceColour.getWidth(0) != sourceWidth
                || sourceColour.getHeight(0) != sourceHeight) {
            throw new IllegalArgumentException("Voxy normal pipeline host depth/colour dimensions must match");
        }
    }

    public VulkanColorTarget colourTarget() {
        this.ensureOpen();
        return this.colourTarget;
    }

    public VulkanDepthStencilTarget depthStencilTarget() {
        this.ensureOpen();
        return this.depthStencilTarget;
    }

    private VulkanMDICSectionRenderer requireSectionRenderer() {
        if (this.sectionRenderer == null) {
            throw new IllegalStateException("Vulkan normal pipeline has no section renderer");
        }
        return this.sectionRenderer;
    }

    private void ensureOpen() {
        if (this.closed) throw new IllegalStateException("Voxy Vulkan normal pipeline is closed");
    }

    @Override
    public void close() {
        if (this.closed) return;
        this.closed = true;
        if (this.sectionRenderer != null) this.sectionRenderer.close();
        this.boundRenderer.close();
        this.colourTarget.close();
        this.depthStencilTarget.close();
    }

    public record FrameTargets(VulkanColorTarget colour, VulkanDepthStencilTarget depthStencil) {
        public FrameTargets {
            Objects.requireNonNull(colour, "colour");
            Objects.requireNonNull(depthStencil, "depthStencil");
        }
    }
}
