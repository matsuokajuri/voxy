package me.cortex.voxy.forge;

import com.mojang.blaze3d.systems.RenderSystem;
import me.cortex.voxy.config.SimpleGpuMeshSource;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class ForgeGpuMeshUploadManager {
    private final ForgeVoxyInstance instance;
    private int uploadBudget;
    private UploadStatusSnapshot lastStatus = UploadStatusSnapshot.disabled();
    private long uploadWindowCount;
    private double uploadWindowMs;
    private double lastAverageUploadMs;
    private long sourceSwitchCount;
    private long orphanReconciledTotal;
    private int pendingOrphanReconciled;
    private double lastBuiltSectionDecodeMs;
    private long builtSectionDecodeWindowCount;
    private double builtSectionDecodeWindowMs;
    private double lastAverageBuiltSectionDecodeMs;
    private SimpleGpuMeshSource activeSource;

    ForgeGpuMeshUploadManager(ForgeVoxyInstance instance) {
        this.instance = instance;
    }

    public void register() {
        MinecraftForge.EVENT_BUS.addListener(this::onClientTick);
    }

    public void clear() {
        int closed = this.instance.getGpuMeshCache().clear();
        this.recordOrphanReconciled(closed);
        this.uploadBudget = 0;
        this.uploadWindowCount = 0;
        this.uploadWindowMs = 0.0D;
        this.lastAverageUploadMs = 0.0D;
        this.lastBuiltSectionDecodeMs = 0.0D;
        this.builtSectionDecodeWindowCount = 0;
        this.builtSectionDecodeWindowMs = 0.0D;
        this.lastAverageBuiltSectionDecodeMs = 0.0D;
        this.activeSource = null;
        this.lastStatus = UploadStatusSnapshot.disabled();
    }

    public UploadStatusSnapshot getLastStatus() {
        return this.lastStatus;
    }

    public UploadStatusSnapshot processUploads(String dimension, int centerChunkX, int centerChunkZ) {
        if (!ForgeVoxyRuntimeOverrides.enableSimpleGpuMeshRenderer()) {
            this.lastStatus = UploadStatusSnapshot.disabled();
            return this.lastStatus;
        }
        if (!RenderSystem.isOnRenderThread()) {
            this.lastStatus = UploadStatusSnapshot.skipped("not-render-thread", this.uploadBudget);
            return this.lastStatus;
        }

        SimpleGpuMeshSource source = getConfiguredSource();
        if (source != this.activeSource) {
            int closed = this.instance.getGpuMeshCache().clear();
            this.recordOrphanReconciled(closed);
            this.sourceSwitchCount++;
            this.activeSource = source;
        }
        if (source == SimpleGpuMeshSource.BUILT_SECTION) {
            return this.processBuiltSectionUploads(dimension, source);
        }
        if (source == SimpleGpuMeshSource.GL_HEAP_READBACK) {
            return this.processGlHeapReadbackUploads(dimension, source);
        }
        return this.processCpuMeshUploads(dimension, centerChunkX, centerChunkZ, source);
    }

    private UploadStatusSnapshot processCpuMeshUploads(String dimension, int centerChunkX, int centerChunkZ, SimpleGpuMeshSource source) {
        int radius = getConfiguredRenderDistanceChunks();
        int maxCandidates = getConfiguredMaxBuffers();
        boolean keepCachedChunks = keepCachedChunks();
        ForgeCpuMeshCache.RenderSnapshot cpuSnapshot = keepCachedChunks
                ? this.instance.getCpuMeshCache().createUploadSnapshot(dimension, maxCandidates)
                : this.instance.getCpuMeshCache().createRenderSnapshot(
                        dimension,
                        centerChunkX,
                        centerChunkZ,
                        radius,
                        maxCandidates
                );
        List<ForgeCpuBuiltSection> sections = cpuSnapshot.sections();
        Set<ForgeCpuMeshCache.Key> liveKeys = keepCachedChunks
                ? this.instance.getCpuMeshCache().createKeySnapshot(dimension)
                : new HashSet<>(sections.size());
        boolean useOriginalColors = useOriginalColors();
        int colorModeStamp = ForgeGpuMeshBuffer.colorModeStamp(useOriginalColors);
        int pendingUploads = 0;
        int alreadyUploaded = 0;
        int skippedTranslucent = 0;
        int skippedEmpty = 0;

        for (ForgeCpuBuiltSection section : sections) {
            ForgeCpuMeshCache.Key key = ForgeCpuMeshCache.Key.from(section);
            if (!keepCachedChunks) {
                liveKeys.add(key);
            }
            if (section.layer() == ForgeCpuMeshLayer.TRANSLUCENT) {
                skippedTranslucent++;
                continue;
            }
            ForgeCpuMeshBuffer meshBuffer = section.meshBuffer();
            if (meshBuffer == null || meshBuffer.isClosed() || meshBuffer.vertexCount() == 0) {
                skippedEmpty++;
                continue;
            }
            if (this.instance.getGpuMeshCache().hasMatching(section, colorModeStamp)) {
                alreadyUploaded++;
                continue;
            }
            pendingUploads++;
        }

        this.instance.getGpuMeshCache().setActiveDimension(dimension);
        int orphanReconciled = this.consumePendingOrphanReconciled();
        int retainedRemoved = this.instance.getGpuMeshCache().retainOnly(dimension, liveKeys);
        orphanReconciled += retainedRemoved;
        this.orphanReconciledTotal += retainedRemoved;

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
                if (this.instance.getGpuMeshCache().hasMatching(section, colorModeStamp)) {
                    continue;
                }

                try {
                    ForgeGpuMeshBuffer uploadedBuffer = ForgeGpuMeshBuffer.upload(section, useOriginalColors);
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
                source,
                dimension,
                this.uploadBudget,
                sections.size(),
                0,
                0,
                pendingUploads,
                uploaded,
                failed,
                alreadyUploaded,
                skippedTranslucent,
                0,
                skippedEmpty,
                cpuSnapshot.skippedByDimension(),
                cpuSnapshot.skippedByDistance(),
                cpuSnapshot.skippedReleased(),
                cpuSnapshot.limitedEntries(),
                0,
                orphanReconciled,
                this.orphanReconciledTotal,
                this.sourceSwitchCount,
                this.lastBuiltSectionDecodeMs,
                this.lastAverageBuiltSectionDecodeMs,
                uploadedVertices,
                uploadedBytes,
                this.lastAverageUploadMs,
                useOriginalColors,
                keepCachedChunks
        );
        return this.lastStatus;
    }

    private UploadStatusSnapshot processBuiltSectionUploads(String dimension, SimpleGpuMeshSource source) {
        int maxCandidates = getConfiguredMaxBuffers();
        ForgeVoxyGeometryCache.RenderSnapshot builtSnapshot = this.instance.getVoxyGeometryCache().createUploadSnapshot(dimension, maxCandidates);
        List<ForgeVoxyBuiltSection> sections = builtSnapshot.sections();
        Set<ForgeCpuMeshCache.Key> liveKeys = this.instance.getVoxyGeometryCache().createKeySnapshot(dimension);
        boolean useOriginalColors = useOriginalColors();
        int colorModeStamp = ForgeGpuMeshBuffer.colorModeStamp(useOriginalColors);
        int pendingUploads = 0;
        int alreadyUploaded = 0;
        int skippedTranslucent = 0;
        int doubleSidedAsSingle = 0;
        int skippedEmpty = 0;

        for (ForgeVoxyBuiltSection section : sections) {
            ForgeBuiltSectionSimpleMeshBuilder.PreviewStats preview = ForgeBuiltSectionSimpleMeshBuilder.preview(section);
            skippedTranslucent += preview.translucentRecords();
            doubleSidedAsSingle += preview.doubleSidedRecords();
            if (preview.uploadableRecords() == 0) {
                skippedEmpty++;
                continue;
            }
            if (this.instance.getGpuMeshCache().hasMatching(section, colorModeStamp)) {
                alreadyUploaded++;
                continue;
            }
            pendingUploads++;
        }

        this.instance.getGpuMeshCache().setActiveDimension(dimension);
        int orphanReconciled = this.consumePendingOrphanReconciled();
        int retainedRemoved = this.instance.getGpuMeshCache().retainOnly(dimension, liveKeys);
        orphanReconciled += retainedRemoved;
        this.orphanReconciledTotal += retainedRemoved;

        int uploadLimit = Math.min(this.uploadBudget, getConfiguredMaxUploadsPerTick());
        int uploaded = 0;
        int failed = 0;
        int skippedInvalid = 0;
        long uploadedVertices = 0;
        long uploadedBytes = 0;
        long start = System.nanoTime();
        if (uploadLimit > 0 && pendingUploads > 0) {
            for (ForgeVoxyBuiltSection section : sections) {
                if (uploaded >= uploadLimit) {
                    break;
                }
                ForgeBuiltSectionSimpleMeshBuilder.PreviewStats preview = ForgeBuiltSectionSimpleMeshBuilder.preview(section);
                if (preview.uploadableRecords() == 0 || this.instance.getGpuMeshCache().hasMatching(section, colorModeStamp)) {
                    continue;
                }

                long decodeStart = System.nanoTime();
                try (ForgeBuiltSectionSimpleMeshBuilder.AdaptedSection adapted = ForgeBuiltSectionSimpleMeshBuilder.build(section)) {
                    this.recordBuiltSectionDecodeMs((System.nanoTime() - decodeStart) / 1_000_000.0D);
                    skippedInvalid += adapted.stats().skippedInvalid();
                    ForgeCpuBuiltSection cpuSection = adapted.section();
                    if (cpuSection == null || cpuSection.meshBuffer() == null || cpuSection.meshBuffer().isClosed() || cpuSection.meshBuffer().vertexCount() == 0) {
                        skippedEmpty++;
                        continue;
                    }
                    ForgeGpuMeshBuffer uploadedBuffer = ForgeGpuMeshBuffer.upload(cpuSection, useOriginalColors);
                    uploadedVertices += uploadedBuffer.vertexCount();
                    uploadedBytes += uploadedBuffer.sizeBytes();
                    this.instance.getGpuMeshCache().put(uploadedBuffer);
                    uploaded++;
                } catch (Exception e) {
                    this.recordBuiltSectionDecodeMs((System.nanoTime() - decodeStart) / 1_000_000.0D);
                    failed++;
                    VoxyForge.LOGGER.error(
                            "Failed to upload Voxy BuiltSection simple GPU mesh for {} chunk {},{} position {}",
                            section.dimension(),
                            section.chunkX(),
                            section.chunkZ(),
                            Long.toUnsignedString(section.position()),
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
                source,
                dimension,
                this.uploadBudget,
                0,
                sections.size(),
                0,
                pendingUploads,
                uploaded,
                failed,
                alreadyUploaded,
                skippedTranslucent,
                doubleSidedAsSingle,
                skippedEmpty,
                builtSnapshot.skippedByDimension(),
                0,
                builtSnapshot.skippedReleased(),
                builtSnapshot.limitedEntries(),
                skippedInvalid,
                orphanReconciled,
                this.orphanReconciledTotal,
                this.sourceSwitchCount,
                this.lastBuiltSectionDecodeMs,
                this.lastAverageBuiltSectionDecodeMs,
                uploadedVertices,
                uploadedBytes,
                this.lastAverageUploadMs,
                useOriginalColors,
                true
        );
        return this.lastStatus;
    }

    private UploadStatusSnapshot processGlHeapReadbackUploads(String dimension, SimpleGpuMeshSource source) {
        int maxCandidates = getConfiguredMaxBuffers();
        ForgeCpuMeshCache.RenderSnapshot snapshot = this.instance.getGpuGeometryReadbackMeshCache().createUploadSnapshot(dimension, maxCandidates);
        List<ForgeCpuBuiltSection> sections = snapshot.sections();
        Set<ForgeCpuMeshCache.Key> liveKeys = this.instance.getGpuGeometryReadbackMeshCache().createKeySnapshot(dimension);
        boolean useOriginalColors = useOriginalColors();
        int colorModeStamp = ForgeGpuMeshBuffer.colorModeStamp(useOriginalColors);
        int pendingUploads = 0;
        int alreadyUploaded = 0;
        int skippedTranslucent = 0;
        int skippedEmpty = 0;

        for (ForgeCpuBuiltSection section : sections) {
            if (section.layer() == ForgeCpuMeshLayer.TRANSLUCENT) {
                skippedTranslucent++;
                continue;
            }
            ForgeCpuMeshBuffer meshBuffer = section.meshBuffer();
            if (meshBuffer == null || meshBuffer.isClosed() || meshBuffer.vertexCount() == 0) {
                skippedEmpty++;
                continue;
            }
            if (this.instance.getGpuMeshCache().hasMatching(section, colorModeStamp)) {
                alreadyUploaded++;
                continue;
            }
            pendingUploads++;
        }

        this.instance.getGpuMeshCache().setActiveDimension(dimension);
        int orphanReconciled = this.consumePendingOrphanReconciled();
        int retainedRemoved = this.instance.getGpuMeshCache().retainOnly(dimension, liveKeys);
        orphanReconciled += retainedRemoved;
        this.orphanReconciledTotal += retainedRemoved;

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
                if (this.instance.getGpuMeshCache().hasMatching(section, colorModeStamp)) {
                    continue;
                }

                try {
                    ForgeGpuMeshBuffer uploadedBuffer = ForgeGpuMeshBuffer.upload(section, useOriginalColors);
                    uploadedVertices += uploadedBuffer.vertexCount();
                    uploadedBytes += uploadedBuffer.sizeBytes();
                    this.instance.getGpuMeshCache().put(uploadedBuffer);
                    uploaded++;
                } catch (Exception e) {
                    failed++;
                    VoxyForge.LOGGER.error(
                            "Failed to upload Voxy GL heap readback simple GPU mesh for {} chunk {},{} position {}",
                            section.dimension(),
                            section.chunkX(),
                            section.chunkZ(),
                            Long.toUnsignedString(section.sectionPosition()),
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
                source,
                dimension,
                this.uploadBudget,
                0,
                0,
                sections.size(),
                pendingUploads,
                uploaded,
                failed,
                alreadyUploaded,
                skippedTranslucent,
                0,
                skippedEmpty,
                snapshot.skippedByDimension(),
                snapshot.skippedByDistance(),
                snapshot.skippedReleased(),
                snapshot.limitedEntries(),
                0,
                orphanReconciled,
                this.orphanReconciledTotal,
                this.sourceSwitchCount,
                this.lastBuiltSectionDecodeMs,
                this.lastAverageBuiltSectionDecodeMs,
                uploadedVertices,
                uploadedBytes,
                this.lastAverageUploadMs,
                useOriginalColors,
                true
        );
        return this.lastStatus;
    }

    private void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (!ForgeVoxyRuntimeOverrides.enableSimpleGpuMeshRenderer() || this.instance.getCurrentEngineOptional().isEmpty()) {
            if (this.lastStatus.enabled()) {
                this.clear();
            }
            return;
        }
        int maxUploads = getConfiguredMaxUploadsPerTick();
        this.uploadBudget = Math.min(maxUploads, this.uploadBudget + maxUploads);
    }

    public static int getConfiguredMaxUploadsPerTick() {
        return Math.min(16, Math.max(1, me.cortex.voxy.config.ForgeVoxyConfig.SIMPLE_GPU_MESH_MAX_UPLOADS_PER_TICK.get()));
    }

    public static int getConfiguredMaxBuffers() {
        return Math.min(8192, Math.max(1, me.cortex.voxy.config.ForgeVoxyConfig.SIMPLE_GPU_MESH_MAX_BUFFERS.get()));
    }

    public static int getConfiguredRenderDistanceChunks() {
        return ForgeVoxyRuntimeOverrides.simpleGpuMeshRenderDistanceChunks();
    }

    public static boolean useOriginalColors() {
        return ForgeVoxyRuntimeOverrides.simpleGpuMeshUseOriginalColors();
    }

    public static boolean keepCachedChunks() {
        return ForgeVoxyRuntimeOverrides.simpleGpuMeshKeepCachedChunks();
    }

    public static SimpleGpuMeshSource getConfiguredSource() {
        return ForgeVoxyRuntimeOverrides.simpleGpuMeshSource();
    }

    private void recordOrphanReconciled(int count) {
        if (count <= 0) {
            return;
        }
        this.pendingOrphanReconciled += count;
        this.orphanReconciledTotal += count;
    }

    private int consumePendingOrphanReconciled() {
        int count = this.pendingOrphanReconciled;
        this.pendingOrphanReconciled = 0;
        return count;
    }

    private void recordBuiltSectionDecodeMs(double elapsedMs) {
        this.lastBuiltSectionDecodeMs = elapsedMs;
        this.builtSectionDecodeWindowCount++;
        this.builtSectionDecodeWindowMs += elapsedMs;
        this.lastAverageBuiltSectionDecodeMs = this.builtSectionDecodeWindowMs / this.builtSectionDecodeWindowCount;
    }

    public record UploadStatusSnapshot(
            boolean enabled,
            String reason,
            SimpleGpuMeshSource source,
            String dimension,
            int uploadBudget,
            int candidateCpuEntries,
            int candidateBuiltSectionEntries,
            int candidateGlHeapReadbackEntries,
            int pendingUploads,
            int uploadedThisFrame,
            int failedThisFrame,
            int alreadyUploaded,
            int skippedTranslucent,
            int builtSectionDoubleSidedRecords,
            int skippedEmpty,
            int skippedCpuByDimension,
            int skippedCpuByDistance,
            int skippedCpuReleased,
            int limitedCpuEntries,
            int skippedBuiltSectionInvalid,
            int orphanReconciled,
            long orphanReconciledTotal,
            long sourceSwitchCount,
            double lastBuiltSectionDecodeMs,
            double averageBuiltSectionDecodeMs,
            long uploadedVerticesThisFrame,
            long uploadedBytesThisFrame,
            double averageUploadMs,
            boolean useOriginalColors,
            boolean keepCachedChunks
    ) {
        private static UploadStatusSnapshot disabled() {
            return skipped("disabled", 0);
        }

        private static UploadStatusSnapshot skipped(String reason, int uploadBudget) {
            return new UploadStatusSnapshot(
                    false,
                    reason,
                    SimpleGpuMeshSource.CPU_MESH,
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
                    0,
                    0,
                    0,
                    0L,
                    0L,
                    0.0D,
                    0.0D,
                    0L,
                    0L,
                    0.0D,
                    true,
                    true
            );
        }
    }
}
