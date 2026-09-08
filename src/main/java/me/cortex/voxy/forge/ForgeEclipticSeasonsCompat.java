package me.cortex.voxy.forge;

import me.cortex.voxy.common.voxelization.ILightingSupplier;
import me.cortex.voxy.common.voxelization.VoxelizedSection;
import me.cortex.voxy.common.world.other.Mapper;
import me.cortex.voxy.forge.compat.ForgeEclipticSeasonsCapabilities;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.fml.loading.LoadingModList;

import java.lang.ref.WeakReference;

/** Optional boundary: no Ecliptic classes are resolved on an installation without that mod. */
public final class ForgeEclipticSeasonsCompat {
    private ForgeEclipticSeasonsCompat() {}

    private static final class Presence {
        static final boolean HAS_VOXY_API = FMLEnvironment.dist.isClient()
                && LoadingModList.get() != null
                && ForgeEclipticSeasonsCapabilities.hasVoxyIntegration();
    }

    public static boolean enabled() {
        return Presence.HAS_VOXY_API && EclipticSeasonsIntegration.enabled();
    }

    public static ILightingSupplier captureChunk(
            LevelChunk chunk, int sectionY, ILightingSupplier lighting) {
        if (!enabled()) {
            return lighting;
        }
        int index = sectionY - chunk.getMinSection();
        var sections = chunk.getSections();
        var above = index + 1 < sections.length ? sections[index + 1] : null;
        SectionPos abovePos = SectionPos.of(chunk.getPos().x, sectionY + 1, chunk.getPos().z);
        var engine = chunk.getLevel().getLightEngine();
        DataLayer block = engine.getLayerListener(LightLayer.BLOCK).getDataLayerData(abovePos);
        DataLayer sky = engine.getLayerListener(LightLayer.SKY).getDataLayerData(abovePos);
        return new SectionContext(lighting, new WeakReference<>(chunk.getLevel()),
                sections[index].getStates(), above == null ? null : above.getStates().copy(),
                block == null ? null : block.copy(), sky == null ? null : sky.copy());
    }

    public static ILightingSupplier forImport(Level level, PalettedContainer<BlockState> states,
            PalettedContainer<BlockState> aboveStates, DataLayer aboveBlock, DataLayer aboveSky,
            ILightingSupplier lighting) {
        return enabled() ? new SectionContext(lighting, new WeakReference<>(level),
                states, aboveStates, aboveBlock, aboveSky) : lighting;
    }

    public static void decorate(VoxelizedSection section, Mapper mapper, ILightingSupplier lighting) {
        if (enabled()) {
            EclipticSeasonsIntegration.decorate(section, mapper, lighting);
        }
    }

    static void appendSnow(BlockState state, RenderType layer, ReuseVertexConsumer opaque,
            ReuseVertexConsumer translucent) {
        if (enabled()) {
            EclipticSeasonsIntegration.appendSnow(state, layer, opaque, translucent);
        }
    }

    public static void onSeasonChange(Level level) {
        if (Presence.HAS_VOXY_API) {
            EclipticSeasonsIntegration.onSeasonChange(level);
        }
    }

    static void tick() {
        if (Presence.HAS_VOXY_API) {
            EclipticSeasonsIntegration.tick();
        }
    }

    record SectionContext(ILightingSupplier original, WeakReference<Level> level,
            PalettedContainer<BlockState> states, PalettedContainer<BlockState> aboveStates,
            DataLayer aboveBlock, DataLayer aboveSky) implements ILightingSupplier {
        @Override
        public byte supply(int x, int y, int z) {
            if (y < 16) {
                return original.supply(x, y, z);
            }
            int block = aboveBlock == null ? 0 : aboveBlock.get(x, y - 16, z);
            int sky = aboveSky == null ? 0 : aboveSky.get(x, y - 16, z);
            return (byte) (sky | block << 4);
        }

        BlockState blockAbove(int x, int y, int z) {
            return y < 16 ? states.get(x, y, z)
                    : aboveStates == null ? Blocks.AIR.defaultBlockState() : aboveStates.get(x, y - 16, z);
        }
    }
}
