package me.cortex.voxy.forge;

import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.level.block.state.BlockState;

public enum ForgeCpuMeshLayer {
    SOLID(0, "solid"),
    CUTOUT(1, "cutout"),
    TRANSLUCENT(2, "translucent"),
    OTHER(3, "other");

    public final int id;
    public final String displayName;

    ForgeCpuMeshLayer(int id, String displayName) {
        this.id = id;
        this.displayName = displayName;
    }

    public static ForgeCpuMeshLayer fromBlockState(BlockState state) {
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
