package me.cortex.voxy.forge.mixin;

import me.jellysquid.mods.sodium.client.render.SodiumWorldRenderer;
import me.jellysquid.mods.sodium.client.render.chunk.RenderSectionManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Forge/Embeddium counterpart to original Voxy's AccessorSodiumWorldRenderer. */
@Mixin(value = SodiumWorldRenderer.class, remap = false)
public interface ForgeOriginalVoxyEmbeddiumWorldRendererAccessor {
    @Accessor("renderSectionManager")
    RenderSectionManager voxy$getRenderSectionManager();
}
