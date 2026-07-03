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
import java.util.Arrays;

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
 * - There is no Forge {@code RenderResourceReuse} geometry-buffer cache; the geometry buffer
 *   is owned per lifecycle.
 */
final class ForgeOriginalVoxyRenderSystem {
    private static final int ORIGINAL_GEOMETRY_MAX_SECTION_COUNT = 1 << 20;

    private final WorldEngine worldIn;

    private ForgeOriginalVoxyModelBakerySubsystem modelService;
    private ForgeOriginalVoxyRenderGenerationService renderGen;
    private ForgeOriginalVoxyBasicSectionGeometryData geometryData;
    private ForgeOriginalVoxyBasicAsyncGeometryManager geometryManager;
    private ForgeOriginalVoxyAsyncNodeGeometrySync nodeManager;
    private ForgeOriginalVoxyNodeCleaner nodeCleaner;
    private ForgeOriginalVoxyHierarchicalOcclusionTraverser traversal;

    private ForgeOriginalVoxyRenderDistanceTracker renderDistanceTracker;
    private ForgeOriginalVoxyChunkBoundRenderer chunkBoundRenderer;

    private ForgeOriginalVoxyViewportSelector viewportSelector;

    private ForgeOriginalVoxyRenderPipeline pipeline;
    private ForgeOriginalVoxyMdicSectionRenderer sectionRenderer;
    private final ForgeOriginalVoxyRenderProperties properties;

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

            this.worldIn = world;

            this.properties = ForgeOriginalVoxyRenderProperties.getRenderProperties();
            {
                this.modelService = new ForgeOriginalVoxyModelBakerySubsystem(world.getMapper(), minecraft);
                this.renderGen = new ForgeOriginalVoxyRenderGenerationService(world, this.modelService, sm, false);

                this.geometryData = new ForgeOriginalVoxyBasicSectionGeometryData(ORIGINAL_GEOMETRY_MAX_SECTION_COUNT);
                String geometryDataError = this.geometryData.buildOnRenderThread();
                if (!"none".equals(geometryDataError)) {
                    throw new IllegalStateException(geometryDataError);
                }
                this.geometryManager = new ForgeOriginalVoxyBasicAsyncGeometryManager(
                        ORIGINAL_GEOMETRY_MAX_SECTION_COUNT,
                        this.geometryData.geometryCapacityBytes());

                this.nodeManager = new ForgeOriginalVoxyAsyncNodeGeometrySync(this.geometryManager, this.geometryData, this.renderGen);
                //Original AsyncNodeManager wires itself into the render generation service during
                // construction; the Forge split attaches the consumer here, before the world dirty
                // callback below can route the first build tasks to the service workers.
                this.renderGen.setResultConsumer(this.nodeManager::submitGeometryResult);
                this.nodeCleaner = new ForgeOriginalVoxyNodeCleaner(this.nodeManager.maxNodeCount());
                this.nodeCleaner.buildOnRenderThread();
                this.traversal = new ForgeOriginalVoxyHierarchicalOcclusionTraverser(this.nodeManager, this.nodeCleaner, this.renderGen);
            }

            this.pipeline = new ForgeOriginalVoxyRenderPipeline(this.properties);
            //Configure the model service (original pipeline.setupExtraModelBakeryData)
            this.modelService.factory.setCustomBlockStateMapping(customBlockStateIds, customBlockStateIdSource);

            //Late stage traversal compile for shaders with taa
            String traversalError = this.traversal.buildOnRenderThread(this.properties, this.pipeline);
            if (!"none".equals(traversalError)) {
                throw new IllegalStateException(traversalError);
            }

            this.sectionRenderer = new ForgeOriginalVoxyMdicSectionRenderer();
            String sectionRendererError = this.sectionRenderer.buildOnRenderThread(this.pipeline);
            if (!"none".equals(sectionRendererError)) {
                throw new IllegalStateException(sectionRendererError);
            }
            this.viewportSelector = new ForgeOriginalVoxyViewportSelector(
                    () -> new ForgeOriginalVoxyMdicViewport(this.properties, ORIGINAL_GEOMETRY_MAX_SECTION_COUNT));

            {
                int minSec = (minecraft.level.getMinBuildHeight() >> 4) >> 5;
                int maxSec = ((minecraft.level.getMaxBuildHeight() >> 4) - 1) >> 5;

                this.renderDistanceTracker = new ForgeOriginalVoxyRenderDistanceTracker(40,
                        minSec,
                        maxSec,
                        this.nodeManager::addTopLevel,
                        this.nodeManager::removeTopLevel);

                this.setRenderDistance(ForgeVoxyConfig.ORIGINAL_VOXY_SECTION_RENDER_DISTANCE.get().floatValue());
            }

            this.chunkBoundRenderer = new ForgeOriginalVoxyChunkBoundRenderer(this.properties, this.pipeline);

            //Forge adaptation: original attaches the world dirty/biome callbacks and starts the
            // node manager in the middle of the constructor. On Forge the ingest services keep
            // running through an owner rebuild (shaderpack toggle / reload), so attaching them
            // there floods the mesh service against an empty model bakery while the traversal
            // and MDIC owners are still compiling. The callbacks stay inside the constructor
            // boundary but are attached last, once every owner they feed exists.
            world.setDirtyCallback(this.nodeManager::worldEvent);
            Arrays.stream(world.getMapper().getBiomeEntries()).forEach(this.modelService::addBiome);
            world.getMapper().setBiomeCallback(this.modelService::addBiome);
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
        if (flushDownloadStream && ForgeOriginalVoxyDownloadStream.isReady()) {
            ForgeOriginalVoxyDownloadStream.instance().flushWaitClear();
            flushed = true;
        }
        Logger.info("Shutting down rendering");
        try {
            //Cleanup callbacks
            this.worldIn.setDirtyCallback(null);
            this.worldIn.getMapper().setBiomeCallback(null);
            this.worldIn.getMapper().setStateCallback(null);

            this.nodeManager.stopOnRenderThread();

            this.modelService.shutdown();
            this.renderGen.shutdown();
            this.traversal.freeOnRenderThread();
            this.nodeCleaner.freeOnRenderThread();
            this.geometryManager.clear();
            this.geometryData.freeOnRenderThread();

            this.chunkBoundRenderer.freeOnRenderThread();

            this.viewportSelector.free();
        } catch (Exception e) {Logger.error("Error shutting down renderer components", e);}
        Logger.info("Shutting down render pipeline");
        try {
            this.sectionRenderer.freeOnRenderThread();
            this.pipeline.freeOnRenderThread();
        } catch (Exception e){Logger.error("Error releasing render pipeline", e);}

        Logger.info("Flushing download stream");
        if (flushDownloadStream && ForgeOriginalVoxyDownloadStream.isReady()) {
            ForgeOriginalVoxyDownloadStream.instance().flushWaitClear();
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
        try {if (this.nodeManager != null) this.nodeManager.stopOnRenderThread();} catch (Exception e) {Logger.error("Error stopping node manager after failed render system construction", e);}
        try {if (this.modelService != null) this.modelService.shutdown();} catch (Exception e) {Logger.error("Error shutting down model bakery after failed render system construction", e);}
        try {if (this.renderGen != null) this.renderGen.shutdown();} catch (Exception e) {Logger.error("Error shutting down render generation after failed render system construction", e);}
        try {if (this.traversal != null) this.traversal.freeOnRenderThread();} catch (Exception e) {Logger.error("Error freeing traversal after failed render system construction", e);}
        try {if (this.nodeCleaner != null) this.nodeCleaner.freeOnRenderThread();} catch (Exception e) {Logger.error("Error freeing node cleaner after failed render system construction", e);}
        try {if (this.geometryManager != null) this.geometryManager.clear();} catch (Exception e) {Logger.error("Error clearing geometry manager after failed render system construction", e);}
        try {if (this.geometryData != null) this.geometryData.freeOnRenderThread();} catch (Exception e) {Logger.error("Error freeing geometry data after failed render system construction", e);}
        try {if (this.chunkBoundRenderer != null) this.chunkBoundRenderer.freeOnRenderThread();} catch (Exception e) {Logger.error("Error freeing chunk-bound renderer after failed render system construction", e);}
        try {if (this.viewportSelector != null) this.viewportSelector.free();} catch (Exception e) {Logger.error("Error freeing viewport selector after failed render system construction", e);}
        try {if (this.sectionRenderer != null) this.sectionRenderer.freeOnRenderThread();} catch (Exception e) {Logger.error("Error freeing section renderer after failed render system construction", e);}
        try {if (this.pipeline != null) this.pipeline.freeOnRenderThread();} catch (Exception e) {Logger.error("Error freeing render pipeline after failed render system construction", e);}
    }

    WorldEngine getEngine() {
        return this.worldIn;
    }

    ForgeOriginalVoxyModelBakerySubsystem modelService() {
        return this.modelService;
    }

    ForgeOriginalVoxyRenderGenerationService renderGenerationService() {
        return this.renderGen;
    }

    ForgeOriginalVoxyBasicSectionGeometryData geometryData() {
        return this.geometryData;
    }

    ForgeOriginalVoxyBasicAsyncGeometryManager geometryManager() {
        return this.geometryManager;
    }

    ForgeOriginalVoxyAsyncNodeGeometrySync nodeManager() {
        return this.nodeManager;
    }

    ForgeOriginalVoxyNodeCleaner nodeCleaner() {
        return this.nodeCleaner;
    }

    ForgeOriginalVoxyHierarchicalOcclusionTraverser traversal() {
        return this.traversal;
    }

    ForgeOriginalVoxyRenderDistanceTracker renderDistanceTracker() {
        return this.renderDistanceTracker;
    }

    ForgeOriginalVoxyChunkBoundRenderer chunkBoundRenderer() {
        return this.chunkBoundRenderer;
    }

    ForgeOriginalVoxyViewportSelector viewportSelector() {
        return this.viewportSelector;
    }

    ForgeOriginalVoxyRenderPipeline renderPipeline() {
        return this.pipeline;
    }

    ForgeOriginalVoxyMdicSectionRenderer sectionRenderer() {
        return this.sectionRenderer;
    }
}
