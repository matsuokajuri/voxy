package me.cortex.voxy.forge;

import com.mojang.blaze3d.platform.GlStateManager;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import me.cortex.voxy.common.Logger;
import me.cortex.voxy.common.thread.ServiceManager;
import me.cortex.voxy.common.world.WorldEngine;
import me.cortex.voxy.config.ForgeVoxyConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import java.util.List;

import static org.lwjgl.opengl.GL11C.glFinish;
import static org.lwjgl.opengl.GL13C.GL_TEXTURE0;
import static org.lwjgl.opengl.GL30C.glBindBufferBase;
import static org.lwjgl.opengl.GL30C.glGetIntegeri;
import static org.lwjgl.opengl.GL33C.glBindSampler;
import static org.lwjgl.opengl.GL43C.GL_SHADER_STORAGE_BUFFER;
import static org.lwjgl.opengl.GL43C.GL_SHADER_STORAGE_BUFFER_BINDING;

/**
 * Port of original {@code me.cortex.voxy.client.core.VoxyRenderSystem} for the Forge parity
 * route: the single outer lifecycle owner. The constructor is the complete original
 * construction boundary (world ref acquired first, model-bakery setup and worker start inside,
 * dirty/biome callbacks and node-manager start at the original positions, SSBO capture/restore
 * and texture/sampler clears around it) and {@link #shutdown(boolean)} mirrors the original
 * shutdown order.
 *
 * Documented Forge adaptations:
 * - Sub-owner GL construction reports error strings; they are rethrown here so the original
 *   single {@code catch (RuntimeException)} -> {@code releaseRef} -> rethrow boundary applies.
 * - Original leaks partially created GL owners when the constructor throws; the Forge port
 *   keeps the VI-round failure cleanup and frees what was created, in shutdown order.
 * - The original {@code AsyncNodeManager} split (geometry manager + geometry sync) requires
 *   explicitly attaching the render-generation result consumer before {@code start()}.
 * - {@code pipeline.setupExtraModelBakeryData(modelService)} maps to applying the Oculus
 *   custom block-state id mapping on the model factory at the same constructor position.
 * - Geometry storage is acquired from the ported {@code RenderResourceReuse} cache, matching
 *   the original owner-reuse contract across renderer rebuilds.
 */
final class ForgeOriginalVoxyRenderSystem {
    private static final int ORIGINAL_GEOMETRY_MAX_SECTION_COUNT = 1 << 20;

    private final WorldEngine worldIn;

    private ModelBakerySubsystem modelService;
    private RenderGenerationService renderGen;
    private BasicSectionGeometryData geometryData;
    private AsyncNodeManager nodeManager;
    private NodeCleaner nodeCleaner;
    private HierarchicalOcclusionTraverser traversal;

    private RenderDistanceTracker renderDistanceTracker;
    private ChunkBoundRenderer chunkBoundRenderer;

    private ViewportSelector viewportSelector;

    private ForgeOriginalVoxyRenderPipeline pipeline;
    private MDICSectionRenderer sectionRenderer;
    private final RenderProperties properties;

    ForgeOriginalVoxyRenderSystem(
            WorldEngine world,
            ServiceManager sm,
            Minecraft minecraft,
            @Nullable Object2IntMap<BlockState> customBlockStateIds,
            String customBlockStateIdSource) {
        //Keep the world loaded, NOTE: this is done FIRST, to keep and ensure that even if the rest of loading takes more
        // than timeout, we keep the world acquired
        world.acquireRef();
        Logger.info("Creating Voxy render system");

        System.gc();

        if (minecraft.options.renderDistance().get() < 3) {
            String msg = "Voxy: Having a vanilla render distance of 2 can cause rare culling near the edge of your screen issues, please use 3 or more";
            Logger.warn(msg);
            minecraft.getChatListener().handleSystemMessage(Component.literal(msg), false);
        }

        //Fking HATE EVERYTHING AAAAAAAAAAAAAAAA
        int[] oldBufferBindings = new int[10];
        for (int i = 0; i < oldBufferBindings.length; i++) {
            oldBufferBindings[i] = glGetIntegeri(GL_SHADER_STORAGE_BUFFER_BINDING, i);
        }

        try {
            //wait for opengl to be finished, this should hopefully ensure all memory allocations are free
            glFinish();
            glFinish();
            //Construction often runs right after an Oculus pipeline reload; do not let its
            // latched GL errors fail the glGetError-based build audits of the owners below.
            ModelStore.drainLatchedGlErrors("original-render-system-construction");

            this.worldIn = world;

            this.properties = RenderProperties.getRenderProperties();
            {
                this.modelService = new ModelBakerySubsystem(world.getMapper(), minecraft);
                this.renderGen = new RenderGenerationService(world, this.modelService, sm, false);

                this.geometryData = new BasicSectionGeometryData(ORIGINAL_GEOMETRY_MAX_SECTION_COUNT);
                String geometryDataError = this.geometryData.buildOnRenderThread();
                if (!"none".equals(geometryDataError)) {
                    throw new IllegalStateException(geometryDataError);
                }
                this.nodeManager = new AsyncNodeManager(1 << 21, this.geometryData, this.renderGen);
                this.nodeCleaner = new NodeCleaner(this.nodeManager);
                this.nodeCleaner.buildOnRenderThread();
                this.traversal = new HierarchicalOcclusionTraverser(this.nodeManager, this.nodeCleaner, this.renderGen);
            }

            this.pipeline = new ForgeOriginalVoxyRenderPipeline(this.properties);
            //Configure the model service (original pipeline.setupExtraModelBakeryData)
            this.modelService.factory.setCustomBlockStateMapping(customBlockStateIds, customBlockStateIdSource);

            //Late stage traversal compile for shaders with taa
            String traversalError = this.traversal.lateStageCompile(this.properties, this.pipeline);
            if (!"none".equals(traversalError)) {
                throw new IllegalStateException(traversalError);
            }

            this.sectionRenderer = new MDICSectionRenderer();
            String sectionRendererError = this.sectionRenderer.buildOnRenderThread(this.pipeline);
            if (!"none".equals(sectionRendererError)) {
                throw new IllegalStateException(sectionRendererError);
            }
            this.viewportSelector = new ViewportSelector(
                    () -> new MDICViewport(this.properties, ORIGINAL_GEOMETRY_MAX_SECTION_COUNT));

            {
                int minSec = (minecraft.level.getMinBuildHeight() >> 4) >> 5;
                int maxSec = ((minecraft.level.getMaxBuildHeight() >> 4) - 1) >> 5;

                this.renderDistanceTracker = new RenderDistanceTracker(40,
                        minSec,
                        maxSec,
                        this.nodeManager::addTopLevel,
                        this.nodeManager::removeTopLevel);

                this.setRenderDistance(ForgeVoxyConfig.ORIGINAL_VOXY_SECTION_RENDER_DISTANCE.get().floatValue());
            }

            this.chunkBoundRenderer = new ChunkBoundRenderer(this.properties, this.pipeline);

            //Forge adaptation: original attaches the world dirty/biome callbacks and starts the
            // node manager in the middle of the constructor. On Forge the ingest services keep
            // running through an owner rebuild (shaderpack toggle / reload), so attaching them
            // there floods the mesh service against an empty model bakery while the traversal
            // and MDIC owners are still compiling. The callbacks stay inside the constructor
            // boundary but are attached last, once every owner they feed exists.
            world.setDirtyCallback(this.nodeManager::worldEvent);
            for (var biome : world.getMapper().setBiomeCallbackAndGetSnapshot(this.modelService::addBiome)) {
                this.modelService.addBiome(biome);
            }
            this.nodeManager.start();

            Logger.info("Voxy render system created with " + ORIGINAL_GEOMETRY_MAX_SECTION_COUNT
                    + " geometry capacity, using pipeline '" + this.pipeline.getClass().getSimpleName()
                    + "' with renderer '" + this.sectionRenderer.getClass().getSimpleName() + "'");
        } catch (RuntimeException e) {
            //Original releases the world ref and rethrows; the Forge port additionally frees the
            // partially created owners (retained VI-round failure cleanup) before doing so.
            this.freeOnConstructionFailure(world);
            world.releaseRef();//If something goes wrong, we must release the world first
            throw e;
        }

        for (int i = 0; i < oldBufferBindings.length; i++) {
            glBindBufferBase(GL_SHADER_STORAGE_BUFFER, i, oldBufferBindings[i]);
        }

        for (int i = 0; i < 12; i++) {
            GlStateManager._activeTexture(GL_TEXTURE0 + i);
            GlStateManager._bindTexture(0);
            glBindSampler(i, 0);
        }
    }

    void setRenderDistance(float renderDistance) {
        this.renderDistanceTracker.setRenderDistance((int) Math.ceil(renderDistance + 1));//the +1 is to cover the outer ring of chunks when rendering a circle
    }

    /**
     * Mirrors original {@code VoxyRenderSystem.shutdown()}: flush the download stream, detach
     * callbacks, stop the node manager, shut down the model bakery and render generation, free
     * the render owners, free the pipeline last, flush again, release the world ref.
     *
     * @param flushDownloadStream Forge generation adaptation: the download stream is a global
     *                            singleton, so the flush is skipped when a newer lifecycle
     *                            generation already owns it.
     * @return whether the download stream was flushed.
     */
    boolean shutdown(boolean flushDownloadStream) {
        boolean flushed = false;
        Logger.info("Flushing download stream");
        if (flushDownloadStream && DownloadStream.isReady()) {
            DownloadStream.instance().flushWaitClear();
            flushed = true;
        }
        Logger.info("Shutting down rendering");
        try {
            //Cleanup callbacks
            this.worldIn.setDirtyCallback(null);
            this.worldIn.getMapper().setBiomeCallback(null);
            this.worldIn.getMapper().setStateCallback(null);

            this.nodeManager.stop();

            this.modelService.shutdown();
            this.renderGen.shutdown();
            this.traversal.free();
            this.nodeCleaner.free();
            this.geometryData.free();

            this.chunkBoundRenderer.free();

            this.viewportSelector.free();
        } catch (Exception e) {Logger.error("Error shutting down renderer components", e);}
        Logger.info("Shutting down render pipeline");
        try {
            this.sectionRenderer.free();
            this.pipeline.free();
        } catch (Exception e){Logger.error("Error releasing render pipeline", e);}

        Logger.info("Flushing download stream");
        if (flushDownloadStream && DownloadStream.isReady()) {
            DownloadStream.instance().flushWaitClear();
            flushed = true;
        }

        //Release hold on the world
        this.worldIn.releaseRef();
        Logger.info("Render shutdown completed");
        return flushed;
    }

    private void freeOnConstructionFailure(WorldEngine world) {
        try {
            world.setDirtyCallback(null);
            world.getMapper().setBiomeCallback(null);
            world.getMapper().setStateCallback(null);
        } catch (Exception e) {Logger.error("Error detaching callbacks after failed render system construction", e);}
        try {if (this.nodeManager != null) this.nodeManager.stop();} catch (Exception e) {Logger.error("Error stopping node manager after failed render system construction", e);}
        try {if (this.modelService != null) this.modelService.shutdown();} catch (Exception e) {Logger.error("Error shutting down model bakery after failed render system construction", e);}
        try {if (this.renderGen != null) this.renderGen.shutdown();} catch (Exception e) {Logger.error("Error shutting down render generation after failed render system construction", e);}
        try {if (this.traversal != null) this.traversal.free();} catch (Exception e) {Logger.error("Error freeing traversal after failed render system construction", e);}
        try {if (this.nodeCleaner != null) this.nodeCleaner.free();} catch (Exception e) {Logger.error("Error freeing node cleaner after failed render system construction", e);}
        try {if (this.geometryData != null) this.geometryData.free();} catch (Exception e) {Logger.error("Error freeing geometry data after failed render system construction", e);}
        try {if (this.chunkBoundRenderer != null) this.chunkBoundRenderer.free();} catch (Exception e) {Logger.error("Error freeing chunk-bound renderer after failed render system construction", e);}
        try {if (this.viewportSelector != null) this.viewportSelector.free();} catch (Exception e) {Logger.error("Error freeing viewport selector after failed render system construction", e);}
        try {if (this.sectionRenderer != null) this.sectionRenderer.free();} catch (Exception e) {Logger.error("Error freeing section renderer after failed render system construction", e);}
        try {if (this.pipeline != null) this.pipeline.free();} catch (Exception e) {Logger.error("Error freeing render pipeline after failed render system construction", e);}
    }

    WorldEngine getEngine() {
        return this.worldIn;
    }

    void addDebugInfo(List<String> debug) {
        this.modelService.addDebugData(debug);
        this.renderGen.addDebugData(debug);
        this.nodeManager.addDebug(debug);
        this.sectionRenderer.addDebug(debug);
        this.traversal.addDebug(debug);
        RenderStatistics.addDebug(debug);
        this.pipeline.addDebug(debug);
    }

    ModelBakerySubsystem modelService() {
        return this.modelService;
    }

    RenderGenerationService renderGenerationService() {
        return this.renderGen;
    }

    BasicSectionGeometryData geometryData() {
        return this.geometryData;
    }

    AsyncNodeManager nodeManager() {
        return this.nodeManager;
    }

    NodeCleaner nodeCleaner() {
        return this.nodeCleaner;
    }

    HierarchicalOcclusionTraverser traversal() {
        return this.traversal;
    }

    RenderDistanceTracker renderDistanceTracker() {
        return this.renderDistanceTracker;
    }

    ChunkBoundRenderer chunkBoundRenderer() {
        return this.chunkBoundRenderer;
    }

    ViewportSelector viewportSelector() {
        return this.viewportSelector;
    }

    ForgeOriginalVoxyRenderPipeline renderPipeline() {
        return this.pipeline;
    }

    MDICSectionRenderer sectionRenderer() {
        return this.sectionRenderer;
    }
}
