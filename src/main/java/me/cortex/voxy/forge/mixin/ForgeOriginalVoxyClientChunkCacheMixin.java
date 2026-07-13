package me.cortex.voxy.forge.mixin;

import me.cortex.voxy.forge.ForgeOriginalVoxyClientChunkCacheAccess;
import net.minecraft.client.multiplayer.ClientChunkCache;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(ClientChunkCache.class)
public class ForgeOriginalVoxyClientChunkCacheMixin implements ForgeOriginalVoxyClientChunkCacheAccess {
    @Shadow
    private volatile ClientChunkCache.Storage storage;

    @Override
    public LevelChunk voxy$getLastLoadedChunk(int x, int z) {
        //Exact original ICheekyClientChunkCache contract: bypass ClientChunkCache's in-range
        // check so RenderSectionManager.onChunkRemoved can ingest the final loaded snapshot.
        ClientChunkCache.Storage currentStorage = this.storage;
        LevelChunk chunk = currentStorage.getChunk(currentStorage.getIndex(x, z));
        if (chunk == null) {
            return null;
        }
        return chunk.getPos().x == x && chunk.getPos().z == z ? chunk : null;
    }
}
