package me.cortex.voxy.forge;

import org.joml.Matrix4f;
import org.joml.Vector4f;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class Round8FaceOcclusionMaskTest {
    private static final int SIZE = ForgeModelAtlasLayout.MODEL_TEXTURE_SIZE;
    private static final int[] OPPOSITE_FACE = {1, 0, 3, 2, 5, 4};

    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void originalBakeryViewsGiveEveryOppositePairTheSameRasterOrientation() throws Exception {
        Field field = ForgeSoftwareModelTextureBakery.class.getDeclaredField("VIEWS");
        field.setAccessible(true);
        Matrix4f[] views = (Matrix4f[]) field.get(null);

        assertProjectedPair(views, 0, point(0.2F, 0.0F, 0.7F), 1, point(0.2F, 1.0F, 0.7F));
        assertProjectedPair(views, 2, point(0.2F, 0.7F, 0.0F), 3, point(0.2F, 0.7F, 1.0F));
        assertProjectedPair(views, 4, point(0.0F, 0.2F, 0.7F), 5, point(1.0F, 0.2F, 0.7F));
    }

    @Test
    void generatedMasksRetainTheOriginalSixFaceRasterCoordinates() {
        ColourDepthTextureData[] textures = new ColourDepthTextureData[ForgeModelAtlasLayout.FACE_COUNT];
        for (int face = 0; face < textures.length; face++) {
            textures[face] = sparseTexture(2 + face, 3, 13 - face, 11);
        }

        long[] masks = FaceOcclusionMask.fromTextures(textures, TextureUtils.WRITE_CHECK_STENCIL);
        assertEquals(FaceOcclusionMask.WORDS_PER_MODEL, masks.length);
        for (int face = 0; face < textures.length; face++) {
            assertTrue(pixel(masks, face, 2 + face, 3), "face " + face + " first marker");
            assertTrue(pixel(masks, face, 13 - face, 11), "face " + face + " second marker");
            assertFalse(pixel(masks, face, 15 - (2 + face), 3), "face " + face + " must not mirror U");
        }
    }

    @Test
    void directSubsetComparisonWorksForAllSixSectionBorderDirections() {
        for (int face = 0; face < ForgeModelAtlasLayout.FACE_COUNT; face++) {
            int opposite = OPPOSITE_FACE[face];
            long[] target = new long[FaceOcclusionMask.WORDS_PER_MODEL];
            long[] occluder = new long[FaceOcclusionMask.WORDS_PER_MODEL];
            setPixel(target, face, 2, 5);
            setPixel(target, face, 11, 13);
            setPixel(occluder, opposite, 2, 5);
            setPixel(occluder, opposite, 11, 13);
            setPixel(occluder, opposite, 7, 7);

            assertTrue(FaceOcclusionMask.covers(occluder, opposite, target, face),
                    "direct opposite-face coverage " + face);

            long[] mirrored = new long[FaceOcclusionMask.WORDS_PER_MODEL];
            setPixel(mirrored, opposite, 13, 5);
            setPixel(mirrored, opposite, 4, 13);
            assertFalse(FaceOcclusionMask.covers(mirrored, opposite, target, face),
                    "no implicit mirror at border " + face);
        }
    }

    @Test
    void emptyOrPartialOccludersCannotHideTargetCoverage() {
        long[] target = new long[FaceOcclusionMask.WORDS_PER_MODEL];
        long[] occluder = new long[FaceOcclusionMask.WORDS_PER_MODEL];
        setPixel(target, 3, 1, 1);
        setPixel(target, 3, 14, 14);
        setPixel(occluder, 2, 1, 1);

        assertFalse(FaceOcclusionMask.covers(occluder, 2, target, 3));
        assertFalse(FaceOcclusionMask.covers(
                new long[FaceOcclusionMask.WORDS_PER_MODEL],
                2,
                new long[FaceOcclusionMask.WORDS_PER_MODEL],
                3));
    }

    @Test
    void cpuMetadataMarksExactMasksButOnlyFullCoverageAsCoarseOcclusion() {
        ModelFactory.PreparedRecord full = prepared(fullTextures(), ForgeOriginalVoxyModelLayer.SOLID);
        assertTrue(ModelQueries.faceUsesOcclusionMask(full.voxyMetadata(), 0));
        assertTrue(ModelQueries.faceOccludes(full.voxyMetadata(), 0));
        assertTrue(ModelQueries.isFullyOpaque(full.voxyMetadata()));

        ColourDepthTextureData[] sparse = new ColourDepthTextureData[ForgeModelAtlasLayout.FACE_COUNT];
        Arrays.fill(sparse, sparseTexture(1, 1, 14, 14));
        ModelFactory.PreparedRecord partial = prepared(sparse, ForgeOriginalVoxyModelLayer.SOLID);
        assertTrue(ModelQueries.faceUsesOcclusionMask(partial.voxyMetadata(), 0));
        assertFalse(ModelQueries.faceOccludes(partial.voxyMetadata(), 0));
        assertFalse(ModelQueries.isFullyOpaque(partial.voxyMetadata()));

        ModelFactory.PreparedRecord translucent = prepared(sparse, ForgeOriginalVoxyModelLayer.TRANSLUCENT);
        assertFalse(ModelQueries.faceUsesOcclusionMask(translucent.voxyMetadata(), 0));
        assertFalse(ModelQueries.faceOccludes(translucent.voxyMetadata(), 0));
    }

    @Test
    void formalMesherConsumesTheCpuMaskAndDoesNotAddDeadGpuMetadata() throws Exception {
        String mesher = Files.readString(Path.of(
                "src/main/java/me/cortex/voxy/forge/RenderDataFactory.java"));
        assertTrue(mesher.contains("RenderFaceDecision.evaluate("));

        String decision = Files.readString(Path.of(
                "src/main/java/me/cortex/voxy/forge/RenderFaceDecision.java"));
        assertTrue(decision.contains("ModelQueries.faceUsesOcclusionMask(neighborMetadata, oppositeFace)"));
        assertTrue(decision.contains("coverage.isFaceCoverageOccludedBy("));

        String modelFactory = Files.readString(Path.of(
                "src/main/java/me/cortex/voxy/forge/ModelFactory.java"));
        assertTrue(modelFactory.contains("private final long[][] faceOcclusionMasks"));
        assertTrue(modelFactory.contains("this.faceOcclusionMasks[modelId] ="));

        String shaderModel = Files.readString(Path.of(
                "src/main/resources/assets/voxy/shaders/lod/block_model.glsl"));
        assertFalse(shaderModel.contains("occlusionMask"));
    }

    private static void assertProjectedPair(
            Matrix4f[] views,
            int firstFace,
            Vector4f firstPoint,
            int secondFace,
            Vector4f secondPoint
    ) {
        Vector4f first = views[firstFace].transform(firstPoint, new Vector4f());
        Vector4f second = views[secondFace].transform(secondPoint, new Vector4f());
        assertEquals(first.x, second.x, 0.00001F, "projected U " + firstFace + '/' + secondFace);
        assertEquals(first.y, second.y, 0.00001F, "projected V " + firstFace + '/' + secondFace);
    }

    private static Vector4f point(float x, float y, float z) {
        return new Vector4f(x, y, z, 1.0F);
    }

    private static ColourDepthTextureData sparseTexture(int firstU, int firstV, int secondU, int secondV) {
        int[] colours = new int[SIZE * SIZE];
        int[] depths = new int[SIZE * SIZE];
        Arrays.fill(colours, 0);
        setTexturePixel(colours, depths, firstU, firstV);
        setTexturePixel(colours, depths, secondU, secondV);
        return new ColourDepthTextureData(colours, depths, SIZE, SIZE);
    }

    private static ColourDepthTextureData[] fullTextures() {
        ColourDepthTextureData[] textures = new ColourDepthTextureData[ForgeModelAtlasLayout.FACE_COUNT];
        for (int face = 0; face < textures.length; face++) {
            int[] colours = new int[SIZE * SIZE];
            int[] depths = new int[SIZE * SIZE];
            Arrays.fill(colours, 0xFFFFFFFF);
            Arrays.fill(depths, 1);
            textures[face] = new ColourDepthTextureData(colours, depths, SIZE, SIZE);
        }
        return textures;
    }

    private static ModelFactory.PreparedRecord prepared(
            ColourDepthTextureData[] textures,
            ForgeOriginalVoxyModelLayer layer
    ) {
        return ModelFactory.prepareRecord(
                Blocks.STONE.defaultBlockState(),
                new ForgeSoftwareModelTextureBakery.BakeResult(textures, layer, 0, "none"),
                new ModelFactory.TintPlan(
                        false,
                        false,
                        new ModelFactory.TintSourcePlan(java.util.List.of()),
                        -1,
                        -1,
                        null,
                        -1),
                -1,
                0);
    }

    private static void setTexturePixel(int[] colours, int[] depths, int u, int v) {
        int index = v * SIZE + u;
        colours[index] = 0xFFFFFFFF;
        depths[index] = 1;
    }

    private static void setPixel(long[] modelMask, int face, int u, int v) {
        int pixel = v * SIZE + u;
        int offset = FaceOcclusionMask.faceOffset(face);
        modelMask[offset + pixel / Long.SIZE] |= 1L << (pixel & (Long.SIZE - 1));
    }

    private static boolean pixel(long[] modelMask, int face, int u, int v) {
        int pixel = v * SIZE + u;
        int offset = FaceOcclusionMask.faceOffset(face);
        return (modelMask[offset + pixel / Long.SIZE] & (1L << (pixel & (Long.SIZE - 1)))) != 0L;
    }
}
