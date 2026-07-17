package me.cortex.voxy.forge;

import net.minecraftforge.fml.ModList;

/** Early-safe runtime boundary for original Voxy's optional Iris/Oculus integration. */
final class ForgeOculusAvailability {
    private ForgeOculusAvailability() {
    }

    static boolean installed() {
        try {
            return ModList.get().isLoaded("oculus");
        } catch (RuntimeException | LinkageError ignored) {
            return false;
        }
    }
}
