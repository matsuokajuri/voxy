package me.cortex.voxy.forge;

import com.teamtea.eclipticseasons.api.EclipticSeasonsApi;
import com.teamtea.eclipticseasons.client.core.ExtraModelManager;
import com.teamtea.eclipticseasons.client.core.ExtraRendererContext;
import com.teamtea.eclipticseasons.client.util.ClientCon;
import com.teamtea.eclipticseasons.common.core.map.MapChecker;
import com.teamtea.eclipticseasons.compat.CompatModule;
import com.teamtea.eclipticseasons.config.CommonConfig.Snow;
import me.cortex.voxy.common.voxelization.ILightingSupplier;
import me.cortex.voxy.common.voxelization.VoxelizedSection;
import me.cortex.voxy.common.world.other.Mapper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.SingleThreadedRandomSource;
import net.minecraft.world.level.storage.LevelResource;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Port of Ecliptic Seasons 0.12.18.9.1's VoxyTool/VoxyClientTool to the actual Forge owners.
 * The snow policy and extra model are Ecliptic's; conversion, mipping, baking, uploading and
 * import cancellation remain the original Voxy pipeline. No second renderer or state database.
 */
final class EclipticSeasonsIntegration {
    private EclipticSeasonsIntegration() {}

    static boolean enabled() {
        return CompatModule.CommonConfig.voxyTest.get();
    }

    static void decorate(VoxelizedSection section, Mapper mapper, ILightingSupplier lighting) {
        var context = lighting instanceof ForgeEclipticSeasonsCompat.SectionContext c ? c : null;
        Level level = context == null ? ClientCon.getUseLevel() : context.level().get();
        if (level == null || section.lvl0NonAirCount == 0) {
            return;
        }
        boolean loaded = MapChecker.isLoaded(level, section.x, section.z);
        Mapper.BiomeEntry[] biomes = loaded ? null : mapper.getBiomeEntries();
        var biomeRegistry = level.registryAccess().registryOrThrow(Registries.BIOME);
        for (int i = 0; i < 4096; i++) {
            long value = section.section[i];
            if (Mapper.isAir(value)) {
                continue;
            }
            int id = Mapper.getBlockId(value);
            BlockState state = mapper.getBlockStateFromBlockId(id);
            int flag = MapChecker.getDefaultBlockTypeFlag(state);
            if (flag <= 0) {
                continue;
            }
            int x = i & 15, y = i >> 8 & 15, z = i >> 4 & 15;
            BlockPos pos = new BlockPos((section.x << 4) + x, (section.y << 4) + y, (section.z << 4) + z);
            boolean snowy = false;
            if (loaded) {
                snowy = EclipticSeasonsApi.getInstance().isSnowyBlock(level, state, pos);
            } else if (context != null && context.aboveStates() != null) {
                int aboveLight = Byte.toUnsignedInt(context.supply(x, y + 1, z));
                if ((aboveLight & 15) > 9 && (!Snow.notSnowyNearGlowingBlock.get()
                        || (aboveLight >> 4) < Snow.notSnowyNearGlowingBlockLevel.get())) {
                    BlockState above = context.blockAbove(x, y + 1, z);
                    boolean exposed = true;
                    if (!MapChecker.leaveLike(flag)) {
                        if (MapChecker.extraSnowPassable(state)) {
                            exposed = !MapChecker.extraSnowPassable(above);
                        }
                    } else if (above.is(state.getBlock()) && (Heightmap.Types.MOTION_BLOCKING_NO_LEAVES
                            .isOpaque().test(above) || MapChecker.extraSnowPassable(above))) {
                        exposed = Snow.snowyTree.get();
                    }
                    if (exposed) {
                        var key = ResourceKey.<Biome>create(Registries.BIOME,
                                new ResourceLocation(biomes[Mapper.getBiomeId(value)].biome));
                        snowy = MapChecker.shouldSnowAtBiome(level, biomeRegistry.getHolderOrThrow(key).value(),
                                state, level.getRandom(), state.getSeed(pos), pos);
                    }
                }
            }
            if (snowy) {
                section.section[i] = Mapper.withBlockBiome(value,
                        ForgeSnowStateIds.snowyId(id, mapper.getBlockStateCount()), Mapper.getBiomeId(value));
            }
        }
    }

    static void appendSnow(BlockState state, RenderType sourceLayer, ReuseVertexConsumer opaque,
            ReuseVertexConsumer translucent) {
        int flag = MapChecker.getDefaultBlockTypeFlag(state);
        var model = ExtraModelManager.getSnowyModel(state, null, flag, MapChecker.getSnowOffset(state, flag));
        if (model == null) {
            return; // Ecliptic deliberately has no overlay for this state.
        }
        Level level = ClientCon.getUseLevel();
        if (level == null) {
            throw new IllegalStateException("Ecliptic snow model requested without an active level");
        }
        var context = new ExtraRendererContext();
        context.setReplace(ExtraModelManager.isModelReplaceable(state, level, BlockPos.ZERO, model))
                .setExtraModel(model)
                .setOriginalModel(Minecraft.getInstance().getBlockRenderer().getBlockModel(state));
        RenderType layer = state.getBlock() instanceof LeavesBlock ? sourceLayer : ExtraModelManager.getRenderType(state);
        for (Direction direction : new Direction[]{Direction.DOWN, Direction.UP, Direction.NORTH,
                Direction.SOUTH, Direction.WEST, Direction.EAST, null}) {
            for (var quad : ExtraModelManager.cancelTop(context, model, level, state, BlockPos.ZERO,
                    direction, level.getRandom(), 42L,
                    model.getQuads(state, direction, new SingleThreadedRandomSource(42L)))) {
                var consumer = ForgeOriginalVoxyQuadMaterialBridge.isTranslucentLayer(quad, layer)
                        ? translucent : opaque;
                consumer.quad(quad, layer, state.getBlock() instanceof LeavesBlock);
            }
        }
    }

    static void onSeasonChange(Level level) {
        Minecraft minecraft = Minecraft.getInstance();
        if (level == minecraft.level && CompatModule.CommonConfig.voxyReloadWhenSeasonChanged.get()) {
            minecraft.execute(() -> {
                if (minecraft.level == level) {
                    ForgeVoxyInstance.INSTANCE.reloadOriginalVoxyRenderer(false);
                }
            });
        }
    }

    static void tick() {
        if (!enabled() || !CompatModule.CommonConfig.voxyLODAutoReload.get()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        var level = minecraft.level;
        var agent = ClientCon.getAgent();
        // Retain Ecliptic's update predicate. A busy/manual importer keeps the change pending.
        if (level == null || level.getGameTime() % 300L == 0L || agent == null || !agent.isSnowChange()) {
            return;
        }
        ForgeVoxyInstance instance = ForgeVoxyInstance.INSTANCE;
        var engine = instance.getCurrentEngineOptional().orElse(null);
        var imports = instance.getImportManager();
        var services = instance.getOriginalVoxyServiceManager();
        if (engine == null || imports == null || services == null) {
            return;
        }
        Path regions;
        var server = minecraft.getSingleplayerServer();
        if (server != null) {
            regions = DimensionType.getStorageFolder(level.dimension(), server.getWorldPath(LevelResource.ROOT))
                    .resolve("region");
        } else {
            // Ecliptic also accepts a named locally saved world/region directory on remote servers.
            String name = agent.getCurrentWorldName();
            if (name == null || name.isBlank()) {
                return;
            }
            Path root = minecraft.gameDirectory.toPath().resolve("saves").resolve(name);
            regions = Files.isRegularFile(root.resolve("level.dat"))
                    ? DimensionType.getStorageFolder(level.dimension(), root).resolve("region")
                    : name.endsWith("region") ? root : root.resolve("region");
        }
        if (!Files.isDirectory(regions)) {
            return; // No on-disk source yet; do not consume the pending seasonal change.
        }
        if (imports.makeAndRunIfNone(engine, () -> {
            WorldImporter importer = new WorldImporter(engine, level, services, instance::canRunOriginalVoxyImportWork);
            importer.importRegionDirectoryAsync(regions.toFile());
            return importer;
        })) {
            agent.setSnowChange(false);
        }
    }
}
