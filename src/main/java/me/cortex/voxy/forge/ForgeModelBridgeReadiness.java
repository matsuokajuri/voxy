package me.cortex.voxy.forge;

import me.cortex.voxy.common.world.WorldEngine;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Optional;

final class ForgeModelBridgeReadiness {
    static final String STAGE = "G6_9_MODELSTORE_BRIDGE_READINESS";
    private static final int SAMPLE_SEARCH_DOWN_BLOCKS = 128;

    private final ForgeVoxyInstance instance;
    private long checkRuns;
    private long clearRuns;
    private ForgeModelBridgeReadinessStats lastStats = ForgeModelBridgeReadinessStats.empty(0L);

    ForgeModelBridgeReadiness(ForgeVoxyInstance instance) {
        this.instance = instance;
    }

    ForgeModelBridgeAuditResult check() {
        this.checkRuns++;
        long start = System.nanoTime();
        Sample sample = this.sampleCurrentBlock();
        ForgeModelBridgeReadinessStats stats = this.createStats(sample, "none", elapsedMs(start));
        this.lastStats = stats;
        return new ForgeModelBridgeAuditResult(true, "none", stats.lastCheckDurationMs(), stats);
    }

    ForgeModelBridgeReadinessStats createStatusSnapshot() {
        return this.withModelStoreStatus(this.lastStats);
    }

    String dumpSample() {
        ForgeModelBridgeReadinessStats stats = this.lastStats;
        if (stats.sampleModelId() < 0) {
            return "Voxy model bridge sample: none. Run /voxy model_bridge_check first.";
        }
        return String.format(
                "Voxy model bridge sample: modelId=%d blockStateId=%d blockStateIdSource=%s blockState=\"%s\" placeholder=%s realModelMetadata=%s textureMetadata=%s note=%s formalModelBridgeReady=%s",
                stats.sampleModelId(),
                stats.sampleBlockStateId(),
                stats.blockStateIdSource(),
                stats.sampleBlockState(),
                stats.sampleIsPlaceholder(),
                stats.sampleHasRealModelMetadata(),
                stats.sampleHasTextureMetadata(),
                stats.sampleNote(),
                stats.formalModelBridgeReady()
        );
    }

    void clear() {
        this.clearRuns++;
        this.checkRuns = 0L;
        this.lastStats = ForgeModelBridgeReadinessStats.empty(this.clearRuns);
    }

    private ForgeModelBridgeReadinessStats createStats(Sample sample, String error, double durationMs) {
        ForgeVoxyGeometryCache.StatusSnapshot cache = this.instance.getVoxyGeometryCache().createStatusSnapshot();
        ForgeModelStoreStats modelStoreStatus = this.instance.getModelStoreSkeleton().createStatusSnapshot();
        ForgeVoxyModelIdMapper mapper = ForgeVoxyModelIdMapper.INSTANCE;
        int mapperSize = mapper.uniqueModelCount();
        boolean placeholderModelIdsPresent = mapperSize > 0 || cache.totalUniqueModelIds() > 0;
        boolean stablePlaceholderModelIds = mapper.hasStableReverseMappings();
        boolean canMapModelIdToBlockState = sample != null && sample.modelId() >= 0 && !"none".equals(sample.blockStateString());

        String dimension = currentDimension();
        boolean activeWorldEnginePresent = this.instance.getCurrentEngineOptional().isPresent();
        String blockStateIdSource = sample == null ? "none" : sample.blockStateIdSource();
        int sampleModelId = sample == null ? -1 : sample.modelId();
        int sampleBlockStateId = sample == null ? -1 : sample.blockStateId();
        String sampleBlockState = sample == null ? "none" : sample.blockStateString();
        String sampleNote = sample == null ? "no-current-world-sample" : sample.note();

        return new ForgeModelBridgeReadinessStats(
                STAGE,
                this.checkRuns,
                this.clearRuns,
                error,
                durationMs,
                placeholderModelIdsPresent,
                stablePlaceholderModelIds,
                canMapModelIdToBlockState,
                modelStoreStatus.placeholderModelStoreReady(),
                modelStoreStatus.placeholderModelDataBufferReady(),
                modelStoreStatus.placeholderModelColourBufferReady(),
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
                false,
                mapperSize,
                cache.totalUniqueModelIds(),
                cache.missingModelRecords(),
                dimension,
                activeWorldEnginePresent,
                blockStateIdSource,
                sampleModelId,
                sampleBlockStateId,
                sampleBlockState,
                true,
                false,
                false,
                sampleNote
        );
    }

    private ForgeModelBridgeReadinessStats withModelStoreStatus(ForgeModelBridgeReadinessStats stats) {
        ForgeModelStoreStats modelStoreStatus = this.instance.getModelStoreSkeleton().createStatusSnapshot();
        return new ForgeModelBridgeReadinessStats(
                stats.stage(),
                stats.checkRuns(),
                stats.clearRuns(),
                stats.lastCheckError(),
                stats.lastCheckDurationMs(),
                stats.placeholderModelIdsPresent(),
                stats.stablePlaceholderModelIds(),
                stats.canMapModelIdToBlockState(),
                modelStoreStatus.placeholderModelStoreReady(),
                modelStoreStatus.placeholderModelDataBufferReady(),
                modelStoreStatus.placeholderModelColourBufferReady(),
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
                false,
                stats.placeholderModelIdCount(),
                stats.builtSectionUniqueModelIds(),
                stats.missingModelRecords(),
                stats.currentDimension(),
                stats.activeWorldEnginePresent(),
                stats.blockStateIdSource(),
                stats.sampleModelId(),
                stats.sampleBlockStateId(),
                stats.sampleBlockState(),
                stats.sampleIsPlaceholder(),
                stats.sampleHasRealModelMetadata(),
                stats.sampleHasTextureMetadata(),
                stats.sampleNote()
        );
    }

    private Sample sampleCurrentBlock() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            return this.sampleFromMapperSnapshot("no-client-world");
        }

        BlockPos origin = minecraft.player.blockPosition();
        BlockPos sampledPos = origin;
        BlockState sampledState = minecraft.level.getBlockState(origin);
        for (int dy = 0; dy <= SAMPLE_SEARCH_DOWN_BLOCKS; dy++) {
            BlockPos candidate = origin.below(dy);
            BlockState state = minecraft.level.getBlockState(candidate);
            if (!state.isAir()) {
                sampledPos = candidate;
                sampledState = state;
                break;
            }
        }

        Optional<WorldEngine> engine = this.instance.getCurrentEngineOptional();
        int blockStateId;
        String blockStateIdSource;
        if (engine.isPresent()) {
            blockStateId = engine.get().getMapper().getIdForBlockState(sampledState);
            blockStateIdSource = "voxy-mapper";
        } else {
            blockStateId = Block.BLOCK_STATE_REGISTRY.getId(sampledState);
            blockStateIdSource = "minecraft-block-state-registry-fallback";
        }

        ForgeVoxyModelIdMapper.ModelIdResult modelId = ForgeVoxyModelIdMapper.INSTANCE.getOrCreateModelId(blockStateId);
        String note = "sampled-current-world-pos=" + sampledPos.getX() + "," + sampledPos.getY() + "," + sampledPos.getZ()
                + (engine.isPresent() ? "" : ";no-active-voxy-world-engine");
        return new Sample(modelId.modelId(), blockStateId, blockStateIdSource, sampledState.toString(), note);
    }

    private Sample sampleFromMapperSnapshot(String note) {
        var snapshot = ForgeVoxyModelIdMapper.INSTANCE.createSnapshot(1);
        if (snapshot.isEmpty()) {
            return null;
        }
        ForgeVoxyModelIdMapper.ModelIdMapping mapping = snapshot.get(0);
        String blockStateString = this.blockStateString(mapping.blockStateId()).orElse("unavailable");
        return new Sample(mapping.modelId(), mapping.blockStateId(), "stored-placeholder-mapper", blockStateString, note);
    }

    private Optional<String> blockStateString(int blockStateId) {
        Optional<WorldEngine> engine = this.instance.getCurrentEngineOptional();
        if (engine.isPresent()) {
            try {
                return Optional.of(engine.get().getMapper().getBlockStateFromBlockId(blockStateId).toString());
            } catch (RuntimeException ignored) {
                // Fall through to the vanilla registry best-effort path below.
            }
        }
        try {
            BlockState state = Block.BLOCK_STATE_REGISTRY.byId(blockStateId);
            return state == null ? Optional.empty() : Optional.of(state.toString());
        } catch (RuntimeException ignored) {
            return Optional.empty();
        }
    }

    private static String currentDimension() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.level == null ? "none" : minecraft.level.dimension().location().toString();
    }

    private static double elapsedMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000.0D;
    }

    private record Sample(int modelId, int blockStateId, String blockStateIdSource, String blockStateString, String note) {
    }
}
