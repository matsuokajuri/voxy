package me.cortex.voxy.common.world.other;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import me.cortex.voxy.common.config.IMappingStorage;
import net.minecraft.SharedConstants;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.ByteArrayOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MapperSnapshotSafetyTest {
    private static final int BLOCK_STATE_TYPE = 1;
    private static final int BIOME_TYPE = 2;

    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void blockSnapshotValidationFailureDoesNotStrandLock() throws Exception {
        Mapper mapper = new Mapper(new RecordingMappingStorage());
        ObjectArrayList<Mapper.StateEntry> entries = blockEntries(mapper);
        entries.add(new Mapper.StateEntry(99, Blocks.STONE.defaultBlockState()));

        ReentrantLock lock = mapperLock(mapper, "blockLock");
        try {
            assertThrows(IllegalStateException.class, mapper::getStateEntries);
            assertTrue(canAcquireFromAnotherThread(lock));
        } finally {
            while (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    @Test
    void biomeSnapshotValidationFailureDoesNotStrandLock() throws Exception {
        Mapper mapper = new Mapper(new RecordingMappingStorage());
        ObjectArrayList<Mapper.BiomeEntry> entries = biomeEntries(mapper);
        entries.add(new Mapper.BiomeEntry(99, "minecraft:plains"));

        ReentrantLock lock = mapperLock(mapper, "biomeLock");
        try {
            assertThrows(IllegalStateException.class, mapper::getBiomeEntries);
            assertTrue(canAcquireFromAnotherThread(lock));
        } finally {
            while (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    @Test
    void invalidStoredBlockStateUsesStableAirPlaceholderWithoutOverwritingBytes() throws Exception {
        byte[] invalidMapping = invalidBlockStateMapping(1, "missing_forxy_test:block");
        for (int attempt = 0; attempt < 8; attempt++) {
            RecordingMappingStorage storage = new RecordingMappingStorage();
            storage.seed(mappingKey(BLOCK_STATE_TYPE, 1), invalidMapping);

            Mapper mapper = new Mapper(storage);
            Mapper.StateEntry[] entries = mapper.getStateEntries();

            assertEquals(2, entries.length);
            assertEquals(0, entries[0].id);
            assertTrue(entries[0].state.isAir());
            assertEquals(1, entries[1].id);
            assertTrue(entries[1].state.isAir());
            assertArrayEquals(invalidMapping, storage.mapping(mappingKey(BLOCK_STATE_TYPE, 1)));
        }
    }

    @Test
    @Timeout(20)
    void concurrentBlockAndBiomeSnapshotsRemainDensePrefixes() throws Exception {
        RecordingMappingStorage storage = new RecordingMappingStorage();
        Mapper mapper = new Mapper(storage);
        List<BlockState> blockStates = distinctNonAirStates(160);
        List<String> biomes = new ArrayList<>();
        for (int index = 0; index < 32; index++) {
            biomes.add("forxy_test:biome_" + index);
        }

        ExecutorService executor = Executors.newFixedThreadPool(10);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> work = new ArrayList<>();
        try {
            for (int worker = 0; worker < 8; worker++) {
                int offset = worker;
                work.add(executor.submit(() -> {
                    await(start);
                    for (int index = offset; index < blockStates.size(); index += 8) {
                        mapper.getIdForBlockState(blockStates.get(index));
                    }
                    for (int index = offset; index < biomes.size(); index += 8) {
                        registerBiome(mapper, biomes.get(index));
                    }
                }));
            }
            work.add(executor.submit(() -> {
                await(start);
                for (int iteration = 0; iteration < 500; iteration++) {
                    assertDense(mapper.getStateEntries());
                    assertDense(mapper.getBiomeEntries());
                }
            }));

            start.countDown();
            for (Future<?> task : work) {
                task.get(20, TimeUnit.SECONDS);
            }
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        }

        Mapper.StateEntry[] stateSnapshot = mapper.getStateEntries();
        Mapper.BiomeEntry[] biomeSnapshot = mapper.getBiomeEntries();
        assertDense(stateSnapshot);
        assertDense(biomeSnapshot);
        assertEquals(blockStates.size() + 1, stateSnapshot.length);
        assertEquals(biomes.size(), biomeSnapshot.length);
    }

    @Test
    @Timeout(20)
    void biomeCallbackAndSnapshotPartitionConcurrentRegistrationsExactlyOnce() throws Exception {
        Mapper mapper = new Mapper(new RecordingMappingStorage());
        for (int index = 0; index < 16; index++) {
            registerBiome(mapper, "forxy_test:preexisting_" + index);
        }

        Map<Integer, Integer> callbackCounts = new ConcurrentHashMap<>();
        ExecutorService executor = Executors.newFixedThreadPool(8);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> work = new ArrayList<>();
        try {
            for (int worker = 0; worker < 8; worker++) {
                int offset = worker;
                work.add(executor.submit(() -> {
                    await(start);
                    for (int index = offset; index < 256; index += 8) {
                        registerBiome(mapper, "forxy_test:concurrent_" + index);
                    }
                }));
            }

            start.countDown();
            Mapper.BiomeEntry[] initial = mapper.setBiomeCallbackAndGetSnapshot(
                    entry -> callbackCounts.merge(entry.id, 1, Integer::sum));
            for (Future<?> task : work) {
                task.get(20, TimeUnit.SECONDS);
            }
            mapper.setBiomeCallback(null);

            Map<Integer, Integer> deliveryCounts = new ConcurrentHashMap<>(callbackCounts);
            for (Mapper.BiomeEntry entry : initial) {
                deliveryCounts.merge(entry.id, 1, Integer::sum);
            }

            Mapper.BiomeEntry[] complete = mapper.getBiomeEntries();
            assertEquals(272, complete.length);
            for (Mapper.BiomeEntry entry : complete) {
                assertEquals(1, deliveryCounts.getOrDefault(entry.id, 0), "biome id " + entry.id);
            }
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    @Test
    void registrationPersistsBeforePublishingCallbacks() {
        RecordingMappingStorage storage = new RecordingMappingStorage();
        Mapper mapper = new Mapper(storage);

        mapper.setStateCallback(entry -> assertTrue(storage.hasMapping(mappingKey(BLOCK_STATE_TYPE, entry.id))));
        mapper.getIdForBlockState(Blocks.STONE.defaultBlockState());

        mapper.setBiomeCallback(entry -> assertTrue(storage.hasMapping(mappingKey(BIOME_TYPE, entry.id))));
        registerBiome(mapper, "forxy_test:persistence_order");
    }

    private static List<BlockState> distinctNonAirStates(int count) {
        List<BlockState> result = new ArrayList<>(count);
        for (Block block : BuiltInRegistries.BLOCK) {
            for (BlockState state : block.getStateDefinition().getPossibleStates()) {
                if (!state.isAir()) {
                    result.add(state);
                    if (result.size() == count) {
                        return result;
                    }
                }
            }
        }
        throw new IllegalStateException("Not enough bootstrapped block states");
    }

    private static void assertDense(Mapper.StateEntry[] entries) {
        for (int index = 0; index < entries.length; index++) {
            assertEquals(index, entries[index].id);
        }
    }

    private static void assertDense(Mapper.BiomeEntry[] entries) {
        for (int index = 0; index < entries.length; index++) {
            assertEquals(index, entries[index].id);
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            assertTrue(latch.await(5, TimeUnit.SECONDS));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(exception);
        }
    }

    private static Mapper.BiomeEntry registerBiome(Mapper mapper, String biome) {
        try {
            Method method = Mapper.class.getDeclaredMethod("registerNewBiome", String.class);
            method.setAccessible(true);
            return (Mapper.BiomeEntry) method.invoke(mapper, biome);
        } catch (ReflectiveOperationException exception) {
            throw new RuntimeException(exception);
        }
    }

    private static boolean canAcquireFromAnotherThread(ReentrantLock lock) throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "Mapper lock probe");
            thread.setDaemon(true);
            return thread;
        });
        try {
            return executor.submit(() -> {
                boolean acquired = lock.tryLock();
                if (acquired) {
                    lock.unlock();
                }
                return acquired;
            }).get(2, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }
    }

    @SuppressWarnings("unchecked")
    private static ObjectArrayList<Mapper.StateEntry> blockEntries(Mapper mapper) throws Exception {
        Field field = Mapper.class.getDeclaredField("blockId2stateEntry");
        field.setAccessible(true);
        return (ObjectArrayList<Mapper.StateEntry>) field.get(mapper);
    }

    @SuppressWarnings("unchecked")
    private static ObjectArrayList<Mapper.BiomeEntry> biomeEntries(Mapper mapper) throws Exception {
        Field field = Mapper.class.getDeclaredField("biomeId2biomeEntry");
        field.setAccessible(true);
        return (ObjectArrayList<Mapper.BiomeEntry>) field.get(mapper);
    }

    private static ReentrantLock mapperLock(Mapper mapper, String name) throws Exception {
        Field field = Mapper.class.getDeclaredField(name);
        field.setAccessible(true);
        return (ReentrantLock) field.get(mapper);
    }

    private static byte[] invalidBlockStateMapping(int id, String blockName) throws Exception {
        CompoundTag root = new CompoundTag();
        root.putInt("id", id);
        CompoundTag blockState = new CompoundTag();
        blockState.putString("Name", blockName);
        root.put("block_state", blockState);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        NbtIo.writeCompressed(root, output);
        return output.toByteArray();
    }

    private static int mappingKey(int type, int id) {
        return id | (type << 30);
    }

    private static final class RecordingMappingStorage implements IMappingStorage {
        private final Map<Integer, byte[]> mappings = new ConcurrentHashMap<>();

        void seed(int id, byte[] data) {
            this.mappings.put(id, Arrays.copyOf(data, data.length));
        }

        byte[] mapping(int id) {
            byte[] data = this.mappings.get(id);
            return data == null ? null : Arrays.copyOf(data, data.length);
        }

        boolean hasMapping(int id) {
            return this.mappings.containsKey(id);
        }

        @Override
        public void putIdMapping(int id, ByteBuffer data) {
            ByteBuffer copy = data.duplicate();
            byte[] bytes = new byte[copy.remaining()];
            copy.get(bytes);
            this.mappings.put(id, bytes);
        }

        @Override
        public Int2ObjectOpenHashMap<byte[]> getIdMappingsData() {
            Int2ObjectOpenHashMap<byte[]> result = new Int2ObjectOpenHashMap<>();
            this.mappings.forEach((key, value) -> result.put(key, Arrays.copyOf(value, value.length)));
            return result;
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
        }
    }
}
