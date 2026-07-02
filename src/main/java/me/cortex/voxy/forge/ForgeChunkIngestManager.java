package me.cortex.voxy.forge;

import me.cortex.voxy.common.world.service.VoxelIngestService;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;

import java.util.ArrayDeque;
import java.util.HashSet;

public final class ForgeChunkIngestManager {
    private static final int SUMMARY_INTERVAL_TICKS = 100;
    //Per-tick wall-time budget for catching up the ingest backlog during fast movement. Since ingest
    //conversion runs on the async "Ingest service" worker, the main thread only captures light data
    //and enqueues; this budget bounds that capture cost per tick.
    private static final long INGEST_CATCHUP_BUDGET_NANOS = 4_000_000L;

    private final ForgeVoxyInstance instance;
    private final ArrayDeque<Long> pendingChunks = new ArrayDeque<>();
    private final HashSet<Long> queuedChunks = new HashSet<>();
    private final HashSet<Long> ingestedChunks = new HashSet<>();

    private String activeDimension;
    private long tickCounter;
    private int scanCooldown;
    private int lastScanChunkX = Integer.MIN_VALUE;
    private int lastScanChunkZ = Integer.MIN_VALUE;
    private int windowChunks;
    private int windowConvertedSections;
    private int windowNonAirSections;
    private int windowStorageWritesBase;
    private int windowMissingBlockLightSections;
    private int windowMissingSkyLightSections;
    private int windowDeferredLightSections;
    private double windowElapsedMs;
    private long lastSummaryTick;
    private double lastAverageMs;
    private long mixinChunkIngestAttempts;
    private long mixinChunkIngestUpdates;
    private long mixinSectionIngestAttempts;
    private long mixinSectionIngestUpdates;

    ForgeChunkIngestManager(ForgeVoxyInstance instance) {
        this.instance = instance;
    }

    public void register() {
        MinecraftForge.EVENT_BUS.addListener(this::onClientTick);
    }

    public void clear() {
        this.pendingChunks.clear();
        this.queuedChunks.clear();
        this.ingestedChunks.clear();
        this.resetWindow();
        this.activeDimension = null;
        this.scanCooldown = 0;
        this.lastScanChunkX = Integer.MIN_VALUE;
        this.lastScanChunkZ = Integer.MIN_VALUE;
        this.lastAverageMs = 0.0;
        this.mixinChunkIngestAttempts = 0L;
        this.mixinChunkIngestUpdates = 0L;
        this.mixinSectionIngestAttempts = 0L;
        this.mixinSectionIngestUpdates = 0L;
    }

    public StatusSnapshot createStatusSnapshot() {
        return new StatusSnapshot(
                this.instance.getCurrentEngineOptional().isPresent(),
                ForgeVoxyRuntimeOverrides.enableAutoChunkIngest(),
                this.activeDimension,
                this.pendingChunks.size(),
                this.ingestedChunks.size(),
                this.mixinChunkIngestAttempts,
                this.mixinChunkIngestUpdates,
                this.mixinSectionIngestAttempts,
                this.mixinSectionIngestUpdates,
                getConfiguredRadius(),
                getConfiguredMaxChunksPerTick(),
                getConfiguredCooldownTicks(),
                this.getAverageMs()
        );
    }

    public void recordMixinChunkIngest(boolean updated) {
        this.mixinChunkIngestAttempts++;
        if (updated) {
            this.mixinChunkIngestUpdates++;
        }
    }

    public void recordMixinSectionIngest(boolean updated) {
        this.mixinSectionIngestAttempts++;
        if (updated) {
            this.mixinSectionIngestUpdates++;
        }
    }

    private void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        this.tickCounter++;

        if (!ForgeVoxyRuntimeOverrides.enableAutoChunkIngest()) {
            if (!this.pendingChunks.isEmpty() || !this.queuedChunks.isEmpty()) {
                this.pendingChunks.clear();
                this.queuedChunks.clear();
            }
            return;
        }

        var engine = this.instance.getCurrentEngineOptional();
        if (engine.isEmpty()) {
            this.clear();
            return;
        }

        var minecraft = Minecraft.getInstance();
        var level = minecraft.level;
        var player = minecraft.player;
        if (level == null || player == null) {
            this.clear();
            return;
        }

        String dimension = level.dimension().location().toString();
        if (!dimension.equals(this.activeDimension)) {
            this.clear();
            this.activeDimension = dimension;
        }

        int centerChunkX = player.chunkPosition().x;
        int centerChunkZ = player.chunkPosition().z;
        //Rescan immediately when the player crosses into a new chunk so newly loaded chunks ahead of
        //fast movement are discovered without waiting out the cooldown, instead of leaving holes.
        boolean movedChunk = centerChunkX != this.lastScanChunkX || centerChunkZ != this.lastScanChunkZ;
        if (this.scanCooldown <= 0 || movedChunk) {
            int radius = getConfiguredRadius();
            this.pruneRecordsAround(centerChunkX, centerChunkZ, radius);
            this.enqueueNearbyLoadedChunks(level, centerChunkX, centerChunkZ, radius);
            this.lastScanChunkX = centerChunkX;
            this.lastScanChunkZ = centerChunkZ;
            this.scanCooldown = getConfiguredCooldownTicks();
        } else {
            this.scanCooldown--;
        }

        this.processQueue(level, dimension);
        this.maybeLogSummary(dimension);
    }

    private void enqueueNearbyLoadedChunks(ClientLevel level, int centerX, int centerZ, int radius) {
        this.tryQueueLoadedChunk(level, centerX, centerZ);
        for (int distance = 1; distance <= radius; distance++) {
            for (int dx = -distance; dx <= distance; dx++) {
                this.tryQueueLoadedChunk(level, centerX + dx, centerZ - distance);
                this.tryQueueLoadedChunk(level, centerX + dx, centerZ + distance);
            }
            for (int dz = -distance + 1; dz <= distance - 1; dz++) {
                this.tryQueueLoadedChunk(level, centerX - distance, centerZ + dz);
                this.tryQueueLoadedChunk(level, centerX + distance, centerZ + dz);
            }
        }
    }

    private void tryQueueLoadedChunk(ClientLevel level, int chunkX, int chunkZ) {
        long key = ChunkPos.asLong(chunkX, chunkZ);
        if (this.ingestedChunks.contains(key) || this.queuedChunks.contains(key)) {
            return;
        }
        if (getLoadedChunk(level, chunkX, chunkZ) == null) {
            return;
        }
        this.pendingChunks.addLast(key);
        this.queuedChunks.add(key);
    }

    private void processQueue(ClientLevel level, String dimension) {
        int minChunks = getConfiguredMaxChunksPerTick();
        long deadline = System.nanoTime() + INGEST_CATCHUP_BUDGET_NANOS;
        int ingested = 0;
        while (!this.pendingChunks.isEmpty() && (ingested < minChunks || System.nanoTime() < deadline)) {
            Long queuedKey = this.pendingChunks.pollFirst();
            if (queuedKey == null) {
                break;
            }
            long key = queuedKey;
            this.queuedChunks.remove(key);
            if (this.ingestedChunks.contains(key)) {
                continue;
            }

            int chunkX = ChunkPos.getX(key);
            int chunkZ = ChunkPos.getZ(key);
            LevelChunk chunk = getLoadedChunk(level, chunkX, chunkZ);
            if (chunk == null) {
                continue;
            }

            this.ingestLoadedChunk(dimension, chunkX, chunkZ, chunk);
            ingested++;
        }
    }

    private void ingestLoadedChunk(String dimension, int chunkX, int chunkZ, LevelChunk chunk) {
        var engine = this.instance.getCurrentEngineOptional();
        if (engine.isEmpty()) {
            return;
        }

        long key = ChunkPos.asLong(chunkX, chunkZ);
        try {
            long start = System.nanoTime();
            var stats = VoxelIngestService.ingestChunkWithStats(engine.get(), chunk);
            double elapsedMs = (System.nanoTime() - start) / 1_000_000.0;

            if (!stats.deferred()) {
                this.ingestedChunks.add(key);
            }
            this.windowChunks++;
            this.windowConvertedSections += stats.convertedSections();
            this.windowNonAirSections += stats.nonAirSections();
            this.windowMissingBlockLightSections += stats.missingBlockLightSections();
            this.windowMissingSkyLightSections += stats.missingSkyLightSections();
            this.windowDeferredLightSections += stats.deferredLightSections();
            this.windowElapsedMs += elapsedMs;
        } catch (Exception e) {
            this.ingestedChunks.add(key);
            VoxyForge.LOGGER.error("Voxy auto ingest failed for {} chunk {},{}", dimension, chunkX, chunkZ, e);
        }
    }

    private void maybeLogSummary(String dimension) {
        if (this.windowChunks == 0 || this.tickCounter - this.lastSummaryTick < SUMMARY_INTERVAL_TICKS) {
            return;
        }

        double averageMs = this.windowElapsedMs / this.windowChunks;
        //Ingest runs on the async worker now, so storage writes are measured as the global
        // counter delta across the summary window instead of around each (now non-blocking) call.
        int storageWriteCount = this.instance.getStorageWriteCount();
        int windowStorageWriteDelta = storageWriteCount - this.windowStorageWritesBase;
        this.windowStorageWritesBase = storageWriteCount;
        VoxyForge.LOGGER.info(
                "Voxy auto ingest {}: queued={} attempted={} enqueuedSections={} nonAirSections={} workerBacklog={} storageWrites={} missingBlockLightSections={} missingSkyLightSections={} deferredLightSections={} avgCaptureMs={}",
                dimension,
                this.pendingChunks.size(),
                this.windowChunks,
                this.windowConvertedSections,
                this.windowNonAirSections,
                this.instance.getIngestService().getTaskCount(),
                windowStorageWriteDelta,
                this.windowMissingBlockLightSections,
                this.windowMissingSkyLightSections,
                this.windowDeferredLightSections,
                String.format("%.2f", averageMs)
        );
        this.resetWindow();
        this.lastSummaryTick = this.tickCounter;
        this.lastAverageMs = averageMs;
    }

    private void resetWindow() {
        this.windowChunks = 0;
        this.windowConvertedSections = 0;
        this.windowNonAirSections = 0;
        this.windowStorageWritesBase = this.instance.getStorageWriteCount();
        this.windowMissingBlockLightSections = 0;
        this.windowMissingSkyLightSections = 0;
        this.windowDeferredLightSections = 0;
        this.windowElapsedMs = 0.0;
        this.lastSummaryTick = this.tickCounter;
    }

    private double getAverageMs() {
        if (this.windowChunks == 0) {
            return this.lastAverageMs;
        }
        return this.windowElapsedMs / this.windowChunks;
    }

    private void pruneRecordsAround(int centerX, int centerZ, int radius) {
        int retentionRadius = getRecordRetentionRadius(radius);
        this.pendingChunks.removeIf(key -> {
            boolean remove = !isWithinChunkRadius(key, centerX, centerZ, retentionRadius);
            if (remove) {
                this.queuedChunks.remove(key);
            }
            return remove;
        });
        this.queuedChunks.removeIf(key -> !isWithinChunkRadius(key, centerX, centerZ, retentionRadius));
        this.ingestedChunks.removeIf(key -> !isWithinChunkRadius(key, centerX, centerZ, retentionRadius));
    }

    private static LevelChunk getLoadedChunk(ClientLevel level, int chunkX, int chunkZ) {
        if (!level.hasChunk(chunkX, chunkZ)) {
            return null;
        }
        return level.getChunkSource().getChunk(chunkX, chunkZ, ChunkStatus.FULL, false);
    }

    private static int getConfiguredRadius() {
        return Math.min(8, Math.max(0, me.cortex.voxy.config.ForgeVoxyConfig.AUTO_INGEST_RADIUS.get()));
    }

    private static int getConfiguredMaxChunksPerTick() {
        return Math.min(8, Math.max(1, me.cortex.voxy.config.ForgeVoxyConfig.AUTO_INGEST_MAX_CHUNKS_PER_TICK.get()));
    }

    private static int getConfiguredCooldownTicks() {
        return Math.min(200, Math.max(0, me.cortex.voxy.config.ForgeVoxyConfig.AUTO_INGEST_COOLDOWN_TICKS.get()));
    }

    private static int getRecordRetentionRadius(int activeRadius) {
        return Math.max(8, activeRadius + 8);
    }

    private static boolean isWithinChunkRadius(long key, int centerX, int centerZ, int radius) {
        return Math.abs(ChunkPos.getX(key) - centerX) <= radius && Math.abs(ChunkPos.getZ(key) - centerZ) <= radius;
    }

    public record StatusSnapshot(
            boolean enginePresent,
            boolean autoEnabled,
            String dimension,
            int queuedChunks,
            int ingestedChunks,
            long mixinChunkIngestAttempts,
            long mixinChunkIngestUpdates,
            long mixinSectionIngestAttempts,
            long mixinSectionIngestUpdates,
            int radius,
            int maxChunksPerTick,
            int cooldownTicks,
            double averageMs
    ) {
    }
}
