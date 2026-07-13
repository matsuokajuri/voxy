package me.cortex.voxy.forge;

import it.unimi.dsi.fastutil.ints.Int2ObjectFunction;
import me.cortex.voxy.common.util.HierarchicalBitSet;

final class ExpandingObjectAllocationList<T> {
    private static final float GROWTH_FACTOR = 0.75f;

    private final Int2ObjectFunction<T[]> arrayGenerator;
    private final HierarchicalBitSet bitSet;
    private T[] objects;

    ExpandingObjectAllocationList(Int2ObjectFunction<T[]> arrayGenerator, int limit) {
        this.arrayGenerator = arrayGenerator;
        this.objects = this.arrayGenerator.apply(16);
        this.bitSet = new HierarchicalBitSet(limit);
    }

    int put(T obj) {
        int id = this.bitSet.allocateNext();
        if (id < 0) {
            throw new IllegalStateException("Id over max request capacity");
        }
        if (this.objects.length <= id) {
            int newLen = this.objects.length + (int) Math.ceil(this.objects.length * GROWTH_FACTOR);
            T[] newArr = this.arrayGenerator.apply(newLen);
            System.arraycopy(this.objects, 0, newArr, 0, this.objects.length);
            this.objects = newArr;
        }
        this.objects[id] = obj;
        return id;
    }

    void release(int id) {
        if (!this.bitSet.free(id)) {
            throw new IllegalArgumentException("Index " + id + " was already released");
        }
        this.objects[id] = null;
    }

    T get(int index) {
        if (!this.bitSet.isSet(index)) {
            throw new IllegalArgumentException("Index " + index + " is not allocated");
        }
        return this.objects[index];
    }

    int count() {
        return this.bitSet.getCount();
    }
}
