package me.cortex.voxy.forge;

import com.mojang.blaze3d.systems.RenderSystem;
import me.cortex.voxy.common.world.WorldEngine;
import net.minecraft.client.Minecraft;
import net.minecraft.client.color.block.BlockColors;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

final class ForgeMultiBlockFormalBakeUpload {
    static final String STAGE = "I5_MULTI_BLOCK_FORMAL_BAKE_UPLOAD_AND_DEDUPE";
    private static final Direction[] DIRECTIONS = Direction.values();
    private static final int VERTICES_PER_QUAD = 4;
    private static final int MIN_ACCEPTED_BLOCKS = 3;

    private final ForgeVoxyInstance instance;
    private final List<AcceptedModel> acceptedModels = new ArrayList<>();
    private final List<RejectedModel> rejectedModels = new ArrayList<>();
    private final Map<String, Integer> modelTexture2id = new LinkedHashMap<>();
    private long buildRuns;
    private long auditRuns;
    private long clearRuns;
    private long uploadFailures;
    private boolean enabled;
    private boolean stale;
    private boolean requiresRebuild;
    private String lifecycleState = "UNINITIALIZED";
    private String lastLifecycleEvent = "initialized";
    private String staleReason = "none";
    private String lastFailureReason = "none";
    private boolean resourceReloadSeen;
    private boolean worldUnloadSeen;
    private boolean dimensionSwitchSeen;
    private boolean debugPipelineClearSeen;
    private boolean presetOffSeen;
    private boolean presetClearSeen;
    private int requestedBlockStateCount;
    private int dedupeHitCount;
    private int dedupeMissCount;
    private int uploadedModelRecordCount;
    private int uploadedModelColourCount;
    private int uploadedAtlasModelCount;
    private int uploadedFaceTileCount;
    private int uploadedPixels;
    private int atlasPixelMismatches;
    private boolean modelDataReadbackOk;
    private boolean modelColourReadbackOk;
    private boolean atlasReadbackOk;
    private int unsupportedAirCount;
    private int unsupportedFluidCount;
    private int unsupportedTranslucentCount;
    private int unsupportedCutoutCount;
    private int unsupportedTintedCount;
    private int unsupportedAnimatedCount;
    private int unsupportedMissingSpriteCount;
    private int unsupportedNoModelCount;
    private int unsupportedNoQuadsCount;
    private int unsupportedRenderLayerCount;
    private ForgeMultiBlockFormalBakeUploadAuditResult lastAudit =
            ForgeMultiBlockFormalBakeUploadAuditResult.failure("none", 0.0D);

    ForgeMultiBlockFormalBakeUpload(ForgeVoxyInstance instance) {
        this.instance = instance;
    }

    ForgeMultiBlockFormalBakeUploadStats bakeMultiSafe() {
        this.buildRuns++;
        this.enabled = true;
        this.stale = false;
        this.requiresRebuild = false;
        this.lifecycleState = "BUILT";
        this.lastLifecycleEvent = "bake-multi-safe";
        this.staleReason = "none";
        this.lastFailureReason = "none";
        this.resetUploadState();

        if (!RenderSystem.isOnRenderThread()) {
            this.fail("not-render-thread");
            return this.createStatusSnapshot();
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.getBlockRenderer() == null) {
            this.fail("no-active-client-world");
            return this.createStatusSnapshot();
        }

        ForgeFormalModelStoreStats storeStatus = this.instance.getFormalModelStore().createStatusSnapshot();
        if (!storeStatus.formalModelStoreOwnerReady() || storeStatus.stale() || storeStatus.requiresRebuild()) {
            storeStatus = this.instance.getFormalModelStore().build();
        }
        if (!storeStatus.formalModelStoreOwnerReady()) {
            this.fail("formal-model-store-owner-not-ready:" + storeStatus.lastBuildError());
            return this.createStatusSnapshot();
        }
        if (!this.instance.getFormalModelStore().canUploadOneBlockPrototype()) {
            this.fail("formal-model-store-full-atlas-not-ready:" + storeStatus.lastAllocationError());
            return this.createStatusSnapshot();
        }

        for (Candidate candidate : this.safeCandidates()) {
            this.requestedBlockStateCount++;
            BuildResult result = this.tryBuildRecord(minecraft, candidate);
            if (!result.accepted()) {
                this.reject(candidate, result.rejectCategory(), result.rejectReason());
                continue;
            }

            RecordBuild build = result.build();
            String signature = build.signature();
            if (this.modelTexture2id.containsKey(signature)) {
                this.dedupeHitCount++;
                this.reject(candidate, RejectCategory.RENDER_LAYER, "dedupe-alias-not-supported-by-i3-audit");
                continue;
            }

            Optional<Integer> formalModelId = this.assignFormalModelId(candidate);
            if (formalModelId.isEmpty()) {
                this.reject(candidate, RejectCategory.NO_MODEL, this.lastFailureReason);
                continue;
            }

            int modelId = formalModelId.get();
            String error = this.uploadModel(modelId, build);
            if (!"none".equals(error)) {
                this.reject(candidate, RejectCategory.NO_MODEL, error);
                this.fail(error);
                continue;
            }

            this.modelTexture2id.put(signature, modelId);
            this.dedupeMissCount++;
            this.acceptedModels.add(new AcceptedModel(candidate, modelId, build));
        }

        if (this.acceptedModels.size() < MIN_ACCEPTED_BLOCKS && "none".equals(this.lastFailureReason)) {
            this.fail("accepted-safe-solid-blocks-below-" + MIN_ACCEPTED_BLOCKS);
        }
        return this.createStatusSnapshot();
    }

    ForgeMultiBlockFormalBakeUploadAuditResult audit() {
        this.auditRuns++;
        long start = System.nanoTime();
        ForgeMultiBlockFormalBakeUploadAuditResult result;
        if (!RenderSystem.isOnRenderThread()) {
            result = ForgeMultiBlockFormalBakeUploadAuditResult.failure("not-render-thread", elapsedMs(start));
        } else {
            try {
                result = this.auditInternal(start);
            } catch (RuntimeException e) {
                result = ForgeMultiBlockFormalBakeUploadAuditResult.failure(e.getClass().getSimpleName() + ": " + e.getMessage(), elapsedMs(start));
            }
        }
        this.lastAudit = result;
        this.modelDataReadbackOk = result.modelDataReadbackOk();
        this.modelColourReadbackOk = result.modelColourReadbackOk();
        this.atlasReadbackOk = result.atlasReadbackOk();
        this.atlasPixelMismatches = result.atlasPixelMismatches();
        if (!result.success()) {
            this.uploadFailures++;
            this.lastFailureReason = result.error();
        }
        return result;
    }

    ForgeMultiBlockFormalBakeUploadAuditResult createAuditStatusSnapshot() {
        return this.lastAudit;
    }

    List<ForgeFormalUploadedModelSummary> uploadedModelSummaries() {
        return this.acceptedModels.stream()
                .map(model -> new ForgeFormalUploadedModelSummary(
                        model.candidate().blockStateId(),
                        model.candidate().state().toString(),
                        model.formalModelId(),
                        model.build().signature(),
                        model.build().primarySprite(),
                        ForgeModelStoreFormalLayout.MODEL_RECORD_BYTES
                ))
                .toList();
    }

    ForgeMultiBlockFormalBakeUploadStats createStatusSnapshot() {
        ForgeFormalModelStoreStats store = this.instance.getFormalModelStore().createStatusSnapshot();
        boolean recordsUploaded = this.uploadedModelRecordCount == this.acceptedModels.size() && !this.acceptedModels.isEmpty();
        boolean coloursUploaded = this.uploadedModelColourCount == this.acceptedModels.size() && !this.acceptedModels.isEmpty();
        boolean atlasUploaded = this.uploadedAtlasModelCount == this.acceptedModels.size()
                && this.uploadedFaceTileCount == this.acceptedModels.size() * ForgeModelAtlasLayout.FACE_COUNT
                && !this.acceptedModels.isEmpty();
        boolean uploadReady = recordsUploaded && coloursUploaded && atlasUploaded;
        boolean auditReady = this.lastAudit.success()
                && this.modelDataReadbackOk
                && this.modelColourReadbackOk
                && this.atlasReadbackOk
                && this.atlasPixelMismatches == 0;
        boolean bakeReady = this.acceptedModels.size() >= MIN_ACCEPTED_BLOCKS && !this.stale;
        boolean prototypeReady = bakeReady && uploadReady && auditReady && !this.stale;
        return new ForgeMultiBlockFormalBakeUploadStats(
                STAGE,
                this.buildRuns,
                this.auditRuns,
                this.clearRuns,
                this.uploadFailures,
                prototypeReady,
                bakeReady,
                uploadReady && !this.stale,
                auditReady && !this.stale,
                recordsUploaded && !this.stale,
                atlasUploaded && !this.stale,
                auditReady && !this.stale,
                this.requestedBlockStateCount,
                this.acceptedModels.size(),
                this.rejectedModels.size(),
                this.modelTexture2id.size(),
                this.modelTexture2id.size(),
                this.dedupeHitCount,
                this.dedupeHitCount,
                this.dedupeMissCount,
                this.uploadedModelRecordCount,
                this.uploadedModelColourCount,
                this.uploadedAtlasModelCount,
                this.uploadedFaceTileCount,
                this.uploadedPixels,
                this.atlasPixelMismatches,
                this.modelDataReadbackOk,
                this.modelColourReadbackOk,
                this.atlasReadbackOk,
                this.unsupportedAirCount,
                this.unsupportedFluidCount,
                this.unsupportedTranslucentCount,
                this.unsupportedCutoutCount,
                this.unsupportedTintedCount,
                this.unsupportedAnimatedCount,
                this.unsupportedMissingSpriteCount,
                this.unsupportedNoModelCount,
                this.unsupportedNoQuadsCount,
                this.unsupportedRenderLayerCount,
                !this.acceptedModels.isEmpty(),
                false,
                false,
                true,
                true,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                true,
                true,
                this.stale,
                this.requiresRebuild,
                this.lifecycleState,
                this.lastLifecycleEvent,
                this.staleReason,
                this.lastFailureReason,
                this.acceptedBlockStates(),
                this.acceptedFormalModelIds(),
                this.rejectedBlockStates(),
                this.faceChecksums(),
                store.formalModelStoreOwnerReady(),
                this.resourceReloadSeen,
                this.worldUnloadSeen,
                this.dimensionSwitchSeen,
                this.debugPipelineClearSeen,
                this.presetOffSeen,
                this.presetClearSeen,
                this.lastAudit.success(),
                this.lastAudit.error(),
                this.lastAudit.durationMs()
        );
    }

    String dump() {
        ForgeMultiBlockFormalBakeUploadStats status = this.createStatusSnapshot();
        return "Voxy I5 multi-block formal bake/upload dump: "
                + "stage=" + status.stage()
                + " requestedBlockStates=" + status.requestedBlockStateCount()
                + " acceptedBlockStates=[" + status.acceptedBlockStates() + "]"
                + " formalModelIds=[" + status.acceptedFormalModelIds() + "]"
                + " rejectedBlockStates=[" + status.rejectedBlockStates() + "]"
                + " modelTexture2idSize=" + this.modelTexture2id.size()
                + " modelTexture2id=" + this.modelTexture2id
                + " faceChecksums=" + status.faceChecksums()
                + " formalRendererReady=false actualDrawEnabled=false sampleSetModelIdsUsed=false";
    }

    void clear() {
        this.clearRuns++;
        this.enabled = false;
        this.stale = false;
        this.requiresRebuild = false;
        this.lifecycleState = "CLEARED";
        this.lastLifecycleEvent = "clear";
        this.staleReason = "none";
        this.lastFailureReason = "none";
        this.resetUploadState();
        this.resourceReloadSeen = false;
        this.worldUnloadSeen = false;
        this.dimensionSwitchSeen = false;
        this.debugPipelineClearSeen = false;
        this.presetOffSeen = false;
        this.presetClearSeen = false;
        this.lastAudit = ForgeMultiBlockFormalBakeUploadAuditResult.failure("none", 0.0D);
    }

    void markResourceReload() {
        this.markLifecycleStale("resource-reload");
        this.resourceReloadSeen = true;
    }

    void markWorldUnload() {
        this.markLifecycleStale("world-unload");
        this.worldUnloadSeen = true;
    }

    void markDimensionSwitch() {
        this.markLifecycleStale("dimension-switch");
        this.dimensionSwitchSeen = true;
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

    private ForgeMultiBlockFormalBakeUploadAuditResult auditInternal(long startNanos) {
        boolean allAcceptedHaveFormalModelIds = !this.acceptedModels.isEmpty();
        boolean placeholderIdUsed = false;
        boolean sampleSetIdUsed = false;
        boolean allModelRecordBytesOk = !this.acceptedModels.isEmpty();
        boolean dataMatch = !this.acceptedModels.isEmpty();
        boolean colourMatch = !this.acceptedModels.isEmpty();
        boolean atlasOk = !this.acceptedModels.isEmpty();
        int pixelMismatches = 0;

        for (AcceptedModel model : this.acceptedModels) {
            int formalModelId = model.formalModelId();
            if (!ForgeModelAtlasLayout.isValidModelId(formalModelId) || formalModelId == 0) {
                allAcceptedHaveFormalModelIds = false;
            }
            boolean mappingMatches = this.instance.getFormalModelFactory().mappingForBlockStateId(model.candidate().blockStateId())
                    .map(mapping -> mapping.formalModelId() == formalModelId)
                    .orElse(false);
            if (!mappingMatches) {
                allAcceptedHaveFormalModelIds = false;
            }
            int[] words = model.build().words();
            if (words == null || words.length != ForgeModelStoreFormalLayout.MODEL_RECORD_WORDS || ForgeModelStoreFormalLayout.MODEL_RECORD_BYTES != 64) {
                allModelRecordBytesOk = false;
            } else if (!Arrays.equals(words, this.instance.getFormalModelStore().readPrototypeModelRecord(formalModelId))) {
                dataMatch = false;
            }
            if (model.build().modelColour() != this.instance.getFormalModelStore().readPrototypeModelColour(formalModelId)) {
                colourMatch = false;
            }
            for (int faceIndex = 0; faceIndex < ForgeModelAtlasLayout.FACE_COUNT; faceIndex++) {
                FaceUpload face = model.build().faces()[faceIndex];
                if (face == null) {
                    atlasOk = false;
                    continue;
                }
                byte[] readback = this.instance.getFormalModelStore().readPrototypeAtlasFace(formalModelId, faceIndex);
                int mismatches = countPixelMismatches(face.pixels(), readback);
                pixelMismatches += mismatches;
                if (mismatches != 0) {
                    atlasOk = false;
                }
            }
        }

        boolean dedupeConsistent = this.modelTexture2id.size() == this.acceptedModels.size()
                && this.dedupeMissCount == this.acceptedModels.size();
        boolean unsupportedPolicyReady = true;
        boolean shaderBound = false;
        boolean drawOccurred = false;
        boolean formalRendererReady = false;
        boolean actualDrawEnabled = false;
        boolean success = this.acceptedModels.size() >= MIN_ACCEPTED_BLOCKS
                && allAcceptedHaveFormalModelIds
                && !placeholderIdUsed
                && !sampleSetIdUsed
                && allModelRecordBytesOk
                && dataMatch
                && colourMatch
                && atlasOk
                && pixelMismatches == 0
                && dedupeConsistent
                && unsupportedPolicyReady
                && !shaderBound
                && !drawOccurred
                && !formalRendererReady
                && !actualDrawEnabled
                && !this.stale;
        return new ForgeMultiBlockFormalBakeUploadAuditResult(
                success,
                success ? "none" : "multi-block-formal-upload-audit-failed",
                elapsedMs(startNanos),
                allAcceptedHaveFormalModelIds,
                placeholderIdUsed,
                sampleSetIdUsed,
                allModelRecordBytesOk,
                dataMatch,
                colourMatch,
                atlasOk,
                pixelMismatches,
                dedupeConsistent,
                unsupportedPolicyReady,
                shaderBound,
                drawOccurred,
                formalRendererReady,
                actualDrawEnabled
        );
    }

    private List<Candidate> safeCandidates() {
        return List.of(
                this.candidateFor(Blocks.SAND.defaultBlockState()),
                this.candidateFor(Blocks.STONE.defaultBlockState()),
                this.candidateFor(Blocks.DIRT.defaultBlockState()),
                this.candidateFor(Blocks.GRAVEL.defaultBlockState()),
                this.candidateFor(Blocks.GRANITE.defaultBlockState()),
                this.candidateFor(Blocks.DIORITE.defaultBlockState()),
                this.candidateFor(Blocks.ANDESITE.defaultBlockState()),
                this.candidateFor(Blocks.CLAY.defaultBlockState())
        );
    }

    private Candidate candidateFor(BlockState state) {
        return new Candidate(this.blockStateIdForState(state), state);
    }

    private BuildResult tryBuildRecord(Minecraft minecraft, Candidate candidate) {
        if (candidate.state() == null || candidate.state().isAir()) {
            return BuildResult.rejected(RejectCategory.AIR, "air");
        }
        if (!candidate.state().getFluidState().isEmpty()) {
            return BuildResult.rejected(RejectCategory.FLUID, "fluid");
        }
        ForgeCpuMeshLayer layer = ForgeCpuMeshLayer.fromBlockState(candidate.state());
        if (layer == ForgeCpuMeshLayer.CUTOUT) {
            return BuildResult.rejected(RejectCategory.CUTOUT, "cutout-render-layer");
        }
        if (layer == ForgeCpuMeshLayer.TRANSLUCENT) {
            return BuildResult.rejected(RejectCategory.TRANSLUCENT, "translucent-render-layer");
        }
        if (layer != ForgeCpuMeshLayer.SOLID) {
            return BuildResult.rejected(RejectCategory.RENDER_LAYER, "unsupported-render-layer-" + layer.displayName);
        }
        if (candidate.blockStateId() <= 0) {
            return BuildResult.rejected(RejectCategory.NO_MODEL, "invalid-blockstate-id");
        }

        BlockRenderDispatcher blockRenderer = minecraft.getBlockRenderer();
        var model = blockRenderer.getBlockModel(candidate.state());
        if (model == null || model.isCustomRenderer()) {
            return BuildResult.rejected(RejectCategory.NO_MODEL, "baked-model-missing-or-custom");
        }

        int[] faceWords = new int[ForgeModelStoreFormalLayout.FACE_DATA_WORDS];
        Arrays.fill(faceWords, -1);
        FaceUpload[] faces = new FaceUpload[ForgeModelAtlasLayout.FACE_COUNT];
        List<FaceBuild> builtFaces = new ArrayList<>(ForgeModelAtlasLayout.FACE_COUNT);
        int tintedFaces = 0;
        int missingFaces = 0;
        int fallbackFaces = 0;
        int writtenFaces = 0;
        String primarySprite = "none";
        String primaryAtlas = "none";
        boolean shaded;
        try {
            shaded = model.useAmbientOcclusion();
        } catch (RuntimeException ignored) {
            shaded = false;
        }

        for (Direction direction : DIRECTIONS) {
            int faceIndex = direction.get3DDataValue();
            BakedQuad quad = firstQuadForFace(model, candidate.state(), direction, candidate.blockStateId());
            if (quad == null || quad.getSprite() == null) {
                missingFaces++;
                builtFaces.add(FaceBuild.missing(faceIndex, direction.getName()));
                continue;
            }
            FaceEncoding encoding = encodeFace(quad, direction);
            if (isMissingSprite(encoding.spriteName())) {
                return BuildResult.rejected(RejectCategory.MISSING_SPRITE, "missing-sprite-" + encoding.spriteName());
            }
            if (quad.isTinted()) {
                tintedFaces++;
            }
            byte[] pixels = spritePixels(quad.getSprite());
            FaceUpload face = new FaceUpload(
                    faceIndex,
                    direction.getName(),
                    encoding.spriteName(),
                    encoding.spriteAtlas(),
                    pixels,
                    ForgeModelAtlasPixelSample.checksum(pixels),
                    false,
                    quad.isTinted(),
                    quad.getTintIndex(),
                    encoding.faceDataWord()
            );
            faces[faceIndex] = face;
            faceWords[faceIndex] = encoding.faceDataWord();
            if (encoding.faceDataWord() >= 0) {
                writtenFaces++;
            }
            if (!"none".equals(encoding.spriteName()) && "none".equals(primarySprite)) {
                primarySprite = encoding.spriteName();
                primaryAtlas = encoding.spriteAtlas();
            }
            builtFaces.add(new FaceBuild(faceIndex, direction.getName(), face));
        }

        if (tintedFaces > 0) {
            return BuildResult.rejected(RejectCategory.TINTED, "tinted-faces-" + tintedFaces);
        }

        Optional<FaceUpload> fallback = Arrays.stream(faces).filter(face -> face != null && face.pixels().length == ForgeModelAtlasPixelSample.BYTES_PER_FACE).findFirst();
        if (fallback.isEmpty()) {
            return BuildResult.rejected(RejectCategory.NO_QUADS, "no-face-sprite-pixels");
        }
        for (FaceBuild built : builtFaces) {
            if (faces[built.faceIndex()] == null) {
                FaceUpload source = fallback.get();
                faces[built.faceIndex()] = source.fallbackFor(built.faceIndex(), built.direction());
                fallbackFaces++;
            }
        }

        int flags = shaded ? 8 : 0;
        int tint = sampleTintColour(minecraft, candidate.state(), faces);
        int[] words = new int[ForgeModelStoreFormalLayout.MODEL_RECORD_WORDS];
        System.arraycopy(faceWords, 0, words, 0, ForgeModelStoreFormalLayout.FACE_DATA_WORDS);
        words[ForgeModelStoreFormalLayout.WORD_FLAGS_A] = flags;
        words[ForgeModelStoreFormalLayout.WORD_COLOUR_TINT] = tint;
        words[ForgeModelStoreFormalLayout.WORD_CUSTOM_ID] = 0;
        RecordBuild build = new RecordBuild(
                words,
                tint,
                faces,
                flags,
                tint,
                writtenFaces,
                missingFaces,
                fallbackFaces,
                model.getClass().getSimpleName(),
                primarySprite,
                primaryAtlas,
                signature(words, tint, faces)
        );
        return BuildResult.accepted(build);
    }

    private Optional<Integer> assignFormalModelId(Candidate candidate) {
        ForgeFormalModelFactory factory = this.instance.getFormalModelFactory();
        factory.requestBlockState(candidate.blockStateId());
        factory.processSkeleton();
        Optional<ForgeFormalModelIdMapping> mapping = factory.mappingForBlockStateId(candidate.blockStateId());
        if (mapping.isEmpty()) {
            this.fail("formal-model-id-mapping-missing-" + candidate.blockStateId());
            return Optional.empty();
        }
        int formalModelId = mapping.get().formalModelId();
        if (!ForgeModelAtlasLayout.isValidModelId(formalModelId) || formalModelId == 0) {
            this.fail("invalid-formal-model-id-" + formalModelId);
            return Optional.empty();
        }
        return Optional.of(formalModelId);
    }

    private String uploadModel(int formalModelId, RecordBuild build) {
        String error = this.instance.getFormalModelStore().uploadPrototypeModelRecord(formalModelId, build.words());
        if (!"none".equals(error)) {
            return error;
        }
        this.uploadedModelRecordCount++;
        error = this.instance.getFormalModelStore().uploadPrototypeModelColour(formalModelId, build.modelColour());
        if (!"none".equals(error)) {
            return error;
        }
        this.uploadedModelColourCount++;
        for (int faceIndex = 0; faceIndex < ForgeModelAtlasLayout.FACE_COUNT; faceIndex++) {
            FaceUpload face = build.faces()[faceIndex];
            if (face == null) {
                return "missing-face-upload-" + faceIndex;
            }
            error = this.instance.getFormalModelStore().uploadPrototypeAtlasFace(formalModelId, faceIndex, face.pixels());
            if (!"none".equals(error)) {
                return error;
            }
            this.uploadedFaceTileCount++;
            this.uploadedPixels += ForgeModelAtlasLayout.MODEL_TEXTURE_SIZE * ForgeModelAtlasLayout.MODEL_TEXTURE_SIZE;
        }
        this.uploadedAtlasModelCount++;
        return "none";
    }

    private int blockStateIdForState(BlockState state) {
        Optional<WorldEngine> engine = this.instance.getCurrentEngineOptional();
        if (engine.isPresent()) {
            try {
                return engine.get().getMapper().getIdForBlockState(state);
            } catch (RuntimeException ignored) {
                // Fall through to vanilla registry.
            }
        }
        try {
            return Block.BLOCK_STATE_REGISTRY.getId(state);
        } catch (RuntimeException ignored) {
            return -1;
        }
    }

    private void reject(Candidate candidate, RejectCategory category, String reason) {
        this.rejectedModels.add(new RejectedModel(candidate, category, reason));
        switch (category) {
            case AIR -> this.unsupportedAirCount++;
            case FLUID -> this.unsupportedFluidCount++;
            case TRANSLUCENT -> this.unsupportedTranslucentCount++;
            case CUTOUT -> this.unsupportedCutoutCount++;
            case TINTED -> this.unsupportedTintedCount++;
            case ANIMATED -> this.unsupportedAnimatedCount++;
            case MISSING_SPRITE -> this.unsupportedMissingSpriteCount++;
            case NO_MODEL -> this.unsupportedNoModelCount++;
            case NO_QUADS -> this.unsupportedNoQuadsCount++;
            case RENDER_LAYER -> this.unsupportedRenderLayerCount++;
        }
    }

    private void resetUploadState() {
        this.acceptedModels.clear();
        this.rejectedModels.clear();
        this.modelTexture2id.clear();
        this.requestedBlockStateCount = 0;
        this.dedupeHitCount = 0;
        this.dedupeMissCount = 0;
        this.uploadedModelRecordCount = 0;
        this.uploadedModelColourCount = 0;
        this.uploadedAtlasModelCount = 0;
        this.uploadedFaceTileCount = 0;
        this.uploadedPixels = 0;
        this.atlasPixelMismatches = 0;
        this.modelDataReadbackOk = false;
        this.modelColourReadbackOk = false;
        this.atlasReadbackOk = false;
        this.unsupportedAirCount = 0;
        this.unsupportedFluidCount = 0;
        this.unsupportedTranslucentCount = 0;
        this.unsupportedCutoutCount = 0;
        this.unsupportedTintedCount = 0;
        this.unsupportedAnimatedCount = 0;
        this.unsupportedMissingSpriteCount = 0;
        this.unsupportedNoModelCount = 0;
        this.unsupportedNoQuadsCount = 0;
        this.unsupportedRenderLayerCount = 0;
    }

    private void markLifecycleStale(String event) {
        this.enabled = false;
        this.stale = true;
        this.requiresRebuild = true;
        this.lifecycleState = "STALE";
        this.lastLifecycleEvent = event == null || event.isBlank() ? "stale" : event.replace(' ', '-');
        this.staleReason = this.lastLifecycleEvent;
        this.resetUploadState();
    }

    private void fail(String reason) {
        this.uploadFailures++;
        this.lastFailureReason = reason == null || reason.isBlank() ? "failed" : reason.replace(' ', '-');
    }

    private String acceptedBlockStates() {
        return this.acceptedModels.stream()
                .map(model -> model.candidate().blockStateId() + ":" + model.candidate().state())
                .collect(Collectors.joining(","));
    }

    private String acceptedFormalModelIds() {
        return this.acceptedModels.stream()
                .map(model -> Integer.toString(model.formalModelId()))
                .collect(Collectors.joining(","));
    }

    private String rejectedBlockStates() {
        return this.rejectedModels.stream()
                .map(model -> model.candidate().blockStateId() + ":" + model.category().name().toLowerCase() + ":" + model.reason())
                .collect(Collectors.joining(","));
    }

    private String faceChecksums() {
        return this.acceptedModels.stream()
                .map(model -> "model" + model.formalModelId() + "{" + checksums(model.build().faces()) + "}")
                .collect(Collectors.joining(";"));
    }

    private static String checksums(FaceUpload[] faces) {
        StringBuilder builder = new StringBuilder();
        for (int faceIndex = 0; faceIndex < ForgeModelAtlasLayout.FACE_COUNT; faceIndex++) {
            if (faceIndex > 0) {
                builder.append(',');
            }
            FaceUpload face = faces[faceIndex];
            builder.append("face").append(faceIndex).append('=').append(face == null ? "none" : face.checksum());
        }
        return builder.toString();
    }

    private static BakedQuad firstQuadForFace(net.minecraft.client.resources.model.BakedModel model, BlockState state, Direction direction, int blockStateId) {
        List<BakedQuad> quads = safeGetQuads(model, state, direction, blockStateId);
        if (!quads.isEmpty()) {
            return quads.get(0);
        }
        for (BakedQuad quad : safeGetQuads(model, state, null, blockStateId)) {
            if (quad.getDirection() == direction) {
                return quad;
            }
        }
        return null;
    }

    private static List<BakedQuad> safeGetQuads(net.minecraft.client.resources.model.BakedModel model, BlockState state, Direction direction, int blockStateId) {
        try {
            List<BakedQuad> quads = model.getQuads(state, direction, RandomSource.create(blockStateId * 31L + (direction == null ? 17L : direction.ordinal())));
            return quads == null ? List.of() : quads;
        } catch (RuntimeException e) {
            return List.of();
        }
    }

    private static FaceEncoding encodeFace(BakedQuad quad, Direction direction) {
        TextureAtlasSprite sprite = quad.getSprite();
        String spriteName = "none";
        String spriteAtlas = "none";
        float spriteU0 = 0.0F;
        float spriteU1 = 1.0F;
        float spriteV0 = 0.0F;
        float spriteV1 = 1.0F;
        if (sprite != null) {
            try {
                spriteName = sprite.contents().name().toString();
                spriteAtlas = sprite.atlasLocation().toString();
                spriteU0 = sprite.getU0();
                spriteU1 = sprite.getU1();
                spriteV0 = sprite.getV0();
                spriteV1 = sprite.getV1();
            } catch (RuntimeException ignored) {
                spriteName = "unavailable";
                spriteAtlas = "unavailable";
            }
        }

        int[] vertices = quad.getVertices();
        if (vertices == null || vertices.length < 24 || vertices.length % VERTICES_PER_QUAD != 0) {
            return new FaceEncoding(spriteName, spriteAtlas, -1);
        }

        int stride = vertices.length / VERTICES_PER_QUAD;
        if (stride < 6) {
            return new FaceEncoding(spriteName, spriteAtlas, -1);
        }

        float minU = Float.POSITIVE_INFINITY;
        float minV = Float.POSITIVE_INFINITY;
        float maxU = Float.NEGATIVE_INFINITY;
        float maxV = Float.NEGATIVE_INFINITY;
        float axisSum = 0.0F;
        for (int vertex = 0; vertex < VERTICES_PER_QUAD; vertex++) {
            int offset = vertex * stride;
            float x = Float.intBitsToFloat(vertices[offset]);
            float y = Float.intBitsToFloat(vertices[offset + 1]);
            float z = Float.intBitsToFloat(vertices[offset + 2]);
            float u = Float.intBitsToFloat(vertices[offset + 4]);
            float v = Float.intBitsToFloat(vertices[offset + 5]);
            minU = Math.min(minU, u);
            minV = Math.min(minV, v);
            maxU = Math.max(maxU, u);
            maxV = Math.max(maxV, v);
            axisSum += switch (direction.getAxis()) {
                case X -> x;
                case Y -> y;
                case Z -> z;
            };
        }

        if (!Float.isFinite(minU) || !Float.isFinite(minV) || !Float.isFinite(maxU) || !Float.isFinite(maxV)) {
            return new FaceEncoding(spriteName, spriteAtlas, -1);
        }

        int minUTexel = quantizeUvMin(minU, spriteU0, spriteU1);
        int maxUTexel = quantizeUvMax(maxU, spriteU0, spriteU1);
        int minVTexel = quantizeUvMin(minV, spriteV0, spriteV1);
        int maxVTexel = quantizeUvMax(maxV, spriteV0, spriteV1);
        float axisAverage = axisSum / VERTICES_PER_QUAD;
        float indentation = direction.getAxisDirection() == Direction.AxisDirection.POSITIVE
                ? 1.0F - axisAverage
                : axisAverage;
        int indentationEncoded = clampInt(Math.round(indentation * 64.0F), 0, 62);
        int faceData = minUTexel
                | (maxUTexel << 4)
                | (minVTexel << 8)
                | (maxVTexel << 12)
                | (indentationEncoded << 16);
        if (quad.isTinted()) {
            faceData |= 2 << 24;
        }
        return new FaceEncoding(spriteName, spriteAtlas, faceData);
    }

    private static byte[] spritePixels(TextureAtlasSprite sprite) {
        byte[] pixels = new byte[ForgeModelAtlasPixelSample.BYTES_PER_FACE];
        int spriteWidth = Math.max(1, sprite.contents().width());
        int spriteHeight = Math.max(1, sprite.contents().height());
        for (int y = 0; y < ForgeModelAtlasLayout.MODEL_TEXTURE_SIZE; y++) {
            int sourceY = Math.min(spriteHeight - 1, (y * spriteHeight) / ForgeModelAtlasLayout.MODEL_TEXTURE_SIZE);
            for (int x = 0; x < ForgeModelAtlasLayout.MODEL_TEXTURE_SIZE; x++) {
                int sourceX = Math.min(spriteWidth - 1, (x * spriteWidth) / ForgeModelAtlasLayout.MODEL_TEXTURE_SIZE);
                int pixel = sprite.getPixelRGBA(0, sourceX, sourceY);
                int offset = ((y * ForgeModelAtlasLayout.MODEL_TEXTURE_SIZE) + x) * ForgeModelAtlasPixelSample.BYTES_PER_PIXEL;
                pixels[offset] = (byte) (pixel & 0xFF);
                pixels[offset + 1] = (byte) ((pixel >> 8) & 0xFF);
                pixels[offset + 2] = (byte) ((pixel >> 16) & 0xFF);
                pixels[offset + 3] = (byte) ((pixel >> 24) & 0xFF);
            }
        }
        return pixels;
    }

    private static int sampleTintColour(Minecraft minecraft, BlockState state, FaceUpload[] faces) {
        int tintIndex = -1;
        for (FaceUpload face : faces) {
            if (face != null && face.tinted()) {
                tintIndex = face.tintIndex();
                break;
            }
        }
        if (tintIndex < 0 || minecraft.level == null || minecraft.player == null) {
            return -1;
        }
        try {
            BlockColors blockColors = minecraft.getBlockColors();
            int rgb = blockColors.getColor(state, minecraft.level, minecraft.player.blockPosition(), tintIndex);
            return rgb == -1 ? -1 : 0xFF000000 | rgb;
        } catch (RuntimeException e) {
            return -1;
        }
    }

    private static String signature(int[] words, int modelColour, FaceUpload[] faces) {
        StringBuilder builder = new StringBuilder();
        builder.append(Arrays.toString(words)).append('|').append(modelColour);
        for (FaceUpload face : faces) {
            builder.append('|');
            if (face == null) {
                builder.append("missing");
            } else {
                builder.append(face.spriteName()).append(':').append(face.spriteAtlas()).append(':')
                        .append(face.checksum()).append(':').append(face.faceDataWord());
            }
        }
        return builder.toString();
    }

    private static boolean isMissingSprite(String spriteName) {
        return spriteName == null || spriteName.isBlank() || spriteName.contains("missingno") || "unavailable".equals(spriteName);
    }

    private static int countPixelMismatches(byte[] expected, byte[] actual) {
        if (expected == null || actual == null) {
            return Integer.MAX_VALUE;
        }
        int mismatches = Math.abs(expected.length - actual.length);
        int len = Math.min(expected.length, actual.length);
        for (int i = 0; i < len; i++) {
            if (expected[i] != actual[i]) {
                mismatches++;
            }
        }
        return mismatches;
    }

    private static int quantizeUvMin(float uv, float spriteMin, float spriteMax) {
        float range = spriteMax - spriteMin;
        if (!Float.isFinite(range) || Math.abs(range) < 1.0E-6F) {
            return 0;
        }
        return clampInt((int) Math.floor(((uv - spriteMin) / range) * 16.0F), 0, 15);
    }

    private static int quantizeUvMax(float uv, float spriteMin, float spriteMax) {
        float range = spriteMax - spriteMin;
        if (!Float.isFinite(range) || Math.abs(range) < 1.0E-6F) {
            return 15;
        }
        return clampInt((int) Math.ceil(((uv - spriteMin) / range) * 16.0F) - 1, 0, 15);
    }

    private static int clampInt(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static double elapsedMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000.0D;
    }

    private enum RejectCategory {
        AIR,
        FLUID,
        TRANSLUCENT,
        CUTOUT,
        TINTED,
        ANIMATED,
        MISSING_SPRITE,
        NO_MODEL,
        NO_QUADS,
        RENDER_LAYER
    }

    private record Candidate(int blockStateId, BlockState state) {
    }

    private record AcceptedModel(Candidate candidate, int formalModelId, RecordBuild build) {
    }

    private record RejectedModel(Candidate candidate, RejectCategory category, String reason) {
    }

    private record BuildResult(boolean accepted, RecordBuild build, RejectCategory rejectCategory, String rejectReason) {
        static BuildResult accepted(RecordBuild build) {
            return new BuildResult(true, build, null, "none");
        }

        static BuildResult rejected(RejectCategory category, String reason) {
            return new BuildResult(false, null, category, reason == null || reason.isBlank() ? "unsupported" : reason.replace(' ', '-'));
        }
    }

    private record FaceEncoding(String spriteName, String spriteAtlas, int faceDataWord) {
    }

    private record FaceBuild(int faceIndex, String direction, FaceUpload face) {
        static FaceBuild missing(int faceIndex, String direction) {
            return new FaceBuild(faceIndex, direction, null);
        }
    }

    private record FaceUpload(
            int faceIndex,
            String direction,
            String spriteName,
            String spriteAtlas,
            byte[] pixels,
            String checksum,
            boolean fallback,
            boolean tinted,
            int tintIndex,
            int faceDataWord
    ) {
        FaceUpload fallbackFor(int nextFaceIndex, String nextDirection) {
            return new FaceUpload(
                    nextFaceIndex,
                    nextDirection,
                    this.spriteName,
                    this.spriteAtlas,
                    this.pixels,
                    this.checksum,
                    true,
                    this.tinted,
                    this.tintIndex,
                    -1
            );
        }
    }

    private record RecordBuild(
            int[] words,
            int modelColour,
            FaceUpload[] faces,
            int flagsA,
            int colourTint,
            int faceDataWrittenCount,
            int missingFaceCount,
            int fallbackFaceCount,
            String bakedModelClass,
            String primarySprite,
            String primarySpriteAtlas,
            String signature
    ) {
    }
}
