package me.cortex.voxy.forge;

import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import me.cortex.voxy.common.world.WorldEngine;
import me.cortex.voxy.common.world.WorldSection;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.StampedLock;
import java.util.function.LongConsumer;

/** Original striped watch routing with explicit CPU-only delivery identities. */
final class SectionUpdateRouter implements ISectionWatcher {
    private static final int SLICES = 1 << 4;
    interface ChildUpdate { void accept(WorldSection section); }
    interface MeshUpdate { void accept(long position, long token); }
    interface VersionedChildUpdate { void accept(WorldSection section, long token); }

    private final Long2ByteOpenHashMap[] slices = new Long2ByteOpenHashMap[SLICES];
    private final Long2LongOpenHashMap[] geometryTokens = new Long2LongOpenHashMap[SLICES];
    private final Long2LongOpenHashMap[] childTokens = new Long2LongOpenHashMap[SLICES];
    private final StampedLock[] locks = new StampedLock[SLICES];
    private final AtomicLong sequence = new AtomicLong();
    private MeshUpdate initialRenderMeshGen;
    private MeshUpdate renderMeshGen;
    private VersionedChildUpdate childUpdateCallback;

    SectionUpdateRouter() {
        for (int i = 0; i < SLICES; i++) {
            this.slices[i] = new Long2ByteOpenHashMap();
            this.geometryTokens[i] = new Long2LongOpenHashMap();
            this.childTokens[i] = new Long2LongOpenHashMap();
            this.locks[i] = new StampedLock();
        }
    }

    void setCallbacks(LongConsumer initial, LongConsumer dirty, ChildUpdate child) {
        this.setVersionedCallbacks((pos, token) -> initial.accept(pos),
                (pos, token) -> dirty.accept(pos), (section, token) -> child.accept(section));
    }

    void setVersionedCallbacks(MeshUpdate initial, MeshUpdate dirty, VersionedChildUpdate child) {
        if (this.renderMeshGen != null) throw new IllegalStateException("Router callbacks already set");
        this.initialRenderMeshGen = initial;
        this.renderMeshGen = dirty;
        this.childUpdateCallback = child;
    }

    @Override
    public boolean watch(long position, int types) {
        int idx = getSliceIndex(position);
        StampedLock lock = this.locks[idx];
        int delta;
        long geometryToken = 0;
        long stamp = lock.writeLock();
        try {
            int current = Byte.toUnsignedInt(this.slices[idx].get(position));
            delta = types & ~current;
            if (delta != 0) this.slices[idx].put(position, (byte) (current | types));
            if ((delta & WorldEngine.UPDATE_TYPE_BLOCK_BIT) != 0) {
                geometryToken = this.nextToken();
                this.geometryTokens[idx].put(position, geometryToken);
            }
            if ((delta & WorldEngine.UPDATE_TYPE_CHILD_EXISTENCE_BIT) != 0) {
                this.childTokens[idx].put(position, this.nextToken());
            }
        } finally { lock.unlockWrite(stamp); }
        // Keep callbacks outside locks. Captured tokens reject delivery overtaken by edits
        // or unwatch/rewatch rather than confusing a reused position with its former owner.
        if (geometryToken != 0) this.initialRenderMeshGen.accept(position, geometryToken);
        return delta != 0;
    }

    @Override
    public boolean unwatch(long position, int types) {
        int idx = getSliceIndex(position);
        StampedLock lock = this.locks[idx];
        long stamp = lock.writeLock();
        try {
            int current = Byte.toUnsignedInt(this.slices[idx].get(position));
            if (current == 0) throw new IllegalStateException("Section pos not in map " + WorldEngine.pprintPos(position));
            int remaining = current & ~types;
            if ((types & WorldEngine.UPDATE_TYPE_BLOCK_BIT) != 0) this.geometryTokens[idx].remove(position);
            if ((types & WorldEngine.UPDATE_TYPE_CHILD_EXISTENCE_BIT) != 0) this.childTokens[idx].remove(position);
            if (remaining == 0) this.slices[idx].remove(position);
            else this.slices[idx].put(position, (byte) remaining);
            return remaining == 0;
        } finally { lock.unlockWrite(stamp); }
    }

    @Override
    public int get(long position) {
        int idx = getSliceIndex(position);
        long stamp = this.locks[idx].readLock();
        try { return Byte.toUnsignedInt(this.slices[idx].get(position)); }
        finally { this.locks[idx].unlockRead(stamp); }
    }

    void forwardEvent(WorldSection section, int type) {
        long position = section.key;
        int idx = getSliceIndex(position);
        long geometryToken = 0, childToken = 0;
        long stamp = this.locks[idx].writeLock();
        try {
            int types = this.slices[idx].get(position) & type;
            if ((types & WorldEngine.UPDATE_TYPE_CHILD_EXISTENCE_BIT) != 0) childToken = this.childTokens[idx].get(position);
            if ((types & WorldEngine.UPDATE_TYPE_BLOCK_BIT) != 0) {
                geometryToken = this.nextToken();
                this.geometryTokens[idx].put(position, geometryToken);
            }
        } finally { this.locks[idx].unlockWrite(stamp); }
        if (childToken != 0) this.childUpdateCallback.accept(section, childToken);
        if (geometryToken != 0) this.renderMeshGen.accept(position, geometryToken);
    }

    void triggerRemesh(long position) {
        int idx = getSliceIndex(position);
        long token = 0;
        long stamp = this.locks[idx].writeLock();
        try {
            if ((this.slices[idx].get(position) & WorldEngine.UPDATE_TYPE_BLOCK_BIT) != 0) {
                token = this.nextToken();
                this.geometryTokens[idx].put(position, token);
            }
        } finally { this.locks[idx].unlockWrite(stamp); }
        if (token != 0) this.renderMeshGen.accept(position, token);
    }

    long getGeometryToken(long position) { return this.getToken(position, this.geometryTokens); }
    boolean isCurrentGeometry(long position, long token) { return token != 0 && this.getGeometryToken(position) == token; }
    boolean isCurrentChild(long position, long token) { return token != 0 && this.getToken(position, this.childTokens) == token; }

    private long getToken(long position, Long2LongOpenHashMap[] tokens) {
        int idx = getSliceIndex(position);
        long stamp = this.locks[idx].readLock();
        try { return tokens[idx].get(position); }
        finally { this.locks[idx].unlockRead(stamp); }
    }

    private long nextToken() {
        long token = this.sequence.incrementAndGet();
        if (token <= 0) throw new IllegalStateException("Router delivery token exhausted");
        return token;
    }

    private static int getSliceIndex(long value) {
        value = (value ^ value >>> 30) * -4658895280553007687L;
        value = (value ^ value >>> 27) * -7723592293110705685L;
        return (int) ((value ^ value >>> 31) & (SLICES - 1));
    }
}
