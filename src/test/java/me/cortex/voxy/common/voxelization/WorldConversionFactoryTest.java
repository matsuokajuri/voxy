package me.cortex.voxy.common.voxelization;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import me.cortex.voxy.common.config.IMappingStorage;
import me.cortex.voxy.common.world.other.Mapper;
import net.minecraft.SharedConstants;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.Holder;
import net.minecraft.core.IdMapper;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.SimpleBitStorage;
import net.minecraft.util.ZeroBitStorage;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.GlobalPalette;
import net.minecraft.world.level.chunk.HashMapPalette;
import net.minecraft.world.level.chunk.LinearPalette;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.chunk.SingleValuePalette;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldConversionFactoryTest {
    private static final Holder<Biome> TEST_BIOME = Holder.direct(null);
    private static IdMapper<BlockState> testBlockRegistry;
    private static List<BlockState> testBlockStates;

    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();

        testBlockRegistry = new IdMapper<>();
        testBlockStates = new ArrayList<>();
        addTestBlockState(Blocks.AIR.defaultBlockState());
        for (Block block : BuiltInRegistries.BLOCK) {
            for (BlockState state : block.getStateDefinition().getPossibleStates()) {
                if (!state.isAir()) {
                    addTestBlockState(state);
                }
            }
        }
        assertTrue(testBlockStates.size() >= 300,
                "Vanilla block definitions must provide enough states for global-palette coverage");
    }

    @Test
    void biomeIndexPackingMatchesSectionCoordinates() {
        for (int index = 0; index < 4096; index++) {
            int x = index & 0xF;
            int y = (index >>> 8) & 0xF;
            int z = (index >>> 4) & 0xF;
            int expected = ((y >>> 2) << 4) | ((z >>> 2) << 2) | (x >>> 2);
            assertEquals(expected, WorldConversionFactory.biomeIndexForLinearIndex(index));
        }
    }

    @Test
    void zeroBitStorageOutputMatchesCoordinateLookup() {
        PalettedContainer<BlockState> blocks = newBlockContainer();
        assertInstanceOf(SingleValuePalette.class, blocks.data.palette);
        assertInstanceOf(ZeroBitStorage.class, blocks.data.storage);
        assertConversionMatchesCoordinateLookup(blocks);
    }

    @Test
    void nonAirZeroBitStorageOutputMatchesCoordinateLookup() {
        PalettedContainer<BlockState> blocks = newBlockContainer(Blocks.STONE.defaultBlockState());
        assertInstanceOf(SingleValuePalette.class, blocks.data.palette);
        assertInstanceOf(ZeroBitStorage.class, blocks.data.storage);
        assertConversionMatchesCoordinateLookup(blocks);
    }

    @Test
    void linearPalettePackedOutputMatchesCoordinateLookup() {
        PalettedContainer<BlockState> blocks = newBlockContainer();
        for (int index = 0; index < 4096; index++) {
            if ((index & 3) == 0) {
                setLinear(blocks, index, Blocks.STONE.defaultBlockState());
            }
        }
        assertInstanceOf(LinearPalette.class, blocks.data.palette);
        assertInstanceOf(SimpleBitStorage.class, blocks.data.storage);
        assertConversionMatchesCoordinateLookup(blocks);
    }

    @Test
    void hashMapPalettePackedOutputMatchesCoordinateLookup() {
        PalettedContainer<BlockState> blocks = newBlockContainer();
        List<BlockState> states = distinctStates(24);
        for (int index = 0; index < 4096; index++) {
            setLinear(blocks, index, states.get(index % states.size()));
        }
        assertInstanceOf(HashMapPalette.class, blocks.data.palette);
        assertInstanceOf(SimpleBitStorage.class, blocks.data.storage);
        assertConversionMatchesCoordinateLookup(blocks);
    }

    @Test
    void globalPalettePackedOutputMatchesCoordinateLookup() {
        PalettedContainer<BlockState> blocks = newBlockContainer();
        List<BlockState> states = distinctStates(300);
        assertEquals(300, states.size(), "Vanilla registry must provide enough distinct states for global-palette coverage");
        for (int index = 0; index < 4096; index++) {
            setLinear(blocks, index, states.get(index % states.size()));
        }
        assertInstanceOf(GlobalPalette.class, blocks.data.palette);
        assertInstanceOf(SimpleBitStorage.class, blocks.data.storage);
        assertConversionMatchesCoordinateLookup(blocks);
    }

    @Test
    void localBlockMappingCacheIsReusedPerMapper() {
        PalettedContainer<BlockState> blocks = newBlockContainer();
        for (int index = 0; index < 4096; index += 5) {
            setLinear(blocks, index, Blocks.STONE.defaultBlockState());
        }

        CountingMapper firstMapper = new CountingMapper();
        convert(blocks, firstMapper);
        int callsAfterFirstConversion = firstMapper.blockLookups;
        assertTrue(callsAfterFirstConversion > 0);

        convert(blocks, firstMapper);
        assertEquals(callsAfterFirstConversion, firstMapper.blockLookups,
                "A second conversion on the same thread and Mapper must reuse the local palette mapping");

        CountingMapper secondMapper = new CountingMapper();
        convert(blocks, secondMapper);
        assertTrue(secondMapper.blockLookups > 0,
                "The local mapping cache must not leak block IDs between Mapper instances");
    }

    private static void assertConversionMatchesCoordinateLookup(PalettedContainer<BlockState> blocks) {
        CountingMapper mapper = new CountingMapper();
        VoxelizedSection actual = convert(blocks, mapper);
        long[] expected = new long[4096];
        int nonAirCount = 0;
        for (int index = 0; index < 4096; index++) {
            int x = index & 0xF;
            int y = (index >>> 8) & 0xF;
            int z = (index >>> 4) & 0xF;
            int blockId = mapper.getIdForBlockState(blocks.get(x, y, z));
            byte light = testLight(x, y, z);
            expected[index] = Mapper.composeMappingId(light, blockId, CountingMapper.BIOME_ID);
            nonAirCount += blockId == 0 ? 0 : 1;
        }

        long[] actualLevelZero = new long[4096];
        System.arraycopy(actual.section, 0, actualLevelZero, 0, actualLevelZero.length);
        assertArrayEquals(expected, actualLevelZero);
        assertEquals(nonAirCount, actual.lvl0NonAirCount);
    }

    private static VoxelizedSection convert(PalettedContainer<BlockState> blocks, CountingMapper mapper) {
        VoxelizedSection section = VoxelizedSection.createEmpty();
        WorldConversionFactory.convert(section, mapper, blocks, newBiomeContainer(), WorldConversionFactoryTest::testLight);
        return section;
    }

    private static byte testLight(int x, int y, int z) {
        return (byte) ((x * 11 + y * 7 + z * 3) & 0xFF);
    }

    private static PalettedContainer<BlockState> newBlockContainer() {
        return newBlockContainer(Blocks.AIR.defaultBlockState());
    }

    private static PalettedContainer<BlockState> newBlockContainer(BlockState defaultState) {
        return new PalettedContainer<>(testBlockRegistry, defaultState,
                PalettedContainer.Strategy.SECTION_STATES);
    }

    private static PalettedContainer<Holder<Biome>> newBiomeContainer() {
        IdMapper<Holder<Biome>> biomes = new IdMapper<>(1);
        biomes.add(TEST_BIOME);
        return new PalettedContainer<>(biomes, TEST_BIOME, PalettedContainer.Strategy.SECTION_BIOMES);
    }

    private static List<BlockState> distinctStates(int count) {
        int firstNonAir = testBlockStates.get(0).isAir() ? 1 : 0;
        return testBlockStates.subList(firstNonAir, Math.min(testBlockStates.size(), firstNonAir + count));
    }

    private static void addTestBlockState(BlockState state) {
        testBlockRegistry.add(state);
        testBlockStates.add(state);
    }

    private static void setLinear(PalettedContainer<BlockState> blocks, int index, BlockState state) {
        blocks.set(index & 0xF, (index >>> 8) & 0xF, (index >>> 4) & 0xF, state);
    }

    private static final class CountingMapper extends Mapper {
        private static final int BIOME_ID = 7;
        private final IdentityHashMap<BlockState, Integer> ids = new IdentityHashMap<>();
        private int nextId = 1;
        private int blockLookups;

        private CountingMapper() {
            super(new EmptyMappingStorage());
        }

        @Override
        public int getIdForBlockState(BlockState state) {
            this.blockLookups++;
            if (state.isAir()) {
                return 0;
            }
            return this.ids.computeIfAbsent(state, ignored -> this.nextId++);
        }

        @Override
        public int getIdForBiome(Holder<Biome> biome) {
            return BIOME_ID;
        }
    }

    private static final class EmptyMappingStorage implements IMappingStorage {
        @Override
        public void putIdMapping(int id, ByteBuffer data) {
        }

        @Override
        public Int2ObjectOpenHashMap<byte[]> getIdMappingsData() {
            return new Int2ObjectOpenHashMap<>();
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
        }
    }
}
