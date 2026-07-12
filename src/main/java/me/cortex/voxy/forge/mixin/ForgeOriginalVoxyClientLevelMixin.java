package me.cortex.voxy.forge.mixin;

import me.cortex.voxy.common.world.service.VoxelIngestService;
import me.cortex.voxy.config.ForgeVoxyConfig;
import me.cortex.voxy.forge.ForgeVoxyInstance;
import me.cortex.voxy.forge.ForgeOriginalVoxyWorldIdentifier;
import me.cortex.voxy.forge.ForgeOriginalVoxyWorldIdentifierAccess;
import net.minecraft.client.multiplayer.ClientChunkCache;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.SectionPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.dimension.DimensionType;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Supplier;

@Mixin(ClientLevel.class)
public abstract class ForgeOriginalVoxyClientLevelMixin implements ForgeOriginalVoxyWorldIdentifierAccess {
    @Unique
    private int voxy$bottomSectionY;
    @Unique
    private ForgeOriginalVoxyWorldIdentifier voxy$worldIdentifier;

    @Shadow
    @Final
    public LevelRenderer levelRenderer;

    @Shadow
    public abstract ClientChunkCache getChunkSource();

    @Inject(method = "<init>", at = @At("TAIL"))
    private void voxy$captureBottomSectionY(
            ClientPacketListener connection,
            ClientLevel.ClientLevelData clientLevelData,
            ResourceKey<Level> dimension,
            Holder<DimensionType> dimensionType,
            int viewDistance,
            int serverSimulationDistance,
            Supplier<ProfilerFiller> profiler,
            LevelRenderer levelRenderer,
            boolean debug,
            long biomeZoomSeed,
            CallbackInfo ci) {
        this.voxy$bottomSectionY = ((Level) (Object) this).getMinBuildHeight() >> 4;
        this.voxy$worldIdentifier = new ForgeOriginalVoxyWorldIdentifier(
                dimension,
                biomeZoomSeed,
                dimensionType == null ? null : dimensionType.unwrapKey().orElse(null));
    }

    @Override
    public ForgeOriginalVoxyWorldIdentifier voxy$getOriginalVoxyWorldIdentifier() {
        return this.voxy$worldIdentifier;
    }

    @Inject(method = "setBlocksDirty", at = @At("TAIL"))
    private void voxy$ingestBorderSectionOnStateChange(BlockPos pos, BlockState oldState, BlockState newState, CallbackInfo ci) {
        if (oldState == newState || !newState.isAir()) {
            return;
        }
        if (!ForgeVoxyConfig.ENABLED.get()) {
            return;
        }
        var engine = ForgeVoxyInstance.INSTANCE.getCurrentEngineOptional();
        if (engine.isEmpty()) {
            return;
        }

        int localX = pos.getX() & 15;
        int localY = pos.getY() & 15;
        int localZ = pos.getZ() & 15;
        if (localX != 0 && localX != 15
                && localY != 0 && localY != 15
                && localZ != 0 && localZ != 15) {
            return;
        }

        Level level = (Level) (Object) this;
        SectionPos sectionPos = SectionPos.of(pos);
        if (!(level.getChunk(pos.getX() >> 4, pos.getZ() >> 4, ChunkStatus.FULL, false) instanceof LevelChunk chunk)) {
            return;
        }
        int sectionIndex = sectionPos.y() - this.voxy$bottomSectionY;
        LevelChunkSection[] sections = chunk.getSections();
        if (sectionIndex < 0 || sectionIndex >= sections.length) {
            return;
        }
        LevelChunkSection section = sections[sectionIndex];
        var lightEngine = level.getLightEngine();
        var blockLight = lightEngine.getLayerListener(LightLayer.BLOCK).getDataLayerData(sectionPos);
        var skyLight = lightEngine.getLayerListener(LightLayer.SKY).getDataLayerData(sectionPos);
        VoxelIngestService.rawIngest(
                engine.get(),
                section,
                sectionPos.x(),
                sectionPos.y(),
                sectionPos.z(),
                blockLight == null ? null : blockLight.copy(),
                skyLight == null ? null : skyLight.copy());
    }
}
