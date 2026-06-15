package me.cortex.voxy.forge;

import me.cortex.voxy.common.world.WorldEngine;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

final class ForgeBakedModelBridge {
    static final String STAGE = "G6_12_BAKED_MODEL_SPRITE_BRIDGE_AUDIT";
    private static final int MAX_SAMPLES = 8;
    private static final int SAMPLE_SEARCH_DOWN_BLOCKS = 128;
    private static final int VERTICES_PER_QUAD = 4;
    private static final Direction[] DIRECTIONS = Direction.values();

    private final ForgeVoxyInstance instance;
    private List<ForgeBakedModelSample> samples = List.of();
    private long checkRuns;
    private long clearRuns;
    private long auditRuns;
    private long auditFailures;
    private boolean bakedModelSamplesStale;
    private boolean spriteSamplesStale;
    private boolean lastReloadInvalidatedBakedModelSamples;
    private ForgeBakedModelBridgeStats lastStats = ForgeBakedModelBridgeStats.empty(0L);
    private ForgeBakedModelBridgeAuditResult lastAudit = ForgeBakedModelBridgeAuditResult.failure(
            "none",
            0.0D,
            ForgeBakedModelBridgeStats.empty(0L)
    );

    ForgeBakedModelBridge(ForgeVoxyInstance instance) {
        this.instance = instance;
    }

    ForgeBakedModelBridgeStats check() {
        this.checkRuns++;
        long start = System.nanoTime();
        String error = "none";
        List<ForgeBakedModelSample> checkedSamples = List.of();
        try {
            this.seedCurrentWorldSamples();
            checkedSamples = this.collectSamples();
        } catch (RuntimeException e) {
            error = e.getClass().getSimpleName() + ": " + e.getMessage();
        }

        this.samples = List.copyOf(checkedSamples);
        this.bakedModelSamplesStale = false;
        this.spriteSamplesStale = false;
        this.lastReloadInvalidatedBakedModelSamples = false;
        this.lastStats = this.createStats(error, elapsedMs(start), false, "none", 0.0D);
        this.lastAudit = ForgeBakedModelBridgeAuditResult.failure("none", 0.0D, this.lastStats);
        return this.lastStats;
    }

    ForgeBakedModelBridgeAuditResult audit() {
        this.auditRuns++;
        long start = System.nanoTime();
        String error = "none";
        boolean success = true;

        if (this.samples.isEmpty()) {
            success = false;
            error = "no-baked-model-samples";
        } else {
            int explainedEmptySamples = 0;
            int invalidSamples = 0;
            for (ForgeBakedModelSample sample : this.samples) {
                if (sample.modelId() < 0 || sample.blockStateId() < 0 || sample.blockState().equals("none")) {
                    invalidSamples++;
                    continue;
                }
                if ("missing".equals(sample.bakedModelClass())) {
                    invalidSamples++;
                    continue;
                }
                if (sample.quadCount() == 0) {
                    if (sample.fluidLike() || sample.airLike() || sample.note().contains("custom-renderer") || sample.note().contains("empty-model")) {
                        explainedEmptySamples++;
                    } else {
                        invalidSamples++;
                    }
                    continue;
                }

                ForgeBakedQuadSample quad = sample.quadSample();
                if (quad.quadVerticesLength() < 24 || !quad.hasAtlasSprite() || !quad.spriteUvReadable()) {
                    invalidSamples++;
                }
            }

            ForgeBakedModelBridgeStats current = this.createStats("none", this.lastStats.lastCheckDurationMs(), false, "none", 0.0D);
            if (current.sampledQuads() == 0 && explainedEmptySamples == 0) {
                invalidSamples++;
            }
            if (invalidSamples != 0) {
                success = false;
                error = "invalid-samples=" + invalidSamples;
            }
        }

        double duration = elapsedMs(start);
        if (!success) {
            this.auditFailures++;
        }
        this.lastStats = this.createStats(this.lastStats.lastCheckError(), this.lastStats.lastCheckDurationMs(), success, error, duration);
        this.lastAudit = new ForgeBakedModelBridgeAuditResult(success, error, duration, this.lastStats);
        return this.lastAudit;
    }

    ForgeBakedModelBridgeStats createStatusSnapshot() {
        return this.lastStats;
    }

    ForgeBakedModelBridgeAuditResult createAuditStatusSnapshot() {
        return this.lastAudit;
    }

    String dumpSample() {
        if (this.samples.isEmpty()) {
            return "Voxy baked model bridge sample: none. Run /voxy baked_model_bridge_check first.";
        }
        ForgeBakedModelSample sample = this.displaySample();
        ForgeBakedQuadSample quad = sample.quadSample();
        return String.format(
                "Voxy baked model bridge sample: modelId=%d blockStateId=%d blockState=\"%s\" fluidLike=%s bakedModelClass=%s renderLayer=%s quadCount=%d quadDirection=%s quadTintIndex=%d quadHasTint=%s quadSpriteName=%s quadSpriteAtlas=%s quadUvMin=%s quadUvMax=%s quadCullDirection=%s quadVerticesLength=%d hasRealModelMetadata=%s hasAtlasSprite=%s hasAtlasUpload=%s note=%s draw=false customAtlasUpload=false formalRenderer=false",
                sample.modelId(),
                sample.blockStateId(),
                sample.blockState(),
                sample.fluidLike(),
                sample.bakedModelClass(),
                sample.renderLayer(),
                sample.quadCount(),
                quad.quadDirection(),
                quad.quadTintIndex(),
                quad.quadHasTint(),
                quad.quadSpriteName(),
                quad.quadSpriteAtlas(),
                quad.quadUvMin(),
                quad.quadUvMax(),
                quad.quadCullDirection(),
                quad.quadVerticesLength(),
                sample.hasRealModelMetadata(),
                quad.hasAtlasSprite(),
                sample.hasAtlasUpload(),
                sample.note()
        );
    }

    void clear() {
        this.clearRuns++;
        this.checkRuns = 0L;
        this.auditRuns = 0L;
        this.auditFailures = 0L;
        this.samples = List.of();
        this.bakedModelSamplesStale = false;
        this.spriteSamplesStale = false;
        this.lastReloadInvalidatedBakedModelSamples = false;
        this.lastStats = ForgeBakedModelBridgeStats.empty(this.clearRuns);
        this.lastAudit = ForgeBakedModelBridgeAuditResult.failure("none", 0.0D, this.lastStats);
    }

    void markStale(String reason) {
        this.samples = List.of();
        this.bakedModelSamplesStale = true;
        this.spriteSamplesStale = true;
        this.lastReloadInvalidatedBakedModelSamples = true;
        String staleReason = reason == null || reason.isBlank() ? "stale" : reason;
        this.lastStats = this.createStats(staleReason, 0.0D, false, staleReason, 0.0D);
        this.lastAudit = ForgeBakedModelBridgeAuditResult.failure(staleReason, 0.0D, this.lastStats);
    }

    private List<ForgeBakedModelSample> collectSamples() {
        Minecraft minecraft = Minecraft.getInstance();
        BlockRenderDispatcher blockRenderer = minecraft.getBlockRenderer();
        if (blockRenderer == null) {
            return List.of();
        }

        List<ForgeVoxyModelIdMapper.ModelIdMapping> mappings = ForgeVoxyModelIdMapper.INSTANCE.createSnapshot(MAX_SAMPLES);
        if (mappings.isEmpty()) {
            return List.of();
        }

        List<ForgeBakedModelSample> result = new ArrayList<>(mappings.size());
        for (ForgeVoxyModelIdMapper.ModelIdMapping mapping : mappings) {
            Optional<BlockState> state = this.blockStateForId(mapping.blockStateId());
            if (state.isEmpty()) {
                result.add(ForgeBakedModelSample.missing(mapping.modelId(), mapping.blockStateId(), "unavailable", "blockstate-unavailable"));
                continue;
            }
            result.add(this.sampleModel(blockRenderer, mapping.modelId(), mapping.blockStateId(), state.get()));
        }
        return result;
    }

    private ForgeBakedModelSample sampleModel(BlockRenderDispatcher blockRenderer, int modelId, int blockStateId, BlockState state) {
        boolean fluidLike = !state.getFluidState().isEmpty();
        boolean airLike = state.isAir();
        String renderLayer = this.renderLayerString(state);
        if (airLike) {
            return new ForgeBakedModelSample(
                    modelId,
                    blockStateId,
                    state.toString(),
                    fluidLike,
                    true,
                    "air",
                    renderLayer,
                    0,
                    ForgeBakedQuadSample.none(),
                    false,
                    false,
                    "air-blockstate"
            );
        }

        try {
            var model = blockRenderer.getBlockModel(state);
            if (model == null) {
                return ForgeBakedModelSample.missing(modelId, blockStateId, state.toString(), "baked-model-null");
            }
            if (model.isCustomRenderer()) {
                return new ForgeBakedModelSample(
                        modelId,
                        blockStateId,
                        state.toString(),
                        fluidLike,
                        false,
                        model.getClass().getName(),
                        renderLayer,
                        0,
                        ForgeBakedQuadSample.none(),
                        true,
                        false,
                        "custom-renderer"
                );
            }

            QuadAccumulation quads = this.collectQuads(model, state, blockStateId);
            String note = quads.count() == 0 ? (fluidLike ? "fluid-like-no-ordinary-baked-quads" : "empty-model") : "sampled-baked-quads";
            return new ForgeBakedModelSample(
                    modelId,
                    blockStateId,
                    state.toString(),
                    fluidLike,
                    false,
                    model.getClass().getName(),
                    renderLayer,
                    quads.count(),
                    quads.sample(),
                    true,
                    false,
                    note
            );
        } catch (RuntimeException e) {
            return ForgeBakedModelSample.missing(modelId, blockStateId, state.toString(), e.getClass().getSimpleName());
        }
    }

    private QuadAccumulation collectQuads(net.minecraft.client.resources.model.BakedModel model, BlockState state, int blockStateId) {
        int count = 0;
        ForgeBakedQuadSample sample = ForgeBakedQuadSample.none();
        List<BakedQuad> general = safeGetQuads(model, state, null, blockStateId);
        count += general.size();
        if (!general.isEmpty()) {
            sample = sampleQuad(general.get(0), null);
        }
        for (Direction direction : DIRECTIONS) {
            List<BakedQuad> directional = safeGetQuads(model, state, direction, blockStateId);
            count += directional.size();
            if (!directional.isEmpty() && !sample.hasAtlasSprite()) {
                sample = sampleQuad(directional.get(0), direction);
            }
        }
        return new QuadAccumulation(count, sample);
    }

    private static List<BakedQuad> safeGetQuads(net.minecraft.client.resources.model.BakedModel model, BlockState state, Direction direction, int blockStateId) {
        try {
            List<BakedQuad> quads = model.getQuads(state, direction, RandomSource.create(blockStateId));
            return quads == null ? List.of() : quads;
        } catch (RuntimeException e) {
            return List.of();
        }
    }

    private static ForgeBakedQuadSample sampleQuad(BakedQuad quad, Direction cullDirection) {
        TextureAtlasSprite sprite = quad.getSprite();
        String spriteName = "none";
        String spriteAtlas = "none";
        boolean hasSprite = sprite != null;
        if (sprite != null) {
            try {
                spriteName = sprite.contents().name().toString();
            } catch (RuntimeException e) {
                spriteName = "unavailable";
            }
            try {
                spriteAtlas = sprite.atlasLocation().toString();
            } catch (RuntimeException e) {
                spriteAtlas = "unavailable";
            }
        }

        UvRange uvRange = readUvRange(quad.getVertices());
        return new ForgeBakedQuadSample(
                quad.getDirection() == null ? "none" : quad.getDirection().getName(),
                quad.getTintIndex(),
                quad.isTinted(),
                spriteName,
                spriteAtlas,
                uvRange.min(),
                uvRange.max(),
                cullDirection == null ? "none" : cullDirection.getName(),
                quad.getVertices() == null ? 0 : quad.getVertices().length,
                hasSprite,
                uvRange.readable()
        );
    }

    private static UvRange readUvRange(int[] vertices) {
        if (vertices == null || vertices.length < 24 || vertices.length % VERTICES_PER_QUAD != 0) {
            return new UvRange("none", "none", false);
        }

        int stride = vertices.length / VERTICES_PER_QUAD;
        if (stride < 6) {
            return new UvRange("none", "none", false);
        }

        float minU = Float.POSITIVE_INFINITY;
        float minV = Float.POSITIVE_INFINITY;
        float maxU = Float.NEGATIVE_INFINITY;
        float maxV = Float.NEGATIVE_INFINITY;
        for (int vertex = 0; vertex < VERTICES_PER_QUAD; vertex++) {
            int offset = vertex * stride;
            float u = Float.intBitsToFloat(vertices[offset + 4]);
            float v = Float.intBitsToFloat(vertices[offset + 5]);
            minU = Math.min(minU, u);
            minV = Math.min(minV, v);
            maxU = Math.max(maxU, u);
            maxV = Math.max(maxV, v);
        }

        if (!Float.isFinite(minU) || !Float.isFinite(minV) || !Float.isFinite(maxU) || !Float.isFinite(maxV)) {
            return new UvRange("none", "none", false);
        }
        return new UvRange(String.format("%.5f,%.5f", minU, minV), String.format("%.5f,%.5f", maxU, maxV), true);
    }

    private void seedCurrentWorldSamples() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            return;
        }

        BlockPos origin = minecraft.player.blockPosition();
        BlockState firstFluid = null;
        BlockState firstSolid = null;
        for (int dy = 0; dy <= SAMPLE_SEARCH_DOWN_BLOCKS && firstSolid == null; dy++) {
            for (int dz = -2; dz <= 2 && firstSolid == null; dz++) {
                for (int dx = -2; dx <= 2; dx++) {
                    BlockPos candidate = origin.offset(dx, -dy, dz);
                    BlockState state = minecraft.level.getBlockState(candidate);
                    if (state.isAir()) {
                        continue;
                    }
                    if (!state.getFluidState().isEmpty()) {
                        if (firstFluid == null) {
                            firstFluid = state;
                        }
                        continue;
                    }
                    firstSolid = state;
                    break;
                }
            }
        }
        if (firstSolid != null) {
            this.ensurePlaceholderModelId(firstSolid);
        }
        if (firstFluid != null) {
            this.ensurePlaceholderModelId(firstFluid);
        }
    }

    private void ensurePlaceholderModelId(BlockState state) {
        int blockStateId = this.blockStateIdForState(state);
        if (blockStateId >= 0) {
            ForgeVoxyModelIdMapper.INSTANCE.getOrCreateModelId(blockStateId);
        }
    }

    private int blockStateIdForState(BlockState state) {
        Optional<WorldEngine> engine = this.instance.getCurrentEngineOptional();
        if (engine.isPresent()) {
            try {
                return engine.get().getMapper().getIdForBlockState(state);
            } catch (RuntimeException ignored) {
                // Fall through to the vanilla registry fallback.
            }
        }
        try {
            return Block.BLOCK_STATE_REGISTRY.getId(state);
        } catch (RuntimeException ignored) {
            return -1;
        }
    }

    private Optional<BlockState> blockStateForId(int blockStateId) {
        Optional<WorldEngine> engine = this.instance.getCurrentEngineOptional();
        if (engine.isPresent()) {
            try {
                return Optional.of(engine.get().getMapper().getBlockStateFromBlockId(blockStateId));
            } catch (RuntimeException ignored) {
                // Fall through to the vanilla registry fallback.
            }
        }
        try {
            BlockState state = Block.BLOCK_STATE_REGISTRY.byId(blockStateId);
            return state == null ? Optional.empty() : Optional.of(state);
        } catch (RuntimeException ignored) {
            return Optional.empty();
        }
    }

    private String renderLayerString(BlockState state) {
        try {
            RenderType type = state.getFluidState().isEmpty()
                    ? ItemBlockRenderTypes.getChunkRenderType(state)
                    : ItemBlockRenderTypes.getRenderLayer(state.getFluidState());
            ForgeCpuMeshLayer layer = ForgeCpuMeshLayer.fromBlockState(state);
            return layer.displayName + ":" + type;
        } catch (RuntimeException e) {
            return "unknown";
        }
    }

    private ForgeBakedModelBridgeStats createStats(String error, double durationMs, boolean auditOk, String auditError, double auditDurationMs) {
        int sampledModelIds = this.samples.size();
        int sampledBlockStates = 0;
        int sampledBakedModels = 0;
        int sampledQuads = 0;
        int sampledSprites = 0;
        int missingBakedModels = 0;
        int missingSprites = 0;
        int fluidLikeSamples = 0;
        int emptyModelSamples = 0;
        int unsupportedSamples = 0;
        boolean renderLayerReadable = false;
        boolean spriteUvReadable = false;
        boolean spriteAtlasReadable = false;

        for (ForgeBakedModelSample sample : this.samples) {
            if (sample.blockStateId() >= 0 && !"unavailable".equals(sample.blockState())) {
                sampledBlockStates++;
            }
            if (!"missing".equals(sample.bakedModelClass()) && !"air".equals(sample.bakedModelClass())) {
                sampledBakedModels++;
            } else if ("missing".equals(sample.bakedModelClass())) {
                missingBakedModels++;
            }
            if (sample.fluidLike()) {
                fluidLikeSamples++;
            }
            if (sample.quadCount() == 0) {
                emptyModelSamples++;
            }
            if (sample.note().contains("custom-renderer")) {
                unsupportedSamples++;
            }
            if (!"unknown".equals(sample.renderLayer())) {
                renderLayerReadable = true;
            }
            sampledQuads += sample.quadCount();
            ForgeBakedQuadSample quad = sample.quadSample();
            if (quad.hasAtlasSprite()) {
                sampledSprites++;
                spriteAtlasReadable = !"none".equals(quad.quadSpriteAtlas()) && !"unavailable".equals(quad.quadSpriteAtlas());
            } else if (sample.quadCount() != 0) {
                missingSprites++;
            }
            if (quad.spriteUvReadable()) {
                spriteUvReadable = true;
            }
        }

        ForgeBakedModelSample sample = this.displaySampleOrNull();
        ForgeBakedQuadSample quad = sample == null ? ForgeBakedQuadSample.none() : sample.quadSample();
        boolean placeholderModelIdsPresent = ForgeVoxyModelIdMapper.INSTANCE.uniqueModelCount() > 0;
        boolean canMapModelIdToBlockState = sampledBlockStates > 0;
        boolean bridgeReady = canMapModelIdToBlockState && sampledBakedModels > 0;
        boolean samplesReady = sampledQuads > 0 && sampledSprites > 0 && spriteUvReadable;
        ResourceLocation atlasLocation = TextureAtlas.LOCATION_BLOCKS;
        String blockAtlasLocation = atlasLocation == null ? "none" : atlasLocation.toString();

        return new ForgeBakedModelBridgeStats(
                STAGE,
                this.checkRuns,
                this.clearRuns,
                this.auditRuns,
                this.auditFailures,
                error,
                durationMs,
                auditOk,
                auditError,
                auditDurationMs,
                placeholderModelIdsPresent,
                canMapModelIdToBlockState,
                bridgeReady,
                samplesReady,
                sampledModelIds,
                sampledBlockStates,
                sampledBakedModels,
                sampledQuads,
                sampledSprites,
                missingBakedModels,
                missingSprites,
                fluidLikeSamples,
                emptyModelSamples,
                unsupportedSamples,
                renderLayerReadable,
                spriteAtlasReadable,
                spriteUvReadable,
                atlasLocation != null,
                blockAtlasLocation,
                false,
                false,
                false,
                false,
                this.bakedModelSamplesStale,
                this.spriteSamplesStale,
                this.lastReloadInvalidatedBakedModelSamples,
                currentDimension(),
                sample == null ? -1 : sample.modelId(),
                sample == null ? -1 : sample.blockStateId(),
                sample == null ? "none" : sample.blockState(),
                sample != null && sample.fluidLike(),
                sample == null ? "none" : sample.bakedModelClass(),
                sample == null ? "unknown" : sample.renderLayer(),
                sample == null ? 0 : sample.quadCount(),
                quad.quadDirection(),
                quad.quadTintIndex(),
                quad.quadHasTint(),
                quad.quadSpriteName(),
                quad.quadSpriteAtlas(),
                quad.quadUvMin(),
                quad.quadUvMax(),
                quad.quadCullDirection(),
                quad.quadVerticesLength(),
                sample != null && sample.hasRealModelMetadata(),
                quad.hasAtlasSprite(),
                sample != null && sample.hasAtlasUpload()
        );
    }

    private static String currentDimension() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.level == null ? "none" : minecraft.level.dimension().location().toString();
    }

    private ForgeBakedModelSample displaySample() {
        ForgeBakedModelSample sample = this.displaySampleOrNull();
        if (sample == null) {
            throw new IllegalStateException("No baked model samples are available");
        }
        return sample;
    }

    private ForgeBakedModelSample displaySampleOrNull() {
        for (ForgeBakedModelSample sample : this.samples) {
            if (sample.quadSample().hasAtlasSprite() && sample.quadSample().spriteUvReadable()) {
                return sample;
            }
        }
        for (ForgeBakedModelSample sample : this.samples) {
            if (sample.quadCount() > 0) {
                return sample;
            }
        }
        return this.samples.isEmpty() ? null : this.samples.get(0);
    }

    private static double elapsedMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000.0D;
    }

    private record QuadAccumulation(int count, ForgeBakedQuadSample sample) {
    }

    private record UvRange(String min, String max, boolean readable) {
    }
}
