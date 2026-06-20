package me.cortex.voxy.forge;

import me.cortex.voxy.config.ForgeVoxyConfig;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

final class ForgeOriginalVoxyServiceThreadPolicy {
    private static final String SODIUM_WORLD_RENDERER_CLASS =
            "me.jellysquid.mods.sodium.client.render.SodiumWorldRenderer";

    private ForgeOriginalVoxyServiceThreadPolicy() {
    }

    static Selection select() {
        int target;
        boolean useBuilderThreads;
        try {
            target = Math.max(1, ForgeVoxyConfig.ORIGINAL_VOXY_SERVICE_THREADS.get());
            useBuilderThreads = ForgeVoxyConfig.ORIGINAL_VOXY_USE_EMBEDDIUM_BUILDER_THREADS.get();
        } catch (IllegalStateException e) {
            return new Selection(
                    false,
                    false,
                    false,
                    0,
                    0,
                    0,
                    "forge-config-not-loaded",
                    e.getClass().getSimpleName() + ":" + String.valueOf(e.getMessage()));
        }
        EmbeddiumBuilderThreads builderThreads = useBuilderThreads
                ? queryEmbeddiumBuilderThreads()
                : new EmbeddiumBuilderThreads(false, 0, "disabled-by-config", "none");
        int dedicated = target;
        if (useBuilderThreads && builderThreads.available()) {
            dedicated = Math.max(1, target - builderThreads.threadCount());
        }
        return new Selection(
                true,
                useBuilderThreads,
                builderThreads.available(),
                target,
                dedicated,
                builderThreads.threadCount(),
                builderThreads.source(),
                builderThreads.failureReason());
    }

    private static EmbeddiumBuilderThreads queryEmbeddiumBuilderThreads() {
        try {
            Class<?> rendererClass = Class.forName(SODIUM_WORLD_RENDERER_CLASS);
            Method instanceNullable = rendererClass.getMethod("instanceNullable");
            Object renderer = instanceNullable.invoke(null);
            if (renderer == null) {
                return new EmbeddiumBuilderThreads(false, 0, "embeddium-renderer-not-attached", "none");
            }
            Field managerField = rendererClass.getDeclaredField("renderSectionManager");
            managerField.setAccessible(true);
            Object manager = managerField.get(renderer);
            if (manager == null) {
                return new EmbeddiumBuilderThreads(false, 0, "embeddium-render-section-manager-not-ready", "none");
            }
            Object builder = manager.getClass().getMethod("getBuilder").invoke(manager);
            if (builder == null) {
                return new EmbeddiumBuilderThreads(false, 0, "embeddium-builder-not-ready", "none");
            }
            Object count = builder.getClass().getMethod("getTotalThreadCount").invoke(builder);
            if (!(count instanceof Integer threadCount)) {
                return new EmbeddiumBuilderThreads(false, 0, "embeddium-builder-thread-count-invalid", "none");
            }
            return new EmbeddiumBuilderThreads(
                    true,
                    Math.max(0, threadCount),
                    "config-minus-embeddium-builder",
                    "none");
        } catch (ReflectiveOperationException | RuntimeException e) {
            return new EmbeddiumBuilderThreads(
                    false,
                    0,
                    "embeddium-builder-query-failed",
                    e.getClass().getSimpleName() + ":" + String.valueOf(e.getMessage()));
        }
    }

    record Selection(
            boolean configOwnerReady,
            boolean useEmbeddiumBuilderThreads,
            boolean embeddiumBuilderThreadCountAvailable,
            int targetThreadCount,
            int dedicatedThreadCount,
            int embeddiumBuilderThreadCount,
            String source,
            String failureReason
    ) {
    }

    private record EmbeddiumBuilderThreads(
            boolean available,
            int threadCount,
            String source,
            String failureReason
    ) {
    }
}
