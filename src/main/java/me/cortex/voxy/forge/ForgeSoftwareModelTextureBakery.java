package me.cortex.voxy.forge;

import com.mojang.blaze3d.systems.RenderSystem;
import me.cortex.voxy.common.util.MemoryBuffer;
import me.cortex.voxy.common.util.UnsafeUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
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
import net.minecraftforge.client.ForgeHooksClient;
import net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions;
import net.minecraftforge.client.model.data.ModelData;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.ARBDirectStateAccess;
import org.lwjgl.opengl.GL12C;
import org.lwjgl.opengl.GL15C;
import org.lwjgl.opengl.GL21C;
import org.lwjgl.opengl.GL30C;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.system.MemoryUtil;

import javax.annotation.Nullable;
import java.nio.ByteBuffer;
import java.util.List;

final class ForgeSoftwareModelTextureBakery {
    private static final int FACE_SIZE = ForgeModelAtlasLayout.MODEL_TEXTURE_SIZE;
    private static final int FACE_PIXELS = FACE_SIZE * FACE_SIZE;
    static final long SINGLE_FACE_OUTPUT_SIZE = FACE_PIXELS * Long.BYTES;
    static final long OUTPUT_BUFFER_BYTES = SINGLE_FACE_OUTPUT_SIZE * ForgeModelAtlasLayout.FACE_COUNT;
    private static final int FLAG_SHADED = 1;
    private static final int FLAG_DARKENED = 2;
    private static final int FLAG_TRANSLUCENT = 4;
    private static final int FLAG_DISCARD = 8;
    private static final Matrix4f[] VIEWS = createViews();

    private final ReuseVertexConsumer opaqueVC = new ReuseVertexConsumer();
    private final ReuseVertexConsumer translucentVC = new ReuseVertexConsumer(1);
    private final SoftwareRasterizer rasterizer = new SoftwareRasterizer(FACE_SIZE);
    private final PreparedLiquidBlockRenderer fluidRenderer = new PreparedLiquidBlockRenderer();
    private int[] atlasPixels;
    private int atlasWidth;
    private int atlasHeight;
    private boolean freed;
    private String lastFailureReason = "none";

    void prepareOnRenderThread(Minecraft minecraft) {
        this.fluidRenderer.setupSpritesForBake();
        this.setupTexture(minecraft);
    }

    BakeResult renderToOutput(Minecraft minecraft, BlockState state, int blockStateId) {
        MemoryBuffer output = new MemoryBuffer(OUTPUT_BUFFER_BYTES);
        try {
            int flags = this.renderToOutput(minecraft, state, output.address);
            ColourDepthTextureData[] faces = texturesFromOutput(output.address);
            return new BakeResult(faces, chooseLayer(state, flags, faces), flags, this.lastFailureReason);
        } finally {
            output.free();
        }
    }

    int renderToOutput(Minecraft minecraft, BlockState state, long outputBuffer) {
        this.lastFailureReason = "none";
        MemoryUtil.memSet(outputBuffer, 0, OUTPUT_BUFFER_BYTES);
        this.setupTexture(minecraft);
        if (state == null) {
            this.lastFailureReason = "null-block-state";
            return 0;
        }
        if (state.isAir()) {
            return 0;
        }
        if (this.atlasPixels == null) {
            this.lastFailureReason = "software-bakery-atlas-unavailable";
            return 0;
        }
        if (state.getBlock() instanceof LiquidBlock) {
            if (Minecraft.getInstance().level == null) {
                this.lastFailureReason = "software-bakery-level-unavailable";
                return 0;
            }
            return this.renderFluid(state, outputBuffer);
        }
        if (state.getRenderShape() == RenderShape.INVISIBLE) {
            return 0;
        }
        return this.renderBlock(minecraft, state, outputBuffer);
    }

    String lastFailureReason() {
        return this.lastFailureReason;
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
        int previousReadFramebuffer = GL11C.glGetInteger(GL30C.GL_READ_FRAMEBUFFER_BINDING);
        int previousDrawFramebuffer = GL11C.glGetInteger(GL30C.GL_DRAW_FRAMEBUFFER_BINDING);
        int previousPixelPackBuffer = GL11C.glGetInteger(GL21C.GL_PIXEL_PACK_BUFFER_BINDING);
        int previousPackRowLength = GL11C.glGetInteger(GL11C.GL_PACK_ROW_LENGTH);
        int previousPackImageHeight = GL11C.glGetInteger(GL12C.GL_PACK_IMAGE_HEIGHT);
        int previousPackSkipRows = GL11C.glGetInteger(GL11C.GL_PACK_SKIP_ROWS);
        int previousPackSkipPixels = GL11C.glGetInteger(GL11C.GL_PACK_SKIP_PIXELS);
        int previousPackAlignment = GL11C.glGetInteger(GL11C.GL_PACK_ALIGNMENT);
        try {
            GL11C.glFlush();
            GL11C.glFinish();
            GL30C.glBindFramebuffer(GL30C.GL_FRAMEBUFFER, 0);
            GL15C.glBindBuffer(GL21C.GL_PIXEL_PACK_BUFFER, 0);
            GL11C.glPixelStorei(GL11C.GL_PACK_ROW_LENGTH, this.atlasWidth);
            GL11C.glPixelStorei(GL12C.GL_PACK_IMAGE_HEIGHT, 0);
            GL11C.glPixelStorei(GL11C.GL_PACK_SKIP_ROWS, 0);
            GL11C.glPixelStorei(GL11C.GL_PACK_SKIP_PIXELS, 0);
            GL11C.glPixelStorei(GL11C.GL_PACK_ALIGNMENT, 4);
            ARBDirectStateAccess.glGetTextureImage(atlas.getId(), 0, GL11C.GL_RGBA, GL11C.GL_UNSIGNED_BYTE, pixels);
        } finally {
            GL15C.glBindBuffer(GL21C.GL_PIXEL_PACK_BUFFER, previousPixelPackBuffer);
            GL11C.glPixelStorei(GL11C.GL_PACK_ROW_LENGTH, previousPackRowLength);
            GL11C.glPixelStorei(GL12C.GL_PACK_IMAGE_HEIGHT, previousPackImageHeight);
            GL11C.glPixelStorei(GL11C.GL_PACK_SKIP_ROWS, previousPackSkipRows);
            GL11C.glPixelStorei(GL11C.GL_PACK_SKIP_PIXELS, previousPackSkipPixels);
            GL11C.glPixelStorei(GL11C.GL_PACK_ALIGNMENT, previousPackAlignment);
            GL30C.glBindFramebuffer(GL30C.GL_READ_FRAMEBUFFER, previousReadFramebuffer);
            GL30C.glBindFramebuffer(GL30C.GL_DRAW_FRAMEBUFFER, previousDrawFramebuffer);
        }
        this.atlasPixels = new int[this.atlasWidth * this.atlasHeight];
        for (int i = 0; i < this.atlasPixels.length; i++) {
            int r = pixels.get(i * 4) & 0xFF;
            int g = pixels.get(i * 4 + 1) & 0xFF;
            int b = pixels.get(i * 4 + 2) & 0xFF;
            int a = pixels.get(i * 4 + 3) & 0xFF;
            this.atlasPixels[i] = (a << 24) | (b << 16) | (g << 8) | r;
        }
        this.rasterizer.setSamplerTexture(this.atlasPixels, this.atlasWidth, this.atlasHeight);
    }

    private int renderBlock(Minecraft minecraft, BlockState state, long outputBuffer) {
        BakedModel model = minecraft.getBlockRenderer().getBlockModel(state);
        if (model == null || model.isCustomRenderer()) {
            this.lastFailureReason = "baked-model-missing-or-custom";
            return 0;
        }
        this.opaqueVC.reset();
        this.translucentVC.reset();
        int flags = 0;
        boolean anyRenderType = false;
        boolean forceSolid = state.is(BlockTags.LEAVES);
        Iterable<RenderType> renderTypes = model.getRenderTypes(state, RandomSource.create(42L), ModelData.EMPTY);
        try {
            for (RenderType renderType : renderTypes) {
                anyRenderType = true;
                for (Direction direction : directionsWithNull()) {
                    List<BakedQuad> quads = getQuads(model, state, direction, renderType);
                    for (BakedQuad quad : quads) {
                        ReuseVertexConsumer target = ForgeOriginalVoxyQuadMaterialBridge.isTranslucentLayer(
                                quad,
                                renderType)
                                ? this.translucentVC
                                : this.opaqueVC;
                        target.quad(quad, renderType, forceSolid);
                    }
                }
            }
        } catch (IllegalArgumentException | IllegalStateException e) {
            this.lastFailureReason = e.getMessage() == null ? "software-bakery-material-bridge-failed" : e.getMessage();
            return 0;
        }
        if (!anyRenderType) {
            return 0;
        }
        flags |= (this.opaqueVC.anyShaded || this.translucentVC.anyShaded) ? FLAG_SHADED : 0;
        flags |= (this.opaqueVC.anyDarkenedTex || this.translucentVC.anyDarkenedTex) ? FLAG_DARKENED : 0;
        flags |= !this.translucentVC.isEmpty() ? FLAG_TRANSLUCENT : 0;
        flags |= this.opaqueVC.anyDiscard ? FLAG_DISCARD : 0;
        if (this.opaqueVC.isEmpty() && this.translucentVC.isEmpty()) {
            return flags;
        }
        for (int face = 0; face < ForgeModelAtlasLayout.FACE_COUNT; face++) {
            this.rasterizer.setFaceCull(face == 1 || face == 2 || face == 4);
            this.rasterizer.clear();
            this.rasterizer.setBlending(false);
            this.rasterizer.raster(VIEWS[face], this.opaqueVC);
            this.rasterizer.setBlending(true);
            this.rasterizer.raster(VIEWS[face], this.translucentVC);
            this.copyFaceToOutput(face, outputBuffer);
        }
        return flags;
    }

    private static ForgeOriginalVoxyModelLayer chooseLayer(BlockState state, int flags, ColourDepthTextureData[] faces) {
        ForgeOriginalVoxyModelLayer layer = ForgeOriginalVoxyModelLayer.OTHER;
        if ((flags & FLAG_TRANSLUCENT) != 0) {
            boolean anyTranslucent = false;
            for (ColourDepthTextureData face : faces) {
                anyTranslucent |= face != null && TextureUtils.hasTranslucentPixel(face);
                if (anyTranslucent) {
                    break;
                }
            }
            if (anyTranslucent) {
                layer = ForgeOriginalVoxyModelLayer.TRANSLUCENT;
            } else {
                boolean solid = true;
                for (ColourDepthTextureData face : faces) {
                    solid &= face == null || TextureUtils.isSolidWhereDrawn(face);
                    if (!solid) {
                        break;
                    }
                }
                layer = solid ? ForgeOriginalVoxyModelLayer.SOLID : ForgeOriginalVoxyModelLayer.CUTOUT;
            }
        }
        if (layer == ForgeOriginalVoxyModelLayer.OTHER && (flags & FLAG_DISCARD) != 0) {
            layer = ForgeOriginalVoxyModelLayer.CUTOUT;
        }
        if (state.is(BlockTags.LEAVES)) {
            layer = ForgeOriginalVoxyModelLayer.SOLID;
        }
        return layer == ForgeOriginalVoxyModelLayer.OTHER ? ForgeOriginalVoxyModelLayer.SOLID : layer;
    }

    private int renderFluid(BlockState state, long outputBuffer) {
        int flags = 0;
        boolean anyFace = false;
        for (int face = 0; face < ForgeModelAtlasLayout.FACE_COUNT; face++) {
            this.opaqueVC.reset();
            this.translucentVC.reset();
            try {
                this.bakeFluidFace(state, face);
            } catch (IllegalArgumentException | IllegalStateException e) {
                this.lastFailureReason = e.getMessage() == null ? "software-bakery-fluid-material-bridge-failed" : e.getMessage();
                return 0;
            }
            flags |= (this.opaqueVC.anyShaded || this.translucentVC.anyShaded) ? FLAG_SHADED : 0;
            flags |= (this.opaqueVC.anyDarkenedTex || this.translucentVC.anyDarkenedTex) ? FLAG_DARKENED : 0;
            flags |= !this.translucentVC.isEmpty() ? FLAG_TRANSLUCENT : 0;
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
            this.copyFaceToOutput(face, outputBuffer);
            anyFace = true;
        }
        if (!anyFace) {
            return flags;
        }
        return flags;
    }

    private void copyFaceToOutput(int face, long outputBuffer) {
        UnsafeUtil.memcpy(this.rasterizer.getRawFramebuffer(), outputBuffer + SINGLE_FACE_OUTPUT_SIZE * face);
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
        this.fluidRenderer.tesselate(
                getter,
                BlockPos.ZERO,
                this.selectFluidConsumer(state.getFluidState(), getter),
                state,
                state.getFluidState());
        this.translucentVC.setDefaultMeta(0);
        this.opaqueVC.setDefaultMeta(0);
    }

    private ReuseVertexConsumer selectFluidConsumer(FluidState fluidState, BlockAndTintGetter getter) {
        RenderType renderType = ItemBlockRenderTypes.getRenderLayer(fluidState);
        TextureAtlasSprite[] sprites = ForgeHooksClient.getFluidSprites(getter, BlockPos.ZERO, fluidState);
        int tintColor = IClientFluidTypeExtensions.of(fluidState)
                .getTintColor(fluidState, getter, BlockPos.ZERO);
        ForgeOriginalVoxyQuadMaterialBridge.MaterialLayer layer =
                ForgeOriginalVoxyQuadMaterialBridge.materialLayerForFluid(renderType, sprites, tintColor);
        if (layer == ForgeOriginalVoxyQuadMaterialBridge.MaterialLayer.TRANSLUCENT) {
            return this.translucentVC;
        }
        if (layer == ForgeOriginalVoxyQuadMaterialBridge.MaterialLayer.CUTOUT) {
            this.opaqueVC.setDefaultMeta(this.opaqueVC.getDefaultMeta() | 1);
        } else {
            this.opaqueVC.setDefaultMeta(this.opaqueVC.getDefaultMeta() & ~1);
        }
        return this.opaqueVC;
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

    private static List<BakedQuad> getQuads(BakedModel model, BlockState state, Direction direction, RenderType renderType) {
        List<BakedQuad> quads = model.getQuads(state, direction, RandomSource.create(42L), ModelData.EMPTY, renderType);
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

    private static final class PreparedLiquidBlockRenderer extends LiquidBlockRenderer {
        private void setupSpritesForBake() {
            super.setupSprites();
        }
    }

    record BakeResult(ColourDepthTextureData[] textures, ForgeOriginalVoxyModelLayer layer, int flags, String failureReason) {
        boolean anyFaceWritten() {
            for (ColourDepthTextureData face : this.textures) {
                if (face != null && TextureUtils.getWrittenPixelCount(face, TextureUtils.WRITE_CHECK_STENCIL) > 0) {
                    return true;
                }
            }
            return false;
        }
    }

    static byte[] rgbaBytes(ColourDepthTextureData face) {
        byte[] pixels = new byte[ForgeModelAtlasPixelFormat.BYTES_PER_FACE];
        for (int i = 0; i < Math.min(face.colour().length, FACE_PIXELS); i++) {
            int pixel = face.colour()[i];
            int offset = i * ForgeModelAtlasPixelFormat.BYTES_PER_PIXEL;
            pixels[offset] = (byte) (pixel & 0xFF);
            pixels[offset + 1] = (byte) ((pixel >>> 8) & 0xFF);
            pixels[offset + 2] = (byte) ((pixel >>> 16) & 0xFF);
            pixels[offset + 3] = (byte) ((pixel >>> 24) & 0xFF);
        }
        return pixels;
    }

    static ColourDepthTextureData[] texturesFromOutput(long outputBuffer) {
        ColourDepthTextureData[] textures = new ColourDepthTextureData[ForgeModelAtlasLayout.FACE_COUNT];
        for (int face = 0; face < textures.length; face++) {
            long facePtr = outputBuffer + SINGLE_FACE_OUTPUT_SIZE * face;
            int[] colour = new int[FACE_PIXELS];
            int[] depth = new int[FACE_PIXELS];
            for (int i = 0; i < FACE_PIXELS; i++) {
                long value = MemoryUtil.memGetLong(facePtr + (long) i * Long.BYTES);
                colour[i] = (int) value;
                depth[i] = (int) (value >>> 32);
            }
            textures[face] = new ColourDepthTextureData(colour, depth, FACE_SIZE, FACE_SIZE);
        }
        return textures;
    }
}
