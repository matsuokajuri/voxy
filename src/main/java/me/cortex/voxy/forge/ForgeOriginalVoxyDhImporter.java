package me.cortex.voxy.forge;

import me.cortex.voxy.common.Logger;
import me.cortex.voxy.common.thread.Service;
import me.cortex.voxy.common.thread.ServiceManager;
import me.cortex.voxy.common.util.Pair;
import me.cortex.voxy.common.voxelization.VoxelizedSection;
import me.cortex.voxy.common.voxelization.WorldVoxilizedSectionMipper;
import me.cortex.voxy.common.world.WorldEngine;
import me.cortex.voxy.common.world.WorldUpdater;
import me.cortex.voxy.common.world.other.Mapper;
import me.cortex.voxy.commonImpl.importers.IDataImporter;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.sqlite.SQLiteConfig;

import java.io.DataInputStream;
import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

/**
 * Forge 1.20.1 adapter for original Voxy's Distant Horizons SQLite importer.
 *
 * <p>The format-1 mapping/voxelization/update path is kept from original Voxy.
 * The database adapter additionally understands DH 3.2.0 format 2 and official
 * compression modes 0 through 4.</p>
 */
public final class ForgeOriginalVoxyDhImporter implements IDataImporter {
    public static final boolean HasRequiredLibraries;

    static {
        HasRequiredLibraries = detectRequiredLibraries(name -> Class.forName(name));
        if (!HasRequiredLibraries) {
            Logger.warn("Distant Horizons import disabled because SQLite JDBC or XZ is unavailable");
        }
    }

    private static final String DATABASE_FILE_NAME = "DistantHorizons.sqlite";
    private static final int CHUNKS_PER_DH_ROW = 4 * 4;
    private static final int MAX_QUEUED_ROWS = 100;
    private static final int FAILURE_SAMPLE_LIMIT = 5;
    private static final int MAX_MAPPING_ENTRIES = 1_000_000;

    private static final Set<String> REQUIRED_COLUMNS = Set.of(
            "detaillevel",
            "posx",
            "posz",
            "compressionmode",
            "dataformatversion",
            "data",
            "mapping");
    private static final Set<String> ADJACENT_COLUMNS = Set.of(
            "northadjdata",
            "southadjdata",
            "eastadjdata",
            "westadjdata");

    private final File databaseFile;
    private final WorldEngine engine;
    private final Registry<Biome> biomeRegistry;
    private final Registry<Block> blockRegistry;
    private final Holder.Reference<Biome> defaultBiome;
    private final int bottomOfWorld;
    private final int worldHeightBlocks;
    private final int worldHeightSections;

    private final ConcurrentLinkedDeque<Task> workQueue = new ConcurrentLinkedDeque<>();
    private final AtomicInteger outstandingRows = new AtomicInteger();
    private final AtomicInteger processedChunks = new AtomicInteger();
    private final AtomicInteger failedRows = new AtomicInteger();
    private final ConcurrentLinkedQueue<String> failureSamples = new ConcurrentLinkedQueue<>();

    private final AtomicBoolean started = new AtomicBoolean();
    private final AtomicBoolean stopRequested = new AtomicBoolean();
    private final AtomicBoolean cleanupStarted = new AtomicBoolean();
    private final AtomicBoolean engineRefHeld = new AtomicBoolean();
    private final AtomicBoolean completionSent = new AtomicBoolean();

    private final Service service;
    private volatile String rowSelectSql;
    private volatile boolean running;
    private volatile Thread runner;
    private volatile int totalChunks;
    private IUpdateCallback updateCallback;
    private ICompletionCallback completionCallback;

    private record Task(int x, int z) {
        long distanceFromZero() {
            return (long) this.x * this.x + (long) this.z * this.z;
        }
    }

    record RowPayload(
            int format,
            int compression,
            ForgeOriginalVoxyDhDataDecoder.Blobs blobs,
            byte[] mapping) {
    }

    @FunctionalInterface
    interface RowWriter {
        void write() throws IOException;
    }

    @FunctionalInterface
    interface SqlOperation {
        void run() throws SQLException, IOException;
    }

    @FunctionalInterface
    interface RequiredLibraryLoader {
        void load(String className) throws ClassNotFoundException;
    }

    static boolean detectRequiredLibraries(RequiredLibraryLoader loader) {
        try {
            loader.load("org.sqlite.JDBC");
            loader.load("org.tukaani.xz.XZInputStream");
            return true;
        } catch (ClassNotFoundException | NoClassDefFoundError exception) {
            return false;
        }
    }

    private record ScanResult(
            Queue<Task> tasks,
            int scannedRows,
            Map<Integer, Integer> unsupportedFormats,
            Map<Integer, Integer> unsupportedCompressions,
            int format2RowsMissingAdjacentColumns,
            boolean hasAdjacentColumns) {
        int skippedRows() {
            return this.scannedRows - this.tasks.size();
        }
    }

    private final class WorkContext {
        private Connection connection;
        private PreparedStatement statement;
        private final long[] storage = new long[16 * ForgeOriginalVoxyDhDataDecoder.WIDTH
                * ForgeOriginalVoxyDhImporter.this.worldHeightSections * 16];
        private final VoxelizedSection section = VoxelizedSection.createEmpty();

        private WorkContext() {
        }

        private PreparedStatement statement() throws SQLException {
            if (this.statement != null
                    && this.connection != null
                    && !this.connection.isClosed()) {
                return this.statement;
            }

            this.invalidateConnection();
            try {
                String sql = ForgeOriginalVoxyDhImporter.this.rowSelectSql;
                if (sql == null) {
                    throw new IllegalStateException("Distant Horizons row query was not initialized");
                }
                this.connection = ForgeOriginalVoxyDhImporter.this.openReadOnlyConnection();
                this.statement = this.connection.prepareStatement(sql);
                return this.statement;
            } catch (SQLException | RuntimeException exception) {
                this.invalidateConnection();
                throw exception;
            }
        }

        private void invalidateConnection() {
            closeQuietly(this.statement);
            closeQuietly(this.connection);
            this.statement = null;
            this.connection = null;
        }

        private void close() {
            this.invalidateConnection();
        }
    }

    public ForgeOriginalVoxyDhImporter(
            File file,
            WorldEngine worldEngine,
            Level mcWorld,
            ServiceManager serviceManager,
            BooleanSupplier runChecker) {
        this.databaseFile = resolveDatabaseFile(file);
        this.engine = worldEngine;
        this.biomeRegistry = mcWorld.registryAccess().registryOrThrow(Registries.BIOME);
        this.blockRegistry = mcWorld.registryAccess().registryOrThrow(Registries.BLOCK);
        this.defaultBiome = this.biomeRegistry.getHolderOrThrow(Biomes.PLAINS);
        this.bottomOfWorld = mcWorld.getMinBuildHeight();
        this.worldHeightBlocks = mcWorld.getHeight();
        this.worldHeightSections = (this.worldHeightBlocks + 15) / 16;

        this.service = serviceManager.createService(() -> {
            WorkContext context = new WorkContext();
            return new Pair<>(() -> this.executeNext(context), context::close);
        }, 10, "DH Importer", runChecker);
    }

    public static File resolveDatabaseFile(File file) {
        if (file != null && file.isDirectory()) {
            return new File(file, DATABASE_FILE_NAME);
        }
        return file;
    }

    @Override
    public void runImport(IUpdateCallback updateCallback, ICompletionCallback completionCallback) {
        if (!this.started.compareAndSet(false, true)) {
            throw new IllegalStateException("Distant Horizons importer can only be run once");
        }
        if (this.databaseFile == null || !this.databaseFile.isFile()) {
            this.cleanupResources();
            completionCallback.onCompletion(0);
            return;
        }

        this.updateCallback = updateCallback;
        this.completionCallback = completionCallback;
        this.engine.acquireRef();
        this.engineRefHeld.set(true);
        this.running = true;

        Thread thread = new Thread(this::runWorker, "Distant Horizons importer");
        thread.setDaemon(true);
        this.runner = thread;
        try {
            thread.start();
        } catch (RuntimeException exception) {
            this.running = false;
            this.runner = null;
            this.cleanupResources();
            throw exception;
        }
    }

    @Override
    public WorldEngine getEngine() {
        return this.engine;
    }

    @Override
    public void shutdown() {
        this.stopRequested.set(true);
        this.drainQueuedRows();

        Thread thread = this.runner;
        if (thread == null) {
            this.running = false;
            this.cleanupResources();
            return;
        }
        if (thread == Thread.currentThread()) {
            return;
        }

        thread.interrupt();
        boolean interrupted = false;
        while (thread.isAlive()) {
            try {
                thread.join();
            } catch (InterruptedException exception) {
                interrupted = true;
            }
        }
        if (interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public boolean isRunning() {
        return this.running;
    }

    private void runWorker() {
        ScanResult scan = null;
        Throwable fatalFailure = null;
        try (Connection scanConnection = this.openReadOnlyConnection()) {
            scan = this.scanTasks(scanConnection);
            this.totalChunks = Math.multiplyExact(scan.tasks().size(), CHUNKS_PER_DH_ROW);
            this.logScanSummary(scan);

            while (!this.stopRequested.get() && !scan.tasks().isEmpty()) {
                Task task = scan.tasks().poll();
                this.workQueue.add(task);
                this.outstandingRows.incrementAndGet();
                this.service.execute();

                while (!this.stopRequested.get() && this.workQueue.size() > MAX_QUEUED_ROWS) {
                    Thread.sleep(10L);
                }
            }
        } catch (InterruptedException exception) {
            if (!this.stopRequested.get()) {
                fatalFailure = exception;
            }
            this.stopRequested.set(true);
        } catch (Throwable throwable) {
            fatalFailure = throwable;
            this.stopRequested.set(true);
        } finally {
            if (this.stopRequested.get()) {
                this.drainQueuedRows();
            }
            this.awaitOutstandingRows();

            if (fatalFailure != null) {
                Logger.error("Distant Horizons import failed for", this.databaseFile.getAbsolutePath(), fatalFailure);
            }
            this.logCompletionSummary(scan, fatalFailure);
            this.running = false;
            this.runner = null;
            this.cleanupResources();
            this.sendCompletion();
        }
    }

    private ScanResult scanTasks(Connection connection) throws SQLException {
        Set<String> columns = new HashSet<>();
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("PRAGMA table_info(FullData);")) {
            while (resultSet.next()) {
                columns.add(resultSet.getString("name").toLowerCase(Locale.ROOT));
            }
        }
        if (!columns.containsAll(REQUIRED_COLUMNS)) {
            Set<String> missing = new HashSet<>(REQUIRED_COLUMNS);
            missing.removeAll(columns);
            throw new SQLException("Distant Horizons FullData table is missing columns " + missing);
        }

        boolean hasAdjacentColumns = columns.containsAll(ADJACENT_COLUMNS);
        this.rowSelectSql = buildRowSelectSql(hasAdjacentColumns);

        Queue<Task> tasks = new PriorityQueue<>(Comparator.comparingLong(Task::distanceFromZero));
        Map<Integer, Integer> unsupportedFormats = new TreeMap<>();
        Map<Integer, Integer> unsupportedCompressions = new TreeMap<>();
        int scannedRows = 0;
        int missingAdjacentRows = 0;
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(
                     "SELECT PosX,PosZ,CompressionMode,DataFormatVersion "
                             + "FROM FullData WHERE DetailLevel = 0;")) {
            while (resultSet.next()) {
                scannedRows++;
                int x = resultSet.getInt("PosX");
                int z = resultSet.getInt("PosZ");
                int compression = resultSet.getInt("CompressionMode");
                int format = resultSet.getInt("DataFormatVersion");

                boolean supported = true;
                if (!ForgeOriginalVoxyDhDataDecoder.supportsFormat(format)) {
                    unsupportedFormats.merge(format, 1, Integer::sum);
                    supported = false;
                }
                if (!ForgeOriginalVoxyDhDataDecoder.supportsCompression(compression)) {
                    unsupportedCompressions.merge(compression, 1, Integer::sum);
                    supported = false;
                }
                if (format == ForgeOriginalVoxyDhDataDecoder.FORMAT_V2 && !hasAdjacentColumns) {
                    missingAdjacentRows++;
                    supported = false;
                }
                if (supported) {
                    tasks.add(new Task(x, z));
                }
            }
        }
        return new ScanResult(
                tasks,
                scannedRows,
                unsupportedFormats,
                unsupportedCompressions,
                missingAdjacentRows,
                hasAdjacentColumns);
    }

    private void executeNext(WorkContext context) {
        Task task = this.workQueue.poll();
        if (task == null) {
            Logger.error("Distant Horizons importer service ran without a queued row");
            return;
        }

        try {
            if (this.stopRequested.get()) {
                return;
            }
            runSqlWithSingleReconnect(
                    () -> this.importTask(context, task),
                    context::invalidateConnection);
        } catch (Exception exception) {
            this.recordRowFailure(task, exception);
        } finally {
            this.decrementOutstandingRows();
        }
    }

    private void importTask(WorkContext context, Task task) throws SQLException, IOException {
        PreparedStatement statement = context.statement();
        statement.setInt(1, task.x());
        statement.setInt(2, task.z());
        RowPayload payload;
        try (ResultSet resultSet = statement.executeQuery()) {
            if (!resultSet.next()) {
                throw new SQLException("Distant Horizons row disappeared during import");
            }
            payload = readRowPayload(resultSet);
        }

        if (!ForgeOriginalVoxyDhDataDecoder.supportsFormat(payload.format())) {
            throw new IOException("Unsupported Distant Horizons data format " + payload.format());
        }
        if (!ForgeOriginalVoxyDhDataDecoder.supportsCompression(payload.compression())) {
            throw new IOException("Unsupported Distant Horizons compression mode "
                    + payload.compression());
        }

        long[] mappings = this.readMappings(payload.compression(), payload.mapping());
        long[][] columns = ForgeOriginalVoxyDhDataDecoder.decode(
                payload.format(),
                payload.compression(),
                payload.blobs());
        validateThenWriteRow(columns, mappings, () -> this.writeColumns(task, context, columns, mappings));
    }

    static String buildRowSelectSql(boolean hasAdjacentColumns) {
        String adjacentSelection = hasAdjacentColumns
                ? "NorthAdjData,SouthAdjData,EastAdjData,WestAdjData"
                : "NULL AS NorthAdjData,NULL AS SouthAdjData,"
                + "NULL AS EastAdjData,NULL AS WestAdjData";
        return "SELECT Data,Mapping,CompressionMode,DataFormatVersion,"
                + adjacentSelection
                + " FROM FullData WHERE DetailLevel = 0 AND PosX = ? AND PosZ = ?;";
    }

    static RowPayload readRowPayload(ResultSet resultSet) throws SQLException {
        int format = resultSet.getInt("DataFormatVersion");
        int compression = resultSet.getInt("CompressionMode");
        byte[] data = resultSet.getBytes("Data");
        ForgeOriginalVoxyDhDataDecoder.Blobs blobs = format == ForgeOriginalVoxyDhDataDecoder.FORMAT_V2
                ? new ForgeOriginalVoxyDhDataDecoder.Blobs(
                        data,
                        resultSet.getBytes("NorthAdjData"),
                        resultSet.getBytes("SouthAdjData"),
                        resultSet.getBytes("EastAdjData"),
                        resultSet.getBytes("WestAdjData"))
                : new ForgeOriginalVoxyDhDataDecoder.Blobs(data, null, null, null, null);
        return new RowPayload(format, compression, blobs, resultSet.getBytes("Mapping"));
    }

    static void runSqlWithSingleReconnect(SqlOperation operation, Runnable invalidateConnection)
            throws SQLException, IOException {
        SQLException firstFailure;
        try {
            operation.run();
            return;
        } catch (SQLException exception) {
            firstFailure = exception;
            invalidateConnection.run();
        }

        try {
            operation.run();
        } catch (SQLException exception) {
            invalidateConnection.run();
            exception.addSuppressed(firstFailure);
            throw exception;
        }
    }

    static void validateThenWriteRow(long[][] columns, long[] mappings, RowWriter writer)
            throws IOException {
        if (columns == null || columns.length != ForgeOriginalVoxyDhDataDecoder.COLUMN_COUNT) {
            throw new IOException("Invalid Distant Horizons column array");
        }
        if (mappings == null) {
            throw new IOException("Distant Horizons mapping array is missing");
        }
        for (int columnIndex = 0; columnIndex < columns.length; columnIndex++) {
            long[] column = columns[columnIndex];
            if (column == null) {
                throw new IOException("Distant Horizons column " + columnIndex + " is missing");
            }
            for (long dataPoint : column) {
                int mappingId = ForgeOriginalVoxyDhDataDecoder.getId(dataPoint);
                if (mappingId < 0 || mappingId >= mappings.length) {
                    throw new IOException("Distant Horizons mapping ID " + mappingId
                            + " is outside mapping size " + mappings.length);
                }
            }
        }
        writer.write();
    }

    private long[] readMappings(int compression, byte[] mappingData) throws IOException {
        if (mappingData == null) {
            throw new IOException("Distant Horizons mapping blob is missing");
        }
        try (DataInputStream stream = new DataInputStream(
                ForgeOriginalVoxyDhDataDecoder.openDecompressedStream(compression, mappingData))) {
            int entries = stream.readInt();
            if (entries < 0 || entries > MAX_MAPPING_ENTRIES) {
                throw new IOException("Invalid Distant Horizons mapping count " + entries);
            }
            long[] mappings = new long[entries];
            for (int index = 0; index < entries; index++) {
                mappings[index] = this.decodeMappingEntry(stream.readUTF());
            }
            return mappings;
        }
    }

    private long decodeMappingEntry(String encodedEntry) throws IOException {
        final String blockStateSeparator = "_DH-BSW_";
        final String stateSeparator = "_STATE_";
        int separatorIndex = encodedEntry.indexOf(blockStateSeparator);
        if (separatorIndex < 0) {
            throw new IOException("Distant Horizons mapping entry has no block-state separator");
        }

        int biomeId;
        ResourceLocation biomeLocation = ResourceLocation.tryParse(encodedEntry.substring(0, separatorIndex));
        Holder<Biome> biome = this.defaultBiome;
        if (biomeLocation != null) {
            ResourceKey<Biome> biomeKey = ResourceKey.create(Registries.BIOME, biomeLocation);
            biome = this.biomeRegistry.getHolder(biomeKey).orElse(this.defaultBiome);
        }
        biomeId = this.engine.getMapper().getIdForBiome(biome);

        int blockStart = separatorIndex + blockStateSeparator.length();
        String blockEncoding = encodedEntry.substring(blockStart);
        int blockId;
        if (blockEncoding.equals("AIR")) {
            blockId = 0;
        } else {
            int stateIndex = encodedEntry.indexOf(stateSeparator, blockStart);
            String stateEncoding = stateIndex >= 0
                    ? encodedEntry.substring(stateIndex + stateSeparator.length())
                    : null;
            String blockLocationText = encodedEntry.substring(
                    blockStart,
                    stateIndex >= 0 ? stateIndex : encodedEntry.length());
            ResourceLocation blockLocation = ResourceLocation.tryParse(blockLocationText);
            Block block = blockLocation == null
                    ? Blocks.AIR
                    : this.blockRegistry.getOptional(blockLocation).orElse(Blocks.AIR);
            BlockState state = block.defaultBlockState();
            if (stateEncoding != null && block != Blocks.AIR) {
                BlockState matchedState = null;
                for (BlockState possibleState : block.getStateDefinition().getPossibleStates()) {
                    if (getSerializedBlockState(possibleState).equals(stateEncoding)) {
                        matchedState = possibleState;
                        break;
                    }
                }
                if (matchedState != null) {
                    state = matchedState;
                } else {
                    Logger.warn("Could not find Distant Horizons block state", blockEncoding);
                }
            }
            if (block == Blocks.AIR && !blockLocationText.equals("minecraft:air")) {
                Logger.warn("Could not find Distant Horizons block", blockLocationText);
            }
            blockId = this.engine.getMapper().getIdForBlockState(state);
        }
        return Mapper.composeMappingId((byte) 0, blockId, biomeId);
    }

    private static String getSerializedBlockState(BlockState state) {
        var properties = new ArrayList<>(state.getProperties());
        properties.sort(Comparator.comparing(property -> property.getName()));
        StringBuilder builder = new StringBuilder();
        for (var property : properties) {
            String value = state.hasProperty(property) ? state.getValue(property).toString() : "NULL";
            builder.append('{').append(property.getName()).append(':').append(value).append('}');
        }
        return builder.toString();
    }

    private void writeColumns(Task task, WorkContext context, long[][] columns, long[] mappings) {
        long[] storage = context.storage;
        VoxelizedSection section = context.section;
        for (int x = 0; x < ForgeOriginalVoxyDhDataDecoder.WIDTH; x++) {
            int localX = x & 15;
            for (int z = 0; z < ForgeOriginalVoxyDhDataDecoder.WIDTH; z++) {
                long[] column = columns[x * ForgeOriginalVoxyDhDataDecoder.WIDTH + z];
                for (long dataPoint : column) {
                    int mappingId = ForgeOriginalVoxyDhDataDecoder.getId(dataPoint);
                    int light = (ForgeOriginalVoxyDhDataDecoder.getBlockLight(dataPoint) << 4)
                            | ForgeOriginalVoxyDhDataDecoder.getSkyLight(dataPoint);
                    long mapped = Mapper.withLight(mappings[mappingId], light);
                    int startY = ForgeOriginalVoxyDhDataDecoder.getBottomY(dataPoint);
                    int endY = Math.min(
                            startY + getHeight(dataPoint),
                            this.worldHeightBlocks);
                    startY = Math.max(startY, 0);
                    for (int y = startY; y < endY; y++) {
                        storage[((y * ForgeOriginalVoxyDhDataDecoder.WIDTH + z) * 16) + localX] = mapped;
                    }
                }
            }

            if (localX == 15) {
                int chunkX = task.x() * 4 + (x >> 4);
                for (int sectionZ = 0; sectionZ < 4; sectionZ++) {
                    for (int sectionY = 0; sectionY < this.worldHeightSections; sectionY++) {
                        int nonAirCount = 0;
                        long[] sectionData = section.section;
                        for (int localY = 0; localY < 16; localY++) {
                            int worldRelativeY = sectionY * 16 + localY;
                            for (int localZ = 0; localZ < 16; localZ++) {
                                int sourceZ = sectionZ * 16 + localZ;
                                int sourceBase = ((worldRelativeY
                                        * ForgeOriginalVoxyDhDataDecoder.WIDTH + sourceZ) * 16);
                                int destinationBase = (localY << 8) | (localZ << 4);
                                for (int sectionX = 0; sectionX < 16; sectionX++) {
                                    long value = storage[sourceBase + sectionX];
                                    sectionData[destinationBase + sectionX] = value;
                                    nonAirCount += Mapper.isAir(value) ? 0 : 1;
                                }
                            }
                        }
                        section.lvl0NonAirCount = nonAirCount;
                        WorldVoxilizedSectionMipper.mipSection(section, this.engine.getMapper());
                        section.setPosition(
                                chunkX,
                                sectionY + (this.bottomOfWorld >> 4),
                                task.z() * 4 + sectionZ);
                        WorldUpdater.insertUpdate(this.engine, section);
                    }

                    int count = this.processedChunks.incrementAndGet();
                    this.updateCallback.onUpdate(count, this.totalChunks);
                }
                Arrays.fill(storage, 0L);
            }
        }
    }

    private static int getHeight(long dataPoint) {
        return (int) ((dataPoint >>> 32) & ((1 << 12) - 1));
    }

    private Connection openReadOnlyConnection() throws SQLException {
        return openReadOnlyConnection(this.databaseFile);
    }

    static Connection openReadOnlyConnection(File databaseFile) throws SQLException {
        SQLiteConfig configuration = new SQLiteConfig();
        configuration.setReadOnly(true);
        configuration.setBusyTimeout(5_000);
        return DriverManager.getConnection(
                "jdbc:sqlite:" + databaseFile.getAbsolutePath(),
                configuration.toProperties());
    }

    private void recordRowFailure(Task task, Exception exception) {
        int failureCount = this.failedRows.incrementAndGet();
        if (failureCount <= FAILURE_SAMPLE_LIMIT) {
            String message = exception.getMessage();
            this.failureSamples.add("[" + task.x() + "," + task.z() + "] "
                    + exception.getClass().getSimpleName()
                    + (message == null ? "" : ": " + message));
        }
    }

    private void logScanSummary(ScanResult scan) {
        Logger.info(
                "Distant Horizons import scan:",
                "file=" + this.databaseFile.getAbsolutePath(),
                "rows=" + scan.scannedRows(),
                "accepted=" + scan.tasks().size(),
                "skipped=" + scan.skippedRows(),
                "adjacentColumns=" + scan.hasAdjacentColumns());
        if (scan.skippedRows() != 0) {
            Logger.warn(
                    "Distant Horizons import skipped unsupported rows:",
                    "unsupportedDataFormats=" + scan.unsupportedFormats(),
                    "unsupportedCompressionModes=" + scan.unsupportedCompressions(),
                    "format2RowsMissingAdjacentColumns=" + scan.format2RowsMissingAdjacentColumns());
        }
    }

    private void logCompletionSummary(ScanResult scan, Throwable fatalFailure) {
        int remainingRows = scan == null ? 0 : scan.tasks().size();
        String summary = "file=" + this.databaseFile.getAbsolutePath()
                + " chunks=" + this.processedChunks.get()
                + " failedRows=" + this.failedRows.get()
                + " unscheduledRows=" + remainingRows
                + " cancelled=" + this.stopRequested.get()
                + " fatal=" + (fatalFailure != null);
        if (this.failedRows.get() != 0) {
            Logger.warn("Distant Horizons import completed with row errors:", summary,
                    "samples=" + this.failureSamples);
        } else {
            Logger.info("Distant Horizons import completed:", summary);
        }
    }

    private void awaitOutstandingRows() {
        boolean interrupted = false;
        while (this.outstandingRows.get() != 0) {
            if (this.stopRequested.get()) {
                this.drainQueuedRows();
            }
            try {
                Thread.sleep(10L);
            } catch (InterruptedException exception) {
                interrupted = true;
                this.stopRequested.set(true);
            }
        }
        if (interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private void drainQueuedRows() {
        if (!this.service.isLive()) {
            return;
        }
        int drained = this.service.drain();
        for (int index = 0; index < drained; index++) {
            Task task = this.workQueue.poll();
            if (task == null) {
                Logger.error("Distant Horizons importer drained a service job without a queued row");
                break;
            }
            this.decrementOutstandingRows();
        }
    }

    private void decrementOutstandingRows() {
        int remaining = this.outstandingRows.decrementAndGet();
        if (remaining < 0) {
            throw new IllegalStateException("Distant Horizons outstanding row count became negative");
        }
    }

    private void cleanupResources() {
        if (!this.cleanupStarted.compareAndSet(false, true)) {
            return;
        }
        if (this.service.isLive()) {
            try {
                this.service.shutdown();
            } catch (RuntimeException exception) {
                Logger.error("Failed to shut down Distant Horizons importer service", exception);
            }
        }
        if (this.engineRefHeld.compareAndSet(true, false)) {
            this.engine.releaseRef();
        }
    }

    private void sendCompletion() {
        ICompletionCallback callback = this.completionCallback;
        this.updateCallback = null;
        this.completionCallback = null;
        if (callback != null && this.completionSent.compareAndSet(false, true)) {
            callback.onCompletion(this.processedChunks.get());
        }
    }

    private static void closeQuietly(AutoCloseable closeable) {
        if (closeable == null) {
            return;
        }
        try {
            closeable.close();
        } catch (Exception exception) {
            Logger.warn("Failed to close Distant Horizons import resource", exception);
        }
    }
}
