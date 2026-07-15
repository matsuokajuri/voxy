package me.cortex.voxy.client.core.rendering;

import me.cortex.voxy.client.core.RenderProperties;
import me.cortex.voxy.client.core.rendering.util.VulkanDepthBoundingTarget;
import me.cortex.voxy.client.core.rendering.util.VulkanHiZBuffer;
import me.cortex.voxy.client.core.vulkan.VoxyVulkanBuffer;
import net.caffeinemc.mods.sodium.client.util.FogParameters;
import net.minecraft.util.Mth;
import org.joml.FrustumIntersection;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector3i;
import org.joml.Vector4f;

import java.lang.reflect.Field;

/** Vulkan viewport owner preserving the original CPU camera/frustum/translation contract. */
public abstract class VulkanViewport<A extends VulkanViewport<A>> implements AutoCloseable {
    private static final Field PLANES_FIELD;

    static {
        try {
            PLANES_FIELD = FrustumIntersection.class.getDeclaredField("planes");
            PLANES_FIELD.setAccessible(true);
        } catch (NoSuchFieldException exception) {
            throw new RuntimeException(exception);
        }
    }

    public final VulkanHiZBuffer hiZBuffer;
    public final VulkanDepthBoundingTarget depthBoundingBuffer;
    public int width;
    public int height;
    public int frameId;
    public final Matrix4f vanillaProjection = new Matrix4f();
    public final Matrix4f projection = new Matrix4f();
    public final Matrix4f modelView = new Matrix4f();
    public final FrustumIntersection frustum = new FrustumIntersection();
    public final Vector4f[] frustumPlanes;
    public double cameraX;
    public double cameraY;
    public double cameraZ;
    public FogParameters fogParameters;
    public final Matrix4f MVP = new Matrix4f();
    public final Vector3i section = new Vector3i();
    public final Vector3f innerTranslation = new Vector3f();
    private final RenderProperties properties;
    private boolean closed;

    protected VulkanViewport(RenderProperties properties) {
        this.properties = properties;
        try {
            this.frustumPlanes = (Vector4f[]) PLANES_FIELD.get(this.frustum);
        } catch (IllegalAccessException exception) {
            throw new RuntimeException(exception);
        }
        this.hiZBuffer = new VulkanHiZBuffer(properties);
        try {
            this.depthBoundingBuffer = new VulkanDepthBoundingTarget();
        } catch (RuntimeException | Error exception) {
            this.hiZBuffer.close();
            throw exception;
        }
    }

    @SuppressWarnings("unchecked")
    public A setVanillaProjection(Matrix4fc projection) {
        this.vanillaProjection.set(projection);
        return (A) this;
    }

    @SuppressWarnings("unchecked")
    public A setProjection(Matrix4f projection) {
        this.projection.set(projection);
        return (A) this;
    }

    @SuppressWarnings("unchecked")
    public A setModelView(Matrix4fc modelView) {
        this.modelView.set(modelView);
        return (A) this;
    }

    @SuppressWarnings("unchecked")
    public A setCamera(double x, double y, double z) {
        this.cameraX = x;
        this.cameraY = y;
        this.cameraZ = z;
        return (A) this;
    }

    @SuppressWarnings("unchecked")
    public A setScreenSize(int width, int height) {
        this.width = width;
        this.height = height;
        return (A) this;
    }

    @SuppressWarnings("unchecked")
    public A setFogParameters(FogParameters fogParameters) {
        this.fogParameters = fogParameters;
        return (A) this;
    }

    @SuppressWarnings("unchecked")
    public A update() {
        this.projection.mul(this.modelView, this.MVP);
        this.frustum.set(this.MVP, false);

        int sx = Mth.floor(this.cameraX) >> 5;
        int sy = Mth.floor(this.cameraY) >> 5;
        int sz = Mth.floor(this.cameraZ) >> 5;
        this.section.set(sx, sy, sz);
        this.innerTranslation.set(
                (float) (this.cameraX - (sx << 5)),
                (float) (this.cameraY - (sy << 5)),
                (float) (this.cameraZ - (sz << 5))
        );
        this.depthBoundingBuffer.resize(this.width, this.height, this.properties.inverseClearDepth());
        return (A) this;
    }

    @Override
    public void close() {
        if (this.closed) return;
        this.closed = true;
        this.hiZBuffer.close();
        this.depthBoundingBuffer.close();
        this.close0();
    }

    protected void close0() {
    }

    public abstract VoxyVulkanBuffer getRenderList();
}
