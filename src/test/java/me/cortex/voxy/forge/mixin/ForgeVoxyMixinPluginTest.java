package me.cortex.voxy.forge.mixin;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ForgeVoxyMixinPluginTest {
    @Test
    void oculusMixinsFollowOptionalModPresence() {
        String oculusMixin =
                "me.cortex.voxy.forge.mixin.ForgeOriginalVoxyOculusIrisRenderingPipelineMixin";

        assertFalse(ForgeVoxyMixinPlugin.shouldApplyMixin(oculusMixin, modId -> false));
        assertTrue(ForgeVoxyMixinPlugin.shouldApplyMixin(oculusMixin, "oculus"::equals));
    }

    @Test
    void acediumMixinFollowsItsOwnDualEntryModIdentity() {
        String acediumMixin =
                "me.cortex.voxy.forge.mixin.ForgeOriginalVoxyAcediumRenderPipelineMixin";

        assertFalse(ForgeVoxyMixinPlugin.shouldApplyMixin(acediumMixin, modId -> false));
        assertFalse(ForgeVoxyMixinPlugin.shouldApplyMixin(acediumMixin, "nvidium"::equals));
        assertTrue(ForgeVoxyMixinPlugin.shouldApplyMixin(acediumMixin, "acedium"::equals));
    }

    @Test
    void chunkyMixinFollowsOptionalModPresence() {
        String chunkyMixin =
                "me.cortex.voxy.forge.mixin.ForgeOriginalVoxyChunkyForgeWorldMixin";

        assertFalse(ForgeVoxyMixinPlugin.shouldApplyMixin(chunkyMixin, modId -> false));
        assertTrue(ForgeVoxyMixinPlugin.shouldApplyMixin(chunkyMixin, "chunky"::equals));
    }

    @Test
    void acediumBufferAdapterIsAbsentWithoutAcedium() {
        String mixin = "me.cortex.voxy.forge.mixin.ForgeOriginalVoxyAcediumMappedBufferMixin";
        assertFalse(ForgeVoxyMixinPlugin.shouldApplyMixin(mixin, modId -> false));
        assertFalse(ForgeVoxyMixinPlugin.shouldApplyMixin(mixin, "nvidium"::equals));
        assertTrue(ForgeVoxyMixinPlugin.shouldApplyMixin(mixin, "acedium"::equals));
    }

    @Test
    void mandatoryEmbeddiumMixinsRemainEnabledWithoutOptionalMods() {
        assertTrue(ForgeVoxyMixinPlugin.shouldApplyMixin(
                "me.cortex.voxy.forge.mixin.ForgeOriginalVoxyEmbeddiumDefaultChunkRendererMixin",
                modId -> false));
    }

    @Test
    void lightPacketCompletionMixinsAreMutuallyExclusiveWithStarlight() {
        String vanilla =
                "me.cortex.voxy.forge.mixin.ForgeOriginalVoxyVanillaLightPacketMixin";
        String starlight =
                "me.cortex.voxy.forge.mixin.ForgeOriginalVoxyStarlightLightPacketMixin";

        assertTrue(ForgeVoxyMixinPlugin.shouldApplyMixin(vanilla, modId -> false));
        assertFalse(ForgeVoxyMixinPlugin.shouldApplyMixin(starlight, modId -> false));
        assertFalse(ForgeVoxyMixinPlugin.shouldApplyMixin(vanilla, "starlight"::equals));
        assertTrue(ForgeVoxyMixinPlugin.shouldApplyMixin(starlight, "starlight"::equals));
    }
}
