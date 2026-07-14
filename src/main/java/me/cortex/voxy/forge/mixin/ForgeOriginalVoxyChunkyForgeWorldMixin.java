package me.cortex.voxy.forge.mixin;

import com.mojang.datafixers.util.Either;
import me.cortex.voxy.common.world.service.VoxelIngestService;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.concurrent.CompletableFuture;

/** Optional Forge counterpart to original Voxy's Chunky Fabric generation hook. */
@Pseudo
@Mixin(targets = "org.popcraft.chunky.platform.ForgeWorld", remap = false)
public abstract class ForgeOriginalVoxyChunkyForgeWorldMixin {
    @Redirect(
            method = "getChunkAtAsync(II)Ljava/util/concurrent/CompletableFuture;",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/level/ChunkHolder;getOrScheduleFuture(Lnet/minecraft/world/level/chunk/ChunkStatus;Lnet/minecraft/server/level/ChunkMap;)Ljava/util/concurrent/CompletableFuture;",
                    remap = false),
            require = 0,
            remap = false)
    private CompletableFuture<Either<ChunkAccess, ChunkHolder.ChunkLoadingFailure>>
            voxy$captureGeneratedChunkMojmap(
            ChunkHolder holder,
            ChunkStatus status,
            ChunkMap chunkMap) {
        return voxy$captureGeneratedChunk(holder.getOrScheduleFuture(status, chunkMap));
    }

    //Forge 1.20.1 production runtime name. The enclosing Chunky target is external and
    //remap=false, so the nested Minecraft invocation cannot receive a refmap entry.
    @Redirect(
            method = "getChunkAtAsync(II)Ljava/util/concurrent/CompletableFuture;",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/level/ChunkHolder;m_140049_(Lnet/minecraft/world/level/chunk/ChunkStatus;Lnet/minecraft/server/level/ChunkMap;)Ljava/util/concurrent/CompletableFuture;",
                    remap = false),
            require = 0,
            remap = false)
    private CompletableFuture<Either<ChunkAccess, ChunkHolder.ChunkLoadingFailure>>
            voxy$captureGeneratedChunkSrg(
            ChunkHolder holder,
            ChunkStatus status,
            ChunkMap chunkMap) {
        return voxy$captureGeneratedChunk(holder.getOrScheduleFuture(status, chunkMap));
    }

    private static CompletableFuture<Either<ChunkAccess, ChunkHolder.ChunkLoadingFailure>>
            voxy$captureGeneratedChunk(
            CompletableFuture<Either<ChunkAccess, ChunkHolder.ChunkLoadingFailure>> future) {
        //Match original Voxy's Fabric hook: consume the exact successful FULL result while Chunky's
        //generation future still owns it. Re-looking up the coordinates after ForgeWorld's Void
        //future completes races Chunky's ticket removal and randomly loses fast-generated chunks.
        return future.thenApply(result -> {
            result.left().ifPresent(chunk -> {
                if (chunk instanceof LevelChunk levelChunk) {
                    VoxelIngestService.tryAutoIngestChunk(levelChunk);
                }
            });
            return result;
        });
    }
}
