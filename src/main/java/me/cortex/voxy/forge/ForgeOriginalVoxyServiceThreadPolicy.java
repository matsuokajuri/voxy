package me.cortex.voxy.forge;

import me.cortex.voxy.config.ForgeVoxyConfig;
import me.cortex.voxy.forge.mixin.ForgeOriginalVoxyEmbeddiumWorldRendererAccessor;
import me.jellysquid.mods.sodium.client.render.SodiumWorldRenderer;
import me.jellysquid.mods.sodium.client.render.chunk.RenderSectionManager;

final class ForgeOriginalVoxyServiceThreadPolicy {
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
        SodiumWorldRenderer renderer = SodiumWorldRenderer.instanceNullable();
        if (renderer == null) {
            return new EmbeddiumBuilderThreads(false, 0, "none");
        }
        if (!(renderer instanceof ForgeOriginalVoxyEmbeddiumWorldRendererAccessor accessor)) {
            throw new IllegalStateException("embeddium-world-renderer-accessor-missing");
        }
        RenderSectionManager manager = accessor.voxy$getRenderSectionManager();
        if (manager == null || manager.getBuilder() == null) {
            return new EmbeddiumBuilderThreads(false, 0, "none");
        }
        return new EmbeddiumBuilderThreads(
                true,
                Math.max(0, manager.getBuilder().getTotalThreadCount()),
                "none");
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
