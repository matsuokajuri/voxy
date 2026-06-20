package me.cortex.voxy.forge;

import org.lwjgl.system.MemoryUtil;

import java.util.Arrays;

final class ForgeOriginalVoxyOccupancySet {
    private long topLvl;
    private final long[] bottomLvl = new long[(4 * 4 * 4) * 8];

    void set(final int pos) {
        final long topBit = 1L << compressBits(pos, 0b11000_11000_11000);
        final int botIdx = compressBits(pos, 0b00111_00111_00111);
        int baseBotIdx = Long.bitCount(this.topLvl & (topBit - 1)) * 8;
        if ((this.topLvl & topBit) == 0) {
            long toMove = this.topLvl & (~((topBit << 1) - 1));
            if (toMove != 0) {
                int base = baseBotIdx + 8;
                int count = Long.bitCount(toMove);
                for (int i = base + count * 8 - 1; base <= i; i--) {
                    this.bottomLvl[i] = this.bottomLvl[i - 8];
                }
                for (int i = baseBotIdx; i < baseBotIdx + 8; i++) {
                    this.bottomLvl[i] = 0;
                }
            }
            this.topLvl |= topBit;
        }
        this.bottomLvl[baseBotIdx + (botIdx >> 6)] |= 1L << (botIdx & 63);
    }

    void reset() {
        if (this.topLvl != 0) {
            Arrays.fill(this.bottomLvl, 0);
        }
        this.topLvl = 0;
    }

    int writeSize() {
        return 8 + Long.bitCount(this.topLvl) * 8 * 8;
    }

    boolean isEmpty() {
        return this.topLvl == 0;
    }

    void write(long ptr, boolean asLongs) {
        if (asLongs) {
            MemoryUtil.memPutLong(ptr, this.topLvl);
            ptr += 8;
            int cnt = Long.bitCount(this.topLvl);
            for (int i = 0; i < cnt; i++) {
                for (int j = 0; j < 8; j++) {
                    MemoryUtil.memPutLong(ptr, this.bottomLvl[i * 8 + j]);
                    ptr += 8;
                }
            }
        } else {
            MemoryUtil.memPutInt(ptr, (int) (this.topLvl >>> 32));
            ptr += 4;
            MemoryUtil.memPutInt(ptr, (int) this.topLvl);
            ptr += 4;
            int cnt = Long.bitCount(this.topLvl);
            for (int i = 0; i < cnt; i++) {
                for (int j = 0; j < 8; j++) {
                    long v = this.bottomLvl[i * 8 + j];
                    MemoryUtil.memPutInt(ptr, (int) (v >>> 32));
                    ptr += 4;
                    MemoryUtil.memPutInt(ptr, (int) v);
                    ptr += 4;
                }
            }
        }
    }

    private static int compressBits(int value, int mask) {
        int result = 0;
        int outBit = 0;
        for (int bit = 0; bit < Integer.SIZE; bit++) {
            int bitMask = 1 << bit;
            if ((mask & bitMask) != 0) {
                if ((value & bitMask) != 0) {
                    result |= 1 << outBit;
                }
                outBit++;
            }
        }
        return result;
    }
}
