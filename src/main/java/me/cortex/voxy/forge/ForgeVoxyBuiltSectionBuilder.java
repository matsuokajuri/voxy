package me.cortex.voxy.forge;

import me.cortex.voxy.common.world.WorldEngine;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ForgeVoxyBuiltSectionBuilder {
    private static final int BUCKET_COUNT = 8;
    private static final int SECTION_SIZE = 32;
    static final String OFFSETS_SEMANTIC = "original-quad-index-buckets";
    static final String[] BUCKET_NAMES = {
            "translucent",
            "double_sided",
            "directional_y_minus",
            "directional_y_plus",
            "directional_z_minus",
            "directional_z_plus",
            "directional_x_minus",
            "directional_x_plus"
    };

    private ForgeVoxyBuiltSectionBuilder() {
    }

    public static ForgeVoxyBuiltSectionBuildResult fromCpuMesh(ForgeCpuMeshBuildResult cpuResult) {
        long start = System.nanoTime();
        var groups = new LinkedHashMap<GroupKey, MutableSection>();
        var cpuSections = cpuResult.sections();
        for (ForgeCpuBuiltSection cpuSection : cpuSections) {
            if (cpuSection == null) {
                continue;
            }
            ForgeCpuMeshBuffer buffer = cpuSection.meshBuffer();
            if (buffer == null || buffer.isClosed() || buffer.quadCount() == 0) {
                continue;
            }

            var key = new GroupKey(cpuSection.dimension(), cpuSection.chunkX(), cpuSection.chunkZ(), cpuSection.sectionPosition());
            MutableSection section = groups.get(key);
            if (section == null) {
                section = new MutableSection(key);
                groups.put(key, section);
            }
            section.add(cpuSection);
        }

        var builtSections = new ArrayList<ForgeVoxyBuiltSection>(groups.size());
        long totalQuads = 0;
        long geometryBytes = 0;
        long occupancyBytes = 0;
        int emptySections = Math.max(0, cpuResult.stats().sectionsFound() - groups.size());
        String offsetsSample = "none";
        String namedOffsetsSample = "none";
        String aabbSample = "none";
        String positionSample = "none";
        String sampleRecordHex = "none";
        String sampleDecodedRecord = "none";
        long missingModelId = 0;
        long missingTexture = 0;
        long missingGreedy = 0;
        long modelIdOverflow = 0;
        long missingBiomeId = 0;
        long biomeIdOverflow = 0;
        long naiveQuads = 0;
        long mergedQuads = 0;
        long quadsAfterMerge = 0;
        long coveredQuadArea = 0;
        int maxQuadLength = 0;
        int maxQuadWidth = 0;
        long skippedTranslucent = 0;
        long skippedNonMergeable = 0;
        String sampleMergedRecordHex = "none";
        String sampleMergedDecodedRecord = "none";
        var uniqueModelIds = new HashSet<Integer>();
        var uniqueBiomeIds = new HashSet<Integer>();
        long createdTime = System.currentTimeMillis();
        for (MutableSection section : groups.values()) {
            ForgeVoxyBuiltSection built = section.build(createdTime);
            builtSections.add(built);
            totalQuads += built.quadCount();
            geometryBytes += built.geometryBytes();
            occupancyBytes += built.occupancyBytes();
            missingModelId += section.missingModelId;
            missingTexture += section.missingTexture;
            missingGreedy += section.missingGreedy;
            modelIdOverflow += section.modelIdOverflow;
            missingBiomeId += section.missingBiomeId;
            biomeIdOverflow += section.biomeIdOverflow;
            naiveQuads += section.greedyStats.naiveQuads();
            mergedQuads += section.greedyStats.mergedQuads();
            quadsAfterMerge += section.greedyStats.quadsAfterMerge();
            coveredQuadArea += section.greedyStats.coveredArea();
            maxQuadLength = Math.max(maxQuadLength, section.greedyStats.maxQuadLength());
            maxQuadWidth = Math.max(maxQuadWidth, section.greedyStats.maxQuadWidth());
            skippedTranslucent += section.greedyStats.skippedTranslucent();
            skippedNonMergeable += section.greedyStats.skippedNonMergeable();
            uniqueModelIds.addAll(section.uniqueModelIds);
            uniqueBiomeIds.addAll(section.uniqueBiomeIds);
            if ("none".equals(offsetsSample)) {
                offsetsSample = formatOffsets(built.offsets());
                namedOffsetsSample = formatNamedOffsets(built.offsets());
                aabbSample = formatAabb(built.aabb());
                positionSample = Long.toUnsignedString(built.position());
            }
            if ("none".equals(sampleRecordHex) && built.hasSampleRecord()) {
                long record = built.sampleRecord();
                sampleRecordHex = ForgeVoxyQuadEncoder.formatRecordHex(record);
                sampleDecodedRecord = ForgeVoxyQuadEncoder.decodeRecord(record);
            }
            if ("none".equals(sampleMergedRecordHex) && !"none".equals(section.greedyStats.sampleMergedRecordHex())) {
                sampleMergedRecordHex = section.greedyStats.sampleMergedRecordHex();
                sampleMergedDecodedRecord = section.greedyStats.sampleMergedDecodedRecord();
            }
        }

        double elapsedMs = (System.nanoTime() - start) / 1_000_000.0;
        var cpuStats = cpuResult.stats();
        var stats = new ForgeVoxyBuiltSectionStats(
                cpuStats.dimension(),
                cpuStats.chunkX(),
                cpuStats.chunkZ(),
                cpuSections.size(),
                builtSections.size(),
                0,
                emptySections,
                totalQuads,
                geometryBytes,
                occupancyBytes,
                OFFSETS_SEMANTIC,
                ForgeVoxyGeometryBuffer.PARTIAL_ORIGINAL_BIT_LAYOUT_FORMAT,
                String.format("0x%016X", ForgeVoxyQuadEncoder.KNOWN_BITS_MASK),
                ForgeVoxyQuadEncoder.KNOWN_FIELDS,
                uniqueModelIds.size(),
                missingModelId,
                modelIdOverflow,
                uniqueBiomeIds.size(),
                missingBiomeId,
                biomeIdOverflow,
                missingTexture,
                missingGreedy,
                naiveQuads,
                mergedQuads,
                quadsAfterMerge,
                quadsAfterMerge == 0 ? 0.0 : (double) coveredQuadArea / quadsAfterMerge,
                naiveQuads == 0 ? 0.0 : (double) mergedQuads / naiveQuads,
                maxQuadLength,
                maxQuadWidth,
                skippedTranslucent,
                skippedNonMergeable,
                sampleMergedRecordHex,
                sampleMergedDecodedRecord,
                offsetsSample,
                namedOffsetsSample,
                positionSample,
                aabbSample,
                sampleRecordHex,
                sampleDecodedRecord,
                occupancyBytes != 0,
                false,
                elapsedMs
        );
        return new ForgeVoxyBuiltSectionBuildResult(stats, builtSections);
    }

    static String formatOffsets(int[] offsets) {
        if (offsets == null) {
            return "none";
        }
        return Arrays.toString(offsets);
    }

    static String formatNamedOffsets(int[] offsets) {
        if (offsets == null) {
            return "none";
        }
        StringBuilder builder = new StringBuilder();
        int previous = 0;
        for (int i = 0; i < BUCKET_COUNT; i++) {
            if (i != 0) {
                builder.append(',');
            }
            int start = i < offsets.length ? offsets[i] : previous;
            int next = i + 1 < offsets.length ? offsets[i + 1] : -1;
            builder.append(BUCKET_NAMES[i]).append('=').append(start);
            if (next >= 0) {
                builder.append('+').append(Math.max(0, next - start));
            }
            previous = start;
        }
        return builder.toString();
    }

    static String formatAabb(int aabb) {
        if (aabb < 0) {
            return "empty";
        }
        int minX = aabb & 31;
        int minY = (aabb >> 5) & 31;
        int minZ = (aabb >> 10) & 31;
        int sizeX = ((aabb >> 15) & 31) + 1;
        int sizeY = ((aabb >> 20) & 31) + 1;
        int sizeZ = ((aabb >> 25) & 31) + 1;
        return minX + "," + minY + "," + minZ + "+" + sizeX + "," + sizeY + "," + sizeZ;
    }

    private static int packAabb(float minX, float minY, float minZ, float maxX, float maxY, float maxZ, long sectionPosition) {
        if (!Float.isFinite(minX) || !Float.isFinite(minY) || !Float.isFinite(minZ)
                || !Float.isFinite(maxX) || !Float.isFinite(maxY) || !Float.isFinite(maxZ)) {
            return -1;
        }

        int level = Math.max(0, WorldEngine.getLevel(sectionPosition));
        int scale = 1 << level;
        float baseX = WorldEngine.getX(sectionPosition) * (float) SECTION_SIZE * scale;
        float baseY = WorldEngine.getY(sectionPosition) * (float) SECTION_SIZE * scale;
        float baseZ = WorldEngine.getZ(sectionPosition) * (float) SECTION_SIZE * scale;

        int localMinX = clampToSection((int) Math.floor((minX - baseX) / scale));
        int localMinY = clampToSection((int) Math.floor((minY - baseY) / scale));
        int localMinZ = clampToSection((int) Math.floor((minZ - baseZ) / scale));
        int localMaxX = Math.max(localMinX, clampToSection((int) Math.ceil((maxX - baseX) / scale) - 1));
        int localMaxY = Math.max(localMinY, clampToSection((int) Math.ceil((maxY - baseY) / scale) - 1));
        int localMaxZ = Math.max(localMinZ, clampToSection((int) Math.ceil((maxZ - baseZ) / scale) - 1));

        int sizeX = Math.max(1, localMaxX - localMinX + 1);
        int sizeY = Math.max(1, localMaxY - localMinY + 1);
        int sizeZ = Math.max(1, localMaxZ - localMinZ + 1);
        return localMinX
                | (localMinY << 5)
                | (localMinZ << 10)
                | ((sizeX - 1) << 15)
                | ((sizeY - 1) << 20)
                | ((sizeZ - 1) << 25);
    }

    private static int clampToSection(int value) {
        return Math.max(0, Math.min(31, value));
    }

    private record GroupKey(String dimension, int chunkX, int chunkZ, long sectionPosition) {
    }

    private static final class MutableSection {
        private final GroupKey key;
        private final List<ForgeCpuBuiltSection> sections = new ArrayList<>();
        private float minX = Float.POSITIVE_INFINITY;
        private float minY = Float.POSITIVE_INFINITY;
        private float minZ = Float.POSITIVE_INFINITY;
        private float maxX = Float.NEGATIVE_INFINITY;
        private float maxY = Float.NEGATIVE_INFINITY;
        private float maxZ = Float.NEGATIVE_INFINITY;
        private long missingModelId;
        private long modelIdOverflow;
        private long missingBiomeId;
        private long biomeIdOverflow;
        private long missingTexture;
        private long missingGreedy;
        private ForgeVoxyGreedyMesher.Stats greedyStats = new ForgeVoxyGreedyMesher.Stats(0, 0, 0, 0, 0, 0, 0, 0, "none", "none");
        private final HashSet<Integer> uniqueModelIds = new HashSet<>();
        private final HashSet<Integer> uniqueBiomeIds = new HashSet<>();

        private MutableSection(GroupKey key) {
            this.key = key;
        }

        private void add(ForgeCpuBuiltSection section) {
            this.sections.add(section);
            ForgeCpuMeshBuffer buffer = section.meshBuffer();
            int[] data = buffer.vertexData();
            for (int vertex = 0; vertex < buffer.vertexCount(); vertex++) {
                int offset = vertex * ForgeCpuMeshBuffer.VERTEX_STRIDE_INTS;
                float x = Float.intBitsToFloat(data[offset + ForgeCpuMeshBuffer.X_OFFSET]);
                float y = Float.intBitsToFloat(data[offset + ForgeCpuMeshBuffer.Y_OFFSET]);
                float z = Float.intBitsToFloat(data[offset + ForgeCpuMeshBuffer.Z_OFFSET]);
                this.minX = Math.min(this.minX, x);
                this.minY = Math.min(this.minY, y);
                this.minZ = Math.min(this.minZ, z);
                this.maxX = Math.max(this.maxX, x);
                this.maxY = Math.max(this.maxY, y);
                this.maxZ = Math.max(this.maxZ, z);
            }
        }

        private ForgeVoxyBuiltSection build(long createdTime) {
            var encodedQuads = new ArrayList<ForgeVoxyQuadEncoder.EncodedQuad>();
            for (ForgeCpuBuiltSection section : this.sections) {
                ForgeCpuMeshBuffer buffer = section.meshBuffer();
                int[] data = buffer.vertexData();
                for (int quad = 0; quad < buffer.quadCount(); quad++) {
                    ForgeVoxyQuadEncoder.EncodedQuad encoded = ForgeVoxyQuadEncoder.encode(section, data, quad);
                    encodedQuads.add(encoded);
                    if (encoded.missingModelId()) {
                        this.missingModelId++;
                    }
                    if (encoded.modelIdOverflow()) {
                        this.modelIdOverflow++;
                    }
                    if (!encoded.missingModelId()) {
                        this.uniqueModelIds.add(encoded.modelId());
                    }
                    if (encoded.missingBiomeId()) {
                        this.missingBiomeId++;
                    }
                    if (encoded.biomeIdOverflow()) {
                        this.biomeIdOverflow++;
                    }
                    if (!encoded.missingBiomeId()) {
                        this.uniqueBiomeIds.add(encoded.biomeId());
                    }
                    if (encoded.missingTexture()) {
                        this.missingTexture++;
                    }
                    if (encoded.missingGreedy()) {
                        this.missingGreedy++;
                    }
                }
            }
            ForgeVoxyGreedyMesher.Result greedyResult = ForgeVoxyGreedyMesher.merge(encodedQuads);
            this.greedyStats = greedyResult.stats();

            int aabb = packAabb(this.minX, this.minY, this.minZ, this.maxX, this.maxY, this.maxZ, this.key.sectionPosition);
            return new ForgeVoxyBuiltSection(
                    this.key.dimension,
                    this.key.chunkX,
                    this.key.chunkZ,
                    this.key.sectionPosition,
                    (byte) 0,
                    aabb,
                    greedyResult.offsets(),
                    ForgeVoxyGeometryBuffer.partialOriginalBitLayout(greedyResult.records()),
                    null,
                    greedyResult.stats().naiveQuads(),
                    greedyResult.stats().mergedQuads(),
                    greedyResult.stats().coveredArea(),
                    greedyResult.stats().skippedTranslucent(),
                    greedyResult.stats().skippedNonMergeable(),
                    createdTime
            );
        }
    }
}
