package me.cortex.voxy.forge;

import net.jpountz.lz4.LZ4FrameOutputStream;
import org.junit.jupiter.api.Test;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.util.zstd.ZSTDInBuffer;
import org.lwjgl.util.zstd.ZSTDOutBuffer;
import me.cortex.voxy.dependency.xz.LZMA2Options;
import me.cortex.voxy.dependency.xz.XZOutputStream;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.lwjgl.util.zstd.Zstd.ZSTD_CCtx_reset;
import static org.lwjgl.util.zstd.Zstd.ZSTD_CStreamOutSize;
import static org.lwjgl.util.zstd.Zstd.ZSTD_compress;
import static org.lwjgl.util.zstd.Zstd.ZSTD_compressBound;
import static org.lwjgl.util.zstd.Zstd.ZSTD_compressStream2;
import static org.lwjgl.util.zstd.Zstd.ZSTD_createCStream;
import static org.lwjgl.util.zstd.Zstd.ZSTD_e_end;
import static org.lwjgl.util.zstd.Zstd.ZSTD_freeCStream;
import static org.lwjgl.util.zstd.Zstd.ZSTD_getErrorName;
import static org.lwjgl.util.zstd.Zstd.ZSTD_isError;
import static org.lwjgl.util.zstd.Zstd.ZSTD_reset_session_only;

class ForgeOriginalVoxyDhDataDecoderTest {
    @Test
    void supportsEveryOfficialCompressionMode() throws IOException {
        byte[] input = new byte[32_768];
        for (int index = 0; index < input.length; index++) {
            input[index] = (byte) (index * 31 + index / 7);
        }

        for (int compression = 0; compression <= 4; compression++) {
            byte[] compressed = compress(compression, input);
            byte[] decoded;
            try (var stream = ForgeOriginalVoxyDhDataDecoder.openDecompressedStream(
                    compression,
                    compressed)) {
                decoded = stream.readAllBytes();
            }
            assertArrayEquals(input, decoded, "compression mode " + compression);
        }
    }

    @Test
    void decodesOriginalFormatOneColumnLayout() throws IOException {
        long first = dataPoint(7, 5, 18, 3, 12);
        long second = dataPoint(9, 2, 16, 0, 0);
        long[][] source = emptyColumns();
        source[index(4, 9)] = new long[]{first, second};

        byte[] data = encodeV1(source);
        long[][] decoded = ForgeOriginalVoxyDhDataDecoder.decode(
                ForgeOriginalVoxyDhDataDecoder.FORMAT_V1,
                ForgeOriginalVoxyDhDataDecoder.COMPRESSION_UNCOMPRESSED,
                new ForgeOriginalVoxyDhDataDecoder.Blobs(data, null, null, null, null));

        assertArrayEquals(new long[]{first, second}, decoded[index(4, 9)]);
        assertEquals(0, decoded[index(5, 9)].length);
    }

    @Test
    void decodesFormatTwoCenterAndAllAdjacentBlobs() throws IOException {
        long[][] source = emptyColumns();
        long centerTop = dataPoint(3, 5, 30, 7, 12);
        long centerBottom = dataPoint(4, 10, 20, 0, 0);
        source[index(10, 20)] = new long[]{centerTop, centerBottom};
        source[index(5, 0)] = new long[]{dataPoint(11, 2, 8, 1, 2)};
        source[index(6, 63)] = new long[]{dataPoint(12, 3, 9, 2, 3)};
        source[index(63, 7)] = new long[]{dataPoint(13, 4, 10, 3, 4)};
        source[index(0, 8)] = new long[]{dataPoint(14, 5, 11, 4, 5)};

        ForgeOriginalVoxyDhDataDecoder.Blobs uncompressedBlobs = new ForgeOriginalVoxyDhDataDecoder.Blobs(
                encodeV2Range(source, 1, 63, 1, 63),
                encodeV2Range(source, 0, 64, 0, 1),
                encodeV2Range(source, 0, 64, 63, 64),
                encodeV2Range(source, 63, 64, 0, 64),
                encodeV2Range(source, 0, 1, 0, 64));

        for (int compression = 0; compression <= 4; compression++) {
            ForgeOriginalVoxyDhDataDecoder.Blobs compressedBlobs = new ForgeOriginalVoxyDhDataDecoder.Blobs(
                    compress(compression, uncompressedBlobs.data()),
                    compress(compression, uncompressedBlobs.north()),
                    compress(compression, uncompressedBlobs.south()),
                    compress(compression, uncompressedBlobs.east()),
                    compress(compression, uncompressedBlobs.west()));
            long[][] decoded = ForgeOriginalVoxyDhDataDecoder.decode(
                    ForgeOriginalVoxyDhDataDecoder.FORMAT_V2,
                    compression,
                    compressedBlobs);

            assertArrayEquals(new long[]{centerTop, centerBottom}, decoded[index(10, 20)],
                    "center, compression mode " + compression);
            assertArrayEquals(source[index(5, 0)], decoded[index(5, 0)]);
            assertArrayEquals(source[index(6, 63)], decoded[index(6, 63)]);
            assertArrayEquals(source[index(63, 7)], decoded[index(63, 7)]);
            assertArrayEquals(source[index(0, 8)], decoded[index(0, 8)]);
        }
    }

    @Test
    void rejectsUnknownContractsAndIncompleteFormatTwoRows() {
        byte[] emptyV1 = new byte[ForgeOriginalVoxyDhDataDecoder.COLUMN_COUNT * Short.BYTES];
        ForgeOriginalVoxyDhDataDecoder.Blobs onlyCenter =
                new ForgeOriginalVoxyDhDataDecoder.Blobs(emptyV1, null, null, null, null);

        assertThrows(IOException.class, () -> ForgeOriginalVoxyDhDataDecoder.decode(99, 0, onlyCenter));
        assertThrows(IOException.class, () -> ForgeOriginalVoxyDhDataDecoder.decode(1, 99, onlyCenter));
        assertThrows(IOException.class, () -> ForgeOriginalVoxyDhDataDecoder.decode(2, 0, onlyCenter));
    }

    private static long[][] emptyColumns() {
        long[][] columns = new long[ForgeOriginalVoxyDhDataDecoder.COLUMN_COUNT][];
        Arrays.setAll(columns, ignored -> new long[0]);
        return columns;
    }

    private static byte[] encodeV1(long[][] columns) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream output = new DataOutputStream(bytes)) {
            for (int x = 0; x < ForgeOriginalVoxyDhDataDecoder.WIDTH; x++) {
                for (int z = 0; z < ForgeOriginalVoxyDhDataDecoder.WIDTH; z++) {
                    long[] column = columns[index(x, z)];
                    output.writeShort(column.length);
                    for (long dataPoint : column) {
                        output.writeLong(dataPoint);
                    }
                }
            }
        }
        return bytes.toByteArray();
    }

    private static byte[] encodeV2Range(
            long[][] columns,
            int minX,
            int maxX,
            int minZ,
            int maxZ) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream output = new DataOutputStream(bytes)) {
            for (int x = minX; x < maxX; x++) {
                for (int z = minZ; z < maxZ; z++) {
                    writeVarInt(output, columns[index(x, z)].length);
                }
            }

            int previousBottomY = 0;
            for (int x = minX; x < maxX; x++) {
                for (int z = minZ; z < maxZ; z++) {
                    for (long dataPoint : columns[index(x, z)]) {
                        int height = getHeight(dataPoint);
                        int bottomY = ForgeOriginalVoxyDhDataDecoder.getBottomY(dataPoint);
                        boolean hasLight = (ForgeOriginalVoxyDhDataDecoder.getBlockLight(dataPoint)
                                | ForgeOriginalVoxyDhDataDecoder.getSkyLight(dataPoint)) != 0;
                        boolean discontinuity = bottomY != previousBottomY - height;
                        int encodedId = (ForgeOriginalVoxyDhDataDecoder.getId(dataPoint) << 2)
                                | (hasLight ? 2 : 0)
                                | (discontinuity ? 1 : 0);
                        writeVarInt(output, encodedId);
                        previousBottomY = bottomY;
                    }
                }
            }

            for (int x = minX; x < maxX; x++) {
                for (int z = minZ; z < maxZ; z++) {
                    for (long dataPoint : columns[index(x, z)]) {
                        writeVarInt(output, getHeight(dataPoint));
                    }
                }
            }

            previousBottomY = 0;
            for (int x = minX; x < maxX; x++) {
                for (int z = minZ; z < maxZ; z++) {
                    for (long dataPoint : columns[index(x, z)]) {
                        int height = getHeight(dataPoint);
                        int bottomY = ForgeOriginalVoxyDhDataDecoder.getBottomY(dataPoint);
                        int expectedBottomY = previousBottomY - height;
                        if (bottomY != expectedBottomY) {
                            writeVarInt(output, zigZagEncode(bottomY - expectedBottomY));
                        }
                        previousBottomY = bottomY;
                    }
                }
            }

            for (int x = minX; x < maxX; x++) {
                for (int z = minZ; z < maxZ; z++) {
                    for (long dataPoint : columns[index(x, z)]) {
                        int packedLight = (ForgeOriginalVoxyDhDataDecoder.getBlockLight(dataPoint) << 4)
                                | ForgeOriginalVoxyDhDataDecoder.getSkyLight(dataPoint);
                        if (packedLight != 0) {
                            output.writeByte(packedLight);
                        }
                    }
                }
            }
        }
        return bytes.toByteArray();
    }

    private static byte[] compress(int compression, byte[] input) throws IOException {
        if (compression == ForgeOriginalVoxyDhDataDecoder.COMPRESSION_UNCOMPRESSED) {
            return input;
        }
        if (compression == ForgeOriginalVoxyDhDataDecoder.COMPRESSION_ZSTD_BLOCK) {
            return compressZstdBlock(input);
        }
        if (compression == ForgeOriginalVoxyDhDataDecoder.COMPRESSION_ZSTD_STREAM) {
            return compressZstdStream(input);
        }

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (OutputStream output = switch (compression) {
            case ForgeOriginalVoxyDhDataDecoder.COMPRESSION_LZ4 -> new LZ4FrameOutputStream(bytes);
            case ForgeOriginalVoxyDhDataDecoder.COMPRESSION_LZMA2 ->
                    new XZOutputStream(bytes, new LZMA2Options());
            default -> throw new IllegalArgumentException("compression " + compression);
        }) {
            output.write(input);
        }
        return bytes.toByteArray();
    }

    private static byte[] compressZstdBlock(byte[] input) throws IOException {
        int capacity;
        try {
            capacity = Math.toIntExact(ZSTD_compressBound(input.length));
        } catch (ArithmeticException exception) {
            throw new IOException("Invalid Zstd compression bound", exception);
        }

        ByteBuffer source = MemoryUtil.memAlloc(Math.max(1, input.length));
        ByteBuffer destination = MemoryUtil.memAlloc(capacity);
        try {
            source.put(input).flip();
            long size = ZSTD_compress(destination, source, 3);
            checkZstdResult(size);
            int compressedSize = Math.toIntExact(size);
            byte[] result = new byte[compressedSize];
            destination.position(0).limit(compressedSize);
            destination.get(result);
            return result;
        } finally {
            MemoryUtil.memFree(destination);
            MemoryUtil.memFree(source);
        }
    }

    private static byte[] compressZstdStream(byte[] inputBytes) throws IOException {
        long context = ZSTD_createCStream();
        if (context == MemoryUtil.NULL) {
            throw new OutOfMemoryError("Could not allocate Zstd compression context");
        }

        int outputChunkSize;
        try {
            outputChunkSize = Math.toIntExact(ZSTD_CStreamOutSize());
        } catch (ArithmeticException exception) {
            ZSTD_freeCStream(context);
            throw new IOException("Invalid Zstd output buffer size", exception);
        }

        ByteBuffer source = MemoryUtil.memAlloc(Math.max(1, inputBytes.length));
        ByteBuffer destination = MemoryUtil.memAlloc(outputChunkSize);
        try (ZSTDInBuffer input = ZSTDInBuffer.calloc();
             ZSTDOutBuffer output = ZSTDOutBuffer.calloc();
             ByteArrayOutputStream result = new ByteArrayOutputStream()) {
            checkZstdResult(ZSTD_CCtx_reset(context, ZSTD_reset_session_only));
            source.put(inputBytes).flip();
            input.set(source, 0L);

            long remaining;
            do {
                destination.clear();
                output.set(destination, 0L);
                remaining = ZSTD_compressStream2(context, output, input, ZSTD_e_end);
                checkZstdResult(remaining);

                int produced = Math.toIntExact(output.pos());
                if (produced > 0) {
                    byte[] chunk = new byte[produced];
                    destination.position(0).limit(produced);
                    destination.get(chunk);
                    result.write(chunk, 0, chunk.length);
                }
            } while (remaining != 0L);

            if (input.pos() != input.size()) {
                throw new IOException("Zstd stream did not consume its input");
            }
            return result.toByteArray();
        } finally {
            MemoryUtil.memFree(destination);
            MemoryUtil.memFree(source);
            ZSTD_freeCStream(context);
        }
    }

    private static void checkZstdResult(long result) throws IOException {
        if (ZSTD_isError(result)) {
            throw new IOException("Zstd test compression failed: " + ZSTD_getErrorName(result));
        }
    }

    private static long dataPoint(int id, int height, int bottomY, int blockLight, int skyLight) {
        return (id & Integer.MAX_VALUE)
                | ((long) height << 32)
                | ((long) bottomY << 44)
                | ((long) skyLight << 56)
                | ((long) blockLight << 60);
    }

    private static int getHeight(long dataPoint) {
        return (int) ((dataPoint >>> 32) & ((1 << 12) - 1));
    }

    private static int index(int x, int z) {
        return x * ForgeOriginalVoxyDhDataDecoder.WIDTH + z;
    }

    private static int zigZagEncode(int value) {
        return (value << 1) ^ (value >> 31);
    }

    private static void writeVarInt(DataOutputStream output, int value) throws IOException {
        if (value < 0) {
            throw new IllegalArgumentException("negative varint");
        }
        while (value >= 128) {
            output.writeByte(value | 128);
            value >>>= 7;
        }
        output.writeByte(value);
    }
}
