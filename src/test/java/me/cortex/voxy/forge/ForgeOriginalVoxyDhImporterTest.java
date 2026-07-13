package me.cortex.voxy.forge;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ForgeOriginalVoxyDhImporterTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void resolvesDirectoryToOfficialDatabaseName() {
        assertEquals(
                this.temporaryDirectory.resolve("DistantHorizons.sqlite").toFile(),
                ForgeOriginalVoxyDhImporter.resolveDatabaseFile(this.temporaryDirectory.toFile()));
    }

    @Test
    void opensExistingDatabaseReadOnly() throws Exception {
        File database = this.temporaryDirectory.resolve("DistantHorizons.sqlite").toFile();
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + database.getAbsolutePath());
             var statement = connection.createStatement()) {
            statement.execute("CREATE TABLE FullData (DetailLevel INTEGER);");
            statement.execute("INSERT INTO FullData VALUES (0);");
        }

        try (var connection = ForgeOriginalVoxyDhImporter.openReadOnlyConnection(database);
             var statement = connection.createStatement();
             var resultSet = statement.executeQuery("SELECT COUNT(*) FROM FullData;")) {
            assertTrue(resultSet.next());
            assertEquals(1, resultSet.getInt(1));
            assertThrows(SQLException.class, () -> statement.execute("DELETE FROM FullData;"));
        }
    }

    @Test
    void rowQueryUsesCurrentFormatAndCompressionFromTheBlobSnapshot() throws Exception {
        File database = this.temporaryDirectory.resolve("DistantHorizons.sqlite").toFile();
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + database.getAbsolutePath());
             var statement = connection.createStatement()) {
            statement.execute("CREATE TABLE FullData ("
                    + "DetailLevel INTEGER,PosX INTEGER,PosZ INTEGER,"
                    + "CompressionMode INTEGER,DataFormatVersion INTEGER,"
                    + "Data BLOB,Mapping BLOB,NorthAdjData BLOB,SouthAdjData BLOB,"
                    + "EastAdjData BLOB,WestAdjData BLOB);");
            statement.execute("INSERT INTO FullData VALUES "
                    + "(0,4,9,3,1,X'01',X'02',NULL,NULL,NULL,NULL);");

            // Represents a row rewritten by DH after the importer scan captured
            // the old format/compression values.
            statement.execute("UPDATE FullData SET "
                    + "CompressionMode=4,DataFormatVersion=2,Data=X'11',Mapping=X'12',"
                    + "NorthAdjData=X'13',SouthAdjData=X'14',EastAdjData=X'15',WestAdjData=X'16' "
                    + "WHERE DetailLevel=0 AND PosX=4 AND PosZ=9;");

            try (var select = connection.prepareStatement(
                    ForgeOriginalVoxyDhImporter.buildRowSelectSql(true))) {
                select.setInt(1, 4);
                select.setInt(2, 9);
                try (var resultSet = select.executeQuery()) {
                    assertTrue(resultSet.next());
                    var payload = ForgeOriginalVoxyDhImporter.readRowPayload(resultSet);
                    assertEquals(2, payload.format());
                    assertEquals(4, payload.compression());
                    assertArrayEquals(new byte[]{0x11}, payload.blobs().data());
                    assertArrayEquals(new byte[]{0x13}, payload.blobs().north());
                    assertArrayEquals(new byte[]{0x16}, payload.blobs().west());
                    assertArrayEquals(new byte[]{0x12}, payload.mapping());
                }
            }
        }
    }

    @Test
    void lateInvalidMappingIdPreventsEveryRowWrite() {
        long[][] columns = new long[ForgeOriginalVoxyDhDataDecoder.COLUMN_COUNT][];
        Arrays.setAll(columns, ignored -> new long[0]);
        columns[ForgeOriginalVoxyDhDataDecoder.COLUMN_COUNT - 1] = new long[]{1L};
        AtomicInteger writes = new AtomicInteger();

        assertThrows(IOException.class, () -> ForgeOriginalVoxyDhImporter.validateThenWriteRow(
                columns,
                new long[]{0L},
                writes::incrementAndGet));
        assertEquals(0, writes.get());
    }

    @Test
    void transientSqlFailureInvalidatesAndRetriesTheSameRow() throws Exception {
        AtomicInteger attempts = new AtomicInteger();
        AtomicInteger invalidations = new AtomicInteger();

        ForgeOriginalVoxyDhImporter.runSqlWithSingleReconnect(() -> {
            if (attempts.getAndIncrement() == 0) {
                throw new SQLException("transient open failure");
            }
        }, invalidations::incrementAndGet);

        assertEquals(2, attempts.get());
        assertEquals(1, invalidations.get());
    }

    @Test
    void repeatedSqlFailureLeavesTheContextReadyForTheNextRow() throws Exception {
        AtomicInteger invalidations = new AtomicInteger();
        AtomicInteger laterAttempts = new AtomicInteger();

        SQLException failure = assertThrows(SQLException.class,
                () -> ForgeOriginalVoxyDhImporter.runSqlWithSingleReconnect(
                        () -> {
                            throw new SQLException("connection unavailable");
                        },
                        invalidations::incrementAndGet));

        assertEquals(2, invalidations.get());
        assertEquals(1, failure.getSuppressed().length);
        ForgeOriginalVoxyDhImporter.runSqlWithSingleReconnect(
                laterAttempts::incrementAndGet,
                invalidations::incrementAndGet);
        assertEquals(1, laterAttempts.get());
        assertEquals(2, invalidations.get());
    }
}
