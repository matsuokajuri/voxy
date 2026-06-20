package me.cortex.voxy.forge;

import java.lang.reflect.Field;

final class ForgeOculusShadowStateBridge {
    private static final String SHADOW_RENDERER = "net.irisshaders.iris.shadows.ShadowRenderer";
    private static Field activeField;
    private static boolean initialized;

    private ForgeOculusShadowStateBridge() {
    }

    static boolean shadowActive() {
        Field field = activeField();
        if (field == null) {
            return false;
        }
        try {
            return field.getBoolean(null);
        } catch (IllegalAccessException | RuntimeException ignored) {
            return false;
        }
    }

    private static Field activeField() {
        if (initialized) {
            return activeField;
        }
        initialized = true;
        try {
            Class<?> type = Class.forName(SHADOW_RENDERER);
            Field field = type.getField("ACTIVE");
            field.setAccessible(true);
            activeField = field;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            activeField = null;
        }
        return activeField;
    }
}
