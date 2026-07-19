package me.cortex.voxy.forge;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;

public final class ForgeOriginalVoxyRenderStateCapture {
    private static Matrix4f projection;
    private static Matrix4f modelView;
    private static double cameraX;
    private static double cameraY;
    private static double cameraZ;
    private static long viewportGeneration;
    private static int lightTextureId;

    private ForgeOriginalVoxyRenderStateCapture() {
    }

    public static synchronized void captureProjection(Matrix4fc value) {
        projection = value == null ? null : new Matrix4f(value);
    }

    public static synchronized void captureViewport(
            Matrix4fc projectionValue,
            Matrix4fc modelViewValue,
            double x,
            double y,
            double z) {
        projection = projectionValue == null ? null : new Matrix4f(projectionValue);
        modelView = modelViewValue == null ? null : new Matrix4f(modelViewValue);
        cameraX = x;
        cameraY = y;
        cameraZ = z;
        viewportGeneration++;
    }

    public static synchronized void captureLightTexture(LightTexture lightTexture) {
        lightTextureId = readLightTextureId(lightTexture);
    }

    static synchronized Matrix4f projectionCopy() {
        return projection == null ? null : new Matrix4f(projection);
    }

    static synchronized CapturedViewport viewportCopy() {
        if (projection == null || modelView == null) {
            return null;
        }
        return new CapturedViewport(
                new Matrix4f(projection),
                new Matrix4f(modelView),
                cameraX,
                cameraY,
                cameraZ,
                viewportGeneration);
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
        modelView = null;
        cameraX = 0.0D;
        cameraY = 0.0D;
        cameraZ = 0.0D;
        viewportGeneration++;
        lightTextureId = 0;
    }

    private static int readLightTextureId(LightTexture lightTexture) {
        if (lightTexture == null || lightTexture.lightTexture == null) {
            return 0;
        }
        return lightTexture.lightTexture.getId();
    }

    record CapturedViewport(
            Matrix4f projection,
            Matrix4f modelView,
            double cameraX,
            double cameraY,
            double cameraZ,
            long generation) {
    }

}
