package me.cortex.voxy.forge.mixin;

import me.cortex.voxy.forge.AcediumBufferResidency;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Acedium-only ABI adapter; original Voxy mapped buffers simply unmap and delete. */
@Pseudo
@Mixin(targets = "me.cortex.nvidium.gl.buffers.PersistentClientMappedBuffer", remap = false)
public abstract class ForgeOriginalVoxyAcediumMappedBufferMixin {
    @Redirect(method = "delete()V", at = @At(value = "INVOKE",
            target = "Lorg/lwjgl/opengl/NVShaderBufferLoad;glMakeNamedBufferNonResidentNV(I)V"),
            require = 1)
    private void voxy$releaseOnlyResidentAddress(int buffer) {
        AcediumBufferResidency.releaseIfResident(buffer);
    }
}
