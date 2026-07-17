package me.cortex.voxy.forge.mixin;

import net.minecraftforge.fml.loading.LoadingModList;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/** Keeps optional-integration mixins out of transformation when their owner mod is absent. */
public final class ForgeVoxyMixinPlugin implements IMixinConfigPlugin {
    private static final String ACEDIUM_MIXIN_PREFIX =
            "me.cortex.voxy.forge.mixin.ForgeOriginalVoxyAcedium";
    private static final String CHUNKY_MIXIN_PREFIX =
            "me.cortex.voxy.forge.mixin.ForgeOriginalVoxyChunky";
    private static final String OCULUS_MIXIN_PREFIX =
            "me.cortex.voxy.forge.mixin.ForgeOriginalVoxyOculus";

    @Override
    public void onLoad(String mixinPackage) {
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return shouldApplyMixin(mixinClassName, ForgeVoxyMixinPlugin::isModPresent);
    }

    static boolean shouldApplyMixin(String mixinClassName, Predicate<String> modPresent) {
        if (mixinClassName.startsWith(ACEDIUM_MIXIN_PREFIX)) {
            // Acedium 0.2.x publishes both acedium and compatibility nvidium mod entries.
            // Gate on its own identity so a different Forge-loaded Nvidium artifact cannot
            // accidentally opt into the Acedium 1.20.1 ABI contract below.
            return modPresent.test("acedium");
        }
        if (mixinClassName.startsWith(CHUNKY_MIXIN_PREFIX)) {
            return modPresent.test("chunky");
        }
        return !mixinClassName.startsWith(OCULUS_MIXIN_PREFIX) || modPresent.test("oculus");
    }

    private static boolean isModPresent(String modId) {
        LoadingModList loadingModList = LoadingModList.get();
        return loadingModList != null && loadingModList.getModFileById(modId) != null;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }
}
