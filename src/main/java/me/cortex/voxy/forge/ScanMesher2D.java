package me.cortex.voxy.forge;

abstract class ScanMesher2D {
    private static final int MAX_SIZE = 16;

    private final long[] rowData = new long[32];
    private final int[] rowLength = new int[32];
    private final int[] rowDepth = new int[32];
    private int rowBitset = 0;
    private int currentIndex = 0;
    private int currentSum = 0;
    private long currentData = 0;

    final void putNext(long data) {
        this.putNext0(data);
    }

    private void putNext0(long data) {
        int idx = (this.currentIndex++) & 31;
        if (idx == 0) {
            if (this.currentData != 0) {
                if ((this.rowBitset & (1 << 31)) != 0) {
                    this.emitQuad(31, ((this.currentIndex - 1) >> 5) - 1, this.rowLength[31], this.rowDepth[31], this.rowData[31]);
                }
                this.rowBitset |= 1 << 31;
                this.rowLength[31] = this.currentSum;
                this.rowDepth[31] = 1;
                this.rowData[31] = this.currentData;
            }
            this.currentData = data;
            this.currentSum = 0;
        }
        if (data != this.currentData || this.currentSum == MAX_SIZE) {
            if (this.currentData != 0) {
                int prev = idx - 1;
                this.rowDepth[prev] = 1;
                this.rowLength[prev] = this.currentSum;
                this.rowData[prev] = this.currentData;
                this.rowBitset |= 1 << prev;
            }
            this.currentData = data;
            this.currentSum = 0;
        }
        this.currentSum++;

        boolean isSet = (this.rowBitset & (1 << idx)) != 0;
        boolean causedByDepthMax = false;
        if (this.currentData != 0 && isSet && this.rowLength[idx] == this.currentSum && this.rowData[idx] == this.currentData) {
            int depth = ++this.rowDepth[idx];
            this.currentSum = 0;
            this.currentData = 0;
            if (depth != MAX_SIZE) {
                return;
            }
            causedByDepthMax = true;
        }
        if (isSet) {
            this.emitQuad(idx & 31, ((this.currentIndex - 1) >> 5) - (causedByDepthMax ? 0 : 1), this.rowLength[idx], this.rowDepth[idx], this.rowData[idx]);
            this.rowBitset &= ~(1 << idx);
        }
    }

    private void emitRanged(int msk) {
        int rowSet = this.rowBitset & msk;
        this.rowBitset &= ~msk;
        while (rowSet != 0) {
            int index = Integer.numberOfTrailingZeros(rowSet);
            rowSet &= ~Integer.lowestOneBit(rowSet);
            this.emitQuad(index, (this.currentIndex >> 5) - 1, this.rowLength[index], this.rowDepth[index], this.rowData[index]);
        }
    }

    final void skip(int count) {
        if (count == 0) {
            return;
        }
        if (this.currentData != 0) {
            this.putNext0(0);
            count--;
        }
        if (0 < count) {
            int msk = (int) ((1L << Math.min(32, count)) - 1) << (this.currentIndex & 31);
            this.emitRanged(msk);
            this.currentIndex += count;
        }
    }

    final void reset() {
        this.rowBitset = 0;
        this.currentSum = 0;
        this.currentData = 0;
        this.currentIndex = 0;
    }

    final void endRow() {
        if ((this.currentIndex & 31) != 0) {
            this.skip(32 - (this.currentIndex & 31));
        }
    }

    final void finish() {
        if (this.currentIndex != 0) {
            this.skip(32 - (this.currentIndex & 31));
            this.emitRanged(-1);
        }
        this.reset();
    }

    protected abstract void emitQuad(int x, int z, int length, int width, long data);
}
