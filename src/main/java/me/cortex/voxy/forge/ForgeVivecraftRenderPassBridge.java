package me.cortex.voxy.forge;

import java.lang.reflect.Method;

final class ForgeVivecraftRenderPassBridge {
    private static final String VR_RENDERING_API = "org.vivecraft.api.client.VRRenderingAPI";
    private static Method instanceMethod;
    private static Method currentRenderPassMethod;
    private static boolean initialized;

    private ForgeVivecraftRenderPassBridge() {
    }

    static Object currentNonVanillaRenderPass() {
        if (!initialize()) {
            return null;
        }
        try {
            Object api = instanceMethod.invoke(null);
            if (api == null) {
                return null;
            }
            Object pass = currentRenderPassMethod.invoke(api);
            if (pass == null
                    || pass instanceof Enum<?> enumPass && "VANILLA".equals(enumPass.name())
                    || "VANILLA".equals(String.valueOf(pass))) {
                return null;
            }
            return pass;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
    }

    private static boolean initialize() {
        if (initialized) {
            return instanceMethod != null && currentRenderPassMethod != null;
        }
        initialized = true;
        try {
            Class<?> type = Class.forName(VR_RENDERING_API);
            instanceMethod = type.getMethod("instance");
            currentRenderPassMethod = type.getMethod("getCurrentRenderPass");
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            instanceMethod = null;
            currentRenderPassMethod = null;
        }
        return instanceMethod != null && currentRenderPassMethod != null;
    }
}
