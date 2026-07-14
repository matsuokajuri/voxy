package me.cortex.voxy.forge.mixin;

import me.cortex.voxy.forge.ForgeVoxyInstance;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundLoginPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Minecraft 1.20.1 anchor for original Voxy's pre-level client-session creation. */
@Mixin(ClientPacketListener.class)
public class ForgeOriginalVoxyClientPacketListenerMixin {
    @Inject(
            method = "handleLogin",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/util/thread/BlockableEventLoop;)V",
                    shift = At.Shift.AFTER))
    private void voxy$beginOriginalSession(ClientboundLoginPacket packet, CallbackInfo ci) {
        ForgeVoxyInstance.INSTANCE.beginOriginalVoxyClientSession((ClientPacketListener) (Object) this);
    }
}
