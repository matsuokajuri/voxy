package me.cortex.voxy.forge;

import me.cortex.voxy.common.util.MemoryBuffer;

import java.util.Arrays;

final class ForgeOriginalVoxyBuiltSection {
    static final boolean VERIFY_BUILT_SECTION_OFFSETS = Boolean.getBoolean("voxy.verifyBuiltSectionOffsets");
    final long position;
    final byte childExistence;
    final int aabb;
    final MemoryBuffer geometryBuffer;
    final int[] offsets;
    final MemoryBuffer occupancy;

    private ForgeOriginalVoxyBuiltSection(long position, byte children) {
        this(position, children, -1, null, null, null);
    }

    static ForgeOriginalVoxyBuiltSection empty(long position) {
        return new ForgeOriginalVoxyBuiltSection(position, (byte) 0);
    }

    static ForgeOriginalVoxyBuiltSection emptyWithChildren(long position, byte children) {
        return new ForgeOriginalVoxyBuiltSection(position, children);
    }

    ForgeOriginalVoxyBuiltSection(long position, byte childExistence, int aabb, MemoryBuffer geometryBuffer, int[] offsets, MemoryBuffer occupancy) {
        this.position = position;
        this.childExistence = childExistence;
        this.aabb = aabb;
        this.geometryBuffer = geometryBuffer;
        this.offsets = offsets;
        if (offsets != null && VERIFY_BUILT_SECTION_OFFSETS) {
            for (int i = 0; i < offsets.length - 1; i++) {
                int delta = offsets[i + 1] - offsets[i];
                if (delta < 0 || delta >= (1 << 16)) {
                    throw new IllegalArgumentException("Offsets out of range");
                }
            }
        }
        this.occupancy = occupancy;
    }

    ForgeOriginalVoxyBuiltSection cloneSection() {
        return new ForgeOriginalVoxyBuiltSection(
                this.position,
                this.childExistence,
                this.aabb,
                this.geometryBuffer != null ? this.geometryBuffer.copy() : null,
                this.offsets != null ? Arrays.copyOf(this.offsets, this.offsets.length) : null,
                this.occupancy != null ? this.occupancy.copy() : null
        );
    }

    void free() {
        if (this.geometryBuffer != null) {
            this.geometryBuffer.free();
        }
        if (this.occupancy != null) {
            this.occupancy.free();
        }
    }

    boolean isEmpty() {
        return this.geometryBuffer == null;
    }

    int recordCount() {
        return this.geometryBuffer == null ? 0 : (int) (this.geometryBuffer.size / Long.BYTES);
    }
}
