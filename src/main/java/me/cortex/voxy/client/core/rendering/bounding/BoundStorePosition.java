package me.cortex.voxy.client.core.rendering.bounding;

import me.cortex.voxy.commonImpl.VoxyCommon;
import net.minecraft.core.SectionPos;

/** Backend-neutral owner of the original bound-store position transform. */
public final class BoundStorePosition {
    private BoundStorePosition() {
    }

    public static long transform(long pos) {
        if (!VoxyCommon.IS_MINE_IN_ABYSS) return pos;

        int x = SectionPos.x(pos);
        int y = SectionPos.y(pos);
        int sector = (x + 512) >> 10;
        x -= sector << 10;
        y += 16 + (256 - 32 - sector * 30);
        return SectionPos.asLong(x, y, SectionPos.z(pos));
    }
}
