package me.cortex.voxy.forge;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Round10ModelShaderContractTest {
    private static final int SIZE = 16;
    private static final int DEPTH_MAX = (1 << 24) - 1;
    private static final ModelFactory.TintPlan NO_TINT = tint(false, -1);

    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void sixBakeryViewsMeasureDistanceFromTheirOwnFaceWithoutAOneQuantumBias() throws Exception {
        var viewsField = ForgeSoftwareModelTextureBakery.class.getDeclaredField("VIEWS");
        viewsField.setAccessible(true);
        Matrix4f[] views = (Matrix4f[]) viewsField.get(null);
        for (int face = 0; face < 6; face++) {
            int axis = worldAxis(face);
            for (int code : new int[]{0, 1, 31, 32, 62, 63, 64}) {
                float depth = code / 64.0F;
                Vector3f point = new Vector3f(0.3125F, 0.5625F, 0.6875F);
                point.setComponent(axis, (face & 1) == 0 ? depth : 1.0F - depth);
                Vector3f projected = views[face].transformPosition(new Vector3f(point));
                float rasterDepth = Math.fma(projected.z, 0.5F, 0.5F);
                assertEquals(depth, rasterDepth, 0.000001F, "face " + face + " code " + code);
            }
        }
    }

    @Test
    void producerPreservesOriginalDepthQuantizationAndPartialBoundsOnEveryFace() {
        for (int code : new int[]{0, 1, 31, 32, 62, 63, 64}) {
            ModelFactory.PreparedRecord record = prepare(
                    faces(code / 64.0F, 3, 12, 2, 14, false, false),
                    ForgeOriginalVoxyModelLayer.SOLID, NO_TINT);
            assertEquals(64, record.words().length * Integer.BYTES);
            for (int face = 0; face < 6; face++) {
                int word = record.words()[face];
                assertEquals(Math.min(code, 62), (word >>> 16) & 63, "face " + face + " code " + code);
                assertEquals(3, word & 15);
                assertEquals(12, (word >>> 4) & 15);
                assertEquals(2, (word >>> 8) & 15);
                assertEquals(14, (word >>> 12) & 15);
                assertEquals(3 / 16.0F, decodeBounds(word)[0]);
                assertEquals(13 / 16.0F, decodeBounds(word)[1]);
                assertEquals(2 / 16.0F, decodeBounds(word)[2]);
                assertEquals(15 / 16.0F, decodeBounds(word)[3]);
            }
        }
        // Reserved decoder code 63 is deliberately not emitted by ModelFactory.
        String decoder = ShaderLoader.parse("voxy:lod/block_model.glsl");
        assertTrue(decoder.contains("enc += uint(enc==63u)"));
        assertTrue(decoder.contains("return float(enc)/64.0"));
    }

    @Test
    void signedSectionCoordinatesAndLodScaleDoNotIntroduceAnAdditionalFaceOffset() {
        for (int code : new int[]{0, 1, 31, 32, 62, 63, 64}) {
            ModelFactory.PreparedRecord record = prepare(
                    faces(code / 64.0F, 0, 15, 0, 15, false, false),
                    ForgeOriginalVoxyModelLayer.SOLID, NO_TINT);
            for (int lod = 0; lod <= GpuBufferLayout.MAX_LOD; lod++) {
                int scale = 1 << lod;
                for (int section : new int[]{-33, -1, 0, 17}) {
                    int baseSection = (section << lod) - (-3);
                    float origin = baseSection << 5;
                    for (int face = 0; face < 6; face++) {
                        int encoded = (record.words()[face] >>> 16) & 63;
                        float indentation = (encoded == 63 ? 64 : encoded) / 64.0F;
                        float normalOffset = (face & 1) == 0 ? indentation : 1.0F - indentation;
                        float producedPosition = origin + (7.0F + normalOffset) * scale;
                        float baselineDepth = Math.min(code, 62) / 64.0F;
                        float expectedPosition = origin + (7.0F
                                + ((face & 1) == 0 ? baselineDepth : 1.0F - baselineDepth)) * scale;
                        assertEquals(expectedPosition, producedPosition);
                        float idealOffset = (face & 1) == 0 ? code / 64.0F : 1.0F - code / 64.0F;
                        float idealPosition = origin + (7.0F + idealOffset) * scale;
                        assertEquals(Math.max(0, code - 62) * scale / 64.0F,
                                Math.abs(idealPosition - producedPosition));
                    }
                }
            }
        }
        String quad = ShaderLoader.parse("voxy:lod/quad_util.glsl");
        assertTrue(quad.contains("mix(depthOffset, 1-depthOffset, float(face&1u))"));
        assertTrue(quad.contains("(quadStart*lodScale)+vec3(baseSection<<5)"));
    }

    @Test
    void mergedPartialSolidBoundsAlreadyRequestAlphaTestingAcrossRepeatedTiles() {
        int partial = prepare(faces(0, 4, 11, 3, 12, false, false),
                ForgeOriginalVoxyModelLayer.SOLID, NO_TINT).words()[0];
        int full = prepare(faces(0, 0, 15, 0, 15, false, false),
                ForgeOriginalVoxyModelLayer.SOLID, NO_TINT).words()[0];
        int cutout = prepare(faces(0, 4, 11, 3, 12, true, false),
                ForgeOriginalVoxyModelLayer.CUTOUT, NO_TINT).words()[0];
        int translucent = prepare(faces(0, 4, 11, 3, 12, false, false),
                ForgeOriginalVoxyModelLayer.TRANSLUCENT, NO_TINT).words()[0];
        assertEquals(0, (partial >>> 22) & 1);
        assertEquals(1, (partial >>> 23) & 1);
        for (int width : new int[]{1, 2, 16}) {
            for (int height : new int[]{1, 2, 16}) {
                assertEquals(width > 1 || height > 1, discardFlag(partial, width, height));
                assertFalse(discardFlag(full, width, height));
                assertTrue(discardFlag(cutout, width, height));
                assertFalse(discardFlag(translucent, width, height));
            }
        }
        String quad = ShaderLoader.parse("voxy:lod/quad_util.glsl");
        assertTrue(quad.contains("flags |= uint(any(greaterThan(quadSize, ivec2(1)))) & faceHasAlphaCuttoutOverride(faceData)"));
        String fragment = ShaderLoader.parse("voxy:lod/gl46/quads.frag");
        assertTrue(fragment.contains("useDiscard() && (textureLod(blockModelAtlas, texPos, 0).a <= 0.1f)"));
        assertTrue(fragment.contains("textureLod(blockModelAtlas, texPos, 0).a == 0.0f"));
    }

    @Test
    void partiallyTintedColourDetailsRetainBaseTexelsAndUnmodifiedAlpha() {
        ColourDepthTextureData[] faces = faces(0, 0, 15, 0, 15, false, true);
        ModelFactory.PreparedRecord record = prepare(faces, ForgeOriginalVoxyModelLayer.SOLID, tint(true, 0xFF55AA33));
        assertEquals(1, (record.words()[0] >>> 24) & 3, "Mixed tinted/untinted bakery pixels select partial tint");
        byte[][] mips = MipGen.putTextures(false, faces);
        assertEquals(0xFF808080, rgbaWord(mips[0], 0));
        assertEquals(0xFF2020C0, rgbaWord(mips[0], 1));
        assertTrue(neutral(rgbaWord(mips[0], 0)));
        assertFalse(neutral(rgbaWord(mips[0], 1)));
        assertFalse(neutral(rgbaWord(mips[1], 0)), "A colour mip average cannot stand in for a base-texel tint mask");
        for (int level = 0; level < mips.length; level++) {
            for (int alpha = 3; alpha < mips[level].length; alpha += 4) {
                assertEquals(255, Byte.toUnsignedInt(mips[level][alpha]), "Tint must not borrow an alpha bit");
            }
        }
        String fragment = ShaderLoader.parse("voxy:lod/gl46/quads.frag");
        assertTrue(fragment.contains("shouldApplyModelTint(tintingState(), texturePos)"));
        assertTrue(fragment.contains("shouldApplyModelTint(tintingState(), texPos)"));
        String tint = ShaderLoader.parse("voxy:lod/model_tint.glsl");
        assertTrue(tint.contains("textureLod(blockModelAtlas, texturePos, 0.0).rgb"));
        assertFalse(tint.contains("texture(blockModelAtlas"));
    }

    @Test
    void fragmentGradientConsumerAndExistingShaderpackAbiRemainExplicit() {
        String fragment = ShaderLoader.parse("voxy:lod/gl46/quads.frag");
        int sample = fragment.indexOf("colour = textureGrad(blockModelAtlas, texPos, dx, dy)");
        assertTrue(fragment.indexOf("dFdx(uvSmol)") < sample);
        assertTrue(fragment.indexOf("dFdy(uvSmol)") < sample);
        assertTrue(sample < fragment.indexOf("if (gl_HelperInvocation)"));
        assertTrue(fragment.contains("#ifndef PATCHED_SHADER_ALLOW_DERIVATIVES"));
        int from = fragment.indexOf("struct VoxyFragmentParameters {");
        int to = fragment.indexOf("};", from);
        String abi = fragment.substring(from, to);
        assertEquals(8L, Pattern.compile("(?m)^\\s*(vec[234]|uint)\\s+\\w+;").matcher(abi).results().count());
        assertFalse(Pattern.compile("(?m)^\\s*(vec[234]|float)\\s+(dx|dy|derivative\\w*);").matcher(abi).find());
        assertFalse(fragment.contains("useMipmaps"));

        String vertex = ShaderLoader.parse("voxy:lod/gl46/quads3.vert");
        int main = vertex.indexOf("void main()");
        assertTrue(vertex.indexOf("taaOffset = taaShift()", main) < vertex.indexOf("setupQuad(quad", main));
        assertTrue(vertex.contains("pos.xy += taaOffset*pos.w"));
        assertFalse(vertex.contains("modelHasMipmaps"));
    }

    private static int worldAxis(int face) {
        return switch (face >> 1) { case 0 -> 1; case 1 -> 2; default -> 0; };
    }

    private static boolean discardFlag(int faceWord, int width, int height) {
        return ((faceWord >>> 22) & 1) != 0 || ((width > 1 || height > 1) && ((faceWord >>> 23) & 1) != 0);
    }

    private static float[] decodeBounds(int word) {
        return new float[]{(word & 15) / 16.0F, (((word >>> 4) & 15) + 1) / 16.0F,
                ((word >>> 8) & 15) / 16.0F, (((word >>> 12) & 15) + 1) / 16.0F};
    }

    private static ModelFactory.TintPlan tint(boolean enabled, int colour) {
        return new ModelFactory.TintPlan(enabled, false, new ModelFactory.TintSourcePlan(List.of()),
                colour, colour, null, -1);
    }

    private static ModelFactory.PreparedRecord prepare(ColourDepthTextureData[] textures,
                                                      ForgeOriginalVoxyModelLayer layer, ModelFactory.TintPlan tint) {
        return ModelFactory.prepareRecord(Blocks.STONE.defaultBlockState(),
                new ForgeSoftwareModelTextureBakery.BakeResult(textures, layer, ForgeSoftwareModelTextureBakery.FLAG_SHADED, "none"),
                tint, -1, 123);
    }

    private static ColourDepthTextureData[] faces(float depth, int minX, int maxX, int minY, int maxY,
                                                boolean holes, boolean mixedTint) {
        ColourDepthTextureData[] result = new ColourDepthTextureData[6];
        int depthWord = (Math.round(depth * DEPTH_MAX) << 8) | 1;
        for (int face = 0; face < result.length; face++) {
            int[] colours = new int[SIZE * SIZE];
            int[] depths = new int[colours.length];
            for (int y = minY; y <= maxY; y++) {
                for (int x = minX; x <= maxX; x++) {
                    if (holes && x > minX && x < maxX && y > minY && y < maxY && (x + y) % 2 == 0) continue;
                    int index = x + y * SIZE;
                    colours[index] = !mixedTint || (x & 1) == 0 ? 0xFF808080 : 0xFF2020C0;
                    depths[index] = depthWord | (mixedTint && (x & 1) == 0 ? 0x80 : 0);
                }
            }
            result[face] = new ColourDepthTextureData(colours, depths, SIZE, SIZE);
        }
        return result;
    }

    private static int rgbaWord(byte[] pixels, int index) {
        int offset = index * 4;
        return Byte.toUnsignedInt(pixels[offset]) | (Byte.toUnsignedInt(pixels[offset + 1]) << 8)
                | (Byte.toUnsignedInt(pixels[offset + 2]) << 16) | (Byte.toUnsignedInt(pixels[offset + 3]) << 24);
    }

    private static boolean neutral(int rgba) {
        float r = (rgba & 255) / 255.0F;
        float g = ((rgba >>> 8) & 255) / 255.0F;
        float b = ((rgba >>> 16) & 255) / 255.0F;
        return Math.abs(r - g) < 0.02F && Math.abs(g - b) < 0.02F;
    }
}
