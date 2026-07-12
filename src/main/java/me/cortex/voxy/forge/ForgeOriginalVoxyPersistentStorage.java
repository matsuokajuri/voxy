package me.cortex.voxy.forge;

import me.cortex.voxy.common.config.section.SectionSerializationStorage;
import me.cortex.voxy.common.config.section.SectionStorage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.storage.LevelResource;

import java.nio.file.Files;
import java.nio.file.Path;

/** Original Voxy's default Serializer -> ZSTD(level 1) -> RocksDB storage chain. */
final class ForgeOriginalVoxyPersistentStorage {
    record Identity(Path basePath, ForgeOriginalVoxyWorldIdentifier worldIdentifier) {
        Identity {
            basePath = basePath.toAbsolutePath().normalize();
        }

        Path storagePath() {
            return this.basePath.resolve(this.worldIdentifier.getWorldId()).resolve("storage");
        }
    }

    private ForgeOriginalVoxyPersistentStorage() {
    }

    static Identity identityForCurrentWorld(Minecraft minecraft) {
        ClientLevel level = minecraft.level;
        if (level == null) {
            throw new IllegalStateException("Client level is unavailable");
        }
        ForgeOriginalVoxyWorldIdentifier identifier =
                ((ForgeOriginalVoxyWorldIdentifierAccess) level).voxy$getOriginalVoxyWorldIdentifier();
        if (identifier == null) {
            throw new IllegalStateException("Original Voxy world identifier was not captured");
        }
        return new Identity(resolveBasePath(minecraft), identifier);
    }

    static SectionStorage open(Identity identity) {
        try {
            Files.createDirectories(identity.storagePath());
        } catch (Exception e) {
            throw new RuntimeException("Failed to create original Voxy storage path " + identity.storagePath(), e);
        }
        var backend = new ForgeOriginalVoxyRocksDBStorageBackend(identity.storagePath().toString());
        var compressed = new ForgeOriginalVoxyCompressionStorageAdaptor(
                new ForgeOriginalVoxyZstdCompressor(1),
                backend);
        return new SectionSerializationStorage(compressed);
    }

    private static Path resolveBasePath(Minecraft minecraft) {
        var integratedServer = minecraft.getSingleplayerServer();
        if (integratedServer != null) {
            return integratedServer.getWorldPath(LevelResource.ROOT).resolve("voxy").toAbsolutePath();
        }

        Path basePath = minecraft.gameDirectory.toPath().resolve(".voxy").resolve("saves");
        var connection = minecraft.getConnection();
        if (connection == null) {
            VoxyForge.LOGGER.error("Network handle null while resolving original Voxy storage path");
            return basePath.resolve("UNKNOWN").toAbsolutePath();
        }
        var serverData = connection.getServerData();
        if (serverData == null) {
            VoxyForge.LOGGER.error("Server info null while resolving original Voxy storage path");
            return basePath.resolve("UNKNOWN").toAbsolutePath();
        }
        return basePath.resolve(minecraft.isConnectedToRealms() ? "realms" : serverData.ip.replace(":", "_")).toAbsolutePath();
    }
}
