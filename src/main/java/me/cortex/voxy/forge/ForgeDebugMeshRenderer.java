package me.cortex.voxy.forge;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import me.cortex.voxy.config.ForgeVoxyConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.common.MinecraftForge;
import org.joml.Matrix4f;

import java.util.List;

public final class ForgeDebugMeshRenderer {
    private static final String RENDER_STAGE = "AFTER_TRANSLUCENT_BLOCKS";
    private static final int SOLID_COLOR = 0xFF00FF;
    private static final int CUTOUT_COLOR = 0x00FFFF;
    private static final int OTHER_COLOR = 0xFFFF00;

    private final ForgeVoxyInstance instance;
    private volatile FrameStats lastFrameStats = FrameStats.skipped("not-run");
    private long nextSummaryLogMillis;

    public ForgeDebugMeshRenderer(ForgeVoxyInstance instance) {
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
        if (!ForgeVoxyConfig.ENABLE_WORLD_ENGINE_SKELETON.get() || !ForgeVoxyConfig.ENABLE_DEBUG_MESH_RENDERER.get()) {
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
        int radius = getConfiguredRenderDistanceChunks();
        int centerChunkX = minecraft.player.chunkPosition().x;
        int centerChunkZ = minecraft.player.chunkPosition().z;
        ForgeCpuMeshCache.RenderSnapshot snapshot = this.instance.getCpuMeshCache().createRenderSnapshot(
                dimension,
                centerChunkX,
                centerChunkZ,
                radius,
                getConfiguredMaxRenderedEntries()
        );
        if (snapshot.sections().isEmpty()) {
            this.lastFrameStats = FrameStats.skipped("cache-empty", dimension, snapshot);
            return;
        }

        try {
            this.lastFrameStats = this.renderSections(event, dimension, snapshot);
            this.logFrameSummaryIfNeeded(this.lastFrameStats);
        } catch (Exception e) {
            this.lastFrameStats = FrameStats.skipped("exception", dimension);
            VoxyForge.LOGGER.error("Failed to render Voxy debug CPU mesh", e);
        }
    }

    private FrameStats renderSections(RenderLevelStageEvent event, String dimension, ForgeCpuMeshCache.RenderSnapshot snapshot) {
        boolean wireframe = ForgeVoxyConfig.DEBUG_MESH_WIREFRAME.get();
        boolean ignoreDepth = ForgeVoxyConfig.DEBUG_MESH_IGNORE_DEPTH.get();
        int alpha = getConfiguredAlphaByte();
        float verticalOffset = getConfiguredVerticalOffsetBlocks();
        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder builder = tesselator.getBuilder();
        VertexFormat.Mode mode = wireframe ? VertexFormat.Mode.DEBUG_LINES : VertexFormat.Mode.TRIANGLES;
        List<ForgeCpuBuiltSection> sections = snapshot.sections();
        RenderCounters counters = new RenderCounters(snapshot);
        boolean began = false;
        try {
            builder.begin(mode, DefaultVertexFormat.POSITION_COLOR);
            began = true;

            PoseStack poseStack = event.getPoseStack();
            Vec3 cameraPos = event.getCamera().getPosition();
            poseStack.pushPose();
            try {
                poseStack.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);
                Matrix4f matrix = poseStack.last().pose();
                for (ForgeCpuBuiltSection section : sections) {
                    this.emitSection(builder, matrix, section, wireframe, alpha, verticalOffset, counters);
                }
            } finally {
                poseStack.popPose();
            }
        } catch (RuntimeException e) {
            if (began) {
                builder.discard();
            }
            throw e;
        }

        if (counters.emittedVertices == 0) {
            builder.discard();
            return counters.toFrameStats(false, "no-vertices", dimension, wireframe, ignoreDepth, alpha, verticalOffset);
        }

        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        if (ignoreDepth) {
            RenderSystem.disableDepthTest();
        } else {
            RenderSystem.enableDepthTest();
        }
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        if (wireframe) {
            RenderSystem.lineWidth(2.0F);
        }

        try {
            tesselator.end();
        } finally {
            if (wireframe) {
                RenderSystem.lineWidth(1.0F);
            }
            RenderSystem.enableCull();
            RenderSystem.enableDepthTest();
            RenderSystem.depthMask(true);
            RenderSystem.disableBlend();
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        }
        return counters.toFrameStats(true, "rendered", dimension, wireframe, ignoreDepth, alpha, verticalOffset);
    }

    private void emitSection(
            BufferBuilder builder,
            Matrix4f matrix,
            ForgeCpuBuiltSection section,
            boolean wireframe,
            int alpha,
            float verticalOffset,
            RenderCounters counters
    ) {
        if (section.layer() == ForgeCpuMeshLayer.TRANSLUCENT) {
            counters.skippedTranslucentEntries++;
            return;
        }

        ForgeCpuMeshBuffer meshBuffer = section.meshBuffer();
        if (meshBuffer == null || meshBuffer.isClosed()) {
            counters.skippedEmptyEntries++;
            return;
        }
        int[] data = meshBuffer.vertexData();
        if (data == null) {
            counters.skippedEmptyEntries++;
            return;
        }

        int color = getDebugColor(section.layer());
        int emitted = 0;
        for (int quad = 0; quad < meshBuffer.quadCount(); quad++) {
            int baseVertex = quad * 4;
            if ((baseVertex + 3) * ForgeCpuMeshBuffer.VERTEX_STRIDE_INTS + 2 >= data.length) {
                break;
            }

            if (wireframe) {
                emitted += this.emitWireQuad(builder, matrix, data, baseVertex, color, alpha, verticalOffset);
            } else {
                emitted += this.emitTriangleQuad(builder, matrix, data, baseVertex, color, alpha, verticalOffset);
            }
        }
        if (emitted == 0) {
            counters.skippedEmptyEntries++;
            return;
        }
        counters.renderedEntries++;
        counters.emittedVertices += emitted;
    }

    private int emitTriangleQuad(BufferBuilder builder, Matrix4f matrix, int[] data, int baseVertex, int color, int alpha, float verticalOffset) {
        emitVertex(builder, matrix, data, baseVertex, color, alpha, verticalOffset);
        emitVertex(builder, matrix, data, baseVertex + 1, color, alpha, verticalOffset);
        emitVertex(builder, matrix, data, baseVertex + 2, color, alpha, verticalOffset);
        emitVertex(builder, matrix, data, baseVertex, color, alpha, verticalOffset);
        emitVertex(builder, matrix, data, baseVertex + 2, color, alpha, verticalOffset);
        emitVertex(builder, matrix, data, baseVertex + 3, color, alpha, verticalOffset);
        return 6;
    }

    private int emitWireQuad(BufferBuilder builder, Matrix4f matrix, int[] data, int baseVertex, int color, int alpha, float verticalOffset) {
        emitLine(builder, matrix, data, baseVertex, baseVertex + 1, color, alpha, verticalOffset);
        emitLine(builder, matrix, data, baseVertex + 1, baseVertex + 2, color, alpha, verticalOffset);
        emitLine(builder, matrix, data, baseVertex + 2, baseVertex + 3, color, alpha, verticalOffset);
        emitLine(builder, matrix, data, baseVertex + 3, baseVertex, color, alpha, verticalOffset);
        return 8;
    }

    private static void emitLine(BufferBuilder builder, Matrix4f matrix, int[] data, int from, int to, int color, int alpha, float verticalOffset) {
        emitVertex(builder, matrix, data, from, color, alpha, verticalOffset);
        emitVertex(builder, matrix, data, to, color, alpha, verticalOffset);
    }

    private static void emitVertex(BufferBuilder builder, Matrix4f matrix, int[] data, int vertex, int color, int alpha, float verticalOffset) {
        int offset = vertex * ForgeCpuMeshBuffer.VERTEX_STRIDE_INTS;
        float x = Float.intBitsToFloat(data[offset]);
        float y = Float.intBitsToFloat(data[offset + 1]) + verticalOffset;
        float z = Float.intBitsToFloat(data[offset + 2]);
        int red = (color >> 16) & 0xFF;
        int green = (color >> 8) & 0xFF;
        int blue = color & 0xFF;
        builder.vertex(matrix, x, y, z).color(red, green, blue, alpha).endVertex();
    }

    private static int getDebugColor(ForgeCpuMeshLayer layer) {
        return switch (layer) {
            case SOLID -> SOLID_COLOR;
            case CUTOUT -> CUTOUT_COLOR;
            case TRANSLUCENT, OTHER -> OTHER_COLOR;
        };
    }

    public static int getConfiguredRenderDistanceChunks() {
        return Math.min(8, Math.max(0, ForgeVoxyConfig.DEBUG_MESH_RENDER_DISTANCE_CHUNKS.get()));
    }

    public static int getConfiguredMaxRenderedEntries() {
        return Math.min(8192, Math.max(1, ForgeVoxyConfig.DEBUG_MESH_MAX_RENDERED_ENTRIES.get()));
    }

    public static int getConfiguredAlphaByte() {
        double alpha = Math.max(0.05D, Math.min(1.0D, ForgeVoxyConfig.DEBUG_MESH_ALPHA.get()));
        return Math.max(1, Math.min(255, (int) Math.round(alpha * 255.0D)));
    }

    public static double getConfiguredAlpha() {
        return Math.max(0.05D, Math.min(1.0D, ForgeVoxyConfig.DEBUG_MESH_ALPHA.get()));
    }

    public static float getConfiguredVerticalOffsetBlocks() {
        double offset = Math.max(-2.0D, Math.min(2.0D, ForgeVoxyConfig.DEBUG_MESH_VERTICAL_OFFSET.get()));
        return (float) offset;
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
                "Voxy debug mesh render: dimension={} entries={}/{} vertices={} ignoredDepth={} alpha={} offset={} wireframe={} stage={}",
                stats.dimension(),
                stats.renderedEntries(),
                stats.candidateEntries(),
                stats.emittedVertices(),
                stats.ignoreDepth(),
                stats.alphaByte(),
                stats.verticalOffset(),
                stats.wireframe(),
                stats.stage()
        );
    }

    public record FrameStats(
            boolean rendered,
            String reason,
            String stage,
            String dimension,
            int candidateEntries,
            int renderedEntries,
            int emittedVertices,
            int skippedTranslucentEntries,
            int skippedEmptyEntries,
            int skippedByDimensionEntries,
            int skippedByDistanceEntries,
            int skippedReleasedEntries,
            int limitedEntries,
            boolean wireframe,
            boolean ignoreDepth,
            int alphaByte,
            float verticalOffset
    ) {
        private static FrameStats skipped(String reason) {
            return skipped(reason, "none");
        }

        private static FrameStats skipped(String reason, String dimension) {
            return new FrameStats(false, reason, RENDER_STAGE, dimension, 0, 0, 0, 0, 0, 0, 0, 0, 0, false, false, 0, 0.0F);
        }

        private static FrameStats skipped(String reason, String dimension, ForgeCpuMeshCache.RenderSnapshot snapshot) {
            return new FrameStats(
                    false,
                    reason,
                    RENDER_STAGE,
                    dimension,
                    snapshot.sections().size(),
                    0,
                    0,
                    0,
                    0,
                    snapshot.skippedByDimension(),
                    snapshot.skippedByDistance(),
                    snapshot.skippedReleased(),
                    snapshot.limitedEntries(),
                    false,
                    false,
                    0,
                    0.0F
            );
        }
    }

    private static final class RenderCounters {
        private final int candidateEntries;
        private final int skippedByDimensionEntries;
        private final int skippedByDistanceEntries;
        private final int skippedReleasedEntries;
        private final int limitedEntries;
        private int renderedEntries;
        private int emittedVertices;
        private int skippedTranslucentEntries;
        private int skippedEmptyEntries;

        private RenderCounters(ForgeCpuMeshCache.RenderSnapshot snapshot) {
            this.candidateEntries = snapshot.sections().size();
            this.skippedByDimensionEntries = snapshot.skippedByDimension();
            this.skippedByDistanceEntries = snapshot.skippedByDistance();
            this.skippedReleasedEntries = snapshot.skippedReleased();
            this.limitedEntries = snapshot.limitedEntries();
        }

        private FrameStats toFrameStats(boolean rendered, String reason, String dimension, boolean wireframe, boolean ignoreDepth, int alphaByte, float verticalOffset) {
            return new FrameStats(
                    rendered,
                    reason,
                    RENDER_STAGE,
                    dimension,
                    this.candidateEntries,
                    this.renderedEntries,
                    this.emittedVertices,
                    this.skippedTranslucentEntries,
                    this.skippedEmptyEntries,
                    this.skippedByDimensionEntries,
                    this.skippedByDistanceEntries,
                    this.skippedReleasedEntries,
                    this.limitedEntries,
                    wireframe,
                    ignoreDepth,
                    alphaByte,
                    verticalOffset
            );
        }
    }
}
