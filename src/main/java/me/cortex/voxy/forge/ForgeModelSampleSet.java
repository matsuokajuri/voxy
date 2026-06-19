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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * Deprecated sample route: kept only as historical G6 evidence. New model work
 * must use the formal ModelFactory/ModelBakery/ModelStore path.
 */
@Deprecated(forRemoval = false)
final class ForgeModelSampleSet {
    static final String STAGE = "G6_19_MULTI_BLOCK_MODEL_SAMPLE_SET";
    static final String LAYOUT_VERSION = "REAL_MODEL_RECORD_SAMPLE_SET_V1";
    static final int DEFAULT_REQUESTED_SAMPLES = 8;
    static final int MAX_REQUESTED_SAMPLES = 16;
    private static final int MAX_MAPPER_CANDIDATES = 96;
    private static final int MAX_HEAP_RECORD_SCAN = 65536;
    private static final int SAMPLE_SEARCH_DOWN_BLOCKS = 128;
    private static final int VERTICES_PER_QUAD = 4;
    private static final Direction[] DIRECTIONS = Direction.values();

    private final ForgeVoxyInstance instance;
    private final ForgeModelSampleSetBuffer buffer = new ForgeModelSampleSetBuffer();
    private List<ForgeRealModelStoreSampleRecord> records = List.of();
    private long buildRuns;
    private long clearRuns;
    private long auditRuns;
    private long auditFailures;
    private long generation = -1L;
    private String dimensionId = "none";
    private String lastBuildError = "none";
    private double lastBuildDurationMs;
    private boolean sampleSetStale;
    private boolean lastReloadInvalidatedSampleSet;
    private int requestedSamples = DEFAULT_REQUESTED_SAMPLES;
    private int rejectedSamples;
    private int fluidRejected;
    private int translucentRejected;
    private int missingModelRejected;
    private int missingSpriteRejected;
    private int unsupportedRejected;
    private ForgeModelSampleSetAuditResult lastAudit = ForgeModelSampleSetAuditResult.failure("none", 0.0D);

    ForgeModelSampleSet(ForgeVoxyInstance instance) {
        this.instance = instance;
    }

    ForgeModelSampleSetStats build() {
        this.buildRuns++;
        long start = System.nanoTime();
        this.requestedSamples = DEFAULT_REQUESTED_SAMPLES;
        this.resetRejectCounters();
        if (!RenderSystem.isOnRenderThread()) {
            this.failBuild("not-render-thread", start);
            return this.createStatusSnapshot();
        }

        try {
            List<ForgeRealModelStoreSampleRecord> built = this.buildRecords(this.requestedSamples);
            if (built.isEmpty()) {
                this.records = List.of();
                this.buffer.closeOnRenderThread();
                this.failBuild("no-solid-baked-model-samples", start);
                return this.createStatusSnapshot();
            }

            long nextGeneration = this.generation + 1L;
            String dimension = currentDimension();
            if (!this.buffer.upload(built, nextGeneration, dimension)) {
                this.records = List.of();
                this.lastBuildError = this.buffer.lastUploadError();
                this.lastBuildDurationMs = elapsedMs(start);
                return this.createStatusSnapshot();
            }

            this.records = List.copyOf(built);
            this.generation = nextGeneration;
            this.dimensionId = dimension;
            this.sampleSetStale = false;
            this.lastReloadInvalidatedSampleSet = false;
            this.lastBuildError = "none";
            this.lastBuildDurationMs = elapsedMs(start);
            this.lastAudit = ForgeModelSampleSetAuditResult.failure("none", 0.0D);
            return this.createStatusSnapshot();
        } catch (RuntimeException e) {
            this.records = List.of();
            this.buffer.closeOnRenderThread();
            this.failBuild(e.getClass().getSimpleName() + ": " + e.getMessage(), start);
            return this.createStatusSnapshot();
        }
    }

    ForgeModelSampleSetAuditResult audit() {
        this.auditRuns++;
        long start = System.nanoTime();
        ForgeModelSampleSetAuditResult result;
        if (!RenderSystem.isOnRenderThread()) {
            result = ForgeModelSampleSetAuditResult.failure("not-render-thread", elapsedMs(start));
        } else if (this.records.isEmpty()) {
            result = ForgeModelSampleSetAuditResult.failure("no-model-sample-set", elapsedMs(start));
        } else if (!this.buffer.isModelDataCreated()) {
            result = ForgeModelSampleSetAuditResult.failure("model-sample-set-buffer-missing", elapsedMs(start));
        } else {
            try {
                result = this.compareReadback(start);
            } catch (RuntimeException e) {
                result = ForgeModelSampleSetAuditResult.failure(e.getClass().getSimpleName() + ": " + e.getMessage(), elapsedMs(start));
            }
        }
        this.lastAudit = result;
        if (!result.success()) {
            this.auditFailures++;
        }
        return result;
    }

    ForgeModelSampleSetStats createStatusSnapshot() {
        boolean ready = !this.records.isEmpty() && this.buffer.isModelDataCreated() && "none".equals(this.lastBuildError) && !this.sampleSetStale;
        return new ForgeModelSampleSetStats(
                STAGE,
                this.buildRuns,
                this.clearRuns,
                this.auditRuns,
                this.auditFailures,
                this.lastBuildError,
                this.lastBuildDurationMs,
                ready,
                this.requestedSamples,
                this.records.size(),
                this.rejectedSamples,
                this.modelIdList(),
                this.blockStateList(),
                this.spriteList(),
                this.records.size(),
                this.fluidRejected,
                this.translucentRejected,
                this.missingModelRejected,
                this.missingSpriteRejected,
                this.unsupportedRejected,
                ForgeRealModelStoreSampleRecord.BYTES,
                this.records.size() * ForgeRealModelStoreSampleRecord.BYTES,
                false,
                false,
                this.sampleSetStale,
                this.lastReloadInvalidatedSampleSet,
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
                this.lastAudit.modelColourBufferMatch()
        );
    }

    ForgeModelSampleSetAuditResult createAuditStatusSnapshot() {
        return this.lastAudit;
    }

    List<ForgeRealModelStoreSampleRecord> records() {
        return this.records;
    }

    int[] modelIds() {
        int[] ids = new int[this.records.size()];
        for (int i = 0; i < this.records.size(); i++) {
            ids[i] = this.records.get(i).modelId();
        }
        return ids;
    }

    String dump() {
        if (this.records.isEmpty()) {
            return "Voxy model sample set: none. Run /voxy model_sample_set_build first.";
        }
        StringBuilder builder = new StringBuilder("Voxy model sample set: ");
        builder.append("stage=").append(STAGE)
                .append(" sampleSetReady=").append(this.createStatusSnapshot().sampleSetReady())
                .append(" acceptedSamples=").append(this.records.size())
                .append(" recordBytesPerModel=").append(ForgeRealModelStoreSampleRecord.BYTES)
                .append(" totalModelRecordBytes=").append(this.records.size() * ForgeRealModelStoreSampleRecord.BYTES)
                .append(" formalLayoutCompatible=false formalModelBridgeReady=false");
        int limit = Math.min(6, this.records.size());
        for (int i = 0; i < limit; i++) {
            ForgeRealModelStoreSampleRecord record = this.records.get(i);
            builder.append(" sample").append(i)
                    .append("{modelId=").append(record.modelId())
                    .append(",blockStateId=").append(record.blockStateId())
                    .append(",blockState=\"").append(record.blockState())
                    .append("\",sprite=").append(record.sourceSprite())
                    .append(",faceMask=0x").append(Integer.toHexString(record.faceMask()).toUpperCase())
                    .append("}");
        }
        return builder.toString();
    }

    void clear() {
        this.clearRuns++;
        this.records = List.of();
        this.generation = -1L;
        this.dimensionId = "none";
        this.lastBuildError = "none";
        this.lastBuildDurationMs = 0.0D;
        this.auditRuns = 0L;
        this.auditFailures = 0L;
        this.sampleSetStale = false;
        this.lastReloadInvalidatedSampleSet = false;
        this.resetRejectCounters();
        this.lastAudit = ForgeModelSampleSetAuditResult.failure("none", 0.0D);
        this.buffer.close();
    }

    void markStale(String reason) {
        this.records = List.of();
        this.generation = -1L;
        this.dimensionId = "none";
        this.sampleSetStale = true;
        this.lastReloadInvalidatedSampleSet = true;
        this.lastBuildError = reason == null || reason.isBlank() ? "stale" : reason;
        this.lastAudit = ForgeModelSampleSetAuditResult.failure(this.lastBuildError, 0.0D);
        this.buffer.close();
    }

    private List<ForgeRealModelStoreSampleRecord> buildRecords(int requested) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null || minecraft.getBlockRenderer() == null) {
            return List.of();
        }

        List<ForgeRealModelStoreSampleRecord> built = new ArrayList<>(requested);
        LinkedHashSet<Integer> seenModelIds = new LinkedHashSet<>();
        for (Candidate candidate : this.collectCandidates()) {
            if (built.size() >= requested) {
                break;
            }
            if (!seenModelIds.add(candidate.modelId())) {
                continue;
            }
            Optional<ForgeRealModelStoreSampleRecord> record = this.tryBuildRecord(minecraft, candidate);
            if (record.isPresent()) {
                built.add(record.get());
            }
        }
        this.rejectedSamples = this.fluidRejected + this.translucentRejected + this.missingModelRejected + this.missingSpriteRejected + this.unsupportedRejected;
        return built;
    }

    private List<Candidate> collectCandidates() {
        LinkedHashMap<Integer, Candidate> candidatesByBlockState = new LinkedHashMap<>();
        this.addHeapModelCandidates(candidatesByBlockState);
        for (ForgeVoxyModelIdMapper.ModelIdMapping mapping : ForgeVoxyModelIdMapper.INSTANCE.createSnapshot(MAX_MAPPER_CANDIDATES)) {
            this.blockStateForId(mapping.blockStateId()).ifPresent(state -> this.addCandidate(candidatesByBlockState, mapping.modelId(), mapping.blockStateId(), state));
        }
        this.addNearbySolidCandidates(candidatesByBlockState);
        this.addCommonCandidate(candidatesByBlockState, Blocks.BEDROCK.defaultBlockState());
        this.addCommonCandidate(candidatesByBlockState, Blocks.STONE.defaultBlockState());
        this.addCommonCandidate(candidatesByBlockState, Blocks.DIRT.defaultBlockState());
        this.addCommonCandidate(candidatesByBlockState, Blocks.GRASS_BLOCK.defaultBlockState());
        this.addCommonCandidate(candidatesByBlockState, Blocks.SAND.defaultBlockState());
        this.addCommonCandidate(candidatesByBlockState, Blocks.GRAVEL.defaultBlockState());
        this.addCommonCandidate(candidatesByBlockState, Blocks.OAK_LOG.defaultBlockState());
        this.addCommonCandidate(candidatesByBlockState, Blocks.OAK_PLANKS.defaultBlockState());
        return new ArrayList<>(candidatesByBlockState.values());
    }

    private void addHeapModelCandidates(LinkedHashMap<Integer, Candidate> candidatesByBlockState) {
        try {
            this.instance.ensureActiveWorldSkeletonForCurrentWorldIfAllowed();
            this.instance.getGpuGeometryUploadManager().processForDebugCommand(8);
            ForgeGpuGeometryHeap heap = this.instance.getGpuGeometryUploadManager().getHeapForDebugReadback();
            if (heap == null || !heap.isCreated()) {
                return;
            }
            ForgeMdicCommandManager commandManager = this.instance.getMdicCommandManager();
            ForgeMdicCommandList commandList = commandManager.commandListForDebugDraw();
            if (!commandList.isValid() || commandList.isStale(heap.generation())) {
                commandManager.planSample();
                commandList = commandManager.commandListForDebugDraw();
            }
            if (!commandList.isValid()) {
                return;
            }
            LinkedHashSet<Integer> modelIds = new LinkedHashSet<>();
            long recordsLeft = MAX_HEAP_RECORD_SCAN;
            for (ForgeMdicCommand command : commandList.commands()) {
                if (recordsLeft <= 0L || modelIds.size() >= MAX_REQUESTED_SAMPLES * 2) {
                    break;
                }
                int count = (int) Math.min(recordsLeft, Math.max(0, command.recordCount()));
                long[] raw = heap.readbackGeometry(command.geometryPtr() + command.recordStart(), count);
                for (long record : raw) {
                    int modelId = ForgeVoxyQuadEncoder.extractModelId(record);
                    if (modelId > 0) {
                        modelIds.add(modelId);
                    }
                    if (modelIds.size() >= MAX_REQUESTED_SAMPLES * 2) {
                        break;
                    }
                }
                recordsLeft -= count;
            }
            for (int modelId : modelIds) {
                OptionalInt blockStateId = ForgeVoxyModelIdMapper.INSTANCE.blockStateIdForModelId(modelId);
                if (blockStateId.isPresent()) {
                    this.blockStateForId(blockStateId.getAsInt()).ifPresent(state -> this.addCandidate(candidatesByBlockState, modelId, blockStateId.getAsInt(), state));
                }
            }
        } catch (RuntimeException ignored) {
            // Heap-backed candidate collection is best-effort; mapper/common candidates still follow.
        }
    }

    private void addNearbySolidCandidates(LinkedHashMap<Integer, Candidate> candidatesByBlockState) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            return;
        }
        BlockPos origin = minecraft.player.blockPosition();
        for (int dy = 0; dy <= SAMPLE_SEARCH_DOWN_BLOCKS && candidatesByBlockState.size() < MAX_REQUESTED_SAMPLES * 2; dy++) {
            for (int dz = -4; dz <= 4 && candidatesByBlockState.size() < MAX_REQUESTED_SAMPLES * 2; dz++) {
                for (int dx = -4; dx <= 4 && candidatesByBlockState.size() < MAX_REQUESTED_SAMPLES * 2; dx++) {
                    BlockState state = minecraft.level.getBlockState(origin.offset(dx, -dy, dz));
                    if (isSolidSampleState(state)) {
                        this.addCommonCandidate(candidatesByBlockState, state);
                    }
                }
            }
        }
    }

    private void addCommonCandidate(LinkedHashMap<Integer, Candidate> candidatesByBlockState, BlockState state) {
        int blockStateId = this.blockStateIdForState(state);
        int modelId = ForgeVoxyModelIdMapper.INSTANCE.getOrCreateModelId(blockStateId).modelId();
        this.addCandidate(candidatesByBlockState, modelId, blockStateId, state);
    }

    private void addCandidate(LinkedHashMap<Integer, Candidate> candidatesByBlockState, int modelId, int blockStateId, BlockState state) {
        if (blockStateId <= 0 || state == null) {
            return;
        }
        candidatesByBlockState.putIfAbsent(blockStateId, new Candidate(modelId, blockStateId, state));
    }

    private Optional<ForgeRealModelStoreSampleRecord> tryBuildRecord(Minecraft minecraft, Candidate candidate) {
        BlockState state = candidate.state();
        if (state == null || state.isAir()) {
            this.unsupportedRejected++;
            return Optional.empty();
        }
        if (!state.getFluidState().isEmpty()) {
            this.fluidRejected++;
            return Optional.empty();
        }
        if (ForgeCpuMeshLayer.fromBlockState(state) != ForgeCpuMeshLayer.SOLID) {
            this.translucentRejected++;
            return Optional.empty();
        }

        BlockRenderDispatcher blockRenderer = minecraft.getBlockRenderer();
        var model = blockRenderer.getBlockModel(state);
        if (model == null || model.isCustomRenderer()) {
            this.missingModelRejected++;
            return Optional.empty();
        }

        List<ForgeRealModelStoreFaceSample> faces = new ArrayList<>(DIRECTIONS.length);
        int[] faceWords = new int[ForgeModelStoreFormalLayout.FACE_DATA_WORDS];
        int tintedFaces = 0;
        int untintedFaces = 0;
        int totalQuads = 0;
        String sourceSprite = "none";
        String sourceAtlas = "none";
        boolean shaded;
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
            if ("none".equals(encoding.spriteName()) || "unavailable".equals(encoding.spriteName())) {
                this.missingSpriteRejected++;
                return Optional.empty();
            }
            faceWords[faceIndex] = encoding.faceDataWord();
            if (quad.isTinted()) {
                tintedFaces++;
            } else {
                untintedFaces++;
            }
            if (sourceSprite.equals("none")) {
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
            this.missingModelRejected++;
            return Optional.empty();
        }

        int flagsA = 0;
        flagsA |= tintedFaces > 0 ? 1 : 0;
        flagsA |= shaded ? 8 : 0;
        int colourTint = tintedFaces == 0 ? -1 : this.sampleTintColour(minecraft, state, faces);
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
                colourTint,
                List.copyOf(faces),
                tintedFaces,
                untintedFaces
        ));
    }

    private ForgeModelSampleSetAuditResult compareReadback(long start) {
        int[] modelWords = this.buffer.readbackModelDataWords();
        int[] colours = this.buffer.readbackModelColours();
        int invalidRecords = 0;
        boolean dataMatch = true;
        boolean colourMatch = colours.length == this.records.size();
        for (int i = 0; i < this.records.size(); i++) {
            ForgeRealModelStoreSampleRecord record = this.records.get(i);
            if (!record.matchesWords(modelWords, i * ForgeRealModelStoreSampleRecord.WORDS)) {
                dataMatch = false;
                invalidRecords++;
            }
            if (i >= colours.length || colours[i] != record.modelColour()) {
                colourMatch = false;
                invalidRecords++;
            }
        }
        boolean success = dataMatch && colourMatch && invalidRecords == 0;
        return new ForgeModelSampleSetAuditResult(
                success,
                success ? "none" : "buffer-mismatch",
                elapsedMs(start),
                this.records.size(),
                (int) this.buffer.modelDataBytes(),
                invalidRecords,
                dataMatch,
                colourMatch
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

    private void failBuild(String error, long start) {
        this.lastBuildError = error == null || error.isBlank() ? "build-failed" : error;
        this.lastBuildDurationMs = elapsedMs(start);
    }

    private void resetRejectCounters() {
        this.rejectedSamples = 0;
        this.fluidRejected = 0;
        this.translucentRejected = 0;
        this.missingModelRejected = 0;
        this.missingSpriteRejected = 0;
        this.unsupportedRejected = 0;
    }

    private String modelIdList() {
        if (this.records.isEmpty()) {
            return "none";
        }
        StringBuilder builder = new StringBuilder();
        for (ForgeRealModelStoreSampleRecord record : this.records) {
            if (builder.length() > 0) {
                builder.append(',');
            }
            builder.append(record.modelId());
        }
        return builder.toString();
    }

    private String blockStateList() {
        return compactList("none", this.records.stream().map(ForgeRealModelStoreSampleRecord::blockState).toList());
    }

    private String spriteList() {
        return compactList("none", this.records.stream().map(ForgeRealModelStoreSampleRecord::sourceSprite).toList());
    }

    private static String compactList(String empty, List<String> values) {
        if (values.isEmpty()) {
            return empty;
        }
        StringBuilder builder = new StringBuilder();
        int limit = Math.min(8, values.size());
        for (int i = 0; i < limit; i++) {
            if (i > 0) {
                builder.append('|');
            }
            builder.append(values.get(i).replace(' ', '_'));
        }
        if (values.size() > limit) {
            builder.append("|...");
        }
        return builder.toString();
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
