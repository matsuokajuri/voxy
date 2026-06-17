package me.cortex.voxy.forge;

import com.mojang.blaze3d.systems.RenderSystem;
import me.cortex.voxy.common.world.WorldEngine;
import net.minecraft.client.Minecraft;
import net.minecraft.client.color.block.BlockColors;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

final class ForgeOneBlockFormalBakeUpload {
    static final String STAGE = "I4_REAL_ONE_BLOCK_BAKE_UPLOAD_PROTOTYPE";
    private static final Direction[] DIRECTIONS = Direction.values();
    private static final int VERTICES_PER_QUAD = 4;
    private static final int SAMPLE_SEARCH_DOWN_BLOCKS = 128;

    private final ForgeVoxyInstance instance;
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
    private boolean resourceReloadSeen;
    private boolean worldUnloadSeen;
    private boolean dimensionSwitchSeen;
    private boolean debugPipelineClearSeen;
    private boolean presetOffSeen;
    private boolean presetClearSeen;

    private Candidate candidate;
    private int formalModelId = -1;
    private int[] modelRecordWords;
    private int modelColour;
    private FaceUpload[] faceUploads = new FaceUpload[ForgeModelAtlasLayout.FACE_COUNT];
    private boolean oneBlockRealBakeReady;
    private boolean modelRecordUploaded;
    private boolean modelColourUploaded;
    private boolean atlasPixelsUploaded;
    private int uploadedFaceTiles;
    private int uploadedPixels;
    private int missingFaceCount;
    private int fallbackFaceCount;
    private int faceDataWrittenCount;
    private boolean sourceTinted;
    private int flagsA;
    private int colourTint = -1;
    private String sourceBakedModelClass = "none";
    private String sourcePrimarySprite = "none";
    private String sourcePrimarySpriteAtlas = "none";
    private String lastFailureReason = "none";
    private boolean modelDataReadbackOk;
    private boolean modelColourReadbackOk;
    private boolean atlasReadbackOk;
    private int atlasPixelMismatches;
    private ForgeOneBlockFormalBakeUploadAuditResult lastAudit =
            ForgeOneBlockFormalBakeUploadAuditResult.failure("none", 0.0D);

    ForgeOneBlockFormalBakeUpload(ForgeVoxyInstance instance) {
        this.instance = instance;
    }

    ForgeOneBlockFormalBakeUploadStats bakeOneCurrent() {
        this.buildRuns++;
        this.enabled = true;
        this.stale = false;
        this.requiresRebuild = false;
        this.lifecycleState = "BUILT";
        this.lastLifecycleEvent = "bake-one-current";
        this.staleReason = "none";
        this.lastFailureReason = "none";
        this.resetUploadState(false);

        if (!RenderSystem.isOnRenderThread()) {
            this.fail("not-render-thread");
            return this.createStatusSnapshot();
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null || minecraft.getBlockRenderer() == null) {
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

        Optional<Candidate> selected = this.selectCandidate(minecraft);
        if (selected.isEmpty()) {
            this.fail("no-safe-solid-block-candidate");
            return this.createStatusSnapshot();
        }
        this.candidate = selected.get();

        ForgeFormalModelFactory factory = this.instance.getFormalModelFactory();
        factory.requestBlockState(this.candidate.blockStateId());
        factory.processSkeleton();
        Optional<ForgeFormalModelIdMapping> mapping = factory.mappingForBlockStateId(this.candidate.blockStateId());
        if (mapping.isEmpty()) {
            this.fail("formal-model-id-mapping-missing");
            return this.createStatusSnapshot();
        }
        this.formalModelId = mapping.get().formalModelId();
        if (!ForgeModelAtlasLayout.isValidModelId(this.formalModelId) || this.formalModelId == 0) {
            this.fail("invalid-formal-model-id-" + this.formalModelId);
            return this.createStatusSnapshot();
        }

        Optional<RecordBuild> record = this.buildRecord(minecraft, this.candidate);
        if (record.isEmpty()) {
            if ("none".equals(this.lastFailureReason)) {
                this.fail("record-build-failed");
            }
            return this.createStatusSnapshot();
        }

        RecordBuild build = record.get();
        this.modelRecordWords = build.words();
        this.modelColour = build.modelColour();
        this.faceUploads = build.faces();
        this.flagsA = build.flagsA();
        this.colourTint = build.colourTint();
        this.faceDataWrittenCount = build.faceDataWrittenCount();
        this.missingFaceCount = build.missingFaceCount();
        this.fallbackFaceCount = build.fallbackFaceCount();
        this.sourceTinted = build.sourceTinted();
        this.sourceBakedModelClass = build.bakedModelClass();
        this.sourcePrimarySprite = build.sourcePrimarySprite();
        this.sourcePrimarySpriteAtlas = build.sourcePrimarySpriteAtlas();
        this.oneBlockRealBakeReady = true;

        String error = this.instance.getFormalModelStore().uploadPrototypeModelRecord(this.formalModelId, this.modelRecordWords);
        if (!"none".equals(error)) {
            this.fail(error);
            return this.createStatusSnapshot();
        }
        this.modelRecordUploaded = true;

        error = this.instance.getFormalModelStore().uploadPrototypeModelColour(this.formalModelId, this.modelColour);
        if (!"none".equals(error)) {
            this.fail(error);
            return this.createStatusSnapshot();
        }
        this.modelColourUploaded = true;

        this.uploadedFaceTiles = 0;
        this.uploadedPixels = 0;
        for (int faceIndex = 0; faceIndex < ForgeModelAtlasLayout.FACE_COUNT; faceIndex++) {
            FaceUpload face = this.faceUploads[faceIndex];
            if (face == null) {
                this.fail("missing-face-upload-" + faceIndex);
                return this.createStatusSnapshot();
            }
            error = this.instance.getFormalModelStore().uploadPrototypeAtlasFace(this.formalModelId, faceIndex, face.pixels());
            if (!"none".equals(error)) {
                this.fail(error);
                return this.createStatusSnapshot();
            }
            this.uploadedFaceTiles++;
            this.uploadedPixels += ForgeModelAtlasLayout.MODEL_TEXTURE_SIZE * ForgeModelAtlasLayout.MODEL_TEXTURE_SIZE;
        }
        this.atlasPixelsUploaded = this.uploadedFaceTiles == ForgeModelAtlasLayout.FACE_COUNT;
        return this.createStatusSnapshot();
    }

    ForgeOneBlockFormalBakeUploadAuditResult audit() {
        this.auditRuns++;
        long start = System.nanoTime();
        ForgeOneBlockFormalBakeUploadAuditResult result;
        if (!RenderSystem.isOnRenderThread()) {
            result = ForgeOneBlockFormalBakeUploadAuditResult.failure("not-render-thread", elapsedMs(start));
        } else {
            try {
                result = this.auditInternal(start);
            } catch (RuntimeException e) {
                result = ForgeOneBlockFormalBakeUploadAuditResult.failure(e.getClass().getSimpleName() + ": " + e.getMessage(), elapsedMs(start));
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

    ForgeOneBlockFormalBakeUploadAuditResult createAuditStatusSnapshot() {
        return this.lastAudit;
    }

    ForgeOneBlockFormalBakeUploadStats createStatusSnapshot() {
        ForgeFormalModelStoreStats store = this.instance.getFormalModelStore().createStatusSnapshot();
        ForgeModelAtlasLayout.Tile base = ForgeModelAtlasLayout.isValidModelId(this.formalModelId)
                ? ForgeModelAtlasLayout.modelBaseTile(this.formalModelId)
                : new ForgeModelAtlasLayout.Tile(-1, -1);
        boolean uploadReady = this.modelRecordUploaded && this.modelColourUploaded && this.atlasPixelsUploaded;
        boolean auditReady = this.lastAudit.success() && this.modelDataReadbackOk && this.modelColourReadbackOk && this.atlasReadbackOk;
        boolean prototypeReady = this.oneBlockRealBakeReady && uploadReady && auditReady && !this.stale;
        return new ForgeOneBlockFormalBakeUploadStats(
                STAGE,
                this.buildRuns,
                this.auditRuns,
                this.clearRuns,
                this.uploadFailures,
                prototypeReady,
                this.oneBlockRealBakeReady && !this.stale,
                uploadReady && !this.stale,
                auditReady && !this.stale,
                this.modelRecordUploaded && !this.stale,
                this.atlasPixelsUploaded && !this.stale,
                auditReady && !this.stale,
                this.candidate == null ? -1 : this.candidate.blockStateId(),
                this.candidate == null ? "none" : this.candidate.state().toString(),
                this.candidate == null ? "none" : ForgeCpuMeshLayer.fromBlockState(this.candidate.state()).displayName,
                this.sourceBakedModelClass,
                this.sourcePrimarySprite,
                this.sourcePrimarySpriteAtlas,
                this.sourceTinted,
                this.candidate != null && !this.candidate.state().getFluidState().isEmpty(),
                this.formalModelId,
                ForgeModelAtlasLayout.isValidModelId(this.formalModelId) && this.formalModelId != 0,
                false,
                false,
                this.oneBlockRealBakeReady && ForgeModelAtlasLayout.isValidModelId(this.formalModelId) && this.formalModelId != 0,
                ForgeModelStoreFormalLayout.MODEL_RECORD_BYTES,
                this.modelRecordUploaded,
                ForgeModelAtlasLayout.isValidModelId(this.formalModelId) ? this.instance.getFormalModelStore().modelDataWriteOffset(this.formalModelId) : -1L,
                this.modelColourUploaded,
                ForgeModelAtlasLayout.isValidModelId(this.formalModelId) ? this.instance.getFormalModelStore().modelColourWriteOffset(this.formalModelId) : -1L,
                this.colourTint,
                this.flagsA,
                this.faceDataWrittenCount,
                this.missingFaceCount,
                this.fallbackFaceCount,
                this.atlasPixelsUploaded,
                this.uploadedFaceTiles,
                this.uploadedPixels,
                base.x(),
                base.y(),
                this.atlasReadbackOk,
                this.atlasPixelMismatches,
                this.faceChecksums(),
                this.modelDataReadbackOk,
                this.modelColourReadbackOk,
                store.formalModelStoreOwnerReady(),
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
        ForgeOneBlockFormalBakeUploadStats status = this.createStatusSnapshot();
        StringBuilder builder = new StringBuilder();
        builder.append("Voxy I4 one-block bake/upload dump: ");
        builder.append("stage=").append(status.stage());
        builder.append(" sourceBlockStateId=").append(status.sourceBlockStateId());
        builder.append(" sourceBlockState=\"").append(status.sourceBlockState()).append('"');
        builder.append(" formalModelId=").append(status.formalModelId());
        builder.append(" atlasBase=").append(status.atlasBaseX()).append(',').append(status.atlasBaseY());
        builder.append(" faceChecksums=").append(status.faceChecksums());
        builder.append(" modelWords=");
        builder.append(this.modelRecordWords == null ? "none" : Arrays.toString(this.modelRecordWords));
        builder.append(" formalRendererReady=false actualDrawEnabled=false sampleSetModelIdsUsed=false");
        return builder.toString();
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
        this.resetUploadState(true);
        this.resourceReloadSeen = false;
        this.worldUnloadSeen = false;
        this.dimensionSwitchSeen = false;
        this.debugPipelineClearSeen = false;
        this.presetOffSeen = false;
        this.presetClearSeen = false;
        this.lastAudit = ForgeOneBlockFormalBakeUploadAuditResult.failure("none", 0.0D);
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

    private ForgeOneBlockFormalBakeUploadAuditResult auditInternal(long startNanos) {
        boolean validFormalModelId = ForgeModelAtlasLayout.isValidModelId(this.formalModelId) && this.formalModelId != 0;
        boolean mappingBelongsToI3 = this.candidate != null
                && this.instance.getFormalModelFactory().mappingForBlockStateId(this.candidate.blockStateId())
                .map(mapping -> mapping.formalModelId() == this.formalModelId)
                .orElse(false);
        boolean placeholderIdUsed = false;
        boolean sampleSetIdUsed = false;
        boolean modelRecordBytesOk = this.modelRecordWords != null
                && this.modelRecordWords.length == ForgeModelStoreFormalLayout.MODEL_RECORD_WORDS
                && ForgeModelStoreFormalLayout.MODEL_RECORD_BYTES == 64;

        boolean dataMatch = false;
        boolean colourMatch = false;
        boolean atlasOk = false;
        int pixelMismatches = 0;
        if (validFormalModelId && modelRecordBytesOk && this.modelRecordUploaded) {
            dataMatch = Arrays.equals(this.modelRecordWords, this.instance.getFormalModelStore().readPrototypeModelRecord(this.formalModelId));
        }
        if (validFormalModelId && this.modelColourUploaded) {
            colourMatch = this.modelColour == this.instance.getFormalModelStore().readPrototypeModelColour(this.formalModelId);
        }
        if (validFormalModelId && this.atlasPixelsUploaded) {
            atlasOk = true;
            for (int faceIndex = 0; faceIndex < ForgeModelAtlasLayout.FACE_COUNT; faceIndex++) {
                FaceUpload face = this.faceUploads[faceIndex];
                if (face == null) {
                    atlasOk = false;
                    continue;
                }
                byte[] readback = this.instance.getFormalModelStore().readPrototypeAtlasFace(this.formalModelId, faceIndex);
                int mismatches = countPixelMismatches(face.pixels(), readback);
                pixelMismatches += mismatches;
                if (mismatches != 0) {
                    atlasOk = false;
                }
            }
        }

        boolean shaderBound = false;
        boolean drawOccurred = false;
        boolean formalRendererReady = false;
        boolean actualDrawEnabled = false;
        boolean success = validFormalModelId
                && mappingBelongsToI3
                && !placeholderIdUsed
                && !sampleSetIdUsed
                && modelRecordBytesOk
                && dataMatch
                && colourMatch
                && atlasOk
                && pixelMismatches == 0
                && !shaderBound
                && !drawOccurred
                && !formalRendererReady
                && !actualDrawEnabled
                && !this.stale;
        return new ForgeOneBlockFormalBakeUploadAuditResult(
                success,
                success ? "none" : "one-block-formal-upload-audit-failed",
                elapsedMs(startNanos),
                validFormalModelId,
                mappingBelongsToI3,
                placeholderIdUsed,
                sampleSetIdUsed,
                modelRecordBytesOk,
                dataMatch,
                colourMatch,
                atlasOk,
                pixelMismatches,
                shaderBound,
                drawOccurred,
                formalRendererReady,
                actualDrawEnabled
        );
    }

    private Optional<RecordBuild> buildRecord(Minecraft minecraft, Candidate candidate) {
        BlockRenderDispatcher blockRenderer = minecraft.getBlockRenderer();
        var model = blockRenderer.getBlockModel(candidate.state());
        if (model == null) {
            this.fail("baked-model-missing");
            return Optional.empty();
        }
        if (model.isCustomRenderer()) {
            this.fail("custom-renderer-unsupported");
            return Optional.empty();
        }

        int[] faceWords = new int[ForgeModelStoreFormalLayout.FACE_DATA_WORDS];
        Arrays.fill(faceWords, -1);
        FaceUpload[] faces = new FaceUpload[ForgeModelAtlasLayout.FACE_COUNT];
        List<FaceBuild> builtFaces = new ArrayList<>(ForgeModelAtlasLayout.FACE_COUNT);
        int tintedFaces = 0;
        int untintedFaces = 0;
        int missingFaces = 0;
        int fallbackFaces = 0;
        int writtenFaces = 0;
        String primarySprite = "none";
        String primaryAtlas = "none";
        boolean shaded = false;
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
            if (quad.isTinted()) {
                tintedFaces++;
            } else {
                untintedFaces++;
            }
            if (!"none".equals(encoding.spriteName()) && "none".equals(primarySprite)) {
                primarySprite = encoding.spriteName();
                primaryAtlas = encoding.spriteAtlas();
            }
            builtFaces.add(new FaceBuild(faceIndex, direction.getName(), face));
        }

        Optional<FaceUpload> fallback = Arrays.stream(faces).filter(face -> face != null && face.pixels().length == ForgeModelAtlasPixelSample.BYTES_PER_FACE).findFirst();
        if (fallback.isEmpty()) {
            this.fail("no-face-sprite-pixels");
            return Optional.empty();
        }
        for (FaceBuild built : builtFaces) {
            if (faces[built.faceIndex()] == null) {
                FaceUpload source = fallback.get();
                faces[built.faceIndex()] = source.fallbackFor(built.faceIndex(), built.direction());
                fallbackFaces++;
            }
        }

        int flags = 0;
        flags |= tintedFaces > 0 ? 1 : 0;
        flags |= shaded ? 8 : 0;
        int tint = tintedFaces == 0 ? -1 : sampleTintColour(minecraft, candidate.state(), faces);
        int[] words = new int[ForgeModelStoreFormalLayout.MODEL_RECORD_WORDS];
        System.arraycopy(faceWords, 0, words, 0, ForgeModelStoreFormalLayout.FACE_DATA_WORDS);
        words[ForgeModelStoreFormalLayout.WORD_FLAGS_A] = flags;
        words[ForgeModelStoreFormalLayout.WORD_COLOUR_TINT] = tint;
        words[ForgeModelStoreFormalLayout.WORD_CUSTOM_ID] = 0;
        return Optional.of(new RecordBuild(
                words,
                tint,
                faces,
                flags,
                tint,
                writtenFaces,
                missingFaces,
                fallbackFaces,
                tintedFaces > 0,
                model.getClass().getSimpleName(),
                primarySprite,
                primaryAtlas
        ));
    }

    private Optional<Candidate> selectCandidate(Minecraft minecraft) {
        Optional<Candidate> nearby = this.nearbySolidCandidate(minecraft);
        if (nearby.isPresent()) {
            return nearby;
        }
        for (BlockState state : List.of(Blocks.SAND.defaultBlockState(), Blocks.STONE.defaultBlockState(), Blocks.DIRT.defaultBlockState())) {
            if (isSolidSampleState(state)) {
                int blockStateId = this.blockStateIdForState(state);
                if (blockStateId > 0) {
                    return Optional.of(new Candidate(blockStateId, state));
                }
            }
        }
        return Optional.empty();
    }

    private Optional<Candidate> nearbySolidCandidate(Minecraft minecraft) {
        BlockPos origin = minecraft.player.blockPosition();
        for (int dy = 0; dy <= SAMPLE_SEARCH_DOWN_BLOCKS; dy++) {
            for (int dz = -2; dz <= 2; dz++) {
                for (int dx = -2; dx <= 2; dx++) {
                    BlockPos candidatePos = origin.offset(dx, -dy, dz);
                    BlockState state = minecraft.level.getBlockState(candidatePos);
                    if (isSolidSampleState(state)) {
                        int blockStateId = this.blockStateIdForState(state);
                        if (blockStateId > 0) {
                            return Optional.of(new Candidate(blockStateId, state));
                        }
                    }
                }
            }
        }
        return Optional.empty();
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

    private void resetUploadState(boolean resetCandidate) {
        if (resetCandidate) {
            this.candidate = null;
            this.formalModelId = -1;
        }
        this.modelRecordWords = null;
        this.modelColour = 0;
        this.faceUploads = new FaceUpload[ForgeModelAtlasLayout.FACE_COUNT];
        this.oneBlockRealBakeReady = false;
        this.modelRecordUploaded = false;
        this.modelColourUploaded = false;
        this.atlasPixelsUploaded = false;
        this.uploadedFaceTiles = 0;
        this.uploadedPixels = 0;
        this.missingFaceCount = 0;
        this.fallbackFaceCount = 0;
        this.faceDataWrittenCount = 0;
        this.sourceTinted = false;
        this.flagsA = 0;
        this.colourTint = -1;
        this.sourceBakedModelClass = "none";
        this.sourcePrimarySprite = "none";
        this.sourcePrimarySpriteAtlas = "none";
        this.modelDataReadbackOk = false;
        this.modelColourReadbackOk = false;
        this.atlasReadbackOk = false;
        this.atlasPixelMismatches = 0;
    }

    private void markLifecycleStale(String event) {
        this.enabled = false;
        this.stale = true;
        this.requiresRebuild = true;
        this.lifecycleState = "STALE";
        this.lastLifecycleEvent = event == null || event.isBlank() ? "stale" : event.replace(' ', '-');
        this.staleReason = this.lastLifecycleEvent;
        this.resetUploadState(false);
    }

    private void fail(String reason) {
        this.uploadFailures++;
        this.lastFailureReason = reason == null || reason.isBlank() ? "failed" : reason.replace(' ', '-');
    }

    private String faceChecksums() {
        StringBuilder builder = new StringBuilder();
        for (int faceIndex = 0; faceIndex < ForgeModelAtlasLayout.FACE_COUNT; faceIndex++) {
            if (faceIndex > 0) {
                builder.append(',');
            }
            FaceUpload face = this.faceUploads[faceIndex];
            builder.append("face").append(faceIndex).append('=').append(face == null ? "none" : face.checksum());
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

    private static boolean isSolidSampleState(BlockState state) {
        return state != null
                && !state.isAir()
                && state.getFluidState().isEmpty()
                && ForgeCpuMeshLayer.fromBlockState(state) == ForgeCpuMeshLayer.SOLID;
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

    private record Candidate(int blockStateId, BlockState state) {
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
            boolean sourceTinted,
            String bakedModelClass,
            String sourcePrimarySprite,
            String sourcePrimarySpriteAtlas
    ) {
    }
}
