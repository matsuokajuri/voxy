package me.cortex.voxy.forge;

import com.mojang.blaze3d.systems.RenderSystem;
import me.cortex.voxy.config.ForgeVoxyConfig;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;

import java.util.HashSet;
import java.util.List;

public final class ForgeGpuMeshUploadManager {
    private final ForgeVoxyInstance instance;
    private int uploadBudget;
    private UploadStatusSnapshot lastStatus = UploadStatusSnapshot.disabled();
    private long uploadWindowCount;
    private double uploadWindowMs;
    private double lastAverageUploadMs;

    ForgeGpuMeshUploadManager(ForgeVoxyInstance instance) {
        this.instance = instance;
    }

    public void register() {
        MinecraftForge.EVENT_BUS.addListener(this::onClientTick);
    }

    public void clear() {
        this.instance.getGpuMeshCache().clear();
        this.uploadBudget = 0;
        this.uploadWindowCount = 0;
        this.uploadWindowMs = 0.0D;
        this.lastAverageUploadMs = 0.0D;
        this.lastStatus = UploadStatusSnapshot.disabled();
    }

    public UploadStatusSnapshot getLastStatus() {
        return this.lastStatus;
    }

    public UploadStatusSnapshot processUploads(String dimension, int centerChunkX, int centerChunkZ) {
        if (!ForgeVoxyConfig.ENABLE_SIMPLE_GPU_MESH_RENDERER.get()) {
            this.lastStatus = UploadStatusSnapshot.disabled();
            return this.lastStatus;
        }
        if (!RenderSystem.isOnRenderThread()) {
            this.lastStatus = UploadStatusSnapshot.skipped("not-render-thread", this.uploadBudget);
            return this.lastStatus;
        }

        int radius = getConfiguredRenderDistanceChunks();
        int maxCandidates = getConfiguredMaxBuffers();
        ForgeCpuMeshCache.RenderSnapshot cpuSnapshot = this.instance.getCpuMeshCache().createRenderSnapshot(
                dimension,
                centerChunkX,
                centerChunkZ,
                radius,
                maxCandidates
        );
        List<ForgeCpuBuiltSection> sections = cpuSnapshot.sections();
        var liveKeys = new HashSet<ForgeCpuMeshCache.Key>(sections.size());
        int pendingUploads = 0;
        int alreadyUploaded = 0;
        int skippedTranslucent = 0;
        int skippedEmpty = 0;

        for (ForgeCpuBuiltSection section : sections) {
            ForgeCpuMeshCache.Key key = ForgeCpuMeshCache.Key.from(section);
            liveKeys.add(key);
            if (section.layer() == ForgeCpuMeshLayer.TRANSLUCENT) {
                skippedTranslucent++;
                continue;
            }
            ForgeCpuMeshBuffer meshBuffer = section.meshBuffer();
            if (meshBuffer == null || meshBuffer.isClosed() || meshBuffer.vertexCount() == 0) {
                skippedEmpty++;
                continue;
            }
            if (this.instance.getGpuMeshCache().hasMatching(section)) {
                alreadyUploaded++;
                continue;
            }
            pendingUploads++;
        }

        this.instance.getGpuMeshCache().setActiveDimension(dimension);
        this.instance.getGpuMeshCache().retainOnly(dimension, liveKeys);

        int uploadLimit = Math.min(this.uploadBudget, getConfiguredMaxUploadsPerTick());
        int uploaded = 0;
        int failed = 0;
        long uploadedVertices = 0;
        long uploadedBytes = 0;
        long start = System.nanoTime();
        if (uploadLimit > 0 && pendingUploads > 0) {
            for (ForgeCpuBuiltSection section : sections) {
                if (uploaded >= uploadLimit) {
                    break;
                }
                if (section.layer() == ForgeCpuMeshLayer.TRANSLUCENT) {
                    continue;
                }
                ForgeCpuMeshBuffer meshBuffer = section.meshBuffer();
                if (meshBuffer == null || meshBuffer.isClosed() || meshBuffer.vertexCount() == 0) {
                    continue;
                }
                if (this.instance.getGpuMeshCache().hasMatching(section)) {
                    continue;
                }

                try {
                    ForgeGpuMeshBuffer uploadedBuffer = ForgeGpuMeshBuffer.upload(section);
                    uploadedVertices += uploadedBuffer.vertexCount();
                    uploadedBytes += uploadedBuffer.sizeBytes();
                    this.instance.getGpuMeshCache().put(uploadedBuffer);
                    uploaded++;
                } catch (Exception e) {
                    failed++;
                    VoxyForge.LOGGER.error(
                            "Failed to upload Voxy simple GPU mesh for {} chunk {},{} layer {}",
                            section.dimension(),
                            section.chunkX(),
                            section.chunkZ(),
                            section.layer().displayName,
                            e
                    );
                }
            }
        }

        if (uploaded > 0) {
            this.uploadBudget = Math.max(0, this.uploadBudget - uploaded);
            double elapsedMs = (System.nanoTime() - start) / 1_000_000.0D;
            this.uploadWindowCount += uploaded;
            this.uploadWindowMs += elapsedMs;
            this.lastAverageUploadMs = this.uploadWindowMs / this.uploadWindowCount;
        }

        this.lastStatus = new UploadStatusSnapshot(
                true,
                "ok",
                dimension,
                this.uploadBudget,
                sections.size(),
                pendingUploads,
                uploaded,
                failed,
                alreadyUploaded,
                skippedTranslucent,
                skippedEmpty,
                cpuSnapshot.skippedByDimension(),
                cpuSnapshot.skippedByDistance(),
                cpuSnapshot.skippedReleased(),
                cpuSnapshot.limitedEntries(),
                uploadedVertices,
                uploadedBytes,
                this.lastAverageUploadMs
        );
        return this.lastStatus;
    }

    private void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (!ForgeVoxyConfig.ENABLE_SIMPLE_GPU_MESH_RENDERER.get() || this.instance.getCurrentEngineOptional().isEmpty()) {
            if (this.lastStatus.enabled()) {
                this.clear();
            }
            return;
        }
        int maxUploads = getConfiguredMaxUploadsPerTick();
        this.uploadBudget = Math.min(maxUploads, this.uploadBudget + maxUploads);
    }

    public static int getConfiguredMaxUploadsPerTick() {
        return Math.min(16, Math.max(1, ForgeVoxyConfig.SIMPLE_GPU_MESH_MAX_UPLOADS_PER_TICK.get()));
    }

    public static int getConfiguredMaxBuffers() {
        return Math.min(8192, Math.max(1, ForgeVoxyConfig.SIMPLE_GPU_MESH_MAX_BUFFERS.get()));
    }

    public static int getConfiguredRenderDistanceChunks() {
        return Math.min(32, Math.max(0, ForgeVoxyConfig.SIMPLE_GPU_MESH_RENDER_DISTANCE_CHUNKS.get()));
    }

    public record UploadStatusSnapshot(
            boolean enabled,
            String reason,
            String dimension,
            int uploadBudget,
            int candidateCpuEntries,
            int pendingUploads,
            int uploadedThisFrame,
            int failedThisFrame,
            int alreadyUploaded,
            int skippedTranslucent,
            int skippedEmpty,
            int skippedCpuByDimension,
            int skippedCpuByDistance,
            int skippedCpuReleased,
            int limitedCpuEntries,
            long uploadedVerticesThisFrame,
            long uploadedBytesThisFrame,
            double averageUploadMs
    ) {
        private static UploadStatusSnapshot disabled() {
            return skipped("disabled", 0);
        }

        private static UploadStatusSnapshot skipped(String reason, int uploadBudget) {
            return new UploadStatusSnapshot(
                    false,
                    reason,
                    "none",
                    uploadBudget,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0.0D
            );
        }
    }
}
