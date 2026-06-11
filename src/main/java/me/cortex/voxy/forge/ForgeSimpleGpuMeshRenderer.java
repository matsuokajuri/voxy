package me.cortex.voxy.forge;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexBuffer;
import me.cortex.voxy.config.SimpleGpuMeshLoadedChunkSkipMode;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.common.MinecraftForge;
import org.joml.Matrix4f;

import java.util.HashSet;
import java.util.List;

public final class ForgeSimpleGpuMeshRenderer {
    private static final String RENDER_STAGE = "AFTER_TRANSLUCENT_BLOCKS";

    private final ForgeVoxyInstance instance;
    private volatile FrameStats lastFrameStats = FrameStats.skipped("not-run");
    private long nextSummaryLogMillis;
    private long renderWindowCount;
    private double renderWindowMs;
    private double lastAverageRenderMs;

    ForgeSimpleGpuMeshRenderer(ForgeVoxyInstance instance) {
        this.instance = instance;
    }

    public void register() {
        MinecraftForge.EVENT_BUS.addListener(this::onRenderLevelStage);
    }

    public FrameStats getLastFrameStats() {
        return this.lastFrameStats;
    }

    private void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }
        if (!ForgeVoxyRuntimeOverrides.enabledWorldEngineSkeleton() || !ForgeVoxyRuntimeOverrides.enableSimpleGpuMeshRenderer()) {
            this.lastFrameStats = FrameStats.skipped("disabled");
            return;
        }
        if (this.instance.getCurrentEngineOptional().isEmpty()) {
            this.lastFrameStats = FrameStats.skipped("engine-missing");
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null || event.getCamera() == null) {
            this.lastFrameStats = FrameStats.skipped("world-missing");
            return;
        }

        String dimension = minecraft.level.dimension().location().toString();
        int centerChunkX = minecraft.player.chunkPosition().x;
        int centerChunkZ = minecraft.player.chunkPosition().z;
        this.instance.getGpuMeshUploadManager().processUploads(dimension, centerChunkX, centerChunkZ);

        ForgeGpuMeshCache.RenderSnapshot snapshot = this.instance.getGpuMeshCache().createRenderSnapshot(
                dimension,
                centerChunkX,
                centerChunkZ,
                ForgeGpuMeshUploadManager.getConfiguredRenderDistanceChunks(),
                getConfiguredMaxRenderedBuffers()
        );
        if (snapshot.buffers().isEmpty()) {
            this.lastFrameStats = FrameStats.skipped("cache-empty", dimension, snapshot);
            return;
        }

        try {
            this.lastFrameStats = this.renderBuffers(event, dimension, snapshot, minecraft.level, centerChunkX, centerChunkZ);
            this.logFrameSummaryIfNeeded(this.lastFrameStats);
        } catch (Exception e) {
            this.lastFrameStats = FrameStats.skipped("exception", dimension);
            VoxyForge.LOGGER.error("Failed to render Voxy simple GPU mesh", e);
        }
    }

    private FrameStats renderBuffers(
            RenderLevelStageEvent event,
            String dimension,
            ForgeGpuMeshCache.RenderSnapshot snapshot,
            ClientLevel level,
            int centerChunkX,
            int centerChunkZ
    ) {
        List<ForgeGpuMeshBuffer> buffers = snapshot.buffers();
        RenderCounters counters = new RenderCounters(snapshot);
        PoseStack poseStack = event.getPoseStack();
        Vec3 cameraPos = event.getCamera().getPosition();
        double alpha = getConfiguredAlpha();
        double verticalOffset = getConfiguredVerticalOffsetBlocks();
        boolean ignoreDepth = shouldIgnoreDepth();
        int minDistance = getConfiguredMinRenderDistanceChunks();
        int maxDistance = ForgeGpuMeshUploadManager.getConfiguredRenderDistanceChunks();
        minDistance = Math.min(minDistance, maxDistance);
        boolean renderLoadedChunks = shouldRenderLoadedChunks();
        SimpleGpuMeshLoadedChunkSkipMode loadedChunkSkipMode = getConfiguredLoadedChunkSkipMode();
        int loadedChunkMargin = getConfiguredLoadedChunkMargin();
        int clientRenderDistance = getClientRenderDistanceChunks();
        long start = System.nanoTime();

        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        ShaderInstance shader = GameRenderer.getPositionColorShader();
        if (shader == null) {
            double elapsedMs = (System.nanoTime() - start) / 1_000_000.0D;
            return counters.toFrameStats(false, "shader-missing", dimension, alpha, elapsedMs, this.lastAverageRenderMs);
        }

        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, (float) alpha);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        if (ignoreDepth) {
            RenderSystem.disableDepthTest();
        } else {
            RenderSystem.enableDepthTest();
        }
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();

        poseStack.pushPose();
        try {
            poseStack.translate(-cameraPos.x, -cameraPos.y + verticalOffset, -cameraPos.z);
            Matrix4f modelView = poseStack.last().pose();
            Matrix4f projection = event.getProjectionMatrix();
            for (ForgeGpuMeshBuffer buffer : buffers) {
                this.drawBuffer(
                        modelView,
                        projection,
                        shader,
                        buffer,
                        level,
                        centerChunkX,
                        centerChunkZ,
                        minDistance,
                        renderLoadedChunks,
                        loadedChunkSkipMode,
                        loadedChunkMargin,
                        clientRenderDistance,
                        counters
                );
            }
        } finally {
            poseStack.popPose();
            VertexBuffer.unbind();
            RenderSystem.enableCull();
            RenderSystem.enableDepthTest();
            RenderSystem.depthMask(true);
            RenderSystem.disableBlend();
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        }

        double elapsedMs = (System.nanoTime() - start) / 1_000_000.0D;
        if (counters.renderedBuffers > 0) {
            this.renderWindowCount++;
            this.renderWindowMs += elapsedMs;
            this.lastAverageRenderMs = this.renderWindowMs / this.renderWindowCount;
        }
        if (counters.renderedBuffers == 0) {
            return counters.toFrameStats(false, "no-buffers", dimension, alpha, elapsedMs, this.lastAverageRenderMs);
        }
        return counters.toFrameStats(true, "rendered", dimension, alpha, elapsedMs, this.lastAverageRenderMs);
    }

    private void drawBuffer(
            Matrix4f modelView,
            Matrix4f projection,
            ShaderInstance shader,
            ForgeGpuMeshBuffer buffer,
            ClientLevel level,
            int centerChunkX,
            int centerChunkZ,
            int minDistance,
            boolean renderLoadedChunks,
            SimpleGpuMeshLoadedChunkSkipMode loadedChunkSkipMode,
            int loadedChunkMargin,
            int clientRenderDistance,
            RenderCounters counters
    ) {
        if (buffer.isClosed() || buffer.vertexBuffer() == null) {
            counters.skippedReleasedBuffers++;
            return;
        }
        if (buffer.layer() == ForgeCpuMeshLayer.TRANSLUCENT) {
            counters.skippedTranslucentBuffers++;
            return;
        }
        int distance = Math.max(Math.abs(buffer.chunkX() - centerChunkX), Math.abs(buffer.chunkZ() - centerChunkZ));
        if (distance < minDistance) {
            counters.skippedNearBuffers++;
            return;
        }
        ForgeGpuMeshLoadedChunkFilter.SkipReason loadedSkipReason = ForgeGpuMeshLoadedChunkFilter.evaluate(
                renderLoadedChunks,
                loadedChunkSkipMode,
                loadedChunkMargin,
                buffer.chunkX(),
                buffer.chunkZ(),
                centerChunkX,
                centerChunkZ,
                clientRenderDistance,
                level::hasChunk
        );
        if (loadedSkipReason == ForgeGpuMeshLoadedChunkFilter.SkipReason.LOADED_STATE) {
            counters.skippedLoadedStateBuffers++;
            return;
        }
        if (loadedSkipReason == ForgeGpuMeshLoadedChunkFilter.SkipReason.RENDER_DISTANCE) {
            counters.skippedRenderDistanceBuffers++;
            return;
        }

        buffer.vertexBuffer().bind();
        buffer.vertexBuffer().drawWithShader(modelView, projection, shader);
        counters.renderedBuffers++;
        counters.renderedVertices += buffer.vertexCount();
        counters.renderedChunks.add(ChunkPos.asLong(buffer.chunkX(), buffer.chunkZ()));
    }

    public static double getConfiguredAlpha() {
        return ForgeVoxyRuntimeOverrides.simpleGpuMeshAlpha();
    }

    public static int getConfiguredMaxRenderedBuffers() {
        return Math.min(8192, Math.max(1, me.cortex.voxy.config.ForgeVoxyConfig.SIMPLE_GPU_MESH_MAX_RENDERED_BUFFERS.get()));
    }

    public static int getConfiguredMinRenderDistanceChunks() {
        return ForgeVoxyRuntimeOverrides.simpleGpuMeshMinRenderDistanceChunks();
    }

    public static boolean shouldRenderLoadedChunks() {
        return ForgeVoxyRuntimeOverrides.simpleGpuMeshRenderLoadedChunks();
    }

    public static SimpleGpuMeshLoadedChunkSkipMode getConfiguredLoadedChunkSkipMode() {
        return ForgeVoxyRuntimeOverrides.simpleGpuMeshLoadedChunkSkipMode();
    }

    public static int getConfiguredLoadedChunkMargin() {
        return ForgeVoxyRuntimeOverrides.simpleGpuMeshLoadedChunkMargin();
    }

    public static boolean shouldIgnoreDepth() {
        return ForgeVoxyRuntimeOverrides.simpleGpuMeshIgnoreDepth();
    }

    public static double getConfiguredVerticalOffsetBlocks() {
        return ForgeVoxyRuntimeOverrides.simpleGpuMeshVerticalOffset();
    }

    public static String getRenderStageName() {
        return RENDER_STAGE;
    }

    private static int getClientRenderDistanceChunks() {
        try {
            return Minecraft.getInstance().options.renderDistance().get();
        } catch (RuntimeException e) {
            return -1;
        }
    }

    private void logFrameSummaryIfNeeded(FrameStats stats) {
        if (!stats.rendered()) {
            return;
        }

        long now = System.currentTimeMillis();
        if (now < this.nextSummaryLogMillis) {
            return;
        }
        this.nextSummaryLogMillis = now + 5_000L;
        VoxyForge.LOGGER.info(
                "Voxy simple GPU mesh render: source={} dimension={} buffers={}/{} chunks={} vertices={} skippedNear={} skippedLoadedState={} skippedRenderDistance={} alpha={} stage={}",
                ForgeGpuMeshUploadManager.getConfiguredSource(),
                stats.dimension(),
                stats.renderedBuffers(),
                stats.candidateBuffers(),
                stats.renderedChunks(),
                stats.renderedVertices(),
                stats.skippedNear(),
                stats.skippedLoadedState(),
                stats.skippedRenderDistance(),
                stats.alpha(),
                stats.stage()
        );
    }

    public record FrameStats(
            boolean rendered,
            String reason,
            String stage,
            String dimension,
            int candidateBuffers,
            int renderedBuffers,
            int renderedChunks,
            long renderedVertices,
            int skippedByDimension,
            int skippedByDistance,
            int skippedReleased,
            int limitedBuffers,
            int skippedNear,
            int skippedLoaded,
            int skippedLoadedState,
            int skippedRenderDistance,
            int skippedTranslucent,
            double alpha,
            double lastRenderMs,
            double averageRenderMs
    ) {
        private static FrameStats skipped(String reason) {
            return skipped(reason, "none");
        }

        private static FrameStats skipped(String reason, String dimension) {
            return new FrameStats(false, reason, RENDER_STAGE, dimension, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0.0D, 0.0D, 0.0D);
        }

        private static FrameStats skipped(String reason, String dimension, ForgeGpuMeshCache.RenderSnapshot snapshot) {
            return new FrameStats(
                    false,
                    reason,
                    RENDER_STAGE,
                    dimension,
                    snapshot.buffers().size(),
                    0,
                    0,
                    0,
                    snapshot.skippedByDimension(),
                    snapshot.skippedByDistance(),
                    snapshot.skippedReleased(),
                    snapshot.limitedBuffers(),
                    0,
                    0,
                    0,
                    0,
                    0,
                    0.0D,
                    0.0D,
                    0.0D
            );
        }
    }

    private static final class RenderCounters {
        private final int candidateBuffers;
        private final int skippedByDimension;
        private final int skippedByDistance;
        private final int skippedReleasedFromSnapshot;
        private final int limitedBuffers;
        private final HashSet<Long> renderedChunks = new HashSet<>();
        private int renderedBuffers;
        private long renderedVertices;
        private int skippedReleasedBuffers;
        private int skippedNearBuffers;
        private int skippedLoadedStateBuffers;
        private int skippedRenderDistanceBuffers;
        private int skippedTranslucentBuffers;

        private RenderCounters(ForgeGpuMeshCache.RenderSnapshot snapshot) {
            this.candidateBuffers = snapshot.buffers().size();
            this.skippedByDimension = snapshot.skippedByDimension();
            this.skippedByDistance = snapshot.skippedByDistance();
            this.skippedReleasedFromSnapshot = snapshot.skippedReleased();
            this.limitedBuffers = snapshot.limitedBuffers();
        }

        private FrameStats toFrameStats(boolean rendered, String reason, String dimension, double alpha, double lastRenderMs, double averageRenderMs) {
            return new FrameStats(
                    rendered,
                    reason,
                    RENDER_STAGE,
                    dimension,
                    this.candidateBuffers,
                    this.renderedBuffers,
                    this.renderedChunks.size(),
                    this.renderedVertices,
                    this.skippedByDimension,
                    this.skippedByDistance,
                    this.skippedReleasedFromSnapshot + this.skippedReleasedBuffers,
                    this.limitedBuffers,
                    this.skippedNearBuffers,
                    this.skippedLoadedStateBuffers + this.skippedRenderDistanceBuffers,
                    this.skippedLoadedStateBuffers,
                    this.skippedRenderDistanceBuffers,
                    this.skippedTranslucentBuffers,
                    alpha,
                    lastRenderMs,
                    averageRenderMs
            );
        }
    }
}
