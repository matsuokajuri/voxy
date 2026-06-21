package me.cortex.voxy.forge.mixin;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.irisshaders.iris.uniforms.custom.CustomUniforms;
import net.irisshaders.iris.uniforms.custom.cached.CachedUniform;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

@Mixin(value = CustomUniforms.class, remap = false)
public interface ForgeOriginalVoxyOculusCustomUniformsAccessor {
    @Accessor
    Map<Object, Object2IntMap<CachedUniform>> getLocationMap();
}
