package me.cortex.voxy.common.world.other;

import me.cortex.voxy.common.config.storage.inmemory.MemoryStorageBackend;
import me.cortex.voxy.common.voxelization.VoxelizedSection;
import me.cortex.voxy.common.voxelization.WorldVoxilizedSectionMipper;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MipperLevelAwareTest {
    private MemoryStorageBackend backend;
    private Mapper mapper;

    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @BeforeEach
    void createMapper() {
        this.backend = new MemoryStorageBackend();
        this.mapper = new Mapper(this.backend);
    }

    @AfterEach
    void closeBackend() {
        this.backend.close();
    }

    @Test
    void opaqueCutoutTranslucentFluidEmissiveAndThinFixturesHaveExplicitPriorities() {
        long air = Mapper.airWithLight(0);
        long stone = mapping(Blocks.STONE.defaultBlockState());
        long leaves = mapping(Blocks.OAK_LEAVES.defaultBlockState());
        long glass = mapping(Blocks.GLASS.defaultBlockState());
        long water = mapping(Blocks.WATER.defaultBlockState());
        long glowstone = mapping(Blocks.GLOWSTONE.defaultBlockState());
        long ironBars = mapping(Blocks.IRON_BARS.defaultBlockState());

        assertBlock(stone, mip(1, stone, glass, air, air, air, air, air, air));
        assertBlock(leaves, mip(1, leaves, glass, air, air, air, air, air, air));
        assertBlock(glass, mip(1, stone, glass, glass, air, air, air, air, air));
        assertBlock(water, mip(1, stone, water, water, air, air, air, air, air));
        assertBlock(glowstone, mip(1, stone, glowstone, air, air, air, air, air, air));
        assertBlock(ironBars, mip(1, ironBars, air, air, air, air, air, air, air));
    }

    @Test
    void sparseDetailSurvivesNearLevelsWithoutExpandingForever() {
        long air = Mapper.airWithLight(0);
        long stone = mapping(Blocks.STONE.defaultBlockState());
        long ironBars = mapping(Blocks.IRON_BARS.defaultBlockState());
        long glowstone = mapping(Blocks.GLOWSTONE.defaultBlockState());

        assertBlock(stone, mip(1, stone, air, air, air, air, air, air, air));
        assertBlock(stone, mip(2, stone, air, air, air, air, air, air, air));
        assertBlock(air, mip(3, stone, air, air, air, air, air, air, air));

        assertBlock(ironBars, mip(2, ironBars, air, air, air, air, air, air, air));
        assertBlock(air, mip(3, ironBars, air, air, air, air, air, air, air));

        assertBlock(glowstone, mip(4, glowstone, air, air, air, air, air, air, air));
        assertBlock(air, mip(5, glowstone, air, air, air, air, air, air, air));
    }

    @Test
    void mixedLightDoesNotLeakAcrossTheSelectedMaterial() {
        long airBlockLight = Mapper.airWithLight(0xD0);
        long airSkyLight = Mapper.airWithLight(0x0F);
        long darkAir = Mapper.airWithLight(0);
        long airResult = mip(1,
                airBlockLight, airSkyLight, darkAir, darkAir,
                darkAir, darkAir, darkAir, darkAir);
        assertEquals(0xD2, Mapper.getLightId(airResult));

        int stoneId = this.mapper.getIdForBlockState(Blocks.STONE.defaultBlockState());
        long dimStone = Mapper.composeMappingId((byte) 0x03, stoneId, 0);
        long skyStone = Mapper.composeMappingId((byte) 0x2F, stoneId, 0);
        long brightAir = Mapper.airWithLight(0xE0);
        long solidResult = mip(3,
                dimStone, skyStone, brightAir, darkAir,
                darkAir, darkAir, darkAir, darkAir);
        assertEquals(stoneId, Mapper.getBlockId(solidResult));
        assertEquals(0x19, Mapper.getLightId(solidResult));
    }

    @Test
    void materialAndBiomeSelectionDoNotDependOnInputTraversalOrder() {
        int stoneId = this.mapper.getIdForBlockState(Blocks.STONE.defaultBlockState());
        int glassId = this.mapper.getIdForBlockState(Blocks.GLASS.defaultBlockState());
        long[] inputs = {
                Mapper.composeMappingId((byte) 0x11, stoneId, 7),
                Mapper.composeMappingId((byte) 0x22, glassId, 4),
                Mapper.composeMappingId((byte) 0x33, stoneId, 3),
                Mapper.composeMappingId((byte) 0x44, glassId, 4),
                Mapper.airWithLight(0x05),
                Mapper.airWithLight(0x06),
                Mapper.airWithLight(0x07),
                Mapper.airWithLight(0x08)
        };
        long expected = mip(1, inputs);
        assertEquals(stoneId, Mapper.getBlockId(expected));
        assertEquals(3, Mapper.getBiomeId(expected));

        Random random = new Random(0x71E5A11L);
        for (int iteration = 0; iteration < 256; iteration++) {
            for (int index = inputs.length - 1; index > 0; index--) {
                int swap = random.nextInt(index + 1);
                long temporary = inputs[index];
                inputs[index] = inputs[swap];
                inputs[swap] = temporary;
            }
            assertEquals(expected, mip(1, inputs));
        }
    }

    @Test
    void sectionMipperPassesEachTargetLevelToTheSelector() {
        VoxelizedSection section = VoxelizedSection.createEmpty();
        section.section[0] = mapping(Blocks.STONE.defaultBlockState());

        WorldVoxilizedSectionMipper.mipSection(section, this.mapper);

        assertEquals(
                Mapper.getBlockId(section.section[0]),
                Mapper.getBlockId(section.section[VoxelizedSection.getBaseIndexForLevel(1)]));
        assertEquals(
                Mapper.getBlockId(section.section[0]),
                Mapper.getBlockId(section.section[VoxelizedSection.getBaseIndexForLevel(2)]));
        assertEquals(0, Mapper.getBlockId(section.section[VoxelizedSection.getBaseIndexForLevel(3)]));
        assertEquals(0, Mapper.getBlockId(section.section[VoxelizedSection.getBaseIndexForLevel(4)]));
    }

    @Test
    void targetLevelMustBePositive() {
        long air = Mapper.airWithLight(0);
        assertThrows(
                IllegalArgumentException.class,
                () -> mip(0, air, air, air, air, air, air, air, air));
    }

    private long mapping(BlockState state) {
        return Mapper.composeMappingId((byte) 0, this.mapper.getIdForBlockState(state), 0);
    }

    private long mip(int targetLevel, long... inputs) {
        assertEquals(8, inputs.length);
        return Mipper.mip(
                inputs[0], inputs[1], inputs[2], inputs[3],
                inputs[4], inputs[5], inputs[6], inputs[7],
                this.mapper, targetLevel);
    }

    private static void assertBlock(long expected, long actual) {
        assertEquals(Mapper.getBlockId(expected), Mapper.getBlockId(actual));
    }
}
