package me.cortex.voxy.forge.mixin;

import me.jellysquid.mods.sodium.client.render.viewport.Viewport;
import me.jellysquid.mods.sodium.client.render.viewport.frustum.Frustum;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = Viewport.class, remap = false)
public interface ForgeOriginalVoxyEmbeddiumViewportAccessor {
    @Accessor("frustum")
    Frustum voxy$getFrustum();
}
