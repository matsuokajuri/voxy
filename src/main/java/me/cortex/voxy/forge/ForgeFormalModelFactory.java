package me.cortex.voxy.forge;

import me.cortex.voxy.common.world.WorldEngine;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

final class ForgeFormalModelFactory {
    static final String STAGE = "I3_FORMAL_MODELFACTORY_LIFECYCLE_SKELETON";
    private static final int SAMPLE_SEARCH_DOWN_BLOCKS = 128;
    private static final int MIN_FORMAL_MODEL_ID = 1;
    private static final int MAX_FORMAL_MODEL_ID = ForgeFormalModelStore.MODEL_CAPACITY - 1;

    private final ForgeVoxyInstance instance;
    private final LinkedHashSet<Integer> seenBlockStateIds = new LinkedHashSet<>();
    private final ArrayDeque<ForgeFormalModelBakeRequest> pendingRequests = new ArrayDeque<>();
    private final LinkedHashSet<Integer> inFlightBlockStateIds = new LinkedHashSet<>();
    private final LinkedHashMap<Integer, ForgeFormalModelIdMapping> idMappings = new LinkedHashMap<>();
    private final LinkedHashMap<Integer, String> metadataCache = new LinkedHashMap<>();
    private final LinkedHashMap<Integer, Integer> fluidStateLut = new LinkedHashMap<>();
    private final LinkedHashMap<String, Integer> modelTexture2id = new LinkedHashMap<>();
    private long requestRuns;
    private long processRuns;
    private long clearRuns;
    private long auditRuns;
    private long auditFailures;
    private int failedCount;
    private int requestedCount;
    private int nextFormalModelId = MIN_FORMAL_MODEL_ID;
    private int lastRequestBlockStateId = -1;
    private int lastAssignedFormalModelId = -1;
    private String lastFailureReason = "none";
    private String lastProcessError = "none";
    private double lastProcessDurationMs;
    private boolean enabled;
    private boolean stale;
    private boolean requiresRebuild;
    private boolean resourceReloadSeen;
    private boolean worldUnloadSeen;
    private boolean dimensionSwitchSeen;
    private boolean debugPipelineClearSeen;
    private boolean presetOffSeen;
    private boolean presetClearSeen;
    private String lastLifecycleEvent = "initialized";
    private String staleReason = "none";
    private ForgeFormalModelFactoryLifecycleState lifecycleState = ForgeFormalModelFactoryLifecycleState.UNINITIALIZED;
    private ForgeFormalModelFactoryAuditResult lastAudit = ForgeFormalModelFactoryAuditResult.failure("none", 0.0D);

    ForgeFormalModelFactory(ForgeVoxyInstance instance) {
        this.instance = instance;
    }

    ForgeFormalModelFactoryStats initialize(String reason) {
        this.enabled = true;
        this.stale = false;
        this.requiresRebuild = false;
        this.lifecycleState = ForgeFormalModelFactoryLifecycleState.INITIALIZED;
        this.lastLifecycleEvent = safeReason(reason);
        this.staleReason = "none";
        this.lastFailureReason = "none";
        return this.createStatusSnapshot();
    }

    ForgeFormalModelFactoryStats requestCurrent() {
        this.initialize("request-current");
        this.requestRuns++;
        Optional<ForgeFormalModelBakeRequest> request = this.createCurrentBlockRequest();
        if (request.isEmpty()) {
            this.recordFailure("no-safe-current-blockstate");
            return this.createStatusSnapshot();
        }
        this.enqueueRequest(request.get());
        return this.createStatusSnapshot();
    }

    ForgeFormalModelFactoryStats requestBlockState(int blockStateId) {
        this.initialize("request-blockstate");
        this.requestRuns++;
        if (blockStateId <= 0) {
            this.recordFailure("invalid-blockStateId-" + blockStateId);
            return this.createStatusSnapshot();
        }
        String blockState = this.blockStateString(blockStateId);
        this.enqueueRequest(new ForgeFormalModelBakeRequest(
                blockStateId,
                this.instance.getCurrentEngineOptional().isPresent() ? "manual-voxy-mapper-or-registry" : "manual-minecraft-registry",
                blockState,
                "manual-request"
        ));
        return this.createStatusSnapshot();
    }

    ForgeFormalModelFactoryStats processSkeleton() {
        this.initialize("process-skeleton");
        this.processRuns++;
        long start = System.nanoTime();
        this.lastProcessError = "none";
        try {
            if (this.pendingRequests.isEmpty()) {
                this.lastFailureReason = this.idMappings.isEmpty() ? "no-pending-requests" : "none";
                this.lastProcessDurationMs = elapsedMs(start);
                return this.createStatusSnapshot();
            }
            while (!this.pendingRequests.isEmpty()) {
                ForgeFormalModelBakeRequest request = this.pendingRequests.removeFirst();
                if (this.idMappings.containsKey(request.blockStateId())) {
                    continue;
                }
                if (this.nextFormalModelId > MAX_FORMAL_MODEL_ID) {
                    this.recordFailure("formal-model-id-overflow");
                    break;
                }
                this.inFlightBlockStateIds.add(request.blockStateId());
                int formalModelId = this.nextFormalModelId++;
                String metadataEntry = "placeholder-metadata:blockStateId=" + request.blockStateId();
                int fluidPlaceholder = 0;
                String textureKey = "placeholder-texture:blockStateId=" + request.blockStateId();
                ForgeFormalModelIdMapping mapping = new ForgeFormalModelIdMapping(
                        request.blockStateId(),
                        formalModelId,
                        request.blockState(),
                        metadataEntry,
                        fluidPlaceholder,
                        textureKey
                );
                this.idMappings.put(request.blockStateId(), mapping);
                this.metadataCache.put(request.blockStateId(), metadataEntry);
                this.fluidStateLut.put(request.blockStateId(), fluidPlaceholder);
                this.modelTexture2id.put(textureKey, formalModelId);
                this.lastAssignedFormalModelId = formalModelId;
                this.inFlightBlockStateIds.remove(request.blockStateId());
            }
        } catch (RuntimeException e) {
            this.recordFailure(e.getClass().getSimpleName() + ": " + e.getMessage());
            this.lastProcessError = this.lastFailureReason;
        }
        this.lastProcessDurationMs = elapsedMs(start);
        return this.createStatusSnapshot();
    }

    ForgeFormalModelFactoryAuditResult audit() {
        this.auditRuns++;
        long start = System.nanoTime();
        ForgeFormalModelFactoryAuditResult result;
        try {
            result = this.auditInternal(start);
        } catch (RuntimeException e) {
            result = ForgeFormalModelFactoryAuditResult.failure(e.getClass().getSimpleName() + ": " + e.getMessage(), elapsedMs(start));
        }
        this.lastAudit = result;
        if (!result.success()) {
            this.auditFailures++;
        }
        return result;
    }

    ForgeFormalModelFactoryAuditResult createAuditStatusSnapshot() {
        return this.lastAudit;
    }

    ForgeFormalModelFactoryStats createStatusSnapshot() {
        ForgeFormalModelStoreStats store = this.instance.getFormalModelStore().createStatusSnapshot();
        boolean formalModelIdsAssigned = !this.idMappings.isEmpty();
        boolean lifecycleReady = this.enabled && !this.stale;
        return new ForgeFormalModelFactoryStats(
                STAGE,
                this.requestRuns,
                this.processRuns,
                this.clearRuns,
                this.auditRuns,
                this.auditFailures,
                this.lastProcessError,
                this.lastProcessDurationMs,
                lifecycleReady,
                lifecycleReady,
                false,
                true,
                true,
                true,
                this.enabled,
                this.lifecycleState.name(),
                this.requestedCount,
                this.seenBlockStateIds.size(),
                this.pendingRequests.size(),
                this.inFlightBlockStateIds.size(),
                this.idMappings.size(),
                this.failedCount,
                formalModelIdsAssigned,
                false,
                this.nextFormalModelId,
                this.idMappings.size(),
                this.metadataCache.size(),
                this.fluidStateLut.size(),
                this.modelTexture2id.size(),
                store.formalModelStoreOwnerReady(),
                false,
                false,
                false,
                false,
                formalModelIdsAssigned,
                false,
                true,
                this.stale,
                this.requiresRebuild,
                this.lastRequestBlockStateId,
                this.lastAssignedFormalModelId,
                this.lastFailureReason,
                this.resourceReloadSeen,
                this.worldUnloadSeen,
                this.dimensionSwitchSeen,
                this.debugPipelineClearSeen,
                this.presetOffSeen,
                this.presetClearSeen,
                this.lastLifecycleEvent,
                this.staleReason,
                this.lastAudit.success(),
                this.lastAudit.error(),
                this.lastAudit.durationMs(),
                this.lastAudit.duplicateMappings(),
                this.lastAudit.invalidFormalModelIds(),
                this.lastAudit.unexpectedRealBake(),
                this.lastAudit.unexpectedUpload(),
                this.lastAudit.sampleSetMisuse()
        );
    }

    String dumpMappings() {
        if (this.idMappings.isEmpty()) {
            return "Voxy formal ModelFactory mappings: none stage=" + STAGE + " noBake=true noUpload=true noDraw=true formalModelFactoryReady=false";
        }
        String mappings = this.idMappings.values().stream()
                .limit(16)
                .map(mapping -> String.format(
                        "blockStateId=%d formalModelId=%d blockState=\"%s\" metadataCache=\"%s\" fluidStateLut=%d modelTextureKey=\"%s\" realBake=false upload=false",
                        mapping.blockStateId(),
                        mapping.formalModelId(),
                        mapping.blockState(),
                        mapping.metadataCacheEntry(),
                        mapping.fluidStateLutValue(),
                        mapping.modelTextureKey()))
                .collect(Collectors.joining(";"));
        return String.format(
                "Voxy formal ModelFactory mappings: stage=%s count=%d nextFormalModelId=%d sampleSetModelIdsUsed=false noBake=true noUpload=true noDraw=true formalModelFactoryReady=false mappings=[%s]",
                STAGE,
                this.idMappings.size(),
                this.nextFormalModelId,
                mappings
        );
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

    void clear() {
        this.clearRuns++;
        this.clearMappings();
        this.enabled = false;
        this.stale = false;
        this.requiresRebuild = false;
        this.lifecycleState = ForgeFormalModelFactoryLifecycleState.CLEARED;
        this.lastLifecycleEvent = "clear";
        this.staleReason = "none";
        this.lastFailureReason = "none";
        this.lastProcessError = "none";
        this.lastProcessDurationMs = 0.0D;
        this.lastRequestBlockStateId = -1;
        this.lastAssignedFormalModelId = -1;
        this.failedCount = 0;
        this.requestedCount = 0;
        this.nextFormalModelId = MIN_FORMAL_MODEL_ID;
        this.auditRuns = 0L;
        this.auditFailures = 0L;
        this.lastAudit = ForgeFormalModelFactoryAuditResult.failure("none", 0.0D);
        this.resourceReloadSeen = false;
        this.worldUnloadSeen = false;
        this.dimensionSwitchSeen = false;
        this.debugPipelineClearSeen = false;
        this.presetOffSeen = false;
        this.presetClearSeen = false;
    }

    private void enqueueRequest(ForgeFormalModelBakeRequest request) {
        this.requestedCount++;
        this.lastRequestBlockStateId = request.blockStateId();
        this.lastFailureReason = "none";
        if (this.seenBlockStateIds.add(request.blockStateId())
                && !this.idMappings.containsKey(request.blockStateId())) {
            this.pendingRequests.addLast(request);
        }
    }

    private Optional<ForgeFormalModelBakeRequest> createCurrentBlockRequest() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            return Optional.empty();
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
        if (sampledState.isAir()) {
            return Optional.empty();
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
        if (blockStateId <= 0) {
            return Optional.empty();
        }

        String reason = "current-world-pos=" + sampledPos.getX() + "," + sampledPos.getY() + "," + sampledPos.getZ();
        return Optional.of(new ForgeFormalModelBakeRequest(
                blockStateId,
                blockStateIdSource,
                sampledState.toString(),
                reason
        ));
    }

    private ForgeFormalModelFactoryAuditResult auditInternal(long startNanos) {
        int duplicateMappings = 0;
        int invalidFormalModelIds = 0;

        Set<Integer> formalIds = new HashSet<>();
        for (ForgeFormalModelIdMapping mapping : this.idMappings.values()) {
            if (!formalIds.add(mapping.formalModelId())) {
                duplicateMappings++;
            }
            if (mapping.formalModelId() < MIN_FORMAL_MODEL_ID || mapping.formalModelId() > MAX_FORMAL_MODEL_ID) {
                invalidFormalModelIds++;
            }
            if (!this.metadataCache.containsKey(mapping.blockStateId())
                    || !this.fluidStateLut.containsKey(mapping.blockStateId())
                    || !this.modelTexture2id.containsKey(mapping.modelTextureKey())) {
                duplicateMappings++;
            }
        }

        Set<Integer> lifecycleIds = new HashSet<>();
        for (ForgeFormalModelBakeRequest request : this.pendingRequests) {
            if (!lifecycleIds.add(request.blockStateId()) || this.idMappings.containsKey(request.blockStateId())) {
                duplicateMappings++;
            }
        }
        for (Integer blockStateId : this.inFlightBlockStateIds) {
            if (!lifecycleIds.add(blockStateId) || this.idMappings.containsKey(blockStateId)) {
                duplicateMappings++;
            }
        }

        boolean unexpectedRealBake = false;
        boolean unexpectedUpload = false;
        boolean sampleSetMisuse = false;
        boolean success = duplicateMappings == 0
                && invalidFormalModelIds == 0
                && !unexpectedRealBake
                && !unexpectedUpload
                && !sampleSetMisuse
                && !this.stale;
        return new ForgeFormalModelFactoryAuditResult(
                success,
                success ? "none" : "formal-model-factory-skeleton-audit-failed",
                elapsedMs(startNanos),
                duplicateMappings,
                invalidFormalModelIds,
                unexpectedRealBake,
                unexpectedUpload,
                sampleSetMisuse
        );
    }

    private void markLifecycleStale(String event) {
        this.enabled = false;
        this.stale = true;
        this.requiresRebuild = true;
        this.lifecycleState = ForgeFormalModelFactoryLifecycleState.STALE;
        this.lastLifecycleEvent = safeReason(event);
        this.staleReason = this.lastLifecycleEvent;
        this.clearMappings();
        this.lastProcessError = this.lastLifecycleEvent;
    }

    private void clearMappings() {
        this.seenBlockStateIds.clear();
        this.pendingRequests.clear();
        this.inFlightBlockStateIds.clear();
        this.idMappings.clear();
        this.metadataCache.clear();
        this.fluidStateLut.clear();
        this.modelTexture2id.clear();
        this.nextFormalModelId = MIN_FORMAL_MODEL_ID;
    }

    private void recordFailure(String reason) {
        this.failedCount++;
        this.lastFailureReason = safeReason(reason);
    }

    private String blockStateString(int blockStateId) {
        Optional<WorldEngine> engine = this.instance.getCurrentEngineOptional();
        if (engine.isPresent()) {
            try {
                return engine.get().getMapper().getBlockStateFromBlockId(blockStateId).toString();
            } catch (RuntimeException ignored) {
                // Fall through to vanilla registry fallback.
            }
        }
        try {
            BlockState state = Block.BLOCK_STATE_REGISTRY.byId(blockStateId);
            return state == null ? "unavailable" : state.toString();
        } catch (RuntimeException ignored) {
            return "unavailable";
        }
    }

    private static String safeReason(String reason) {
        return reason == null || reason.isBlank() ? "unspecified" : reason.replace(' ', '-');
    }

    private static double elapsedMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000.0D;
    }
}
