package me.cortex.voxy.forge;

import com.mojang.blaze3d.systems.RenderSystem;
import me.cortex.voxy.config.ForgeVoxyConfig;
import me.cortex.voxy.config.SimpleGpuMeshSource;
import net.minecraft.client.Minecraft;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;

public final class ForgeGpuGeometryReadbackMeshRefreshManager {
    public static final String REASON_MANUAL_COMMAND = "MANUAL_COMMAND";
    public static final String REASON_PRESET = "PRESET";
    public static final String REASON_COOLDOWN = "COOLDOWN";
    public static final String REASON_DIMENSION_CHANGE = "DIMENSION_CHANGE";
    public static final String REASON_SOURCE_SWITCH = "SOURCE_SWITCH";
    public static final String REASON_UPLOAD_CLEAR_REBUILD = "UPLOAD_CLEAR_REBUILD";

    public static final String SKIP_SOURCE_NOT_ACTIVE = "SOURCE_NOT_ACTIVE";
    public static final String SKIP_RENDERER_DISABLED = "RENDERER_DISABLED";
    public static final String SKIP_HEAP_MISSING = "HEAP_MISSING";
    public static final String SKIP_NO_UPLOADED_SECTIONS = "NO_UPLOADED_SECTIONS";
    public static final String SKIP_WORLD_MISSING = "WORLD_MISSING";
    public static final String SKIP_COOLDOWN = "COOLDOWN";

    private final ForgeVoxyInstance instance;
    private int ticksUntilNextRefresh;
    private String requestedReason;
    private long refreshRuns;
    private long refreshSkipped;
    private long refreshFailures;
    private String lastRefreshReason = "none";
    private String lastRefreshSkippedReason = "none";
    private double lastRefreshDurationMs;
    private int lastRefreshSections;
    private int lastRefreshRecords;
    private int lastRefreshQuads;
    private int lastRefreshVertices;
    private String lastRefreshError = "none";

    ForgeGpuGeometryReadbackMeshRefreshManager(ForgeVoxyInstance instance) {
        this.instance = instance;
    }

    public void register() {
        MinecraftForge.EVENT_BUS.addListener(this::onClientTick);
    }

    public synchronized void requestRefresh(String reason) {
        this.requestedReason = reason == null ? REASON_MANUAL_COMMAND : reason;
        this.ticksUntilNextRefresh = 0;
    }

    public synchronized RefreshAttempt refreshNow(String reason) {
        return this.tryRefresh(reason == null ? REASON_MANUAL_COMMAND : reason, true);
    }

    public synchronized void clear() {
        this.ticksUntilNextRefresh = getConfiguredCooldownTicks();
        this.requestedReason = null;
        this.refreshRuns = 0;
        this.refreshSkipped = 0;
        this.refreshFailures = 0;
        this.lastRefreshReason = "none";
        this.lastRefreshSkippedReason = "cleared";
        this.lastRefreshDurationMs = 0.0D;
        this.lastRefreshSections = 0;
        this.lastRefreshRecords = 0;
        this.lastRefreshQuads = 0;
        this.lastRefreshVertices = 0;
        this.lastRefreshError = "none";
    }

    public synchronized StatusSnapshot createStatusSnapshot() {
        return new StatusSnapshot(
                isAutoRefreshEnabled(),
                getConfiguredCooldownTicks(),
                this.ticksUntilNextRefresh,
                this.refreshRuns,
                this.refreshSkipped,
                this.refreshFailures,
                this.lastRefreshReason,
                this.lastRefreshSkippedReason,
                this.lastRefreshDurationMs,
                this.lastRefreshSections,
                this.lastRefreshRecords,
                this.lastRefreshQuads,
                this.lastRefreshVertices,
                this.lastRefreshError,
                getConfiguredMaxSections(),
                getConfiguredMaxRecords(),
                onlyWhenSourceActive(),
                onlyWhenRendererEnabled()
        );
    }

    private synchronized void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        String requested = this.requestedReason;
        if (requested != null) {
            this.requestedReason = null;
            this.tryRefresh(requested, true);
            return;
        }

        if (!isAutoRefreshEnabled()) {
            return;
        }
        if (this.ticksUntilNextRefresh > 0) {
            this.ticksUntilNextRefresh--;
            return;
        }

        this.tryRefresh(REASON_COOLDOWN, false);
    }

    private RefreshAttempt tryRefresh(String reason, boolean forced) {
        long start = System.nanoTime();
        String skipReason = this.findSkipReason(forced);
        if (skipReason != null) {
            this.recordSkipped(reason, skipReason, start);
            return new RefreshAttempt(false, true, skipReason, ForgeGpuGeometryReadbackMeshResult.failure(skipReason), this.createStatusSnapshot());
        }

        ForgeGpuGeometryReadbackMeshResult result = ForgeGpuGeometryReadbackMeshBuilder.buildSample(
                this.instance,
                getConfiguredMaxSections(),
                getConfiguredMaxRecords()
        );
        this.refreshRuns++;
        this.lastRefreshReason = reason;
        this.lastRefreshSkippedReason = "none";
        this.lastRefreshDurationMs = (System.nanoTime() - start) / 1_000_000.0D;
        this.lastRefreshSections = result.builtSections();
        this.lastRefreshRecords = result.recordsRead();
        this.lastRefreshQuads = result.quads();
        this.lastRefreshVertices = result.vertices();
        this.lastRefreshError = result.success() ? "none" : result.reason();
        if (!result.success()) {
            this.refreshFailures++;
        }
        this.ticksUntilNextRefresh = getConfiguredCooldownTicks();
        return new RefreshAttempt(result.success(), false, "none", result, this.createStatusSnapshot());
    }

    private String findSkipReason(boolean forced) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            return SKIP_WORLD_MISSING;
        }
        if (!forced && onlyWhenSourceActive() && ForgeGpuMeshUploadManager.getConfiguredSource() != SimpleGpuMeshSource.GL_HEAP_READBACK) {
            return SKIP_SOURCE_NOT_ACTIVE;
        }
        if (forced && onlyWhenSourceActive() && ForgeGpuMeshUploadManager.getConfiguredSource() != SimpleGpuMeshSource.GL_HEAP_READBACK) {
            return SKIP_SOURCE_NOT_ACTIVE;
        }
        if (!forced && onlyWhenRendererEnabled() && !ForgeVoxyRuntimeOverrides.enableSimpleGpuMeshRenderer()) {
            return SKIP_RENDERER_DISABLED;
        }
        if (forced && onlyWhenRendererEnabled() && !ForgeVoxyRuntimeOverrides.enableSimpleGpuMeshRenderer()) {
            return SKIP_RENDERER_DISABLED;
        }
        if (!RenderSystem.isOnRenderThread()) {
            return "NOT_RENDER_THREAD";
        }
        if (!ForgeGpuGeometryUploadManager.isEnabled()) {
            return SKIP_HEAP_MISSING;
        }
        ForgeGpuGeometryHeap heap = this.instance.getGpuGeometryUploadManager().getHeapForDebugReadback();
        if (!heap.isCreated()) {
            return SKIP_HEAP_MISSING;
        }
        if (this.instance.getGpuGeometryUploadManager().createUploadedSectionIdSnapshot().isEmpty()) {
            return SKIP_NO_UPLOADED_SECTIONS;
        }
        return null;
    }

    private void recordSkipped(String reason, String skipReason, long start) {
        this.refreshSkipped++;
        this.lastRefreshReason = reason;
        this.lastRefreshSkippedReason = skipReason;
        this.lastRefreshDurationMs = (System.nanoTime() - start) / 1_000_000.0D;
        this.lastRefreshSections = 0;
        this.lastRefreshRecords = 0;
        this.lastRefreshQuads = 0;
        this.lastRefreshVertices = 0;
        this.lastRefreshError = skipReason;
        this.ticksUntilNextRefresh = retryDelayFor(skipReason);
    }

    private static int retryDelayFor(String skipReason) {
        int cooldown = getConfiguredCooldownTicks();
        if (SKIP_HEAP_MISSING.equals(skipReason) || SKIP_NO_UPLOADED_SECTIONS.equals(skipReason) || SKIP_WORLD_MISSING.equals(skipReason)) {
            return Math.min(10, cooldown);
        }
        if (SKIP_COOLDOWN.equals(skipReason)) {
            return Math.max(1, cooldown);
        }
        return cooldown;
    }

    public static boolean isAutoRefreshEnabled() {
        return ForgeVoxyRuntimeOverrides.enableGeometryGpuReadbackMeshAutoRefresh();
    }

    public static int getConfiguredCooldownTicks() {
        return Math.min(400, Math.max(1, ForgeVoxyConfig.GEOMETRY_GPU_READBACK_MESH_REFRESH_COOLDOWN_TICKS.get()));
    }

    public static int getConfiguredMaxSections() {
        return Math.min(64, Math.max(1, ForgeVoxyConfig.GEOMETRY_GPU_READBACK_MESH_REFRESH_MAX_SECTIONS.get()));
    }

    public static int getConfiguredMaxRecords() {
        return Math.min(131072, Math.max(1, ForgeVoxyConfig.GEOMETRY_GPU_READBACK_MESH_REFRESH_MAX_RECORDS.get()));
    }

    public static boolean onlyWhenSourceActive() {
        return ForgeVoxyConfig.GEOMETRY_GPU_READBACK_MESH_REFRESH_ONLY_WHEN_SOURCE_ACTIVE.get();
    }

    public static boolean onlyWhenRendererEnabled() {
        return ForgeVoxyConfig.GEOMETRY_GPU_READBACK_MESH_REFRESH_ONLY_WHEN_RENDERER_ENABLED.get();
    }

    public static boolean refreshOnDimensionChange() {
        return ForgeVoxyConfig.GEOMETRY_GPU_READBACK_MESH_REFRESH_ON_DIMENSION_CHANGE.get();
    }

    public static boolean refreshOnPreset() {
        return ForgeVoxyConfig.GEOMETRY_GPU_READBACK_MESH_REFRESH_ON_PRESET.get();
    }

    public record RefreshAttempt(
            boolean success,
            boolean skipped,
            String skippedReason,
            ForgeGpuGeometryReadbackMeshResult result,
            StatusSnapshot status
    ) {
    }

    public record StatusSnapshot(
            boolean autoRefresh,
            int refreshCooldownTicks,
            int ticksUntilNextRefresh,
            long refreshRuns,
            long refreshSkipped,
            long refreshFailures,
            String lastRefreshReason,
            String lastRefreshSkippedReason,
            double lastRefreshDurationMs,
            int lastRefreshSections,
            int lastRefreshRecords,
            int lastRefreshQuads,
            int lastRefreshVertices,
            String lastRefreshError,
            int refreshMaxSections,
            int refreshMaxRecords,
            boolean onlyWhenSourceActive,
            boolean onlyWhenRendererEnabled
    ) {
    }
}
