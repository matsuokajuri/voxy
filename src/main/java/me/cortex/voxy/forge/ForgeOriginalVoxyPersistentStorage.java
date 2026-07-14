package me.cortex.voxy.forge;

import me.cortex.voxy.common.config.ConfigBuildCtx;
import me.cortex.voxy.common.config.section.SectionStorage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.world.level.storage.LevelResource;

import java.nio.file.Path;

/** Original Voxy's configured section-storage owner and default persistent chain. */
final class ForgeOriginalVoxyPersistentStorage {
    record Identity(Path basePath, WorldIdentifier worldIdentifier) {
        Identity {
            basePath = basePath.toAbsolutePath().normalize();
        }

        Path storagePath() {
            return this.basePath.resolve(this.worldIdentifier.getWorldId()).resolve("storage");
        }
    }

    /**
     * Storage configuration captured by one client network session.
     *
     * <p>Original {@code VoxyClientInstance} resolves its base path and loads {@code config.json}
     * in its constructor. Keeping the loaded config here gives the Forge event-shell the same
     * lifetime: reconnecting creates a new session and therefore rereads the file, while late
     * callbacks from an old level cannot resolve against the new connection's server address.</p>
     */
    record Session(
            Path basePath,
            String playerUuid,
            ForgeOriginalVoxyStorageConfig.Loaded configuration) {
        Session {
            basePath = basePath.toAbsolutePath().normalize();
        }

        boolean ready() {
            return this.configuration.ready() && !this.configuration.config().disabled;
        }

        Identity identityForCurrentWorld(Minecraft minecraft) {
            ClientLevel level = minecraft.level;
            if (level == null) {
                throw new IllegalStateException("Client level is unavailable");
            }
            return this.identityForLevel(level);
        }

        Identity identityForLevel(ClientLevel level) {
            WorldIdentifier identifier = ((IWorldGetIdentifier) level).voxy$getIdentifier();
            if (identifier == null) {
                throw new IllegalStateException("Original Voxy world identifier was not captured");
            }
            return new Identity(this.basePath, identifier);
        }

        SectionStorage open(Identity identity) {
            if (!identity.basePath().equals(this.basePath)) {
                throw new IllegalArgumentException("Storage identity belongs to another Voxy session");
            }
            if (!this.ready()) {
                throw new IllegalStateException(
                        "Original Voxy storage is unavailable for this session: " + this.configuration.path());
            }
            ConfigBuildCtx context = new ConfigBuildCtx();
            context.setProperty(ConfigBuildCtx.BASE_SAVE_PATH, identity.basePath().toString());
            context.setProperty(ConfigBuildCtx.WORLD_IDENTIFIER, identity.worldIdentifier().getWorldId());
            context.setProperty(ConfigBuildCtx.PLAYER_UUID, this.playerUuid);
            context.pushPath(ConfigBuildCtx.DEFAULT_STORAGE_PATH);
            return this.configuration.config().sectionStorageConfig.build(context);
        }
    }

    private ForgeOriginalVoxyPersistentStorage() {
    }

    static Session beginSession(Minecraft minecraft, ClientPacketListener connection) {
        Path basePath = resolveBasePath(minecraft, connection).toAbsolutePath().normalize();
        ForgeOriginalVoxyStorageConfig.Loaded configuration =
                ForgeOriginalVoxyStorageConfig.loadOrCreate(basePath);
        String playerUuid = minecraft.getUser().getGameProfile().getId().toString().replace(':', '-');
        return new Session(basePath, playerUuid, configuration);
    }

    private static Path resolveBasePath(Minecraft minecraft, ClientPacketListener sessionConnection) {
        var integratedServer = minecraft.getSingleplayerServer();
        if (integratedServer != null) {
            return integratedServer.getWorldPath(LevelResource.ROOT).resolve("voxy").toAbsolutePath();
        }

        Path basePath = minecraft.gameDirectory.toPath().resolve(".voxy").resolve("saves");
        var connection = sessionConnection != null ? sessionConnection : minecraft.getConnection();
        if (connection == null) {
            VoxyForge.LOGGER.error("Network handle null while resolving original Voxy storage path");
            return basePath.resolve("UNKNOWN").toAbsolutePath();
        }
        var serverData = connection.getServerData();
        if (serverData == null) {
            VoxyForge.LOGGER.error("Server info null while resolving original Voxy storage path");
            return basePath.resolve("UNKNOWN").toAbsolutePath();
        }
        // ServerData gained isRealm() after 1.20.1; Minecraft owns the equivalent
        // connection classification in this target version.
        return basePath.resolve(minecraft.isConnectedToRealms() ? "realms" : serverData.ip.replace(":", "_")).toAbsolutePath();
    }
}
