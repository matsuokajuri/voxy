package me.cortex.voxy.forge;

import me.cortex.voxy.config.ForgeVoxyConfig;
import me.cortex.voxy.platform.ForgePlatformServices;
import me.cortex.voxy.platform.PlatformServices;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(VoxyForge.MOD_ID)
public final class VoxyForge {
    public static final String MOD_ID = "voxy";
    public static final Logger LOGGER = LoggerFactory.getLogger("Voxy");
    public static final PlatformServices PLATFORM = new ForgePlatformServices(MOD_ID);

    public VoxyForge() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, ForgeVoxyConfig.CLIENT_SPEC);

        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ForgeVoxyClientModEvents.register(modBus));

        LOGGER.info("Voxy Forge 1.20.1 original-parity route loaded. Formal renderer readiness remains gated by runtime validation.");
    }
}
