package me.cortex.voxy.forge;

import me.cortex.voxy.common.config.compressors.StorageCompressor;
import me.cortex.voxy.common.config.section.SectionSerializationStorage;
import me.cortex.voxy.common.util.MemoryBuffer;
import me.cortex.voxy.common.util.ResizingThreadLocalMemoryBuffer;

import static me.cortex.voxy.common.util.GlobalCleaner.CLEANER;
import static org.lwjgl.util.zstd.Zstd.*;

/** Java 17 Forge namespace port of original {@code ZSTDCompressor}. */
final class ForgeOriginalVoxyZstdCompressor implements StorageCompressor {
    private record Ref(long ptr) {
    }

    private static Ref createCleanableCompressionContext() {
        long context = ZSTD_createCCtx();
        Ref ref = new Ref(context);
        CLEANER.register(ref, () -> ZSTD_freeCCtx(context));
        return ref;
    }

    private static Ref createCleanableDecompressionContext() {
        long context = ZSTD_createDCtx();
        nZSTD_DCtx_setParameter(context, ZSTD_d_experimentalParam3, 1);
        Ref ref = new Ref(context);
        CLEANER.register(ref, () -> ZSTD_freeDCtx(context));
        return ref;
    }

    private static final ThreadLocal<Ref> COMPRESSION_CONTEXT =
            ThreadLocal.withInitial(ForgeOriginalVoxyZstdCompressor::createCleanableCompressionContext);
    private static final ThreadLocal<Ref> DECOMPRESSION_CONTEXT =
            ThreadLocal.withInitial(ForgeOriginalVoxyZstdCompressor::createCleanableDecompressionContext);
    private static final ResizingThreadLocalMemoryBuffer SCRATCH = new ResizingThreadLocalMemoryBuffer(
            SectionSerializationStorage.BIGGEST_SERIALIZED_SECTION_SIZE + 1024);

    private final int level;

    ForgeOriginalVoxyZstdCompressor(int level) {
        this.level = level;
    }

    @Override
    public MemoryBuffer compress(MemoryBuffer saveData) {
        MemoryBuffer compressed = SCRATCH.get(ZSTD_COMPRESSBOUND(saveData.size))
                .createUntrackedUnfreeableReference();
        long compressedSize = nZSTD_compressCCtx(
                COMPRESSION_CONTEXT.get().ptr,
                compressed.address,
                compressed.size,
                saveData.address,
                saveData.size,
                this.level);
        return compressed.subSize(compressedSize);
    }

    @Override
    public MemoryBuffer decompress(MemoryBuffer saveData) {
        MemoryBuffer decompressed = SCRATCH.get().createUntrackedUnfreeableReference();
        long size = nZSTD_decompressDCtx(
                DECOMPRESSION_CONTEXT.get().ptr,
                decompressed.address,
                decompressed.size,
                saveData.address,
                saveData.size);
        return decompressed.subSize(size);
    }

    @Override
    public void close() {
    }
}
