package me.cortex.voxy.forge;

import java.util.Arrays;

public final class ForgeVoxyGeometryBuffer implements AutoCloseable {
    public static final String PARTIAL_ORIGINAL_BIT_LAYOUT_FORMAT = ForgeVoxyQuadEncoder.GEOMETRY_FORMAT;
    public static final String FORMAL_ORIGINAL_BIT_LAYOUT_FORMAT = "formal-original-bit-layout";

    private long[] packedQuads;
    private final boolean finalRendererFormat;
    private final String geometryFormat;

    private ForgeVoxyGeometryBuffer(long[] packedQuads, boolean finalRendererFormat, String geometryFormat) {
        this.packedQuads = packedQuads;
        this.finalRendererFormat = finalRendererFormat;
        this.geometryFormat = geometryFormat;
    }

    public static ForgeVoxyGeometryBuffer partialOriginalBitLayout(long[] packedQuads) {
        return new ForgeVoxyGeometryBuffer(Arrays.copyOf(packedQuads, packedQuads.length), false, PARTIAL_ORIGINAL_BIT_LAYOUT_FORMAT);
    }

    public static ForgeVoxyGeometryBuffer formalOriginalBitLayout(long[] packedQuads) {
        return new ForgeVoxyGeometryBuffer(Arrays.copyOf(packedQuads, packedQuads.length), true, FORMAL_ORIGINAL_BIT_LAYOUT_FORMAT);
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

    public String geometryFormat() {
        return this.geometryFormat;
    }

    public boolean isClosed() {
        return this.packedQuads == null;
    }

    public boolean hasSampleRecord() {
        return this.packedQuads != null && this.packedQuads.length != 0;
    }

    public long sampleRecord() {
        return this.hasSampleRecord() ? this.packedQuads[0] : 0L;
    }

    @Override
    public void close() {
        this.packedQuads = null;
    }
}
