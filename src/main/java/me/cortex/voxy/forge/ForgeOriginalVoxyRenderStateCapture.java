package me.cortex.voxy.forge;

import org.joml.Matrix4f;
import org.joml.Matrix4fc;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public final class ForgeOriginalVoxyRenderStateCapture {
    private static Matrix4f projection;
    private static long projectionGeneration;
    private static int lightTextureId;

    private ForgeOriginalVoxyRenderStateCapture() {
    }

    public static synchronized void captureProjection(Matrix4fc value) {
        projection = value == null ? null : new Matrix4f(value);
        projectionGeneration++;
    }

    public static synchronized void captureLightTexture(Object lightTexture) {
        lightTextureId = readLightTextureId(lightTexture);
    }

    static synchronized Matrix4f projectionCopy() {
        return projection == null ? null : new Matrix4f(projection);
    }

    static synchronized int lightTextureId() {
        return lightTextureId;
    }

    static synchronized long projectionGeneration() {
        return projectionGeneration;
    }

    static synchronized void clear() {
        projection = null;
        lightTextureId = 0;
        projectionGeneration++;
    }

    private static int readLightTextureId(Object lightTexture) {
        if (lightTexture == null) {
            return 0;
        }
        try {
            Field field = lightTexture.getClass().getDeclaredField("lightTexture");
            field.setAccessible(true);
            Object texture = field.get(lightTexture);
            if (texture == null) {
                return 0;
            }
            Method method = texture.getClass().getMethod("getId");
            Object id = method.invoke(texture);
            return id instanceof Number number ? number.intValue() : 0;
        } catch (ReflectiveOperationException ignored) {
            return 0;
        }
    }
}
