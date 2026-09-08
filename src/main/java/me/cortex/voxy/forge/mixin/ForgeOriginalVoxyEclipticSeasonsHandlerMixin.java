package me.cortex.voxy.forge.mixin;

import com.teamtea.eclipticseasons.api.event.SolarTermChangeEvent;
import me.cortex.voxy.forge.ForgeEclipticSeasonsCompat;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Pseudo;

@Pseudo
@Mixin(targets = "com.teamtea.eclipticseasons.compat.voxy.VoxyEsHandler", remap = false)
public abstract class ForgeOriginalVoxyEclipticSeasonsHandlerMixin {
    /**
     * @author Forxy
     * @reason Preserve the seasonal refresh using the actual Forge render owner. The upstream
     * body references the absent Fabric IGetVoxyRenderSystem, so it must be replaced in full.
     */
    @Overwrite
    @SubscribeEvent
    public void onSolarTermChangeEvent(SolarTermChangeEvent event) {
        ForgeEclipticSeasonsCompat.onSeasonChange(event.getLevel());
    }
}
