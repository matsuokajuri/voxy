package me.cortex.voxy.forge;

import me.cortex.voxy.common.world.WorldEngine;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ForgeVoxyBuiltSectionBuilder {
    private static final int BUCKET_COUNT = 8;
    private static final int BYTES_PER_PARTIAL_QUAD = Long.BYTES;
    private static final int SECTION_SIZE = 32;

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
        String offsetsSample = "none";
        String aabbSample = "none";
        long createdTime = System.currentTimeMillis();
        for (MutableSection section : groups.values()) {
            ForgeVoxyBuiltSection built = section.build(createdTime);
            builtSections.add(built);
            totalQuads += built.quadCount();
            geometryBytes += built.geometryBytes();
            if ("none".equals(offsetsSample)) {
                offsetsSample = formatOffsets(built.offsets());
                aabbSample = formatAabb(built.aabb());
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
                totalQuads,
                geometryBytes,
                offsetsSample,
                aabbSample,
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

    private static int bucketFor(ForgeCpuMeshLayer layer) {
        return switch (layer) {
            case SOLID -> 0;
            case CUTOUT -> 1;
            case TRANSLUCENT -> 2;
            case OTHER -> 3;
        };
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

    private static long packPartialQuadRecord(ForgeCpuBuiltSection section, int[] data, int quadIndex, int globalQuadIndex) {
        int baseVertex = quadIndex * 4;
        int baseOffset = baseVertex * ForgeCpuMeshBuffer.VERTEX_STRIDE_INTS;
        int color = data[baseOffset + 3];
        int normal = data[baseOffset + 7] & 15;
        int layer = section.layer().id & 15;
        int bucket = bucketFor(section.layer()) & 7;
        int centroidX = 0;
        int centroidY = 0;
        int centroidZ = 0;
        for (int i = 0; i < 4; i++) {
            int vertexOffset = (baseVertex + i) * ForgeCpuMeshBuffer.VERTEX_STRIDE_INTS;
            centroidX += Math.round(Float.intBitsToFloat(data[vertexOffset]));
            centroidY += Math.round(Float.intBitsToFloat(data[vertexOffset + 1]));
            centroidZ += Math.round(Float.intBitsToFloat(data[vertexOffset + 2]));
        }
        centroidX = clampToSection(Math.floorMod(centroidX / 4, SECTION_SIZE));
        centroidY = clampToSection(Math.floorMod(centroidY / 4, SECTION_SIZE));
        centroidZ = clampToSection(Math.floorMod(centroidZ / 4, SECTION_SIZE));

        return (color & 0xFFFFFFFFL)
                | ((long) layer << 32)
                | ((long) normal << 36)
                | ((long) centroidX << 40)
                | ((long) centroidY << 45)
                | ((long) centroidZ << 50)
                | ((long) bucket << 55)
                | (((long) globalQuadIndex & 0x3FL) << 58);
    }

    private record GroupKey(String dimension, int chunkX, int chunkZ, long sectionPosition) {
    }

    private static final class MutableSection {
        private final GroupKey key;
        private final List<ForgeCpuBuiltSection> sections = new ArrayList<>();
        private final int[] bucketQuadCounts = new int[BUCKET_COUNT];
        private float minX = Float.POSITIVE_INFINITY;
        private float minY = Float.POSITIVE_INFINITY;
        private float minZ = Float.POSITIVE_INFINITY;
        private float maxX = Float.NEGATIVE_INFINITY;
        private float maxY = Float.NEGATIVE_INFINITY;
        private float maxZ = Float.NEGATIVE_INFINITY;

        private MutableSection(GroupKey key) {
            this.key = key;
        }

        private void add(ForgeCpuBuiltSection section) {
            this.sections.add(section);
            this.bucketQuadCounts[bucketFor(section.layer())] += section.quadCount();
            ForgeCpuMeshBuffer buffer = section.meshBuffer();
            int[] data = buffer.vertexData();
            for (int vertex = 0; vertex < buffer.vertexCount(); vertex++) {
                int offset = vertex * ForgeCpuMeshBuffer.VERTEX_STRIDE_INTS;
                float x = Float.intBitsToFloat(data[offset]);
                float y = Float.intBitsToFloat(data[offset + 1]);
                float z = Float.intBitsToFloat(data[offset + 2]);
                this.minX = Math.min(this.minX, x);
                this.minY = Math.min(this.minY, y);
                this.minZ = Math.min(this.minZ, z);
                this.maxX = Math.max(this.maxX, x);
                this.maxY = Math.max(this.maxY, y);
                this.maxZ = Math.max(this.maxZ, z);
            }
        }

        private ForgeVoxyBuiltSection build(long createdTime) {
            int totalQuads = 0;
            int[] offsets = new int[BUCKET_COUNT];
            for (int i = 0; i < BUCKET_COUNT; i++) {
                offsets[i] = totalQuads * BYTES_PER_PARTIAL_QUAD;
                totalQuads += this.bucketQuadCounts[i];
            }

            long[] records = new long[totalQuads];
            int[] writePositions = new int[BUCKET_COUNT];
            for (int i = 0; i < BUCKET_COUNT; i++) {
                writePositions[i] = offsets[i] / BYTES_PER_PARTIAL_QUAD;
            }

            int globalQuadIndex = 0;
            for (int bucket = 0; bucket < BUCKET_COUNT; bucket++) {
                for (ForgeCpuBuiltSection section : this.sections) {
                    if (bucketFor(section.layer()) != bucket) {
                        continue;
                    }
                    ForgeCpuMeshBuffer buffer = section.meshBuffer();
                    int[] data = buffer.vertexData();
                    for (int quad = 0; quad < buffer.quadCount(); quad++) {
                        records[writePositions[bucket]++] = packPartialQuadRecord(section, data, quad, globalQuadIndex++);
                    }
                }
            }

            int aabb = packAabb(this.minX, this.minY, this.minZ, this.maxX, this.maxY, this.maxZ, this.key.sectionPosition);
            return new ForgeVoxyBuiltSection(
                    this.key.dimension,
                    this.key.chunkX,
                    this.key.chunkZ,
                    this.key.sectionPosition,
                    (byte) 0,
                    aabb,
                    offsets,
                    ForgeVoxyGeometryBuffer.partial(records),
                    null,
                    createdTime
            );
        }
    }
}
