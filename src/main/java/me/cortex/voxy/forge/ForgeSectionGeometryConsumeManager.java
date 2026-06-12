package me.cortex.voxy.forge;

import me.cortex.voxy.config.ForgeVoxyConfig;
import net.minecraft.client.Minecraft;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;

public final class ForgeSectionGeometryConsumeManager {
    private static final int SUMMARY_INTERVAL_TICKS = 100;

    private final ForgeVoxyInstance instance;
    private final ArrayDeque<Key> pendingSections = new ArrayDeque<>();
    private final HashSet<Key> queuedSections = new HashSet<>();
    private final HashMap<Key, Long> consumedSections = new HashMap<>();
    private final HashMap<Key, Long> failedSections = new HashMap<>();

    private String activeDimension;
    private int scanCooldown;
    private long tickCounter;
    private long lastSummaryTick;
    private int windowConsumed;
    private int windowReplaced;
    private int windowRemoved;
    private long windowGeometryBytes;
    private double windowElapsedMs;
    private double lastConsumeMs;
    private double lastAverageMs;
    private String lastFailureReason = "none";

    ForgeSectionGeometryConsumeManager(ForgeVoxyInstance instance) {
        this.instance = instance;
    }

    public void register() {
        MinecraftForge.EVENT_BUS.addListener(this::onClientTick);
    }

    public void clear() {
        this.clearRecords();
        this.instance.getSectionGeometryManager().clear();
    }

    public void clearRecords() {
        this.pendingSections.clear();
        this.queuedSections.clear();
        this.consumedSections.clear();
        this.failedSections.clear();
        this.scanCooldown = 0;
        this.activeDimension = null;
        this.resetWindow();
        this.lastConsumeMs = 0.0D;
        this.lastAverageMs = 0.0D;
        this.lastFailureReason = "none";
    }

    public StatusSnapshot createStatusSnapshot() {
        return new StatusSnapshot(
                this.instance.getCurrentEngineOptional().isPresent(),
                isAutoEnabled(),
                this.activeDimension,
                this.pendingSections.size(),
                this.consumedSections.size(),
                this.failedSections.size(),
                getConfiguredRadius(),
                getConfiguredMaxSectionsPerTick(),
                getConfiguredCooldownTicks(),
                this.lastConsumeMs,
                this.getAverageMs(),
                this.lastFailureReason
        );
    }

    private void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        this.tickCounter++;

        if (!isAutoEnabled()) {
            if (!this.pendingSections.isEmpty() || !this.queuedSections.isEmpty()) {
                this.pendingSections.clear();
                this.queuedSections.clear();
            }
            return;
        }

        if (this.instance.getCurrentEngineOptional().isEmpty()) {
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
            this.instance.getSectionGeometryManager().setActiveDimension(dimension);
        }

        if (this.scanCooldown <= 0) {
            int centerChunkX = player.chunkPosition().x;
            int centerChunkZ = player.chunkPosition().z;
            int radius = getConfiguredRadius();
            this.pruneRecords(dimension, centerChunkX, centerChunkZ, radius);
            this.enqueueNearbyBuiltSections(dimension, centerChunkX, centerChunkZ, radius);
            this.scanCooldown = getConfiguredCooldownTicks();
        } else {
            this.scanCooldown--;
        }

        this.processQueue(dimension);
        this.maybeLogSummary(dimension);
    }

    private void enqueueNearbyBuiltSections(String dimension, int centerChunkX, int centerChunkZ, int radius) {
        int maxCandidates = Math.max(getConfiguredMaxSectionsPerTick(), (2 * radius + 1) * (2 * radius + 1) * 32);
        var sections = this.instance.getVoxyGeometryCache().createAreaSnapshot(dimension, centerChunkX, centerChunkZ, radius, maxCandidates);
        for (ForgeVoxyBuiltSection section : sections) {
            Key key = Key.from(section);
            long stamp = sourceStamp(section);
            Long consumedStamp = this.consumedSections.get(key);
            if (consumedStamp != null && consumedStamp == stamp && this.instance.getSectionGeometryManager().hasSection(dimension, key.position())) {
                continue;
            }
            Long failedStamp = this.failedSections.get(key);
            if (failedStamp != null && failedStamp == stamp) {
                continue;
            }
            if (this.queuedSections.add(key)) {
                this.pendingSections.addLast(key);
            }
        }
    }

    private void processQueue(String dimension) {
        int maxSections = getConfiguredMaxSectionsPerTick();
        for (int processed = 0; processed < maxSections && !this.pendingSections.isEmpty(); processed++) {
            Key key = this.pendingSections.pollFirst();
            if (key == null) {
                break;
            }
            this.queuedSections.remove(key);
            if (!dimension.equals(key.dimension())) {
                continue;
            }

            ForgeVoxyBuiltSection section = this.instance.getVoxyGeometryCache().findLiveSection(key.dimension(), key.position());
            if (section == null) {
                this.consumedSections.remove(key);
                this.failedSections.remove(key);
                if (this.instance.getSectionGeometryManager().removeSection(key.dimension(), key.position())) {
                    this.windowRemoved++;
                }
                continue;
            }

            long stamp = sourceStamp(section);
            Long consumedStamp = this.consumedSections.get(key);
            if (consumedStamp != null && consumedStamp == stamp && this.instance.getSectionGeometryManager().hasSection(key.dimension(), key.position())) {
                continue;
            }

            try {
                long start = System.nanoTime();
                ForgeSectionGeometryManager.SectionConsumeResult result = this.instance.getSectionGeometryManager().consumeSection(section);
                double elapsedMs = (System.nanoTime() - start) / 1_000_000.0D;
                this.lastConsumeMs = elapsedMs;
                if (!result.consumed()) {
                    this.failedSections.put(key, stamp);
                    this.lastFailureReason = result.reason();
                    continue;
                }
                this.consumedSections.put(key, stamp);
                this.failedSections.remove(key);
                this.windowConsumed++;
                if (result.replaced()) {
                    this.windowReplaced++;
                }
                this.windowGeometryBytes += result.geometryBytes();
                this.windowElapsedMs += elapsedMs;
            } catch (Exception e) {
                this.failedSections.put(key, stamp);
                this.lastFailureReason = e.getClass().getSimpleName() + ": " + e.getMessage();
                VoxyForge.LOGGER.error(
                        "Voxy auto section geometry consume failed for {} position {}",
                        key.dimension(),
                        Long.toUnsignedString(key.position()),
                        e
                );
            }
        }
    }

    private void pruneRecords(String dimension, int centerChunkX, int centerChunkZ, int radius) {
        int retentionRadius = Math.max(8, radius + 8);
        this.pendingSections.removeIf(key -> {
            boolean remove = !dimension.equals(key.dimension()) || !isWithinChunkRadius(key, centerChunkX, centerChunkZ, retentionRadius);
            if (remove) {
                this.queuedSections.remove(key);
            }
            return remove;
        });
        this.queuedSections.removeIf(key -> !dimension.equals(key.dimension()) || !isWithinChunkRadius(key, centerChunkX, centerChunkZ, retentionRadius));
        this.failedSections.keySet().removeIf(key -> !dimension.equals(key.dimension()) || !isWithinChunkRadius(key, centerChunkX, centerChunkZ, retentionRadius));
        this.consumedSections.keySet().removeIf(key -> {
            boolean remove = !dimension.equals(key.dimension()) || !isWithinChunkRadius(key, centerChunkX, centerChunkZ, retentionRadius)
                    || this.instance.getVoxyGeometryCache().findLiveSection(key.dimension(), key.position()) == null;
            if (remove && this.instance.getSectionGeometryManager().removeSection(key.dimension(), key.position())) {
                this.windowRemoved++;
            }
            return remove;
        });
    }

    private void maybeLogSummary(String dimension) {
        if (this.windowConsumed == 0 || this.tickCounter - this.lastSummaryTick < SUMMARY_INTERVAL_TICKS) {
            return;
        }

        double averageMs = this.windowElapsedMs / this.windowConsumed;
        VoxyForge.LOGGER.info(
                "Voxy auto section geometry consume {}: queued={} consumed={} replaced={} removed={} failed={} bytes={} avgMs={}",
                dimension,
                this.pendingSections.size(),
                this.windowConsumed,
                this.windowReplaced,
                this.windowRemoved,
                this.failedSections.size(),
                this.windowGeometryBytes,
                String.format("%.2f", averageMs)
        );
        this.lastAverageMs = averageMs;
        this.resetWindow();
        this.lastSummaryTick = this.tickCounter;
    }

    private void resetWindow() {
        this.windowConsumed = 0;
        this.windowReplaced = 0;
        this.windowRemoved = 0;
        this.windowGeometryBytes = 0;
        this.windowElapsedMs = 0.0D;
    }

    private double getAverageMs() {
        if (this.windowConsumed == 0) {
            return this.lastAverageMs;
        }
        return this.windowElapsedMs / this.windowConsumed;
    }

    private static long sourceStamp(ForgeVoxyBuiltSection section) {
        long stamp = section.createdTimeMillis();
        stamp = 31L * stamp + section.quadCount();
        stamp = 31L * stamp + section.geometryBytes();
        stamp = 31L * stamp + section.sampleRecord();
        return stamp;
    }

    private static boolean isWithinChunkRadius(Key key, int centerX, int centerZ, int radius) {
        return Math.abs(key.chunkX() - centerX) <= radius && Math.abs(key.chunkZ() - centerZ) <= radius;
    }

    private static boolean isAutoEnabled() {
        return ForgeVoxyRuntimeOverrides.enableAutoGeometryManagerConsume();
    }

    private static int getConfiguredRadius() {
        return Math.min(8, Math.max(0, ForgeVoxyConfig.AUTO_GEOMETRY_CONSUME_RADIUS.get()));
    }

    private static int getConfiguredMaxSectionsPerTick() {
        return Math.min(64, Math.max(1, ForgeVoxyConfig.AUTO_GEOMETRY_CONSUME_MAX_SECTIONS_PER_TICK.get()));
    }

    private static int getConfiguredCooldownTicks() {
        return Math.min(200, Math.max(0, ForgeVoxyConfig.AUTO_GEOMETRY_CONSUME_COOLDOWN_TICKS.get()));
    }

    private record Key(String dimension, long position, int chunkX, int chunkZ) {
        private static Key from(ForgeVoxyBuiltSection section) {
            return new Key(section.dimension(), section.position(), section.chunkX(), section.chunkZ());
        }
    }

    public record StatusSnapshot(
            boolean enginePresent,
            boolean autoEnabled,
            String dimension,
            int queuedSections,
            int consumedRecords,
            int failedRecords,
            int radius,
            int maxSectionsPerTick,
            int cooldownTicks,
            double lastConsumeMs,
            double averageMs,
            String lastFailureReason
    ) {
    }
}
