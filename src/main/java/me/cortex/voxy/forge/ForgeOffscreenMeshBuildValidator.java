package me.cortex.voxy.forge;

import me.cortex.voxy.common.world.WorldEngine;
import me.cortex.voxy.common.world.WorldSection;
import me.cortex.voxy.common.world.other.Mapper;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.LinkedHashSet;

public final class ForgeOffscreenMeshBuildValidator {
    private static final int SECTION_SIZE = 32;
    private static final int VERTICES_PER_QUAD = 4;
    private static final int PACKED_QUAD_BYTES = Long.BYTES;

    private ForgeOffscreenMeshBuildValidator() {
    }

    public static MeshBuildStats buildCurrentChunk(WorldEngine engine, LevelChunk chunk, String dimension) {
        if (engine == null || chunk == null) {
            return MeshBuildStats.empty(dimension, 0, 0);
        }
        if (!engine.isLive()) {
            throw new IllegalStateException("Tried building mesh stats from a WorldEngine that was not alive");
        }

        long start = System.nanoTime();
        int chunkX = chunk.getPos().x;
        int chunkZ = chunk.getPos().z;

        var sectionPositions = new LinkedHashSet<Long>();
        int sectionY = chunk.getMinSection();
        for (int i = 0; i < chunk.getSections().length; i++, sectionY++) {
            sectionPositions.add(WorldEngine.getWorldSectionId(
                    0,
                    Math.floorDiv(chunkX, 2),
                    Math.floorDiv(sectionY, 2),
                    Math.floorDiv(chunkZ, 2)
            ));
        }

        int sectionsFound = 0;
        int sectionsBuilt = 0;
        long nonAirVoxels = 0;
        long quads = 0;

        for (long position : sectionPositions) {
            WorldSection section = null;
            try {
                section = engine.acquireIfExists(position);
                if (section == null) {
                    continue;
                }

                sectionsFound++;
                SectionMeshStats sectionStats = buildSectionStats(section);
                if (sectionStats.nonAirVoxels() != 0 || sectionStats.quads() != 0) {
                    sectionsBuilt++;
                }
                nonAirVoxels += sectionStats.nonAirVoxels();
                quads += sectionStats.quads();
            } finally {
                if (section != null) {
                    section.release();
                }
            }
        }

        double elapsedMs = (System.nanoTime() - start) / 1_000_000.0;
        return new MeshBuildStats(
                dimension,
                chunkX,
                chunkZ,
                sectionsFound,
                sectionsBuilt,
                nonAirVoxels,
                quads,
                quads * VERTICES_PER_QUAD,
                quads * PACKED_QUAD_BYTES,
                elapsedMs
        );
    }

    private static SectionMeshStats buildSectionStats(WorldSection section) {
        long nonAirVoxels = 0;
        long quads = 0;
        long[] data = section._unsafeGetRawDataArray();

        for (int y = 0; y < SECTION_SIZE; y++) {
            for (int z = 0; z < SECTION_SIZE; z++) {
                for (int x = 0; x < SECTION_SIZE; x++) {
                    if (isAir(data, x, y, z)) {
                        continue;
                    }

                    nonAirVoxels++;
                    quads += isAir(data, x - 1, y, z) ? 1 : 0;
                    quads += isAir(data, x + 1, y, z) ? 1 : 0;
                    quads += isAir(data, x, y - 1, z) ? 1 : 0;
                    quads += isAir(data, x, y + 1, z) ? 1 : 0;
                    quads += isAir(data, x, y, z - 1) ? 1 : 0;
                    quads += isAir(data, x, y, z + 1) ? 1 : 0;
                }
            }
        }

        return new SectionMeshStats(nonAirVoxels, quads);
    }

    private static boolean isAir(long[] data, int x, int y, int z) {
        if (x < 0 || x >= SECTION_SIZE || y < 0 || y >= SECTION_SIZE || z < 0 || z >= SECTION_SIZE) {
            return true;
        }
        return Mapper.isAir(data[WorldSection.getIndex(x, y, z)]);
    }

    private record SectionMeshStats(long nonAirVoxels, long quads) {
    }

    public record MeshBuildStats(
            String dimension,
            int chunkX,
            int chunkZ,
            int sectionsFound,
            int sectionsBuilt,
            long nonAirVoxels,
            long quads,
            long vertices,
            long estimatedBytes,
            double elapsedMs
    ) {
        private static MeshBuildStats empty(String dimension, int chunkX, int chunkZ) {
            return new MeshBuildStats(dimension, chunkX, chunkZ, 0, 0, 0, 0, 0, 0, 0.0);
        }
    }
}
