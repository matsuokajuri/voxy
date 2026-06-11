package me.cortex.voxy.forge;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;

public final class ForgeGpuMeshBuffer implements AutoCloseable {
    private static final int TRIANGLE_VERTICES_PER_QUAD = 6;
    private static final int SOLID_DEBUG_COLOR = 0xFF00FF;
    private static final int CUTOUT_DEBUG_COLOR = 0x00FFFF;
    private static final int OTHER_DEBUG_COLOR = 0xFFFF00;

    private final ForgeCpuMeshCache.Key key;
    private final long sourceHash;
    private final int colorModeStamp;
    private final int vertexCount;
    private final int quadCount;
    private final long sizeBytes;
    private VertexBuffer vertexBuffer;

    private ForgeGpuMeshBuffer(
            ForgeCpuMeshCache.Key key,
            long sourceHash,
            int colorModeStamp,
            int vertexCount,
            int quadCount,
            long sizeBytes,
            VertexBuffer vertexBuffer
    ) {
        this.key = key;
        this.sourceHash = sourceHash;
        this.colorModeStamp = colorModeStamp;
        this.vertexCount = vertexCount;
        this.quadCount = quadCount;
        this.sizeBytes = sizeBytes;
        this.vertexBuffer = vertexBuffer;
    }

    public static ForgeGpuMeshBuffer upload(ForgeCpuBuiltSection section, boolean useOriginalColors) {
        if (!RenderSystem.isOnRenderThread()) {
            throw new IllegalStateException("Simple GPU mesh upload must run on the render thread");
        }
        if (section.layer() == ForgeCpuMeshLayer.TRANSLUCENT) {
            throw new IllegalArgumentException("The simple GPU renderer does not upload translucent mesh entries yet");
        }

        ForgeCpuMeshBuffer meshBuffer = section.meshBuffer();
        if (meshBuffer == null || meshBuffer.isClosed() || meshBuffer.vertexCount() == 0) {
            throw new IllegalArgumentException("Cannot upload an empty CPU mesh entry");
        }

        int quadCount = meshBuffer.quadCount();
        int emittedVertices = quadCount * TRIANGLE_VERTICES_PER_QUAD;
        BufferBuilder builder = new BufferBuilder(Math.max(256, emittedVertices * 16));
        builder.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);

        int[] data = meshBuffer.vertexData();
        for (int quad = 0; quad < quadCount; quad++) {
            int baseVertex = quad * 4;
            emitTriangleQuad(builder, data, baseVertex, section.layer(), useOriginalColors);
        }

        VertexBuffer vertexBuffer = new VertexBuffer(VertexBuffer.Usage.STATIC);
        vertexBuffer.bind();
        vertexBuffer.upload(builder.end());
        VertexBuffer.unbind();

        return new ForgeGpuMeshBuffer(
                ForgeCpuMeshCache.Key.from(section),
                section.sourceHash(),
                colorModeStamp(useOriginalColors),
                emittedVertices,
                quadCount,
                (long) emittedVertices * 16L,
                vertexBuffer
        );
    }

    public static int colorModeStamp(boolean useOriginalColors) {
        return useOriginalColors ? 1 : 2;
    }

    private static void emitTriangleQuad(BufferBuilder builder, int[] data, int baseVertex, ForgeCpuMeshLayer layer, boolean useOriginalColors) {
        emitVertex(builder, data, baseVertex, layer, useOriginalColors);
        emitVertex(builder, data, baseVertex + 1, layer, useOriginalColors);
        emitVertex(builder, data, baseVertex + 2, layer, useOriginalColors);
        emitVertex(builder, data, baseVertex, layer, useOriginalColors);
        emitVertex(builder, data, baseVertex + 2, layer, useOriginalColors);
        emitVertex(builder, data, baseVertex + 3, layer, useOriginalColors);
    }

    private static void emitVertex(BufferBuilder builder, int[] data, int vertex, ForgeCpuMeshLayer layer, boolean useOriginalColors) {
        int offset = vertex * ForgeCpuMeshBuffer.VERTEX_STRIDE_INTS;
        float x = Float.intBitsToFloat(data[offset]);
        float y = Float.intBitsToFloat(data[offset + 1]);
        float z = Float.intBitsToFloat(data[offset + 2]);
        int color = useOriginalColors ? data[offset + 3] : getDebugColor(layer);
        int alpha = (color >>> 24) & 0xFF;
        if (alpha == 0) {
            alpha = 0xFF;
        }
        int red = (color >>> 16) & 0xFF;
        int green = (color >>> 8) & 0xFF;
        int blue = color & 0xFF;
        builder.vertex(x, y, z).color(red, green, blue, alpha).endVertex();
    }

    private static int getDebugColor(ForgeCpuMeshLayer layer) {
        return switch (layer) {
            case SOLID -> SOLID_DEBUG_COLOR;
            case CUTOUT -> CUTOUT_DEBUG_COLOR;
            case TRANSLUCENT, OTHER -> OTHER_DEBUG_COLOR;
        };
    }

    public ForgeCpuMeshCache.Key key() {
        return this.key;
    }

    public String dimension() {
        return this.key.dimension();
    }

    public int chunkX() {
        return this.key.chunkX();
    }

    public int chunkZ() {
        return this.key.chunkZ();
    }

    public ForgeCpuMeshLayer layer() {
        return this.key.layer();
    }

    public long sourceHash() {
        return this.sourceHash;
    }

    public int colorModeStamp() {
        return this.colorModeStamp;
    }

    public int vertexCount() {
        return this.vertexCount;
    }

    public int quadCount() {
        return this.quadCount;
    }

    public long sizeBytes() {
        return this.sizeBytes;
    }

    public VertexBuffer vertexBuffer() {
        return this.vertexBuffer;
    }

    public boolean isClosed() {
        return this.vertexBuffer == null;
    }

    @Override
    public void close() {
        VertexBuffer buffer = this.vertexBuffer;
        if (buffer == null) {
            return;
        }
        this.vertexBuffer = null;
        if (RenderSystem.isOnRenderThread()) {
            buffer.close();
        } else {
            RenderSystem.recordRenderCall(buffer::close);
        }
    }
}
