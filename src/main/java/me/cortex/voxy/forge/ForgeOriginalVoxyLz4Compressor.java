package me.cortex.voxy.forge;

import me.cortex.voxy.common.config.compressors.StorageCompressor;
import me.cortex.voxy.common.config.section.SectionSerializationStorage;
import me.cortex.voxy.common.util.MemoryBuffer;
import me.cortex.voxy.common.util.ResizingThreadLocalMemoryBuffer;
import net.jpountz.lz4.LZ4Factory;
import org.lwjgl.system.MemoryUtil;

/** Forge namespace port of original {@code LZ4Compressor}. */
final class ForgeOriginalVoxyLz4Compressor implements StorageCompressor {
    private static final ResizingThreadLocalMemoryBuffer SCRATCH = new ResizingThreadLocalMemoryBuffer(
            SectionSerializationStorage.BIGGEST_SERIALIZED_SECTION_SIZE + 1024);

    private final net.jpountz.lz4.LZ4Compressor compressor;
    private final net.jpountz.lz4.LZ4FastDecompressor decompressor;

    ForgeOriginalVoxyLz4Compressor() {
        this.decompressor = LZ4Factory.nativeInstance().fastDecompressor();
        this.compressor = LZ4Factory.nativeInstance().fastCompressor();
    }

    @Override
    public MemoryBuffer compress(MemoryBuffer saveData) {
        MemoryBuffer result = SCRATCH.get(this.compressor.maxCompressedLength((int) saveData.size) + 4)
                .createUntrackedUnfreeableReference();
        MemoryUtil.memPutInt(result.address, (int) saveData.size);
        int size = this.compressor.compress(
                saveData.asByteBuffer(),
                0,
                (int) saveData.size,
                result.asByteBuffer(),
                4,
                (int) result.size - 4);
        return result.subSize(size + 4L);
    }

    @Override
    public MemoryBuffer decompress(MemoryBuffer saveData) {
        MemoryBuffer result = SCRATCH.get().createUntrackedUnfreeableReference();
        int size = this.decompressor.decompress(
                saveData.asByteBuffer(),
                4,
                result.asByteBuffer(),
                0,
                MemoryUtil.memGetInt(saveData.address));
        return result.subSize(size);
    }

    @Override
    public void close() {
    }
}
