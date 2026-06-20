package me.cortex.voxy.forge;

import com.mojang.blaze3d.vertex.VertexConsumer;
import me.cortex.voxy.common.util.MemoryBuffer;
import net.minecraft.client.renderer.block.model.BakedQuad;
import org.lwjgl.system.MemoryUtil;

final class ForgeOriginalVoxyReuseVertexConsumer implements VertexConsumer {
    static final int VERTEX_FORMAT_SIZE = 24;

    private MemoryBuffer buffer = new MemoryBuffer(8192);
    private long ptr;
    private int count;
    private int defaultMeta;
    private double x;
    private double y;
    private double z;
    private float u;
    private float v;
    private int color = 0xFFFFFFFF;

    boolean anyShaded;
    boolean anyDarkenedTex;
    boolean anyDiscard;

    private final int globalOrMetadata;

    ForgeOriginalVoxyReuseVertexConsumer() {
        this(0);
    }

    ForgeOriginalVoxyReuseVertexConsumer(int globalOrMetadata) {
        this.globalOrMetadata = globalOrMetadata;
        this.reset();
    }

    ForgeOriginalVoxyReuseVertexConsumer setDefaultMeta(int meta) {
        this.defaultMeta = meta;
        return this;
    }

    int getDefaultMeta() {
        return this.defaultMeta;
    }

    ForgeOriginalVoxyReuseVertexConsumer reset() {
        this.anyShaded = false;
        this.anyDarkenedTex = false;
        this.anyDiscard = false;
        this.defaultMeta = 0;
        this.count = 0;
        this.color = 0xFFFFFFFF;
        this.ptr = this.buffer.address - VERTEX_FORMAT_SIZE;
        return this;
    }

    boolean isEmpty() {
        return this.count == 0;
    }

    int quadCount() {
        if (this.count % 4 != 0) {
            throw new IllegalStateException("vertex count is not quad aligned");
        }
        return this.count / 4;
    }

    long getAddress() {
        return this.buffer.address;
    }

    void free() {
        this.ptr = 0;
        this.count = 0;
        if (this.buffer != null) {
            this.buffer.free();
            this.buffer = null;
        }
    }

    ForgeOriginalVoxyReuseVertexConsumer quad(BakedQuad quad, boolean forceSolid) {
        throw new IllegalStateException("render-layer-required-for-original-voxy-quad-material");
    }

    ForgeOriginalVoxyReuseVertexConsumer quad(BakedQuad quad, net.minecraft.client.renderer.RenderType renderType, boolean forceSolid) {
        ForgeOriginalVoxyQuadMaterialBridge.QuadMaterialData data = ForgeOriginalVoxyQuadMaterialBridge.read(quad, renderType, forceSolid);
        this.anyShaded |= data.shaded();
        this.anyDarkenedTex |= data.darkenedTexture();
        int meta = data.metadata() | this.globalOrMetadata;
        this.anyDiscard |= (meta & 1) != 0;
        this.ensureCanPut(4);
        for (int i = 0; i < 4; i++) {
            this.putVertex(
                    data.x()[i],
                    data.y()[i],
                    data.z()[i],
                    meta,
                    data.u()[i],
                    data.v()[i]
            );
        }
        return this;
    }

    @Override
    public VertexConsumer vertex(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
        return this;
    }

    @Override
    public VertexConsumer color(int red, int green, int blue, int alpha) {
        this.color = (alpha & 0xFF) << 24 | (blue & 0xFF) << 16 | (green & 0xFF) << 8 | (red & 0xFF);
        return this;
    }

    @Override
    public VertexConsumer uv(float u, float v) {
        this.u = u;
        this.v = v;
        return this;
    }

    @Override
    public VertexConsumer overlayCoords(int u, int v) {
        return this;
    }

    @Override
    public VertexConsumer uv2(int u, int v) {
        return this;
    }

    @Override
    public VertexConsumer normal(float x, float y, float z) {
        return this;
    }

    @Override
    public void endVertex() {
        int meta = this.defaultMeta | this.globalOrMetadata | (this.color != 0xFFFFFFFF ? 4 : 0);
        this.ensureCanPut(1);
        this.putVertex((float) this.x, (float) this.y, (float) this.z, meta, this.u, this.v);
        this.color = 0xFFFFFFFF;
    }

    @Override
    public void defaultColor(int red, int green, int blue, int alpha) {
    }

    @Override
    public void unsetDefaultColor() {
    }

    private void putVertex(float x, float y, float z, int metadata, float u, float v) {
        this.ptr += VERTEX_FORMAT_SIZE;
        this.count++;
        this.anyDiscard |= (metadata & 1) != 0;
        MemoryUtil.memPutFloat(this.ptr, x);
        MemoryUtil.memPutFloat(this.ptr + 4, y);
        MemoryUtil.memPutFloat(this.ptr + 8, z);
        MemoryUtil.memPutInt(this.ptr + 12, metadata);
        MemoryUtil.memPutFloat(this.ptr + 16, u);
        MemoryUtil.memPutFloat(this.ptr + 20, v);
    }

    private void ensureCanPut(int vertices) {
        if ((long) (this.count + vertices + 1) * VERTEX_FORMAT_SIZE < this.buffer.size) {
            return;
        }
        long offset = this.ptr - this.buffer.address;
        long newSize = (((long) this.buffer.size * 2L + VERTEX_FORMAT_SIZE - 1L) / VERTEX_FORMAT_SIZE) * VERTEX_FORMAT_SIZE;
        while ((long) (this.count + vertices + 1) * VERTEX_FORMAT_SIZE >= newSize) {
            newSize *= 2L;
        }
        MemoryBuffer newBuffer = new MemoryBuffer(newSize);
        this.buffer.cpyTo(newBuffer.address);
        this.buffer.free();
        this.buffer = newBuffer;
        this.ptr = offset + newBuffer.address;
    }
}
