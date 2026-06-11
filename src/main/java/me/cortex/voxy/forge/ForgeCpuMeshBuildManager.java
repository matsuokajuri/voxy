package me.cortex.voxy.forge;

import me.cortex.voxy.config.ForgeVoxyConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;

import java.util.ArrayDeque;
import java.util.HashSet;

public final class ForgeCpuMeshBuildManager {
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
    private long windowVertices;
    private long windowBytes;
    private double windowElapsedMs;
    private long lastSummaryTick;
    private double lastBuildMs;

    ForgeCpuMeshBuildManager(ForgeVoxyInstance instance) {
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
    }

    public StatusSnapshot createStatusSnapshot() {
        return new StatusSnapshot(
                this.instance.getCurrentEngineOptional().isPresent(),
                ForgeVoxyConfig.ENABLE_AUTO_CPU_MESH_BUILD.get(),
                this.activeDimension,
                this.pendingChunks.size(),
                this.builtChunks.size(),
                this.failedChunks.size(),
                getConfiguredRadius(),
                getConfiguredMaxChunksPerTick(),
                getConfiguredCooldownTicks(),
                this.lastBuildMs
        );
    }

    private void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        this.tickCounter++;

        if (!ForgeVoxyConfig.ENABLE_AUTO_CPU_MESH_BUILD.get()) {
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
            this.instance.getCpuMeshCache().setActiveDimension(dimension);
        }

        if (this.scanCooldown <= 0) {
            this.enqueueNearbyLoadedChunks(level, player.chunkPosition().x, player.chunkPosition().z, getConfiguredRadius());
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
        if (this.builtChunks.contains(key) || this.failedChunks.contains(key) || this.queuedChunks.contains(key)) {
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
        try {
            var result = ForgeCpuMeshBuilder.buildCurrentChunk(engine.get(), chunk, level, dimension);
            var stats = result.stats();
            if (stats.sectionsFound() == 0) {
                return;
            }

            if (!dimension.equals(this.activeDimension)) {
                for (ForgeCpuBuiltSection section : result.sections()) {
                    section.close();
                }
                return;
            }

            var cache = this.instance.getCpuMeshCache();
            cache.setActiveDimension(dimension);
            int cacheEntriesWritten = cache.putAll(result.sections());
            stats = stats.withCacheEntriesWritten(cacheEntriesWritten);

            this.builtChunks.add(key);
            this.lastBuildMs = stats.elapsedMs();
            this.windowChunks++;
            this.windowCacheEntries += stats.cacheEntriesWritten();
            this.windowVertices += stats.vertices();
            this.windowBytes += stats.estimatedBytes();
            this.windowElapsedMs += stats.elapsedMs();
        } catch (OutOfMemoryError e) {
            this.failedChunks.add(key);
            VoxyForge.LOGGER.error("Voxy auto CPU mesh build ran out of memory for {} chunk {},{}", dimension, chunkX, chunkZ, e);
        } catch (Exception e) {
            this.failedChunks.add(key);
            VoxyForge.LOGGER.error("Voxy auto CPU mesh build failed for {} chunk {},{}", dimension, chunkX, chunkZ, e);
        }
    }

    private void maybeLogSummary(String dimension) {
        if (this.windowChunks == 0 || this.tickCounter - this.lastSummaryTick < SUMMARY_INTERVAL_TICKS) {
            return;
        }

        double averageMs = this.windowElapsedMs / this.windowChunks;
        VoxyForge.LOGGER.info(
                "Voxy auto CPU mesh build {}: queued={} built={} failed={} cacheEntries={} vertices={} bytes={} avgMs={}",
                dimension,
                this.pendingChunks.size(),
                this.windowChunks,
                this.failedChunks.size(),
                this.windowCacheEntries,
                this.windowVertices,
                this.windowBytes,
                String.format("%.2f", averageMs)
        );
        this.resetWindow();
        this.lastSummaryTick = this.tickCounter;
    }

    private void resetWindow() {
        this.windowChunks = 0;
        this.windowCacheEntries = 0;
        this.windowVertices = 0;
        this.windowBytes = 0;
        this.windowElapsedMs = 0.0;
        this.lastSummaryTick = this.tickCounter;
    }

    private static LevelChunk getLoadedChunk(ClientLevel level, int chunkX, int chunkZ) {
        if (!level.hasChunk(chunkX, chunkZ)) {
            return null;
        }
        return level.getChunkSource().getChunk(chunkX, chunkZ, ChunkStatus.FULL, false);
    }

    private static int getConfiguredRadius() {
        return Math.min(8, Math.max(0, ForgeVoxyConfig.AUTO_MESH_BUILD_RADIUS.get()));
    }

    private static int getConfiguredMaxChunksPerTick() {
        return Math.min(8, Math.max(1, ForgeVoxyConfig.AUTO_MESH_BUILD_MAX_CHUNKS_PER_TICK.get()));
    }

    private static int getConfiguredCooldownTicks() {
        return Math.min(200, Math.max(0, ForgeVoxyConfig.AUTO_MESH_BUILD_COOLDOWN_TICKS.get()));
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
            double lastBuildMs
    ) {
    }
}
