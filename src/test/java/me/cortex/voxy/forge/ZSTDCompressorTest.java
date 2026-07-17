package me.cortex.voxy.forge;

import me.cortex.voxy.common.config.section.SectionSerializationStorage;
import me.cortex.voxy.common.util.MemoryBuffer;
import me.cortex.voxy.common.world.WorldSection;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.lwjgl.util.zstd.Zstd.ZSTD_COMPRESSBOUND;
import static org.lwjgl.util.zstd.Zstd.ZSTD_createCCtx;
import static org.lwjgl.util.zstd.Zstd.ZSTD_createDCtx;
import static org.lwjgl.util.zstd.Zstd.ZSTD_freeCCtx;
import static org.lwjgl.util.zstd.Zstd.ZSTD_freeDCtx;
import static org.lwjgl.util.zstd.Zstd.ZSTD_getErrorName;
import static org.lwjgl.util.zstd.Zstd.ZSTD_isError;
import static org.lwjgl.util.zstd.Zstd.nZSTD_compressCCtx;
import static org.lwjgl.util.zstd.Zstd.nZSTD_DCtx_setParameter;

class ZSTDCompressorTest {
    private static final int MINIMUM_SERIALIZED_SECTION_SIZE =
            Long.BYTES * 3 + WorldSection.SECTION_VOLUME * Short.BYTES;

    @Test
    void roundTripsMinimumSizedSectionPayload() {
        byte[] expected = new byte[MINIMUM_SERIALIZED_SECTION_SIZE];
        new Random(0x5A535444L).nextBytes(expected);

        try (OwnedBuffer source = OwnedBuffer.from(expected);
             OwnedBuffer compressed = compressToOwned(source.buffer())) {
            ZSTDCompressor compressor = new ZSTDCompressor(1);
            MemoryBuffer decompressed = compressor.decompress(compressed.buffer());
            byte[] actual = new byte[(int) decompressed.size];
            decompressed.asByteBuffer().get(actual);
            assertArrayEquals(expected, actual);
        }
    }

    @Test
    void truncatedFrameReportsNativeErrorBeforeBufferSizing() {
        byte[] sourceBytes = new byte[MINIMUM_SERIALIZED_SECTION_SIZE];
        new Random(1L).nextBytes(sourceBytes);

        try (OwnedBuffer source = OwnedBuffer.from(sourceBytes);
             OwnedBuffer compressed = compressToOwned(source.buffer());
             OwnedBuffer truncated = OwnedBuffer.copyPrefix(compressed.buffer(), compressed.buffer().size - 1)) {
            ZSTDCompressor compressor = new ZSTDCompressor(1);
            IllegalStateException exception = assertThrows(
                    IllegalStateException.class,
                    () -> compressor.decompress(truncated.buffer()));
            assertNativeDecompressionFailure(exception);
        }
    }

    @Test
    void randomDataReportsNativeErrorBeforeBufferSizing() {
        byte[] randomBytes = new byte[257];
        new Random(2L).nextBytes(randomBytes);

        try (OwnedBuffer randomData = OwnedBuffer.from(randomBytes)) {
            ZSTDCompressor compressor = new ZSTDCompressor(1);
            IllegalStateException exception = assertThrows(
                    IllegalStateException.class,
                    () -> compressor.decompress(randomData.buffer()));
            assertNativeDecompressionFailure(exception);
        }
    }

    @Test
    void destinationTooSmallReportsNativeErrorBeforeBufferSizing() {
        long uncompressedSize = SectionSerializationStorage.BIGGEST_SERIALIZED_SECTION_SIZE + 1025L;
        try (OwnedBuffer oversizedSource = OwnedBuffer.allocate(uncompressedSize);
             OwnedBuffer compressed = rawCompressToOwned(oversizedSource.buffer())) {
            ZSTDCompressor compressor = new ZSTDCompressor(1);
            IllegalStateException exception = assertThrows(
                    IllegalStateException.class,
                    () -> compressor.decompress(compressed.buffer()));
            assertNativeDecompressionFailure(exception);
        }
    }

    @Test
    void decompressionRejectsPayloadBelowSerializedSectionMinimum() {
        try (OwnedBuffer source = OwnedBuffer.from(new byte[]{1, 2, 3, 4});
             OwnedBuffer compressed = rawCompressToOwned(source.buffer())) {
            ZSTDCompressor compressor = new ZSTDCompressor(1);
            IllegalStateException exception = assertThrows(
                    IllegalStateException.class,
                    () -> compressor.decompress(compressed.buffer()));
            assertTrue(exception.getMessage().contains("serialized section size"));
        }
    }

    @Test
    void compressionRejectsPayloadAboveSerializedSectionMaximum() {
        try (OwnedBuffer oversized = OwnedBuffer.allocate(
                SectionSerializationStorage.BIGGEST_SERIALIZED_SECTION_SIZE + 1L)) {
            ZSTDCompressor compressor = new ZSTDCompressor(1);
            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> compressor.compress(oversized.buffer()));
            assertTrue(exception.getMessage().contains("serialized section size"));
        }
    }

    @Test
    void nullNativeContextIsRejected() {
        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> ZSTDCompressor.requireContext("test context creation", 0L));
        assertTrue(exception.getMessage().contains("null context"));
    }

    @Test
    void nativeParameterErrorIncludesPinnedZstdErrorName() {
        long context = ZSTD_createDCtx();
        assertTrue(context != 0L);
        try {
            long error = nZSTD_DCtx_setParameter(context, Integer.MIN_VALUE, 1);
            assertTrue(ZSTD_isError(error));
            IllegalStateException exception = assertThrows(
                    IllegalStateException.class,
                    () -> ZSTDCompressor.requireSuccessfulNativeResult("test parameter setup", error));
            assertTrue(exception.getMessage().equals(
                    "test parameter setup failed: " + ZSTD_getErrorName(error)));
        } finally {
            ZSTD_freeDCtx(context);
        }
    }

    private static OwnedBuffer compressToOwned(MemoryBuffer source) {
        ZSTDCompressor compressor = new ZSTDCompressor(1);
        return OwnedBuffer.copyOf(compressor.compress(source));
    }

    private static OwnedBuffer rawCompressToOwned(MemoryBuffer source) {
        long context = ZSTD_createCCtx();
        if (context == 0L) {
            throw new IllegalStateException("Test could not create a ZSTD compression context");
        }
        try {
            OwnedBuffer destination = OwnedBuffer.allocate(ZSTD_COMPRESSBOUND(source.size));
            long result = nZSTD_compressCCtx(
                    context,
                    destination.buffer().address,
                    destination.buffer().size,
                    source.address,
                    source.size,
                    1);
            if (ZSTD_isError(result)) {
                destination.close();
                throw new IllegalStateException("Test ZSTD compression failed: " + ZSTD_getErrorName(result));
            }
            return destination.prefix(result);
        } finally {
            ZSTD_freeCCtx(context);
        }
    }

    private static void assertNativeDecompressionFailure(IllegalStateException exception) {
        assertTrue(exception.getMessage().startsWith("ZSTD decompression failed: "));
        assertTrue(exception.getMessage().length() > "ZSTD decompression failed: ".length());
    }

    private static final class OwnedBuffer implements AutoCloseable {
        private MemoryBuffer buffer;

        private OwnedBuffer(MemoryBuffer buffer) {
            this.buffer = buffer;
        }

        static OwnedBuffer allocate(long size) {
            return new OwnedBuffer(new MemoryBuffer(size).zero());
        }

        static OwnedBuffer from(byte[] bytes) {
            MemoryBuffer buffer = new MemoryBuffer(bytes.length);
            buffer.asByteBuffer().put(bytes);
            return new OwnedBuffer(buffer);
        }

        static OwnedBuffer copyOf(MemoryBuffer source) {
            return new OwnedBuffer(source.copy());
        }

        static OwnedBuffer copyPrefix(MemoryBuffer source, long size) {
            return new OwnedBuffer(new MemoryBuffer(size).cpyFrom(source.address));
        }

        MemoryBuffer buffer() {
            return this.buffer;
        }

        OwnedBuffer prefix(long size) {
            this.buffer = this.buffer.subSize(size);
            return this;
        }

        @Override
        public void close() {
            if (this.buffer != null) {
                this.buffer.free();
                this.buffer = null;
            }
        }
    }
}
