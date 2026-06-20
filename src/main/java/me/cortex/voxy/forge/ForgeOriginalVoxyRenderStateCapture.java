package me.cortex.voxy.forge;

import org.joml.Matrix4f;
import org.joml.Matrix4fc;

public final class ForgeOriginalVoxyRenderStateCapture {
    private static Matrix4f projection;
    private static long projectionGeneration;

    private ForgeOriginalVoxyRenderStateCapture() {
    }

    public static synchronized void captureProjection(Matrix4fc value) {
        projection = value == null ? null : new Matrix4f(value);
        projectionGeneration++;
    }

    static synchronized Matrix4f projectionCopy() {
        return projection == null ? null : new Matrix4f(projection);
    }

    static synchronized long projectionGeneration() {
        return projectionGeneration;
    }

    static synchronized void clear() {
        projection = null;
        projectionGeneration++;
    }
}
