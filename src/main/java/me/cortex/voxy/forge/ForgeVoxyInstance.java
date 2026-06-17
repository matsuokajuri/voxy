package me.cortex.voxy.forge;

import me.cortex.voxy.common.config.section.SectionSerializationStorage;
import me.cortex.voxy.common.config.storage.inmemory.MemoryStorageBackend;
import me.cortex.voxy.common.world.WorldEngine;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

public final class ForgeVoxyInstance {
    public static final ForgeVoxyInstance INSTANCE = new ForgeVoxyInstance();

    private WorldEngine activeWorld;
    private final ForgeChunkIngestManager chunkIngestManager = new ForgeChunkIngestManager(this);
    private final ForgeCpuMeshBuildManager cpuMeshBuildManager = new ForgeCpuMeshBuildManager(this);
    private final ForgeCpuMeshCache cpuMeshCache = new ForgeCpuMeshCache();
    private final ForgeVoxyBuiltSectionBuildManager builtSectionBuildManager = new ForgeVoxyBuiltSectionBuildManager(this);
    private final ForgeVoxyGeometryCache voxyGeometryCache = new ForgeVoxyGeometryCache();
    private final ForgeSectionGeometryManager sectionGeometryManager = new ForgeSectionGeometryManager();
    private final ForgeSectionGeometryConsumeManager sectionGeometryConsumeManager = new ForgeSectionGeometryConsumeManager(this);
    private final ForgeDebugMeshRenderer debugMeshRenderer = new ForgeDebugMeshRenderer(this);
    private final ForgeGpuMeshCache gpuMeshCache = new ForgeGpuMeshCache();
    private final ForgeGpuMeshUploadManager gpuMeshUploadManager = new ForgeGpuMeshUploadManager(this);
    private final ForgeGpuGeometryVisualizationCache gpuGeometryVisualizationCache = new ForgeGpuGeometryVisualizationCache();
    private final ForgeGpuGeometryReadbackMeshCache gpuGeometryReadbackMeshCache = new ForgeGpuGeometryReadbackMeshCache();
    private final ForgeGpuGeometryReadbackMeshRefreshManager gpuGeometryReadbackMeshRefreshManager = new ForgeGpuGeometryReadbackMeshRefreshManager(this);
    private final ForgeGpuGeometryReadbackDebugRenderer gpuGeometryReadbackDebugRenderer = new ForgeGpuGeometryReadbackDebugRenderer(this);
    private final ForgeGpuGeometryUploadManager gpuGeometryUploadManager = new ForgeGpuGeometryUploadManager(this);
    private final ForgeSimpleGpuMeshRenderer simpleGpuMeshRenderer = new ForgeSimpleGpuMeshRenderer(this);
    private final ForgeDirectGpuGeometryRenderer directGpuGeometryRenderer = new ForgeDirectGpuGeometryRenderer(this);
    private final ForgeMdicCommandManager mdicCommandManager = new ForgeMdicCommandManager(this);
    private final ForgeMdicDebugRenderer mdicDebugRenderer = new ForgeMdicDebugRenderer(this);
    private final ForgeModelBridgeReadiness modelBridgeReadiness = new ForgeModelBridgeReadiness(this);
    private final ForgeModelStoreSkeleton modelStoreSkeleton = new ForgeModelStoreSkeleton(this);
    private final ForgeModelStoreLayoutAuditor modelStoreLayoutAuditor = new ForgeModelStoreLayoutAuditor();
    private final ForgeModelBridgeResourceReloadTracker modelBridgeResourceReloadTracker = new ForgeModelBridgeResourceReloadTracker(this);
    private final ForgeBakedModelBridge bakedModelBridge = new ForgeBakedModelBridge(this);
    private final ForgeRealModelStoreSample realModelStoreSample = new ForgeRealModelStoreSample(this);
    private final ForgeModelSampleSet modelSampleSet = new ForgeModelSampleSet(this);
    private final ForgeModelAtlasSkeleton modelAtlasSkeleton = new ForgeModelAtlasSkeleton(this);
    private final ForgeModelAtlasPixelUploader modelAtlasPixelUploader = new ForgeModelAtlasPixelUploader(this);
    private final ForgeModelAtlasSampleSetUploader modelAtlasSampleSetUploader = new ForgeModelAtlasSampleSetUploader(this);
    private final ForgeFormalShaderInputBridge formalShaderInputBridge = new ForgeFormalShaderInputBridge(this);
    private final ForgeFormalRendererManager formalRendererManager = new ForgeFormalRendererManager(this);
    private final ForgeTexturedDebugQuadRenderer texturedDebugQuadRenderer = new ForgeTexturedDebugQuadRenderer(this);
    private final ForgeTexturedReadbackRenderer texturedReadbackRenderer = new ForgeTexturedReadbackRenderer(this);
    private final ForgeTexturedMdicDebugRenderer texturedMdicDebugRenderer = new ForgeTexturedMdicDebugRenderer(this);
    private final AtomicInteger storageWriteCount = new AtomicInteger();
    private String activeClientDimension;

    private ForgeVoxyInstance() {
    }

    public void register() {
        MinecraftForge.EVENT_BUS.addListener(this::onClientLogin);
        MinecraftForge.EVENT_BUS.addListener(this::onClientLogout);
        MinecraftForge.EVENT_BUS.addListener(this::onClientTick);
        MinecraftForge.EVENT_BUS.addListener(this::onRegisterClientCommands);
        this.chunkIngestManager.register();
        this.cpuMeshBuildManager.register();
        this.builtSectionBuildManager.register();
        this.sectionGeometryConsumeManager.register();
        this.debugMeshRenderer.register();
        this.gpuMeshUploadManager.register();
        this.gpuGeometryReadbackDebugRenderer.register();
        this.gpuGeometryUploadManager.register();
        this.gpuGeometryReadbackMeshRefreshManager.register();
        this.simpleGpuMeshRenderer.register();
        this.directGpuGeometryRenderer.register();
        this.mdicDebugRenderer.register();
        this.texturedDebugQuadRenderer.register();
        this.texturedReadbackRenderer.register();
        this.texturedMdicDebugRenderer.register();
    }

    public WorldEngine getActiveWorld() {
        return this.activeWorld;
    }

    public Optional<WorldEngine> getCurrentEngineOptional() {
        return this.activeWorld != null && this.activeWorld.isLive()
                ? Optional.of(this.activeWorld)
                : Optional.empty();
    }

    public int getStorageWriteCount() {
        return this.storageWriteCount.get();
    }

    public ForgeChunkIngestManager getChunkIngestManager() {
        return this.chunkIngestManager;
    }

    public ForgeCpuMeshBuildManager getCpuMeshBuildManager() {
        return this.cpuMeshBuildManager;
    }

    public ForgeCpuMeshCache getCpuMeshCache() {
        return this.cpuMeshCache;
    }

    public ForgeVoxyBuiltSectionBuildManager getBuiltSectionBuildManager() {
        return this.builtSectionBuildManager;
    }

    public ForgeVoxyGeometryCache getVoxyGeometryCache() {
        return this.voxyGeometryCache;
    }

    public ForgeSectionGeometryManager getSectionGeometryManager() {
        return this.sectionGeometryManager;
    }

    public ForgeSectionGeometryConsumeManager getSectionGeometryConsumeManager() {
        return this.sectionGeometryConsumeManager;
    }

    public ForgeDebugMeshRenderer getDebugMeshRenderer() {
        return this.debugMeshRenderer;
    }

    public ForgeGpuMeshCache getGpuMeshCache() {
        return this.gpuMeshCache;
    }

    public ForgeGpuMeshUploadManager getGpuMeshUploadManager() {
        return this.gpuMeshUploadManager;
    }

    public ForgeGpuGeometryUploadManager getGpuGeometryUploadManager() {
        return this.gpuGeometryUploadManager;
    }

    public ForgeGpuGeometryVisualizationCache getGpuGeometryVisualizationCache() {
        return this.gpuGeometryVisualizationCache;
    }

    public ForgeGpuGeometryReadbackMeshCache getGpuGeometryReadbackMeshCache() {
        return this.gpuGeometryReadbackMeshCache;
    }

    public ForgeGpuGeometryReadbackMeshRefreshManager getGpuGeometryReadbackMeshRefreshManager() {
        return this.gpuGeometryReadbackMeshRefreshManager;
    }

    public ForgeGpuGeometryReadbackDebugRenderer getGpuGeometryReadbackDebugRenderer() {
        return this.gpuGeometryReadbackDebugRenderer;
    }

    public ForgeSimpleGpuMeshRenderer getSimpleGpuMeshRenderer() {
        return this.simpleGpuMeshRenderer;
    }

    public ForgeDirectGpuGeometryRenderer getDirectGpuGeometryRenderer() {
        return this.directGpuGeometryRenderer;
    }

    public ForgeMdicCommandManager getMdicCommandManager() {
        return this.mdicCommandManager;
    }

    public ForgeMdicDebugRenderer getMdicDebugRenderer() {
        return this.mdicDebugRenderer;
    }

    public ForgeModelBridgeReadiness getModelBridgeReadiness() {
        return this.modelBridgeReadiness;
    }

    public ForgeModelStoreSkeleton getModelStoreSkeleton() {
        return this.modelStoreSkeleton;
    }

    public ForgeModelStoreLayoutAuditor getModelStoreLayoutAuditor() {
        return this.modelStoreLayoutAuditor;
    }

    public ForgeModelBridgeResourceReloadTracker getModelBridgeResourceReloadTracker() {
        return this.modelBridgeResourceReloadTracker;
    }

    public ForgeBakedModelBridge getBakedModelBridge() {
        return this.bakedModelBridge;
    }

    public ForgeRealModelStoreSample getRealModelStoreSample() {
        return this.realModelStoreSample;
    }

    public ForgeModelSampleSet getModelSampleSet() {
        return this.modelSampleSet;
    }

    public ForgeModelAtlasSkeleton getModelAtlasSkeleton() {
        return this.modelAtlasSkeleton;
    }

    public ForgeModelAtlasPixelUploader getModelAtlasPixelUploader() {
        return this.modelAtlasPixelUploader;
    }

    public ForgeModelAtlasSampleSetUploader getModelAtlasSampleSetUploader() {
        return this.modelAtlasSampleSetUploader;
    }

    public ForgeFormalShaderInputBridge getFormalShaderInputBridge() {
        return this.formalShaderInputBridge;
    }

    public ForgeFormalRendererManager getFormalRendererManager() {
        return this.formalRendererManager;
    }

    public ForgeTexturedDebugQuadRenderer getTexturedDebugQuadRenderer() {
        return this.texturedDebugQuadRenderer;
    }

    public ForgeTexturedReadbackRenderer getTexturedReadbackRenderer() {
        return this.texturedReadbackRenderer;
    }

    public ForgeTexturedMdicDebugRenderer getTexturedMdicDebugRenderer() {
        return this.texturedMdicDebugRenderer;
    }

    private void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        ForgeVoxyCommands.register(event.getDispatcher());
    }

    private void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        var minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            return;
        }

        String dimension = minecraft.level.dimension().location().toString();
        if (this.activeClientDimension == null) {
            this.activeClientDimension = dimension;
            return;
        }
        if (this.activeClientDimension.equals(dimension)) {
            return;
        }

        this.activeClientDimension = dimension;
        this.chunkIngestManager.clear();
        this.cpuMeshBuildManager.clear();
        this.builtSectionBuildManager.clear();
        this.cpuMeshCache.setActiveDimension(dimension);
        this.voxyGeometryCache.setActiveDimension(dimension);
        this.sectionGeometryConsumeManager.clear();
        this.gpuMeshUploadManager.clear();
        this.gpuGeometryUploadManager.clear();
        this.gpuGeometryVisualizationCache.clear();
        this.gpuGeometryReadbackMeshCache.clear();
        this.gpuGeometryReadbackMeshRefreshManager.clear();
        this.gpuGeometryReadbackDebugRenderer.clearStats();
        this.directGpuGeometryRenderer.clear();
        this.mdicCommandManager.clear();
        this.mdicDebugRenderer.clear();
        this.modelBridgeReadiness.clear();
        this.modelStoreSkeleton.clear();
        this.modelStoreLayoutAuditor.clear();
        this.modelBridgeResourceReloadTracker.clear();
        this.bakedModelBridge.clear();
        this.realModelStoreSample.clear();
        this.modelSampleSet.clear();
        this.modelAtlasSkeleton.clear();
        this.modelAtlasPixelUploader.clear();
        this.modelAtlasSampleSetUploader.clear();
        this.formalShaderInputBridge.clear();
        this.formalRendererManager.markDimensionSwitch();
        this.texturedDebugQuadRenderer.clear();
        this.texturedReadbackRenderer.clear();
        this.texturedMdicDebugRenderer.clear();
        this.gpuMeshCache.setActiveDimension(dimension);
        this.closeActiveWorld();
        if (ForgeVoxyRuntimeOverrides.enabledWorldEngineSkeleton()) {
            this.createActiveWorldSkeleton();
        }
        if (ForgeGpuGeometryReadbackMeshRefreshManager.refreshOnDimensionChange()) {
            this.gpuGeometryReadbackMeshRefreshManager.requestRefresh(ForgeGpuGeometryReadbackMeshRefreshManager.REASON_DIMENSION_CHANGE);
        }
        if (ForgeDirectGpuGeometryRendererConfig.autoPlanOnDimensionChange()) {
            this.directGpuGeometryRenderer.requestAutoPlan(ForgeDirectGpuGeometryRenderer.REASON_DIMENSION_CHANGE);
        }
        VoxyForge.LOGGER.info("Cleared Voxy debug pipeline state after client dimension switch to {}.", dimension);
    }

    private void onClientLogin(ClientPlayerNetworkEvent.LoggingIn event) {
        if (!ForgeVoxyRuntimeOverrides.enabledWorldEngineSkeleton()) {
            return;
        }
        if (this.activeWorld != null && this.activeWorld.isLive()) {
            return;
        }

        this.createActiveWorldSkeleton();
    }

    public boolean ensureActiveWorldSkeletonForCurrentWorldIfAllowed() {
        if (!ForgeVoxyRuntimeOverrides.enabledWorldEngineSkeleton()) {
            return false;
        }
        if (this.activeWorld != null && this.activeWorld.isLive()) {
            return true;
        }

        var minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            return false;
        }

        this.activeClientDimension = minecraft.level.dimension().location().toString();
        this.createActiveWorldSkeleton();
        return true;
    }

    private void createActiveWorldSkeleton() {
        var storage = new SectionSerializationStorage(new MemoryStorageBackend());
        this.storageWriteCount.set(0);
        this.activeWorld = new WorldEngine(storage, this);
        this.activeWorld.setSaveCallback((engine, section, nonBlocking, sectionAlreadyAcquired) -> {
            try {
                section.setNotDirty();
                engine.storage.saveSection(section);
                this.storageWriteCount.incrementAndGet();
            } catch (Exception e) {
                VoxyForge.LOGGER.error("Failed to synchronously save Voxy skeleton section", e);
            }
            return false;
        });
        VoxyForge.LOGGER.info("Created empty Voxy WorldEngine skeleton for client world.");
    }

    private void onClientLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        this.chunkIngestManager.clear();
        this.cpuMeshBuildManager.clear();
        this.builtSectionBuildManager.clear();
        this.cpuMeshCache.clear();
        this.voxyGeometryCache.clear();
        this.sectionGeometryConsumeManager.clear();
        this.gpuMeshUploadManager.clear();
        this.gpuGeometryUploadManager.clear();
        this.gpuGeometryVisualizationCache.clear();
        this.gpuGeometryReadbackMeshCache.clear();
        this.gpuGeometryReadbackMeshRefreshManager.clear();
        this.gpuGeometryReadbackDebugRenderer.clearStats();
        this.directGpuGeometryRenderer.clear();
        this.mdicCommandManager.clear();
        this.mdicDebugRenderer.clear();
        this.modelBridgeReadiness.clear();
        this.modelStoreSkeleton.clear();
        this.modelStoreLayoutAuditor.clear();
        this.modelBridgeResourceReloadTracker.clear();
        this.bakedModelBridge.clear();
        this.realModelStoreSample.clear();
        this.modelSampleSet.clear();
        this.modelAtlasSkeleton.clear();
        this.modelAtlasPixelUploader.clear();
        this.modelAtlasSampleSetUploader.clear();
        this.formalShaderInputBridge.clear();
        this.formalRendererManager.markWorldUnload();
        this.texturedDebugQuadRenderer.clear();
        this.texturedReadbackRenderer.clear();
        this.texturedMdicDebugRenderer.clear();
        this.gpuMeshCache.clear();
        this.activeClientDimension = null;
        this.closeActiveWorld();
    }

    public void closeActiveWorld() {
        if (this.activeWorld == null) {
            return;
        }

        try {
            this.activeWorld.free();
            VoxyForge.LOGGER.info("Closed Voxy WorldEngine skeleton.");
        } finally {
            this.activeWorld = null;
        }
    }
}
