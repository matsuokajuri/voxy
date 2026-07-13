package me.cortex.voxy.forge;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/** Client-global startup gate matching the original VoxyClient initialization contract. */
final class ForgeOriginalVoxyClientRuntime {
    private static FileChannel lockChannel;
    private static FileLock exclusiveLock;
    private static boolean initialized;
    private static boolean available;
    private static boolean sharedIndexInitialized;

    private ForgeOriginalVoxyClientRuntime() {
    }

    static synchronized boolean initialize() {
        if (initialized) {
            return available;
        }
        initialized = true;
        if (!RenderSystem.isOnRenderThreadOrInit()) {
            VoxyForge.LOGGER.error("Voxy client runtime initialization did not run on the render thread; disabling Voxy.");
            return false;
        }

        Capabilities capabilities = Capabilities.INSTANCE;
        if (capabilities.hasBrokenDepthSampler) {
            VoxyForge.LOGGER.error(
                    "AMD broken depth sampler detected; Voxy cannot render correctly and has been disabled.");
        }
        if (!capabilities.systemSupported()) {
            VoxyForge.LOGGER.error("Voxy is unsupported on this system: {}.", capabilities.unsupportedReason());
            return false;
        }
        if (Boolean.parseBoolean(System.getProperty("voxy.exclusiveLock", "false")) && !acquireExclusiveLock()) {
            VoxyForge.LOGGER.error("Failed to acquire the exclusive Voxy lock file; Voxy has been disabled.");
            return false;
        }
        if (!capabilities.subgroup) {
            VoxyForge.LOGGER.warn("GPU does not support subgroup operations; expect some performance degradation.");
        }
        //Original Voxy eagerly initializes the client-global shared index buffer at startup.
        SharedIndexBuffer.INSTANCE.id();
        sharedIndexInitialized = true;
        available = true;
        return true;
    }

    static synchronized boolean isAvailable() {
        return initialized && available;
    }

    static synchronized void shutdown() {
        available = false;
        PrintfDebugUtil.free();
        if (sharedIndexInitialized) {
            SharedIndexBuffer.freeAll();
            sharedIndexInitialized = false;
        }
        if (exclusiveLock != null) {
            try {
                exclusiveLock.release();
            } catch (IOException e) {
                VoxyForge.LOGGER.warn("Failed to release the exclusive Voxy lock cleanly.", e);
            }
            exclusiveLock = null;
        }
        if (lockChannel != null) {
            try {
                lockChannel.close();
            } catch (IOException e) {
                VoxyForge.LOGGER.warn("Failed to close the exclusive Voxy lock channel cleanly.", e);
            }
            lockChannel = null;
        }
    }

    private static boolean acquireExclusiveLock() {
        Path directory = Minecraft.getInstance().gameDirectory.toPath().resolve(".voxy");
        Path lockPath = directory.resolve("voxy.lock");
        try {
            Files.createDirectories(directory);
            lockChannel = FileChannel.open(
                    lockPath,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.WRITE);
            //Original Voxy uses FileChannel.lock(): when the opt-in property is set, startup waits
            // for exclusive ownership instead of silently continuing without the requested guard.
            exclusiveLock = lockChannel.lock(0L, Long.MAX_VALUE, false);
            return true;
        } catch (IOException e) {
            if (lockChannel != null) {
                try {
                    lockChannel.close();
                } catch (IOException ignored) {
                }
                lockChannel = null;
            }
            return false;
        }
    }
}
