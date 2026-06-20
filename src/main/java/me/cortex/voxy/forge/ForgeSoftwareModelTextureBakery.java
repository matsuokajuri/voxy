package me.cortex.voxy.forge;

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
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11C;

import javax.annotation.Nullable;
import java.nio.ByteBuffer;
import java.util.List;

final class ForgeSoftwareModelTextureBakery {
    private static final int FACE_SIZE = ForgeModelAtlasLayout.MODEL_TEXTURE_SIZE;
    private static final int FACE_PIXELS = FACE_SIZE * FACE_SIZE;
    private static final int FLAG_SHADED = 1;
    private static final int FLAG_DARKENED = 2;
    private static final int FLAG_TRANSLUCENT = 4;
    private static final int FLAG_DISCARD = 8;
    private static final Matrix4f[] VIEWS = createViews();

    private final ForgeOriginalVoxyReuseVertexConsumer opaqueVC = new ForgeOriginalVoxyReuseVertexConsumer();
    private final ForgeOriginalVoxyReuseVertexConsumer translucentVC = new ForgeOriginalVoxyReuseVertexConsumer(1);
    private final ForgeOriginalVoxySoftwareRasterizer rasterizer = new ForgeOriginalVoxySoftwareRasterizer(FACE_SIZE);
    private final LiquidBlockRenderer fluidRenderer = new LiquidBlockRenderer();
    private int[] atlasPixels;
    private int atlasWidth;
    private int atlasHeight;
    private boolean freed;

    void prepareOnRenderThread(Minecraft minecraft) {
        this.setupTexture(minecraft);
    }

    BakeResult renderToOutput(Minecraft minecraft, BlockState state, int blockStateId) {
        this.setupTexture(minecraft);
        var faces = new ForgeOriginalVoxyColourDepthTextureData[ForgeModelAtlasLayout.FACE_COUNT];
        for (int i = 0; i < faces.length; i++) {
            faces[i] = emptyTexture();
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
            this.rasterizer.setSamplerTexture(this.atlasPixels, this.atlasWidth, this.atlasHeight);
        } finally {
            GL11C.glBindTexture(GL11C.GL_TEXTURE_2D, oldTexture);
        }
    }

    private BakeResult renderBlock(Minecraft minecraft, BlockState state, int blockStateId, ForgeOriginalVoxyColourDepthTextureData[] faces) {
        BakedModel model = minecraft.getBlockRenderer().getBlockModel(state);
        if (model == null || model.isCustomRenderer()) {
            return new BakeResult(faces, ForgeCpuMeshLayer.OTHER, 0, "baked-model-missing-or-custom");
        }
        this.opaqueVC.reset();
        this.translucentVC.reset();
        int flags = 0;
        boolean anyRenderType = false;
        boolean forceSolid = state.is(BlockTags.LEAVES);
        Iterable<RenderType> renderTypes = model.getRenderTypes(state, RandomSource.create(blockStateId), ModelData.EMPTY);
        for (RenderType renderType : renderTypes) {
            anyRenderType = true;
            boolean translucentLayer = renderType == RenderType.translucent();
            ForgeOriginalVoxyReuseVertexConsumer target = translucentLayer ? this.translucentVC : this.opaqueVC;
            for (Direction direction : directionsWithNull()) {
                List<BakedQuad> quads = getQuads(model, state, direction, blockStateId, renderType);
                for (BakedQuad quad : quads) {
                    target.quad(quad, renderType, forceSolid);
                }
            }
        }
        if (!anyRenderType) {
            for (Direction direction : directionsWithNull()) {
                List<BakedQuad> quads = getQuads(model, state, direction, blockStateId, null);
                for (BakedQuad quad : quads) {
                    this.opaqueVC.quad(quad, forceSolid);
                }
            }
        }
        flags |= (this.opaqueVC.anyShaded || this.translucentVC.anyShaded) ? FLAG_SHADED : 0;
        flags |= (this.opaqueVC.anyDarkenedTex || this.translucentVC.anyDarkenedTex) ? FLAG_DARKENED : 0;
        flags |= !this.translucentVC.isEmpty() ? FLAG_TRANSLUCENT : 0;
        flags |= this.opaqueVC.anyDiscard ? FLAG_DISCARD : 0;
        if (this.opaqueVC.isEmpty() && this.translucentVC.isEmpty()) {
            return new BakeResult(faces, ForgeCpuMeshLayer.OTHER, flags, "no-quads");
        }
        for (int face = 0; face < faces.length; face++) {
            this.rasterizer.setFaceCull(face == 1 || face == 2 || face == 4);
            this.rasterizer.clear();
            this.rasterizer.setBlending(false);
            this.rasterizer.raster(VIEWS[face], this.opaqueVC);
            this.rasterizer.setBlending(true);
            this.rasterizer.raster(VIEWS[face], this.translucentVC);
            faces[face] = this.rasterizer.copyFace();
        }
        ForgeCpuMeshLayer layer = chooseLayer(state, flags, faces);
        return new BakeResult(faces, layer, flags, "none");
    }

    private static ForgeCpuMeshLayer chooseLayer(BlockState state, int flags, ForgeOriginalVoxyColourDepthTextureData[] faces) {
        ForgeCpuMeshLayer layer = ForgeCpuMeshLayer.OTHER;
        if ((flags & FLAG_TRANSLUCENT) != 0) {
            boolean anyTranslucent = false;
            for (ForgeOriginalVoxyColourDepthTextureData face : faces) {
                anyTranslucent |= face != null && ForgeOriginalVoxyTextureUtils.hasTranslucentPixel(face);
                if (anyTranslucent) {
                    break;
                }
            }
            if (anyTranslucent) {
                layer = ForgeCpuMeshLayer.TRANSLUCENT;
            } else {
                boolean solid = true;
                for (ForgeOriginalVoxyColourDepthTextureData face : faces) {
                    solid &= face == null || ForgeOriginalVoxyTextureUtils.isSolidWhereDrawn(face);
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

    private BakeResult renderFluid(BlockState state, ForgeOriginalVoxyColourDepthTextureData[] faces) {
        int flags = FLAG_TRANSLUCENT | FLAG_DISCARD | FLAG_SHADED;
        boolean anyFace = false;
        for (int face = 0; face < faces.length; face++) {
            this.opaqueVC.reset();
            this.translucentVC.reset();
            this.bakeFluidFace(state, face);
            flags |= (this.opaqueVC.anyShaded || this.translucentVC.anyShaded) ? FLAG_SHADED : 0;
            flags |= (this.opaqueVC.anyDarkenedTex || this.translucentVC.anyDarkenedTex) ? FLAG_DARKENED : 0;
            flags |= this.opaqueVC.anyDiscard ? FLAG_DISCARD : 0;
            if (this.opaqueVC.isEmpty() && this.translucentVC.isEmpty()) {
                continue;
            }
            this.rasterizer.setFaceCull(face == 1 || face == 2 || face == 4);
            this.rasterizer.clear();
            this.rasterizer.setBlending(false);
            this.rasterizer.raster(VIEWS[face], this.opaqueVC);
            this.rasterizer.setBlending(true);
            this.rasterizer.raster(VIEWS[face], this.translucentVC);
            faces[face] = this.rasterizer.copyFace();
            anyFace = true;
        }
        if (!anyFace) {
            return new BakeResult(faces, ForgeCpuMeshLayer.OTHER, flags, "no-fluid-quads");
        }
        return new BakeResult(faces, ForgeCpuMeshLayer.TRANSLUCENT, flags, "none");
    }

    private void bakeFluidFace(BlockState state, int face) {
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
                opaqueVC.setDefaultMeta(opaqueVC.getDefaultMeta() | 4);
                translucentVC.setDefaultMeta(translucentVC.getDefaultMeta() | 4);
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
        this.fluidRenderer.tesselate(getter, BlockPos.ZERO, this.translucentVC, state, state.getFluidState());
    }

    private static boolean shouldReturnAirForFluid(BlockPos pos, int face) {
        var normal = Direction.from3DDataValue(face).getNormal();
        int dot = normal.getX() * pos.getX() + normal.getY() * pos.getY() + normal.getZ() * pos.getZ();
        return dot >= 1;
    }

    void free() {
        if (this.freed) {
            return;
        }
        this.opaqueVC.free();
        this.translucentVC.free();
        this.freed = true;
    }

    private static List<BakedQuad> getQuads(BakedModel model, BlockState state, Direction direction, int blockStateId, @Nullable RenderType renderType) {
        List<BakedQuad> quads = model.getQuads(state, direction, RandomSource.create(blockStateId * 31L + (direction == null ? 17L : direction.ordinal())), ModelData.EMPTY, renderType);
        return quads == null ? List.of() : quads;
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

    record BakeResult(ForgeOriginalVoxyColourDepthTextureData[] textures, ForgeCpuMeshLayer layer, int flags, String failureReason) {
        boolean anyFaceWritten() {
            for (ForgeOriginalVoxyColourDepthTextureData face : this.textures) {
                if (face != null && ForgeOriginalVoxyTextureUtils.getWrittenPixelCount(face, ForgeOriginalVoxyTextureUtils.WRITE_CHECK_STENCIL) > 0) {
                    return true;
                }
            }
            return false;
        }

        @Deprecated
        FaceTexture[] faces() {
            FaceTexture[] legacy = new FaceTexture[this.textures.length];
            for (int i = 0; i < this.textures.length; i++) {
                legacy[i] = FaceTexture.from(this.textures[i]);
            }
            return legacy;
        }
    }

    @Deprecated
    record FaceTexture(int[] colour, int[] depth, int width, int height) {
        static FaceTexture from(ForgeOriginalVoxyColourDepthTextureData data) {
            if (data == null) {
                return new FaceTexture(new int[FACE_PIXELS], emptyDepth(), FACE_SIZE, FACE_SIZE);
            }
            return new FaceTexture(data.colour(), data.depth(), data.width(), data.height());
        }

        int writtenPixelCount(ForgeCpuMeshLayer layer) {
            return ForgeOriginalVoxyTextureUtils.getWrittenPixelCount(this.asOriginalData(), layer == ForgeCpuMeshLayer.SOLID
                    ? ForgeOriginalVoxyTextureUtils.WRITE_CHECK_STENCIL
                    : ForgeOriginalVoxyTextureUtils.WRITE_CHECK_ALPHA);
        }

        boolean wasPixelWritten(int index, ForgeCpuMeshLayer layer) {
            if (layer == ForgeCpuMeshLayer.SOLID) {
                return (this.depth[index] & 0xFF) != 0;
            }
            return ((this.colour[index] >>> 24) & 0xFF) > 1;
        }

        boolean hasTranslucentPixel() {
            return ForgeOriginalVoxyTextureUtils.hasTranslucentPixel(this.asOriginalData());
        }

        boolean isSolidWhereDrawn() {
            return ForgeOriginalVoxyTextureUtils.isSolidWhereDrawn(this.asOriginalData());
        }

        int[] bounds(ForgeCpuMeshLayer layer) {
            return ForgeOriginalVoxyTextureUtils.computeBounds(this.asOriginalData(), layer == ForgeCpuMeshLayer.SOLID
                    ? ForgeOriginalVoxyTextureUtils.WRITE_CHECK_STENCIL
                    : ForgeOriginalVoxyTextureUtils.WRITE_CHECK_ALPHA);
        }

        float depth(ForgeCpuMeshLayer layer, boolean minMode) {
            return ForgeOriginalVoxyTextureUtils.computeDepth(
                    this.asOriginalData(),
                    minMode ? ForgeOriginalVoxyTextureUtils.DEPTH_MODE_MIN : ForgeOriginalVoxyTextureUtils.DEPTH_MODE_AVG,
                    layer == ForgeCpuMeshLayer.SOLID
                            ? ForgeOriginalVoxyTextureUtils.WRITE_CHECK_STENCIL
                            : ForgeOriginalVoxyTextureUtils.WRITE_CHECK_ALPHA);
        }

        int tintState(ForgeCpuMeshLayer layer) {
            return ForgeOriginalVoxyTextureUtils.computeFaceTint(this.asOriginalData(), layer == ForgeCpuMeshLayer.SOLID
                    ? ForgeOriginalVoxyTextureUtils.WRITE_CHECK_STENCIL
                    : ForgeOriginalVoxyTextureUtils.WRITE_CHECK_ALPHA);
        }

        private ForgeOriginalVoxyColourDepthTextureData asOriginalData() {
            return new ForgeOriginalVoxyColourDepthTextureData(this.colour, this.depth, this.width, this.height);
        }
    }

    private static ForgeOriginalVoxyColourDepthTextureData emptyTexture() {
        return new ForgeOriginalVoxyColourDepthTextureData(new int[FACE_PIXELS], emptyDepth(), FACE_SIZE, FACE_SIZE);
    }

    private static int[] emptyDepth() {
        int[] depth = new int[FACE_PIXELS];
        for (int i = 0; i < depth.length; i++) {
            depth[i] = ((1 << 24) - 1) << 8;
        }
        return depth;
    }

    static byte[] rgbaBytes(ForgeOriginalVoxyColourDepthTextureData face) {
        byte[] pixels = new byte[ForgeModelAtlasPixelSample.BYTES_PER_FACE];
        for (int i = 0; i < Math.min(face.colour().length, FACE_PIXELS); i++) {
            int pixel = face.colour()[i];
            int offset = i * ForgeModelAtlasPixelSample.BYTES_PER_PIXEL;
            pixels[offset] = (byte) (pixel & 0xFF);
            pixels[offset + 1] = (byte) ((pixel >>> 8) & 0xFF);
            pixels[offset + 2] = (byte) ((pixel >>> 16) & 0xFF);
            pixels[offset + 3] = (byte) ((pixel >>> 24) & 0xFF);
        }
        return pixels;
    }

    @Deprecated
    static byte[] rgbaBytes(FaceTexture face) {
        return rgbaBytes(new ForgeOriginalVoxyColourDepthTextureData(face.colour(), face.depth(), face.width(), face.height()));
    }
}
