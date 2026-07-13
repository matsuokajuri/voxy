package me.cortex.voxy.forge.mixin;

import me.cortex.voxy.forge.ICheekyClientChunkCache;
import me.cortex.voxy.common.world.service.VoxelIngestService;
import me.cortex.voxy.config.ForgeVoxyConfig;
import net.minecraft.client.multiplayer.ClientChunkCache;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.fml.ModList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientChunkCache.class)
public class ForgeOriginalVoxyClientChunkCacheMixin implements ICheekyClientChunkCache {
    @Unique
    private static final boolean VOXY$BOBBY_INSTALLED = ModList.get().isLoaded("bobby");

    @Shadow
    private volatile ClientChunkCache.Storage storage;

    @Override
    public LevelChunk voxy$cheekyGetChunk(int x, int z) {
        //Exact original ICheekyClientChunkCache contract: bypass ClientChunkCache's in-range
        // check so RenderSectionManager.onChunkRemoved can ingest the final loaded snapshot.
        ClientChunkCache.Storage currentStorage = this.storage;
        LevelChunk chunk = currentStorage.getChunk(currentStorage.getIndex(x, z));
        if (chunk == null) {
            return null;
        }
        return chunk.getPos().x == x && chunk.getPos().z == z ? chunk : null;
    }

    @Inject(method = "drop", at = @At("HEAD"))
    private void voxy$captureBobbyChunkBeforeUnload(int x, int z, CallbackInfo ci) {
        if (!VOXY$BOBBY_INSTALLED
                || !ForgeVoxyConfig.ENABLED.get()
                || !ForgeVoxyConfig.INGEST_ENABLED.get()) {
            return;
        }
        LevelChunk chunk = this.voxy$cheekyGetChunk(x, z);
        if (chunk != null) {
            VoxelIngestService.tryAutoIngestChunk(chunk);
        }
    }
}
