package me.cortex.voxy.forge;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

final class ForgeOculusWorldRenderingSettingsBridge {
    private static final String WORLD_RENDERING_SETTINGS = "net.irisshaders.iris.shaderpack.materialmap.WorldRenderingSettings";

    private ForgeOculusWorldRenderingSettingsBridge() {
    }

    static Result getBlockStateIds() {
        try {
            Class<?> type = Class.forName(WORLD_RENDERING_SETTINGS);
            Field instanceField = type.getField("INSTANCE");
            Object instance = instanceField.get(null);
            Method method = type.getMethod("getBlockStateIds");
            Object value = method.invoke(instance);
            if (value == null) {
                return new Result(true, null, "oculus-world-rendering-settings-null", "none");
            }
            if (!(value instanceof Object2IntMap<?> map)) {
                return new Result(false, null, "oculus-world-rendering-settings", "oculus-block-state-id-map-wrong-type");
            }
            @SuppressWarnings("unchecked")
            Object2IntMap<BlockState> typed = (Object2IntMap<BlockState>) map;
            return new Result(true, typed, "oculus-world-rendering-settings", "none");
        } catch (ReflectiveOperationException | RuntimeException e) {
            return new Result(false, null, "oculus-world-rendering-settings", "oculus-block-state-id-map-" + e.getClass().getSimpleName());
        }
    }

    static ReloadState isReloadRequired() {
        try {
            Class<?> type = Class.forName(WORLD_RENDERING_SETTINGS);
            Field instanceField = type.getField("INSTANCE");
            Object instance = instanceField.get(null);
            Method method = type.getMethod("isReloadRequired");
            Object value = method.invoke(instance);
            if (!(value instanceof Boolean reloadRequired)) {
                return new ReloadState(false, false, "oculus-world-rendering-settings", "oculus-reload-flag-wrong-type");
            }
            return new ReloadState(true, reloadRequired, "oculus-world-rendering-settings", "none");
        } catch (ReflectiveOperationException | RuntimeException e) {
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
