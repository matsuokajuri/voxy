package me.cortex.voxy.forge;

import net.minecraft.SharedConstants;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class ForgeOriginalVoxyPersistentStorageIdentityTest {
    @TempDir
    Path temporaryDirectory;

    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void serverAndWorldIdentitiesResolveToIsolatedStoragePaths() {
        ForgeOriginalVoxyWorldIdentifier overworld = identifier("overworld", 41L);
        ForgeOriginalVoxyWorldIdentifier nether = identifier("the_nether", 41L);
        ForgeOriginalVoxyWorldIdentifier alternateSeed = identifier("overworld", 42L);

        Path serverA = this.temporaryDirectory.resolve("server-a");
        Path serverB = this.temporaryDirectory.resolve("server-b");
        ForgeOriginalVoxyPersistentStorage.Identity serverAOverworld =
                new ForgeOriginalVoxyPersistentStorage.Identity(serverA, overworld);
        ForgeOriginalVoxyPersistentStorage.Identity serverANether =
                new ForgeOriginalVoxyPersistentStorage.Identity(serverA, nether);
        ForgeOriginalVoxyPersistentStorage.Identity serverAAlternateSeed =
                new ForgeOriginalVoxyPersistentStorage.Identity(serverA, alternateSeed);
        ForgeOriginalVoxyPersistentStorage.Identity serverBOverworld =
                new ForgeOriginalVoxyPersistentStorage.Identity(serverB, overworld);

        assertNotEquals(serverAOverworld.storagePath(), serverANether.storagePath());
        assertNotEquals(serverAOverworld.storagePath(), serverAAlternateSeed.storagePath());
        assertNotEquals(serverAOverworld.storagePath(), serverBOverworld.storagePath());
        assertEquals(
                serverA.toAbsolutePath().normalize().resolve(overworld.getWorldId()).resolve("storage"),
                serverAOverworld.storagePath());
    }

    private static ForgeOriginalVoxyWorldIdentifier identifier(String dimension, long biomeSeed) {
        ResourceKey<Level> key = ResourceKey.create(
                net.minecraft.core.registries.Registries.DIMENSION,
                new ResourceLocation("minecraft", dimension));
        return new ForgeOriginalVoxyWorldIdentifier(key, biomeSeed, null);
    }
}
