package me.cortex.voxy.forge;

import me.cortex.voxy.common.world.WorldEngine;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ForgeVoxyBuiltSectionBuilder {
    private static final int BUCKET_COUNT = 8;
    private static final int SECTION_SIZE = 32;
    private static final int MAX_ORIGINAL_QUAD_SPAN = 16;
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
        long createdTime = System.currentTimeMillis();
        for (MutableSection section : groups.values()) {
            ForgeVoxyBuiltSection built = section.build(createdTime);
            builtSections.add(built);
            totalQuads += built.quadCount();
            geometryBytes += built.geometryBytes();
            occupancyBytes += built.occupancyBytes();
            if ("none".equals(offsetsSample)) {
                offsetsSample = formatOffsets(built.offsets());
                namedOffsetsSample = formatNamedOffsets(built.offsets());
                aabbSample = formatAabb(built.aabb());
                positionSample = Long.toUnsignedString(built.position());
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
                ForgeVoxyGeometryBuffer.PARTIAL_ORIGINAL_POSITION_FORMAT,
                offsetsSample,
                namedOffsetsSample,
                positionSample,
                aabbSample,
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

    private static int bucketFor(ForgeCpuBuiltSection section, int[] data, int quadIndex) {
        if (section.layer() == ForgeCpuMeshLayer.TRANSLUCENT) {
            return 0;
        }
        int face = originalFace(data, quadIndex);
        if (face < 0 || face > 5) {
            return 1;
        }
        return 2 + face;
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

    private static long packPartialQuadRecord(ForgeCpuBuiltSection section, int[] data, int quadIndex) {
        int baseVertex = quadIndex * 4;
        int baseOffset = baseVertex * ForgeCpuMeshBuffer.VERTEX_STRIDE_INTS;
        int encodedPosition = packOriginalPositionBits(section.sectionPosition(), data, quadIndex);
        int light = data[baseOffset + 6] & 0xFF;
        int tint = data[baseOffset + 9] < 0 ? 0 : Math.min(3, data[baseOffset + 9]);
        int layer = section.layer().id & 15;
        long partialMetadata = ((long) light << 55)
                | ((long) tint << 53)
                | ((long) layer << 49);
        return partialMetadata | Integer.toUnsignedLong(encodedPosition);
    }

    private static int packOriginalPositionBits(long sectionPosition, int[] data, int quadIndex) {
        int face = originalFace(data, quadIndex);
        if (face < 0 || face > 5) {
            face = 0;
        }
        int axis = face >> 1;
        int axisSide = face & 1;
        Bounds localBounds = localBounds(sectionPosition, data, quadIndex);

        int x;
        int z;
        int length;
        int width;
        int auxiliaryPosition;
        if (axis == 0) {
            x = localBounds.minX();
            z = localBounds.minZ();
            length = localBounds.sizeX();
            width = localBounds.sizeZ();
            auxiliaryPosition = axisSide == 0 ? localBounds.minY() : localBounds.maxY();
        } else if (axis == 1) {
            x = localBounds.minX();
            z = localBounds.minY();
            length = localBounds.sizeX();
            width = localBounds.sizeY();
            auxiliaryPosition = axisSide == 0 ? localBounds.minZ() : localBounds.maxZ();
        } else {
            x = localBounds.minY();
            z = localBounds.minZ();
            length = localBounds.sizeY();
            width = localBounds.sizeZ();
            auxiliaryPosition = axisSide == 0 ? localBounds.minX() : localBounds.maxX();
        }

        length = Math.max(1, Math.min(MAX_ORIGINAL_QUAD_SPAN, length));
        width = Math.max(1, Math.min(MAX_ORIGINAL_QUAD_SPAN, width));
        int encodedPosition = face;
        encodedPosition |= ((width - 1) << 7) | ((length - 1) << 3);
        encodedPosition |= clampToSection(x) << (axis == 2 ? 16 : 21);
        encodedPosition |= clampToSection(z) << (axis == 1 ? 16 : 11);
        int shiftAmount = axis == 0 ? 16 : (axis == 1 ? 11 : 21);
        encodedPosition |= clampToSection(auxiliaryPosition) << shiftAmount;
        return encodedPosition;
    }

    private static int originalFace(int[] data, int quadIndex) {
        int offset = quadIndex * 4 * ForgeCpuMeshBuffer.VERTEX_STRIDE_INTS;
        int normal = data[offset + 7];
        return normal >= 0 && normal <= 5 ? normal : -1;
    }

    private static Bounds localBounds(long sectionPosition, int[] data, int quadIndex) {
        int level = Math.max(0, WorldEngine.getLevel(sectionPosition));
        int scale = 1 << level;
        float baseX = WorldEngine.getX(sectionPosition) * (float) SECTION_SIZE * scale;
        float baseY = WorldEngine.getY(sectionPosition) * (float) SECTION_SIZE * scale;
        float baseZ = WorldEngine.getZ(sectionPosition) * (float) SECTION_SIZE * scale;

        float minX = Float.POSITIVE_INFINITY;
        float minY = Float.POSITIVE_INFINITY;
        float minZ = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY;
        float maxY = Float.NEGATIVE_INFINITY;
        float maxZ = Float.NEGATIVE_INFINITY;
        int baseVertex = quadIndex * 4;
        for (int i = 0; i < 4; i++) {
            int offset = (baseVertex + i) * ForgeCpuMeshBuffer.VERTEX_STRIDE_INTS;
            float x = (Float.intBitsToFloat(data[offset]) - baseX) / scale;
            float y = (Float.intBitsToFloat(data[offset + 1]) - baseY) / scale;
            float z = (Float.intBitsToFloat(data[offset + 2]) - baseZ) / scale;
            minX = Math.min(minX, x);
            minY = Math.min(minY, y);
            minZ = Math.min(minZ, z);
            maxX = Math.max(maxX, x);
            maxY = Math.max(maxY, y);
            maxZ = Math.max(maxZ, z);
        }

        return new Bounds(
                clampToSection((int) Math.floor(minX)),
                clampToSection((int) Math.floor(minY)),
                clampToSection((int) Math.floor(minZ)),
                clampToSection((int) Math.ceil(maxX) - 1),
                clampToSection((int) Math.ceil(maxY) - 1),
                clampToSection((int) Math.ceil(maxZ) - 1)
        );
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
            ForgeCpuMeshBuffer buffer = section.meshBuffer();
            int[] data = buffer.vertexData();
            for (int quad = 0; quad < buffer.quadCount(); quad++) {
                this.bucketQuadCounts[bucketFor(section, data, quad)]++;
            }
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
                offsets[i] = totalQuads;
                totalQuads += this.bucketQuadCounts[i];
            }

            long[] records = new long[totalQuads];
            int[] writePositions = new int[BUCKET_COUNT];
            for (int i = 0; i < BUCKET_COUNT; i++) {
                writePositions[i] = offsets[i];
            }

            for (int bucket = 0; bucket < BUCKET_COUNT; bucket++) {
                for (ForgeCpuBuiltSection section : this.sections) {
                    ForgeCpuMeshBuffer buffer = section.meshBuffer();
                    int[] data = buffer.vertexData();
                    for (int quad = 0; quad < buffer.quadCount(); quad++) {
                        if (bucketFor(section, data, quad) != bucket) {
                            continue;
                        }
                        records[writePositions[bucket]++] = packPartialQuadRecord(section, data, quad);
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
                    ForgeVoxyGeometryBuffer.partialOriginalPosition(records),
                    null,
                    createdTime
            );
        }
    }

    private record Bounds(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        private int sizeX() {
            return Math.max(1, this.maxX - this.minX + 1);
        }

        private int sizeY() {
            return Math.max(1, this.maxY - this.minY + 1);
        }

        private int sizeZ() {
            return Math.max(1, this.maxZ - this.minZ + 1);
        }
    }
}
