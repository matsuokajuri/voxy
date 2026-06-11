package me.cortex.voxy.forge;

import me.cortex.voxy.common.world.WorldEngine;
import me.cortex.voxy.common.world.WorldSection;
import me.cortex.voxy.common.world.other.Mapper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.color.block.BlockColors;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

public final class ForgeCpuMeshBuilder {
    private static final int CHUNK_SECTION_SIZE = 16;
    private static final int VERTICES_PER_QUAD = 4;
    private static final Direction[] DIRECTIONS = Direction.values();

    private ForgeCpuMeshBuilder() {
    }

    public static ForgeCpuMeshBuildResult buildCurrentChunk(WorldEngine engine, LevelChunk chunk, ClientLevel level, String dimension) {
        if (engine == null || chunk == null || level == null) {
            return new ForgeCpuMeshBuildResult(ForgeCpuMeshBuildStats.empty(dimension, 0, 0), List.of());
        }
        if (!engine.isLive()) {
            throw new IllegalStateException("Tried building CPU mesh from a WorldEngine that was not alive");
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
        var context = new ModelLookupContext(engine.getMapper(), blockRenderer, blockColors, level);
        var mutableStats = new MutableBuildStats(dimension, chunkX, chunkZ);
        var accumulators = new HashMap<SectionLayerKey, MutableBuiltSection>();

        int baseLocalX = Math.floorMod(chunkX, 2) * CHUNK_SECTION_SIZE;
        int baseLocalZ = Math.floorMod(chunkZ, 2) * CHUNK_SECTION_SIZE;

        int sectionY = chunk.getMinSection();
        for (int i = 0; i < chunk.getSections().length; i++, sectionY++) {
            long sectionPosition = WorldEngine.getWorldSectionId(
                    0,
                    Math.floorDiv(chunkX, 2),
                    Math.floorDiv(sectionY, 2),
                    Math.floorDiv(chunkZ, 2)
            );

            WorldSection section = null;
            try {
                section = engine.acquireIfExists(sectionPosition);
                if (section == null) {
                    continue;
                }

                mutableStats.foundSections.add(sectionPosition);
                int baseLocalY = Math.floorMod(sectionY, 2) * CHUNK_SECTION_SIZE;
                buildChunkSectionSubregion(
                        context,
                        mutableStats,
                        accumulators,
                        sectionPosition,
                        section._unsafeGetRawDataArray(),
                        baseLocalX,
                        baseLocalY,
                        baseLocalZ,
                        chunkX,
                        sectionY,
                        chunkZ,
                        dimension
                );
            } finally {
                if (section != null) {
                    section.release();
                }
            }
        }

        double elapsedMs = (System.nanoTime() - start) / 1_000_000.0;
        long createdTime = System.currentTimeMillis();
        var builtSections = new ArrayList<ForgeCpuBuiltSection>(accumulators.size());
        for (MutableBuiltSection accumulator : accumulators.values()) {
            if (accumulator.builder.vertexCount() == 0) {
                continue;
            }
            ForgeCpuMeshBuffer buffer = accumulator.builder.build();
            builtSections.add(new ForgeCpuBuiltSection(
                    dimension,
                    chunkX,
                    chunkZ,
                    accumulator.sectionPosition,
                    accumulator.layer,
                    accumulator.sourceHash,
                    createdTime,
                    elapsedMs,
                    buffer
            ));
        }

        mutableStats.bakedModelCount = context.bakedModelIds.size();
        mutableStats.sectionsBuilt = mutableStats.builtSections.size();
        mutableStats.cacheEntriesWritten = builtSections.size();
        mutableStats.elapsedMs = elapsedMs;
        return new ForgeCpuMeshBuildResult(mutableStats.toImmutable(), builtSections);
    }

    private static void buildChunkSectionSubregion(
            ModelLookupContext context,
            MutableBuildStats stats,
            HashMap<SectionLayerKey, MutableBuiltSection> accumulators,
            long sectionPosition,
            long[] data,
            int baseLocalX,
            int baseLocalY,
            int baseLocalZ,
            int chunkX,
            int sectionY,
            int chunkZ,
            String dimension
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
                    CachedModel model = context.getModel(blockId);
                    if (!model.supported) {
                        stats.unsupportedBlocks++;
                        continue;
                    }

                    int worldX = worldBaseX + x;
                    int worldY = worldBaseY + y;
                    int worldZ = worldBaseZ + z;
                    pos.set(worldX, worldY, worldZ);

                    MutableBuiltSection target = getAccumulator(accumulators, dimension, chunkX, chunkZ, sectionPosition, model.layer);
                    int quadsBefore = target.builder.quadCount();
                    appendQuads(context, stats, target, model, model.generalQuads, mapping, worldX, worldY, worldZ, pos, null);
                    for (Direction direction : DIRECTIONS) {
                        if (!isSubregionNeighborAir(data, baseLocalX, baseLocalY, baseLocalZ, x, y, z, direction)) {
                            continue;
                        }
                        appendQuads(context, stats, target, model, model.directionQuads[direction.get3DDataValue()], mapping, worldX, worldY, worldZ, pos, direction);
                    }

                    if (target.builder.quadCount() != quadsBefore) {
                        target.updateHash(mapping, worldX, worldY, worldZ);
                        stats.builtSections.add(sectionPosition);
                    }
                }
            }
        }
    }

    private static MutableBuiltSection getAccumulator(
            HashMap<SectionLayerKey, MutableBuiltSection> accumulators,
            String dimension,
            int chunkX,
            int chunkZ,
            long sectionPosition,
            ForgeCpuMeshLayer layer
    ) {
        var key = new SectionLayerKey(sectionPosition, layer);
        MutableBuiltSection existing = accumulators.get(key);
        if (existing != null) {
            return existing;
        }

        var created = new MutableBuiltSection(dimension, chunkX, chunkZ, sectionPosition, layer);
        accumulators.put(key, created);
        return created;
    }

    private static void appendQuads(
            ModelLookupContext context,
            MutableBuildStats stats,
            MutableBuiltSection target,
            CachedModel model,
            List<BakedQuad> quads,
            long mapping,
            int worldX,
            int worldY,
            int worldZ,
            BlockPos pos,
            Direction cullDirection
    ) {
        if (quads.isEmpty()) {
            return;
        }

        int light = Mapper.getLightId(mapping);
        for (BakedQuad quad : quads) {
            if (!appendQuad(context, target, model, quad, worldX, worldY, worldZ, pos, light, cullDirection)) {
                stats.unsupportedBlocks++;
                continue;
            }

            stats.quads++;
            stats.vertices += VERTICES_PER_QUAD;
            stats.estimatedBytes += VERTICES_PER_QUAD * ForgeCpuMeshBuffer.VERTEX_STRIDE_BYTES;
            stats.addLayerQuad(model.layer);
            if (quad.isTinted()) {
                stats.tintedQuads++;
                stats.tintLookups++;
            }
        }
    }

    private static boolean appendQuad(
            ModelLookupContext context,
            MutableBuiltSection target,
            CachedModel model,
            BakedQuad quad,
            int worldX,
            int worldY,
            int worldZ,
            BlockPos pos,
            int light,
            Direction cullDirection
    ) {
        int[] vertices = quad.getVertices();
        if (vertices == null || vertices.length < 24 || vertices.length % VERTICES_PER_QUAD != 0) {
            return false;
        }

        int stride = vertices.length / VERTICES_PER_QUAD;
        if (stride < 6) {
            return false;
        }

        int tintIndex = quad.isTinted() ? quad.getTintIndex() : -1;
        int color = resolveColor(context, model.state, pos, tintIndex);
        Direction direction = cullDirection == null ? quad.getDirection() : cullDirection;
        int normal = direction == null ? -1 : direction.get3DDataValue();

        for (int vertex = 0; vertex < VERTICES_PER_QUAD; vertex++) {
            int offset = vertex * stride;
            float x = worldX + Float.intBitsToFloat(vertices[offset]);
            float y = worldY + Float.intBitsToFloat(vertices[offset + 1]);
            float z = worldZ + Float.intBitsToFloat(vertices[offset + 2]);
            float u = Float.intBitsToFloat(vertices[offset + 4]);
            float v = Float.intBitsToFloat(vertices[offset + 5]);
            int vertexColor = quad.isTinted() ? color : getBakedColor(vertices, offset, color);
            target.builder.putVertex(x, y, z, vertexColor, u, v, light, normal, model.layer.id, tintIndex);
        }
        return true;
    }

    private static int resolveColor(ModelLookupContext context, BlockState state, BlockPos pos, int tintIndex) {
        if (tintIndex < 0) {
            return 0xFFFFFFFF;
        }

        try {
            int rgb = context.blockColors.getColor(state, context.level, pos, tintIndex);
            return rgb == -1 ? 0xFFFFFFFF : 0xFF000000 | rgb;
        } catch (RuntimeException e) {
            return 0xFFFFFFFF;
        }
    }

    private static int getBakedColor(int[] vertices, int offset, int fallback) {
        if (offset + 3 >= vertices.length) {
            return fallback;
        }
        int color = vertices[offset + 3];
        return color == 0 ? fallback : color;
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

    @SuppressWarnings("unchecked")
    private static CachedModel computeModel(Mapper mapper, BlockRenderDispatcher blockRenderer, int blockId) {
        BlockState state;
        try {
            state = mapper.getBlockStateFromBlockId(blockId);
        } catch (RuntimeException e) {
            return CachedModel.unsupported();
        }

        if (state == null || state.isAir()) {
            return CachedModel.unsupported();
        }

        BakedModel model;
        try {
            model = blockRenderer.getBlockModel(state);
        } catch (RuntimeException e) {
            return CachedModel.unsupported();
        }

        if (model == null || model.isCustomRenderer()) {
            return CachedModel.unsupported();
        }

        List<BakedQuad>[] directional = new List[DIRECTIONS.length];
        for (Direction direction : DIRECTIONS) {
            directional[direction.get3DDataValue()] = safeGetQuads(model, state, direction, blockId);
        }
        return new CachedModel(
                state,
                ForgeCpuMeshLayer.fromBlockState(state),
                safeGetQuads(model, state, null, blockId),
                directional
        );
    }

    private static List<BakedQuad> safeGetQuads(BakedModel model, BlockState state, Direction direction, int blockId) {
        try {
            List<BakedQuad> quads = model.getQuads(state, direction, RandomSource.create(blockId));
            return quads == null ? List.of() : quads;
        } catch (RuntimeException e) {
            return List.of();
        }
    }

    private static final class ModelLookupContext {
        private final Mapper mapper;
        private final BlockRenderDispatcher blockRenderer;
        private final BlockColors blockColors;
        private final ClientLevel level;
        private final HashMap<Integer, CachedModel> modelCache = new HashMap<>();
        private final HashSet<Integer> bakedModelIds = new HashSet<>();

        private ModelLookupContext(Mapper mapper, BlockRenderDispatcher blockRenderer, BlockColors blockColors, ClientLevel level) {
            this.mapper = mapper;
            this.blockRenderer = blockRenderer;
            this.blockColors = blockColors;
            this.level = level;
        }

        private CachedModel getModel(int blockId) {
            CachedModel model = this.modelCache.get(blockId);
            if (model == null) {
                model = computeModel(this.mapper, this.blockRenderer, blockId);
                this.modelCache.put(blockId, model);
                if (model.supported) {
                    this.bakedModelIds.add(blockId);
                }
            }
            return model;
        }
    }

    private static final class CachedModel {
        private final boolean supported;
        private final BlockState state;
        private final ForgeCpuMeshLayer layer;
        private final List<BakedQuad> generalQuads;
        private final List<BakedQuad>[] directionQuads;

        private CachedModel(BlockState state, ForgeCpuMeshLayer layer, List<BakedQuad> generalQuads, List<BakedQuad>[] directionQuads) {
            this.supported = true;
            this.state = state;
            this.layer = layer;
            this.generalQuads = generalQuads;
            this.directionQuads = directionQuads;
        }

        @SuppressWarnings("unchecked")
        private CachedModel() {
            this.supported = false;
            this.state = null;
            this.layer = ForgeCpuMeshLayer.OTHER;
            this.generalQuads = List.of();
            this.directionQuads = new List[DIRECTIONS.length];
            for (int i = 0; i < this.directionQuads.length; i++) {
                this.directionQuads[i] = List.of();
            }
        }

        private static CachedModel unsupported() {
            return new CachedModel();
        }
    }

    private static final class MutableBuiltSection {
        private final String dimension;
        private final int chunkX;
        private final int chunkZ;
        private final long sectionPosition;
        private final ForgeCpuMeshLayer layer;
        private final ForgeCpuMeshBuffer.Builder builder = new ForgeCpuMeshBuffer.Builder();
        private long sourceHash = 0x9E3779B97F4A7C15L;

        private MutableBuiltSection(String dimension, int chunkX, int chunkZ, long sectionPosition, ForgeCpuMeshLayer layer) {
            this.dimension = dimension;
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
            this.sectionPosition = sectionPosition;
            this.layer = layer;
        }

        private void updateHash(long mapping, int worldX, int worldY, int worldZ) {
            this.sourceHash ^= Long.rotateLeft(mapping, 17);
            this.sourceHash = this.sourceHash * 31 + worldX;
            this.sourceHash = this.sourceHash * 31 + worldY;
            this.sourceHash = this.sourceHash * 31 + worldZ;
        }
    }

    private static final class MutableBuildStats {
        private final String dimension;
        private final int chunkX;
        private final int chunkZ;
        private final HashSet<Long> foundSections = new HashSet<>();
        private final HashSet<Long> builtSections = new HashSet<>();
        private int sectionsBuilt;
        private int cacheEntriesWritten;
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

        private MutableBuildStats(String dimension, int chunkX, int chunkZ) {
            this.dimension = dimension;
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
        }

        private void addLayerQuad(ForgeCpuMeshLayer layer) {
            switch (layer) {
                case SOLID -> this.solidQuads++;
                case CUTOUT -> this.cutoutQuads++;
                case TRANSLUCENT -> this.translucentQuads++;
                case OTHER -> this.otherLayerQuads++;
            }
        }

        private ForgeCpuMeshBuildStats toImmutable() {
            return new ForgeCpuMeshBuildStats(
                    this.dimension,
                    this.chunkX,
                    this.chunkZ,
                    this.foundSections.size(),
                    this.sectionsBuilt,
                    this.cacheEntriesWritten,
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

    private record SectionLayerKey(long sectionPosition, ForgeCpuMeshLayer layer) {
    }
}
