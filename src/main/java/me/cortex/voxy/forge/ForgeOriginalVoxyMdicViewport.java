package me.cortex.voxy.forge;

import org.joml.FrustumIntersection;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector3i;
import org.joml.Vector4f;

import java.lang.reflect.Field;

final class ForgeOriginalVoxyMdicViewport {
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

    final ForgeOriginalVoxyGlBuffer drawCountCallBuffer = new ForgeOriginalVoxyGlBuffer(1024).zero();
    final ForgeOriginalVoxyGlBuffer drawCallBuffer = new ForgeOriginalVoxyGlBuffer(
            5L * 4L * (OPAQUE_DRAW_COUNT + TRANSLUCENT_DRAW_COUNT + TEMPORAL_DRAW_COUNT)).zero();
    final ForgeOriginalVoxyGlBuffer positionScratchBuffer = new ForgeOriginalVoxyGlBuffer(8L * 400_000L).zero();
    final ForgeOriginalVoxyGlBuffer indirectLookupBuffer = new ForgeOriginalVoxyGlBuffer(
            ForgeOriginalVoxyHierarchicalOcclusionTraverser.MAX_QUEUE_SIZE * 4L + 4L);
    final ForgeOriginalVoxyGlBuffer visibilityBuffer;

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
    private int hizTextureId;
    private int packedHizLevels;

    ForgeOriginalVoxyMdicViewport(int maxSectionCount) {
        try {
            this.frustumPlanes = (Vector4f[]) PLANES_FIELD.get(this.frustum);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Unable to access JOML frustum planes", e);
        }
        this.visibilityBuffer = new ForgeOriginalVoxyGlBuffer(maxSectionCount * 4L);
    }

    ForgeOriginalVoxyMdicViewport setProjection(Matrix4fc projection) {
        this.projection.set(projection);
        return this;
    }

    ForgeOriginalVoxyMdicViewport setModelView(Matrix4fc modelView) {
        this.modelView.set(modelView);
        return this;
    }

    ForgeOriginalVoxyMdicViewport setCamera(double x, double y, double z) {
        this.cameraX = x;
        this.cameraY = y;
        this.cameraZ = z;
        return this;
    }

    ForgeOriginalVoxyMdicViewport setScreenSize(int width, int height) {
        this.width = Math.max(1, width);
        this.height = Math.max(1, height);
        return this;
    }

    ForgeOriginalVoxyMdicViewport setHiz(int textureId, int packedLevels) {
        this.hizTextureId = textureId;
        this.packedHizLevels = packedLevels;
        return this;
    }

    ForgeOriginalVoxyMdicViewport update() {
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
        return this;
    }

    int renderListBufferId() {
        return this.indirectLookupBuffer.id;
    }

    long renderListBufferSize() {
        return this.indirectLookupBuffer.size();
    }

    int hizTextureId() {
        return this.hizTextureId;
    }

    int packedHizLevels() {
        return this.packedHizLevels;
    }

    boolean ready() {
        return this.drawCountCallBuffer.id != 0
                && this.drawCallBuffer.id != 0
                && this.positionScratchBuffer.id != 0
                && this.indirectLookupBuffer.id != 0
                && this.visibilityBuffer.id != 0;
    }

    void free() {
        this.visibilityBuffer.free();
        this.indirectLookupBuffer.free();
        this.drawCountCallBuffer.free();
        this.drawCallBuffer.free();
        this.positionScratchBuffer.free();
    }
}
