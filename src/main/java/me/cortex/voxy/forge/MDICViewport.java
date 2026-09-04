package me.cortex.voxy.forge;

import org.joml.FrustumIntersection;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector3i;
import org.joml.Vector4f;

import java.lang.reflect.Field;

final class MDICViewport {
    static final int OPAQUE_DRAW_COUNT = GpuBufferLayout.OPAQUE_CAPACITY;
    static final int TRANSLUCENT_DRAW_COUNT = GpuBufferLayout.TRANSLUCENT_CAPACITY;
    static final int TEMPORAL_DRAW_COUNT = GpuBufferLayout.TEMPORAL_CAPACITY;
    static final int COMMAND_DIAGNOSTICS_OFFSET = GpuBufferLayout.DRAW_COUNT_ABI_BYTES;
    static final int COMMAND_DIAGNOSTICS_BYTES = 6 * Integer.BYTES;

    private static final Field PLANES_FIELD;

    static {
        try {
            PLANES_FIELD = FrustumIntersection.class.getDeclaredField("planes");
            PLANES_FIELD.setAccessible(true);
        } catch (NoSuchFieldException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    final GlBuffer drawCountCallBuffer = new GlBuffer(GpuBufferLayout.DRAW_COUNT_BYTES).zero();
    final GlBuffer commandDispatchBuffer = new GlBuffer(GpuBufferLayout.DISPATCH_BYTES).zero();
    final GlBuffer drawCallBuffer = new GlBuffer(GpuBufferLayout.DRAW_BUFFER_BYTES).zero();
    final GlBuffer positionScratchBuffer = new GlBuffer(GpuBufferLayout.POSITION_BUFFER_BYTES).zero();
    final GlBuffer indirectLookupBuffer = new GlBuffer(GpuBufferLayout.HOC_RENDER_LIST_BYTES);
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
    private int commandDiagnosticFrame;
    private boolean commandDiagnosticReadPending;
    private int reportedCommandOverflowFlags;
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
                && this.commandDispatchBuffer.id != 0
                && this.drawCallBuffer.id != 0
                && this.positionScratchBuffer.id != 0
                && this.indirectLookupBuffer.id != 0
                && this.visibilityBuffer.id != 0;
    }

    boolean beginCommandDiagnosticSample() {
        // Diagnostics are sticky on the GPU, so a small asynchronous sample cannot miss
        // an overflow between samples. Never introduce a synchronous per-frame readback.
        if (this.commandDiagnosticReadPending || (this.commandDiagnosticFrame++ & 63) != 0) {
            return false;
        }
        this.commandDiagnosticReadPending = true;
        return true;
    }

    void acceptCommandDiagnostics(int flags, long opaque, long translucent, long temporal,
                                  long inputs, long translucentBuilds) {
        this.commandDiagnosticReadPending = false;
        int newlyReported = flags & ~this.reportedCommandOverflowFlags;
        if (newlyReported != 0) {
            this.reportedCommandOverflowFlags |= flags;
            VoxyForge.LOGGER.warn(
                    "Original Voxy MDIC capacity/input rejection: flags=0x{}, opaqueCommands={}, "
                            + "translucentCommands={}, temporalCommands={}, inputs={}, translucentBuilds={}. "
                            + "Rejected batches are not drawn; command counts reset and retry next frame. "
                            + "Persistent capacity pressure can still omit geometry.",
                    Integer.toHexString(flags), opaque, translucent, temporal, inputs, translucentBuilds);
        }
    }

    void free() {
        this.depthBoundingBuffer.free();
        this.hiZBuffer.free();
        this.visibilityBuffer.free();
        this.indirectLookupBuffer.free();
        this.drawCountCallBuffer.free();
        this.commandDispatchBuffer.free();
        this.drawCallBuffer.free();
        this.positionScratchBuffer.free();
    }
}
