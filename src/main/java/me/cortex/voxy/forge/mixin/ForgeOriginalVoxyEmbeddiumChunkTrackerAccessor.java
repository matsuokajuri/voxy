package me.cortex.voxy.forge.mixin;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = "me.jellysquid.mods.sodium.client.render.chunk.map.ChunkTracker", remap = false)
public interface ForgeOriginalVoxyEmbeddiumChunkTrackerAccessor {
    @Accessor("chunkStatus")
    Long2IntOpenHashMap voxy$getChunkStatus();
}
