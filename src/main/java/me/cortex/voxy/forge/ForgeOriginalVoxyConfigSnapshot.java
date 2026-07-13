package me.cortex.voxy.forge;

import me.cortex.voxy.config.ForgeVoxyConfig;

/** Shared configuration state and apply semantics for the Forge and Embeddium screens. */
record ForgeOriginalVoxyConfigSnapshot(
        boolean enabled,
        boolean renderingEnabled,
        boolean ingestEnabled,
        int serviceThreads,
        boolean useEmbeddiumThreads,
        double renderDistance,
        double subdivisionSize,
        boolean environmentalFog,
        String ssaoMode) {
    static final int SUBDIVISION_INPUT_MAX = 100;
    static final int RENDER_DISTANCE_INPUT_MIN = 10;
    static final int RENDER_DISTANCE_INPUT_MAX = 64 * 16;

    private static final double SUBDIVISION_MIN = 28.0D;
    private static final double SUBDIVISION_MAX = 256.0D;
    private static final double SUBDIVISION_CONSTANT =
            Math.log(SUBDIVISION_MAX / SUBDIVISION_MIN) / Math.log(2.0D);

    ForgeOriginalVoxyConfigSnapshot {
        ssaoMode = normalizeSsaoMode(ssaoMode);
    }

    static ForgeOriginalVoxyConfigSnapshot load() {
        return new ForgeOriginalVoxyConfigSnapshot(
                ForgeVoxyConfig.ENABLED.get(),
                ForgeVoxyConfig.RENDERING_ENABLED.get(),
                ForgeVoxyConfig.INGEST_ENABLED.get(),
                ForgeVoxyConfig.ORIGINAL_VOXY_SERVICE_THREADS.get(),
                ForgeVoxyConfig.ORIGINAL_VOXY_USE_EMBEDDIUM_BUILDER_THREADS.get(),
                ForgeVoxyConfig.ORIGINAL_VOXY_SECTION_RENDER_DISTANCE.get(),
                ForgeVoxyConfig.ORIGINAL_VOXY_SUBDIVISION_SIZE.get(),
                ForgeVoxyConfig.ORIGINAL_VOXY_USE_ENVIRONMENTAL_FOG.get(),
                ForgeVoxyConfig.ORIGINAL_VOXY_SSAO_MODE.get());
    }

    ChangeSet changesFrom(ForgeOriginalVoxyConfigSnapshot previous) {
        boolean instanceReload = this.enabled != previous.enabled;
        boolean renderingChanged = this.renderingEnabled != previous.renderingEnabled;
        boolean vanillaRendererReload = this.useEmbeddiumThreads != previous.useEmbeddiumThreads
                || this.environmentalFog != previous.environmentalFog
                || !this.ssaoMode.equals(previous.ssaoMode);
        boolean rendererReload = renderingChanged || vanillaRendererReload;
        boolean oculusReload = instanceReload || renderingChanged;
        boolean threadPolicyChanged = this.serviceThreads != previous.serviceThreads
                || this.useEmbeddiumThreads != previous.useEmbeddiumThreads;
        boolean renderDistanceChanged = Double.compare(this.renderDistance, previous.renderDistance) != 0;
        return new ChangeSet(
                instanceReload,
                rendererReload,
                vanillaRendererReload,
                oculusReload,
                threadPolicyChanged,
                renderDistanceChanged);
    }

    void applyChangesFrom(ForgeOriginalVoxyConfigSnapshot previous) {
        ChangeSet changes = this.changesFrom(previous);

        ForgeVoxyConfig.ENABLED.set(this.enabled);
        ForgeVoxyConfig.RENDERING_ENABLED.set(this.renderingEnabled);
        ForgeVoxyConfig.INGEST_ENABLED.set(this.ingestEnabled);
        ForgeVoxyConfig.ORIGINAL_VOXY_SERVICE_THREADS.set(this.serviceThreads);
        ForgeVoxyConfig.ORIGINAL_VOXY_USE_EMBEDDIUM_BUILDER_THREADS.set(this.useEmbeddiumThreads);
        ForgeVoxyConfig.ORIGINAL_VOXY_SECTION_RENDER_DISTANCE.set(this.renderDistance);
        ForgeVoxyConfig.ORIGINAL_VOXY_SUBDIVISION_SIZE.set(this.subdivisionSize);
        ForgeVoxyConfig.ORIGINAL_VOXY_USE_ENVIRONMENTAL_FOG.set(this.environmentalFog);
        ForgeVoxyConfig.ORIGINAL_VOXY_SSAO_MODE.set(this.ssaoMode);
        ForgeVoxyConfig.CLIENT_SPEC.save();

        ForgeVoxyInstance instance = ForgeVoxyInstance.INSTANCE;
        if (changes.threadPolicyChanged()) {
            instance.getOriginalVoxyModelPipeline().refreshOriginalServiceThreadPolicy();
        }
        if (changes.renderDistanceChanged() && !changes.instanceReload() && !changes.rendererReload()) {
            instance.getOriginalVoxyModelPipeline().updateOriginalRenderDistance((float) this.renderDistance);
        }
        if (changes.instanceReload()) {
            instance.reloadOriginalVoxyRuntime();
        } else if (changes.rendererReload()) {
            instance.reloadOriginalVoxyRenderer(changes.vanillaRendererReload());
        }
        if (changes.oculusReload()) {
            ForgeOriginalVoxyOculusPipelineBridge.reloadShaders();
        }
    }

    static String normalizeSsaoMode(String mode) {
        return SSAO.modeFromConfig(mode).name();
    }

    static float inputToSubdivision(int input) {
        int clamped = clamp(input, 0, SUBDIVISION_INPUT_MAX);
        return (float) (SUBDIVISION_MIN * Math.pow(
                2.0D,
                SUBDIVISION_CONSTANT * ((double) clamped / SUBDIVISION_INPUT_MAX)));
    }

    static int subdivisionToInput(double subdivision) {
        int input = (int) (((Math.log(subdivision / SUBDIVISION_MIN) / Math.log(2.0D))
                / SUBDIVISION_CONSTANT) * SUBDIVISION_INPUT_MAX);
        return clamp(input, 0, SUBDIVISION_INPUT_MAX);
    }

    static double inputToRenderDistance(int input) {
        return clamp(input, RENDER_DISTANCE_INPUT_MIN, RENDER_DISTANCE_INPUT_MAX) / 16.0D;
    }

    static int renderDistanceToInput(double renderDistance) {
        return clamp(
                (int) Math.round(renderDistance * 16.0D),
                RENDER_DISTANCE_INPUT_MIN,
                RENDER_DISTANCE_INPUT_MAX);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    record ChangeSet(
            boolean instanceReload,
            boolean rendererReload,
            boolean vanillaRendererReload,
            boolean oculusReload,
            boolean threadPolicyChanged,
            boolean renderDistanceChanged) {
    }
}
