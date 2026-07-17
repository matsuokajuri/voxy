package me.cortex.voxy.forge.mixin;

import me.jellysquid.mods.sodium.client.render.viewport.frustum.SimpleFrustum;
import org.joml.FrustumIntersection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = SimpleFrustum.class, remap = false)
public interface ForgeOriginalVoxyEmbeddiumSimpleFrustumAccessor {
    @Accessor("frustum")
    FrustumIntersection voxy$getFrustumIntersection();
}
