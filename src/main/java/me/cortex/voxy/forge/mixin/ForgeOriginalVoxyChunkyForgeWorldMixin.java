package me.cortex.voxy.forge.mixin;

import me.cortex.voxy.common.world.service.VoxelIngestService;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.concurrent.CompletableFuture;

/** Optional Forge counterpart to original Voxy's Chunky Fabric generation hook. */
@Pseudo
@Mixin(targets = "org.popcraft.chunky.platform.ForgeWorld", remap = false)
public abstract class ForgeOriginalVoxyChunkyForgeWorldMixin {
    @Shadow
    @Final
    private ServerLevel world;

    @Inject(
            method = "getChunkAtAsync(II)Ljava/util/concurrent/CompletableFuture;",
            at = @At("RETURN"),
            cancellable = true,
            require = 0,
            remap = false)
    private void voxy$ingestGeneratedChunk(
            int chunkX,
            int chunkZ,
            CallbackInfoReturnable<CompletableFuture<Void>> cir) {
        //ForgeWorld recursively dispatches off-thread calls onto the server thread. Attach only
        //to that inner invocation so one generated chunk is not ingested twice.
        if (Thread.currentThread() != this.world.getServer().getRunningThread()) {
            return;
        }
        CompletableFuture<Void> generated = cir.getReturnValue();
        if (generated == null) {
            return;
        }
        //ChunkHolder futures are not an API guarantee that dependent stages run on the main
        //thread, while ServerChunkCache#getChunkNow deliberately returns null off-thread.
        cir.setReturnValue(generated.thenRunAsync(() -> {
            LevelChunk chunk = this.world.getChunkSource().getChunkNow(chunkX, chunkZ);
            if (chunk != null) {
                VoxelIngestService.tryAutoIngestChunk(chunk);
            }
        }, this.world.getServer()));
    }
}
