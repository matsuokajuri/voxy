package me.cortex.voxy.config;

import net.minecraftforge.common.ForgeConfigSpec;

public final class ForgeVoxyConfig {
    public static final ForgeConfigSpec CLIENT_SPEC;
    public static final ForgeConfigSpec.BooleanValue ENABLED;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        builder.push("general");
        ENABLED = builder
                .comment("Placeholder toggle for the Forge 1.20.1 skeleton. It does not enable LoD rendering yet.")
                .define("enabled", true);
        builder.pop();
        CLIENT_SPEC = builder.build();
    }

    private ForgeVoxyConfig() {
    }
}
