package me.cortex.voxy.forge;

import me.cortex.voxy.config.ForgeVoxyConfig;
import net.irisshaders.iris.gl.uniform.UniformHolder;
import org.joml.Matrix4f;

import java.util.function.Supplier;

import static net.irisshaders.iris.gl.uniform.UniformUpdateFrequency.PER_FRAME;

public final class ForgeOriginalVoxyOculusVoxyUniforms {
    private static final Object LOCK = new Object();
    private static final Matrix4f VIEW_PROJECTION = new Matrix4f();
    private static final Matrix4f MODEL_VIEW = new Matrix4f();
    private static final Matrix4f PROJECTION = new Matrix4f();

    private ForgeOriginalVoxyOculusVoxyUniforms() {
    }

    static void captureViewport(MDICViewport viewport) {
        synchronized (LOCK) {
            VIEW_PROJECTION.set(viewport.MVP);
            MODEL_VIEW.set(viewport.modelView);
            PROJECTION.set(viewport.projection);
        }
    }

    public static Matrix4f getViewProjection() {
        synchronized (LOCK) {
            return new Matrix4f(VIEW_PROJECTION);
        }
    }

    public static Matrix4f getModelView() {
        synchronized (LOCK) {
            return new Matrix4f(MODEL_VIEW);
        }
    }

    public static Matrix4f getProjection() {
        synchronized (LOCK) {
            return new Matrix4f(PROJECTION);
        }
    }

    public static void addUniforms(UniformHolder uniforms) {
        uniforms
                .uniform1i(
                        PER_FRAME,
                        "vxRenderDistance",
                        () -> (int) Math.round(ForgeVoxyConfig.ORIGINAL_VOXY_SECTION_RENDER_DISTANCE.get() * 32.0D))
                .uniformMatrix(PER_FRAME, "vxViewProj", ForgeOriginalVoxyOculusVoxyUniforms::getViewProjection)
                .uniformMatrix(PER_FRAME, "vxViewProjInv", new Inverted(ForgeOriginalVoxyOculusVoxyUniforms::getViewProjection))
                .uniformMatrix(PER_FRAME, "vxViewProjPrev", new PreviousMat(ForgeOriginalVoxyOculusVoxyUniforms::getViewProjection))
                .uniformMatrix(PER_FRAME, "vxModelView", ForgeOriginalVoxyOculusVoxyUniforms::getModelView)
                .uniformMatrix(PER_FRAME, "vxModelViewInv", new Inverted(ForgeOriginalVoxyOculusVoxyUniforms::getModelView))
                .uniformMatrix(PER_FRAME, "vxModelViewPrev", new PreviousMat(ForgeOriginalVoxyOculusVoxyUniforms::getModelView))
                .uniformMatrix(PER_FRAME, "vxProj", ForgeOriginalVoxyOculusVoxyUniforms::getProjection)
                .uniformMatrix(PER_FRAME, "vxProjInv", new Inverted(ForgeOriginalVoxyOculusVoxyUniforms::getProjection))
                .uniformMatrix(PER_FRAME, "vxProjPrev", new PreviousMat(ForgeOriginalVoxyOculusVoxyUniforms::getProjection));
    }

    private record Inverted(Supplier<Matrix4f> parent) implements Supplier<Matrix4f> {
        @Override
        public Matrix4f get() {
            return new Matrix4f(this.parent.get()).invert();
        }
    }

    private static final class PreviousMat implements Supplier<Matrix4f> {
        private final Supplier<Matrix4f> parent;
        private Matrix4f previous = new Matrix4f();

        private PreviousMat(Supplier<Matrix4f> parent) {
            this.parent = parent;
        }

        @Override
        public Matrix4f get() {
            Matrix4f oldPrevious = this.previous;
            this.previous = new Matrix4f(this.parent.get());
            return oldPrevious;
        }
    }
}
