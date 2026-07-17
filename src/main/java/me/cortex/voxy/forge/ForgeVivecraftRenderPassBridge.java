package me.cortex.voxy.forge;

import net.minecraftforge.fml.ModList;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

final class ForgeVivecraftRenderPassBridge {
    private static final String VIVECRAFT_MOD_ID = "vivecraft";
    private static final String VR_RENDERING_API = "org.vivecraft.api.client.VRRenderingAPI";
    private static Method instanceMethod;
    private static Method currentRenderPassMethod;
    private static boolean initialized;
    private static boolean incompatible;
    private static boolean warningLogged;

    private ForgeVivecraftRenderPassBridge() {
    }

    static RenderPassSelection currentSelection() {
        if (!ModList.get().isLoaded(VIVECRAFT_MOD_ID)) {
            return RenderPassSelection.vanilla();
        }
        if (!initialize()) {
            return RenderPassSelection.incompatible();
        }
        try {
            Object api = instanceMethod.invoke(null);
            if (api == null) {
                throw new IllegalStateException("VRRenderingAPI.instance() returned null");
            }
            Object pass = currentRenderPassMethod.invoke(api);
            if (pass == null) {
                // Original Voxy treats a null pass as the normal/default viewport.
                return RenderPassSelection.vanilla();
            }
            if (!(pass instanceof Enum<?> enumPass)) {
                throw new IllegalStateException("Vivecraft render pass is not an enum: " + pass.getClass().getName());
            }
            if ("VANILLA".equals(enumPass.name())) {
                return RenderPassSelection.vanilla();
            }
            return RenderPassSelection.dedicated(enumPass);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            incompatible = true;
            warnOnce("Vivecraft render-pass invocation failed; Voxy will skip its draw instead of sharing the vanilla viewport.", e);
            return RenderPassSelection.incompatible();
        }
    }

    private static synchronized boolean initialize() {
        if (initialized) {
            return !incompatible && instanceMethod != null && currentRenderPassMethod != null;
        }
        initialized = true;
        try {
            Class<?> type = Class.forName(VR_RENDERING_API);
            ApiMethods methods = inspectApiType(type);
            instanceMethod = methods.instanceMethod();
            currentRenderPassMethod = methods.currentRenderPassMethod();
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            incompatible = true;
            instanceMethod = null;
            currentRenderPassMethod = null;
            warnOnce("Vivecraft is loaded but its 1.20.1 rendering API is incompatible; Voxy will skip VR-pass drawing.", e);
        }
        return !incompatible && instanceMethod != null && currentRenderPassMethod != null;
    }

    static ApiMethods inspectApiType(Class<?> type) throws ReflectiveOperationException {
        Method instance = type.getMethod("instance");
        if (!Modifier.isStatic(instance.getModifiers()) || !type.isAssignableFrom(instance.getReturnType())) {
            throw new NoSuchMethodException(type.getName() + ".instance() must be static and return the API type");
        }
        Method currentRenderPass = type.getMethod("getCurrentRenderPass");
        Class<?> renderPassType = currentRenderPass.getReturnType();
        if (!renderPassType.isEnum()) {
            throw new NoSuchMethodException(type.getName() + ".getCurrentRenderPass() must return the RenderPass enum");
        }
        boolean hasVanilla = false;
        for (Object constant : renderPassType.getEnumConstants()) {
            if (constant instanceof Enum<?> enumConstant && "VANILLA".equals(enumConstant.name())) {
                hasVanilla = true;
                break;
            }
        }
        if (!hasVanilla) {
            throw new NoSuchFieldException(renderPassType.getName() + ".VANILLA");
        }
        return new ApiMethods(instance, currentRenderPass, renderPassType);
    }

    private static synchronized void warnOnce(String message, Throwable failure) {
        if (warningLogged) {
            return;
        }
        warningLogged = true;
        VoxyForge.LOGGER.warn(message, failure);
    }

    record ApiMethods(Method instanceMethod, Method currentRenderPassMethod, Class<?> renderPassType) {
    }

    record RenderPassSelection(Object viewportKey, String label, boolean skipVoxy) {
        private static RenderPassSelection vanilla() {
            return new RenderPassSelection(null, "vanilla", false);
        }

        private static RenderPassSelection dedicated(Enum<?> pass) {
            return new RenderPassSelection(pass, pass.name(), false);
        }

        private static RenderPassSelection incompatible() {
            return new RenderPassSelection(null, "incompatible", true);
        }
    }
}
