package me.cortex.voxy.forge;

import me.cortex.voxy.common.util.MemoryBuffer;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.lwjgl.system.MemoryUtil;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class Round8ModelMaterialGoldenTest {
    private static final int SIZE = ForgeModelAtlasLayout.MODEL_TEXTURE_SIZE;
    private static final int OPAQUE_COLOUR = 0xFF807060;
    private static final int CONSTANT_TINT = 0xFF55AA33;

    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void representativePostRasterFixturesProduceTheOriginalPackedMaterialContract() {
        for (MaterialFixture fixture : fixtures()) {
            ForgeOriginalVoxyModelLayer layer = ForgeSoftwareModelTextureBakery.chooseLayer(
                    fixture.forceSolid(),
                    fixture.softwareFlags(),
                    fixture.faces());
            assertEquals(fixture.expectedLayer(), layer, fixture.name());

            ForgeSoftwareModelTextureBakery.BakeResult bake =
                    new ForgeSoftwareModelTextureBakery.BakeResult(
                            fixture.faces(),
                            layer,
                            fixture.softwareFlags(),
                            "none");
            ModelFactory.PreparedRecord prepared = ModelFactory.prepareRecord(
                    fixture.state(),
                    bake,
                    fixture.tint(),
                    fixture.fluidModelId(),
                    fixture.customId());

            assertEquals(ForgeModelStoreLayoutSpec.MODEL_RECORD_WORDS, prepared.words().length, fixture.name());
            assertEquals(fixture.expectedGpuFlags(), prepared.flags(), fixture.name());
            assertEquals(fixture.expectedGpuFlags(),
                    prepared.words()[ForgeModelStoreLayoutSpec.WORD_FLAGS_A], fixture.name());
            assertEquals(fixture.tint().dedupeColour(),
                    prepared.words()[ForgeModelStoreLayoutSpec.WORD_COLOUR_TINT], fixture.name());
            assertEquals(fixture.customId(),
                    prepared.words()[ForgeModelStoreLayoutSpec.WORD_CUSTOM_ID], fixture.name());
            for (int face = 0; face < ForgeModelAtlasLayout.FACE_COUNT; face++) {
                assertEquals(fixture.expectedFaceWord(), prepared.words()[face], fixture.name() + " face " + face);
            }
            for (int word = ForgeModelStoreLayoutSpec.WORD_CUSTOM_ID + 1;
                 word < ForgeModelStoreLayoutSpec.MODEL_RECORD_WORDS;
                 word++) {
                assertEquals(0, prepared.words()[word], fixture.name() + " padding word " + word);
            }

            MemoryBuffer serialized = ModelFactory.serializeModelRecord(prepared.words());
            try {
                assertEquals(ForgeModelStoreLayoutSpec.MODEL_RECORD_BYTES, serialized.size, fixture.name());
                for (int word = 0; word < ForgeModelStoreLayoutSpec.MODEL_RECORD_WORDS; word++) {
                    assertEquals(prepared.words()[word],
                            MemoryUtil.memGetInt(serialized.address + (long) word * Integer.BYTES),
                            fixture.name() + " serialized word " + word);
                }
            } finally {
                serialized.free();
            }

            long metadata = prepared.voxyMetadata();
            assertEquals(fixture.biomeTint(), ModelQueries.isBiomeColoured(metadata), fixture.name());
            assertEquals(fixture.translucent(), ModelQueries.isTranslucent(metadata), fixture.name());
            assertEquals(fixture.fluid(), ModelQueries.isFluid(metadata), fixture.name());
            assertEquals(fixture.emission(), ModelQueries.lightEmission(metadata), fixture.name());
            assertEquals(fixture.fullyOpaque(), ModelQueries.isFullyOpaque(metadata), fixture.name());
            assertEquals(fixture.faceOccludes(), ModelQueries.faceOccludes(metadata, 0), fixture.name());
            assertEquals(fixture.faceSelfLighting(), ModelQueries.faceUsesSelfLighting(metadata, 0), fixture.name());
        }
    }

    @Test
    void representativeFixturesProduceStableMipChainsOnArrayAndUploadPaths() {
        List<String> actualGoldens = new ArrayList<>();
        for (MaterialFixture fixture : fixtures()) {
            boolean darkened = (fixture.softwareFlags() & ForgeSoftwareModelTextureBakery.FLAG_DARKENED) != 0;
            byte[][] mips = MipGen.putTextures(darkened, fixture.faces());
            assertEquals(4, mips.length, fixture.name());
            assertArrayEquals(new int[]{6144, 1536, 384, 96},
                    Arrays.stream(mips).mapToInt(level -> level.length).toArray(), fixture.name());

            MemoryBuffer upload = MipGen.putTexturesBuffer(darkened, fixture.faces());
            try {
                int offset = 0;
                for (byte[] mip : mips) {
                    for (int index = 0; index < mip.length; index++) {
                        assertEquals(mip[index], MemoryUtil.memGetByte(upload.address + offset + index),
                                fixture.name() + " upload byte " + (offset + index));
                    }
                    offset += mip.length;
                }
                assertEquals(MipGen.UPLOADED_MIP_CHAIN_BYTES, offset, fixture.name());
                assertEquals(MipGen.ORIGINAL_MODEL_TEXTURE_BUFFER_BYTES, upload.size, fixture.name());
                for (long index = offset; index < upload.size; index++) {
                    assertEquals(0, MemoryUtil.memGetByte(upload.address + index),
                            fixture.name() + " unused original mip tail byte " + index);
                }
            } finally {
                upload.free();
            }
            actualGoldens.add(fixture.name() + "=" + sha256(mips));
        }

        assertEquals(List.of(
                "opaque-stone=a19337ba8c6179b83f6f766170c5d28508170f182a4d9fe3a9f35715d938dae2",
                "cutout-iron-bars=ffd9bb91707accd625b308e6cccdc8a78d61bb626ec598ecd7671d4ce8a4318a",
                "translucent-glass=39ec4940985455e8c89989f4189caebc11db1a900d99b45732b9fdbbe8a07db0",
                "fluid-water=6527e3d43552256891140d9ff53adfcf93dacf0f6cd6f54f99ecfdc2d6565774",
                "solid-leaves=28e77f09ce9f9f16e1209d96417e7a707fa4406f755b6a09357832fc3cf82287",
                "emissive-glowstone=1a2641d5664d9920f1c867c8b07df30e86b21d6da7a54a5f2da3974d534eb046",
                "partial-constant-tint=1d2f827d31961b0291e6eabf0e395e2b90187683c9f9ff8a541e38271c84c941"
        ), actualGoldens);
    }

    @Test
    void packedFaceWordsDecodeWithTheActiveShaderBitContract() {
        for (MaterialFixture fixture : fixtures()) {
            int word = fixture.expectedFaceWord();
            assertEquals(0, (word >>> 16) & 63, fixture.name() + " depth");
            assertEquals(fixture.alphaDiscard() ? 1 : 0, (word >>> 22) & 1,
                    fixture.name() + " alpha discard");
            assertEquals(fixture.tintMode(), (word >>> 24) & 3, fixture.name() + " tint mode");
            assertEquals(0, word & 15, fixture.name() + " min U");
            assertEquals(15, (word >>> 4) & 15, fixture.name() + " max U");
            assertEquals(0, (word >>> 8) & 15, fixture.name() + " min V");
            assertEquals(15, (word >>> 12) & 15, fixture.name() + " max V");
        }
    }

    private static List<MaterialFixture> fixtures() {
        ModelFactory.TintPlan noTint = tint(false, false, -1);
        ModelFactory.TintPlan biomeTint = tint(true, true, -1);
        ModelFactory.TintPlan constantTint = tint(true, false, CONSTANT_TINT);
        int shaded = ForgeSoftwareModelTextureBakery.FLAG_SHADED;
        return List.of(
                new MaterialFixture(
                        "opaque-stone", Blocks.STONE.defaultBlockState(), fullFaces(OPAQUE_COLOUR, 1),
                        shaded, false, noTint, -1, 101,
                        ForgeOriginalVoxyModelLayer.SOLID, 8, 0x0000F0F0,
                        false, false, false, 0, true, true, false, false, 0),
                new MaterialFixture(
                        "cutout-iron-bars", Blocks.IRON_BARS.defaultBlockState(), cutoutFaces(false),
                        shaded | ForgeSoftwareModelTextureBakery.FLAG_DISCARD, false, noTint, -1, 102,
                        ForgeOriginalVoxyModelLayer.CUTOUT, 8, 0x0040F0F0,
                        false, false, false, 0, false, false, false, true, 0),
                new MaterialFixture(
                        "translucent-glass", Blocks.GLASS.defaultBlockState(), fullFaces(0x7F90A0B0, 1),
                        shaded | ForgeSoftwareModelTextureBakery.FLAG_TRANSLUCENT, false, noTint, -1, 103,
                        ForgeOriginalVoxyModelLayer.TRANSLUCENT, 12, 0x0000F0F0,
                        false, true, false, 0, false, false, true, false, 0),
                new MaterialFixture(
                        "fluid-water", Blocks.WATER.defaultBlockState(), fullFaces(0x800040C0, 0x81),
                        shaded | ForgeSoftwareModelTextureBakery.FLAG_TRANSLUCENT, false, biomeTint, -1, 104,
                        ForgeOriginalVoxyModelLayer.TRANSLUCENT, 15, 0x0200F0F0,
                        true, true, true, 0, false, false, true, false, 2),
                new MaterialFixture(
                        "solid-leaves", Blocks.OAK_LEAVES.defaultBlockState(), cutoutFaces(true),
                        shaded | ForgeSoftwareModelTextureBakery.FLAG_DARKENED
                                | ForgeSoftwareModelTextureBakery.FLAG_DISCARD,
                        true, biomeTint, -1, 105,
                        ForgeOriginalVoxyModelLayer.SOLID, 11, 0x0240F0F0,
                        true, false, false, 0, false, false, false, true, 2),
                new MaterialFixture(
                        "emissive-glowstone", Blocks.GLOWSTONE.defaultBlockState(), fullFaces(0xFFFFC060, 1),
                        shaded, false, noTint, -1, 106,
                        ForgeOriginalVoxyModelLayer.SOLID, 8, 0x0000F0F0,
                        false, false, false, 15, true, true, false, false, 0),
                new MaterialFixture(
                        "partial-constant-tint", Blocks.GRASS_BLOCK.defaultBlockState(), partialTintFaces(),
                        shaded, false, constantTint, -1, 107,
                        ForgeOriginalVoxyModelLayer.SOLID, 9, 0x0100F0F0,
                        false, false, false, 0, true, true, false, false, 1)
        );
    }

    private static ModelFactory.TintPlan tint(boolean hasTint, boolean biomeDependent, int colour) {
        return new ModelFactory.TintPlan(
                hasTint,
                biomeDependent,
                new ModelFactory.TintSourcePlan(List.of()),
                colour,
                colour,
                null,
                -1);
    }

    private static ColourDepthTextureData[] fullFaces(int colour, int depth) {
        ColourDepthTextureData[] faces = new ColourDepthTextureData[ForgeModelAtlasLayout.FACE_COUNT];
        for (int face = 0; face < faces.length; face++) {
            int[] colours = new int[SIZE * SIZE];
            int[] depths = new int[colours.length];
            Arrays.fill(colours, colour);
            Arrays.fill(depths, depth);
            faces[face] = new ColourDepthTextureData(colours, depths, SIZE, SIZE);
        }
        return faces;
    }

    private static ColourDepthTextureData[] cutoutFaces(boolean tinted) {
        ColourDepthTextureData[] faces = new ColourDepthTextureData[ForgeModelAtlasLayout.FACE_COUNT];
        for (int face = 0; face < faces.length; face++) {
            int[] colours = new int[SIZE * SIZE];
            int[] depths = new int[colours.length];
            for (int y = 0; y < SIZE; y++) {
                for (int x = 0; x < SIZE; x++) {
                    int index = x + y * SIZE;
                    boolean hole = x > 0 && x < SIZE - 1 && y > 0 && y < SIZE - 1 && (x + y) % 3 == 0;
                    if (!hole) {
                        colours[index] = 0xFF207010;
                        depths[index] = tinted ? 0x81 : 1;
                    }
                }
            }
            faces[face] = new ColourDepthTextureData(colours, depths, SIZE, SIZE);
        }
        return faces;
    }

    private static ColourDepthTextureData[] partialTintFaces() {
        ColourDepthTextureData[] faces = fullFaces(0xFF60A040, 1);
        for (ColourDepthTextureData face : faces) {
            for (int y = 0; y < SIZE; y++) {
                for (int x = 0; x < SIZE / 2; x++) {
                    face.depth()[x + y * SIZE] = 0x81;
                }
            }
        }
        return faces;
    }

    private static String sha256(byte[][] levels) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (byte[] level : levels) {
                digest.update(level);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new AssertionError(exception);
        }
    }

    private record MaterialFixture(
            String name,
            BlockState state,
            ColourDepthTextureData[] faces,
            int softwareFlags,
            boolean forceSolid,
            ModelFactory.TintPlan tint,
            int fluidModelId,
            int customId,
            ForgeOriginalVoxyModelLayer expectedLayer,
            int expectedGpuFlags,
            int expectedFaceWord,
            boolean biomeTint,
            boolean translucent,
            boolean fluid,
            int emission,
            boolean fullyOpaque,
            boolean faceOccludes,
            boolean faceSelfLighting,
            boolean alphaDiscard,
            int tintMode
    ) {
    }
}
