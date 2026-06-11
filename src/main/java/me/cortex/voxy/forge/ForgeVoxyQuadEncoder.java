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
        int encodedPosition = packOriginalPositionBits(section.sectionPosition(), data, quadIndex, face);
        int light = readLight(data, quadIndex);
        int blockId = readBlockId(data, quadIndex);
        ForgeVoxyModelIdMapper.ModelIdResult modelId = ForgeVoxyModelIdMapper.INSTANCE.getOrCreateModelId(blockId);
        int rawBiomeId = readBiomeId(data, quadIndex);
        boolean missingBiomeId = rawBiomeId < 0;
        boolean biomeOverflow = rawBiomeId > MAX_BIOME_ID;
        int biomeId = missingBiomeId ? 0 : Math.min(rawBiomeId, MAX_BIOME_ID);
        long record = Integer.toUnsignedLong(encodedPosition)
                | ((long) modelId.modelId() << MODEL_ID_SHIFT)
                | ((long) biomeId << BIOME_ID_SHIFT)
                | ((long) light << LIGHT_SHIFT);
        return new EncodedQuad(
                record,
                bucket,
                modelId.modelId(),
                modelId.missing(),
                modelId.overflow(),
                biomeId,
                missingBiomeId,
                biomeOverflow,
                true,
                true
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

    private static int packOriginalPositionBits(long sectionPosition, int[] data, int quadIndex, int face) {
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
        encodedPosition |= ((length - 1) << LENGTH_SHIFT);
        encodedPosition |= ((width - 1) << WIDTH_SHIFT);
        encodedPosition |= clampToSection(x) << (axis == 2 ? Y_SHIFT : X_SHIFT);
        encodedPosition |= clampToSection(z) << (axis == 1 ? Y_SHIFT : Z_SHIFT);
        int shiftAmount = axis == 0 ? Y_SHIFT : (axis == 1 ? Z_SHIFT : X_SHIFT);
        encodedPosition |= clampToSection(auxiliaryPosition) << shiftAmount;
        return encodedPosition;
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

        return new Bounds(
                clampToSection((int) Math.floor(minX)),
                clampToSection((int) Math.floor(minY)),
                clampToSection((int) Math.floor(minZ)),
                clampToSection((int) Math.ceil(maxX) - 1),
                clampToSection((int) Math.ceil(maxY) - 1),
                clampToSection((int) Math.ceil(maxZ) - 1)
        );
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
            int modelId,
            boolean missingModelId,
            boolean modelIdOverflow,
            int biomeId,
            boolean missingBiomeId,
            boolean biomeIdOverflow,
            boolean missingTexture,
            boolean missingGreedy
    ) {
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
