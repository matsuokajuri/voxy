package me.cortex.voxy.platform;

import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.fml.loading.FMLPaths;

import java.nio.file.Path;

public final class ForgePlatformServices implements PlatformServices {
    private final String modid;

    public ForgePlatformServices(String modid) {
        this.modid = modid;
    }

    @Override
    public Path getGameDir() {
        return FMLPaths.GAMEDIR.get();
    }

    @Override
    public Path getConfigDir() {
        return FMLPaths.CONFIGDIR.get();
    }

    @Override
    public boolean isModLoaded(String modid) {
        return ModList.get().isLoaded(modid);
    }

    @Override
    public String getModVersion() {
        return ModList.get()
                .getModContainerById(this.modid)
                .map(container -> container.getModInfo().getVersion().toString())
                .orElse("unknown");
    }

    @Override
    public boolean isClient() {
        return FMLEnvironment.dist.isClient();
    }
}
