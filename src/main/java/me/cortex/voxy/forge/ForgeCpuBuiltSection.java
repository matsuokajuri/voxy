package me.cortex.voxy.forge;

public final class ForgeCpuBuiltSection implements AutoCloseable {
    private final String dimension;
    private final int chunkX;
    private final int chunkZ;
    private final long sectionPosition;
    private final ForgeCpuMeshLayer layer;
    private final long sourceHash;
    private final long createdTimeMillis;
    private final double buildElapsedMs;
    private ForgeCpuMeshBuffer meshBuffer;

    public ForgeCpuBuiltSection(
            String dimension,
            int chunkX,
            int chunkZ,
            long sectionPosition,
            ForgeCpuMeshLayer layer,
            long sourceHash,
            long createdTimeMillis,
            double buildElapsedMs,
            ForgeCpuMeshBuffer meshBuffer
    ) {
        this.dimension = dimension;
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        this.sectionPosition = sectionPosition;
        this.layer = layer;
        this.sourceHash = sourceHash;
        this.createdTimeMillis = createdTimeMillis;
        this.buildElapsedMs = buildElapsedMs;
        this.meshBuffer = meshBuffer;
    }

    public String dimension() {
        return this.dimension;
    }

    public int chunkX() {
        return this.chunkX;
    }

    public int chunkZ() {
        return this.chunkZ;
    }

    public long sectionPosition() {
        return this.sectionPosition;
    }

    public ForgeCpuMeshLayer layer() {
        return this.layer;
    }

    public long sourceHash() {
        return this.sourceHash;
    }

    public long createdTimeMillis() {
        return this.createdTimeMillis;
    }

    public double buildElapsedMs() {
        return this.buildElapsedMs;
    }

    public ForgeCpuMeshBuffer meshBuffer() {
        return this.meshBuffer;
    }

    public int vertexCount() {
        return this.meshBuffer == null ? 0 : this.meshBuffer.vertexCount();
    }

    public int quadCount() {
        return this.meshBuffer == null ? 0 : this.meshBuffer.quadCount();
    }

    public long sizeBytes() {
        return this.meshBuffer == null ? 0 : this.meshBuffer.sizeBytes();
    }

    @Override
    public void close() {
        if (this.meshBuffer != null) {
            this.meshBuffer.close();
            this.meshBuffer = null;
        }
    }
}
