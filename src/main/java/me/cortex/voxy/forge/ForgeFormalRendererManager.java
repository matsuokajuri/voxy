package me.cortex.voxy.forge;

import net.minecraft.client.Minecraft;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

final class ForgeFormalRendererManager {
    static final String STAGE = "H2_FORMAL_RENDERER_LIFECYCLE_STATUS_HARDENING";

    private static final List<ForgeFormalRendererBlocker> BLOCKERS = List.of(
            new ForgeFormalRendererBlocker("P0", "P0_REAL_MODEL_FACTORY_REAL_BAKE_MISSING", "Real ModelFactory bake missing", "The formal ModelFactory skeleton can assign placeholder formal ids, but it does not bake real BakedModel data.", "Add the real Forge ModelFactory / ModelBakery bake path before formal draw.", true),
            new ForgeFormalRendererBlocker("P0", "P0_REAL_MODEL_FACTORY_UPLOAD_PIPELINE_MISSING", "Real ModelFactory upload pipeline missing", "I4 can upload one prototype block, but the formal ModelFactory still lacks a general upload pipeline.", "Connect real bake results to the formal ModelStore upload pipeline for the full model set.", true),
            new ForgeFormalRendererBlocker("P0", "P0_FORMAL_MODELSTORE_REAL_DATA_MISSING", "Formal ModelStore real data missing", "The formal ModelStore owner can receive one I4 prototype record, but it is not populated as a real ModelStore.", "Populate the formal ModelStore from the real ModelFactory / ModelBakery bridge before formal draw.", true),
            new ForgeFormalRendererBlocker("P0", "P0_FORMAL_MODELSTORE_REBUILD_MISSING", "Formal ModelStore rebuild path missing", "Resource reload can stale the formal ModelStore owner, but it cannot rebuild real model records or atlas data yet.", "Add resource reload rebuild ownership for formal model data and atlas resources.", true),
            new ForgeFormalRendererBlocker("P0", "P0_FORMAL_SHADER_MISSING", "Formal textured shader missing", "Current textured shaders are debug variants and do not implement the full formal contract.", "Add a formal shader after real model inputs exist.", true),
            new ForgeFormalRendererBlocker("P0", "P0_RESOURCE_RELOAD_REBUILD_MISSING", "Resource reload rebuild path missing", "Reload invalidates sample resources but does not rebuild a formal model/atlas set.", "Define rebuild ownership for model store, atlas, shader inputs, and renderer state.", true),
            new ForgeFormalRendererBlocker("P0", "P0_FORMAL_SHADER_INPUT_CONTRACT_INCOMPLETE", "Formal shader input contract incomplete", "The sample bridge proves bindings but not complete light, material, tint, alpha, and model semantics.", "Complete the formal shader input contract audit and implementation.", true),
            new ForgeFormalRendererBlocker("P1", "P1_VISIBILITY_TRAVERSAL_MISSING", "Formal visibility / LOD traversal missing", "Current command selection is debug radius/frustum planning, not formal traversal.", "Add formal visibility and LOD traversal ownership.", false),
            new ForgeFormalRendererBlocker("P1", "P1_COMMAND_BUFFER_OWNERSHIP_SEPARATION_MISSING", "Formal command buffer ownership missing", "Debug MDIC command buffers are not separated from future formal renderer ownership.", "Separate formal DrawCommand and draw count ownership from debug paths.", false),
            new ForgeFormalRendererBlocker("P1", "P1_BIOME_TINT_MODELCOLOUR_FORMAL_PATH_MISSING", "Biome tint / modelColour formal path missing", "Sample colours do not provide real biome tint or model colour lifecycle.", "Add formal modelColour and biome tint paths.", false),
            new ForgeFormalRendererBlocker("P1", "P1_LIGHTMAP_MISSING", "Lightmap missing", "The debug textured shaders do not carry formal lightmap input.", "Define and bind formal lightmap data.", false),
            new ForgeFormalRendererBlocker("P1", "P1_MATERIAL_ALPHA_CUTOUT_INCOMPLETE", "Material / alpha / cutout semantics incomplete", "Cutout, alpha, and material handling are debug-only.", "Implement formal material and alpha semantics.", false),
            new ForgeFormalRendererBlocker("P1", "P1_FORMAL_WORLD_LIFECYCLE_STRESS_MISSING", "Formal lifecycle stress missing", "Dimension/world unload tests have covered debug paths, not a formal owner.", "Stress the formal manager once it owns resources.", false),
            new ForgeFormalRendererBlocker("P2", "P2_SHADERPACK_INTEGRATION_MISSING", "Shaderpack integration missing", "No shaderpack renderer is connected.", "Handle shaderpack integration after a baseline formal renderer exists.", false),
            new ForgeFormalRendererBlocker("P2", "P2_EMBEDDIUM_OCULUS_IRIS_INTEGRATION_MISSING", "Embeddium / Oculus / Iris integration missing", "Optimization and shader-mod integration is out of scope for the skeleton.", "Defer until the renderer baseline is stable.", false),
            new ForgeFormalRendererBlocker("P2", "P2_ADVANCED_OCCLUSION_PERFORMANCE_MISSING", "Advanced occlusion performance missing", "HiZ and advanced occlusion are not implemented.", "Optimize after correctness and ownership are in place.", false),
            new ForgeFormalRendererBlocker("P2", "P2_TRANSLUCENT_SORTING_QUALITY_MISSING", "Translucent sorting quality missing", "Formal translucent sorting is not implemented.", "Design translucent handling after opaque renderer baseline.", false),
            new ForgeFormalRendererBlocker("P2", "P2_FULL_RENDERER_PERFORMANCE_TUNING_MISSING", "Full renderer performance tuning missing", "No formal renderer performance pass has happened.", "Tune after formal renderer correctness.", false)
    );

    private final ForgeVoxyInstance instance;
    private boolean enabled;
    private ForgeFormalRendererLifecycleState lifecycleState = ForgeFormalRendererLifecycleState.DISABLED;
    private String lastEnableReason = "none";
    private String lastDisableReason = "none";
    private String lastClearReason = "none";
    private String lastLifecycleEvent = "initialized";
    private String lastCheckAt = "none";
    private String lastCheckReason = "none";
    private String lastStaleReason = "none";
    private long readinessGeneration;
    private long lifecycleGeneration;
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
        this.lastCheckReason = safeReason(reason);
        this.lastCheckAt = Instant.now().toString();
        this.readinessGeneration++;
        if (this.enabled) {
            this.lifecycleState = ForgeFormalRendererLifecycleState.ENABLED_NO_DRAW;
        } else if (this.formalRendererStale) {
            this.lifecycleState = ForgeFormalRendererLifecycleState.STALE;
        } else if (this.lifecycleState != ForgeFormalRendererLifecycleState.CLEARED) {
            this.lifecycleState = ForgeFormalRendererLifecycleState.DISABLED;
        }
        if (!this.formalRendererStale) {
            this.lastStaleReason = "none";
        }
        this.lastLifecycleEvent = "readiness-check:" + this.lastCheckReason;
        return this.createStatusSnapshot();
    }

    ForgeFormalRendererStats enable(String reason) {
        this.enabled = true;
        this.formalRendererStale = false;
        this.lifecycleState = ForgeFormalRendererLifecycleState.ENABLED_NO_DRAW;
        this.lastEnableReason = safeReason(reason);
        this.lastStaleReason = "none";
        this.lastLifecycleEvent = "enable:" + this.lastEnableReason;
        this.lifecycleGeneration++;
        return this.createStatusSnapshot();
    }

    ForgeFormalRendererStats disable(String reason) {
        this.enabled = false;
        this.lifecycleState = ForgeFormalRendererLifecycleState.DISABLED;
        this.lastDisableReason = safeReason(reason);
        this.lastLifecycleEvent = "disable:" + this.lastDisableReason;
        this.lifecycleGeneration++;
        return this.createStatusSnapshot();
    }

    ForgeFormalRendererStats clear(String reason) {
        this.enabled = false;
        this.lifecycleState = ForgeFormalRendererLifecycleState.CLEARED;
        this.lastClearReason = safeReason(reason);
        this.lastLifecycleEvent = "clear:" + this.lastClearReason;
        this.formalRendererStale = false;
        this.lastCheckAt = "none";
        this.lastCheckReason = "none";
        this.lastStaleReason = "none";
        this.readinessGeneration = 0L;
        this.lifecycleGeneration++;
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
        int formalDrawBlocking = countFormalDrawBlockingBlockers();
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
                this.lastCheckAt,
                this.lastCheckReason,
                this.lastStaleReason,
                this.requiresRecheck(),
                this.readinessGeneration,
                this.lifecycleGeneration,
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
                readiness.infrastructureReady(),
                readiness.debugProofReady(),
                readiness.sampleBridgeReady(),
                readiness.oneBlockBakePrototypeReady(),
                readiness.oneBlockFormalUploadReady(),
                readiness.oneBlockFormalUploadAuditReady(),
                readiness.multiBlockBakePrototypeReady(),
                readiness.multiBlockFormalUploadReady(),
                readiness.multiBlockFormalUploadAuditReady(),
                readiness.formalModelBakeryLifecycleSkeletonReady(),
                readiness.reloadRebuildPrototypeReady(),
                readiness.aliasSafeDedupeReady(),
                readiness.formalModelFactorySkeletonReady(),
                readiness.formalModelFactoryLifecycleReady(),
                readiness.formalModelStoreSkeletonReady(),
                readiness.formalModelStoreOwnerReady(),
                readiness.formalShaderInputConsumerReady(),
                readiness.formalShaderInputBindingLayoutKnown(),
                readiness.formalShaderInputBindingLayoutCompatible(),
                readiness.formalShaderProgramValidatorReady(),
                readiness.formalShaderProgramValidationReady(),
                readiness.validationShaderCompileOk(),
                readiness.validationProgramLinkOk(),
                readiness.gpuValidationOk(),
                readiness.formalTexturedShaderPrototypeReady(),
                readiness.formalTexturedShaderPreviewReady(),
                readiness.terrainDrawStarted(),
                readiness.formalRendererDrawStarted(),
                readiness.actualRendererDrawEnabled(),
                readiness.formalShaderInputContractReady(),
                readiness.formalPrerequisitesReady(),
                false,
                false,
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
                formalDrawBlocking,
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
                        ForgeFormalRendererBlocker::severity,
                        java.util.TreeMap::new,
                        Collectors.mapping(ForgeFormalRendererBlocker::detailed, Collectors.joining(","))))
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
        ForgeFormalModelFactoryStats formalModelFactory = this.instance.getFormalModelFactory().createStatusSnapshot();
        ForgeFormalModelStoreStats formalModelStore = this.instance.getFormalModelStore().createStatusSnapshot();
        ForgeOneBlockFormalBakeUploadStats oneBlock = this.instance.getOneBlockFormalBakeUpload().createStatusSnapshot();
        ForgeMultiBlockFormalBakeUploadStats multiBlock = this.instance.getMultiBlockFormalBakeUpload().createStatusSnapshot();
        ForgeFormalModelBakeryLifecycleStats bakeryLifecycle = this.instance.getFormalModelBakeryLifecycle().createStatusSnapshot();
        ForgeFormalShaderInputConsumerStats shaderInputConsumer = this.instance.getFormalShaderInputConsumer().createStatusSnapshot();
        ForgeFormalShaderProgramStats shaderProgram = this.instance.getFormalShaderProgramValidator().createStatusSnapshot();
        ForgeFormalTexturedShaderPreviewStats texturedShaderPreview = this.instance.getFormalTexturedShaderPreview().createStatusSnapshot();
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
        boolean infrastructureReady = geometry.heapCreated()
                && (geometry.metadataWrites() > 0L || section.metadataValid() > 0)
                && section.maxSections() > 0
                && reload.resourceReloadReady()
                && this.instance.getCurrentEngineOptional().isPresent()
                && !"none".equals(dimension);
        boolean debugProofReady = mdicCommandReady && mdicDrawCountReady;
        boolean sampleBridgeReady = shaderInput.formalShaderInputBridgeReady()
                && !shaderInput.formalShaderInputBridgeStale()
                && atlasReady;
        boolean formalPrerequisitesReady = false;
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
                !"none".equals(dimension),
                infrastructureReady,
                debugProofReady,
                sampleBridgeReady,
                oneBlock.oneBlockBakePrototypeReady(),
                oneBlock.oneBlockFormalUploadReady(),
                oneBlock.oneBlockFormalUploadAuditReady(),
                multiBlock.multiBlockBakePrototypeReady(),
                multiBlock.multiBlockFormalUploadReady(),
                multiBlock.multiBlockFormalUploadAuditReady(),
                bakeryLifecycle.formalModelBakeryLifecycleSkeletonReady(),
                bakeryLifecycle.reloadRebuildPrototypeReady(),
                bakeryLifecycle.aliasSafeDedupeReady(),
                formalModelFactory.formalModelFactorySkeletonReady(),
                formalModelFactory.formalModelFactoryLifecycleReady(),
                formalModelStore.formalModelStoreSkeletonReady(),
                formalModelStore.formalModelStoreOwnerReady(),
                shaderInputConsumer.formalShaderInputConsumerReady(),
                shaderInputConsumer.bindingLayoutKnown(),
                shaderInputConsumer.bindingLayoutCompatible(),
                shaderProgram.formalShaderProgramValidatorReady(),
                shaderProgram.formalShaderProgramValidationReady(),
                shaderProgram.validationShaderCompileOk(),
                shaderProgram.validationProgramLinkOk(),
                shaderProgram.gpuValidationOk(),
                texturedShaderPreview.formalTexturedShaderPrototypeReady(),
                texturedShaderPreview.formalTexturedShaderPreviewReady(),
                texturedShaderPreview.terrainDrawStarted(),
                texturedShaderPreview.formalRendererDrawStarted(),
                texturedShaderPreview.actualRendererDrawEnabled(),
                shaderInputConsumer.formalShaderInputContractReady(),
                formalPrerequisitesReady
        );
    }

    private void markLifecycleStale(String event) {
        this.enabled = false;
        this.formalRendererStale = true;
        this.lifecycleState = ForgeFormalRendererLifecycleState.STALE;
        this.lastStaleReason = safeReason(event);
        this.lastLifecycleEvent = this.lastStaleReason;
        this.lifecycleGeneration++;
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

    private static int countFormalDrawBlockingBlockers() {
        int count = 0;
        for (ForgeFormalRendererBlocker blocker : BLOCKERS) {
            if (blocker.formalDrawBlocking()) {
                count++;
            }
        }
        return count;
    }

    private boolean requiresRecheck() {
        return this.formalRendererStale || "none".equals(this.lastCheckAt);
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
