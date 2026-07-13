package me.cortex.voxy.forge;

final class IdNotYetComputedException extends RuntimeException {
    final int id;
    final boolean isIdBlockId;
    int auxBitMsk;
    long[] auxData;

    IdNotYetComputedException(int id, boolean isIdBlockId) {
        this.id = id;
        this.isIdBlockId = isIdBlockId;
    }
}
