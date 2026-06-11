package me.cortex.voxy.forge;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexBuffer;
import me.cortex.voxy.config.ForgeVoxyConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.common.MinecraftForge;
import org.joml.Matrix4f;

import java.util.List;

public final class ForgeSimpleGpuMeshRenderer {
    private static final String RENDER_STAGE = "AFTER_TRANSLUCENT_BLOCKS";

    private final ForgeVoxyInstance instance;
    private volatile FrameStats lastFrameStats = FrameStats.skipped("not-run");
    private long nextSummaryLogMillis;

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
        if (!ForgeVoxyConfig.ENABLE_WORLD_ENGINE_SKELETON.get() || !ForgeVoxyConfig.ENABLE_SIMPLE_GPU_MESH_RENDERER.get()) {
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
                ForgeGpuMeshUploadManager.getConfiguredRenderDistanceChunks()
        );
        if (snapshot.buffers().isEmpty()) {
            this.lastFrameStats = FrameStats.skipped("cache-empty", dimension, snapshot);
            return;
        }

        try {
            this.lastFrameStats = this.renderBuffers(event, dimension, snapshot);
            this.logFrameSummaryIfNeeded(this.lastFrameStats);
        } catch (Exception e) {
            this.lastFrameStats = FrameStats.skipped("exception", dimension);
            VoxyForge.LOGGER.error("Failed to render Voxy simple GPU mesh", e);
        }
    }

    private FrameStats renderBuffers(RenderLevelStageEvent event, String dimension, ForgeGpuMeshCache.RenderSnapshot snapshot) {
        List<ForgeGpuMeshBuffer> buffers = snapshot.buffers();
        RenderCounters counters = new RenderCounters(snapshot);
        PoseStack poseStack = event.getPoseStack();
        Vec3 cameraPos = event.getCamera().getPosition();
        double alpha = getConfiguredAlpha();

        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        ShaderInstance shader = GameRenderer.getPositionColorShader();
        if (shader == null) {
            return counters.toFrameStats(false, "shader-missing", dimension, alpha);
        }

        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, (float) alpha);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();

        poseStack.pushPose();
        try {
            poseStack.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);
            Matrix4f modelView = poseStack.last().pose();
            Matrix4f projection = event.getProjectionMatrix();
            for (ForgeGpuMeshBuffer buffer : buffers) {
                this.drawBuffer(modelView, projection, shader, buffer, counters);
            }
        } finally {
            poseStack.popPose();
            VertexBuffer.unbind();
            RenderSystem.enableCull();
            RenderSystem.depthMask(true);
            RenderSystem.disableBlend();
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        }

        if (counters.renderedBuffers == 0) {
            return counters.toFrameStats(false, "no-buffers", dimension, alpha);
        }
        return counters.toFrameStats(true, "rendered", dimension, alpha);
    }

    private void drawBuffer(
            Matrix4f modelView,
            Matrix4f projection,
            ShaderInstance shader,
            ForgeGpuMeshBuffer buffer,
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

        buffer.vertexBuffer().bind();
        buffer.vertexBuffer().drawWithShader(modelView, projection, shader);
        counters.renderedBuffers++;
        counters.renderedVertices += buffer.vertexCount();
    }

    public static double getConfiguredAlpha() {
        return Math.max(0.05D, Math.min(1.0D, ForgeVoxyConfig.SIMPLE_GPU_MESH_ALPHA.get()));
    }

    public static String getRenderStageName() {
        return RENDER_STAGE;
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
                "Voxy simple GPU mesh render: dimension={} buffers={}/{} vertices={} alpha={} stage={}",
                stats.dimension(),
                stats.renderedBuffers(),
                stats.candidateBuffers(),
                stats.renderedVertices(),
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
            long renderedVertices,
            int skippedByDimension,
            int skippedByDistance,
            int skippedReleased,
            int skippedTranslucent,
            double alpha
    ) {
        private static FrameStats skipped(String reason) {
            return skipped(reason, "none");
        }

        private static FrameStats skipped(String reason, String dimension) {
            return new FrameStats(false, reason, RENDER_STAGE, dimension, 0, 0, 0, 0, 0, 0, 0, 0.0D);
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
                    snapshot.skippedByDimension(),
                    snapshot.skippedByDistance(),
                    snapshot.skippedReleased(),
                    0,
                    0.0D
            );
        }
    }

    private static final class RenderCounters {
        private final int candidateBuffers;
        private final int skippedByDimension;
        private final int skippedByDistance;
        private final int skippedReleasedFromSnapshot;
        private int renderedBuffers;
        private long renderedVertices;
        private int skippedReleasedBuffers;
        private int skippedTranslucentBuffers;

        private RenderCounters(ForgeGpuMeshCache.RenderSnapshot snapshot) {
            this.candidateBuffers = snapshot.buffers().size();
            this.skippedByDimension = snapshot.skippedByDimension();
            this.skippedByDistance = snapshot.skippedByDistance();
            this.skippedReleasedFromSnapshot = snapshot.skippedReleased();
        }

        private FrameStats toFrameStats(boolean rendered, String reason, String dimension, double alpha) {
            return new FrameStats(
                    rendered,
                    reason,
                    RENDER_STAGE,
                    dimension,
                    this.candidateBuffers,
                    this.renderedBuffers,
                    this.renderedVertices,
                    this.skippedByDimension,
                    this.skippedByDistance,
                    this.skippedReleasedFromSnapshot + this.skippedReleasedBuffers,
                    this.skippedTranslucentBuffers,
                    alpha
            );
        }
    }
}
