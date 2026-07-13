package me.cortex.voxy.forge;

import it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap;

/** Mirrors original GlTexture allocation counters for Forge-owned texture ids. */
final class ForgeOriginalVoxyGlResourceStatistics {
    private static final Int2LongOpenHashMap TEXTURES = new Int2LongOpenHashMap();
    private static long textureBytes;

    private ForgeOriginalVoxyGlResourceStatistics() {
    }

    static synchronized void textureAllocated(int id, long estimatedBytes) {
        if (id == 0) {
            return;
        }
        long previous = TEXTURES.put(id, Math.max(0L, estimatedBytes));
        textureBytes += Math.max(0L, estimatedBytes) - previous;
    }

    static synchronized void textureFreed(int id) {
        if (id != 0 && TEXTURES.containsKey(id)) {
            textureBytes -= TEXTURES.remove(id);
        }
    }

    static synchronized int textureCount() {
        return TEXTURES.size();
    }

    static synchronized long textureBytes() {
        return textureBytes;
    }

    static long mipChainBytes(int width, int height, int levels, int bytesPerPixel) {
        long size = 0L;
        for (int level = 0; level < levels; level++) {
            size += Math.max(width >> level, 1) * (long) Math.max(height >> level, 1) * bytesPerPixel;
        }
        return size;
    }
}
