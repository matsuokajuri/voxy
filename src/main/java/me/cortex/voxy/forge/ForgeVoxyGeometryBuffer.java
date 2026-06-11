package me.cortex.voxy.forge;

import java.util.Arrays;

public final class ForgeVoxyGeometryBuffer implements AutoCloseable {
    private long[] packedQuads;
    private final boolean finalRendererFormat;

    private ForgeVoxyGeometryBuffer(long[] packedQuads, boolean finalRendererFormat) {
        this.packedQuads = packedQuads;
        this.finalRendererFormat = finalRendererFormat;
    }

    public static ForgeVoxyGeometryBuffer partial(long[] packedQuads) {
        return new ForgeVoxyGeometryBuffer(Arrays.copyOf(packedQuads, packedQuads.length), false);
    }

    public long[] packedQuads() {
        return this.packedQuads;
    }

    public int quadCount() {
        return this.packedQuads == null ? 0 : this.packedQuads.length;
    }

    public long sizeBytes() {
        return (long) this.quadCount() * Long.BYTES;
    }

    public boolean isFinalRendererFormat() {
        return this.finalRendererFormat;
    }

    public boolean isClosed() {
        return this.packedQuads == null;
    }

    @Override
    public void close() {
        this.packedQuads = null;
    }
}
