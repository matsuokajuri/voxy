package me.cortex.voxy.common.world.other;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import me.cortex.voxy.common.config.IMappingStorage;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Objects;
import java.util.zip.CRC32;
import org.lwjgl.system.MemoryUtil;

/** Durable metadata around the original per-id mapping byte format. */
public final class MappingStorageMetadata {
    public static final int MANIFEST_KEY = 0;
    public static final int BACKUP_KEY = 1;
    public static final int CURRENT_SCHEMA_VERSION = 1;

    private static final int MANIFEST_MAGIC = 0x56584D46; // VXMF
    private static final int BACKUP_MAGIC = 0x56584D42; // VXMB
    private static final int ENVELOPE_VERSION = 1;
    private static final int MAX_BACKUP_ENTRIES = 1 << 20;
    private static final int MAX_ENTRY_BYTES = 64 << 20;

    private MappingStorageMetadata() {
    }

    public enum State {
        COMMITTED,
        UPGRADING,
        FAILED
    }

    public record Manifest(
            int schemaVersion,
            int dataVersion,
            State state,
            int targetSchemaVersion,
            int targetDataVersion,
            String detail) {
        public Manifest {
            validateNonNegativeVersion("schema", schemaVersion);
            validateNonNegativeVersion("Minecraft data", dataVersion);
            validateNonNegativeVersion("target schema", targetSchemaVersion);
            validateNonNegativeVersion("target Minecraft data", targetDataVersion);
            Objects.requireNonNull(state, "state");
        }
    }

    public record Backup(
            int schemaVersion,
            int dataVersion,
            Int2ObjectOpenHashMap<byte[]> mappings) {
        public Backup {
            validateNonNegativeVersion("backup schema", schemaVersion);
            validateNonNegativeVersion("backup Minecraft data", dataVersion);
            Objects.requireNonNull(mappings, "mappings");
        }
    }

    public static final class UpgradeSession {
        private final IMappingStorage storage;
        private final int sourceSchemaVersion;
        private final int sourceDataVersion;
        private final int targetDataVersion;
        private final Int2ObjectOpenHashMap<byte[]> mappings;
        private final boolean upgradeRequired;
        private final boolean recoveredInterruptedUpgrade;
        private boolean begun;

        private UpgradeSession(
                IMappingStorage storage,
                int sourceSchemaVersion,
                int sourceDataVersion,
                int targetDataVersion,
                Int2ObjectOpenHashMap<byte[]> mappings,
                boolean upgradeRequired,
                boolean recoveredInterruptedUpgrade) {
            this.storage = storage;
            this.sourceSchemaVersion = sourceSchemaVersion;
            this.sourceDataVersion = sourceDataVersion;
            this.targetDataVersion = targetDataVersion;
            this.mappings = mappings;
            this.upgradeRequired = upgradeRequired;
            this.recoveredInterruptedUpgrade = recoveredInterruptedUpgrade;
        }

        public Int2ObjectOpenHashMap<byte[]> mappings() {
            return deepCopy(this.mappings);
        }

        public int sourceDataVersion() {
            return this.sourceDataVersion;
        }

        public boolean upgradeRequired() {
            return this.upgradeRequired;
        }

        public boolean recoveredInterruptedUpgrade() {
            return this.recoveredInterruptedUpgrade;
        }

        public void begin() {
            if (!this.upgradeRequired || this.begun) {
                return;
            }
            putBytes(this.storage, BACKUP_KEY, encodeBackup(new Backup(
                    this.sourceSchemaVersion,
                    this.sourceDataVersion,
                    this.mappings)));
            this.storage.flush();
            putBytes(this.storage, MANIFEST_KEY, encodeManifest(new Manifest(
                    this.sourceSchemaVersion,
                    this.sourceDataVersion,
                    State.UPGRADING,
                    CURRENT_SCHEMA_VERSION,
                    this.targetDataVersion,
                    "mapping upgrade in progress")));
            this.storage.flush();
            this.begun = true;
        }

        public void commit() {
            if (!this.upgradeRequired) {
                return;
            }
            putBytes(this.storage, MANIFEST_KEY, encodeManifest(new Manifest(
                    CURRENT_SCHEMA_VERSION,
                    this.targetDataVersion,
                    State.COMMITTED,
                    CURRENT_SCHEMA_VERSION,
                    this.targetDataVersion,
                    "committed")));
            this.storage.flush();
        }

        public void fail(Throwable failure) {
            if (!this.upgradeRequired || !this.begun) {
                return;
            }
            restoreMappings(this.storage, this.mappings);
            String detail = failure.getClass().getSimpleName() + ": " + String.valueOf(failure.getMessage());
            if (detail.length() > 512) {
                detail = detail.substring(0, 512);
            }
            putBytes(this.storage, MANIFEST_KEY, encodeManifest(new Manifest(
                    this.sourceSchemaVersion,
                    this.sourceDataVersion,
                    State.FAILED,
                    CURRENT_SCHEMA_VERSION,
                    this.targetDataVersion,
                    detail)));
            this.storage.flush();
        }
    }

    public static UpgradeSession prepare(
            IMappingStorage storage,
            Int2ObjectOpenHashMap<byte[]> rawMappings,
            int currentDataVersion) {
        validateNonNegativeVersion("running Minecraft data", currentDataVersion);
        byte[] manifestBytes = rawMappings.get(MANIFEST_KEY);
        Int2ObjectOpenHashMap<byte[]> mappings = dataMappings(rawMappings);
        validateDataMappings(mappings);

        if (manifestBytes == null) {
            return new UpgradeSession(storage, 0, 0, currentDataVersion, mappings, true, false);
        }

        Manifest manifest = decodeManifest(manifestBytes);
        if (manifest.state() != State.COMMITTED) {
            validateSupportedVersions(manifest.schemaVersion(), manifest.dataVersion(), currentDataVersion);
            if (manifest.targetSchemaVersion() > CURRENT_SCHEMA_VERSION) {
                throw new IllegalStateException(
                        "Mapping target schema " + manifest.targetSchemaVersion()
                                + " is newer than supported schema " + CURRENT_SCHEMA_VERSION);
            }
            if (manifest.targetDataVersion() > currentDataVersion) {
                throw new IllegalStateException(
                        "Mapping target Minecraft data version " + manifest.targetDataVersion()
                                + " is newer than the running version " + currentDataVersion);
            }
            byte[] backupBytes = rawMappings.get(BACKUP_KEY);
            if (backupBytes == null) {
                throw new IllegalStateException(
                        "Mapping upgrade is " + manifest.state() + " but no recovery backup exists");
            }
            Backup backup = decodeBackup(backupBytes);
            validateSupportedVersions(backup.schemaVersion(), backup.dataVersion(), currentDataVersion);
            if (backup.schemaVersion() != manifest.schemaVersion()
                    || backup.dataVersion() != manifest.dataVersion()) {
                throw new IllegalStateException(
                        "Mapping recovery backup version does not match the interrupted upgrade manifest");
            }
            restoreMappings(storage, backup.mappings());
            return new UpgradeSession(
                    storage,
                    backup.schemaVersion(),
                    backup.dataVersion(),
                    currentDataVersion,
                    backup.mappings(),
                    true,
                    true);
        }

        validateSupportedVersions(manifest.schemaVersion(), manifest.dataVersion(), currentDataVersion);
        boolean upgradeRequired = manifest.schemaVersion() < CURRENT_SCHEMA_VERSION
                || manifest.dataVersion() < currentDataVersion;
        return new UpgradeSession(
                storage,
                manifest.schemaVersion(),
                manifest.dataVersion(),
                currentDataVersion,
                mappings,
                upgradeRequired,
                false);
    }

    public static boolean isMetadataKey(int key) {
        return key == MANIFEST_KEY || key == BACKUP_KEY;
    }

    public static Int2ObjectOpenHashMap<byte[]> dataMappings(Int2ObjectOpenHashMap<byte[]> source) {
        Int2ObjectOpenHashMap<byte[]> output = new Int2ObjectOpenHashMap<>();
        for (var entry : source.int2ObjectEntrySet()) {
            if (!isMetadataKey(entry.getIntKey())) {
                output.put(entry.getIntKey(), Arrays.copyOf(entry.getValue(), entry.getValue().length));
            }
        }
        return output;
    }

    public static void validateDataMappings(Int2ObjectOpenHashMap<byte[]> mappings) {
        int maxBlock = 0;
        int blockCount = 0;
        int maxBiome = -1;
        int biomeCount = 0;
        for (var entry : mappings.int2ObjectEntrySet()) {
            int key = entry.getIntKey();
            int type = key >>> 30;
            int id = key & ((1 << 30) - 1);
            if (entry.getValue() == null || entry.getValue().length == 0) {
                throw new IllegalStateException("Empty mapping payload for key " + key);
            }
            if (type == 1) {
                if (id == 0) {
                    throw new IllegalStateException("Block mapping id 0 is reserved for air");
                }
                blockCount++;
                maxBlock = Math.max(maxBlock, id);
            } else if (type == 2) {
                biomeCount++;
                maxBiome = Math.max(maxBiome, id);
            } else {
                throw new IllegalStateException("Unknown mapping key namespace " + key);
            }
        }
        if (blockCount != maxBlock) {
            throw new IllegalStateException(
                    "Block mappings are not contiguous: count=" + blockCount + " maxId=" + maxBlock);
        }
        if (biomeCount != maxBiome + 1) {
            throw new IllegalStateException(
                    "Biome mappings are not contiguous: count=" + biomeCount + " maxId=" + maxBiome);
        }
    }

    public static byte[] encodeManifest(Manifest manifest) {
        return encodeEnvelope(MANIFEST_MAGIC, output -> {
            output.writeInt(manifest.schemaVersion());
            output.writeInt(manifest.dataVersion());
            output.writeByte(manifest.state().ordinal());
            output.writeInt(manifest.targetSchemaVersion());
            output.writeInt(manifest.targetDataVersion());
            output.writeUTF(manifest.detail() == null ? "" : manifest.detail());
        });
    }

    public static Manifest decodeManifest(byte[] encoded) {
        return decodeEnvelope(MANIFEST_MAGIC, encoded, input -> {
            int schemaVersion = input.readInt();
            int dataVersion = input.readInt();
            int stateOrdinal = input.readUnsignedByte();
            if (stateOrdinal >= State.values().length) {
                throw new IllegalStateException("Unknown mapping manifest state " + stateOrdinal);
            }
            return new Manifest(
                    schemaVersion,
                    dataVersion,
                    State.values()[stateOrdinal],
                    input.readInt(),
                    input.readInt(),
                    input.readUTF());
        });
    }

    public static byte[] encodeBackup(Backup backup) {
        validateDataMappings(backup.mappings());
        return encodeEnvelope(BACKUP_MAGIC, output -> {
            output.writeInt(backup.schemaVersion());
            output.writeInt(backup.dataVersion());
            var entries = backup.mappings().int2ObjectEntrySet().stream()
                    .sorted(Comparator.comparingInt(entry -> entry.getIntKey()))
                    .toList();
            output.writeInt(entries.size());
            for (var entry : entries) {
                output.writeInt(entry.getIntKey());
                output.writeInt(entry.getValue().length);
                output.write(entry.getValue());
            }
        });
    }

    public static Backup decodeBackup(byte[] encoded) {
        return decodeEnvelope(BACKUP_MAGIC, encoded, input -> {
            int schemaVersion = input.readInt();
            int dataVersion = input.readInt();
            int count = input.readInt();
            if (count < 0 || count > MAX_BACKUP_ENTRIES) {
                throw new IllegalStateException("Invalid mapping backup entry count " + count);
            }
            Int2ObjectOpenHashMap<byte[]> mappings = new Int2ObjectOpenHashMap<>(count);
            for (int index = 0; index < count; index++) {
                int key = input.readInt();
                int size = input.readInt();
                if (size <= 0 || size > MAX_ENTRY_BYTES) {
                    throw new IllegalStateException("Invalid mapping backup payload size " + size);
                }
                byte[] value = input.readNBytes(size);
                if (value.length != size || mappings.put(key, value) != null) {
                    throw new IllegalStateException("Truncated or duplicate mapping backup key " + key);
                }
            }
            validateDataMappings(mappings);
            return new Backup(schemaVersion, dataVersion, mappings);
        });
    }

    public static void putBytes(IMappingStorage storage, int key, byte[] value) {
        ByteBuffer buffer = MemoryUtil.memAlloc(value.length);
        try {
            buffer.put(value).flip();
            storage.putIdMapping(key, buffer);
        } finally {
            MemoryUtil.memFree(buffer);
        }
    }

    public static void restoreMappings(
            IMappingStorage storage,
            Int2ObjectOpenHashMap<byte[]> mappings) {
        validateDataMappings(mappings);
        for (var entry : mappings.int2ObjectEntrySet()) {
            putBytes(storage, entry.getIntKey(), entry.getValue());
        }
        storage.flush();
    }

    public static Int2ObjectOpenHashMap<byte[]> deepCopy(Int2ObjectOpenHashMap<byte[]> source) {
        Int2ObjectOpenHashMap<byte[]> output = new Int2ObjectOpenHashMap<>(source.size());
        for (var entry : source.int2ObjectEntrySet()) {
            output.put(entry.getIntKey(), Arrays.copyOf(entry.getValue(), entry.getValue().length));
        }
        return output;
    }

    private interface OutputWriter {
        void write(DataOutputStream output) throws IOException;
    }

    private interface InputReader<T> {
        T read(DataInputStream input) throws IOException;
    }

    private static byte[] encodeEnvelope(int magic, OutputWriter writer) {
        try {
            ByteArrayOutputStream payloadBytes = new ByteArrayOutputStream();
            try (DataOutputStream payload = new DataOutputStream(payloadBytes)) {
                payload.writeInt(ENVELOPE_VERSION);
                writer.write(payload);
            }
            byte[] body = payloadBytes.toByteArray();
            CRC32 crc = new CRC32();
            crc.update(body);
            ByteArrayOutputStream encodedBytes = new ByteArrayOutputStream(body.length + 16);
            try (DataOutputStream encoded = new DataOutputStream(encodedBytes)) {
                encoded.writeInt(magic);
                encoded.writeInt(body.length);
                encoded.write(body);
                encoded.writeInt((int) crc.getValue());
            }
            return encodedBytes.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to encode mapping metadata", exception);
        }
    }

    private static <T> T decodeEnvelope(int expectedMagic, byte[] encoded, InputReader<T> reader) {
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(encoded))) {
            if (input.readInt() != expectedMagic) {
                throw new IllegalStateException("Invalid mapping metadata magic");
            }
            int payloadSize = input.readInt();
            if (payloadSize < Integer.BYTES || payloadSize > encoded.length - 12) {
                throw new IllegalStateException("Invalid mapping metadata payload size " + payloadSize);
            }
            byte[] body = input.readNBytes(payloadSize);
            int expectedCrc = input.readInt();
            if (input.available() != 0) {
                throw new IllegalStateException("Trailing mapping metadata bytes");
            }
            CRC32 crc = new CRC32();
            crc.update(body);
            if ((int) crc.getValue() != expectedCrc) {
                throw new IllegalStateException("Mapping metadata checksum mismatch");
            }
            try (DataInputStream payload = new DataInputStream(new ByteArrayInputStream(body))) {
                int envelopeVersion = payload.readInt();
                if (envelopeVersion != ENVELOPE_VERSION) {
                    throw new IllegalStateException("Unsupported mapping metadata envelope " + envelopeVersion);
                }
                T result = reader.read(payload);
                if (payload.available() != 0) {
                    throw new IllegalStateException("Trailing mapping metadata payload");
                }
                return result;
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to decode mapping metadata", exception);
        }
    }

    private static void validateNonNegativeVersion(String label, int version) {
        if (version < 0) {
            throw new IllegalStateException("Negative mapping " + label + " version " + version);
        }
    }

    private static void validateSupportedVersions(int schemaVersion, int dataVersion, int currentDataVersion) {
        if (schemaVersion > CURRENT_SCHEMA_VERSION) {
            throw new IllegalStateException(
                    "Mapping schema " + schemaVersion + " is newer than supported schema "
                            + CURRENT_SCHEMA_VERSION);
        }
        if (dataVersion > currentDataVersion) {
            throw new IllegalStateException(
                    "Mapping Minecraft data version " + dataVersion
                            + " is newer than the running version " + currentDataVersion);
        }
    }
}
