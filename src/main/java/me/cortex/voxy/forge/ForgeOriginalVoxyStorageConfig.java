package me.cortex.voxy.forge;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import me.cortex.voxy.common.config.ConfigBuildCtx;
import me.cortex.voxy.common.config.compressors.StorageCompressor;
import me.cortex.voxy.common.config.section.SectionSerializationStorage;
import me.cortex.voxy.common.config.section.SectionStorage;
import me.cortex.voxy.common.config.storage.StorageBackend;
import me.cortex.voxy.common.config.storage.inmemory.MemoryStorageBackend;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Forge registry adapter for original Voxy's polymorphic storage config JSON. */
final class ForgeOriginalVoxyStorageConfig {
    private static final ConfigTypeRegistry<SectionStorageConfig> SECTION_TYPES =
            new ConfigTypeRegistry<>(SectionStorageConfig.class);
    private static final ConfigTypeRegistry<StorageConfig> STORAGE_TYPES =
            new ConfigTypeRegistry<>(StorageConfig.class);
    private static final ConfigTypeRegistry<CompressorConfig> COMPRESSOR_TYPES =
            new ConfigTypeRegistry<>(CompressorConfig.class);
    private static final Gson GSON;

    static {
        SECTION_TYPES.register("Serializer", SerializerConfig.class);
        STORAGE_TYPES.register("RocksDB", RocksDbConfig.class);
        STORAGE_TYPES.register("LMDB", LmdbConfig.class);
        STORAGE_TYPES.register("Redis", RedisConfig.class);
        STORAGE_TYPES.register("Memory", MemoryConfig.class);
        STORAGE_TYPES.register("CompressionAdaptor", CompressionAdaptorConfig.class);
        STORAGE_TYPES.register("BasicPathConfig", BasicPathConfig.class);
        STORAGE_TYPES.register("FragmentationAdaptor", FragmentationAdaptorConfig.class);
        STORAGE_TYPES.register("AutoFragmentationAdaptor", AutoFragmentationAdaptorConfig.class);
        STORAGE_TYPES.register("ReadonlyCachingLayer", ReadonlyCachingLayerConfig.class);
        STORAGE_TYPES.reject(
                "ConditionalConfig",
                "upstream declares ConditionalConfig but does not define build semantics");
        COMPRESSOR_TYPES.register("ZSTD", ZstdConfig.class);
        COMPRESSOR_TYPES.register("LZ4", Lz4Config.class);
        GSON = new GsonBuilder()
                .setPrettyPrinting()
                .registerTypeAdapterFactory(SECTION_TYPES)
                .registerTypeAdapterFactory(STORAGE_TYPES)
                .registerTypeAdapterFactory(COMPRESSOR_TYPES)
                .create();
    }

    private ForgeOriginalVoxyStorageConfig() {
    }

    static Loaded loadOrCreate(Path basePath) {
        Path normalizedBase = basePath.toAbsolutePath().normalize();
        try {
            Files.createDirectories(normalizedBase);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        Path configPath = normalizedBase.resolve("config.json");
        ClientConfig config = null;
        String source = "default-created";
        if (Files.exists(configPath)) {
            try {
                config = GSON.fromJson(Files.readString(configPath), ClientConfig.class);
                if (config == null || config.version != 1 || config.sectionStorageConfig == null) {
                    VoxyForge.LOGGER.error(
                            "Invalid original Voxy storage config; resetting it to the original default: {}",
                            configPath);
                    config = null;
                    source = "invalid-reset";
                } else {
                    source = "loaded";
                }
            } catch (UnsupportedStorageConfigException e) {
                throw new IllegalStateException(
                        "Refusing unsupported original Voxy storage configuration at " + configPath
                                + "; the file was preserved unchanged",
                        e);
            } catch (Exception e) {
                VoxyForge.LOGGER.error(
                        "Failed to load original Voxy storage config; resetting it to the original default: {}",
                        configPath,
                        e);
                config = null;
                source = "load-failure-reset";
            }
        }

        if (config == null) {
            config = createDefault();
        }
        try {
            Files.writeString(configPath, GSON.toJson(config));
        } catch (Exception e) {
            throw new RuntimeException("Failed to write original Voxy storage config " + configPath, e);
        }
        return new Loaded(config, configPath, source);
    }

    private static ClientConfig createDefault() {
        RocksDbConfig rocksDb = new RocksDbConfig();
        ZstdConfig zstd = new ZstdConfig();
        zstd.compressionLevel = 1;
        CompressionAdaptorConfig compression = new CompressionAdaptorConfig();
        compression.delegate = rocksDb;
        compression.compressor = zstd;
        SerializerConfig serializer = new SerializerConfig();
        serializer.storage = compression;
        ClientConfig config = new ClientConfig();
        config.sectionStorageConfig = serializer;
        return config;
    }

    static final class ClientConfig {
        int version = 1;
        boolean disabled;
        SectionStorageConfig sectionStorageConfig;
    }

    record Loaded(ClientConfig config, Path path, String source) {
        boolean ready() {
            return this.config != null && this.config.version == 1 && this.config.sectionStorageConfig != null;
        }

        String backendChain() {
            return this.ready() ? this.config.sectionStorageConfig.describe() : "none";
        }
    }

    abstract static class SectionStorageConfig {
        abstract SectionStorage build(ConfigBuildCtx context);

        abstract String describe();
    }

    abstract static class StorageConfig {
        abstract StorageBackend build(ConfigBuildCtx context);

        abstract String describe();
    }

    abstract static class CompressorConfig {
        abstract StorageCompressor build(ConfigBuildCtx context);

        abstract String describe();
    }

    static final class SerializerConfig extends SectionStorageConfig {
        StorageConfig storage;

        @Override
        SectionStorage build(ConfigBuildCtx context) {
            if (this.storage == null) {
                throw new IllegalStateException("Serializer storage config is null");
            }
            return new SectionSerializationStorage(this.storage.build(context));
        }

        @Override
        String describe() {
            return "Serializer->" + (this.storage == null ? "none" : this.storage.describe());
        }
    }

    static final class RocksDbConfig extends StorageConfig {
        @Override
        StorageBackend build(ConfigBuildCtx context) {
            String path = context.ensurePathExists(context.substituteString(context.resolvePath()));
            return new RocksDBStorageBackend(path);
        }

        @Override
        String describe() {
            return "RocksDB";
        }
    }

    static final class MemoryConfig extends StorageConfig {
        @Override
        StorageBackend build(ConfigBuildCtx context) {
            return new MemoryStorageBackend();
        }

        @Override
        String describe() {
            return "Memory";
        }
    }

    static final class LmdbConfig extends StorageConfig {
        @Override
        StorageBackend build(ConfigBuildCtx context) {
            String path = context.ensurePathExists(context.substituteString(context.resolvePath()));
            return new LMDBStorageBackend(path);
        }

        @Override
        String describe() {
            return "LMDB";
        }
    }

    static final class RedisConfig extends StorageConfig {
        String host;
        int port;
        String prefix;

        @Override
        StorageBackend build(ConfigBuildCtx context) {
            return new RedisStorageBackend(
                    this.host,
                    this.port,
                    context.substituteString(this.prefix));
        }

        @Override
        String describe() {
            return "Redis(host=" + this.host + ",port=" + this.port + ",prefix=" + this.prefix + ")";
        }
    }

    static final class CompressionAdaptorConfig extends StorageConfig {
        CompressorConfig compressor;
        StorageConfig delegate;

        @Override
        StorageBackend build(ConfigBuildCtx context) {
            if (this.compressor == null || this.delegate == null) {
                throw new IllegalStateException("Compression adaptor config is incomplete");
            }
            return new CompressionStorageAdaptor(
                    this.compressor.build(context),
                    this.delegate.build(context));
        }

        @Override
        String describe() {
            String compressorDescription = this.compressor == null ? "none" : this.compressor.describe();
            String delegateDescription = this.delegate == null ? "none" : this.delegate.describe();
            return compressorDescription + "->" + delegateDescription;
        }
    }

    static final class ZstdConfig extends CompressorConfig {
        int compressionLevel;

        @Override
        StorageCompressor build(ConfigBuildCtx context) {
            return new ZSTDCompressor(this.compressionLevel);
        }

        @Override
        String describe() {
            return "ZSTD(level=" + this.compressionLevel + ")";
        }
    }

    static final class Lz4Config extends CompressorConfig {
        @Override
        StorageCompressor build(ConfigBuildCtx context) {
            return new LZ4Compressor();
        }

        @Override
        String describe() {
            return "LZ4";
        }
    }

    static final class BasicPathConfig extends StorageConfig {
        String path = "";
        StorageConfig delegate;

        @Override
        StorageBackend build(ConfigBuildCtx context) {
            if (this.delegate == null) {
                throw new IllegalStateException("Basic path delegate is null");
            }
            context.pushPath(this.path);
            StorageBackend storage = this.delegate.build(context);
            context.popPath();
            return storage;
        }

        @Override
        String describe() {
            return "BasicPath(" + this.path + ")->" + (this.delegate == null ? "none" : this.delegate.describe());
        }
    }

    static final class FragmentationAdaptorConfig extends StorageConfig {
        List<StorageConfig> backends = new ArrayList<>();

        @Override
        StorageBackend build(ConfigBuildCtx context) {
            StorageBackend[] builtBackends = new StorageBackend[this.backends.size()];
            for (int i = 0; i < this.backends.size(); i++) {
                builtBackends[i] = this.backends.get(i).build(context);
            }
            return new FragmentedStorageBackendAdaptor(builtBackends);
        }

        @Override
        String describe() {
            return "Fragmentation(count=" + this.backends.size() + ")";
        }
    }

    static final class AutoFragmentationAdaptorConfig extends StorageConfig {
        StorageConfig delegate;
        String basePath;
        int count;

        @Override
        StorageBackend build(ConfigBuildCtx context) {
            if (this.delegate == null) {
                throw new IllegalStateException("Auto fragmentation delegate is null");
            }
            StorageBackend[] builtBackends = new StorageBackend[this.count];
            for (int i = 0; i < this.count; i++) {
                context.pushPath(this.basePath + "_" + i);
                builtBackends[i] = this.delegate.build(context);
                context.popPath();
            }
            return new FragmentedStorageBackendAdaptor(builtBackends);
        }

        @Override
        String describe() {
            return "AutoFragmentation(basePath=" + this.basePath + ",count=" + this.count + ")->"
                    + (this.delegate == null ? "none" : this.delegate.describe());
        }
    }

    static final class ReadonlyCachingLayerConfig extends StorageConfig {
        StorageConfig cache;
        StorageConfig onMiss;

        @Override
        StorageBackend build(ConfigBuildCtx context) {
            if (this.cache == null || this.onMiss == null) {
                throw new IllegalStateException("Readonly caching layer config is incomplete");
            }
            return new ReadonlyCachingLayer(
                    this.cache.build(context),
                    this.onMiss.build(context));
        }

        @Override
        String describe() {
            return "ReadonlyCachingLayer(cache=" + (this.cache == null ? "none" : this.cache.describe())
                    + ",onMiss=" + (this.onMiss == null ? "none" : this.onMiss.describe()) + ")";
        }
    }

    private static final class ConfigTypeRegistry<T> implements TypeAdapterFactory {
        private static final String TYPE_FIELD = "TYPE";
        private final Class<T> baseType;
        private final Map<String, Class<? extends T>> nameToType = new LinkedHashMap<>();
        private final Map<Class<? extends T>, String> typeToName = new HashMap<>();
        private final Map<String, String> rejectedTypes = new HashMap<>();

        private ConfigTypeRegistry(Class<T> baseType) {
            this.baseType = baseType;
        }

        private void register(String typeName, Class<? extends T> type) {
            if (this.nameToType.put(typeName, type) != null) {
                throw new IllegalStateException("Duplicate config type name " + typeName);
            }
            if (this.typeToName.put(type, typeName) != null) {
                throw new IllegalStateException("Duplicate config class " + type.getName());
            }
        }

        private void reject(String typeName, String reason) {
            if (this.nameToType.containsKey(typeName) || this.rejectedTypes.put(typeName, reason) != null) {
                throw new IllegalStateException("Duplicate config type name " + typeName);
            }
        }

        @Override
        @SuppressWarnings({"unchecked", "rawtypes"})
        public <X> TypeAdapter<X> create(Gson gson, TypeToken<X> token) {
            if (!this.baseType.isAssignableFrom(token.getRawType())) {
                return null;
            }
            TypeAdapter<JsonElement> jsonAdapter = gson.getAdapter(JsonElement.class);
            return (TypeAdapter<X>) new TypeAdapter<T>() {
                @Override
                public void write(JsonWriter out, T value) throws IOException {
                    Class<? extends T> runtimeType = (Class<? extends T>) value.getClass();
                    String typeName = ConfigTypeRegistry.this.typeToName.get(runtimeType);
                    if (typeName == null) {
                        throw new IllegalStateException("Unregistered storage config type " + runtimeType.getName());
                    }
                    JsonElement body = gson.getDelegateAdapter(
                            ConfigTypeRegistry.this,
                            TypeToken.get((Class) runtimeType)).toJsonTree(value);
                    JsonObject output = new JsonObject();
                    output.addProperty(TYPE_FIELD, typeName);
                    body.getAsJsonObject().entrySet().forEach(entry -> output.add(entry.getKey(), entry.getValue()));
                    jsonAdapter.write(out, output);
                }

                @Override
                public T read(JsonReader in) throws IOException {
                    JsonObject input = jsonAdapter.read(in).getAsJsonObject();
                    JsonElement typeElement = input.remove(TYPE_FIELD);
                    if (typeElement == null) {
                        throw new IllegalStateException("Storage config is missing " + TYPE_FIELD);
                    }
                    String typeName = typeElement.getAsString();
                    String rejection = ConfigTypeRegistry.this.rejectedTypes.get(typeName);
                    if (rejection != null) {
                        throw new UnsupportedStorageConfigException(typeName + ": " + rejection);
                    }
                    Class<? extends T> type = ConfigTypeRegistry.this.nameToType.get(typeName);
                    if (type == null) {
                        throw new IllegalStateException("Unknown storage config type " + typeName);
                    }
                    return gson.getDelegateAdapter(
                            ConfigTypeRegistry.this,
                            TypeToken.get(type)).fromJsonTree(input);
                }
            };
        }
    }

    private static final class UnsupportedStorageConfigException extends RuntimeException {
        private UnsupportedStorageConfigException(String message) {
            super(message);
        }
    }
}
