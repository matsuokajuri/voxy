package me.cortex.voxy.forge;

final class ForgeOriginalVoxyIdNotYetComputedException extends RuntimeException {
    final int id;
    final boolean isIdBlockId;
    int auxBitMsk;
    long[] auxData;

    ForgeOriginalVoxyIdNotYetComputedException(int id, boolean isIdBlockId) {
        this.id = id;
        this.isIdBlockId = isIdBlockId;
    }
}
