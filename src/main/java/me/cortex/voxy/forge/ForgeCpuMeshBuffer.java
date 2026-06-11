package me.cortex.voxy.forge;

import java.util.Arrays;

public final class ForgeCpuMeshBuffer implements AutoCloseable {
    public static final int X_OFFSET = 0;
    public static final int Y_OFFSET = 1;
    public static final int Z_OFFSET = 2;
    public static final int COLOR_OFFSET = 3;
    public static final int U_OFFSET = 4;
    public static final int V_OFFSET = 5;
    public static final int LIGHT_OFFSET = 6;
    public static final int NORMAL_OFFSET = 7;
    public static final int LAYER_OFFSET = 8;
    public static final int TINT_INDEX_OFFSET = 9;
    public static final int BLOCK_ID_OFFSET = 10;
    public static final int BIOME_ID_OFFSET = 11;
    public static final int VERTEX_STRIDE_INTS = 12;
    public static final int VERTEX_STRIDE_BYTES = VERTEX_STRIDE_INTS * Integer.BYTES;

    private int[] vertexData;
    private final int vertexCount;
    private final int quadCount;

    private ForgeCpuMeshBuffer(int[] vertexData, int vertexCount, int quadCount) {
        this.vertexData = vertexData;
        this.vertexCount = vertexCount;
        this.quadCount = quadCount;
    }

    public int[] vertexData() {
        return this.vertexData;
    }

    public int vertexCount() {
        return this.vertexCount;
    }

    public int quadCount() {
        return this.quadCount;
    }

    public long sizeBytes() {
        return (long) this.vertexCount * VERTEX_STRIDE_BYTES;
    }

    public boolean isClosed() {
        return this.vertexData == null;
    }

    @Override
    public void close() {
        this.vertexData = null;
    }

    public static final class Builder {
        private int[] vertexData = new int[VERTEX_STRIDE_INTS * 256];
        private int vertexCount;

        public void putVertex(
                float x,
                float y,
                float z,
                int color,
                float u,
                float v,
                int light,
                int normal,
                int layer,
                int tintIndex,
                int blockId,
                int biomeId
        ) {
            this.ensureVertexCapacity(this.vertexCount + 1);
            int offset = this.vertexCount * VERTEX_STRIDE_INTS;
            this.vertexData[offset + X_OFFSET] = Float.floatToRawIntBits(x);
            this.vertexData[offset + Y_OFFSET] = Float.floatToRawIntBits(y);
            this.vertexData[offset + Z_OFFSET] = Float.floatToRawIntBits(z);
            this.vertexData[offset + COLOR_OFFSET] = color;
            this.vertexData[offset + U_OFFSET] = Float.floatToRawIntBits(u);
            this.vertexData[offset + V_OFFSET] = Float.floatToRawIntBits(v);
            this.vertexData[offset + LIGHT_OFFSET] = light;
            this.vertexData[offset + NORMAL_OFFSET] = normal;
            this.vertexData[offset + LAYER_OFFSET] = layer;
            this.vertexData[offset + TINT_INDEX_OFFSET] = tintIndex;
            this.vertexData[offset + BLOCK_ID_OFFSET] = blockId;
            this.vertexData[offset + BIOME_ID_OFFSET] = biomeId;
            this.vertexCount++;
        }

        public int vertexCount() {
            return this.vertexCount;
        }

        public int quadCount() {
            return this.vertexCount / 4;
        }

        public long sizeBytes() {
            return (long) this.vertexCount * VERTEX_STRIDE_BYTES;
        }

        public ForgeCpuMeshBuffer build() {
            int used = this.vertexCount * VERTEX_STRIDE_INTS;
            return new ForgeCpuMeshBuffer(Arrays.copyOf(this.vertexData, used), this.vertexCount, this.quadCount());
        }

        private void ensureVertexCapacity(int requestedVertices) {
            int requiredInts = requestedVertices * VERTEX_STRIDE_INTS;
            if (requiredInts <= this.vertexData.length) {
                return;
            }

            int nextLength = this.vertexData.length;
            while (nextLength < requiredInts) {
                nextLength *= 2;
            }
            this.vertexData = Arrays.copyOf(this.vertexData, nextLength);
        }
    }
}
