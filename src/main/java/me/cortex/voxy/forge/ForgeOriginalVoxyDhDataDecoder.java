package me.cortex.voxy.forge;

import com.github.luben.zstd.ZstdInputStream;
import net.jpountz.lz4.LZ4FrameInputStream;
import org.tukaani.xz.BasicArrayCache;
import org.tukaani.xz.ResettableArrayCache;
import org.tukaani.xz.XZInputStream;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

/**
 * Decoder for the on-disk Distant Horizons FullData payload.
 *
 * <p>Format 1 follows original Voxy's {@code DHImporter}. Format 2 and the
 * five compression modes follow DH 3.2.0's {@code FullDataSourceV2DTO} and
 * {@code DhDataInputStream} contracts.</p>
 */
final class ForgeOriginalVoxyDhDataDecoder {
    static final int WIDTH = 64;
    static final int COLUMN_COUNT = WIDTH * WIDTH;

    static final int FORMAT_V1 = 1;
    static final int FORMAT_V2 = 2;

    static final int COMPRESSION_UNCOMPRESSED = 0;
    static final int COMPRESSION_LZ4 = 1;
    static final int COMPRESSION_ZSTD_STREAM = 2;
    static final int COMPRESSION_LZMA2 = 3;
    static final int COMPRESSION_ZSTD_BLOCK = 4;

    private static final int MAX_COLUMN_DATAPOINTS = 4096;
    private static final long ID_MASK = Integer.MAX_VALUE;
    private static final long HEIGHT_MASK = (1L << 12) - 1L;
    private static final long BOTTOM_Y_MASK = (1L << 12) - 1L;
    private static final ThreadLocal<ResettableArrayCache> XZ_ARRAY_CACHE =
            ThreadLocal.withInitial(() -> new ResettableArrayCache(new BasicArrayCache()));

    private static final Range CENTER = new Range(1, WIDTH - 1, 1, WIDTH - 1);
    private static final Range NORTH = new Range(0, WIDTH, 0, 1);
    private static final Range SOUTH = new Range(0, WIDTH, WIDTH - 1, WIDTH);
    private static final Range EAST = new Range(WIDTH - 1, WIDTH, 0, WIDTH);
    private static final Range WEST = new Range(0, 1, 0, WIDTH);

    private ForgeOriginalVoxyDhDataDecoder() {
    }

    record Blobs(byte[] data, byte[] north, byte[] south, byte[] east, byte[] west) {
    }

    private record Range(int minX, int maxX, int minZ, int maxZ) {
    }

    static boolean supportsFormat(int format) {
        return format == FORMAT_V1 || format == FORMAT_V2;
    }

    static boolean supportsCompression(int compression) {
        return compression >= COMPRESSION_UNCOMPRESSED && compression <= COMPRESSION_ZSTD_BLOCK;
    }

    static long[][] decode(int format, int compression, Blobs blobs) throws IOException {
        if (!supportsFormat(format)) {
            throw new IOException("Unsupported Distant Horizons data format " + format);
        }
        if (!supportsCompression(compression)) {
            throw new IOException("Unsupported Distant Horizons compression mode " + compression);
        }
        if (blobs == null || blobs.data() == null) {
            throw new IOException("Distant Horizons data blob is missing");
        }

        long[][] columns = new long[COLUMN_COUNT][];
        if (format == FORMAT_V1) {
            try (DataInputStream input = openDataInput(compression, blobs.data())) {
                decodeV1(input, columns);
            }
        } else {
            requireAdjacentBlob("NorthAdjData", blobs.north());
            requireAdjacentBlob("SouthAdjData", blobs.south());
            requireAdjacentBlob("EastAdjData", blobs.east());
            requireAdjacentBlob("WestAdjData", blobs.west());
            decodeV2Blob(compression, blobs.data(), columns, CENTER);
            decodeV2Blob(compression, blobs.north(), columns, NORTH);
            decodeV2Blob(compression, blobs.south(), columns, SOUTH);
            decodeV2Blob(compression, blobs.east(), columns, EAST);
            decodeV2Blob(compression, blobs.west(), columns, WEST);
        }

        for (int index = 0; index < columns.length; index++) {
            if (columns[index] == null) {
                columns[index] = new long[0];
            }
        }
        return columns;
    }

    static InputStream openDecompressedStream(int compression, byte[] compressed) throws IOException {
        if (!supportsCompression(compression)) {
            throw new IOException("Unsupported Distant Horizons compression mode " + compression);
        }
        if (compressed == null) {
            throw new IOException("Distant Horizons compressed blob is missing");
        }

        ByteArrayInputStream source = new ByteArrayInputStream(compressed);
        return switch (compression) {
            case COMPRESSION_UNCOMPRESSED -> source;
            case COMPRESSION_LZ4 -> new LZ4FrameInputStream(new BufferedInputStream(source));
            case COMPRESSION_ZSTD_STREAM, COMPRESSION_ZSTD_BLOCK ->
                    new ZstdInputStream(new BufferedInputStream(source));
            case COMPRESSION_LZMA2 -> {
                ResettableArrayCache cache = XZ_ARRAY_CACHE.get();
                cache.reset();
                yield new XZInputStream(new BufferedInputStream(source), -1, false, cache);
            }
            default -> throw new IOException("Unsupported Distant Horizons compression mode " + compression);
        };
    }

    private static DataInputStream openDataInput(int compression, byte[] compressed) throws IOException {
        return new DataInputStream(openDecompressedStream(compression, compressed));
    }

    private static void requireAdjacentBlob(String name, byte[] blob) throws IOException {
        if (blob == null) {
            throw new IOException("Distant Horizons format 2 row is missing " + name);
        }
    }

    private static void decodeV1(DataInputStream input, long[][] columns) throws IOException {
        for (int x = 0; x < WIDTH; x++) {
            for (int z = 0; z < WIDTH; z++) {
                int count = input.readShort();
                validateColumnCount(x, z, count);
                long[] column = new long[count];
                for (int index = 0; index < count; index++) {
                    column[index] = input.readLong();
                }
                columns[columnIndex(x, z)] = column;
            }
        }
    }

    private static void decodeV2Blob(int compression, byte[] blob, long[][] columns, Range range)
            throws IOException {
        try (DataInputStream input = openDataInput(compression, blob)) {
            decodeV2Range(input, columns, range);
        }
    }

    private static void decodeV2Range(DataInputStream input, long[][] columns, Range range)
            throws IOException {
        int[][] flags = new int[COLUMN_COUNT][];

        // 1. Column lengths.
        forEachColumn(range, (x, z) -> {
            int count = readVarInt(input);
            validateColumnCount(x, z, count);
            int index = columnIndex(x, z);
            columns[index] = new long[count];
            flags[index] = new int[count];
        });

        // 2. Mapping IDs plus the has-light/discontinuity flags.
        forEachColumn(range, (x, z) -> {
            int index = columnIndex(x, z);
            long[] column = columns[index];
            int[] columnFlags = flags[index];
            for (int pointIndex = 0; pointIndex < column.length; pointIndex++) {
                int encodedId = readVarInt(input);
                if (encodedId < 0) {
                    throw new IOException("Negative Distant Horizons encoded mapping ID at ["
                            + x + "," + z + "]");
                }
                int id = encodedId >> 2;
                column[pointIndex] = id & ID_MASK;
                columnFlags[pointIndex] = encodedId & 3;
            }
        });

        // 3. Heights.
        forEachColumn(range, (x, z) -> {
            long[] column = columns[columnIndex(x, z)];
            for (int pointIndex = 0; pointIndex < column.length; pointIndex++) {
                int height = readVarInt(input);
                if (height < 0 || height > HEIGHT_MASK) {
                    throw new IOException("Invalid Distant Horizons height " + height
                            + " at [" + x + "," + z + "]");
                }
                column[pointIndex] |= (long) height << 32;
            }
        });

        // 4. Bottom Y prediction errors. DH deliberately carries the predictor
        // across column boundaries for each individual blob.
        int[] previousBottomY = new int[1];
        forEachColumn(range, (x, z) -> {
            int index = columnIndex(x, z);
            long[] column = columns[index];
            int[] columnFlags = flags[index];
            for (int pointIndex = 0; pointIndex < column.length; pointIndex++) {
                int height = getHeight(column[pointIndex]);
                int error = (columnFlags[pointIndex] & 1) != 0
                        ? zigZagDecode(readVarInt(input))
                        : 0;
                int bottomY = previousBottomY[0] - height + error;
                if (bottomY < 0 || bottomY > BOTTOM_Y_MASK || bottomY + height > 4096) {
                    throw new IOException("Invalid Distant Horizons bottom Y " + bottomY
                            + " with height " + height + " at [" + x + "," + z + "]");
                }
                column[pointIndex] |= (long) bottomY << 44;
                previousBottomY[0] = bottomY;
            }
        });

        // 5. Packed block/sky light for points whose has-light bit is set.
        forEachColumn(range, (x, z) -> {
            int index = columnIndex(x, z);
            long[] column = columns[index];
            int[] columnFlags = flags[index];
            for (int pointIndex = 0; pointIndex < column.length; pointIndex++) {
                if ((columnFlags[pointIndex] & 2) == 0) {
                    continue;
                }
                int packedLight = input.readUnsignedByte();
                int blockLight = (packedLight >>> 4) & 15;
                int skyLight = packedLight & 15;
                column[pointIndex] |= (long) skyLight << 56;
                column[pointIndex] |= (long) blockLight << 60;
            }
        });

        Arrays.fill(flags, null);
    }

    private static int readVarInt(DataInputStream input) throws IOException {
        int value = 0;
        int shift = 0;
        int current;
        do {
            if (shift >= 32) {
                throw new IOException("Invalid Distant Horizons varint");
            }
            current = input.readUnsignedByte();
            value |= (current & 127) << shift;
            shift += 7;
        } while ((current & 128) != 0);
        return value;
    }

    private static int zigZagDecode(int value) {
        return (value >>> 1) ^ -(value & 1);
    }

    private static int getHeight(long dataPoint) {
        return (int) ((dataPoint >>> 32) & HEIGHT_MASK);
    }

    static int getId(long dataPoint) {
        return (int) (dataPoint & ID_MASK);
    }

    static int getBottomY(long dataPoint) {
        return (int) ((dataPoint >>> 44) & BOTTOM_Y_MASK);
    }

    static int getSkyLight(long dataPoint) {
        return (int) ((dataPoint >>> 56) & 15);
    }

    static int getBlockLight(long dataPoint) {
        return (int) ((dataPoint >>> 60) & 15);
    }

    private static int columnIndex(int x, int z) {
        return x * WIDTH + z;
    }

    private static void validateColumnCount(int x, int z, int count) throws IOException {
        if (count < 0 || count > MAX_COLUMN_DATAPOINTS) {
            throw new IOException("Invalid Distant Horizons column length " + count
                    + " at [" + x + "," + z + "]");
        }
    }

    private static void forEachColumn(Range range, ColumnConsumer consumer) throws IOException {
        for (int x = range.minX(); x < range.maxX(); x++) {
            for (int z = range.minZ(); z < range.maxZ(); z++) {
                consumer.accept(x, z);
            }
        }
    }

    @FunctionalInterface
    private interface ColumnConsumer {
        void accept(int x, int z) throws IOException;
    }
}
