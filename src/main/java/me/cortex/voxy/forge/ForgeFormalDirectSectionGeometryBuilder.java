package me.cortex.voxy.forge;

import me.cortex.voxy.common.world.WorldEngine;
import me.cortex.voxy.common.world.WorldSection;
import me.cortex.voxy.common.world.other.Mapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class ForgeFormalDirectSectionGeometryBuilder {
    private static final int SECTION_SIZE = 32;
    private static final int FACE_COUNT = 6;
    private static final int MAX_RECORDS_PER_SECTION = 1 << 16;

    private static final int[] DX = {0, 0, 0, 0, -1, 1};
    private static final int[] DY = {-1, 1, 0, 0, 0, 0};
    private static final int[] DZ = {0, 0, -1, 1, 0, 0};

    private ForgeFormalDirectSectionGeometryBuilder() {
    }

    static Result build(
            WorldEngine engine,
            String dimension,
            long sectionPosition,
            Map<Integer, ForgeFormalUploadedModelSummary> summariesByBlockState
    ) {
        if (engine == null || summariesByBlockState == null || summariesByBlockState.isEmpty()) {
            return Result.empty("missing-engine-or-formal-models");
        }

        WorldSection section = null;
        try {
            section = engine.acquireIfExists(sectionPosition);
            if (section == null) {
                return Result.empty("world-section-not-loaded");
            }
            return buildSection(dimension, section, summariesByBlockState);
        } finally {
            if (section != null) {
                section.release();
            }
        }
    }

    private static Result buildSection(
            String dimension,
            WorldSection section,
            Map<Integer, ForgeFormalUploadedModelSummary> summariesByBlockState
    ) {
        long[] raw = section._unsafeGetRawDataArray();
        if (raw == null || raw.length < WorldSection.SECTION_VOLUME) {
            return Result.empty("world-section-raw-data-missing");
        }

        var quads = new ArrayList<ForgeVoxyQuadEncoder.EncodedQuad>();
        var stats = new MutableStats(section.key);
        int minX = SECTION_SIZE;
        int minY = SECTION_SIZE;
        int minZ = SECTION_SIZE;
        int maxX = -1;
        int maxY = -1;
        int maxZ = -1;

        for (int y = 0; y < SECTION_SIZE; y++) {
            for (int z = 0; z < SECTION_SIZE; z++) {
                for (int x = 0; x < SECTION_SIZE; x++) {
                    long mapping = raw[WorldSection.getIndex(x, y, z)];
                    if (Mapper.isAir(mapping)) {
                        continue;
                    }
                    stats.blocksVisited++;
                    int blockStateId = Mapper.getBlockId(mapping);
                    ForgeFormalUploadedModelSummary model = summariesByBlockState.get(blockStateId);
                    if (model == null) {
                        stats.rejectedUnsupportedBlock++;
                        continue;
                    }
                    if (model.modelRecordBytes() != ForgeModelStoreFormalLayout.MODEL_RECORD_BYTES
                            || !ForgeModelAtlasLayout.isValidModelId(model.formalModelId())
                            || model.formalModelId() <= 0) {
                        stats.rejectedUnsafeModel++;
                        continue;
                    }

                    minX = Math.min(minX, x);
                    minY = Math.min(minY, y);
                    minZ = Math.min(minZ, z);
                    maxX = Math.max(maxX, x);
                    maxY = Math.max(maxY, y);
                    maxZ = Math.max(maxZ, z);

                    int biomeId = clampBiome(Mapper.getBiomeId(mapping));
                    int light = Mapper.getLightId(mapping) & 0xFF;
                    for (int face = 0; face < FACE_COUNT; face++) {
                        if (!shouldEmitFace(raw, x, y, z, face)) {
                            continue;
                        }
                        quads.add(encodedUnitQuad(face, x, y, z, model.formalModelId(), biomeId, light));
                        stats.naiveQuads++;
                        if (quads.size() >= MAX_RECORDS_PER_SECTION) {
                            stats.truncated = true;
                            break;
                        }
                    }
                    if (stats.truncated) {
                        break;
                    }
                }
                if (stats.truncated) {
                    break;
                }
            }
            if (stats.truncated) {
                break;
            }
        }

        if (quads.isEmpty()) {
            stats.empty = true;
            return new Result(List.of(), new Stats(
                    section.key,
                    0,
                    0,
                    stats.blocksVisited,
                    stats.rejectedUnsupportedBlock,
                    stats.rejectedUnsafeModel,
                    true,
                    stats.truncated,
                    "no-formal-quads"
            ));
        }

        ForgeVoxyGreedyMesher.Result greedy = ForgeVoxyGreedyMesher.merge(quads);
        int aabb = packAabb(minX, minY, minZ, maxX, maxY, maxZ);
        int chunkX = section.x * 2;
        int chunkZ = section.z * 2;
        ForgeVoxyBuiltSection built = new ForgeVoxyBuiltSection(
                dimension,
                chunkX,
                chunkZ,
                section.key,
                section.getNonEmptyChildren(),
                aabb,
                greedy.offsets(),
                ForgeVoxyGeometryBuffer.formalOriginalBitLayout(greedy.records()),
                null,
                stats.naiveQuads,
                greedy.stats().mergedQuads(),
                greedy.stats().coveredArea(),
                greedy.stats().skippedTranslucent(),
                greedy.stats().skippedNonMergeable(),
                System.currentTimeMillis()
        );
        return new Result(List.of(built), new Stats(
                section.key,
                1,
                greedy.records().length,
                stats.blocksVisited,
                stats.rejectedUnsupportedBlock,
                stats.rejectedUnsafeModel,
                false,
                stats.truncated,
                "none"
        ));
    }

    private static ForgeVoxyQuadEncoder.EncodedQuad encodedUnitQuad(
            int face,
            int localX,
            int localY,
            int localZ,
            int formalModelId,
            int biomeId,
            int light
    ) {
        long record = ForgeVoxyQuadEncoder.packPreviewRecord(face, localX, localY, localZ, 1, 1, formalModelId, biomeId, light);
        return new ForgeVoxyQuadEncoder.EncodedQuad(
                record,
                ForgeVoxyQuadEncoder.bucketFor(ForgeCpuMeshLayer.SOLID, face),
                face,
                localX,
                localY,
                localZ,
                1,
                1,
                formalModelId,
                false,
                false,
                biomeId,
                false,
                false,
                light,
                ForgeCpuMeshLayer.SOLID.id,
                -1,
                false,
                false,
                true
        );
    }

    private static boolean shouldEmitFace(long[] raw, int x, int y, int z, int face) {
        int nx = x + DX[face];
        int ny = y + DY[face];
        int nz = z + DZ[face];
        if (nx < 0 || nx >= SECTION_SIZE || ny < 0 || ny >= SECTION_SIZE || nz < 0 || nz >= SECTION_SIZE) {
            return true;
        }
        return Mapper.isAir(raw[WorldSection.getIndex(nx, ny, nz)]);
    }

    private static int clampBiome(int biomeId) {
        if (biomeId < 0) {
            return 0;
        }
        return Math.min(biomeId, (1 << ForgeVoxyQuadEncoder.BIOME_ID_BITS) - 1);
    }

    private static int packAabb(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        if (maxX < minX || maxY < minY || maxZ < minZ) {
            return -1;
        }
        int sizeX = Math.max(1, maxX - minX + 1);
        int sizeY = Math.max(1, maxY - minY + 1);
        int sizeZ = Math.max(1, maxZ - minZ + 1);
        return clampToSection(minX)
                | (clampToSection(minY) << 5)
                | (clampToSection(minZ) << 10)
                | ((Math.min(sizeX, SECTION_SIZE) - 1) << 15)
                | ((Math.min(sizeY, SECTION_SIZE) - 1) << 20)
                | ((Math.min(sizeZ, SECTION_SIZE) - 1) << 25);
    }

    private static int clampToSection(int value) {
        return Math.max(0, Math.min(31, value));
    }

    static Map<Integer, ForgeFormalUploadedModelSummary> summariesByBlockState(List<ForgeFormalUploadedModelSummary> summaries) {
        var map = new LinkedHashMap<Integer, ForgeFormalUploadedModelSummary>();
        for (ForgeFormalUploadedModelSummary summary : summaries) {
            map.putIfAbsent(summary.blockStateId(), summary);
        }
        return map;
    }

    record Result(List<ForgeVoxyBuiltSection> sections, Stats stats) {
        static Result empty(String reason) {
            return new Result(List.of(), new Stats(0L, 0, 0, 0, 0, 0, true, false, reason));
        }
    }

    record Stats(
            long sectionPosition,
            int sectionCount,
            int recordCount,
            int blocksVisited,
            int rejectedUnsupportedBlock,
            int rejectedUnsafeModel,
            boolean empty,
            boolean truncated,
            String lastFailureReason
    ) {
    }

    private static final class MutableStats {
        private final long sectionPosition;
        private int blocksVisited;
        private int rejectedUnsupportedBlock;
        private int rejectedUnsafeModel;
        private int naiveQuads;
        private boolean empty;
        private boolean truncated;

        private MutableStats(long sectionPosition) {
            this.sectionPosition = sectionPosition;
        }
    }
}
