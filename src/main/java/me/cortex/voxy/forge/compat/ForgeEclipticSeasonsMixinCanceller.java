package me.cortex.voxy.forge.compat;

import com.bawnorton.mixinsquared.api.MixinCanceller;

import java.util.List;
import java.util.Set;

/**
 * Replaced one-for-one by the native Forge adapter; never changes the user's mixin config.
 * Kept outside the mixin package because ServiceLoader constructs this as an ordinary class.
 */
public final class ForgeEclipticSeasonsMixinCanceller implements MixinCanceller {
    private static final String PREFIX = "com.teamtea.eclipticseasons.mixin.compat.voxy.";
    private static final Set<String> REPLACED = Set.of(
            "MixinClientLevel", "MixinIngestSection", "MixinIngestSection2", "MixinMapping",
            "MixinModelBakerySubsystem", "MixinModelFactory", "MixinModelTextureBakery",
            "MixinVoxelIngestService", "MixinWorldConversionFactory", "MixinWorldImporter");

    @Override
    public boolean shouldCancel(List<String> targetClassNames, String mixinClassName) {
        return mixinClassName.startsWith(PREFIX)
                && REPLACED.contains(mixinClassName.substring(PREFIX.length()))
                && ForgeEclipticSeasonsCapabilities.hasVoxyIntegration();
    }
}
