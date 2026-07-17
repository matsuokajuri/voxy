package me.cortex.voxy.forge;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import me.cortex.voxy.common.config.storage.StorageBackend;
import me.cortex.voxy.common.util.MemoryBuffer;
import me.cortex.voxy.common.world.WorldEngine;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.io.TempDir;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.params.ScanParams;
import redis.clients.jedis.resps.ScanResult;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.LongConsumer;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OptionalStorageIterationTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void emptyOptionalBackendsProduceNoPositions() throws Exception {
        Path lmdbPath = Files.createDirectory(this.temporaryDirectory.resolve("lmdb-empty"));
        LMDBStorageBackend lmdb = new LMDBStorageBackend(lmdbPath.toString());
        try {
            assertTrue(positions(lmdb, -1).isEmpty());
            assertTrue(positions(lmdb, 0).isEmpty());
        } finally {
            lmdb.close();
        }

        ReadonlyCachingLayer readonly = new ReadonlyCachingLayer(
                new RecordingStorageBackend(), new RecordingStorageBackend());
        try {
            assertTrue(positions(readonly, -1).isEmpty());
        } finally {
            readonly.close();
        }

        FakeJedis jedis = new FakeJedis();
        jedis.page("0", "0");
        RedisStorageBackend redis = new RedisStorageBackend(
                new FakeJedisPool(jedis), "forxy_empty_test:", null, null);
        try {
            assertTrue(positions(redis, -1).isEmpty());
        } finally {
            redis.close();
        }
    }

    @Test
    @Timeout(20)
    void lmdbIteratesMixedLevelsPropagatesCallbackFailuresAndSurvivesRestart() throws Exception {
        Path databasePath = Files.createDirectory(this.temporaryDirectory.resolve("lmdb"));
        long levelZero = WorldEngine.getWorldSectionId(0, 1, 2, 3);
        long levelTwoA = WorldEngine.getWorldSectionId(2, -4, 5, 6);
        long levelTwoB = WorldEngine.getWorldSectionId(2, 7, -8, 9);
        long levelFive = WorldEngine.getWorldSectionId(5, 10, 11, -12);
        Set<Long> expected = Set.of(levelZero, levelTwoA, levelTwoB, levelFive);

        LMDBStorageBackend backend = new LMDBStorageBackend(databasePath.toString());
        try {
            MemoryBuffer data = new MemoryBuffer(8).zero();
            try {
                for (long key : expected) {
                    backend.setSectionData(key, data);
                }
            } finally {
                data.free();
            }

            assertEquals(expected, positions(backend, -1));
            assertEquals(Set.of(levelTwoA, levelTwoB), positions(backend, 2));
            assertTrue(positions(backend, 7).isEmpty());

            RuntimeException callbackFailure = new RuntimeException("iteration-callback-failure");
            RuntimeException thrown = assertThrows(
                    RuntimeException.class,
                    () -> backend.iteratePositions(-1, key -> {
                        throw callbackFailure;
                    }));
            assertSame(callbackFailure, thrown);
            assertEquals(expected, positions(backend, -1));
            backend.flush();
        } finally {
            backend.close();
        }

        LMDBStorageBackend reopened = new LMDBStorageBackend(databasePath.toString());
        try {
            assertEquals(expected, positions(reopened, -1));
            assertEquals(Set.of(levelTwoA, levelTwoB), positions(reopened, 2));
        } finally {
            reopened.close();
        }
    }

    @Test
    void readonlyCacheIteratesDeduplicatedUnionAndReplicatesMappings() {
        RecordingStorageBackend cache = new RecordingStorageBackend();
        RecordingStorageBackend source = new RecordingStorageBackend();
        long cacheOnly = WorldEngine.getWorldSectionId(1, 1, 0, 0);
        long shared = WorldEngine.getWorldSectionId(1, 2, 0, 0);
        long sourceOnly = WorldEngine.getWorldSectionId(1, 3, 0, 0);
        long otherLevel = WorldEngine.getWorldSectionId(3, 4, 0, 0);
        cache.positions.add(cacheOnly);
        cache.positions.add(shared);
        source.positions.add(shared);
        source.positions.add(sourceOnly);
        source.positions.add(otherLevel);

        cache.mapping(20, new byte[]{2, 0});
        source.mapping(10, new byte[]{1, 0});
        source.mapping(20, new byte[]{2, 0});

        ReadonlyCachingLayer layer = new ReadonlyCachingLayer(cache, source);
        assertEquals(Set.of(cacheOnly, shared, sourceOnly, otherLevel), positions(layer, -1));
        assertEquals(Set.of(cacheOnly, shared, sourceOnly), positions(layer, 1));
        RuntimeException callbackFailure = new RuntimeException("readonly-callback-failure");
        RuntimeException thrown = assertThrows(
                RuntimeException.class,
                () -> layer.iteratePositions(-1, key -> {
                    throw callbackFailure;
                }));
        assertSame(callbackFailure, thrown);
        assertEquals(Set.of(cacheOnly, shared, sourceOnly, otherLevel), positions(layer, -1));

        Int2ObjectOpenHashMap<byte[]> mappings = layer.getIdMappingsData();
        assertEquals(Set.of(10, 20), mappings.keySet());
        assertArrayEquals(new byte[]{1, 0}, mappings.get(10));
        assertArrayEquals(new byte[]{2, 0}, mappings.get(20));
        assertArrayEquals(new byte[]{1, 0}, cache.getIdMappingsData().get(10));
        assertEquals(1, cache.mappingWrites.get());
        assertEquals(0, source.mappingWrites.get());

        layer.flush();
        assertEquals(1, cache.flushes.get());
        assertEquals(1, source.flushes.get());
        assertEquals(0, cache.closes.get());
        assertEquals(0, source.closes.get());

        layer.close();
        assertEquals(1, cache.closes.get());
        assertEquals(1, source.closes.get());
    }

    @Test
    void readonlyCacheRejectsConflictingMappingBytesWithoutMutatingSource() {
        RecordingStorageBackend cache = new RecordingStorageBackend();
        RecordingStorageBackend source = new RecordingStorageBackend();
        cache.mapping(7, new byte[]{1});
        source.mapping(7, new byte[]{2});

        ReadonlyCachingLayer layer = new ReadonlyCachingLayer(cache, source);
        assertThrows(IllegalStateException.class, layer::getIdMappingsData);
        assertArrayEquals(new byte[]{1}, cache.getIdMappingsData().get(7));
        assertArrayEquals(new byte[]{2}, source.getIdMappingsData().get(7));
        assertEquals(0, source.mappingWrites.get());
        layer.close();
    }

    @Test
    @Timeout(30)
    void rocksDbReferenceContractAndLmdbHandleLargeMixedLevelSets() throws Exception {
        Set<Long> keys = new LinkedHashSet<>();
        Set<Long> levelThree = new LinkedHashSet<>();
        for (int index = 0; index < 1024; index++) {
            int level = index & 3;
            long key = WorldEngine.getWorldSectionId(level, index, index >> 2, -index);
            keys.add(key);
            if (level == 3) {
                levelThree.add(key);
            }
        }

        Path rocksPath = Files.createDirectory(this.temporaryDirectory.resolve("rocks-large"));
        RocksDBStorageBackend rocks = new RocksDBStorageBackend(rocksPath.toString());
        try {
            storeKeys(rocks, keys);
            assertEquals(keys, positions(rocks, -1));
            assertEquals(levelThree, positions(rocks, 3));
            RuntimeException callbackFailure = new RuntimeException("rocks-callback-failure");
            assertSame(
                    callbackFailure,
                    assertThrows(RuntimeException.class, () -> rocks.iteratePositions(-1, key -> {
                        throw callbackFailure;
                    })));
            assertEquals(keys, positions(rocks, -1));
            rocks.flush();
        } finally {
            rocks.close();
        }
        RocksDBStorageBackend reopenedRocks = new RocksDBStorageBackend(rocksPath.toString());
        try {
            assertEquals(keys, positions(reopenedRocks, -1));
        } finally {
            reopenedRocks.close();
        }

        Path lmdbPath = Files.createDirectory(this.temporaryDirectory.resolve("lmdb-large"));
        LMDBStorageBackend lmdb = new LMDBStorageBackend(lmdbPath.toString());
        try {
            storeKeys(lmdb, keys);
            assertEquals(keys, positions(lmdb, -1));
            assertEquals(levelThree, positions(lmdb, 3));
            lmdb.flush();
        } finally {
            lmdb.close();
        }
        LMDBStorageBackend reopenedLmdb = new LMDBStorageBackend(lmdbPath.toString());
        try {
            assertEquals(keys, positions(reopenedLmdb, -1));
        } finally {
            reopenedLmdb.close();
        }
    }

    @Test
    void readonlyAndRedisLargeUnionsRemainCompleteAndDeduplicated() {
        RecordingStorageBackend cache = new RecordingStorageBackend();
        RecordingStorageBackend source = new RecordingStorageBackend();
        Set<Long> expectedReadonly = new LinkedHashSet<>();
        for (int index = 0; index < 4096; index++) {
            long key = WorldEngine.getWorldSectionId(2, index, 0, 0);
            cache.positions.add(key);
            expectedReadonly.add(key);
        }
        for (int index = 2048; index < 6144; index++) {
            long key = WorldEngine.getWorldSectionId(2, index, 0, 0);
            source.positions.add(key);
            expectedReadonly.add(key);
        }
        ReadonlyCachingLayer layer = new ReadonlyCachingLayer(cache, source);
        try {
            assertEquals(expectedReadonly, positions(layer, -1));
        } finally {
            layer.close();
        }

        FakeJedis jedis = new FakeJedis();
        Set<Long> expectedRedis = new LinkedHashSet<>();
        int pageCount = 16;
        for (int page = 0; page < pageCount; page++) {
            long[] pageKeys = new long[129];
            for (int offset = 0; offset < 128; offset++) {
                long key = WorldEngine.getWorldSectionId(2, page * 128 + offset, 1, 1);
                pageKeys[offset] = key;
                expectedRedis.add(key);
            }
            pageKeys[128] = pageKeys[0];
            String cursor = page == 0 ? "0" : Integer.toString(page);
            String nextCursor = page + 1 == pageCount ? "0" : Integer.toString(page + 1);
            jedis.page(cursor, nextCursor, pageKeys);
        }
        RedisStorageBackend redis = new RedisStorageBackend(
                new FakeJedisPool(jedis), "forxy_large_test:", null, null);
        try {
            assertEquals(expectedRedis, positions(redis, -1));
        } finally {
            redis.close();
        }
        assertEquals(pageCount, jedis.scanCalls.get());
    }

    @Test
    @Timeout(30)
    void readonlyCacheWithLmdbChildrenReplicatesMappingsAndSurvivesRestart() throws Exception {
        Path cachePath = Files.createDirectory(this.temporaryDirectory.resolve("readonly-cache"));
        Path sourcePath = Files.createDirectory(this.temporaryDirectory.resolve("readonly-source"));
        long sourceOnly = WorldEngine.getWorldSectionId(1, 10, 0, 0);
        long cacheOnly = WorldEngine.getWorldSectionId(1, 20, 0, 0);

        LMDBStorageBackend initialSource = new LMDBStorageBackend(sourcePath.toString());
        try {
            storeKeys(initialSource, Set.of(sourceOnly));
            putMapping(initialSource, 1, new byte[]{1, 2, 3});
            initialSource.flush();
        } finally {
            initialSource.close();
        }

        ReadonlyCachingLayer first = new ReadonlyCachingLayer(
                new LMDBStorageBackend(cachePath.toString()),
                new LMDBStorageBackend(sourcePath.toString()));
        try {
            assertEquals(Set.of(sourceOnly), positions(first, -1));
            assertArrayEquals(new byte[]{1, 2, 3}, first.getIdMappingsData().get(1));
            storeKeys(first, Set.of(cacheOnly));
            putMapping(first, 2, new byte[]{4, 5, 6});
            first.flush();
        } finally {
            first.close();
        }

        ReadonlyCachingLayer reopened = new ReadonlyCachingLayer(
                new LMDBStorageBackend(cachePath.toString()),
                new LMDBStorageBackend(sourcePath.toString()));
        try {
            assertEquals(Set.of(sourceOnly, cacheOnly), positions(reopened, -1));
            Int2ObjectOpenHashMap<byte[]> mappings = reopened.getIdMappingsData();
            assertEquals(Set.of(1, 2), mappings.keySet());
            assertArrayEquals(new byte[]{1, 2, 3}, mappings.get(1));
            assertArrayEquals(new byte[]{4, 5, 6}, mappings.get(2));
        } finally {
            reopened.close();
        }

        LMDBStorageBackend sourceAudit = new LMDBStorageBackend(sourcePath.toString());
        try {
            Int2ObjectOpenHashMap<byte[]> sourceMappings = sourceAudit.getIdMappingsData();
            assertEquals(Set.of(1), sourceMappings.keySet());
            assertArrayEquals(new byte[]{1, 2, 3}, sourceMappings.get(1));
            assertEquals(Set.of(sourceOnly), positions(sourceAudit, -1));
        } finally {
            sourceAudit.close();
        }

        LMDBStorageBackend cacheAudit = new LMDBStorageBackend(cachePath.toString());
        try {
            Int2ObjectOpenHashMap<byte[]> cacheMappings = cacheAudit.getIdMappingsData();
            assertEquals(Set.of(1, 2), cacheMappings.keySet());
            assertEquals(Set.of(cacheOnly), positions(cacheAudit, -1));
        } finally {
            cacheAudit.close();
        }
    }

    @Test
    @Tag("redis-real-backend")
    @Timeout(60)
    void realRedisIteratesMixedLevelsAndSurvivesBackendRestart() {
        int port = Integer.parseInt(System.getProperty("voxyRedisTestPort"));
        String prefix = "forxy_round3:" + UUID.randomUUID() + ':';
        Set<Long> expected = new LinkedHashSet<>();
        Set<Long> levelFour = new LinkedHashSet<>();
        for (int index = 0; index < 2048; index++) {
            int level = index & 7;
            long key = WorldEngine.getWorldSectionId(level, index, index >> 3, -index);
            expected.add(key);
            if (level == 4) {
                levelFour.add(key);
            }
        }

        RedisStorageBackend first = new RedisStorageBackend("127.0.0.1", port, prefix);
        try {
            storeKeys(first, expected);
            putMapping(first, 23, new byte[]{9, 8, 7});
            assertEquals(expected, positions(first, -1));
            assertEquals(levelFour, positions(first, 4));
            first.flush();
        } finally {
            first.close();
        }

        RedisStorageBackend reopened = new RedisStorageBackend("127.0.0.1", port, prefix);
        try {
            assertEquals(expected, positions(reopened, -1));
            assertEquals(levelFour, positions(reopened, 4));
            assertArrayEquals(new byte[]{9, 8, 7}, reopened.getIdMappingsData().get(23));
        } finally {
            reopened.close();
        }

        try (Jedis cleanup = new Jedis("127.0.0.1", port)) {
            cleanup.del(
                    (prefix + "world_sections").getBytes(StandardCharsets.UTF_8),
                    (prefix + "id_mappings").getBytes(StandardCharsets.UTF_8));
        }
    }

    @Test
    void redisIterationUsesBoundedHashScanInsteadOfWholeHashMaterialization() throws Exception {
        String source = Files.readString(Path.of("src/main/java/me/cortex/voxy/forge/RedisStorageBackend.java"));
        assertFalse(source.contains("throw new IllegalStateException(\"Not yet implemented\")"));
        assertTrue(source.contains(".hscan("));
        assertTrue(source.contains("new ScanParams().count("));
        assertFalse(source.contains("hkeys("));
    }

    @Test
    void redisHashScanFiltersLevelsDeduplicatesPagesAndPropagatesCallbackFailures() {
        long levelZero = WorldEngine.getWorldSectionId(0, 1, 2, 3);
        long levelTwo = WorldEngine.getWorldSectionId(2, 4, 5, 6);
        long levelFour = WorldEngine.getWorldSectionId(4, 7, 8, 9);
        FakeJedis jedis = new FakeJedis();
        jedis.page("0", "17", levelZero, levelTwo);
        jedis.page("17", "0", levelTwo, levelFour);
        FakeJedisPool pool = new FakeJedisPool(jedis);
        RedisStorageBackend backend = new RedisStorageBackend(pool, "forxy_test:", null, null);
        try {
            assertEquals(Set.of(levelZero, levelTwo, levelFour), positions(backend, -1));
            assertEquals(Set.of(levelTwo), positions(backend, 2));
            assertEquals(4, jedis.scanCalls.get());

            RuntimeException callbackFailure = new RuntimeException("redis-callback-failure");
            RuntimeException thrown = assertThrows(
                    RuntimeException.class,
                    () -> backend.iteratePositions(-1, key -> {
                        throw callbackFailure;
                    }));
            assertSame(callbackFailure, thrown);
            assertEquals(Set.of(levelZero, levelTwo, levelFour), positions(backend, -1));
        } finally {
            backend.close();
        }
        assertTrue(pool.closed);
    }

    @Test
    @Timeout(20)
    void lmdbCloseWaitsForActiveIteration() throws Exception {
        Path databasePath = Files.createDirectory(this.temporaryDirectory.resolve("lmdb-close"));
        LMDBStorageBackend backend = new LMDBStorageBackend(databasePath.toString());
        MemoryBuffer data = new MemoryBuffer(8).zero();
        try {
            backend.setSectionData(WorldEngine.getWorldSectionId(0, 1, 1, 1), data);
        } finally {
            data.free();
        }
        assertCloseWaitsForIteration(backend);
    }

    @Test
    @Timeout(20)
    void readonlyCacheCloseWaitsForActiveIteration() throws Exception {
        RecordingStorageBackend cache = new RecordingStorageBackend();
        cache.positions.add(WorldEngine.getWorldSectionId(0, 2, 2, 2));
        assertCloseWaitsForIteration(new ReadonlyCachingLayer(cache, new RecordingStorageBackend()));
    }

    @Test
    @Timeout(20)
    void redisCloseWaitsForActiveIteration() throws Exception {
        FakeJedis jedis = new FakeJedis();
        jedis.page("0", "0", WorldEngine.getWorldSectionId(0, 3, 3, 3));
        assertCloseWaitsForIteration(
                new RedisStorageBackend(new FakeJedisPool(jedis), "forxy_close_test:", null, null));
    }

    @Test
    @Timeout(20)
    void optionalBackendsRejectReentrantCloseWithoutDeadlockingOrClosing() throws Exception {
        Path databasePath = Files.createDirectory(this.temporaryDirectory.resolve("lmdb-reentrant-close"));
        LMDBStorageBackend lmdb = new LMDBStorageBackend(databasePath.toString());
        try {
            storeKeys(lmdb, Set.of(WorldEngine.getWorldSectionId(0, 4, 4, 4)));
            assertReentrantCloseRejected(lmdb);
        } finally {
            lmdb.close();
        }

        RecordingStorageBackend cache = new RecordingStorageBackend();
        cache.positions.add(WorldEngine.getWorldSectionId(0, 5, 5, 5));
        ReadonlyCachingLayer readonly = new ReadonlyCachingLayer(cache, new RecordingStorageBackend());
        try {
            assertReentrantCloseRejected(readonly);
        } finally {
            readonly.close();
        }

        FakeJedis jedis = new FakeJedis();
        jedis.page("0", "0", WorldEngine.getWorldSectionId(0, 6, 6, 6));
        RedisStorageBackend redis = new RedisStorageBackend(
                new FakeJedisPool(jedis), "forxy_reentrant_close_test:", null, null);
        try {
            assertReentrantCloseRejected(redis);
        } finally {
            redis.close();
        }
    }

    private static void assertReentrantCloseRejected(StorageBackend backend) {
        AtomicInteger callbacks = new AtomicInteger();
        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> backend.iteratePositions(-1, key -> {
                    callbacks.incrementAndGet();
                    backend.close();
                }));
        assertTrue(failure.getMessage().startsWith("Cannot close"));
        assertEquals(1, callbacks.get());
        assertEquals(1, positions(backend, -1).size());
    }

    private static void assertCloseWaitsForIteration(StorageBackend backend) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch callbackEntered = new CountDownLatch(1);
        CountDownLatch releaseCallback = new CountDownLatch(1);
        Future<?> iteration = null;
        Future<?> close = null;
        try {
            iteration = executor.submit(() -> backend.iteratePositions(-1, key -> {
                callbackEntered.countDown();
                await(releaseCallback);
            }));
            assertTrue(callbackEntered.await(5, TimeUnit.SECONDS));
            close = executor.submit(backend::close);
            Future<?> closeAttempt = close;
            assertThrows(TimeoutException.class, () -> closeAttempt.get(150, TimeUnit.MILLISECONDS));

            releaseCallback.countDown();
            iteration.get(5, TimeUnit.SECONDS);
            close.get(5, TimeUnit.SECONDS);
            assertThrows(IllegalStateException.class, () -> backend.iteratePositions(-1, key -> {
            }));
        } finally {
            releaseCallback.countDown();
            if (iteration != null) {
                try {
                    iteration.get(5, TimeUnit.SECONDS);
                } catch (Exception ignored) {
                }
            }
            if (close == null || !close.isDone()) {
                try {
                    backend.close();
                } catch (Exception ignored) {
                }
            }
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting for iteration release");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(exception);
        }
    }

    private static Set<Long> positions(StorageBackend backend, int level) {
        Set<Long> positions = new LinkedHashSet<>();
        backend.iteratePositions(level, positions::add);
        return positions;
    }

    private static void storeKeys(StorageBackend backend, Set<Long> keys) {
        MemoryBuffer data = new MemoryBuffer(8).zero();
        try {
            for (long key : keys) {
                backend.setSectionData(key, data);
            }
        } finally {
            data.free();
        }
    }

    private static void putMapping(StorageBackend backend, int id, byte[] bytes) {
        ByteBuffer buffer = ByteBuffer.allocateDirect(bytes.length);
        buffer.put(bytes);
        buffer.flip();
        backend.putIdMapping(id, buffer);
    }

    private static final class RecordingStorageBackend extends StorageBackend {
        private final Set<Long> positions = new LinkedHashSet<>();
        private final Int2ObjectOpenHashMap<byte[]> mappings = new Int2ObjectOpenHashMap<>();
        private final AtomicInteger mappingWrites = new AtomicInteger();
        private final AtomicInteger flushes = new AtomicInteger();
        private final AtomicInteger closes = new AtomicInteger();

        void mapping(int id, byte[] data) {
            this.mappings.put(id, Arrays.copyOf(data, data.length));
        }

        @Override
        public void iteratePositions(int level, LongConsumer consumer) {
            for (long key : new ArrayList<>(this.positions)) {
                if (level == -1 || WorldEngine.getLevel(key) == level) {
                    consumer.accept(key);
                }
            }
        }

        @Override
        public MemoryBuffer getSectionData(long key, MemoryBuffer scratch) {
            return null;
        }

        @Override
        public void setSectionData(long key, MemoryBuffer data) {
            this.positions.add(key);
        }

        @Override
        public void deleteSectionData(long key) {
            this.positions.remove(key);
        }

        @Override
        public void putIdMapping(int id, ByteBuffer data) {
            ByteBuffer copy = data.duplicate();
            byte[] bytes = new byte[copy.remaining()];
            copy.get(bytes);
            this.mappings.put(id, bytes);
            this.mappingWrites.incrementAndGet();
        }

        @Override
        public Int2ObjectOpenHashMap<byte[]> getIdMappingsData() {
            Int2ObjectOpenHashMap<byte[]> copy = new Int2ObjectOpenHashMap<>();
            for (var entry : this.mappings.int2ObjectEntrySet()) {
                copy.put(entry.getIntKey(), Arrays.copyOf(entry.getValue(), entry.getValue().length));
            }
            return copy;
        }

        @Override
        public void flush() {
            this.flushes.incrementAndGet();
        }

        @Override
        public void close() {
            this.closes.incrementAndGet();
        }
    }

    private static final class FakeJedisPool extends JedisPool {
        private final Jedis jedis;
        private boolean closed;

        private FakeJedisPool(Jedis jedis) {
            this.jedis = jedis;
        }

        @Override
        public Jedis getResource() {
            if (this.closed) {
                throw new IllegalStateException("Fake Redis pool is closed");
            }
            return this.jedis;
        }

        @Override
        public void close() {
            this.closed = true;
        }
    }

    private static final class FakeJedis extends Jedis {
        private final Map<String, ScanResult<Map.Entry<byte[], byte[]>>> pages = new ConcurrentHashMap<>();
        private final AtomicInteger scanCalls = new AtomicInteger();

        void page(String cursor, String nextCursor, long... keys) {
            var entries = new ArrayList<Map.Entry<byte[], byte[]>>();
            for (long key : keys) {
                entries.add(new AbstractMap.SimpleImmutableEntry<>(longToBytes(key), new byte[]{1}));
            }
            this.pages.put(
                    cursor,
                    new ScanResult<>(nextCursor.getBytes(StandardCharsets.US_ASCII), entries));
        }

        @Override
        public ScanResult<Map.Entry<byte[], byte[]>> hscan(byte[] key, byte[] cursor, ScanParams params) {
            this.scanCalls.incrementAndGet();
            ScanResult<Map.Entry<byte[], byte[]>> page = this.pages.get(
                    new String(cursor, StandardCharsets.US_ASCII));
            if (page == null) {
                throw new IllegalStateException("Unknown fake Redis cursor");
            }
            return page;
        }

        @Override
        public void close() {
        }
    }

    private static byte[] longToBytes(long value) {
        byte[] result = new byte[Long.BYTES];
        for (int index = Long.BYTES - 1; index >= 0; index--) {
            result[index] = (byte) (value & 0xFF);
            value >>= Byte.SIZE;
        }
        return result;
    }
}
