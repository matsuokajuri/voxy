package me.cortex.voxy.forge;

import me.cortex.voxy.config.ForgeVoxyConfig;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;

/** Forge Mod List configuration screen mapped from original VoxyConfigMenu. */
public final class ForgeOriginalVoxyConfigScreen extends Screen {
    private static final int SUBDIVISION_INPUT_MAX = 100;
    private static final double SUBDIVISION_MIN = 28.0D;
    private static final double SUBDIVISION_MAX = 256.0D;
    private static final double SUBDIVISION_CONSTANT = Math.log(SUBDIVISION_MAX / SUBDIVISION_MIN) / Math.log(2.0D);

    private final Screen parent;
    private final boolean initialEnabled;
    private final boolean initialRenderingEnabled;
    private final boolean initialUseEmbeddiumThreads;
    private final boolean initialEnvironmentalFog;
    private final String initialSsaoMode;
    private final double initialRenderDistance;
    private final int initialServiceThreads;

    private boolean enabled;
    private boolean renderingEnabled;
    private boolean ingestEnabled;
    private boolean useEmbeddiumThreads;
    private boolean environmentalFog;
    private String ssaoMode;
    private int serviceThreads;
    private double renderDistance;
    private double subdivisionSize;

    private AbstractWidget renderingWidget;
    private AbstractWidget subdivisionWidget;
    private AbstractWidget renderDistanceWidget;
    private AbstractWidget environmentalFogWidget;
    private AbstractWidget ssaoWidget;

    public ForgeOriginalVoxyConfigScreen(Screen parent) {
        super(Component.translatable("voxy.config.title"));
        this.parent = parent;
        this.enabled = ForgeVoxyConfig.ENABLED.get();
        this.renderingEnabled = ForgeVoxyConfig.RENDERING_ENABLED.get();
        this.ingestEnabled = ForgeVoxyConfig.INGEST_ENABLED.get();
        this.serviceThreads = ForgeVoxyConfig.ORIGINAL_VOXY_SERVICE_THREADS.get();
        this.useEmbeddiumThreads = ForgeVoxyConfig.ORIGINAL_VOXY_USE_EMBEDDIUM_BUILDER_THREADS.get();
        this.renderDistance = ForgeVoxyConfig.ORIGINAL_VOXY_SECTION_RENDER_DISTANCE.get();
        this.subdivisionSize = ForgeVoxyConfig.ORIGINAL_VOXY_SUBDIVISION_SIZE.get();
        this.environmentalFog = ForgeVoxyConfig.ORIGINAL_VOXY_USE_ENVIRONMENTAL_FOG.get();
        this.ssaoMode = normalizeSsaoMode(ForgeVoxyConfig.ORIGINAL_VOXY_SSAO_MODE.get());

        this.initialEnabled = this.enabled;
        this.initialRenderingEnabled = this.renderingEnabled;
        this.initialUseEmbeddiumThreads = this.useEmbeddiumThreads;
        this.initialEnvironmentalFog = this.environmentalFog;
        this.initialSsaoMode = this.ssaoMode;
        this.initialRenderDistance = this.renderDistance;
        this.initialServiceThreads = this.serviceThreads;
    }

    @Override
    protected void init() {
        int left = this.width / 2 - 155;
        int right = this.width / 2 + 5;
        int top = 40;
        int width = 150;
        int height = 20;
        int gap = 24;

        this.addRenderableWidget(CycleButton.onOffBuilder(this.enabled).create(
                left, top, width, height,
                Component.translatable("voxy.config.general.enabled"),
                (button, value) -> {
                    this.enabled = value;
                    this.updateEnabledStates();
                }));
        this.renderingWidget = this.addRenderableWidget(CycleButton.onOffBuilder(this.renderingEnabled).create(
                right, top, width, height,
                Component.translatable("voxy.config.general.rendering"),
                (button, value) -> {
                    this.renderingEnabled = value;
                    this.updateEnabledStates();
                }));

        this.addRenderableWidget(CycleButton.onOffBuilder(this.ingestEnabled).create(
                left, top + gap, width, height,
                Component.translatable("voxy.config.general.ingest"),
                (button, value) -> this.ingestEnabled = value));
        this.addRenderableWidget(CycleButton.onOffBuilder(this.useEmbeddiumThreads).create(
                right, top + gap, width, height,
                Component.literal("Use Embeddium threads"),
                (button, value) -> this.useEmbeddiumThreads = value));

        this.addRenderableWidget(new IntSlider(
                left, top + gap * 2, width, height,
                Component.translatable("voxy.config.general.serviceThreads"),
                1,
                ForgeVoxyConfig.serviceThreadMaximum(),
                this.serviceThreads,
                value -> Component.literal(Integer.toString(value)),
                value -> this.serviceThreads = value));
        this.subdivisionWidget = this.addRenderableWidget(new IntSlider(
                right, top + gap * 2, width, height,
                Component.translatable("voxy.config.general.subDivisionSize"),
                0,
                SUBDIVISION_INPUT_MAX,
                subdivisionToInput(this.subdivisionSize),
                value -> Component.literal(Integer.toString(Math.round(inputToSubdivision(value)))),
                value -> this.subdivisionSize = inputToSubdivision(value)));

        this.renderDistanceWidget = this.addRenderableWidget(new IntSlider(
                left, top + gap * 3, width, height,
                Component.translatable("voxy.config.general.renderDistance"),
                10,
                64 * 16,
                (int) Math.round(this.renderDistance * 16.0D),
                value -> Component.literal(Integer.toString(value * 2)),
                value -> this.renderDistance = value / 16.0D));
        this.environmentalFogWidget = this.addRenderableWidget(CycleButton.onOffBuilder(this.environmentalFog).create(
                right, top + gap * 3, width, height,
                Component.translatable("voxy.config.general.environmental_fog"),
                (button, value) -> this.environmentalFog = value));

        this.ssaoWidget = this.addRenderableWidget(CycleButton.<String>builder(value -> Component.literal(value))
                .withValues(List.of("AUTO", "BASIC", "BETTER", "BEST"))
                .withInitialValue(this.ssaoMode)
                .create(left, top + gap * 4, width, height,
                        Component.translatable("voxy.config.general.ssao_mode"),
                        (button, value) -> this.ssaoMode = value));

        int bottom = Math.min(this.height - 28, top + gap * 6);
        this.addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> this.applyAndClose())
                .bounds(left, bottom, width, height)
                .build());
        this.addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), button -> this.onClose())
                .bounds(right, bottom, width, height)
                .build());
        this.updateEnabledStates();
    }

    private void updateEnabledStates() {
        boolean renderingPage = this.enabled && this.renderingEnabled;
        this.renderingWidget.active = this.enabled;
        this.subdivisionWidget.active = renderingPage;
        this.renderDistanceWidget.active = renderingPage;
        boolean shaderpackInactive = !ForgeOriginalVoxyOculusPipelineBridge.shaderpackActive();
        this.environmentalFogWidget.active = renderingPage && shaderpackInactive;
        this.ssaoWidget.active = renderingPage && shaderpackInactive;
    }

    private void applyAndClose() {
        boolean rendererReload = this.enabled != this.initialEnabled
                || this.renderingEnabled != this.initialRenderingEnabled
                || this.useEmbeddiumThreads != this.initialUseEmbeddiumThreads
                || this.environmentalFog != this.initialEnvironmentalFog
                || !this.ssaoMode.equals(this.initialSsaoMode);
        boolean threadPolicyChanged = this.serviceThreads != this.initialServiceThreads
                || this.useEmbeddiumThreads != this.initialUseEmbeddiumThreads;
        boolean renderDistanceChanged = Double.compare(this.renderDistance, this.initialRenderDistance) != 0;

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
        if (threadPolicyChanged) {
            instance.getOriginalVoxyModelPipeline().refreshOriginalServiceThreadPolicy();
        }
        if (renderDistanceChanged) {
            instance.getOriginalVoxyModelPipeline().updateOriginalRenderDistance((float) this.renderDistance);
        }
        if (rendererReload) {
            instance.reloadOriginalVoxyRuntime();
        }
        this.onClose();
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(this.parent);
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 16, 0xFFFFFF);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private static String normalizeSsaoMode(String mode) {
        if (mode == null) {
            return "AUTO";
        }
        String normalized = mode.toUpperCase(java.util.Locale.ROOT);
        return List.of("AUTO", "BASIC", "BETTER", "BEST").contains(normalized) ? normalized : "AUTO";
    }

    private static float inputToSubdivision(int input) {
        return (float) (SUBDIVISION_MIN * Math.pow(
                2.0D,
                SUBDIVISION_CONSTANT * ((double) input / SUBDIVISION_INPUT_MAX)));
    }

    private static int subdivisionToInput(double subdivision) {
        return (int) (((Math.log(subdivision / SUBDIVISION_MIN) / Math.log(2.0D))
                / SUBDIVISION_CONSTANT) * SUBDIVISION_INPUT_MAX);
    }

    private static final class IntSlider extends AbstractSliderButton {
        private final Component label;
        private final int min;
        private final int max;
        private final IntFunction<Component> formatter;
        private final IntConsumer consumer;
        private int current;

        private IntSlider(
                int x,
                int y,
                int width,
                int height,
                Component label,
                int min,
                int max,
                int initial,
                IntFunction<Component> formatter,
                IntConsumer consumer) {
            super(x, y, width, height, Component.empty(), normalize(initial, min, max));
            this.label = label;
            this.min = min;
            this.max = max;
            this.formatter = formatter;
            this.consumer = consumer;
            this.current = clamp(initial, min, max);
            this.updateMessage();
        }

        @Override
        protected void updateMessage() {
            this.setMessage(this.label.copy().append(": ").append(this.formatter.apply(this.current)));
        }

        @Override
        protected void applyValue() {
            this.current = clamp((int) Math.round(this.min + this.value * (this.max - this.min)), this.min, this.max);
            this.consumer.accept(this.current);
            this.updateMessage();
        }

        private static double normalize(int value, int min, int max) {
            return max == min ? 0.0D : (double) (clamp(value, min, max) - min) / (max - min);
        }

        private static int clamp(int value, int min, int max) {
            return Math.max(min, Math.min(max, value));
        }
    }
}
