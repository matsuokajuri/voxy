package me.cortex.voxy.forge;

import me.cortex.voxy.config.ForgeVoxyConfig;
import net.minecraft.client.Minecraft;

/**
 * Keeps extra vanilla chunk meshes available as Oculus shadow casters without
 * moving the visible vanilla/LOD boundary selected by the player.
 */
public final class ForgeOriginalVoxyOculusShadowCasterRange {
    static final int MAX_RENDER_DISTANCE_CHUNKS = 32;
    public static final int HIDDEN_SHADOW_CASTER_CHUNKS = 3;

    private ForgeOriginalVoxyOculusShadowCasterRange() {
    }

    public static boolean active() {
        return ForgeVoxyConfig.isEnabledEarlySafe()
                && ForgeOriginalVoxyOculusPipelineBridge.shaderpackActive();
    }

    public static int loadedRenderDistanceChunks(int visibleRenderDistanceChunks) {
        return active()
                ? expandRenderDistanceChunks(visibleRenderDistanceChunks)
                : visibleRenderDistanceChunks;
    }

    public static int visibleRenderDistanceChunks() {
        return Minecraft.getInstance().options.getEffectiveRenderDistance();
    }

    public static float visibleRenderDistanceBlocks() {
        return visibleRenderDistanceChunks() * 16.0F;
    }

    static int expandRenderDistanceChunks(int visibleRenderDistanceChunks) {
        return Math.min(
                MAX_RENDER_DISTANCE_CHUNKS,
                visibleRenderDistanceChunks + HIDDEN_SHADOW_CASTER_CHUNKS);
    }
}
