package me.cortex.voxy.client.core.rendering;

import me.cortex.voxy.client.core.util.IrisUtil;
import net.fabricmc.loader.api.FabricLoader;
import org.vivecraft.api.client.VRRenderingAPI;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

import static org.vivecraft.api.client.data.RenderPass.VANILLA;

/** Vulkan-lifetime translation of the original ViewportSelector. */
public final class VulkanViewportSelector<T extends VulkanViewport<?>> implements AutoCloseable {
    public static final boolean VIVECRAFT_INSTALLED = FabricLoader.getInstance().isModLoaded("vivecraft");
    private static final Object IRIS_SHADOW_OBJECT = new Object();

    private final Supplier<T> creator;
    private final T defaultViewport;
    private final Map<Object, T> extraViewports = new HashMap<>();
    private boolean closed;

    public VulkanViewportSelector(Supplier<T> viewportCreator) {
        this.creator = Objects.requireNonNull(viewportCreator, "viewportCreator");
        this.defaultViewport = Objects.requireNonNull(viewportCreator.get(), "defaultViewport");
    }

    private T getOrCreate(Object holder) {
        this.ensureOpen();
        return this.extraViewports.computeIfAbsent(
                holder,
                ignored -> Objects.requireNonNull(this.creator.get(), "viewportCreator result")
        );
    }

    private T getVivecraftViewport() {
        var pass = VRRenderingAPI.instance().getCurrentRenderPass();
        if (pass == null || pass == VANILLA) return null;
        return this.getOrCreate(pass);
    }

    public T getViewport() {
        this.ensureOpen();
        T viewport = null;
        if (VIVECRAFT_INSTALLED) viewport = this.getVivecraftViewport();
        if (viewport == null && IrisUtil.irisShadowActive()) {
            viewport = this.getOrCreate(IRIS_SHADOW_OBJECT);
        }
        return viewport == null ? this.defaultViewport : viewport;
    }

    private void ensureOpen() {
        if (this.closed) throw new IllegalStateException("Voxy Vulkan viewport selector is closed");
    }

    @Override
    public void close() {
        if (this.closed) return;
        this.closed = true;
        this.defaultViewport.close();
        this.extraViewports.values().forEach(VulkanViewport::close);
        this.extraViewports.clear();
    }
}
