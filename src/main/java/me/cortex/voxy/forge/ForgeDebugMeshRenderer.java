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
    private static final int SOLID_COLOR = 0x50FF80;
    private static final int CUTOUT_COLOR = 0xFFD24A;
    private static final int OTHER_COLOR = 0xFF50FF;

    private final ForgeVoxyInstance instance;

    public ForgeDebugMeshRenderer(ForgeVoxyInstance instance) {
        this.instance = instance;
    }

    public void register() {
        MinecraftForge.EVENT_BUS.addListener(this::onRenderLevelStage);
    }

    private void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }
        if (!ForgeVoxyConfig.ENABLE_WORLD_ENGINE_SKELETON.get() || !ForgeVoxyConfig.ENABLE_DEBUG_MESH_RENDERER.get()) {
            return;
        }
        if (this.instance.getCurrentEngineOptional().isEmpty()) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null || event.getCamera() == null) {
            return;
        }

        String dimension = minecraft.level.dimension().location().toString();
        int radius = getConfiguredRenderDistanceChunks();
        int centerChunkX = minecraft.player.chunkPosition().x;
        int centerChunkZ = minecraft.player.chunkPosition().z;
        List<ForgeCpuBuiltSection> sections = this.instance.getCpuMeshCache().snapshotNear(dimension, centerChunkX, centerChunkZ, radius);
        if (sections.isEmpty()) {
            return;
        }

        try {
            this.renderSections(event, sections);
        } catch (Exception e) {
            VoxyForge.LOGGER.error("Failed to render Voxy debug CPU mesh", e);
        }
    }

    private void renderSections(RenderLevelStageEvent event, List<ForgeCpuBuiltSection> sections) {
        boolean wireframe = ForgeVoxyConfig.DEBUG_MESH_WIREFRAME.get();
        int alpha = getConfiguredAlphaByte();
        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder builder = tesselator.getBuilder();
        VertexFormat.Mode mode = wireframe ? VertexFormat.Mode.DEBUG_LINES : VertexFormat.Mode.TRIANGLES;
        int emittedVertices = 0;
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
                    emittedVertices += this.emitSection(builder, matrix, section, wireframe, alpha);
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

        if (emittedVertices == 0) {
            builder.discard();
            return;
        }

        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableDepthTest();
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
            RenderSystem.depthMask(true);
            RenderSystem.disableBlend();
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        }
    }

    private int emitSection(BufferBuilder builder, Matrix4f matrix, ForgeCpuBuiltSection section, boolean wireframe, int alpha) {
        if (section.layer() == ForgeCpuMeshLayer.TRANSLUCENT) {
            return 0;
        }

        ForgeCpuMeshBuffer meshBuffer = section.meshBuffer();
        if (meshBuffer == null || meshBuffer.isClosed()) {
            return 0;
        }
        int[] data = meshBuffer.vertexData();
        if (data == null) {
            return 0;
        }

        int color = getDebugColor(section.layer());
        int emitted = 0;
        for (int quad = 0; quad < meshBuffer.quadCount(); quad++) {
            int baseVertex = quad * 4;
            if ((baseVertex + 3) * ForgeCpuMeshBuffer.VERTEX_STRIDE_INTS + 2 >= data.length) {
                break;
            }

            if (wireframe) {
                emitted += this.emitWireQuad(builder, matrix, data, baseVertex, color, alpha);
            } else {
                emitted += this.emitTriangleQuad(builder, matrix, data, baseVertex, color, alpha);
            }
        }
        return emitted;
    }

    private int emitTriangleQuad(BufferBuilder builder, Matrix4f matrix, int[] data, int baseVertex, int color, int alpha) {
        emitVertex(builder, matrix, data, baseVertex, color, alpha);
        emitVertex(builder, matrix, data, baseVertex + 1, color, alpha);
        emitVertex(builder, matrix, data, baseVertex + 2, color, alpha);
        emitVertex(builder, matrix, data, baseVertex, color, alpha);
        emitVertex(builder, matrix, data, baseVertex + 2, color, alpha);
        emitVertex(builder, matrix, data, baseVertex + 3, color, alpha);
        return 6;
    }

    private int emitWireQuad(BufferBuilder builder, Matrix4f matrix, int[] data, int baseVertex, int color, int alpha) {
        emitLine(builder, matrix, data, baseVertex, baseVertex + 1, color, alpha);
        emitLine(builder, matrix, data, baseVertex + 1, baseVertex + 2, color, alpha);
        emitLine(builder, matrix, data, baseVertex + 2, baseVertex + 3, color, alpha);
        emitLine(builder, matrix, data, baseVertex + 3, baseVertex, color, alpha);
        return 8;
    }

    private static void emitLine(BufferBuilder builder, Matrix4f matrix, int[] data, int from, int to, int color, int alpha) {
        emitVertex(builder, matrix, data, from, color, alpha);
        emitVertex(builder, matrix, data, to, color, alpha);
    }

    private static void emitVertex(BufferBuilder builder, Matrix4f matrix, int[] data, int vertex, int color, int alpha) {
        int offset = vertex * ForgeCpuMeshBuffer.VERTEX_STRIDE_INTS;
        float x = Float.intBitsToFloat(data[offset]);
        float y = Float.intBitsToFloat(data[offset + 1]);
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

    public static int getConfiguredAlphaByte() {
        double alpha = Math.max(0.05D, Math.min(1.0D, ForgeVoxyConfig.DEBUG_MESH_ALPHA.get()));
        return Math.max(1, Math.min(255, (int) Math.round(alpha * 255.0D)));
    }
}
