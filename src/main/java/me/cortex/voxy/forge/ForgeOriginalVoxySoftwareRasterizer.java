package me.cortex.voxy.forge;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.system.MemoryUtil;

import java.util.Arrays;

final class ForgeOriginalVoxySoftwareRasterizer {
    private static final long DEPTH_MASK = ((1L << 24) - 1) << (64 - 24);
    private static final long CLEAR_VALUE = DEPTH_MASK;
    private static final int CHANNEL_MASK = 0x00FF00FF;

    private final Vector4f scratch = new Vector4f();
    private final Vector3f scratch1 = new Vector3f();
    private final Vector3f scratch2 = new Vector3f();
    private final Vector3f scratch3 = new Vector3f();
    private final Vector3f scratch4 = new Vector3f();
    private final Vector3f qmuv1 = new Vector3f();
    private final Vector3f qmuv2 = new Vector3f();
    private final Vector3f qmuv3 = new Vector3f();
    private final Vector3f qmuv4 = new Vector3f();
    private final Vector3f scratchR1 = new Vector3f();
    private final Vector3f scratchR2 = new Vector3f();
    private final Vector3f scratchR3 = new Vector3f();
    private final Vector3f a1 = new Vector3f();
    private final Vector3f a2 = new Vector3f();
    private final Vector3f a3 = new Vector3f();

    private final int targetSize;
    private final long[] framebuffer;
    private boolean cullBackFace;
    private boolean doTheBlending;
    private int samplerWidth;
    private int samplerHeight;
    private int[] samplerTexture;

    ForgeOriginalVoxySoftwareRasterizer(int targetSize) {
        this.targetSize = targetSize;
        this.framebuffer = new long[targetSize * targetSize];
    }

    void setFaceCull(boolean isBackFaceCulling) {
        this.cullBackFace = isBackFaceCulling;
    }

    void setBlending(boolean blending) {
        this.doTheBlending = blending;
    }

    void setSamplerTexture(int[] texture, int width, int height) {
        if (texture.length != width * height) {
            throw new IllegalArgumentException("sampler texture size mismatch");
        }
        this.samplerTexture = texture;
        this.samplerWidth = width;
        this.samplerHeight = height;
    }

    void clear() {
        Arrays.fill(this.framebuffer, CLEAR_VALUE);
    }

    void raster(Matrix4f mvp, ForgeOriginalVoxyReuseVertexConsumer vertices) {
        this.raster(mvp, vertices.getAddress(), vertices.quadCount());
    }

    void raster(Matrix4f mvp, long verticesAddr, int quadCount) {
        if (quadCount == 0) {
            return;
        }
        for (int i = 0; i < quadCount; i++) {
            this.rasterQuad(mvp, verticesAddr + ForgeOriginalVoxyReuseVertexConsumer.VERTEX_FORMAT_SIZE * 4L * i);
        }
    }

    ForgeOriginalVoxyColourDepthTextureData copyFace() {
        int[] colour = new int[this.framebuffer.length];
        int[] depth = new int[this.framebuffer.length];
        for (int i = 0; i < this.framebuffer.length; i++) {
            colour[i] = (int) this.framebuffer[i];
            depth[i] = (int) (this.framebuffer[i] >>> 32);
        }
        return new ForgeOriginalVoxyColourDepthTextureData(colour, depth, this.targetSize, this.targetSize);
    }

    long[] getRawFramebuffer() {
        return this.framebuffer;
    }

    private void rasterQuad(Matrix4f transform, long addr) {
        this.loadTransformPos(transform, addr, 0, this.scratch1, this.qmuv1);
        this.loadTransformPos(transform, addr, 1, this.scratch2, this.qmuv2);
        this.loadTransformPos(transform, addr, 2, this.scratch3, this.qmuv3);
        this.loadTransformPos(transform, addr, 3, this.scratch4, this.qmuv4);
        this.rasterConvexQuad();
    }

    //Rasterize the whole quad with four edge functions instead of two independent triangles.
    // Splitting into triangles made the shared diagonal a coverage boundary evaluated with
    // OPPOSITE vertex orders by the two triangles; float rounding then let edge pixels fail both
    // tests, leaving a one-pixel diagonal gap of clear-value texels baked into some face textures
    // (the visible corner-to-center slash on LOD faces). With one coverage test per quad the
    // interior diagonal cannot leak; the triangle split below is only used to pick a barycentric
    // basis for attribute interpolation, which is continuous across the diagonal.
    private void rasterConvexQuad() {
        Vector3f v1 = this.scratch1;
        Vector3f v2 = this.scratch2;
        Vector3f v3 = this.scratch3;
        Vector3f v4 = this.scratch4;
        float areaA = edge(v1, v2, v3);
        float areaB = edge(v3, v4, v1);
        float area = areaA + areaB;
        if (area < 0 == this.cullBackFace) {
            return;
        }
        if (Math.abs(area) < 0.001F) {
            return;
        }
        float sign = area > 0.0F ? 1.0F : -1.0F;
        boolean triangleAUsable = Math.abs(areaA) >= 0.001F;
        boolean triangleBUsable = Math.abs(areaB) >= 0.001F;
        float invAreaA = triangleAUsable ? 1.0F / areaA : 0.0F;
        float invAreaB = triangleBUsable ? 1.0F / areaB : 0.0F;
        int minX = Math.max((int) Math.floor(Math.min(Math.min(v1.x, v2.x), Math.min(v3.x, v4.x))), 0);
        int maxX = Math.min((int) Math.ceil(Math.max(Math.max(v1.x, v2.x), Math.max(v3.x, v4.x))), this.targetSize - 1);
        int minY = Math.max((int) Math.floor(Math.min(Math.min(v1.y, v2.y), Math.min(v3.y, v4.y))), 0);
        int maxY = Math.min((int) Math.ceil(Math.max(Math.max(v1.y, v2.y), Math.max(v3.y, v4.y))), this.targetSize - 1);
        for (int py = minY; py <= maxY; py++) {
            for (int px = minX; px <= maxX; px++) {
                float cx = px + 0.5F;
                float cy = py + 0.5F;
                //A degenerate edge (duplicated vertex encoding a triangle as a quad) evaluates to
                // exactly 0 and so imposes no constraint under >= 0.
                if (edge(v1, v2, cx, cy) * sign < 0.0F
                        || edge(v2, v3, cx, cy) * sign < 0.0F
                        || edge(v3, v4, cx, cy) * sign < 0.0F
                        || edge(v4, v1, cx, cy) * sign < 0.0F) {
                    continue;
                }
                boolean useTriangleA = triangleAUsable
                        && (!triangleBUsable || edge(v3, v1, cx, cy) * sign >= 0.0F);
                float w1;
                float w2;
                float w3;
                if (useTriangleA) {
                    w1 = edge(v2, v3, cx, cy) * invAreaA;
                    w2 = edge(v3, v1, cx, cy) * invAreaA;
                    w3 = 1.0F - w1 - w2;
                    this.scratchR1.set(v1);
                    this.scratchR2.set(v2);
                    this.scratchR3.set(v3);
                    this.a1.set(this.qmuv1);
                    this.a2.set(this.qmuv2);
                    this.a3.set(this.qmuv3);
                } else {
                    if (!triangleBUsable) {
                        continue;
                    }
                    w1 = edge(v4, v1, cx, cy) * invAreaB;
                    w2 = edge(v1, v3, cx, cy) * invAreaB;
                    w3 = 1.0F - w1 - w2;
                    this.scratchR1.set(v3);
                    this.scratchR2.set(v4);
                    this.scratchR3.set(v1);
                    this.a1.set(this.qmuv3);
                    this.a2.set(this.qmuv4);
                    this.a3.set(this.qmuv1);
                }
                this.rasterPixel(px + py * this.targetSize, w1, w2, w3);
            }
        }
    }

    private void rasterPixel(int index, float b1, float b2, float b3) {
        float z = Math.fma(b1, this.scratchR1.z, Math.fma(b2, this.scratchR2.z, b3 * this.scratchR3.z));
        z = Math.fma(z, 0.5F, 0.5F);
        if (z < 0.0F && -0.000001F <= z) {
            z = 0.0F;
        }
        if (z < 0.0F || z > 1.0F) {
            return;
        }

        int meta = Float.floatToRawIntBits(this.a1.x);
        float u = Math.fma(b1, this.a1.y, Math.fma(b2, this.a2.y, b3 * this.a3.y));
        float v = Math.fma(b1, this.a1.z, Math.fma(b2, this.a2.z, b3 * this.a3.z));
        int colour = this.sampleTexture(u, v);
        if ((meta & 1) != 0 && (colour >>> 24) <= 0) {
            return;
        }

        this.framebuffer[index] += 1L << 32;
        long depthVal = ((long) (((double) z) * ((1 << 24) - 1))) << (64 - 24);
        if (depthVal == DEPTH_MASK) {
            depthVal--;
        }
        if (Long.compareUnsigned(this.framebuffer[index], depthVal) <= 0) {
            return;
        }
        this.framebuffer[index] &= ~DEPTH_MASK;
        this.framebuffer[index] |= depthVal;
        this.framebuffer[index] &= ~(1L << 39);
        this.framebuffer[index] |= ((long) (meta & 4)) << 37;

        int srcColour = (int) this.framebuffer[index];
        this.framebuffer[index] &= ~Integer.toUnsignedLong(-1);
        if (this.doTheBlending) {
            colour = doBlending(srcColour, colour);
        }
        this.framebuffer[index] |= Integer.toUnsignedLong(colour);
    }

    private int sampleTexture(float u, float v) {
        if (this.samplerTexture == null || this.samplerWidth <= 0 || this.samplerHeight <= 0) {
            return 0;
        }
        int pu = clamp(Math.round(u * this.samplerWidth - 0.5F), 0, this.samplerWidth - 1);
        int pv = clamp(Math.round(v * this.samplerHeight - 0.5F), 0, this.samplerHeight - 1);
        return this.samplerTexture[this.samplerWidth * pv + pu];
    }

    private void loadTransformPos(Matrix4f transform, long addr, int vert, Vector3f out, Vector3f otherAttributesOut) {
        long vertexAddr = addr + vert * ForgeOriginalVoxyReuseVertexConsumer.VERTEX_FORMAT_SIZE;
        this.scratch.set(
                MemoryUtil.memGetFloat(vertexAddr),
                MemoryUtil.memGetFloat(vertexAddr + 4),
                MemoryUtil.memGetFloat(vertexAddr + 8),
                1.0F
        );
        otherAttributesOut.set(
                Float.intBitsToFloat(MemoryUtil.memGetInt(vertexAddr + 12)),
                MemoryUtil.memGetFloat(vertexAddr + 16),
                MemoryUtil.memGetFloat(vertexAddr + 20)
        );
        Vector4f vec = transform.transformProject(this.scratch);
        if (Math.abs(this.scratch.w - 1.0F) > 0.000001F) {
            throw new IllegalStateException("software rasterizer projected w drifted");
        }
        out.set(
                Math.fma(vec.x, 0.5F, 0.5F) * this.targetSize,
                Math.fma(vec.y, 0.5F, 0.5F) * this.targetSize,
                vec.z
        );
    }

    private static int doBlending(int scr, int dst) {
        int srcAlpha = (scr >>> 24) & 0xFF;
        if (srcAlpha == 0) {
            return dst;
        }
        int dstAlpha = (dst >>> 24) & 0xFF;
        scr &= ~(0xFF << 24);
        dst &= ~(0xFF << 24);
        int blendAlpha = Math.min(0xFF, srcAlpha + ((dstAlpha * (255 - srcAlpha)) >> 8));
        int blend = mix(dst, scr, dstAlpha);
        return blend | (blendAlpha << 24);
    }

    private static int mix(int aColor, int bColor, int ratio) {
        int aRatio = ratio & 0xFF;
        int bRatio = 256 - aRatio;
        int a1 = aColor & CHANNEL_MASK;
        int b1 = bColor & CHANNEL_MASK;
        int a2 = (aColor >> 8) & CHANNEL_MASK;
        int b2 = (bColor >> 8) & CHANNEL_MASK;
        int c1 = (((a1 * aRatio) + (b1 * bRatio)) >> 8) & CHANNEL_MASK;
        int c2 = (((a2 * aRatio) + (b2 * bRatio)) >> 8) & CHANNEL_MASK;
        return c1 | (c2 << 8);
    }

    private static float edge(Vector3f a, Vector3f b, Vector3f c) {
        return (c.x - a.x) * (b.y - a.y) - (c.y - a.y) * (b.x - a.x);
    }

    private static float edge(Vector3f a, Vector3f b, float cx, float cy) {
        return (cx - a.x) * (b.y - a.y) - (cy - a.y) * (b.x - a.x);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
