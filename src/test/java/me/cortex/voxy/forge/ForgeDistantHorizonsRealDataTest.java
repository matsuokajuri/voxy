package me.cortex.voxy.forge;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.DataInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.sql.Connection;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Set;
import java.util.jar.JarFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("compat-real-data")
class ForgeDistantHorizonsRealDataTest {
    private static final String EXACT_DH_SHA1 = "5667440fdca4d4543c345c9ba6fda2dda64928ca";

    @Test
    void currentImporterDecoderReadsRealDistantHorizonsDatabase() throws Exception {
        Path artifact = configuredPath("voxyDistantHorizonsDevJar").toRealPath();
        Path database = configuredPath("voxyDhDatabaseTestPath").toRealPath();

        assertEquals("DistantHorizons-3.2.0-b-1.20.1-fabric-forge.jar",
                artifact.getFileName().toString());
        assertEquals(EXACT_DH_SHA1, digest("SHA-1", artifact));
        try (JarFile jar = new JarFile(artifact.toFile())) {
            var metadataEntry = jar.getJarEntry("META-INF/mods.toml");
            assertNotNull(metadataEntry);
            try (InputStream input = jar.getInputStream(metadataEntry)) {
                String metadata = new String(input.readAllBytes(), StandardCharsets.UTF_8);
                assertTrue(metadata.matches("(?s).*modId\\s*=\\s*\"distanthorizons\".*"));
                assertTrue(metadata.matches("(?s).*version\\s*=\\s*\"3.2.0-b\".*"));
            }
        }

        assertTrue(Files.size(database) > 1_000_000L, "Expected a real DH database, not a schema fixture");
        try (Connection connection = ForgeOriginalVoxyDhImporter.openReadOnlyConnection(database.toFile())) {
            assertCurrentSchema(connection);
            int detailZeroRows;
            try (var statement = connection.createStatement();
                 var rows = statement.executeQuery("SELECT COUNT(*) FROM FullData WHERE DetailLevel=0")) {
                assertTrue(rows.next());
                detailZeroRows = rows.getInt(1);
            }
            assertTrue(detailZeroRows >= 32, "Expected real detail-zero DH rows");

            int decodedRows = 0;
            long decodedPoints = 0L;
            boolean sawNonZeroMappingId = false;
            try (var statement = connection.createStatement();
                 var rows = statement.executeQuery("SELECT DataFormatVersion,CompressionMode,Data,"
                         + "NorthAdjData,SouthAdjData,EastAdjData,WestAdjData,Mapping "
                         + "FROM FullData WHERE DetailLevel=0 AND Data IS NOT NULL AND Mapping IS NOT NULL "
                         + "AND NorthAdjData IS NOT NULL AND SouthAdjData IS NOT NULL "
                         + "AND EastAdjData IS NOT NULL AND WestAdjData IS NOT NULL LIMIT 32")) {
                while (rows.next()) {
                    var payload = ForgeOriginalVoxyDhImporter.readRowPayload(rows);
                    assertEquals(ForgeOriginalVoxyDhDataDecoder.FORMAT_V2, payload.format());
                    assertEquals(ForgeOriginalVoxyDhDataDecoder.COMPRESSION_ZSTD_BLOCK, payload.compression());

                    MappingSummary mapping = readMapping(payload.compression(), payload.mapping());
                    assertTrue(mapping.count() > 1);
                    assertTrue(mapping.namespaces().contains("minecraft"));
                    long[][] columns = ForgeOriginalVoxyDhDataDecoder.decode(
                            payload.format(), payload.compression(), payload.blobs());
                    assertEquals(ForgeOriginalVoxyDhDataDecoder.COLUMN_COUNT, columns.length);
                    for (long[] column : columns) {
                        for (long point : column) {
                            int id = ForgeOriginalVoxyDhDataDecoder.getId(point);
                            assertTrue(id >= 0 && id < mapping.count(),
                                    "Decoded DH mapping ID is outside the row mapping table");
                            sawNonZeroMappingId |= id != 0;
                            decodedPoints++;
                        }
                    }
                    decodedRows++;
                }
            }

            assertEquals(32, decodedRows);
            assertTrue(decodedPoints > 4096L, "Expected substantial real DH column data");
            assertTrue(sawNonZeroMappingId, "Expected non-air/non-default data in the real DH rows");
        }
    }

    private static void assertCurrentSchema(Connection connection) throws Exception {
        Set<String> columns = new HashSet<>();
        try (var statement = connection.createStatement();
             var rows = statement.executeQuery("PRAGMA table_info(FullData)")) {
            while (rows.next()) {
                columns.add(rows.getString("name"));
            }
        }
        assertTrue(columns.containsAll(Set.of(
                "DetailLevel", "PosX", "PosZ", "Data", "Mapping",
                "DataFormatVersion", "CompressionMode", "NorthAdjData",
                "SouthAdjData", "EastAdjData", "WestAdjData")));
    }

    private static MappingSummary readMapping(int compression, byte[] bytes) throws Exception {
        Set<String> namespaces = new HashSet<>();
        try (DataInputStream input = new DataInputStream(
                ForgeOriginalVoxyDhDataDecoder.openDecompressedStream(compression, bytes))) {
            int count = input.readInt();
            assertTrue(count > 0 && count <= 1_000_000);
            for (int index = 0; index < count; index++) {
                String mapping = input.readUTF();
                assertFalse(mapping.isBlank());
                int separator = mapping.indexOf(':');
                if (separator > 0) {
                    namespaces.add(mapping.substring(0, separator));
                }
            }
            return new MappingSummary(count, namespaces);
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

    private record MappingSummary(int count, Set<String> namespaces) {
    }
}
