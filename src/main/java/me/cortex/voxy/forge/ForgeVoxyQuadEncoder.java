package me.cortex.voxy.forge;

import me.cortex.voxy.common.world.WorldEngine;

/**
 * CPU-only documentation and partial encoder for the original Voxy 8-byte quad record.
 *
 * <p>The original renderer consumes one unsigned 64-bit record per quad:
 * face 0..2, length-1 3..6, width-1 7..10, local z/y/x 11..25,
 * Voxy client model id 26..41, biome tint index 46..54, and light id 55..62.
 * Bucket/type is not stored in the record; it is represented by BuiltSection offsets[8].
 */
public final class ForgeVoxyQuadEncoder {
    static final String GEOMETRY_FORMAT = "partial-original-bit-layout";
    static final String KNOWN_FIELDS = "face,size,localPosition,clientModelId,biomeId,light";
    static final int FACE_SHIFT = 0;
    static final int FACE_BITS = 3;
    static final int LENGTH_SHIFT = 3;
    static final int LENGTH_BITS = 4;
    static final int WIDTH_SHIFT = 7;
    static final int WIDTH_BITS = 4;
    static final int Z_SHIFT = 11;
    static final int Y_SHIFT = 16;
    static final int X_SHIFT = 21;
    static final int LOCAL_POSITION_BITS = 5;
    static final int MODEL_ID_SHIFT = 26;
    static final int MODEL_ID_BITS = 16;
    static final int BIOME_ID_SHIFT = 46;
    static final int BIOME_ID_BITS = 9;
    static final int LIGHT_SHIFT = 55;
    static final int LIGHT_BITS = 8;
    static final long KNOWN_BITS_MASK = mask(FACE_BITS, FACE_SHIFT)
            | mask(LENGTH_BITS, LENGTH_SHIFT)
            | mask(WIDTH_BITS, WIDTH_SHIFT)
            | mask(LOCAL_POSITION_BITS, Z_SHIFT)
            | mask(LOCAL_POSITION_BITS, Y_SHIFT)
            | mask(LOCAL_POSITION_BITS, X_SHIFT)
            | mask(MODEL_ID_BITS, MODEL_ID_SHIFT)
            | mask(BIOME_ID_BITS, BIOME_ID_SHIFT)
            | mask(LIGHT_BITS, LIGHT_SHIFT);

    private static final int SECTION_SIZE = 32;
    private static final int MAX_ORIGINAL_QUAD_SPAN = 16;
    private static final int MAX_MODEL_ID = (1 << MODEL_ID_BITS) - 1;
    private static final int MAX_BIOME_ID = (1 << BIOME_ID_BITS) - 1;

    private ForgeVoxyQuadEncoder() {
    }

    static EncodedQuad encode(ForgeCpuBuiltSection section, int[] data, int quadIndex) {
        int face = originalFace(data, quadIndex);
        int bucket = bucketFor(section.layer(), face);
        QuadGeometry geometry = quadGeometry(section.sectionPosition(), data, quadIndex, face);
        int light = readLight(data, quadIndex);
        int blockId = readBlockId(data, quadIndex);
        int layerId = readLayerId(data, quadIndex);
        int tintIndex = readTintIndex(data, quadIndex);
        ForgeVoxyModelIdMapper.ModelIdResult modelId = ForgeVoxyModelIdMapper.INSTANCE.getOrCreateModelId(blockId);
        int rawBiomeId = readBiomeId(data, quadIndex);
        boolean missingBiomeId = rawBiomeId < 0;
        boolean biomeOverflow = rawBiomeId > MAX_BIOME_ID;
        int biomeId = missingBiomeId ? 0 : Math.min(rawBiomeId, MAX_BIOME_ID);
        long record = packRecord(face, geometry.localX(), geometry.localY(), geometry.localZ(), geometry.length(), geometry.width(), modelId.modelId(), biomeId, light);
        return new EncodedQuad(
                record,
                bucket,
                face,
                geometry.localX(),
                geometry.localY(),
                geometry.localZ(),
                geometry.length(),
                geometry.width(),
                modelId.modelId(),
                modelId.missing(),
                modelId.overflow(),
                biomeId,
                missingBiomeId,
                biomeOverflow,
                light,
                layerId,
                tintIndex,
                true,
                false,
                geometry.mergeable()
        );
    }

    static EncodedQuad encodeMerged(EncodedQuad source, int localX, int localY, int localZ, int length, int width) {
        long record = packRecord(source.face(), localX, localY, localZ, length, width, source.modelId(), source.biomeId(), source.lightId());
        return new EncodedQuad(
                record,
                source.bucket(),
                source.face(),
                localX,
                localY,
                localZ,
                length,
                width,
                source.modelId(),
                source.missingModelId(),
                source.modelIdOverflow(),
                source.biomeId(),
                source.missingBiomeId(),
                source.biomeIdOverflow(),
                source.lightId(),
                source.layerId(),
                source.tintIndex(),
                source.missingTexture(),
                length == 1 && width == 1,
                source.mergeable()
        );
    }

    static int bucketFor(ForgeCpuMeshLayer layer, int face) {
        if (layer == ForgeCpuMeshLayer.TRANSLUCENT) {
            return 0;
        }
        if (face < 0 || face > 5) {
            return 1;
        }
        return 2 + face;
    }

    static String formatRecordHex(long record) {
        return String.format("0x%016X", record);
    }

    static String decodeRecord(long record) {
        int face = extract(record, FACE_BITS, FACE_SHIFT);
        int length = extract(record, LENGTH_BITS, LENGTH_SHIFT) + 1;
        int width = extract(record, WIDTH_BITS, WIDTH_SHIFT) + 1;
        int x = extract(record, LOCAL_POSITION_BITS, X_SHIFT);
        int y = extract(record, LOCAL_POSITION_BITS, Y_SHIFT);
        int z = extract(record, LOCAL_POSITION_BITS, Z_SHIFT);
        int modelId = extractModelId(record);
        int biomeId = extractBiomeId(record);
        int light = extractLightId(record);
        return "face=" + face
                + " size=" + length + "x" + width
                + " pos=" + x + "," + y + "," + z
                + " modelId=" + modelId
                + " biome=" + biomeId
                + " light=" + light;
    }

    static int extractModelId(long record) {
        return extract(record, MODEL_ID_BITS, MODEL_ID_SHIFT);
    }

    static int extractBiomeId(long record) {
        return extract(record, BIOME_ID_BITS, BIOME_ID_SHIFT);
    }

    static int extractLightId(long record) {
        return extract(record, LIGHT_BITS, LIGHT_SHIFT);
    }

    private static long packRecord(int face, int localX, int localY, int localZ, int length, int width, int modelId, int biomeId, int light) {
        length = Math.max(1, Math.min(MAX_ORIGINAL_QUAD_SPAN, length));
        width = Math.max(1, Math.min(MAX_ORIGINAL_QUAD_SPAN, width));
        modelId = Math.max(0, Math.min(MAX_MODEL_ID, modelId));
        biomeId = Math.max(0, Math.min(MAX_BIOME_ID, biomeId));
        light &= 0xFF;

        long record = face & ((1L << FACE_BITS) - 1L);
        record |= (long) (length - 1) << LENGTH_SHIFT;
        record |= (long) (width - 1) << WIDTH_SHIFT;
        record |= (long) clampToSection(localZ) << Z_SHIFT;
        record |= (long) clampToSection(localY) << Y_SHIFT;
        record |= (long) clampToSection(localX) << X_SHIFT;
        record |= (long) modelId << MODEL_ID_SHIFT;
        record |= (long) biomeId << BIOME_ID_SHIFT;
        record |= (long) light << LIGHT_SHIFT;
        return record;
    }

    private static QuadGeometry quadGeometry(long sectionPosition, int[] data, int quadIndex, int face) {
        if (face < 0 || face > 5) {
            face = 0;
        }
        int axis = face >> 1;
        int axisSide = face & 1;
        FloatBounds floatBounds = localFloatBounds(sectionPosition, data, quadIndex);
        Bounds localBounds = floatBounds.toCellBounds();

        int localX;
        int localY;
        int localZ;
        int length;
        int width;
        if (axis == 0) {
            localX = localBounds.minX();
            localY = axisSide == 0 ? localBounds.minY() : localBounds.maxY();
            localZ = localBounds.minZ();
            length = localBounds.sizeX();
            width = localBounds.sizeZ();
        } else if (axis == 1) {
            localX = localBounds.minX();
            localY = localBounds.minY();
            localZ = axisSide == 0 ? localBounds.minZ() : localBounds.maxZ();
            length = localBounds.sizeX();
            width = localBounds.sizeY();
        } else {
            localX = axisSide == 0 ? localBounds.minX() : localBounds.maxX();
            localY = localBounds.minY();
            localZ = localBounds.minZ();
            length = localBounds.sizeY();
            width = localBounds.sizeZ();
        }

        length = Math.max(1, Math.min(MAX_ORIGINAL_QUAD_SPAN, length));
        width = Math.max(1, Math.min(MAX_ORIGINAL_QUAD_SPAN, width));
        return new QuadGeometry(
                clampToSection(localX),
                clampToSection(localY),
                clampToSection(localZ),
                length,
                width,
                isMergeableUnitFace(floatBounds, axis)
        );
    }

    private static FloatBounds localFloatBounds(long sectionPosition, int[] data, int quadIndex) {
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
            float x = (Float.intBitsToFloat(data[offset + ForgeCpuMeshBuffer.X_OFFSET]) - baseX) / scale;
            float y = (Float.intBitsToFloat(data[offset + ForgeCpuMeshBuffer.Y_OFFSET]) - baseY) / scale;
            float z = (Float.intBitsToFloat(data[offset + ForgeCpuMeshBuffer.Z_OFFSET]) - baseZ) / scale;
            minX = Math.min(minX, x);
            minY = Math.min(minY, y);
            minZ = Math.min(minZ, z);
            maxX = Math.max(maxX, x);
            maxY = Math.max(maxY, y);
            maxZ = Math.max(maxZ, z);
        }

        return new FloatBounds(minX, minY, minZ, maxX, maxY, maxZ);
    }

    private static boolean isMergeableUnitFace(FloatBounds bounds, int axis) {
        float sizeX = bounds.maxX() - bounds.minX();
        float sizeY = bounds.maxY() - bounds.minY();
        float sizeZ = bounds.maxZ() - bounds.minZ();
        if (axis == 0) {
            return isOne(sizeX) && isOne(sizeZ) && isZero(sizeY)
                    && isInteger(bounds.minX()) && isInteger(bounds.minZ()) && isInteger(bounds.minY());
        }
        if (axis == 1) {
            return isOne(sizeX) && isOne(sizeY) && isZero(sizeZ)
                    && isInteger(bounds.minX()) && isInteger(bounds.minY()) && isInteger(bounds.minZ());
        }
        return isOne(sizeY) && isOne(sizeZ) && isZero(sizeX)
                && isInteger(bounds.minY()) && isInteger(bounds.minZ()) && isInteger(bounds.minX());
    }

    private static boolean isOne(float value) {
        return Math.abs(value - 1.0f) < 0.0001f;
    }

    private static boolean isZero(float value) {
        return Math.abs(value) < 0.0001f;
    }

    private static boolean isInteger(float value) {
        return Math.abs(value - Math.round(value)) < 0.0001f;
    }

    private static int readLight(int[] data, int quadIndex) {
        int offset = quadIndex * 4 * ForgeCpuMeshBuffer.VERTEX_STRIDE_INTS;
        return data[offset + ForgeCpuMeshBuffer.LIGHT_OFFSET] & 0xFF;
    }

    private static int readBlockId(int[] data, int quadIndex) {
        int offset = quadIndex * 4 * ForgeCpuMeshBuffer.VERTEX_STRIDE_INTS;
        int index = offset + ForgeCpuMeshBuffer.BLOCK_ID_OFFSET;
        return index < data.length ? data[index] : 0;
    }

    private static int readBiomeId(int[] data, int quadIndex) {
        int offset = quadIndex * 4 * ForgeCpuMeshBuffer.VERTEX_STRIDE_INTS;
        int index = offset + ForgeCpuMeshBuffer.BIOME_ID_OFFSET;
        return index < data.length ? data[index] : -1;
    }

    private static int originalFace(int[] data, int quadIndex) {
        int offset = quadIndex * 4 * ForgeCpuMeshBuffer.VERTEX_STRIDE_INTS;
        int normal = data[offset + ForgeCpuMeshBuffer.NORMAL_OFFSET];
        return normal >= 0 && normal <= 5 ? normal : -1;
    }

    private static int readLayerId(int[] data, int quadIndex) {
        int offset = quadIndex * 4 * ForgeCpuMeshBuffer.VERTEX_STRIDE_INTS;
        return data[offset + ForgeCpuMeshBuffer.LAYER_OFFSET];
    }

    private static int readTintIndex(int[] data, int quadIndex) {
        int offset = quadIndex * 4 * ForgeCpuMeshBuffer.VERTEX_STRIDE_INTS;
        return data[offset + ForgeCpuMeshBuffer.TINT_INDEX_OFFSET];
    }

    private static int clampToSection(int value) {
        return Math.max(0, Math.min(31, value));
    }

    private static int extract(long record, int bits, int shift) {
        return (int) ((record >>> shift) & ((1L << bits) - 1L));
    }

    private static long mask(int bits, int shift) {
        return ((1L << bits) - 1L) << shift;
    }

    record EncodedQuad(
            long record,
            int bucket,
            int face,
            int localX,
            int localY,
            int localZ,
            int length,
            int width,
            int modelId,
            boolean missingModelId,
            boolean modelIdOverflow,
            int biomeId,
            boolean missingBiomeId,
            boolean biomeIdOverflow,
            int lightId,
            int layerId,
            int tintIndex,
            boolean missingTexture,
            boolean missingGreedy,
            boolean mergeable
    ) {
    }

    private record QuadGeometry(int localX, int localY, int localZ, int length, int width, boolean mergeable) {
    }

    private record FloatBounds(float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
        private Bounds toCellBounds() {
            return new Bounds(
                    clampToSection((int) Math.floor(this.minX)),
                    clampToSection((int) Math.floor(this.minY)),
                    clampToSection((int) Math.floor(this.minZ)),
                    clampToSection((int) Math.ceil(this.maxX) - 1),
                    clampToSection((int) Math.ceil(this.maxY) - 1),
                    clampToSection((int) Math.ceil(this.maxZ) - 1)
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
