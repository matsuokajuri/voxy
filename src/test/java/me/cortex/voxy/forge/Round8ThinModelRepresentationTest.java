package me.cortex.voxy.forge;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class Round8ThinModelRepresentationTest {
    private static final int SIZE = ForgeModelAtlasLayout.MODEL_TEXTURE_SIZE;

    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void representativeThinModelsKeepTheirFaceCountAndUseTheOriginalDoubleSidedBucket() {
        assertThinFixture("vine", Blocks.VINE.defaultBlockState(), new int[]{3}, 0b001000, 0b101);
        assertThinFixture("glow-lichen-corner", Blocks.GLOW_LICHEN.defaultBlockState(),
                new int[]{2, 5}, 0b100100, 0b001);
        assertThinFixture("cross-plant", Blocks.TALL_GRASS.defaultBlockState(),
                new int[]{2, 3, 4, 5}, 0b111100, 0b001);
    }

    @Test
    void barsAndPanesWithAllDirectionalProjectionsStayDirectional() {
        assertDirectionalFixture("iron-bars", Blocks.IRON_BARS.defaultBlockState());
        assertDirectionalFixture("glass-pane", Blocks.GLASS_PANE.defaultBlockState());
    }

    @Test
    void emptyModelsAreNotMisclassifiedAsDoubleSidedGeometry() {
        ModelFactory.PreparedRecord empty = prepare(
                Blocks.AIR.defaultBlockState(),
                faces(),
                ForgeOriginalVoxyModelLayer.CUTOUT);
        ModelFactory.DoubleSidedClassification classification =
                ModelFactory.classifyDoubleSidedFaces(empty.faces());

        assertFalse(classification.required());
        assertEquals(0, classification.presentFaceMask());
        assertEquals(0b111, classification.missingOppositeAxisMask());
        assertFalse(ModelQueries.isDoubleSided(empty.voxyMetadata()));
        assertEquals(0, empty.writtenFaceCount());
    }

    @Test
    void translucentPriorityAndCommandBufferLayoutRemainTheOriginalContract() throws Exception {
        long directional = 0L;
        long doubleSided = 4L << (ForgeModelAtlasLayout.FACE_COUNT * 8);
        long translucentDoubleSided = 6L << (ForgeModelAtlasLayout.FACE_COUNT * 8);
        assertEquals(0b100L, RenderDataFactory.getQuadTyping(directional));
        assertEquals(0b010L, RenderDataFactory.getQuadTyping(doubleSided));
        assertEquals(0L, RenderDataFactory.getQuadTyping(translucentDoubleSided));

        String mesher = Files.readString(Path.of(
                "src/main/java/me/cortex/voxy/forge/RenderDataFactory.java"));
        assertTrue(mesher.contains("int type = (auxData>>1)&3;//Translucent, double side, directional"));
        assertTrue(mesher.contains("int bufferIdx = type+(type==2?face:0)"));

        String cmdgen = Files.readString(Path.of(
                "src/main/resources/assets/voxy/shaders/lod/gl46/cmdgen.comp"));
        assertTrue(cmdgen.contains("//Double sided quads"));
        assertTrue(cmdgen.contains("count = (counts.x>>16)&0xFFFFu;"));
    }

    private static void assertThinFixture(
            String name,
            BlockState state,
            int[] presentFaces,
            int expectedPresentMask,
            int expectedMissingAxisMask
    ) {
        ModelFactory.PreparedRecord prepared = prepare(
                state,
                faces(presentFaces),
                ForgeOriginalVoxyModelLayer.CUTOUT);
        ModelFactory.DoubleSidedClassification classification =
                ModelFactory.classifyDoubleSidedFaces(prepared.faces());

        assertTrue(classification.required(), name);
        assertEquals(expectedPresentMask, classification.presentFaceMask(), name);
        assertEquals(expectedMissingAxisMask, classification.missingOppositeAxisMask(), name);
        assertEquals(presentFaces.length, prepared.writtenFaceCount(), name + " must not duplicate faces");
        assertTrue(ModelQueries.isDoubleSided(prepared.voxyMetadata()), name);
        assertEquals(0b010L, RenderDataFactory.getQuadTyping(prepared.voxyMetadata()), name);
    }

    private static void assertDirectionalFixture(String name, BlockState state) {
        ModelFactory.PreparedRecord prepared = prepare(
                state,
                faces(0, 1, 2, 3, 4, 5),
                ForgeOriginalVoxyModelLayer.CUTOUT);
        ModelFactory.DoubleSidedClassification classification =
                ModelFactory.classifyDoubleSidedFaces(prepared.faces());

        assertFalse(classification.required(), name);
        assertEquals(0b111111, classification.presentFaceMask(), name);
        assertEquals(0, classification.missingOppositeAxisMask(), name);
        assertEquals(ForgeModelAtlasLayout.FACE_COUNT, prepared.writtenFaceCount(), name);
        assertFalse(ModelQueries.isDoubleSided(prepared.voxyMetadata()), name);
        assertEquals(0b100L, RenderDataFactory.getQuadTyping(prepared.voxyMetadata()), name);
    }

    private static ModelFactory.PreparedRecord prepare(
            BlockState state,
            ColourDepthTextureData[] textures,
            ForgeOriginalVoxyModelLayer layer
    ) {
        return ModelFactory.prepareRecord(
                state,
                new ForgeSoftwareModelTextureBakery.BakeResult(textures, layer, 0, "none"),
                new ModelFactory.TintPlan(
                        false,
                        false,
                        new ModelFactory.TintSourcePlan(List.of()),
                        -1,
                        -1,
                        null,
                        -1),
                -1,
                0);
    }

    private static ColourDepthTextureData[] faces(int... presentFaces) {
        ColourDepthTextureData[] faces = new ColourDepthTextureData[ForgeModelAtlasLayout.FACE_COUNT];
        for (int face = 0; face < faces.length; face++) {
            faces[face] = emptyFace();
        }
        for (int face : presentFaces) {
            faces[face] = thinFace();
        }
        return faces;
    }

    private static ColourDepthTextureData emptyFace() {
        return new ColourDepthTextureData(new int[SIZE * SIZE], new int[SIZE * SIZE], SIZE, SIZE);
    }

    private static ColourDepthTextureData thinFace() {
        int[] colours = new int[SIZE * SIZE];
        int[] depths = new int[SIZE * SIZE];
        for (int v = 2; v < 14; v++) {
            for (int u = 6; u < 10; u++) {
                int index = v * SIZE + u;
                colours[index] = 0xFFFFFFFF;
                depths[index] = 1;
            }
        }
        return new ColourDepthTextureData(colours, depths, SIZE, SIZE);
    }
}
