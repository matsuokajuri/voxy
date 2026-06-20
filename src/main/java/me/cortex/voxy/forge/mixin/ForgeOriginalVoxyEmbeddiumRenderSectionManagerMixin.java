package me.cortex.voxy.forge.mixin;

import me.cortex.voxy.common.world.WorldEngine;
import me.cortex.voxy.common.world.service.VoxelIngestService;
import me.cortex.voxy.forge.ForgeVoxyInstance;
import me.jellysquid.mods.sodium.client.render.chunk.RenderSection;
import me.jellysquid.mods.sodium.client.render.chunk.data.BuiltSectionInfo;
import me.jellysquid.mods.sodium.client.render.chunk.map.ChunkStatus;
import me.jellysquid.mods.sodium.client.render.chunk.map.ChunkTrackerHolder;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.LightLayer;
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

    private long voxy$cachedChunkPos = Long.MIN_VALUE;
    private int voxy$cachedChunkStatus;

    @Inject(method = "onChunkAdded", at = @At("HEAD"))
    private void voxy$ingestOnChunkAdd(int x, int z, CallbackInfo ci) {
        LevelChunk chunk = this.world.getChunkSource().getChunk(x, z, net.minecraft.world.level.chunk.ChunkStatus.FULL, false);
        if (chunk != null) {
            VoxelIngestService.tryAutoIngestChunk(chunk);
        }
    }

    @Inject(method = "onChunkRemoved", at = @At("HEAD"))
    private void voxy$ingestOnChunkRemove(int x, int z, CallbackInfo ci) {
        LevelChunk chunk = this.world.getChunkSource().getChunk(x, z, net.minecraft.world.level.chunk.ChunkStatus.FULL, false);
        if (chunk != null) {
            VoxelIngestService.tryAutoIngestChunk(chunk);
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
        if (flags == 0 || !wasBuilt) {
            return;
        }

        WorldEngine engine = ForgeVoxyInstance.INSTANCE.getCurrentEngineOptional().orElse(null);
        if (engine == null) {
            return;
        }

        int x = section.getChunkX();
        int y = section.getChunkY();
        int z = section.getChunkZ();
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
        int sectionIndex = y - this.world.getMinSection();
        if (sectionIndex < 0 || sectionIndex >= chunk.getSections().length) {
            return;
        }

        var levelSection = chunk.getSection(sectionIndex);
        var lightEngine = this.world.getLightEngine();
        var sectionPos = SectionPos.of(x, y, z);
        var blockLight = lightEngine.getLayerListener(LightLayer.BLOCK).getDataLayerData(sectionPos);
        var skyLight = lightEngine.getLayerListener(LightLayer.SKY).getDataLayerData(sectionPos);
        VoxelIngestService.rawIngest(
                engine,
                levelSection,
                x,
                y,
                z,
                blockLight == null ? null : blockLight.copy(),
                skyLight == null ? null : skyLight.copy());
    }
}
