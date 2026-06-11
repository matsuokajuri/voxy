package me.cortex.voxy.forge;

import java.util.Arrays;

public final class ForgeCpuMeshBuffer implements AutoCloseable {
    public static final int VERTEX_STRIDE_INTS = 10;
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
                int tintIndex
        ) {
            this.ensureVertexCapacity(this.vertexCount + 1);
            int offset = this.vertexCount * VERTEX_STRIDE_INTS;
            this.vertexData[offset] = Float.floatToRawIntBits(x);
            this.vertexData[offset + 1] = Float.floatToRawIntBits(y);
            this.vertexData[offset + 2] = Float.floatToRawIntBits(z);
            this.vertexData[offset + 3] = color;
            this.vertexData[offset + 4] = Float.floatToRawIntBits(u);
            this.vertexData[offset + 5] = Float.floatToRawIntBits(v);
            this.vertexData[offset + 6] = light;
            this.vertexData[offset + 7] = normal;
            this.vertexData[offset + 8] = layer;
            this.vertexData[offset + 9] = tintIndex;
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
