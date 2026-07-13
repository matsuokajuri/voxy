package me.cortex.voxy.forge;

import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.level.block.state.BlockState;

/** Forge render-type classification used while porting original Voxy model-bakery metadata. */
public enum ForgeOriginalVoxyModelLayer {
    SOLID(0, "solid"),
    CUTOUT(1, "cutout"),
    TRANSLUCENT(2, "translucent"),
    OTHER(3, "other");

    public final int id;
    public final String displayName;

    ForgeOriginalVoxyModelLayer(int id, String displayName) {
        this.id = id;
        this.displayName = displayName;
    }

    public static ForgeOriginalVoxyModelLayer fromBlockState(BlockState state) {
        RenderType type = ItemBlockRenderTypes.getChunkRenderType(state);
        if (type == RenderType.translucent()) {
            return TRANSLUCENT;
        }
        if (type == RenderType.cutout() || type == RenderType.cutoutMipped()) {
            return CUTOUT;
        }
        if (type == RenderType.solid()) {
            return SOLID;
        }
        return OTHER;
    }
}
