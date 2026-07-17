package me.cortex.voxy.forge;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.world.level.block.state.BlockState;
import net.irisshaders.iris.shaderpack.materialmap.WorldRenderingSettings;

import javax.annotation.Nullable;

final class ForgeOculusWorldRenderingSettingsBridge {
    private ForgeOculusWorldRenderingSettingsBridge() {
    }

    static Result getBlockStateIds() {
        if (!ForgeOculusAvailability.installed()) {
            return new Result(true, null, "oculus-not-installed", "none");
        }
        return getBlockStateIds0();
    }

    private static Result getBlockStateIds0() {
        try {
            Object value = WorldRenderingSettings.INSTANCE.getBlockStateIds();
            if (value == null) {
                return new Result(true, null, "oculus-world-rendering-settings-null", "none");
            }
            if (!(value instanceof Object2IntMap<?> map)) {
                return new Result(false, null, "oculus-world-rendering-settings", "oculus-block-state-id-map-wrong-type");
            }
            @SuppressWarnings("unchecked")
            Object2IntMap<BlockState> typed = (Object2IntMap<BlockState>) map;
            return new Result(true, typed, "oculus-world-rendering-settings", "none");
        } catch (RuntimeException | LinkageError e) {
            return new Result(false, null, "oculus-world-rendering-settings", "oculus-block-state-id-map-" + e.getClass().getSimpleName());
        }
    }

    static ReloadState isReloadRequired() {
        if (!ForgeOculusAvailability.installed()) {
            return new ReloadState(true, false, "oculus-not-installed", "none");
        }
        return isReloadRequired0();
    }

    private static ReloadState isReloadRequired0() {
        try {
            Object value = WorldRenderingSettings.INSTANCE.isReloadRequired();
            if (!(value instanceof Boolean reloadRequired)) {
                return new ReloadState(false, false, "oculus-world-rendering-settings", "oculus-reload-flag-wrong-type");
            }
            return new ReloadState(true, reloadRequired, "oculus-world-rendering-settings", "none");
        } catch (RuntimeException | LinkageError e) {
            return new ReloadState(
                    false,
                    false,
                    "oculus-world-rendering-settings",
                    "oculus-reload-flag-" + e.getClass().getSimpleName());
        }
    }

    record Result(
            boolean ready,
            @Nullable Object2IntMap<BlockState> blockStateIds,
            String source,
            String failureReason
    ) {
    }

    record ReloadState(
            boolean ready,
            boolean reloadRequired,
            String source,
            String failureReason
    ) {
    }
}
