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

    private final ForgeVoxyInstance instance;
    private final ArrayDeque<Long> pendingChunks = new ArrayDeque<>();
    private final HashSet<Long> queuedChunks = new HashSet<>();
    private final HashSet<Long> ingestedChunks = new HashSet<>();

    private String activeDimension;
    private long tickCounter;
    private int scanCooldown;
    private int windowChunks;
    private int windowConvertedSections;
    private int windowNonAirSections;
    private int windowNonAirVoxels;
    private int windowStorageWrites;
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

        if (this.scanCooldown <= 0) {
            int centerChunkX = player.chunkPosition().x;
            int centerChunkZ = player.chunkPosition().z;
            int radius = getConfiguredRadius();
            this.pruneRecordsAround(centerChunkX, centerChunkZ, radius);
            this.enqueueNearbyLoadedChunks(level, centerChunkX, centerChunkZ, radius);
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
        int maxChunks = getConfiguredMaxChunksPerTick();
        for (int processed = 0; processed < maxChunks && !this.pendingChunks.isEmpty(); processed++) {
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
            int storageWritesBefore = this.instance.getStorageWriteCount();
            var stats = VoxelIngestService.ingestChunkWithStats(engine.get(), chunk);
            int storageWrites = this.instance.getStorageWriteCount() - storageWritesBefore;
            stats = stats.withStorageWrites(storageWrites);
            double elapsedMs = (System.nanoTime() - start) / 1_000_000.0;

            this.ingestedChunks.add(key);
            this.windowChunks++;
            this.windowConvertedSections += stats.convertedSections();
            this.windowNonAirSections += stats.nonAirSections();
            this.windowNonAirVoxels += stats.nonAirVoxels();
            this.windowStorageWrites += stats.storageWrites();
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
        VoxyForge.LOGGER.info(
                "Voxy auto ingest {}: queued={} ingested={} converted={} nonAirSections={} nonAirVoxels={} storageWrites={} avgMs={}",
                dimension,
                this.pendingChunks.size(),
                this.windowChunks,
                this.windowConvertedSections,
                this.windowNonAirSections,
                this.windowNonAirVoxels,
                this.windowStorageWrites,
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
        this.windowNonAirVoxels = 0;
        this.windowStorageWrites = 0;
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
