package me.cortex.voxy.forge;

import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

final class ForgeMdicVisibilityTracker {
    private ForgeMdicVisibilityTracker() {
    }

    static ForgeMdicVisibilitySnapshot capture(Minecraft minecraft, boolean useFrustum) {
        if (minecraft == null || minecraft.level == null || minecraft.player == null) {
            return ForgeMdicVisibilitySnapshot.missingCamera("CAMERA_UNAVAILABLE");
        }
        Vec3 position = cameraPositionOrPlayer(minecraft);
        int chunkX = floorDiv(position.x, 16.0D);
        int chunkZ = floorDiv(position.z, 16.0D);
        int sectionX = floorDiv(position.x, 32.0D);
        int sectionY = floorDiv(position.y, 32.0D);
        int sectionZ = floorDiv(position.z, 32.0D);
        // Forge's command execution context does not expose a reliable current frustum here without
        // reaching into renderer-specific integrations. G6.5 records this explicitly and falls back.
        String frustumReason = useFrustum ? "FRUSTUM_UNAVAILABLE" : "FRUSTUM_DISABLED";
        return new ForgeMdicVisibilitySnapshot(true, position.x, position.y, position.z, chunkX, chunkZ, sectionX, sectionY, sectionZ, false, frustumReason, System.currentTimeMillis());
    }

    private static Vec3 cameraPositionOrPlayer(Minecraft minecraft) {
        if (minecraft.gameRenderer != null && minecraft.gameRenderer.getMainCamera() != null) {
            return minecraft.gameRenderer.getMainCamera().getPosition();
        }
        return minecraft.player == null ? Vec3.ZERO : minecraft.player.position();
    }

    private static int floorDiv(double value, double divisor) {
        return (int) Math.floor(value / divisor);
    }
}
