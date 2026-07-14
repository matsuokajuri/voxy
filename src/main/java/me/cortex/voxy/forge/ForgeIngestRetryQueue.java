package me.cortex.voxy.forge;

import me.cortex.voxy.common.world.WorldEngine;
import me.cortex.voxy.common.world.service.VoxelIngestService;
import me.cortex.voxy.config.ForgeVoxyConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;

/**
 * Forge-only retry adapter for the original event-driven ingest route.
 *
 * <p>The original Sodium hooks ingest on chunk add/remove and section updates. Forge 1.20.1 can
 * expose a client chunk before its sky-light storage reaches {@code LIGHT_AND_DATA}; the port
 * deliberately defers that capture rather than baking placeholder darkness. Only those deferred
 * hook results enter this queue. It never scans nearby chunks and therefore cannot become a
 * second ingest owner.</p>
 */
final class ForgeIngestRetryQueue {
    private static final int MAX_RETRIES_PER_TICK = 8;

    private final ForgeVoxyInstance instance;
    private final ArrayDeque<Long> pendingChunks = new ArrayDeque<>();
    private final HashSet<Long> queuedChunks = new HashSet<>();

    ForgeIngestRetryQueue(ForgeVoxyInstance instance) {
        this.instance = instance;
    }

    void register() {
        MinecraftForge.EVENT_BUS.addListener(this::onClientTick);
    }

    void clear() {
        this.pendingChunks.clear();
        this.queuedChunks.clear();
    }

    boolean ingestChunk(LevelChunk chunk) {
        VoxelIngestService.IngestStats stats = VoxelIngestService.tryAutoIngestChunkWithStats(chunk);
        this.recordDeferred(chunk, stats);
        return stats.updated();
    }

    boolean ingestSection(ClientLevel level, LevelChunk chunk, int sectionY) {
        WorldEngine engine = this.instance.getEngineForLevel(level).orElse(null);
        if (engine == null) {
            return false;
        }
        VoxelIngestService.IngestStats stats =
                VoxelIngestService.ingestChunkSectionWithStats(engine, chunk, sectionY);
        this.recordDeferred(chunk, stats);
        return stats.updated();
    }

    private void recordDeferred(LevelChunk chunk, VoxelIngestService.IngestStats stats) {
        long key = chunk.getPos().toLong();
        updateDeferredRetryState(this.pendingChunks, this.queuedChunks, key, stats.deferred());
    }

    static void updateDeferredRetryState(
            Deque<Long> pendingChunks,
            Set<Long> queuedChunks,
            long key,
            boolean deferred) {
        if (!deferred) {
            //A successful single-section ingest does not prove that another deferred section in
            //the same chunk is ready. Keep the chunk retry until the tick loop consumes it.
            return;
        }
        if (queuedChunks.add(key)) {
            pendingChunks.addLast(key);
        }
    }

    static long pollDeferredRetryState(Deque<Long> pendingChunks, Set<Long> queuedChunks) {
        long key = pendingChunks.removeFirst();
        queuedChunks.remove(key);
        return key;
    }

    private void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || this.pendingChunks.isEmpty()) {
            return;
        }
        if (!ForgeVoxyConfig.ENABLED.get() || !ForgeVoxyConfig.INGEST_ENABLED.get()) {
            this.clear();
            return;
        }

        ClientLevel level = Minecraft.getInstance().level;
        if (level == null || this.instance.getEngineForLevel(level).isEmpty()) {
            this.clear();
            return;
        }

        int attempts = Math.min(MAX_RETRIES_PER_TICK, this.pendingChunks.size());
        for (int i = 0; i < attempts; i++) {
            long key = pollDeferredRetryState(this.pendingChunks, this.queuedChunks);
            LevelChunk chunk = getLoadedChunk(level, ChunkPos.getX(key), ChunkPos.getZ(key));
            if (chunk != null) {
                this.ingestChunk(chunk);
            }
        }
    }

    private static LevelChunk getLoadedChunk(ClientLevel level, int chunkX, int chunkZ) {
        if (!level.hasChunk(chunkX, chunkZ)) {
            return null;
        }
        return level.getChunkSource().getChunk(chunkX, chunkZ, ChunkStatus.FULL, false);
    }
}
