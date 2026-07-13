package me.cortex.voxy.forge;

import net.minecraft.world.level.chunk.LevelChunk;

import javax.annotation.Nullable;

/** Forge-package port of original Voxy's direct client chunk-cache access contract. */
public interface ICheekyClientChunkCache {
    @Nullable
    LevelChunk voxy$cheekyGetChunk(int x, int z);
}
