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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
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
        STORAGE_TYPES.register("CompressionAdaptor", CompressionAdaptorConfig.class);
        COMPRESSOR_TYPES.register("ZSTD", ZstdConfig.class);
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
            source = "loaded";
            try {
                config = GSON.fromJson(Files.readString(configPath), ClientConfig.class);
                if (config == null || config.version != 1 || config.sectionStorageConfig == null) {
                    VoxyForge.LOGGER.error("Invalid original Voxy storage config, reverting to default: {}", configPath);
                    config = null;
                    source = "invalid-reset";
                }
            } catch (Exception e) {
                VoxyForge.LOGGER.error(
                        "Failed to load original Voxy storage config; resetting to default may break a custom save: {}",
                        configPath,
                        e);
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
            return new ForgeOriginalVoxyRocksDBStorageBackend(path);
        }

        @Override
        String describe() {
            return "RocksDB";
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
            return new ForgeOriginalVoxyCompressionStorageAdaptor(
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
            return new ForgeOriginalVoxyZstdCompressor(this.compressionLevel);
        }

        @Override
        String describe() {
            return "ZSTD(level=" + this.compressionLevel + ")";
        }
    }

    private static final class ConfigTypeRegistry<T> implements TypeAdapterFactory {
        private static final String TYPE_FIELD = "TYPE";
        private final Class<T> baseType;
        private final Map<String, Class<? extends T>> nameToType = new LinkedHashMap<>();
        private final Map<Class<? extends T>, String> typeToName = new HashMap<>();

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
                    Class<? extends T> type = ConfigTypeRegistry.this.nameToType.get(typeElement.getAsString());
                    if (type == null) {
                        throw new IllegalStateException("Unknown storage config type " + typeElement.getAsString());
                    }
                    return gson.getDelegateAdapter(
                            ConfigTypeRegistry.this,
                            TypeToken.get(type)).fromJsonTree(input);
                }
            };
        }
    }
}
