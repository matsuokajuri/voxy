package me.cortex.voxy.forge;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import me.cortex.voxy.common.config.storage.StorageBackend;
import me.cortex.voxy.common.util.MemoryBuffer;
import me.cortex.voxy.common.world.WorldEngine;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.rocksdb.AbstractImmutableNativeReference;
import org.rocksdb.BlockBasedTableConfig;
import org.rocksdb.BloomFilter;
import org.rocksdb.ColumnFamilyDescriptor;
import org.rocksdb.ColumnFamilyHandle;
import org.rocksdb.ColumnFamilyOptions;
import org.rocksdb.CompactionPriority;
import org.rocksdb.CompressionType;
import org.rocksdb.DBOptions;
import org.rocksdb.DataBlockIndexType;
import org.rocksdb.HyperClockCache;
import org.rocksdb.ReadOptions;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;
import org.rocksdb.WriteOptions;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.LongConsumer;

/** Java 17 Forge namespace port of original {@code RocksDBStorageBackend}. */
final class ForgeOriginalVoxyRocksDBStorageBackend extends StorageBackend {
    private final RocksDB db;
    private final ColumnFamilyHandle worldSections;
    private final ColumnFamilyHandle idMappings;
    private final ReadOptions sectionReadOptions;
    private final WriteOptions sectionWriteOptions;
    private final List<AbstractImmutableNativeReference> closeList = new ArrayList<>();

    ForgeOriginalVoxyRocksDBStorageBackend(String path) {
        RocksDB.loadLibrary();

        ColumnFamilyOptions defaultOptions = new ColumnFamilyOptions()
                .setCompressionType(CompressionType.ZSTD_COMPRESSION)
                .optimizeForSmallDb();
        ColumnFamilyOptions worldSectionOptions = new ColumnFamilyOptions()
                .setCompressionType(CompressionType.NO_COMPRESSION)
                .setCompactionPriority(CompactionPriority.MinOverlappingRatio)
                .setLevelCompactionDynamicLevelBytes(true)
                .optimizeForPointLookup(128);

        HyperClockCache blockCache = new HyperClockCache(128 * 1024L * 1024L, 0, 4, false);
        BloomFilter filter = new BloomFilter(10);
        worldSectionOptions.setTableFormatConfig(new BlockBasedTableConfig()
                .setCacheIndexAndFilterBlocksWithHighPriority(true)
                .setBlockCache(blockCache)
                .setDataBlockHashTableUtilRatio(0.75)
                .setDataBlockIndexType(DataBlockIndexType.kDataBlockBinaryAndHash)
                .setFilterPolicy(filter));

        List<ColumnFamilyDescriptor> descriptors = Arrays.asList(
                new ColumnFamilyDescriptor(RocksDB.DEFAULT_COLUMN_FAMILY, defaultOptions),
                new ColumnFamilyDescriptor("world_sections".getBytes(), worldSectionOptions),
                new ColumnFamilyDescriptor("id_mappings".getBytes(), defaultOptions));
        DBOptions options = new DBOptions()
                .setAvoidUnnecessaryBlockingIO(true)
                .setIncreaseParallelism(2)
                .setCreateIfMissing(true)
                .setCreateMissingColumnFamilies(true)
                .setMaxTotalWalSize(128L * 1024L * 1024L);
        List<ColumnFamilyHandle> handles = new ArrayList<>();

        try {
            this.db = RocksDB.open(options, path, descriptors, handles);
            this.sectionReadOptions = new ReadOptions();
            this.sectionWriteOptions = new WriteOptions();
            this.closeList.add(options);
            this.closeList.add(defaultOptions);
            this.closeList.add(worldSectionOptions);
            this.closeList.add(this.sectionReadOptions);
            this.closeList.add(this.sectionWriteOptions);
            this.closeList.add(filter);
            this.closeList.add(blockCache);
            this.closeList.addAll(handles);
            this.worldSections = handles.get(1);
            this.idMappings = handles.get(2);
            this.db.flushWal(true);
        } catch (RocksDBException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void iteratePositions(int level, LongConsumer consumer) {
        try (MemoryStack stack = MemoryStack.stackPush();
             var iterator = this.db.newIterator(this.worldSections, this.sectionReadOptions)) {
            ByteBuffer keyBuffer = stack.calloc(8);
            long keyAddress = MemoryUtil.memAddress(keyBuffer);
            if (level != -1) {
                ByteBuffer seekBuffer = stack.calloc(8);
                MemoryUtil.memPutLong(
                        MemoryUtil.memAddress(seekBuffer),
                        Long.reverseBytes(Integer.toUnsignedLong(level) << 60));
                iterator.seek(seekBuffer);
            } else {
                iterator.seekToFirst();
            }
            while (iterator.isValid()) {
                keyBuffer.clear();
                iterator.key(keyBuffer);
                long key = Long.reverseBytes(MemoryUtil.memGetLong(keyAddress));
                if (level != -1 && WorldEngine.getLevel(key) != level) {
                    break;
                }
                consumer.accept(key);
                iterator.next();
            }
        }
    }

    @Override
    public MemoryBuffer getSectionData(long key, MemoryBuffer scratch) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer keyBuffer = stack.malloc(8);
            MemoryUtil.memPutLong(MemoryUtil.memAddress(keyBuffer), Long.reverseBytes(key));
            int result = this.db.get(
                    this.worldSections,
                    this.sectionReadOptions,
                    keyBuffer,
                    MemoryUtil.memByteBuffer(scratch.address, (int) scratch.size));
            return result == RocksDB.NOT_FOUND ? null : scratch.subSize(result);
        } catch (RocksDBException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void setSectionData(long key, MemoryBuffer data) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer keyBuffer = stack.calloc(8);
            MemoryUtil.memPutLong(MemoryUtil.memAddress(keyBuffer), Long.reverseBytes(key));
            this.db.put(this.worldSections, this.sectionWriteOptions, keyBuffer, data.asByteBuffer());
        } catch (RocksDBException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void deleteSectionData(long key) {
        try {
            this.db.delete(this.worldSections, longToBytes(key));
        } catch (RocksDBException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void putIdMapping(int id, ByteBuffer data) {
        try {
            byte[] value = new byte[data.remaining()];
            data.get(value);
            data.rewind();
            this.db.put(this.idMappings, intToBytes(id), value);
        } catch (RocksDBException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Int2ObjectOpenHashMap<byte[]> getIdMappingsData() {
        Int2ObjectOpenHashMap<byte[]> output = new Int2ObjectOpenHashMap<>();
        try (var iterator = this.db.newIterator(this.idMappings)) {
            for (iterator.seekToFirst(); iterator.isValid(); iterator.next()) {
                output.put(bytesToInt(iterator.key()), iterator.value());
            }
        }
        return output;
    }

    @Override
    public void flush() {
        try {
            this.db.flushWal(true);
        } catch (RocksDBException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void close() {
        this.flush();
        this.closeList.forEach(AbstractImmutableNativeReference::close);
        try {
            this.db.closeE();
        } catch (RocksDBException e) {
            throw new RuntimeException(e);
        }
    }

    private static byte[] intToBytes(int value) {
        return new byte[]{(byte) (value >> 24), (byte) (value >> 16), (byte) (value >> 8), (byte) value};
    }

    private static int bytesToInt(byte[] value) {
        return (Byte.toUnsignedInt(value[0]) << 24)
                | (Byte.toUnsignedInt(value[1]) << 16)
                | (Byte.toUnsignedInt(value[2]) << 8)
                | Byte.toUnsignedInt(value[3]);
    }

    private static byte[] longToBytes(long value) {
        byte[] result = new byte[Long.BYTES];
        for (int i = Long.BYTES - 1; i >= 0; i--) {
            result[i] = (byte) (value & 0xFF);
            value >>= Byte.SIZE;
        }
        return result;
    }
}
