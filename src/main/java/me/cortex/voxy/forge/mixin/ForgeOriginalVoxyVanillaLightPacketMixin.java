package me.cortex.voxy.forge.mixin;

import me.cortex.voxy.forge.ForgeVoxyInstance;
import me.cortex.voxy.forge.VoxyForge;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Vanilla light-packet completion boundary; disabled when Starlight owns that boundary. */
@Mixin(ClientPacketListener.class)
public class ForgeOriginalVoxyVanillaLightPacketMixin {
    @Unique
    private static boolean voxy$loggedPacketBoundary;

    @Shadow
    private ClientLevel level;

    @Unique
    private int voxy$pendingLightChunkX;

    @Unique
    private int voxy$pendingLightChunkZ;

    //The network-thread invocation schedules the packet and throws out of the vanilla method.
    //Only the client-thread invocation returns from this guard; capture coordinates there so
    //another network packet cannot overwrite the fields while this packet builds its Runnable.
    @Inject(
            method = "handleLevelChunkWithLight",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/util/thread/BlockableEventLoop;)V",
                    shift = At.Shift.AFTER))
    private void voxy$captureLightPacketChunk(
            ClientboundLevelChunkWithLightPacket packet,
            CallbackInfo ci) {
        this.voxy$pendingLightChunkX = packet.getX();
        this.voxy$pendingLightChunkZ = packet.getZ();
    }

    @ModifyArg(
            method = "handleLevelChunkWithLight",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/multiplayer/ClientLevel;queueLightUpdate(Ljava/lang/Runnable;)V"),
            index = 0)
    private Runnable voxy$ingestAfterVanillaLightPacket(Runnable applyLightData) {
        ClientLevel packetLevel = this.level;
        int chunkX = this.voxy$pendingLightChunkX;
        int chunkZ = this.voxy$pendingLightChunkZ;
        return () -> {
            applyLightData.run();
            if (!voxy$loggedPacketBoundary) {
                voxy$loggedPacketBoundary = true;
                VoxyForge.LOGGER.info("Original Voxy vanilla post-light packet ingest hook reached.");
            }
            voxy$ingestCompletedChunk(packetLevel, chunkX, chunkZ);
        };
    }

    @Unique
    private static void voxy$ingestCompletedChunk(ClientLevel level, int chunkX, int chunkZ) {
        LevelChunk chunk = level.getChunkSource().getChunk(chunkX, chunkZ, ChunkStatus.FULL, false);
        if (chunk != null) {
            ForgeVoxyInstance.INSTANCE.ingestChunkAfterLightUpdate(chunk);
        }
    }
}
