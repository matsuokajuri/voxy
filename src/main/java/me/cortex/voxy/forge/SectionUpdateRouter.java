package me.cortex.voxy.forge;

import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import me.cortex.voxy.common.world.WorldEngine;
import me.cortex.voxy.common.world.WorldSection;

import java.util.concurrent.locks.StampedLock;
import java.util.function.LongConsumer;

final class SectionUpdateRouter implements ISectionWatcher {
    private static final int SLICES = 1 << 4;

    interface ChildUpdate {
        void accept(WorldSection section);
    }

    private final Long2ByteOpenHashMap[] slices = new Long2ByteOpenHashMap[SLICES];
    private final StampedLock[] locks = new StampedLock[SLICES];
    private LongConsumer initialRenderMeshGen;
    private LongConsumer renderMeshGen;
    private ChildUpdate childUpdateCallback;

    SectionUpdateRouter() {
        for (int i = 0; i < this.slices.length; i++) {
            this.slices[i] = new Long2ByteOpenHashMap();
            this.locks[i] = new StampedLock();
        }
    }

    void setCallbacks(LongConsumer initialRenderMeshGen, LongConsumer renderMeshGen, ChildUpdate childUpdateCallback) {
        if (this.renderMeshGen != null) {
            throw new IllegalStateException();
        }
        this.initialRenderMeshGen = initialRenderMeshGen;
        this.renderMeshGen = renderMeshGen;
        this.childUpdateCallback = childUpdateCallback;
    }

    @Override
    public boolean watch(long position, int types) {
        int idx = getSliceIndex(position);
        Long2ByteOpenHashMap set = this.slices[idx];
        StampedLock lock = this.locks[idx];
        byte delta;
        long stamp = lock.readLock();
        byte current = set.getOrDefault(position, (byte) 0);
        delta = (byte) (current & types);
        current |= (byte) types;
        delta ^= (byte) (current & types);
        if (delta != 0) {
            long writeStamp = lock.tryConvertToWriteLock(stamp);
            if (writeStamp == 0) {
                lock.unlockRead(stamp);
                stamp = lock.writeLock();
                current = set.getOrDefault(position, (byte) 0);
                delta = (byte) (current & types);
                current |= (byte) types;
                delta ^= (byte) (current & types);
                if (delta != 0) {
                    set.put(position, current);
                }
            } else {
                stamp = writeStamp;
                set.put(position, current);
            }
        }
        lock.unlock(stamp);
        if (((delta & types) & WorldEngine.UPDATE_TYPE_BLOCK_BIT) != 0) {
            this.initialRenderMeshGen.accept(position);
        }
        return delta != 0;
    }

    @Override
    public boolean unwatch(long position, int types) {
        int idx = getSliceIndex(position);
        Long2ByteOpenHashMap set = this.slices[idx];
        StampedLock lock = this.locks[idx];
        long stamp = lock.readLock();
        byte current = set.getOrDefault(position, (byte) 0);
        if (current == 0) {
            throw new IllegalStateException("Section pos not in map " + WorldEngine.pprintPos(position));
        }
        boolean removed = false;
        if ((current & types) != 0) {
            long writeStamp = lock.tryConvertToWriteLock(stamp);
            if (writeStamp == 0) {
                lock.unlockRead(stamp);
                stamp = lock.writeLock();
                current = set.getOrDefault(position, (byte) 0);
                if (current == 0) {
                    throw new IllegalStateException("Section pos not in map " + WorldEngine.pprintPos(position));
                }
            } else {
                stamp = writeStamp;
            }
            if ((current & types) != 0) {
                current &= (byte) ~types;
                if (current == 0) {
                    set.remove(position);
                    removed = true;
                } else {
                    set.put(position, current);
                }
            }
        }
        lock.unlock(stamp);
        return removed;
    }

    @Override
    public int get(long position) {
        int idx = getSliceIndex(position);
        Long2ByteOpenHashMap set = this.slices[idx];
        StampedLock lock = this.locks[idx];
        long stamp = lock.readLock();
        int ret = set.getOrDefault(position, (byte) 0);
        lock.unlockRead(stamp);
        return ret;
    }

    void forwardEvent(WorldSection section, int type) {
        long position = section.key;
        int idx = getSliceIndex(position);
        Long2ByteOpenHashMap set = this.slices[idx];
        StampedLock lock = this.locks[idx];
        long stamp = lock.readLock();
        byte types = (byte) (set.getOrDefault(position, (byte) 0) & type);
        lock.unlockRead(stamp);

        if (types != 0) {
            if ((types & WorldEngine.UPDATE_TYPE_CHILD_EXISTENCE_BIT) != 0) {
                this.childUpdateCallback.accept(section);
            }
            if ((types & WorldEngine.UPDATE_TYPE_BLOCK_BIT) != 0) {
                this.renderMeshGen.accept(section.key);
            }
        }
    }

    void triggerRemesh(long position) {
        int idx = getSliceIndex(position);
        Long2ByteOpenHashMap set = this.slices[idx];
        StampedLock lock = this.locks[idx];
        long stamp = lock.readLock();
        byte types = set.getOrDefault(position, (byte) 0);
        lock.unlockRead(stamp);
        if ((types & WorldEngine.UPDATE_TYPE_BLOCK_BIT) != 0) {
            this.renderMeshGen.accept(position);
        }
    }

    private static int getSliceIndex(long value) {
        value = (value ^ value >>> 30) * -4658895280553007687L;
        value = (value ^ value >>> 27) * -7723592293110705685L;
        return (int) ((value ^ value >>> 31) & (SLICES - 1));
    }
}
