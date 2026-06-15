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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

final class ForgeRealModelStoreSample {
    static final String STAGE = "G6_13_REAL_MODELSTORE_RECORD_SAMPLE";
    private static final int MAX_MAPPER_CANDIDATES = 32;
    private static final int SAMPLE_SEARCH_DOWN_BLOCKS = 128;
    private static final int VERTICES_PER_QUAD = 4;
    private static final Direction[] DIRECTIONS = Direction.values();

    private final ForgeVoxyInstance instance;
    private final ForgeRealModelStoreSampleBuffer buffer = new ForgeRealModelStoreSampleBuffer();
    private ForgeRealModelStoreSampleRecord record;
    private long buildRuns;
    private long clearRuns;
    private long auditRuns;
    private long auditFailures;
    private long generation = -1L;
    private String dimensionId = "none";
    private String lastBuildError = "none";
    private double lastBuildDurationMs;
    private boolean realModelRecordSampleStale;
    private boolean lastReloadInvalidatedRealModelRecordSample;
    private ForgeRealModelStoreSampleAuditResult lastAudit = ForgeRealModelStoreSampleAuditResult.failure("none", 0.0D);

    ForgeRealModelStoreSample(ForgeVoxyInstance instance) {
        this.instance = instance;
    }

    ForgeRealModelStoreSampleStats build() {
        this.buildRuns++;
        long start = System.nanoTime();
        if (!RenderSystem.isOnRenderThread()) {
            this.lastBuildError = "not-render-thread";
            this.lastBuildDurationMs = elapsedMs(start);
            return this.createStatusSnapshot();
        }

        try {
            Optional<ForgeRealModelStoreSampleRecord> builtRecord = this.buildRecord();
            if (builtRecord.isEmpty()) {
                this.record = null;
                this.buffer.closeOnRenderThread();
                this.lastBuildError = "no-solid-baked-model-sample";
                this.lastBuildDurationMs = elapsedMs(start);
                return this.createStatusSnapshot();
            }

            long nextGeneration = this.generation + 1L;
            String dimension = currentDimension();
            ForgeRealModelStoreSampleRecord nextRecord = builtRecord.get();
            boolean uploaded = this.buffer.upload(nextRecord, nextGeneration, dimension);
            this.record = nextRecord;
            if (uploaded) {
                this.generation = nextGeneration;
                this.dimensionId = dimension;
                this.realModelRecordSampleStale = false;
                this.lastReloadInvalidatedRealModelRecordSample = false;
                this.lastBuildError = "none";
            } else {
                this.lastBuildError = this.buffer.lastUploadError();
            }
            this.lastAudit = ForgeRealModelStoreSampleAuditResult.failure("none", 0.0D);
        } catch (RuntimeException e) {
            this.record = null;
            this.buffer.closeOnRenderThread();
            this.lastBuildError = e.getClass().getSimpleName() + ": " + e.getMessage();
        }
        this.lastBuildDurationMs = elapsedMs(start);
        return this.createStatusSnapshot();
    }

    ForgeRealModelStoreSampleAuditResult audit() {
        this.auditRuns++;
        long start = System.nanoTime();
        ForgeRealModelStoreSampleAuditResult result;
        if (!RenderSystem.isOnRenderThread()) {
            result = ForgeRealModelStoreSampleAuditResult.failure("not-render-thread", elapsedMs(start));
        } else if (this.record == null) {
            result = ForgeRealModelStoreSampleAuditResult.failure("no-real-model-record-sample", elapsedMs(start));
        } else if (!this.buffer.isModelDataCreated()) {
            result = ForgeRealModelStoreSampleAuditResult.failure("model-data-buffer-missing", elapsedMs(start));
        } else {
            try {
                result = this.compareReadback(start);
            } catch (RuntimeException e) {
                result = ForgeRealModelStoreSampleAuditResult.failure(e.getClass().getSimpleName() + ": " + e.getMessage(), elapsedMs(start));
            }
        }
        this.lastAudit = result;
        if (!result.success()) {
            this.auditFailures++;
        }
        return result;
    }

    ForgeRealModelStoreSampleStats createStatusSnapshot() {
        ForgeRealModelStoreFaceSample face = this.record == null
                ? ForgeRealModelStoreFaceSample.empty("none")
                : this.record.sampleFace();
        boolean dataReady = this.buffer.isModelDataCreated();
        boolean colourReady = this.buffer.isModelColourCreated();
        boolean sampleReady = this.record != null && dataReady && "none".equals(this.lastBuildError);
        boolean faceReady = this.record != null && this.record.faceCount() > 0;
        boolean spriteReady = faceReady && !"none".equals(this.record.sourceSprite()) && !"none".equals(this.record.sourceSpriteAtlas());
        return new ForgeRealModelStoreSampleStats(
                STAGE,
                this.buildRuns,
                this.clearRuns,
                this.auditRuns,
                this.auditFailures,
                this.lastBuildError,
                this.lastBuildDurationMs,
                sampleReady,
                dataReady,
                this.record == null ? 0 : 1,
                ForgeRealModelStoreSampleRecord.BYTES,
                ForgeRealModelStoreSampleRecord.LAYOUT_VERSION,
                ForgeRealModelStoreSampleRecord.FORMAL_LAYOUT_COMPATIBLE,
                this.record == null ? -1 : this.record.modelId(),
                this.record == null ? -1 : this.record.blockStateId(),
                this.record == null ? "none" : this.record.blockState(),
                this.record == null ? "unknown" : this.record.renderLayer(),
                this.record == null ? "none" : this.record.sourceSprite(),
                this.record == null ? "none" : this.record.sourceSpriteAtlas(),
                this.record == null ? 0 : this.record.faceCount(),
                faceReady,
                faceReady ? "partial" : "none",
                false,
                spriteReady,
                colourReady,
                this.record == null ? 0 : this.record.tintedFaces(),
                this.record == null ? 0 : this.record.untintedFaces(),
                false,
                false,
                false,
                false,
                this.realModelRecordSampleStale,
                this.lastReloadInvalidatedRealModelRecordSample,
                this.generation,
                this.dimensionId,
                this.buffer.isStale(this.generation, currentDimension()),
                this.lastAudit.success(),
                this.lastAudit.error(),
                this.lastAudit.durationMs(),
                this.lastAudit.auditedRecords(),
                this.lastAudit.auditedBytes(),
                this.lastAudit.invalidRecords(),
                this.lastAudit.modelDataBufferMatch(),
                this.lastAudit.modelColourBufferMatch(),
                this.record == null ? 0 : this.record.faceCount(),
                face.direction(),
                face.uvMin(),
                face.uvMax(),
                face.tintIndex(),
                face.hasTint(),
                face.cullDirection(),
                face.verticesLength(),
                face.encodedFaceDataWord()
        );
    }

    ForgeRealModelStoreSampleAuditResult createAuditStatusSnapshot() {
        return this.lastAudit;
    }

    String dumpSample() {
        if (this.record == null) {
            return "Voxy real ModelStore sample: none. Run /voxy baked_model_bridge_check then /voxy model_store_real_sample_build first.";
        }
        ForgeRealModelStoreFaceSample face = this.record.sampleFace();
        return String.format(
                "Voxy real ModelStore sample: modelId=%d blockStateId=%d blockState=\"%s\" renderLayer=%s sprite=%s spriteAtlas=%s faceCount=%d sampleFace=%s direction=%s uMin/vMin/uMax/vMax=%s/%s tintIndex=%d hasTint=%s cullDirection=%s verticesLength=%d faceDataWord=0x%08X layoutVersion=%s formalLayoutCompatible=%s realTextureAtlasUploadReady=false formalTexturedShaderReady=false formalModelBridgeReady=false draw=false",
                this.record.modelId(),
                this.record.blockStateId(),
                this.record.blockState(),
                this.record.renderLayer(),
                this.record.sourceSprite(),
                this.record.sourceSpriteAtlas(),
                this.record.faceCount(),
                face.direction(),
                face.direction(),
                face.uvMin(),
                face.uvMax(),
                face.tintIndex(),
                face.hasTint(),
                face.cullDirection(),
                face.verticesLength(),
                face.encodedFaceDataWord(),
                ForgeRealModelStoreSampleRecord.LAYOUT_VERSION,
                ForgeRealModelStoreSampleRecord.FORMAL_LAYOUT_COMPATIBLE
        );
    }

    void clear() {
        this.clearRuns++;
        this.record = null;
        this.generation = -1L;
        this.dimensionId = "none";
        this.lastBuildError = "none";
        this.lastBuildDurationMs = 0.0D;
        this.auditRuns = 0L;
        this.auditFailures = 0L;
        this.realModelRecordSampleStale = false;
        this.lastReloadInvalidatedRealModelRecordSample = false;
        this.lastAudit = ForgeRealModelStoreSampleAuditResult.failure("none", 0.0D);
        this.buffer.close();
    }

    void markStale(String reason) {
        this.record = null;
        this.generation = -1L;
        this.dimensionId = "none";
        this.realModelRecordSampleStale = true;
        this.lastReloadInvalidatedRealModelRecordSample = true;
        this.lastBuildError = reason == null || reason.isBlank() ? "stale" : reason;
        this.lastAudit = ForgeRealModelStoreSampleAuditResult.failure(this.lastBuildError, 0.0D);
        this.buffer.close();
    }

    private Optional<ForgeRealModelStoreSampleRecord> buildRecord() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null || minecraft.getBlockRenderer() == null) {
            return Optional.empty();
        }

        for (Candidate candidate : this.collectCandidates()) {
            Optional<ForgeRealModelStoreSampleRecord> record = this.tryBuildRecord(minecraft, candidate);
            if (record.isPresent()) {
                return record;
            }
        }
        return Optional.empty();
    }

    private List<Candidate> collectCandidates() {
        LinkedHashMap<Integer, BlockState> states = new LinkedHashMap<>();
        for (ForgeVoxyModelIdMapper.ModelIdMapping mapping : ForgeVoxyModelIdMapper.INSTANCE.createSnapshot(MAX_MAPPER_CANDIDATES)) {
            this.blockStateForId(mapping.blockStateId()).ifPresent(state -> states.putIfAbsent(mapping.blockStateId(), state));
        }
        this.addNearbySolidCandidates(states);
        this.addCommonCandidate(states, Blocks.SAND.defaultBlockState());
        this.addCommonCandidate(states, Blocks.STONE.defaultBlockState());
        this.addCommonCandidate(states, Blocks.DIRT.defaultBlockState());

        List<Candidate> candidates = new ArrayList<>(states.size());
        for (Map.Entry<Integer, BlockState> entry : states.entrySet()) {
            int modelId = ForgeVoxyModelIdMapper.INSTANCE.getOrCreateModelId(entry.getKey()).modelId();
            candidates.add(new Candidate(modelId, entry.getKey(), entry.getValue()));
        }
        return candidates;
    }

    private void addNearbySolidCandidates(LinkedHashMap<Integer, BlockState> states) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            return;
        }
        BlockPos origin = minecraft.player.blockPosition();
        for (int dy = 0; dy <= SAMPLE_SEARCH_DOWN_BLOCKS; dy++) {
            for (int dz = -2; dz <= 2; dz++) {
                for (int dx = -2; dx <= 2; dx++) {
                    BlockPos candidate = origin.offset(dx, -dy, dz);
                    BlockState state = minecraft.level.getBlockState(candidate);
                    if (isSolidSampleState(state)) {
                        this.addCommonCandidate(states, state);
                        return;
                    }
                }
            }
        }
    }

    private void addCommonCandidate(LinkedHashMap<Integer, BlockState> states, BlockState state) {
        int blockStateId = this.blockStateIdForState(state);
        if (blockStateId > 0) {
            states.putIfAbsent(blockStateId, state);
        }
    }

    private Optional<ForgeRealModelStoreSampleRecord> tryBuildRecord(Minecraft minecraft, Candidate candidate) {
        BlockState state = candidate.state();
        if (!isSolidSampleState(state)) {
            return Optional.empty();
        }

        BlockRenderDispatcher blockRenderer = minecraft.getBlockRenderer();
        var model = blockRenderer.getBlockModel(state);
        if (model == null || model.isCustomRenderer()) {
            return Optional.empty();
        }

        List<ForgeRealModelStoreFaceSample> faces = new ArrayList<>(DIRECTIONS.length);
        int[] faceWords = new int[ForgeModelStoreFormalLayout.FACE_DATA_WORDS];
        int tintedFaces = 0;
        int untintedFaces = 0;
        int totalQuads = 0;
        String sourceSprite = "none";
        String sourceAtlas = "none";
        boolean shaded = false;
        try {
            shaded = model.useAmbientOcclusion();
        } catch (RuntimeException ignored) {
            shaded = false;
        }

        for (Direction direction : DIRECTIONS) {
            List<BakedQuad> quads = safeGetQuads(model, state, direction, candidate.blockStateId());
            BakedQuad quad = quads.isEmpty() ? firstGeneralQuadForDirection(model, state, direction, candidate.blockStateId()) : quads.get(0);
            totalQuads += quads.size();
            int faceIndex = direction.get3DDataValue();
            if (quad == null) {
                faceWords[faceIndex] = -1;
                faces.add(ForgeRealModelStoreFaceSample.empty(direction.getName()));
                continue;
            }

            FaceEncoding encoding = encodeFace(quad, direction);
            faceWords[faceIndex] = encoding.faceDataWord();
            if (quad.isTinted()) {
                tintedFaces++;
            } else {
                untintedFaces++;
            }
            if (!encoding.spriteName().equals("none") && sourceSprite.equals("none")) {
                sourceSprite = encoding.spriteName();
                sourceAtlas = encoding.spriteAtlas();
            }
            faces.add(new ForgeRealModelStoreFaceSample(
                    direction.getName(),
                    true,
                    encoding.spriteName(),
                    encoding.spriteAtlas(),
                    encoding.uvMin(),
                    encoding.uvMax(),
                    quad.getTintIndex(),
                    quad.isTinted(),
                    direction.getName(),
                    quad.getVertices() == null ? 0 : quad.getVertices().length,
                    encoding.faceDataWord()
            ));
        }

        if (totalQuads == 0 && faces.stream().noneMatch(ForgeRealModelStoreFaceSample::hasQuad)) {
            return Optional.empty();
        }

        int flagsA = 0;
        flagsA |= tintedFaces > 0 ? 1 : 0;
        flagsA |= shaded ? 8 : 0;
        int colourTint = tintedFaces == 0 ? -1 : this.sampleTintColour(minecraft, state, faces);
        int modelColour = colourTint;
        return Optional.of(new ForgeRealModelStoreSampleRecord(
                candidate.modelId(),
                candidate.blockStateId(),
                state.toString(),
                ForgeCpuMeshLayer.fromBlockState(state).displayName,
                sourceSprite,
                sourceAtlas,
                faceWords,
                flagsA,
                colourTint,
                0,
                modelColour,
                List.copyOf(faces),
                tintedFaces,
                untintedFaces
        ));
    }

    private ForgeRealModelStoreSampleAuditResult compareReadback(long start) {
        int[] modelWords = this.buffer.readbackModelDataWords();
        int[] colours = this.buffer.readbackModelColours();
        int invalidRecords = 0;
        boolean dataMatch = this.record.matchesWords(modelWords, 0);
        boolean colourMatch = colours.length == 1 && colours[0] == this.record.modelColour();
        if (!dataMatch) {
            invalidRecords++;
        }
        if (!colourMatch) {
            invalidRecords++;
        }
        boolean success = dataMatch && colourMatch && invalidRecords == 0;
        return new ForgeRealModelStoreSampleAuditResult(
                success,
                success ? "none" : "buffer-mismatch",
                elapsedMs(start),
                1,
                this.buffer.modelDataBytes(),
                invalidRecords,
                dataMatch,
                colourMatch,
                this.generation,
                this.dimensionId
        );
    }

    private int sampleTintColour(Minecraft minecraft, BlockState state, List<ForgeRealModelStoreFaceSample> faces) {
        int tintIndex = -1;
        for (ForgeRealModelStoreFaceSample face : faces) {
            if (face.hasTint()) {
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

    private static BakedQuad firstGeneralQuadForDirection(net.minecraft.client.resources.model.BakedModel model, BlockState state, Direction direction, int blockStateId) {
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
            return new FaceEncoding(spriteName, spriteAtlas, "none", "none", -1);
        }

        int stride = vertices.length / VERTICES_PER_QUAD;
        if (stride < 6) {
            return new FaceEncoding(spriteName, spriteAtlas, "none", "none", -1);
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
            return new FaceEncoding(spriteName, spriteAtlas, "none", "none", -1);
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
        return new FaceEncoding(
                spriteName,
                spriteAtlas,
                String.format("%.5f,%.5f", minU, minV),
                String.format("%.5f,%.5f", maxU, maxV),
                faceData
        );
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

    private Optional<BlockState> blockStateForId(int blockStateId) {
        Optional<WorldEngine> engine = this.instance.getCurrentEngineOptional();
        if (engine.isPresent()) {
            try {
                return Optional.of(engine.get().getMapper().getBlockStateFromBlockId(blockStateId));
            } catch (RuntimeException ignored) {
                // Fall through to vanilla registry fallback.
            }
        }
        try {
            BlockState state = Block.BLOCK_STATE_REGISTRY.byId(blockStateId);
            return state == null ? Optional.empty() : Optional.of(state);
        } catch (RuntimeException ignored) {
            return Optional.empty();
        }
    }

    private int blockStateIdForState(BlockState state) {
        Optional<WorldEngine> engine = this.instance.getCurrentEngineOptional();
        if (engine.isPresent()) {
            try {
                return engine.get().getMapper().getIdForBlockState(state);
            } catch (RuntimeException ignored) {
                // Fall through to vanilla registry fallback.
            }
        }
        try {
            return Block.BLOCK_STATE_REGISTRY.getId(state);
        } catch (RuntimeException ignored) {
            return -1;
        }
    }

    private static boolean isSolidSampleState(BlockState state) {
        return state != null
                && !state.isAir()
                && state.getFluidState().isEmpty()
                && ForgeCpuMeshLayer.fromBlockState(state) == ForgeCpuMeshLayer.SOLID;
    }

    private static String currentDimension() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.level == null ? "none" : minecraft.level.dimension().location().toString();
    }

    private static int clampInt(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static double elapsedMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000.0D;
    }

    private record Candidate(int modelId, int blockStateId, BlockState state) {
    }

    private record FaceEncoding(String spriteName, String spriteAtlas, String uvMin, String uvMax, int faceDataWord) {
    }
}
