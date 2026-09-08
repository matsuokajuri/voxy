package me.cortex.voxy.forge;

/** Ecliptic Seasons' existing 20-bit complement encoding, not an additional state registry. */
public final class ForgeSnowStateIds {
    public static final int MAX_ID = (1 << 20) - 1;

    private ForgeSnowStateIds() {}

    public static int baseId(int id, int stateCount) {
        if (id >= stateCount && id <= MAX_ID) {
            int base = MAX_ID - id;
            if (base > 0 && base < stateCount) {
                return base;
            }
        }
        return id;
    }

    public static boolean isSnowy(int id, int stateCount) {
        return baseId(id, stateCount) != id;
    }

    public static int snowyId(int base, int stateCount) {
        int encoded = MAX_ID - base;
        if (base <= 0 || base >= stateCount || encoded < stateCount) {
            throw new IllegalStateException("Ecliptic snowy state id overlaps the block registry: " + base);
        }
        return encoded;
    }
}
