package me.cortex.voxy.forge.mixin;

import it.unimi.dsi.fastutil.longs.Long2ReferenceMap;
import me.jellysquid.mods.sodium.client.gl.device.CommandList;
import me.cortex.voxy.common.world.WorldEngine;
import me.cortex.voxy.common.world.service.VoxelIngestService;
import me.cortex.voxy.config.ForgeVoxyConfig;
import me.cortex.voxy.forge.ForgeOriginalVoxyClientChunkCacheAccess;
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

    private static final boolean VOXY_AUDIT_CHUNK_BOUND = Boolean.getBoolean("voxy.forge.auditChunkBound");

    private long voxy$cachedChunkPos = Long.MIN_VALUE;
    private int voxy$cachedChunkStatus;
    private boolean voxy$chunkBoundSeededForOwner;
    private long voxy$lastMaskAuditNanos;

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

    //Gated by -Dvoxy.forge.auditChunkBound: once per second, classify built-but-not-drawn sections.
    // The Voxy chunk-bound mask culls its AABBs with a cylinder over a box expanded by 1 block
    // (original outline.vsh), while Embeddium's OcclusionCuller draws sections using the same
    // cylinder over the UNEXPANDED box. A section inside the mask cylinder but outside the draw
    // cylinder is masked-but-never-drawn = a hole at the render-distance circle. This audit counts
    // that ring-sliver population separately from in-circle sections skipped by frustum/occlusion
    // culling, so the two hole theories can be told apart from the log alone.
    @Inject(method = "update", at = @At("TAIL"))
    private void voxy$auditMaskCoverage(Camera camera, Viewport viewport, int frame, boolean spectator, CallbackInfo ci) {
        if (!VOXY_AUDIT_CHUNK_BOUND) {
            return;
        }
        long now = System.nanoTime();
        if (now - this.voxy$lastMaskAuditNanos < 1_000_000_000L) {
            return;
        }
        this.voxy$lastMaskAuditNanos = now;
        double camX = camera.getPosition().x;
        double camY = camera.getPosition().y;
        double camZ = camera.getPosition().z;
        double radius = net.minecraft.client.Minecraft.getInstance().options.getEffectiveRenderDistance() * 16.0D;
        int built = 0;
        int notVisited = 0;
        int ringSliver = 0;
        int inCircleNotVisited = 0;
        StringBuilder samples = new StringBuilder();
        int sampleCount = 0;
        for (RenderSection section : this.sectionByPosition.values()) {
            if (section == null || section.getFlags() == 0) {
                continue;
            }
            built++;
            if (section.getLastVisibleFrame() == frame) {
                continue;
            }
            notVisited++;
            double ox = section.getChunkX() * 16.0D;
            double oy = section.getChunkY() * 16.0D;
            double oz = section.getChunkZ() * 16.0D;
            double dxDraw = voxy$nearestToZero(ox - camX, ox + 16.0D - camX);
            double dyDraw = voxy$nearestToZero(oy - camY, oy + 16.0D - camY);
            double dzDraw = voxy$nearestToZero(oz - camZ, oz + 16.0D - camZ);
            boolean insideDraw = dxDraw * dxDraw + dzDraw * dzDraw < radius * radius && Math.abs(dyDraw) < radius;
            double dxMask = voxy$nearestToZero(ox - 1.0D - camX, ox + 17.0D - camX);
            double dyMask = voxy$nearestToZero(oy - 1.0D - camY, oy + 17.0D - camY);
            double dzMask = voxy$nearestToZero(oz - 1.0D - camZ, oz + 17.0D - camZ);
            boolean insideMask = dxMask * dxMask + dzMask * dzMask < radius * radius && Math.abs(dyMask) < radius;
            if (insideMask && !insideDraw) {
                ringSliver++;
                if (sampleCount < 4) {
                    sampleCount++;
                    samples.append(String.format(
                            " sliver[%d,%d,%d d=%.1f/r=%.0f]",
                            section.getChunkX(), section.getChunkY(), section.getChunkZ(),
                            Math.sqrt(dxDraw * dxDraw + dzDraw * dzDraw), radius));
                }
            } else if (insideDraw) {
                inCircleNotVisited++;
            }
        }
        VoxyForge.LOGGER.info(
                "Voxy mask-coverage audit: built={} notVisited={} ringSliver={} inCircleNotVisited={}{}",
                built,
                notVisited,
                ringSliver,
                inCircleNotVisited,
                samples);
    }

    private static double voxy$nearestToZero(double min, double max) {
        if (min > 0.0D) {
            return min;
        }
        if (max < 0.0D) {
            return max;
        }
        return 0.0D;
    }

    @Inject(method = "onChunkAdded", at = @At("HEAD"))
    private void voxy$ingestOnChunkAdd(int x, int z, CallbackInfo ci) {
        if (!ForgeVoxyConfig.ENABLED.get() || !ForgeVoxyConfig.INGEST_ENABLED.get()) {
            return;
        }
        LevelChunk chunk = this.world.getChunkSource().getChunk(x, z, net.minecraft.world.level.chunk.ChunkStatus.FULL, false);
        if (chunk != null) {
            boolean updated = VoxelIngestService.tryAutoIngestChunk(chunk);
            ForgeVoxyInstance.INSTANCE.getChunkIngestManager().recordMixinChunkIngest(updated);
        }
    }

    @Inject(method = "onChunkRemoved", at = @At("HEAD"))
    private void voxy$ingestOnChunkRemove(int x, int z, CallbackInfo ci) {
        if (!ForgeVoxyConfig.ENABLED.get() || !ForgeVoxyConfig.INGEST_ENABLED.get()) {
            return;
        }
        LevelChunk chunk = ((ForgeOriginalVoxyClientChunkCacheAccess) this.world.getChunkSource())
                .voxy$getLastLoadedChunk(x, z);
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
        if (!ForgeVoxyConfig.ENABLED.get() || !ForgeVoxyConfig.INGEST_ENABLED.get()) {
            return;
        }

        WorldEngine engine = ForgeVoxyInstance.INSTANCE.getEngineForLevel(this.world).orElse(null);
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
