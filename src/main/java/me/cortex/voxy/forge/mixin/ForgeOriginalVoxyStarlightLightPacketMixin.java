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
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Runs after Starlight priority 1001 finishes clientUpdateLight/clientChunkLoad at RETURN. */
@Mixin(value = ClientPacketListener.class, priority = 900)
public class ForgeOriginalVoxyStarlightLightPacketMixin {
    @Unique
    private static boolean voxy$loggedPacketBoundary;

    @Shadow
    private ClientLevel level;

    @Inject(method = "handleLevelChunkWithLight", at = @At("RETURN"))
    private void voxy$ingestAfterStarlightPacket(
            ClientboundLevelChunkWithLightPacket packet,
            CallbackInfo ci) {
        if (!voxy$loggedPacketBoundary) {
            voxy$loggedPacketBoundary = true;
            VoxyForge.LOGGER.info("Original Voxy Starlight post-light packet ingest hook reached.");
        }
        LevelChunk chunk = this.level.getChunkSource()
                .getChunk(packet.getX(), packet.getZ(), ChunkStatus.FULL, false);
        if (chunk != null) {
            ForgeVoxyInstance.INSTANCE.ingestChunkAfterLightUpdate(chunk);
        }
    }
}
