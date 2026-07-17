package me.cortex.voxy.forge;

import org.joml.FrustumIntersection;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector3i;
import org.joml.Vector4f;

import java.lang.reflect.Field;

final class MDICViewport {
    static final int OPAQUE_DRAW_COUNT = 400_000;
    static final int TRANSLUCENT_DRAW_COUNT = 100_000;
    static final int TEMPORAL_DRAW_COUNT = 100_000;

    private static final Field PLANES_FIELD;

    static {
        try {
            PLANES_FIELD = FrustumIntersection.class.getDeclaredField("planes");
            PLANES_FIELD.setAccessible(true);
        } catch (NoSuchFieldException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    final GlBuffer drawCountCallBuffer = new GlBuffer(1024).zero();
    final GlBuffer drawCallBuffer = new GlBuffer(
            5L * 4L * (OPAQUE_DRAW_COUNT + TRANSLUCENT_DRAW_COUNT + TEMPORAL_DRAW_COUNT)).zero();
    final GlBuffer positionScratchBuffer = new GlBuffer(8L * 400_000L).zero();
    final GlBuffer indirectLookupBuffer = new GlBuffer(
            HierarchicalOcclusionTraverser.MAX_QUEUE_SIZE * 4L + 4L);
    final GlBuffer visibilityBuffer;

    final Matrix4f vanillaProjection = new Matrix4f();
    final Matrix4f projection = new Matrix4f();
    final Matrix4f modelView = new Matrix4f();
    final Matrix4f MVP = new Matrix4f();
    final FrustumIntersection frustum = new FrustumIntersection();
    final Vector4f[] frustumPlanes;
    final Vector3i section = new Vector3i();
    final Vector3f innerTranslation = new Vector3f();

    int width = 1;
    int height = 1;
    int frameId;
    double cameraX;
    double cameraY;
    double cameraZ;
    ForgeOriginalVoxyFogParameters fogParameters = ForgeOriginalVoxyFogParameters.disabled();
    private final RenderProperties properties;
    private final HiZBuffer hiZBuffer;
    private final DepthFramebuffer depthBoundingBuffer = new DepthFramebuffer();

    MDICViewport(RenderProperties properties, int maxSectionCount) {
        this.properties = properties;
        this.hiZBuffer = new HiZBuffer(properties);
        try {
            this.frustumPlanes = (Vector4f[]) PLANES_FIELD.get(this.frustum);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Unable to access JOML frustum planes", e);
        }
        this.visibilityBuffer = new GlBuffer(maxSectionCount * 4L);
    }

    MDICViewport setVanillaProjection(Matrix4fc projection) {
        this.vanillaProjection.set(projection);
        return this;
    }

    MDICViewport setProjection(Matrix4fc projection) {
        this.projection.set(projection);
        return this;
    }

    MDICViewport setModelView(Matrix4fc modelView) {
        this.modelView.set(modelView);
        return this;
    }

    MDICViewport setCamera(double x, double y, double z) {
        this.cameraX = x;
        this.cameraY = y;
        this.cameraZ = z;
        return this;
    }

    MDICViewport setScreenSize(int width, int height) {
        this.width = Math.max(1, width);
        this.height = Math.max(1, height);
        return this;
    }

    MDICViewport setFogParameters(ForgeOriginalVoxyFogParameters fogParameters) {
        this.fogParameters = fogParameters == null ? ForgeOriginalVoxyFogParameters.disabled() : fogParameters;
        return this;
    }

    MDICViewport update() {
        this.projection.mul(this.modelView, this.MVP);
        this.frustum.set(this.MVP, false);
        int sx = (int) Math.floor(this.cameraX) >> 5;
        int sy = (int) Math.floor(this.cameraY) >> 5;
        int sz = (int) Math.floor(this.cameraZ) >> 5;
        this.section.set(sx, sy, sz);
        this.innerTranslation.set(
                (float) (this.cameraX - (sx << 5)),
                (float) (this.cameraY - (sy << 5)),
                (float) (this.cameraZ - (sz << 5)));
        if (this.depthBoundingBuffer.resize(this.width, this.height)) {
            this.depthBoundingBuffer.clear(this.properties.inverseClearDepth());
        }
        return this;
    }

    MDICViewport copyFrustumFrom(FrustumIntersection source) {
        if (source == null) {
            throw new IllegalArgumentException("source");
        }
        try {
            Vector4f[] sourcePlanes = (Vector4f[]) PLANES_FIELD.get(source);
            for (int i = 0; i < this.frustumPlanes.length; i++) {
                this.frustumPlanes[i].set(sourcePlanes[i]);
            }
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Unable to copy supplied frustum planes", e);
        }
        return this;
    }

    void buildHizFromSourceDepth(int sourceDepthTextureId, int sourceWidth, int sourceHeight) {
        this.hiZBuffer.buildMipChain(sourceDepthTextureId, sourceWidth, sourceHeight);
    }

    int renderListBufferId() {
        return this.indirectLookupBuffer.id;
    }

    long renderListBufferSize() {
        return this.indirectLookupBuffer.size();
    }

    int hizTextureId() {
        return this.hiZBuffer.getHizTextureId();
    }

    int depthBoundingTextureId() {
        return this.depthBoundingBuffer.getDepthTex();
    }

    void clearDepthBounding(float depth) {
        this.depthBoundingBuffer.clear(depth);
    }

    void bindDepthBoundingFramebuffer() {
        this.depthBoundingBuffer.bind();
    }

    int packedHizLevels() {
        return this.hiZBuffer.getPackedLevels();
    }

    boolean ready() {
        return this.drawCountCallBuffer.id != 0
                && this.drawCallBuffer.id != 0
                && this.positionScratchBuffer.id != 0
                && this.indirectLookupBuffer.id != 0
                && this.visibilityBuffer.id != 0;
    }

    void free() {
        this.depthBoundingBuffer.free();
        this.hiZBuffer.free();
        this.visibilityBuffer.free();
        this.indirectLookupBuffer.free();
        this.drawCountCallBuffer.free();
        this.drawCallBuffer.free();
        this.positionScratchBuffer.free();
    }
}
