package me.cortex.voxy.config;

import me.cortex.voxy.common.util.cpu.CpuLayout;
import net.minecraftforge.common.ForgeConfigSpec;

public final class ForgeVoxyConfig {
    public static final ForgeConfigSpec CLIENT_SPEC;
    public static final ForgeConfigSpec.BooleanValue ENABLED;
    public static final ForgeConfigSpec.BooleanValue RENDERING_ENABLED;
    public static final ForgeConfigSpec.BooleanValue INGEST_ENABLED;
    public static final ForgeConfigSpec.IntValue ORIGINAL_VOXY_SERVICE_THREADS;
    public static final ForgeConfigSpec.BooleanValue ORIGINAL_VOXY_USE_EMBEDDIUM_BUILDER_THREADS;
    public static final ForgeConfigSpec.DoubleValue ORIGINAL_VOXY_SECTION_RENDER_DISTANCE;
    public static final ForgeConfigSpec.DoubleValue ORIGINAL_VOXY_SUBDIVISION_SIZE;
    public static final ForgeConfigSpec.BooleanValue ORIGINAL_VOXY_USE_ENVIRONMENTAL_FOG;
    public static final ForgeConfigSpec.ConfigValue<String> ORIGINAL_VOXY_SSAO_MODE;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        builder.push("general");
        ENABLED = builder
                .comment("Enables the Forge original-Voxy parity route.")
                .define("enabled", true);
        RENDERING_ENABLED = builder
                .comment("Forge equivalent of original VoxyConfig.enableRendering. World ingest may remain active while rendering is disabled.")
                .define("enableRendering", true);
        INGEST_ENABLED = builder
                .comment("Forge equivalent of original VoxyConfig.ingestEnabled.")
                .define("ingestEnabled", true);
        ORIGINAL_VOXY_SERVICE_THREADS = builder
                .comment("Original Voxy serviceThreads target. The effective dedicated Voxy pool subtracts Embeddium builder threads when originalVoxyUseEmbeddiumBuilderThreads is true.")
                .defineInRange("originalVoxyServiceThreads", defaultServiceThreads(), 1, maxServiceThreads());
        ORIGINAL_VOXY_USE_EMBEDDIUM_BUILDER_THREADS = builder
                .comment("Forge equivalent of original Voxy's Sodium-thread sharing option, using Embeddium builder workers when available.")
                .define("originalVoxyUseEmbeddiumBuilderThreads", true);
        ORIGINAL_VOXY_SECTION_RENDER_DISTANCE = builder
                .comment("Forge equivalent of original VoxyConfig.CONFIG.sectionRenderDistance, used by RenderDistanceTracker and hierarchical traversal.")
                .defineInRange("originalVoxySectionRenderDistance", 16.0D, 10.0D / 16.0D, 512.0D);
        ORIGINAL_VOXY_SUBDIVISION_SIZE = builder
                .comment("Forge equivalent of original VoxyConfig.CONFIG.subDivisionSize, used by hierarchical traversal screen-space descent.")
                .defineInRange("originalVoxySubDivisionSize", 64.0D, 1.0D, 512.0D);
        ORIGINAL_VOXY_USE_ENVIRONMENTAL_FOG = builder
                .comment("Forge equivalent of original VoxyConfig.CONFIG.useEnvironmentalFog for the original-shaped render pipeline final blit.")
                .define("originalVoxyUseEnvironmentalFog", true);
        ORIGINAL_VOXY_SSAO_MODE = builder
                .comment("Forge equivalent of original VoxyConfig.ssaoMode: AUTO, BASIC, BETTER, or BEST. Invalid values fall back to AUTO.")
                .define("ssaoMode", "AUTO");

        builder.pop();
        CLIENT_SPEC = builder.build();
    }

    private ForgeVoxyConfig() {
    }

    public static boolean isEnabledEarlySafe() {
        try {
            return ENABLED.get() && RENDERING_ENABLED.get();
        } catch (IllegalStateException ignored) {
            return true;
        }
    }

    private static int defaultServiceThreads() {
        return Math.max((int) (CpuLayout.getCoreCount() / 1.5D), 1);
    }

    private static int maxServiceThreads() {
        return Math.max(CpuLayout.getCoreCount(), 1);
    }

    public static int serviceThreadMaximum() {
        return maxServiceThreads();
    }
}
