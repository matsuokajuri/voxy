package me.cortex.voxy.forge;

import net.minecraft.client.Minecraft;

import java.util.List;
import java.util.stream.Collectors;

final class ForgeFormalRendererManager {
    static final String STAGE = "H1_FORMAL_RENDERER_NO_DRAW_SKELETON";

    private static final List<ForgeFormalRendererBlocker> BLOCKERS = List.of(
            new ForgeFormalRendererBlocker("P0", "real-model-factory-modelbakery-bridge-missing", "real ModelFactory / ModelBakery bridge missing"),
            new ForgeFormalRendererBlocker("P0", "formal-modelstore-missing", "formal ModelStore missing"),
            new ForgeFormalRendererBlocker("P0", "formal-textured-shader-missing", "formal textured shader missing"),
            new ForgeFormalRendererBlocker("P0", "formal-renderer-ownership-partial", "formal renderer ownership incomplete / partial"),
            new ForgeFormalRendererBlocker("P0", "resource-reload-rebuild-path-missing", "resource reload rebuild path missing"),
            new ForgeFormalRendererBlocker("P0", "formal-shader-input-contract-incomplete", "formal shader input contract incomplete"),
            new ForgeFormalRendererBlocker("P1", "formal-visibility-lod-traversal-missing", "formal visibility / LOD traversal missing"),
            new ForgeFormalRendererBlocker("P1", "formal-command-buffer-ownership-separation-missing", "formal command buffer ownership separation missing"),
            new ForgeFormalRendererBlocker("P1", "biome-tint-modelcolour-formal-path-missing", "biome tint / modelColour formal path missing"),
            new ForgeFormalRendererBlocker("P1", "lightmap-missing", "lightmap missing"),
            new ForgeFormalRendererBlocker("P1", "material-alpha-cutout-semantics-incomplete", "material / alpha / cutout semantics incomplete"),
            new ForgeFormalRendererBlocker("P1", "formal-world-lifecycle-stress-missing", "dimension/world unload stress under formal ownership missing"),
            new ForgeFormalRendererBlocker("P2", "shaderpack-integration-missing", "shaderpack integration"),
            new ForgeFormalRendererBlocker("P2", "embeddium-oculus-iris-integration-missing", "Embeddium / Oculus / Iris integration"),
            new ForgeFormalRendererBlocker("P2", "advanced-occlusion-performance-missing", "advanced occlusion performance"),
            new ForgeFormalRendererBlocker("P2", "translucent-sorting-quality-missing", "translucent sorting quality"),
            new ForgeFormalRendererBlocker("P2", "full-renderer-performance-tuning-missing", "full renderer performance tuning")
    );

    private final ForgeVoxyInstance instance;
    private boolean enabled;
    private ForgeFormalRendererLifecycleState lifecycleState = ForgeFormalRendererLifecycleState.INITIALIZED_NO_DRAW;
    private String lastEnableReason = "none";
    private String lastDisableReason = "none";
    private String lastClearReason = "none";
    private String lastLifecycleEvent = "initialized";
    private boolean worldUnloadSeen;
    private boolean dimensionSwitchSeen;
    private boolean resourceReloadSeen;
    private boolean debugPipelineClearSeen;
    private boolean presetOffSeen;
    private boolean presetClearSeen;
    private boolean formalRendererStale;
    private boolean existingMdicDebugTouched;
    private boolean texturedMdicDebugTouched;
    private boolean actualDrawStartedByFormalRenderer;

    ForgeFormalRendererManager(ForgeVoxyInstance instance) {
        this.instance = instance;
    }

    ForgeFormalRendererStats checkReadiness(String reason) {
        if (this.enabled) {
            this.lifecycleState = ForgeFormalRendererLifecycleState.ENABLED_NO_DRAW;
        } else if (!this.formalRendererStale) {
            this.lifecycleState = ForgeFormalRendererLifecycleState.CHECKED_NO_DRAW;
        }
        this.lastLifecycleEvent = "readiness-check:" + safeReason(reason);
        return this.createStatusSnapshot();
    }

    ForgeFormalRendererStats enable(String reason) {
        this.enabled = true;
        this.formalRendererStale = false;
        this.lifecycleState = ForgeFormalRendererLifecycleState.ENABLED_NO_DRAW;
        this.lastEnableReason = safeReason(reason);
        this.lastLifecycleEvent = "enable:" + this.lastEnableReason;
        return this.createStatusSnapshot();
    }

    ForgeFormalRendererStats disable(String reason) {
        this.enabled = false;
        this.lifecycleState = ForgeFormalRendererLifecycleState.DISABLED_NO_DRAW;
        this.lastDisableReason = safeReason(reason);
        this.lastLifecycleEvent = "disable:" + this.lastDisableReason;
        return this.createStatusSnapshot();
    }

    ForgeFormalRendererStats clear(String reason) {
        this.enabled = false;
        this.lifecycleState = ForgeFormalRendererLifecycleState.CLEARED;
        this.lastClearReason = safeReason(reason);
        this.lastLifecycleEvent = "clear:" + this.lastClearReason;
        this.formalRendererStale = false;
        this.worldUnloadSeen = false;
        this.dimensionSwitchSeen = false;
        this.resourceReloadSeen = false;
        this.debugPipelineClearSeen = false;
        this.presetOffSeen = false;
        this.presetClearSeen = false;
        this.existingMdicDebugTouched = false;
        this.texturedMdicDebugTouched = false;
        this.actualDrawStartedByFormalRenderer = false;
        return this.createStatusSnapshot();
    }

    void markWorldUnload() {
        this.markLifecycleStale("world-unload");
        this.worldUnloadSeen = true;
    }

    void markDimensionSwitch() {
        this.markLifecycleStale("dimension-switch");
        this.dimensionSwitchSeen = true;
    }

    void markResourceReload() {
        this.markLifecycleStale("resource-reload");
        this.resourceReloadSeen = true;
    }

    void markDebugPipelineClear() {
        this.markLifecycleStale("debug-pipeline-clear");
        this.debugPipelineClearSeen = true;
    }

    void markPresetOff() {
        this.markLifecycleStale("preset-off");
        this.presetOffSeen = true;
    }

    void markPresetClear() {
        this.markLifecycleStale("preset-clear");
        this.presetClearSeen = true;
    }

    ForgeFormalRendererStats createStatusSnapshot() {
        ForgeFormalRendererReadiness readiness = this.createReadinessSnapshot();
        int p0 = countBlockers("P0");
        int p1 = countBlockers("P1");
        int p2 = countBlockers("P2");
        return new ForgeFormalRendererStats(
                STAGE,
                true,
                false,
                false,
                true,
                this.enabled,
                this.lifecycleState.name(),
                true,
                this.lastEnableReason,
                this.lastDisableReason,
                this.lastClearReason,
                this.lastLifecycleEvent,
                readiness.geometryHeapReady(),
                readiness.metadataReady(),
                readiness.sectionGeometryManagerReady(),
                readiness.mdicCommandReady(),
                readiness.mdicDrawCountReady(),
                readiness.modelBridgeReady(),
                readiness.formalShaderInputBridgeReady(),
                readiness.atlasReady(),
                readiness.resourceReloadReady(),
                readiness.worldEngineReady(),
                readiness.dimensionReady(),
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                BLOCKERS.size(),
                p0,
                p1,
                p2,
                compactBlockers(),
                this.worldUnloadSeen,
                this.dimensionSwitchSeen,
                this.resourceReloadSeen,
                this.debugPipelineClearSeen,
                this.presetOffSeen,
                this.presetClearSeen,
                this.formalRendererStale,
                !this.existingMdicDebugTouched && !this.texturedMdicDebugTouched && !this.actualDrawStartedByFormalRenderer,
                this.existingMdicDebugTouched,
                this.texturedMdicDebugTouched,
                this.actualDrawStartedByFormalRenderer
        );
    }

    String dumpBlockers() {
        return BLOCKERS.stream()
                .collect(Collectors.groupingBy(
                        ForgeFormalRendererBlocker::priority,
                        java.util.TreeMap::new,
                        Collectors.mapping(blocker -> blocker.id() + "=\"" + blocker.description() + "\"", Collectors.joining(","))))
                .entrySet()
                .stream()
                .map(entry -> entry.getKey() + "[" + entry.getValue() + "]")
                .collect(Collectors.joining(" "));
    }

    private ForgeFormalRendererReadiness createReadinessSnapshot() {
        ForgeGpuGeometryStats geometry = this.instance.getGpuGeometryUploadManager().createStatusSnapshot();
        ForgeSectionGeometryStats section = this.instance.getSectionGeometryManager().createStatusSnapshot();
        ForgeMdicCommandStats mdic = this.instance.getMdicCommandManager().createStatusSnapshot();
        ForgeMdicDebugDrawStats mdicDraw = this.instance.getMdicDebugRenderer().createStatusSnapshot();
        ForgeModelBridgeReadinessStats modelBridge = this.instance.getModelBridgeReadiness().createStatusSnapshot();
        ForgeFormalShaderInputStats shaderInput = this.instance.getFormalShaderInputBridge().createStatusSnapshot();
        ForgeModelAtlasSampleSetUploadStats atlas = this.instance.getModelAtlasSampleSetUploader().createStatusSnapshot();
        ForgeModelBridgeResourceReloadStats reload = this.instance.getModelBridgeResourceReloadTracker().createStatusSnapshot();
        String dimension = currentDimensionId();
        boolean mdicCommandReady = mdic.commandListValid()
                && mdic.commandBufferCreated()
                && !mdic.commandListStale()
                && !mdic.commandBufferStale();
        boolean mdicDrawCountReady = mdicDraw.drawCountBufferCreated() && !mdicDraw.drawCountBufferStale();
        boolean atlasReady = atlas.sampleSetAtlasUploadReady()
                && atlas.atlasTextureObjectCreated()
                && atlas.atlasPixelsUploaded()
                && !atlas.atlasSampleSetStale();
        return new ForgeFormalRendererReadiness(
                geometry.heapCreated(),
                geometry.metadataWrites() > 0L || section.metadataValid() > 0,
                section.maxSections() > 0,
                mdicCommandReady,
                mdicDrawCountReady,
                modelBridge.formalModelBridgeReady(),
                shaderInput.formalShaderInputBridgeReady() && !shaderInput.formalShaderInputBridgeStale(),
                atlasReady,
                reload.resourceReloadReady(),
                this.instance.getCurrentEngineOptional().isPresent(),
                !"none".equals(dimension)
        );
    }

    private void markLifecycleStale(String event) {
        this.enabled = false;
        this.formalRendererStale = true;
        this.lifecycleState = ForgeFormalRendererLifecycleState.STALE_DISABLED;
        this.lastLifecycleEvent = event;
    }

    private static int countBlockers(String priority) {
        int count = 0;
        for (ForgeFormalRendererBlocker blocker : BLOCKERS) {
            if (blocker.priority().equals(priority)) {
                count++;
            }
        }
        return count;
    }

    private static String compactBlockers() {
        return BLOCKERS.stream()
                .map(ForgeFormalRendererBlocker::compact)
                .collect(Collectors.joining("|"));
    }

    private static String currentDimensionId() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return "none";
        }
        return minecraft.level.dimension().location().toString();
    }

    private static String safeReason(String reason) {
        return reason == null || reason.isBlank() ? "unspecified" : reason.replace(' ', '-');
    }
}
