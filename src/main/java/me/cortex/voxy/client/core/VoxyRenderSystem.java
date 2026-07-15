package me.cortex.voxy.client.core;

import com.mojang.blaze3d.textures.GpuTextureView;
import me.cortex.voxy.client.RenderStatistics;
import me.cortex.voxy.client.TimingStatistics;
import me.cortex.voxy.client.VoxyClient;
import me.cortex.voxy.client.config.VoxyConfig;
import me.cortex.voxy.client.core.model.ModelBakerySubsystem;
import me.cortex.voxy.client.core.rendering.RenderDistanceTracker;
import me.cortex.voxy.client.core.rendering.VulkanViewportSelector;
import me.cortex.voxy.client.core.rendering.bounding.VulkanBoundStore;
import me.cortex.voxy.client.core.rendering.bounding.VulkanColumnStreamedBoundStore;
import me.cortex.voxy.client.core.rendering.bounding.VulkanStreamedBoundStore;
import me.cortex.voxy.client.core.rendering.building.RenderGenerationService;
import me.cortex.voxy.client.core.rendering.hierachical.AsyncNodeManager;
import me.cortex.voxy.client.core.rendering.hierachical.VulkanHierarchicalOcclusionTraverser;
import me.cortex.voxy.client.core.rendering.hierachical.VulkanNodeCleaner;
import me.cortex.voxy.client.core.rendering.section.backend.mdic.VulkanMDICSectionRenderer;
import me.cortex.voxy.client.core.rendering.section.backend.mdic.VulkanMDICViewport;
import me.cortex.voxy.client.core.rendering.section.geometry.BasicSectionGeometryData;
import me.cortex.voxy.client.core.util.GPUTiming;
import me.cortex.voxy.client.core.util.IrisUtil;
import me.cortex.voxy.client.core.vulkan.VoxyVulkanBuffer;
import me.cortex.voxy.client.core.vulkan.VoxyVulkanContext;
import me.cortex.voxy.common.Logger;
import me.cortex.voxy.common.thread.ServiceManager;
import me.cortex.voxy.common.world.WorldEngine;
import me.cortex.voxy.commonImpl.VoxyCommon;
import net.caffeinemc.mods.sodium.client.util.FogParameters;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/** The single formal Minecraft 26.2 Vulkan owner for Voxy's normal render route. */
public final class VoxyRenderSystem {
    private final WorldEngine worldIn;
    private final ModelBakerySubsystem modelService;
    private final RenderGenerationService renderGen;
    private final BasicSectionGeometryData geometryData;
    private final AsyncNodeManager nodeManager;
    private final VulkanNodeCleaner nodeCleaner;
    private final VulkanHierarchicalOcclusionTraverser traversal;
    private final RenderDistanceTracker renderDistanceTracker;
    public final VulkanStreamedBoundStore visbleSectionStream;
    private VulkanColumnStreamedBoundStore columnStreamedBoundStore;
    private final VulkanViewportSelector<VulkanMDICViewport> viewportSelector;
    private final VulkanNormalRenderPipeline pipeline;
    private final RenderProperties properties;
    private boolean shutdown;

    public VoxyRenderSystem(WorldEngine world, ServiceManager serviceManager) {
        this.worldIn = Objects.requireNonNull(world, "world");
        Objects.requireNonNull(serviceManager, "serviceManager");
        world.acquireRef();
        Logger.info("Creating formal Voxy Vulkan render system");

        this.properties = RenderProperties.getRenderProperties();
        ModelBakerySubsystem createdModelService = null;
        RenderGenerationService createdRenderGen = null;
        BasicSectionGeometryData createdGeometry = null;
        AsyncNodeManager createdNodeManager = null;
        VulkanNodeCleaner createdNodeCleaner = null;
        VulkanHierarchicalOcclusionTraverser createdTraversal = null;
        VulkanNormalRenderPipeline createdPipeline = null;
        VulkanMDICSectionRenderer createdSectionRenderer = null;
        VulkanViewportSelector<VulkanMDICViewport> createdViewportSelector = null;
        VulkanStreamedBoundStore createdVisibleSections = null;
        RenderDistanceTracker createdRenderDistanceTracker = null;
        boolean sectionRendererOwnedByPipeline = false;

        try {
            if (Minecraft.getInstance().options.renderDistance().get() < 3) {
                String message = "Voxy: Having a vanilla render distance of 2 can cause rare culling near the edge of your screen issues, please use 3 or more";
                Logger.warn(message);
                Minecraft.getInstance().gui.chatListener().handleSystemMessage(Component.literal(message), false);
            }

            createdModelService = new ModelBakerySubsystem(world.getMapper());
            createdRenderGen = new RenderGenerationService(
                    world,
                    createdModelService,
                    serviceManager,
                    false
            );
            createdGeometry = new BasicSectionGeometryData(1 << 20, RenderResourceReuse.getOrCreateGeometryBuffer());
            createdNodeManager = new AsyncNodeManager(1 << 21, createdGeometry, createdRenderGen);
            createdNodeCleaner = new VulkanNodeCleaner(createdNodeManager);
            createdTraversal = new VulkanHierarchicalOcclusionTraverser(
                    createdNodeManager,
                    createdNodeCleaner,
                    createdRenderGen,
                    this.properties
            );
            createdPipeline = new VulkanNormalRenderPipeline(
                    this.properties,
                    createdNodeManager,
                    createdNodeCleaner,
                    createdTraversal,
                    this::frexStillHasWork
            );
            createdSectionRenderer = new VulkanMDICSectionRenderer(
                    this.properties,
                    createdModelService.getStore(),
                    createdGeometry
            );
            createdPipeline.setSectionRenderer(createdSectionRenderer);
            sectionRendererOwnedByPipeline = true;
            VulkanMDICSectionRenderer viewportOwner = createdSectionRenderer;
            createdViewportSelector = new VulkanViewportSelector<>(viewportOwner::createViewport);
            createdVisibleSections = new VulkanStreamedBoundStore();

            int minSection = Objects.requireNonNull(Minecraft.getInstance().level, "client level")
                    .getMinSectionY() >> 5;
            int maxSection = (Minecraft.getInstance().level.getMaxSectionY() - 1) >> 5;
            if (VoxyCommon.IS_MINE_IN_ABYSS) {
                minSection = -8;
                maxSection = 7;
            }
            createdRenderDistanceTracker = new RenderDistanceTracker(
                    40,
                    minSection,
                    maxSection,
                    createdNodeManager::addTopLevel,
                    createdNodeManager::removeTopLevel
            );
            createdRenderDistanceTracker.setRenderDistance(
                    (int) Math.ceil(VoxyConfig.CONFIG.sectionRenderDistance + 1)
            );

            Arrays.stream(world.getMapper().getBiomeEntries()).forEach(createdModelService::addBiome);
            world.getMapper().setBiomeCallback(createdModelService::addBiome);
            world.setDirtyCallback(createdNodeManager::worldEvent);
            createdNodeManager.start();
        } catch (RuntimeException | Error exception) {
            world.setDirtyCallback(null);
            world.getMapper().setBiomeCallback(null);
            world.getMapper().setStateCallback(null);
            VulkanViewportSelector<VulkanMDICViewport> failedViewportSelector = createdViewportSelector;
            cleanup("failed Vulkan viewport selector", () -> closeIfPresent(failedViewportSelector));
            VulkanNormalRenderPipeline failedPipeline = createdPipeline;
            cleanup("failed Vulkan pipeline", () -> closeIfPresent(failedPipeline));
            if (!sectionRendererOwnedByPipeline) {
                VulkanMDICSectionRenderer failedSectionRenderer = createdSectionRenderer;
                cleanup("failed Vulkan section renderer", () -> closeIfPresent(failedSectionRenderer));
            }
            VulkanHierarchicalOcclusionTraverser failedTraversal = createdTraversal;
            cleanup("failed Vulkan traversal", () -> closeIfPresent(failedTraversal));
            VulkanNodeCleaner failedNodeCleaner = createdNodeCleaner;
            cleanup("failed Vulkan node cleaner", () -> closeIfPresent(failedNodeCleaner));
            AsyncNodeManager failedNodeManager = createdNodeManager;
            cleanup("failed async node manager", () -> {
                if (failedNodeManager != null) failedNodeManager.stop();
            });
            RenderGenerationService failedRenderGen = createdRenderGen;
            cleanup("failed render generation service", () -> {
                if (failedRenderGen != null) failedRenderGen.shutdown();
            });
            ModelBakerySubsystem failedModelService = createdModelService;
            cleanup("failed model bakery", () -> {
                if (failedModelService != null) failedModelService.shutdown();
            });
            BasicSectionGeometryData failedGeometry = createdGeometry;
            cleanup("failed Vulkan geometry", () -> releaseGeometry(failedGeometry));
            VulkanStreamedBoundStore failedVisibleSections = createdVisibleSections;
            cleanup("failed Vulkan bound store", () -> closeIfPresent(failedVisibleSections));
            world.releaseRef();
            throw exception;
        }

        this.modelService = createdModelService;
        this.renderGen = createdRenderGen;
        this.geometryData = createdGeometry;
        this.nodeManager = createdNodeManager;
        this.nodeCleaner = createdNodeCleaner;
        this.traversal = createdTraversal;
        this.pipeline = createdPipeline;
        this.viewportSelector = createdViewportSelector;
        this.visbleSectionStream = createdVisibleSections;
        this.renderDistanceTracker = createdRenderDistanceTracker;

        Logger.info(
                "Voxy Vulkan render system created with " + this.geometryData.getMaxCapacity()
                        + " geometry capacity, pipeline='" + this.pipeline.getClass().getSimpleName()
                        + "', renderer='" + VulkanMDICSectionRenderer.class.getSimpleName() + "'"
        );
    }

    public VulkanMDICViewport setupViewport(
            Matrix4fc vanillaProjection,
            Matrix4fc modelView,
            FogParameters fogParameters,
            int width,
            int height,
            double cameraX,
            double cameraY,
            double cameraZ
    ) {
        VulkanMDICViewport viewport = this.getViewport();
        if (viewport == null) return null;

        if (VoxyCommon.IS_MINE_IN_ABYSS) {
            int sector = (((int) Math.floor(cameraX) >> 4) + 512) >> 10;
            cameraX -= sector << 14;
            cameraY += (16 + (256 - 32 - sector * 30)) * 16;
        }
        if (width <= 0 || height <= 0) {
            Logger.error("Viewport width or height was zero, aborting Voxy Vulkan frame");
            return null;
        }

        viewport
                .setVanillaProjection(vanillaProjection)
                .setProjection(computeProjectionMat(this.properties, vanillaProjection))
                .setModelView(new Matrix4f(modelView))
                .setCamera(cameraX, cameraY, cameraZ)
                .setScreenSize(width, height)
                .setFogParameters(fogParameters)
                .update();
        if (VoxyClient.getOcclusionDebugState() == 0) viewport.frameId++;
        return viewport;
    }

    /** Executes the formal Vulkan normal pipeline through the Minecraft-target composite. */
    public void renderOpaque(
            VulkanMDICViewport viewport,
            GpuTextureView sourceDepth,
            GpuTextureView sourceColour,
            GpuTextureView lightmap
    ) {
        if (viewport == null) return;
        if (IrisUtil.irisShaderPackEnabled()) {
            throw new UnsupportedOperationException(
                    "Voxy Vulkan shaderpack integration belongs to Round IX and has no fallback route"
            );
        }
        Objects.requireNonNull(sourceDepth, "sourceDepth");
        Objects.requireNonNull(sourceColour, "sourceColour");
        Objects.requireNonNull(lightmap, "lightmap");
        if (sourceDepth.isClosed() || sourceColour.isClosed() || lightmap.isClosed()) {
            throw new IllegalArgumentException("Voxy Vulkan frame inputs must remain open for the frame submission");
        }
        if (viewport.width <= 0 || viewport.height <= 0) {
            throw new IllegalArgumentException("Voxy Vulkan viewport dimensions must be positive");
        }

        int sourceWidth = sourceDepth.texture().getWidth(sourceDepth.baseMipLevel());
        int sourceHeight = sourceDepth.texture().getHeight(sourceDepth.baseMipLevel());
        if (sourceWidth <= 0 || sourceHeight <= 0) {
            throw new IllegalArgumentException("Minecraft Vulkan depth target has invalid dimensions");
        }

        TimingStatistics.resetSamplers();
        TimingStatistics.all.start();
        TimingStatistics.main.start();
        GPUTiming.INSTANCE.marker();
        GPUTiming.INSTANCE.marker("RO");

        VulkanBoundStore boundStore = this.selectBoundStore();
        this.pipeline.runFrame(
                viewport,
                sourceDepth,
                sourceColour,
                sourceWidth,
                sourceHeight,
                lightmap,
                boundStore
        );

        TimingStatistics.main.stop();
        TimingStatistics.postDynamic.start();
        while (this.renderDistanceTracker.setCenterAndProcess(viewport.cameraX, viewport.cameraZ)
                && VoxyClient.isFrexActive()) {
            // Preserve the original FREX drain-until-stable contract.
        }
        TimingStatistics.H.start();
        do {
            this.modelService.tick(900_000);
        } while (VoxyClient.isFrexActive() && !this.modelService.areQueuesEmpty());
        TimingStatistics.H.stop();
        TimingStatistics.postDynamic.stop();
        GPUTiming.INSTANCE.marker();
        GPUTiming.INSTANCE.tick();
        TimingStatistics.all.stop();
    }

    private VulkanBoundStore selectBoundStore() {
        boolean frexActive = VoxyClient.isFrexActive();
        if (frexActive != (this.columnStreamedBoundStore != null)) {
            if (frexActive) {
                this.columnStreamedBoundStore = new VulkanColumnStreamedBoundStore();
            } else {
                this.columnStreamedBoundStore.close();
                this.columnStreamedBoundStore = null;
            }
        }
        return this.columnStreamedBoundStore == null ? this.visbleSectionStream : this.columnStreamedBoundStore;
    }

    private boolean frexStillHasWork() {
        if (!VoxyClient.isFrexActive()) return false;
        this.modelService.tick(100_000_000);
        // The raw Voxy command buffers only join Minecraft's current submission builder. Queue idle
        // cannot observe them until that builder is submitted, whereas the original glFinish both
        // flushed and waited. Submit through the host encoder, then wait on the same graphics queue.
        submitAndWaitGraphicsQueue();
        return this.nodeManager.hasWork()
                || this.renderGen.getTaskCount() != 0
                || !this.modelService.areQueuesEmpty();
    }

    private static void submitAndWaitGraphicsQueue() {
        VoxyVulkanContext context = VoxyVulkanContext.get();
        context.hostDevice().createCommandEncoder().submit();
        context.vulkanDevice().graphicsQueue().waitIdle();
    }

    public void setRenderDistance(float renderDistance) {
        this.renderDistanceTracker.setRenderDistance((int) Math.ceil(renderDistance + 1));
    }

    public VulkanMDICViewport getViewport() {
        if (IrisUtil.irisShadowActive()) return null;
        return this.viewportSelector.getViewport();
    }

    public void addDebugInfo(List<String> debug) {
        debug.add(
                "VkBuf [#/Mb]: [" + VoxyVulkanBuffer.getCount() + "/"
                        + (VoxyVulkanBuffer.getTotalSize() / 1_000_000) + "]"
        );
        this.modelService.addDebugData(debug);
        this.renderGen.addDebugData(debug);
        this.nodeManager.addDebug(debug);
        this.traversal.addDebug(debug);
        RenderStatistics.addDebug(debug);
        TimingStatistics.update();
        debug.add(
                "Voxy frame runtime (millis): " + TimingStatistics.dynamic.pVal() + ", "
                        + TimingStatistics.main.pVal() + ", " + TimingStatistics.postDynamic.pVal() + ", "
                        + TimingStatistics.all.pVal()
        );
        debug.add("Extra time: " + TimingStatistics.A.pVal() + ", " + TimingStatistics.B.pVal() + ", "
                + TimingStatistics.C.pVal() + ", " + TimingStatistics.D.pVal());
        debug.add("Extra 2 time: " + TimingStatistics.E.pVal() + ", " + TimingStatistics.F.pVal() + ", "
                + TimingStatistics.G.pVal() + ", " + TimingStatistics.H.pVal() + ", "
                + TimingStatistics.I.pVal());
        debug.add(GPUTiming.INSTANCE.getDebug());
    }

    public synchronized void shutdown() {
        if (this.shutdown) return;
        this.shutdown = true;
        Logger.info("Shutting down formal Voxy Vulkan render system");

        this.worldIn.setDirtyCallback(null);
        this.worldIn.getMapper().setBiomeCallback(null);
        this.worldIn.getMapper().setStateCallback(null);

        cleanup("Vulkan traversal downloads", this.traversal::flushDownloads);
        cleanup("Vulkan node-cleaner downloads", this.nodeCleaner::flushDownloads);
        cleanup("Vulkan graphics submission", VoxyRenderSystem::submitAndWaitGraphicsQueue);
        cleanup("async node manager", this.nodeManager::stop);
        cleanup("model bakery", this.modelService::shutdown);
        cleanup("render generation", this.renderGen::shutdown);
        cleanup("Vulkan viewports", this.viewportSelector::close);
        cleanup("Vulkan normal pipeline", this.pipeline::close);
        cleanup("Vulkan traversal", this.traversal::close);
        cleanup("Vulkan node cleaner", this.nodeCleaner::close);
        cleanup("Vulkan geometry", () -> releaseGeometry(this.geometryData));
        cleanup("Vulkan streamed bounds", this.visbleSectionStream::close);
        if (this.columnStreamedBoundStore != null) {
            cleanup("Vulkan column bounds", this.columnStreamedBoundStore::close);
            this.columnStreamedBoundStore = null;
        }

        this.worldIn.releaseRef();
        Logger.info("Voxy Vulkan render shutdown completed");
    }

    public WorldEngine getEngine() {
        return this.worldIn;
    }

    private static Matrix4f computeProjectionMat(RenderProperties properties, Matrix4fc base) {
        Matrix4f rawMinecraftProjection = Minecraft.getInstance().gameRenderer.gameRenderState()
                .levelRenderState.cameraRenderState.projectionMatrix;
        Matrix4f extraProjection = rawMinecraftProjection.invert(new Matrix4f()).mul(base);

        float near = getRenderDistance() <= 32.0f ? 8.0f : 16.0f;
        near = VoxyClient.disableSodiumChunkRender() ? 0.1f : near;
        float far = 16 * 3000;
        if (properties.isReverseZ()) {
            float temporary = near;
            near = far;
            far = temporary;
        }
        return extraProjection.mulLocal(
                new Matrix4f(rawMinecraftProjection)
                        .m22((properties.isZero2One() ? far : far + near) / (near - far))
                        .m32((properties.isZero2One() ? far : far + far) * near / (near - far))
        );
    }

    public static float getRenderDistance() {
        return Minecraft.getInstance().options.getEffectiveRenderDistance() * 16;
    }

    private static void releaseGeometry(BasicSectionGeometryData geometry) {
        if (geometry == null) return;
        geometry.free();
        if (geometry.isExternalGeometryBuffer) {
            RenderResourceReuse.giveBackGeometryBuffer(geometry.getGeometryBuffer());
        }
    }

    private static void closeIfPresent(AutoCloseable closeable) throws Exception {
        if (closeable != null) closeable.close();
    }

    private static void cleanup(String owner, ThrowingRunnable action) {
        try {
            action.run();
        } catch (Exception | LinkageError exception) {
            Logger.error("Error releasing " + owner, exception);
        }
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }
}
