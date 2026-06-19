package me.cortex.voxy.forge;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.common.MinecraftForge;
import org.joml.Matrix4f;

/**
 * Deprecated GL heap readback debug renderer.
 *
 * <p>This class is not part of the formal original-Voxy parity route. Keep it
 * isolated until command/status references are retired.</p>
 */
@Deprecated(forRemoval = false)
public final class ForgeGpuGeometryReadbackDebugRenderer {
    private static final String RENDER_STAGE = "AFTER_TRANSLUCENT_BLOCKS";

    private final ForgeVoxyInstance instance;
    private volatile FrameStats lastFrameStats = FrameStats.skipped("not-run");

    public ForgeGpuGeometryReadbackDebugRenderer(ForgeVoxyInstance instance) {
        this.instance = instance;
    }

    public void register() {
        MinecraftForge.EVENT_BUS.addListener(this::onRenderLevelStage);
    }

    public FrameStats getLastFrameStats() {
        return this.lastFrameStats;
    }

    public void clearStats() {
        this.lastFrameStats = FrameStats.skipped("cleared");
    }

    private void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }
        if (!ForgeVoxyRuntimeOverrides.enabledWorldEngineSkeleton() || !ForgeVoxyRuntimeOverrides.enableGeometryGpuVisualization()) {
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
        int radius = getConfiguredRenderDistanceChunks();
        ForgeGpuGeometryVisualizationCache.RenderSnapshot snapshot = this.instance.getGpuGeometryVisualizationCache()
                .createRenderSnapshot(dimension, centerChunkX, centerChunkZ, radius);
        if (snapshot.entries().isEmpty()) {
            this.lastFrameStats = FrameStats.skipped("cache-empty", dimension, snapshot, radius);
            return;
        }

        try {
            this.lastFrameStats = this.renderSnapshot(event, dimension, snapshot, radius);
        } catch (RuntimeException e) {
            this.lastFrameStats = FrameStats.skipped("exception", dimension);
            VoxyForge.LOGGER.error("Failed to render Voxy GL heap readback visualization", e);
        }
    }

    private FrameStats renderSnapshot(RenderLevelStageEvent event, String dimension, ForgeGpuGeometryVisualizationCache.RenderSnapshot snapshot, int radius) {
        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder builder = tesselator.getBuilder();
        int alpha = getConfiguredAlphaByte();
        boolean ignoreDepth = shouldIgnoreDepth();
        boolean doubleSided = shouldRenderDoubleSided();
        Counters counters = new Counters(snapshot, radius);
        boolean began = false;
        try {
            builder.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
            began = true;

            PoseStack poseStack = event.getPoseStack();
            Vec3 cameraPos = event.getCamera().getPosition();
            poseStack.pushPose();
            try {
                poseStack.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);
                Matrix4f matrix = poseStack.last().pose();
                for (ForgeGpuGeometryVisualizationCache.Entry entry : snapshot.entries()) {
                    counters.emitEntry(builder, matrix, entry, alpha);
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
            return counters.toFrameStats(false, "no-vertices", dimension, ignoreDepth, doubleSided, alpha);
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
        if (doubleSided) {
            RenderSystem.disableCull();
        } else {
            RenderSystem.enableCull();
        }

        try {
            tesselator.end();
        } finally {
            RenderSystem.enableCull();
            RenderSystem.enableDepthTest();
            RenderSystem.depthMask(true);
            RenderSystem.disableBlend();
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        }
        return counters.toFrameStats(true, "rendered", dimension, ignoreDepth, doubleSided, alpha);
    }

    public static int getConfiguredRenderDistanceChunks() {
        return Math.min(128, Math.max(0, ForgeVoxyRuntimeOverrides.simpleGpuMeshRenderDistanceChunks()));
    }

    public static double getConfiguredAlpha() {
        return ForgeVoxyRuntimeOverrides.geometryGpuVisualizationAlpha();
    }

    public static int getConfiguredAlphaByte() {
        return Math.max(1, Math.min(255, (int) Math.round(getConfiguredAlpha() * 255.0D)));
    }

    public static boolean shouldIgnoreDepth() {
        return ForgeVoxyRuntimeOverrides.geometryGpuVisualizationIgnoreDepth();
    }

    public static boolean shouldRenderDoubleSided() {
        return ForgeVoxyRuntimeOverrides.geometryGpuVisualizationDoubleSided();
    }

    public static String getRenderStageName() {
        return RENDER_STAGE;
    }

    private static void emitVertex(BufferBuilder builder, Matrix4f matrix, int[] data, int vertex, int alpha) {
        int offset = vertex * ForgeGpuGeometryVisualizationCache.VERTEX_STRIDE_INTS;
        float x = Float.intBitsToFloat(data[offset + ForgeGpuGeometryVisualizationCache.X_OFFSET]);
        float y = Float.intBitsToFloat(data[offset + ForgeGpuGeometryVisualizationCache.Y_OFFSET]);
        float z = Float.intBitsToFloat(data[offset + ForgeGpuGeometryVisualizationCache.Z_OFFSET]);
        int color = data[offset + ForgeGpuGeometryVisualizationCache.COLOR_OFFSET];
        int red = (color >> 16) & 0xFF;
        int green = (color >> 8) & 0xFF;
        int blue = color & 0xFF;
        builder.vertex(matrix, x, y, z).color(red, green, blue, alpha).endVertex();
    }

    public record FrameStats(
            boolean rendered,
            String reason,
            String stage,
            String dimension,
            int candidateSections,
            int renderedSections,
            int renderedQuads,
            int renderedVertices,
            int skippedDimension,
            int skippedDistance,
            int skippedEmpty,
            int renderDistanceChunks,
            boolean ignoreDepth,
            boolean doubleSided,
            int alphaByte,
            String source
    ) {
        private static FrameStats skipped(String reason) {
            return skipped(reason, "none");
        }

        private static FrameStats skipped(String reason, String dimension) {
            return new FrameStats(false, reason, RENDER_STAGE, dimension, 0, 0, 0, 0, 0, 0, 0, 0, false, false, 0, ForgeGpuGeometryVisualizationCache.SOURCE);
        }

        private static FrameStats skipped(String reason, String dimension, ForgeGpuGeometryVisualizationCache.RenderSnapshot snapshot, int radius) {
            return new FrameStats(false, reason, RENDER_STAGE, dimension, snapshot.entries().size(), 0, 0, 0, snapshot.skippedDimension(), snapshot.skippedDistance(), 0, radius, false, false, 0, ForgeGpuGeometryVisualizationCache.SOURCE);
        }
    }

    private static final class Counters {
        private final int candidateSections;
        private final int skippedDimension;
        private final int skippedDistance;
        private final int radius;
        private int renderedSections;
        private int emittedQuads;
        private int emittedVertices;
        private int skippedEmpty;

        private Counters(ForgeGpuGeometryVisualizationCache.RenderSnapshot snapshot, int radius) {
            this.candidateSections = snapshot.entries().size();
            this.skippedDimension = snapshot.skippedDimension();
            this.skippedDistance = snapshot.skippedDistance();
            this.radius = radius;
        }

        private void emitEntry(BufferBuilder builder, Matrix4f matrix, ForgeGpuGeometryVisualizationCache.Entry entry, int alpha) {
            int[] data = entry.vertexData();
            if (data == null || data.length == 0) {
                this.skippedEmpty++;
                return;
            }
            int vertices = data.length / ForgeGpuGeometryVisualizationCache.VERTEX_STRIDE_INTS;
            int emitted = 0;
            for (int vertex = 0; vertex < vertices; vertex++) {
                emitVertex(builder, matrix, data, vertex, alpha);
                emitted++;
            }
            if (emitted == 0) {
                this.skippedEmpty++;
                return;
            }
            this.renderedSections++;
            this.emittedVertices += emitted;
            this.emittedQuads += entry.quadCount();
        }

        private FrameStats toFrameStats(boolean rendered, String reason, String dimension, boolean ignoreDepth, boolean doubleSided, int alphaByte) {
            return new FrameStats(
                    rendered,
                    reason,
                    RENDER_STAGE,
                    dimension,
                    this.candidateSections,
                    this.renderedSections,
                    this.emittedQuads,
                    this.emittedVertices,
                    this.skippedDimension,
                    this.skippedDistance,
                    this.skippedEmpty,
                    this.radius,
                    ignoreDepth,
                    doubleSided,
                    alphaByte,
                    ForgeGpuGeometryVisualizationCache.SOURCE
            );
        }
    }
}
