package me.cortex.voxy.client.core;

import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vulkan.VulkanGpuTextureView;
import me.cortex.voxy.client.VoxyClient;
import me.cortex.voxy.client.core.rendering.bounding.VulkanBoundRenderer;
import me.cortex.voxy.client.core.rendering.bounding.VulkanBoundStore;
import me.cortex.voxy.client.core.rendering.hierachical.AsyncNodeManager;
import me.cortex.voxy.client.core.rendering.hierachical.VulkanHierarchicalOcclusionTraverser;
import me.cortex.voxy.client.core.rendering.hierachical.VulkanNodeCleaner;
import me.cortex.voxy.client.core.rendering.post.VulkanFinalComposite;
import me.cortex.voxy.client.core.rendering.post.VulkanSSAO;
import me.cortex.voxy.client.core.rendering.section.backend.mdic.VulkanMDICSectionRenderer;
import me.cortex.voxy.client.core.rendering.section.backend.mdic.VulkanMDICViewport;
import me.cortex.voxy.client.core.rendering.util.VulkanColorTarget;
import me.cortex.voxy.client.core.rendering.util.VulkanDepthStencilTarget;
import me.cortex.voxy.client.core.util.GPUTiming;
import me.cortex.voxy.client.core.util.IrisUtil;
import me.cortex.voxy.client.core.vulkan.VoxyVulkanContext;
import org.lwjgl.vulkan.VK12;

import java.util.Objects;
import java.util.function.BooleanSupplier;

/**
 * Vulkan owner for the original normal pipeline through final Minecraft-target composite.
 */
public final class VulkanNormalRenderPipeline implements AutoCloseable {
    private final RenderProperties properties;
    private final AsyncNodeManager nodeManager;
    private final VulkanNodeCleaner nodeCleaner;
    private final VulkanHierarchicalOcclusionTraverser traversal;
    private final BooleanSupplier frexStillHasWork;
    private final VulkanDepthStencilTarget depthStencilTarget;
    private final VulkanColorTarget colourTarget;
    private final VulkanColorTarget ssaoColourTarget;
    private final VulkanBoundRenderer boundRenderer;
    private final VulkanSSAO ssao;
    private final VulkanFinalComposite finalComposite;
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
        VulkanColorTarget createdSsaoColour = null;
        VulkanBoundRenderer createdBounds = null;
        VulkanSSAO createdSsao = null;
        VulkanFinalComposite createdFinalComposite = null;
        try {
            createdDepthStencil = new VulkanDepthStencilTarget(this.properties);
            createdColour = new VulkanColorTarget("Voxy normal terrain colour");
            createdSsaoColour = new VulkanColorTarget(
                    "Voxy normal SSAO/translucent colour",
                    VK12.VK_IMAGE_USAGE_STORAGE_BIT
            );
            createdBounds = new VulkanBoundRenderer(this.properties);
            createdSsao = new VulkanSSAO(this.properties);
            createdFinalComposite = new VulkanFinalComposite(this.properties);
        } catch (RuntimeException | Error exception) {
            if (createdFinalComposite != null) createdFinalComposite.close();
            if (createdSsao != null) createdSsao.close();
            if (createdBounds != null) createdBounds.close();
            if (createdSsaoColour != null) createdSsaoColour.close();
            if (createdColour != null) createdColour.close();
            if (createdDepthStencil != null) createdDepthStencil.close();
            throw exception;
        }
        this.depthStencilTarget = createdDepthStencil;
        this.colourTarget = createdColour;
        this.ssaoColourTarget = createdSsaoColour;
        this.boundRenderer = createdBounds;
        this.ssao = createdSsao;
        this.finalComposite = createdFinalComposite;
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
     * Executes the exact original normal-pipeline order through the host target composite.
     */
    public void runFrame(
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
        this.ssaoColourTarget.resize(viewport.width, viewport.height);
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
                // Original AbstractRenderPipeline.innerPrimaryWork advances the one global download stream here.
                VoxyVulkanContext.get().downloadStream().tick();
                this.nodeManager.tick(this.traversal.getNodeBuffer(), this.nodeCleaner);
                this.nodeCleaner.tick(this.traversal.getNodeBuffer());
                this.traversal.doTraversal(viewport);
            } while (this.frexStillHasWork.getAsBoolean());
        }

        if (occlusionDebug <= 1) {
            renderer.buildDrawCalls(viewport, this.depthStencilTarget.attachmentView());
        }

        renderer.renderTemporal(viewport, this.colourTarget, this.depthStencilTarget, lightmap);
        // The original MDIC postOpaquePreperation hook is empty for this renderer.
        GPUTiming.INSTANCE.marker("ao");
        this.ssao.compute(
                viewport,
                this.ssaoColourTarget,
                this.colourTarget,
                this.depthStencilTarget,
                sourceDepth
        );
        GPUTiming.INSTANCE.marker("RT");
        renderer.renderTranslucent(
                viewport,
                this.ssaoColourTarget,
                this.depthStencilTarget,
                lightmap
        );
        GPUTiming.INSTANCE.marker();
        this.finalComposite.render(
                viewport,
                this.ssaoColourTarget,
                this.depthStencilTarget,
                sourceDepth,
                sourceColour,
                sourceWidth,
                sourceHeight
        );
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
        this.finalComposite.close();
        this.ssao.close();
        this.boundRenderer.close();
        this.ssaoColourTarget.close();
        this.colourTarget.close();
        this.depthStencilTarget.close();
    }
}
