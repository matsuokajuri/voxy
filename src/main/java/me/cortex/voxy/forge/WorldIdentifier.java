package me.cortex.voxy.forge;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.dimension.DimensionType;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** Forge 1.20.1 mapping adapter for original Voxy's {@code WorldIdentifier}. */
public final class WorldIdentifier {
    private static final ResourceKey<DimensionType> NULL_DIMENSION_KEY = ResourceKey.create(
            Registries.DIMENSION_TYPE,
            new ResourceLocation("voxy", "null_dimension_id"));

    private final ResourceKey<Level> key;
    private final long biomeSeed;
    private final ResourceKey<DimensionType> dimension;
    private final long hashCode;

    public WorldIdentifier(
            ResourceKey<Level> key,
            long biomeSeed,
            ResourceKey<DimensionType> dimension) {
        if (key == null) {
            throw new IllegalStateException("Key cannot be null");
        }
        this.key = key;
        this.biomeSeed = biomeSeed;
        this.dimension = dimension == null ? NULL_DIMENSION_KEY : dimension;
        this.hashCode = mixStafford13(registryKeyHashCode(this.key))
                ^ mixStafford13(registryKeyHashCode(this.dimension))
                ^ mixStafford13(this.biomeSeed);
    }

    static WorldIdentifier fromServerLevel(ServerLevel level) {
        //Original MixinWorld captures Level's biome-zoom seed. MinecraftServer derives that
        //constructor value by obfuscating the raw world-generation seed before creating every
        //ServerLevel, so reproduce that identity instead of comparing against getSeed() directly.
        return new WorldIdentifier(
                level.dimension(),
                BiomeManager.obfuscateSeed(level.getSeed()),
                level.dimensionTypeRegistration().unwrapKey().orElse(null));
    }

    ResourceKey<Level> key() {
        return this.key;
    }

    long biomeSeed() {
        return this.biomeSeed;
    }

    ResourceKey<DimensionType> dimension() {
        return this.dimension;
    }

    long getLongHash() {
        return this.hashCode;
    }

    String getWorldId() {
        String data = this.biomeSeed + this.key.toString();
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(data.getBytes());
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte value : hash) {
                String part = Integer.toHexString(0xff & value);
                if (part.length() == 1) {
                    hex.append('0');
                }
                hex.append(part);
            }
            return hex.substring(0, 32);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public int hashCode() {
        return (int) this.hashCode;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof WorldIdentifier other)) {
            return false;
        }
        return other.hashCode == this.hashCode
                && other.biomeSeed == this.biomeSeed
                && equal(other.key, this.key)
                && equal(other.dimension, this.dimension);
    }

    @Override
    public String toString() {
        return "WorldIdentifier[" + this.key.location() + ", " + this.biomeSeed + ", " + this.dimension.location() + ']';
    }

    private static <T> boolean equal(ResourceKey<T> a, ResourceKey<T> b) {
        if (a == b) {
            return true;
        }
        if (a == null || b == null) {
            return false;
        }
        return a.registry().equals(b.registry()) && a.location().equals(b.location());
    }

    private static long registryKeyHashCode(ResourceKey<?> key) {
        ResourceLocation registry = key.registry();
        ResourceLocation location = key.location();
        int registryHash = registry == null ? 0 : registry.hashCode();
        int locationHash = location == null ? 0 : location.hashCode();
        return (Integer.toUnsignedLong(registryHash) << 32) | Integer.toUnsignedLong(locationHash);
    }

    private static long mixStafford13(long seed) {
        seed += 918759875987111L;
        seed = (seed ^ seed >>> 30) * -4658895280553007687L;
        seed = (seed ^ seed >>> 27) * -7723592293110705685L;
        return seed ^ seed >>> 31;
    }
}
