package me.cortex.voxy.forge.mixin;

import it.unimi.dsi.fastutil.longs.Long2ReferenceMap;
import me.jellysquid.mods.sodium.client.gl.device.CommandList;
import me.cortex.voxy.common.world.WorldEngine;
import me.cortex.voxy.common.world.service.VoxelIngestService;
import me.cortex.voxy.forge.ForgeVoxyInstance;
import me.cortex.voxy.forge.VoxyForge;
import me.jellysquid.mods.sodium.client.render.viewport.Viewport;
import me.jellysquid.mods.sodium.client.render.chunk.RenderSection;
import me.jellysquid.mods.sodium.client.render.chunk.data.BuiltSectionInfo;
import me.jellysquid.mods.sodium.client.render.chunk.map.ChunkStatus;
import me.jellysquid.mods.sodium.client.render.chunk.map.ChunkTrackerHolder;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "me.jellysquid.mods.sodium.client.render.chunk.RenderSectionManager", remap = false)
public class ForgeOriginalVoxyEmbeddiumRenderSectionManagerMixin {
    @Shadow
    @Final
    private ClientLevel world;

    @Shadow
    @Final
    private Long2ReferenceMap<RenderSection> sectionByPosition;

    private long voxy$cachedChunkPos = Long.MIN_VALUE;
    private int voxy$cachedChunkStatus;
    private boolean voxy$chunkBoundSeededForOwner;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void voxy$resetChunkBoundTracker(ClientLevel world, int renderDistance, CommandList commandList, CallbackInfo ci) {
        ForgeVoxyInstance.INSTANCE.resetOriginalVoxyChunkBoundTracker();
        this.voxy$chunkBoundSeededForOwner = false;
    }

    @Inject(method = "update", at = @At("TAIL"))
    private void voxy$seedChunkBoundTracker(Camera camera, Viewport viewport, int frame, boolean spectator, CallbackInfo ci) {
        if (!ForgeVoxyInstance.INSTANCE.isOriginalVoxyChunkBoundTrackerActive()) {
            this.voxy$chunkBoundSeededForOwner = false;
            return;
        }
        if (this.voxy$chunkBoundSeededForOwner) {
            return;
        }
        ForgeVoxyInstance.INSTANCE.resetOriginalVoxyChunkBoundTracker();
        int seeded = 0;
        for (RenderSection section : this.sectionByPosition.values()) {
            if (section != null && section.getFlags() != 0) {
                ForgeVoxyInstance.INSTANCE.trackOriginalVoxyChunkBoundSection(
                        false,
                        section.getChunkX(),
                        section.getChunkY(),
                        section.getChunkZ());
                seeded++;
            }
        }
        if (seeded == 0) {
            return;
        }
        VoxyForge.LOGGER.info("Original Voxy chunk-bound tracker seeded {} Embeddium built sections.", seeded);
        this.voxy$chunkBoundSeededForOwner = true;
    }

    @Inject(method = "onChunkAdded", at = @At("HEAD"))
    private void voxy$ingestOnChunkAdd(int x, int z, CallbackInfo ci) {
        LevelChunk chunk = this.world.getChunkSource().getChunk(x, z, net.minecraft.world.level.chunk.ChunkStatus.FULL, false);
        if (chunk != null) {
            boolean updated = VoxelIngestService.tryAutoIngestChunk(chunk);
            ForgeVoxyInstance.INSTANCE.getChunkIngestManager().recordMixinChunkIngest(updated);
        }
    }

    @Inject(method = "onChunkRemoved", at = @At("HEAD"))
    private void voxy$ingestOnChunkRemove(int x, int z, CallbackInfo ci) {
        LevelChunk chunk = this.world.getChunkSource().getChunk(x, z, net.minecraft.world.level.chunk.ChunkStatus.FULL, false);
        if (chunk != null) {
            boolean updated = VoxelIngestService.tryAutoIngestChunk(chunk);
            ForgeVoxyInstance.INSTANCE.getChunkIngestManager().recordMixinChunkIngest(updated);
        }
    }

    @Redirect(
            method = "updateSectionInfo",
            at = @At(
                    value = "INVOKE",
                    target = "Lme/jellysquid/mods/sodium/client/render/chunk/RenderSection;setInfo(Lme/jellysquid/mods/sodium/client/render/chunk/data/BuiltSectionInfo;)V"))
    private void voxy$ingestOnSectionInfoUpdate(RenderSection section, BuiltSectionInfo info) {
        boolean wasBuilt = section.getFlags() != 0;
        int flags = section.getFlags();
        section.setInfo(info);

        if (wasBuilt == (section.getFlags() != 0)) {
            return;
        }
        flags |= section.getFlags();
        int x = section.getChunkX();
        int y = section.getChunkY();
        int z = section.getChunkZ();
        ForgeVoxyInstance.INSTANCE.trackOriginalVoxyChunkBoundSection(wasBuilt, x, y, z);
        if (flags == 0 || !wasBuilt) {
            return;
        }

        WorldEngine engine = ForgeVoxyInstance.INSTANCE.getCurrentEngineOptional().orElse(null);
        if (engine == null) {
            return;
        }

        long chunkKey = net.minecraft.world.level.ChunkPos.asLong(x, z);
        if (chunkKey != this.voxy$cachedChunkPos) {
            this.voxy$cachedChunkPos = chunkKey;
            this.voxy$cachedChunkStatus = ((ForgeOriginalVoxyEmbeddiumChunkTrackerAccessor) ChunkTrackerHolder.get(this.world))
                    .voxy$getChunkStatus()
                    .getOrDefault(chunkKey, 0);
        }
        if (this.voxy$cachedChunkStatus != ChunkStatus.FLAG_ALL) {
            return;
        }

        LevelChunk chunk = this.world.getChunkSource().getChunk(x, z, net.minecraft.world.level.chunk.ChunkStatus.FULL, false);
        if (chunk == null) {
            return;
        }
        boolean updated = VoxelIngestService.ingestChunkSection(
                engine,
                chunk,
                y);
        ForgeVoxyInstance.INSTANCE.getChunkIngestManager().recordMixinSectionIngest(updated);
    }
}
