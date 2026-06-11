package me.cortex.voxy.forge;

import me.cortex.voxy.common.world.WorldEngine;

public final class ForgeBuiltSectionSimpleMeshBuilder {
    private static final int SECTION_SIZE = 32;

    private ForgeBuiltSectionSimpleMeshBuilder() {
    }

    public static AdaptedSection build(ForgeVoxyBuiltSection section) {
        if (section == null || section.isClosed() || section.isEmpty()) {
            return AdaptedSection.empty();
        }
        ForgeVoxyGeometryBuffer geometryBuffer = section.geometryBuffer();
        if (geometryBuffer == null || geometryBuffer.isClosed()) {
            return AdaptedSection.empty();
        }

        long[] records = geometryBuffer.packedQuads();
        int[] offsets = section.offsets();
        if (records == null || records.length == 0 || offsets == null || offsets.length != 8) {
            return AdaptedSection.empty();
        }

        ForgeCpuMeshBuffer.Builder builder = new ForgeCpuMeshBuffer.Builder();
        int emittedQuads = 0;
        int skippedTranslucent = 0;
        int doubleSidedAsSingle = 0;
        int skippedInvalid = 0;
        int level = Math.max(0, WorldEngine.getLevel(section.position()));
        int scale = 1 << level;
        float baseX = WorldEngine.getX(section.position()) * (float) SECTION_SIZE * scale;
        float baseY = WorldEngine.getY(section.position()) * (float) SECTION_SIZE * scale;
        float baseZ = WorldEngine.getZ(section.position()) * (float) SECTION_SIZE * scale;

        for (int bucket = 0; bucket < 8; bucket++) {
            int start = clampOffset(offsets[bucket], records.length);
            int end = bucket + 1 < 8 ? clampOffset(offsets[bucket + 1], records.length) : records.length;
            if (end < start) {
                skippedInvalid += start - end;
                continue;
            }
            if (bucket == 0) {
                skippedTranslucent += end - start;
                continue;
            }
            if (bucket == 1) {
                doubleSidedAsSingle += end - start;
            }
            for (int index = start; index < end; index++) {
                long record = records[index];
                int face = ForgeVoxyQuadEncoder.extractFace(record);
                if (face < 0 || face > 5) {
                    skippedInvalid++;
                    continue;
                }
                emitQuad(builder, record, bucket, baseX, baseY, baseZ, scale);
                emittedQuads++;
            }
        }

        if (builder.quadCount() == 0) {
            return new AdaptedSection(null, new Stats(0, skippedTranslucent, doubleSidedAsSingle, skippedInvalid));
        }

        ForgeCpuBuiltSection adapted = new ForgeCpuBuiltSection(
                section.dimension(),
                section.chunkX(),
                section.chunkZ(),
                section.position(),
                ForgeCpuMeshLayer.OTHER,
                ForgeGpuMeshBuffer.sourceHashFromBuiltSection(section),
                System.currentTimeMillis(),
                0.0D,
                builder.build()
        );
        return new AdaptedSection(adapted, new Stats(emittedQuads, skippedTranslucent, doubleSidedAsSingle, skippedInvalid));
    }

    public static PreviewStats preview(ForgeVoxyBuiltSection section) {
        if (section == null || section.isClosed() || section.isEmpty() || section.geometryBuffer() == null || section.geometryBuffer().isClosed()) {
            return new PreviewStats(0, 0, 0, 0);
        }
        long[] records = section.geometryBuffer().packedQuads();
        int[] offsets = section.offsets();
        if (records == null || offsets == null || offsets.length != 8) {
            return new PreviewStats(0, 0, 0, 0);
        }
        int total = records.length;
        int translucent = bucketSize(offsets, records.length, 0);
        int doubleSided = bucketSize(offsets, records.length, 1);
        int uploadable = Math.max(0, total - translucent);
        return new PreviewStats(total, uploadable, translucent, doubleSided);
    }

    private static void emitQuad(ForgeCpuMeshBuffer.Builder builder, long record, int bucket, float baseX, float baseY, float baseZ, int scale) {
        int face = ForgeVoxyQuadEncoder.extractFace(record);
        int localX = ForgeVoxyQuadEncoder.extractLocalX(record);
        int localY = ForgeVoxyQuadEncoder.extractLocalY(record);
        int localZ = ForgeVoxyQuadEncoder.extractLocalZ(record);
        int length = ForgeVoxyQuadEncoder.extractLength(record);
        int width = ForgeVoxyQuadEncoder.extractWidth(record);
        int light = ForgeVoxyQuadEncoder.extractLightId(record);
        int modelId = ForgeVoxyQuadEncoder.extractModelId(record);
        int biomeId = ForgeVoxyQuadEncoder.extractBiomeId(record);
        int color = stableRecordColor(modelId, biomeId, light, face, bucket);
        float x0 = baseX + localX * scale;
        float y0 = baseY + localY * scale;
        float z0 = baseZ + localZ * scale;
        float x1 = x0;
        float y1 = y0;
        float z1 = z0;
        float x2 = x0;
        float y2 = y0;
        float z2 = z0;
        float x3 = x0;
        float y3 = y0;
        float z3 = z0;

        if (face == 0 || face == 1) {
            x1 = x0 + length * scale;
            z2 = z0 + width * scale;
            x3 = x1;
            z3 = z2;
        } else if (face == 2 || face == 3) {
            x1 = x0 + length * scale;
            y2 = y0 + width * scale;
            x3 = x1;
            y3 = y2;
        } else {
            y1 = y0 + length * scale;
            z2 = z0 + width * scale;
            y3 = y1;
            z3 = z2;
        }

        putVertex(builder, x0, y0, z0, color, light, face, bucket, modelId, biomeId);
        putVertex(builder, x1, y1, z1, color, light, face, bucket, modelId, biomeId);
        putVertex(builder, x3, y3, z3, color, light, face, bucket, modelId, biomeId);
        putVertex(builder, x2, y2, z2, color, light, face, bucket, modelId, biomeId);
    }

    private static void putVertex(
            ForgeCpuMeshBuffer.Builder builder,
            float x,
            float y,
            float z,
            int color,
            int light,
            int face,
            int bucket,
            int modelId,
            int biomeId
    ) {
        builder.putVertex(
                x,
                y,
                z,
                color,
                0.0F,
                0.0F,
                light,
                face,
                ForgeCpuMeshLayer.OTHER.id,
                bucket,
                modelId,
                biomeId
        );
    }

    private static int stableRecordColor(int modelId, int biomeId, int light, int face, int bucket) {
        int seed = modelId * 0x45D9F3B ^ biomeId * 0x27D4EB2D ^ face * 0x165667B1 ^ bucket * 0x9E3779B9;
        seed ^= seed >>> 16;
        int brightness = 96 + Math.min(159, Math.max(0, light));
        int red = scaleColor(80 + ((seed >>> 16) & 0x7F), brightness);
        int green = scaleColor(80 + ((seed >>> 8) & 0x7F), brightness);
        int blue = scaleColor(80 + (seed & 0x7F), brightness);
        return 0xFF000000 | (red << 16) | (green << 8) | blue;
    }

    private static int scaleColor(int value, int brightness) {
        return Math.max(32, Math.min(255, value * brightness / 255));
    }

    private static int bucketSize(int[] offsets, int recordCount, int bucket) {
        int start = clampOffset(offsets[bucket], recordCount);
        int end = bucket + 1 < 8 ? clampOffset(offsets[bucket + 1], recordCount) : recordCount;
        return Math.max(0, end - start);
    }

    private static int clampOffset(int value, int recordCount) {
        return Math.max(0, Math.min(recordCount, value));
    }

    public record PreviewStats(
            int totalRecords,
            int uploadableRecords,
            int translucentRecords,
            int doubleSidedRecords
    ) {
    }

    public record Stats(
            int emittedQuads,
            int skippedTranslucent,
            int doubleSidedAsSingle,
            int skippedInvalid
    ) {
    }

    public static final class AdaptedSection implements AutoCloseable {
        private final ForgeCpuBuiltSection section;
        private final Stats stats;

        private AdaptedSection(ForgeCpuBuiltSection section, Stats stats) {
            this.section = section;
            this.stats = stats;
        }

        private static AdaptedSection empty() {
            return new AdaptedSection(null, new Stats(0, 0, 0, 0));
        }

        public ForgeCpuBuiltSection section() {
            return this.section;
        }

        public Stats stats() {
            return this.stats;
        }

        @Override
        public void close() {
            if (this.section != null) {
                this.section.close();
            }
        }
    }
}
