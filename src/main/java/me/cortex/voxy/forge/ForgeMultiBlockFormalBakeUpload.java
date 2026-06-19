package me.cortex.voxy.forge;

import com.mojang.blaze3d.systems.RenderSystem;
import me.cortex.voxy.common.world.WorldEngine;
import net.minecraft.client.Minecraft;
import net.minecraft.client.color.block.BlockColors;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
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
    private static final int MIN_ACCEPTED_BLOCKS = 3;

    private final ForgeVoxyInstance instance;
    private final List<AcceptedModel> acceptedModels = new ArrayList<>();
    private final List<RejectedModel> rejectedModels = new ArrayList<>();
    private final Map<String, Integer> modelTexture2id = new LinkedHashMap<>();
    private final ForgeSoftwareModelTextureBakery softwareBakery = new ForgeSoftwareModelTextureBakery();
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
                        ForgeModelStoreFormalLayout.MODEL_RECORD_BYTES,
                        model.build().voxyMetadata(),
                        model.build().fluidFormalModelId()
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
                this.candidateFor(Blocks.CLAY.defaultBlockState()),
                this.candidateFor(Blocks.OAK_LEAVES.defaultBlockState()),
                this.candidateFor(Blocks.GLASS.defaultBlockState()),
                this.candidateFor(Blocks.WATER.defaultBlockState())
        );
    }

    private Candidate candidateFor(BlockState state) {
        return new Candidate(this.blockStateIdForState(state), state);
    }

    private BuildResult tryBuildRecord(Minecraft minecraft, Candidate candidate) {
        if (candidate.state() == null || candidate.state().isAir()) {
            return BuildResult.rejected(RejectCategory.AIR, "air");
        }
        ForgeCpuMeshLayer layer = ForgeCpuMeshLayer.fromBlockState(candidate.state());
        if (layer == ForgeCpuMeshLayer.OTHER) {
            return BuildResult.rejected(RejectCategory.RENDER_LAYER, "unsupported-render-layer-" + layer.displayName);
        }
        if (candidate.blockStateId() <= 0) {
            return BuildResult.rejected(RejectCategory.NO_MODEL, "invalid-blockstate-id");
        }

        ForgeSoftwareModelTextureBakery.BakeResult softwareBake = this.softwareBakery.renderToOutput(minecraft, candidate.state(), candidate.blockStateId());
        if (!"none".equals(softwareBake.failureReason())) {
            return BuildResult.rejected("no-quads".equals(softwareBake.failureReason()) ? RejectCategory.NO_QUADS : RejectCategory.NO_MODEL, softwareBake.failureReason());
        }
        layer = softwareBake.layer();
        if (layer == ForgeCpuMeshLayer.OTHER) {
            return BuildResult.rejected(RejectCategory.RENDER_LAYER, "software-bakery-unsupported-render-layer");
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
        boolean shaded = (softwareBake.flags() & 1) != 0;

        for (Direction direction : DIRECTIONS) {
            int faceIndex = direction.get3DDataValue();
            ForgeSoftwareModelTextureBakery.FaceTexture faceTexture = softwareBake.faces()[faceIndex];
            int writtenPixels = faceTexture == null ? 0 : faceTexture.writtenPixelCount(layer);
            if (writtenPixels == 0) {
                missingFaces++;
                builtFaces.add(FaceBuild.missing(faceIndex, direction.getName()));
                continue;
            }
            int tintState = faceTexture.tintState(layer);
            if (tintState == 2 || tintState == 3) {
                tintedFaces++;
            }
            byte[] pixels = ForgeSoftwareModelTextureBakery.rgbaBytes(faceTexture);
            int faceDataWord = encodeSoftwareFaceData(faceTexture, layer);
            FaceUpload face = new FaceUpload(
                    faceIndex,
                    direction.getName(),
                    "software-bakery-face-" + direction.getName(),
                    "minecraft:block-atlas-software-rasterized",
                    pixels,
                    ForgeModelAtlasPixelSample.checksum(pixels),
                    false,
                    tintState == 2 || tintState == 3,
                    0,
                    faceDataWord
            );
            faces[faceIndex] = face;
            faceWords[faceIndex] = faceDataWord;
            if (faceDataWord >= 0) {
                writtenFaces++;
            }
            if ("none".equals(primarySprite)) {
                primarySprite = face.spriteName();
                primaryAtlas = face.spriteAtlas();
            }
            builtFaces.add(new FaceBuild(faceIndex, direction.getName(), face));
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
        int fluidFormalModelId = resolveFluidFormalModelId(candidate.state());
        long voxyMetadata = buildVoxyMetadata(candidate.state(), layer, faces, tintedFaces > 0, fluidFormalModelId);
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
                "ForgeSoftwareModelTextureBakery",
                primarySprite,
                primaryAtlas,
                signature(words, tint, faces),
                voxyMetadata,
                fluidFormalModelId
        );
        return BuildResult.accepted(build);
    }

    private int resolveFluidFormalModelId(BlockState state) {
        if (state == null || state.getFluidState().isEmpty() || state.getBlock() instanceof LiquidBlock) {
            return -1;
        }
        try {
            BlockState fluidBlockState = state.getFluidState().createLegacyBlock();
            int fluidBlockStateId = this.blockStateIdForState(fluidBlockState);
            Optional<ForgeFormalModelIdMapping> mapping = this.instance.getFormalModelFactory().mappingForBlockStateId(fluidBlockStateId);
            return mapping.map(ForgeFormalModelIdMapping::formalModelId).orElse(-1);
        } catch (RuntimeException ignored) {
            return -1;
        }
    }

    private static long buildVoxyMetadata(
            BlockState state,
            ForgeCpuMeshLayer layer,
            FaceUpload[] faces,
            boolean biomeColourDependent,
            int fluidFormalModelId
    ) {
        boolean isFluid = state.getBlock() instanceof LiquidBlock;
        boolean containsFluid = !isFluid && !state.getFluidState().isEmpty();
        boolean translucent = layer == ForgeCpuMeshLayer.TRANSLUCENT || isFluid;
        boolean doubleSided = needsDoubleSidedQuads(faces, layer);
        boolean cullsSame = cullsSame(state);
        boolean fullyOpaque = true;
        long metadata = 0L;
        for (int face = ForgeModelAtlasLayout.FACE_COUNT - 1; face >= 0; face--) {
            metadata <<= 8;
            FaceUpload upload = faces[face];
            if (upload == null || upload.faceDataWord() < 0) {
                metadata |= 0xFFL;
                fullyOpaque = false;
                continue;
            }
            int faceData = upload.faceDataWord();
            int minU = faceData & 0xF;
            int maxU = (faceData >>> 4) & 0xF;
            int minV = (faceData >>> 8) & 0xF;
            int maxV = (faceData >>> 12) & 0xF;
            int depth = (faceData >>> 16) & 0x3F;
            int writtenPixels = writtenPixelCount(upload.pixels(), layer);
            int area = Math.max(1, (maxU - minU + 1) * (maxV - minV + 1));
            boolean faceCoversFullBlock = minU == 0 && maxU == 15 && minV == 0 && maxV == 15;
            boolean occludesFace = layer != ForgeCpuMeshLayer.TRANSLUCENT
                    && !isFluid
                    && depth < 7
                    && ((float) writtenPixels / (float) (ForgeModelAtlasLayout.MODEL_TEXTURE_SIZE * ForgeModelAtlasLayout.MODEL_TEXTURE_SIZE)) > 0.9F;
            boolean canBeOccluded = depth < 20;
            boolean selfLighting = depth > 1 || translucent;
            long faceMetadata = 0L;
            faceMetadata |= occludesFace ? 1L : 0L;
            faceMetadata |= faceCoversFullBlock ? 2L : 0L;
            faceMetadata |= canBeOccluded ? 4L : 0L;
            faceMetadata |= selfLighting ? 8L : 0L;
            metadata |= faceMetadata;
            fullyOpaque &= occludesFace && area == 256;
        }
        long global = 0L;
        global |= biomeColourDependent ? 1L : 0L;
        global |= translucent ? 2L : 0L;
        global |= doubleSided ? 4L : 0L;
        global |= containsFluid ? 8L : 0L;
        global |= isFluid ? 16L : 0L;
        global |= cullsSame ? 32L : 0L;
        global |= fullyOpaque ? 64L : 0L;
        global |= ((long) clampInt(state.getLightEmission(), 0, 15)) << 7;
        metadata |= global << (8 * ForgeModelAtlasLayout.FACE_COUNT);
        return metadata;
    }

    private static boolean needsDoubleSidedQuads(FaceUpload[] faces, ForgeCpuMeshLayer layer) {
        if (layer == ForgeCpuMeshLayer.SOLID) {
            return false;
        }
        return isMissingFace(faces, 0) && isMissingFace(faces, 1)
                || isMissingFace(faces, 2) && isMissingFace(faces, 3)
                || isMissingFace(faces, 4) && isMissingFace(faces, 5);
    }

    private static boolean isMissingFace(FaceUpload[] faces, int face) {
        return faces == null || face < 0 || face >= faces.length || faces[face] == null || faces[face].faceDataWord() < 0;
    }

    private static boolean cullsSame(BlockState state) {
        boolean allTrue = true;
        boolean allFalse = true;
        for (Direction direction : DIRECTIONS) {
            if (state.skipRendering(state, direction)) {
                allFalse = false;
            } else {
                allTrue = false;
            }
        }
        return allTrue && !allFalse;
    }

    private static int writtenPixelCount(byte[] pixels, ForgeCpuMeshLayer layer) {
        if (pixels == null || pixels.length < ForgeModelAtlasPixelSample.BYTES_PER_PIXEL) {
            return 0;
        }
        int count = 0;
        for (int i = ForgeModelAtlasPixelSample.BYTES_PER_PIXEL - 1; i < pixels.length; i += ForgeModelAtlasPixelSample.BYTES_PER_PIXEL) {
            int alpha = pixels[i] & 0xFF;
            if (layer == ForgeCpuMeshLayer.TRANSLUCENT ? alpha > 0 : alpha >= 128) {
                count++;
            }
        }
        return count;
    }

    Optional<ForgeFormalUploadedModelSummary> ensureUploadedBlockState(int blockStateId, BlockState state) {
        for (AcceptedModel model : this.acceptedModels) {
            if (model.candidate().blockStateId() == blockStateId) {
                return Optional.of(new ForgeFormalUploadedModelSummary(
                        model.candidate().blockStateId(),
                        model.candidate().state().toString(),
                        model.formalModelId(),
                        model.build().signature(),
                        model.build().primarySprite(),
                        ForgeModelStoreFormalLayout.MODEL_RECORD_BYTES,
                        model.build().voxyMetadata(),
                        model.build().fluidFormalModelId()
                ));
            }
        }
        if (state == null || state.isAir()) {
            return Optional.empty();
        }
        Minecraft minecraft = Minecraft.getInstance();
        BuildResult result = this.tryBuildRecord(minecraft, new Candidate(blockStateId, state));
        if (!result.accepted()) {
            this.reject(new Candidate(blockStateId, state), result.rejectCategory(), result.rejectReason());
            this.lastFailureReason = "on-demand-formal-model-rejected:" + result.rejectReason();
            return Optional.empty();
        }
        Optional<Integer> formalModelId = this.assignFormalModelId(new Candidate(blockStateId, state));
        if (formalModelId.isEmpty()) {
            return Optional.empty();
        }
        String uploadError = this.uploadModel(formalModelId.get(), result.build());
        if (!"none".equals(uploadError)) {
            this.fail(uploadError);
            return Optional.empty();
        }
        AcceptedModel accepted = new AcceptedModel(new Candidate(blockStateId, state), formalModelId.get(), result.build());
        this.acceptedModels.add(accepted);
        this.modelTexture2id.putIfAbsent(result.build().signature(), formalModelId.get());
        return Optional.of(new ForgeFormalUploadedModelSummary(
                blockStateId,
                state.toString(),
                formalModelId.get(),
                result.build().signature(),
                result.build().primarySprite(),
                ForgeModelStoreFormalLayout.MODEL_RECORD_BYTES,
                result.build().voxyMetadata(),
                result.build().fluidFormalModelId()
        ));
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

    private static int encodeSoftwareFaceData(ForgeSoftwareModelTextureBakery.FaceTexture texture, ForgeCpuMeshLayer layer) {
        int[] bounds = texture.bounds(layer);
        if (bounds[1] < bounds[0] || bounds[3] < bounds[2]) {
            return -1;
        }
        float depth = texture.depth(layer, layer != ForgeCpuMeshLayer.SOLID);
        if (depth < -0.1F) {
            return -1;
        }
        int minU = clampInt(bounds[0], 0, 15);
        int maxU = clampInt(bounds[1], 0, 15);
        int minV = clampInt(bounds[2], 0, 15);
        int maxV = clampInt(bounds[3], 0, 15);
        int depthEncoded = clampInt(Math.round(depth * 64.0F), 0, 62);
        int faceData = minU
                | (maxU << 4)
                | (minV << 8)
                | (maxV << 12)
                | (depthEncoded << 16);
        int area = Math.max(1, (maxU - minU + 1) * (maxV - minV + 1));
        int written = texture.writtenPixelCount(layer);
        boolean faceCoversFullBlock = minU == 0 && maxU == 15 && minV == 0 && maxV == 15;
        boolean needsAlphaDiscard = ((float) written / (float) area) < 0.9F;
        needsAlphaDiscard |= layer != ForgeCpuMeshLayer.SOLID;
        needsAlphaDiscard &= layer != ForgeCpuMeshLayer.TRANSLUCENT;
        faceData |= needsAlphaDiscard ? 1 << 22 : 0;
        faceData |= (!faceCoversFullBlock && layer != ForgeCpuMeshLayer.TRANSLUCENT) ? 1 << 23 : 0;
        int tintState = texture.tintState(layer);
        if (tintState == 2) {
            faceData |= 1 << 24;
        } else if (tintState == 3) {
            faceData |= 2 << 24;
        }
        return faceData;
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
            String signature,
            long voxyMetadata,
            int fluidFormalModelId
    ) {
    }
}
