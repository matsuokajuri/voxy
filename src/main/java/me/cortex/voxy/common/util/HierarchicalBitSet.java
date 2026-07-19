package me.cortex.voxy.common.util;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;

import java.util.Random;

public class HierarchicalBitSet {
    public static final int SET_FULL = -1;
    public static final int LIMIT_REACHED = -2;
    private final int limit;
    private int cnt;
    //If a bit is 1 it means all children are also set
    private long A = 0;
    private final long[] B = new long[64];
    private final long[] C = new long[64*64];
    private final long[] D = new long[64*64*64];
    public HierarchicalBitSet(int limit) {//Fixed size of 64^4
        this.limit = limit;
        if (limit > (1<<(6*4))) {
            throw new IllegalArgumentException("Limit greater than capacity");
        }
    }

    private int endId = -1;

    public HierarchicalBitSet() {
        this(1<<(6*4));
    }

    public int allocateNext() {
        if (this.A==-1) {
            return -1;
        }
        if (this.cnt+1>this.limit) {
            return -1;//Limit reached
        }
        int idx = Long.numberOfTrailingZeros(~this.A);
        long bp = this.B[idx];
        idx = Long.numberOfTrailingZeros(~bp) + 64*idx;
        long cp = this.C[idx];
        idx = Long.numberOfTrailingZeros(~cp) + 64*idx;
        long dp = this.D[idx];
        idx =  Long.numberOfTrailingZeros(~dp) + 64*idx;
        int ret = idx;

        //if (this.isSet(ret)) {
        //    throw new IllegalStateException();
        //}

        dp |= 1L<<(idx&0x3f);
        this.D[idx>>6] = dp;
        if (dp==-1) {
            idx >>= 6;
            cp |= 1L<<(idx&0x3f);
            this.C[idx>>6] = cp;
            if (cp==-1) {
                idx >>= 6;
                bp |= 1L<<(idx&0x3f);
                this.B[idx>>6] = bp;
                if (bp==-1) {
                    idx >>= 6;
                    this.A |= 1L<<(idx&0x3f);
                }
            }
        }
        this.cnt++;
        this.endId += ret==(this.endId+1)?1:0;
        return ret;
    }

    private void set(int idx) {
        //if (this.isSet(idx)) {
        //    throw new IllegalStateException();
        //}

        this.endId += idx==(this.endId+1)?1:0;
        long dp = this.D[idx>>6] |= 1L<<(idx&0x3f);
        if (dp==-1) {
            idx >>= 6;
            long cp = (this.C[idx>>6] |= 1L<<(idx&0x3f));
            if (cp==-1) {
                idx >>= 6;
                long bp = this.B[idx>>6] |= 1L<<(idx&0x3f);
                if (bp==-1) {
                    idx >>= 6;
                    this.A |= 1L<<(idx&0x3f);
                }
            }
        }
        this.cnt++;
    }

    //Returns the next free index from idx
    private int findNextFree(int idx) {
        if (idx < 0) {
            throw new IllegalArgumentException("Negative bit index");
        }
        if (idx >= this.limit) {
            return this.limit;
        }
        int wordIndex = idx >>> 6;
        long available = ~this.D[wordIndex] & (-1L << (idx & 63));
        while (available == 0L) {
            wordIndex++;
            if ((wordIndex << 6) >= this.limit) {
                return this.limit;
            }
            available = ~this.D[wordIndex];
        }
        int result = (wordIndex << 6) + Long.numberOfTrailingZeros(available);
        return Math.min(result, this.limit);
    }


    public int allocateNextConsecutiveCounted(int count) {
        if (count <= 0 || count > 64) {
            throw new IllegalArgumentException("Count must be between 1 and 64");
        }
        if (this.A==-1) {
            return SET_FULL;
        }
        if (this.cnt + count > this.limit) {
            return LIMIT_REACHED;
        }
        long chkMsk = count == 64 ? -1L : (1L << count) - 1L;
        int i = this.findNextFree(0);
        while (i + count <= this.limit) {
            long fusedValue = this.D[i>>6]>>>(i&63);
            if (64-(i&63) < count) {
                fusedValue |= this.D[(i>>6)+1] << (64-(i&63));
            }

            if ((fusedValue&chkMsk) != 0) {
                i = this.findNextFree(i + Long.numberOfTrailingZeros(fusedValue) + 1);
                continue;
            }
            this.setRange(i, count, chkMsk);
            return i;
        }
        return SET_FULL;
    }


    public boolean free(int idx) {
        if (idx < 0 || idx >= this.limit) {
            throw new IndexOutOfBoundsException(idx);
        }
        long v = this.D[idx>>6];
        boolean wasSet = (v&(1L<<(idx&0x3f)))!=0;
        this.cnt -= wasSet?1:0;

        if (wasSet && idx == this.endId) {
            this.endId = this.findPreviousSet(idx - 1);
        }

        this.D[idx>>6] = v&~(1L<<(idx&0x3f));
        idx >>= 6;
        this.C[idx>>6] &= ~(1L<<(idx&0x3f));
        idx >>= 6;
        this.B[idx>>6] &= ~(1L<<(idx&0x3f));
        idx >>= 6;
        this.A &= ~(1L<<(idx&0x3f));

        return wasSet;
    }

    private void setRange(int start, int count, long rangeMask) {
        int wordIndex = start >>> 6;
        int bitOffset = start & 63;
        long firstMask = rangeMask << bitOffset;
        this.setWordBits(wordIndex, firstMask);
        if (bitOffset + count > 64) {
            this.setWordBits(wordIndex + 1, rangeMask >>> (64 - bitOffset));
        }
        this.cnt += count;
        if (start == this.endId + 1) {
            this.endId += count;
        }
    }

    private void setWordBits(int wordIndex, long mask) {
        long previous = this.D[wordIndex];
        if ((previous & mask) != 0L) {
            throw new IllegalStateException("Attempted to allocate an occupied bit range");
        }
        long updated = previous | mask;
        this.D[wordIndex] = updated;
        if (updated != -1L) {
            return;
        }
        int cIndex = wordIndex >>> 6;
        long c = this.C[cIndex] |= 1L << (wordIndex & 63);
        if (c != -1L) {
            return;
        }
        int bIndex = cIndex >>> 6;
        long b = this.B[bIndex] |= 1L << (cIndex & 63);
        if (b == -1L) {
            this.A |= 1L << bIndex;
        }
    }

    private int findPreviousSet(int idx) {
        if (idx < 0) {
            return -1;
        }
        int wordIndex = idx >>> 6;
        long mask = -1L >>> (63 - (idx & 63));
        long occupied = this.D[wordIndex] & mask;
        while (occupied == 0L) {
            if (--wordIndex < 0) {
                return -1;
            }
            occupied = this.D[wordIndex];
        }
        return (wordIndex << 6) + 63 - Long.numberOfLeadingZeros(occupied);
    }

    public int getCount() {
        return this.cnt;
    }
    public int getLimit() {
        return this.limit;
    }

    public boolean isSet(int idx) {
        return (this.D[idx>>6]&(1L<<(idx&0x3f)))!=0;
    }


    public int getMaxIndex() {
        return this.endId;
    }


    public static void main3(String[] args)  {
        var h = new HierarchicalBitSet(1<<19);
        for (int i = 0; i < 1<<19; i++) {
            if (h.allocateNext() != i) {
                throw new IllegalStateException("At:" + i);
            }
            if (h.endId != i) {
                throw new IllegalStateException();
            }
        }
        for (int i = 0; i < 1<<18; i++) {
            if (!h.free(i)) {
                throw new IllegalStateException();
            }
        }
        for (int i = (1<<19)-1; i != (1<<18)-1; i--) {
            if (h.endId != i) {
                throw new IllegalStateException();
            }
            if (!h.free(i)) {
                throw new IllegalStateException();
            }
        }
        if (h.endId != -1) {
            throw new IllegalStateException();
        }
    }

    public static void main2(String[] args) {
        var h = new HierarchicalBitSet();
        for (int i = 0; i < 64*32; i++) {
            h.set(i);
        }
        h.set(0);
        {
            int i = 0;
            while (i<64*32) {
                int j = h.findNextFree(i);
                if (h.isSet(j)) {
                    throw new IllegalStateException();
                }
                for (int k = i; k < j; k++) {
                    if (!h.isSet(k)) {
                        throw new IllegalStateException();
                    }
                }
                i = j + 1;
            }
        }
        var r = new Random(0);
        for (int i = 0; i < 500; i++) {
            h.free(r.nextInt(64*32));
        }

        h.allocateNextConsecutiveCounted(10);
    }


    public static void main(String[] args) {
        for (int i = 0; i < 100; i++) {
            var r = new Random(i*12345L);
            var h = new HierarchicalBitSet();
            IntSet set = new IntOpenHashSet(10000);
            for (int j = 0; j < 100_000; j++) {
                int q = h.allocateNext();
                if (q != j || !set.add(q)) {
                    throw new IllegalStateException();
                }
            }
            for (int j = 0; j < 100_000; j++) {
                int op = r.nextInt(5);
                int extra = r.nextInt(8)+1;
                if (op == 0) {
                    int v = h.allocateNext();
                    if (v < 0) {
                        throw new IllegalStateException();
                    }
                    if (!set.add(v)) {
                        throw new IllegalStateException();
                    }
                } else if (op == 1) {
                    int base = h.allocateNextConsecutiveCounted(extra);
                    if (base < 0) {
                        throw new IllegalStateException();
                    }
                    for (int q = 0; q < extra; q++) {
                        if (!set.add(q+base)) {
                            throw new IllegalStateException();
                        }
                    }
                } else if (op < 5 && !set.isEmpty()) {
                    int rr = r.nextInt(set.size());
                    var s = set.iterator();
                    if (rr != 0) {
                        s.skip(rr);
                    }

                    int q = s.nextInt();
                    s.remove();

                    if (!h.free(q)) {
                        throw new IllegalStateException();
                    }
                }
            }
        }
    }
}
