package me.cortex.voxy.forge;

import net.irisshaders.iris.shadows.ShadowRenderer;

final class ForgeOculusShadowStateBridge {
    private ForgeOculusShadowStateBridge() {
    }

    static boolean shadowActive() {
        if (!ForgeOculusAvailability.installed()) {
            return false;
        }
        return shadowActive0();
    }

    private static boolean shadowActive0() {
        //ShadowRenderer.ACTIVE is only ever reset by Oculus's own shadow pass; after the
        // shaderpack is disabled mid-session the flag can be left stuck true. Gate on an
        // actually-active shaderpack pipeline here so EVERY caller is protected.
        return ShadowRenderer.ACTIVE && ForgeOriginalVoxyOculusPipelineBridge.shaderpackActive();
    }
}
