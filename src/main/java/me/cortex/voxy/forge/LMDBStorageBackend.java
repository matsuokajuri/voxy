package me.cortex.voxy.forge;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import me.cortex.voxy.common.config.storage.StorageBackend;
import me.cortex.voxy.common.config.storage.lmdb.LMDBInterface;
import me.cortex.voxy.common.util.MemoryBuffer;
import me.cortex.voxy.common.util.UnsafeUtil;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.util.lmdb.MDBVal;

import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.LongConsumer;
import java.util.function.Supplier;

import static org.lwjgl.util.lmdb.LMDB.MDB_NEXT;
import static org.lwjgl.util.lmdb.LMDB.MDB_NOTFOUND;
import static org.lwjgl.util.lmdb.LMDB.MDB_RDONLY;

/** Forge namespace port of original {@code LMDBStorageBackend}. */
final class LMDBStorageBackend extends StorageBackend {
    private static final long GROW_SIZE = 1L << 25;

    private final AtomicInteger accessingCounts = new AtomicInteger();
    private final ReentrantLock resizeLock = new ReentrantLock();
    private final LMDBInterface dbi;
    private final LMDBInterface.Database sectionDatabase;
    private final LMDBInterface.Database idMappingDatabase;

    LMDBStorageBackend(String file) {
        this.dbi = new LMDBInterface.Builder()
                .setMaxDbs(2)
                .open(file, 0)
                .fetch();
        this.dbi.setMapSize(GROW_SIZE);
        this.sectionDatabase = this.dbi.createDb("world_sections");
        this.idMappingDatabase = this.dbi.createDb("id_mapping");
    }

    private void growEnv() {
        long size = this.dbi.getMapSize() + GROW_SIZE;
        VoxyForge.LOGGER.info("Growing DBI env size to: {} bytes", size);
        this.dbi.setMapSize(size);
    }

    private <T> T resizingTransaction(Supplier<T> transaction) {
        while (true) {
            try {
                return this.synchronizedTransaction(transaction);
            } catch (Throwable throwable) {
                if (throwable.getMessage().startsWith("Code: -30792")) {
                    if (this.resizeLock.tryLock()) {
                        while (this.accessingCounts.get() != 0) {
                            Thread.onSpinWait();
                        }
                        this.growEnv();
                        this.resizeLock.unlock();
                    }
                } else {
                    throw throwable;
                }
            }
        }
    }

    private <T> T synchronizedTransaction(Supplier<T> transaction) {
        try {
            this.accessingCounts.getAndAdd(1);
            while (this.resizeLock.isLocked()) {
                this.accessingCounts.getAndAdd(-1);
                while (this.resizeLock.isLocked()) {
                    Thread.onSpinWait();
                }
                this.accessingCounts.getAndAdd(1);
            }
            return transaction.get();
        } finally {
            this.accessingCounts.getAndAdd(-1);
        }
    }

    @Override
    public void iteratePositions(int level, LongConsumer consumer) {
        throw new IllegalStateException("Not yet implemented");
    }

    @Override
    public MemoryBuffer getSectionData(long key, MemoryBuffer scratch) {
        return this.synchronizedTransaction(() -> this.sectionDatabase.transaction(MDB_RDONLY, transaction -> {
            ByteBuffer keyBuffer = transaction.stack.malloc(8);
            keyBuffer.putLong(0, key);
            ByteBuffer value = transaction.get(keyBuffer);
            if (value == null) {
                return null;
            }
            UnsafeUtil.memcpy(MemoryUtil.memAddress(value), scratch.address, value.remaining());
            return scratch.subSize(value.remaining());
        }));
    }

    @Override
    public void setSectionData(long key, MemoryBuffer data) {
        this.resizingTransaction(() -> this.sectionDatabase.transaction(transaction -> {
            ByteBuffer keyBuffer = transaction.stack.malloc(8);
            keyBuffer.putLong(0, key);
            transaction.put(keyBuffer, MemoryUtil.memByteBuffer(data.address, (int) data.size), 0);
            return null;
        }));
    }

    @Override
    public void deleteSectionData(long key) {
        this.synchronizedTransaction(() -> this.sectionDatabase.transaction(transaction -> {
            ByteBuffer keyBuffer = transaction.stack.malloc(8);
            keyBuffer.putLong(0, key);
            transaction.del(keyBuffer);
            return null;
        }));
    }

    @Override
    public synchronized void putIdMapping(int id, ByteBuffer data) {
        this.resizingTransaction(() -> this.idMappingDatabase.transaction(transaction -> {
            ByteBuffer keyBuffer = transaction.stack.malloc(4);
            keyBuffer.putInt(0, id);
            transaction.put(keyBuffer, data, 0);
            return null;
        }));
    }

    @Override
    public Int2ObjectOpenHashMap<byte[]> getIdMappingsData() {
        return this.synchronizedTransaction(() -> {
            Int2ObjectOpenHashMap<byte[]> mappings = new Int2ObjectOpenHashMap<>();
            this.idMappingDatabase.transaction(MDB_RDONLY, transaction -> {
                try (var cursor = transaction.createCursor()) {
                    MDBVal keyPointer = MDBVal.malloc(transaction.stack);
                    MDBVal valuePointer = MDBVal.malloc(transaction.stack);
                    while (cursor.get(MDB_NEXT, keyPointer, valuePointer) != MDB_NOTFOUND) {
                        int key = keyPointer.mv_data().getInt(0);
                        byte[] data = new byte[(int) valuePointer.mv_size()];
                        Objects.requireNonNull(valuePointer.mv_data()).get(data);
                        if (mappings.put(key, data) != null) {
                            throw new IllegalStateException("Multiple mappings to same id");
                        }
                    }
                }
                return null;
            });
            return mappings;
        });
    }

    @Override
    public void flush() {
        this.dbi.flush(true);
    }

    @Override
    public void close() {
        this.sectionDatabase.close();
        this.idMappingDatabase.close();
        this.dbi.close();
    }
}
