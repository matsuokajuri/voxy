package me.cortex.voxy.common.world.other;

import com.mojang.serialization.Dynamic;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import me.cortex.voxy.common.Logger;
import me.cortex.voxy.common.config.IMappingStorage;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.util.datafix.fixes.References;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.lwjgl.system.MemoryUtil;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;


//There are independent mappings for biome and block states, these get combined in the shader and allow for more
// variaty of things
public class Mapper {
    private static final int BLOCK_STATE_TYPE = 1;
    private static final int BIOME_TYPE = 2;

    private final IMappingStorage storage;
    public static final long UNKNOWN_MAPPING = -1;
    public static final long AIR = 0;

    private final ReentrantLock blockLock = new ReentrantLock();
    private final ConcurrentHashMap<BlockState, StateEntry> block2stateEntry = new ConcurrentHashMap<>(2000,0.75f, 10);
    private final ObjectArrayList<StateEntry> blockId2stateEntry = new ObjectArrayList<>();


    private final ReentrantLock biomeLock = new ReentrantLock();
    private final ConcurrentHashMap<String, BiomeEntry> biome2biomeEntry = new ConcurrentHashMap<>(2000,0.75f, 10);
    private final ObjectArrayList<BiomeEntry> biomeId2biomeEntry = new ObjectArrayList<>();

    private Consumer<StateEntry> newStateCallback;
    private Consumer<BiomeEntry> newBiomeCallback;
    public Mapper(IMappingStorage storage) {
        this.storage = storage;
        //Insert air since its a special entry (index 0)
        var airEntry = new StateEntry(0, Blocks.AIR.defaultBlockState());
        this.block2stateEntry.put(airEntry.state, airEntry);
        this.blockId2stateEntry.add(airEntry);

        this.loadFromStorage();
    }


    public static boolean isAir(long id) {
        //Note: air can mean void, cave or normal air, as the block state is remapped during ingesting
        return (id&(((1L<<20)-1)<<27)) == 0;
    }

    public static int getBlockId(long id) {
        return (int) ((id>>27)&((1<<20)-1));
    }

    public static int getBiomeId(long id) {
        return (int) ((id>>47)&0x1FF);
    }

    public static int getLightId(long id) {
        return (int) ((id>>56)&0xFF);
    }

    public static long withLight(long id, int light) {
        return (id&(~(0xFFL<<56)))|(Integer.toUnsignedLong(light&0xFF)<<56);
    }

    public static long withBlockBiome(long id, int block, int biome) {
        return (id&(0xFFL<<56))|(Integer.toUnsignedLong(block)<<27)|(Integer.toUnsignedLong(biome)<<47);
    }

    public static long airWithLight(int light) {
        return Integer.toUnsignedLong(light&0xFF)<<56;
    }

    public void setStateCallback(Consumer<StateEntry> stateCallback) {
        this.blockLock.lock();
        try {
            this.newStateCallback = stateCallback;
        } finally {
            this.blockLock.unlock();
        }
    }

    public void setBiomeCallback(Consumer<BiomeEntry> biomeCallback) {
        this.biomeLock.lock();
        try {
            this.newBiomeCallback = biomeCallback;
        } finally {
            this.biomeLock.unlock();
        }
    }

    public StateEntry[] setStateCallbackAndGetSnapshot(Consumer<StateEntry> stateCallback) {
        this.blockLock.lock();
        try {
            this.newStateCallback = stateCallback;
            return this.snapshotStateEntriesLocked();
        } finally {
            this.blockLock.unlock();
        }
    }

    public BiomeEntry[] setBiomeCallbackAndGetSnapshot(Consumer<BiomeEntry> biomeCallback) {
        this.biomeLock.lock();
        try {
            this.newBiomeCallback = biomeCallback;
            return this.snapshotBiomeEntriesLocked();
        } finally {
            this.biomeLock.unlock();
        }
    }

    private void loadFromStorage() {
        int currentDataVersion = SharedConstants.getCurrentVersion().getDataVersion().getVersion();
        MappingStorageMetadata.UpgradeSession upgrade = MappingStorageMetadata.prepare(
                this.storage,
                this.storage.getIdMappingsData(),
                currentDataVersion);
        if (upgrade.recoveredInterruptedUpgrade()) {
            Logger.warn("Recovered interrupted mapping upgrade from verified backup");
        }
        upgrade.begin();

        try {
            var mappings = upgrade.mappings();
            List<StateEntry> sentries = new ArrayList<>();
            List<BiomeEntry> bentries = new ArrayList<>();
            boolean[] forceResave = new boolean[1];
            for (var entry : mappings.int2ObjectEntrySet()) {
                int entryType = entry.getIntKey()>>>30;
                int id = entry.getIntKey() & ((1<<30)-1);
                try {
                    if (entryType == BLOCK_STATE_TYPE) {
                        var sentry = StateEntry.deserialize(
                                id,
                                entry.getValue(),
                                forceResave,
                                upgrade.sourceDataVersion());
                        if (sentry.state.isAir()) {
                            Logger.error("Deserialization was air; preserving block id " + id + " as a deterministic missing-state placeholder");
                            sentries.add(sentry);
                            continue;
                        }
                        sentries.add(sentry);
                        var oldEntry = this.block2stateEntry.putIfAbsent(sentry.state, sentry);
                        if (oldEntry != null) {
                            Logger.warn("Multiple mappings for blockstate, using old state, expect things to possibly go really badly. " + oldEntry.id + ":" + sentry.id + ":" + sentry.state );
                        }
                    } else if (entryType == BIOME_TYPE) {
                        var bentry = BiomeEntry.deserialize(id, entry.getValue());
                        bentries.add(bentry);
                        if (this.biome2biomeEntry.put(bentry.biome, bentry) != null) {
                            throw new IllegalStateException("Multiple mappings for biome entry");
                        }
                    } else {
                        throw new IllegalStateException("Unknown entryType");
                    }
                } catch (RuntimeException exception) {
                    throw new IllegalStateException(
                            "Unable to decode persisted mapping key " + entry.getIntKey(),
                            exception);
                }
            }

            sentries.stream().sorted(Comparator.comparing(a->a.id)).forEach(entry -> {
                if (this.blockId2stateEntry.size() != entry.id) {
                    throw new IllegalStateException("Block entry not ordered");
                }
                this.blockId2stateEntry.add(entry);
            });

            bentries.stream().sorted(Comparator.comparing(a->a.id)).forEach(entry -> {
                if (this.biomeId2biomeEntry.size() != entry.id) {
                    throw new IllegalStateException("Biome entry not ordered. got " + entry.biome + " with id " + entry.id + " expected id " + this.biomeId2biomeEntry.size());
                }
                this.biomeId2biomeEntry.add(entry);
            });

            if (forceResave[0]) {
                Logger.warn("Forced state resave triggered from Minecraft data version "
                        + upgrade.sourceDataVersion() + " to " + currentDataVersion);
                this.forceResaveStates();
            }
            upgrade.commit();
        } catch (RuntimeException | Error failure) {
            try {
                upgrade.fail(failure);
            } catch (RuntimeException rollbackFailure) {
                failure.addSuppressed(rollbackFailure);
            }
            throw failure;
        }
    }

    public final int getBlockStateCount() {
        return this.blockId2stateEntry.size();
    }

    private StateEntry registerNewBlockState(BlockState state) {
        this.blockLock.lock();
        try {
            var entry = this.block2stateEntry.get(state);
            if (entry != null) {
                return entry;
            }

            entry = new StateEntry(this.blockId2stateEntry.size(), state);
            this.blockId2stateEntry.add(entry);
            this.block2stateEntry.put(state, entry);
            this.storeMapping(entry.id | (BLOCK_STATE_TYPE << 30), entry.serialize());

            if (this.newStateCallback != null) {
                this.newStateCallback.accept(entry);
            }
            return entry;
        } finally {
            this.blockLock.unlock();
        }
    }

    private BiomeEntry registerNewBiome(String biome) {
        this.biomeLock.lock();
        try {
            var entry = this.biome2biomeEntry.get(biome);
            if (entry != null) {
                return entry;
            }
            entry = new BiomeEntry(this.biomeId2biomeEntry.size(), biome);
            this.biomeId2biomeEntry.add(entry);
            this.biome2biomeEntry.put(biome, entry);
            this.storeMapping(entry.id | (BIOME_TYPE << 30), entry.serialize());

            if (this.newBiomeCallback != null) {
                this.newBiomeCallback.accept(entry);
            }
            return entry;
        } finally {
            this.biomeLock.unlock();
        }
    }


    //TODO:FIXME: IS VERY SLOW NEED TO MAKE IT LOCK FREE, or at minimum use a concurrent map
    public long getBaseId(byte light, BlockState state, Holder<Biome> biome) {
        if (state.isAir()) return Byte.toUnsignedLong(light) <<56;//Special case and fast return for air, dont care about the biome
        return composeMappingId(light, this.getIdForBlockState(state), this.getIdForBiome(biome));
    }

    public BlockState getBlockStateFromBlockId(int blockId) {
        return this.blockId2stateEntry.get(this.baseBlockStateId(blockId)).state;
    }

    public int baseBlockStateId(int blockId) {
        // Preserve Ecliptic's encoded ids in voxel storage/model mappings, but use their real
        // block state for physical properties. This also decodes existing seasonal cache data.
        return me.cortex.voxy.forge.ForgeSnowStateIds.baseId(blockId, this.getBlockStateCount());
    }

    public int getIdForBlockState(BlockState state) {
        if (state.isAir()) {
            return 0;
        }
        var mapping = this.block2stateEntry.get(state);
        if (mapping == null) {
            mapping = this.registerNewBlockState(state);
        }
        return mapping.id;
    }

    public int getBlockStateOpacity(long mappingId) {
        return this.getBlockStateOpacity(getBlockId(mappingId));
    }

    public int getBlockStateOpacity(int blockId) {
        return this.blockId2stateEntry.get(this.baseBlockStateId(blockId)).opacity;
    }

    int getBlockStateMipProperties(int blockId) {
        return this.blockId2stateEntry.get(this.baseBlockStateId(blockId)).mipProperties;
    }

    public int getIdForBiome(Holder<Biome> biome) {
        String biomeId = biome.unwrapKey().orElseThrow().location().toString();
        var entry = this.biome2biomeEntry.get(biomeId);
        if (entry == null) {
            entry = this.registerNewBiome(biomeId);
        }
        return entry.id;
    }

    public static long composeMappingId(byte light, int blockId, int biomeId) {
        if (blockId == AIR) {//Dont care about biome for air
            return Byte.toUnsignedLong(light)<<56;
        }
        return (Byte.toUnsignedLong(light)<<56)|(Integer.toUnsignedLong(biomeId) << 47)|(Integer.toUnsignedLong(blockId)<<27);
    }

    public StateEntry[] getStateEntries() {
        this.blockLock.lock();
        try {
            return this.snapshotStateEntriesLocked();
        } finally {
            this.blockLock.unlock();
        }
    }

    public BiomeEntry[] getBiomeEntries() {
        this.biomeLock.lock();
        try {
            return this.snapshotBiomeEntriesLocked();
        } finally {
            this.biomeLock.unlock();
        }
    }

    public void forceResaveStates() {
        List<StateEntry> blocks;
        this.blockLock.lock();
        try {
            blocks = new ArrayList<>(this.block2stateEntry.values());
            for (var entry : blocks) {
                if (entry.id >= this.blockId2stateEntry.size() || this.blockId2stateEntry.get(entry.id) != entry) {
                    throw new IllegalStateException("State Id NOT THE SAME, very critically bad. entry: " + entry.id);
                }
            }
        } finally {
            this.blockLock.unlock();
        }

        List<BiomeEntry> biomes;
        this.biomeLock.lock();
        try {
            biomes = new ArrayList<>(this.biome2biomeEntry.values());
            for (var entry : biomes) {
                if (entry.id >= this.biomeId2biomeEntry.size() || this.biomeId2biomeEntry.get(entry.id) != entry) {
                    throw new IllegalStateException("Biome Id NOT THE SAME, very critically bad");
                }
            }
        } finally {
            this.biomeLock.unlock();
        }

        for (var entry : blocks) {
            if (entry.state.isAir() && entry.id == 0) {
                continue;
            }
            this.storeMapping(entry.id | (BLOCK_STATE_TYPE << 30), entry.serialize());
        }

        for (var entry : biomes) {
            this.storeMapping(entry.id | (BIOME_TYPE << 30), entry.serialize());
        }

        this.storage.flush();
    }

    private StateEntry[] snapshotStateEntriesLocked() {
        StateEntry[] out = this.blockId2stateEntry.toArray(new StateEntry[0]);
        for (int id = 0; id < out.length; id++) {
            if (out[id].id != id) {
                throw new IllegalStateException("Block entry not ordered at id " + id);
            }
        }
        return out;
    }

    private BiomeEntry[] snapshotBiomeEntriesLocked() {
        BiomeEntry[] out = this.biomeId2biomeEntry.toArray(new BiomeEntry[0]);
        for (int id = 0; id < out.length; id++) {
            if (out[id].id != id) {
                throw new IllegalStateException("Biome entry not ordered at id " + id);
            }
        }
        return out;
    }

    private void storeMapping(int id, byte[] serialized) {
        ByteBuffer buffer = MemoryUtil.memAlloc(serialized.length);
        try {
            buffer.put(serialized);
            buffer.rewind();
            this.storage.putIdMapping(id, buffer);
        } finally {
            MemoryUtil.memFree(buffer);
        }
    }

    public void close() {

    }


    public static final class StateEntry {
        public final int id;
        public final BlockState state;
        public final int opacity;
        final int mipProperties;
        public StateEntry(int id, BlockState state) {
            this.id = id;
            this.state = state;
            //Override opacity of leaves to be solid
            if (state.getBlock() instanceof LeavesBlock) {
                this.opacity = 15;
            } else {
                this.opacity = state.getLightBlock(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
            }
            this.mipProperties = packMipProperties(state, this.opacity);
        }

        private static int packMipProperties(BlockState state, int opacity) {
            int visualCoverage;
            boolean hasFluid = !state.getFluidState().isEmpty();
            if (state.isAir()) {
                visualCoverage = 0;
            } else if (hasFluid) {
                visualCoverage = 255;
            } else {
                double volume = 0.0;
                for (var box : state.getShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO).toAabbs()) {
                    double width = Math.max(0.0, Math.min(1.0, box.maxX) - Math.max(0.0, box.minX));
                    double height = Math.max(0.0, Math.min(1.0, box.maxY) - Math.max(0.0, box.minY));
                    double depth = Math.max(0.0, Math.min(1.0, box.maxZ) - Math.max(0.0, box.minZ));
                    volume += width * height * depth;
                }
                visualCoverage = Math.max(1, Math.min(255, (int) Math.round(volume * 255.0)));
            }
            int lightEmission = Math.max(0, Math.min(15, state.getLightEmission()));
            return visualCoverage
                    | (Math.max(0, Math.min(15, opacity)) << 8)
                    | (lightEmission << 12)
                    | (hasFluid ? 1 << 16 : 0);
        }

        public byte[] serialize() {
            try {
                var serialized = new CompoundTag();
                serialized.putInt("id", this.id);
                serialized.put("block_state", BlockState.CODEC.encodeStart(NbtOps.INSTANCE, this.state).result().get());
                var out = new ByteArrayOutputStream();
                NbtIo.writeCompressed(serialized, out);
                return out.toByteArray();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        public static StateEntry deserialize(int id, byte[] data, boolean[] forceResave) {
            return deserialize(id, data, forceResave, 0);
        }

        public static StateEntry deserialize(int id, byte[] data, boolean[] forceResave, int sourceDataVersion) {
            try {
                var compound = NbtIo.readCompressed(new ByteArrayInputStream(data));
                if (getIntOr(compound, "id", -1) != id) {
                    throw new IllegalStateException("Encoded id != expected id");
                }
                var bsc = requireCompound(compound, "block_state");
                var state = BlockState.CODEC.parse(NbtOps.INSTANCE, bsc);
                if (state.result().isEmpty()) {
                    Logger.info("Could not decode blockstate, attempting fixes, error: "+ state.error().map(error -> error.message()).orElse("unknown"));
                    bsc = (CompoundTag) DataFixers.getDataFixer().update(
                            References.BLOCK_STATE,
                            new Dynamic<>(NbtOps.INSTANCE,bsc),
                            sourceDataVersion,
                            SharedConstants.getCurrentVersion().getDataVersion().getVersion()).getValue();
                    state = BlockState.CODEC.parse(NbtOps.INSTANCE, bsc);
                    if (state.result().isEmpty()) {
                        Logger.error("Could not decode blockstate setting to air. id:" + id + " error: " + state.error().map(error -> error.message()).orElse("unknown"));
                        return new StateEntry(id, Blocks.AIR.defaultBlockState());
                    } else {
                        Logger.info("Fixed blockstate to: " + state.getOrThrow(false, Logger::error));
                        forceResave[0] |= true;
                        return new StateEntry(id, state.getOrThrow(false, Logger::error));
                    }
                } else {
                    return new StateEntry(id, state.getOrThrow(false, Logger::error));
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public static final class BiomeEntry {
        public final int id;
        public final String biome;

        public BiomeEntry(int id, String biome) {
            this.id = id;
            this.biome = biome;
        }

        public byte[] serialize() {
            try {
                var serialized = new CompoundTag();
                serialized.putInt("id", this.id);
                serialized.putString("biome_id", this.biome);
                var out = new ByteArrayOutputStream();
                NbtIo.writeCompressed(serialized, out);
                return out.toByteArray();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        public static BiomeEntry deserialize(int id, byte[] data) {
            try {
                var compound = NbtIo.readCompressed(new ByteArrayInputStream(data));
                if (getIntOr(compound, "id", -1) != id) {
                    throw new IllegalStateException("Encoded id != expected id");
                }
                String biome = getStringOr(compound, "biome_id", null);
                return new BiomeEntry(id, biome);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private static int getIntOr(CompoundTag compound, String key, int fallback) {
        return compound.contains(key, Tag.TAG_ANY_NUMERIC) ? compound.getInt(key) : fallback;
    }

    private static String getStringOr(CompoundTag compound, String key, String fallback) {
        return compound.contains(key, Tag.TAG_STRING) ? compound.getString(key) : fallback;
    }

    private static CompoundTag requireCompound(CompoundTag compound, String key) {
        if (!compound.contains(key, Tag.TAG_COMPOUND)) {
            throw new IllegalStateException("Expected compound tag: " + key);
        }
        return compound.getCompound(key);
    }
}
