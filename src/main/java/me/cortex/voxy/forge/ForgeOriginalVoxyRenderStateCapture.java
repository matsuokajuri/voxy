package me.cortex.voxy.forge;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;

public final class ForgeOriginalVoxyRenderStateCapture {
    private static Matrix4f projection;
    private static int lightTextureId;

    private ForgeOriginalVoxyRenderStateCapture() {
    }

    public static synchronized void captureProjection(Matrix4fc value) {
        projection = value == null ? null : new Matrix4f(value);
    }

    public static synchronized void captureLightTexture(LightTexture lightTexture) {
        lightTextureId = readLightTextureId(lightTexture);
    }

    static synchronized Matrix4f projectionCopy() {
        return projection == null ? null : new Matrix4f(projection);
    }

    static synchronized int lightTextureId() {
        try {
            int currentLightTextureId = readLightTextureId(Minecraft.getInstance().gameRenderer.lightTexture());
            if (currentLightTextureId != 0) {
                lightTextureId = currentLightTextureId;
            }
        } catch (RuntimeException ignored) {
            // Keep the LevelRenderer-captured id as a startup/reload fallback.
        }
        return lightTextureId;
    }

    static synchronized void clear() {
        projection = null;
        lightTextureId = 0;
    }

    private static int readLightTextureId(LightTexture lightTexture) {
        if (lightTexture == null || lightTexture.lightTexture == null) {
            return 0;
        }
        return lightTexture.lightTexture.getId();
    }

}
