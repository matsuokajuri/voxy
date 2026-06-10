package me.cortex.voxy.config;

import net.minecraftforge.common.ForgeConfigSpec;

public final class ForgeVoxyConfig {
    public static final ForgeConfigSpec CLIENT_SPEC;
    public static final ForgeConfigSpec.BooleanValue ENABLED;
    public static final ForgeConfigSpec.BooleanValue ENABLE_WORLD_ENGINE_SKELETON;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        builder.push("general");
        ENABLED = builder
                .comment("Placeholder toggle for the Forge 1.20.1 skeleton. It does not enable LoD rendering yet.")
                .define("enabled", true);
        ENABLE_WORLD_ENGINE_SKELETON = builder
                .comment("Creates an empty in-memory WorldEngine on client world join for lifecycle testing only. It does not ingest chunks or render LoD.")
                .define("enableWorldEngineSkeleton", false);
        builder.pop();
        CLIENT_SPEC = builder.build();
    }

    private ForgeVoxyConfig() {
    }
}
