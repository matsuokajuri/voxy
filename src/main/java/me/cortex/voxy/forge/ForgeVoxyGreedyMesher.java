package me.cortex.voxy.forge;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ForgeVoxyGreedyMesher {
    private static final int BUCKET_COUNT = 8;
    private static final int GRID_SIZE = 32;
    private static final int MAX_SPAN = 16;

    private ForgeVoxyGreedyMesher() {
    }

    static Result merge(List<ForgeVoxyQuadEncoder.EncodedQuad> quads) {
        var bucketRecords = new ArrayList<ArrayList<Long>>(BUCKET_COUNT);
        for (int i = 0; i < BUCKET_COUNT; i++) {
            bucketRecords.add(new ArrayList<>());
        }

        var mutableStats = new MutableStats(quads.size());
        for (int bucket = 0; bucket < BUCKET_COUNT; bucket++) {
            var directRecords = bucketRecords.get(bucket);
            var groups = new HashMap<MergeKey, ArrayList<ForgeVoxyQuadEncoder.EncodedQuad>>();
            for (ForgeVoxyQuadEncoder.EncodedQuad quad : quads) {
                if (quad.bucket() != bucket) {
                    continue;
                }
                if (!canGreedilyMerge(quad)) {
                    directRecords.add(quad.record());
                    mutableStats.recordOutput(quad.length(), quad.width());
                    if (quad.bucket() == 0) {
                        mutableStats.skippedTranslucent++;
                    } else {
                        mutableStats.skippedNonMergeable++;
                    }
                    continue;
                }

                MergeKey key = MergeKey.from(quad);
                groups.computeIfAbsent(key, ignored -> new ArrayList<>()).add(quad);
            }

            for (Map.Entry<MergeKey, ArrayList<ForgeVoxyQuadEncoder.EncodedQuad>> entry : groups.entrySet()) {
                mergeGroup(entry.getKey(), entry.getValue(), directRecords, mutableStats);
            }
        }

        int[] offsets = new int[BUCKET_COUNT];
        int totalRecords = 0;
        for (int i = 0; i < BUCKET_COUNT; i++) {
            offsets[i] = totalRecords;
            totalRecords += bucketRecords.get(i).size();
        }

        long[] records = new long[totalRecords];
        int writeIndex = 0;
        for (int i = 0; i < BUCKET_COUNT; i++) {
            for (long record : bucketRecords.get(i)) {
                records[writeIndex++] = record;
            }
        }

        return new Result(records, offsets, mutableStats.toImmutable(totalRecords));
    }

    private static boolean canGreedilyMerge(ForgeVoxyQuadEncoder.EncodedQuad quad) {
        return quad.mergeable()
                && quad.bucket() >= 2
                && quad.bucket() < BUCKET_COUNT
                && quad.length() == 1
                && quad.width() == 1
                && !quad.missingModelId()
                && !quad.modelIdOverflow()
                && !quad.missingBiomeId()
                && !quad.biomeIdOverflow();
    }

    private static void mergeGroup(
            MergeKey key,
            List<ForgeVoxyQuadEncoder.EncodedQuad> quads,
            ArrayList<Long> output,
            MutableStats stats
    ) {
        var grid = new ForgeVoxyQuadEncoder.EncodedQuad[GRID_SIZE * GRID_SIZE];
        var used = new boolean[GRID_SIZE * GRID_SIZE];
        for (ForgeVoxyQuadEncoder.EncodedQuad quad : quads) {
            int u = planeU(quad);
            int v = planeV(quad);
            if (!isGridCoordinate(u) || !isGridCoordinate(v)) {
                output.add(quad.record());
                stats.skippedNonMergeable++;
                stats.recordOutput(quad.length(), quad.width());
                continue;
            }

            int index = v * GRID_SIZE + u;
            if (grid[index] != null) {
                output.add(quad.record());
                stats.skippedNonMergeable++;
                stats.recordOutput(quad.length(), quad.width());
                continue;
            }
            grid[index] = quad;
        }

        for (int v = 0; v < GRID_SIZE; v++) {
            for (int u = 0; u < GRID_SIZE; u++) {
                int index = v * GRID_SIZE + u;
                ForgeVoxyQuadEncoder.EncodedQuad base = grid[index];
                if (base == null || used[index]) {
                    continue;
                }

                int length = findLength(grid, used, u, v);
                int width = findWidth(grid, used, u, v, length);
                markUsed(used, u, v, length, width);

                int localX = localXFor(key, u, v);
                int localY = localYFor(key, u, v);
                int localZ = localZFor(key, u, v);
                ForgeVoxyQuadEncoder.EncodedQuad merged = ForgeVoxyQuadEncoder.encodeMerged(base, localX, localY, localZ, length, width);
                output.add(merged.record());
                stats.recordOutput(length, width);
                if (length * width > 1 && stats.sampleMergedRecordHex.equals("none")) {
                    stats.sampleMergedRecordHex = ForgeVoxyQuadEncoder.formatRecordHex(merged.record());
                    stats.sampleMergedDecodedRecord = ForgeVoxyQuadEncoder.decodeRecord(merged.record());
                }
            }
        }
    }

    private static int findLength(ForgeVoxyQuadEncoder.EncodedQuad[] grid, boolean[] used, int startU, int v) {
        int length = 0;
        while (length < MAX_SPAN && startU + length < GRID_SIZE) {
            int index = v * GRID_SIZE + startU + length;
            if (grid[index] == null || used[index]) {
                break;
            }
            length++;
        }
        return Math.max(1, length);
    }

    private static int findWidth(ForgeVoxyQuadEncoder.EncodedQuad[] grid, boolean[] used, int startU, int startV, int length) {
        int width = 1;
        while (width < MAX_SPAN && startV + width < GRID_SIZE) {
            for (int u = 0; u < length; u++) {
                int index = (startV + width) * GRID_SIZE + startU + u;
                if (grid[index] == null || used[index]) {
                    return width;
                }
            }
            width++;
        }
        return width;
    }

    private static void markUsed(boolean[] used, int startU, int startV, int length, int width) {
        for (int v = 0; v < width; v++) {
            for (int u = 0; u < length; u++) {
                used[(startV + v) * GRID_SIZE + startU + u] = true;
            }
        }
    }

    private static boolean isGridCoordinate(int coordinate) {
        return coordinate >= 0 && coordinate < GRID_SIZE;
    }

    private static int planeU(ForgeVoxyQuadEncoder.EncodedQuad quad) {
        int axis = quad.face() >> 1;
        return axis == 2 ? quad.localY() : quad.localX();
    }

    private static int planeV(ForgeVoxyQuadEncoder.EncodedQuad quad) {
        int axis = quad.face() >> 1;
        return axis == 1 ? quad.localY() : quad.localZ();
    }

    private static int localXFor(MergeKey key, int u, int v) {
        return key.axis() == 2 ? key.auxiliaryPosition() : u;
    }

    private static int localYFor(MergeKey key, int u, int v) {
        if (key.axis() == 0) {
            return key.auxiliaryPosition();
        }
        return key.axis() == 1 ? v : u;
    }

    private static int localZFor(MergeKey key, int u, int v) {
        return key.axis() == 1 ? key.auxiliaryPosition() : v;
    }

    private record MergeKey(
            int face,
            int axis,
            int auxiliaryPosition,
            int bucket,
            int modelId,
            int biomeId,
            int lightId,
            int layerId,
            int tintIndex
    ) {
        private static MergeKey from(ForgeVoxyQuadEncoder.EncodedQuad quad) {
            int axis = quad.face() >> 1;
            int auxiliaryPosition;
            if (axis == 0) {
                auxiliaryPosition = quad.localY();
            } else if (axis == 1) {
                auxiliaryPosition = quad.localZ();
            } else {
                auxiliaryPosition = quad.localX();
            }
            return new MergeKey(
                    quad.face(),
                    axis,
                    auxiliaryPosition,
                    quad.bucket(),
                    quad.modelId(),
                    quad.biomeId(),
                    quad.lightId(),
                    quad.layerId(),
                    quad.tintIndex()
            );
        }
    }

    private static final class MutableStats {
        private final int naiveQuads;
        private long coveredArea;
        private int maxQuadLength;
        private int maxQuadWidth;
        private long skippedTranslucent;
        private long skippedNonMergeable;
        private String sampleMergedRecordHex = "none";
        private String sampleMergedDecodedRecord = "none";

        private MutableStats(int naiveQuads) {
            this.naiveQuads = naiveQuads;
        }

        private void recordOutput(int length, int width) {
            this.coveredArea += (long) length * width;
            this.maxQuadLength = Math.max(this.maxQuadLength, length);
            this.maxQuadWidth = Math.max(this.maxQuadWidth, width);
        }

        private Stats toImmutable(int quadsAfterMerge) {
            return new Stats(
                    this.naiveQuads,
                    Math.max(0, this.naiveQuads - quadsAfterMerge),
                    quadsAfterMerge,
                    this.coveredArea,
                    this.maxQuadLength,
                    this.maxQuadWidth,
                    this.skippedTranslucent,
                    this.skippedNonMergeable,
                    this.sampleMergedRecordHex,
                    this.sampleMergedDecodedRecord
            );
        }
    }

    record Result(long[] records, int[] offsets, Stats stats) {
    }

    record Stats(
            int naiveQuads,
            int mergedQuads,
            int quadsAfterMerge,
            long coveredArea,
            int maxQuadLength,
            int maxQuadWidth,
            long skippedTranslucent,
            long skippedNonMergeable,
            String sampleMergedRecordHex,
            String sampleMergedDecodedRecord
    ) {
        double averageQuadArea() {
            return this.quadsAfterMerge == 0 ? 0.0 : (double) this.coveredArea / this.quadsAfterMerge;
        }

        double mergeRatio() {
            return this.naiveQuads == 0 ? 0.0 : (double) this.mergedQuads / this.naiveQuads;
        }
    }
}
