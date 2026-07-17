package me.cortex.voxy.forge;

import me.cortex.voxy.common.config.compressors.StorageCompressor;
import me.cortex.voxy.common.config.section.SectionSerializationStorage;
import me.cortex.voxy.common.util.MemoryBuffer;
import me.cortex.voxy.common.util.ResizingThreadLocalMemoryBuffer;

import static me.cortex.voxy.common.util.GlobalCleaner.CLEANER;
import static org.lwjgl.util.zstd.Zstd.*;

/** Java 17 Forge namespace port of original {@code ZSTDCompressor}. */
final class ZSTDCompressor implements StorageCompressor {
    private record Ref(long ptr) {
    }

    private static Ref createCleanableCompressionContext() {
        long context = requireContext("ZSTD compression context creation", ZSTD_createCCtx());
        Ref ref = new Ref(context);
        CLEANER.register(ref, () -> ZSTD_freeCCtx(context));
        return ref;
    }

    private static Ref createCleanableDecompressionContext() {
        long context = requireContext("ZSTD decompression context creation", ZSTD_createDCtx());
        try {
            requireSuccessfulNativeResult(
                    "ZSTD decompression context parameter setup",
                    nZSTD_DCtx_setParameter(context, ZSTD_d_experimentalParam3, 1));
        } catch (RuntimeException exception) {
            ZSTD_freeDCtx(context);
            throw exception;
        }
        Ref ref = new Ref(context);
        CLEANER.register(ref, () -> ZSTD_freeDCtx(context));
        return ref;
    }

    private static final ThreadLocal<Ref> COMPRESSION_CONTEXT =
            ThreadLocal.withInitial(ZSTDCompressor::createCleanableCompressionContext);
    private static final ThreadLocal<Ref> DECOMPRESSION_CONTEXT =
            ThreadLocal.withInitial(ZSTDCompressor::createCleanableDecompressionContext);
    private static final ResizingThreadLocalMemoryBuffer SCRATCH = new ResizingThreadLocalMemoryBuffer(
            SectionSerializationStorage.BIGGEST_SERIALIZED_SECTION_SIZE + 1024);

    private final int level;

    ZSTDCompressor(int level) {
        this.level = level;
    }

    @Override
    public MemoryBuffer compress(MemoryBuffer saveData) {
        requireSerializedSectionInputSize(saveData.size);
        MemoryBuffer compressed = SCRATCH.get(ZSTD_COMPRESSBOUND(saveData.size))
                .createUntrackedUnfreeableReference();
        long compressedSize = requireSuccessfulNativeResult(
                "ZSTD compression",
                nZSTD_compressCCtx(
                COMPRESSION_CONTEXT.get().ptr,
                compressed.address,
                compressed.size,
                saveData.address,
                saveData.size,
                this.level));
        requireResultFitsDestination("ZSTD compression", compressedSize, compressed.size);
        return compressed.subSize(compressedSize);
    }

    @Override
    public MemoryBuffer decompress(MemoryBuffer saveData) {
        MemoryBuffer decompressed = SCRATCH.get().createUntrackedUnfreeableReference();
        long size = requireSuccessfulNativeResult(
                "ZSTD decompression",
                nZSTD_decompressDCtx(
                        DECOMPRESSION_CONTEXT.get().ptr,
                        decompressed.address,
                        decompressed.size,
                        saveData.address,
                        saveData.size));
        requireResultFitsDestination("ZSTD decompression", size, decompressed.size);
        if (size < SectionSerializationStorage.MINIMUM_SERIALIZED_SECTION_SIZE
                || size > SectionSerializationStorage.BIGGEST_SERIALIZED_SECTION_SIZE) {
            throw new IllegalStateException(
                    "ZSTD decompression produced an invalid serialized section size: " + size
                            + " (expected " + SectionSerializationStorage.MINIMUM_SERIALIZED_SECTION_SIZE
                            + ".." + SectionSerializationStorage.BIGGEST_SERIALIZED_SECTION_SIZE + ")");
        }
        return decompressed.subSize(size);
    }

    static long requireContext(String operation, long context) {
        if (context == 0L) {
            throw new IllegalStateException(operation + " failed: native ZSTD returned a null context");
        }
        return context;
    }

    static long requireSuccessfulNativeResult(String operation, long result) {
        if (ZSTD_isError(result)) {
            throw new IllegalStateException(operation + " failed: " + ZSTD_getErrorName(result));
        }
        return result;
    }

    private static void requireSerializedSectionInputSize(long size) {
        if (size < SectionSerializationStorage.MINIMUM_SERIALIZED_SECTION_SIZE
                || size > SectionSerializationStorage.BIGGEST_SERIALIZED_SECTION_SIZE) {
            throw new IllegalArgumentException(
                    "Invalid serialized section size for ZSTD compression: " + size
                            + " (expected " + SectionSerializationStorage.MINIMUM_SERIALIZED_SECTION_SIZE
                            + ".." + SectionSerializationStorage.BIGGEST_SERIALIZED_SECTION_SIZE + ")");
        }
    }

    private static void requireResultFitsDestination(String operation, long result, long capacity) {
        if (result <= 0L || result > capacity) {
            throw new IllegalStateException(
                    operation + " produced an invalid output size: " + result + " (capacity " + capacity + ")");
        }
    }

    @Override
    public void close() {
    }
}
