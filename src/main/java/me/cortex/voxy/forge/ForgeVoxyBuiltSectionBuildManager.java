package me.cortex.voxy.forge;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;

import java.util.ArrayDeque;
import java.util.HashSet;

public final class ForgeVoxyBuiltSectionBuildManager {
    private static final int SUMMARY_INTERVAL_TICKS = 100;

    private final ForgeVoxyInstance instance;
    private final ArrayDeque<Long> pendingChunks = new ArrayDeque<>();
    private final HashSet<Long> queuedChunks = new HashSet<>();
    private final HashSet<Long> builtChunks = new HashSet<>();
    private final HashSet<Long> failedChunks = new HashSet<>();

    private String activeDimension;
    private long tickCounter;
    private int scanCooldown;
    private int windowChunks;
    private int windowCacheEntries;
    private long windowNaiveQuads;
    private long windowQuadsAfterMerge;
    private long windowGeometryBytes;
    private double windowElapsedMs;
    private long lastSummaryTick;
    private double lastBuildMs;
    private double lastAverageMs;

    ForgeVoxyBuiltSectionBuildManager(ForgeVoxyInstance instance) {
        this.instance = instance;
    }

    public void register() {
        MinecraftForge.EVENT_BUS.addListener(this::onClientTick);
    }

    public void clear() {
        this.pendingChunks.clear();
        this.queuedChunks.clear();
        this.builtChunks.clear();
        this.failedChunks.clear();
        this.resetWindow();
        this.activeDimension = null;
        this.scanCooldown = 0;
        this.lastBuildMs = 0.0;
        this.lastAverageMs = 0.0;
    }

    public StatusSnapshot createStatusSnapshot() {
        return new StatusSnapshot(
                this.instance.getCurrentEngineOptional().isPresent(),
                isAutoEnabled(),
                this.activeDimension,
                this.pendingChunks.size(),
                this.builtChunks.size(),
                this.failedChunks.size(),
                getConfiguredRadius(),
                getConfiguredMaxChunksPerTick(),
                getConfiguredCooldownTicks(),
                this.lastBuildMs,
                this.getAverageMs()
        );
    }

    private void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        this.tickCounter++;

        if (!isAutoEnabled()) {
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
            this.instance.getVoxyGeometryCache().setActiveDimension(dimension);
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
        if (this.builtChunks.contains(key)) {
            if (this.activeDimension != null && this.instance.getVoxyGeometryCache().hasChunkEntries(this.activeDimension, chunkX, chunkZ)) {
                return;
            }
            this.builtChunks.remove(key);
        }
        if (this.failedChunks.contains(key) || this.queuedChunks.contains(key)) {
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
            if (this.builtChunks.contains(key) || this.failedChunks.contains(key)) {
                continue;
            }

            int chunkX = ChunkPos.getX(key);
            int chunkZ = ChunkPos.getZ(key);
            LevelChunk chunk = getLoadedChunk(level, chunkX, chunkZ);
            if (chunk == null) {
                continue;
            }

            this.buildLoadedChunk(level, dimension, chunkX, chunkZ, chunk);
        }
    }

    private void buildLoadedChunk(ClientLevel level, String dimension, int chunkX, int chunkZ, LevelChunk chunk) {
        var engine = this.instance.getCurrentEngineOptional();
        if (engine.isEmpty()) {
            return;
        }

        long key = ChunkPos.asLong(chunkX, chunkZ);
        ForgeCpuMeshBuildResult cpuResult = null;
        try {
            long start = System.nanoTime();
            cpuResult = ForgeCpuMeshBuilder.buildCurrentChunk(engine.get(), chunk, level, dimension);
            var cpuStats = cpuResult.stats();
            if (cpuStats.sectionsFound() == 0 || cpuResult.sections().isEmpty()) {
                return;
            }

            var builtResult = ForgeVoxyBuiltSectionBuilder.fromCpuMesh(cpuResult);
            if (builtResult.sections().isEmpty()) {
                return;
            }

            if (!dimension.equals(this.activeDimension)) {
                closeBuiltSections(builtResult);
                return;
            }

            var cache = this.instance.getVoxyGeometryCache();
            cache.setActiveDimension(dimension);
            int cacheEntriesWritten = cache.putAll(builtResult.sections());
            double elapsedMs = (System.nanoTime() - start) / 1_000_000.0;
            var stats = builtResult.stats()
                    .withCacheEntriesWritten(cacheEntriesWritten)
                    .withElapsedMs(elapsedMs);

            this.builtChunks.add(key);
            this.lastBuildMs = stats.elapsedMs();
            this.windowChunks++;
            this.windowCacheEntries += stats.cacheEntriesWritten();
            this.windowNaiveQuads += stats.naiveQuads();
            this.windowQuadsAfterMerge += stats.quadsAfterMerge();
            this.windowGeometryBytes += stats.geometryBytes();
            this.windowElapsedMs += stats.elapsedMs();
        } catch (OutOfMemoryError e) {
            this.failedChunks.add(key);
            VoxyForge.LOGGER.error("Voxy auto BuiltSection build ran out of memory for {} chunk {},{}", dimension, chunkX, chunkZ, e);
        } catch (Exception e) {
            this.failedChunks.add(key);
            VoxyForge.LOGGER.error("Voxy auto BuiltSection build failed for {} chunk {},{}", dimension, chunkX, chunkZ, e);
        } finally {
            closeTemporaryCpuMesh(cpuResult);
        }
    }

    private void maybeLogSummary(String dimension) {
        if (this.windowChunks == 0 || this.tickCounter - this.lastSummaryTick < SUMMARY_INTERVAL_TICKS) {
            return;
        }

        double averageMs = this.windowElapsedMs / this.windowChunks;
        double averageArea = this.windowQuadsAfterMerge == 0 ? 0.0 : (double) this.windowNaiveQuads / this.windowQuadsAfterMerge;
        VoxyForge.LOGGER.info(
                "Voxy auto BuiltSection build {}: queued={} built={} failed={} cacheEntries={} naiveQuads={} quadsAfterMerge={} avgArea={} bytes={} avgMs={}",
                dimension,
                this.pendingChunks.size(),
                this.windowChunks,
                this.failedChunks.size(),
                this.windowCacheEntries,
                this.windowNaiveQuads,
                this.windowQuadsAfterMerge,
                String.format("%.2f", averageArea),
                this.windowGeometryBytes,
                String.format("%.2f", averageMs)
        );
        this.resetWindow();
        this.lastSummaryTick = this.tickCounter;
        this.lastAverageMs = averageMs;
    }

    private void resetWindow() {
        this.windowChunks = 0;
        this.windowCacheEntries = 0;
        this.windowNaiveQuads = 0;
        this.windowQuadsAfterMerge = 0;
        this.windowGeometryBytes = 0;
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
        this.builtChunks.removeIf(key -> !isWithinChunkRadius(key, centerX, centerZ, retentionRadius));
        this.failedChunks.removeIf(key -> !isWithinChunkRadius(key, centerX, centerZ, retentionRadius));
    }

    private static LevelChunk getLoadedChunk(ClientLevel level, int chunkX, int chunkZ) {
        if (!level.hasChunk(chunkX, chunkZ)) {
            return null;
        }
        return level.getChunkSource().getChunk(chunkX, chunkZ, ChunkStatus.FULL, false);
    }

    private static boolean isAutoEnabled() {
        return ForgeVoxyRuntimeOverrides.enableAutoBuiltSectionBuild();
    }

    private static int getConfiguredRadius() {
        return Math.min(8, Math.max(0, me.cortex.voxy.config.ForgeVoxyConfig.AUTO_BUILT_SECTION_BUILD_RADIUS.get()));
    }

    private static int getConfiguredMaxChunksPerTick() {
        return Math.min(8, Math.max(1, me.cortex.voxy.config.ForgeVoxyConfig.AUTO_BUILT_SECTION_MAX_CHUNKS_PER_TICK.get()));
    }

    private static int getConfiguredCooldownTicks() {
        return Math.min(200, Math.max(0, me.cortex.voxy.config.ForgeVoxyConfig.AUTO_BUILT_SECTION_COOLDOWN_TICKS.get()));
    }

    private static int getRecordRetentionRadius(int activeRadius) {
        return Math.max(8, activeRadius + 8);
    }

    private static boolean isWithinChunkRadius(long key, int centerX, int centerZ, int radius) {
        return Math.abs(ChunkPos.getX(key) - centerX) <= radius && Math.abs(ChunkPos.getZ(key) - centerZ) <= radius;
    }

    private static void closeTemporaryCpuMesh(ForgeCpuMeshBuildResult cpuResult) {
        if (cpuResult == null) {
            return;
        }
        for (ForgeCpuBuiltSection section : cpuResult.sections()) {
            if (section != null) {
                section.close();
            }
        }
    }

    private static void closeBuiltSections(ForgeVoxyBuiltSectionBuildResult builtResult) {
        if (builtResult == null) {
            return;
        }
        for (ForgeVoxyBuiltSection section : builtResult.sections()) {
            if (section != null) {
                section.close();
            }
        }
    }

    public record StatusSnapshot(
            boolean enginePresent,
            boolean autoEnabled,
            String dimension,
            int queuedChunks,
            int builtChunks,
            int failedChunks,
            int radius,
            int maxChunksPerTick,
            int cooldownTicks,
            double lastBuildMs,
            double averageMs
    ) {
    }
}
