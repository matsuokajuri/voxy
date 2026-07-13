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
                    0,
                    e.getClass().getSimpleName() + ":" + String.valueOf(e.getMessage()));
        }
        EmbeddiumBuilderThreads builderThreads = useBuilderThreads
                ? queryEmbeddiumBuilderThreads()
                : new EmbeddiumBuilderThreads(false, 0, "none");
        int dedicated = target;
        if (useBuilderThreads && builderThreads.available()) {
            dedicated = Math.max(1, target - builderThreads.threadCount());
        }
        return new Selection(
                true,
                useBuilderThreads,
                dedicated,
                builderThreads.failureReason());
    }

    private static EmbeddiumBuilderThreads queryEmbeddiumBuilderThreads() {
        try {
            Class<?> rendererClass = Class.forName(SODIUM_WORLD_RENDERER_CLASS);
            Method instanceNullable = rendererClass.getMethod("instanceNullable");
            Object renderer = instanceNullable.invoke(null);
            if (renderer == null) {
                return new EmbeddiumBuilderThreads(false, 0, "none");
            }
            Field managerField = rendererClass.getDeclaredField("renderSectionManager");
            managerField.setAccessible(true);
            Object manager = managerField.get(renderer);
            if (manager == null) {
                return new EmbeddiumBuilderThreads(false, 0, "none");
            }
            Object builder = manager.getClass().getMethod("getBuilder").invoke(manager);
            if (builder == null) {
                return new EmbeddiumBuilderThreads(false, 0, "none");
            }
            Object count = builder.getClass().getMethod("getTotalThreadCount").invoke(builder);
            if (!(count instanceof Integer threadCount)) {
                return new EmbeddiumBuilderThreads(false, 0, "none");
            }
            return new EmbeddiumBuilderThreads(
                    true,
                    Math.max(0, threadCount),
                    "none");
        } catch (ReflectiveOperationException | RuntimeException e) {
            return new EmbeddiumBuilderThreads(
                    false,
                    0,
                    e.getClass().getSimpleName() + ":" + String.valueOf(e.getMessage()));
        }
    }

    record Selection(
            boolean configOwnerReady,
            boolean useEmbeddiumBuilderThreads,
            int dedicatedThreadCount,
            String failureReason
    ) {
    }

    private record EmbeddiumBuilderThreads(
            boolean available,
            int threadCount,
            String failureReason
    ) {
    }
}
