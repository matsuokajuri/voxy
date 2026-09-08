package me.cortex.voxy.forge;

import me.cortex.voxy.common.util.MemoryBuffer;

import java.util.Arrays;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;

final class BuiltSection {
    static final boolean VERIFY_BUILT_SECTION_OFFSETS =
            System.getProperty("voxy.verifyBuiltSectionOffsets", "false").equals("true");
    final long position;
    final byte childExistence;
    final int aabb;
    final MemoryBuffer geometryBuffer;
    final int[] offsets;
    final MemoryBuffer occupancy;
    long cacheEpoch = Long.MIN_VALUE;
    long watchToken;
    private volatile boolean released;
    private static final VarHandle RELEASED;

    static {
        try {
            RELEASED = MethodHandles.lookup().findVarHandle(BuiltSection.class, "released", boolean.class);
        } catch (ReflectiveOperationException failure) {
            throw new ExceptionInInitializerError(failure);
        }
    }

    private BuiltSection(long position, byte children) {
        this(position, children, -1, null, null, null);
    }

    static BuiltSection empty(long position) {
        return new BuiltSection(position, (byte) 0);
    }

    static BuiltSection emptyWithChildren(long position, byte children) {
        return new BuiltSection(position, children);
    }

    BuiltSection(long position, byte childExistence, int aabb, MemoryBuffer geometryBuffer, int[] offsets, MemoryBuffer occupancy) {
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

    @Override
    public BuiltSection clone() {
        this.requireOwned();
        return new BuiltSection(
                this.position,
                this.childExistence,
                this.aabb,
                this.geometryBuffer != null ? this.geometryBuffer.copy() : null,
                this.offsets != null ? Arrays.copyOf(this.offsets, this.offsets.length) : null,
                this.occupancy != null ? this.occupancy.copy() : null
        ).withCacheEpoch(this.cacheEpoch).withWatchToken(this.watchToken);
    }

    BuiltSection withCacheEpoch(long cacheEpoch) {
        this.requireOwned();
        this.cacheEpoch = cacheEpoch;
        return this;
    }

    void free() {
        this.claimRelease();
        try {
            if (this.geometryBuffer != null) this.geometryBuffer.free();
        } finally {
            if (this.occupancy != null) this.occupancy.free();
        }
    }

    BuiltSection withWatchToken(long watchToken) {
        this.requireOwned();
        this.watchToken = watchToken;
        return this;
    }

    /** Transfers the vertex buffer to the upload owner and releases unused auxiliary memory. */
    MemoryBuffer detachGeometryBuffer() {
        this.claimRelease();
        try {
            if (this.occupancy != null) this.occupancy.free();
        } catch (RuntimeException | Error failure) {
            if (this.geometryBuffer != null) {
                try { this.geometryBuffer.free(); } catch (RuntimeException | Error cleanup) { failure.addSuppressed(cleanup); }
            }
            throw failure;
        }
        return this.geometryBuffer;
    }

    boolean isReleased() { return this.released; }

    private void claimRelease() {
        if (!RELEASED.compareAndSet(this, false, true)) {
            throw new IllegalStateException("BuiltSection ownership already transferred or released");
        }
    }

    private void requireOwned() {
        if (this.released) throw new IllegalStateException("BuiltSection ownership already transferred or released");
    }

    boolean isEmpty() {
        return this.geometryBuffer == null;
    }

}
