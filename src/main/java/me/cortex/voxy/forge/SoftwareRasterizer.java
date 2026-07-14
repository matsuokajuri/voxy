package me.cortex.voxy.forge;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.system.MemoryUtil;

import java.util.Arrays;

final class SoftwareRasterizer {
    private static final long DEPTH_MASK = ((1L << 24) - 1) << (64 - 24);
    private static final long CLEAR_VALUE = DEPTH_MASK;
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

    SoftwareRasterizer(int targetSize) {
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

    void raster(Matrix4f mvp, ReuseVertexConsumer vertices) {
        this.raster(mvp, vertices.getAddress(), vertices.quadCount());
    }

    void raster(Matrix4f mvp, long verticesAddr, int quadCount) {
        if (quadCount == 0) {
            return;
        }
        for (int i = 0; i < quadCount; i++) {
            this.rasterQuad(mvp, verticesAddr + ReuseVertexConsumer.VERTEX_FORMAT_SIZE * 4L * i);
        }
    }

    ColourDepthTextureData copyFace() {
        int[] colour = new int[this.framebuffer.length];
        int[] depth = new int[this.framebuffer.length];
        for (int i = 0; i < this.framebuffer.length; i++) {
            colour[i] = (int) this.framebuffer[i];
            depth[i] = (int) (this.framebuffer[i] >>> 32);
        }
        return new ColourDepthTextureData(colour, depth, this.targetSize, this.targetSize);
    }

    long[] getRawFramebuffer() {
        return this.framebuffer;
    }

    private void rasterQuad(Matrix4f transform, long addr) {
        this.loadTransformPos(transform, addr, 0, this.scratch1, this.qmuv1);
        this.loadTransformPos(transform, addr, 1, this.scratch2, this.qmuv2);
        this.loadTransformPos(transform, addr, 2, this.scratch3, this.qmuv3);
        this.loadTransformPos(transform, addr, 3, this.scratch4, this.qmuv4);

        //0,1,2 | 2,3,0
        this.scratchR1.set(this.scratch1);
        this.scratchR2.set(this.scratch2);
        this.scratchR3.set(this.scratch3);
        this.a1.set(this.qmuv1);
        this.a2.set(this.qmuv2);
        this.a3.set(this.qmuv3);
        this.rasterTriangle(false);
        this.scratchR1.set(this.scratch3);
        this.scratchR2.set(this.scratch4);
        this.scratchR3.set(this.scratch1);
        this.a1.set(this.qmuv3);
        this.a2.set(this.qmuv4);
        this.a3.set(this.qmuv1);
        this.rasterTriangle(true);
    }

    private void rasterTriangle(boolean orZero) {
        Vector3f v1 = this.scratchR1;
        Vector3f v2 = this.scratchR2;
        Vector3f v3 = this.scratchR3;

        float area = edge(v1, v2, v3);
        if (area < 0 == this.cullBackFace) {
            return;
        }
        if (Math.abs(area) < 0.001F) {
            return;
        }

        int minX = Math.max((int) Math.floor(Math.min(Math.min(v1.x, v2.x), v3.x)), 0);
        int maxX = Math.min((int) Math.ceil(Math.max(Math.max(v1.x, v2.x), v3.x)), this.targetSize - 1);
        int minY = Math.max((int) Math.floor(Math.min(Math.min(v1.y, v2.y), v3.y)), 0);
        int maxY = Math.min((int) Math.ceil(Math.max(Math.max(v1.y, v2.y), v3.y)), this.targetSize - 1);

        float invArea = 1.0F / area;
        for (int py = minY; py <= maxY; py++) {
            for (int px = minX; px <= maxX; px++) {
                float cx = px + 0.5F;
                float cy = py + 0.5F;
                float w1 = edge(v2, v3, cx, cy) * invArea;
                float w2 = edge(v3, v1, cx, cy) * invArea;
                float w3 = 1.0F - w1 - w2;
                if ((w1 > 0.0F && w2 > 0.0F && w3 > 0.0F)
                        || (orZero && w1 >= 0.0F && w2 >= 0.0F && w3 >= 0.0F)) {
                    this.rasterPixel(px + py * this.targetSize, w1, w2, w3);
                }
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
        int pu = clamp(Math.round(u * this.samplerWidth - 0.5F), 0, this.samplerWidth - 1);
        int pv = clamp(Math.round(v * this.samplerHeight - 0.5F), 0, this.samplerHeight - 1);
        return this.samplerTexture[this.samplerWidth * pv + pu];
    }

    private void loadTransformPos(Matrix4f transform, long addr, int vert, Vector3f out, Vector3f otherAttributesOut) {
        long vertexAddr = addr + vert * ReuseVertexConsumer.VERTEX_FORMAT_SIZE;
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

    static int mix(int start, int end, int weight) {
        long hi = ((start & 0x00FF00FFL) * weight)
                + ((end & 0x00FF00FFL) * (255 - weight));
        long lo = ((start & 0xFF00FF00L) * weight)
                + ((end & 0xFF00FF00L) * (255 - weight));
        long result = (((hi + 0x00FF00FFL) >>> 8) & 0x00FF00FFL)
                | (((lo + 0xFF00FF00L) >>> 8) & 0xFF00FF00L);
        return (int) result;
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
