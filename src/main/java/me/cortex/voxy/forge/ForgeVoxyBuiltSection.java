package me.cortex.voxy.forge;

import java.util.Arrays;

public final class ForgeVoxyBuiltSection implements AutoCloseable {
    private static final boolean VERIFY_OFFSETS = Boolean.getBoolean("voxy.forge.verifyBuiltSectionOffsets");

    private final String dimension;
    private final int chunkX;
    private final int chunkZ;
    private final long position;
    private final byte childExistence;
    private final int aabb;
    private final int[] offsets;
    private final byte[] occupancy;
    private final long createdTimeMillis;
    private ForgeVoxyGeometryBuffer geometryBuffer;
    private boolean closed;

    public ForgeVoxyBuiltSection(
            String dimension,
            int chunkX,
            int chunkZ,
            long position,
            byte childExistence,
            int aabb,
            int[] offsets,
            ForgeVoxyGeometryBuffer geometryBuffer,
            byte[] occupancy,
            long createdTimeMillis
    ) {
        this.dimension = dimension;
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        this.position = position;
        this.childExistence = childExistence;
        this.aabb = aabb;
        this.offsets = offsets == null ? null : Arrays.copyOf(offsets, offsets.length);
        this.geometryBuffer = geometryBuffer;
        this.occupancy = occupancy == null ? null : Arrays.copyOf(occupancy, occupancy.length);
        this.createdTimeMillis = createdTimeMillis;
        this.verifyOffsets();
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

    public long position() {
        return this.position;
    }

    public byte childExistence() {
        return this.childExistence;
    }

    public int aabb() {
        return this.aabb;
    }

    public int[] offsets() {
        return this.offsets == null ? null : Arrays.copyOf(this.offsets, this.offsets.length);
    }

    public ForgeVoxyGeometryBuffer geometryBuffer() {
        return this.geometryBuffer;
    }

    public int occupancyBytes() {
        return this.occupancy == null ? 0 : this.occupancy.length;
    }

    public boolean occupancyPresent() {
        return this.occupancy != null && this.occupancy.length != 0;
    }

    public long createdTimeMillis() {
        return this.createdTimeMillis;
    }

    public int quadCount() {
        return this.geometryBuffer == null ? 0 : this.geometryBuffer.quadCount();
    }

    public long geometryBytes() {
        return this.geometryBuffer == null ? 0 : this.geometryBuffer.sizeBytes();
    }

    public boolean isClosed() {
        return this.closed;
    }

    public boolean isEmpty() {
        return this.geometryBuffer == null || this.geometryBuffer.quadCount() == 0;
    }

    public boolean isFinalRendererFormat() {
        return this.geometryBuffer != null && this.geometryBuffer.isFinalRendererFormat();
    }

    public String geometryFormat() {
        return this.geometryBuffer == null ? "none" : this.geometryBuffer.geometryFormat();
    }

    @Override
    public void close() {
        if (this.closed) {
            return;
        }
        this.closed = true;
        if (this.geometryBuffer != null) {
            this.geometryBuffer.close();
            this.geometryBuffer = null;
        }
    }

    private void verifyOffsets() {
        if (!VERIFY_OFFSETS || this.offsets == null) {
            return;
        }
        if (this.offsets.length != 8) {
            throw new IllegalArgumentException("BuiltSection offsets must contain 8 buckets");
        }
        for (int i = 0; i < this.offsets.length - 1; i++) {
            int delta = this.offsets[i + 1] - this.offsets[i];
            if (delta < 0 || delta >= (1 << 16)) {
                throw new IllegalArgumentException("BuiltSection offsets out of range");
            }
        }
    }
}
