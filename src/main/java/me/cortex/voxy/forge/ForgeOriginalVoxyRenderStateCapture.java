package me.cortex.voxy.forge;

import net.minecraft.client.Minecraft;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public final class ForgeOriginalVoxyRenderStateCapture {
    private static Matrix4f projection;
    private static long projectionGeneration;
    private static CapturedViewportParameters oculusViewportParameters;
    private static long oculusViewportGeneration;
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

    public static synchronized void captureOculusViewport(
            Matrix4fc vanillaProjection,
            Matrix4fc modelView,
            double cameraX,
            double cameraY,
            double cameraZ) {
        if (vanillaProjection == null || modelView == null) {
            oculusViewportParameters = null;
        } else {
            oculusViewportParameters = new CapturedViewportParameters(
                    new Matrix4f(vanillaProjection),
                    new Matrix4f(modelView),
                    cameraX,
                    cameraY,
                    cameraZ,
                    ++oculusViewportGeneration);
            return;
        }
        oculusViewportGeneration++;
    }

    static synchronized Matrix4f projectionCopy() {
        return projection == null ? null : new Matrix4f(projection);
    }

    static synchronized CapturedViewportParameters oculusViewportParametersCopy() {
        return oculusViewportParameters == null ? null : oculusViewportParameters.copy();
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

    static synchronized long projectionGeneration() {
        return projectionGeneration;
    }

    static synchronized long oculusViewportGeneration() {
        return oculusViewportGeneration;
    }

    static synchronized void clear() {
        projection = null;
        oculusViewportParameters = null;
        lightTextureId = 0;
        projectionGeneration++;
        oculusViewportGeneration++;
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

    record CapturedViewportParameters(
            Matrix4f vanillaProjection,
            Matrix4f modelView,
            double cameraX,
            double cameraY,
            double cameraZ,
            long generation) {
        CapturedViewportParameters copy() {
            return new CapturedViewportParameters(
                    new Matrix4f(this.vanillaProjection),
                    new Matrix4f(this.modelView),
                    this.cameraX,
                    this.cameraY,
                    this.cameraZ,
                    this.generation);
        }
    }
}
