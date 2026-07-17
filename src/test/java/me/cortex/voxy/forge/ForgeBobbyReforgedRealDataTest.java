package me.cortex.voxy.forge;

import me.cortex.voxy.common.util.MemoryBuffer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("compat-real-data")
class ForgeBobbyReforgedRealDataTest {
    private static final String EXACT_BOBBY_SHA256 =
            "4eb8296c24fa88cfc27145dc006b8acdea75d5f5175dd8b49dc5ef9709e66ee4";
    private static final Pattern REGION_NAME = Pattern.compile("r\\.(-?\\d+)\\.(-?\\d+)\\.mca");

    @Test
    void currentImporterDecoderReadsRealBobbyReforgedCache() throws Exception {
        Path artifact = configuredPath("voxyBobbyDevJar").toRealPath();
        Path cache = configuredPath("voxyBobbyCacheTestPath").toRealPath();

        assertEquals("bobby-1.20.1_v5.0.1.jar", artifact.getFileName().toString());
        assertEquals(EXACT_BOBBY_SHA256, digest("SHA-256", artifact));
        assertTrue(Files.isDirectory(cache));

        List<Path> regions;
        try (var files = Files.list(cache)) {
            regions = files.filter(path -> REGION_NAME.matcher(path.getFileName().toString()).matches())
                    .sorted()
                    .toList();
        }
        assertTrue(regions.size() >= 4, "Expected several real Bobby region files");

        Counters counters = new Counters();
        for (Path region : regions) {
            if (Files.size(region) == 0L) {
                continue;
            }
            inspectRegion(region, counters);
        }

        assertTrue(counters.nonEmptyRegions >= 4, "Expected non-empty Bobby region files");
        assertTrue(counters.allocatedChunks >= 128, "Expected a real multi-region Bobby cache");
        assertEquals(counters.allocatedChunks, counters.decodedChunks,
                "Every allocated Bobby chunk must decode through Voxy's production decompressor");
        assertEquals(0, counters.externalChunks, "External Anvil chunk streams are unsupported by Voxy");
        assertEquals(0, counters.regionMismatches, "Bobby chunks must be stored in their declared regions");
        assertTrue(counters.fullChunks >= 128, "Expected real FULL chunks rather than an empty fixture");
        assertTrue(counters.blockStateSections > 0);
        assertTrue(counters.nonAirPaletteEntries > 0);
    }

    private static void inspectRegion(Path path, Counters counters) throws Exception {
        Matcher matcher = REGION_NAME.matcher(path.getFileName().toString());
        assertTrue(matcher.matches());
        int regionX = Integer.parseInt(matcher.group(1));
        int regionZ = Integer.parseInt(matcher.group(2));

        try (FileChannel channel = FileChannel.open(path, StandardOpenOption.READ)) {
            long size = channel.size();
            assertTrue(size >= 8192L, path.toString());
            ByteBuffer header = ByteBuffer.allocate(8192);
            readFully(channel, header, 0L);
            header.flip();
            counters.nonEmptyRegions++;

            for (int index = 0; index < 1024; index++) {
                int location = header.getInt(index * Integer.BYTES);
                if (location == 0) {
                    continue;
                }
                counters.allocatedChunks++;
                int sectorStart = location >>> 8;
                int sectorCount = location & 0xFF;
                assertTrue(sectorStart >= 2 && sectorCount > 0, path + " header slot " + index);

                long chunkOffset = (long) sectorStart * 4096L;
                ByteBuffer prefix = ByteBuffer.allocate(5);
                readFully(channel, prefix, chunkOffset);
                prefix.flip();
                int streamLength = prefix.getInt();
                byte flags = prefix.get();
                int payloadLength = streamLength - 1;
                assertTrue(payloadLength >= 0 && payloadLength <= sectorCount * 4096 - 5,
                        path + " invalid payload length at slot " + index);
                assertTrue(chunkOffset + 5L + payloadLength <= size,
                        path + " truncated payload at slot " + index);
                if ((flags & 0x80) != 0) {
                    counters.externalChunks++;
                    continue;
                }

                ByteBuffer payload = ByteBuffer.allocate(payloadLength);
                readFully(channel, payload, chunkOffset + 5L);
                MemoryBuffer nativePayload = new MemoryBuffer(payloadLength);
                try {
                    payload.flip();
                    nativePayload.asByteBuffer().put(payload).flip();
                    try (DataInputStream input = WorldImporter.decompress(flags, nativePayload)) {
                        assertFalse(input == null, "Unsupported Anvil compression " + flags);
                        inspectChunk(NbtIo.read(input), regionX, regionZ, counters);
                    }
                } finally {
                    nativePayload.free();
                }
                counters.decodedChunks++;
            }
        }
    }

    private static void inspectChunk(CompoundTag chunk, int regionX, int regionZ, Counters counters) {
        String status = chunk.getString("Status");
        if (status.equals("full") || status.equals("minecraft:full")) {
            counters.fullChunks++;
        }
        int chunkX = WorldImporter.getIntOrSentinel(chunk, "xPos");
        int chunkZ = WorldImporter.getIntOrSentinel(chunk, "zPos");
        if ((chunkX >> 5) != regionX || (chunkZ >> 5) != regionZ) {
            counters.regionMismatches++;
        }

        ListTag sections = WorldImporter.requireList(chunk, "sections");
        for (net.minecraft.nbt.Tag value : sections) {
            CompoundTag section = (CompoundTag) value;
            if (!section.contains("block_states", net.minecraft.nbt.Tag.TAG_COMPOUND)) {
                continue;
            }
            counters.blockStateSections++;
            ListTag palette = section.getCompound("block_states")
                    .getList("palette", net.minecraft.nbt.Tag.TAG_COMPOUND);
            for (net.minecraft.nbt.Tag paletteValue : palette) {
                String name = ((CompoundTag) paletteValue).getString("Name");
                if (!name.isBlank() && !name.equals("minecraft:air")) {
                    counters.nonAirPaletteEntries++;
                }
            }
        }
    }

    private static void readFully(FileChannel channel, ByteBuffer target, long offset) throws IOException {
        while (target.hasRemaining()) {
            int read = channel.read(target, offset + target.position());
            if (read < 0) {
                throw new IOException("Unexpected end of region file");
            }
        }
    }

    private static Path configuredPath(String name) {
        String value = System.getProperty(name, "");
        assertFalse(value.isBlank(), name);
        return Path.of(value);
    }

    private static String digest(String algorithm, Path path) throws Exception {
        MessageDigest digest = MessageDigest.getInstance(algorithm);
        try (InputStream input = Files.newInputStream(path)) {
            byte[] bytes = new byte[8192];
            int read;
            while ((read = input.read(bytes)) >= 0) {
                digest.update(bytes, 0, read);
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static final class Counters {
        int nonEmptyRegions;
        int allocatedChunks;
        int decodedChunks;
        int externalChunks;
        int regionMismatches;
        int fullChunks;
        int blockStateSections;
        int nonAirPaletteEntries;
    }
}
