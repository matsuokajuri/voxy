package me.cortex.voxy.platform;

import java.nio.file.Path;

public interface PlatformServices {
    Path getGameDir();

    Path getConfigDir();

    boolean isModLoaded(String modid);

    String getModVersion();

    boolean isClient();
}
