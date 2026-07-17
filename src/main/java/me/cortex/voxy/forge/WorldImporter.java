package me.cortex.voxy.forge;

import com.mojang.serialization.Codec;
import me.cortex.voxy.common.Logger;
import me.cortex.voxy.common.thread.Service;
import me.cortex.voxy.common.thread.ServiceManager;
import me.cortex.voxy.common.util.MemoryBuffer;
import me.cortex.voxy.common.util.Pair;
import me.cortex.voxy.common.util.UnsafeUtil;
import me.cortex.voxy.common.voxelization.VoxelizedSection;
import me.cortex.voxy.common.voxelization.WorldConversionFactory;
import me.cortex.voxy.common.voxelization.WorldVoxilizedSectionMipper;
import me.cortex.voxy.common.world.WorldEngine;
import me.cortex.voxy.common.world.WorldUpdater;
import me.cortex.voxy.commonImpl.importers.IDataImporter;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.chunk.PalettedContainerRO;
import net.minecraft.world.level.chunk.storage.RegionFileVersion;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.lwjgl.system.MemoryUtil;

import java.io.DataInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

/**
 * Forge 1.20.1 API adapter for original Voxy's {@code WorldImporter}.
 *
 * <p>The region-sector parser, weighted service, queue pressure, NBT conversion,
 * mip generation and WorldUpdater insertion remain the original implementation.
 * Only the Minecraft 1.20.1 palette/registry/NBT APIs differ from upstream.</p>
 */
public final class WorldImporter implements IDataImporter {
    private final WorldEngine world;
    private final PalettedContainerRO<Holder<Biome>> defaultBiomeProvider;
    private final Codec<PalettedContainerRO<Holder<Biome>>> biomeCodec;
    private final Codec<PalettedContainer<BlockState>> blockStateCodec;
    private final AtomicInteger estimatedTotalChunks = new AtomicInteger();
    private final AtomicInteger totalChunks = new AtomicInteger();
    private final AtomicInteger chunksProcessed = new AtomicInteger();

    private final ConcurrentLinkedDeque<Runnable> jobQueue = new ConcurrentLinkedDeque<>();
    private final Service service;

    private volatile boolean isRunning;
    private final AtomicBoolean isShutdown = new AtomicBoolean();
    private volatile Thread worker;
    private IUpdateCallback updateCallback;
    private ICompletionCallback completionCallback;

    public WorldImporter(
            WorldEngine worldEngine,
            Level mcWorld,
            ServiceManager serviceManager,
            BooleanSupplier runChecker) {
        this.world = worldEngine;
        this.service = serviceManager.createService(
                () -> new Pair<>(() -> this.jobQueue.poll().run(), () -> {}),
                3,
                "World importer",
                runChecker);

        Registry<Biome> biomeRegistry = mcWorld.registryAccess().registryOrThrow(Registries.BIOME);
        Holder<Biome> defaultBiome = biomeRegistry.getHolderOrThrow(Biomes.PLAINS);
        this.defaultBiomeProvider = new PalettedContainer<>(
                biomeRegistry.asHolderIdMap(),
                defaultBiome,
                PalettedContainer.Strategy.SECTION_BIOMES);
        this.biomeCodec = PalettedContainer.codecRO(
                biomeRegistry.asHolderIdMap(),
                biomeRegistry.holderByNameCodec(),
                PalettedContainer.Strategy.SECTION_BIOMES,
                defaultBiome);
        this.blockStateCodec = PalettedContainer.codecRW(
                Block.BLOCK_STATE_REGISTRY,
                BlockState.CODEC,
                PalettedContainer.Strategy.SECTION_STATES,
                Blocks.AIR.defaultBlockState());
    }

    @Override
    public void runImport(IUpdateCallback updateCallback, ICompletionCallback completionCallback) {
        if (this.isRunning) {
            throw new IllegalStateException();
        }
        if (this.worker == null) {
            completionCallback.onCompletion(0);
            return;
        }
        this.isRunning = true;
        this.world.acquireRef();
        this.updateCallback = updateCallback;
        this.completionCallback = completionCallback;
        this.worker.start();
    }

    @Override
    public WorldEngine getEngine() {
        return this.world;
    }

    @Override
    public void shutdown() {
        if (this.isShutdown.getAndSet(true)) {
            return;
        }
        this.isRunning = false;
        if (this.worker != null) {
            try {
                this.worker.join();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
        if (this.service.isLive()) {
            this.world.releaseRef();
            this.service.shutdown();
        }
        while (!this.jobQueue.isEmpty()) {
            this.jobQueue.poll().run();
        }
    }

    public void importRegionDirectoryAsync(File directory) {
        File[] files = directory.listFiles((dir, name) -> {
            String[] sections = name.split("\\.");
            if (sections.length != 4 || !sections[0].equals("r") || !sections[3].equals("mca")) {
                Logger.error("Unknown file: " + name);
                return false;
            }
            return true;
        });
        if (files == null) {
            return;
        }
        Arrays.sort(files, File::compareTo);
        this.importRegionsAsync(files, this::importRegionFile);
    }

    public void importZippedRegionDirectoryAsync(File zip, String innerDirectory) {
        try {
            String normalizedInnerDirectory = innerDirectory.replace("\\\\", "\\").replace("\\", "/");
            ZipFile file = new ZipFile(zip);
            ArrayList<ZipArchiveEntry> regions = new ArrayList<>();
            for (var entries = file.getEntries(); entries.hasMoreElements();) {
                ZipArchiveEntry entry = entries.nextElement();
                if (entry.isDirectory() || !entry.getName().startsWith(normalizedInnerDirectory)) {
                    continue;
                }
                String[] parts = entry.getName().split("/");
                String name = parts[parts.length - 1];
                String[] sections = name.split("\\.");
                if (sections.length != 4 || !sections[0].equals("r") || !sections[3].equals("mca")) {
                    Logger.error("Unknown file: " + name);
                    continue;
                }
                regions.add(entry);
            }
            this.importRegionsAsync(regions.toArray(ZipArchiveEntry[]::new), entry -> {
                if (entry.getSize() == 0) {
                    return;
                }
                MemoryBuffer buffer = new MemoryBuffer(entry.getSize());
                try (var channel = Channels.newChannel(file.getInputStream(entry))) {
                    if (channel.read(buffer.asByteBuffer()) != buffer.size) {
                        buffer.free();
                        throw new IllegalStateException("Could not read full zip entry");
                    }
                }

                String[] parts = entry.getName().split("/");
                String name = parts[parts.length - 1];
                String[] sections = name.split("\\.");
                try {
                    this.importRegion(buffer, Integer.parseInt(sections[1]), Integer.parseInt(sections[2]));
                } catch (NumberFormatException e) {
                    Logger.error("Invalid format for region position, x: \"" + sections[1]
                            + "\" z: \"" + sections[2] + "\" skipping region");
                }
                buffer.free();
            });
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private <T> void importRegionsAsync(T[] regionFiles, IImporterMethod<T> importer) {
        this.totalChunks.set(0);
        this.estimatedTotalChunks.set(0);
        this.chunksProcessed.set(0);
        this.worker = new Thread(() -> {
            this.estimatedTotalChunks.addAndGet(regionFiles.length * 1024);
            for (T file : regionFiles) {
                this.estimatedTotalChunks.addAndGet(-1024);
                try {
                    importer.importRegion(file);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
                while (this.totalChunks.get() - this.chunksProcessed.get() > 10_000 && this.isRunning) {
                    try {
                        Thread.sleep(1);
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }
                if (!this.isRunning) {
                    this.service.blockTillEmpty();
                    this.completionCallback.onCompletion(this.totalChunks.get());
                    this.worker = null;
                    return;
                }
            }
            this.service.blockTillEmpty();
            while (this.chunksProcessed.get() != this.totalChunks.get() && this.isRunning) {
                Thread.yield();
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
            if (!this.isShutdown.getAndSet(true)) {
                this.worker = null;
                this.service.shutdown();
                this.world.releaseRef();
            }
            this.completionCallback.onCompletion(this.totalChunks.get());
        });
        this.worker.setName("World importer");
    }

    @Override
    public boolean isRunning() {
        return this.isRunning || (this.worker != null && this.worker.isAlive());
    }

    private void importRegionFile(File file) throws IOException {
        String name = file.getName();
        String[] sections = name.split("\\.");
        if (sections.length != 4 || !sections[0].equals("r") || !sections[3].equals("mca")) {
            Logger.error("Unknown file: " + name);
            throw new IllegalStateException();
        }
        int regionX;
        int regionZ;
        try {
            regionX = Integer.parseInt(sections[1]);
            regionZ = Integer.parseInt(sections[2]);
        } catch (NumberFormatException e) {
            Logger.error("Invalid format for region position, x: \"" + sections[1]
                    + "\" z: \"" + sections[2] + "\" skipping region");
            return;
        }
        try (FileChannel fileStream = FileChannel.open(file.toPath(), StandardOpenOption.READ)) {
            if (fileStream.size() == 0) {
                return;
            }
            MemoryBuffer fileData = new MemoryBuffer(fileStream.size());
            if (fileStream.read(fileData.asByteBuffer(), 0) < 8192) {
                fileData.free();
                Logger.warn("Header of region file invalid");
                return;
            }
            this.importRegion(fileData, regionX, regionZ);
            fileData.free();
        }
    }

    private void importRegion(MemoryBuffer regionFile, int regionX, int regionZ) {
        if (regionFile.size < 8192) {
            Logger.warn("Header of region file invalid");
            return;
        }
        for (int index = 0; index < 1024; index++) {
            int sectorMeta = Integer.reverseBytes(MemoryUtil.memGetInt(regionFile.address + index * 4L));
            if (sectorMeta == 0) {
                continue;
            }
            int sectorStart = sectorMeta >>> 8;
            int sectorCount = sectorMeta & 0xFF;
            if (sectorCount == 0) {
                continue;
            }
            if (regionFile.size < ((sectorCount - 1L) + sectorStart) * 4096L) {
                Logger.warn("Cannot access chunk sector as it goes out of bounds. start bytes: "
                        + sectorStart * 4096L + " sector count: " + sectorCount + " fileSize: " + regionFile.size);
                continue;
            }

            long base = regionFile.address + sectorStart * 4096L;
            int chunkLength = sectorCount * 4096;
            int streamLength = Integer.reverseBytes(MemoryUtil.memGetInt(base));
            byte flags = MemoryUtil.memGetByte(base + 4L);
            if (streamLength == 0) {
                Logger.error("Chunk is allocated, but stream is missing");
                continue;
            }
            int payloadLength = streamLength - 1;
            if (regionFile.size < payloadLength + sectorStart * 4096L) {
                Logger.warn("Chunk stream to small");
            } else if ((flags & 128) != 0) {
                if (payloadLength != 0) {
                    Logger.error("Chunk has both internal and external streams");
                }
                Logger.error("Chunk has external stream which is not supported");
            } else if (payloadLength > chunkLength - 5) {
                Logger.error("Chunk stream is truncated: expected " + payloadLength + " but read " + (chunkLength - 5));
            } else if (payloadLength < 0) {
                Logger.error("Declared size of chunk is negative");
            } else {
                MemoryBuffer data = new MemoryBuffer(payloadLength).cpyFrom(base + 5L);
                this.jobQueue.add(() -> {
                    if (!this.isRunning) {
                        data.free();
                        return;
                    }
                    try {
                        try (DataInputStream decompressedData = this.decompress(flags, data)) {
                            if (decompressedData == null) {
                                Logger.error("Error decompressing chunk data");
                            } else {
                                this.importChunkNbt(NbtIo.read(decompressedData), regionX, regionZ);
                            }
                        }
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    } finally {
                        data.free();
                    }
                });
                this.totalChunks.incrementAndGet();
                this.estimatedTotalChunks.incrementAndGet();
                this.service.execute();
            }
        }
    }

    static InputStream createInputStream(MemoryBuffer data) {
        return new InputStream() {
            private long offset;

            @Override
            public int read() {
                return MemoryUtil.memGetByte(data.address + this.offset++) & 0xFF;
            }

            @Override
            public int read(byte[] bytes, int offset, int length) {
                length = Math.min(length, this.available());
                if (length == 0) {
                    return -1;
                }
                UnsafeUtil.memcpy(data.address + this.offset, length, bytes, offset);
                this.offset += length;
                return length;
            }

            @Override
            public int available() {
                return (int) (data.size - this.offset);
            }
        };
    }

    static DataInputStream decompress(byte flags, MemoryBuffer stream) throws IOException {
        RegionFileVersion chunkStreamVersion = RegionFileVersion.fromId(flags);
        if (chunkStreamVersion == null) {
            Logger.error("Chunk has invalid chunk stream version");
            return null;
        }
        return new DataInputStream(chunkStreamVersion.wrap(createInputStream(stream)));
    }

    private void importChunkNbt(CompoundTag chunk, int regionX, int regionZ) {
        if (!chunk.contains("Status", Tag.TAG_STRING)) {
            this.totalChunks.decrementAndGet();
            return;
        }

        ChunkStatus status = ChunkStatus.byName(chunk.getString("Status"));
        if (status != ChunkStatus.FULL && status != ChunkStatus.EMPTY) {
            this.totalChunks.decrementAndGet();
            return;
        }

        try {
            int chunkX = getIntOrSentinel(chunk, "xPos");
            int chunkZ = getIntOrSentinel(chunk, "zPos");
            if (chunkX >> 5 != regionX || chunkZ >> 5 != regionZ) {
                Logger.error("Chunk position is not located in correct region, expected: (" + regionX + ", " + regionZ
                        + "), got: (" + (chunkX >> 5) + ", " + (chunkZ >> 5) + "), importing anyway");
            }

            ListTag sections = requireList(chunk, "sections");
            for (int index = 0; index < sections.size(); index++) {
                CompoundTag section = (CompoundTag) sections.get(index);
                this.importSectionNbt(chunkX, getIntOrSentinel(section, "Y"), chunkZ, section);
            }
        } catch (Exception e) {
            Logger.error("Exception importing world chunk:", e);
        }

        this.updateCallback.onUpdate(this.chunksProcessed.incrementAndGet(), this.estimatedTotalChunks.get());
    }

    private static final ThreadLocal<VoxelizedSection> SECTION_CACHE =
            ThreadLocal.withInitial(VoxelizedSection::createEmpty);

    private void importSectionNbt(int x, int y, int z, CompoundTag section) {
        if (!section.contains("block_states", Tag.TAG_COMPOUND)) {
            return;
        }

        byte[] blockLightData = section.getByteArray("BlockLight");
        byte[] skyLightData = section.getByteArray("SkyLight");
        DataLayer blockLight = blockLightData.length == 0 ? null : new DataLayer(blockLightData);
        DataLayer skyLight = skyLightData.length == 0 ? null : new DataLayer(skyLightData);

        var blockStatesResult = this.blockStateCodec
                .parse(NbtOps.INSTANCE, section.getCompound("block_states"))
                .resultOrPartial(message -> Logger.error("Failed to decode imported block states: " + message));
        if (blockStatesResult.isEmpty()) {
            return;
        }
        PalettedContainerRO<Holder<Biome>> biomes = this.defaultBiomeProvider;
        if (section.contains("biomes", Tag.TAG_COMPOUND)) {
            biomes = this.biomeCodec
                    .parse(NbtOps.INSTANCE, section.getCompound("biomes"))
                    .result()
                    .orElse(this.defaultBiomeProvider);
        }

        VoxelizedSection converted = WorldConversionFactory.convert(
                SECTION_CACHE.get().setPosition(x, y, z),
                this.world.getMapper(),
                blockStatesResult.get(),
                biomes,
                (blockX, blockY, blockZ) -> {
                    int block = blockLight == null ? 0 : blockLight.get(blockX, blockY, blockZ);
                    int sky = skyLight == null ? 0 : skyLight.get(blockX, blockY, blockZ);
                    return (byte) (sky | block << 4);
                });
        WorldVoxilizedSectionMipper.mipSection(converted, this.world.getMapper());
        WorldUpdater.insertUpdate(this.world, converted);
    }

    static int getIntOrSentinel(CompoundTag tag, String key) {
        return tag.contains(key, Tag.TAG_ANY_NUMERIC) ? tag.getInt(key) : Integer.MIN_VALUE;
    }

    static ListTag requireList(CompoundTag tag, String key) {
        Tag value = tag.get(key);
        if (value instanceof ListTag list) {
            return list;
        }
        throw new IllegalStateException("Missing required list tag: " + key);
    }

    @FunctionalInterface
    private interface IImporterMethod<T> {
        void importRegion(T file) throws Exception;
    }
}
