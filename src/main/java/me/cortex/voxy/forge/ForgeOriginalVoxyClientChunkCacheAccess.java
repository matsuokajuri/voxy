package me.cortex.voxy.forge;

import net.minecraft.world.level.chunk.LevelChunk;

/**
 * Original Voxy's ICheekyClientChunkCache contract, kept outside the configured
 * Mixin package so transformed Minecraft classes can expose it at runtime.
 */
public interface ForgeOriginalVoxyClientChunkCacheAccess {
    LevelChunk voxy$getLastLoadedChunk(int x, int z);
}
