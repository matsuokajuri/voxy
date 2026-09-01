package me.cortex.voxy.forge;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiPredicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class Round9FaceDecisionContractTest {
    private static final int SIZE = ForgeModelAtlasLayout.MODEL_TEXTURE_SIZE;
    private static final int FACE = 4;
    private static final int OPPOSITE_FACE = FACE ^ 1;
    private static final long SELF_LIGHT = 0xA5L << 55;
    private static final long NEIGHBOR_LIGHT = 0x3CL << 55;

    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void decisionTableRequiresRealCoverageAndSelectsOneLightOwner() {
        MaskLookup lookup = new MaskLookup();
        int selfId = 31;
        int neighborId = 47;
        long[] selfMasks = mask(FACE, (u, v) -> u == 3 || v == 9);
        copyFaceMask(selfMasks, FACE, selfMasks, OPPOSITE_FACE);
        lookup.put(selfId, selfMasks);
        lookup.put(neighborId, mask(OPPOSITE_FACE, (u, v) -> u == 3 || v == 9));

        long ordinaryFace = metadata(0b0100, 0);
        long selfLitFace = metadata(0b1100, 0);
        long sameCullFace = metadata(0b0100, 0b10_0000);
        long exactNeighbor = metadata(0b1_0100, 0);
        long coarseNeighbor = metadata(0b0001, 0);
        long missingFace = metadata(0xFF, 0);

        assertEquals(RenderFaceDecision.Result.CULLED_MISSING_FACE,
                decide(FACE, selfId, missingFace, neighborId, 0L, lookup));
        assertEquals(RenderFaceDecision.Result.CULLED_SAME_MODEL_COVERAGE,
                decide(FACE, selfId, sameCullFace, selfId, sameCullFace, lookup));
        assertEquals(RenderFaceDecision.Result.CULLED_NEIGHBOR_COVERAGE,
                decide(FACE, selfId, ordinaryFace, neighborId, exactNeighbor, lookup));
        assertEquals(RenderFaceDecision.Result.CULLED_NEIGHBOR_COVERAGE,
                decide(FACE, selfId, ordinaryFace, neighborId, coarseNeighbor, lookup));

        lookup.put(neighborId, mask(OPPOSITE_FACE, (u, v) -> u == 3 && v < 9));
        assertEquals(RenderFaceDecision.Result.MESH_NEIGHBOR_LIGHT,
                decide(FACE, selfId, ordinaryFace, neighborId, exactNeighbor, lookup));
        assertEquals(RenderFaceDecision.Result.MESH_SELF_LIGHT,
                decide(FACE, selfId, selfLitFace, neighborId, exactNeighbor, lookup));

        long selfQuad = quad(selfId, SELF_LIGHT);
        long neighborQuad = quad(neighborId, NEIGHBOR_LIGHT);
        assertEquals(NEIGHBOR_LIGHT, RenderFaceDecision.selectedLight(
                RenderFaceDecision.Result.MESH_NEIGHBOR_LIGHT,
                selfQuad,
                neighborQuad));
        assertEquals(SELF_LIGHT, RenderFaceDecision.selectedLight(
                RenderFaceDecision.Result.MESH_SELF_LIGHT,
                selfQuad,
                neighborQuad));
        assertEquals(selfId, RenderFaceDecision.modelId(selfQuad));
        assertEquals(neighborId, RenderFaceDecision.modelId(neighborQuad));
    }

    @Test
    void sameModelPermissionAloneCannotCullMismatchedPartialFaces() {
        int modelId = 61;
        MaskLookup lookup = new MaskLookup();
        long[] masks = new long[FaceOcclusionMask.WORDS_PER_MODEL];
        setPixel(masks, FACE, 2, 3);
        setPixel(masks, OPPOSITE_FACE, 13, 3);
        lookup.put(modelId, masks);

        long sameCullFace = metadata(0b0100, 0b10_0000);
        assertEquals(RenderFaceDecision.Result.MESH_NEIGHBOR_LIGHT,
                decide(FACE, modelId, sameCullFace, modelId, sameCullFace, lookup));
    }

    @Test
    void representativeOriginalModelClassesUseTheSharedProductionDecision() {
        BlockState waterloggedSlab = initialized(Blocks.OAK_SLAB.defaultBlockState()
                .setValue(BlockStateProperties.WATERLOGGED, true));
        List<MaterialFixture> fixtures = List.of(
                new MaterialFixture(
                        "slab",
                        initialized(Blocks.OAK_SLAB.defaultBlockState()),
                        patternedFaces((u, v) -> v >= SIZE / 2, 0xFF907050, 1),
                        ForgeOriginalVoxyModelLayer.SOLID,
                        -1,
                        false,
                        false,
                        false,
                        0,
                        RenderFaceDecision.Result.CULLED_NEIGHBOR_COVERAGE),
                new MaterialFixture(
                        "stair",
                        initialized(Blocks.OAK_STAIRS.defaultBlockState()),
                        patternedFaces((u, v) -> v >= SIZE / 2 || u < SIZE / 2, 0xFF907050, 1),
                        ForgeOriginalVoxyModelLayer.SOLID,
                        -1,
                        false,
                        false,
                        false,
                        0,
                        RenderFaceDecision.Result.CULLED_NEIGHBOR_COVERAGE),
                new MaterialFixture(
                        "leaves",
                        initialized(Blocks.OAK_LEAVES.defaultBlockState()),
                        patternedFaces((u, v) -> (u + v) % 3 != 0, 0xFF208040, 1),
                        ForgeOriginalVoxyModelLayer.SOLID,
                        -1,
                        false,
                        false,
                        false,
                        0,
                        RenderFaceDecision.Result.CULLED_NEIGHBOR_COVERAGE),
                new MaterialFixture(
                        "stained-glass",
                        initialized(Blocks.RED_STAINED_GLASS.defaultBlockState()),
                        fullFaces(0x7FFF2020, 1),
                        ForgeOriginalVoxyModelLayer.TRANSLUCENT,
                        -1,
                        true,
                        false,
                        false,
                        0,
                        RenderFaceDecision.Result.CULLED_SAME_MODEL_COVERAGE),
                new MaterialFixture(
                        "glass-pane",
                        initialized(Blocks.GLASS_PANE.defaultBlockState()),
                        patternedFaces((u, v) -> u == 7 || u == 8, 0x7FFFFFFF, 1),
                        ForgeOriginalVoxyModelLayer.TRANSLUCENT,
                        -1,
                        false,
                        false,
                        false,
                        0,
                        RenderFaceDecision.Result.MESH_SELF_LIGHT),
                new MaterialFixture(
                        "vine",
                        initialized(Blocks.VINE.defaultBlockState()),
                        singleFace(FACE, patternedTexture((u, v) -> (u + v) % 4 != 0, 0xFF207020, 1)),
                        ForgeOriginalVoxyModelLayer.CUTOUT,
                        -1,
                        false,
                        false,
                        false,
                        0,
                        RenderFaceDecision.Result.MESH_NEIGHBOR_LIGHT),
                new MaterialFixture(
                        "water",
                        initialized(Blocks.WATER.defaultBlockState()),
                        fullFaces(0x804080D0, 1),
                        ForgeOriginalVoxyModelLayer.TRANSLUCENT,
                        -1,
                        true,
                        true,
                        false,
                        0,
                        RenderFaceDecision.Result.CULLED_SAME_MODEL_COVERAGE),
                new MaterialFixture(
                        "waterlogged-slab",
                        waterloggedSlab,
                        patternedFaces((u, v) -> v >= SIZE / 2, 0xFF907050, 1),
                        ForgeOriginalVoxyModelLayer.SOLID,
                        91,
                        false,
                        false,
                        true,
                        0,
                        RenderFaceDecision.Result.CULLED_NEIGHBOR_COVERAGE),
                new MaterialFixture(
                        "emissive",
                        initialized(Blocks.GLOWSTONE.defaultBlockState()),
                        fullFaces(0xFFFFD080, 1),
                        ForgeOriginalVoxyModelLayer.SOLID,
                        -1,
                        false,
                        false,
                        false,
                        15,
                        RenderFaceDecision.Result.CULLED_NEIGHBOR_COVERAGE));

        int modelId = 100;
        for (MaterialFixture fixture : fixtures) {
            assertEquals(
                    fixture.containsFluid(),
                    !fixture.state().getFluidState().isEmpty() && fixture.fluidModelId() != -1,
                    fixture.name() + " source fluid state");
            ModelFactory.PreparedRecord prepared = prepared(fixture);
            long metadata = prepared.voxyMetadata();
            assertEquals(fixture.cullsSame(), ModelQueries.cullsSame(metadata), fixture.name());
            assertEquals(fixture.fluid(), ModelQueries.isFluid(metadata), fixture.name());
            assertEquals(fixture.containsFluid(), ModelQueries.containsFluid(metadata), fixture.name());
            assertEquals(fixture.emission(), ModelQueries.lightEmission(metadata), fixture.name());

            MaskLookup lookup = new MaskLookup();
            lookup.put(modelId, prepared.faceOcclusionMasks());
            RenderFaceDecision.Result result = decide(
                    FACE,
                    modelId,
                    metadata,
                    modelId,
                    metadata,
                    lookup);
            assertEquals(fixture.sameModelDecision(), result, fixture.name());
            if (result.meshes()) {
                long selected = RenderFaceDecision.selectedLight(
                        result,
                        quad(modelId, SELF_LIGHT),
                        quad(modelId, NEIGHBOR_LIGHT));
                assertEquals(result.usesSelfLight() ? SELF_LIGHT : NEIGHBOR_LIGHT, selected, fixture.name());
            }
            modelId++;
        }
    }

    private static ModelFactory.PreparedRecord prepared(MaterialFixture fixture) {
        return ModelFactory.prepareRecord(
                fixture.state(),
                new ForgeSoftwareModelTextureBakery.BakeResult(
                        fixture.faces(),
                        fixture.layer(),
                        0,
                        "none"),
                new ModelFactory.TintPlan(
                        false,
                        false,
                        new ModelFactory.TintSourcePlan(List.of()),
                        -1,
                        -1,
                        null,
                        -1),
                fixture.fluidModelId(),
                0);
    }

    private static BlockState initialized(BlockState state) {
        state.initCache();
        return state;
    }

    private static RenderFaceDecision.Result decide(
            int face,
            int selfId,
            long selfMetadata,
            int neighborId,
            long neighborMetadata,
            RenderFaceDecision.FaceCoverageLookup lookup
    ) {
        return RenderFaceDecision.evaluate(
                face,
                quad(selfId, SELF_LIGHT),
                selfMetadata,
                quad(neighborId, NEIGHBOR_LIGHT),
                neighborMetadata,
                lookup);
    }

    private static long quad(int modelId, long light) {
        return ((long) modelId << 26) | light;
    }

    private static long metadata(int faceByte, int globalByte) {
        long metadata = ((long) globalByte & 0xFFFFL) << 48;
        for (int face = 0; face < ForgeModelAtlasLayout.FACE_COUNT; face++) {
            metadata |= ((long) faceByte & 0xFFL) << (face * Byte.SIZE);
        }
        return metadata;
    }

    private static ColourDepthTextureData[] fullFaces(int colour, int depth) {
        return patternedFaces((u, v) -> true, colour, depth);
    }

    private static ColourDepthTextureData[] patternedFaces(
            BiPredicate<Integer, Integer> written,
            int colour,
            int depth
    ) {
        ColourDepthTextureData[] faces = new ColourDepthTextureData[ForgeModelAtlasLayout.FACE_COUNT];
        for (int face = 0; face < faces.length; face++) {
            faces[face] = patternedTexture(written, colour, depth);
        }
        return faces;
    }

    private static ColourDepthTextureData[] singleFace(int face, ColourDepthTextureData texture) {
        ColourDepthTextureData[] faces = new ColourDepthTextureData[ForgeModelAtlasLayout.FACE_COUNT];
        faces[face] = texture;
        return faces;
    }

    private static ColourDepthTextureData patternedTexture(
            BiPredicate<Integer, Integer> written,
            int colour,
            int depth
    ) {
        int[] colours = new int[SIZE * SIZE];
        int[] depths = new int[colours.length];
        for (int v = 0; v < SIZE; v++) {
            for (int u = 0; u < SIZE; u++) {
                if (written.test(u, v)) {
                    int index = u + v * SIZE;
                    colours[index] = colour;
                    depths[index] = depth;
                }
            }
        }
        return new ColourDepthTextureData(colours, depths, SIZE, SIZE);
    }

    private static long[] mask(int face, BiPredicate<Integer, Integer> written) {
        long[] masks = new long[FaceOcclusionMask.WORDS_PER_MODEL];
        for (int v = 0; v < SIZE; v++) {
            for (int u = 0; u < SIZE; u++) {
                if (written.test(u, v)) {
                    setPixel(masks, face, u, v);
                }
            }
        }
        return masks;
    }

    private static void setPixel(long[] masks, int face, int u, int v) {
        int pixel = u + v * SIZE;
        masks[FaceOcclusionMask.faceOffset(face) + pixel / Long.SIZE]
                |= 1L << (pixel & (Long.SIZE - 1));
    }

    private static void copyFaceMask(long[] source, int sourceFace, long[] target, int targetFace) {
        System.arraycopy(
                source,
                FaceOcclusionMask.faceOffset(sourceFace),
                target,
                FaceOcclusionMask.faceOffset(targetFace),
                FaceOcclusionMask.WORDS_PER_FACE);
    }

    private static final class MaskLookup implements RenderFaceDecision.FaceCoverageLookup {
        private final Map<Integer, long[]> masks = new HashMap<>();

        void put(int modelId, long[] modelMasks) {
            this.masks.put(modelId, Arrays.copyOf(modelMasks, modelMasks.length));
        }

        @Override
        public boolean isFaceCoverageOccludedBy(
                int modelId,
                int face,
                int occluderModelId,
                int occluderFace
        ) {
            return FaceOcclusionMask.covers(
                    this.masks.get(occluderModelId),
                    occluderFace,
                    this.masks.get(modelId),
                    face);
        }
    }

    private record MaterialFixture(
            String name,
            BlockState state,
            ColourDepthTextureData[] faces,
            ForgeOriginalVoxyModelLayer layer,
            int fluidModelId,
            boolean cullsSame,
            boolean fluid,
            boolean containsFluid,
            int emission,
            RenderFaceDecision.Result sameModelDecision
    ) {
    }
}
