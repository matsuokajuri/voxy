package me.cortex.voxy.forge;

import me.cortex.voxy.common.world.WorldEngine;
import me.cortex.voxy.common.world.WorldSection;
import me.cortex.voxy.common.world.other.Mapper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.color.block.BlockColors;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

public final class ForgeModelAwareMeshBuildValidator {
    private static final int VOXY_SECTION_SIZE = 32;
    private static final int CHUNK_SECTION_SIZE = 16;
    private static final int VERTICES_PER_QUAD = 4;
    private static final int VANILLA_BAKED_QUAD_BYTES = VERTICES_PER_QUAD * 8 * Integer.BYTES;
    private static final Direction[] DIRECTIONS = Direction.values();

    private ForgeModelAwareMeshBuildValidator() {
    }

    public static ModelMeshStats buildCurrentChunk(WorldEngine engine, LevelChunk chunk, ClientLevel level, String dimension) {
        if (engine == null || chunk == null || level == null) {
            return ModelMeshStats.empty(dimension, 0, 0);
        }
        if (!engine.isLive()) {
            throw new IllegalStateException("Tried building model mesh stats from a WorldEngine that was not alive");
        }

        var minecraft = Minecraft.getInstance();
        BlockRenderDispatcher blockRenderer = minecraft.getBlockRenderer();
        BlockColors blockColors = minecraft.getBlockColors();
        if (blockRenderer == null || blockColors == null) {
            throw new IllegalStateException("Minecraft model system is not ready");
        }

        long start = System.nanoTime();
        int chunkX = chunk.getPos().x;
        int chunkZ = chunk.getPos().z;
        var mutable = new MutableModelMeshStats(dimension, chunkX, chunkZ);
        var context = new ModelLookupContext(engine.getMapper(), blockRenderer, blockColors, level);

        int baseLocalX = Math.floorMod(chunkX, 2) * CHUNK_SECTION_SIZE;
        int baseLocalZ = Math.floorMod(chunkZ, 2) * CHUNK_SECTION_SIZE;

        int sectionY = chunk.getMinSection();
        for (int i = 0; i < chunk.getSections().length; i++, sectionY++) {
            long position = WorldEngine.getWorldSectionId(
                    0,
                    Math.floorDiv(chunkX, 2),
                    Math.floorDiv(sectionY, 2),
                    Math.floorDiv(chunkZ, 2)
            );

            WorldSection section = null;
            try {
                section = engine.acquireIfExists(position);
                if (section == null) {
                    continue;
                }

                mutable.foundSections.add(position);
                int baseLocalY = Math.floorMod(sectionY, 2) * CHUNK_SECTION_SIZE;
                long quadsBefore = mutable.quads;
                long blocksBefore = mutable.blocksSampled;
                buildChunkSectionSubregion(
                        context,
                        mutable,
                        section._unsafeGetRawDataArray(),
                        baseLocalX,
                        baseLocalY,
                        baseLocalZ,
                        chunkX,
                        sectionY,
                        chunkZ
                );
                if (mutable.quads != quadsBefore || mutable.blocksSampled != blocksBefore) {
                    mutable.builtSections.add(position);
                }
            } finally {
                if (section != null) {
                    section.release();
                }
            }
        }

        mutable.bakedModelCount = context.bakedModelIds.size();
        mutable.elapsedMs = (System.nanoTime() - start) / 1_000_000.0;
        return mutable.toImmutable();
    }

    private static void buildChunkSectionSubregion(
            ModelLookupContext context,
            MutableModelMeshStats stats,
            long[] data,
            int baseLocalX,
            int baseLocalY,
            int baseLocalZ,
            int chunkX,
            int sectionY,
            int chunkZ
    ) {
        var pos = new BlockPos.MutableBlockPos();
        int worldBaseX = chunkX * CHUNK_SECTION_SIZE;
        int worldBaseY = sectionY * CHUNK_SECTION_SIZE;
        int worldBaseZ = chunkZ * CHUNK_SECTION_SIZE;

        for (int y = 0; y < CHUNK_SECTION_SIZE; y++) {
            int localY = baseLocalY + y;
            for (int z = 0; z < CHUNK_SECTION_SIZE; z++) {
                int localZ = baseLocalZ + z;
                for (int x = 0; x < CHUNK_SECTION_SIZE; x++) {
                    int localX = baseLocalX + x;
                    long mapping = data[WorldSection.getIndex(localX, localY, localZ)];
                    if (Mapper.isAir(mapping)) {
                        continue;
                    }

                    stats.blocksSampled++;
                    int blockId = Mapper.getBlockId(mapping);
                    CachedModelStats model = context.getModelStats(blockId);
                    if (!model.supported) {
                        stats.unsupportedBlocks++;
                        continue;
                    }

                    int worldX = worldBaseX + x;
                    int worldY = worldBaseY + y;
                    int worldZ = worldBaseZ + z;
                    pos.set(worldX, worldY, worldZ);

                    QuadCounts counts = countVisibleQuads(model, data, baseLocalX, baseLocalY, baseLocalZ, x, y, z);
                    if (counts.quads == 0) {
                        continue;
                    }

                    stats.quads += counts.quads;
                    stats.tintedQuads += counts.tintedQuads;
                    stats.vertices += counts.quads * VERTICES_PER_QUAD;
                    stats.estimatedBytes += counts.quads * VANILLA_BAKED_QUAD_BYTES;
                    stats.addLayerQuads(model.layer, counts.quads);

                    if (counts.tintedQuads != 0) {
                        try {
                            context.blockColors.getColor(model.state, context.level, pos, model.firstTintIndex);
                            stats.tintLookups++;
                        } catch (Exception e) {
                            stats.tintFailures++;
                        }
                    }
                }
            }
        }
    }

    private static QuadCounts countVisibleQuads(
            CachedModelStats model,
            long[] data,
            int baseLocalX,
            int baseLocalY,
            int baseLocalZ,
            int x,
            int y,
            int z
    ) {
        long quads = model.generalQuads;
        long tinted = model.generalTintedQuads;

        for (Direction direction : DIRECTIONS) {
            if (!isSubregionNeighborAir(data, baseLocalX, baseLocalY, baseLocalZ, x, y, z, direction)) {
                continue;
            }
            int index = direction.get3DDataValue();
            quads += model.directionQuads[index];
            tinted += model.directionTintedQuads[index];
        }

        return new QuadCounts(quads, tinted);
    }

    private static boolean isSubregionNeighborAir(
            long[] data,
            int baseLocalX,
            int baseLocalY,
            int baseLocalZ,
            int x,
            int y,
            int z,
            Direction direction
    ) {
        int neighborX = x + direction.getStepX();
        int neighborY = y + direction.getStepY();
        int neighborZ = z + direction.getStepZ();
        if (neighborX < 0 || neighborX >= CHUNK_SECTION_SIZE
                || neighborY < 0 || neighborY >= CHUNK_SECTION_SIZE
                || neighborZ < 0 || neighborZ >= CHUNK_SECTION_SIZE) {
            return true;
        }

        long neighbor = data[WorldSection.getIndex(
                baseLocalX + neighborX,
                baseLocalY + neighborY,
                baseLocalZ + neighborZ
        )];
        return Mapper.isAir(neighbor);
    }

    private static CachedModelStats computeModelStats(Mapper mapper, BlockRenderDispatcher blockRenderer, int blockId) {
        BlockState state;
        try {
            state = mapper.getBlockStateFromBlockId(blockId);
        } catch (RuntimeException e) {
            return CachedModelStats.unsupported();
        }

        if (state == null || state.isAir()) {
            return CachedModelStats.unsupported();
        }

        BakedModel model;
        try {
            model = blockRenderer.getBlockModel(state);
        } catch (RuntimeException e) {
            return CachedModelStats.unsupported();
        }

        if (model == null || model.isCustomRenderer()) {
            return CachedModelStats.unsupported();
        }

        var stats = new CachedModelStats(state, getLayerBucket(state));
        addQuadStats(stats, -1, model.getQuads(state, null, RandomSource.create(blockId)));
        for (Direction direction : DIRECTIONS) {
            addQuadStats(stats, direction.get3DDataValue(), model.getQuads(state, direction, RandomSource.create(blockId)));
        }
        return stats;
    }

    private static void addQuadStats(CachedModelStats stats, int directionIndex, List<BakedQuad> quads) {
        if (quads == null || quads.isEmpty()) {
            return;
        }

        int tinted = 0;
        int firstTintIndex = stats.firstTintIndex;
        for (BakedQuad quad : quads) {
            if (quad.isTinted()) {
                tinted++;
                if (firstTintIndex < 0) {
                    firstTintIndex = quad.getTintIndex();
                }
            }
        }
        stats.firstTintIndex = firstTintIndex;

        if (directionIndex < 0) {
            stats.generalQuads += quads.size();
            stats.generalTintedQuads += tinted;
        } else {
            stats.directionQuads[directionIndex] += quads.size();
            stats.directionTintedQuads[directionIndex] += tinted;
        }
    }

    private static LayerBucket getLayerBucket(BlockState state) {
        RenderType type = ItemBlockRenderTypes.getChunkRenderType(state);
        if (type == RenderType.translucent()) {
            return LayerBucket.TRANSLUCENT;
        }
        if (type == RenderType.cutout() || type == RenderType.cutoutMipped()) {
            return LayerBucket.CUTOUT;
        }
        if (type == RenderType.solid()) {
            return LayerBucket.SOLID;
        }
        return LayerBucket.OTHER;
    }

    private static final class ModelLookupContext {
        private final Mapper mapper;
        private final BlockRenderDispatcher blockRenderer;
        private final BlockColors blockColors;
        private final ClientLevel level;
        private final HashMap<Integer, CachedModelStats> modelCache = new HashMap<>();
        private final HashSet<Integer> bakedModelIds = new HashSet<>();

        private ModelLookupContext(Mapper mapper, BlockRenderDispatcher blockRenderer, BlockColors blockColors, ClientLevel level) {
            this.mapper = mapper;
            this.blockRenderer = blockRenderer;
            this.blockColors = blockColors;
            this.level = level;
        }

        private CachedModelStats getModelStats(int blockId) {
            CachedModelStats stats = this.modelCache.get(blockId);
            if (stats == null) {
                stats = computeModelStats(this.mapper, this.blockRenderer, blockId);
                this.modelCache.put(blockId, stats);
                if (stats.supported) {
                    this.bakedModelIds.add(blockId);
                }
            }
            return stats;
        }
    }

    private static final class CachedModelStats {
        private final boolean supported;
        private final BlockState state;
        private final LayerBucket layer;
        private final int[] directionQuads = new int[DIRECTIONS.length];
        private final int[] directionTintedQuads = new int[DIRECTIONS.length];
        private int generalQuads;
        private int generalTintedQuads;
        private int firstTintIndex = -1;

        private CachedModelStats(BlockState state, LayerBucket layer) {
            this.supported = true;
            this.state = state;
            this.layer = layer;
        }

        private CachedModelStats() {
            this.supported = false;
            this.state = null;
            this.layer = LayerBucket.OTHER;
        }

        private static CachedModelStats unsupported() {
            return new CachedModelStats();
        }
    }

    private static final class MutableModelMeshStats {
        private final String dimension;
        private final int chunkX;
        private final int chunkZ;
        private final HashSet<Long> foundSections = new HashSet<>();
        private final HashSet<Long> builtSections = new HashSet<>();
        private long blocksSampled;
        private long bakedModelCount;
        private long quads;
        private long vertices;
        private long tintedQuads;
        private long tintLookups;
        private long tintFailures;
        private long solidQuads;
        private long cutoutQuads;
        private long translucentQuads;
        private long otherLayerQuads;
        private long unsupportedBlocks;
        private long estimatedBytes;
        private double elapsedMs;

        private MutableModelMeshStats(String dimension, int chunkX, int chunkZ) {
            this.dimension = dimension;
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
        }

        private void addLayerQuads(LayerBucket layer, long quads) {
            switch (layer) {
                case SOLID -> this.solidQuads += quads;
                case CUTOUT -> this.cutoutQuads += quads;
                case TRANSLUCENT -> this.translucentQuads += quads;
                case OTHER -> this.otherLayerQuads += quads;
            }
        }

        private ModelMeshStats toImmutable() {
            return new ModelMeshStats(
                    this.dimension,
                    this.chunkX,
                    this.chunkZ,
                    this.foundSections.size(),
                    this.builtSections.size(),
                    this.blocksSampled,
                    this.bakedModelCount,
                    this.quads,
                    this.vertices,
                    this.tintedQuads,
                    this.tintLookups,
                    this.tintFailures,
                    this.solidQuads,
                    this.cutoutQuads,
                    this.translucentQuads,
                    this.otherLayerQuads,
                    this.unsupportedBlocks,
                    this.estimatedBytes,
                    this.elapsedMs
            );
        }
    }

    private enum LayerBucket {
        SOLID,
        CUTOUT,
        TRANSLUCENT,
        OTHER
    }

    private record QuadCounts(long quads, long tintedQuads) {
    }

    public record ModelMeshStats(
            String dimension,
            int chunkX,
            int chunkZ,
            int sectionsFound,
            int sectionsBuilt,
            long blocksSampled,
            long bakedModelCount,
            long quads,
            long vertices,
            long tintedQuads,
            long tintLookups,
            long tintFailures,
            long solidQuads,
            long cutoutQuads,
            long translucentQuads,
            long otherLayerQuads,
            long unsupportedBlocks,
            long estimatedBytes,
            double elapsedMs
    ) {
        private static ModelMeshStats empty(String dimension, int chunkX, int chunkZ) {
            return new ModelMeshStats(dimension, chunkX, chunkZ, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0.0);
        }
    }
}
