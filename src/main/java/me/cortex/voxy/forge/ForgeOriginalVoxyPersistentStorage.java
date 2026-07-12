package me.cortex.voxy.forge;

import me.cortex.voxy.common.config.ConfigBuildCtx;
import me.cortex.voxy.common.config.section.SectionStorage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.storage.LevelResource;

import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Original Voxy's configured section-storage owner and default persistent chain. */
final class ForgeOriginalVoxyPersistentStorage {
    private static final Map<Path, ForgeOriginalVoxyStorageConfig.Loaded> CONFIGS = new ConcurrentHashMap<>();

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
        ForgeOriginalVoxyStorageConfig.Loaded loaded = configuration(identity);
        if (loaded.config().disabled) {
            throw new IllegalStateException("Original Voxy storage is disabled by " + loaded.path());
        }
        ConfigBuildCtx context = new ConfigBuildCtx();
        context.setProperty(ConfigBuildCtx.BASE_SAVE_PATH, identity.basePath().toString());
        context.setProperty(ConfigBuildCtx.WORLD_IDENTIFIER, identity.worldIdentifier().getWorldId());
        context.setProperty(
                ConfigBuildCtx.PLAYER_UUID,
                Minecraft.getInstance().getUser().getGameProfile().getId().toString().replace(':', '-'));
        context.pushPath(ConfigBuildCtx.DEFAULT_STORAGE_PATH);
        return loaded.config().sectionStorageConfig.build(context);
    }

    static ForgeOriginalVoxyStorageConfig.Loaded configuration(Identity identity) {
        return CONFIGS.computeIfAbsent(
                identity.basePath(),
                ForgeOriginalVoxyStorageConfig::loadOrCreate);
    }

    static boolean disabled(Identity identity) {
        return configuration(identity).config().disabled;
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
