package me.cortex.voxy.forge;

import com.google.common.collect.ImmutableList;
import me.jellysquid.mods.sodium.client.gui.options.Option;
import me.jellysquid.mods.sodium.client.gui.options.OptionGroup;
import me.jellysquid.mods.sodium.client.gui.options.OptionImpact;
import me.jellysquid.mods.sodium.client.gui.options.OptionImpl;
import me.jellysquid.mods.sodium.client.gui.options.OptionPage;
import me.jellysquid.mods.sodium.client.gui.options.control.CyclingControl;
import me.jellysquid.mods.sodium.client.gui.options.control.ControlValueFormatter;
import me.jellysquid.mods.sodium.client.gui.options.control.SliderControl;
import me.jellysquid.mods.sodium.client.gui.options.control.TickBoxControl;
import me.jellysquid.mods.sodium.client.gui.options.storage.OptionStorage;
import me.jellysquid.mods.sodium.client.gui.SodiumOptionsGUI;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.embeddedt.embeddium.api.OptionGUIConstructionEvent;
import org.embeddedt.embeddium.client.gui.options.OptionIdentifier;

/** Embeddium's official option-page adaptation of original VoxyConfigMenu. */
final class ForgeOriginalVoxyEmbeddiumOptions {
    // Embeddium interns option identifiers globally by namespace/path and rejects a later
    // request for the same identifier with a different value type. Keep the original Voxy
    // option ids below and give page ids their own paths so `voxy:rendering` remains Boolean.
    private static final OptionIdentifier<Void> GENERAL_PAGE_ID = id("general_page");
    private static final OptionIdentifier<Void> RENDERING_PAGE_ID = id("rendering_page");
    private static final OptionIdentifier<Void> GENERAL_LIFECYCLE_GROUP_ID = id("general_lifecycle");
    private static final OptionIdentifier<Void> GENERAL_THREADS_GROUP_ID = id("general_threads");
    private static final OptionIdentifier<Void> GENERAL_INGEST_GROUP_ID = id("general_ingest");
    private static final OptionIdentifier<Void> RENDERING_LIFECYCLE_GROUP_ID = id("rendering_lifecycle");
    private static final OptionIdentifier<Void> RENDERING_DISTANCE_GROUP_ID = id("rendering_distance");
    private static final OptionIdentifier<Void> RENDERING_EFFECTS_GROUP_ID = id("rendering_effects");

    private static final OptionIdentifier<Boolean> ENABLED_ID = id("enabled", Boolean.class);
    private static final OptionIdentifier<Integer> THREAD_COUNT_ID = id("thread_count", Integer.class);
    private static final OptionIdentifier<Boolean> USE_EMBEDDIUM_THREADS_ID =
            id("use_sodium_threads", Boolean.class);
    private static final OptionIdentifier<Boolean> INGEST_ENABLED_ID = id("ingest_enabled", Boolean.class);
    private static final OptionIdentifier<Boolean> RENDERING_ENABLED_ID = id("rendering", Boolean.class);
    private static final OptionIdentifier<Integer> SUBDIVISION_SIZE_ID = id("subdivsize", Integer.class);
    private static final OptionIdentifier<Integer> RENDER_DISTANCE_ID = id("render_distance", Integer.class);
    private static final OptionIdentifier<Boolean> ENVIRONMENTAL_FOG_ID = id("eviromental_fog", Boolean.class);
    private static final OptionIdentifier<SSAO.SSAOMode> SSAO_MODE_ID =
            id("ssao_mode", SSAO.SSAOMode.class);

    private static boolean registered;

    private ForgeOriginalVoxyEmbeddiumOptions() {
    }

    static synchronized void register() {
        if (registered) {
            return;
        }
        OptionGUIConstructionEvent.BUS.addListener(ForgeOriginalVoxyEmbeddiumOptions::addPages);
        registered = true;
        VoxyForge.LOGGER.info("Registered Voxy option pages with Embeddium");
    }

    static Screen createScreen(Screen parent) {
        //Original ModMenu opens Sodium's option screen. Embeddium's public entry screen posts the
        //construction event above and then hands the complete page list to its modern GUI.
        return new SodiumOptionsGUI(parent);
    }

    private static void addPages(OptionGUIConstructionEvent event) {
        ConfigStorage storage = new ConfigStorage();
        OptionImpl<ConfigData, Boolean> enabled = OptionImpl.createBuilder(Boolean.class, storage)
                .setId(ENABLED_ID)
                .setName(Component.translatable("voxy.config.general.enabled"))
                .setTooltip(Component.translatable("voxy.config.general.enabled.tooltip"))
                .setBinding((data, value) -> data.enabled = value, data -> data.enabled)
                .setControl(TickBoxControl::new)
                .build();

        OptionImpl<ConfigData, Integer> serviceThreads = OptionImpl.createBuilder(Integer.class, storage)
                .setId(THREAD_COUNT_ID)
                .setName(Component.translatable("voxy.config.general.serviceThreads"))
                .setTooltip(Component.translatable("voxy.config.general.serviceThreads.tooltip"))
                .setBinding((data, value) -> data.serviceThreads = value, data -> data.serviceThreads)
                .setControl(option -> new SliderControl(
                        option,
                        1,
                        me.cortex.voxy.config.ForgeVoxyConfig.serviceThreadMaximum(),
                        1,
                        ControlValueFormatter.number()))
                .setEnabledPredicate(enabled::getValue)
                .build();

        OptionImpl<ConfigData, Boolean> useEmbeddiumThreads = OptionImpl.createBuilder(Boolean.class, storage)
                .setId(USE_EMBEDDIUM_THREADS_ID)
                .setName(Component.translatable("voxy.config.general.useSodiumBuilder"))
                .setTooltip(Component.translatable("voxy.config.general.useSodiumBuilder.tooltip"))
                .setBinding((data, value) -> data.useEmbeddiumThreads = value, data -> data.useEmbeddiumThreads)
                .setControl(TickBoxControl::new)
                .setEnabledPredicate(enabled::getValue)
                .build();

        OptionImpl<ConfigData, Boolean> ingestEnabled = OptionImpl.createBuilder(Boolean.class, storage)
                .setId(INGEST_ENABLED_ID)
                .setName(Component.translatable("voxy.config.general.ingest"))
                .setTooltip(Component.translatable("voxy.config.general.ingest.tooltip"))
                .setBinding((data, value) -> data.ingestEnabled = value, data -> data.ingestEnabled)
                .setControl(TickBoxControl::new)
                .setEnabledPredicate(enabled::getValue)
                .build();

        OptionImpl<ConfigData, Boolean> renderingEnabled = OptionImpl.createBuilder(Boolean.class, storage)
                .setId(RENDERING_ENABLED_ID)
                .setName(Component.translatable("voxy.config.general.rendering"))
                .setTooltip(Component.translatable("voxy.config.general.rendering.tooltip"))
                .setBinding((data, value) -> data.renderingEnabled = value, data -> data.renderingEnabled)
                .setControl(TickBoxControl::new)
                .setEnabledPredicate(enabled::getValue)
                .build();

        OptionImpl<ConfigData, Integer> subdivisionSize = OptionImpl.createBuilder(Integer.class, storage)
                .setId(SUBDIVISION_SIZE_ID)
                .setName(Component.translatable("voxy.config.general.subDivisionSize"))
                .setTooltip(Component.translatable("voxy.config.general.subDivisionSize.tooltip"))
                .setBinding(
                        (data, value) -> data.subdivisionSize =
                                ForgeOriginalVoxyConfigSnapshot.inputToSubdivision(value),
                        data -> ForgeOriginalVoxyConfigSnapshot.subdivisionToInput(data.subdivisionSize))
                .setControl(option -> new SliderControl(
                        option,
                        0,
                        ForgeOriginalVoxyConfigSnapshot.SUBDIVISION_INPUT_MAX,
                        1,
                        value -> Component.literal(Integer.toString(Math.round(
                                ForgeOriginalVoxyConfigSnapshot.inputToSubdivision(value))))))
                .setEnabledPredicate(() -> enabled.getValue() && renderingEnabled.getValue())
                .setImpact(OptionImpact.HIGH)
                .build();

        OptionImpl<ConfigData, Integer> renderDistance = OptionImpl.createBuilder(Integer.class, storage)
                .setId(RENDER_DISTANCE_ID)
                .setName(Component.translatable("voxy.config.general.renderDistance"))
                .setTooltip(Component.translatable("voxy.config.general.renderDistance.tooltip"))
                .setBinding(
                        (data, value) -> data.renderDistance =
                                ForgeOriginalVoxyConfigSnapshot.inputToRenderDistance(value),
                        data -> ForgeOriginalVoxyConfigSnapshot.renderDistanceToInput(data.renderDistance))
                .setControl(option -> new SliderControl(
                        option,
                        ForgeOriginalVoxyConfigSnapshot.RENDER_DISTANCE_INPUT_MIN,
                        ForgeOriginalVoxyConfigSnapshot.RENDER_DISTANCE_INPUT_MAX,
                        1,
                        value -> Component.literal(Integer.toString(value * 2))))
                .setEnabledPredicate(() -> enabled.getValue() && renderingEnabled.getValue())
                .setImpact(OptionImpact.MEDIUM)
                .build();

        OptionImpl<ConfigData, Boolean> environmentalFog = OptionImpl.createBuilder(Boolean.class, storage)
                .setId(ENVIRONMENTAL_FOG_ID)
                .setName(Component.translatable("voxy.config.general.environmental_fog"))
                .setTooltip(Component.translatable("voxy.config.general.environmental_fog.tooltip"))
                .setBinding((data, value) -> data.environmentalFog = value, data -> data.environmentalFog)
                .setControl(TickBoxControl::new)
                .setEnabledPredicate(() -> enabled.getValue()
                        && renderingEnabled.getValue()
                        && !ForgeOriginalVoxyOculusPipelineBridge.shaderpackActive())
                .build();

        OptionImpl<ConfigData, SSAO.SSAOMode> ssaoMode =
                OptionImpl.createBuilder(SSAO.SSAOMode.class, storage)
                        .setId(SSAO_MODE_ID)
                        .setName(Component.translatable("voxy.config.general.ssao_mode"))
                        .setTooltip(Component.translatable("voxy.config.general.ssao_mode.tooltip"))
                        .setBinding((data, value) -> data.ssaoMode = value.name(),
                                data -> SSAO.modeFromConfig(data.ssaoMode))
                        .setControl(option -> new CyclingControl<>(
                                option,
                                SSAO.SSAOMode.class,
                                new Component[]{
                                        Component.literal("AUTO"),
                                        Component.literal("BASIC"),
                                        Component.literal("BETTER"),
                                        Component.literal("BEST")
                                }))
                        .setEnabledPredicate(() -> enabled.getValue()
                                && renderingEnabled.getValue()
                                && !ForgeOriginalVoxyOculusPipelineBridge.shaderpackActive())
                        .setImpact(OptionImpact.MEDIUM)
                        .build();

        event.addPage(new OptionPage(
                GENERAL_PAGE_ID,
                Component.translatable("voxy.config.general"),
                ImmutableList.of(
                        group(GENERAL_LIFECYCLE_GROUP_ID, enabled),
                        group(GENERAL_THREADS_GROUP_ID, serviceThreads, useEmbeddiumThreads),
                        group(GENERAL_INGEST_GROUP_ID, ingestEnabled))));
        event.addPage(new OptionPage(
                RENDERING_PAGE_ID,
                Component.translatable("voxy.config.rendering"),
                ImmutableList.of(
                        group(RENDERING_LIFECYCLE_GROUP_ID, renderingEnabled),
                        group(RENDERING_DISTANCE_GROUP_ID, subdivisionSize, renderDistance),
                        group(RENDERING_EFFECTS_GROUP_ID, environmentalFog, ssaoMode))));
    }

    private static OptionGroup group(OptionIdentifier<Void> id, Option<?>... options) {
        OptionGroup.Builder builder = OptionGroup.createBuilder().setId(id);
        for (Option<?> option : options) {
            builder.add(option);
        }
        return builder.build();
    }

    private static OptionIdentifier<Void> id(String path) {
        return OptionIdentifier.create("voxy", path);
    }

    private static <T> OptionIdentifier<T> id(String path, Class<T> type) {
        return OptionIdentifier.create("voxy", path, type);
    }

    private static final class ConfigStorage implements OptionStorage<ConfigData> {
        private final ConfigData data;
        private ForgeOriginalVoxyConfigSnapshot baseline;

        private ConfigStorage() {
            this.baseline = ForgeOriginalVoxyConfigSnapshot.load();
            this.data = new ConfigData(this.baseline);
        }

        @Override
        public ConfigData getData() {
            return this.data;
        }

        @Override
        public void save() {
            ForgeOriginalVoxyConfigSnapshot current = this.data.snapshot();
            current.applyChangesFrom(this.baseline);
            this.baseline = current;
        }
    }

    private static final class ConfigData {
        private boolean enabled;
        private boolean renderingEnabled;
        private boolean ingestEnabled;
        private int serviceThreads;
        private boolean useEmbeddiumThreads;
        private double renderDistance;
        private double subdivisionSize;
        private boolean environmentalFog;
        private String ssaoMode;

        private ConfigData(ForgeOriginalVoxyConfigSnapshot snapshot) {
            this.enabled = snapshot.enabled();
            this.renderingEnabled = snapshot.renderingEnabled();
            this.ingestEnabled = snapshot.ingestEnabled();
            this.serviceThreads = snapshot.serviceThreads();
            this.useEmbeddiumThreads = snapshot.useEmbeddiumThreads();
            this.renderDistance = snapshot.renderDistance();
            this.subdivisionSize = snapshot.subdivisionSize();
            this.environmentalFog = snapshot.environmentalFog();
            this.ssaoMode = snapshot.ssaoMode();
        }

        private ForgeOriginalVoxyConfigSnapshot snapshot() {
            return new ForgeOriginalVoxyConfigSnapshot(
                    this.enabled,
                    this.renderingEnabled,
                    this.ingestEnabled,
                    this.serviceThreads,
                    this.useEmbeddiumThreads,
                    this.renderDistance,
                    this.subdivisionSize,
                    this.environmentalFog,
                    this.ssaoMode);
        }
    }
}
