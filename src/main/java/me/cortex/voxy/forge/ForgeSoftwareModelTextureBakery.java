package me.cortex.voxy.forge;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.LiquidBlockRenderer;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.FluidState;
import net.minecraftforge.client.model.data.ModelData;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11C;

import javax.annotation.Nullable;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

final class ForgeSoftwareModelTextureBakery {
    private static final int FACE_SIZE = ForgeModelAtlasLayout.MODEL_TEXTURE_SIZE;
    private static final int FACE_PIXELS = FACE_SIZE * FACE_SIZE;
    private static final int FLAG_SHADED = 1;
    private static final int FLAG_DARKENED = 2;
    private static final int FLAG_TRANSLUCENT = 4;
    private static final int FLAG_DISCARD = 8;
    private static final Matrix4f[] VIEWS = createViews();

    private final SoftwareRasterizer rasterizer = new SoftwareRasterizer(FACE_SIZE);
    private final LiquidBlockRenderer fluidRenderer = new LiquidBlockRenderer();
    private int[] atlasPixels;
    private int atlasWidth;
    private int atlasHeight;

    BakeResult renderToOutput(Minecraft minecraft, BlockState state, int blockStateId) {
        this.setupTexture(minecraft);
        var faces = new FaceTexture[ForgeModelAtlasLayout.FACE_COUNT];
        for (int i = 0; i < faces.length; i++) {
            faces[i] = FaceTexture.empty();
        }
        if (state == null || state.isAir() || state.getRenderShape() == RenderShape.INVISIBLE) {
            return new BakeResult(faces, ForgeCpuMeshLayer.OTHER, 0, "invisible-or-air");
        }
        if (state.getBlock() instanceof LiquidBlock) {
            if (this.atlasPixels == null || Minecraft.getInstance().level == null) {
                return new BakeResult(faces, ForgeCpuMeshLayer.OTHER, 0, "software-bakery-atlas-or-level-unavailable");
            }
            return this.renderFluid(state, faces);
        }
        return this.renderBlock(minecraft, state, blockStateId, faces);
    }

    private void setupTexture(Minecraft minecraft) {
        if (this.atlasPixels != null) {
            return;
        }
        if (!RenderSystem.isOnRenderThread()) {
            return;
        }
        AbstractTexture texture = minecraft.getTextureManager().getTexture(TextureAtlas.LOCATION_BLOCKS);
        if (!(texture instanceof TextureAtlas atlas) || atlas.getTextureLocations().isEmpty()) {
            return;
        }
        TextureAtlasSprite sample = atlas.getSprite(atlas.getTextureLocations().iterator().next());
        float uRange = sample.getU1() - sample.getU0();
        float vRange = sample.getV1() - sample.getV0();
        if (uRange <= 0.0F || vRange <= 0.0F) {
            return;
        }
        this.atlasWidth = Math.max(1, Math.round(sample.contents().width() / uRange));
        this.atlasHeight = Math.max(1, Math.round(sample.contents().height() / vRange));
        ByteBuffer pixels = BufferUtils.createByteBuffer(this.atlasWidth * this.atlasHeight * 4);
        int oldTexture = GL11C.glGetInteger(GL11C.GL_TEXTURE_BINDING_2D);
        try {
            GL11C.glBindTexture(GL11C.GL_TEXTURE_2D, atlas.getId());
            GL11C.glGetTexImage(GL11C.GL_TEXTURE_2D, 0, GL11C.GL_RGBA, GL11C.GL_UNSIGNED_BYTE, pixels);
            this.atlasPixels = new int[this.atlasWidth * this.atlasHeight];
            for (int i = 0; i < this.atlasPixels.length; i++) {
                int r = pixels.get(i * 4) & 0xFF;
                int g = pixels.get(i * 4 + 1) & 0xFF;
                int b = pixels.get(i * 4 + 2) & 0xFF;
                int a = pixels.get(i * 4 + 3) & 0xFF;
                this.atlasPixels[i] = (a << 24) | (b << 16) | (g << 8) | r;
            }
            this.rasterizer.setAtlas(this.atlasPixels, this.atlasWidth, this.atlasHeight);
        } finally {
            GL11C.glBindTexture(GL11C.GL_TEXTURE_2D, oldTexture);
        }
    }

    private BakeResult renderBlock(Minecraft minecraft, BlockState state, int blockStateId, FaceTexture[] faces) {
        BakedModel model = minecraft.getBlockRenderer().getBlockModel(state);
        if (model == null || model.isCustomRenderer()) {
            return new BakeResult(faces, ForgeCpuMeshLayer.OTHER, 0, "baked-model-missing-or-custom");
        }
        var opaque = new SoftwareVertexList();
        var translucent = new SoftwareVertexList(1);
        int flags = 0;
        boolean anyRenderType = false;
        boolean forceSolid = state.is(BlockTags.LEAVES);
        Iterable<RenderType> renderTypes = model.getRenderTypes(state, RandomSource.create(blockStateId), ModelData.EMPTY);
        for (RenderType renderType : renderTypes) {
            anyRenderType = true;
            boolean translucentLayer = renderType == RenderType.translucent();
            boolean discardLayer = renderType == RenderType.cutout() || renderType == RenderType.cutoutMipped();
            flags |= translucentLayer ? FLAG_TRANSLUCENT : 0;
            SoftwareVertexList target = translucentLayer ? translucent : opaque;
            int quadMeta = forceSolid ? 0 : (translucentLayer || discardLayer ? 1 : 0);
            flags |= !translucentLayer && (quadMeta & 1) != 0 ? FLAG_DISCARD : 0;
            for (Direction direction : directionsWithNull()) {
                List<BakedQuad> quads = safeGetQuads(model, state, direction, blockStateId, renderType);
                for (BakedQuad quad : quads) {
                    target.addQuad(quad, quadMeta);
                    flags |= quad.isShade() ? FLAG_SHADED : 0;
                }
            }
        }
        if (!anyRenderType) {
            for (Direction direction : directionsWithNull()) {
                List<BakedQuad> quads = safeGetQuads(model, state, direction, blockStateId, null);
                for (BakedQuad quad : quads) {
                    opaque.addQuad(quad, 0);
                    flags |= quad.isShade() ? FLAG_SHADED : 0;
                }
            }
        }
        if (opaque.isEmpty() && translucent.isEmpty()) {
            return new BakeResult(faces, ForgeCpuMeshLayer.OTHER, flags, "no-quads");
        }
        for (int face = 0; face < faces.length; face++) {
            this.rasterizer.setFaceCull(face == 1 || face == 2 || face == 4);
            this.rasterizer.clear();
            this.rasterizer.setBlending(false);
            this.rasterizer.raster(VIEWS[face], opaque);
            this.rasterizer.setBlending(true);
            this.rasterizer.raster(VIEWS[face], translucent);
            faces[face] = this.rasterizer.copyFace();
        }
        ForgeCpuMeshLayer layer = chooseLayer(state, flags, faces);
        return new BakeResult(faces, layer, flags, "none");
    }

    private static ForgeCpuMeshLayer chooseLayer(BlockState state, int flags, FaceTexture[] faces) {
        ForgeCpuMeshLayer layer = ForgeCpuMeshLayer.OTHER;
        if ((flags & FLAG_TRANSLUCENT) != 0) {
            boolean anyTranslucent = false;
            for (FaceTexture face : faces) {
                anyTranslucent |= face != null && face.hasTranslucentPixel();
                if (anyTranslucent) {
                    break;
                }
            }
            if (anyTranslucent) {
                layer = ForgeCpuMeshLayer.TRANSLUCENT;
            } else {
                boolean solid = true;
                for (FaceTexture face : faces) {
                    solid &= face == null || face.isSolidWhereDrawn();
                    if (!solid) {
                        break;
                    }
                }
                layer = solid ? ForgeCpuMeshLayer.SOLID : ForgeCpuMeshLayer.CUTOUT;
            }
        }
        if (layer == ForgeCpuMeshLayer.OTHER && (flags & FLAG_DISCARD) != 0) {
            layer = ForgeCpuMeshLayer.CUTOUT;
        }
        if (state.is(BlockTags.LEAVES)) {
            layer = ForgeCpuMeshLayer.SOLID;
        }
        return layer == ForgeCpuMeshLayer.OTHER ? ForgeCpuMeshLayer.SOLID : layer;
    }

    private BakeResult renderFluid(BlockState state, FaceTexture[] faces) {
        int flags = FLAG_TRANSLUCENT | FLAG_DISCARD | FLAG_SHADED;
        boolean anyFace = false;
        for (int face = 0; face < faces.length; face++) {
            var opaque = new SoftwareVertexList();
            var translucent = new SoftwareVertexList(1);
            this.bakeFluidFace(state, face, opaque, translucent);
            if (opaque.isEmpty() && translucent.isEmpty()) {
                continue;
            }
            this.rasterizer.setFaceCull(face == 1 || face == 2 || face == 4);
            this.rasterizer.clear();
            this.rasterizer.setBlending(false);
            this.rasterizer.raster(VIEWS[face], opaque);
            this.rasterizer.setBlending(true);
            this.rasterizer.raster(VIEWS[face], translucent);
            faces[face] = this.rasterizer.copyFace();
            anyFace = true;
        }
        if (!anyFace) {
            return new BakeResult(faces, ForgeCpuMeshLayer.OTHER, flags, "no-fluid-quads");
        }
        return new BakeResult(faces, ForgeCpuMeshLayer.TRANSLUCENT, flags, "none");
    }

    private void bakeFluidFace(BlockState state, int face, SoftwareVertexList opaque, SoftwareVertexList translucent) {
        BlockAndTintGetter getter = new BlockAndTintGetter() {
            @Override
            public float getShade(Direction direction, boolean shade) {
                return 1.0F;
            }

            @Override
            public LevelLightEngine getLightEngine() {
                return Minecraft.getInstance().level.getLightEngine();
            }

            @Override
            public int getBlockTint(BlockPos pos, ColorResolver resolver) {
                opaque.setDefaultMeta(opaque.defaultMeta() | 4);
                translucent.setDefaultMeta(translucent.defaultMeta() | 4);
                return -1;
            }

            @Nullable
            @Override
            public BlockEntity getBlockEntity(BlockPos pos) {
                return null;
            }

            @Override
            public BlockState getBlockState(BlockPos pos) {
                return shouldReturnAirForFluid(pos, face) ? Blocks.AIR.defaultBlockState() : state;
            }

            @Override
            public FluidState getFluidState(BlockPos pos) {
                return shouldReturnAirForFluid(pos, face) ? Blocks.AIR.defaultBlockState().getFluidState() : state.getFluidState();
            }

            @Override
            public int getHeight() {
                return 1;
            }

            @Override
            public int getMinBuildHeight() {
                return 0;
            }

            @Override
            public int getBrightness(LightLayer type, BlockPos pos) {
                return 0;
            }
        };
        this.fluidRenderer.tesselate(getter, BlockPos.ZERO, translucent, state, state.getFluidState());
    }

    private static boolean shouldReturnAirForFluid(BlockPos pos, int face) {
        var normal = Direction.from3DDataValue(face).getNormal();
        int dot = normal.getX() * pos.getX() + normal.getY() * pos.getY() + normal.getZ() * pos.getZ();
        return dot >= 1;
    }

    private static List<BakedQuad> safeGetQuads(BakedModel model, BlockState state, Direction direction, int blockStateId, @Nullable RenderType renderType) {
        try {
            List<BakedQuad> quads = model.getQuads(state, direction, RandomSource.create(blockStateId * 31L + (direction == null ? 17L : direction.ordinal())), ModelData.EMPTY, renderType);
            return quads == null ? List.of() : quads;
        } catch (RuntimeException e) {
            return List.of();
        }
    }

    private static Direction[] directionsWithNull() {
        return new Direction[]{Direction.DOWN, Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST, null};
    }

    private static Matrix4f[] createViews() {
        Matrix4f[] views = new Matrix4f[ForgeModelAtlasLayout.FACE_COUNT];
        views[0] = view(-90, 0, 0, 0);
        views[1] = view(90, 0, 0, 0b100);
        views[2] = view(0, 180, 0, 0b001);
        views[3] = view(0, 0, 0, 0);
        views[4] = view(0, 90, 270, 0b100);
        views[5] = view(0, 270, 270, 0);
        return views;
    }

    private static Matrix4f view(float pitch, float yaw, float rotation, int flip) {
        Matrix4f mat = new Matrix4f()
                .translate(0.5F, 0.5F, 0.5F)
                .rotate(makeQuatFromAxisExact(new Vector3f(0, 0, 1), rotation))
                .rotate(makeQuatFromAxisExact(new Vector3f(1, 0, 0), pitch))
                .rotate(makeQuatFromAxisExact(new Vector3f(0, 1, 0), yaw))
                .scale(1 - 2 * (flip & 1), 1 - (flip & 2), 1 - ((flip >> 1) & 2))
                .translate(-0.5F, -0.5F, -0.5F);
        return new Matrix4f().set(
                2, 0, 0, 0,
                0, 2, 0, 0,
                0, 0, -2, 0,
                -1, -1, 1, 1
        ).mul(mat);
    }

    private static Quaternionf makeQuatFromAxisExact(Vector3f axis, float angleDegrees) {
        float angle = (float) Math.toRadians(angleDegrees);
        float halfAngle = angle / 2.0F;
        float sinAngle = (float) Math.sin(halfAngle);
        float invLength = (float) (1.0D / Math.sqrt(axis.lengthSquared()));
        return new Quaternionf(
                axis.x * invLength * sinAngle,
                axis.y * invLength * sinAngle,
                axis.z * invLength * sinAngle,
                (float) Math.cos(halfAngle)
        );
    }

    record BakeResult(FaceTexture[] faces, ForgeCpuMeshLayer layer, int flags, String failureReason) {
        boolean anyFaceWritten() {
            for (FaceTexture face : this.faces) {
                if (face != null && face.writtenPixelCount(ForgeCpuMeshLayer.SOLID) > 0) {
                    return true;
                }
            }
            return false;
        }
    }

    record FaceTexture(int[] colour, int[] depth, int width, int height) {
        static FaceTexture empty() {
            return new FaceTexture(new int[FACE_PIXELS], emptyDepth(), FACE_SIZE, FACE_SIZE);
        }

        int writtenPixelCount(ForgeCpuMeshLayer layer) {
            int count = 0;
            for (int i = 0; i < this.colour.length; i++) {
                if (this.wasPixelWritten(i, layer)) {
                    count++;
                }
            }
            return count;
        }

        boolean wasPixelWritten(int index, ForgeCpuMeshLayer layer) {
            if (layer == ForgeCpuMeshLayer.SOLID) {
                return (this.depth[index] & 0xFF) != 0;
            }
            return ((this.colour[index] >>> 24) & 0xFF) > 1;
        }

        boolean hasTranslucentPixel() {
            for (int i = 0; i < this.colour.length; i++) {
                if ((this.depth[i] & 0xFF) != 0) {
                    int alpha = this.colour[i] >>> 24;
                    if (alpha != 0 && alpha != 255) {
                        return true;
                    }
                }
            }
            return false;
        }

        boolean isSolidWhereDrawn() {
            for (int i = 0; i < this.colour.length; i++) {
                if ((this.depth[i] & 0xFF) != 0 && (this.colour[i] >>> 24) != 255) {
                    return false;
                }
            }
            return true;
        }

        int[] bounds(ForgeCpuMeshLayer layer) {
            int minX = this.width;
            int minY = this.height;
            int maxX = -1;
            int maxY = -1;
            for (int y = 0; y < this.height; y++) {
                for (int x = 0; x < this.width; x++) {
                    int index = x + y * this.width;
                    if (!this.wasPixelWritten(index, layer)) {
                        continue;
                    }
                    minX = Math.min(minX, x);
                    minY = Math.min(minY, y);
                    maxX = Math.max(maxX, x);
                    maxY = Math.max(maxY, y);
                }
            }
            return maxX < minX || maxY < minY ? new int[]{0, -1, 0, -1} : new int[]{minX, maxX, minY, maxY};
        }

        float depth(ForgeCpuMeshLayer layer, boolean minMode) {
            long count = 0;
            long value = minMode ? Long.MAX_VALUE : 0;
            for (int i = 0; i < this.depth.length; i++) {
                if (!this.wasPixelWritten(i, layer)) {
                    continue;
                }
                int depthValue = this.depth[i] >>> 8;
                if (minMode) {
                    value = Math.min(value, depthValue);
                } else {
                    value += depthValue;
                    count++;
                }
            }
            if (minMode) {
                return value == Long.MAX_VALUE ? -1.0F : (float) ((double) value / ((1 << 24) - 1));
            }
            return count == 0 ? -1.0F : (float) ((double) (value / count) / ((1 << 24) - 1));
        }

        int tintState(ForgeCpuMeshLayer layer) {
            boolean allTinted = true;
            boolean someTinted = false;
            boolean written = false;
            for (int i = 0; i < this.colour.length; i++) {
                if (!this.wasPixelWritten(i, layer)) {
                    continue;
                }
                if ((this.colour[i] & 0xFFFFFF) == 0 || (this.colour[i] >>> 24) == 0) {
                    continue;
                }
                boolean tinted = (this.depth[i] & (1 << 7)) != 0;
                written = true;
                allTinted &= tinted;
                someTinted |= tinted;
            }
            if (!written) {
                return 0;
            }
            return someTinted ? (allTinted ? 3 : 2) : 1;
        }

        private static int[] emptyDepth() {
            int[] depth = new int[FACE_PIXELS];
            for (int i = 0; i < depth.length; i++) {
                depth[i] = ((1 << 24) - 1) << 8;
            }
            return depth;
        }
    }

    private static final class SoftwareVertexList implements VertexConsumer {
        private final List<SoftwareQuad> quads = new ArrayList<>();
        private final Vertex[] current = new Vertex[4];
        private final int globalOrMetadata;
        private int vertexIndex;
        private int defaultMeta;
        private double x;
        private double y;
        private double z;
        private int color = 0xFFFFFFFF;
        private float u;
        private float v;

        private SoftwareVertexList() {
            this(0);
        }

        private SoftwareVertexList(int globalOrMetadata) {
            this.globalOrMetadata = globalOrMetadata;
        }

        private boolean isEmpty() {
            return this.quads.isEmpty();
        }

        private void setDefaultMeta(int defaultMeta) {
            this.defaultMeta = defaultMeta;
        }

        private int defaultMeta() {
            return this.defaultMeta;
        }

        private void addQuad(BakedQuad quad, int metadata) {
            int[] vertices = quad.getVertices();
            if (vertices == null || vertices.length < 32) {
                return;
            }
            int stride = vertices.length / 4;
            if (stride < 8) {
                return;
            }
            Vertex[] out = new Vertex[4];
            for (int i = 0; i < 4; i++) {
                int offset = i * stride;
                float x = Float.intBitsToFloat(vertices[offset]);
                float y = Float.intBitsToFloat(vertices[offset + 1]);
                float z = Float.intBitsToFloat(vertices[offset + 2]);
                float u = Float.intBitsToFloat(vertices[offset + 4]);
                float v = Float.intBitsToFloat(vertices[offset + 5]);
                out[i] = new Vertex(x, y, z, u, v, metadata | (quad.isTinted() ? 4 : 0), quad.getSprite(), 0xFFFFFFFF);
            }
            this.quads.add(new SoftwareQuad(out));
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
            this.current[this.vertexIndex++] = new Vertex((float) this.x, (float) this.y, (float) this.z, this.u, this.v, meta, null, this.color);
            if (this.vertexIndex == 4) {
                this.quads.add(new SoftwareQuad(this.current.clone()));
                this.vertexIndex = 0;
            }
            this.color = 0xFFFFFFFF;
        }

        @Override
        public void defaultColor(int red, int green, int blue, int alpha) {
        }

        @Override
        public void unsetDefaultColor() {
        }
    }

    private record SoftwareQuad(Vertex[] vertices) {
    }

    private record Vertex(float x, float y, float z, float u, float v, int metadata, @Nullable TextureAtlasSprite sprite, int color) {
    }

    private static final class SoftwareRasterizer {
        private static final long DEPTH_MASK = ((1L << 24) - 1) << (64 - 24);
        private static final long CLEAR_VALUE = DEPTH_MASK;
        private final int targetSize;
        private final long[] framebuffer;
        private boolean cullBackFace;
        private boolean blending;
        private int[] atlasPixels;
        private int atlasWidth;
        private int atlasHeight;

        private final Vector4f tmp = new Vector4f();
        private final Vector3f p1 = new Vector3f();
        private final Vector3f p2 = new Vector3f();
        private final Vector3f p3 = new Vector3f();
        private final Vector3f p4 = new Vector3f();
        private final Vector3f a1 = new Vector3f();
        private final Vector3f a2 = new Vector3f();
        private final Vector3f a3 = new Vector3f();
        private final Vector3f a4 = new Vector3f();

        private SoftwareRasterizer(int targetSize) {
            this.targetSize = targetSize;
            this.framebuffer = new long[targetSize * targetSize];
        }

        private void setFaceCull(boolean cullBackFace) {
            this.cullBackFace = cullBackFace;
        }

        private void setBlending(boolean blending) {
            this.blending = blending;
        }

        private void setAtlas(int[] atlasPixels, int atlasWidth, int atlasHeight) {
            this.atlasPixels = atlasPixels;
            this.atlasWidth = atlasWidth;
            this.atlasHeight = atlasHeight;
        }

        private void clear() {
            for (int i = 0; i < this.framebuffer.length; i++) {
                this.framebuffer[i] = CLEAR_VALUE;
            }
        }

        private void raster(Matrix4f transform, SoftwareVertexList list) {
            for (SoftwareQuad quad : list.quads) {
                this.rasterQuad(transform, quad);
            }
        }

        private void rasterQuad(Matrix4f transform, SoftwareQuad quad) {
            load(transform, quad.vertices[0], this.p1, this.a1);
            load(transform, quad.vertices[1], this.p2, this.a2);
            load(transform, quad.vertices[2], this.p3, this.a3);
            load(transform, quad.vertices[3], this.p4, this.a4);
            this.rasterTriangle(quad.vertices[0].sprite, this.p1, this.p2, this.p3, this.a1, this.a2, this.a3, false);
            this.rasterTriangle(quad.vertices[2].sprite, this.p3, this.p4, this.p1, this.a3, this.a4, this.a1, true);
        }

        private void load(Matrix4f transform, Vertex vertex, Vector3f position, Vector3f attributes) {
            this.tmp.set(vertex.x, vertex.y, vertex.z, 1.0F);
            Vector4f projected = transform.transformProject(this.tmp);
            position.set(
                    (projected.x * 0.5F + 0.5F) * this.targetSize,
                    (projected.y * 0.5F + 0.5F) * this.targetSize,
                    projected.z
            );
            attributes.set(Float.intBitsToFloat(vertex.metadata), vertex.u, vertex.v);
        }

        private void rasterTriangle(@Nullable TextureAtlasSprite sprite, Vector3f v1, Vector3f v2, Vector3f v3, Vector3f uv1, Vector3f uv2, Vector3f uv3, boolean orZero) {
            float area = edge(v1, v2, v3);
            if (area < 0 == this.cullBackFace || Math.abs(area) < 0.001F) {
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
                    if ((w1 > 0.0F && w2 > 0.0F && w3 > 0.0F) || (orZero && w1 >= 0.0F && w2 >= 0.0F && w3 >= 0.0F)) {
                        this.rasterPixel(px + py * this.targetSize, sprite, v1, v2, v3, uv1, uv2, uv3, w1, w2, w3);
                    }
                }
            }
        }

        private void rasterPixel(int index, @Nullable TextureAtlasSprite sprite, Vector3f v1, Vector3f v2, Vector3f v3, Vector3f uv1, Vector3f uv2, Vector3f uv3, float w1, float w2, float w3) {
            float z = w1 * v1.z + w2 * v2.z + w3 * v3.z;
            z = z * 0.5F + 0.5F;
            if (z < 0.0F && z >= -0.000001F) {
                z = 0.0F;
            }
            if (z < 0.0F || z > 1.0F) {
                return;
            }
            int meta = Float.floatToRawIntBits(uv1.x);
            float u = w1 * uv1.y + w2 * uv2.y + w3 * uv3.y;
            float v = w1 * uv1.z + w2 * uv2.z + w3 * uv3.z;
            int colour = sprite == null ? this.sampleAtlas(u, v) : sampleSprite(sprite, u, v);
            if ((meta & 1) != 0 && (colour >>> 24) <= 0) {
                return;
            }
            this.framebuffer[index] += 1L << 32;
            long depthValue = ((long) ((double) z * ((1 << 24) - 1))) << (64 - 24);
            if (depthValue == DEPTH_MASK) {
                depthValue--;
            }
            if (Long.compareUnsigned(this.framebuffer[index], depthValue) <= 0) {
                return;
            }
            this.framebuffer[index] &= ~DEPTH_MASK;
            this.framebuffer[index] |= depthValue;
            this.framebuffer[index] &= ~(1L << 39);
            this.framebuffer[index] |= ((long) (meta & 4)) << 37;
            int dst = (int) this.framebuffer[index];
            this.framebuffer[index] &= ~Integer.toUnsignedLong(-1);
            if (this.blending) {
                colour = blend(dst, colour);
            }
            this.framebuffer[index] |= Integer.toUnsignedLong(colour);
        }

        private FaceTexture copyFace() {
            int[] colour = new int[this.framebuffer.length];
            int[] depth = new int[this.framebuffer.length];
            for (int i = 0; i < this.framebuffer.length; i++) {
                colour[i] = (int) this.framebuffer[i];
                depth[i] = (int) (this.framebuffer[i] >>> 32);
            }
            return new FaceTexture(colour, depth, this.targetSize, this.targetSize);
        }

        private static int sampleSprite(TextureAtlasSprite sprite, float u, float v) {
            float uRange = sprite.getU1() - sprite.getU0();
            float vRange = sprite.getV1() - sprite.getV0();
            int width = Math.max(1, sprite.contents().width());
            int height = Math.max(1, sprite.contents().height());
            int x = clamp(Math.round(((u - sprite.getU0()) / uRange) * width - 0.5F), 0, width - 1);
            int y = clamp(Math.round(((v - sprite.getV0()) / vRange) * height - 0.5F), 0, height - 1);
            return sprite.getPixelRGBA(0, x, y);
        }

        private int sampleAtlas(float u, float v) {
            if (this.atlasPixels == null || this.atlasWidth <= 0 || this.atlasHeight <= 0) {
                return 0;
            }
            int x = clamp(Math.round(u * this.atlasWidth - 0.5F), 0, this.atlasWidth - 1);
            int y = clamp(Math.round(v * this.atlasHeight - 0.5F), 0, this.atlasHeight - 1);
            return this.atlasPixels[x + y * this.atlasWidth];
        }

        private static int blend(int dst, int src) {
            int srcA = (src >>> 24) & 0xFF;
            if (srcA == 255) {
                return src;
            }
            int dstA = (dst >>> 24) & 0xFF;
            int outA = Math.min(255, srcA + ((dstA * (255 - srcA)) >> 8));
            int outR = blendChannel(src & 0xFF, srcA, dst & 0xFF, dstA);
            int outG = blendChannel((src >>> 8) & 0xFF, srcA, (dst >>> 8) & 0xFF, dstA);
            int outB = blendChannel((src >>> 16) & 0xFF, srcA, (dst >>> 16) & 0xFF, dstA);
            return (outA << 24) | (outB << 16) | (outG << 8) | outR;
        }

        private static int blendChannel(int src, int srcA, int dst, int dstA) {
            int value = src * srcA + ((dst * dstA * (255 - srcA)) >> 8);
            int divisor = Math.max(1, srcA + ((dstA * (255 - srcA)) >> 8));
            return clamp(value / divisor, 0, 255);
        }

        private static float edge(Vector3f a, Vector3f b, Vector3f c) {
            return (c.x - a.x) * (b.y - a.y) - (c.y - a.y) * (b.x - a.x);
        }

        private static float edge(Vector3f a, Vector3f b, float x, float y) {
            return (x - a.x) * (b.y - a.y) - (y - a.y) * (b.x - a.x);
        }
    }

    static byte[] rgbaBytes(ForgeSoftwareModelTextureBakery.FaceTexture face) {
        byte[] pixels = new byte[ForgeModelAtlasPixelSample.BYTES_PER_FACE];
        for (int i = 0; i < Math.min(face.colour.length, FACE_PIXELS); i++) {
            int pixel = face.colour[i];
            int offset = i * ForgeModelAtlasPixelSample.BYTES_PER_PIXEL;
            pixels[offset] = (byte) (pixel & 0xFF);
            pixels[offset + 1] = (byte) ((pixel >>> 8) & 0xFF);
            pixels[offset + 2] = (byte) ((pixel >>> 16) & 0xFF);
            pixels[offset + 3] = (byte) ((pixel >>> 24) & 0xFF);
        }
        return pixels;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
