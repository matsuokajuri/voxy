package me.cortex.voxy.forge;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class Round8ModelSemanticDedupTest {
    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void identicalTextureAndRenderSemanticsDeduplicate() {
        ModelFactory.ModelSemanticKey semantics = baseSemantics();
        ModelFactory.ModelEntry left = new ModelFactory.ModelEntry(faces(0xFF102030, 0x21), semantics);
        ModelFactory.ModelEntry right = new ModelFactory.ModelEntry(faces(0xFF102030, 0x21), semantics);

        assertEquals(left, right);
        assertEquals(left.hashCode(), right.hashCode());
    }

    @Test
    void everyPublishedRenderSemanticPreventsAnUnsafeMerge() {
        ModelFactory.ModelSemanticKey base = baseSemantics();
        List<ModelFactory.ModelSemanticKey> differences = List.of(
                new ModelFactory.ModelSemanticKey(8, base.voxyMetadata(), base.flags(), base.customId(),
                        base.tintColour(), base.biomeTintState(), base.softwareFlags(), base.layer(), base.faceData()),
                new ModelFactory.ModelSemanticKey(base.fluidModelId(), base.voxyMetadata() ^ (1L << 55), base.flags(), base.customId(),
                        base.tintColour(), base.biomeTintState(), base.softwareFlags(), base.layer(), base.faceData()),
                new ModelFactory.ModelSemanticKey(base.fluidModelId(), base.voxyMetadata(), base.flags() ^ 8, base.customId(),
                        base.tintColour(), base.biomeTintState(), base.softwareFlags(), base.layer(), base.faceData()),
                new ModelFactory.ModelSemanticKey(base.fluidModelId(), base.voxyMetadata(), base.flags(), 102,
                        base.tintColour(), base.biomeTintState(), base.softwareFlags(), base.layer(), base.faceData()),
                new ModelFactory.ModelSemanticKey(base.fluidModelId(), base.voxyMetadata(), base.flags(), base.customId(),
                        0xFFABCDEF, base.biomeTintState(), base.softwareFlags(), base.layer(), base.faceData()),
                new ModelFactory.ModelSemanticKey(base.fluidModelId(), base.voxyMetadata(), base.flags(), base.customId(),
                        base.tintColour(), Blocks.OAK_LEAVES.defaultBlockState(), base.softwareFlags(), base.layer(), base.faceData()),
                new ModelFactory.ModelSemanticKey(base.fluidModelId(), base.voxyMetadata(), base.flags(), base.customId(),
                        base.tintColour(), base.biomeTintState(), base.softwareFlags() ^ 2, base.layer(), base.faceData()),
                new ModelFactory.ModelSemanticKey(base.fluidModelId(), base.voxyMetadata(), base.flags(), base.customId(),
                        base.tintColour(), base.biomeTintState(), base.softwareFlags(), ForgeOriginalVoxyModelLayer.CUTOUT, base.faceData()),
                new ModelFactory.ModelSemanticKey(base.fluidModelId(), base.voxyMetadata(), base.flags(), base.customId(),
                        base.tintColour(), base.biomeTintState(), base.softwareFlags(), base.layer(),
                        new ModelFactory.FaceDataKey(1, 2, 3, 4, 5, 7))
        );

        ModelFactory.ModelEntry reference = new ModelFactory.ModelEntry(faces(0xFF102030, 0x21), base);
        for (ModelFactory.ModelSemanticKey different : differences) {
            assertNotEquals(reference, new ModelFactory.ModelEntry(faces(0xFF102030, 0x21), different));
        }
    }

    @Test
    void colourAndDepthRemainPartOfTheExactBakedContentKey() {
        ModelFactory.ModelSemanticKey semantics = baseSemantics();
        ModelFactory.ModelEntry reference = new ModelFactory.ModelEntry(faces(0xFF102030, 0x21), semantics);

        assertNotEquals(reference, new ModelFactory.ModelEntry(faces(0xFF102031, 0x21), semantics));
        assertNotEquals(reference, new ModelFactory.ModelEntry(faces(0xFF102030, 0x22), semantics));
    }

    @Test
    void capacityDiagnosticsStartAtSeventyFivePercentAndFollowAllocation() throws IOException {
        assertEquals(49_152, ModelFactory.MODEL_CAPACITY_WARNING_START);
        assertEquals(4_096, ModelFactory.MODEL_CAPACITY_WARNING_STEP);

        String source = Files.readString(Path.of("src/main/java/me/cortex/voxy/forge/ModelFactory.java"));
        int allocation = source.indexOf("this.nextModelId++;");
        int highWater = source.indexOf("this.recordModelCapacityHighWater();", allocation);
        assertTrue(allocation >= 0 && highWater > allocation);
        assertTrue(source.contains("Original Voxy model capacity high-water:"));
        assertTrue(source.contains("Original Voxy model summary:"));
        assertTrue(source.contains("deduplicatedMappings={}"));
    }

    private static ModelFactory.ModelSemanticKey baseSemantics() {
        return new ModelFactory.ModelSemanticKey(
                7,
                0x0D7F_0102_0408L,
                0b1111,
                101,
                0xFF456789,
                Blocks.GRASS_BLOCK.defaultBlockState(),
                0b11,
                ForgeOriginalVoxyModelLayer.SOLID,
                new ModelFactory.FaceDataKey(1, 2, 3, 4, 5, 6));
    }

    private static ColourDepthTextureData[] faces(int colour, int depth) {
        ColourDepthTextureData[] faces = new ColourDepthTextureData[ForgeModelAtlasLayout.FACE_COUNT];
        for (int face = 0; face < faces.length; face++) {
            int[] colours = new int[ForgeModelAtlasLayout.MODEL_TEXTURE_SIZE * ForgeModelAtlasLayout.MODEL_TEXTURE_SIZE];
            int[] depths = new int[colours.length];
            colours[face] = colour + face;
            depths[face] = depth + face;
            faces[face] = new ColourDepthTextureData(
                    colours,
                    depths,
                    ForgeModelAtlasLayout.MODEL_TEXTURE_SIZE,
                    ForgeModelAtlasLayout.MODEL_TEXTURE_SIZE);
        }
        return faces;
    }
}
